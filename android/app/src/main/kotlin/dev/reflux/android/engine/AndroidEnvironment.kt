package dev.reflux.android.engine

import dev.reflux.core.BuildIdentity
import dev.reflux.core.RefluxBuild
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.reflux.android.playback.AndroidCapabilitySource
import dev.reflux.android.playback.AndroidMediaProber
import dev.reflux.android.storage.ContentDocumentTree
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.DownloadsSource
import dev.reflux.library.FileDownloadStore
import dev.reflux.library.HttpDownloadTransport
import dev.reflux.library.Offline
import dev.reflux.library.Library
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.SourceConnections
import dev.reflux.library.SourceRecord
import dev.reflux.library.SignOut
import dev.reflux.library.SourceRegistry
import dev.reflux.library.db.RefluxDatabase
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.sources.jellyfin.JellyfinConnector
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.plex.PlexConnector
import dev.reflux.sources.plex.PlexCredentials
import dev.reflux.sources.plex.PlexSource
import dev.reflux.sources.smb.LocalStreamServer
import dev.reflux.sources.smb.SmbConnector
import dev.reflux.sources.smb.SmbShareConfig
import dev.reflux.sources.smb.SmbSource
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavConnector
import dev.reflux.sources.webdav.WebDavSource
import android.os.LocaleList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/** What happened when a folder from the system picker was offered as a media source. */
sealed interface TreeAddition {
    /** The folder is now a source. [replaced] are narrower folder sources it took over. */
    data class Added(val source: MediaSource, val replaced: List<SourceRecord> = emptyList()) : TreeAddition

    /** The folder, or a folder containing it, is already a source. */
    data class AlreadyCovered(val by: SourceRecord) : TreeAddition

    /** The folder was granted but cannot be listed (its storage is not there). */
    data class Unreadable(val name: String) : TreeAddition
}

/**
 * HTTP for the engine on Android, and the way to read a document: sources hand out `content:` addresses
 * for what lives in a granted folder, and whoever needs the bytes (the artwork cache) fetches them here
 * like any other address.
 */
