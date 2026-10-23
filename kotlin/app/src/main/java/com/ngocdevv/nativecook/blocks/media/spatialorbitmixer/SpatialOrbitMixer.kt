package com.ngocdevv.nativecook.blocks.media.spatialorbitmixer

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.Canvas as ComposeCanvas

/*
 Spatial Orbit Mixer — five instruments as glowing orbs on rings round a listener's head. A port
 of the SwiftUI block (SpatialOrbitMixer.swift); the numbers are the same.

 Look: deep indigo-black (#07070F) with a vignette and still star dust. The floor is seen from
 40° above (rings 72, 112 and 152 dp, white 18 % hairlines; ticks every 10° and 0 · 90 · 180 ·
 270 on the outer one; two dashed arches over the head), scaled to the screen's width. In the
 middle a listener seen from behind (a grey head on shoulders, dark over-ear headphones with
 lime accents) on a soft shadow and a lime under-glow. Five sources orbit: Vocals (lime
 #B6F23A, inner ring), Keys (violet #A98BFF) and Guitar (amber #FFC247) on the middle ring,
 Drums (coral #FF6A5C) and Bass (cyan #35D6FF) outside. Each orb is a lit sphere with a halo, a
 white pictogram, a ring that pulses out on its beat, a comet tail of 20 dots (where it was over
 the last 0.8 s), a stem down to a dot on the floor and a faint wedge of light towards the head.
 Above: "Spatial Mix" and "Head tracking on"; below: a segmented control (Stereo, Spatial,
 Fixed), five level bars, a play button and the track.

 Motion (SpatialOrbitMixerModel.kt): every source turns a whole number of times per loop (Keys
 twice, Vocals and Guitar once, Bass once the other way; Drums sway ±12°), bobs ±6 dp and pulses
 (radius +25 %, a ring out to 2.6×) on its own beat; the orbs are drawn back to front around
 the head.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.2   idle orbits.
   1.0 – 3.6   a finger (a soft white disc) takes the Drums (it grows 30 %, its wedge lights, a
               readout shows level and direction), drags it from the outer ring to the inner ring
               on the left (1.3 – 2.9, ease in-out), holds, and lets go at 3.3.
   4.0 – 5.6   the view turns 25° and back, as if the phone turned.
   6.0 – 6.6   STEREO: every orb slides onto a line in front of the head (ease-out-back).
   7.4 – 8.0   SPATIAL again: they spring back out to their orbits (the Drums to its own ring).
   8.0 – 10.0  idle; the orbits have made whole turns, so 10.0 is the frame at 0.0.

 Touch: drag an orb across the floor (it grows, its wedge lights and a readout follows it, a
 tick haptic every 15° round the head); dropped within 12 dp of a ring it settles onto it
 (0.25 s) and orbits from there. Drag the empty floor to turn the view (0.35° per dp) and tilt
 it (24° – 56°); Fixed stops the turning. Tap Stereo, Spatial or Fixed. 2 s after the last touch
 everything eases back over 1 s and the loop starts again.
 Reduced motion (animator duration scale 0): no loop, orbits, pulses or tails; the sources rest
 on their rings. Dragging and the segments still work.
 */

private typealias M = SpatialOrbitMixerModel

private fun nowSeconds() = System.nanoTime() / 1e9

