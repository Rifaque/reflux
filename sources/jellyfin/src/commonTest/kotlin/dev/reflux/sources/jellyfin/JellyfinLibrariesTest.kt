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

/** Choosing which of a server's libraries Reflux reads. */
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
    fun aLibraryLeftOutIsNotReadAndEverythingElseIsReadOnce() = runTest {
        assertEquals(listOf("Alien", "Heat", "Paddington"), titles(source()))
        assertTrue(asked.none { "ParentId" in it }, "with nothing left out, the whole catalog is asked for at once")
        asked.clear()
        assertEquals(listOf("Alien", "Heat"), titles(source(setOf("lib-k"))))
        assertTrue(asked.none { "ParentId=lib-k" in it }, "a library left out is never read")
        // A library the server no longer has is simply not there; one it gained is included.
        assertEquals(listOf("Alien", "Heat", "Paddington"), titles(source(setOf("lib-gone"))))
    }

    @Test
    fun theChoiceIsKeptWithTheConnectionAndNothingElseChanges() {
        val config = JellyfinCredentials("http://jf.local", "srv1", "u1", "tok", "dev1", "alex").encode()
        val changed = JellyfinConnector(http, "dev1").withExcludedSections(config, setOf("lib-k", "lib-s"))!!
        val decoded = JellyfinCredentials.decode(changed)!!
        assertEquals(setOf("lib-k", "lib-s"), decoded.excludedLibraries)
        assertEquals("tok", decoded.accessToken)
        assertEquals(source(setOf("lib-k", "lib-s")).descriptor.id, source().descriptor.id, "the same source, whatever it includes")
        assertEquals(config, JellyfinConnector(http, "dev1").withExcludedSections(changed, emptySet()))
    }

    @Test
    fun aServerThatIsAwayCannotBeAskedForItsLibraries() = runTest {
        online = false
        assertFailsWith<SourceUnavailableException> { source().sections() }
        assertFailsWith<SourceUnavailableException> { source(setOf("lib-k")).catalog().toList() }
    }
}
