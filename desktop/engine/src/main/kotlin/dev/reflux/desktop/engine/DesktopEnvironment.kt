package dev.reflux.desktop.engine

import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.SourceRecord
import dev.reflux.library.SourceRegistry
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.playback.mpv.MpvProbe
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.sources.smb.LocalStreamServer
import dev.reflux.sources.smb.SmbShareConfig
import dev.reflux.sources.smb.SmbSource
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavSource
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.file.Files
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
class DesktopEnvironment(dataDirectory: Path = AppDirectories.data()) {
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

    /** Streams SMB files to players over loopback HTTP. */
    val streams by lazy { LocalStreamServer() }

    val registry = SourceRegistry(
        mapOf(
            LocalFolderSource.TYPE to { record -> LocalFolderSource.fromConfig(record.config, record.displayName) },
            JellyfinSource.TYPE to { record ->
                JellyfinCredentials.decode(record.config)?.let { JellyfinSource(it, http, record.displayName) }
            },
            WebDavSource.TYPE to { record -> WebDavConfig.decode(record.config)?.let { WebDavSource(it, http, record.displayName) } },
            SmbSource.TYPE to { record -> SmbShareConfig.decode(record.config)?.let { SmbSource(it, streams, record.displayName) } },
        ),
    ).apply { load(library.sources()) }

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

    fun deviceCapabilities(): DeviceCapabilities = DesktopCapabilities.of(display())

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

    /** Removes a source and what Reflux derived from it. Watch history is kept; files are never touched. */
    fun removeSource(id: SourceId) {
        library.removeSource(id)
        registry.unregister(id)
    }

    /**
     * Creates a playback engine with Reflux's defaults plus [options], or null when libmpv is not installed.
     * Setting `REFLUX_MPV_LOG` to a file makes the engine write its own verbose log there, for diagnosing
     * playback problems that Reflux only sees the outcome of; set to a directory, every engine gets a file.
     */
    fun createPlayer(options: Map<String, String> = emptyMap()): MpvPlayer? {
        val log = System.getenv("REFLUX_MPV_LOG")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.let { target ->
            val file = if (Files.isDirectory(target)) target.resolve("mpv-${System.currentTimeMillis()}.log") else target
            mapOf("log-file" to file.toString(), "msg-level" to "all=v")
        }
        return MpvPlayer.create(deviceCapabilities(), options + log.orEmpty())
    }

    private fun display(): DisplayCapabilities {
        if (GraphicsEnvironment.isHeadless()) return DisplayCapabilities(1920, 1080)
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        return DisplayCapabilities(mode.width, mode.height)
    }
}
