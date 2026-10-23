package com.ngocdevv.nativecook.blocks.forms.keycapmoodinput

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal val keycapMoodHex = intArrayOf(0xB6FF3B, 0xFF4FA3, 0xFFB21F, 0x2FE6D2, 0x8E6BFF, 0xFF4A2E)
internal val keycapMoodNames = arrayOf("happy", "in love", "amazed", "calm", "down", "angry")
internal val keycapWords = arrayOf(arrayOf("i", "we"), arrayOf("feel", "felt"), arrayOf("so", "very", "a bit"))
internal val keycapFanOrder = intArrayOf(5, 2, 0, 3, 4, 1)
private fun keycapLinear(c: Int): Double {
    val v = c / 255.0
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}
internal val keycapMoodRgb = Array(6) { i ->
    doubleArrayOf(keycapLinear(keycapMoodHex[i] shr 16 and 255), keycapLinear(keycapMoodHex[i] shr 8 and 255), keycapLinear(keycapMoodHex[i] and 255))
}

internal data class KeycapPoint(val x: Double, val y: Double)
internal data class KeycapRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(px: Double, py: Double) = px >= x && px <= x + width && py >= y && py < y + height
}
internal class KeycapLayout(val width: Double, val height: Double) {
    val u = min(width / 402.0, height / 874.0)
    val k = 134 * u
    val pitch = 142 * u
    val mini = 52 * u
    private val top = (height - 560 * u) / 2 - 20 * u
    val keys = Array(4) { i -> KeycapRect(width / 2 - k / 2, top + i * pitch, k, k) }
    val hits = Array(4) { i -> KeycapRect(keys[i].x - 12 * u, keys[i].y - 4 * u, k + 24 * u, pitch) }
    val minis = Array(6) { i ->
        val angle = (155 - 26 * i) * PI / 180
        KeycapPoint(width / 2 + pitch * cos(angle), top + 3 * pitch + k / 2 + pitch * sin(angle))
    }
    fun center(i: Int) = KeycapPoint(width / 2, keys[i].y + k / 2)
    fun hitKey(x: Double, y: Double) = hits.indexOfFirst { it.contains(x, y) }
    fun hitMini(x: Double, y: Double): Int {
        var best = 34 * u
        var found = -1
        for (i in 0..5) {
            val distance = hypot(x - minis[i].x, y - minis[i].y)
            if (distance < best) {
                best = distance
                found = i
            }
        }
        return found
    }
}

internal data class KeycapPointer(val down: Boolean, val x: Double, val y: Double)
private fun glide(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)
internal fun keycapDemoPointer(ms: Int, layout: KeycapLayout): KeycapPointer {
    val t = ((ms % 13000) + 13000) % 13000
    val cx = layout.width / 2
    val top = layout.center(0).y
    val moodY = top + 3 * layout.pitch
    var x = cx
    var y = top
    var down = false
    when {
        t in 1000 until 1260 -> { down = true; y = top + 2 * layout.pitch }
        t in 2200 until 2760 -> { down = true; y = glide(top, top + 2 * layout.pitch, (t - 2260) / 380.0) }
        t in 3900 until 4200 -> { down = true; y = moodY }
        t in 6100 until 8850 -> {
            down = true
            y = moodY
            val starts = intArrayOf(6850, 7250, 7600, 7950, 8300)
            for (i in starts.indices) {
                if (t < starts[i]) break
                val next = layout.minis[4 - i]
                val previous = if (i == 0) KeycapPoint(cx, moodY) else layout.minis[5 - i]
                x = glide(previous.x, next.x, (t - starts[i]) / 200.0)
                y = glide(previous.y, next.y, (t - starts[i]) / 200.0)
            }
        }
        t in 10300 until 11000 -> { down = true; y = glide(top, moodY, (t - 10360) / 500.0) }
        t in 1260 until 3900 -> y = top + 2 * layout.pitch
        t in 4200 until 6100 -> y = moodY
        t in 8850 until 10300 -> { x = layout.minis[0].x; y = layout.minis[0].y }
        t >= 11000 -> y = moodY
    }
    return KeycapPointer(down, x, y)
}

