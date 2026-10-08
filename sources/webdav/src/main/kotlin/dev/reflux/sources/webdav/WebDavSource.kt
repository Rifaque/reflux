package dev.reflux.sources.webdav

import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/** Connection settings for a WebDAV share. Persisted as the source configuration. */
data class WebDavConfig(val url: String, val username: String? = null, val password: String? = null) {
    fun encode(): String = listOfNotNull("url=$url", username?.let { "user=$it" }, password?.let { "password=$it" }).joinToString("\n")

    companion object {
        fun decode(value: String): WebDavConfig? {
            val pairs = value.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return WebDavConfig(pairs["url"] ?: return null, pairs["user"], pairs["password"])
        }
    }
}

/**
 * A WebDAV share (Nextcloud, NAS devices, Apache/nginx) as a read-only file source.
 *
 * Only `PROPFIND` and `GET` are ever sent: Reflux never writes to the share. Paths are share-relative and
 * decoded; the player streams files over HTTP(S) with the share's credentials.
 */
class WebDavSource(
    private val config: WebDavConfig,
    private val http: HttpFetcher,
    displayName: String = URI(config.url).host ?: "WebDAV",
) : FileEnumeratingSource {
    private val root: URI = URI(config.url.trimEnd('/') + "/")

    override val descriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, root.toString() + (config.username ?: "")),
        type = TYPE,
        displayName = displayName,
        locality = localityOf(root.host.orEmpty()),
        capabilities = setOf(SourceCapability.ENUMERATE_FILES),
    )

    private val authHeaders: Map<String, String> = config.username?.let { user ->
        val token = Base64.getEncoder().encodeToString("$user:${config.password.orEmpty()}".toByteArray(StandardCharsets.UTF_8))
        mapOf("Authorization" to "Basic $token")
    } ?: emptyMap()

    override suspend fun availability(): Availability = try {
        if (propfind(root, depth = 0).isNotEmpty()) Availability.AVAILABLE else Availability.UNAVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Availability.UNAVAILABLE
    }

    override fun files(): Flow<SourceFile> = flow {
        val pending = ArrayDeque(listOf(root))
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            if (!visited.add(directory.path)) continue
            val entries = try {
                propfind(directory, depth = 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (directory == root) throw SourceUnavailableException(descriptor.id, e)
                continue // unreadable folder: skip it, keep scanning
            }
            val subdirectories = mutableListOf<URI>()
            for (entry in entries.sortedBy { it.href.path }) {
                if (entry.href.path.trimEnd('/') == directory.path.trimEnd('/')) continue // the folder itself
                val relative = relativePath(entry.href) ?: continue
                val name = relative.trimEnd('/').substringAfterLast('/')
                if (entry.collection) {
                    if (ScanRules.shouldEnter(name)) subdirectories += entry.href
                } else if (ScanRules.roleOf(name) != null) {
                    emit(SourceFile(relative, entry.size, entry.modifiedAtEpochMs))
                }
            }
            subdirectories.asReversed().forEach(pending::addLast)
        }
    }

    override suspend fun playbackTarget(path: String): PlaybackTarget {
        val encoded = path.split('/').joinToString("/") { segment -> URI(null, null, segment, null).rawPath }
        return PlaybackTarget(root.resolve(encoded).toASCIIString(), authHeaders)
    }

    private data class Entry(val href: URI, val collection: Boolean, val size: Long, val modifiedAtEpochMs: Long)

    private suspend fun propfind(uri: URI, depth: Int): List<Entry> {
        val result = http.send(
            HttpRequest(
                url = uri.toString(),
                method = "PROPFIND",
                headers = authHeaders + mapOf("Depth" to depth.toString(), "Content-Type" to "application/xml; charset=utf-8"),
                body = PROPFIND_BODY.toByteArray(StandardCharsets.UTF_8),
            ),
        )
        if (result.status != 207) throw IllegalStateException("PROPFIND ${uri.path}: HTTP ${result.status}")
        return parseMultistatus(result.body, uri)
    }

    private fun parseMultistatus(xml: ByteArray, base: URI): List<Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) // no XXE
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val responses = document.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as Element
            val href = response.text("href") ?: return@mapNotNull null
            // Only properties reported with a 200 status are real.
            val okProps = response.children("propstat").filter { it.text("status")?.contains(" 200") != false }
                .flatMap { it.children("prop") }
            Entry(
                href = base.resolve(href.trim()),
                collection = okProps.any { prop -> prop.children("resourcetype").any { it.children("collection").isNotEmpty() } },
                size = okProps.firstNotNullOfOrNull { it.text("getcontentlength")?.trim()?.toLongOrNull() } ?: 0,
                modifiedAtEpochMs = okProps.firstNotNullOfOrNull { it.text("getlastmodified")?.let(::parseHttpDate) } ?: 0,
            )
        }
    }

    /** The share-relative, decoded path of [href], or null when it lies outside the share. */
    private fun relativePath(href: URI): String? {
        val rootPath = root.rawPath
        val path = href.rawPath ?: return null
        if (!path.startsWith(rootPath)) return null
        return path.removePrefix(rootPath).split('/').joinToString("/") { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8) }
            .takeIf { it.isNotEmpty() }
    }

    companion object {
        const val TYPE: String = "webdav"
        private const val DAV = "DAV:"
        private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
            |<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>""".trimMargin()

        internal fun parseHttpDate(value: String): Long? =
            runCatching { ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()

        fun localityOf(host: String): SourceLocality {
            val octets = host.split('.').mapNotNull { it.toIntOrNull() }
            val privateIp = octets.size == 4 && (
                octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)
                )
            val localName = host == "localhost" || '.' !in host || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")
            return if (privateIp || localName) SourceLocality.LOCAL_NETWORK else SourceLocality.REMOTE
        }

        private fun Element.children(localName: String): List<Element> {
            val nodes = childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.localName == localName && it.namespaceURI == DAV }
        }

        private fun Element.text(localName: String): String? = children(localName).firstOrNull()?.textContent
    }
}
