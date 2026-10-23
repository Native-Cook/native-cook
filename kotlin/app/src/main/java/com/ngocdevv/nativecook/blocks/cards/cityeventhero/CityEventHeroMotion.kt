package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal data class CityEventHeroSpring(val duration: Float, val dampingRatio: Float)

internal val CityEventHeroFanSpring = CityEventHeroSpring(.55f, .72f)
internal val CityEventHeroSpotSpring = CityEventHeroSpring(.4f, .62f)
internal val CityEventHeroUiSpring = CityEventHeroSpring(.4f, .8f)
internal val CityEventHeroPopSpring = CityEventHeroSpring(.32f, .3f)
internal val CityEventHeroNudgeSpring = CityEventHeroSpring(.45f, .55f)
internal val CityEventHeroCameraSpring = CityEventHeroSpring(.7f, .82f)

internal object CityEventHeroSpace {
    const val focusX = 428f
    const val focusY = 650f
    const val far = .08f
    const val perDepth = .32f
    const val tower = .5f
    const val near = .78f
    const val ampX = 32f
    const val ampY = 24f
    const val dolly = .1f
    const val coverX = 1f / 400f
    const val coverY = 1f / 600f
    const val title = .3f
    const val follow = .12f
    const val reach = 1.4f
    const val tilt = 30f
    const val tiltTau = 1.2f
}

internal const val CityEventHeroDepthScale = 5.2f

internal object CityEventHeroDay {
    const val from = .7f
    const val to = 2.5f
    const val hourFrom = 9f
    const val hourDusk = 19f
    const val hourEnd = 23.75f
    const val azFrom = 110f
    const val azTo = 345f
    const val elPeak = 50f
    const val rise = 7.2f
    const val set = 19.1f
    const val breakIn = .14f
    const val fadeFrom = .93f
}

internal object CityEventHeroViewCamera {
    const val f = 1000f
    const val cx = 425f
    const val horizon = 930f
}

internal object CityEventHeroNight {
    const val fall = 2.5f
    const val fallEnd = 3.4f
    const val lift = 8f
    const val liftEnd = 9f
    const val grade = .7f
    const val frontFrom = .15f
    const val soft = .035f
    const val edge = .03f
    const val scrubX = .6f
    const val scrubY = .4f
    const val flick = .8f
    const val toggle = 2.2f
    const val fireFade = .6f
}

internal object CityEventHeroTitle {
    const val inAt = .75f
    const val inEnd = 1f
    const val out = 3.05f
    const val outEnd = 3.4f
    const val hold = .6f
    const val fade = .35f
    const val step = 5
    const val base = 640f
    const val size = 92f
    const val suffix = 26f
    const val sep = 8f
}

internal object CityEventHeroCta {
    const val press = 3.2f
    const val release = 3.4f
    const val dusk = 1.1f
}

internal object CityEventHeroTower {
    const val tipX = 428f
    const val tipY = 293f
    const val baseY = 1320f
}

internal object CityEventHeroLed {
    const val at = 3.45f
    const val cols = 29f
    const val row = 3f
    const val mid = 427.5f
    const val eye = 930f
    const val sag = .035f
    const val rows = 342f
    const val boot = .35f
    val words = listOf("SEE", "YOU", "THERE")
    val pages = floatArrayOf(.3f, .72f, 1.14f, 1.62f)
    const val pagesEnd = 2.22f
    const val enter = .16f
    const val leave = .1f
    const val climb = 300f
    const val drift = 20f
    const val centre = 200f
    const val slot = 40f
    const val dateGap = 8f
    const val colsFrom = 7f
    const val glyphCols = 3f
    const val glyphRows = 5f
    const val stagger = .04f
    const val letterFlash = .18f
    const val dateFrom = 31f
    const val dateTo = 17f
    const val roll = .4f
    const val avatarIn = 2.2f
    const val avatarInEnd = 2.36f
    const val avatarRow = 150f
    const val climbEnd = 2.95f
    const val avatarR = 1.5f
    const val fire = 2.95f
    const val out = 2.95f
    const val outEnd = 3.4f
    const val fieldWave = 120f
    const val fieldSpeed = 180f
}

internal val CityEventHeroTowerHalfWidths = floatArrayOf(
    0f, 4.2f, 8f, 11.1f, 13.9f, 16.6f, 18.2f, 19f, 20f, 21f, 21.8f, 22.1f, 23f, 24f, 25f, 25.8f,
    26.1f, 27f, 28f, 28.9f, 29.1f, 30f, 30f, 30.5f, 32f, 32f, 32.8f, 34f, 34f, 34.9f, 36f, 36f,
    37f, 37.9f, 38f, 39f, 39.5f, 40f, 40f, 40.3f, 41f, 41f, 42f, 42f, 42f, 42f, 42f, 42f, 42.5f, 43f,
    43f, 43.5f, 43.5f, 43.6f, 44.4f, 44.5f, 44.5f, 45f, 45.5f, 46f, 46f, 46f, 46f, 46.1f, 46f,
)

internal data class CityEventHeroBurst(
    val at: Float,
    val dx: Float,
    val dy: Float,
    val launchDx: Float,
    val launchDy: Float,
    val count: Int,
    val speed: Float,
    val spread: Float,
    val upSlow: Float,
    val drag: Float,
    val gravity: Float,
    val life: Float,
    val trail: Float,
    val crackle: Float,
    val hot: Int,
    val color: Int,
    val ember: Int,
    val flash: Float,
)

internal object CityEventHeroFire {
    const val end = 2.3f
    const val flareIn = .15f
    const val flareOut = .55f
    const val flashIn = .05f
    const val flashTau = .32f
    const val flashGain = 1.1f
    const val flashR = 260f
    const val rise = .45f
    const val smokeIn = .25f
    const val smokeTau = 1.1f
    const val smokeR = 70f
    const val smokeRise = 18f
    const val depthDp = 70f
    val bursts = listOf(
        CityEventHeroBurst(.12f, 0f, 0f, 0f, 0f, 96, 520f, 1.95f, .5f, 1.8f, 360f, 1.9f, .26f, .15f, 0xFFFFF7E6.toInt(), 0xFFFFC266.toInt(), 0xFFE8642A.toInt(), 1f),
        CityEventHeroBurst(.55f, -270f, 330f, 25f, 600f, 64, 540f, 0f, 0f, 2.6f, 300f, 1.5f, .12f, .45f, 0xFFFFFFFF.toInt(), 0xFFE4ECFF.toInt(), 0xFF9DB4FF.toInt(), .7f),
        CityEventHeroBurst(.72f, 260f, 260f, -20f, 640f, 64, 560f, 0f, 0f, 2.6f, 300f, 1.4f, .13f, .25f, 0xFFFFF1E8.toInt(), 0xFFFF6B57.toInt(), 0xFFC2311F.toInt(), .75f),
    )
}

