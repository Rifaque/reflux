package dev.reflux.desktop.engine

import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.ConfigSeal
import dev.reflux.library.DownloadsSource
import dev.reflux.library.FileDownloadStore
import dev.reflux.library.HttpDownloadTransport
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.Offline
import dev.reflux.library.SourceConnections
import dev.reflux.library.SourceRecord
import dev.reflux.library.SignOut
import dev.reflux.library.SourceRegistry
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.playback.mpv.DecodeEvidence
import dev.reflux.playback.mpv.MpvDecodeProbe
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.playback.mpv.MpvProbe
import dev.reflux.sources.jellyfin.JellyfinConnector
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.sources.smb.LocalStreamServer
import dev.reflux.sources.smb.SmbConnector
import dev.reflux.sources.smb.SmbShareConfig
import dev.reflux.sources.smb.SmbSource
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavConnector
import dev.reflux.sources.webdav.WebDavSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.Path
import java.util.Locale

/** Where Reflux keeps its own state on desktop platforms. Never inside the user's media folders. */
object AppDirectories {
    fun data(env: Map<String, String> = System.getenv(), os: String = System.getProperty("os.name"), home: String = System.getProperty("user.home")): Path {
        env["REFLUX_HOME"]?.let { return Path.of(it) }
        return when {
            os.startsWith("Windows") -> Path.of(env["LOCALAPPDATA"] ?: "$home\\AppData\\Local", "Reflux")
            os.startsWith("Mac") -> Path.of(home, "Library", "Application Support", "Reflux")
            else -> Path.of(env["XDG_DATA_HOME"] ?: "$home/.local/share", "reflux")
        }
    }
}

/** What happened when a folder was offered as a media source. */
sealed interface FolderAddition {
    /** The folder is now a source. [replaced] are narrower folder sources it took over. */
    data class Added(val source: MediaSource, val replaced: List<SourceRecord> = emptyList()) : FolderAddition

    /** The folder, or a folder containing it, is already a source. */
    data class AlreadyCovered(val by: SourceRecord) : FolderAddition

    data class NotAFolder(val path: Path) : FolderAddition

    /** The folder exists but its contents cannot be listed (permissions, a disconnected mount). */
    data class Unreadable(val path: Path) : FolderAddition
}

/**
 * The Reflux engine wired for a desktop machine: library database, artwork cache, source adapters, metadata,
 * probing, and the background pipeline. Shared by the desktop shell and the developer CLI, so both exercise
 * exactly the same engine.
 */
