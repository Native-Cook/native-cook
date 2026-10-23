import CoreGraphics
import Foundation
import Observation

/// Fixed-step simulation ported from `call-sim.ts`.
///
/// The observable boundary publishes one value snapshot per display frame. The
/// integrator itself stays private and deterministic, and can be advanced in a
/// headless validation without UIKit or a SwiftUI render tree.
@MainActor
@Observable
final class SlideToAnswerCordSimulation {
    enum Phase: Int, Equatable, Sendable {
        case ringing = 0
        case connected = 1
        case declined = 2
        case ended = 3
    }

    enum Command: Equatable, Sendable {
        case answer
        case decline
        case end
    }

    enum HapticEvent: Int, Equatable, Sendable {
        case grab = 1
        case answer = 2
        case decline = 3
        case tick = 4
    }

    struct Geometry: Equatable, Sendable {
        let width: Double
        let height: Double
        let scale: Double
        let anchorX: Double
        let anchorY: Double
        let restY: Double
        let baseLength: Double

        static let empty = Self(
            width: SlideToAnswerCordTheme.stageWidth,
            height: 874,
            scale: 1,
            anchorX: SlideToAnswerCordTheme.stageWidth / 2,
            anchorY: SlideToAnswerCordTheme.Cord.anchorY,
            restY: 874 * SlideToAnswerCordTheme.Cord.restRatio,
            baseLength: 874 * SlideToAnswerCordTheme.Cord.restRatio
                - SlideToAnswerCordTheme.Cord.anchorY
                - SlideToAnswerCordMotion.Chain.hangStretch
        )
    }

    struct Wash: Equatable, Sendable {
        let center: CGPoint
        let progress: Double
    }

    struct Pulse: Equatable, Sendable {
        let center: CGPoint
        let radius: Double
        let opacity: Double

        static let hidden = Self(center: .zero, radius: 0, opacity: 0)
    }

    /// The renderer's narrow, immutable boundary with the simulation.
    struct Frame: Equatable, Sendable {
        let geometry: Geometry
        let chain: [CGPoint]
        let handsetBaseAngle: Double
        let handsetAngle: Double
        let handsetCenter: CGPoint
        let phase: Phase
        let phaseElapsed: Double
        let callElapsed: Double
        let connectedMix: Double
        let offMix: Double
        let morph: Double
        let wash: Wash?
        let chevronProgress: Double?
        let pulse: Pulse
        let acceptGlow: Double
        let declineGlow: Double
        let glowOn: Double
        let isGrabbed: Bool
        let armedDirection: Int
        let clock: Double
        let swingTime: Double
        let sourceWeight: Double
        let reducedMotion: Bool

        static let empty = Self(
            geometry: .empty,
            chain: [],
            handsetBaseAngle: SlideToAnswerCordTheme.Handset.restTilt,
            handsetAngle: SlideToAnswerCordTheme.Handset.restTilt,
            handsetCenter: .zero,
            phase: .ringing,
            phaseElapsed: 0,
            callElapsed: 0,
            connectedMix: 0,
            offMix: 0,
            morph: 0,
            wash: nil,
            chevronProgress: nil,
            pulse: .hidden,
            acceptGlow: 0,
            declineGlow: 0,
            glowOn: 1,
            isGrabbed: false,
            armedDirection: 0,
            clock: 0,
            swingTime: 0,
            sourceWeight: 1,
            reducedMotion: false
        )
    }

    private struct State: Equatable, Sendable {
        var accumulator = 0.0
        var clock = 0.0
        var positionX: [Double]
        var positionY: [Double]
        var velocityX: [Double]
        var velocityY: [Double]
        var angle = 0.0
        var angularVelocity = 0.0
        var horizontalDelta = 0.0
        var naturalLength: Double
        var naturalTarget: Double
        var phase = Phase.ringing
        var phaseElapsed = 0.0
        var callElapsed = 0.0
        var connectedMix = 0.0
        var offMix = 0.0
        var isGrabbed = false
        var isScripted = false
        var grabLocalX = 0.0
        var grabLocalY = 0.0
        var downX = 0.0
        var downY = 0.0
        var fingerX = 0.0
        var fingerY = 0.0
        var fingerVelocityX = 0.0
        var fingerVelocityY = 0.0
        var armedDirection = 0
        var tickIndex = 0
        var isDemoRunning: Bool
        var demoTime = 0.0
        var quietTime = 0.0
        var demoX = 0.0
        var demoY = 0.0
        var reducedMotion: Bool
        var events: [HapticEvent] = []
        var sourceWeight: Double
        var look: Double
        var isReturningToRest = false
        var returnElapsed = 0.0
        var returnPositionX: [Double] = []
        var returnPositionY: [Double] = []
        var returnVelocityX: [Double] = []
        var returnVelocityY: [Double] = []
        var returnAngle = 0.0
        var returnAngularVelocity = 0.0
        var returnNaturalLength = 0.0
        var returnConnectedMix = 0.0
        var returnOffMix = 0.0
        var returnSourceWeight = 0.0
        var returnLook = 0.0
    }

    @ObservationIgnored private let autoPlay: Bool
    @ObservationIgnored private var geometry: Geometry?
    @ObservationIgnored private var state: State?
    @ObservationIgnored private var loopSeam: State?
    @ObservationIgnored private var previousTimestamp: TimeInterval?
    @ObservationIgnored private var isRunning = false

