package dev.reflux.core.net

/**
 * Removes credentials from text that is about to be written down or shown. Sources keep server addresses,
 * passwords, and tokens, and a log is something people attach to bug reports; errors that quote a URL or a
 * header must not carry those along. Every shell passes what it records through here.
 */
object Redaction {
    private val userInfo = Regex("""://[^/\s:@]+:[^/\s@]+@""")
    private val secretParameters = Regex("""(?i)\b(api_?key|access_?token|token|password|passwd|pwd|secret)=[^&\s"'<>]+""")
    private val secretHeaders = Regex("""(?i)\b(authorization|x-emby-token|x-mediabrowser-token|x-emby-authorization)(["']?\s*[:=]\s*)[^\r\n]+""")

    fun redact(text: String): String = text
        .replace(userInfo, "://<credentials>@")
        .replace(secretParameters) { "${it.groupValues[1]}=<redacted>" }
        .replace(secretHeaders) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
}