private fun argb(hex: Long, a: Double = 1.0): Int =
    android.graphics.Color.argb((255 * a.coerceIn(0.0, 1.0)).toInt(), ((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())

/** SwiftUI's blur radius as a Gaussian sigma; Android's radius ≈ (sigma − 0.5) / 0.577. */
private fun blur(sigma: Float) = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

private class OrbitController(val reduced: Boolean, autoPlay: Boolean) {
    val origin = nowSeconds()
    /** On the block's clock: when the loop starts, and when the ease back to it began. */
    var autoStart: Double? = if (autoPlay && !reduced) 0.0 else null
    var returnStart: Double? = null
    var manual = SpatialOrbitMixerModel.Manual()
    var frame by mutableDoubleStateOf(0.0)
    var resume: Job? = null

    fun now() = nowSeconds() - origin

    /** The scene at a clock time, and the clock its beats and bobs follow. */
    fun scene(t: Double): Pair<SpatialOrbitMixerModel.Scene, Double> {
        if (reduced) return M.scene(manual, t) to 0.0
        val a = autoStart
        if (a != null) {
            val lt = M.posmod(t - a, M.LOOP)
            if (t >= a) return M.auto(lt) to lt
            val rs = returnStart
            if (rs != null) return M.mix(M.scene(manual, t), M.auto(lt), M.easeInOut((t - rs) / (a - rs))) to lt
        }
        return M.scene(manual, t) to (t - manual.anchor)
    }

    /** Freezes whatever plays into the touch scene. */
    fun takeOver(t: Double) {
        resume?.cancel()
        val a = autoStart ?: return
        val sc = scene(t).first
        val m = SpatialOrbitMixerModel.Manual()
        m.anchor = a
        for (i in 0 until 5) {
            val o = sc.orbs[i]
            m.radius[i] = o.r
            m.snapFrom[i] = o.r
            m.offset[i] = o.a - M.idleAngle(i, t - a)
        }
        m.yaw = sc.yaw
        m.elevation = sc.elevation
        m.mode = if (sc.segment < 0.5) 0 else 1
        m.stereoFrom = sc.stereo
        m.stereoTo = if (m.mode == 0) 1.0 else 0.0
        m.segmentFrom = sc.segment
        m.segmentTo = if (m.mode == 0) 0.0 else 1.0
        m.modeAt = t
        if (sc.held >= 0) {
            // The loop's finger lets go.
            val i = sc.held
            m.radius[i] = M.ring(m.radius[i])
            m.snapAt[i] = t
            m.dropped = i
            m.droppedAt = t
        }
        manual = m
        autoStart = null
        returnStart = null
    }
}

private class OrbitLayout(val w: Float, val h: Float, val top: Float, bottom: Float) {
    val rowY = h - max(bottom, 16f) - 30
    val segY = rowY - 70
    val sceneTop = top + 76
    val cx = w / 2
    val cy = (sceneTop + segY - 30) / 2 + 16
    val scale = min(1.2f, (w - 30) / 344)
    fun view(s: SpatialOrbitMixerModel.Scene) = SpatialOrbitMixerModel.View(cx.toDouble(), cy.toDouble(), scale.toDouble(), s.yaw, s.elevation)
}

@Composable
fun SpatialOrbitMixer(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { OrbitController(reduced, autoPlay) }
    val painter = remember { OrbitPainter() }
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
            c.returnStart = t
            c.autoStart = t + 1
        }
    }

    fun choose(mode: Int) {
        val t = c.now()
        c.takeOver(t)
        val sc = c.scene(t).first
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val m = c.manual
        m.mode = mode
        m.stereoFrom = sc.stereo
        m.stereoTo = if (mode == 0) 1.0 else 0.0
        m.segmentFrom = sc.segment
        m.segmentTo = mode.toDouble()
        m.modeAt = t
        if (mode == 2) {
            m.yawFrom = sc.yaw
            m.yaw = 0.0
            m.yawAt = t
        }
        scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val l = remember(maxWidth, maxHeight, top, bottom) { OrbitLayout(maxWidth.value, maxHeight.value, top, bottom) }

        ComposeCanvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Spatial mix: five sources round the listener. Stereo, Spatial and Fixed modes below." }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val x0 = down.position.x / density
                        val y0 = down.position.y / density
                        if (y0 >= l.segY - 20 && y0 <= l.segY + 20 && x0 >= 40 && x0 <= l.w - 40) {
                            // A segment: chosen when the finger lifts without moving off.
                            var moved = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > 12 * density) moved = true
                                if (!ch.pressed) break
                            }
                            if (!moved) choose(((x0 - 40) / ((l.w - 80) / 3)).toInt().coerceIn(0, 2))
                            return@awaitEachGesture
                        }
                        if (y0 < l.sceneTop || y0 > l.segY - 30) return@awaitEachGesture
                        val t0 = c.now()
                        c.takeOver(t0)
                        val sc0 = c.scene(t0).first
                        val v0 = l.view(sc0)
                        var best = -1
                        var bestD = 34.0
                        for (i in 0 until 5) {
                            val o = sc0.orbs[i]
                            val p = M.project(v0, o.x, o.z, o.h)
                            val d = hypot(p[0] - x0, p[1] - y0)
                            if (d < bestD) { best = i; bestD = d }
                        }
                        val m = c.manual
                        var dx = 0.0
                        var dz = 0.0
                        var lastTick = 0
                        val yaw0 = sc0.yaw
                        val elevation0 = sc0.elevation
                        if (best >= 0) {
                            val f = M.unproject(v0, x0.toDouble(), y0.toDouble())
                            val o = sc0.orbs[best]
                            dx = o.x - f.first
                            dz = o.z - f.second
                            m.held = best
                            m.heldX = o.x
                            m.heldZ = o.z
                            m.heldAt = t0
                            lastTick = floor(M.unpolar(o.x, o.z).second / 15).toInt()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        down.consume()
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val x = ch.position.x / density
                            val y = ch.position.y / density
                            if (best >= 0) {
                                val sc = c.scene(c.now()).first
                                val f = M.unproject(l.view(sc), x.toDouble(), y.toDouble())
                                var fx = f.first + dx
                                var fz = f.second + dz
                                val r = hypot(fx, fz)
                                if (r > 175) { fx *= 175 / r; fz *= 175 / r }
                                m.heldX = fx
                                m.heldZ = fz
                                val tick = floor(M.unpolar(fx, fz).second / 15).toInt()
                                if (tick != lastTick) {
                                    lastTick = tick
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else {
                                if (m.mode != 2) {
                                    m.yaw = yaw0 + (x - x0) * 0.35
                                    m.yawAt = -100.0
                                }
                                m.elevation = (elevation0 - (y - y0) * 0.12).coerceIn(24.0, 56.0)
                            }
                            ch.consume()
                        }
                        if (best >= 0) {
                            val t = c.now()
                            val (r, a) = M.unpolar(m.heldX, m.heldZ)
                            m.radius[best] = M.ring(r)
                            m.snapFrom[best] = r
                            m.snapAt[best] = t
                            m.offset[best] = a - M.idleAngle(best, t - m.anchor)
                            m.held = -1
                            m.dropped = best
                            m.droppedAt = t
                        }
                        scheduleResume()
                    }
                },
        ) {
            val t = c.frame
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.scale(density, density)
            painter.draw(nc, l, c, t)
            nc.restore()
        }
    }
}

