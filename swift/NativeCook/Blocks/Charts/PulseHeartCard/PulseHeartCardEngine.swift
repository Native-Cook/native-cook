import Foundation
import SwiftUI

/// Everything the shader and the reading need for one frame (FRAME in HeartCanvas.tsx), plus the chart cursor.
struct PulseHeartCardFrame {
    var beat = PulseHeartCardMotion.Beat()
    var tau0 = PulseHeartCardMotion.never
    var tau1 = PulseHeartCardMotion.never
    var phi = PulseHeartCardEngine.restPhi
    var traceStart = 0.0
    var traceEnd = -1.0
    /// Selected chart sample, or nil.
    var selected: Int?
    var cursorOpacity = 0.0

    static let rest = PulseHeartCardFrame()
}

/// Who drives the heart: the port of the UI-thread state machine of PulseHeartCard.tsx.
/// The view samples it from `TimelineView`s; every call is idempotent for one timestamp.
final class PulseHeartCardEngine: @unchecked Sendable {
    /// The rest frame's pen position: no autoplay or scrub frame ever has it, so it marks "already at rest".
    static let restPhi = -99.0

    private enum Mode { case auto, manual, released, rest }
    private typealias M = PulseHeartCardMotion

    // Clock and state (all touched on the main thread only).
    private var mode: Mode = .rest
    private var clock = 0.0
    private var lastTimestamp = -1.0
    private var lastStep = -Double.infinity
    private var loopOrigin = 0.0
    private var phi = -99.0
    private var bpm = M.restBPM
    private var lastLub = -M.never
    private var prevLub = -M.never
    private var traceStart = 0.0
    private var traceEnd = -1.0
    private var releasedAt = -1.0
    private var selected = -1
    private var cursorOpacity = 0.0
    private var lastHapticAt = -1e9
    private var current = PulseHeartCardFrame.rest
    private(set) var autoPlay = false
    private(set) var quiet = false
    private(set) var foreground = true
    private var sleeping = false

    /// Called when the engine itself clears the selection (the idle reset), so the view can refresh.
    var onSelectionCleared: (() -> Void)?

    // Read by the timeline schedule (any thread).
    private let lock = NSLock()
    private var wakeAtStorage: Double?

    /// The wall time the next frame is needed, `.infinity` for "only after an interaction", nil for every frame.
    var wakeAt: Double? {
        lock.lock(); defer { lock.unlock() }
        return wakeAtStorage
    }

    private func setWake(_ value: Double?) {
        lock.lock(); wakeAtStorage = value; lock.unlock()
        sleeping = value != nil
    }

