package dev.reflux.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import dev.reflux.core.BuildIdentity
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Chapter
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.TrackType
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.SourceRegistry
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.library.Offline
import dev.reflux.library.SourceConnections
import dev.reflux.ui.legal.NoticeTarget
import dev.reflux.core.offline.StorageSnapshot
import dev.reflux.library.DownloadStore
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.FolderBrowser
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.FullscreenMode
import dev.reflux.ui.state.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.math.absoluteValue

/** A real library over a temporary media folder: real scanning and identification, a fake metadata provider. */
class TestWorld(metadata: Boolean = true) {
    private val root: Path = Files.createTempDirectory("reflux-ui").also { it.toFile().deleteOnExit() }
    private val media: Path = root.resolve("Media").createDirectories()
    var clock = 1_790_000_000_000L
    val library = Library(LibraryDatabase.open(root.resolve("data/library.db"))) { clock }
    val source = LocalFolderSource(media, "Media")
    /** How sources are recreated from what the library stored; tests add the kinds they connect. */
    val factories = mutableMapOf<String, (dev.reflux.library.SourceRecord) -> dev.reflux.core.source.MediaSource?>()
    val registry = SourceRegistry(factories).apply { register(source) }
    val pipeline = LibraryPipeline(library, registry, metadata = FakeCatalog.takeIf { metadata })

    init {
        library.addSource(source, media.toString())
    }

    fun file(relative: String) {
        media.resolve(relative).also { it.parent.createDirectories() }.writeBytes(ByteArray(8))
    }

    /** A small but varied library: movies with several versions and qualities, and two shows. */
    suspend fun populate() {
        file("Heat (1995)/Heat.1995.1080p.BluRay.x264.mkv")
        file("Heat (1995)/Heat.1995.2160p.UHD.HDR.x265.mkv")
        file("Blade Runner 2049 (2017)/Blade.Runner.2049.2017.2160p.DV.TrueHD.Atmos.x265.mkv")
        file("Arrival (2016)/Arrival.2016.1080p.x264.mkv")
        file("Dune (2021)/Dune.2021.2160p.HDR.x265.mkv")
        file("Interstellar (2014)/Interstellar.2014.2160p.HDR.x265.mkv")
        file("Mad Max Fury Road (2015)/Mad.Max.Fury.Road.2015.2160p.HDR.x265.mkv")
        file("Alien (1979).mkv")
        file("The Matrix (1999).mkv")
        file("Parasite (2019).mkv")
        file("Whiplash (2014).mkv")
        for (episode in 1..5) file("Breaking Bad/Season 1/Breaking.Bad.S01E0$episode.1080p.mkv")
        for (episode in 1..3) file("Breaking Bad/Season 2/Breaking.Bad.S02E0$episode.1080p.mkv")
        for (episode in 1..4) file("Severance/Season 1/Severance.S01E0$episode.2160p.mkv")
        pipeline.refresh()
    }

    fun movie(title: String) = library.movies().first { it.item.title == title }

    fun show(title: String) = library.shows().first { it.item.title == title }

    fun episodes(title: String) = library.showDetail(show(title).item.id)!!.seasons.flatMap { it.episodes }
}

/** A tiny provider catalog covering [TestWorld.populate]. Artwork URLs are resolved by [FakeArtwork]. */
object FakeCatalog : MetadataProvider {
    override val id = "tmdb"
    override val attribution = TmdbProvider.ATTRIBUTION

    private class Work(
        val key: String,
        val kind: MetadataKind,
        val title: String,
        val year: Int,
        val genres: List<String>,
        val runtime: Int,
        val rating: Double,
        val overview: String,
        val people: List<Pair<String, CreditRole>>,
        val episodes: Map<Int, List<String>> = emptyMap(),
    )

