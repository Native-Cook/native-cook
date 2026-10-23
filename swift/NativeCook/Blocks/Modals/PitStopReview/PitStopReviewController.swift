import Foundation

/// Owns who drives the scene time and the review draft (port of controller.ts). Times are monotonic seconds.
///
/// - Demo: s = loop time (only while eligible: autoPlay, foreground, Home tab, feed at top, no owned flow).
/// - User: s follows a path of timeline segments, always forwards, so a tap plays the same frames as the loop
///   (open = TAP → HOLD, close = CLOSE → REST) and an interruption fast-forwards instead of jumping.
/// - Quiet (Reduce Motion / VoiceOver): the hero stays at rest; the composer fades in 120 ms.
struct PitStopReviewController {
    typealias Motion = PitStopReviewMotion
    enum Owner: Equatable { case demo, user }
    enum Cadence: Equatable { case stopped, animation }
    struct Seg: Equatable { var from: Double; var to: Double; var rate: Double }

    static let quietFade = 0.12
    static let restartDelay = 2.0

    private struct Path: Equatable { var segs: [Seg]; var startedAt: Double }
    private struct Fade: Equatable { var from = 0.0, to = 0.0, start = -1.0 }

    private(set) var autoPlay: Bool
    private(set) var owner: Owner
    private(set) var anchor: Double
    private(set) var restartAt: Double?
    private(set) var suspendedAt: Double?
    private(set) var rating = 0
    private(set) var note = ""
    private(set) var editorOwned = false
    private(set) var destinationOwned = false
    private(set) var sharing = false
    private(set) var keyboardPresented = false
    private(set) var foreground = true
    private(set) var homeSelected = true
    private(set) var feedAtTop = true
    private(set) var quiet = false
    private var path: Path?
    private var still = 0.0
    private var draftPresentation = false
    private var fade = Fade()

    init(autoPlay: Bool, anchor: Double) {
        self.autoPlay = autoPlay
        owner = autoPlay ? .demo : .user
        self.anchor = anchor
    }

    // MARK: Segment paths

    static func pathAt(_ segs: [Seg], _ elapsed: Double) -> Double {
        var e = max(0, elapsed)
        for g in segs {
            let d = (g.to - g.from) / g.rate
            if e < d { return g.from + e * g.rate }
            e -= d
        }
        return segs.last?.to ?? 0
    }
    static func pathDuration(_ segs: [Seg]) -> Double { segs.reduce(0) { $0 + ($1.to - $1.from) / $1.rate } }

    private static func atRest(_ s: Double) -> Bool { s < Motion.T.tap || s >= Motion.T.rest }
    /// Path from scene time s to the open composer.
    private static func toHold(_ s: Double, _ rate: Double) -> [Seg] {
        typealias T = Motion.T
        if s >= T.hold && s <= T.close { return [Seg(from: T.hold, to: T.hold, rate: 1)] }
        if s > T.close && s < T.rest { return [Seg(from: s, to: T.rest, rate: Motion.M.closeRush), Seg(from: T.tap, to: T.hold, rate: rate)] }
        if atRest(s) { return [Seg(from: T.tap, to: T.hold, rate: rate)] }
        return [Seg(from: s, to: T.hold, rate: rate)]
    }
    /// Path from scene time s back to the rest frame.
    private static func toRest(_ s: Double) -> [Seg] {
        typealias T = Motion.T
        if atRest(s) { return [Seg(from: 0, to: 0, rate: 1)] }
        if s < T.hold { return [Seg(from: s, to: T.hold, rate: Motion.M.closeRush), Seg(from: T.close, to: T.rest, rate: 1)] }
        if s <= T.close { return [Seg(from: T.close, to: T.rest, rate: 1)] }
        return [Seg(from: s, to: T.rest, rate: 1)]
    }

    // MARK: Reading

    var eligible: Bool {
        autoPlay && foreground && homeSelected && feedAtTop && !quiet && !editorOwned && !destinationOwned && !sharing && !keyboardPresented
    }
    var hasOwnedFlow: Bool { editorOwned || destinationOwned }
    private func logical(_ now: Double) -> Double { suspendedAt ?? now }
    private func shouldDemo(_ time: Double) -> Bool {
        eligible && (restartAt.map { time >= $0 } ?? (owner == .demo))
    }
    private func showsDraft(_ now: Double) -> Bool { draftPresentation && !shouldDemo(logical(now)) }
    func displayRating(_ now: Double) -> Int { showsDraft(now) ? rating : 0 }
    func displayNote(_ now: Double) -> String { showsDraft(now) ? note : "" }
    private func pathDone(_ time: Double) -> Bool {
        guard let path else { return true }
        return time >= path.startedAt + Self.pathDuration(path.segs)
    }

