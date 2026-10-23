import AVFoundation

/// expo-audio's `useAudioPlayer` reduced to what the detent click needs:
/// seek to 0 and play. The app's audio session is left alone, as in RN.
@MainActor
final class GlowknobAVAudioPlayerShim {
  private let player: AVAudioPlayer

  init?(resource: String, ext: String) {
    guard let url = Bundle.main.url(forResource: resource, withExtension: ext),
          let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
    player.prepareToPlay()
    self.player = player
  }

  func restart() {
    player.currentTime = 0
    player.play()
  }
}
