package dev.reflux.playback.mpv

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Generates small real media files with FFmpeg for engine tests. */
object TestMedia {
    val ffmpegAvailable: Boolean by lazy {
        runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
    }

    /** 3 s of 320x240 H.264 with English and Japanese AAC audio and an embedded English subtitle. */
    fun multiTrackMkv(directory: Path): Path {
        val srt = directory.resolve("embedded.srt")
        Files.writeString(srt, "1\n00:00:00,000 --> 00:00:02,000\nHello\n")
        val output = directory.resolve("Sample (2020).mkv")
        val chapters = directory.resolve("chapters.txt")
        Files.writeString(
            chapters,
            ";FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=1000\ntitle=Intro\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=1000\nEND=3000\ntitle=Chapter 1\n",
        )
        run(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc=duration=3:size=320x240:rate=24",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
            "-f", "lavfi", "-i", "sine=frequency=660:duration=3",
            "-i", srt.toString(),
            "-i", chapters.toString(),
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s", "-map_chapters", "4",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac:a:1", "6", "-c:s", "srt",
            "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=jpn", "-metadata:s:s:0", "language=eng",
            output.toString(),
        )
        return output
    }

    /** 2 s of 10-bit HEVC tagged as HDR10 (PQ transfer). */
    fun hdrHevcMp4(directory: Path): Path {
        val output = directory.resolve("hdr.mp4")
        run(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=duration=2:size=640x360:rate=24",
            "-c:v", "libx265", "-pix_fmt", "yuv420p10le", "-x265-params", "log-level=error",
            "-color_primaries", "bt2020", "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
            output.toString(),
        )
        return output
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $output" }
    }
}
