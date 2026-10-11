package com.ngocdevv.grokbotemoji.orb

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val BODY_CENTER = 114.2705
private const val EYE_ORIGIN_Y = 92.0
private const val ORBIT_CENTER_X = 114.25
private const val ORBIT_CENTER_Y = 106.25
private const val ORBIT_RADIUS_X = 148.45
private const val ORBIT_RADIUS_Y = 56.45
private const val ORBIT_SPEED = 1.55096587
private const val PARTICLE_MIN_RADIUS = 4.13
private const val PARTICLE_RADIUS_RANGE = 3.37
private const val PARTICLE_MIN_OPACITY = 0.685
private const val PARTICLE_OPACITY_RANGE = 0.315

private const val EYE_CYCLE_DURATION = 13.6
private const val MORPH_UP_START = 3.35
private const val MORPH_UP_END = 4.15
private const val MORPH_DOWN_START = 9.25
private const val MORPH_DOWN_END = 10.05

/** Seconds; the Expo scene uses 320 ms. */
private const val EXPRESSION_MORPH_DURATION = 0.32
private const val EXPRESSION_POSE_MORPH_DURATION = 0.72
private val ExpressionEasing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/**
 * Per-Orb mutable state. Snapshot-backed fields invalidate the draw pass when they change; the
 * scratch buffers are reused frame after frame so steady-state drawing allocates nothing.
 */
private class GrokOrbSceneState(initialExpression: GrokOrbExpression) {
    var startNanos = -1L
    var frameTime by mutableDoubleStateOf(0.0)
    var fromExpression by mutableStateOf(initialExpression)
    var toExpression by mutableStateOf(initialExpression)
    var transitionStartTime by mutableDoubleStateOf(0.0)
    var trailStartTime by mutableDoubleStateOf(0.0)

    val naturalLeft = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val naturalRight = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val previousLeft = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val previousRight = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val selectedLeft = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val selectedRight = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val leftEye = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val rightEye = FloatArray(GrokOrbGeometry.EYE_FLOAT_COUNT)
    val leftEyePath = Path()
    val rightEyePath = Path()
    val trails = GrokOrbTrailRenderer()
}

/**
 * Port of `grok-orb-scene.tsx` (exported as `GrokOrb` in the Expo project).
 *
 * The Expo version drives Skia from Reanimated shared values. Here a frame clock feeds a Compose
 * [Canvas] and all the per-frame values are recomputed from the elapsed time, which keeps the
 * maths identical: same orbit, same eye morph cycle, same cadence, same trails.
 *
 * @param paused Stops the animation on its reference pose.
 * @param trailReplayKey Restarts the one-shot colour trails whenever this value changes.
 */
@Composable
fun GrokOrb(
    modifier: Modifier = Modifier,
    expression: GrokOrbExpression = GrokOrbExpression.Default,
    paused: Boolean = false,
    trailReplayKey: Int = 0,
    contentDescription: String = "Animated Grok Bot Orb",
) {
    val reduceMotion = rememberReduceMotion()
    val isStatic = paused || reduceMotion
    val state = remember { GrokOrbSceneState(expression) }

    // Equivalent of Skia's `useClock`: seconds since the Orb was mounted.
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (state.startNanos < 0) state.startNanos = nanos
                state.frameTime = (nanos - state.startNanos) / 1_000_000_000.0
            }
        }
    }

    LaunchedEffect(expression) {
        if (state.toExpression != expression) {
            state.fromExpression = state.toExpression
            state.toExpression = expression
            state.transitionStartTime = state.frameTime
        }
    }

    LaunchedEffect(isStatic) {
        if (state.fromExpression != state.toExpression) {
            state.transitionStartTime = state.frameTime
        }
    }

    LaunchedEffect(trailReplayKey) {
        state.trailStartTime = state.frameTime
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        drawOrb(state, isStatic)
    }
}

private fun animatedExpressionValue(time: Double, motion: GrokOrbExpressionMotion): Double {
    val total = motion.totalDuration
    var segmentTime = ((time % total) + total) % total
    var poseIndex = 0

    for (index in motion.durations.indices) {
        if (segmentTime < motion.durations[index]) {
            poseIndex = index
            break
        }
        segmentTime -= motion.durations[index]
    }

    val segmentDuration = motion.durations[poseIndex]
    val morphDuration = min(EXPRESSION_POSE_MORPH_DURATION, segmentDuration * 0.42)
    val holdDuration = segmentDuration - morphDuration
    val morphProgress = GrokOrbMath.smoothstep((segmentTime - holdDuration) / morphDuration)

    return poseIndex + morphProgress
}

