import SwiftUI
import UIKit

@MainActor
final class AiFeatureTourClock: ObservableObject {
    typealias M = AiFeatureTourMotion
    private var driver = M.Driver()
    private var now = 0.0
    private var previous: Date?
    private let audio = AiFeatureTourAudio()
    func resetDate() { previous = nil }
    func reset() {
        driver = M.Driver()
        now = 0
        previous = nil
        audio.stop()
    }
    func stop() { resetDate(); audio.stop() }
    func sample(_ date: Date, auto: Bool, pinned: Double?) -> M.Frame {
        if let pinned { return M.frameAt(pinned) }
        let dt = min(max(previous.map { date.timeIntervalSince($0) } ?? 0.016, 0), 0.05)
        previous = date
        now += dt
        driver.step(dt, now, auto)
        return driver.frame(now)
    }
    private func button(_ point: CGPoint) -> Bool {
        let b = AiFeatureTourTheme.UI.button
        return point.x > b.x - 10 && point.x < b.x + b.w + 10
            && point.y > b.y - 10 && point.y < b.y + b.h + 10
    }
    func begin(_ point: CGPoint) {
        driver.takeOver(now)
        if button(point) { driver.pressing = 1; driver.pressAt = now }
    }
    func end(_ point: CGPoint, reduced: Bool) {
        driver.last = now
        if driver.pressing == 1 {
            driver.pressing = 0
            if !button(point) { return }
            audio.play("tap", reduced: reduced)
            if reduced || now - driver.flyAt < M.LOOP - M.T.press { return }
            driver.flyAt = now
            audio.send()
            return
        }
        let rows = AiFeatureTourTheme.UI.rows
        for i in 0..<M.ICONS {
            let ty = rows.y0 + Double(i) * rows.pitch
            if point.y > ty - 70 && point.y < ty + 130 {
                if now - driver.actAt[i] < M.ACT { return }
                driver.actAt[i] = reduced ? now - M.ACT : now
                audio.play("tick", reduced: reduced)
                UISelectionFeedbackGenerator().selectionChanged()
                return
            }
        }
    }
    func accessibilityContinue() {
        driver.takeOver(now)
        driver.flyAt = now
    }
}
