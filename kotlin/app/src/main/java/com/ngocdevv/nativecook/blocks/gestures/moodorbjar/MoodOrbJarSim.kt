package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Fixed-step simulation of the jar, ported from `jar-sim.ts`: a pile of orbs that keep their slots, one orb thrown on a
 * solved arc that lands with real collisions, the lid's hinge and the undo swipe. A finger and the autoplay share
 * `applyInput`. Everything runs on the main thread, in points (dp) and seconds, in doubles like the JS original.
 */

internal object MoodOrbJarOrb {
    const val OFF = 0
    const val PILE = 1
    const val SETTLING = 2
    const val FLYING = 3
    const val HELD = 4
    const val LEAVING = 5
    const val RETURNING = 6
}

internal object MoodOrbJarEvent {
    const val ADDED = 100
    const val REMOVED = 200
}

/** The source's 18 resting orbs plus four reusable bodies for throws and undo. */
internal val PILE_N = MoodOrbJarTokens.REPLICA_PILE.size
internal val POOL = PILE_N + 4

internal class MoodOrbJarGeometry(
    val w: Double,
    val h: Double,
    val s: Double,
    val top: Double,
    val cx: Double,
    val mouthY: Double,
    val floor: Double,
    val left: Double,
    val right: Double,
    val shoulderEnd: Double,
    val mouthHalf: Double,
    val corner: Double,
    val slotY: Double,
    val slotX: DoubleArray,
    val rowY: Double,
    val rowH: Double,
    val lidX: Double,
    val lidY: Double,
)

internal fun makeGeometry(w: Double, h: Double, safeTop: Double): MoodOrbJarGeometry {
    val s = w / MoodOrbJarTheme.STAGE_WIDTH
    val top = safeTop + MoodOrbJarLayout.HEADER * s
    val cx = w / 2
    val jarWidth = MoodOrbJarLayout.JAR_WIDTH * s
    val mouthY = top + MoodOrbJarLayout.JAR_TOP * s
    val slotX = DoubleArray(MoodOrbJarMoods.size) { i ->
        cx + (MoodOrbJarLayout.SLOT_DX + (i - (MoodOrbJarMoods.size - 1) / 2.0) * MoodOrbJarLayout.SLOT_GAP) * s
    }
    return MoodOrbJarGeometry(
        w = w,
        h = h,
        s = s,
        top = top,
        cx = cx,
        mouthY = mouthY,
        floor = mouthY + (MoodOrbJarLayout.JAR_HEIGHT - MoodOrbJarLayout.BASE) * s,
        left = cx - jarWidth / 2 + MoodOrbJarLayout.WALL * s,
        right = cx + jarWidth / 2 - MoodOrbJarLayout.WALL * s,
        shoulderEnd = mouthY + MoodOrbJarLayout.SHOULDER * s,
        mouthHalf = MoodOrbJarLayout.MOUTH_HALF * s,
        corner = MoodOrbJarLayout.CORNER * s,
        slotY = top + (MoodOrbJarLayout.PICKER_TOP + MoodOrbJarLayout.PICKER_ORB_Y) * s,
        slotX = slotX,
        rowY = top + MoodOrbJarLayout.ENTRIES_TOP * s,
        rowH = MoodOrbJarLayout.ENTRY_HEIGHT * s,
        lidX = cx - (MoodOrbJarLayout.LID_WIDTH * s) / 2,
        lidY = mouthY - (MoodOrbJarLayout.LID_HEIGHT - MoodOrbJarLayout.LID_OVERLAP) * s,
    )
}

internal sealed interface MoodOrbJarInput {
    data class Down(val x: Double, val y: Double, val script: Boolean = false) : MoodOrbJarInput
    data class Move(val x: Double, val y: Double, val vx: Double, val vy: Double) : MoodOrbJarInput
    data class Up(val vx: Double, val vy: Double) : MoodOrbJarInput
    data class Add(val mood: Int) : MoodOrbJarInput
    data object UndoCommand : MoodOrbJarInput
}

