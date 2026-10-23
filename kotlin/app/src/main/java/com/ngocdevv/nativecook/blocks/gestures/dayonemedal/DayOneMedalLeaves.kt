package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Source seeded 900-slot leaf system; the accepted low tier draws 360 moving leaves. */
internal class DayOneMedalLeaves {
    private data class Leaf(
        var x: Float, var y: Float, var z: Float, val seed: Float,
        val width: Float, val height: Float, val phase: Float, val spinRate: Float,
        val fall: Float, val drift: Float, val driftRate: Float, val tumbleRate: Float,
        var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
        var air: Float = 0f, var travelX: Float = 0f, var travelY: Float = 0f,
        var capture: Float = 0f,
    )
    private var seed = 20261013
    private fun next(): Float {
        seed = seed * 1664525 + 1013904223
        return (seed.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
    private val leaves = ArrayList<Leaf>(900)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private val path = Path()
    private var time = 0f
    private var impulse = 0f

    init {
        repeat(900) { index ->
            val depth = next()
            val z = if (index < 4) 1.45f + depth * 0.2f else (depth - 0.5f) * 1.7f
            val perspective = (6.9f - z) / 6.9f
            val phase = next() * (2f * PI.toFloat())
            val population = next()
            val size = if (population < 0.1f) 8f + next() * 3f else if (population < 0.16f) 3f + next() * 2f else 4f + next() * 4f
            val x = (next() - 0.5f) * 430f * 1.15f / 177.5f * perspective
            val y = (if (index < 900 * 0.55f) -1.1f + next() * 2.2f else 273f / 177.5f + 0.2f + next() * 878f / 177.5f * 0.9f) * perspective
            val sequence = next()
            val width = size / 177.5f
            val height = size * (0.65f + next() * 0.3f) / 177.5f
            val spinRate = (if (next() < 0.5f) -1f else 1f) * (1.2f + 3.4f * next())
            val fall = (62f + 88f * next()) * (0.7f + 0.5f * depth) / 177.5f
            val drift = (6f + 14f * next()) / 177.5f
            leaves += Leaf(x, y, z, sequence, width, height, phase, spinRate,
                fall, drift, 0.6f + 1.3f * next(), 2.2f + 4.6f * next())
        }
    }

    fun reset() {
        time = 0f
        impulse = 0f
        for (leaf in leaves) {
            leaf.vx = 0f
            leaf.vy = 0f
            leaf.vz = 0f
            leaf.air = 0f
            leaf.capture = 0f
        }
    }

    fun strike() { impulse = 9f }

    fun step(dt: Float, centreX: Float, centreY: Float, spin: Float, yaw: Float) {
        time += dt
        val powered = smooth(0f, 6f, abs(spin))
        val angle = yaw
        val cy = cos(angle)
        val sy = sin(angle)
        for (i in 0 until 360) {
            val leaf = leaves[i]
            val oldX = leaf.x
            val oldY = leaf.y
            val oldZ = leaf.z
            val radius = max(0.001f, hypot(leaf.x, leaf.z))
            val proximity = exp(-radius * radius / 5f - leaf.y * leaf.y / 5f)
            leaf.air = (leaf.air * exp(-dt / 1.2f) + spin * proximity * dt * 6.5f).coerceIn(-24f, 24f)
            val capture = smooth(1.2f, 8f, abs(leaf.air))
            val orbitRadius = 1.03f + 0.30f * leaf.seed
            val radialStep = ((orbitRadius - radius) * 4f * powered + 0.55f * (1f - powered)) * capture * dt
            leaf.x += leaf.x / radius * radialStep
            leaf.z += leaf.z / radius * radialStep
            val turn = leaf.air * dt / (0.65f + radius * 0.4f)
            val ox = leaf.x
            val oz = leaf.z
            leaf.x = ox * cos(turn) + oz * sin(turn)
            leaf.z = oz * cos(turn) - ox * sin(turn)
            leaf.y += (0.27f * (leaf.x - oldX) + 0.42f * (leaf.z - oldZ)) * capture
            val lane = kotlin.math.floor(leaf.seed * 3f) - 1f
            val laneY = lane * 0.72f + 0.27f * leaf.x + 0.42f * leaf.z + 0.17f * sin(leaf.phase)
            leaf.y += (laneY - leaf.y) * (1f - exp(-dt * 4.5f * capture * powered))
            val drift = leaf.drift * leaf.driftRate * cos(time * leaf.driftRate + leaf.phase)
            val decay = exp(-dt / 0.48f)
            leaf.vx *= decay
            leaf.vy *= decay
            leaf.vz *= decay
            val fromCentre = sqrt(leaf.x * leaf.x + leaf.y * leaf.y + leaf.z * leaf.z + 0.00001f)
            val burst = impulse * exp(-(leaf.x * leaf.x + leaf.y * leaf.y + leaf.z * leaf.z) / 4f)
            leaf.vx += leaf.x / fromCentre * burst
            leaf.vy += leaf.y / fromCentre * burst
            leaf.vz += leaf.z / fromCentre * burst
            val rain = 1f - capture * 0.94f * powered
            leaf.x += (drift * rain + leaf.vx) * dt
            leaf.y += (-leaf.fall * rain + leaf.vy) * dt
            leaf.z += leaf.vz * dt
            // Sweep a leaf around the physical slab instead of allowing front/back popping.
            val localX = cy * leaf.x - sy * leaf.z
            val localZ = sy * leaf.x + cy * leaf.z
            val clearance = max(leaf.width, leaf.height) * 0.65f + 0.015f
            val capY = leaf.y.coerceIn(-(1f - 0.5352f), 1f - 0.5352f)
            val localRadius = hypot(localX, leaf.y - capY)
            if (abs(localZ) < 0.08f + clearance && localRadius < 0.5352f + clearance) {
                val side = if (localX < 0f) -1f else 1f
                val newX = side * (0.5352f + clearance)
                leaf.x = cy * newX + sy * localZ
                leaf.z = -sy * newX + cy * localZ
            }
            leaf.z = leaf.z.coerceIn(-2.4f, 2.4f)
            leaf.travelX = (leaf.x - oldX) / max(dt, 0.0001f)
            leaf.travelY = (leaf.y - oldY) / max(dt, 0.0001f)
            leaf.capture = capture
            val perspective = (6.9f - leaf.z) / 6.9f
            val bottom = -(878f - centreY) / 177.5f * perspective - 0.22f
            val top = centreY / 177.5f * perspective + 0.22f
            if (leaf.y < bottom) leaf.y += top - bottom
            val span = 430f / 177.5f * perspective * 1.15f
            val offset = (215f - centreX) / 177.5f * perspective
            if (leaf.x > offset + span * 0.5f) leaf.x -= span
            if (leaf.x < offset - span * 0.5f) leaf.x += span
        }
        impulse = 0f
    }

    fun draw(canvas: Canvas, centreX: Float, centreY: Float, near: Boolean) {
        for (i in 0 until 360) {
            val leaf = leaves[i]
            if ((leaf.z > 0f) != near) continue
            val perspective = (6.9f - leaf.z) / 6.9f
            val x = centreX + leaf.x * 177.5f / perspective
            val y = centreY - leaf.y * 177.5f / perspective
            if (x < -30f || x > 460f || y < -30f || y > 910f) continue
            val rotation = leaf.phase + leaf.spinRate * time
            val tumble = leaf.seed * 2f * PI.toFloat() + leaf.tumbleRate * time
            val twist = leaf.phase * 1.7f + time * 0.43f
            val tilt = max(0.16f, abs(cos(tumble) * cos(twist)))
            val w = leaf.width * 177.5f / perspective
            val h = leaf.height * 177.5f / perspective * tilt
            val brightness = (0.45f + 0.5f * abs(cos(tumble))) * (if (near) 1f else 0.65f)
            val red = min(255, (225f * brightness).toInt())
            val green = min(255, (176f * brightness).toInt())
            val blue = min(255, (94f * brightness).toInt())
            val alpha = ((if (i < 4) 0.23f else 0.86f) * 255f).toInt()
            paint.color = Color.argb(alpha, red, green, blue)
            canvas.save()
            canvas.rotate(rotation * 180f / PI.toFloat(), x, y)
            path.reset()
            path.moveTo(x - w * 0.5f, y - h * 0.5f)
            path.lineTo(x + w * 0.5f, y - h * 0.42f)
            path.lineTo(x + w * 0.46f, y + h * 0.5f)
            path.lineTo(x - w * 0.55f, y + h * 0.42f)
            path.close()
            if (i < 4 || leaf.capture > 0.4f) {
                glow.color = Color.argb(if (i < 4) 28 else 24, 255, 189, 90)
                canvas.drawPath(path, glow)
            }
            canvas.drawPath(path, paint)
            if (leaf.capture > 0.3f) {
                paint.color = Color.argb((leaf.capture * 42f).toInt(), 255, 190, 98)
                canvas.drawLine(x, y, x - leaf.travelX * 1.6f, y + leaf.travelY * 1.6f, paint)
            }
            canvas.restore()
        }
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
