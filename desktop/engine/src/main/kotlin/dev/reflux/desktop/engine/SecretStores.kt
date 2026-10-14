package dev.reflux.desktop.engine

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import dev.reflux.core.model.SourceId
import dev.reflux.library.ConfigSeal
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A place the operating system keeps secrets for the signed-in user. Every call may throw. */
interface SecretStore {
    fun write(name: String, secret: String)

    /** Null when there is nothing under [name]. */
    fun read(name: String): String?

    fun delete(name: String)
}

/**
 * Source connections kept in the system's credential store. The library database holds only a reference
 * (`keychain:v1:<source id>`), so a copy of the database carries no token and no password.
 *
 * Entries are named after the data directory as well as the source, so two Reflux libraries on one account
 * (a test run beside the real one) never share an entry.
 */
class KeychainSeal(private val store: SecretStore, dataDirectory: Path) : ConfigSeal {
    private val scope: String = MessageDigest.getInstance("SHA-256")
        .digest(dataDirectory.toAbsolutePath().normalize().toString().toByteArray(StandardCharsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it) }

    internal fun name(id: SourceId) = "Reflux/$scope/${id.value}"

    override fun seal(id: SourceId, config: String): String {
        store.write(name(id), config)
        return PREFIX + id.value
    }

    override fun open(id: SourceId, stored: String): String {
        if (!sealed(stored)) return stored
        // An entry the store no longer has opens as nothing: the source shows as not connected until the
        // user signs in again. Nothing else about it is lost.
        return runCatching { store.read(name(id)) }.getOrNull().orEmpty()
    }

    override fun sealed(stored: String): Boolean = stored.startsWith(PREFIX)

    override fun forget(id: SourceId) = store.delete(name(id))

    private companion object {
        const val PREFIX = "keychain:v1:"
    }
}

/** The Windows Credential Manager, through its own API: generic credentials for the signed-in user. */
class WindowsCredentials : SecretStore {
    /** `CREDENTIALW`, as `wincred.h` lays it out. */
    @Structure.FieldOrder(
        "flags", "type", "targetName", "comment", "lastWrittenLow", "lastWrittenHigh", "credentialBlobSize",
        "credentialBlob", "persist", "attributeCount", "attributes", "targetAlias", "userName",
    )
    class Credential() : Structure() {
        @JvmField var flags = 0
        @JvmField var type = 0
        @JvmField var targetName: WString? = null
        @JvmField var comment: WString? = null
        @JvmField var lastWrittenLow = 0
        @JvmField var lastWrittenHigh = 0
        @JvmField var credentialBlobSize = 0
        @JvmField var credentialBlob: Pointer? = null
        @JvmField var persist = 0
        @JvmField var attributeCount = 0
        @JvmField var attributes: Pointer? = null
        @JvmField var targetAlias: WString? = null
        @JvmField var userName: WString? = null

        constructor(pointer: Pointer) : this() {
            useMemory(pointer)
            read()
        }
    }