    private val works = listOf(
        Work("1", MetadataKind.MOVIE, "Heat", 1995, listOf("Crime", "Drama"), 170, 8.3, "A master thief and the detective obsessed with catching him circle each other across Los Angeles, each recognizing the other as the only equal he has.", listOf("Michael Mann" to CreditRole.DIRECTOR, "Al Pacino" to CreditRole.ACTOR, "Robert De Niro" to CreditRole.ACTOR, "Val Kilmer" to CreditRole.ACTOR)),
        Work("2", MetadataKind.MOVIE, "Blade Runner 2049", 2017, listOf("Science Fiction", "Drama"), 164, 8.0, "A young blade runner unearths a long-buried secret that leads him to track down a former blade runner who has been missing for thirty years.", listOf("Denis Villeneuve" to CreditRole.DIRECTOR, "Ryan Gosling" to CreditRole.ACTOR, "Harrison Ford" to CreditRole.ACTOR)),
        Work("3", MetadataKind.MOVIE, "Arrival", 2016, listOf("Science Fiction", "Drama"), 116, 7.9, "A linguist is recruited to find a way to communicate with visitors whose arrival has divided the world.", listOf("Denis Villeneuve" to CreditRole.DIRECTOR, "Amy Adams" to CreditRole.ACTOR)),
        Work("4", MetadataKind.MOVIE, "Dune", 2021, listOf("Science Fiction", "Adventure"), 155, 8.0, "Paul Atreides travels to the most dangerous planet in the universe to secure the future of his family and his people.", listOf("Denis Villeneuve" to CreditRole.DIRECTOR, "Timothée Chalamet" to CreditRole.ACTOR)),
        // Another film of the same name, for choosing between them.
        Work("41", MetadataKind.MOVIE, "Dune", 1984, listOf("Science Fiction"), 137, 6.3, "A duke's son leads desert warriors against the galactic emperor and his father's evil nemesis.", listOf("David Lynch" to CreditRole.DIRECTOR, "Kyle MacLachlan" to CreditRole.ACTOR)),
        Work("5", MetadataKind.MOVIE, "Interstellar", 2014, listOf("Science Fiction", "Adventure"), 169, 8.4, "A team of explorers travels through a wormhole in search of a new home for humanity.", listOf("Christopher Nolan" to CreditRole.DIRECTOR, "Matthew McConaughey" to CreditRole.ACTOR)),
        Work("6", MetadataKind.MOVIE, "Mad Max: Fury Road", 2015, listOf("Action", "Adventure"), 120, 7.6, "In a wasteland where water and fuel are power, a drifter and a rebel commander flee a tyrant across the desert.", listOf("George Miller" to CreditRole.DIRECTOR, "Tom Hardy" to CreditRole.ACTOR)),
        Work("7", MetadataKind.MOVIE, "Alien", 1979, listOf("Horror", "Science Fiction"), 117, 8.2, "The crew of a commercial spacecraft answers a distress call and brings something back aboard.", listOf("Ridley Scott" to CreditRole.DIRECTOR, "Sigourney Weaver" to CreditRole.ACTOR)),
        Work("8", MetadataKind.MOVIE, "The Matrix", 1999, listOf("Action", "Science Fiction"), 136, 8.2, "A hacker learns the world he knows is a simulation, and joins the people fighting to wake everyone else.", listOf("Lana Wachowski" to CreditRole.DIRECTOR, "Keanu Reeves" to CreditRole.ACTOR)),
        Work("9", MetadataKind.MOVIE, "Parasite", 2019, listOf("Thriller", "Drama"), 133, 8.5, "A struggling family talks its way into the household of a wealthy one, until an unexpected incident upends everything.", listOf("Bong Joon-ho" to CreditRole.DIRECTOR, "Song Kang-ho" to CreditRole.ACTOR)),
        Work("10", MetadataKind.MOVIE, "Whiplash", 2014, listOf("Drama", "Music"), 107, 8.4, "A young drummer enrolls at a cut-throat conservatory where an instructor will stop at nothing to realize a student's potential.", listOf("Damien Chazelle" to CreditRole.DIRECTOR, "Miles Teller" to CreditRole.ACTOR)),
        Work(
            "20", MetadataKind.SHOW, "Breaking Bad", 2008, listOf("Drama", "Crime"), 47, 8.9,
            "A chemistry teacher diagnosed with cancer turns to making methamphetamine to secure his family's future.",
            listOf("Vince Gilligan" to CreditRole.CREATOR, "Bryan Cranston" to CreditRole.ACTOR, "Aaron Paul" to CreditRole.ACTOR),
            mapOf(
                1 to listOf("Pilot", "Cat's in the Bag...", "...And the Bag's in the River", "Cancer Man", "Gray Matter"),
                2 to listOf("Seven Thirty-Seven", "Grilled", "Bit by a Dead Bee"),
            ),
        ),
        Work(
            "21", MetadataKind.SHOW, "Severance", 2022, listOf("Drama", "Mystery"), 50, 8.4,
            "Office workers whose memories are surgically divided between work and home begin to uncover what their employer is hiding.",
            listOf("Dan Erickson" to CreditRole.CREATOR, "Adam Scott" to CreditRole.ACTOR),
            mapOf(1 to listOf("Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are")),
        ),
    )

