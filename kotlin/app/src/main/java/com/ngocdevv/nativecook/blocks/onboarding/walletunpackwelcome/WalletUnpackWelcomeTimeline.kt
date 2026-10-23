package com.ngocdevv.nativecook.blocks.onboarding.walletunpackwelcome

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The hero as a function of one clock: [heroAt] turns the time into the sequence (the card falls
 * back and the wallet comes up, two notes rise out of it and slide back, the card rises out and
 * lands) into positions, angles and opacities. The autoPlay loop and a tap read the same one.
 */

/** Seconds of one autoPlay loop. */
internal const val LOOP = 7.5
/** Seconds after the last touch, once the sequence has ended, before the loop starts again. */
internal const val USER_HOLD = 3.0

// The sequence, in seconds from the start of a loop; a tap starts it at OUT_AT.
/** The card lies still until then. */
internal const val OUT_AT = 0.8
/** The card tips back past edge-on and drops behind the wallet, to show below it. */
private const val OUT_FOR = 0.7
private const val TIP_FOR = 0.6
private const val TIP = 104.0
private const val DROP = 100.0
private const val WALLET_IN_AT = 0.25
private const val WALLET_IN = 0.3
private const val WALLET_FROM = 0.94
private const val NOTE1_UP_AT = 1.9
private const val NOTE2_UP_AT = 2.2
private const val NOTE_UP = 0.6
private const val NOTE2_DOWN_AT = 3.5
private const val NOTE1_DOWN_AT = 3.7
private const val NOTE_DOWN = 0.5
/** The card rises out of the pocket, tilted, then lands flat on a spring as the wallet goes. */
private const val BACK_AT = 4.7
private const val RISE = 0.55
private const val RISE_FROM_Y = 10.0
private const val RISE_Y = -88.0
private const val RISE_TURN = -12.0
private const val RISE_TIP = 28.0
private const val RISE_FROM_SCALE = 0.9
private const val RISE_SCALE = 0.96
private const val LAND_RESPONSE = 0.55
private const val LAND_DAMPING = 0.8
private const val DISSOLVE_AT = 0.35
private const val DISSOLVE = 0.4
/** The sequence is over and the card at rest by then. */
internal const val SEQUENCE_END = BACK_AT + RISE + 1

// The two notes, in dp from the hero's centre: resting with a pale edge showing above the
// pocket, and how high each one rises.
private const val NOTE_REST = -24.0
private const val NOTE1_UP = -118.0
private const val NOTE2_UP = -62.0
private const val NOTE1_TURN = -3.0
private const val NOTE2_TURN = 2.0
/** The notes ripple while out: dp of wave and its period. */
private const val WAVE = 3.5
private const val WAVE_PERIOD = 1.1
private const val BLUR_REST = 0.8
private const val BLUR_MOVING = 1.8

private const val TAU_D = PI * 2

/** The hero at one moment. */
internal class WalletHero(
    val card: Float,
    /** The card's printing, which goes before the card is edge-on. */
    val face: Float,
    val cardY: Float,
    /** Degrees about the card's horizontal axis (top edge away) and in the screen's plane. */
    val tip: Float,
    val turn: Float,
    val cardScale: Float,
    val wallet: Float,
    val walletScale: Float,
    val note1Y: Float,
    val note2Y: Float,
    val note1Turn: Float,
    val note2Turn: Float,
    val note1Wave: Float,
    val note2Wave: Float,
    /** The ripple's phase, radians. */
    val phase: Float,
    val note1Blur: Float,
    val note2Blur: Float,
)

private fun clamp01(x: Double) = min(max(x, 0.0), 1.0)

private fun smooth(t: Double, from: Double, to: Double): Double {
    val x = clamp01((t - from) / (to - from))
    return x * x * (3 - 2 * x)
}

private fun easeOut(x: Double): Double {
    val k = 1 - clamp01(x)
    return 1 - k * k * k
}

private fun easeInOut(x: Double): Double {
    val k = clamp01(x)
    return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
}

/** A spring from 0 to 1 after [t] seconds, as SwiftUI's spring(response:dampingFraction:) moves. */
private fun springAt(t: Double, response: Double, damping: Double): Double {
    if (t <= 0) return 0.0
    val w0 = TAU_D / response
    val wd = w0 * sqrt(1 - damping * damping)
    return 1 - exp(-damping * w0 * t) * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
}

