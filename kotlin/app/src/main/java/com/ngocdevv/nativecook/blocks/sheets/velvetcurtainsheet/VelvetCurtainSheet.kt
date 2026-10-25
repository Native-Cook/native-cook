package com.ngocdevv.nativecook.blocks.sheets.velvetcurtainsheet

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Velvet Curtain Sheet — "Odeon Lane", tonight's screening of "The Glass Orchard". The seat picker
 is a sheet whose cover is a theatre curtain: two red velvet halves that part from the middle,
 bunching their folds toward the sides, with a gold fringe at the hem. The loop is in
 VelvetCurtainSheetMotion.kt (LOOP = 9 s).

 Touch: drag the curtain sideways to open or close it (the finger's side of the centre sets the
 direction; rubber-banded past both ends; released, it springs to the nearer end with the finger's
 velocity, ω 10, ζ 0.85); the hem trails the rail on its own spring (ω 9, ζ 0.35), so the cloth
 swings. Drag the sheet up or down between its half and full detents (ω 14, ζ 0.86, the target
 chosen from the projected position). Tap the closed curtain to open it, tap a seat to pick it
 (a 0.35 s pop). Light click at a detent or when the curtain lands; a tick per seat. 2 s after
 the last touch the curtain closes, the sheet returns to its half detent (0.8 s, ease-in-out), the
 picks clear behind the curtain and the loop restarts. Reduced motion: no loop, no sway,
 critically damped springs.
*/

private const val RADIUS = 28f
private val Ink = Color(0xFFF3E9E4)
private val InkSoft = Color(243 / 255f, 233 / 255f, 228 / 255f, 0.62f)
private val Gold = Color(0xFFF2C060)
internal val VelvetPage = Color(0xFF140A0F)

/** Everything the frame loop and the finger share; [frame] is the one state the drawing reads. */
private class VelvetState(halfTop: Float) {
    var clock = 0f
    var now = 0f
    var mode = 0 // 0 loop, 1 finger down, 2 released, 3 returning
    var axis = 0 // 1 sheet (vertical), 2 curtain (horizontal)
    var o = 0f
    var oV = 0f
    var oGoal = 0f
    var hem = 0f
    var hemV = 0f
    var top = halfTop
    var topV = 0f
    var topGoal = halfTop
    var bar = 0f
    var press = 1f
    /** Picked seats and the time each was lit. */
    val picked = ArrayList<Pair<Int, Float>>()
    var idle = 0f
    var ret = 0f
    var fromO = 0f
    var fromTop = halfTop
    var start = 0f
    var side = 1f
    /** Seats lit this frame: seat, light 0..1, pop scale, …; rebuilt each frame. */
    var lit = FloatArray(0)
    var litCount = 0
    val frame = mutableFloatStateOf(0f)
    val wake = mutableIntStateOf(0)

    fun takeOver(auto: Boolean, halfTop: Float) {
        if (mode == 0) {
            oV = 0f
            hemV = 0f
            topV = 0f
            topGoal = halfTop
            oGoal = if (o > 0.5f) 1f else 0f
            picked.clear()
            for (k in LOOP_SEATS.indices) if (auto && loopSeat(clock, k) > 0.5f) picked += LOOP_SEATS[k] to now - 1
        }
        idle = 0f
    }

    fun updateLit(auto: Boolean, reduced: Boolean) {
        val need = max(LOOP_SEATS.size, picked.size) * 3
        if (lit.size < need) lit = FloatArray(need)
        litCount = 0
        fun push(seat: Int, a: Float, pop: Float) {
            lit[litCount] = seat.toFloat(); lit[litCount + 1] = a; lit[litCount + 2] = pop
            litCount += 3
        }
        if (mode == 0) {
            if (!auto) return
            for (k in LOOP_SEATS.indices) {
                val a = loopSeat(clock, k)
                if (a > 0.01f) push(LOOP_SEATS[k], a, if (reduced) 1f else popScale(clock - LOOP_SEAT_AT[k]))
            }
            return
        }
        for ((seat, at) in picked) {
            val e = now - at
            push(seat, vClamp(e / 0.15f, 0f, 1f), if (reduced) 1f else popScale(e))
        }
    }
}

