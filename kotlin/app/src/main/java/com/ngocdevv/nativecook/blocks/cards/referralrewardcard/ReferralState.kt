package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail
import kotlin.math.abs
import kotlin.math.max

/** Owns the choreography, including cancellation: there are no detached delay coroutines. */
internal class ReferralState(
    val feedback: (Int) -> Unit,
    val announce: (Int, Int, Boolean) -> Unit,
    val autoPlay: Boolean,
) {
    val scroll = ReferralValue(2.0)
    val follower = ReferralValue(2.0)
    val peek = ReferralValue(0.0)
    val enter = ReferralValue(0.0)
    val phase = ReferralValue(0.0)
    val pop = ReferralValue(0.0)
    val kick = ReferralValue(0.0)
    val lock = ReferralValue(0.0)
    val glow = ReferralValue(0.0)
    val flash = ReferralValue(0.0)
    val wave = ReferralValue(1.0)
    val crest = ReferralValue(1.0)
    val ripple = ReferralValue(1.0)
    val shape = ReferralValue(0.0)
    val label = ReferralValue(0.0)
    val press = ReferralValue(0.0)
    val swap = ReferralValue(0.0)
    val capsuleShape = ReferralValue(0.0)
    val capsuleLabel = ReferralValue(0.0)
    val actionPress = ReferralValue(0.0)

    // One clock owns the waiting dot, departing head, and button visibility. Separate clocks
    // could disagree for a frame and either duplicate the dot or leave a gap at launch.
    val comet = ReferralValue(Trail.end)
    val flight = ReferralValue(Trail.end)
    val arcStart = ReferralValue(0.0)
    val arcEnd = ReferralValue(2.0 / 3)
    val level = ReferralValue(0.0)
    val bump = ReferralValue(0.0)
    val columns = ReferralValue(2.0)
    val turns = Array(3) { ReferralValue(if (it == 1) 5.0 else 0.0) }
    val shown = Array(3) { ReferralValue(if (it < 2) 1.0 else 0.0) }
    val badges =
        Array(ReferralPalette.friendCount) {
            ReferralValue(
                if (it < ReferralPalette.joinedAtStart) {
                    1.0
                } else {
                    0.0
                },
            )
        }
    private val all =
        arrayOf(
            scroll,
            follower,
            peek,
            enter,
            pop,
            kick,
            lock,
            glow,
            flash,
            wave,
            crest,
            ripple,
            shape,
            label,
            press,
            swap,
            capsuleShape,
            capsuleLabel,
            actionPress,
            comet,
            flight,
            arcStart,
            arcEnd,
            level,
            bump,
            columns,
        ) + turns + shown + badges
    var joinedMask by mutableIntStateOf(3)
        internal set

    var count by mutableIntStateOf(ReferralPalette.joinedAtStart)
        internal set

    var focus by mutableIntStateOf(2)
        private set

    var shownTier by mutableIntStateOf(0)
        private set

    var open by mutableStateOf(false)
        private set

    var capsuleIndex by mutableIntStateOf(2)
        private set

    var reduced by mutableStateOf(false)
        private set

    fun setReducedMotion(value: Boolean) {
        if (reduced == value) return
        reduced = value
        if (!value) {
            liveAt = now
            phase.set(0.0)
            return
        }
        settleReducedMotion()
    }

    var touched = false
        private set

    var hand = false
    var down = false
    var now = 0.0
        private set

    var live = false
        private set

    var path: ReferralPath? = null
        internal set

    var footerPath: ReferralPath? = null
    var width = Size.cardMax.toDouble()
    var railTop = 128.0
    var actionWidth = 62.0
    var fromCapsule by mutableStateOf(false)
        internal set

    var launchingPeek by androidx.compose.runtime.mutableDoubleStateOf(-1.0)
        internal set

    var rewardIndex = 0
        internal set

    private var mounted = -1.0
    private var liveAt = 0.0
    private var smoothFrames = 0
    private var priorFrame = 0.0
    private var nextBeat = Double.POSITIVE_INFINITY
    internal var caught = false
    internal var tierCaught = false
    internal var fillPending = false
    internal var catchArc = 0.0
    internal var countValue = 50
    internal val digitTargets = doubleArrayOf(0.0, 5.0, 0.0)
    internal val digitLast = intArrayOf(0, 5, -1)
    private var previousRest = true
    private var previousScroll = 2.0
    private var warmValue by androidx.compose.runtime.mutableDoubleStateOf(1.0)

    fun joined(index: Int) = joinedMask and (1 shl index) != 0

    fun touch() {
        touched = true
        nextBeat = Double.POSITIVE_INFINITY
    }

    fun frame(ms: Double) {
        now = ms
        updateArrival(ms)
        var i = 0
        while (i < all.size) {
            all[i].step(ms)
            i++
        }
        updateScroll()
        if (live && !reduced) phase.set(((ms - liveAt) % Motion.drift) / Motion.drift)
        updateCatch()
        shownTier = ReferralMath.nearest(level.value).coerceIn(0, 3)
        if (autoPlay && live && !touched && now >= nextBeat) {
            val was = count
            val fills = ReferralMath.tier(was + 1) > ReferralMath.tier(was)
            activate(false)
            nextBeat =
                now +
                when {
                    was == 7 -> Motion.beat
                    was + 1 == 7 -> Motion.restBeat
                    fills -> Motion.tierBeat
                    else -> Motion.beat
                }
        }
    }

    // Let image uploads and the first layout settle before the entrance starts. The deadline
    // prevents a busy device from holding the card dark indefinitely.
    private fun updateArrival(ms: Double) {
        if (mounted < 0) {
            mounted = ms
            priorFrame = ms
        }
        if (!live && (reduced || ms - mounted >= Motion.warmup)) {
            smoothFrames = if (ms - priorFrame < Motion.smoothFrame) smoothFrames + 1 else 0
            if (reduced || smoothFrames >= 6 || ms - mounted > Motion.startupDeadline) {
                live = true
                liveAt = ms
                enter.timing(
                    Motion.arrivalTotal,
                    if (reduced) Motion.gentle else Motion.arrivalTotal,
                    now,
                    curve = if (reduced) ReferralValue.easeOut else ReferralValue.linear,
                )
                nextBeat = ms + Motion.firstBeat
            }
        }
        priorFrame = ms
        warmValue = if (live || reduced) 0.0 else (1 - (now - mounted) / Motion.warmup).coerceIn(0.0, 1.0)
    }

    private fun updateScroll() {
        if (scroll.value != previousScroll && !reduced) {
            follower.timing(scroll.value, Motion.follower, now, curve = ReferralValue.linear)
        }
        previousScroll = scroll.value
        val nextFocus = ReferralMath.index(ReferralMath.nearest(scroll.value))
        if (nextFocus != focus) {
            focus = nextFocus
            if (hand) feedback(0)
        }
        val rest = !down && abs(scroll.value - ReferralMath.nearest(scroll.value)) < 0.05
        if (rest && !previousRest && hand && !reduced) {
            lock.timing(1.0, Motion.follower, now).thenSpring(0.0, Motion.wave, 0.55)
        }
        previousRest = rest
    }

    val warm: Double
        get() = warmValue

    val railArrival
        get() = ReferralMath.quart((enter.value - Motion.stagger) / Motion.enter)

    fun part(index: Int) = ReferralMath.quart((enter.value - index * Motion.stagger) / Motion.enter)

    val rush
        get() =
            if (reduced) {
                0.0
            } else {
                max(abs(scroll.value - follower.value) / 0.09 - 1.5, 0.0) + 13 * warm
            }

    fun changePeek(target: Boolean) {
        // Closing must shed the opening spring's velocity before it can reverse direction.
        peek.cancel()
        open = target
        if (target) {
            launchingPeek = -1.0
            capsuleIndex = focus
            feedback(0)
            announce(focus, if (joined(focus)) -1 else count, true)
        }
        if (reduced) {
            peek.timing(if (target) 1.0 else 0.0, Motion.gentle, now)
        } else {
            peek.spring(
                if (target) 1.0 else 0.0,
                if (target) Motion.peekOpen else Motion.peekClose,
                if (target) 0.78 else 1.0,
                now,
                clamp = !target,
            )
        }
    }

    fun settle(
        to: Int,
        velocity: Double = 0.0,
        flick: Boolean = false,
    ) {
        if (reduced) {
            scroll.timing(to.toDouble(), Motion.gentle, now)
        } else {
            scroll.spring(
                to.toDouble(),
                if (flick) Motion.flick else Motion.settle,
                if (flick) 0.86 else 1.0,
                now,
                initialVelocity = velocity,
            )
        }
    }

    fun step(by: Int) {
        touch()
        hand = true
        changePeek(false)
        settle(ReferralMath.nearest(scroll.value) + by)
    }

    fun activate(byTouch: Boolean = true) {
        if (byTouch) touch()
        if (count == ReferralPalette.friendCount) reset() else invite(null)
    }

    fun inviteCapsule() {
        if (!open || joined(focus)) return
        touch()
        launchingPeek = peek.value
        invite(focus)
    }
}
