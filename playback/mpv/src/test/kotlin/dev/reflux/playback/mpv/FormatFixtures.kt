package dev.reflux.playback.mpv

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Formats the real library does not have, made small and deterministic by FFmpeg to answer one question each
 * (docs/PLAYBACK.md, section 16). The Android build makes the same files from the same arguments
 * (`android/app/build.gradle.kts`, `GenerateTestMedia`). Never media of anyone's: test pictures and tones.
 */
object FormatFixtures {
    class Fixture(val name: String, val question: String, val arguments: List<String>)

    private fun picture(size: String, rate: Int, seconds: Int) = listOf("-f", "lavfi", "-i", "testsrc2=size=$size:rate=$rate", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "$seconds")
    private val aac = listOf("-c:a", "aac", "-ac", "2", "-b:a", "64k")
    private const val MASTERING = "master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"

    val all = listOf(
        Fixture("av1-1080p-8bit.mkv", "AV1 Main 8-bit at 1080p", picture("1920x1080", 24, 2) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-b:v", "400k", "-pix_fmt", "yuv420p") + aac),
        Fixture("av1-1080p-10bit.mkv", "AV1 Main 10-bit at 1080p", picture("1920x1080", 24, 2) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-b:v", "400k", "-pix_fmt", "yuv420p10le") + aac),
        Fixture("av1-2160p-10bit.mkv", "AV1 Main 10-bit at 4K", picture("3840x2160", 5, 1) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-tiles", "2x2", "-b:v", "800k", "-pix_fmt", "yuv420p10le") + aac),
        Fixture(
            "hevc-2160p-hdr10.mkv", "HEVC Main 10 at 4K, HDR10 (PQ, BT.2020, mastering metadata)",
            picture("3840x2160", 5, 1) + listOf(
                "-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020", "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
                "-x265-params", "log-level=error:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:hdr10=1:$MASTERING",
            ) + aac,
        ),
        Fixture(
            "hevc-2160p-hlg.mkv", "HEVC Main 10 at 4K, HLG",
            picture("3840x2160", 5, 1) + listOf(
                "-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020", "-color_trc", "arib-std-b67", "-colorspace", "bt2020nc",
                "-x265-params", "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc",
            ) + aac,
        ),
        Fixture("truehd.mkv", "TrueHD audio", picture("320x180", 10, 2) + listOf("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "truehd", "-strict", "-2", "-ar", "48000", "-ac", "2")),
        Fixture("vp9-1080p.webm", "VP9 Profile 0 at 1080p, Opus", picture("1920x1080", 24, 2) + listOf("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-b:v", "400k", "-pix_fmt", "yuv420p", "-c:a", "libopus", "-b:a", "64k")),
    )

    fun make(fixture: Fixture, directory: Path): Path {
        val output = directory.resolve(fixture.name)
        val process = ProcessBuilder(listOf("ffmpeg", "-y", "-loglevel", "error") + fixture.arguments + output.toString()).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(300, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg could not make ${fixture.name}: $log" }
        return output
    }
}
