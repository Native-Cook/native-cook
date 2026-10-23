import Observation
import SwiftUI
import UIKit

@MainActor
@Observable
final class HabitBubbleClusterModel {
    var simulation = HabitBubbleClusterSimulation()
    var size = CGSize.zero
    var safeTop = 0.0
    var tab = 0
    var detail = -1
    var detailProgress = 0.0
    var finger = CGPoint.zero
    var fingerAlpha = 0.0

    let autoPlay: Bool
    var reducedMotion: Bool

    private let displayLink = HabitBubbleClusterDisplayLink()
    private var previousTimestamp: CFTimeInterval?
    private var loopEpoch: CFTimeInterval?
    private var previousLoopMS = 0.0
    private var scriptDown = false
    private var paused = false
    private var realTouchDown = false
    private var lastRealTouchTimestamp: CFTimeInterval?
    private var returningToRestAt: CFTimeInterval?
    private var touchPreviousPoint = CGPoint.zero
    private var touchPreviousTime = 0.0
    private var touchVelocity = CGVector.zero
    private var detailTransitionStart = 0.0
    private var detailTransitionFrom = 0.0
    private var detailTransitionTarget = 0.0
    private var detailTransitionDuration = 0.0

    init(autoPlay: Bool, reducedMotion: Bool) {
        self.autoPlay = autoPlay
        self.reducedMotion = reducedMotion
        displayLink.onFrame = { [weak self] timestamp in self?.frame(timestamp: timestamp) }
    }

    var origin: CGPoint {
        CGPoint(x: size.width / 2 + HabitBubbleClusterTokens.cluster.x - HabitBubbleClusterTokens.referenceWidth / 2, y: HabitBubbleClusterTokens.cluster.y)
    }

    var doneCount: Int {
        HabitBubbleClusterSimulation.entities.indices.filter {
            HabitBubbleClusterSimulation.entities[$0].set == tab && !HabitBubbleClusterSimulation.entities[$0].quiet && simulation.doneTarget[$0] > 0.5
        }.count
    }

    var totalCount: Int {
        HabitBubbleClusterSimulation.entities.filter { $0.set == tab && !$0.quiet }.count
    }

    func start() { displayLink.start() }
    func stop() { displayLink.stop() }

    func update(size: CGSize, safeTop: Double) {
        self.size = size
        self.safeTop = safeTop
    }

    func selectTab(_ next: Int, scripted: Bool = false) {
        if !scripted { interruptAutoplay(touching: false) }
        simulation.setTab(next)
        tab = next
        UIAccessibility.post(notification: .announcement, argument: next == 0 ? "Social habits" : "Personal habits")
    }

    func closeDetail(scripted: Bool = false) {
        if !scripted { interruptAutoplay(touching: false) }
        simulation.close()
        detail = -1
        beginDetailTransition(to: 0, durationMS: HabitBubbleClusterTokens.detail.fadeOutMs)
    }

    func toggle(_ index: Int) {
        interruptAutoplay(touching: false)
        let next = simulation.doneTarget[index] < 0.5
        simulation.setDone(index, next)
        haptic(next ? 2 : 3)
        UIAccessibility.post(notification: .announcement, argument: next ? "Habit completed" : "Habit reopened")
    }

    func pointerBegan(at point: CGPoint, timestamp: CFTimeInterval) {
        interruptAutoplay(touching: true)
        realTouchDown = true
        fingerAlpha = 0
        touchPreviousPoint = point
        touchPreviousTime = timestamp
        touchVelocity = .zero
        simulation.touchDown(clusterPoint(point))
        if simulation.held >= 0 { haptic(1) }
    }

    func pointerMoved(to point: CGPoint, timestamp: CFTimeInterval) {
        let elapsed = timestamp - touchPreviousTime
        if elapsed > 0 {
            touchVelocity = CGVector(dx: (point.x - touchPreviousPoint.x) / elapsed, dy: (point.y - touchPreviousPoint.y) / elapsed)
        }
        touchPreviousPoint = point
        touchPreviousTime = timestamp
        simulation.touchMove(clusterPoint(point))
    }

    func pointerEnded(at point: CGPoint, timestamp: CFTimeInterval, cancelled: Bool) {
        pointerMoved(to: point, timestamp: timestamp)
        let tapped = simulation.touchUp(velocity: cancelled ? .zero : touchVelocity, reducedMotion: reducedMotion)
        realTouchDown = false
        lastRealTouchTimestamp = timestamp
        if tapped >= 0 { openDetail(tapped) }
    }

