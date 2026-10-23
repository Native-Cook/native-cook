package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Only the shell resizes. Its fixed-size children stay pinned to the bottom-right corner. */
@Composable
internal fun DragMultiSelectToolbar(
    state: DragMultiSelectState,
    selectedCount: Int,
    bottomInset: Dp,
    reduceMotion: Boolean,
    onWhen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabPress = remember { Animatable(0f) }
    LaunchedEffect(fabPressed) {
        fabPress.animateTo(
            targetValue = if (fabPressed) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (fabPressed) {
                    DragMultiSelectMotion.timing.fabPressIn.toInt()
                } else {
                    DragMultiSelectMotion.timing.fabPressOut.toInt()
                },
                easing = DragMultiSelectMotion.ease.out,
            ),
        )
    }
    fun progress(): Float {
        val p = state.shown.value.coerceIn(0f, 1.08f)
        return if (reduceMotion) if (p > 0.5f) 1f else 0f else p
    }
    fun breath(): Float {
        return if (reduceMotion) 0f else state.breath.value * (1f - state.shown.value.coerceIn(0f, 1f))
    }
    fun radius(): Float {
        return lerp(DragMultiSelectLayout.fab / 2f, DragMultiSelectLayout.toolbar.radius, progress().coerceAtMost(1f))
    }

    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Layout(
                modifier = Modifier
                    .graphicsLayer {
                        val scale = 1f + DragMultiSelectTokens.View.Toolbar.breathScale * breath()
                        scaleX = scale * (1f - DragMultiSelectTokens.View.Toolbar.pressScale * fabPress.value)
                        scaleY = scale * (1f - DragMultiSelectTokens.View.Toolbar.pressScale * fabPress.value)
                    }
                    // Shadow is drawn outside the clipping layer: radius/opacity/offset match RN.
                    .drawBehind {
                        val b = breath()
                        val blur = (
                            DragMultiSelectTokens.View.Toolbar.shadowRadius +
                                DragMultiSelectTokens.View.Toolbar.shadowBreathRadius * b
                            ).dp.toPx()
                        val opacity = DragMultiSelectTokens.View.Toolbar.shadowOpacity +
                            DragMultiSelectTokens.View.Toolbar.shadowBreathOpacity * b
                        val radius = radius().dp.toPx()
                        if (Build.VERSION.SDK_INT >= 28) {
                            shadowPaint.color = DragMultiSelectColors.black.toArgb()
                            shadowPaint.setShadowLayer(
                                blur,
                                0f,
                                DragMultiSelectTokens.View.Toolbar.shadowOffsetY.dp.toPx(),
                                DragMultiSelectColors.black.copy(alpha = opacity).toArgb(),
                            )
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, shadowPaint)
                            }
                        } else {
                            // API 26–27 cannot hardware-blur non-text shadows. Nested translucent
                            // contours approximate the same radius, offset and total opacity.
                            val layers = DragMultiSelectTokens.View.Toolbar.api26ShadowLayers
                            val layerAlpha = 1f - (1f - opacity).pow(1f / layers)
                            for (layer in layers downTo 1) {
                                val spread = blur * layer / layers
                                drawRoundRect(
                                    color = DragMultiSelectColors.black.copy(alpha = layerAlpha),
                                    topLeft = Offset(
                                        -spread,
                                        DragMultiSelectTokens.View.Toolbar.shadowOffsetY.dp.toPx() - spread,
                                    ),
                                    size = Size(size.width + 2f * spread, size.height + 2f * spread),
                                    cornerRadius = CornerRadius(radius + spread),
                                )
                            }
                        }
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(radius().dp)
                        clip = true
                    }
                    .drawBehind {
                        drawRect(
                            toolbarColor(
                                (progress() / DragMultiSelectTokens.View.Toolbar.colorEnd)
                                    .coerceIn(0f, 1f),
                            ),
                        )
                    },
                content = {
                    Box(
                        modifier = Modifier
                            .size(DragMultiSelectLayout.fab.dp)
                            .then(if (state.isSelecting) Modifier.clearAndSetSemantics {} else Modifier.semantics {
                                role = Role.Button
                                contentDescription = "New to-do"
                            })
                            .clickable(
                                interactionSource = fabInteraction,
                                indication = null,
                                enabled = !state.isSelecting,
                                onClick = {},
                            )
                            .graphicsLayer {
                                alpha = 1f - (
                                    state.shown.value *
                                        DragMultiSelectTokens.View.Toolbar.plusFadeMultiplier
                                    ).coerceIn(0f, 1f)
                                rotationZ = DragMultiSelectTokens.View.Toolbar.plusRotation *
                                    state.shown.value.coerceAtMost(1f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(DragMultiSelectTokens.View.Toolbar.plusIcon.dp)) {
                            dragMultiSelectIcon(
                                DragMultiSelectIcon.Plus,
                                DragMultiSelectColors.white,
                                DragMultiSelectTokens.View.iconStroke,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(DragMultiSelectLayout.toolbar.width.dp, DragMultiSelectLayout.toolbar.height.dp)
                            .then(if (!state.isSelecting) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        DragMultiSelectTool(state, 0, DragMultiSelectIcon.Calendar, "When", selectedCount == 0, onWhen)
                        DragMultiSelectTool(state, 1, DragMultiSelectIcon.ArrowRight, "Move", selectedCount == 0, onMove)
                        DragMultiSelectTool(state, 2, DragMultiSelectIcon.Trash, null, selectedCount == 0, onDelete, "Delete $selectedCount")
                        DragMultiSelectTool(state, 3, DragMultiSelectIcon.More, null, selectedCount == 0, onMove, "More actions")
                    }
                },
            ) { measurables, constraints ->
                val fab = DragMultiSelectLayout.fab.dp.roundToPx()
                val plus = measurables[0].measure(Constraints.fixed(fab, fab))
                val tools = measurables[1].measure(Constraints.fixed(
                    DragMultiSelectLayout.toolbar.width.dp.roundToPx(),
                    DragMultiSelectLayout.toolbar.height.dp.roundToPx(),
                ))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    plus.place(constraints.maxWidth - plus.width, constraints.maxHeight - plus.height)
                    tools.place(constraints.maxWidth - tools.width, constraints.maxHeight - tools.height)
                }
            }
        },
    ) { measurables, constraints ->
        // Read animated dimensions during measurement, never in composition.
        val q = progress()
        val width = lerp(DragMultiSelectLayout.fab, DragMultiSelectLayout.toolbar.width, q).dp.roundToPx()
        val height = lerp(DragMultiSelectLayout.fab, DragMultiSelectLayout.toolbar.height, q).dp.roundToPx()
        val shell = measurables.single().measure(Constraints.fixed(width, height))
        val fabLeft = constraints.maxWidth - (DragMultiSelectLayout.gutter + DragMultiSelectLayout.fab).dp.toPx()
        val barLeft = (constraints.maxWidth - DragMultiSelectLayout.toolbar.width.dp.toPx()) / 2f
        val left = lerp(fabLeft, barLeft, q)
        val bottom = bottomInset.toPx() + lerp(DragMultiSelectLayout.fabBottom, DragMultiSelectLayout.toolbar.bottom, q).dp.toPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            shell.place(left.roundToInt(), (constraints.maxHeight - bottom - height).roundToInt())
        }
    }
}

