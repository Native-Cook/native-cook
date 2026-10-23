package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** What the screen shows at one moment. */
internal data class TasteProfileFrame(
    val change: TasteProfileChange,
    val sx: Double,
    val sk: Double,
    val hold: TasteProfileHoldView,
    val followProgress: Double,
    val followPress: Double,
    val following: Boolean,
)

/** A value that is constant, eased over time, or a duration-based spring: a closed-form function of time. */
private class TasteProfileAnim(
    val from: Double,
    val to: Double,
    val t0: Double,
    private val kind: Int,
    private val duration: Double = 0.0,
    private val omega: Double = 0.0,
    private val zeta: Double = 0.0,
    private val v0: Double = 0.0,
    private val settle: Double = 0.0,
) {
    fun value(now: Double): Double = when (kind) {
        TIMING_OUT -> interpolate(from, to, easeOut(segment(now, t0, t0 + duration)))
        TIMING_IN_OUT -> interpolate(from, to, easeInOut(segment(now, t0, t0 + duration)))
        SPRING -> durationSpringValue(now, t0, from, to, v0, omega, zeta, settle)
        else -> to
    }

    companion object {
        const val CONSTANT = 0
        const val TIMING_OUT = 1
        const val TIMING_IN_OUT = 2
        const val SPRING = 3

        fun constant(value: Double) = TasteProfileAnim(value, value, 0.0, CONSTANT)
        fun timing(from: Double, to: Double, t0: Double, duration: Double, inOut: Boolean) =
            TasteProfileAnim(from, to, t0, if (inOut) TIMING_IN_OUT else TIMING_OUT, duration = duration)

        fun spring(from: Double, to: Double, t0: Double, velocity: Double, duration: Double, zeta: Double) =
            TasteProfileAnim(from, to, t0, SPRING, omega = durationOmega(duration, zeta), zeta = zeta, v0 = velocity, settle = duration * 1.5)
    }
}

private data class TasteProfileRecord(
    val from: Int,
    val to: Int,
    val start: Double,
    val leftStart: Double,
    val rightStart: Double,
    val angleStart: Double,
    val backStart: Int,
    val photoStart: Double,
)

private data class TasteProfilePressRecord(val chip: Int, val downAt: Double, val upAt: Double)

private data class TasteProfileTiming(
    val from: Double,
    val to: Double,
    val startedAt: Double,
    val duration: Double,
)

private data class TasteProfileSpringTransition(
    val from: Double,
    val to: Double,
    val startedAt: Double,
    val velocity: Double,
    val stiffness: Double,
    val initialEnergy: Double,
)

private data class TasteProfileSpringSample(val value: Double, val velocity: Double, val finished: Boolean)

private const val FOLLOW_MASS = 4.0
private const val FOLLOW_ENERGY_THRESHOLD = 6e-9

/** Duration-based Reanimated spring stiffness, including its 1.5 perceptual-duration factor. */
private fun followStiffness(displacement: Double, velocity: Double): Double {
    val settlingDuration = TasteProfileFollowMotion.durationMilliseconds * 1.5 / 1000.0
    val zeta = TasteProfileFollowMotion.dampingRatio

    fun energy(stiffness: Double, x: Double, v: Double): Double =
        0.5 * stiffness * x * x + 0.5 * FOLLOW_MASS * v * v

    fun difference(stiffness: Double): Double {
        val omega = sqrt(stiffness / FOLLOW_MASS) * zeta
        val envelope = exp(-omega * settlingDuration)
        val shiftedVelocity = velocity + displacement * omega
        val position = (displacement + shiftedVelocity * settlingDuration) * envelope
        val nextVelocity = position * -omega + shiftedVelocity * envelope
        val startEnergy = energy(stiffness, displacement, velocity)
        if (startEnergy == 0.0) return -FOLLOW_ENERGY_THRESHOLD
        return energy(stiffness, position, nextVelocity) / startEnergy - FOLLOW_ENERGY_THRESHOLD
    }

    var low = 2.220446049250313e-16
    var high = 8_000.0
    val direction = if (difference(high) >= difference(low)) 1.0 else -1.0
    var current = (low + high) / 2.0
    repeat(100) {
        if (abs(difference(current)) <= FOLLOW_ENERGY_THRESHOLD * 1e-3) return current
        if (difference(current) * direction < 0.0) low = current else high = current
        current = (low + high) / 2.0
    }
    return current
}

