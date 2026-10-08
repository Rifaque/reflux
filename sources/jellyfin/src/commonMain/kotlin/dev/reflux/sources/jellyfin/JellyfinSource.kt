package dev.reflux.sources.jellyfin

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.StableIds
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Jellyfin as a Reflux source: an adapter, not the foundation.
 *
 * Jellyfin's items become catalog entries that Reflux identifies with its own rules; playback uses Jellyfin's
 * direct (static) streams so Reflux's own player, Best Version, and track logic apply. Server-side transcoding
 * is not used.
 *
 * Paths are adapter-defined locators: `items/{item}/sources/{mediaSource}` for versions,
 * `videos/{item}/{mediaSource}/subtitles/{index}/stream.{ext}` for external subtitles, and
 * `items/{item}/images/{type}` for artwork.
 */
class JellyfinSource(
    private val credentials: JellyfinCredentials,
    http: HttpFetcher,
    displayName: String = "Jellyfin",
    deviceName: String = "Reflux",
) : CatalogSource, WatchStateSyncSource {
    private val api = JellyfinApi(credentials.serverUrl, http, credentials.deviceId, deviceName, credentials.accessToken)
    private val startedSessions = mutableSetOf<String>()

    override val descriptor: SourceDescriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, "${credentials.serverId}/${credentials.userId}"),
        type = TYPE,
        displayName = displayName,
        locality = localityOf(credentials.serverUrl),
        capabilities = setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )

    override suspend fun availability(): Availability = try {
        api.get("System/Info/Public")
        Availability.AVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Availability.UNAVAILABLE
    }

    override fun catalog(): Flow<CatalogEntry> = flow {
        val series = try {
            fetchSeries()
        } catch (e: JellyfinException) {
            throw SourceUnavailableException(descriptor.id, e)
        }
        var start = 0
        while (true) {
            val page = try {
                api.get(
                    "Items",
                    mapOf(
                        "userId" to credentials.userId,
                        "Recursive" to "true",
                        "IncludeItemTypes" to "Movie,Episode",
                        "Fields" to "ProviderIds,MediaSources,DateCreated,PremiereDate",
                        "EnableUserData" to "true",
                        "SortBy" to "SortName",
                        "StartIndex" to start.toString(),
                        "Limit" to PAGE_SIZE.toString(),
                    ),
                ).obj()
            } catch (e: JellyfinException) {
                throw SourceUnavailableException(descriptor.id, e)
            } ?: break
            val items = page.array("Items")
            for (item in items) entry(item, series)?.let { emit(it) }
            start += items.size
            val total = page.int("TotalRecordCount") ?: start
            if (items.isEmpty() || start >= total) break
        }
    }

    override suspend fun playbackTarget(path: String): PlaybackTarget {
        val segments = path.split('/')
        val url = when {
            segments.size == 4 && segments[0] == "items" && segments[2] == "sources" -> api.url(
                "Videos/${segments[1]}/stream",
                mapOf("static" to "true", "mediaSourceId" to segments[3]),
            )
            segments.size == 6 && segments[0] == "videos" && segments[3] == "subtitles" ->
                api.url("Videos/${segments[1]}/${segments[2]}/Subtitles/${segments[4]}/${segments[5].replaceFirstChar { it.uppercase() }}")
            segments.size == 4 && segments[0] == "items" && segments[2] == "images" ->
                api.url("Items/${segments[1]}/Images/${segments[3]}")
            else -> throw IllegalArgumentException("not a Jellyfin locator: $path")
        }
        return PlaybackTarget(url, mapOf("Authorization" to api.authorization))
    }

    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) {
        val (itemId, sourceId) = versionIds(path) ?: return
        val body = playbackBody(itemId, sourceId, positionMs, paused)
        if (startedSessions.add(path)) {
            api.post("Sessions/Playing", body)
        } else {
            api.post("Sessions/Playing/Progress", body)
        }
    }

    override suspend fun reportStopped(path: String, positionMs: Long) {
        val (itemId, sourceId) = versionIds(path) ?: return
        startedSessions.remove(path)
        api.post("Sessions/Playing/Stopped", playbackBody(itemId, sourceId, positionMs, paused = false))
    }

    override suspend fun setPlayed(path: String, played: Boolean) {
        val (itemId, _) = versionIds(path) ?: return
        val params = mapOf("userId" to credentials.userId)
        if (played) api.post("UserPlayedItems/$itemId", null, params) else api.delete("UserPlayedItems/$itemId", params)
    }

    private fun versionIds(path: String): Pair<String, String>? {
        val segments = path.split('/')
        return if (segments.size == 4 && segments[0] == "items" && segments[2] == "sources") segments[1] to segments[3] else null
    }

    private fun playbackBody(itemId: String, mediaSourceId: String, positionMs: Long, paused: Boolean) = JsonObject(
        mapOf(
            "ItemId" to JsonPrimitive(itemId),
            "MediaSourceId" to JsonPrimitive(mediaSourceId),
            "PositionTicks" to JsonPrimitive(positionMs * TICKS_PER_MS),
            "IsPaused" to JsonPrimitive(paused),
            "PlayMethod" to JsonPrimitive("DirectStream"),
            "CanSeek" to JsonPrimitive(true),
        ),
    )

    private class Series(val name: String, val year: Int?, val providerIds: Map<String, String>, val artwork: Map<ArtworkKind, String>)

    private suspend fun fetchSeries(): Map<String, Series> {
        val response = api.get(
            "Items",
            mapOf(
                "userId" to credentials.userId,
                "Recursive" to "true",
                "IncludeItemTypes" to "Series",
                "Fields" to "ProviderIds,PremiereDate",
            ),
        ).obj() ?: return emptyMap()
        return response.array("Items").mapNotNull { item ->
            val id = item.string("Id") ?: return@mapNotNull null
            id to Series(item.string("Name") ?: return@mapNotNull null, item.int("ProductionYear"), providerIds(item), artwork(item, episode = false))
        }.toMap()
    }

    private fun entry(item: JsonObject, series: Map<String, Series>): CatalogEntry? {
        val id = item.string("Id") ?: return null
        val versions = item.array("MediaSources").mapNotNull { version(id, item, it) }
        if (versions.isEmpty()) return null
        val identity = when (item.string("Type")) {
            "Movie" -> ParsedMedia(
                kind = ParsedKind.MOVIE,
                title = item.string("Name") ?: return null,
                year = item.int("ProductionYear") ?: dateOf(item.string("PremiereDate"))?.year,
                externalIds = providerIds(item),
            )
            "Episode" -> {
                val show = item.string("SeriesId")?.let(series::get)
                ParsedMedia(
                    kind = ParsedKind.EPISODE,
                    title = show?.name ?: item.string("SeriesName") ?: return null,
                    year = show?.year,
                    season = item.int("ParentIndexNumber") ?: 1,
                    episode = item.int("IndexNumber"),
                    episodeEnd = item.int("IndexNumberEnd"),
                    airDate = dateOf(item.string("PremiereDate")).takeIf { item.int("IndexNumber") == null },
                    episodeTitle = item.string("Name"),
                    externalIds = show?.providerIds.orEmpty(),
                )
            }
            else -> return null
        }
        val show = item.string("SeriesId")?.let(series::get)
        return CatalogEntry(
            identity = identity,
            versions = versions,
            artwork = artwork(item, episode = identity.kind == ParsedKind.EPISODE),
            showArtwork = show?.artwork.orEmpty(),
            userState = item.obj("UserData")?.let(::userState),
        )
    }

    private fun version(itemId: String, item: JsonObject, source: JsonObject): CatalogVersion? {
        val sourceId = source.string("Id") ?: return null
        val streams = source.array("MediaStreams")
        val video = streams.firstOrNull { it.string("Type") == "Video" }?.let(::videoStream)
        val audio = streams.filter { it.string("Type") == "Audio" }.map(::audioStream)
        val embeddedSubtitles = streams.filter { it.string("Type") == "Subtitle" && !it.bool("IsExternal") }.map {
            SubtitleStream(subtitleFormat(it.string("Codec")), it.string("Language"), it.bool("IsForced"), it.bool("IsDefault"))
        }
        val externalSubtitles = streams.filter { it.string("Type") == "Subtitle" && it.bool("IsExternal") }.mapNotNull {
            val index = it.int("Index") ?: return@mapNotNull null
            val format = subtitleFormat(it.string("Codec"))
            val extension = when (format) {
                SubtitleFormat.ASS -> "ass"
                SubtitleFormat.WEBVTT -> "vtt"
                SubtitleFormat.PGS -> "sup"
                else -> "srt"
            }
            CatalogSubtitle(
                path = "videos/$itemId/$sourceId/subtitles/$index/stream.$extension",
                format = format,
                language = it.string("Language"),
                forced = it.bool("IsForced"),
                hearingImpaired = it.bool("IsHearingImpaired"),
            )
        }
        val sources = item.array("MediaSources").size
        return CatalogVersion(
            path = "items/$itemId/sources/$sourceId",
            sizeBytes = source.long("Size") ?: 0,
            modifiedAtEpochMs = instantOf(item.string("DateCreated")) ?: 0,
            stream = StreamInfo(
                container = Container.fromExtension(source.string("Container")?.substringBefore(',').orEmpty()),
                video = video,
                audio = audio,
                subtitles = embeddedSubtitles,
                durationMs = (source.long("RunTimeTicks") ?: item.long("RunTimeTicks"))?.div(TICKS_PER_MS),
                bitrateBps = source.long("Bitrate"),
            ),
            // Jellyfin names multiple versions of one item ("4K", "Director's Cut").
            edition = source.string("Name")?.takeIf { sources > 1 && it.isNotBlank() },
            subtitles = externalSubtitles,
        )
    }

    private fun videoStream(json: JsonObject) = VideoStream(
        codec = when (json.string("Codec")?.lowercase()) {
            "h264", "avc" -> VideoCodec.H264
            "hevc", "h265" -> VideoCodec.HEVC
            "av1" -> VideoCodec.AV1
            "vp9" -> VideoCodec.VP9
            "vp8" -> VideoCodec.VP8
            "mpeg2video" -> VideoCodec.MPEG2
            "mpeg4", "msmpeg4v3" -> VideoCodec.MPEG4_PART2
            "vc1" -> VideoCodec.VC1
            else -> VideoCodec.UNKNOWN
        },
        width = json.int("Width"),
        height = json.int("Height"),
        bitDepth = json.int("BitDepth"),
        dynamicRange = when (json.string("VideoRangeType")?.uppercase()) {
            "DOVI", "DOVIWITHHDR10", "DOVIWITHHLG", "DOVIWITHSDR", "DOVIWITHHDR10PLUS" -> DynamicRange.DOLBY_VISION
            "HDR10PLUS" -> DynamicRange.HDR10_PLUS
            "HDR10" -> DynamicRange.HDR10
            "HLG" -> DynamicRange.HLG
            else -> if (json.string("VideoRange") == "HDR") DynamicRange.HDR10 else DynamicRange.SDR
        },
        dolbyVisionProfile = json.int("DvProfile"),
        frameRate = json.double("RealFrameRate"),
    )

    private fun audioStream(json: JsonObject): AudioStream {
        val profile = json.string("Profile").orEmpty().lowercase()
        val title = (json.string("DisplayTitle") ?: json.string("Title")).orEmpty().lowercase()
        return AudioStream(
            codec = when (json.string("Codec")?.lowercase()) {
                "aac" -> AudioCodec.AAC
                "mp3" -> AudioCodec.MP3
                "ac3" -> AudioCodec.AC3
                "eac3" -> AudioCodec.EAC3
                "dts" -> if ("ma" in profile || "dts:x" in profile || "x" == profile) AudioCodec.DTS_HD_MA else AudioCodec.DTS
                "truehd" -> AudioCodec.TRUEHD
                "flac" -> AudioCodec.FLAC
                "alac" -> AudioCodec.ALAC
                "opus" -> AudioCodec.OPUS
                "vorbis" -> AudioCodec.VORBIS
                null -> AudioCodec.UNKNOWN
                else -> if (json.string("Codec")!!.startsWith("pcm")) AudioCodec.PCM else AudioCodec.UNKNOWN
            },
            channels = json.int("Channels"),
            language = json.string("Language"),
            atmos = "atmos" in profile || "atmos" in title,
            default = json.bool("IsDefault"),
        )
    }

    private fun subtitleFormat(codec: String?) = when (codec?.lowercase()) {
        "subrip", "srt" -> SubtitleFormat.SRT
        "ass", "ssa" -> SubtitleFormat.ASS
        "webvtt", "vtt" -> SubtitleFormat.WEBVTT
        "pgssub", "pgs", "hdmv_pgs_subtitle" -> SubtitleFormat.PGS
        "dvdsub", "vobsub", "dvd_subtitle" -> SubtitleFormat.VOBSUB
        "dvbsub" -> SubtitleFormat.DVB
        "ttml" -> SubtitleFormat.TTML
        else -> SubtitleFormat.UNKNOWN
    }

    private fun providerIds(item: JsonObject): Map<String, String> {
        val ids = item.obj("ProviderIds") ?: return emptyMap()
        return buildMap {
            ids.string("Tmdb")?.takeIf { it.isNotBlank() }?.let { put("tmdb", it) }
            ids.string("Imdb")?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
            ids.string("Tvdb")?.takeIf { it.isNotBlank() }?.let { put("tvdb", it) }
        }
    }

    private fun artwork(item: JsonObject, episode: Boolean): Map<ArtworkKind, String> {
        val id = item.string("Id") ?: return emptyMap()
        val tags = item.obj("ImageTags")
        return buildMap {
            if (tags?.string("Primary") != null) put(if (episode) ArtworkKind.THUMBNAIL else ArtworkKind.POSTER, "items/$id/images/Primary")
            if (tags?.string("Logo") != null) put(ArtworkKind.LOGO, "items/$id/images/Logo")
            if ((item["BackdropImageTags"] as? JsonArray)?.isNotEmpty() == true) {
                put(ArtworkKind.BACKDROP, "items/$id/images/Backdrop")
            }
        }
    }

    private fun userState(data: JsonObject) = SourceUserState(
        played = data.bool("Played"),
        positionMs = (data.long("PlaybackPositionTicks") ?: 0) / TICKS_PER_MS,
        playCount = data.int("PlayCount") ?: 0,
        lastPlayedAtEpochMs = instantOf(data.string("LastPlayedDate")),
        favorite = data.bool("IsFavorite"),
    )

    companion object {
        const val TYPE: String = "jellyfin"
        private const val PAGE_SIZE = 500
        private const val TICKS_PER_MS = 10_000L

        /**
         * Signs in and returns credentials to persist. Never stores the password.
         *
         * @throws JellyfinException when the server is unreachable or rejects the login.
         */
        suspend fun connect(
            serverUrl: String,
            username: String,
            password: String,
            http: HttpFetcher,
            deviceId: String,
            deviceName: String = "Reflux",
        ): JellyfinCredentials {
            val url = normalizeUrl(serverUrl)
            val api = JellyfinApi(url, http, deviceId, deviceName)
            val info = api.get("System/Info/Public").obj() ?: throw JellyfinException("not a Jellyfin server")
            val body = JsonObject(mapOf("Username" to JsonPrimitive(username), "Pw" to JsonPrimitive(password)))
            val auth = api.post("Users/AuthenticateByName", body).obj() ?: throw JellyfinException("empty login response")
            return JellyfinCredentials(
                serverUrl = url,
                serverId = auth.string("ServerId") ?: info.string("Id") ?: throw JellyfinException("missing server id"),
                userId = auth.obj("User")?.string("Id") ?: throw JellyfinException("missing user id"),
                accessToken = auth.string("AccessToken") ?: throw JellyfinException("missing access token"),
                deviceId = deviceId,
            )
        }

        /** Accepts `host`, `host:8096`, or full URLs; defaults to http. */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if ("://" in trimmed) trimmed else "http://$trimmed"
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
                host.endsWith(".home") || host.endsWith(".internal") || host == "::1" || host.startsWith("fd") || host.startsWith("fe80")
            return if (privateIp || localName) SourceLocality.LOCAL_NETWORK else SourceLocality.REMOTE
        }

        internal fun dateOf(value: String?): CalendarDate? = value?.take(10)?.let(CalendarDate::parse)

        /** Parses ISO-8601 timestamps as Jellyfin writes them (`2024-03-14T12:34:56.1234567Z`). */
        internal fun instantOf(value: String?): Long? {
            val match = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$""")
                .matchEntire(value ?: return null) ?: return null
            val g = match.groupValues
            val days = daysFromCivil(g[1].toInt(), g[2].toInt(), g[3].toInt())
            val millis = g[7].padEnd(3, '0').take(3).ifEmpty { "0" }.toLong()
            var epoch = ((days * 24 + g[4].toLong()) * 60 + g[5].toLong()) * 60_000 + g[6].toLong() * 1000 + millis
            val zone = g[8]
            if (zone.isNotEmpty() && zone != "Z") {
                val sign = if (zone[0] == '-') -1 else 1
                val digits = zone.drop(1).replace(":", "")
                epoch -= sign * (digits.take(2).toLong() * 60 + digits.drop(2).toLong()) * 60_000
            }
            return epoch
        }

        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yearOfEra = y - era * 400
            val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }
    }
}