    var selectedIndex: Int? { selected >= 0 ? selected : nil }

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        mode = autoPlay ? .auto : .rest
    }

    // MARK: Frames

    /// The frame at wall time `time` (seconds, any monotonic reference).
    func frame(at time: Double) -> PulseHeartCardFrame {
        step(time)
        return composed()
    }

    private func composed() -> PulseHeartCardFrame {
        var frame = current
        frame.selected = selected >= 0 ? selected : nil
        frame.cursorOpacity = cursorOpacity
        return frame
    }

    private func resetFrame() {
        current = PulseHeartCardFrame.rest
    }

    /// Advance the clock to `time` and compute the frame (frameWorklet).
    private func step(_ time: Double) {
        if time <= lastStep { return }
        lastStep = time
        guard foreground else {
            lastTimestamp = -1
            setWake(.infinity)
            return
        }
        let previous = lastTimestamp
        lastTimestamp = time
        if previous < 0 { return }
        // A frame after an intended sleep keeps the whole gap: the autoplay loop follows the wall clock.
        let gap = max(time - previous, 0)
        let dt = sleeping ? min(gap, 60) : min(gap, 0.1)
        let now = clock + dt
        clock = now
        if quiet {
            setWake(.infinity)
            return
        }
        let currentMode = mode
        if currentMode == .rest {
            setWake(.infinity)
            return
        }
        let tau0: Double
        let tau1: Double
        let untilNext: Double
        let period: Double
        let p: Double
        let start: Double
        let end: Double
        if currentMode == .auto {
            let pulse = M.autoplayAt(now - loopOrigin)
            (tau0, tau1, untilNext, period) = (pulse.tau0, pulse.tau1, pulse.untilNext, pulse.period)
            (p, start, end) = (pulse.phi, pulse.traceStart, pulse.traceEnd)
        } else {
            if currentMode == .released {
                let idle = now - releasedAt
                if idle >= M.idleReset {
                    reset()
                    notifyCleared()
                    return
                }
                // The selection fades over the last 200 ms instead of vanishing.
                cursorOpacity = min(1, (M.idleReset - idle) / M.cursorFade)
            }
            period = 60 / bpm
            let before = phi
            let after = before + dt / period
            phi = after
            if currentMode == .manual && after.rounded(.down) > before.rounded(.down) {
                // The lub happened inside this frame: date it exactly.
                let at = now - (after - after.rounded(.down)) * period
                prevLub = lastLub
                lastLub = at
            }
            tau0 = min(now - lastLub, M.never)
            tau1 = min(now - prevLub, M.never)
            untilNext = currentMode == .manual ? (after.rounded(.up) - after) * period : M.never
            p = after
            start = traceStart
            end = traceEnd
        }
        let beat = M.beatAt(tau0: tau0, tau1: tau1, untilNext: untilNext, period: period)
        if currentMode == .auto && M.autoplayIdle(beat, tau0: tau0, phi: p, traceStart: start, traceEnd: end) {
            // The loop's rest stretch: the pin's frame is written once, then nothing changes, so the shader
            // stops redrawing until the next beat gathers.
            if current.phi != Self.restPhi { resetFrame() }
            let loopTime = (now - loopOrigin).truncatingRemainder(dividingBy: M.loop)
            let wait = loopTime < M.wakeLoopTime ? M.wakeLoopTime - loopTime : M.loop - loopTime + M.wakeLoopTime
            setWake(time + wait)
            return
        }
        setWake(nil)
        current = PulseHeartCardFrame(beat: beat, tau0: tau0, tau1: tau1, phi: p, traceStart: start, traceEnd: end)
    }

    private func notifyCleared() {
        if let onSelectionCleared { DispatchQueue.main.async(execute: onSelectionCleared) }
    }

    // MARK: Interaction (resetUI, selectSampleUI, releaseSelectionUI)

    /// Back to the pin's frame; autoplay restarts its loop from t = 0.
    func reset() {
        selected = -1
        cursorOpacity = 0
        bpm = M.restBPM
        releasedAt = -1
        loopOrigin = clock
        mode = autoPlay ? .auto : .rest
        resetFrame()
        setWake(nil)
    }

    /// Reset from outside (the Heart chip, a prop change).
    func resetNow(at time: Double) {
        step(time)
        reset()
    }

    /// Take over without a jump: continue the autoplay beat and trace if they are on screen, otherwise
    /// start a fresh sweep with the pen at the left edge. Returns whether the sample changed and whether
    /// the caller should play a selection haptic.
    @discardableResult
    func select(index rawIndex: Int, at time: Double) -> (changed: Bool, haptic: Bool) {
        step(time)
        let index = M.clampIndex(Double(rawIndex))
        let previous = selected
        let now = clock
        if mode != .manual {
            if mode == .auto {
                let p = M.autoplayAt(now - loopOrigin)
                if p.phi >= p.traceStart && p.phi <= p.traceEnd + M.traceFadeOut {
                    phi = p.phi
                    traceStart = p.traceStart
                    lastLub = now - p.tau0
                    prevLub = now - p.tau1
                } else {
                    phi = -M.traceLead
                    traceStart = -M.traceLead
                    lastLub = now - M.never
                    prevLub = now - M.never
                }
            } else if mode == .rest || phi > traceEnd + M.traceFadeOut {
                if mode == .rest {
                    phi = -M.traceLead
                    lastLub = now - M.never
                    prevLub = now - M.never
                }
                traceStart = phi
            }
            traceEnd = 1e6
            mode = .manual
        }
        selected = index
        bpm = Double(M.samples[index])
        releasedAt = -1
        cursorOpacity = 1
        setWake(nil)
        var haptic = false
        if previous != index && !quiet && now - lastHapticAt >= 0.1 {
            lastHapticAt = now
            haptic = true
        }
        return (previous != index, haptic)
    }

    func release(at time: Double) {
        step(time)
        guard mode == .manual else { return }
        mode = .released
        releasedAt = clock
        traceEnd = phi
        setWake(nil)
    }

    // MARK: Environment

    func setQuiet(_ value: Bool, at time: Double) {
        step(time)
        quiet = value
        resetFrame()
        if !value { loopOrigin = clock }
        setWake(value ? .infinity : nil)
    }

    func setForeground(_ active: Bool, at time: Double) {
        foreground = active
        lastTimestamp = -1
        lastStep = -.infinity
        if active {
            setWake(nil)
            if mode == .manual { release(at: time) }
        } else {
            setWake(.infinity)
        }
    }

    func setAutoPlay(_ value: Bool, at time: Double) {
        step(time)
        autoPlay = value
        reset()
    }
}

/// A timeline that ticks every frame while the heart moves and sleeps while it rests.
struct PulseHeartCardSchedule: TimelineSchedule {
    let engine: PulseHeartCardEngine
    /// Changes whenever the view restarts the schedule (an interaction, a scene change).
    let generation: Int

    func entries(from start: Date, mode: TimelineScheduleMode) -> Iterator {
        Iterator(engine: engine, cursor: start.timeIntervalSinceReferenceDate)
    }

    struct Iterator: Sequence, IteratorProtocol {
        let engine: PulseHeartCardEngine
        var cursor: Double
        private var first = true

        init(engine: PulseHeartCardEngine, cursor: Double) {
            self.engine = engine
            self.cursor = cursor
        }

        mutating func next() -> Date? {
            if first {
                first = false
                return Date(timeIntervalSinceReferenceDate: cursor)
            }
            if let wake = engine.wakeAt {
                // Asleep: nothing to draw until the wake time (or until an interaction restarts the schedule).
                if wake.isInfinite { return nil }
                cursor = Swift.max(wake, cursor + 1.0 / 60.0)
            } else {
                cursor = Swift.max(cursor + 1.0 / 60.0, Date.timeIntervalSinceReferenceDate)
            }
            return Date(timeIntervalSinceReferenceDate: cursor)
        }
    }
}
