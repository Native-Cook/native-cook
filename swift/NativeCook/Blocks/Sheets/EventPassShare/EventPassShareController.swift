import Foundation

/// Owns every time-dependent value so rendering and the later platform ports can sample one frame.
final class EventPassShareController {
    enum ClockCadence: Equatable {
        case stopped
        case animation

        var interval: TimeInterval { 1.0 / 60.0 }
    }

    private struct AngleTransition {
        var from: Double
        var to: Double
        var startedAt: TimeInterval
        var duration: TimeInterval

        func value(at now: TimeInterval) -> Double {
            let p = EventPassShareMath.strongInOut((now - startedAt) / max(0.001, duration))
            return EventPassShareMath.lerp(from, to, p)
        }

        func isFinished(at now: TimeInterval) -> Bool { now - startedAt >= duration }
    }

    private struct TiltSpring {
        var x: Double
        var y: Double
        var velocityX: Double
        var velocityY: Double
        var startedAt: TimeInterval

        func value(at now: TimeInterval) -> (x: Double, y: Double) {
            let elapsed = max(0, now - startedAt)
            return (
                EventPassShareMath.spring(elapsed: elapsed, position: x, velocity: velocityX),
                EventPassShareMath.spring(elapsed: elapsed, position: y, velocity: velocityY)
            )
        }
    }

    private(set) var phase = EventPassSharePhase.front
    private(set) var isDragging = false

    private var autoPlayEnabled = false
    private var quietMode = false
    private var isForeground = true
    private var isVisible = true
    private var sharePresented = false
    private var suspendedAt: TimeInterval?
    private var demoOrigin: TimeInterval?
    private var interruptedAt: TimeInterval?
    private var resumeDemoAfterFront = false
    private var pendingDemoRestart = false

    private var angle = 0.0
    private var tiltX = 0.0
    private var tiltY = 0.0
    private var angleTransition: AngleTransition?
    private var interruptedTurnTarget: Double?
    private var tiltSpring: TiltSpring?
    private var touchStartedAt: TimeInterval?
    private var touchDown = false
    private var interactionMaxDistance = 0.0
    private var pressReleaseAt: TimeInterval?
    private var pressReleaseDepth = 0.0

    private var quietBack = false
    private var quietMix = 0.0
    private var quietFrom = 0.0
    private var quietTo = 0.0
    private var quietStartedAt: TimeInterval?
    private var copyConfirmedUntil: TimeInterval?

    func appear(autoPlay: Bool, quiet: Bool, at now: TimeInterval) {
        autoPlayEnabled = autoPlay
        quietMode = quiet
        quietMix = quietBack ? 1 : 0
        demoOrigin = autoPlay && !quiet ? now : nil
    }

    func setAutoPlay(_ enabled: Bool, at now: TimeInterval) {
        guard autoPlayEnabled != enabled else { return }
        autoPlayEnabled = enabled
        if !enabled {
            demoOrigin = nil
            interruptedAt = nil
            resumeDemoAfterFront = false
            pendingDemoRestart = false
        } else if !quietMode {
            if isRunning {
                if phase == .front, abs(angle) < 0.001, !isDragging {
                    demoOrigin = now
                } else {
                    interruptedAt = now
                }
            } else {
                pendingDemoRestart = true
            }
        }
    }

    func setQuiet(_ quiet: Bool, at now: TimeInterval) {
        guard quietMode != quiet else { return }
        let current = frame(at: now)
        quietMode = quiet
        demoOrigin = nil
        interruptedAt = nil
        resumeDemoAfterFront = false
        pendingDemoRestart = false
        touchDown = false
        isDragging = false
        tiltSpring = nil
        tiltX = 0
        tiltY = 0
        angleTransition = nil
        if quiet {
            quietBack = current.flipAngle >= 90 || current.backOpacity > current.frontOpacity
            quietMix = quietBack ? 1 : 0
            angle = quietBack ? 180 : 0
            phase = quietBack ? .back : .front
        } else {
            angle = quietBack ? 180 : 0
            phase = quietBack ? .back : .front
            quietMix = quietBack ? 1 : 0
            if autoPlayEnabled {
                if isRunning { interruptedAt = now }
                else { pendingDemoRestart = true }
            }
        }
    }

    func setForeground(_ foreground: Bool, at now: TimeInterval) {
        guard isForeground != foreground else { return }
        isForeground = foreground
        updateSuspension(at: now)
    }

    func setSharePresented(_ presented: Bool, at now: TimeInterval) {
        guard sharePresented != presented else { return }
        sharePresented = presented
        updateSuspension(at: now)
    }

