package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry and the analytic 9-second demo (motion.ts): pit stop, sticker peel, quick-card rearrangement. */
object PaddockWidgetBentoMotion {
    const val LOOP = 9.0
    const val loopDuration = LOOP

    /** Tops of the three quick-card slots on the 402-point screen. */
    val slotY = listOf(54.5, 128.5, 202.5)
    const val longPressDuration = 0.320
    const val targetHysteresis = 8.0
    const val outsideCancelDistance = 44.0
    val defaultOrder = listOf(
        PaddockWidgetBentoCardID.Laps,
        PaddockWidgetBentoCardID.Reflex,
        PaddockWidgetBentoCardID.HomeCards,
    )
    const val reflowStiffness = 360.0
    const val reflowDamping = 28.0
    const val reflowMass = 0.9
    const val settleStiffness = 420.0
    const val settleDamping = 30.0
    const val settleMass = 0.8
    const val springSettleDuration = 0.60
    const val quietReflowDuration = 0.12
    const val liftScale = 1.025
    const val liftShadow = 14.0
    const val restShadow = 4.0
    const val maxRotation = 0.7
    const val rotationPerPoint = 0.012
    const val restartDelay = 2.0

    object Timeline {
        const val editStart = 4.75
        const val editEnd = 5.0
        const val reflowEnd = 5.8
        const val settleEnd = 6.1
        const val holdEnd = 6.3
        const val returnEnd = 7.1
        const val restEnd = 7.45
    }

    /** Pit-stop tiles in reading order; the quick cards keep their stable ids 0-2. */
    object Tile {
        const val violet = 0
        const val orange = 1
        const val yellow = 2
        const val pitcast = 3
        const val stats = 4
        const val glossary = 5
        const val education = 6
        const val hero = 7
        const val guide = 8
        const val banner = 9
        const val count = 10
    }

    object Pit {
        const val loopStart = 0.55
        const val brace = 0.14
        const val braceScale = 0.965
        const val exit = 0.24
        const val exitPower = 2.2
        const val gap = 0.1
        const val omega = 17.0
        const val zeta = 0.85
        const val launch = 0.9
        const val settle = 0.62
        const val margin = 46.0
        val width = doubleArrayOf(186.0, 186.0, 186.0, 186.0, 382.0, 186.0, 186.0, 382.0, 382.0, 382.0)
        val delay = doubleArrayOf(0.0, 0.07, 0.14, 0.03, 0.1, 0.19, 0.21, 0.27, 0.33, 0.39)
        val side = doubleArrayOf(-1.0, -1.0, -1.0, 1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0)
        val rise = doubleArrayOf(-26.0, 0.0, 22.0, -34.0, 10.0, 16.0, -16.0, 24.0, -20.0, 40.0)
        val tilt = doubleArrayOf(-6.0, -5.0, -6.0, 5.0, 3.0, -5.0, 5.0, -3.0, 3.0, -2.5)
        const val stretchPerSpeed = 1.0 / 22000
        const val maxStretch = 0.18
        const val squashPerAccel = 1.0 / 700000
        const val maxSquash = 0.08
        const val ghosts = 4.0
        const val ghostStep = 0.016
        const val ghostAlpha = 0.55
        const val ghostSpeed = 2400.0
        const val arrive = brace + exit + gap
        val duration = arrive + delay.max() + settle
        val windowStart = loopStart
        val windowEnd = loopStart + duration
    }

    object Peel {
        const val start = 2.45
        const val tease = 2.62
        const val full = 3.22
        const val hold = 3.55
        const val snap = 3.8
        const val end = 4.35
        const val cornerX = 324.0
        const val cornerY = 124.0
        const val angle = -153.4
        const val teaseDistance = 26.0
        const val maxDistance = 236.0
        const val touchMaxDistance = 290.0
        const val flutter = 7.0
        const val flutterHz = 2.6
        const val wobble = 4.0
        const val release = 0.24
        const val slapPress = 0.03
        const val slapPressTime = 0.16
        const val shake = 2.2
        const val shakeHz = 16.0
        const val shakeDecay = 0.07
        const val slapEnd = 0.55
        const val lines = 7
        const val lineInner = 10.0
        const val lineReach = 26.0
        const val lineLength = 12.0
        const val lineTime = 0.3
        const val foldShade = 34.0
        const val border = 6.0
        const val shadowBlur = 5.0
        const val shadowOffset = 5.0
        const val hint = 16.0
    }

    object Jiggle {
        const val amplitude = 1.2
        const val hz = 5.6
        const val phase = 2.1
    }

    object Goo {
        const val blur = 6.5
        const val gain = 18.0
        const val bias = 8.0
        const val pad = 28.0
    }

    object Pull {
        const val trigger = 72.0
        const val flick = 900.0
        const val flickShare = 0.5
        const val rubber = 260.0
    }

