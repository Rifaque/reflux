package dev.reflux.core.telemetry

/**
 * The opt-in technical statistics foundation (Phase 1; docs/DECISIONS.md, 2026-11-18, "Opt-in technical statistics";
 * docs/PRIVACY.md). Nothing here sends anything: the only transport builds have is [TelemetryTransport.Disabled], and
 * a real one is added only once the owner has approved a provider and its data contract.
 *
 * The rules this code enforces, so that no caller can break them:
 * - **Off unless the person turns it on** ([TelemetryConsent], [TelemetryRecorder]): nothing is recorded, queued, or
 *   sent before; turning it off stops it and forgets what was waiting.
 * - **Closed vocabulary.** An event is one of [TelemetryEvent]'s kinds with fields from closed sets (enums) and numbers
 *   put into coarse buckets. There is no free text anywhere in the schema, so a title, a file name or path, an address,
 *   a credential, a profile name, an exception's message, a URL, or a header cannot be put into one.
 * - **No identity.** No installation, device, account, or advertising identifier, and no session identifier that
 *   lasts: an event says what happened on a kind of device, never whose.
 */
sealed interface TelemetryEvent {
    /** Which build: a release channel and the version, which every event carries. */
    val context: TelemetryContext

    /** Start-up: how long until the library could be shown, bucketed; or why it failed, by category. */
    data class Startup(override val context: TelemetryContext, val duration: DurationBucket, val failure: StartupFailure?) : TelemetryEvent

    /** A playback engine could not be started, by category (B-031's kind of failure, counted, not described). */
    data class PlayerInitFailed(override val context: TelemetryContext, val engine: PlaybackEngine, val reason: PlayerInitFailure) : TelemetryEvent

    /** A playback ended: which engine and delivery, whether it failed and by what category. Never what was played. */
    data class PlaybackOutcome(
        override val context: TelemetryContext,
        val engine: PlaybackEngine,
        val delivery: DeliveryKind,
        val sourceKind: SourceKind,
        val failure: PlaybackFailureKind?,
    ) : TelemetryEvent

    /** A source could not be reached or read, by kind of source and category. Never the server, its address, or the account. */
    data class SourceError(override val context: TelemetryContext, val sourceKind: SourceKind, val category: SourceErrorCategory) : TelemetryEvent

    /** How a screen performs: how long it took to show and how many frames were slow, bucketed. */
    data class UiPerformance(override val context: TelemetryContext, val screen: ScreenKind, val shown: DurationBucket, val slowFrames: CountBucket) : TelemetryEvent

    /** Reflux ended unexpectedly; the category, never the stack trace or its messages. */
    data class Crash(override val context: TelemetryContext, val category: CrashCategory) : TelemetryEvent
}

/** The build an event comes from: never more than this. */
data class TelemetryContext(val platform: PlatformKind, val version: String, val channel: ReleaseChannel) {
    init {
        // A version is the build's own (`0.9.0-beta.2`), never anything a person typed.
        require(VERSION.matches(version)) { "not a version" }
    }