    func setVisible(_ visible: Bool, at now: TimeInterval) {
        guard isVisible != visible else { return }
        isVisible = visible
        updateSuspension(at: now)
    }

    @discardableResult
    func beginInteraction(at now: TimeInterval) -> Bool {
        guard isRunning else { return false }
        let visible = frame(at: now)
        angle = visible.flipAngle
        tiltX = visible.tiltX
        tiltY = visible.tiltY
        demoOrigin = nil
        interruptedAt = nil
        resumeDemoAfterFront = false
        interruptedTurnTarget = angleTransition?.to
        angleTransition = nil
        tiltSpring = nil
        isDragging = true
        touchDown = true
        touchStartedAt = now
        pressReleaseAt = nil
        interactionMaxDistance = 0
        phase = .dragging
        return true
    }

    func updateInteraction(horizontal: Double, vertical: Double) {
        guard isDragging else { return }
        interactionMaxDistance = max(interactionMaxDistance, hypot(horizontal, vertical))
        tiltY = EventPassShareMath.clamp(horizontal / 269 * 10, lower: -10, upper: 10)
        tiltX = EventPassShareMath.clamp(-vertical / 381 * 8, lower: -8, upper: 8)
    }

    @discardableResult
    func endInteraction(horizontal: Double, vertical: Double, predictedHorizontal: Double, predictedVertical: Double, at now: TimeInterval, quiet: Bool) -> Bool {
        guard isDragging else { return false }
        updateInteraction(horizontal: horizontal, vertical: vertical)
        isDragging = false
        releasePress(at: now)
        interruptedAt = now
        let wasTap = interactionMaxDistance < 8
        interactionMaxDistance = 0
        if wasTap {
            tiltX = 0
            tiltY = 0
            toggleFace(at: now, quiet: quiet)
            return true
        }
        interruptedTurnTarget = nil
        let velocityX = EventPassShareMath.clamp((-(predictedVertical - vertical) / 0.20) / 381 * 8, lower: -80, upper: 80)
        let velocityY = EventPassShareMath.clamp(((predictedHorizontal - horizontal) / 0.20) / 269 * 10, lower: -100, upper: 100)
        tiltSpring = TiltSpring(x: tiltX, y: tiltY, velocityX: velocityX, velocityY: velocityY, startedAt: now)
        let target = angle >= 90 ? 180.0 : 0.0
        startTurn(to: target, at: now)
        phase = .settling
        return false
    }

    func cancelInteraction(at now: TimeInterval) {
        guard isDragging || touchDown else { return }
        isDragging = false
        releasePress(at: now)
        interactionMaxDistance = 0
        interruptedAt = now
        tiltSpring = TiltSpring(x: tiltX, y: tiltY, velocityX: 0, velocityY: 0, startedAt: now)
        interruptedTurnTarget = nil
        startTurn(to: angle >= 90 ? 180 : 0, at: now)
        phase = .settling
    }

    func toggleFace(at now: TimeInterval, quiet: Bool) {
        interruptedAt = now
        demoOrigin = nil
        resumeDemoAfterFront = false
        if quiet {
            quietMode = true
            let current = quietMixValue(at: now)
            quietBack.toggle()
            quietFrom = current
            quietTo = quietBack ? 1 : 0
            quietStartedAt = now
            angle = quietBack ? 180 : 0
            phase = quietBack ? .back : .front
            return
        }
        let current = angleValue(at: now)
        angle = current
        let target: Double
        if let interruptedTurnTarget {
            target = interruptedTurnTarget >= 90 ? 0 : 180
        } else if let transition = angleTransition {
            target = transition.to >= 90 ? 0 : 180
        } else {
            target = current >= 90 ? 0 : 180
        }
        interruptedTurnTarget = nil
        startTurn(to: target, at: now)
        phase = target == 180 ? .turningBack : .turningFront
    }

    func confirmCopy(at now: TimeInterval) {
        copyConfirmedUntil = now + EventPassShareTiming.copyConfirmationDuration
    }

    func frame(at now: TimeInterval) -> EventPassShareFrame {
        guard suspendedAt == nil else { return makeFrame(at: suspendedAt ?? now) }
        advanceState(at: now)
        return makeFrame(at: now)
    }

    func clockCadence(at now: TimeInterval) -> ClockCadence {
        guard isRunning else { return .stopped }
        if autoPlayEnabled, !quietMode, demoOrigin != nil || interruptedAt != nil { return .animation }
        if isDragging || touchDown || angleTransition != nil || tiltSpring != nil || quietStartedAt != nil { return .animation }
        if let copyConfirmedUntil, now < copyConfirmedUntil { return .animation }
        return .stopped
    }

