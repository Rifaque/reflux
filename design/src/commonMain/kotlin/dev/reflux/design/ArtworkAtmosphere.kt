package dev.reflux.design

/**
 * The atmosphere a piece of artwork lends the page around it: a deep, muted tone of its dominant hue.
 *
 * It is for the environment only (the wash behind a details page or Home's hero), never for controls,
 * navigation, focus, or text: the interface stays neutral and artwork brings the color. The tone is dark
 * enough that primary text stays at 7:1 on it, so it can sit behind anything.
 *
 * Deterministic: a saturation- and lightness-weighted hue histogram picks the dominant vivid hue. Grey or
 * near-monochrome artwork has no atmosphere.
 */
object ArtworkAtmosphere {
    private const val BUCKETS = 36
    private const val SATURATION = 0.45
    private const val LIGHTNESS = 0.20

    /** [pixels] are 0xAARRGGBB values, typically a sparse sample of the image. Null for colorless artwork. */
    fun from(pixels: LongArray): Argb? = dominantHue(pixels)?.let(::ofHue)

    /** The hue (0–360) the atmosphere of [pixels] is made from; null for colorless artwork. */
    fun dominantHue(pixels: LongArray): Double? {
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
        if (total == 0.0) return null
        // Smooth over neighbouring buckets so a hue split across a boundary is not penalized.
        val smoothed = DoubleArray(BUCKETS) { i -> weights[i] + 0.5 * (weights[(i + 1) % BUCKETS] + weights[(i + BUCKETS - 1) % BUCKETS]) }
        val best = smoothed.indices.maxBy { smoothed[it] }
        if (weights[best] / total < 0.04) return null // not colorful enough to have a color of its own
        return hueSums[best] / weights[best]
    }

    /** The atmosphere of a [hue] (0–360), for generated artwork that is known by its hue. */
    fun ofHue(hue: Double): Argb = Hsl(((hue % 360) + 360) % 360, SATURATION, LIGHTNESS).toArgb()
}