    @Suppress("FunctionName")
    private interface Api : Library {
        fun CredWriteW(credential: Credential, flags: Int): Boolean
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer)
    }

    private val api: Api by lazy { Native.load("Advapi32", Api::class.java) }

    override fun write(name: String, secret: String) {
        val bytes = secret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BLOB) { "a connection of ${bytes.size} bytes is larger than Windows keeps" }
        val blob = Memory(bytes.size.toLong().coerceAtLeast(1)).apply { write(0, bytes, 0, bytes.size) }
        val credential = Credential().apply {
            type = CRED_TYPE_GENERIC
            targetName = WString(name)
            userName = WString("Reflux")
            credentialBlobSize = bytes.size
            credentialBlob = blob
            persist = CRED_PERSIST_LOCAL_MACHINE
        }
        try {
            check(api.CredWriteW(credential, 0)) { "CredWrite failed: ${Native.getLastError()}" }
        } finally {
            blob.clear()
        }
    }

    override fun read(name: String): String? {
        val result = PointerByReference()
        if (!api.CredReadW(WString(name), CRED_TYPE_GENERIC, 0, result)) {
            val error = Native.getLastError()
            if (error == ERROR_NOT_FOUND) return null
            throw IllegalStateException("CredRead failed: $error")
        }
        try {
            val credential = Credential(result.value)
            return String(credential.credentialBlob!!.getByteArray(0, credential.credentialBlobSize), StandardCharsets.UTF_8)
        } finally {
            api.CredFree(result.value)
        }
    }

    override fun delete(name: String) {
        if (!api.CredDeleteW(WString(name), CRED_TYPE_GENERIC, 0)) {
            val error = Native.getLastError()
            check(error == ERROR_NOT_FOUND) { "CredDelete failed: $error" }
        }
    }

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
        const val ERROR_NOT_FOUND = 1168

        /** CRED_MAX_CREDENTIAL_BLOB_SIZE. */
        const val MAX_BLOB = 5 * 512
    }
}

/** Runs a command with [input] on its standard input; the secret never appears on a command line. */
fun interface CommandRunner {
    data class Result(val exitCode: Int, val output: String, val error: String)

    fun run(command: List<String>, input: String?): Result
}

/**
 * The freedesktop Secret Service (GNOME Keyring, KWallet, KeePassXC) through `secret-tool`, which Linux
 * desktops ship with libsecret. Secrets go in on standard input and come out on standard output, never in
 * arguments that other users could read from the process list.
 */
class SecretServiceStore(private val runner: CommandRunner = ProcessRunner) : SecretStore {
    private fun attributes(name: String) = listOf("application", "reflux", "entry", name)

    override fun write(name: String, secret: String) {
        val result = runner.run(listOf("secret-tool", "store", "--label=Reflux ($name)") + attributes(name), secret)
        check(result.exitCode == 0) { "secret-tool store failed (${result.exitCode}): ${result.error.trim()}" }
    }

    override fun read(name: String): String? {
        val result = runner.run(listOf("secret-tool", "lookup") + attributes(name), null)
        return when {
            result.exitCode == 0 -> result.output
            result.error.isBlank() -> null // nothing stored under it
            else -> throw IllegalStateException("secret-tool lookup failed (${result.exitCode}): ${result.error.trim()}")
        }
    }

    override fun delete(name: String) {
        runner.run(listOf("secret-tool", "clear") + attributes(name), null)
    }

    /** Whether `secret-tool` is installed and a Secret Service answers it in this session. */
    fun available(): Boolean = runCatching {
        val probe = runner.run(listOf("secret-tool", "lookup") + attributes("reflux-probe"), null)
        probe.exitCode == 0 || (probe.exitCode == 1 && probe.error.isBlank())
    }.getOrDefault(false)
}

/** Runs commands as processes, with a deadline so that a store that hangs cannot hold Reflux up. */
object ProcessRunner : CommandRunner {
    override fun run(command: List<String>, input: String?): CommandRunner.Result {
        val process = ProcessBuilder(command).start()
        process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray(StandardCharsets.UTF_8)) } }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return CommandRunner.Result(-1, "", "timed out")
        }
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val error = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
        return CommandRunner.Result(process.exitValue(), output, error)
    }
}

/** The seal this desktop offers, and what it is, for the diagnostics. */
object DesktopSecrets {
    fun seal(dataDirectory: Path, os: String = System.getProperty("os.name"), report: (String) -> Unit = {}): ConfigSeal = when {
        os.startsWith("Windows") -> KeychainSeal(WindowsCredentials(), dataDirectory)
        os.startsWith("Linux") && SecretServiceStore().available() -> KeychainSeal(SecretServiceStore(), dataDirectory)
        else -> {
            report("No system credential store is available; source connections are kept in the library database as they are")
            ConfigSeal.None
        }
    }
}
