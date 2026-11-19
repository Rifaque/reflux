package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.MultiAddressSource
import dev.reflux.library.ConnectResult
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.SourceConnections
import dev.reflux.library.SourceRegistry
import dev.reflux.library.activeProfile
import dev.reflux.library.createProfile
import dev.reflux.library.endpoints
import dev.reflux.library.switchProfile
import dev.reflux.sources.jellyfin.JellyfinConnector
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-003 through the library: the owner's server at home (192.168.1.20) and through Tailscale (100.101.102.103), the same
 * account added at both. One source, one copy of each film, both addresses, home preferred, the first session ended so
 * that one stays on the server; an address removed without the source; another profile's account untouched.
 */
class JellyfinEndpointsLibraryTest {
    private val lan = "http://192.168.1.20:8096"
    private val tailscale = "http://100.101.102.103:8096"
    private val reachable = mutableSetOf(lan, tailscale)
    private var tokens = 0
    private val loggedOut = mutableListOf<String>()

    private val movie = """{"Id":"m1","Name":"Heat","Type":"Movie","ProductionYear":1995,"RunTimeTicks":102000000000,
        "UserData":{"PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false},
        "MediaSources":[{"Id":"s1","Container":"mkv","Size":1,"MediaStreams":[{"Type":"Video","Codec":"h264","Width":1920,"Height":1080}]}]}"""

    private val http = HttpFetcher { request ->
        val base = listOf(lan, tailscale).firstOrNull { request.url.startsWith("$it/") } ?: error("no route to host")
        if (base !in reachable) error("no route to host")
        val path = request.url.removePrefix("$base/").substringBefore('?')
        val query = request.url.substringAfter('?', "")
        val token = Regex("Token=\"([^\"]+)\"").find(request.headers["Authorization"].orEmpty())?.groupValues?.get(1)
        when (path) {
            "System/Info/Public" -> HttpResult(200, """{"ServerName":"Home","Version":"10.10.3","Id":"srv1"}""".encodeToByteArray())
            "Users/AuthenticateByName" -> {
                val who = if ("\"Username\":\"sam\"" in request.body!!.decodeToString()) "u2" else "u1"
                tokens++
                HttpResult(200, """{"AccessToken":"tok$tokens","ServerId":"srv1","User":{"Id":"$who","Name":"$who"}}""".encodeToByteArray())
            }
            "Sessions/Logout" -> {
                loggedOut += token.orEmpty()
                HttpResult(204, ByteArray(0))
            }
            "UserViews" -> HttpResult(200, """{"Items":[]}""".encodeToByteArray())
            "Items" -> HttpResult(
                200,
                (if ("IncludeItemTypes=Series" in query || "StartIndex=0" !in query) """{"Items":[],"TotalRecordCount":1}""" else """{"Items":[$movie],"TotalRecordCount":1}""").encodeToByteArray(),
            )
            else -> HttpResult(200, "{}".encodeToByteArray())
        }
    }

    private val file = Files.createTempDirectory("reflux-endpoints").resolve("library.db")
    private var clock = 1_790_000_000_000L
    private val library = Library(LibraryDatabase.open(file)) { clock }
    private fun registry() = SourceRegistry(mapOf(JellyfinSource.TYPE to { record -> JellyfinCredentials.decode(record.config)?.let { JellyfinSource(it, http, record.displayName) } }))
    private val registry = registry()
    private val connections = SourceConnections(library, registry, listOf(JellyfinConnector(http, "installation")), signOutContext = kotlinx.coroutines.Dispatchers.Unconfined)

    private fun connect(address: String, user: String = "alex") = runBlocking {
        val (result, signOut) = connections.connecting(ConnectionKind.SERVER, ConnectionRequest(address, user, "pw"))
        signOut.await()
        assertIs<ConnectResult.Connected>(result)
    }

    private fun scan() = runBlocking { library.sources().forEach { record -> registry.resolve(record.id)?.let { library.scan(it) } } }

    @Test
    fun theSameAccountAtASecondAddressIsOneSourceWithBoth() {
        val home = connect(lan)
        scan()
        assertEquals(listOf("Heat"), library.movies().map { it.item.title })
        val away = connect(tailscale)
        assertEquals(home.sourceId, away.sourceId, "the same source")
        assertTrue(away.updated)
        scan()
        assertEquals(1, library.sources().size, "never a second source")
        assertEquals(listOf("Heat"), library.movies().map { it.item.title }, "and never a second copy")
        assertEquals(1, library.versions(library.movies().single().item.id).size)
        assertEquals(listOf(lan, tailscale), connections.addresses(home.sourceId), "home first")
        // One session stays on the server: the first sign-in's was ended, the one kept was not.
        assertEquals(listOf("tok1"), loggedOut)
        // Both addresses are known on this device, for another profile to sign in at.
        assertEquals(setOf(lan, tailscale), library.endpoints().map { it.address }.toSet())
        assertEquals(lan, library.endpoints().single { it.id == library.source(home.sourceId)!!.endpointId }.address, "the source names its home address")
    }

    @Test
    fun awayFromHomeItPlaysThroughTailscaleAndComesBackAfterARestart() = runBlocking {
        val id = connect(lan).sourceId
        connect(tailscale)
        val source = registry.resolve(id)!!
        assertIs<MultiAddressSource>(source)
        reachable -= lan
        assertEquals(Availability.AVAILABLE, library.refreshAvailability(source))
        scan()
        assertEquals(listOf("Heat"), library.movies().map { it.item.title }, "the library through the other address")
        val version = library.versions(library.movies().single().item.id).single().version
        assertTrue((source as JellyfinSource).playbackTarget(version.location.path).uri.startsWith("$tailscale/"))
        // Restarted: the library opened again, the adapters made again from what was kept. Both addresses, home first.
        val reopened = Library(LibraryDatabase.open(file)) { clock }
        val again = registry().apply { load(reopened.sources()) }.resolve(id) as JellyfinSource
        reachable += lan
        assertEquals(Availability.AVAILABLE, again.availability())
        assertTrue(again.playbackTarget(version.location.path).uri.startsWith("$lan/"), "home again")
        assertEquals(listOf(lan, tailscale), again.addressHealth().map { it.address })
    }

    @Test
    fun removingOneAddressKeepsTheSourceAndTheOther() {
        val id = connect(lan).sourceId
        connect(tailscale)
        scan()
        assertTrue(connections.removeAddress(id, tailscale))
        assertEquals(listOf(lan), connections.addresses(id))
        assertEquals(listOf("Heat"), library.movies().map { it.item.title }, "the library is untouched")
        assertFalse(connections.removeAddress(id, lan), "the last address stays")
        assertEquals(listOf(lan), connections.addresses(id))
        assertEquals(id, library.sources().single().id)
    }

    @Test
    fun anotherProfilesAccountOnTheSameServerIsNotCombined() {
        val main = library.activeProfile()
        val alex = connect(lan).sourceId
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        val sams = connect(tailscale, user = "sam").sourceId
        assertTrue(alex != sams, "another user is another source")
        assertEquals(listOf(tailscale), connections.addresses(sams))
        library.switchProfile(main)
        assertEquals(listOf(lan), connections.addresses(alex), "Alex's connection is as it was")
        assertTrue(loggedOut.isEmpty(), "nobody was signed out")
    }
}
