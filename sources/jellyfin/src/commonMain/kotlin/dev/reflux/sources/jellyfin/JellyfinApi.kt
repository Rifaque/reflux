package dev.reflux.sources.jellyfin

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
) {
    /** Compact persisted form (`key=value` lines). The access token is a secret; see SECURITY.md. */
    fun encode(): String = listOf("server=$serverUrl", "serverId=$serverId", "user=$userId", "token=$accessToken", "device=$deviceId")
        .joinToString("\n")

    companion object {
        fun decode(value: String): JellyfinCredentials? {
            val pairs = value.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return JellyfinCredentials(
                serverUrl = pairs["server"] ?: return null,
                serverId = pairs["serverId"] ?: return null,
                userId = pairs["user"] ?: return null,
                accessToken = pairs["token"] ?: return null,
                deviceId = pairs["device"] ?: return null,
            )
        }
    }
}

class JellyfinException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause)

/** A thin Jellyfin REST client (10.9+ API surface). */
internal class JellyfinApi(
    private val serverUrl: String,
    private val http: HttpFetcher,
    private val deviceId: String,
    private val deviceName: String,
    private val token: String? = null,
) {
    private val base = serverUrl.trimEnd('/')

    /** The `Authorization` header Jellyfin expects from clients. */
    val authorization: String
        get() = buildString {
            append("MediaBrowser Client=\"Reflux\", Device=\"").append(deviceName.replace("\"", ""))
            append("\", DeviceId=\"").append(deviceId).append("\", Version=\"").append(CLIENT_VERSION).append('"')
            token?.let { append(", Token=\"").append(it).append('"') }
        }

    fun url(path: String, params: Map<String, String> = emptyMap()): String =
        "$base/$path" + if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement =
        parse(send(HttpRequest(url(path, params), headers = headers())))

    suspend fun post(path: String, body: JsonObject?, params: Map<String, String> = emptyMap()): JsonElement? {
        val result = send(
            HttpRequest(
                url(path, params), "POST", headers() + ("Content-Type" to "application/json"),
                (body ?: JsonObject(emptyMap())).toString().encodeToByteArray(),
            ),
        )
        return if (result.body.isEmpty()) null else parse(result)
    }

    suspend fun delete(path: String, params: Map<String, String> = emptyMap()) {
        send(HttpRequest(url(path, params), "DELETE", headers()))
    }

    private fun headers() = mapOf("Authorization" to authorization, "Accept" to "application/json")

    private suspend fun send(request: HttpRequest): HttpResult {
        val result = try {
            http.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw JellyfinException("server unreachable", cause = e)
        }
        if (!result.ok) throw JellyfinException("HTTP ${result.status}", result.status)
        return result
    }

    private fun parse(result: HttpResult): JsonElement = try {
        Json.parseToJsonElement(result.text())
    } catch (e: Exception) {
        throw JellyfinException("unexpected response", result.status, e)
    }

    companion object {
        const val CLIENT_VERSION = "0.1.0"

        fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
                else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
