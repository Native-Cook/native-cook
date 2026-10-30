import AVFoundation

final class GreatWaveMorningAudio {
  var enabled = true
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    for cue in ["surge", "splash", "tap"] {
      if let url = Bundle.main.url(
        forResource: "GreatWaveMorning-gwm-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "surge" ? 0.6 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String, reduced: Bool) {
    guard enabled, !reduced, let player = players[cue] else { return }
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: .mixWithOthers)
    player.currentTime = 0
    player.play()
  }
  func stop() { for player in players.values { player.stop() } }
}