    private func frame(timestamp: CFTimeInterval) {
        guard size.width > 0, size.height > 0 else { previousTimestamp = timestamp; return }
        let wallElapsed = max(0, timestamp - (previousTimestamp ?? timestamp))
        let elapsed = min(0.1, wallElapsed)
        previousTimestamp = timestamp

        if autoPlay {
            if paused {
                if !realTouchDown, let lastRealTouchTimestamp, timestamp - lastRealTouchTimestamp >= HabitBubbleClusterTokens.interactionReturnDelayMs / 1000 {
                    if returningToRestAt == nil { beginReturnToRest(at: timestamp) }
                    else if let returningToRestAt, timestamp - returningToRestAt >= 1 { resetScript(at: timestamp) }
                }
            } else {
                runScript(timestamp: timestamp)
            }
        }
        simulation.advanceClock(milliseconds: wallElapsed * 1000)
        let events = simulation.step(milliseconds: elapsed * 1000, reducedMotion: reducedMotion)
        if !scriptDown { fingerAlpha = max(0, fingerAlpha - elapsed / 0.16) }
        updateDetailProgress(timestamp: timestamp)
        if !paused {
            for event in events where event == 1 || event == 2 { _ = event }
        } else {
            for event in events { haptic(event) }
        }
    }

    private func runScript(timestamp: CFTimeInterval) {
        if loopEpoch == nil { loopEpoch = timestamp }
        let elapsedMS = (timestamp - (loopEpoch ?? timestamp)) * 1000
        let loopMS = HabitBubbleClusterTokens.loopMs
        if elapsedMS >= loopMS {
            resetScript(at: timestamp)
            return
        }
        let current = HabitBubbleClusterTokens.qaHold.flatMap { hold in
            hold == "rest" ? HabitBubbleClusterTokens.qaHoldAt.rest : hold == "a" ? HabitBubbleClusterTokens.qaHoldAt.a : HabitBubbleClusterTokens.qaHoldAt.b
        } ?? elapsedMS
        processScriptEvents(from: previousLoopMS, to: current)
        previousLoopMS = current
    }

    private func processScriptEvents(from previous: Double, to current: Double) {
        let autoplay = HabitBubbleClusterTokens.autoplay
        if crossed(autoplay.holdStart, previous, current) { scriptTouchDown(id: autoplay.holdId) }
        if crossed(autoplay.holdEnd, previous, current) { scriptTouchUp() }
        if crossed(autoplay.openAt, previous, current) { scriptTouchDown(id: autoplay.openId) }
        if crossed(autoplay.openAt + autoplay.tapMs, previous, current) { scriptTouchUp() }
        if crossed(autoplay.backAt, previous, current) { closeDetail(scripted: true); showScriptFinger(at: backPoint) }
        if crossed(autoplay.backAt + autoplay.tapMs, previous, current) { hideScriptFinger() }
        if crossed(autoplay.personalAt, previous, current) { selectTab(1, scripted: true); showScriptFinger(at: personalPoint) }
        if crossed(autoplay.personalAt + autoplay.tapMs, previous, current) { hideScriptFinger() }
        if crossed(autoplay.socialAt, previous, current) { selectTab(0, scripted: true); showScriptFinger(at: socialPoint) }
        if crossed(autoplay.socialAt + autoplay.tapMs, previous, current) { hideScriptFinger() }
        if crossed(autoplay.tapAt, previous, current) { scriptTouchDown(id: autoplay.holdId) }
        if crossed(autoplay.tapAt + autoplay.tapMs, previous, current) { scriptTouchUp() }
    }

    private func crossed(_ event: Double, _ previous: Double, _ current: Double) -> Bool {
        previous < event && current >= event
    }

    private func scriptTouchDown(id: String) {
        guard let index = HabitBubbleClusterSimulation.entities.firstIndex(where: { $0.id == id }) else { return }
        let point = CGPoint(x: simulation.x[index], y: simulation.y[index])
        simulation.touchDown(point)
        finger = screenPoint(point)
        fingerAlpha = 1
        scriptDown = true
    }

    private func scriptTouchUp() {
        let tapped = simulation.touchUp(velocity: .zero, reducedMotion: reducedMotion)
        scriptDown = false
        if tapped >= 0 { openDetail(tapped) }
    }

    private func openDetail(_ index: Int) {
        simulation.open(index, at: CGPoint(x: size.width / 2 - origin.x, y: size.height * HabitBubbleClusterTokens.detail.y - origin.y))
        detail = index
        beginDetailTransition(to: 1, durationMS: HabitBubbleClusterTokens.detail.fadeMs)
    }