private fun eyeMorphAt(time: Double): Double {
    val localTime = time % EYE_CYCLE_DURATION

    if (localTime < MORPH_UP_START) return 0.0

    if (localTime < MORPH_UP_END) {
        return GrokOrbMath.smoothstep((localTime - MORPH_UP_START) / (MORPH_UP_END - MORPH_UP_START))
    }

    if (localTime < MORPH_DOWN_START) return 1.0

    if (localTime < MORPH_DOWN_END) {
        return 1 - GrokOrbMath.smoothstep(
            (localTime - MORPH_DOWN_START) / (MORPH_DOWN_END - MORPH_DOWN_START),
        )
    }

    return 0.0
}

/** Returns `progress` (−1 outside the morph windows) and whether the eyes are morphing up. */
private fun transitionAt(time: Double): Pair<Boolean, Double> {
    val localTime = time % EYE_CYCLE_DURATION

    if (localTime >= MORPH_UP_START && localTime < MORPH_UP_END) {
        return true to (localTime - MORPH_UP_START) / (MORPH_UP_END - MORPH_UP_START)
    }

    if (localTime >= MORPH_DOWN_START && localTime < MORPH_DOWN_END) {
        return false to (localTime - MORPH_DOWN_START) / (MORPH_DOWN_END - MORPH_DOWN_START)
    }

    return false to -1.0
}

private fun particleDepth(angle: Double): Double = (1 - sin(angle)) * 0.5

/** Interpolated eye polygon for a preset expression at [value] (pose index + morph). */
private fun presetEyeInto(motion: GrokOrbExpressionMotion, value: Double, left: Boolean, out: FloatArray) {
    val lastIndex = motion.poseIndices.size - 1
    val clamped = GrokOrbMath.clamp(value, 0.0, lastIndex.toDouble())
    val lower = min(clamped.toInt(), lastIndex - 1)
    val fraction = (clamped - lower).toFloat()
    val fromPose = GrokOrbGeometry.poses[motion.poseIndices[lower]]
    val toPose = GrokOrbGeometry.poses[motion.poseIndices[lower + 1]]

    GrokOrbMath.lerpInto(
        if (left) fromPose.left else fromPose.right,
        if (left) toPose.left else toPose.right,
        fraction,
        out,
    )
}

