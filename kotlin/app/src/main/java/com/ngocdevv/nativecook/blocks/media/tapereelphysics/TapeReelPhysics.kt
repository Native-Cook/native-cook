package com.ngocdevv.nativecook.blocks.media.tapereelphysics

import android.content.Context
import android.graphics.Canvas
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.blocks.media.tapereelphysics.TapeReelPhysicsArt.Glyph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Tape Reel Physics — a cassette deck whose tape behaves like tape. A port of the SwiftUI block
 (TapeReelPhysics.swift); the numbers are the same.

 Look: dark walnut (#17140F) with a warm glow behind a big cream cassette (the screen's width
 less 32 dp, 1.58 : 1): a label with a red-and-amber band, side A, "Night Drive Mix" in a marker
 hand (Gochi Hand) and C60; a black glass window with two brown tape packs on white six-toothed
 hubs; the tape running from each pack down round two steel guides and straight across the head
 opening, with its felt pad. Under it six transport keys (rewind, play, fast forward, pause,
 stop, record), two backlit VU meters, the tape's time and progress, and a hint.

 Mechanics, all closed form (TapeReelPhysicsModel.kt):
   Reels    the packs' radii follow the tape played s (r_supply² + r_takeup² is constant, 20 to
            50 dp); each hub's angle is ∫ feed ds / r, so a fuller pack turns slower, and both
            return exactly when the tape is rewound. Spinning past 10 rad/s the teeth smear into rings.
   Slack    the runs from the packs to the guides bow out 7 dp when the transport stops and pull
            straight when it moves: a damped spring (γ 5, ω 16 rad/s) driven by steps, so it sags
            with a wobble and tightens with a twang.
   Span     pulled, the tape between the guides is a triangle through the finger; let go, it
            rings as a string (its first four modes, 5 Hz fundamental, dying in about a second);
            winding fast makes it hum (18 and 27 Hz standing waves). Pulling turns both hubs a hair.

 autoPlay timeline (LOOP = 9 s), resting stopped at 12:41 with the slack bowed:
   0.6         PLAY: the tape tightens with a twang, hubs turn, meters wake.
   1.45 – 2.35 a finger comes down on the tape at 38 %, pulls it 22 dp (1.6 – 1.95, ease-out),
               holds, and lets go at 2.1; the span rings.
   3.2         FAST FORWARD: 320×, the packs trade size, the hubs blur, the span hums.
   4.6         STOP: the slack sags with a wobble.
   5.2 – 7.4   REWIND back to 12:41 (ease in-out).
   7.4         STOP; rest to 9.0, the same frame as 0.0.

 Touch: tap a key (a click; play, fast forward, rewind and pause latch, stop and record flash);
 drag the tape between the guides to pull it, and let go to pluck it. 2 s after the last touch
 the tape winds back to 12:41 (1 s), stops, and the loop starts again 1 s later.
 Reduced motion (animator duration scale 0): no loop, no ringing and no hum; the keys and the
 pull still work.
 */

private typealias M = TapeReelPhysicsModel
private typealias A = TapeReelPhysicsArt

private const val LABEL_FONT = "tapereelphysics/tapereelphysics_GochiHand-Regular.ttf"
private val CREAM = argb(0xF1E8D6)
private val MUTED = argb(0x9B907D)

private fun nowSeconds() = System.nanoTime() / 1e9

private class TapeController(val reduced: Boolean, autoPlay: Boolean) {
    val origin = nowSeconds()
    /** The loop's start on the block's clock (seconds since it appeared), or null. */
    var autoStart by mutableStateOf(if (autoPlay && !reduced) 0.0 else null)
    /** A timeline driven by touch, on the block's clock. */
    var segments by mutableStateOf(listOf(TapeReelPhysicsModel.Segment(0.0, TapeReelPhysicsModel.Mode.Stop, M.HOME)))
    var sagBase by mutableDoubleStateOf(M.SLACK)
    var steps by mutableStateOf(listOf<Pair<Double, Double>>())
    var pluck by mutableStateOf<TapeReelPhysicsModel.Pluck?>(null)
    var flash by mutableStateOf(false)
    var frame by mutableDoubleStateOf(0.0)
    var resume: Job? = null

