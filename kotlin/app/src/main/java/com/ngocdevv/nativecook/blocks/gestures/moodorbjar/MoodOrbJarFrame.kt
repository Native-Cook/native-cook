package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Floats per orb in [MoodOrbJarFrame.orbs]: x, y, spin, scale, opacity, mood, squash, vx, vy, flying. */
internal const val ORB_STRIDE = 10

/**
 * What the drawing reads each frame, copied out of the simulation (`publish` of the React Native block). The canvases
 * read [tick] in their draw lambdas, so a frame redraws without recomposing the screen; only the count, the swipe
 * offset and the card's growth are Compose state for the list.
 */
internal class MoodOrbJarFrame(initialCount: Int) {
    val orbs = FloatArray(POOL * ORB_STRIDE)
    val slots = FloatArray(MoodOrbJarMoods.size) { 1f }

    /** The shared element: x, y, scale, mood, visible. */
    val ghost = floatArrayOf(0f, 0f, 1f, 0f, 0f)

    /** The landing ripple: x, y, progress, mood, visible. */
    val ripple = FloatArray(5)
    val aim = FloatArray(MoodOrbJarAim.POINTS * 2)
    var aimSize = 0
    var tilt = 0f
    var lid = 0f

    val tick = mutableLongStateOf(0L)
    val rowX = mutableFloatStateOf(0f)

    /** The new entry card's container morph, 0..1. */
    val grow = mutableFloatStateOf(1f)
    val count = mutableIntStateOf(initialCount)

    /** Reading this in a draw lambda subscribes it to every frame. */
    fun readTick(): Long = tick.longValue

    fun publish(s: MoodOrbJarSim, g: MoodOrbJarGeometry, avatarX: Double, avatarY: Double) {
        val small = MoodOrbJarLayout.PICKER_ORB / MoodOrbJarLayout.ORB
        for (i in 0 until POOL) {
            val st = s.state[i]
            var scale = 1.0
            if (st == MoodOrbJarOrb.HELD) {
                scale = small * MoodOrbJarThrow.LIFT
            } else if (st == MoodOrbJarOrb.RETURNING) {
                scale = small
            } else if (st == MoodOrbJarOrb.LEAVING) {
                scale = 1 - (1 - small) * min(1.0, s.t[i] / (MoodOrbJarUndo.RISE + MoodOrbJarUndo.FLY))
            }
            val k = i * ORB_STRIDE
            orbs[k] = s.x[i].toFloat()
            orbs[k + 1] = s.y[i].toFloat()
            orbs[k + 2] = s.spin[i].toFloat()
            orbs[k + 3] = scale.toFloat()
            orbs[k + 4] = (if (st == MoodOrbJarOrb.OFF) 0.0 else s.fade[i]).toFloat()
            orbs[k + 5] = s.mood[i].toFloat()
            orbs[k + 6] = s.sq[i].toFloat()
            orbs[k + 7] = s.vx[i].toFloat()
            orbs[k + 8] = s.vy[i].toFloat()
            orbs[k + 9] = if (st == MoodOrbJarOrb.FLYING) 1f else 0f
        }
        // Shared element: the landed orb's copy flies to the new entry's avatar on an arc.
        if (s.ghostT >= 0) {
            // Lifts straight out of the jar, then arcs (ease-in-out) to the new entry's avatar, shrinking to its size.
            val u = min(1.0, s.ghostT / MoodOrbJarGhost.DURATION)
            val l = min(1.0, u / MoodOrbJarGhost.LIFT)
            val lift = 1 - (1 - l).pow(3)
            val sx = s.ghostX
            val sy = s.ghostY - MoodOrbJarGhost.RISE * g.s * lift
            val a = max(0.0, (u - MoodOrbJarGhost.LIFT) / (1 - MoodOrbJarGhost.LIFT))
            val e = if (a < 0.5) 4 * a * a * a else 1 - (-2 * a + 2).pow(3) / 2
            val end = MoodOrbJarLayout.ENTRY_ORB / MoodOrbJarLayout.ORB
            ghost[0] = (sx + (avatarX - sx) * e).toFloat()
            ghost[1] = (sy + (avatarY - sy) * e - sin(PI * e) * MoodOrbJarGhost.ARC * g.s).toFloat()
            ghost[2] = (1 + 0.08 * lift * (1 - e) + (end - 1) * e).toFloat()
            ghost[3] = s.ghostMood.toFloat()
            ghost[4] = 1f
        } else {
            ghost[0] = 0f
            ghost[1] = 0f
            ghost[2] = 1f
            ghost[3] = 0f
            ghost[4] = 0f
        }
        grow.floatValue = if (s.reduced || s.growT == -1.0) {
            1f
        } else if (s.growT < 0) {
            0f
        } else {
            min(1.0, s.growT / MoodOrbJarGhost.GROW).toFloat()
        }
        if (s.rippleT < 0) {
            ripple.fill(0f)
        } else {
            ripple[0] = s.rippleX.toFloat()
            ripple[1] = s.rippleY.toFloat()
            ripple[2] = (s.rippleT / MoodOrbJarRipple.DURATION).toFloat()
            ripple[3] = s.rippleMood.toFloat()
            ripple[4] = 1f
        }
        aimSize = aimArc(s, g, aim)
        tilt = s.tilt.toFloat()
        var n = 0
        for (i in 0 until POOL) {
            if (s.state[i] == MoodOrbJarOrb.PILE) n++
        }
        if (n != count.intValue) count.intValue = n
        for (m in slots.indices) slots[m] = s.slotScale[m].toFloat()
        lid = s.lid.toFloat()
        rowX.floatValue = s.rowX.toFloat()
    }
}
