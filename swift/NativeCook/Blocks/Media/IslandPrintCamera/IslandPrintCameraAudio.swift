import AVFoundation

/// Local, silent-switch-respecting mechanical cues. Autoplay remains silent.
final class IslandPrintCameraAudio {
    private var players: [String: AVAudioPlayer] = [:]
    init() {
        for cue in ["shutter", "motor", "land"] {
            guard let url=Bundle.main.url(forResource: "IslandPrintCamera-"+cue,withExtension: "wav"), let player=try? AVAudioPlayer(contentsOf: url) else { continue }
            player.volume=cue == "motor" ? 0.4 : cue == "land" ? 0.5 : 0.7
            player.prepareToPlay(); players[cue]=player
        }
    }
    func play(_ cue: String) { guard let player=players[cue] else { return }; player.currentTime=0; player.play() }
    func stop() { players.values.forEach { $0.stop() } }
}
