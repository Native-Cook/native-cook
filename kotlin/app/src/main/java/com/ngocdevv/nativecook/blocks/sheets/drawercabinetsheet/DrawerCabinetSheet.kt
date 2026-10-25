package com.ngocdevv.nativecook.blocks.sheets.drawercabinetsheet

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Drawer Cabinet Sheet — "Drawers · Office supplies". The bottom sheet is a wooden drawer: an oak
 front with a brass pull that slides out from under the cabinet's top board, and behind it the
 box of supplies — pens in a felt tray, paper clips, binder clips, an eraser, a pencil, tape and
 sticky notes — every one on its own spring, so they rattle and slide as the drawer starts and
 stops. Drag it to half or full (detents, rubber band past full); let go near closed and the
 soft-close damper eases it home. Tap a supply to lift it out onto the desk; tap it there to put
 it back.

 autoPlay (LOOP = 8 s, DrawerCabinetSheetMotion.kt): rest 0–1.2 · pulled to half 1.2–2.2 · to
 full 2.8–3.6 · the blue pen picked 4.0–5.0 · soft close 5.6–7.0 · the desk clears 7.0–7.5 · rest.
 A touch takes over at once; 2 s after the last, the drawer soft-closes, what is on the desk flies
 back (0.9 s) and the loop restarts. Reduced motion: no loop, no rattle; the drawer moves without
 overshoot and picked things appear on the desk.

 Drawing: one Canvas — the desk bitmap, the drawer bitmap (moved by a transform, clipped at the
 board), the supplies in the drawer and on the desk as sprites from two baked atlases (shadows,
 faces), the board bitmap. Poses are computed in the frame loop, drawn in the draw lambda.
*/

private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2.0
private const val RETURN = 0.9
/** The auto loop's pick: the blue fineliner. */
private const val PEN = 0

private const val FEEL_LIGHT = 1
private const val FEEL_SOFT = 2

private val Ink = Color(0xFF2A1E14)
private val InkSoft = Color(0x9E2A1E14)
private val Paper = Color(0xFFEFE7DA)

private class DrawerState(private val art: DrawerArt, private val reduced: Boolean) {
    private val l = art.layout
    private val items = art.items
    val n = items.size
    var mode = MODE_AUTO
    var clock = 0.0
    var now = 0.0
    var lastTouch = 0.0
    var dragging = false
    var dragFrom = 0.0
    private var returnAt = 0.0

    // The drawer's opening under touch: a value, or a timing or spring animation toward one.
    private var openValue = 0.0
    private var anim = 0 // 0 none, 1 timing, 2 spring
    private var animFrom = 0.0
    private var animTo = 0.0
    private var animStart = 0.0
    private var animDur = 1.0
    private var animSoft = true
    private var animV0 = 0.0

    // Items under touch: 0 in the drawer, 1 flying out, 2 on the desk, 3 flying back.
    val place = IntArray(n)
    private val start = DoubleArray(n)
    private val from = Array(n) { DoubleArray(3) }
    private val slot = IntArray(n)
    private val lag = Array(n) { DoubleArray(2) }
    private var prevOpen = 0.0
    private var prevV = 0.0
    private var acc = 0.0
    private var chipAt = -10.0
    private var idle = false

    // What is drawn.
    var shownOpen = 0.0
    var topAlpha = 1f
    val chip = mutableFloatStateOf(0f)
    val chipName = mutableStateOf("Blue fineliner")
    val frame = mutableIntStateOf(0)
    var feel = 0
    /** Per item: drawn on the desk layer (out of the drawer) this frame, and its pose. */
    val isOut = BooleanArray(n)
    val px = DoubleArray(n)
    val py = DoubleArray(n)
    val pr = DoubleArray(n)
    val pLift = DoubleArray(n)
    val pScale = DoubleArray(n)
    private val tmp = DoubleArray(5)
    private val home = DoubleArray(3)
    private val desk = DoubleArray(3)

