package dev.reflux.android.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.reflux.android.engine.AndroidEnvironment
import java.io.File
import java.io.FileNotFoundException

/**
 * Lends cached artwork to the launcher, which draws Reflux's Watch Next cards in its own process and cannot
 * read Reflux's private storage. Read-only, and only files of the artwork cache, by their cache names
 * (digests that cannot be guessed from what they show); nothing else of Reflux's is reachable through it.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Artwork is read-only")
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = fileFor(uri)?.let { file ->
        when (file.extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/*"
        }
    }

    private fun fileFor(uri: Uri): File? {
        val name = uri.pathSegments.singleOrNull()?.takeIf(CACHE_NAME::matches) ?: return null
        val directory = context?.filesDir?.resolve(AndroidEnvironment.ARTWORK_DIRECTORY) ?: return null
        return directory.resolve(name).takeIf { it.isFile }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Artwork is read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Artwork is read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Artwork is read-only")

    companion object {
        /** The names the artwork cache gives its files (`ArtworkCache`). */
        private val CACHE_NAME = Regex("[0-9a-f]{32}\\.(jpg|jpeg|png|webp|img)")

        fun uriFor(packageName: String, file: File): Uri = Uri.Builder().scheme("content").authority("$packageName.artwork").appendPath(file.name).build()
    }
}