internal const val CityEventHeroShowEnd = CityEventHeroLed.fire + CityEventHeroFire.end
internal const val CityEventHeroLedStill = 2f

internal data class CityEventHeroFrame(
    val fan: FloatArray = FloatArray(7),
    val spot: FloatArray = FloatArray(7),
    var nameFrom: Int = 0,
    var nameTo: Int = 0,
    var nameProgress: Float = 0f,
    var like: Float = 0f,
    var pop: Float = 1f,
    var ring: Float = 0f,
    var count: Float = 0f,
    var press: Float = 0f,
    var chevron: Float = 0f,
    var sweep: Float = 0f,
    var mist: Float = 0f,
    var drift: Float = 0f,
    var day: Float = 0f,
    var night: Float = 0f,
    var show: Float = -1f,
    var camX: Float = 0f,
    var camY: Float = 0f,
    var camZ: Float = 0f,
    var title: Float = 0f,
    var still: Float = 1f,
    var guestOpacity: Float = 1f,
) {
    val fire: Float
        get() = CityEventHeroFireOf(show)
}

internal fun CityEventHeroClamp(value: Float): Float = value.coerceIn(0f, 1f)

internal fun CityEventHeroSegment(value: Float, from: Float, to: Float): Float = CityEventHeroClamp((value - from) / (to - from))

internal fun CityEventHeroBezier(value: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val x = CityEventHeroClamp(value)
    if (x == 0f || x == 1f) return x
    var low = 0f
    var high = 1f
    repeat(18) {
        val t = (low + high) / 2f
        val u = 1f - t
        val at = 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t
        if (at < x) {
            low = t
        } else {
            high = t
        }
    }
    val t = (low + high) / 2f
    val u = 1f - t
    return 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t
}

internal fun CityEventHeroEaseOut(value: Float): Float = CityEventHeroBezier(value, .23f, 1f, .32f, 1f)

internal fun CityEventHeroEaseInOut(value: Float): Float = CityEventHeroBezier(value, .77f, 0f, .175f, 1f)

internal fun CityEventHeroEaseNightTap(value: Float): Float = CityEventHeroBezier(value, .3f, .55f, .3f, 1f)

internal fun CityEventHeroEaseSine(value: Float): Float = ((1.0 - cos(PI * CityEventHeroClamp(value))) / 2.0).toFloat()

/** Reanimated's `Easing.bezier(0.37, 0, 0.63, 1)`, named `sine` in the reference. */
internal fun CityEventHeroEaseShowCamera(value: Float): Float = CityEventHeroBezier(value, .37f, 0f, .63f, 1f)

internal fun CityEventHeroSpringAt(seconds: Float, spring: CityEventHeroSpring): Float {
    if (seconds <= 0f) return 0f
    if (spring.dampingRatio >= 1f) {
        val omega = (2.0 * PI / spring.duration).toFloat()
        return 1f - (1f + omega * seconds) * exp(-omega * seconds)
    }
    val omega = 2.0 * PI / spring.duration
    val damped = omega * sqrt(1.0 - spring.dampingRatio * spring.dampingRatio)
    val envelope = exp(-spring.dampingRatio * omega * seconds)
    if (envelope < .0001) return 1f
    return (1.0 - envelope * (cos(damped * seconds) + spring.dampingRatio * omega / damped * sin(damped * seconds))).toFloat()
}

internal fun CityEventHeroSpringValue(
    from: Float,
    to: Float,
    velocity: Float,
    elapsed: Float,
    spring: CityEventHeroSpring,
): Float {
    if (elapsed <= 0f) return from
    val omega = (2.0 * PI / spring.duration).toFloat()
    val displacement = from - to
    if (spring.dampingRatio >= 1f) {
        return to + (displacement + (velocity + omega * displacement) * elapsed) * exp(-omega * elapsed)
    }
    val damped = omega * sqrt(1f - spring.dampingRatio * spring.dampingRatio)
    val envelope = exp(-spring.dampingRatio * omega * elapsed)
    val oscillation = displacement * cos(damped * elapsed) +
        (velocity + spring.dampingRatio * omega * displacement) / damped * sin(damped * elapsed)
    return to + envelope * oscillation
}

internal fun CityEventHeroRubber(value: Float, limit: Float): Float {
    val amount = abs(value)
    if (amount <= limit) return value
    val over = amount - limit
    return sign(value) * (limit + over / (1f + over / limit))
}

internal fun CityEventHeroFlight(fan: Float): Float {
    val value = CityEventHeroClamp(fan)
    return 4f * value * (1f - value)
}

internal val CityEventHeroSlots = CityEventHeroGuests.sortedBy { it.cx }

internal data class CityEventHeroFanned(val cx: Float, val cy: Float, val rotation: Float)

internal val CityEventHeroFannedSlots: List<CityEventHeroFanned> = run {
    val gap = 18f
    val radius = 2400f
    val center = CityEventHeroSlots[3]
    val total = CityEventHeroSlots.sumOf { it.d.toDouble() }.toFloat() + gap * 6f
    var x = center.cx - total / 2f
    CityEventHeroSlots.map { guest ->
        val cx = x + guest.d / 2f
        x += guest.d + gap
        val dx = cx - center.cx
        val drop = radius - sqrt(radius * radius - dx * dx)
        CityEventHeroFanned(cx, center.cy - 8f + drop, (asin(dx / radius) * 180.0 / PI * .6).toFloat())
    }
}

internal fun CityEventHeroNightAt(time: Float): Float = when {
    time < CityEventHeroNight.fall || time >= CityEventHeroNight.liftEnd -> 0f
    time < CityEventHeroNight.fallEnd -> CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroNight.fall, CityEventHeroNight.fallEnd))
    time < CityEventHeroNight.lift -> 1f
    else -> 1f - CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroNight.lift, CityEventHeroNight.liftEnd))
}