private fun followSample(transition: TasteProfileSpringTransition, now: Double): TasteProfileSpringSample {
    val elapsed = max(0.0, now - transition.startedAt)
    val zeta = TasteProfileFollowMotion.dampingRatio
    val omega0 = sqrt(transition.stiffness / FOLLOW_MASS)
    val omega1 = omega0 * sqrt(1.0 - zeta * zeta)
    val displacement = transition.from - transition.to
    val envelope = exp(-zeta * omega0 * elapsed)
    val sine = sin(omega1 * elapsed)
    val cosine = cos(omega1 * elapsed)
    val fragment = envelope * (sine * ((transition.velocity + zeta * omega0 * displacement) / omega1) + displacement * cosine)
    val value = transition.to + fragment
    val velocity = -zeta * omega0 * fragment + envelope * (cosine * (transition.velocity + zeta * omega0 * displacement) - omega1 * displacement * sine)
    val energy = 0.5 * transition.stiffness * (transition.to - value) * (transition.to - value) + 0.5 * FOLLOW_MASS * velocity * velocity
    val finished = transition.initialEnergy == 0.0 || energy / transition.initialEnergy <= FOLLOW_ENERGY_THRESHOLD
    return if (finished) TasteProfileSpringSample(transition.to, 0.0, true) else TasteProfileSpringSample(value, velocity, false)
}

internal class TasteProfileController(looping: Boolean, now: Double) {
    var frame: TasteProfileFrame = restFrame()
        private set
    val selectedCity: Int get() = frame.change.to
    val following: Boolean get() = frame.following
    val tileGrown: Boolean get() = holdRecord.tile >= 0 && holdRecord.t1 >= 0.0

    var onSelectionHaptic: () -> Unit = {}
    var onImpactHaptic: () -> Unit = {}

    private var looping = looping
    private var reduced = false
    private var auto = looping
    private var lifecycleActive = true
    private var loopBase = 0.0
    private var loopSince = now
    private var record = restRecord()
    private var press = TasteProfilePressRecord(-1, 0.0, 0.0)
    private var sx = TasteProfileAnim.constant(0.0)
    private var sk = TasteProfileAnim.constant(0.0)
    private var hl = TasteProfileAnim.constant(5.0)
    private var flatAt = -1.0
    private var dragFrom = 0.0
    private var holdRecord = TasteProfileHold.NONE
    private var pendingHold = false
    var holding = false
        private set
    private var touchedAt = -1_000.0
    private var followTransition: TasteProfileSpringTransition? = null
    private var followPressTiming: TasteProfileTiming? = null
    private var followProgress = 0.0
    private var followPress = 0.0
    private var followingNow = false
    private var cellPx = 1.0
    private var unit = 1.0
    private var cardX = listOf(0.0, 0.0, 0.0, 0.0)

    fun setGeometry(cell: Double, unit: Double, tileCardX: List<Double>) {
        cellPx = cell
        this.unit = unit
        cardX = tileCardX
    }

    fun setLifecycleActive(active: Boolean, now: Double) {
        if (active == lifecycleActive) return
        if (!active) {
            loopBase = loopTime(now)
        } else {
            loopSince = now
        }
        lifecycleActive = active
    }

    private fun loopTime(now: Double): Double {
        if (!lifecycleActive || !auto) return loopBase
        val t = (loopBase + now - loopSince) % LOOP
        return if (t < 0.0) t + LOOP else t
    }

    private fun setLoop(t: Double, now: Double) {
        loopBase = t
        loopSince = now
    }

