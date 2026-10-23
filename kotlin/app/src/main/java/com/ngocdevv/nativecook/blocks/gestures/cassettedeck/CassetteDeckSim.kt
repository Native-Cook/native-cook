package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Chassis
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Key
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Light
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Pager
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Scrub
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Transport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** JavaScript's `Math.round`: halves go up. Kotlin's `round` goes to even, which would differ on 0.5 and 2.5. */
internal fun cassetteDeckRound(x: Double): Double = floor(x + 0.5)

/**
 * The deck as one fixed-step simulation, shared by the fingers and the autoplay script (RN `deck-sim.ts`).
 * Plain arrays, mutated in place on the main thread; the drawing reads them without recomposing.
 */
internal class CassetteDeckSim {
    // Pager, in cassette widths (one screen width each).
    var p = 0.0
    var pv = 0.0
    var target = 0.0
    var dragging = false
    var dragStart = 0.0

    // Keys.
    val d = DoubleArray(KEYS)
    val dv = DoubleArray(KEYS)
    val held = IntArray(KEYS) // 1 while a finger (or the script) holds the key
    val armed = IntArray(KEYS) { 1 } // 1 while the key can click at the bottom
    val lift = IntArray(KEYS) // 1 when the finger has left but the stroke has not reached the bottom yet
    var latched = false

    // Transport and reels.
    var v = 0.0
    val w = DoubleArray(SLOTS)
    val ang = DoubleArray(SLOTS * 2)
    var k = DoubleArray(SLOTS * 2) { 1.0 }
    val prog = DoubleArray(SLOTS) { PROG0[it] }

    // Lights.
    var led = 0.0
    val lit = DoubleArray(KEYS)

    // Scrubbing a reel by hand.
    var scrubbing = false
    var scrubV = 0.0
    var tooth = 0.0

    // Chassis dip and cassette rattle (pt).
    var dip = 0.0
    var dipV = 0.0
    var rattle = 0.0
    var rattleV = 0.0
    var clock = 0.0

    // The slot that last seated in the tray (-1 while none is), and when the last scrub tick fired.
    var seated = 0
    var tickAt = -1.0

    // Reduce Motion cross-fade after a track change.
    var fade = 1.0
    var reduced = false

    val events = ArrayList<CassetteDeckHaptic>()

    /** Puts every moving part back at rest, keeping the reels' angles (the loop's seam). RN `resetToRest`. */
    fun resetToRest() {
        p = 0.0
        pv = 0.0
        target = 0.0
        dragging = false
        dragStart = 0.0
        for (i in 0 until KEYS) {
            d[i] = 0.0
            dv[i] = 0.0
            held[i] = 0
            armed[i] = 1
            lift[i] = 0
            lit[i] = 0.0
        }
        latched = false
        v = 0.0
        led = 0.0
        fade = 1.0
        scrubbing = false
        scrubV = 0.0
        dip = 0.0
        dipV = 0.0
        rattle = 0.0
        rattleV = 0.0
        clock = 0.0
        seated = 0
        tickAt = -1.0
        for (j in 0 until SLOTS) {
            w[j] = 0.0
            prog[j] = PROG0[j]
        }
        for (i in 0 until SLOTS * 2) ang[i] = ang[i] % (PI * 2.0)
    }

    /** The slot whose cassette sits in the deck, or -1 while none is close enough. */
    fun loadedSlot(): Int {
        val n = cassetteDeckRound(p)
        if (abs(p - n) > Pager.loadedWithin) return -1
        return (((n % SLOTS) + SLOTS) % SLOTS).toInt()
    }

    private fun act(i: Int) {
        if (i == 0 && !latched) {
            latched = true
            events.add(CassetteDeckHaptic.LATCH)
            return
        }
        if (i == 1 && latched) {
            latched = false
            events.add(CassetteDeckHaptic.RELEASE)
        }
        if (i == 2) {
            target = cassetteDeckRound(target) + 1.0
            if (reduced) {
                p = target
                pv = 0.0
                fade = 0.0
            }
        }
        events.add(CassetteDeckHaptic.CLICK)
    }

