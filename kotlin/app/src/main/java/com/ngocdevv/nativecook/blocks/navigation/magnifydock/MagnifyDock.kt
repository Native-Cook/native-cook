package com.ngocdevv.nativecook.blocks.navigation.magnifydock

import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The dock. Icons are ICON dp at rest, GAP apart, inside PAD of glass that sits DOCK_LIFT dp
// above the navigation bar. Icons are vector art, redrawn at every size, so they stay sharp.
private const val ICON = 40f
private const val GAP = 7f
private const val PAD = 8f
private const val PITCH = ICON + GAP
private const val DOCK_LIFT = 8f
private const val DOCK_RADIUS = 18f
// Magnification: an icon grows with a cosine bell of its resting centre's distance from the
// focus, up to MAX_SCALE under it and nothing past REACH dp. Grown icons push the others out from
// the dock's centre; on a screen too narrow for that the whole dock is scaled to fit within EDGE
// dp of its sides.
private const val MAX_SCALE = 1.55f
private const val REACH = 110f
private const val EDGE = 6f
// The focus follows the finger on a critically damped spring of natural frequency FOCUS_OMEGA
// (rad/s, ~40 ms behind a moving finger), and the magnification eases in and out on another
// (MAG_OMEGA), letting go when the finger rises more than LEAVE dp above the dock. Both springs are
// stepped with their exact solution, so they stay smooth at any frame rate.
private const val FOCUS_OMEGA = 50f
private const val MAG_OMEGA = 15f
private const val LEAVE = 120f
// The name popover: one pill that glides between icons on a critically damped spring
// (LABEL_OMEGA), resizing to each name while the old name fades out (NAME_OUT s) and the new one
// in (NAME_FADE s, once the old is below NAME_GATE); it appears and leaves with a fade, a 4 dp rise
// and a 0.94 → 1 scale on its own spring (SHOW_OMEGA). It sits LABEL_GAP dp above the icon under
// the focus.
private const val LABEL_OMEGA = 26f
private const val SHOW_OMEGA = 18f
private const val NAME_FADE = 0.07f
private const val NAME_OUT = 0.035f
private const val NAME_GATE = 0.25f
private const val LABEL_GAP = 12f
private const val LABEL_HEIGHT = 28f
private const val LABEL_PAD = 12f
private const val LABEL_TEXT = 13f
// Launching: the icon jumps BOUNCES times, each BOUNCE_TIME s and BOUNCE_DECAY as high as the one
// before, then its running dot fades in.
private const val BOUNCE_HEIGHT = 22f
private const val BOUNCE_TIME = 0.42f
private const val BOUNCES = 2
private const val BOUNCE_DECAY = 0.5f
private const val DOT_AT = 0.35f
// Editing: a press held LONG_PRESS s without moving more than SLOP dp wiggles every icon by
// ±WIGGLE_DEG at WIGGLE_HZ and lifts the pressed one to LIFT_SCALE. Moving it past half a slot
// reorders the others, which spring to their new places (SLOT_STIFFNESS, SLOT_DAMPING per unit mass).
private const val LONG_PRESS = 0.5f
private const val SLOP = 8f
private const val TAP_TIME = 0.4f
private const val WIGGLE_DEG = 2.4f
private const val WIGGLE_HZ = 4.2f
private const val LIFT_SCALE = 1.25f
private const val SLOT_STIFFNESS = 320f
private const val SLOT_DAMPING = 30f

// autoPlay (seconds): the finger lands on the first icon, slides to the last and back to Hum,
// lifts and taps it to launch it. Then it holds Wayfare until the dock starts wiggling, carries it
// two places right, puts it down, carries it back and taps Done; Hum quits before the loop starts over.
private const val LOOP = 12f
private val Sweep = listOf(0.8f to 0f, 3.0f to 6f, 4.2f to 2f)
private const val LIFT_AT = 4.35f
private const val TAP_AT = 4.95f
private const val HOLD_AT = 6.3f
private const val HOLD_SLOT = 3

private class Carry(val t0: Float, val t1: Float, val from: Int, val to: Int)

