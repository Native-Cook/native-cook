import Observation
import SwiftUI
import UIKit

/// Owns the loop clock, the Continue/Replay state and the timers of Daylight Icon Reveal.
///
/// It mirrors the React Native component's `clock` shared value and its `useEffect`: the clock is
/// worked out from a start date rather than added up frame by frame (so the loop keeps a constant
/// period), a finger on the button freezes it, and 2 s after the finger leaves the screen settles
/// and the loop starts again. Nothing here runs per frame; the view reads `time(at:)`.
@MainActor
@Observable
final class DaylightIconRevealController {

    // MARK: Clock

    /// What the clock is doing. `.loop` and `.ramp` move with the date; `.still` does not.
    private enum Segment {
        case still(Double)
        case loop(origin: Date)
        case ramp(from: Double, to: Double, start: Date, duration: Double, curve: Curve)

        var isMoving: Bool {
            if case .still = self { return false }
            return true
        }
    }

    /// The timing function of a ramp.
    private enum Curve {
        /// `Easing.linear`.
        case linear
        /// `EASE` in the React Native file.
        case ease

        func value(_ progress: Double) -> Double {
            switch self {
            case .linear: return progress
            case .ease: return DaylightIconRevealScript.ease(progress)
            }
        }
    }

    private var segment: Segment

    /// The loop time `t`, in seconds, at `date`.
    func time(at date: Date) -> Double {
        switch segment {
        case .still(let value):
            return value
        case .loop(let origin):
            let elapsed = max(0, date.timeIntervalSince(origin))
            return elapsed.truncatingRemainder(dividingBy: DaylightIconReveal.loop)
        case .ramp(let from, let to, let start, let duration, let curve):
            let progress = min(1, max(0, date.timeIntervalSince(start) / duration))
            return from + (to - from) * curve.value(progress)
        }
    }

    /// Whether the timeline has to keep ticking: the clock moves or a text is swapping.
    var isMoving: Bool {
        segment.isMoving || swapping
    }

    // MARK: State the view reads

    /// Continue was pressed: the copy reads "Make room for your rhythm." and the button "Replay welcome".
    private(set) var began = false
    /// A finger is down on the button (and still inside its press region).
    private(set) var pressed = false
    /// The copy that was on screen before the last change, while it fades out.
    private(set) var previousBegan = false
    /// When the copy last changed; nil until it does (the first render does not animate).
    private(set) var swapStart: Date?
    /// True while the old and the new copy cross-fade.
    private(set) var swapping = false

    // MARK: Inputs

    private var autoPlay = false
    private var previewTime: Double?
    private var onContinue: (() -> Void)?
    var reduced = false
    var foreground = true

    private var holding = false
    private var landed = false
    private var replayEpoch = 0

    private var timer: Task<Void, Never>?
    private var landingTimer: Task<Void, Never>?
    private var rampEnd: Task<Void, Never>?
    private var swapEnd: Task<Void, Never>?

    private let selectionHaptic = UISelectionFeedbackGenerator()
    private let landingHaptic = UIImpactFeedbackGenerator(style: .soft)

    /// `clock` starts at the settled icon (4 s) unless it plays by itself, and at the previewed
    /// phase when a preview time is given.
    init(autoPlay: Bool, previewTime: Double?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        if let previewTime {
            segment = .still(DaylightIconRevealScript.previewPhase(previewTime))
        } else {
            segment = .still(autoPlay ? 0 : 4)
        }
    }

    func configure(autoPlay: Bool, previewTime: Double?, onContinue: (() -> Void)?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onContinue = onContinue
    }

    // MARK: Clock control

    private func now() -> Date { Date() }

    private func setStill(_ value: Double) {
        segment = .still(value)
    }

    /// `cancelAnimation(clock)`: stop where the clock is.
    private func freeze() {
        setStill(time(at: now()))
    }

    private func startLoop() {
        segment = .loop(origin: now())
    }

    private func startRamp(to target: Double, from start: Double, duration: Double, curve: Curve) {
        segment = .ramp(from: start, to: target, start: now(), duration: duration, curve: curve)
        rampEnd?.cancel()
        rampEnd = schedule(after: duration) { [weak self] in
            guard let self, case .ramp = self.segment else { return }
            self.setStill(target)
        }
    }

    private func cancelTimers() {
        timer?.cancel()
        timer = nil
        landingTimer?.cancel()
        landingTimer = nil
        rampEnd?.cancel()
        rampEnd = nil
    }

    private func schedule(after seconds: Double, _ work: @escaping @MainActor () -> Void) -> Task<Void, Never> {
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            work()
        }
    }

    /// `play`: restart the loop from the resting state, unless something stops it.
    private func play() {
        guard autoPlay, !reduced, foreground, !holding, !began, previewTime == nil else { return }
        startLoop()
    }

    /// The component's effect: whenever an input changes, stop what runs and start what is due.
    func sync() {
        cancelTimers()
        freeze()
        if let previewTime {
            setStill(DaylightIconRevealScript.previewPhase(previewTime))
        } else if !autoPlay, replayEpoch > 0, !began, !reduced, foreground {
            // Replay without autoPlay: the introduction runs once, over 4 s.
            startRamp(to: 4, from: 0, duration: 4, curve: .linear)
            if !landed {
                // The soft haptic when the icon lands (the clock crosses 2.45 s).
                landingTimer = schedule(after: 2.45) { [weak self] in
                    self?.landNow()
                }
            }
        } else {
            play()
        }
    }

    func stop() {
        cancelTimers()
        swapEnd?.cancel()
        swapEnd = nil
        swapping = false
        freeze()
    }

    private func landNow() {
        guard !landed else { return }
        landed = true
        landingHaptic.impactOccurred()
    }

    // MARK: Touch on the button

    /// `begin`: a finger lands on the button; the clock stops where it is.
    func pressIn() {
        guard previewTime == nil else { return }
        holding = true
        pressed = true
        selectionHaptic.prepare()
        cancelTimers()
        freeze()
    }

    /// `end`: the finger leaves. Two seconds later the screen settles and the loop restarts.
    func pressOut() {
        guard previewTime == nil else { return }
        holding = false
        pressed = false
        timer?.cancel()
        timer = schedule(after: 2) { [weak self] in
            guard let self, !self.began, self.autoPlay, !self.reduced, !self.holding else { return }
            // Finish settling before starting a fresh explanatory sequence.
            self.startRamp(to: 4, from: self.time(at: self.now()), duration: 0.24, curve: .ease)
            self.timer = self.schedule(after: 0.65) { [weak self] in
                self?.play()
            }
        }
    }

    /// `continueAction`: Continue begins the story, Replay welcome starts it over.
    func activate() {
        guard previewTime == nil else { return }
        cancelTimers()
        freeze()
        selectionHaptic.selectionChanged()
        let wasBegan = began
        if wasBegan {
            landed = false
            replayEpoch += 1
            began = false
            if reduced { setStill(4) }
        } else {
            began = true
            setStill(4)
        }
        recordSwap(from: wasBegan)
        if !wasBegan { onContinue?() }
        sync()
    }

    // MARK: Text swap

    /// Remember that the copy changed now, so the texts can cross-fade for a moment.
    private func recordSwap(from previous: Bool) {
        previousBegan = previous
        swapStart = now()
        swapping = true
        swapEnd?.cancel()
        swapEnd = schedule(after: DaylightIconRevealScript.swapDuration + 0.1) { [weak self] in
            self?.swapping = false
        }
    }
}
