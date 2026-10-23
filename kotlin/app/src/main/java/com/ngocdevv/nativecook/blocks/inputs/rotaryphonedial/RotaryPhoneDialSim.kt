package com.ngocdevv.nativecook.blocks.inputs.rotaryphonedial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The dial's mechanism: the finger wheel's angle, the drag that winds it toward the finger stop,
// and the governed return that counts one pulse for each number that passes the stop. Stepped in
// fixed substeps, a line-for-line port of the React Native simulation.
//
// Angles are in degrees on the screen: 0 points right and positive turns clockwise (screen y grows
// downward). `phi` is how far the wheel is wound clockwise from rest.

/** The finger stop's angle. */
internal const val STOP_ANGLE = 52.0
/** Angle between neighbouring holes. */
private const val HOLE_STEP = 30.0
/** Holes, in dial order: digits 1…9 then 0. */
internal const val HOLES = 10
/** The digit each hole dials. */
internal val HoleDigits = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
// A finger in a hole meets the stop STOP_GAP degrees before the hole's centre would reach it.
private const val STOP_GAP = 16.0
// Past the stop the wheel gives at most OVER_MAX degrees (rubber band, OVER_SOFT degrees soft);
// below rest it gives UNDER_MAX.
private const val OVER_MAX = 11.0
private const val OVER_SOFT = 20.0
private const val UNDER_MAX = 3.0
private const val UNDER_SOFT = 8.0
// A release within REACH_SLACK degrees of the stop still counts as reaching it.
private const val REACH_SLACK = 2.0
// The governor: the wheel returns at RETURN_SPEED degrees per second (ten pulses a second),
// reaching that speed with a time constant of GOVERNOR_LAG s.
private const val RETURN_SPEED = 300.0
private const val GOVERNOR_LAG = 0.035
// Landing on the rest stop it bounces back BOUNCE of its speed, then settles on a spring.
private const val BOUNCE = 0.13
private const val SETTLE_K = 900.0
private const val SETTLE_DAMP = 30.0
// Each pulse twitches the finger stop (degrees per second of kick, spring back).
private const val KICK = 34.0
private const val KICK_K = 1400.0
private const val KICK_DAMP = 26.0

private const val SUBSTEP = 1.0 / 240
internal const val MAX_FRAME = 1.0 / 30

internal const val EVENT_TICK = 1
internal const val EVENT_STOP = 2
internal const val EVENT_DIGIT = 4
internal const val EVENT_CLEAR = 8

private const val MODE_REST = 0
internal const val MODE_DRAG = 1
private const val MODE_RETURN = 2
private const val MODE_SETTLE = 3

internal const val SCRIPT_ID = 1000L

/** Where hole i sits at rest. */
internal fun holeRest(i: Int) = STOP_ANGLE - (i + 2) * HOLE_STEP

/** How far hole i winds before the finger meets the stop. */
internal fun holeTravel(i: Int) = (i + 2) * HOLE_STEP - STOP_GAP

/** `a` moved by whole turns to lie within half a turn of `near` (JavaScript's Math.round). */
private fun nearest(a: Double, near: Double) = a - 360 * floor((a - near) / 360 + 0.5)

// autoPlay: dials four digits, then presses the card to clear the strip, and rests. Each digit: a
// finger settles into its hole, winds it to the stop on an ease-in-out curve, rests against the
// stop, lets go, and waits for the governed return.

/** The number the script dials. */
private val ScriptDigits = intArrayOf(4, 1, 0, 7)
private const val START = 0.7
private const val FINGER_IN = 0.28
private const val DRAG_SPEED = 250.0
private const val DRAG_MIN = 0.32
private const val HOLD = 0.2
private const val AFTER = 0.42
private const val CLEAR_HOLD = 0.75
private const val CLEAR_REST = 1.3

private class Stroke(val at: Double, val hole: Int, val drag: Double, val release: Double)

private class Plan(val strokes: List<Stroke>, val clearAt: Double, val end: Double)

