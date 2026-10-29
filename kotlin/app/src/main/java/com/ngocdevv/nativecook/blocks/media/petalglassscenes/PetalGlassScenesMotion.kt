package com.ngocdevv.nativecook.blocks.media.petalglassscenes

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing and touch model of Petal Glass Scenes, ported from the Swift reference. Every value is a
 * pure function of the loop time (autoPlay) or of the clock since a touch event, so the loop's last
 * frame equals its first.
 */
object PetalGlassScenesMotion {
    const val LOOP = 9.6
    const val SCENES = 3
    /** Each scene owns 3.2 s of the loop: it rests, then the next one grows out of the glass. */
    const val SCENE_LENGTH = 3.2
    const val CHANGE_AT = 1.8
    const val CHANGE_LENGTH = 1.4
    /** Reduced motion: the scenes cross-fade. */
    const val FADE_LENGTH = 0.4

    /** What one frame shows: scene [from] lies under, scene [to] grows out of the card (p 0 … 1). */
    class Frame(
        var from: Int = 0,
        var to: Int = 0,
        var p: Double = 0.0,
        var theta: Double = 0.0, // drift phase, 2π per loop
        var fade: Double = -1.0, // cross-fade 0 … 1 instead of the front (negative: none)
        var offsetX: Double = 0.0, // the card's drag offset, units
        var offsetY: Double = 0.0,
    )

    class Fx(val alpha: Double, val shift: Double, val blur: Double)

    // Curves

