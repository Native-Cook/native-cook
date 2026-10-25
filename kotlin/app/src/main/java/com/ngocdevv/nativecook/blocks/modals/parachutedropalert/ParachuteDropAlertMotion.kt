package com.ngocdevv.nativecook.blocks.modals.parachutedropalert

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 Parachute Drop Alert — the motion, ported from parachuteMotion.ts. A frame is the alert card
 (centre, angle, squash) plus one or two canopies (position, size, tilt, how collapsed) and their
 suspension lines. The loop computes them from the timeline; under a finger the component
 simulates the card and the canopy follows.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest: the app, no alert.
   1.2 – 4.0   descent from above the screen (decelerating, 1 - (1 - u)^2.2) with a pendulum sway
               (0.17 rad at 0.85 Hz, dying out before touchdown) and a drift; the canopy breathes
               (±3 % at 2.1 Hz) and leans against the swing; the scrim fades in from 3.0.
   4.0         touchdown: the card squashes (6 %, 4 Hz, decaying in 0.1 s).
   4.0 – 4.7   the canopy deflates and drapes down over the card's top, lines going slack.
   4.6 – 5.1   the wind pulls it away to the right, lines trailing.
   5.0 – 6.6   hold: "Track" / "Later".
   6.6 – 7.0   a fresh canopy blooms out of the card's top (ease-out back).
   7.0 – 8.0   the card is lifted up and out (accelerating), the scrim fades.
   8.0 – 9.0   rest. 9.0 is the same frame as 0.0.
*/

internal const val LOOP = 9.0
internal const val PARACHUTE_LAND_AT = 4.0
internal const val PARACHUTE_LIFT_AT = 6.6
internal const val PARACHUTE_GORES = 7

internal class ParachuteGeo(
    val w: Double,
    val h: Double,
    /** The card's size and landed centre. */
    val cardW: Double,
    val cardH: Double,
    val landX: Double,
    val landY: Double,
    /** Canopy size and line length (skirt to card top). */
    val canW: Double,
    val canH: Double,
    val lines: Double,
)

/** A canopy: skirt centre, width, height, tilt (rad), collapse 0..1, attached lines, slack 0..1, alpha. */
internal class ParachuteCanopy {
    var x = 0.0
    var y = 0.0
    var w = 0.0
    var h = 0.0
    var tilt = 0.0
    var collapse = 0.0
    var attached = true
    var slack = 0.0
    var alpha = 1.0

    fun set(o: ParachuteCanopy) {
        x = o.x; y = o.y; w = o.w; h = o.h; tilt = o.tilt; collapse = o.collapse; attached = o.attached; slack = o.slack; alpha = o.alpha
    }
}

internal class ParachutePose {
    /** Card centre, angle (rad), squash x / y; visible when shown. */
    var cardX = 0.0
    var cardY = 0.0
    var cardRot = 0.0
    var sx = 1.0
    var sy = 1.0
    var shown = false
    var scrim = 0.0
    /** Reduced motion fades the whole alert instead of moving it. */
    var fade = 1.0
    val canopies = arrayOf(ParachuteCanopy(), ParachuteCanopy())
    var canopyCount = 0

    fun add(): ParachuteCanopy = canopies[canopyCount++]
}

internal object ParachuteMotion {
    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOutCubic(x: Double): Double {
        val k = clamp01(x)
        return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
    }
    fun easeOutBack(x: Double, c: Double = 1.7): Double {
        val k = clamp01(x) - 1
        return 1 + (c + 1) * k * k * k + c * k * k
    }

    /** The canopy a card of centre (x, y) and angle rot hangs from, fully open. */
    fun hangingCanopy(g: ParachuteGeo, x: Double, y: Double, rot: Double, breath: Double, scale: Double, out: ParachuteCanopy): ParachuteCanopy {
        val top = g.cardH / 2 + g.lines * scale
        out.x = x + sin(rot) * top
        out.y = y - cos(rot) * top
        out.w = g.canW * scale * (1 + 0.03 * breath)
        out.h = g.canH * scale * (1 - 0.04 * breath)
        out.tilt = rot * 0.45
        out.collapse = 0.0
        out.attached = true
        out.slack = 0.0
        out.alpha = 1.0
        return out
    }

