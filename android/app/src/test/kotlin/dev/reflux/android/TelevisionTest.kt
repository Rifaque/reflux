package dev.reflux.android

import android.media.AudioFormat
import dev.reflux.android.engine.TreeAddition
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.AudioOutputDescription
import dev.reflux.android.playback.DecoderDescription
import dev.reflux.android.storage.StorageFolders
import dev.reflux.android.storage.StorageVolumeRoot
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.ui.state.BrowsedFolder
import dev.reflux.ui.state.FolderChoice
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a television needs of the shell that a handheld does not: its storage to choose from, and its audio output. */
class TelevisionTest {
    private fun storage(): File = Files.createTempDirectory("reflux-tv").toFile().also { root ->
        root.deleteOnExit()
        fun file(path: String) = File(root, path).also { it.parentFile.mkdirs() }.writeBytes(ByteArray(4))
        file("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        file("Movies/Alien (1979).mkv")
        file("Movies/Arrival (2016).MP4")
        file("Movies/cover.jpg")
        file("Movies/notes.txt")
        file("music/song.flac")
        file(".thumbnails/1.jpg")
        File(root, "Android").mkdirs()
        File(root, "LOST+FOUND").mkdirs()
    }

    @Test
    fun storageIsWalkedOneFolderAtATimeAndOnlyRead() = runTest {
        val root = storage()
        val before = root.walkTopDown().map { it.path to it.length() }.toList()
        var allowed = true
        val registered = mutableListOf<Pair<File, String>>()
        val folders = StorageFolders(
            volumes = { listOf(StorageVolumeRoot(root, "Internal shared storage"), StorageVolumeRoot(File(root, "ejected"), "USB drive")) },
            allowed = { allowed },
            register = { folder, name ->
                registered += folder to name
                TreeAddition.Unreadable(name)
            },
        )

        // Only storage that is there is offered, under the system's name for it.
        val storage = folders.roots()!!.single()
        assertEquals("Internal shared storage", storage.name)

        // Folders in the order a person expects, whatever their case; hidden and system ones are not shown.
        val top = folders.list(storage)
        assertEquals(listOf("Android", "Movies", "music"), top.folders.map { it.name })
        assertEquals(0, top.videos)
        val movies = folders.list(top.folders[1])
        assertEquals(listOf("Heat (1995)"), movies.folders.map { it.name })
        assertEquals(2, movies.videos, "videos are counted by what Reflux plays, not by everything in the folder")
        // A folder that is gone is empty, not an error.
        assertEquals(0, folders.list(BrowsedFolder(File(root, "nowhere").path, "nowhere")).let { it.folders.size + it.videos })

        // What is chosen is handed on by its path and its own name, and the answer is passed back in the experience layer's terms.
        assertEquals(FolderChoice.Unreadable("Movies"), folders.add(top.folders[1]))
        assertEquals(listOf(File(root, "Movies") to "Movies"), registered)

        // Nothing is listed without the user's leave.
        allowed = false
        assertNull(folders.roots())

        assertEquals(before, root.walkTopDown().map { it.path to it.length() }.toList(), "browsing changed nothing")
    }

    @Test
    fun soundLeavesTheDeviceAsItsOutputSaysItCan() {
        // Nothing attached, or an output that says nothing: stereo, decoded here.
        assertEquals(2, AndroidCapabilities.audioOutput(emptyList()).channels)
        assertTrue(AndroidCapabilities.audioOutput(listOf(AudioOutputDescription(0, emptyList()))).passthrough.isEmpty())

        // A receiver behind HDMI: its channels, and the formats it decodes itself.
        val receiver = AndroidCapabilities.audioOutput(
            listOf(
                AudioOutputDescription(2, listOf(AudioFormat.ENCODING_PCM_16BIT)),
                AudioOutputDescription(8, listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_TRUEHD)),
            ),
        )
        assertEquals(8, receiver.channels)
        assertEquals(setOf(AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.TRUEHD), receiver.passthrough)

        val decoders = listOf(DecoderDescription("audio/mp4a-latm", hardware = false))
        val television = AndroidCapabilities.of(decoders, DisplayCapabilities(3840, 2160), pictureInPicture = false, audio = receiver)
        assertEquals(8, television.maxOutputChannels)
        assertEquals(receiver.passthrough, television.audioPassthrough)
        assertTrue(PlaybackFeature.AUDIO_PASSTHROUGH in television.features)
        assertFalse(PlaybackFeature.PICTURE_IN_PICTURE in television.features)

        // Without one, nothing is claimed.
        val alone = AndroidCapabilities.of(decoders, DisplayCapabilities(1920, 1080), pictureInPicture = false)
        assertEquals(2, alone.maxOutputChannels)
        assertFalse(PlaybackFeature.AUDIO_PASSTHROUGH in alone.features)
    }
}