internal data class KeycapFlip(var from: Int = 0, var to: Int = 0, var t: Int = -1, var mode: Int = 0)
internal data class KeycapMoodMix(var from: Int = 0, var to: Int = 0, var p: Double = 1.0, var ox: Double = 0.0, var oy: Double = 0.0, var radial: Boolean = false)
internal data class KeycapEvent(val mask: Int, val key: Int, val clock: Int)

/** The fixed 1 ms simulation is a direct translation of keycap-sim.ts. */
internal class KeycapMotion(val layout: KeycapLayout, val reduced: Boolean, val autoPlay: Boolean) {
    var clock = 0
    private var remainder = 0.0
    val events = ArrayList<KeycapEvent>()
    val x = DoubleArray(4)
    val v = DoubleArray(4)
    val held = BooleanArray(4)
    val stroke = BooleanArray(4)
    val pressAge = IntArray(4)
    val pressFrom = DoubleArray(4)
    val bottom = BooleanArray(4)
    val direction = IntArray(4) { 1 }
    val autoRelease = BooleanArray(4)
    val choices = IntArray(4)
    val legends = Array(4) { KeycapFlip(mode = if (reduced) 2 else if (it == 3) 1 else 0) }
    var mood = KeycapMoodMix()
    private var moodAge = 0
    private var moodDuration = 200
    private var moodPending = false
    private var moodTarget = -1
    var ox = 0.0
    var oy = 0.0
    var charge = 0.0
    private var chargeAge = 0
    var burst = 1.0
    private var burstAge = -1
    var ring = 0.0
    val rim = DoubleArray(4)
    private val rimStart = IntArray(4) { -1 }
    val scene = keycapMoodRgb[0].copyOf()
    var fanOpen = false
    private var fanSince = 0
    private var fanSticky = false
    private var fanSession = false
    private var fanRequest = false
    val out = DoubleArray(6)
    private val outV = DoubleArray(6)
    private val outFrom = DoubleArray(6)
    val miniX = DoubleArray(6)
    private val miniV = DoubleArray(6)
    var miniActive = -1
    var dim = 1.0
    private var dimFrom = 1.0
    private var pointerDown = false
    private var pointerX = 0.0
    private var pointerY = 0.0
    private var pointerKey = -1
    private var pointerT0 = 0
    private var pointerEligible = false
    private var realDown = false
    private var demoActive = autoPlay
    private var demoT = 0
    private var demoIdle = 0

