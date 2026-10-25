package com.ngocdevv.nativecook.blocks.gestures.zipperunlock

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
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
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Zipper Unlock — the lock screen of "Pocket", a private journal: a denim cover with a brass zipper
 down the middle. Pull the tab down and the teeth part in a V above the slider while the two
 halves roll back at their edges, showing the journal underneath. The loop is in
 ZipperUnlockMotion.kt (LOOP = 9 s).

 Touch: the tab follows the finger (the slider rides the finger's height, rubber-banded past both
 stops); the tab points at the finger on a stiff spring (ω 24, ζ 0.9) and swings free as a
 pendulum when let go (ω 7, ζ 0.16). Released past 70 % (or flicked so its projection passes it)
 the slider springs to the bottom (ω 13, ζ 0.82) and the halves swing open in 0.6 s (ease-out);
 short of it, it zips back up on the same spring. A tick at 70 %, a light click when the halves
 let go and when the zipper closes. 2 s after the last touch (or on the lock button) the halves
 come back in 0.35 s and the slider rises in 0.55 s, then the loop restarts. Reduced motion: no
 loop, critically damped springs, the halves fade open instead of swinging.
*/

private const val OPEN_FOR = 0.6f
private const val CLOSE_HALVES_FOR = 0.35f
private const val CLOSE_SLIDER_FOR = 0.55f
internal val ZipperBackground = Color(0xFF17243F)

/** Everything the frame loop and the finger share; [frame] is the one state the drawing reads. */
private class ZipState {
    var clock = 0f
    var mode = 0 // 0 loop, 1 dragging, 2 settling, 3 closing
    var p = 0f
    var pV = 0f
    var o = 0f
    var oLin = 0f
    var lift = 0f
    var angle = 0f
    var angleV = 0f
    var since = -1f
    var target = 0f
    var idle = 0f
    var ret = 0f
    var fromP = 0f
    var fromO = 0f
    var startYs = 0f
    var fingerX = 0f
    var fingerY = 0f
    var grabbing = false
    var armed = false
    val script = FloatArray(5)
    val frame = mutableFloatStateOf(0f)
    val wake = mutableIntStateOf(0)
}

private fun step(s: ZipState, dt: Float, auto: Boolean, reduced: Boolean, zip: ZipGeometry, width: Float, feel: (Int) -> Unit) {
    val m = s.mode
    if (m == 0) {
        if (!auto) {
            s.p = 0f; s.o = 0f; s.lift = 0f; s.angle = 0f; s.since = -1f
            return
        }
        val t = (s.clock + dt) % LOOP
        s.clock = t
        zipScript(t, s.script)
        s.p = s.script[0]
        s.o = s.script[1]
        s.lift = s.script[2]
        s.angle = s.script[3]
        s.since = s.script[4]
        return
    }
    // The pull tab: aims at the finger while held, a free pendulum otherwise.
    val ys = sliderY(zip, s.p)
    val sx = zip.cx + (width / 2 + 70) * s.o
    var aim = 0f
    var w = 7f
    var z = if (reduced) 1f else 0.16f
    if (m == 1) {
        aim = zClamp(atan2(-(s.fingerX - sx), max(18f, s.fingerY - ys - ZIP_TAB_PIVOT)), -1.1f, 1.1f)
        w = 24f
        z = 0.9f
    }
    val acc = -w * w * (s.angle - aim) - 2 * z * w * s.angleV
    s.angleV += acc * dt
    s.angle += s.angleV * dt
    s.lift += ((if (m == 1) 1f else 0f) - s.lift) * min(1f, dt * 12)

    if (m == 2) {
        val goal = s.target
        val sw = 13f
        val sz = if (reduced) 1f else 0.82f
        val a = -sw * sw * (s.p - goal) - 2 * sz * sw * s.pV
        s.pV += a * dt
        s.p += s.pV * dt
        if (goal == 1f) s.p = min(s.p, 1f)
        if (goal == 1f && s.p > 0.985f && s.oLin < 1f) {
            if (s.oLin == 0f) feel(1)
            s.oLin = min(1f, s.oLin + dt / OPEN_FOR)
            s.o = zEaseOut(s.oLin)
        }
        if (goal == 0f && s.armed && s.p < 0.01f) {
            s.armed = false
            feel(1)
        }
        if (s.o > 0f) s.since = if (s.since < 0f) 0f else s.since + dt
        val settled = abs(s.p - goal) < 0.004f && abs(s.pV) < 0.03f && (goal == 0f || s.oLin >= 1f)
        if (settled) s.idle += dt
        if (s.idle > ZIP_IDLE_BEFORE_RETURN) {
            s.mode = 3
            s.ret = 0f
            s.fromP = s.p
            s.fromO = s.o
        }
    } else if (m == 3) {
        val r = s.ret + dt
        s.ret = r
        s.o = s.fromO * (1 - zEaseInOut(r / CLOSE_HALVES_FOR))
        val k = if (s.fromO > 0f) CLOSE_HALVES_FOR else 0f
        s.p = s.fromP * (1 - zEaseInOut((r - k) / CLOSE_SLIDER_FOR))
        s.since = if (s.o <= 0.001f) -1f else s.since + dt
        if (r >= k + CLOSE_SLIDER_FOR) {
            s.mode = 0
            s.clock = 0f
            s.oLin = 0f
            s.o = 0f
            s.p = 0f
        }
    }
}

