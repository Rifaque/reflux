package dev.reflux.library

import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The gate between Reflux and a metadata provider: one question asked once, no storm, a rest after failing. */
class ProviderGateTest {
    private var time = 0L
    private val provider = FakeMetadataProvider().apply { movie("603", "The Matrix", 1999) }
    private val gate = ProviderGate(provider, spacingMs = 0, clock = { time })
    private val ref = ProviderRef("tmdb", MetadataKind.MOVIE, "603")

    @Test
    fun theSameQuestionAtOnceIsAskedOnce() = runTest {
        val answers = (1..20).map { async { gate.details(ref, "en-US") } }.awaitAll()
        assertEquals(1, provider.calls.count { it == "details:603" })
        assertEquals(1, answers.map { it?.title }.distinct().size)
    }

    @Test
    fun anAnswerIsRememberedForAWhile() = runTest {
        val query = MetadataQuery(MetadataKind.MOVIE, "The Matrix", 1999, language = "en-US")
        gate.search(query)
        gate.search(query)
        assertEquals(1, gate.requests)
        time += 11 * 60_000
        gate.search(query)
        assertEquals(2, gate.requests, "asked again once forgotten")
        gate.search(query.copy(year = 2003))
        assertEquals(3, gate.requests, "another question is asked")
    }

    @Test
    fun aProviderOutOfReachIsLeftToRestThenTriedAgain() = runTest {
        provider.offline = true
        assertFailsWith<MetadataUnavailableException> { gate.details(ref, "en-US") }
        provider.offline = false
        // While it rests, callers are told at once, without a request.
        assertFailsWith<MetadataUnavailableException> { gate.details(ref, "en-US") }
        assertEquals(1, gate.requests)
        time += 61_000
        assertEquals("The Matrix", gate.details(ref, "en-US")?.title)
        assertEquals(2, gate.requests)
    }

    @Test
    fun theRestGrowsWithEachFailureAndAResetEndsIt() = runTest {
        provider.offline = true
        assertFailsWith<MetadataUnavailableException> { gate.details(ref, "en-US") }
        time += 61_000
        assertFailsWith<MetadataUnavailableException> { gate.details(ref, "en-US") }
        time += 61_000
        assertFailsWith<MetadataUnavailableException> { gate.details(ref, "en-US") }
        assertEquals(2, gate.requests, "the second rest is longer than a minute")
        provider.offline = false
        gate.reset()
        assertEquals("The Matrix", gate.details(ref, "en-US")?.title)
    }

    @Test
    fun requestsAreSpacedApart() = runTest {
        var now = 0L
        val spaced = ProviderGate(provider, spacingMs = 500, clock = { now })
        spaced.details(ref, "en-US")
        val started = testScheduler.currentTime
        now = 100 // 100 ms later, the next has to wait 400
        spaced.season(ref, 1, "en-US")
        assertEquals(400, testScheduler.currentTime - started)
    }
}
