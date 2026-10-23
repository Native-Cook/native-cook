package com.ngocdevv.nativecook.blocks.inputs.inksignaturepad

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.exp

// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`, and the block's own curves.
internal val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
internal val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal val StampEase = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
internal val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** What a finished animation sets off. */
internal enum class PadEvent { Wipe, Thud }

/**
 * A value animated on the pad's clock (ms) like Reanimated's `withTiming`, `withSequence` and
 * `withDelay`: a run of legs, each from where the last left off; during a delay the animation it
 * replaces runs on.
 */
internal class Track(value: Double) {
    class Leg(val start: Double, val duration: Double, val to: Double, val curve: CubicBezierEasing, val event: PadEvent?)
    class Step(val to: Double, val duration: Double, val curve: CubicBezierEasing = InOutQuad, val event: PadEvent? = null)

    private var base = value
    private var legs = listOf<Leg>()
    private var fired = 0
    private var oldBase = 0.0
    private var oldLegs = listOf<Leg>()

    private fun run(base: Double, legs: List<Leg>, t: Double): Double {
        var v = base
        for (leg in legs) {
            if (t < leg.start) return v
            if (leg.duration <= 0 || t >= leg.start + leg.duration) {
                v = leg.to
                continue
            }
            return v + (leg.to - v) * leg.curve.transform(((t - leg.start) / leg.duration).toFloat())
        }
        return v
    }

    fun value(t: Double): Double {
        val first = legs.firstOrNull()
        if (first != null && t < first.start && oldLegs.isNotEmpty()) return run(oldBase, oldLegs, t)
        return run(base, legs, t)
    }

    val target get() = legs.lastOrNull()?.to ?: base

    fun moving(t: Double) = legs.lastOrNull()?.let { t < it.start + it.duration } ?: false

    /** Runs [steps] one after another from `now + delay`. */
    fun animate(now: Double, vararg steps: Step, delay: Double = 0.0) {
        val start = now + delay
        val from = value(start)
        if (delay > 0) {
            val first = legs.firstOrNull()
            // While already waiting, the animation running now is the old one.
            if (!(first != null && now < first.start && oldLegs.isNotEmpty())) {
                oldBase = base
                oldLegs = legs
            }
        } else {
            oldLegs = emptyList()
        }
        base = from
        var at = start
        legs = steps.map { step -> Leg(at, step.duration, step.to, step.curve, step.event).also { at += step.duration } }
        fired = 0
    }

    fun set(value: Double) {
        base = value
        legs = emptyList()
        oldLegs = emptyList()
        fired = 0
    }

    /** What the legs that have ended by [t] set off, once each. */
    fun events(t: Double): List<PadEvent> {
        val out = mutableListOf<PadEvent>()
        while (fired < legs.size && legs[fired].start + legs[fired].duration <= t) {
            legs[fired].event?.let { out.add(it) }
            fired++
        }
        return out
    }
}

// A stroke made after a pause longer than REPLAY_GAP ms is replayed REPLAY_GAP after the one before.
// The replay starts REPLAY_LEAD ms after the press, over the ghost of the ink it redraws, which fades
// in over GHOST_IN and out over GHOST_OUT ms.
private const val REPLAY_GAP = 450.0
private const val REPLAY_LEAD = 260.0
private const val GHOST_IN = 220.0
private const val GHOST_OUT = 380.0
// The pen fades in and out with a time constant of PEN_FADE ms.
private const val PEN_FADE = 70.0
private const val MAX_SAMPLES = 9000
// Clear: the wiper crosses the pad in SWEEP_MS.
private const val SWEEP_MS = 1300.0
// Done: the page shrinks in SEAL_MS, then the wax drops in STAMP_MS, squashes by STAMP_SQUASH and settles.
private const val SEAL_MS = 720.0
private const val STAMP_MS = 230.0
private const val STAMP_SQUASH = 0.06
private const val UNSEAL_MS = 560.0

