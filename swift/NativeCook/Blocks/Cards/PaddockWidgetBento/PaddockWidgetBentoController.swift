import Foundation

/// Authoritative component-lifetime arrangement, press/drag, settling and demo controller.
/// Demo order is ephemeral; order changes only on a completed user move.
struct PaddockWidgetBentoController {
    typealias CardID = PaddockWidgetBentoCardID
    typealias Frame = PaddockWidgetBentoMotion.Frame

    private struct PositionTransition {
        var from: [CardID: Double]
        var to: [CardID: Double]
        var velocity: [CardID: Double]
        var startedAt: TimeInterval
        var duration: TimeInterval
        var stiffness: Double
        var damping: Double
        var mass: Double
        var quietFade: Bool
        var selectedID: CardID?
        var selectedScaleFrom: Double
        var selectedShadowFrom: Double
        var selectedRotationFrom: Double

        func sample(now: TimeInterval) -> (positions: [CardID: Double], velocities: [CardID: Double], opacity: [CardID: Double]) {
            let elapsed = max(0, now - startedAt)
            var positions: [CardID: Double] = [:]
            var velocities: [CardID: Double] = [:]
            var opacity: [CardID: Double] = [:]
            for id in CardID.allCases {
                let start = from[id] ?? to[id] ?? PaddockWidgetBentoMotion.slotY[0]
                let target = to[id] ?? start
                let delta = target - start
                let moves = abs(delta) > 0.001
                if elapsed >= duration {
                    positions[id] = target
                    velocities[id] = 0
                    opacity[id] = 1
                } else if quietFade {
                    let p = PaddockWidgetBentoMotion.clamp(elapsed / duration)
                    positions[id] = start + delta * PaddockWidgetBentoMotion.smooth(p)
                    velocities[id] = duration > 0 ? delta * 6 * p * (1 - p) / duration : 0
                    opacity[id] = moves ? (p < 0.5 ? 1 - p * 2 : (p - 0.5) * 2) : 1
                } else {
                    let value = Self.springValue(from: start, to: target, velocity: velocity[id] ?? 0,
                                                 elapsed: elapsed, stiffness: stiffness, damping: damping, mass: mass)
                    positions[id] = value.position
                    velocities[id] = value.velocity
                    opacity[id] = 1
                }
            }
            return (positions, velocities, opacity)
        }

        private static func springValue(from: Double, to: Double, velocity: Double, elapsed: Double,
                                        stiffness: Double, damping: Double, mass: Double) -> (position: Double, velocity: Double) {
            let omega = sqrt(stiffness / mass)
            let zeta = damping / (2 * sqrt(stiffness * mass))
            let displacement = from - to
            if zeta < 1 {
                let damped = omega * sqrt(1 - zeta * zeta)
                let coefficient = (velocity + zeta * omega * displacement) / damped
                let exponential = exp(-zeta * omega * elapsed)
                let cosine = cos(damped * elapsed)
                let sine = sin(damped * elapsed)
                let position = to + exponential * (displacement * cosine + coefficient * sine)
                let speed = exponential * (-zeta * omega * (displacement * cosine + coefficient * sine)
                    + damped * (-displacement * sine + coefficient * cosine))
                return (position, speed)
            }
            let c = velocity + omega * displacement
            let exponential = exp(-omega * elapsed)
            return (to + exponential * (displacement + c * elapsed),
                    exponential * (c - omega * (displacement + c * elapsed)))
        }
    }

    private(set) var order: [CardID] = PaddockWidgetBentoMotion.defaultOrder
    private(set) var isActive = false
    private(set) var isVisible = false
    private(set) var isQuiet = false
    private(set) var autoPlay = false
    private(set) var isEditing = false
    private(set) var draggedID: CardID?
    private(set) var dragY: Double?
    private(set) var candidateIndex: Int?