private val ScriptPlan: Plan = run {
    val strokes = ArrayList<Stroke>()
    var t = START
    for (digit in ScriptDigits) {
        val hole = if (digit == 0) 9 else digit - 1
        val travel = holeTravel(hole)
        val drag = max(DRAG_MIN, travel / DRAG_SPEED)
        val release = t + FINGER_IN + drag + HOLD
        strokes.add(Stroke(t, hole, drag, release))
        t = release + travel / RETURN_SPEED + AFTER
    }
    Plan(strokes, t, t + CLEAR_HOLD + CLEAR_REST)
}

/** Seconds the script takes before it starts over. */
internal val LOOP = ceil(ScriptPlan.end * 2) / 2

private fun easeInOut(p: Double): Double {
    val t = min(1.0, max(0.0, p))
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
private fun passed(before: Double, now: Double, at: Double) =
    if (now >= before) before < at && now >= at else at > before || at <= now

/** Where the scripted finger is drawn: its hole (-1 when it is away), its angle, how visible it is. */
internal class DialFinger {
    var hole = -1
    var angle = 0.0
    var shown = 0.0
}

internal class DialSim(val reduced: Boolean) {
    var phi = 0.0
    var omega = 0.0
    var mode = MODE_REST
    /** The hole the finger is in (0…9), or -1. */
    var hole = -1
    var touch = -1L
    var scripted = false
    var grabFinger = 0.0
    var grabPhi = 0.0
    /** Raw wind the finger asks for (before the stops). */
    var wish = 0.0
    var reached = false
    var dialling = false
    /** Pulses counted on this return. */
    var pulses = 0
    /** The digit the last finished return dialled. */
    var digit = -1
    var kick = 0.0
    var kickV = 0.0
    /** 0…1, how far the number card is pressed in. */
    var card = 0.0
    var cardTarget = 0.0
    var events = 0
    var eventsByUser = false

    /** The hole whose disc contains the point at (angle, radius) from the centre, or -1. */
    fun holeAt(angle: Double, radius: Double, holeR: Double, ringR: Double): Int {
        for (i in 0 until HOLES) {
            // A hole already wound past the stop cannot be taken (the wheel would jump back to it).
            if (holeTravel(i) < phi) continue
            val a = (holeRest(i) + phi) * PI / 180
            val t = angle * PI / 180
            val dx = radius * cos(t) - ringR * cos(a)
            val dy = radius * sin(t) - ringR * sin(a)
            if (dx * dx + dy * dy < holeR * holeR * 1.25) return i
        }
        return -1
    }

    /** A finger lands in hole `hole` at `angle`: it takes the wheel, wherever it was. */
    fun grab(id: Long, hole: Int, angle: Double, scripted: Boolean) {
        touch = id
        this.scripted = scripted
        this.hole = hole
        mode = MODE_DRAG
        grabFinger = angle
        grabPhi = phi
        wish = phi
        omega = 0.0
        reached = false
        // Catching the wheel mid-return loses that number.
        dialling = false
        pulses = 0
    }

    /** The finger moved to `angle`; it is unwrapped against the last angle so it can go round. */
    fun drag(id: Long, angle: Double) {
        if (touch != id || mode != MODE_DRAG) return
        val a = nearest(angle, grabFinger + (wish - grabPhi))
        wish = grabPhi + (a - grabFinger)
        val limit = holeTravel(hole)
        var p = wish
        if (p > limit) {
            p = limit + OVER_MAX * (1 - exp(-(p - limit) / OVER_SOFT))
        } else if (p < 0) {
            p = -UNDER_MAX * (1 - exp(p / UNDER_SOFT))
        }
        phi = p
        if (!reached && wish >= limit - REACH_SLACK) {
            reached = true
            events = events or EVENT_STOP
            if (!scripted) eventsByUser = true
        }
    }

    /** The finger lets go: the governor takes the wheel home, dialling if it reached the stop. */
    fun release(id: Long) {
        if (touch != id) return
        touch = -1L
        if (mode != MODE_DRAG) return
        dialling = reached
        pulses = 0
        mode = if (phi > 0) MODE_RETURN else MODE_SETTLE
        omega = 0.0
    }

    /** Long press on the number card: pressed while held, clears the strip when it fires. */
    fun cardPress(down: Boolean) {
        cardTarget = if (down) 1.0 else 0.0
    }

    /** A real finger takes over: the script's finger lets go without dialling. */
    fun cancelScripted() {
        if (scripted && touch != -1L) {
            touch = -1L
            reached = false
            dialling = false
            if (mode == MODE_DRAG) mode = if (phi > 0) MODE_RETURN else MODE_SETTLE
        }
        if (scripted) dialling = false
        cardTarget = 0.0
    }

    fun clear(byUser: Boolean) {
        events = events or EVENT_CLEAR
        if (byUser) eventsByUser = true
    }

    fun step(frame: Double) {
        if (!(frame > 0) || frame.isInfinite()) return
        val dt = min(frame, MAX_FRAME)
        val n = max(1, ceil(dt / SUBSTEP - 1e-6).toInt())
        val h = dt / n
        repeat(n) {
            if (mode == MODE_RETURN) {
                val before = phi
                omega += (-RETURN_SPEED - omega) * (1 - exp(-h / GOVERNOR_LAG))
                phi += omega * h
                // A pulse for every multiple of HOLE_STEP the wheel passes on its way home.
                if (dialling && floor(before / HOLE_STEP) > floor(max(phi, 0.0) / HOLE_STEP) && before > 0) {
                    pulses += 1
                    events = events or EVENT_TICK
                    if (!scripted) eventsByUser = true
                    kickV += KICK
                }
                if (phi <= 0) {
                    phi = 0.0
                    omega = if (reduced) 0.0 else -omega * BOUNCE
                    mode = MODE_SETTLE
                    if (dialling) {
                        digit = pulses % 10
                        events = events or EVENT_DIGIT
                        if (!scripted) eventsByUser = true
                    }
                    dialling = false
                }
            } else if (mode == MODE_SETTLE) {
                omega += (-SETTLE_K * phi - SETTLE_DAMP * omega) * h
                phi += omega * h
                if (phi < 0) {
                    phi = 0.0
                    omega = -omega * 0.3
                }
                if (abs(phi) < 0.02 && abs(omega) < 0.5) {
                    phi = 0.0
                    omega = 0.0
                    mode = MODE_REST
                }
            }
            // The finger stop's twitch.
            kickV += (-KICK_K * kick - KICK_DAMP * kickV) * h
            kick += kickV * h
            // The card follows the press quickly.
            card += (cardTarget - card) * (1 - exp(-h * 18))
        }
    }

    /**
     * Drives the scripted finger from the loop's clock, which moved from `before` to `now`, and
     * writes where the finger is drawn into `finger`.
     */
    fun script(before: Double, now: Double, finger: DialFinger) {
        finger.hole = -1
        finger.angle = 0.0
        finger.shown = 0.0
        for (st in ScriptPlan.strokes) {
            val rest = holeRest(st.hole)
            val travel = holeTravel(st.hole)
            if (passed(before, now, st.at + FINGER_IN)) grab(SCRIPT_ID, st.hole, rest, true)
            if (now >= st.at && now < st.release + 0.3) {
                finger.hole = st.hole
                val u = (now - st.at - FINGER_IN) / st.drag
                // A little past the stop, so the wheel visibly presses into it.
                val wind = (travel + 5) * easeInOut(u)
                finger.angle = rest + (if (now >= st.release) travel else max(0.0, wind))
                finger.shown = minOf(1.0, (now - st.at) / FINGER_IN, max(0.0, (st.release + 0.3 - now) / 0.3))
                if (touch == SCRIPT_ID && now < st.release) drag(SCRIPT_ID, finger.angle)
            }
            if (passed(before, now, st.release)) release(SCRIPT_ID)
        }
        if (passed(before, now, ScriptPlan.clearAt)) cardPress(true)
        if (passed(before, now, ScriptPlan.clearAt + CLEAR_HOLD)) {
            cardPress(false)
            clear(false)
        }
    }
}
