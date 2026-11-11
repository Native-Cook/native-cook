package com.ngocdevv.nativecook.blocks.media.glassvoicecapsule

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

internal const val LOOP = 9.0

internal object GlassVoiceCapsuleMath {
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun out(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun spring(age: Double, response: Double, damping: Double, duration: Double): Double {
        if (age <= 0) return 0.0
        if (age >= duration) return 1.0
        val w = 2 * PI / response
        val wd = w * sqrt(1 - damping * damping)
        val value = 1 - exp(-damping * w * age) * (cos(wd * age) + damping * w / wd * sin(wd * age))
        val tail = smooth((age - duration + 0.16) / 0.16)
        return value + (1 - value) * tail
    }
    fun voice(seconds: Double) = clamp(0.46 + 0.30 * sin(2 * PI * 1.5 * seconds) + 0.24 * sin(2 * PI * 3.5 * seconds + 1.1))
    fun filteredVoice(seconds: Double): Double {
        val dt = 0.54 / 16
        var sum = 0.0
        var weight = 0.0
        repeat(16) { i ->
            val age = (i + 0.5) * dt
            val w = exp(-age / 0.09)
            sum += voice(max(0.0, seconds - age)) * w
            weight += w
        }
        return sum / weight
    }
    fun rubber(raw: Double) = if (raw < 0) raw / (1 + abs(raw) * 14) else raw
}

/** Immutable values; the Canvas reads them directly from time, without screen recomposition. */
internal data class GlassVoiceCapsuleFrame(
    val time: Double = 0.0,
    val amount: Double = 0.0,
    val scale: Double = 1.0,
    val content: Double = 0.0,
    val droplet: Double = 0.2,
    val level: Double = 0.18,
    val seconds: Double = 0.0,
    val digitAge: Double = 1.0,
    val timerScale: Double = 0.86,
    val floatY: Double = 0.0,
    val angle: Double = 0.0,
    val micPulse: Double = 1.0,
    val micRed: Double = 0.0,
    val listening: Double = 0.0,
    val saved: Double = 0.0,
    val sheen: Double = -1.0,
    val recording: Boolean = false,
    val digitRise: Boolean = true,
) {
    val width get() = 84 + 238 * amount
    val height get() = 84 + 12 * amount + 5 * level * GlassVoiceCapsuleMath.clamp(amount)

    companion object {
        fun automatic(time: Double): GlassVoiceCapsuleFrame {
            val m = GlassVoiceCapsuleMath
            val t = time % LOOP
            val start = m.spring(t - 0.92, 0.55, 0.62, 0.98)
            val stop = m.spring(t - 6.82, 0.55, 0.62, 0.98)
            val amount = m.rubber(start * (1 - stop))
            val r = m.clamp(amount)
            val seconds = min(5.0, max(0.0, t - 1.7))
            val level = when {
                t >= 1.7 && t < 6.82 -> r * m.voice(seconds)
                t >= 6.82 -> m.voice(5.0) + (0.18 - m.voice(5.0)) * m.out((t - 6.82) / 0.78)
                else -> 0.18
            }
            val floating = t >= 1.7 && t < 6.7
            val phase = 2 * PI * (t - 1.7) / 5
            val envelope = if (floating) m.smooth((t - 1.7) / 0.25) * (1 - m.smooth((t - 6.45) / 0.25)) else 0.0
            return GlassVoiceCapsuleFrame(
                time = t, amount = amount,
                scale = if (t < 0.92) 1 - 0.07 * m.out((t - 0.8) / 0.12) else 0.93 + 0.07 * start,
                content = m.out((t - 1.15) / 0.45) * (1 - m.clamp((t - 6.82) / 0.38).pow(3)),
                timerScale = (0.86 + 0.14 * m.out((t - 1.15) / 0.45)) * (1 - 0.7 * m.out((t - 6.82) / 0.38)),
                droplet = (0.2 + 0.8 * m.spring(t - 1.2, 0.45, 0.58, 0.8)) *
                    (1 - 0.12 * m.out((t - 6.7) / 0.12)) * (1 - 0.7 * m.out((t - 6.82) / 0.38)),
                seconds = seconds, digitAge = max(0.0, t - 1.7 - floor(seconds)), level = level,
                micRed = m.out((t - 0.92) / 0.3) * (1 - m.out((t - 6.82) / 0.58)),
                recording = t >= 0.92 && t < 6.82,
                listening = m.out((t - 0.92) / 0.25) * (1 - m.out((t - 6.82) / 0.25)),
                saved = m.out((t - 6.82) / 0.25) * (1 - m.out((t - 8.02) / 0.25)),
                floatY = -6 * sin(2 * phase) * r * envelope,
                angle = 1.6 * sin(phase + 0.6) * r * envelope,
                micPulse = 1 + 0.08 * sin(PI * (t - 1.7)).pow(2) * envelope,
                sheen = if (t > 7.9 && t < 8.7) m.smooth((t - 7.9) / 0.8) else -1.0,
            )
        }
    }
}

internal data class GlassVoiceCapsuleInteraction(
    val source: GlassVoiceCapsuleFrame,
    val at: Double,
    val recording: Boolean,
    val recordAt: Double,
    val stoppedAt: Double?,
) {
    fun frame(now: Double, reduced: Boolean): GlassVoiceCapsuleFrame {
        val m = GlassVoiceCapsuleMath
        val age = max(0.0, now - at)
        val p = if (reduced) m.smooth(age / 0.3) else m.spring(age, 0.55, 0.62, 0.98)
        val q = m.out(age / 0.38)
        val target = if (recording) 1.0 else 0.0
        val raw = source.amount + (target - source.amount) * p
        val lower = min(0.0, source.amount)
        val over = raw - lower
        val amount = if (over < 0) lower + over / (1 + abs(over) * 14) else raw
        val r = m.clamp(amount)
        val d = if (reduced) q else m.spring(age, 0.45, 0.58, 0.8)
        val seconds = max(0.0, (stoppedAt ?: now) - recordAt)
        val level = if (recording) { if (reduced) 0.55 else m.filteredVoice(seconds) } else 0.18
        val saved = stoppedAt?.let { 1 - m.out((now - it - 1.2) / 0.25) } ?: 0.0
        val phase = 2 * PI * seconds / 5
        val floatY = if (recording && !reduced) -6 * sin(2 * phase) * r else 0.0
        val angle = if (recording && !reduced) 1.6 * sin(phase + 0.6) * r else 0.0
        return source.copy(
            time = source.time + age, amount = amount, scale = source.scale + (1 - source.scale) * p,
            content = source.content + (target - source.content) * q,
            timerScale = source.timerScale + ((if (recording) 1.0 else 0.3) - source.timerScale) * q,
            droplet = source.droplet + ((if (recording) 1.0 else 0.3) - source.droplet) * d,
            seconds = seconds, digitAge = if (floor(seconds) == floor(source.seconds)) source.digitAge + age else max(0.0, now - recordAt - floor(seconds)),
            level = source.level + (level - source.level) * m.smooth(age / if (reduced) 0.4 else 0.09),
            micRed = source.micRed + (target - source.micRed) * m.out(age / 0.3),
            listening = source.listening + (target - source.listening) * m.out(age / 0.25),
            saved = source.saved + (saved - source.saved) * m.out(age / 0.25),
            floatY = source.floatY + (floatY - source.floatY) * m.smooth(age / 0.3),
            angle = source.angle + (angle - source.angle) * m.smooth(age / 0.3),
            micPulse = if (reduced) 1.0 else 1 + 0.08 * sin(PI * seconds).pow(2) * r,
            recording = recording, sheen = -1.0, digitRise = !reduced,
        )
    }
}

internal data class GlassVoiceCapsulePoint(val x: Double = 0.0, val y: Double = 0.0)

internal class GlassVoiceCapsuleDrag {
    private var from = GlassVoiceCapsulePoint()
    private var target = GlassVoiceCapsulePoint()
    private var velocity = GlassVoiceCapsulePoint()
    private var at = 0.0
    private var releasing = false
    fun value(now: Double) = state(now).first
    private fun state(now: Double): Pair<GlassVoiceCapsulePoint, GlassVoiceCapsulePoint> {
        val t = max(0.0, now - at)
        if (t >= 1.2) return target to GlassVoiceCapsulePoint()
        val w = 2 * PI / if (releasing) 0.5 else 0.38
        val z = if (releasing) 0.55 else 0.6
        val a = z * w
        val b = w * sqrt(1 - z * z)
        val e = exp(-a * t)
        fun axis(start: Double, end: Double, speed: Double): Pair<Double, Double> {
            val d = start - end
            val k = (speed + a * d) / b
            return end + e * (d * cos(b * t) + k * sin(b * t)) to
                e * ((k * b - a * d) * cos(b * t) + (-d * b - a * k) * sin(b * t))
        }
        val x = axis(from.x, target.x, velocity.x)
        val y = axis(from.y, target.y, velocity.y)
        return GlassVoiceCapsulePoint(x.first, y.first) to GlassVoiceCapsulePoint(x.second, y.second)
    }
    fun retarget(next: GlassVoiceCapsulePoint, now: Double, releasing: Boolean) {
        val current = state(now)
        from = current.first; velocity = current.second; target = next; at = now; this.releasing = releasing
    }
}

internal class GlassVoiceCapsuleController(val autoPlay: Boolean, val reduced: Boolean, now: Double) {
    var epoch = now
    var interaction: GlassVoiceCapsuleInteraction? = null
    var drag = GlassVoiceCapsuleDrag()
    var touching = false
    private var idleAt = Double.POSITIVE_INFINITY
    private var resetAt = Double.POSITIVE_INFINITY
    fun reset(now: Double) {
        epoch = now; interaction = null; touching = false; drag = GlassVoiceCapsuleDrag()
        idleAt = Double.POSITIVE_INFINITY; resetAt = Double.POSITIVE_INFINITY
    }
    fun frame(now: Double) = interaction?.frame(now, reduced)
        ?: GlassVoiceCapsuleFrame.automatic(if (autoPlay && !reduced) max(0.0, now - epoch) % LOOP else 0.0)
    fun begin(now: Double) {
        idleAt = Double.POSITIVE_INFINITY; resetAt = Double.POSITIVE_INFINITY
        if (interaction == null) {
            val f = frame(now)
            interaction = GlassVoiceCapsuleInteraction(f, now, f.recording, now - f.seconds, if (f.saved > 0) now else null)
        }
    }
    fun toggle(now: Double) {
        begin(now)
        val f = frame(now)
        val recording = !(interaction?.recording ?: false)
        interaction = GlassVoiceCapsuleInteraction(f, now, recording,
            if (recording) now else interaction?.recordAt ?: now, if (recording) null else now)
        scheduleResume(now)
    }
    fun scheduleResume(now: Double) { idleAt = now + 2; resetAt = Double.POSITIVE_INFINITY }
    fun tick(now: Double) {
        if (!touching && now >= idleAt) {
            val f = frame(idleAt)
            interaction = GlassVoiceCapsuleInteraction(f, idleAt, false, interaction?.recordAt ?: idleAt,
                interaction?.stoppedAt ?: if (f.recording) idleAt else null)
            resetAt = idleAt + 1.45
            idleAt = Double.POSITIVE_INFINITY
        }
        if (!touching && now >= resetAt) { interaction = null; epoch = resetAt; resetAt = Double.POSITIVE_INFINITY }
    }
    val awake get() = touching || interaction != null
}