    private var timelineAnchor: TimeInterval = 0
    private var frozenAt: TimeInterval?
    private var pressID: CardID?
    private var pressOwner: Int?
    private var pressStartedAt: TimeInterval?
    private var pressFrame: Frame?
    private var dragOwner: Int?
    private var pointerOffset = 0.0
    private var dragOriginOrder: [CardID]?
    private var dragVisualOrder: [CardID]?
    /// Slot positions the neighbours were last sent to while a card is held.
    private var dragSlots: [CardID: Double]?
    private var restartAt: TimeInterval?
    private var transition: PositionTransition?

    var isDragging: Bool { draggedID != nil }
    var hasPendingPress: Bool { pressID != nil && !isDragging }
    var hasValidOrder: Bool { PaddockWidgetBentoMotion.isValid(order) }

    mutating func appear(autoPlay: Bool, quiet: Bool, now: TimeInterval) {
        self.autoPlay = autoPlay
        isQuiet = quiet
        isActive = true
        isVisible = true
        isEditing = false
        clearGesture()
        transition = nil
        restartAt = nil
        frozenAt = nil
        timelineAnchor = now
    }

    mutating func setActive(_ active: Bool, now: TimeInterval) {
        guard active != isActive else { return }
        if !active {
            cancelImmediately()
            freeze(at: now)
            isActive = false
        } else {
            isActive = true
            unfreezeIfEligible(at: now)
        }
    }

    mutating func setVisible(_ visible: Bool, now: TimeInterval) {
        guard visible != isVisible else { return }
        if !visible {
            cancelImmediately()
            freeze(at: now)
            isVisible = false
        } else {
            isVisible = true
            unfreezeIfEligible(at: now)
        }
    }

    mutating func setQuiet(_ quiet: Bool, now: TimeInterval) {
        guard quiet != isQuiet else { return }
        if quiet {
            if isDragging { retargetDrag(now: now, quiet: true) }
            else { cancelImmediately() }
            freeze(at: now)
            isQuiet = true
        } else {
            isQuiet = false
            if isDragging {
                unfreezeIfEligible(at: now, shiftTransition: false)
                retargetDrag(now: now, quiet: false)
            } else {
                unfreezeIfEligible(at: now)
            }
        }
    }

    mutating func setAutoPlay(_ enabled: Bool, now: TimeInterval) {
        guard enabled != autoPlay else { return }
        autoPlay = enabled
        restartAt = nil
        timelineAnchor = now
        frozenAt = enabled && (!isActive || !isVisible || isQuiet || isEditing) ? now : nil
    }

    mutating func setEditing(_ editing: Bool, now: TimeInterval) {
        guard editing != isEditing else { return }
        if editing {
            pressFrame = nil
            clearPress()
            freeze(at: now)
            isEditing = true
        } else {
            cancelDrag(now: now)
            isEditing = false
            timelineAnchor = now
            frozenAt = (!isActive || !isVisible || isQuiet) ? now : nil
            restartAt = autoPlay ? now + 2 : nil
        }
    }

    /// Starts a touch-owned pending press in public idle or during the demonstration.
    mutating func beginPress(id: CardID, y: Double, owner: Int, now: TimeInterval) {
        guard isActive, isVisible, order.contains(id) else { return }
        if let pressOwner {
            if pressOwner == owner { return }
            cancelDrag(now: now)
            return
        }
        guard draggedID == nil else { return }
        if !isEditing && autoPlay && !isQuiet {
            if let restartAt, now < restartAt {
                pressFrame = PaddockWidgetBentoMotion.restFrame(order: order)
            } else {
                pressFrame = demoFrame(now: now)
            }
            freeze(at: now)
        } else {
            pressFrame = nil
        }
        pressID = id
        pressOwner = owner
        pressStartedAt = now
        dragY = y
    }

