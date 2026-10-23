import CoreGraphics
import Combine
import Foundation
import UIKit
import simd

struct BadgeVaultGridLayout {
    let width: Double
    let height: Double
    let safeTop: Double
    let safeBottom: Double

    var k: Double { min(1, width / 402) }
    var compact: Bool { height < 760 }
    var pitch: Double { half(196 * k) }
    var headerHeight: Double { safeTop + 125 }
    var footerY: Double { headerHeight + 6 * pitch }
    var footerHeight: Double { 96 + safeBottom }
    var scrollEnd: Double { max(0, footerY + footerHeight - height) }
    var radius: Double { half(50 * k) }
    var badgeY: Double { half(64 * k) }
    var canvasHeight: Double { half(136 * k) }
    var nameY: Double { half(132 * k) }
    var dateY: Double { half(152 * k) }
    var barY: Double { half(176 * k) }
    var heldX: Double { half(width / 2) }
    var heldY: Double { compact ? 170 : 300 }
    var heldR: Double { compact ? 96 : 120 }
    var titleY: Double { compact ? 328 : 496 }
    var titleSize: Double { compact ? 30 : 32 }
    var chipY: Double { compact ? 292 : 456 }
    var earnedY: Double { compact ? 368 : 538 }
    var blurbY: Double { compact ? 392 : 566 }
    var statsY: Double { compact ? 452 : 632 }
    var shareY: Double { height - safeBottom - 72 }

    func column(_ index: Int) -> (x: Double, width: Double) {
        let x = half(16 + (width - 32) * Double(index) / 3)
        let end = half(16 + (width - 32) * Double(index + 1) / 3)
        return (x, end - x)
    }

    func home(_ index: Int, scroll: Double, recede: Double) -> (x: Double, y: Double, r: Double, nameY: Double, scale: Double) {
        let col = column(index % 3)
        let scale = 1 - 0.06 * recede
        let x = half(col.x + col.width / 2)
        let rowY = headerHeight + Double(index / 3) * pitch - scroll
        return (width / 2 + (x - width / 2) * scale,
                height / 2 + (rowY + badgeY - height / 2) * scale,
                radius * scale,
                height / 2 + (rowY + nameY + 9 - height / 2) * scale, scale)
    }

    private func half(_ number: Double) -> Double { (number * 2).rounded() / 2 }
}

struct BadgeVaultGridSpring {
    var x = 0.0
    var v = 0.0
}

struct BadgeVaultGridSpin {
    var x = 0.0
    var v = 0.0
    var target = 0.0
    var friction = 3.8
    var detent = false
    var snapped = true
}

