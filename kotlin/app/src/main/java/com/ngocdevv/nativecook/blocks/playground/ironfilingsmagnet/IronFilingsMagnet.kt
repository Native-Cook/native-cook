package com.ngocdevv.nativecook.blocks.playground.ironfilingsmagnet

import android.graphics.Matrix
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Iron Filings Magnet — "Field Notes": 900 iron filings on a sheet of graph paper, a bar magnet
 under it. The filings line up along the field and crowd at the poles; move the magnet and they
 turn after it, the far ones later. A second magnet waits at the right edge.

 Rendering: one Canvas. The paper is a bitmap rendered once; per frame the two magnets (one
 pre-rendered bitmap, rotated), then 900 filing shadows and 900 filings, each a cell of one sprite
 atlas placed with one affine matrix and an alpha (the metal comes in six darkness steps for the
 tufts), then the readings. Nothing recomposes per frame: the frame loop writes plain arrays and
 bumps one counter that only the draw lambda reads. In the loop each filing reads the magnets'
 path a little in the past (16 delay buckets, 30 – 520 ms by distance); under the finger it is one
 damped spring per filing (stiffer near the magnet).

 Touch: drag either magnet (pull B in by its tab on the right); it follows the finger on a spring
 (ζ 0.8). Tap a magnet to turn it end for end. Haptics: a tick on pick-up and on a flip, a light
 tap when two poles come within 70 dp. 2 s after the last touch both magnets ease home (0.9 s)
 and the loop restarts. Reduced motion: no loop; filings settle at once instead of turning.
 */

private const val BUCKETS = 16
private const val MAG_K = 300f
private val MAG_C = 2 * 0.8f * sqrt(MAG_K)
private const val TAP_MS = 260L
private const val PIF = PI.toFloat()

/** Everything the frame loop and the gestures share; plain fields, never read by composition. */
private class IronState(val g: IronGeo) {
    val filings = IronFilings(g)
    val ang = FloatArray(IRON_FILINGS)
    val vel = FloatArray(IRON_FILINGS)
    val ox = FloatArray(IRON_FILINGS)
    val oy = FloatArray(IRON_FILINGS)
    /** How much a filing stands up in a pole's tuft (0 … 1), and how strong its field is (0 … 1). */
    val stand = FloatArray(IRON_FILINGS)
    val pow = FloatArray(IRON_FILINGS)
    val mag = FloatArray(6)
    val magV = FloatArray(6)
    val target = FloatArray(6)
    val past = Array(BUCKETS) { FloatArray(6) }
    val out = FloatArray(5)
    val field = FloatArray(2)
    val pole = FloatArray(3)
    var held = -1
    var grabX = 0f
    var grabY = 0f
    var near = 0
    var tension = 0f
    var ready = false
    var now = -1.0
    var start = -1.0
    var resumeAt = -1.0
    /** Bumped every frame that changed the picture: the only value the draw lambda reads. */
    val tick = mutableIntStateOf(0)
    /** Bumped by a touch to wake a sleeping frame loop. */
    val wake = mutableIntStateOf(0)
    var awake = true

    /** Angle and pull of filing i in a field of magnets m, into out. */
    fun filingTarget(i: Int, m: FloatArray) {
        val x = filings.x[i]
        val y = filings.y[i]
        val soft = 64 * g.u * g.u
        ironFieldAt(x, y, m, g.half, soft, field)
        val bx = field[0]
        val by = field[1]
        val strength = hypot(bx, by) * g.half * g.half
        val k = min(1f, max(0f, (strength - 0.003f) / 0.03f))
        val align = k * k * (3 - 2 * k)
        val a0 = filings.a0[i]
        out[0] = a0 + ironAxial(atan2(by, bx) - a0) * align
        // Tufts: filings within ~160 dp of a pole are drawn in towards it (up to 26 dp, never past
        // it), so the ends crowd; the closest stand up.
        ironNearestPole(x, y, m, g.half, pole)
        val dx = pole[0]
        val dy = pole[1]
        val d = pole[2]
        val reach = max(0f, 1 - d / (160 * g.u))
        val move = min(26 * g.u * reach.pow(1.6f) * min(1f, strength * 4), d * 0.82f)
        val pull = move / max(d, 1f)
        out[1] = dx * pull
        out[2] = dy * pull
        val nearPole = max(0f, 1 - (d - move) / (44 * g.u))
        out[3] = nearPole * nearPole * align
        out[4] = align
    }

