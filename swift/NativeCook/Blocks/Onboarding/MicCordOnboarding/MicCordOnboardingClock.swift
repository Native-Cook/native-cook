import SwiftUI
import AVFoundation
import UIKit

final class MicCordOnboardingClock: ObservableObject {
    @Published var running = false
    var rope = MicCordOnboardingRope(0, 0)
    var rest = MicCordOnboardingRope(0, 0)
    var time = 0.0
    private var ax = -1.0, ay = -1.0
    private var date: Date?
    private var now = 0.0, last = -10.0, accumulator = 0.0
    private var debugQuiet = false
    private var debugTime: Double?
    private var start = CGPoint.zero, offset = CGPoint.zero
    private var finger = false
    private var players: [String: AVAudioPlayer] = [:]
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)

    func configure(_ width: Double, _ top: Double, _ pinned: Double?, _ reduced: Bool) {
        guard ax != width*0.52 || ay != top-30 || debugTime != pinned || (pinned != nil && debugQuiet != reduced) else { return }
        ax = width*0.52; ay = top-30; debugTime = pinned; debugQuiet = reduced
        rest = .rest(ax, ay); rope = rest; time = 0; accumulator = 0; date = nil
        if let pinned {
            var st = 0.0
            // Match the RN debug hook's fixed 1/120 s replay, including its boundary rounding.
            while st + MicCordOnboardingRope.step <= pinned {
                st += MicCordOnboardingRope.step
                rope.script(st, rest, ax, ay, reduced)
            }
            time = pinned
        }
    }
    func active(_ active: Bool, _ reduced: Bool) {
        date = nil
        if active && reduced && debugTime == nil && rope.notes.enumerated().contains(where: { $0.offset % 6 == 4 && $0.element <= 1.6 }) {
            running = true
        }
        if active && !reduced && debugTime == nil && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
            for key in ["grab", "swing", "notes"] {
                if let url = Bundle.main.url(forResource: "MicCordOnboarding-mco-\(key)", withExtension: "wav"),
                   let p = try? AVAudioPlayer(contentsOf: url) {
                    p.volume = key == "grab" ? 0.5 : 0.7; p.prepareToPlay(); players[key] = p
                }
            }
        }
        if !active || reduced { for p in players.values { p.stop() } }
    }
    func cue(_ key: String, _ reduced: Bool) {
        guard !reduced else { return }
        players[key]?.currentTime = 0; players[key]?.play()
    }
    func cta(_ reduced: Bool) { medium.impactOccurred(); cue("notes", reduced) }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool) {
        guard debugTime == nil else { return }
        // Wall-clock time is capped only at 0.5 s; physics still advances at fixed 120 Hz.
        let dt = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at; now += dt
        let touched = finger || now - last < 2
        accumulator += dt
        while accumulator >= MicCordOnboardingRope.step {
            accumulator -= MicCordOnboardingRope.step
            if auto && !touched {
                let before = time
                time += MicCordOnboardingRope.step
                if time >= 8 { time -= 8; rope = rest }
                rope.script(time, rest, ax, ay, reduced)
                if DemoSound.inAutoPlay {
                    if before < 1 && time >= 1 { cue("grab", reduced) }
                    // A slow release is silent, just like the RN velocity >500 touch threshold.
                    if before < 1.8 && time >= 1.8 && rope.speed > 500 { cue("swing", reduced) }
                }
            } else {
                if !reduced || rope.held { rope.integrate(ax, ay, MicCordOnboardingRope.step, 0.992) }
                rope.stepNotes(MicCordOnboardingRope.step, reduced)
            }
        }
        if !auto && !finger && now-last > 2 && (reduced || rope.speed < 0.01) && !rope.notes.enumerated().contains(where: { $0.offset % 6 == 4 && $0.element <= 1.6 }) && running {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
    }
    func touch(_ state: UIGestureRecognizer.State, _ p: CGPoint, _ velocity: CGPoint, _ reduced: Bool) {
        guard debugTime == nil else { return }
        switch state {
        case .began:
            last = now; date = nil; running = true
            let angle = atan2(rope.y[17]-rope.y[15], rope.x[17]-rope.x[15])
            let mx = rope.x[17] + cos(angle)*126, my = rope.y[17] + sin(angle)*126
            guard hypot(p.x-mx, p.y-my) <= 100 else { return }
            finger = true; rope.held = true; start = p
            offset = CGPoint(x: rope.x[17]-p.x, y: rope.y[17]-p.y)
            rope.hx = rope.x[17]; rope.hy = rope.y[17]
            cue("grab", reduced); light.impactOccurred()
        case .changed:
            guard finger else { return }; last = now
            rope.hx = p.x+offset.x; rope.hy = max(ay+40, p.y+offset.y)
        case .ended, .cancelled, .failed:
            guard finger else { return }; last = now; finger = false; rope.held = false
            if hypot(p.x-start.x, p.y-start.y) < 6 { rope.px[17] = rope.x[17]-7; rope.py[17] = rope.y[17]+2 }
            if hypot(velocity.x, velocity.y) > 500 { cue("swing", reduced) }
            medium.impactOccurred()
        default: break
        }
    }
}