    /** Time ranges (seconds) of the loop in which the demo needs 60 Hz frames. */
    val activeRanges: List<Pair<Double, Double>> = listOf(
        Pit.windowStart to Pit.windowEnd,
        Peel.start to Peel.end,
        Timeline.editStart to Timeline.restEnd,
    )

    enum class Phase {
        Rest,
        PitStop,
        Peel,
        EditCue,
        Reflow,
        PreviewHold,
        ReturnReflow,
        Settle,
    }

    val validOrders = listOf(
        listOf(PaddockWidgetBentoCardID.Laps, PaddockWidgetBentoCardID.Reflex, PaddockWidgetBentoCardID.HomeCards),
        listOf(PaddockWidgetBentoCardID.Laps, PaddockWidgetBentoCardID.HomeCards, PaddockWidgetBentoCardID.Reflex),
        listOf(PaddockWidgetBentoCardID.Reflex, PaddockWidgetBentoCardID.Laps, PaddockWidgetBentoCardID.HomeCards),
        listOf(PaddockWidgetBentoCardID.Reflex, PaddockWidgetBentoCardID.HomeCards, PaddockWidgetBentoCardID.Laps),
        listOf(PaddockWidgetBentoCardID.HomeCards, PaddockWidgetBentoCardID.Laps, PaddockWidgetBentoCardID.Reflex),
        listOf(PaddockWidgetBentoCardID.HomeCards, PaddockWidgetBentoCardID.Reflex, PaddockWidgetBentoCardID.Laps),
    )

    fun isValid(order: List<PaddockWidgetBentoCardID>): Boolean {
        return order.size == 3 && order.toSet().size == 3 && order.toSet() == defaultOrder.toSet()
    }

    fun clamp(value: Double): Double = min(1.0, max(0.0, value))

    fun smooth(value: Double): Double {
        val t = clamp(value)
        return t * t * (3.0 - 2.0 * t)
    }

    fun easeOut(value: Double): Double {
        val t = clamp(value)
        return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
    }

    fun cubicBezier(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp(value)
        var low = 0.0
        var high = 1.0
        var t = x
        var iteration = 0
        while (iteration < 14) {
            val current = cubicCoordinate(t, x1, x2)
            if (abs(current - x) < 0.00001) break
            if (current < x) low = t else high = t
            t = (low + high) * 0.5
            iteration += 1
        }
        return cubicCoordinate(t, y1, y2)
    }

    fun previewOrder(from: List<PaddockWidgetBentoCardID>): List<PaddockWidgetBentoCardID> {
        if (!isValid(from)) return defaultOrder
        val selected = from.lastOrNull() ?: return defaultOrder
        return listOf(selected) + from.dropLast(1)
    }

    fun sample(elapsed: Double, order: List<PaddockWidgetBentoCardID>): PaddockWidgetBentoFrame {
        val validOrder = if (isValid(order)) order.toList() else defaultOrder
        val t = elapsed % loopDuration
        val selected = validOrder.last()
        val preview = previewOrder(validOrder)
        val initialY = positionsFor(validOrder)
        val previewY = positionsFor(preview)
        val k = Timeline
        var phase = Phase.Rest
        var positions = initialY
        var handles = 0.0
        var selectedScale = 1.0
        var shadow = restShadow
        var rotation = 0.0
        val curve = doubleArrayOf(0.32, 0.72, 0.0, 1.0)

        if (t >= Pit.windowStart && t < Pit.windowEnd) {
            phase = Phase.PitStop
        } else if (t >= Peel.start && t < Peel.end) {
            phase = Phase.Peel
        } else if (t >= k.editStart && t < k.editEnd) {
            phase = Phase.EditCue
            val p = smooth((t - k.editStart) / (k.editEnd - k.editStart))
            handles = p
            selectedScale = 1.0 + (liftScale - 1.0) * p
            shadow = restShadow + (liftShadow - restShadow) * p
            rotation = maxRotation * p
        } else if (t >= k.editEnd && t < k.reflowEnd) {
            phase = Phase.Reflow
            val p = cubicBezier((t - k.editEnd) / (k.reflowEnd - k.editEnd), curve[0], curve[1], curve[2], curve[3])
            positions = interpolate(initialY, previewY, p)
            handles = 1.0
            selectedScale = liftScale
            shadow = liftShadow
            rotation = maxRotation * (1.0 - p)
        } else if (t >= k.reflowEnd && t < k.settleEnd) {
            phase = Phase.Settle
            positions = previewY
            val p = smooth((t - k.reflowEnd) / (k.settleEnd - k.reflowEnd))
            handles = 1.0
            selectedScale = liftScale - (liftScale - 1.0) * p
            shadow = liftShadow - (liftShadow - restShadow) * p
        } else if (t >= k.settleEnd && t < k.holdEnd) {
            phase = Phase.PreviewHold
            positions = previewY
            handles = 1.0
        } else if (t >= k.holdEnd && t < k.returnEnd) {
            phase = Phase.ReturnReflow
            val p = cubicBezier((t - k.holdEnd) / (k.returnEnd - k.holdEnd), curve[0], curve[1], curve[2], curve[3])
            positions = interpolate(previewY, initialY, p)
            handles = 1.0
            selectedScale = liftScale - (liftScale - 1.0) * p
            shadow = liftShadow - (liftShadow - restShadow) * p
        } else if (t >= k.returnEnd && t < k.restEnd) {
            phase = Phase.Settle
            positions = initialY
            val p = smooth((t - k.returnEnd) / (k.restEnd - k.returnEnd))
            handles = 1.0 - p
        }

        val showsPreview = phase == Phase.Reflow || (phase == Phase.Settle && t < k.settleEnd) ||
            phase == Phase.PreviewHold || phase == Phase.ReturnReflow
        val holdsCard = phase == Phase.EditCue || showsPreview
        return PaddockWidgetBentoFrame(
            phase = phase,
            order = if (showsPreview) preview else validOrder,
            cardY = positions,
            cardOpacity = validOrder.associateWith { 1.0 },
            handlesOpacity = handles,
            selectedID = if (holdsCard) selected else null,
            selectedScale = selectedScale,
            shadowRadius = shadow,
            rotationDegrees = rotation,
            isDemo = true,
            loopTime = t,
        )
    }

