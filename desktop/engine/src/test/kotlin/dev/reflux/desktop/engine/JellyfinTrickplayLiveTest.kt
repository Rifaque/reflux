package dev.reflux.desktop.engine

import dev.reflux.core.playback.BifIndex
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trickplay previews from a real Jellyfin server (B-030): the manifest the server describes and each of its sheets, read
 * through the adapter as the player does. Runs when a server is named ([LiveJellyfin]); read-only. The server must have
 * generated trickplay images for a film (its library's "trickplay image extraction" on, and the scheduled task run):
 * `tools/ci/jellyfin-test-server.sh`, then the library option and the task, as VERIFICATION.md, "Phase 3", records.
 */
class JellyfinTrickplayLiveTest {
    @Test
    fun aFilmsPreviewsAreDescribedAndEverySheetIsAnImageOfThatSize() = runBlocking {
        val live = LiveJellyfin.settings()
        assumeTrue("no live Jellyfin server named (environment or ~/.reflux-live/jellyfin.env)", live != null)
        val http = JdkHttpFetcher()
        val jellyfin = JellyfinSource(JellyfinSource.connect(live!!.url, live.user, live.password, http, deviceId = "reflux-live-trickplay"), http, "Live test")
        val version = jellyfin.catalog().toList().flatMap { it.versions }.firstOrNull { (it.stream.durationMs ?: 0) > 0 }
        val path = assertNotNull(version, "the test user's library needs a film").path
        val manifest = assertNotNull(jellyfin.trickplay(path), "the server has generated trickplay images for $path")
        assertTrue(manifest.intervalMs > 0 && manifest.count > 0 && manifest.columns >= 1 && manifest.rows >= 1)
        val duration = version.stream.durationMs!!
        assertTrue(manifest.count.toLong() * manifest.intervalMs in (duration - manifest.intervalMs * 2)..(duration + manifest.intervalMs * 2), "the pictures span the film: $manifest against $duration ms")
        for (sheet in 0 until manifest.sheets) {
            val bytes = assertNotNull(jellyfin.trickplaySheet(path, manifest, sheet), "sheet $sheet")
            val (width, height) = assertNotNull(BifIndex.jpegSize(bytes), "sheet $sheet is a JPEG")
            assertEquals(manifest.frameWidth * manifest.columns, width, "sheet $sheet is the grid's width")
            assertTrue(height <= manifest.frameHeight * manifest.rows && height % manifest.frameHeight == 0, "sheet $sheet holds whole rows: $height")
        }
        assertNull(jellyfin.trickplaySheet(path, manifest, manifest.sheets), "nothing past the last sheet")
        println("live trickplay: $manifest, ${manifest.sheets} sheets read")
    }
}