internal class MoodOrbJarSim(var reduced: Boolean, autoPlay: Boolean) {
    var acc = 0.0
    var clock = 0.0
    val x = DoubleArray(POOL)
    val y = DoubleArray(POOL)
    val vx = DoubleArray(POOL)
    val vy = DoubleArray(POOL)
    val hx = DoubleArray(POOL)
    val hy = DoubleArray(POOL)
    val mood = IntArray(POOL)
    val state = IntArray(POOL)
    val restT = DoubleArray(POOL)
    val t = DoubleArray(POOL)
    val spin = DoubleArray(POOL)
    val fade = DoubleArray(POOL)
    val fromX = DoubleArray(POOL)
    val fromY = DoubleArray(POOL)
    var held = -1
    var gdx = 0.0
    var gdy = 0.0
    var downX = 0.0
    var downY = 0.0
    var downT = 0.0
    var moved = 0.0
    var swiping = false
    var rowX = 0.0
    var rowV = 0.0
    var lastAdded = -1
    var swipeX0 = 0.0
    var lid = 0.0
    var lidV = 0.0
    var lidHold = 0.0
    val slotScale = DoubleArray(MoodOrbJarMoods.size) { 1.0 }
    var scripted = false
    var demoOn = autoPlay
    var demoT = 0.0
    var quiet = 0.0
    val events = ArrayList<Int>()
    val sq = DoubleArray(POOL)
    val sqv = DoubleArray(POOL)
    val pvy = DoubleArray(POOL)
    var tilt = 0.0
    var ghostT = -1.0
    var ghostX = 0.0
    var ghostY = 0.0
    var ghostMood = 0
    var growT = -1.0
    var rippleT = -1.0
    var rippleX = 0.0
    var rippleY = 0.0
    var rippleMood = 0
    var hopAt = 0

    // Scratch positions of the collision step, kept here so a step allocates nothing.
    val px = DoubleArray(POOL)
    val py = DoubleArray(POOL)
}

internal fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0): Double = if (v < lo) lo else if (v > hi) hi else v

/** The JS original's generator, in doubles so the numbers match; its result is never read there either. */
private fun lcg(seed: Double): Double = (seed * 1103515245.0 + 12345.0) % 2147483648.0

private fun easeInOut(u: Double): Double = if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

private fun inJar(state: Int) = state == MoodOrbJarOrb.PILE || state == MoodOrbJarOrb.SETTLING

/** Keeps a point inside the jar: floor, round bottom corners, straight walls, shoulders narrowing to the mouth. */
private fun bound(g: MoodOrbJarGeometry, px: DoubleArray, py: DoubleArray, i: Int, r: Double) {
    if (py[i] < g.mouthY + r * 0.5) return
    if (py[i] > g.floor - r) py[i] = g.floor - r
    var half = (g.right - g.left) / 2
    if (py[i] < g.shoulderEnd) {
        val u = clamp((py[i] - g.mouthY) / (g.shoulderEnd - g.mouthY))
        half = g.mouthHalf + (half - g.mouthHalf) * (u * u * (3 - 2 * u))
    }
    val lo = g.cx - half + r
    val hi = g.cx + half - r
    if (px[i] < lo) px[i] = lo
    if (px[i] > hi) px[i] = hi
    val cy = g.floor - g.corner
    if (py[i] > cy) {
        val cxs = if (px[i] < g.cx) g.left + g.corner else g.right - g.corner
        if ((px[i] < g.cx && px[i] < cxs) || (px[i] >= g.cx && px[i] > cxs)) {
            val dx = px[i] - cxs
            val dy = py[i] - cy
            val length = sqrt(dx * dx + dy * dy)
            val d = if (length == 0.0) 1e-6 else length
            val lim = g.corner - r
            if (d > lim) {
                px[i] = cxs + (dx / d) * lim
                py[i] = cy + (dy / d) * lim
            }
        }
    }
}