private val Carries = listOf(Carry(6.95f, 7.75f, 3, 5), Carry(8.45f, 9.25f, 5, 3))
private const val DROP_LAG = 0.15f
private const val DONE_AT = 9.95f
private const val QUIT_AT = 10.4f
private const val USER_HOLD = 4f

private fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

/**
 * One frame of a critically damped spring of natural frequency [omega], solved exactly: the value
 * and its speed after [dt] s, heading for [target].
 */
private fun critical(x: Float, v: Float, target: Float, omega: Float, dt: Float): Pair<Float, Float> {
    val d = x - target
    val e = exp(-omega * dt)
    val k = (v + omega * d) * dt
    return Pair(target + (d + k) * e, (v - omega * k) * e)
}

private fun smooth(u: Float): Float {
    val t = clamp(u, 0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** 1 while a virtual touch from t0 to t1 is down, fading in and out over 0.2 s around it. */
private fun touchWindow(c: Float, t0: Float, t1: Float) = clamp(min((c - t0 + 0.25f) / 0.2f, (t1 + 0.2f - c) / 0.2f), 0f, 1f)

/** Width of the dock at rest, glass included. */
private fun restingWidth(count: Int) = count * ICON + (count - 1) * GAP + 2f * PAD

/** Centre of slot [slot] at rest, for a dock centred on a screen [width] wide. */
private fun slotCentre(slot: Float, count: Int, width: Float) = width / 2f - restingWidth(count) / 2f + PAD + ICON / 2f + slot * PITCH

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Float) = floor(x + 0.5f)

/** Height of a launch bounce [age] s after the tap: BOUNCES arches, each lower than the last. */
private fun bounceAt(age: Float): Float {
    if (age < 0f || age >= BOUNCES * BOUNCE_TIME) return 0f
    val k = floor(age / BOUNCE_TIME)
    val u = age / BOUNCE_TIME - k
    return BOUNCE_HEIGHT * BOUNCE_DECAY.pow(k) * 4f * u * (1f - u)
}

/** Where every icon sits and how big it is. */
private class DockLayout(count: Int) {
    val cx = FloatArray(count)
    val scale = FloatArray(count)
    var dockWidth = 0f
    var fit = 1f
    private val base = FloatArray(count)
    private val extra = FloatArray(count)

    /** For icons resting at [slots] and the focus at [focusX]. */
    fun update(slots: FloatArray, focusX: Float, mag: Float, width: Float) {
        val count = slots.size
        var total = 0f
        for (i in 0 until count) {
            val c = slotCentre(slots[i], count, width)
            base[i] = c
            val d = abs(c - focusX)
            val bell = if (d < REACH) (cos(PI.toFloat() * d / REACH) + 1f) / 2f else 0f
            extra[i] = ICON * (MAX_SCALE - 1f) * bell * max(0f, mag)
            total += extra[i]
        }
        val full = restingWidth(count) + total
        fit = if (full > 0f) min(1f, (width - 2f * EDGE) / full) else 1f
        for (i in 0 until count) {
            // Everything grown to this icon's left pushes it right, and the dock stays centred.
            var before = 0f
            for (j in 0 until count) {
                if (j != i && (slots[j] < slots[i] || (slots[j] == slots[i] && j < i))) before += extra[j]
            }
            val c = base[i] + before + extra[i] / 2f - total / 2f
            cx[i] = width / 2f + (c - width / 2f) * fit
            scale[i] = (1f + extra[i] / ICON) * fit
        }
        dockWidth = full * fit
    }

    /** The icon under [x], or -1. */
    fun iconAt(x: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in cx.indices) {
            val d = abs(x - cx[i])
            if (d < (ICON * scale[i]) / 2f + GAP / 2f && d < bestD) {
                best = i
                bestD = d
            }
        }
        return best
    }
}

