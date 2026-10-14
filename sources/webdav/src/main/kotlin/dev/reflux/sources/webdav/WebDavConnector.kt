package dev.reflux.sources.webdav

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceConnector
import kotlinx.coroutines.CancellationException
import java.net.URI

/**
 * Adding a share that is reached by a web address (Nextcloud, a NAS, a web server). The share is asked one
 * question about its root, so that a wrong address, a wrong password, and a folder that may not be read are
 * told apart before anything is added.
 */
class WebDavConnector(private val http: HttpFetcher) : SourceConnector {
    override val type: String = WebDavSource.TYPE
    override val kind: ConnectionKind = ConnectionKind.SHARE
    override val product: String? = null
    override val addressExample: String = "https://nas.local/media"
    override val anonymous: Boolean = true

    /** A web address is this connector's; anything else is some other kind of share. */
    override fun accepts(address: String): Boolean = schemes.any { address.trim().startsWith("$it://", ignoreCase = true) }

    override suspend fun connect(request: ConnectionRequest): Connection {
        val url = normalize(request.address) ?: return Connection.Failed(ConnectionProblem.INVALID_ADDRESS)
        val username = request.username?.takeIf { it.isNotBlank() }
        val config = WebDavConfig(url, username, request.password?.takeIf { username != null && it.isNotEmpty() })
        val source = WebDavSource(config, http)
        val status = try {
            source.probe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Connection.Failed(ConnectionProblem.UNREACHABLE, e.message)
        }
        return when (val problem = problemOf(status, signedIn = username != null)) {
            null -> Connection.Established(source, config.encode())
            else -> Connection.Failed(problem, "HTTP $status")
        }
    }

    override fun describe(config: String): ConnectionRequest? = WebDavConfig.decode(config)?.let { ConnectionRequest(it.url, it.username) }

    internal companion object {
        private val schemes = listOf("http", "https", "dav", "davs", "webdav", "webdavs")

        /** The address as `http(s)://host/path`, or null when it is not one. `dav:` and `davs:` are the same thing by another name. */
        fun normalize(address: String): String? {
            val trimmed = address.trim()
            val scheme = trimmed.substringBefore("://", "").lowercase()
            val web = when (scheme) {
                "http", "dav", "webdav" -> "http"
                "https", "davs", "webdavs" -> "https"
                else -> return null
            }
            // A folder name with spaces is typed as it reads.
            val url = web + trimmed.substring(scheme.length).replace(" ", "%20")
            // Credentials belong in their own fields, not in an address that is shown and logged.
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
            return url.trimEnd('/')
        }

        fun problemOf(status: Int, signedIn: Boolean): ConnectionProblem? = when (status) {
            207 -> null
            401 -> if (signedIn) ConnectionProblem.AUTHENTICATION else ConnectionProblem.SIGN_IN_REQUIRED
            403 -> ConnectionProblem.PERMISSION_DENIED
            in 500..599 -> ConnectionProblem.SERVER_ERROR
            // It answered, and not as a share does: a web page, a wrong path, a server without WebDAV.
            else -> ConnectionProblem.NOT_FOUND
        }
    }
}
