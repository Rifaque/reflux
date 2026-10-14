package dev.reflux.sources.smb

import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SmbConnectorTest {
    @Test
    fun aShareIsWrittenTheWayPeopleWriteIt() {
        val expected = SmbShareConfig("nas", "Movies", "Films/HD")
        assertEquals(expected, SmbConnector.parse("\\\\nas\\Movies\\Films\\HD"))
        assertEquals(expected, SmbConnector.parse("smb://nas/Movies/Films/HD/"))
        assertEquals(expected, SmbConnector.parse("nas/Movies/Films/HD"))
        assertEquals(SmbShareConfig("192.168.1.4", "media", "", port = 1445), SmbConnector.parse("//192.168.1.4:1445/media"))
        assertNull(SmbConnector.parse("nas"), "a server without a shared folder is not enough")
        assertNull(SmbConnector.parse("https://nas/Movies"), "a web address is another kind of share")
        assertNull(SmbConnector.parse("smb://alex@nas/Movies"), "credentials have their own fields")
    }

    @Test
    fun aStoredShareIsDescribedAsItWouldBeTyped() {
        LocalStreamServer().use { streams ->
            val connector = SmbConnector(streams)
            val config = SmbShareConfig("nas", "Movies", "HD", "alex", "secret", "HOME").encode()
            assertEquals(ConnectionRequest("\\\\nas\\Movies\\HD", "HOME\\alex"), connector.describe(config))
        }
    }

    @Test
    fun whatWentWrongIsToldApart() {
        assertEquals(ConnectionProblem.NOT_FOUND, SmbConnector.problemOf(FileNotFoundException("HD"), signedIn = true))
        assertEquals(ConnectionProblem.UNREACHABLE, SmbConnector.problemOf(java.net.ConnectException("refused"), signedIn = false))
        assertEquals(ConnectionProblem.NOT_FOUND, SmbConnector.problemOf(IllegalStateException("not a disk share"), signedIn = false))
    }

    @Test
    fun aServerThatDoesNotAnswerIsUnreachable() = runBlocking {
        // A port that is closed: nothing answers there.
        val port = ServerSocket(0).use { it.localPort }
        LocalStreamServer().use { streams ->
            val failed = assertIs<Connection.Failed>(SmbConnector(streams).connect(ConnectionRequest("\\\\127.0.0.1:$port\\Movies", null, null)))
            assertEquals(ConnectionProblem.UNREACHABLE, failed.problem)
        }
    }
}
