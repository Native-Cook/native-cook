// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.ReduceMotion
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SharedValue
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SpringConfig
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.cancelAnimation
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.invoke
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withDecay
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withDelay
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withSpring
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withTiming
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.PanGesture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.TapGesture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Haptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// A full level range turns the knob 1.5π: at the specimen's scale that arc is
// close to the 160pt drag, so the grooves travel with the finger.
private const val DIAL_TURN = PI * 1.5
private const val DIAL_DRAG = 160.0
// Visual give past 0/100 on the knob, in level units (≈21° at most).
private const val DIAL_GIVE = 0.08
// Interrupting a replay or handoff morphs the last visible frame into the
// new source rather than cutting to it. Both ends are on screen, so this is
// ease-in-out: a strong ease-out spent most of the change in the first frame.
private const val HANDOFF = 240.0
private val easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
// Mode switch: the light contracts into the end cap (ease-in-out, so it does
// not cut), the color transfers at the trough, then it blooms out to the new
// level with a soft start and a long tail. 380ms: long enough to read as one
// gesture, still under the platform push.
private const val SWITCH = 380.0
private const val TROUGH = 150.0 / SWITCH
private val contract = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val bloom = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
// Fill fraction at which the liquid fill is exactly the round end cap.
private const val CAP = G.Track.height / G.Track.width
// Power-on: the light rises like liquid from 0 with a soft settle.
private val rise = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val BOOT = 900.0
// Knob detents: 28 grooves per revolution.
private const val GROOVE = (PI * 2) / 28
// Fine scrub: finger this far below the track switches to ¼ speed.
private const val FINE_DROP = 32.0

data class Frame(
    val fill: Double,
    val light: Double,
    val blur: Double,
    val number: Double,
    val label: Double,
    val readout: Double,
)

// Skips the lerp when no handoff is running, so the live value is exact.
private fun fade(live: Double, last: Double, amount: Double) =
    if (amount > 0) live + (last - live) * amount else live

private fun clamp01(level: Double) = max(0.0, min(1.0, level))

/**
 * Port of `use-tactile-control.ts`: levels, channel switch choreography,
 * gestures, haptics, power-on. Shared values and derived values keep the
 * RN names; derived values are recomputed on read (they are read once per
 * frame by the draw passes and reactions, as Reanimated mappers run once).
 */
