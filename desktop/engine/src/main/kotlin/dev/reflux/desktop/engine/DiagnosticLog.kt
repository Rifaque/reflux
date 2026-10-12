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
        val entry = redact(
            buildString {
                append(Instant.now()).append("  ").append(problem).append('\n')
                if (cause != null) append(StringWriter().also { cause.printStackTrace(PrintWriter(it)) })
            },
        )
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

    companion object {
        private val userInfo = Regex("""://[^/\s:@]+:[^/\s@]+@""")
        private val secretParameters = Regex("""(?i)\b(api_?key|access_?token|token|password|passwd|pwd|secret)=[^&\s"'<>]+""")
        private val secretHeaders = Regex("""(?i)\b(authorization|x-emby-token|x-mediabrowser-token|x-emby-authorization)(["']?\s*[:=]\s*)[^\r\n]+""")

        /**
         * Removes credentials from text that is about to be written down. Sources keep server addresses,
         * passwords, and tokens, and a log is something people attach to bug reports; errors that quote a
         * URL or a header must not carry those along.
         */
        fun redact(text: String): String = text
            .replace(userInfo, "://<credentials>@")
            .replace(secretParameters) { "${it.groupValues[1]}=<redacted>" }
            .replace(secretHeaders) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
    }
}
