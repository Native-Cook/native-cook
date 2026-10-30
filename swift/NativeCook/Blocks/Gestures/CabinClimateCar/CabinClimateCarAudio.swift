import AVFoundation
import UIKit

final class CabinClimateCarAudio {
  private var players: [String: AVAudioPlayer] = [:]
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  init() {
    for cue in ["tick", "slide", "air", "tap"] {
      if let url = Bundle.main.url(forResource: "CabinClimateCar-ccc-\(cue)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "tick" ? 0.5 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String, _ reduced: Bool) {
    // Autoplay never calls this. Ambient audio respects the silent switch.
    if !reduced {
      try? AVAudioSession.sharedInstance().setCategory(
        .ambient, mode: .default, options: .mixWithOthers)
      if let player = players[cue] {
        player.currentTime = 0
        player.play()
      }
    }
    if cue == "tick" || cue == "air" {
      selection.selectionChanged()
    } else {
      light.impactOccurred()
    }
  }
  func stop() { for player in players.values { player.stop() } }
}
