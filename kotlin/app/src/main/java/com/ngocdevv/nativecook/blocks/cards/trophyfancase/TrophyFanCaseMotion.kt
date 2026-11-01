package com.ngocdevv.nativecook.blocks.cards.trophyfancase

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin

/** The same eight-second script and sub-stepped damped springs as the RN reference. */
const val LOOP = 8.0
private const val CARD_COUNT = 4
private const val FAN_AT = 0.45
private const val SCRUB_START = 1.5
private const val SCRUB_END = 2.6
private const val PICK_AT = 2.85
private const val BACK_AT = 5.25
private const val CLOSE_AT = 6.45
private const val PICK = 1
private const val CLEAR = 190.0
private const val PASS = 172.0
private const val DROP = 70.0

internal data class TrophyFanCasePose(var x: Double, var y: Double, var r: Double, var s: Double, var f: Double)

internal data class TrophyFanCaseBody(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var r: Double = 0.0,
    var s: Double = 1.0,
    var f: Double = 0.0,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var vr: Double = 0.0,
    var vs: Double = 0.0,
    var vf: Double = 0.0,
    var ph: Int = 0,
)

internal class TrophyFanCaseHand {
    val cards = Array(CARD_COUNT) { TrophyFanCaseBody() }
    var spread = 0.0
    var vspread = 0.0
    var focus = -9.0
    var vfocus = 0.0
    var focusOn = 0.0
    var vfocusOn = 0.0
    var sel = -1
    var held = -1
    var hx = 0.0
    var hy = 0.0
}

internal data class TrophyFanCaseTarget(
    val spread: Double,
    val focus: Double,
    val focusOn: Double,
    val sel: Int,
)

internal data class TrophyFanCaseTouch(
    val startX: Double = 0.0,
    val startY: Double = 0.0,
    val at: Double = 0.0,
    val card: Int = -1,
    val mode: Int = 0,
    val detent: Int = -9,
)

private fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)
private fun segment(time: Double, start: Double, end: Double) = clamp01((time - start) / (end - start))
private fun mix(a: Double, b: Double, amount: Double) = a + (b - a) * amount
private fun smooth(value: Double): Double {
    val x = clamp01(value)
    return x * x * (3.0 - 2.0 * x)
}

private fun springTo(position: Double, velocity: Double, target: Double, stiffness: Double, dampingRatio: Double, dt: Double): Pair<Double, Double> {
    val c = 2.0 * sqrt(stiffness) * dampingRatio
    val steps = max(1, ceil(dt / (1.0 / 240.0)).toInt())
    val h = dt / steps
    var x = position
    var v = velocity
    repeat(steps) {
        v += (stiffness * (target - x) - c * v) * h
        x += v * h
    }
    return x to v
}

private fun handPose(index: Int, spread: Double, focus: Double, focusOn: Double, centerX: Double, fanY: Double): TrophyFanCasePose {
    val c = index - (CARD_COUNT - 1) / 2.0
    val angle = c * 11.0 * spread
    val radians = angle * PI / 180.0
    val radius = 520.0
    val deckX = c * 5.0
    val deckY = abs(c) * 3.0
    val lift = focusOn * max(0.0, 1.0 - abs(focus - index) * 1.5)
    return TrophyFanCasePose(
        x = centerX + mix(deckX, radius * sin(radians), spread),
        y = fanY + mix(deckY, radius * (1.0 - cos(radians)), spread) - 24.0 * lift,
        r = mix(c * 1.6, angle, spread),
        s = mix(0.97, 1.0, spread) * (1.0 + 0.04 * lift),
        f = 0.0,
    )
}

private fun script(time: Double): TrophyFanCaseTarget {
    val t = ((time % LOOP) + LOOP) % LOOP
    val spread = if (t >= FAN_AT && t < CLOSE_AT) 1.0 else 0.0
    val scrubAmount = segment(t, SCRUB_START, SCRUB_END)
    val scrubbing = t >= SCRUB_START - 0.1 && t < SCRUB_END + 0.25
    val focus = if (scrubbing) mix(-0.4, 3.4, smooth(scrubAmount)) else -9.0
    val selected = if (t >= PICK_AT && t < BACK_AT) PICK else -1
    return TrophyFanCaseTarget(spread, focus, if (scrubbing) 1.0 else 0.0, selected)
}

