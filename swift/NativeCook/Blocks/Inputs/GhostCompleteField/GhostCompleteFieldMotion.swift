import SwiftUI

/*
 Ghost Complete Field: the script and the clock of the demo.

 The loop is a pure function of the loop time `t` (seconds, 0 ..< loop). It produces the same
 pose a person's typing produces, so the field never knows which of the two drives it. Positions
 are glyph counts and caret stops, never points: the field turns them into geometry.
*/
enum GhostCompleteFieldMotion {
    static let loop: Double = 10
    static let idle: Double = 2
    static let returnLen: Double = 0.6
    static let tFilled: Double = 8.2

    static let ccTyped = "theo@"
    static let ccDomain = "postbox.co"
    static let ccFull = ccTyped + ccDomain
    static let ccChips = ["postbox.co", "northmail.com"]
    static let bodyTyped1 = "Hi Mara, tha"
    static let bodyGhost1 = "nks for the notes \u{2014} "
    static let bodyTyped2 = "can we "
    static let bodyGhost2 = "walk through them Thursday?"
    static let bodyFull = bodyTyped1 + bodyGhost1 + bodyTyped2 + bodyGhost2

    private static let b1 = bodyTyped1.utf16.count
    private static let b2 = b1 + bodyGhost1.utf16.count
    private static let b3 = b2 + bodyTyped2.utf16.count
    private static let b4 = bodyFull.utf16.count
    static let bodyGhosts: [(Int, Int)] = [(b1, b2), (b3, b4)]
    static let ccGhosts: [(Int, Int)] = [(ccTyped.utf16.count, ccFull.utf16.count)]

    private static let keyGap = 0.07
    private static let ccTap = 0.55
    private static let ccType = 0.75
    private static let chipsAt = 1.2
    private static let pickAt = 1.85
    private static let pickLen = 0.55
    private static let bodyTap = 2.55
    private static let type1 = 2.75
    private static var type1End: Double { type1 + Double(b1 - 1) * keyGap }
    private static var ghost1: Double { type1End + 0.26 }
    private static var keycap1: Double { ghost1 + 0.38 }
    private static let keyTap = 4.8
    private static let accept1 = 4.85
    private static let type2 = 5.55
    private static var type2End: Double { type2 + Double(b3 - b2 - 1) * keyGap }
    private static var ghost2: Double { type2End + 0.26 }
    private static var keycap2: Double { ghost2 + 0.36 }
    private static let swipeIn = 7.05
    private static let swipeAt = 7.25
    private static let release = 7.75
    private static let clearAt = 8.5
    private static let clearLen = 0.35

    static let wordGap = 0.07
    static let wordS = 0.28
    static let wordBlur = 5.0
    static let dissolveS = 0.2
    static let dissolveBlur = 4.0
    static let sweepS = 0.55
    static let sweepBand = 48.0
    static let jumpS = 0.32
    static let jumpBack = 0.9
    static let keycapS = 0.28
    static let debounceS = 0.24
    static let swipeDist = 180.0
    static let swipeAccept = 0.45
    static let demoSwipe = 150.0
    private static let demoSwipeShare = 0.8
    static let pickS = pickLen
    static let chipGap = 0.07
    static let chipS = 0.26

    struct Pose {
        var n = 0, run = -1
        var appear = 0.0, fade = 1.0, sweep = 0.0
        var caretFrom = 0, caretTo = 0
        var caretMix = 1.0, caret = 0.0, keycap = 0.0, keycapPress = 0.0, ink = 1.0, hint = 1.0
    }

