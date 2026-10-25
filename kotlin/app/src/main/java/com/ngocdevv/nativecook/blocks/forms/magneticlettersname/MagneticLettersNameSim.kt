package com.ngocdevv.nativecook.blocks.forms.magneticlettersname

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 Magnetic Letters Name — the door under a finger (the React Native magneticLettersSim.ts). Each
 magnet keeps one flight record (kind, from, to, start, duration); its pose at any time comes from
 the curves in MagneticLettersNameMotion.kt, so a flight can be interrupted by another from
 wherever it is. Every function takes the time `at` (seconds on the frame clock). Main thread only.
*/

/** A shifted letter slides along the line in this long. */
private const val SHIFT = 0.28f

internal class MagSim(private val art: MagArt, private val reduced: Boolean) {
    private val l = art.layout
    val n = l.magnets.size
    val kind = IntArray(n) { MAG_KIND_REST }
    val from = Array(n) { l.magnets[it].home.copy() }
    val to = Array(n) { l.magnets[it].home.copy() }
    val start = FloatArray(n)
    val dur = FloatArray(n) { 1f }
    /** Where each magnet rests on the heap (moved by a drag; reset when the loop takes over). */
    val home = Array(n) { l.magnets[it].home.copy() }
    /** The poses drawn this frame. */
    val poses = Array(n) { l.magnets[it].home.copy() }
    /** The magnets on the name line, left to right. */
    val name = ArrayList<Int>()
    var held = -1
    var heldX = 0f
    var heldY = 0f
    var grabX = 0f
    var grabY = 0f
    var vx = 0f
    var ready = 0f
    var idleDrawn = false

    /** Magnet i in the loop at loop time t. */
    fun autoPose(i: Int, t: Float, out: MagPose): MagPose {
        val h = l.magnets[i].home
        val k = art.scriptOf[i]
        if (k >= 0) {
            val phase = magAutoPhase(k, t)
            val since = magAutoSince(k, t)
            if (phase == 1) return magSnapPose(h, art.autoSlots[k], since, MAG_FLY, out)
            if (phase == 2) return magLandPose(art.autoSlots[k], h, since, MAG_POP, out)
        }
        out.x = h.x; out.y = h.y; out.rot = h.rot; out.lift = 0f; out.scale = 1f
        return out
    }

    /** Magnet i under touch at time [at]; returns whether it is still moving. */
    fun simPose(i: Int, at: Float, out: MagPose): Boolean {
        val k = kind[i]
        val u = at - start[i]
        when (k) {
            MAG_KIND_HELD -> {
                val lift = if (reduced) 1f else magEaseOutCubic(u / 0.12f)
                out.x = heldX + grabX
                out.y = heldY + grabY
                out.rot = from[i].rot + max(-0.3f, min(0.3f, vx * 0.0005f))
                out.lift = max(from[i].lift, lift)
                out.scale = 1 + 0.06f * out.lift
                return true
            }
            MAG_KIND_SNAP -> magSnapPose(from[i], to[i], u, dur[i], out)
            MAG_KIND_LAND -> magLandPose(from[i], to[i], u, dur[i], out)
            MAG_KIND_DROP -> magDropPose(from[i], u, out)
            else -> {
                val p = to[i]
                out.x = p.x; out.y = p.y; out.rot = p.rot; out.lift = 0f; out.scale = 1f
                return false
            }
        }
        return u < dur[i] + 0.7f
    }

    private fun startFlight(i: Int, k: Int, target: MagPose, at: Float, d: Float) {
        from[i].set(poses[i])
        to[i].set(target).also { it.lift = 0f; it.scale = 1f }
        kind[i] = k
        start[i] = at
        dur[i] = if (reduced) 0.001f else d
    }

    private fun slotPose(k: Int): MagPose {
        var x = l.lineX0
        for (j in 0 until k) x += l.magnets[name[j]].w + l.gap
        return MagPose(x + l.magnets[name[k]].w / 2, l.letterY, 0f, 0f, 1f)
    }

    /** Slides every letter from position k on to its slot (after an insert or a removal). */
    private fun relayout(k: Int, at: Float) {
        for (j in k until name.size) {
            val m = name[j]
            if (m != held) startFlight(m, MAG_KIND_SNAP, slotPose(j), at, SHIFT)
        }
    }

    fun nameText(): String {
        val sb = StringBuilder()
        for (m in name) sb.append(l.magnets[m].letter)
        return sb.toString()
    }

    /** Whether one more magnet of width w still fits on the line. */
    private fun fits(w: Float): Boolean {
        if (name.size >= art.maxFit) return false
        var x = l.lineX0
        for (m in name) x += l.magnets[m].w + l.gap
        return x + w <= l.lineX1
    }

