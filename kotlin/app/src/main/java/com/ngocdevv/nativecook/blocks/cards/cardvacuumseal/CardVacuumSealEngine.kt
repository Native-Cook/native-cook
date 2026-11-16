package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealMotion as M
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cues the host turns into haptics and sound (touch only; the loop is silent). */
internal object CardVacuumSealBeat {
    const val ONSET = 0 // finger lands on the Protect tile: light
    const val DETENT = 1 // p crosses 0.45 / 0.6 / 0.75 while held (a new crease forms): selection
    const val RIGID = 2 // ... and 0.9: rigid
    const val LOCK = 3 // the film locks: medium + a 70 ms thump
    const val AIR_BACK = 4 // the air returns: two soft taps 60 ms apart + an inhale
    const val DIMPLE = 5 // a finger lands on the sealed film: soft
}

/**
 * The film's controller (RN `useVacuumSeal`): one state value `p`, the autoplay loop, the finger on the shield tile (hold), the finger on the film
 * (squeegee, dimple, peel), haptics, sound, the crease seed and amplitude, and the `sealed` state. All on the main thread.
 *
 *   mode 0  the loop plays: `look` = lookAt(loop time), a pure function of time
 *   mode 1  a finger (or nothing) moves p
 * A touch takes over from the loop's current look without a jump; 2 s after the last touch the film glides to rest and the loop restarts.
 */
internal class CardVacuumSealEngine(val autoPlay: Boolean, val reduce: Boolean, val holdW: Double, val holdH: Double) {
    private class Touch(
        val down: Boolean = false,
        val kind: Int = 0, // 0 holds the shield tile, 1 squeegees the film, 2 dents the sealed film
        val onTile: Boolean = false,
        val t0: Double = 0.0,
        val p0: Double = 1.0,
        val dir: Int = -1,
        val off: Double = 0.0,
        val ready: Boolean = false,
    )

    class Geom(val s: Double, val hw: Double, val hh: Double)

    var cue: (Int) -> Unit = {}
    /** the air hiss: a playback rate 0.8 .. 1.4, or null to stop it */
    var air: (Double?) -> Unit = {}
    var onSealedChange: ((Boolean) -> Unit)? = null
    var geom = Geom(1.0, 177.0, 111.5)

    private val initialP = 1.0
    var look: CardVacuumSealLook = M.REST.copy(p = if (reduce) 1.0 else initialP)
        private set
    private var loopT = 0.0
    var ambient = 0.0
        private set
    private var ambOff = 0.0
    private var mode = if (autoPlay && !reduce) 0 else 1
    val pLive = CardVacuumSealAnim(initialP)
    val press = CardVacuumSealAnim(0.0)
    val flash = CardVacuumSealAnim(0.0)
    val settle = CardVacuumSealAnim(1.0)
    val glint = CardVacuumSealAnim(0.0)
    val discGlint = CardVacuumSealAnim(0.0)
    val tug = CardVacuumSealAnim(0.0)
    val tugSide = CardVacuumSealAnim(0.0)
    val dimpleK = CardVacuumSealAnim(0.0)
    val fingerK = CardVacuumSealAnim(0.0)
    val stretch = CardVacuumSealAnim(0.0)
    val fade = CardVacuumSealAnim(if (reduce) initialP else 1.0)
    val labelK = CardVacuumSealAnim(1.0)
    private var labelOn = true
    private var target = initialP
    private var touch = Touch()
    private var lastTouch = -1e9
    private var resuming = false
    private var lastP = initialP
    private var lockArmed = false
    var dimpleX = 0.0
        private set
    var dimpleY = 0.0
        private set
    var fingerX = 0.0
        private set
    var fingerY = 0.0
        private set
    private var sqTarget = initialP
    private var velocity = 0.0
    private var velocity1 = 0.0
    private var velocity2 = 0.0
    private var holdActive = false
    private var userDriven = false
    private var peakSpeed = 0.0
    var seed = 0
        private set
    var amp = 1.0
        private set
    var extra = 0.0
        private set
    private var lastAir = -1.0
    private var notified = true
    var sealed = true
        private set

