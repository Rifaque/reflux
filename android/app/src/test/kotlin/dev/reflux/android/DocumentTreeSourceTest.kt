package dev.reflux.android

import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.DecoderDescription
import dev.reflux.android.storage.DocumentTree
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.android.storage.TreeEntry
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.model.Availability
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A folder tree in memory: document IDs are opaque numbers, as with providers that are not path based. */
internal class FakeTree(build: FakeTree.() -> Unit = {}) : DocumentTree {
    private val children = HashMap<String, MutableList<TreeEntry>>()
    private var next = 1
    var granted = true
    val unreadable = HashSet<String>()
    var listings = 0
    override val rootDocumentId = "root"

    init {
        build()
    }

    /** Adds a file at [path], creating the folders above it, and returns its document ID. */
    fun file(path: String, size: Long = 1_000, modified: Long = 1): String {
        var parent = rootDocumentId
        val names = path.split('/')
        for (name in names.dropLast(1)) parent = folder(parent, name)
        return "doc${next++}".also { children.getOrPut(parent) { mutableListOf() } += TreeEntry(it, names.last(), false, size, modified) }
    }

    fun remove(path: String) {
        children.values.forEach { entries -> entries.removeAll { it.name == path.substringAfterLast('/') } }
    }

    fun documentOf(folderPath: String): String = folderPath.split('/').fold(rootDocumentId) { parent, name -> folder(parent, name) }

    private fun folder(parent: String, name: String): String {
        val entries = children.getOrPut(parent) { mutableListOf() }
        return entries.firstOrNull { it.directory && it.name == name }?.documentId
            ?: "dir${next++}".also { entries += TreeEntry(it, name, true, 0, 0) }
    }

    override fun reachable() = granted

    override fun children(documentId: String): List<TreeEntry> {
        listings++
        if (!granted || documentId in unreadable) throw SecurityException("no access to $documentId")
        return children[documentId].orEmpty().toList()
    }

    override fun open(documentId: String) = "content://test/$documentId"
}

class DocumentTreeSourceTest {
    @Test
    fun listsMediaByNameFromTheRootDown() = runTest {
        val tree = FakeTree {
            file("Movies/Heat (1995)/Heat.1995.1080p.mkv", size = 42, modified = 7)
            file("Movies/Heat (1995)/Heat.1995.1080p.en.srt")
            file("Movies/Heat (1995)/poster.jpg")
            file("Movies/Heat (1995)/notes.txt")
            file("Shows/Severance/Season 1/Severance.S01E01.mkv")
            file("Movies/.hidden/Secret.mkv")
        }
        val source = DocumentTreeSource(tree, "content://tree/a", "Media")
        val files = source.files().toList()
        assertEquals(
            listOf("Movies/Heat (1995)/Heat.1995.1080p.en.srt", "Movies/Heat (1995)/Heat.1995.1080p.mkv", "Movies/Heat (1995)/poster.jpg", "Shows/Severance/Season 1/Severance.S01E01.mkv"),
            files.map { it.path },
            "media, subtitles, and artwork, in name order; hidden folders and unrelated files are left out",
        )
        assertEquals(42 to 7L, files.first { it.path.endsWith(".mkv") }.let { it.sizeBytes.toInt() to it.modifiedAtEpochMs })
        assertEquals(files.map { it.path }, source.files().toList().map { it.path }, "the same folder always lists the same way")
    }

    @Test
    fun documentsAreOpenedByTheirPath() = runTest {
        val tree = FakeTree()
        val movie = tree.file("Movies/Heat (1995)/Heat.mkv")
        val listed = DocumentTreeSource(tree, "content://tree/a", "Media")
        listed.files().toList()
        assertEquals("content://test/$movie", listed.playbackTarget("Movies/Heat (1995)/Heat.mkv").uri)

        // After a restart nothing has been listed yet: the path is walked down from the root.
        val fresh = DocumentTreeSource(tree, "content://tree/a", "Media")
        assertEquals("content://test/$movie", fresh.playbackTarget("Movies/Heat (1995)/Heat.mkv").uri)
        assertFailsWith<NoSuchElementException> { fresh.playbackTarget("Movies/Gone.mkv") }
    }

    @Test
    fun aRevokedOrMissingFolderIsUnavailableNotEmpty() = runTest {
        val tree = FakeTree { file("Movies/Heat.mkv") }
        val source = DocumentTreeSource(tree, "content://tree/a", "Media")
        assertEquals(Availability.AVAILABLE, source.availability())
        tree.granted = false
        assertEquals(Availability.UNAVAILABLE, source.availability())
        // Saying "unavailable" is what keeps the library: an empty listing would read as "everything was deleted".
        assertFailsWith<SourceUnavailableException> { source.files().toList() }
    }

