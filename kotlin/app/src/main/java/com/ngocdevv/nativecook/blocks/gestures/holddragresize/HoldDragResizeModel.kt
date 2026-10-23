package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal enum class HoldDragResizeHaptic { Lift, Tick, Wall, Drop }

internal class HoldDragResizeModel(
    val layout: HoldDragResizeLayout,
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
) {
    private val n = HoldDragResizePlan.size
    val kind = IntArray(n) { HoldDragResizePlan[it].kind }
    val start = DoubleArray(n) { HoldDragResizePlan[it].start }
    val end = DoubleArray(n) { HoldDragResizePlan[it].end }
    val top = start.copyOf()
    val topVelocity = DoubleArray(n)
    val length = DoubleArray(n) { end[it] - start[it] }
    val lengthVelocity = DoubleArray(n)
    val elevation = DoubleArray(n) { HoldDragResizeTokens.Elevation.Rest }
    private val elevationVelocity = DoubleArray(n)
    val lock = DoubleArray(n)
    private val lockTarget = DoubleArray(n)
    val push = DoubleArray(n)
    val was = start.copyOf()
    val events = mutableListOf<HoldDragResizeHaptic>()

    var active = -1
        private set
    var edge = 1
        private set
    private var grabEdge = 0.0
    private var grabY = 0.0
    var target = 0.0
        private set
    var over = 0.0
        private set
    private var atWall = false
    private var wall = n
    var shown = -1
        private set
    private var refused = -1
    private var pressCard = -1
    private var pressShown = -1
    var press = 0.0
        private set
    var lift = 0.0
        private set
    private var liftVelocity = 0.0
    var focus = focusTotal()
        private set
    private var focusVelocity = 0.0
    var open = openTotal()
        private set
    private var openVelocity = 0.0
    var fingerX = 0.0
        private set
    var fingerY = 0.0
        private set
    var delta = 0.0
        private set
    var deltaLast = 0.0
        private set

    val originalEdge: Double
        get() = grabEdge

    val pressedIndex: Int
        get() = pressShown
    var ghostX = 0.0
        private set
    var ghostY = 0.0
        private set
    var ghostOpacity = 0.0
        private set
    private var ghostOpacityTarget = 0.0
    var ghostPress = 0.0
        private set
    private var ghostPressTarget = 0.0
    private var scriptDown = false
    private val tweens = DoubleArray((2 * n + 4) * 3)
    var version = 0
        private set
    var moving = true
        private set

    private var paused = !autoPlay || reducedMotion
    private var touching = false
    private var quiet = 0.0
    private var tidy = -1.0
    private var clock = 0.0
    private var silent = autoPlay

    fun advance(deltaSeconds: Double) {
        val dt = deltaSeconds.coerceIn(0.0, 0.05)
        if (autoPlay && !reducedMotion) {
            if (paused) {
                if (!touching && active < 0 && pressCard < 0) quiet += dt
                if (tidy < 0 && quiet >= HoldDragResizeTokens.ResumeMs / 1_000.0 && active < 0) {
                    tidy = 0.0
                    silent = true
                    resetPlan()
                }
                if (tidy >= 0) {
                    tidy += dt
                    if (tidy >= HoldDragResizeTokens.Demo.Tidy) {
                        tidy = -1.0
                        clock = 0.0
                        paused = false
                    }
                }
            } else {
                val before = clock
                val after = before + dt
                script(before, after)
                clock = if (after >= LOOP / 1_000.0) after - LOOP / 1_000.0 else after
            }
        }
        step(dt)
    }

    fun touchStart() {
        touching = true
        quiet = 0.0
        tidy = -1.0
        silent = false
        if (!paused) {
            paused = true
            ghostOpacityTarget = 0.0
            ghostPressTarget = 0.0
            if (scriptDown) {
                scriptDown = false
                silent = true
                endResize()
                silent = false
            }
            pressEnd()
        }
    }

    fun touchEnd() {
        touching = false
        quiet = 0.0
    }

    fun updateFinger(x: Double, y: Double) {
        fingerX = x
        fingerY = y
    }

    fun cardAt(x: Double, y: Double): Int {
        if (x < layout.cardX - 4) return -1
        for (i in 0 until n) {
            val y0 = layout.yOf(top[i])
            val y1 = layout.yOf(top[i] + length[i])
            if (y in y0..y1) return i
        }
        return -1
    }

    fun pressBegin(index: Int) {
        if (active >= 0 || index < 0 || kind[index] == HoldDragResizeKind.Meeting) return
        pressCard = index
        if (pressShown != index) {
            pressShown = index
            press = 0.0
            snapTween(slot(PressSlot), 0.0)
        }
        moving = true
    }

    fun pressEnd() {
        pressCard = -1
        moving = true
    }

    fun beginResize(index: Int, y: Double) {
        if (index < 0 || active >= 0) return
        pressCard = -1
        moving = true
        if (kind[index] == HoldDragResizeKind.Meeting) {
            refused = index
            lockTarget[index] = 1.0
            emit(HoldDragResizeHaptic.Wall)
            return
        }
        val (targets, lengths) = plannedLayout()
        start[index] = targets[index]
        end[index] = targets[index] + lengths[index]
        edge = if (y > layout.yOf(targets[index] + lengths[index] / 2)) 1 else 0
        grabEdge = if (edge == 1) end[index] else start[index]
        grabY = y
        target = grabEdge
        over = 0.0
        atWall = false
        wall = wallAfter(index)
        active = index
        shown = index
        delta = 0.0
        snapTween(slot(DeltaSlot), 0.0)
        version++
        emit(HoldDragResizeHaptic.Lift)
    }

    fun moveResize(y: Double) {
        val index = active
        if (index < 0) return
        val raw = grabEdge + (y - grabY) / layout.ppm
        val (low, high) = edgeLimits(index, edge)
        val snapped = quarter(raw).coerceIn(low, high)
        var excess = 0.0
        if (!reducedMotion) {
            if (edge == 1) {
                if (raw > high) excess = rubber(raw - high)
                else if (raw < low) excess = -rubber(low - raw) * 0.5
            } else if (raw > high) {
                excess = rubber(raw - high) * 0.5
            }
        }
        over = excess
        val ticked = snapped != target
        if (ticked) {
            target = snapped
            version++
        }
        val wasAtWall = atWall
        atWall = edge == 1 && raw > high + 1
        if (atWall && !wasAtWall) {
            emit(HoldDragResizeHaptic.Wall)
            if (wall < n) lockTarget[wall] = 1.0
        } else {
            if (ticked) emit(HoldDragResizeHaptic.Tick)
            if (!atWall && wasAtWall && wall < n) lockTarget[wall] = 0.0
        }
        moving = true
    }

    fun endResize() {
        pressCard = -1
        if (refused >= 0) {
            lockTarget[refused] = 0.0
            refused = -1
        }
        val index = active
        if (index < 0) return
        if (edge == 1) end[index] = target else start[index] = target
        over = 0.0
        atWall = false
        if (wall < n) lockTarget[wall] = 0.0
        active = -1
        version++
        moving = true
        emit(HoldDragResizeHaptic.Drop)
    }

    fun nudgeEnd(index: Int, direction: Int) {
        if (index !in 0 until n || active >= 0 || kind[index] == HoldDragResizeKind.Meeting) return
        val (targets, lengths) = plannedLayout()
        start[index] = targets[index]
        end[index] = targets[index] + lengths[index]
        val (low, high) = edgeLimits(index, 1)
        val next = (end[index] + direction * HoldDragResizeTokens.Resize.Quarter).coerceIn(low, high)
        if (next == end[index]) return
        end[index] = next
        shown = index
        version++
        moving = true
        emit(HoldDragResizeHaptic.Tick)
    }

    fun settledLayout(): Pair<DoubleArray, DoubleArray> = plannedLayout(withOver = false)

    private fun resetPlan() {
        endResize()
        HoldDragResizePlan.forEachIndexed { index, item ->
            start[index] = item.start
            end[index] = item.end
            lockTarget[index] = 0.0
        }
        version++
        moving = true
    }

    private fun step(dt: Double) {
        val (targets, lengths) = plannedLayout()
        var busy = false
        for (i in 0 until n) {
            if (kind[i] == HoldDragResizeKind.Meeting) {
                top[i] = targets[i]
                length[i] = lengths[i]
                continue
            }
            val spring = when {
                reducedMotion -> HoldDragResizeTokens.Springs.Reduced
                i != active && !(active >= 0 && over > 0) -> {
                    val distance = if (shown >= 0) max(1, i - shown) else 1
                    if (i == shown) HoldDragResizeTokens.Springs.Detent else HoldDragResizeTokens.Spring(
                        HoldDragResizeTokens.Springs.Ripple.durationMs + HoldDragResizeTokens.Resize.RippleStep * (distance - 1),
                        HoldDragResizeTokens.Springs.Ripple.dampingRatio,
                    )
                }
                else -> HoldDragResizeTokens.Springs.Detent
            }
            val topStep = springStep(top[i], topVelocity[i], targets[i], dt, spring)
            val lengthStep = springStep(length[i], lengthVelocity[i], lengths[i], dt, spring)
            top[i] = topStep.first
            topVelocity[i] = topStep.second
            length[i] = lengthStep.first
            lengthVelocity[i] = lengthStep.second
            if (topStep.second != 0.0 || lengthStep.second != 0.0) busy = true
        }

        var floor = -1e9
        var floorVelocity = 0.0
        for (i in 0 until n) {
            if (kind[i] != HoldDragResizeKind.Meeting && i != active && top[i] < floor) {
                top[i] = floor
                topVelocity[i] = max(topVelocity[i], floorVelocity)
            }
            floor = top[i] + length[i]
            floorVelocity = topVelocity[i] + lengthVelocity[i]
        }
        var ceiling = 1e9
        for (i in n - 1 downTo 0) {
            if (kind[i] != HoldDragResizeKind.Meeting && top[i] + length[i] > ceiling) {
                length[i] = max(ceiling - top[i], 4.0)
            }
            ceiling = if (kind[i] == HoldDragResizeKind.Meeting) top[i] else min(ceiling, top[i])
        }

        val pressGoal = if (pressCard >= 0 || (active >= 0 && active == pressShown)) 1.0 else 0.0
        press = tween(slot(PressSlot), press, pressGoal, dt, HoldDragResizeTokens.Resize.HoldMs / 1_000.0, HoldDragResizeTokens.Timing.PressOut, true)
        if (press != pressGoal) busy = true

        val settled = plannedLayout(withOver = false).first
        for (i in 0 until n) {
            val elevationTarget = when {
                i == active -> if (reducedMotion) HoldDragResizeTokens.Elevation.Held * 0.5 else HoldDragResizeTokens.Elevation.Held
                i == pressCard -> HoldDragResizeTokens.Elevation.Rest +
                    (HoldDragResizeTokens.Elevation.Pressed - HoldDragResizeTokens.Elevation.Rest) * press
                else -> HoldDragResizeTokens.Elevation.Rest
            }
            val elevationSpring = when {
                reducedMotion -> HoldDragResizeTokens.Springs.Reduced
                elevationTarget > elevation[i] -> HoldDragResizeTokens.Springs.Lift
                else -> HoldDragResizeTokens.Springs.Drop
            }
            val elevationStep = springStep(elevation[i], elevationVelocity[i], elevationTarget, dt, elevationSpring)
            elevation[i] = elevationStep.first
            elevationVelocity[i] = elevationStep.second
            if (elevationStep.second != 0.0) busy = true
            lock[i] = tween(i, lock[i], lockTarget[i], dt, HoldDragResizeTokens.Timing.LockIn, HoldDragResizeTokens.Timing.LockOut)
            if (lock[i] != lockTarget[i]) busy = true
            val pushed = if (kind[i] == HoldDragResizeKind.Task && i != active && settled[i] > start[i] + 0.5) 1.0 else 0.0
            if (pushed > 0) was[i] = start[i]
            push[i] = tween(n + i, push[i], pushed, dt, HoldDragResizeTokens.Timing.FadeIn, HoldDragResizeTokens.Timing.WasOut)
            if (push[i] != pushed) busy = true
        }

        val liftTarget = if (active >= 0) 1.0 else 0.0
        val liftSpring = if (reducedMotion) HoldDragResizeTokens.Springs.Reduced else if (liftTarget > 0) {
            HoldDragResizeTokens.Springs.Lift
        } else {
            HoldDragResizeTokens.Springs.Drop
        }
        val liftStep = springStep(lift, liftVelocity, liftTarget, dt, liftSpring)
        lift = max(liftStep.first, 0.0)
        liftVelocity = liftStep.second
        if (active < 0 && lift < 0.002) {
            lift = 0.0
            liftVelocity = 0.0
        }

        val focusStep = springStep(focus, focusVelocity, liveLength(focusOnly = true), dt, if (reducedMotion) HoldDragResizeTokens.Springs.Reduced else HoldDragResizeTokens.Springs.Roll)
        focus = focusStep.first
        focusVelocity = focusStep.second
        val openStep = springStep(open, openVelocity, HoldDragResizeTokens.Day.End - HoldDragResizeTokens.Day.Start - liveLength(focusOnly = false), dt, if (reducedMotion) HoldDragResizeTokens.Springs.Reduced else HoldDragResizeTokens.Springs.Roll)
        open = openStep.first
        openVelocity = openStep.second

        val settledDelta = if (shown < 0) 0.0 else if (edge == 1) target - grabEdge else grabEdge - target
        val deltaGoal = if (abs(settledDelta) >= HoldDragResizeTokens.Resize.Quarter) 1.0 else 0.0
        if (deltaGoal > 0) deltaLast = settledDelta
        delta = tween(slot(DeltaSlot), delta, deltaGoal, dt, HoldDragResizeTokens.Timing.FadeIn, HoldDragResizeTokens.Timing.FadeOut)
        if (delta != deltaGoal) busy = true

        ghostOpacity = tween(slot(FingerSlot), ghostOpacity, ghostOpacityTarget, dt, HoldDragResizeTokens.Timing.FingerIn, HoldDragResizeTokens.Timing.FingerOut)
        ghostPress = tween(slot(FingerPressSlot), ghostPress, ghostPressTarget, dt, HoldDragResizeTokens.Timing.FingerIn, HoldDragResizeTokens.Timing.FingerOut)
        moving = busy || liftVelocity != 0.0 || focusVelocity != 0.0 || openVelocity != 0.0 || active >= 0 ||
            pressCard >= 0 || ghostOpacity != ghostOpacityTarget || ghostPress != ghostPressTarget
    }

    private fun script(t0: Double, t1: Double) {
        scriptedGesture(
            HoldDragResizeTokens.Demo.GrowDown,
            HoldDragResizeTokens.Demo.GrowLifted,
            HoldDragResizeTokens.Demo.GrowGrab,
            HoldDragResizeTokens.Demo.GrowPath,
            HoldDragResizeTokens.Demo.GrowUp,
            t0,
            t1,
        )
        scriptedGesture(
            HoldDragResizeTokens.Demo.ShrinkDown,
            HoldDragResizeTokens.Demo.ShrinkLifted,
            HoldDragResizeTokens.Demo.ShrinkGrab,
            HoldDragResizeTokens.Demo.ShrinkPath,
            HoldDragResizeTokens.Demo.ShrinkUp,
            t0,
            t1,
        )
    }

    private fun scriptedGesture(
        down: Double,
        lifted: Double,
        grab: Double,
        path: Array<DoubleArray>,
        up: Double,
        t0: Double,
        t1: Double,
    ) {
        fun crosses(at: Double) = t0 < at && t1 >= at
        val x = layout.cardX + layout.cardW * HoldDragResizeTokens.Demo.FingerX
        if (crosses(down)) {
            ghostX = x
            ghostY = layout.yOf(grab)
            fingerX = x
            fingerY = ghostY
            ghostOpacityTarget = 1.0
            ghostPressTarget = 1.0
            pressBegin(MemoIndex)
        }
        if (crosses(lifted)) {
            beginResize(MemoIndex, layout.yOf(grab))
            scriptDown = true
        }
        if (scriptDown && t1 >= lifted && t1 < up) {
            val y = layout.yOf(grab + pathAt(path, t1))
            ghostY = y
            fingerY = y
            moveResize(y)
        }
        if (crosses(up)) {
            scriptDown = false
            endResize()
            ghostOpacityTarget = 0.0
            ghostPressTarget = 0.0
        }
    }

    private fun plannedLayout(withOver: Boolean = true): Pair<DoubleArray, DoubleArray> {
        val excess = if (withOver) over else 0.0
        val tops = DoubleArray(n)
        val lengths = DoubleArray(n)
        var floor = HoldDragResizeTokens.Day.Start.toDouble()
        for (i in 0 until n) {
            var itemStart = start[i]
            var itemEnd = end[i]
            if (i == active) {
                if (edge == 1) itemEnd = target + excess else itemStart = target + excess
            }
            tops[i] = if (kind[i] == HoldDragResizeKind.Meeting || i == active) itemStart else max(itemStart, floor)
            lengths[i] = itemEnd - itemStart
            floor = tops[i] + lengths[i]
        }
        val index = active
        if (index >= 0 && edge == 1 && excess > 0) {
            val wallTop = if (wall < n) start[wall] else HoldDragResizeTokens.Day.End.toDouble()
            val bottom = tops[index] + lengths[index]
            var sum = 0.0
            for (j in index + 1 until wall) sum += lengths[j]
            if (sum > 0) {
                val scale = max((wallTop - bottom) / sum, 0.3)
                var y = bottom
                for (j in index + 1 until wall) {
                    tops[j] = y
                    lengths[j] *= scale
                    y += lengths[j]
                }
            } else {
                lengths[index] = wallTop - tops[index]
            }
        }
        return tops to lengths
    }

    private fun edgeLimits(index: Int, selectedEdge: Int): Pair<Double, Double> {
        if (selectedEdge == 1) {
            var tasks = 0.0
            var high = HoldDragResizeTokens.Day.End.toDouble()
            for (j in index + 1 until n) {
                if (kind[j] == HoldDragResizeKind.Meeting) {
                    high = start[j]
                    break
                }
                tasks += end[j] - start[j]
            }
            return start[index] + HoldDragResizeTokens.Resize.MinLength to high - tasks
        }
        var low = HoldDragResizeTokens.Day.Start.toDouble()
        if (index > 0) {
            val (tops, lengths) = plannedLayout()
            low = tops[index - 1] + lengths[index - 1]
        }
        return low to end[index] - HoldDragResizeTokens.Resize.MinLength
    }

    private fun wallAfter(index: Int): Int {
        for (j in index + 1 until n) if (kind[j] == HoldDragResizeKind.Meeting) return j
        return n
    }

    private fun liveLength(focusOnly: Boolean): Double {
        var total = 0.0
        for (i in 0 until n) {
            if (focusOnly && kind[i] != HoldDragResizeKind.Focus) continue
            total += if (i == active) {
                if (edge == 1) target - start[i] else end[i] - target
            } else {
                end[i] - start[i]
            }
        }
        return total
    }

    private fun focusTotal(): Double = HoldDragResizePlan.filter { it.kind == HoldDragResizeKind.Focus }.sumOf { it.end - it.start }

    private fun openTotal(): Double = HoldDragResizeTokens.Day.End - HoldDragResizeTokens.Day.Start -
        HoldDragResizePlan.sumOf { it.end - it.start }

    private fun emit(haptic: HoldDragResizeHaptic) {
        if (!silent) events += haptic
    }

    private fun tween(slot: Int, value: Double, goal: Double, dt: Double, durationIn: Double, durationOut: Double, linearIn: Boolean = false): Double {
        val offset = slot * 3
        if (tweens[offset + 1] != goal) {
            tweens[offset] = value
            tweens[offset + 1] = goal
            tweens[offset + 2] = 0.0
        }
        val from = tweens[offset]
        val rising = goal > from
        val duration = if (rising) durationIn else durationOut
        val age = min(tweens[offset + 2] + dt, duration)
        tweens[offset + 2] = age
        val progress = if (duration > 0) age / duration else 1.0
        if (progress >= 1) return goal
        val eased = if (rising && linearIn) progress else easeOut(progress)
        return from + (goal - from) * eased
    }

    private fun snapTween(slot: Int, value: Double) {
        tweens[slot * 3] = value
        tweens[slot * 3 + 1] = value
        tweens[slot * 3 + 2] = 0.0
    }

    private fun slot(kind: Int) = 2 * n + kind

    private companion object {
        const val FingerSlot = 0
        const val FingerPressSlot = 1
        const val DeltaSlot = 2
        const val PressSlot = 3
        val MemoIndex = HoldDragResizePlan.indexOfFirst { it.id == "memo" }
    }
}

