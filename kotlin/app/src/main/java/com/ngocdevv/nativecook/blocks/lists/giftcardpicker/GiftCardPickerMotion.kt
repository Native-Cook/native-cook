package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object GiftCardPickerMotionSpec {
    const val DiscoverMs = 700L
    const val DiscoveringMs = 2_400L
    const val RevealAmountMs = 600L
    const val CloseFadeMs = 90L
    const val PushDelayMs = 30L
    const val PushMs = 250L
    const val UnderShift = 0.3f
    const val ContentDelayMs = 530L
    const val ContentInMs = 200L
    const val LogoInMs = 240L
    const val LabelSwitchMs = 270L
    const val BackdropFadeMs = 450L
    const val DescriptionFadeMs = 150L
    const val LOOP_MS = 19_870L
    const val ResumeAfterMs = 3_500L
    const val DriftPeriodSeconds = 9.75f
    const val SpinPeriodSeconds = 3f
    const val SpinAtSeconds = 4.6f
    const val PressScale = 0.97f
    const val PressInMs = 120L
    const val PressOutMs = 150L
    const val ReducedFadeMs = 200L
    const val SettleStiffness = 20.25f
    const val SettleDamping = 9f
    const val SettleMass = 1f
    const val SettleVelocity = 1.2f
    const val SnapMs = 400L
    const val SnapDampingRatio = 0.8f
    const val CommitMs = 2_540L
    const val CommitFlightAtMs = 1_500L
    const val CommitFlightMs = 620L
    const val CommitLandingMs = 420L

    val Cues = buildList {
        add(GiftCardPickerCue(500L, GiftCardPickerCueKind.Present))
        add(GiftCardPickerCue(3_100L, GiftCardPickerCueKind.Discover))
        add(GiftCardPickerCue(8_600L, GiftCardPickerCueKind.Choose))
        listOf(10_650L, 12_670L, 14_770L, 17_070L).forEach { at ->
            add(GiftCardPickerCue(at, GiftCardPickerCueKind.Swipe))
            add(GiftCardPickerCue(at + LabelSwitchMs, GiftCardPickerCueKind.Pick))
        }
        add(GiftCardPickerCue(19_500L, GiftCardPickerCueKind.Dismiss))
    }.sortedBy { it.atMs }
}

internal enum class GiftCardPickerEase {
    Linear,
    EaseOut,
    EaseIn,
    EaseInOut,
    EaseInOutQuad,
    Sheet,
    Spring,
}

internal data class GiftCardPickerCue(
    val atMs: Long,
    val kind: GiftCardPickerCueKind,
)

internal enum class GiftCardPickerCueKind {
    Present,
    Discover,
    Choose,
    Swipe,
    Pick,
    Dismiss,
}

private data class GiftCardPickerSegment(
    val from: Float,
    val to: Float,
    val startMs: Long,
    val durationMs: Long,
    val ease: GiftCardPickerEase,
    val velocity: Float = 0f,
    val stiffness: Float = 0f,
    val damping: Float = 0f,
    val mass: Float = 1f,
    val dampingRatio: Float = 0.8f,
)

