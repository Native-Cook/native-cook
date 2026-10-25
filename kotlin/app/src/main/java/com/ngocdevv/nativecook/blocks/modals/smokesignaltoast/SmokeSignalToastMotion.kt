package com.ngocdevv.nativecook.blocks.modals.smokesignaltoast

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Smoke Signal Toast — geometry, the autoPlay script and the touch simulation, ported from
 smokeMotion.ts. LOOP = 9 s:
   0.0 – 1.3   rest: the fire crackles, a faint wisp rises.
   1.3 – 2.2   the fire flares and puffs a cloud (flare 1 → 1.35 → 1).
   2.2 – 3.8   the cloud rises to the middle of the sky (ease-in-out, 1.6 – 3.1) and condenses
               into the message: blurred words appear inside it (2.8 – 3.3), their scatter shrinks
               50 → 0 dp and their offset from below 50 → 0 dp (ease-in-out, 2.9 – 3.85) while
               the cloud thins away (3.0 – 3.7).
   3.8 – 5.6   readable hold.
   5.6 – 7.2   a gust from the left tears it to the right and it thins out (ease-in-out).
   7.2 – 9.0   the fire calms (flare dips to 0.85 and returns) and rests.
*/

internal const val LOOP = 9f
private const val REDUCED_AT = 4.6f
private const val IDLE_BEFORE_BACK = 2f
private const val BACK_FOR = 0.9f
private const val MODE_AUTO = 0
internal const val SMOKE_MODE_LIVE = 1
private const val MODE_BACK = 2

internal const val SMOKE_HEADER_H = 60f
internal const val SMOKE_INPUT_H = 52f

internal class SmokeGeo(
    val w: Float,
    val h: Float,
    val fireX: Float,
    val fireY: Float,
    val fireS: Float,
    val treeY: Float,
    val toastY: Float,
    val inputTop: Float,
)

