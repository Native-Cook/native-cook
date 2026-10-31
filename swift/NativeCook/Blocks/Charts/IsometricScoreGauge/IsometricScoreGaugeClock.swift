import SwiftUI
import UIKit

final class IsometricScoreGaugeClock: ObservableObject {
    typealias M = IsometricScoreGaugeMotion
    @Published var revision = 0
    var driver = M.makeDriver()
    private var date: Date?
    private var now = 0.0
    private var start = CGPoint.zero
    private var startScroll = 0.0
    private var inGrid = false
    private var moved = false
    private var reducedPick = -1
    private var fadeFrom = 0.0
    private var fadeTo = 0.0
    private var fadeDate = Date.distantPast
    private let audio = IsometricScoreGaugeAudio()
    func prepareAudio() { audio.prepare() }
    func resetDate() { date = nil }
    func reset() {
        driver = M.makeDriver()
        now = 0; date = nil
        revision += 1
    }
    func stop() { date = nil; audio.stop() }
    func fade(at date: Date) -> Double {
        let progress = M.clamp01(date.timeIntervalSince(fadeDate) / 0.2)
        // Reanimated withTiming's default is Easing.inOut(Easing.quad).
        let eased = progress < 0.5 ? 2 * progress * progress : 1 - pow(-2 * progress + 2, 2) / 2
        return M.mix(fadeFrom, fadeTo, eased)
    }
    func reducedFrame() -> M.Frame {
        guard reducedPick >= 0 else { return M.Frame() }
        return M.Frame(sel: reducedPick, selW: 1,
                       score: IsometricScoreGaugeTheme.Gauge.score + IsometricScoreGaugeTheme.columns[reducedPick / 2][reducedPick % 2].delta)
    }
    func sample(_ sampleDate: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
        let dt = min(max(0, date.map { sampleDate.timeIntervalSince($0) } ?? 0.016), 0.05)
        date = sampleDate
        guard !reduced else { return M.Frame() }
        now += dt
        if let pinned {
            driver.mode = 0; driver.t = pinned; driver.amb = pinned
            M.step(&driver, dt: 0, now: now, auto: false)
        } else {
            let before = driver.t
            M.step(&driver, dt: dt, now: now, auto: auto)
            // The site's recording plays the sounds a touch would make (DemoSound).
            if DemoSound.inAutoPlay && auto && driver.mode == 0 && driver.t > before {
                let cues: [(Double, Int)] = [(M.T.pick1, 0), (M.T.drop1, 0), (M.T.pick2, 3), (M.T.drop2, 3)]
                for (time, index) in cues where before < time && driver.t >= time {
                    let picking = time == M.T.pick1 || time == M.T.pick2
                    let delta = picking ? IsometricScoreGaugeTheme.columns[index / 2][index % 2].delta : 0
                    audio.play(delta > 0 ? "up" : "pick")
                    audio.play("roll")
                }
            }
        }
        return driver.frame
    }
    func begin(_ point: CGPoint, reduced: Bool) {
        start = point; startScroll = driver.scroll; moved = false
        inGrid = point.y > IsometricScoreGaugeTheme.UI.rows[0] - 20
        if !reduced { M.takeOver(&driver, now: now) }
        revision += 1
    }
    func change(dx: Double, screenDX: Double, reduced: Bool) {
        guard !reduced, inGrid else { return }
        driver.last = now
        if abs(screenDX) > 8 { moved = true }
        if moved { M.dragTo(&driver, from: startScroll, dx: dx) }
    }
    func end(velocity: Double, reduced: Bool) {
        driver.last = now
        if reduced {
            let index = M.cardAt(start, scroll: 0)
            if index >= 0 {
                let currentFade = fade(at: Date())
                let same = reducedPick == index && currentFade > 0.5
                if !same { reducedPick = index }
                fadeFrom = currentFade; fadeTo = same ? 0 : 1; fadeDate = Date()
                revision += 1
            }
            return
        }
        if moved {
            M.release(&driver, velocity: velocity)
            UISelectionFeedbackGenerator().selectionChanged()
            return
        }
        let index = M.cardAt(start, scroll: driver.scroll)
        if index >= 0 {
            let delta = M.choose(&driver, index: index)
            audio.play(delta > 0 ? "up" : "pick")
            audio.play("roll")
            UIImpactFeedbackGenerator(style: delta != 0 ? .medium : .light).impactOccurred()
        }
    }
    func accessibilityChoose() {
        M.takeOver(&driver, now: now)
        M.choose(&driver, index: driver.sel >= 0 ? driver.sel : 0)
        revision += 1
    }
    var fadeEnd: Date { fadeDate.addingTimeInterval(0.2) }
}