/** The dock's state, stepped once a frame like the RN frame callback, and the touch that drives it. */
private class DockEngine(val count: Int) {
    var time = 0f
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    val dockBottom get() = height - bottom - DOCK_LIFT
    val baseline get() = dockBottom - PAD
    val dockTop get() = dockBottom - ICON - 2f * PAD
    var fingerX = -1000f
    var fingerY = 0f
    var focusX = -1000f
    var focusV = 0f
    var mag = 0f
    var magV = 0f
    var magTarget = 0f
    // Resting slot of each app (by id), springing towards its index in `order`.
    val slots = FloatArray(count) { it.toFloat() }
    val slotSpeed = FloatArray(count)
    var order = MutableList(count) { it }
    val launchedAt = FloatArray(count) { -100f }
    val runningOn = FloatArray(count)
    val running = FloatArray(count)
    var editing = false
    var wiggle = 0f
    var dragId = -1
    var dragY = 0f
    var pressing = false
    var moved = false
    var inDock = false
    var pressAt = 0f
    var startX = 0f
    var startY = 0f
    // autoPlay
    var clock = 0f
    var looping = false
    var hold = 0f
    var autoId = -1
    var ghostX = 0f
    var ghostY = 0f
    var ghostAlpha = 0f
    var ghostPress = 0f
    var reduced = false
    val layout = DockLayout(count)
    // The popover: centre x and speed, top, width and speed, how far it shows and that speed, each
    // name's opacity, and each name's measured width.
    var labelX = 0f
    var labelXV = 0f
    var labelY = 0f
    var labelW = 0f
    var labelWV = 0f
    var labelShow = 0f
    var labelShowV = 0f
    val names = FloatArray(count)
    var nameWidths = FloatArray(count) { 60f }
    // Bumped once a frame; the drawing reads it, so it is drawn again without composing.
    var tick by mutableLongStateOf(0L)

    var light: () -> Unit = {}
    var medium: () -> Unit = {}
    var selection: () -> Unit = {}
    var launched: (Int) -> Unit = {}

    private fun relayout() = layout.update(slots, focusX, mag, width)

    fun launch(id: Int, user: Boolean) {
        if (id < 0 || id >= count) return
        if (runningOn[id] < 1f) {
            launchedAt[id] = time
            runningOn[id] = 1f
        }
        if (user) {
            light()
            launched(id)
        }
    }

    /** Puts a carried icon down where it is; its slot spring carries it home. */
    fun drop(user: Boolean) {
        val id = dragId
        if (id < 0) return
        val half = restingWidth(count) / 2f - PAD - ICON / 2f
        val x = clamp(fingerX, width / 2f - half, width / 2f + half)
        slots[id] = (x - slotCentre(0f, count, width)) / PITCH
        dragId = -1
        if (user) light()
    }

    /** Moves a carried icon with the finger; past half a slot it takes the new place. */
    fun carryTo(x: Float, y: Float) {
        val id = dragId
        if (id < 0) return
        val dy = y - startY
        dragY = if (dy < -70f) -70f + (dy + 70f) * 0.2f else if (dy > 12f) 12f + (dy - 12f) * 0.2f else dy
        val slot = clamp(jsRound((x - slotCentre(0f, count, width)) / PITCH), 0f, (count - 1).toFloat()).toInt()
        if (order.indexOf(id) != slot) {
            order.remove(id)
            order.add(slot, id)
            if (!looping || hold > 0f) selection()
        }
    }