    private fun ref(work: Work) = ProviderRef(id, work.kind, work.key)

    private fun normalize(title: String) = title.lowercase().filter { it.isLetterOrDigit() }

    /** Every search asked of the catalog, by title, in order. */
    val searches: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** While set, the catalog cannot be reached, as when the device is offline. */
    @Volatile
    var unreachable = false

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        if (unreachable) throw MetadataUnavailableException("offline")
        searches += query.title
        return works.filter { it.kind == query.kind && normalize(it.title) == normalize(query.title) }
            .map { MetadataCandidate(ref(it), it.title, year = it.year, overview = it.overview, popularity = 50.0, posterUrl = "art://result/${it.title}/${it.year}") }
    }

    override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? = null

    override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? {
        val work = works.firstOrNull { ref(it) == ref } ?: return null
        return WorkMetadata(
            ref = ref,
            title = work.title,
            overview = work.overview,
            genres = work.genres,
            runtimeMinutes = work.runtime,
            releaseDate = CalendarDate(work.year, 6, 1),
            rating = work.rating,
            contentRating = if (work.kind == MetadataKind.MOVIE) "R" else "TV-MA",
            credits = work.people.map { (name, role) -> Credit(name, role, character = "Character".takeIf { role == CreditRole.ACTOR }) },
            artwork = listOf(
                RemoteArtwork(ArtworkKind.POSTER, "art://poster/${work.title}"),
                RemoteArtwork(ArtworkKind.BACKDROP, "art://backdrop/${work.title}"),
            ),
        )
    }

    override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? {
        val work = works.firstOrNull { ref(it) == ref } ?: return null
        val titles = work.episodes[seasonNumber] ?: return null
        return SeasonMetadata(
            number = seasonNumber,
            title = "Season $seasonNumber",
            episodes = titles.mapIndexed { index, title ->
                EpisodeMetadata(seasonNumber, index + 1, title, overview = "In which $title happens.", runtimeMinutes = work.runtime)
            },
        )
    }
}

/**
 * Paints a distinct image for every artwork URL and every image file in a source, standing in for posters and
 * backdrops. [backdrops] false leaves works with posters only, [posters] false with backdrops only: the other
 * image is there but cannot be read.
 */
class FakeArtwork(private val enabled: Boolean = true, private val backdrops: Boolean = true, private val posters: Boolean = true) : ArtworkLoader {
    private val images = mutableMapOf<Pair<ArtworkLocator, Int>, ImageBitmap>()

    /** Every image handed out, as its URL and the width in pixels it was asked for. */
    val served: List<Pair<String, Int>> get() = synchronized(images) { images.keys.map { name(it.first) to it.second } }

    private fun name(locator: ArtworkLocator) = when (locator) {
        is ArtworkLocator.Remote -> locator.url
        is ArtworkLocator.SourceFile -> "file:${locator.location.path}"
    }

    override fun cached(locator: ArtworkLocator, widthPx: Int): ImageBitmap? = synchronized(images) { images[locator to widthPx] }

    override suspend fun load(locator: ArtworkLocator, widthPx: Int): ImageBitmap? {
        val name = name(locator)
        if (!enabled || (!backdrops && "backdrop" in name) || (!posters && "poster" in name)) return null
        return synchronized(images) { images.getOrPut(locator to widthPx) { paint(name, widthPx) } }
    }