    private fun store(i: Int) {
        ang[i] = out[0]
        ox[i] = out[1]
        oy[i] = out[2]
        stand[i] = out[3]
        pow[i] = out[4]
    }

    fun centreField(): Float {
        ironFieldAt(g.cx, g.cy, mag, g.half, 64 * g.u * g.u, field)
        return hypot(field[0], field[1]) * g.half * g.half * 19
    }

    /** One frame at n seconds. Returns whether the loop must keep running. */
    fun step(n: Double, auto: Boolean, reduced: Boolean, haptic: (Boolean) -> Unit): Boolean {
        val prev = now
        if (!ready) {
            ironMagnetsAt(g, 0f, mag)
            mag.copyInto(target)
            for (i in 0 until IRON_FILINGS) {
                filingTarget(i, mag)
                store(i)
            }
            if (auto) start = n
            ready = true
            tick.intValue += 1
        }
        now = n
        val dt = if (prev < 0) 0f else min((n - prev).toFloat(), 1f / 30f)
        if (start >= 0 && n >= start) {
            // The loop: each filing reads the magnets as they were a moment ago.
            val t = ((n - start) % LOOP).toFloat()
            ironMagnetsAt(g, t, mag)
            for (b in 0 until BUCKETS) {
                val tb = (((t - 0.03f - b * 0.0327f) % LOOP) + LOOP) % LOOP
                ironMagnetsAt(g, tb, past[b])
            }
            for (i in 0 until IRON_FILINGS) {
                val d = hypot(filings.x[i] - mag[0], filings.y[i] - mag[1])
                val b = min(BUCKETS - 1, floor(d / (520 * g.u) * BUCKETS).toInt())
                filingTarget(i, past[b])
                store(i)
                vel[i] = 0f
            }
            mag.copyInto(target)
            magV.fill(0f)
            tick.intValue += 1
            return true
        }
        // Under the finger: magnets on springs, each filing on its own spring.
        var moving = 0f
        for (k in 0 until 6) {
            val a = MAG_K * (target[k] - mag[k]) - MAG_C * magV[k]
            magV[k] += a * dt
            mag[k] += magV[k] * dt
            moving += abs(magV[k]) + abs(target[k] - mag[k])
        }
        if (reduced) target.copyInto(mag)
        val busy = moving > 0.05f || tension > 0.002f || held >= 0
        if (busy || dt == 0f) {
            var sum = 0f
            for (i in 0 until IRON_FILINGS) {
                filingTarget(i, mag)
                if (reduced) {
                    store(i)
                    continue
                }
                val d = hypot(filings.x[i] - mag[0], filings.y[i] - mag[1])
                val q = 1 + d / (110 * g.u)
                val k = 340 / (q * q)
                val c = 2 * 0.7f * sqrt(k)
                val err = ironAxial(out[0] - ang[i])
                vel[i] += (k * err - c * vel[i]) * dt
                ang[i] += vel[i] * dt
                val f = 1 - exp(-dt / 0.12f)
                ox[i] += (out[1] - ox[i]) * f
                oy[i] += (out[2] - oy[i]) * f
                stand[i] += (out[3] - stand[i]) * f
                pow[i] += (out[4] - pow[i]) * f
                sum += abs(err) + abs(vel[i]) * 0.05f
            }
            tension = sum / IRON_FILINGS
            tick.intValue += 1
        }
        // Two poles meeting: one light tap.
        val nn = hypot(
            mag[0] + cos(mag[2]) * g.half - (mag[3] + cos(mag[5]) * g.half),
            mag[1] + sin(mag[2]) * g.half - (mag[4] + sin(mag[5]) * g.half),
        )
        val close = if (nn < 70 * g.u) 1 else 0
        if (close != near) {
            near = close
            if (close == 1 && held >= 0) haptic(false)
        }
        val r = resumeAt
        if (r >= 0 && n >= r && held < 0) {
            resumeAt = -1.0
            ironMagnetsAt(g, 0f, target)
            start = n + 0.9
        }
        return busy || resumeAt >= 0 || start >= 0
    }

