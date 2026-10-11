package com.ngocdevv.grokbotemoji.orb

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CONTENT_MAX_WIDTH = 820.dp
private val GRID_GAP = 10.dp
private val CORNER_RADIUS = 22.dp
private val PressEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Port of `grok-orb-expression-picker.tsx`: the animated Orb, a responsive grid of expression
 * thumbnails and the colour-trail replay triggered by the "Tự nhiên" card.
 *
 * Uncontrolled by default; pass [expression] (and [onExpressionChange]) to control the selection
 * from a parent.
 *
 * @param paused Freezes the Orb on its reference pose.
 * @param contentDescription Overrides the Orb's accessibility description.
 */
@Composable
fun GrokOrbExpressionPicker(
    modifier: Modifier = Modifier,
    defaultExpression: GrokOrbExpression = GrokOrbExpression.Default,
    expression: GrokOrbExpression? = null,
    onExpressionChange: ((GrokOrbExpression) -> Unit)? = null,
    paused: Boolean = false,
    contentDescription: String? = null,
) {
    var internalExpression by rememberSaveable { mutableStateOf(defaultExpression) }
    var trailReplayKey by remember { mutableIntStateOf(0) }
    val selectedExpression = expression ?: internalExpression

    fun select(next: GrokOrbExpression) {
        if (expression == null) {
            internalExpression = next
        }

        onExpressionChange?.invoke(next)

        if (next.isNatural) {
            trailReplayKey += 1
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = CONTENT_MAX_WIDTH),
    ) {
        val contentWidth = maxWidth
        val columnCount = when {
            contentWidth >= 728.dp -> 6
            contentWidth >= 448.dp -> 5
            contentWidth < 328.dp -> 3
            else -> 4
        }
        val itemSize = ((contentWidth - GRID_GAP * (columnCount - 1)) / columnCount).coerceAtLeast(1.dp)
        val orbHeight = contentWidth.coerceIn(320.dp, 430.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GrokOrb(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(orbHeight),
                expression = selectedExpression,
                paused = paused,
                trailReplayKey = trailReplayKey,
                contentDescription = contentDescription
                    ?: "Orb đang thể hiện trạng thái ${selectedExpression.label}",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                GrokOrbExpression.entries.chunked(columnCount).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                        row.forEach { option ->
                            GrokExpressionItem(
                                option = option,
                                selected = option == selectedExpression,
                                size = itemSize,
                                onSelect = ::select,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One radio-style card. The press feedback mirrors the Expo `Pressable`: 0.98 scale over 100 ms
 * on press-in, back to 1 over 130 ms on release, cubic-bezier(0.23, 1, 0.32, 1).
 */
@Composable
private fun GrokExpressionItem(
    option: GrokOrbExpression,
    selected: Boolean,
    size: Dp,
    onSelect: (GrokOrbExpression) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.98f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 100 else 130, easing = PressEasing),
        label = "pressScale",
    )
    val shape = RoundedCornerShape(CORNER_RADIUS)
    val label = "${option.label}. ${option.description}"

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(GrokOrbPalette.CardBackground)
            .border(2.dp, if (selected) option.accent else Color.Transparent, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = { onSelect(option) },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GrokExpressionPreview(
            expression = option,
            size = (size - 20.dp).coerceAtLeast(1.dp),
        )
    }
}
