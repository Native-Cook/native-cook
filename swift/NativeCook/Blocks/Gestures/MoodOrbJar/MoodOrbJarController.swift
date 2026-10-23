@preconcurrency import CoreMotion
import Observation
import SwiftUI
import UIKit

/// Owns the simulation, the display clock, the phone's tilt, the haptics and the list of entries
/// (MoodOrbJar.tsx). Each property a view reads changes on its own, so the canvas redraws every frame
/// while the list only redraws when its own values change.
@MainActor
@Observable
final class MoodOrbJarController {
    typealias Tokens = MoodOrbJarTokens
    typealias Motion = MoodOrbJarTokens.Motion

    /// What the canvas draws.
    private(set) var frame = MoodOrbJarFrame.empty
    private(set) var entries: [MoodOrbJarEntry]
    /// Orbs in the pile, rolled in the jar's label.
    private(set) var count = Tokens.pileCount
    /// The newest entry's swipe offset.
    private(set) var rowX = 0.0
    /// The newest card's growth out of its avatar, 0 ... 1.
    private(set) var grow = 1.0
    /// Whether Reduce Motion is on: the list and the count then change without animation.
    @ObservationIgnored private(set) var reducedMotion = false

    @ObservationIgnored private(set) var geometry: MoodOrbJarGeometry?
    @ObservationIgnored private var sim: MoodOrbJarSim?
    @ObservationIgnored private let autoPlay: Bool
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored private var nextEntryID = 1

    @ObservationIgnored private lazy var ticker = MoodOrbJarDisplayLink { [weak self] timestamp in
        self?.tick(timestamp: timestamp)
    }
    private let motionManager = CMMotionManager()
    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)
    private let mediumFeedback = UIImpactFeedbackGenerator(style: .medium)
    private let heavyFeedback = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        entries = Tokens.Copy.entries.enumerated().map { index, entry in
            MoodOrbJarEntry(id: -1 - index, mood: entry.mood, tags: entry.tags, time: entry.time, isFresh: false)
        }
    }

    // MARK: Lifecycle

    func configure(width: Double, height: Double, safeTop: Double, reducedMotion: Bool) {
        guard width > 0, height > 0 else { return }
        let next = MoodOrbJarGeometry(width: width, height: height, safeTop: safeTop)
        self.reducedMotion = reducedMotion
        if next != geometry || sim == nil {
            geometry = next
            sim = MoodOrbJarSim(geometry: next, reduced: reducedMotion, autoPlay: autoPlay)
        } else {
            sim?.reduced = reducedMotion
        }
        publish()
    }

    func start() {
        guard geometry != nil, sim != nil else { return }
        previousTimestamp = nil
        selectionFeedback.prepare()
        lightFeedback.prepare()
        mediumFeedback.prepare()
        heavyFeedback.prepare()
        notificationFeedback.prepare()
        if motionManager.isDeviceMotionAvailable, !motionManager.isDeviceMotionActive {
            motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
            motionManager.startDeviceMotionUpdates()
        }
        ticker.start()
    }

    func stop() {
        ticker.stop()
        previousTimestamp = nil
        motionManager.stopDeviceMotionUpdates()
    }

    // MARK: Input

    func hits(at point: CGPoint) -> Bool {
        guard let sim, let geometry else { return false }
        return sim.hits(x: point.x, y: point.y, geometry: geometry)
    }

    func touchBegan(at point: CGPoint) {
        send(.down(x: point.x, y: point.y, script: false))
    }

    func touchMoved(to point: CGPoint) {
        send(.move(x: point.x, y: point.y))
    }

    func touchEnded(velocity: CGVector) {
        send(.up(vx: velocity.dx, vy: velocity.dy))
    }

    /// The accessible actions: a picker orb's button and the entry's Undo.
    func add(mood: Int) {
        send(.add(mood: mood))
    }

    func undo() {
        send(.undo)
    }

    private func send(_ input: MoodOrbJarInput) {
        guard let geometry else { return }
        sim?.apply(input, geometry: geometry)
    }

    // MARK: Frames

    private func tick(timestamp: CFTimeInterval) {
        guard let geometry, sim != nil else { return }
        defer { previousTimestamp = timestamp }
        guard let previousTimestamp else { return }

        let roll = motionManager.deviceMotion?.attitude.roll ?? 0
        sim?.reduced = reducedMotion
        sim?.tilt = reducedMotion ? 0 : max(-1, min(1, roll))
        if Motion.replicaHold >= 0 {
            // QA only: run to the held time once, then freeze.
            if sim?.clock == 0 {
                sim?.step(geometry, frameDelta: Motion.replicaHold, autoPlay: autoPlay, uncapped: true)
            }
        } else {
            // The simulation bounds its physics accumulator; its autoplay script receives wall time.
            let wallDelta = max(0, timestamp - previousTimestamp) * Motion.qaSpeed
            sim?.step(geometry, frameDelta: wallDelta, autoPlay: autoPlay)
        }
        publish()
    }

    private func publish() {
        guard let sim, let geometry else { return }
        frame = MoodOrbJarFrame(sim: sim, geometry: geometry)
        if rowX != sim.rowX {
            rowX = sim.rowX
        }
        let nextGrow = MoodOrbJarFrame.grow(sim)
        if grow != nextGrow {
            grow = nextGrow
        }
        let pileCount = sim.orbs.count(where: { $0.state == .pile })
        if pileCount != count {
            withAnimation(listAnimation(Motion.countEnter)) {
                count = pileCount
            }
        }
        guard !sim.events.isEmpty else { return }
        let events = sim.events
        self.sim?.events.removeAll(keepingCapacity: true)
        events.forEach(handle)
    }

    // MARK: Events

    private func handle(_ event: MoodOrbJarEvent) {
        switch event {
        case .haptic(let haptic):
            play(haptic)
        case .added(let mood):
            withAnimation(listAnimation(Motion.rowLayout)) {
                let entry = MoodOrbJarEntry(
                    id: nextEntryID,
                    mood: mood,
                    tags: Tokens.moods[mood].tags,
                    time: Tokens.Copy.now,
                    isFresh: true
                )
                nextEntryID += 1
                entries = [entry] + entries.map { old in
                    var older = old
                    older.isFresh = false
                    return older
                }
            }
        case .removed:
            guard entries.first?.isFresh == true else { return }
            withAnimation(listAnimation(Motion.rowLayout)) {
                entries = Array(entries.dropFirst())
            }
        }
    }

    /// Reanimated's layout animations ease in and out; Reduce Motion drops them.
    private func listAnimation(_ duration: Double) -> Animation? {
        reducedMotion ? nil : .timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration)
    }

    private func play(_ haptic: MoodOrbJarEvent.Haptic) {
        switch haptic {
        case .grab:
            selectionFeedback.selectionChanged()
            selectionFeedback.prepare()
        case .throwOrb, .landSoft:
            lightFeedback.impactOccurred()
            lightFeedback.prepare()
        case .land:
            mediumFeedback.impactOccurred()
            mediumFeedback.prepare()
        case .landHard:
            heavyFeedback.impactOccurred()
            heavyFeedback.prepare()
        case .undo:
            notificationFeedback.notificationOccurred(.warning)
            notificationFeedback.prepare()
        }
    }
}
