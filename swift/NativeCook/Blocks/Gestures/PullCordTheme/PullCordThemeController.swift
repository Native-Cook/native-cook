@preconcurrency import CoreMotion
import Observation
import UIKit

/// Owns the UI-thread simulation, display clock, motion sensor, and feedback.
@MainActor
@Observable
final class PullCordThemeController {
    struct Snapshot {
        var renderValues: PullCordThemeRenderValues

        static let empty = Self(renderValues: .empty)
    }

    private(set) var snapshot = Snapshot.empty

    var renderValues: PullCordThemeRenderValues {
        snapshot.renderValues
    }

    @ObservationIgnored private(set) var geometry: PullCordThemeSimulation.Geometry?

    private let autoPlay: Bool
    @ObservationIgnored private var state: PullCordThemeSimulation.State?
    @ObservationIgnored private var reducedMotion = false
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored private var tiltRoll = 0.0

    @ObservationIgnored private lazy var ticker = PullCordThemeDisplayLink { [weak self] timestamp in
        self?.displayLinkDidTick(timestamp: timestamp)
    }
    private let motionManager = CMMotionManager()
    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let detentFeedback = UIImpactFeedbackGenerator(style: .heavy)
    private let homeFeedback = UIImpactFeedbackGenerator(style: .soft)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    func configure(width: Double, height: Double, reducedMotion: Bool) {
        guard width > 0, height > 0 else { return }
        let nextGeometry = PullCordThemeSimulation.makeGeometry(width: width, height: height)
        let geometryChanged = geometry?.width != nextGeometry.width
            || geometry?.height != nextGeometry.height

        self.reducedMotion = reducedMotion
        geometry = nextGeometry

        if geometryChanged || state == nil {
            state = PullCordThemeSimulation.initialState(
                geometry: nextGeometry,
                reducedMotion: reducedMotion,
                autoPlay: autoPlay
            )
        } else {
            state?.reducedMotion = reducedMotion
        }
        publish()
    }

    func start() {
        guard geometry != nil, state != nil else { return }
        previousTimestamp = nil
        prepareFeedback()
        startMotionUpdates()
        ticker.start()
    }

    func stop() {
        ticker.stop()
        previousTimestamp = nil
        motionManager.stopDeviceMotionUpdates()
        tiltRoll = 0
    }

    func touchBegan(at point: CGPoint) {
        send(.down(x: point.x, y: point.y))
    }

    func touchMoved(to point: CGPoint, velocity: CGVector) {
        send(
            .move(
                x: point.x,
                y: point.y,
                velocityX: velocity.dx,
                velocityY: velocity.dy
            )
        )
    }

    func touchEnded(velocity: CGVector, cancelled: Bool) {
        send(
            .up(
                velocityX: velocity.dx,
                velocityY: velocity.dy,
                cancelled: cancelled
            )
        )
    }

    func toggle() {
        send(.toggle)
    }

    private func displayLinkDidTick(timestamp: CFTimeInterval) {
        guard
            let geometry,
            var state
        else { return }

        defer { previousTimestamp = timestamp }
        guard let previousTimestamp else { return }

        if let roll = motionManager.deviceMotion?.attitude.roll {
            tiltRoll = roll
        }

        state.reducedMotion = reducedMotion
        let wallDelta = max(0, timestamp - previousTimestamp) * PullCordThemeMotion.qaSpeed
        PullCordThemeSimulation.step(
            state: &state,
            geometry: geometry,
            frameDelta: min(0.1, wallDelta),
            scriptDelta: wallDelta,
            autoPlay: autoPlay
        )
        self.state = state
        publish()
    }

    private func send(_ input: PullCordThemeSimulation.Input) {
        guard let geometry, var state else { return }
        PullCordThemeSimulation.input(state: &state, geometry: geometry, input: input)
        self.state = state
    }

    private func publish() {
        guard let geometry, var state else { return }
        let pointCount = Int(PullCordThemeTokens.Cord.points)
        var rope: [CGPoint] = []
        rope.reserveCapacity(pointCount)
        for index in 0..<pointCount {
            rope.append(CGPoint(x: state.positionX[index], y: state.positionY[index]))
        }

        let simulatedAngle = PullCordThemeSimulation.bulbAngle(
            state: state,
            geometry: geometry
        )
        let visualAngle = simulatedAngle
            + (state.reducedMotion ? 0 : tiltRoll * PullCordThemeMotion.Hero.tilt)
        let center = PullCordThemeSimulation.glassCenter(state: state, geometry: geometry)
        let reach = PullCordThemeSimulation.revealReach(center: center, geometry: geometry)
        let pull = state.isArmed && !state.reducedMotion
            ? min(
                1,
                state.extensionAmount
                    / (PullCordThemeMotion.Switch.detent * geometry.scale)
            )
            : 0
        let revealFraction = state.light == 0
            ? state.revealValue + PullCordThemeMotion.Feel.preview * pull * pull
            : state.revealValue - PullCordThemeMotion.Feel.preview * pull * pull
        let revealRadius = state.reducedMotion
            ? (state.revealValue > 0 ? reach : 0)
            : max(0, revealFraction) * reach

        let maximumAngle = (
            PullCordThemeTokens.ReplicaArt.swingSrc
                * PullCordThemeTokens.Fit.k
                * geometry.scale
        ) / PullCordThemeSimulation.glassArm(geometry: geometry)
        let side = max(-1, min(1, visualAngle / maximumAngle))
        let bead = rope[pointCount - 1]

        snapshot = Snapshot(
            renderValues: PullCordThemeRenderValues(
                rope: rope,
                bead: bead,
                bulbAngle: visualAngle,
                bulbCenter: CGPoint(x: center.x, y: center.y),
                reveal: revealRadius,
                revealFraction: max(0, min(1, revealFraction)),
                fade: state.reducedMotion
                    ? state.revealValue
                    : (state.revealValue > 0 ? 1 : 0),
                level: state.level,
                neon: PullCordThemeSimulation.ignitionValue(state: state),
                side: side,
                commitAge: state.reducedMotion ? 99 : state.commitTime,
                clock: state.clock,
                isLight: state.light == 1,
                reducedMotion: state.reducedMotion
            )
        )

        playAndClearEvents(state: &state)
        self.state = state
    }

    private func playAndClearEvents(state: inout PullCordThemeSimulation.State) {
        for event in state.events {
            switch event {
            case .grab, .tick:
                selectionFeedback.selectionChanged()
                selectionFeedback.prepare()
            case .detent:
                detentFeedback.impactOccurred()
                detentFeedback.prepare()
            case .home:
                homeFeedback.impactOccurred()
                homeFeedback.prepare()
            }
        }
        state.events.removeAll(keepingCapacity: true)
    }

    private func prepareFeedback() {
        selectionFeedback.prepare()
        detentFeedback.prepare()
        homeFeedback.prepare()
    }

    private func startMotionUpdates() {
        guard motionManager.isDeviceMotionAvailable, !motionManager.isDeviceMotionActive else {
            return
        }
        motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
        motionManager.startDeviceMotionUpdates()
    }
}
