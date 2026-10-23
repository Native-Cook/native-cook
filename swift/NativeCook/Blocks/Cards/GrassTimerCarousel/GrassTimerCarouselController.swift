import Foundation

/// Owns gesture and lifecycle state while leaving rendering as a pure function of time.
final class GrassTimerCarouselController {
    enum ClockCadence: Equatable {
        case stopped
        case animation
        case seconds

        var interval: TimeInterval {
            switch self {
            case .stopped, .animation: 1.0 / 60.0
            case .seconds: 1
            }
        }
    }

    private struct Transition {
        var from: Double
        var to: Double
        var startedAt: TimeInterval
        var duration: Double
        var curve: (Double) -> Double

        func value(at now: TimeInterval) -> Double {
            let progress = GrassTimerCarouselMath.clamp((now - startedAt) / duration)
            return from + (to - from) * curve(progress)
        }

        func isFinished(at now: TimeInterval) -> Bool {
            now - startedAt >= duration
        }
    }

    private struct CarouselSpring {
        var from: Double
        var to: Double
        var velocity: Double
        var startedAt: TimeInterval

        func value(at now: TimeInterval) -> Double {
            let elapsed = max(0, now - startedAt)
            let omega = 15.7
            let damping = 0.8
            let damped = omega * sqrt(1 - damping * damping)
            let displacement = from - to
            let sineCoefficient = (velocity + damping * omega * displacement) / damped
            return to + exp(-damping * omega * elapsed) *
                (displacement * cos(damped * elapsed) + sineCoefficient * sin(damped * elapsed))
        }

        func velocity(at now: TimeInterval) -> Double {
            let elapsed = max(0, now - startedAt)
            let omega = 15.7
            let damping = 0.8
            let damped = omega * sqrt(1 - damping * damping)
            let decay = damping * omega
            let displacement = from - to
            let sineCoefficient = (self.velocity + decay * displacement) / damped
            let cosine = cos(damped * elapsed)
            let sine = sin(damped * elapsed)
            return exp(-decay * elapsed) * (
                -decay * (displacement * cosine + sineCoefficient * sine) +
                (-displacement * damped * sine + sineCoefficient * damped * cosine)
            )
        }
    }

    private var transition = Transition(from: 0, to: 0, startedAt: 0, duration: 0.001, curve: { $0 })
    private var holdStartedAt: TimeInterval?
    private var releaseStartedAt: TimeInterval?
    private var releaseProgress = 0.0
    private var activeStartedAt: TimeInterval?
    private var demoOrigin: TimeInterval?
    private var interruptedAt: TimeInterval?
    private var suspendedAt: TimeInterval?
    private var quietMode = false
    private var autoPlayEnabled = false
    private var resumeDemoAfterReturn = false
    private var carouselOffset = 0.0
    private var carouselSpring: CarouselSpring?

    private(set) var phase: GrassTimerCarouselPhase = .idle
    private(set) var selectedCard = 0
    private(set) var isFingerDown = false
    private(set) var isCarouselDragging = false
    private(set) var activationSequence = 0
    private(set) var selectionSequence = 0

    func appear(at now: TimeInterval) {
        if demoOrigin == nil { demoOrigin = now }
    }

    func autoPlayChanged(_ enabled: Bool, at now: TimeInterval) {
        autoPlayEnabled = enabled
        guard phase == .idle, !isFingerDown, !quietMode else {
            if !enabled {
                demoOrigin = nil
                resumeDemoAfterReturn = false
            } else if !quietMode, (phase == .morphing || phase == .active || phase == .returning) {
                interruptedAt = now
            }
            return
        }
        demoOrigin = enabled ? now : nil
        interruptedAt = nil
    }

    @discardableResult
    func selectCard(_ index: Int, at now: TimeInterval) -> Bool {
        guard phase == .idle else { return false }
        let next = min(1, max(0, index))
        guard selectedCard != next else { return false }
        selectedCard = next
        interruptDemo(at: now)
        return true
    }

