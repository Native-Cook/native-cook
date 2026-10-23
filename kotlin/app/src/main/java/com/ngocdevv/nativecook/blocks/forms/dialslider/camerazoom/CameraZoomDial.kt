package com.ngocdevv.nativecook.blocks.forms.dialslider.camerazoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.motionDuration
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val CENTER_LABEL_EXCLUSION_DEGREES = 8.0
private const val EXPANSION_MS = 170
private const val PROP_SYNC_MS = 180

/** Reanimated `withSpring({ duration: 400, dampingRatio: 1 })` as a critically damped Compose spring. */
private const val SETTLE_STIFFNESS = 300f

private data class WheelPoint(val x: Float, val y: Float)

private fun polarPoint(center: Float, radius: Float, angleDegrees: Double): WheelPoint {
    val radians = angleDegrees * PI / 180
    return WheelPoint(
        x = center + (sin(radians) * radius).toFloat(),
        y = center - (cos(radians) * radius).toFloat(),
    )
}

private fun defaultZoomStops(min: Double, max: Double): List<CameraZoomStop> {
    val preferred = listOf(0.5, 1.0, 2.0).filter { it >= min && it <= max }
    return (if (preferred.isNotEmpty()) preferred else listOf(min)).map { CameraZoomStop(it) }
}

/** Adapts the shared rounded-triangle routine to a Compose [Path]. */
private class ComposePathSink(private val path: Path) : CameraZoomPathSink {
    override fun moveTo(x: Double, y: Double) = path.moveTo(x.toFloat(), y.toFloat())
    override fun lineTo(x: Double, y: Double) = path.lineTo(x.toFloat(), y.toFloat())
    override fun quadraticTo(controlX: Double, controlY: Double, x: Double, y: Double) =
        path.quadraticTo(controlX.toFloat(), controlY.toFloat(), x.toFloat(), y.toFloat())

    override fun close() = path.close()
}

private fun roundedTrianglePath(
    centerX: Double,
    topY: Double,
    halfWidth: Double,
    apexY: Double,
    cornerRadius: Double,
): Path = Path().also { path ->
    buildCameraZoomRoundedTriangle(centerX, topY, halfWidth, apexY, cornerRadius, ComposePathSink(path))
}

/** Mutable values read by gesture and coroutine callbacks without recomposition. */
private class CameraZoomRefs(
    var expanded: Boolean,
    var expansionIsControlled: Boolean,
    var controlledExpanded: Boolean?,
    /** Last value the step reaction forwarded (RN: `lastReportedZoom` shared value). */
    var lastReportedZoom: Double,
) {
    /** Last value emitted through `onZoomChange` (RN: `lastReportedZoomRef`). */
    var lastEmittedZoom: Double = lastReportedZoom
    var interactionActive: Boolean = false
    var currentLogZoom: Double = 0.0
    var startZoom: Double = 1.0
}

private class WheelLabel(
    val stop: CameraZoomStop,
    val angle: Double,
    val label: TextLayoutResult,
    val focalLength: TextLayoutResult?,
)

/**
 * iPhone-camera-style logarithmic zoom wheel. Touch a compact zoom stop to
 * jump to it, or touch and hold then drag horizontally for fine adjustment.
 *
 * @param minZoom Smallest supported zoom factor. Must be positive. Defaults to `0.5`.
 * @param maxZoom Largest supported zoom factor. Must exceed [minZoom]. Defaults to `10`.
 * @param step Display/callback precision. Defaults to `0.1`.
 * @param value Controlled zoom factor.
 * @param defaultValue Initial uncontrolled zoom factor. Defaults to `1` when it is in range.
 * @param zoomStops Optical or quick-zoom stops shown in the compact control and on
 * the wheel. Empty or entirely invalid input falls back to the default in-range stops.
 * @param expanded Controlled expanded state.
 * @param defaultExpanded Initial expanded state for uncontrolled use. Defaults to `false`.
 * @param onExpandedChange Reports compact/wheel state requests.
 * @param onZoomChange Reports each crossed zoom step while dragging or animating.
 * @param onInteractionEnd Reports the final snapped zoom factor after a drag.
 */
