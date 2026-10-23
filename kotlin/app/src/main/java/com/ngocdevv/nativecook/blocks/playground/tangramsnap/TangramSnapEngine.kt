package com.ngocdevv.nativecook.blocks.playground.tangramsnap

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// One fixed clock for the springs and the autoPlay script.
internal const val TICK = 1.0 / 60
private const val MAX_TICKS = 3
internal const val QUARTER = PI / 4
// A piece within SNAP of its slot (dp) at the slot's turn snaps in on release; within MAGNET it is
// already pulled towards it while held, at most MAGNET_PULL of the way.
private const val SNAP = 34.0
private const val MAGNET = 64.0
private const val MAGNET_PULL = 0.7
// A turn within this of the slot's (rad) still counts as matching.
private const val TURN_SLACK = 0.3
// Seconds of speed a release carries a piece on.
private const val THROW = 0.09
// Pieces flying home leave one after another, this far apart (s), lifted for LIFT_TIME s.
private const val SCATTER_GAP = 0.06
private const val LIFT_TIME = 0.28
// Solving: the colour floods out from the last piece over FILL_TIME s.
private const val FILL_TIME = 1.1
// A user's next puzzle: the solved figure turns into it, holds, then comes apart.
private const val NEXT_HOLD = 1.1
// After a touch, autoPlay waits this long, then takes the figure apart and starts again.
private const val USER_HOLD = 5.0
// Gestures: a pan starts after PAN_SLOP dp; a tap is shorter than TAP_TIME s; a second tap within
// DOUBLE_GAP s makes a double tap (a single tap waits that long for it); two fingers start a turn
// once they have turned TURN_START rad.
private const val PAN_SLOP = 2.0
private const val TAP_TIME = 0.5
private const val DOUBLE_GAP = 0.26
private const val TURN_START = 0.03

// Haptic requests, read and cleared by the view: light (snap), success, selection, soft.
internal const val BUZZ_SNAP = 1
internal const val BUZZ_SOLVED = 2
internal const val BUZZ_SELECT = 4
internal const val BUZZ_SOFT = 8

internal fun easeInOut(t: Double): Double {
    val u = min(1.0, max(0.0, t))
    return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}

internal fun smooth01(t: Double): Double {
    val u = min(1.0, max(0.0, t))
    return u * u * (3 - 2 * u)
}

/** Corners of shape [k] at a pose, with the flip drawn as a squeeze of the local x (cos π·flip), into [out]. */
internal fun corners(k: Int, x: Double, y: Double, angle: Double, flip: Double, unit: Double, grow: Double, out: DoubleArray = DoubleArray(SHAPES[k].size)): DoubleArray {
    val shape = SHAPES[k]
    val c = cos(angle)
    val s = sin(angle)
    val sx = cos(PI * flip)
    for (i in shape.indices step 2) {
        val lx = shape[i] * unit * grow * sx
        val ly = shape[i + 1] * unit * grow
        out[i] = x + lx * c - ly * s
        out[i + 1] = y + lx * s + ly * c
    }
    return out
}

/** Where the board, the well and the figures along the bottom sit (dp). */
internal data class TangramLayout(
    val unit: Double = 0.0,
    val boardX: Double = 0.0,
    val boardY: Double = 0.0,
    val looseX: Double = 0.0,
    val looseY: Double = 0.0,
    val thumbY: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val insetTop: Double = 0.0,
) {
    companion object {
        fun of(width: Double, height: Double, insetTop: Double, insetBottom: Double): TangramLayout {
            val headerBottom = insetTop + 104
            val thumbY = height - insetBottom - 38
            val room = thumbY - 34 - headerBottom
            // The board and the well stacked between the header and the figures, as large as fits.
            val unit = max(
                20.0,
                min(min((width - 26) / (LOOSE_HALF_W * 2 + 0.9), (width - 40) / (MAX_HALF_W * 2)), room / (MAX_HALF_H * 2 + LOOSE_HALF_H * 2 + 1.4)),
            )
            val boardY = headerBottom + MAX_HALF_H * unit
            val looseY = (boardY + MAX_HALF_H * unit + 0.5 * unit + thumbY - 34) / 2
            return TangramLayout(unit, width / 2, boardY, width / 2 + 0.05 * unit, looseY, thumbY, width, height, insetTop)
        }
    }
}