    @discardableResult
    func beginCarousel(at now: TimeInterval) -> Bool {
        guard carouselIsAvailable(at: now), !isFingerDown else { return false }
        // A new drag can catch a settling card without changing ownership or jumping its position.
        carouselOffset = carouselValue(at: now)
        carouselSpring = nil
        isCarouselDragging = true
        interruptDemo(at: now)
        return true
    }

    func updateCarousel(translation: Double) {
        guard isCarouselDragging else { return }
        let spacing = GrassTimerCarouselTiming.carouselSpacing
        switch selectedCard {
        case 0:
            if translation > 0 {
                carouselOffset = rubberBand(translation)
            } else if translation < -spacing {
                carouselOffset = -spacing + rubberBand(translation + spacing)
            } else {
                carouselOffset = translation
            }
        default:
            if translation < 0 {
                carouselOffset = rubberBand(translation)
            } else if translation > spacing {
                carouselOffset = spacing + rubberBand(translation - spacing)
            } else {
                carouselOffset = translation
            }
        }
    }

    func endCarousel(translation: Double, predictedTranslation: Double, at now: TimeInterval) {
        guard isCarouselDragging else { return }
        updateCarousel(translation: translation)
        isCarouselDragging = false
        interruptedAt = now
        let spacing = GrassTimerCarouselTiming.carouselSpacing
        let commits = selectedCard == 0
            ? predictedTranslation < -spacing * 0.45 || translation < -spacing * 0.5
            : predictedTranslation > spacing * 0.45 || translation > spacing * 0.5
        let target = commits ? (selectedCard == 0 ? -spacing : spacing) : 0
        let velocity = GrassTimerCarouselMath.clamp(
            (predictedTranslation - translation) / 0.20,
            lower: -1_600,
            upper: 1_600
        )
        carouselSpring = CarouselSpring(from: carouselOffset, to: target, velocity: velocity, startedAt: now)
    }

    func cancelCarousel(at now: TimeInterval) {
        guard isCarouselDragging else { return }
        isCarouselDragging = false
        interruptedAt = now
        carouselSpring = CarouselSpring(from: carouselOffset, to: 0, velocity: 0, startedAt: now)
    }

    /// Freeze scripted and transitional presentation while backgrounded. The active timer keeps its
    /// monotonic start instant, so its remaining time is still correct when the app returns.
    func scenePhaseChanged(isActive: Bool, at now: TimeInterval) {
        if !isActive {
            if isFingerDown || phase == .pressing { cancelPress(at: now) }
            if isCarouselDragging { cancelCarousel(at: now) }
            if suspendedAt == nil { suspendedAt = now }
            return
        }
        guard let suspendedAt else { return }
        let pause = max(0, now - suspendedAt)
        self.suspendedAt = nil
        demoOrigin = demoOrigin.map { $0 + pause }
        interruptedAt = interruptedAt.map { $0 + pause }
        if phase != .active {
            transition.startedAt += pause
            holdStartedAt = holdStartedAt.map { $0 + pause }
            releaseStartedAt = releaseStartedAt.map { $0 + pause }
            activeStartedAt = activeStartedAt.map { $0 + pause }
            if var carouselSpring {
                carouselSpring.startedAt += pause
                self.carouselSpring = carouselSpring
            }
        }
    }

