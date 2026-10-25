package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import android.content.Context
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP = 16.0 / 3.0

internal object ElasticFoodCardsMath {
    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = x.coerceIn(a, b)
    fun mix(a: Double, b: Double, u: Double) = a + (b - a) * u
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun smoother(x: Double): Double { val u = clamp(x); return u * u * u * (10 + u * (-15 + 6 * u)) }
    fun smootherSlope(x: Double): Double { val u = clamp(x); return 30 * u * u * (1 - u) * (1 - u) }
    fun softClamp(x: Double): Double {
        val u = clamp(x); val edge = 0.15
        fun end(t: Double) = t * t * t * (6 + t * (-8 + 3 * t))
        return when { u < edge -> edge * end(u / edge); u > 1 - edge -> 1 - edge * end((1 - u) / edge); else -> u }
    }
    fun rotated(x: Double, y: Double, degrees: Double): Pair<Double, Double> {
        val a = degrees * Math.PI / 180
        return (x * cos(a) - y * sin(a)) to (x * sin(a) + y * cos(a))
    }
    fun oscillator(x: Double, v: Double, target: Double, time: Double, damping: Double, omega: Double): Pair<Double, Double> {
        if (time <= 0) return x to v
        val a = damping * omega
        val d = omega * sqrt(1 - damping * damping)
        val b = (v + a * (x - target)) / d
        val c = cos(d * time); val s = sin(d * time); val e = exp(-a * time)
        val f = (x - target) * c + b * s
        return (target + e * f) to (e * (-a * f - (x - target) * d * s + b * d * c))
    }
    fun hermite(x: Double, v: Double, y: Double, w: Double, elapsed: Double, duration: Double): Pair<Double, Double> {
        val u = clamp(elapsed / duration); val u2 = u * u; val u3 = u2 * u
        val position = (2 * u3 - 3 * u2 + 1) * x + (u3 - 2 * u2 + u) * duration * v +
            (-2 * u3 + 3 * u2) * y + (u3 - u2) * duration * w
        val velocity = ((6 * u2 - 6 * u) * x + (3 * u2 - 4 * u + 1) * duration * v +
            (-6 * u2 + 6 * u) * y + (3 * u2 - 2 * u) * duration * w) / duration
        return position to velocity
    }
    fun rubber(q: Double): Double {
        if (q in 0.0..1.0) return q
        val edge = if (q < 0) 0.0 else 1.0
        val over = (q - edge) * 148
        return edge + 0.55 * over / (1 + abs(over) / 120) / 148
    }
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val target = clamp(x)
        if (target == 0.0 || target == 1.0) return target
        fun sample(t: Double, a: Double, b: Double): Double {
            val s = 1 - t
            return 3 * s * s * t * a + 3 * s * t * t * b + t * t * t
        }
        var lo = 0.0; var hi = 1.0
        repeat(18) { val u = (lo + hi) * 0.5; if (sample(u, x1, x2) < target) lo = u else hi = u }
        return sample((lo + hi) * 0.5, y1, y2)
    }
}

