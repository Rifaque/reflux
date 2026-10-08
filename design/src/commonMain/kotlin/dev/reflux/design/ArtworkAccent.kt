package dev.reflux.design

import kotlin.math.roundToInt

/**
 * Picks an accent color from artwork so details pages and the player feel tied to what is playing.
 *
 * Deterministic: a saturation- and lightness-weighted hue histogram picks the dominant vivid hue; the color is
 * then adjusted until dark text on it meets WCAG AA. Grey or near-monochrome artwork keeps the default accent.
 */
object ArtworkAccent {
    private const val BUCKETS = 36

    /** [pixels] are 0xAARRGGBB values, typically a small downscaled thumbnail. */
    fun from(pixels: LongArray): Argb {
        val weights = DoubleArray(BUCKETS)
        val hueSums = DoubleArray(BUCKETS)
        var total = 0.0
        for (pixel in pixels) {
            val color = Argb(pixel)
            if (color.alpha < 128) continue
            val hsl = Hsl.of(color)
            total += 1
            // Vivid mid-tones dominate; near-black, near-white, and grey pixels barely count.
            val weight = hsl.saturation * (1 - kotlin.math.abs(hsl.lightness - 0.5) * 2)
            if (weight < 0.08) continue
            val bucket = (hsl.hue / 360 * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            weights[bucket] += weight
            hueSums[bucket] += hsl.hue * weight
        }
        if (total == 0.0) return Palette.accent
        // Smooth over neighbouring buckets so a hue split across a boundary is not penalized.
        val smoothed = DoubleArray(BUCKETS) { i -> weights[i] + 0.5 * (weights[(i + 1) % BUCKETS] + weights[(i + BUCKETS - 1) % BUCKETS]) }
        val best = smoothed.indices.maxBy { smoothed[it] }
        if (weights[best] / total < 0.04) return Palette.accent // not colorful enough to be the artwork's color
        val hue = hueSums[best] / weights[best]
        return readable(Hsl(hue, 0.70, 0.62).toArgb())
    }

    /** Lightens [color] until [Palette.onAccent] text meets 4.5:1 on it. */
    fun readable(color: Argb): Argb {
        var hsl = Hsl.of(color)
        var result = color
        while (Palette.onAccent.contrast(result) < 4.5 && hsl.lightness < 0.95) {
            hsl = hsl.copy(lightness = ((hsl.lightness + 0.02) * 100).roundToInt() / 100.0)
            result = hsl.toArgb()
        }
        return result
    }
}
