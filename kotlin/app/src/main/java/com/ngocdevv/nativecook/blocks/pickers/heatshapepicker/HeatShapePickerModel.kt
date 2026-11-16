package com.ngocdevv.nativecook.blocks.pickers.heatshapepicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP = 8.0

internal data class HeatShapePickerPose(val p: Double, val c: Double, val k: Double, val phi: Double, val h: Double)
internal data class HeatShapePickerLayer(val pose: HeatShapePickerPose, val opacity: Double = 1.0)

internal object HeatShapePickerModel {
    val names = listOf("Boost", "Warm", "Eco", "Breeze")
    val captions = listOf("Full power for ten minutes", "Steady 22° through the evening", "Holds 18° and saves energy", "Fan only, no heat")
    val modes = listOf(
        HeatShapePickerPose(.62, 0.0, 1.16, 0.0, .50),
        HeatShapePickerPose(2.0, 0.0, .96, PI / 4, 1.25),
        HeatShapePickerPose(4.6, 0.0, .92, PI / 2, 2.0),
        HeatShapePickerPose(2.6, .26, 1.04, 3 * PI / 4, 2.75),
        HeatShapePickerPose(.62, 0.0, 1.16, PI, 3.50),
    )
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, m: Double) = a + (b - a) * m
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun ease(x: Double): Double { val u = clamp(x); return if (u < .5) 4 * u * u * u else 1 - (2 - 2 * u).pow(3) / 2 }
    fun out(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun back(x: Double): Double { val u = clamp(x) - 1; return 1 + 2.2 * u * u * u + 1.2 * u * u }
    fun spring(age: Double, response: Double, damping: Double): Double {
        if (age <= 0) return 0.0
        val w = 2 * PI / response; val wd = w * sqrt(1 - damping * damping)
        val v = 1 - exp(-damping * w * age) * (cos(wd * age) + damping * w / wd * sin(wd * age))
        return mix(v, 1.0, smooth((age - 1.0) / .2))
    }
    fun interpolate(a: HeatShapePickerPose, b: HeatShapePickerPose, m: Double) = HeatShapePickerPose(
        exp(mix(ln(a.p), ln(b.p), m)), mix(a.c, b.c, m), mix(a.k, b.k, m), mix(a.phi, b.phi, m), mix(a.h, b.h, m),
    )

    /** The single outline formula. A null time disables wobble for glyphs and reduced motion. */
    fun radius(theta: Double, p: Double, c: Double, k: Double, phi: Double, t: Double?): Double {
        val a = theta - phi
        val ac = sqrt(cos(a).pow(2) + .004); val ass = sqrt(sin(a).pow(2) + .004)
        val rp = (ac.pow(p) + ass.pow(p)).pow(-1 / p)
        val lobe = 1 - c * (1 - abs(sin(2 * a))).pow(2.2)
        val tau = 2 * PI * (t ?: 0.0) / LOOP
        val wob = if (t == null) 1.0 else 1 + .018 * sin(3 * theta + 2 * tau) + .012 * sin(5 * theta - 3 * tau + 1.7)
        return 110 * k * rp * lobe * wob
    }

    private val positions = doubleArrayOf(0.0, .12, .25, .38, .50, .62, .78, .90, 1.0)
    private val colors = intArrayOf(0x2B2BE8, 0x14051F, 0xE3210B, 0xFF7A1A, 0xFFD75A, 0xFFFFFF, 0xC9C4FF, 0x6A63FF, 0x2B2BE8)
    fun color(hex: Int, alpha: Double = 1.0) = ((clamp(alpha) * 255 + .5).toInt() shl 24) or hex
    /** Interpolate channels in sRGB, matching Swift and Skia. */
    fun ramp(value: Double, alpha: Double = 1.0): Int {
        val x = value - floor(value)
        val j = (0..7).firstOrNull { x < positions[it + 1] } ?: 7
        val m = (x - positions[j]) / (positions[j + 1] - positions[j])
        fun channel(shift: Int) = (mix(((colors[j] shr shift) and 255).toDouble(), ((colors[j + 1] shr shift) and 255).toDouble(), m) + .5).toInt()
        return color((channel(16) shl 16) or (channel(8) shl 8) or channel(0), alpha)
    }
    fun gradient(h: Double) = doubleArrayOf(.20, .10, 0.0, -.10, -.20).map { ramp(h + it) }.toIntArray()
    fun rounded(x: Double) = if (x >= 0) floor(x + .5) else -floor(-x + .5)
    fun coalesced(layers: List<HeatShapePickerLayer>): List<HeatShapePickerLayer> {
        val result = mutableListOf<HeatShapePickerLayer>()
        for (layer in layers) {
            if (layer.opacity <= .001) continue
            val p = layer.pose
            val i = result.indexOfFirst { item ->
                val q = item.pose; val dh = p.h - q.h
                abs(p.p - q.p) + abs(p.c - q.c) + abs(p.k - q.k) < .00001 &&
                    abs(sin(2 * (p.phi - q.phi))) < .00001 && abs(dh - rounded(dh)) < .00001
            }
            if (i >= 0) result[i] = result[i].copy(opacity = result[i].opacity + layer.opacity) else result.add(layer)
        }
        return result
    }
}