    /** An image already painted for a URL containing [part]. */
    fun painted(part: String): ImageBitmap? = synchronized(images) { images.entries.firstOrNull { (it.key.first as? ArtworkLocator.Remote)?.url?.contains(part) == true }?.value }

    private fun paint(url: String, widthPx: Int): ImageBitmap {
        val wide = "backdrop" in url || "fanart" in url
        val width = widthPx.coerceIn(64, 1600)
        val height = if (wide) width * 9 / 16 else width * 3 / 2
        val hue = (url.hashCode().absoluteValue % 360).toFloat()
        val image = ImageBitmap(width, height)
        val canvas = Canvas(image)
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint().apply {
                shader = LinearGradientShader(
                    Offset.Zero, Offset(width.toFloat(), height.toFloat()),
                    listOf(Color.hsl(hue, 0.65f, 0.58f), Color.hsl((hue + 50f) % 360f, 0.6f, 0.22f)),
                )
            },
        )
        canvas.drawCircle(Offset(width * 0.7f, height * 0.3f), width * 0.22f, Paint().apply { color = Color.hsl((hue + 180f) % 360f, 0.7f, 0.7f, 0.55f) })
        return image
    }
}

val testDevice = DeviceCapabilities(
    videoDecoders = VideoCodec.entries.map { VideoDecoderSupport(it, 3840, 2160, 10, hardware = true) },
    audioDecoders = AudioCodec.entries.toSet(),
    display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR, DynamicRange.HDR10)),
    containers = Container.entries.toSet(),
    subtitleFormats = SubtitleFormat.entries.toSet(),
)

class FakePlatform(
    artwork: Boolean = true,
    backdrops: Boolean = true,
    posters: Boolean = true,
    override val television: Boolean = false,
    override val systemVolume: Boolean = television,
    override val fullscreenMode: FullscreenMode = if (television) FullscreenMode.NONE else FullscreenMode.WINDOW,
    override val folderBrowser: FolderBrowser? = null,
    override val offline: Offline? = null,
    override val connections: SourceConnections? = null,
    override val build: BuildIdentity = BuildIdentity(commit = "8bd4bba"),
    override val notices: NoticeTarget? = NoticeTarget.DESKTOP,
) : Platform {
    override val artwork: FakeArtwork = FakeArtwork(artwork, backdrops, posters)
    override var device: DeviceCapabilities = testDevice
    override val preferredLanguages: List<String> = listOf("en")
    override var canPlay: Boolean = true
    override var accessibility: AccessibilityPreferences by mutableStateOf(AccessibilityPreferences())

    val players = mutableListOf<FakePlayer>()
    val released = mutableListOf<Player>()
    val removed = mutableListOf<SourceId>()
    val reports = mutableListOf<String>()
    var chooseFolder: suspend () -> FolderChoice = { FolderChoice.Cancelled }
    var failToStart = false

    /** False gives every new engine a file it accepts and never gets anywhere with ([FakePlayer.starts]). */
    var starts = true

    override suspend fun createPlayer(): Player? = if (failToStart) null else FakePlayer(device).also { it.starts = starts; players += it }

    /** Completes when the engine may go; lets a test hold a player in the middle of being released. */
    var releaseGate: CompletableDeferred<Unit>? = null

    override suspend fun releasePlayer(player: Player) {
        releaseGate?.await()
        released += player
        player.close()
    }

    override suspend fun addMediaFolder(): FolderChoice = chooseFolder()

    var remove: suspend (SourceId) -> Unit = {}

    override suspend fun removeSource(id: SourceId) {
        removed += id
        remove(id)
    }

    override var fullscreen: Boolean by mutableStateOf(false)
        private set

    override fun requestFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
    }

    override fun report(problem: String, cause: Throwable?) {
        reports += problem
    }
}

