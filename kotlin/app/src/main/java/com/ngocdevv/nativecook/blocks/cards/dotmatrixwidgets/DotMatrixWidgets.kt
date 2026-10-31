package com.ngocdevv.nativecook.blocks.cards.dotmatrixwidgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.exp
import kotlin.math.pow

/** Seconds. Both ends of the loop show the completed launcher. */
const val LOOP = 8.0
private const val M = 23f
private const val G = 17f
private const val HOLD = 2.0

// RN reference: rest 0–1; reverse cubic fold 1–1.88; empty until 2.3;
// back-eased 0.6 s pops, 0.42 s apart: calendar/weather/icons/map, ending 5.42.
// Streak days light at 2.7/2.9/3.1, today at 3.5. Drag tilts 0.08 degrees/dp,
// capped at ±14. Touch and the following 2 s freeze the current loop phase.
private fun shownAt(n: Int, time: Double): Float {
    fun seg(a: Double, b: Double) = ((time - a) / (b - a)).coerceIn(0.0, 1.0)
    if (time < 1.9) return (1.0 - seg(1.0 + (6 - n) * 0.08, 1.4 + (6 - n) * 0.08).pow(3)).toFloat()
    val x = seg(2.3 + n * 0.42, 2.9 + n * 0.42) - 1.0
    return (1.0 + 2.6 * x.pow(3) + 1.6 * x.pow(2)).toFloat()
}

private class DotMatrixWidgetsClock {
    var elapsed = 0.0
    var origin = 0.0
    var time by mutableDoubleStateOf(0.0)
    var tiltX by mutableFloatStateOf(0f)
    var tiltY by mutableFloatStateOf(0f)
    var heldX = 0f
    var heldY = 0f
    var touching = false
    var released = -10.0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching || elapsed - released < HOLD) {
            origin += dt
            if (!touching) {
                val k = exp(-6.0 * (elapsed - released)).toFloat()
                tiltX = heldX * k
                tiltY = heldY * k
            }
        }
        if (autoPlay) time = ((elapsed - origin) % LOOP + LOOP) % LOOP
    }
}

/** A full-screen launcher board. Drag anywhere to tilt the widgets. */
@Composable
fun DotMatrixWidgets(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val auto by rememberUpdatedState(autoPlay)
    val motion = remember { DotMatrixWidgetsClock() }
    val d = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = insets.calculateTopPadding().value
    val safeBottom = insets.calculateBottomPadding().value
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reducedMotion) {
        if (!active || reducedMotion) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
            previous = now
            motion.advance(dt, auto)
        }
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFF1B5D8E))
            .drawWithCache {
                val gradient = Brush.verticalGradient(listOf(Color(0xFF2C7FB8), Color(0xFF1B5D8E), Color(0xFF123F63)))
                onDrawBehind {
                    drawRect(gradient)
                    val ink = Color.White.copy(alpha = 0.12f)
                    repeat(30) { drawLine(ink, Offset(0f, it * 30f * density), Offset(size.width, it * 30f * density), density) }
                    repeat(15) { drawLine(ink, Offset(it * 30f * density, 0f), Offset(it * 30f * density, 900f * density), density) }
                }
            }
            .pointerInput(reducedMotion) {
                if (reducedMotion) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    motion.touching = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = (change.position - down.position) / d
                            motion.tiltX = (-delta.y * 0.08f).coerceIn(-14f, 14f)
                            motion.tiltY = (delta.x * 0.08f).coerceIn(-14f, 14f)
                            motion.heldX = motion.tiltX
                            motion.heldY = motion.tiltY
                            change.consume()
                        }
                    } finally {
                        motion.touching = false
                        motion.released = motion.elapsed
                    }
                }
            },
    ) {
        val full = maxWidth.value - 2 * M
        val half = (full - G) / 2
        val icon = (half - G) / 2
        val top = safeTop + 64f
        Row(Modifier.offset(M.dp, (safeTop + 8).dp).size(full.dp, 34.dp), verticalAlignment = Alignment.CenterVertically) {
            DotMatrixWidgetsText("Tiles", 20f, FontWeight.Black)
            Spacer(Modifier.weight(1f))
            DotMatrixWidgetsText("Edit home screen", 14f, FontWeight.SemiBold, Color.White.copy(alpha = 0.75f))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            cameraDistance = 900f * density
            rotationX = if (reducedMotion) 0f else motion.tiltX
            rotationY = if (reducedMotion) 0f else motion.tiltY
        }) {
            DotMatrixWidgetsPop(6, motion, reducedMotion, M, top, full, 136f) { DotMatrixWidgetsMap() }
            DotMatrixWidgetsPop(1, motion, reducedMotion, M, top + 136 + G, half, half) { DotMatrixWidgetsWeather() }
            repeat(4) { k ->
                DotMatrixWidgetsPop(2 + k, motion, reducedMotion, M + half + G + (k % 2) * (icon + G), top + 136 + G + (k / 2) * (icon + G), icon, icon) {
                    DotMatrixWidgetsIcon(k)
                }
            }
            DotMatrixWidgetsPop(0, motion, reducedMotion, M, top + 136 + G + half + G, full, 230f) {
                DotMatrixWidgetsCalendar(motion, reducedMotion)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = M.dp, end = M.dp, bottom = (safeBottom + 18).dp)) {
            DotMatrixWidgetsText("Your stack is ready", 26f, FontWeight.Black, tracking = -0.6f)
            DotMatrixWidgetsText("Hold any tile to resize it, or drag it somewhere new.", 15f, FontWeight.Normal,
                Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp), lineHeight = 21f)
        }
    }
}

