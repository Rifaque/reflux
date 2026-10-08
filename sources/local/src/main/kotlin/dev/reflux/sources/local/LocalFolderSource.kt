package dev.reflux.sources.local

import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.source.ChangeNotifyingSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes

/**
 * A folder on a filesystem the JVM can read directly: internal disks, removable drives, and mounted
 * network shares on desktop platforms.
 *
 * Read-only by construction: this adapter never opens files for writing, renames, or deletes anything.
 * Symbolic links are followed, with loop protection, because symlinked libraries are common.
 */
class LocalFolderSource(
    root: Path,
    displayName: String = root.fileName?.toString() ?: root.toString(),
) : FileEnumeratingSource, ChangeNotifyingSource {
    val root: Path = root.toAbsolutePath().normalize()

    override val descriptor: SourceDescriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, this.root.toString()),
        type = TYPE,
        displayName = displayName,
        locality = SourceLocality.DEVICE,
        capabilities = setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.CHANGE_NOTIFICATIONS),
    )

    override suspend fun availability(): Availability = withContext(Dispatchers.IO) {
        if (Files.isDirectory(root) && Files.isReadable(root)) Availability.AVAILABLE else Availability.UNAVAILABLE
    }

    override suspend fun playbackTarget(path: String): PlaybackTarget =
        PlaybackTarget(uri = resolve(path).toUri().toString())

    /** The absolute path of a source-relative [path]. Rejects paths that escape the root. */
    fun resolve(path: String): Path {
        val resolved = root.resolve(path).normalize()
        require(resolved.startsWith(root)) { "path escapes source root: $path" }
        return resolved
    }

    override fun files(): Flow<SourceFile> = flow {
        if (!Files.isDirectory(root)) throw SourceUnavailableException(descriptor.id)
        val visited = HashSet<Any>()
        val pending = ArrayDeque<Path>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = pending.removeLast()
            val key = directoryKey(directory) ?: continue
            if (!visited.add(key)) continue // symlink loop or duplicate mount
            val entries = try {
                Files.newDirectoryStream(directory).use { stream -> stream.sortedBy { it.fileName.toString() } }
            } catch (e: IOException) {
                if (directory == root) throw SourceUnavailableException(descriptor.id, e)
                continue // unreadable subfolder: skip, keep scanning
            }
            val subdirectories = mutableListOf<Path>()
            for (entry in entries) {
                val name = entry.fileName.toString()
                val attributes = try {
                    Files.readAttributes(entry, BasicFileAttributes::class.java)
                } catch (_: IOException) {
                    continue // broken symlink or vanished file
                }
                when {
                    attributes.isDirectory -> if (ScanRules.shouldEnter(name)) subdirectories.add(entry)
                    attributes.isRegularFile && ScanRules.roleOf(name) != null -> emit(
                        SourceFile(
                            path = root.relativize(entry).joinToString("/"),
                            sizeBytes = attributes.size(),
                            modifiedAtEpochMs = attributes.lastModifiedTime().toMillis(),
                        ),
                    )
                }
            }
            // Depth-first in name order keeps scans deterministic.
            subdirectories.asReversed().forEach(pending::addLast)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Emits when relevant files or folders change anywhere below the root. Uses the platform file watcher
     * (inotify, ReadDirectoryChangesW, ...) and registers new subfolders as they appear.
     */
    override fun changes(): Flow<Unit> = callbackFlow {
        val watcher = root.fileSystem.newWatchService()
        val watched = HashMap<WatchKey, Path>()
        fun register(directory: Path) {
            val walk = ArrayDeque(listOf(directory))
            while (walk.isNotEmpty()) {
                val current = walk.removeLast()
                val key = try {
                    current.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
                } catch (_: IOException) {
                    continue
                }
                watched[key] = current
                try {
                    Files.newDirectoryStream(current) { Files.isDirectory(it) && ScanRules.shouldEnter(it.fileName.toString()) }
                        .use { stream -> stream.forEach(walk::addLast) }
                } catch (_: IOException) {
                    // unreadable subfolder: not watched
                }
            }
        }
        register(root)
        val thread = Thread({
            try {
                while (true) {
                    val key = watcher.take()
                    val directory = watched[key]
                    var relevant = false
                    for (event in key.pollEvents()) {
                        if (event.kind() == OVERFLOW) {
                            relevant = true
                            continue
                        }
                        val name = (event.context() as? Path)?.fileName?.toString() ?: continue
                        val path = directory?.resolve(name)
                        val isDirectory = path != null && Files.isDirectory(path)
                        if (isDirectory && event.kind() == ENTRY_CREATE && ScanRules.shouldEnter(name)) register(path!!)
                        // Folder modifications (reported on Windows when anything inside changes) carry no information:
                        // subfolders are watched themselves.
                        val folderAddedOrRemoved = isDirectory && event.kind() != ENTRY_MODIFY
                        if (folderAddedOrRemoved || ScanRules.roleOf(name) != null || event.kind() == ENTRY_DELETE) relevant = true
                    }
                    if (!key.reset()) watched.remove(key)
                    if (relevant) trySend(Unit)
                }
            } catch (_: InterruptedException) {
                // closed
            } catch (_: ClosedWatchServiceException) {
                // closed
            }
        }, "reflux-watch-${descriptor.id}")
        thread.isDaemon = true
        thread.start()
        awaitClose {
            watcher.close()
            thread.interrupt()
        }
    }

    private fun directoryKey(directory: Path): Any? = try {
        Files.readAttributes(directory, BasicFileAttributes::class.java).fileKey()
            ?: directory.toRealPath().toString()
    } catch (_: IOException) {
        null
    }

    companion object {
        const val TYPE: String = "local"

        /** Recreates a source from its persisted configuration (the root path). */
        fun fromConfig(config: String, displayName: String): LocalFolderSource =
            LocalFolderSource(Path.of(config), displayName)
    }
}