    private(set) var frame: Frame = .empty

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
    }

    func configure(size: CGSize, reducedMotion: Bool) {
        guard size.width > 0, size.height > 0 else { return }
        let nextGeometry = Self.makeGeometry(width: size.width, height: size.height)
        guard geometry != nextGeometry || state?.reducedMotion != reducedMotion else { return }

        var nextState = Self.initialState(
            geometry: nextGeometry,
            reducedMotion: reducedMotion,
            autoPlay: autoPlay && !reducedMotion
        )

        if reducedMotion {
            // Reduced Motion stops autoplay and large travel, but still settles
            // the cord under gravity into a stable, legible resting pose.
            let steps = Self.prerollStepCount
            for _ in 0..<steps {
                Self.fixedStep(
                    state: &nextState,
                    geometry: nextGeometry,
                    autoPlay: false,
                    loopSeam: nil
                )
            }
            nextState.isDemoRunning = false
        } else if autoPlay {
            Self.preroll(state: &nextState, geometry: nextGeometry)
        }

        // The RN pre-roll ends at the loop boundary with phaseElapsed ~= 1.6
        // and clock ~= 36. Preserve those values because they drive the ring;
        // normalize only script time so live playback starts at loop time zero.
        nextState.demoTime = 0
        nextState.events.removeAll(keepingCapacity: true)
        geometry = nextGeometry
        state = nextState
        loopSeam = nextState
        previousTimestamp = nil
        publish()
    }

    func start() {
        isRunning = true
        previousTimestamp = nil
    }

    func stop() {
        isRunning = false
        previousTimestamp = nil
    }

    /// Called by the UIKit display-link bridge. The fixed integrator stays at
    /// 120 Hz regardless of the device's presentation refresh rate.
    func advance(to timestamp: TimeInterval) {
        guard isRunning, let geometry, var state else { return }
        defer { previousTimestamp = timestamp }
        guard let previousTimestamp else {
            publish()
            return
        }

        let frameDuration = min(
            SlideToAnswerCordMotion.maximumFrameDelta,
            max(0, timestamp - previousTimestamp)
        )
        Self.step(
            state: &state,
            geometry: geometry,
            frameDuration: frameDuration,
            autoPlay: autoPlay && !state.reducedMotion,
            loopSeam: loopSeam
        )
        self.state = state
        publish()
    }

    /// Advances only semantic timers while Reduce Motion has paused the
    /// display link. No spring, autoplay script or large spatial travel runs.
    func advanceReducedClock(by duration: TimeInterval) {
        guard isRunning, duration > 0, let geometry, var state, state.reducedMotion else { return }
        Self.advanceReducedTimers(state: &state, geometry: geometry, duration: duration)
        self.state = state
        publish()
    }

    @discardableResult
    func touchBegan(at point: CGPoint) -> Bool {
        guard let geometry, var state,
              !state.reducedMotion,
              Self.hitsHandset(point: point, state: state, geometry: geometry)
        else { return false }

        Self.input(
            state: &state,
            geometry: geometry,
            input: .down(x: point.x, y: point.y, scripted: false)
        )
        self.state = state
        publish()
        return true
    }

    func touchMoved(to point: CGPoint, velocity: CGVector) {
        guard let geometry, var state else { return }
        Self.input(
            state: &state,
            geometry: geometry,
            input: .move(
                x: point.x,
                y: point.y,
                velocityX: velocity.dx,
                velocityY: velocity.dy
            )
        )
        self.state = state
    }

    func touchEnded(velocity: CGVector, cancelled: Bool) {
        guard let geometry, var state else { return }
        Self.input(
            state: &state,
            geometry: geometry,
            input: .up(
                velocityX: cancelled ? 0 : velocity.dx,
                velocityY: cancelled ? 0 : velocity.dy
            )
        )
        self.state = state
        publish()
    }

    func command(_ command: Command) {
        guard let geometry, var state else { return }
        Self.input(state: &state, geometry: geometry, input: .command(command))
        self.state = state
        publish()
    }

    func hitTestHandset(_ point: CGPoint) -> Bool {
        guard let geometry, let state else { return false }
        return Self.hitsHandset(point: point, state: state, geometry: geometry)
    }

    func handsetPoint(localX: Double, localY: Double, extraAngle: Double = 0) -> CGPoint {
        guard let geometry, let state else { return .zero }
        return Self.handsetPoint(
            state: state,
            geometry: geometry,
            x: localX,
            y: localY,
            extraAngle: extraAngle
        )
    }

    func drainHapticEvents() -> [HapticEvent] {
        guard var state else { return [] }
        let events = state.events
        state.events.removeAll(keepingCapacity: true)
        self.state = state
        return events
    }

    private func publish() {
        guard let geometry, let state else { return }
        frame = Self.makeFrame(state: state, geometry: geometry)
    }
}

private extension SlideToAnswerCordSimulation {
    enum Input {
        case down(x: Double, y: Double, scripted: Bool)
        case move(x: Double, y: Double, velocityX: Double, velocityY: Double)
        case up(velocityX: Double, velocityY: Double)
        case command(Command)
    }

    static var prerollStepCount: Int {
        Int((Double(SlideToAnswerCordMotion.prerollLoops) * SlideToAnswerCordMotion.loopMS
             / 1_000 / SlideToAnswerCordMotion.step).rounded())
    }

    static func makeGeometry(width: Double, height: Double) -> Geometry {
        let scale = width / SlideToAnswerCordTheme.stageWidth
        let anchorY = SlideToAnswerCordTheme.Cord.anchorY * scale
        let restY = height * SlideToAnswerCordTheme.Cord.restRatio
        let baseLength = restY - anchorY - SlideToAnswerCordMotion.Chain.hangStretch * scale
        return Geometry(
            width: width,
            height: height,
            scale: scale,
            anchorX: width / 2 + SlideToAnswerCordTheme.Cord.replicaAnchorDX * scale,
            anchorY: anchorY,
            restY: restY,
            baseLength: baseLength
        )
    }

    private static func initialState(
        geometry: Geometry,
        reducedMotion: Bool,
        autoPlay: Bool
    ) -> State {
        let linkCount = SlideToAnswerCordTheme.Cord.links
        var positionX: [Double] = []
        var positionY: [Double] = []
        positionX.reserveCapacity(linkCount + 1)
        positionY.reserveCapacity(linkCount + 1)
        for index in 0...linkCount {
            positionX.append(geometry.anchorX)
            positionY.append(
                geometry.anchorY
                    + (geometry.restY - geometry.anchorY) * Double(index) / Double(linkCount)
            )
        }
        return State(
            positionX: positionX,
            positionY: positionY,
            velocityX: Array(repeating: 0, count: linkCount + 1),
            velocityY: Array(repeating: 0, count: linkCount + 1),
            naturalLength: geometry.baseLength,
            naturalTarget: geometry.baseLength,
            isDemoRunning: autoPlay,
            reducedMotion: reducedMotion,
            sourceWeight: reducedMotion ? 0 : 1,
            look: reducedMotion ? 0 : 1
        )
    }