internal class TrophyFanCaseEngine {
    val hand = TrophyFanCaseHand()
    var target = TrophyFanCaseTarget(0.0, -9.0, 0.0, -1)
    var touch = TrophyFanCaseTouch()
    var t = 0.0
    var clock = 0.0
    var lastTouch = -10.0
    var autoPlay = false
    var reduced = false
    var touching = false
    var centerX = 201.0
    var fanY = 352.0
    var selectedY = 336.0
    var bigScale = 1.55
    private var configured = false

    fun configure(width: Double, topInset: Double) {
        val nextCenter = width / 2.0
        val nextFanY = topInset + 352.0
        val nextSelectedY = topInset + 336.0
        val nextBig = min(1.55, (width - 56.0) / CARD_W.toDouble())
        if (!configured || abs(centerX - nextCenter) > 0.01 || abs(fanY - nextFanY) > 0.01) {
            centerX = nextCenter
            fanY = nextFanY
            selectedY = nextSelectedY
            bigScale = nextBig
            configured = true
            reset(reduced)
        } else {
            selectedY = nextSelectedY
            bigScale = nextBig
        }
    }

    fun reset(isReduced: Boolean = reduced) {
        reduced = isReduced
        t = 0.0
        clock = 0.0
        lastTouch = -10.0
        touch = TrophyFanCaseTouch()
        touching = false
        target = TrophyFanCaseTarget(if (reduced) 1.0 else 0.0, -9.0, 0.0, -1)
        hand.spread = target.spread
        hand.vspread = 0.0
        hand.focus = -9.0
        hand.vfocus = 0.0
        hand.focusOn = 0.0
        hand.vfocusOn = 0.0
        hand.sel = -1
        hand.held = -1
        for (i in 0 until CARD_COUNT) {
            val pose = handPose(i, target.spread, -9.0, 0.0, centerX, fanY)
            hand.cards[i].apply {
                x = pose.x; y = pose.y; r = pose.r; s = pose.s; f = 0.0
                vx = 0.0; vy = 0.0; vr = 0.0; vs = 0.0; vf = 0.0; ph = 0
            }
        }
    }

    fun touchStarted() {
        lastTouch = clock
        touching = true
    }

    fun touchEnded() {
        touching = false
        lastTouch = clock
    }

    fun advance(dt: Double) {
        val frame = dt.coerceIn(0.0, 0.05)
        clock += frame
        val touched = hand.held >= 0 || clock - lastTouch < 2.0
        if (autoPlay && !touched) {
            t += frame
            if (t >= LOOP) t -= LOOP
            target = script(t)
        }
        stepHand(frame, target)
    }

    fun choose(index: Int) {
        target = target.copy(spread = 1.0, focusOn = 0.0, sel = index)
    }

    fun closeSelection() {
        target = target.copy(sel = -1)
    }

    fun openCard(index: Int) {
        lastTouch = clock
        touching = false
        target = target.copy(spread = 1.0, focusOn = 0.0, sel = if (target.sel == index) -1 else index)
    }

    /** Stops a touch-only/reduced frame clock only after every spring and phase has settled. */
    fun hasMotion(): Boolean {
        if (touching || hand.held >= 0) return true
        if (hand.sel != target.sel) return true
        if (abs(hand.spread - target.spread) > 0.002 || abs(hand.focusOn - target.focusOn) > 0.002) return true
        if (target.focusOn > 0.0 && abs(hand.focus - target.focus) > 0.01) return true
        if (abs(hand.vspread) > 0.08 || abs(hand.vfocusOn) > 0.08 || (target.focusOn > 0.0 && abs(hand.vfocus) > 0.08)) return true
        for (body in hand.cards) {
            if (abs(body.vx) > 0.08 || abs(body.vy) > 0.08 || abs(body.vr) > 0.08 || abs(body.vs) > 0.002 || abs(body.vf) > 0.08) return true
            if (body.ph == 1 || body.ph == 3 || body.ph == 4) return true
            if (body.ph == 2 && (abs(body.f - 180.0) > 1.0 || hypot(body.x - centerX, body.y - selectedY) > 2.0)) return true
        }
        return false
    }

    fun cardAt(x: Double, y: Double): Int {
        val order = ArrayList<Int>(CARD_COUNT)
        val top = topCard()
        if (top >= 0) order += top
        for (i in CARD_COUNT - 1 downTo 0) if (i != top) order += i
        for (index in order) {
            val body = hand.cards[index]
            val radians = -body.r * PI / 180.0
            val dx = x - body.x
            val dy = y - body.y
            val localX = dx * cos(radians) - dy * sin(radians)
            val localY = dx * sin(radians) + dy * cos(radians)
            if (abs(localX) < CARD_W.toDouble() / 2.0 * body.s && abs(localY) < CARD_H.toDouble() / 2.0 * body.s) return index
        }
        return -1
    }