    fun mod(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(max(v, lo), hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeInCubic(x: Double) = x * x * x
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Wraps an angle to −π … π. */
    fun wrap(a: Double) = mod(a + PI, 2 * PI) - PI

    // The scene change (p = 0 … 1 over 1.4 s)

    /**
     * Where the front is, in units from the card's edge: from the card's centre (−124) to its rim,
     * a short hold there, then out past the farthest corner of the screen.
     */
    fun front(p: Double, reach: Double): Double {
        if (p < 0.26) return -124 + 124 * easeOutCubic(p / 0.26)
        if (p < 0.36) return 0.0
        val x = seg(p, 0.36, 1.0)
        return reach * x * x * (3 - 2 * x)
    }

    /** Strength of the lens bulge along the front. */
    fun bulge(p: Double) = sin(PI * clamp(p))

    /** Amplitude of the glass's liquid warp, units. */
    fun warp(p: Double) = 9 + 16 * sin(PI * clamp(p))

    /** The card gives a little as the front reaches its rim, then swells as it leaves. */
    fun cardScale(p: Double) =
        1 - 0.03 * sin(PI * seg(p, 0.06, 0.44)) + 0.012 * sin(PI * seg(p, 0.44, 1.0))

    /** The text of the scene going out. */
    fun textOut(p: Double, delay: Double = 0.0): Fx {
        val e = easeInCubic(seg(p, 0.24 + delay, 0.40 + delay))
        return Fx(1 - e, 18 * e, 6 * e)
    }

    /** The text of the scene coming in. */
    fun textIn(p: Double, delay: Double = 0.0): Fx {
        val e = easeOutCubic(seg(p, 0.40 + delay, 0.72 + delay))
        return Fx(e, -18 * (1 - e), 6 * (1 - e))
    }

    /** The loop's frame at loop time t. */
    fun auto(t: Double): Frame {
        val time = mod(t, LOOP)
        val k = min((time / SCENE_LENGTH).toInt(), SCENES - 1)
        val u = time - k * SCENE_LENGTH
        return Frame(from = k, to = (k + 1) % SCENES, p = seg(u, CHANGE_AT, CHANGE_AT + CHANGE_LENGTH),
            theta = 2 * PI * time / LOOP)
    }

    // The card's drag

    /** Rubber band: the card follows the finger less and less. */
    fun rubber(x: Double) = x / (1 + abs(x) / 180)
    fun unrubber(o: Double): Double {
        val a = min(abs(o), 170.0)
        return (if (o < 0) -1 else 1) * a / (1 - a / 180)
    }
    fun rubberSlope(x: Double) = 1 / (1 + abs(x) / 180).pow(2)

    /** A released spring (response 0.5 s, damping 0.72) from x0 moving at v0. */
    fun spring(tau: Double, x0: Double, v0: Double): Double {
        if (tau <= 0) return x0
        val z = 0.72
        val w = 2 * PI / 0.5
        val wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (x0 * cos(wd * tau) + (v0 + z * w * x0) / wd * sin(wd * tau))
    }
}

/**
 * What the finger did. Auto → touch makes it manual; 2 s after the last touch (and after the scene
 * change it started) the loop resumes at the shown scene's rest, its drift eased back.
 */
class PetalGlassScenesTouch {
    private val m = PetalGlassScenesMotion

    var manual = false
    var loopStart = 0.0 // clock at loop time 0
    var resumedAt = -100.0
    var thetaLag = 0.0

    var scene = 0
    var changeFrom = 0
    var changeStart = -100.0
    var fading = false

    var driftBase = 0.0
    var manualSince = 0.0

    var touching = false
    var lastTouch = -100.0
    var dragX = 0.0
    var dragY = 0.0
    var baseX = 0.0
    var baseY = 0.0
    var releasedAt = -100.0
    var releaseX = 0.0
    var releaseY = 0.0
    var releaseVx = 0.0
    var releaseVy = 0.0
    private var haptic = -100.0
    private var hapticTaken = -100.0

    private val changeEnd get() = changeStart + if (fading) m.FADE_LENGTH else m.CHANGE_LENGTH
    val idleUntil get() = max(lastTouch, changeEnd) + 2.2

    /** Applies a pending resume (pure in effect: the same clock always gives the same state). */
    private fun resolve(clock: Double, autoPlay: Boolean) {
        if (!manual || !autoPlay || touching) return
        val at = max(lastTouch, changeEnd) + 2
        if (clock < at) return
        manual = false
        loopStart = at - scene * m.SCENE_LENGTH
        resumedAt = at
        val manualTheta = 2 * PI * (driftBase + at - manualSince) / m.LOOP
        val loopTheta = 2 * PI * scene * m.SCENE_LENGTH / m.LOOP
        thetaLag = m.wrap(manualTheta - loopTheta)
    }

    fun frame(clock: Double, autoPlay: Boolean, reduced: Boolean): PetalGlassScenesMotion.Frame {
        resolve(clock, autoPlay)
        val f: PetalGlassScenesMotion.Frame
        if (reduced) {
            f = PetalGlassScenesMotion.Frame(from = scene, to = scene)
            val p = m.seg(clock, changeStart, changeStart + m.FADE_LENGTH)
            if (p < 1) { f.from = changeFrom; f.p = p; f.fade = m.easeInOutCubic(p) }
            return f
        }
        if (!manual && autoPlay) {
            f = m.auto(clock - loopStart)
            val ease = m.easeInOutCubic(m.seg(clock, resumedAt, resumedAt + 1.2))
            f.theta += thetaLag * (1 - ease)
        } else {
            f = PetalGlassScenesMotion.Frame(from = scene, to = scene)
            val p = m.seg(clock, changeStart, changeStart + m.CHANGE_LENGTH)
            if (p < 1) { f.from = changeFrom; f.p = p }
            f.theta = 2 * PI * (driftBase + clock - manualSince) / m.LOOP
        }
        f.offsetX = offsetX(clock)
        f.offsetY = offsetY(clock)
        return f
    }

    fun offsetX(clock: Double) = if (touching) m.rubber(dragX) else m.spring(clock - releasedAt, releaseX, releaseVx)
    fun offsetY(clock: Double) = if (touching) m.rubber(dragY) else m.spring(clock - releasedAt, releaseY, releaseVy)

    fun begin(clock: Double, autoPlay: Boolean, reduced: Boolean) {
        val f = frame(clock, autoPlay, reduced)
        val ox = offsetX(clock)
        val oy = offsetY(clock)
        if (!manual && autoPlay && !reduced) {
            // Take over the loop where it is, a scene change in progress included.
            manual = true
            driftBase = f.theta / (2 * PI) * m.LOOP
            manualSince = clock
            if (f.p > 0) {
                changeFrom = f.from
                scene = f.to
                changeStart = clock - f.p * m.CHANGE_LENGTH
                fading = false
            } else {
                scene = f.from
                changeStart = -100.0
            }
        } else if (!manual) {
            manual = true
            driftBase = clock
            manualSince = clock
        }
        touching = true
        // Pick the card up where the spring left it.
        baseX = m.unrubber(ox)
        baseY = m.unrubber(oy)
        dragX = baseX
        dragY = baseY
        lastTouch = clock
    }

    fun move(clock: Double, dx: Double, dy: Double) {
        dragX = baseX + dx
        dragY = baseY + dy
        lastTouch = clock
    }

    /** Ends a drag; returns true when it changed the scene. */
    fun end(clock: Double, dx: Double, dy: Double, vx: Double, vy: Double, reduced: Boolean): Boolean {
        touching = false
        lastTouch = clock
        releasedAt = clock
        releaseX = m.rubber(dragX)
        releaseY = m.rubber(dragY)
        releaseVx = (vx * m.rubberSlope(dragX)).coerceIn(-1500.0, 1500.0)
        releaseVy = (vy * m.rubberSlope(dragY)).coerceIn(-1500.0, 1500.0)
        val horizontal = abs(dx) > abs(dy)
        val busy = clock < changeStart + (if (fading) m.FADE_LENGTH else m.CHANGE_LENGTH) * 0.6
        if (!horizontal || busy || !(abs(dx) > 80 || abs(vx) > 650)) return false
        val step = if ((if (abs(dx) > 80) dx else vx) < 0) 1 else m.SCENES - 1
        changeFrom = scene
        scene = (scene + step) % m.SCENES
        changeStart = clock
        fading = reduced
        haptic = clock
        return true
    }

    /** True once per haptic asked for. */
    fun takeHaptic(): Boolean {
        if (haptic <= hapticTaken) return false
        hapticTaken = haptic
        return true
    }
}