@Composable
public fun CameraZoomDial(
    modifier: Modifier = Modifier,
    minZoom: Double = CameraZoomDefaults.MIN,
    maxZoom: Double = CameraZoomDefaults.MAX,
    step: Double = CameraZoomDefaults.STEP,
    value: Double? = null,
    defaultValue: Double? = null,
    zoomStops: List<CameraZoomStop>? = null,
    expanded: Boolean? = null,
    defaultExpanded: Boolean = false,
    onExpandedChange: ((expanded: Boolean) -> Unit)? = null,
    onZoomChange: ((zoom: Double) -> Unit)? = null,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionEnd: ((zoom: Double) -> Unit)? = null,
    formatValue: ((zoom: Double) -> String)? = null,
    accentColor: Color = CameraZoomDialColors.Accent,
    surfaceColor: Color = CameraZoomDialColors.Surface,
    labelColor: Color = CameraZoomDialColors.Label,
    disabled: Boolean = false,
    accessibilityLabel: String = "Camera zoom",
    accessibilityHint: String =
        "Touch and hold, then drag left or right. You can also use increment and decrement actions.",
    testTag: String? = null,
) {
    val range = remember(minZoom, maxZoom) { normalizeCameraZoomRange(minZoom, maxZoom) }
    val safeStep = getSafeCameraZoomStep(step)
    val stops = remember(range, safeStep, zoomStops) {
        val normalized = normalizeCameraZoomStops(
            zoomStops ?: defaultZoomStops(range.min, range.max),
            range.min,
            range.max,
            safeStep,
        )
        normalized.ifEmpty {
            normalizeCameraZoomStops(defaultZoomStops(range.min, range.max), range.min, range.max, safeStep)
        }
    }

    val initialZoom = roundCameraZoom(
        defaultValue ?: if (range.min <= 1 && range.max >= 1) 1.0 else range.min,
        safeStep,
        range.min,
        range.max,
    )
    val zoomIsControlled = value != null
    var uncontrolledZoom by remember { mutableDoubleStateOf(initialZoom) }
    val resolvedZoom = roundCameraZoom(
        value ?: uncontrolledZoom,
        safeStep,
        range.min,
        range.max,
    )

    val expansionIsControlled = expanded != null
    var uncontrolledExpanded by remember { mutableStateOf(defaultExpanded) }
    val resolvedExpanded = expanded ?: uncontrolledExpanded

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val refs = remember {
        CameraZoomRefs(
            expanded = resolvedExpanded,
            expansionIsControlled = expansionIsControlled,
            controlledExpanded = expanded,
            lastReportedZoom = initialZoom,
        )
    }
    val latestOnZoomChange by rememberUpdatedState(onZoomChange)
    val latestOnInteractionStart by rememberUpdatedState(onInteractionStart)
    val latestOnInteractionEnd by rememberUpdatedState(onInteractionEnd)
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    SideEffect {
        refs.expanded = resolvedExpanded
        refs.expansionIsControlled = expansionIsControlled
        refs.controlledExpanded = expanded
    }

    val expansionProgress = remember { Animatable(if (resolvedExpanded) 1f else 0f) }
    var collapseJob by remember { mutableStateOf<Job?>(null) }

    fun animateExpansion(nextExpanded: Boolean) {
        scope.launch {
            expansionProgress.animateTo(
                if (nextExpanded) 1f else 0f,
                tween(motionDuration(EXPANSION_MS, reduceMotion), easing = EASE_OUT),
            )
        }
    }

    fun requestExpanded(nextExpanded: Boolean) {
        collapseJob?.cancel()
        collapseJob = null
        if (refs.expansionIsControlled) {
            if (refs.controlledExpanded == nextExpanded) {
                // Keep the visual state aligned with the controlled prop.
                animateExpansion(nextExpanded)
                return
            }
            latestOnExpandedChange?.invoke(nextExpanded)
            return
        }
        if (refs.expanded == nextExpanded) return
        refs.expanded = nextExpanded
        uncontrolledExpanded = nextExpanded
        animateExpansion(nextExpanded)
        latestOnExpandedChange?.invoke(nextExpanded)
    }

    LaunchedEffect(resolvedExpanded, reduceMotion) {
        expansionProgress.animateTo(
            if (resolvedExpanded) 1f else 0f,
            tween(motionDuration(EXPANSION_MS, reduceMotion), easing = EASE_OUT),
        )
    }

    // ---- Motion (port of useCameraZoomMotion) ----
    var displayZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isInteracting by remember { mutableStateOf(false) }
    val logZoom = remember { Animatable(cameraZoomToLogPosition(initialZoom).toFloat()) }
    SideEffect { if (!refs.interactionActive) refs.currentLogZoom = logZoom.value.toDouble() }

    fun handleZoomChange(nextZoom: Double) {
        if (!zoomIsControlled) uncontrolledZoom = nextZoom
        latestOnZoomChange?.invoke(nextZoom)
    }

    fun reportZoom(nextZoom: Double) {
        displayZoom = nextZoom
        if (refs.lastEmittedZoom == nextZoom) return
        refs.lastEmittedZoom = nextZoom
        handleZoomChange(nextZoom)
    }

    fun beginInteraction() {
        isInteracting = true
        requestExpanded(true)
        latestOnInteractionStart?.invoke()
    }

    fun finishInteraction(finalZoom: Double) {
        displayZoom = finalZoom
        isInteracting = false
        latestOnInteractionEnd?.invoke(finalZoom)
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(CameraZoomDialGeometry.COLLAPSE_DELAY_MS)
            requestExpanded(false)
            collapseJob = null
        }
    }

    // Report each crossed step while a gesture or programmatic move is active.
    LaunchedEffect(range, safeStep) {
        snapshotFlow {
            roundCameraZoom(cameraLogPositionToZoom(logZoom.value.toDouble()), safeStep, range.min, range.max)
        }
            .drop(1)
            .collect { zoom ->
                if (refs.interactionActive && zoom != refs.lastReportedZoom) {
                    refs.lastReportedZoom = zoom
                    reportZoom(zoom)
                }
            }
    }

    // Prop-driven synchronization updates the visible label and wheel.
    LaunchedEffect(resolvedZoom, isInteracting, range, safeStep, reduceMotion) {
        if (isInteracting || refs.interactionActive) return@LaunchedEffect
        val next = roundCameraZoom(resolvedZoom, safeStep, range.min, range.max)
        refs.lastReportedZoom = next
        refs.lastEmittedZoom = next
        displayZoom = next
        logZoom.animateTo(
            cameraZoomToLogPosition(next).toFloat(),
            tween(motionDuration(PROP_SYNC_MS, reduceMotion), easing = EASE_OUT),
        )
    }

    val ticks = remember(range, safeStep) { buildCameraZoomTicks(range.min, range.max, safeStep) }
    val formattedValue = formatValue?.invoke(displayZoom) ?: formatCameraZoomValue(displayZoom, safeStep)
    val activeStop = findCameraZoomStop(displayZoom, stops, safeStep)
    val compactSelectedStop = findNearestCameraZoomStop(displayZoom, stops)
    val wheelLabelStops = remember(stops, range, safeStep) {
        normalizeCameraZoomStops(
            stops + listOf(CameraZoomStop(range.min), CameraZoomStop(range.max)),
            range.min,
            range.max,
            safeStep,
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .aspectRatio((1 / CameraZoomDialGeometry.VISIBLE_HEIGHT_TO_WIDTH).toFloat())
            .clipToBounds(),
    ) {
        val layoutWidth: Dp = maxWidth
        val layoutWidthValue = layoutWidth.value.toDouble()
        val diameter = layoutWidth * CameraZoomDialGeometry.DIAMETER_TO_WIDTH.toFloat()
        val radiusDp = diameter / 2
        val radius = radiusDp.value.toDouble()
        val unit = if (layoutWidthValue > 0) max(0.75, min(1.5, layoutWidthValue / 393)) else 1.0
        val enabled = !disabled && layoutWidthValue > 0

        fun moveToZoom(requestedZoom: Double) {
            if (!enabled) return
            val next = roundCameraZoom(
                clampCameraZoom(requestedZoom, range.min, range.max),
                safeStep,
                range.min,
                range.max,
            )
            if (next == displayZoom) return
            refs.interactionActive = true
            refs.lastReportedZoom = displayZoom
            isInteracting = true
            latestOnInteractionStart?.invoke()
            scope.launch {
                logZoom.stop()
                val target = cameraZoomToLogPosition(next)
                refs.currentLogZoom = target
                logZoom.animateTo(
                    target.toFloat(),
                    tween(motionDuration(PROP_SYNC_MS, reduceMotion), easing = EASE_OUT),
                )
                // Only reached when the timing animation finished without interruption.
                refs.interactionActive = false
                refs.lastReportedZoom = next
                reportZoom(next)
                finishInteraction(next)
            }
        }

        fun handleCompactStopPress(stopValue: Double, selected: Boolean) {
            if (selected) {
                requestExpanded(true)
                return
            }
            moveToZoom(stopValue)
        }

        val gestureModifier = Modifier.pointerInput(enabled, radius, range, safeStep) {
            if (!enabled) return@pointerInput
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var totalDx = 0f

                if (!refs.expanded) {
                    // Compact: the wheel only engages after a touch-and-hold.
                    val lifted = withTimeoutOrNull(CameraZoomDialGeometry.LONG_PRESS_MS) {
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            released = change == null || !change.pressed
                        }
                        true
                    }
                    if (lifted != null) return@awaitEachGesture
                } else {
                    // Expanded: a horizontal drag engages immediately.
                    val slop = awaitHorizontalTouchSlopOrCancellation(pointerId) { change, over ->
                        totalDx += over
                        change.consume()
                    } ?: return@awaitEachGesture
                    velocityTracker.resetTracking()
                    velocityTracker.addPointerInputChange(slop)
                }

                // ---- onStart ----
                scope.launch { logZoom.stop() }
                refs.startZoom = cameraLogPositionToZoom(logZoom.value.toDouble())
                refs.currentLogZoom = logZoom.value.toDouble()
                refs.interactionActive = true
                beginInteraction()

                fun update(dx: Float) {
                    val next = cameraZoomFromTranslation(
                        refs.startZoom,
                        (dx / density.density).toDouble(),
                        radius,
                        range.min,
                        range.max,
                        CameraZoomDialGeometry.DEGREES_PER_OCTAVE,
                    )
                    val nextLog = cameraZoomToLogPosition(next)
                    refs.currentLogZoom = nextLog
                    scope.launch { logZoom.snapTo(nextLog.toFloat()) }
                }
                if (totalDx != 0f) update(totalDx)

                // ---- onUpdate ----
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        pressed = false
                        continue
                    }
                    if (!change.pressed) {
                        change.consume()
                        pressed = false
                        continue
                    }
                    velocityTracker.addPointerInputChange(change)
                    totalDx += change.positionChange().x
                    change.consume()
                    update(totalDx)
                }

                // ---- onFinalize ----
                if (!refs.interactionActive) return@awaitEachGesture
                val finalZoom = roundCameraZoom(
                    cameraLogPositionToZoom(refs.currentLogZoom),
                    safeStep,
                    range.min,
                    range.max,
                )
                val pointsPerOctave = max(
                    1.0,
                    radius * (CameraZoomDialGeometry.DEGREES_PER_OCTAVE * PI / 180),
                )
                val velocityDp = velocityTracker.calculateVelocity().x / density.density
                val velocity = -velocityDp / pointsPerOctave
                refs.interactionActive = false
                refs.lastReportedZoom = finalZoom
                val target = cameraZoomToLogPosition(finalZoom)
                refs.currentLogZoom = target
                scope.launch {
                    logZoom.animateTo(
                        target.toFloat(),
                        spring(dampingRatio = 1f, stiffness = SETTLE_STIFFNESS),
                        initialVelocity = velocity.toFloat(),
                    )
                }
                reportZoom(finalZoom)
                finishInteraction(finalZoom)
            }
        }

        val semanticsModifier = if (resolvedExpanded) {
            Modifier.clearAndSetSemantics {
                contentDescription = if (accessibilityHint.isBlank()) {
                    accessibilityLabel
                } else {
                    "$accessibilityLabel. $accessibilityHint"
                }
                stateDescription = formattedValue
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayZoom.toFloat(),
                    range = range.min.toFloat()..range.max.toFloat(),
                )
                if (disabled) disabled()
                customActions = listOf(
                    CustomAccessibilityAction("Increment zoom") {
                        moveToZoom(displayZoom + safeStep)
                        true
                    },
                    CustomAccessibilityAction("Decrement zoom") {
                        moveToZoom(displayZoom - safeStep)
                        true
                    },
                )
                setProgress { target ->
                    if (disabled) return@setProgress false
                    moveToZoom(target.toDouble())
                    true
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .then(semanticsModifier)
                .then(gestureModifier),
        ) {
            if (layoutWidthValue > 0) {
                CameraZoomWheel(
                    diameter = diameter,
                    offsetX = (layoutWidth - diameter) / 2,
                    unit = unit,
                    ticks = ticks,
                    stops = stops,
                    wheelLabelStops = wheelLabelStops,
                    range = range,
                    step = safeStep,
                    displayZoom = displayZoom,
                    formattedValue = formattedValue,
                    activeFocalLength = activeStop?.focalLength,
                    logZoom = { logZoom.value },
                    expansionProgress = { expansionProgress.value },
                    reduceMotion = reduceMotion,
                    accentColor = accentColor,
                    surfaceColor = surfaceColor,
                    labelColor = labelColor,
                )
            }

            Row(
                modifier = Modifier
                    .offset(y = (CameraZoomDialGeometry.COMPACT_ROW_TOP * unit).dp)
                    .fillMaxWidth()
                    .height((44 * unit).dp)
                    .graphicsLayer {
                        val progress = expansionProgress.value
                        alpha = 1 - progress
                        val scale = if (reduceMotion) 1f else 1f - progress * 0.06f
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(if (resolvedExpanded) Modifier.clearAndSetSemantics {} else Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stops.forEach { stop ->
                    val selected = compactSelectedStop?.value == stop.value
                    val label = if (selected) {
                        formatValue?.invoke(displayZoom) ?: formatCameraZoomValue(displayZoom, safeStep)
                    } else {
                        stop.compactLabel ?: stop.label ?: formatCameraZoomCompactNumber(stop.value, safeStep)
                    }
                    CompactStopButton(
                        label = label,
                        selected = selected,
                        disabled = disabled,
                        interactive = !resolvedExpanded,
                        unit = unit,
                        accentColor = accentColor,
                        labelColor = labelColor,
                        testTag = testTag?.let { "$it-stop-${svgNumberText(stop.value)}" },
                        onPress = { handleCompactStopPress(stop.value, selected) },
                    )
                }
            }
        }
    }
}

private fun svgNumberText(value: Double): String {
    val asLong = value.toLong()
    return if (asLong.toDouble() == value) asLong.toString() else value.toString()
}

@Composable
private fun CompactStopButton(
    label: String,
    selected: Boolean,
    disabled: Boolean,
    interactive: Boolean,
    unit: Double,
    accentColor: Color,
    labelColor: Color,
    testTag: String?,
    onPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val size = (42 * unit).dp
    val hint = if (selected) "Opens the adjustable zoom wheel." else "Changes zoom to $label."

    Box(
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .size(size)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label zoom. $hint"
                this.selected = selected
                if (disabled) disabled()
            }
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape((21 * unit).dp))
                        .background(CameraZoomDialColors.CompactSurface)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = interactive && !disabled,
                onClick = onPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (selected) accentColor else labelColor,
                fontSize = with(density) { (14 * unit).dp.toSp() },
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun CameraZoomWheel(
    diameter: Dp,
    offsetX: Dp,
    unit: Double,
    ticks: List<Double>,
    stops: List<CameraZoomStop>,
    wheelLabelStops: List<CameraZoomStop>,
    range: CameraZoomRange,
    step: Double,
    displayZoom: Double,
    formattedValue: String,
    activeFocalLength: String?,
    logZoom: () -> Float,
    expansionProgress: () -> Float,
    reduceMotion: Boolean,
    accentColor: Color,
    surfaceColor: Color,
    labelColor: Color,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val px = density.density
    val radiusDp = diameter.value.toDouble() / 2

    val geometry = remember(radiusDp, unit, px) {
        WheelGeometry(radiusDp = radiusDp, unit = unit, px = px)
    }
    val tickSegments = remember(ticks, stops, step, range, geometry) {
        ticks.map { tick ->
            val angle = cameraZoomToAngle(tick, 1.0, CameraZoomDialGeometry.DEGREES_PER_OCTAVE)
            val major = isCameraZoomMajorTick(tick, stops, step, range.min, range.max)
            val length = (if (major) CameraZoomDialGeometry.MAJOR_TICK_LENGTH else CameraZoomDialGeometry.MINOR_TICK_LENGTH) * unit
            val outer = polarPoint(geometry.centerPx, geometry.outerTickRadiusPx, angle)
            val inner = polarPoint(geometry.centerPx, geometry.outerTickRadiusPx - (length * px).toFloat(), angle)
            TickSegment(outer, inner, major)
        }
    }
    val labelStyle = remember(labelColor, unit, density) {
        TextStyle(
            color = labelColor,
            fontSize = with(density) { (17 * unit).dp.toSp() },
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
    val focalStyle = remember(unit, density) {
        TextStyle(
            color = CameraZoomDialColors.SecondaryLabel,
            fontSize = with(density) { (12.5 * unit).dp.toSp() },
            fontWeight = FontWeight.Normal,
            letterSpacing = with(density) { (0.35 * unit).dp.toSp() },
            textAlign = TextAlign.Center,
        )
    }
    val wheelLabels = remember(wheelLabelStops, step, labelStyle, focalStyle, textMeasurer) {
        wheelLabelStops.map { stop ->
            WheelLabel(
                stop = stop,
                angle = cameraZoomToAngle(stop.value, 1.0, CameraZoomDialGeometry.DEGREES_PER_OCTAVE),
                label = textMeasurer.measure(stop.label ?: formatCameraZoomNumber(stop.value, step), labelStyle),
                focalLength = stop.focalLength?.let { textMeasurer.measure(it, focalStyle) },
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .size(diameter)
            .graphicsLayer {
                val progress = expansionProgress()
                alpha = progress
                val scale = if (reduceMotion) 1f else 0.46f + progress * 0.54f
                scaleX = scale
                scaleY = scale
            }
            .clearAndSetSemantics {},
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val center = geometry.centerPx
            drawCircle(color = surfaceColor, radius = center, center = Offset(center, center))

            clipPath(geometry.occlusionPath, ClipOp.Difference) {
                clipPath(geometry.tailPath, ClipOp.Difference) {
                    rotate(
                        degrees = (-logZoom() * CameraZoomDialGeometry.DEGREES_PER_OCTAVE).toFloat(),
                        pivot = Offset(center, center),
                    ) {
                        for (segment in tickSegments) {
                            drawLine(
                                color = if (segment.major) CameraZoomDialColors.MajorTick else CameraZoomDialColors.MinorTick,
                                start = Offset(segment.outer.x, segment.outer.y),
                                end = Offset(segment.inner.x, segment.inner.y),
                                strokeWidth = ((if (segment.major) 1.45 else 0.75) * unit * px).toFloat(),
                                cap = StrokeCap.Round,
                            )
                        }

                        for (label in wheelLabels) {
                            val relativeAngle = cameraZoomToAngle(
                                label.stop.value,
                                displayZoom,
                                CameraZoomDialGeometry.DEGREES_PER_OCTAVE,
                            )
                            if (abs(relativeAngle) < CENTER_LABEL_EXCLUSION_DEGREES) continue

                            val labelPoint = polarPoint(center, geometry.labelRadiusPx, label.angle)
                            rotate(degrees = label.angle.toFloat(), pivot = Offset(labelPoint.x, labelPoint.y)) {
                                val baseline = labelPoint.y + (5 * unit * px).toFloat()
                                drawText(
                                    textLayoutResult = label.label,
                                    topLeft = Offset(
                                        labelPoint.x - label.label.size.width / 2f,
                                        baseline - label.label.firstBaseline,
                                    ),
                                )
                            }
                            val focal = label.focalLength ?: continue
                            val focalPoint = polarPoint(center, geometry.focalRadiusPx, label.angle)
                            rotate(degrees = label.angle.toFloat(), pivot = Offset(focalPoint.x, focalPoint.y)) {
                                val baseline = focalPoint.y + (4 * unit * px).toFloat()
                                drawText(
                                    textLayoutResult = focal,
                                    topLeft = Offset(
                                        focalPoint.x - focal.size.width / 2f,
                                        baseline - focal.firstBaseline,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            drawPath(path = geometry.pointerPath, color = accentColor)
        }

        Column(
            modifier = Modifier
                .offset(x = (radiusDp - 70 * unit).dp, y = (29 * unit).dp)
                .width((140 * unit).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = formattedValue,
                style = TextStyle(
                    color = accentColor,
                    fontSize = with(density) { (17 * unit).dp.toSp() },
                    lineHeight = with(density) { (21 * unit).dp.toSp() },
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                ),
            )
            if (activeFocalLength != null) {
                BasicText(
                    text = activeFocalLength,
                    style = TextStyle(
                        color = accentColor,
                        fontSize = with(density) { (12.5 * unit).dp.toSp() },
                        lineHeight = with(density) { (17 * unit).dp.toSp() },
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.35.sp,
                        fontFeatureSettings = "tnum",
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

private class TickSegment(val outer: WheelPoint, val inner: WheelPoint, val major: Boolean)

/** Pixel-space wheel geometry derived from the dp constants, `unit`, and density. */
private class WheelGeometry(radiusDp: Double, unit: Double, px: Float) {
    val centerPx: Float = (radiusDp * px).toFloat()
    val outerTickRadiusPx: Float =
        ((radiusDp - CameraZoomDialGeometry.OUTER_TICK_INSET * unit) * px).toFloat()
    val labelRadiusPx: Float = ((radiusDp - CameraZoomDialGeometry.LABEL_INSET * unit) * px).toFloat()
    val focalRadiusPx: Float =
        ((radiusDp - CameraZoomDialGeometry.FOCAL_LABEL_INSET * unit) * px).toFloat()

    private val pointerTop = CameraZoomDialGeometry.OUTER_TICK_INSET * unit
    private val pointerHalfWidth = CameraZoomDialGeometry.POINTER_HALF_WIDTH * unit
    private val pointerHeight = CameraZoomDialGeometry.POINTER_HEIGHT * unit
    private val occlusionPadding = CameraZoomDialGeometry.POINTER_OCCLUSION_PADDING * unit
    private val occlusionTop = max(0.0, pointerTop - occlusionPadding)
    private val occlusionApex =
        pointerTop + pointerHeight + CameraZoomDialGeometry.POINTER_OCCLUSION_APEX_OFFSET * unit
    private val occlusionEnd =
        pointerTop + CameraZoomDialGeometry.MAJOR_TICK_LENGTH * unit + occlusionPadding
    private val occlusionHalfWidth = getCameraZoomTriangleHalfWidth(
        occlusionApex - occlusionTop,
        CameraZoomDialGeometry.POINTER_OCCLUSION_APEX_ANGLE,
    )
    private val tailHalfWidth = CameraZoomDialGeometry.POINTER_OCCLUSION_TAIL_HALF_WIDTH * unit
    private val tailTop = occlusionApex - tailHalfWidth

    val tailHalfWidthPx: Float = (tailHalfWidth * px).toFloat()
    val tailTopPx: Float = (tailTop * px).toFloat()
    val tailHeightPx: Float = (max(0.0, occlusionEnd - tailTop) * px).toFloat()

    val occlusionPath: Path = roundedTrianglePath(
        centerX = radiusDp * px,
        topY = occlusionTop * px,
        halfWidth = occlusionHalfWidth * px,
        apexY = occlusionApex * px,
        cornerRadius = CameraZoomDialGeometry.POINTER_OCCLUSION_CORNER_RADIUS * unit * px,
    )
    val tailPath: Path = Path().apply {
        addRoundRect(
            RoundRect(
                left = centerPx - tailHalfWidthPx,
                top = tailTopPx,
                right = centerPx + tailHalfWidthPx,
                bottom = tailTopPx + tailHeightPx,
                cornerRadius = CornerRadius(tailHalfWidthPx, tailHalfWidthPx),
            ),
        )
    }
    val pointerPath: Path = roundedTrianglePath(
        centerX = radiusDp * px,
        topY = pointerTop * px,
        halfWidth = pointerHalfWidth * px,
        apexY = (pointerTop + pointerHeight) * px,
        cornerRadius = CameraZoomDialGeometry.POINTER_CORNER_RADIUS * unit * px,
    )
}
