import Observation
import SwiftUI
import UIKit

/// Owns the loop clock, the touch presses of the keys, the headline's operation and the
/// Continue / Start again state of Tactile Calculator Welcome.
///
/// It mirrors the React Native component: the `clock` shared value and its `play` effect, the
/// per-key `press` shared values, and `begin` / `end`. The clock is worked out from a start
/// date rather than added up frame by frame (so the loop keeps a constant period), a finger on
/// a key or the button stops it at the resting state, and 2 s after the finger leaves the
/// loop starts again. Nothing here runs per frame; the view reads `time(at:)` and
/// `pressure(of:at:)` in its timeline.
@MainActor
@Observable
final class TactileCalculatorWelcomeController {

    // MARK: Clock

    /// What the clock is doing. `.loop` moves with the date; `.still` does not.
    private enum ClockState {
        case still(Double)
        case loop(origin: Date)
    }

    @ObservationIgnored private var clock: ClockState

    /// `previewPhase`: a preview time folded into one loop.
    static func phase(_ time: Double) -> Double {
        guard time.isFinite else { return 0 }
        let loop = TactileCalculatorWelcome.loop
        let value = time.truncatingRemainder(dividingBy: loop)
        return value < 0 ? value + loop : value
    }

    /// The loop time `t`, in seconds, at `date`.
    func time(at date: Date) -> Double {
        switch clock {
        case .still(let value):
            return value
        case .loop(let origin):
            let elapsed = max(0, date.timeIntervalSince(origin))
            return elapsed.truncatingRemainder(dividingBy: TactileCalculatorWelcome.loop)
        }
    }

    // MARK: Presses

    /// `PRESSES`: the demo presses of the loop, `down` and `release` in seconds
    /// (`frame / 60` in the React Native file).
    private struct Beat {
        let symbol: TactileCalculatorWelcomeSymbol
        let down: Double
        let release: Double
    }

    private static let beats: [Beat] = [
        Beat(symbol: .divide, down: 10.0 / 60, release: 40.0 / 60),
        Beat(symbol: .plus, down: 70.0 / 60, release: 100.0 / 60),
        Beat(symbol: .multiply, down: 132.0 / 60, release: 160.0 / 60),
        Beat(symbol: .equals, down: 191.0 / 60, release: 220.0 / 60),
        Beat(symbol: .divide, down: 251.0 / 60, release: 280.0 / 60),
        Beat(symbol: .plus, down: 311.0 / 60, release: 340.0 / 60),
    ]

    /// How far the loop's own press has pushed a cap at loop time `t`: a 5 frame smoothstep
    /// down, a hold until the release, then a damped ring-out.
    private static func demo(_ symbol: TactileCalculatorWelcomeSymbol, t: Double) -> Double {
        var value = 0.0
        for beat in beats where beat.symbol == symbol {
            if t < beat.down || t > beat.release + 0.3 {
                continue
            }
            if t < beat.down + 5.0 / 60 {
                let p = (t - beat.down) / (5.0 / 60)
                value = p * p * (3 - 2 * p)
            } else if t < beat.release {
                value = 1
            } else {
                let elapsed = t - beat.release
                value = max(0, exp(-13 * elapsed) * cos(7 * elapsed))
            }
        }
        return value
    }

    /// The `press` shared value of one key: `withTiming(1, { duration: 110, easing: EASE })`
    /// when the finger lands, `withSpring(0, { duration: 420, dampingRatio: 0.62 })` when it
    /// leaves. Both start from wherever the value is.
    private enum PressTrack {
        case rest
        case down(from: Double, start: Date)
        case up(from: Double, start: Date)

        /// `{ duration: 110 }`.
        static let downDuration = 0.11
        /// Damping ratio of the release spring.
        static let zeta = 0.62
        /// Natural frequency of the release spring in rad/s. Reanimated 4.5 treats `duration`
        /// as a perceptual duration and solves for the stiffness that has the spring settled
        /// (energy below 6e-9) after 1.5 x 420 ms (`calculateNewStiffnessToMatchDuration`);
        /// for a damping ratio of 0.62 that gives w0 = sqrt(stiffness / mass) = 31.2 rad/s
        /// whatever the mass, about 2x the (2 pi / 0.42) = 15 rad/s of a plain perceptual
        /// duration, so the cap is back at rest after about 90 ms.
        static let omega0 = 31.2005
        /// The release spring has settled after about 1.5 x 420 ms.
        static let upDuration = 0.63

