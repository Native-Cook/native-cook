package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** The React Native reference's exact loop period, in seconds. */
const val LOOP = 12.0

internal const val PullCordThemeStageWidth = 402f
internal const val PullCordThemeBalls = 25
internal const val PullCordThemePitch = 4.6f
internal const val PullCordThemeBallRadius = 1.75f
internal const val PullCordThemePullWidth = 10f
internal const val PullCordThemePullHeight = 22f
internal const val PullCordThemeHitSize = 56f

internal const val PullCordThemeLampX = 312f
internal const val PullCordThemeCapTop = 60f
internal const val PullCordThemeCord = 640f
internal const val PullCordThemeBulbY = 70f
internal const val PullCordThemeBulbRadius = 16f
internal const val PullCordThemeDepth = 120f
internal const val PullCordThemeSwitchX = -30f
internal const val PullCordThemeSwitchTop = 60f
internal const val PullCordThemeSwitchHeight = 26f

private const val Step = 1f / 120f
private const val MaxSteps = 30
private const val Gravity = 4000f
private const val RopeDamping = 0.995f
private const val RopePasses = 14
private const val PullWeight = 0.3f
private const val Bend = 1.7f
private const val Slack = 0.86f
private const val Lift = 0.6f
private const val ReleaseSpeed = 1500f
private const val SnapRest = 1.5f

private const val SwitchTravel = 9f
private const val SwitchDetent = 7f
private const val SwitchGive = 2.5f
private const val SwitchRearm = 2f
private const val SwitchStretch = 3f
private const val SwitchSpringDuration = 0.4f
private const val SwitchSpringDamping = 0.8f
private const val SwitchKick = 1.6f
private const val SwitchFlickSpeed = 900f
private const val SwitchFlickMin = 3f

private const val SwingPeriod = 2.4f
private const val SwingDamping = 0.3f
private const val SwingCouple = 0.016f
private const val SwingKick = 0.02f
private const val SwingMaxThrow = 0.05f
private const val SwingMaxAngle = 0.06f
private const val SwingGrabRadius = 80f

private const val FilamentWarmUp = 0.06f
private const val FilamentCoolDown = 0.12f
private const val FilamentReduced = 0.04f
private const val FilamentFlicker = 0.3f
private const val FilamentFlickerDepth = 0.07f
private const val AdaptTime = 0.4f

private const val FrontOnDelay = 0.08f
private const val FrontOn = 1.2f
private const val FrontOff = 0.45f
private const val FrontReduced = 0.2f
internal const val PullCordThemeFrontBand = 0.24f

// The port brief explicitly asks for autoplay to resume two seconds after touch.
private const val ResumeSeconds = 2f
private const val NightRestAt = 5f

private val EaseOut = floatArrayOf(0.23f, 1f, 0.32f, 1f)
private val EaseInOut = floatArrayOf(0.77f, 0f, 0.175f, 1f)

private data class PullCordThemeTrack(
    val start: Float,
    val end: Float,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val down: Boolean,
    val up: Boolean,
)

private val DemoTrack = arrayOf(
    PullCordThemeTrack(1f, 1.55f, 0f, 0f, -16f, 2f, true, false),
    PullCordThemeTrack(1.55f, 1.95f, -16f, 2f, -11f, 14f, false, false),
    PullCordThemeTrack(1.95f, 2.1f, -11f, 14f, -11f, 14f, false, true),
    PullCordThemeTrack(7f, 7.5f, 0f, 0f, 14f, 3f, true, false),
    PullCordThemeTrack(7.5f, 7.85f, 14f, 3f, 10f, 14f, false, false),
    PullCordThemeTrack(7.85f, 8f, 10f, 14f, 10f, 14f, false, true),
)

private val TapTrack = arrayOf(
    PullCordThemeTrack(0f, 0.22f, 0f, 0f, 0f, 14f, true, false),
    PullCordThemeTrack(0.22f, 0.32f, 0f, 14f, 0f, 14f, false, true),
)

internal data class PullCordThemePoint(val x: Float, val y: Float)

internal data class PullCordThemeGeometry(
    val width: Float,
    val height: Float,
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
    val arm: Float,
    val segment: Float,
)