    /** A touch takes the magnets from the loop where they are. */
    fun takeOver() {
        if (start >= 0 && now >= start) {
            mag.copyInto(target)
            magV.fill(0f)
        }
        start = -1.0
        resumeAt = -1.0
    }

    /** The magnet under (x, y): 0, 1 (B also by its tab at the right edge), or −1. */
    fun pick(x: Float, y: Float): Int {
        for (k in 0 until 2) {
            val dx = x - mag[k * 3]
            val dy = y - mag[k * 3 + 1]
            val a = mag[k * 3 + 2]
            val along = abs(dx * cos(a) + dy * sin(a))
            val across = abs(-dx * sin(a) + dy * cos(a))
            if (along < g.half + 26 * g.u && across < g.thick + 22 * g.u) return k
        }
        if (x > g.w - 56 && abs(y - mag[4]) < 44) return 1
        return -1
    }
}

/**
 * Iron filings on a sheet of graph paper over a bar magnet: drag a magnet and the filings swing
 * after it along the field lines, tap one to flip its poles, pull the second in from the edge.
 *
 * @param autoPlay sweeps magnet A in a figure eight, brings magnet B in against it and out again (LOOP 9 s).
 */
@Composable
fun IronFilingsMagnet(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val density = screen.density
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(screen) / density
    val bottom = insets.getBottom(screen) / density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Iron filings on paper over a bar magnet. Drag a magnet to move the field, tap it to flip its poles." },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val state = remember(w, h, top, bottom) { IronState(IronGeo(w, h, top, bottom)) }
        val g = state.g
        val fonts = remember { IronFonts() }
        val paper = remember(g, density) { ironPaperBitmap(g, fonts, density) }
        val atlas = remember { ironSpriteAtlas() }
        val magnetSprite = remember(g, density) { IronMagnetSprite(g, density) }
        val paints = remember { IronLivePaints() }
        val columns = remember(g) { ironFooterColumns(g) }
        val shadowSrc = remember { ironSpriteRow(0) }
        val metalSrc = remember { Array(IRON_TINTS) { ironSpriteRow(it + 1) } }
        val cell = remember { RectF(0f, 0f, IRON_SPRITE_W.toFloat(), IRON_SPRITE_H.toFloat()) }
        val matrix = remember { Matrix() }
        val values = remember { FloatArray(9).also { it[8] = 1f } }
        val paperDst = remember(g) { RectF(0f, 0f, g.w * density, g.h * density) }

        val haptic: (Boolean) -> Unit = { tick ->
            view.performHapticFeedback(if (tick) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY)
        }

        LaunchedEffect(state, auto, reduced) {
            state.ready = false
            state.start = -1.0
            state.resumeAt = -1.0
            state.now = -1.0
            while (true) {
                val keep = withFrameNanos { nanos -> state.step(nanos / 1e9, auto, reduced, haptic) }
                if (!keep) {
                    // Nothing moves: sleep until a touch wakes the loop.
                    state.awake = false
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    state.awake = true
                    state.now = -1.0
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, auto) {
                    val slop = 4 * density
                    fun startDrag(p: Offset) {
                        state.takeOver()
                        val x = p.x / density
                        val y = p.y / density
                        val k = state.pick(x, y)
                        state.held = k
                        if (k < 0) return
                        state.grabX = state.mag[k * 3] - x
                        state.grabY = state.mag[k * 3 + 1] - y
                        if (k == 1 && state.mag[3] > g.w - 20) {
                            state.grabX = -16f
                            state.grabY = 0f
                        }
                        haptic(true)
                    }
                    fun moveDrag(p: Offset) {
                        val k = state.held
                        if (k < 0) return
                        val minY = g.fieldTop + 10
                        val maxY = g.fieldBottom - 10
                        state.target[k * 3] = max(g.paperX - 20, min(g.w + 30 * g.u, p.x / density + state.grabX))
                        state.target[k * 3 + 1] = max(minY, min(maxY, p.y / density + state.grabY))
                    }
                    fun endTouch() {
                        state.held = -1
                        if (auto) state.resumeAt = state.now + 2
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        val origin = down.position
                        var dragging = false
                        var finished = false
                        state.wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                if (!change.pressed) {
                                    if (dragging) {
                                        endTouch()
                                    } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                        // A tap turns a magnet end for end.
                                        state.takeOver()
                                        val k = state.pick(origin.x / density, origin.y / density)
                                        if (k >= 0) {
                                            state.target[k * 3 + 2] += PIF
                                            haptic(true)
                                        }
                                        if (auto) state.resumeAt = state.now + 2
                                    }
                                    finished = true
                                    state.wake.intValue += 1
                                    break
                                }
                                if (!dragging && (change.position - origin).getDistance() > slop) {
                                    dragging = true
                                    startDrag(origin)
                                }
                                if (dragging) {
                                    moveDrag(change.position)
                                    change.consume()
                                }
                            }
                        } finally {
                            if (dragging && !finished) endTouch()
                        }
                    }
                },
        ) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawBitmap(paper, null, paperDst, null)
                c.save()
                c.scale(density, density)
                ironDrawUnder(c, g, state.mag, magnetSprite, paints, fonts)
                val f = state.filings
                val p = paints.sprite
                // Shadows: a standing filing casts a longer, offset contact shadow.
                for (i in 0 until IRON_FILINGS) {
                    val st = state.stand[i]
                    val pw = state.pow[i]
                    val sc = (f.len[i] / 48f) * (0.8f + 0.2f * pw + 0.8f * st)
                    val cs = cos(state.ang[i]) * sc
                    val sn = sin(state.ang[i]) * sc
                    val x = f.x[i] + state.ox[i] + 0.7f + 2.4f * st
                    val y = f.y[i] + state.oy[i] + 1.2f + 3.2f * st
                    values[0] = cs; values[1] = -sn; values[2] = x - (cs * 24 - sn * 6)
                    values[3] = sn; values[4] = cs; values[5] = y - (sn * 24 + cs * 6)
                    matrix.setValues(values)
                    p.alpha = ((0.55f + 0.45f * st) * (0.5f + 0.5f * max(pw, st)) * 255).roundToInt().coerceIn(0, 255)
                    c.save()
                    c.concat(matrix)
                    c.drawBitmap(atlas, shadowSrc, cell, p)
                    c.restore()
                }
                // Metal: tuft filings longer and darker, weak-field ones shorter and fainter.
                for (i in 0 until IRON_FILINGS) {
                    val st = state.stand[i]
                    val pw = state.pow[i]
                    val sc = (f.len[i] / 48f) * (0.8f + 0.2f * pw + 0.5f * st)
                    val cs = cos(state.ang[i]) * sc
                    val sn = sin(state.ang[i]) * sc
                    val x = f.x[i] + state.ox[i]
                    val y = f.y[i] + state.oy[i]
                    values[0] = cs; values[1] = -sn; values[2] = x - (cs * 24 - sn * 6)
                    values[3] = sn; values[4] = cs; values[5] = y - (sn * 24 + cs * 6)
                    matrix.setValues(values)
                    p.alpha = ((0.5f + 0.5f * max(pw, st)) * 255).roundToInt().coerceIn(0, 255)
                    val row = (st.coerceIn(0f, 1f) * (IRON_TINTS - 1)).roundToInt()
                    c.save()
                    c.concat(matrix)
                    c.drawBitmap(atlas, metalSrc[row], cell, p)
                    c.restore()
                }
                val onPaper = if (state.mag[3] < g.paperX + g.paperW - 10) 4 else 2
                ironDrawReadings(c, g, state.centreField(), onPaper, fonts, columns)
                c.restore()
            }
        }
    }
}