/** One step of the orbs in the jar: springs (pile) or gravity (landing), then collisions on predicted positions. */
private fun stepJar(s: MoodOrbJarSim, g: MoodOrbJarGeometry, settleAll: Boolean): Double {
    val dt = MoodOrbJarMotion.STEP
    // The source's glass rims overlap slightly, so collision bodies use 90% of the drawn radius.
    val r = MoodOrbJarLayout.ORB * g.s * MoodOrbJarPile.COLLISION_SCALE
    val px = s.px
    val py = s.py
    val damp = exp(-MoodOrbJarPile.DAMPING * dt)
    for (i in 0 until POOL) {
        if (inJar(s.state[i])) {
            if (s.state[i] == MoodOrbJarOrb.SETTLING || settleAll) {
                s.vy[i] += MoodOrbJarThrow.GRAVITY * g.s * dt
            } else {
                // Phone tilt shifts every slot a little: the pile sloshes.
                s.vx[i] += -MoodOrbJarPile.HOME * (s.x[i] - s.hx[i] - s.tilt * MoodOrbJarTilt.SLOSH * g.s) * dt
                s.vy[i] += -MoodOrbJarPile.HOME * (s.y[i] - s.hy[i]) * dt
            }
            s.vx[i] *= damp
            s.vy[i] *= damp
        }
        px[i] = s.x[i] + (if (inJar(s.state[i])) s.vx[i] * dt else 0.0)
        py[i] = s.y[i] + (if (inJar(s.state[i])) s.vy[i] * dt else 0.0)
    }
    var impact = 0.0
    for (k in 0 until MoodOrbJarPile.PASSES) {
        for (i in 0 until POOL) {
            if (!inJar(s.state[i])) continue
            for (j in i + 1 until POOL) {
                if (!inJar(s.state[j])) continue
                val dx = px[j] - px[i]
                val dy = py[j] - py[i]
                val length = sqrt(dx * dx + dy * dy)
                val d = if (length == 0.0) 1e-6 else length
                if (d >= r * 2) continue
                val c = (r * 2 - d) / d / 2
                px[i] -= dx * c
                py[i] -= dy * c
                px[j] += dx * c
                py[j] += dy * c
                if (k == 0 && (s.state[i] == MoodOrbJarOrb.SETTLING || s.state[j] == MoodOrbJarOrb.SETTLING)) {
                    impact = max(impact, r * 2 - d)
                }
            }
        }
        for (i in 0 until POOL) {
            if (inJar(s.state[i])) bound(g, px, py, i, r)
        }
    }
    for (i in 0 until POOL) {
        if (!inJar(s.state[i])) continue
        s.vx[i] = (px[i] - s.x[i]) / dt
        s.vy[i] = (py[i] - s.y[i]) / dt
        s.x[i] = px[i]
        s.y[i] = py[i]
    }
    return impact
}

internal fun initialSim(g: MoodOrbJarGeometry, reduced: Boolean, autoPlay: Boolean): MoodOrbJarSim {
    val s = MoodOrbJarSim(reduced, autoPlay)
    // A seeded pile, dropped and settled once; that pose is its home.
    var seed = MoodOrbJarPile.SEED.toDouble()
    val r = MoodOrbJarLayout.ORB * g.s
    for (i in 0 until POOL) {
        seed = lcg(seed)
        val on = i < PILE_N
        val col = i % 5
        val row = i / 5
        s.x[i] = g.cx + (col - 2) * r * 2.2 + (row % 2) * r * 0.6
        s.y[i] = g.floor - r - row * r * 2.4 - 20 * g.s
        // The reference's jar is mostly awful/bad purples and blues, a few neutral, good and great.
        s.mood[i] = MoodOrbJarPileMoods[i % MoodOrbJarPileMoods.size]
        s.state[i] = if (on) MoodOrbJarOrb.SETTLING else MoodOrbJarOrb.OFF
        s.fade[i] = if (on) 1.0 else 0.0
    }
    repeat(MoodOrbJarPile.SETTLE_STEPS) { stepJar(s, g, true) }
    val sourceScale = g.w / 1206.0
    for (i in 0 until PILE_N) {
        val source = MoodOrbJarTokens.REPLICA_PILE[i]
        s.x[i] = source.x * sourceScale
        s.y[i] = source.y * sourceScale
        s.mood[i] = source.mood
        s.spin[i] = if (MoodOrbJarTheme.REPLICA) -source.turn * PI / 180.0 else 0.0
        s.state[i] = MoodOrbJarOrb.PILE
        s.hx[i] = s.x[i]
        s.hy[i] = s.y[i]
        s.vx[i] = 0.0
        s.vy[i] = 0.0
    }
    return s
}

/** Haptics only for a real finger: the scripted autoplay stays silent. */
private fun emit(s: MoodOrbJarSim, haptic: Int) {
    if (!s.scripted && !s.demoOn) s.events.add(haptic)
}

private fun freeOrb(s: MoodOrbJarSim): Int {
    for (i in PILE_N until POOL) {
        if (s.state[i] == MoodOrbJarOrb.OFF) return i
    }
    return -1
}

