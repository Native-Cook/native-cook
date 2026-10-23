package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.buildDialTickValues
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.dialTranslationToValue
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.dialValueToTranslation
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getDialTickOpacity
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getDialTranslationBounds
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getNearestDialTick
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.jsRound
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.snapDialTranslation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RENDERED_TICKS = 201
private const val EDGE_MASK_WIDTH_DP = 80f
internal val RULER_HEIGHT_DP = (DialConfig.CENTER_TICK_HEIGHT + 24).dp

/**
 * Reanimated's `withDecay` integrates `v * exp(-(1 - deceleration) * dt)` with
 * `dt` in milliseconds. Compose's exponential decay uses `exp(-4.2 * k * t)`
 * with `t` in seconds, so the friction multiplier is derived from the shared
 * deceleration constant.
 */
private val FLING_FRICTION_MULTIPLIER: Float =
    (-ln(DialConfig.FLING_DECELERATION) * 1000.0 / 4.2).toFloat()

/**
 * Reanimated spring (damping 34, stiffness 280, mass 0.75, overshoot clamped)
 * expressed as Compose's unit-mass damping ratio and stiffness.
 */
private val SNAP_DAMPING_RATIO: Float = (34.0 / (2 * sqrt(280.0 * 0.75))).toFloat()
private val SNAP_STIFFNESS: Float = (280.0 / 0.75).toFloat()

private data class RulerTick(val value: Double, val isMajor: Boolean)

/**
 * Horizontal ruler driven by a pan gesture with fling, edge clamping and
 * integer snapping.
 *
 * @param value Committed dial value owned by the parent. The ruler animates to
 * it whenever it differs from the ruler position outside of a gesture.
 * @param interactionRevision Invalidates in-flight gesture callbacks when the
 * owning preset changes.
 * @param onValueChange Reports each crossed integer value, tagged with the
 * revision captured when the gesture began.
 */
