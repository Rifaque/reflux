package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceConnector
import kotlinx.coroutines.CancellationException

/**
 * Signing in to a Jellyfin server from an address, a name, and a password. The password is used for the
 * one request that signs in and goes no further; what is kept is the server's access token.
 */
class JellyfinConnector(
    private val http: HttpFetcher,
    private val deviceId: String,
    private val deviceName: String = "Reflux",
) : SourceConnector {
    override val type: String = JellyfinSource.TYPE
    override val kind: ConnectionKind = ConnectionKind.SERVER
    override val product: String = "Jellyfin"
    override val addressExample: String = "http://192.168.1.10:8096"
    override val anonymous: Boolean = false

    override fun accepts(address: String): Boolean = address.isNotBlank()

    override suspend fun connect(request: ConnectionRequest): Connection {
        val url = JellyfinSource.normalizeUrl(request.address)
        if (!address.matches(url)) return Connection.Failed(ConnectionProblem.INVALID_ADDRESS)
        val username = request.username?.takeIf { it.isNotBlank() } ?: return Connection.Failed(ConnectionProblem.SIGN_IN_REQUIRED)
        val (credentials, serverName) = try {
            JellyfinSource.signIn(url, username, request.password.orEmpty(), http, deviceId, deviceName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyfinException) {
            return Connection.Failed(problemOf(e), e.message)
        } catch (e: Exception) {
            return Connection.Failed(ConnectionProblem.UNKNOWN, e.message)
        }
        return Connection.Established(JellyfinSource(credentials, http, nameOf(serverName), deviceName), credentials.encode())
    }

    override fun withExcludedSections(config: String, excluded: Set<String>): String? =
        JellyfinCredentials.decode(config)?.copy(excludedLibraries = excluded)?.encode()

    override fun describe(config: String): ConnectionRequest? =
        JellyfinCredentials.decode(config)?.let { ConnectionRequest(it.serverUrl, it.userName) }

    /** Ends the session of [config]'s access token, and no other (see [JellyfinSource.signOut]). */
    override suspend fun signOut(config: String, keeping: String?): Boolean {
        val credentials = JellyfinCredentials.decode(config) ?: return false
        // A server may hand the same token out again to a sign-in from this device; ending it would end both.
        if (keeping?.let(JellyfinCredentials::decode)?.let { it.serverUrl == credentials.serverUrl && it.accessToken == credentials.accessToken } == true) return false
        JellyfinSource.signOut(credentials, http, deviceName)
        return true
    }

    internal companion object {
        private val address = Regex("""^https?://[^\s/?#@]+(/[^\s?#]*)?$""", RegexOption.IGNORE_CASE)

        /**
         * What the server calls itself, unless that is not a name: a server in a container is called by the
         * container's generated host name ("4f2331b42400") unless someone named it, and that says nothing.
         */
        fun nameOf(serverName: String?): String =
            serverName?.trim()?.takeUnless { it.isEmpty() || generatedHost.matches(it) } ?: "Jellyfin"

        private val generatedHost = Regex("""^[0-9a-f]{12}$""")

        /**
         * A wrong password is the server's 401. Anything else that is not Jellyfin's answer means the
         * address leads somewhere, but not to a Jellyfin server.
         */
        fun problemOf(e: JellyfinException): ConnectionProblem = when {
            e.status == 401 -> ConnectionProblem.AUTHENTICATION
            e.status == 403 -> ConnectionProblem.PERMISSION_DENIED
            e.status != null && e.status >= 500 -> ConnectionProblem.SERVER_ERROR
            e.status != null -> ConnectionProblem.NOT_FOUND
            e.cause != null -> ConnectionProblem.UNREACHABLE
            else -> ConnectionProblem.NOT_FOUND
        }
    }
}