@Composable
private fun DotMatrixWidgetsText(
    text: String, size: Float, weight: FontWeight, color: Color = Color.White,
    modifier: Modifier = Modifier, tracking: Float = 0f, lineHeight: Float = size * 1.2f,
) {
    Text(text, modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight,
        letterSpacing = tracking.sp, lineHeight = lineHeight.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun DotMatrixWidgetsPop(
    n: Int, motion: DotMatrixWidgetsClock, reduced: Boolean,
    x: Float, y: Float, width: Float, height: Float, content: @Composable () -> Unit,
) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp).graphicsLayer {
        val s = shownAt(n, if (reduced) 0.0 else motion.time)
        alpha = (s * 2.5f).coerceIn(0f, 1f)
        cameraDistance = 700f * density
        translationY = 40f * (1 - s.coerceIn(0f, 1f)) * density
        rotationX = 40f * (1 - s)
        rotationY = -22f * (1 - s)
        scaleX = 0.2f + 0.8f * s
        scaleY = scaleX
    }) { content() }
}

@Composable
private fun DotMatrixWidgetsMap() {
    val river = remember { PathParser().parsePathString("M0 96 C80 80 140 120 220 98 S320 70 347 82 L347 104 C300 96 250 126 200 120 S80 104 0 116 Z").toPath() }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Color(0xFFEEF0EC))) {
        Canvas(Modifier.fillMaxSize()) {
            // Preserve the SVG's default xMidYMid meet, including on a wider Android screen.
            val s = minOf(size.width / 347, size.height / 136)
            translate((size.width - 347 * s) / 2, (size.height - 136 * s) / 2) {
                scale(s, s, Offset.Zero) {
                    drawRect(Color(0xFFEEF0EC), size = Size(347f, 136f))
                    drawPath(river, Color(0xFF9CCBF0))
                    drawRoundRect(Color(0xFFCDE8C3), Offset(240f, 14f), Size(80f, 46f), CornerRadius(10f))
                    drawLine(Color.White, Offset(0f, 40f), Offset(347f, 30f), 9f)
                    drawLine(Color.White, Offset(120f, 0f), Offset(150f, 136f), 11f)
                    drawLine(Color.White, Offset(0f, 64f), Offset(347f, 60f), 5f)
                    drawLine(Color(0xFFFFD27A), Offset(210f, 0f), Offset(196f, 136f), 7f)
                    drawCircle(Color(0xFF1F7BFF), 11f, Offset(140f, 52f))
                    drawCircle(Color.White, 11f, Offset(140f, 52f), style = Stroke(3f))
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp).height(26.dp)
            .background(Color.White, CircleShape).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            DotMatrixWidgetsText("12 min to Studio", 12f, FontWeight.Bold, Color(0xFF1C1C1E))
        }
    }
}