    /// Quiet mode settles any in-flight presentation into a readable endpoint and removes the
    /// autonomous demo. Leaving quiet mode restarts the demo from its exact resting frame.
    func setQuiet(_ quiet: Bool, autoPlay: Bool, at now: TimeInterval) {
        guard quietMode != quiet else { return }
        autoPlayEnabled = autoPlay
        if quiet {
            if isFingerDown || phase == .pressing { cancelPress(at: now) }
            if isCarouselDragging { cancelCarousel(at: now) }
            releaseStartedAt = nil
            releaseProgress = 0
            demoOrigin = nil
            interruptedAt = nil
            resumeDemoAfterReturn = false
            let current = morph(at: now)
            switch phase {
            case .morphing:
                transition = Transition(
                    from: current,
                    to: 1,
                    startedAt: now,
                    duration: GrassTimerCarouselTiming.reducedFadeDuration,
                    curve: { $0 }
                )
                activeStartedAt = now + GrassTimerCarouselTiming.reducedFadeDuration
            case .returning:
                transition = Transition(
                    from: current,
                    to: 0,
                    startedAt: now,
                    duration: GrassTimerCarouselTiming.reducedFadeDuration,
                    curve: { $0 }
                )
            case .idle, .pressing, .active:
                break
            }
        } else if autoPlay {
            if phase == .idle {
                demoOrigin = now
                interruptedAt = nil
            } else if phase == .morphing || phase == .active || phase == .returning {
                interruptedAt = now
            }
        }
        quietMode = quiet
    }

    func beginPress(at now: TimeInterval, reducedMotion: Bool) {
        guard !isFingerDown else { return }
        let current = presentationMorph(at: now)
        isFingerDown = true
        interruptDemo(at: now)
        guard phase == .idle || phase == .returning else {
            isFingerDown = false
            return
        }
        // Holding freezes the current presentation. Commit and cancellation both retarget from
        // this exact value, including when Start is pressed during the return.
        transition = Transition(from: current, to: current, startedAt: now, duration: 0.001, curve: { $0 })
        holdStartedAt = now
        releaseStartedAt = nil
        phase = .pressing
    }

    func endPress(at now: TimeInterval, reducedMotion: Bool) {
        guard isFingerDown else { return }
        isFingerDown = false
        if reducedMotion {
            if phase == .pressing { _ = activate(at: now, reducedMotion: true) }
            return
        }
        guard phase == .pressing, let holdStartedAt else {
            // A successful long hold may commit before the finger rises. The demo's two-second
            // idle window begins at the actual release, never at the earlier commit frame.
            if autoPlayEnabled, phase == .morphing || phase == .active { interruptedAt = now }
            return
        }
        let elapsed = max(0, now - holdStartedAt)
        if elapsed <= GrassTimerCarouselTiming.quickTapDuration {
            _ = activate(at: now, reducedMotion: false)
        } else if elapsed >= GrassTimerCarouselTiming.holdDuration {
            commitHold(at: now)
        } else {
            cancelPress(at: now)
        }
    }

    func cancelPress(at now: TimeInterval) {
        isFingerDown = false
        guard phase == .pressing else { return }
        let progress = pressProgress(at: now)
        let current = morph(at: now)
        holdStartedAt = nil
        releaseProgress = progress
        releaseStartedAt = now
        transition = Transition(
            from: current,
            to: 0,
            startedAt: now,
            duration: max(0.12, current * GrassTimerCarouselTiming.returnDuration),
            curve: GrassTimerCarouselMath.strongOut
        )
        phase = current > 0.001 ? .returning : .idle
    }

    /// Activate immediately for a quick tap, an accessibility action, or a Resume action.
    @discardableResult
    func activate(at now: TimeInterval, reducedMotion: Bool) -> Bool {
        guard phase != .active, phase != .morphing else { return false }
        let current = presentationMorph(at: now)
        isFingerDown = false
        interruptDemo(at: now)
        holdStartedAt = nil
        releaseStartedAt = nil
        transition = Transition(
            from: current,
            to: 1,
            startedAt: now,
            duration: reducedMotion
                ? GrassTimerCarouselTiming.reducedFadeDuration
                : max(0.12, (1 - current) * GrassTimerCarouselTiming.morphDuration),
            curve: reducedMotion ? { $0 } : GrassTimerCarouselMath.strongInOut
        )
        activeStartedAt = now + transition.duration
        phase = reducedMotion ? .active : .morphing
        activationSequence += 1
        return true
    }