    private func interruptAutoplay(touching: Bool) {
        guard autoPlay else { return }
        if scriptDown { _ = simulation.touchUp(velocity: .zero, reducedMotion: reducedMotion) }
        paused = true
        scriptDown = false
        fingerAlpha = 0
        if !touching { lastRealTouchTimestamp = CACurrentMediaTime() }
    }

    private func resetScript(at timestamp: CFTimeInterval) {
        simulation = HabitBubbleClusterSimulation()
        tab = 0
        detail = -1
        detailProgress = 0
        detailTransitionTarget = 0
        fingerAlpha = 0
        scriptDown = false
        paused = false
        previousLoopMS = 0
        loopEpoch = timestamp
        lastRealTouchTimestamp = nil
        returningToRestAt = nil
    }

    private func beginReturnToRest(at timestamp: CFTimeInterval) {
        if simulation.focus >= 0 { simulation.close() }
        if simulation.tab != 0 { simulation.setTab(0) }
        for index in HabitBubbleClusterSimulation.entities.indices where simulation.doneTarget[index] > 0.5 {
            simulation.setDone(index, false)
        }
        tab = 0
        detail = -1
        beginDetailTransition(to: 0, durationMS: HabitBubbleClusterTokens.detail.fadeOutMs)
        returningToRestAt = timestamp
    }

    private func beginDetailTransition(to target: Double, durationMS: Double) {
        detailTransitionStart = CACurrentMediaTime()
        detailTransitionFrom = detailProgress
        detailTransitionTarget = target
        detailTransitionDuration = durationMS / 1000
    }

    private func updateDetailProgress(timestamp: CFTimeInterval) {
        guard detailTransitionDuration > 0, detailProgress != detailTransitionTarget else { return }
        let progress = min(1, max(0, (timestamp - detailTransitionStart) / detailTransitionDuration))
        let ease = cubicBezier(progress, x1: HabitBubbleClusterTokens.detail.ease[0], y1: HabitBubbleClusterTokens.detail.ease[1], x2: HabitBubbleClusterTokens.detail.ease[2], y2: HabitBubbleClusterTokens.detail.ease[3])
        detailProgress = detailTransitionFrom + (detailTransitionTarget - detailTransitionFrom) * ease
    }

    private func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        func component(_ t: Double, _ first: Double, _ second: Double) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t
        }
        func derivative(_ t: Double, _ first: Double, _ second: Double) -> Double {
            3 * (1 - t) * (1 - t) * first + 6 * (1 - t) * t * (second - first) + 3 * t * t * (1 - second)
        }
        var parameter = progress
        for _ in 0..<5 {
            let slope = derivative(parameter, x1, x2)
            if abs(slope) < 0.0001 { break }
            parameter = min(1, max(0, parameter - (component(parameter, x1, x2) - progress) / slope))
        }
        return component(parameter, y1, y2)
    }

    private func clusterPoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x - origin.x, y: point.y - origin.y)
    }

    private func screenPoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x + origin.x, y: point.y + origin.y)
    }

    private var backPoint: CGPoint { CGPoint(x: HabitBubbleClusterTokens.layout.gutter + HabitBubbleClusterTokens.layout.iconButton / 2, y: safeTop + HabitBubbleClusterTokens.layout.headerTop + HabitBubbleClusterTokens.layout.control / 2) }
    private var socialPoint: CGPoint { CGPoint(x: HabitBubbleClusterTokens.layout.gutter + HabitBubbleClusterTokens.layout.segmentWidth / 4, y: safeTop + HabitBubbleClusterTokens.layout.headerTop + HabitBubbleClusterTokens.layout.control / 2) }
    private var personalPoint: CGPoint { CGPoint(x: HabitBubbleClusterTokens.layout.gutter + HabitBubbleClusterTokens.layout.segmentWidth * 0.75, y: safeTop + HabitBubbleClusterTokens.layout.headerTop + HabitBubbleClusterTokens.layout.control / 2) }

    private func showScriptFinger(at point: CGPoint) { finger = point; fingerAlpha = 1 }
    private func hideScriptFinger() { scriptDown = false }

    private func haptic(_ event: Int) {
        switch event {
        case 1: UISelectionFeedbackGenerator().selectionChanged()
        case 2: UINotificationFeedbackGenerator().notificationOccurred(.success)
        case 3: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        default: break
        }
    }
}
