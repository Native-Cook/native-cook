package com.ngocdevv.nativecook.blocks.media.ribbonresonanceplayer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val RIBBON_RESONANCE_LOOP = 8.0
private val RibbonCream = Color(0xFFE8E2FF)

private fun ribbonResonanceSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun ribbonResonancePose(time: Double): Float {
    return ribbonResonanceSmooth(((time - 1.4) / 1.8).toFloat()) *
        (1f - ribbonResonanceSmooth(((time - 4.6) / 1.8).toFloat()))
}

@Composable
fun RibbonResonancePlayer(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    val localDensity = LocalDensity.current
    val pose = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val pausedAt = remember { mutableDoubleStateOf(0.0) }
    val revision = remember { mutableIntStateOf(0) }
    val seconds = remember { mutableIntStateOf(744) }
    var playing by remember { mutableStateOf(true) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }

    fun interrupt() {
        returnFrom = pose.floatValue
        returnElapsed = 0f
        dragging = false
        returning = true
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        revision.intValue++
    }

    fun accessibleBend(value: Float) {
        pose.floatValue = value
        returnFrom = value
        returnElapsed = 0f
        dragging = false
        returning = true
        revision.intValue++
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) active = false
            else if (event == Lifecycle.Event.ON_START) {
                active = true
                pose.floatValue = 0f
                clock.doubleValue = 0.0
                pausedAt.doubleValue = 0.0
                dragging = false
                returning = false
                revision.intValue++
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, playing, reduced, previewTime, revision.intValue, active) {
        if (reduced) {
            dragging = false
            returning = false
            clock.doubleValue = 0.0
            pausedAt.doubleValue = 0.0
            pose.floatValue = 0f
            return@LaunchedEffect
        }
        if (previewTime != null && !dragging && !returning) {
            clock.doubleValue = ((previewTime % RIBBON_RESONANCE_LOOP) + RIBBON_RESONANCE_LOOP) % RIBBON_RESONANCE_LOOP
            pose.floatValue = ribbonResonancePose(clock.doubleValue)
            return@LaunchedEffect
        }
        if (!active || (!dragging && !returning && (!autoPlay || !playing))) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (active && !reduced && (previewTime == null || dragging || returning)) {
            val now = withFrameNanos { it }
            val delta = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            if (returning) {
                returnElapsed += delta
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = (returnElapsed - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - ribbonResonanceSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else ribbonResonancePose(((previewTime % RIBBON_RESONANCE_LOOP) + RIBBON_RESONANCE_LOOP) % RIBBON_RESONANCE_LOOP)
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        pausedAt.doubleValue = 0.0
                        if (!autoPlay || !playing || previewTime != null) break
                    }
                }
            } else if (!dragging && autoPlay && playing) {
                clock.doubleValue = (clock.doubleValue + delta) % RIBBON_RESONANCE_LOOP
                pose.floatValue = ribbonResonancePose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF160D3B), Color(0xFF231858), Color(0xFF29205F))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 30.dp, y = 18.dp).width(333.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("≋", color = RibbonCream, fontSize = 22.sp, lineHeight = 24.sp)
                Text("RESONANCE / NOW PLAYING", Modifier.offset(x = 8.dp), color = RibbonCream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
                Text("•••", Modifier.weight(1f), color = RibbonCream, fontSize = 14.sp, letterSpacing = 2.sp, textAlign = TextAlign.End)
            }
            Canvas(
                Modifier.offset(x = 16.5.dp, y = 80.dp).size(360.dp)
                    .pointerInput(reduced, previewTime) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    dragging = true
                                    returning = false
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && dragging) {
                                    change.consume()
                                    dragDistance += amount.x / localDensity.density
                                    pose.floatValue = (dragStart + dragDistance / 200f).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (!reduced && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                            onDragCancel = {
                                if (!reduced && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Resonance strings. Drag right to bend the strings."
                        customActions = listOf(
                            CustomAccessibilityAction("Increase resonance") { accessibleBend(1f); true },
                            CustomAccessibilityAction("Reduce resonance") { accessibleBend(0f); true },
                        )
                    },
            ) {
                val p = pose.floatValue
                val unit = density.toDouble()
                drawCircle(
                    Brush.radialGradient(0f to Color(0xFF2DE4CB), 0.176f to Color(0xFF2DE4CB), 0.451f to Color(0xA60C9AAB), 0.725f to Color(0x403D4DB9), 1f to Color.Transparent, center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2), radius = 170.dp.toPx()),
                    radius = 170.dp.toPx(),
                )
                for (i in 0 until 64) {
                    val a = i * 2 * PI / 64
                    val startRadius = 63.0 * unit / max(abs(cos(a)), abs(sin(a)))
                    val center = 180.0 * unit
                    val beginX = center + cos(a) * startRadius
                    val beginY = center + sin(a) * startRadius
                    val theta = a + p * 0.32 * sin(a)
                    val radius = (148.0 + p * 18 * sin(3 * a)) * unit
                    val endX = center + cos(theta) * radius
                    val endY = center + sin(theta) * radius
                    val controlX = (beginX + endX) / 2 - p * 36 * unit * sin(2 * a) * sin(a)
                    val controlY = (beginY + endY) / 2 + p * 36 * unit * sin(2 * a) * cos(a)
                    val string = Path().apply { moveTo(beginX.toFloat(), beginY.toFloat()); quadraticTo(controlX.toFloat(), controlY.toFloat(), endX.toFloat(), endY.toFloat()) }
                    drawPath(string, Color(0xFF061E3A).copy(alpha = 0.5f), style = Stroke(0.8.dp.toPx()))
                    drawCircle(Color(0xFF130E37), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(endX.toFloat(), endY.toFloat()))
                }
                rotate(p * 12f, pivot = androidx.compose.ui.geometry.Offset(180.dp.toPx(), 180.dp.toPx())) {
                    drawRect(
                        Brush.linearGradient(listOf(Color(0xFF140D37), Color(0xFF241358))),
                        topLeft = androidx.compose.ui.geometry.Offset(117.dp.toPx(), 117.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(126.dp.toPx(), 126.dp.toPx()),
                    )
                    drawRect(Color(0xFF7566C0).copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(117.dp.toPx(), 117.dp.toPx()), size = androidx.compose.ui.geometry.Size(126.dp.toPx(), 126.dp.toPx()), style = Stroke(1.dp.toPx()))
                    for (row in 0 until 9) {
                        val wave = Path()
                        for (j in 0..60) {
                            val x = j * 1.4 + 21
                            val envelope = sin(j / 60.0 * PI).let { it * it }
                            val y = row * 4 + 47 + sin(j * 0.36 + row * 0.32) * envelope * (6 + 4 * p)
                            val point = androidx.compose.ui.geometry.Offset((117 + x).dp.toPx(), (117 + y).dp.toPx())
                            if (j == 0) wave.moveTo(point.x, point.y) else wave.lineTo(point.x, point.y)
                        }
                        drawPath(wave, Color(0xFF856DF4).copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
                    }
                }
            }
            androidx.compose.foundation.layout.Column(Modifier.offset(x = 30.dp, y = 458.dp).width(333.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Morrow Radio", color = RibbonCream.copy(alpha = 0.55f), fontSize = 13.sp)
                Text("The space between", color = RibbonCream, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            val fraction = seconds.intValue / 2280f
            Box(Modifier.offset(x = 30.dp, y = 538.dp).width(333.dp).height(26.dp)) {
                Box(Modifier.align(Alignment.TopStart).width(333.dp).height(3.dp).clip(CircleShape).background(RibbonCream.copy(alpha = 0.13f)))
                Box(Modifier.align(Alignment.TopStart).width(333.dp * fraction).height(3.dp).clip(CircleShape).background(Color(0xFFAEA1FC)))
                Box(Modifier.offset(x = 333.dp * fraction - 5.dp, y = (-3.5).dp).size(10.dp).background(RibbonCream, CircleShape))
                Row(Modifier.align(Alignment.BottomStart).fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    Text("${seconds.intValue / 60}:${(seconds.intValue % 60).toString().padStart(2, '0')}", color = RibbonCream.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("38:00", Modifier.weight(1f), color = RibbonCream.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End)
                }
            }
            Row(Modifier.offset(x = 60.5.dp, y = 599.dp).size(272.dp, 62.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("↶15", Modifier.size(54.dp, 62.dp).clickable(role = Role.Button) { seconds.intValue = max(0, seconds.intValue - 15); interrupt() }.semantics { contentDescription = "Back 15 seconds"; role = Role.Button }, color = RibbonCream, fontSize = 20.sp, textAlign = TextAlign.Center)
                Row(
                    Modifier.size(112.dp, 62.dp).clip(RoundedCornerShape(31.dp)).background(Color(0xFFAEA1FC))
                        .clickable(role = Role.Button) {
                            if (playing) pausedAt.doubleValue = clock.doubleValue else clock.doubleValue = pausedAt.doubleValue
                            playing = !playing
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        .semantics { contentDescription = if (playing) "Pause" else "Play"; role = Role.Button },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (playing) "Ⅱ" else "▶", color = Color(0xFF231858), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text("↷15", Modifier.size(54.dp, 62.dp).clickable(role = Role.Button) { seconds.intValue = min(2280, seconds.intValue + 15); interrupt() }.semantics { contentDescription = "Forward 15 seconds"; role = Role.Button }, color = RibbonCream, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
            Text("DRAG TO FEEL THE RESONANCE", Modifier.offset(y = 708.dp).width(393.dp), color = RibbonCream.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.8.sp, textAlign = TextAlign.Center)
            Box(Modifier.offset(x = 179.5.dp, y = 744.dp).size(34.dp, 3.dp).clip(CircleShape).background(RibbonCream.copy(alpha = 0.3f)))
        }
    }
}