internal fun pullCordThemeGeometry(width: Float, height: Float): PullCordThemeGeometry {
    val scale = width / PullCordThemeStageWidth
    val arm = PullCordThemeCord * scale
    return PullCordThemeGeometry(
        width = width,
        height = height,
        scale = scale,
        pivotX = PullCordThemeLampX * scale,
        pivotY = PullCordThemeCapTop * scale - arm,
        arm = arm,
        segment = PullCordThemePitch * scale,
    )
}

internal fun pullCordThemeLampPoint(
    geometry: PullCordThemeGeometry,
    angle: Float,
    localX: Float,
    localY: Float,
): PullCordThemePoint {
    val c = cos(angle)
    val s = sin(angle)
    val y = geometry.arm + localY * geometry.scale
    val x = localX * geometry.scale
    return PullCordThemePoint(
        geometry.pivotX + x * c + y * s,
        geometry.pivotY - x * s + y * c,
    )
}

private fun switchFoot(geometry: PullCordThemeGeometry, angle: Float) = pullCordThemeLampPoint(
    geometry,
    angle,
    PullCordThemeSwitchX,
    PullCordThemeSwitchTop + PullCordThemeSwitchHeight,
)

internal sealed interface PullCordThemeInput {
    data class Down(val x: Float, val y: Float, val scripted: Boolean = false) : PullCordThemeInput
    data class Move(
        val x: Float,
        val y: Float,
        val velocityX: Float,
        val velocityY: Float,
        val scripted: Boolean = false,
    ) : PullCordThemeInput
    data class Up(
        val velocityX: Float,
        val velocityY: Float,
        val cancelled: Boolean = false,
        val scripted: Boolean = false,
    ) : PullCordThemeInput
    data class Tap(val x: Float, val y: Float) : PullCordThemeInput
    data object Toggle : PullCordThemeInput
}