internal fun CityEventHeroDayAt(time: Float): Float {
    if (time >= CityEventHeroNight.fallEnd) return 0f
    return CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroDay.from, CityEventHeroDay.to))
}

internal fun CityEventHeroCameraAt(time: Float, output: FloatArray) {
    output[0] = if (time < CityEventHeroDay.to) {
        -CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroDay.from, CityEventHeroDay.to))
    } else {
        -(1f - CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroDay.to, CityEventHeroLed.at)))
    }
    val back = 1f - CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroNight.lift, CityEventHeroNight.liftEnd))
    output[2] = if (time < CityEventHeroNight.lift) CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroLed.at, CityEventHeroLed.at + 2.55f)) else back
    output[1] = if (time < CityEventHeroNight.lift) CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroLed.at + CityEventHeroLed.avatarIn - .05f, CityEventHeroLed.at + CityEventHeroLed.climbEnd)) else back
}

internal fun CityEventHeroFrameAt(time: Float): CityEventHeroFrame {
    // Avoid adding a full period to positive Float times: it shifts exact phase boundaries.
    val wrapped = time % LOOP
    val t = if (wrapped < 0f) wrapped + LOOP else wrapped
    val frame = CityEventHeroFrame()
    frame.day = CityEventHeroDayAt(t)
    frame.night = CityEventHeroNightAt(t)
    frame.show = if (t >= CityEventHeroLed.at && t < CityEventHeroLed.at + CityEventHeroShowEnd) t - CityEventHeroLed.at else -1f
    val camera = FloatArray(3)
    CityEventHeroCameraAt(t, camera)
    frame.camX = camera[0]
    frame.camY = camera[1]
    frame.camZ = camera[2]
    frame.title = CityEventHeroEaseOut(CityEventHeroSegment(t, CityEventHeroTitle.inAt, CityEventHeroTitle.inEnd)) * (1f - CityEventHeroEaseSine(CityEventHeroSegment(t, CityEventHeroTitle.out, CityEventHeroTitle.outEnd)))
    frame.press = if (t < CityEventHeroCta.release) {
        CityEventHeroEaseOut(CityEventHeroSegment(t, CityEventHeroCta.press, CityEventHeroCta.press + .12f))
    } else {
        1f - CityEventHeroSpringAt(t - CityEventHeroCta.release, CityEventHeroUiSpring)
    }
    frame.chevron = if (t < CityEventHeroCta.release) {
        0f
    } else if (t < CityEventHeroCta.release + .12f) {
        CityEventHeroEaseOut(CityEventHeroSegment(t, CityEventHeroCta.release, CityEventHeroCta.release + .12f))
    } else {
        1f - CityEventHeroSpringAt(t - CityEventHeroCta.release - .12f, CityEventHeroNudgeSpring)
    }
    val sweep = CityEventHeroSegment(t, CityEventHeroCta.release, CityEventHeroCta.release + .5f)
    frame.sweep = if (sweep > 0f && sweep < 1f) CityEventHeroEaseInOut(sweep) else 0f
    frame.mist = ((1.0 - cos(2.0 * PI * t / LOOP)) / 2.0).toFloat()
    frame.drift = t
    return frame
}

internal fun CityEventHeroFireOf(show: Float): Float {
    if (show < CityEventHeroLed.fire || show >= CityEventHeroShowEnd) return -1f
    return show - CityEventHeroLed.fire
}

internal fun CityEventHeroFlareAt(fire: Float): Float = when {
    fire < 0f -> 0f
    fire < CityEventHeroFire.flareIn -> CityEventHeroEaseOut(fire / CityEventHeroFire.flareIn)
    else -> 1f - CityEventHeroEaseOut(CityEventHeroSegment(fire, CityEventHeroFire.flareIn, CityEventHeroFire.flareIn + CityEventHeroFire.flareOut))
}

internal fun CityEventHeroFlashAt(burst: CityEventHeroBurst, age: Float): Float {
    if (age <= 0f) return 0f
    val attack = if (age < CityEventHeroFire.flashIn) age / CityEventHeroFire.flashIn else 1f
    return burst.flash * attack * exp(-(age - minOf(age, CityEventHeroFire.flashIn)) / CityEventHeroFire.flashTau)
}

internal fun CityEventHeroFireLight(fire: Float): Float {
    if (fire < 0f || fire >= CityEventHeroFire.end) return 0f
    var sum = .6f * CityEventHeroFlareAt(fire)
    CityEventHeroFire.bursts.forEach { burst ->
        sum += CityEventHeroFlashAt(burst, fire - burst.at)
    }
    return CityEventHeroClamp(sum)
}

internal fun CityEventHeroHash3(burst: Int, spark: Int, channel: Int): Float {
    val value = sin(burst * 127.1 + spark * 311.7 + channel * 74.7) * 43758.5453
    return (value - floor(value)).toFloat()
}

internal fun CityEventHeroShellLeft(seconds: Float): Float = 1f - CityEventHeroEaseOut(CityEventHeroClamp(seconds / CityEventHeroFire.rise))

internal fun CityEventHeroTowerHalfWidth(y: Float): Float {
    val position = (y - 293f) / 16f
    if (position <= 0f) return 0f
    val last = CityEventHeroTowerHalfWidths.lastIndex
    if (position >= last) return CityEventHeroTowerHalfWidths[last]
    val index = floor(position).toInt()
    return CityEventHeroTowerHalfWidths[index] + (CityEventHeroTowerHalfWidths[index + 1] - CityEventHeroTowerHalfWidths[index]) * (position - index)
}

