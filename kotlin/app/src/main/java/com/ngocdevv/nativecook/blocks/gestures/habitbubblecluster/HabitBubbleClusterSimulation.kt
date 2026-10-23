package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object HabitBubbleClusterEvent {
    const val Tick = 1
    const val Done = 2
    const val Undo = 3
    const val Grab = 4
    const val Count = 5
}

internal data class HabitBubbleClusterCount(val done: Int, val total: Int)

internal class HabitBubbleClusterSimulation {
    private val tokens = HabitBubbleClusterTokens
    val entities = tokens.Entities
    val quietIndex = tokens.QuietIndex

    val x = DoubleArray(entities.size)
    val y = DoubleArray(entities.size)
    val velocityX = DoubleArray(entities.size)
    val velocityY = DoubleArray(entities.size)
    val radius = DoubleArray(entities.size)
    private val radiusVelocity = DoubleArray(entities.size)
    val radiusTarget = DoubleArray(entities.size)
    private val homeX = DoubleArray(entities.size)
    private val homeY = DoubleArray(entities.size)
    val done = DoubleArray(entities.size)
    val doneTarget = DoubleArray(entities.size)
    private val pendingRadius = DoubleArray(entities.size) { -1.0 }
    private val pendingAt = DoubleArray(entities.size)
    val events = mutableListOf<Int>()

    var tab = 0
        private set
    var held = -1
        private set
    var mode = 0
        private set
    var pressMs = 0.0
        private set
    var charge = 0.0
        private set
    var ring = -1
        private set
    var fingerX = 0.0
        private set
    var fingerY = 0.0
        private set
    var burst = -1
        private set
    var burstMs = 0.0
        private set
    var clockMs = 0.0
        private set

    private var ticks = 0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var startX = 0.0
    private var startY = 0.0
    private var remainderMs = 0.0

    init {
        reset()
    }

    private fun baseRadius(index: Int, selectedTab: Int, completed: Boolean): Double {
        val entity = entities[index]
        if (entity.set != selectedTab) return 0.0
        return if (completed) entity.habit.radius * HabitBubbleClusterTokens.Hold.doneScale else entity.habit.radius
    }

    fun reset() {
        tab = 0
        held = -1
        mode = 0
        pressMs = 0.0
        charge = 0.0
        ring = -1
        fingerX = 0.0
        fingerY = 0.0
        burst = -1
        burstMs = 0.0
        clockMs = 0.0
        remainderMs = 0.0
        ticks = 0
        entities.indices.forEach { index ->
            val entity = entities[index]
            val target = baseRadius(index, 0, completed = false)
            x[index] = entity.habit.x
            y[index] = entity.habit.y
            velocityX[index] = 0.0
            velocityY[index] = 0.0
            radius[index] = target
            radiusVelocity[index] = 0.0
            radiusTarget[index] = target
            homeX[index] = entity.habit.x
            homeY[index] = entity.habit.y
            done[index] = 0.0
            doneTarget[index] = 0.0
            pendingRadius[index] = -1.0
            pendingAt[index] = 0.0
        }
        packHomes()
        entities.indices.forEach { index ->
            x[index] = homeX[index]
            y[index] = homeY[index]
        }
        events.clear()
        events += HabitBubbleClusterEvent.Count
    }