    private fun emit(mask: Int, key: Int) { events.add(KeycapEvent(mask, key, clock)) }
    private fun unit(t: Double) = t.coerceIn(0.0, 1.0)
    private fun easeOut(t: Double): Double { val a = 1 - unit(t); return 1 - a * a * a }
    private fun bezier(t: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (t <= 0 || t >= 1) return unit(t)
        var lo = 0.0
        var hi = 1.0
        var q = t
        repeat(14) {
            q = (lo + hi) / 2
            val value = 3 * (1 - q) * (1 - q) * q * x1 + 3 * (1 - q) * q * q * x2 + q * q * q
            if (value < t) lo = q else hi = q
        }
        return 3 * (1 - q) * (1 - q) * q * y1 + 3 * (1 - q) * q * q * y2 + q * q * q
    }
    private fun flip(key: Int, to: Int) {
        val from = choices[key]
        if (from == to) return
        choices[key] = to
        legends[key] = KeycapFlip(from, to, 0, if (reduced) 2 else if (key == 3) 1 else 0)
        emit(128, key)
    }
    private fun mixMood(to: Int, radial: Boolean, duration: Int) {
        val from = mood.to
        flip(3, to)
        mood = KeycapMoodMix(from, to, if (from == to) 1.0 else 0.0, ox, oy, !reduced && radial)
        moodAge = 0
        moodDuration = if (reduced) 200 else duration
    }
    private fun burst() {
        burstAge = 0
        charge = 0.0
        if (!reduced) {
            burst = 2.4
            for (i in 0..2) rimStart[i] = clock + (3 - i) * 70
        }
        emit(16, 3)
    }
    private fun commitMood() {
        val target = if (moodTarget >= 0) moodTarget else (choices[3] + direction[3] + 6) % 6
        mixMood(target, true, 460)
        moodPending = false
        moodTarget = -1
        burst()
    }
    private fun openFan() {
        fanOpen = true
        fanSince = clock
        fanSticky = false
        fanSession = true
        fanRequest = false
        moodPending = false
        held[3] = true
        dimFrom = dim
        emit(32, 3)
    }
    private fun closeFan(celebrate: Boolean) {
        fanOpen = false
        fanSince = clock
        fanSticky = false
        dimFrom = dim
        out.copyInto(outFrom)
        fanSession = false
        held[3] = false
        miniActive = -1
        emit(256, 3)
        if (celebrate) burst()
    }
    private fun selectMini(mini: Int) {
        miniActive = mini
        if (choices[3] == keycapFanOrder[mini]) return
        mixMood(keycapFanOrder[mini], false, 160)
        emit(64, mini)
    }
    private fun press(key: Int) {
        held[key] = true
        if (stroke[key]) return
        stroke[key] = true
        bottom[key] = false
        pressAge[key] = 0
        pressFrom[key] = x[key]
        if (key == 3) { chargeAge = 0; charge = 0.0 }
    }
    private fun release(key: Int, cancelled: Boolean) {
        held[key] = false
        if (key == 3 && !fanSession && !cancelled) {
            if (bottom[3]) commitMood() else moodPending = true
        }
    }
    private fun pointer(p: KeycapPointer, cancelled: Boolean) {
        val wasDown = pointerDown
        val key = if (p.down) layout.hitKey(p.x, p.y) else -1
        if (p.down && !wasDown) {
            pointerT0 = clock
            pointerEligible = key == 3
        }
        if (cancelled && fanOpen) closeFan(false)
        if (fanOpen) {
            val mini = layout.hitMini(p.x, p.y)
            if (p.down && mini >= 0) selectMini(mini)
            if (p.down && !wasDown && fanSticky && mini < 0) closeFan(false)
            else if (!p.down && wasDown) {
                if (mini >= 0 && !cancelled) closeFan(true) else fanSticky = true
            }
            pointerKey = -1
        } else {
            if (key != 3) pointerEligible = false
            if (pointerKey != key) {
                if (pointerKey >= 0) release(pointerKey, cancelled)
                if (key >= 0) { direction[key] = 1; press(key) }
            }
            pointerKey = key
        }
        if (key == 3) {
            ox = p.x - layout.width / 2
            oy = p.y - layout.center(3).y
        }
        pointerDown = p.down
        pointerX = p.x
        pointerY = p.y
    }
    private fun stopDemo() {
        if (demoActive) pointer(KeycapPointer(false, pointerX, pointerY), false)
        demoActive = false
        demoIdle = 0
    }
    fun touch(p: KeycapPointer, cancelled: Boolean = false) {
        events.clear()
        if (p.down || realDown) {
            stopDemo()
            realDown = p.down
            pointer(p, cancelled)
        }
    }
    fun action(type: String, key: Int = 0, target: Int = 0) {
        events.clear()
        stopDemo()
        if (type == "toggleFan") {
            if (fanOpen) closeFan(false)
            else { press(3); fanRequest = true; fanSince = clock }
            return
        }
        if (type == "selectMood" && fanOpen) {
            for (i in 0..5) if (keycapFanOrder[i] == target) selectMini(i)
            closeFan(true)
            return
        }
        val k = if (type == "selectMood") 3 else key.coerceIn(0, 3)
        if (fanOpen) closeFan(false)
        direction[k] = if (type == "previous") -1 else 1
        if (type == "selectMood") moodTarget = target.coerceIn(0, 5)
        ox = 0.0
        oy = 0.0
        press(k)
        autoRelease[k] = true
    }
    private fun spring(values: DoubleArray, velocity: DoubleArray, i: Int, target: Double, k: Double, c: Double) {
        if (values[i] == target && velocity[i] == 0.0) return
        val z = values[i] - target
        val d = c / 2
        val e = exp(-d * 0.001)
        if (k <= d * d) {
            val b = velocity[i] + d * z
            values[i] = target + (z + b * 0.001) * e
            velocity[i] = (velocity[i] - d * b * 0.001) * e
        } else {
            val w = sqrt(k - d * d)
            val cs = cos(w * 0.001)
            val sn = sin(w * 0.001)
            values[i] = target + e * (z * cs + (velocity[i] + d * z) * sn / w)
            velocity[i] = e * (velocity[i] * cs - (d * velocity[i] + k * z) * sn / w)
        }
        if (abs(values[i] - target) < 0.0001 && abs(velocity[i]) < 0.001) {
            values[i] = target
            velocity[i] = 0.0
        }
    }
    private fun mechanics() {
        for (i in 0..3) {
            if (stroke[i]) {
                pressAge[i]++
                val age = pressAge[i]
                val old = x[i]
                if (reduced) x[i] = pressFrom[i] + (0.6 - pressFrom[i]) * easeOut(age / 85.0)
                else {
                    val drive = if (age <= 40) 0.45 * easeOut(age / 40.0) else if (age <= 60) 0.45 else 0.45 + 0.55 * ((age - 60) / 25.0).pow(2)
                    x[i] = pressFrom[i] + (1 - pressFrom[i]) * min(1.0, drive)
                }
                v[i] = (x[i] - old) * 1000
                if (age >= 85) {
                    x[i] = if (reduced) 0.6 else 1.0
                    v[i] = if (reduced) 0.0 else -v[i] * 0.2
                    stroke[i] = false
                    bottom[i] = true
                    emit(1 shl i, i)
                    if (i < 3) flip(i, (choices[i] + direction[i] + if (i == 2) 3 else 2) % (if (i == 2) 3 else 2))
                    else if (moodPending) commitMood()
                    if (autoRelease[i]) { autoRelease[i] = false; release(i, false) }
                }
            } else if (held[i]) {
                spring(x, v, i, if (reduced) 0.6 else 1.0, 6400.0, 160.0)
                if (x[i] > 1) { x[i] = 1.0; v[i] = 0.0 }
            } else {
                spring(x, v, i, 0.0, if (reduced) 1600.0 else 1100.0, if (reduced) 80.0 else 42.0)
                if (x[i] < -0.12) { x[i] = -0.12; v[i] = 0.0 }
            }
            val flip = legends[i]
            if (flip.t >= 0) {
                flip.t++
                val duration = if (reduced) 140.0 else if (i == 3) 110 + (40 * layout.u + hypot(mood.ox, mood.oy)) * 0.9 else 208.0
                if (flip.t >= duration) { flip.from = flip.to; flip.t = -1 }
            }
        }
        if (held[3] && bottom[3]) { chargeAge++; charge = easeOut(chargeAge / 300.0) }
        else { charge *= exp(-1.0 / 60); if (charge < 0.0001) charge = 0.0 }
        if (!fanOpen && ((pointerDown && pointerEligible && clock - pointerT0 >= 450) || (fanRequest && pressAge[3] >= 85 && clock - fanSince >= 450))) openFan()
        val age = clock - fanSince
        dim = dimFrom + ((if (fanOpen) 0.72 else 1.0) - dimFrom) * unit(age / 200.0)
        for (i in 0..5) {
            val rank = min(abs(i - 2), abs(i - 3))
            if (reduced) out[i] = if (fanOpen) unit(age / 200.0) else outFrom[i] * (1 - unit(age / 200.0))
            else if (fanOpen) {
                if (age >= rank * 28) spring(out, outV, i, 1.0, 260.0, 20.0)
                out[i] = min(1.06, out[i])
            } else {
                out[i] = outFrom[i] * (1 - bezier(unit((age - (2 - rank) * 28) / 280.0), 0.32, 0.72, 0.0, 1.0))
                outV[i] = 0.0
            }
            spring(miniX, miniV, i, if (fanOpen && keycapFanOrder[i] == choices[3]) { if (reduced) 0.6 else 1.0 } else 0.0, 1600.0, if (reduced) 80.0 else 58.0)
        }
    }
    private fun lighting() {
        if (mood.p < 1) {
            moodAge++
            val t = unit(moodAge / moodDuration.toDouble())
            mood.p = if (mood.radial) bezier(t, 0.23, 1.0, 0.32, 1.0) else t
            if (t == 1.0) mood.from = mood.to
        }
        for (i in 0..2) {
            val target = keycapMoodRgb[choices[3]][i]
            scene[i] += (target - scene[i]) * (1 - exp(-1.0 / 90))
            if (abs(target - scene[i]) < 0.00001) scene[i] = target
        }
        if (burstAge >= 0) burstAge++
        burst = if (reduced) 1.0 else if (burstAge in 0 until 700) 1 + 1.4 * (1 - easeOut(burstAge / 700.0))
            else 1 + 0.05 * sin(clock * PI / 2000) * (if (demoActive) 1 - easeOut((demoT - 12000) / 1000.0) else 1.0)
        ring = if (!reduced && burstAge in 0 until 900) burstAge / 900.0 else 0.0
        for (i in 0..2) {
            val age = clock - rimStart[i]
            rim[i] = if (!reduced && rimStart[i] >= 0 && age in 0 until 580) {
                if (age == 0 && !held[i] && !stroke[i]) v[i] += 3.4
                if (age < 60) age / 60.0 else (exp(-5 * (age - 60) / 520.0) - exp(-5.0)) / (1 - exp(-5.0))
            } else 0.0
        }
    }
    private fun quietReset() {
        if (fanOpen) closeFan(false)
        for (i in 0..2) flip(i, 0)
        mixMood(0, false, 200)
        emit(512, -1)
    }
    private fun reset() {
        val fresh = KeycapMotion(layout, reduced, autoPlay)
        fresh.remainder = remainder
        val previousEvents = events.toList()
        copyFrom(fresh)
        events.addAll(previousEvents)
    }
    private fun copyFrom(other: KeycapMotion) {
        clock = other.clock
        x.fill(0.0); v.fill(0.0); held.fill(false); stroke.fill(false); pressAge.fill(0); pressFrom.fill(0.0)
        bottom.fill(false); direction.fill(1); autoRelease.fill(false); choices.fill(0)
        for (i in 0..3) legends[i] = other.legends[i]
        mood = other.mood
        moodAge = 0; moodDuration = 200; moodPending = false; moodTarget = -1
        ox = 0.0; oy = 0.0; charge = 0.0; chargeAge = 0; burst = 1.0; burstAge = -1; ring = 0.0
        rim.fill(0.0); rimStart.fill(-1); keycapMoodRgb[0].copyInto(scene)
        fanOpen = false; fanSince = 0; fanSticky = false; fanSession = false; fanRequest = false
        out.fill(0.0); outV.fill(0.0); outFrom.fill(0.0); miniX.fill(0.0); miniV.fill(0.0)
        miniActive = -1; dim = 1.0; dimFrom = 1.0
        pointerDown = false; pointerX = 0.0; pointerY = 0.0; pointerKey = -1; pointerT0 = 0; pointerEligible = false
        realDown = false; demoActive = autoPlay; demoT = 0; demoIdle = 0
    }
    fun step(dtMs: Double) {
        events.clear()
        remainder += dtMs.coerceIn(0.0, 100.0)
        while (remainder >= 1 - 0.0000001) {
            remainder = max(0.0, remainder - 1)
            clock++
            if (demoActive) {
                demoT++
                if (demoT == 13000) {
                    reset()
                    emit(1024, -1)
                    continue
                }
                pointer(keycapDemoPointer(demoT, layout), false)
            } else if (!realDown) {
                demoIdle++
                if (autoPlay && demoIdle == 3500) quietReset()
                if (autoPlay && demoIdle >= 4200) {
                    reset()
                    emit(512, -1)
                    continue
                }
            }
            mechanics()
            lighting()
        }
    }
}