    /// Scene time now.
    func scene(_ now: Double) -> Double {
        let time = logical(now)
        if shouldDemo(time) { return Motion.wrap(time - (restartAt ?? anchor)) }
        if let path { return Self.pathAt(path.segs, time - path.startedAt) }
        return still
    }
    private func quietPanel(_ now: Double) -> Double {
        let u = min(1, max(0, (logical(now) - fade.start) / Self.quietFade))
        return fade.from + (fade.to - fade.from) * u
    }
    func panel(_ now: Double) -> Double { quiet ? quietPanel(now) : Motion.panel(scene(now)) }
    func panelVisible(_ now: Double) -> Bool { editorOwned || panel(now) > 0.001 }

    func cadence(_ now: Double) -> Cadence {
        let time = logical(now)
        if suspendedAt != nil { return .stopped }
        if quiet { return time < fade.start + Self.quietFade ? .animation : .stopped }
        if path != nil && !pathDone(time) { return .animation }
        if !shouldDemo(time) { return .stopped }
        let p = Motion.wrap(time - (restartAt ?? anchor))
        return (p >= Motion.T.press && p < Motion.T.hold) || (p >= Motion.T.close && p < Motion.T.rest) ? .animation : .stopped
    }

    func nextWakeDelay(_ now: Double) -> Double? {
        typealias T = Motion.T
        let time = logical(now)
        if suspendedAt != nil { return nil }
        if quiet && time < fade.start + Self.quietFade { return fade.start + Self.quietFade - time }
        if let path, !pathDone(time) { return path.startedAt + Self.pathDuration(path.segs) - time }
        if let restartAt, eligible, time < restartAt { return restartAt - time + T.press }
        if !shouldDemo(time) { return nil }
        let p = Motion.wrap(time - (restartAt ?? anchor))
        if p < T.press { return T.press - p }
        if p >= T.hold && p < T.close { return T.close - p }
        if p >= T.rest { return Motion.loop - p + T.press }
        return nil
    }

    // MARK: Commands

    mutating func setAutoPlay(_ enabled: Bool, at now: Double) {
        if autoPlay == enabled { return }
        normalize(now); autoPlay = enabled
        if !enabled {
            restartAt = nil
            if owner == .demo { owner = .user; settle(0) }
        } else if eligible { scheduleRestart(logical(now)) }
    }

    mutating func openEditor(at now: Double) {
        normalize(now)
        if editorOwned { return }
        let time = logical(now), s = scene(now), panelNow = panel(now)
        take(); editorOwned = true; draftPresentation = true
        if quiet { settle(0); fadeTo(panelNow, 1, time); return }
        begin(Self.toHold(s, Motion.M.manualRate), time)
    }

    mutating func closeEditor(at now: Double) {
        normalize(now)
        if !editorOwned && panel(now) <= 0.001 { return }
        let time = logical(now), s = scene(now), panelNow = panel(now)
        draftPresentation = showsDraft(now)
        editorOwned = false; keyboardPresented = false; owner = .user
        if quiet { settle(0); fadeTo(panelNow, 0, time); restartAt = nil; return }
        let segs = Self.toRest(s)
        begin(segs, time)
        restartAt = eligible ? time + Self.pathDuration(segs) + Self.restartDelay : nil
    }