    private fun resetSim() {
        for (i in 0 until n) {
            place[i] = 0; start[i] = 0.0; from[i].fill(0.0); slot[i] = 0; lag[i].fill(0.0)
        }
        prevOpen = openNow(); prevV = 0.0; acc = 0.0; chipAt = -10.0; idle = false
    }

    fun openNow(): Double = when (anim) {
        1 -> {
            val u = min(1.0, max(0.0, (now - animStart) / animDur))
            // Reanimated's default timing curve is ease-in-out quad.
            val e = if (animSoft) drawerSoft(u) else if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2) * (-2 * u + 2) / 2
            if (u >= 1) {
                anim = 0; openValue = animTo; animTo
            } else {
                animFrom + (animTo - animFrom) * e
            }
        }
        2 -> {
            // ζ 0.72, settling in ~0.5 s (as the Swift port reads Reanimated's spring).
            val k = now - animStart
            if (k > 1.2) {
                anim = 0; openValue = animTo; animTo
            } else {
                val w0 = 12.0
                val z = 0.72
                val wd = w0 * sqrt(1 - z * z)
                val a = animFrom - animTo
                val b = (animV0 + z * w0 * a) / wd
                animTo + exp(-z * w0 * k) * (a * cos(wd * k) + b * sin(wd * k))
            }
        }
        else -> openValue
    }

    private fun setOpen(v: Double) {
        anim = 0
        openValue = v
    }

    private fun timing(to: Double, dur: Double, soft: Boolean) {
        animFrom = openNow(); animTo = to; animStart = now; animDur = dur; animSoft = soft; anim = 1
    }

    /** Item i's place in the drawer on screen, for an opening and a lag. */
    private fun homeOf(i: Int, o: Double, lg: Double, out: DoubleArray) {
        val it = items[i]
        out[0] = (l.ix + it.x).toDouble()
        out[1] = l.boardY - o + it.y + lg
        out[2] = it.rot + lg / it.spring.slack * it.spring.turn
    }

    /** Where item i lands on the desk: long things stacked on the right, small ones in a row. */
    private fun deskOf(i: Int, s: Int, out: DoubleArray) {
        val long = items[i].w > 100
        if (long) {
            out[0] = l.w * 0.6; out[1] = l.top + 172.0 + s * 30; out[2] = -0.12 + s * 0.05
        } else {
            out[0] = 70.0 + (s % 4) * 72; out[1] = l.top + 242.0 + (s / 4) * 54; out[2] = 0.2 - s * 0.1
        }
    }

    private fun nextSlot(i: Int): Int {
        val long = items[i].w > 100
        var k = 0
        for (j in 0 until n) if (j != i && place[j] != 0 && place[j] != 3 && (items[j].w > 100) == long) k++
        return k
    }

    fun touched() {
        lastTouch = now
        idle = false
        if (mode == MODE_USER) return
        if (mode == MODE_AUTO) {
            val t = clock
            resetSim()
            setOpen(drawerAutoOpen(t, l.half.toDouble(), l.full.toDouble()))
            prevOpen = openValue
            for (i in 0 until n) {
                lag[i][0] = drawerTableAt(art.table[i], t)
                lag[i][1] = (drawerTableAt(art.table[i], t) - drawerTableAt(art.table[i], t - 1.0 / DRAWER_RATE)) * DRAWER_RATE
            }
            if (t >= DRAWER_PICK_AT && t < DRAWER_CLEAR_AT) {
                homeOf(PEN, l.full.toDouble(), 0.0, from[PEN])
                place[PEN] = if (t < DRAWER_PICK_AT + DRAWER_LIFT_FOR + DRAWER_FLY_FOR) 1 else 2
                start[PEN] = now - (t - DRAWER_PICK_AT)
                slot[PEN] = 0
            }
        }
        mode = MODE_USER
    }

    private fun pick(i: Int) {
        homeOf(i, openNow(), lag[i][0], from[i])
        slot[i] = nextSlot(i)
        place[i] = 1
        start[i] = now - (if (reduced) 10 else 0)
        chipName.value = items[i].name
        feel = feel or FEEL_LIGHT
    }

    private fun putBack(i: Int, pose: DoubleArray) {
        from[i][0] = pose[0]; from[i][1] = pose[1]; from[i][2] = pose[2]
        place[i] = 3
        start[i] = now - (if (reduced) 10 else 0)
    }

    private fun settleTo(target: Double, velocity: Double) {
        when {
            target <= 0 -> timing(0.0, if (reduced) 0.25 else 0.85, true)
            reduced -> timing(target, 0.25, false)
            else -> {
                animFrom = openNow(); animTo = target; animStart = now; animV0 = velocity; anim = 2
            }
        }
    }

    /** The topmost item under a point: on the desk first, then in the open part of the drawer. Returns i, or -1. */
    private fun hitItem(x: Double, y: Double, onDesk: BooleanArray, pose: DoubleArray): Int {
        for (i in n - 1 downTo 0) {
            if (place[i] != 2) continue
            deskOf(i, slot[i], pose)
            if (inside(i, x, y, pose, 10.0, 12.0)) { onDesk[0] = true; return i }
        }
        val o = openNow()
        if (y > l.boardY || y < l.boardY - o) return -1
        for (i in n - 1 downTo 0) {
            if (place[i] != 0) continue
            homeOf(i, o, lag[i][0], pose)
            if (inside(i, x, y, pose, 8.0, 10.0)) { onDesk[0] = false; return i }
        }
        return -1
    }

    private fun inside(i: Int, x: Double, y: Double, pose: DoubleArray, padX: Double, padY: Double): Boolean {
        val it = items[i]
        val dx = x - pose[0]
        val dy = y - pose[1]
        val lx = dx * cos(pose[2]) + dy * sin(pose[2])
        val ly = -dx * sin(pose[2]) + dy * cos(pose[2])
        return abs(lx) < it.w / 2 + padX && abs(ly) < it.h / 2 + padY
    }

    private val hitDesk = BooleanArray(1)
    private val hitPose = DoubleArray(3)

    fun tap(x: Double, y: Double) {
        touched()
        val i = hitItem(x, y, hitDesk, hitPose)
        if (i >= 0) {
            if (hitDesk[0]) putBack(i, hitPose.copyOf()) else pick(i)
            return
        }
        val o = openNow()
        val top = l.boardY - o - DRAWER_PANEL
        if (y >= top && y <= top + DRAWER_PANEL) {
            settleTo(if (o < 10) l.half.toDouble() else 0.0, 0.0)
            feel = feel or FEEL_LIGHT
        }
    }

    /** A drag begins at y0; returns whether it holds the drawer. */
    fun dragStart(y0: Double): Boolean {
        val o = openNow()
        val top = l.boardY - o - DRAWER_PANEL
        if (y0 < top - 12 || y0 > l.boardY + 40) return false
        touched()
        dragging = true
        setOpen(openNow())
        dragFrom = openValue
        return true
    }

    fun dragMove(translationY: Double) {
        if (!dragging) return
        lastTouch = now
        var o = dragFrom - translationY
        if (o > l.full) {
            val over = o - l.full
            o = l.full + over * 0.55 / (1 + over / 120)
        } else if (o < 0) {
            o *= 0.2
        }
        setOpen(o)
    }

    fun dragEnd(velocityY: Double) {
        if (!dragging) return
        dragging = false
        lastTouch = now
        val o = openNow()
        val aim = o - velocityY * 0.18
        var best = 0.0
        for (p in doubleArrayOf(0.0, l.half.toDouble(), l.full.toDouble())) if (abs(aim - p) < abs(aim - best)) best = p
        settleTo(best, -velocityY)
        if (best > 0) feel = feel or FEEL_LIGHT
    }

    /** One frame; returns whether the frame loop must keep running. */
    fun step(dt: Double, auto: Boolean): Boolean {
        val at = now + dt
        now = at
        var o: Double
        var top = 1f
        val ch: Double
        for (i in 0 until n) isOut[i] = false
        var moving: Boolean
        var keep: Boolean

        if (mode == MODE_AUTO) {
            moving = auto
            val t = if (auto) (clock + dt) % LOOP else 0.0
            clock = t
            o = drawerAutoOpen(t, l.half.toDouble(), l.full.toDouble())
            for (i in 0 until n) {
                homeOf(i, o, if (reduced) 0.0 else drawerTableAt(art.table[i], t), home)
                px[i] = home[0]; py[i] = home[1]; pr[i] = home[2]
            }
            if (t >= DRAWER_PICK_AT && t < DRAWER_CLEAR_AT + 0.5) {
                homeOf(PEN, l.full.toDouble(), 0.0, home)
                deskOf(PEN, 0, desk)
                drawerPickPose(home[0], home[1], home[2], desk[0], desk[1], desk[2], t - DRAWER_PICK_AT, tmp)
                setOut(PEN)
                top = (1 - drawerEaseOutCubic((t - DRAWER_CLEAR_AT) / 0.5)).toFloat()
            }
            ch = drawerClamp01((t - 4.95) / 0.25) * (1 - drawerClamp01((t - 6.8) / 0.25))
            keep = auto
        } else {
            if (mode == MODE_USER && auto && !dragging && at - lastTouch > IDLE) {
                // Back to rest: soft-close, everything on the desk flies home.
                timing(0.0, RETURN - 0.05, true)
                for (i in 0 until n) {
                    if (place[i] == 1 || place[i] == 2) {
                        val d = DoubleArray(3)
                        deskOf(i, slot[i], d)
                        putBack(i, d)
                    }
                }
                returnAt = at
                mode = MODE_RETURN
            }
            o = openNow()
            // The drawer's acceleration, smoothed, shakes the springs (four substeps a frame).
            val v = (o - prevOpen) / max(dt, 0.001)
            val a = (v - prevV) / max(dt, 0.001)
            acc += (a - acc) * min(1.0, dt * 30)
            if (prevOpen > 0.5 && o <= 0.5 && v < 0 && mode == MODE_USER) feel = feel or FEEL_SOFT
            prevOpen = o
            prevV = v
            for (i in 0 until n) {
                if (!reduced) repeat(4) { drawerStepItem(items[i].spring, lag[i], acc, dt / 4) }
                homeOf(i, o, lag[i][0], home)
                px[i] = home[0]; py[i] = home[1]; pr[i] = home[2]
            }
            for (i in 0 until n) {
                val p = place[i]
                if (p == 0) continue
                val u = at - start[i]
                deskOf(i, slot[i], desk)
                if (p == 1 || p == 2) {
                    drawerPickPose(from[i][0], from[i][1], from[i][2], desk[0], desk[1], desk[2], u, tmp)
                    if (p == 1 && u >= DRAWER_LIFT_FOR + DRAWER_FLY_FOR) {
                        place[i] = 2
                        chipAt = at
                    }
                } else {
                    homeOf(i, o, lag[i][0], home)
                    drawerPickPose(from[i][0], from[i][1], from[i][2], home[0], home[1], home[2], u, tmp)
                    if (u >= DRAWER_LIFT_FOR + DRAWER_FLY_FOR) {
                        place[i] = 0
                        lag[i][0] = 0.0
                        lag[i][1] = 0.0
                        homeOf(i, o, 0.0, home)
                        px[i] = home[0]; py[i] = home[1]; pr[i] = home[2]
                        continue
                    }
                }
                setOut(i)
            }
            ch = drawerClamp01((at - chipAt) / 0.25) * (1 - drawerClamp01((at - chipAt - 2.4) / 0.25))
            // Still when the drawer, the springs and every flight have settled.
            moving = mode == MODE_RETURN || dragging || abs(v) > 0.05 || abs(acc) > 0.5 || (at - chipAt > 0 && at - chipAt < 2.8) || anim != 0
            for (i in 0 until n) {
                if (abs(lag[i][0]) > 0.02 || abs(lag[i][1]) > 0.05 || place[i] == 1 || place[i] == 3) moving = true
                if (place[i] == 2 && at - start[i] < DRAWER_LIFT_FOR + DRAWER_FLY_FOR + 0.6) moving = true
            }
            keep = moving || (auto && mode == MODE_USER)
            if (mode == MODE_RETURN && at - returnAt >= RETURN) {
                mode = MODE_AUTO
                clock = 0.0
                setOpen(0.0)
                resetSim()
                chipName.value = "Blue fineliner"
            }
        }
        shownOpen = o
        topAlpha = top
        chip.floatValue = ch.toFloat()
        if (!moving && idle) return keep
        idle = !moving
        frame.intValue += 1
        return keep || !idle
    }

    private fun setOut(i: Int) {
        isOut[i] = true
        px[i] = tmp[0]; py[i] = tmp[1]; pr[i] = tmp[2]; pLift[i] = tmp[3]; pScale[i] = tmp[4]
    }
}