    /// The same touch owner must hold for 320 ms; reaching the threshold enters edit mode.
    mutating func beginDrag(id: CardID, y: Double, owner: Int, now: TimeInterval) {
        guard isActive, isVisible, draggedID == nil else { return }
        guard pressID == id, pressOwner == owner, let pressStartedAt,
              now - pressStartedAt >= PaddockWidgetBentoMotion.longPressDuration,
              order.contains(id) else { return }
        let visibleFrame = pressFrame
        let visualOrder = visibleFrame?.order ?? order
        guard let index = visualOrder.firstIndex(of: id) else { return }
        isEditing = true
        dragOriginOrder = order
        dragVisualOrder = visualOrder
        dragSlots = PaddockWidgetBentoMotion.slotPositions(for: visualOrder)
        draggedID = id
        dragOwner = owner
        candidateIndex = index
        let visibleY = visibleFrame?.cardY[id] ?? PaddockWidgetBentoMotion.slotY[index]
        pointerOffset = y - visibleY
        dragY = y
        pressFrame = nil
        transition = nil
    }

    mutating func updateDrag(y: Double, owner: Int, now: TimeInterval) {
        guard isDragging, dragOwner == owner, let current = candidateIndex else { return }
        dragY = y
        let center = y - pointerOffset
        var target = current
        while target < 2 {
            let boundary = (PaddockWidgetBentoMotion.slotY[target] + PaddockWidgetBentoMotion.slotY[target + 1]) / 2
            if center > boundary + PaddockWidgetBentoMotion.targetHysteresis { target += 1 } else { break }
        }
        while target > 0 {
            let boundary = (PaddockWidgetBentoMotion.slotY[target - 1] + PaddockWidgetBentoMotion.slotY[target]) / 2
            if center < boundary - PaddockWidgetBentoMotion.targetHysteresis { target -= 1 } else { break }
        }
        guard target != current else { return }
        candidateIndex = target
        retargetDrag(now: now, quiet: isQuiet)
    }

    @discardableResult
    mutating func endDrag(owner: Int, now: TimeInterval) -> Bool {
        guard let pressOwner, pressOwner == owner else { return false }
        guard let id = draggedID, dragOwner == owner, let target = candidateIndex else {
            finishShortPress(now: now)
            return false
        }
        let currentPositions = sampledPositions(now: now)
        let selectedRotation = currentDragRotation
        let center = (dragY ?? pointerOffset) - pointerOffset
        let original = dragOriginOrder ?? order
        let visualOrder = dragVisualOrder ?? original
        guard center >= PaddockWidgetBentoMotion.slotY[0] - PaddockWidgetBentoMotion.outsideCancelDistance,
              center <= PaddockWidgetBentoMotion.slotY[2] + 62 + PaddockWidgetBentoMotion.outsideCancelDistance else {
            order = original
            clearGesture()
            beginTransition(from: currentPositions, to: slotPositions(for: original), now: now, settling: true,
                            selectedID: id, selectedScale: isQuiet ? 1 : 1.025,
                            selectedShadow: isQuiet ? 4 : 14, selectedRotation: selectedRotation)
            return false
        }

        var updated = original
        let visualSource = visualOrder.firstIndex(of: id)
        if visualSource != target, let source = updated.firstIndex(of: id) {
            updated.remove(at: source)
            updated.insert(id, at: min(target, updated.count))
        }
        let changed = updated != original
        order = updated
        clearGesture()
        beginTransition(from: currentPositions, to: slotPositions(for: updated), now: now, settling: true,
                        selectedID: id, selectedScale: isQuiet ? 1 : 1.025,
                        selectedShadow: isQuiet ? 4 : 14, selectedRotation: selectedRotation)
        return changed
    }