    fun restFrame(order: List<PaddockWidgetBentoCardID>): PaddockWidgetBentoFrame {
        val validOrder = if (isValid(order)) order.toList() else defaultOrder
        return PaddockWidgetBentoFrame(
            phase = Phase.Rest,
            order = validOrder,
            cardY = positionsFor(validOrder),
            cardOpacity = validOrder.associateWith { 1.0 },
            handlesOpacity = 0.0,
            selectedID = null,
            selectedScale = 1.0,
            shadowRadius = restShadow,
            rotationDegrees = 0.0,
            isDemo = false,
            loopTime = -1.0,
        )
    }

    private fun cubicCoordinate(t: Double, a: Double, b: Double): Double {
        val u = 1.0 - t
        return 3.0 * u * u * t * a + 3.0 * u * t * t * b + t * t * t
    }

    fun positionsFor(order: List<PaddockWidgetBentoCardID>): Map<PaddockWidgetBentoCardID, Double> {
        return order.mapIndexed { index, id -> id to slotY[index] }.toMap()
    }

    private fun interpolate(
        from: Map<PaddockWidgetBentoCardID, Double>,
        to: Map<PaddockWidgetBentoCardID, Double>,
        progress: Double,
    ): Map<PaddockWidgetBentoCardID, Double> {
        return PaddockWidgetBentoCardID.entries.associateWith { id ->
            val start = from[id] ?: slotY[0]
            val end = to[id] ?: start
            start + (end - start) * progress
        }
    }

    /** Rotation of the held card: leans with the finger, at most 0.7 degrees. */
    fun dragRotation(dragY: Double, candidate: Int, pointerOffset: Double, quiet: Boolean): Double {
        if (quiet) return 0.0
        return min(maxRotation, max(-maxRotation, (dragY - (slotY[candidate] + pointerOffset)) * rotationPerPoint))
    }

    // Pit stop

    class TilePose(
        val x: Double = 0.0,
        val y: Double = 0.0,
        val sx: Double = 1.0,
        val sy: Double = 1.0,
        val rotate: Double = 0.0,
        val speed: Double = 0.0,
    )

    private val restPose = TilePose()

    /** Launched spring from 1 to 0 (normalised displacement): position, velocity, acceleration per second. */
    private fun launchedSpring(tau: Double): DoubleArray {
        val w = Pit.omega
        val z = Pit.zeta
        val wd = w * sqrt(1.0 - z * z)
        val b = (-Pit.launch * w + z * w) / wd
        val e = exp(-z * w * tau)
        val c = cos(wd * tau)
        val s = sin(wd * tau)
        val f = c + b * s
        val df = wd * (-s + b * c)
        return doubleArrayOf(e * f, e * (df - z * w * f), e * ((z * z * w * w - wd * wd) * f - 2.0 * z * w * df))
    }

