package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class HabitBubbleEntity(
    val habit: Tokens.Habit,
    val set: Int,
    val quiet: Boolean = false,
)

internal data class HabitBubbleCount(
    val done: Int,
    val total: Int,
)

internal object HabitBubbleEvent {
    const val TICK = 1
    const val DONE = 2
    const val UNDO = 3
    const val GRAB = 4
    const val COUNT = 5
}

internal class HabitBubbleClusterSimulation {
    val entities = buildList {
        Tokens.SOCIAL.forEach { add(HabitBubbleEntity(it, 0)) }
        add(
            HabitBubbleEntity(
                Tokens.Habit(
                    id = "quiet",
                    name = Tokens.QUIET.getValue("title") as String,
                    icon = "moon-outline",
                    hue = "red",
                    r = Tokens.QUIET.getValue("r") as Double,
                    x = Tokens.QUIET.getValue("x") as Double,
                    y = Tokens.QUIET.getValue("y") as Double,
                ),
                set = 0,
                quiet = true,
            ),
        )
        Tokens.PERSONAL.forEach { add(HabitBubbleEntity(it, 1)) }
    }

    val quietIndex = Tokens.SOCIAL.size
    val x = DoubleArray(entities.size)
    val y = DoubleArray(entities.size)
    val vx = DoubleArray(entities.size)
    val vy = DoubleArray(entities.size)
    val radius = DoubleArray(entities.size)
    private val radiusVelocity = DoubleArray(entities.size)
    val radiusTarget = DoubleArray(entities.size)
    private val homeX = DoubleArray(entities.size)
    private val homeY = DoubleArray(entities.size)
    val done = DoubleArray(entities.size)
    val doneTarget = DoubleArray(entities.size)
    val flash = DoubleArray(entities.size) { -1.0 }
    private val pendingRadius = DoubleArray(entities.size) { -1.0 }
    private val pendingAt = DoubleArray(entities.size)
    val events = mutableListOf<Int>()

    var tab = 0
        private set
    var focus = -1
        private set
    var held = -1
        private set
    var mode = 0
        private set
    var pressMs = 0.0
        private set
    var charge = 0.0
        private set
    private var ticks = 0
    var ring = -1
        private set
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var startX = 0.0
    private var startY = 0.0
    var burst = -1
        private set
    var burstMs = 0.0
        private set
    var clockMs = 0.0
        private set
    private var remainderMs = 0.0

    init {
        reset()
    }

    fun reset() {
        tab = 0
        focus = -1
        held = -1
        mode = 0
        pressMs = 0.0
        charge = 0.0
        ticks = 0
        ring = -1
        burst = -1
        burstMs = 0.0
        clockMs = 0.0
        remainderMs = 0.0
        events.clear()
        entities.indices.forEach { index ->
            val value = baseRadius(index, 0, false)
            x[index] = entities[index].habit.x
            y[index] = entities[index].habit.y
            vx[index] = 0.0
            vy[index] = 0.0
            radius[index] = value
            radiusVelocity[index] = 0.0
            radiusTarget[index] = value
            homeX[index] = 0.0
            homeY[index] = 0.0
            done[index] = 0.0
            doneTarget[index] = 0.0
            flash[index] = -1.0
            pendingRadius[index] = -1.0
            pendingAt[index] = 0.0
        }
        packHomes()
        entities.indices.forEach { index ->
            x[index] = homeX[index]
            y[index] = homeY[index]
        }
    }

    private fun baseRadius(index: Int, selectedTab: Int, completed: Boolean): Double {
        val entity = entities[index]
        if (entity.set != selectedTab) {
            return 0.0
        }
        return if (completed) entity.habit.r * Tokens.HOLD.doneScale else entity.habit.r
    }

