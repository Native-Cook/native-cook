import Foundation

struct PastePlacesImportPlayback {
    enum Control { case paste, start, close }

    private enum Mode { case automatic, stationary, playing }

    private var mode: Mode = .stationary
    private var from = 0.0
    private var startedAt = 0.0
    private var stopAt = PastePlacesImportMotion.filledAt
    private var speed = 1.0
    private var thenReset = false
    private var manualSuccess = false
    private var resumeAt = Double.infinity
    private var heldAt: Double?
    private var inactiveAt: Double?
    private var sequence = 0
    private(set) var autoPlay = false

    var isHeld: Bool { heldAt != nil }

    mutating func start(autoPlay: Bool, at now: Double, phase: Double = 0) {
        let inactive = inactiveAt
        self = Self()
        inactiveAt = inactive
        self.autoPlay = autoPlay
        from = PastePlacesImportMotion.normalized(phase)
        startedAt = inactive ?? now
        mode = autoPlay ? .automatic : .stationary
    }

    mutating func setAutoPlay(_ enabled: Bool, at now: Double) {
        let current = phase(at: now)
        autoPlay = enabled
        from = current
        startedAt = effectiveTime(now)
        resumeAt = effectiveTime(now) + PastePlacesImportMotion.resumeAfter
        if mode == .automatic || mode == .stationary { mode = .stationary }
    }

    mutating func beginTouch(at now: Double) {
        guard heldAt == nil else { return }
        let current = phase(at: now)
        if isAutomatic(at: now) {
            from = current
            startedAt = now
            mode = .stationary
        }
        heldAt = now
    }

    mutating func endTouchWithoutAction(at now: Double) {
        guard let heldAt else { return }
        if mode == .playing { startedAt += max(0, now - heldAt) }
        self.heldAt = nil
        resumeAt = now + PastePlacesImportMotion.resumeAfter
    }

    mutating func activate(_ control: Control, at now: Double, hasDraft: Bool = false) {
        let current = phase(at: now)
        heldAt = nil
        from = current
        startedAt = now
        resumeAt = now + PastePlacesImportMotion.resumeAfter
        speed = 1
        thenReset = false
        manualSuccess = false
        sequence += 1

        switch control {
        case .paste where isEnabled(.paste, at: current, hasDraft: hasDraft):
            from = PastePlacesImportMotion.pasteAt
            stopAt = PastePlacesImportMotion.filledAt
            mode = .playing
        case .start where isEnabled(.start, at: current, hasDraft: hasDraft):
            // The user's own text starts the import of the sample note from the start of the scan.
            let own = hasDraft && Self.atRest(current)
            from = own ? PastePlacesImportMotion.startAt : max(current, PastePlacesImportMotion.startAt)
            stopAt = PastePlacesImportMotion.foundAtTime
            manualSuccess = true
            mode = .playing
        case .close where isEnabled(.close, at: current, hasDraft: hasDraft):
            if current < PastePlacesImportMotion.startAt {
                stopAt = PastePlacesImportMotion.pasteAt
                speed = -1.6
            } else if current < PastePlacesImportMotion.foundAtTime {
                stopAt = PastePlacesImportMotion.foundAtTime
                speed = 2.5
                thenReset = true
            } else {
                from = max(current, PastePlacesImportMotion.resetAt)
                stopAt = PastePlacesImportMotion.loop
            }
            mode = .playing
        default:
            mode = .stationary
        }
    }

    mutating func setActive(_ active: Bool, at now: Double) {
        if !active, inactiveAt == nil {
            if heldAt != nil { endTouchWithoutAction(at: now) }
            inactiveAt = now
        } else if active, let pausedAt = inactiveAt {
            let pause = max(0, now - pausedAt)
            startedAt += pause
            resumeAt += pause
            if let heldAt { self.heldAt = heldAt + pause }
            inactiveAt = nil
        }
    }

    /// At rest: nothing of the sample flow on screen (the text area is the user's).
    static func atRest(_ time: Double) -> Bool {
        time < PastePlacesImportMotion.pastePress.down || time >= PastePlacesImportMotion.restAgain
    }

    func isEnabled(_ control: Control, at time: Double, hasDraft: Bool = false) -> Bool {
        switch control {
        case .paste:
            Self.atRest(time)
        case .start:
            (time >= PastePlacesImportMotion.filledAt - 0.3 && time < PastePlacesImportMotion.startAt)
                || (hasDraft && Self.atRest(time))
        case .close:
            time >= PastePlacesImportMotion.pasteAt && time < PastePlacesImportMotion.restAgain
        }
    }

