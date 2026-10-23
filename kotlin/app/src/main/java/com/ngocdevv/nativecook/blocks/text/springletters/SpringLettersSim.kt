package com.ngocdevv.nativecook.blocks.text.springletters

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/*
 * The letters' physics, ported step for step from the React Native `SpringLettersSim.ts`. It knows
 * nothing of the font: each letter is just a box. The composable calls `step` once a frame and
 * draws what it finds.
 *
 * Every letter is a rigid body (position, angle and their speeds) under gravity, standing on a
 * floor. A soft spring pulls it back over its slot and another stands it upright; springs between
 * the facing edges of neighbours chain the word together, so a letter that is dragged tugs the
 * ones beside it and they swing on the links. Contact with the floor is a corner hitting a plane:
 * an impulse with bounce and friction, which also tips the letter.
 */

internal const val MAX_LETTERS = 8
internal val Words = arrayOf("BOUNCE", "SPRING", "WOBBLE", "JIGGLE")

// Gravity (dp/s²).
private const val GRAVITY = 2600.0
// The spring over a letter's slot: across (stiffness, damping) and, gently, up and down.
private const val HOME_X = 60.0
private const val HOME_X_DAMPING = 4.6
private const val HOME_Y = 10.0
private const val AIR = 0.5
// Standing upright: angular stiffness and damping (1/s², 1/s).
private const val UPRIGHT = 150.0
private const val UPRIGHT_DAMPING = 8.0
// Links between neighbours, edge to edge.
private const val LINK = 75.0
private const val LINK_DAMPING = 5.0
// The finger's hold on a letter: stiff enough to follow, soft enough to lag and swing.
private const val GRAB = 330.0
// A touch this far (dp) outside every letter still picks up the nearest one.
private const val GRAB_REACH = 26.0
private const val GRAB_DAMPING = 20.0
// On release, the letter takes this share of the finger's speed; speeds are capped.
private const val THROW_SHARE = 0.65
private const val MAX_SPEED = 2800.0
// Floor: bounce for a hard landing, none for a soft one, and friction.
private const val BOUNCE = 0.42
private const val BOUNCE_BELOW = 140.0
private const val FRICTION = 0.5
// Corners are rounded by this share of the letter's smaller half-size where it meets the floor.
private const val ROUNDING = 0.6
// A flick up from the floor throws every letter, the nearest first, the wave crossing at this speed (dp/s).
private const val FLICK_MIN = 450.0
private const val FLICK_WAVE = 1300.0
private const val FLICK_POWER = 0.7
private const val FLICK_MAX = 1750.0
// A word change: old letters shrink and turn away over LEAVE_TIME, new ones drop in one by one.
internal const val LEAVE_TIME = 0.45
private const val LEAVE_STAGGER = 0.035
private const val DROP_DELAY = 0.12
private const val DROP_STAGGER = 0.075
private const val DROP_FROM = 140.0

private const val DT_CAP = 1.0 / 30
private const val SUBSTEPS = 6

// autoPlay: drag the O up and fling it, flick the floor, change word, drag the G and throw it,
// change back, rest.
private const val LOOP = 12.0
// [start, end, letter, release vx, release vy, then (dx, dy) pairs the finger passes through]
private val Drags = arrayOf(
    doubleArrayOf(0.6, 1.75, 1.0, 1100.0, -900.0, 0.0, 0.0, 30.0, -90.0, 70.0, -190.0, 90.0, -230.0),
    doubleArrayOf(6.2, 7.35, 5.0, -1000.0, -700.0, 0.0, 0.0, -30.0, -80.0, -90.0, -170.0, -130.0, -210.0),
)
private const val FLICK_AT = 3.2
// [time, word]
private val Swaps = arrayOf(doubleArrayOf(4.7, 1.0), doubleArrayOf(8.5, 0.0))
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 3.5