// autoPlay loop (seconds): sign, replay, seal, open, wipe. A button "presses" PRESS before it acts.
private const val LOOP = 17.4
private const val SIGN_AT = 0.7
private const val REPLAY_AT = 5.9
private const val DONE_AT = 11.1
private const val UNSEAL_AT = 14.3
private const val CLEAR_AT = 15.5
private const val PRESS = 0.14
// After a touch, autoPlay waits this long (s) with no finger down before it picks up again.
private const val USER_HOLD = 4.0

/** Whether [at] was passed going from [before] to [t] on a clock that wraps every LOOP. */
private fun crossed(before: Double, t: Double, at: Double) = if (t >= before) before < at && t >= at else at > before || at <= t

/**
 * Everything on the pad that changes, stepped frame by frame on the pad's own clock as the React
 * Native block does in its frame callback.
 */
internal class PadModel {
    var reduced = false
    var onDone: ((DoubleArray) -> Unit)? = null
    var thud: () -> Unit = {}
    var tap: () -> Unit = {}

    // The script as ink samples, laid out for the pad.
    private var script = Ink()
    private var scriptStrokes = 0
    private var scriptKey = ""

    var ink = Ink()
        private set
    var replay = Ink(16)
        private set
    private var replayCursor = 0
    val ghost = Track(0.0)
    private var strokeCount = 0.0
    var drawing = false
        private set

    /** The pad's clock, ms. */
    var clock = 0.0
        private set
    private var lastNanos = -1L
    val sweep = Track(0.0)
    val inkAlpha = Track(1.0)
    val seal = Track(0.0)
    val stamp = Track(0.0)
    val fade = Track(1.0)
    val hint = Track(1.0)
    var pen = Pen(0.0, 0.0, 0.0)
        private set
    var penAlpha = 0.0
        private set
    private var penOn = false
    // autoPlay: where the script is (sample index, or −1), when it started, and its first stroke.
    private var scriptCursor = -1
    private var scriptFrom = 0.0
    private var scriptStroke = 0.0
    private var looping = false
    private var loopAt = 0.0
    private var hold = 0.0
    val pressClear = Track(0.0)
    val pressReplay = Track(0.0)
    val pressDone = Track(0.0)
    var sealed = false
        private set
    private var hadInk = false

    val hasInk get() = !ink.isEmpty

    fun configure(autoPlay: Boolean, padWidth: Double, lineY: Double, signLeft: Double, signRight: Double, lift: Double) {
        if (looping != autoPlay) {
            looping = autoPlay
            loopAt = 0.0
            hold = 0.0
        }
        val key = "$padWidth $lineY"
        if (key != scriptKey) {
            scriptKey = key
            val (samples, strokes) = signatureScript(signLeft, padWidth - signRight, lineY - lift)
            script = samples
            scriptStrokes = strokes
        }
    }

    /** Whether anything moves, so the pad has to be drawn again next frame. */
    val live: Boolean
        get() {
            val t = clock
            if (looping || drawing || scriptCursor >= 0 || !replay.isEmpty || penOn || penAlpha > 0 || hold > 0) return true
            if (!ink.isEmpty && t - ink[ink.size - 3] < DRY_MS + 60) return true
            return listOf(ghost, sweep, inkAlpha, seal, stamp, fade, hint, pressClear, pressReplay, pressDone).any { it.moving(t) }
        }

    private fun flash(press: Track) {
        press.animate(clock, Track.Step(1.0, 90.0), Track.Step(0.0, 240.0))
    }

    /** Ends a replay now: the redrawn ink becomes the ink. */
    private fun finishReplay() {
        if (replay.isEmpty) return
        ink = replay
        replay = Ink(16)
        ghost.animate(clock, Track.Step(0.0, GHOST_OUT))
        penOn = false
    }

    fun startReplay() {
        if (ink.isEmpty || sweep.value(clock) > 0 || seal.value(clock) > 0) return
        finishReplay()
        replay = retime(ink, clock + REPLAY_LEAD, REPLAY_GAP)
        replayCursor = 0
        ghost.animate(clock, Track.Step(1.0, GHOST_IN))
        penOn = !reduced
    }