    private val c0 = ParachuteCanopy()

    /** The collapse after touchdown, e seconds after it, for the canopy open over the landed card. */
    fun collapsing(g: ParachuteGeo, breath: Double, cardX: Double, cardY: Double, e: Double, out: ParachuteCanopy): ParachuteCanopy {
        hangingCanopy(g, g.landX, g.landY, 0.0, breath, 1.0, c0)
        val k = easeInOutCubic(seg(e, 0.0, 0.7))
        val away = easeInOutCubic(seg(e, 0.6, 1.1))
        // The hem settles a little below the card's top edge, so the cloth lies over it.
        val drapeY = cardY - g.cardH / 2 + 24
        out.x = c0.x + (cardX + g.cardW * 0.08 - c0.x) * k + g.w * 1.05 * away
        out.y = c0.y + (drapeY - c0.y) * k - 40 * away
        out.w = c0.w * (1 + 0.12 * k)
        out.h = c0.h * (1 - 0.82 * k)
        out.tilt = c0.tilt * (1 - k) + 0.32 * away
        out.collapse = k
        out.attached = away < 0.02
        out.slack = k
        out.alpha = 1.0
        return out
    }

    /** Card touchdown squash, e seconds after landing. */
    private fun landSquash(e: Double, p: ParachutePose) {
        if (e < 0 || e > 0.6) {
            p.sx = 1.0
            p.sy = 1.0
            return
        }
        val k = exp(-e / 0.1) * cos(2 * PI * 4 * e)
        p.sx = 1 + 0.035 * k
        p.sy = 1 - 0.06 * k
    }

    fun loopPose(t: Double, g: ParachuteGeo, p: ParachutePose): ParachutePose {
        p.cardX = g.landX
        p.cardY = g.landY
        p.cardRot = 0.0
        p.sx = 1.0
        p.sy = 1.0
        p.shown = false
        p.fade = 1.0
        p.canopyCount = 0
        val breath = sin(2 * PI * 2.1 * t)
        p.scrim = 0.36 * easeInOutCubic(seg(t, 3.0, 4.0)) * (1 - easeInOutCubic(seg(t, 7.0, 7.9)))
        if (t < 1.2 || t >= 8.0) return p
        p.shown = true
        val startY = -(g.cardH / 2 + g.lines + g.canH + 30)
        if (t < PARACHUTE_LAND_AT) {
            val u = (t - 1.2) / (PARACHUTE_LAND_AT - 1.2)
            val fall = 1 - (1 - u).pow(2.2)
            val env = (1 - u).pow(0.9) * seg(u, 0.0, 0.12)
            val rot = 0.17 * sin(2 * PI * 0.85 * (t - 1.2)) * env
            p.cardX = g.landX + 30 * sin(2 * PI * 0.42 * (t - 1.2)) * (1 - u).pow(1.4)
            p.cardY = startY + (g.landY - startY) * fall
            p.cardRot = rot * 0.6
            hangingCanopy(g, p.cardX, p.cardY, rot, breath, 1.0, p.add())
            return p
        }
        val e = t - PARACHUTE_LAND_AT
        landSquash(e, p)
        if (e < 1.2) collapsing(g, breath, g.landX, g.landY, e, p.add())
        if (t >= PARACHUTE_LIFT_AT) {
            val bloom = easeOutBack(seg(t, PARACHUTE_LIFT_AT, PARACHUTE_LIFT_AT + 0.4), 1.4)
            val up = seg(t, PARACHUTE_LIFT_AT + 0.4, 8.0).pow(2.2)
            val total = g.landY + g.cardH / 2 + g.lines + g.canH + 40
            p.cardY = g.landY - total * up
            val rot = 0.05 * sin(2 * PI * 1.1 * (t - PARACHUTE_LIFT_AT)) * seg(t, PARACHUTE_LIFT_AT + 0.2, 7.2)
            p.cardRot = rot * 0.6
            val c = hangingCanopy(g, p.cardX, p.cardY, rot, breath * bloom, 0.25 + 0.75 * bloom, p.add())
            c.alpha = seg(t, PARACHUTE_LIFT_AT, PARACHUTE_LIFT_AT + 0.08)
        }
        return p
    }
}