private fun easeInOut(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/** Did the loop clock pass [at] going from [before] to [now] (wrapping at LOOP)? */
private fun crossed(before: Double, now: Double, at: Double) =
    if (now >= before) before < at && now >= at else before < at || now >= at

/** The letters' state: the same fields as the RN `LettersSim`, mutated in place once a frame. */
internal class LettersSim(var looping: Boolean, var still: Boolean) {
    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    var seed = 7
    // Screen: where the floor is, the side walls and the ceiling.
    var floor = 0.0
    var left = 0.0
    var right = 0.0
    var ceiling = 0.0
    // The word on the floor.
    var word = 0
    var count = 0
    val x = DoubleArray(MAX_LETTERS)
    val y = DoubleArray(MAX_LETTERS)
    val a = DoubleArray(MAX_LETTERS)
    val vx = DoubleArray(MAX_LETTERS)
    val vy = DoubleArray(MAX_LETTERS)
    val va = DoubleArray(MAX_LETTERS)
    val restX = DoubleArray(MAX_LETTERS)
    val restY = DoubleArray(MAX_LETTERS)
    val halfW = DoubleArray(MAX_LETTERS)
    val halfH = DoubleArray(MAX_LETTERS)
    // Seconds before each new letter drops in (it is hidden until then); 0 once it has.
    val wait = DoubleArray(MAX_LETTERS)
    // Seconds since the word was placed; with reduced motion the new word fades in over this.
    var arrive = 0.0
    // A pending kick from a floor flick: its strength and the seconds until it arrives.
    val kick = DoubleArray(MAX_LETTERS)
    val kickIn = DoubleArray(MAX_LETTERS) { -1.0 }
    // The word leaving: its letters' poses and how far through leaving they are.
    var leftWord = -1
    var leftCount = 0
    val lx = DoubleArray(MAX_LETTERS)
    val ly = DoubleArray(MAX_LETTERS)
    val la = DoubleArray(MAX_LETTERS)
    var leaveAge = -1.0
    // The finger: down, where, how fast, and which letter it holds (−1 none) at what local point.
    var down = false
    var scripted = false
    var fx = 0.0
    var fy = 0.0
    var fvx = 0.0
    var fvy = 0.0
    var held = -1
    var gx = 0.0
    var gy = 0.0
    // Where the scripted finger came down, which its path is measured from.
    var dragX = 0.0
    var dragY = 0.0
    // Things a real finger did, counted; the composable answers each with a haptic.
    var userGrabs = 0
    var userFlicks = 0
    var userSwaps = 0
    // The last double tap, drawn as a ripple (seconds since, or −1).
    var tapAge = -1.0
    var tapX = 0.0
    var tapY = 0.0

    /** A seeded random number in [0, 1) (mulberry32, in 32-bit integers like JavaScript), so every loop plays the same. */
    fun random(): Double {
        seed += 0x6d2b79f5
        var t = seed
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }

    /**
     * Lays a word out on the floor. [boxes] holds each letter's width and height (dp) for every
     * word, flat: boxes[word][2i], boxes[word][2i + 1]. With [drop], the letters fall in from
     * above; otherwise they stand in their slots at once.
     */
    fun placeWord(word: Int, boxes: List<DoubleArray>, gap: Double, drop: Boolean) {
        if (word !in boxes.indices) return
        val sizes = boxes[word]
        val n = min(MAX_LETTERS, sizes.size / 2)
        var total = gap * (n - 1)
        for (i in 0 until n) total += max(2.0, sizes[i * 2])
        var px = (left + right) / 2 - total / 2
        this.word = word
        count = n
        arrive = if (drop) 0.0 else 1.0
        for (i in 0 until n) {
            // Never a zero box (a glyph that failed to load): the physics divides by its size.
            val hw = max(1.0, sizes[i * 2] / 2)
            val hh = max(1.0, sizes[i * 2 + 1] / 2)
            halfW[i] = hw
            halfH[i] = hh
            restX[i] = px + hw
            restY[i] = floor - hh
            px += hw * 2 + gap
            kickIn[i] = -1.0
            if (drop && !still) {
                wait[i] = DROP_DELAY + i * DROP_STAGGER
                x[i] = restX[i] + (random() - 0.5) * 30
                y[i] = ceiling - DROP_FROM - hh
                a[i] = (random() - 0.5) * 0.9
                vx[i] = 0.0
                vy[i] = 0.0
                va[i] = (random() - 0.5) * 6
            } else {
                wait[i] = 0.0
                x[i] = restX[i]
                y[i] = restY[i]
                a[i] = 0.0
                vx[i] = 0.0
                vy[i] = 0.0
                va[i] = 0.0
            }
        }
    }

    /** Swaps to word [word]: the letters on the floor leave, the new ones drop in. */
    fun swapWord(word: Int, boxes: List<DoubleArray>, gap: Double, user: Boolean) {
        leftWord = this.word
        leftCount = count
        for (i in 0 until count) {
            lx[i] = x[i]
            ly[i] = y[i]
            la[i] = a[i]
        }
        leaveAge = 0.0
        held = -1
        placeWord(word, boxes, gap, true)
        if (user) {
            userSwaps += 1
            hold = USER_HOLD
        }
    }

    /** The letter under a point, topmost first, or −1. */
    private fun letterAt(px: Double, py: Double): Int {
        for (i in count - 1 downTo 0) {
            if (wait[i] > 0) continue
            val dx = px - x[i]
            val dy = py - y[i]
            val c = cos(-a[i])
            val s = sin(-a[i])
            val lx = dx * c - dy * s
            val ly = dx * s + dy * c
            if (abs(lx) < halfW[i] + 12 && abs(ly) < halfH[i] + 12) return i
        }
        // Nothing under the finger: the nearest letter, if the finger is close to it.
        var best = -1
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in 0 until count) {
            if (wait[i] > 0) continue
            val d = hypot(px - x[i], py - y[i])
            if (d < max(halfW[i], halfH[i]) + GRAB_REACH && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }

    /** Takes hold of letter [i] at the finger. */
    private fun grab(i: Int) {
        val dx = fx - x[i]
        val dy = fy - y[i]
        val c = cos(-a[i])
        val s = sin(-a[i])
        held = i
        gx = dx * c - dy * s
        gy = dx * s + dy * c
    }

    /** Lets go of the held letter, throwing it with the finger's speed. */
    private fun letGo() {
        val i = held
        if (i < 0) return
        held = -1
        if (still) return
        var tx = vx[i] * (1 - THROW_SHARE) + fvx * THROW_SHARE
        var ty = vy[i] * (1 - THROW_SHARE) + fvy * THROW_SHARE
        val speed = hypot(tx, ty)
        if (speed > MAX_SPEED) {
            tx *= MAX_SPEED / speed
            ty *= MAX_SPEED / speed
        }
        vx[i] = tx
        vy[i] = ty
    }

    /** A flick up off the floor at [at] with upward speed [speed] (dp/s): every letter jumps, nearest first. */
    private fun flick(at: Double, speed: Double, user: Boolean) {
        if (still) return
        val power = min(FLICK_MAX, 380 + abs(speed) * FLICK_POWER)
        val width = max(1.0, right - left)
        for (i in 0 until count) {
            val far = abs(x[i] - at)
            kick[i] = power * (1 - (0.3 * far) / width)
            kickIn[i] = far / FLICK_WAVE
        }
        if (user) userFlicks += 1
    }

    fun fingerDown(px: Double, py: Double) {
        down = true
        scripted = false
        fx = px
        fy = py
        fvx = 0.0
        fvy = 0.0
        hold = USER_HOLD
        val i = letterAt(px, py)
        if (i >= 0) {
            grab(i)
            userGrabs += 1
        } else {
            held = -1
        }
    }

    fun fingerMove(px: Double, py: Double, svx: Double, svy: Double) {
        if (!down || scripted) return
        fx = px
        fy = py
        fvx = svx
        fvy = svy
        hold = USER_HOLD
    }

    /** The finger lifts: a held letter is thrown; a flick up from empty floor makes every letter jump. */
    fun fingerUp(svx: Double, svy: Double) {
        if (!down || scripted) return
        down = false
        fvx = svx
        fvy = svy
        hold = USER_HOLD
        if (held >= 0) letGo() else if (svy < -FLICK_MIN) flick(fx, svy, true)
    }

    /** A double tap at a point: the next word. */
    fun doubleTap(px: Double, py: Double, boxes: List<DoubleArray>, gap: Double) {
        if (boxes.isEmpty()) return
        tapAge = 0.0
        tapX = px
        tapY = py
        swapWord((word + 1) % boxes.size, boxes, gap, true)
    }

    /** The scripted hand: drags, a flick and two word changes, at their times in the loop. */
    private fun playScript(before: Double, now: Double, boxes: List<DoubleArray>, gap: Double) {
        for (d in Drags) {
            val start = d[0]
            val end = d[1]
            val letter = d[2].toInt()
            if (crossed(before, now, start) && letter < count) {
                // Touch down on the letter's middle, a little above centre.
                down = true
                scripted = true
                fx = x[letter]
                fy = y[letter] - halfH[letter] * 0.25
                fvx = 0.0
                fvy = 0.0
                grab(letter)
                gx = 0.0
                gy = -halfH[letter] * 0.25
                dragX = fx
                dragY = fy
            }
            if (scripted && down && held == letter && now > start && now < end) {
                // Through the keyframes, eased, from where the finger first came down.
                val keys = (d.size - 5) / 2
                val t = easeInOut((now - start) / (end - start)) * (keys - 1)
                val k = min(keys - 2, floor(t).toInt())
                val f = t - k
                fx = dragX + d[5 + k * 2] + (d[7 + k * 2] - d[5 + k * 2]) * f
                fy = dragY + d[6 + k * 2] + (d[8 + k * 2] - d[6 + k * 2]) * f
            }
            if (crossed(before, now, end) && scripted && down) {
                fvx = d[3]
                fvy = d[4]
                down = false
                letGo()
            }
        }
        if (crossed(before, now, FLICK_AT)) flick((left + right) / 2, -1500.0, false)
        for (swap in Swaps) {
            if (crossed(before, now, swap[0])) {
                tapAge = 0.0
                tapX = (left + right) / 2
                tapY = floor - 70
                swapWord(swap[1].toInt(), boxes, gap, false)
            }
        }
    }

    /** Applies force (fx, fy) at world point (px, py) of letter [i] for [h] seconds. */
    private fun push(i: Int, fx: Double, fy: Double, px: Double, py: Double, h: Double) {
        val inertia = (4 * (halfW[i] * halfW[i] + halfH[i] * halfH[i])) / 12
        val rx = px - x[i]
        val ry = py - y[i]
        vx[i] += fx * h
        vy[i] += fy * h
        va[i] += ((rx * fy - ry * fx) / inertia) * h
    }

    /** Point on letter [i] at local (lx, ly), in world space. */
    private fun worldX(i: Int, lx: Double, ly: Double) = x[i] + lx * cos(a[i]) - ly * sin(a[i])

    private fun worldY(i: Int, lx: Double, ly: Double) = y[i] + lx * sin(a[i]) + ly * cos(a[i])

    /** Corner contact with the floor, walls and ceiling: push out, bounce, rub. */
    private fun collide(i: Int) {
        val inertia = (4 * (halfW[i] * halfW[i] + halfH[i] * halfH[i])) / 12
        // The letter's lowest point, treating it as a box with well-rounded corners: as it tips it
        // rolls on a curve instead of see-sawing between corners, so it rights itself.
        val round = min(halfW[i], halfH[i]) * ROUNDING
        val c = cos(a[i])
        val sn = sin(a[i])
        // "Down" in the letter's own frame.
        val dx = sn
        val dy = c
        val lx = (halfW[i] - round) * sign(dx) + round * dx
        val ly = (halfH[i] - round) * sign(dy) + round * dy
        val cx = lx * c - ly * sn
        val cy = lx * sn + ly * c
        val deepest = y[i] + cy - floor
        if (deepest > 0) {
            y[i] -= deepest
            // Speed of the contact point, into the floor (+y).
            val vcx = vx[i] - va[i] * cy
            val vcy = vy[i] + va[i] * cx
            if (vcy > 0) {
                val e = if (vcy > BOUNCE_BELOW) BOUNCE else 0.0
                val j = ((1 + e) * vcy) / (1 + (cx * cx) / inertia)
                vy[i] -= j
                va[i] -= (cx * j) / inertia
                // Friction along the floor, no more than the push allows.
                val jt0 = -vcx / (1 + (cy * cy) / inertia)
                val jt = max(-FRICTION * j, min(FRICTION * j, jt0))
                vx[i] += jt
                va[i] -= (cy * jt) / inertia
            }
        }
        // Walls and ceiling, against the letter's reach.
        val reach = hypot(halfW[i], halfH[i]) * 0.8
        if (x[i] - reach < left) {
            x[i] = left + reach
            if (vx[i] < 0) vx[i] *= -0.5
        }
        if (x[i] + reach > right) {
            x[i] = right - reach
            if (vx[i] > 0) vx[i] *= -0.5
        }
        if (y[i] - reach < ceiling && vy[i] < 0 && held != i) {
            y[i] = ceiling + reach
            if (vy[i] < 0) vy[i] *= -0.5
        }
    }

    /** One substep of forces for every letter that is on the floor (not still waiting to drop). */
    private fun forces(h: Double) {
        for (i in 0 until count) {
            if (wait[i] > 0) continue
            // Home, upright, gravity, air.
            vx[i] += (HOME_X * (restX[i] - x[i]) - HOME_X_DAMPING * vx[i]) * h
            vy[i] += (GRAVITY + HOME_Y * (restY[i] - y[i]) - AIR * vy[i]) * h
            va[i] += (-UPRIGHT * a[i] - UPRIGHT_DAMPING * va[i]) * h
        }
        // Links: the right edge of each letter to the left edge of the next.
        for (i in 0 until count - 1) {
            val j = i + 1
            if (wait[i] > 0 || wait[j] > 0) continue
            val ax = worldX(i, halfW[i], 0.0)
            val ay = worldY(i, halfW[i], 0.0)
            val bx = worldX(j, -halfW[j], 0.0)
            val by = worldY(j, -halfW[j], 0.0)
            val rest = restX[j] - halfW[j] - (restX[i] + halfW[i])
            val dx = bx - ax
            val dy = by - ay
            val hl = hypot(dx, dy)
            val length = if (hl == 0.0) 1e-3 else hl
            val ux = dx / length
            val uy = dy / length
            // Speeds of the two link ends, and how fast the link is stretching.
            val vax = vx[i] - va[i] * (ay - y[i])
            val vay = vy[i] + va[i] * (ax - x[i])
            val vbx = vx[j] - va[j] * (by - y[j])
            val vby = vy[j] + va[j] * (bx - x[j])
            val stretch = (vbx - vax) * ux + (vby - vay) * uy
            val f = LINK * (length - rest) + LINK_DAMPING * stretch
            push(i, f * ux, f * uy, ax, ay, h)
            push(j, -f * ux, -f * uy, bx, by, h)
        }
        // The finger, on the point where it took hold.
        val g = held
        if (g >= 0 && wait[g] <= 0) {
            val px = worldX(g, gx, gy)
            val py = worldY(g, gx, gy)
            val vpx = vx[g] - va[g] * (py - y[g])
            val vpy = vy[g] + va[g] * (px - x[g])
            val pullX = GRAB * (fx - px) - GRAB_DAMPING * vpx
            val pullY = GRAB * (fy - py) - GRAB_DAMPING * vpy
            push(g, pullX, pullY, px, py, h)
        }
    }

    /** One frame: the script, the drops and kicks, the physics, the leaving word. */
    fun step(frameDt: Double, boxes: List<DoubleArray>, gap: Double) {
        if (!frameDt.isFinite()) return
        val dt = min(max(frameDt, 0.0), DT_CAP)
        if (dt <= 0) return
        time += dt
        arrive += dt

        if (hold > 0) {
            if (!down) hold -= dt
            if (hold <= 0 && looping) {
                // The loop starts over from its first word, at rest.
                clock = 0.0
                if (word != 0) swapWord(0, boxes, gap, false)
            }
        } else if (looping) {
            val before = clock
            clock = (clock + dt) % LOOP
            playScript(before, clock, boxes, gap)
        }

        for (i in 0 until count) {
            if (wait[i] > 0) wait[i] = max(0.0, wait[i] - dt)
            if (kickIn[i] >= 0) {
                kickIn[i] -= dt
                if (kickIn[i] < 0 && wait[i] <= 0) {
                    vy[i] = -kick[i]
                    vx[i] += (random() - 0.5) * 160
                    va[i] += (random() - 0.5) * 9
                }
            }
        }

        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            forces(h)
            for (i in 0 until count) {
                if (wait[i] > 0) continue
                x[i] += vx[i] * h
                y[i] += vy[i] * h
                a[i] += va[i] * h
                collide(i)
            }
        }

        if (leaveAge >= 0) {
            leaveAge += dt
            if (leaveAge > LEAVE_TIME + LEAVE_STAGGER * MAX_LETTERS) leaveAge = -1.0
        }
        if (tapAge >= 0) {
            tapAge += dt
            if (tapAge > 0.6) tapAge = -1.0
        }
    }

    /** How far letter [i] of the leaving word is through leaving, 0 … 1. */
    fun leaving(i: Int): Double {
        if (leaveAge < 0) return 1.0
        return min(1.0, max(0.0, (leaveAge - i * LEAVE_STAGGER) / LEAVE_TIME))
    }
}