    private fun wipe() {
        ink = Ink()
        replay = Ink(16)
        ghost.set(0.0)
        sweep.set(0.0)
        inkAlpha.set(1.0)
        penOn = false
    }

    fun startSweep(user: Boolean) {
        if (ink.isEmpty || sweep.value(clock) > 0 || seal.value(clock) > 0) return
        finishReplay()
        scriptCursor = -1
        penOn = false
        if (user) tap()
        if (reduced) {
            inkAlpha.animate(clock, Track.Step(0.0, 260.0, event = PadEvent.Wipe))
            return
        }
        sweep.animate(clock, Track.Step(1.0, SWEEP_MS, EaseInOut, PadEvent.Wipe))
    }

    fun sealUp(user: Boolean) {
        if (ink.isEmpty || seal.value(clock) > 0 || sweep.value(clock) > 0) return
        finishReplay()
        scriptCursor = -1
        penOn = false
        sealed = true
        if (user) onDone?.invoke(DoubleArray(ink.size) { ink[it] })
        val thud = if (user) PadEvent.Thud else null
        if (reduced) {
            fade.animate(clock, Track.Step(0.0, 160.0), Track.Step(1.0, 260.0))
            seal.animate(clock, Track.Step(1.0, 0.0), delay = 160.0)
            stamp.animate(clock, Track.Step(1.0, 260.0, event = thud), delay = 420.0)
            return
        }
        seal.animate(clock, Track.Step(1.0, SEAL_MS, EaseInOut))
        stamp.animate(
            clock,
            Track.Step(1.0, STAMP_MS, StampEase, thud),
            Track.Step(1 + STAMP_SQUASH, 80.0),
            Track.Step(1.0, 200.0, EaseOut),
            delay = SEAL_MS - 60,
        )
    }

    fun sealDown() {
        if (seal.value(clock) < 1) return
        sealed = false
        stamp.animate(clock, Track.Step(0.0, 180.0))
        if (reduced) {
            fade.animate(clock, Track.Step(0.0, 160.0), Track.Step(1.0, 260.0))
            seal.animate(clock, Track.Step(0.0, 0.0), delay = 160.0)
            return
        }
        seal.animate(clock, Track.Step(0.0, UNSEAL_MS, EaseInOut), delay = 120.0)
    }

    /** A touch: autoPlay stops writing and waits until the pad has been left alone for a while. */
    fun touched() {
        hold = USER_HOLD
        if (scriptCursor >= 0) {
            scriptCursor = -1
            penOn = false
        }
    }

    /** Where the loop picks up after a touch: the page is opened or wiped first if need be. */
    private fun resumePoint() = when {
        seal.value(clock) > 0.5 -> UNSEAL_AT - 0.4
        !ink.isEmpty -> CLEAR_AT - 0.5
        else -> 0.0
    }

    // The finger.

    fun begin(x: Double, y: Double) {
        if (seal.value(clock) > 0 || sweep.value(clock) > 0 || inkAlpha.value(clock) < 1) return
        touched()
        finishReplay()
        strokeCount += 1
        if (ink.size >= MAX_SAMPLES * STRIDE) return
        addSample(ink, x, y, clock, strokeCount, true, -1.0)
        drawing = true
    }

    fun move(x: Double, y: Double, speed: Double) {
        if (!drawing || ink.size >= MAX_SAMPLES * STRIDE) return
        addSample(ink, x, y, clock, strokeCount, false, speed)
    }

    fun end() {
        drawing = false
    }

    // The frame.

    /** Moves the pad's clock on to [nanos] and does what a frame does. */
    fun advance(nanos: Long) {
        val dt = if (lastNanos < 0) 16.0 else ((nanos - lastNanos) / 1e6).coerceIn(0.0, 64.0)
        lastNanos = nanos
        if (dt > 0) step(dt)
    }

    /** The frame clock rested: the next frame counts as a fresh one. */
    fun rest() {
        lastNanos = -1
    }