    private fun stepAnims(dt: Double) {
        pLive.step(dt)
        press.step(dt)
        flash.step(dt)
        settle.step(dt)
        glint.step(dt)
        discGlint.step(dt)
        tug.step(dt)
        tugSide.step(dt)
        dimpleK.step(dt)
        fingerK.step(dt)
        stretch.step(dt)
        fade.step(dt)
        labelK.step(dt)
    }

    /** Whether anything animated is still moving. */
    val animating: Boolean
        get() = pLive.running || press.running || flash.running || settle.running || glint.running || discGlint.running || tug.running ||
            tugSide.running || dimpleK.running || fingerK.running || stretch.running || fade.running || labelK.running

    private fun announce(on: Boolean) {
        if (notified != on) {
            notified = on
            sealed = on
            onSealedChange?.invoke(on)
        }
    }

    /** the label follows the film's state, never its progress (held half-way through a pause, it read as two labels) */
    private fun syncLabel(p: Double) {
        val ease = { x: Double -> M.bezier(0.65, 0.0, 0.35, 1.0, x) }
        if (!labelOn && p >= M.LABEL_ON) {
            labelOn = true
            labelK.run(AnimStep.Timing(1.0, M.LABEL_MS / 1000, ease))
        } else if (labelOn && p < M.LABEL_OFF) {
            labelOn = false
            labelK.run(AnimStep.Timing(0.0, M.LABEL_MS / 1000, ease))
        }
    }

