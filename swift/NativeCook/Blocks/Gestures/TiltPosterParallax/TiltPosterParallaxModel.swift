import CoreMotion
import Observation
import QuartzCore
import UIKit

@MainActor
@Observable
final class TiltPosterParallaxModel {
    struct Finger: Equatable {
        var x: CGFloat = 0
        var y: CGFloat = 0
        var alpha = 0.0
    }

    private struct SpringState {
        var value: Double
        var velocity: Double
    }

    private struct SpringChannel {
        var value: Double
        var velocity: Double = 0
        var target: Double
        var parameters: TiltPosterParallaxTokens.Spring
    }

    let autoPlay: Bool

    var tiltX = 0.0
    var tiltY = 0.0
    var lift = 1.0
    var flip = 0.0
    var clock = 0.0
    var finger = Finger()
    var saved = false
    var savedProgress = 0.0
    var watchPress = 1.0

    @ObservationIgnored private let motion = CMMotionManager()
    @ObservationIgnored private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    @ObservationIgnored private let selection = UISelectionFeedbackGenerator()
    @ObservationIgnored private let success = UINotificationFeedbackGenerator()
    @ObservationIgnored private var cardSize = CGSize.zero
    @ObservationIgnored private var watchPoint = CGPoint.zero
    @ObservationIgnored private var reduceMotion = false
    @ObservationIgnored private var lastTimestamp: CFTimeInterval?
    @ObservationIgnored private var loopStart: CFTimeInterval?
    @ObservationIgnored private var currentTimestamp: CFTimeInterval = 0
    @ObservationIgnored private var paused = false
    @ObservationIgnored private var realTouchDown = false
    @ObservationIgnored private var lastInteraction: CFTimeInterval?
    @ObservationIgnored private var returningToRest = false
    @ObservationIgnored private var ambientX = 0.0
    @ObservationIgnored private var ambientY = 0.0
    @ObservationIgnored private var baseRoll = 0.0
    @ObservationIgnored private var basePitch = 0.0
    @ObservationIgnored private var hasSensorBase = false
    @ObservationIgnored private var holdActive = false
    @ObservationIgnored private var holdOrigin = CGPoint.zero
    @ObservationIgnored private var holdStartPoint = CGPoint.zero
    @ObservationIgnored private var holdStartedAt: CFTimeInterval = 0
    @ObservationIgnored private var holdTravel: CGFloat = 0
    @ObservationIgnored private var holdX = 0.0
    @ObservationIgnored private var holdY = 0.0
    @ObservationIgnored private var touchedEdge = false
    @ObservationIgnored private var flipped = false
    @ObservationIgnored private var visibleFace = 0
    @ObservationIgnored private var offsetX = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.release)
    @ObservationIgnored private var offsetY = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.release)
    @ObservationIgnored private var flipSpring = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.flip)
    @ObservationIgnored private var liftSpring = SpringChannel(value: 1, target: 1, parameters: TiltPosterParallaxTokens.Springs.lift)
    @ObservationIgnored private var savedSpring = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.morph)
    @ObservationIgnored private var pressSpring = SpringChannel(value: 1, target: 1, parameters: TiltPosterParallaxTokens.Springs.press)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    func configure(cardSize: CGSize, watchPoint: CGPoint, reduceMotion: Bool) {
        self.cardSize = cardSize
        self.watchPoint = watchPoint
        self.reduceMotion = reduceMotion
    }

    func start() {
        if motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive {
            motion.deviceMotionUpdateInterval = 1 / 120
            motion.startDeviceMotionUpdates()
        }
        lightImpact.prepare()
        mediumImpact.prepare()
        selection.prepare()
        success.prepare()
    }

    func stop() {
        motion.stopDeviceMotionUpdates()
    }

    func advance(to timestamp: CFTimeInterval) {
        currentTimestamp = timestamp
        guard cardSize != .zero else { return }
        guard let previous = lastTimestamp else {
            lastTimestamp = timestamp
            loopStart = timestamp
            applyRest()
            return
        }

        let delta = min(max(timestamp - previous, 0), TiltPosterParallaxTokens.Timing.maxFrameDelta)
        lastTimestamp = timestamp

        if autoPlay, !paused {
            let start = loopStart ?? timestamp
            let elapsed = max(0, timestamp - start)
            let phase = heldPhase ?? elapsed.truncatingRemainder(dividingBy: TiltPosterParallaxTokens.loop)
            applyAutoplay(phase: phase)
        } else {
            advanceManual(delta: delta, timestamp: timestamp)
        }
    }

    func touchBegan(at point: CGPoint, timestamp: CFTimeInterval) {
        guard cardSize.width > 0, cardSize.height > 0 else { return }
        pauseAutoplay(timestamp: timestamp, touchDown: true)
        holdActive = true
        holdStartedAt = timestamp
        holdStartPoint = point
        holdTravel = 0
        touchedEdge = false
        let currentX = tiltX - ambientX
        let currentY = tiltY - ambientY
        holdOrigin = CGPoint(
            x: point.x - CGFloat(currentX / TiltPosterParallaxTokens.Tilt.perHalfCard) * cardSize.width / 2,
            y: point.y - CGFloat(currentY / TiltPosterParallaxTokens.Tilt.perHalfCard) * cardSize.height / 2
        )
        holdX = currentX
        holdY = currentY
        offsetX.value = 0
        offsetX.velocity = 0
        offsetY.value = 0
        offsetY.velocity = 0
        if !reduceMotion {
            liftSpring.value = lift
            liftSpring.target = TiltPosterParallaxTokens.Tilt.lift
        }
    }

    func touchMoved(to point: CGPoint, timestamp: CFTimeInterval) {
        guard holdActive else { return }
        lastInteraction = timestamp
        let next = fingerTilt(dx: point.x - holdOrigin.x, dy: point.y - holdOrigin.y)
        holdX = next.x
        holdY = next.y
        holdTravel = max(holdTravel, hypot(point.x - holdStartPoint.x, point.y - holdStartPoint.y))
        let limit = reduceMotion ? TiltPosterParallaxTokens.Tilt.reducedMax : TiltPosterParallaxTokens.Tilt.max
        let atEdge = max(abs(holdX), abs(holdY)) > limit
        if atEdge, !touchedEdge {
            selection.selectionChanged()
            selection.prepare()
        }
        touchedEdge = atEdge
    }

    func touchEnded(velocity: CGVector, timestamp: CFTimeInterval, cancelled: Bool) {
        guard holdActive else { return }
        holdActive = false
        realTouchDown = false
        lastInteraction = timestamp

        let isTap = !cancelled
            && timestamp - holdStartedAt < TiltPosterParallaxTokens.Flip.tapDuration
            && holdTravel < TiltPosterParallaxTokens.Flip.slop
            && abs(velocity.dx) + abs(velocity.dy) < TiltPosterParallaxTokens.Flip.tapSpeed
        if isTap {
            lightImpact.impactOccurred()
            lightImpact.prepare()
            setFlipped(!flipped)
        } else {
            liftSpring.target = flipped && !reduceMotion ? TiltPosterParallaxTokens.Tilt.lift : 1
        }

        offsetX.value = holdX
        offsetY.value = holdY
        offsetX.target = 0
        offsetY.target = 0
        if reduceMotion {
            offsetX.velocity = 0
            offsetY.velocity = 0
        } else {
            offsetX.velocity = Double(velocity.dx / max(cardSize.width / 2, 1)) * TiltPosterParallaxTokens.Tilt.perHalfCard
            offsetY.velocity = Double(velocity.dy / max(cardSize.height / 2, 1)) * TiltPosterParallaxTokens.Tilt.perHalfCard
        }
    }

    func toggleFlip() {
        pauseAutoplay(timestamp: CACurrentMediaTime(), touchDown: false)
        lightImpact.impactOccurred()
        lightImpact.prepare()
        setFlipped(!flipped)
    }

    func watchPressBegan() {
        pauseAutoplay(timestamp: CACurrentMediaTime(), touchDown: false)
        pressSpring.target = 0.97
    }

    func watchPressEnded() {
        lastInteraction = CACurrentMediaTime()
        pressSpring.target = 1
    }

    func toggleSaved() {
        pauseAutoplay(timestamp: CACurrentMediaTime(), touchDown: false)
        saved.toggle()
        savedSpring.target = saved ? 1 : 0
        if saved {
            success.notificationOccurred(.success)
            success.prepare()
        } else {
            lightImpact.impactOccurred()
            lightImpact.prepare()
        }
    }

    private var heldPhase: Double? {
        guard let hold = TiltPosterParallaxTokens.QA.hold else { return nil }
        return TiltPosterParallaxTokens.QA.holdAt[hold]
    }

    private func pauseAutoplay(timestamp: CFTimeInterval, touchDown: Bool) {
        guard autoPlay else { return }
        paused = true
        realTouchDown = touchDown
        returningToRest = false
        lastInteraction = timestamp
        flipSpring.value = flip
        flipSpring.velocity = 0
        flipSpring.target = flipped ? 1 : 0
        liftSpring.value = lift
        liftSpring.velocity = 0
        savedSpring.value = savedProgress
        savedSpring.velocity = 0
        savedSpring.target = saved ? 1 : 0
        visibleFace = cos(tiltX + flip * .pi) >= 0 ? 0 : 1
    }

    private func setFlipped(_ next: Bool) {
        flipped = next
        flipSpring.value = flip
        flipSpring.target = next ? 1 : 0
        liftSpring.value = lift
        liftSpring.target = next && !reduceMotion ? TiltPosterParallaxTokens.Tilt.lift : 1
    }

    private func advanceManual(delta: Double, timestamp: CFTimeInterval) {
        updateAmbient(delta: delta)
        step(&flipSpring, delta: delta)
        step(&liftSpring, delta: delta)
        step(&savedSpring, delta: delta)
        step(&pressSpring, delta: delta)
        if !holdActive {
            step(&offsetX, delta: delta)
            step(&offsetY, delta: delta)
        }

        flip = reduceMotion ? clamped(flipSpring.value) : flipSpring.value
        lift = reduceMotion ? 1 : liftSpring.value
        savedProgress = reduceMotion ? savedSpring.target : savedSpring.value
        watchPress = reduceMotion ? pressSpring.target : pressSpring.value
        tiltX = (holdActive ? holdX : offsetX.value) + ambientX
        tiltY = (holdActive ? holdY : offsetY.value) + ambientY
        let nextFace = cos(tiltX + flip * .pi) >= 0 ? 0 : 1
        if nextFace != visibleFace {
            visibleFace = nextFace
            mediumImpact.impactOccurred()
            mediumImpact.prepare()
        }
        clock += delta
        finger = Finger()

        if returningToRest {
            let settled = abs(flipSpring.value) < 0.001
                && abs(liftSpring.value - 1) < 0.001
                && abs(savedSpring.value) < 0.001
                && abs(offsetX.value) < 0.001
                && abs(offsetY.value) < 0.001
            if settled || reduceMotion {
                restartAutoplay(at: timestamp)
            }
            return
        }

        guard autoPlay, !realTouchDown, let lastInteraction else { return }
        if timestamp - lastInteraction >= TiltPosterParallaxTokens.Timing.interactionResumeDelay {
            returningToRest = true
            flipped = false
            saved = false
            flipSpring.target = 0
            liftSpring.target = 1
            savedSpring.target = 0
            offsetX.target = 0
            offsetY.target = 0
        }
    }

    private func restartAutoplay(at timestamp: CFTimeInterval) {
        paused = false
        returningToRest = false
        lastInteraction = nil
        loopStart = timestamp
        applyRest()
    }

    private func updateAmbient(delta: Double) {
        guard !reduceMotion else {
            ambientX = 0
            ambientY = 0
            return
        }
        let attitude = motion.deviceMotion?.attitude
        let roll = attitude?.roll ?? 0
        let pitch = attitude?.pitch ?? 0
        if !hasSensorBase, roll != 0 || pitch != 0 {
            baseRoll = roll
            basePitch = pitch
            hasSensorBase = true
        }
        let sensorX = clamped((roll - baseRoll) * TiltPosterParallaxTokens.Tilt.sensorGain, limit: TiltPosterParallaxTokens.Tilt.sensorMax)
        let sensorY = clamped(-(pitch - basePitch) * TiltPosterParallaxTokens.Tilt.sensorGain, limit: TiltPosterParallaxTokens.Tilt.sensorMax)
        let phase = clock / TiltPosterParallaxTokens.Tilt.driftPeriod * Double.pi * 2
        let driftX = sin(phase) * TiltPosterParallaxTokens.Tilt.driftAmplitude
        let driftY = sin(phase * 2) * TiltPosterParallaxTokens.Tilt.driftAmplitude * 0.6
        ambientX += (sensorX + driftX - ambientX) * TiltPosterParallaxTokens.Tilt.sensorSmoothing
        ambientY += (sensorY + driftY - ambientY) * TiltPosterParallaxTokens.Tilt.sensorSmoothing
    }

    private func applyAutoplay(phase: Double) {
        clock = phase
        let ambient = reduceMotion ? (x: 0.0, y: 0.0) : autoplayDrift(at: phase)
        let offset = autoplayOffset(at: phase)
        tiltX = offset.x + ambient.x
        tiltY = offset.y + ambient.y
        flip = autoplayFlip(at: phase)
        lift = reduceMotion ? 1 : autoplayLift(at: phase)
        let savedState = autoplaySaved(at: phase)
        saved = phase >= TiltPosterParallaxTokens.Autoplay.taps[2].at
            && phase < TiltPosterParallaxTokens.Autoplay.taps[3].at
        savedProgress = savedState.value
        watchPress = 1
        finger = autoplayFinger(at: phase)
        // Scripted card releases have fixed destinations, independent of which frame crosses them.
        flipped = autoplayFlipTarget(at: phase) ?? (flip >= 0.5)
    }

    private func applyRest() {
        tiltX = 0
        tiltY = 0
        lift = 1
        flip = 0
        clock = 0
        finger = Finger()
        saved = false
        savedProgress = 0
        watchPress = 1
        flipped = false
        visibleFace = 0
        ambientX = 0
        ambientY = 0
        offsetX = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.release)
        offsetY = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.release)
        flipSpring = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.flip)
        liftSpring = SpringChannel(value: 1, target: 1, parameters: TiltPosterParallaxTokens.Springs.lift)
        savedSpring = SpringChannel(value: 0, target: 0, parameters: TiltPosterParallaxTokens.Springs.morph)
        pressSpring = SpringChannel(value: 1, target: 1, parameters: TiltPosterParallaxTokens.Springs.press)
    }

    private func autoplayDrift(at phase: Double) -> (x: Double, y: Double) {
        let angle = phase / TiltPosterParallaxTokens.Tilt.driftPeriod * Double.pi * 2
        return (
            sin(angle) * TiltPosterParallaxTokens.Tilt.driftAmplitude,
            sin(angle * 2) * TiltPosterParallaxTokens.Tilt.driftAmplitude * 0.6
        )
    }

    private func autoplayPath(at time: Double) -> CGPoint {
        let span = TiltPosterParallaxTokens.Autoplay.holdEnd - TiltPosterParallaxTokens.Autoplay.holdStart
        let unit = clamped((time - TiltPosterParallaxTokens.Autoplay.holdStart) / span)
        let ramp = clamped((time - TiltPosterParallaxTokens.Autoplay.holdStart) / TiltPosterParallaxTokens.Autoplay.ramp)
        let eased = ramp * ramp * (3 - 2 * ramp)
        let angle = Double.pi * 2 * TiltPosterParallaxTokens.Autoplay.pathCycles * unit
        return CGPoint(
            x: cardSize.width / 2 * CGFloat(TiltPosterParallaxTokens.Autoplay.amplitudeX * sin(angle) * eased),
            y: cardSize.height / 2 * CGFloat(TiltPosterParallaxTokens.Autoplay.amplitudeY * sin(angle * 2) * eased)
        )
    }

    private func autoplayOffset(at phase: Double) -> (x: Double, y: Double) {
        let start = TiltPosterParallaxTokens.Autoplay.holdStart
        let end = TiltPosterParallaxTokens.Autoplay.holdEnd
        if phase < start { return (0, 0) }
        if phase < end {
            let point = autoplayPath(at: phase)
            return fingerTilt(dx: point.x, dy: point.y)
        }

        let point = autoplayPath(at: end)
        let before = autoplayPath(at: end - TiltPosterParallaxTokens.Autoplay.releaseVelocitySample)
        let held = fingerTilt(dx: point.x, dy: point.y)
        let earlier = fingerTilt(dx: before.x, dy: before.y)
        let velocityX = (held.x - earlier.x) / TiltPosterParallaxTokens.Autoplay.releaseVelocitySample
        let velocityY = (held.y - earlier.y) / TiltPosterParallaxTokens.Autoplay.releaseVelocitySample
        return (
            tappedReleaseState(phase: phase, initial: SpringState(value: held.x, velocity: velocityX)).value,
            tappedReleaseState(phase: phase, initial: SpringState(value: held.y, velocity: velocityY)).value
        )
    }

    private func tappedReleaseState(phase: Double, initial: SpringState) -> SpringState {
        let release = TiltPosterParallaxTokens.Springs.release
        let firstTap = TiltPosterParallaxTokens.Autoplay.taps[0]
        let secondTap = TiltPosterParallaxTokens.Autoplay.taps[1]
        let firstUp = firstTap.at + TiltPosterParallaxTokens.Autoplay.press
        let secondUp = secondTap.at + TiltPosterParallaxTokens.Autoplay.press
        if phase < firstTap.at {
            return springState(initial: initial, target: 0, elapsed: phase - TiltPosterParallaxTokens.Autoplay.holdEnd, spring: release)
        }
        let atFirstTap = springState(initial: initial, target: 0, elapsed: firstTap.at - TiltPosterParallaxTokens.Autoplay.holdEnd, spring: release)
        if phase < firstUp { return SpringState(value: atFirstTap.value, velocity: 0) }
        let afterFirst = SpringState(value: atFirstTap.value, velocity: 0)
        if phase < secondTap.at {
            return springState(initial: afterFirst, target: 0, elapsed: phase - firstUp, spring: release)
        }
        let atSecondTap = springState(initial: afterFirst, target: 0, elapsed: secondTap.at - firstUp, spring: release)
        if phase < secondUp { return SpringState(value: atSecondTap.value, velocity: 0) }
        return springState(initial: SpringState(value: atSecondTap.value, velocity: 0), target: 0, elapsed: phase - secondUp, spring: release)
    }

    private func autoplayFlip(at phase: Double) -> Double {
        let first = TiltPosterParallaxTokens.Autoplay.taps[0].at + TiltPosterParallaxTokens.Autoplay.press
        let second = TiltPosterParallaxTokens.Autoplay.taps[1].at + TiltPosterParallaxTokens.Autoplay.press
        guard phase >= first else { return 0 }
        if reduceMotion {
            if phase < second { return clamped((phase - first) / TiltPosterParallaxTokens.Timing.reduced) }
            return 1 - clamped((phase - second) / TiltPosterParallaxTokens.Timing.reduced)
        }
        // RN commits each flip on release, 110 ms after the scripted press.
        return autoplaySpring(
            at: phase,
            initial: 0,
            keyframes: [(first, 1), (second, 0)],
            spring: TiltPosterParallaxTokens.Springs.flip
        )
    }

    private func autoplayFlipTarget(at phase: Double) -> Bool? {
        let script = TiltPosterParallaxTokens.Autoplay.self
        let firstRelease = script.taps[0].at + script.press
        let secondRelease = script.taps[1].at + script.press
        guard phase >= firstRelease else { return nil }
        return phase < secondRelease
    }

    private func autoplayLift(at phase: Double) -> Double {
        let script = TiltPosterParallaxTokens.Autoplay.self
        let raised = TiltPosterParallaxTokens.Tilt.lift
        // Match holdBegin / holdEnd / setFlip, including presses while already raised.
        return autoplaySpring(
            at: phase,
            initial: 1,
            keyframes: [
                (script.holdStart, raised),
                (script.holdEnd, 1),
                (script.taps[0].at, raised),
                (script.taps[0].at + script.press, raised),
                (script.taps[1].at, raised),
                (script.taps[1].at + script.press, 1),
            ],
            spring: TiltPosterParallaxTokens.Springs.lift
        )
    }

    private func autoplaySpring(
        at phase: Double,
        initial: Double,
        keyframes: [(at: Double, target: Double)],
        spring: TiltPosterParallaxTokens.Spring
    ) -> Double {
        var state = SpringState(value: initial, velocity: 0)
        var target = initial
        var startedAt = 0.0
        for keyframe in keyframes {
            guard phase >= keyframe.at else { break }
            // Reanimated continues an in-flight spring when its target is unchanged.
            guard keyframe.target != target else { continue }
            state = settledAutoplaySpring(initial: state, target: target, elapsed: keyframe.at - startedAt, spring: spring)
            target = keyframe.target
            // withSpring preserves momentum only when it points toward the new target.
            if (target - state.value) * state.velocity < 0 {
                state.velocity = 0
            }
            startedAt = keyframe.at
        }
        return settledAutoplaySpring(initial: state, target: target, elapsed: phase - startedAt, spring: spring).value
    }

    private func settledAutoplaySpring(
        initial: SpringState,
        target: Double,
        elapsed: Double,
        spring: TiltPosterParallaxTokens.Spring
    ) -> SpringState {
        let state = springState(initial: initial, target: target, elapsed: elapsed, spring: spring)
        let initialEnergy = spring.stiffness * pow(initial.value - target, 2) + spring.mass * pow(initial.velocity, 2)
        let energy = spring.stiffness * pow(state.value - target, 2) + spring.mass * pow(state.velocity, 2)
        // Reanimated's default relative energy threshold snaps settled springs to rest.
        if initialEnergy == 0 || energy / initialEnergy <= 6e-9 {
            return SpringState(value: target, velocity: 0)
        }
        return state
    }

    private func autoplaySaved(at phase: Double) -> SpringState {
        let on = TiltPosterParallaxTokens.Autoplay.taps[2].at
        let off = TiltPosterParallaxTokens.Autoplay.taps[3].at
        let initial = SpringState(value: 0, velocity: 0)
        guard phase >= on else { return initial }
        if reduceMotion {
            return SpringState(value: phase < off ? 1 : 0, velocity: 0)
        }
        let spring = TiltPosterParallaxTokens.Springs.morph
        if phase < off {
            return springState(initial: initial, target: 1, elapsed: phase - on, spring: spring)
        }
        let reversed = springState(initial: initial, target: 1, elapsed: off - on, spring: spring)
        return springState(initial: reversed, target: 0, elapsed: phase - off, spring: spring)
    }

    private func autoplayFinger(at phase: Double) -> Finger {
        let holdStart = TiltPosterParallaxTokens.Autoplay.holdStart
        let holdEnd = TiltPosterParallaxTokens.Autoplay.holdEnd
        let centre = CGPoint(x: cardSize.width / 2, y: cardSize.height / 2)
        if phase >= holdStart, phase < holdEnd {
            let path = autoplayPath(at: phase)
            return Finger(
                x: centre.x + path.x,
                y: centre.y + path.y,
                alpha: clamped((phase - holdStart) / TiltPosterParallaxTokens.Autoplay.fingerFade)
            )
        }
        if phase >= holdEnd, phase < holdEnd + TiltPosterParallaxTokens.Autoplay.fingerFade {
            let path = autoplayPath(at: holdEnd)
            return Finger(
                x: centre.x + path.x,
                y: centre.y + path.y,
                alpha: 1 - clamped((phase - holdEnd) / TiltPosterParallaxTokens.Autoplay.fingerFade)
            )
        }
        for tap in TiltPosterParallaxTokens.Autoplay.taps {
            let from = tap.at - TiltPosterParallaxTokens.Autoplay.fingerLead
            let to = tap.at + TiltPosterParallaxTokens.Autoplay.press + TiltPosterParallaxTokens.Autoplay.fingerLead
            guard phase >= from, phase < to else { continue }
            let point = tap.target == .card ? centre : watchPoint
            return Finger(
                x: point.x,
                y: point.y,
                alpha: min(
                    1,
                    min(
                        (phase - from) / TiltPosterParallaxTokens.Autoplay.fingerFade,
                        (to - phase) / TiltPosterParallaxTokens.Autoplay.fingerFade
                    )
                )
            )
        }
        return Finger()
    }

    private func fingerTilt(dx: CGFloat, dy: CGFloat) -> (x: Double, y: Double) {
        let limit = reduceMotion ? TiltPosterParallaxTokens.Tilt.reducedMax : TiltPosterParallaxTokens.Tilt.max
        let rubber = reduceMotion ? 0 : TiltPosterParallaxTokens.Tilt.rubber
        return (
            soften(Double(dx / max(cardSize.width / 2, 1)) * TiltPosterParallaxTokens.Tilt.perHalfCard, maximum: limit, rubber: rubber),
            soften(Double(dy / max(cardSize.height / 2, 1)) * TiltPosterParallaxTokens.Tilt.perHalfCard, maximum: limit, rubber: rubber)
        )
    }

    private func soften(_ value: Double, maximum: Double, rubber: Double) -> Double {
        let magnitude = abs(value)
        guard magnitude > maximum else { return value }
        return value.sign == .minus
            ? -(maximum + (magnitude - maximum) * rubber)
            : maximum + (magnitude - maximum) * rubber
    }

    private func step(_ channel: inout SpringChannel, delta: Double) {
        guard delta > 0 else { return }
        if reduceMotion {
            channel.value = channel.target
            channel.velocity = 0
            return
        }
        let count = max(1, Int(ceil(delta / (1 / 120))))
        let step = delta / Double(count)
        for _ in 0..<count {
            let acceleration = (
                -channel.parameters.stiffness * (channel.value - channel.target)
                - channel.parameters.damping * channel.velocity
            ) / channel.parameters.mass
            channel.velocity += acceleration * step
            channel.value += channel.velocity * step
        }
    }

    private func springState(initial: SpringState, target: Double, elapsed: Double, spring: TiltPosterParallaxTokens.Spring) -> SpringState {
        guard elapsed > 0 else { return initial }
        let omega = sqrt(spring.stiffness / spring.mass)
        let ratio = spring.damping / (2 * sqrt(spring.stiffness * spring.mass))
        let displacement = initial.value - target
        if ratio < 1 {
            let damped = omega * sqrt(1 - ratio * ratio)
            let decay = exp(-ratio * omega * elapsed)
            let a = displacement
            let b = (initial.velocity + ratio * omega * displacement) / damped
            let cosine = cos(damped * elapsed)
            let sine = sin(damped * elapsed)
            let position = decay * (a * cosine + b * sine)
            let velocity = decay * (
                -ratio * omega * (a * cosine + b * sine)
                + (-a * damped * sine + b * damped * cosine)
            )
            return SpringState(value: target + position, velocity: velocity)
        }
        let decay = exp(-omega * elapsed)
        let b = initial.velocity + omega * displacement
        return SpringState(
            value: target + (displacement + b * elapsed) * decay,
            velocity: (b - omega * (displacement + b * elapsed)) * decay
        )
    }

    private func clamped(_ value: Double, limit: Double) -> Double {
        min(limit, max(-limit, value))
    }

    private func clamped(_ value: Double) -> Double {
        min(1, max(0, value))
    }
}
