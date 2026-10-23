package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/** A task keeps its selected neighbours visually joined into one light-blue run. */
@Composable
internal fun DragMultiSelectTaskRow(
    task: DragMultiSelectTask,
    index: Int,
    state: DragMultiSelectState,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val isRowSelected = state.isSelected(task.id)
    val selectedAbove = state.selectedAbove(task.id)
    val selectedBelow = state.selectedBelow(task.id)
    val rowMotion = state.rowMotion(task.id)
    val circlePaint = remember { Paint() }
    val selectedPaint = remember { Paint() }
    val cornerTop by animateDpAsState(
        targetValue = if (selectedAbove) 0.dp else DragMultiSelectLayout.bandRadius.dp,
        animationSpec = tween(DragMultiSelectMotion.timing.corner.toInt(), easing = DragMultiSelectMotion.ease.inOut),
        label = "dragMultiSelectTopCorner",
    )
    val cornerBottom by animateDpAsState(
        targetValue = if (selectedBelow) 0.dp else DragMultiSelectLayout.bandRadius.dp,
        animationSpec = tween(DragMultiSelectMotion.timing.corner.toInt(), easing = DragMultiSelectMotion.ease.inOut),
        label = "dragMultiSelectBottomCorner",
    )
    val textRight = if (state.isSelecting) DragMultiSelectLayout.circleColumn - DragMultiSelectSpace.sm else DragMultiSelectLayout.gutter

    Box(
        modifier = modifier
            .fillMaxWidth()
                .height(DragMultiSelectLayout.row.dp)
            .graphicsLayer {
                scaleX = 1f - DragMultiSelectTokens.View.Row.pressScale * rowMotion.pressScale.value
                scaleY = 1f - DragMultiSelectTokens.View.Row.pressScale * rowMotion.pressScale.value
            }
            .semantics {
                role = Role.Button
                if (state.isSelecting) {
                    selected = isRowSelected
                }
                onClick(label = if (state.isSelecting) "Toggle ${task.title}" else task.title) {
                    onToggle()
                    true
                }
            },
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = DragMultiSelectLayout.bandInset.dp)
                .fillMaxSize()
                .graphicsLayer {
                    alpha = rowMotion.pressPreview.value * DragMultiSelectTokens.View.Row.previewAlpha
                    scaleX = DragMultiSelectTokens.View.Row.previewStartScale +
                        DragMultiSelectTokens.View.Row.previewEndScale * rowMotion.pressPreview.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(DragMultiSelectLayout.bandRadius.dp))
                .background(DragMultiSelectColors.band),
        )
        Canvas(
            modifier = Modifier
                .padding(horizontal = DragMultiSelectLayout.bandInset.dp)
                .fillMaxSize()
                .graphicsLayer {
                    val band = rowMotion.band.value
                    val scale = if (reduceMotion) {
                        1f
                    } else {
                        DragMultiSelectTokens.View.Row.bandStartScale +
                            DragMultiSelectTokens.View.Row.bandScaleRange *
                            band.coerceIn(0f, DragMultiSelectTokens.View.Row.bandOvershootLimit)
                    }
                    alpha = if (reduceMotion) {
                        band
                    } else {
                        (band * DragMultiSelectTokens.View.Row.bandOpacityMultiplier).coerceIn(0f, 1f)
                    }
                    scaleY = scale
                    translationY = rowMotion.bandOrigin.value * -(1f - scale) * DragMultiSelectLayout.row.dp.toPx() / 2f
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            // Corner animation is observed in drawing; no whole-row recomposition each frame.
            val outline = Path().apply {
                addRoundRect(RoundRect(
                    rect = Rect(Offset.Zero, size),
                    topLeft = CornerRadius(cornerTop.toPx()),
                    topRight = CornerRadius(cornerTop.toPx()),
                    bottomLeft = CornerRadius(cornerBottom.toPx()),
                    bottomRight = CornerRadius(cornerBottom.toPx()),
                ))
            }
            clipPath(outline) {
                drawRect(DragMultiSelectColors.band)
                if (selectedAbove) {
                    drawRect(
                        DragMultiSelectColors.bandSeam,
                        size = Size(size.width, DragMultiSelectTokens.View.Row.seamHeight.dp.toPx()),
                    )
                }
                val gloss = rowMotion.gloss.value
                if (gloss < 1f) {
                    val width = DragMultiSelectTokens.View.Row.glossWidth.dp.toPx()
                    val left = -width +
                        (DragMultiSelectTokens.View.Row.glossTravelWidth.dp.toPx() + width) * gloss
                    // Skew the stripe and its gradient together about the stripe's centre.
                    drawContext.canvas.save()
                    drawContext.canvas.translate(left + width / 2f, size.height / 2f)
                    drawContext.canvas.skew(
                        tan(DragMultiSelectTokens.View.Row.glossSkewDegrees * PI.toFloat() / 180f),
                        0f,
                    )
                    drawContext.canvas.translate(-width / 2f, -size.height / 2f)
                    drawRect(
                        brush = Brush.horizontalGradient(DragMultiSelectColors.gloss, 0f, width),
                        size = Size(width, size.height),
                        alpha = sin(PI.toFloat() * gloss),
                    )
                    drawContext.canvas.restore()
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = DragMultiSelectLayout.gutter.dp, end = textRight.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(DragMultiSelectLayout.check.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = DragMultiSelectColors.check,
                            topLeft = Offset(
                                (DragMultiSelectTokens.View.Row.checkStroke / 2f).dp.toPx(),
                                (DragMultiSelectTokens.View.Row.checkStroke / 2f).dp.toPx(),
                            ),
                            size = Size(
                                size.width - DragMultiSelectTokens.View.Row.checkStroke.dp.toPx(),
                                size.height - DragMultiSelectTokens.View.Row.checkStroke.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                (
                                    DragMultiSelectLayout.checkRadius -
                                        DragMultiSelectTokens.View.Row.checkStroke / 2f
                                    ).dp.toPx(),
                            ),
                            style = Stroke(
                                width = DragMultiSelectTokens.View.Row.checkStroke.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(
                                        DragMultiSelectTokens.View.Row.checkDash.dp.toPx(),
                                        DragMultiSelectTokens.View.Row.checkDash.dp.toPx(),
                                    ),
                                ),
                            ),
                        )
                    },
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = (DragMultiSelectLayout.textLeft - DragMultiSelectLayout.gutter - DragMultiSelectLayout.check).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = task.title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = DragMultiSelectColors.ink,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = DragMultiSelectType.row.fontSize.sp,
                        lineHeight = DragMultiSelectType.row.lineHeight.sp,
                        fontWeight = FontWeight(DragMultiSelectType.row.fontWeight),
                    ),
                )
                if (task.note) {
                    Canvas(
                        modifier = Modifier
                            .padding(start = DragMultiSelectTokens.View.Row.noteGap.dp)
                            .size(DragMultiSelectTokens.View.Row.noteIcon.dp),
                    ) {
                        dragMultiSelectIcon(
                            DragMultiSelectIcon.FileText,
                            DragMultiSelectColors.note,
                            DragMultiSelectTokens.View.iconStroke,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = (DragMultiSelectLayout.circleRight + DragMultiSelectLayout.bandInset).dp)
                .size(DragMultiSelectLayout.circle.dp)
                .graphicsLayer {
                    translationX = if (reduceMotion) 0f else (1f - rowMotion.circleIn.value) * DragMultiSelectMotion.timing.circleSlide.dp.toPx()
                }
                .drawBehind {
                    // An expanded group layer preserves the -1.5 offsets and the escaping ripple
                    // while fading the complete circle group, without clipping to its 26 dp box.
                    val overflow = DragMultiSelectTokens.View.Row.circleOverflow.dp.toPx()
                    val bounds = Rect(-overflow, -overflow, size.width + overflow, size.height + overflow)
                    circlePaint.alpha = rowMotion.circleIn.value.coerceIn(0f, 1f)
                    drawContext.canvas.saveLayer(bounds, circlePaint)
                    drawCircle(DragMultiSelectColors.white, size.minDimension / 2f)
                    drawCircle(
                        color = DragMultiSelectColors.circle,
                        radius = size.minDimension / 2f -
                            (DragMultiSelectTokens.View.Row.circleStroke / 2f).dp.toPx(),
                        style = Stroke(DragMultiSelectTokens.View.Row.circleStroke.dp.toPx()),
                    )
                    val ring = rowMotion.ring.value
                    if (ring < 1f) {
                        val rippleScale = 1f + DragMultiSelectTokens.View.Row.rippleScaleRange * ring
                        drawCircle(
                            color = DragMultiSelectColors.blue.copy(
                                alpha = DragMultiSelectTokens.View.Row.rippleStartAlpha * (1f - ring),
                            ),
                            radius = (
                                size.minDimension / 2f +
                                    (DragMultiSelectTokens.View.Row.selectedCircleOutset - 1f).dp.toPx()
                                ) * rippleScale,
                            style = Stroke(
                                DragMultiSelectTokens.View.Row.rippleStroke.dp.toPx() * rippleScale,
                            ),
                        )
                    }
                    val band = rowMotion.band.value
                    val checkedScale = if (reduceMotion) {
                        1f
                    } else {
                        DragMultiSelectTokens.View.Row.selectedStartScale +
                            DragMultiSelectTokens.View.Row.selectedScaleRange * band.coerceAtMost(1f)
                    }
                    selectedPaint.alpha = band.coerceIn(0f, 1f)
                    drawContext.canvas.saveLayer(bounds, selectedPaint)
                    scale(checkedScale) {
                        // RN's negative 1.5 dp insets yield a 29 dp box centred on the 26 dp base.
                        drawRoundRect(
                            color = DragMultiSelectColors.blue,
                            topLeft = Offset(
                                -DragMultiSelectTokens.View.Row.selectedCircleOutset.dp.toPx(),
                                -DragMultiSelectTokens.View.Row.selectedCircleOutset.dp.toPx(),
                            ),
                            size = Size(
                                size.width + 2f * DragMultiSelectTokens.View.Row.selectedCircleOutset.dp.toPx(),
                                size.height + 2f * DragMultiSelectTokens.View.Row.selectedCircleOutset.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius((DragMultiSelectLayout.circle / 2f).dp.toPx()),
                        )
                        // Inner ring: 4 dp inset and an inside 2 dp border (stroke centre +1 dp).
                        drawCircle(
                            color = DragMultiSelectColors.white,
                            radius = (
                                DragMultiSelectLayout.circle / 2f +
                                    DragMultiSelectTokens.View.Row.selectedCircleOutset -
                                    DragMultiSelectTokens.View.Row.selectedInnerInset -
                                    DragMultiSelectTokens.View.Row.selectedInnerStroke / 2f
                                ).dp.toPx(),
                            style = Stroke(DragMultiSelectTokens.View.Row.selectedInnerStroke.dp.toPx()),
                        )
                    }
                    drawContext.canvas.restore()
                    drawContext.canvas.restore()
                },
        )
    }
}
