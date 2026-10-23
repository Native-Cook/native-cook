package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import kotlin.math.*

internal object HoloTheme {
    const val loop = 13333f
    const val beat = loop / 4f
    val phases = floatArrayOf(.84f, .55f, .56f, .904f)
    val sessions = arrayOf(
        arrayOf("WELCOME", "DAY 1 · 09:00", "Opening welcome", "Doors, coffee and a first look at what the next two days hold.", "Mira Solène", "Host, Iridia", "MS"),
        arrayOf("2ND DAY", "DAY 2 · 09:30", "Second day kickoff", "A recap of day one, then straight into the workshops.", "Kenji Marlow", "Programme lead", "KM"),
        arrayOf("EXPERIENCE", "DAY 2 · 14:00", "The experience lab", "Hands-on stations: light, sound and motion you can touch.", "Ines Okoro", "Studio director", "IO"),
        arrayOf("KEYNOTE", "DAY 2 · 17:30", "Closing keynote", "One idea, forty minutes, and the reason we all came.", "Avery Lindqvist", "Founder", "AL"),
    )
    val palettes = arrayOf(
        intArrayOf(0xCC18F5, 0x9A4DFF, 0x5C8CFF, 0x6FB6FF, 0xBDF4EA, 0xFFFFFF),
        intArrayOf(0xE8203A, 0xEE4038, 0xFFF0A0, 0xD6E8FF, 0xFF6FB4, 0xF23D8E),
        intArrayOf(0xCB3AF5, 0xB94BF3, 0x8E7DFF, 0x5FCDF6, 0x1FF2A4, 0x7F8CFF),
        intArrayOf(0xEF10C9, 0xE60A3E, 0xFFEB96, 0xF45BC8, 0xF9B4D6, 0xFBC9E0),
    )
    fun mod(n: Float, divisor: Float = 4f) = ((n % divisor) + divisor) % divisor
    fun wrap(n: Float) = mod(n + 2f) - 2f
    fun nearest(n: Float) = floor(n + .5f).toInt()
    fun index(n: Int) = ((n % 4) + 4) % 4
    fun smooth(lo: Float, hi: Float, n: Float): Float {
        val t = ((n - lo) / (hi - lo)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun bezier(x: Float, x1: Float = .23f, y1: Float = 1f, x2: Float = .32f, y2: Float = 1f): Float {
        val value = x.coerceIn(0f, 1f)
        if (value == 0f || value == 1f) return value
        var lo = 0f
        var hi = 1f
        var t = value
        repeat(18) {
            val inv = 1f - t
            val bx = 3f * inv * inv * t * x1 + 3f * inv * t * t * x2 + t * t * t
            if (bx < value) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        val inv = 1f - t
        return 3f * inv * inv * t * y1 + 3f * inv * t * t * y2 + t * t * t
    }
    private val xs = floatArrayOf(0f, 80f, 117f, 100f, 56f)
    private val dxs = floatArrayOf(180f, 101f, 0f, -49f, -88f)
    private val sides = floatArrayOf(192f, 164f, 136f, 120f, 104f)
    private val dsides = floatArrayOf(0f, -56f, -40f, -32f, -32f)
    private fun curve(a: Float, values: FloatArray, slopes: FloatArray): Float {
        val j = min(3, floor(a * 2f).toInt())
        val t = (a * 2f - j).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        return (2*t3-3*t2+1)*values[j] + (t3-2*t2+t)*slopes[j]/2 +
            (-2*t3+3*t2)*values[j+1] + (t3-t2)*slopes[j+1]/2
    }
    data class Pose(val x: Float, val side: Float, val opacity: Float, val z: Int)
    fun pose(d: Float): Pose {
        val a = abs(d)
        return Pose(sign(d) * curve(a, xs, dxs), curve(a, sides, dsides),
            1f - smooth(1.6f, 1.95f, a), nearest((2f-a)*1000)*2 + if (d >= 0) 1 else 0)
    }
    data class Auto(val pos: Float, val squeeze: Float, val buttonScale: Float, val added: Boolean)
    fun autoplay(ms: Float): Auto {
        val loops = floor(ms / loop)
        val time = mod(ms, loop)
        val beatIndex = floor(time / beat)
        val local = time - beatIndex * beat
        var advance = 0f
        var squeeze = 1f
        if (local >= 2400f) {
            if (local < 2530f) {
                val p = smooth(0f, 1f, (local - 2400f) / 130f)
                advance = .03f * p
                squeeze = 1f - .03f * p
            } else {
                val p = bezier((local - 2530f) / 800f, .04f, .85f, .55f, 1f)
                advance = .03f + .97f * p
                squeeze = .97f + .03f * p
            }
        }
        val sincePress = local - 700f
        val button = if (beatIndex == 2f && sincePress >= 0 && sincePress < 240)
            1f - .03f * sin(sincePress / 240f * PI.toFloat()) else 1f
        return Auto(loops*4 + beatIndex + advance, squeeze, button,
            (beatIndex == 2f && local >= 820) || (beatIndex == 3f && local < 3000))
    }
    private val crossings = FloatArray(4) { beatIndex ->
        var lo = beatIndex * beat
        var hi = (beatIndex + 1) * beat
        repeat(32) {
            val mid = (lo + hi) / 2f
            if (autoplay(mid).pos < beatIndex + .5f) lo = mid else hi = mid
        }
        (lo + hi) / 2f
    }
    fun autoVelocity(ms: Float): Float {
        var velocity = 0f
        var weightTotal = 0f
        repeat(14) { j ->
            val lag = j * 25f
            val weight = exp(-lag / 70f)
            val sample = (autoplay(ms-lag).pos-autoplay(ms-lag-8).pos)*125
            velocity += sample.coerceIn(-8f, 8f)*weight
            weightTotal += weight
        }
        return velocity / weightTotal
    }
    fun autoAge(ms: Float): Float {
        val time = mod(ms, loop)
        var crossing = crossings[3] - loop
        crossings.forEach { if (time >= it) crossing = it }
        return min(900f, time-crossing)
    }
    fun releaseTarget(position: Float, start: Float, velocity: Float): Float {
        val origin = nearest(start)
        val projected = position.toDouble() + velocity.toDouble() * .16
        val limit = if (abs(velocity) > 7 && abs(projected-origin) > 1.6) 2 else 1
        return floor(projected + .5).toInt().coerceIn(origin-limit, origin+limit).toFloat()
    }
    fun arrivalScale(age: Float) = 1f + .02f * sin(PI.toFloat() * (age/420).coerceIn(0f, 1f))
}
