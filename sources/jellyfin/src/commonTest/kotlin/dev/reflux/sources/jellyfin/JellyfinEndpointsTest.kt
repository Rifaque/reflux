package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.model.Availability
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-003: one Jellyfin server and account reached at home through its LAN address and away through Tailscale is one
 * connection that knows both, prefers home, falls back, and comes back.
 */
class JellyfinEndpointsTest {
    private val lan = "http://192.168.1.20:8096"
    private val tailscale = "http://100.101.102.103:8096"

    /** Which of the server's addresses answer now. */
    private val reachable = mutableSetOf(lan, tailscale)
    private val asked = mutableListOf<String>()
    private var tokens = 0

    /** One server (srv1), one account (alex / secret), at both addresses; every sign-in gets a token of its own. */
    private val http = HttpFetcher { request ->
        val base = listOf(lan, tailscale).firstOrNull { request.url.startsWith("$it/") } ?: error("no route to host")
        asked += base
        if (base !in reachable) error("no route to host")
        when (request.url.removePrefix("$base/").substringBefore('?')) {
            "System/Info/Public" -> HttpResult(200, """{"ServerName":"Home","Version":"10.10.3","Id":"srv1"}""".encodeToByteArray())
            "Users/AuthenticateByName" ->
                if ("\"Pw\":\"secret\"" in request.body!!.decodeToString()) {
                    tokens++
                    HttpResult(200, """{"AccessToken":"tok$tokens","ServerId":"srv1","User":{"Id":"u1","Name":"Alex"}}""".encodeToByteArray())
                } else {
                    HttpResult(401, ByteArray(0))
                }
            "Users/Me" -> HttpResult(200, """{"Id":"u1"}""".encodeToByteArray())
            else -> HttpResult(404, ByteArray(0))
        }
    }
    private val connector = JellyfinConnector(http, "device-1")

    private suspend fun signIn(address: String) = assertIs<Connection.Established>(connector.connect(ConnectionRequest(address, "alex", "secret"))).config

    @Test
    fun aSecondAddressOfTheSameAccountIsAddedNotSwapped() = runTest {
        val home = signIn(lan)
        val away = signIn(tailscale)
        val combined = JellyfinCredentials.decode(connector.combine(home, away))!!
        assertEquals(listOf(lan, tailscale), combined.addresses, "both, home first, whichever was added first")
        assertEquals(lan, combined.serverUrl)
        assertEquals("tok2", combined.accessToken, "the sign-in just accepted is the one kept")
        assertEquals(JellyfinCredentials.decode(away)!!.deviceId, combined.deviceId, "with the device it signed in as")
        assertEquals(listOf(lan, tailscale), connector.addresses(connector.combine(home, away)))
        // The other order, Tailscale first: home is still preferred.
        assertEquals(listOf(lan, tailscale), JellyfinCredentials.decode(connector.combine(away, signIn(lan)))!!.addresses)
        // The same identity either way: one source, never two.
        val first = JellyfinSource(JellyfinCredentials.decode(home)!!, http)
        val both = JellyfinSource(combined, http)
        assertEquals(first.descriptor.id, both.descriptor.id)
        // Signing in again at an address it already has keeps the others.
        assertEquals(listOf(lan, tailscale), JellyfinCredentials.decode(connector.combine(connector.combine(home, away), signIn(lan)))!!.addresses)
    }

    @Test
    fun anotherServerOrAnotherUserIsNeverCombined() {
        val alex = JellyfinCredentials(lan, "srv1", "u1", "a", "d1")
        val sam = JellyfinCredentials(tailscale, "srv1", "u2", "b", "d2")
        val other = JellyfinCredentials(tailscale, "srv2", "u1", "c", "d3")
        assertEquals(sam.encode(), connector.combine(alex.encode(), sam.encode()))
        assertEquals(other.encode(), connector.combine(alex.encode(), other.encode()))
    }

