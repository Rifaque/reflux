package dev.reflux.sources.jellyfin

import dev.reflux.core.RefluxBuild
import dev.reflux.core.net.EndpointSelector
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** What Reflux needs to talk to a Jellyfin server as one user. Persisted as the source configuration. */
data class JellyfinCredentials(
    val serverUrl: String,
    val serverId: String,
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    /** The name the user signs in with, for showing who is signed in. Never needed to talk to the server. */
    val userName: String? = null,
    /** Libraries a connection left unread before M3, by id: moved to the library's inclusion at start (docs/NAVIGATION.md). */
    val excludedLibraries: Set<String> = emptySet(),
    /**
     * The server's other addresses (B-003): the same server and account reached another way (at home through the LAN,
     * away through Tailscale). [serverUrl] is the most preferred; a connection stored before has none.
     */
    val alternates: List<String> = emptyList(),
) {
    /** Every address of the server, most preferred first ([EndpointSelector.preferenceOrder]). */
    val addresses: List<String> get() = EndpointSelector.preferenceOrder(listOf(serverUrl) + alternates)

    /** These credentials reaching the server at [addresses] instead, the most preferred as [serverUrl]. */
    fun at(addresses: List<String>): JellyfinCredentials {
        val ordered = EndpointSelector.preferenceOrder(addresses)
        return copy(serverUrl = ordered.first(), alternates = ordered.drop(1))
    }

    /** Everything but what proves who is signed in, so that an exception or a log line cannot carry it. */
    override fun toString(): String = "JellyfinCredentials(serverId=$serverId, userId=$userId, deviceId=$deviceId)"

    /** Compact persisted form (`key=value` lines). The access token is a secret; see SECURITY.md. */
    fun encode(): String = listOfNotNull(
        "server=$serverUrl", "serverId=$serverId", "user=$userId", "token=$accessToken", "device=$deviceId", userName?.let { "name=$it" },
        excludedLibraries.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")?.let { "exclude=$it" },
        alternates.takeIf { it.isNotEmpty() }?.joinToString(" ")?.let { "alt=$it" },
    ).joinToString("\n")

    companion object {
        fun decode(value: String): JellyfinCredentials? {
            val pairs = value.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return JellyfinCredentials(
                serverUrl = pairs["server"] ?: return null,
                serverId = pairs["serverId"] ?: return null,
                userId = pairs["user"] ?: return null,
                accessToken = pairs["token"] ?: return null,
                deviceId = pairs["device"] ?: return null,
                userName = pairs["name"],
                excludedLibraries = pairs["exclude"]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                alternates = pairs["alt"]?.split(' ')?.filter { it.isNotBlank() }.orEmpty(),
            )
        }
    }
}

class JellyfinException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * A thin Jellyfin REST client (10.9+ API surface), to one server at any of its addresses ([EndpointSelector]): a
 * request goes to the address that last answered and, only when that one does not answer at all, to the next.
 */
internal class JellyfinApi(
    private val endpoints: EndpointSelector,
    private val http: HttpFetcher,
    private val deviceId: String,
    private val deviceName: String,
    private val token: String? = null,
) {
    constructor(serverUrl: String, http: HttpFetcher, deviceId: String, deviceName: String, token: String? = null) :
        this(EndpointSelector(listOf(serverUrl), monotonicMs), http, deviceId, deviceName, token)

    /** The `Authorization` header Jellyfin expects from clients. */
    val authorization: String
        get() = buildString {
            append("MediaBrowser Client=\"Reflux\", Device=\"").append(deviceName.replace("\"", ""))
            append("\", DeviceId=\"").append(deviceId).append("\", Version=\"").append(CLIENT_VERSION).append('"')
            token?.let { append(", Token=\"").append(it).append('"') }
        }

    /** What is known of each address. */
    fun health() = endpoints.health()

    /** A URL at the address in use now: what a player or a download is handed. */
    fun url(path: String, params: Map<String, String> = emptyMap()): String = url(endpoints.current, path, params)

    private fun url(base: String, path: String, params: Map<String, String>): String =
        "${base.trimEnd('/')}/$path" + if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement =
        parse(send { base -> HttpRequest(url(base, path, params), headers = headers()) })

    /**
     * As [get], for an availability check: the more preferred addresses are tried first again from time to time
     * ([EndpointSelector.probeOrder]), so that one that answers again is used again.
     */
    suspend fun probe(path: String): JsonElement =
        parse(send(endpoints.probeOrder()) { base -> HttpRequest(url(base, path, emptyMap()), headers = headers()) })

    suspend fun post(path: String, body: JsonObject?, params: Map<String, String> = emptyMap()): JsonElement? {
        val result = send { base ->
            HttpRequest(
                url(base, path, params), "POST", headers() + ("Content-Type" to "application/json"),
                (body ?: JsonObject(emptyMap())).toString().encodeToByteArray(),
            )
        }
        return if (result.body.isEmpty()) null else parse(result)
    }

    /** A POST whose success is an empty answer; any body there is not what was expected. */
    suspend fun postWithoutAnswer(path: String) {
        val result = send { base -> HttpRequest(url(base, path, emptyMap()), "POST", headers()) }
        if (result.body.isNotEmpty()) throw JellyfinException("unexpected response", result.status)
    }

    suspend fun delete(path: String, params: Map<String, String> = emptyMap()) {
        send { base -> HttpRequest(url(base, path, params), "DELETE", headers()) }
    }

    private fun headers() = mapOf("Authorization" to authorization, "Accept" to "application/json")

    /**
     * Sends what [request] makes for an address, to each of [order] until one answers. Any answer, an HTTP error
     * included, is the server's and ends the search; only an address that cannot be reached is passed over.
     */
    private suspend fun send(order: List<String> = endpoints.requestOrder(), request: (String) -> HttpRequest): HttpResult {
        var unreachable: Exception? = null
        for (base in order) {
            val result = try {
                http.send(request(base))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                endpoints.failed(base)
                unreachable = e
                continue
            }
            endpoints.succeeded(base)
            if (!result.ok) throw JellyfinException("HTTP ${result.status}", result.status)
            return result
        }
        throw JellyfinException("server unreachable", cause = unreachable)
    }

    private fun parse(result: HttpResult): JsonElement = try {
        Json.parseToJsonElement(result.text())
    } catch (e: Exception) {
        throw JellyfinException("unexpected response", result.status, e)
    }

    companion object {
        const val CLIENT_VERSION = RefluxBuild.VERSION

        fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
                else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }
}

/** Milliseconds on a clock that only goes forward, for how long ago an address was tried. */
internal val monotonicMs: () -> Long = kotlin.time.TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
