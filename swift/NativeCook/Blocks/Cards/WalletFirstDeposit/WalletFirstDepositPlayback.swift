import Foundation

/// Event-owned clock. Drawing samples it without writing state on every frame.
struct WalletFirstDepositPlayback {
    enum Action { case fund, empty }
    private enum Mode { case automatic, stationary, funding, returning }

    private var mode = Mode.stationary
    private var from = 0.0
    private var started = 0.0
    private var resume = Double.infinity
    private var heldAt: Double?
    private var inactiveAt: Double?
    private var sequence = 0
    private(set) var autoPlay = false

    var isHeld: Bool { heldAt != nil }

    mutating func start(autoPlay: Bool, at now: Double, phase: Double = 0) {
        let frozen = inactiveAt
        self = Self()
        inactiveAt = frozen
        self.autoPlay = autoPlay
        started = frozen ?? now
        from = normalized(phase)
        mode = autoPlay ? .automatic : .stationary
    }

    mutating func setAutoPlay(_ enabled: Bool, at now: Double) {
        let current = phase(at: now)
        let time = effectiveTime(now)
        autoPlay = enabled
        if mode == .automatic || mode == .stationary {
            from = current
            started = time
            resume = time + 2
            mode = .stationary
        }
    }

    mutating func beginTouch(at now: Double) {
        guard heldAt == nil else { return }
        heldAt = now
    }

    mutating func endTouch(_ action: Action?, at now: Double) {
        let current = phase(at: now)
        let time = inactiveAt ?? now
        if action == nil && (mode == .funding || mode == .returning) {
            // Cancellation releases the finger, not the in-flight state transition.
            if let heldAt { started += max(0, time - heldAt) }
            heldAt = nil
            resume = time + 2
            return
        }
        heldAt = nil
        from = current
        started = time
        resume = time + 2
        mode = .stationary
        sequence += 1
        switch action {
        case .fund where current < 2.75 || current >= 9.7:
            from = current >= 9.7 ? 1.4 : max(1.4, current)
            mode = .funding
        case .empty where current >= 3.3 && current < 9:
            from = current >= 6.2 ? max(8.4, current) : current
            mode = .returning
        default:
            break
        }
    }

    mutating func setActive(_ active: Bool, at now: Double) {
        if !active, inactiveAt == nil {
            // UIKit can cancel a gesture without delivering its normal end callback.
            if heldAt != nil { endTouch(nil, at: now) }
            inactiveAt = now
        } else if active, let paused = inactiveAt {
            let duration = max(0, now - paused)
            started += duration
            resume += duration
            if let heldAt { self.heldAt = heldAt + duration }
            inactiveAt = nil
        }
    }

    func shouldTick(at now: Double) -> Bool {
        if inactiveAt != nil { return false }
        if autoPlay { return true }
        let elapsed = max(0, effectiveTime(now) - started)
        switch mode {
        case .automatic, .stationary: return false
        case .funding: return elapsed < max(0, 6.2 - from)
        case .returning: return elapsed < max(0, 6.2 - from) / 2.5 + 10 - max(8.4, from)
        }
    }

    func phase(at now: Double) -> Double {
        let time = effectiveTime(now)
        let elapsed = max(0, time - started)
        switch mode {
        case .automatic:
            return normalized(from + elapsed)
        case .stationary:
            return autoPlay && time >= resume ? normalized(from + time - resume) : from
        case .funding:
            let duration = max(0, 6.2 - from)
            if elapsed < duration { return from + elapsed }
            return afterTransition(endpoint: 6.2, duration: duration, time: time)
        case .returning:
            let finish = max(0, 6.2 - from) / 2.5
            if elapsed < finish { return from + elapsed * 2.5 }
            let returnStart = max(8.4, from)
            let duration = finish + 10 - returnStart
            if elapsed < duration { return returnStart + elapsed - finish }
            return afterTransition(endpoint: 0, duration: duration, time: time)
        }
    }

    /// A stable token at the manually requested landing, never during automatic cycles.
    func landingToken(at now: Double) -> Int? {
        guard mode == .funding, from < 3.22 else { return nil }
        let elapsed = effectiveTime(now) - started
        return elapsed >= 3.22 - from && elapsed < 6.2 - from ? sequence : nil
    }

    private func afterTransition(endpoint: Double, duration: Double, time: Double) -> Double {
        let nextStart = max(resume, started + duration)
        return autoPlay && time >= nextStart ? normalized(endpoint + time - nextStart) : endpoint
    }

    private func effectiveTime(_ now: Double) -> Double {
        min(now, heldAt ?? now, inactiveAt ?? now)
    }

    private func normalized(_ time: Double) -> Double {
        (time.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
    }
}