/** Reused objects for the sprite draws. */
private class DrawerPaints(art: DrawerArt) {
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val outShadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val outFace = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val src = Rect()
    val dst = RectF()
    val l = art.layout
    // Inside the cabinet it is darker: the last few centimetres before the board.
    val dark = Paint().apply {
        shader = LinearGradient(0f, l.boardY - 46, 0f, l.boardY, 0x001E1004, android.graphics.Color.argb(140, 30, 16, 4), Shader.TileMode.CLAMP)
    }
    val cellPx = art.cells.map { c ->
        Rect((c.x * art.px).roundToInt(), (c.y * art.px).roundToInt(), ((c.x + c.w) * art.px).roundToInt(), ((c.y + c.h) * art.px).roundToInt())
    }
}

/** Draws sprite i of [atlas] centred at (x, y), turned by rot, at [scale] dp per dp of the item. */
private fun sprite(c: android.graphics.Canvas, atlas: Bitmap, p: DrawerPaints, i: Int, x: Double, y: Double, rot: Double, scale: Double, px: Float, paint: Paint) {
    val r = p.cellPx[i]
    c.save()
    c.translate(x.toFloat(), y.toFloat())
    c.rotate((rot * 180 / PI).toFloat())
    val k = (scale / px).toFloat()
    c.scale(k, k)
    p.dst.set(-r.width() / 2f, -r.height() / 2f, r.width() / 2f, r.height() / 2f)
    c.drawBitmap(atlas, r, p.dst, paint)
    c.restore()
}

