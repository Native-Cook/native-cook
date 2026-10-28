import Foundation
import SwiftUI

/*
 Prism Beam Folder — geometry, the session model and the autoPlay script.

 A session is one press of the folder: τ = seconds since the press. The folder squashes (0 – 0.2),
 a thin prism beam shoots up out of it (0.2 – 0.45) and widens into a cone (0.4 – 1.05). While the
 finger is down an item spawns at the top of the beam every 0.26 s (τᵢ = 0.65 + 0.26 i) and floats
 down through it for 2.3 s, funnelled by the cone, into the folder; each landing bumps the folder
 and ticks the badge. After the release and the last landing the beam narrows to a thin line
 (cs … cs + 0.45), the line falls into the folder (cs + 0.4 … cs + 0.65), the front closes, and at
 cs + 1.05 … cs + 1.35 the badge clears (cs = max(release, 1.05, last landing) + 0.05).

 autoPlay: loop = 8 s, one session pressed at t = 0.8 and held to τ = 2.8 (nine items):
   0.0 – 0.8   rest
   0.8 – 1.85  press, the beam shoots up and widens
   1.45 – 5.83 nine items pour in, landing 3.75 … 5.83 (badge 1 → 9)
   5.88 – 6.68 the beam narrows to a line, falls into the folder, the front closes
   6.93 – 7.23 the badge clears
   7.23 – 8.0 rest (the frame at 8 is the frame at 0)
*/
enum PrismBeamFolderMotion {
    static let loop = 8.0
    /// Loop time of the reduced-motion poster: the beam fully open, items in flight.
    static let reducedAt = 3.8
    /// The autoPlay press, and how long its virtual finger stays down.
    static let autoPress = 0.8
    static let autoHold = 2.8
    static let idleBeforeBack = 2.0

    static let spawn0 = 0.65
    static let gap = 0.26
    static let flight = 2.3
    static let widenEnd = 1.05

    /// Item kinds: 0 peach, 1 cherries, 2 aubergine, 3 chili.
    static let kinds = [0, 2, 1, 3, 0, 1, 2, 0, 3]
    static let lanes = [-0.5, 0.42, -0.08, 0.6, -0.62, 0.2, -0.3, 0.5, 0.02]
    static let turns = [-20.0, 35, 10, -40, 15, -10, 50, -25, 20]
    static let sizes = [55.0, 58, 53, 53, 51, 53, 55, 53, 51]

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    /// 0 before a, 1 after b (safe when a and b are infinite: a close that has not been scheduled).
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        t <= a ? 0 : (t >= b ? 1 : (t - a) / (b - a))
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    /// Ease-out with a small overshoot (about 3 %).
    static func easeOutBack(_ x: Double) -> Double {
        let c1 = 0.8, c3 = c1 + 1
        return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
    }
}

struct PrismBeamFolderGeo {
    var w = 1.0
    var h = 1.0
    var top = 0.0
    var bottom = 0.0
    var cx = 0.5
    var folderCY = 0.5
    /// The foot of the beam, and its full length (to y = -40).
    var originY = 0.5
    var length = 1.0
    var itemStartY = 0.0
    var itemEndY = 0.0

    static let hbFull = 36.0
    static let htFull = 232.0

    init() {}

    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w = w
        self.h = h
        self.top = top
        self.bottom = bottom
        cx = w / 2
        folderCY = (h * 0.64).rounded()
        originY = folderCY - 34
        length = originY + 40
        itemStartY = top + 110
        itemEndY = folderCY - 16
    }

    /// Half width of the full cone at height y.
    func halfWidth(_ y: Double, hb: Double = hbFull, ht: Double = htFull) -> Double {
        let d = max(0, originY - y)
        return hb + (ht - hb) * d / length
    }

    func onFolder(_ x: Double, _ y: Double) -> Bool {
        abs(x - cx) < 52 + 30 && y > folderCY - 50 - 30 && y < folderCY + 42 + 30
    }
}

struct PrismBeamFolderItem {
    var kind = 0
    var x = 0.0
    var y = 0.0
    var size = 48.0
    var scale = 1.0
    var angle = 0.0
    var alpha = 1.0
}

/// Everything one frame draws.
struct PrismBeamFolderFrame {
    var hb = 0.0
    var ht = 0.0
    var tip = 0.0
    var beamAlpha = 0.0
    var glow = 0.0
    var phase = 0.0
    var folderSX = 1.0
    var folderSY = 1.0
    var open = 0.0
    var rippleR = 0.0
    var rippleA = 0.0
    var items: [PrismBeamFolderItem] = []
    var count = 0
    var badge = 0.0
    var saved = 0.0
    var header = 1.0
    /// Session facts the touch layer needs.
    var landed = 0
    var opened = false
    var restAt = Double.infinity
    var closeStart = Double.infinity
}

enum PrismBeamFolderSession {
    typealias M = PrismBeamFolderMotion