/** A slot's pose on screen. */
internal class SlotPose {
    var x = 0.0
    var y = 0.0
    var angle = 0.0
    var flip = 0.0
}

/**
 * The pieces, the figure and its morphs, the autoPlay script and the gestures, stepped like the
 * React Native frame callback: whole ticks of 1/60 s, at most three a frame. Gestures arrive as
 * counters with their data, exactly as React Native's shared values carry them, and each tick
 * handles every new one once. Plain Kotlin, so it also runs in a command-line parity check.
 * [auto] is passed in at construction so the very first frame is already the script's first frame.
 */
internal class TangramSnapEngine(auto: Boolean, val reduced: Boolean) {
    var events = 0
    private var looping = auto
    var layout = TangramLayout()
        private set

    // The scene.
    var pieces = Pieces()
        private set
    private var rest = Pieces()
    var ready = false
        private set
    /** The silhouette shown, and the morph to another (0…1; 1 when settled). */
    var puzzle = 0
        private set
    var from = 0
        private set
    var morph = 1.0
        private set
    /** Slot poses at the morph's start and end (dp, rad, flip). */
    private val ax = DoubleArray(7)
    private val ay = DoubleArray(7)
    private val aa = DoubleArray(7)
    private val af = DoubleArray(7)
    private val bx = DoubleArray(7)
    private val by = DoubleArray(7)
    private val ba = DoubleArray(7)
    private val bf = DoubleArray(7)
    /** Pieces that ride the morph in their slots, with their own start and end turns. */
    private val ride = BooleanArray(7)
    private val pa = DoubleArray(7)
    private val pf = DoubleArray(7)
    private val qa = DoubleArray(7)
    private val qf = DoubleArray(7)
    /** Solving: whether every piece is in, and the colour's flood (0…1) and where it starts. */
    var solved = false
        private set
    var fill = 0.0
        private set
    var revealX = 0.0
        private set
    var revealY = 0.0
        private set
    /** Per piece: seconds until it flies home (-1 none), and seconds left lifted. */
    private val scatter = DoubleArray(7) { -1.0 }
    private val lifted = DoubleArray(7)
    /** Per slot: seconds since a piece snapped in (for the pulse), -1 none. */
    val pulse = DoubleArray(7) { -1.0 }
    /** The piece in the finger (-1), where it was grabbed, and the slot it is drawn towards. */
    var held = -1
        private set
    private var grabX = 0.0
    private var grabY = 0.0
    var magnetSlot = -1
        private set
    /** The piece two fingers turn, and its turn when they landed. */
    private var turning = -1
    private var turnFrom = 0.0
    /** The piece the script is carrying, from where, to which slot. */
    var carry = -1
        private set
    private var carryX = 0.0
    private var carryY = 0.0
    private var carryA0 = 0.0
    private var carryA = 0.0
    private var carryF = 0.0
    private var carryAt = 0.0
    /** A user's next puzzle: seconds until the solved figure comes apart (-1 none). */
    private var apartIn = -1.0
    /** Gesture events already handled. */
    private val seen = IntArray(6)

    private var acc = 0.0
    var clock = 0.0
        private set
    var hold = 0.0
        private set

    // Gesture events, as counters with their data (React Native's shared values).
    private var downN = 0
    private var downX = 0.0
    private var downY = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var upN = 0
    private var upVX = 0.0
    private var upVY = 0.0
    private var tapN = 0
    private var tapX = 0.0
    private var tapY = 0.0
    private var dblN = 0
    private var dblX = 0.0
    private var dblY = 0.0
    private var turnN = 0
    private var turnEndN = 0
    private var turnX = 0.0
    private var turnY = 0.0
    private var turnBy = 0.0