@Composable
private fun DragMultiSelectTool(
    state: DragMultiSelectState,
    index: Int,
    icon: DragMultiSelectIcon,
    label: String?,
    isDisabled: Boolean,
    onClick: () -> Unit,
    description: String = label.orEmpty(),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .offset(
                x = (
                    DragMultiSelectLayout.tools[index] -
                        DragMultiSelectTokens.View.Toolbar.toolSlot / 2f
                    ).dp,
            )
            .width(DragMultiSelectTokens.View.Toolbar.toolSlot.dp)
            .height(DragMultiSelectLayout.toolbar.height.dp)
            .graphicsLayer {
                val progress = toolProgress(state.shown.value, index)
                val kick = if (index == 2) state.trashKick.value else 0f
                alpha = progress
                translationY = (
                    (1f - progress) * DragMultiSelectTokens.View.Toolbar.toolRise -
                        DragMultiSelectTokens.View.Toolbar.trashRise * kick
                    ).dp.toPx()
                scaleX = 1f + DragMultiSelectTokens.View.Toolbar.trashScale * kick
                scaleY = 1f + DragMultiSelectTokens.View.Toolbar.trashScale * kick
                rotationZ = DragMultiSelectTokens.View.Toolbar.trashRotation * kick
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(DragMultiSelectTokens.View.Toolbar.toolHeight.dp)
                .widthIn(min = DragMultiSelectTokens.View.Toolbar.toolMinimumWidth.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = state.isSelecting && !isDisabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = description
                    if (isDisabled) disabled()
                }
                .graphicsLayer {
                    alpha = if (isDisabled || pressed) {
                        DragMultiSelectTokens.View.Toolbar.disabledAlpha
                    } else {
                        1f
                    }
                }
                .padding(horizontal = DragMultiSelectTokens.View.Toolbar.toolHorizontalPadding.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Canvas(Modifier.size(DragMultiSelectLayout.toolIcon.dp)) {
                dragMultiSelectIcon(
                    icon,
                    DragMultiSelectColors.toolbarInk,
                    DragMultiSelectTokens.View.iconStroke,
                )
            }
            if (label != null) {
                BasicText(
                    text = label,
                    modifier = Modifier.padding(start = DragMultiSelectTokens.View.Toolbar.toolGap.dp),
                    style = TextStyle(
                        color = DragMultiSelectColors.toolbarInk,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = DragMultiSelectType.tool.fontSize.sp,
                        lineHeight = DragMultiSelectType.tool.lineHeight.sp,
                        fontWeight = FontWeight(DragMultiSelectType.tool.fontWeight),
                    ),
                )
            }
        }
    }
}

private fun toolProgress(shown: Float, index: Int): Float {
    val start = DragMultiSelectTokens.View.Toolbar.toolStart +
        index * DragMultiSelectMotion.timing.toolStagger
    return (
        (shown - start) / DragMultiSelectTokens.View.Toolbar.toolDuration
        ).coerceIn(0f, 1f)
}

/** Reanimated interpolateColor defaults to RGB with gamma 2.2, not Compose's Oklab lerp. */
private fun toolbarColor(progress: Float): Color {
    val from = DragMultiSelectColors.blue
    val to = DragMultiSelectColors.toolbar
    val gamma = DragMultiSelectTokens.View.Toolbar.colorGamma
    fun channel(a: Float, b: Float): Float = lerp(a.pow(gamma), b.pow(gamma), progress).pow(1f / gamma)
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
}

private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
