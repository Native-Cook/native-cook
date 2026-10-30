import AVFoundation
import UIKit

@MainActor
final class AiFeatureTourAudio {
    private var players: [String: AVAudioPlayer] = [:]
    private var pending: DispatchWorkItem?
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        for cue in ["tick", "whoosh", "sparkle", "tap"] {
            if let url = Bundle.main.url(forResource: "AiFeatureTour-aft-\(cue)", withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = cue == "tick" ? 0.5 : 0.85
                player.prepareToPlay()
                players[cue] = player
            }
        }
    }
    func play(_ cue: String, reduced: Bool) {
        guard !reduced, let player = players[cue] else { return }
        player.currentTime = 0
        player.play()
    }
    func send() {
        play("whoosh", reduced: false)
        let item = DispatchWorkItem { [weak self] in
            self?.play("sparkle", reduced: false)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
        pending?.cancel()
        pending = item
        // This delay is a cue in the authored timeline, not a driver or layout synchronization.
        DispatchQueue.main.asyncAfter(deadline: .now() + (AiFeatureTourMotion.T.sparkle - AiFeatureTourMotion.T.press), execute: item)
    }
    func stop() {
        pending?.cancel()
        pending = nil
        players.values.forEach { $0.stop() }
    }
}
