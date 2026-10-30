import AVFoundation
import UIKit

// Autoplay never calls this owner. The ambient session respects the hardware silent switch.
final class CashbackPickFiveAudio {
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let selection = UISelectionFeedbackGenerator()
  private let warning = UINotificationFeedbackGenerator()

  init() {
    for cue in ["pop", "drop", "back", "nope", "sparkle"] {
      if let url = Bundle.main.url(
        forResource: "CashbackPickFive-cpf-\(cue)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "sparkle" ? 0.6 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String, enabled: Bool) {
    guard enabled else { return }
    try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
    players[cue]?.currentTime = 0
    players[cue]?.play()
  }
  func buzz(_ kind: Int) {
    switch kind {
    case 2: selection.selectionChanged()
    case 3: warning.notificationOccurred(.warning)
    default: light.impactOccurred()
    }
  }
  func pick(enabled: Bool) {
    play("pop", enabled: enabled)
    DispatchQueue.main.asyncAfter(deadline: .now() + CashbackPickFiveMotion.FLY) { [weak self] in
      self?.play("drop", enabled: enabled)
      self?.buzz(0)
    }
  }
}
