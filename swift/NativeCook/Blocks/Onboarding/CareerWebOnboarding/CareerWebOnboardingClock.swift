import SwiftUI
import UIKit

// The driver is owned by the UI thread. Frame samples never publish view state.
final class CareerWebOnboardingClock: ObservableObject {
    typealias M = CareerWebOnboardingMotion
    typealias Theme = CareerWebOnboardingTheme
    typealias Art = CareerWebOnboardingArt
    @Published private var revision = 0
    var driver = M.Driver()
    private var date: Date?
    private var now = 0.0
    private var live = Art.Live()
    private var target = 0, hitLink = -1
    private var moved = false
    private var loopT = 0.0
    private let audio = CareerWebOnboardingAudio()
    func prepare() { audio.prepare() }
    func resetDate() { date = nil }
    func stop() { date = nil; audio.stop() }
    func reset() { driver = M.Driver(); live = Art.Live(); date = nil; now = 0; loopT = 0; target = 0; revision += 1 }

    func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> (M.Frame, Art.Live) {
        let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
        date = at; now += dt
        if let pinned { driver.mode = 0; driver.t = pinned; driver.amb = pinned }
        let frame = M.frameAt(driver.t)
        let grow = M.stepDriver(&driver, pinned != nil || reduced ? 0 : dt, now, auto && !reduced && pinned == nil, frame.grow)
        // The site's recording plays the sounds a touch would make (DemoSound).
        if DemoSound.inAutoPlay && auto && !reduced && pinned == nil && driver.mode == 0 {
            let t = driver.t
            func crossed(_ at: Double) -> Bool { t >= loopT ? (loopT < at && t >= at) : (at > loopT || at <= t) }
            if crossed(M.T.pick) { audio.play("attach", reduced: false) }
            if crossed(M.T.tug[1]) || crossed(M.T.ungrow[0]) { audio.play("pluck", reduced: false) }
            loopT = t
        }
        for i in 0..<7 {
            let home = M.nodeHome(i, driver.t, driver.amb)
            live.px[i] = home.x + driver.ox[i]; live.py[i] = home.y + driver.oy[i]
        }
        for l in M.links.indices {
            let ring = driver.mode == 0 ? frame.tugVib * (l >= Theme.bars.count ? 7 : 3) * M.linkOn(l, grow) : 0
            live.vib[l] = M.vibration(driver, l, now) + ring
        }
        for p in 0..<3 {
            let elapsed = now - driver.pickAt[p]
            live.pick[p] = driver.pickAt[p] >= 0 && elapsed < 0.7 ? elapsed / 0.7 : p == 1 && driver.mode == 0 ? frame.pick : -1
        }
        let shoot = driver.shootAt >= 0 ? (now - driver.shootAt) / 0.5 : -1
        live.grow = grow; live.hold = driver.hold; live.press = driver.press
        live.shoot = shoot >= 0 && shoot < 1 ? shoot : -1
        return (M.frameAt(reduced ? 0 : driver.t), live)
    }
    private func reference(_ point: CGPoint, _ size: CGSize) -> CGPoint {
        let scale = size.width / Theme.refW, dy = size.height - Theme.refH * scale
        return CGPoint(x: point.x / scale, y: (point.y - dy) / scale)
    }
    private func segmentDistance(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> Double {
        let dx = b.x - a.x, dy = b.y - a.y
        let squared = dx * dx + dy * dy
        let u = max(0, min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / (squared == 0 ? 1 : squared)))
        return hypot(p.x - a.x - dx * u, p.y - a.y - dy * u)
    }
    func begin(_ location: CGPoint, size: CGSize, reduced: Bool) {
        let point = reference(location, size)
        let x = point.x, y = point.y, b = Theme.button
        target = 0; moved = false; hitLink = -1
        if x > b.minX - 6 && x < b.maxX + 6 && y > b.minY - 6 && y < b.maxY + 6 { target = 3 }
        else {
            var best = -1, bestDistance = 1e9
            for i in 0..<7 {
                let distance = hypot(x - live.px[i], y - live.py[i])
                if distance < Theme.nodes[i].r + 6 && distance < bestDistance { best = i; bestDistance = distance }
            }
            if best >= 0 {
                target = 1
                if !reduced {
                    M.touched(&driver, now); driver.hold = best; driver.fx = x; driver.fy = y
                    driver.gdx = x - live.px[best]; driver.gdy = y - live.py[best]
                }
            } else {
                for l in M.links.indices {
                    if M.linkOn(l, live.grow) < 0.9 { continue }
                    let a = M.links[l][0], b = M.links[l][1]
                    if segmentDistance(point, CGPoint(x: live.px[a], y: live.py[a]), CGPoint(x: live.px[b], y: live.py[b])) < 16 {
                        target = 2; hitLink = l; break
                    }
                }
            }
        }
        if !reduced {
            if target == 3 { M.touched(&driver, now); driver.pressAim = 1 }
            if target == 2 { M.touched(&driver, now) }
        }
        revision += 1
    }
    func change(_ location: CGPoint, translation: CGPoint, size: CGSize, reduced: Bool) {
        guard !reduced else { return }
        driver.last = now
        if hypot(translation.x, translation.y) > 8 { moved = true }
        if target == 1 {
            let point = reference(location, size); driver.fx = point.x; driver.fy = point.y
        }
    }
    func end(velocity: CGVector, size: CGSize, reduced: Bool) {
        let tg = target, tap = !moved, held = driver.hold
        target = 0; driver.pressAim = 0; driver.last = now; driver.hold = -1
        if tg == 1 && held >= 0 {
            if tap && held < 3 {
                let on = (driver.grow[held] >= 0 ? driver.grow[held] : 0) > 0.5
                if reduced {
                    driver.grow[held] = on ? 0 : 1; driver.growAim[held] = driver.grow[held]
                } else {
                    if driver.grow[held] < 0 { driver.grow[held] = on ? 1 : 0 }
                    driver.growAim[held] = on ? 0 : 1; driver.pickAt[held] = now
                }
                audio.play(on ? "pluck" : "attach", reduced: reduced)
                UISelectionFeedbackGenerator().selectionChanged()
            } else if !tap && !reduced {
                let scale = size.width / Theme.refW
                driver.vx[held] = velocity.dx / scale; driver.vy[held] = velocity.dy / scale
                let speed = min(1, hypot(driver.vx[held], driver.vy[held]) / 1400)
                for l in M.links.indices where M.links[l].contains(held) { M.pluck(&driver, l, 5 + 9 * speed, now) }
                if speed > 0.25 { audio.play("pluck", reduced: reduced); UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            }
        } else if tg == 2 && tap && hitLink >= 0 && !reduced {
            M.pluck(&driver, hitLink, 11, now); audio.play("pluck", reduced: reduced)
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else if tg == 3 && tap {
            driver.shootAt = now; UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
        revision += 1
    }
    func accessibilityGrow() {
        M.touched(&driver, now)
        let on = (driver.grow[0] >= 0 ? driver.grow[0] : 0) > 0.5
        for p in Theme.fits.indices {
            if driver.grow[p] < 0 { driver.grow[p] = on ? 1 : 0 }
            driver.growAim[p] = on ? 0 : 1
        }
        revision += 1
    }
}