    struct ChipsPose { var show = 0.0, pick = -1, take = 0.0 }
    struct DotPose { var target = 0, alpha = 0.0, scale = 1.0, travel = 0.0 }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double, _ c: Double) -> Double {
        let u = x - 1
        return 1 + (c + 1) * u * u * u + c * u * u
    }

    private static func keys(_ t: Double, _ at: Double, _ gap: Double, _ most: Int) -> Int {
        if t < at { return 0 }
        return min(most, Int(floor((t - at) / gap + 1e-6)) + 1)
    }

    private static func blink(_ since: Double) -> Double {
        since < 0.5 ? 1 : clamp01(0.5 + 1.6 * cos((2 * .pi * (since - 0.5)) / 1.06))
    }

    private static func inkAt(_ t: Double) -> Double {
        t >= clearAt + clearLen ? 1 : 1 - seg(t, clearAt, clearAt + clearLen)
    }

    static func bodyCount(_ t: Double) -> Int {
        if t >= clearAt + clearLen { return 0 }
        if t >= release + 0.2 { return b4 }
        if t >= accept1 + sweepS { return b2 + keys(t, type2, keyGap, b3 - b2) }
        return keys(t, type1, keyGap, b1)
    }

    static func ccCount(_ t: Double) -> Int {
        if t >= clearAt + clearLen { return 0 }
        if t >= pickAt + pickLen { return ccFull.utf16.count }
        return keys(t, ccType, keyGap, ccTyped.utf16.count)
    }

    static func autoBody(_ t: Double) -> Pose {
        var p = Pose()
        let n = bodyCount(t)
        let accept1End = accept1 + sweepS
        let accept2End = release + 0.2
        p.n = n
        if t >= ghost1 && t < accept1End {
            p.run = 0
            p.appear = t - ghost1
            p.sweep = easeInOutCubic(seg(t, accept1, accept1End))
            p.keycap = easeOutBack(seg(t, keycap1, keycap1 + keycapS), 1.6) * (1 - seg(t, accept1, accept1 + 0.14))
            p.keycapPress = sin(.pi * seg(t, keyTap - 0.04, keyTap + 0.14))
        } else if t >= ghost2 && t < accept2End {
            p.run = 1
            p.appear = t - ghost2
            let drag = demoSwipeShare * easeInOutCubic(seg(t, swipeAt, release))
            p.sweep = drag + (1 - demoSwipeShare) * easeOutCubic(seg(t, release, accept2End))
            p.keycap = easeOutBack(seg(t, keycap2, keycap2 + keycapS), 1.6) * (1 - seg(t, release, release + 0.14))
        }

        p.caretFrom = n
        p.caretTo = n
        p.caretMix = 1
        if t >= accept1 && t < accept1End {
            p.caretFrom = b1
            p.caretTo = b2
            p.caretMix = easeOutBack(seg(t, accept1, accept1 + jumpS), jumpBack)
        } else if t >= release && t < clearAt + clearLen {
            p.caretFrom = b3
            p.caretTo = b4
            p.caretMix = easeOutBack(seg(t, release, release + jumpS), jumpBack)
        }

        var last = bodyTap
        if t >= type1 { last = min(t, type1End) }
        if t >= accept1 { last = min(t, accept1 + jumpS) }
        if t >= type2 { last = min(t, type2End) }
        if t >= swipeAt { last = min(t, release + jumpS) }
        p.caret = seg(t, bodyTap, bodyTap + 0.12) * (1 - seg(t, clearAt - 0.1, clearAt)) * blink(t - last)
        p.ink = inkAt(t)
        p.hint = t < type1 ? 1 : seg(t, clearAt + clearLen, clearAt + clearLen + 0.3)
        return p
    }

    static func autoCc(_ t: Double) -> Pose {
        var p = Pose()
        let n = ccCount(t)
        let typed = ccTyped.utf16.count
        let pickEnd = pickAt + pickLen
        let picking = t >= pickAt && t < pickEnd
        p.n = n
        p.caretFrom = n
        p.caretTo = n
        p.caretMix = 1
        if picking {
            p.caretFrom = typed
            p.caretTo = ccFull.utf16.count
            p.caretMix = easeOutBack(seg(t, pickAt + 0.08, pickAt + 0.08 + jumpS), jumpBack)
        }
        var last = ccTap
        if t >= ccType { last = min(t, ccType + Double(typed - 1) * keyGap) }
        if t >= pickAt { last = t }
        p.caret = seg(t, ccTap, ccTap + 0.12) * (1 - seg(t, bodyTap - 0.1, bodyTap)) * blink(t - last)
        p.ink = inkAt(t)
        p.hint = t < ccType ? 1 : seg(t, clearAt + clearLen, clearAt + clearLen + 0.3)
        p.run = picking ? 0 : -1
        p.appear = 9
        p.sweep = easeInOutCubic(seg(t, pickAt + 0.08, pickEnd))
        return p
    }

    static func autoChips(_ t: Double) -> ChipsPose {
        if t < chipsAt || t >= pickAt + pickLen { return ChipsPose() }
        return ChipsPose(show: t - chipsAt, pick: t >= pickAt ? 0 : -1, take: seg(t, pickAt, pickAt + pickLen))
    }

    static func autoSend(_ t: Double) -> Double {
        easeOutCubic(seg(t, type1, type1 + 0.3)) * (1 - seg(t, clearAt, clearAt + clearLen))
    }

    static func autoDot(_ t: Double) -> DotPose {
        if t >= swipeIn - 0.02 {
            return DotPose(
                target: 4,
                alpha: seg(t, swipeIn, swipeIn + 0.16) * (1 - seg(t, release, release + 0.2)),
                scale: 1 - 0.16 * seg(t, swipeAt - 0.08, swipeAt) * (1 - seg(t, release - 0.02, release + 0.1)),
                travel: easeInOutCubic(seg(t, swipeAt, release))
            )
        }
        var pose = DotPose()
        for (k, at) in [ccTap, pickAt, bodyTap, keyTap].enumerated() {
            let a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36))
            if a > pose.alpha {
                pose.alpha = a
                pose.target = k
                pose.scale = 1 - 0.22 * sin(.pi * seg(t, at - 0.06, at + 0.16))
            }
        }
        return pose
    }
}

