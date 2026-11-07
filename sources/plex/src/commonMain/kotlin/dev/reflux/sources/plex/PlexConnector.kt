package dev.reflux.sources.plex

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceConnector
import kotlinx.coroutines.CancellationException

/**
 * Connecting a Plex Media Server from its address (docs/NAVIGATION.md, section 6), in one of three ways:
 *
 * - a plex.tv account's name and password: Reflux signs in once at plex.tv and keeps only the token, never the
 *   password; for a server shared with the account rather than owned by it, the token plex.tv gives for that server;
 * - a Plex token in the password field, with the name left empty;
 * - nothing, for a server that lets this network in without signing in.
 *
 * Removing the source forgets the token on this device; plex.tv is not asked to end it (not in M3).
 */
class PlexConnector(
    private val http: HttpFetcher,
    private val deviceId: String,
    private val deviceName: String = "Reflux",
    /** Where plex.tv is; replaced only by tests. */
    private val plexTv: String = PlexApi.PLEX_TV,
) : SourceConnector {
    override val type: String = PlexSource.TYPE
    override val kind: ConnectionKind = ConnectionKind.SERVER
    override val product: String = "Plex"
    override val addressExample: String = "http://192.168.1.10:32400"
    override val anonymous: Boolean = true

    override fun accepts(address: String): Boolean = address.isNotBlank()

    override suspend fun connect(request: ConnectionRequest): Connection {
        val url = PlexSource.normalizeUrl(request.address)
        if (!address.matches(url)) return Connection.Failed(ConnectionProblem.INVALID_ADDRESS)
        return try {
            val machine = PlexApi(url, http, deviceId, deviceName).get("identity").container()?.string("machineIdentifier")
                ?: return Connection.Failed(ConnectionProblem.NOT_FOUND, "no machine identifier")
            val username = request.username?.takeIf { it.isNotBlank() }
            val password = request.password?.takeIf { it.isNotEmpty() }
            val (token, account) = when {
                username != null -> signIn(username, password.orEmpty(), machine)
                password != null -> password to null
                else -> null to null
            }
            val root = try {
                PlexApi(url, http, deviceId, deviceName, token).get("").container()
            } catch (e: PlexException) {
                return Connection.Failed(
                    when (e.status) {
                        401 -> if (token == null) ConnectionProblem.SIGN_IN_REQUIRED else ConnectionProblem.AUTHENTICATION
                        403 -> ConnectionProblem.PERMISSION_DENIED
                        else -> problemOf(e)
                    },
                    e.message,
                )
            }
            val credentials = PlexCredentials(url, machine, token, deviceId, account)
            Connection.Established(PlexSource(credentials, http, nameOf(root?.string("friendlyName")), deviceName), credentials.encode())
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlexException) {
            Connection.Failed(problemOf(e), e.message)
        } catch (e: Exception) {
            Connection.Failed(ConnectionProblem.UNKNOWN, e.message)
        }
    }

    /**
     * Signs in at plex.tv and returns the token for [machine] and who signed in. An owned server takes the account's
     * own token; a server shared with the account takes the one plex.tv lists for it.
     */
    private suspend fun signIn(username: String, password: String, machine: String): Pair<String, String> {
        val tv = PlexApi(plexTv, http, deviceId, deviceName)
        val user = try {
            tv.postForm("api/v2/users/signin", mapOf("login" to username, "password" to password, "rememberMe" to "true"))
        } catch (e: PlexException) {
            throw PlexSignInException(e)
        }
        val token = user.string("authToken") ?: throw PlexException("plex.tv answered without a token")
        val account = user.string("username") ?: user.string("email") ?: username
        val shared = runCatching {
            PlexApi(plexTv, http, deviceId, deviceName, token).getList("api/v2/resources", mapOf("includeHttps" to "1"))
                .firstOrNull { it.string("clientIdentifier") == machine && it.bool("owned").not() }?.string("accessToken")
        }.getOrNull()
        return (shared ?: token) to account
    }

    override fun describe(config: String): ConnectionRequest? =
        PlexCredentials.decode(config)?.let { ConnectionRequest(it.serverUrl, it.account) }

    private fun problemOf(e: PlexException): ConnectionProblem = when {
        e is PlexSignInException -> if (e.status == 401 || e.status == 400 || e.status == 422) ConnectionProblem.AUTHENTICATION else ConnectionProblem.UNREACHABLE
        e.status == 401 -> ConnectionProblem.AUTHENTICATION
        e.status == 403 -> ConnectionProblem.PERMISSION_DENIED
        e.status != null && e.status >= 500 -> ConnectionProblem.SERVER_ERROR
        e.status != null -> ConnectionProblem.NOT_FOUND
        e.cause != null -> ConnectionProblem.UNREACHABLE
        else -> ConnectionProblem.NOT_FOUND
    }

    /** plex.tv refused, or was not reached: a problem with the account, not with the server. */
    private class PlexSignInException(cause: PlexException) : PlexException("plex.tv sign-in: ${cause.message}", cause.status, cause)

    internal companion object {
        private val address = Regex("""^https?://[^\s/?#@]+(/[^\s?#]*)?$""", RegexOption.IGNORE_CASE)

        /** What the server calls itself, or "Plex". */
        fun nameOf(serverName: String?): String = serverName?.trim()?.takeIf { it.isNotEmpty() } ?: "Plex"
    }
}