/**
 * "Drawers · Office supplies": a bottom sheet that is a wooden drawer full of supplies on springs;
 * pull it to half or full, tap a supply to lift it out onto the desk.
 *
 * @param autoPlay opens the drawer to half and full, picks a pen onto the desk, soft-closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerCabinetSheet(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Paper)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val art by produceState<DrawerArt?>(null, w, h, top, bottom) {
            value = withContext(Dispatchers.Default) { drawerBakeArt(w, h, top, bottom, density.density) }
        }
        val a = art ?: return@BoxWithConstraints
        val l = a.layout
        val state = remember(a, reduced) { DrawerState(a, reduced) }
        val running = remember(state) { mutableStateOf(true) }
        val isRunning by running

        LaunchedEffect(state, auto, isRunning) {
            if (!isRunning) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { t ->
                    val dt = if (last < 0) 1.0 / 60 else min((t - last) / 1e9, 0.05)
                    last = t
                    val keep = state.step(dt, auto)
                    if (state.feel != 0) {
                        if (state.mode != MODE_AUTO) {
                            view.performHapticFeedback(if (state.feel and FEEL_LIGHT != 0) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
                        }
                        state.feel = 0
                    }
                    keep
                }
                if (!keep) {
                    running.value = false
                    break
                }
            }
        }

        val paints = remember(a) { DrawerPaints(a) }
        val sheetH = DRAWER_SHADOW_PAD + DRAWER_PANEL + DRAWER_DEPTH
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Supplies drawer. Drag the brass pull to open it; tap a supply to put it on the desk." }
                .pointerInput(state) {
                    val d = density.density
                    val slop = 6 * d
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = false
                        var holding = false
                        running.value = true
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val total = change.position - down.position
                                if (!moved && total.getDistance() > slop) {
                                    moved = true
                                    holding = state.dragStart((down.position.y / d).toDouble())
                                }
                                if (holding) {
                                    state.dragMove((total.y / d).toDouble())
                                    running.value = true
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (!moved && change.uptimeMillis - down.uptimeMillis <= 350) {
                                        state.tap((change.position.x / d).toDouble(), (change.position.y / d).toDouble())
                                    }
                                    break
                                }
                            }
                        } finally {
                            if (holding) state.dragEnd((tracker.calculateVelocity().y / d).toDouble())
                            running.value = true
                        }
                    }
                },
        ) {
            state.frame.intValue
            val d = density.density
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(d, d)
                paints.dst.set(0f, 0f, l.w, l.h)
                c.drawBitmap(a.desk, null, paints.dst, paints.bitmap)
                c.save()
                c.clipRect(0f, 0f, l.w, l.boardY)
                val sy = (l.boardY - DRAWER_PANEL - DRAWER_SHADOW_PAD - state.shownOpen).toFloat()
                paints.dst.set(l.sheetX, sy, l.sheetX + l.sheetW, sy + sheetH)
                c.drawBitmap(a.sheet, null, paints.dst, paints.bitmap)
                for (i in 0 until state.n) {
                    if (state.isOut[i]) continue
                    sprite(c, a.shadows, paints, i, state.px[i] + 1.5, state.py[i] + 2.5, state.pr[i], 1.0, a.px, paints.bitmap)
                }
                for (i in 0 until state.n) {
                    if (state.isOut[i]) continue
                    sprite(c, a.faces, paints, i, state.px[i], state.py[i], state.pr[i], 1.0, a.px, paints.bitmap)
                }
                val darkY = (l.boardY - max(0.0, min(46.0, state.shownOpen))).toFloat()
                c.drawRect(l.sheetX, darkY, l.sheetX + l.sheetW, darkY + 46, paints.dark)
                c.restore()
                paints.dst.set(0f, l.boardY - 20, l.w, l.h)
                c.drawBitmap(a.board, null, paints.dst, paints.bitmap)
                if (state.topAlpha > 0.001f) {
                    paints.outShadow.alpha = (0.6f * state.topAlpha * 255).roundToInt()
                    paints.outFace.alpha = (state.topAlpha * 255).roundToInt()
                    for (i in 0 until state.n) {
                        if (!state.isOut[i]) continue
                        val lift = state.pLift[i]
                        val sc = state.pScale[i]
                        sprite(c, a.shadows, paints, i, state.px[i] + 3 + 12 * lift, state.py[i] + 4 + 22 * lift, state.pr[i], sc * (1 + 0.06 * lift), a.px, paints.outShadow)
                    }
                    for (i in 0 until state.n) {
                        if (!state.isOut[i]) continue
                        sprite(c, a.faces, paints, i, state.px[i], state.py[i], state.pr[i], state.pScale[i], a.px, paints.outFace)
                    }
                }
                c.restore()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("DRAWERS", style = TextStyle(color = Color(0xFF9A6A3A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
            BasicText("Office supplies", style = TextStyle(color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
            BasicText("Top drawer · ${state.n} items · pull to open", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The chip: "<supply> · on your desk", right-aligned over the mat.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = 30.dp)
                .offset(y = (l.matY + 14).dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val c = state.chip.floatValue
                        alpha = c
                        translationY = 8.dp.toPx() * (1 - c)
                    }
                    .widthIn(max = 240.dp)
                    .height(30.dp)
                    .cssBoxShadow(Color(0x2E141E14), 6.dp, 14.dp, 15.dp)
                    .cssBoxShadow(Color(0x40141E14), 1.dp, 2.dp, 15.dp)
                    .background(Color(0xF0F6F0E4), RoundedCornerShape(15.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    "${state.chipName.value} · on your desk",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