    func stop(at now: TimeInterval, reducedMotion: Bool) {
        // The deterministic demo is rendered analytically while the controller remains idle.
        // Sample that visible mask before interrupting it so Stop retargets without a jump.
        let current = presentationMorph(at: now)
        let visibleDemo = autoPlayEnabled && !quietMode && phase == .idle &&
            demoOrigin != nil && interruptedAt == nil && current > 0.001
        guard phase == .active || phase == .morphing || visibleDemo else { return }
        isFingerDown = false
        interruptDemo(at: now)
        activeStartedAt = nil
        transition = Transition(
            from: current,
            to: 0,
            startedAt: now,
            duration: reducedMotion
                ? GrassTimerCarouselTiming.reducedFadeDuration
                : max(0.12, current * GrassTimerCarouselTiming.returnDuration),
            curve: reducedMotion ? { $0 } : GrassTimerCarouselMath.strongOut
        )
        phase = .returning
    }

    func frame(at now: TimeInterval, autoPlay: Bool, reducedMotion: Bool) -> GrassTimerCarouselFrame {
        autoPlayEnabled = autoPlay
        let currentCarouselOffset = carouselValue(at: now)
        if autoPlay, !reducedMotion, !quietMode, demoResumeIsDue(at: now) {
            switch phase {
            case .idle:
                demoOrigin = now
                interruptedAt = nil
            case .morphing, .active:
                beginAutomaticDemoReturn(at: now)
            case .pressing, .returning:
                break
            }
        }
        if autoPlay, !reducedMotion, !quietMode, demoOrigin != nil, interruptedAt == nil, phase == .idle {
            return Self.demoFrame(at: now - (demoOrigin ?? now), selectedCard: selectedCard)
        }

        if phase == .pressing, !reducedMotion, !quietMode, pressProgress(at: now) >= 1 { commitHold(at: now) }
        let p = morph(at: now)
        if phase == .morphing, p >= 0.999 { phase = .active }
        if phase == .returning, p <= 0.001 {
            phase = .idle
            if resumeDemoAfterReturn, autoPlay, !reducedMotion, !quietMode {
                resumeDemoAfterReturn = false
                demoOrigin = now
                interruptedAt = nil
                return Self.demoFrame(at: 0, selectedCard: selectedCard)
            }
        }

        let press = pressProgress(at: now)
        let timerTime = activeStartedAt.map { max(0, now - $0) } ?? 0
        return Self.makeFrame(
            phase: phase,
            selectedCard: selectedCard,
            morph: reducedMotion ? 0 : p,
            press: reducedMotion ? 0 : press,
            timerVisible: reducedMotion ? p : Self.timerVisibility(for: p),
            elapsed: timerTime,
            landing: phase == .active ? max(0, 1 - timerTime / 0.18) : 0,
            activationSequence: activationSequence,
            carouselOffset: currentCarouselOffset,
            selectionSequence: selectionSequence
        )
    }

    func clockCadence(at now: TimeInterval, autoPlay: Bool, reducedMotion: Bool) -> ClockCadence {
        if suspendedAt != nil { return .stopped }
        if reducedMotion, phase == .pressing { return .stopped }
        if isCarouselDragging || carouselSpring != nil { return .animation }
        let demoRunning = autoPlay && !reducedMotion && !quietMode && phase == .idle &&
            (demoOrigin != nil || interruptedAt != nil)
        if demoRunning { return .animation }
        let demoWaiting = autoPlay && !reducedMotion && !quietMode && interruptedAt != nil
        if demoWaiting { return .animation }
        if phase == .pressing || phase == .morphing || phase == .returning ||
            isFingerDown || releaseStartedAt != nil || !transition.isFinished(at: now) {
            return .animation
        }
        if phase == .active {
            if let activeStartedAt, now - activeStartedAt < 0.18 { return .animation }
            return remainingSeconds(at: now) > 0 ? .seconds : .stopped
        }
        return .stopped
    }

