import AVFoundation

final class ListeningBuddyPaywallAudio {
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    for cue in ["pick1", "pick2", "pick3", "notes", "boop"] {
      if let url = Bundle.main.url(
        forResource: "ListeningBuddyPaywall-lbp-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "notes" ? 0.7 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String) {
    // Ambient mixes with other audio and respects the hardware silent switch.
    try? AVAudioSession.sharedInstance().setCategory(.ambient)
    guard let player = players[cue] else { return }
    player.currentTime = 0
    player.play()
  }
  func stop() { for player in players.values { player.stop() } }
}
