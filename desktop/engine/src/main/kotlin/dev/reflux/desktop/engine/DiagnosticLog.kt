package dev.reflux.desktop.engine

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
 */
class DiagnosticLog(val file: Path, private val maxBytes: Long = 1_000_000) {
    @Synchronized
    fun record(problem: String, cause: Throwable? = null) {
        val entry = buildString {
            append(Instant.now()).append("  ").append(problem).append('\n')
            if (cause != null) append(StringWriter().also { cause.printStackTrace(PrintWriter(it)) })
        }
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
}
