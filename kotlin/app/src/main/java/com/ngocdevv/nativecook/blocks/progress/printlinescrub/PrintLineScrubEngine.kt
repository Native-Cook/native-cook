package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubMotion as M
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** The haptic beats (touch only; the autoplay loop is silent). */
internal object PrintLineScrubBeat {
    const val ONSET = 0
    const val TICK = 1
    const val LIMIT = 2
    const val SUCCESS = 3
}

/** What a screen reader says about the scrubber. */
internal data class PrintLineScrubA11y(val pct: Int, val layer: Int)

/**
 * The loop plays `lookAt`; a finger hands it to the live values (RN `PrintLineScrub`'s frame callback and gestures), all on the main
 * thread. Modes: 0 loop / rest, 1 finger down, 2 released (the line springs back to the live layer carrying the finger's velocity and
 * the layers it left hot cool).
 */
internal class PrintLineScrubEngine(val autoPlay: Boolean, val reduce: Boolean, val g: PrintLineScrubGeo) {
    private class Grip {
        var h0 = M.H_LIVE
        var moved = false
        var tickAt = 0.0
        var tickLayer = M.layerAt(M.H_LIVE)
        var atEnd = false
    }

    var haptic: (Int) -> Unit = {}
    var onArrive: () -> Unit = {}

    val h = PrintLineScrubAnim(M.H_LIVE)
    val duck = PrintLineScrubAnim(0.0)
    val yaw = PrintLineScrubAnim(0.0)
    val heat = PrintLineScrubAnim(0.0)
    val press = PrintLineScrubAnim(0.0)
    val held = PrintLineScrubAnim(0.0)
    var bead = 0.0
        private set
    var flash = -1.0
        private set
    var over = 0.0
        private set
    var tail = 0.0
        private set
    var speed = 0.0
        private set
    var gizDir = 1.0
        private set
    private var prevH = M.H_LIVE
    private var prevPill = g.pillX(M.H_LIVE)
    private var mode = 0
    private var loopT = 0.0
    var clock = 0.0
        private set
    private var touchedAt = -100.0
    private var arrived = 1
    private var arrivedAt = -100.0
    private val grip = Grip()

    /** the pill's screen-space geometry */
    fun cy(): Double = g.capsuleCy(h.value, duck.value)
    fun band(): Double = if (reduce) 4.0 else M.mix(M.BAND_MIN, M.BAND_MAX, M.clamp01(max(heat.value, abs(speed) / M.SPEED_FULL)))
    fun heatNow(): Double = if (reduce) 0.0 else heat.value

    /** Frame time pinned at t seconds of the loop (previews and checks). */
    fun frameAt(t: Double, dt: Double) {
        clock += dt
        applyLook(M.lookAt(t))
        inertia(dt)
    }

    private fun applyLook(k: PrintLineScrubLook) {
        h.set(k.h)
        duck.set(k.duck)
        yaw.set(k.yaw)
        heat.set(k.heat)
        press.set(k.press)
        held.set(k.held)
        bead = k.bead
        flash = k.flash
        over = k.over
    }

    fun frame(rawMs: Double) {
        val dt = min(rawMs, 50.0) / 1000
        val now = clock + dt
        clock = now
        h.step(dt)
        duck.step(dt)
        yaw.step(dt)
        heat.step(dt)
        press.step(dt)
        held.step(dt)
        val m = mode
        if (m == 0 && autoPlay && !reduce) {
            val t = loopT + dt
            loopT = t
            applyLook(M.lookAt(t))
        } else if (m == 0) {
            h.set(M.H_LIVE)
            duck.set(0.0)
            yaw.set(0.0)
            heat.set(0.0)
            press.set(0.0)
            held.set(0.0)
        } else {
            if (!reduce) bead += (dt * PI * 2) / M.BEAD_PERIOD
            if (m == 2) {
                val hv0 = abs(speed)
                if (arrived == 0 && abs(h.value - M.H_LIVE) < 0.004 && hv0 < 60) {
                    arrived = 1
                    arrivedAt = now
                    haptic(PrintLineScrubBeat.SUCCESS)
                    onArrive()
                }
                if (arrived == 1) {
                    val tau = now - arrivedAt
                    flash = if (tau < 0.6) tau / 0.6 else -1.0
                    over = if (tau < 0.7 && !reduce) 5 * exp(-tau * 7) * kotlin.math.cos(tau * 24) else 0.0
                }
                if (arrived == 1 && autoPlay && !reduce && now - touchedAt > M.IDLE_TO_LOOP && abs(h.value - M.H_LIVE) < 0.003 &&
                    duck.value < 0.6 && abs(yaw.value) < 0.01 && heat.value < 0.04 && held.value < 0.02
                ) {
                    mode = 0
                    loopT = 0.0
                }
            }
        }
        inertia(dt)
    }

    /** the dots' inertia: a fast scrub fans the loose dots into a tail behind the pill */
    private fun inertia(dt: Double) {
        val hNow = h.value
        val hv = (hNow - prevH) / max(dt, 0.001)
        prevH = hNow
        speed += (hv * g.perY - speed) * (1 - exp(-dt / 0.05))
        val pxNow = g.pillX(hNow)
        val pv = (pxNow - prevPill) / max(dt, 0.001)
        prevPill = pxNow
        val target = max(-M.DOTS_TAIL, min(M.DOTS_TAIL, (pv / g.s) * 0.03))
        tail += (target - tail) * (1 - exp(-dt / 0.09))
        val wantDir = if (abs(speed) > 220 && speed < 0) -1.0 else 1.0 // the arrow turns down while the line moves down the screen
        gizDir += (wantDir - gizDir) * (1 - exp(-dt / 0.08))
    }

