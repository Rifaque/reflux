package dev.reflux.android.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** One entry of a folder, as a document provider lists it. */
data class TreeEntry(val documentId: String, val name: String, val directory: Boolean, val sizeBytes: Long, val modifiedAtEpochMs: Long)

/**
 * A folder tree the user granted access to, reduced to what a source needs: its listing and a way to open
 * a document. The Android implementation asks the document provider; tests supply their own.
 */
interface DocumentTree {
    /** The document that is the granted folder itself. */
    val rootDocumentId: String

    /** Whether the grant still stands and the folder can be listed. Must not throw. */
    fun reachable(): Boolean

    /** The entries directly inside [documentId]. Throws when the folder cannot be listed. */
    fun children(documentId: String): List<TreeEntry>

    /** Something a player or a reader can open for [documentId]. */
    fun open(documentId: String): String
}

/**
 * A folder chosen through Android's system picker (Storage Access Framework): internal storage, an SD card,
 * a USB drive, or any other document provider. Android hands out such folders as trees of documents, not
 * as paths, and access to them is a grant the user can take back.
 *
 * Read-only by construction: nothing here opens a document for writing, renames, or deletes.
 *
 * Source-relative paths are the display names from the root down, joined by `/`, so that they read like the
 * paths every other file source produces and identify the same way.
 */
class DocumentTreeSource(
    private val tree: DocumentTree,
    /** What identifies the folder across restarts: its tree URI. */
    identity: String,
    displayName: String,
) : FileEnumeratingSource {
    override val descriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, identity),
        type = TYPE,
        displayName = displayName,
        locality = SourceLocality.DEVICE,
        capabilities = setOf(SourceCapability.ENUMERATE_FILES),
    )

    /** Document IDs by path, learned while listing; a path not seen yet is found by walking down to it. */
    private val documents = ConcurrentHashMap<String, String>()

    override suspend fun availability(): Availability = withContext(Dispatchers.IO) {
        if (tree.reachable()) Availability.AVAILABLE else Availability.UNAVAILABLE
    }

    override fun files(): Flow<SourceFile> = flow {
        if (!tree.reachable()) throw SourceUnavailableException(descriptor.id)
        val seen = HashSet<String>()
        // Depth-first in name order keeps scans deterministic.
        val pending = ArrayDeque(listOf("" to tree.rootDocumentId))
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (path, documentId) = pending.removeLast()
            if (!seen.add(documentId)) continue
            val entries = try {
                tree.children(documentId).sortedBy { it.name }
            } catch (e: Exception) {
                if (documentId == tree.rootDocumentId) throw SourceUnavailableException(descriptor.id, e)
                // An unreadable subfolder (a drive removed partway, permissions) is reported, so that what was
                // found below it stays; the scan goes on.
                emit(SourceFile.folderNotRead(path))
                continue
            }
            val folders = mutableListOf<Pair<String, String>>()
            for (entry in entries) {
                if (entry.name.isEmpty() || '/' in entry.name) continue
                val entryPath = if (path.isEmpty()) entry.name else "$path/${entry.name}"
                when {
                    entry.directory -> if (ScanRules.shouldEnter(entry.name)) folders += entryPath to entry.documentId
                    ScanRules.roleOf(entry.name) != null -> {
                        documents[entryPath] = entry.documentId
                        emit(SourceFile(entryPath, entry.sizeBytes, entry.modifiedAtEpochMs))
                    }
                }
            }
            folders.asReversed().forEach(pending::addLast)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun playbackTarget(path: String): PlaybackTarget = withContext(Dispatchers.IO) {
        PlaybackTarget(tree.open(documents[path] ?: find(path)))
    }

    /** Walks from the root to [path] by name, for paths asked for before any listing in this process. */
    private fun find(path: String): String {
        var documentId = tree.rootDocumentId
        var walked = ""
        for (name in path.split('/')) {
            walked = if (walked.isEmpty()) name else "$walked/$name"
            documentId = documents[walked]
                ?: tree.children(documentId).firstOrNull { it.name == name }?.documentId?.also { documents[walked] = it }
                ?: throw NoSuchElementException("no such document: $path")
        }
        return documentId
    }

    companion object {
        const val TYPE: String = "android-folder"

        /**
         * Whether the folder [outer] is, or contains, the folder [inner], judged by the document IDs of two
         * trees from the same provider. Storage providers name documents by their path below a volume
         * (`primary:Movies/Kids`); where a provider does not, two different folders are simply not related.
         */
        fun covers(outer: String, inner: String): Boolean =
            inner == outer || inner.startsWith(if (outer.endsWith(':') || outer.endsWith('/')) outer else "$outer/")
    }
}

/** A tree as Android's document providers serve it. */
class ContentDocumentTree(private val resolver: ContentResolver, private val treeUri: Uri) : DocumentTree {
    override val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun reachable(): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!granted) return false
        // A grant outlives its storage: an ejected card or unplugged drive still has one.
        return runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    override fun children(documentId: String): List<TreeEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw IllegalStateException("the folder could not be listed")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        TreeEntry(
                            documentId = it.getString(0) ?: continue,
                            name = it.getString(1).orEmpty(),
                            directory = it.getString(2) == Document.MIME_TYPE_DIR,
                            sizeBytes = if (it.isNull(3)) 0 else it.getLong(3),
                            modifiedAtEpochMs = if (it.isNull(4)) 0 else it.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    override fun open(documentId: String): String = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
}
