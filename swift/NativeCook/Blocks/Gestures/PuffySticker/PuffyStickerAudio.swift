import AVFoundation

final class PuffyStickerAudio {
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: .mixWithOthers)
    for cue in ["inflate", "morph", "land", "tick"] {
      guard let url = Bundle.main.url(forResource: "PuffySticker-pst-\(cue)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      else { continue }
      player.volume = cue == "tick" ? 0.35 : cue == "inflate" ? 0.55 : 0.7
      player.prepareToPlay()
      players[cue] = player
    }
  }
  func play(_ cue: String) {
    guard let p = players[cue] else { return }
    p.currentTime = 0
    p.play()
  }
  func stop(_ cue: String) { players[cue]?.pause() }
  func stopAll() { for p in players.values { p.stop() } }
}
