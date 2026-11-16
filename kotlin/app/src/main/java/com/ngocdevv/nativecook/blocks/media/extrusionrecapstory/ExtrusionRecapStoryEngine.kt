package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import com.ngocdevv.nativecook.blocks.media.extrusionrecapstory.ExtrusionRecapStoryMotion as M
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** One animated scalar: a plain write cancels, a spring (velocity, completion) or a timing runs from where the value is. */
internal class ExtrusionRecapStoryAnim(initial: Double) {
    var value = initial
        private set
    private var target = initial
    private var velocity = 0.0
    private var spring: ExtrusionRecapStorySpring? = null
    private var done: ((Boolean) -> Unit)? = null
    private var timing = false
    private var tFrom = 0.0
    private var tTo = 0.0
    private var tDur = 0.0
    private var tT = 0.0

    fun set(v: Double) {
        cancel(); velocity = 0.0; value = v; target = v
    }

    fun cancel() {
        spring = null; timing = false; done = null
    }

    fun spring(to: Double, s: ExtrusionRecapStorySpring, v: Double = 0.0, cb: ((Boolean) -> Unit)? = null) {
        timing = false
        spring = s; target = to; velocity = v; done = cb
    }

    /** An in-out quad timing of [seconds]. */
    fun timing(to: Double, seconds: Double) {
        spring = null; done = null
        timing = true; tFrom = value; tTo = to; tDur = seconds; tT = 0.0
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
            if (abs(v) < 2.0 && abs(x - target) < 0.01) {
                value = target; velocity = 0.0; spring = null
                val cb = done
                done = null
                cb?.invoke(true)
            }
        } else if (timing) {
            tT = min(1.0, tT + dt / tDur)
            val e = if (tT < 0.5) 2 * tT * tT else 1 - (-2 * tT + 2) * (-2 * tT + 2) / 2
            value = tFrom + (tTo - tFrom) * e
            if (tT >= 1.0) { value = tTo; timing = false }
        }
    }
}

/**
 * The block's state machine (RN `ExtrusionStory`): the three-page film as a pure function of time, a drag (300 design points per page,
 * a flick commits, a hold steers the light vertically), taps on either side, the spring that returns to the opening page two seconds
 * after the last touch, and the landing pulse. Times in ms (clock) or s (elapsed, age); positions in dp.
 */