    private static func input(state: inout State, geometry: Geometry, input: Input) {
        switch input {
        case let .command(command):
            state.isDemoRunning = false
            state.quietTime = 0
            state.isReturningToRest = false
            state.isScripted = false
            // Buttons and accessibility actions are always authoritative. A
            // command can land during an autoplay hold, so release that
            // scripted grab before leaving the script clock.
            state.isGrabbed = false
            switch command {
            case .answer where state.phase == .ringing:
                setPhase(state: &state, geometry: geometry, phase: .connected)
            case .decline where state.phase == .ringing:
                setPhase(state: &state, geometry: geometry, phase: .declined)
            case .end where state.phase == .connected:
                setPhase(state: &state, geometry: geometry, phase: .ended)
            default:
                break
            }

        case let .down(x, y, scripted):
            state.isScripted = scripted
            if !scripted {
                state.isDemoRunning = false
                state.quietTime = 0
                state.isReturningToRest = false
            }
            let last = SlideToAnswerCordTheme.Cord.links
            let inverseRotation = -handsetRotation(angle: state.angle)
            let deltaX = (x - state.positionX[last]) / geometry.scale
            let deltaY = (y - state.positionY[last]) / geometry.scale
            state.grabLocalX = deltaX * cos(inverseRotation) - deltaY * sin(inverseRotation)
            state.grabLocalY = deltaX * sin(inverseRotation) + deltaY * cos(inverseRotation)
            state.isGrabbed = true
            state.downX = x
            state.downY = y
            state.fingerX = x
            state.fingerY = y
            state.fingerVelocityX = 0
            state.fingerVelocityY = 0
            state.armedDirection = 0
            if !scripted {
                state.events.append(.grab)
            }

        case let .move(x, y, velocityX, velocityY):
            guard state.isGrabbed else { return }
            state.fingerX = x
            state.fingerY = y
            state.fingerVelocityX = velocityX
            state.fingerVelocityY = velocityY

        case let .up(velocityX, velocityY):
            guard state.isGrabbed else { return }
            let wasScripted = state.isScripted
            state.isGrabbed = false
            let last = SlideToAnswerCordTheme.Cord.links
            let speedLimit = SlideToAnswerCordMotion.Drag.releaseSpeed
            state.velocityX[last] = clamp(velocityX, minimum: -speedLimit, maximum: speedLimit)
            state.velocityY[last] = clamp(velocityY, minimum: -speedLimit, maximum: speedLimit)
            let distance = dragDistance(state: state, geometry: geometry)
            let up = distance <= -SlideToAnswerCordMotion.Drag.answer * geometry.scale
                || (velocityY < -SlideToAnswerCordMotion.Flick.speed * geometry.scale
                    && distance < -SlideToAnswerCordMotion.Flick.min * geometry.scale)
            let down = distance >= SlideToAnswerCordMotion.Drag.decline * geometry.scale
                || (velocityY > SlideToAnswerCordMotion.Flick.speed * geometry.scale
                    && distance > SlideToAnswerCordMotion.Flick.min * geometry.scale)
            if state.phase == .ringing, up {
                setPhase(state: &state, geometry: geometry, phase: .connected)
            } else if state.phase == .ringing, down {
                setPhase(state: &state, geometry: geometry, phase: .declined)
            } else if state.phase == .connected, down {
                setPhase(state: &state, geometry: geometry, phase: .ended)
            }
            state.isScripted = false
            if !wasScripted {
                // The two-second interruption window starts at the user's
                // release, even if another command arrived during the drag.
                state.quietTime = 0
            }
        }
    }

    private static func setPhase(state: inout State, geometry: Geometry, phase: Phase) {
        state.phase = phase
        state.phaseElapsed = 0
        switch phase {
        case .connected:
            state.naturalTarget = geometry.baseLength
                - SlideToAnswerCordMotion.State.pickUp * geometry.scale
            state.callElapsed = 0
        case .ringing:
            state.naturalTarget = geometry.baseLength
        case .declined, .ended:
            state.naturalTarget = geometry.baseLength
                + SlideToAnswerCordMotion.State.drop * geometry.scale
        }
        if state.reducedMotion {
            // There is no display-link integration in Reduce Motion. Publish
            // the destination palette and control geometry synchronously.
            state.naturalLength = state.naturalTarget
            state.connectedMix = phase == .connected ? 1 : 0
            state.offMix = phase == .declined || phase == .ended ? 1 : 0
        }
        if !state.isScripted, phase != .ringing {
            state.events.append(phase == .connected ? .answer : .decline)
        }
    }

    private static func advanceReducedTimers(
        state: inout State,
        geometry: Geometry,
        duration: Double
    ) {
        state.phaseElapsed += duration
        if state.phase == .connected {
            state.callElapsed += duration
        }
        if (state.phase == .declined || state.phase == .ended),
           state.phaseElapsed >= SlideToAnswerCordMotion.State.resetAfter {
            setPhase(state: &state, geometry: geometry, phase: .ringing)
        }
    }

    private static func runScript(
        state: inout State,
        geometry: Geometry,
        startTime: Double,
        endTime: Double
    ) {
        guard !SlideToAnswerCordTheme.replica else { return }
        for segment in SlideToAnswerCordMotion.script {
            if segment.downAtStart == 1,
               startTime <= segment.start,
               segment.start < endTime {
                let point = handsetPoint(
                    state: state,
                    geometry: geometry,
                    x: SlideToAnswerCordTheme.Handset.comX,
                    y: SlideToAnswerCordTheme.Handset.comY
                )
                state.demoX = point.x
                state.demoY = point.y
                input(
                    state: &state,
                    geometry: geometry,
                    input: .down(x: point.x, y: point.y, scripted: true)
                )
            }
            if endTime > segment.start,
               startTime < segment.end,
               state.isGrabbed,
               state.isScripted {
                let progress = clamp(
                    (min(endTime, segment.end) - segment.start) / (segment.end - segment.start)
                )
                let eased = (1 - cos(.pi * progress)) / 2
                let x = state.demoX
                    + (segment.fromDX + (segment.toDX - segment.fromDX) * eased) * geometry.scale
                let y = state.demoY
                    + (segment.fromDY + (segment.toDY - segment.fromDY) * eased) * geometry.scale
                input(
                    state: &state,
                    geometry: geometry,
                    input: .move(x: x, y: y, velocityX: 0, velocityY: 0)
                )
            }
            if segment.upAtEnd == 1,
               startTime < segment.end,
               segment.end <= endTime,
               state.isScripted {
                input(
                    state: &state,
                    geometry: geometry,
                    input: .up(velocityX: 0, velocityY: 0)
                )
            }
        }
    }

