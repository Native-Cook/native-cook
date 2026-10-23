import Observation
import SwiftUI
import UIKit

@MainActor
@Observable
final class HabitBubbleClusterModel {
    var simulation = HabitBubbleClusterSimulation()
    var size = CGSize.zero
    var safeTop = 0.0
    var safeBottom = 0.0
    var tab = 0
    var labelIndex = -1
    var labelAtClock = -1_000_000.0
    var finger = CGPoint.zero
    var fingerAlpha = 0.0
    var reducedMotion = false

    let autoPlay: Bool

    private let displayLink = HabitBubbleClusterDisplayLink()
    private var previousTimestamp: CFTimeInterval?
    private var loopEpoch: CFTimeInterval?
    private var previousLoopMS = 0.0
    private var scriptDown = false
    private var paused: Bool
    private var realTouchDown = false
    private var lastRealTouchTimestamp: CFTimeInterval?
    private var touchPreviousPoint = CGPoint.zero
    private var touchPreviousTime = 0.0
    private var touchVelocity = CGVector.zero

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        paused = !autoPlay
        displayLink.onFrame = { [weak self] timestamp in self?.frame(timestamp: timestamp) }
    }

    var origin: CGPoint {
        CGPoint(
            x: size.width / 2 + HabitBubbleClusterTokens.cluster.x - HabitBubbleClusterTokens.referenceWidth / 2,
            y: HabitBubbleClusterTokens.cluster.y + safeTop - 62
        )
    }

    var count: (done: Int, total: Int) { simulation.doneCount() }

    func start() { displayLink.start() }
    func stop() { displayLink.stop() }

    func update(size: CGSize, safeTop: Double, safeBottom: Double) {
        self.size = size
        self.safeTop = safeTop
        self.safeBottom = safeBottom
    }

    func updateReducedMotion(_ value: Bool) {
        guard reducedMotion != value else { return }
        reducedMotion = value
        if value {
            if scriptDown { _ = simulation.touchUp(velocity: .zero, reducedMotion: true) }
            scriptDown = false
            fingerAlpha = 0
            paused = true
        } else if autoPlay {
            resetScriptClock(at: CACurrentMediaTime())
            paused = false
        }
    }

    func selectTab(_ next: Int, scripted: Bool = false) {
        if !scripted { interruptAutoplay(touching: false) }
        simulation.setTab(next)
        tab = next
        labelIndex = -1
        if !scripted { UIAccessibility.post(notification: .announcement, argument: next == 0 ? "Social habits" : "Personal habits") }
    }

    func toggle(_ index: Int) {
        interruptAutoplay(touching: false)
        let next = simulation.doneTarget[index] < 0.5
        simulation.setDone(index, next)
    }

    func pointerBegan(at point: CGPoint, timestamp: CFTimeInterval) {
        interruptAutoplay(touching: true)
        realTouchDown = true
        fingerAlpha = 0
        touchPreviousPoint = point
        touchPreviousTime = timestamp
        touchVelocity = .zero
        simulation.touchDown(clusterPoint(point))
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
        if tapped >= 0 {
            labelIndex = tapped
            labelAtClock = simulation.clock
        }
    }

    private func frame(timestamp: CFTimeInterval) {
        guard size.width > 0, size.height > 0 else {
            previousTimestamp = timestamp
            return
        }
        let elapsed = max(0, timestamp - (previousTimestamp ?? timestamp))
        previousTimestamp = timestamp

        if autoPlay, !reducedMotion {
            if paused {
                if !realTouchDown,
                   let lastRealTouchTimestamp,
                   timestamp - lastRealTouchTimestamp >= HabitBubbleClusterTokens.autoplay.resumeDelayMs / 1000 {
                    simulation.resetDay()
                    tab = 0
                    labelIndex = -1
                    paused = false
                    resetScriptClock(at: timestamp)
                }
            } else {
                runScript(timestamp: timestamp)
            }
        }

        let events = simulation.step(milliseconds: elapsed * 1000, reducedMotion: reducedMotion)
        if paused || !autoPlay {
            for event in events {
                if event == HabitBubbleClusterSimulation.Event.done {
                    UIAccessibility.post(notification: .announcement, argument: "Habit done")
                } else if event == HabitBubbleClusterSimulation.Event.undo {
                    UIAccessibility.post(notification: .announcement, argument: "Habit reopened")
                }
                if event != HabitBubbleClusterSimulation.Event.count { emitHaptic(event) }
            }
        }
    }

    private func runScript(timestamp: CFTimeInterval) {
        if loopEpoch == nil { loopEpoch = timestamp }
        let elapsedMS = (timestamp - (loopEpoch ?? timestamp)) * 1000
        if elapsedMS >= HabitBubbleClusterTokens.loopMs {
            resetScriptClock(at: timestamp)
            simulation.clock = 0
            labelIndex = -1
            return
        }
        processScriptEvents(from: previousLoopMS, to: elapsedMS)
        previousLoopMS = elapsedMS
    }

    private func processScriptEvents(from previous: Double, to current: Double) {
        let autoplay = HabitBubbleClusterTokens.autoplay
        if crossed(autoplay.holdStart, previous, current) { scriptTouchDown(id: autoplay.holdID) }
        if crossed(autoplay.holdEnd, previous, current) { scriptTouchUp() }
        if crossed(autoplay.nameAt, previous, current) { scriptTouchDown(id: autoplay.nameID) }
        if crossed(autoplay.nameAt + autoplay.tapMs, previous, current) { scriptTouchUp() }
        if crossed(autoplay.personalAt, previous, current) {
            selectTab(1, scripted: true)
            showScriptFinger(at: personalPoint)
        }
        if crossed(autoplay.personalAt + autoplay.tapMs, previous, current) { hideScriptFinger() }
        if crossed(autoplay.socialAt, previous, current) {
            selectTab(0, scripted: true)
            showScriptFinger(at: socialPoint)
        }
        if crossed(autoplay.socialAt + autoplay.tapMs, previous, current) { hideScriptFinger() }
        if crossed(autoplay.tapAt, previous, current) { scriptTouchDown(id: autoplay.holdID) }
        if crossed(autoplay.tapAt + autoplay.tapMs, previous, current) { scriptTouchUp() }
    }

    private func crossed(_ event: Double, _ previous: Double, _ current: Double) -> Bool {
        previous < event && current >= event
    }

    private func scriptTouchDown(id: String) {
        guard let index = HabitBubbleClusterSimulation.entities.firstIndex(where: { $0.id == id }) else { return }
        let point = CGPoint(x: simulation.x[index], y: simulation.y[index])
        simulation.touchDown(point)
        showScriptFinger(at: screenPoint(point))
        scriptDown = true
    }

    private func scriptTouchUp() {
        let tapped = simulation.touchUp(velocity: .zero, reducedMotion: reducedMotion)
        scriptDown = false
        fingerAlpha = 0
        if tapped >= 0 {
            labelIndex = tapped
            labelAtClock = simulation.clock
        }
    }

    private func interruptAutoplay(touching: Bool) {
        guard autoPlay else { return }
        if scriptDown { _ = simulation.touchUp(velocity: .zero, reducedMotion: reducedMotion) }
        paused = true
        scriptDown = false
        fingerAlpha = 0
        if !touching { lastRealTouchTimestamp = CACurrentMediaTime() }
    }

    private func resetScriptClock(at timestamp: CFTimeInterval) {
        loopEpoch = timestamp
        previousLoopMS = 0
        lastRealTouchTimestamp = nil
    }

    private func clusterPoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x - origin.x, y: point.y - origin.y)
    }

    private func screenPoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x + origin.x, y: point.y + origin.y)
    }

    private var socialPoint: CGPoint {
        CGPoint(
            x: HabitBubbleClusterTokens.layout.gutter + HabitBubbleClusterTokens.layout.segmentWidth / 4,
            y: safeTop + HabitBubbleClusterTokens.space.xs + HabitBubbleClusterTokens.layout.control / 2
        )
    }

    private var personalPoint: CGPoint {
        CGPoint(
            x: HabitBubbleClusterTokens.layout.gutter + HabitBubbleClusterTokens.layout.segmentWidth * 0.75,
            y: safeTop + HabitBubbleClusterTokens.space.xs + HabitBubbleClusterTokens.layout.control / 2
        )
    }

    private func showScriptFinger(at point: CGPoint) {
        finger = point
        fingerAlpha = 1
    }

    private func hideScriptFinger() {
        scriptDown = false
        fingerAlpha = 0
    }

    private func emitHaptic(_ event: Int) {
        switch event {
        case HabitBubbleClusterSimulation.Event.tick, HabitBubbleClusterSimulation.Event.grab:
            UISelectionFeedbackGenerator().selectionChanged()
        case HabitBubbleClusterSimulation.Event.done:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case HabitBubbleClusterSimulation.Event.undo:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        default:
            break
        }
    }
}