/** Launch on an arc solved to peak above the mouth and drop into it, blended with the finger's own velocity. */
private fun launch(s: MoodOrbJarSim, g: MoodOrbJarGeometry, i: Int, ux: Double, uy: Double) {
    val gr = MoodOrbJarThrow.GRAVITY * g.s
    val apex = min(s.y[i], g.mouthY) - MoodOrbJarThrow.APEX * g.s
    val vy0 = -sqrt(2 * gr * max(1.0, s.y[i] - apex))
    val tUp = -vy0 / gr
    val tDown = sqrt((2 * (g.mouthY + 40 * g.s - apex)) / gr)
    val vx0 = (g.cx - s.x[i]) / (tUp + tDown)
    val k = MoodOrbJarThrow.SOLVED
    val lim = MoodOrbJarThrow.MAX_SPEED * g.s
    s.vx[i] = clamp(vx0 * k + ux * (1 - k) * 0.3, -lim, lim)
    s.vy[i] = vy0 * k + clamp(uy, -lim, 0.0) * (1 - k) * 0.25
    // The finger's share must not miss the mouth: re-aim horizontally for the time actually flown.
    val t = (-s.vy[i] + sqrt(s.vy[i] * s.vy[i] + 2 * gr * max(0.0, g.mouthY + 40 * g.s - s.y[i]))) / gr
    s.vx[i] = (g.cx - s.x[i]) / max(0.2, t)
    s.state[i] = MoodOrbJarOrb.FLYING
    s.spin[i] = 0.0
    s.slotScale[s.mood[i]] = 0.0
    emit(s, MoodOrbJarHaptic.THROW)
}

/** Reduce Motion: the orb appears at the slot physics would give it, without the flight. */
private fun placeInstantly(s: MoodOrbJarSim, g: MoodOrbJarGeometry, i: Int) {
    s.x[i] = g.cx
    s.y[i] = g.mouthY + 30 * g.s
    s.vx[i] = 0.0
    s.vy[i] = 0.0
    s.state[i] = MoodOrbJarOrb.SETTLING
    repeat(240) { stepJar(s, g, false) }
    s.state[i] = MoodOrbJarOrb.PILE
    s.hx[i] = s.x[i]
    s.hy[i] = s.y[i]
    s.fade[i] = 0.0
    s.slotScale[s.mood[i]] = 0.0
}

private fun added(s: MoodOrbJarSim, i: Int) {
    s.lastAdded = i
    s.events.add(MoodOrbJarEvent.ADDED + s.mood[i])
    // Shared element: a copy of the orb flies to the new entry's avatar.
    if (!s.reduced) {
        s.ghostT = 0.0
        s.ghostX = s.x[i]
        s.ghostY = s.y[i]
        s.ghostMood = s.mood[i]
        s.growT = -2.0 // the new card waits for the orb to arrive
    }
}

private fun undo(s: MoodOrbJarSim) {
    val i = s.lastAdded
    if (i < 0 || s.state[i] != MoodOrbJarOrb.PILE) return
    s.state[i] = MoodOrbJarOrb.LEAVING
    s.t[i] = 0.0
    s.fromX[i] = s.x[i]
    s.fromY[i] = s.y[i]
    s.lastAdded = -1
    s.events.add(MoodOrbJarEvent.REMOVED)
    emit(s, MoodOrbJarHaptic.UNDO)
}

internal fun applyInput(s: MoodOrbJarSim, g: MoodOrbJarGeometry, ev: MoodOrbJarInput) {
    when (ev) {
        is MoodOrbJarInput.UndoCommand -> {
            s.demoOn = false
            s.quiet = 0.0
            undo(s)
        }
        is MoodOrbJarInput.Add -> {
            s.demoOn = false
            s.quiet = 0.0
            val i = freeOrb(s)
            if (i < 0) return
            s.mood[i] = ev.mood
            s.x[i] = g.slotX[ev.mood]
            s.y[i] = g.slotY
            s.fade[i] = 1.0
            if (s.reduced) placeInstantly(s, g, i) else launch(s, g, i, 0.0, 0.0)
            if (s.reduced) added(s, i)
        }
        is MoodOrbJarInput.Down -> down(s, g, ev)
        is MoodOrbJarInput.Move -> move(s, g, ev)
        is MoodOrbJarInput.Up -> up(s, g, ev)
    }
}