internal fun CityEventHeroLedAt(time: Float, output: FloatArray) {
    output.fill(0f)
    output[2] = -1f
    if (time < 0f || time >= CityEventHeroShowEnd) {
        output[1] = -1f
        return
    }
    val dissolve = CityEventHeroEaseSine(CityEventHeroSegment(time, CityEventHeroLed.out, CityEventHeroLed.outEnd))
    output[0] = CityEventHeroClamp(time / .12f) * (1f - dissolve)
    output[1] = if (time < CityEventHeroLed.boot) CityEventHeroEaseInOut(time / CityEventHeroLed.boot) * (CityEventHeroLed.rows + 20f) else 10_000f
    for (page in CityEventHeroLed.pages.indices) {
        val from = CityEventHeroLed.pages[page]
        val to = if (page + 1 < CityEventHeroLed.pages.size) CityEventHeroLed.pages[page + 1] else CityEventHeroLed.pagesEnd
        if (time < from || time >= to) continue
        val length = if (page < CityEventHeroLed.words.size) CityEventHeroLed.words[page].length else 5
        val height = length * CityEventHeroLed.slot - (CityEventHeroLed.slot - CityEventHeroLed.glyphRows * 7f) + if (page >= CityEventHeroLed.words.size) CityEventHeroLed.dateGap else 0f
        val rest = CityEventHeroLed.centre - height / 2f
        val enter = CityEventHeroEaseOut(CityEventHeroSegment(time, from, from + CityEventHeroLed.enter))
        val leave = CityEventHeroSegment(time, to - CityEventHeroLed.leave, to)
        output[2] = page.toFloat()
        output[3] = -height + (rest + height) * enter + CityEventHeroLed.drift * (time - from) + CityEventHeroLed.climb * leave * leave * leave
        output[4] = (time - from) / CityEventHeroLed.stagger
    }
    val dateAt = CityEventHeroLed.pages.last()
    output[5] = round(CityEventHeroLed.dateFrom - (CityEventHeroLed.dateFrom - CityEventHeroLed.dateTo) * CityEventHeroEaseOut(CityEventHeroSegment(time, dateAt + CityEventHeroLed.enter * .5f, dateAt + CityEventHeroLed.enter * .5f + CityEventHeroLed.roll)))
    val climb = CityEventHeroClamp(CityEventHeroSegment(time, CityEventHeroLed.avatarInEnd, CityEventHeroLed.climbEnd)).let { it * it * it }
    val rowAt = CityEventHeroLed.avatarRow + (CityEventHeroLed.rows - 6f - CityEventHeroLed.avatarRow) * climb
    val y = CityEventHeroTower.baseY - rowAt * CityEventHeroLed.row
    output[6] = y
    output[7] = CityEventHeroLed.avatarR * CityEventHeroTowerHalfWidth(y) * CityEventHeroEaseOut(CityEventHeroSegment(time, CityEventHeroLed.avatarIn, CityEventHeroLed.avatarInEnd))
    output[8] = if (time >= CityEventHeroLed.avatarIn && time < CityEventHeroLed.climbEnd + .06f) 1f else 0f
    val boot = time - CityEventHeroLed.boot
    val fire = time - CityEventHeroLed.fire
    output[9] = (if (boot > 0f) .55f * exp(-boot / .12f) else 0f) + (if (fire > 0f) exp(-fire / .2f) else 0f)
    output[10] = time * CityEventHeroLed.fieldSpeed
    output[11] = output[0] * (.55f + .45f * max(if (output[2] >= 0f) 1f else 0f, output[8])) + .4f * output[9]
    output[12] = dissolve
}

internal data class CityEventHeroClock(var hour: Int = 9, var minute: Int = 0, var pm: Boolean = false)

internal fun CityEventHeroClockAt(day: Float, night: Float, output: CityEventHeroClock) {
    val hour = if (night > 0f) {
        CityEventHeroDay.hourDusk + (CityEventHeroDay.hourEnd - CityEventHeroDay.hourDusk) * night
    } else {
        CityEventHeroDay.hourFrom + (CityEventHeroDay.hourDusk - CityEventHeroDay.hourFrom) * day
    }
    val total = floor(hour * 60f / CityEventHeroTitle.step + 1e-6f).toInt() * CityEventHeroTitle.step
    val hour24 = total / 60 % 24
    output.minute = total - total / 60 * 60
    output.pm = hour24 >= 12
    output.hour = (hour24 % 12).let { if (it == 0) 12 else it }
}

internal data class CityEventHeroSun(var azimuth: Float = 0f, var elevation: Float = 0f, var amount: Float = 0f)

internal fun CityEventHeroSunAt(day: Float, output: CityEventHeroSun) {
    val progress = CityEventHeroClamp(day)
    val hour = CityEventHeroDay.hourFrom + (CityEventHeroDay.hourDusk - CityEventHeroDay.hourFrom) * progress
    output.azimuth = ((CityEventHeroDay.azFrom + (CityEventHeroDay.azTo - CityEventHeroDay.azFrom) * progress) * PI / 180.0).toFloat()
    val arc = CityEventHeroClamp((hour - CityEventHeroDay.rise) / (CityEventHeroDay.set - CityEventHeroDay.rise))
    output.elevation = (CityEventHeroDay.elPeak * sin(PI * arc) * PI / 180.0).toFloat()
    val breakProgress = progress / CityEventHeroDay.breakIn
    val enter = if (breakProgress >= 1f) 1f else breakProgress * breakProgress * (3f - 2f * breakProgress)
    val fade = CityEventHeroClamp((progress - CityEventHeroDay.fadeFrom) / (1f - CityEventHeroDay.fadeFrom))
    output.amount = enter * (1f - fade * fade * (3f - 2f * fade))
}

internal enum class CityEventHeroMode { AUTO, TOUCH, RETURN }

internal class CityEventHeroController(autoPlay: Boolean) {
    var frame = CityEventHeroFrameAt(0f)
        private set
    var mode = if (autoPlay) CityEventHeroMode.AUTO else CityEventHeroMode.TOUCH
        private set
    var fanned = false
        private set
    var liked = false
        private set
    var spotlight = -1
        private set
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    private var phase = 0f
    private var lastNanos = -1L
    private var touchNanos = Long.MIN_VALUE
    private var returnNanos = 0L
    private var returnFrom = CityEventHeroFrameAt(0f)
    private var transitionNanos = 0L
    private var transitionPending = false
    private var transitionFrom = CityEventHeroFrameAt(0f)
    private var transitionName = 0
    private var transitionLiked = false
    private var transitionFanned = false
    private var pressedView = false
    private var viewReleasedNanos = -1L
    private var viewCommitted = false
    private var timeOfDay = 0f
    private var timeOfDayFrom = 0f
    private var timeOfDayTo = 0f
    private var timeOfDayStart = -1L
    private var timeOfDayDuration = 0f
    private var timeOfDaySpringVelocity = 0f
    private var timeOfDaySpring = false
    private var timeOfDayNightTap = false
    private var skyDragging = false
    private var holding = false
    private var panTimeOfDay = 0f
    private var panCameraX = 0f
    private var panCameraY = 0f
    private var cameraX = 0f
    private var cameraY = 0f
    private var cameraFromX = 0f
    private var cameraFromY = 0f
    private var cameraVelocityX = 0f
    private var cameraVelocityY = 0f
    private var cameraSpringNanos = -1L
    private var showStartsNanos = -1L
    private var showDelayNanos = 0L
    private var showCameraFromX = 0f
    private var showCameraFromY = 0f
    private var reducedStill = false
    private var reducedStillFrom = 1f
    private var reducedStillTo = 1f
    private var reducedStillNanos = -1L
    private var titleStartedNanos = -1L
    private var titleHoldNanos = 0L
    private var gravityReady = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var showHapticNanos = -1L
    private var lifecycleActive = true
    private var pausedAtNanos = -1L