    fun frame(dt: Float) {
        time += dt
        val t = time
        relayout()

        // A press held still on the dock starts editing and picks the icon up.
        if (pressing && !moved && inDock && !editing && t - pressAt > LONG_PRESS) {
            editing = true
            magTarget = 0f
            dragId = layout.iconAt(fingerX)
            startY = fingerY
            dragY = 0f
            medium()
        }

        // The focus trails the finger on a stiff, critically damped spring; from rest it starts there.
        if ((mag < 0.02f && magTarget > 0f) || reduced) {
            focusX = fingerX
            focusV = 0f
        } else {
            val (fx, fv) = critical(focusX, focusV, fingerX, FOCUS_OMEGA, dt)
            focusX = fx
            focusV = fv
        }
        // The magnification swells and settles on a critically damped spring.
        val (m, mv) = critical(mag, magV, magTarget, MAG_OMEGA, dt)
        magV = mv
        mag = if (abs(m - magTarget) < 0.0005f && abs(mv) < 0.01f) magTarget else clamp(m, 0f, 1f)
        relayout()
        stepLabel(dt)

        wiggle += ((if (editing) 1f else 0f) - wiggle) * (1f - exp(-dt * 10f))
        if (dragId < 0 && dragY != 0f) {
            val y = dragY * exp(-dt * 14f)
            dragY = if (abs(y) < 0.1f) 0f else y
        }

        // Each icon's slot springs to its place in `order`; a carried icon is left to the finger.
        for (i in 0 until count) {
            if (i == dragId) continue
            val goal = order.indexOf(i).toFloat()
            if (abs(goal - slots[i]) <= 0.001f && abs(slotSpeed[i]) <= 0.001f) continue
            if (reduced) {
                slots[i] = goal
                slotSpeed[i] = 0f
                continue
            }
            // Small steps, so a long frame cannot make the spring overshoot and fling the icon away.
            val steps = max(1, kotlin.math.ceil(dt / 0.004f).toInt())
            val h = dt / steps
            repeat(steps) {
                slotSpeed[i] += (SLOT_STIFFNESS * (goal - slots[i]) - SLOT_DAMPING * slotSpeed[i]) * h
                slots[i] += slotSpeed[i] * h
            }
            if (abs(goal - slots[i]) < 0.001f && abs(slotSpeed[i]) < 0.01f) {
                slots[i] = goal
                slotSpeed[i] = 0f
            }
        }

        // Running dots fade in once the launch bounce is under way, and out when an app quits.
        for (i in 0 until count) {
            val goal = if (runningOn[i] > 0f && t - launchedAt[i] > DOT_AT) 1f else 0f
            running[i] = if (abs(goal - running[i]) > 0.002f) running[i] + (goal - running[i]) * (1f - exp(-dt * 9f)) else goal
        }

        script(dt)
        relayout()
        tick += 1
    }

