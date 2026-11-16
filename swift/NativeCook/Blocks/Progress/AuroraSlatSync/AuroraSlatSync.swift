import SwiftUI
import UIKit

/// A sync hero: an aurora seen through eleven slats of reeded glass. Drag to push the light and
/// scrub the sync; two seconds after the last touch it settles and the eight-second loop resumes.
@MainActor
struct AuroraSlatSync: View {
    static let loop: Double = 8.0
    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date.now
    @State private var mode = AuroraSlatSyncPlayback.playing
    @State private var dragging = false
    @State private var startP = 0.0
    @State private var holdDrift = 0.0
    @State private var holdStart = Date.now
    @State private var pushX = 0.0
    @State private var pushY = 0.0
    @State private var releaseDate = Date.distantPast
    @State private var sweepDate = Date.distantPast
    @State private var generation = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var touching = false

    init(autoPlay: Bool = false) { self.autoPlay = autoPlay }

    private var paused: Bool {
        if reduceMotion || scenePhase != .active { return true }
        if case .playing = mode { return !autoPlay }
        return false
    }

    var body: some View {
        TimelineView(.animation(paused: paused)) { timeline in
            let frame = frame(at: timeline.date)
            let tick = sweepTick(at: timeline.date)
            Canvas { context, size in
                AuroraSlatSyncScene.draw(&context, size: size, frame: frame)
            }
            // Changes once per loop, when the sweep starts; never at the loop seam.
            .onChange(of: tick) { before, after in
                if let before, let after, after > before { impact() }
            }
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .gesture(push)
        .accessibilityElement()
        .accessibilityLabel("Halo Vault is syncing 1,284 files across 3 devices")
        .accessibilityHint("Drag to push the light and scrub the sync")
        .preferredColorScheme(.dark)
        .onAppear { restart(); haptic.prepare() }
        .onDisappear { generation += 1 }
        .onChange(of: reduceMotion) { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: scenePhase) { restart() }
        .onChange(of: touching) { _, down in if !down { finishPush() } }
        .task(id: generation) { await returnAfterIdle(generation) }
    }

    // MARK: Clock (every frame is a function of the date and the state set at touch boundaries)

    private func frame(at date: Date) -> AuroraSlatSyncFrame {
        guard !reduceMotion else { return AuroraSlatSyncMotion.finished }
        switch mode {
        case .playing:
            let t = autoPlay ? AuroraSlatSyncMotion.wrap(max(0, date.timeIntervalSince(origin))) : 0
            return AuroraSlatSyncMotion.loopFrame(t)
        case .held(let p):
            let settle = dragging ? 1 : AuroraSlatSyncMotion.spring(date.timeIntervalSince(releaseDate))
            let sweep = date.timeIntervalSince(sweepDate) / AuroraSlatSyncMotion.sweepLength
            return AuroraSlatSyncMotion.heldFrame(
                p: p, drift: holdDrift + date.timeIntervalSince(holdStart),
                pushX: pushX * settle, pushY: pushY * settle,
                sweep: sweep >= 0 && sweep <= 1 ? sweep : -1)
        case .returning(let fromP, let fromDrift, let toDrift, let start, let duration):
            let e = AuroraSlatSyncMotion.inOut(AuroraSlatSyncMotion.seg(date.timeIntervalSince(start), 0, duration))
            return AuroraSlatSyncMotion.heldFrame(
                p: fromP * (1 - e), drift: fromDrift + (toDrift - fromDrift) * e, pushX: 0, pushY: 0, sweep: -1)
        }
    }

    private func sweepTick(at date: Date) -> Int? {
        guard autoPlay, !reduceMotion, scenePhase == .active, case .playing = mode else { return nil }
        let elapsed = max(0, date.timeIntervalSince(origin))
        return Int(((elapsed + Self.loop - AuroraSlatSyncMotion.sweepStart) / Self.loop).rounded(.down))
    }

    // MARK: Touch

    private var push: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($touching) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduceMotion else { return }
                if !dragging {
                    let frozen = frame(at: value.time)
                    generation += 1
                    dragging = true
                    startP = frozen.p
                    holdDrift = frozen.drift
                    holdStart = value.time
                    mode = .held(frozen.p)
                }
                let dx = Double(value.translation.width), dy = Double(value.translation.height)
                pushX = AuroraSlatSyncMotion.rubber(dx)
                pushY = AuroraSlatSyncMotion.rubber(dy)
                let before: Double
                if case .held(let p) = mode { before = p } else { before = startP }
                let next = min(1, max(0, startP + dx / 300 - dy / 460))
                if next >= 1, before < 1 {
                    sweepDate = value.time
                    impact()
                }
                mode = .held(next)
            }
            .onEnded { _ in finishPush() }
    }

    private func finishPush() {
        // GestureState also releases a cancelled drag, so the idle timer cannot get stranded.
        guard dragging else { return }
        dragging = false
        releaseDate = .now
        generation += 1
    }

    private func returnAfterIdle(_ token: Int) async {
        guard !dragging, !reduceMotion, case .held = mode else { return }
        do { try await Task.sleep(for: .seconds(2)) } catch { return }
        guard generation == token, !dragging, !reduceMotion, case .held(let p) = mode else { return }
        let now = Date.now
        let drift = AuroraSlatSyncMotion.wrap(holdDrift + now.timeIntervalSince(holdStart))
        let duration = 0.9
        mode = .returning(
            fromP: p, fromDrift: drift, toDrift: drift < Self.loop / 2 ? 0 : Self.loop, start: now, duration: duration)
        do { try await Task.sleep(for: .seconds(duration)) } catch { return }
        guard generation == token, !dragging, !reduceMotion else { return }
        origin = .now
        mode = .playing
    }

    private func restart() {
        generation += 1
        dragging = false
        pushX = 0
        pushY = 0
        origin = .now
        mode = .playing
    }

    private func impact() {
        guard !reduceMotion else { return }
        haptic.impactOccurred()
        haptic.prepare()
    }
}

private enum AuroraSlatSyncPlayback {
    case playing
    case held(Double)
    case returning(fromP: Double, fromDrift: Double, toDrift: Double, start: Date, duration: Double)
}

#Preview {
    AuroraSlatSync(autoPlay: true)
}