internal data class ElasticFoodCardsFrame(
    var q: DoubleArray = DoubleArray(6),
    var qVel: DoubleArray = DoubleArray(6),
    var handBX: Double = 198.0,
    var handBY: Double = -90.0,
    var handAngle: Double = 0.0,
    var handAlpha: Double = 0.0,
    var grip: Double = 0.0,
    var gatherX: Double = 190.0,
    var gatherY: Double = -90.0,
    var reliefAmplitude: Double = 0.0,
    var fanWidth: Double = 0.0,
    var tailY: Double = 120.0,
    var scalarVel: DoubleArray = DoubleArray(10),
) {
    var values: DoubleArray
        get() = doubleArrayOf(handBX, handBY, handAngle, handAlpha, grip, gatherX, gatherY, reliefAmplitude, fanWidth, tailY)
        set(value) {
            handBX = value[0]; handBY = value[1]; handAngle = value[2]; handAlpha = value[3]; grip = value[4]
            gatherX = value[5]; gatherY = value[6]; reliefAmplitude = value[7]; fanWidth = value[8]; tailY = value[9]
        }
    fun deepCopy() = copy(q = q.copyOf(), qVel = qVel.copyOf(), scalarVel = scalarVel.copyOf())
    fun mixed(other: ElasticFoodCardsFrame, u: Double): ElasticFoodCardsFrame = deepCopy().also { f ->
        f.q = DoubleArray(6) { ElasticFoodCardsMath.mix(q[it], other.q[it], u) }
        f.qVel = DoubleArray(6) { ElasticFoodCardsMath.mix(qVel[it], other.qVel[it], u) }
        val a = values; val b = other.values
        f.values = DoubleArray(10) { ElasticFoodCardsMath.mix(a[it], b[it], u) }
        f.scalarVel = DoubleArray(10) { ElasticFoodCardsMath.mix(scalarVel[it], other.scalarVel[it], u) }
    }
    fun bridged(other: ElasticFoodCardsFrame, elapsed: Double, duration: Double): ElasticFoodCardsFrame = deepCopy().also { f ->
        for (i in 0..5) {
            val pair = ElasticFoodCardsMath.hermite(q[i], qVel[i], other.q[i], other.qVel[i], elapsed, duration)
            f.q[i] = pair.first; f.qVel[i] = pair.second
        }
        val a = values; val b = other.values
        for (i in 0..9) {
            val pair = ElasticFoodCardsMath.hermite(a[i], scalarVel[i], b[i], other.scalarVel[i], elapsed, duration)
            a[i] = pair.first; f.scalarVel[i] = pair.second
        }
        f.values = a
    }
    companion object {
        fun open(q: Double, angle: Double = 0.0): ElasticFoodCardsFrame = ElasticFoodCardsFrame().also { f ->
            f.q = DoubleArray(6) { q }; f.handBX = 204.0; f.handBY = -90 + 286 * q; f.handAngle = angle
            f.handAlpha = ElasticFoodCardsMath.smoother((f.handBY - 20) / 26)
            val contact = ElasticFoodCardsMath.rotated(4.0, 16.0, angle)
            f.gatherX = f.handBX - contact.first; f.gatherY = f.handBY - contact.second; f.tailY = 263.0
        }
    }
}

