package dev.reflux.sources.jellyfin

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Responses follow Jellyfin 10.9+ API shapes, trimmed to the fields Reflux reads. */
class JellyfinSourceTest {
    private val requests = mutableListOf<HttpRequest>()
    private var online = true

    private val movieJson = """
        {"Id":"m1","Name":"Heat","Type":"Movie","ProductionYear":1995,"RunTimeTicks":102000000000,
         "DateCreated":"2024-03-14T12:34:56.1234567Z",
         "ProviderIds":{"Tmdb":"949","Imdb":"tt0113277"},
         "ImageTags":{"Primary":"p1","Logo":"l1"},"BackdropImageTags":["b1"],
         "UserData":{"PlaybackPositionTicks":12000000000,"PlayCount":1,"IsFavorite":true,"Played":false,"LastPlayedDate":"2024-05-01T20:00:00Z"},
         "MediaSources":[
           {"Id":"s1","Name":"Heat 4K","Container":"mkv","Size":40000000000,"Bitrate":60000000,
            "MediaStreams":[
              {"Type":"Video","Codec":"hevc","Width":3840,"Height":2160,"BitDepth":10,"VideoRange":"HDR","VideoRangeType":"DOVIWithHDR10","DvProfile":8,"RealFrameRate":23.976},
              {"Type":"Audio","Codec":"truehd","Channels":8,"Language":"eng","IsDefault":true,"Profile":"TrueHD + Atmos"},
              {"Type":"Subtitle","Codec":"PGSSUB","Language":"eng","IsForced":false,"IsExternal":false,"Index":2},
              {"Type":"Subtitle","Codec":"subrip","Language":"fre","IsForced":true,"IsExternal":true,"Index":3}]},
           {"Id":"s2","Name":"Heat 1080p","Container":"mp4","Size":8000000000,
            "MediaStreams":[{"Type":"Video","Codec":"h264","Width":1920,"Height":1080,"VideoRange":"SDR"},
                            {"Type":"Audio","Codec":"ac3","Channels":6,"Language":"eng"}]}]}
    """.trimIndent()

    private val episodeJson = """
        {"Id":"e1","Name":"Pilot","Type":"Episode","SeriesId":"show1","SeriesName":"Breaking Bad","ParentIndexNumber":1,
         "IndexNumber":1,"ImageTags":{"Primary":"t"},"MediaSources":[{"Id":"e1","Container":"mkv","Size":1,"MediaStreams":[]}]}
    """.trimIndent()

    private val http = HttpFetcher { request ->
        requests += request
        if (!online) error("offline")
        val path = request.url.removePrefix("https://jf.example.com/").substringBefore('?')
        val query = request.url.substringAfter('?', "")
        val body = when (path) {
            "System/Info/Public" -> """{"ServerName":"Home","Version":"10.10.3","Id":"srv1"}"""
            "Users/AuthenticateByName" -> """{"AccessToken":"tok","ServerId":"srv1","User":{"Id":"u1","Name":"alex"}}"""
            "Items" -> when {
                "IncludeItemTypes=Series" in query ->
                    """{"Items":[{"Id":"show1","Name":"Breaking Bad","ProductionYear":2008,"ProviderIds":{"Tvdb":"81189","Tmdb":"1396"},"ImageTags":{"Primary":"sp"},"BackdropImageTags":["sb"]}],"TotalRecordCount":1}"""
                "StartIndex=0" in query -> """{"Items":[$movieJson],"TotalRecordCount":2}"""
                "StartIndex=1" in query -> """{"Items":[$episodeJson],"TotalRecordCount":2}"""
                else -> """{"Items":[],"TotalRecordCount":2}"""
            }
            else -> ""
        }
        HttpResult(if (path == "missing") 404 else 200, body.encodeToByteArray())
    }

    private val credentials = JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1")
    private val source = JellyfinSource(credentials, http)

    @Test
    fun connectSignsInWithoutKeepingThePassword() = runTest {
        val connected = JellyfinSource.connect("https://jf.example.com/", "alex", "secret", http, "dev1")
        assertEquals(credentials, connected)
        val login = requests.single { it.url.endsWith("Users/AuthenticateByName") }
        assertEquals("POST", login.method)
        assertTrue("\"Pw\":\"secret\"" in login.body!!.decodeToString())
        assertTrue("Client=\"Reflux\"" in login.headers.getValue("Authorization"))
        assertTrue("secret" !in connected.encode())
        assertEquals(connected, JellyfinCredentials.decode(connected.encode()))
    }

