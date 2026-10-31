import AVFoundation
import SwiftUI
import UIKit

final class PerfectMonthGemClock: ObservableObject {
    typealias M = PerfectMonthGemMotion
    @Published var running = true
    private var date: Date?
    private var now = 0.0
    private var time = 0.0
    private var last = -10.0
    private var p = 0.0
    private var v = 0.0
    private var target = 1.0
    private var held = false
    private var p0 = 0.0
    private var forged = false
    private var players: [String: AVAudioPlayer] = [:]
    private var reduced = false
    private var auto = false
    private var pinned = false
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()

    func configure(_ auto: Bool, _ reduced: Bool, _ pinned: Bool, _ active: Bool) {
        self.auto = auto && !reduced
        self.reduced = reduced
        self.pinned = pinned
        date = nil
        if active && !pinned && !reduced && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
            for name in ["gather", "forge", "tap"] {
                if let url = Bundle.main.url(forResource: "PerfectMonthGem-pmg-\(name)", withExtension: "wav"), let player = try? AVAudioPlayer(contentsOf: url) {
                    player.volume = name == "tap" ? 0.5 : 0.75
                    player.prepareToPlay()
                    players[name] = player
                }
            }
        }
        if !active || reduced { players.values.forEach { $0.stop() } }
        running = active && !pinned
    }
    private func cue(_ name: String) {
        guard !reduced, !pinned, let player = players[name] else { return }
        player.currentTime = 0
        player.play()
    }
    func buzz(_ kind: Int) {
        if kind == 2 { success.notificationOccurred(.success) }
        else if kind == 1 { light.impactOccurred() }
        else { selection.selectionChanged() }
    }
    func sample(_ at: Date, _ pinnedTime: Double?, _ autoPlay: Bool, _ reduceMotion: Bool) -> M.Frame {
        auto = autoPlay && !reduceMotion
        reduced = reduceMotion
        if let t = pinnedTime { return .init(p: M.progress(t), turn: 2 * .pi * t / M.loop, reduced: reduced) }
        // The clock follows wall time on slow Cloud simulators. Only the spring step is clamped.
        let elapsed = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        let dt = min(elapsed, 0.05)
        date = at
        now += elapsed
        let playing = auto && !held && now - last >= 2
        if playing {
            let before = time
            let oldP = p
            time = (time + elapsed).truncatingRemainder(dividingBy: M.loop)
            p = M.progress(time)
            v = 0
            target = p > 0.5 ? 1 : 0
            if DemoSound.inAutoPlay {
                if before < 0.6 && time >= 0.6 { cue("gather") }
                if oldP <= 0.92 && p > 0.92 { cue("forge") }
                if before < 6.6 && time >= 6.6 { cue("tap") }
            }
        } else if !held {
            if reduced { p += (target - p) * (1 - exp(-dt * 16)) }
            else {
                let k = 170.0
                v += (k * (target - p) - 2 * sqrt(k) * 0.8 * v) * dt
                p += v * dt
            }
        }
        if held || !playing {
            if p > 0.92 && !forged { forged = true; cue("forge"); buzz(2) }
            else if p < 0.6 { forged = false }
        }
        // A settled calendar has no animated marks. The formed gem keeps the RN slow turn.
        if !auto && !held && target == 0 && abs(p) < 0.00001 && abs(v) < 0.0001 && running {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return .init(p: p, turn: 2 * .pi * (auto && !held ? time : now) / M.loop, reduced: reduced)
    }
    func touch(_ state: UIGestureRecognizer.State, _ translation: CGSize, _ velocity: CGVector) {
        guard !pinned else { return }
        if !running { date = nil; running = true }
        switch state {
        case .began: last = now
        case .changed:
            if !held && hypot(translation.width, translation.height) >= 6 {
                held = true; p0 = p; v = 0
                cue("gather"); buzz(1)
            }
            if held {
                last = now
                var next = p0 - translation.height / 180
                if next > 1 { next = 1 + 0.08 * tanh((next - 1) / 0.08) }
                if next < 0 { next = -0.08 * tanh(-next / 0.08) }
                p = next
            }
        case .ended, .cancelled, .failed:
            if held {
                last = now
                held = false
                v = -velocity.dy / 180
                target = p + v * 0.15 > 0.5 ? 1 : 0
            } else if state == .ended {
                last = now
                target = target > 0.5 && p > 0.5 ? 0 : 1
                cue(target == 1 ? "gather" : "tap")
                buzz(0)
            }
        default: break
        }
    }
    func accessibilityToggle() {
        guard !pinned else { return }
        if !running { date = nil; running = true }
        last = now
        target = p > 0.5 ? 0 : 1
        buzz(0)
    }
}
