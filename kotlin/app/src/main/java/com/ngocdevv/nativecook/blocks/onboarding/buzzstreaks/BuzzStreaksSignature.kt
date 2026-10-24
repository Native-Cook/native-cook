package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Cached contract and drawing materials; every cue is a pure function of the existing clock. */
internal class BuzzStreaksSignature(assets: AssetManager) {
    private val contract = JSONObject(assets.open("buzzstreaks/buzzstreaks_signature.json")
        .bufferedReader().use { it.readText() })
    private val contactEase = numbers(contract.getJSONObject("easing").getJSONArray("contact"))
    private val travelEase = numbers(contract.getJSONObject("easing").getJSONArray("travel"))
    private val contacts = characterWindows("contact")
    private val reflections = characterWindows("reflection")
    private val gazes = characterWindows("gaze")
    private val relays = flights(contract.getJSONArray("relay"))
    private val portals = flights(contract.getJSONArray("portal"))
    private val packet = contract.getJSONObject("packet")
    private val packetHeadRadius = packet.getDouble("headRadius").toFloat()
    private val packetCoreRadius = packet.getDouble("coreRadius").toFloat()
    private val trailLength = packet.getDouble("trailLength").toFloat()
    private val trailHalfWidth = packet.getDouble("trailWidth").toFloat() / 2f
    private val packetAlpha = packet.getDouble("alpha")
    private val packetEdgeFade = packet.getDouble("edgeFade")
    private val reflectionRadius = contract.getDouble("reflectionRadius").toFloat()
    private val lip = contract.getJSONObject("lip")
    private val lipHalfLength = lip.getDouble("length").toFloat() / 2f
    private val lipWidth = lip.getDouble("width").toFloat()
    private val lipLineHalfLength = lipHalfLength - lipWidth / 2f
    private val lipAlpha = lip.getDouble("alpha")
    private val lipDuration = lip.getDouble("duration")
    private val lock = contract.getJSONObject("rewardLock")
    private val lockStart = lock.getDouble("start")
    private val lockEnd = lock.getDouble("end")
    private val lockIDs = lock.getJSONArray("ids").let { ids -> Array(ids.length()) { ids.getString(it) } }
    private val lockX = lock.getDouble("x")
    private val lockY = lock.getDouble("y")
    private val lockReflection = lock.getDouble("reflectionAlpha")
    private val badgeReveal = numbers(contract.getJSONArray("badgeReveal"))
    private val domino = contract.getJSONObject("domino")
    private val dominoStart = domino.getDouble("start")
    private val dominoStagger = domino.getDouble("stagger")
    private val dominoDuration = domino.getDouble("duration")
    val dominoTranslation = domino.getDouble("translation").toFloat()
    val dominoRotation = domino.getDouble("rotation").toFloat()
    val dominoScale = domino.getDouble("scale").toFloat()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-trailLength, 0f, 0f, 0f, Color.TRANSPARENT,
            Color.rgb(224, 245, 64), Shader.TileMode.CLAMP)
    }
    private val packetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 255, 92) }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pinkReflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, reflectionRadius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    private val orangeReflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(75f, 325f, reflectionRadius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-lipHalfLength, 0f, lipHalfLength, 0f,
            intArrayOf(Color.TRANSPARENT, Color.rgb(242, 255, 158), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        strokeWidth = lipWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val coinClip = Path().apply { addCircle(0f, 0f, 1f, Path.Direction.CW) }
    private val point = FloatArray(4)

    fun contactInset(kind: String, time: Double): Float {
        val values = contacts.getValue(kind)
        if (time <= values[0] || time >= values[2]) return 0f
        val amount = if (time <= values[1]) {
            ease(contactEase, (time - values[0]) / (values[1] - values[0]))
        } else {
            1.0 - ease(contactEase, (time - values[1]) / (values[2] - values[1]))
        }
        return (values[3] * amount).toFloat()
    }

    fun reflectionAlpha(kind: String, time: Double): Float {
        val values = reflections.getValue(kind)
        return (values[2] * bell(time, values[0], values[1])).toFloat()
    }

    fun gazeOffset(kind: String, time: Double, axis: Int): Float {
        val values = gazes.getValue(kind)
        return (values[2 + axis] * bell(time, values[0], values[1])).toFloat()
    }

    fun drawReflection(kind: String, time: Double, opacity: Float, canvas: Canvas) {
        val amount = reflectionAlpha(kind, time) * opacity
        if (amount <= 0f) return
        val paint = if (kind == "pink") pinkReflectionPaint else orangeReflectionPaint
        paint.alpha = alpha(amount.toDouble())
        canvas.drawCircle(if (kind == "pink") 0f else 75f, if (kind == "pink") 0f else 325f,
            reflectionRadius, paint)
    }

    fun drawRelay(time: Double, canvas: Canvas) {
        for (flight in relays) {
            if (time <= flight.start || time >= flight.end) continue
            cubic(flight, ease(travelEase, (time - flight.start) / (flight.end - flight.start)))
            val opacity = packetAlpha * min(1.0,
                min((time - flight.start) / packetEdgeFade, (flight.end - time) / packetEdgeFade))
            val saved = canvas.save()
            canvas.translate(point[0], point[1])
            canvas.rotate(Math.toDegrees(atan2(point[3].toDouble(), point[2].toDouble())).toFloat())
            trailPaint.alpha = alpha(opacity)
            canvas.drawRoundRect(-trailLength, -trailHalfWidth, 0f, trailHalfWidth,
                trailHalfWidth, trailHalfWidth, trailPaint)
            packetPaint.alpha = alpha(opacity)
            canvas.drawCircle(0f, 0f, packetHeadRadius, packetPaint)
            whitePaint.alpha = alpha(opacity)
            canvas.drawCircle(0f, 0f, packetCoreRadius, whitePaint)
            canvas.restoreToCount(saved)
        }
    }

    fun adjustCoin(id: String, time: Double, value: FloatArray) {
        val lockIndex = lockIDs.indexOf(id)
        if (lockIndex >= 0 && time > lockStart && time < lockEnd) {
            val p = (time - lockStart) / (lockEnd - lockStart)
            val wave = sin(2.0 * PI * p) * (1.0 - p) * (1.0 - p)
            value[0] += ((if (lockIndex == 0) lockX else -lockX) * wave).toFloat()
            value[1] += (lockY * wave).toFloat()
        }
        for (flight in portals) {
            if (flight.id != id || time < flight.start || time > flight.end) continue
            cubic(flight, ease(travelEase, (time - flight.start) / (flight.end - flight.start)))
            value[0] = point[0]
            value[1] = point[1]
        }
    }

    fun drawCoinGloss(id: String, time: Double, x: Float, y: Float, radius: Float, opacity: Float, canvas: Canvas) {
        if (id !in lockIDs) return
        val amount = lockReflection * bell(time, lockStart, lockEnd) * opacity
        if (amount <= 0.0) return
        val saved = canvas.save()
        canvas.clipRect(x - radius, y - radius, x + radius, y + radius)
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        canvas.clipPath(coinClip)
        whitePaint.alpha = alpha(amount)
        canvas.drawOval(-.65f, -.7f, .65f, -.25f, whitePaint)
        canvas.restoreToCount(saved)
    }

    fun badgeGate(id: String, time: Double): Float {
        if (id != "upToDay15") return 1f
        return ease(contactEase, (time - badgeReveal[0]) / (badgeReveal[1] - badgeReveal[0])).toFloat()
    }

    fun drawCardLips(time: Double, canvas: Canvas) {
        for (flight in portals) {
            val amount = lipAlpha * bell(time, flight.start, flight.start + lipDuration)
            if (amount <= 0.0) continue
            lipPaint.alpha = alpha(amount)
            val saved = canvas.save()
            canvas.translate(flight.lipX, 0f)
            canvas.drawLine(-lipLineHalfLength, .7f, lipLineHalfLength, .7f, lipPaint)
            canvas.restoreToCount(saved)
        }
    }

    fun dominoWeight(day: Int, time: Double): Float {
        if (day !in 5..7) return 0f
        val start = dominoStart + dominoStagger * (day - 5)
        return bell(time, start, start + dominoDuration).toFloat()
    }

    private fun cubic(flight: Flight, p: Double) {
        val q = 1.0 - p
        val values = flight.points
        for (axis in 0..1) {
            point[axis] = (q * q * q * values[axis] + 3 * q * q * p * values[2 + axis] +
                3 * q * p * p * values[4 + axis] + p * p * p * values[6 + axis]).toFloat()
            point[2 + axis] = (3 * q * q * (values[2 + axis] - values[axis]) +
                6 * q * p * (values[4 + axis] - values[2 + axis]) +
                3 * p * p * (values[6 + axis] - values[4 + axis])).toFloat()
        }
    }

    private fun characterWindows(name: String): Map<String, DoubleArray> {
        val values = contract.getJSONObject(name)
        return arrayOf("orange", "pink").associateWith { numbers(values.getJSONArray(it)) }
    }

    private fun flights(values: JSONArray): Array<Flight> {
        return Array(values.length()) { index ->
            val item = values.getJSONObject(index)
            val points = item.getJSONArray("points")
            Flight(item.optString("id"), item.getDouble("start"), item.getDouble("end"),
                DoubleArray(8) { points.getJSONArray(it / 2).getDouble(it % 2) }, item.optDouble("lipX", 0.0).toFloat())
        }
    }

    private class Flight(val id: String, val start: Double, val end: Double, val points: DoubleArray, val lipX: Float)

    private companion object {
        fun numbers(values: JSONArray): DoubleArray = DoubleArray(values.length()) { values.getDouble(it) }

        fun ease(samples: DoubleArray, progress: Double): Double {
            val frame = progress.coerceIn(0.0, 1.0) * samples.lastIndex
            val index = frame.toInt()
            val next = min(index + 1, samples.lastIndex)
            return samples[index] + (samples[next] - samples[index]) * (frame - index)
        }

        fun bell(time: Double, start: Double, end: Double): Double {
            if (time <= start || time >= end) return 0.0
            return sin(PI * (time - start) / (end - start))
        }

        fun alpha(value: Double): Int = (value.coerceIn(0.0, 1.0) * 255).roundToInt()
    }
}