class DesktopEnvironment(
    dataDirectory: Path = AppDirectories.data(),
    /** Where source connections are kept; by default the system's credential store (see [DesktopSecrets]). */
    sealFor: (Path, (String) -> Unit) -> ConfigSeal = { directory, report -> DesktopSecrets.seal(directory, report = report) },
) {
    val http = JdkHttpFetcher(userAgent = "Reflux/0.1")

    /** Problems worth a developer's attention, kept next to the library. */
    val diagnostics = DiagnosticLog(dataDirectory.resolve("reflux.log"))
    val library = Library(LibraryDatabase.open(dataDirectory.resolve("library.db"))) { System.currentTimeMillis() }
    val artwork = ArtworkCache(dataDirectory.resolve("artwork"), http)
    val language: String = Locale.getDefault().toLanguageTag().takeIf { it != "und" } ?: "en-US"
    val deviceId: String = dataDirectory.resolve("device-id").let { file ->
        if (Files.exists(file)) Files.readString(file).trim()
        else java.util.UUID.randomUUID().toString().also { Files.writeString(file, it) }
    }

    /** Keeps source connections out of the library database: the database holds only a reference. */
    private val seal: ConfigSeal = sealFor(dataDirectory) { diagnostics.record(it) }

    /** Streams SMB files to players over loopback HTTP. */
    val streams by lazy { LocalStreamServer() }

    /** Where copies for offline are kept: Reflux's own data directory, never one of the user's folders. */
    private val downloads = FileDownloadStore(dataDirectory.resolve("downloads").toFile())

    val registry = SourceRegistry(
        mapOf(
            DownloadsSource.TYPE to { _ -> DownloadsSource(downloads) },
            LocalFolderSource.TYPE to { record -> LocalFolderSource.fromConfig(record.config, record.displayName) },
            JellyfinSource.TYPE to { record ->
                JellyfinCredentials.decode(seal.open(record.id, record.config))?.let { JellyfinSource(it, http, record.displayName) }
            },
            WebDavSource.TYPE to { record -> WebDavConfig.decode(seal.open(record.id, record.config))?.let { WebDavSource(it, http, record.displayName) } },
            SmbSource.TYPE to { record -> SmbShareConfig.decode(seal.open(record.id, record.config))?.let { SmbSource(it, streams, record.displayName) } },
        ),
    ).apply { load(library.sources()) }

    /** Servers and shares, added by their address: a Jellyfin server, a web share, a Windows or NAS share. */
    val connections: SourceConnections by lazy {
        SourceConnections(
            library,
            registry,
            listOf(JellyfinConnector(http, deviceId, deviceName = "Reflux on ${hostName()}"), SmbConnector(streams), WebDavConnector(http)),
            report = diagnostics::record,
            seal = seal,
        )
    }

    init {
        // A connection an earlier version wrote into the database is moved into the credential store first.
        connections.sealStored()
    }

    /** TMDB is enabled when the build or environment provides an application credential. */
    val metadata: MetadataProvider? = (System.getenv("REFLUX_TMDB_TOKEN") ?: System.getProperty("reflux.tmdb.token"))
        ?.takeIf { it.isNotBlank() }
        ?.let { TmdbProvider(it, http) }

    val prober = MpvProbe.createOrNull()

    val pipeline = LibraryPipeline(
        library = library,
        registry = registry,
        prober = prober?.let { probe -> dev.reflux.core.playback.MediaProber { target -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { probe.probe(target) } } },
        metadata = metadata,
        language = language,
        prefetchArtwork = { locators -> artwork.prefetch(locators, registry::resolve) },
    )

    fun source(id: SourceId): MediaSource? = registry.resolve(id)

    /**
     * What this machine can show and decode, as observed (docs/DECISIONS.md, "Desktop capabilities are observed,
     * not declared"). Starts from the default screen and nothing known of hardware decoding; the shell tells it
     * which display the window is on, and [probeDecoding] and every playback what the engine decodes.
     */
    val capabilities = ObservedCapabilities(report = diagnostics::record).also { it.observeDisplay(null) }

    /** What Best Version is told now. */
    fun deviceCapabilities(): DeviceCapabilities = capabilities.current()

    /**
     * Asks the engine which codecs this machine's hardware decodes ([MpvDecodeProbe]), once. Blocking, about a
     * second or two; run it in the background after start. Without libmpv nothing is learned, and decoding stays
     * unknown.
     */
    fun probeDecoding() {
        val probe = MpvDecodeProbe.createOrNull() ?: return capabilities.probed(DecodeEvidence.UNKNOWN, 0).let { }
        val started = System.nanoTime()
        val evidence = try {
            probe.run()
        } catch (e: Exception) {
            diagnostics.record("The hardware decoding probe failed; decoding stays unknown", e)
            DecodeEvidence.UNKNOWN
        }
        capabilities.probed(evidence, (System.nanoTime() - started) / 1_000_000)
    }

    /**
     * Smart Offline on this machine, with the same planner and rules as everywhere (docs/OFFLINE.md). Copies are
     * made while Reflux is running; one that was cut short continues from where it was the next time Reflux
     * starts (see [startOffline]).
     */
    val offline: Offline by lazy {
        Offline(
            library = library,
            store = downloads,
            transport = HttpDownloadTransport("Reflux/0.1"),
            sources = registry::resolve,
            device = { capabilities.current() },
            preferredLanguages = listOf(Locale.getDefault().language).filter { it.isNotBlank() },
            register = registry::register,
            dispatcher = Dispatchers.IO,
            report = diagnostics::record,
        )
    }

    private var offlineJob: Job? = null

    /** Starts making copies in [scope], once: it should live as long as the application does. */
    @Synchronized
    fun startOffline(scope: CoroutineScope) {
        if (offlineJob == null) offlineJob = offline.start(scope)
    }

    /** Whether a playback engine is installed. Without one the library still works; only playback is unavailable. */
    val canPlay: Boolean get() = MpvPlayer.isAvailable

    /**
     * Offers a local folder as a media source. The folder is only ever read. A folder that is already
     * covered by a source is not added twice, and a folder that contains existing folder sources takes them
     * over, so the same files never appear as two sources. Call [LibraryPipeline.refresh] afterwards to scan.
     */
    fun addLocalFolder(folder: Path, displayName: String? = null): FolderAddition {
        val root = folder.toAbsolutePath().normalize()
        if (!Files.isDirectory(root)) return FolderAddition.NotAFolder(root)
        val canonical = try {
            Files.newDirectoryStream(root).use { }
            root.toRealPath()
        } catch (_: IOException) {
            return FolderAddition.Unreadable(root)
        } catch (_: SecurityException) {
            return FolderAddition.Unreadable(root)
        }
        val folders = library.sources().filter { it.type == LocalFolderSource.TYPE }.associateWith { canonical(Path.of(it.config)) }
        folders.entries.firstOrNull { (_, existing) -> canonical.startsWith(existing) }?.let { return FolderAddition.AlreadyCovered(it.key) }
        val narrower = folders.filterValues { it.startsWith(canonical) }.keys.toList()
        narrower.forEach { removeSource(it.id) }
        val source = LocalFolderSource(root, displayName ?: root.fileName?.toString() ?: root.toString())
        library.addSource(source, root.toString())
        registry.register(source)
        return FolderAddition.Added(source, narrower)
    }

    /** The real location of a folder source, or its normalized path while it cannot be reached. */
    private fun canonical(path: Path): Path = try {
        path.toRealPath()
    } catch (_: IOException) {
        path.toAbsolutePath().normalize()
    }

    /**
     * Removes a source and what Reflux derived from it. Watch history is kept; files are never touched. A
     * server's session is then ended without holding the removal up ([SourceConnections.remove]).
     */
    fun removeSource(id: SourceId): Deferred<SignOut> = connections.remove(id)

    /**
     * Creates a playback engine with Reflux's defaults plus [options], or null when libmpv is not installed.
     * Setting `REFLUX_MPV_LOG` to a file makes the engine write its own verbose log there, for diagnosing
     * playback problems that Reflux only sees the outcome of; set to a directory, every engine gets a file.
     */
    fun createPlayer(options: Map<String, String> = emptyMap()): MpvPlayer? {
        val log = System.getenv("REFLUX_MPV_LOG")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.let { target ->
            val file = if (Files.isDirectory(target)) target.resolve("mpv-${System.currentTimeMillis()}.log") else target
            // Written by Reflux, through the same redaction as its own log: the engine's own log file records the
            // stream's HTTP headers, a server's access token among them.
            val sink: (String) -> Unit = { line ->
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            }
            sink
        }
        return MpvPlayer.create(deviceCapabilities(), options, log, onDecode = { capabilities.decoded(it) })
    }

    private fun hostName(): String = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "desktop"
}
