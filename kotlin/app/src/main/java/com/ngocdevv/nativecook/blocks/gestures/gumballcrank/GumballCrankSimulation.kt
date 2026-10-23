package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class GumballCrankPhase { Ready, Dispensing, Reward, Collecting }
internal enum class GumballCrankHaptic { Tick, Strain, Drop, Open, Collect }
internal data class GumballCrankCapsulePose(val x: Double, val y: Double, val scale: Double, val rotation: Double, val split: Double, val alpha: Double)

internal class GumballCrankSimulation(reduced: Boolean) {
    companion object { private const val TAU = PI * 2.0; private const val QUARTER = PI / 2.0 }

    var reduced = reduced; private set
    var angle = 0.0; private set
    var wobble = 0.0; private set
    var phase = GumballCrankPhase.Ready; private set
    var phaseTime = 0.0; private set
    var card = 0.0; private set
    var scrim = 0.0; private set
    var press = 0.0; private set
    var wallet = 1240.0; private set
    var walletPrevious = 1240.0; private set
    var walletMix = 1.0; private set
    var walletBump = 0.0; private set
    var hint = 0; private set
    var hintFrom = 0; private set
    var hintMix = 1.0; private set
    var ghostX = 0.0; private set
    var ghostY = 0.0; private set
    var ghostAlpha = 0.0; private set
    var ghostPress = 0.0; private set
    var detent = 0; private set
    var moving = true; private set

    val ballX: DoubleArray
    val ballY: DoubleArray
    val ballR: DoubleArray
    val ballColor: IntArray
    private val homeX: DoubleArray
    private val homeY: DoubleArray
    private val ballVX: DoubleArray
    private val ballVY: DoubleArray
    private var base = 0.0
    private var goal = 0.0
    private var lastPointerAngle = 0.0
    private var grabbed = false
    private var pressing = false
    private var wobbleVelocity = 0.0
    private var cardVelocity = 0.0
    private var walletVelocity = 0.0
    private var walletMixDuration = 0.15
    private var demoRunning = false
    private var demoTime = 0.0
    private var quietTime = 0.0
    private var scriptDown = false
    private val events = ArrayList<GumballCrankHaptic>(8)

    init {
        val pile = settlePile()
        ballX = pile.x.copyOf(); ballY = pile.y.copyOf(); ballR = pile.r; ballColor = pile.c
        homeX = pile.x; homeY = pile.y
        ballVX = DoubleArray(ballX.size); ballVY = DoubleArray(ballX.size)
    }

    val accessibilityDescription: String
        get() = when (phase) {
            GumballCrankPhase.Reward -> "Day 7 reward, 50 coins. Collect reward."
            GumballCrankPhase.Collecting -> "Collecting 50 coins."
            else -> if (hint == 2) GumballCrankCopy.Done else GumballCrankCopy.Hint
        }
    val accessibilityAction: String get() = if (phase == GumballCrankPhase.Reward) "Collect reward" else "Turn the handle one quarter"

    fun start(autoPlay: Boolean, reduceMotion: Boolean) {
        reduced = reduceMotion; demoRunning = autoPlay; demoTime = 0.0; quietTime = 0.0; toRest()
    }