    private fun packHomes() {
        val packedX = DoubleArray(entities.size) { entities[it].habit.x }
        val packedY = DoubleArray(entities.size) { entities[it].habit.y }
        var active = 0
        var changed = false
        entities.indices.forEach { index ->
            if (radiusTarget[index] > 0.0 && !entities[index].quiet) {
                active += 1
            }
            if (radiusTarget[index] > 0.0 && abs(radiusTarget[index] - entities[index].habit.r) > 0.5) {
                changed = true
            }
        }
        val iterations = if (active > 1 && changed) Tokens.PHYSICS.relaxIterations else 0
        repeat(iterations) {
            entities.indices.forEach { index ->
                if (radiusTarget[index] > 0.0 && !entities[index].quiet) {
                    packedX[index] -= packedX[index] * Tokens.PHYSICS.relaxGravity
                    packedY[index] -= packedY[index] * Tokens.PHYSICS.relaxGravity
                }
            }
            for (first in entities.indices) {
                val firstRadius = radiusTarget[first]
                if (firstRadius <= 0.0) {
                    continue
                }
                for (second in first + 1 until entities.size) {
                    val secondRadius = radiusTarget[second]
                    if (secondRadius <= 0.0) {
                        continue
                    }
                    val dx = packedX[second] - packedX[first]
                    val dy = packedY[second] - packedY[first]
                    val distance = max(0.001, hypot(dx, dy))
                    val minimum = firstRadius + secondRadius + Tokens.LAYOUT.gap
                    if (distance >= minimum) {
                        continue
                    }
                    val firstWeight = if (entities[first].quiet) 0.0 else secondRadius * secondRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                    val secondWeight = if (entities[second].quiet) 0.0 else firstRadius * firstRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                    val sum = max(1.0, firstWeight + secondWeight)
                    val push = (minimum - distance) / distance
                    packedX[first] -= dx * push * firstWeight / sum
                    packedY[first] -= dy * push * firstWeight / sum
                    packedX[second] += dx * push * secondWeight / sum
                    packedY[second] += dy * push * secondWeight / sum
                }
            }
        }
        entities.indices.forEach { index ->
            homeX[index] = packedX[index]
            homeY[index] = packedY[index]
        }
    }

    fun setDone(index: Int, completed: Boolean, reducedMotion: Boolean = false) {
        doneTarget[index] = if (completed) 1.0 else 0.0
        radiusTarget[index] = baseRadius(index, tab, completed)
        if (completed) {
            flash[index] = if (reducedMotion) -1.0 else 0.0
            burst = if (reducedMotion) -1 else index
            burstMs = 0.0
            if (!reducedMotion) {
                entities.indices.forEach { other ->
                    if (other == index || radiusTarget[other] <= 0.0 || entities[other].quiet) {
                        return@forEach
                    }
                    val dx = x[other] - x[index]
                    val dy = y[other] - y[index]
                    val distance = max(1.0, hypot(dx, dy))
                    val kick = Tokens.WAVE.speed * exp(-distance / Tokens.WAVE.reach)
                    vx[other] += dx / distance * kick
                    vy[other] += dy / distance * kick
                }
            }
        }
        packHomes()
        events.add(if (completed) HabitBubbleEvent.DONE else HabitBubbleEvent.UNDO)
        events.add(HabitBubbleEvent.COUNT)
    }

    fun setTab(nextTab: Int) {
        if (tab == nextTab) {
            return
        }
        tab = nextTab
        entities.indices.forEach { index ->
            val target = baseRadius(index, nextTab, doneTarget[index] > 0.5)
            val delay = hypot(entities[index].habit.x, entities[index].habit.y) / Tokens.STAGGER.per * Tokens.STAGGER.stepMs
            pendingRadius[index] = target
            pendingAt[index] = clockMs + delay
        }
        val before = radiusTarget.copyOf()
        entities.indices.forEach { radiusTarget[it] = pendingRadius[it] }
        packHomes()
        entities.indices.forEach { radiusTarget[it] = before[it] }
        events.add(HabitBubbleEvent.COUNT)
    }

    fun openFocus(index: Int, targetX: Double, targetY: Double) {
        focus = index
        entities.indices.forEach { other ->
            radiusTarget[other] = if (other == index) Tokens.DETAIL.radius else 0.0
            if (other != index) {
                val dx = x[other] - x[index]
                val dy = y[other] - y[index]
                val distance = max(1.0, hypot(dx, dy))
                homeX[other] = x[other] + dx / distance * Tokens.SCATTER.distance
                homeY[other] = y[other] + dy / distance * Tokens.SCATTER.distance
            }
        }
        homeX[index] = targetX
        homeY[index] = targetY
    }

    fun closeFocus() {
        focus = -1
        entities.indices.forEach { index ->
            radiusTarget[index] = baseRadius(index, tab, doneTarget[index] > 0.5)
        }
        packHomes()
    }

