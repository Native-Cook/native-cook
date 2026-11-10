package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** CPU equivalent of smokeCs; 512 cached soft sprites preserve the 3,072 puff field's opacity. */
internal class SmokedClocheRevealSmoke {
    private val count = 512
    private val puffs = Array(count) { Puff() }
    private var layout: SmokedClocheRevealLayout? = null
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sprite = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
        val pixels = IntArray(96 * 96)
        for (y in 0 until 96) for (x in 0 until 96) {
            val u = (x + 0.5) / 48 - 1
            val v = (y + 0.5) / 48 - 1
            val r2 = u * u + v * v
            val a = exp(-r2 * 3.2) * (1 - smooth(0.8, 1.0, r2)) * 0.05 * 3072 / count
            pixels[y * 96 + x] = Color.argb((a * 255).roundToInt().coerceIn(0, 255), 249, 247, 243)
        }
        setPixels(pixels, 0, 96, 0, 0, 96, 96)
    }

    private class Puff {
        var x = 0.0; var y = 0.0; var vx = 0.0; var vy = 0.0
        var alpha = 0.0; var size = 0.0; var seed = 0.0; var age = 0.0
    }

    fun step(l: SmokedClocheRevealLayout, f: SmokedClocheRevealMotion.Frame) {
        val respawn = layout != l || f.mode == 3
        layout = l
        val dt = min(f.dt, 1.0 / 30)
        val r = l.radius.toDouble()
        puffs.forEachIndexed { i, p ->
            if (respawn || p.seed == 0.0) {
                // Sample evenly across the reference's original ID range.
                val id = i.toDouble() * 3072 / count
                val a = hash(id + 1) * PI * 2
                val radius = sqrt(hash(id + 2)) * r * 0.88
                p.x = l.cx + cos(a) * radius; p.y = l.cy + sin(a) * radius
                p.vx = 0.0; p.vy = 0.0; p.alpha = 0.0
                p.size = 14 + 16 * hash(id + 3); p.seed = 0.001 + hash(id + 4); p.age = 0.0
            }
            p.age += dt
            val rx = p.x - l.cx
            val ry = p.y - l.cy
            val t = f.time * 0.25 + p.seed * 3
            val px = p.x * 0.012
            val py = p.y * 0.012
            val qx = px + t * 0.6
            val qy = py - t * 0.4
            val mx = px * 2.1 - t * 0.9 + 17
            val my = py * 2.1 + t * 0.7 + 17
            val flowX = ((noise(qx, qy + 0.5) - noise(qx, qy - 0.5)) + 0.5 * (noise(mx, my + 0.5) - noise(mx, my - 0.5))) * 26 - ry / max(r, 1.0) * 14
            val flowY = (-(noise(qx + 0.5, qy) - noise(qx - 0.5, qy)) - 0.5 * (noise(mx + 0.5, my) - noise(mx - 0.5, my))) * 26 + rx / max(r, 1.0) * 14
            p.vx += (flowX - p.vx) * min(1.0, dt * 2.5)
            p.vy += (flowY - p.vy) * min(1.0, dt * 2.5)
            val rl = hypot(rx, ry)
            if (f.mode != 1) {
                if (rl > r * 0.9) {
                    val force = (rl - r * 0.9) * 12 * dt * 10 / max(rl, 1.0)
                    p.vx -= rx * force; p.vy -= ry * force
                }
                p.alpha = min(1.0, p.alpha + dt / 0.9)
            } else {
                val force = (40 + 200 * f.lift) * smooth(0.45, 1.0, max(rl, 1.0) / r) * dt * 3 / max(rl, 1.0)
                p.vx += rx * force; p.vy += ry * force
                p.size += dt * (6 + 12 * f.lift)
                p.alpha = max(0.0, p.alpha - dt * ((0.14 + 0.22 * f.lift) * (0.6 + 0.8 * p.seed) + 2.2 * smooth(r, 1.35 * r, max(rl, 1.0))))
            }
            if (f.fingerDown) {
                val dx = p.x - f.fx
                val dy = p.y - f.fy
                val distance = hypot(dx, dy)
                if (distance < 60) {
                    val k = 1 - distance / 60
                    val drag = 0.6 * k * k * dt * 8 / max(dt, 0.004)
                    p.vx += f.dx * drag - dy / max(distance, 1.0) * 60 * k
                    p.vy += f.dy * drag + dx / max(distance, 1.0) * 60 * k
                }
            }
            p.x += p.vx * dt; p.y += p.vy * dt
        }
    }

    fun draw(c: Canvas) {
        for (p in puffs) {
            if (p.alpha <= 0.002) continue
            rect.set((p.x - p.size).toFloat(), (p.y - p.size).toFloat(), (p.x + p.size).toFloat(), (p.y + p.size).toFloat())
            paint.alpha = (p.alpha * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(sprite, null, rect, paint)
        }
    }

    fun close() { sprite.recycle() }
    fun reset() { layout = null }

    private companion object {
        fun fract(n: Double) = n - floor(n)
        fun hash(n: Double) = fract(sin(n * 91.3458) * 47453.5453)
        fun hash2(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)
        fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
        fun noise(x: Double, y: Double): Double {
            val ix = floor(x); val iy = floor(y)
            val fx = fract(x); val fy = fract(y)
            val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy)
            val a = hash2(ix, iy) * (1 - u) + hash2(ix + 1, iy) * u
            val b = hash2(ix, iy + 1) * (1 - u) + hash2(ix + 1, iy + 1) * u
            return a * (1 - v) + b * v
        }
    }
}
