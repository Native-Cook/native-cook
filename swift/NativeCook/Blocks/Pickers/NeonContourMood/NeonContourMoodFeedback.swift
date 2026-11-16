import AVFoundation
import UIKit

@MainActor
final class NeonContourMoodFeedback {
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private var lastCue = -1e6
    private var player: AVAudioPlayer?

    init() {
        // Ambient mixes with other audio and respects the hardware ringer switch.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        if let url = Bundle.main.url(forResource: "NeonContourMood-ncm-strike", withExtension: "wav") {
            player = try? AVAudioPlayer(contentsOf: url)
            player?.volume = 0.7; player?.enableRate = true; player?.prepareToPlay()
        }
    }
    func cue(_ cue: String, weight: Int = 1) {
        let now = Date().timeIntervalSinceReferenceDate
        guard now - lastCue >= 0.035 else { return }
        lastCue = now
        if cue == "limit" || (cue == "detent" && weight == 2) { medium.impactOccurred() }
        else if cue == "pulse" || (cue == "detent" && weight == 0) { soft.impactOccurred() }
        else { light.impactOccurred() }
    }
    func strike(_ index: Int) {
        player?.rate = Float(0.9 + 0.25 * Double(index) / 6)
        player?.currentTime = 0; player?.play()
    }
}