    /** Leaving the loop (reduced motion, a screen reader): hold the screen as it is now. */
    fun configure(looping: Boolean, reduced: Boolean, now: Double) {
        this.reduced = reduced
        if (looping == this.looping) return
        this.looping = looping
        if (looping || !auto) return
        val t = loopTime(now)
        val c = loopChange(t)
        val st = stripAt(t)
        val shown = if (c.tau >= 0.0) c.to else c.from
        record = changeFrom(c, shown, now - SETTLE, jsRound(st.first))
        sx = TasteProfileAnim.constant(jsRound(st.first))
        sk = TasteProfileAnim.constant(0.0)
        auto = false
    }

    fun tick(now: Double): Boolean {
        if (!lifecycleActive) return false
        updateFollow(now)
        updateFollowPress(now)
        val c: TasteProfileChange
        val x: Double
        val k: Double
        val hv: TasteProfileHoldView
        if (auto) {
            val t = loopTime(now)
            c = loopChange(t)
            val st = stripAt(t)
            x = st.first
            k = st.second
            val hs = holdAt(t, TasteProfileHold.SCRIPT)
            hv = holdView(t, TasteProfileHold.SCRIPT, scriptHl(hs.since), false)
        } else {
            if (pendingHold && holding && holdRecord.tile >= 0 && now >= holdRecord.t0 + TasteProfileExpandMotion.hold) {
                holdRecord = holdRecord.copy(t1 = holdRecord.t0 + TasteProfileExpandMotion.hold)
                pendingHold = false
                onImpactHaptic()
            }
            c = manualChange(now)
            hv = holdView(now, holdRecord, hl.value(now), reduced)
            if (flatAt >= 0.0 && now >= flatAt && !holding) {
                flatAt = -1.0
                sk = TasteProfileAnim.timing(sk.value(now), 0.0, now, TasteProfileCoverMotion.outDuration, inOut = true)
            }
            x = sx.value(now)
            k = sk.value(now)
            handBack(now, c, x, k, hv)
        }
        frame = TasteProfileFrame(c, x, k, hv, followProgress, followPress, followingNow)
        return true
    }

    /** Two seconds after the last touch, once the screen has settled, the loop takes over again. */
    private fun handBack(now: Double, c: TasteProfileChange, x: Double, k: Double, hv: TasteProfileHoldView) {
        val still = k == 0.0 && abs(x - jsRound(x)) < 1e-3 && hv.tile < 0
        if (!(looping && still && !holding && now - touchedAt >= RESUME_AFTER && c.tau >= SETTLE && c.press == 0.0)) return
        if (followProgress > 0.5 || followingNow) setFollowing(false, now, reduced = false)
        holdRecord = TasteProfileHold.NONE
        hl = TasteProfileAnim.constant(5.0)
        val rounded = jsRound(x)
        if (record.to == 0 && rounded == 0.0) {
            auto = true
            setLoop(0.0, now)
        } else if (record.to == SCRIPT[0].city && rounded == 0.0) {
            auto = true
            setLoop(TasteProfileStripScript.down - 0.1, now)
        } else if (record.to == SCRIPT[0].city && rounded == TasteProfileStripScript.to.toDouble()) {
            auto = true
            setLoop(TasteProfileHold.SCRIPT.t0 - 0.1, now)
        } else {
            press = TasteProfilePressRecord(0, now, now + TasteProfilePress.`in`)
            record = changeFrom(c, 0, now + TasteProfilePress.`in`, rounded)
            sx = TasteProfileAnim.constant(0.0)
        }
    }

