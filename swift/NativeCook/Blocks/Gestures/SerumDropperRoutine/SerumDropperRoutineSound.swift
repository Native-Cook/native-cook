import AVFoundation

// Short, cached PCM cues shared by touch and the site's autoplay recording.
final class SerumDropperRoutineSound {
    enum Cue: CaseIterable { case tick, drop, done }
    private var players: [Cue: AVAudioPlayer] = [:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for cue in Cue.allCases {
            if let player = try? AVAudioPlayer(data:Self.wave(cue)) {
                player.volume = 0.35
                player.prepareToPlay()
                players[cue] = player
            }
        }
    }
    func play(_ cue: Cue) {
        guard let player = players[cue] else { return }
        player.currentTime = 0
        player.play()
    }
    private static func wave(_ cue: Cue) -> Data {
        let rate = 44100
        let duration = cue == .tick ? 0.04 : cue == .drop ? 0.11 : 0.2
        let count = Int(duration*Double(rate))
        var data = Data()
        func word(_ value: UInt16) {
            var little = value.littleEndian
            withUnsafeBytes(of:&little) { data.append(contentsOf:$0) }
        }
        func integer(_ value: UInt32) {
            var little = value.littleEndian
            withUnsafeBytes(of:&little) { data.append(contentsOf:$0) }
        }
        data.append(contentsOf:"RIFF".utf8); integer(UInt32(36+count*2))
        data.append(contentsOf:"WAVEfmt ".utf8); integer(16)
        word(1); word(1); integer(UInt32(rate)); integer(UInt32(rate*2)); word(2); word(16)
        data.append(contentsOf:"data".utf8); integer(UInt32(count*2))
        for i in 0..<count {
            let t = Double(i)/Double(rate)
            let attack = min(1,t/0.003), release = min(1,(duration-t)/0.01)
            let phase: Double
            switch cue {
            case .tick: phase = 2 * .pi * 1600*t
            case .drop: phase = 2 * .pi * (720*t-2200*t*t)
            case .done: phase = 2 * .pi * (t < 0.09 ? 880*t : 880*0.09+1174*(t-0.09))
            }
            let envelope = attack*release*exp(-t/(cue == .tick ? 0.012 : 0.07))
            let sample = Int16(sin(phase)*envelope*8000)
            word(UInt16(bitPattern:sample))
        }
        return data
    }
}
