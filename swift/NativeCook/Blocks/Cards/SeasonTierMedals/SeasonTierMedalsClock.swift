import AVFoundation
import SwiftUI
import UIKit

final class SeasonTierMedalsClock: ObservableObject {
    typealias M = SeasonTierMedalsMotion
    @Published var awake = false
    var current = 1
    private var rail = M.Rail()
    private var time = 0.0, now = 0.0, last = -10.0
    private var date: Date?
    private var startP = 1.0, at = 0.0
    private var detent = 1, byHand = false
    private var pinnedCache: M.Frame?
    private var pinnedReduced = false
    private var art: SeasonTierMedalsResources?
    private var players: [String: AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    func resources(_ size: CGSize, _ top: Double) -> SeasonTierMedalsResources {
        if let art, art.size == size && art.top == top { return art }
        let made = SeasonTierMedalsResources(size: size, top: top)
        art = made
        return made
    }
    func active(_ active: Bool, _ reduced: Bool, _ pinned: Bool) {
        date = nil
        if active && !reduced && !pinned {
            if players.isEmpty {
                try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
                for name in ["slide", "land", "tick"] {
                    if let url = Bundle.main.url(forResource: "SeasonTierMedals-stm-" + name, withExtension: "wav"),
                       let player = try? AVAudioPlayer(contentsOf: url) {
                        player.volume = name == "tick" ? 0.45 : name == "slide" ? 0.6 : 0.85
                        player.prepareToPlay(); players[name] = player
                    }
                }
            }
        } else {
            for player in players.values { player.stop() }
            if reduced { players.removeAll() }
        }
    }
    private func cue(_ name: String) {
        guard let player = players[name] else { return }
        player.currentTime = 0; player.play()
    }
    func buzz(_ kind: Int) {
        if kind == 0 { selection.selectionChanged() }
        else if kind == 1 { light.impactOccurred() }
        else { medium.impactOccurred() }
    }
    func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
        if let pinned {
            if pinnedCache?.time != pinned || pinnedReduced != reduced {
                pinnedCache = M.at(max(0, pinned), reduced: reduced)
                pinnedReduced = reduced
            }
            return pinnedCache!
        }
        // The loop runs on wall-clock time (a slow recorder must not stretch it); springs take a clamped step.
        let raw = max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016)
        let dt = min(raw, 0.05)
        self.date = date; now += dt
        let touched = rail.mode == 1 || now - last < 2
        let autoPlaying = auto && !reduced && !touched
        if autoPlaying {
            let before = time
            time += min(raw, 0.5)
            if time >= M.loop { time -= M.loop }
            rail.script(time, dt, now, reduced)
            if DemoSound.inAutoPlay {
                // Match the touch landing at 78% of each chosen medal's spin.
                for (beat, name) in [(1.4, "slide"), (1.4 + M.spin * 0.78, "land"),
                                     (4.4, "slide"), (4.4 + M.spin * 0.78, "land")] {
                    let crossed = before <= time
                        ? before < beat && time >= beat : before < beat || time >= beat
                    if crossed { cue(name) }
                }
            }
        } else { rail.step(dt) }
        // A previous touch must not make subsequent autoplay spins play a second landing.
        if byHand && !autoPlaying {
            for i in 0..<3 {
                let since = now - rail.spins[i]
                if since >= M.spin * 0.78 && since < M.spin && rail.landed != i {
                    rail.landed = i; cue("land"); buzz(1)
                }
            }
        }
        let frame = M.Frame(rail: rail, time: time, now: now)
        let moving = rail.mode == 1 || abs(rail.p - Double(rail.target)) > 0.000001 || abs(rail.v) > 0.000001 || rail.spins.contains { now - $0 < M.spin + 1.2 }
        current = frame.current
        // Publishing re-renders the whole screen, which cost a 40–50 ms frame at every medal change
        // while it played by itself (seen in the recordings); the timeline runs regardless then.
        if !(auto && !reduced) && awake != moving {
            DispatchQueue.main.async { [weak self] in
                guard let self, self.awake != moving else { return }
                self.awake = moving
            }
        }
        return frame
    }
    private func wake() {
        if !awake { date = nil; awake = true }
    }
    @discardableResult private func go(_ target: Int, _ spin: Bool, _ reduced: Bool) -> Bool {
        let tg = min(2, max(0, target)), changed = tg != rail.target
        rail.target = tg; rail.stiff = reduced ? 260 : 150
        if (changed || spin) && !reduced { rail.setSpin(tg, now) }
        wake()
        return changed
    }
    func step(_ dir: Int, _ reduced: Bool) {
        last = now
        if go(rail.target + dir, false, reduced) { buzz(0) }
    }
    func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ translation: CGSize,
               _ velocity: CGVector, _ width: Double, _ my: Double, _ reduced: Bool) {
        let sp = min(172, width * 0.43), rc = min(84, width * 0.21)
        switch state {
        case .began:
            wake(); last = now; startP = rail.p; at = now
            detent = M.round(rail.p); byHand = true
            rail.mode = 1; rail.finger = rail.p
        case .changed:
            last = now
            rail.finger = M.rubber(startP - translation.width / sp)
            let next = M.round(max(0, min(2, rail.finger)))
            if next != detent { detent = next; cue("tick"); buzz(0) }
        case .ended, .cancelled, .failed:
            last = now; rail.mode = 0
            if abs(translation.width) <= 8 && now - at < 0.35 {
                let o = (point.x - width / 2) / sp
                // RN compares gesture-local e.y with full-screen MY. Preserve that hit test.
                if abs(point.y - my) < rc * 1.2 {
                    let pick = M.round(rail.p + Double(max(-1, min(1, M.round(o)))))
                    let changed = go(pick, true, reduced)
                    cue(changed ? "slide" : "tick"); buzz(changed ? 0 : 2)
                }
                return
            }
            rail.v = -velocity.dx / sp
            if go(M.round(rail.p + rail.v * 0.16), false, reduced) { cue("slide") }
        default: break
        }
    }
}
