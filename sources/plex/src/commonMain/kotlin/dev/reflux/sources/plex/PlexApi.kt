package dev.reflux.sources.plex

import dev.reflux.core.RefluxBuild
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

/**
 * What Reflux needs to talk to one Plex Media Server as one account. Persisted as the source configuration.
 *
 * @param token the token the server accepts; null for a server that lets this network in without one.
 * @param account who is signed in, for showing it and for the source's identity; null without an account.
 */
data class PlexCredentials(
    val serverUrl: String,
    val machineId: String,
    val token: String?,
    val deviceId: String,
    val account: String? = null,
) {
    /** Everything but the token, so that an exception or a log line cannot carry it. */
    override fun toString(): String = "PlexCredentials(machineId=$machineId, deviceId=$deviceId)"

    /** Compact persisted form (`key=value` lines). The token is a secret; see SECURITY.md. */
    fun encode(): String = listOfNotNull(
        "server=$serverUrl", "machine=$machineId", token?.let { "token=$it" }, "device=$deviceId", account?.let { "account=$it" },
    ).joinToString("\n")

    companion object {
        fun decode(value: String): PlexCredentials? {
            val pairs = value.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return PlexCredentials(
                serverUrl = pairs["server"] ?: return null,
                machineId = pairs["machine"] ?: return null,
                token = pairs["token"],
                deviceId = pairs["device"] ?: return null,
                account = pairs["account"],
            )
        }
    }
}

open class PlexException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * A thin client of a Plex Media Server's HTTP API, or of plex.tv, asking for JSON. Every request names Reflux as
 * the client (`X-Plex-*`), as Plex asks of every client, and carries the token in a header, never in an address.
 */
internal class PlexApi(
    baseUrl: String,
    private val http: HttpFetcher,
    private val deviceId: String,
    private val deviceName: String,
    private val token: String? = null,
) {
    private val base = baseUrl.trimEnd('/')

    /** What identifies Reflux to Plex, and the token where there is one. */
    val headers: Map<String, String>
        get() = buildMap {
            put("Accept", "application/json")
            put("X-Plex-Product", "Reflux")
            put("X-Plex-Version", RefluxBuild.VERSION)
            put("X-Plex-Client-Identifier", deviceId)
            put("X-Plex-Device-Name", deviceName.replace(Regex("[\\r\\n]"), " "))
            put("X-Plex-Platform", "Reflux")
            token?.let { put("X-Plex-Token", it) }
        }

    fun url(path: String, params: Map<String, String> = emptyMap()): String =
        "$base/${path.trimStart('/')}" + if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject =
        parse(send(HttpRequest(url(path, params), headers = headers)))

    /** An answer that is a list at its top (plex.tv's newer API). */
    suspend fun getList(path: String, params: Map<String, String> = emptyMap()): List<JsonObject> {
        val result = send(HttpRequest(url(path, params), headers = headers))
        val parsed = try {
            Json.parseToJsonElement(result.text())
        } catch (e: Exception) {
            throw PlexException("unexpected response", result.status, e)
        }
        return (parsed as? JsonArray)?.mapNotNull { it as? JsonObject } ?: throw PlexException("unexpected response", result.status)
    }

    /** A request whose answer, if any, is not needed. */
    suspend fun call(path: String, params: Map<String, String> = emptyMap(), method: String = "GET") {
        send(HttpRequest(url(path, params), method, headers))
    }

    suspend fun postForm(path: String, form: Map<String, String>): JsonObject = parse(
        send(
            HttpRequest(
                url(path), "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"),
                form.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.encodeToByteArray(),
            ),
        ),
    )

    private suspend fun send(request: HttpRequest): HttpResult {
        val result = try {
            http.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PlexException("server unreachable", cause = e)
        }
        if (!result.ok) throw PlexException("HTTP ${result.status}", result.status)
        return result
    }

    private fun parse(result: HttpResult): JsonObject = try {
        Json.parseToJsonElement(result.text()) as? JsonObject ?: throw PlexException("unexpected response", result.status)
    } catch (e: PlexException) {
        throw e
    } catch (e: Exception) {
        throw PlexException("unexpected response", result.status, e)
    }

    companion object {
        const val PLEX_TV = "https://plex.tv"

        fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
                else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }
}

internal fun JsonObject.container(): JsonObject? = this["MediaContainer"] as? JsonObject
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") } ?: false
internal fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