    /// Close start: after the release, the widening and the last landing.
    static func closeStart(_ holds: [(Double, Double)]) -> Double {
        guard let last = holds.last, last.1.isFinite else { return .infinity }
        var lastLand = -Double.infinity
        var i = 0
        while true {
            let s = M.spawn0 + M.gap * Double(i)
            if s > last.1 { break }
            if holds.contains(where: { s >= $0.0 && s <= $0.1 }) { lastLand = s + M.flight }
            i += 1
        }
        return max(last.1, M.widenEnd, lastLand) + 0.05
    }

    /// The frame at τ seconds into a session. Pure: the same inputs give the same frame.
    static func frame(_ tau: Double, holds: [(Double, Double)], base: Int, phase: Double,
                      geo g: PrismBeamFolderGeo) -> PrismBeamFolderFrame {
        var f = PrismBeamFolderFrame()
        f.phase = phase
        guard tau >= 0, !holds.isEmpty else { return f }
        let cs = closeStart(holds)
        f.closeStart = cs
        f.restAt = cs + 1.35

        // Beam: shoot up, widen, then narrow to a line and fall into the folder.
        let shoot = M.easeOut(M.seg(tau, 0.2, 0.45))
        let widen = M.easeOutBack(M.seg(tau, 0.4, M.widenEnd))
        let narrow = M.easeInOut(M.seg(tau, cs, cs + 0.45))
        let retract = M.easeIn(M.seg(tau, cs + 0.4, cs + 0.65))
        f.hb = (4 + (PrismBeamFolderGeo.hbFull - 4) * widen) * (1 - 0.94 * narrow)
        f.ht = (12 + (PrismBeamFolderGeo.htFull - 12) * widen) * (1 - 0.985 * narrow)
        f.tip = g.length * shoot * (1 - retract)
        f.beamAlpha = M.seg(tau, 0.2, 0.3) * (1 - M.seg(tau, cs + 0.5, cs + 0.65))
        f.glow = min(1, max(0, widen)) * (1 - narrow)
        f.header = 1 - 0.55 * f.glow
        f.open = M.easeOut(M.seg(tau, 0.2, 0.5)) * (1 - M.easeInOut(M.seg(tau, cs + 0.45, cs + 0.8)))
        f.opened = tau >= 0.2

        // Folder squash at the latest press, then a damped spring back.
        let hs = holds.last(where: { $0.0 <= tau })?.0 ?? 0
        let x = tau - hs
        var a = 0.0
        if x < 0.2 {
            a = M.easeOut(M.seg(x, 0, 0.2))
        } else if x < 1.2 {
            let k = x - 0.2
            a = exp(-6 * k) * cos(14 * k)
        }
        if x < 0.5 {
            let r = x / 0.5
            f.rippleR = 18 + 28 * M.easeOut(r)
            f.rippleA = 0.5 * (1 - r)
        }

        // Items.
        var landed = 0
        var firstLand = -1.0
        var lastLand = -1.0
        var bump = 0.0
        var i = 0
        while true {
            let s = M.spawn0 + M.gap * Double(i)
            if s > tau { break }
            defer { i += 1 }
            guard holds.contains(where: { s >= $0.0 && s <= $0.1 }) else { continue }
            let land = s + M.flight
            if tau >= land {
                landed += 1
                if firstLand < 0 { firstLand = land }
                lastLand = land
                let k = tau - land
                if k < 0.6 { bump += 0.03 * exp(-9 * k) * cos(16 * k) }
                continue
            }
            f.items.append(item(i, p: (tau - s) / M.flight, geo: g))
        }
        f.landed = landed
        f.folderSX = 1 + 0.04 * a + 0.5 * bump
        f.folderSY = 1 - 0.07 * a - bump

        // Badge and count line.
        f.count = base + landed
        let clear = M.easeIn(M.seg(tau, cs + 1.05, cs + 1.35))
        var pop = 0.0
        var shown = 0.0
        if base > 0 {
            pop = 1
            shown = 1
        } else if firstLand >= 0 {
            let k = tau - firstLand
            pop = 1 - exp(-8 * k) * cos(12 * k)
            shown = M.easeOut(M.seg(tau, firstLand, firstLand + 0.25))
        }
        var pulse = 0.0
        if lastLand >= 0, firstLand != lastLand || base > 0 {
            let k = tau - lastLand
            if k < 0.6 { pulse = 0.25 * exp(-9 * k) * sin(14 * k) }
        }
        f.badge = f.count > 0 ? max(0, (pop + pulse) * (1 - clear)) : 0
        f.saved = shown * (1 - M.easeOut(M.seg(tau, cs + 1.05, cs + 1.3)))
        return f
    }

