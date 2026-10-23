import Observation
import QuartzCore
import SwiftUI
import UIKit

struct GumballCrank: View {
    /// LOOP_MS / 1000 = UP_LOOP_MS / 1000; literal required by scripts/catalog.mjs.
    static let loop: Double = 12

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: GumballCrankModel

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = State(initialValue: GumballCrankModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )

            stage(size: size, safeTop: insets.top)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(GumballCrankColor.hex(GumballCrankTheme.replicaArt.wallFill).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of: reduceMotion) { _, reduced in
            model.configure(autoPlay: autoPlay, reduced: reduced)
        }
        .onChange(of: autoPlay) { _, enabled in
            model.configure(autoPlay: enabled, reduced: reduceMotion)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                model.startClockIfNeeded()
            } else {
                model.stopClock()
            }
        }
        .onDisappear { model.stopClock() }
    }

    private func stage(size: CGSize, safeTop: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            GumballCrankScene(
                state: model.state,
                width: size.width,
                height: size.height,
                safeTop: safeTop,
                onTurn: { model.command(.turn) },
                onCollect: { model.command(.collect) }
            )

            GumballCrankTouch(model: model)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear {
            model.layout(width: size.width)
            model.configure(autoPlay: autoPlay, reduced: reduceMotion)
            model.startClockIfNeeded()
        }
        .onChange(of: size) { _, newSize in
            model.layout(width: newSize.width)
        }
    }
}

@MainActor
@Observable
final class GumballCrankModel {
    private(set) var state: GumballCrankState

    @ObservationIgnored private var geometry: GumballCrankGeometry
    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var displayLink: CADisplayLink?
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored private lazy var displayLinkTarget = GumballCrankDisplayLinkTarget(model: self)

    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let rigidFeedback = UIImpactFeedbackGenerator(style: .rigid)
    private let softFeedback = UIImpactFeedbackGenerator(style: .soft)
    private let heavyFeedback = UIImpactFeedbackGenerator(style: .heavy)
    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        state = GumballCrankSimulation.initialState(reduced: false, autoPlay: autoPlay)
        geometry = GumballCrankSimulation.geometry(width: GumballCrankTheme.stageWidth)
    }

    func layout(width: CGFloat) {
        guard width > 0 else { return }
        geometry = GumballCrankSimulation.geometry(width: Double(width))
    }

    func configure(autoPlay: Bool, reduced: Bool) {
        let reducedChanged = state.reduced != reduced
        self.autoPlay = autoPlay
        state.reduced = reduced

        if reduced {
            if state.phase == GumballCrankPhase.ready {
                state = GumballCrankSimulation.initialState(reduced: true, autoPlay: false)
            }
            state.demoOn = false
            state.w = 0
            state.wob = 0
            state.wobV = 0
            state.back = 0
            state.strain = 0
            state.jx = 0
            state.jxv = 0
            state.jy = 0
            state.jyv = 0
            state.px.removeAll(keepingCapacity: true)
            state.py.removeAll(keepingCapacity: true)
            state.pvx.removeAll(keepingCapacity: true)
            state.pvy.removeAll(keepingCapacity: true)
            state.prot.removeAll(keepingCapacity: true)
            state.pcol.removeAll(keepingCapacity: true)
            state.resumeT = 0
        } else if autoPlay, reducedChanged, state.phase == GumballCrankPhase.ready {
            state = GumballCrankSimulation.initialState(reduced: false, autoPlay: true)
        } else if !autoPlay {
            state.demoOn = false
        }

        if needsClock {
            startClockIfNeeded()
        } else {
            stopClock()
        }
    }

    func containsKnob(_ point: CGPoint) -> Bool {
        guard state.phase == GumballCrankPhase.ready else { return false }
        let distance = hypot(Double(point.x) - geometry.knobX, Double(point.y) - geometry.knobY)
        return distance < GumballCrankMotion.knob.grab * geometry.scale
    }

    func pointerDown(at point: CGPoint) {
        send(.down(x: Double(point.x), y: Double(point.y)))
    }

    func pointerMoved(to point: CGPoint) {
        send(.move(x: Double(point.x), y: Double(point.y)))
    }

    func pointerEnded() {
        send(.up)
    }

    func command(_ action: GumballCrankInput.Action) {
        send(.command(action))
    }

    func startClockIfNeeded() {
        guard needsClock, displayLink == nil else { return }
        let link = CADisplayLink(target: displayLinkTarget, selector: #selector(GumballCrankDisplayLinkTarget.frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(
            minimum: GumballCrankMotion.dynamics.preferredMinimumFPS,
            maximum: GumballCrankMotion.dynamics.preferredMaximumFPS,
            preferred: GumballCrankMotion.dynamics.preferredFPS
        )
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stopClock() {
        displayLink?.invalidate()
        displayLink = nil
        previousTimestamp = nil
    }

    fileprivate func frame(at timestamp: CFTimeInterval) {
        guard let previousTimestamp else {
            self.previousTimestamp = timestamp
            return
        }
        self.previousTimestamp = timestamp
        GumballCrankSimulation.step(
            &state,
            geometry: geometry,
            frameDelta: min(
                GumballCrankMotion.dynamics.maximumFrameDelta,
                max(0, timestamp - previousTimestamp)
            ) * GumballCrankMotion.qaSpeed,
            autoPlay: autoPlay
        )
        playPendingFeedback()
        if !needsClock { stopClock() }
    }

    private func send(_ event: GumballCrankInput) {
        GumballCrankSimulation.input(&state, geometry: geometry, event: event)
        playPendingFeedback()
        startClockIfNeeded()
    }

    private var needsClock: Bool {
        if state.grab || state.w > 0 || state.autoTurn >= 0 { return true }
        if state.phase == GumballCrankPhase.dispensing
            || state.phase == GumballCrankPhase.collecting { return true }
        // RN's ring and glass glint continue even without the scripted demonstration.
        if !state.reduced { return true }
        if state.phase == GumballCrankPhase.reward,
           state.phaseT < GumballCrankMotion.reduced.fade { return true }
        let threshold = GumballCrankMotion.dynamics.restingThreshold
        return abs(state.wob) > threshold
            || abs(state.wobV) > threshold
            || abs(state.jx) > threshold
            || abs(state.jxv) > threshold
            || abs(state.jy) > threshold
            || abs(state.jyv) > threshold
            || state.press > threshold
    }

    private func playPendingFeedback() {
        let events = state.events
        state.events.removeAll(keepingCapacity: true)
        guard !state.demoOn else { return }
        for event in events {
            switch event {
            case GumballCrankMotion.haptic.grab:
                selectionFeedback.selectionChanged()
            case GumballCrankMotion.haptic.detent:
                rigidFeedback.impactOccurred()
            case GumballCrankMotion.haptic.ratchet:
                softFeedback.impactOccurred()
            case GumballCrankMotion.haptic.dispense:
                heavyFeedback.impactOccurred()
            case GumballCrankMotion.haptic.open:
                notificationFeedback.notificationOccurred(.success)
            case GumballCrankMotion.haptic.collect:
                lightFeedback.impactOccurred()
            default:
                break
            }
        }
    }
}

@MainActor
private final class GumballCrankDisplayLinkTarget: NSObject {
    weak var model: GumballCrankModel?

    init(model: GumballCrankModel) {
        self.model = model
    }

    @objc func frame(_ link: CADisplayLink) {
        model?.frame(at: link.targetTimestamp)
    }
}