private fun step(s: VelvetState, dt: Float, auto: Boolean, reduced: Boolean, halfTop: Float) {
    s.now += dt
    val m = s.mode
    if (m == 0) {
        s.top = halfTop
        if (!auto) {
            s.o = 0f; s.hem = 0f; s.bar = 0f; s.press = 1f
        } else {
            val t = (s.clock + dt) % LOOP
            s.clock = t
            s.o = railAt(t)
            s.hem = hemAt(t)
            s.bar = barAt(t)
            s.press = pressAt(t)
        }
        s.updateLit(auto, reduced)
        return
    }
    val zeta = if (reduced) 1f else 0.85f
    if (!(m == 1 && s.axis == 2) && m != 3) {
        val a = -100 * (s.o - s.oGoal) - 2 * zeta * 10 * s.oV
        s.oV += a * dt
        s.o += s.oV * dt
    }
    if (!(m == 1 && s.axis == 1) && m != 3) {
        val a = -196 * (s.top - s.topGoal) - 2 * (if (reduced) 1f else 0.86f) * 14 * s.topV
        s.topV += a * dt
        s.top += s.topV * dt
    }
    if (m == 3) {
        val r = min(1f, s.ret + dt / VELVET_RETURN_FOR)
        s.ret = r
        val e = vEaseInOut(r)
        s.o = s.fromO * (1 - e)
        s.top = s.fromTop + (halfTop - s.fromTop) * e
        if (r >= 1f) {
            s.picked.clear()
            s.mode = 0
            s.clock = 0f
            s.o = 0f
            s.hem = 0f
            s.updateLit(auto, reduced)
            return
        }
    }
    // The hem trails the rail.
    if (reduced) {
        s.hem = s.o
    } else {
        val a = -SWAY_OMEGA * SWAY_OMEGA * (s.hem - s.o) - 2 * SWAY_ZETA * SWAY_OMEGA * s.hemV
        s.hemV += a * dt
        s.hem += s.hemV * dt
        if (m == 3) s.hem += (s.o - s.hem) * vEaseInOut(s.ret)
    }
    s.bar += ((if (s.o > 0.6f && m != 3) 1f else 0f) - s.bar) * min(1f, dt * 9)
    s.press = 1f
    if (m == 2) {
        s.idle += dt
        if (s.idle > VELVET_IDLE_BEFORE_RETURN) {
            s.mode = 3
            s.ret = 0f
            s.fromO = s.o
            s.fromTop = s.top
        }
    }
    s.updateLit(auto, reduced)
}

private class VelvetArt(val poster: Bitmap, val seats: Bitmap)

/** The paints the sheet draws with every frame. */
private class VelvetPainter {
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(140, 0, 0, 0)
        if (blurOk) maskFilter = BlurMaskFilter(sigmaToRadius(14f), BlurMaskFilter.Blur.NORMAL)
    }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(179, 0xF2, 0xC0, 0x60)
        if (blurOk) maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
    }
    val seat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0xF2, 0xC0, 0x60) }
    val litPath = android.graphics.Path()
    val at = FloatArray(2)
    val clip = Path()
    val halfL = Path()
    val halfR = Path()
}

/**
 * A cinema's seat picker on a sheet whose cover is a red velvet theatre curtain that parts from
 * the middle, its folds bunching toward the sides and its fringed hem swinging after the rail.
 *
 * @param autoPlay plays the 9 s demo: open the curtain, pick C7 and C8, close it.
 */
