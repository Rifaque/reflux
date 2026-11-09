package dev.reflux.core.diagnostics

import dev.reflux.core.BuildIdentity
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.DeviceCapabilities

/**
 * A support report: what a person attaches to a bug report when they choose to (docs/RELEASE.md, "Diagnostics
 * export"). It is made only when asked, never sent by Reflux, and says enough to understand a problem without
 * saying who has it or what they watch: no credential, token, cookie, server address, file path, or title.
 *
 * Everything that goes in passes through [SupportRedaction.scrub], including what the caller already thinks is
 * clean, so a careless line elsewhere cannot carry a secret into it.
 */
data class SupportReport(
    val build: BuildIdentity,
    /** When it was made, as an ISO 8601 UTC time (`2026-11-08T12:00:00Z`). */
    val generatedAt: String,
    /** Facts about the platform the shell knows: operating system and version, form factor, engine. Never a name or an ID. */
    val environment: List<Fact>,
    /** What this device was found able to play (decoders, display, audio), one fact each. */
    val capabilities: List<Fact>,
    val sources: List<SourceSummary>,
    /** How the most recent playback went (PlaybackReport's lines); empty when nothing has played since Reflux started. */
    val playback: List<Fact>,
    /** The end of the diagnostics log, oldest first. */
    val log: List<String>,
    /**
     * Words that must not appear, whatever their shape: the library's titles, source names, addresses, and user
     * names. Removed wherever they occur, before anything else is.
     */
    val privateValues: Collection<String> = emptyList(),
) {
    data class Fact(val name: String, val value: String)

    /** A source as the report describes it: its kind and state, never its name or where it is. */
    data class SourceSummary(
        val kind: String,
        val availability: String,
        val included: Boolean,
        val signInRequired: Boolean,
        val works: Int,
    )

    companion object {
        /** What [device] can play, as a support report lists it: decoders, display, sound, subtitles. */
        fun capabilities(device: DeviceCapabilities): List<Fact> = buildList {
            val video = device.videoDecoders.groupBy { it.codec }.entries.sortedBy { it.key.ordinal }.map { (codec, decoders) ->
                val best = device.decoderFor(codec) ?: decoders.first()
                val how = when (best.hardware) { true -> "hardware"; false -> "software"; null -> "unknown" }
                "${codec.name} $how to ${best.maxWidth}x${best.maxHeight} ${best.maxBitDepth}-bit"
            }
            add(Fact("Video decoders", video.joinToString(", ").ifEmpty { "none known" }))
            val display = device.display
            add(Fact("Display", listOfNotNull(
                if (display.maxWidth != null && display.maxHeight != null) "${display.maxWidth}x${display.maxHeight}" else "size unknown",
                // Spaced, so a long list (Dolby Vision and every HDR kind) never reads as one token to the redaction.
                display.dynamicRanges.sorted().joinToString(" / ") { it.name } + if (display.dynamicRangesKnown) "" else " (others unknown)",
                "tone-maps HDR".takeIf { display.canToneMap },
            ).joinToString(", ")))
            add(Fact("Audio decoders", device.audioDecoders.sorted().joinToString(", ") { it.name }.ifEmpty { "none known" }))
            add(Fact("Audio passthrough", device.audioPassthrough.sorted().joinToString(", ") { it.name }.ifEmpty { "none" } + if (device.audioPassthroughKnown) "" else " (others unknown)"))
            add(Fact("Output channels", "${device.maxOutputChannels}" + if (device.maxOutputChannelsKnown) "" else " or more"))
            add(Fact("Containers", device.containers.sorted().joinToString(", ") { it.name }))
            add(Fact("Subtitle formats", device.subtitleFormats.sorted().joinToString(", ") { it.name }))
            if (device.features.isNotEmpty()) add(Fact("Features", device.features.sorted().joinToString(", ") { it.name }))
        }
    }

    /** The report as the text a person attaches. Scrubbed line by line. */
    fun render(): String {
        val scrub = SupportRedaction(privateValues)
        val out = StringBuilder()
        fun line(text: String = "") { out.append(scrub(text)).append('\n') }
        fun section(title: String) { line(); line("## $title") }
        line("Reflux support report")
        line("Reflux ${build.label}")
        line("Made $generatedAt. Reflux sends nothing by itself: this file goes only where you send it.")
        line("Passwords, tokens, server addresses, file paths, and titles are removed from it.")
        section("Environment")
        environment.forEach { line("${it.name}: ${it.value}") }
        section("This device plays")
        if (capabilities.isEmpty()) line("Not known yet.") else capabilities.forEach { line("${it.name}: ${it.value}") }
        section("Sources")
        if (sources.isEmpty()) line("None added.")
        sources.forEachIndexed { index, source ->
            val state = listOfNotNull(
                source.availability.lowercase(),
                "left out of the library".takeUnless { source.included },
                "asks to sign in again".takeIf { source.signInRequired },
            ).joinToString(", ")
            line("${index + 1}. ${source.kind}: $state; ${source.works} ${if (source.works == 1) "work" else "works"}")
        }
        section("Last playback")
        if (playback.isEmpty()) line("Nothing has played since Reflux started.") else playback.forEach { line("${it.name}: ${it.value}") }
        section("Recent problems")
        if (log.isEmpty()) line("None recorded.") else log.forEach { line(it) }
        return out.toString()
    }
}