    private fun packHomes() {
        val packedX = DoubleArray(entities.size) { entities[it].habit.x }
        val packedY = DoubleArray(entities.size) { entities[it].habit.y }
        val active = entities.indices.count { radiusTarget[it] > 0.0 && !entities[it].quiet }
        val changed = entities.indices.any {
            radiusTarget[it] > 0.0 && abs(radiusTarget[it] - entities[it].habit.radius) > 0.5
        }
        val iterations = if (active > 1 && changed) HabitBubbleClusterTokens.Physics.relaxIterations else 0
        repeat(iterations) {
            entities.indices.forEach { index ->
                if (radiusTarget[index] <= 0.0 || entities[index].quiet) return@forEach
                packedX[index] -= packedX[index] * HabitBubbleClusterTokens.Physics.relaxGravity
                packedY[index] -= packedY[index] * HabitBubbleClusterTokens.Physics.relaxGravity
            }
            for (first in entities.indices) {
                val firstRadius = radiusTarget[first]
                if (firstRadius <= 0.0) continue
                for (second in first + 1 until entities.size) {
                    val secondRadius = radiusTarget[second]
                    if (secondRadius <= 0.0) continue
                    val dx = packedX[second] - packedX[first]
                    val dy = packedY[second] - packedY[first]
                    val distance = max(0.001, hypot(dx, dy))
                    val minimum = firstRadius + secondRadius + HabitBubbleClusterTokens.Layout.gap
                    if (distance >= minimum) continue
                    val firstWeight = if (entities[first].quiet) {
                        0.0
                    } else {
                        secondRadius * secondRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                    }
                    val secondWeight = if (entities[second].quiet) {
                        0.0
                    } else {
                        firstRadius * firstRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                    }
                    val sum = firstWeight + secondWeight
                    val normalizer = if (sum == 0.0) 1.0 else sum
                    val push = (minimum - distance) / distance
                    packedX[first] -= dx * push * firstWeight / normalizer
                    packedY[first] -= dy * push * firstWeight / normalizer
                    packedX[second] += dx * push * secondWeight / normalizer
                    packedY[second] += dy * push * secondWeight / normalizer
                }
            }
        }
        entities.indices.forEach { index ->
            homeX[index] = packedX[index]
            homeY[index] = packedY[index]
        }
    }

    fun setDone(index: Int, completed: Boolean, reducedMotion: Boolean) {
        if (index !in entities.indices || entities[index].quiet) return
        doneTarget[index] = if (completed) 1.0 else 0.0
        radiusTarget[index] = baseRadius(index, tab, completed)
        if (completed && !reducedMotion) {
            burst = index
            burstMs = 0.0
            entities.indices.forEach { other ->
                if (other == index || radiusTarget[other] <= 0.0 || entities[other].quiet) return@forEach
                val dx = x[other] - x[index]
                val dy = y[other] - y[index]
                val distance = max(1.0, hypot(dx, dy))
                val kick = HabitBubbleClusterTokens.Wave.speed * exp(-distance / HabitBubbleClusterTokens.Wave.reach)
                velocityX[other] += dx / distance * kick
                velocityY[other] += dy / distance * kick
            }
        }
        packHomes()
        events += if (completed) HabitBubbleClusterEvent.Done else HabitBubbleClusterEvent.Undo
        events += HabitBubbleClusterEvent.Count
    }

    fun setTab(nextTab: Int) {
        if (nextTab == tab || nextTab !in 0..1) return
        tab = nextTab
        entities.indices.forEach { index ->
            val target = baseRadius(index, nextTab, doneTarget[index] > 0.5)
            val entity = entities[index].habit
            val delay = hypot(entity.x, entity.y) / HabitBubbleClusterTokens.Stagger.per * HabitBubbleClusterTokens.Stagger.stepMs
            pendingRadius[index] = target
            pendingAt[index] = clockMs + delay
        }
        val previous = radiusTarget.copyOf()
        entities.indices.forEach { radiusTarget[it] = pendingRadius[it] }
        packHomes()
        entities.indices.forEach { radiusTarget[it] = previous[it] }
        events += HabitBubbleClusterEvent.Count
    }