    fun step(dt: Double, autoPlay: Boolean) {
        if (!demoRunning && autoPlay) {
            quietTime += dt
            if (quietTime >= 2.0) { toRest(); demoTime = 0.0; demoRunning = true }
        }
        if (demoRunning && autoPlay) {
            val old = demoTime
            demoTime += dt
            if (demoTime >= 10.0) { toRest(); demoTime %= 10.0 }
            script(if (demoTime < old) 0.0 else old, demoTime)
        }

        var busy = stepPile(dt)
        if (!grabbed && phase == GumballCrankPhase.Ready && angle != base) {
            angle += (base - angle) * min(1.0, dt * if (reduced) 30.0 else 14.0)
            if (abs(angle - base) < 0.002) angle = base
            busy = true
        }
        spring(wobble, wobbleVelocity, 0.0, dt, 0.18, 0.45).also { wobble = it.first; wobbleVelocity = it.second }
        if (wobbleVelocity != 0.0) busy = true
        if (phase != GumballCrankPhase.Ready) { phaseTime += dt; busy = true }
        if (phase == GumballCrankPhase.Dispensing) {
            if (crossed(0.25, dt)) events += GumballCrankHaptic.Drop
            if (crossed(1.32, dt)) events += GumballCrankHaptic.Open
            if (phaseTime >= 1.95) {
                phase = GumballCrankPhase.Reward; phaseTime = 0.0
                angle = 0.0; base = 0.0; goal = 0.0; detent = 0
            }
        } else if (phase == GumballCrankPhase.Collecting) {
            repeat(8) { i ->
                if (crossed(i * 0.05 + 0.5, dt)) {
                    setWallet(min(1290.0, wallet + 6.25), 0.15)
                    if (!reduced) walletVelocity += 1.6
                }
            }
            if (phaseTime >= 0.95) { phase = GumballCrankPhase.Ready; phaseTime = 0.0; setHint(2) }
        }

        val cardGoal = if (phase == GumballCrankPhase.Reward || phase == GumballCrankPhase.Dispensing && phaseTime >= 1.45) 1.0 else 0.0
        if (phase != GumballCrankPhase.Collecting) {
            spring(card, cardVelocity, cardGoal, dt, if (reduced) 0.2 else 0.48, if (reduced) 1.0 else 0.82).also { card = it.first; cardVelocity = it.second }
        }
        val scrimGoal = if (cardGoal > 0.0 || phase == GumballCrankPhase.Collecting && phaseTime < 0.75) 1.0 else 0.0
        scrim = toward(scrim, scrimGoal, dt, if (scrimGoal > 0.0) 0.3 else 0.25)
        press = toward(press, if (pressing) 1.0 else 0.0, dt, 0.12)
        spring(walletBump, walletVelocity, 0.0, dt, 0.26, 0.5).also { walletBump = it.first; walletVelocity = it.second }
        hintMix = min(1.0, hintMix + dt / 0.25); walletMix = min(1.0, walletMix + dt / walletMixDuration)
        ghostAlpha = toward(ghostAlpha, if (scriptDown) 1.0 else 0.0, dt, if (scriptDown) 0.15 else 0.12)
        ghostPress = toward(ghostPress, if (scriptDown || pressing) 1.0 else 0.0, dt, 0.15)
        moving = busy || demoRunning || grabbed || cardVelocity != 0.0 || walletVelocity != 0.0 || hintMix < 1.0 || walletMix < 1.0
    }

    fun pointerDown(x: Float, y: Float) {
        takeOver()
        if (phase == GumballCrankPhase.Reward && insideButton(x.toDouble(), y.toDouble())) { pressing = true; moving = true } else grabAt(x.toDouble(), y.toDouble())
    }
    fun pointerMove(x: Float, y: Float) { if (grabbed) turnTo(x.toDouble(), y.toDouble()); if (pressing && !insideButton(x.toDouble(), y.toDouble())) pressing = false }
    fun pointerUp(x: Float, y: Float) {
        if (pressing && insideButton(x.toDouble(), y.toDouble())) collect()
        pressing = false; grabbed = false; goal = base; moving = true
    }
    fun accessibilityActivate() {
        takeOver()
        if (phase == GumballCrankPhase.Reward) collect() else if (phase == GumballCrankPhase.Ready) {
            angle = min(TAU, (detent + 1) * QUARTER + 0.02)
            while (detent < 4 && angle >= (detent + 1) * QUARTER) hitDetent()
        }
    }
    fun drainHaptics(): List<GumballCrankHaptic> = events.toList().also { events.clear() }

    fun capsulePose(): GumballCrankCapsulePose {
        val doorY = 561.0
        if (phase == GumballCrankPhase.Ready) return GumballCrankCapsulePose(201.0, 690.0, 1.0, 0.0, 0.0, 0.0)
        val t = if (phase == GumballCrankPhase.Dispensing) phaseTime else 99.0
        val fade = if (phase == GumballCrankPhase.Collecting) 1.0 - span(phaseTime, 0.45, 0.85) else 1.0
        if (reduced) return GumballCrankCapsulePose(201.0, 690.0, 1.0, 0.0, span(t, 1.32, 1.7), span(t, 0.3, 0.95) * fade)
        if (t < 0.3) return GumballCrankCapsulePose(201.0, doorY, 0.6, 0.0, 0.0, 0.0)
        val u = span(t, 0.3, 0.95); val e = easeOut(u)
        var rotation = -2.6 * (1.0 - e)
        if (t >= 0.95) { val w = span(t, 0.95, 1.7); rotation = 0.16 * sin(w * PI * 3.0) * (1.0 - w) }
        return GumballCrankCapsulePose(201.0, doorY + 129.0 * e, 0.6 + 0.4 * e, rotation, easeOut(span(t, 1.32, 1.7)), min(1.0, u * 6.0) * fade)
    }

