package com.ngocdevv.grokbotemoji.orb

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object GrokOrbMath {
    fun clamp(value: Double, minimum: Double, maximum: Double): Double = min(maximum, max(minimum, value))

    fun smoothstep(value: Double): Double {
        val clamped = clamp(value, 0.0, 1.0)
        return clamped * clamped * (3 - 2 * clamped)
    }

    /**
     * Weighted average of two equal-length vertex arrays, the same operation Skia performs in
     * `SkPath::interpolate`. `progress == 0` yields [from], `progress == 1` yields [to].
     */
    fun lerpInto(from: FloatArray, to: FloatArray, progress: Float, out: FloatArray) {
        val count = min(min(from.size, to.size), out.size)
        for (index in 0 until count) {
            out[index] = from[index] + (to[index] - from[index]) * progress
        }
    }

    /** Rebuilds [target] as the closed polygon described by `[x0, y0, x1, y1, …]`. */
    fun polygonPath(points: FloatArray, target: Path): Path {
        target.reset()
        if (points.size < 2) return target

        target.moveTo(points[0], points[1])
        var index = 2
        while (index + 1 < points.size) {
            target.lineTo(points[index], points[index + 1])
            index += 2
        }
        target.close()

        return target
    }

    /** HSL → sRGB, matching the worklet used for the trail gradients. */
    fun hslColor(hue: Double, saturation: Double, lightness: Double): Color {
        val normalizedHue = ((hue % 360) + 360) % 360
        val chroma = (1 - abs(2 * lightness - 1)) * saturation
        val sector = normalizedHue / 60
        val secondary = chroma * (1 - abs((sector % 2) - 1))
        var red = 0.0
        var green = 0.0
        var blue = 0.0

        when {
            sector < 1 -> { red = chroma; green = secondary }
            sector < 2 -> { red = secondary; green = chroma }
            sector < 3 -> { green = chroma; blue = secondary }
            sector < 4 -> { green = secondary; blue = chroma }
            sector < 5 -> { red = secondary; blue = chroma }
            else -> { red = chroma; blue = secondary }
        }

        val lightnessOffset = lightness - chroma / 2

        return Color(
            red = (red + lightnessOffset).toFloat().coerceIn(0f, 1f),
            green = (green + lightnessOffset).toFloat().coerceIn(0f, 1f),
            blue = (blue + lightnessOffset).toFloat().coerceIn(0f, 1f),
        )
    }
}