    private fun takeOver(now: Double) {
        if (!auto) return
        val t = loopTime(now)
        val c = loopChange(t)
        val st = stripAt(t)
        record = TasteProfileRecord(c.from, c.to, now - c.tau, c.leftStart, c.rightStart, c.angleStart, c.backStart, c.photoStart)
        press = TasteProfilePressRecord(-1, 0.0, 0.0)
        val h = TasteProfileHold.SCRIPT
        if (t >= h.t0 && t < h.t2 + TasteProfileExpandMotion.out) {
            val hs = holdAt(t, h)
            holdRecord = TasteProfileHold(
                h.tile,
                now - (t - h.t0),
                if (t >= h.t1) now - (t - h.t1) else -1.0,
                if (t < h.t2) now else now - (t - h.t2),
            )
            hl = TasteProfileAnim.constant(scriptHl(hs.since))
        } else {
            holdRecord = TasteProfileHold.NONE
        }
        sk = TasteProfileAnim.constant(st.second)
        sx = if (st.second > 0.0) {
            TasteProfileAnim.spring(st.first, jsRound(st.first), now, 0.0, TasteProfileCoverMotion.takeoverDuration, TasteProfileCoverMotion.snapZeta)
        } else {
            TasteProfileAnim.constant(st.first)
        }
        if (st.second > 0.0) flatAt = now + 0.3
        auto = false
    }

    // Chips.

    fun chipDown(index: Int, now: Double) {
        takeOver(now)
        holding = true
        touchedAt = now
        if (!reduced) press = TasteProfilePressRecord(index, now, 1e9)
    }

    fun chipRelease(now: Double) {
        holding = false
        touchedAt = now
        if (press.chip >= 0 && press.upAt > now) press = press.copy(upAt = now)
    }

    /** Returns true when the committed city changed. */
    fun chipTap(index: Int, now: Double): Boolean {
        takeOver(now)
        touchedAt = now
        val c = manualChange(now)
        val shown = if (c.tau >= 0.0) c.to else c.from
        if (index == shown && index == c.to) return false
        val x0 = jsRound(sx.value(now))
        sx = TasteProfileAnim.constant(0.0)
        sk = TasteProfileAnim.constant(0.0)
        flatAt = -1.0
        record = changeFrom(c, index, now, x0)
        onSelectionHaptic()
        return true
    }

    fun chipCancel(now: Double) = chipRelease(now)

    // Photo strip.

    fun stripBegin(now: Double) {
        takeOver(now)
        holding = true
        touchedAt = now
    }

    fun stripStart(now: Double) {
        val current = sx.value(now)
        sx = TasteProfileAnim.constant(current)
        dragFrom = current
        if (!reduced) sk = TasteProfileAnim.timing(sk.value(now), 1.0, now, TasteProfileCoverMotion.inDuration, inOut = false)
        touchedAt = now
    }

    private fun stripMax(): Double = (STRIP_PHOTOS[record.to].size - 4).toDouble()

    fun stripMove(translation: Double, now: Double) {
        val max = stripMax()
        val raw = dragFrom - translation / cellPx
        val x = if (raw < 0.0) raw * TasteProfileCoverMotion.rubber else if (raw > max) max + (raw - max) * TasteProfileCoverMotion.rubber else raw
        sx = TasteProfileAnim.constant(x)
        touchedAt = now
    }

    fun stripEnd(velocity: Double, now: Double) {
        val max = stripMax()
        val v = -velocity / cellPx
        val from = jsRound(dragFrom)
        val current = sx.value(now)
        val target = jsRound(current + v * TasteProfileCoverMotion.project).coerceIn(0.0, max)
        sx = TasteProfileAnim.spring(current, target, now, v, TasteProfileCoverMotion.snapDuration, TasteProfileCoverMotion.snapZeta)
        flatAt = now + 0.42
        touchedAt = now
        if (target != from) onSelectionHaptic()
    }

    fun stripFinalize(now: Double) {
        holding = false
        touchedAt = now
        if (sk.value(now) > 0.0 && flatAt < 0.0) flatAt = now
    }

    // Stat tiles.

    fun tileDown(tile: Int, now: Double) {
        takeOver(now)
        holding = true
        touchedAt = now
        hl = TasteProfileAnim.constant(5.0)
        holdRecord = TasteProfileHold(tile, now, -1.0, 1e9)
        pendingHold = true
    }