    fun now() = nowSeconds() - origin

    /** The frame at a time, and the time its pluck and hum are measured on. */
    fun frame(t: Double): Pair<TapeReelPhysicsModel.Frame, Double> {
        val start = autoStart
        if (start != null && t >= start && !reduced) {
            val lt = (t - start) % M.LOOP
            return M.auto(lt) to lt
        }
        return M.frame(t, segments, sagBase, steps, pluck) to t
    }

    /** Takes the tape over from the loop (if it is playing) into the touch timeline. */
    fun takeOver(): Double {
        resume?.cancel()
        val t = now()
        val start = autoStart
        if (start != null && t >= start) {
            val lt = (t - start) % M.LOOP
            val f = M.auto(lt)
            val p = f.pluck
            pluck = p?.let { TapeReelPhysicsModel.Pluck(it.x, it.d, it.releasedAt?.let { r -> t - (lt - r) } ?: t) }
            val target = if (M.moving(f.mode)) 0.0 else M.SLACK
            segments = listOf(TapeReelPhysicsModel.Segment(t, f.mode, f.s))
            sagBase = f.sag
            steps = listOf(t to target - f.sag)
        }
        autoStart = null
        // Fold settled steps into the base.
        sagBase += steps.filter { t - it.first > 3 }.sumOf { it.second }
        steps = steps.filter { t - it.first <= 3 }
        if (segments.size > 6) segments = segments.takeLast(6)
        return t
    }
}

private class TapeLayout(val w: Float, val h: Float, val top: Float, bottom: Float) {
    val k = (w - 32) / A.W
    val ch = A.H * k
    val keyW = (w - 32 - 40) / 6
    val meterW = (w - 32 - 14) / 2
    val meterH = 160f
    val hintY = h - max(bottom, 16f) - 18
    /** The header takes the top 76 dp; the rest is shared by four equal gaps. */
    private val gap = max(20f, (hintY - 28 - (top + 76) - (ch + 56 + meterH + 24)) / 4)
    val x0 = 16f
    val y0 = top + 76 + gap
    val keysY = y0 + ch + gap
    val metersY = keysY + 56 + gap
    val progressY = metersY + meterH + gap
    fun sx(x: Float) = x0 + x * k
    fun sy(y: Float) = y0 + y * k
}