internal fun makeSmokeGeo(w: Float, h: Float, insetTop: Float, insetBottom: Float): SmokeGeo {
    val inputTop = h - max(insetBottom, 12f) - 8f - SMOKE_INPUT_H
    val fireS = min(1f, h / 852f)
    val fireY = inputTop - 40f * fireS
    val treeY = fireY - 70f * fireS
    val msgBottom = insetTop + SMOKE_HEADER_H + 200f
    val toastY = max(msgBottom + 70f, min((msgBottom + treeY - 110f) / 2f + 20f, treeY - 170f))
    return SmokeGeo(w, h, w / 2f, fireY, fireS, treeY, toastY, inputTop)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

internal class SmokeFrame {
    var flare = 1f
    /** Puff: x, y, radius, density. */
    val puff = floatArrayOf(0f, 0f, 1f, 0f)
    /** Formed, scatter, rise, alpha. */
    val text = floatArrayOf(0f, 50f, 50f, 0f)
    /** Wind direction and amount. */
    val wind = floatArrayOf(1f, 0f, 0f, 0f)
    var phase = 0f
}

private fun scriptFrame(t: Float, g: SmokeGeo, f: SmokeFrame) {
    val pi = PI.toFloat()
    f.phase = t / LOOP
    // The flare when the puff leaves, the calm at the end.
    f.flare = 1f + 0.35f * sin(pi * seg(t, 1.3f, 2.2f)) - 0.15f * sin(pi * seg(t, 7.2f, 8.6f))
    // The puff: born at the fire, rising to the toast's line.
    val born = easeOut(seg(t, 1.3f, 1.8f))
    val up = easeInOut(seg(t, 1.6f, 3.1f))
    f.puff[0] = g.fireX + sin(up * pi) * 18f
    f.puff[1] = g.fireY - 40f * g.fireS - (g.fireY - 40f * g.fireS - g.toastY) * up
    f.puff[2] = 22f + 110f * up
    f.puff[3] = born * (1f - easeInOut(seg(t, 3.0f, 3.7f))) * 0.9f
    // The message condenses out of it.
    val form = easeInOut(seg(t, 2.9f, 3.85f))
    f.text[0] = form
    f.text[1] = 50f * (1f - form)
    f.text[2] = 50f * (1f - form)
    f.text[3] = easeOut(seg(t, 2.8f, 3.3f))
    f.wind[0] = 1f
    f.wind[1] = -0.18f
    f.wind[2] = easeInOut(seg(t, 5.6f, 7.2f))
    if (t >= 7.2f) f.text[3] = 0f
}

internal class SmokeSim {
    var mode = MODE_AUTO
        private set
    private var t = 0f
    private var now = 0f
    private var last = 0f
    private var back = 0f
    val frame = SmokeFrame()
    /** Swipe: whether a finger drags the message, how far, and the release animation. */
    var dragging = false
        private set
    private var blow = 0f
    private var blowV = 0f
    private var blowTarget = 0f
    /** Whether the message card is open. */
    var open = false
        private set
    private val from = FloatArray(5)

    /** Whether the frame loop has nothing to do: at rest outside autoPlay. */
    fun idle(auto: Boolean) = mode == MODE_AUTO && !auto

    private fun goLive() {
        if (mode == SMOKE_MODE_LIVE) return
        mode = SMOKE_MODE_LIVE
        blow = frame.wind[2]
        blowTarget = if (blow > 0.5f) 1f else blow
        blowV = 0f
        frame.puff[3] = 0f
    }

    fun touch() {
        goLive()
        last = now
    }

    /** Whether the message is up and readable enough to be tapped open or blown away. */
    fun readable(): Boolean {
        val f = frame
        return f.text[3] > 0.5f && f.text[0] > 0.6f && f.wind[2] < 0.5f
    }

    /** A swipe drags the wind: its direction and how far it has gone blow the words apart. */
    fun drag(dx: Float, dy: Float) {
        goLive()
        dragging = true
        last = now
        val d = hypot(dx, dy)
        if (d > 4f) {
            frame.wind[0] = dx / d
            frame.wind[1] = dy / d
        }
        blow = min(1f, d / 260f)
    }

    fun dragEnd(vx: Float, vy: Float) {
        dragging = false
        last = now
        val fling = hypot(vx, vy)
        blowTarget = if (blow > 0.32f || fling > 650f) 1f else 0f
        blowV = min(4f, fling / 400f)
    }

    fun openCard(open: Boolean) {
        goLive()
        this.open = open
        last = now
        if (open) {
            // The words drift up out of the way as the card takes their place.
            frame.wind[0] = 0.15f
            frame.wind[1] = -1f
            blowTarget = 1f
            blowV = 1.2f
        }
    }

    /** One frame of the simulation. */
    fun step(dt: Float, g: SmokeGeo, auto: Boolean, reduced: Boolean) {
        now += dt
        val f = frame
        if (mode == MODE_AUTO) {
            if (auto) t = (t + dt) % LOOP
            scriptFrame(if (reduced) REDUCED_AT else t, g, f)
            return
        }
        f.phase += dt / LOOP
        if (mode == SMOKE_MODE_LIVE) {
            f.flare += (1f - f.flare) * min(1f, dt * 3f)
            if (!dragging) {
                // Released: a spring to blown away (1) or back together (0), carrying the fling.
                val w = 9f
                val a = w * w * (blowTarget - blow) - 2f * 0.9f * w * blowV
                blowV += a * dt
                blow = (blow + blowV * dt).coerceIn(0f, 1f)
            }
            f.wind[2] = blow
            if (auto && !dragging && now - last > IDLE_BEFORE_BACK) {
                mode = MODE_BACK
                back = 0f
                from[0] = f.text[3]
                from[1] = f.wind[2]
                from[2] = f.phase
                from[3] = f.flare
                open = false
            }
            return
        }
        back = min(1f, back + dt / BACK_FOR)
        val k = easeInOut(back)
        f.text[3] = from[0] * (1f - k)
        f.wind[2] = from[1] + (1f - from[1]) * k * 0.5f
        f.phase = from[2] + (ceil(from[2]) - from[2]) * k
        f.flare = from[3] + (1f - from[3]) * k
        if (back >= 1f) {
            mode = MODE_AUTO
            t = 0f
            blow = 0f
            open = false
            scriptFrame(0f, g, f)
        }
    }
}
