package com.ngocdevv.nativecook.blocks.progress.sandhourglass

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

/*
 * The focus timer around the sand, ported step for step from the React Native
 * `sandHourglassEngine.ts`: the glass's turn, the minute ruler, the session (set, running, done) and
 * the autoPlay script. The composable runs `tick` at a fixed 1/60 s and draws what it finds.
 *
 * The sand is the clock. A session starts when the glass is turned over; the neck's gate then lets
 * the grains through at the rate that empties the upper bulb in the chosen number of minutes, and the
 * time left is read straight from the grains still above the neck. Turning the glass mid-session
 * does what it does to a real one: the sand that already ran out is now on top and runs back.
 */

internal const val TICK = 1.0 / 60
// Passes of the grain grid per tick: grains fall 4 cells per tick.
private const val PASSES = 4

// The turn: a spring on the glass's angle (rad), a little under critical so it lands with a sway.
private const val TURN_STIFFNESS = 38.0
private const val TURN_DAMPING = 7.4
// Seconds of spin a release throws ahead when it picks which way up the glass lands.
private const val THROW = 0.22

// The ruler: minutes it offers, finger travel per minute (dp), the coast after a flick (speed decays
// at this rate, 1/s), its fastest coast (min/s), the speed under which it snaps to a whole minute,
// that snap, and how much of a pull past either end shows.
internal const val MIN_MINUTES = 1.0
internal const val MAX_MINUTES = 60.0
internal const val RULER_PX = 11.0
private const val COAST_DECAY = 4.5
private const val COAST_MAX = 60.0
private const val SNAP_SPEED = 2.0
private const val SNAP_STIFFNESS = 220.0
private const val SNAP_DAMPING = 28.0
private const val RUBBER = 0.3
// Selection ticks are at most this often (s).
private const val TICK_GAP = 0.04
private const val DEFAULT_MINUTES = 25.0

// Phases of a session.
internal const val IDLE = 0
internal const val RUNNING = 1
internal const val DONE = 2
// The last grain leaves the neck this long before it lands (s); a glass turned back within
// CANCEL_WITHIN s of a session simply goes back to being set; the "done" moment holds DONE_HOLD s.
private const val LAST_GRAIN = 0.35
private const val CANCEL_WITHIN = 2.0
private const val DONE_HOLD = 2.6
// The labels and the ruler cross-fade at this rate (1/s).
private const val FADE_RATE = 6.0

// autoPlay (s): the ruler is pulled up to 40 min and back to 25, the glass is turned over at TURN_AT,
// the sand runs out in about 10.5 s with the neck open, the session ends, and the glass rests.
internal const val LOOP = 18.0
private val ScriptRuler = arrayOf(
    doubleArrayOf(0.6, 1.5, 25.0, 40.0),
    doubleArrayOf(1.9, 2.7, 40.0, 25.0),
)
private const val TURN_AT = 3.1
// The time of day the script pretends it is, for "Ends at" (minutes).
private const val SCRIPT_TIME = 9 * 60 + 41
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 4.0

/** JavaScript's Math.round: halves go up. */
internal fun jsRound(x: Double) = floor(x + 0.5)

/** "25:00", "04:07". */
internal fun formatClock(seconds: Double): String {
    val whole = max(0, ceil(if (seconds.isFinite()) seconds else 0.0).toInt())
    val m = whole / 60
    val s = whole % 60
    return "${if (m < 10) "0" else ""}$m:${if (s < 10) "0" else ""}$s"
}

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

internal class HourglassEngine(seed: Int) {
    val sand = SandHourglassSand(seed)
    var acc = 0.0
    var looping = false
    var still = false
    var clock = 0.0
    var hold = 0.0
    private var time = 0.0
    var wallMinutes = 0
    // The glass's angle (rad, clockwise), its spin and where its spring is taking it.
    var angle = 0.0
        private set
    private var spin = 0.0
    private var target = 0.0
    var turning = false
        private set
    private var grabAngle = 0.0
    var turned = 0.0
        private set
    /** A turn made by hand: it lands with a haptic. */
    private var landing = false
    // Ruler: minutes under the needle (fractional while it moves) and what moves it.
    var minutes = DEFAULT_MINUTES
        private set
    private var rulerV = 0.0
    private var rulerDragging = false
    private var rulerAtGrab = DEFAULT_MINUTES
    private var rulerCoasting = false
    private var rulerSnapping = false
    private var lastWhole = DEFAULT_MINUTES.toInt()
    private var lastTick = -1.0
    // Session.
    var phase = IDLE
        private set
    var duration = DEFAULT_MINUTES.toInt()
        private set
    var endsAt = 0
        private set
    private var doneTime = 0.0
    private var lastGrain = 0.0
    private var startTime = 0.0
    private var userSession = false
    var runBlend = 0.0
        private set
    var doneBlend = 0.0
        private set
    var secondsLeft = DEFAULT_MINUTES * 60
        private set
    // Answered with haptics by the composable.
    var userTicks = 0
        private set
    var userLandings = 0
        private set
    var userCompletions = 0
        private set

