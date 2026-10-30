import AVFoundation

final class QrJoyPostcardAudio {
  private var players: [String: AVAudioPlayer] = [:]
  init() {
    for cue in ["flip", "sticker", "sparkle"] {
      guard
        let url = Bundle.main.url(forResource: "QrJoyPostcard-qjp-\(cue)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      else { continue }
      player.volume = cue == "sparkle" ? 0.7 : 0.9
      player.prepareToPlay()
      players[cue] = player
    }
  }
  func play(_ cue: String) {
    // Ambient respects the ringer switch and mixes with other apps, like the RN player.
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: [.mixWithOthers])
    guard let player = players[cue] else { return }
    player.currentTime = 0
    player.play()
  }
}
