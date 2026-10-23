package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import kotlin.math.*

// Scripted and real fingers share inputTear; resumption keeps the current stable phase.
private val DEMO_LINE = sampleTearLine()

internal fun initialDemo() = TearDemo()

internal fun initialEngine(reduced: Boolean = false) = TearEngine(initialTear(reduced), initialDemo())

internal fun lineHeight(front: Double): Double {
    val at = clamp(front / TEAR.step, 0.0, TEAR.columns - 1.0)
    val low = floor(at)
    val high = min(low + 1.0, TEAR.columns - 1.0)
    val value = DEMO_LINE[(low).toInt()] + (DEMO_LINE[(high).toInt()] - DEMO_LINE[(low).toInt()]) * (at - low)
    return TEAR.wander * atanh(clamp(value / TEAR.wander, -0.99, 0.99))
}

internal fun segmentAt(t: Double): Double {
    if (t >= 1.4 && t < 2.15) return 1.0
    if (t >= 2.7 && t < 3.55) return 2.0
    if (t >= 5.3 && t < 5.48) return 3.0
    if (t >= 7.2 && t < 10.2) return 4.0
    if (t >= 10.5 && t < 10.56) return 5.0
    return 0.0
}

internal fun finger(s: TearState, d: TearDemo, dt: Double): Unit {
    val t = d.time
    val segment = segmentAt(t + 1e-9)
    if (segment != d.segment) {
        if (d.segment != 0.0 && s.down) {
            val x = (if (d.segment == 1.0) PACK.x + PACK.width * 0.46 else (if (d.segment == 2.0) PACK.x + PACK.width else d.startX))
            val y = (if (d.segment <= 2.0) PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x) else (if (d.segment == 3.0) d.startY - 48.0 else d.startY))
            inputTear(s, TearInput(type = "move", x = x, y = y, vx = (x - d.lastX) / dt, vy = (y - d.lastY) / dt))
            inputTear(s, TearInput(type = "up", vx = s.vx, vy = s.vy))
        }
        d.segment = segment
        if (segment == 0.0) return
        if (segment == 1.0 || segment == 2.0) {
            d.startFront = s.pose.tearFront
            d.startX = PACK.x + d.startFront + ((if (segment == 1.0) 2.0 else 0.0))
            d.startY = PACK.y + TEAR.y + s.lineEnd
            d.grabOffset = d.startY - PACK.y - TEAR.y - s.lineEnd
        } else {
            d.startX = s.pose.cardX
            d.startY = (if (segment == 3.0) min(s.pose.cardY, PACK.y + TEAR.y - 24.0) else s.pose.cardY)
        }
        d.lastX = d.startX
        d.lastY = d.startY
        inputTear(s, TearInput(type = "down", x = d.startX, y = d.startY, silent = true))
    }
    if (segment == 0.0) return
    var x = d.startX
    var y = d.startY
    if (segment == 1.0) {
        val u = clamp((t - 1.4) / 0.75)
        x += (PACK.x + PACK.width * 0.46 - x) * (1.0 - cos(PI * u)) / 2.0
        y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x) * clamp(u / 0.12)
    } else if (segment == 2.0) {
        val u = clamp((t - 2.7) / 0.85)
        val remaining = PACK.width - d.startFront
        val power = max(1.0, 500.0 * 0.85 / max(remaining, 1.0))
        x += remaining * u.pow(power)
        y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x)
    } else if (segment == 3.0) y -= 48.0 * clamp((t - 5.3) / 0.18) else if (segment == 4.0) {
        val angle = 2.0 * PI * clamp((t - 7.2) / 3.0)
        x += 110.0 * 0.24 * sin(angle)
        y -= 140.0 * 0.16 * sin(2.0 * angle)
    }
    inputTear(s, TearInput(type = "move", x = x, y = y, vx = (x - d.lastX) / dt, vy = (y - d.lastY) / dt))
    d.lastX = x
    d.lastY = y
}

internal fun actEngine(engine: TearEngine, input: TearInput): TearEngine {
    val sim = engine.sim
    val demo = engine.demo
    if (!demo.paused && sim.down) inputTear(sim, TearInput("up", cancelled = true))
    demo.paused = true
    demo.quiet = 0.0
    demo.segment = 0.0
    if (input.type == "down") demo.realDown = true
    if (input.type == "up") demo.realDown = false
    inputTear(sim, input)
    return engine
}

internal fun resumeAt(s: TearState): Double {
    if (s.phase == "sealed") return 0.8
    if (s.phase == "tearing") return 2.7
    if (s.phase == "peek") return 5.0
    if (s.phase == "revealed") return 9.9
    return -1.0
}

internal fun stepEngine(engine: TearEngine, elapsed: Double, autoPlay: Boolean): TearEngine {
    val sim = engine.sim
    val demo = engine.demo
    demo.remainder += clamp(elapsed, 0.0, MOTION.maxElapsed)
    while (demo.remainder >= MOTION.step - 1e-9) {
        val dt = MOTION.step
        if (demo.paused) {
            if (!demo.realDown) demo.quiet += dt
            val next = resumeAt(sim)
            if (autoPlay && !demo.realDown && demo.quiet >= MOTION.resumeDelay && next >= 0.0) {
                demo.paused = false
                demo.time = next
                demo.segment = 0.0
                sim.clock = next
            }
        }
        if (autoPlay && !demo.paused) {
            finger(sim, demo, dt)
            tickTear(sim, dt)
            demo.time += dt
            if (demo.time >= MOTION.loop - 1e-9) {
                demo.time = 0.0
                demo.segment = 0.0
                sim.clock = 0.0
            }
        } else tickTear(sim, dt)
        demo.remainder = max(0.0, demo.remainder - dt)
    }
    return engine
}
