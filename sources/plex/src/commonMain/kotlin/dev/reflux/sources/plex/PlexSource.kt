package dev.reflux.sources.plex

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SectionedSource
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceSection
import dev.reflux.core.source.SourceSignInRequiredException
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import dev.reflux.core.net.HttpFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/**
 * A Plex Media Server as a catalog source (docs/NAVIGATION.md, section 6): its film and show libraries, each work
 * already identified by the server, played and kept from the files themselves. Plex is an adapter like any other:
 * its works join the unified library through the same identity rules, Best Version, planner, and engines.
 *
 * The server is not asked to convert anything: a file this device cannot play is said to be so by the planner.
 *
 * Paths are adapter-defined locators: `items/{ratingKey}/{server path}` for a part or an external subtitle of an
 * item (the server path is the part's or the stream's own key), and `images/{server path}` for artwork.
 */
class PlexSource(
    private val credentials: PlexCredentials,
    http: HttpFetcher,
    displayName: String = "Plex",
    deviceName: String = "Reflux",
) : CatalogSource, SectionedSource, WatchStateSyncSource, DownloadableSource {
    private val api = PlexApi(credentials.serverUrl, http, credentials.deviceId, deviceName, credentials.token)

    override val descriptor: SourceDescriptor = SourceDescriptor(
        id = idOf(credentials),
        type = TYPE,
        displayName = displayName,
        locality = localityOf(credentials.serverUrl),
        capabilities = setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC, SourceCapability.DOWNLOAD),
    )

    override suspend fun availability(): Availability = try {
        api.get("identity")
        Availability.AVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Availability.UNAVAILABLE
    }

    /** The server's film and show libraries, in its order. Music, photos, and the rest hold nothing Reflux plays. */
    override suspend fun sections(): List<SourceSection> {
        val answer = try {
            api.get("library/sections")
        } catch (e: PlexException) {
            throw unavailable(e)
        }
        return answer.container()?.array("Directory").orEmpty().mapNotNull { directory ->
            val key = directory.string("key") ?: return@mapNotNull null
            val kind = when (directory.string("type")) {
                "movie" -> SectionKind.MOVIES
                "show" -> SectionKind.SHOWS
                else -> return@mapNotNull null
            }
            SourceSection(key, directory.string("title") ?: key, kind)
        }
    }

    /**
     * Every film and episode of every library, each saying which library it is in. A library is listed a page at a
     * time, and each page's items are then read whole, with their media, parts, and streams.
     */
    override fun catalog(): Flow<CatalogEntry> = flow {
        for (section in sections()) {
            val shows = if (section.kind == SectionKind.SHOWS) fetchShows(section.id) else emptyMap()
            val type = if (section.kind == SectionKind.SHOWS) EPISODE else MOVIE
            var start = 0
            while (true) {
                val page = try {
                    api.get(
                        "library/sections/${section.id}/all",
                        mapOf("type" to type.toString(), "X-Plex-Container-Start" to start.toString(), "X-Plex-Container-Size" to PAGE_SIZE.toString()),
                    ).container()
                } catch (e: PlexException) {
                    throw unavailable(e)
                } ?: break
                val keys = page.array("Metadata").mapNotNull { it.string("ratingKey") }
                for (item in details(keys)) entry(item, shows)?.let { emit(it.copy(section = section.id)) }
                start += page.array("Metadata").size
                val total = page.int("totalSize") ?: page.int("size") ?: start
                if (keys.isEmpty() || start >= total) break
            }
        }
    }

    /** A server that answers 401 no longer accepts the token: signing in again is the way back. */
    private fun unavailable(e: PlexException): SourceUnavailableException =
        if (e.status == 401) SourceSignInRequiredException(descriptor.id, e) else SourceUnavailableException(descriptor.id, e)

    private suspend fun details(keys: List<String>): List<JsonObject> = keys.chunked(DETAIL_CHUNK).flatMap { chunk ->
        try {
            api.get("library/metadata/${chunk.joinToString(",")}", mapOf("includeGuids" to "1")).container()?.array("Metadata").orEmpty()
        } catch (e: PlexException) {
            throw unavailable(e)
        }
    }

    private class Series(val title: String, val year: Int?, val externalIds: Map<String, String>, val artwork: Map<ArtworkKind, String>)

    private suspend fun fetchShows(section: String): Map<String, Series> {
        val answer = try {
            api.get("library/sections/$section/all", mapOf("type" to SHOW.toString(), "includeGuids" to "1"))
        } catch (e: PlexException) {
            throw unavailable(e)
        }
        return answer.container()?.array("Metadata").orEmpty().mapNotNull { show ->
            val key = show.string("ratingKey") ?: return@mapNotNull null
            key to Series(show.string("title") ?: return@mapNotNull null, show.int("year"), externalIds(show), artwork(show, episode = false))
        }.toMap()
    }

    private fun entry(item: JsonObject, shows: Map<String, Series>): CatalogEntry? {
        val ratingKey = item.string("ratingKey") ?: return null
        val versions = item.array("Media").mapNotNull { version(ratingKey, item, it) }
        if (versions.isEmpty()) return null
        val show = item.string("grandparentRatingKey")?.let(shows::get)
        val identity = when (item.string("type")) {
            "movie" -> ParsedMedia(
                kind = ParsedKind.MOVIE,
                title = item.string("title") ?: return null,
                year = item.int("year") ?: dateOf(item.string("originallyAvailableAt"))?.year,
                edition = item.string("editionTitle")?.takeIf { it.isNotBlank() },
                externalIds = externalIds(item),
            )
            "episode" -> ParsedMedia(
                kind = ParsedKind.EPISODE,
                title = show?.title ?: item.string("grandparentTitle") ?: return null,
                year = show?.year,
                season = item.int("parentIndex") ?: 1,
                episode = item.int("index"),
                airDate = dateOf(item.string("originallyAvailableAt")).takeIf { item.int("index") == null },
                episodeTitle = item.string("title"),
                externalIds = show?.externalIds.orEmpty(),
            )
            else -> return null
        }
        return CatalogEntry(
            identity = identity,
            versions = versions,
            artwork = artwork(item, episode = identity.kind == ParsedKind.EPISODE),
            showArtwork = show?.artwork.orEmpty(),
            userState = userState(item),
        )
    }

    /** One version per media of an item that is a single file; a film split into parts is not one version yet (ROADMAP.md, V1.x). */
    private fun version(ratingKey: String, item: JsonObject, media: JsonObject): CatalogVersion? {
        val part = media.array("Part").singleOrNull() ?: return null
        val key = part.string("key") ?: return null
        val streams = part.array("Stream")
        val videoJson = streams.firstOrNull { it.int("streamType") == 1 }
        val video = VideoStream(
            codec = videoCodec(videoJson?.string("codec") ?: media.string("videoCodec")),
            width = videoJson?.int("width") ?: media.int("width"),
            height = videoJson?.int("height") ?: media.int("height"),
            bitDepth = videoJson?.int("bitDepth"),
            dynamicRange = dynamicRange(videoJson),
            dolbyVisionProfile = videoJson?.int("DOVIProfile"),
            frameRate = videoJson?.double("frameRate"),
            profile = VideoProfile.fromName(videoJson?.string("profile") ?: media.string("videoProfile")),
            level = videoJson?.int("level"),
        )
        val audio = streams.filter { it.int("streamType") == 2 }.map(::audioStream).ifEmpty {
            // Without the part's streams, what the media says of its main sound.
            listOfNotNull(media.string("audioCodec")?.let { AudioStream(audioCodec(it, null), media.int("audioChannels")) })
        }
        val subtitleJson = streams.filter { it.int("streamType") == 3 }
        val embedded = subtitleJson.filter { it.string("key") == null }.map {
            SubtitleStream(subtitleFormat(it.string("codec")), language(it), it.bool("forced"), it.bool("default"), it.bool("hearingImpaired"), it.int("index"))
        }
        val external = subtitleJson.mapNotNull { stream ->
            val streamKey = stream.string("key") ?: return@mapNotNull null
            CatalogSubtitle("items/$ratingKey/${streamKey.trimStart('/')}", subtitleFormat(stream.string("codec")), language(stream), stream.bool("forced"), stream.bool("hearingImpaired"))
        }
        val container = Container.fromExtension(part.string("container") ?: media.string("container") ?: key.substringAfterLast('.', ""))
        return CatalogVersion(
            path = "items/$ratingKey/${key.trimStart('/')}",
            sizeBytes = part.long("size") ?: 0,
            modifiedAtEpochMs = (item.long("updatedAt") ?: item.long("addedAt") ?: 0) * 1000,
            stream = StreamInfo(
                container = container,
                video = video.takeIf { videoJson != null || media.string("videoCodec") != null },
                audio = audio,
                subtitles = embedded,
                durationMs = part.long("duration") ?: media.long("duration") ?: item.long("duration"),
                // Plex counts in kilobits a second.
                bitrateBps = media.long("bitrate")?.times(1000),
            ),
            edition = null,
            subtitles = external,
        )
    }

    private fun audioStream(json: JsonObject): AudioStream {
        val title = listOfNotNull(json.string("displayTitle"), json.string("extendedDisplayTitle"), json.string("title")).joinToString(" ").lowercase()
        return AudioStream(
            codec = audioCodec(json.string("codec"), json.string("profile")),
            channels = json.int("channels"),
            language = language(json),
            atmos = "atmos" in title,
            default = json.bool("default") || json.bool("selected"),
            sampleRate = json.int("samplingRate"),
            sourceIndex = json.int("index"),
        )
    }

    private fun language(json: JsonObject): String? = json.string("languageCode") ?: json.string("languageTag")

    private fun dynamicRange(video: JsonObject?): DynamicRange = when {
        video == null -> DynamicRange.SDR
        video.bool("DOVIPresent") -> DynamicRange.DOLBY_VISION
        video.string("colorTrc") == "smpte2084" -> DynamicRange.HDR10
        video.string("colorTrc") == "arib-std-b67" -> DynamicRange.HLG
        else -> DynamicRange.SDR
    }

    private fun externalIds(item: JsonObject): Map<String, String> = buildMap {
        for (guid in item.array("Guid")) {
            val id = guid.string("id") ?: continue
            val scheme = id.substringBefore("://", "")
            val value = id.substringAfter("://", "").takeIf { it.isNotBlank() } ?: continue
            if (scheme in setOf("imdb", "tmdb", "tvdb")) put(scheme, value)
        }
    }

    private fun artwork(item: JsonObject, episode: Boolean): Map<ArtworkKind, String> = buildMap {
        item.string("thumb")?.let { put(if (episode) ArtworkKind.THUMBNAIL else ArtworkKind.POSTER, "images/${it.trimStart('/')}") }
        item.string("art")?.let { put(ArtworkKind.BACKDROP, "images/${it.trimStart('/')}") }
    }

    private fun userState(item: JsonObject) = SourceUserState(
        played = (item.int("viewCount") ?: 0) > 0 && item.long("viewOffset") == null,
        positionMs = item.long("viewOffset") ?: 0,
        playCount = item.int("viewCount") ?: 0,
        lastPlayedAtEpochMs = item.long("lastViewedAt")?.times(1000),
        favorite = false,
    )

    override suspend fun downloadTarget(path: String): PlaybackTarget = playbackTarget(path)

    override suspend fun playbackTarget(path: String): PlaybackTarget {
        val serverPath = when {
            path.startsWith("items/") && path.count { it == '/' } >= 2 -> path.substringAfter('/').substringAfter('/')
            path.startsWith("images/") -> path.removePrefix("images/")
            else -> throw IllegalArgumentException("not a Plex locator: $path")
        }
        return PlaybackTarget(api.url(serverPath), credentials.token?.let { mapOf("X-Plex-Token" to it) }.orEmpty())
    }

    /** Plex marks an item played once 90% of it has been watched, unless the server's owner changed that. */
    override val marksPlayedPast: Double = 0.90

    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) =
        progress(path, positionMs, if (paused) "paused" else "playing")

    override suspend fun reportStopped(path: String, positionMs: Long) = progress(path, positionMs, "stopped")

    override suspend fun setPlayed(path: String, played: Boolean) {
        val key = ratingKey(path) ?: return
        api.call(if (played) ":/scrobble" else ":/unscrobble", mapOf("key" to key, "identifier" to LIBRARY_IDENTIFIER))
    }

    private suspend fun progress(path: String, positionMs: Long, state: String) {
        val key = ratingKey(path) ?: return
        api.call(":/progress", mapOf("key" to key, "identifier" to LIBRARY_IDENTIFIER, "time" to positionMs.toString(), "state" to state))
    }

    private fun ratingKey(path: String): String? = path.takeIf { it.startsWith("items/") }?.split('/')?.getOrNull(1)

    companion object {
        const val TYPE: String = "plex"
        private const val MOVIE = 1
        private const val SHOW = 2
        private const val EPISODE = 4
        private const val PAGE_SIZE = 200
        private const val DETAIL_CHUNK = 50
        private const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"

        /** The same server for the same account is the same source, however it was reached. */
        fun idOf(credentials: PlexCredentials) = StableIds.sourceId(TYPE, "${credentials.machineId}/${credentials.account.orEmpty()}")

        /** Accepts `host`, `host:32400`, or full URLs; defaults to http and Plex's own port. */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val authority = withScheme.substringAfter("://").substringBefore('/')
            val hasPort = authority.substringAfterLast(']').contains(':')
            return if (hasPort) withScheme else withScheme.replaceFirst(authority, "$authority:32400")
        }

        /** Private addresses and single-label or `.local` hosts are on the local network. */
        fun localityOf(serverUrl: String): SourceLocality {
            val host = serverUrl.substringAfter("://").substringBefore('/').substringBeforeLast(':').removePrefix("[").removeSuffix("]").lowercase()
            val octets = host.split('.').mapNotNull { it.toIntOrNull() }
            val privateIp = octets.size == 4 && (
                octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 100 && octets[1] in 64..127)
                )
            val localName = host == "localhost" || '.' !in host || host.endsWith(".local") || host.endsWith(".lan") ||
                host.endsWith(".home") || host.endsWith(".internal") || host.endsWith(".plex.direct")
            return if (privateIp || localName) SourceLocality.LOCAL_NETWORK else SourceLocality.REMOTE
        }

        internal fun dateOf(value: String?): CalendarDate? = value?.take(10)?.let(CalendarDate::parse)

        internal fun videoCodec(name: String?): VideoCodec = when (name?.lowercase()) {
            "h264", "avc" -> VideoCodec.H264
            "hevc", "h265" -> VideoCodec.HEVC
            "av1" -> VideoCodec.AV1
            "vp9" -> VideoCodec.VP9
            "vp8" -> VideoCodec.VP8
            "mpeg2video", "mpeg2" -> VideoCodec.MPEG2
            "mpeg4", "msmpeg4v3", "divx", "xvid" -> VideoCodec.MPEG4_PART2
            "vc1" -> VideoCodec.VC1
            else -> VideoCodec.UNKNOWN
        }

        internal fun audioCodec(name: String?, profile: String?): AudioCodec {
            val detail = profile.orEmpty().lowercase()
            return when (name?.lowercase()) {
                "aac" -> AudioCodec.AAC
                "mp3" -> AudioCodec.MP3
                "ac3" -> AudioCodec.AC3
                "eac3" -> AudioCodec.EAC3
                "dca", "dts" -> if ("ma" in detail || "x" == detail) AudioCodec.DTS_HD_MA else AudioCodec.DTS
                "dca-ma" -> AudioCodec.DTS_HD_MA
                "truehd" -> AudioCodec.TRUEHD
                "flac" -> AudioCodec.FLAC
                "alac" -> AudioCodec.ALAC
                "opus" -> AudioCodec.OPUS
                "vorbis" -> AudioCodec.VORBIS
                null -> AudioCodec.UNKNOWN
                else -> if (name.startsWith("pcm")) AudioCodec.PCM else AudioCodec.UNKNOWN
            }
        }

        internal fun subtitleFormat(codec: String?) = when (codec?.lowercase()) {
            "srt", "subrip" -> SubtitleFormat.SRT
            "ass", "ssa" -> SubtitleFormat.ASS
            "vtt", "webvtt" -> SubtitleFormat.WEBVTT
            "pgs", "hdmv_pgs_subtitle" -> SubtitleFormat.PGS
            "vobsub", "dvd_subtitle" -> SubtitleFormat.VOBSUB
            "dvb_subtitle" -> SubtitleFormat.DVB
            else -> SubtitleFormat.UNKNOWN
        }
    }
}