@Composable
fun VelvetCurtainSheet(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val halfTop = (height - vClamp(height * 0.56f, 380f, 480f)).roundToInt().toFloat()
        val fullTop = safeTop + 12
        val drawH = height - fullTop + 60
        val pitch = rowPitch(height - halfTop)
        val pillY = (PELMET + (height - halfTop)) / 2 - 24
        val state = remember { VelvetState(halfTop) }
        val painter = remember { VelvetPainter() }
        val scale = min(2f, screen.density)
        val art by produceState<VelvetArt?>(null, width, halfTop, pitch, scale) {
            value = withContext(Dispatchers.Default) {
                VelvetArt(buildVelvetPoster(width, halfTop + 60, scale), buildVelvetSeats(width, pitch, scale))
            }
        }
        val curtain = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(VELVET_CURTAIN_AGSL) }.getOrNull() else null
        }
        val curtainBrush = remember(curtain) { curtain?.let { ShaderBrush(it) } }
        val feel: (Int) -> Unit = { kind ->
            view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
        }

        LaunchedEffect(auto, halfTop) {
            state.clock = 0f
            state.mode = 0
            state.top = halfTop
        }
        val wake = state.wake.intValue
        LaunchedEffect(auto, reduced, wake, halfTop) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60 else min((now - last) / 1e9f, 1f / 20)
                    last = now
                    step(state, dt, auto, reduced, halfTop)
                    state.frame.floatValue += dt
                }
                if (!auto && state.mode == 0) break
            }
        }

        val bodyBrush = remember(drawH) {
            Brush.verticalGradient(0f to Color(0xFF22121A), 0.4f to Color(0xFF170C12), 1f to Color(0xFF10080C), startY = 0f, endY = drawH)
        }

        // The page and its poster: never redrawn.
        val a0 = art
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width / density
                    val h = size.height / density
                    val page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { RuntimeShader(VELVET_PAGE_AGSL) }.getOrNull()?.let {
                            it.setFloatUniform("size", w, h)
                            ShaderBrush(it)
                        }
                    } else {
                        null
                    }
                    val base = Brush.verticalGradient(
                        0f to Color(0.090f, 0.050f, 0.065f), 0.5f to Color(0.070f, 0.038f, 0.050f), 1f to Color(0.045f, 0.024f, 0.032f),
                        startY = 0f, endY = h,
                    )
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f),
                        center = Offset(w / 2, h / 2), radius = max(w, h) * 0.62f,
                    )
                    onDrawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            if (page != null) {
                                drawRect(page, Offset.Zero, Size(w, h))
                            } else {
                                drawRect(base, Offset.Zero, Size(w, h))
                                drawRect(vignette, Offset.Zero, Size(w, h))
                            }
                            if (a0 != null) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawBitmap(a0.poster, null, android.graphics.RectF(0f, 0f, w, halfTop + 60), painter.bitmap)
                                }
                            }
                        }
                    }
                },
        )

        // The page's title gives way as the sheet rises over it.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = (height - halfTop + 28).dp)
                .fillMaxWidth()
                .graphicsLayer {
                    state.frame.floatValue
                    alpha = vClamp((state.top - fullTop) / max(1f, halfTop - fullTop), 0f, 1f)
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("ODEON LANE · SCREEN 3", style = TextStyle(color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            BasicText("The Glass Orchard", style = TextStyle(color = Ink, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Tonight 19:40 · 2 h 06 min · 12A", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in listOf("Drama", "Mystery", "Rated 4.6")) {
                    Box(
                        Modifier
                            .height(28.dp)
                            .background(Color(243 / 255f, 233 / 255f, 228 / 255f, 0.08f), RoundedCornerShape(14.dp))
                            .border(0.5.dp, Color(243 / 255f, 233 / 255f, 228 / 255f, 0.16f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(c, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        // The sheet: body, seat map, lit seats, the curtain and the grabber, redrawn every frame.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            state.frame.floatValue
            scale(density, density, pivot = Offset.Zero) {
                val top = state.top
                translate(top = top) {
                    drawIntoCanvas { if (painter.blurOk) it.nativeCanvas.drawRect(8f, -6f, width - 8, 34f, painter.shadow) }
                    val clip = painter.clip
                    clip.rewind()
                    clip.addRoundRect(RoundRect(0f, 0f, width, drawH, CornerRadius(RADIUS)))
                    clipPath(clip) {
                        drawRect(bodyBrush, Offset.Zero, Size(width, drawH))
                        drawIntoCanvas { cv ->
                            val c = cv.nativeCanvas
                            if (a0 != null) c.drawBitmap(a0.seats, null, android.graphics.RectF(0f, 0f, width, velvetSeatsHeight(pitch)), painter.bitmap)
                            val lp = painter.litPath
                            lp.rewind()
                            var k = 0
                            while (k < state.litCount) {
                                seatAt(state.lit[k].toInt(), width, pitch, painter.at)
                                val s = (0.86f + 0.14f * vEaseOut(state.lit[k + 1])) * state.lit[k + 2]
                                val w = SEAT_W * s
                                val h = SEAT_H * s
                                val l = painter.at[0] + SEAT_W / 2 - w / 2
                                val t = painter.at[1] + SEAT_H / 2 - h / 2
                                lp.addRoundRect(l, t, l + w, t + h, 5 * s, 5 * s, android.graphics.Path.Direction.CW)
                                k += 3
                            }
                            if (state.litCount > 0) {
                                if (painter.blurOk) c.drawPath(lp, painter.glow)
                                c.drawPath(lp, painter.seat)
                            }
                        }
                        val hemY = height - top - safeBottom * 0.5f - 4
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && curtain != null && curtainBrush != null) {
                            curtain.setFloatUniform("size", width, drawH)
                            curtain.setFloatUniform("curtain", PELMET, hemY, state.o, state.hem)
                            drawRect(curtainBrush, Offset.Zero, Size(width, drawH))
                        } else {
                            fallbackCurtain(painter, width, hemY, state.o, state.hem)
                        }
                        drawRoundRect(Color(255 / 255f, 226 / 255f, 170 / 255f, 0.45f), Offset(width / 2 - 18, 7f), Size(36f, 4f), CornerRadius(2f))
                    }
                }
            }
        }

        // The touch surface rides with the sheet (moved in layout, not recomposed).
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Box(
            Modifier
                .offset {
                    state.frame.floatValue
                    IntOffset(0, (state.top * density).roundToInt())
                }
                .fillMaxWidth()
                .height(drawH.dp)
                .onPlaced { coords[0] = it }
                .semantics { contentDescription = "Seat picker behind a curtain. Tap or drag the curtain sideways to open it, tap a seat to pick it." }
                .pointerInput(state, width, halfTop, fullTop, pitch, auto) {
                    fun rubber01(v: Float): Float = when {
                        v < 0f -> v / (1 - v * 3) * 0.3f
                        v > 1f -> 1 + (v - 1) / (1 + (v - 1) * 3) * 0.3f
                        else -> v
                    }
                    val tracker = VelocityTracker()
                    val slop = 10.dp.toPx()
                    val fail = 14.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun root(local: Offset) = (coords[0]?.positionInRoot() ?: Offset.Zero) + local
                        val start = root(down.position)
                        val local = down.position
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, start)
                        var axis = 0
                        var tap = true
                        var lastLocal = local
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val at = root(change.position)
                                tracker.addPosition(change.uptimeMillis, at)
                                lastLocal = change.position
                                val dx = at.x - start.x
                                val dy = at.y - start.y
                                if (!change.pressed) break
                                if (axis == 0) {
                                    if (abs(dx) > slop || abs(dy) > slop) tap = false
                                    if (abs(dy) > slop && abs(dx) <= fail) {
                                        axis = 1
                                        state.takeOver(auto, halfTop)
                                        state.mode = 1
                                        state.axis = 1
                                        state.start = state.top
                                        state.wake.intValue += 1
                                    } else if (abs(dx) > slop && abs(dy) <= fail) {
                                        axis = 2
                                        state.takeOver(auto, halfTop)
                                        state.mode = 1
                                        state.axis = 2
                                        state.start = state.o
                                        state.side = if (local.x / density < width / 2) -1f else 1f
                                        state.wake.intValue += 1
                                    }
                                }
                                if (axis != 0) change.consume()
                                val v = tracker.calculateVelocity()
                                if (axis == 1) {
                                    var y = state.start + dy / density
                                    if (y < fullTop) y = fullTop - (fullTop - y) / (1 + (fullTop - y) / 50)
                                    if (y > halfTop) y = halfTop + (y - halfTop) / (1 + (y - halfTop) / 70)
                                    state.top = y
                                    state.topV = v.y / density
                                } else if (axis == 2) {
                                    val span = width * 0.42f
                                    state.o = rubber01(state.start + state.side * dx / density / span)
                                    state.oV = state.side * v.x / density / span
                                }
                            }
                        } finally {
                            when (axis) {
                                1 -> {
                                    val projected = state.top + state.topV * 0.2f
                                    val goal = if (abs(projected - fullTop) < abs(projected - halfTop)) fullTop else halfTop
                                    if (goal != state.topGoal) feel(1)
                                    state.topGoal = goal
                                    state.mode = 2
                                    state.axis = 0
                                    state.idle = 0f
                                }
                                2 -> {
                                    state.oGoal = if (state.o + state.oV * 0.2f > 0.5f) 1f else 0f
                                    feel(1)
                                    state.mode = 2
                                    state.axis = 0
                                    state.idle = 0f
                                }
                                else -> if (tap) {
                                    val x = lastLocal.x / density
                                    val y = lastLocal.y / density
                                    state.takeOver(auto, halfTop)
                                    state.mode = 2
                                    state.wake.intValue += 1
                                    if (state.o < 0.5f) {
                                        if (y > PELMET) {
                                            state.oGoal = 1f
                                            feel(1)
                                        }
                                    } else {
                                        val i = seatIndexAt(x, y, width, pitch)
                                        if (i >= 0 && !isTaken(i)) {
                                            val found = state.picked.indexOfFirst { it.first == i }
                                            if (found >= 0) state.picked.removeAt(found) else state.picked += i to state.now
                                            feel(0)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            // "Choose seats", on the closed curtain.
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = pillY.dp)
                    .graphicsLayer {
                        state.frame.floatValue
                        alpha = 1 - vClamp(state.o * 3, 0f, 1f)
                        scaleX = state.press
                        scaleY = state.press
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .height(48.dp)
                        .cssBoxShadow(Color(40 / 255f, 10 / 255f, 10 / 255f, 0.5f), 1.dp, 2.dp, 24.dp)
                        .cssBoxShadow(Color(20 / 255f, 4 / 255f, 8 / 255f, 0.55f), 12.dp, 28.dp, 24.dp)
                        .drawBehind {
                            val r = CornerRadius(size.height / 2)
                            val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                            clipPath(pill) {
                                drawRect(Color(255 / 255f, 250 / 255f, 230 / 255f, 0.6f))
                                translate(top = 1.dp.toPx()) {
                                    drawRoundRect(
                                        Brush.verticalGradient(0f to Color(0xFFFFE3A0), 0.5f to Color(0xFFF2C060), 1f to Color(0xFFC98E2E), startY = -1.dp.toPx(), endY = size.height - 1.dp.toPx()),
                                        cornerRadius = r,
                                    )
                                }
                            }
                        }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Choose seats", style = TextStyle(color = Color(0xFF3A1A0C), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp))
                }
            }
        }

        // The booking bar.
        val summary by remember {
            derivedStateOf {
                state.frame.floatValue
                val names = ArrayList<String>()
                var k = 0
                while (k < state.litCount) {
                    names += seatName(state.lit[k].toInt())
                    k += 3
                }
                names.sort()
                if (names.isEmpty()) "Tap a seat to pick it" else "${names.size} seat${if (names.size > 1) "s" else ""} · ${names.joinToString(", ")}"
            }
        }
        val price by remember {
            derivedStateOf {
                state.frame.floatValue
                "$" + String.format(java.util.Locale.US, "%.2f", state.litCount / 3 * PRICE.toFloat())
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer {
                    state.frame.floatValue
                    translationY = (1 - state.bar) * 150.dp.toPx()
                    alpha = vClamp(state.bar * 2, 0f, 1f)
                }
                .background(Color(24 / 255f, 12 / 255f, 18 / 255f, 0.96f))
                .drawBehind { drawRect(Color(242 / 255f, 192 / 255f, 96 / 255f, 0.25f), size = Size(size.width, 0.5.dp.toPx())) }
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = (safeBottom + 12).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(summary, maxLines = 1, style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                BasicText(
                    "Tonight 19:40 · Screen 3",
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                )
            }
            BasicText(
                price,
                modifier = Modifier.widthIn(min = 64.dp),
                style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
            )
            Box(Modifier.height(44.dp).background(Gold, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                BasicText("Continue", style = TextStyle(color = Color(0xFF3A1A0C), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/**
 * Below Android 13: the two halves as gathered red velvet (a repeating fold gradient that narrows
 * as they open), their inner edges blended from the rail to the swaying hem, a gold fringe band at
 * the hem and a plain pelmet with a gold trim. No sheen, no scallops.
 */
private fun DrawScope.fallbackCurtain(painter: VelvetPainter, width: Float, hem: Float, rail: Float, hemOpen: Float) {
    val deep = Color(0.200f, 0.020f, 0.050f)
    val mid = Color(0.520f, 0.050f, 0.110f)
    val lit = Color(0.760f, 0.150f, 0.210f)
    val o = vClamp(rail, 0f, 1f)
    for (i in 0 until 2) {
        val path = if (i == 0) painter.halfL else painter.halfR
        path.rewind()
        val left = i == 0
        val outer = if (left) 0f else width
        path.moveTo(outer, PELMET)
        var y = PELMET
        while (true) {
            val drop = vClamp((y - PELMET) / max(hem - PELMET, 1f), 0f, 1f)
            val oy = rail + (hemOpen - rail) * drop.pow(1.5f)
            val e = width * 0.5f * (1 - 0.84f * vClamp(oy, -0.05f, 1.05f))
            path.lineTo(if (left) e else width - e, y)
            if (y >= hem) break
            y = min(hem, y + 6f)
        }
        path.lineTo(outer, hem)
        path.close()
        val e0 = width * 0.5f * (1 - 0.84f * o)
        val fold = max(4f, e0 / 9f)
        drawPath(
            path,
            Brush.horizontalGradient(
                0f to deep, 0.3f to mid, 0.55f to lit, 0.8f to mid, 1f to deep,
                startX = if (left) 0f else width - fold, endX = if (left) fold else width,
                tileMode = TileMode.Repeated,
            ),
        )
        clipPath(path) {
            drawRect(Color(0.98f, 0.80f, 0.42f), Offset(0f, hem - 22), Size(width, 5f))
            drawRect(Color(0.80f, 0.58f, 0.22f, 0.85f), Offset(0f, hem - 17), Size(width, 14f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)), startY = PELMET, endY = hem - 22), Offset(0f, PELMET), Size(width, hem - 22 - PELMET))
        }
    }
    drawRect(Brush.verticalGradient(listOf(Color(0.36f, 0.04f, 0.08f), mid), startY = 0f, endY = PELMET + 4), Offset.Zero, Size(width, PELMET + 4))
    drawRect(Color(0.80f, 0.58f, 0.22f), Offset(0f, PELMET + 1), Size(width, 3f))
}
