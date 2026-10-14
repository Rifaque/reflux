package dev.reflux.desktop.engine

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.library.ConfigSeal
import dev.reflux.sources.jellyfin.JellyfinCredentials
import dev.reflux.sources.jellyfin.JellyfinSource
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A credential store in memory, which can be told to refuse. */
private class MemoryStore : SecretStore {
    val entries = HashMap<String, String>()
    var refuse = false
    override fun write(name: String, secret: String) {
        check(!refuse) { "refused" }
        entries[name] = secret
    }
    override fun read(name: String) = entries[name]
    override fun delete(name: String) {
        entries.remove(name)
    }
}

/** Where desktop source connections are kept: the system's credential store, with only a reference in the database. */
class CredentialStorageTest {
    private val data: Path = Files.createTempDirectory("reflux-credentials").also { it.toFile().deleteOnExit() }
    private val credentials = JellyfinCredentials("http://jf.local:8096", "srv1", "u1", "secret-token", "dev1", "alex")

    /** As an earlier version left it: the connection written into the library database as it is. */
    private fun plaintextLibrary(): Path {
        DesktopEnvironment(data, sealFor = { _, _ -> ConfigSeal.None }).apply {
            library.addSource(JellyfinSource(credentials, HttpFetcher { HttpResult(503, ByteArray(0)) }, "RS-X1"), credentials.encode())
        }
        return data
    }

    private fun stored(environment: DesktopEnvironment) = environment.library.sources().single { it.type == JellyfinSource.TYPE }

    @Test
    fun aConnectionStoredInTheOpenIsMovedToTheCredentialStoreAtStart() {
        plaintextLibrary()
        val store = MemoryStore()
        val environment = DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        val record = stored(environment)
        assertTrue(record.config.startsWith("keychain:v1:"), "the database now holds a reference")
        assertFalse("secret-token" in record.config)
        assertEquals(credentials.encode(), store.entries.values.single(), "and the store holds the connection, unchanged")
        // The source still works: it is recreated from the store, and still says where it is and who is signed in.
        assertNotNull(environment.source(record.id), "the adapter is recreated through the seal")
        assertEquals("alex", environment.connections.describe(record)?.request?.username)
        // A second start finds nothing left to move.
        DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        assertEquals(1, store.entries.size)
    }

    @Test
    fun aStoreThatRefusesLeavesTheWorkingConnectionAsItWas() {
        plaintextLibrary()
        val store = MemoryStore().apply { refuse = true }
        val environment = DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        assertEquals(credentials.encode(), stored(environment).config, "never thrown away")
        assertNotNull(environment.source(stored(environment).id))
        assertTrue(Files.readString(data.resolve("reflux.log")).contains("could not be moved to secure storage"))
        assertFalse(Files.readString(data.resolve("reflux.log")).contains("secret-token"), "and the log does not repeat it")
    }

    @Test
    fun removingASourceRemovesItsEntry() {
        plaintextLibrary()
        val store = MemoryStore()
        val environment = DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        environment.removeSource(stored(environment).id)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun anEntryTheStoreLostOpensAsNothingAndTheSourceWaitsForASignIn() {
        plaintextLibrary()
        val store = MemoryStore()
        DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        store.entries.clear()
        val environment = DesktopEnvironment(data, sealFor = { directory, _ -> KeychainSeal(store, directory) })
        assertNull(environment.source(stored(environment).id), "no adapter without its connection")
        assertEquals(1, environment.library.sources().count { it.type == JellyfinSource.TYPE }, "but the source and its library stay")
    }

    @Test
    fun twoLibrariesOnOneAccountNeverShareAnEntry() {
        val store = MemoryStore()
        val id = dev.reflux.core.model.SourceId("same")
        KeychainSeal(store, data.resolve("one")).seal(id, "a")
        KeychainSeal(store, data.resolve("two")).seal(id, "b")
        assertEquals(2, store.entries.size)
    }

    @Test
    fun theSecretServiceGetsSecretsOnItsInputNeverInItsArguments() {
        val calls = mutableListOf<Pair<List<String>, String?>>()
        val runner = CommandRunner { command, input ->
            calls += command to input
            when (command[1]) {
                "lookup" -> if (calls.any { it.first[1] == "store" }) CommandRunner.Result(0, "the-connection", "") else CommandRunner.Result(1, "", "")
                else -> CommandRunner.Result(0, "", "")
            }
        }
        val store = SecretServiceStore(runner)
        assertTrue(store.available())
        assertNull(store.read("entry"))
        store.write("entry", "the-connection")
        assertTrue(calls.none { (command, _) -> command.any { "the-connection" in it } })
        assertEquals("the-connection", calls.single { it.first[1] == "store" }.second)
        assertEquals("the-connection", store.read("entry"))
        // No keyring running in the session: not available, and the desktop says so instead of pretending.
        assertFalse(SecretServiceStore { _, _ -> CommandRunner.Result(1, "", "Cannot autolaunch D-Bus without X11") }.available())
        assertFalse(SecretServiceStore { _, _ -> throw java.io.IOException("secret-tool: not found") }.available())
    }

    @Test
    fun theWindowsCredentialManagerKeepsAndLetsGo() {
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows"))
        val store = WindowsCredentials()
        val name = "Reflux/test/${UUID.randomUUID()}"
        try {
            assertNull(store.read(name))
            store.write(name, "server=x\ntoken=t0k ü")
            assertEquals("server=x\ntoken=t0k ü", store.read(name))
            store.write(name, "token=changed")
            assertEquals("token=changed", store.read(name))
        } finally {
            store.delete(name)
        }
        assertNull(store.read(name))
        store.delete(name) // deleting what is not there is fine
    }
}