    private fun script(dt: Float) {
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping && !pressing) {
                // The loop starts over from rest: editing ends, apps quit and the icons go home.
                clock = 0f
                editing = false
                dragId = -1
                magTarget = 0f
                runningOn.fill(0f)
                order = MutableList(count) { it }
            } else if (hold <= 0f && pressing) {
                hold = USER_HOLD
            }
            return
        }
        if (!looping || width <= 0f) return
        val w = width
        val before = clock
        var c = before + dt
        if (c >= LOOP) c -= LOOP
        clock = c

        val dockY = baseline - ICON / 2f
        // The sweep: eased from stop to stop.
        if (c >= Sweep[0].first && c < LIFT_AT) {
            var x = slotCentre(Sweep[0].second, count, w)
            for (k in 1 until Sweep.size) {
                val (t0, s0) = Sweep[k - 1]
                val (t1, s1) = Sweep[k]
                if (c >= t0) x = slotCentre(s0 + (s1 - s0) * smooth((c - t0) / (t1 - t0)), count, w)
            }
            fingerX = x
            ghostX = x
            ghostY = dockY
        }
        val last = Sweep.last().second.toInt()
        if (crossed(before, c, Sweep[0].first)) magTarget = 1f
        if (crossed(before, c, LIFT_AT)) magTarget = 0f
        if (crossed(before, c, TAP_AT - 0.3f)) {
            ghostX = slotCentre(last.toFloat(), count, w)
            ghostY = dockY
        }
        if (crossed(before, c, TAP_AT)) {
            val id = order.getOrElse(last) { -1 }
            autoId = id
            launch(id, false)
        }

        // Editing: a long hold, two carries and Done.
        if (crossed(before, c, HOLD_AT - 0.25f)) {
            ghostX = slotCentre(HOLD_SLOT.toFloat(), count, w)
            ghostY = dockY
        }
        if (crossed(before, c, HOLD_AT + LONG_PRESS)) {
            editing = true
            fingerX = slotCentre(HOLD_SLOT.toFloat(), count, w)
            startY = dockY
            dragId = order.getOrElse(HOLD_SLOT) { -1 }
        }
        for ((k, carry) in Carries.withIndex()) {
            if (k > 0 && crossed(before, c, carry.t0 - 0.2f)) {
                fingerX = slotCentre(carry.from.toFloat(), count, w)
                startY = dockY
                dragId = order.getOrElse(carry.from) { -1 }
            }
            if (c >= carry.t0 && c <= carry.t1 + DROP_LAG) {
                val u = smooth((c - carry.t0) / (carry.t1 - carry.t0))
                val x = slotCentre(carry.from + (carry.to - carry.from) * u, count, w)
                val y = dockY - 16f * sin(PI.toFloat() * u)
                fingerX = x
                ghostX = x
                ghostY = y
                carryTo(x, y)
            }
            if (crossed(before, c, carry.t1 + DROP_LAG)) drop(false)
        }
        if (crossed(before, c, DONE_AT - 0.3f)) {
            ghostX = w - 52f
            ghostY = top + 31f
        }
        if (crossed(before, c, DONE_AT)) editing = false
        if (crossed(before, c, QUIT_AT) && autoId in 0 until count) runningOn[autoId] = 0f

        // The virtual finger shows while it touches: the sweep, the tap, the hold and carries, Done.
        var alpha = touchWindow(c, Sweep[0].first, LIFT_AT)
        alpha = max(alpha, touchWindow(c, TAP_AT - 0.08f, TAP_AT + 0.08f))
        alpha = max(alpha, touchWindow(c, HOLD_AT, Carries[0].t1 + DROP_LAG))
        alpha = max(alpha, touchWindow(c, Carries[1].t0 - 0.2f, Carries[1].t1 + DROP_LAG))
        alpha = max(alpha, touchWindow(c, DONE_AT - 0.08f, DONE_AT + 0.08f))
        ghostAlpha = alpha
        val press = max(clamp(1f - abs(c - TAP_AT) / 0.12f, 0f, 1f), clamp(1f - abs(c - DONE_AT) / 0.12f, 0f, 1f))
        ghostPress = if (c > HOLD_AT && c < HOLD_AT + LONG_PRESS) smooth((c - HOLD_AT) / 0.2f) else press
    }

    // One touch handler from touch down, so taps and still long presses reach it too: hover, tap
    // to launch, hold to edit, carry to reorder.

    /** The popover: over the icon under the focus while the dock is swollen, gliding between icons. */
    private fun stepLabel(dt: Float) {
        val over = if (mag > 0.3f && magTarget > 0f && !editing && dragId < 0) layout.iconAt(focusX) else -1
        if (over >= 0) {
            val tx = layout.cx[over]
            val ty = baseline - ICON * layout.scale[over] - LABEL_GAP - LABEL_HEIGHT - 6f
            val tw = nameWidths[over] + LABEL_PAD * 2f
            if (labelShow < 0.02f || reduced) {
                // Appearing: it starts right over its icon, showing its name.
                labelX = tx
                labelXV = 0f
                labelY = ty
                labelW = tw
                labelWV = 0f
                for (i in names.indices) names[i] = if (i == over) 1f else 0f
            } else {
                val (nx, nxv) = critical(labelX, labelXV, tx, LABEL_OMEGA, dt)
                labelX = nx
                labelXV = nxv
                labelY += (ty - labelY) * (1f - exp(-dt * LABEL_OMEGA))
                val (nw, nwv) = critical(labelW, labelWV, tw, LABEL_OMEGA, dt)
                labelW = max(LABEL_PAD * 2f, nw)
                labelWV = nwv
                // The old name goes first and quickly; the new one comes in once it has mostly gone,
                // so two names never sit on top of each other.
                val out = 1f - exp(-dt / NAME_OUT)
                var left = 0f
                for (i in names.indices) {
                    if (i == over) continue
                    names[i] = if (names[i] < 0.01f) 0f else names[i] * (1f - out)
                    left = max(left, names[i])
                }
                if (left < NAME_GATE) names[over] += (1f - names[over]) * (1f - exp(-dt / NAME_FADE))
            }
        }
        if (reduced) {
            labelShow = if (over >= 0) 1f else 0f
            labelShowV = 0f
        } else {
            val (sh, shv) = critical(labelShow, labelShowV, if (over >= 0) 1f else 0f, SHOW_OMEGA, dt)
            labelShow = clamp(sh, 0f, 1f)
            labelShowV = shv
            if (over < 0 && labelShow < 0.001f && abs(labelShowV) < 0.01f) {
                labelShow = 0f
                labelShowV = 0f
            }
        }
    }

    fun begin(x: Float, y: Float) {
        // An icon the demo was carrying is put down where it is, not handed to this finger.
        if (dragId >= 0) drop(false)
        hold = USER_HOLD
        ghostAlpha = 0f
        pressing = true
        moved = false
        pressAt = time
        startX = x
        startY = y
        fingerX = x
        fingerY = y
        relayout()
        val dockHalf = layout.dockWidth / 2f + 10f
        val onDock = y > dockTop - 24f && abs(x - width / 2f) < dockHalf
        inDock = onDock
        if (editing) {
            if (onDock) {
                dragId = layout.iconAt(x)
                dragY = 0f
            }
        } else if (onDock) {
            magTarget = 1f
        }
    }

    fun move(x: Float, y: Float) {
        fingerX = x
        fingerY = y
        if (hypot(x - startX, y - startY) > SLOP) moved = true
        if (dragId >= 0) {
            carryTo(x, y)
        } else if (!editing && inDock) {
            // Rising well above the dock lets go of it, as a pointer leaving it would.
            magTarget = if (y < dockTop - LEAVE) 0f else 1f
        }
    }

    fun end() {
        val wasPressing = pressing
        pressing = false
        magTarget = 0f
        hold = USER_HOLD
        if (!wasPressing) return
        val quick = !moved && time - pressAt < TAP_TIME
        if (dragId >= 0) {
            drop(true)
            return
        }
        relayout()
        if (editing) {
            // A tap anywhere off the icons (or on Done) ends editing.
            if (quick && (!inDock || layout.iconAt(startX) < 0)) {
                editing = false
                selection()
            }
            return
        }
        if (quick && inDock) launch(layout.iconAt(startX), true)
    }

    /** Where icon [id] is drawn: its centre x, its size, how far it is lifted and turned. */
    fun pose(id: Int): FloatArray {
        val dragging = dragId == id
        val half = restingWidth(count) / 2f - PAD - ICON / 2f
        val cx = if (dragging) clamp(fingerX, width / 2f - half, width / 2f + half) else layout.cx[id]
        val size = ICON * (if (dragging) LIFT_SCALE * layout.fit else layout.scale[id])
        val hop = if (reduced) 0f else bounceAt(time - launchedAt[id])
        val wob = if (dragging || reduced) 0f else wiggle
        val phase = time * WIGGLE_HZ * PI.toFloat() * 2f + id * 1.7f
        val lift = if (dragging) dragY - 8f else 0f
        return floatArrayOf(cx, size, hop - lift - wob * 0.7f * sin(phase * 0.5f), wob * WIGGLE_DEG * sin(phase))
    }
}