    fun tick(now: Long, autoPlay: Boolean, reducedMotion: Boolean): Boolean {
        if (!lifecycleActive) return false
        val delta = if (lastNanos < 0L) 0f else (now - lastNanos).coerceAtLeast(0L) / 1_000_000_000f
        lastNanos = now
        when (mode) {
            CityEventHeroMode.AUTO -> {
                if (!autoPlay) return false
                phase = (phase + delta) % LOOP
                frame = CityEventHeroFrameAt(phase)
                return true
            }
            CityEventHeroMode.TOUCH -> {
                val animating = manualNeedsFrame(now, reducedMotion)
                if (animating) frame = manualFrame(now, reducedMotion)
                val showRunning = showIsRunning(now)
                if (autoPlay && !holding && !skyDragging && !pressedView && !showRunning && now - touchNanos >= RESUME_AFTER * 1_000_000L) {
                    beginReturn(now)
                    return true
                }
                return animating
            }
            CityEventHeroMode.RETURN -> {
                val elapsed = (now - returnNanos).coerceAtLeast(0L) / 1_000_000_000f
                frame = returnFrame(elapsed)
                if (now - returnNanos >= RETURN_MS * 1_000_000L) {
                    phase = 0f
                    frame = CityEventHeroFrameAt(0f)
                    mode = if (autoPlay) CityEventHeroMode.AUTO else CityEventHeroMode.TOUCH
                }
                return true
            }
        }
    }

    fun setLifecycleActive(active: Boolean, now: Long) {
        if (active == lifecycleActive) {
            lastNanos = -1L
            return
        }
        lifecycleActive = active
        if (!active) {
            if (holding) {
                holding = false
                skyDragging = false
                pressedView = false
                touchNanos = now
            }
            pausedAtNanos = now
            lastNanos = -1L
            return
        }
        val pausedFor = if (pausedAtNanos >= 0L) (now - pausedAtNanos).coerceAtLeast(0L) else 0L
        fun shifted(value: Long): Long = if (value >= 0L) value + pausedFor else value
        if (touchNanos != Long.MIN_VALUE) touchNanos += pausedFor
        returnNanos = shifted(returnNanos)
        transitionNanos = shifted(transitionNanos)
        viewReleasedNanos = shifted(viewReleasedNanos)
        timeOfDayStart = shifted(timeOfDayStart)
        cameraSpringNanos = shifted(cameraSpringNanos)
        showStartsNanos = shifted(showStartsNanos)
        reducedStillNanos = shifted(reducedStillNanos)
        titleStartedNanos = shifted(titleStartedNanos)
        showHapticNanos = shifted(showHapticNanos)
        pausedAtNanos = -1L
        lastNanos = -1L
    }

    fun beginGesture(now: Long) {
        holding = true
        takeOver(now)
    }

    fun endGesture(now: Long) {
        holding = false
        touchNanos = now
    }

    fun cancelSkyDrag(now: Long) {
        skyDragging = false
        touchNanos = now
    }

    fun takeOver(now: Long) {
        if (mode != CityEventHeroMode.TOUCH) {
            timeOfDay = if (frame.night > 0f) 1f + frame.night else frame.day
            cameraX = frame.camX
            cameraY = frame.camY
            fanned = frame.fan.any { it > .5f }
            liked = frame.like > .5f
            spotlight = frame.spot.indices.maxByOrNull { frame.spot[it] }?.takeIf { frame.spot[it] > .5f } ?: -1
            if (frame.show >= 0f && frame.show < CityEventHeroShowEnd) {
                showStartsNanos = now - (frame.show * 1_000_000_000L).toLong()
                showDelayNanos = 0L
            }
            mode = CityEventHeroMode.TOUCH
        }
        beginTransition(now)
        touchNanos = now
    }

    fun tapGuests(now: Long, slot: Int) {
        beginTransition(now)
        if (!fanned) {
            fanned = true
        } else if (slot >= 0 && slot != spotlight) {
            spotlight = slot
        } else {
            spotlight = -1
            fanned = false
        }
        touchNanos = now
    }

    fun tapLike(now: Long) {
        beginTransition(now)
        liked = !liked
        touchNanos = now
    }

    fun setViewPressed(now: Long, pressed: Boolean, commit: Boolean = false, reduced: Boolean = false) {
        beginTransition(now)
        pressedView = pressed
        if (!pressed) {
            viewReleasedNanos = now
            viewCommitted = commit
            if (commit) startShow(now, reduced)
        }
        touchNanos = now
    }

    fun toggleEvening(now: Long, reduced: Boolean) {
        if (showIsRunning(now)) return
        val target = if (timeOfDay > 1f) 0f else 2f
        startTimeOfDayAnimation(
            now = now,
            target = target,
            duration = if (reduced) .2f else CityEventHeroNight.toggle,
            spring = false,
            nightTap = !reduced,
        )
        if (reduced) {
            titleStartedNanos = -1L
        } else {
            startTitle(now, CityEventHeroNight.toggle)
        }
        touchNanos = now
    }

    fun startSkyDrag(now: Long, reduced: Boolean) {
        skyDragging = true
        panTimeOfDay = timeOfDay
        panCameraX = cameraX
        panCameraY = cameraY
        timeOfDayStart = -1L
        cameraSpringNanos = -1L
        titleStartedNanos = if (reduced) -1L else now
        titleHoldNanos = if (reduced) 0L else Long.MAX_VALUE
        touchNanos = now
    }