internal data class HeatShapePickerFrame(
    var pose: HeatShapePickerPose = HeatShapePickerModel.modes[0],
    var layers: List<HeatShapePickerLayer> = listOf(HeatShapePickerLayer(HeatShapePickerModel.modes[0])),
    var time: Double = 0.0,
    var morph: Double = 0.0,
    var glow: Double = 1.0,
    var ring: Double = 0.0,
    var selected: Int = 0,
    var labels: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0),
    var labelY: DoubleArray = DoubleArray(4),
    var chips: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0),
) {
    companion object {
        fun automatic(time: Double, reduced: Boolean): HeatShapePickerFrame {
            val m = HeatShapePickerModel
            val t = max(0.0, time) % LOOP; val i = (t / 2).toInt(); val next = (i + 1) % 4; val s = t - i * 2
            val u = m.seg(s, 1.1, 2.0); val progress = m.ease(u)
            val f = HeatShapePickerFrame(time = t, selected = if (s < 1.55) i else next)
            f.ring = m.mix(i.toDouble(), next.toDouble(), if (reduced) m.seg(s, 1.25, 1.75) else m.back(m.seg(s, 1.25, 1.75)))
            f.chips = DoubleArray(4)
            val chip = m.seg(s, 1.45, 1.65); f.chips[i] = 1 - chip; f.chips[next] = chip
            f.labels = DoubleArray(4)
            if (reduced) {
                val fade = m.smooth(m.seg(s, 1.55, 1.85))
                f.pose = m.modes[f.selected]
                f.layers = listOf(HeatShapePickerLayer(m.modes[i], 1 - fade), HeatShapePickerLayer(m.modes[next], fade))
                f.labels[i] = 1 - fade; f.labels[next] = fade
                return f
            }
            val pose = m.interpolate(m.modes[i], m.modes[i + 1], progress)
            val scale = when {
                s < 1.1 -> 1 + .022 * sin(2 * PI * s / 1.1)
                u < .28 -> m.mix(1.0, .90, m.smooth(u / .28))
                u < .72 -> m.mix(.90, 1.07, m.smooth((u - .28) / .44))
                else -> m.mix(1.07, 1.0, m.smooth((u - .72) / .28))
            }
            f.pose = pose.copy(k = pose.k * scale)
            if (s < 1.1) f.pose = f.pose.copy(h = f.pose.h + .045 * sin(2 * PI * s / 1.1))
            f.layers = listOf(HeatShapePickerLayer(f.pose)); f.morph = progress
            f.glow = if (s < 1.1) 1 + .08 * sin(PI * s) else 1 + .5 * sin(PI * progress)
            val old = m.seg(s, 1.35, 1.55).pow(3); val new = m.out(m.seg(s, 1.55, 1.85))
            f.labels[i] = 1 - old; f.labelY[i] = -10 * old
            f.labels[next] = new; f.labelY[next] = 10 * (1 - new)
            return f
        }
    }
}