private val White = Color.White
private val LabelFill = Color(22 / 255f, 26 / 255f, 36 / 255f, 0.86f)

/**
 * A desktop dock over a blue-hour mountain wallpaper. Sliding a finger along it swells the icons
 * near the finger on a smooth falloff, pushing their neighbours apart, with the name of the one
 * under the finger floating above it. A tap launches an app with two bounces and a running dot; a
 * long press sets the icons wiggling, and an icon can then be dragged to a new place in the row.
 *
 * @param autoPlay Sweeps a virtual finger along the dock, launches an app and rearranges the icons, on a loop.
 * @param onLaunch Called with an app's name when it is launched.
 */
@Composable
fun MagnifyDock(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onLaunch: ((String) -> Unit)? = null,
) {
    val apps = DockApps
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val launchCallback by rememberUpdatedState(onLaunch)
    val engine = remember { DockEngine(apps.size) }

    val insets = WindowInsets.safeDrawing
    engine.top = insets.getTop(density) / density.density
    engine.bottom = insets.getBottom(density) / density.density
    engine.reduced = reduced
    engine.light = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    engine.medium = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    engine.selection = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.launched = { id -> apps.getOrNull(id)?.let { launchCallback?.invoke(it.name) } }

    LaunchedEffect(engine, auto) {
        engine.looping = auto
        engine.clock = 0f
        engine.hold = 0f
    }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) engine.frame(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1D3150))
            .semantics { contentDescription = "Dock: ${apps.joinToString(", ") { it.name }}" }
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.begin(down.position.x / density.density, down.position.y / density.density)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.position != change.previousPosition) {
                            engine.move(change.position.x / density.density, change.position.y / density.density)
                        }
                        change.consume()
                    }
                    engine.end()
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        engine.width = width
        engine.height = height
        val top = engine.top
        val d = density.density

        // The wallpaper never changes, so this canvas reads no per-frame state.
        Canvas(Modifier.fillMaxSize()) {
            scale(d, d, pivot = Offset.Zero) { drawDockWallpaper(width, height) }
        }

        Column(Modifier.padding(start = 26.dp, top = (top + 22f).dp)) {
            BasicText("Tuesday 14 October", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = White.copy(alpha = 0.88f), letterSpacing = 0.1.sp))
            BasicText(
                "9:41",
                Modifier.offset(y = (-4).dp),
                style = TextStyle(fontSize = 84.sp, fontWeight = FontWeight.Bold, color = White, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"),
            )
        }

        // Frosted glass: the wallpaper again, blurred, clipped to the dock as it grows.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    engine.tick
                    val left = width / 2f - engine.layout.dockWidth / 2f
                    val rect = RoundRect(
                        left * d, engine.dockTop * d, (left + max(1f, engine.layout.dockWidth)) * d, engine.dockBottom * d,
                        CornerRadius(DOCK_RADIUS * d),
                    )
                    clip = true
                    shape = GenericShape { _, _ -> addRoundRect(rect) }
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val sigma = 18f * d
                        // Android turns a blur radius into sigma = 0.57735 · radius + 0.5.
                        val radius = max(0.1f, (sigma - 0.5f) / 0.57735f)
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    },
            ) {
                scale(d, d, pivot = Offset.Zero) { drawDockWallpaper(width, height) }
            }
        }

        // The dock's shadow, tint and rim, the running dots and the icons, the carried one on top.
        Canvas(Modifier.fillMaxSize()) {
            engine.tick
            scale(d, d, pivot = Offset.Zero) {
                val dockW = max(1f, engine.layout.dockWidth)
                val left = width / 2f - dockW / 2f
                val dockH = engine.dockBottom - engine.dockTop
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        paint.color = android.graphics.Color.argb(115, 4, 8, 18)
                        paint.maskFilter = BlurMaskFilter(max(0.1f, (12f * d - 0.5f) / 0.57735f) / d, BlurMaskFilter.Blur.NORMAL)
                        canvas.nativeCanvas.drawRoundRect(
                            left + 6f, engine.dockTop + 10f, left + max(7f, dockW - 6f), engine.dockTop + 10f + dockH,
                            DOCK_RADIUS, DOCK_RADIUS, paint,
                        )
                    }
                }
                val dock = Path().apply { addRoundRect(RoundRect(left, engine.dockTop, left + dockW, engine.dockBottom, CornerRadius(DOCK_RADIUS))) }
                drawPath(dock, Color(214, 224, 240).copy(alpha = 0.2f))
                drawPath(
                    dock,
                    Brush.linearGradient(listOf(White.copy(alpha = 0.55f), White.copy(alpha = 0.12f)), start = Offset(0f, engine.dockTop), end = Offset(0f, engine.dockBottom)),
                    style = Stroke(width = 1f),
                )
                for (id in apps.indices) {
                    val alpha = engine.running[id]
                    if (alpha > 0.001f) drawCircle(White.copy(alpha = 0.92f * alpha), 2.2f, Offset(engine.layout.cx[id], engine.dockBottom - 3.5f))
                }
                for (onTop in listOf(false, true)) {
                    for ((id, app) in apps.withIndex()) {
                        if ((engine.dragId == id) != onTop) continue
                        val p = engine.pose(id)
                        val s = max(0.01f, p[1] / 100f)
                        withTransform({
                            translate(p[0], engine.baseline - p[1] / 2f - p[2])
                            rotate(p[3], pivot = Offset.Zero)
                            scale(s, s, pivot = Offset.Zero)
                            translate(-50f, -50f)
                        }) {
                            drawDockIcon(app, s * d)
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = (top + 14f).dp, end = 20.dp)
                .graphicsLayer {
                    engine.tick
                    alpha = engine.wiggle
                    translationY = ((1f - engine.wiggle) * -8f).dp.toPx()
                }
                .height(34.dp)
                .background(White.copy(alpha = 0.22f), RoundedCornerShape(17.dp))
                .border(0.5.dp, White.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Done", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White))
        }

        DockPopover(engine, apps, width)

        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer {
                    engine.tick
                    alpha = engine.ghostAlpha * 0.85f
                    translationX = (engine.ghostX - 21f).dp.toPx()
                    translationY = (engine.ghostY - 21f).dp.toPx()
                    val s = 1f - 0.2f * engine.ghostPress
                    scaleX = s
                    scaleY = s
                }
                .background(White.copy(alpha = 0.28f), CircleShape)
                .border(1.5.dp, White.copy(alpha = 0.7f), CircleShape),
        )
    }
}