/** The sprites, paints and paths the cover draws every frame. */
private class ZipPainter(scale: Float) {
    val slider = zipSlider(scale)
    val tab = zipTab(scale)
    val tabShadow = zipTabShadow(scale)
    val stop = zipStop(scale)
    val patch = zipPatch(scale)
    val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val shadow = Path()
    val body = Path()
    val light = Path()
    val halfL = Path()
    val halfR = Path()
    val tapeL = Path()
    val tapeR = Path()
    val stitchL = Path()
    val stitchR = Path()
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.5f, 3f), 0f)

    fun draw(c: android.graphics.Canvas, s: ZipSprite, alpha: Float = 1f) {
        sprite.alpha = (zClamp(alpha, 0f, 1f) * 255).roundToInt()
        c.drawBitmap(s.bitmap, null, s.rect, sprite)
    }
}

/**
 * The lock screen of a private journal: a denim cover with a brass zipper. Pull the tab down and
 * the teeth part, the halves roll back and swing open on the journal underneath.
 *
 * @param autoPlay plays the 9 s demo: grab, unzip, open the journal, zip back up.
 */
@Composable
fun ZipperUnlock(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { ZipState() }
    val painter = remember(screen.density) { ZipPainter(min(3f, screen.density)) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(ZIP_DENIM_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val zip = remember(width, height, safeTop, safeBottom) { makeZip(width, height, safeTop, safeBottom) }
        val range = zip.ys1 - zip.ys0
        val patchX = width * 0.25f
        val patchY = height * 0.34f

        LaunchedEffect(auto) {
            state.clock = 0f
            state.mode = 0
        }
        val wake = state.wake.intValue
        LaunchedEffect(auto, reduced, wake, zip) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60 else min((now - last) / 1e9f, 1f / 20)
                    last = now
                    step(state, dt, auto, reduced, zip, width) { kind ->
                        view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                    state.frame.floatValue += dt
                }
                if (!auto && state.mode == 0) break
            }
        }

        ZipperUnlockJournal(
            since = { state.frame.floatValue; state.since },
            onLock = {
                if (state.mode == 2 && state.o > 0f) {
                    state.mode = 3
                    state.ret = 0f
                    state.fromP = state.p
                    state.fromO = state.o
                    state.wake.intValue += 1
                }
            },
        )

        // The cover: denim, patch, teeth and the metal, one canvas redrawn every frame.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    state.frame.floatValue
                    // Under reduced motion the cover fades instead of swinging.
                    alpha = if (reduced) 1 - state.o else 1f
                },
        ) {
            state.frame.floatValue
            scale(density, density, pivot = Offset.Zero) {
                val o = if (reduced) 0f else state.o
                val p = state.p
                val ys = sliderY(zip, p)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", width, height)
                    shader.setFloatUniform("zip", zip.yTop, ys, zip.yEnd, zip.gapMax)
                    shader.setFloatUniform("open", o, 6 + 22 * state.o + 6 * zSmooth(p * 3))
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    fallbackDenim(painter, zip, ys, o, width, height)
                }
                val sliderX = zip.cx + (width / 2 + 70) * o
                val lift = state.lift
                val angleDeg = state.angle * 180f / PI.toFloat()
                buildZipTeeth(zip, ys, o, painter.shadow, painter.body, painter.light)
                drawIntoCanvas { cv ->
                    val c = cv.nativeCanvas
                    c.save()
                    c.translate(patchX - gapAt(zip, patchY, ys, o), patchY)
                    c.rotate(-0.04f * 180f / PI.toFloat())
                    painter.draw(c, painter.patch)
                    c.restore()
                }
                drawPath(painter.shadow, Color(4 / 255f, 8 / 255f, 22 / 255f, 0.5f))
                drawPath(painter.body, Color(0xFFC89A4C))
                drawPath(painter.body, Color(0xFF6B4A1B), style = Stroke(width = 0.6f))
                drawPath(painter.light, Color(250 / 255f, 228 / 255f, 168 / 255f, 0.85f))
                drawIntoCanvas { cv ->
                    val c = cv.nativeCanvas
                    c.save()
                    c.translate(zip.cx - gapAt(zip, zip.yEnd, ys, o) + 1, zip.yEnd)
                    painter.draw(c, painter.stop)
                    c.restore()
                    c.save()
                    c.translate(sliderX, ys)
                    painter.draw(c, painter.slider)
                    c.restore()
                    c.save()
                    c.translate(sliderX + 2 + 5 * lift, ys + ZIP_TAB_PIVOT + 3 + 7 * lift)
                    c.rotate(angleDeg)
                    painter.draw(c, painter.tabShadow, 0.45f - 0.15f * lift)
                    c.restore()
                    c.save()
                    c.translate(sliderX, ys + ZIP_TAB_PIVOT)
                    c.rotate(angleDeg)
                    val k = 1 + 0.06f * lift
                    c.scale(k, k)
                    painter.draw(c, painter.tab)
                    c.restore()
                }
            }
        }

        BasicText(
            "Pull the tab down to open",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 34).dp)
                .graphicsLayer {
                    state.frame.floatValue
                    alpha = (1 - zSmooth(state.p * 8)) * (1 - state.o)
                },
            style = TextStyle(
                color = Color(236 / 255f, 226 / 255f, 206 / 255f, 0.62f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            ),
        )

        // The pull's hit area rides with the slider (moved in layout, not recomposed).
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Box(
            Modifier
                .offset {
                    state.frame.floatValue
                    val o = if (reduced) 0f else state.o
                    val sliderX = zip.cx + (width / 2 + 70) * o
                    IntOffset(((sliderX - 55) * density).roundToInt(), ((sliderY(zip, state.p) - 22) * density).roundToInt())
                }
                .size(110.dp, 130.dp)
                .onPlaced { coords[0] = it }
                .semantics { contentDescription = "Zipper pull. Drag down to open the journal." }
                .pointerInput(state, zip) {
                    fun rubber(raw: Float): Float {
                        if (raw < 0f) {
                            val over = -raw * range
                            return -over / (1 + over / 60) / range
                        }
                        if (raw > 1f) {
                            val over = (raw - 1) * range
                            return 1 + over / (1 + over / 40) / range
                        }
                        return raw
                    }
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The tab can't be caught while the halves are swinging open.
                        if (state.o > 0.02f) {
                            state.grabbing = false
                            return@awaitEachGesture
                        }
                        fun absolute(local: Offset): Offset = (coords[0]?.positionInRoot() ?: Offset.Zero) + local
                        val start = absolute(down.position)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, start)
                        state.grabbing = true
                        if (state.mode == 0) {
                            state.angleV = 0f
                            state.pV = 0f
                        }
                        state.mode = 1
                        state.startYs = sliderY(zip, state.p)
                        state.fingerX = start.x / density
                        state.fingerY = start.y / density
                        state.wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val at = absolute(change.position)
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, at)
                                val before = state.p
                                val next = rubber((state.startYs + (at.y - start.y) / density - zip.ys0) / range)
                                state.p = next
                                state.pV = tracker.calculateVelocity().y / density / range
                                state.fingerX = at.x / density
                                state.fingerY = at.y / density
                                if ((before - ZIP_OPEN_AT) * (next - ZIP_OPEN_AT) < 0f) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (next > 0.05f) state.armed = true
                            }
                        } finally {
                            if (state.grabbing) {
                                state.grabbing = false
                                val projected = state.p + state.pV * 0.15f
                                state.target = if (projected >= ZIP_OPEN_AT) 1f else 0f
                                state.oLin = 0f
                                state.idle = 0f
                                state.mode = 2
                            }
                        }
                    }
                },
        )
    }
}

