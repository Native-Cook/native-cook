import Foundation

final class NightSleepScoreController {
    enum Cadence: Equatable {
        case stopped, animation
        var interval: TimeInterval { 1.0 / 60.0 }
    }

    private struct Settle {
        var from: Double
        var to: Double
        var startedAt: TimeInterval

        func value(at now: TimeInterval) -> Double {
            let elapsed = min(1, max(0, now - startedAt))
            let mass = 0.8
            let damping = 34.0
            let stiffness = 460.0
            let decay = damping / (2 * mass)
            let angular = sqrt(stiffness / mass - decay * decay)
            let offset = from - to
            return to + exp(-decay * elapsed) *
                (offset * cos(angular * elapsed) + decay * offset / angular * sin(angular * elapsed))
        }

        func finished(at now: TimeInterval) -> Bool { now - startedAt >= 0.8 }
    }

    private struct ScrubSnapshot {
        var startedAt: TimeInterval
        var selectedMinute: Double?
        var lastTouch: TimeInterval?
        var settle: Settle?
        var quietFadeStarted: TimeInterval?
    }

    private(set) var autoPlay = false
    private(set) var quiet = false
    private(set) var active = true
    private(set) var visible = true
    private(set) var selectedMinute: Double?
    private(set) var fingerDown = false

    private var appeared = false
    private var origin = 0.0
    private var pausedAt: TimeInterval?
    private var lastTouch: TimeInterval?
    private var settle: Settle?
    private var quietFadeStarted: TimeInterval?
    private var scrubSnapshot: ScrubSnapshot?

    private var isRunning: Bool { active && visible }
    private func logicalTime(_ at: TimeInterval) -> TimeInterval { pausedAt ?? at }

    func appear(autoPlay: Bool, quiet: Bool, at now: TimeInterval) {
        guard !appeared else { return }
        appeared = true
        self.autoPlay = autoPlay
        self.quiet = quiet
        origin = logicalTime(now)
    }

    func setAutoPlay(_ enabled: Bool, at now: TimeInterval) {
        autoPlay = enabled
        let time = logicalTime(now)
        if enabled {
            if selectedMinute == nil { origin = time }
            else { lastTouch = time }
        } else {
            lastTouch = nil
        }
    }

    func setQuiet(_ enabled: Bool, at now: TimeInterval) {
        guard quiet != enabled else { return }
        let time = logicalTime(now)
        cancelHeldTouch(at: time)
        quiet = enabled
        settle = nil
        if selectedMinute != nil {
            quietFadeStarted = time
            lastTouch = autoPlay ? time : nil
        } else {
            quietFadeStarted = nil
            origin = time
        }
    }

    func setActive(_ enabled: Bool, at now: TimeInterval) {
        guard active != enabled else { return }
        active = enabled
        updateSuspension(at: now)
    }

    func setVisible(_ enabled: Bool, at now: TimeInterval,
                    clearSelection: Bool = false) {
        guard visible != enabled || clearSelection else { return }
        if clearSelection { reset(at: now) }
        visible = enabled
        updateSuspension(at: now)
    }

    func beginScrub(minute: Double, at now: TimeInterval) {
        guard !fingerDown else {
            updateScrub(minute: minute)
            return
        }
        let time = logicalTime(now)
        scrubSnapshot = ScrubSnapshot(
            startedAt: time,
            selectedMinute: selectedMinute,
            lastTouch: lastTouch,
            settle: settle,
            quietFadeStarted: quietFadeStarted
        )
        settle = nil
        quietFadeStarted = quiet ? time : nil
        selectedMinute = bounded(minute)
        fingerDown = true
        lastTouch = nil
    }

    func updateScrub(minute: Double) {
        guard fingerDown else { return }
        selectedMinute = bounded(minute)
    }

    func endScrub(at now: TimeInterval) {
        guard fingerDown, let minute = selectedMinute else { return }
        let time = logicalTime(now)
        fingerDown = false
        scrubSnapshot = nil
        lastTouch = time
        if quiet {
            settle = nil
            quietFadeStarted = quietFadeStarted ?? time
            return
        }
        let index = NightSleepScoreData.segmentIndex(at: minute)
        let midpoint = Double(NightSleepScoreData.segmentStart(index)) +
            Double(NightSleepScoreData.segments[index].minutes) / 2
        if abs(midpoint - minute) > 0.01 {
            settle = Settle(from: minute, to: midpoint, startedAt: time)
            selectedMinute = midpoint
        }
    }

    func cancelHeldTouch(at now: TimeInterval) {
        guard fingerDown else { return }
        let snapshot = scrubSnapshot
        let heldDuration = snapshot.map { max(0, logicalTime(now) - $0.startedAt) } ?? 0
        fingerDown = false
        selectedMinute = snapshot?.selectedMinute
        lastTouch = snapshot?.lastTouch.map { $0 + heldDuration }
        if var restoredSettle = snapshot?.settle {
            restoredSettle.startedAt += heldDuration
            settle = restoredSettle
        } else {
            settle = nil
        }
        quietFadeStarted = snapshot?.quietFadeStarted.map { $0 + heldDuration }
        if snapshot?.selectedMinute == nil { origin += heldDuration }
        scrubSnapshot = nil
    }