    /** One frame; returns true when the picture changed. */
    fun frame(rawDt: Double): Boolean {
        val dt = min(rawDt, 0.05)
        stepAnims(dt)
        val now = ambient + dt
        // Reduce Motion: no clock, no film motion; the film is always drawn as the sealed look and `fade` shows or hides it
        if (reduce) return animating
        if (mode == 0) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            ambient = t + ambOff
            val l = M.lookAt(t)
            look = l
            pLive.set(l.p)
            val lk = M.labelAt(t)
            labelK.set(lk)
            labelOn = lk > 0.5
            return true
        }
        // the finger's state
        val tc = touch
        var p = pLive.value
        val prev = lastP
        // nothing moves and no loop plays: the canvas stays still
        val moving = tc.down || resuming || abs(p - prev) > 0.00005 || abs(settle.value - 1) > 0.001 || glint.value > 0.001 || discGlint.value > 0.001 ||
            abs(press.value) > 0.001 || flash.value > 0.001 || abs(tug.value) > 0.001 || abs(tugSide.value) > 0.001 || abs(dimpleK.value) > 0.001 ||
            fingerK.value > 0.001 || abs(stretch.value) > 0.001
        if (!moving && !autoPlay) {
            if (lastAir > 0) {
                lastAir = -1.0
                air(null)
            }
            return animating
        }
        ambient = now
        if (tc.down) {
            if (tc.kind == 0 && tc.onTile) {
                val rate = if (tc.dir > 0) 1000 / M.SEAL_MS else 1000 / M.UNSEAL_MS
                p = min(1.0, max(0.0, p + tc.dir * rate * dt))
                pLive.set(p)
            } else if (tc.kind == 1) {
                if (!tc.ready) {
                    // Anchor at the completed drop; subsequent pointer travel is exactly 1:1 with no catch-up lag.
                    if (p >= M.SQ_BASE - 0.005) {
                        val g = geom
                        val off = p - M.fingerP(fingerX - g.hw, fingerY - g.hh, g.hw, g.hh)
                        touch = Touch(true, tc.kind, tc.onTile, tc.t0, tc.p0, tc.dir, off, true)
                        sqTarget = p
                    }
                } else {
                    p = min(1.0, max(M.SQ_BASE, sqTarget))
                    pLive.set(p)
                }
            }
        }
        // the speed the air is pressed out at (p/s), and what it leaves in the creases
        val v = if (dt > 0) (p - prev) / dt else 0.0
        velocity = (v + velocity1 + velocity2) / 3
        velocity2 = velocity1
        velocity1 = v
        if (tc.down && tc.kind != 2 && p > M.SQ_BASE && p < 0.95) {
            val sp = abs(velocity)
            if (sp > peakSpeed) {
                peakSpeed = sp
                amp = M.creaseAmpOf(sp)
                extra = if (sp > M.SQ_FLICK) 1.0 else 0.0
            }
        }
        // haptics and sound at the creases, the lock and the sheet leaving
        if (tc.down && tc.kind != 2) {
            for (i in M.TICKS.indices) {
                val k = M.TICKS[i]
                if ((prev < k && p >= k) || (prev > k && p <= k)) cue(if (i == M.TICKS.size - 1) CardVacuumSealBeat.RIGID else CardVacuumSealBeat.DETENT)
            }
            if (p > 0.02 && p < 0.985 && abs(velocity) > 0.05) {
                if (now - lastAir > 0.07) {
                    lastAir = now
                    air(0.8 + (1.4 - 0.8) * min(1.0, max(0.0, p)))
                }
            } else if (lastAir > 0) {
                lastAir = -1.0
                air(null)
            }
        } else if (lastAir > 0) {
            lastAir = -1.0
            air(null)
        }
        syncLabel(p)
        if (p < 0.9) lockArmed = true
        if (lockArmed && p >= 0.999) {
            lockArmed = false
            if (userDriven) cue(CardVacuumSealBeat.LOCK)
            announce(true)
            val boost = 1 + 0.6 * extra
            settle.run(AnimStep.Timing(1.03, 0.06), AnimStep.Spring(1.0, CardVacuumSealSpring(12.0, 120.0, 1.0)))
            glint.run(AnimStep.Timing(boost, 0.07), AnimStep.Timing(0.0, 0.26))
            discGlint.run(AnimStep.Delay(0.06), AnimStep.Timing(1.0, 0.04), AnimStep.Timing(0.0, 0.08))
            tug.run(AnimStep.Timing(1.0, 0.07), AnimStep.Spring(0.0, M.SPRING_TUG))
            tugSide.run(AnimStep.Delay(0.04), AnimStep.Timing(1.0, 0.07), AnimStep.Spring(0.0, M.SPRING_TUG))
        }
        if (prev > 0.001 && p <= 0.001) {
            if (userDriven) cue(CardVacuumSealBeat.AIR_BACK)
            announce(false)
            // a new crease layout and a fresh amplitude for the next seal
            peakSpeed = 0.0
            amp = 1.0
            extra = 0.0
            seed = (seed + 1) % 3
        }
        lastP = p
        look = CardVacuumSealLook(
            p, press.value, flash.value, 0.0, settle.value, glint.value, -1.0, 0.0, tug.value, tugSide.value, discGlint.value,
        )
        // the loop comes back 2 s after the last touch: the film eases to rest, then the timeline restarts at 0
        if (autoPlay && !tc.down && now - lastTouch > M.RESUME_AFTER && dimpleK.value < 0.01 && fingerK.value < 0.01) {
            if (!resuming) {
                resuming = true
                userDriven = false
                target = 1.0
                if (p < 0.999) pLive.run(AnimStep.Spring(1.0, M.SPRING_GLIDE))
            } else if (abs(p - 1) < 0.002) {
                resuming = false
                loopT = 0.0
                ambOff = now % M.LOOP
                seed = 0
                mode = 0
            }
        }
        return true
    }

    // ---- touches ----

    private fun takeOver() {
        userDriven = true
        velocity1 = 0.0
        velocity2 = 0.0
        velocity = 0.0
        if (mode == 0) {
            val l = look
            pLive.set(l.p)
            lastP = l.p
            target = if (l.p >= 0.5) 1.0 else 0.0
            lockArmed = l.p < 0.9
            mode = 1
        }
        resuming = false
    }

    private fun toggleReduced() {
        val next = if (target > 0.5) 0.0 else 1.0
        target = next
        pLive.set(next)
        fade.run(AnimStep.Timing(next, 0.2))
        syncLabel(next)
        cue(if (next == 1.0) CardVacuumSealBeat.LOCK else CardVacuumSealBeat.AIR_BACK)
        announce(next == 1.0)
    }

    fun startHold() {
        holdActive = true
        takeOver()
        press.run(AnimStep.Spring(1.0, M.SPRING_PRESS))
        flash.run(AnimStep.Timing(1.0, 0.09), AnimStep.Timing(0.0, 0.32))
        cue(CardVacuumSealBeat.ONSET)
        if (reduce) return
        val p = pLive.value
        touch = Touch(true, 0, true, ambient, p, if (target > 0.5) -1 else 1, 0.0, true)
        pLive.set(p) // stop any running spring where it is
    }

    fun updateHold(x: Double, y: Double) {
        val tc = touch
        if (!tc.down || tc.kind != 0) return
        val slop = 16.0
        val on = x > -slop && y > -slop && x < holdW + slop && y < holdH + slop
        if (on != tc.onTile) {
            touch = Touch(tc.down, tc.kind, on, tc.t0, tc.p0, tc.dir, tc.off, tc.ready)
            press.run(AnimStep.Spring(if (on) 1.0 else 0.45, M.SPRING_PRESS))
        }
    }

    fun finishHold() {
        if (!holdActive) return
        holdActive = false
        press.run(AnimStep.Spring(0.0, M.SPRING_PRESS))
        lastTouch = ambient
        if (reduce) {
            toggleReduced()
            return
        }
        val tc = touch
        if (!tc.down || tc.kind != 0) return
        touch = Touch(false, tc.kind, tc.onTile, tc.t0, tc.p0, tc.dir, tc.off, tc.ready)
        val p = pLive.value
        val dur = ambient - tc.t0
        val sealing = tc.dir > 0
        if (dur < M.TAP_MAX && abs(p - tc.p0) < 0.2) {
            // a tap runs the whole change by itself, at the hold's pace
            val next = if (sealing) 1.0 else 0.0
            target = next
            val ms = abs(next - p) * (if (sealing) M.SEAL_MS else M.UNSEAL_MS)
            pLive.run(AnimStep.Timing(next, ms / 1000, { x -> M.bezier(0.4, 0.0, 0.3, 1.0, x) }))
            return
        }
        val rate = if (sealing) 1000 / M.SEAL_MS else 1000 / M.UNSEAL_MS
        val commit = if (sealing) p >= M.COMMIT_AT else p < M.COMMIT_AT
        val next = if (commit == sealing) 1.0 else 0.0
        target = next
        if (next == 1.0) pLive.run(AnimStep.Spring(1.0, M.SPRING_COMPLETE, if (sealing) rate else 0.0))
        else pLive.run(AnimStep.Spring(0.0, M.SPRING_REVERT, if (sealing) 0.0 else -rate))
    }

    /** The finger landed on the film; (x, y) in dp from the film's (the card's) top-left corner. */
    fun squeegeeBegin(x: Double, y: Double) {
        if (reduce) return
        takeOver()
        val g = geom
        val qx = x / g.s - g.hw
        val qy = y / g.s - g.hh
        val p0 = pLive.value
        lastTouch = ambient
        if (p0 >= 0.95) {
            val nearEdge = y / g.s > 2 * g.hh - M.SQ_EDGE || x / g.s > 2 * g.hw - M.SQ_EDGE
            if (!nearEdge) {
                touch = Touch(down = true, kind = 2)
                dimpleX = g.hw + qx
                dimpleY = g.hh + qy
                dimpleK.run(AnimStep.Spring(1.0, M.SPRING_DIMPLE))
                cue(CardVacuumSealBeat.DIMPLE)
                return
            }
            // peel: the same map, reversed, continuing from where the film is now
            val off = 1 - M.fingerP(qx, qy, g.hw, g.hh)
            sqTarget = 1.0
            touch = Touch(down = true, kind = 1, p0 = p0, off = off, ready = true, t0 = ambient)
            fingerX = g.hw + qx
            fingerY = g.hh + qy
            fingerK.run(AnimStep.Timing(1.0, 0.09))
            cue(CardVacuumSealBeat.ONSET)
            return
        }
        peakSpeed = 0.0
        amp = 1.0
        extra = 0.0
        val ready = p0 >= M.SQ_BASE - 0.005
        if (!ready) pLive.run(AnimStep.Spring(M.SQ_BASE, M.SPRING_DROP))
        target = 1.0
        val mapped = M.fingerP(qx, qy, g.hw, g.hh)
        val off = if (ready) p0 - mapped else 0.0
        touch = Touch(down = true, kind = 1, p0 = p0, off = off, ready = ready, t0 = ambient)
        sqTarget = if (ready) p0 else mapped
        fingerX = g.hw + qx
        fingerY = g.hh + qy
        fingerK.run(AnimStep.Timing(1.0, 0.09))
        cue(CardVacuumSealBeat.ONSET)
    }

    fun squeegeeMove(x: Double, y: Double) {
        val tc = touch
        if (!tc.down) return
        val g = geom
        val qx = x / g.s - g.hw
        val qy = y / g.s - g.hh
        if (tc.kind == 2) {
            dimpleX = g.hw + qx
            dimpleY = g.hh + qy
            return
        }
        if (tc.kind != 1) return
        val mapped = min(1.0, max(M.SQ_BASE, M.fingerP(qx, qy, g.hw, g.hh) + tc.off))
        sqTarget = mapped
        if (tc.ready) pLive.set(mapped)
        fingerX = g.hw + min(qx, g.hw)
        fingerY = g.hh + min(qy, g.hh)
        // pulled past the bottom edge: the lip stretches with the finger
        stretch.set(min(M.SQ_STRETCH_MAX, max(0.0, qy - g.hh) * M.SQ_STRETCH))
    }

    fun squeegeeEnd() {
        lastTouch = ambient
        val tc = touch
        if (!tc.down) return
        touch = Touch(false, tc.kind, tc.onTile, tc.t0, tc.p0, tc.dir, tc.off, tc.ready)
        if (tc.kind == 2) {
            dimpleK.run(AnimStep.Spring(0.0, M.SPRING_DIMPLE))
            return
        }
        if (tc.kind != 1) return
        fingerK.run(AnimStep.Timing(0.0, 0.16))
        stretch.run(AnimStep.Spring(0.0, M.SPRING_TUG))
        val p = pLive.value
        val vp = velocity
        // the release: where the air is heading decides whether the film seals or lets go
        val next = if (p + M.SQ_COMMIT_LEAD * vp >= M.COMMIT_AT) 1.0 else 0.0
        target = next
        if (next == 1.0) pLive.run(AnimStep.Spring(1.0, M.SPRING_COMPLETE, vp)) else pLive.run(AnimStep.Spring(0.0, M.SPRING_REVERT, vp))
    }

    /** TalkBack: toggle the seal at once (the reduced sequence). */
    fun toggle(next: Boolean) {
        target = if (next) 1.0 else 0.0
        if (reduce) {
            pLive.set(if (next) 1.0 else 0.0)
            fade.run(AnimStep.Timing(if (next) 1.0 else 0.0, 0.2))
            syncLabel(if (next) 1.0 else 0.0)
        } else {
            mode = 1
            pLive.run(AnimStep.Timing(if (next) 1.0 else 0.0, 0.22))
        }
        lastTouch = ambient
        announce(next)
    }
}
