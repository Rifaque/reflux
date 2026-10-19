package dev.reflux.desktop

import dev.reflux.core.RefluxBuild
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the desktop build recorded about itself, read back the way the About page reads it. */
class BuildIdentityTest {
    private fun git(vararg args: String): String? = runCatching {
        val process = ProcessBuilder("git", *args).directory(File("../..")).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) out else null
    }.getOrNull()

    @Test
    fun theBuildRecordsTheCommitItWasMadeFrom() {
        val identity = readBuildIdentity()
        assertEquals(RefluxBuild.VERSION, identity.version)
        // Without a Git checkout to ask (a source archive, a container that does not trust the mount), the version stands alone.
        val head = git("rev-parse", "--short=7", "HEAD")
        assertEquals(head, identity.commit)
        if (head != null) assertEquals(git("status", "--porcelain")?.isNotEmpty(), identity.modified)
    }
}
