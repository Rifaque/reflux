package dev.reflux.sources.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** Connection settings for an SMB share. Persisted as the source configuration. */
data class SmbShareConfig(
    val host: String,
    val share: String,
    /** Folder inside the share, `/`-separated, empty for the share root. */
    val path: String = "",
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val port: Int = 445,
) {
    fun encode(): String = listOfNotNull(
        "host=$host", "share=$share", "path=$path", "port=$port",
        username?.let { "user=$it" }, password?.let { "password=$it" }, domain?.let { "domain=$it" },
    ).joinToString("\n")

    companion object {
        fun decode(value: String): SmbShareConfig? {
            val pairs = value.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return SmbShareConfig(
                host = pairs["host"] ?: return null,
                share = pairs["share"] ?: return null,
                path = pairs["path"].orEmpty(),
                username = pairs["user"],
                password = pairs["password"],
                domain = pairs["domain"],
                port = pairs["port"]?.toIntOrNull() ?: 445,
            )
        }
    }
}

/**
 * An SMB (Windows/Samba/NAS) share as a read-only file source.
 *
 * Files are opened with read access only. Players reach them through a loopback [LocalStreamServer], because
 * neither Media3 nor typical libmpv builds can open `smb://` URLs.
 */
class SmbSource(
    private val config: SmbShareConfig,
    private val streams: LocalStreamServer,
    displayName: String = "${config.share} on ${config.host}",
) : FileEnumeratingSource, Closeable {
    private val client = SMBClient(
        SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).build(),
    )
    private var connection: Connection? = null
    private var session: Session? = null
    private val root = config.path.trim('/').replace('/', '\\')

    override val descriptor = SourceDescriptor(
        id = StableIds.sourceId(TYPE, "${config.host}:${config.port}/${config.share}/${config.path.trim('/')}|${config.username.orEmpty()}"),
        type = TYPE,
        displayName = displayName,
        locality = SourceLocality.LOCAL_NETWORK,
        capabilities = setOf(SourceCapability.ENUMERATE_FILES),
    )

    override suspend fun availability(): Availability = withContext(Dispatchers.IO) {
        try {
            share().use { if (it.folderExists(root)) Availability.AVAILABLE else Availability.UNAVAILABLE }
        } catch (_: Exception) {
            reset()
            Availability.UNAVAILABLE
        }
    }

    override fun files(): Flow<SourceFile> = flow {
        val share = try {
            share()
        } catch (e: Exception) {
            reset()
            throw SourceUnavailableException(descriptor.id, e)
        }
        share.use {
            val pending = ArrayDeque(listOf(""))
            while (pending.isNotEmpty()) {
                val relative = pending.removeLast()
                val entries = try {
                    share.list(join(root, relative))
                } catch (e: Exception) {
                    if (relative.isEmpty()) throw SourceUnavailableException(descriptor.id, e)
                    continue
                }
                val subdirectories = mutableListOf<String>()
                for (entry in entries.sortedBy { it.fileName }) {
                    val name = entry.fileName
                    if (name == "." || name == "..") continue
                    val path = if (relative.isEmpty()) name else "$relative/$name"
                    val directory = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    when {
                        directory -> if (ScanRules.shouldEnter(name)) subdirectories += path
                        ScanRules.roleOf(name) != null -> emit(SourceFile(path, entry.endOfFile, entry.lastWriteTime.toEpochMillis()))
                    }
                }
                subdirectories.asReversed().forEach(pending::addLast)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun playbackTarget(path: String): PlaybackTarget {
        require(".." !in path.split('/')) { "path escapes the share: $path" }
        val url = streams.register("${descriptor.id}/$path", path.substringAfterLast('/')) { openReader(path) }
        return PlaybackTarget(url)
    }

    override fun close() {
        reset()
        client.close()
    }

    private fun openReader(path: String): RangeReader {
        val share = share()
        val file: File = share.openFile(
            join(root, path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        return object : RangeReader, Closeable {
            override val length: Long = file.fileInformation.standardInformation.endOfFile
            override fun read(offset: Long, buffer: ByteArray, count: Int): Int = file.read(buffer, offset, 0, count)
            override fun close() {
                file.close()
                share.close()
            }
        }
    }

    @Synchronized
    private fun share(): DiskShare {
        val live = session?.takeIf { connection?.isConnected == true } ?: run {
            reset()
            val newConnection = client.connect(config.host, config.port)
            val auth = if (config.username == null) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(config.username, config.password.orEmpty().toCharArray(), config.domain)
            }
            newConnection.authenticate(auth).also {
                connection = newConnection
                session = it
            }
        }
        return live.connectShare(config.share) as? DiskShare ?: throw IllegalStateException("${config.share} is not a disk share")
    }

    @Synchronized
    private fun reset() {
        runCatching { connection?.close() }
        connection = null
        session = null
    }

    private fun join(base: String, relative: String): String =
        listOf(base, relative.replace('/', '\\')).filter { it.isNotEmpty() }.joinToString("\\")

    companion object {
        const val TYPE: String = "smb"
    }
}