private fun down(s: MoodOrbJarSim, g: MoodOrbJarGeometry, ev: MoodOrbJarInput.Down) {
    s.scripted = ev.script
    if (!s.scripted) {
        s.demoOn = false
        s.quiet = 0.0
    }
    s.downX = ev.x
    s.downY = ev.y
    s.downT = s.clock
    s.moved = 0.0
    for (m in MoodOrbJarMoods.indices) {
        if (hypot(ev.x - g.slotX[m], ev.y - g.slotY) < 30 * g.s && s.slotScale[m] > 0.9) {
            val i = freeOrb(s)
            if (i < 0) return
            s.held = i
            s.mood[i] = m
            s.state[i] = MoodOrbJarOrb.HELD
            s.x[i] = g.slotX[m]
            s.y[i] = g.slotY
            s.fade[i] = 1.0
            s.gdx = s.x[i] - ev.x
            s.gdy = s.y[i] - ev.y
            s.slotScale[m] = 0.0
            emit(s, MoodOrbJarHaptic.GRAB)
            return
        }
    }
    if (s.lastAdded >= 0 && ev.y > g.rowY && ev.y < g.rowY + g.rowH) {
        s.swiping = true
        s.swipeX0 = ev.x
    }
}

private fun move(s: MoodOrbJarSim, g: MoodOrbJarGeometry, ev: MoodOrbJarInput.Move) {
    s.moved = max(s.moved, hypot(ev.x - s.downX, ev.y - s.downY))
    if (s.held >= 0) {
        s.x[s.held] = ev.x + s.gdx
        s.y[s.held] = ev.y + s.gdy
    } else if (s.swiping) {
        val d = min(0.0, ev.x - s.swipeX0)
        s.rowX = if (d > -140 * g.s) d else -140 * g.s - (-(d + 140 * g.s)) * 0.3
    }
}

private fun up(s: MoodOrbJarSim, g: MoodOrbJarGeometry, ev: MoodOrbJarInput.Up) {
    if (s.held >= 0) {
        val i = s.held
        s.held = -1
        val tap = s.moved < 8 * g.s && s.clock - s.downT < 0.25
        if (ev.vy < -MoodOrbJarThrow.MIN_FLICK * g.s || tap) {
            if (s.reduced) {
                placeInstantly(s, g, i)
                added(s, i)
            } else {
                launch(s, g, i, ev.vx, ev.vy)
            }
        } else {
            s.state[i] = MoodOrbJarOrb.RETURNING
            s.fromX[i] = s.x[i]
            s.fromY[i] = s.y[i]
            s.t[i] = 0.0
        }
    } else if (s.swiping) {
        s.swiping = false
        if (s.rowX < -MoodOrbJarUndo.THRESHOLD * g.s) undo(s)
    }
    s.scripted = false
}

private fun runDemo(s: MoodOrbJarSim, g: MoodOrbJarGeometry, t0: Double, t1: Double) {
    val p = MoodOrbJarScript.pick
    val sx = g.slotX[MoodOrbJarScript.MOOD]
    if (t0 <= p[0] && p[0] < t1) applyInput(s, g, MoodOrbJarInput.Down(sx, g.slotY, script = true))
    if (t1 > p[0] && t0 < p[1] && s.held >= 0) {
        val u = clamp((min(t1, p[1]) - p[0]) / (p[1] - p[0]))
        val e = u * u
        applyInput(
            s,
            g,
            MoodOrbJarInput.Move(
                sx + MoodOrbJarScript.pickBy[0] * g.s * e,
                g.slotY + MoodOrbJarScript.pickBy[1] * g.s * e,
                0.0,
                0.0,
            ),
        )
    }
    if (t0 < p[1] && p[1] <= t1 && s.held >= 0) {
        applyInput(s, g, MoodOrbJarInput.Up(MoodOrbJarScript.release[0] * g.s, MoodOrbJarScript.release[1] * g.s))
    }
    val w = MoodOrbJarScript.swipe
    val rowCy = g.rowY + g.rowH / 2
    if (t0 <= w[0] && w[0] < t1) applyInput(s, g, MoodOrbJarInput.Down(g.w * 0.72, rowCy, script = true))
    if (t1 > w[0] && t0 < w[1] && s.swiping) {
        val u = easeInOut(clamp((min(t1, w[1]) - w[0]) / (w[1] - w[0])))
        applyInput(s, g, MoodOrbJarInput.Move(g.w * 0.72 + MoodOrbJarScript.SWIPE_BY * g.s * u, rowCy, 0.0, 0.0))
    }
    if (t0 < w[1] && w[1] <= t1 && s.swiping) applyInput(s, g, MoodOrbJarInput.Up(0.0, 0.0))
}