    private fun takeOver() { demoRunning = false; quietTime = 0.0; scriptDown = false; ghostAlpha = 0.0; ghostPress = 0.0 }
    private fun crossed(value: Double, dt: Double) = phaseTime >= value && phaseTime - dt < value
    private fun insideButton(x: Double, y: Double) = x in 66.0..336.0 && y in 580.0..632.0
    private fun grabAt(x: Double, y: Double): Boolean {
        val dx = x - 201.0; val dy = y - 462.0
        if (dx * dx + dy * dy > 4900.0 || phase != GumballCrankPhase.Ready) return false
        grabbed = true; lastPointerAngle = atan2(dx, -dy); goal = angle; moving = true; return true
    }
    private fun turnTo(x: Double, y: Double) {
        val a = atan2(x - 201.0, -(y - 462.0)); var delta = a - lastPointerAngle
        if (delta > PI) delta -= TAU; if (delta < -PI) delta += TAU
        lastPointerAngle = a; goal += delta
        val back = goal - base
        if (back < 0.0) {
            val before = angle; angle = base - 0.22 * (1.0 - exp(back / 0.22))
            if (before >= base && angle < base - 0.05) events += GumballCrankHaptic.Strain
        } else angle = min(goal, TAU)
        while (detent < 4 && angle >= (detent + 1) * QUARTER) hitDetent()
        moving = true
    }
    private fun hitDetent() {
        detent++; base = detent * QUARTER; wobbleVelocity += if (reduced) 0.0 else 4.0; kickPile(detent); events += GumballCrankHaptic.Tick
        if (detent == 4) { phase = GumballCrankPhase.Dispensing; phaseTime = 0.0; grabbed = false; setHint(1) }
    }
    private fun collect() { if (phase == GumballCrankPhase.Reward) { pressing = false; phase = GumballCrankPhase.Collecting; phaseTime = 0.0; events += GumballCrankHaptic.Collect } }
    private fun toRest() {
        grabbed = false; pressing = false; phase = GumballCrankPhase.Ready; phaseTime = 0.0; angle = 0.0; base = 0.0; goal = 0.0; detent = 0
        card = 0.0; cardVelocity = 0.0; scrim = 0.0
        if (wallet != 1240.0) setWallet(1240.0, 0.5)
        setHint(0); moving = true
    }
    private fun setHint(value: Int) { if (hint != value) { hintFrom = hint; hint = value; hintMix = 0.0 } }
    private fun setWallet(value: Double, duration: Double) { walletPrevious = wallet; wallet = value; walletMix = 0.0; walletMixDuration = duration }
    private fun script(old: Double, now: Double) {
        fun cross(at: Double) = old < at && now >= at
        if (cross(0.5)) { gripAt(angle).also { ghostX = it.first; ghostY = it.second }; scriptDown = true; grabAt(ghostX, ghostY) }
        if (scriptDown && now > 0.7 && now <= 2.55) {
            val u = span(now, 0.7, 2.5) * 4.0; val q = floor(min(u, 3.9999)); val a = (q + easeInOut(u - q)) * QUARTER + 0.02
            gripAt(a).also { ghostX = it.first; ghostY = it.second }; turnTo(ghostX, ghostY)
        }
        if (cross(2.65)) { scriptDown = false; grabbed = false; goal = base }
        if (cross(5.25)) { ghostX = 228.0; ghostY = 606.0; scriptDown = true }
        if (cross(5.45)) pressing = true
        if (cross(5.7)) { collect(); pressing = false; scriptDown = false }
        if (cross(9.0)) { setWallet(1240.0, 0.5); setHint(0) }
    }
    private fun gripAt(a: Double) = Pair(201.0 + 34.0 * sin(a), 462.0 - 34.0 * cos(a))
    private fun kickPile(k: Int) {
        if (reduced) return
        for (i in ballX.indices) { val low = clamp((homeY[i] - 254.0) / 117.0); val kick = 220.0 * (0.3 + 0.7 * low); val a = hash(i * 13 + k * 7) * TAU; ballVX[i] += cos(a) * kick * 0.6; ballVY[i] -= (0.5 + hash(i + k * 3) * 0.6) * kick }
    }
    private fun stepPile(dt: Double): Boolean {
        val w = TAU / 0.52; val stiffness = w * w; val damping = 2.0 * 0.55 * w; val steps = max(1, ceil(dt / (1.0 / 240.0)).toInt()); val h = dt / steps
        repeat(steps) {
            for (i in ballX.indices) { ballVX[i] += (-stiffness * (ballX[i] - homeX[i]) - damping * ballVX[i]) * h; ballVY[i] += (-stiffness * (ballY[i] - homeY[i]) - damping * ballVY[i]) * h; ballX[i] += ballVX[i] * h; ballY[i] += ballVY[i] * h }
            collide(ballX, ballY, ballR, ballVX, ballVY)
        }
        var busy = false
        for (i in ballX.indices) if (abs(ballX[i] - homeX[i]) > 0.05 || abs(ballY[i] - homeY[i]) > 0.05 || abs(ballVX[i]) > 0.5 || abs(ballVY[i]) > 0.5) busy = true else { ballX[i] = homeX[i]; ballY[i] = homeY[i]; ballVX[i] = 0.0; ballVY[i] = 0.0 }
        return busy
    }
    private data class Pile(val x: DoubleArray, val y: DoubleArray, val r: DoubleArray, val c: IntArray)
    private fun settlePile(): Pile {
        val n = 23; val x = DoubleArray(n); val y = DoubleArray(n); val r = DoubleArray(n); val c = IntArray(n); val vx = DoubleArray(n); val vy = DoubleArray(n)
        repeat(n) { i -> r[i] = 15.5 + hash(i + 1) * 4.0; c[i] = floor(hash(i * 7 + 3) * 9).toInt(); x[i] = 121.0 + (i % 7) * 27.0 + hash(i + 9) * 4.0; y[i] = 264.0 + floor(i / 7.0) * 26.0 }
        val h = 1.0 / 240.0
        repeat(1600) { repeat(n) { i -> vy[i] += 900.0 * h; vx[i] *= 0.985; vy[i] *= 0.985; x[i] += vx[i] * h; y[i] += vy[i] * h }; repeat(3) { collide(x, y, r, vx, vy) } }
        return Pile(x, y, r, c)
    }
    private fun collide(x: DoubleArray, y: DoubleArray, r: DoubleArray, vx: DoubleArray, vy: DoubleArray) {
        for (i in x.indices) for (j in i + 1 until x.size) { val dx = x[j] - x[i]; val dy = y[j] - y[i]; val d = hypot(dx, dy).coerceAtLeast(1e-6); val limit = r[i] + r[j]; if (d < limit) { val push = (limit - d) / d / 2.0; x[i] -= dx * push; y[i] -= dy * push; x[j] += dx * push; y[j] += dy * push } }
        for (i in x.indices) { val dx = x[i] - 201.0; val dy = y[i] - 254.0; val d = hypot(dx, dy).coerceAtLeast(1e-6); val limit = 117.0 - r[i]; if (d > limit) { x[i] = 201.0 + dx / d * limit; y[i] = 254.0 + dy / d * limit; val nx = dx / d; val ny = dy / d; val vn = vx[i] * nx + vy[i] * ny; if (vn > 0) { vx[i] -= 1.4 * vn * nx; vy[i] -= 1.4 * vn * ny } }; if (y[i] > 364.0 - r[i]) { y[i] = 364.0 - r[i]; if (vy[i] > 0) vy[i] *= -0.3 } }
    }
}

private fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0) = value.coerceIn(low, high)
private fun span(value: Double, start: Double, end: Double) = clamp((value - start) / (end - start))
private fun hash(n: Int): Double { val value = sin(n * 127.1 + 311.7) * 43758.5453; return value - floor(value) }
private fun toward(value: Double, goal: Double, dt: Double, duration: Double): Double { val amount = dt / duration; return if (goal > value) min(goal, value + amount) else max(goal, value - amount) }
private fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, u: Double): Double {
    val k = clamp(u); val cx = 3 * x1; val bx = 3 * (x2 - x1) - cx; val ax = 1 - cx - bx; val cy = 3 * y1; val by = 3 * (y2 - y1) - cy; val ay = 1 - cy - by; var t = k
    repeat(8) { val x = ((ax * t + bx) * t + cx) * t - k; val dx = (3 * ax * t + 2 * bx) * t + cx; if (abs(x) > 1e-5 && abs(dx) > 1e-6) t -= x / dx }
    t = clamp(t); return ((ay * t + by) * t + cy) * t
}
private fun easeOut(u: Double) = bezier(0.23, 1.0, 0.32, 1.0, u)
private fun easeInOut(u: Double) = bezier(0.77, 0.0, 0.175, 1.0, u)
private fun spring(value: Double, velocity: Double, target: Double, dt: Double, duration: Double, dampingRatio: Double): Pair<Double, Double> {
    val w = PI * 2 / duration; val stiffness = w * w; val damping = 2 * dampingRatio * w; val count = max(1, ceil(dt / 0.004).toInt()); val h = dt / count; var x = value; var v = velocity
    repeat(count) { v += (-stiffness * (x - target) - damping * v) * h; x += v * h }
    return if (abs(x - target) < 0.0005 && abs(v) < 0.005) Pair(target, 0.0) else Pair(x, v)
}