    private companion object {
        val VERSION = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}(?:-(?:alpha|beta|rc)\.\d{1,3})?$""")
    }
}

enum class PlatformKind { ANDROID_PHONE, ANDROID_TABLET, ANDROID_TV, FIRE_TV, WINDOWS, LINUX }
enum class ReleaseChannel { DEVELOPMENT, ALPHA, BETA, RELEASE_CANDIDATE, RELEASE }
enum class PlaybackEngine { MEDIA3, LIBMPV }
enum class DeliveryKind { DIRECT_PLAY, DIRECT_STREAM, TRANSCODE, OFFLINE_COPY }
enum class SourceKind { FOLDER, JELLYFIN, PLEX, SMB, WEBDAV, DOWNLOADS }
enum class StartupFailure { LIBRARY_NEWER, LIBRARY_UNREADABLE, OTHER }
enum class PlayerInitFailure { LIBRARY_MISSING, REFUSED_TO_START, INITIALIZE_FAILED, NO_VIDEO_OUTPUT, OTHER }
enum class PlaybackFailureKind { UNREACHABLE, UNSUPPORTED, OUTPUT, DECODER, NETWORK, OTHER }
enum class SourceErrorCategory { UNREACHABLE, SIGN_IN_REJECTED, SERVER_ERROR, TIMEOUT, UNEXPECTED_ANSWER, OTHER }
enum class ScreenKind { HOME, MOVIES, SHOWS, SEARCH, DETAILS, PLAYER, SOURCES, SETTINGS }
enum class CrashCategory { OUT_OF_MEMORY, NATIVE, UNHANDLED, OTHER }

/** A duration in a coarse range: precise times can tell devices apart. */
enum class DurationBucket(val upToMs: Long) {
    UNDER_250_MS(250), UNDER_500_MS(500), UNDER_1_S(1_000), UNDER_2_S(2_000), UNDER_5_S(5_000), UNDER_10_S(10_000), LONGER(Long.MAX_VALUE);

    companion object {
        fun of(ms: Long): DurationBucket = entries.first { ms.coerceAtLeast(0) < it.upToMs }
    }
}

/** A count in a coarse range. */
enum class CountBucket(val upTo: Int) {
    NONE(1), FEW(5), SOME(20), MANY(100), VERY_MANY(Int.MAX_VALUE);

    companion object {
        fun of(count: Int): CountBucket = entries.first { count.coerceAtLeast(0) < it.upTo }
    }
}

/** Whether the person has said yes. [OFF] until they do; nothing is asked of them in the background. */
enum class TelemetryConsent { OFF, ON }

/** Where the person's answer is kept: on the device, for the installation (a technical setting, not a profile's). */
interface ConsentStore {
    fun load(): TelemetryConsent?
    fun save(consent: TelemetryConsent)
}

/**
 * Where events would go. Only [Disabled] exists until a provider is approved (docs/DECISIONS.md): with it, every
 * event is dropped where it stands and nothing touches the network.
 */
fun interface TelemetryTransport {
    /** Sends [events] as one batch; returns whether they were accepted. Never retried in a loop. */
    suspend fun send(events: List<TelemetryEvent>): Boolean

    /** Whether there is anywhere to send to at all; without one, Settings does not offer the choice. */
    val configured: Boolean get() = true

    object Disabled : TelemetryTransport {
        override val configured: Boolean = false
        override suspend fun send(events: List<TelemetryEvent>): Boolean = false
    }
}

/**
 * Records events only with consent, holds at most [capacity] of them (the oldest dropped first), and sends them in one
 * batch when asked ([flush]). A batch that is not accepted is dropped, not retried: statistics are worth less than the
 * network, the battery, and the person's patience. Turning consent off forgets what was waiting.
 */
class TelemetryRecorder(
    private val store: ConsentStore,
    private val transport: TelemetryTransport = TelemetryTransport.Disabled,
    private val capacity: Int = 100,
) {
    /**
     * What is waiting, replaced whole on every change. Two events recorded at once may lose one of them, which costs a
     * statistic and nothing else: whether anything is ever sent is decided by consent at [flush], not by this list.
     */
    @kotlin.concurrent.Volatile
    private var queue: List<TelemetryEvent> = emptyList()

    /** The person's answer; OFF when they never gave one. */
    val consent: TelemetryConsent get() = store.load() ?: TelemetryConsent.OFF

    /** Whether the choice can be offered: only where there is somewhere to send to. */
    val available: Boolean get() = transport.configured

    fun setConsent(consent: TelemetryConsent) {
        store.save(consent)
        if (consent == TelemetryConsent.OFF) queue = emptyList()
    }

    /** Keeps [event] for the next batch, if the person said yes and there is somewhere to send it. Otherwise nothing. */
    fun record(event: TelemetryEvent) {
        if (consent != TelemetryConsent.ON || !transport.configured) return
        queue = (queue + event).takeLast(capacity)
    }

    /** What is waiting, for tests and for showing the person what would be sent. */
    fun pending(): List<TelemetryEvent> = queue

    /** Sends what is waiting, once; whatever happens, it is not kept. Returns how many were accepted. */
    suspend fun flush(): Int {
        val batch = queue
        queue = emptyList()
        if (consent != TelemetryConsent.ON || !transport.configured || batch.isEmpty()) return 0
        val accepted = try {
            transport.send(batch)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        return if (accepted) batch.size else 0
    }
}