/** Restores every visible value at the loop seam, including tiny spring residues. */
private fun resetLoopRest(s: MoodOrbJarSim) {
    for (i in 0 until POOL) {
        if (i < PILE_N) {
            s.state[i] = MoodOrbJarOrb.PILE
            s.x[i] = s.hx[i]
            s.y[i] = s.hy[i]
            s.fade[i] = 1.0
        } else {
            s.state[i] = MoodOrbJarOrb.OFF
            s.fade[i] = 0.0
        }
        s.vx[i] = 0.0
        s.vy[i] = 0.0
        s.restT[i] = 0.0
        s.t[i] = 0.0
        s.spin[i] = 0.0
        s.sq[i] = 0.0
        s.sqv[i] = 0.0
        s.pvy[i] = 0.0
    }
    s.held = -1
    s.swiping = false
    s.rowX = 0.0
    s.rowV = 0.0
    s.lastAdded = -1
    s.lid = 0.0
    s.lidV = 0.0
    s.lidHold = 0.0
    s.slotScale.fill(1.0)
    s.ghostT = -1.0
    s.growT = -1.0
    s.rippleT = -1.0
    s.hopAt = 0
}

private fun hasTransientOrb(s: MoodOrbJarSim): Boolean {
    for (i in PILE_N until POOL) {
        if (s.state[i] != MoodOrbJarOrb.OFF) {
            return true
        }
    }
    return false
}

