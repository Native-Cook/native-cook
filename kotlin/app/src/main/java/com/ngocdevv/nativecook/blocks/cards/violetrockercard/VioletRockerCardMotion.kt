package com.ngocdevv.nativecook.blocks.cards.violetrockercard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP = 10.0
internal data class VioletRockerCardFrame(
    val n: Double = 0.0, val c: Double = 0.0, val scale: Double = 1.223,
    val originX: Double = 187.0, val originY: Double = 431.0, val snap: Double = 0.0,
    val time: Double? = null, val flat: Boolean = false, val cameraZ: Double? = null, val cameraX: Double? = null,
) { val rock get() = if (flat) 0.0 else -9 + 18 * n + snap }

internal object VioletRockerCardMath {
    val events = doubleArrayOf(1.0, 2.2, 3.25, 4.45, 5.45, 6.65, 7.65, 8.85)
    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = x.coerceIn(lo, hi)
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun seg(t: Double, a: Double, b: Double) = smooth((t - a) / (b - a))
    fun automatic(time: Double): VioletRockerCardFrame {
        val t = max(0.0, time) % LOOP
        var n = 0.0; var extra = 0.0
        for (index in events.indices) {
            val start = events[index]; val direction = if (index % 2 == 0) 1.0 else -1.0
            val u = clamp((t - (start - 0.09)) / ((start + 0.09) - (start - 0.09)))
            n += direction * seg(t, start - 0.09, start + 0.09)
            extra += direction * 1.2 * 16 * u * u * (1 - u) * (1 - u) * sin(2 * PI * u)
        }
        if (t < 4.45) return VioletRockerCardFrame(n = n, snap = extra, time = t)
        if (t < 6.65) return VioletRockerCardFrame(n = n, scale = 2.7, originX = -40.0, originY = 350.0, snap = extra, time = t, cameraZ = 90.0, cameraX = 20.0)
        val c = 1 - seg(t, 9.1, 9.6)
        return VioletRockerCardFrame(n = n, c = c, scale = 1.223 + 0.577 * c, originX = 187 - 13 * c, originY = 431 - 5 * c, snap = extra, time = t)
    }
    fun spring(age: Double, from: Double, to: Double, velocity: Double): Double {
        if (age >= 0.64) return to
        val t = max(0.0, age); val omega = 2 * PI / 0.32; val zeta = 0.78
        val damp = omega * zeta; val wd = omega * sqrt(1 - zeta * zeta); val delta = from - to
        val x = exp(-damp * t) * (delta * cos(wd * t) + (velocity + damp * delta) / wd * sin(wd * t))
        return clamp(to + x * (1 - seg(t, 0.48, 0.64)), -1.0 / 15, 16.0 / 15)
    }
    fun contains(x: Double, y: Double): Boolean {
        val ax = abs(x); val ay = abs(y)
        return ax <= 50 && ay <= 130 && (ay <= 80 || ax * ax + (ay - 80) * (ay - 80) <= 2500)
    }
    fun dot(index: Int, f: VioletRockerCardFrame): Double {
        val base = if (index == 18) 0.25 else 0.04
        val t = f.time ?: return base + (1 - base) * clamp(f.n)
        var active = 0.0
        for (event in events.indices) {
            val delay = 0.008 + (18 - index) * 0.004
            active += (if (event % 2 == 0) 1 else -1) * seg(t, events[event] - 0.09 + delay, events[event] - 0.09 + delay + 0.1)
        }
        return base + (1 - base) * active
    }
}

/** Row-major homography for source u/v in0…100/0…260; inverse returns centered model coordinates. */
internal class VioletRockerCardProjection(f: VioletRockerCardFrame, rock: Double, depth: Double) {
    val originX = f.originX; val originY = f.originY
    val a: Double; val b: Double; val d: Double; val e: Double; val g: Double; val h: Double
    val tx: Double; val ty: Double; val w: Double
    init {
        val az = (f.cameraZ ?: (55 * (1 - f.c))) * PI / 180
        val ac = (f.cameraX ?: (50.2 * (1 - f.c))) * PI / 180
        val ar = rock * PI / 180
        val cz = cos(az); val sz = sin(az); val cc = cos(ac); val sc = sin(ac); val cr = cos(ar); val sr = sin(ar)
        a = f.scale * cz; b = -f.scale * sz * cr
        d = f.scale * cc * sz; e = f.scale * (cc * cz * cr + sc * sr)
        g = -sc * sz / 900; h = -(sc * cz * cr - cc * sr) / 900
        tx = -50 * a - 130 * b
        ty = -50 * d - 130 * e - f.scale * sc * depth
        w = 1 - cc * depth / 900 - 50 * g - 130 * h
    }
    fun point(x: Double, y: Double): Pair<Double, Double> {
        val u = x + 50; val v = y + 130; val divisor = g * u + h * v + w
        return (originX + (a * u + b * v + tx) / divisor) to (originY + (d * u + e * v + ty) / divisor)
    }
    fun local(x: Double, y: Double): Pair<Double, Double>? {
        val xx = x - originX; val yy = y - originY
        val aa = a - xx * g; val bb = b - xx * h; val dd = d - yy * g; val ee = e - yy * h
        val rhsX = xx * w - tx; val rhsY = yy * w - ty; val determinant = aa * ee - bb * dd
        if (abs(determinant) <= 0.000001) return null
        return ((rhsX * ee - bb * rhsY) / determinant - 50) to ((aa * rhsY - rhsX * dd) / determinant - 130)
    }
}

