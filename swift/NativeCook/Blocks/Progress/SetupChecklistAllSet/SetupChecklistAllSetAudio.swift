import AVFoundation

final class SetupChecklistAllSetAudio {
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: [.mixWithOthers])
    for cue in ["check", "uncheck", "complete", "pop"] {
      if let url = Bundle.main.url(
        forResource: "SetupChecklistAllSet-sca-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "complete" ? 0.7 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String) {
    players[cue]?.currentTime = 0
    players[cue]?.play()
  }
  func stop() { players.values.forEach { $0.stop() } }
}