    /** Pose of pit-stop tile [index] at [t] seconds into the pit stop (rest outside 0..duration). */
    fun pitTile(index: Int, t: Double): TilePose {
        if (t < 0.0 || t >= Pit.duration) return restPose
        val dx = Pit.side[index] * (Pit.width[index] + Pit.margin)
        val dy = Pit.rise[index]
        val tilt = Pit.tilt[index]
        if (t < Pit.brace) {
            val b = 1.0 - (1.0 - Pit.braceScale) * easeOut(t / Pit.brace)
            return TilePose(0.0, 0.0, b, b, 0.0, 0.0)
        }
        if (t < Pit.brace + Pit.exit) {
            val e = (t - Pit.brace) / Pit.exit
            val f = e.pow(Pit.exitPower)
            val speed = abs(dx) * Pit.exitPower * e.pow(Pit.exitPower - 1.0) / Pit.exit
            val stretch = 1.0 + min(Pit.maxStretch, speed * Pit.stretchPerSpeed)
            return TilePose(dx * f, dy * f, Pit.braceScale * stretch, Pit.braceScale / sqrt(stretch), tilt * f, speed)
        }
        val tau = t - Pit.arrive - Pit.delay[index]
        if (tau < 0.0) return TilePose(dx, dy, 1.0, 1.0, tilt, 0.0)
        val state = launchedSpring(tau)
        val speed = abs(dx * state[1])
        val stretch = min(Pit.maxStretch, speed * Pit.stretchPerSpeed)
        val squash = if (state[0] < 0.0) min(Pit.maxSquash, abs(dx * state[2]) * Pit.squashPerAccel) else 0.0
        val along = 1.0 + stretch - squash
        return TilePose(dx * state[0], dy * state[0], along, 1.0 / sqrt(along), tilt * state[0], speed)
    }

    /** Opacity at the head of the smear behind a tile moving at [speed] points per second. */
    fun smearAlpha(speed: Double): Double = Pit.ghostAlpha * clamp(speed / Pit.ghostSpeed)

    // Sticker

    class PeelState(val distance: Double, val angle: Double, val slap: Double)

    fun peelDemo(t: Double): PeelState {
        val k = Peel
        if (t < k.start || t >= k.end) return PeelState(0.0, k.angle, -1.0)
        if (t < k.tease) return PeelState(k.teaseDistance * easeOut((t - k.start) / (k.tease - k.start)), k.angle, -1.0)
        if (t < k.full) {
            val p = cubicBezier((t - k.tease) / (k.full - k.tease), 0.77, 0.0, 0.175, 1.0)
            return PeelState(k.teaseDistance + (k.maxDistance - k.teaseDistance) * p, k.angle, -1.0)
        }
        if (t < k.hold) {
            val local = t - k.full
            val fade = 1.0 - local / (k.hold - k.full)
            val wave = sin(2.0 * PI * k.flutterHz * local)
            return PeelState(
                k.maxDistance + k.flutter * wave * fade,
                k.angle + k.wobble * sin(2.0 * PI * k.flutterHz * local + 0.6) * fade,
                -1.0,
            )
        }
        if (t < k.snap) {
            val e = (t - k.hold) / (k.snap - k.hold)
            return PeelState(k.maxDistance * (1.0 - e * e * e), k.angle, -1.0)
        }
        return PeelState(0.0, k.angle, t - k.snap)
    }

    /** A released finger: the corner falls back from [distance] over `Peel.release` (accelerating), then slaps. */
    fun peelRelease(distance: Double, angle: Double, since: Double): PeelState {
        if (since < Peel.release) {
            val e = since / Peel.release
            return PeelState(distance * (1.0 - e * e * e), angle, -1.0)
        }
        val slap = since - Peel.release
        return PeelState(0.0, angle, if (slap < Peel.slapEnd) slap else -1.0)
    }

    /** The resting curl: the corner never lies flatter than `Peel.hint`. */
    fun withHint(p: PeelState): PeelState {
        return if (p.distance >= Peel.hint) p else PeelState(Peel.hint, if (p.distance > 0.5) p.angle else Peel.angle, p.slap)
    }

    class Slap(val press: Double, val shake: Double, val lines: Double)

    private val noSlap = Slap(1.0, 0.0, -1.0)

    /** The slap after a peel: lettering press (scale), card shake (points) and impact-line progress (0..1, -1 = none). */
    fun slapPose(slap: Double): Slap {
        if (slap < 0.0) return noSlap
        val press = 1.0 - Peel.slapPress * sin(PI * clamp(slap / Peel.slapPressTime))
        val shake = Peel.shake * exp(-slap / Peel.shakeDecay) * sin(2.0 * PI * Peel.shakeHz * slap)
        return Slap(press, shake, if (slap < Peel.lineTime) slap / Peel.lineTime else -1.0)
    }

    /** Rotation (degrees) of a quick card that is not held while the cards are being arranged. */
    fun jiggle(id: Int, time: Double, handles: Double): Double {
        val direction = if (id % 2 == 0) 1.0 else -1.0
        return handles * Jiggle.amplitude * direction * sin(2.0 * PI * Jiggle.hz * time + id * Jiggle.phase)
    }
}
