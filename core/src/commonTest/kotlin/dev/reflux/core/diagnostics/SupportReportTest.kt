package dev.reflux.core.diagnostics

import dev.reflux.core.BuildIdentity
import dev.reflux.core.diagnostics.SupportReport.Fact
import dev.reflux.core.diagnostics.SupportReport.SourceSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportReportTest {
    private val secrets = listOf(
        "s3cr3t-Passw0rd", "0123456789abcdef0123456789abcdef", "QWxhZGRpbjpvcGVuIHNlc2FtZQ", "sessionid=abc123",
        "192.168.1.5", "jellyfin.home.example", "rifaque", "Harbour Lights", "Severance", "My NAS",
        "C:\\Users\\rifaque", "/home/rifaque", "/storage/emulated/0/Movies", "content://com.android.externalstorage",
        "person@example.com", "fe80::1c2b:3a4d", "plex-token-value",
    )

    private fun report(log: List<String>, playback: List<Fact> = emptyList()) = SupportReport(
        build = BuildIdentity(version = "0.9.0-alpha.2", commit = "abc1234"),
        generatedAt = "2026-11-08T12:30:00Z",
        environment = listOf(Fact("Platform", "Android 16 (API 36)"), Fact("Form factor", "phone")),
        capabilities = listOf(Fact("Video", "H.264 hardware to 3840x2160, HEVC 10-bit hardware"), Fact("Display", "SDR, HDR10")),
        sources = listOf(
            SourceSummary("Jellyfin", "AVAILABLE", included = true, signInRequired = false, works = 187),
            SourceSummary("SMB", "UNAVAILABLE", included = false, signInRequired = true, works = 1),
        ),
        playback = playback,
        log = log,
        privateValues = listOf("Harbour Lights", "Severance", "My NAS", "rifaque", "jellyfin.home.example"),
    )

    @Test
    fun itSaysWhatADeveloperNeeds() {
        val text = report(
            log = listOf("2026-11-08T12:00:01.123Z  Playback stopped: decoder error -1094995529"),
            playback = listOf(Fact("How it plays", "The original file, as it is."), Fact("Sound", "E-AC-3 5.1, decoded in software.")),
        ).render()
        assertTrue(text.startsWith("Reflux support report\nReflux 0.9.0-alpha.2 (abc1234)\n"), text)
        assertTrue("Made 2026-11-08T12:30:00Z." in text, "timestamps are kept whole")
        assertTrue("Platform: Android 16 (API 36)" in text)
        assertTrue("Video: H.264 hardware to 3840x2160, HEVC 10-bit hardware" in text)
        assertTrue("1. Jellyfin: available; 187 works" in text)
        assertTrue("2. SMB: unavailable, left out of the library, asks to sign in again; 1 work" in text)
        assertTrue("Sound: E-AC-3 5.1, decoded in software." in text)
        assertTrue("2026-11-08T12:00:01.123Z  Playback stopped: decoder error -1094995529" in text)
    }

    @Test
    fun anEmptyReportStillReadsWell() {
        val text = SupportReport(BuildIdentity(version = "0.9.0-alpha.2"), "2026-11-08T12:30:00Z", emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).render()
        assertTrue("Not known yet." in text)
        assertTrue("None added." in text)
        assertTrue("Nothing has played since Reflux started." in text)
        assertTrue("None recorded." in text)
    }

    @Test
    fun noSecretSurvivesHoweverItIsWritten() {
        val log = listOf(
            "Sign-in failed: https://rifaque:s3cr3t-Passw0rd@jellyfin.home.example:8096/Users/AuthenticateByName",
            "GET http://192.168.1.5:8096/Videos/1/stream?api_key=0123456789abcdef0123456789abcdef&Static=true",
            "Authorization: MediaBrowser Client=\"Reflux\", Token=\"0123456789abcdef0123456789abcdef\"",
            "X-Plex-Token: plex-token-value",
            "Cookie: sessionid=abc123; theme=dark",
            "Bearer QWxhZGRpbjpvcGVuIHNlc2FtZQ0123456789",
            "Couldn't open C:\\Users\\rifaque\\Videos\\Harbour Lights (2024)\\Harbour.Lights.2024.mkv",
            "Couldn't open /home/rifaque/Movies/Severance/S01E01.mkv and /storage/emulated/0/Movies/a.mp4",
            "Folder content://com.android.externalstorage.documents/tree/primary%3AMovies is gone",
            "SMB //My NAS/Media: STATUS_LOGON_FAILURE",
            "Report from person@example.com via fe80::1c2b:3a4d and [fe80::1]:8096",
            "Unreachable: jellyfin.home.example:8096 and nas.local:445",
        )
        val text = report(log).render()
        for (secret in secrets) assertFalse(text.contains(secret, ignoreCase = true), "\"$secret\" survived:\n$text")
        assertTrue("<path>.mkv" in text, "a path keeps only its kind of file")
        assertTrue("STATUS_LOGON_FAILURE" in text, "the error itself is kept")
    }

    @Test
    fun wordsThatOnlyContainAPrivateValueAreKept() {
        val scrub = SupportRedaction(listOf("Her", "Up"))
        assertEquals("<private> was played; the other one was not, and Up stays", scrub("Her was played; the other one was not, and Up stays"))
    }

    @Test
    fun aStackTraceKeepsItsLinesAndTimesTheirDigits() {
        val scrub = SupportRedaction()
        assertEquals("\tat dev.reflux.android.playback.Media3Player.load(Media3Player.kt:182)", scrub("\tat dev.reflux.android.playback.Media3Player.load(Media3Player.kt:182)"))
        assertEquals("2026-11-08T12:00:00Z at 12:30:45", scrub("2026-11-08T12:00:00Z at 12:30:45"))
        assertEquals("version 0.9.0-alpha.2, commit abc1234", scrub("version 0.9.0-alpha.2, commit abc1234"))
    }

    @Test
    fun addressesOfEveryKindBecomePlaceholders() {
        val scrub = SupportRedaction()
        assertEquals("smb://<server>/…", scrub("smb://nas/share/file.mkv"))
        assertEquals("at <address> and <address>", scrub("at 10.0.0.2:8096 and ::1"))
        assertEquals("server <server> stopped", scrub("server media-box.lan:32400 stopped"))
    }
}