    @Test
    fun anUnreadableSubfolderIsSkippedAndTheRestIsListed() = runTest {
        val tree = FakeTree {
            file("Movies/Heat.mkv")
            file("Private/Hidden.mkv")
        }
        tree.unreadable += tree.documentOf("Private")
        assertEquals(listOf("Movies/Heat.mkv"), DocumentTreeSource(tree, "content://tree/a", "Media").files().toList().map { it.path })

        tree.unreadable += tree.rootDocumentId
        assertFailsWith<SourceUnavailableException> { DocumentTreeSource(tree, "content://tree/a", "Media").files().toList() }
    }

    @Test
    fun theSameFolderIsTheSameSourceAndFoldersNestByTheirDocuments() {
        val tree = FakeTree()
        assertEquals(DocumentTreeSource(tree, "content://tree/a", "A").descriptor.id, DocumentTreeSource(tree, "content://tree/a", "Renamed").descriptor.id)
        assertFalse(DocumentTreeSource(tree, "content://tree/a", "A").descriptor.id == DocumentTreeSource(tree, "content://tree/b", "B").descriptor.id)

        assertTrue(DocumentTreeSource.covers("primary:Movies", "primary:Movies"))
        assertTrue(DocumentTreeSource.covers("primary:Movies", "primary:Movies/Kids"))
        assertTrue(DocumentTreeSource.covers("primary:", "primary:Movies"), "a whole volume covers its folders")
        assertFalse(DocumentTreeSource.covers("primary:Movies", "primary:Movies 4K"))
        assertFalse(DocumentTreeSource.covers("primary:Movies/Kids", "primary:Movies"))
        assertFalse(DocumentTreeSource.covers("primary:Movies", "1A2B-3C4D:Movies"), "the same name on another volume is another folder")
    }

    @Test
    fun capabilitiesAreWhatTheDeviceReports() {
        val display = DisplayCapabilities(2400, 1080, setOf(DynamicRange.SDR, DynamicRange.HDR10))
        val device = AndroidCapabilities.of(
            listOf(
                DecoderDescription("video/avc", hardware = true, maxWidth = 3840, maxHeight = 2160),
                DecoderDescription("video/avc", hardware = false, maxWidth = 1920, maxHeight = 1080),
                DecoderDescription("video/hevc", hardware = true, maxWidth = 3840, maxHeight = 2160, tenBit = true),
                DecoderDescription("video/x-vnd.on2.vp9", hardware = false, maxWidth = 1920, maxHeight = 1080),
                DecoderDescription("video/some-vendor-codec", hardware = true, maxWidth = 1920, maxHeight = 1080),
                DecoderDescription("audio/mp4a-latm", hardware = false),
                DecoderDescription("audio/eac3", hardware = false),
            ),
            display,
        )
        assertEquals(true, device.decoderFor(VideoCodec.H264)?.hardware)
        assertEquals(10, device.decoderFor(VideoCodec.HEVC)?.maxBitDepth)
        assertEquals(false, device.decoderFor(VideoCodec.VP9)?.hardware)
        // Nothing is assumed: this device lists no AV1 decoder, so AV1 is not claimed.
        assertNull(device.decoderFor(VideoCodec.AV1))
        assertEquals(setOf(AudioCodec.AAC, AudioCodec.EAC3, AudioCodec.PCM), device.audioDecoders)
        assertFalse(AudioCodec.TRUEHD in device.audioDecoders)
        assertEquals(display, device.display)
        assertFalse(device.display.canToneMap)
        assertFalse(PlaybackFeature.PICTURE_IN_PICTURE in device.features, "not claimed until it is built")
    }

    @Test
    fun aStickIsOnePressPerPush() {
        val stick = StickDirections()
        assertEquals(listOf(GamepadButton.DPAD_RIGHT), stick.changes(1, 0))
        assertEquals(emptyList(), stick.changes(1, 0), "holding it is not pressing it again")
        assertEquals(emptyList(), stick.changes(0, 0))
        assertEquals(listOf(GamepadButton.DPAD_RIGHT), stick.changes(1, 0))
        assertEquals(listOf(GamepadButton.DPAD_LEFT, GamepadButton.DPAD_UP), stick.changes(-1, -1))
        assertEquals(listOf(GamepadButton.DPAD_DOWN), stick.changes(-1, 1))
    }
}