/**
 * Below Android 13: each half a flat sheet of indigo up to its zipper edge, the dark tape along
 * the edge and one row of gold stitches beside it (no rolled edge, no weave).
 */
private fun DrawScope.fallbackDenim(painter: ZipPainter, zip: ZipGeometry, ys: Float, o: Float, width: Float, height: Float) {
    val halves = arrayOf(painter.halfL, painter.halfR)
    val tapes = arrayOf(painter.tapeL, painter.tapeR)
    val stitches = arrayOf(painter.stitchL, painter.stitchR)
    for (i in 0 until 2) {
        val side = if (i == 0) -1f else 1f
        val half = halves[i]
        val tape = tapes[i]
        val stitch = stitches[i]
        half.rewind()
        tape.rewind()
        stitch.rewind()
        val outer = if (side < 0f) -1f else width + 1
        half.moveTo(outer, -1f)
        var y = -1f
        var first = true
        while (true) {
            val edge = zip.cx + side * gapAt(zip, y, ys, o)
            half.lineTo(edge, y)
            if (first) {
                tape.moveTo(edge - side * 5.5f, y)
                stitch.moveTo(edge - side * 15f, y)
                first = false
            } else {
                tape.lineTo(edge - side * 5.5f, y)
                stitch.lineTo(edge - side * 15f, y)
            }
            if (y >= height + 1) break
            y = min(height + 1, y + 4f)
        }
        half.lineTo(outer, height + 1)
        half.close()
        drawPath(
            half,
            Brush.linearGradient(
                0f to Color(0.150f, 0.235f, 0.385f), 0.55f to Color(0.120f, 0.190f, 0.320f), 1f to Color(0.070f, 0.115f, 0.205f),
                start = Offset(0f, 0f), end = Offset(width * 0.4f, height),
            ),
        )
        drawPath(tape, Color(0.075f, 0.090f, 0.130f), style = Stroke(width = 11f))
        drawPath(stitch, Color(0.86f, 0.60f, 0.28f, 0.9f), style = Stroke(width = 1.2f, pathEffect = painter.dash))
    }
}