    /** Minutes the ruler shows, as a whole number in range. */
    val chosenMinutes: Int
        get() = max(MIN_MINUTES, min(MAX_MINUTES, jsRound(minutes))).toInt()

    /** Seconds into the "done" moment, or below 0. */
    val doneAge: Double
        get() = if (phase == DONE) doneTime else -1.0

    /** The ruler only listens while a session is being set. */
    val rulerLive: Boolean
        get() = phase == IDLE && runBlend < 0.5

    private fun touched(user: Boolean) {
        if (user) hold = USER_HOLD
    }

    /** A session starts (or a finished one starts again) as the glass goes over. */
    private fun startSession(user: Boolean) {
        if (phase == RUNNING) return
        phase = RUNNING
        startTime = time
        userSession = user
        duration = chosenMinutes
        endsAt = (if (looping) SCRIPT_TIME else wallMinutes) + duration
        lastGrain = 0.0
    }

    /** Sends the glass over to the next way up. */
    fun flip(user: Boolean) {
        touched(user)
        target = jsRound(target / PI) * PI + PI
        landing = user
        startSession(user)
    }

    /** A finger takes hold of the glass (a drag around it or a two-finger twist). */
    fun grabGlass() {
        touched(true)
        turning = true
        grabAngle = angle
        spin = 0.0
        turned = 0.0
    }

    /** The glass follows the finger: `turn` rad from where it was grabbed. */
    fun turnGlass(turn: Double) {
        if (!turning || !turn.isFinite()) return
        turned = turn
        angle = grabAngle + turn
    }

    /** Lets go at `velocity` rad/s: the spring takes the glass to the nearest way up, thrown ahead. */
    fun releaseGlass(velocity: Double) {
        if (!turning) return
        turning = false
        touched(true)
        val before = jsRound(grabAngle / PI) * PI
        val v = if (still || !velocity.isFinite()) 0.0 else max(-30.0, min(30.0, velocity))
        val goal = jsRound((angle + v * THROW) / PI) * PI
        spin = v
        target = goal
        landing = goal != before
        if (goal != before) startSession(true)
    }

    fun grabRuler() {
        touched(true)
        rulerDragging = true
        rulerCoasting = false
        rulerSnapping = false
        rulerV = 0.0
        rulerAtGrab = minutes
    }

    /** Past either end the ruler gives way less and less. */
    private fun rubber(raw: Double): Double {
        if (raw < MIN_MINUTES) {
            val d = MIN_MINUTES - raw
            return MIN_MINUTES - d * RUBBER / (1 + d * 0.08)
        }
        if (raw > MAX_MINUTES) {
            val d = raw - MAX_MINUTES
            return MAX_MINUTES + d * RUBBER / (1 + d * 0.08)
        }
        return raw
    }

    /** A drag to the left brings longer times under the needle. */
    fun dragRuler(translationX: Double) {
        if (!rulerDragging || !translationX.isFinite()) return
        touched(true)
        minutes = rubber(rulerAtGrab - translationX / RULER_PX)
        answerRuler(true)
    }

    fun releaseRuler(velocityX: Double) {
        if (!rulerDragging) return
        rulerDragging = false
        touched(true)
        val v = if (still || !velocityX.isFinite()) 0.0 else -velocityX / RULER_PX
        rulerV = max(-COAST_MAX, min(COAST_MAX, v))
        val outside = minutes < MIN_MINUTES || minutes > MAX_MINUTES
        rulerCoasting = !outside
        rulerSnapping = outside
        if (outside) rulerV = 0.0
    }

    /** A selection tick each time a new whole minute comes under the needle. */
    private fun answerRuler(user: Boolean) {
        val whole = chosenMinutes
        if (whole == lastWhole) return
        lastWhole = whole
        if (user && time - lastTick >= TICK_GAP) {
            lastTick = time
            userTicks += 1
        }
    }

