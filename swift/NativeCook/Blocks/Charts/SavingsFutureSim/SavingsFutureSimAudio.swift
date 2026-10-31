import AVFoundation

// The touch driver calls these cues; autoplay stays silent except in the site's recording (DemoSound).
final class SavingsFutureSimAudio {
  private var players: [String: AVAudioPlayer] = [:]
  func play(_ cue: String) {
    if players[cue] == nil {
      guard
        let url = Bundle.main.url(forResource: "SavingsFutureSim-sfs-\(cue)", withExtension: "wav")
      else { return }
      do {
        try AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        let player = try AVAudioPlayer(contentsOf: url)
        player.volume = cue == "tick" ? 0.5 : 0.85
        player.prepareToPlay()
        players[cue] = player
      } catch { return }
    }
    players[cue]?.currentTime = 0
    players[cue]?.play()
  }
  func stop() { for player in players.values { player.stop() } }
}
