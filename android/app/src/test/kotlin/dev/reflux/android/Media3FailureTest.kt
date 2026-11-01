package dev.reflux.android

import androidx.media3.common.PlaybackException
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.playback.PlaybackFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Media3 decides by itself, through its read timeout and retries, that a source which went silent cannot be
 * reached. That failure must arrive as the `Player` contract's unreachable source, which the shared player
 * shows at once as the source having stopped, with Try again (DECISIONS.md, 2026-10-16).
 */
class Media3FailureTest {
    @Test
    fun aStreamThatIsNotWhatItsPlaylistDeclaredIsNotAnUnreachableFile() {
        val mismatch = java.io.IOException(androidx.media3.exoplayer.hls.SampleQueueMappingException("audio/mp4a-latm"))
        assertEquals(PlaybackFailure.UNKNOWN, Media3Player.failureOf(mismatch, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertEquals(PlaybackFailure.UNREACHABLE, Media3Player.failureOf(java.io.IOException("reset"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun aSourceThatWentSilentIsUnreachable() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )) {
            assertEquals(PlaybackFailure.UNREACHABLE, Media3Player.failureOf(code), "error code $code")
        }
    }

    @Test
    fun otherFailuresAreNotOfferedAsASourceProblem() {
        assertEquals(PlaybackFailure.UNSUPPORTED, Media3Player.failureOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(PlaybackFailure.UNSUPPORTED, Media3Player.failureOf(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(PlaybackFailure.OUTPUT, Media3Player.failureOf(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertEquals(PlaybackFailure.UNKNOWN, Media3Player.failureOf(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
