import AVFoundation
import UIKit

@MainActor
final class CardVacuumSealFeedback {
    private var airPlayer: AVAudioPlayer?
    private var tupPlayer: AVAudioPlayer?
    private var inhalePlayer: AVAudioPlayer?
    private var airOn = false

    init() {
        func load(_ cue: String,_ volume: Float,_ loop: Bool = false) -> AVAudioPlayer? {
            guard let url = Bundle.main.url(forResource: "CardVacuumSeal-\(cue)",withExtension: "wav"),
                  let p = try? AVAudioPlayer(contentsOf: url) else { return nil }
            p.volume = volume; p.numberOfLoops = loop ? -1 : 0; p.enableRate = loop
            p.prepareToPlay()
            return p
        }
        airPlayer = load("air",0.032,true); tupPlayer = load("tup",0.12); inhalePlayer = load("inhale",0.12)
    }
    func air(_ rate: Double?) {
        guard let p = airPlayer else { return }
        guard let rate else {
            if airOn { p.pause(); p.currentTime = 0; airOn = false }
            return
        }
        // Ambient honours the hardware ringer. Configure only when touch actually requests sound.
        guard ambientSession() else { return }
        p.rate = Float(rate)
        if !airOn { airOn = true; p.play() }
    }
    func onset() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    func dimple() { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
    func detent(hard: Bool) {
        if hard { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
        else { UISelectionFeedbackGenerator().selectionChanged() }
    }
    func lock(sound: Bool) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        if sound { shot(tupPlayer) }
    }
    func airBack(sound: Bool) {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        // This is the reference's deliberate second cue, 60 ms after the first.
        DispatchQueue.main.asyncAfter(deadline: .now()+0.06) { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        if sound { shot(inhalePlayer) }
    }
    func stop() { air(nil); tupPlayer?.stop(); inhalePlayer?.stop() }
    private func ambientSession() -> Bool {
        let session = AVAudioSession.sharedInstance()
        if session.category == .ambient { return true }
        do { try session.setCategory(.ambient,mode: .default,options: []); return true }
        catch { return false }
    }
    private func shot(_ p: AVAudioPlayer?) {
        guard ambientSession(), let p else { return }
        p.currentTime = 0; p.play()
    }
}