private fun fixedStep(s: MoodOrbJarSim, g: MoodOrbJarGeometry, autoPlay: Boolean) {
    val dt = MoodOrbJarMotion.STEP
    if (s.demoOn) {
        val t0 = s.demoT
        var t1 = t0 + dt
        runDemo(s, g, t0, t1)
        if (t1 * 1000 >= MoodOrbJarMotion.LOOP_MS) {
            t1 -= MoodOrbJarMotion.LOOP_MS / 1000
            resetLoopRest(s)
        }
        s.demoT = t1
    } else if (autoPlay && s.held < 0 && !s.swiping) {
        s.quiet += dt
        if (s.quiet * 1000 >= MoodOrbJarMotion.RESUME_MS) {
            if (s.lastAdded >= 0 && s.state[s.lastAdded] == MoodOrbJarOrb.PILE) {
                s.scripted = true
                undo(s)
                s.scripted = false
            } else if (!hasTransientOrb(s)) {
                resetLoopRest(s)
                s.demoOn = true
                s.demoT = 0.0
                s.quiet = 0.0
            }
        }
    }

    // Flight, return and departure are kinematic; landing and the pile are physics.
    val r = MoodOrbJarLayout.ORB * g.s
    var anyAir = false
    for (i in 0 until POOL) {
        val st = s.state[i]
        if (st == MoodOrbJarOrb.FLYING) {
            anyAir = true
            s.vy[i] += MoodOrbJarThrow.GRAVITY * g.s * dt
            s.x[i] += s.vx[i] * dt
            s.y[i] += s.vy[i] * dt
            // JS: Math.sign(vx || 1), so a standing still orb spins clockwise.
            s.spin[i] += MoodOrbJarThrow.SPIN * dt * sign(if (s.vx[i] == 0.0) 1.0 else s.vx[i])
            if (s.vy[i] > 0 && s.y[i] > g.mouthY + r && abs(s.x[i] - g.cx) < g.mouthHalf) {
                s.state[i] = MoodOrbJarOrb.SETTLING
                s.restT[i] = 0.0
                s.t[i] = -1.0 // not landed yet: the first impact gives one haptic
            }
            if (s.y[i] > g.h + r) {
                s.state[i] = MoodOrbJarOrb.OFF
                s.slotScale[s.mood[i]] = 0.0
            }
        } else if (st == MoodOrbJarOrb.RETURNING || st == MoodOrbJarOrb.LEAVING) {
            anyAir = anyAir || st == MoodOrbJarOrb.LEAVING
            s.t[i] += dt
            val tx = g.slotX[s.mood[i]]
            if (st == MoodOrbJarOrb.RETURNING) {
                val u = easeInOut(clamp(s.t[i] / 0.3))
                s.x[i] = s.fromX[i] + (tx - s.fromX[i]) * u
                s.y[i] = s.fromY[i] + (g.slotY - s.fromY[i]) * u
                if (u >= 1) {
                    s.state[i] = MoodOrbJarOrb.OFF
                    s.slotScale[s.mood[i]] = 1.0
                }
            } else if (s.reduced) {
                s.fade[i] = 1 - clamp(s.t[i] / 0.2)
                if (s.t[i] >= 0.2) s.state[i] = MoodOrbJarOrb.OFF
            } else if (s.t[i] < MoodOrbJarUndo.RISE) {
                // Straight up out of the jar, then an arc back to its slot in the picker.
                val u = easeInOut(s.t[i] / MoodOrbJarUndo.RISE)
                s.x[i] = s.fromX[i] + (g.cx - s.fromX[i]) * u
                s.y[i] = s.fromY[i] + (g.mouthY - 50 * g.s - s.fromY[i]) * u
            } else {
                val u = easeInOut(clamp((s.t[i] - MoodOrbJarUndo.RISE) / MoodOrbJarUndo.FLY))
                val ax = g.cx
                val ay = g.mouthY - 50 * g.s
                s.x[i] = ax + (tx - ax) * u
                s.y[i] = ay + (g.slotY - ay) * u - sin(PI * u) * 60 * g.s
                if (u >= 1) s.state[i] = MoodOrbJarOrb.OFF
            }
        }
    }

    for (i in 0 until POOL) s.pvy[i] = s.vy[i]
    stepJar(s, g, false)
    for (i in 0 until POOL) {
        if (!inJar(s.state[i])) continue
        // Soft bodies: a sudden stop squashes the orb, the spring overshoots into a stretch.
        val dv = s.pvy[i] - s.vy[i]
        if (dv > MoodOrbJarSoft.TRIGGER * g.s && !s.reduced) {
            s.sqv[i] += min(MoodOrbJarSoft.MAX, dv * MoodOrbJarSoft.PER_SPEED / g.s) * 30
            if (s.state[i] == MoodOrbJarOrb.SETTLING && s.t[i] < 0) {
                s.t[i] = 0.0
                s.sq[i] = max(s.sq[i], MoodOrbJarSoft.LANDING)
                val haptic = if (dv > MoodOrbJarSoft.HARD * g.s) {
                    MoodOrbJarHaptic.LAND_HARD
                } else if (dv > MoodOrbJarSoft.SOFT * g.s) {
                    MoodOrbJarHaptic.LAND
                } else {
                    MoodOrbJarHaptic.LAND_SOFT
                }
                emit(s, haptic)
                s.rippleT = 0.0
                s.rippleX = s.x[i]
                s.rippleY = s.y[i]
                s.rippleMood = s.mood[i]
            }
        }
        s.sqv[i] += (-MoodOrbJarSoft.STIFFNESS * s.sq[i] - MoodOrbJarSoft.DAMPING * s.sqv[i]) * dt
        s.sq[i] = max(-MoodOrbJarSoft.MAX, min(MoodOrbJarSoft.MAX, s.sq[i] + s.sqv[i] * dt))
    }
    // The loop clock makes the same orb hop at 1, 3, ... 11 seconds on every autoplay period.
    val loopTime = if (s.demoOn) s.demoT else s.clock
    val hop = floor((loopTime + MoodOrbJarHop.PHASE) / MoodOrbJarHop.EVERY).toInt()
    if (hop != s.hopAt) {
        s.hopAt = hop
        val index = (hop * 7) % PILE_N
        if (hop != 0 && !s.reduced && s.state[index] == MoodOrbJarOrb.PILE) {
            s.vy[index] -= MoodOrbJarHop.SPEED * g.s
        }
    }
    if (s.ghostT >= 0) {
        s.ghostT += dt
        if (s.ghostT > MoodOrbJarGhost.DURATION) {
            s.ghostT = -1.0
            s.growT = 0.0 // container morph: the card grows out of the avatar
        }
    }
    if (s.growT >= 0) s.growT += dt
    if (s.rippleT >= 0) {
        s.rippleT += dt
        if (s.rippleT > MoodOrbJarRipple.DURATION) s.rippleT = -1.0
    }
    for (i in 0 until POOL) {
        if (s.state[i] != MoodOrbJarOrb.SETTLING) continue
        val v = hypot(s.vx[i], s.vy[i])
        s.restT[i] = if (v < MoodOrbJarPile.REST_SPEED * g.s) s.restT[i] + dt else 0.0
        if (s.restT[i] >= MoodOrbJarPile.REST_TIME) {
            s.state[i] = MoodOrbJarOrb.PILE
            s.hx[i] = s.x[i]
            s.hy[i] = s.y[i]
            added(s, i)
        }
        if (s.y[i] < g.shoulderEnd) anyAir = true
    }
    for (i in 0 until POOL) {
        val hidden = s.state[i] == MoodOrbJarOrb.OFF || s.state[i] == MoodOrbJarOrb.LEAVING
        s.fade[i] = min(1.0, s.fade[i] + (if (hidden) 0.0 else dt / 0.2))
    }
    for (m in MoodOrbJarMoods.indices) {
        var out = false
        for (i in PILE_N until POOL) {
            if (s.mood[i] == m && (s.state[i] == MoodOrbJarOrb.HELD || s.state[i] == MoodOrbJarOrb.RETURNING)) out = true
        }
        if (!out && s.slotScale[m] < 1) s.slotScale[m] = min(1.0, s.slotScale[m] + dt / MoodOrbJarThrow.REGROW)
    }

    // Lid: hinged on its left edge, open while an orb is in the air near the mouth.
    // The lid stays lifted a moment after the orb lands, as in the source.
    s.lidHold = if (anyAir) MoodOrbJarLid.HOLD else max(0.0, s.lidHold - dt)
    val want = if (s.lidHold > 0 && !s.reduced) MoodOrbJarLid.OPEN else 0.0
    s.lidV += (-MoodOrbJarLid.STIFFNESS * (s.lid - want) - MoodOrbJarLid.DAMPING * s.lidV) * dt
    s.lid += s.lidV * dt
    // Row: springs back unless a finger holds it.
    if (!s.swiping) {
        s.rowV += (-MoodOrbJarUndo.ROW_STIFFNESS * s.rowX - MoodOrbJarUndo.ROW_DAMPING * s.rowV) * dt
        s.rowX += s.rowV * dt
    }
    s.clock += dt
}