private fun DrawScope.drawOrb(state: GrokOrbSceneState, isStatic: Boolean) {
    val time = if (isStatic) 0.0 else state.frameTime
    val trailTime = if (isStatic) 0.0 else max(0.0, time - state.trailStartTime)
    val fromExpression = state.fromExpression
    val toExpression = state.toExpression
    val previousIsNatural = fromExpression.isNatural
    val expressionIsNatural = toExpression.isNatural

    val expressionProgress = if (isStatic || fromExpression == toExpression) {
        1.0
    } else {
        ExpressionEasing.transform(
            GrokOrbMath.clamp((time - state.transitionStartTime) / EXPRESSION_MORPH_DURATION, 0.0, 1.0).toFloat(),
        ).toDouble()
    }

    // Eye geometry ---------------------------------------------------------------------------

    val morph = eyeMorphAt(time)
    GrokOrbMath.lerpInto(GrokOrbGeometry.lowerLeftEye, GrokOrbGeometry.upperLeftEye, morph.toFloat(), state.naturalLeft)
    GrokOrbMath.lerpInto(GrokOrbGeometry.lowerRightEye, GrokOrbGeometry.upperRightEye, morph.toFloat(), state.naturalRight)

    val previousMotion = GrokOrbGeometry.motion(fromExpression)
    val selectedMotion = GrokOrbGeometry.motion(toExpression)
    val previousValue = animatedExpressionValue(time, previousMotion)
    val selectedValue = animatedExpressionValue(time, selectedMotion)

    val fromLeft = if (previousIsNatural) state.naturalLeft else state.previousLeft.also {
        presetEyeInto(previousMotion, previousValue, left = true, out = it)
    }
    val fromRight = if (previousIsNatural) state.naturalRight else state.previousRight.also {
        presetEyeInto(previousMotion, previousValue, left = false, out = it)
    }
    val toLeft = if (expressionIsNatural) state.naturalLeft else state.selectedLeft.also {
        presetEyeInto(selectedMotion, selectedValue, left = true, out = it)
    }
    val toRight = if (expressionIsNatural) state.naturalRight else state.selectedRight.also {
        presetEyeInto(selectedMotion, selectedValue, left = false, out = it)
    }

    GrokOrbMath.lerpInto(fromLeft, toLeft, expressionProgress.toFloat(), state.leftEye)
    GrokOrbMath.lerpInto(fromRight, toRight, expressionProgress.toFloat(), state.rightEye)
    GrokOrbMath.polygonPath(state.leftEye, state.leftEyePath)
    GrokOrbMath.polygonPath(state.rightEye, state.rightEyePath)

    val naturalWeight = when {
        previousIsNatural && expressionIsNatural -> 1.0
        previousIsNatural -> 1 - expressionProgress
        expressionIsNatural -> expressionProgress
        else -> 0.0
    }
    val presetWeight = 1 - naturalWeight

    // Transforms -------------------------------------------------------------------------------

    val bodyTranslateX = 1.4878 * sin((PI * 2 * time) / 18.29 - 3.0698)
    val bodyTranslateY = 1.4539 * sin((PI * 2 * time) / 7.835 - 0.828)
    val bodyRotationDegrees = 1.9847 * sin((PI * 2 * time) / 13.71 + 2.2731)

    val (isMorphingUp, cycleProgress) = transitionAt(time)
    val selectionPulse = sin(PI * expressionProgress)
    val eyeScaleX: Double
    val eyeScaleY: Double
    if (cycleProgress < 0) {
        val selectionScale = 1 + 0.045 * selectionPulse
        eyeScaleX = selectionScale
        eyeScaleY = selectionScale
    } else {
        val naturalPulse = sin(PI * cycleProgress)
        val overshoot = 1 + 0.052 * naturalPulse + 0.045 * selectionPulse
        val blink = if (isMorphingUp) naturalPulse.pow(10) * 0.58 else 0.0
        eyeScaleX = overshoot
        eyeScaleY = overshoot * (1 - blink)
    }

    val leftEyeTranslateX =
        naturalWeight * (-4.05 + 0.22 * morph + 0.85 * sin(time * 0.71)) +
            presetWeight * (0.68 * sin(time * 0.71) + 0.2 * sin(time * 1.31 + 0.4))
    val leftEyeTranslateY =
        naturalWeight * (-2.9 + 4.25 * morph + 0.7 * sin(time * 0.93 + 1.1)) +
            presetWeight * 0.5 * sin(time * 0.93 + 1.1)
    val rightEyeTranslateX =
        naturalWeight * (-5.6 + 1.1 * morph + 0.8 * sin(time * 0.66 + 2.1)) +
            presetWeight * (0.64 * sin(time * 0.66 + 2.1) + 0.18 * sin(time * 1.23 + 1.7))
    val rightEyeTranslateY =
        naturalWeight * (-2.1 + 2.6 * morph + 0.65 * sin(time * 0.87 + 2.4)) +
            presetWeight * 0.46 * sin(time * 0.87 + 2.4)

    // Layout: centre the Orb, scale it to 72% of the shorter side and mirror it horizontally
    // exactly like the Expo scene (`scaleX: -orbScale`).
    val center = BODY_CENTER.toFloat()
    val orbScale = min(size.width, size.height) * 0.36f / center

    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(-orbScale, orbScale, pivot = Offset.Zero)
        translate(-center, -center)
    }) {
        state.trails.draw(this, GrokOrbTrailLayer.Back, trailTime)

        // Orbiting particles -----------------------------------------------------------------

        for (phase in doubleArrayOf(0.42, 0.42 + PI)) {
            val angle = time * ORBIT_SPEED + phase
            val depth = particleDepth(angle)
            val radius = PARTICLE_MIN_RADIUS + PARTICLE_RADIUS_RANGE * depth
            val opacity = PARTICLE_MIN_OPACITY + PARTICLE_OPACITY_RANGE * depth

            drawCircle(
                color = GrokOrbPalette.Body,
                radius = radius.toFloat(),
                center = Offset(
                    (ORBIT_CENTER_X + ORBIT_RADIUS_X * cos(angle)).toFloat(),
                    (ORBIT_CENTER_Y + ORBIT_RADIUS_Y * sin(angle)).toFloat(),
                ),
                alpha = opacity.toFloat(),
            )
        }

        // Body + eyes --------------------------------------------------------------------------

        withTransform({
            translate(center, center)
            translate(bodyTranslateX.toFloat(), bodyTranslateY.toFloat())
            rotate(bodyRotationDegrees.toFloat(), pivot = Offset.Zero)
            translate(-center, -center)
        }) {
            drawPath(GrokOrbGeometry.bodyPath, GrokOrbPalette.Body)

            clipPath(GrokOrbGeometry.bodyPath) {
                withTransform({
                    translate(center, EYE_ORIGIN_Y.toFloat())
                    scale(eyeScaleX.toFloat(), eyeScaleY.toFloat(), pivot = Offset.Zero)
                    translate(-center, -EYE_ORIGIN_Y.toFloat())
                }) {
                    translate(leftEyeTranslateX.toFloat(), leftEyeTranslateY.toFloat()) {
                        drawPath(state.leftEyePath, GrokOrbPalette.Eye)
                    }
                    translate(rightEyeTranslateX.toFloat(), rightEyeTranslateY.toFloat()) {
                        drawPath(state.rightEyePath, GrokOrbPalette.Eye)
                    }
                }
            }
        }

        state.trails.draw(this, GrokOrbTrailLayer.Front, trailTime)
    }
}
