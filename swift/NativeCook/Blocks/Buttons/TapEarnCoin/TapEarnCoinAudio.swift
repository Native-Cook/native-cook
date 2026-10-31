import AVFoundation

final class TapEarnCoinAudio {
    private var players: [String: AVAudioPlayer] = [:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        for cue in ["coin", "morph", "ding"] {
            guard let url = Bundle.main.url(forResource: "TapEarnCoin-tec-\(cue)", withExtension: "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url) else { continue }
            player.volume = cue == "morph" ? 0.5 : cue == "coin" ? 0.75 : 0.8
            player.enableRate = true
            player.prepareToPlay()
            players[cue] = player
        }
    }
    func play(_ cue: String, rate: Double = 1) {
        guard let p = players[cue] else { return }
        p.currentTime = 0; p.rate = Float(rate); p.play()
    }
    func stop() { for p in players.values { p.stop() } }
}