internal fun stepSim(s: MoodOrbJarSim, g: MoodOrbJarGeometry, frameDt: Double, autoPlay: Boolean, uncapped: Boolean = false) {
    val delta = if (uncapped) frameDt else min(frameDt, MoodOrbJarMotion.MAX_FRAME_DELTA)
    s.acc = if (uncapped) s.acc + delta else min(s.acc + delta, MoodOrbJarMotion.STEP * MoodOrbJarMotion.MAX_STEPS)
    while (s.acc >= MoodOrbJarMotion.STEP) {
        fixedStep(s, g, autoPlay)
        s.acc -= MoodOrbJarMotion.STEP
    }
}

/** The solved launch from where a held orb is now, sampled as an aim arc: x, y pairs written to [out]; returns how many floats. */
internal fun aimArc(s: MoodOrbJarSim, g: MoodOrbJarGeometry, out: FloatArray): Int {
    if (s.held < 0) return 0
    val i = s.held
    val gr = MoodOrbJarThrow.GRAVITY * g.s
    val apex = min(s.y[i], g.mouthY) - MoodOrbJarThrow.APEX * g.s
    val vy = -sqrt(2 * gr * max(1.0, s.y[i] - apex))
    val t = (-vy + sqrt(vy * vy + 2 * gr * max(0.0, g.mouthY + 40 * g.s - s.y[i]))) / gr
    val vx = (g.cx - s.x[i]) / max(0.2, t)
    for (k in 1..MoodOrbJarAim.POINTS) {
        val tt = (t * k) / MoodOrbJarAim.POINTS
        out[(k - 1) * 2] = (s.x[i] + vx * tt).toFloat()
        out[(k - 1) * 2 + 1] = (s.y[i] + vy * tt + 0.5 * gr * tt * tt).toFloat()
    }
    return MoodOrbJarAim.POINTS * 2
}