internal data class HeatShapePickerTransition(val source: HeatShapePickerFrame, val target: Int, val at: Double, val settling: Boolean = false) {
    fun frame(now: Double, reduced: Boolean): HeatShapePickerFrame {
        val m = HeatShapePickerModel
        val age = max(0.0, now - at); val fade = m.smooth(age / .3)
        val f = source.copy(labels = source.labels.copyOf(), labelY = source.labelY.copyOf(), chips = source.chips.copyOf())
        var end = m.modes[target]
        if (settling) {
            end = end.copy(h = end.h + m.rounded(source.pose.h - end.h), phi = end.phi + m.rounded((source.pose.phi - end.phi) / (PI / 2)) * (PI / 2))
        } else {
            while (end.h < source.pose.h) end = end.copy(h = end.h + 1)
            while (end.phi < source.pose.phi) end = end.copy(phi = end.phi + PI)
        }
        val progress = if (reduced) fade else m.spring(age, .55, .68)
        f.selected = target; f.pose = m.interpolate(source.pose, end, progress)
        f.time = m.mix(source.time, target * 2.0, m.smooth(age / 1.2))
        f.morph = if (reduced) 0.0 else if (settling) m.mix(source.morph, 0.0, fade) else m.clamp(progress)
        f.glow = m.mix(source.glow, 1.0, fade) + if (reduced || settling) 0.0 else .5 * sin(PI * f.morph)
        if (reduced) {
            f.pose = end
            f.layers = m.coalesced(source.layers.map { HeatShapePickerLayer(it.pose, it.opacity * (1 - fade)) } + HeatShapePickerLayer(end, fade))
        } else {
            if (!settling) f.pose = f.pose.copy(k = f.pose.k * (1 + .8 * max(0.0, progress - 1)))
            // One idle heat cycle ends at the two-second autoplay resume boundary.
            val heatTime = max(0.0, age - .9)
            f.pose = f.pose.copy(h = f.pose.h + .045 * sin(2 * PI * heatTime / 1.1))
            f.layers = listOf(HeatShapePickerLayer(f.pose))
        }
        val ring = if (reduced) fade else m.spring(age, .4, .72)
        f.ring = m.mix(source.ring, target.toDouble(), ring)
        for (i in 0..3) {
            val to = if (i == target) 1.0 else 0.0
            f.chips[i] = m.mix(source.chips[i], to, m.clamp(age / .2))
            f.labels[i] = m.mix(source.labels[i], to, fade)
            f.labelY[i] = if (reduced) 0.0 else if (i == target) m.mix(if (source.labels[i] > 0) source.labelY[i] else 10.0, 0.0, m.out(age / .3)) else source.labelY[i] - 10 * fade
        }
        return f
    }
}

/** Retargeted only by touch events; position and velocity are analytical functions of time. */
internal class HeatShapePickerSpring {
    private var from = 0.0; private var target = 0.0; private var velocity = 0.0; private var at = 0.0
    private var response = .5; private var damping = .5
    fun state(now: Double): Pair<Double, Double> {
        val age = max(0.0, now - at)
        if (age >= 1.4) return target to 0.0
        val w = 2 * PI / response; val a = damping * w; val b = w * sqrt(1 - damping * damping)
        val d = from - target; val k = (velocity + a * d) / b; val e = exp(-a * age)
        val tail = 1 - HeatShapePickerModel.smooth((age - 1.2) / .2)
        return (target + e * (d * cos(b * age) + k * sin(b * age)) * tail) to
            (e * ((k * b - a * d) * cos(b * age) + (-d * b - a * k) * sin(b * age)) * tail)
    }
    fun retarget(value: Double, now: Double, releasing: Boolean = false) {
        val current = state(now); from = current.first; velocity = current.second; target = value; at = now
        response = if (releasing) .5 else .35; damping = if (releasing) .5 else .68
    }
}

internal data class HeatShapePickerLayout(val width: Double, val height: Double, val top: Double, val bottom: Double) {
    val sx get() = width / 393
    val sy get() = height / 852
    val shift get() = max(0.0, top - 59) - max(0.0, bottom - 34)
    fun x(value: Double) = value * sx
    fun y(value: Double) = value * sy + shift
}

internal class HeatShapePickerMotion {
    var now by mutableDoubleStateOf(System.nanoTime() / 1e9)
    var epoch = now
    var transition: HeatShapePickerTransition? = null
    var heldFrame: HeatShapePickerFrame? = null
    var dragging = false
    var live by mutableStateOf(false)
    var dragX = HeatShapePickerSpring(); var dragY = HeatShapePickerSpring(); var grip = HeatShapePickerSpring()
    var direction = 0.0
    fun frame(autoPlay: Boolean, reduced: Boolean) = heldFrame ?: transition?.frame(now, reduced)
        ?: HeatShapePickerFrame.automatic(if (autoPlay) max(0.0, now - epoch) else 0.0, reduced)
    fun reset() {
        now = System.nanoTime() / 1e9; epoch = now; transition = null; heldFrame = null; dragging = false; live = false
        dragX = HeatShapePickerSpring(); dragY = HeatShapePickerSpring(); grip = HeatShapePickerSpring()
    }
}
