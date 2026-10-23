import Foundation

/// Event-owned clocks: TimelineView samples these values without writing state per frame.
struct FindFriendsInvitePlayback {
    typealias M = FindFriendsInviteMotion

    private struct Clock {
        var value: Double
        var anchor: TimeInterval
        var held = false

        /// Elapsed value, stopped just before the release while a finger holds the press.
        func running(at now: TimeInterval, release: Double? = nil) -> Double {
            let result = value + max(0, now - anchor)
            if held, let release { return min(result, release - 0.001) }
            return result
        }

        func sample(at now: TimeInterval, end: Double, release: Double? = nil) -> Double {
            min(running(at: now, release: release), end)
        }

        func finish(at now: TimeInterval, end: Double) -> TimeInterval {
            now + max(0, end - sample(at: now, end: end))
        }
    }

    private enum Mode { case automatic, manual }
    private var mode: Mode
    private var anchor: TimeInterval
    private var invite: Clock?
    private var scan: Clock?
    private var cup: Clock?
    private var cupTap: Clock?
    private var inviteDrivesCup = false
    private var pausedAt: TimeInterval?
    /// The loop is on: autoPlay and neither Reduce Motion nor a screen reader.
    private(set) var autoPlay: Bool

    init(autoPlay: Bool, at now: TimeInterval) {
        self.autoPlay = autoPlay
        mode = autoPlay ? .automatic : .manual
        anchor = now
    }

    /// The loop's start while it is running, nil in manual mode.
    var loopAnchor: TimeInterval? { mode == .automatic && autoPlay ? anchor : nil }

    /// Turning the loop off plays the running clocks to rest; turning it on waits for the idle stretch.
    mutating func setAutoPlay(_ enabled: Bool, at now: TimeInterval, reduced: Bool) {
        if autoPlay == enabled { return }
        if !enabled { interrupt(at: now, reduced: reduced) }
        autoPlay = enabled
        if !enabled { mode = .manual }
    }

    mutating func setForeground(_ foreground: Bool, at now: TimeInterval) {
        if !foreground, pausedAt == nil { pausedAt = now }
        if foreground, let pausedAt {
            let delta = now - pausedAt
            anchor += delta
            invite?.anchor += delta
            scan?.anchor += delta
            cup?.anchor += delta
            cupTap?.anchor += delta
            self.pausedAt = nil
        }
    }

    private static func ends(reduced: Bool) -> (invite: Double, scan: Double) {
        (reduced ? M.Reduced.inviteEnd : M.inviteEnd, reduced ? M.Reduced.scanEnd : M.Scan.end)
    }

    func clocks(at now: TimeInterval, reduced: Bool) -> M.Clocks {
        let time = pausedAt ?? now
        if mode == .automatic, autoPlay {
            return M.automatic(time: time - anchor)
        }
        let e = Self.ends(reduced: reduced)
        let inv = invite?.sample(at: time, end: e.invite, release: reduced ? nil : M.Invite.release) ?? 0
        let scanValue = scan?.sample(at: time, end: e.scan, release: reduced ? nil : M.Scan.release) ?? 0
        let cupValue = cup?.sample(at: time, end: M.Cup.end) ?? M.Cup.end
        var driven = M.Cup.end
        if inviteDrivesCup, let invite {
            let v = invite.running(at: time, release: M.Invite.release)
            driven = min(max(v - M.gustAfterPress, 0), M.Cup.end)
            if driven <= 0 { driven = M.Cup.end } // not yet: the cup rests
        }
        let tap = cupTap?.sample(at: time, end: M.Cup.end) ?? M.Cup.end
        return M.Clocks(invite: inv, scan: scanValue, cup: inviteDrivesCup ? driven : cupValue, cupTap: tap)
    }

    mutating func beginInvite(at now: TimeInterval, reduced: Bool) -> Bool {
        interrupt(at: now, reduced: reduced)
        let end = Self.ends(reduced: reduced).invite
        if let invite, invite.sample(at: now, end: end) > 0, invite.sample(at: now, end: end) < end { return false }
        invite = Clock(value: 0.0001, anchor: now, held: !reduced)
        inviteDrivesCup = !reduced
        cup = nil
        return true
    }