internal class PullCordThemeEngine(
    val geometry: PullCordThemeGeometry,
    val reducedMotion: Boolean,
    private val autoPlay: Boolean,
) {
    val x = FloatArray(PullCordThemeBalls)
    val y = FloatArray(PullCordThemeBalls)
    private val oldX = FloatArray(PullCordThemeBalls)
    private val oldY = FloatArray(PullCordThemeBalls)
    val restX = FloatArray(PullCordThemeBalls)
    val restY = FloatArray(PullCordThemeBalls)

    var switchTravel = 0f
        private set
    private var switchVelocity = 0f
    private var armed = true
    private var grab = 0
    private var grabIndex = PullCordThemeBalls - 1
    private var scripted = false
    private var fingerX = 0f
    private var fingerY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var lean = 0f
    private var currentPitch = geometry.segment

    var light = 1f
        private set
    var heat = 1f
        private set
    var adaptation = 1f
        private set
    private var onTime = 99f
    var front = 1f
        private set
    private var frontFrom = 1f
    private var frontTo = 1f
    private var frontTime = 1f
    private var frontDuration = 1f
    private var frontTurningOn = true

    var angle = 0f
        private set
    private var angularVelocity = 0f
    private var grabAngle = 0f
    var clock = 0f
        private set

    private var demoOn = autoPlay && !reducedMotion
    private var demoTime = 0f
    private var quiet = 0f
    private var tapOn = false
    private var tapTime = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var accumulator = 0f
    private var clickPending = false

    init {
        val foot = switchFoot(geometry, 0f)
        for (index in 0 until PullCordThemeBalls) {
            x[index] = foot.x
            y[index] = foot.y + geometry.segment * index
            oldX[index] = x[index]
            oldY[index] = y[index]
        }
        repeat(900) { chainStep() }
        x.copyInto(restX)
        y.copyInto(restY)
        x.copyInto(oldX)
        y.copyInto(oldY)
    }

    fun takeClick(): Boolean {
        val result = clickPending
        clickPending = false
        return result
    }

    fun pullCentre(): PullCordThemePoint {
        val index = PullCordThemeBalls - 1
        return PullCordThemePoint(x[index], y[index] + PullCordThemePullHeight * geometry.scale / 2f)
    }

    fun restPullCentre(): PullCordThemePoint {
        val index = PullCordThemeBalls - 1
        return PullCordThemePoint(restX[index], restY[index] + PullCordThemePullHeight * geometry.scale / 2f)
    }

    fun filamentFlicker(): Float {
        if (reducedMotion || onTime >= FilamentFlicker) return 1f
        val noise = 0.5f + 0.5f * sin(onTime * 97f) * sin(onTime * 41f + 1.3f)
        return 1f - FilamentFlickerDepth * noise * (1f - onTime / FilamentFlicker)
    }

    fun input(event: PullCordThemeInput) {
        when (event) {
            PullCordThemeInput.Toggle -> {
                demoOn = false
                quiet = 0f
                startTap()
            }
            is PullCordThemeInput.Tap -> {
                val centre = pullCentre()
                val hit = PullCordThemeHitSize * geometry.scale / 2f
                if (abs(event.x - centre.x) < hit && abs(event.y - centre.y) < hit) {
                    demoOn = false
                    quiet = 0f
                    startTap()
                }
            }
            is PullCordThemeInput.Down -> down(event)
            is PullCordThemeInput.Move -> move(event)
            is PullCordThemeInput.Up -> up(event)
        }
    }

    private fun down(event: PullCordThemeInput.Down) {
        scripted = event.scripted
        if (!scripted) {
            demoOn = false
            tapOn = false
            quiet = 0f
        }
        val last = PullCordThemeBalls - 1
        val centre = pullCentre()
        val hit = PullCordThemeHitSize * geometry.scale / 2f
        val bulb = pullCordThemeLampPoint(geometry, angle, 0f, PullCordThemeBulbY)
        grab = 0
        if (abs(event.x - centre.x) < hit && abs(event.y - centre.y) < hit) {
            grab = 1
            grabIndex = last
        } else {
            var best = hit * 0.8f
            for (index in 2 until last) {
                val distance = hypot(event.x - x[index], event.y - y[index])
                if (distance < best) {
                    best = distance
                    grab = 1
                    grabIndex = index
                }
            }
        }
        if (grab == 1) {
            grabDx = x[grabIndex] - event.x
            grabDy = y[grabIndex] - event.y
        } else if (hypot(event.x - bulb.x, event.y - bulb.y) < SwingGrabRadius * geometry.scale) {
            grab = 2
            grabDx = 0f
            grabDy = 0f
            grabAngle = kotlin.math.atan2(event.x - geometry.pivotX, event.y - geometry.pivotY) - angle
        }
        fingerX = event.x + grabDx
        fingerY = event.y + grabDy
    }

    private fun move(event: PullCordThemeInput.Move) {
        if (scripted && !event.scripted) return
        if (grab == 0) return
        fingerX = event.x + grabDx
        fingerY = event.y + grabDy
    }

    private fun up(event: PullCordThemeInput.Up) {
        if (scripted && !event.scripted) return
        if (grab == 1) {
            val index = grabIndex
            val limit = ReleaseSpeed * geometry.scale
            val vx = event.velocityX.coerceIn(-limit, limit)
            val vy = event.velocityY.coerceIn(-limit, limit)
            grab = 0
            oldX[index] = x[index] - vx * Step
            oldY[index] = y[index] - vy * Step
            if (armed && !event.cancelled && vy > SwitchFlickSpeed * geometry.scale && switchTravel > SwitchFlickMin * geometry.scale) {
                click()
            }
        } else if (grab == 2) {
            angularVelocity = (event.velocityX / (geometry.arm + PullCordThemeBulbY * geometry.scale))
                .coerceIn(-SwingMaxThrow, SwingMaxThrow)
            grab = 0
        }
        scripted = false
    }

    fun step(frameSeconds: Float) {
        accumulator = min(accumulator + frameSeconds, Step * MaxSteps)
        while (accumulator >= Step) {
            fixedStep()
            accumulator -= Step
        }
    }

    private fun fixedStep() {
        if (demoOn) {
            val before = demoTime
            var after = before + Step
            runTrack(DemoTrack, before, after, DemoTrack[0].fromX, DemoTrack[0].fromY)
            if (after >= LOOP.toFloat()) {
                after -= LOOP.toFloat()
                snapRestIfStill()
            }
            demoTime = after
        } else if (tapOn) {
            val before = tapTime
            val after = before + Step
            runTrack(TapTrack, before, after, tapX, tapY)
            tapTime = after
            if (after > TapTrack.last().end + Step) tapOn = false
        } else if (autoPlay && !reducedMotion && grab == 0) {
            quiet += Step
            if (quiet >= ResumeSeconds) {
                demoOn = true
                demoTime = if (light == 0f) NightRestAt else 0f
            }
        }

        if (grab == 1) {
            val previous = switchTravel
            switchTravel = follow()
            switchVelocity = (switchTravel - previous) / Step
        } else {
            lean = 0f
            currentPitch = geometry.segment
            val frequency = (2f * PI.toFloat()) / SwitchSpringDuration
            switchVelocity += (
                -frequency * frequency * switchTravel -
                    2f * SwitchSpringDamping * frequency * switchVelocity
                ) * Step
            switchTravel += switchVelocity * Step
            if (switchTravel < 0f || reducedMotion) {
                switchTravel = 0f
                switchVelocity = 0f
            }
        }
        if (switchTravel < SwitchRearm * geometry.scale) armed = true
        if (armed && switchTravel >= SwitchDetent * geometry.scale) click()

        if (reducedMotion && grab != 1) hangStraight() else chainStep()

        val heatTime = when {
            reducedMotion -> FilamentReduced
            light == 1f -> FilamentWarmUp
            else -> FilamentCoolDown
        }
        heat += (light - heat) * (1f - exp(-Step / heatTime))
        adaptation += (light - adaptation) * (1f - exp(-Step / if (reducedMotion) FilamentReduced else AdaptTime))
        onTime += Step
        if (frontTime < frontDuration) {
            frontTime = min(frontDuration, frontTime + Step)
            val progress = (frontTime / frontDuration).coerceIn(0f, 1f)
            val eased = bezier(if (frontTurningOn || reducedMotion) EaseOut else EaseInOut, progress)
            front = frontFrom + (frontTo - frontFrom) * eased
        }

        if (reducedMotion) {
            angle = 0f
            angularVelocity = 0f
        } else if (grab == 2) {
            val wanted = kotlin.math.atan2(fingerX - geometry.pivotX, fingerY - geometry.pivotY) - grabAngle
            val limited = if (abs(wanted) <= SwingMaxAngle) wanted else {
                sign(wanted) * (SwingMaxAngle + rubber(abs(wanted) - SwingMaxAngle, SwingMaxAngle * 0.4f))
            }
            angularVelocity = (limited - angle) / Step
            angle = limited
        } else {
            val frequency = (2f * PI.toFloat()) / SwingPeriod
            val rest = SwingCouple * lean
            angularVelocity += (
                -frequency * frequency * (angle - rest) - 2f * SwingDamping * frequency * angularVelocity
                ) * Step
            angle += angularVelocity * Step
        }
        clock += Step
    }

    private fun startTap() {
        val pull = pullCentre()
        val rest = restPullCentre()
        tapOn = true
        tapTime = 0f
        tapX = (pull.x - rest.x) / geometry.scale
        tapY = (pull.y - rest.y) / geometry.scale
    }

    private fun startFront(to: Float) {
        frontFrom = front
        frontTo = to
        frontTurningOn = to == 1f
        val span = abs(to - front)
        if (reducedMotion) {
            frontDuration = max(0.05f, FrontReduced * span)
            frontTime = 0f
        } else {
            frontDuration = max(0.05f, (if (to == 1f) FrontOn else FrontOff) * span)
            frontTime = if (to == 1f) -FrontOnDelay else 0f
        }
    }

    private fun click() {
        light = 1f - light
        armed = false
        if (light == 1f) onTime = 0f
        startFront(light)
        if (!reducedMotion) {
            angularVelocity += SwingKick
            val last = PullCordThemeBalls - 1
            for (index in 1..last) {
                if (inverseMass(index) == 0f) continue
                val kick = sin(PI.toFloat() * index / last) * SwitchKick * geometry.scale
                oldX[index] -= kick
                oldY[index] += kick * 0.3f
            }
        }
        if (!scripted || tapOn) clickPending = true
    }

    private fun runTrack(
        track: Array<PullCordThemeTrack>,
        before: Float,
        after: Float,
        firstX: Float,
        firstY: Float,
    ) {
        val rest = restPullCentre()
        track.forEachIndexed { index, segment ->
            val fromX = if (index == 0) firstX else segment.fromX
            val fromY = if (index == 0) firstY else segment.fromY
            if (segment.down && before <= segment.start && segment.start < after) {
                input(PullCordThemeInput.Down(
                    rest.x + fromX * geometry.scale,
                    rest.y + fromY * geometry.scale,
                    scripted = true,
                ))
            }
            if (after > segment.start && before < segment.end) {
                val progress = easeInOutSine(
                    ((min(after, segment.end) - segment.start) / (segment.end - segment.start)).coerceIn(0f, 1f),
                )
                val px = fromX + (segment.toX - fromX) * progress
                val py = fromY + (segment.toY - fromY) * progress
                if (scripted && grab != 0) {
                    input(PullCordThemeInput.Move(
                        rest.x + px * geometry.scale,
                        rest.y + py * geometry.scale,
                        0f,
                        0f,
                        scripted = true,
                    ))
                }
            }
            if (segment.up && before < segment.end && segment.end <= after && scripted) {
                input(PullCordThemeInput.Up(0f, 0f, scripted = true))
            }
        }
    }

    private fun follow(): Float {
        val foot = switchFoot(geometry, angle)
        val reach = grabIndex * geometry.segment
        val dx = fingerX - foot.x
        var dy = fingerY - foot.y
        val top = reach * (1f - Lift)
        if (dy < top) dy = top - rubber(top - dy, 16f * geometry.scale)
        val distance = hypot(dx, dy).coerceAtLeast(1e-6f)
        val over = distance - reach
        var hold = 0f
        var give = 0f
        if (over > 0f) {
            val travel = SwitchTravel * geometry.scale
            hold = min(over, travel)
            if (over > travel) give = rubber(over - travel, SwitchStretch * geometry.scale)
            if (!armed) hold = max(0f, hold - SwitchGive * geometry.scale)
            lean = dx / distance
        } else {
            lean = 0f
        }
        val radius = if (over > 0f) reach + hold + give else distance
        currentPitch = geometry.segment + give / max(1, grabIndex)
        oldX[grabIndex] = x[grabIndex]
        oldY[grabIndex] = y[grabIndex]
        x[grabIndex] = foot.x + dx / distance * radius
        y[grabIndex] = foot.y + dy / distance * radius
        return hold
    }

    private fun chainStep() {
        val foot = switchFoot(geometry, angle)
        val anchorX = foot.x + sin(angle) * switchTravel
        val anchorY = foot.y + cos(angle) * switchTravel
        oldX[0] = x[0]
        oldY[0] = y[0]
        x[0] = anchorX
        y[0] = anchorY
        val gravityStep = Gravity * geometry.scale * Step * Step
        for (index in 1 until PullCordThemeBalls) {
            if (inverseMass(index) == 0f) continue
            val velocityX = (x[index] - oldX[index]) * RopeDamping
            val velocityY = (y[index] - oldY[index]) * RopeDamping
            oldX[index] = x[index]
            oldY[index] = y[index]
            x[index] += velocityX
            y[index] += velocityY + gravityStep
        }
        solveChain()
    }

    private fun solveChain() {
        val short = geometry.segment * Slack
        val bend = geometry.segment * Bend
        repeat(RopePasses) {
            for (index in 0 until PullCordThemeBalls - 1) {
                val next = index + 1
                val dx = x[next] - x[index]
                val dy = y[next] - y[index]
                val distance = hypot(dx, dy).coerceAtLeast(1e-6f)
                val wanted = when {
                    distance > currentPitch -> currentPitch
                    distance < short -> short
                    else -> continue
                }
                val wi = inverseMass(index)
                val wj = inverseMass(next)
                val sum = wi + wj
                if (sum == 0f) continue
                val correction = (distance - wanted) / distance / sum
                x[index] += dx * correction * wi
                y[index] += dy * correction * wi
                x[next] -= dx * correction * wj
                y[next] -= dy * correction * wj
            }
            for (index in 0 until PullCordThemeBalls - 2) {
                val next = index + 2
                val dx = x[next] - x[index]
                val dy = y[next] - y[index]
                val distance = hypot(dx, dy).coerceAtLeast(1e-6f)
                if (distance >= bend) continue
                val wi = inverseMass(index)
                val wj = inverseMass(next)
                val sum = wi + wj
                if (sum == 0f) continue
                val correction = (distance - bend) / distance / sum * 0.5f
                x[index] += dx * correction * wi
                y[index] += dy * correction * wi
                x[next] -= dx * correction * wj
                y[next] -= dy * correction * wj
            }
            tether(0)
            if (grab == 1) tether(grabIndex)
        }
    }

    private fun tether(from: Int) {
        val fromX = x[from]
        val fromY = y[from]
        for (index in 0 until PullCordThemeBalls) {
            if (index == from || inverseMass(index) == 0f) continue
            val limit = abs(index - from) * currentPitch
            val dx = x[index] - fromX
            val dy = y[index] - fromY
            val distance = hypot(dx, dy)
            if (distance <= limit) continue
            x[index] = fromX + dx / distance * limit
            y[index] = fromY + dy / distance * limit
        }
    }

    private fun inverseMass(index: Int): Float = when {
        index == 0 -> 0f
        grab == 1 && index == grabIndex -> 0f
        index == PullCordThemeBalls - 1 -> PullWeight
        else -> 1f
    }

    private fun hangStraight() {
        val foot = switchFoot(geometry, 0f)
        for (index in 0 until PullCordThemeBalls) {
            x[index] = foot.x
            y[index] = foot.y + switchTravel + geometry.segment * index
            oldX[index] = x[index]
            oldY[index] = y[index]
        }
    }

    private fun snapRestIfStill() {
        var worst = abs(switchTravel)
        for (index in 0 until PullCordThemeBalls) {
            worst = max(worst, abs(x[index] - restX[index]))
            worst = max(worst, abs(y[index] - restY[index]))
        }
        if (worst > SnapRest * geometry.scale || grab != 0) return
        restX.copyInto(x)
        restY.copyInto(y)
        x.copyInto(oldX)
        y.copyInto(oldY)
        switchTravel = 0f
        switchVelocity = 0f
        angle = 0f
        angularVelocity = 0f
        lean = 0f
        heat = light
        adaptation = light
        front = light
        frontTime = frontDuration
    }
}