    static func demoFrame(at elapsed: TimeInterval, selectedCard: Int = 0) -> GrassTimerCarouselFrame {
        let remainder = elapsed.truncatingRemainder(dividingBy: GrassTimerCarouselTiming.loop)
        let time = remainder >= 0 ? remainder : remainder + GrassTimerCarouselTiming.loop
        let press: Double
        let morph: Double
        let phase: GrassTimerCarouselPhase
        let elapsedActive: Double
        if time < 1.6 {
            press = 0; morph = 0; phase = .idle; elapsedActive = 0
        } else if time < 1.72 {
            press = 0; morph = 0; phase = .pressing; elapsedActive = 0
        } else if time < 2.25 {
            press = (time - 1.72) / 0.53; morph = 0; phase = .pressing; elapsedActive = 0
        } else if time < 3.05 {
            press = 1; morph = GrassTimerCarouselMath.strongInOut((time - 2.25) / 0.80); phase = .morphing; elapsedActive = 0
        } else if time < 6.45 {
            press = 1; morph = 1; phase = .active; elapsedActive = time - 3.05
        } else if time < 7.2 {
            press = 0; morph = 1 - GrassTimerCarouselMath.strongOut((time - 6.45) / 0.75); phase = .returning; elapsedActive = 3.4
        } else {
            press = 0; morph = 0; phase = .idle; elapsedActive = 0
        }
        let anticipation = time >= 1.6 && time < 1.72 ? GrassTimerCarouselMath.strongOut((time - 1.6) / 0.12) : 0
        var frame = makeFrame(
            phase: phase,
            selectedCard: selectedCard,
            morph: morph,
            press: press,
            timerVisible: timerVisibility(for: morph),
            elapsed: elapsedActive,
            landing: phase == .active ? max(0, 1 - elapsedActive / 0.18) : 0,
            activationSequence: 0,
            carouselOffset: 0,
            selectionSequence: 0
        )
        frame.chipScale = 1 - anticipation * 0.03
        return frame
    }

    private func commitHold(at now: TimeInterval) {
        guard phase == .pressing else { return }
        // Keep the physical hold ownership until the gesture ends, but let activate own the single
        // transition and activation token.
        let held = isFingerDown
        _ = activate(at: now, reducedMotion: false)
        isFingerDown = held
    }

    private func presentationMorph(at now: TimeInterval) -> Double {
        if autoPlayEnabled, !quietMode, let demoOrigin, interruptedAt == nil, phase == .idle {
            return Self.demoFrame(at: now - demoOrigin, selectedCard: selectedCard).morph
        }
        return morph(at: now)
    }

    private func carouselIsAvailable(at now: TimeInterval) -> Bool {
        guard phase == .idle else { return false }
        if autoPlayEnabled, !quietMode, let demoOrigin, interruptedAt == nil {
            let frame = Self.demoFrame(at: now - demoOrigin, selectedCard: selectedCard)
            return frame.phase == .idle && frame.morph <= 0.001
        }
        return morph(at: now) <= 0.001
    }

    private func morph(at now: TimeInterval) -> Double {
        GrassTimerCarouselMath.clamp(transition.value(at: now))
    }

    private func pressProgress(at now: TimeInterval) -> Double {
        if let holdStartedAt {
            return GrassTimerCarouselMath.clamp((now - holdStartedAt) / GrassTimerCarouselTiming.holdDuration)
        }
        if let releaseStartedAt {
            let value = GrassTimerCarouselMath.springBack(now - releaseStartedAt, initial: releaseProgress)
            if now - releaseStartedAt > 0.8 { self.releaseStartedAt = nil }
            return value
        }
        return 0
    }

    private func interruptDemo(at now: TimeInterval) {
        demoOrigin = nil
        interruptedAt = now
        resumeDemoAfterReturn = false
    }

    private func demoResumeIsDue(at now: TimeInterval) -> Bool {
        guard !isFingerDown, !isCarouselDragging, let interruptedAt else { return false }
        return now - interruptedAt >= 2
    }