    @Test
    fun aConnectionStoredBeforeHasOneAddressAndTheNewFormReadsBack() {
        // As M8 to 0.9.0-beta.1 stored it: no alternates.
        val stored = "server=$lan\nserverId=srv1\nuser=u1\ntoken=tok\ndevice=d1\nname=Alex"
        val old = JellyfinCredentials.decode(stored)!!
        assertEquals(listOf(lan), old.addresses)
        assertEquals(stored, old.encode(), "written back exactly as it was")
        val both = old.at(listOf(tailscale, lan))
        assertEquals(both, JellyfinCredentials.decode(both.encode()))
        assertTrue("alt=$tailscale" in both.encode())
        assertFalse("tok" in both.toString(), "the token stays out of anything printed")
    }

    @Test
    fun homeIsUsedThenTailscaleWhenHomeIsAwayThenHomeAgain() = runTest {
        var now = 0L
        val credentials = JellyfinCredentials(tailscale, "srv1", "u1", "tok", "d1", alternates = listOf(lan))
        val selector = dev.reflux.core.net.EndpointSelector(credentials.addresses, { now })
        val source = JellyfinSource(credentials, http, selector = selector)
        assertEquals(Availability.AVAILABLE, source.availability())
        assertTrue(source.playbackTarget("items/i1/sources/s1").uri.startsWith("$lan/"), "at home: the LAN address")

        // Away: the LAN address does not answer, and everything goes through Tailscale, the stream included.
        reachable -= lan
        asked.clear()
        assertEquals(Availability.AVAILABLE, source.availability())
        assertEquals(listOf(lan, tailscale), asked)
        assertTrue(source.playbackTarget("items/i1/sources/s1").uri.startsWith("$tailscale/"))
        asked.clear()
        runCatching { source.sections() }
        assertTrue(asked.all { it == tailscale }, "a request while away does not wait for home first: $asked")
        assertEquals(listOf(false, true), source.addressHealth().map { it.inUse })

        // Home again: the next check a minute on finds the LAN address and goes back to it.
        reachable += lan
        now += 61_000
        assertEquals(Availability.AVAILABLE, source.availability())
        assertTrue(source.playbackTarget("items/i1/sources/s1").uri.startsWith("$lan/"))

        // Neither answers: unavailable, as before.
        reachable.clear()
        assertEquals(Availability.UNAVAILABLE, source.availability())
    }

    @Test
    fun anHttpErrorIsTheServersAnswerAndIsNotFailedOver() = runTest {
        val credentials = JellyfinCredentials(lan, "srv1", "u1", "tok", "d1", alternates = listOf(tailscale))
        val refusing = HttpFetcher { request -> asked += request.url; HttpResult(401, ByteArray(0)) }
        val source = JellyfinSource(credentials, refusing)
        runCatching { source.sections() }
        assertTrue(asked.isNotEmpty() && asked.all { it.startsWith(lan) }, "the same server would say the same at its other address: $asked")
    }

    @Test
    fun oneAddressCanBeRemovedAndTheLastCannot() {
        val both = JellyfinCredentials(lan, "srv1", "u1", "tok", "d1", alternates = listOf(tailscale)).encode()
        val homeOnly = JellyfinCredentials.decode(connector.withoutAddress(both, "$tailscale/")!!)!!
        assertEquals(listOf(lan), homeOnly.addresses)
        assertEquals("tok", homeOnly.accessToken, "the account stays signed in")
        assertEquals(listOf(tailscale), JellyfinCredentials.decode(connector.withoutAddress(both, lan)!!)!!.addresses)
        assertNull(connector.withoutAddress(homeOnly.encode(), lan), "a server keeps at least one address")
        assertNull(connector.withoutAddress(both, "http://elsewhere:8096"))
    }

    @Test
    fun endingAReplacedSessionNeverEndsTheOneKeptAtAnotherAddress() = runTest {
        val kept = JellyfinCredentials(lan, "srv1", "u1", "same", "d1", alternates = listOf(tailscale)).encode()
        val replaced = JellyfinCredentials(tailscale, "srv1", "u1", "same", "d1").encode()
        assertFalse(connector.signOut(replaced, keeping = kept), "the same token, kept at another address, is left alone")
    }
}