/**
 * Removes from support text whatever identifies a person, their servers, or their media, on top of [Redaction]'s
 * credentials: addresses, paths, host names, e-mail addresses, cookies, long tokens, and the [privateValues] the
 * caller names. What is left is what a developer needs: versions, formats, decoders, states, and errors.
 */
class SupportRedaction(privateValues: Collection<String> = emptyList()) {
    // Longest first, so a title containing another is removed whole; whole words only, so "Her" leaves "other" be.
    private val values = privateValues.map { it.trim() }.filter { it.length >= MIN_PRIVATE_LENGTH }.distinct().sortedByDescending { it.length }
        .map { Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(it) + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }

    operator fun invoke(text: String): String {
        // Shapes first (a path or an address goes whole, titles in it included), then the named words anywhere else.
        var result = Redaction.redact(text)
            .replace(cookies) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
            .replace(urls) { "${it.groupValues[1].lowercase()}://<server>/…" }
            .replace(windowsPaths) { "<path>" + extension(it.value) }
            .replace(uncPaths) { "<path>" + extension(it.value) }
            .replace(unixPaths) { "<path>" + extension(it.value) }
            .replace(emails, "<email>")
            .replace(ipv6, "<address>")
            .replace(ipv4, "<address>")
            .replace(hostPorts, "<server>")
            .replace(longTokens, "<redacted>")
        for (value in values) result = result.replace(value, PRIVATE)
        return result
    }

    private fun extension(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\').substringAfterLast('.', "").takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }?.let { ".$it" }.orEmpty()

    private companion object {
        const val PRIVATE = "<private>"
        const val MIN_PRIVATE_LENGTH = 3
        val cookies = Regex("""(?i)\b(cookie|set-cookie)(\s*[:=]\s*)[^\r\n]+""")
        val urls = Regex("""(?i)\b([a-z][a-z0-9+.-]{1,15})://[^\s"'<>)\]]+""")
        /**
         * The rest of a path: up to the last file of a kind Reflux reads on the line, spaces included (folders are
         * named after titles), or else to the next space.
         */
        const val REST = """(?:[^"'<>|*?\r\n]*\.(?i:mkv|mp4|m4v|avi|mov|webm|ts|m2ts|mts|mpg|mpeg|wmv|flv|ogv|srt|ass|ssa|vtt|sub|idx|sup|jpg|jpeg|png|webp|nfo|txt|log|iso|dll|db)\b|[^\s"'<>|*?]*)"""
        val windowsPaths = Regex("""\b[A-Za-z]:[\\/]$REST""")
        val uncPaths = Regex("""\\\\[^\s\\"'<>]+\\$REST""")
        val unixPaths = Regex("""(?<![\w.<])/(?:home|Users|storage|sdcard|mnt|media|data|run|tmp|var|srv|Volumes|private|root|opt)(?:/$REST)?""")
        val emails = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
        val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
        /** Full or compressed (`::`) IPv6, bracketed or not. A time of day (`12:00:00`) is neither. */
        val ipv6 = Regex("""(?i)\[[0-9a-f:.]*:[0-9a-f:.]*](?::\d{1,5})?|(?<![\w:])(?:(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})?::(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})?)(?![\w:])""")
        /** `name.local:8096`, `nas:445`: a host with a port, as addresses appear in errors. Not a source line (`Player.kt:120`). */
        val hostPorts = Regex("""(?i)\b[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)*(?<!\.kt)(?<!\.java)(?<!\.kts):\d{2,5}\b""")
        /** Anything a token could be: 32 or more hex or base64 characters in a row, with letters and digits both. */
        val longTokens = Regex("""(?=[A-Za-z0-9_\-+/]{32})(?=[A-Za-z_\-+/]*\d)(?=[0-9_\-+/]*[A-Za-z])\b[A-Za-z0-9_\-+/]{32,}={0,2}""")
    }
}