    private static func fixedStep(
        state: inout State,
        geometry: Geometry,
        autoPlay: Bool,
        loopSeam: State?
    ) {
        let deltaTime = SlideToAnswerCordMotion.step
        let linkCount = SlideToAnswerCordTheme.Cord.links

        if state.isDemoRunning {
            let startTime = state.demoTime
            let endTime = startTime + deltaTime
            runScript(
                state: &state,
                geometry: geometry,
                startTime: startTime,
                endTime: endTime
            )
            if endTime * 1_000 >= SlideToAnswerCordMotion.loopMS - 1e-9 {
                if let loopSeam {
                    let accumulator = state.accumulator
                    state = loopSeam
                    state.accumulator = accumulator
                    return
                }
                // Pre-roll has no saved seam yet, but its script still repeats
                // on the same 12 s clock as the live loop.
                state.demoTime = endTime - SlideToAnswerCordMotion.loopMS / 1_000
            } else {
                state.demoTime = endTime
            }
        } else if autoPlay, !state.isGrabbed {
            state.quietTime += deltaTime
            // The RN fidelity token remains resumeMS = 3_500. The Swift port
            // intentionally follows the owner's two-second interruption rule,
            // then eases to the saved rest seam before restarting autoplay.
            if !state.isReturningToRest,
               state.quietTime >= SlideToAnswerCordMotion.interactiveIdleBeforeReturn {
                beginReturnToRest(state: &state, geometry: geometry)
            }
        }

        if state.isReturningToRest, let loopSeam {
            returnToRestStep(
                state: &state,
                seam: loopSeam,
                autoPlay: autoPlay,
                deltaTime: deltaTime
            )
            return
        }

        state.phaseElapsed += deltaTime
        if state.phase == .connected {
            state.callElapsed += deltaTime
        }
        if (state.phase == .declined || state.phase == .ended),
           state.phaseElapsed >= SlideToAnswerCordMotion.State.resetAfter {
            setPhase(state: &state, geometry: geometry, phase: .ringing)
        }
        state.naturalLength += (state.naturalTarget - state.naturalLength)
            * (1 - exp(-SlideToAnswerCordMotion.State.lengthEase * deltaTime))
        let colorAmount = state.reducedMotion
            ? 1
            : 1 - exp(-SlideToAnswerCordMotion.State.colorEase * deltaTime)
        state.connectedMix += ((state.phase == .connected ? 1.0 : 0.0) - state.connectedMix)
            * colorAmount
        state.offMix += ((state.phase == .declined || state.phase == .ended ? 1.0 : 0.0)
            - state.offMix) * colorAmount

        if state.isGrabbed {
            let distance = dragDistance(state: state, geometry: geometry)
            let wantedDirection = if distance <= -SlideToAnswerCordMotion.Drag.answer * geometry.scale,
                                     state.phase == .ringing {
                -1
            } else if distance >= SlideToAnswerCordMotion.Drag.decline * geometry.scale {
                1
            } else {
                0
            }
            if wantedDirection != state.armedDirection {
                state.armedDirection = wantedDirection
                if wantedDirection != 0, !state.isScripted {
                    state.events.append(.grab)
                }
            }
            let tickIndex = Int(floor(abs(distance) / (SlideToAnswerCordMotion.tick * geometry.scale)))
            if tickIndex > state.tickIndex, !state.isScripted {
                state.events.append(.tick)
            }
            state.tickIndex = tickIndex
        } else {
            state.tickIndex = 0
        }

        let scaledGravity = SlideToAnswerCordMotion.Chain.gravity * geometry.scale
        let linkStiffness = (
            SlideToAnswerCordMotion.Chain.handsetMass * scaledGravity
                / (SlideToAnswerCordMotion.Chain.hangStretch * geometry.scale)
        ) * Double(linkCount)
        let restLength = state.naturalLength / Double(linkCount)
        var forceX = Array(repeating: 0.0, count: linkCount + 1)
        var forceY = Array(repeating: 0.0, count: linkCount + 1)
        for index in 0...linkCount {
            let mass = index == linkCount
                ? SlideToAnswerCordMotion.Chain.handsetMass
                : SlideToAnswerCordMotion.Chain.linkMass
            forceY[index] = scaledGravity * mass
        }
        for index in 0..<linkCount {
            let deltaX = state.positionX[index + 1] - state.positionX[index]
            let deltaY = state.positionY[index + 1] - state.positionY[index]
            let distance = max(hypot(deltaX, deltaY), 1e-6)
            let stretch = distance - restLength
            let force = linkStiffness * (stretch > 0 ? stretch : stretch * 0.25)
            let unitX = deltaX / distance
            let unitY = deltaY / distance
            forceX[index] += force * unitX
            forceY[index] += force * unitY
            forceX[index + 1] -= force * unitX
            forceY[index + 1] -= force * unitY
        }
        if linkCount > 1 {
            for index in 1..<linkCount {
                forceX[index] += SlideToAnswerCordMotion.Chain.straighten
                    * SlideToAnswerCordMotion.Chain.linkMass
                    * ((state.positionX[index - 1] + state.positionX[index + 1]) / 2
                       - state.positionX[index])
                forceY[index] += SlideToAnswerCordMotion.Chain.straighten
                    * SlideToAnswerCordMotion.Chain.linkMass
                    * ((state.positionY[index - 1] + state.positionY[index + 1]) / 2
                       - state.positionY[index])
            }
        }
        if state.phase == .ringing,
           !state.reducedMotion,
           !state.isReturningToRest {
            forceX[linkCount] += SlideToAnswerCordMotion.Chain.idleDrive * geometry.scale
                * sin(2 * .pi * state.clock / SlideToAnswerCordMotion.Chain.drivePeriod)
        }

        let previousEndX = state.positionX[linkCount]
        let previousEndVelocityX = state.velocityX[linkCount]
        for index in 1...linkCount {
            if index == linkCount, state.isGrabbed { continue }
            let mass = index == linkCount
                ? SlideToAnswerCordMotion.Chain.handsetMass
                : SlideToAnswerCordMotion.Chain.linkMass
            let damping = exp(-(
                index == linkCount
                    ? SlideToAnswerCordMotion.Chain.handsetDamping
                    : SlideToAnswerCordMotion.Chain.damping
            ) * deltaTime)
            state.velocityX[index] = (
                state.velocityX[index] + forceX[index] / mass * deltaTime
            ) * damping
            state.velocityY[index] = (
                state.velocityY[index] + forceY[index] / mass * deltaTime
            ) * damping
            state.positionX[index] += state.velocityX[index] * deltaTime
            state.positionY[index] += state.velocityY[index] * deltaTime
        }

        if state.isGrabbed {
            let distance = dragDistance(state: state, geometry: geometry)
            let rotation = handsetRotation(angle: state.angle)
            let offsetX = (
                state.grabLocalX * cos(rotation) - state.grabLocalY * sin(rotation)
            ) * geometry.scale
            let offsetY = (
                state.grabLocalX * sin(rotation) + state.grabLocalY * cos(rotation)
            ) * geometry.scale
            let nextX = state.fingerX - offsetX
            let nextY = state.downY + distance - offsetY
            state.velocityX[linkCount] = (nextX - state.positionX[linkCount]) / deltaTime
            state.velocityY[linkCount] = (nextY - state.positionY[linkCount]) / deltaTime
            state.positionX[linkCount] = nextX
            state.positionY[linkCount] = nextY
        }

        if SlideToAnswerCordTheme.replica, !state.isGrabbed {
            pinToSource(
                state: &state,
                geometry: geometry,
                time: state.clock + deltaTime,
                deltaTime: deltaTime
            )
            state.horizontalDelta = state.positionX[linkCount] - previousEndX
            state.clock += deltaTime
            return
        }

        let lastDeltaX = state.positionX[linkCount] - state.positionX[linkCount - 1]
        let lastDeltaY = state.positionY[linkCount] - state.positionY[linkCount - 1]
        let targetAngle = atan2(lastDeltaX, lastDeltaY)
        let acceleration = (state.velocityX[linkCount] - previousEndVelocityX) / deltaTime
        let centerOfMass = hypot(
            SlideToAnswerCordTheme.Handset.comX,
            SlideToAnswerCordTheme.Handset.comY
        ) * geometry.scale
        state.angularVelocity += (
            -SlideToAnswerCordMotion.Tilt.stiffness * (state.angle - targetAngle)
                - SlideToAnswerCordMotion.Tilt.damping * state.angularVelocity
                - acceleration / centerOfMass * 0.6
        ) * deltaTime
        state.angle += state.angularVelocity * deltaTime
        if !SlideToAnswerCordTheme.replica {
            if state.phase == .ringing, !state.isGrabbed, !state.reducedMotion {
                state.sourceWeight += (1 - state.sourceWeight)
                    * (1 - exp(-deltaTime / SlideToAnswerCordMotion.Chain.toSource))
            } else {
                state.sourceWeight = 0
            }
            if state.sourceWeight > 1e-3 {
                pinToSource(
                    state: &state,
                    geometry: geometry,
                    time: state.clock + deltaTime,
                    deltaTime: deltaTime,
                    weight: state.sourceWeight
                )
            }
            state.look += (state.sourceWeight - state.look)
                * (1 - exp(-deltaTime / SlideToAnswerCordMotion.Chain.lookEase))
        }
        state.horizontalDelta = state.positionX[linkCount] - previousEndX
        state.clock += deltaTime

    }