    fun scrubSky(now: Long, translationX: Float, translationY: Float, scrubX: Float, scrubY: Float, reduced: Boolean) {
        timeOfDay = (panTimeOfDay - translationX / scrubX + translationY / scrubY).coerceIn(0f, 2f)
        if (!reduced) {
            cameraX = CityEventHeroRubber(panCameraX + translationX * CityEventHeroSpace.follow / CityEventHeroSpace.ampX, CityEventHeroSpace.reach)
            cameraY = CityEventHeroRubber(panCameraY + translationY * CityEventHeroSpace.follow / CityEventHeroSpace.ampY, CityEventHeroSpace.reach)
        }
        touchNanos = now
    }

    fun finishSkyDrag(now: Long, velocityX: Float, velocityY: Float, scrubX: Float, scrubY: Float, reduced: Boolean) {
        skyDragging = false
        val velocity = -velocityX / scrubX + velocityY / scrubY
        val target = if (velocity > CityEventHeroNight.flick || (velocity > -CityEventHeroNight.flick && timeOfDay > 1f)) 2f else 0f
        timeOfDayFrom = timeOfDay
        timeOfDayTo = target
        timeOfDaySpringVelocity = velocity
        timeOfDayStart = now
        timeOfDayDuration = .9f
        timeOfDaySpring = true
        timeOfDayNightTap = false
        cameraFromX = cameraX
        cameraFromY = cameraY
        cameraVelocityX = velocityX * CityEventHeroSpace.follow / CityEventHeroSpace.ampX
        cameraVelocityY = velocityY * CityEventHeroSpace.follow / CityEventHeroSpace.ampY
        cameraSpringNanos = now
        if (!reduced) {
            titleStartedNanos = now
            titleHoldNanos = ((.9f + CityEventHeroTitle.hold) * 1_000_000_000L).toLong()
        } else {
            titleStartedNanos = -1L
        }
        touchNanos = now
    }

    fun updateGravity(x: Float, y: Float, deltaSeconds: Float, reduced: Boolean) {
        if (reduced) {
            tiltX = 0f
            tiltY = 0f
            gravityReady = false
            return
        }
        if (!gravityReady) {
            gravityReady = true
            gravityX = x
            gravityY = y
        }
        val amount = (deltaSeconds / CityEventHeroSpace.tiltTau).coerceIn(0f, 1f)
        gravityX += (x - gravityX) * amount
        gravityY += (y - gravityY) * amount
        tiltX = (gravityX - x) * CityEventHeroSpace.tilt
        tiltY = (y - gravityY) * CityEventHeroSpace.tilt
    }

    fun consumeShowHaptic(now: Long): Boolean {
        if (showHapticNanos < 0L || now < showHapticNanos) return false
        showHapticNanos = -1L
        return true
    }

    private fun startShow(now: Long, reduced: Boolean) {
        if (reduced) {
            timeOfDayStart = -1L
            reducedStill = !reducedStill
            reducedStillFrom = if (reducedStill) 0f else 1f
            reducedStillTo = if (reducedStill) 1f else 0f
            reducedStillNanos = now
            if (reducedStill) {
                timeOfDay = 2f
            }
            return
        }
        if (showIsRunning(now)) return
        val wait = if (timeOfDay >= 1.98f) 0f else CityEventHeroCta.dusk
        if (wait > 0f) startTimeOfDayAnimation(now, 2f, wait, spring = false, nightTap = false)
        showStartsNanos = now
        showDelayNanos = (wait * 1_000_000_000L).toLong()
        showCameraFromX = cameraX
        showCameraFromY = cameraY
        showHapticNanos = now + showDelayNanos + ((CityEventHeroLed.fire + CityEventHeroFire.bursts[0].at) * 1_000_000_000L).toLong()
    }

    private fun showIsRunning(now: Long): Boolean {
        if (showStartsNanos < 0L) return false
        val elapsed = (now - showStartsNanos - showDelayNanos) / 1_000_000_000f
        return elapsed >= 0f && elapsed < CityEventHeroShowEnd
    }

    private fun startTimeOfDayAnimation(
        now: Long,
        target: Float,
        duration: Float,
        spring: Boolean,
        nightTap: Boolean,
    ) {
        timeOfDayFrom = timeOfDay
        timeOfDayTo = target
        timeOfDayStart = now
        timeOfDayDuration = duration
        timeOfDaySpringVelocity = 0f
        timeOfDaySpring = spring
        timeOfDayNightTap = nightTap
    }

    private fun startTitle(now: Long, holdSeconds: Float) {
        titleStartedNanos = now
        titleHoldNanos = (holdSeconds * 1_000_000_000L).toLong()
    }

    private fun beginTransition(now: Long) {
        transitionFrom = frame.deepCopy()
        transitionNanos = now
        transitionPending = true
        transitionName = if (frame.nameProgress > .5f) frame.nameTo else frame.nameFrom
        transitionLiked = frame.like > .5f
        transitionFanned = fanned
        viewReleasedNanos = -1L
        viewCommitted = false
    }

    private fun beginReturn(now: Long) {
        returnFrom = frame.deepCopy()
        returnNanos = now
        fanned = false
        liked = false
        spotlight = -1
        showStartsNanos = -1L
        reducedStill = false
        mode = CityEventHeroMode.RETURN
        frame = returnFrame(0f)
    }

    private fun manualNeedsFrame(now: Long, reduced: Boolean): Boolean {
        if (timeOfDayStart >= 0L || cameraSpringNanos >= 0L || reducedStillNanos >= 0L) return true
        if (titleStartedNanos >= 0L) {
            val titleElapsed = (now - titleStartedNanos).coerceAtLeast(0L)
            val titleEnd = if (titleHoldNanos == Long.MAX_VALUE) {
                160_000_000L
            } else {
                160_000_000L + titleHoldNanos + (CityEventHeroTitle.fade * 1_000_000_000L).toLong()
            }
            if (titleElapsed <= titleEnd) return true
            if (titleHoldNanos != Long.MAX_VALUE && frame.title != 0f) return true
        }
        if (transitionPending) return true
        if (showStartsNanos >= 0L) {
            val showTime = (now - showStartsNanos - showDelayNanos) / 1_000_000_000f
            if (showTime < CityEventHeroShowEnd + .9f) return true
            if (frame.camX != 0f || frame.camY != 0f || frame.camZ != 0f) return true
        }
        return false
    }