    var isDemoDriven: Bool {
        isRunning && suspendedAt == nil && autoPlayEnabled && !quietMode &&
            demoOrigin != nil && interruptedAt == nil && phase == .front
    }

    static func demoFrame(at elapsed: TimeInterval) -> EventPassShareFrame {
        let remainder = elapsed.truncatingRemainder(dividingBy: EventPassShareTiming.loop)
        let time = remainder >= 0 ? remainder : remainder + EventPassShareTiming.loop
        var angle = 0.0
        var tiltX = 0.0
        var tiltY = 0.0
        var phase = EventPassSharePhase.front

        if time >= EventPassShareTiming.foilStart, time < EventPassShareTiming.foilEnd {
            let progress = (time - EventPassShareTiming.foilStart) /
                (EventPassShareTiming.foilEnd - EventPassShareTiming.foilStart)
            let bell = sin(.pi * progress)
            tiltY = EventPassShareTiming.foilTiltY * sin(2 * .pi * progress) * bell
            tiltX = EventPassShareTiming.foilTiltX * bell * bell
        } else if time >= EventPassShareTiming.turnBackStart,
                  time < EventPassShareTiming.turnBackStart + EventPassShareTiming.demoTurnDuration {
            angle = 180 * EventPassShareMath.strongInOut(
                (time - EventPassShareTiming.turnBackStart) / EventPassShareTiming.demoTurnDuration
            )
            phase = .turningBack
        } else if time >= EventPassShareTiming.turnBackStart + EventPassShareTiming.demoTurnDuration,
                  time < EventPassShareTiming.turnFrontStart {
            angle = 180
            phase = .back
        } else if time >= EventPassShareTiming.turnFrontStart,
                  time < EventPassShareTiming.turnFrontStart + EventPassShareTiming.demoTurnDuration {
            angle = 180 * (1 - EventPassShareMath.strongInOut(
                (time - EventPassShareTiming.turnFrontStart) / EventPassShareTiming.demoTurnDuration
            ))
            phase = .turningFront
        } else if time >= EventPassShareTiming.nodStart, time < EventPassShareTiming.nodEnd {
            let bell = sin(.pi * (time - EventPassShareTiming.nodStart) /
                (EventPassShareTiming.nodEnd - EventPassShareTiming.nodStart))
            tiltY = EventPassShareTiming.nodTiltY * bell * bell
            tiltX = EventPassShareTiming.nodTiltX * bell * bell
        }
        let front = angle < 90 ? 1.0 : 0.0
        return EventPassShareFrame(
            phase: phase,
            flipAngle: angle,
            tiltX: tiltX,
            tiltY: tiltY,
            pressScale: 1,
            frontOpacity: front,
            backOpacity: 1 - front,
            quietCrossfade: false,
            copyConfirmed: false
        )
    }

    private var isRunning: Bool { isForeground && isVisible && !sharePresented }

    private func advanceState(at now: TimeInterval) {
        if let copyConfirmedUntil, now >= copyConfirmedUntil { self.copyConfirmedUntil = nil }
        if let quietStartedAt, now - quietStartedAt >= EventPassShareTiming.quietFadeDuration {
            quietMix = quietTo
            self.quietStartedAt = nil
        }
        if let spring = tiltSpring {
            let settled = spring.value(at: now)
            tiltX = settled.x
            tiltY = settled.y
            if now - spring.startedAt >= EventPassShareTiming.settleDuration {
                tiltX = 0
                tiltY = 0
                tiltSpring = nil
            }
        }
        if let transition = angleTransition {
            angle = transition.value(at: now)
            if transition.isFinished(at: now) {
                angle = transition.to
                angleTransition = nil
            }
        }
        if phase == .settling, tiltSpring == nil, angleTransition == nil {
            phase = angle >= 90 ? .back : .front
        } else if phase == .turningBack, angleTransition == nil {
            phase = .back
        } else if phase == .turningFront, angleTransition == nil {
            phase = .front
            if resumeDemoAfterFront, autoPlayEnabled, !quietMode {
                resumeDemoAfterFront = false
                interruptedAt = nil
                demoOrigin = now
            }
        }
        if autoPlayEnabled, !quietMode, !isDragging, let interruptedAt,
           now - interruptedAt >= EventPassShareTiming.resumeDelay {
            if phase == .front, angle < 0.001, tiltSpring == nil, angleTransition == nil {
                self.interruptedAt = nil
                demoOrigin = now
            } else if angleTransition == nil {
                resumeDemoAfterFront = true
                startTurn(to: 0, at: now)
                phase = .turningFront
            }
        }
    }