    private static func beginReturnToRest(state: inout State, geometry: Geometry) {
        state.isReturningToRest = true
        state.returnElapsed = 0
        state.returnPositionX = state.positionX
        state.returnPositionY = state.positionY
        state.returnVelocityX = state.velocityX
        state.returnVelocityY = state.velocityY
        state.returnAngle = state.angle
        state.returnAngularVelocity = state.angularVelocity
        state.returnNaturalLength = state.naturalLength
        state.returnConnectedMix = state.connectedMix
        state.returnOffMix = state.offMix
        state.returnSourceWeight = state.sourceWeight
        state.returnLook = state.look
        state.isScripted = true
        setPhase(state: &state, geometry: geometry, phase: .ringing)
        state.isScripted = false
    }

    private static func returnToRestStep(
        state: inout State,
        seam: State,
        autoPlay: Bool,
        deltaTime: Double
    ) {
        state.returnElapsed += deltaTime
        let duration = SlideToAnswerCordMotion.State.resetAfter / 2
        let elapsed = min(state.returnElapsed, duration)
        let easeRate = SlideToAnswerCordMotion.State.lengthEase
        let amount = (1 - exp(-easeRate * elapsed)) / (1 - exp(-easeRate * duration))
        for index in state.positionX.indices {
            state.positionX[index] = mix(state.returnPositionX[index], seam.positionX[index], amount)
            state.positionY[index] = mix(state.returnPositionY[index], seam.positionY[index], amount)
            state.velocityX[index] = mix(state.returnVelocityX[index], seam.velocityX[index], amount)
            state.velocityY[index] = mix(state.returnVelocityY[index], seam.velocityY[index], amount)
        }
        state.angle = mix(state.returnAngle, seam.angle, amount)
        state.angularVelocity = mix(state.returnAngularVelocity, seam.angularVelocity, amount)
        state.naturalLength = mix(state.returnNaturalLength, seam.naturalLength, amount)
        state.connectedMix = mix(state.returnConnectedMix, seam.connectedMix, amount)
        state.offMix = mix(state.returnOffMix, seam.offMix, amount)
        state.sourceWeight = mix(state.returnSourceWeight, seam.sourceWeight, amount)
        state.look = mix(state.returnLook, seam.look, amount)

        guard state.returnElapsed + 1e-12 >= duration else { return }
        let accumulator = state.accumulator
        let pendingEvents = state.events
        state = seam
        state.accumulator = accumulator
        state.events = pendingEvents
        state.isDemoRunning = autoPlay
    }

    private static func step(
        state: inout State,
        geometry: Geometry,
        frameDuration: Double,
        autoPlay: Bool,
        loopSeam: State?,
        uncapped: Bool = false
    ) {
        state.accumulator = uncapped
            ? state.accumulator + frameDuration
            : min(
                state.accumulator + frameDuration,
                SlideToAnswerCordMotion.step * Double(SlideToAnswerCordMotion.maxSteps)
            )
        while state.accumulator + 1e-12 >= SlideToAnswerCordMotion.step {
            state.accumulator -= SlideToAnswerCordMotion.step
            if abs(state.accumulator) < 1e-12 {
                state.accumulator = 0
            }
            fixedStep(
                state: &state,
                geometry: geometry,
                autoPlay: autoPlay,
                loopSeam: loopSeam
            )
        }
    }

    private static func preroll(state: inout State, geometry: Geometry) {
        for _ in 0..<prerollStepCount {
            fixedStep(state: &state, geometry: geometry, autoPlay: true, loopSeam: nil)
        }
        state.events.removeAll(keepingCapacity: true)
    }

