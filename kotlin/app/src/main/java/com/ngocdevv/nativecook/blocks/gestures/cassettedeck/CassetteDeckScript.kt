package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Script
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The scripted finger of the autoplay: the same inputs a hand gives (RN `scriptInput`, `loopScales`). */
internal object CassetteDeckScript {
    /** Steps in one loop of the autoplay. */
    val loopSteps: Int = (CassetteDeckMotion.LOOP_MS / 1000.0 / CassetteDeckMotion.STEP).roundToInt()

    private fun within(t: Double, window: DoubleArray): Int = if (t >= window[0] && t < window[1]) 1 else 0

    fun input(sim: CassetteDeckSim, t: Double) {
        sim.held[0] = within(t, Script.play)
        sim.held[1] = within(t, Script.pause)
        sim.held[2] = within(t, Script.next)
        sim.held[3] = 0
        sim.held[4] = within(t, Script.ff)
        val u = (t - Script.Drag.start) / Script.Drag.time
        if (u >= 0.0 && u < 1.0) {
            val p = 1.0 - Script.Drag.dist * u.pow(Script.Drag.power)
            val vp = (-Script.Drag.dist * Script.Drag.power * u.pow(Script.Drag.power - 1.0)) / Script.Drag.time
            sim.dragTo(p, vp, false)
        } else if (sim.dragging && u >= 1.0) {
            sim.dragTo(1.0 - Script.Drag.dist, (-Script.Drag.dist * Script.Drag.power) / Script.Drag.time, true)
        }
    }

    /**
     * Runs one loop of the script ahead of time and scales each reel's turn so it ends on a whole tooth (60 degrees):
     * the reels then look the same at the seam and the preview loops without a jump.
     */
    val loopScales: DoubleArray by lazy {
        val sim = CassetteDeckSim()
        for (step in 0 until loopSteps) {
            input(sim, step * CassetteDeckMotion.STEP)
            sim.stepSim(CassetteDeckMotion.STEP, false)
        }
        val scales = DoubleArray(CassetteDeckSim.SLOTS * 2)
        for (i in scales.indices) {
            val a = sim.ang[i]
            scales[i] = if (a < 0.01) 1.0 else max(1.0, cassetteDeckRound(a / CassetteDeckSim.SECTOR)) * CassetteDeckSim.SECTOR / a
        }
        return@lazy scales
    }
}
