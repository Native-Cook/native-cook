package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesData as D
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesMotion as M
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesTheme as Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign

/** One animated scalar: a plain write cancels, a spring (with a velocity and a completion) or a timing runs from where the value is. */
internal class ThermalGlassTilesAnim(initial: Double) {
    var value = initial
        private set
    private var target = initial
    private var velocity = 0.0
    private var spring: ThermalGlassTilesSpring? = null
    private var restDisp = 0.01
    private var restSpeed = 2.0
    private var done: ((Boolean) -> Unit)? = null
    private var timing = false
    private var tFrom = 0.0
    private var tTo = 0.0
    private var tDur = 0.0
    private var tT = 0.0
    private var ease: (Double) -> Double = { it }
    val running get() = spring != null || timing

    fun set(v: Double) {
        cancel()
        velocity = 0.0; value = v; target = v
    }

    fun cancel() {
        spring = null; timing = false; done = null
    }

    fun spring(to: Double, s: ThermalGlassTilesSpring, v: Double = 0.0, disp: Double = 0.01, speed: Double = 2.0, cb: ((Boolean) -> Unit)? = null) {
        timing = false
        spring = s; target = to; velocity = v; restDisp = disp; restSpeed = speed; done = cb
    }

    fun timing(to: Double, seconds: Double, e: (Double) -> Double) {
        spring = null; done = null
        timing = true; tFrom = value; tTo = to; tDur = seconds; tT = 0.0; ease = e
    }

    fun step(dt: Double) {
        val s = spring
        if (s != null) {
            val n = max(1, ceil(dt / (1.0 / 240)).toInt())
            val h = dt / n
            var x = value
            var v = velocity
            for (i in 0 until n) {
                val nv = v + (-s.stiffness * (x - target) - s.damping * v) / s.mass * h
                x += nv * h
                v = nv
            }
            value = x; velocity = v
            if (abs(v) < restSpeed && abs(x - target) < restDisp) {
                value = target; velocity = 0.0; spring = null
                val cb = done
                done = null
                cb?.invoke(true)
            }
        } else if (timing) {
            tT = min(1.0, tT + dt / tDur)
            value = tFrom + (tTo - tFrom) * ease(tT)
            if (tT >= 1.0) { value = tTo; timing = false }
        }
    }
}

internal fun thermalInOutQuad(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) / 2
internal fun thermalOutCubic(t: Double) = 1 - (1 - t) * (1 - t) * (1 - t)

/**
 * The block's state machine (RN `RunReplay`): the autoplay loop as a pure function of time, a drag along the route or the timeline with
 * a coast and rubber band, a spring home three seconds after the last touch, the tile taps that jump to the longest contact and the
 * highest bounce, the white-hot peak, and the three odometer numerals. Time in seconds, positions in dp.
 */
