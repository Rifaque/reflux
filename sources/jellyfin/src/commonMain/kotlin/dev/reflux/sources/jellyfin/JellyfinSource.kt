package dev.reflux.sources.jellyfin

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.StableIds
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.PlaybackEvidence
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.NegotiatedDelivery
import dev.reflux.core.source.NegotiatingSource
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SectionedSource
import dev.reflux.core.source.SourceSection
import dev.reflux.core.source.PlaybackEvidenceSource
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Jellyfin as a Reflux source: an adapter, not the foundation.
 *
 * Jellyfin's items become catalog entries that Reflux identifies with its own rules; playback uses Jellyfin's
 * direct (static) streams so Reflux's own player, Best Version, and track logic apply. Server-side transcoding
 * is not used. `PlaybackInfo` is asked only to observe what the server would do and to name the session.
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
) : CatalogSource, WatchStateSyncSource, DownloadableSource, SectionedSource, PlaybackEvidenceSource, NegotiatingSource {
    private val api = JellyfinApi(credentials.serverUrl, http, credentials.deviceId, deviceName, credentials.accessToken)
    private val startedSessions = mutableSetOf<String>()

    /** What the server answered when each open playback began. */
    private val asked = mutableMapOf<String, Asked>()

    private class Asked(val answer: JellyfinPlaybackAnswer?, val authenticated: Boolean?, val delivery: DeliveryMode = DeliveryMode.DIRECT_PLAY)

    override val descriptor: SourceDescriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, "${credentials.serverId}/${credentials.userId}"),
        type = TYPE,
        displayName = displayName,
        locality = localityOf(credentials.serverUrl),
        capabilities = setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC, SourceCapability.DOWNLOAD) + SERVER_CONVERSIONS,
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
        // Everything the account sees, unless libraries were left out: then each included one in turn. An
        // item in two libraries is listed once.
        val parents: List<String?> = if (credentials.excludedLibraries.isEmpty()) {
            listOf(null)
        } else {
            sections().map { it.id }.filter { it !in credentials.excludedLibraries }
        }
        val seen = HashSet<String>()
        for (parent in parents) {
            var start = 0
            while (true) {
                val page = try {
                    api.get(
                        "Items",
                        listOfNotNull(
                            "userId" to credentials.userId,
                            parent?.let { "ParentId" to it },
                            "Recursive" to "true",
                            "IncludeItemTypes" to "Movie,Episode",
                            "Fields" to "ProviderIds,MediaSources,DateCreated,PremiereDate",
                            "EnableUserData" to "true",
                            "SortBy" to "SortName",
                            "StartIndex" to start.toString(),
                            "Limit" to PAGE_SIZE.toString(),
                        ).toMap(),
                    ).obj()
                } catch (e: JellyfinException) {
                    throw SourceUnavailableException(descriptor.id, e)
                } ?: break
                val items = page.array("Items")
                for (item in items) {
                    if (item.string("Id")?.let(seen::add) == false) continue
                    entry(item, series)?.let { emit(it) }
                }
                start += items.size
                val total = page.int("TotalRecordCount") ?: start
                if (items.isEmpty() || start >= total) break
            }
        }
    }

    override val excludedSections: Set<String> get() = credentials.excludedLibraries

    /**
     * The account's libraries that can hold films and series. Music, books, photos, playlists, and live
     * television are not listed: Reflux would find nothing to play in them.
     */
    override suspend fun sections(): List<SourceSection> {
        val views = try {
            api.get("UserViews", mapOf("userId" to credentials.userId))
        } catch (e: JellyfinException) {
            // Servers before 10.9 only answer the older address.
            if (e.status != 404) throw SourceUnavailableException(descriptor.id, e)
            try {
                api.get("Users/${credentials.userId}/Views")
            } catch (e: JellyfinException) {
                throw SourceUnavailableException(descriptor.id, e)
            }
        }.obj() ?: return emptyList()
        return views.array("Items").mapNotNull { view ->
            val id = view.string("Id") ?: return@mapNotNull null
            val kind = when (view.string("CollectionType")?.lowercase()) {
                "movies" -> SectionKind.MOVIES
                "tvshows" -> SectionKind.SHOWS
                null, "", "mixed", "folders" -> SectionKind.MIXED
                else -> return@mapNotNull null
            }
            SourceSection(id, view.string("Name") ?: id, kind)
        }
    }

    /** The server streams the file itself (`static`), so what plays is also what a copy is made from. */
    override suspend fun downloadTarget(path: String): PlaybackTarget = playbackTarget(path)

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

    /**
     * The server's own rule (its "maximum resume percentage", 90% unless an administrator changed it): a
     * progress report or a stop past it marks the item played and clears its position.
     */
    override val marksPlayedPast: Double = 0.90

    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) {
        val (itemId, sourceId) = versionIds(path) ?: return
        if (startedSessions.add(path)) {
            if (path !in asked) asked[path] = ask(path)
            api.post("Sessions/Playing", playbackBody(path, itemId, sourceId, positionMs, paused))
        } else {
            api.post("Sessions/Playing/Progress", playbackBody(path, itemId, sourceId, positionMs, paused))
        }
    }

    override suspend fun reportStopped(path: String, positionMs: Long) {
        val (itemId, sourceId) = versionIds(path) ?: return
        val open = startedSessions.remove(path)
        val body = playbackBody(path, itemId, sourceId, positionMs, paused = false)
        api.post("Sessions/Playing/Stopped", body)
        // A position brought up to date has nothing to do with a stream being opened for that file.
        if (open) release(path)
    }

    /**
     * With a playback of [path] open, the position goes as that playback's progress: a stop would end its
     * session on the server, and the playback's next report would open another, which the server counts as
     * another play.
     */
    override suspend fun updatePosition(path: String, positionMs: Long) {
        val (itemId, sourceId) = versionIds(path) ?: return
        if (path in startedSessions) {
            api.post("Sessions/Playing/Progress", playbackBody(path, itemId, sourceId, positionMs, paused = false))
        } else {
            reportStopped(path, positionMs)
        }
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

    /**
     * What Reflux is really doing, in Jellyfin's words: it opens the file itself ([playbackTarget]), or the stream
     * [negotiate] agreed.
     */
    private fun playbackBody(path: String, itemId: String, mediaSourceId: String, positionMs: Long, paused: Boolean) = JsonObject(
        buildMap {
            put("ItemId", JsonPrimitive(itemId))
            put("MediaSourceId", JsonPrimitive(mediaSourceId))
            put("PositionTicks", JsonPrimitive(positionMs * TICKS_PER_MS))
            put("IsPaused", JsonPrimitive(paused))
            put("PlayMethod", JsonPrimitive(playMethod(asked[path]?.delivery ?: DeliveryMode.DIRECT_PLAY)))
            put("CanSeek", JsonPrimitive(true))
            asked[path]?.answer?.playSessionId?.let { put("PlaySessionId", JsonPrimitive(it)) }
        },
    )

    /**
     * What the server says it would do with [path] for this [request]. Nothing starts on the server until a
     * stream is fetched.
     *
     * @throws JellyfinException when it could not be asked.
     */
    suspend fun playbackInfo(path: String, request: JellyfinPlaybackAsk = JellyfinPlaybackAsk()): JellyfinPlaybackAnswer? {
        val (itemId, sourceId) = requireNotNull(versionIds(path)) { "not a Jellyfin version: $path" }
        val body = JsonObject(
            buildMap<String, JsonElement> {
                put("UserId", JsonPrimitive(credentials.userId))
                put("MediaSourceId", JsonPrimitive(sourceId))
                put("DeviceProfile", Json.parseToJsonElement(request.deviceProfile))
                put("SubtitleStreamIndex", JsonPrimitive(request.subtitleStreamIndex))
                request.audioStreamIndex?.let { put("AudioStreamIndex", JsonPrimitive(it)) }
                put("EnableDirectPlay", JsonPrimitive(request.allowDirectPlay))
                put("EnableDirectStream", JsonPrimitive(request.allowConversion))
                put("EnableTranscoding", JsonPrimitive(request.allowConversion))
                put("AllowVideoStreamCopy", JsonPrimitive(request.copyVideo))
                put("AllowAudioStreamCopy", JsonPrimitive(request.copyAudio))
                put("MaxStreamingBitrate", JsonPrimitive(request.maxBitrate))
                put("AutoOpenLiveStream", JsonPrimitive(false))
            },
        )
        return api.post("Items/$itemId/PlaybackInfo", body).obj()?.let { JellyfinPlaybackAnswer.parse(it, sourceId) }
    }

    /**
     * Carries out [plan] with the server: the plan is decided, and this only says what it needs, with a true account
     * of the client. The answer is kept for reports and evidence. A plan for the original opens the file whatever
     * the server's flags say; if the server disagrees, evidence will show it.
     */
    override suspend fun negotiate(version: MediaVersion, plan: PlannedPlayback, device: DeviceCapabilities): NegotiatedDelivery? {
        val path = version.location.path
        val (itemId, sourceId) = versionIds(path) ?: return null
        val original = plan.delivery == DeliveryMode.DIRECT_PLAY
        val subtitleTrack = plan.subtitleIndex?.takeIf { !plan.subtitleExternal }?.let(version.stream.subtitles::getOrNull)
        val request = JellyfinPlaybackAsk(
            deviceProfile = JellyfinProfile.of(device, plan, version.stream),
            audioStreamIndex = plan.audioIndex?.let(version.stream.audio::getOrNull)?.sourceIndex,
            subtitleStreamIndex = subtitleTrack?.sourceIndex?.takeIf { plan.subtitles == SubtitlePlan.BURN_IN } ?: -1,
            allowConversion = !original,
            allowDirectPlay = original,
            copyVideo = plan.video != VideoPlan.TRANSCODE,
            copyAudio = plan.audio != AudioPlan.CONVERT,
            maxBitrate = JellyfinProfile.bitrateFor(plan, version.stream),
        )
        val answer = ask(path, request, plan.delivery)
        asked[path] = answer
        val subtitle = subtitleTarget(itemId, sourceId, version, plan)
        if (original) return NegotiatedDelivery(playbackTarget(path), subtitle)
        val target = answer.answer?.let(::transcodingTarget) ?: run {
            asked.remove(path)
            return null
        }
        return NegotiatedDelivery(target, subtitle)
    }

    /** The file a plan's subtitle is fetched as: as it is, or in a text format for a conversion. */
    private fun subtitleTarget(itemId: String, sourceId: String, version: MediaVersion, plan: PlannedPlayback): PlaybackTarget? {
        val converted = plan.subtitles == SubtitlePlan.CONVERT
        if (plan.subtitles != SubtitlePlan.EXTERNAL && !converted) return null
        // A sidecar is the client's to open, unless it needs converting.
        if (plan.subtitleExternal && !converted) return null
        val index = plan.subtitleIndex ?: return null
        val track = if (plan.subtitleExternal) null else version.stream.subtitles.getOrNull(index)
        val stream = track?.sourceIndex ?: return null
        val format = if (converted) "vtt" else JellyfinProfile.subtitleExtension(track.format)
        return PlaybackTarget(api.url("Videos/$itemId/$sourceId/Subtitles/$stream/0/Stream.$format"), mapOf("Authorization" to api.authorization))
    }

    override suspend fun release(path: String) {
        startedSessions.remove(path)
        val done = asked.remove(path) ?: return
        val session = done.answer?.playSessionId ?: return
        if (done.delivery == DeliveryMode.DIRECT_PLAY) return
        try {
            stopConversion(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The server ends an abandoned conversion by itself; this only makes it sooner.
        }
    }

    /** This device's session on the server, or null when the server lists none. */
    suspend fun sessionFacts(): JellyfinSessionFacts? = JellyfinSessionFacts.parse(api.get("Sessions"), credentials.deviceId)

    /** Where [answer]'s conversion is fetched. The URL carries the server's key: it is never logged unredacted. */
    fun transcodingTarget(answer: JellyfinPlaybackAnswer): PlaybackTarget? =
        answer.transcodingUrl?.let { PlaybackTarget(api.url(it.trimStart('/')), mapOf("Authorization" to api.authorization)) }

    /** Ends a conversion this device started, so that no encode outlives its viewer. */
    suspend fun stopConversion(playSessionId: String) {
        api.delete("Videos/ActiveEncodings", mapOf("deviceId" to credentials.deviceId, "playSessionId" to playSessionId))
    }

    override suspend fun playbackEvidence(path: String): PlaybackEvidence? {
        val (itemId, _) = versionIds(path) ?: return null
        val answer = asked[path] ?: ask(path)
        val session = try {
            withTimeoutOrNull(ASK_WITHIN_MS) { sessionFacts() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val original = answer.delivery == DeliveryMode.DIRECT_PLAY
        val requested = if (original) "the original file (static stream)" else "a server stream (${answer.delivery})"
        return JellyfinDelivery.evidence(requested, originalRequested = original, itemId, answer.answer, session, answer.authenticated)
    }

    /**
     * Best effort, with a short limit: this only observes, and a server that does not answer must not hold up
     * the report behind it, let alone the playback.
     */
    private suspend fun ask(path: String, request: JellyfinPlaybackAsk = JellyfinPlaybackAsk(), delivery: DeliveryMode = DeliveryMode.DIRECT_PLAY): Asked = try {
        withTimeoutOrNull(ASK_WITHIN_MS) {
            val answer = playbackInfo(path, request)
            Asked(answer, authenticated = answer != null, delivery)
        } ?: Asked(null, null, delivery)
    } catch (e: CancellationException) {
        throw e
    } catch (e: JellyfinException) {
        Asked(null, if (e.status == 401 || e.status == 403) false else null, delivery)
    } catch (_: Exception) {
        Asked(null, null, delivery)
    }

    private fun playMethod(delivery: DeliveryMode): String = when (delivery) {
        DeliveryMode.REMUX -> "DirectStream"
        DeliveryMode.AUDIO_CONVERSION, DeliveryMode.VIDEO_TRANSCODE -> "Transcode"
        else -> "DirectPlay"
    }

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
            val (title, year) = titleAndYear(item.string("Name") ?: return@mapNotNull null, item.int("ProductionYear"))
            id to Series(title, year, providerIds(item), artwork(item, episode = false))
        }.toMap()
    }

    private fun entry(item: JsonObject, series: Map<String, Series>): CatalogEntry? {
        val id = item.string("Id") ?: return null
        val versions = item.array("MediaSources").mapNotNull { version(id, item, it) }
        if (versions.isEmpty()) return null
        val identity = when (item.string("Type")) {
            "Movie" -> {
                val (title, year) = titleAndYear(item.string("Name") ?: return null, item.int("ProductionYear") ?: dateOf(item.string("PremiereDate"))?.year)
                ParsedMedia(kind = ParsedKind.MOVIE, title = title, year = year, externalIds = providerIds(item))
            }
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
            SubtitleStream(subtitleFormat(it.string("Codec")), it.string("Language"), it.bool("IsForced"), it.bool("IsDefault"), it.bool("IsHearingImpaired"), it.int("Index"))
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
                container = containerOf(source.string("Container")),
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
        profile = VideoProfile.fromName(json.string("Profile")),
        level = json.int("Level")?.takeIf { it >= 0 },
    )

    /** A server may name every format a demuxer takes (`mov,mp4,m4a,3gp`): the first one known is not the point, MP4 is. */
    private fun containerOf(names: String?): Container {
        val known = names.orEmpty().split(',').map { Container.fromExtension(it.trim()) }.filter { it != Container.UNKNOWN }
        return if (Container.MP4 in known) Container.MP4 else known.firstOrNull() ?: Container.UNKNOWN
    }

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
            sampleRate = json.int("SampleRate"),
            sourceIndex = json.int("Index"),
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

        /** What a Jellyfin server can do, each seen live (docs/PLAYBACK.md, section 3). Reflux does not ask for any of it yet. */
        private val SERVER_CONVERSIONS = setOf(
            SourceCapability.SERVER_REMUX, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE,
            SourceCapability.SERVER_SUBTITLE_EXTRACTION, SourceCapability.SERVER_SUBTITLE_CONVERSION, SourceCapability.SERVER_BURN_IN,
        )
        private const val PAGE_SIZE = 500
        private const val TICKS_PER_MS = 10_000L
        private const val ASK_WITHIN_MS = 3_000L

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
        ): JellyfinCredentials = signIn(serverUrl, username, password, http, deviceId, deviceName).first

        /** Signs in, and also says what the server calls itself. */
        internal suspend fun signIn(
            serverUrl: String,
            username: String,
            password: String,
            http: HttpFetcher,
            deviceId: String,
            deviceName: String,
        ): Pair<JellyfinCredentials, String?> {
            val url = normalizeUrl(serverUrl)
            val api = JellyfinApi(url, http, deviceId, deviceName)
            val info = api.get("System/Info/Public").obj() ?: throw JellyfinException("not a Jellyfin server")
            if (info.string("Id") == null && info.string("Version") == null) throw JellyfinException("not a Jellyfin server")
            val body = JsonObject(mapOf("Username" to JsonPrimitive(username), "Pw" to JsonPrimitive(password)))
            val auth = api.post("Users/AuthenticateByName", body).obj() ?: throw JellyfinException("empty login response")
            val credentials = JellyfinCredentials(
                serverUrl = url,
                serverId = auth.string("ServerId") ?: info.string("Id") ?: throw JellyfinException("missing server id"),
                userId = auth.obj("User")?.string("Id") ?: throw JellyfinException("missing user id"),
                accessToken = auth.string("AccessToken") ?: throw JellyfinException("missing access token"),
                deviceId = deviceId,
                userName = auth.obj("User")?.string("Name") ?: username,
            )
            return credentials to info.string("ServerName")?.takeIf { it.isNotBlank() }
        }

        /**
         * Ends the session [credentials] signed in to: `POST Sessions/Logout`, sent with the access token
         * itself, revokes that token and no other (the server looks up the one device record holding it). Not
         * the account's other sessions, other devices, or other clients; no administrator rights are needed.
         *
         * A 401 means the server no longer accepts the token (it was already ended, or expired), which is the
         * point. Anything else but the server's empty success is not taken as ended.
         *
         * @throws JellyfinException when the server could not be reached or the end not confirmed.
         */
        internal suspend fun signOut(credentials: JellyfinCredentials, http: HttpFetcher, deviceName: String) {
            val api = JellyfinApi(credentials.serverUrl, http, credentials.deviceId, deviceName, credentials.accessToken)
            try {
                api.postWithoutAnswer("Sessions/Logout")
            } catch (e: JellyfinException) {
                if (e.status != 401) throw e
            }
        }

        private val trailingYear =Regex("""^(.*\S)\s*\(((?:18|19|20)\d{2})\)$""")

        /**
         * A title as a work is known by, and its year. A server names an item it found no metadata for after
         * its folder, year and all ("Night Drive (2021)"); that year is not part of the title, and with it
         * left in, the server's copy would not be recognized as the work a local file of the same film is.
         * A year in the name that contradicts the one the server reports is left alone.
         */
        internal fun titleAndYear(name: String, year: Int?): Pair<String, Int?> {
            val match = trailingYear.matchEntire(name.trim()) ?: return name to year
            val named = match.groupValues[2].toInt()
            return if (year == null || year == named) match.groupValues[1] to named else name to year
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
