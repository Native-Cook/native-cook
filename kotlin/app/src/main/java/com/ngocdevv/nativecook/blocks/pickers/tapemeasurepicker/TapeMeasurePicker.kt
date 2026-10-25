package com.ngocdevv.nativecook.blocks.pickers.tapemeasurepicker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Tape Measure Picker — "Fitline · Your height": a height picker that is a real tape measure. Pull
 the yellow tape out of its case and the value under the red hairline is your height; let go and
 it springs to the nearest centimetre; flick it left and it reels in to the minimum with a whip.
 The readout above is a mechanical counter whose units wheel turns with the tape.

 One Canvas (TapeMeasurePickerArt.kt: background and case rendered once into bitmaps, the tape
 drawn per frame from the value, only its visible range). Value and wobble:
 TapeMeasurePickerMotion.kt, analytic in autoPlay (LOOP = 8 s), a spring under the finger. The
 counter wheels move in graphicsLayer; Compose recomposes only when the whole centimetre changes.

 Modes: 0 loop · 1 dragging · 2 released, springing to a centimetre · 3 easing back to 178 cm
 (0.9 s) two seconds after the last touch, then the loop restarts.
 */

private val Background = Color(0xFFEFEBE4)
private val Ink = Color(0xFF1D1B17)
private val InkSoft = Color(0xFF6E685E)
private const val DIGIT_SIZE = 84f
private const val DIGIT_LINE = 92f
private const val DIGIT_W = 52f
private const val WEIGHT_KG = 72
private const val FLICK = -90.0
private const val RETURN_AFTER = 2.0
private const val RETURN_FOR = 0.9
private val Digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private fun nowSeconds() = System.nanoTime() / 1e9

private class TapeState(auto: Boolean) {
    val frame = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    /** The whole centimetre, state for the imperial line and the stats only. */
    val wholeCm = mutableIntStateOf(TAPE_REST.toInt())
    var now = -1.0
    var loopStart = 0.0
    var mode = if (auto) 0 else 2
    var value = TAPE_REST
    var speed = 0.0
    var amp = 0.0
    var phase = 0.0
    // Spring after release.
    var target = TAPE_REST
    var springW = 9.0
    var springZ = 0.72
    var settled = true
    // Drag.
    var dragging = false
    var dragStartX = 0f
    var dragStartValue = 0.0
    var dragLastX = 0f
    var dragLastTime = 0.0
    var dragVel = 0.0
    var lastTouch = 0.0
    var backStart = 0.0
    var backFrom = TAPE_REST
    var detentCm = TAPE_REST.roundToInt()
    var detentTime = 0.0
    val scratch = DoubleArray(1)

    fun syncCm() {
        val cm = value.coerceIn(TAPE_MIN, TAPE_MAX).roundToInt()
        if (wholeCm.intValue != cm) wholeCm.intValue = cm
    }

    fun moving(auto: Boolean) = mode != 2 || !settled || auto || amp > 0.01
}

/** One frame. Returns 1 for a detent tick, 2 for the snap at the end of a spring, 0 otherwise. */
private fun tapeStep(s: TapeState, t0: Double, dt: Double, auto: Boolean, reduced: Boolean): Int {
    var feel = 0
    s.phase = ((t0 - s.loopStart) * TAPE_WAVE_HZ * PI * 2) % (PI * 2 * TAPE_WAVE_HZ * LOOP)
    when (s.mode) {
        0 -> {
            val t = (t0 - s.loopStart).mod(LOOP)
            s.value = tapeLoopValue(t, s.scratch)
            s.speed = s.scratch[0]
            s.amp = tapeLoopWobble(t, s.scratch)
            s.phase = ((t * TAPE_WAVE_HZ) % 1) * PI * 2
            s.syncCm()
            return 0
        }
        // A finger that holds still stops the wobble.
        1 -> s.speed *= exp(-dt / 0.05)
        2 -> if (!s.settled) {
            var v = s.value
            var sp = s.speed
            val h = dt / 4
            repeat(4) {
                sp += (-s.springW * s.springW * (v - s.target) - 2 * s.springZ * s.springW * sp) * h
                v += sp * h
            }
            if (abs(v - s.target) < 0.02 && abs(sp) < 0.3) {
                v = s.target
                sp = 0.0
                s.settled = true
                feel = 2
            }
            s.value = v
            s.speed = sp
        } else if (auto && t0 - s.lastTouch > RETURN_AFTER) {
            s.mode = 3
            s.backStart = t0
            s.backFrom = s.value
        }
        3 -> {
            val k = tapeEaseInOut(tapeSeg(t0 - s.backStart, 0.0, RETURN_FOR))
            s.value = s.backFrom + (TAPE_REST - s.backFrom) * k
            s.speed = 0.0
            if (k >= 1) {
                s.mode = 0
                s.loopStart = t0
            }
        }
    }
    s.amp = if (reduced) 0.0 else max(s.amp * exp(-dt / 0.3), tapeWobbleFromSpeed(abs(s.speed)))
    if (s.amp < 0.005) s.amp = 0.0
    // Detents: a selection tick per whole centimetre under the finger, at most ~12 a second.
    if (s.mode == 1 || s.mode == 2) {
        val cm = s.value.roundToInt()
        if (cm != s.detentCm && t0 - s.detentTime > 0.08) {
            s.detentCm = cm
            s.detentTime = t0
            if (feel == 0) feel = 1
        }
    }
    s.syncCm()
    return feel
}