internal class ThermalGlassTilesEngine(
    val autoPlay: Boolean,
    val reduced: Boolean,
    val data: List<ThermalGlassTilesSample>,
    val points: Array<ThermalGlassTilesRoutePoint>,
    val s: Double,
    val units: String = "km",
) {
    var onBuzz: (kind: Int) -> Unit = {}

    companion object {
        const val BUZZ_ONSET = 0
        const val BUZZ_KM = 1
        const val BUZZ_PEAK = 2
        private const val RETURN_AFTER = M.RETURN_AFTER_MS / 1000
    }

    val progress = ThermalGlassTilesAnim(1.0)
    val time = ThermalGlassTilesAnim(0.0)
    var clock = 0.0
    private var loopEpoch = -1.0
    private var lastTouch = -1.0
    private var pinned = -1.0
    var playing = false
    var held = false
    private var returning = false
    private var coasting = false
    private var coastOrigin = 1.0
    private var coastVelocity = 0.0
    private var releasedAt = 0.0
    private var startX = 0.0
    private var startP = 1.0
    private var routeGrab = false
    private var velocity = 0.0
    private var lastP = 1.0
    val touch = doubleArrayOf(0.0, 0.0, -1.0)
    private val touchPosition = doubleArrayOf(0.0, 0.0)
    private var touchStart = -100.0
    private var peakStart = -100.0
    private var peakArmed = true
    private var lastTick = 0L
    private var lastDistance = Double.NaN
    private var effortPrev = Double.NaN
    var active = false
    var foreground = true
    var dirty = true

    val ringBase = ThermalGlassTilesAnim(4.0)
    val thermalEffort = ThermalGlassTilesAnim(D.effortAt(sampleOf(1.0)))
    private var heldPrev = false

    private val routeLength = D.routeLength(points)
    val contactExtreme = data.indices.fold(0) { best, i -> if (data[i].contact > data[best].contact) i else best }.toDouble() / max(1, data.size - 1)
    val verticalExtreme = data.indices.fold(0) { best, i -> if (data[i].oscillation > data[best].oscillation) i else best }.toDouble() / max(1, data.size - 1)

    // derived
    val sample = ThermalGlassTilesSample()
    var effort = 0.0
    var streak = 0.0
    var direction = 1.0
    var phase = 0.0
    var fieldTime = 0.0
    var peak = 0.0
    var circlePeak = 0.0
    var smallPeak = 0.0
    var bpmScale = 1.0
    val ring: Double get() = ringBase.value + (if (reduced || held) 0.0 else 2 * Math.pow(max(0.0, Math.sin(time.value * Math.PI / 2)), 8.0))

    // odometer numerals: shown values in the integer units of each figure (distance x100, contact, oscillation x10)
    val shown = Array(3) { ThermalGlassTilesAnim(-1.0) }
    private val rollScale = doubleArrayOf(100.0, 1.0, 10.0)
    private val rollTarget = doubleArrayOf(-1.0, -1.0, -1.0)

    private fun sampleOf(p: Double): ThermalGlassTilesSample {
        val out = ThermalGlassTilesSample()
        D.sampleAt(data, p, out)
        return out
    }

    init {
        derive()
        effortPrev = effort
        playing = autoPlay && !reduced
    }

    fun rollValue(i: Int): Double = when (i) {
        0 -> sample.distance * (if (units == "mi") 0.621371 else 1.0)
        1 -> sample.contact
        else -> sample.oscillation
    }

    fun pin(t: Double) {
        progress.cancel()
        pinned = max(0.0, t)
        time.set(max(0.0, t) % M.LOOP)
        progress.set(M.progressAt(t))
        active = false
        dirty = true
    }

    private fun wake() {
        active = !reduced
        dirty = true
    }

    private fun derive() {
        D.sampleAt(data, progress.value, sample)
        effort = D.effortAt(sample)
        streak = M.clamp((376 - sample.pace) / 96)
        direction = if (playing || pinned >= 0) {
            val d = sign(M.progressAt(time.value + 1.0 / 120) - M.progressAt(time.value - 1.0 / 120))
            if (d == 0.0) 1.0 else d
        } else {
            val d = sign(velocity)
            if (d == 0.0) 1.0 else d
        }
        phase = M.strideAt(progress.value, time.value, reduced)
        val running = playing || pinned >= 0
        fieldTime = if (reduced) 0.0 else if (running) M.sceneTimeAt(time.value) else time.value
        val age = clock - peakStart
        peak = if (reduced) 0.0 else if (running) M.peakAt(time.value) else M.smooth(0.0, 0.08, age) * (1 - M.smooth(0.55, 0.7, age))
        circlePeak = if (reduced) 0.0 else if (running) M.peakAt(time.value - 0.08) else M.smooth(0.08, 0.16, age) * (1 - M.smooth(0.63, 0.78, age))
        smallPeak = if (reduced) 0.0 else if (running) M.peakAt(time.value - 0.16) else M.smooth(0.16, 0.24, age) * (1 - M.smooth(0.71, 0.86, age))
        bpmScale = if (reduced) 1.0 else M.bpmPulseAt(if (running) time.value - M.PEAK_START else age)
    }

    /** The reactions (`useAnimatedReaction`) that run whether or not the frame loop is awake. */
    private fun reactions() {
        if (effortPrev.isNaN() || abs(effort - effortPrev) > 1e-12) {
            effortPrev = effort
            if (reduced) thermalEffort.timing(effort, 0.2, ::thermalInOutQuad) else thermalEffort.set(effort)
            if (effort < 0.6) peakArmed = true
            if (effort >= 0.8 && peakArmed) {
                peakArmed = false
                peakStart = clock
                if (!playing && !returning && pinned < 0) onBuzz(BUZZ_PEAK)
            }
        }
        if (held != heldPrev) {
            heldPrev = held
            if (reduced) ringBase.set(if (held) 6.0 else 4.0) else ringBase.spring(if (held) 6.0 else 4.0, M.RING)
        }
        val distance = sample.distance
        if (lastDistance.isNaN()) lastDistance = distance
        if (distance != lastDistance) {
            val now = System.currentTimeMillis()
            if (!playing && pinned < 0 && (held || coasting)) {
                if (floor(distance) != floor(lastDistance) && now - lastTick >= M.HAPTIC_GAP_MS) {
                    lastTick = now
                    onBuzz(BUZZ_KM)
                }
            }
            lastDistance = distance
        }
        // the odometer: each figure eases to its rounded target in 180 ms (no easing under Reduce Motion)
        for (i in 0 until 3) {
            val q = round(rollValue(i) * rollScale[i])
            if (q == rollTarget[i]) continue
            rollTarget[i] = q
            val sh = shown[i]
            if (reduced || sh.value < 0) sh.set(q) else sh.timing(q, 0.18, ::thermalOutCubic)
        }
    }

    fun frame(nowT: Double, rawDt: Double) {
        val dt = min(max(0.0, rawDt), 0.1)
        progress.step(dt); time.step(dt); ringBase.step(dt); thermalEffort.step(dt)
        for (a in shown) a.step(dt)
        if (active && foreground && !reduced) {
            clock += dt
            body(nowT, dt)
        }
        derive()
        reactions()
        if (active || progress.running || time.running || ringBase.running || thermalEffort.running || shown.any { it.running }) dirty = true
    }

    private fun body(nowT: Double, dt: Double) {
        if (pinned >= 0) {
            val t = pinned % M.LOOP
            time.set(t)
            progress.set(M.progressAt(t))
            return
        }
        if (playing) {
            if (loopEpoch < 0) loopEpoch = nowT - time.value
            time.set((nowT - loopEpoch) % M.LOOP)
            progress.set(M.progressAt(time.value))
        } else {
            if (coasting && !held && !returning) {
                M.coastAt(coastOrigin, coastVelocity, clock - releasedAt, coastOut)
                progress.set(M.rubberProgress(coastOut[0]))
                velocity = coastOut[1]
                if (abs(coastOut[1]) < 0.001 || clock - releasedAt > 1.2) {
                    coasting = false
                    progress.spring(M.clamp(coastOut[0]), M.SNAP, coastOut[1])
                }
            }
            if (!returning) time.set((time.value + dt) % M.LOOP)
            if (!held && lastTouch >= 0 && !returning && clock - lastTouch >= RETURN_AFTER) {
                coasting = false
                returning = true
                time.timing(0.0, 0.6, ::thermalInOutQuad)
                progress.spring(1.0, M.RETURN, 0.0) { finished ->
                    if (finished) {
                        returning = false
                        lastTouch = -1.0
                        time.set(0.0)
                        loopEpoch = -1.0
                        playing = autoPlay
                        if (!autoPlay) active = false
                    }
                }
            }
            lastP = progress.value
        }
        if (held) { touch[0] = touchPosition[0]; touch[1] = touchPosition[1]; touch[2] = 0.0 }
        else { touch[0] = touchPosition[0]; touch[1] = touchPosition[1]; touch[2] = clock - touchStart }
    }

    private val coastOut = DoubleArray(2)

    /** Starts the loop on the first frame after the assets are in. */
    fun start() {
        loopEpoch = -1.0
        playing = autoPlay && !reduced
        active = autoPlay && !reduced
        dirty = true
    }

    // ---- gestures -------------------------------------------------------------------------------------------------------------

    /** [x], [y]: the finger in the large tile's own space (dp). */
    fun panBegin(x: Double, y: Double) {
        progress.cancel(); time.cancel()
        pinned = -1.0; playing = false; returning = false; coasting = false; held = true; velocity = 0.0
        startX = x; startP = progress.value; lastTouch = clock
        val rx = Theme.LARGE.x + x / s
        val ry = Theme.LARGE.y + y / s
        val near = D.nearestRoute(points, rx, ry, progress.value)
        routeGrab = near.second <= Theme.ROUTE_GRAB
        if (routeGrab) progress.set(near.first)
        touchPosition[0] = rx; touchPosition[1] = ry
        touchStart = clock
        touch[0] = rx; touch[1] = ry; touch[2] = 0.0
        wake()
        onBuzz(BUZZ_ONSET)
    }

    fun panUpdate(x: Double, y: Double, vx: Double, vy: Double) {
        val rx = Theme.LARGE.x + x / s
        val ry = Theme.LARGE.y + y / s
        var next = startP + (x - startX) / (M.DRAG_PT * s)
        if (routeGrab) next = D.nearestRoute(points, rx, ry, progress.value).first
        val raw = M.rubberProgress(next)
        progress.set(raw)
        if (routeGrab) {
            val i = round(M.clamp(raw) * (points.size - 1)).toInt()
            val a = points[max(0, i - 1)]
            val b = points[min(points.size - 1, i + 1)]
            val len = max(0.001, hypot(b.x - a.x, b.y - a.y))
            velocity = (vx * (b.x - a.x) + vy * (b.y - a.y)) / (s * len * routeLength)
        } else velocity = vx / (M.DRAG_PT * s)
        touchPosition[0] = rx; touchPosition[1] = ry
        lastTouch = clock
    }

    fun panEnd() {
        if (!reduced) {
            coastOrigin = progress.value
            coastVelocity = velocity
            releasedAt = clock
            coasting = true
        } else progress.set(M.clamp(progress.value))
    }

    fun panFinalize() {
        held = false
        lastTouch = clock
        touchStart = clock
    }

    private fun shortcut(target: Double) {
        progress.cancel()
        pinned = -1.0; playing = false; returning = false; coasting = false
        lastTouch = clock
        onBuzz(BUZZ_ONSET)
        wake()
        if (reduced) progress.set(target) else progress.spring(target, M.SHORTCUT)
    }

    fun tapBegin(x: Double, y: Double) {
        touchPosition[0] = x; touchPosition[1] = y
        touchStart = clock
        touch[0] = x; touch[1] = y; touch[2] = 0.0
        wake()
    }

    fun contactTap() = shortcut(contactExtreme)
    fun verticalTap() = shortcut(verticalExtreme)

    /** Accessibility: step one kilometre. */
    fun nudge(direction: Int) {
        val total = data.last().distance
        shortcut(M.clamp(progress.value + direction * (if (units == "mi") 1.609344 else 1.0) / max(0.1, total)))
    }
}