internal class ExtrusionRecapStoryEngine(
    val pages: List<ExtrusionRecapStoryPage>,
    val autoPlay: Boolean,
    val reduce: Boolean,
    val s: Double,
    val width: Double,
) {
    val count = pages.size
    var onCue: (Int) -> Unit = {}
    var onIndex: (Int) -> Unit = {}

    companion object {
        const val CUE_GRAB = 0
        const val CUE_WALL = 1
        const val CUE_LANDED = 2
    }

    val progress = ExtrusionRecapStoryAnim(0.0)
    var clock = 0.0
    var elapsed = 0.0
    var age = 10.0
    private var mode = 0 // 0 film, 1 touch, 2 pinned, 3 returning, 4 done
    var held = false
    private var downAt = 0L
    private var lastTouch = -1e9
    private var origin = 0.0
    private var intent = false
    private var pin = -1.0
    val steerX = ExtrusionRecapStoryAnim(0.0)
    val steerY = ExtrusionRecapStoryAnim(0.0)
    var speed = 0.0
    val limit = ExtrusionRecapStoryAnim(0.0)
    private var landing = -1e9
    private var wallCue = false
    private var landedPage = -1
    var interacting = false
    var paused = false
    var foreground = true
    var screenReader = false
    var activePage = 0
    var dirty = true

    private var indexPrev = -1
    private var landedPrev: Boolean? = null

    private val animatedEnabled get() = !reduce && !screenReader && foreground && !paused
    val playEnabled get() = count > 0 && autoPlay && animatedEnabled
    private val frameActive get() = foreground && !reduce && !paused && !screenReader && (playEnabled || interacting)

    init {
        if (reduce) progress.set(round(progress.value))
    }

    fun pin(t: Double) {
        mode = 2
        pin = t
        progress.set(M.storyAt(t, count))
        elapsed = t
        age = M.landingAt(t)
        dirty = true
    }

    fun frame(rawDtMs: Double) {
        val dt = min(max(rawDtMs, 0.0), 50.0)
        progress.step(dt / 1000); steerX.step(dt / 1000); steerY.step(dt / 1000); limit.step(dt / 1000)
        if (frameActive) {
            clock += dt
            body(dt)
        } else if (pin >= 0) {
            progress.set(M.storyAt(pin, count))
        }
        reactions()
        dirty = true
    }

    private fun body(dt: Double) {
        if (pin >= 0) {
            progress.set(M.storyAt(pin, count))
            elapsed = pin
            age = M.landingAt(pin)
            return
        }
        if (!animatedEnabled) return
        if (mode == 0 && playEnabled) {
            elapsed += dt / 1000
            progress.set(M.storyAt(elapsed, count))
            age = M.landingAt(elapsed)
        } else {
            age = (clock - landing) / 1000
            if (!held && !playEnabled && clock - lastTouch > M.RESUME && mode == 1) interacting = false
            if (!held && clock - lastTouch > M.RESUME && playEnabled && mode == 1) {
                mode = 3
                progress.spring(ceil(progress.value / count) * count, M.GLIDE) { finished ->
                    if (finished) {
                        mode = 0
                        elapsed = 0.0
                        progress.set(0.0)
                        age = 10.0
                        interacting = false
                    }
                }
            }
        }
    }

    private fun reactions() {
        val c = max(1, count)
        val i = round(M.mod(progress.value, c.toDouble())).toInt() % c
        if (i != indexPrev) {
            indexPrev = i
            if (count > 0) { activePage = i; onIndex(i) }
        }
        val landed = abs(progress.value - round(progress.value)) < 0.002 && !held
        val was = landedPrev
        landedPrev = landed
        if (landed && was == false && mode == 1 && landedPage != round(progress.value).toInt()) {
            landedPage = round(progress.value).toInt()
            landing = clock
            onCue(CUE_LANDED)
        }
    }

    // ---- navigation -----------------------------------------------------------------------------------------------------------

    fun changePage(delta: Int) {
        if (count == 0) return
        pin = -1.0
        mode = 1
        lastTouch = clock
        interacting = true
        val current = round(M.mod(progress.value, count.toDouble())).toInt()
        val target = M.clamp((current + delta).toDouble(), 0.0, (count - 1).toDouble())
        if (target.toInt() == current && delta > 0) return
        if (reduce) progress.timing(target, 0.25) else progress.spring(target, M.PAGE)
        onCue(CUE_GRAB)
    }

    fun advance() {
        if (count == 0) return
        val current = round(M.mod(progress.value, count.toDouble())).toInt()
        if (current < count - 1) {
            changePage(1)
            return
        }
        pin = -1.0
        mode = 1
        lastTouch = clock
        interacting = true
        onCue(CUE_GRAB)
        if (reduce) {
            progress.timing(0.0, 0.25)
            return
        }
        progress.set(current.toDouble())
        progress.spring(count.toDouble(), M.GLIDE) { finished -> if (finished) progress.set(0.0) }
    }

    // ---- gesture --------------------------------------------------------------------------------------------------------------

    fun panBegin() {
        progress.cancel(); steerX.cancel(); steerY.cancel()
        pin = -1.0
        mode = 1
        held = true
        downAt = System.currentTimeMillis()
        lastTouch = clock
        val p = M.mod(progress.value, count.toDouble())
        progress.set(p)
        origin = p
        intent = false
        wallCue = false
        landedPage = round(p).toInt()
        interacting = true
        onCue(CUE_GRAB)
    }

    fun panUpdate(tx: Double, ty: Double, vx: Double) {
        val dx = tx / s
        val dy = ty / s
        if (abs(dx) >= M.INTENT) intent = true
        if (intent) {
            val raw = origin - dx / M.DISTANCE
            val clamped = M.clamp(raw, 0.0, (count - 1).toDouble())
            val overscroll = raw - clamped
            limit.set((0.3 * M.RUBBER * abs(overscroll)) / (1 + M.RUBBER * abs(overscroll)))
            if (!reduce) progress.set(clamped)
            speed = abs(vx / s / M.DISTANCE)
            if (raw % 1 > 0.55 && !wallCue) {
                wallCue = true
                onCue(CUE_WALL)
            }
        } else if (!reduce) {
            steerX.set(M.clamp(dx / M.STEER_DISTANCE, -1.0, 1.0) * M.STEER_ANGLE)
            steerY.set(M.clamp(dy / M.STEER_DISTANCE, -1.0, 1.0) * M.STEER_ANGLE)
        }
    }

    fun panEnd(tx: Double, vx: Double, x: Double) {
        val duration = System.currentTimeMillis() - downAt
        val velocity = -vx / s / M.DISTANCE
        var target = round(progress.value)
        if (intent) {
            val delta = -tx / s / M.DISTANCE
            val committed = abs(delta) > M.THRESHOLD || abs(vx / s) > M.VELOCITY
            target = if (reduce) round(origin) + (if (committed) M.signOf(delta) else 0.0) else round(progress.value + velocity * M.PROJECTION)
            if (!reduce && committed) {
                val direction = if (abs(vx / s) > M.VELOCITY) M.signOf(velocity) else M.signOf(delta)
                target = round(origin) + direction
            }
        } else if (duration < M.HOLD) target = round(origin) + (if (x > width / 2) 1 else -1)
        target = M.clamp(target, 0.0, (count - 1).toDouble())
        if (reduce) progress.timing(target, 0.25) else progress.spring(target, M.PAGE, velocity)
    }

    fun panFinalize() {
        held = false
        lastTouch = clock
        speed = 0.0
        steerX.spring(0.0, M.STEER)
        steerY.spring(0.0, M.STEER)
        limit.spring(0.0, M.PAGE)
    }
}