/** One wheel of the counter: 0–9 and a second 0, rolled by the value (odometer carry). */
@Composable
private fun Wheel(s: TapeState, place: Int) {
    Box(Modifier.size(DIGIT_W.dp, DIGIT_LINE.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    s.frame.intValue
                    val unit = 10.0.pow(place)
                    val q = max(0.0, s.value) / unit
                    val base = floor(q) % 10
                    val frac = q - floor(q)
                    val roll = if (place == 0) frac else (frac * unit - (unit - 1)).coerceIn(0.0, 1.0)
                    translationY = -((base + roll) * DIGIT_LINE).toFloat() * density
                },
        ) {
            for (d in Digits) {
                Text(
                    d,
                    color = Ink,
                    fontSize = DIGIT_SIZE.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    textAlign = TextAlign.Center,
                    style = Tabular.copy(lineHeight = DIGIT_LINE.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)),
                    maxLines = 1,
                    modifier = Modifier.size(DIGIT_W.dp, DIGIT_LINE.dp).wrapContentHeight(),
                )
            }
        }
    }
}

@Composable
private fun Imperial(cm: MutableIntState) {
    Text(tapeFeetInches(cm.intValue), color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = Tabular)
}

@Composable
private fun Stats(cmState: MutableIntState) {
    val cm = cmState.intValue
    val bmi = WEIGHT_KG / ((cm / 100.0) * (cm / 100.0))
    val items = listOf("Weight" to "$WEIGHT_KG kg", "Stride" to "${(cm * 0.415).roundToInt()} cm", "BMI" to String.format(Locale.US, "%.1f", bmi))
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color(0xFF3C2C14), spotColor = Color(0xFF3C2C14))
            .background(Color(0xB8FFFDF8), shape)
            .drawBehind { drawLine(Color(0xCCFFFFFF), Offset(18.dp.toPx(), 0.5f), Offset(size.width - 18.dp.toPx(), 0.5f), strokeWidth = 1.dp.toPx()) }
            .padding(vertical = 14.dp),
    ) {
        items.forEachIndexed { i, (label, v) ->
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind { if (i > 0) drawLine(Color(0x241D1B17), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(v, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
            }
        }
    }
}

/** A press that scales to 0.97 and dims, like the RN Pressables. */
@Composable
private fun rememberPress(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(130), label = "press")
    return press
}