    /** One fixed step. `live` lets the tape packs move (the autoplay's dry run keeps them still). */
    fun stepSim(dt: Double, live: Boolean) {
        // Keys.
        for (i in 0 until KEYS) {
            if (lift[i] != 0 && armed[i] == 0) {
                held[i] = 0
                lift[i] = 0
            }
            val down = held[i] > 0
            val keyGoal = if (down) 1.0 else if (i == 0 && latched) Key.latchDepth else 0.0
            val stiff = if (down) Key.pressStiffness else Key.returnStiffness
            val damp = if (down) Key.pressDamping else Key.returnDamping
            dv[i] += (stiff * (keyGoal - d[i]) - damp * dv[i]) * dt
            d[i] += dv[i] * dt
            if (d[i] >= 1.0) {
                d[i] = 1.0
                if (dv[i] > 0.0) {
                    if (armed[i] != 0 && down) {
                        armed[i] = 0
                        act(i)
                    }
                    // The impact pushes the chassis down and shakes the cassette in its tray.
                    if (!reduced) {
                        val hit = dv[i] * CassetteDeckTheme.keys.travel
                        dipV += hit * Chassis.dipImpulse * Chassis.dipStiffness * 0.05
                        rattleV += hit * Chassis.rattleImpulse * Chassis.rattleStiffness * 0.05
                    }
                    dv[i] = -dv[i] * Key.bottomRestitution
                }
            }
            if (d[i] <= 0.0) {
                d[i] = 0.0
                if (dv[i] < 0.0) dv[i] = -dv[i] * Key.topRestitution
            }
            if (!down && d[i] < Key.rearm) armed[i] = 1
        }

        // Transport: winding while rewind / fast-forward is held at the bottom, else play speed while latched.
        val rewinding = held[3] > 0 && armed[3] == 0
        val forwarding = held[4] > 0 && armed[4] == 0
        val goal = if (forwarding) Transport.wind else if (rewinding) -Transport.wind else if (latched) 1.0 else 0.0
        val speeding = abs(goal) > abs(v) && goal * v >= 0.0
        if (scrubbing) {
            v = scrubV
        } else if (speeding) {
            v = relax(v, goal, Transport.spinUpTau, dt)
        } else {
            val dec = (Transport.brake + Transport.friction * abs(v)) * dt
            val gap = goal - v
            v += if (abs(gap) <= dec) gap else sign(gap) * dec
        }

        // Reels: the loaded cassette's hubs follow the capstan, ejected ones coast down. w = v / r.
        val loaded = loadedSlot()
        // A cassette that slides into the tray seats with a small clack: the rattle spring is kicked by its arrival speed.
        if (loaded != seated) {
            if (loaded >= 0 && !reduced) rattleV += min(1.0, abs(pv)) * Chassis.seatKick
            seated = loaded
        }
        for (j in 0 until SLOTS) {
            if (j == loaded) {
                w[j] = relax(w[j], v, Transport.engageTau, dt)
            } else {
                val dec = (Transport.brake + Transport.friction * abs(w[j])) * dt
                w[j] = if (abs(w[j]) <= dec) 0.0 else w[j] - sign(w[j]) * dec
            }
            if (reduced) continue
            val rl = tapeRadius(1.0 - prog[j])
            val rr = tapeRadius(prog[j])
            val base = w[j] * Transport.baseOmega * Transport.refRadius
            ang[j * 2] += (base / rl) * dt * k[j * 2]
            ang[j * 2 + 1] += (base / rr) * dt * k[j * 2 + 1]
            if (live) prog[j] = max(0.0, min(1.0, prog[j] + w[j] * Transport.progressRate * dt))
        }

        // Pager spring (the finger owns it while dragging).
        if (!dragging) {
            pv += (Pager.stiffness * (target - p) - Pager.damping * pv) * dt
            p += pv * dt
        }
        if (target >= SLOTS && p >= SLOTS - 1) {
            target -= SLOTS
            p -= SLOTS
            dragStart -= SLOTS
        }
        if (target <= -SLOTS && p <= 1 - SLOTS) {
            target += SLOTS
            p += SLOTS
            dragStart += SLOTS
        }

        // A tick per hub tooth while the finger turns the reel.
        if (loaded >= 0) {
            val nextTooth = floor(ang[loaded * 2] / SECTOR)
            if (nextTooth != tooth) {
                // Past 25 Hz the ticks blur into a buzz, so match RN's perceptual rate limit.
                if (scrubbing && clock - tickAt >= Scrub.tickMinGap) {
                    events.add(CassetteDeckHaptic.TICK)
                    tickAt = clock
                }
                tooth = nextTooth
            }
        }

        // Chassis dip and cassette rattle: two springs excited by key impacts.
        dipV += (-Chassis.dipStiffness * dip - Chassis.dipDamping * dipV) * dt
        dip += dipV * dt
        rattleV += (-Chassis.rattleStiffness * rattle - Chassis.rattleDamping * rattleV) * dt
        rattle += rattleV * dt
        clock += dt

        // Lights.
        led = relax(led, if (latched || abs(v) > 0.05) 1.0 else 0.0, Light.ledTau, dt)
        for (i in 0 until KEYS) {
            val on = if (i == 0) latched else d[i] > 0.5 && armed[i] == 0
            lit[i] = relax(lit[i], if (on) 1.0 else 0.0, if (on) Light.glyphOnTau else Light.glyphOffTau, dt)
        }
        fade = min(1.0, fade + dt / CassetteDeckMotion.REDUCED_FADE)
    }

