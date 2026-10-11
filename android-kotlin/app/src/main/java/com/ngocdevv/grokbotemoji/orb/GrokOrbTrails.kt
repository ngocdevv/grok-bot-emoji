package com.ngocdevv.grokbotemoji.orb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class GrokOrbTrailConfig(
    val arc: Double,
    val baseHue: Double,
    val baseWidth: Double,
    val follow: Double,
    val hueSpan: Double,
    val hueVelocity: Double,
    val lambdaOffset: Double,
    val lambdaVelocity: Double,
    val radius: Double,
    val radiusVelocity: Double,
    val roll: Double,
    val spawnDelay: Double,
    val tilt: Double,
)

enum class GrokOrbTrailLayer { Back, Front }

/**
 * Port of `grok-orb-trails.tsx`: nine one-shot colour ribbons orbiting the head on three tilted
 * planes. Each ribbon is split into "behind" and "in front" segments so the body can be painted
 * between the two layers. One instance owns scratch buffers, so keep it per Orb.
 */
class GrokOrbTrailRenderer {
    private val xs = DoubleArray(MAX_POINT_COUNT)
    private val ys = DoubleArray(MAX_POINT_COUNT)
    private val zs = DoubleArray(MAX_POINT_COUNT)
    private val leftXs = DoubleArray(MAX_POINT_COUNT)
    private val leftYs = DoubleArray(MAX_POINT_COUNT)
    private val rightXs = DoubleArray(MAX_POINT_COUNT)
    private val rightYs = DoubleArray(MAX_POINT_COUNT)
    private val path = Path()
    private val colorStops = Array(GRADIENT_POSITIONS.size) { 0f to Color.Black }

    /**
     * Draws every ribbon segment that belongs to [layer]. The [DrawScope] transform must already
     * map Orb units (0…228.541) to the canvas.
     */
    fun draw(scope: DrawScope, layer: GrokOrbTrailLayer, time: Double) {
        for (config in CONFIGS) {
            drawRibbon(scope, config, layer, time)
        }
    }