    private fun stepRuler() {
        if (rulerCoasting) {
            minutes += rulerV * TICK
            rulerV *= exp(-COAST_DECAY * TICK)
            if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
                minutes = max(MIN_MINUTES, min(MAX_MINUTES, minutes))
                rulerV = 0.0
                rulerCoasting = false
                rulerSnapping = true
            } else if (abs(rulerV) < SNAP_SPEED) {
                rulerCoasting = false
                rulerSnapping = true
            }
            answerRuler(true)
        } else if (rulerSnapping) {
            val goal = chosenMinutes.toDouble()
            rulerV += (SNAP_STIFFNESS * (goal - minutes) - SNAP_DAMPING * rulerV) * TICK
            minutes += rulerV * TICK
            if (abs(minutes - goal) < 0.002 && abs(rulerV) < 0.02) {
                minutes = goal
                rulerV = 0.0
                rulerSnapping = false
            }
            answerRuler(true)
        }
    }

    /** Puts the loop back at its resting state. */
    private fun restartScript() {
        clock = 0.0
        minutes = DEFAULT_MINUTES
        lastWhole = DEFAULT_MINUTES.toInt()
        rulerV = 0.0
        rulerDragging = false
        rulerCoasting = false
        rulerSnapping = false
    }

    private fun runScript(before: Double, after: Double) {
        for (step in ScriptRuler) {
            val t0 = step[0]
            val t1 = step[1]
            if (after > t0 && before < t1) {
                val u = min(1.0, (after - t0) / (t1 - t0))
                minutes = step[2] + (step[3] - step[2]) * easeInOut(u)
                answerRuler(false)
            }
        }
        if (before < TURN_AT && after >= TURN_AT) flip(false)
    }

    /** One fixed tick: the script, the turn's spring, the ruler, the gate, the sand, then the session. */
    fun tick() {
        time += TICK
        if (!turning) {
            if (hold > 0) {
                hold -= TICK
                if (hold <= 0 && looping) restartScript()
            } else if (looping) {
                val before = clock
                val after = before + TICK
                runScript(before, after)
                clock = after
                if (after >= LOOP) restartScript()
            }
            val a = angle
            val goal = target
            if (still) {
                angle = goal
                spin = 0.0
                if (landing) {
                    landing = false
                    userLandings += 1
                }
            } else {
                val w = spin + (TURN_STIFFNESS * (goal - a) - TURN_DAMPING * spin) * TICK
                val next = a + w * TICK
                if (landing && (a - goal) * (next - goal) <= 0) {
                    landing = false
                    userLandings += 1
                }
                spin = w
                angle = next
            }
            // A whole turn changes nothing on screen, so the numbers are kept small.
            if (abs(target) > 2 * PI + 0.1) {
                val turn = (if (target > 0) 1.0 else -1.0) * 2 * PI
                target -= turn
                angle -= turn
            }
        }
        if (!rulerDragging) stepRuler()

        // The neck lets the grains through at the session's rate; the self-playing loop runs it open.
        val sessionMinutes = if (phase == RUNNING) duration else chosenMinutes
        sand.gate = if (looping) -1.0 else SAND_GRAINS / (sessionMinutes * 60.0)
        sand.feedGate(TICK)
        val a = angle
        repeat(PASSES) { sand.step(sin(a), cos(a)) }

        // The time left is the sand above the neck (counted by the last paint).
        val up = if (cos(angle) >= 0) sand.upper else sand.lower
        secondsLeft = if (phase == IDLE) chosenMinutes * 60.0 else up.toDouble() / SAND_GRAINS * sessionMinutes * 60

        when (phase) {
            RUNNING -> {
                // Only once the glass has landed: before a turn the empty bulb is still on top.
                val upright = !turning && abs(angle - target) < 0.15 && abs(spin) < 1
                if (upright && up == 0 && sand.neck == 0) {
                    lastGrain += TICK
                    if (time - startTime < CANCEL_WITHIN) {
                        // Turned straight back: nothing ran, so there is nothing to celebrate.
                        phase = IDLE
                    } else if (lastGrain >= LAST_GRAIN) {
                        phase = DONE
                        doneTime = 0.0
                        if (userSession) userCompletions += 1
                    }
                } else {
                    lastGrain = 0.0
                }
            }
            DONE -> {
                doneTime += TICK
                if (doneTime >= DONE_HOLD) phase = IDLE
            }
        }
        val fade = 1 - exp(-FADE_RATE * TICK)
        runBlend += ((if (phase == IDLE) 0.0 else 1.0) - runBlend) * fade
        doneBlend += ((if (phase == DONE) 1.0 else 0.0) - doneBlend) * fade
    }

    /** "Ends at 10:06". */
    fun endsAtText(): String {
        val t = ((endsAt % 1440) + 1440) % 1440
        val h = t / 60
        val m = t % 60
        return "Ends at ${if (h < 10) "0" else ""}$h:${if (m < 10) "0" else ""}$m"
    }
}
