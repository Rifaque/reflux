package dev.reflux.design

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.pow

/** An sRGB color as 0xAARRGGBB. UI toolkits convert this to their own color type. */
@JvmInline
value class Argb(val value: Long) {
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) :
        this((alpha.toLong() shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong())

    val alpha: Int get() = ((value shr 24) and 0xff).toInt()
    val red: Int get() = ((value shr 16) and 0xff).toInt()
    val green: Int get() = ((value shr 8) and 0xff).toInt()
    val blue: Int get() = (value and 0xff).toInt()

    fun withAlpha(alpha: Float): Argb = Argb(red, green, blue, (alpha.coerceIn(0f, 1f) * 255).toInt())

    /** WCAG relative luminance (alpha ignored). */
    val luminance: Double
        get() {
            fun channel(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
        }

    /** WCAG contrast ratio against [other], 1..21. */
    fun contrast(other: Argb): Double {
        val a = luminance + 0.05
        val b = other.luminance + 0.05
        return if (a > b) a / b else b / a
    }

    /** Alpha-composites this color over an opaque [background]. */
    fun over(background: Argb): Argb {
        val a = alpha / 255.0
        fun mix(fg: Int, bg: Int) = (fg * a + bg * (1 - a)).toInt().coerceIn(0, 255)
        return Argb(mix(red, background.red), mix(green, background.green), mix(blue, background.blue))
    }

    override fun toString(): String = "#" + value.toString(16).padStart(8, '0')
}

/** Hue (0–360), saturation and lightness (0–1). */
internal data class Hsl(val hue: Double, val saturation: Double, val lightness: Double) {
    fun toArgb(): Argb {
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val x = c * (1 - abs((hue / 60) % 2 - 1))
        val m = lightness - c / 2
        val (r, g, b) = when {
            hue < 60 -> Triple(c, x, 0.0)
            hue < 120 -> Triple(x, c, 0.0)
            hue < 180 -> Triple(0.0, c, x)
            hue < 240 -> Triple(0.0, x, c)
            hue < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun channel(v: Double) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return Argb(channel(r), channel(g), channel(b))
    }

    companion object {
        fun of(color: Argb): Hsl {
            val r = color.red / 255.0
            val g = color.green / 255.0
            val b = color.blue / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val lightness = (max + min) / 2
            if (max == min) return Hsl(0.0, 0.0, lightness)
            val d = max - min
            val saturation = if (lightness > 0.5) d / (2 - max - min) else d / (max + min)
            val hue = when (max) {
                r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60
                g -> ((b - r) / d + 2) * 60
                else -> ((r - g) / d + 4) * 60
            }
            return Hsl(hue, saturation, lightness)
        }
    }
}