    private fun hitTest(touchX: Double, touchY: Double): Int {
        var best = -1
        var bestDistance = Double.POSITIVE_INFINITY
        entities.indices.forEach { index ->
            if (entities[index].quiet || radiusTarget[index] <= 0.0) {
                return@forEach
            }
            val distance = hypot(touchX - x[index], touchY - y[index])
            if (distance < max(radius[index], 22.0) && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    fun touchDown(touchX: Double, touchY: Double) {
        if (focus >= 0) {
            held = -1
            return
        }
        val index = hitTest(touchX, touchY)
        held = index
        mode = 0
        pressMs = 0.0
        charge = 0.0
        ticks = 0
        fingerX = touchX
        fingerY = touchY
        startX = touchX
        startY = touchY
        if (index >= 0) {
            offsetX = x[index] - touchX
            offsetY = y[index] - touchY
            events.add(HabitBubbleEvent.GRAB)
        }
    }

    fun touchMove(touchX: Double, touchY: Double) {
        fingerX = touchX
        fingerY = touchY
        if (held < 0 || mode == 2) {
            return
        }
        if (hypot(touchX - startX, touchY - startY) > Tokens.HOLD.moveTolerance) {
            mode = 2
        }
    }

    fun touchUp(releaseVelocityX: Double, releaseVelocityY: Double, reducedMotion: Boolean): Int {
        val index = held
        held = -1
        if (index < 0) {
            return -1
        }
        if (mode == 2) {
            if (!reducedMotion) {
                val speed = hypot(releaseVelocityX, releaseVelocityY)
                val factor = if (speed > Tokens.PHYSICS.maxFling) Tokens.PHYSICS.maxFling / speed else 1.0
                vx[index] = releaseVelocityX * factor
                vy[index] = releaseVelocityY * factor
            }
            return -1
        }
        if (pressMs < Tokens.HOLD.tapMs && doneTarget[index] > 0.5) {
            setDone(index, false, reducedMotion)
            return -1
        }
        return if (pressMs < Tokens.HOLD.tapMs) index else -1
    }

    fun step(frameMs: Double, reducedMotion: Boolean) {
        remainderMs += min(frameMs, Tokens.PHYSICS.maxFrameMs)
        val seconds = Tokens.PHYSICS.step
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
            stepEntities(seconds, stepMs, reducedMotion)
            separateCollisions(seconds)
            if (burst >= 0) {
                burstMs += stepMs
                if (burstMs > Tokens.BURST.sparkMs) {
                    burst = -1
                }
            }
        }
    }

    private fun stepHold(stepMs: Double, reducedMotion: Boolean) {
        if (held >= 0 && mode != 2) {
            pressMs += stepMs
            if (pressMs >= Tokens.HOLD.delayMs && doneTarget[held] < 0.5) {
                mode = 1
                ring = held
                charge = min(1.0, charge + stepMs / Tokens.HOLD.chargeMs)
                val tick = if (charge >= Tokens.HOLD.ticks[1]) 2 else if (charge >= Tokens.HOLD.ticks[0]) 1 else 0
                if (tick > ticks) {
                    ticks = tick
                    events.add(HabitBubbleEvent.TICK)
                }
                if (charge >= 1.0) {
                    setDone(held, true, reducedMotion)
                    held = -1
                    mode = 0
                }
            }
        } else if (charge > 0.0 && (held < 0 || mode == 2)) {
            charge = if (charge >= 1.0) 0.0 else max(0.0, charge - stepMs / Tokens.HOLD.drainMs)
        }
    }

    private fun stepEntities(seconds: Double, stepMs: Double, reducedMotion: Boolean) {
        entities.indices.forEach { index ->
            val squeeze = if (index == held && mode != 2 && !reducedMotion) Tokens.HOLD.squeeze else 1.0
            val cycles = Tokens.IDLE.cyclesMin + index % 3
            val breathe = if (reducedMotion || Tokens.QA_HOLD != null) {
                0.0
            } else {
                sin(clockMs / (Tokens.LOOP_MS.toDouble() / cycles) * PI * 2.0 + index) * Tokens.IDLE.breathe
            }
            val target = radiusTarget[index] * squeeze * (1.0 + breathe)
            if (reducedMotion) {
                radius[index] = approach(radius[index], target, seconds * 18.0)
                radiusVelocity[index] = 0.0
            } else {
                radiusVelocity[index] += (Tokens.PHYSICS.radiusStiffness * (target - radius[index]) - Tokens.PHYSICS.radiusDamping * radiusVelocity[index]) * seconds
                radius[index] = max(0.0, radius[index] + radiusVelocity[index] * seconds)
            }
            done[index] = approach(done[index], doneTarget[index], seconds * if (reducedMotion) 14.0 else 9.0)
            if (flash[index] >= 0.0) {
                flash[index] = if (flash[index] + stepMs > Tokens.BURST.flashMs) -1.0 else flash[index] + stepMs
            }
            if (index == held && mode == 2) {
                val nextX = fingerX + offsetX
                val nextY = fingerY + offsetY
                vx[index] = (nextX - x[index]) / seconds
                vy[index] = (nextY - y[index]) / seconds
                x[index] = nextX
                y[index] = nextY
                return@forEach
            }
            if (reducedMotion) {
                x[index] = approach(x[index], homeX[index], seconds * 18.0)
                y[index] = approach(y[index], homeY[index], seconds * 18.0)
                vx[index] = 0.0
                vy[index] = 0.0
                return@forEach
            }
            vx[index] += (Tokens.PHYSICS.stiffness * (homeX[index] - x[index]) - Tokens.PHYSICS.damping * vx[index]) * seconds
            vy[index] += (Tokens.PHYSICS.stiffness * (homeY[index] - y[index]) - Tokens.PHYSICS.damping * vy[index]) * seconds
            x[index] += vx[index] * seconds
            y[index] += vy[index] * seconds
        }
    }

    private fun separateCollisions(seconds: Double) {
        for (first in entities.indices) {
            val firstRadius = radius[first]
            if (firstRadius <= 0.5) {
                continue
            }
            for (second in first + 1 until entities.size) {
                val secondRadius = radius[second]
                if (secondRadius <= 0.5) {
                    continue
                }
                val dx = x[second] - x[first]
                val dy = y[second] - y[first]
                val distance = max(0.001, hypot(dx, dy))
                val atDesignedSize = abs(radiusTarget[first] - entities[first].habit.r) < 0.5 && abs(radiusTarget[second] - entities[second].habit.r) < 0.5
                val designedDistance = if (atDesignedSize) {
                    hypot(entities[second].habit.x - entities[first].habit.x, entities[second].habit.y - entities[first].habit.y)
                } else {
                    Double.POSITIVE_INFINITY
                }
                val minimum = min(firstRadius + secondRadius + Tokens.LAYOUT.gap, designedDistance)
                if (distance >= minimum) {
                    continue
                }
                val firstFixed = first == held && mode == 2 || entities[first].quiet
                val secondFixed = second == held && mode == 2 || entities[second].quiet
                if (firstFixed && secondFixed) {
                    continue
                }
                val firstWeight = if (firstFixed) 0.0 else if (secondFixed) 1.0 else secondRadius * secondRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                val secondWeight = if (secondFixed) 0.0 else if (firstFixed) 1.0 else firstRadius * firstRadius / (firstRadius * firstRadius + secondRadius * secondRadius)
                val push = (minimum - distance) / distance * Tokens.PHYSICS.separation
                x[first] -= dx * push * firstWeight
                y[first] -= dy * push * firstWeight
                x[second] += dx * push * secondWeight
                y[second] += dy * push * secondWeight
                vx[first] -= dx * push * firstWeight / seconds * 0.25
                vy[first] -= dy * push * firstWeight / seconds * 0.25
                vx[second] += dx * push * secondWeight / seconds * 0.25
                vy[second] += dy * push * secondWeight / seconds * 0.25
            }
        }
    }

    fun doneCount(): HabitBubbleCount {
        var completed = 0
        var total = 0
        entities.indices.forEach { index ->
            if (entities[index].set == tab && !entities[index].quiet) {
                total += 1
                if (doneTarget[index] > 0.5) {
                    completed += 1
                }
            }
        }
        return HabitBubbleCount(completed, total)
    }

    fun isResting(): Boolean {
        return held < 0 && focus < 0 && tab == 0 && doneCount().done == 0
    }

    fun indexOf(id: String): Int {
        return entities.indexOfFirst { it.habit.id == id }
    }

    fun setClock(value: Double) {
        clockMs = value
    }

    private fun approach(value: Double, target: Double, rate: Double): Double {
        return value + (target - value) * min(1.0, rate)
    }
}
