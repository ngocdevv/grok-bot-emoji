package com.ngocdevv.grokbotemoji.orb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/**
 * Static thumbnail of an expression (port of `grok-expression-preview.tsx`). It paints the body
 * plus the first pose of the expression without the horizontal mirror or any motion, matching the
 * Expo thumbnails pixel for pixel.
 */
@Composable
fun GrokExpressionPreview(
    expression: GrokOrbExpression,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val eyes = remember(expression) { GrokOrbGeometry.previewEyePaths(expression) }

    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        val scale = this.size.width / GrokOrbGeometry.ORB_SIZE

        scale(scale, scale, pivot = Offset.Zero) {
            drawPath(GrokOrbGeometry.bodyPath, GrokOrbPalette.Body)
            drawPath(eyes.first, GrokOrbPalette.Eye)
            drawPath(eyes.second, GrokOrbPalette.Eye)
        }
    }
}