@Composable
internal fun DialRuler(
    value: Double,
    minValue: Double,
    maxValue: Double,
    enabled: Boolean,
    interactionRevision: Int,
    fadeColor: Color,
    onValueChange: (value: Double, callbackRevision: Int) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spacing = DialConfig.TICK_SPACING
    val bounds = getDialTranslationBounds(minValue, maxValue, spacing)
    val tickValues = remember(minValue, maxValue) {
        buildDialTickValues(minValue, maxValue, DialConfig.TICK_STEP, MAX_RENDERED_TICKS)
    }
    val ticks = remember(tickValues) {
        tickValues.map { RulerTick(it, it % DialConfig.MAJOR_TICK_EVERY == 0.0) }
    }

    val translationX = remember {
        Animatable(dialValueToTranslation(value, minValue, maxValue, spacing).toFloat())
    }
    var startX by remember { mutableFloatStateOf(translationX.value) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val isDragging = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    var gestureActive by remember { mutableStateOf(false) }
    var flingActive by remember { mutableStateOf(false) }
    var gestureRevision by remember { mutableIntStateOf(interactionRevision) }
    var programmaticMove by remember { mutableStateOf(false) }
    var draggingJob by remember { mutableStateOf<Job?>(null) }

    val latestValue by rememberUpdatedState(value)
    val latestRevision by rememberUpdatedState(interactionRevision)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnInteractionStart by rememberUpdatedState(onInteractionStart)
    val latestOnInteractionEnd by rememberUpdatedState(onInteractionEnd)

    LaunchedEffect(bounds) {
        translationX.updateBounds(
            bounds.minTranslation.toFloat(),
            bounds.maxTranslation.toFloat(),
        )
    }

    val currentRounded by remember(minValue, maxValue) {
        derivedStateOf {
            jsRound(dialTranslationToValue(translationX.value.toDouble(), minValue, maxValue, spacing))
        }
    }

    // Gesture/decay driven values flow up to the parent (RN: value reaction).
    LaunchedEffect(minValue, maxValue) {
        snapshotFlow { currentRounded }.collect { current ->
            if (programmaticMove) return@collect
            if (gestureRevision != latestRevision) return@collect
            if (current != jsRound(latestValue)) {
                latestOnValueChange(current, gestureRevision)
            }
        }
    }

    // Parent driven values flow down into the ruler (RN: programmatic move reaction).
    var lastSeenRevision by remember { mutableIntStateOf(interactionRevision) }
    LaunchedEffect(value, interactionRevision, gestureActive, flingActive, minValue, maxValue) {
        if (gestureActive || flingActive) return@LaunchedEffect
        val revisionChanged = interactionRevision != lastSeenRevision
        lastSeenRevision = interactionRevision
        if (revisionChanged) translationX.stop()

        val fromValue = jsRound(
            dialTranslationToValue(translationX.value.toDouble(), minValue, maxValue, spacing),
        )
        val targetValue = jsRound(value)
        if (fromValue == targetValue) {
            if (revisionChanged) programmaticMove = false
            return@LaunchedEffect
        }

        val target = dialValueToTranslation(targetValue, minValue, maxValue, spacing)
        translationX.stop()
        programmaticMove = true
        try {
            translationX.animateTo(target.toFloat(), tween(durationMillis = 120))
        } finally {
            programmaticMove = false
        }
    }

    // Brief dip of the whole ruler when the owning preset changes.
    var revisionFlashPrimed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionRevision) {
        if (!revisionFlashPrimed) {
            revisionFlashPrimed = true
            return@LaunchedEffect
        }
        contentAlpha.stop()
        contentAlpha.animateTo(0.28f, tween(durationMillis = 160))
        contentAlpha.animateTo(1f, tween(durationMillis = 380))
    }

    // Nearest tick grows to the center height (RN: per-tick height reaction).
    val nearestTick by remember(tickValues) {
        derivedStateOf { getNearestDialTick(-translationX.value / spacing, tickValues) }
    }
    var lastNearest by remember { mutableStateOf(nearestTick) }
    var previousNearest by remember { mutableStateOf<Double?>(null) }
    val nearestProgress = remember { Animatable(1f) }
    LaunchedEffect(nearestTick) {
        if (nearestTick == lastNearest) return@LaunchedEffect
        previousNearest = lastNearest
        lastNearest = nearestTick
        nearestProgress.snapTo(0f)
        nearestProgress.animateTo(1f, tween(durationMillis = 150))
        previousNearest = null
    }

    // Ticks flash brighter when the value crosses them (RN: per-tick color reaction).
    val flashes = remember { mutableStateMapOf<Double, Animatable<Float, AnimationVector1D>>() }
    LaunchedEffect(tickValues) {
        var previous: Double? = null
        snapshotFlow { jsRound(-translationX.value / spacing) }.collect { current ->
            val prior = previous
            previous = current
            if (prior == null) return@collect
            for (tick in tickValues) {
                val crossed = (prior < tick && current >= tick) || (prior > tick && current <= tick)
                if (!crossed) continue
                launch {
                    val flash = flashes.getOrPut(tick) { Animatable(0f) }
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(durationMillis = 300))
                }
            }
        }
    }

    val draggableState = rememberDraggableState { deltaPx ->
        dragTotal += deltaPx / density.density
        val next = (startX + dragTotal).toDouble()
        val clamped = max(bounds.minTranslation, min(bounds.maxTranslation, next)).toFloat()
        scope.launch { translationX.snapTo(clamped) }
        draggingJob?.cancel()
        draggingJob = scope.launch {
            isDragging.snapTo(1f)
            delay(100)
            isDragging.animateTo(0f, tween(durationMillis = 200))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT_DP)
            .clipToBounds()
            .graphicsLayer { alpha = contentAlpha.value }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = {
                    translationX.stop()
                    programmaticMove = false
                    gestureActive = true
                    flingActive = false
                    gestureRevision = latestRevision
                    startX = translationX.value
                    dragTotal = 0f
                    latestOnInteractionStart()
                },
                onDragStopped = { velocityPx ->
                    gestureActive = false
                    draggingJob?.cancel()
                    draggingJob = scope.launch { isDragging.animateTo(0f, tween(durationMillis = 200)) }
                    latestOnInteractionEnd()

                    flingActive = true
                    try {
                        translationX.animateDecay(
                            initialVelocity = velocityPx / density.density,
                            animationSpec = exponentialDecay(frictionMultiplier = FLING_FRICTION_MULTIPLIER),
                        )
                        val targetX = snapDialTranslation(
                            translationX.value.toDouble(),
                            minValue,
                            maxValue,
                            spacing,
                        )
                        translationX.animateTo(
                            targetX.toFloat(),
                            spring(dampingRatio = SNAP_DAMPING_RATIO, stiffness = SNAP_STIFFNESS),
                        )
                    } finally {
                        flingActive = false
                    }
                },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = density.density
            val widthDp = size.width / px
            val screenCenter = widthDp / 2.0
            val centerX = size.width / 2f
            val baseY = size.height
            val tx = translationX.value.toDouble()
            val currentVal = -tx / spacing
            val startVal = -startX / spacing
            val dragging = isDragging.value.toDouble()
            val nearest = getNearestDialTick(currentVal, tickValues)
            val progress = nearestProgress.value.toDouble()
            val prior = previousNearest
            val tickWidthPx = (DialConfig.TICK_WIDTH * px).toFloat()
            val lo = min(startVal, currentVal)
            val hi = max(startVal, currentVal)
            val rangeSize = hi - lo

            for (tick in ticks) {
                val x = tick.value * spacing + tx
                val opacity = getDialTickOpacity(x, screenCenter)
                if (opacity <= 0.0) continue

                val inRange = tick.value >= lo && tick.value <= hi && rangeSize > 0
                val distFromCurrent = abs(currentVal - tick.value)
                val rawDist = if (rangeSize > 0) min(distFromCurrent / rangeSize, 1.0) else 1.0
                val normalizedDist = sqrt(rawDist)
                val waveFactor = if (inRange) ((cos(normalizedDist * PI) + 1) / 2) * dragging else 0.0
                val waveHeight = DialConfig.TICK_HEIGHT +
                    (DialConfig.CENTER_TICK_HEIGHT - DialConfig.TICK_HEIGHT) * waveFactor
                val settledHeight = when (tick.value) {
                    nearest -> DialConfig.TICK_HEIGHT +
                        (DialConfig.CENTER_TICK_HEIGHT - DialConfig.TICK_HEIGHT) * progress
                    prior -> DialConfig.CENTER_TICK_HEIGHT -
                        (DialConfig.CENTER_TICK_HEIGHT - DialConfig.TICK_HEIGHT) * progress
                    else -> DialConfig.TICK_HEIGHT
                }
                val height = max(settledHeight, waveHeight)
                val t = if (tick.isMajor) 1f else flashes[tick.value]?.value ?: 0f
                val color = if (tick.value == nearest) {
                    DialColors.Positive
                } else {
                    lerp(DialColors.MinorTick, DialColors.MajorTick, t)
                }
                val heightPx = (height * px).toFloat()
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * opacity.toFloat()),
                    topLeft = Offset(centerX + (x * px).toFloat() - tickWidthPx / 2, baseY - heightPx),
                    size = Size(tickWidthPx, heightPx),
                    cornerRadius = CornerRadius(px, px),
                )
            }

            val dotOpacity = if (abs(tx) < 2) 0.0 else max(0.0, 1 - abs(tx) / 160)
            if (dotOpacity > 0) {
                drawCircle(
                    color = DialColors.OriginDot.copy(alpha = dotOpacity.toFloat()),
                    radius = 3f * px,
                    center = Offset(
                        centerX + (tx * px).toFloat(),
                        baseY - ((DialConfig.TICK_HEIGHT + 6 + 3) * px).toFloat(),
                    ),
                )
            }

            val maskWidth = EDGE_MASK_WIDTH_DP * px
            val transparent = fadeColor.copy(alpha = 0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(fadeColor, transparent),
                    startX = 0f,
                    endX = maskWidth,
                ),
                size = Size(maskWidth, size.height),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(transparent, fadeColor),
                    startX = size.width - maskWidth,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - maskWidth, 0f),
                size = Size(maskWidth, size.height),
            )
        }
    }
}
