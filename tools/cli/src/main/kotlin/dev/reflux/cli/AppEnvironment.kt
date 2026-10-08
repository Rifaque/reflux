package dev.reflux.cli

import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.SourceRegistry
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvProbe
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavSource
import java.awt.GraphicsEnvironment
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

/** Wires the engine the same way a desktop shell does. */
class AppEnvironment(dataDirectory: Path = AppDirectories.data()) {
    val http = JdkHttpFetcher(userAgent = "Reflux/0.1")
    val library = Library(LibraryDatabase.open(dataDirectory.resolve("library.db"))) { System.currentTimeMillis() }
    val artwork = ArtworkCache(dataDirectory.resolve("artwork"), http)
    val language: String = Locale.getDefault().toLanguageTag().takeIf { it != "und" } ?: "en-US"
    val deviceId: String = dataDirectory.resolve("device-id").let { file ->
        if (java.nio.file.Files.exists(file)) java.nio.file.Files.readString(file).trim()
        else java.util.UUID.randomUUID().toString().also { java.nio.file.Files.writeString(file, it) }
    }

    val registry = SourceRegistry(
        mapOf(
            LocalFolderSource.TYPE to { record -> LocalFolderSource.fromConfig(record.config, record.displayName) },
            JellyfinSource.TYPE to { record ->
                JellyfinCredentials.decode(record.config)?.let { JellyfinSource(it, http, record.displayName) }
            },
            WebDavSource.TYPE to { record -> WebDavConfig.decode(record.config)?.let { WebDavSource(it, http, record.displayName) } },
        ),
    ).apply { load(library.sources()) }

    /** TMDB is enabled when the build or environment provides an application credential. */
    val metadata: TmdbProvider? = (System.getenv("REFLUX_TMDB_TOKEN") ?: System.getProperty("reflux.tmdb.token"))
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

    fun deviceCapabilities() = DesktopCapabilities.of(display())

    private fun display(): DisplayCapabilities {
        if (GraphicsEnvironment.isHeadless()) return DisplayCapabilities(1920, 1080)
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        return DisplayCapabilities(mode.width, mode.height)
    }
}