@Composable
private fun DotMatrixWidgetsWeather() {
    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF4FA3FF), Color(0xFF1F5BEA))), RoundedCornerShape(26.dp),
    ).padding(14.dp)) {
        DotMatrixWidgetsText("Harbourview ◂", 13f, FontWeight.Bold)
        DotMatrixWidgetsText("18°", 44f, FontWeight.Light, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.padding(top = 4.dp).size(16.dp).background(Color(0xFFFFD34D), CircleShape))
        DotMatrixWidgetsText("Mostly sunny", 13f, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        DotMatrixWidgetsText("H:21° L:12°", 12f, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun DotMatrixWidgetsIcon(kind: Int) {
    val shapes = remember {
        listOf("M0.5 -0.5 L0.08 0.08 L-0.08 -0.08 Z", "M-0.5 0.5 L-0.08 -0.08 L0.08 0.08 Z",
            "M-0.85 -0.5 L0 0.15 L0.85 -0.5",
            "M-0.2 0.6 L-0.2 -0.6 L0.6 -0.8 L0.6 0.4 M-0.2 0.6 A0.25 0.25 0 1 1 -0.21 0.59 M0.6 0.4 A0.25 0.25 0 1 1 0.59 0.39"
        ).map { PathParser().parsePathString(it).toPath() }
    }
    Box(Modifier.fillMaxSize().background(if (kind == 3) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp)) {
            translate(size.width / 2, size.height / 2) {
                scale(size.width / 2, size.height / 2, Offset.Zero) {
                    when (kind) {
                        0 -> {
                            drawCircle(Color(0xFFD7D9DE), 0.85f, Offset.Zero, style = Stroke(0.08f))
                            drawPath(shapes[0], Color(0xFFE8344A))
                            drawPath(shapes[1], Color(0xFF2F6BFF))
                        }
                        1 -> {
                            drawRoundRect(Color(0xFF1C1C1E), Offset(-0.85f, -0.55f), Size(1.7f, 1.2f), CornerRadius(0.25f))
                            drawCircle(Color.White, 0.38f, Offset(0f, 0.05f))
                            drawCircle(Color(0xFF1C1C1E), 0.24f, Offset(0f, 0.05f))
                        }
                        2 -> {
                            drawRoundRect(Color(0xFF2F80FF), Offset(-0.85f, -0.6f), Size(1.7f, 1.2f), CornerRadius(0.2f))
                            drawPath(shapes[2], Color.White, style = Stroke(0.12f))
                        }
                        3 -> drawPath(shapes[3], Color(0xFFFF4F7B), style = Stroke(0.14f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.dotMatrixWidgetsDays(time: Double) {
    repeat(3) { col -> drawCircle(Color(0xFF1E1E22), 15.dp.toPx(), Offset((35 + col * 44).dp.toPx(), 65.dp.toPx())) }
    for (day in 1..31) {
        val index = day + 2
        val streak = day in 12..14
        val on = when {
            streak -> !(time >= 2.3 && time < 2.7 + (day - 12) * 0.2 || time >= 1.0 && time < 2.3)
            day == 15 -> !(time >= 2.3 && time < 3.5)
            else -> false
        }
        val color = when {
            on && streak -> Color(0xFF43D86B)
            on && day == 15 -> Color(0xFFFF3B4E)
            else -> Color(0xFF2E2E33)
        }
        drawCircle(color, 15.dp.toPx(), Offset((35 + (index % 7) * 44).dp.toPx(), (65 + (index / 7) * 34).dp.toPx()))
    }
}

@Composable
private fun DotMatrixWidgetsCalendar(motion: DotMatrixWidgetsClock, reduced: Boolean) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D), RoundedCornerShape(30.dp))) {
        Canvas(Modifier.fillMaxSize()) { dotMatrixWidgetsDays(if (reduced) 0.0 else motion.time) }
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            DotMatrixWidgetsText("OCTOBER", 18f, FontWeight.Light, Color(0xFFE8E8EC), tracking = 1f)
            Spacer(Modifier.weight(1f))
            DotMatrixWidgetsText("2026", 22f, FontWeight.Light, Color(0xFFE8E8EC))
        }
        for (day in 1..31) {
            val index = day + 2
            val hot = day in 12..15
            Box(Modifier.offset((20 + (index % 7) * 44).dp, (50 + (index / 7) * 34).dp).size(30.dp), contentAlignment = Alignment.Center) {
                DotMatrixWidgetsText(day.toString(), 12f, if (hot) FontWeight.ExtraBold else FontWeight.SemiBold,
                    if (hot) Color(0xFF0B0B0D) else Color(0xFFC9C9CF))
            }
        }
    }
}