    fun typeMagnet(i: Int, at: Float) {
        if (!fits(l.magnets[i].w) || name.contains(i)) return
        name.add(i)
        startFlight(i, MAG_KIND_SNAP, slotPose(name.size - 1), at, MAG_FLY)
    }

    fun removeAt(k: Int, land: Boolean, at: Float) {
        val m = name.removeAt(k)
        if (land) startFlight(m, MAG_KIND_LAND, home[m], at, MAG_POP)
        relayout(k, at)
    }

    fun typeLetter(ch: Char, at: Float) {
        for (i in 0 until n) {
            if (l.magnets[i].letter == ch && !name.contains(i) && held != i) {
                typeMagnet(i, at)
                return
            }
        }
    }

    /** The topmost magnet under a point, or -1. */
    fun hit(x: Float, y: Float): Int {
        var best = -1
        var bestKey = -1f
        for (i in 0 until n) {
            val p = poses[i]
            val dx = x - p.x
            val dy = y - p.y
            val r = l.magnets[i].r + 6
            if (dx * dx + dy * dy < r * r) {
                val key = p.lift * 10 + p.y / 10000 + 1
                if (key > bestKey) { bestKey = key; best = i }
            }
        }
        return best
    }

    /** Copies the loop's state at loop time t into the record, so a touch continues from it. */
    fun takeOver(t: Float, at: Float) {
        name.clear()
        held = -1
        for (i in 0 until n) {
            val h = l.magnets[i].home
            home[i].set(h)
            kind[i] = MAG_KIND_REST
            to[i].set(h)
            val k = art.scriptOf[i]
            if (k < 0) continue
            val phase = magAutoPhase(k, t)
            val since = magAutoSince(k, t)
            if (phase == 1) {
                kind[i] = MAG_KIND_SNAP; from[i].set(h); to[i].set(art.autoSlots[k])
                start[i] = at - since; dur[i] = MAG_FLY
            } else if (phase == 2 && since < MAG_POP + 0.7f) {
                kind[i] = MAG_KIND_LAND; from[i].set(art.autoSlots[k]); to[i].set(h)
                start[i] = at - since; dur[i] = MAG_POP
            }
        }
        // The line holds the script letters that have left the heap and not yet popped, in order.
        for (k in l.script.indices) if (magAutoPhase(k, t) == 1) name.add(l.script[k])
    }

    /** Picks up magnet i under the finger at (x, y); a letter taken off the line closes the gap. */
    fun grab(i: Int, x: Float, y: Float, at: Float) {
        val k = name.indexOf(i)
        if (k >= 0) {
            name.removeAt(k)
            relayout(k, at)
        }
        val cur = poses[i]
        from[i].set(cur)
        kind[i] = MAG_KIND_HELD
        start[i] = at
        held = i
        heldX = x; heldY = y; grabX = cur.x - x; grabY = cur.y - y; vx = 0f
    }

    /** Lets go of the held magnet: onto the line where it was dropped, or back down onto the steel. */
    fun drop(at: Float): Boolean {
        val i = held
        if (i < 0) return false
        held = -1
        val p = poses[i]
        if (abs(p.y - l.letterY) < 56 && fits(l.magnets[i].w)) {
            var k = 0
            while (k < name.size && slotPose(k).x < p.x) k++
            name.add(k, i)
            startFlight(i, MAG_KIND_SNAP, slotPose(k), at, MAG_FLY * 0.8f)
            relayout(k + 1, at)
            return true
        }
        val x = min(l.w - 40, max(24f, p.x))
        val y = min(l.pile.y1, max(l.lineY + 30, p.y))
        home[i].set(MagPose(x, y, p.rot, 0f, 1f))
        from[i].set(MagPose(x, y, p.rot, p.lift, p.scale))
        to[i].set(home[i])
        kind[i] = MAG_KIND_DROP
        start[i] = at
        dur[i] = MAG_DROP
        return false
    }

    /** The line empties and every magnet away from its home flies back, staggered (done in 0.85 s). */
    fun sendHome(at: Float) {
        name.clear()
        held = -1
        var j = 0
        for (i in l.order) {
            val h = l.magnets[i].home
            val p = poses[i]
            home[i].set(h)
            val far = abs(p.x - h.x) + abs(p.y - h.y) + abs(p.rot - h.rot) * 20 + p.lift * 20 > 0.5f
            if (far) {
                startFlight(i, MAG_KIND_LAND, h, at + min(0.4f, j * 0.04f), MAG_POP)
                j++
            } else {
                kind[i] = MAG_KIND_REST
                to[i].set(h)
            }
        }
    }
}