/** Immutable PCHIP keys are decoded once; every sample preserves its analytic derivative. */
internal class ElasticFoodCardsMotion(context: Context) {
    private val tracks: Array<Array<DoubleArray>> = context.assets.open("elasticfoodcards/elasticfoodcards_motion.json").bufferedReader().use { reader ->
        val json = JSONArray(reader.readText())
        require(json.length() == 13)
        Array(13) { i -> val keys = json.getJSONArray(i); Array(keys.length()) { j ->
            val key = keys.getJSONArray(j); DoubleArray(3) { k -> key.getDouble(k) }
        } }
    }
    private fun pchip(points: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(points.size) { doubleArrayOf(points[it][0], points[it][1], 0.0) }
        for (i in 1 until points.lastIndex) {
            val h0 = points[i][0] - points[i - 1][0]; val h1 = points[i + 1][0] - points[i][0]
            val a = (points[i][1] - points[i - 1][1]) / h0; val b = (points[i + 1][1] - points[i][1]) / h1
            if (a * b > 0) { val w0 = 2 * h1 + h0; val w1 = h1 + 2 * h0; result[i][2] = (w0 + w1) / (w0 / a + w1 / b) }
        }
        return result
    }
    private val tail = pchip(arrayOf(
        doubleArrayOf(0.0,263.0),doubleArrayOf(0.8,263.0),doubleArrayOf(0.9,260.0),doubleArrayOf(1.0,255.0),
        doubleArrayOf(1.1,251.0),doubleArrayOf(1.2,253.0),doubleArrayOf(1.3,255.0),doubleArrayOf(1.4,205.0),
        doubleArrayOf(1.5,160.0),doubleArrayOf(1.6,120.0),doubleArrayOf(3.4,120.0),doubleArrayOf(3.8,263.0),doubleArrayOf(LOOP,263.0)))
    private fun sample(keys: Array<DoubleArray>, t: Double): Pair<Double, Double> {
        if (keys.size < 2) return 0.0 to 0.0
        if (t <= keys[0][0]) return keys[0][1] to keys[0][2]
        if (t >= keys.last()[0]) return keys.last()[1] to keys.last()[2]
        var lo = 0; var hi = keys.lastIndex
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (keys[m][0] <= t) lo = m else hi = m }
        val a = keys[lo]; val b = keys[hi]
        return ElasticFoodCardsMath.hermite(a[1], a[2], b[1], b[2], t - a[0], b[0] - a[0])
    }
    fun source(time: Double): ElasticFoodCardsFrame { var native = (time - 2.2) % LOOP; if (native < 0) native += LOOP; return frame(native) }
    fun frame(time: Double): ElasticFoodCardsFrame {
        val t = max(0.0, time) % LOOP
        val samples = tracks.map { sample(it, t) }
        val f = ElasticFoodCardsFrame()
        f.q = DoubleArray(6) { samples[min(it, 4)].first }; f.qVel = DoubleArray(6) { samples[min(it, 4)].second }
        f.handBX = samples[5].first; f.handBY = samples[6].first; f.handAngle = samples[7].first
        f.grip = samples[8].first; f.gatherX = samples[9].first; f.gatherY = samples[10].first
        f.reliefAmplitude = samples[11].first; f.fanWidth = samples[12].first
        f.scalarVel[0] = samples[5].second; f.scalarVel[1] = samples[6].second; f.scalarVel[2] = samples[7].second
        f.scalarVel[4] = samples[8].second; f.scalarVel[5] = samples[9].second; f.scalarVel[6] = samples[10].second
        f.scalarVel[7] = samples[11].second; f.scalarVel[8] = samples[12].second
        val u = ElasticFoodCardsMath.clamp((f.grip - 0.85) / 0.15); val lock = ElasticFoodCardsMath.smoother(u)
        val lockV = ElasticFoodCardsMath.smootherSlope(u) * f.scalarVel[4] / 0.15
        val r = ElasticFoodCardsMath.rotated(4.0, 16.0, f.handAngle); val angleV = f.scalarVel[2] * Math.PI / 180
        val bx = f.gatherX + r.first; val by = f.gatherY + r.second
        val bxV = f.scalarVel[5] - r.second * angleV; val byV = f.scalarVel[6] + r.first * angleV
        f.scalarVel[0] = (1 - lock) * f.scalarVel[0] + lock * bxV + lockV * (bx - f.handBX)
        f.scalarVel[1] = (1 - lock) * f.scalarVel[1] + lock * byV + lockV * (by - f.handBY)
        f.handBX = ElasticFoodCardsMath.mix(f.handBX, bx, lock); f.handBY = ElasticFoodCardsMath.mix(f.handBY, by, lock)
        val visibility = ElasticFoodCardsMath.clamp((f.handBY - 20) / 26)
        f.handAlpha = ElasticFoodCardsMath.smoother(visibility)
        f.scalarVel[3] = ElasticFoodCardsMath.smootherSlope(visibility) / 26 * f.scalarVel[1]
        val reach = sample(tail, (t + 2.2) % LOOP); f.tailY = reach.first; f.scalarVel[9] = reach.second
        return f
    }
    fun recoveryPhase(origin: ElasticFoodCardsFrame) = 0.55 + 0.25 * ElasticFoodCardsMath.clamp(origin.grip)
    fun recoveryDuration(origin: ElasticFoodCardsFrame) = 0.2 + 2.2 - recoveryPhase(origin)
    fun recovery(elapsed: Double, origin: ElasticFoodCardsFrame): ElasticFoodCardsFrame {
        val phase = recoveryPhase(origin); val first = source(phase)
        if (elapsed >= recoveryDuration(origin)) return frame(0.0)
        fun mapped(sourceTime: Double): ElasticFoodCardsFrame {
            val f = source(sourceTime)
            for (i in 0..5) { f.q[i] *= origin.q[i]; f.qVel[i] *= origin.q[i] }
            val amplitude = ElasticFoodCardsMath.clamp(max(origin.q[0], origin.reliefAmplitude / 24), 0.0, 1.2)
            f.reliefAmplitude *= amplitude; f.scalarVel[7] *= amplitude
            val u = ElasticFoodCardsMath.clamp((sourceTime - 1.6) / 0.6)
            val fade = 1 - u * u * u * (10 + u * (-15 + 6 * u))
            val fadeV = if (u > 0 && u < 1) -30 * u * u * (1 - u) * (1 - u) / 0.6 else 0.0
            val dx = origin.handBX - first.handBX; val dy = min(0.0, origin.handBY - first.handBY)
            f.handBX += dx * fade; f.handBY += dy * fade; f.gatherX += dx * fade; f.gatherY += dy * fade
            f.scalarVel[0] += dx * fadeV; f.scalarVel[1] += dy * fadeV; f.scalarVel[5] += dx * fadeV; f.scalarVel[6] += dy * fadeV
            val startVisibility = ElasticFoodCardsMath.smoother((min(origin.handBY, first.handBY) - 20) / 26)
            val uVisibility = ElasticFoodCardsMath.clamp((f.handBY - 20) / 26)
            val visibility = ElasticFoodCardsMath.smoother(uVisibility)
            val visibilityV = ElasticFoodCardsMath.smootherSlope(uVisibility) / 26 * f.scalarVel[1]
            val ratio = if (startVisibility > 0.00001) ElasticFoodCardsMath.clamp(visibility / startVisibility) else 0.0
            f.handAlpha = origin.handAlpha * ElasticFoodCardsMath.smoother(ratio)
            f.scalarVel[3] = if (ratio > 0 && ratio < 1) origin.handAlpha * ElasticFoodCardsMath.smootherSlope(ratio) * visibilityV / startVisibility else 0.0
            return f
        }
        val target = mapped(phase)
        return if (elapsed < 0.2) origin.bridged(target, elapsed, 0.2) else mapped(phase + elapsed - 0.2)
    }
}