enum BadgeVaultGridMath {
    static let tau = Double.pi * 2
    static func clamp(_ n: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { max(lo, min(hi, n)) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func bezier(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if p <= 0 { return 0 }
        if p >= 1 { return 1 }
        var lo = 0.0
        var hi = 1.0
        var t = p
        for _ in 0..<12 {
            let q = 1 - t
            let x = 3 * q * q * t * x1 + 3 * q * t * t * x2 + t * t * t
            if x < p { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    }

    static func ease(_ p: Double) -> Double { bezier(p, 0.23, 1, 0.32, 1) }

    static func spring(_ s: BadgeVaultGridSpring, _ target: Double, _ dt: Double, _ stiffness: Double, _ damping: Double) -> BadgeVaultGridSpring {
        let a = damping / 2
        let w = sqrt(max(0.001, stiffness - a * a))
        let delta = s.x - target
        let b = (s.v + a * delta) / w
        let attenuation = exp(-a * dt)
        let sn = sin(w * dt)
        let cs = cos(w * dt)
        let x = target + attenuation * (delta * cs + b * sn)
        let v = attenuation * ((b * w - a * delta) * cs - (delta * w + a * b) * sn)
        return abs(x - target) < 0.00005 && abs(v) < 0.0001 ? .init(x: target, v: 0) : .init(x: x, v: v)
    }

    static func springAt(_ seconds: Double, _ stiffness: Double, _ damping: Double, _ velocity: Double = 0) -> Double {
        spring(.init(x: 0, v: velocity), 1, max(0, seconds), stiffness, damping).x
    }

    static func settleTail(_ seconds: Double, _ start: Double, _ end: Double) -> Double {
        let p = clamp((seconds - start) / (end - start))
        return 1 - p * p * (3 - 2 * p)
    }

    static func flightSpring(_ seconds: Double, _ stiffness: Double, _ damping: Double, _ start: Double, _ end: Double) -> Double {
        1 + (springAt(seconds, stiffness, damping) - 1) * settleTail(seconds, start, end)
    }

    static func launchTurn(_ seconds: Double) -> Double {
        let clock = seconds < 0.95 ? 0.95 * pow(max(0, seconds) / 0.95, 1.9) : 0.95 + (seconds - 0.95) * 1.9
        return tau * (1 + (springAt(clock, 17.76, 5.233) - 1) * settleTail(seconds, 1.35, 1.6))
    }

    static func flickSpin(_ x: Double, _ velocity: Double, front: Bool) -> BadgeVaultGridSpin {
        let interval = front ? tau : Double.pi
        let target = ((x + velocity / 3.8) / interval).rounded() * interval
        let friction = velocity * (target - x) > 0 ? clamp(velocity / (target - x), 1.5, 6) : 3.8
        return .init(x: x, v: velocity, target: target, friction: friction, detent: false, snapped: false)
    }

    static func advanceSpin(_ spin: BadgeVaultGridSpin, _ dt: Double) -> BadgeVaultGridSpin {
        var next = spin
        let steps = max(1, Int(ceil(dt * 240)))
        let h = dt / Double(steps)
        for _ in 0..<steps {
            if !next.detent && abs(next.v) > 1.8 {
                let decay = exp(-next.friction * h)
                next.x += next.v * (1 - decay) / next.friction
                next.v *= decay
            } else {
                next.detent = true
                let moved = spring(.init(x: next.x, v: next.v), next.target, h, 85, 13)
                next.x = moved.x
                next.v = moved.v
                if abs(next.x - next.target) < 0.012 && abs(next.v) < 0.07 {
                    next.x = next.target
                    next.v = 0
                    next.snapped = true
                }
            }
        }
        return next
    }

    static func scrollCurve(_ p: Double) -> Double {
        (1 - exp(-5.2 * clamp(p))) / (1 - exp(-5.2))
    }

    static func resistDrag(_ y: Double) -> Double {
        y < 0 ? y * 0.12 : y <= 160 ? y : 160 + (y - 160) * 0.28
    }
}

struct BadgeVaultGridPose {
    var cx = 0.0
    var cy = 0.0
    var r = 1.0
    var tiltX = 0.0
    var tiltY = 0.0
    var spin = 0.0
    var roll = 0.0
    var envX = 0.0
    var envY = 0.0
    var sheen = -1.0
    var fill = 1.0
    var wave = 0.0
    var time = 0.0
    var lift = 0.0
    var present = 0.0
    var socket = 0.0
}

struct BadgeVaultGridCell {
    var have: Int
    var earned: Bool
    var pressAt = -10000.0
    var pressFrom = 0.0
    var pressTo = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var rattleAt = -10000.0
    var progressAt = -10000.0
    var progressFrom: Double
    var unlockAt = -1.0
    var popAt = -10000.0
    var popGain = 0.0
    var unlocked = false

    init(_ badge: BadgeVaultGridBadge) {
        have = badge.initialHave
        earned = badge.earned != nil
        progressFrom = badge.need > 0 ? Double(badge.initialHave) / Double(badge.need) : 1
    }
}

struct BadgeVaultGridCellVisual {
    var pose = BadgeVaultGridPose()
    var flash = 0.0
    var progress = 1.0
    var reveal = 1.0
    var shake = 0.0
}

struct BadgeVaultGridFlood {
    var ox = 0.0
    var oy = 0.0
    var cx = 0.0
    var cy = 0.0
    var r = 1.0
    var spread = 0.0
    var power = 0.0
    var rays = 0.0
    var since = -1.0
    var time = 0.0
}

struct BadgeVaultGridHero {
    var index = -1
    var pose = BadgeVaultGridPose()
    var flood = BadgeVaultGridFlood()
    var recede = 0.0
    var alpha = 0.0
    var age = 0.0
    var details = 0.0
    var nameX = 0.0
    var nameY = 0.0
    var nameScale = 0.0
}

@MainActor final class BadgeVaultGridEngine: ObservableObject {
    @Published private(set) var serial = 0
    var layout: BadgeVaultGridLayout
    var autoPlay: Bool
    var reduced: Bool
    var now = 0.0
    var scrollY = 0.0
    var previousScroll = 0.0
    var velocity = 0.0
    var followers = Array(repeating: BadgeVaultGridSpring(), count: 3)
    var cells = BadgeVaultGridBadge.all.map(BadgeVaultGridCell.init)
    var countFrom = 15
    var countAt = -10000.0
    var visuals = Array(repeating: BadgeVaultGridCellVisual(), count: 18)
    var selected = -1
    var phase = "rest"
    var phaseAt = 0.0
    var pressPose = (r: 1.0, tiltX: 0.0, tiltY: 0.0)
    var launchPose: BadgeVaultGridPose?
    var recedeFrom = 0.0
    var closePose: BadgeVaultGridHero?
    var turn = BadgeVaultGridSpin()
    var turnMode = "launch"
    var turnAt = 0.0
    var turnFrom = 0.0
    var turnTo = 0.0
    var drag = BadgeVaultGridSpring()
    var dragging = false
    var landed = false
    var ripple = (at: -10000.0, x: 0.0, y: 0.0, gain: 0.0)
    var bandAt = -10000.0
    var lastIdleBand = 0.0
    var pillAt = -10000.0
    var pillDown = false
    var pillFrom = 0.0
    var hero = BadgeVaultGridHero()
    var demoTime = 0.0
    var demoCursor = 0
    var demoStarted = false
    var readyAt = -1.0
    var touchAt = -1.0
    var touching = false
    var returningAt = -1.0
    var scrollAt = -1.0
    var scrollFrom = 0.0
    var scrollTo = 0.0
    var scrollDuration = 1500.0
    var inertia = BadgeVaultGridSpring()
    var lastDate: CFTimeInterval?
    private var displayLink: CADisplayLink?

    init(layout: BadgeVaultGridLayout, autoPlay: Bool, reduced: Bool) {
        self.layout = layout
        self.autoPlay = autoPlay
        self.reduced = reduced
        for index in 0..<18 {
            let col = layout.column(index % 3)
            visuals[index].pose = BadgeVaultGridPose(cx: col.width.rounded() / 2,
                                                       cy: layout.badgeY, r: layout.radius,
                                                       fill: cells[index].earned ? 1 : 0,
                                                       present: 1, socket: 1)
            visuals[index].progress = cells[index].progressFrom
            visuals[index].reveal = cells[index].earned ? 1 : 0
        }
    }

    var count: Int { cells.filter(\.earned).count }

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(displayFrame(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastDate = nil
    }

    @objc private func displayFrame(_ link: CADisplayLink) {
        frame(link.timestamp)
    }
    var pillPress: Double {
        pillFrom + (Double(pillDown ? 1 : 0) - pillFrom) * BadgeVaultGridMath.ease((now - pillAt) / 110)
    }

    func frame(_ date: CFTimeInterval) {
        let dtMs = max(0, (date - (lastDate ?? date)) * 1000)
        lastDate = date
        now += dtMs
        if autoPlay && !reduced { advanceDemo(dtMs) }
        if !autoPlay && !reduced && phase == "rest" && now - lastIdleBand >= 6000 {
            bandAt = now
            lastIdleBand = now
        }
        if scrollAt >= 0 {
            let p = (now - scrollAt) / scrollDuration
            scrollY = scrollFrom + (scrollTo - scrollFrom) * BadgeVaultGridMath.scrollCurve(p)
            if p >= 1 { scrollAt = -1 }
        } else if !touching && abs(inertia.v) > 0.1 && selected < 0 {
            let decay = exp(-5.2 * dtMs / 1000)
            scrollY = BadgeVaultGridMath.clamp(scrollY + inertia.v * (1 - decay) / 5.2, 0, layout.scrollEnd)
            inertia.v *= decay
        }
        advanceFollowers(dtMs / 1000)
        advanceHero(min(dtMs / 1000, 0.064))
        for index in 0..<18 { cellFrame(index) }
        serial &+= 1
    }

    private func haptic(_ kind: String) {
        switch kind {
        case "selection": UISelectionFeedbackGenerator().selectionChanged()
        case "medium": UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case "light": UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case "warning": UINotificationFeedbackGenerator().notificationOccurred(.warning)
        case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
        default: break
        }
    }

    private func startScroll(_ to: Double, duration: Double) {
        scrollAt = now
        scrollFrom = scrollY
        scrollTo = to
        scrollDuration = duration
    }

    private func advanceDemo(_ dtMs: Double) {
        if readyAt < 0 { readyAt = now }
        if touchAt >= 0 {
            if touching || now - touchAt < 3500 { return }
            if returningAt < 0 {
                returningAt = now
                action("close")
                startScroll(0, duration: 600)
            }
            if now - returningAt < 850 { return }
            action("reset")
            touchAt = -1
            returningAt = -1
            demoTime = 0
            demoCursor = 0
            demoStarted = true
            return
        }
        if !demoStarted {
            if now - readyAt < 300 { return }
            demoStarted = true
            demoTime = now - readyAt - 300
        } else {
            demoTime += dtMs
        }
        let frameNow = now
        while true {
            while demoCursor < Self.events.count && Double(Self.events[demoCursor].at) <= demoTime {
                let event = Self.events[demoCursor]
                now = frameNow - (demoTime - Double(event.at))
                demoAction(event)
                demoCursor += 1
            }
            if demoTime < 18000 { break }
            demoTime -= 18000
            demoCursor = 0
        }
        now = frameNow
    }

    private static let events: [(at: Int, action: String, index: Int)] = [
        (400, "band", -1), (1300, "press", 4), (1420, "open", 4),
        (3700, "flick", 1), (5100, "close", -1), (6000, "open", 5),
        (6900, "scroll-end", -1), (8700, "pill-in", -1), (8820, "log", -1),
        (11100, "press", 14), (11220, "open", 14), (12900, "slow-turn", -1),
        (14000, "flick", 1), (14900, "close", -1), (15700, "scroll-top", -1),
        (17300, "reset", -1)
    ]

    private func demoAction(_ event: (at: Int, action: String, index: Int)) {
        if event.action == "band" { bandAt = now; return }
        if event.action == "scroll-end" || event.action == "scroll-top" {
            startScroll(event.action == "scroll-end" ? layout.scrollEnd : 0, duration: 1500)
            return
        }
        if event.action == "open" { action("release", index: event.index) }
        if event.action == "log" { action("pill-out") }
        action(event.action, index: event.index,
               x: event.action == "flick" ? 13 : 0.3,
               y: event.action == "close" ? 900 : 0.25)
    }

    func touch(_ down: Bool) {
        touchAt = now
        touching = down
        demoStarted = false
        returningAt = -1
        scrollAt = -1
    }

    private func pressAmount(_ index: Int) -> Double {
        let cell = cells[index]
        let age = now - cell.pressAt
        let p = cell.pressTo > 0 ? BadgeVaultGridMath.ease(age / 110) : BadgeVaultGridMath.springAt(age / 1000, 320, 22)
        return cell.pressFrom + (cell.pressTo - cell.pressFrom) * p
    }

    private func causeRipple(_ index: Int, gain: Double) {
        let home = layout.home(index, scroll: scrollY, recede: 0)
        ripple = (now, home.x, home.y, gain)
    }

    func action(_ kind: String, index: Int = -1, x: Double = 0, y: Double = 0) {
        if kind == "reset" {
            cells = BadgeVaultGridBadge.all.map(BadgeVaultGridCell.init)
            countFrom = 15
            countAt = -10000
            return
        }
        if kind == "pill-in" || kind == "pill-out" {
            pillFrom = pillPress
            pillDown = kind == "pill-in"
            pillAt = now
            if pillDown { haptic("light") }
            return
        }
        if kind == "log" {
            var stagger = 0.0
            for index in 0..<18 {
                let badge = BadgeVaultGridBadge.all[index]
                if cells[index].earned || badge.need == 0 || cells[index].unlockAt >= 0 { continue }
                cells[index].progressFrom = Double(cells[index].have) / Double(badge.need)
                cells[index].have = min(badge.need, cells[index].have + 1)
                cells[index].progressAt = now
                if cells[index].have == badge.need {
                    cells[index].unlockAt = now + 300 + stagger
                    stagger += 250
                }
            }
            return
        }
        if kind == "close" {
            if phase == "rest" || phase == "closing" { return }
            closePose = hero
            recedeFrom = hero.recede
            phase = "closing"
            phaseAt = now
            dragging = false
            drag.v = y
            turnTo = (hero.pose.spin / BadgeVaultGridMath.tau).rounded() * BadgeVaultGridMath.tau
            return
        }
        if kind == "flick" && selected >= 0 && !reduced {
            turn = BadgeVaultGridMath.flickSpin(hero.pose.spin, x, front: index == 1)
            turnMode = "free"
            return
        }
        if kind == "slow-turn" && selected >= 0 && !reduced {
            turnMode = "slow"
            turnAt = now
            turnFrom = hero.pose.spin
            turnTo = ((hero.pose.spin - .pi) / BadgeVaultGridMath.tau).rounded() * BadgeVaultGridMath.tau + .pi
            return
        }
        if index < 0 || index >= 18 || phase != "rest" { return }
        if kind == "press" || kind == "release" {
            cells[index].pressFrom = pressAmount(index)
            cells[index].pressTo = kind == "press" ? 1 : 0
            cells[index].pressAt = now
            if kind == "press" {
                cells[index].fingerX = BadgeVaultGridMath.clamp(x, -1, 1)
                cells[index].fingerY = BadgeVaultGridMath.clamp(y, -1, 1)
            }
            return
        }
        if kind != "open" { return }
        if !cells[index].earned {
            cells[index].rattleAt = now
            haptic("warning")
            return
        }
        let source = visuals[index].pose
        launchPose = source
        pressPose = (source.r / layout.radius, source.tiltX, source.tiltY)
        selected = index
        phase = "opening"
        phaseAt = now
        landed = false
        drag = .init()
        turnMode = "launch"
        turn = .init(x: 0, v: 0, target: BadgeVaultGridMath.tau, friction: 3.8, detent: false, snapped: true)
        causeRipple(index, gain: 1)
        haptic("selection")
    }

    private func advanceFollowers(_ dt: Double) {
        velocity = dt > 0 ? (previousScroll - scrollY) / dt : 0
        previousScroll = scrollY
        let target = reduced ? 0 : BadgeVaultGridMath.clamp(velocity / 2400, -1, 1) * 0.42
        followers[0] = BadgeVaultGridMath.spring(followers[0], target, min(dt, 0.064), 260, 20)
        followers[1] = BadgeVaultGridMath.spring(followers[1], target, min(dt, 0.064), 170, 14)
        followers[2] = BadgeVaultGridMath.spring(followers[2], target, min(dt, 0.064), 110, 10)
    }

    private func cellFrame(_ index: Int) {
        let m = BadgeVaultGridMath.self
        let badge = BadgeVaultGridBadge.all[index]
        var cell = cells[index]
        let home = layout.home(index, scroll: scrollY, recede: hero.recede)
        let press = pressAmount(index)
        let rattle = m.clamp((now - cell.rattleAt) / 460)
        let shake = reduced ? 0 : 7 * sin(5 * .pi * rattle) * pow(1 - rattle, 1.5)
        let unlockClock = cell.unlockAt >= 0 ? m.clamp((now - cell.unlockAt) / (reduced ? 300 : 1150)) : 0
        var fill = cell.earned ? 1 : cell.unlockAt >= 0 ? (reduced ? unlockClock : m.bezier(unlockClock, 0.3, 0.7, 0.3, 1)) : 0
        let didUnlock = unlockClock == 1 && !cell.earned
        let previousCount = didUnlock ? count : 0
        if didUnlock {
            cell.earned = true
            cell.unlocked = true
            cell.popAt = now
            cell.popGain = 0.14
            fill = 1
            causeRipple(index, gain: 1)
            haptic("success")
        }
        let reveal = badge.earned != nil ? 1 : cell.earned ? m.clamp((now - cell.popAt) / 260) : 0
        let goalProgress = badge.need > 0 ? Double(cell.have) / Double(badge.need) : 1
        let progress = cell.progressFrom + (goalProgress - cell.progressFrom) * m.ease((now - cell.progressAt) / 300)
        let h = Double((index * 73 + 19) % 101) / 101
        let swing = reduced ? 0 : followers[index % 3].x * (1 - h) + followers[(index + 1) % 3].x * h
        var sheen = -1.0
        var rippleX = 0.0
        var rippleY = 0.0
        if !reduced {
            let diagonal = (home.x + home.y) / 900
            let sweep = ((now - bandAt) / 1000 - diagonal) / 0.7
            if sweep >= 0 && sweep <= 1 { sheen = sweep }
            if abs(velocity) > 500 { sheen = m.clamp(home.y / layout.height) }
            let dx = home.x - ripple.x
            let dy = home.y - ripple.y
            let range = sqrt(dx * dx + dy * dy)
            let travelled = (now - ripple.at) * 1.1
            let separation = range - travelled
            if abs(separation) < 210 && travelled >= 0 {
                let strength = exp(-0.5 * separation * separation / 4900) * exp(-range / 1000) * ripple.gain
                rippleX = -dy / max(1, range) * 0.30 * strength
                rippleY = dx / max(1, range) * 0.30 * strength
                sheen = m.clamp((travelled - range + 105) / 210)
            }
            if cell.popGain > 0 && now - cell.popAt < 450 { sheen = m.clamp((now - cell.popAt) / 450) }
        }
        let popAge = (now - cell.popAt) / 1000
        let pop = reduced || popAge > 1.5 ? 1 : 1 + cell.popGain * (1 - m.springAt(popAge, cell.popGain > 0 ? 300 : 320, cell.popGain > 0 ? 14 : 18))
        let rising = !reduced && !cell.earned && cell.unlockAt >= 0 ? m.ease((now - cell.unlockAt) / 300) : 0
        let size = cell.earned ? pop : 1 + 0.14 * rising
        let lift = reduced ? 0 : cell.earned && cell.popGain > 0 ? 0.5 * m.clamp((pop - 1) / 0.14) : 0.5 * rising
        let col = layout.column(index % 3)
        let pose = BadgeVaultGridPose(cx: col.x + col.width.rounded() / 2,
                                      cy: layout.headerHeight + Double(index / 3) * layout.pitch - scrollY + layout.badgeY,
                                      r: min(57 * layout.k, layout.radius * (1 - 0.05 * press) * size),
                                      tiltX: swing - cell.fingerY * 0.24 * press + rippleX,
                                      tiltY: cell.fingerX * 0.24 * press + shake / 7 * 0.2 + rippleY,
                                      spin: 0, roll: shake / 7 * 0.06,
                                      envX: m.clamp((home.x - layout.width / 2) / (layout.width / 2), -1, 1),
                                      envY: m.clamp((home.y - layout.height / 2) / (layout.height / 2), -1, 1),
                                      sheen: sheen, fill: fill,
                                      wave: reduced || cell.unlockAt < 0 || now < cell.unlockAt || fill >= 1 ? 0 : 1 - m.clamp((unlockClock - 0.85) / 0.15),
                                      time: cell.unlockAt >= 0 && unlockClock < 1 ? unlockClock * 1.15 : 0,
                                      lift: lift, present: selected == index ? 0 : 1, socket: 1)
        visuals[index] = .init(pose: pose, flash: 1 - m.clamp((now - cell.rattleAt) / 500),
                               progress: progress, reveal: reveal, shake: shake)
        cells[index] = cell
        if didUnlock {
            countFrom = previousCount
            countAt = now
        }
    }

    private func advanceHero(_ dt: Double) {
        let m = BadgeVaultGridMath.self
        if selected < 0 { hero = BadgeVaultGridHero(); return }
        let age = now - phaseAt
        let seconds = age / 1000
        let closing = phase == "closing"
        let from = closePose
        let recess = m.bezier(age / (reduced ? 200 : 500), 0.32, 0.72, 0, 1)
        let recede = closing ? recedeFrom * (1 - recess) : recess
        let home = layout.home(selected, scroll: scrollY, recede: recede)
        if !dragging && !closing { drag = m.spring(drag, 0, dt, 240, 24) }
        let dragY = reduced ? 0 : drag.x
        let shrink = 1 - 0.3 * m.clamp(dragY / 300)
        let xp = reduced ? 1 : m.flightSpring(seconds, 80, 15, 0.35, 0.45)
        let yp = reduced ? 1 : m.flightSpring(seconds, 42, 11, 0.50, 0.62)
        let rp = reduced ? 1 : m.flightSpring(seconds, 52, 8.8, 0.72, 0.9)
        var cx = m.lerp(home.x, layout.heldX, xp)
        var cy = m.lerp(home.y, layout.heldY, yp) + dragY
        var r = m.lerp(home.r * pressPose.r, layout.heldR, rp) * shrink
        var tiltX = reduced ? 0 : pressPose.tiltX * (1 - m.clamp(yp)) + 0.25 * sin(.pi * m.clamp(age / 600))
        var tiltY = reduced ? 0 : pressPose.tiltY * (1 - m.clamp(xp))
        var nameX = cx
        var nameY = m.lerp(home.nameY, layout.titleY + 19, yp) + dragY * 0.3
        var nameScale = m.lerp(13 / layout.titleSize * home.scale, 1, rp)
        var lift = reduced ? 1 : m.ease(age / 180)
        var details = 1 - m.clamp(dragY / 300)
        var alpha = reduced ? m.clamp(age / 200) : 1
        var spread = reduced ? alpha : m.ease((age - 60) / 700)
        var power = details
        var spin = 0.0
        var driftX = 0.0
        var driftY = 0.0
        if !reduced && !closing {
            if turnMode == "launch" { turn.x = m.launchTurn(seconds) }
            else if turnMode == "free" {
                let snapped = turn.snapped
                turn = m.advanceSpin(turn, dt)
                if !snapped && turn.snapped { haptic("light") }
            } else if turnMode == "slow" {
                turn.x = m.lerp(turnFrom, turnTo, m.clamp((now - turnAt) / 700))
                if now - turnAt >= 700 { turnMode = "drag"; haptic("light") }
            }
            spin = turn.x
            if age > 900 {
                let sway = min(1, (age - 900) / 500)
                let phase = (age - 900) / 5500 * m.tau
                driftX = sin(phase) * sway
                driftY = cos(phase) * sway
                tiltX += driftX * 0.09
                tiltY += driftY * 0.09
            }
        }
        if !closing && age >= (reduced ? 200 : 620) && !landed { landed = true; haptic("medium") }
        if !closing && age >= 1600 { phase = "held" }
        if closing, let from {
            let p = reduced ? 0 : m.flightSpring(seconds, 75, 15, 0.42, 0.56)
            cx = m.lerp(from.pose.cx, home.x, p)
            let impulse = reduced ? 0 : m.spring(.init(x: 0, v: drag.v), 0, seconds, 75, 15).x * m.settleTail(seconds, 0.42, 0.56)
            cy = m.lerp(from.pose.cy, home.y, p) + impulse
            r = m.lerp(from.pose.r, home.r, p) * (reduced ? 1 : 1 - 0.04 * m.ease((age - 500) / 60))
            spin = reduced ? 0 : m.lerp(from.pose.spin, turnTo, p)
            tiltX = reduced ? 0 : from.pose.tiltX * (1 - m.clamp(p)) - 0.3 * sin(.pi * m.clamp(age / 560))
            tiltY = from.pose.tiltY * (1 - m.clamp(p))
            nameX = m.lerp(from.nameX, home.x, p)
            nameY = m.lerp(from.nameY, home.nameY, p)
            nameScale = m.lerp(from.nameScale, 13 / layout.titleSize * home.scale, p)
            details = from.details * (1 - m.clamp(age / 140))
            spread = from.flood.spread * (1 - m.ease(age / 420))
            power = from.flood.power * (1 - m.ease(age / 420))
            lift = from.pose.lift * (1 - m.ease((age - 380) / 180))
            alpha = reduced ? 1 - m.clamp(age / 200) : 1
            if age >= (reduced ? 200 : 560) {
                cells[selected].popAt = now
                cells[selected].popGain = -0.04
                causeRipple(selected, gain: 0.5)
                haptic("light")
                selected = -1
                phase = "rest"
                closePose = nil
                hero = BadgeVaultGridHero()
                return
            }
        }
        let sourceSheen = closing ? from?.pose.sheen ?? -1 : launchPose?.sheen ?? -1
        let band = reduced ? -1 : closing ? (sourceSheen < 0 ? -1 : sourceSheen + age / 500) : (age - 620) / 500
        let envX = m.clamp((cx - layout.width / 2) / (layout.width / 2), -1, 1)
        let envY = m.clamp((cy - layout.height / 2) / (layout.height / 2), -1, 1)
        let envReturn = reduced ? 0 : m.flightSpring(seconds, 75, 15, 0.42, 0.56)
        let pose = BadgeVaultGridPose(cx: cx, cy: cy, r: r, tiltX: tiltX, tiltY: tiltY,
                                      spin: spin,
                                      roll: closing && from != nil ? from!.pose.roll * (1 - envReturn) : (launchPose?.roll ?? 0) * (1 - m.clamp(xp)),
                                      envX: closing && from != nil ? m.lerp(from!.pose.envX, envX, envReturn) : m.clamp(envX + 0.35 * driftX, -1, 1),
                                      envY: closing && from != nil ? m.lerp(from!.pose.envY, envY, envReturn) : m.clamp(envY + 0.35 * driftY, -1, 1),
                                      sheen: band >= 0 && band <= 1 ? band : !closing && age < 100 ? sourceSheen : -1,
                                      fill: 1, lift: lift, present: 1)
        let flood = BadgeVaultGridFlood(ox: home.x, oy: home.y, cx: cx, cy: cy, r: r,
                                        spread: spread, power: power,
                                        rays: closing && from != nil ? from!.flood.rays * (1 - m.ease(age / 420)) : landed ? m.ease((age - 620) / 400) : 0,
                                        since: reduced || !landed ? -1 : closing && from != nil ? from!.flood.since + seconds : max(0, age - 620) / 1000,
                                        time: reduced ? 0 : closing && from != nil ? from!.flood.time + seconds : seconds)
        hero = .init(index: selected, pose: pose, flood: flood, recede: recede,
                     alpha: alpha, age: closing ? from?.age ?? 0 : age,
                     details: details, nameX: nameX, nameY: nameY, nameScale: nameScale)
    }
}
