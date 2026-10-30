import AVFoundation

final class SunUvBuddyAudio {
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    // Ambient mixes with other audio and respects the hardware silent switch.
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: [.mixWithOthers])
    for cue in ["boing", "tick", "shades"] {
      if let url = Bundle.main.url(forResource: "SunUvBuddy-suv-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "tick" ? 0.5 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String) {
    guard let player = players[cue] else { return }
    player.currentTime = 0
    player.play()
  }
  deinit { for player in players.values { player.stop() } }
}