@Composable
fun TapeReelPhysics(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { TapeController(reduced, autoPlay) }
    val art = remember { A(Typeface.createFromAsset(context.assets, LABEL_FONT)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        while (true) withFrameNanos { c.frame = it / 1e9 - c.origin }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val t = c.now()
            val f = M.frame(t, c.segments, c.sagBase, c.steps, c.pluck)
            val current = c.segments.last().mode
            c.segments = c.segments + TapeReelPhysicsModel.Segment(t, TapeReelPhysicsModel.Mode.Seek, f.s, M.HOME, 1.0) + TapeReelPhysicsModel.Segment(t + 1, TapeReelPhysicsModel.Mode.Stop, M.HOME)
            var steps = c.steps
            if (!M.moving(current)) steps = steps + (t to -M.SLACK)
            c.steps = steps + ((t + 1) to M.SLACK)
            c.autoStart = t + 2
        }
    }

    fun press(glyph: Glyph) {
        val t = c.takeOver()
        view.performHapticFeedback(if (glyph == Glyph.FF || glyph == Glyph.Rew) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
        val f = M.frame(t, c.segments, c.sagBase, c.steps, c.pluck)
        val current = c.segments.last().mode
        val mode = when (glyph) {
            Glyph.Rew -> TapeReelPhysicsModel.Mode.Rew
            Glyph.Play -> TapeReelPhysicsModel.Mode.Play
            Glyph.FF -> TapeReelPhysicsModel.Mode.FF
            Glyph.Pause -> if (current == TapeReelPhysicsModel.Mode.Pause) TapeReelPhysicsModel.Mode.Play else TapeReelPhysicsModel.Mode.Pause
            Glyph.Stop -> TapeReelPhysicsModel.Mode.Stop
            Glyph.Rec -> {
                c.flash = true
                scope.launch {
                    delay(250)
                    c.flash = false
                }
                scheduleResume()
                return
            }
        }
        if (mode != current) {
            c.segments = c.segments + TapeReelPhysicsModel.Segment(t, mode, f.s)
            val old = if (M.moving(current)) 0.0 else M.SLACK
            val new = if (M.moving(mode)) 0.0 else M.SLACK
            if (old != new) c.steps = c.steps + (t to new - old)
        }
        scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val l = remember(maxWidth, maxHeight, top, bottom) { TapeLayout(maxWidth.value, maxHeight.value, top, bottom) }
        val glyphs = Glyph.entries

        ComposeCanvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Cassette deck: rewind, play, fast forward, pause, stop and record keys; drag the tape to pluck it" }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val x = down.position.x / density
                        val y = down.position.y / density
                        val spanX0 = l.sx(A.SPAN_X0)
                        val spanX1 = l.sx(A.SPAN_X1)
                        val spanY = l.sy(A.SPAN_Y)
                        val onSpan = x >= spanX0 - 10 && x <= spanX1 + 10 && y >= spanY - 40 && y <= spanY + 60
                        val keyIndex = if (y >= l.keysY && y <= l.keysY + 60) {
                            val i = floor((x - l.x0) / (l.keyW + 8)).toInt()
                            if (i in 0..5 && x - l.x0 - i * (l.keyW + 8) <= l.keyW) i else -1
                        } else -1
                        if (onSpan) {
                            c.takeOver()
                            var lastNotch = 0
                            fun pull(px: Float, py: Float) {
                                val u = ((px - spanX0) / (spanX1 - spanX0)).toDouble().coerceIn(0.08, 0.92)
                                val d = ((py - spanY) / l.k).toDouble().coerceIn(-36.0, 48.0)
                                c.pluck = TapeReelPhysicsModel.Pluck(u, d, null)
                                val notch = (abs(d) / 8).toInt()
                                if (notch != lastNotch) {
                                    lastNotch = notch
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            pull(x, y)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pull(change.position.x / density, change.position.y / density)
                                change.consume()
                            }
                            c.pluck?.let { c.pluck = it.copy(releasedAt = c.now()) }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            scheduleResume()
                        } else if (keyIndex >= 0) {
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > 12 * density) moved = true
                                if (!change.pressed) break
                            }
                            if (!moved) press(glyphs[keyIndex])
                        }
                    }
                },
        ) {
            val (f, t) = c.frame(c.frame)
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.scale(density, density)
            draw(nc, art, l, f, t, reduced, c.flash)
            nc.restore()
        }
    }
}