    fun tileMove(xPx: Double, tile: Int, now: Double) {
        if (!tileGrown) return
        val index = barUnder(xPx / unit - cardX[tile]).toDouble()
        val current = hl.value(now)
        if (current != index) {
            if (jsRound(current) != index) onSelectionHaptic()
            hl = TasteProfileAnim.timing(current, index, now, TasteProfileExpandMotion.hlDuration, inOut = false)
        }
        touchedAt = now
    }

    fun tileEnd(now: Double) {
        holdRecord = holdRecord.copy(t2 = now)
        pendingHold = false
        holding = false
        touchedAt = now
    }

    fun followDown(now: Double, reduced: Boolean) {
        if (reduced) return
        startFollowPress(1.0, now, TasteProfilePress.`in`)
    }

    fun followRelease(now: Double) {
        startFollowPress(0.0, now, TasteProfilePress.out)
    }

    fun toggleFollow(now: Double, reduced: Boolean) {
        setFollowing(!followingNow, now, reduced)
        onImpactHaptic()
    }

    private fun setFollowing(next: Boolean, now: Double, reduced: Boolean) {
        followingNow = next
        val target = if (next) 1.0 else 0.0
        if (reduced) {
            followProgress = target
            followTransition = null
            return
        }
        val prior = followTransition?.let { followSample(it, now) }
        val from = prior?.value ?: followProgress
        var velocity = prior?.velocity ?: 0.0
        if ((target > from && velocity < 0.0) || (target < from && velocity > 0.0)) velocity = 0.0
        val stiffness = followStiffness(from - target, velocity)
        val initialEnergy = 0.5 * stiffness * (from - target) * (from - target)
        followProgress = from
        followTransition = TasteProfileSpringTransition(from, target, now, velocity, stiffness, initialEnergy)
    }

    private fun updateFollow(now: Double) {
        val transition = followTransition ?: return
        val sample = followSample(transition, now)
        followProgress = sample.value
        if (sample.finished) followTransition = null
    }

    private fun startFollowPress(target: Double, now: Double, duration: Double) {
        updateFollowPress(now)
        followPressTiming = TasteProfileTiming(followPress, target, now, duration)
    }

    private fun updateFollowPress(now: Double) {
        val timing = followPressTiming ?: return
        val progress = easeOut(segment(now, timing.startedAt, timing.startedAt + timing.duration))
        followPress = interpolate(timing.from, timing.to, progress)
        if (progress >= 1.0) followPressTiming = null
    }

    private fun manualChange(now: Double): TasteProfileChange = TasteProfileChange(
        from = record.from,
        to = record.to,
        tau = now - record.start,
        leftStart = record.leftStart,
        rightStart = record.rightStart,
        angleStart = record.angleStart,
        backStart = record.backStart,
        photoStart = record.photoStart,
        pressedChip = press.chip,
        press = if (press.chip >= 0) pressAt(now, press.downAt, press.upAt) else 0.0,
        reduced = reduced,
    )

    private fun changeFrom(current: TasteProfileChange, target: Int, start: Double, photoStart: Double): TasteProfileRecord {
        val edges = pillAt(current)
        val coin = coinAt(current)
        return TasteProfileRecord(
            from = if (current.tau >= 0.0) current.to else current.from,
            to = target,
            start = start,
            leftStart = edges.first,
            rightStart = edges.second,
            angleStart = coin.angle,
            backStart = coin.back,
            photoStart = photoStart,
        )
    }

    private companion object {
        fun restRecord(): TasteProfileRecord {
            val edges = chipEdges(0)
            return TasteProfileRecord(0, 0, -1e3, edges.first, edges.second, 0.0, 0, 0.0)
        }

        fun restFrame() = TasteProfileFrame(loopChange(0.0), 0.0, 0.0, TasteProfileHoldView(), 0.0, 0.0, false)
    }
}

internal fun tasteProfileNowSeconds(): Double = System.nanoTime() / 1_000_000_000.0