    /// True when the release lets the press play on (haptic scheduling follows the clock).
    mutating func releaseInvite(at now: TimeInterval, reduced: Bool) -> Bool {
        if reduced { return false }
        guard let invite, invite.held else { return false }
        self.invite = Clock(value: M.Invite.release, anchor: now)
        return true
    }

    mutating func beginScan(at now: TimeInterval, reduced: Bool) -> Bool {
        interrupt(at: now, reduced: reduced)
        let end = Self.ends(reduced: reduced).scan
        if let scan, scan.sample(at: now, end: end) > 0, scan.sample(at: now, end: end) < end { return false }
        scan = Clock(value: 0.0001, anchor: now, held: !reduced)
        return true
    }

    mutating func releaseScan(at now: TimeInterval, reduced: Bool) -> Bool {
        if reduced { return false }
        guard let scan, scan.held else { return false }
        self.scan = Clock(value: M.Scan.release, anchor: now)
        return true
    }

    mutating func tapCup(at now: TimeInterval, reduced: Bool) -> Bool {
        interrupt(at: now, reduced: reduced)
        if reduced { return false }
        let current = cupTap?.sample(at: now, end: M.Cup.end) ?? M.Cup.end
        if current < M.Cup.tapRearm { return false }
        cupTap = Clock(value: 0, anchor: now)
        return true
    }

    /// When every running clock has reached its end; nil while a finger holds a press or in autoPlay.
    func finish(at now: TimeInterval, reduced: Bool) -> TimeInterval? {
        if mode == .automatic, autoPlay { return nil }
        if invite?.held == true || scan?.held == true { return nil }
        let e = Self.ends(reduced: reduced)
        var end = now
        if let invite { end = max(end, invite.finish(at: now, end: e.invite)) }
        if let scan { end = max(end, scan.finish(at: now, end: e.scan)) }
        if let cup { end = max(end, cup.finish(at: now, end: M.Cup.end)) }
        if let cupTap { end = max(end, cupTap.finish(at: now, end: M.Cup.end)) }
        if inviteDrivesCup, let invite, !reduced {
            let v = invite.running(at: now, release: M.Invite.release)
            end = max(end, now + max(0, M.gustAfterPress + M.Cup.end - v))
        }
        return end
    }

    /// Seconds until the invite haptic: the launch (or the middle of the reduced-motion dip).
    func inviteHapticDelay(at now: TimeInterval, reduced: Bool) -> TimeInterval? {
        guard mode == .manual, let invite, !invite.held else { return nil }
        let e = Self.ends(reduced: reduced).invite
        let value = invite.sample(at: now, end: e)
        let target = reduced ? M.Reduced.inviteEnd * 0.5 : M.Invite.release + M.Fold.launch
        guard value > 0, value < e, value < target else { return nil }
        return target - value
    }

    /// Seconds until the "match" haptic: the end of the fill (or the middle of the reduced dip).
    func scanHapticDelay(at now: TimeInterval, reduced: Bool) -> TimeInterval? {
        guard mode == .manual, let scan, !scan.held else { return nil }
        let e = Self.ends(reduced: reduced).scan
        let value = scan.sample(at: now, end: e)
        let target = reduced ? M.Reduced.scanEnd * 0.5 : M.Scan.fillStart + M.Scan.fill
        guard value > 0, value < e, value < target else { return nil }
        return target - value
    }

    mutating func restart(at now: TimeInterval) {
        guard autoPlay else { return }
        mode = .automatic
        anchor = now
        invite = nil
        scan = nil
        cup = nil
        cupTap = nil
        inviteDrivesCup = false
    }

    private mutating func interrupt(at now: TimeInterval, reduced: Bool) {
        guard mode == .automatic else { return }
        let captured = clocks(at: now, reduced: reduced)
        mode = .manual
        let e = Self.ends(reduced: reduced)
        if captured.invite > 0, captured.invite < e.invite {
            invite = Clock(value: captured.invite, anchor: now)
            // A press that has not reached the gust yet still rocks the cup when the plane passes.
            inviteDrivesCup = !reduced && captured.invite < M.gustAfterPress
        }
        if captured.scan > 0, captured.scan < e.scan { scan = Clock(value: captured.scan, anchor: now) }
        if captured.cup > 0, captured.cup < M.Cup.end { cup = Clock(value: captured.cup, anchor: now) }
    }
}