    /// Cancels a live drag or short pending press and animates back to committed positions.
    mutating func cancelDrag(now: TimeInterval) {
        guard hasPendingPress || isDragging else { return }
        let current = sampledPositions(now: now)
        let original = dragOriginOrder ?? order
        let wasEditing = isEditing
        let selected = draggedID
        let scale = selected == nil || isQuiet ? 1 : 1.025
        let shadow = selected == nil || isQuiet ? 4.0 : 14.0
        let rotation = selected == nil ? 0 : currentDragRotation
        order = original
        clearGesture()
        if isActive && isVisible {
            beginTransition(from: current, to: slotPositions(for: order), now: now, settling: true,
                            selectedID: selected, selectedScale: scale, selectedShadow: shadow, selectedRotation: rotation)
        } else {
            transition = nil
        }
        if !wasEditing && autoPlay {
            restartAt = now + 2
            timelineAnchor = now
            frozenAt = nil
        }
    }

    @discardableResult
    mutating func move(id: CardID, offset: Int, now: TimeInterval) -> Bool {
        guard isEditing, !isDragging, offset != 0, let current = order.firstIndex(of: id) else { return false }
        let from = sampledPositions(now: now)
        let target = min(order.count - 1, max(0, current + offset))
        guard target != current else { return false }
        var updated = order
        updated.remove(at: current)
        updated.insert(id, at: target)
        guard PaddockWidgetBentoMotion.isValid(updated) else { return false }
        order = updated
        beginTransition(from: from, to: slotPositions(for: updated), now: now, settling: true)
        return true
    }

    /// A touch on the pit stop or the sticker: the demo rests until `until` (the touch's own effect plays meanwhile),
    /// then starts again from the top after the restart delay. Arranging cards keeps its own rules.
    mutating func holdDemo(until: TimeInterval) {
        if isEditing { return }
        restartAt = autoPlay ? until + PaddockWidgetBentoMotion.restartDelay : nil
    }

    mutating func done(now: TimeInterval) {
        guard isEditing else { return }
        if isDragging || hasPendingPress { cancelDrag(now: now) }
        isEditing = false
        clearPress()
        timelineAnchor = now
        frozenAt = (!isActive || !isVisible || isQuiet) ? now : nil
        restartAt = autoPlay ? now + 2 : nil
    }

    mutating func frame(now: TimeInterval) -> Frame {
        if isDragging || isEditing || transition != nil || hasPendingPress {
            var result = PaddockWidgetBentoMotion.restFrame(order: order)
            result.handlesOpacity = isEditing ? 1 : 0
            if let pressFrame, !isDragging { result = pressFrame }
            if isDragging, let dragSlots { result.cardY = dragSlots }
            if let transition {
                let samples = transition.sample(now: now)
                result.cardY = samples.positions
                result.cardOpacity = samples.opacity
                let progress = PaddockWidgetBentoMotion.smooth((now - transition.startedAt) / transition.duration)
                result.selectedID = transition.selectedID
                result.selectedScale = transition.selectedScaleFrom + (1 - transition.selectedScaleFrom) * progress
                result.shadowRadius = transition.selectedShadowFrom + (4 - transition.selectedShadowFrom) * progress
                result.rotationDegrees = transition.selectedRotationFrom * (1 - progress)
                if now - transition.startedAt >= transition.duration { self.transition = nil }
            }
            if let draggedID, let dragY {
                result.selectedID = draggedID
                result.order = candidateOrder()
                result.cardY[draggedID] = dragY - pointerOffset
                result.selectedScale = isQuiet ? 1 : 1.025
                result.shadowRadius = isQuiet ? 4 : 14
                result.rotationDegrees = isQuiet ? 0 : min(0.7, max(-0.7, (dragY - (PaddockWidgetBentoMotion.slotY[candidateIndex ?? 0] + pointerOffset)) * 0.012))
            }
            result.isDemo = false
            return result
        }
        guard isActive, isVisible, !isQuiet, autoPlay, frozenAt == nil else {
            return PaddockWidgetBentoMotion.restFrame(order: order)
        }
        if let restartAt {
            guard now >= restartAt else { return PaddockWidgetBentoMotion.restFrame(order: order) }
            timelineAnchor = now
            self.restartAt = nil
        }
        return demoFrame(now: now)
    }

