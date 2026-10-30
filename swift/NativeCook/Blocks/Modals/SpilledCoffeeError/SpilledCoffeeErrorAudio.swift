import AVFoundation
import UIKit

final class SpilledCoffeeErrorAudio {
  var enabled = true
  private var players: [String: AVAudioPlayer] = [:]
  private var script: [DispatchWorkItem] = []
  init() {
    try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
    for cue in ["rewind", "clack", "tip", "splash", "tap"] {
      if let url = Bundle.main.url(
        forResource: "SpilledCoffeeError-sce-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "rewind" ? 0.6 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func play(_ cue: String) {
    guard enabled, let player = players[cue] else { return }
    player.currentTime = 0
    player.play()
  }
  func buzz(_ kind: Int) {
    if kind == 2 {
      UISelectionFeedbackGenerator().selectionChanged()
    } else if kind == 3 {
      UINotificationFeedbackGenerator().notificationOccurred(.error)
    } else {
      UIImpactFeedbackGenerator(style: kind == 1 ? .medium : .light).impactOccurred()
    }
  }
  func cancelScript() {
    for item in script { item.cancel() }
    script.removeAll()
  }
  func playScript() {
    typealias T = SpilledCoffeeErrorMotion.T
    cancelScript()
    play("rewind")
    at(T.rise - T.press + 0.25) { $0.play("clack") }
    at(T.land - T.press - 0.05) {
      $0.play("tip")
      $0.buzz(1)
    }
    at(T.land - T.press + 0.1) {
      $0.play("splash")
      $0.buzz(3)
    }
  }
  private func at(_ seconds: Double, _ action: @escaping (SpilledCoffeeErrorAudio) -> Void) {
    let item = DispatchWorkItem { [weak self] in if let self { action(self) } }
    script.append(item)
    DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: item)
  }
  deinit { for item in script { item.cancel() } }
}
