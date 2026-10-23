package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import kotlin.math.min

/**
 * What the simulation hands to the drawing each frame: the Kotlin form of the shared values that
 * PullCordTheme.tsx's `publish` sets. Plain fields, read by the draw lambdas, so a new frame
 * redraws without composing again.
 */
internal class PullCordThemeFrame(private val g: PullCordThemeGeometry) {
    /** The rope's points as x, y pairs. */
    val rope = DoubleArray(PullCordThemeTheme.Cord.points * 2)
    var angle = 0.0
    var cx = g.pivotX
    var cy = g.socketTop + PullCordThemeTheme.ReplicaArt.glassCenterSrc * PullCordThemeTheme.FIT_K * g.s
    var radius = 0.0
    var fade = 0.0
    var level = 0.0
    var neon = 1.0
    var themeNow = 0
    var reduced = false

    /** Reveal progress 0..1 including the scrub preview. */
    var frac = 0.0

    /** Seconds since the last theme commit. */
    var commit = 99.0
    var clock = 0.0

    init {
        for (i in 0 until PullCordThemeTheme.Cord.points) {
            rope[i * 2] = g.anchorX
            rope[i * 2 + 1] = g.anchorY + g.seg * i
        }
    }

    /** Which stile the bulb lights more: -1 when it swings fully left, 1 fully right. */
    val side: Double
        get() {
            val maxAngle = (PullCordThemeTheme.ReplicaArt.swingSrc * PullCordThemeTheme.FIT_K * g.s) / g.glassArm
            return (angle / maxAngle).coerceIn(-1.0, 1.0)
        }

    /** The reveal mask's radius, never below half a dp. */
    val revealRadius: Double
        get() = maxOf(0.5, radius)

    /** Copies the simulation's state; [tiltRoll] is the phone's roll in radians. Returns true when the theme changed. */
    fun publish(sim: PullCordThemeSim, tiltRoll: Double): Boolean {
        for (i in 0 until PullCordThemeTheme.Cord.points) {
            rope[i * 2] = sim.px[i]
            rope[i * 2 + 1] = sim.py[i]
        }
        reduced = sim.reduced
        // Phone tilt adds to the swing.
        angle = sim.bulbAngle() + (if (sim.reduced) 0.0 else tiltRoll * PullCordThemeMotion.Hero.tilt)
        cx = sim.glassCenterX()
        cy = sim.glassCenterY()
        // Reduce Motion: the light scene cross-fades instead of growing from the bulb.
        val reach = sim.revealReach(cx, cy)
        // Scrub preview: while the cord is pulled the other room bleeds in (or the light room shrinks back).
        val pulled = if (sim.armed && !sim.reduced) min(1.0, sim.e / (PullCordThemeMotion.Switch.detent * g.s)) else 0.0
        val preview = PullCordThemeMotion.Feel.preview * pulled * pulled
        val f = if (sim.light == 0) sim.rv + preview else sim.rv - preview
        radius = if (sim.reduced) (if (sim.rv > 0) reach else 0.0) else maxOf(0.0, f) * reach
        frac = f.coerceIn(0.0, 1.0)
        commit = if (sim.reduced) 99.0 else sim.commitT
        clock = sim.clock
        fade = if (sim.reduced) sim.rv else if (sim.rv > 0) 1.0 else 0.0
        level = sim.level
        neon = sim.igniteValue()
        if (sim.light != themeNow) {
            themeNow = sim.light
            return true
        }
        return false
    }
}
