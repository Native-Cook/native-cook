package com.ngocdevv.nativecook.blocks.charts.growthodometertimeline

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.growthodometertimeline.GrowthSimulation.Companion.smooth

/** Same five-lobe field as the stage, without the cosmetic noise warp, for mote visibility. */
internal object GrowthHalo {
    fun field(x: Double, y: Double, width: Double, height: Double, cy: Double, x0: Double, wheel: GrowthSimulation): Double {
        val level = wheel.halo.coerceIn(0.0, 1.0)
        val px = x * 402 / width
        val py = (y - cy) * 874 / height
        val breathing = if (wheel.reduced) 1.0 else 1 + .03 * level * sin(wheel.time * 1.3823)
        val growth = (1 + .10 * wheel.surge) * breathing
        val crownScale = (.45 + .55 * level) * growth
        var field = (.1 + 1.5 * smooth(0.0, .55, level)) * lobe(px, py, 430.0, -250.0, 135 * crownScale, 210 * crownScale)
        val lower = smooth(.5, 1.0, level)
        val coreScale = (.3 + .7 * level) * growth
        field += 1.1 * lower * lobe(px, py, 452.0, -120 + 150 * level, 95 * coreScale, 170 * coreScale)
        field += .45 * smooth(.25, 1.0, level) * lobe(px, py, 455.0, -110.0, 60 * growth, 240 * growth)
        field += .3 * lower * lobe(px, py, 440.0, 250.0, 120 * growth, 200 * growth)
        field *= 1 - smooth(150.0, 400.0, py)
        field += .12 * smooth(.1, 1.0, level) * lobe(px, py, 430.0, -40.0, 240.0, 420.0) * (1 - .5 * smooth(230.0, 330.0, py))
        val bayX = (x - x0 - 60) / 90
        val bayY = (y - cy) / 100
        field *= 1 - .78 * exp(-(bayX * bayX + bayY * bayY))
        val inner = 220 + 84 * exp(-py * py / 24000)
        field *= smooth(inner, inner + 65, px)
        field *= 1 + .38 * wheel.surge + .5 * max(0.0, wheel.p - 12)
        return 1.1 * (1 - exp(-1.35 * field))
    }

    private fun lobe(x: Double, y: Double, cx: Double, cy: Double, rx: Double, ry: Double): Double {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return exp(-(dx * dx + dy * dy))
    }
}
