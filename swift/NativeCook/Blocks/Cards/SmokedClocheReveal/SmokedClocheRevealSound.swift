import AVFoundation

/// Short, cached glass and cart cues, shared by touch and the recording-only demo audio.
final class SmokedClocheRevealSound {
  private var players=[Int:AVAudioPlayer]()
  func play(_ kind: Int) {
    if players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
      for i in 0...2 {
        if let player=try? AVAudioPlayer(data:Self.wave(i)) {
          player.volume=0.35;player.prepareToPlay();players[i]=player
        }
      }
    }
    guard let player=players[kind] else { return }
    player.currentTime=0;player.play()
  }
  private static func wave(_ kind: Int) -> Data {
    let rate=44100,count=Int(0.14*Double(rate))
    var data=Data()
    func word(_ value: UInt16) {
      var v=value.littleEndian;withUnsafeBytes(of:&v) { data.append(contentsOf:$0) }
    }
    func long(_ value: UInt32) {
      var v=value.littleEndian;withUnsafeBytes(of:&v) { data.append(contentsOf:$0) }
    }
    data.append(contentsOf:"RIFF".utf8);long(UInt32(36+count*2))
    data.append(contentsOf:"WAVEfmt ".utf8);long(16);word(1);word(1)
    long(UInt32(rate));long(UInt32(rate*2));word(2);word(16)
    data.append(contentsOf:"data".utf8);long(UInt32(count*2))
    let frequency=[880.0,1320.0,660.0][kind]
    for i in 0..<count {
      let t=Double(i)/Double(rate),envelope=min(1,t/0.002)*exp(-t*45)
      let tone=sin(2*Double.pi*frequency*t)+0.3*sin(2*Double.pi*frequency*2.76*t)
      word(UInt16(bitPattern:Int16(tone*envelope*9000)))
    }
    return data
  }
}
