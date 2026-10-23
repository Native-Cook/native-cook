import Observation
import UIKit

/// Owns the simulation and its clock, and turns touches into the inputs the simulation takes.
/// The views read `snapshot()`, which changes once per drawn frame and stops changing when the deck rests.
@MainActor
@Observable
final class CassetteDeckModel {
    private typealias Motion = CassetteDeckMotion
    private typealias Sim = CassetteDeckSim

    /// What a touch that began on the cassette area does: slide the cassettes or turn one of the two reels.
    private enum Grab { case slide, leftReel, rightReel }

    /// One scale per reel, found by running the whole script once, so the loop has no seam.
    private static let loopScales = Sim.loopScales()

    /// Bumped once per drawn frame; reading it makes a view redraw.
    private(set) var tick = 0

    @ObservationIgnored private var sim = Sim()
    @ObservationIgnored private let autoPlay: Bool
    @ObservationIgnored private let clock = CassetteDeckClock()
    @ObservationIgnored private var previousTime: CFTimeInterval = 0
    @ObservationIgnored private var pageAtStart = 0.0
    @ObservationIgnored private var grab = Grab.slide
    @ObservationIgnored private var lastOmega = 0.0
    @ObservationIgnored private var impacts: [UIImpactFeedbackGenerator.FeedbackStyle: UIImpactFeedbackGenerator] = [:]
    @ObservationIgnored private lazy var selection = UISelectionFeedbackGenerator()

