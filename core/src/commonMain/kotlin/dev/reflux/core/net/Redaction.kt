package dev.reflux.core.net

/**
 * Removes credentials from text that is about to be written down or shown. Sources keep server addresses,
 * passwords, and tokens, and a log is something people attach to bug reports; errors that quote a URL or a
 * header must not carry those along. Every shell passes what it records through here.
 */
object Redaction {
    private val userInfo = Regex("""://[^/\s:@]+:[^/\s@]+@""")
    private val secretParameters = Regex("""(?i)\b(api_?key|access_?token|token|password|passwd|pwd|secret)=[^&\s"'<>]+""")
    private val secretHeaders = Regex("""(?i)\b(authorization|x-emby-token|x-mediabrowser-token|x-emby-authorization|x-plex-token)(["']?\s*[:=]\s*)[^\r\n]+""")

    /** A token as an authorization value quotes it (`Token="..."`), with or without the header's name. */
    private val quotedSecrets = Regex("""(?i)\b(token|api_key|password|pw)(\s*=\s*)"[^"]*"""")

    /** The per-file tokens of the loopback stream server, which let a local process read that file. */
    private val streamTokens = Regex("""(://(?:127\.0\.0\.1|localhost|\[::1]):\d+/)[0-9a-fA-F]{32}/""")

    fun redact(text: String): String = text
        .replace(quotedSecrets) { "${it.groupValues[1]}${it.groupValues[2]}\"<redacted>\"" }
        .replace(streamTokens) { "${it.groupValues[1]}<stream>/" }
        .replace(userInfo, "://<credentials>@")
        .replace(secretParameters) { "${it.groupValues[1]}=<redacted>" }
        .replace(secretHeaders) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
}