internal class VioletRockerCardDriver {
    private var epoch = violetRockerCardNow()
    private var manual = false
    var held = false
        private set
    private var dragged = false
    private var manualCamera = 0.0; private var manualScale = 1.223
    private var manualOriginX = 187.0; private var manualOriginY = 431.0
    private var manualSnap = 0.0; private var manualCameraZ = 55.0; private var manualCameraX = 50.2
    private var originalOn = false; private var heldNight = 0.0
    private var releaseFrom = 0.0; private var releaseTarget = 0.0; private var releaseVelocity = 0.0
    private var releaseDate = epoch; private var lastTouch = epoch
    var moving = false
        private set
    private var reducedNight = 1.0
    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): VioletRockerCardFrame {
        if (reduced) return VioletRockerCardFrame(n = reducedNight, c = 1.0, scale = 1.8, originX = 174.0, originY = 426.0, flat = true)
        if (manual) {
            val n = if (held) heldNight else VioletRockerCardMath.spring(now - releaseDate, releaseFrom, releaseTarget, releaseVelocity)
            val recovery = if (held || !autoPlay) 0.0 else VioletRockerCardMath.smooth((now - lastTouch - 2) / 0.6)
            return VioletRockerCardFrame(n = n * (1 - recovery), c = manualCamera * (1 - recovery),
                scale = manualScale + (1.223 - manualScale) * recovery, originX = manualOriginX + (187 - manualOriginX) * recovery,
                originY = manualOriginY + (431 - manualOriginY) * recovery,
                snap = manualSnap * (if (held) 1.0 else 1 - VioletRockerCardMath.seg(now - releaseDate, 0.0, 0.32)) * (1 - recovery),
                cameraZ = manualCameraZ + (55 - manualCameraZ) * recovery, cameraX = manualCameraX + (50.2 - manualCameraX) * recovery)
        }
        return VioletRockerCardMath.automatic(preview ?: if (autoPlay) now - epoch else 0.0)
    }
    private fun capture(f: VioletRockerCardFrame) {
        manualCamera = f.c; manualScale = f.scale; manualOriginX = f.originX; manualOriginY = f.originY; manualSnap = f.snap
        manualCameraZ = f.cameraZ ?: 55 * (1 - f.c); manualCameraX = f.cameraX ?: 50.2 * (1 - f.c)
    }
    fun select(target: Double, now: Double, autoPlay: Boolean, reduced: Boolean): Boolean {
        if (reduced) { reducedNight = target; return false }
        val f = frame(now, autoPlay, false, null)
        capture(f); manual = true; held = false
        releaseFrom = f.n; releaseTarget = target; releaseVelocity = 0.0; releaseDate = now; lastTouch = now; moving = true
        return (f.n >= 0.5) != (target >= 0.5)
    }
    fun begin(x: Double, y: Double, now: Double, autoPlay: Boolean): Boolean {
        if (held) return true
        val f = frame(now, autoPlay, false, null)
        val p = VioletRockerCardProjection(f, f.rock, 18.0).local(x, y) ?: return false
        if (!VioletRockerCardMath.contains(p.first, p.second)) return false
        capture(f); manual = true; held = true; dragged = false; originalOn = f.n >= 0.5; heldNight = f.n; moving = true; lastTouch = now
        return true
    }
    fun move(x: Double, y: Double, travel: Double, now: Double, autoPlay: Boolean) {
        if (!held) return
        if (travel > 3) dragged = true
        if (dragged) {
            val f = frame(now, autoPlay, false, null)
            VioletRockerCardProjection(f, f.rock, 18.0).local(x, y)?.let { heldNight = VioletRockerCardMath.clamp((it.second + 95) / 190) }
        }
        lastTouch = now
    }
    fun finish(x: Double, y: Double, vx: Double, vy: Double, normal: Boolean, now: Double, autoPlay: Boolean): Boolean {
        if (!held) return false
        val f = frame(now, autoPlay, false, null); val projection = VioletRockerCardProjection(f, f.rock, 18.0)
        var target = if (heldNight >= 0.5) 1.0 else 0.0
        var velocity = 0.0
        if (normal && !dragged) target = if ((projection.local(x, y)?.second ?: 0.0) < 0) 0.0 else 1.0
        else if (normal) {
            val mapped = projection.local(x, y)?.let { VioletRockerCardMath.clamp((it.second + 95) / 190) } ?: heldNight
            val projected = projection.local(x + vx * 0.10, y + vy * 0.10)?.let { VioletRockerCardMath.clamp((it.second + 95) / 190) } ?: heldNight
            target = if (projected >= 0.5) 1.0 else 0.0; velocity = (projected - mapped) / 0.10
        }
        val changed = originalOn != (target >= 0.5)
        releaseFrom = heldNight; releaseTarget = target; releaseVelocity = VioletRockerCardMath.clamp(velocity, -1.8, 1.8)
        held = false; releaseDate = now; lastTouch = now; moving = true
        return normal && changed
    }
    fun reducedTap(x: Double, y: Double, now: Double): Boolean {
        val f = frame(now, false, true, null); val p = VioletRockerCardProjection(f, f.rock, 18.0).local(x, y) ?: return false
        if (!VioletRockerCardMath.contains(p.first, p.second)) return false
        reducedNight = if (p.second < 0) 0.0 else 1.0
        return true
    }
    fun complete(now: Double, autoPlay: Boolean, reduced: Boolean) {
        if (!manual || held || reduced) return
        if (autoPlay && now - lastTouch >= 2.6) reset(now)
        else if (!autoPlay && now - releaseDate >= 0.66) moving = false
    }
    fun reset(now: Double) { manual = false; held = false; dragged = false; moving = false; epoch = now; reducedNight = 1.0 }
}

internal fun violetRockerCardNow() = System.nanoTime() / 1_000_000_000.0
