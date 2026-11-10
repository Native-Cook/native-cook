import AVFoundation

// Prepare both short liquid cues once; touch and the recording script share the same players.
final class EyewearLensTintSound {
  private var players: [AVAudioPlayer] = []
  init() {
    try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
    for pouring in [false, true] {
      let rate = 22050, duration = pouring ? 0.14 : 0.055
      let count = Int(Double(rate) * duration)
      var data = Data()
      func word(_ value: UInt16) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
      func long(_ value: UInt32) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
      data.append(contentsOf: "RIFF".utf8); long(UInt32(36 + count * 2))
      data.append(contentsOf: "WAVEfmt ".utf8); long(16); word(1); word(1)
      long(UInt32(rate)); long(UInt32(rate * 2)); word(2); word(16)
      data.append(contentsOf: "data".utf8); long(UInt32(count * 2))
      for i in 0..<count {
        let t = Double(i) / Double(rate), u = t / duration
        let phase = 2 * Double.pi * ((pouring ? 650 : 1100) * t - (pouring ? 1900 : 3500) * t * t)
        let envelope = min(1, t / 0.003) * pow(1 - u, 3)
        let sample = Int16(sin(phase) * envelope * 7000)
        word(UInt16(bitPattern: sample))
      }
      if let player = try? AVAudioPlayer(data: data) { player.prepareToPlay(); players.append(player) }
    }
  }
  func play(_ pouring: Bool) {
    let index = pouring ? 1 : 0
    guard players.indices.contains(index) else { return }
    players[index].currentTime = 0; players[index].play()
  }
}
