import SwiftUI
import UIKit

private typealias M = FighterProfileTourMotion

/// A spring on one number, with Reanimated's duration-based parametrisation:
/// `duration` is the perceptual duration, so omega = 2π / duration and zeta is the damping ratio.
/// Rest thresholds are Reanimated's defaults (speed 2, displacement 0.01).
struct FighterProfileTourSpring {
    var value: Double
    var velocity: Double
    var target: Double
    private let omega: Double
    private let zeta: Double
    private let clampOvershoot: Bool
    private let initial: Double

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double, clampOvershoot: Bool) {
        self.value = value
        self.initial = value
        self.velocity = velocity
        self.target = target
        self.omega = 2 * Double.pi / duration
        self.zeta = dampingRatio
        self.clampOvershoot = clampOvershoot
    }

    /// Advances by `dt` seconds; returns true when the spring has finished (value is then the target).
    mutating func step(_ dt: Double) -> Bool {
        var remaining = dt
        while remaining > 0 {
            let h = min(remaining, 1.0 / 240.0)
            remaining -= h
            let acceleration = -omega * omega * (value - target) - 2 * zeta * omega * velocity
            velocity += acceleration * h
            value += velocity * h
        }
        let overshooting = clampOvershoot && (initial < target ? value > target : value < target)
        if overshooting || (abs(velocity) < 2 && abs(target - value) < 0.01) {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// `withTiming`: a number moving to a target over `duration` seconds along a curve.
struct FighterProfileTourTiming {
    private(set) var value: Double
    private var from: Double
    private var to: Double
    private var elapsed = 0.0
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }
    private(set) var active = false

    init(_ value: Double) {
        self.value = value
        from = value
        to = value
    }

    mutating func set(_ v: Double) {
        value = v
        from = v
        to = v
        active = false
    }

    mutating func start(to target: Double, duration: Double, curve: @escaping (Double) -> Double) {
        from = value
        to = target
        elapsed = 0
        self.duration = duration
        self.curve = curve
        active = true
    }

    mutating func cancel() { active = false }

    mutating func step(_ dt: Double) {
        guard active else { return }
        elapsed += dt
        let p = min(1, elapsed / duration)
        value = from + (to - from) * curve(p)
        if p >= 1 {
            value = to
            active = false
        }
    }
}

/// All state of the tour, the counterpart of the shared values in `FighterProfileTour.tsx`.
/// One frame callback (`advance`) steps the clock and every animation; the view reads the result.
final class FighterProfileTourModel {
    static let rowPitch = 107.0
    static let statCard = 118.0
    static let statGap = 10.0
    static let statScroll = 5.0 * (statCard + statGap) - statGap - 353

    // Configuration, set from the view on every render.
    var autoPlay = true
    var controlled = false
    var reduced = false
    var systemReduced = false
    var stageWidth = 393.0
    var onComplete: (() -> Void)?

    // The clock.
    private(set) var progress = 0.0
    private var progressSpring: FighterProfileTourSpring?
    private(set) var playing = false
    private var clockActive = false
    private var resumeDelay = 0.0
    private var routeFocused = false
    private var interrupted = false
    private var appActive = true
    private var lastDate: Date?
    private var configured = false

    // Selection.
    private(set) var selected = 0
    private(set) var completed = false
    private(set) var ringRow = FighterProfileTourTiming(0)
    private var ringSpring: FighterProfileTourSpring?
    private(set) var weights = [1.0, 0, 0, 0, 0].map { FighterProfileTourTiming($0) }
    /// Tier-label colour transition (CSS `transition: color 420ms`): rgb of the accent it leaves and the progress.
    private(set) var accentFrom: UInt32 = FighterProfileTourData.fighters[0].accent
    private(set) var accentTransition = FighterProfileTourTiming(1)

    // Matchup cards.
    private(set) var firstExpanded = FighterProfileTourTiming(1)
    private(set) var secondExpanded = FighterProfileTourTiming(1)
    private(set) var firstTarget = 1.0
    private(set) var secondTarget = 1.0

    // Stat strip.
    private(set) var stripManual = -1.0
    private var stripSpring: FighterProfileTourSpring?
    private var stripStart = 0.0

    // Pan.
    private var dragStart = 0.0
    private var dragOrigin = 0.0
    private(set) var gestureActive = false
    /// True from the moment a touch has travelled far enough to be a drag until just after it ends;
    /// taps that arrive meanwhile belong to the drag.
    var suppressTap = false

    // Shimmer.
    private(set) var sweep = FighterProfileTourTiming(-1)

    private let selection = UISelectionFeedbackGenerator()

    var time: Double { M.sceneTime(progress, reduced: reduced) }
    var step: Int { M.stepAt(time) }
    var shimmerEnabled: Double { reduced ? 0 : 1 }

    var stripOffset: Double {
        if stripManual >= 0 { return stripManual }
        return reduced ? 0 : Self.statScroll * M.ramp(time, 0.405, 0.445, moving: true)
    }

    /// Fighter colour page weights; Villager's page is the base layer.
    func weight(_ index: Int) -> Double { weights[index].value }

    var tierAccent: [Double] {
        let to = FighterProfileTourData.fighters[selected].accent
        let p = accentTransition.value
        func rgb(_ hex: UInt32) -> [Double] {
            [Double((hex >> 16) & 0xff), Double((hex >> 8) & 0xff), Double(hex & 0xff)]
        }
        let a = rgb(accentFrom), b = rgb(to)
        return (0..<3).map { M.mix(a[$0], b[$0], p) }
    }

    // MARK: Frame

    func advance(_ date: Date) {
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        guard raw > 0 else { return }
        let dt = min(raw, 0.048)
        stepAnimations(dt)
        guard playing, clockActive else { return }
        if resumeDelay > 0 {
            resumeDelay = max(0, resumeDelay - dt)
            return
        }
        progress = (progress + dt / M.loop).truncatingRemainder(dividingBy: 1)
    }

    /// Forget the last frame time, so a pause does not count as one long frame.
    func resetClock() { lastDate = nil }

    private func stepAnimations(_ dt: Double) {
        if var spring = progressSpring {
            let done = spring.step(dt)
            progress = spring.value
            progressSpring = done ? nil : spring
        }
        if var spring = ringSpring {
            let done = spring.step(dt)
            ringRow.set(spring.value)
            ringSpring = done ? nil : spring
        }
        if var spring = stripSpring {
            let done = spring.step(dt)
            stripManual = spring.value
            stripSpring = done ? nil : spring
        }
        for index in weights.indices { weights[index].step(dt) }
        accentTransition.step(dt)
        firstExpanded.step(dt)
        secondExpanded.step(dt)
        sweep.step(dt)
    }

    // MARK: Progress

    private func setProgress(_ value: Double) {
        progressSpring = nil
        progress = value
    }

    private func animateProgress(to target: Double, velocity: Double? = nil, dampingRatio: Double = 1) {
        if reduced { setProgress(target); return }
        let carried = (progressSpring?.velocity ?? 0) + (velocity ?? 0)
        progressSpring = FighterProfileTourSpring(from: progress, to: target, velocity: carried, duration: 0.4, dampingRatio: dampingRatio, clampOvershoot: true)
    }

    private func settleToHold(velocity: Double = 0, animated: Bool = true) {
        let target = M.nearestHold(progress, velocity: velocity)
        if !animated { setProgress(target); return }
        animateProgress(to: target, velocity: velocity, dampingRatio: abs(velocity) > 0 ? 0.8 : 1)
    }

    // MARK: Lifecycle

    /// Applies `autoPlay` and `previewTime` (a deterministic preview position in seconds), as the
    /// effect on `[autoPlay, controlled, previewTime]` does in the React Native version.
    func configure(autoPlay: Bool, previewTime: Double?) {
        let controlled = previewTime != nil
        if !configured || autoPlay != self.autoPlay || controlled != self.controlled {
            configured = true
            self.autoPlay = autoPlay
            self.controlled = controlled
            progressSpring = nil
            playing = autoPlay && !controlled
        }
        if let previewTime { setProgress(M.clamp(previewTime / M.loop)) }
    }

    private func pauseForInterruption() {
        interrupted = true
        gestureActive = false
        clockActive = false
        progressSpring = nil
        sweep.cancel()
        resetClock()
    }

    private func restoreAfterInterruption() {
        if interrupted && !controlled {
            settleToHold(animated: false)
            playing = autoPlay
            resumeDelay = autoPlay ? 2 : 0
        }
        interrupted = false
        clockActive = routeFocused && autoPlay && !controlled && appActive
    }

    func setFocused(_ focused: Bool) {
        routeFocused = focused
        if focused { restoreAfterInterruption() } else { pauseForInterruption() }
    }

    func setAppActive(_ active: Bool) {
        appActive = active
        if !active { pauseForInterruption() } else if routeFocused { restoreAfterInterruption() }
    }

    // MARK: Interaction

    private func haptic() { selection.selectionChanged() }

    private func shimmer() {
        guard !reduced else { return }
        sweep.set(-0.15)
        sweep.start(to: 1.2, duration: 0.22, curve: { $0 })
    }

    enum CommitKind { case proceed, skip, select }

    func commit(_ kind: CommitKind) {
        haptic()
        shimmer()
        playing = false
        resumeDelay = 0
        clockActive = false
        stripManual = -1
        stripSpring = nil
        let raw = progress
        let current = M.sceneTime(raw, reduced: false)
        if raw >= 0.84 { setProgress(current) } // Same visible pose, forward manual coordinate.
        let currentStep = M.stepAt(M.sceneTime(current, reduced: reduced))
        if currentStep == 3 && kind == .proceed {
            animateProgress(to: 0.72)
            completed = true
            onComplete?()
            return
        }
        let target = kind == .skip || currentStep != 1 ? 0.72 : 0.40
        animateProgress(to: target)
    }

    /// Picking another card moves the ring and recolours the page; picking the chosen one starts.
    func pick(_ index: Int) {
        if index == selected { commit(.select); return }
        haptic()
        playing = false
        clockActive = false
        resumeDelay = 0
        // Restart the transition from the colour currently shown.
        let shown = tierAccent
        accentFrom = (UInt32(shown[0].rounded()) << 16) | (UInt32(shown[1].rounded()) << 8) | UInt32(shown[2].rounded())
        selected = index
        completed = false
        if reduced {
            ringSpring = nil
            ringRow.set(Double(index))
            accentTransition.set(1)
            for fighter in weights.indices { weights[fighter].set(fighter == index ? 1 : 0) }
        } else {
            ringSpring = FighterProfileTourSpring(from: ringRow.value, to: Double(index), velocity: ringSpring?.velocity ?? 0, duration: 0.38, dampingRatio: 0.82, clampOvershoot: false)
            accentTransition.set(0)
            accentTransition.start(to: 1, duration: 0.42, curve: M.easeOut)
            for fighter in weights.indices { weights[fighter].start(to: fighter == index ? 1 : 0, duration: 0.42, curve: M.easeOut) }
        }
    }

    func toggle(card: Int) {
        haptic()
        playing = false
        clockActive = false
        if progress >= 0.84 { setProgress(M.sceneTime(progress, reduced: false)) }
        animateProgress(to: 0.72)
        let target = card == 0 ? firstTarget : secondTarget
        let next = target > 0.5 ? 0.0 : 1.0
        if card == 0 { firstTarget = next } else { secondTarget = next }
        func move(_ timing: inout FighterProfileTourTiming) {
            if reduced { timing.set(next) } else { timing.start(to: next, duration: 0.18, curve: M.easeOut) }
        }
        if card == 0 { move(&firstExpanded) } else { move(&secondExpanded) }
    }

    // Main pan: scrubs the scene time with the finger.
    func panStart(translationX: Double) {
        dragOrigin = translationX
        gestureActive = true
        playing = false
        resumeDelay = 0
        progressSpring = nil
        let start = M.sceneTime(progress, reduced: false)
        dragStart = start
        setProgress(start)
    }

    func panUpdate(translationX: Double) {
        setProgress(M.clamp(dragStart - (translationX - dragOrigin) / stageWidth * 0.72, 0, 0.72))
    }

    func panEnd(velocityX: Double) {
        gestureActive = false
        settleToHold(velocity: M.clamp(-velocityX / stageWidth * 0.72, -3, 3))
        clockActive = false
        completed = false
        haptic()
    }

    // Stat strip pan.
    func statsStart() {
        stripStart = stripOffset
        stripManual = stripOffset
        stripSpring = nil
        playing = false
        resumeDelay = 0
    }

    func statsUpdate(translationX: Double) {
        let raw = stripStart - translationX
        let edge = M.clamp(raw, 0, Self.statScroll)
        stripManual = edge + (raw - edge) / (1 + abs(raw - edge) / 60)
    }

    func statsEnd(velocityX: Double) {
        let projected = stripManual - velocityX * 0.18
        let pitch = Self.statCard + Self.statGap
        let snapped = M.clamp((projected / pitch).rounded() * pitch, 0, Self.statScroll)
        if systemReduced {
            stripManual = snapped
            stripSpring = nil
        } else {
            stripSpring = FighterProfileTourSpring(from: stripManual, to: snapped, velocity: -velocityX, duration: 0.42, dampingRatio: 0.9, clampOvershoot: false)
        }
    }
}