    @Test
    fun catalogMapsMoviesWithAllVersions() = runTest {
        val entries = source.catalog().toList()
        assertEquals(2, entries.size)
        val heat = entries.first()
        assertEquals(ParsedKind.MOVIE, heat.identity.kind)
        assertEquals("Heat", heat.identity.title)
        assertEquals(1995, heat.identity.year)
        assertEquals(mapOf("tmdb" to "949", "imdb" to "tt0113277"), heat.identity.externalIds)
        assertEquals(listOf("items/m1/sources/s1", "items/m1/sources/s2"), heat.versions.map { it.path })
        assertEquals(listOf("Heat 4K", "Heat 1080p"), heat.versions.map { it.edition })

        val uhd = heat.versions.first()
        assertEquals(Container.MATROSKA, uhd.stream.container)
        assertEquals(VideoCodec.HEVC, uhd.stream.video?.codec)
        assertEquals(DynamicRange.DOLBY_VISION, uhd.stream.video?.dynamicRange)
        assertEquals(8, uhd.stream.video?.dolbyVisionProfile)
        assertEquals(AudioCodec.TRUEHD, uhd.stream.audio.single().codec)
        assertTrue(uhd.stream.audio.single().atmos)
        assertEquals(SubtitleFormat.PGS, uhd.stream.subtitles.single().format)
        assertEquals(10_200_000L, uhd.stream.durationMs)
        assertEquals("videos/m1/s1/subtitles/3/stream.srt", uhd.subtitles.single().path)
        assertTrue(uhd.subtitles.single().forced)
        assertEquals(1_710_419_696_123L, uhd.modifiedAtEpochMs)

        assertEquals(
            mapOf(ArtworkKind.POSTER to "items/m1/images/Primary", ArtworkKind.LOGO to "items/m1/images/Logo", ArtworkKind.BACKDROP to "items/m1/images/Backdrop"),
            heat.artwork,
        )
        val state = heat.userState!!
        assertEquals(1_200_000L, state.positionMs)
        assertTrue(state.favorite)
        assertEquals(1_714_593_600_000L, state.lastPlayedAtEpochMs)
    }

    @Test
    fun episodesTakeShowIdentityFromTheSeries() = runTest {
        val episode = source.catalog().toList().last()
        assertEquals(ParsedKind.EPISODE, episode.identity.kind)
        assertEquals("Breaking Bad", episode.identity.title)
        assertEquals(2008, episode.identity.year)
        assertEquals(1, episode.identity.season)
        assertEquals(1, episode.identity.episode)
        assertEquals("Pilot", episode.identity.episodeTitle)
        assertEquals(mapOf("tmdb" to "1396", "tvdb" to "81189"), episode.identity.externalIds)
        assertEquals(mapOf(ArtworkKind.THUMBNAIL to "items/e1/images/Primary"), episode.artwork)
        assertEquals(ArtworkKind.POSTER, episode.showArtwork.keys.first())
    }

    @Test
    fun playbackUsesDirectStreamsWithAuthorization() = runTest {
        val target = source.playbackTarget("items/m1/sources/s1")
        assertEquals("https://jf.example.com/Videos/m1/stream?static=true&mediaSourceId=s1", target.uri)
        assertTrue("Token=\"tok\"" in target.headers.getValue("Authorization"))
        assertEquals("https://jf.example.com/Videos/m1/s1/Subtitles/3/Stream.srt", source.playbackTarget("videos/m1/s1/subtitles/3/stream.srt").uri)
        assertEquals("https://jf.example.com/Items/m1/Images/Primary", source.playbackTarget("items/m1/images/Primary").uri)
    }

    @Test
    fun watchStateIsReportedBack() = runTest {
        source.reportProgress("items/m1/sources/s1", 60_000, paused = false)
        source.reportProgress("items/m1/sources/s1", 70_000, paused = true)
        source.reportStopped("items/m1/sources/s1", 80_000)
        source.setPlayed("items/m1/sources/s1", true)
        val posts = requests.map { it.method + " " + it.url.removePrefix("https://jf.example.com/") }
        assertEquals(
            listOf("POST Sessions/Playing", "POST Sessions/Playing/Progress", "POST Sessions/Playing/Stopped", "POST UserPlayedItems/m1?userId=u1"),
            posts,
        )
        assertTrue("\"PositionTicks\":700000000" in requests[1].body!!.decodeToString())
        assertTrue("\"IsPaused\":true" in requests[1].body!!.decodeToString())
    }

    @Test
    fun offlineServer() = runTest {
        online = false
        assertEquals(Availability.UNAVAILABLE, source.availability())
        assertFailsWith<SourceUnavailableException> { source.catalog().toList() }
    }

    @Test
    fun localityAndUrls() {
        assertEquals(SourceLocality.LOCAL_NETWORK, JellyfinSource.localityOf("http://192.168.1.20:8096"))
        assertEquals(SourceLocality.LOCAL_NETWORK, JellyfinSource.localityOf("http://nas.local:8096"))
        assertEquals(SourceLocality.LOCAL_NETWORK, JellyfinSource.localityOf("http://jellyfin:8096"))
        assertEquals(SourceLocality.REMOTE, JellyfinSource.localityOf("https://jf.example.com"))
        assertEquals("http://nas:8096", JellyfinSource.normalizeUrl(" nas:8096/ "))
        assertNull(JellyfinSource.instantOf("yesterday"))
        assertEquals(0L, JellyfinSource.instantOf("1970-01-01T00:00:00Z"))
        assertEquals(3_600_000L, JellyfinSource.instantOf("1970-01-01T00:00:00-01:00"))
    }
}