    func phase(at now: Double) -> Double {
        let time = effectiveTime(now)
        let elapsed = max(0, time - startedAt)
        switch mode {
        case .automatic:
            return PastePlacesImportMotion.normalized(from + elapsed)
        case .stationary:
            if autoPlay, time >= resumeAt {
                return PastePlacesImportMotion.normalized(from + time - resumeAt)
            }
            return from
        case .playing:
            return playingPhase(elapsed: elapsed, time: time)
        }
    }

    func shouldTick(at now: Double) -> Bool {
        guard inactiveAt == nil, heldAt == nil else { return false }
        let time = effectiveTime(now)
        switch mode {
        case .automatic:
            return PastePlacesImportMotion.quietIndex(at: phase(at: now)) == nil
        case .stationary:
            guard autoPlay, time >= resumeAt else { return false }
            return PastePlacesImportMotion.quietIndex(at: phase(at: now)) == nil
        case .playing:
            let duration = playingDuration
            if time - startedAt < duration { return true }
            let nextStart = max(resumeAt, startedAt + duration)
            guard autoPlay, time >= nextStart else { return false }
            return PastePlacesImportMotion.quietIndex(at: phase(at: now)) == nil
        }
    }

    func nextWakeDelay(at now: Double) -> Double? {
        guard inactiveAt == nil, heldAt == nil, autoPlay else { return nil }
        let time = effectiveTime(now)
        let phase = phase(at: now)
        let nextStart: Double
        switch mode {
        case .automatic:
            nextStart = time
        case .stationary:
            if time < resumeAt { return resumeAt - time }
            nextStart = time
        case .playing:
            let complete = startedAt + playingDuration
            let restart = max(resumeAt, complete)
            if time < restart { return restart - time }
            nextStart = time
        }
        guard nextStart == time else { return nextStart - time }
        return PastePlacesImportMotion.nextMotionDelay(at: phase)
    }

    func successToken(at now: Double) -> Int? {
        guard mode == .playing, manualSuccess else { return nil }
        let current = phase(at: now)
        return current >= PastePlacesImportMotion.lastLand && current < PastePlacesImportMotion.foundAtTime
            ? sequence
            : nil
    }

    func scriptedPress(_ control: Control, at now: Double) -> Double {
        guard isAutomatic(at: now) else { return 0 }
        let time = phase(at: now)
        switch control {
        case .paste:
            return PastePlacesImportMotion.press(at: time, interval: PastePlacesImportMotion.pastePress)
        case .start:
            return PastePlacesImportMotion.press(at: time, interval: PastePlacesImportMotion.startPress)
        case .close:
            return PastePlacesImportMotion.press(at: time, interval: PastePlacesImportMotion.closePress)
        }
    }

    private var playingDuration: Double {
        if thenReset {
            return max(0, stopAt - from) / max(0.001, speed)
                + PastePlacesImportMotion.loop - PastePlacesImportMotion.resetAt
        }
        return abs(stopAt - from) / max(0.001, abs(speed))
    }

    private func playingPhase(elapsed: Double, time: Double) -> Double {
        if thenReset {
            let first = max(0, stopAt - from) / max(0.001, speed)
            if elapsed < first { return from + elapsed * speed }
            let reset = PastePlacesImportMotion.resetAt + elapsed - first
            if reset < PastePlacesImportMotion.loop { return reset }
            return afterTransition(endpoint: 0, duration: playingDuration, time: time)
        }

        let duration = playingDuration
        if elapsed < duration { return from + elapsed * speed }
        let endpoint = speed < 0 || stopAt >= PastePlacesImportMotion.loop ? 0 : stopAt
        return afterTransition(endpoint: endpoint, duration: duration, time: time)
    }

    private func afterTransition(endpoint: Double, duration: Double, time: Double) -> Double {
        let nextStart = max(resumeAt, startedAt + duration)
        return autoPlay && time >= nextStart
            ? PastePlacesImportMotion.normalized(endpoint + time - nextStart)
            : endpoint
    }

    private func isAutomatic(at now: Double) -> Bool {
        let time = effectiveTime(now)
        switch mode {
        case .automatic:
            return true
        case .stationary:
            return autoPlay && time >= resumeAt
        case .playing:
            return autoPlay && time >= max(resumeAt, startedAt + playingDuration)
        }
    }

    private func effectiveTime(_ now: Double) -> Double {
        min(now, heldAt ?? now, inactiveAt ?? now)
    }
}