    mutating func setRating(_ value: Int, at now: Double) {
        normalize(now); capture(now); rating = min(5, max(0, value)); rush(now)
    }
    mutating func setNote(_ value: String, at now: Double) {
        normalize(now); capture(now); note = String(value.prefix(280)); rush(now)
    }
    mutating func setDestinationOwned(_ owned: Bool, at now: Double) {
        normalize(now)
        let time = logical(now)
        destinationOwned = owned; owner = .user; settle(0)
        restartAt = !owned && eligible ? time + Self.restartDelay : nil
    }
    mutating func setHomeSelected(_ selected: Bool, at now: Double) {
        if homeSelected == selected { return }
        normalize(now); homeSelected = selected; updateEligibility(now)
    }
    mutating func setFeedAtTop(_ top: Bool, at now: Double) {
        if feedAtTop == top { return }
        normalize(now); feedAtTop = top; updateEligibility(now)
    }
    mutating func setQuiet(_ enabled: Bool, at now: Double) {
        if quiet == enabled { return }
        normalize(now)
        let time = logical(now), panelNow = panel(now)
        draftPresentation = showsDraft(now)
        quiet = enabled; owner = .user
        settle(editorOwned ? Motion.T.hold : 0)
        fadeTo(enabled ? panelNow : (editorOwned ? 1 : 0), editorOwned ? 1 : 0, time)
        restartAt = !enabled && eligible ? time + Self.restartDelay : nil
    }
    @discardableResult
    mutating func beginShare(at now: Double) -> Bool {
        normalize(now); capture(now)
        if !editorOwned || rating == 0 { return false }
        sharing = true; updateSuspension(now)
        return true
    }
    mutating func setForeground(_ active: Bool, at now: Double) {
        if foreground == active { return }
        if !active { preserve(now) }
        foreground = active; updateSuspension(now)
    }
    mutating func setSharing(_ active: Bool, at now: Double) {
        if sharing == active { return }
        if active { preserve(now) }
        sharing = active; updateSuspension(now)
    }
    mutating func setKeyboardPresented(_ active: Bool, at now: Double) {
        normalize(now)
        if active { capture(now) }
        if active && editorOwned { settle(quiet ? 0 : Motion.T.hold) }
        keyboardPresented = active; updateSuspension(now)
    }

    // MARK: Internals

    private mutating func begin(_ segs: [Seg], _ time: Double) { path = Path(segs: segs, startedAt: time) }
    private mutating func settle(_ s: Double) { path = nil; still = s }
    private mutating func take() { owner = .user; destinationOwned = false; restartAt = nil }
    private mutating func fadeTo(_ from: Double, _ to: Double, _ time: Double) { fade = Fade(from: from, to: to, start: time) }
    private mutating func normalize(_ now: Double) {
        let time = logical(now)
        if let path, pathDone(time) { settle(Self.pathAt(path.segs, .infinity)) }
        if let restartAt, eligible, time >= restartAt {
            owner = .demo; anchor = restartAt; self.restartAt = nil; settle(0)
        }
    }
    /// A touch on the composer while the demo shows it takes it over (finishing the opening quickly).
    private mutating func capture(_ now: Double) {
        if editorOwned { return }
        if panel(now) <= 0.001 { return }
        let s = scene(now)
        take(); editorOwned = true; draftPresentation = true
        if quiet { settle(0); fadeTo(1, 1, logical(now)); return }
        if s >= Motion.T.hold && s <= Motion.T.close { settle(Motion.T.hold) }
        else if s > Motion.T.close { settle(Motion.T.hold) }
        else { settle(s) }
    }
    /// Input while the opening still plays finishes it at once (fast-forward, no jump).
    private mutating func rush(_ now: Double) {
        if !editorOwned || quiet { return }
        let time = logical(now), s = scene(now)
        restartAt = nil; owner = .user
        if s < Motion.T.hold { begin([Seg(from: max(s, Motion.T.tap), to: Motion.T.hold, rate: Motion.M.rush)], time) }
    }
    private mutating func updateEligibility(_ now: Double) {
        let time = logical(now)
        if eligible {
            if owner != .demo && !hasOwnedFlow { scheduleRestart(time) }
        } else {
            restartAt = nil
            if owner == .demo { owner = .user; settle(0) }
        }
    }
    private mutating func scheduleRestart(_ time: Double) { owner = .user; settle(0); restartAt = time + Self.restartDelay }
    private mutating func preserve(_ now: Double) {
        normalize(now)
        if suspendedAt != nil { return }
        draftPresentation = showsDraft(now)
    }
    private mutating func updateSuspension(_ now: Double) {
        let should = !foreground || sharing || keyboardPresented
        if should { if suspendedAt == nil { suspendedAt = now }; return }
        guard let was = suspendedAt else { return }
        let pause = max(0, now - was)
        suspendedAt = nil
        anchor += pause
        if let restartAt { self.restartAt = restartAt + pause }
        if path != nil { path?.startedAt += pause }
        fade.start += pause
        if eligible && owner == .user && !hasOwnedFlow && restartAt == nil { scheduleRestart(now) }
    }
}