    /// Seconds until the 60 Hz window of the demo or a transition ends; nil when only an event ends it.
    func frameWindowEnd(now: TimeInterval) -> TimeInterval? {
        if isDragging { return nil }
        if let transition { return max(0, transition.startedAt + transition.duration - now) }
        let time = max(0, now - timelineAnchor).truncatingRemainder(dividingBy: PaddockWidgetBentoMotion.loopDuration)
        return PaddockWidgetBentoMotion.activeRanges.first(where: { time >= $0.0 && time < $0.1 }).map { $0.1 - time }
    }

    /// Commits deadline/transition bookkeeping at event or scheduled wake boundaries.
    mutating func advance(now: TimeInterval) {
        _ = frame(now: now)
    }

    /// 60 Hz while moving; otherwise one wake at the next motion boundary or restart.
    func cadence(now: TimeInterval) -> TimeInterval? {
        if isDragging { return 1.0 / 60 }
        if let transition {
            let untilSettle = transition.startedAt + transition.duration - now
            return untilSettle > 0 ? min(1.0 / 60, untilSettle) : nil
        }
        guard isActive, isVisible, !isQuiet, autoPlay, !isEditing, frozenAt == nil else { return nil }
        if let restartAt, now < restartAt { return max(0.001, restartAt - now) }
        let elapsed = max(0, now - timelineAnchor)
        let time = elapsed.truncatingRemainder(dividingBy: PaddockWidgetBentoMotion.loopDuration)
        let activeRanges = PaddockWidgetBentoMotion.activeRanges
        if activeRanges.contains(where: { time >= $0.0 && time < $0.1 }) { return 1.0 / 60 }
        let nextBoundary = activeRanges.flatMap { [$0.0, $0.1] }.first(where: { $0 > time })
        let next = nextBoundary ?? (PaddockWidgetBentoMotion.loopDuration + activeRanges[0].0)
        return max(0.001, next - time)
    }

    private func demoFrame(now: TimeInterval) -> Frame {
        PaddockWidgetBentoMotion.sample(elapsed: max(0, now - timelineAnchor), order: order)
    }

