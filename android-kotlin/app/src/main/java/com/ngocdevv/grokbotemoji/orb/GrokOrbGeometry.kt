package com.ngocdevv.grokbotemoji.orb

import androidx.compose.ui.graphics.Path

class GrokOrbEyePose(val left: FloatArray, val right: FloatArray)

/**
 * One preset expression's pose loop. [poseIndices] repeats the first pose at the end so the loop
 * closes seamlessly, exactly like `[...sequence, sequence[0]]` in the Expo scene.
 */
class GrokOrbExpressionMotion(
    val poseIndices: IntArray,
    val durations: DoubleArray,
    val totalDuration: Double,
)

/** Parsed geometry shared by the animated Orb and the static previews. */
object GrokOrbGeometry {
    const val ORB_SIZE = 228.541f
    const val BODY_CENTER = 114.2705f

    /** Number of floats in every eye polygon (48 vertices × 2). */
    const val EYE_FLOAT_COUNT = 96

    val bodyPath: Path by lazy { SvgPath.path(GrokOrbPathData.BODY) }

    val lowerLeftEye: FloatArray by lazy { SvgPath.polygon(GrokOrbPathData.EYE_LOWER_LEFT) }
    val upperLeftEye: FloatArray by lazy { SvgPath.polygon(GrokOrbPathData.EYE_UPPER_LEFT) }
    val lowerRightEye: FloatArray by lazy { SvgPath.polygon(GrokOrbPathData.EYE_LOWER_RIGHT) }
    val upperRightEye: FloatArray by lazy { SvgPath.polygon(GrokOrbPathData.EYE_UPPER_RIGHT) }

    val poses: List<GrokOrbEyePose> by lazy {
        GrokOrbPathData.POSES.map { (left, right) ->
            GrokOrbEyePose(SvgPath.polygon(left), SvgPath.polygon(right))
        }
    }

    /** Stable cadence samples so every pose lasts a reproducible time inside its range. */
    private val cadenceVariations = doubleArrayOf(0.18, 0.72, 0.42, 0.9, 0.58)

    val motions: Map<GrokOrbExpression, GrokOrbExpressionMotion> by lazy {
        GrokOrbExpressionData.presets.mapIndexed { expressionIndex, expression ->
            val sequence = GrokOrbExpressionData.sequences.getValue(expression)
            val (minimum, maximum) = GrokOrbExpressionData.timingRanges.getValue(expression)
            val durations = DoubleArray(sequence.size) { poseIndex ->
                val variation = cadenceVariations[(expressionIndex + poseIndex) % cadenceVariations.size]
                minimum + (maximum - minimum) * variation
            }

            expression to GrokOrbExpressionMotion(
                poseIndices = (sequence + sequence[0]).toIntArray(),
                durations = durations,
                totalDuration = durations.sum(),
            )
        }.toMap()
    }

    /**
     * `Natural` has no preset loop; the Expo scene falls back to `happy` for the hidden side of
     * the cross-fade, so we do the same.
     */
    fun motion(expression: GrokOrbExpression): GrokOrbExpressionMotion =
        motions[expression] ?: motions.getValue(GrokOrbExpression.Happy)

    /**
     * Eye pose shown by the static thumbnails: the lower natural eyes, or the first pose of the
     * preset sequence.
     */
    fun previewEyes(expression: GrokOrbExpression): GrokOrbEyePose {
        if (expression.isNatural) return GrokOrbEyePose(lowerLeftEye, lowerRightEye)
        val poseIndex = GrokOrbExpressionData.sequences[expression]?.firstOrNull() ?: 0
        return poses[poseIndex]
    }

    private val previewPaths = HashMap<GrokOrbExpression, Pair<Path, Path>>()

    /** Cached (left, right) eye paths for the thumbnails. */
    fun previewEyePaths(expression: GrokOrbExpression): Pair<Path, Path> =
        synchronized(previewPaths) {
            previewPaths.getOrPut(expression) {
                val eyes = previewEyes(expression)
                GrokOrbMath.polygonPath(eyes.left, Path()) to GrokOrbMath.polygonPath(eyes.right, Path())
            }
        }
}
