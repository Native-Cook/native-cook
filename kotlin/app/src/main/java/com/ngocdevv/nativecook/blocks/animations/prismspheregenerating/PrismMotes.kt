package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import android.graphics.*
import kotlin.math.*

internal class PrismMotes {
    data class Mote(val x: Double, val y: Double, val z: Double, val radius: Double,
                    val alpha: Double, val colour: Int, val phase: Double, val cycles: Int,
                    val rise: Double, val sway: Double, val twinkle: Double, val throwDistance: Double, val bokeh: Boolean)
    data class Point(val x: Double, val y: Double, val alpha: Double)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val halo = BlurMaskFilter(((1.1 - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
    private val bokeh = BlurMaskFilter(((3 - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
    private val colours = intArrayOf(0xFFEDE4FF.toInt(), 0xFFC4B0FF.toInt(), 0xFF9F7BFF.toInt(), 0xFFF0ABFC.toInt())
    val motes = makeMotes()

    fun draw(canvas: Canvas, back: Boolean, motion: PrismMotion, scale: Double) {
        val time = if (motion.reduced) 0.0 else motion.clock
        for (mote in motes) {
            if ((mote.z < 0) != back) continue
            val point = at(mote, time, motion.values.charge, motion.burstAge, motion.burstGain, motion.values.flash, scale)
            paint.color = colours[mote.colour]
            paint.alpha = (point.alpha * 0.25 * 255).roundToInt().coerceIn(0, 255)
            paint.maskFilter = halo
            canvas.drawCircle((201 + point.x).toFloat(), (472 + point.y).toFloat(), mote.radius.toFloat() * 3, paint)
            paint.alpha = (point.alpha * 255).roundToInt().coerceIn(0, 255)
            paint.maskFilter = if (mote.bokeh) bokeh else null
            canvas.drawCircle((201 + point.x).toFloat(), (472 + point.y).toFloat(), mote.radius.toFloat(), paint)
        }
    }

    companion object {
        fun makeMotes(): List<Mote> {
            var seed = 62417L
            fun random(): Double {
                seed = (seed * 1664525 + 1013904223) and 0xffffffffL
                return seed / 4294967296.0
            }
            return List(64) { i ->
                val angle = random() * PI * 2
                val distance = 124 * (1.08 + 1.52 * random())
                val back = i < 38
                val bokeh = i >= 58
                Mote(cos(angle) * distance * 0.9, sin(angle) * distance * 1.25,
                    (if (back) -1 else 1) * (0.05 + 0.95 * random()),
                    if (bokeh) 4 + 3 * random() else if (back) 0.7 + 0.9 * random() else 1 + 1.2 * random(),
                    if (bokeh) 0.09 + 0.07 * random() else 0.22 + 0.42 * random(),
                    if (bokeh) 2 else if (random() < 0.1) 3 else floor(random() * 3).toInt(),
                    random(), if (random() < 0.5) 1 else 2, 40 + 70 * random(),
                    4 + 4 * random(), random(), 24 + 40 * random(), bokeh)
            }
        }

        private fun boxDistance(x: Double, y: Double, cy: Double, halfWidth: Double, halfHeight: Double) =
            hypot(max(0.0, abs(x) - halfWidth), max(0.0, abs(y - cy) - halfHeight))

        fun at(m: Mote, clock: Double, charge: Double, b: Double, gain: Double, flash: Double, scale: Double): Point {
            val u = PrismTimeline.modulo(m.cycles * clock / 10 + m.phase, 1.0)
            val x = m.x + m.sway * sin(2 * PI * (m.cycles * clock / 10 + m.phase))
            val y = m.y - m.rise * (u - 0.5)
            val distance = hypot(x, y)
            val thrown = if (b >= 0) m.throwDistance * gain * (1 - exp(-b / 0.12)) * exp(-b / 0.9) else 0.0
            val radial = -0.16 * (distance - 124) * charge + thrown
            val ratio = 1 + radial / max(distance, 1.0)
            val twinkle = 0.75 + 0.25 * sin(2 * PI * (clock / 10 + m.twinkle))
            var alpha = m.alpha * sin(PI * u).pow(1.5) * twinkle * (1 + 1.5 * flash)
            if (m.z < 0) alpha *= PrismTimeline.smooth(124 * scale, 124 * scale + 6, distance * ratio)
            val px = x * ratio
            val py = y * ratio
            val reach = m.radius * if (m.bokeh) 2.5 else 1.0
            alpha *= 0.3 + 0.7 * exp(-(px * px) / (150 * 150))
            alpha *= PrismTimeline.smooth(0.0, 14.0, boxDistance(px, py, -232.0, 150.0, 44.0) - reach)
            alpha *= PrismTimeline.smooth(0.0, 14.0, boxDistance(px, py, 200.0, 96.0, 12.0) - reach)
            return Point(px, py, min(if (m.bokeh) 0.16 else 1.0, alpha))
        }
    }
}