    private fun step(dt: Double) {
        val now = clock + dt
        clock = now

        for (event in sweep.events(now) + inkAlpha.events(now) + stamp.events(now)) {
            when (event) {
                PadEvent.Wipe -> wipe()
                PadEvent.Thud -> thud()
            }
        }

        if (looping) {
            if (hold > 0) {
                if (!drawing) {
                    hold -= dt / 1000
                    if (hold <= 0) loopAt = resumePoint()
                }
            } else {
                val before = loopAt
                val t = (before + dt / 1000) % LOOP
                loopAt = t
                if (crossed(before, t, SIGN_AT) && seal.value(now) == 0.0 && sweep.value(now) == 0.0) {
                    scriptFrom = now - (t - SIGN_AT) * 1000
                    scriptCursor = 0
                    scriptStroke = strokeCount + 1
                    strokeCount += scriptStrokes
                    penOn = true
                }
                if (crossed(before, t, REPLAY_AT - PRESS) && !ink.isEmpty) flash(pressReplay)
                if (crossed(before, t, REPLAY_AT)) startReplay()
                if (crossed(before, t, DONE_AT - PRESS) && !ink.isEmpty) flash(pressDone)
                if (crossed(before, t, DONE_AT)) sealUp(false)
                if (crossed(before, t, UNSEAL_AT)) sealDown()
                if (crossed(before, t, CLEAR_AT - PRESS) && !ink.isEmpty) flash(pressClear)
                if (crossed(before, t, CLEAR_AT)) startSweep(false)
            }
        }

        // autoPlay's hand: every script sample whose time has come goes down as ink.
        if (scriptCursor >= 0) {
            val count = script.size / STRIDE
            val rel = now - scriptFrom
            var k = scriptCursor
            if (k < count && script[k * STRIDE + 2] <= rel && ink.size < MAX_SAMPLES * STRIDE) {
                while (k < count && script[k * STRIDE + 2] <= rel) {
                    val at = k * STRIDE
                    val starts = k == 0 || script[at + 4] != script[at - 1]
                    addSample(ink, script[at], script[at + 1], scriptFrom + script[at + 2], scriptStroke + script[at + 4], starts, -1.0)
                    k++
                }
            }
            val p = penAt(script, k, rel)
            pen = Pen(p.x, p.y, if (k == 0) 1.0 else p.lift)
            if (k >= count) {
                scriptCursor = -1
                penOn = false
            } else {
                scriptCursor = k
            }
        }

        // The replay: the pen follows the redrawn ink, and when it has caught up the replay ends.
        if (!replay.isEmpty) {
            var k = replayCursor
            val count = replay.size / STRIDE
            while (k < count && replay[k * STRIDE + 2] <= now) k++
            replayCursor = k
            val p = penAt(replay, k, now)
            pen = Pen(p.x, p.y, if (k == 0) 1.0 else p.lift)
            if (k >= count && now > replay[replay.size - 3] + 140) finishReplay()
        }

        val target = if (penOn && !reduced) 1.0 else 0.0
        if (penAlpha != target) {
            val next = penAlpha + (target - penAlpha) * (1 - exp(-dt / PEN_FADE))
            penAlpha = if (abs(next - target) < 0.01) target else next
        }

        // "Sign above the line" fades as the first ink lands and back when it is gone.
        if (hasInk != hadInk) {
            hadInk = hasInk
            hint.animate(now, Track.Step(if (hasInk) 0.0 else 1.0, 220.0, CssEase))
        }
    }

    /** What the ink layer draws this frame. */
    fun frame(width: Float, height: Float) = InkFrame(
        ink = ink,
        replay = replay,
        now = clock,
        ghost = ghost.value(clock).toFloat(),
        sweep = sweep.value(clock).toFloat(),
        alpha = inkAlpha.value(clock).toFloat(),
        width = width,
        height = height,
        penX = pen.x.toFloat(),
        penY = pen.y.toFloat(),
        penLift = pen.lift.toFloat(),
        penAlpha = penAlpha.toFloat(),
    )
}
