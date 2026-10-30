import AVFoundation
import UIKit

final class MorphObjectOnboardingAudio {
    private var players: [String: AVAudioPlayer] = [:]
    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    func prepare() {
        guard players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        for cue in ["morph", "pop", "tick"] {
            guard let url = MorphObjectOnboardingResources.url("moo-\(cue)", "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url) else { continue }
            player.volume = cue == "tick" ? 0.6 : 0.85
            player.prepareToPlay()
            players[cue] = player
        }
    }
    func play(_ cue: String) {
        guard let player = players[cue] else { return }
        player.currentTime = 0
        player.play()
    }
    func buzz(selection isSelection: Bool = false) {
        if isSelection { selection.selectionChanged() } else { impact.impactOccurred() }
    }
    func stop() {
        for player in players.values { player.stop() }
        players.removeAll()
    }
}