private data class ElasticFoodCardsTargetEvent(val time: Double, val target: ElasticFoodCardsFrame, val expanding: Boolean, val scalarTarget: DoubleArray)

internal class ElasticFoodCardsDrive(initial: ElasticFoodCardsFrame, origin: Double) {
    private var initial = initial.deepCopy()
    private var origin = origin
    private val events = ArrayList<ElasticFoodCardsTargetEvent>()
    fun frame(now: Double): ElasticFoodCardsFrame {
        val result = initial.deepCopy(); val initialValues = initial.values; val resultValues = initialValues.copyOf()
        for (i in 0..15) {
            val band = i < 6; val j = if (band) i else i - 6
            var x = if (band) initial.q[j] else initialValues[j]
            var v = if (band) initial.qVel[j] else initial.scalarVel[j]
            var target = x; var date = origin; var z = 0.69; var omega = 5.0
            for (event in events) {
                val delay = if (band) min(j, 4) * (if (event.expanding) 1.0 / 30 else 2.0 / 30) else 0.0
                val arrival = event.time + delay
                if (arrival > now) break
                val pair = ElasticFoodCardsMath.oscillator(x, v, target, max(0.0, arrival - date), z, omega)
                x = pair.first; v = pair.second; date = arrival
                target = if (band) event.target.q[j] else event.scalarTarget[j]
                z = if (event.expanding) 0.69 else 0.64; omega = if (event.expanding) 5.0 else 7.1
            }
            val pair = ElasticFoodCardsMath.oscillator(x, v, target, max(0.0, now - date), z, omega)
            if (band) { result.q[j] = pair.first; result.qVel[j] = pair.second }
            else { resultValues[j] = pair.first; result.scalarVel[j] = pair.second }
        }
        result.values = resultValues; result.q[5] = result.q[4]; result.qVel[5] = result.qVel[4]
        return result
    }
    fun append(target: ElasticFoodCardsFrame, time: Double, expanding: Boolean) {
        if (events.size >= 96) { initial = frame(time); origin = time; events.clear() }
        events.add(ElasticFoodCardsTargetEvent(time, target.deepCopy(), expanding, target.values))
    }
}

internal data class ElasticFoodCardsHandMaterial(
    val closure: Double, val lift: Double, val weights: DoubleArray,
    val castX: Double, val castY: Double, val castBlur: Double, val castAlpha: Double,
    val contactWidth: Double, val contactHeight: Double, val contactBlur: Double, val contactAlpha: Double,
)

/** R2 shadow and pose parameters are pure functions of the same resolved hand frame. */
internal fun elasticFoodCardsHandMaterial(f: ElasticFoodCardsFrame): ElasticFoodCardsHandMaterial {
    val g = ElasticFoodCardsMath.clamp(f.grip)
    val first = ElasticFoodCardsMath.smoother(2 * g); val second = ElasticFoodCardsMath.smoother(2 * g - 1)
    val hover = ElasticFoodCardsMath.softClamp((194 - f.handBY) / 10)
    val lift = ElasticFoodCardsMath.smoother((180 - f.handBY) / 140) * ElasticFoodCardsMath.smoother((g - 0.65) / 0.35)
    return ElasticFoodCardsHandMaterial(g, lift, doubleArrayOf(1 - first, first - second, second),
        ElasticFoodCardsMath.mix(4 * (2 * ElasticFoodCardsMath.softClamp((f.handAngle / 24 + 1) / 2) - 1), 3.0, g),
        ElasticFoodCardsMath.mix(12 + 32 * hover, 18.0, g) + 9 * lift,
        ElasticFoodCardsMath.mix(6 + 6 * hover, 7.0, g) + 4 * lift,
        ElasticFoodCardsMath.mix(0.20 + 0.02 * hover, 0.26, g) * (1 - 0.28 * lift),
        (54 - 14 * g) * (1 - 0.25 * lift), (18 - 6 * g) * (1 - 0.35 * lift),
        5 - 2 * g + 1.5 * lift, (0.10 + 0.06 * g) * (1 - lift))
}