/** Downloads on a disk with plenty of room, whatever the disk of the machine running the tests has left. */
class RoomyStore(store: DownloadStore) : DownloadStore by store {
    override fun storage() = StorageSnapshot(freeBytes = 400_000_000_000, totalBytes = 500_000_000_000)
}

/** A playback engine that plays instantly and goes wherever the test tells it to. */
class FakePlayer(override val capabilities: DeviceCapabilities) : Player {
    private val mutable = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutable.asStateFlow()
    val requests = mutableListOf<PlaybackRequest>()
    var closed = false

    var durationMs = 50 * 60_000L
    var tracks = listOf(
        PlayerTrack("1", TrackType.AUDIO, language = "en", channels = 6, default = true, position = 0),
        PlayerTrack("2", TrackType.AUDIO, language = "fr", channels = 2, position = 1),
        PlayerTrack("1", TrackType.SUBTITLE, language = "en"),
        PlayerTrack("2", TrackType.SUBTITLE, language = "es", forced = true),
    )
    var chapters = listOf(Chapter("Intro", 60_000), Chapter("Part One", 150_000), Chapter("Part Two", 25 * 60_000L), Chapter("Credits", 47 * 60_000L))

    /** False makes the engine accept a file and then never get anywhere with it. */
    var starts = true

    /** A subtitle the source was making never came. */
    fun loseSubtitle() {
        mutable.value = mutable.value.copy(subtitlesNotLoaded = mutable.value.subtitlesNotLoaded + 1)
    }

    override fun load(request: PlaybackRequest) {
        requests += request
        if (!starts) return mutable.update { PlayerState(status = PlayerStatus.LOADING, volume = it.volume, muted = it.muted) }
        mutable.update {
            PlayerState(
                status = PlayerStatus.PLAYING, positionMs = request.startPositionMs ?: 0, durationMs = durationMs,
                volume = it.volume, muted = it.muted, tracks = tracks, selectedAudioId = "1", chapters = chapters,
            )
        }
    }

    override fun play() = mutable.update { it.copy(status = PlayerStatus.PLAYING) }
    override fun pause() = mutable.update { it.copy(status = PlayerStatus.PAUSED) }
    override fun seekTo(positionMs: Long) = mutable.update { it.copy(positionMs = positionMs) }
    override fun setSpeed(speed: Double) = mutable.update { it.copy(speed = speed) }
    override fun setVolume(volume: Int) = mutable.update { it.copy(volume = volume) }
    override fun setMuted(muted: Boolean) = mutable.update { it.copy(muted = muted) }
    override fun selectAudio(trackId: String) = mutable.update { it.copy(selectedAudioId = trackId) }
    override fun selectNoAudio() = mutable.update { it.copy(selectedAudioId = null) }
    override fun selectSubtitle(trackId: String?) = mutable.update { it.copy(selectedSubtitleId = trackId) }

    /** Where subtitles were last asked to stay clear of the controls. */
    var clearance = 0f
    override fun setSubtitleClearance(fraction: Float) {
        clearance = fraction
    }

    override fun stop() = mutable.update { it.copy(status = PlayerStatus.IDLE) }

    override fun close() {
        closed = true
    }

    fun playTo(positionMs: Long) = mutable.update { it.copy(status = PlayerStatus.PLAYING, positionMs = positionMs) }

    /** Playback that wants to run and is waiting: for data, or for a seek to land. */
    fun buffer() = mutable.update { it.copy(status = PlayerStatus.BUFFERING) }

    fun end() = mutable.update { it.copy(status = PlayerStatus.ENDED, positionMs = it.durationMs ?: it.positionMs) }

    /** The media has sound and no track of it can be used, while the picture plays on. */
    fun loseSound(unsupported: List<String> = emptyList()) = mutable.update { it.copy(selectedAudioId = null, unsupportedAudioCodecs = unsupported) }

    fun regainSound() = mutable.update { it.copy(selectedAudioId = "1", unsupportedAudioCodecs = emptyList()) }

    fun fail(message: String, failure: PlaybackFailure = PlaybackFailure.UNKNOWN) =
        mutable.update { it.copy(status = PlayerStatus.ERROR, failure = failure, error = message) }
}
