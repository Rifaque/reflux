package dev.reflux.metadata.trakt

import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderAttribution
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The Trakt API v2 adapter (the Standard API, no user account; docs/product/METADATA.md, "Provider").
 *
 * The application's Client ID is sent as `trakt-api-key`. It identifies the application and is not a secret; there
 * is no client secret and no OAuth here, because looking up a title needs neither. It comes from the build, never
 * from the user, and is never logged or put in an error message.
 *
 * What is asked for is what Reflux shows: summaries with `extended=full,images`, the people endpoint for credits, the
 * studios endpoint for a film's studios, the seasons endpoints for a show's seasons and episodes, and a translation
 * only when the viewer's language is not English. Image URLs are Trakt's own, as it returns them (with `https://`
 * added, as its documentation says); the library downloads them into its artwork cache and the interface draws
 * only cached files, because Trakt does not allow its images to be loaded from its CDN by an app.
 */
class TraktProvider(
    clientId: String,
    private val http: HttpFetcher,
    private val baseUrl: String = "https://api.trakt.tv",
    private val limiter: TraktRateLimiter = TraktRateLimiter(),
    private val userAgent: String = "Reflux",
) : MetadataProvider {
    init {
        require(clientId.isNotBlank()) { "Trakt needs the application's Client ID" }
    }

    private val headers = mapOf(
        "trakt-api-key" to clientId.trim(),
        "trakt-api-version" to "2",
        "Accept" to "application/json",
        "Content-Type" to "application/json",
        "User-Agent" to userAgent,
    )

    override val id: String = ID

    override val attribution: ProviderAttribution get() = ATTRIBUTION

    private val memoLock = Mutex()
    private val genreNames = mutableMapOf<String, Map<String, String>>()
    private val showZones = LinkedHashMap<String, String?>()

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        val type = type(query.kind)
        // One search by title. The year is not sent: a wrong year in a file name must not hide the right work, and
        // the matcher weighs the year of every candidate itself (MetadataMatcher).
        val results = getArray(
            "search/$type",
            mapOf("query" to escapeQuery(query.title), "extended" to "full,images", "limit" to SEARCH_LIMIT.toString()),
        ).orEmpty()
        return results.mapNotNull { element ->
            val result = element as? JsonObject ?: return@mapNotNull null
            if (result.string("type") != type) return@mapNotNull null
            result.obj(type)?.let { candidate(query.kind, it) }
        }
    }

    override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? {
        if (source !in ID_TYPES || id.isBlank()) return null
        // Trakt keys TVDB IDs to shows and episodes only.
        if (source == "tvdb" && kind == MetadataKind.MOVIE) return null
        val type = type(kind)
        val results = getArray("search/$source/${encode(id)}", mapOf("type" to type)).orEmpty()
        val match = results.firstNotNullOfOrNull { element ->
            (element as? JsonObject)?.takeIf { it.string("type") == type }?.obj(type)
        } ?: return null
        return traktId(match)?.let { ProviderRef(ID, kind, it) }
    }

    override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? = when (ref.kind) {
        MetadataKind.MOVIE -> movie(ref, language)
        MetadataKind.SHOW -> show(ref, language)
    }

    override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? {
        val info = getObject("shows/${ref.id}/seasons/$seasonNumber/info", mapOf("extended" to "full,images")) ?: return null
        val episodes = getArray("shows/${ref.id}/seasons/$seasonNumber", mapOf("extended" to "full,images")).orEmpty()
        val zone = zoneOf(ref.id)
        return SeasonMetadata(
            number = info.int("number") ?: seasonNumber,
            title = info.string("title")?.takeIf(String::isNotBlank),
            overview = info.string("overview")?.takeIf(String::isNotBlank),
            posterUrl = image(info.obj("images"), "poster"),
            airDate = localDate(info.string("first_aired"), zone),
            episodes = episodes.mapNotNull { element ->
                val episode = element as? JsonObject ?: return@mapNotNull null
                EpisodeMetadata(
                    seasonNumber = episode.int("season") ?: seasonNumber,
                    episodeNumber = episode.int("number") ?: return@mapNotNull null,
                    title = episode.string("title")?.takeIf(String::isNotBlank),
                    overview = episode.string("overview")?.takeIf(String::isNotBlank),
                    airDate = localDate(episode.string("first_aired"), zone),
                    runtimeMinutes = episode.int("runtime")?.takeIf { it > 0 },
                    stillUrl = image(episode.obj("images"), "screenshot"),
                    rating = rating(episode),
                    // Trakt lists an episode's guest cast only per episode; one request per episode is not worth it.
                )
            },
        )
    }

    private suspend fun movie(ref: ProviderRef, language: String): WorkMetadata? {
        val json = getObject("movies/${ref.id}", mapOf("extended" to "full,images")) ?: return null
        val people = getObject("movies/${ref.id}/people", mapOf("extended" to "images"))
        val studios = getArray("movies/${ref.id}/studios", emptyMap()).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) }
        val translated = translation("movies", ref.id, language)
        val directors = crew(people, "directing") { "Director" in it }.mapNotNull { person(it, CreditRole.DIRECTOR) }
        return WorkMetadata(
            ref = ref,
            title = translated?.string("title")?.takeIf(String::isNotBlank) ?: json.string("title") ?: "",
            originalTitle = json.string("original_title")?.takeIf(String::isNotBlank),
            overview = translated?.string("overview")?.takeIf(String::isNotBlank) ?: json.string("overview")?.takeIf(String::isNotBlank),
            tagline = translated?.string("tagline")?.takeIf(String::isNotBlank) ?: json.string("tagline")?.takeIf(String::isNotBlank),
            genres = genres("movies", json),
            runtimeMinutes = json.int("runtime")?.takeIf { it > 0 },
            releaseDate = json.string("released")?.let(CalendarDate::parse),
            rating = rating(json),
            contentRating = json.string("certification")?.takeIf(String::isNotBlank),
            studios = studios.take(2),
            credits = directors + cast(people),
            artwork = artwork(json.obj("images")),
            externalIds = externalIds(json),
            // Trakt groups no films into franchises; nothing is invented for it.
            collection = null,
        )
    }

    private suspend fun show(ref: ProviderRef, language: String): WorkMetadata? {
        val json = getObject("shows/${ref.id}", mapOf("extended" to "full,images")) ?: return null
        val zone = json.obj("airs")?.string("timezone")?.takeIf(String::isNotBlank)
        remember(ref.id, zone)
        val people = getObject("shows/${ref.id}/people", mapOf("extended" to "images"))
        val translated = translation("shows", ref.id, language)
        val creators = (crew(people, "created by") { true } + crew(people, "creator") { true })
            .mapNotNull { person(it, CreditRole.CREATOR) }
            .distinctBy { it.name }
        return WorkMetadata(
            ref = ref,
            title = translated?.string("title")?.takeIf(String::isNotBlank) ?: json.string("title") ?: "",
            originalTitle = json.string("original_title")?.takeIf(String::isNotBlank),
            overview = translated?.string("overview")?.takeIf(String::isNotBlank) ?: json.string("overview")?.takeIf(String::isNotBlank),
            tagline = translated?.string("tagline")?.takeIf(String::isNotBlank) ?: json.string("tagline")?.takeIf(String::isNotBlank),
            genres = genres("shows", json),
            runtimeMinutes = json.int("runtime")?.takeIf { it > 0 },
            releaseDate = localDate(json.string("first_aired"), zone),
            endDate = localDate(json.string("last_aired"), zone),
            rating = rating(json),
            contentRating = json.string("certification")?.takeIf(String::isNotBlank),
            studios = listOfNotNull(json.string("network")?.takeIf(String::isNotBlank)),
            credits = creators + cast(people),
            artwork = artwork(json.obj("images")),
            externalIds = externalIds(json),
        )
    }

    private fun candidate(kind: MetadataKind, json: JsonObject): MetadataCandidate? {
        val id = traktId(json) ?: return null
        val title = json.string("title")?.takeIf(String::isNotBlank) ?: return null
        return MetadataCandidate(
            ref = ProviderRef(ID, kind, id),
            title = title,
            originalTitle = json.string("original_title")?.takeIf(String::isNotBlank),
            year = json.int("year"),
            overview = json.string("overview")?.takeIf(String::isNotBlank),
            // How many people rated it: what breaks a tie between equally good titles (MetadataMatcher).
            popularity = json.int("votes")?.toDouble() ?: 0.0,
            posterUrl = image(json.obj("images"), "poster"),
        )
    }

    /**
     * The translation for the viewer's language, or null for English or when Trakt has none. Trakt can split one
     * language over several entries (a title in one with no country, the overview in the region's), so each field is
     * taken from the first entry that has it: the viewer's region first, then the rest in Trakt's order.
     */
    private suspend fun translation(type: String, id: String, language: String): JsonObject? {
        val code = language.substringBefore('-').lowercase()
        if (code.isBlank() || code == "en") return null
        val region = language.substringAfter('-', "").lowercase()
        val all = getArray("$type/$id/translations/${encode(code)}", emptyMap()).orEmpty().mapNotNull { it as? JsonObject }
        if (all.isEmpty()) return null
        val ordered = all.sortedBy { if (region.isNotEmpty() && it.string("country")?.lowercase() == region) 0 else 1 }
        return JsonObject(
            listOf("title", "overview", "tagline").mapNotNull { field ->
                ordered.firstNotNullOfOrNull { it.string(field)?.takeIf(String::isNotBlank) }?.let { field to JsonPrimitive(it) }
            }.toMap(),
        )
    }

    /** Trakt names genres by slug; their names come from its genre list, read once per kind. */
    private suspend fun genres(type: String, json: JsonObject): List<String> {
        val slugs = json.array("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        if (slugs.isEmpty()) return emptyList()
        val names = memoLock.withLock { genreNames[type] } ?: try {
            getArray("genres/$type", emptyMap()).orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { genre -> genre.string("slug")?.let { slug -> genre.string("name")?.let { slug to it } } }
                .toMap().also { list -> memoLock.withLock { genreNames[type] = list } }
        } catch (_: MetadataUnavailableException) {
            emptyMap() // asked again next time; a readable name is made from the slug meanwhile
        }
        return slugs.map { names[it] ?: readableSlug(it) }
    }

    /** The time zone a show airs in, to give its episodes their local air dates; read once per show. */
    private suspend fun zoneOf(showId: String): String? {
        memoLock.withLock { if (showZones.containsKey(showId)) return showZones[showId] }
        val zone = getObject("shows/$showId", mapOf("extended" to "full"))?.obj("airs")?.string("timezone")?.takeIf(String::isNotBlank)
        remember(showId, zone)
        return zone
    }

    private suspend fun remember(showId: String, zone: String?) = memoLock.withLock {
        showZones.remove(showId)
        showZones[showId] = zone
        while (showZones.size > MAX_REMEMBERED_SHOWS) showZones.remove(showZones.keys.first())
    }

    private fun crew(people: JsonObject?, department: String, job: (List<String>) -> Boolean): List<JsonObject> =
        people?.obj("crew")?.array(department).orEmpty().mapNotNull { it as? JsonObject }.filter { member ->
            job(member.array("jobs").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } + listOfNotNull(member.string("job")))
        }

    private fun cast(people: JsonObject?): List<Credit> =
        people?.array("cast").orEmpty().mapNotNull { (it as? JsonObject)?.let { entry -> person(entry, CreditRole.ACTOR) } }

    private fun person(entry: JsonObject, role: CreditRole): Credit? {
        val person = entry.obj("person") ?: return null
        val character = if (role != CreditRole.ACTOR) null else entry.string("character")?.takeIf(String::isNotBlank)
            ?: entry.array("characters").orEmpty().firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        return Credit(
            name = person.string("name")?.takeIf(String::isNotBlank) ?: return null,
            role = role,
            character = character,
            profileUrl = image(entry.obj("images") ?: person.obj("images"), "headshot"),
        )
    }

    /** Trakt's poster, fanart, and logo, as one witness among the others (docs/product/METADATA.md, "Artwork precedence"). */
    private fun artwork(images: JsonObject?): List<RemoteArtwork> = buildList {
        for ((key, kind) in listOf("poster" to ArtworkKind.POSTER, "fanart" to ArtworkKind.BACKDROP, "logo" to ArtworkKind.LOGO)) {
            // Trakt gives no language or vote for an image; the model's defaults stand.
            images?.array(key).orEmpty().mapNotNull { imageUrl(it) }.forEach { add(RemoteArtwork(kind, it)) }
        }
    }

    private fun externalIds(json: JsonObject): Map<String, String> {
        val ids = json.obj("ids") ?: return emptyMap()
        return buildMap {
            traktId(json)?.let { put(ID, it) }
            ids.string("imdb")?.takeIf(String::isNotBlank)?.let { put("imdb", it) }
            ids.string("tmdb")?.takeIf(String::isNotBlank)?.let { put("tmdb", it) }
            ids.string("tvdb")?.takeIf(String::isNotBlank)?.let { put("tvdb", it) }
        }
    }

    private fun traktId(json: JsonObject): String? = json.obj("ids")?.string("trakt")?.takeIf(String::isNotBlank)

    private fun rating(json: JsonObject): Double? =
        json.double("rating")?.takeIf { it > 0 && (json.int("votes") ?: 1) > 0 }

    private fun image(images: JsonObject?, key: String): String? = images?.array(key).orEmpty().firstNotNullOfOrNull { imageUrl(it) }

    private fun imageUrl(element: JsonElement): String? {
        val value = (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf(String::isNotBlank) ?: return null
        return when {
            value.startsWith("https://") -> value
            value.startsWith("http://") -> "https://" + value.removePrefix("http://")
            value.startsWith("//") -> "https:$value"
            else -> "https://$value"
        }
    }

    private suspend fun getObject(path: String, params: Map<String, String>): JsonObject? = get(path, params)?.let {
        it as? JsonObject ?: throw MetadataUnavailableException("Trakt sent a response Reflux cannot read")
    }

    private suspend fun getArray(path: String, params: Map<String, String>): JsonArray? = get(path, params)?.let {
        it as? JsonArray ?: throw MetadataUnavailableException("Trakt sent a response Reflux cannot read")
    }

    /**
     * GET a JSON document; null when Trakt has no such record (404) or nothing to say (204). Throws
     * [MetadataUnavailableException] when Trakt cannot be used now, with how long to wait when Trakt said or it is
     * known. Cancellation is passed on as it is, never turned into a provider failure.
     */
    private suspend fun get(path: String, params: Map<String, String>): JsonElement? {
        val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val url = "$baseUrl/$path" + if (query.isEmpty()) "" else "?$query"
        repeat(MAX_ATTEMPTS) { attempt ->
            limiter.acquire()
            val result = try {
                http.get(url, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw MetadataUnavailableException("Trakt unreachable", e)
            }
            limiter.observe(result.status, result.headers)
            when (result.status) {
                in 200..299 -> {
                    if (result.status == 204 || result.body.isEmpty()) return null
                    return try {
                        Json.parseToJsonElement(result.text())
                    } catch (e: IllegalArgumentException) {
                        throw MetadataUnavailableException("Trakt sent a response Reflux cannot read", e)
                    }
                }
                404 -> return null
                429 -> {
                    val wait = limiter.rateLimited(result.headers)
                    // A short wait is waited out by the next turn; a long one is the caller's to come back after.
                    if (attempt == MAX_ATTEMPTS - 1 || wait > limiter.maxWaitMs) {
                        throw MetadataUnavailableException("Trakt rate limit", retryAfterMs = wait)
                    }
                }
                401, 403 -> throw MetadataUnavailableException("Trakt refused the application's Client ID (HTTP ${result.status})")
                in 500..599 -> {
                    limiter.pause(SERVER_PAUSE_MS)
                    throw MetadataUnavailableException("Trakt is unavailable (HTTP ${result.status})", retryAfterMs = SERVER_PAUSE_MS)
                }
                else -> throw MetadataUnavailableException("Trakt returned HTTP ${result.status}")
            }
        }
        throw MetadataUnavailableException("Trakt rate limit")
    }

    companion object {
        const val ID: String = "trakt"
        private const val MAX_ATTEMPTS = 3
        private const val SEARCH_LIMIT = 20
        private const val MAX_REMEMBERED_SHOWS = 512
        /** Trakt's own advice for an overloaded server: try again in 30 seconds. */
        private const val SERVER_PAUSE_MS = 30_000L
        private val ID_TYPES = setOf("trakt", "imdb", "tmdb", "tvdb")

        /**
         * What Trakt's API use policy asks of an application: this notice, in the application. Reflux shows it on
         * its About page and in its store listings. No logo: it is optional, and Reflux does not use Trakt's marks.
         */
        val ATTRIBUTION = ProviderAttribution(
            name = "Trakt",
            notice = "Reflux uses Trakt but is not endorsed or certified by Trakt.",
            site = "trakt.tv",
        )

        /**
         * The Trakt provider for a build's Client ID, or null when the build has none: without it Reflux names works
         * from files and sources, and makes no lookups.
         */
        fun fromClientId(clientId: String?, http: HttpFetcher): TraktProvider? =
            clientId?.trim()?.takeIf { it.isNotEmpty() }?.let { TraktProvider(it, http) }

        /** RFC 3986 percent-encoding of a query component. */
        internal fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") append(c)
                else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
            }
        }

        /** Trakt's search reads its query as search syntax; its documentation asks for these characters escaped. */
        internal fun escapeQuery(title: String): String = buildString {
            var i = 0
            while (i < title.length) {
                val c = title[i]
                val pair = i + 1 < title.length && (c == '&' || c == '|') && title[i + 1] == c
                when {
                    pair -> { append('\\').append(c).append(c); i += 2; continue }
                    c in SEARCH_SPECIAL -> append('\\').append(c)
                    else -> append(c)
                }
                i++
            }
        }

        private const val SEARCH_SPECIAL = "+-!(){}[]^\"~*?:/\\"

        internal fun readableSlug(slug: String): String =
            slug.split('-').filter(String::isNotBlank).joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

        private fun type(kind: MetadataKind): String = if (kind == MetadataKind.MOVIE) "movie" else "show"
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