    private val slotA = SlotPose()
    private val scratch = DoubleArray(8)

    fun setAuto(auto: Boolean) {
        if (auto == looping) return
        looping = auto
        hold = 0.0
        clock = 0.0
    }

    /** A new size starts the board over. */
    fun setLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        // No board until the size is real (0 before layout, infinite in an unbounded parent).
        val sized = width.isFinite() && height.isFinite() && width > 0 && height > 0
        val l = if (sized) TangramLayout.of(width, height, insetTop, insetBottom) else TangramLayout()
        if (l == layout) return
        layout = l
        ready = false
    }

    val placedCount get() = pieces.slot.count { it >= 0 }

    // Slots.

    /** Slot [k] of silhouette [p] on screen, at its first listed pose, into [out]. */
    fun slotAt(p: Int, k: Int, out: SlotPose): SlotPose {
        val slot = SILHOUETTES[p].slots[k]
        out.x = layout.boardX + slot.x * layout.unit
        out.y = layout.boardY + slot.y * layout.unit
        out.angle = (slot.turns[0] * PI) / 4
        out.flip = slot.flips[0].toDouble()
        return out
    }

    /** The morph's eased progress. */
    val morphEase get() = if (reduced) smooth01(morph) else easeInOut(morph)

    /** Where slot [k] is drawn now: settled, or partway through a morph, into [out]. */
    fun slotNow(k: Int, out: SlotPose): SlotPose {
        if (morph >= 1) return slotAt(puzzle, k, out)
        val e = morphEase
        out.x = ax[k] + (bx[k] - ax[k]) * e
        out.y = ay[k] + (by[k] - ay[k]) * e
        out.angle = aa[k] + (ba[k] - aa[k]) * e
        out.flip = af[k] + (bf[k] - af[k]) * e
        return out
    }

    // Moves.

    /** Sends piece [i] home to the well, after [delay] s. */
    private fun sendHome(i: Int, delay: Double) {
        pieces.slot[i] = -1
        scatter[i] = delay
    }

    /** Takes piece [i] out of its slot (the figure is no longer whole). */
    private fun unsnap(i: Int) {
        if (pieces.slot[i] < 0) return
        pieces.slot[i] = -1
        if (solved) solved = false
    }

    /** Snaps piece [i] into a matching free slot near (x, y) at its turn, if there is one. */
    private fun trySnap(i: Int, x: Double, y: Double, user: Boolean): Boolean {
        if (morph < 1) return false
        val p = pieces
        val sil = SILHOUETTES[puzzle]
        val flip = if (p.tf[i] > 0.5) 1 else 0
        var best = -1
        var bestD = SNAP
        for (k in sil.slots.indices) {
            if (PIECES[k] != PIECES[i]) continue
            var taken = false
            for (j in p.slot.indices) if (j != i && p.slot[j] == k) taken = true
            if (taken || !turnMatches(sil.slots[k], p.ta[i], flip, TURN_SLACK)) continue
            val at = slotAt(puzzle, k, slotA)
            val d = hypot(at.x - x, at.y - y)
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        if (best < 0) return false
        val pose = slotPose(sil.slots[best], p.ta[i], flip, false) ?: return false
        val at = slotAt(puzzle, best, slotA)
        p.slot[i] = best
        p.mode[i] = 2
        p.tx[i] = at.x
        p.ty[i] = at.y
        p.ta[i] = pose.first
        p.tl[i] = 0.0
        pulse[best] = 0.0
        if (user) events = events or BUZZ_SNAP
        // Every piece in: the figure is whole.
        val all = p.slot.none { it < 0 }
        if (all && !solved) {
            solved = true
            revealX = at.x
            revealY = at.y
            if (user) events = events or BUZZ_SOLVED
        }
        return true
    }

    /** Morphs to silhouette [to]. A solved figure carries its pieces along; otherwise they go home. */
    private fun startMorph(to: Int, keepSolved: Boolean) {
        val p = pieces
        val previous = puzzle
        val b = SlotPose()
        for (k in 0 until 7) {
            val a = slotNow(k, slotA)
            ax[k] = a.x
            ay[k] = a.y
            aa[k] = a.angle
            af[k] = a.flip
            slotAt(to, k, b)
            val pose = slotPose(SILHOUETTES[to].slots[k], a.angle, if (a.flip > 0.5) 1 else 0, true)!!
            bx[k] = b.x
            by[k] = b.y
            ba[k] = pose.first
            bf[k] = pose.second.toDouble()
        }
        val carrying = keepSolved && solved
        for (i in 0 until 7) {
            ride[i] = false
            if (carrying && p.slot[i] >= 0) {
                val k = p.slot[i]
                ride[i] = true
                pa[i] = p.ta[i]
                pf[i] = p.tf[i]
                val pose = slotPose(SILHOUETTES[to].slots[k], p.ta[i], if (p.tf[i] > 0.5) 1 else 0, true)!!
                qa[i] = pose.first
                qf[i] = pose.second.toDouble()
                p.mode[i] = 1
            } else {
                unsnap(i)
                sendHome(i, SCATTER_GAP * i)
            }
        }
        if (!carrying && solved) solved = false
        from = previous
        puzzle = to
        morph = if (to == previous && !carrying) 1.0 else 0.0
    }

    /** The topmost piece under (x, y), or the nearest within reach. */
    private fun pieceAt(x: Double, y: Double): Int {
        val p = pieces
        val unit = layout.unit
        for (k in p.order.size - 1 downTo 0) {
            val i = p.order[k]
            val c = corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, 1.08)
            if (insideConvex(c, x, y)) return i
        }
        var best = -1
        var bestD = unit * 0.55
        for (i in 0 until 7) {
            val d = hypot(p.x[i] - x, p.y[i] - y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    /** Lets go of the scripted piece where it is (a touch interrupts the script). */
    private fun dropCarry() {
        val i = carry
        if (i < 0) return
        val p = pieces
        p.mode[i] = 0
        p.tx[i] = p.x[i]
        p.ty[i] = p.y[i]
        p.ta[i] = jsRound(p.ta[i] / QUARTER) * QUARTER
        p.tf[i] = if (p.tf[i] > 0.5) 1.0 else 0.0
        p.tl[i] = 0.0
        carry = -1
    }

    private fun touched() {
        hold = USER_HOLD
        dropCarry()
    }

    /** Gesture events, in the order React Native handles them. */
    private fun handleInput() {
        val p = pieces
        val unit = layout.unit
        if (downN != seen[0]) {
            seen[0] = downN
            touched()
            val i = pieceAt(downX, downY)
            if (i >= 0 && morph >= 1) {
                unsnap(i)
                scatter[i] = -1.0
                held = i
                grabX = p.x[i] - downX
                grabY = p.y[i] - downY
                fingerX = downX
                fingerY = downY
                p.mode[i] = 1
                p.tl[i] = 1.0
                p.raise(i)
            }
        }
        if (turnN != seen[3]) {
            seen[3] = turnN
            touched()
            // Not during a morph: a piece riding it must keep its slot.
            val i = if (morph < 1) -1 else if (held >= 0) held else pieceAt(turnX, turnY)
            turning = i
            if (i >= 0) {
                unsnap(i)
                turnFrom = p.ta[i]
            }
        }
        if (turning >= 0) {
            p.ta[turning] = turnFrom + (if (reduced) 0.0 else 1.0) * turnBy
            if (reduced) p.ta[turning] = turnFrom + jsRound(turnBy / QUARTER) * QUARTER
        }
        if (turnEndN != seen[4]) {
            seen[4] = turnEndN
            val i = turning
            turning = -1
            if (i >= 0) {
                p.ta[i] = jsRound(p.ta[i] / QUARTER) * QUARTER
                events = events or BUZZ_SELECT
                if (held != i) trySnap(i, p.tx[i], p.ty[i], true)
            }
        }
        if (upN != seen[1]) {
            seen[1] = upN
            val i = held
            held = -1
            magnetSlot = -1
            if (i >= 0) {
                p.mode[i] = 0
                p.tl[i] = 0.0
                if (turning != i) p.ta[i] = jsRound(p.ta[i] / QUARTER) * QUARTER
                // The finger's speed carries on into the spring, and decides where it settles.
                val vx = if (reduced) 0.0 else upVX
                val vy = if (reduced) 0.0 else upVY
                p.vx[i] = vx
                p.vy[i] = vy
                val gx = p.tx[i] + vx * THROW
                val gy = p.ty[i] + vy * THROW
                if (!trySnap(i, p.tx[i], p.ty[i], true) && !trySnap(i, gx, gy, true)) {
                    val m = unit * 0.6
                    p.tx[i] = min(layout.width - m, max(m, gx))
                    p.ty[i] = min(layout.thumbY - 30, max(layout.insetTop + 40, gy))
                }
            }
        }
        if (tapN != seen[2]) {
            seen[2] = tapN
            touched()
            // The figures along the bottom: tap one to morph into it.
            if (abs(tapY - layout.thumbY) < 30) {
                val k = jsRound((tapX - layout.width / 2) / 64 + 1.5).toInt()
                if (k >= 0 && k < SILHOUETTES.size && abs(tapX - (layout.width / 2 + (k - 1.5) * 64)) < 30 && k != puzzle && morph >= 1) {
                    val wasSolved = solved
                    startMorph(k, true)
                    if (wasSolved) apartIn = MORPH_TIME + NEXT_HOLD
                    events = events or BUZZ_SOFT
                }
            } else {
                val i = pieceAt(tapX, tapY)
                if (i >= 0 && morph >= 1) {
                    unsnap(i)
                    p.ta[i] = jsRound(p.ta[i] / QUARTER) * QUARTER + QUARTER
                    p.raise(i)
                    events = events or BUZZ_SELECT
                    trySnap(i, p.tx[i], p.ty[i], true)
                }
            }
        }
        if (dblN != seen[5]) {
            seen[5] = dblN
            touched()
            val i = pieceAt(dblX, dblY)
            if (i >= 0 && morph >= 1) {
                unsnap(i)
                p.tf[i] = if (p.tf[i] > 0.5) 0.0 else 1.0
                p.raise(i)
                events = events or BUZZ_SELECT
                trySnap(i, p.tx[i], p.ty[i], true)
            }
        }
        // The held piece follows the finger, pulled towards a matching slot when it is close.
        if (held >= 0) {
            val i = held
            var tx = fingerX + grabX
            var ty = fingerY + grabY
            magnetSlot = -1
            val sil = SILHOUETTES[puzzle]
            val flip = if (p.tf[i] > 0.5) 1 else 0
            var k = 0
            while (k < sil.slots.size && morph >= 1) {
                val slotK = k
                k += 1
                if (PIECES[slotK] != PIECES[i] || !turnMatches(sil.slots[slotK], p.ta[i], flip, TURN_SLACK)) continue
                var taken = false
                for (j in 0 until 7) if (j != i && p.slot[j] == slotK) taken = true
                if (taken) continue
                val at = slotAt(puzzle, slotK, slotA)
                val d = hypot(at.x - tx, at.y - ty)
                if (d < MAGNET) {
                    val pull = MAGNET_PULL * (1 - d / MAGNET).pow(1.5)
                    tx += (at.x - tx) * pull
                    ty += (at.y - ty) * pull
                    magnetSlot = slotK
                    break
                }
            }
            p.tx[i] = tx
            p.ty[i] = ty
        }
    }

    /** autoPlay: the script between [before] and [t]. */
    private fun script(before: Double, t: Double) {
        val p = pieces
        for (i in 0 until 7) {
            val at = PLACE_FROM + PLACE_EVERY * i
            if (before < at && t >= at && puzzle == 0 && morph >= 1 && p.slot[i] < 0) {
                val pose = slotPose(SILHOUETTES[0].slots[i], p.ta[i], if (p.tf[i] > 0.5) 1 else 0, true)!!
                carry = i
                carryX = p.x[i]
                carryY = p.y[i]
                carryA0 = p.ta[i]
                carryA = pose.first
                carryF = pose.second.toDouble()
                carryAt = at
                p.mode[i] = 1
                p.tl[i] = 1.0
                p.tf[i] = pose.second.toDouble()
                p.raise(i)
            }
        }
        if (carry >= 0) {
            val i = carry
            val u = (t - carryAt) / PLACE_TIME
            val at = slotAt(0, i, slotA)
            if (u >= 1) {
                p.ta[i] = carryA
                carry = -1
                p.mode[i] = 0
                p.tx[i] = at.x
                p.ty[i] = at.y
                trySnap(i, at.x, at.y, false)
            } else {
                val e = easeInOut(u)
                // An arc: up off the well and down into the slot.
                p.tx[i] = carryX + (at.x - carryX) * e
                p.ty[i] = carryY + (at.y - carryY) * e - sin(PI * e) * layout.unit * 0.6
                p.ta[i] = carryA0 + (carryA - carryA0) * min(1.0, e * 1.3)
            }
        }
        if (before < MORPH_AT && t >= MORPH_AT && solved) startMorph(AUTO_SECOND, true)
        if (before < RESET_AT && t >= RESET_AT) startMorph(0, false)
    }

    private fun tick() {
        val p = pieces
        handleInput()
        if (hold > 0) {
            if (held < 0 && turning < 0) hold -= TICK
            // Back to the loop: take the figure apart and start from the empty cat again.
            if (hold <= 0 && looping) clock = RESET_AT - TICK
        } else if (looping) {
            val before = clock
            var t = before + TICK
            if (t >= LOOP) {
                t -= LOOP
                p.copyFrom(rest)
                puzzle = 0
                morph = 1.0
                fill = 0.0
                solved = false
                scatter.fill(-1.0)
                lifted.fill(0.0)
                pulse.fill(-1.0)
                ride.fill(false)
                carry = -1
                apartIn = -1.0
            }
            clock = t
            script(before, t)
        }
        if (apartIn > 0) {
            apartIn -= TICK
            if (apartIn <= 0) startMorph(puzzle, false)
        }
        // The morph, and the pieces riding it.
        if (morph < 1) {
            morph = min(1.0, morph + TICK / (if (reduced) 0.3 else MORPH_TIME))
            val e = morphEase
            for (i in 0 until 7) {
                if (!ride[i]) continue
                val k = p.slot[i]
                if (k < 0) {
                    ride[i] = false
                    p.mode[i] = 0
                    continue
                }
                val at = slotNow(k, slotA)
                p.tx[i] = at.x
                p.ty[i] = at.y
                p.ta[i] = pa[i] + (qa[i] - pa[i]) * e
                p.tf[i] = pf[i] + (qf[i] - pf[i]) * e
                if (morph >= 1) {
                    p.mode[i] = 2
                    p.ta[i] = qa[i]
                    p.tf[i] = qf[i]
                    ride[i] = false
                }
            }
        }
        // Pieces flying home, one after another, lifted on the way.
        for (i in 0 until 7) {
            if (scatter[i] >= 0) {
                scatter[i] -= TICK
                if (scatter[i] < 0) {
                    val home = LOOSE[i]
                    p.mode[i] = 0
                    p.tx[i] = layout.looseX + home[0] * layout.unit
                    p.ty[i] = layout.looseY + home[1] * layout.unit
                    p.ta[i] = nearestTurn((home[2] * PI) / 4, p.ta[i])
                    p.tf[i] = home[3]
                    p.tl[i] = 1.0
                    lifted[i] = LIFT_TIME
                    scatter[i] = -1.0
                }
            }
            if (lifted[i] > 0) {
                lifted[i] -= TICK
                if (lifted[i] <= 0 && held != i && carry != i) p.tl[i] = 0.0
            }
            if (pulse[i] >= 0) {
                pulse[i] += TICK
                if (pulse[i] > 0.6) pulse[i] = -1.0
            }
        }
        val goal = if (solved) 1.0 else 0.0
        fill = if (goal > fill) min(1.0, fill + TICK / FILL_TIME) else max(0.0, fill - TICK / (FILL_TIME * 0.6))
        p.step(TICK, reduced)
    }

    private fun setUp() {
        val l = layout
        val p = Pieces()
        for (i in 0 until 7) {
            val home = LOOSE[i]
            p.x[i] = l.looseX + home[0] * l.unit
            p.y[i] = l.looseY + home[1] * l.unit
            p.angle[i] = (home[2] * PI) / 4
            p.flip[i] = home[3]
            p.tx[i] = p.x[i]
            p.ty[i] = p.y[i]
            p.ta[i] = p.angle[i]
            p.tf[i] = p.flip[i]
        }
        val r = Pieces()
        r.copyFrom(p)
        pieces = p
        rest = r
        puzzle = 0
        from = 0
        morph = 1.0
        for (a in listOf(ax, ay, aa, af, bx, by, ba, bf, pa, pf, qa, qf)) a.fill(0.0)
        ride.fill(false)
        solved = false
        fill = 0.0
        revealX = l.boardX
        revealY = l.boardY
        scatter.fill(-1.0)
        lifted.fill(0.0)
        pulse.fill(-1.0)
        held = -1
        grabX = 0.0
        grabY = 0.0
        magnetSlot = -1
        turning = -1
        turnFrom = 0.0
        carry = -1
        carryX = 0.0
        carryY = 0.0
        carryA0 = 0.0
        carryA = 0.0
        carryF = 0.0
        carryAt = 0.0
        apartIn = -1.0
        seen[0] = downN
        seen[1] = upN
        seen[2] = tapN
        seen[3] = turnN
        seen[4] = turnEndN
        seen[5] = dblN
        clock = 0.0
        ready = true
    }

    /** One display frame of [elapsed] seconds (null on the first frame). Returns whether it ticked. */
    fun frame(elapsed: Double?): Boolean {
        if (layout.width == 0.0) return false
        realTime += elapsed ?: 0.0
        firePendingTap()
        if (!ready) setUp()
        val dt = min(elapsed ?: 0.016, MAX_TICKS * TICK)
        var pending = acc + dt
        var ticks = 0
        while (pending >= TICK && ticks < MAX_TICKS) {
            tick()
            pending -= TICK
            ticks += 1
        }
        acc = min(pending, TICK)
        return ticks > 0
    }

    // Gestures, as React Native's Gesture Handler reports them.

    fun panBegin(x: Double, y: Double) {
        downX = x
        downY = y
        downN += 1
    }

    fun panUpdate(x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingerX = x
        fingerY = y
        upVX = velocityX
        upVY = velocityY
    }

    fun panEnd(velocityX: Double, velocityY: Double) {
        upVX = velocityX
        upVY = velocityY
    }

    fun panFinalize(success: Boolean) {
        if (!success) {
            upVX = 0.0
            upVY = 0.0
        }
        upN += 1
    }

    fun rotateStart(anchorX: Double, anchorY: Double) {
        turnX = anchorX
        turnY = anchorY
        turnBy = 0.0
        turnN += 1
    }

    fun rotateUpdate(rotation: Double) {
        turnBy = rotation
    }

    fun rotateFinalize() {
        turnEndN += 1
    }

    fun tap(x: Double, y: Double) {
        tapX = x
        tapY = y
        tapN += 1
    }

    fun doubleTap(x: Double, y: Double) {
        dblX = x
        dblY = y
        dblN += 1
    }

    // Raw touches from the view.

    // Seconds of display time, for timing taps.
    private var realTime = 0.0
    private val fingers = LinkedHashMap<Long, DoubleArray>()
    private var firstFinger = -1L
    private var startX = 0.0
    private var startY = 0.0
    private var startAt = 0.0
    private var panActive = false
    private var multi = false
    private var turnActive = false
    private var turnLast = 0.0
    private var turnSum = 0.0
    private var pendingTap: DoubleArray? = null

    private fun firePendingTap() {
        val p = pendingTap ?: return
        if (realTime >= p[2]) {
            pendingTap = null
            tap(p[0], p[1])
        }
    }

    /** The angle between the first two fingers and their midpoint, into [scratch] (0..2), or false. */
    private fun twoFingers(): Boolean {
        val ids = fingers.keys.sorted()
        if (ids.size < 2) return false
        val a = fingers[ids[0]] ?: return false
        val b = fingers[ids[1]] ?: return false
        scratch[0] = atan2(b[1] - a[1], b[0] - a[0])
        scratch[1] = (a[0] + b[0]) / 2
        scratch[2] = (a[1] + b[1]) / 2
        return true
    }

    fun touchDown(id: Long, x: Double, y: Double) {
        fingers[id] = doubleArrayOf(x, y)
        if (fingers.size == 1) {
            firstFinger = id
            startX = x
            startY = y
            startAt = realTime
            panActive = false
            multi = false
            turnActive = false
            panBegin(x, y)
        } else {
            multi = true
            if (twoFingers()) {
                turnLast = scratch[0]
                turnSum = 0.0
            }
        }
    }

    fun touchMove(id: Long, x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingers[id]?.let {
            it[0] = x
            it[1] = y
        }
        if (id == firstFinger) {
            val dx = x - startX
            val dy = y - startY
            if (!panActive && dx * dx + dy * dy >= PAN_SLOP * PAN_SLOP) panActive = true
            if (panActive) panUpdate(x, y, velocityX, velocityY)
        }
        if (fingers.size >= 2 && twoFingers()) {
            var d = scratch[0] - turnLast
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            turnLast = scratch[0]
            turnSum += d
            if (!turnActive && abs(turnSum) >= TURN_START) {
                turnActive = true
                rotateStart(scratch[1], scratch[2])
            }
            if (turnActive) rotateUpdate(turnSum)
        }
    }

    fun touchUp(id: Long, x: Double, y: Double, velocityX: Double, velocityY: Double) {
        fingers.remove(id)
        if (fingers.size < 2 && turnActive) {
            turnActive = false
            rotateFinalize()
        }
        if (fingers.isNotEmpty()) return
        if (panActive) {
            panEnd(velocityX, velocityY)
            panFinalize(true)
        } else {
            panFinalize(false)
            if (!multi && realTime - startAt <= TAP_TIME) {
                // A second tap soon after the first makes a double tap; a lone tap waits for it.
                val p = pendingTap
                if (p != null && startAt <= p[3]) {
                    pendingTap = null
                    doubleTap(x, y)
                } else {
                    pendingTap = doubleArrayOf(x, y, realTime + DOUBLE_GAP, realTime + DOUBLE_GAP)
                }
            }
        }
        panActive = false
        firstFinger = -1L
    }

    /** The gesture was cut off (the pointer input restarted or left): every finger lifts, no tap. */
    fun touchCancel() {
        if (fingers.isEmpty() && firstFinger < 0) return
        fingers.clear()
        if (turnActive) {
            turnActive = false
            rotateFinalize()
        }
        panFinalize(false)
        panActive = false
        firstFinger = -1L
    }
}