private fun springStep(
    value: Double,
    velocity: Double,
    target: Double,
    dt: Double,
    spring: HoldDragResizeTokens.Spring,
): Pair<Double, Double> {
    val omega = 2 * PI / (spring.durationMs / 1_000.0)
    val stiffness = omega * omega
    val damping = 2 * spring.dampingRatio * omega
    val steps = max(1, ceil(dt / 0.004).toInt())
    val interval = dt / steps
    var position = value
    var speed = velocity
    repeat(steps) {
        speed += (-stiffness * (position - target) - damping * speed) * interval
        position += speed * interval
    }
    return if (abs(position - target) < 0.002 && abs(speed) < 0.01) target to 0.0 else position to speed
}

private fun rubber(value: Double): Double = HoldDragResizeTokens.Resize.RubberMax *
    (1 - exp(-value * HoldDragResizeTokens.Resize.Rubber / HoldDragResizeTokens.Resize.RubberMax))

private fun quarter(value: Double): Double = round(value / HoldDragResizeTokens.Resize.Quarter) * HoldDragResizeTokens.Resize.Quarter

private fun pathAt(path: Array<DoubleArray>, time: Double): Double {
    if (time <= path[0][0]) return path[0][1]
    for (i in 1 until path.size) {
        val t1 = path[i][0]
        val v1 = path[i][1]
        val t0 = path[i - 1][0]
        val v0 = path[i - 1][1]
        if (time <= t1) return v0 + (v1 - v0) * easeInOut((time - t0) / (t1 - t0))
    }
    return path.last()[1]
}

private fun easeOut(value: Double): Double = cubicBezier(0.23, 1.0, 0.32, 1.0, value)

private fun easeInOut(value: Double): Double = cubicBezier(0.77, 0.0, 0.175, 1.0, value)

private fun cubicBezier(x1: Double, y1: Double, x2: Double, y2: Double, value: Double): Double {
    val clamped = value.coerceIn(0.0, 1.0)
    val cx = 3 * x1
    val bx = 3 * (x2 - x1) - cx
    val ax = 1 - cx - bx
    val cy = 3 * y1
    val by = 3 * (y2 - y1) - cy
    val ay = 1 - cy - by
    var time = clamped
    repeat(6) {
        val x = ((ax * time + bx) * time + cx) * time - clamped
        val dx = (3 * ax * time + 2 * bx) * time + cx
        if (abs(x) >= 1e-5 && abs(dx) >= 1e-6) time -= x / dx
    }
    if (time !in 0.0..1.0) {
        var low = 0.0
        var high = 1.0
        time = clamped
        repeat(20) {
            val x = ((ax * time + bx) * time + cx) * time
            if (x < clamped) low = time else high = time
            time = (low + high) / 2
        }
    }
    return ((ay * time + by) * time + cy) * time
}
