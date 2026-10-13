package dev.reflux.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job

/**
 * Choosing a media folder where the platform has no picker of its own: a walk down the device's storage,
 * one folder at a time, until the user says "this one". It only ever lists; nothing is created or changed.
 */
@Stable
class FolderPicker internal constructor(private val app: AppState, private val browser: FolderBrowser, val roots: List<BrowsedFolder>) {
    /** One step of the walk: a folder and what is in it. */
    class Level internal constructor(val folder: BrowsedFolder, val listing: FolderListing)

    private val levels = mutableStateListOf<Level>()

    /** The way from the storage chosen at the start down to the folder that is open. Empty while choosing a storage. */
    val path: List<BrowsedFolder> get() = levels.map { it.folder }

    /** The open folder, or null while the choice is still between storages. */
    val current: Level? get() = levels.lastOrNull()

    /** The folders that can be gone into from here. */
    val folders: List<BrowsedFolder> get() = current?.listing?.folders ?: roots

    /** Set while a folder is being read or registered, so a slow drive is not asked twice. */
    var busy by mutableStateOf(false)
        private set

    fun open(folder: BrowsedFolder): Job = app.attempt(Problems.FOLDER_NOT_OPENED) {
        if (busy) return@attempt
        busy = true
        try {
            levels += Level(folder, browser.list(folder))
        } finally {
            busy = false
        }
    }

    /** Makes the open folder a source and closes the picker; scanning follows by itself. */
    fun choose(): Job = app.attempt(Problems.FOLDER_NOT_ADDED) {
        val folder = current?.folder ?: return@attempt
        if (busy) return@attempt
        busy = true
        val choice = try {
            browser.add(folder)
        } finally {
            busy = false
        }
        app.closeFolderPicker()
        app.folderChosen(choice)
    }

    /** Up one folder, or out of the picker from where it started. */
    fun back() {
        if (levels.isEmpty()) app.closeFolderPicker() else levels.removeAt(levels.lastIndex)
    }

    fun close() = app.closeFolderPicker()
}