    private func makeFrame(at now: TimeInterval) -> EventPassShareFrame {
        if autoPlayEnabled, !quietMode, let demoOrigin, interruptedAt == nil, phase == .front {
            var demo = Self.demoFrame(at: now - demoOrigin)
            demo.copyConfirmed = copyConfirmedUntil.map { now < $0 } ?? false
            return demo
        }
        let currentAngle = angleValue(at: now)
        let currentTilt: (x: Double, y: Double)
        if let tiltSpring { currentTilt = tiltSpring.value(at: now) }
        else { currentTilt = (tiltX, tiltY) }
        let press = pressScale(at: now)
        let quietMix = quietMixValue(at: now)
        let frontOpacity: Double
        let backOpacity: Double
        if quietMode {
            frontOpacity = 1 - quietMix
            backOpacity = quietMix
        } else {
            frontOpacity = currentAngle < 90 ? 1 : 0
            backOpacity = 1 - frontOpacity
        }
        return EventPassShareFrame(
            phase: phase,
            flipAngle: quietMode ? (quietBack ? 180 : 0) : currentAngle,
            tiltX: quietMode ? 0 : currentTilt.x,
            tiltY: quietMode ? 0 : currentTilt.y,
            pressScale: press,
            frontOpacity: frontOpacity,
            backOpacity: backOpacity,
            quietCrossfade: quietMode,
            copyConfirmed: copyConfirmedUntil.map { now < $0 } ?? false
        )
    }

    private func angleValue(at now: TimeInterval) -> Double {
        angleTransition?.value(at: now) ?? angle
    }

    private func quietMixValue(at now: TimeInterval) -> Double {
        guard let quietStartedAt else { return quietMix }
        let p = EventPassShareMath.clamp((now - quietStartedAt) / EventPassShareTiming.quietFadeDuration)
        return EventPassShareMath.lerp(quietFrom, quietTo, p)
    }

    private func pressScale(at now: TimeInterval) -> Double {
        if touchDown, let touchStartedAt {
            let depth = 0.03 * EventPassShareMath.strongOut((now - touchStartedAt) / EventPassShareTiming.pressDuration)
            return 1 - depth
        }
        if let pressReleaseAt {
            let depth = EventPassShareMath.pressRelease(elapsed: now - pressReleaseAt, depth: pressReleaseDepth)
            if now - pressReleaseAt >= 0.55 { self.pressReleaseAt = nil }
            return 1 - depth
        }
        return 1
    }

    private func releasePress(at now: TimeInterval) {
        let currentScale = pressScale(at: now)
        touchDown = false
        touchStartedAt = nil
        pressReleaseDepth = max(0, 1 - currentScale)
        pressReleaseAt = now
    }

    private func startTurn(to target: Double, at now: TimeInterval) {
        let current = angleValue(at: now)
        angle = current
        let distance = abs(target - current)
        guard distance > 0.001 else {
            angle = target
            angleTransition = nil
            return
        }
        angleTransition = AngleTransition(
            from: current,
            to: target,
            startedAt: now,
            duration: max(0.12, EventPassShareTiming.flipDuration * distance / 180)
        )
    }

    private func updateSuspension(at now: TimeInterval) {
        if !isRunning {
            if isDragging || touchDown { cancelInteraction(at: now) }
            if suspendedAt == nil { suspendedAt = now }
            return
        }
        guard let suspendedAt else { return }
        let pause = max(0, now - suspendedAt)
        self.suspendedAt = nil
        demoOrigin = demoOrigin.map { $0 + pause }
        interruptedAt = interruptedAt.map { $0 + pause }
        touchStartedAt = touchStartedAt.map { $0 + pause }
        pressReleaseAt = pressReleaseAt.map { $0 + pause }
        quietStartedAt = quietStartedAt.map { $0 + pause }
        copyConfirmedUntil = copyConfirmedUntil.map { $0 + pause }
        if var transition = angleTransition {
            transition.startedAt += pause
            angleTransition = transition
        }
        if var spring = tiltSpring {
            spring.startedAt += pause
            tiltSpring = spring
        }
        if pendingDemoRestart, autoPlayEnabled, !quietMode {
            pendingDemoRestart = false
            if phase == .front, abs(angle) < 0.001, !isDragging, angleTransition == nil, tiltSpring == nil {
                interruptedAt = nil
                demoOrigin = now
            } else {
                demoOrigin = nil
                interruptedAt = now
            }
        }
    }
}
