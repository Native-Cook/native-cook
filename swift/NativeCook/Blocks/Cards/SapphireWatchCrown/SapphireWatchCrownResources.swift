import AVFoundation
import CoreText
import UIKit

/// Fonts and sound players are registered/preloaded once. Images are cached per geometry.
final class SapphireWatchCrownResources {
  static let shared = SapphireWatchCrownResources()
  var players: [String: AVAudioPlayer] = [:]
  private init() {
    for weight in ["Regular","Medium","SemiBold"] {
      if let url = Bundle.main.url(forResource: "SapphireWatchCrown-Poppins-\(weight)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
    try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
    for cue in ["detent","tick"] {
      if let url = Bundle.main.url(forResource: "SapphireWatchCrown-swc-\(cue)",withExtension: "wav"), let p = try? AVAudioPlayer(contentsOf: url) {
        p.volume = cue == "detent" ? 0.5 : 0.55; p.prepareToPlay(); players[cue]=p
      }
    }
  }
  func play(_ cue: String) { if let p = players[cue] { p.currentTime=0; p.play() } }
  func stop() { for p in players.values { p.stop() } }
}