@Stable
internal class GiftCardPickerTrack(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set

    private var initialValue = initial
    private val segments = mutableListOf<GiftCardPickerSegment>()

    fun animateTo(
        target: Float,
        startMs: Long,
        durationMs: Long,
        ease: GiftCardPickerEase,
    ) {
        val from = valueAt(startMs)
        replaceFuture(from, startMs)
        segments += GiftCardPickerSegment(from, target, startMs, durationMs, ease)
    }

    fun springTo(
        target: Float,
        startMs: Long,
        durationMs: Long,
        dampingRatio: Float,
        velocity: Float,
    ) {
        val from = valueAt(startMs)
        val inheritedVelocity = velocityAt(startMs)
        val proposedVelocity = inheritedVelocity + velocity
        val resolvedVelocity = if (
            (target > from && proposedVelocity < 0f) ||
            (target < from && proposedVelocity > 0f)
        ) {
            0f
        } else {
            proposedVelocity
        }
        val mass = 4f
        val stiffness = giftCardPickerDurationSpringStiffness(
            displacement = from - target,
            velocity = resolvedVelocity,
            durationMs = durationMs,
            dampingRatio = dampingRatio,
        )
        val damping = 2f * dampingRatio * sqrt(stiffness * mass)
        replaceFuture(from, startMs)
        segments += GiftCardPickerSegment(
            from = from,
            to = target,
            startMs = startMs,
            durationMs = giftCardPickerPhysicalSpringDurationMs(
                displacement = from - target,
                velocity = resolvedVelocity,
                initialEnergyVelocity = velocity,
                stiffness = stiffness,
                damping = damping,
                mass = mass,
            ),
            ease = GiftCardPickerEase.Spring,
            velocity = resolvedVelocity,
            stiffness = stiffness,
            damping = damping,
            mass = mass,
        )
    }

    fun physicalSpringTo(
        target: Float,
        startMs: Long,
        stiffness: Float,
        damping: Float,
        mass: Float,
        velocity: Float,
    ) {
        val from = valueAt(startMs)
        val inheritedVelocity = velocityAt(startMs)
        val proposedVelocity = inheritedVelocity + velocity
        val resolvedVelocity = if (
            (target > from && proposedVelocity < 0f) ||
            (target < from && proposedVelocity > 0f)
        ) {
            0f
        } else {
            proposedVelocity
        }
        replaceFuture(from, startMs)
        segments += GiftCardPickerSegment(
            from = from,
            to = target,
            startMs = startMs,
            durationMs = giftCardPickerPhysicalSpringDurationMs(
                displacement = from - target,
                velocity = resolvedVelocity,
                initialEnergyVelocity = velocity,
                stiffness = stiffness,
                damping = damping,
                mass = mass,
            ),
            ease = GiftCardPickerEase.Spring,
            velocity = resolvedVelocity,
            stiffness = stiffness,
            damping = damping,
            mass = mass,
        )
    }

    fun jumpTo(target: Float) {
        initialValue = target
        segments.clear()
        value = target
    }

    fun cancelAt(nowMs: Long) {
        jumpTo(valueAt(nowMs))
    }

    fun update(nowMs: Long) {
        val next = valueAt(nowMs)
        if (abs(next - value) > 0.00001f) value = next
    }

    fun isRunning(nowMs: Long): Boolean {
        if (segments.any { nowMs < it.startMs + it.durationMs }) return true
        val terminal = segments.lastOrNull() ?: return false
        // A frame deadline can pass between the last sample and the loop's continuation check.
        // Keep the driver alive for one reconciliation frame until the observable value reaches
        // the terminal target; otherwise guards such as stage >= 1.99 can remain false forever.
        return nowMs >= terminal.startMs && abs(value - terminal.to) > 0.00001f
    }

    // Reanimated starts a replacement from the sampled value immediately. Keep completed history
    // for delayed sequences, but truncate the segment that the new animation interrupts.
    private fun replaceFuture(from: Float, startMs: Long) {
        segments.removeAll { it.startMs >= startMs }
        val interrupted = segments.indexOfLast { segment ->
            segment.startMs < startMs && segment.startMs + segment.durationMs > startMs
        }
        if (interrupted >= 0) {
            val segment = segments[interrupted]
            segments[interrupted] = segment.copy(
                to = from,
                durationMs = startMs - segment.startMs,
            )
        }
    }

    private fun velocityAt(nowMs: Long): Float {
        val segment = segments.lastOrNull { segment ->
            segment.ease == GiftCardPickerEase.Spring &&
                segment.stiffness > 0f &&
                nowMs >= segment.startMs &&
                nowMs < segment.startMs + segment.durationMs
        } ?: return 0f
        return giftCardPickerPhysicalSpringState(
            displacement = (segment.from - segment.to).toDouble(),
            velocity = segment.velocity.toDouble(),
            stiffness = segment.stiffness.toDouble(),
            damping = segment.damping.toDouble(),
            mass = segment.mass.toDouble(),
            time = (nowMs - segment.startMs) / 1_000.0,
        ).second.toFloat()
    }

    private fun valueAt(nowMs: Long): Float {
        var result = initialValue
        for (segment in segments) {
            if (nowMs < segment.startMs) return result
            val elapsedMs = nowMs - segment.startMs
            if (elapsedMs < segment.durationMs) {
                if (segment.ease == GiftCardPickerEase.Spring && segment.stiffness > 0f) {
                    val displacement = giftCardPickerPhysicalSpringState(
                        displacement = (segment.from - segment.to).toDouble(),
                        velocity = segment.velocity.toDouble(),
                        stiffness = segment.stiffness.toDouble(),
                        damping = segment.damping.toDouble(),
                        mass = segment.mass.toDouble(),
                        time = elapsedMs / 1_000.0,
                    ).first
                    return segment.to + displacement.toFloat()
                }
                val t = (elapsedMs.toDouble() / segment.durationMs).coerceIn(0.0, 1.0)
                val eased = when (segment.ease) {
                    GiftCardPickerEase.Linear -> t
                    GiftCardPickerEase.EaseOut -> giftCardPickerBezier(t, 0.23, 1.0, 0.32, 1.0)
                    GiftCardPickerEase.EaseIn -> t * t
                    GiftCardPickerEase.EaseInOut -> giftCardPickerBezier(t, 0.77, 0.0, 0.175, 1.0)
                    GiftCardPickerEase.EaseInOutQuad -> if (t < 0.5) 2.0 * t * t else 1.0 - (-2.0 * t + 2.0).pow(2) / 2.0
                    GiftCardPickerEase.Sheet -> giftCardPickerBezier(t, 0.32, 0.72, 0.0, 1.0)
                    GiftCardPickerEase.Spring -> giftCardPickerSpringValue(segment, t)
                }
                return segment.from + (segment.to - segment.from) * eased.toFloat()
            }
            result = segment.to
        }
        return result
    }
}

