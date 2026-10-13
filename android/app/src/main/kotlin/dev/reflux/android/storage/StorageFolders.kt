package dev.reflux.android.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import dev.reflux.android.engine.TreeAddition
import dev.reflux.core.source.FileRole
import dev.reflux.core.source.ScanRules
import dev.reflux.ui.state.BrowsedFolder
import dev.reflux.ui.state.FolderBrowser
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.FolderListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A place storage starts: the device's own, or a drive connected to it. */
data class StorageVolumeRoot(val directory: File, val name: String)

/**
 * The device's storage as folders to choose from, for devices whose system has no folder picker to hand
 * over to: a television. It reads names and nothing else, and what the user chooses becomes a read-only
 * source like any other folder.
 *
 * @param volumes the storage that is there right now.
 * @param allowed asks, if it still has to, for the user's leave to read storage; false when they refuse.
 * @param register makes a chosen folder a source.
 */
class StorageFolders(
    private val volumes: () -> List<StorageVolumeRoot>,
    private val allowed: suspend () -> Boolean,
    private val register: (File, String) -> TreeAddition,
) : FolderBrowser {
    override suspend fun roots(): List<BrowsedFolder>? {
        if (!allowed()) return null
        return withContext(Dispatchers.IO) { volumes().filter { it.directory.isDirectory }.map { BrowsedFolder(it.directory.path, it.name) } }
    }

    override suspend fun list(folder: BrowsedFolder): FolderListing = withContext(Dispatchers.IO) {
        // A folder that cannot be listed (a drive pulled out a moment ago) is simply empty here.
        val entries = File(folder.id).listFiles().orEmpty()
        FolderListing(
            folders = entries.filter { it.isDirectory && ScanRules.shouldEnter(it.name) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { BrowsedFolder(it.path, it.name) },
            videos = entries.count { it.isFile && ScanRules.roleOf(it.name) == FileRole.VIDEO },
        )
    }

    override suspend fun add(folder: BrowsedFolder): FolderChoice = withContext(Dispatchers.IO) {
        when (val added = register(File(folder.id), folder.name)) {
            is TreeAddition.Added -> FolderChoice.Added(added.source.descriptor.id)
            is TreeAddition.AlreadyCovered -> FolderChoice.AlreadyInLibrary(added.by.id, added.by.displayName)
            is TreeAddition.Unreadable -> FolderChoice.Unreadable(added.name)
        }
    }

    companion object {
        /** The storage the system has mounted, under the names the system gives it. */
        fun volumes(context: Context): List<StorageVolumeRoot> {
            val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
            return manager.storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNull { volume ->
                    val directory = when {
                        Build.VERSION.SDK_INT >= 30 -> volume.directory
                        @Suppress("DEPRECATION") volume.isPrimary -> Environment.getExternalStorageDirectory()
                        else -> volume.uuid?.let { File("/storage/$it") }
                    } ?: return@mapNotNull null
                    StorageVolumeRoot(directory, volume.getDescription(context)?.takeIf { it.isNotBlank() } ?: directory.name)
                }
        }
    }
}