    private func slotPositions(for order: [CardID]) -> [CardID: Double] {
        Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, PaddockWidgetBentoMotion.slotY[$0.offset]) })
    }

    private func candidateOrder() -> [CardID] {
        guard let draggedID, let candidateIndex else { return order }
        var result = dragVisualOrder ?? order
        guard let source = result.firstIndex(of: draggedID) else { return order }
        result.remove(at: source)
        result.insert(draggedID, at: min(candidateIndex, result.count))
        return result
    }

    private mutating func sampledPositions(now: TimeInterval) -> [CardID: Double] {
        var result = transition?.sample(now: now).positions ?? (isDragging ? dragSlots : nil) ?? slotPositions(for: dragVisualOrder ?? order)
        if let pressFrame, !isDragging { result = pressFrame.cardY }
        if let draggedID, let dragY { result[draggedID] = dragY - pointerOffset }
        return result
    }

    private mutating func sampledVelocities(now: TimeInterval) -> [CardID: Double] {
        transition?.sample(now: now).velocities ?? Dictionary(uniqueKeysWithValues: CardID.allCases.map { ($0, 0) })
    }

    private mutating func retargetDrag(now: TimeInterval, quiet: Bool) {
        guard isDragging, let draggedID else { return }
        let from = sampledPositions(now: now)
        let velocity = sampledVelocities(now: now)
        var targets = slotPositions(for: candidateOrder())
        targets.removeValue(forKey: draggedID)
        var sources = from
        sources.removeValue(forKey: draggedID)
        var velocities = velocity
        velocities.removeValue(forKey: draggedID)
        beginTransition(from: sources, to: targets, velocity: velocities, now: now, settling: false, quiet: quiet)
        dragSlots = slotPositions(for: candidateOrder())
    }

    private mutating func beginTransition(from: [CardID: Double], to: [CardID: Double], velocity: [CardID: Double] = [:],
                                          now: TimeInterval, settling: Bool, quiet: Bool? = nil,
                                          selectedID: CardID? = nil, selectedScale: Double = 1,
                                          selectedShadow: Double = 4, selectedRotation: Double = 0) {
        let moving = CardID.allCases.contains { abs((from[$0] ?? to[$0] ?? PaddockWidgetBentoMotion.slotY[0]) - (to[$0] ?? from[$0] ?? PaddockWidgetBentoMotion.slotY[0])) > 0.001 }
        let selectedMoves = selectedID != nil && (abs(selectedScale - 1) > 0.001 || abs(selectedShadow - 4) > 0.001 || abs(selectedRotation) > 0.001)
        guard moving || selectedMoves else { transition = nil; return }
        let useQuiet = quiet ?? isQuiet
        transition = PositionTransition(from: from, to: to, velocity: velocity, startedAt: now,
                                        duration: useQuiet ? PaddockWidgetBentoMotion.quietReflowDuration : PaddockWidgetBentoMotion.springSettleDuration,
                                        stiffness: settling ? PaddockWidgetBentoMotion.settleStiffness : PaddockWidgetBentoMotion.reflowStiffness,
                                        damping: settling ? PaddockWidgetBentoMotion.settleDamping : PaddockWidgetBentoMotion.reflowDamping,
                                        mass: settling ? PaddockWidgetBentoMotion.settleMass : PaddockWidgetBentoMotion.reflowMass,
                                        quietFade: useQuiet,
                                        selectedID: selectedID,
                                        selectedScaleFrom: selectedScale,
                                        selectedShadowFrom: selectedShadow,
                                        selectedRotationFrom: selectedRotation)
    }

    private mutating func finishShortPress(now: TimeInterval) {
        guard hasPendingPress else { return }
        let from = sampledPositions(now: now)
        let sourceFrame = pressFrame
        clearGesture()
        beginTransition(from: from, to: slotPositions(for: order), now: now, settling: true,
                        selectedID: sourceFrame?.selectedID,
                        selectedScale: sourceFrame?.selectedScale ?? 1,
                        selectedShadow: sourceFrame?.shadowRadius ?? 4,
                        selectedRotation: sourceFrame?.rotationDegrees ?? 0)
        if autoPlay {
            restartAt = now + 2
            timelineAnchor = now
            frozenAt = nil
        }
    }

    private mutating func cancelImmediately() {
        order = dragOriginOrder ?? order
        clearGesture()
        transition = nil
    }

    private mutating func clearGesture() {
        draggedID = nil
        dragY = nil
        candidateIndex = nil
        dragOwner = nil
        dragOriginOrder = nil
        dragVisualOrder = nil
        dragSlots = nil
        pointerOffset = 0
        pressFrame = nil
        clearPress()
    }

    private mutating func clearPress() {
        pressID = nil
        pressOwner = nil
        pressStartedAt = nil
    }

    private var currentDragRotation: Double {
        guard let dragY, let candidateIndex else { return 0 }
        return isQuiet ? 0 : min(0.7, max(-0.7, (dragY - (PaddockWidgetBentoMotion.slotY[candidateIndex] + pointerOffset)) * 0.012))
    }

    private mutating func freeze(at now: TimeInterval) {
        guard frozenAt == nil else { return }
        frozenAt = now
    }

    private mutating func unfreeze(at now: TimeInterval, shiftTransition: Bool = true) {
        guard let frozenAt else { return }
        let pause = max(0, now - frozenAt)
        timelineAnchor += pause
        if let restartAt { self.restartAt = restartAt + pause }
        if shiftTransition { transition?.startedAt += pause }
        self.frozenAt = nil
    }

    private mutating func unfreezeIfEligible(at now: TimeInterval, shiftTransition: Bool = true) {
        guard isActive, isVisible, !isQuiet, !isEditing else { return }
        unfreeze(at: now, shiftTransition: shiftTransition)
    }
}