    private static func dragDistance(state: State, geometry: Geometry) -> Double {
        let rawDistance = state.fingerY - state.downY
        let reach = SlideToAnswerCordMotion.Drag.reach * geometry.scale
        let magnitude = abs(rawDistance)
        let adjusted = magnitude > reach
            ? reach + rubber(magnitude - reach, limit: reach * 0.4)
            : magnitude
        return rawDistance.sign == .minus ? -adjusted : adjusted
    }

    static func rubber(_ over: Double, limit: Double) -> Double {
        limit * (1 - exp(-over / limit))
    }

    static func mix(_ start: Double, _ end: Double, _ amount: Double) -> Double {
        start + (end - start) * amount
    }

    static func handsetRotation(angle: Double) -> Double {
        atan2(
            SlideToAnswerCordTheme.Handset.comX,
            SlideToAnswerCordTheme.Handset.comY
        ) - angle + SlideToAnswerCordTheme.Handset.restTilt
    }

    private static func handsetPoint(
        state: State,
        geometry: Geometry,
        x: Double,
        y: Double,
        extraAngle: Double = 0
    ) -> CGPoint {
        let rotation = handsetRotation(angle: state.angle) + extraAngle
        let cosine = cos(rotation)
        let sine = sin(rotation)
        let last = SlideToAnswerCordTheme.Cord.links
        return CGPoint(
            x: state.positionX[last] + (x * cosine - y * sine) * geometry.scale,
            y: state.positionY[last] + (x * sine + y * cosine) * geometry.scale
        )
    }

    private static func hitsHandset(point: CGPoint, state: State, geometry: Geometry) -> Bool {
        let center = handsetPoint(
            state: state,
            geometry: geometry,
            x: SlideToAnswerCordTheme.Handset.comX,
            y: SlideToAnswerCordTheme.Handset.comY
        )
        return hypot(point.x - center.x, point.y - center.y)
            < SlideToAnswerCordTheme.Handset.hit / 2 * geometry.scale
    }

    private static func series(_ coefficients: [Double], time: Double) -> Double {
        let angle = 2 * .pi * time / SlideToAnswerCordMotion.ReplicaSwing.period
        var value = coefficients[0]
        for harmonic in 1...SlideToAnswerCordMotion.ReplicaSwing.harmonics {
            value += coefficients[2 * harmonic - 1] * cos(Double(harmonic) * angle)
                + coefficients[2 * harmonic] * sin(Double(harmonic) * angle)
        }
        return value
    }

    private static func sourceCordX(y: Double, time: Double) -> Double {
        let cord = SlideToAnswerCordMotion.ReplicaCord.self
        let frameCount = SlideToAnswerCordMotion.ReplicaCord.x.count
        let frameCountValue = Double(frameCount)
        let frame = ((time / SlideToAnswerCordMotion.ReplicaCord.frameDuration)
            .truncatingRemainder(dividingBy: frameCountValue) + frameCountValue)
            .truncatingRemainder(dividingBy: frameCountValue)
        let frame0 = Int(floor(frame))
        let frame1 = (frame0 + 1) % frameCount
        let frameProgress = frame - Double(frame0)
        let last = cord.y.count - 1
        let clampedY = min(cord.y[last], max(cord.y[0], y))
        var index = 0
        while index < last - 1, clampedY > cord.y[index + 1] { index += 1 }
        let progress = (clampedY - cord.y[index]) / (cord.y[index + 1] - cord.y[index])
        let first = mix(cord.x[frame0][index], cord.x[frame0][index + 1], progress)
        let second = mix(cord.x[frame1][index], cord.x[frame1][index + 1], progress)
        return mix(first, second, frameProgress)
    }

    private static func pinToSource(
        state: inout State,
        geometry: Geometry,
        time: Double,
        deltaTime: Double,
        weight: Double = 1
    ) {
        let last = SlideToAnswerCordTheme.Cord.links
        let swing = SlideToAnswerCordMotion.ReplicaSwing.self
        let screenScale = geometry.width / swing.sourceWidth
        let leanDegrees = series(swing.lean, time: time)
        let rotation = ((leanDegrees - swing.leanAtZero) / swing.leanPerRadian) * .pi / 180
        let centerX = series(swing.x, time: time) * screenScale
        let centerY = series(swing.y, time: time) * screenScale
        let localX = swing.centroid.x * screenScale - swing.attach.x * geometry.scale
        let localY = swing.centroid.y * screenScale - swing.attach.y * geometry.scale
        let offsetX = localX * cos(rotation) - localY * sin(rotation)
        let offsetY = localX * sin(rotation) + localY * cos(rotation)
        let targetX = centerX - offsetX
        let targetY = centerY - offsetY
        let endX = mix(state.positionX[last], targetX, weight)
        let endY = mix(state.positionY[last], targetY, weight)
        state.velocityX[last] = mix(
            state.velocityX[last],
            (endX - state.positionX[last]) / deltaTime,
            weight
        )
        state.velocityY[last] = mix(
            state.velocityY[last],
            (endY - state.positionY[last]) / deltaTime,
            weight
        )
        state.positionX[last] = endX
        state.positionY[last] = endY
        let targetAngle = atan2(
            SlideToAnswerCordTheme.Handset.comX,
            SlideToAnswerCordTheme.Handset.comY
        ) + SlideToAnswerCordTheme.Handset.restTilt - rotation
        state.angle = mix(state.angle, targetAngle, weight)
        state.angularVelocity *= 1 - weight

        let measured = SlideToAnswerCordMotion.ReplicaCord.self
        let topY = measured.y[0] * screenScale
        let bottomY = measured.y[measured.y.count - 1] * screenScale
        for index in 1..<last {
            let y = geometry.anchorY + (endY - geometry.anchorY) * Double(index) / Double(last)
            let x: Double
            if y < topY {
                let measuredX = sourceCordX(y: measured.y[0], time: time) * screenScale
                x = geometry.anchorX + (measuredX - geometry.anchorX)
                    * (y - geometry.anchorY) / (topY - geometry.anchorY)
            } else if y > bottomY {
                let measuredX = sourceCordX(y: measured.y.last ?? 780, time: time) * screenScale
                x = measuredX + (endX - measuredX) * (y - bottomY) / max(1, endY - bottomY)
            } else {
                x = sourceCordX(y: y / screenScale, time: time) * screenScale
            }
            state.velocityX[index] *= 1 - weight
            state.velocityY[index] *= 1 - weight
            state.positionX[index] = mix(state.positionX[index], x, weight)
            state.positionY[index] = mix(state.positionY[index], y, weight)
        }
    }

