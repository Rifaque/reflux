package dev.reflux.desktop.engine

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The diagnostics log writes a repeated problem once and counts it, instead of filling itself with it (B-031). */
class DiagnosticLogTest {
    private val file = Files.createTempDirectory("reflux-log").resolve("reflux.log")
    private val log = DiagnosticLog(file)
    private fun lines() = Files.readAllLines(file).map { it.substringAfter("  ") }

    @Test
    fun theSameProblemIsWrittenOnceAndCounted() {
        repeat(25) { log.record("libmpv was found but could not be initialized: libmpv refused to start") }
        log.record("Something else")
        assertEquals(
            listOf(
                "libmpv was found but could not be initialized: libmpv refused to start",
                "(the previous problem has happened 10 more times)",
                "(the previous problem happened 24 more times)",
                "Something else",
            ),
            lines(),
        )
    }

    @Test
    fun differentProblemsAndDifferentCausesAreAllWritten() {
        log.record("A")
        log.record("B")
        log.record("A")
        log.record("A", IllegalStateException("one"))
        log.record("A", IllegalStateException("two"))
        assertEquals(listOf("A", "B", "A", "A", "A"), lines().filter { it.length == 1 }, "each differs from the one before it")
        assertEquals(2, lines().count { it.startsWith("java.lang.IllegalStateException") })
    }

    @Test
    fun aRepeatIsStillRedacted() {
        repeat(3) { log.record("Failed at http://alex:secret@home:8096/x?api_key=abc123") }
        val text = Files.readString(file)
        assertFalse("secret" in text || "abc123" in text, text)
        assertTrue(lines().size == 1, "written once: ${lines()}")
    }
}