    /** The finger (or the script) dragging the cassettes. `done` is the release. RN `dragTo`. */
    fun dragTo(position: Double, velocity: Double, done: Boolean) {
        if (!done) {
            if (!dragging) {
                dragging = true
                dragStart = cassetteDeckRound(p)
            }
            p = position
            pv = velocity
            return
        }
        dragging = false
        pv = velocity
        val projected = p + velocity * Pager.momentum
        val t = max(dragStart - 1.0, min(dragStart + 1.0, cassetteDeckRound(projected)))
        if (t != dragStart) events.add(CassetteDeckHaptic.COMMIT)
        target = t
        if (reduced) {
            p = t
            pv = 0.0
            if (t != dragStart) fade = 0.0
        }
    }

    /** A finger on a key. Lifting before the cap reaches the bottom finishes the stroke first, so a quick tap clicks. */
    fun pressKey(i: Int, down: Boolean) {
        if (down) {
            held[i] = 1
            lift[i] = 0
            return
        }
        if (armed[i] == 0 || d[i] >= 1.0) {
            held[i] = 0
            lift[i] = 0
        } else {
            lift[i] = 1
        }
    }

    /** The finger turning a reel: omega (rad/s) converted to tape speed at that reel's pack radius. */
    fun scrubTo(omega: Double, radius: Double, active: Boolean) {
        scrubbing = active
        val speed = (omega * radius) / (Transport.baseOmega * Transport.refRadius)
        scrubV = max(-Scrub.maxSpeed, min(Scrub.maxSpeed, speed))
        if (!active) v = scrubV
    }

    companion object {
        const val KEYS = 5
        const val SLOTS = 3

        // A hub has six teeth: it looks the same every 60 degrees.
        const val SECTOR = PI / 3.0
        private val PROG0 = doubleArrayOf(0.32, 0.58, 0.18)

        /** Pack radius for a tape fraction. */
        fun tapeRadius(prog: Double): Double {
            val pack = CassetteDeckTheme.tapePack
            return pack.min + (pack.max - pack.min) * prog
        }

        /** x of a slot in cassette widths, wrapped into [-1, 2) so three cassettes make an endless strip. */
        fun slotX(slot: Int, p: Double): Double {
            val x = (((slot - p + 1.0) % SLOTS) + SLOTS) % SLOTS
            return x - 1.0
        }

        fun relax(x: Double, to: Double, tau: Double, dt: Double): Double {
            return x + (to - x) * (1.0 - exp(-dt / tau))
        }
    }
}