    private static func ringState(state: State) -> (buzz: Double, pulse: Double?) {
        guard !SlideToAnswerCordTheme.replica,
              state.phase == .ringing,
              !state.isGrabbed,
              !state.isReturningToRest,
              !state.reducedMotion
        else { return (0, nil) }

        let period = SlideToAnswerCordMotion.Ring.every
        let ringTime = ((state.clock - SlideToAnswerCordMotion.Ring.offset)
            .truncatingRemainder(dividingBy: period) + period)
            .truncatingRemainder(dividingBy: period)
        var buzz = 0.0
        for start in SlideToAnswerCordMotion.Ring.buzz {
            let progress = (ringTime - start) / SlideToAnswerCordMotion.Ring.buzzLength
            if (0...1).contains(progress) {
                buzz += sin(.pi * progress) * SlideToAnswerCordMotion.Ring.buzzAngle
                    * sin(2 * .pi * SlideToAnswerCordMotion.Ring.buzzHz * ringTime)
            }
        }
        let pulse = ringTime < SlideToAnswerCordMotion.Ring.pulse
            ? ringTime / SlideToAnswerCordMotion.Ring.pulse
            : nil
        return (buzz, pulse)
    }

    private static func chevronProgress(state: State) -> Double? {
        guard state.phase == .ringing,
              !state.isGrabbed,
              !state.isReturningToRest,
              !state.reducedMotion
        else { return nil }

        let period = SlideToAnswerCordMotion.Chevron.period
        return state.clock.truncatingRemainder(dividingBy: period) / period
    }

    private static func makeFrame(state: State, geometry: Geometry) -> Frame {
        let ring = ringState(state: state)
        let center = handsetPoint(
            state: state,
            geometry: geometry,
            x: SlideToAnswerCordTheme.Handset.comX,
            y: SlideToAnswerCordTheme.Handset.comY
        )
        let drag = state.isGrabbed ? dragDistance(state: state, geometry: geometry) : 0
        let wash = state.phase != .ringing
            && state.phaseElapsed < SlideToAnswerCordMotion.Wash.duration
            && !state.reducedMotion
            ? Wash(
                center: center,
                progress: state.phaseElapsed / SlideToAnswerCordMotion.Wash.duration
            )
            : nil
        let chevron = chevronProgress(state: state)
        let pulse: Pulse
        if let pulseProgress = ring.pulse {
            pulse = Pulse(
                center: center,
                radius: (
                    SlideToAnswerCordMotion.Ring.pulseFrom
                        + (SlideToAnswerCordMotion.Ring.pulseTo
                           - SlideToAnswerCordMotion.Ring.pulseFrom)
                        * (1 - pow(1 - pulseProgress, 2))
                ) * geometry.scale,
                opacity: 0.55 * (1 - pulseProgress)
            )
        } else {
            pulse = Pulse(center: center, radius: 0, opacity: 0)
        }
        return Frame(
            geometry: geometry,
            chain: zip(state.positionX, state.positionY).map { CGPoint(x: $0, y: $1) },
            handsetBaseAngle: handsetRotation(angle: state.angle),
            handsetAngle: handsetRotation(angle: state.angle) + ring.buzz,
            handsetCenter: center,
            phase: state.phase,
            phaseElapsed: state.phaseElapsed,
            callElapsed: state.callElapsed,
            connectedMix: state.connectedMix,
            offMix: state.offMix,
            morph: state.reducedMotion ? (state.phase == .connected ? 1 : 0) : state.connectedMix,
            wash: wash,
            chevronProgress: chevron,
            pulse: pulse,
            acceptGlow: state.phase == .ringing
                ? clamp(-drag / (SlideToAnswerCordMotion.Drag.answer * geometry.scale))
                : 0,
            declineGlow: clamp(drag / (SlideToAnswerCordMotion.Drag.decline * geometry.scale)),
            glowOn: 1 - 0.8 * state.offMix,
            isGrabbed: state.isGrabbed,
            armedDirection: state.armedDirection,
            clock: state.clock,
            swingTime: state.clock.truncatingRemainder(dividingBy: SlideToAnswerCordMotion.ReplicaSwing.period),
            sourceWeight: SlideToAnswerCordTheme.replica ? 1 : state.look,
            reducedMotion: state.reducedMotion
        )
    }

    static func clamp(_ value: Double, minimum: Double = 0, maximum: Double = 1) -> Double {
        min(maximum, max(minimum, value))
    }
}

