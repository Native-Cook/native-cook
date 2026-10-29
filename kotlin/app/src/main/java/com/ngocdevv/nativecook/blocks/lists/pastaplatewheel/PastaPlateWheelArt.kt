package com.ngocdevv.nativecook.blocks.lists.pastaplatewheel

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The menu and its artwork. Every dish is drawn in code, top-down, once, into three bitmaps:
 * the plate (its light never turns), the food (it spins with the wheel) and a light layer laid
 * over the food (a soft highlight and a darker rim, fixed, so the food turns under the light).
 * Ported 1:1 from PastaPlateWheelArt.swift.
 */
internal object PastaPlateWheelArt {
    class Dish(val name: String, val line: String, val price: String, val bowl: Boolean)

    val dishes = listOf(
        Dish("Spaghetti Pomodoro", "Slow tomato, torn basil, olive oil", "$12.50", false),
        Dish("Penne Arrabbiata", "Fiery tomato, garlic, chilli flakes", "$13.00", true),
        Dish("Farfalle al Pesto", "Basil pesto, pine nuts, pecorino", "$14.50", false),
        Dish("Fettuccine Bianca", "Butter, cream, cracked pepper", "$15.00", true),
        Dish("Baked Mac & Cheese", "Three cheeses, crisp crumb top", "$11.50", false),
        Dish("Ricotta Ravioli", "Brown butter, sage, lemon zest", "$16.00", true),
        Dish("Lasagna al Forno", "Beef ragù, béchamel, golden top", "$17.50", false),
        Dish("Shells & Peas", "Cream, sweet peas, smoked ham", "$13.50", true),
    )

    /** The largest plate's diameter (design units); bitmaps are drawn at this size and only scaled down. */
    const val PLATE_SIZE = 164.0
    const val SCALE = 3f

    fun foodFraction(bowl: Boolean) = if (bowl) 0.76 else 0.75

    class Atlas(val plates: List<Bitmap>, val lights: List<Bitmap>, val foods: List<Bitmap>, val grain: Bitmap)

    val atlas: Atlas by lazy {
        Atlas(
            plates = listOf(plateImage(false), plateImage(true)),
            lights = listOf(lightImage(false), lightImage(true)),
            foods = dishes.indices.map { foodImage(it) },
            grain = grainImage(),
        )
    }

    // Colour and hash

    fun hash(n: Double): Double {
        val v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    fun argb(hex: Int, alpha: Double = 1.0): Int =
        ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (hex and 0xFFFFFF)

    fun mix(a: Int, b: Int, t: Double): Int {
        val u = t.coerceIn(0.0, 1.0)
        fun ch(s: Int): Int {
            val x = ((a shr s) and 255).toDouble()
            val y = ((b shr s) and 255).toDouble()
            return (x + (y - x) * u).roundToInt()
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    class Stop(val hex: Int, val alpha: Double, val at: Double)

    // Pen — the few primitives every dish is drawn with

    class Pen(val c: NativeCanvas) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val shaded = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun circle(x: Double, y: Double, r: Double, hex: Int, a: Double = 1.0) {
            fill.color = argb(hex, a)
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), fill)
        }

        fun ellipse(x: Double, y: Double, rx: Double, ry: Double, angle: Double, hex: Int, a: Double = 1.0) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.rotate(Math.toDegrees(angle).toFloat())
            fill.color = argb(hex, a)
            c.drawOval(RectF(-rx.toFloat(), -ry.toFloat(), rx.toFloat(), ry.toFloat()), fill)
            c.restore()
        }

        /** An open smooth line through the points (quadratic curves between midpoints), round caps. */
        fun stroke(p: DoubleArray, width: Double, hex: Int, a: Double = 1.0) {
            line.color = argb(hex, a)
            line.strokeWidth = width.toFloat()
            c.drawPath(smooth(p, false), line)
        }

        fun blob(p: DoubleArray, hex: Int, a: Double = 1.0) {
            fill.color = argb(hex, a)
            c.drawPath(smooth(p, true), fill)
        }

        fun poly(p: DoubleArray, hex: Int, a: Double = 1.0) {
            val path = Path()
            path.moveTo(p[0].toFloat(), p[1].toFloat())
            var i = 2
            while (i < p.size) {
                path.lineTo(p[i].toFloat(), p[i + 1].toFloat()); i += 2
            }
            path.close()
            fill.color = argb(hex, a)
            c.drawPath(path, fill)
        }

        fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double, hex: Int, a: Double = 1.0) {
            fill.color = argb(hex, a)
            c.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r.toFloat(), r.toFloat(), fill)
        }

        fun radial(x: Double, y: Double, r: Double, stops: List<Stop>) {
            // A fresh opaque paint per gradient: a reused paint keeps the alpha of its last colour.
            shaded.color = 0xFF000000.toInt()
            shaded.shader = RadialGradient(
                x.toFloat(), y.toFloat(), r.toFloat(),
                IntArray(stops.size) { argb(stops[it].hex, stops[it].alpha) },
                FloatArray(stops.size) { stops[it].at.toFloat() },
                Shader.TileMode.CLAMP,
            )
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), shaded)
            shaded.shader = null
        }

        /** An arc stroke from `from` to `to` (radians, increasing angle). */
        fun arc(x: Double, y: Double, r: Double, from: Double, to: Double, width: Double, hex: Int, a: Double = 1.0) {
            line.color = argb(hex, a)
            line.strokeWidth = width.toFloat()
            val path = Path()
            path.addArc(
                RectF((x - r).toFloat(), (y - r).toFloat(), (x + r).toFloat(), (y + r).toFloat()),
                Math.toDegrees(from).toFloat(), Math.toDegrees(to - from).toFloat(),
            )
            c.drawPath(path, line)
        }

        fun ring(x: Double, y: Double, r: Double, width: Double, hex: Int, a: Double) {
            line.color = argb(hex, a)
            line.strokeWidth = width.toFloat()
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), line)
        }

        fun push(x: Double, y: Double, angle: Double, s: Double = 1.0) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.rotate(Math.toDegrees(angle).toFloat())
            c.scale(s.toFloat(), s.toFloat())
        }

        fun pop() = c.restore()

        private fun smooth(p: DoubleArray, closed: Boolean): Path {
            val n = p.size / 2
            val path = Path()
            fun mx(i: Int, j: Int) = ((p[2 * i] + p[2 * j]) / 2).toFloat()
            fun my(i: Int, j: Int) = ((p[2 * i + 1] + p[2 * j + 1]) / 2).toFloat()
            if (closed) {
                path.moveTo(mx(n - 1, 0), my(n - 1, 0))
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    path.quadTo(p[2 * i].toFloat(), p[2 * i + 1].toFloat(), mx(i, j), my(i, j))
                }
                path.close()
            } else {
                path.moveTo(p[0].toFloat(), p[1].toFloat())
                if (n > 2) for (i in 1 until n - 1) {
                    path.quadTo(p[2 * i].toFloat(), p[2 * i + 1].toFloat(), mx(i, i + 1), my(i, i + 1))
                }
                path.lineTo(p[2 * (n - 1)].toFloat(), p[2 * (n - 1) + 1].toFloat())
            }
            return path
        }
    }

    // Shared shapes

    fun strand(r: Double, a0: Double, sweep: Double, wobble: Double, freq: Double, phase: Double, steps: Int = 12): DoubleArray {
        val p = DoubleArray((steps + 1) * 2)
        for (s in 0..steps) {
            val a = a0 + sweep * s / steps
            val rr = r + wobble * sin(a * freq + phase)
            p[2 * s] = rr * cos(a); p[2 * s + 1] = rr * sin(a)
        }
        return p
    }

    fun blobPoints(x: Double, y: Double, r: Double, k1: Double, k2: Double, phase: Double, count: Int = 16): DoubleArray {
        val p = DoubleArray(count * 2)
        for (i in 0 until count) {
            val a = 2 * PI * i / count
            val rr = r * (1 + k1 * sin(3 * a + phase) + k2 * sin(5 * a + 2 * phase))
            p[2 * i] = x + rr * cos(a); p[2 * i + 1] = y + rr * sin(a)
        }
        return p
    }

    private fun DoubleArray.shift(dx: Double, dy: Double) = DoubleArray(size) { this[it] + if (it % 2 == 0) dx else dy }

    fun leaf(pen: Pen, x: Double, y: Double, len: Double, w: Double, angle: Double, hex: Int, vein: Int) {
        pen.push(x, y, angle)
        pen.blob(doubleArrayOf(-len / 2, 0.0, -len * 0.18, -w / 2, len * 0.22, -w * 0.42, len / 2, 0.0, len * 0.22, w * 0.42, -len * 0.18, w / 2), hex)
        pen.ellipse(-len * 0.05, -w * 0.16, len * 0.3, w * 0.16, 0.0, 0xFFFFFF, 0.12)
        pen.stroke(doubleArrayOf(-len * 0.48, 0.0, 0.0, -w * 0.03, len * 0.44, 0.0), len * 0.035, vein, 0.75)
        pen.pop()
    }

    fun specks(pen: Pen, seed: Double, count: Int, reach: Double, size: Double, colors: IntArray, alpha: Double) {
        for (k in 0 until count) {
            val kk = k.toDouble()
            val rr = reach * sqrt(hash(seed + kk * 4.1))
            val a = 2 * PI * hash(seed + kk * 4.1 + 1.3)
            val s = size * (0.6 + 0.8 * hash(seed + kk * 4.1 + 2.7))
            pen.ellipse(rr * cos(a), rr * sin(a), s, s * 0.62, 6.28 * hash(seed + kk * 4.1 + 3.9), colors[k % colors.size], alpha)
        }
    }

    // Dishes (unit food disc: radius 1)

    fun drawFood(pen: Pen, i: Int) {
        val seed = i * 1000.0 + 17
        when (i) {
            0 -> spaghetti(pen, seed)
            1 -> penne(pen, seed)
            2 -> farfalle(pen, seed)
            3 -> fettuccine(pen, seed)
            4 -> macAndCheese(pen, seed)
            5 -> ravioli(pen, seed)
            6 -> lasagna(pen, seed)
            else -> shells(pen, seed)
        }
    }

    private fun stops(vararg s: Stop) = s.toList()

    private fun spaghetti(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xE8C374, 1.0, 0.0), Stop(0xD9AE5C, 1.0, 0.78), Stop(0xD9AE5C, 0.0, 1.0)))
        val strands = (0 until 96).map { k ->
            val s = seed + k * 3.0
            strand(0.16 + 0.76 * sqrt(hash(s + 1)), 2 * PI * hash(s + 2), 1.0 + 1.8 * hash(s + 3), 0.035, 2.5, 6 * hash(s + 4))
        }
        for (p in strands) pen.stroke(p, 0.046, 0xA2722F, 0.5)
        strands.forEachIndexed { k, p ->
            val h = hash(seed + k * 3.0 + 5)
            pen.stroke(p, 0.03, if (h < 0.33) 0xF3D791 else if (h < 0.66) 0xEBC877 else 0xDDB25E)
            if (k % 2 == 0) pen.stroke(p.shift(-0.006, -0.006), 0.01, 0xFFF4CF, 0.55)
        }
        pen.blob(blobPoints(0.04, -0.02, 0.42, 0.1, 0.07, 1.2), 0xA92816)
        pen.radial(-0.02, -0.08, 0.4, stops(Stop(0xE5502F, 1.0, 0.0), Stop(0xD23D24, 0.9, 0.6), Stop(0xC7341F, 0.0, 1.0)))
        for (k in 0 until 18) {
            val s = seed + 300 + k * 5.0
            val rr = 0.33 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1)
            val big = hash(s + 2)
            pen.circle(0.04 + rr * cos(a), -0.02 + rr * sin(a), 0.02 + 0.035 * big,
                if (k % 3 == 0) 0x8E1E12 else 0xE95E3D, if (k % 3 == 0) 0.4 else 0.5)
        }
        for (k in 0 until 5) {
            val s = seed + 400 + k * 7.0
            pen.stroke(strand(0.36 + 0.08 * hash(s), 2 * PI * hash(s + 1), 0.7, 0.03, 3.0, 2.0, 6), 0.034, 0xEBC877)
        }
        for (k in 0 until 7) {
            val s = seed + 500 + k * 3.0
            pen.circle(-0.14 + 0.2 * hash(s), -0.2 + 0.2 * hash(s + 1), 0.008 + 0.01 * hash(s + 2), 0xFFFFFF, 0.5)
        }
        leaf(pen, 0.06, -0.14, 0.36, 0.18, -0.6, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.2, 0.08, 0.28, 0.15, 0.9, 0x357D30, 0x79BE5E)
        leaf(pen, -0.12, 0.1, 0.26, 0.13, 2.4, 0x418F39, 0x86C76A)
        specks(pen, seed + 600, 18, 0.85, 0.026, intArrayOf(0xFFF5D8, 0xFBEBC0), 0.95)
    }

    private fun penne(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xB83E24, 1.0, 0.0), Stop(0x8E2716, 1.0, 0.88), Stop(0x8E2716, 0.0, 1.0)))
        val count = 52
        for (k in 0 until count) {
            val s = seed + k * 5.0
            val rr = 0.8 * sqrt(hash(s + 1)); val a = 2 * PI * hash(s + 2)
            val len = 0.3 + 0.06 * hash(s + 4); val w = 0.13; val cut = 0.055
            val shade = 0.4 + 0.6 * k / count
            pen.push(rr * cos(a), rr * sin(a), 2 * PI * hash(s + 3))
            val body = doubleArrayOf(-len / 2 - cut, -w / 2, len / 2 - cut, -w / 2, len / 2 + cut, w / 2, -len / 2 + cut, w / 2)
            pen.poly(body.shift(0.016, 0.024), 0x4A1208, 0.5)
            pen.poly(body, mix(0x9A3A1A, 0xD8743A, shade))
            pen.stroke(doubleArrayOf(-len / 2 + 0.03, 0.0, len / 2 - 0.03, 0.0), w * 0.6, mix(0xBE5428, 0xF29A52, shade), 0.9)
            for (y in doubleArrayOf(-0.38, -0.18, 0.18, 0.38)) {
                pen.stroke(doubleArrayOf(-len / 2 + 0.012 + cut * y * 2, w * y, len / 2 - 0.012 + cut * y * 2, w * y), 0.006, 0x6E2410, 0.3)
            }
            pen.stroke(doubleArrayOf(-len / 2 + 0.04, -w * 0.13, len / 2 - 0.05, -w * 0.13), w * 0.18, 0xFFD7A0, 0.5)
            pen.ellipse(len / 2 - 0.012, 0.0, 0.022, w * 0.4, -atan(2 * cut / w), 0x4E1508, 0.85)
            pen.circle(-len * 0.18, w * 0.22, 0.03, 0xC23A20, 0.75)
            pen.circle(len * 0.12, -w * 0.25, 0.022, 0xB0301A, 0.6)
            pen.pop()
        }
        specks(pen, seed + 400, 6, 0.7, 0.04, intArrayOf(0xF3E6C4), 0.95)
        specks(pen, seed + 500, 34, 0.88, 0.018, intArrayOf(0x7E1A0C, 0xE0502E), 0.9)
        specks(pen, seed + 600, 26, 0.85, 0.02, intArrayOf(0x4C8B36, 0x6FAE4A), 0.95)
    }

    private val farfalleShape = doubleArrayOf(
        0.0, -0.035, 0.19, -0.11, 0.215, -0.075, 0.195, -0.04, 0.215, 0.0, 0.195, 0.04, 0.215, 0.075, 0.19, 0.11,
        0.0, 0.035, -0.19, 0.11, -0.215, 0.075, -0.195, 0.04, -0.215, 0.0, -0.195, -0.04, -0.215, -0.075, -0.19, -0.11,
    )

    private fun farfalle(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0x88B04A, 1.0, 0.0), Stop(0x6A9236, 1.0, 0.88), Stop(0x6A9236, 0.0, 1.0)))
        val count = 44
        for (k in 0 until count) {
            val s = seed + k * 5.0
            val rr = 0.82 * sqrt(hash(s + 1)); val a = 2 * PI * hash(s + 2)
            val shade = 0.45 + 0.55 * k / count
            pen.push(rr * cos(a), rr * sin(a), 2 * PI * hash(s + 3), 1.0 + 0.2 * hash(s + 4))
            pen.poly(farfalleShape.shift(0.012, 0.018), 0x2F4A16, 0.45)
            pen.poly(farfalleShape, mix(0xC2C26A, 0xF2E8A8, shade))
            pen.ellipse(0.12, -0.03, 0.05, 0.022, 0.4, 0x5F8F2E, 0.5)
            pen.ellipse(-0.11, 0.04, 0.045, 0.02, -0.3, 0x5F8F2E, 0.45)
            for (sx in doubleArrayOf(-1.0, 1.0)) {
                pen.stroke(doubleArrayOf(sx * 0.05, -0.03, sx * 0.17, -0.085), 0.007, 0x8E9A46, 0.5)
                pen.stroke(doubleArrayOf(sx * 0.05, 0.03, sx * 0.17, 0.085), 0.007, 0x8E9A46, 0.5)
            }
            pen.ellipse(0.0, 0.0, 0.032, 0.046, 0.0, 0x9DA552)
            pen.stroke(doubleArrayOf(-0.012, -0.03, -0.012, 0.03), 0.007, 0x7C8438, 0.7)
            pen.stroke(doubleArrayOf(0.012, -0.03, 0.012, 0.03), 0.007, 0x7C8438, 0.7)
            pen.pop()
        }
        for (k in 0 until 14) {
            val s = seed + 400 + k * 4.0
            val rr = 0.8 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1); val ang = 6.28 * hash(s + 2)
            pen.ellipse(rr * cos(a) + 0.008, rr * sin(a) + 0.01, 0.04, 0.022, ang, 0x3A4A18, 0.35)
            pen.ellipse(rr * cos(a), rr * sin(a), 0.04, 0.022, ang, 0xF2E3BA)
            pen.ellipse(rr * cos(a) - 0.008, rr * sin(a) - 0.006, 0.022, 0.01, ang, 0xFFF9E8, 0.9)
        }
        specks(pen, seed + 500, 9, 0.75, 0.06, intArrayOf(0xFBF4DF), 0.95)
        leaf(pen, -0.1, -0.12, 0.34, 0.17, 0.5, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.14, 0.02, 0.28, 0.14, 2.1, 0x357D30, 0x79BE5E)
    }

    private fun fettuccine(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xF7EED6, 1.0, 0.0), Stop(0xEAD8AC, 1.0, 0.88), Stop(0xEAD8AC, 0.0, 1.0)))
        val ribbons = (0 until 30).map { k ->
            val s = seed + k * 3.0
            strand(0.18 + 0.7 * hash(s + 1), 2 * PI * hash(s + 2), 1.4 + 1.6 * hash(s + 3), 0.05, 2.0, 6 * hash(s + 4))
        }
        for (p in ribbons) pen.stroke(p, 0.13, 0x9C7A3A, 0.6)
        ribbons.forEachIndexed { k, p ->
            pen.stroke(p, 0.105, mix(0xE3BC6E, 0xF3D695, hash(seed + k * 3.0 + 5)))
            pen.stroke(p.shift(-0.016, -0.016), 0.022, 0xFFF8E2, 0.65)
        }
        for (k in 0 until 5) {
            val s = seed + 300 + k * 4.0
            val rr = 0.7 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1)
            pen.ellipse(rr * cos(a), rr * sin(a), 0.1, 0.06, 6.28 * hash(s + 2), 0xFBF4E2, 0.55)
        }
        specks(pen, seed + 400, 70, 0.9, 0.01, intArrayOf(0x3A332B, 0x5A5046), 0.85)
        specks(pen, seed + 500, 20, 0.8, 0.022, intArrayOf(0x5A9A3E, 0x3F7F2E), 0.95)
    }

    private fun macAndCheese(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xF3B847, 1.0, 0.0), Stop(0xDD962D, 1.0, 0.88), Stop(0xDD962D, 0.0, 1.0)))
        val count = 90
        for (k in 0 until count) {
            val s = seed + k * 5.0
            val rr = 0.86 * sqrt(hash(s + 1)); val a = 2 * PI * hash(s + 2)
            val shade = 0.4 + 0.6 * k / count
            pen.push(rr * cos(a), rr * sin(a), 2 * PI * hash(s + 3))
            pen.push(0.01, 0.014, 0.0)
            pen.arc(0.0, 0.0, 0.075, -1.3, 1.3, 0.085, 0xA9661A, 0.5)
            pen.pop()
            pen.arc(0.0, 0.0, 0.075, -1.3, 1.3, 0.07, mix(0xEFAE3E, 0xFBD772, shade))
            pen.arc(0.0, 0.0, 0.066, -1.0, 0.9, 0.018, 0xFFF0B8, 0.7)
            pen.pop()
        }
        for (k in 0 until 7) {
            val s = seed + 300 + k * 4.0
            val rr = 0.65 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1)
            pen.radial(rr * cos(a), rr * sin(a), 0.12 + 0.08 * hash(s + 2), stops(Stop(0xA0521A, 0.5, 0.0), Stop(0xA0521A, 0.0, 1.0)))
        }
        specks(pen, seed + 400, 110, 0.9, 0.022, intArrayOf(0xC98633, 0xA9621F, 0xE9B455, 0x8E4E17), 0.9)
        for (k in 0 until 14) {
            val s = seed + 600 + k * 4.0
            val rr = 0.75 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1); val ang = 6.28 * hash(s + 2)
            val x = rr * cos(a); val y = rr * sin(a)
            pen.stroke(doubleArrayOf(x - 0.035 * cos(ang), y - 0.035 * sin(ang), x + 0.035 * cos(ang), y + 0.035 * sin(ang)), 0.015, 0x4F8E33)
        }
    }

    private fun ravioli(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xDDA752, 1.0, 0.0), Stop(0xB5752C, 0.92, 0.74), Stop(0xB5752C, 0.0, 1.0)))
        val spots = (0 until 5).map { k ->
            val a = k * 2 * PI / 5 + 0.18
            doubleArrayOf(0.53 * cos(a), 0.53 * sin(a), a + 0.3)
        } + listOf(doubleArrayOf(0.0, 0.0, 0.2))
        val side = 0.46
        spots.forEachIndexed { k, sp ->
            pen.push(sp[0], sp[1], sp[2])
            pen.roundRect(-side / 2 + 0.02, -side / 2 + 0.03, side, side, 0.07, 0x4A2A0E, 0.5)
            pen.roundRect(-side / 2, -side / 2, side, side, 0.07, 0xE6CC92)
            pen.radial(-0.02, -0.03, 0.2, stops(Stop(0xFFF4D6, 0.95, 0.0), Stop(0xF2DEAD, 0.7, 0.6), Stop(0xF0DCAA, 0.0, 1.0)))
            pen.stroke(doubleArrayOf(-0.13, 0.12, 0.0, 0.16, 0.13, 0.12), 0.012, 0xD3B87C, 0.5)
            val inset = side / 2 - 0.035
            for (j in 0 until 7) {
                val u = -inset + 2 * inset * j / 6
                pen.circle(u, -inset, 0.011, 0xC9AA6A, 0.8)
                pen.circle(u, inset, 0.011, 0xC9AA6A, 0.8)
                pen.circle(-inset, u, 0.011, 0xC9AA6A, 0.8)
                pen.circle(inset, u, 0.011, 0xC9AA6A, 0.8)
            }
            pen.ellipse(-0.06, -0.08, 0.06, 0.025, -0.5, 0xFFFFFF, 0.35 + 0.1 * (k % 2))
            pen.pop()
        }
        leaf(pen, 0.0, -0.3, 0.26, 0.12, 0.3, 0x56703A, 0x8BA45E)
        leaf(pen, 0.3, 0.22, 0.24, 0.11, 2.2, 0x4E6833, 0x8BA45E)
        leaf(pen, -0.34, 0.12, 0.25, 0.12, -1.9, 0x56703A, 0x8BA45E)
        leaf(pen, -0.1, 0.36, 0.22, 0.1, 1.2, 0x4E6833, 0x8BA45E)
        for (k in 0 until 16) {
            val s = seed + 500 + k * 4.0
            val rr = 0.85 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1); val ang = 6.28 * hash(s + 2)
            val x = rr * cos(a); val y = rr * sin(a)
            pen.stroke(doubleArrayOf(x, y, x + 0.03 * cos(ang), y + 0.03 * sin(ang), x + 0.05 * cos(ang + 0.9), y + 0.05 * sin(ang + 0.9)), 0.012, 0xF0CF3A)
        }
        specks(pen, seed + 600, 20, 0.9, 0.01, intArrayOf(0x3A2A1B), 0.8)
    }

    private fun lasagna(pen: Pen, seed: Double) {
        pen.blob(blobPoints(0.0, 0.0, 0.9, 0.06, 0.04, 0.7), 0xA42C19)
        pen.radial(-0.1, -0.12, 0.85, stops(Stop(0xC8432A, 1.0, 0.0), Stop(0xB43722, 0.6, 0.7), Stop(0xA42C19, 0.0, 1.0)))
        specks(pen, seed + 50, 16, 0.85, 0.03, intArrayOf(0x7E1E10, 0xD9573A), 0.7)
        pen.push(0.0, 0.0, -0.14)
        val side = 1.1; val h = side / 2
        pen.roundRect(-h + 0.07, -h + 0.09, side, side, 0.1, 0x5E1E0E, 0.5)
        pen.roundRect(-h + 0.06, -h + 0.075, side, side, 0.1, 0xEFD7A4)
        pen.roundRect(-h + 0.042, -h + 0.052, side, side, 0.1, 0x8E3218)
        pen.roundRect(-h + 0.022, -h + 0.028, side, side, 0.1, 0xF1DDB0)
        pen.roundRect(-h, -h, side, side, 0.1, 0xE3A443)
        for (k in 0 until 34) {
            val s = seed + 100 + k * 4.0
            val x = -h + 0.06 + (side - 0.12) * hash(s); val y = -h + 0.06 + (side - 0.12) * hash(s + 1)
            pen.radial(x, y, 0.05 + 0.08 * hash(s + 2), stops(Stop(0xB0612A, 0.6, 0.0), Stop(0xB0612A, 0.0, 1.0)))
        }
        for (k in 0 until 20) {
            val s = seed + 300 + k * 4.0
            val x = -h + 0.05 + (side - 0.1) * hash(s); val y = -h + 0.05 + (side - 0.1) * hash(s + 1)
            pen.circle(x, y, 0.016 + 0.03 * hash(s + 2), 0x84401A, 0.5)
        }
        for (k in 0 until 30) {
            val s = seed + 500 + k * 4.0
            val x = -h + 0.05 + (side - 0.1) * hash(s); val y = -h + 0.05 + (side - 0.1) * hash(s + 1)
            val r = 0.014 + 0.026 * hash(s + 2)
            pen.circle(x, y, r, 0xF7D27C, 0.85)
            pen.circle(x - r * 0.3, y - r * 0.3, r * 0.35, 0xFFFFFF, 0.6)
        }
        for (k in 0 until 5) {
            val s = seed + 700 + k * 4.0
            val edge = (hash(s) * 4).toInt()
            val u = -h + 0.12 + (side - 0.24) * hash(s + 1)
            val pos = listOf(doubleArrayOf(u, -h + 0.04), doubleArrayOf(h - 0.04, u), doubleArrayOf(u, h - 0.04), doubleArrayOf(-h + 0.04, u))[edge]
            pen.circle(pos[0], pos[1], 0.05 + 0.03 * hash(s + 2), 0xB83A20, 0.85)
        }
        leaf(pen, 0.16, -0.12, 0.3, 0.15, -0.8, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.28, 0.02, 0.24, 0.12, 0.4, 0x357D30, 0x79BE5E)
        pen.pop()
    }

    private fun shells(pen: Pen, seed: Double) {
        pen.radial(0.0, 0.0, 1.0, stops(Stop(0xF5E8C8, 1.0, 0.0), Stop(0xE6CF9C, 1.0, 0.88), Stop(0xE6CF9C, 0.0, 1.0)))
        fun peas(from: Int, to: Int) {
            for (k in from until to) {
                val s = seed + 400 + k * 4.0
                val rr = 0.82 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1)
                val x = rr * cos(a); val y = rr * sin(a)
                pen.circle(x + 0.01, y + 0.014, 0.05, 0x2E4A12, 0.4)
                pen.circle(x, y, 0.05, 0x5E9C2A)
                pen.radial(x - 0.015, y - 0.015, 0.04, stops(Stop(0xC2E88A, 0.95, 0.0), Stop(0x8CC44E, 0.5, 0.5), Stop(0x8CC44E, 0.0, 1.0)))
            }
        }
        peas(0, 10)
        val count = 52
        for (k in 0 until count) {
            val s = seed + k * 5.0
            val rr = 0.82 * sqrt(hash(s + 1)); val a = 2 * PI * hash(s + 2)
            val shade = 0.45 + 0.55 * k / count
            pen.push(rr * cos(a), rr * sin(a), 2 * PI * hash(s + 3), 1.0 + 0.25 * hash(s + 4))
            pen.ellipse(0.012, 0.018, 0.12, 0.095, 0.0, 0x8F6E35, 0.45)
            pen.ellipse(0.0, 0.0, 0.12, 0.095, 0.0, mix(0xE2B565, 0xF7DA95, shade))
            for (j in 0 until 7) {
                val ang = -1.15 + 2.3 * j / 6
                pen.stroke(doubleArrayOf(-0.09, 0.0, -0.09 + 0.2 * cos(ang), 0.088 * sin(ang) * 1.05), 0.007, 0xC99A4C, 0.55)
            }
            pen.ellipse(0.025, 0.0, 0.055, 0.04, 0.0, 0xC58F45, 0.7)
            pen.ellipse(0.03, 0.0, 0.035, 0.022, 0.0, 0xFBF0D6)
            pen.circle(-0.1, 0.0, 0.02, 0xD9AB5C)
            pen.pop()
        }
        peas(10, 28)
        for (k in 0 until 10) {
            val s = seed + 600 + k * 4.0
            val rr = 0.78 * sqrt(hash(s)); val a = 2 * PI * hash(s + 1)
            pen.push(rr * cos(a), rr * sin(a), 6.28 * hash(s + 2))
            pen.roundRect(-0.04, -0.035, 0.08, 0.07, 0.015, 0xE4978A)
            pen.roundRect(-0.04, -0.012, 0.08, 0.018, 0.008, 0xF6C9BE, 0.9)
            pen.pop()
        }
        specks(pen, seed + 700, 30, 0.9, 0.01, intArrayOf(0x3A332B), 0.8)
    }

    // Bitmaps

    private fun render(side: Double, draw: (Pen) -> Unit): Bitmap {
        val px = (side * SCALE).roundToInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = NativeCanvas(bitmap)
        canvas.scale(SCALE, SCALE)
        draw(Pen(canvas))
        return bitmap
    }

    fun plateImage(bowl: Boolean): Bitmap = render(PLATE_SIZE) { pen ->
        val c = PLATE_SIZE / 2; val r = c - 1
        if (bowl) {
            pen.radial(c, c, r, stops(Stop(0x1D1B19, 1.0, 0.0), Stop(0x1D1B19, 1.0, 0.77), Stop(0x37332E, 1.0, 0.85), Stop(0x24211E, 1.0, 0.94), Stop(0x0E0D0C, 1.0, 1.0)))
            pen.radial(c, c, r * 0.78, stops(Stop(0x2A2723, 1.0, 0.0), Stop(0x1B1917, 1.0, 0.9), Stop(0x121110, 1.0, 1.0)))
            pen.arc(c, c, r * 0.88, PI * 1.08, PI * 1.55, r * 0.035, 0xFFFFFF, 0.14)
            pen.arc(c, c, r * 0.795, PI * 0.1, PI * 0.6, 1.5, 0xFFFFFF, 0.1)
            pen.ring(c, c, r - 0.5, 1.0, 0x000000, 0.55)
        } else {
            pen.radial(c, c, r, stops(Stop(0xF6F4EF, 1.0, 0.0), Stop(0xF6F4EF, 1.0, 0.75), Stop(0xFFFFFF, 1.0, 0.82), Stop(0xF1EEE8, 1.0, 0.94), Stop(0xD9D5CC, 1.0, 1.0)))
            pen.radial(c, c, r * 0.8, stops(Stop(0x000000, 0.0, 0.86), Stop(0x6B5E48, 0.1, 0.95), Stop(0x6B5E48, 0.0, 1.0)))
            pen.arc(c, c, r * 0.885, PI * 1.12, PI * 1.5, r * 0.03, 0xFFFFFF, 0.7)
            pen.arc(c, c, r * 0.89, PI * 0.15, PI * 0.55, r * 0.05, 0xC9C3B8, 0.18)
            pen.ring(c, c, r - 0.5, 1.0, 0xC7C1B6, 0.8)
        }
    }

    fun foodImage(i: Int): Bitmap {
        val fr = PLATE_SIZE / 2 * foodFraction(dishes[i].bowl)
        val side = fr * 2 * 1.08
        return render(side) { pen ->
            pen.c.translate((side / 2).toFloat(), (side / 2).toFloat())
            pen.c.scale(fr.toFloat(), fr.toFloat())
            drawFood(pen, i)
        }
    }

    fun lightImage(bowl: Boolean): Bitmap {
        val fr = PLATE_SIZE / 2 * foodFraction(bowl)
        val side = fr * 2 * 1.08
        return render(side) { pen ->
            val c = side / 2
            pen.radial(c, c, fr * 0.98, stops(Stop(0x000000, 0.0, 0.0), Stop(0x000000, 0.0, 0.55), Stop(0x2A1608, 0.22, 0.9), Stop(0x2A1608, 0.0, 1.0)))
            pen.radial(c - fr * 0.32, c - fr * 0.38, fr * 0.8, stops(Stop(0xFFFFFF, 0.2, 0.0), Stop(0xFFFFFF, 0.06, 0.5), Stop(0xFFFFFF, 0.0, 1.0)))
        }
    }

    /** 128 × 128 tile of static grain (a hash per pixel), drawn at 3.5 % over the background. */
    fun grainImage(): Bitmap {
        val n = 128
        val pixels = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val v = hash(x * 1.7 + y * 113.3 + 0.5)
            val g = if (v > 0.5) 255 else 0
            val a = (kotlin.math.abs(v - 0.5) * 2 * 255).toInt()
            pixels[y * n + x] = (a shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
    }
}
