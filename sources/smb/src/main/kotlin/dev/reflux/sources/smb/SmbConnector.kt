package dev.reflux.sources.smb

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Adding a folder shared by a computer or a NAS, written the way people write it: `\\nas\Movies`,
 * `nas/Movies`, or `smb://nas/Movies`, optionally with a folder inside the share. A server that lets
 * anyone read needs no name and password.
 */
class SmbConnector(private val streams: LocalStreamServer) : SourceConnector {
    override val type: String = SmbSource.TYPE
    override val kind: ConnectionKind = ConnectionKind.SHARE
    override val product: String? = null
    override val addressExample: String = "\\\\nas\\Movies"
    override val anonymous: Boolean = true

    override fun accepts(address: String): Boolean = parse(address) != null

    override suspend fun connect(request: ConnectionRequest): Connection {
        val place = parse(request.address) ?: return Connection.Failed(ConnectionProblem.INVALID_ADDRESS)
        val username = request.username?.takeIf { it.isNotBlank() }
        // "DOMAIN\name" is how Windows accounts are often written.
        val (domain, user) = username?.let { if ('\\' in it) it.substringBefore('\\') to it.substringAfter('\\') else null to it } ?: (null to null)
        val config = place.copy(username = user, password = request.password?.takeIf { user != null }, domain = domain)
        val source = SmbSource(config, streams)
        return try {
            withContext(Dispatchers.IO) { source.probe() }
            Connection.Established(source, config.encode())
        } catch (e: Exception) {
            source.close()
            Connection.Failed(problemOf(e, signedIn = user != null), e.javaClass.simpleName + ": " + e.message)
        }
    }

    override fun describe(config: String): ConnectionRequest? = SmbShareConfig.decode(config)?.let { share ->
        val folder = listOf(share.share, share.path).filter { it.isNotBlank() }.joinToString("\\") { it.replace('/', '\\') }
        val host = if (share.port == SMB_PORT) share.host else "${share.host}:${share.port}"
        ConnectionRequest("\\\\$host\\$folder", share.domain?.let { "$it\\${share.username}" } ?: share.username)
    }

    internal companion object {
        private const val SMB_PORT = 445

        /** Where an address leads: server, port, share, and folder. Null when it is not a share address. */
        fun parse(address: String): SmbShareConfig? {
            var rest = address.trim()
            if (rest.contains("://")) {
                if (!rest.startsWith("smb://", ignoreCase = true)) return null
                rest = rest.substring("smb://".length)
            }
            val parts = rest.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val hostPart = parts[0]
            if ('@' in hostPart || hostPart.any { it.isWhitespace() }) return null
            val port = if (':' in hostPart) hostPart.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null else SMB_PORT
            val host = hostPart.substringBeforeLast(':').takeIf { ':' in hostPart } ?: hostPart
            if (host.isBlank()) return null
            return SmbShareConfig(host = host, share = parts[1], path = parts.drop(2).joinToString("/"), port = port)
        }

        fun problemOf(e: Throwable, signedIn: Boolean): ConnectionProblem = when (e) {
            is FileNotFoundException -> ConnectionProblem.NOT_FOUND
            is SMBApiException -> when (e.status) {
                NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_PASSWORD_EXPIRED, NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED ->
                    if (signedIn) ConnectionProblem.AUTHENTICATION else ConnectionProblem.SIGN_IN_REQUIRED
                NtStatus.STATUS_ACCESS_DENIED -> if (signedIn) ConnectionProblem.PERMISSION_DENIED else ConnectionProblem.SIGN_IN_REQUIRED
                NtStatus.STATUS_BAD_NETWORK_NAME, NtStatus.STATUS_BAD_NETWORK_PATH, NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
                NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NOT_FOUND, NtStatus.STATUS_NOT_A_DIRECTORY -> ConnectionProblem.NOT_FOUND
                else -> ConnectionProblem.UNKNOWN
            }
            // A share that is not a folder share (a printer, a pipe).
            is IllegalStateException -> ConnectionProblem.NOT_FOUND
            else -> ConnectionProblem.UNREACHABLE
        }
    }
}
