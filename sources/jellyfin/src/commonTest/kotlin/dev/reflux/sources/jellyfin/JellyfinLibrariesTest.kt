package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SourceSection
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A server's libraries: every one is read, and each entry says which it is in. */
class JellyfinLibrariesTest {
    private var oldServer = false
    private var online = true
    private val asked = mutableListOf<String>()

    private fun movie(id: String, name: String) =
        """{"Id":"$id","Name":"$name","Type":"Movie","ProductionYear":2001,"MediaSources":[{"Id":"$id","Container":"mkv","Size":1,"MediaStreams":[]}]}"""

    private val http = HttpFetcher { request ->
        if (!online) error("offline")
        val path = request.url.substringAfter("jf.local/").substringBefore('?')
        val query = request.url.substringAfter('?', "")
        asked += path + "?" + query
        val views = """{"Items":[
            {"Id":"lib-m","Name":"Movies","CollectionType":"movies"},
            {"Id":"lib-k","Name":"Kids","CollectionType":"movies"},
            {"Id":"lib-x","Name":"Everything"},
            {"Id":"lib-music","Name":"Music","CollectionType":"music"},
            {"Id":"lib-s","Name":"TV Shows","CollectionType":"tvshows"}]}"""
        val body = when {
            path == "UserViews" -> if (oldServer) return@HttpFetcher HttpResult(404, ByteArray(0)) else views
            path == "Users/u1/Views" -> views
            path == "Items" && "IncludeItemTypes=Series" in query -> """{"Items":[],"TotalRecordCount":0}"""
            path == "Items" && "StartIndex=0" in query -> when {
                "ParentId=lib-m" in query -> """{"Items":[${movie("a", "Heat")}],"TotalRecordCount":1}"""
                "ParentId=lib-k" in query -> """{"Items":[${movie("b", "Paddington")}],"TotalRecordCount":1}"""
                // A film in two libraries is one film.
                "ParentId=lib-x" in query -> """{"Items":[${movie("a", "Heat")},${movie("c", "Alien")}],"TotalRecordCount":2}"""
                "ParentId" in query -> """{"Items":[],"TotalRecordCount":0}"""
                else -> """{"Items":[${movie("a", "Heat")},${movie("b", "Paddington")},${movie("c", "Alien")}],"TotalRecordCount":3}"""
            }
            path == "Items" -> """{"Items":[],"TotalRecordCount":0}"""
            else -> "{}"
        }
        HttpResult(200, body.encodeToByteArray())
    }

    private fun source(excluded: Set<String> = emptySet()) =
        JellyfinSource(JellyfinCredentials("http://jf.local", "srv1", "u1", "tok", "dev1", "alex", excluded), http)

    private suspend fun titles(source: JellyfinSource) = source.catalog().toList().map { it.identity.title }.sorted()

    @Test
    fun onlyLibrariesWithFilmsOrSeriesAreOffered() = runTest {
        assertEquals(
            listOf(
                SourceSection("lib-m", "Movies", SectionKind.MOVIES),
                SourceSection("lib-k", "Kids", SectionKind.MOVIES),
                SourceSection("lib-x", "Everything", SectionKind.MIXED),
                SourceSection("lib-s", "TV Shows", SectionKind.SHOWS),
            ),
            source().sections(),
        )
        oldServer = true
        assertEquals(4, source().sections().size, "an older server is asked at its older address")
    }

    @Test
    fun everyLibraryIsReadAndEachEntrySaysWhichLibraryItIsIn() = runTest {
        val entries = source().catalog().toList()
        assertEquals(listOf("Alien", "Heat", "Paddington"), entries.map { it.identity.title }.sorted())
        assertEquals("lib-m", entries.single { it.identity.title == "Heat" }.section, "a film in two libraries is in the first")
        assertEquals("lib-k", entries.single { it.identity.title == "Paddington" }.section)
        assertEquals("lib-x", entries.single { it.identity.title == "Alien" }.section)
        assertTrue(asked.any { "ParentId=lib-k" in it }, "every library is read, whatever takes part in the unified library")
        asked.clear()
        // What a connection left out before M3 is not the adapter's to apply any more: the library decides.
        assertEquals(listOf("Alien", "Heat", "Paddington"), titles(source(setOf("lib-k"))))
    }

    @Test
    fun librariesLeftOutBeforeAreHandedToTheLibraryOnceAndClearedFromTheConnection() {
        val config = JellyfinCredentials("http://jf.local", "srv1", "u1", "tok", "dev1", "alex", setOf("lib-k", "lib-s")).encode()
        val connector = JellyfinConnector(http, "dev1")
        assertEquals(setOf("lib-k", "lib-s"), connector.excludedSections(config))
        val cleared = connector.withoutExcludedSections(config)!!
        val decoded = JellyfinCredentials.decode(cleared)!!
        assertEquals(emptySet(), decoded.excludedLibraries)
        assertEquals("tok", decoded.accessToken, "nothing else about the connection changes")
        assertEquals(null, connector.withoutExcludedSections(cleared), "a connection with nothing left out is not rewritten")
        assertEquals(source(setOf("lib-k", "lib-s")).descriptor.id, source().descriptor.id, "the same source, whatever it included")
    }

    @Test
    fun aServerThatIsAwayCannotBeAskedForItsLibraries() = runTest {
        online = false
        assertFailsWith<SourceUnavailableException> { source().sections() }
        assertFailsWith<SourceUnavailableException> { source(setOf("lib-k")).catalog().toList() }
    }

    @Test
    fun aServerThatNoLongerAcceptsTheTokenNeedsSigningInAgain() = runTest {
        val refusing = JellyfinSource(
            JellyfinCredentials("http://jf.local", "srv1", "u1", "tok", "dev1", "alex"),
            HttpFetcher { HttpResult(401, ByteArray(0)) },
        )
        assertFailsWith<dev.reflux.core.source.SourceSignInRequiredException> { refusing.sections() }
        assertFailsWith<dev.reflux.core.source.SourceSignInRequiredException> { refusing.catalog().toList() }
    }
}
