import AVFoundation

// Touch cues only. Ambient playback respects the hardware ringer switch.
final class CareerWebOnboardingAudio {
    private var players: [String: AVAudioPlayer] = [:]
    func prepare() {
        guard players.isEmpty else { return }
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            for cue in ["pluck", "attach", "marker"] {
                guard let url = Bundle.main.url(forResource: "CareerWebOnboarding-cwo-\(cue)", withExtension: "wav") else { continue }
                let player = try AVAudioPlayer(contentsOf: url)
                player.volume = cue == "marker" ? 0.6 : 0.85
                player.prepareToPlay(); players[cue] = player
            }
        } catch {
            // Missing/unavailable audio must not interrupt the visual interaction.
        }
    }
    func play(_ cue: String, reduced: Bool) {
        guard !reduced else { return }
        players[cue]?.currentTime = 0
        players[cue]?.play()
    }
    func stop() { for player in players.values { player.stop() } }
}