    func select(stage: NightSleepScoreStage, at now: TimeInterval) {
        let matches = NightSleepScoreData.segments.indices.filter {
            NightSleepScoreData.segments[$0].stage == stage
        }
        guard !matches.isEmpty else { return }
        let current = selectedMinute.map { NightSleepScoreData.segmentIndex(at: $0) }
        let index: Int
        if let current, NightSleepScoreData.segments[current].stage == stage {
            index = matches.first(where: { $0 > current }) ?? matches[0]
        } else {
            index = matches[0]
        }
        selectSegment(index, at: now)
    }

    func adjustStage(forward: Bool, at now: TimeInterval) {
        let stages = NightSleepScoreStage.allCases
        let currentStage = selectedMinute.map {
            NightSleepScoreData.segments[NightSleepScoreData.segmentIndex(at: $0)].stage
        }
        let current = currentStage.flatMap { stages.firstIndex(of: $0) } ?? (forward ? -1 : stages.count)
        let next = (current + (forward ? 1 : -1) + stages.count) % stages.count
        select(stage: stages[next], at: now)
    }

    func reset(at now: TimeInterval) {
        selectedMinute = nil
        fingerDown = false
        lastTouch = nil
        settle = nil
        quietFadeStarted = nil
        scrubSnapshot = nil
        origin = logicalTime(now)
    }

    func frame(at now: TimeInterval) -> NightSleepScoreFrame {
        let time = logicalTime(now)
        if let target = selectedMinute {
            var minute = target
            if let settle {
                minute = settle.value(at: time)
                if settle.finished(at: time) { self.settle = nil }
            }
            let presence: Double
            if quiet {
                presence = NightSleepScoreMotion.easeOutCubic(
                    (time - (quietFadeStarted ?? time - 0.12)) / 0.12
                )
            } else if autoPlay, !fingerDown, let lastTouch, time - lastTouch > 2 {
                presence = 1 - NightSleepScoreMotion.easeInOut((time - lastTouch - 2) / 0.25)
                if presence <= 0.000_001 {
                    selectedMinute = nil
                    self.lastTouch = nil
                    settle = nil
                    origin = lastTouch + 2.25
                    return NightSleepScoreMotion.frame(at: max(0, time - origin))
                }
            } else {
                presence = 1
            }
            let index = NightSleepScoreData.segmentIndex(at: minute)
            return NightSleepScoreFrame(
                cursorProgress: NightSleepScoreMotion.clamp(minute / Double(NightSleepScoreData.windowMinutes)),
                selectionPresence: presence,
                segmentIndex: index,
                isRest: presence == 0,
                night: presence, reveal: 1, meteor: -1
            )
        }
        if autoPlay, !quiet, visible {
            return NightSleepScoreMotion.frame(at: max(0, time - origin))
        }
        return .rest
    }

    func cadence(at now: TimeInterval) -> Cadence {
        guard isRunning else { return .stopped }
        if fingerDown {
            if !quiet { return .animation }
            if let quietFadeStarted,
               logicalTime(now) - quietFadeStarted < 0.12 { return .animation }
            return .stopped
        }
        if settle != nil { return .animation }
        if quiet, let quietFadeStarted, selectedMinute != nil,
           logicalTime(now) - quietFadeStarted < 0.12 { return .animation }
        if autoPlay, !quiet { return .animation }
        return .stopped
    }

    var hasManualSelection: Bool { selectedMinute != nil }

    private func selectSegment(_ index: Int, at now: TimeInterval) {
        let time = logicalTime(now)
        settle = nil
        selectedMinute = Double(NightSleepScoreData.segmentStart(index))
        fingerDown = false
        scrubSnapshot = nil
        lastTouch = autoPlay ? time : nil
        quietFadeStarted = quiet ? time : nil
    }

    private func bounded(_ minute: Double) -> Double {
        min(Double(NightSleepScoreData.windowMinutes) - 0.000_001, max(0, minute))
    }

    private func updateSuspension(at now: TimeInterval) {
        if !isRunning {
            cancelHeldTouch(at: logicalTime(now))
            if pausedAt == nil { pausedAt = now }
            return
        }
        guard let pausedAt else { return }
        let pause = max(0, now - pausedAt)
        self.pausedAt = nil
        origin += pause
        lastTouch = lastTouch.map { $0 + pause }
        quietFadeStarted = quietFadeStarted.map { $0 + pause }
        if var settle {
            settle.startedAt += pause
            self.settle = settle
        }
    }
}
