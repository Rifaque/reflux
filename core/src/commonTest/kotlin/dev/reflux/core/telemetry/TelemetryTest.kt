package dev.reflux.core.telemetry

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The opt-in statistics foundation: off means nothing recorded and nothing sent, whatever is asked of it. */
class TelemetryTest {
    private class MemoryConsent(var value: TelemetryConsent? = null) : ConsentStore {
        override fun load() = value
        override fun save(consent: TelemetryConsent) { value = consent }
    }

    /** A transport that counts what it is handed: what an approved provider would get, if one existed. */
    private class Counting(var accept: Boolean = true, var throws: Boolean = false) : TelemetryTransport {
        val batches = mutableListOf<List<TelemetryEvent>>()
        override suspend fun send(events: List<TelemetryEvent>): Boolean {
            batches += events
            if (throws) error("no network")
            return accept
        }
    }

    private val context = TelemetryContext(PlatformKind.LINUX, "0.9.0-beta.2", ReleaseChannel.BETA)
    private val failed = TelemetryEvent.PlayerInitFailed(context, PlaybackEngine.LIBMPV, PlayerInitFailure.REFUSED_TO_START)

    @Test
    fun offByDefaultNothingIsRecordedOrSent() = runTest {
        val transport = Counting()
        val recorder = TelemetryRecorder(MemoryConsent(), transport)
        assertEquals(TelemetryConsent.OFF, recorder.consent, "never asked: off")
        repeat(10) { recorder.record(failed) }
        assertTrue(recorder.pending().isEmpty(), "nothing kept before a yes")
        assertEquals(0, recorder.flush())
        assertTrue(transport.batches.isEmpty(), "nothing handed to any transport")
    }

    @Test
    fun theBuildsTransportSendsNothingEvenWithAYes() = runTest {
        val consent = MemoryConsent(TelemetryConsent.ON)
        val recorder = TelemetryRecorder(consent)
        assertFalse(recorder.available, "no provider is approved: Settings does not offer it")
        recorder.record(failed)
        assertTrue(recorder.pending().isEmpty())
        assertEquals(0, recorder.flush())
        assertFalse(TelemetryTransport.Disabled.configured)
    }

    @Test
    fun aYesSendsOneBatchAndANoStopsAndForgets() = runTest {
        val consent = MemoryConsent()
        val transport = Counting()
        val recorder = TelemetryRecorder(consent, transport)
        recorder.setConsent(TelemetryConsent.ON)
        recorder.record(failed)
        recorder.record(TelemetryEvent.Startup(context, DurationBucket.of(1_200), null))
        assertEquals(2, recorder.flush())
        assertEquals(1, transport.batches.size)
        assertEquals(0, recorder.flush(), "sent once, not kept")
        // No: what was waiting is forgotten, and nothing more is kept or sent.
        recorder.record(failed)
        recorder.setConsent(TelemetryConsent.OFF)
        assertTrue(recorder.pending().isEmpty())
        recorder.record(failed)
        assertEquals(0, recorder.flush())
        assertEquals(1, transport.batches.size)
        assertEquals(TelemetryConsent.OFF, consent.value, "the answer is kept on the device")
    }

    @Test
    fun aRefusedOrFailedBatchIsDroppedNotRetriedAndTheQueueIsBounded() = runTest {
        val transport = Counting(accept = false)
        val recorder = TelemetryRecorder(MemoryConsent(TelemetryConsent.ON), transport, capacity = 3)
        repeat(10) { recorder.record(TelemetryEvent.SourceError(context, SourceKind.JELLYFIN, SourceErrorCategory.UNREACHABLE)) }
        assertEquals(3, recorder.pending().size, "at most what it may hold")
        assertEquals(0, recorder.flush())
        assertEquals(0, recorder.flush())
        assertEquals(1, transport.batches.size, "one attempt, then dropped")
        transport.throws = true
        recorder.record(failed)
        assertEquals(0, recorder.flush(), "offline: dropped, not thrown")
        assertTrue(recorder.pending().isEmpty())
    }

    @Test
    fun numbersAreBucketedAndAVersionIsTheOnlyText() {
        assertEquals(DurationBucket.UNDER_250_MS, DurationBucket.of(-5))
        assertEquals(DurationBucket.UNDER_2_S, DurationBucket.of(1_999))
        assertEquals(DurationBucket.LONGER, DurationBucket.of(600_000))
        assertEquals(CountBucket.NONE, CountBucket.of(0))
        assertEquals(CountBucket.MANY, CountBucket.of(99))
        for (notAVersion in listOf("Heat (1995)", "http://192.168.1.20:8096", "/home/alex/Movies", "0.9.0-beta.1 alex", "")) {
            assertFailsWith<IllegalArgumentException>(notAVersion) { TelemetryContext(PlatformKind.WINDOWS, notAVersion, ReleaseChannel.BETA) }
        }
        TelemetryContext(PlatformKind.FIRE_TV, "1.0.0", ReleaseChannel.RELEASE)
    }
}