    static func item(_ i: Int, p: Double, geo g: PrismBeamFolderGeo) -> PrismBeamFolderItem {
        let j = i % M.kinds.count
        var it = PrismBeamFolderItem()
        it.kind = M.kinds[j]
        it.size = M.sizes[j]
        let e = 0.55 * p + 0.45 * p * p * p
        it.y = g.itemStartY + (g.itemEndY - g.itemStartY) * e
        let wob = 5 * sin(2 * .pi * (1.1 * p + 0.37 * Double(i)))
        it.x = g.cx + M.lanes[j] * g.halfWidth(it.y) * 0.88 * (1 - 0.5 * p * p * p) + wob
        it.scale = (0.6 + 0.4 * M.easeOut(M.seg(p, 0, 0.12))) * (1 - 0.5 * M.seg(p, 0.8, 1))
        it.alpha = M.seg(p, 0, 0.1)
        let spin = i % 2 == 0 ? 90.0 : -90.0
        it.angle = M.turns[j] + 18 * sin(2 * .pi * (0.6 * p + 0.21 * Double(i))) + spin * p * p * p
        return it
    }

    /// The autoPlay frame at loop time t.
    static func script(_ t: Double, geo: PrismBeamFolderGeo) -> PrismBeamFolderFrame {
        frame(t - M.autoPress, holds: [(0, M.autoHold)], base: 0, phase: t, geo: geo)
    }
}

/// Wall-clock state behind the frames: the autoPlay clock and the session a finger started.
final class PrismBeamFolderSim {
    typealias M = PrismBeamFolderMotion
    typealias S = PrismBeamFolderSession

    private var epoch: Date?
    private var start: Date?
    private var holds: [(Double, Double)] = []
    private var base = 0
    private var holding = false
    private var pending = false
    private var released: Date?
    private var lastLanded = 0
    private var wasOpen = false
    var onFeel: (() -> Void)?

    var inSession: Bool { start != nil }

    func frame(at date: Date, geo: PrismBeamFolderGeo, auto: Bool) -> PrismBeamFolderFrame {
        if epoch == nil { epoch = date }
        guard let start else {
            let t = auto ? date.timeIntervalSince(epoch!).truncatingRemainder(dividingBy: M.loop) : 0
            return S.script(t, geo: geo)
        }
        var tau = date.timeIntervalSince(start)
        var f = S.frame(tau, holds: holds, base: base, phase: date.timeIntervalSince(epoch!), geo: geo)
        if pending && tau >= f.closeStart + 0.65 {
            pending = false
            begin(at: date, base: 0)
            tau = 0
            f = S.frame(0, holds: holds, base: base, phase: date.timeIntervalSince(epoch!), geo: geo)
        }
        // Haptics on the beam opening and on each landing, while a finger drives it.
        if f.opened && !wasOpen { onFeel?() }
        if f.landed > lastLanded { onFeel?() }
        wasOpen = f.opened
        lastLanded = f.landed
        if !holding && !pending, let released, date.timeIntervalSince(released) >= M.idleBeforeBack,
           tau >= f.restAt {
            self.start = nil
            epoch = date
            return S.script(0, geo: geo)
        }
        return f
    }

    private func begin(at date: Date, base: Int) {
        start = date
        holds = [(0, .infinity)]
        self.base = base
        lastLanded = 0
        wasOpen = false
    }

    func press(at date: Date, geo: PrismBeamFolderGeo, auto: Bool) {
        holding = true
        if start == nil {
            let t = auto && epoch != nil ? date.timeIntervalSince(epoch!).truncatingRemainder(dividingBy: M.loop) : -1
            let tau = t - M.autoPress
            let restAt = S.closeStart([(0, M.autoHold)]) + 1.35
            if tau >= 0 && tau < restAt {
                // The running loop becomes the session: its virtual finger lifts now.
                start = date.addingTimeInterval(-tau)
                holds = [(0, min(M.autoHold, tau))]
                base = 0
                let f = S.frame(tau, holds: holds, base: 0, phase: 0, geo: geo)
                lastLanded = f.landed
                wasOpen = f.opened
            } else {
                begin(at: date, base: 0)
                return
            }
        }
        guard let start else { return }
        let tau = date.timeIntervalSince(start)
        let cs = S.closeStart(holds)
        if tau < cs {
            holds.append((tau, .infinity))
        } else if tau < cs + 0.65 {
            pending = true
        } else {
            let f = S.frame(tau, holds: holds, base: base, phase: 0, geo: geo)
            begin(at: date, base: tau < cs + 1.05 ? f.count : 0)
        }
    }

    func release(at date: Date) {
        holding = false
        pending = false
        released = date
        guard let start, let last = holds.last, !last.1.isFinite else { return }
        holds[holds.count - 1].1 = date.timeIntervalSince(start)
    }

    /// Seconds until the screen is back at rest after a release (for pausing without autoPlay).
    func secondsToRest(from date: Date) -> Double {
        guard let start else { return 0 }
        let tau = date.timeIntervalSince(start)
        let rest = S.closeStart(holds) + 1.35
        return max(M.idleBeforeBack, rest - tau)
    }
}
