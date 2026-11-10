package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

/** State is owned by the UI coroutine; animation values are read only in the Canvas draw phase. */
internal class VitaminCapsuleDoseDriver(
    private val autoPlay: Boolean, val reduced: Boolean,
    private val cue: (String, Boolean) -> Unit, private val buzz: (Int) -> Unit,
) {
    private val m = VitaminCapsuleDoseMotion
    var frame = VitaminCapsuleDoseMotion.Frame()
    var now = 0.0
    var manual = !autoPlay || reduced
    var med by mutableStateOf(0)
        private set
    var checks by mutableStateOf(List(9) { if (it % 3 == 0) 1 else 0 })
        private set
    var targetRow = 1
    var holding = false
    var dragging = false
    var lastTouch = -100.0
    var stirX = -999.0; var stirY = -999.0; var stirDx = 0.0; var stirDy = 0.0
    var stirDown = false
    val pearls = VitaminCapsuleDosePearls()
    var layout = VitaminCapsuleDoseLayout(402.0, 874.0, 62.0, 34.0)
    private var yaw = 0.0; private var pitch = 0.0
    private var yawV = 0.0; private var pitchV = 0.0
    private var autoStart = 0.0
    private var prevT = -1.0
    private var holdTick = 0
    private var pendingMark = -1
    private var markAt = 0.0
    private var takeAt = -100.0
    private var resuming = false
    private data class Tween(val from: Double, val to: Double, val at: Double, val duration: Double,
        val easing: (Double) -> Double, val end: (() -> Unit)?)
    private val tweens = mutableMapOf<Int, Tween>()
    private data class Spring(val to: Double, var value: Double, var velocity: Double,
        val omega: Double, val zeta: Double, val energy: Double) {
        fun step(dt: Double): Boolean {
            val t = dt.coerceIn(0.0, 0.064); val x = value - to
            val w1 = omega * sqrt(1 - zeta * zeta); val e = exp(-zeta * omega * t)
            val sn = sin(w1 * t); val cs = cos(w1 * t)
            val fragment = e * (sn * ((velocity + zeta * omega * x) / w1) + x * cs)
            velocity = -zeta * omega * fragment + e * (cs * (velocity + zeta * omega * x) - w1 * x * sn)
            value = to + fragment
            if (energy == 0.0 || (omega * omega * 4 * (value - to).pow(2) + 4 * velocity * velocity) / 2 / energy <= 6e-9) {
                value = to; velocity = 0.0; return true
            }
            return false
        }
    }
    private val springs = mutableMapOf<Int, Spring>()
    private fun value(key: Int) = when (key) { 0 -> frame.hold; 1 -> frame.sep; 2 -> frame.twist; 3 -> frame.tilt; 4 -> frame.k; 5 -> frame.reform; else -> frame.finger }
    private fun put(key: Int, v: Double) { when (key) { 0 -> frame.hold = v; 1 -> frame.sep = v; 2 -> frame.twist = v; 3 -> frame.tilt = v; 4 -> frame.k = v; 5 -> frame.reform = v; else -> frame.finger = v } }
    private fun set(key: Int, v: Double) { tweens.remove(key); springs.remove(key); put(key, v) }
    private fun tween(key: Int, to: Double, duration: Double, delay: Double = 0.0,
        ease: (Double) -> Double = m::io, end: (() -> Unit)? = null) {
        springs.remove(key)
        tweens[key] = Tween(value(key), to, now + delay, duration, ease, end)
    }
    /** Reanimated's duration spring, including its velocity and relative energy threshold. */
    private fun spring(key: Int, to: Double, duration: Double, zeta: Double, velocity: Double) {
        val x = value(key) - to
        var v = (springs[key]?.velocity ?: 0.0) + velocity
        if (x * v > 0) v = 0.0
        if (abs(x) < 1e-12 && abs(v) < 1e-12) { set(key, to); return }
        val settling = duration * 1.5; val mass = 4.0; val threshold = 6e-9
        fun diff(k: Double): Double {
            val w = sqrt(k / mass) * zeta; val e = exp(-w * settling)
            val xt = (x + (v + x * w) * settling) * e
            val vt = xt * -w + (v + x * w) * e
            return (k * xt * xt + mass * vt * vt) / (k * x * x + mass * v * v) - threshold
        }
        var lo = Math.ulp(1.0); var hi = 8000.0; var mid = (lo + hi) / 2
        val direction = if (diff(hi) >= diff(lo)) 1.0 else -1.0
        for (i in 0 until 100) {
            if (abs(diff(mid)) <= threshold * 0.001) break
            if (diff(mid) * direction < 0) lo = mid else hi = mid
            mid = (lo + hi) / 2
        }
        tweens.remove(key)
        springs[key] = Spring(to, value(key), v, sqrt(mid / mass), zeta, (mid * x * x + mass * velocity * velocity) / 2)
    }
    fun touch() {
        lastTouch = now; resuming = false
        if (!manual) {
            manual = true; tween(6, 0.0, 0.15); frame.press = 0.0
            frame.a = if (frame.k > 0.5) frame.b else frame.a; set(4, 0.0)
            tween(0, 0.0, 0.2); frame.take = 0.0; tween(5, 1.0, 0.3)
        }
    }
    fun select(delta: Int, audible: Boolean = true) {
        if (frame.take > 0.0) return
        med = (med + delta + 3) % 3
        frame.a = if (frame.k > 0.5) frame.b else frame.a
        frame.b = VitaminCapsuleDoseTheme.meds[med].shape; set(4, 0.0)
        if (audible) { buzz(0); cue("morph", false) }
        tween(4, 1.0, if (reduced) 0.25 else m.MORPH,
            ease = { m.bezier(it, 0.65, 0.0, 0.35, 1.0) }) { frame.a = frame.b; set(4, 0.0) }
    }
    fun pull(amount: Double) {
        if (reduced || frame.mode != 0) return
        set(1, if (amount < 0.6) max(0.0, amount) else 0.6 + (amount - 0.6) * 0.2)
        set(2, min(1.2, frame.sep * 1.8))
    }
    fun endPull(vx: Double = 0.0, vy: Double = 0.0) {
        if (frame.sep > 0.45) {
            tween(1, m.SEP_OFF, 0.38, ease = { m.bezier(it, 0.34, 1.5, 0.64, 1.0) })
            tween(2, 1.1, 0.38); tween(3, m.POUR_TILT, 0.5)
            frame.mode = 2; frame.hidden = 1.0; pearls.spawn(layout, frame)
            buzz(1); cue("take", false)
        } else {
            val along = (vx * sqrt(0.5) - vy * sqrt(0.5)) / layout.unit * (if (frame.sep < 0.6) 1.0 else 0.2)
            spring(1, 0.0, 0.45, 0.72, along); spring(2, 0.0, 0.45, 0.7, along * 1.8)
        }
    }
    fun turn(dx: Double, dy: Double, vx: Double, vy: Double) {
        if (reduced || frame.mode != 0) return
        dragging = true; yaw = (dx / 70).coerceIn(-1.4, 1.4); pitch = (-dy / 90).coerceIn(-0.9, 0.9)
        yawV = vx / 70; pitchV = -vy / 90
    }
    fun endTurn(dx: Double, vx: Double) {
        dragging = false
        if (frame.mode == 0 && (abs(dx) > 60 || abs(vx) > 650)) select(if (dx < 0) 1 else -1)
    }
    fun close(toCheck: Boolean = false) {
        if (frame.mode == 0) {
            tween(1, 0.0, 0.5) { frame.hidden = 0.0 }; tween(2, 0.0, 0.4); tween(3, m.TILT, 0.45); return
        }
        frame.mode = if (toCheck) 4 else 3
        tween(3, m.TILT, 0.45, 0.9); tween(2, 0.0, 0.5, 1.1)
        tween(1, 0.0, 0.5, 1.1) { frame.mode = 0; frame.hidden = 0.0 }
    }
    fun beginHold() {
        touch()
        if (frame.take > 0) return
        holding = true; holdTick = 0; tweens.remove(0); cue("fill", false)
    }
    fun endHold() {
        lastTouch = now
        if (holding) { holding = false; tween(0, 0.0, 0.26, ease = m::out); cue("fill", true) }
    }
    fun takeDose() {
        holding = false; tween(0, 0.0, 0.26, ease = m::out); cue("fill", true)
        val row = (0..2).firstOrNull { checks[med * 3 + it] == 0 }
        if (row == null) { buzz(1); return }
        targetRow = row
        if (reduced) { cue("take", false); mark(med * 3 + row); return }
        if (frame.mode == 2) {
            close(true); pendingMark = med * 3 + row; markAt = now + 1.0; return
        }
        cue("take", false); takeAt = now
    }
    private fun mark(index: Int) {
        checks = checks.toMutableList().also { it[index] = 1 }
        buzz(2); cue("done", false)
    }
    fun undo(row: Int) {
        touch()
        if (row == 0 || checks[med * 3 + row] == 0) return
        checks = checks.toMutableList().also { it[med * 3 + row] = 0 }; buzz(1)
    }
    fun stir(x: Double, y: Double) {
        stirDx = x - stirX; stirDy = y - stirY; stirX = x; stirY = y
    }
    fun step(dt: Double) {
        now += dt
        val callbacks = mutableListOf<() -> Unit>()
        val iter = tweens.iterator()
        while (iter.hasNext()) { val (key, t) = iter.next()
            if (now < t.at) continue
            put(key, t.from + (t.to - t.from) * t.easing(m.seg(now, t.at, t.at + t.duration)))
            if (now >= t.at + t.duration) { iter.remove(); t.end?.let(callbacks::add) }
        }
        callbacks.forEach { it() }
        val springIter = springs.iterator()
        while (springIter.hasNext()) {
            val (key, spring) = springIter.next()
            val done = spring.step(dt); put(key, spring.value)
            if (done) springIter.remove()
        }
        if (!manual) {
            val t = (now - autoStart) % m.LOOP
            frame = m.frameAt(t); med = 0; targetRow = 1
            val want = if (frame.taken > 0.5) 1 else 0
            if (checks[1] != want) checks = checks.toMutableList().also { it[1] = want }
            if (prevT < 1.3 && t >= 1.3) pearls.spawn(layout, frame)
            if (frame.fingerAt == 3) {
                stir(layout.w * (0.12 + 0.72 * frame.fingerX), layout.floor - 6)
            }
            pearls.step(dt, layout, frame, layout.checkX, layout.rowsY[1], stirX, stirY, stirDx, stirDy,
                frame.fingerAt == 3 && frame.finger > 0.5)
            prevT = t; stirDx = 0.0; stirDy = 0.0
            return
        }
        if (!dragging && !reduced) {
            yawV += (-36 * yaw - 5.4 * yawV) * dt; pitchV += (-36 * pitch - 5.4 * pitchV) * dt
            yaw += yawV * dt; pitch += pitchV * dt
        }
        if (holding) {
            frame.hold = min(1.0, frame.hold + dt / m.HOLD)
            val q = floor(frame.hold * 4).toInt()
            if (q > holdTick) { holdTick = q; buzz(1) }
            if (frame.hold >= 1) takeDose()
        }
        val id = if (reduced) Triple(0.0, 0.0, 0.0) else m.idle(now % m.LOOP)
        frame.yaw = id.first + yaw; frame.pitch = id.second + pitch - frame.hold * 0.06; frame.bob = id.third
        frame.t = now % m.LOOP
        if (takeAt > -50) {
            frame.take = m.seg(now, takeAt, takeAt + m.TAKE)
            if (frame.take >= 1) {
                mark(med * 3 + targetRow); frame.take = 0.0; takeAt = -100.0
                set(5, 0.0); tween(5, 1.0, m.REFORM, ease = { m.bezier(it, 0.34, 1.56, 0.64, 1.0) })
            }
        }
        if (pendingMark >= 0 && now >= markAt) { mark(pendingMark); pendingMark = -1 }
        pearls.step(dt, layout, frame, layout.checkX, layout.rowsY[targetRow], stirX, stirY, stirDx, stirDy, stirDown)
        stirDx = 0.0; stirDy = 0.0
        if (!autoPlay || reduced || dragging || holding || now - lastTouch < 2) return
        if (!resuming) {
            resuming = true; checks = List(9) { if (it % 3 == 0) 1 else 0 }
            if (frame.mode != 0 || frame.sep > 0.01) close()
            if (med != 0 || frame.a != 0.0) { val delta = (3 - med) % 3; select(delta, false) }
        }
        if (resuming && frame.mode == 0 && med != 0 && frame.k == 0.0) select((3 - med) % 3, false)
        val still = frame.mode == 0 && frame.sep < 0.001 && abs(frame.tilt - m.TILT) < 0.001 && abs(yaw) < 0.01 && abs(pitch) < 0.01 && abs(yawV) < 0.05 && frame.k == 0.0 && frame.take == 0.0 && frame.reform > 0.999
        if (resuming && still && frame.a == 0.0 && abs(id.first) < 0.02 && abs(id.third) < 0.3) {
            manual = false; resuming = false; yaw = 0.0; pitch = 0.0; autoStart = now; prevT = -1.0
        }
    }
    fun moving() = !reduced || holding || tweens.isNotEmpty() || springs.isNotEmpty() || pendingMark >= 0
}