class AndroidFetcher(private val context: Context, private val userAgent: String) : HttpFetcher {
    override suspend fun send(request: HttpRequest): HttpResult = withContext(Dispatchers.IO) {
        if (request.url.startsWith("content:")) {
            val bytes = context.contentResolver.openInputStream(Uri.parse(request.url))?.use { it.readBytes() }
            return@withContext if (bytes == null) HttpResult(404, ByteArray(0)) else HttpResult(200, bytes)
        }
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            val headers = connection.headerFields.filterKeys { it != null }.mapValues { it.value.firstOrNull().orEmpty() }
            HttpResult(status, body, headers)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * The Reflux engine wired for an Android device: the same library, pipeline, and sources as on the
 * desktop, on Android's SQLite, its document providers, and its media framework. No user interface.
 *
 * Reflux's own state lives in the app's private storage. The user's media is only ever read.
 */
class AndroidEnvironment(context: Context, dataDirectory: File = context.filesDir, databaseName: String? = "library.db") {
    private val application = context.applicationContext

    /** The application context, for what the shell creates on the engine's behalf (players). */
    val context: Context get() = application

    /** The system's accessibility settings, followed for as long as the process lives. */
    val accessibility: AndroidAccessibility by lazy { AndroidAccessibility(application) }

    /** Which build this is: the version, and the commit its build recorded in the package's assets. */
    val build: BuildIdentity = BuildIdentity.parse(runCatching { application.assets.open(BUILD_IDENTITY).bufferedReader().use { it.readText() } }.getOrNull())

    val http: HttpFetcher = AndroidFetcher(application, userAgent = USER_AGENT)

    /** Web shares' listings, which [http] cannot send (see [WebDavTransport]). Made when the first share is. */
    private val webDav: HttpFetcher by lazy { WebDavTransport(userAgent = USER_AGENT) }

    /** [databaseName] null keeps the library in memory, for tests. */
    val library = Library(RefluxDatabase(AndroidSqliteDriver(RefluxDatabase.Schema, application, databaseName))) { System.currentTimeMillis() }
    val artwork = ArtworkCache(dataDirectory.resolve(ARTWORK_DIRECTORY).also { it.mkdirs() }.toPath(), http)
    val language: String = Locale.getDefault().toLanguageTag().takeIf { it != "und" } ?: "en-US"
    val deviceId: String = dataDirectory.resolve("device-id").let { file ->
        if (file.exists()) file.readText().trim() else UUID.randomUUID().toString().also(file::writeText)
    }
    private val log = dataDirectory.resolve("reflux.log")

    /** How this device names itself to servers: their lists of signed-in devices show it. */
    private val deviceName: String = "Reflux on " +
        listOfNotNull(android.os.Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, android.os.Build.MODEL).joinToString(" ").ifBlank { "Android" }

    /**
     * Where copies for offline are kept: the application's own storage, which no other application can
     * read, needs no permission, and goes when Reflux is uninstalled.
     */
    private val downloads = FileDownloadStore(dataDirectory.resolve("downloads"))

    /** What source connections store is sealed with a key in the Android keystore. */
    private val seal = KeystoreSeal()

    /** Streams files on Windows and NAS shares to the player over loopback HTTP. */
    private val streams by lazy { LocalStreamServer() }

    val registry = SourceRegistry(
        mapOf(
            DownloadsSource.TYPE to { _ -> DownloadsSource(downloads) },
            DocumentTreeSource.TYPE to { record -> folderSource(Uri.parse(record.config), record.displayName) },
            // A folder chosen by its path, on devices where Reflux browses storage itself (televisions).
            LocalFolderSource.TYPE to { record -> LocalFolderSource(File(record.config).toPath(), record.displayName) },
            JellyfinSource.TYPE to { record -> JellyfinCredentials.decode(seal.open(record.id, record.config))?.let { JellyfinSource(it, http, record.displayName) } },
            PlexSource.TYPE to { record -> PlexCredentials.decode(seal.open(record.id, record.config))?.let { PlexSource(it, http, record.displayName, deviceName) } },
            WebDavSource.TYPE to { record -> WebDavConfig.decode(seal.open(record.id, record.config))?.let { WebDavSource(it, webDav, record.displayName) } },
            SmbSource.TYPE to { record -> SmbShareConfig.decode(seal.open(record.id, record.config))?.let { SmbSource(it, streams, record.displayName) } },
        ),
    ).apply { load(library.sources()) }

    /** Servers and shares, added by their address: Jellyfin, Plex, Windows and NAS shares, and web shares. */
    val connections: SourceConnections by lazy {
        SourceConnections(
            library,
            registry,
            listOf(JellyfinConnector(http, deviceId, deviceName = deviceName), PlexConnector(http, deviceId, deviceName = deviceName), SmbConnector(streams), WebDavConnector(webDav)),
            report = ::report,
            seal = seal,
        )
    }

    init {
        // Which build wrote what follows (docs/VERSIONING.md).
        report("Reflux ${build.label} started")
        // Whatever an earlier version stored in the open is sealed before anything else reads it.
        connections.sealStored()
        connections.migrateExcludedSections()
    }

    /** TMDB is enabled when the build provides an application credential; without one, names come from files. */
    val metadata: MetadataProvider? = tmdbToken(application)?.let { TmdbProvider(it, http) }

    val pipeline = LibraryPipeline(
        library = library,
        registry = registry,
        prober = AndroidMediaProber(application),
        metadata = metadata,
        language = language,
        prefetchArtwork = { locators -> artwork.prefetch(locators, registry::resolve) },
    )

    private val capabilitySource by lazy { AndroidCapabilitySource(application) }

    /** What this device can play now: read again after an output or a display has come or gone. */
    val capabilities: DeviceCapabilities get() = capabilitySource.current()

    /**
     * Smart Offline, on this device's storage and network. Copies are made while Reflux is running; one
     * that was cut short continues from where it was the next time Reflux starts (see [startOffline]).
     */
    val offline: Offline by lazy {
        Offline(
            library = library,
            store = downloads,
            transport = HttpDownloadTransport(USER_AGENT),
            sources = registry::resolve,
            device = { capabilities },
            preferredLanguages = LocaleList.getDefault().let { locales -> (0 until locales.size()).map { locales[it].language } }.filter { it.isNotBlank() },
            register = registry::register,
            dispatcher = Dispatchers.IO,
            report = ::report,
        )
    }

    private var offlineJob: Job? = null

    /** Starts making copies in [scope], once: it should be one that lives as long as the process does. */
    @Synchronized
    fun startOffline(scope: CoroutineScope) {
        if (offlineJob == null) offlineJob = offline.start(scope)
    }

    fun source(id: SourceId): MediaSource? = registry.resolve(id)

    /**
     * Offers a folder the user picked as a media source and keeps the grant across restarts. A folder that
     * is already covered by a source is not added twice, and a folder that contains existing folder sources
     * takes them over, so the same file is never two versions of a work. Call [LibraryPipeline.refresh]
     * afterwards to scan.
     */
    fun addFolder(treeUri: Uri): TreeAddition {
        val resolver = application.contentResolver
        runCatching { resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = displayName(treeUri)
        val tree = ContentDocumentTree(resolver, treeUri)
        if (!tree.reachable()) return TreeAddition.Unreadable(name)

        val folders = library.sources().filter { it.type == DocumentTreeSource.TYPE }.associateWith { Uri.parse(it.config) }
        fun related(outer: Uri, inner: Uri) = outer.authority == inner.authority &&
            DocumentTreeSource.covers(DocumentsContract.getTreeDocumentId(outer), DocumentsContract.getTreeDocumentId(inner))
        folders.entries.firstOrNull { (_, existing) -> related(existing, treeUri) }?.let { return TreeAddition.AlreadyCovered(it.key) }
        val narrower = folders.filterValues { related(treeUri, it) }.keys.toList()
        narrower.forEach { removeSource(it.id) }

        val source = DocumentTreeSource(tree, treeUri.toString(), name)
        library.addSource(source, treeUri.toString())
        registry.register(source)
        return TreeAddition.Added(source, narrower)
    }

    /**
     * Offers a folder chosen by its path as a media source, under the same rule as [addFolder]: one folder
     * is one source, a folder inside one that is already a source is not added again, and a folder that
     * contains others takes them over.
     */
    fun addFolder(folder: File, name: String): TreeAddition {
        if (!folder.isDirectory || !folder.canRead()) return TreeAddition.Unreadable(name)
        val root = folder.toPath().toAbsolutePath().normalize()
        val folders = library.sources().filter { it.type == LocalFolderSource.TYPE }.associateWith { File(it.config).toPath() }
        folders.entries.firstOrNull { (_, existing) -> root.startsWith(existing) }?.let { return TreeAddition.AlreadyCovered(it.key) }
        val narrower = folders.filterValues { it.startsWith(root) }.keys.toList()
        narrower.forEach { removeSource(it.id) }

        val source = LocalFolderSource(root, name)
        library.addSource(source, root.toString())
        registry.register(source)
        return TreeAddition.Added(source, narrower)
    }

    /**
     * Removes a source and what Reflux derived from it, and gives back its folder grant. Files are never
     * touched. A server's session is then ended without holding the removal up ([SourceConnections.remove]).
     */
    fun removeSource(id: SourceId): Deferred<SignOut> {
        val record = library.source(id)
        val signOut = connections.remove(id)
        if (record?.type == DocumentTreeSource.TYPE) {
            runCatching { application.contentResolver.releasePersistableUriPermission(Uri.parse(record.config), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        return signOut
    }

    /** Records a problem for developers: in the system log, and next to the library for bug reports. */
    fun report(problem: String, cause: Throwable? = null) {
        // Errors quote addresses, and addresses carry credentials: neither log gets those.
        val entry = Redaction.redact(problem + cause?.let { ": ${Log.getStackTraceString(it)}" }.orEmpty())
        Log.w("Reflux", entry)
        runCatching {
            if (log.length() > MAX_LOG_BYTES) log.delete()
            log.appendText("${System.currentTimeMillis()} $entry\n")
        }
    }

    private fun folderSource(treeUri: Uri, displayName: String) =
        DocumentTreeSource(ContentDocumentTree(application.contentResolver, treeUri), treeUri.toString(), displayName)

    /** The folder's own name ("Movies"), or the volume's for a whole volume. */
    private fun displayName(treeUri: Uri): String {
        val resolver = application.contentResolver
        val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val name = runCatching {
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast('/').substringAfterLast(':').ifBlank { "Media" }
    }

    companion object {
        /** Where artwork is cached, inside the data directory; the launcher reads it there (ArtworkProvider). */
        const val ARTWORK_DIRECTORY = "artwork"

        private const val MAX_LOG_BYTES = 512 * 1024L

        private fun tmdbToken(context: Context): String? {
            val id = context.resources.getIdentifier("reflux_tmdb_token", "string", context.packageName)
            return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
        }
    }
}

/** How Reflux names itself to the servers it talks to, with the one product version (docs/VERSIONING.md). */
private val USER_AGENT = "Reflux/${RefluxBuild.VERSION} (Android)"

/** Written into the package's assets by the build (`generateBuildIdentity`). */
private const val BUILD_IDENTITY = "build-identity.properties"