    private fun drawRibbon(scope: DrawScope, config: GrokOrbTrailConfig, layer: GrokOrbTrailLayer, time: Double) {
        val life = trailLife(time, config.spawnDelay)
        val retraction = trailRetraction(life)
        val visibleArc = trailVisibleArc(life, config)

        if (life <= 0 || retraction >= 1 || visibleArc < 0.025) return

        val currentLambda = trailLambda(life, config)
        val radius = config.radius + config.radiusVelocity * life
        val pointCount = min(MAX_POINT_COUNT, max(2, ceil(visibleArc / MAX_SAMPLE_ANGLE).toInt() + 1))
        var pathLength = 0.0

        for (index in 0 until pointCount) {
            val progress = index.toDouble() / (pointCount - 1)
            val lambda = currentLambda - visibleArc * (1 - progress)
            projectOrbit(lambda, radius, config.tilt, config.roll, index)

            if (index > 0) {
                pathLength += hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1])
            }
        }

        val headDepth = 0.72 + 0.28 * GrokOrbMath.clamp(zs[pointCount - 1], 0.0, 1.0)
        val growth = GrokOrbMath.smoothstep(GrokOrbMath.clamp(life / 0.34, 0.0, 1.0))
        val rawWidth = max(
            config.baseWidth * headDepth * 1.7 * growth * (1 - 0.72 * retraction * retraction),
            0.5,
        )
        val width = min(rawWidth, pathLength * 0.34)

        for (index in 0 until pointCount) {
            val previous = max(index - 1, 0)
            val next = min(index + 1, pointCount - 1)
            val dx = xs[next] - xs[previous]
            val dy = ys[next] - ys[previous]
            val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
            val halfWidth = (width * (0.5 + 0.5 * (index.toDouble() / (pointCount - 1)))) / 2
            val normalX = (-dy / length) * halfWidth
            val normalY = (dx / length) * halfWidth

            leftXs[index] = xs[index] + normalX
            leftYs[index] = ys[index] + normalY
            rightXs[index] = xs[index] - normalX
            rightYs[index] = ys[index] - normalY
        }

        path.reset()
        var hasContour = false
        var groupStart = 0

        while (groupStart < pointCount) {
            val groupIsFront = zs[groupStart] >= 0
            var groupEnd = groupStart

            while (groupEnd + 1 < pointCount && (zs[groupEnd + 1] >= 0) == groupIsFront) {
                groupEnd += 1
            }

            if (groupIsFront == (layer == GrokOrbTrailLayer.Front)) {
                val expandedStart = max(groupStart - 1, 0)
                val expandedEnd = min(groupEnd + 1, pointCount - 1)

                if (expandedEnd > expandedStart) {
                    addRibbonContour(expandedStart, expandedEnd, pointCount)
                    hasContour = true
                }
            }

            groupStart = groupEnd + 1
        }

        if (!hasContour) return

        val tailLambda = currentLambda - visibleArc
        val tailX = HEAD_CENTER + orbitX(tailLambda, radius, config.tilt, config.roll)
        val tailY = HEAD_CENTER + orbitY(tailLambda, radius, config.tilt, config.roll)
        val headX = HEAD_CENTER + orbitX(currentLambda, radius, config.tilt, config.roll)
        val headY = HEAD_CENTER + orbitY(currentLambda, radius, config.tilt, config.roll)
        val movingHue = config.baseHue + config.hueVelocity * life

        colorStops[0] = GRADIENT_POSITIONS[0] to GrokOrbMath.hslColor(movingHue, 0.56, 0.56)
        colorStops[1] = GRADIENT_POSITIONS[1] to GrokOrbMath.hslColor(movingHue + config.hueSpan * 0.25, 0.56, 0.59)
        colorStops[2] = GRADIENT_POSITIONS[2] to GrokOrbMath.hslColor(movingHue + config.hueSpan * 0.5, 0.56, 0.62)
        colorStops[3] = GRADIENT_POSITIONS[3] to GrokOrbMath.hslColor(movingHue + config.hueSpan * 0.75, 0.56, 0.64)
        colorStops[4] = GRADIENT_POSITIONS[4] to GrokOrbMath.hslColor(movingHue + config.hueSpan, 0.56, 0.67)

        val brush = Brush.linearGradient(
            *colorStops,
            start = Offset(tailX.toFloat(), tailY.toFloat()),
            end = Offset(headX.toFloat(), headY.toFloat()),
        )

        scope.drawPath(path = path, brush = brush, alpha = min(1.0, life / 0.26).toFloat())
    }

    private fun addRibbonContour(start: Int, end: Int, pointCount: Int) {
        path.moveTo(leftXs[start].toFloat(), leftYs[start].toFloat())

        for (index in start + 1..end) {
            path.lineTo(leftXs[index].toFloat(), leftYs[index].toFloat())
        }

        if (end == pointCount - 1) {
            val previous = max(end - 1, 0)
            val dx = xs[end] - xs[previous]
            val dy = ys[end] - ys[previous]
            val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
            val tangentX = dx / length
            val tangentY = dy / length
            val capRadius = hypot(leftXs[end] - xs[end], leftYs[end] - ys[end]).takeIf { it != 0.0 } ?: 0.2

            path.quadraticTo(
                (xs[end] + tangentX * capRadius * 2).toFloat(),
                (ys[end] + tangentY * capRadius * 2).toFloat(),
                rightXs[end].toFloat(),
                rightYs[end].toFloat(),
            )
        } else {
            path.lineTo(rightXs[end].toFloat(), rightYs[end].toFloat())
        }

        var index = end - 1
        while (index >= start) {
            path.lineTo(rightXs[index].toFloat(), rightYs[index].toFloat())
            index -= 1
        }

        if (start == 0) {
            val next = min(start + 1, pointCount - 1)
            val dx = xs[next] - xs[start]
            val dy = ys[next] - ys[start]
            val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
            val tangentX = dx / length
            val tangentY = dy / length
            val capRadius = hypot(leftXs[start] - xs[start], leftYs[start] - ys[start]).takeIf { it != 0.0 } ?: 0.2

            path.quadraticTo(
                (xs[start] - tangentX * capRadius * 2).toFloat(),
                (ys[start] - tangentY * capRadius * 2).toFloat(),
                leftXs[start].toFloat(),
                leftYs[start].toFloat(),
            )
        } else {
            path.lineTo(leftXs[start].toFloat(), leftYs[start].toFloat())
        }

        path.close()
    }

    /** Writes the projected orbit point for [lambda] into the scratch buffers at [index]. */
    private fun projectOrbit(lambda: Double, radius: Double, tilt: Double, roll: Double, index: Int) {
        xs[index] = HEAD_CENTER + orbitX(lambda, radius, tilt, roll)
        ys[index] = HEAD_CENTER + orbitY(lambda, radius, tilt, roll)
        zs[index] = cos(lambda) * cos(tilt)
    }

    companion object {
        const val HEAD_CENTER = 114.2705
        private const val HUMMING_START = 0.8
        private const val TRAIL_MAX_LIFE = 9.0
        private const val RETRACTION_START = TRAIL_MAX_LIFE * 0.55
        private const val RETRACTION_DURATION = 0.5
        private const val MAX_SAMPLE_ANGLE = 0.09
        private const val MAX_POINT_COUNT = 48
        private val GRADIENT_POSITIONS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        // The website creates three orbital planes and emits three ribbons on each plane. Its
        // values are randomized per visit; these are stable samples from the same ranges so the
        // native version remains reproducible (identical to the Expo port).
        val CONFIGS: List<GrokOrbTrailConfig> = listOf(
            GrokOrbTrailConfig(arc = 2.72, baseHue = 79.0, baseWidth = 6.3, follow = 0.84, hueSpan = -74.0, hueVelocity = 24.0, lambdaOffset = 0.08, lambdaVelocity = 0.78, radius = 115.2, radiusVelocity = 1.4, roll = -0.31, spawnDelay = 0.0, tilt = 0.23),
            GrokOrbTrailConfig(arc = 3.18, baseHue = 122.0, baseWidth = 6.8, follow = 0.76, hueSpan = 61.0, hueVelocity = -31.0, lambdaOffset = 0.14, lambdaVelocity = 1.02, radius = 116.6, radiusVelocity = 0.5, roll = 0.78, spawnDelay = 0.07, tilt = 0.4),
            GrokOrbTrailConfig(arc = 2.38, baseHue = 160.0, baseWidth = 5.9, follow = 0.92, hueSpan = 89.0, hueVelocity = 38.0, lambdaOffset = 0.04, lambdaVelocity = 0.57, radius = 114.9, radiusVelocity = 2.1, roll = 1.86, spawnDelay = 0.15, tilt = 0.47),
            GrokOrbTrailConfig(arc = 3.34, baseHue = 204.0, baseWidth = 7.1, follow = 0.8, hueSpan = -48.0, hueVelocity = -20.0, lambdaOffset = 0.16, lambdaVelocity = 0.91, radius = 134.2, radiusVelocity = 1.8, roll = -0.25, spawnDelay = 0.235, tilt = 0.2),
            GrokOrbTrailConfig(arc = 2.9, baseHue = 244.0, baseWidth = 6.5, follow = 0.88, hueSpan = 82.0, hueVelocity = 29.0, lambdaOffset = 0.1, lambdaVelocity = 0.64, radius = 135.8, radiusVelocity = 0.9, roll = 0.83, spawnDelay = 0.315, tilt = 0.37),
            GrokOrbTrailConfig(arc = 2.46, baseHue = 286.0, baseWidth = 5.7, follow = 0.74, hueSpan = -92.0, hueVelocity = -40.0, lambdaOffset = 0.06, lambdaVelocity = 1.08, radius = 135.1, radiusVelocity = 2.3, roll = 1.93, spawnDelay = 0.385, tilt = 0.44),
            GrokOrbTrailConfig(arc = 3.06, baseHue = 322.0, baseWidth = 6.9, follow = 0.94, hueSpan = 58.0, hueVelocity = 21.0, lambdaOffset = 0.13, lambdaVelocity = 0.52, radius = 154.6, radiusVelocity = 0.3, roll = -0.36, spawnDelay = 0.475, tilt = 0.27),
            GrokOrbTrailConfig(arc = 2.58, baseHue = 7.0, baseWidth = 6.0, follow = 0.78, hueSpan = -69.0, hueVelocity = -27.0, lambdaOffset = 0.02, lambdaVelocity = 0.87, radius = 153.4, radiusVelocity = 1.6, roll = 0.72, spawnDelay = 0.555, tilt = 0.34),
            GrokOrbTrailConfig(arc = 3.26, baseHue = 45.0, baseWidth = 7.3, follow = 0.86, hueSpan = 94.0, hueVelocity = 35.0, lambdaOffset = 0.11, lambdaVelocity = 0.69, radius = 155.1, radiusVelocity = 2.4, roll = 1.8, spawnDelay = 0.64, tilt = 0.49),
        )

        // --- Motion helpers (one-to-one with the Expo worklets) ---

        private fun integralEaseInOutCubic(value: Double): Double =
            if (value <= 0.5) value.pow(4) else value - 0.5 + (1 - value).pow(4)

        private fun hummingSpinAngle(time: Double): Double {
            if (time <= 0) return 0.0

            if (time < 0.5) {
                return 3.5 * integralEaseInOutCubic(time / 0.5)
            }

            val firstStageAngle = 1.75

            if (time < 1.3) {
                val progress = (time - 0.5) / 0.8
                return firstStageAngle + 5.6 * progress - 4.32 * integralEaseInOutCubic(progress)
            }

            val secondStageAngle = firstStageAngle + 3.44

            return secondStageAngle + 1.6 * (time - 1.3) + 0.6 * (cos(0.65) - cos(0.5 * time))
        }

        private fun orbitX(lambda: Double, radius: Double, tilt: Double, roll: Double): Double {
            val horizontal = radius * sin(lambda)
            val vertical = -radius * cos(lambda) * sin(tilt)
            return horizontal * cos(roll) - vertical * sin(roll)
        }

        private fun orbitY(lambda: Double, radius: Double, tilt: Double, roll: Double): Double {
            val horizontal = radius * sin(lambda)
            val vertical = -radius * cos(lambda) * sin(tilt)
            return horizontal * sin(roll) + vertical * cos(roll)
        }

        private fun trailLife(time: Double, spawnDelay: Double): Double =
            time - HUMMING_START - 0.3 - spawnDelay

        private fun trailRetraction(life: Double): Double =
            GrokOrbMath.clamp((life - RETRACTION_START) / RETRACTION_DURATION, 0.0, 1.0)

        private fun trailLambda(life: Double, config: GrokOrbTrailConfig): Double {
            val spawnTime = 0.3 + config.spawnDelay
            val currentTime = spawnTime + max(life, 0.0)
            val spinAtSpawn = hummingSpinAngle(spawnTime)

            return spinAtSpawn -
                config.lambdaOffset +
                (hummingSpinAngle(currentTime) - spinAtSpawn) * config.follow +
                config.lambdaVelocity * max(life, 0.0)
        }

        private fun trailVisibleArc(life: Double, config: GrokOrbTrailConfig): Double {
            if (life <= 0) return 0.0

            val initialLambda = trailLambda(0.0, config)
            val currentLambda = trailLambda(life, config)
            val travelled = abs(currentLambda - initialLambda)
            val retraction = trailRetraction(life)
            val retractionEase = retraction * retraction * (3 - 2 * retraction)

            return min(travelled, config.arc * (1 - retractionEase))
        }
    }
}