private fun draw(cv: Canvas, a: A, l: TapeLayout, f: TapeReelPhysicsModel.Frame, t: Double, still: Boolean, flash: Boolean) {
    val fill = a.fill
    cv.drawColor(argb(0x17140F))
    fill.maskFilter = null
    fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
    fill.shader = RadialGradient(l.w / 2, l.y0 + l.ch / 2, l.w * 0.9f, argb(0x3A2C1C, 0.6), argb(0x3A2C1C, 0.0), Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, l.w, l.h, fill)
    // Vignette: clear inside 0.3 h of its centre, half black at 0.75 h.
    fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
    fill.shader = RadialGradient(
        l.w / 2, l.h * 0.45f, l.h * 0.75f,
        intArrayOf(argb(0x000000, 0.0), argb(0x000000, 0.0), argb(0x000000, 0.5)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
    )
    cv.drawRect(0f, 0f, l.w, l.h, fill)
    fill.shader = null

    // Header.
    a.text(cv, "SIDE A · C60", 20f, l.top + 22, 11f, MUTED, Typeface.create(Typeface.DEFAULT, 600, false), hx = 0f, tracking = 1.4f)
    a.text(cv, "Night Drive Mix", 20f, l.top + 46, 24f, CREAM, Typeface.create(Typeface.DEFAULT, 600, false), hx = 0f)
    counter(cv, a, RectF(l.w - 16 - 84, l.top + 22, l.w - 16, l.top + 56), f.s / 4)

    // Cassette, on its shadow.
    fill.color = argb(0x000000, 0.6)
    fill.maskFilter = A.blur(14f)
    cv.drawOval(RectF(l.x0 + 0.06f * (l.w - 32), l.y0 + l.ch - 8, l.x0 + 0.94f * (l.w - 32), l.y0 + l.ch + 18), fill)
    fill.maskFilter = null
    cv.save()
    cv.translate(l.x0, l.y0)
    cv.scale(l.k, l.k)
    a.cassette(cv, f, t, still)
    cv.restore()

    // The scripted finger.
    if (f.fingerAlpha > 0.01) {
        val px = l.sx(A.SPAN_X0 + f.fingerX.toFloat() * (A.SPAN_X1 - A.SPAN_X0))
        val py = l.sy(A.SPAN_Y + f.fingerD.toFloat())
        cv.drawCircle(px, py, 20f, a.fill(argb(0xFFFFFF, 0.2 * f.fingerAlpha)))
        cv.drawCircle(px, py, 19.25f, a.stroke(argb(0xFFFFFF, 0.55 * f.fingerAlpha), 1.5f))
    }

    // Keys.
    Glyph.entries.forEachIndexed { i, g ->
        val pressed = f.key == i || (g == Glyph.Rec && flash)
        val x = l.x0 + i * (l.keyW + 8)
        a.key(cv, g, RectF(x, l.keysY, x + l.keyW, l.keysY + 56), pressed)
    }

    // Meters.
    for (ch in 0 until 2) {
        val level = f.meters * min(1.0, max(0.0, M.level(t, ch)))
        val x = l.x0 + ch * (l.meterW + 14)
        a.meter(cv, RectF(x, l.metersY, x + l.meterW, l.metersY + l.meterH), level, f.meters, if (ch == 0) "L" else "R")
    }

    // Progress.
    val mono = Typeface.create(Typeface.MONOSPACE, 500, false)
    a.text(cv, M.clock(f.s), l.x0, l.progressY, 12f, CREAM, mono, hx = 0f)
    a.text(cv, M.clock(M.TOTAL), l.w - 16, l.progressY, 12f, MUTED, mono, hx = 1f)
    val by = l.progressY + 16
    cv.drawRoundRect(RectF(l.x0, by, l.w - 16, by + 4), 2f, 2f, a.fill(argb(0x3A342B)))
    cv.drawRoundRect(RectF(l.x0, by, l.x0 + (l.w - 32) * (f.s / M.TOTAL).toFloat(), by + 4), 2f, 2f, a.fill(A.AMBER))
    a.text(cv, "Pull the tape, or press a key", l.w / 2, l.hintY, 12f, argb(0x7A705F))
}

/** A four-digit odometer: the last digit rolls with the tape, fading as it leaves the window. */
private fun counter(cv: Canvas, a: A, r: RectF, value: Double) {
    cv.drawRoundRect(r, 8f, 8f, a.fill(argb(0x0E0C09)))
    cv.drawRoundRect(RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f), 8f, 8f, a.stroke(argb(0x3A3225), 1f))
    val n = floor(value).toInt()
    val frac = value - floor(value)
    val digits = "%04d".format(n % 10000)
    val cell = (r.width() - 12) / 4
    val font = Typeface.create(Typeface.MONOSPACE, 600, false)
    for (i in 0 until 4) {
        val x = r.left + 6 + cell * (i + 0.5f)
        if (i == 3) {
            val roll = (22 * M.smoothstep(0.7, 1.0, frac)).toFloat()
            a.text(cv, digits[i].toString(), x, r.centerY() - roll, 17f, argb(0xF1E8D6, 1.0 - roll / 22), font)
            a.text(cv, ((n + 1) % 10).toString(), x, r.centerY() + 22 - roll, 17f, argb(0xF1E8D6, roll / 22.0), font)
        } else {
            a.text(cv, digits[i].toString(), x, r.centerY(), 17f, CREAM, font)
        }
        if (i > 0) {
            val sx = r.left + 6 + cell * i
            cv.drawLine(sx, r.top + 6, sx, r.bottom - 6, a.stroke(argb(0xFFFFFF, 0.08), 1f))
        }
    }
    cv.drawRect(r.left + 1, r.top + 1, r.right - 1, r.centerY(), a.fill(argb(0xFFFFFF, 0.04)))
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