/** Draws the screen with the native canvas, in dp. */
private class OrbitPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val semibold = Typeface.create(Typeface.DEFAULT, 600, false)
    private val medium = Typeface.create(Typeface.DEFAULT, 500, false)
    private val mono = Typeface.create(Typeface.MONOSPACE, 500, false)
    private val dash = DashPathEffect(floatArrayOf(2f, 5f), 0f)

    private fun fill(c: Int) = fill.apply { shader = null; maskFilter = null; color = c }
    private fun stroke(c: Int, w: Float, round: Boolean = false) = stroke.apply {
        color = c
        strokeWidth = w
        pathEffect = null
        strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokeJoin = if (round) Paint.Join.ROUND else Paint.Join.MITER
    }

    /** Text anchored like SwiftUI's: hx 0/0.5/1 (left, centre, right), centred vertically on y. */
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, hx: Float = 0.5f, tracking: Float = 0f): Float {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.letterSpacing = tracking / size
        val fm = text.fontMetrics
        val w = text.measureText(s)
        c.drawText(s, x - w * hx, y - (fm.ascent + fm.descent) / 2, text)
        return w
    }

    fun draw(cv: Canvas, l: OrbitLayout, ctl: OrbitController, t: Double) {
        val reduced = ctl.reduced
        val (sc, clock) = ctl.scene(t)
        val v = l.view(sc)
        cv.drawColor(argb(0x07070F))
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(l.cx, l.cy, l.w * 0.8f, argb(0x1A1A3A, 0.55), argb(0x1A1A3A, 0.0), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, l.w, l.h, fill)
        fill.shader = null
        // Star dust.
        for (i in 0 until 70) {
            val fx = (i * 0.618034 + 0.11) % 1.0
            val fy = (i * 0.754877 + 0.37) % 1.0
            val r = if (i % 7 == 0) 1.1f else 0.6f
            cv.drawCircle((fx * l.w).toFloat(), (fy * l.h).toFloat(), r, fill(argb(0xFFFFFF, 0.12 + 0.3 * (i % 5) / 4.0)))
        }
        // Vignette: clear inside 0.3 h of the centre, 60 % black at 0.75 h.
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(
            l.w / 2, l.h / 2, l.h * 0.75f,
            intArrayOf(argb(0x000000, 0.0), argb(0x000000, 0.0), argb(0x000000, 0.6)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, l.w, l.h, fill)
        fill.shader = null

        // Header.
        text(cv, "Spatial Mix", 24f, l.top + 26, 22f, argb(0xFFFFFF), semibold, 0f)
        cv.drawCircle(27f, l.top + 51, 3f, fill(if (sc.tracking) argb(0xB6F23A) else argb(0x5A5A70)))
        text(cv, if (sc.tracking) "Head tracking on" else "Head tracking off", 36f, l.top + 51, 11f, argb(0x8A8AA0), medium, 0f)
        val chip = RectF(l.w - 24 - 72, l.top + 14, l.w - 24, l.top + 40)
        cv.drawRoundRect(RectF(chip.left + 0.5f, chip.top + 0.5f, chip.right - 0.5f, chip.bottom - 0.5f), 13f, 13f, stroke(argb(0xFFFFFF, 0.18), 1f))
        text(cv, "5 STEMS", chip.centerX(), chip.centerY(), 10f, argb(0xFFFFFF, 0.7), semibold, tracking = 1.2f)

        // The floor: a soft glow, three rings, two arches, ticks and bearings on the outer one.
        val fc = M.project(v, 0.0, 0.0, 0.0)
        val e = sc.elevation * PI / 180
        val gr = 170 * l.scale
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(fc[0].toFloat(), fc[1].toFloat(), gr, argb(0x6C6CFF, 0.08), argb(0x6C6CFF, 0.0), Shader.TileMode.CLAMP)
        cv.drawOval(RectF(fc[0].toFloat() - gr, (fc[1] - gr * sin(e)).toFloat(), fc[0].toFloat() + gr, (fc[1] + gr * sin(e)).toFloat()), fill)
        fill.shader = null
        for (r in M.rings) {
            path.reset()
            for (k in 0..72) {
                val (x, z) = M.polar(r, k * 5.0)
                val p = M.project(v, x, z, 0.0)
                if (k == 0) path.moveTo(p[0].toFloat(), p[1].toFloat()) else path.lineTo(p[0].toFloat(), p[1].toFloat())
            }
            cv.drawPath(path, stroke(argb(0xFFFFFF, 0.18), 1f))
        }
        for (side in 0..1) {
            path.reset()
            for (k in 0..48) {
                val phi = k / 48.0 * PI
                val p = if (side == 0) M.project(v, 0.0, 152 * cos(phi), 152 * sin(phi)) else M.project(v, 152 * cos(phi), 0.0, 152 * sin(phi))
                if (k == 0) path.moveTo(p[0].toFloat(), p[1].toFloat()) else path.lineTo(p[0].toFloat(), p[1].toFloat())
            }
            stroke(argb(0xFFFFFF, 0.16), 1f).pathEffect = dash
            cv.drawPath(path, stroke)
            stroke.pathEffect = null
        }
        path.reset()
        for (k in 0 until 36) {
            val a = k * 10.0
            val (x0, z0) = M.polar(152.0, a)
            val (x1, z1) = M.polar(if (k % 3 == 0) 159.0 else 156.0, a)
            val p0 = M.project(v, x0, z0, 0.0)
            val p1 = M.project(v, x1, z1, 0.0)
            path.moveTo(p0[0].toFloat(), p0[1].toFloat())
            path.lineTo(p1[0].toFloat(), p1[1].toFloat())
        }
        cv.drawPath(path, stroke(argb(0xFFFFFF, 0.22), 0.8f))
        for (a in doubleArrayOf(0.0, 90.0, 180.0, 270.0)) {
            val (x, z) = M.polar(172.0, a)
            val p = M.project(v, x, z, 0.0)
            text(cv, a.toInt().toString(), p[0].toFloat(), p[1].toFloat(), 8f, argb(0xFFFFFF, 0.4), mono)
        }

        // Stems, floor dots and the wedges of light towards the head.
        val head = M.project(v, 0.0, 0.0, 40.0)
        for (i in 0 until 5) {
            val o = sc.orbs[i]
            val p = M.project(v, o.x, o.z, o.h)
            val g = M.project(v, o.x, o.z, 0.0)
            cv.drawLine(p[0].toFloat(), p[1].toFloat(), g[0].toFloat(), g[1].toFloat(), stroke(argb(0xFFFFFF, 0.15), 0.7f))
            cv.drawOval(RectF(g[0].toFloat() - 2, g[1].toFloat() - 1.2f, g[0].toFloat() + 2, g[1].toFloat() + 1.2f), fill(argb(M.sources[i].color, 0.6)))
            val s = orbSize(o, p[2], l, 0.0)
            val dx = p[0] - head[0]
            val dy = p[1] - head[1]
            val len = max(hypot(dx, dy), 1.0)
            val nx = -dy / len * s * 1.1
            val ny = dx / len * s * 1.1
            path.reset()
            path.moveTo(head[0].toFloat(), head[1].toFloat())
            path.lineTo((p[0] + nx).toFloat(), (p[1] + ny).toFloat())
            path.lineTo((p[0] - nx).toFloat(), (p[1] - ny).toFloat())
            path.close()
            cv.drawPath(path, fill(argb(if (o.lit > 0.01) M.sources[i].color else 0xFFFFFF, 0.035 + 0.12 * o.lit)))
        }

        // Tails: where each orb was over the last 0.8 s.
        if (!reduced) {
            for (k in 20 downTo 1) {
                val past = ctl.scene(t - k * 0.04).first
                val pv = l.view(past)
                for (i in 0 until 5) {
                    if (past.held == i && sc.held == i) continue
                    val o = past.orbs[i]
                    val p = M.project(pv, o.x, o.z, o.h)
                    val f = 1 - k / 21.0
                    cv.drawCircle(p[0].toFloat(), p[1].toFloat(), (3.2 * l.scale * f).toFloat(), fill(argb(M.sources[i].color, 0.45 * f)))
                }
            }
        }

        // Orbs behind the head, the head, then the orbs in front.
        val order = (0 until 5).map { i -> i to M.project(v, sc.orbs[i].x, sc.orbs[i].z, sc.orbs[i].h)[2] }.sortedBy { it.second }
        for ((i, d) in order) if (d < 0) orb(cv, i, sc, v, l, clock, reduced)
        listener(cv, v, fc[0].toFloat(), fc[1].toFloat(), l)
        for ((i, d) in order) if (d >= 0) orb(cv, i, sc, v, l, clock, reduced)

        // The readout beside a held orb, and the loop's finger.
        if (sc.held >= 0 && sc.chip > 0.01) {
            val o = sc.orbs[sc.held]
            val p = M.project(v, o.x, o.z, o.h)
            val label = M.readout(o.x, o.z)
            text.typeface = mono
            text.textSize = 11f
            text.letterSpacing = 0f
            val tw = text.measureText(label)
            val box = RectF((p[0] - (tw + 20) / 2).toFloat(), (p[1] - 52 * l.scale - 26).toFloat(), 0f, 0f)
            box.right = box.left + tw + 20
            box.bottom = box.top + 26
            cv.drawRoundRect(box, 13f, 13f, fill(argb(0x15152A, 0.92 * sc.chip)))
            cv.drawRoundRect(RectF(box.left + 0.5f, box.top + 0.5f, box.right - 0.5f, box.bottom - 0.5f), 13f, 13f, stroke(argb(M.sources[sc.held].color, 0.6 * sc.chip), 1f))
            text(cv, label, box.centerX(), box.centerY(), 11f, argb(0xFFFFFF, sc.chip), mono)
        }
        if (sc.finger > 0.01) {
            val o = sc.orbs[3]
            val p = M.project(v, o.x, o.z, o.h)
            val cx = p[0].toFloat() + 6
            val cy = p[1].toFloat() + 10
            cv.drawCircle(cx, cy, 22f, fill(argb(0xFFFFFF, 0.16 * sc.finger)))
            cv.drawCircle(cx, cy, 21.25f, stroke(argb(0xFFFFFF, 0.5 * sc.finger), 1.5f))
        }

        // The segmented control.
        val segW = l.w - 80
        val seg = RectF(40f, l.segY - 20, 40f + segW, l.segY + 20)
        cv.drawRoundRect(seg, 20f, 20f, fill(argb(0xFFFFFF, 0.07)))
        val ix = seg.left + 3 + (segW - 6) / 3 * sc.segment.coerceIn(0.0, 2.0).toFloat()
        cv.drawRoundRect(RectF(ix, seg.top + 3, ix + (segW - 6) / 3, seg.top + 37), 17f, 17f, fill(argb(0xFFFFFF, 0.14)))
        M.modes.forEachIndexed { i, name ->
            val on = max(0.0, 1 - abs(sc.segment - i))
            text(cv, name, seg.left + segW / 6 * (2 * i + 1), seg.centerY(), 13f, argb(0xFFFFFF, 0.5 + 0.5 * on), medium)
        }

        // Levels, play, and the track.
        for (i in 0 until 5) {
            val lv = if (reduced) 0.5 else 0.3 + 0.7 * M.beat(i, clock).first
            val bh = (4 + 24 * lv).toFloat()
            cv.drawRoundRect(RectF(24f + i * 10, l.rowY + 14 - bh, 29f + i * 10, l.rowY + 14), 2.5f, 2.5f, fill(argb(M.sources[i].color)))
        }
        cv.drawCircle(l.w / 2, l.rowY, 26f, fill(argb(0xFFFFFF)))
        cv.drawRoundRect(RectF(l.w / 2 - 7, l.rowY - 8, l.w / 2 - 2, l.rowY + 8), 1.5f, 1.5f, fill(argb(0x07070F)))
        cv.drawRoundRect(RectF(l.w / 2 + 2, l.rowY - 8, l.w / 2 + 7, l.rowY + 8), 1.5f, 1.5f, fill(argb(0x07070F)))
        text(cv, "Low Orbit", l.w - 24, l.rowY - 8, 13f, argb(0xFFFFFF), semibold, 1f)
        text(cv, "Kaito Vale", l.w - 24, l.rowY + 9, 11f, argb(0x8A8AA0), Typeface.DEFAULT, 1f)
    }

    private fun orbSize(o: SpatialOrbitMixerModel.Orb, depth: Double, l: OrbitLayout, pulse: Double) =
        15 * l.scale * o.grow * (1 + 0.15 * depth / 152) * (1 + 0.25 * pulse)

    private fun orb(cv: Canvas, i: Int, sc: SpatialOrbitMixerModel.Scene, v: SpatialOrbitMixerModel.View, l: OrbitLayout, clock: Double, reduced: Boolean) {
        val o = sc.orbs[i]
        val p = M.project(v, o.x, o.z, o.h)
        val (pulse, phase) = if (reduced) 0.0 to 0.5 else M.beat(i, clock)
        val s = orbSize(o, p[2], l, pulse).toFloat()
        val cx = p[0].toFloat()
        val cy = p[1].toFloat()
        val color = M.sources[i].color
        fill.shader = null
        fill.color = argb(color, 0.3 + 0.25 * o.lit)
        fill.maskFilter = blur(8f)
        cv.drawCircle(cx, cy, 2.2f * s, fill)
        fill.maskFilter = null
        if (!reduced) {
            val pr = (s * (1 + 1.6 * phase)).toFloat()
            cv.drawCircle(cx, cy, pr, stroke(argb(color, 0.5 * (1 - phase)), 1.5f))
        }
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(
            cx - 0.3f * s, cy - 0.35f * s, 1.3f * s,
            intArrayOf(argb(0xFFFFFF, 0.95), argb(color), argb(color, 0.85)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        cv.drawCircle(cx, cy, s, fill)
        fill.shader = null
        // The pictogram.
        val k = s / 15
        path.reset()
        when (i) {
            0 -> { // a microphone
                path.addRoundRect(RectF(cx - 2.5f * k, cy - 6 * k, cx + 2.5f * k, cy + 2 * k), 2.5f * k, 2.5f * k, Path.Direction.CW)
                path.moveTo(cx, cy + 2 * k); path.lineTo(cx, cy + 6 * k)
            }
            1 -> for (d in floatArrayOf(-3.5f, 0f, 3.5f)) { // keys
                path.addRoundRect(RectF(cx + d * k - 1.2f * k, cy - 5 * k, cx + d * k + 1.2f * k, cy + 5 * k), 0.8f * k, 0.8f * k, Path.Direction.CW)
            }
            2 -> { // a pick
                path.moveTo(cx - 4.5f * k, cy - 4 * k)
                path.quadTo(cx, cy - 7 * k, cx + 4.5f * k, cy - 4 * k)
                path.quadTo(cx + 3 * k, cy + 2 * k, cx, cy + 6 * k)
                path.quadTo(cx - 3 * k, cy + 2 * k, cx - 4.5f * k, cy - 4 * k)
            }
            3 -> { // a drum and sticks
                path.addOval(RectF(cx - 4.5f * k, cy - 2 * k, cx + 4.5f * k, cy + 5 * k), Path.Direction.CW)
                path.moveTo(cx - 5 * k, cy - 6 * k); path.lineTo(cx - 1 * k, cy - 1 * k)
                path.moveTo(cx + 5 * k, cy - 6 * k); path.lineTo(cx + 1 * k, cy - 1 * k)
            }
            else -> for ((d, hh) in listOf(-4f to 3f, -1.3f to 6f, 1.3f to 4.5f, 4f to 2f)) { // a waveform
                path.moveTo(cx + d * k, cy - hh * k); path.lineTo(cx + d * k, cy + hh * k)
            }
        }
        cv.drawPath(path, stroke(argb(0xFFFFFF, 0.95), 1.3f * k, true))
    }

    /** The listener: a grey head on shoulders with over-ear headphones, the far cup behind it and the near one in front. */
    private fun listener(cv: Canvas, v: SpatialOrbitMixerModel.View, fx: Float, fy: Float, l: OrbitLayout) {
        val k = l.scale
        fill.shader = null
        fill.maskFilter = blur(6f)
        fill.color = argb(0x000000, 0.7)
        cv.drawOval(RectF(fx - 30 * k, fy - 9 * k, fx + 30 * k, fy + 9 * k), fill)
        fill.color = argb(0xB6F23A, 0.12)
        cv.drawOval(RectF(fx - 40 * k, fy - 12 * k, fx + 40 * k, fy + 12 * k), fill)
        fill.maskFilter = null
        val c = M.project(v, 0.0, 0.0, 40.0)
        val cx = c[0].toFloat()
        val cy = c[1].toFloat()
        val cups = listOf(-1.0, 1.0).map { side -> M.project(v, 22 * side, 0.0, 38.0) }
        fun cup(p: DoubleArray) {
            val px = p[0].toFloat()
            val py = p[1].toFloat()
            val r = RectF(px - 7 * k, py - 12 * k, px + 7 * k, py + 12 * k)
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = LinearGradient(r.left, r.top, r.right, r.bottom, argb(0x34343F), argb(0x18181F), Shader.TileMode.CLAMP)
            cv.drawRoundRect(r, 7 * k, 7 * k, fill)
            fill.shader = null
            cv.drawLine(px, py - 7 * k, px, py + 7 * k, stroke(argb(0xB6F23A, 0.9), 1.5f * k, true))
        }
        // Shoulders under the head.
        path.reset()
        path.moveTo(cx - 38 * k, cy + 38 * k)
        path.quadTo(cx - 37 * k, cy + 15 * k, cx - 13 * k, cy + 13 * k)
        path.lineTo(cx + 13 * k, cy + 13 * k)
        path.quadTo(cx + 37 * k, cy + 15 * k, cx + 38 * k, cy + 38 * k)
        path.quadTo(cx, cy + 44 * k, cx - 38 * k, cy + 38 * k)
        path.close()
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(cx, cy + 12 * k, cx, cy + 40 * k, argb(0x5C5C6C), argb(0x26262F), Shader.TileMode.CLAMP)
        cv.drawPath(path, fill)
        fill.shader = null
        val far = if (cups[0][2] < cups[1][2]) 0 else 1
        cup(cups[far])
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(
            cx - 7 * k, cy - 12 * k, 34 * k,
            intArrayOf(argb(0xF1F1F5), argb(0xB9B9C6), argb(0x6E6E7E)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        cv.drawOval(RectF(cx - 20 * k, cy - 26 * k, cx + 20 * k, cy + 22 * k), fill)
        fill.shader = null
        // The headband over the top, from cup to cup.
        path.reset()
        path.moveTo(cups[0][0].toFloat(), (cups[0][1] - 10 * k).toFloat())
        path.quadTo(cx, cy - 50 * k, cups[1][0].toFloat(), (cups[1][1] - 10 * k).toFloat())
        cv.drawPath(path, stroke(argb(0x23232B), 5 * k, true))
        cv.drawPath(path, stroke(argb(0xB6F23A, 0.5), 1 * k, true))
        cup(cups[1 - far])
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