    private fun returnFrame(elapsed: Float): CityEventHeroFrame {
        val glide = CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, 0f, (RETURN_MS - 100L) / 1000f))
        val result = CityEventHeroFrameAt(0f)
        for (slot in result.fan.indices) {
            val rank = abs(slot - 3)
            val fanProgress = CityEventHeroSpringAt(elapsed - rank * .045f, CityEventHeroFanSpring)
            result.fan[slot] = returnFrom.fan[slot] * (1f - fanProgress)
            val spotProgress = CityEventHeroSpringAt(elapsed, CityEventHeroSpotSpring)
            result.spot[slot] = returnFrom.spot[slot] * (1f - spotProgress)
        }
        val shownName = if (returnFrom.nameProgress > .5f) returnFrom.nameTo else returnFrom.nameFrom
        result.nameFrom = shownName
        result.nameTo = 0
        result.nameProgress = if (shownName == 0) 0f else CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, 0f, .32f))
        result.like = returnFrom.like * (1f - CityEventHeroEaseOut(CityEventHeroSegment(elapsed, 0f, .2f)))
        result.count = returnFrom.count * (1f - CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, 0f, .3f)))
        val popProgress = CityEventHeroSpringAt(elapsed, CityEventHeroUiSpring)
        result.pop = returnFrom.pop + (1f - returnFrom.pop) * popProgress
        result.ring = returnFrom.ring
        result.press = returnFrom.press * (1f - popProgress)
        result.chevron = returnFrom.chevron * (1f - CityEventHeroSpringAt(elapsed, CityEventHeroNudgeSpring))
        result.sweep = returnFrom.sweep
        result.mist = returnFrom.mist * (1f - glide)
        result.drift = returnFrom.drift
        result.day = if (returnFrom.night > 0f) 0f else returnFrom.day * (1f - glide)
        result.night = returnFrom.night * (1f - glide)
        result.show = -1f
        result.camX = returnFrom.camX * (1f - glide)
        result.camY = returnFrom.camY * (1f - glide)
        result.camZ = returnFrom.camZ * (1f - glide)
        result.title = returnFrom.title * (1f - glide)
        result.still = returnFrom.still + (1f - returnFrom.still) * glide
        result.guestOpacity = 1f
        return result
    }

    private fun manualFrame(now: Long, reduced: Boolean): CityEventHeroFrame {
        updateTimeOfDay(now)
        updateCamera(now)
        val elapsed = (now - transitionNanos).coerceAtLeast(0L) / 1_000_000_000f
        val target = CityEventHeroStillFrame(fanned, spotlight, liked)
        if (reduced) target.spot.fill(0f)
        val result = animateInteraction(target, elapsed, now, reduced)
        result.day = if (timeOfDay < 1f) timeOfDay else 1f
        result.night = if (timeOfDay > 1f) timeOfDay - 1f else 0f
        result.camX = cameraX
        result.camY = cameraY
        result.mist = transitionFrom.mist
        result.drift = transitionFrom.drift

        if (showStartsNanos >= 0L) {
            val showTime = (now - showStartsNanos - showDelayNanos) / 1_000_000_000f
            result.show = if (showTime < 0f) -1f else minOf(showTime, CityEventHeroShowEnd)
            if (showTime >= 0f) applyShowCamera(result, showTime)
        }
        val reducedStillVisible = reduced && (reducedStill || (reducedStillNanos >= 0L && reducedStillTo == 0f))
        if (reducedStillVisible) {
            result.show = CityEventHeroLedStill
            result.night = 1f
            result.day = 1f
            result.camX = 0f
            result.camY = 0f
            result.camZ = 0f
        }
        if (reducedStillNanos >= 0L) {
            val fade = CityEventHeroEaseInOut(CityEventHeroSegment((now - reducedStillNanos) / 1_000_000_000f, 0f, .4f))
            result.still = reducedStillFrom + (reducedStillTo - reducedStillFrom) * fade
            if (fade >= 1f && !reducedStill) {
                result.show = -1f
                result.day = 0f
                result.night = 0f
                timeOfDay = 0f
                result.still = 1f
            }
            if (fade >= 1f) reducedStillNanos = -1L
        }
        result.title = titleAt(now, reduced)
        return result
    }

    private fun updateTimeOfDay(now: Long) {
        if (timeOfDayStart < 0L) return
        val elapsed = (now - timeOfDayStart).coerceAtLeast(0L) / 1_000_000_000f
        if (timeOfDaySpring) {
            timeOfDay = CityEventHeroSpringValue(
                from = timeOfDayFrom,
                to = timeOfDayTo,
                velocity = timeOfDaySpringVelocity,
                elapsed = elapsed,
                spring = CityEventHeroSpring(timeOfDayDuration, 1f),
            ).coerceIn(0f, 2f)
        } else {
            val segment = CityEventHeroSegment(elapsed, 0f, timeOfDayDuration)
            val progress = if (timeOfDayNightTap) CityEventHeroEaseNightTap(segment) else CityEventHeroEaseInOut(segment)
            timeOfDay = timeOfDayFrom + (timeOfDayTo - timeOfDayFrom) * progress
        }
        if (elapsed >= timeOfDayDuration) {
            timeOfDay = timeOfDayTo
            timeOfDayStart = -1L
        }
    }

    private fun updateCamera(now: Long) {
        if (cameraSpringNanos < 0L) return
        val elapsed = (now - cameraSpringNanos).coerceAtLeast(0L) / 1_000_000_000f
        cameraX = CityEventHeroSpringValue(cameraFromX, 0f, cameraVelocityX, elapsed, CityEventHeroCameraSpring)
        cameraY = CityEventHeroSpringValue(cameraFromY, 0f, cameraVelocityY, elapsed, CityEventHeroCameraSpring)
        if (elapsed >= CityEventHeroCameraSpring.duration) {
            cameraX = 0f
            cameraY = 0f
            cameraSpringNanos = -1L
        }
    }

    private fun applyShowCamera(frame: CityEventHeroFrame, showTime: Float) {
        frame.camX = showCameraFromX * (1f - CityEventHeroEaseShowCamera(CityEventHeroSegment(showTime, 0f, .6f)))
        frame.camZ = if (showTime < 2.55f) {
            CityEventHeroEaseShowCamera(CityEventHeroSegment(showTime, 0f, 2.55f))
        } else if (showTime < CityEventHeroShowEnd) {
            1f
        } else {
            1f - CityEventHeroEaseShowCamera(CityEventHeroSegment(showTime, CityEventHeroShowEnd, CityEventHeroShowEnd + .9f))
        }
        frame.camY = if (showTime < CityEventHeroLed.avatarIn - .05f) {
            showCameraFromY
        } else if (showTime < CityEventHeroLed.climbEnd) {
            showCameraFromY + (1f - showCameraFromY) * CityEventHeroEaseShowCamera(
                CityEventHeroSegment(showTime, CityEventHeroLed.avatarIn - .05f, CityEventHeroLed.climbEnd),
            )
        } else if (showTime < CityEventHeroShowEnd) {
            1f
        } else {
            1f - CityEventHeroEaseShowCamera(CityEventHeroSegment(showTime, CityEventHeroShowEnd, CityEventHeroShowEnd + .9f))
        }
    }

    private fun titleAt(now: Long, reduced: Boolean): Float {
        if (reduced || titleStartedNanos < 0L) return 0f
        val elapsed = (now - titleStartedNanos).coerceAtLeast(0L)
        if (titleHoldNanos == Long.MAX_VALUE) return CityEventHeroEaseOut((elapsed / 160_000_000f).coerceIn(0f, 1f))
        if (elapsed < 160_000_000L) return CityEventHeroEaseOut(elapsed / 160_000_000f)
        if (elapsed < 160_000_000L + titleHoldNanos) return 1f
        val fade = (elapsed - 160_000_000L - titleHoldNanos) / (CityEventHeroTitle.fade * 1_000_000_000f)
        if (fade >= 1f) {
            titleStartedNanos = -1L
            return 0f
        }
        return 1f - CityEventHeroEaseInOut(fade)
    }

    private fun animateInteraction(target: CityEventHeroFrame, elapsed: Float, now: Long, reduced: Boolean): CityEventHeroFrame {
        val result = target.deepCopy()
        fun progress(delay: Float, spring: CityEventHeroSpring): Float = if (reduced) {
            CityEventHeroSegment(elapsed, delay, delay + .2f)
        } else {
            CityEventHeroSpringAt(elapsed - delay, spring)
        }
        for (slot in result.fan.indices) {
            val rank = abs(slot - 3)
            val movingOut = target.fan[slot] > transitionFrom.fan[slot]
            val delay = if (movingOut) (3 - rank) * .045f else rank * .045f
            val fanProgress = progress(delay, CityEventHeroFanSpring)
            result.fan[slot] = if (reduced && fanned != transitionFanned) {
                if (elapsed < .1f) transitionFrom.fan[slot] else target.fan[slot]
            } else {
                transitionFrom.fan[slot] + (target.fan[slot] - transitionFrom.fan[slot]) * fanProgress
            }
            val spotProgress = progress(0f, CityEventHeroSpotSpring)
            result.spot[slot] = transitionFrom.spot[slot] + (target.spot[slot] - transitionFrom.spot[slot]) * spotProgress
        }
        val targetName = if (spotlight >= 0) CityEventHeroSlots[spotlight].img else 0
        result.nameFrom = transitionName
        result.nameTo = targetName
        result.nameProgress = if (targetName == transitionName) 0f else if (reduced) CityEventHeroSegment(elapsed, 0f, .2f) else CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, 0f, .32f))
        if (liked != transitionLiked) {
            if (liked) {
                if (reduced) {
                    result.like = CityEventHeroSegment(elapsed, 0f, .2f)
                    result.count = result.like
                    result.pop = 1f
                } else {
                    result.like = CityEventHeroEaseOut(CityEventHeroSegment(elapsed, .07f, .19f))
                    result.pop = if (elapsed < .07f) 1f - .14f * CityEventHeroEaseOut(elapsed / .07f) else .86f + .14f * CityEventHeroSpringAt(elapsed - .07f, CityEventHeroPopSpring)
                    result.ring = CityEventHeroEaseOut(CityEventHeroSegment(elapsed, .07f, .57f))
                    result.count = CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, .07f, .37f))
                }
            } else {
                val likeProgress = CityEventHeroEaseOut(CityEventHeroSegment(elapsed, 0f, .2f))
                val countDuration = if (reduced) .2f else .3f
                val countProgress = CityEventHeroEaseInOut(CityEventHeroSegment(elapsed, 0f, countDuration))
                result.like = transitionFrom.like * (1f - likeProgress)
                result.count = transitionFrom.count * (1f - countProgress)
            }
        }
        if (pressedView) {
            result.press = CityEventHeroEaseOut(CityEventHeroSegment(elapsed, 0f, .12f))
        } else if (viewReleasedNanos > 0L) {
            val release = (now - viewReleasedNanos).coerceAtLeast(0L) / 1_000_000_000f
            result.press = transitionFrom.press * (1f - CityEventHeroSpringAt(release, CityEventHeroUiSpring))
            if (!reduced && viewCommitted) {
                result.chevron = if (release < .12f) CityEventHeroEaseOut(release / .12f) else 1f - CityEventHeroSpringAt(release - .12f, CityEventHeroNudgeSpring)
                result.sweep = CityEventHeroEaseInOut(CityEventHeroSegment(release, 0f, .5f))
            }
        }
        result.guestOpacity = if (reduced && fanned != transitionFanned) {
            if (elapsed < .1f) 1f - elapsed / .1f else CityEventHeroSegment(elapsed, .1f, .2f)
        } else {
            1f
        }
        if (elapsed >= if (reduced) .4f else 1f) transitionPending = false
        return result
    }
}

internal fun CityEventHeroStillFrame(fanned: Boolean, spotlight: Int, liked: Boolean): CityEventHeroFrame {
    val frame = CityEventHeroFrameAt(0f)
    frame.fan.fill(if (fanned) 1f else 0f)
    if (spotlight >= 0) frame.spot[spotlight] = 1f
    val image = if (spotlight >= 0) CityEventHeroSlots[spotlight].img else 0
    frame.nameFrom = image
    frame.nameTo = image
    frame.like = if (liked) 1f else 0f
    frame.count = if (liked) 1f else 0f
    return frame
}

private fun CityEventHeroFrame.deepCopy(): CityEventHeroFrame = copy(fan = fan.copyOf(), spot = spot.copyOf())

