// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SharedValue
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withTiming

/**
 * `use-readout-legibility.ts`: the percentage reads as one color at a time —
 * light glyphs on the empty well, dark glyphs once the light covers more than
 * half of the glyph run (back to light below 40%). The whole number swaps
 * with a 140ms crossfade, so a light edge never splits a digit into two colors.
 */
class ReadoutLegibility(readout: () -> Double, fill: () -> Double, reduced: Boolean) {
    private val numberRun = { glyphRun(jsRound(readout()), G.Readout.size) }
    private val coverage = {
        runCoverage(
            G.Track.x + G.Track.width * fill(),
            G.Readout.x + G.Readout.width,
            numberRun(),
        )
    }
    val numberInk = SharedValue(0.0)

    private val reaction = AnimationEngine.reaction({ coverage() }) { c, previous ->
        val dark = numberInk.get() >= 0.5
        val target = inkTarget(c, dark)
        if (previous == null) numberInk.set(target.toDouble())
        else if ((target == 1) != dark) {
            numberInk.set(withTiming(target.toDouble(), if (reduced) 0.0 else 140.0, Motion.easeOut))
        }
    }

    fun dispose() = AnimationEngine.removeReaction(reaction)
}
