import SwiftUI
import AVFoundation
import UIKit

final class VoiceOrbScannerClock: ObservableObject {
    private var frame = VoiceOrbScannerMotion.Frame()
    private var date: Date?
    private var last = -10.0
    private var direction = 0.0
    private var held = false
    private var players: [String: AVAudioPlayer] = [:]
    func prepare(_ reduced: Bool) {
        date = nil
        if reduced {
            players.values.forEach { $0.stop() }
            players.removeAll()
            return
        }
        guard players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for name in ["listen", "split", "merge"] {
            if let url = Bundle.main.url(forResource: "VoiceOrbScanner-vos-\(name)", withExtension: "wav"), let p = try? AVAudioPlayer(contentsOf: url) {
                p.volume = name == "listen" ? 0.45 : 0.75
                p.prepareToPlay(); players[name] = p
            }
        }
    }
    func suspend() { date = nil; players.values.forEach { $0.stop() } }
    private func cue(_ name: String, _ reduced: Bool) {
        guard !reduced, let p = players[name] else { return }
        p.currentTime = 0; p.play()
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> VoiceOrbScannerMotion.Frame {
        if let pinned { return VoiceOrbScannerMotion.at(pinned) }
        let raw = max(0, date.map { at.timeIntervalSince($0) } ?? 0.016)
        let dt = min(raw, 0.05)
        date = at; frame.now += dt
        let touched = held || frame.now-last < 2
        if auto && !reduced && !touched {
            // Slow recording frames must not stretch the loop; touch keeps the clamped step.
            let before = frame.t
            let t = (before+min(raw, 0.5)).truncatingRemainder(dividingBy: VoiceOrbScannerMotion.loop)
            if DemoSound.inAutoPlay {
                for (beat, name) in [(1.0, "listen"), (3.4, "split"), (6.9, "merge")] {
                    let crossed = before <= t
                        ? before < beat && t >= beat : before < beat || t >= beat
                    if crossed { cue(name, reduced) }
                }
            }
            frame = VoiceOrbScannerMotion.at(t, now: frame.now); direction = 0
        } else {
            if held && frame.S < 0.01 {
                frame.listening = true; frame.L = min(1, frame.L+dt/2.4)
                if frame.L >= 1 {
                    frame.listening = false; direction = 1
                    cue("split", reduced); UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
            } else if direction == 0 && frame.S < 0.01 {
                frame.listening = false; frame.L = max(0, frame.L-dt*2.5)
            }
            if direction != 0 {
                frame.S = VoiceOrbScannerMotion.clamp(frame.S+direction*dt/(direction > 0 ? 1.1 : 1))
                if direction < 0 && frame.S <= 0 { direction = 0; frame.L = 0 }
                if direction > 0 && frame.S >= 1 { direction = 0 }
            }
        }
        frame.held = held
        frame.useLoopPhase = auto && !reduced && frame.now - last >= 2
        return frame
    }
    func begin(_ reduced: Bool) {
        last = frame.now; date = nil
        if frame.S > 0.99 && direction == 0 {
            direction = -1; cue("merge", reduced); UIImpactFeedbackGenerator(style: .soft).impactOccurred(); return
        }
        if frame.S > 0.01 { return }
        held = true; cue("listen", reduced); UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }
    func end() { last = frame.now; held = false }
    func rescan(_ reduced: Bool) {
        last = frame.now; date = nil
        if frame.S > 0.5 {
            direction = -1; cue("merge", reduced); UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }
}
