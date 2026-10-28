package com.ngocdevv.nativecook.blocks.charts.prismwindowchart

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

private const val PRISM_WINDOW_LOOP = 8.0
private val PrismAccent = Color(0xFF685BEE)
private val PrismInk = Color(0xFF272832)
private fun observationFor(pose: Float): Int = if (pose > 0.7f) 2 else if (pose < 0.3f) 0 else 1

private fun prismWindowSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun prismWindowPose(time: Double): Float {
    return prismWindowSmooth(((time - 1.4) / 1.8).toFloat()) *
        (1f - prismWindowSmooth(((time - 4.8) / 1.8).toFloat()))
}

private fun prismWindowSample(x: Double, selection: Int): Double {
    return 178 - 90 * x + 26 * kotlin.math.sin(2 * Math.PI * x) + 18 * kotlin.math.cos(6 * Math.PI * x + selection * 0.5)
}

@Composable
fun PrismWindowChart(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current
    BlockSystemBars(darkIcons = true)
    val initialTime = previewTime?.let { ((it % PRISM_WINDOW_LOOP) + PRISM_WINDOW_LOOP) % PRISM_WINDOW_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 0.5f else if (previewTime == null) 0f else prismWindowPose(initialTime)) }
    val clock = remember { mutableDoubleStateOf(initialTime) }
    val revision = remember { mutableIntStateOf(0) }
    val selection = remember { mutableIntStateOf(0) }
    var observation by remember { mutableIntStateOf(if (reduced) 1 else if (previewTime == null) 0 else observationFor(prismWindowPose(initialTime))) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableStateOf(false) }

    fun chooseRange(index: Int, resetLens: Boolean) {
        selection.intValue = index
        if (reduced) return
        if (resetLens) {
            pose.floatValue = 0f
            returnFrom = 0f
            returnElapsed = 0f
            dragging = false
            returning = true
            revision.intValue++
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun accessibleSet(value: Float) {
        if (reduced) return
        pose.floatValue = value
        returnFrom = value
        returnElapsed = 0f
        dragging = false
        returning = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    DisposableEffect(lifecycle, reduced) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) active = false
            else if (event == Lifecycle.Event.ON_START) {
                active = true
                pose.floatValue = if (reduced) 0.5f else 0f
                clock.doubleValue = 0.0
                dragging = false
                returning = false
                revision.intValue++
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, reduced, previewTime, revision.intValue, active) {
        if (reduced) {
            clock.doubleValue = 0.0
            pose.floatValue = 0.5f
            observation = 1
            dragging = false
            returning = false
            returnElapsed = 0f
            return@LaunchedEffect
        }
        if (previewTime != null && !dragging && !returning) {
            clock.doubleValue = ((previewTime % PRISM_WINDOW_LOOP) + PRISM_WINDOW_LOOP) % PRISM_WINDOW_LOOP
            pose.floatValue = prismWindowPose(clock.doubleValue)
            observation = observationFor(pose.floatValue)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        if (!dragging && !returning && !autoPlay) { pose.floatValue = 0f; clock.doubleValue = 0.0; return@LaunchedEffect }
        var previous = withFrameNanos { it }
        while (active && !reduced && (previewTime == null || dragging || returning)) {
            val now = withFrameNanos { it }
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            val nextObservation = observationFor(pose.floatValue)
            if (nextObservation != observation) observation = nextObservation
            if (returning) {
                returnElapsed += dt
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = (returnElapsed - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - prismWindowSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else prismWindowPose(((previewTime % PRISM_WINDOW_LOOP) + PRISM_WINDOW_LOOP) % PRISM_WINDOW_LOOP)
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % PRISM_WINDOW_LOOP
                pose.floatValue = prismWindowPose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color(0xFFE9ECFF))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 20.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("≋", color = PrismInk, fontSize = 17.sp)
                Text("PRISM / OBSERVATORY", Modifier.offset(x = 8.dp), color = PrismInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("NOVA", Modifier.weight(1f), color = PrismInk.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Text("See the\nwhole picture.", Modifier.offset(y = 77.dp).width(393.dp), color = PrismInk, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 41.sp, textAlign = TextAlign.Center)
            Text("A closer look at your daily index.", Modifier.offset(y = 180.dp).width(393.dp), color = PrismInk.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Box(
                Modifier.offset(x = 28.dp, y = 220.dp).size(337.dp, 330.dp)
                    .pointerInput(reduced, previewTime, scale) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    detent = false
                                    dragging = true
                                    returning = false
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && dragging) {
                                    change.consume()
                                    dragDistance += amount.x / density.density / scale
                                    val next = (dragStart + dragDistance / 180f).coerceIn(0f, 1f)
                                    pose.floatValue = next
                                    if (next > 0.85f && !detent) {
                                        detent = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                }
                            },
                            onDragEnd = { if (!reduced && dragging) { returnFrom = pose.floatValue; returnElapsed = 0f; dragging = false; returning = true; revision.intValue++ } },
                            onDragCancel = { if (!reduced && dragging) { returnFrom = pose.floatValue; returnElapsed = 0f; dragging = false; returning = true; revision.intValue++ } },
                        )
                    }
                    .semantics {
                        contentDescription = "Prism Window Chart"
                        stateDescription = when (observation) { 2 -> "Friday observation"; 0 -> "Monday observation"; else -> "Midweek observation" }
                        customActions = listOf(
                            CustomAccessibilityAction("Move lens right") { accessibleSet(1f); true },
                            CustomAccessibilityAction("Move lens left") { accessibleSet(0f); true },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pixelScale = this.density
                    val p = pose.floatValue
                    val selected = selection.intValue
                    val panelTop = 15f * pixelScale
                    val panelSize = androidx.compose.ui.geometry.Size(337f * pixelScale, 275f * pixelScale)
                    drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0xFFCDD4FD).copy(alpha = 0.7f)), Offset.Zero, Offset(337f * pixelScale, 290f * pixelScale)), topLeft = Offset(0f, panelTop), size = panelSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f * pixelScale))
                    drawRoundRect(Color.White, topLeft = Offset(0f, panelTop), size = panelSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f * pixelScale), style = Stroke(1f * pixelScale))
                    for (i in 0 until 4) {
                        val y = (70 + i * 48f) * pixelScale
                        drawLine(PrismAccent.copy(alpha = 0.09f), Offset(24f * pixelScale, y), Offset(313f * pixelScale, y), 1f * pixelScale)
                    }
                    val base = prismCurve(selected, pixelScale)
                    drawPath(base, PrismAccent.copy(alpha = 0.45f), style = Stroke(2f * pixelScale))
                    val u = 0.35f + 0.3f * p
                    val lensX = (74f + 208f * p) * pixelScale
                    val lensY = prismWindowSample((74 + p * 208 - 24) / 289.0, selected).toFloat() * pixelScale
                    val lensRadius = 53f * pixelScale
                    val lensBounds = androidx.compose.ui.geometry.Rect(lensX - lensRadius, lensY - lensRadius, lensX + lensRadius, lensY + lensRadius)
                    val lensClip = Path().apply { addOval(lensBounds) }
                    clipPath(lensClip) {
                        drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0xFFDDD9FF).copy(alpha = 0.8f)), Offset(lensX - lensRadius, lensY - lensRadius), Offset(lensX + lensRadius, lensY + lensRadius)), radius = lensRadius, center = Offset(lensX, lensY))
                        val zoomed = prismCurve(selected, pixelScale, lensX, lensY, 1.35f)
                        drawPath(zoomed, PrismAccent, style = Stroke(2.5f * pixelScale))
                    }
                    drawCircle(Brush.linearGradient(listOf(Color.White, Color(0xFF8CE0E2), PrismAccent.copy(alpha = 0.6f)), Offset(lensX - lensRadius, lensY - lensRadius), Offset(lensX + lensRadius, lensY + lensRadius)), radius = lensRadius, center = Offset(lensX, lensY), style = Stroke(3f * pixelScale))
                    drawCircle(Color.White.copy(alpha = 0.75f), radius = 48f * pixelScale, center = Offset(lensX, lensY), style = Stroke(0.7f * pixelScale))
                    drawCircle(PrismAccent, radius = 4f * pixelScale, center = Offset(lensX, lensY))
                    drawLine(PrismAccent.copy(alpha = 0.4f), Offset(lensX, lensY + 56f * pixelScale), Offset(lensX, 247f * pixelScale), 1f * pixelScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * pixelScale, 3f * pixelScale)))
                }
                PrismChartLabels()
            }
            Row(Modifier.offset(x = 36.5.dp, y = 568.dp).width(320.dp).height(46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Week", "Month", "Year").forEachIndexed { index, text ->
                    Box(
                        Modifier.width(104.dp).height(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (selection.intValue == index) PrismAccent else Color.White.copy(alpha = 0.6f))
                            .clickable(role = Role.Button) { chooseRange(index, resetLens = true) }
                            .semantics { contentDescription = "$text range"; role = Role.Button; selected = selection.intValue == index },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, color = if (selection.intValue == index) Color.White else PrismInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Row(
                Modifier.offset(x = 28.dp, y = 653.dp).size(337.dp, 54.dp).clip(RoundedCornerShape(18.dp)).background(PrismAccent)
                    .clickable(role = Role.Button) { chooseRange(0, resetLens = true) }
                    .semantics { contentDescription = "Reset view"; role = Role.Button },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↗", color = Color.White, fontSize = 15.sp)
                Text("Reset view", Modifier.offset(x = 9.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("SCRUB THE LENS HORIZONTALLY", Modifier.offset(y = 731.dp).width(393.dp), color = PrismInk.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PrismChartLabels() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Text("OBSERVATION WINDOW", Modifier.offset(x = 0.dp, y = 32.dp).width(337.dp), color = PrismAccent.copy(alpha = 0.65f), fontSize = 9.sp, textAlign = TextAlign.Center)
        listOf("MON", "TUE", "WED", "THU", "FRI").forEachIndexed { index, label ->
            Text(label, Modifier.offset(x = (35 + index * 66 - 15).dp, y = 255.dp).width(30.dp), color = PrismInk.copy(alpha = 0.5f), fontSize = 8.sp, textAlign = TextAlign.Center)
        }
        Text("128.6", Modifier.offset(x = 0.dp, y = 294.dp).width(337.dp), color = PrismInk, fontSize = 24.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

private fun prismCurve(selection: Int, px: Float, lensX: Float? = null, lensY: Float? = null, scale: Float = 1f): Path {
    val path = Path()
    for (i in 0..96) {
        val t = i / 96f
        val x = 24f + t * 289f
        val y = prismWindowSample(t.toDouble(), selection).toFloat()
        val outX = if (lensX == null) x else ((x * px - lensX) * scale + lensX) / px
        val outY = if (lensY == null) y else ((y * px - lensY) * scale + lensY) / px
        if (i == 0) path.moveTo(outX * px, outY * px) else path.lineTo(outX * px, outY * px)
    }
    return path
}