/** How far a note is out of the wallet, 0 to 1, and how fast it is moving (0 still, 1 fastest). */
private fun lift(q: Double, upAt: Double, downAt: Double, still: Boolean): Pair<Double, Double> {
    if (still) return (if (q >= upAt && q < downAt + NOTE_DOWN / 2) 1.0 else 0.0) to 0.0
    if (q < upAt || q >= downAt + NOTE_DOWN) return 0.0 to 0.0
    if (q < upAt + NOTE_UP) {
        val p = (q - upAt) / NOTE_UP
        return easeOut(p) to sin(PI * p)
    }
    if (q < downAt) return 1.0 to 0.0
    val p = (q - downAt) / NOTE_DOWN
    return (1 - easeInOut(p)) to sin(PI * p)
}

/** The hero [q] seconds into the sequence; reduced motion swaps the movement for cross-fades. */
internal fun heroAt(q: Double, still: Boolean): WalletHero {
    var card = 1.0
    var face = 1.0
    var cardY = 0.0
    var tip = 0.0
    var turn = 0.0
    var cardScale = 1.0
    var wallet = 0.0
    var walletScale = 1.0

    val c = q - OUT_AT
    val b = q - BACK_AT
    if (c >= 0 && b < 0) {
        if (still) {
            card = 1 - smooth(c, 0.0, 0.3)
            wallet = smooth(c, 0.0, 0.3)
        } else {
            tip = TIP * easeInOut(c / TIP_FOR)
            cardY = DROP * easeInOut(c / OUT_FOR)
            face = 1 - smooth(tip, 60.0, 88.0)
            card = 1 - smooth(c, OUT_FOR - 0.2, OUT_FOR)
            wallet = smooth(c, WALLET_IN_AT, WALLET_IN_AT + WALLET_IN)
            walletScale = WALLET_FROM + (1 - WALLET_FROM) * easeOut((c - WALLET_IN_AT) / WALLET_IN)
        }
    } else if (b >= 0) {
        if (still) {
            card = smooth(b, 0.0, 0.3)
            wallet = 1 - smooth(b, 0.0, 0.3)
        } else {
            val gone = smooth(b, DISSOLVE_AT, DISSOLVE_AT + DISSOLVE)
            wallet = 1 - gone
            walletScale = 1 - (1 - WALLET_FROM) * gone
            if (b < RISE) {
                val r = easeOut(b / RISE)
                cardY = RISE_FROM_Y + (RISE_Y - RISE_FROM_Y) * r
                turn = RISE_TURN * r
                tip = RISE_TIP * r
                cardScale = RISE_FROM_SCALE + (RISE_SCALE - RISE_FROM_SCALE) * r
            } else {
                val left = 1 - springAt(b - RISE, LAND_RESPONSE, LAND_DAMPING)
                cardY = RISE_Y * left
                turn = RISE_TURN * left
                tip = RISE_TIP * left
                cardScale = RISE_SCALE + (1 - RISE_SCALE) * (1 - left)
            }
        }
    }

    val (out1, moving1) = lift(q, NOTE1_UP_AT, NOTE1_DOWN_AT, still)
    val (out2, moving2) = lift(q, NOTE2_UP_AT, NOTE2_DOWN_AT, still)
    return WalletHero(
        card = card.toFloat(),
        face = face.toFloat(),
        cardY = cardY.toFloat(),
        tip = tip.toFloat(),
        turn = turn.toFloat(),
        cardScale = cardScale.toFloat(),
        wallet = wallet.toFloat(),
        walletScale = walletScale.toFloat(),
        note1Y = (NOTE_REST + (NOTE1_UP - NOTE_REST) * out1).toFloat(),
        note2Y = (NOTE_REST + (NOTE2_UP - NOTE_REST) * out2).toFloat(),
        note1Turn = (if (still) 0.0 else NOTE1_TURN * out1).toFloat(),
        note2Turn = (if (still) 0.0 else NOTE2_TURN * out2).toFloat(),
        note1Wave = (if (still) 0.0 else WAVE * out1).toFloat(),
        note2Wave = (if (still) 0.0 else WAVE * out2).toFloat(),
        phase = ((q - NOTE1_UP_AT) / WAVE_PERIOD * TAU_D).toFloat(),
        note1Blur = (BLUR_REST + BLUR_MOVING * moving1).toFloat(),
        note2Blur = (BLUR_REST + BLUR_MOVING * moving2).toFloat(),
    )
}