@Composable
fun TapeMeasurePicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(auto) { TapeState(auto) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding().value
    val insetBottom = insets.calculateBottomPadding().value

    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h) { TapeGeo(w, h) }
        val fonts = remember { TapeFonts() }
        val bg = remember(g, density) { tapeRenderBackground(g, density).asImageBitmap() }
        val body = remember(g, density) { tapeRenderCase(g, fonts, density).asImageBitmap() }
        val painter = remember(g) { TapePainter(g, fonts) }

        LaunchedEffect(state, auto, reduced) {
            while (true) {
                withFrameNanos { nanos ->
                    val t0 = nanos / 1e9
                    val dt = if (state.now < 0) 1.0 / 60 else min(t0 - state.now, 1.0 / 30)
                    if (state.now < 0 && state.mode == 0) state.loopStart = t0
                    state.now = t0
                    when (tapeStep(state, t0, dt, auto, reduced)) {
                        1 -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        2 -> haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                    state.frame.intValue++
                }
                if (!state.moving(auto)) {
                    // At rest: sleep until a touch wakes the loop.
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    state.now = -1.0
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            state.frame.intValue
            drawImage(bg)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.tape(c, state.value, state.amp.toFloat(), state.phase.toFloat())
                c.restore()
            }
            drawImage(body)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.hairline(c)
                c.restore()
            }
        }

        // Navigation: back, the onboarding's six steps (three done), Skip.
        Row(
            Modifier
                .offset(y = (top + 6f).dp)
                .padding(start = 16.dp, end = 20.dp)
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val source = remember { MutableInteractionSource() }
            val press = rememberPress(source)
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        val k = 1f - 0.03f * press
                        scaleX = k
                        scaleY = k
                        alpha = 1f - 0.1f * press
                    }
                    .shadow(6.dp, CircleShape, ambientColor = Color(0xFF3C2C14), spotColor = Color(0xFF3C2C14))
                    .background(Color(0xCCFFFDF8), CircleShape)
                    .clickable(source, indication = null, role = Role.Button) {}
                    .semantics { contentDescription = "Back" }
                    .drawBehind {
                        // chevron.left, semibold, 17 dp.
                        val u = 1.dp.toPx()
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val path = Path().apply {
                            moveTo(cx + 3.5f * u, cy - 7.5f * u)
                            lineTo(cx - 4f * u, cy)
                            lineTo(cx + 3.5f * u, cy + 7.5f * u)
                        }
                        drawPath(path, Ink, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    },
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 6) {
                    Box(Modifier.weight(1f).height(4.dp).background(if (i < 3) Ink else Color(0x1A1D1B17), RoundedCornerShape(2.dp)))
                }
            }
            Text("Skip", color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            Modifier
                .offset(y = (top + 62f).dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("STEP 3 OF 6 · BODY", color = Color(0xFFB07D06), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("How tall are you?", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text("Pull the tape out to your height. It tunes your stride and calorie burn.", color = InkSoft, fontSize = 15.sp, lineHeight = 21.sp)
        }

        // The counter: three wheels and the units.
        Row(
            Modifier
                .offset(y = max(top + 174f, g.caseY - DIGIT_LINE - 34f).dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .semantics { contentDescription = "Height" },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.height(DIGIT_LINE.dp)) {
                Row {
                    Wheel(state, 2)
                    Wheel(state, 1)
                    Wheel(state, 0)
                }
                Box(Modifier.width((DIGIT_W * 3).dp).height(16.dp).align(Alignment.TopStart).background(Brush.verticalGradient(listOf(Background, Background.copy(alpha = 0f)))))
                Box(Modifier.width((DIGIT_W * 3).dp).height(16.dp).align(Alignment.BottomStart).background(Brush.verticalGradient(listOf(Background.copy(alpha = 0f), Background))))
            }
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("cm", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Imperial(state.wholeCm)
            }
        }

        // The tape's touch area: from the case to the right edge, 80 dp above and below the tape.
        Box(
            Modifier
                .offset(x = g.caseX.dp, y = (g.tapeY - 80f).dp)
                .width((w - g.caseX).dp)
                .height(160.dp)
                .semantics { contentDescription = "Tape measure. Drag sideways to set your height." }
                .pointerInput(state, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val t = nowSeconds()
                        val x0 = down.position.x / density
                        state.mode = 1
                        state.dragging = true
                        state.dragStartX = x0
                        state.dragStartValue = tapeUnband(state.value)
                        state.dragLastX = x0
                        state.dragLastTime = t
                        state.dragVel = 0.0
                        state.detentCm = state.value.roundToInt()
                        state.detentTime = t
                        state.wake.intValue++
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val x = change.position.x / density
                            val now = nowSeconds()
                            val raw = state.dragStartValue + (x - state.dragStartX) / TAPE_PPCM
                            val dtt = max(1.0 / 240, now - state.dragLastTime)
                            state.dragVel = 0.6 * state.dragVel + 0.4 * ((x - state.dragLastX) / TAPE_PPCM) / dtt
                            state.dragLastX = x
                            state.dragLastTime = now
                            val v = tapeBand(raw)
                            state.speed = (v - state.value) / dtt
                            state.value = v
                        }
                        state.dragging = false
                        val v = state.value
                        val vel = state.dragVel
                        state.lastTouch = nowSeconds()
                        // A fast flick to the left lets the spring reel the tape all the way in.
                        val whip = vel < FLICK && !reduced
                        state.target = if (whip) TAPE_MIN else (v + if (reduced) 0.0 else vel * 0.12).roundToInt().toDouble().coerceIn(TAPE_MIN, TAPE_MAX)
                        state.speed = if (reduced) 0.0 else vel
                        if (whip) { state.springW = 11.0; state.springZ = 0.6 } else { state.springW = if (reduced) 14.0 else 9.0; state.springZ = if (reduced) 1.0 else 0.72 }
                        state.settled = false
                        state.mode = 2
                        state.wake.intValue++
                    }
                },
        )

        Text(
            "Drag the tape · flick left to reel it in",
            color = InkSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (g.tapeY + g.tapeH / 2 + 30f).dp).padding(horizontal = 24.dp).fillMaxWidth(),
        )

        Box(Modifier.offset(y = (g.tapeY + g.tapeH / 2 + 62f).dp).padding(horizontal = 20.dp).fillMaxWidth()) {
            Stats(state.wholeCm)
        }

        val source = remember { MutableInteractionSource() }
        val press = rememberPress(source)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = (max(insetBottom, 16f) + 8f).dp)
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    val k = 1f - 0.03f * press
                    scaleX = k
                    scaleY = k
                    alpha = 1f - 0.1f * press
                }
                .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF140E06), spotColor = Color(0xFF140E06))
                .background(Color(0xFF22201B), RoundedCornerShape(28.dp))
                .drawBehind { drawLine(Color(0x1FFFFFFF), Offset(28.dp.toPx(), 0.5f), Offset(size.width - 28.dp.toPx(), 0.5f), strokeWidth = 1.dp.toPx()) }
                .clickable(source, indication = null, role = Role.Button) {},
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", color = Color(0xFFF7F3EA), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