/**
 * The name popover: a single dark pill with a tip, gliding from icon to icon and resizing to the
 * name it shows while the names cross-fade, so moving along the dock never makes it blink.
 */
@Composable
private fun DockPopover(engine: DockEngine, apps: List<DockApp>, width: Float) {
    val density = LocalDensity.current
    val d = density.density
    val text = remember(density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = LABEL_TEXT * density.fontScale
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 600, false)
            } else {
                android.graphics.Typeface.DEFAULT_BOLD
            }
        }
    }
    LaunchedEffect(engine, text) {
        engine.nameWidths = FloatArray(engine.count) { i -> apps.getOrNull(i)?.let { text.measureText(it.name) } ?: 60f }
    }
    val pill = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val tipShape = remember { android.graphics.Path() }
    Canvas(Modifier.fillMaxSize()) {
        engine.tick
        val show = engine.labelShow
        if (show <= 0.002f) return@Canvas
        val w = max(LABEL_PAD * 2f, engine.labelW)
        // The pill stays on the screen; its tip keeps pointing at the icon.
        val cx = clamp(engine.labelX, w / 2f + 8f, max(w / 2f + 8f, width - w / 2f - 8f))
        val tip = clamp(engine.labelX - cx, -w / 2f + 10f, w / 2f - 10f)
        val top = engine.labelY + (1f - show) * 4f
        val s = 0.94f + 0.06f * show
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.scale(d, d)
            canvas.saveLayerAlpha(cx - w / 2f - 2f, top - 2f, cx + w / 2f + 2f, top + LABEL_HEIGHT + 8f, (show * 255f).toInt().coerceIn(0, 255))
            canvas.scale(s, s, cx, top + (LABEL_HEIGHT + 6f) / 2f)
            pill.style = android.graphics.Paint.Style.FILL
            pill.color = LabelFill.toArgbInt()
            canvas.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + LABEL_HEIGHT, 8f, 8f, pill)
            pill.style = android.graphics.Paint.Style.STROKE
            pill.strokeWidth = 0.33f
            pill.color = android.graphics.Color.argb(56, 255, 255, 255)
            canvas.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + LABEL_HEIGHT, 8f, 8f, pill)
            pill.style = android.graphics.Paint.Style.FILL
            pill.color = LabelFill.toArgbInt()
            val tx = cx + tip
            tipShape.reset()
            tipShape.moveTo(tx - 6f, top + LABEL_HEIGHT)
            tipShape.lineTo(tx + 6f, top + LABEL_HEIGHT)
            tipShape.lineTo(tx, top + LABEL_HEIGHT + 6f)
            tipShape.close()
            canvas.drawPath(tipShape, pill)
            // The names, clipped to the pill, each at its own opacity.
            canvas.save()
            canvas.clipRect(cx - w / 2f, top, cx + w / 2f, top + LABEL_HEIGHT)
            val baseline = top + LABEL_HEIGHT / 2f - (text.ascent() + text.descent()) / 2f
            for ((i, app) in apps.withIndex()) {
                val a = engine.names.getOrNull(i) ?: 0f
                if (a <= 0.004f) continue
                text.alpha = (a * 255f).toInt().coerceIn(0, 255)
                canvas.drawText(app.name, cx, baseline + (1f - a) * 3f, text)
            }
            canvas.restore()
            canvas.restore()
            canvas.restore()
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255f).toInt(), (red * 255f).toInt(), (green * 255f).toInt(), (blue * 255f).toInt())