    // ---- touch ----

    private fun stopAll() {
        h.cancel()
        duck.cancel()
        yaw.cancel()
        heat.cancel()
        press.cancel()
        held.cancel()
    }

    private fun release(vy: Double) {
        mode = 2
        touchedAt = clock
        arrived = 0
        val dist = abs(h.value - M.H_LIVE)
        val v = max(-1.5, min(1.5, -vy / g.perY))
        val heatNow = min(1.0, heat.value + dist * M.HEAT_GAIN)
        heat.set(heatNow)
        val tau = M.HEAT_TAU * (1 + abs(v * 500) / M.HEAT_TAU_V)
        if (reduce) {
            h.run(AnimStep.Timing(M.H_LIVE, 0.2))
            yaw.run(AnimStep.Timing(0.0, 0.2))
            duck.run(AnimStep.Timing(0.0, 0.2))
            press.run(AnimStep.Timing(0.0, 0.2))
            held.run(AnimStep.Delay(0.06), AnimStep.Timing(0.0, 0.14))
            heat.set(0.0)
            return
        }
        h.run(AnimStep.Spring(M.H_LIVE, M.SPRING_CATCH_UP, v))
        yaw.run(AnimStep.Spring(0.0, M.SPRING_YAW))
        duck.run(AnimStep.Delay(0.7), AnimStep.Spring(0.0, M.SPRING_DUCK))
        press.run(AnimStep.Spring(0.0, M.SPRING_TOUCH))
        held.run(AnimStep.Delay(0.76), AnimStep.Timing(0.0, 0.14))
        // Easing.out(Easing.exp): 1 - 2^(-10 t)
        heat.run(AnimStep.Timing(0.0, tau * 3.0, ease = { 1 - Math.pow(2.0, 10 * ((1 - it) - 1)) }))
    }

    fun inHandle(x: Double, y: Double): Boolean {
        val cyNow = cy()
        return x >= g.capL && x <= g.capL + g.capW && y >= cyNow - g.capH / 2 && y <= cyNow + g.capH / 2
    }

    fun panBegin() {
        stopAll()
        mode = 1
        touchedAt = clock
        arrived = 1
        flash = -1.0
        over = 0.0
        grip.h0 = h.value
        grip.moved = false
        grip.tickAt = clock
        grip.tickLayer = M.layerAt(h.value)
        grip.atEnd = false
        duck.run(AnimStep.Spring(if (reduce) 0.0 else M.DUCK_PT, M.SPRING_DUCK))
        press.run(AnimStep.Spring(1.0, M.SPRING_TOUCH))
        held.run(AnimStep.Timing(1.0, 0.12))
        haptic(PrintLineScrubBeat.ONSET)
    }

    fun panUpdate(tx: Double, ty: Double) {
        val raw = grip.h0 - ty / g.perY
        var hv = raw
        var end = false
        if (raw < 0) {
            hv = -M.rubber(-raw * g.perY, M.RUBBER_PT) / g.perY
            end = true
        } else if (raw > 1) {
            hv = 1 + M.rubber((raw - 1) * g.perY, M.RUBBER_PT) / g.perY
            end = true
        }
        h.set(hv)
        if (!reduce) {
            val y = max(-M.YAW_MAX, min(M.YAW_MAX, tx * M.YAW_PER_PT))
            yaw.set((y * PI) / 180)
        }
        val layer = M.layerAt(M.clamp01(hv))
        val now = clock
        if (floor(layer.toDouble() / M.TICK_EVERY) != floor(grip.tickLayer.toDouble() / M.TICK_EVERY) && now - grip.tickAt > 0.06) {
            haptic(PrintLineScrubBeat.TICK)
            grip.moved = true
            grip.tickAt = now
            grip.tickLayer = layer
            grip.atEnd = end
        } else if (end && !grip.atEnd) {
            haptic(PrintLineScrubBeat.LIMIT)
            grip.moved = true
            grip.atEnd = true
        } else if (!end && grip.atEnd) {
            grip.atEnd = false
        }
    }

    /** The finger lifted or the touch was cancelled; `vy` in dp per second. */
    fun panFinalize(vy: Double) {
        release(vy)
    }

    // ---- screen reader ----

    fun a11yStep(dir: Int): PrintLineScrubA11y {
        stopAll()
        mode = 2
        touchedAt = clock
        arrived = 0
        val hh = M.clamp01(h.value + dir * 0.05)
        h.run(AnimStep.Timing(hh, 0.2), AnimStep.Delay(1.8), AnimStep.Spring(M.H_LIVE, M.SPRING_CATCH_UP))
        heat.run(AnimStep.Timing(min(1.0, heat.value + 0.3), 0.12), AnimStep.Delay(2.0), AnimStep.Timing(0.0, 0.9))
        duck.run(AnimStep.Spring(if (reduce) 0.0 else M.DUCK_PT, M.SPRING_DUCK), AnimStep.Delay(2.4), AnimStep.Spring(0.0, M.SPRING_DUCK))
        held.run(AnimStep.Timing(1.0, 0.12), AnimStep.Delay(2.5), AnimStep.Timing(0.0, 0.14))
        return PrintLineScrubA11y(round(M.percentAt(hh)).toInt(), M.layerAt(hh))
    }
}

