import CoreGraphics
import Observation
import QuartzCore
import UIKit

struct PullCordThemeSnapshot {
    let geometry: PullCordThemeSimulation.Geometry
    let chain: [CGPoint]
    let foot: CGPoint
    let pull: CGPoint
    let angle: Double
    let heat: Double
    let front: Double
    let adaptation: Double
    let flicker: Double
    let clock: Double
    let light: Double
    let reduced: Bool
    let range: PullCordThemeLight.Range
}

@MainActor
@Observable
final class PullCordThemeController {
    private(set) var snapshot: PullCordThemeSnapshot
    private(set) var pageMasks: PullCordThemePageMasks?

    @ObservationIgnored private var state: PullCordThemeSimulation.State
    @ObservationIgnored private var geometry: PullCordThemeSimulation.Geometry
    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored private lazy var displayLink = PullCordThemeDisplayLink { [weak self] timestamp in
        self?.frame(timestamp)
    }
    @ObservationIgnored private let feedback = UIImpactFeedbackGenerator(style: .rigid)

    init(autoPlay: Bool) {
        let geometry = PullCordThemeSimulation.makeGeometry(
            width: PullCordThemeTokens.stageWidth,
            height: 852
        )
        self.geometry = geometry
        self.autoPlay = autoPlay
        state = PullCordThemeSimulation.State(geometry: geometry, reduced: false, autoPlay: autoPlay)
        snapshot = Self.makeSnapshot(state: state, geometry: geometry)
    }

    func configure(size: CGSize, autoPlay: Bool, reducedMotion: Bool, displayScale: CGFloat) {
        guard size.width > 0, size.height > 0 else { return }
        let changedSize = abs(geometry.width - size.width) > 0.25 || abs(geometry.height - size.height) > 0.25
        let changedMode = state.reduced != reducedMotion
        self.autoPlay = autoPlay && !reducedMotion
        if changedSize || changedMode {
            geometry = PullCordThemeSimulation.makeGeometry(width: size.width, height: size.height)
            state = PullCordThemeSimulation.State(
                geometry: geometry,
                reduced: reducedMotion,
                autoPlay: self.autoPlay
            )
        } else if !self.autoPlay {
            state.demoOn = false
        }
        if changedSize || pageMasks == nil {
            pageMasks = PullCordThemePageMasks.make(size: size, scale: displayScale)
        }
        publish()
        updateClock()
    }

    func start() {
        previousTimestamp = nil
        feedback.prepare()
        updateClock()
    }

    func stop() {
        displayLink.stop()
        previousTimestamp = nil
    }

    func pointerDown(at point: CGPoint) {
        send(.down(x: point.x, y: point.y))
    }

    func pointerMoved(to point: CGPoint, velocity: CGVector) {
        send(.move(
            x: point.x,
            y: point.y,
            velocityX: velocity.dx,
            velocityY: velocity.dy
        ))
    }

    func pointerEnded(velocity: CGVector, cancelled: Bool) {
        send(.up(velocityX: velocity.dx, velocityY: velocity.dy, cancelled: cancelled))
    }

    func tap(at point: CGPoint) {
        send(.tap(x: point.x, y: point.y))
    }

    func toggle() {
        send(.toggle)
    }

    private func send(_ input: PullCordThemeSimulation.Input) {
        PullCordThemeSimulation.input(state, geometry, input)
        playEvents()
        publish()
        updateClock()
    }

    private func frame(_ timestamp: CFTimeInterval) {
        guard let previousTimestamp else {
            self.previousTimestamp = timestamp
            return
        }
        self.previousTimestamp = timestamp
        PullCordThemeSimulation.step(
            state,
            geometry,
            frameDelta: max(0, timestamp - previousTimestamp),
            autoPlay: autoPlay
        )
        playEvents()
        publish()
        updateClock()
    }

    private func playEvents() {
        guard !state.events.isEmpty else { return }
        state.events.removeAll(keepingCapacity: true)
        feedback.impactOccurred(intensity: 0.82)
        feedback.prepare()
    }

    private func publish() {
        snapshot = Self.makeSnapshot(state: state, geometry: geometry)
    }

    private var needsClock: Bool {
        if !state.reduced { return true }
        return state.grab != 0
            || state.tapOn
            || abs(state.travel) > 0.001
            || abs(state.travelVelocity) > 0.001
            || abs(state.heat - state.light) > 0.001
            || abs(state.adaptation - state.light) > 0.001
            || state.frontTime < state.frontDuration
    }

    private func updateClock() {
        if needsClock {
            displayLink.start()
        } else {
            displayLink.stop()
            previousTimestamp = nil
        }
    }

    private static func makeSnapshot(
        state: PullCordThemeSimulation.State,
        geometry: PullCordThemeSimulation.Geometry
    ) -> PullCordThemeSnapshot {
        PullCordThemeSnapshot(
            geometry: geometry,
            chain: zip(state.x, state.y).map { CGPoint(x: $0.0, y: $0.1) },
            foot: PullCordThemeSimulation.switchFoot(geometry, angle: state.angle),
            pull: PullCordThemeSimulation.pullCentre(state, geometry),
            angle: state.angle,
            heat: state.heat,
            front: state.front,
            adaptation: state.adaptation,
            flicker: PullCordThemeSimulation.flicker(state),
            clock: state.clock,
            light: state.light,
            reduced: state.reduced,
            range: PullCordThemeLight.fieldRange(geometry)
        )
    }
}
