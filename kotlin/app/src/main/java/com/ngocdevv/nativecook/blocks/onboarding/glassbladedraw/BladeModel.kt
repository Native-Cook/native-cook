package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import kotlin.math.*

internal object BladeModel {
    val values = intArrayOf(60, 47, 49, 71, 38, 55, 62, 44, 53)
    val hues = arrayOf("lime", "ember", "teal")
    val colours = arrayOf(rgb(0xD8F21A), rgb(0xE8480C), rgb(0x0FE0D2))
    private const val RAD = PI / 180
    fun cardIndex(pos: Double) = Math.floorMod(floor(pos + .5).toInt(), 9)
    private fun rgb(value: Int) = doubleArrayOf((value shr 16) / 255.0, ((value shr 8) and 255) / 255.0, (value and 255) / 255.0)
    private fun smooth(low: Double, high: Double, value: Double): Double {
        val t = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Same flat uniform ABI as the reference, with a stable nearest-first ordering. */
    fun uniforms(l: BladeLayout, f: BladeFrame, seconds: Double, pixelSize: Double): Map<String, FloatArray> {
        val result = linkedMapOf<String, FloatArray>()
        fun put(name: String, vararg values: Double) { result[name] = FloatArray(values.size) { values[it].toFloat() } }
        val centre = ArrayList<Double>()
        val axisX = ArrayList<Double>()
        val axisY = ArrayList<Double>()
        val normal = ArrayList<Double>()
        val bounds = ArrayList<Double>()
        val cards = ArrayList<Double>()
        val colours = ArrayList<Double>()
        val pivot = floor(f.pos + .5).toInt()
        val indices = (pivot - 5..pivot + 5).sortedBy { abs(f.pos - it) }
        val focal = 420 * l.scale
        val eyeX = l.width / 2
        val eyeY = l.ctaY - l.cardH * .44
        var fanTop = l.height
        val heroColour = DoubleArray(3)
        var heroPower = 0.0
        for (index in indices) {
            val u = f.pos - index
            val a = abs(u)
            val near = 1 - smooth(.18, .85, a)
            val raised = 1 - smooth(0.0, 1.08, a)
            val opening = f.intro.coerceIn(0.0, 1.0)
            val magnitude = when {
                a > 3 -> 42 - 10 * smooth(3.0, 4.0, a)
                a > 2 -> 55 - 13 * smooth(2.0, 3.0, a)
                a > 1 -> 77 - 22 * smooth(1.0, 2.0, a)
                else -> 77.0
            }
            val turn = smooth(0.0, .48, a)
            val side = if (u < 0) 1 else -1
            val idle = (1 - f.still.coerceIn(0.0, 1.0)) * opening
            val breath = sin(seconds * PI * 2 / 5 + u * 1.13)
            val phi = u * 9.5 * RAD * opening
            val poseYaw = ((-42 * (1 - turn) + side * magnitude * turn) * opening + 1.2 * breath * idle) * RAD
            val lift = raised * opening
            val bob = 1.5 * raised * breath * idle
            val depth = (26 * raised + (24 - 52 * a) * (1 - raised)) * opening
            val light = (near * f.charge).coerceIn(0.0, 1.25) * opening
            val opacity = 1 - smooth(4.0, 5.0, a)
            val cardIndex = Math.floorMod(index, 9)
            val colour = this.colours[cardIndex % 3]
            val radius = l.sideBottomR + (l.heroBottomR - l.sideBottomR) * lift + l.cardH / 2 + bob * l.scale
            val cx = l.pivotX + sin(phi) * radius
            val cy = l.pivotY - cos(phi) * radius
            val cz = depth * l.scale
            val yaw = poseYaw - atan((cx - eyeX) / (focal - cz)) * (1 - lift)
            val lean = phi + 3 * RAD * lift
            val ax = doubleArrayOf(cos(lean) * cos(yaw), sin(lean) * cos(yaw), -sin(yaw))
            val ay = doubleArrayOf(-sin(lean), cos(lean), 0.0)
            val az = doubleArrayOf(cos(lean) * sin(yaw), sin(lean) * sin(yaw), cos(yaw))
            centre.addAll(listOf(cx, cy, cz))
            axisX.addAll(ax.toList())
            axisY.addAll(ay.toList())
            normal.addAll(az.toList())
            var minX = l.width * 10
            var minY = l.height * 10
            var maxX = -minX
            var maxY = -minY
            for (corner in 0 until 8) {
                val x = (if (corner and 1 != 0) .5 else -.5) * l.cardW
                val y = (if (corner and 2 != 0) .5 else -.5) * l.cardH
                val z = (if (corner and 4 != 0) .5 else -.5) * l.cardT
                val persp = focal / (focal - cz - ax[2] * x - az[2] * z)
                val px = eyeX + (cx + ax[0] * x + ay[0] * y + az[0] * z - eyeX) * persp
                val py = eyeY + (cy + ax[1] * x + ay[1] * y + az[1] * z - eyeY) * persp
                minX = min(minX, px)
                minY = min(minY, py)
                maxX = max(maxX, px)
                maxY = max(maxY, py)
            }
            val margin = (if (light > .01) 42 else 8) * l.scale
            bounds.addAll(listOf(minX - margin, minY - margin, maxX + margin, maxY + margin))
            fanTop = min(fanTop, minY - 8)
            cards.addAll(listOf(values[cardIndex].toDouble(), light, opacity, u))
            colours.addAll(colour.toList())
            val energy = light * light
            for (c in 0..2) heroColour[c] += colour[c] * energy
            heroPower += energy
        }
        for (c in 0..2) heroColour[c] /= max(heroPower, .001)
        put("uResolution", l.width, l.height)
        put("uEye", eyeX, eyeY, focal)
        put("uSize", l.cardW / 2, l.cardH / 2, l.cardT / 2, l.cornerR)
        for ((name, data) in listOf("uCentre" to centre, "uAxisX" to axisX, "uAxisY" to axisY,
            "uNormal" to normal, "uBounds" to bounds, "uCard" to cards, "uColour" to colours)) {
            result[name] = FloatArray(data.size) { data[it].toFloat() }
        }
        put("uHero", *heroColour, min(heroPower, 1.5))
        put("uBar", l.ctaX + l.ctaW / 2, l.ctaY + l.ctaH / 2, l.ctaW / 2, l.ctaH / 2)
        put("uBarRadius", l.ctaR)
        put("uLime", *rgb(0xDCFF1F))
        put("uMotion", f.flare, f.press, f.ctaEnergy, f.intro)
        put("uPixel", pixelSize)
        put("uScale", l.scale)
        put("uFanTop", fanTop)
        return result
    }
}