    /// Follows the system's Reduce Motion setting.
    @ObservationIgnored var reduceMotion = false {
        didSet { sim.reduced = reduceMotion }
    }

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        sim.k = Self.loopScales
        sim.mode = autoPlay ? .autoplay : .fingers
    }

    /// The deck as of the last frame. Reading it subscribes the view to the next one.
    func snapshot() -> CassetteDeckSim {
        _ = tick
        return sim
    }

    // MARK: Clock

    func start() {
        previousTime = 0
        clock.start { [weak self] time in self?.frame(at: time) }
    }

    func stop() {
        clock.stop()
    }

    func setReduceMotion(_ value: Bool) {
        guard reduceMotion != value else { return }
        reduceMotion = value
        tick &+= 1
    }

    private func wake() {
        guard !clock.isRunning else { return }
        previousTime = 0
        clock.resume()
    }

    private func frame(at time: CFTimeInterval) {
        // Reanimated supplies 16 ms when its first frame has no predecessor.
        let elapsed = previousTime == 0 ? 0.016 : time - previousTime
        previousTime = time
        advance(by: elapsed)
        tick &+= 1
        if !autoPlay && sim.mode != .autoplay && sim.isSettled {
            clock.pause()
            previousTime = 0
        }
    }

    private func advance(by elapsed: Double) {
        sim.reduced = reduceMotion
        if let held = Motion.qaHold {
            freeze(held)
            return
        }
        if let t = Motion.qaT {
            if sim.acc != -t {
                var frozen = Sim()
                frozen.k = sim.k
                frozen.reduced = reduceMotion
                let n = Int(Sim.jsRound(t / Motion.step))
                for _ in 0..<n { frozen.advanceAutoplay() }
                frozen.acc = -t
                sim = frozen
            }
            return
        }
        let dt = min(0.05, elapsed)
        sim.acc += dt
        while sim.acc >= Motion.step {
            sim.acc -= Motion.step
            if sim.mode == .autoplay {
                sim.advanceAutoplay()
                if sim.step >= Sim.autoplayStepCount {
                    sim.canonicalizeAutoplayBoundary()
                }
                continue
            }
            sim.idle += Motion.step
            var free = !sim.dragging && !sim.scrubbing
            if sim.held.contains(true) { free = false }
            if sim.mode == .fingers && autoPlay && free && sim.idle * 1000 > Motion.interactionResumeMS {
                sim.mode = .returning
            }
            if sim.mode == .returning {
                // Back to rest before the script resumes: the play key is released and the first cassette comes home.
                sim.latched = false
                sim.target = Double(Sim.slotCount) * Sim.jsRound(sim.p / Double(Sim.slotCount))
                var still = abs(sim.p - sim.target) < 0.002 && abs(sim.pv) < 0.01 && sim.v == 0
                for i in 0..<Sim.keyCount where sim.d[i] > 0.002 { still = false }
                for j in 0..<Sim.slotCount where abs(sim.w[j]) > 0.003 { still = false }
                if still {
                    sim.resetToRest()
                    sim.mode = .autoplay
                    sim.step = 0
                    continue
                }
            }
            sim.advance(Motion.step, live: true)
        }
        if sim.mode != .autoplay {
            for event in sim.events { play(event) }
        }
        sim.events = []
    }

    /// Holds a frozen state for the replica's screenshots (QA only; off in the shipped block).
    private func freeze(_ hold: Motion.QAHold) {
        sim.resetToRest()
        if hold == .a || hold == .b {
            sim.latched = true
            sim.d[0] = 0.86
            sim.lit[0] = 1
            sim.led = 1
        }
        if hold == .b {
            sim.p = Motion.qaBPage
            sim.d[2] = 1
            sim.lit[2] = 1
        }
    }

    // MARK: Haptics

    private func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) -> UIImpactFeedbackGenerator {
        if let generator = impacts[style] { return generator }
        let generator = UIImpactFeedbackGenerator(style: style)
        impacts[style] = generator
        return generator
    }

    private func play(_ event: CassetteDeckHaptic) {
        switch event {
        case .latch: impact(.heavy).impactOccurred()
        case .release: impact(.light).impactOccurred()
        case .commit: impact(.rigid).impactOccurred()
        case .tick: selection.selectionChanged()
        case .click: impact(.medium).impactOccurred()
        }
    }

    // MARK: Input

    /// Any touch takes the deck from the autoplay script and restarts the idle count.
    private func touch() {
        if sim.mode == .autoplay {
            for i in 0..<Sim.keyCount { sim.held[i] = false }
            if sim.dragging { sim.dragTo(sim.p, velocity: sim.pv, done: true) }
        }
        sim.mode = .fingers
        sim.idle = 0
        // Warm the taptic engine so a key's click lands with its visual.
        impact(.medium).prepare()
        wake()
    }

    func press(_ key: Int, down: Bool) {
        touch()
        sim.pressKey(key, down: down)
    }

    /// VoiceOver's next / previous cassette.
    func nudge(_ direction: Double) {
        touch()
        sim.target = Sim.jsRound(sim.target) + direction
        if sim.reduced {
            sim.p = sim.target
            sim.fade = 0
        }
    }

    /// The finger went down on the cassette area at `point` (in that area's coordinates).
    func swipeBegan(at point: CGPoint, metrics: CassetteDeckMetrics) {
        touch()
        grab = .slide
        if sim.loadedSlot < 0 || sim.reduced { return }
        for i in 0..<2 {
            let centre = reelCentre(i, metrics: metrics)
            if hypot(point.x - centre.x, point.y - centre.y) < Motion.Scrub.grabRadius * metrics.scale {
                grab = i == 0 ? .leftReel : .rightReel
            }
        }
    }

    /// The finger travelled far enough to count as a drag.
    func swipeStarted() {
        pageAtStart = sim.p
    }

    func swipeMoved(translationX: Double, at point: CGPoint, velocity: CGVector, metrics: CassetteDeckMetrics) {
        switch grab {
        case .slide:
            sim.dragTo(pageAtStart - translationX / metrics.width, velocity: -velocity.dx / metrics.width, done: false)
        case .leftReel, .rightReel:
            // Angular velocity of the finger about the reel's centre: ω = (r × v) / |r|².
            let reel = grab == .leftReel ? 0 : 1
            let centre = reelCentre(reel, metrics: metrics)
            let rx = point.x - centre.x
            let ry = point.y - centre.y
            let scale = metrics.scale
            let r2 = max(rx * rx + ry * ry, 100 * scale * scale)
            let omega = (rx * velocity.dy - ry * velocity.dx) / r2
            lastOmega = omega
            sim.scrubTo(omega: omega, radius: packRadius(reel), active: true)
        }
        wake()
    }

    func swipeEnded(velocity: CGVector, metrics: CassetteDeckMetrics) {
        if sim.dragging { sim.dragTo(sim.p, velocity: -velocity.dx / metrics.width, done: true) }
        if sim.scrubbing {
            sim.scrubTo(omega: lastOmega, radius: packRadius(grab == .rightReel ? 1 : 0), active: false)
        }
        sim.idle = 0
        wake()
    }

    /// A reel's centre in the cassette area's coordinates.
    private func reelCentre(_ reel: Int, metrics: CassetteDeckMetrics) -> CGPoint {
        let layout = CassetteDeckTheme.Layout.self
        return CGPoint(
            x: (layout.cassette.x + layout.reels[reel].cx) * metrics.scale,
            y: (layout.swipeMargin + layout.reels[reel].cy) * metrics.scale
        )
    }

    private func packRadius(_ reel: Int) -> Double {
        // Nearest slot, not only a loaded one: the React Native block indexes a missing slot here.
        let slot = sim.nearestSlot
        return Sim.tapeRadius(reel == 0 ? 1 - sim.prog[slot] : sim.prog[slot])
    }
}