        func value(at date: Date) -> Double {
            switch self {
            case .rest:
                return 0
            case .down(let from, let start):
                let elapsed = max(0, date.timeIntervalSince(start))
                let p = TactileCalculatorWelcomeEase.clamp(elapsed / Self.downDuration)
                return from + (1 - from) * TactileCalculatorWelcomeEase.settle.value(at: p)
            case .up(let from, let start):
                let elapsed = max(0, date.timeIntervalSince(start))
                if elapsed >= Self.upDuration {
                    return 0
                }
                // Underdamped spring towards 0 from `from`, starting at rest.
                let zeta = Self.zeta
                let omega0 = Self.omega0
                let omega1 = omega0 * (1 - zeta * zeta).squareRoot()
                let envelope = exp(-zeta * omega0 * elapsed)
                let cosine = cos(omega1 * elapsed)
                let sine = sin(omega1 * elapsed)
                return envelope * from * (cosine + (zeta * omega0 / omega1) * sine)
            }
        }

        /// Whether the value may still change after `date`.
        func isMoving(at date: Date) -> Bool {
            switch self {
            case .rest:
                return false
            case .down(_, let start):
                return date.timeIntervalSince(start) < Self.downDuration + 0.05
            case .up(_, let start):
                return date.timeIntervalSince(start) < Self.upDuration + 0.05
            }
        }
    }

    @ObservationIgnored private var presses: [TactileCalculatorWelcomeSymbol: PressTrack] = [:]

    /// The `pressure` derived value of `KeyButton`: the larger of the finger's press and the
    /// loop's demo press, kept between 0 and 1; always 0 under Reduce Motion.
    func pressure(of symbol: TactileCalculatorWelcomeSymbol, at date: Date) -> Double {
        if reduced {
            return 0
        }
        let demo = Self.demo(symbol, t: time(at: date))
        let touch = presses[symbol]?.value(at: date) ?? 0
        return min(1, max(0, touch, demo))
    }

    // MARK: State the view reads

    /// `operation`: the last key pressed (nil until one is).
    private(set) var symbol: TactileCalculatorWelcomeSymbol?
    /// `started`: Continue was pressed; the copy reads "A little clarity, every day." and the
    /// button "Start again".
    private(set) var started = false
    /// Whether the keys' timeline has to keep ticking: the loop runs or a press is moving.
    private(set) var moving = false

    // MARK: Inputs

    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var previewTime: Double?
    @ObservationIgnored private var onContinue: (() -> Void)?
    @ObservationIgnored var reduced = false
    @ObservationIgnored var foreground = true
    /// `held`: a finger is down on a key or the button.
    @ObservationIgnored private var held = false

    @ObservationIgnored private var restartTimer: Task<Void, Never>?
    @ObservationIgnored private var settleTimer: Task<Void, Never>?

    private let rigidHaptic = UIImpactFeedbackGenerator(style: .rigid)
    private let selectionHaptic = UISelectionFeedbackGenerator()

    /// `clock` starts at rest (0), and at the previewed phase when a preview time is given.
    init(autoPlay: Bool, previewTime: Double?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        if let previewTime {
            clock = .still(Self.phase(previewTime))
        } else {
            clock = .still(0)
        }
    }

    func configure(autoPlay: Bool, previewTime: Double?, onContinue: (() -> Void)?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onContinue = onContinue
    }

    /// Whether touch is live; a previewed frame is frozen.
    var isInteractive: Bool {
        previewTime == nil
    }

    // MARK: Clock control

    private func now() -> Date {
        Date()
    }

