package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PaddockWidgetBentoController {
    private data class PositionTransition(
        val from: Map<PaddockWidgetBentoCardID, Double>,
        val to: Map<PaddockWidgetBentoCardID, Double>,
        val velocity: Map<PaddockWidgetBentoCardID, Double>,
        var startedAt: Double,
        val duration: Double,
        val stiffness: Double,
        val damping: Double,
        val mass: Double,
        val quietFade: Boolean,
        val selectedID: PaddockWidgetBentoCardID?,
        val selectedScaleFrom: Double,
        val selectedShadowFrom: Double,
        val selectedRotationFrom: Double,
    ) {
        data class Sample(
            val positions: Map<PaddockWidgetBentoCardID, Double>,
            val velocities: Map<PaddockWidgetBentoCardID, Double>,
            val opacity: Map<PaddockWidgetBentoCardID, Double>,
        )

        fun sample(now: Double): Sample {
            val elapsed = max(0.0, now - startedAt)
            val positions = mutableMapOf<PaddockWidgetBentoCardID, Double>()
            val velocities = mutableMapOf<PaddockWidgetBentoCardID, Double>()
            val opacity = mutableMapOf<PaddockWidgetBentoCardID, Double>()
            for (id in PaddockWidgetBentoCardID.entries) {
                val start = from[id] ?: to[id] ?: PaddockWidgetBentoMotion.slotY[0]
                val target = to[id] ?: start
                val delta = target - start
                val moves = abs(delta) > 0.001
                if (elapsed >= duration) {
                    positions[id] = target
                    velocities[id] = 0.0
                    opacity[id] = 1.0
                } else if (quietFade) {
                    val p = PaddockWidgetBentoMotion.clamp(elapsed / duration)
                    positions[id] = start + delta * PaddockWidgetBentoMotion.smooth(p)
                    velocities[id] = if (duration > 0.0) delta * 6.0 * p * (1.0 - p) / duration else 0.0
                    opacity[id] = if (moves) {
                        if (p < 0.5) 1.0 - p * 2.0 else (p - 0.5) * 2.0
                    } else {
                        1.0
                    }
                } else {
                    val value = springValue(
                        from = start,
                        to = target,
                        velocity = velocity[id] ?: 0.0,
                        elapsed = elapsed,
                        stiffness = stiffness,
                        damping = damping,
                        mass = mass,
                    )
                    positions[id] = value.first
                    velocities[id] = value.second
                    opacity[id] = 1.0
                }
            }
            return Sample(
                positions = positions.toMap(),
                velocities = velocities.toMap(),
                opacity = opacity.toMap(),
            )
        }

        private fun springValue(
            from: Double,
            to: Double,
            velocity: Double,
            elapsed: Double,
            stiffness: Double,
            damping: Double,
            mass: Double,
        ): Pair<Double, Double> {
            val omega = sqrt(stiffness / mass)
            val zeta = damping / (2.0 * sqrt(stiffness * mass))
            val displacement = from - to
            if (zeta < 1.0) {
                val damped = omega * sqrt(1.0 - zeta * zeta)
                val coefficient = (velocity + zeta * omega * displacement) / damped
                val exponential = exp(-zeta * omega * elapsed)
                val cosine = cos(damped * elapsed)
                val sine = sin(damped * elapsed)
                val position = to + exponential * (displacement * cosine + coefficient * sine)
                val speed = exponential * (
                    -zeta * omega * (displacement * cosine + coefficient * sine) +
                        damped * (-displacement * sine + coefficient * cosine)
                    )
                return position to speed
            }
            val c = velocity + omega * displacement
            val exponential = exp(-omega * elapsed)
            val position = to + exponential * (displacement + c * elapsed)
            val speed = exponential * (c - omega * (displacement + c * elapsed))
            return position to speed
        }
    }

    var order: List<PaddockWidgetBentoCardID> = PaddockWidgetBentoMotion.defaultOrder
        private set
    var isActive = false
        private set
    var isVisible = false
        private set
    var isQuiet = false
        private set
    var autoPlay = false
        private set
    var isEditing = false
        private set
    var draggedID: PaddockWidgetBentoCardID? = null
        private set
    var dragY: Double? = null
        private set
    var candidateIndex: Int? = null
        private set

    private var timelineAnchor = 0.0
    private var frozenAt: Double? = null
    private var pressID: PaddockWidgetBentoCardID? = null
    private var pressOwner: Long? = null
    private var pressStartedAt: Double? = null
    private var pressFrame: PaddockWidgetBentoFrame? = null
    private var dragOwner: Long? = null
    private var pointerOffset = 0.0
    private var dragOriginOrder: List<PaddockWidgetBentoCardID>? = null
    private var dragVisualOrder: List<PaddockWidgetBentoCardID>? = null

    /** Slot positions the neighbours were last sent to while a card is held. */
    private var dragSlots: Map<PaddockWidgetBentoCardID, Double>? = null
    private var restartAt: Double? = null
    private var transition: PositionTransition? = null

    val isDragging: Boolean
        get() = draggedID != null
    val hasPendingPress: Boolean
        get() = pressID != null && !isDragging
    val hasValidOrder: Boolean
        get() = PaddockWidgetBentoMotion.isValid(order)

    fun appear(autoPlay: Boolean, quiet: Boolean, now: Double) {
        this.autoPlay = autoPlay
        isQuiet = quiet
        isActive = true
        isVisible = true
        isEditing = false
        clearGesture()
        transition = null
        restartAt = null
        frozenAt = null
        timelineAnchor = now
    }

    fun setActive(active: Boolean, now: Double) {
        if (active == isActive) {
            return
        }
        if (!active) {
            cancelImmediately()
            freeze(now)
            isActive = false
        } else {
            isActive = true
            unfreezeIfEligible(now)
        }
    }

    fun setVisible(visible: Boolean, now: Double) {
        if (visible == isVisible) {
            return
        }
        if (!visible) {
            cancelImmediately()
            freeze(now)
            isVisible = false
        } else {
            isVisible = true
            unfreezeIfEligible(now)
        }
    }

    fun setQuiet(quiet: Boolean, now: Double) {
        if (quiet == isQuiet) {
            return
        }
        if (quiet) {
            if (isDragging) {
                retargetDrag(now, true)
            } else {
                cancelImmediately()
            }
            freeze(now)
            isQuiet = true
        } else {
            isQuiet = false
            if (isDragging) {
                unfreezeIfEligible(now, false)
                retargetDrag(now, false)
            } else {
                unfreezeIfEligible(now)
            }
        }
    }

    fun setAutoPlay(enabled: Boolean, now: Double) {
        if (enabled == autoPlay) {
            return
        }
        autoPlay = enabled
        restartAt = null
        timelineAnchor = now
        frozenAt = if (enabled && (!isActive || !isVisible || isQuiet || isEditing)) now else null
    }

    fun setEditing(editing: Boolean, now: Double) {
        if (editing == isEditing) {
            return
        }
        if (editing) {
            pressFrame = null
            clearPress()
            freeze(now)
            isEditing = true
        } else {
            cancelDrag(now)
            isEditing = false
            timelineAnchor = now
            frozenAt = if (!isActive || !isVisible || isQuiet) now else null
            restartAt = if (autoPlay) now + 2.0 else null
        }
    }

    fun beginPress(id: PaddockWidgetBentoCardID, y: Double, owner: Long, now: Double) {
        if (!isActive || !isVisible || !order.contains(id)) {
            return
        }
        val activePressOwner = pressOwner
        if (activePressOwner != null) {
            if (activePressOwner == owner) {
                return
            }
            cancelDrag(now)
            return
        }
        if (draggedID != null) {
            return
        }
        if (!isEditing && autoPlay && !isQuiet) {
            val scheduledRestart = restartAt
            pressFrame = if (scheduledRestart != null && now < scheduledRestart) {
                PaddockWidgetBentoMotion.restFrame(order)
            } else {
                demoFrame(now)
            }
            freeze(now)
        } else {
            pressFrame = null
        }
        pressID = id
        pressOwner = owner
        pressStartedAt = now
        dragY = y
    }

    fun beginDrag(id: PaddockWidgetBentoCardID, y: Double, owner: Long, now: Double) {
        if (!isActive || !isVisible || draggedID != null) {
            return
        }
        val startedAt = pressStartedAt
        if (
            pressID != id ||
            pressOwner != owner ||
            startedAt == null ||
            now - startedAt < PaddockWidgetBentoMotion.longPressDuration ||
            !order.contains(id)
        ) {
            return
        }
        val visibleFrame = pressFrame
        val visualOrder = visibleFrame?.order ?: order
        val index = visualOrder.indexOf(id)
        if (index < 0) {
            return
        }
        isEditing = true
        dragOriginOrder = order
        dragVisualOrder = visualOrder
        dragSlots = slotPositions(visualOrder)
        draggedID = id
        dragOwner = owner
        candidateIndex = index
        val visibleY = visibleFrame?.cardY?.get(id) ?: PaddockWidgetBentoMotion.slotY[index]
        pointerOffset = y - visibleY
        dragY = y
        pressFrame = null
        transition = null
    }

    fun updateDrag(y: Double, owner: Long, now: Double) {
        val current = candidateIndex
        if (!isDragging || dragOwner != owner || current == null) {
            return
        }
        dragY = y
        val center = y - pointerOffset
        var target = current
        while (target < 2) {
            val boundary = (
                PaddockWidgetBentoMotion.slotY[target] +
                    PaddockWidgetBentoMotion.slotY[target + 1]
                ) / 2.0
            if (center > boundary + PaddockWidgetBentoMotion.targetHysteresis) {
                target += 1
            } else {
                break
            }
        }
        while (target > 0) {
            val boundary = (
                PaddockWidgetBentoMotion.slotY[target - 1] +
                    PaddockWidgetBentoMotion.slotY[target]
                ) / 2.0
            if (center < boundary - PaddockWidgetBentoMotion.targetHysteresis) {
                target -= 1
            } else {
                break
            }
        }
        if (target == current) {
            return
        }
        candidateIndex = target
        retargetDrag(now, isQuiet)
    }

    fun endDrag(owner: Long, now: Double): Boolean {
        val activePressOwner = pressOwner
        if (activePressOwner == null || activePressOwner != owner) {
            return false
        }
        val id = draggedID
        val target = candidateIndex
        if (id == null || dragOwner != owner || target == null) {
            finishShortPress(now)
            return false
        }
        val currentPositions = sampledPositions(now)
        val selectedRotation = currentDragRotation
        val center = (dragY ?: pointerOffset) - pointerOffset
        val original = dragOriginOrder ?: order
        val visualOrder = dragVisualOrder ?: original
        val minimum = PaddockWidgetBentoMotion.slotY[0] - PaddockWidgetBentoMotion.outsideCancelDistance
        val maximum = PaddockWidgetBentoMotion.slotY[2] + 62.0 + PaddockWidgetBentoMotion.outsideCancelDistance
        if (center < minimum || center > maximum) {
            order = original
            clearGesture()
            beginTransition(
                from = currentPositions,
                to = slotPositions(original),
                now = now,
                settling = true,
                selectedID = id,
                selectedScale = if (isQuiet) 1.0 else 1.025,
                selectedShadow = if (isQuiet) 4.0 else 14.0,
                selectedRotation = selectedRotation,
            )
            return false
        }

        val updated = original.toMutableList()
        val visualSource = visualOrder.indexOf(id).takeIf { it >= 0 }
        if (visualSource != target) {
            val source = updated.indexOf(id)
            if (source >= 0) {
                updated.removeAt(source)
                updated.add(min(target, updated.size), id)
            }
        }
        val changed = updated != original
        order = updated.toList()
        clearGesture()
        beginTransition(
            from = currentPositions,
            to = slotPositions(updated),
            now = now,
            settling = true,
            selectedID = id,
            selectedScale = if (isQuiet) 1.0 else 1.025,
            selectedShadow = if (isQuiet) 4.0 else 14.0,
            selectedRotation = selectedRotation,
        )
        return changed
    }

    fun cancelDrag(now: Double) {
        if (!hasPendingPress && !isDragging) {
            return
        }
        val current = sampledPositions(now)
        val original = dragOriginOrder ?: order
        val wasEditing = isEditing
        val selected = draggedID
        val scale = if (selected == null || isQuiet) 1.0 else 1.025
        val shadow = if (selected == null || isQuiet) 4.0 else 14.0
        val rotation = if (selected == null) 0.0 else currentDragRotation
        order = original
        clearGesture()
        if (isActive && isVisible) {
            beginTransition(
                from = current,
                to = slotPositions(order),
                now = now,
                settling = true,
                selectedID = selected,
                selectedScale = scale,
                selectedShadow = shadow,
                selectedRotation = rotation,
            )
        } else {
            transition = null
        }
        if (!wasEditing && autoPlay) {
            restartAt = now + 2.0
            timelineAnchor = now
            frozenAt = null
        }
    }

    fun move(id: PaddockWidgetBentoCardID, offset: Int, now: Double): Boolean {
        val current = order.indexOf(id)
        if (!isEditing || isDragging || offset == 0 || current < 0) {
            return false
        }
        val from = sampledPositions(now)
        val target = min(order.size - 1, max(0, current + offset))
        if (target == current) {
            return false
        }
        val updated = order.toMutableList()
        updated.removeAt(current)
        updated.add(target, id)
        if (!PaddockWidgetBentoMotion.isValid(updated)) {
            return false
        }
        order = updated.toList()
        beginTransition(
            from = from,
            to = slotPositions(updated),
            now = now,
            settling = true,
        )
        return true
    }

    /**
     * A touch on the pit stop or the sticker: the demo rests until [until] (the touch's own effect plays meanwhile),
     * then starts again from the top after the restart delay. Arranging cards keeps its own rules.
     */
    fun holdDemo(until: Double) {
        if (isEditing) return
        restartAt = if (autoPlay) until + PaddockWidgetBentoMotion.restartDelay else null
    }

    fun done(now: Double) {
        if (!isEditing) {
            return
        }
        if (isDragging || hasPendingPress) {
            cancelDrag(now)
        }
        isEditing = false
        clearPress()
        timelineAnchor = now
        frozenAt = if (!isActive || !isVisible || isQuiet) now else null
        restartAt = if (autoPlay) now + 2.0 else null
    }

    fun frame(now: Double): PaddockWidgetBentoFrame {
        if (isDragging || isEditing || transition != null || hasPendingPress) {
            var result = PaddockWidgetBentoMotion.restFrame(order).copy(
                handlesOpacity = if (isEditing) 1.0 else 0.0,
            )
            val heldFrame = pressFrame
            if (heldFrame != null && !isDragging) {
                result = heldFrame
            }
            val slots = dragSlots
            if (isDragging && slots != null) {
                result = result.copy(cardY = slots)
            }
            val activeTransition = transition
            if (activeTransition != null) {
                val samples = activeTransition.sample(now)
                val progress = PaddockWidgetBentoMotion.smooth(
                    (now - activeTransition.startedAt) / activeTransition.duration,
                )
                result = result.copy(
                    cardY = samples.positions,
                    cardOpacity = samples.opacity,
                    selectedID = activeTransition.selectedID,
                    selectedScale = activeTransition.selectedScaleFrom +
                        (1.0 - activeTransition.selectedScaleFrom) * progress,
                    shadowRadius = activeTransition.selectedShadowFrom +
                        (4.0 - activeTransition.selectedShadowFrom) * progress,
                    rotationDegrees = activeTransition.selectedRotationFrom * (1.0 - progress),
                )
                if (now - activeTransition.startedAt >= activeTransition.duration) {
                    transition = null
                }
            }
            val selected = draggedID
            val pointerY = dragY
            if (selected != null && pointerY != null) {
                val index = candidateIndex ?: 0
                result = result.copy(
                    selectedID = selected,
                    order = candidateOrder(),
                    cardY = result.cardY + (selected to (pointerY - pointerOffset)),
                    selectedScale = if (isQuiet) 1.0 else 1.025,
                    shadowRadius = if (isQuiet) 4.0 else 14.0,
                    rotationDegrees = if (isQuiet) {
                        0.0
                    } else {
                        min(
                            0.7,
                            max(
                                -0.7,
                                (pointerY - (PaddockWidgetBentoMotion.slotY[index] + pointerOffset)) * 0.012,
                            ),
                        )
                    },
                )
            }
            return result.copy(isDemo = false)
        }
        if (!isActive || !isVisible || isQuiet || !autoPlay || frozenAt != null) {
            return PaddockWidgetBentoMotion.restFrame(order)
        }
        val scheduledRestart = restartAt
        if (scheduledRestart != null) {
            if (now < scheduledRestart) {
                return PaddockWidgetBentoMotion.restFrame(order)
            }
            timelineAnchor = now
            restartAt = null
        }
        return demoFrame(now)
    }

    fun advance(now: Double) {
        frame(now)
    }

    fun cadence(now: Double): Double? {
        if (isDragging) {
            return 1.0 / 60.0
        }
        val activeTransition = transition
        if (activeTransition != null) {
            val untilSettle = activeTransition.startedAt + activeTransition.duration - now
            return if (untilSettle > 0.0) min(1.0 / 60.0, untilSettle) else null
        }
        if (!isActive || !isVisible || isQuiet || !autoPlay || isEditing || frozenAt != null) {
            return null
        }
        val scheduledRestart = restartAt
        if (scheduledRestart != null && now < scheduledRestart) {
            return max(0.001, scheduledRestart - now)
        }
        val elapsed = max(0.0, now - timelineAnchor)
        val time = elapsed % PaddockWidgetBentoMotion.loopDuration
        val activeRanges = PaddockWidgetBentoMotion.activeRanges
        if (activeRanges.any { time >= it.first && time < it.second }) {
            return 1.0 / 60.0
        }
        val boundaries = activeRanges.flatMap { listOf(it.first, it.second) }
        val nextBoundary = boundaries.firstOrNull { it > time }
        val next = nextBoundary ?: (PaddockWidgetBentoMotion.loopDuration + activeRanges[0].first)
        return max(0.001, next - time)
    }

    private fun demoFrame(now: Double): PaddockWidgetBentoFrame {
        return PaddockWidgetBentoMotion.sample(
            elapsed = max(0.0, now - timelineAnchor),
            order = order,
        )
    }

    private fun slotPositions(order: List<PaddockWidgetBentoCardID>): Map<PaddockWidgetBentoCardID, Double> {
        return order.mapIndexed { index, id -> id to PaddockWidgetBentoMotion.slotY[index] }.toMap()
    }

    private fun candidateOrder(): List<PaddockWidgetBentoCardID> {
        val selected = draggedID ?: return order
        val target = candidateIndex ?: return order
        val result = (dragVisualOrder ?: order).toMutableList()
        val source = result.indexOf(selected)
        if (source < 0) {
            return order
        }
        result.removeAt(source)
        result.add(min(target, result.size), selected)
        return result
    }

    private fun sampledPositions(now: Double): Map<PaddockWidgetBentoCardID, Double> {
        var result = transition?.sample(now)?.positions ?: (if (isDragging) dragSlots else null) ?: slotPositions(dragVisualOrder ?: order)
        val heldFrame = pressFrame
        if (heldFrame != null && !isDragging) {
            result = heldFrame.cardY
        }
        val selected = draggedID
        val pointerY = dragY
        if (selected != null && pointerY != null) {
            result = result + (selected to (pointerY - pointerOffset))
        }
        return result
    }

    private fun sampledVelocities(now: Double): Map<PaddockWidgetBentoCardID, Double> {
        return transition?.sample(now)?.velocities
            ?: PaddockWidgetBentoCardID.entries.associateWith { 0.0 }
    }

    private fun retargetDrag(now: Double, quiet: Boolean) {
        val selected = draggedID ?: return
        if (!isDragging) {
            return
        }
        val from = sampledPositions(now).toMutableMap()
        val velocity = sampledVelocities(now).toMutableMap()
        val targets = slotPositions(candidateOrder()).toMutableMap()
        targets.remove(selected)
        from.remove(selected)
        velocity.remove(selected)
        beginTransition(
            from = from,
            to = targets,
            velocity = velocity,
            now = now,
            settling = false,
            quiet = quiet,
        )
        dragSlots = slotPositions(candidateOrder())
    }

    private fun beginTransition(
        from: Map<PaddockWidgetBentoCardID, Double>,
        to: Map<PaddockWidgetBentoCardID, Double>,
        velocity: Map<PaddockWidgetBentoCardID, Double> = emptyMap(),
        now: Double,
        settling: Boolean,
        quiet: Boolean? = null,
        selectedID: PaddockWidgetBentoCardID? = null,
        selectedScale: Double = 1.0,
        selectedShadow: Double = 4.0,
        selectedRotation: Double = 0.0,
    ) {
        val moving = PaddockWidgetBentoCardID.entries.any { id ->
            abs((from[id] ?: to[id] ?: PaddockWidgetBentoMotion.slotY[0]) - (to[id] ?: from[id] ?: PaddockWidgetBentoMotion.slotY[0])) > 0.001
        }
        val selectedMoves = selectedID != null && (
            abs(selectedScale - 1.0) > 0.001 ||
                abs(selectedShadow - 4.0) > 0.001 ||
                abs(selectedRotation) > 0.001
            )
        if (!moving && !selectedMoves) {
            transition = null
            return
        }
        val useQuiet = quiet ?: isQuiet
        transition = PositionTransition(
            from = from.toMap(),
            to = to.toMap(),
            velocity = velocity.toMap(),
            startedAt = now,
            duration = if (useQuiet) {
                PaddockWidgetBentoMotion.quietReflowDuration
            } else {
                PaddockWidgetBentoMotion.springSettleDuration
            },
            stiffness = if (settling) {
                PaddockWidgetBentoMotion.settleStiffness
            } else {
                PaddockWidgetBentoMotion.reflowStiffness
            },
            damping = if (settling) {
                PaddockWidgetBentoMotion.settleDamping
            } else {
                PaddockWidgetBentoMotion.reflowDamping
            },
            mass = if (settling) {
                PaddockWidgetBentoMotion.settleMass
            } else {
                PaddockWidgetBentoMotion.reflowMass
            },
            quietFade = useQuiet,
            selectedID = selectedID,
            selectedScaleFrom = selectedScale,
            selectedShadowFrom = selectedShadow,
            selectedRotationFrom = selectedRotation,
        )
    }

    private fun finishShortPress(now: Double) {
        if (!hasPendingPress) {
            return
        }
        val from = sampledPositions(now)
        val sourceFrame = pressFrame
        clearGesture()
        beginTransition(
            from = from,
            to = slotPositions(order),
            now = now,
            settling = true,
            selectedID = sourceFrame?.selectedID,
            selectedScale = sourceFrame?.selectedScale ?: 1.0,
            selectedShadow = sourceFrame?.shadowRadius ?: 4.0,
            selectedRotation = sourceFrame?.rotationDegrees ?: 0.0,
        )
        if (autoPlay) {
            restartAt = now + 2.0
            timelineAnchor = now
            frozenAt = null
        }
    }

    private fun cancelImmediately() {
        order = dragOriginOrder ?: order
        clearGesture()
        transition = null
    }

    private fun clearGesture() {
        draggedID = null
        dragY = null
        candidateIndex = null
        dragOwner = null
        dragOriginOrder = null
        dragVisualOrder = null
        dragSlots = null
        pointerOffset = 0.0
        pressFrame = null
        clearPress()
    }

    private fun clearPress() {
        pressID = null
        pressOwner = null
        pressStartedAt = null
    }

    private val currentDragRotation: Double
        get() {
            val pointerY = dragY ?: return 0.0
            val index = candidateIndex ?: return 0.0
            if (isQuiet) {
                return 0.0
            }
            return min(
                0.7,
                max(
                    -0.7,
                    (pointerY - (PaddockWidgetBentoMotion.slotY[index] + pointerOffset)) * 0.012,
                ),
            )
        }

    private fun freeze(now: Double) {
        if (frozenAt != null) {
            return
        }
        frozenAt = now
    }

    private fun unfreeze(now: Double, shiftTransition: Boolean = true) {
        val freezeTime = frozenAt ?: return
        val pause = max(0.0, now - freezeTime)
        timelineAnchor += pause
        restartAt = restartAt?.plus(pause)
        if (shiftTransition) {
            val activeTransition = transition
            if (activeTransition != null) {
                activeTransition.startedAt += pause
            }
        }
        frozenAt = null
    }

    private fun unfreezeIfEligible(now: Double, shiftTransition: Boolean = true) {
        if (!isActive || !isVisible || isQuiet || isEditing) {
            return
        }
        unfreeze(now, shiftTransition)
    }
}
