package dev.reflux.metadata.tmdb

import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderCollection
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The Movie Database (TMDB) v3 adapter.
 *
 * Credentials come from the build (an app-level key), never from the user: metadata must work with no setup.
 * A v4 read-access token (a JWT) is sent as a bearer token; a v3 key as the `api_key` parameter.
 */
class TmdbProvider(
    private val credential: String,
    private val http: HttpFetcher,
    private val baseUrl: String = "https://api.themoviedb.org/3",
    private val imageBaseUrl: String = "https://image.tmdb.org/t/p",
) : MetadataProvider {
    override val id: String = ID

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        val (path, yearParam) = when (query.kind) {
            MetadataKind.MOVIE -> "search/movie" to "primary_release_year"
            MetadataKind.SHOW -> "search/tv" to "first_air_date_year"
        }
        val params = mutableMapOf("query" to query.title, "language" to query.language, "include_adult" to "false")
        val withYear = query.year?.let { get(path, params + (yearParam to it.toString())) }?.array("results").orEmpty()
        // A wrong year in a file name should not hide the right work: fall back to a search without it.
        val results = withYear.ifEmpty { get(path, params)?.array("results").orEmpty() }
        return results.mapNotNull { (it as? JsonObject)?.let { result -> candidate(query.kind, result) } }
    }

    override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? {
        val externalSource = when (source) {
            "imdb" -> "imdb_id"
            "tvdb" -> "tvdb_id"
            else -> return null
        }
        val response = get("find/${encode(id)}", mapOf("external_source" to externalSource)) ?: return null
        val key = if (kind == MetadataKind.MOVIE) "movie_results" else "tv_results"
        val first = response.array(key)?.firstOrNull() as? JsonObject ?: return null
        return first.int("id")?.let { ProviderRef(ID, kind, it.toString()) }
    }

    override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? {
        val imageLanguages = "${language.substringBefore('-')},null"
        val region = language.substringAfter('-', "US").uppercase()
        return when (ref.kind) {
            MetadataKind.MOVIE -> get(
                "movie/${ref.id}",
                mapOf(
                    "language" to language,
                    "append_to_response" to "credits,images,release_dates,external_ids",
                    "include_image_language" to imageLanguages,
                ),
            )?.let { movie(ref, it, region) }
            MetadataKind.SHOW -> get(
                "tv/${ref.id}",
                mapOf(
                    "language" to language,
                    "append_to_response" to "credits,images,content_ratings,external_ids",
                    "include_image_language" to imageLanguages,
                ),
            )?.let { show(ref, it, region) }
        }
    }

    override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? {
        val json = get("tv/${ref.id}/season/$seasonNumber", mapOf("language" to language)) ?: return null
        return SeasonMetadata(
            number = json.int("season_number") ?: seasonNumber,
            title = json.string("name"),
            overview = json.string("overview"),
            posterUrl = image(json.string("poster_path"), POSTER_SIZE),
            episodes = json.array("episodes").orEmpty().mapNotNull { element ->
                val episode = element as? JsonObject ?: return@mapNotNull null
                EpisodeMetadata(
                    seasonNumber = episode.int("season_number") ?: seasonNumber,
                    episodeNumber = episode.int("episode_number") ?: return@mapNotNull null,
                    title = episode.string("name"),
                    overview = episode.string("overview"),
                    airDate = date(episode.string("air_date")),
                    runtimeMinutes = episode.int("runtime"),
                    stillUrl = image(episode.string("still_path"), STILL_SIZE),
                )
            },
        )
    }

    private fun candidate(kind: MetadataKind, json: JsonObject): MetadataCandidate? {
        val id = json.int("id") ?: return null
        val title = json.string(if (kind == MetadataKind.MOVIE) "title" else "name") ?: return null
        val date = json.string(if (kind == MetadataKind.MOVIE) "release_date" else "first_air_date")
        return MetadataCandidate(
            ref = ProviderRef(ID, kind, id.toString()),
            title = title,
            originalTitle = json.string(if (kind == MetadataKind.MOVIE) "original_title" else "original_name"),
            year = date(date)?.year,
            overview = json.string("overview"),
            popularity = json.double("popularity") ?: 0.0,
            posterUrl = image(json.string("poster_path"), POSTER_SIZE),
        )
    }

    private fun movie(ref: ProviderRef, json: JsonObject, region: String): WorkMetadata {
        val crew = json.obj("credits")?.array("crew").orEmpty().mapNotNull { it as? JsonObject }
        val certification = json.obj("release_dates")?.array("results").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("iso_3166_1") == region }
            ?.array("release_dates").orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("certification") }
            .firstOrNull { it.isNotBlank() }
        return WorkMetadata(
            ref = ref,
            title = json.string("title") ?: "",
            originalTitle = json.string("original_title"),
            overview = json.string("overview"),
            tagline = json.string("tagline"),
            genres = genres(json),
            runtimeMinutes = json.int("runtime")?.takeIf { it > 0 },
            releaseDate = date(json.string("release_date")),
            rating = json.double("vote_average")?.takeIf { it > 0 },
            contentRating = certification,
            credits = crew.filter { it.string("job") == "Director" }.mapNotNull { person(it, CreditRole.DIRECTOR) } +
                cast(json),
            artwork = artwork(json),
            externalIds = externalIds(json),
            collection = json.obj("belongs_to_collection")?.let { collection ->
                val id = collection.int("id") ?: return@let null
                ProviderCollection(
                    id = "$ID:collection:$id",
                    name = collection.string("name") ?: return@let null,
                    posterUrl = image(collection.string("poster_path"), POSTER_SIZE),
                    backdropUrl = image(collection.string("backdrop_path"), BACKDROP_SIZE),
                )
            },
        )
    }

    private fun show(ref: ProviderRef, json: JsonObject, region: String): WorkMetadata {
        val creators = json.array("created_by").orEmpty().mapNotNull { (it as? JsonObject)?.let { p -> person(p, CreditRole.CREATOR) } }
        val rating = json.obj("content_ratings")?.array("results").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("iso_3166_1") == region }
            ?.string("rating")
        return WorkMetadata(
            ref = ref,
            title = json.string("name") ?: "",
            originalTitle = json.string("original_name"),
            overview = json.string("overview"),
            tagline = json.string("tagline"),
            genres = genres(json),
            runtimeMinutes = json.array("episode_run_time").orEmpty().firstNotNullOfOrNull { (it as? JsonPrimitive)?.intOrNull },
            releaseDate = date(json.string("first_air_date")),
            rating = json.double("vote_average")?.takeIf { it > 0 },
            contentRating = rating?.takeIf { it.isNotBlank() },
            credits = creators + cast(json),
            artwork = artwork(json),
            externalIds = externalIds(json),
        )
    }

    private fun genres(json: JsonObject): List<String> =
        json.array("genres").orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }

    private fun cast(json: JsonObject): List<Credit> =
        json.obj("credits")?.array("cast").orEmpty().mapNotNull { it as? JsonObject }
            .sortedBy { it.int("order") ?: Int.MAX_VALUE }
            .mapNotNull { person(it, CreditRole.ACTOR) }

    private fun person(json: JsonObject, role: CreditRole): Credit? = Credit(
        name = json.string("name") ?: return null,
        role = role,
        character = json.string("character")?.takeIf { role == CreditRole.ACTOR && it.isNotBlank() },
        profileUrl = image(json.string("profile_path"), PROFILE_SIZE),
    )

    private fun artwork(json: JsonObject): List<RemoteArtwork> {
        val images = json.obj("images") ?: return emptyList()
        fun list(key: String, kind: ArtworkKind, size: String) = images.array(key).orEmpty().mapNotNull { element ->
            val image = element as? JsonObject ?: return@mapNotNull null
            RemoteArtwork(
                kind = kind,
                url = image(image.string("file_path"), size) ?: return@mapNotNull null,
                language = image.string("iso_639_1"),
                score = image.double("vote_average") ?: 0.0,
            )
        }
        return list("posters", ArtworkKind.POSTER, POSTER_SIZE) +
            list("backdrops", ArtworkKind.BACKDROP, BACKDROP_SIZE) +
            list("logos", ArtworkKind.LOGO, LOGO_SIZE)
    }

    private fun externalIds(json: JsonObject): Map<String, String> {
        val ids = json.obj("external_ids")
        return buildMap {
            (ids?.string("imdb_id") ?: json.string("imdb_id"))?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
            ids?.int("tvdb_id")?.let { put("tvdb", it.toString()) }
            json.int("id")?.let { put(ID, it.toString()) }
        }
    }

    private fun image(path: String?, size: String): String? = path?.takeIf { it.isNotBlank() }?.let { "$imageBaseUrl/$size$it" }

    private fun date(value: String?): CalendarDate? = value?.let(CalendarDate::parse)

    /** GET a JSON object; null for 404. Throws [MetadataUnavailableException] when TMDB cannot be used now. */
    private suspend fun get(path: String, params: Map<String, String>): JsonObject? {
        val bearer = '.' in credential
        val query = (if (bearer) params else params + ("api_key" to credential))
            .entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val url = "$baseUrl/$path" + if (query.isEmpty()) "" else "?$query"
        val headers = buildMap {
            put("Accept", "application/json")
            if (bearer) put("Authorization", "Bearer $credential")
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            val result: HttpResult = try {
                http.get(url, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw MetadataUnavailableException("TMDB unreachable", e)
            }
            when {
                result.ok -> return Json.parseToJsonElement(result.text()) as? JsonObject
                result.status == 404 -> return null
                result.status == 429 && attempt < MAX_ATTEMPTS - 1 -> {
                    val wait = result.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                        ?.value?.toLongOrNull() ?: 1L
                    delay(wait.coerceIn(1, 10) * 1000)
                }
                else -> throw MetadataUnavailableException("TMDB returned HTTP ${result.status}")
            }
        }
        throw MetadataUnavailableException("TMDB rate limit")
    }

    companion object {
        const val ID: String = "tmdb"
        private const val MAX_ATTEMPTS = 3
        private const val POSTER_SIZE = "w780"
        private const val BACKDROP_SIZE = "w1280"
        private const val LOGO_SIZE = "w500"
        private const val STILL_SIZE = "w780"
        private const val PROFILE_SIZE = "w185"

        /** RFC 3986 percent-encoding of a query component. */
        internal fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") append(c)
                else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

