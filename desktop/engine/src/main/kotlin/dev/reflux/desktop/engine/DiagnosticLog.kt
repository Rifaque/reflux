package dev.reflux.desktop.engine

import dev.reflux.core.net.Redaction
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * Where problems are written down for developers and bug reports: what failed and why, in the technical
 * detail the interface deliberately leaves out. One small text file in Reflux's data directory, with the
 * previous one kept when it grows past [maxBytes].
 *
 * The same problem again and again (an engine that will not start, asked for on every press of Play) is written once,
 * then counted: a line says how many more times it happened when something else is written, and every power of ten
 * along the way, so a flood neither fills the file nor pushes the first, useful entry out of it.
 */
class DiagnosticLog(val file: Path, private val maxBytes: Long = 1_000_000) {
    /** What the last entry was, without its time, and how many times it came again since it was written. */
    private var last: String? = null
    private var repeats = 0

    @Synchronized
    fun record(problem: String, cause: Throwable? = null) {
        val body = redact(
            buildString {
                append(problem).append('\n')
                if (cause != null) append(StringWriter().also { cause.printStackTrace(PrintWriter(it)) })
            },
        )
        // A repeat is the same words and the same cause (its kind and message), not the same stack trace line for line.
        val identity = redact(problem + "\u0000" + cause?.javaClass?.name + "\u0000" + cause?.message)
        if (identity == last) {
            repeats++
            if (isPowerOfTen(repeats)) write("${Instant.now()}  (the previous problem has happened $repeats more times)\n")
            return
        }
        if (repeats > 0 && !isPowerOfTen(repeats)) write("${Instant.now()}  (the previous problem happened $repeats more times)\n")
        last = identity
        repeats = 0
        write("${Instant.now()}  $body")
    }

    private fun write(entry: String) {
        System.err.print(entry)
        // Diagnostics must never become a problem themselves.
        runCatching {
            Files.createDirectories(file.toAbsolutePath().parent)
            if (Files.exists(file) && Files.size(file) > maxBytes) {
                Files.move(file, file.resolveSibling("${file.fileName}.1"), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    private fun isPowerOfTen(n: Int): Boolean {
        var x = n
        while (x >= 10 && x % 10 == 0) x /= 10
        return x == 1 && n >= 10
    }

    companion object {
        /** Removes credentials from text that is about to be written down (see [Redaction]). */
        fun redact(text: String): String = Redaction.redact(text)
    }
}
