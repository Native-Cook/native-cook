import AVFoundation

final class IsometricScoreGaugeAudio {
    private var players: [String: AVAudioPlayer] = [:]
    func prepare() {
        guard players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        for cue in ["roll", "pick", "up"] {
            guard let url = Bundle.main.url(forResource: "IsometricScoreGauge-isg-" + cue, withExtension: "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url) else { continue }
            player.volume = cue == "roll" ? 0.6 : 0.85
            player.prepareToPlay()
            players[cue] = player
        }
    }
    func play(_ cue: String) {
        prepare()
        guard let player = players[cue] else { return }
        player.currentTime = 0
        player.play()
    }
    func stop() { players.values.forEach { $0.stop() } }
}
