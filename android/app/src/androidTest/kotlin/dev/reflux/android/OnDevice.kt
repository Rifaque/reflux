package dev.reflux.android

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.storage.DocumentTree
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.android.storage.TreeEntry
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.source.MediaSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

internal const val MINUTE = 60_000L

internal val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** A real folder in the app's own storage, served as a document tree: what the system picker would hand over, without the picker. */
internal class FolderTree(private val root: File) : DocumentTree {
    var present = true
    override val rootDocumentId: String = root.path
    override fun reachable() = present && root.isDirectory
    override fun children(documentId: String): List<TreeEntry> {
        check(present) { "the folder is gone" }
        return File(documentId).listFiles().orEmpty().map { TreeEntry(it.path, it.name, it.isDirectory, it.length(), it.lastModified()) }
    }

    override fun open(documentId: String): String = File(documentId).toURI().toString().replace("file:/", "file:///").replace("file:////", "file:///")
}

/** The engine over a fresh, private directory, with one local folder registered as a source. */
internal class DeviceWorld(name: String) {
    // Not the cache directory: a device short of space clears that behind a running test.
    val directory: File = File(context.filesDir, "reflux-test-$name-${System.nanoTime()}").also { it.mkdirs() }
    val media: File = File(directory, "media").also { it.mkdirs() }
    val environment = AndroidEnvironment(context, dataDirectory = File(directory, "data").also { it.mkdirs() }, databaseName = null)
    val tree = FolderTree(media)
    val folder = DocumentTreeSource(tree, "test://$name", "Phone storage")
    private val extra = HashMap<SourceId, MediaSource>()
    val sources = { id: SourceId -> extra[id] ?: environment.registry.resolve(id) }

    init {
        environment.library.addSource(folder, "test://$name")
        environment.registry.register(folder)
    }

    fun add(source: MediaSource, config: String) {
        environment.library.addSource(source, config)
        environment.registry.register(source)
        extra[source.descriptor.id] = source
    }

    /** Puts the generated test clip (three minutes of video with English audio) into the folder at [path]. */
    fun clip(path: String): File = File(media, path).also { file ->
        file.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("clip.mp4").use { input -> file.outputStream().use(input::copyTo) }
    }

    /** A file with a media name and nothing playable inside, for tests that are about the library only. */
    fun stub(path: String): File = File(media, path).also { file ->
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(16))
    }

    fun movie(title: String): MediaId = environment.library.movies().single { it.item.title == title }.item.id

    fun copyOn(source: MediaSource, item: MediaId): MediaVersion =
        environment.library.versions(item).single { it.version.location.sourceId == source.descriptor.id }.version

    fun close() {
        directory.deleteRecursively()
    }
}

/** Waits for the engine to get somewhere; real decoders on an emulator are not quick. */
internal fun Player.await(what: String, timeoutMs: Long = 30_000, condition: (PlayerState) -> Boolean): PlayerState = runBlocking {
    try {
        withTimeout(timeoutMs) { state.first(condition) }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("Timed out waiting for $what. Player: ${state.value}", e)
    }
}