#if DEBUG
extension SlideToAnswerCordSimulation {
    /// Pure-kernel regression checks used by the port's headless Swift probe.
    /// An empty result means all deterministic checks passed.
    static func validationFailures() -> [String] {
        let geometry = makeGeometry(width: 402, height: 874)
        var seam = initialState(geometry: geometry, reducedMotion: false, autoPlay: true)
        preroll(state: &seam, geometry: geometry)
        seam.demoTime = 0
        let referenceSeam = seam
        var failures: [String] = []

        func expectNear(_ actual: Double, _ expected: Double, tolerance: Double, _ name: String) {
            if abs(actual - expected) > tolerance {
                failures.append("\(name): \(actual), expected \(expected) ± \(tolerance)")
            }
        }

        expectNear(seam.positionX[14], 175.88789068668444, tolerance: 0.000_001, "pre-roll x")
        expectNear(seam.positionY[14], 312.2658690177039, tolerance: 0.000_001, "pre-roll y")
        expectNear(seam.angle, -0.09413041788146131, tolerance: 0.000_001, "pre-roll angle")
        expectNear(seam.phaseElapsed, 1.6, tolerance: 0.000_001, "pre-roll phase time")

        var scripted = referenceSeam
        step(
            state: &scripted,
            geometry: geometry,
            frameDuration: 3.55,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        expectNear(scripted.positionX[14], 225.68871283744326, tolerance: 0.000_001, "script 3.55 x")
        expectNear(scripted.positionY[14], 242.68408974435505, tolerance: 0.000_001, "script 3.55 y")
        expectNear(scripted.angle, 0.07542699461795571, tolerance: 0.000_001, "script 3.55 angle")
        if !scripted.isGrabbed {
            failures.append("script 3.55 did not hold the handset")
        }
        step(
            state: &scripted,
            geometry: geometry,
            frameDuration: 1.45,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        expectNear(scripted.positionX[14], 194.1642324656003, tolerance: 0.000_001, "script 5.0 x")
        expectNear(scripted.positionY[14], 297.2717454271048, tolerance: 0.000_001, "script 5.0 y")
        if scripted.phase != .connected {
            failures.append("script 5.0 was not connected")
        }
        step(
            state: &scripted,
            geometry: geometry,
            frameDuration: 3.4,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        expectNear(scripted.positionX[14], 208.20158487964915, tolerance: 0.000_001, "script 8.4 x")
        expectNear(scripted.positionY[14], 373.8904023632049, tolerance: 0.000_001, "script 8.4 y")
        if scripted.phase != .ended {
            failures.append("script 8.4 was not ended")
        }

        var adjacentSeam = referenceSeam
        step(
            state: &adjacentSeam,
            geometry: geometry,
            frameDuration: SlideToAnswerCordMotion.loopMS / 1_000
                - SlideToAnswerCordMotion.step,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        if let before = chevronProgress(state: adjacentSeam),
           let after = chevronProgress(state: referenceSeam),
           abs(before - after) > 0.001 {
            failures.append("chevron recording seam jumped \(abs(before - after)) phase")
        }

        step(
            state: &seam,
            geometry: geometry,
            frameDuration: SlideToAnswerCordMotion.loopMS / 1_000,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        if seam != referenceSeam {
            failures.append("loop seam did not restore exactly")
        }

        var distanceCommit = referenceSeam
        let center = handsetPoint(
            state: distanceCommit,
            geometry: geometry,
            x: SlideToAnswerCordTheme.Handset.comX,
            y: SlideToAnswerCordTheme.Handset.comY
        )
        input(state: &distanceCommit, geometry: geometry, input: .down(x: center.x, y: center.y, scripted: false))
        input(
            state: &distanceCommit,
            geometry: geometry,
            input: .move(
                x: center.x,
                y: center.y - SlideToAnswerCordMotion.Drag.answer - 1,
                velocityX: 0,
                velocityY: 0
            )
        )
        input(state: &distanceCommit, geometry: geometry, input: .up(velocityX: 0, velocityY: 0))
        if distanceCommit.phase != .connected {
            failures.append("distance threshold did not answer")
        }

        var flickCommit = referenceSeam
        input(state: &flickCommit, geometry: geometry, input: .down(x: center.x, y: center.y, scripted: false))
        input(
            state: &flickCommit,
            geometry: geometry,
            input: .move(
                x: center.x,
                y: center.y - SlideToAnswerCordMotion.Flick.min - 1,
                velocityX: 0,
                velocityY: -SlideToAnswerCordMotion.Flick.speed - 1
            )
        )
        input(
            state: &flickCommit,
            geometry: geometry,
            input: .up(velocityX: 0, velocityY: -SlideToAnswerCordMotion.Flick.speed - 1)
        )
        if flickCommit.phase != .connected {
            failures.append("velocity threshold did not answer")
        }

        var interrupted = referenceSeam
        input(state: &interrupted, geometry: geometry, input: .command(.answer))
        step(
            state: &interrupted,
            geometry: geometry,
            frameDuration: SlideToAnswerCordMotion.interactiveIdleBeforeReturn
                + SlideToAnswerCordMotion.State.resetAfter / 2
                + SlideToAnswerCordMotion.step,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        if !interrupted.isDemoRunning || interrupted.phase != .ringing {
            failures.append("interactive interruption did not return to autoplay rest")
        }

        var easedReturn = distanceCommit
        beginReturnToRest(state: &easedReturn, geometry: geometry)
        step(
            state: &easedReturn,
            geometry: geometry,
            frameDuration: SlideToAnswerCordMotion.State.resetAfter / 2
                - SlideToAnswerCordMotion.step,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        let beforeFinalReturn = CGPoint(
            x: easedReturn.positionX[14],
            y: easedReturn.positionY[14]
        )
        step(
            state: &easedReturn,
            geometry: geometry,
            frameDuration: SlideToAnswerCordMotion.step,
            autoPlay: true,
            loopSeam: referenceSeam,
            uncapped: true
        )
        let finalReturnDelta = hypot(
            easedReturn.positionX[14] - beforeFinalReturn.x,
            easedReturn.positionY[14] - beforeFinalReturn.y
        )
        if finalReturnDelta > 1 {
            failures.append("return-to-rest final frame jumped \(finalReturnDelta) pt")
        }

        var reduced = initialState(
            geometry: geometry,
            reducedMotion: true,
            autoPlay: false
        )
        let reducedRestX = reduced.positionX
        let reducedRestY = reduced.positionY
        input(state: &reduced, geometry: geometry, input: .command(.answer))
        if reduced.phase != .connected
            || reduced.connectedMix != 1
            || reduced.offMix != 0 {
            failures.append("reduced answer did not snap connected weights")
        }
        advanceReducedTimers(state: &reduced, geometry: geometry, duration: 1)
        if reduced.callElapsed != 1 {
            failures.append("reduced connected timer did not advance")
        }
        input(state: &reduced, geometry: geometry, input: .command(.end))
        if reduced.phase != .ended
            || reduced.connectedMix != 0
            || reduced.offMix != 1 {
            failures.append("reduced end did not snap ended weights")
        }
        advanceReducedTimers(state: &reduced, geometry: geometry, duration: 1)
        if reduced.phase != .ended {
            failures.append("reduced ended state reset before two seconds")
        }
        advanceReducedTimers(state: &reduced, geometry: geometry, duration: 1)
        if reduced.phase != .ringing
            || reduced.connectedMix != 0
            || reduced.offMix != 0 {
            failures.append("reduced ended state did not reset to ringing")
        }
        input(state: &reduced, geometry: geometry, input: .command(.decline))
        if reduced.phase != .declined || reduced.offMix != 1 {
            failures.append("reduced decline did not snap declined weights")
        }
        advanceReducedTimers(
            state: &reduced,
            geometry: geometry,
            duration: SlideToAnswerCordMotion.State.resetAfter
        )
        if reduced.phase != .ringing || reduced.offMix != 0 {
            failures.append("reduced declined state did not reset to ringing")
        }
        if reduced.positionX != reducedRestX || reduced.positionY != reducedRestY {
            failures.append("reduced semantic clock advanced physics")
        }
        return failures
    }
}
#endif