private fun rubber(over: Float, limit: Float) = limit * (1f - exp(-over / limit))

private fun easeInOutSine(value: Float) = (1f - cos(PI.toFloat() * value)) / 2f

private fun bezier(curve: FloatArray, value: Float): Float {
    if (value <= 0f) return 0f
    if (value >= 1f) return 1f
    val cx = 3f * curve[0]
    val bx = 3f * (curve[2] - curve[0]) - cx
    val ax = 1f - cx - bx
    val cy = 3f * curve[1]
    val by = 3f * (curve[3] - curve[1]) - cy
    val ay = 1f - cy - by
    var t = value
    repeat(8) {
        val x = ((ax * t + bx) * t + cx) * t - value
        val derivative = (3f * ax * t + 2f * bx) * t + cx
        if (abs(x) < 1e-5f || abs(derivative) < 1e-6f) return@repeat
        t = (t - x / derivative).coerceIn(0f, 1f)
    }
    return ((ay * t + by) * t + cy) * t
}

internal fun pullCordThemeBrightness(heat: Float) = heat.coerceIn(0f, 1f).pow(4)

internal fun pullCordThemeLampColor(heat: Float): FloatArray {
    val stops = floatArrayOf(0f, 0.3f, 0.5f, 0.72f, 1f)
    val colours = arrayOf(
        floatArrayOf(0x3A / 255f, 0x08 / 255f, 0f),
        floatArrayOf(0xB3 / 255f, 0x22 / 255f, 0x04 / 255f),
        floatArrayOf(0xF2 / 255f, 0x60 / 255f, 0x0F / 255f),
        floatArrayOf(1f, 0xA9 / 255f, 0x4F / 255f),
        floatArrayOf(1f, 0xE4 / 255f, 0xBC / 255f),
    )
    val h = heat.coerceIn(0f, 1f)
    for (index in 0 until stops.lastIndex) {
        if (h <= stops[index + 1]) {
            val t = (h - stops[index]) / (stops[index + 1] - stops[index])
            return FloatArray(3) { channel ->
                colours[index][channel] + (colours[index + 1][channel] - colours[index][channel]) * t
            }
        }
    }
    return colours.last()
}