    /// `cancelAnimation(clock)`: stop where the clock is.
    private func freeze() {
        clock = .still(time(at: now()))
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
        if let previewTime {
            clock = .still(Self.phase(previewTime))
            refreshMoving()
            return
        }
        guard autoPlay, !reduced, foreground, !held, !started else { return }
        clock = .loop(origin: now())
        refreshMoving()
    }

    /// The component's effect: whenever an input changes, stop what runs (and the restart
    /// timer), then start what is due.
    func sync() {
        restartTimer?.cancel()
        restartTimer = nil
        freeze()
        play()
        refreshMoving()
    }

    /// The component unmounts.
    func stop() {
        restartTimer?.cancel()
        restartTimer = nil
        settleTimer?.cancel()
        settleTimer = nil
        freeze()
        refreshMoving()
    }

    /// `begin`: a finger lands; the clock stops at the resting state.
    private func begin() {
        held = true
        restartTimer?.cancel()
        restartTimer = nil
        clock = .still(0)
        refreshMoving()
    }

    /// `end`: the finger leaves. Two seconds later the loop starts again.
    private func end() {
        held = false
        restartTimer?.cancel()
        restartTimer = nil
        if let previewTime {
            clock = .still(Self.phase(previewTime))
            refreshMoving()
            return
        }
        restartTimer = schedule(after: 2) { [weak self] in
            self?.play()
        }
    }

    // MARK: Moving

    /// Turns the keys' timeline on while the loop or a press moves, and schedules the moment
    /// it can be switched off again.
    private func refreshMoving() {
        let date = now()
        var active = false
        if case .loop = clock {
            active = true
        }
        for track in presses.values where track.isMoving(at: date) {
            active = true
        }
        if moving != active {
            moving = active
        }
        settleTimer?.cancel()
        settleTimer = nil
        if presses.values.contains(where: { $0.isMoving(at: date) }) {
            settleTimer = schedule(after: 0.8) { [weak self] in
                self?.refreshMoving()
            }
        }
    }

    // MARK: Keys

    /// `onPressIn` of a key: the clock stops, the cap goes down in 110 ms, a rigid tap.
    func keyPressIn(_ key: TactileCalculatorWelcomeSymbol) {
        guard isInteractive else { return }
        begin()
        if reduced {
            presses[key] = .rest
        } else {
            let date = now()
            presses[key] = .down(from: presses[key]?.value(at: date) ?? 0, start: date)
        }
        rigidHaptic.impactOccurred()
        refreshMoving()
    }

    /// `onPressOut` of a key: the cap springs back, and the loop restarts in 2 s.
    func keyPressOut(_ key: TactileCalculatorWelcomeSymbol) {
        guard isInteractive else { return }
        let date = now()
        presses[key] = .up(from: presses[key]?.value(at: date) ?? 0, start: date)
        end()
        refreshMoving()
    }

    /// `onPress` of a key: `setOperation`.
    func keySelect(_ key: TactileCalculatorWelcomeSymbol) {
        guard isInteractive else { return }
        symbol = key
    }

    /// VoiceOver's activation of a key: the same sequence a finger makes, 130 ms long.
    func activateKey(_ key: TactileCalculatorWelcomeSymbol) {
        guard isInteractive else { return }
        keyPressIn(key)
        _ = schedule(after: 0.13) { [weak self] in
            self?.keyPressOut(key)
            self?.keySelect(key)
        }
    }

    // MARK: Button

    /// `onPressIn` of Continue / Start again: the clock stops at rest.
    func buttonPressIn() {
        guard isInteractive else { return }
        begin()
    }

    /// `onPressOut` of the button: the loop restarts in 2 s unless the press itself restarts it.
    func buttonPressOut() {
        guard isInteractive else { return }
        end()
    }

    /// `onPress` of the button: Continue starts the story, Start again takes it back. The
    /// effect then runs again, so Start again restarts the loop at once when the finger is up.
    func buttonPress() {
        guard isInteractive else { return }
        let wasStarted = started
        started = !wasStarted
        if !wasStarted {
            onContinue?()
        }
        selectionHaptic.selectionChanged()
        sync()
    }
}
