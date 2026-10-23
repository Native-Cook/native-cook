package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class PitStopReviewTab { Home, Calendar, Results, Circuits, Customize }

private object PitStopReviewLens {
    const val duration = 420
    const val dampingRatio = 0.72f
    const val quietDuration = 160
    const val lift = 1.12f
    const val liftIn = 160
    const val liftOut = 260
    const val stretch = 0.14f
}

@Composable
internal fun PitStopReviewDock(
    active: PitStopReviewTab,
    scale: Float,
    disabled: Boolean,
    quiet: Boolean,
    onSelect: (PitStopReviewTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = PitStopReviewTheme.TabBar
    val width = theme.width * scale
    val height = theme.height * scale
    val padding = theme.padding * scale
    val cell = (width - 2f * padding) / PitStopReviewTab.entries.size
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val phase = remember { Animatable(1f) }
    val lift = remember { Animatable(1f) }
    var from by remember { mutableFloatStateOf(active.ordinal * cell) }
    var target by remember { mutableFloatStateOf(active.ordinal * cell) }
    var dragX by remember { mutableFloatStateOf(target) }
    var dragging by remember { mutableStateOf(false) }
    var releasedFromDrag by remember { mutableStateOf(false) }

    fun settledX(): Float {
        if (dragging) return dragX
        val amount = if (quiet) phase.value else pitStopReviewLensSpring(phase.value)
        return from + (target - from) * amount
    }

    LaunchedEffect(active, cell, quiet) {
        if (dragging) return@LaunchedEffect
        if (releasedFromDrag) {
            releasedFromDrag = false
        } else {
            from = settledX()
        }
        target = active.ordinal * cell
        phase.snapTo(0f)
        phase.animateTo(
            1f,
            tween(
                durationMillis = if (quiet) PitStopReviewLens.quietDuration else PitStopReviewLens.duration,
                easing = LinearEasing,
            ),
        )
    }

    val dragModifier = if (disabled) {
        Modifier
    } else {
        Modifier.pointerInput(cell, padding, quiet) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragging = true
                    dragX = (offset.x - padding - cell / 2f).coerceIn(0f, cell * 4f)
                    scope.launch {
                        phase.stop()
                        if (!quiet) {
                            lift.animateTo(
                                PitStopReviewLens.lift,
                                tween(PitStopReviewLens.liftIn, easing = LinearEasing),
                            )
                        }
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    dragX = (change.position.x - padding - cell / 2f).coerceIn(0f, cell * 4f)
                },
                onDragEnd = {
                    val index = (dragX / cell).roundToInt().coerceIn(0, 4)
                    from = dragX
                    target = index * cell
                    releasedFromDrag = true
                    dragging = false
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelect(PitStopReviewTab.entries[index])
                    scope.launch {
                        phase.snapTo(0f)
                        phase.animateTo(
                            1f,
                            tween(PitStopReviewLens.duration, easing = LinearEasing),
                        )
                        lift.animateTo(
                            1f,
                            tween(PitStopReviewLens.liftOut, easing = LinearEasing),
                        )
                    }
                },
                onDragCancel = {
                    from = dragX
                    target = active.ordinal * cell
                    dragging = false
                    scope.launch {
                        phase.snapTo(0f)
                        phase.animateTo(
                            1f,
                            tween(PitStopReviewLens.duration, easing = LinearEasing),
                        )
                        lift.animateTo(
                            1f,
                            tween(PitStopReviewLens.liftOut, easing = LinearEasing),
                        )
                    }
                },
            )
        }
    }

    Box(
        modifier
            .requiredSize(width.dp, height.dp)
            .then(dragModifier)
            .clip(CircleShape)
            .background(Color(0xAD0F0D10))
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape),
    ) {
        Box(
            Modifier
                .requiredSize((theme.lensWidth * scale).dp, (theme.lensHeight * scale).dp)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    val x = settledX()
                    val travel = if (quiet) {
                        0f
                    } else {
                        (2f * abs(x / cell - (x / cell).roundToInt())).coerceAtMost(1f)
                    }
                    val size = if (quiet) 1f else lift.value
                    translationX = (padding + (cell - theme.lensWidth * scale) / 2f).dp.toPx() + x.dp.toPx()
                    scaleX = size * (1f + PitStopReviewLens.stretch * travel)
                    scaleY = size * (1f - PitStopReviewLens.stretch * 0.4f * travel)
                    transformOrigin = TransformOrigin.Center
                }
                .clip(CircleShape)
                .background(theme.lensFill)
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.10f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = padding.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PitStopReviewTab.entries.forEach { tab ->
                val icon = when (tab) {
                    PitStopReviewTab.Home -> Icons.Default.Home
                    PitStopReviewTab.Calendar -> Icons.Default.CalendarMonth
                    PitStopReviewTab.Results -> Icons.Default.EmojiEvents
                    PitStopReviewTab.Circuits -> Icons.Default.Language
                    PitStopReviewTab.Customize -> Icons.Default.GridView
                }
                Box(
                    Modifier
                        .width(cell.dp)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = tab.name
                            role = Role.Tab
                            selected = tab == active
                        }
                        .clickable(enabled = !disabled, role = Role.Tab) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(tab)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.requiredSize((theme.icon * scale).dp),
                        tint = theme.iconColor,
                    )
                }
            }
        }
    }
}

private fun pitStopReviewLensSpring(progress: Float): Float {
    if (progress <= 0f) return 0f
    if (progress >= 1f) return 1f
    val damping = PitStopReviewLens.dampingRatio.toDouble()
    val omega = 10.0
    val damped = omega * sqrt(1.0 - damping * damping)
    val time = progress.toDouble()
    val response = 1.0 - exp(-damping * omega * time) *
        (cos(damped * time) + damping / sqrt(1.0 - damping * damping) * sin(damped * time))
    return response.toFloat()
}