class TactileControl(
    private val haptics: Haptics,
    val fx: Effects,
    reducedFromPreferences: Boolean,
    gentle: Boolean = false,
) {
    val reduced = reducedFromPreferences || gentle
    private val disabled = false

    // React state.
    var mode by mutableIntStateOf(0)
        private set
    var snapshot by mutableStateOf(listOf(52, 53))
        private set
    var playing by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(true)
        private set
    private var mounted = true
    private var runId = 0

    val brightness = SharedValue(0.52)
    val volume = SharedValue(0.53)
    // Committed levels. The visible levels may still be gliding toward them.
    private val goal = Shared(doubleArrayOf(0.52, 0.53))
    private val channel = SharedValue(0.0)
    private val mix = SharedValue(0.0)
    private val reveal = SharedValue(1.0)
    private val selector = SharedValue(0.0)
    private val outgoingFill = SharedValue(fillForLevel(0.52))
    private val outgoingNumber = SharedValue(52.0)
    val trackPress = SharedValue(0.0)
    val dialPress = SharedValue(0.0)
    val brightPress = SharedValue(0.0)
    val volumePress = SharedValue(0.0)
    val width = SharedValue(1.0)
    private val dragStart = SharedValue(0.0)
    private val dragOwner = SharedValue(0.0)
    private val dialAngle = SharedValue(0.0)
    private val dialGive = SharedValue(0.0)
    private val available = Shared(true)
    val active = SharedValue(if (disabled) 0.0 else 1.0)
    private val replayTime = SharedValue(-1.0)
    private val replayRunning = Shared(false)
    private val replayId = Shared(0)
    private val blend = SharedValue(0.0)
    private val blendFill = Shared(false)
    private val from = Shared(Frame(fillForLevel(0.52), 1.0, 0.0, 1.0, 1.0, 52.0))
    private val boot = SharedValue(if (fx.powerOn && !reduced) 0.0 else 1.0)
    private val spinning = Shared(false)
    private var lastTick = 0L
    private val fine = Shared(false)
    private val fineAnchor = Shared(0.0 to 0.0)
    val fineShown = SharedValue(0.0)
    private val magnetHeld = Shared(false)

    // Derived values (useDerivedValue).
    private fun pose() = referencePose(replayTime.get(), reduced)
    private fun value() = if (channel.get() == 0.0) brightness.get() else volume.get()

    private fun sourceFill(): Double {
        if (replayTime.get() >= 0) return pose().fill
        val p = reveal.get()
        val target = fillForLevel(value())
        if (p >= 1) return target
        // Trough is the round cap, never the thin liquid slice below it.
        val trough = min(outgoingFill.get(), CAP)
        if (p < TROUGH) {
            val k = contract(p / TROUGH)
            return outgoingFill.get() + (trough - outgoingFill.get()) * k
        }
        return trough + (target - trough) * bloom((p - TROUGH) / (1 - TROUGH))
    }

    // A finger on the light always wins over the fade: the endpoint stays under it.
    fun fill(): Double {
        val b = boot.get()
        val k = if (b >= 1) 1.0 else rise(b)
        val live = sourceFill()
        if (!blendFill.get() || dragOwner.get() != 0.0) return live * k
        return fade(live, from.get().fill, blend.get()) * k
    }

    val meniscus = Meniscus(::fill, fx.meniscus && !reduced)
    fun slosh() = meniscus.slosh.get()

    fun lightMix() = if (replayTime.get() >= 0) pose().mix else mix.get()

    fun lightAlpha(): Double {
        val live = if (replayTime.get() >= 0) pose().light else 1.0
        return fade(live, from.get().light, blend.get()) * min(1.0, boot.get() * 5)
    }

    fun lightBlur(): Double {
        if (reduced) return 0.0
        val live = if (replayTime.get() >= 0) pose().blur else {
            // A light softening that peaks at the trough; kept low so the color
            // stays saturated while it blooms.
            val p = reveal.get()
            if (p < TROUGH) (p / TROUGH) * 7 else max(0.0, 1 - (p - TROUGH) / 0.4) * 7
        }
        return fade(live, from.get().blur, blend.get())
    }

    fun numberAlpha(): Double {
        var live = 1.0
        if (replayTime.get() >= 0) live = pose().number
        else if (!reduced) {
            // Out while contracting, swapped unseen at the trough, back in while
            // the new light blooms — the old channel's number never rides along.
            val p = reveal.get()
            live = if (p < TROUGH) max(0.0, 1 - p / (TROUGH * 0.8))
            else max(0.0, min(1.0, (p - TROUGH - 0.12) / 0.4))
        }
        return fade(live, from.get().number, blend.get()) * min(1.0, boot.get() * 4)
    }

    fun readout(): Double {
        // Power-on: show 0, then the number rolls up to the level in one move.
        if (boot.get() < 0.12) return 0.0
        if (blend.get() > 0.5) return from.get().readout
        if (replayTime.get() >= 0) return pose().display.toDouble()
        return if (reveal.get() < TROUGH) outgoingNumber.get() else jsRound(clamp01(value()) * 100)
    }

    // The mode title is one NumericText that swaps its own glyphs on a switch,
    // so live it stays fully visible. The replay keeps its sampled visibility
    // (the reference fades the title out and back in around the transfer).
    fun labelAlpha(): Double {
        val live = if (replayTime.get() >= 0) max(pose().brightnessLabel, pose().volumeLabel) else 1.0
        return fade(live, from.get().label, blend.get())
    }

    // Selector glyphs/wells answer the press immediately; the light's color
    // transfer is choreographed separately.
    fun selectorMix() = if (replayTime.get() >= 0) pose().mix else selector.get()

    // The number rolls for every in-place change, including under a dragging
    // finger, but not for a channel swap (that one is hidden under the fade).
    fun numberRolls() = reveal.get() >= 1

    // A finger is moving the level continuously: rolls must be short enough to
    // settle between steps or the digits stay blurred for the whole drag.
    fun numberQuick() = dragOwner.get() == 1.0 || dragOwner.get() == 2.0

    val legibility = ReadoutLegibility(::readout, ::fill, reduced)
    fun numberInk() = legibility.numberInk.get()

    fun dialRotation() = (dialAngle.get() + dialGive.get()) * DIAL_TURN
    fun glowPress() = max(trackPress.get(), dialPress.get())

    private fun publish(b: Double, v: Double) {
        if (mounted) snapshot = listOf(jsRound(b * 100).toInt(), jsRound(v * 100).toInt())
    }

    private fun publishReplay(id: Int, next: Int, b: Double, v: Double) {
        if (!mounted || id != runId) return
        mode = next
        publish(b, v)
    }

    private fun finishReplay(id: Int) {
        if (mounted && id == runId) playing = false
    }

    private fun feedback() = haptics.selection()
    private fun edgeFeedback() = haptics.impact(Haptics.Impact.Light)
    // Detent click (fx.sound) is off; the haptic is the detent.
    private fun tickFeedback() = haptics.selection()
    private fun softFeedback() = haptics.impact(Haptics.Impact.Soft)

    private fun stopPlayback() {
        runId += 1
        if (mounted) playing = false
    }

    private fun setLevel(ch: Int, target: Double, duration: Double, easing: Easing) {
        val level = clamp01(target)
        val g = goal.get()
        goal.set(if (ch == 0) doubleArrayOf(level, g[1]) else doubleArrayOf(g[0], level))
        val sv = if (ch == 0) brightness else volume
        if (duration > 0) sv.set(withTiming(level, duration, easing)) else sv.set(level)
    }

    private fun publishGoal() {
        val g = goal.get()
        publish(g[0], g[1])
    }

    private val reactions = listOf(
        // Endless encoder: grooves turn only when the level changes inside one
        // channel, so switching modes never spins the knob.
        AnimationEngine.reaction({ channel.get() to value() }) { next, previous ->
            if (previous != null && next.first == previous.first) {
                dialAngle.set(dialAngle.get() + next.second - previous.second)
            }
        },
        // One light tick when a drag reaches 0 or 100; never at drag start.
        AnimationEngine.reaction({
            val owner = dragOwner.get()
            if (owner != 1.0 && owner != 2.0) 9 else {
                val v = value()
                if (v <= 0) -1 else if (v >= 1) 1 else 0
            }
        }) { edge, previous ->
            if (edge != 0 && edge != 9 && previous == 0) edgeFeedback()
        },
        // Trough haptic: one soft tick in the frame the color transfer starts.
        AnimationEngine.reaction({ reveal.get() >= TROUGH }) { past, previous ->
            if (past && previous == false && reveal.get() < 0.9 && fx.troughHaptic && !reduced) softFeedback()
        },
        // Crown detents: a tick each time a groove passes under the finger or
        // during an inertial spin; at most ~33 ticks a second.
        AnimationEngine.reaction({ floor(((dialAngle.get() + dialGive.get()) * DIAL_TURN) / GROOVE) }) { groove, previous ->
            if (previous == null || groove == previous || !fx.crown) return@reaction
            if (dragOwner.get() != 2.0 && !spinning.get()) return@reaction
            val now = System.currentTimeMillis()
            if (now - lastTick < 30) return@reaction
            lastTick = now
            tickFeedback()
        },
        AnimationEngine.reaction({ if (replayRunning.get()) pose().phase else -1 }) { phase, previous ->
            if (phase < 0 || phase == previous) return@reaction
            val frame = pose()
            val next = frame.mode
            val switching = channel.get() != next.toDouble()
            channel.set(next.toDouble())
            // The sampler draws the light; the level glides so the knob turns with
            // it instead of jumping at the phase boundary.
            setLevel(next, frame.level, if (switching || reduced) 0.0 else Motion.hero, easeInOut)
            val g = goal.get()
            publishReplay(replayId.get(), next, g[0], g[1])
        },
    )

    private fun capture(withFill: Boolean) {
        from.set(Frame(fill(), lightAlpha(), lightBlur(), numberAlpha(), labelAlpha(), readout()))
        blendFill.set(withFill && !reduced)
        blend.set(1.0)
        // Opacity only under Reduce Motion; fill movement is excluded above.
        blend.set(withTiming(0.0, HANDOFF, easeInOut, ReduceMotion.Never))
    }

    private fun cancelVisuals(withFill: Boolean) {
        val interrupted = replayTime.get() >= 0 || reveal.get() < 1 || blend.get() > 0
        val shownMix = lightMix()
        if (interrupted) capture(withFill)
        cancelAnimation(replayTime)
        replayRunning.set(false)
        replayTime.set(-1.0)
        cancelAnimation(reveal)
        cancelAnimation(mix)
        reveal.set(1.0)
        mix.set(if (interrupted) shownMix else channel.get())
        if (interrupted) mix.set(withTiming(channel.get(), HANDOFF, easeInOut, ReduceMotion.Never))
        selector.set(withTiming(channel.get(), Motion.fast, Motion.easeOut, ReduceMotion.Never))
        dragOwner.set(0.0)
    }

    private fun quiet() {
        cancelVisuals(true)
        for (sv in listOf(trackPress, dialPress, dialGive)) {
            cancelAnimation(sv)
            sv.set(0.0)
        }
    }

    // Feedback on touch-down, before the recognizer activates or a tap completes.
    private fun pressIn(target: SharedValue) {
        if (!available.get() || reduced) return
        target.set(withTiming(1.0, Motion.press, Motion.easeOut))
    }

    private fun pressOut(target: SharedValue) {
        if (reduced) target.set(0.0) else target.set(withSpring(0.0, Tactile.release))
    }

    private fun settle() {
        dragOwner.set(0.0)
        publishGoal()
    }

    private fun begin(owner: Int) {
        cancelVisuals(true)
        spinning.set(false)
        fine.set(false)
        magnetHeld.set(false)
        dragOwner.set(owner.toDouble())
        dragStart.set(value())
        stopPlayback()
        feedback()
    }

    private fun update(next: Double) {
        if (!available.get()) return
        var level = next
        // Magnet at 50%: snaps within ±1.8% with one light haptic on entry.
        if (fx.magnet) {
            val near = abs(level - 0.5) < 0.018
            if (near) level = 0.5
            if (near && !magnetHeld.get()) edgeFeedback()
            magnetHeld.set(near)
        }
        setLevel(channel.get().toInt(), level, 0.0, Motion.easeOut)
    }

    // Track position → level, switching to ¼-speed relative mode once the
    // finger drops below the track (fine scrub). Stays fine until release.
    private fun trackLevel(x: Double, dy: Double): Double {
        val absolute = levelForFill(x / width.get())
        if (!fx.fineScrub) return absolute
        if (!fine.get() && dy > FINE_DROP) {
            fine.set(true)
            fineAnchor.set(x to value())
            fineShown.set(withTiming(1.0, Motion.fast))
            tickFeedback()
        }
        if (!fine.get()) return absolute
        val (ax, alevel) = fineAnchor.get()
        return alevel + ((x - ax) / width.get()) * 0.25
    }

    val trackPan = PanGesture().apply {
        maxPointers = 1
        onTouchesDown = { touches, fail -> if (touches > 1) fail() }
        activeOffsetX = -4f to 4f
        // Fine scrub needs the finger free to travel down.
        failOffsetY = if (fx.fineScrub) -12f to 400f else -12f to 12f
        onBegin = { pressIn(trackPress) }
        onStart = { e ->
            if (available.get()) {
                begin(1)
                update(trackLevel(e.x.toDouble(), e.translationY.toDouble()))
            }
        }
        onUpdate = { e ->
            if (dragOwner.get() == 1.0) update(trackLevel(e.x.toDouble(), e.translationY.toDouble()))
        }
        onFinalize = { _, _ ->
            pressOut(trackPress)
            if (fine.get()) {
                fine.set(false)
                fineShown.set(withTiming(0.0, Motion.base))
            }
            if (dragOwner.get() == 1.0) settle()
        }
    }

    val trackTap = TapGesture().apply {
        onTouchesDown = { touches, fail -> if (touches > 1) fail() }
        maxDist = 10f
        onEnd = { e, success ->
            if (success && available.get()) {
                begin(3)
                // A jump to a new point glides; a drag follows the finger exactly.
                setLevel(
                    channel.get().toInt(),
                    levelForFill(e.x / this@TactileControl.width.get()),
                    if (reduced) 0.0 else 180.0,
                    Motion.easeOut,
                )
                settle()
            }
        }
    }

    val dialPan = PanGesture().apply {
        maxPointers = 1
        onTouchesDown = { touches, fail -> if (touches > 1) fail() }
        activeOffsetX = -3f to 3f
        failOffsetY = -12f to 12f
        onBegin = { pressIn(dialPress) }
        onStart = { if (available.get()) begin(2) }
        onUpdate = { e ->
            if (dragOwner.get() == 2.0) {
                val raw = dragStart.get() + e.translationX / DIAL_DRAG
                update(raw)
                dialGive.set(if (reduced) 0.0 else rubberBand(raw - clamp01(raw), DIAL_GIVE))
            }
        }
        onEnd = { e, _ ->
            // Crown flick: the level keeps turning with the finger's speed and
            // decays, rubber-banding at the ends; detent ticks thin out with it.
            if (dragOwner.get() == 2.0 && fx.crown && !reduced && abs(e.velocityX) >= 250) {
                val ch = channel.get().toInt()
                spinning.set(true)
                val sv = if (ch == 0) brightness else volume
                sv.set(
                    withDecay(
                        velocity = (e.velocityX / DIAL_DRAG) * 0.6,
                        deceleration = 0.99,
                        clamp = 0.0 to 1.0,
                        rubberBandEffect = true,
                        rubberBandFactor = 0.6,
                    ) {
                        spinning.set(false)
                        setLevel(ch, sv.get(), 0.0, Motion.easeOut)
                        publishGoal()
                    },
                )
            }
        }
        onFinalize = { _, _ ->
            pressOut(dialPress)
            if (dialGive.get() != 0.0) {
                if (reduced) dialGive.set(0.0) else dialGive.set(withSpring(0.0, Tactile.release))
            }
            if (dragOwner.get() == 2.0) settle()
        }
    }

    private fun handoff(next: Int) {
        val currentFill = fill()
        val currentMix = lightMix()
        val currentNumber = readout()
        val changed = channel.get() != next.toDouble()
        // A mode change draws its own contraction; only fade the fill otherwise.
        cancelVisuals(!changed)
        channel.set(next.toDouble())
        if (!changed) return
        outgoingFill.set(currentFill)
        outgoingNumber.set(currentNumber)
        mix.set(currentMix)
        selector.set(withTiming(next.toDouble(), Motion.fast, Motion.easeOut, ReduceMotion.Never))
        // Color feedback is meaningful under Reduce Motion; no movement is
        // retained. Otherwise the color transfers around the trough.
        val color = withTiming(next.toDouble(), 200.0, if (reduced) Motion.easeOut else easeInOut, ReduceMotion.Never)
        mix.set(if (reduced) color else withDelay(90.0, color, ReduceMotion.Never))
        if (!reduced) {
            reveal.set(0.0)
            reveal.set(withTiming(1.0, SWITCH, LinearEasing))
        }
    }

    fun select(next: Int) {
        if (!enabled) return
        stopPlayback()
        mode = next
        if (!available.get()) return
        handoff(next)
        publishGoal()
    }

    fun adjust(delta: Int) {
        if (!enabled) return
        stopPlayback()
        if (!available.get()) return
        cancelVisuals(true)
        val ch = channel.get().toInt()
        // Step from the committed level so rapid presses never lose a step.
        val target = (jsRound(goal.get()[ch] * 100) + delta) / 100
        setLevel(ch, target, if (reduced) 0.0 else Motion.fast, Motion.easeOut)
        publishGoal()
    }

    // Mode selectors sink into their wells on press (90ms) and spring back on
    // release; under Reduce Motion only the darkening remains, instantly.
    fun pressSelector(mode: Int, down: Boolean) {
        val sv = if (mode == 0) brightPress else volumePress
        if (reduced) sv.set(if (down) 1.0 else 0.0)
        else sv.set(if (down) withTiming(1.0, 90.0, Motion.easeOut) else withSpring(0.0, Tactile.release))
    }

    // Power-on: light rises from 0, the number rolls up once, the knob homes
    // from -32° with a soft overshoot, and one soft haptic lands at the top.
    private var booted = false
    fun powerOnOnce() {
        if (booted) return
        booted = true
        if (!fx.powerOn || reduced) return
        boot.set(0.0)
        boot.set(withDelay(400.0, withTiming(1.0, BOOT, LinearEasing) { finished -> if (finished) softFeedback() }))
        dialGive.set(-0.12)
        dialGive.set(withDelay(550.0, withSpring(0.0, SpringConfig(duration = 900.0, dampingRatio = 0.55))))
    }

    /** `enabled = !disabled && focused && foreground`, mirrored to the gestures. */
    fun setForeground(foreground: Boolean) {
        val next = !disabled && foreground
        enabled = next
        available.set(next)
        trackPan.enabled = next
        trackTap.enabled = next
        dialPan.enabled = next
        if (!next || reduced) {
            stopPlayback()
            quiet()
        }
    }

    fun dispose() {
        mounted = false
        stopPlayback()
        quiet()
        reactions.forEach { AnimationEngine.removeReaction(it) }
        legibility.dispose()
        meniscus.dispose()
    }
}
