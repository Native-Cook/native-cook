import SwiftUI

// Mutable driver stays off published frame state; TimelineView only samples it on the main thread.
final class MorphObjectOnboardingClock: ObservableObject {
    typealias M = MorphObjectOnboardingMotion
    typealias T = MorphObjectOnboardingTheme
    @Published private(set) var fading = false
    var driver = M.Driver()
    private var date: Date?
    private var now = 0.0
    private var target = 0
    private var start = CGPoint.zero
    private var moved = false
    private var fadePage = 0.0
    private var fadeFrom = 0.0
    private var fadeTo = 0.0
    private var fadeStarted: Date?
    private var stopScheduled = false
    private var loopT = 0.0
    private let audio = MorphObjectOnboardingAudio()

    func resetDate() { date = nil }
    func reset() {
        driver = M.Driver()
        date = nil
        now = 0
        target = 0
        loopT = 0
        fadePage = 0
        fadeStarted = nil
        fading = false
        stopScheduled = false
    }
    func prepareAudio(reduced: Bool) {
        if reduced { audio.stop() } else { audio.prepare() }
    }
    func disappear() { audio.stop(); date = nil }
    func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Live {
        if let pinned { return M.Live(ambient: pinned, page: M.pageAt(pinned), arrive: M.arrivalAt(pinned)) }
        if reduced {
            updateFade(at)
            return M.Live(page: fadePage)
        }
        let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
        date = at
        now += dt
        let before = driver.page
        let arrived = M.step(&driver, dt: dt, now: now, auto: auto)
        // The site's recording plays the sounds a touch would make (DemoSound).
        if DemoSound.inAutoPlay && auto && driver.mode == 0 {
            let t = driver.time
            func crossed(_ at: Double) -> Bool { t >= loopT ? (loopT < at && t >= at) : (at > loopT || at <= t) }
            for move in M.moves {
                if crossed(move[0]) { audio.play("morph") }
                if crossed(move[1]) { audio.play("pop") }
            }
            loopT = t
        }
        if arrived >= 0 {
            driver.arriveAt = now
            audio.play("pop")
            audio.buzz(selection: true)
        }
        if driver.mode == 2 && driver.aim < 0 && floor(before + 0.5) != floor(driver.page + 0.5) { audio.buzz(selection: true) }
        let loopArrival = driver.mode == 0 ? M.arrivalAt(driver.time) : -1
        let arrival = driver.arriveAt >= 0 && now - driver.arriveAt < 1 ? now - driver.arriveAt : loopArrival
        let hop = driver.hopAt >= 0 && now - driver.hopAt < 1.2 ? now - driver.hopAt : -1
        return M.Live(ambient: driver.ambient, page: driver.page, arrive: arrival, hop: hop, press: driver.press)
    }
    private func updateFade(_ date: Date) {
        guard let began = fadeStarted else { return }
        let progress = M.clamp01(date.timeIntervalSince(began) / 0.2)
        // Reanimated withTiming's default Easing.inOut(Easing.quad).
        let eased = progress < 0.5 ? 2 * progress * progress : 1 - pow(-2 * progress + 2, 2) / 2
        fadePage = M.mix(fadeFrom, fadeTo, eased)
        if progress >= 1 {
            fadeStarted = nil
            if !stopScheduled {
                stopScheduled = true
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    self.stopScheduled = false
                    if self.fadeStarted == nil { self.fading = false }
                }
            }
        }
    }
    private func reducedGo(_ page: Double) {
        let date = Date()
        updateFade(date)
        fadeFrom = fadePage
        fadeTo = max(0, min(2, page))
        fadeStarted = date
        fading = true
    }
    private func inButton(_ point: CGPoint, padding: Double) -> Bool {
        T.UI.button.insetBy(dx: -padding, dy: -padding).contains(point)
    }
    func begin(_ point: CGPoint, reduced: Bool) {
        start = point
        moved = false
        target = 1
        if inButton(point, padding: 6) { target = 2 }
        else if abs(point.x - T.UI.skip.x + 16) < 34 && abs(point.y - T.UI.skip.y) < 24 { target = 3 }
        else {
            let box = T.Morph.box
            if point.x > box.minX + 40 && point.x < box.maxX - 40 && point.y > box.minY + 30 && point.y < box.maxY - 30 { target = 4 }
        }
        guard !reduced else { return }
        if target == 2 { driver.pressAim = 1; driver.last = now }
        else if target == 1 || target == 4 { M.grab(&driver, now: now) }
    }
    func change(translation: Double, screenTranslation: Double, reduced: Bool) {
        guard !reduced, target == 1 || target == 4 else { return }
        driver.last = now
        if abs(screenTranslation) > 8 { moved = true }
        driver.page = M.band(driver.dragFrom - translation / (T.width * 0.85))
        driver.velocity = 0
    }
    func end(_ point: CGPoint, translation: Double, velocity: Double, reduced: Bool) {
        let touched = target
        let tap = hypot(point.x - start.x, point.y - start.y) < 10 && !moved
        target = 0
        driver.pressAim = 0
        driver.last = now
        if reduced {
            let current = M.roundPage(fadePage)
            if tap && touched == 2 { reducedGo(current + 1 > 2 ? 0 : current + 1) }
            else if tap && touched == 3 { reducedGo(2) }
            else if !tap && (touched == 1 || touched == 4) { reducedGo(current + (translation < 0 ? 1 : -1)) }
            return
        }
        if touched == 2 && tap && inButton(point, padding: 12) {
            let current = M.roundPage(driver.mode == 0 ? driver.page : driver.aim >= 0 ? driver.aim : driver.page)
            M.goTo(&driver, page: current >= 2 ? 0 : current + 1, now: now)
            audio.play("morph")
            audio.buzz()
        } else if touched == 3 && tap {
            M.goTo(&driver, page: 2, now: now, skip: true)
            audio.play("morph")
            audio.buzz()
        } else if (touched == 1 || touched == 4) && !tap {
            let from = M.roundPage(driver.dragFrom)
            M.release(&driver, velocity: -velocity / (T.width * 0.85))
            if driver.aim != from { audio.play("morph") }
        } else if touched == 4 && tap {
            driver.hopAt = now
            driver.aim = M.roundPage(driver.page)
            audio.buzz()
        } else if touched == 1 { driver.aim = M.roundPage(driver.page) }
    }
    func accessibilityNext(reduced: Bool) {
        if reduced {
            let current = M.roundPage(fadePage)
            reducedGo(current + 1 > 2 ? 0 : current + 1)
        } else {
            let current = M.roundPage(driver.page)
            M.goTo(&driver, page: current >= 2 ? 0 : current + 1, now: now)
        }
    }
}