    private func carouselValue(at now: TimeInterval) -> Double {
        guard let spring = carouselSpring else { return carouselOffset }
        let value = spring.value(at: now)
        let spacing = GrassTimerCarouselTiming.carouselSpacing
        let crossedLead = selectedCard == 0
            ? spring.to < 0 && value <= -spacing
            : spring.to > 0 && value >= spacing
        if crossedLead {
            let shifted = value + (selectedCard == 0 ? spacing : -spacing)
            let velocity = spring.velocity(at: now)
            selectedCard = selectedCard == 0 ? 1 : 0
            selectionSequence += 1
            carouselOffset = shifted
            carouselSpring = CarouselSpring(from: shifted, to: 0, velocity: velocity, startedAt: now)
            return shifted
        }
        if now - spring.startedAt >= GrassTimerCarouselTiming.carouselSettleDuration {
            if abs(spring.to) > 0.5 {
                selectedCard = selectedCard == 0 ? 1 : 0
                selectionSequence += 1
            }
            carouselOffset = 0
            carouselSpring = nil
            return 0
        }
        carouselOffset = value
        return value
    }

    private func rubberBand(_ value: Double) -> Double {
        let magnitude = abs(value)
        let bent = magnitude * 0.42 / (1 + magnitude / 120)
        return value < 0 ? -bent : bent
    }

    private func beginAutomaticDemoReturn(at now: TimeInterval) {
        let current = morph(at: now)
        activeStartedAt = nil
        interruptedAt = nil
        demoOrigin = nil
        resumeDemoAfterReturn = true
        transition = Transition(
            from: current,
            to: 0,
            startedAt: now,
            duration: max(0.12, current * GrassTimerCarouselTiming.returnDuration),
            curve: GrassTimerCarouselMath.strongOut
        )
        phase = .returning
    }

    private func remainingSeconds(at now: TimeInterval) -> Int {
        let elapsed = activeStartedAt.map { max(0, now - $0) } ?? 0
        let duration = selectedCard == 0 ? GrassTimerCarouselTiming.grassDuration : GrassTimerCarouselTiming.quietDuration
        return max(0, duration - Int(elapsed.rounded(.down)))
    }

    private static func timerVisibility(for morph: Double) -> Double {
        GrassTimerCarouselMath.clamp((morph - 0.60) / 0.40)
    }

    private static func makeFrame(
        phase: GrassTimerCarouselPhase,
        selectedCard: Int,
        morph: Double,
        press: Double,
        timerVisible: Double,
        elapsed: Double,
        landing: Double,
        activationSequence: Int,
        carouselOffset: Double,
        selectionSequence: Int
    ) -> GrassTimerCarouselFrame {
        let p = GrassTimerCarouselMath.clamp(morph)
        let duration = selectedCard == 0 ? GrassTimerCarouselTiming.grassDuration : GrassTimerCarouselTiming.quietDuration
        let remaining = max(0, duration - Int(elapsed.rounded(.down)))
        let remainingFraction = max(0, 1 - elapsed / Double(duration))
        return GrassTimerCarouselFrame(
            phase: phase,
            selectedCard: selectedCard,
            aperture: GrassTimerCarouselRect.interpolate(from: GrassTimerCarouselTiming.restingCard, to: GrassTimerCarouselTiming.timerAperture, progress: p),
            morph: p,
            pressProgress: GrassTimerCarouselMath.clamp(press),
            chipScale: 0.97 + 0.03 * (1 - GrassTimerCarouselMath.clamp(press)),
            imageParallax: -4 * GrassTimerCarouselMath.clamp(press),
            secondCardOffset: 160 * p,
            secondCardOpacity: 1 - p,
            copyOpacity: (1 - GrassTimerCarouselMath.clamp(p / 0.40)) * (1 - timerVisible),
            timerOpacity: timerVisible,
            ringOpacity: p == 0 ? 0 : timerVisible,
            ringProgress: p == 0 ? 0 : remainingFraction,
            landingHighlightOpacity: landing,
            remainingSeconds: remaining,
            activationSequence: activationSequence,
            carouselOffset: carouselOffset,
            neighborCardX: (selectedCard == 0 ? 263 : -235) + carouselOffset + (selectedCard == 0 ? 1 : -1) * 160 * p,
            selectionSequence: selectionSequence
        )
    }
}