    fun topCard(): Int {
        var top = -1
        for (i in 0 until CARD_COUNT) {
            val phase = hand.cards[i].ph
            if (phase == 2 || phase == 3) top = i
        }
        return top
    }

    private fun stepHand(dt: Double, wanted: TrophyFanCaseTarget) {
        var spring = springTo(hand.spread, hand.vspread, wanted.spread, 90.0, 0.85, dt)
        hand.spread = spring.first
        hand.vspread = spring.second
        if (wanted.focusOn > 0.0 && hand.focusOn < 0.02) {
            hand.focus = wanted.focus
            hand.vfocus = 0.0
        }
        if (wanted.focusOn > 0.0) {
            spring = springTo(hand.focus, hand.vfocus, wanted.focus, 260.0, 1.0, dt)
            hand.focus = spring.first
            hand.vfocus = spring.second
        }
        spring = springTo(hand.focusOn, hand.vfocusOn, wanted.focusOn, 220.0, 0.9, dt)
        hand.focusOn = spring.first
        hand.vfocusOn = spring.second
        hand.sel = wanted.sel

        var away = hand.sel >= 0 || hand.held >= 0
        for (body in hand.cards) if (body.ph in 1..3) away = true
        for (index in 0 until CARD_COUNT) {
            val body = hand.cards[index]
            val home = handPose(index, hand.spread, hand.focus, hand.focusOn, centerX, fanY)
            if (hand.held == index) {
                if (body.ph != 2) body.ph = if (body.y < home.y - PASS) 2 else 1
                val nextX = hand.hx
                val nextY = hand.hy
                body.vx = if (dt > 0.0) body.vx * 0.5 + ((nextX - body.x) / dt) * 0.5 else body.vx
                body.vy = if (dt > 0.0) body.vy * 0.5 + ((nextY - body.y) / dt) * 0.5 else body.vy
                body.x = nextX
                body.y = nextY
                spring = springTo(body.r, body.vr, (body.vx * 0.012).coerceIn(-14.0, 14.0), 300.0, 0.8, dt)
                body.r = spring.first; body.vr = spring.second
                spring = springTo(body.s, body.vs, 1.08, 300.0, 0.8, dt)
                body.s = spring.first; body.vs = spring.second
                continue
            }
            if (hand.sel == index) {
                if (body.ph == 0 || body.ph == 4) body.ph = 1
                else if (body.ph == 3) body.ph = 2
            } else if (body.ph == 2) body.ph = 3
            else if (body.ph == 1) body.ph = 0
            if (body.ph == 1 && body.y < home.y - PASS) body.ph = 2
            if (body.ph == 3 && abs(body.x - home.x) < 24.0 && abs(body.f) < 25.0 && body.y < home.y - PASS) body.ph = 4
            if (body.ph == 4 && hypot(body.x - home.x, body.y - home.y) < 3.0) body.ph = 0

            var targetX = home.x
            var targetY = home.y
            var targetR = home.r
            var targetScale = home.s
            var targetFlip = 0.0
            var stiffness = 150.0
            if (body.ph == 1 || body.ph == 3) {
                targetY = home.y - CLEAR
                targetR = home.r * if (body.ph == 1) 0.4 else 1.0
                targetScale = 1.04
                stiffness = 170.0
            } else if (body.ph == 2) {
                targetX = centerX
                targetY = selectedY
                targetR = 0.0
                targetScale = bigScale
                stiffness = 120.0
                if (hypot(body.x - centerX, body.y - selectedY) < 70.0) targetFlip = 180.0
            } else if (away) {
                targetY += DROP
                targetScale *= 0.92
            }
            spring = springTo(body.x, body.vx, targetX, stiffness, 0.82, dt)
            body.x = spring.first; body.vx = spring.second
            spring = springTo(body.y, body.vy, targetY, stiffness, 0.82, dt)
            body.y = spring.first; body.vy = spring.second
            spring = springTo(body.r, body.vr, targetR, stiffness, 0.82, dt)
            body.r = spring.first; body.vr = spring.second
            spring = springTo(body.s, body.vs, targetScale, stiffness, 0.82, dt)
            body.s = spring.first; body.vs = spring.second
            spring = springTo(body.f, body.vf, targetFlip, 110.0, 0.86, dt)
            body.f = spring.first; body.vf = spring.second
        }
    }
}