    private fun hitTest(touchX: Double, touchY: Double): Int {
        var best = -1
        var bestDistance = Double.POSITIVE_INFINITY
        entities.indices.forEach { index ->
            if (entities[index].quiet || radiusTarget[index] <= 0.0) return@forEach
            val distance = hypot(touchX - x[index], touchY - y[index])
            if (distance < max(radius[index], 22.0) && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    fun touchDown(touchX: Double, touchY: Double) {
        held = hitTest(touchX, touchY)
        mode = 0
        pressMs = 0.0
        charge = 0.0
        ticks = 0
        fingerX = touchX
        fingerY = touchY
        startX = touchX
        startY = touchY
        if (held >= 0) {
            offsetX = x[held] - touchX
            offsetY = y[held] - touchY
            events += HabitBubbleClusterEvent.Grab
        }
    }

    fun touchMove(touchX: Double, touchY: Double) {
        fingerX = touchX
        fingerY = touchY
        if (held < 0 || mode == 2) return
        if (hypot(touchX - startX, touchY - startY) > HabitBubbleClusterTokens.Hold.moveTolerance) {
            mode = 2
        }
    }

    fun touchUp(releaseVelocityX: Double, releaseVelocityY: Double, reducedMotion: Boolean): Int {
        val index = held
        held = -1
        if (index < 0) return -1
        if (mode == 2) {
            if (!reducedMotion) {
                val speed = hypot(releaseVelocityX, releaseVelocityY)
                val factor = if (speed > HabitBubbleClusterTokens.Physics.maxFling) HabitBubbleClusterTokens.Physics.maxFling / speed else 1.0
                velocityX[index] = releaseVelocityX * factor
                velocityY[index] = releaseVelocityY * factor
            }
            return -1
        }
        if (pressMs < HabitBubbleClusterTokens.Hold.tapMs && doneTarget[index] > 0.5) {
            setDone(index, completed = false, reducedMotion)
            return -1
        }
        return if (pressMs < HabitBubbleClusterTokens.Hold.tapMs) index else -1
    }

    fun step(frameMs: Double, reducedMotion: Boolean) {
        remainderMs += min(frameMs, HabitBubbleClusterTokens.Physics.maxFrameMs)
        val seconds = HabitBubbleClusterTokens.Physics.step
        val stepMs = seconds * 1000.0
        while (remainderMs >= stepMs - 0.000001) {
            remainderMs -= stepMs
            clockMs += stepMs
            stepHold(stepMs, reducedMotion)
            entities.indices.forEach { index ->
                if (pendingRadius[index] >= 0.0 && clockMs >= pendingAt[index]) {
                    radiusTarget[index] = pendingRadius[index]
                    pendingRadius[index] = -1.0
                }
            }
            stepEntities(seconds, reducedMotion)
            separateCollisions(seconds)
            if (burst >= 0) {
                burstMs += stepMs
                if (burstMs > HabitBubbleClusterTokens.Ripple.durationMs) burst = -1
            }
        }
    }

    private fun stepHold(stepMs: Double, reducedMotion: Boolean) {
        if (held >= 0 && mode != 2) {
            pressMs += stepMs
            if (pressMs >= HabitBubbleClusterTokens.Hold.delayMs && doneTarget[held] < 0.5) {
                mode = 1
                ring = held
                charge = min(1.0, charge + stepMs / HabitBubbleClusterTokens.Hold.chargeMs)
                val tick = when {
                    charge >= HabitBubbleClusterTokens.Hold.ticks[1] -> 2
                    charge >= HabitBubbleClusterTokens.Hold.ticks[0] -> 1
                    else -> 0
                }
                if (tick > ticks) {
                    ticks = tick
                    events += HabitBubbleClusterEvent.Tick
                }
                if (charge >= 1.0) {
                    setDone(held, completed = true, reducedMotion)
                    held = -1
                    mode = 0
                }
            }
        } else if (charge > 0.0 && (held < 0 || mode == 2)) {
            charge = if (charge >= 1.0) 0.0 else max(0.0, charge - stepMs / HabitBubbleClusterTokens.Hold.drainMs)
        }
    }

    private fun stepEntities(seconds: Double, reducedMotion: Boolean) {
        entities.indices.forEach { index ->
            val squeeze = if (index == held && mode != 2 && !reducedMotion) HabitBubbleClusterTokens.Hold.squeeze else 1.0
            val cycles = HabitBubbleClusterTokens.Idle.cyclesMin + index % 3
            val breathe = if (reducedMotion) {
                0.0
            } else {
                sin(clockMs / (tokens.LoopMs.toDouble() / cycles) * PI * 2.0 + index) * HabitBubbleClusterTokens.Idle.breathe
            }
            val targetRadius = radiusTarget[index] * squeeze * (1.0 + breathe)
            if (reducedMotion) {
                radius[index] = approach(radius[index], targetRadius, seconds * 18.0)
                radiusVelocity[index] = 0.0
            } else {
                radiusVelocity[index] += (
                    HabitBubbleClusterTokens.Physics.radiusStiffness * (targetRadius - radius[index]) -
                        HabitBubbleClusterTokens.Physics.radiusDamping * radiusVelocity[index]
                    ) * seconds
                radius[index] = max(0.0, radius[index] + radiusVelocity[index] * seconds)
            }
            done[index] = approach(done[index], doneTarget[index], seconds * if (reducedMotion) 14.0 else 9.0)

            if (index == held && mode == 2) {
                val nextX = fingerX + offsetX
                val nextY = fingerY + offsetY
                velocityX[index] = (nextX - x[index]) / seconds
                velocityY[index] = (nextY - y[index]) / seconds
                x[index] = nextX
                y[index] = nextY
                return@forEach
            }
            if (reducedMotion) {
                x[index] = approach(x[index], homeX[index], seconds * 18.0)
                y[index] = approach(y[index], homeY[index], seconds * 18.0)
                velocityX[index] = 0.0
                velocityY[index] = 0.0
                return@forEach
            }
            velocityX[index] += (
                HabitBubbleClusterTokens.Physics.stiffness * (homeX[index] - x[index]) -
                    HabitBubbleClusterTokens.Physics.damping * velocityX[index]
                ) * seconds
            velocityY[index] += (
                HabitBubbleClusterTokens.Physics.stiffness * (homeY[index] - y[index]) -
                    HabitBubbleClusterTokens.Physics.damping * velocityY[index]
                ) * seconds
            x[index] += velocityX[index] * seconds
            y[index] += velocityY[index] * seconds
        }
    }

    private fun separateCollisions(seconds: Double) {
        for (first in entities.indices) {
            val firstRadius = radius[first]
            if (firstRadius <= 0.5) continue
            for (second in first + 1 until entities.size) {
                val secondRadius = radius[second]
                if (secondRadius <= 0.5) continue
                val dx = x[second] - x[first]
                val dy = y[second] - y[first]
                val distance = max(0.001, hypot(dx, dy))
                val atDesignedSize =
                    abs(radiusTarget[first] - entities[first].habit.radius) < 0.5 &&
                        abs(radiusTarget[second] - entities[second].habit.radius) < 0.5
                val designedDistance = if (atDesignedSize) {
                    hypot(
                        entities[second].habit.x - entities[first].habit.x,
                        entities[second].habit.y - entities[first].habit.y,
                    )
                } else {
                    Double.POSITIVE_INFINITY
                }
                val minimum = min(firstRadius + secondRadius + HabitBubbleClusterTokens.Layout.gap, designedDistance)
                if (distance >= minimum) continue
                val firstFixed = first == held && mode == 2 || entities[first].quiet
                val secondFixed = second == held && mode == 2 || entities[second].quiet
                if (firstFixed && secondFixed) continue
                val firstWeight = when {
                    firstFixed -> 0.0
                    secondFixed -> 1.0
                    else -> secondRadius * secondRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                }
                val secondWeight = when {
                    secondFixed -> 0.0
                    firstFixed -> 1.0
                    else -> firstRadius * firstRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                }
                val push = (minimum - distance) / distance * HabitBubbleClusterTokens.Physics.separation
                x[first] -= dx * push * firstWeight
                y[first] -= dy * push * firstWeight
                x[second] += dx * push * secondWeight
                y[second] += dy * push * secondWeight
                velocityX[first] -= dx * push * firstWeight / seconds * 0.25
                velocityY[first] -= dy * push * firstWeight / seconds * 0.25
                velocityX[second] += dx * push * secondWeight / seconds * 0.25
                velocityY[second] += dy * push * secondWeight / seconds * 0.25
            }
        }
    }

    fun count(): HabitBubbleClusterCount {
        var completed = 0
        var total = 0
        entities.indices.forEach { index ->
            if (entities[index].set != tab || entities[index].quiet) return@forEach
            total += 1
            if (doneTarget[index] > 0.5) completed += 1
        }
        return HabitBubbleClusterCount(completed, total)
    }

    fun indexOf(id: String): Int = entities.indexOfFirst { it.habit.id == id }

    fun setClock(value: Double) {
        clockMs = value
    }

    private fun approach(value: Double, target: Double, rate: Double): Double {
        return value + (target - value) * min(1.0, rate)
    }
}
