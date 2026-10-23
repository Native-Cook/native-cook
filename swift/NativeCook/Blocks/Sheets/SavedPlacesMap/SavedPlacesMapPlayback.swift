import Foundation
import Observation

@MainActor
@Observable
final class SavedPlacesMapPlayback {
    enum Hit: Int {
        case none = 0
        case checkbox = 1
        case tile = 2
        case sheet = 3
        case marker = 10
    }

    private enum Mode { case auto, touch }

    private struct SpringMotion {
        let from: Double
        let to: Double
        let velocity: Double
        let startedAt: Double
        let spring: SavedPlacesMapMotion.SpringSpec

        func value(at time: Double) -> Double {
            SavedPlacesMapMotion.springValue(from: from, to: to, velocity: velocity,
                                             seconds: time - startedAt, spring: spring)
        }
    }

    private struct TimingMotion {
        let from: Double
        let to: Double
        let startedAt: Double

        func value(at time: Double) -> Double {
            let progress = SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.segment(time, startedAt,
                                             startedAt + SavedPlacesMapMotion.VISIT.reducedFade)
            )
            return SavedPlacesMapMotion.lerp(from, to, progress)
        }
    }

    private var mode = Mode.touch
    private var autoAnchor = 0.0
    private var autoPlay = false
    private var reduced = false
    private var calm = false
    /// The loop plays only when asked for and neither Reduce Motion nor a screen reader is on.
    private var loops: Bool { autoPlay && !calm }
    private var sheetValue = 0.0
    private var sheetSpring: SpringMotion?
    private var sheetTiming: TimingMotion?
    private var clusterAnchor = 0.0
    private var clusterAnchorAt = 0.0
    private var visitDirection = 0
    private var visitStartedAt = -1e9
    private var lastToggleAt = -1e9
    private var tappedMarker = -1
    private var markerTappedAt = -1e9
    private var pressedHit = Hit.checkbox.rawValue
    private var pressStartedAt = -1e9
    private var pressReleaseAt = -1e9
    private var pressReleaseFrom = 0.0
    private var grabStartedAt = -1e9
    private var grabReleaseAt = -1e9
    private var grabReleaseFrom = 0.0
    private var contactActive = false
    private(set) var lastTouchedAt = -1e9

    func start(autoPlay: Bool, reduced: Bool, calm: Bool, at time: Double) {
        self.autoPlay = autoPlay
        self.reduced = reduced
        self.calm = calm
        autoAnchor = time
        mode = loops ? .auto : .touch
        sheetValue = 0
        sheetSpring = nil
        sheetTiming = nil
        clusterAnchor = 0
        clusterAnchorAt = time
        visitDirection = 0
        visitStartedAt = -1e9
        lastTouchedAt = -1e9
        contactActive = false
    }

    /// Follows autoPlay / Reduce Motion / VoiceOver live. A loop that is switched off settles to the pin's frame and
    /// stays there (touch and the accessibility actions still work); one switched on starts again from rest.
    func configure(autoPlay: Bool, reduced: Bool, calm: Bool, at time: Double) {
        let wasLooping = loops
        let wasAuto = mode == .auto || restartedAuto(at: time)
        let current = frame(at: time)
        self.autoPlay = autoPlay
        self.reduced = reduced
        self.calm = calm
        if wasLooping && !loops {
            let restarted = restartedAuto(at: time)
            _ = restarted
            if wasAuto {
                if mode != .auto {
                    let returnAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
                    autoAnchor = returnRestartAt(returnAt: returnAt, before: manualFrame(at: returnAt))
                }
                capture(current, at: time)
                captureVisitFromAuto(at: time)
                if visitDirection == 1 { visitDirection = 0; visitStartedAt = time }
                if current.sheet > 0.001 {
                    if reduced {
                        sheetTiming = TimingMotion(from: current.sheet, to: 0, startedAt: time)
                    } else {
                        sheetSpring = SpringMotion(from: current.sheet, to: 0, velocity: 0, startedAt: time,
                                                   spring: SavedPlacesMapMotion.SPRINGS.sheetFall)
                    }
                }
            }
            mode = .touch
            lastTouchedAt = -1e9
        } else if !wasLooping && loops {
            let now = manualFrame(at: time)
            if abs(now.sheet) < 0.001, now.cluster == 0 {
                autoAnchor = time
                mode = .auto
            } else {
                lastTouchedAt = time
            }
        }
    }

    func frame(at time: Double) -> SavedPlacesMapMotion.Frame {
        if mode == .auto { return SavedPlacesMapMotion.autoplay(time - autoAnchor) }
        guard loops, !contactActive, lastTouchedAt > -1e8 else { return manualFrame(at: time) }
        let returnAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
        guard time >= returnAt else { return manualFrame(at: time) }
        let before = manualFrame(at: returnAt)
        let restartAt = returnRestartAt(returnAt: returnAt, before: before)
        if time >= restartAt { return SavedPlacesMapMotion.autoplay(time - restartAt) }
        return returnFrame(at: time, returnAt: returnAt, before: before)
    }

    func enterTouch(at time: Double) {
        let wasRestarted = restartedAuto(at: time)
        let wasReturning = returning(at: time)
        let wasAutoPlayback = mode == .auto || wasRestarted
        if wasAutoPlayback || wasReturning {
            let current = frame(at: time)
            if wasRestarted {
                let returnAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
                autoAnchor = returnRestartAt(returnAt: returnAt, before: manualFrame(at: returnAt))
            }
            capture(current, at: time)
            if wasReturning {
                visitDirection = 0
                visitStartedAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
                if current.sheet > 0.001 {
                    sheetSpring = SpringMotion(from: current.sheet, to: 0, velocity: 0,
                                               startedAt: time, spring: SavedPlacesMapMotion.SPRINGS.sheetFall)
                }
            } else {
                captureVisitFromAuto(at: time)
            }
            if wasAutoPlayback {
                let target = current.sheet > 0.5 ? 1.0 : 0.0
                if abs(current.sheet - target) > 0.001 {
                    sheetSpring = SpringMotion(from: current.sheet, to: target, velocity: 0,
                                               startedAt: time, spring: SavedPlacesMapMotion.SPRINGS.sheetRise)
                }
            }
            mode = .touch
        }
        lastTouchedAt = time
    }

    func beginContact(at time: Double) {
        enterTouch(at: time)
        contactActive = true
    }

    func endContact(at time: Double) {
        contactActive = false
        lastTouchedAt = time
    }

    func beginDrag(at time: Double) {
        enterTouch(at: time)
        commitSheet(at: time)
        grabStartedAt = time
        grabReleaseAt = -1e9
    }

    func drag(from start: Double, translationY: Double, rise: Double, at time: Double) {
        commitCluster(at: time)
        sheetSpring = nil
        sheetTiming = nil
        sheetValue = SavedPlacesMapMotion.band(start - translationY / rise)
        lastTouchedAt = time
    }

    func endDrag(start: Double, velocity: Double, at time: Double) -> Bool {
        commitCluster(at: time)
        commitSheet(at: time)
        let target = sheetValue + velocity * SavedPlacesMapMotion.DRAG.project > 0.5 ? 1.0 : 0.0
        let changed = Int(target.rounded()) != Int(SavedPlacesMapMotion.clamp01(start).rounded())
        if reduced {
            sheetTiming = TimingMotion(from: sheetValue, to: target, startedAt: time)
            sheetSpring = nil
        } else {
            sheetTiming = nil
            sheetSpring = SpringMotion(from: sheetValue, to: target, velocity: velocity,
                                       startedAt: time, spring: SavedPlacesMapMotion.SPRINGS.sheetRise)
        }
        releaseGrab(at: time)
        lastTouchedAt = time
        return changed
    }

    func cancelDrag(at time: Double) {
        commitSheet(at: time)
        releaseGrab(at: time)
        lastTouchedAt = time
    }

    /// Accessibility increment / decrement / activate: the same move a finger makes. Returns whether the detent changed.
    func sheetTo(_ target: Double, at time: Double) -> Bool {
        enterTouch(at: time)
        commitCluster(at: time)
        commitSheet(at: time)
        let from = Int(SavedPlacesMapMotion.clamp01(sheetValue).rounded())
        if reduced {
            sheetTiming = TimingMotion(from: sheetValue, to: target, startedAt: time)
            sheetSpring = nil
        } else {
            sheetTiming = nil
            sheetSpring = SpringMotion(from: sheetValue, to: target, velocity: 0, startedAt: time,
                                       spring: SavedPlacesMapMotion.SPRINGS.sheetRise)
        }
        lastTouchedAt = time
        return Int(target.rounded()) != from
    }

    func beginPress(_ hit: Hit, at time: Double) {
        enterTouch(at: time)
        pressedHit = hit.rawValue
        pressStartedAt = time
        pressReleaseAt = -1e9
    }

    func endPress(at time: Double) {
        pressReleaseFrom = manualPress(at: time)
        pressReleaseAt = time
        lastTouchedAt = time
    }

    func toggleVisited(at time: Double) -> Int? {
        enterTouch(at: time)
        guard time - lastToggleAt >= SavedPlacesMapMotion.TOGGLE_GAP else { return nil }
        visitDirection = visitDirection == 1 ? 0 : 1
        visitStartedAt = time
        lastToggleAt = time
        lastTouchedAt = time
        return visitDirection
    }

    func popMarker(_ index: Int, at time: Double) {
        enterTouch(at: time)
        tappedMarker = index
        markerTappedAt = time
        lastTouchedAt = time
    }

    func sheet(at time: Double) -> Double {
        if mode == .auto || restartedAuto(at: time) { return frame(at: time).sheet }
        return manualSheet(at: time)
    }

    func shouldTick(at time: Double) -> Bool {
        if contactActive { return true }
        if mode == .auto { return loops }
        if loops, lastTouchedAt > -1e8 { return true }
        return lastTouchedAt > -1e8 && time - lastTouchedAt < 1.6
    }

    private func restartedAuto(at time: Double) -> Bool {
        guard loops, mode == .touch, !contactActive, lastTouchedAt > -1e8 else { return false }
        let returnAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
        return time >= returnRestartAt(returnAt: returnAt, before: manualFrame(at: returnAt))
    }

    private func returning(at time: Double) -> Bool {
        guard loops, mode == .touch, !contactActive, lastTouchedAt > -1e8 else { return false }
        let returnAt = lastTouchedAt + SavedPlacesMapMotion.RESUME_AFTER
        guard time >= returnAt else { return false }
        return time < returnRestartAt(returnAt: returnAt, before: manualFrame(at: returnAt))
    }

    private func manualFrame(at time: Double) -> SavedPlacesMapMotion.Frame {
        let visit = manualVisit(at: time)
        return SavedPlacesMapMotion.Frame(
            sheet: manualSheet(at: time),
            cluster: manualCluster(at: time),
            fill: visit.fill,
            draw: visit.draw,
            roll: visit.roll,
            badge: visit.badge,
            visitPop: visit.pop,
            tapIndex: tappedMarker,
            tapPop: reduced ? 1 : SavedPlacesMapMotion.popScale(time - markerTappedAt),
            press: manualPress(at: time),
            pressTarget: pressedHit,
            grab: manualGrab(at: time)
        )
    }

    private func manualSheet(at time: Double) -> Double {
        sheetTiming?.value(at: time) ?? sheetSpring?.value(at: time) ?? sheetValue
    }

    private func manualCluster(at time: Double) -> Double {
        if reduced { return SavedPlacesMapMotion.clamp01(manualSheet(at: time)) }
        return move(clusterAnchor, toward: SavedPlacesMapMotion.clamp01(manualSheet(at: time)),
                    by: max(0, time - clusterAnchorAt) / SavedPlacesMapMotion.CLUSTER.dur)
    }

    private func manualVisit(at time: Double) -> SavedPlacesMapMotion.VisitFrame {
        SavedPlacesMapMotion.visitFrame(direction: visitDirection,
                                        seconds: (time - visitStartedAt), reduced: reduced)
    }

    private func manualPress(at time: Double) -> Double {
        if pressReleaseAt > -1e8 {
            if reduced {
                return pressReleaseFrom * (1 - SavedPlacesMapMotion.easeOut(
                    SavedPlacesMapMotion.segment(time, pressReleaseAt, pressReleaseAt + SavedPlacesMapMotion.PRESS.duration)
                ))
            }
            return pressReleaseFrom * (1 - SavedPlacesMapMotion.springStep(
                time - pressReleaseAt, SavedPlacesMapMotion.SPRINGS.press
            ))
        }
        if pressStartedAt <= -1e8 { return 0 }
        return SavedPlacesMapMotion.easeOut(
            SavedPlacesMapMotion.segment(time, pressStartedAt, pressStartedAt + SavedPlacesMapMotion.PRESS.duration)
        )
    }

    private func manualGrab(at time: Double) -> Double {
        if grabReleaseAt > -1e8 {
            return grabReleaseFrom * (1 - SavedPlacesMapMotion.easeOut(
                SavedPlacesMapMotion.segment(time, grabReleaseAt,
                                             grabReleaseAt + SavedPlacesMapMotion.GRAB.fadeOut)
            ))
        }
        if grabStartedAt <= -1e8 { return 0 }
        return SavedPlacesMapMotion.easeOut(
            SavedPlacesMapMotion.segment(time, grabStartedAt,
                                         grabStartedAt + SavedPlacesMapMotion.GRAB.fadeIn)
        )
    }

    private func releaseGrab(at time: Double) {
        grabReleaseFrom = manualGrab(at: time)
        grabReleaseAt = time
    }

    private func capture(_ frame: SavedPlacesMapMotion.Frame, at time: Double) {
        sheetValue = frame.sheet
        sheetSpring = nil
        sheetTiming = nil
        clusterAnchor = frame.cluster
        clusterAnchorAt = time
        pressedHit = frame.pressTarget
        pressReleaseFrom = frame.press
        pressReleaseAt = time
        grabReleaseFrom = frame.grab
        grabReleaseAt = time
        tappedMarker = frame.tapIndex
    }

    private func captureVisitFromAuto(at time: Double) {
        let phase = SavedPlacesMapMotion.normalized(time - autoAnchor)
        if phase >= SavedPlacesMapMotion.T.check, phase < SavedPlacesMapMotion.T.uncheck {
            visitDirection = 1
            visitStartedAt = time - (phase - SavedPlacesMapMotion.T.check)
        } else if phase >= SavedPlacesMapMotion.T.uncheck {
            visitDirection = 0
            visitStartedAt = time - (phase - SavedPlacesMapMotion.T.uncheck)
        } else {
            visitDirection = 0
            visitStartedAt = -1e9
        }
    }

    private func commitSheet(at time: Double) {
        sheetValue = manualSheet(at: time)
        sheetSpring = nil
        sheetTiming = nil
    }

    private func commitCluster(at time: Double) {
        clusterAnchor = manualCluster(at: time)
        clusterAnchorAt = time
    }

    private func move(_ value: Double, toward target: Double, by amount: Double) -> Double {
        if abs(target - value) <= amount { return target }
        return value + (target > value ? amount : -amount)
    }

    private func returnFrame(at time: Double, returnAt: Double,
                             before: SavedPlacesMapMotion.Frame) -> SavedPlacesMapMotion.Frame {
        let seconds = max(0, time - returnAt)
        let rawSheet = SavedPlacesMapMotion.springValue(
            from: before.sheet, to: 0, velocity: 0, seconds: seconds,
            spring: SavedPlacesMapMotion.SPRINGS.sheetFall
        )
        let sheet = abs(rawSheet) < 0.001 ? 0 : rawSheet
        let cluster = move(before.cluster, toward: SavedPlacesMapMotion.clamp01(sheet), by: seconds)
        let visit = visitDirection == 1
            ? SavedPlacesMapMotion.visitFrame(direction: 0, seconds: seconds, reduced: false)
            : manualVisit(at: time)
        return SavedPlacesMapMotion.Frame(
            sheet: sheet, cluster: cluster, fill: visit.fill, draw: visit.draw,
            roll: visit.roll, badge: visit.badge, visitPop: visit.pop,
            tapIndex: tappedMarker, tapPop: SavedPlacesMapMotion.popScale(time - markerTappedAt),
            press: manualPress(at: time), pressTarget: pressedHit, grab: manualGrab(at: time)
        )
    }

    private func returnRestartAt(returnAt: Double, before: SavedPlacesMapMotion.Frame) -> Double {
        let visitWait = visitDirection == 1 ? 0.6 : 0.0
        for step in 1...360 {
            let seconds = Double(step) / 120
            let rawSheet = SavedPlacesMapMotion.springValue(
                from: before.sheet, to: 0, velocity: 0, seconds: seconds,
                spring: SavedPlacesMapMotion.SPRINGS.sheetFall
            )
            let sheet = abs(rawSheet) < 0.001 ? 0 : rawSheet
            let cluster = move(before.cluster, toward: SavedPlacesMapMotion.clamp01(sheet), by: seconds)
            if abs(sheet) < 0.001, cluster == 0, seconds > visitWait { return returnAt + seconds }
        }
        return returnAt + 3
    }
}