// MARK: - Tweens for real input

enum GhostCompleteFieldEase {
    case linear, outCubic, inOutCubic, outBack(Double)

    func callAsFunction(_ x: Double) -> Double {
        switch self {
        case .linear: return x
        case .outCubic: return GhostCompleteFieldMotion.easeOutCubic(x)
        case .inOutCubic: return GhostCompleteFieldMotion.easeInOutCubic(x)
        case .outBack(let c): return GhostCompleteFieldMotion.easeOutBack(x, c)
        }
    }
}

/// A value that moves over time and is read at any instant, so no frame writes state.
struct GhostCompleteFieldTween {
    private var from: Double
    private var target: Double
    private var start = 0.0
    private var duration = 0.0
    private var ease = GhostCompleteFieldEase.linear
    private var spring: (omega: Double, zeta: Double, velocity: Double)?

    init(_ value: Double = 0) { from = value; target = value }

    func value(_ now: Double) -> Double {
        if let s = spring {
            let e = now - start
            if e <= 0 { return from }
            let wd = s.omega * sqrt(1 - s.zeta * s.zeta)
            let a = from - target
            let b = (s.velocity + s.zeta * s.omega * a) / wd
            return target + exp(-s.zeta * s.omega * e) * (a * cos(wd * e) + b * sin(wd * e))
        }
        if duration <= 0 { return now < start ? from : target }
        if now <= start { return from }
        if now >= start + duration { return target }
        return from + (target - from) * ease((now - start) / duration)
    }

    mutating func set(_ v: Double) { from = v; target = v; duration = 0; start = 0; spring = nil }

    mutating func to(_ v: Double, _ seconds: Double, _ curve: GhostCompleteFieldEase = .linear, now: Double, delay: Double = 0) {
        from = value(now)
        target = v
        start = now + delay
        duration = seconds
        ease = curve
        spring = nil
    }

    /// An underdamped spring back, with the finger's speed.
    mutating func springTo(_ v: Double, velocity: Double, stiffness: Double, damping: Double, now: Double) {
        from = value(now)
        target = v
        start = now
        let omega = sqrt(stiffness)
        spring = (omega, damping / (2 * omega), velocity)
    }
}

// MARK: - Colour

struct GhostCompleteFieldRGB {
    let r: Double, g: Double, b: Double, a: Double

    init(_ hex: UInt32, _ alpha: Double = 1) {
        r = Double((hex >> 16) & 255) / 255
        g = Double((hex >> 8) & 255) / 255
        b = Double(hex & 255) / 255
        a = alpha
    }

    init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b; a = 1 }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }

    /// Reanimated's interpolateColor mixes in gamma 2.2.
    func mix(_ o: GhostCompleteFieldRGB, _ t: Double) -> GhostCompleteFieldRGB {
        func m(_ x: Double, _ y: Double) -> Double {
            pow(pow(x, 2.2) + (pow(y, 2.2) - pow(x, 2.2)) * t, 1 / 2.2)
        }
        return GhostCompleteFieldRGB(r: m(r, o.r), g: m(g, o.g), b: m(b, o.b))
    }
}
