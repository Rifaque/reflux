package dev.reflux.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** What may be written down about a source, and what never is. */
class RedactionTest {
    private fun clean(text: String) = Redaction.redact(text)

    @Test
    fun credentialsInAddressesParametersAndHeadersAreRemoved() {
        assertEquals("GET http://<credentials>@nas/dav", clean("GET http://alex:hunter2@nas/dav"))
        assertFalse("hunter2" in clean("https://jf/Videos/1/stream?static=true&api_key=hunter2"))
        assertFalse("hunter2" in clean("Authorization: Basic hunter2"))
        assertFalse("hunter2" in clean("X-Emby-Token: hunter2"))
    }

    @Test
    fun aJellyfinTokenQuotedOnItsOwnIsRemoved() {
        // Jellyfin's authorization value, as an exception or a player may quote it without the header's name.
        val quoted = clean("""request failed with MediaBrowser Client="Reflux", Device="Desk", DeviceId="d1", Version="0.1.0", Token="abc123def"""")
        assertFalse("abc123def" in quoted)
        assertEquals(true, "Client=\"Reflux\"" in quoted, "what is not secret stays, so the line is still useful")
    }

    @Test
    fun aShareStreamsAddressCarriesNoToken() {
        // The loopback stream server's per-file tokens let any local process read that file while Reflux runs.
        val line = clean("Playback failed: http://127.0.0.1:40177/0f1e2d3c4b5a69788796a5b4c3d2e1f0/Night_Drive.mkv")
        assertFalse("0f1e2d3c4b5a69788796a5b4c3d2e1f0" in line)
        assertEquals(true, "Night_Drive.mkv" in line)
    }

    @Test
    fun aPlexTokenHeaderIsRedacted() {
        val line = clean("GET http://192.168.1.20:32400/library/sections X-Plex-Token: abcdef123456")
        assertFalse("abcdef123456" in line)
    }
}