private fun giftCardPickerBezier(
    progress: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Double {
    fun sample(t: Double, first: Double, second: Double): Double {
        val inverse = 1.0 - t
        return 3.0 * inverse * inverse * t * first +
            3.0 * inverse * t * t * second +
            t * t * t
    }

    var low = 0.0
    var high = 1.0
    repeat(14) {
        val middle = (low + high) / 2.0
        if (sample(middle, x1, x2) < progress) low = middle else high = middle
    }
    return sample((low + high) / 2.0, y1, y2)
}

private fun giftCardPickerSpringValue(segment: GiftCardPickerSegment, fraction: Double): Double {
    val durationSeconds = segment.durationMs / 1_000.0
    val time = fraction * durationSeconds

    val zeta = segment.dampingRatio.toDouble().coerceIn(0.01, 0.999)
    val naturalFrequency = 4.0 / (zeta * durationSeconds)
    val dampedFrequency = naturalFrequency * sqrt(1.0 - zeta * zeta)
    val distance = (segment.to - segment.from).toDouble()
    val normalizedVelocity = if (abs(distance) > 0.0001) segment.velocity.toDouble() / distance else 0.0

    fun raw(at: Double): Double {
        val decay = exp(-zeta * naturalFrequency * at)
        val sineCoefficient = (zeta * naturalFrequency - normalizedVelocity) / dampedFrequency
        return 1.0 - decay * (
            cos(dampedFrequency * at) + sineCoefficient * sin(dampedFrequency * at)
        )
    }

    val end = raw(durationSeconds)
    return if (abs(end) > 0.0001) raw(time) / end else fraction
}

// Reanimated 4.5.1 duration spring: mass 4, threshold 6e-9 and 1.5× perceptual duration.
private fun giftCardPickerDurationSpringStiffness(
    displacement: Float,
    velocity: Float,
    durationMs: Long,
    dampingRatio: Float,
): Float {
    if (abs(displacement) + abs(velocity) < 1e-12f) return 900f
    val mass = 4.0
    val duration = durationMs / 1_000.0 * 1.5
    val startDisplacement = displacement.toDouble()
    val startVelocity = velocity.toDouble()

    fun difference(stiffness: Double): Double {
        val omega = sqrt(stiffness / mass) * dampingRatio.toDouble()
        val envelope = exp(-omega * duration)
        val x = (startDisplacement + (startVelocity + startDisplacement * omega) * duration) * envelope
        val v = x * -omega + (startVelocity + startDisplacement * omega) * envelope
        val initialEnergy = stiffness * startDisplacement * startDisplacement + mass * startVelocity * startVelocity
        return (stiffness * x * x + mass * v * v) / initialEnergy - 6e-9
    }

    var low = 2.220446049250313e-16
    var high = 8_000.0
    var stiffness = (low + high) / 2.0
    repeat(100) {
        val delta = difference(stiffness)
        if (abs(delta) <= 6e-12) return stiffness.toFloat()
        if (delta > 0.0) low = stiffness else high = stiffness
        stiffness = (low + high) / 2.0
    }
    return stiffness.toFloat()
}

private fun giftCardPickerPhysicalSpringState(
    displacement: Double,
    velocity: Double,
    stiffness: Double,
    damping: Double,
    mass: Double,
    time: Double,
): Pair<Double, Double> {
    val naturalFrequency = sqrt(stiffness / mass)
    val dampingRatio = damping / (2.0 * sqrt(stiffness * mass))
    if (dampingRatio >= 0.999) {
        val coefficient = velocity + naturalFrequency * displacement
        val envelope = exp(-naturalFrequency * time)
        val x = (displacement + coefficient * time) * envelope
        val v = (coefficient - naturalFrequency * (displacement + coefficient * time)) * envelope
        return x to v
    }
    val dampedFrequency = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
    val coefficient = (velocity + dampingRatio * naturalFrequency * displacement) / dampedFrequency
    val envelope = exp(-dampingRatio * naturalFrequency * time)
    val shape = displacement * cos(dampedFrequency * time) + coefficient * sin(dampedFrequency * time)
    val derivative = -displacement * dampedFrequency * sin(dampedFrequency * time) +
        coefficient * dampedFrequency * cos(dampedFrequency * time)
    return envelope * shape to envelope * (derivative - dampingRatio * naturalFrequency * shape)
}

private fun giftCardPickerPhysicalSpringDurationMs(
    displacement: Float,
    velocity: Float,
    initialEnergyVelocity: Float = velocity,
    stiffness: Float,
    damping: Float,
    mass: Float,
): Long {
    val k = stiffness.toDouble()
    val m = mass.toDouble()
    val c = damping.toDouble()
    val initialDisplacement = displacement.toDouble()
    val initialVelocity = velocity.toDouble()
    val energyVelocity = initialEnergyVelocity.toDouble()
    val initialEnergy = k * initialDisplacement * initialDisplacement + m * energyVelocity * energyVelocity
    if (initialEnergy <= 0.0) return 0L
    fun energyRatio(time: Double): Double {
        val state = giftCardPickerPhysicalSpringState(
            displacement = initialDisplacement,
            velocity = initialVelocity,
            stiffness = k,
            damping = c,
            mass = m,
            time = time,
        )
        return (k * state.first * state.first + m * state.second * state.second) / initialEnergy
    }

    var low = 0.0
    var high = 20.0
    repeat(48) {
        val middle = (low + high) / 2.0
        if (energyRatio(middle) > 6e-9) low = middle else high = middle
    }
    return ceil(high * 1_000.0).toLong()
}

@Stable
internal class GiftCardPickerMotionState(
    private val reduceMotion: Boolean,
) {
    val sheet = GiftCardPickerTrack(0f)
    val stage = GiftCardPickerTrack(0f)
    val push = GiftCardPickerTrack(0f)
    val reveal = GiftCardPickerTrack(0f)
    val scroll = GiftCardPickerTrack(0f)
    val trackFade = GiftCardPickerTrack(1f)
    val commit = GiftCardPickerTrack(0f)
    val pressGift = GiftCardPickerTrack(0f)
    val pressLight = GiftCardPickerTrack(0f)
    val pressDark = GiftCardPickerTrack(0f)
    val backdrops = List(GiftCardPickerStocks.size) { index ->
        GiftCardPickerTrack(if (index == 0) 1f else 0f)
    }
    val descriptions = List(GiftCardPickerStocks.size) { index ->
        GiftCardPickerTrack(if (index == 0) 1f else 0f)
    }

    var activeIndex by mutableIntStateOf(0)
        private set
    var layer by mutableIntStateOf(0)
        private set
    var commitIndex by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var frameEpoch by mutableIntStateOf(0)
        private set
    var resumeGeneration by mutableIntStateOf(0)
        private set
    var resumePending by mutableStateOf(false)
        private set
    var timeSeconds by mutableFloatStateOf(0f)
        private set

    private var autoPlay = false
    private var loopStartNanos = 0L
    private var lastFrameNanos = 0L
    private var loopClockMs = -1L
    private var dragStart = 0f
    private var dismissResetAtMs = Long.MAX_VALUE
    private var reducedScrollJumpAtMs = Long.MAX_VALUE
    private var reducedScrollTarget = 0f
    private var commitResetAtMs = Long.MAX_VALUE

    fun configureAutoPlay(enabled: Boolean) {
        autoPlay = enabled && !reduceMotion
        playing = autoPlay
    }

    fun freezeAt(milliseconds: Long) {
        val at = milliseconds.coerceIn(0L, GiftCardPickerMotionSpec.LOOP_MS - 1L)
        autoPlay = false
        playing = false
        sheet.jumpTo(0f)
        resetContent()
        layer = 0
        GiftCardPickerMotionSpec.Cues.forEach { cue ->
            if (cue.atMs <= at) runCue(cue, cue.atMs)
        }
        updateTracks(at)
        loopClockMs = at
        timeSeconds = at / 1_000f
    }

    fun wakeFrames() {
        frameEpoch++
    }

    fun touchStarted(nowMs: Long) {
        resumeGeneration++
        resumePending = false
        if (!autoPlay) return
        playing = false
        wakeFrames()
    }

    fun touchEnded(nowMs: Long) {
        if (!autoPlay) return
        resumePending = true
        resumeGeneration++
    }

    fun resume(nowMs: Long) {
        if (!autoPlay) return
        val at = if (sheet.value > 0.01f) {
            dismiss(nowMs)
            GiftCardPickerMotionSpec.LOOP_MS - 600L
        } else {
            resetContent()
            0L
        }
        loopStartNanos = nowMs * 1_000_000L - at * 1_000_000L
        loopClockMs = at - 1L
        playing = true
        resumePending = false
        wakeFrames()
    }

    fun present(nowMs: Long) {
        if (sheet.value > 0.01f) return
        resetContent()
        layer = 1
        val duration = if (reduceMotion) GiftCardPickerMotionSpec.ReducedFadeMs else 300L
        val ease = if (reduceMotion) GiftCardPickerEase.EaseInOutQuad else GiftCardPickerEase.Sheet
        sheet.animateTo(1f, nowMs, duration, ease)
    }

    fun dismiss(nowMs: Long) {
        val duration = if (reduceMotion) GiftCardPickerMotionSpec.ReducedFadeMs else 240L
        val ease = if (reduceMotion) GiftCardPickerEase.EaseInOutQuad else GiftCardPickerEase.Sheet
        sheet.animateTo(0f, nowMs, duration, ease)
        dismissResetAtMs = nowMs + duration
    }

    fun discover(nowMs: Long) {
        if (stage.value > 0.001f || sheet.value < 0.99f) return
        stage.animateTo(1f, nowMs, GiftCardPickerMotionSpec.DiscoverMs, GiftCardPickerEase.Linear)
        stage.animateTo(
            2f,
            nowMs + GiftCardPickerMotionSpec.DiscoverMs + GiftCardPickerMotionSpec.DiscoveringMs,
            GiftCardPickerMotionSpec.RevealAmountMs,
            GiftCardPickerEase.Linear,
        )
    }

    fun choose(nowMs: Long) {
        if (stage.value < 1.99f || push.value > 0.001f) return
        layer = 2
        val pushStart = if (reduceMotion) nowMs else nowMs + GiftCardPickerMotionSpec.PushDelayMs
        val pushDuration = if (reduceMotion) GiftCardPickerMotionSpec.ReducedFadeMs else GiftCardPickerMotionSpec.PushMs
        val pushEase = if (reduceMotion) GiftCardPickerEase.EaseInOutQuad else GiftCardPickerEase.EaseOut
        push.animateTo(1f, pushStart, pushDuration, pushEase)

        val revealStart = nowMs + GiftCardPickerMotionSpec.PushDelayMs + GiftCardPickerMotionSpec.ContentDelayMs
        reveal.animateTo(1f, revealStart, GiftCardPickerMotionSpec.ContentInMs, GiftCardPickerEase.EaseInOutQuad)
        reveal.animateTo(
            2f,
            revealStart + GiftCardPickerMotionSpec.ContentInMs,
            GiftCardPickerMotionSpec.LogoInMs,
            GiftCardPickerEase.EaseInOutQuad,
        )
    }

    fun commitSelection(nowMs: Long) {
        if (layer != 2 || reveal.value < 0.99f || commit.value > 0.001f) return
        commitIndex = activeIndex
        layer = 3
        if (reduceMotion) {
            commit.animateTo(1f, nowMs, 420L, GiftCardPickerEase.EaseInOutQuad)
            sheet.animateTo(0f, nowMs + 200L, 200L, GiftCardPickerEase.EaseInOutQuad)
            commitResetAtMs = nowMs + 420L
        } else {
            commit.animateTo(1f, nowMs, GiftCardPickerMotionSpec.CommitMs, GiftCardPickerEase.Linear)
            sheet.animateTo(
                0f,
                nowMs + GiftCardPickerMotionSpec.CommitFlightAtMs,
                GiftCardPickerMotionSpec.CommitFlightMs,
                GiftCardPickerEase.Sheet,
            )
            commitResetAtMs = nowMs + GiftCardPickerMotionSpec.CommitMs
        }
        wakeFrames()
    }

    fun pick(index: Int, nowMs: Long, allowHaptic: Boolean): Boolean {
        val selected = index.coerceIn(0, GiftCardPickerStocks.lastIndex)
        val previous = activeIndex
        if (selected == previous) return false
        activeIndex = selected
        val fadeMs = GiftCardPickerMotionSpec.BackdropFadeMs
        if (selected > previous) {
            for (i in 0 until selected) backdrops[i].jumpTo(1f)
            backdrops[selected].animateTo(1f, nowMs, fadeMs, GiftCardPickerEase.EaseInOutQuad)
        } else {
            backdrops[selected].jumpTo(1f)
            for (i in selected + 1..GiftCardPickerStocks.lastIndex) {
                if (backdrops[i].value > 0f) {
                    backdrops[i].animateTo(0f, nowMs, fadeMs, GiftCardPickerEase.EaseInOutQuad)
                }
            }
        }
        descriptions.forEachIndexed { i, track ->
            track.animateTo(
                if (i == selected) 1f else 0f,
                nowMs,
                GiftCardPickerMotionSpec.DescriptionFadeMs,
                GiftCardPickerEase.EaseInOutQuad,
            )
        }
        return allowHaptic
    }

    fun goTo(index: Int, velocity: Float, nowMs: Long, allowHaptic: Boolean): Boolean {
        val selected = index.coerceIn(0, GiftCardPickerStocks.lastIndex)
        val changed = pick(selected, nowMs, allowHaptic)
        if (reduceMotion) {
            trackFade.animateTo(0f, nowMs, 90L, GiftCardPickerEase.EaseInOutQuad)
            reducedScrollJumpAtMs = nowMs + 90L
            reducedScrollTarget = selected.toFloat()
        } else {
            scroll.springTo(
                target = selected.toFloat(),
                startMs = nowMs,
                durationMs = GiftCardPickerMotionSpec.SnapMs,
                dampingRatio = GiftCardPickerMotionSpec.SnapDampingRatio,
                velocity = velocity,
            )
        }
        return changed
    }

    fun beginDrag(nowMs: Long) {
        scroll.cancelAt(nowMs)
        dragStart = scroll.value
    }

    fun drag(translationX: Float, spacing: Float, nowMs: Long): Boolean {
        val last = GiftCardPickerStocks.lastIndex.toFloat()
        var position = dragStart - translationX / spacing
        if (position < 0f) {
            val distance = -position * 0.35f
            position = -distance / (1f + distance)
        }
        if (position > last) {
            val distance = (position - last) * 0.35f
            position = last + distance / (1f + distance)
        }
        scroll.jumpTo(position)
        val rounded = position.roundToInt().coerceIn(0, GiftCardPickerStocks.lastIndex)
        return pick(rounded, nowMs, allowHaptic = !playing)
    }

    fun endDrag(velocityX: Float, spacing: Float, nowMs: Long): Boolean {
        val value = -velocityX / spacing
        val base = dragStart.roundToInt()
        val target = (scroll.value + value * 0.18f)
            .roundToInt()
            .coerceIn(base - 1, base + 1)
            .coerceIn(0, GiftCardPickerStocks.lastIndex)
        return goTo(target, value, nowMs, allowHaptic = !playing)
    }

    fun swipe(nowMs: Long) {
        val index = scroll.value.roundToInt()
        if (index >= GiftCardPickerStocks.lastIndex) return
        scroll.physicalSpringTo(
            target = index + 1f,
            startMs = nowMs,
            stiffness = GiftCardPickerMotionSpec.SettleStiffness,
            damping = GiftCardPickerMotionSpec.SettleDamping,
            mass = GiftCardPickerMotionSpec.SettleMass,
            velocity = GiftCardPickerMotionSpec.SettleVelocity,
        )
    }

    fun setPress(track: GiftCardPickerTrack, pressed: Boolean, nowMs: Long) {
        track.animateTo(
            if (pressed) 1f else 0f,
            nowMs,
            if (pressed) GiftCardPickerMotionSpec.PressInMs else GiftCardPickerMotionSpec.PressOutMs,
            GiftCardPickerEase.EaseInOutQuad,
        )
    }

    fun needsFrames(nowMs: Long): Boolean {
        if (autoPlay) return true
        if (reducedScrollJumpAtMs != Long.MAX_VALUE) return true
        val tracks = listOf(sheet, stage, push, reveal, scroll, trackFade, commit, pressGift, pressLight, pressDark) + backdrops + descriptions
        if (tracks.any { it.isRunning(nowMs) }) return true
        val currentStage = stage.value
        return !reduceMotion && layer == 1 && currentStage > 0.25f && currentStage < 1.8f
    }

    fun frame(frameNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameNanos
            if (autoPlay && playing && loopStartNanos == 0L) loopStartNanos = frameNanos
        }
        val deltaNanos = (frameNanos - lastFrameNanos).coerceAtLeast(0L)
        lastFrameNanos = frameNanos
        val nowMs = frameNanos / 1_000_000L

        if (autoPlay && playing) {
            if (loopStartNanos == 0L) loopStartNanos = frameNanos
            val previous = loopClockMs
            val nowLoopMs = ((frameNanos - loopStartNanos) / 1_000_000L).coerceAtLeast(0L)
            if (nowLoopMs >= GiftCardPickerMotionSpec.LOOP_MS) {
                GiftCardPickerMotionSpec.Cues.forEach { cue ->
                    if (cue.atMs > previous) runCue(cue, loopStartNanos / 1_000_000L + cue.atMs)
                }
                resetContent()
                layer = 0
                sheet.jumpTo(0f)
                loopStartNanos += GiftCardPickerMotionSpec.LOOP_MS * 1_000_000L
                loopClockMs = -1L
                timeSeconds = 0f
            } else {
                for (cue in GiftCardPickerMotionSpec.Cues) {
                    if (cue.atMs > previous && cue.atMs <= nowLoopMs) {
                        runCue(cue, loopStartNanos / 1_000_000L + cue.atMs)
                    }
                }
                loopClockMs = nowLoopMs
                timeSeconds = nowLoopMs / 1_000f
            }
        } else {
            timeSeconds += deltaNanos / 1_000_000_000f
        }

        updateTracks(nowMs)
    }

    private fun updateTracks(nowMs: Long) {
        sheet.update(nowMs)
        stage.update(nowMs)
        push.update(nowMs)
        reveal.update(nowMs)
        scroll.update(nowMs)
        trackFade.update(nowMs)
        commit.update(nowMs)
        pressGift.update(nowMs)
        pressLight.update(nowMs)
        pressDark.update(nowMs)
        backdrops.forEach { it.update(nowMs) }
        descriptions.forEach { it.update(nowMs) }
        if (nowMs >= reducedScrollJumpAtMs) {
            val jumpAt = reducedScrollJumpAtMs
            reducedScrollJumpAtMs = Long.MAX_VALUE
            scroll.jumpTo(reducedScrollTarget)
            trackFade.animateTo(1f, jumpAt, 130L, GiftCardPickerEase.EaseInOutQuad)
        }
        if (nowMs >= dismissResetAtMs) {
            dismissResetAtMs = Long.MAX_VALUE
            resetContent()
            layer = 0
        }
        if (nowMs >= commitResetAtMs) {
            commitResetAtMs = Long.MAX_VALUE
            sheet.jumpTo(0f)
            resetContent()
            layer = 0
        }
    }

    private fun runCue(cue: GiftCardPickerCue, atMs: Long) {
        when (cue.kind) {
            GiftCardPickerCueKind.Present -> present(atMs)
            GiftCardPickerCueKind.Discover -> discover(atMs)
            GiftCardPickerCueKind.Choose -> choose(atMs)
            GiftCardPickerCueKind.Swipe -> swipe(atMs)
            GiftCardPickerCueKind.Pick -> pick(activeIndex + 1, atMs, allowHaptic = false)
            GiftCardPickerCueKind.Dismiss -> dismiss(atMs)
        }
    }

    private fun resetContent() {
        stage.jumpTo(0f)
        push.jumpTo(0f)
        reveal.jumpTo(0f)
        scroll.jumpTo(0f)
        trackFade.jumpTo(1f)
        commit.jumpTo(0f)
        activeIndex = 0
        backdrops.forEachIndexed { index, track -> track.jumpTo(if (index == 0) 1f else 0f) }
        descriptions.forEachIndexed { index, track -> track.jumpTo(if (index == 0) 1f else 0f) }
        dismissResetAtMs = Long.MAX_VALUE
        reducedScrollJumpAtMs = Long.MAX_VALUE
        commitResetAtMs = Long.MAX_VALUE
    }
}

internal fun giftCardPickerClamp01(value: Float): Float = value.coerceIn(0f, 1f)

internal fun giftCardPickerLerp(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

internal fun giftCardPickerEaseOut(value: Float): Float {
    val t = giftCardPickerClamp01(value)
    val inverse = 1f - t
    return 1f - inverse * inverse * inverse
}

internal fun giftCardPickerEaseIn(value: Float): Float {
    val t = giftCardPickerClamp01(value)
    return t * t
}

internal const val GiftCardPickerTau = (PI * 2.0).toFloat()
