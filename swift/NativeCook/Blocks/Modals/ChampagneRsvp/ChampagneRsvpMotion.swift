import SwiftUI

// The reference uses full-screen points, with safe-area insets only in makeLayout.
enum ChampagneRsvpMotion {
    static let loop = 10.0
    static let restLean = 0.05
    static let meet = asin(min(0.9, (1.7 - 0.84) / (2 * 2.62)))
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func clinkLeanAt(_ tau: Double) -> Double {
        if tau < 0 { return -restLean }
        let k = tau / 1.2
        if k >= 1 { return -restLean }
        return -restLean + (meet + restLean) * (1 - easeInOut(k))
            - 0.035 * sin(.pi * min(1, k / 0.12))
    }
    struct Frame {
        var lean = -ChampagneRsvpMotion.restLean
        var clink = 0.0, burst = 0.0, phase = 0.0
        var done = false
        var finger = 0.0, fingerPress = 0.0
    }
    static func at(_ t: Double) -> Frame {
        var f = Frame()
        if t >= 1.1 && t < 1.6 {
            let k = seg(t, 1.1, 1.6)
            f.lean = -restLean + (meet + restLean) * k * k
        } else if t >= 1.6 { f.lean = clinkLeanAt(t - 1.6) }
        f.clink = t > 1.6 && t < 2.5 ? seg(t, 1.6, 2.5) : 0
        f.burst = t > 1.6 && t < 3 ? seg(t, 1.6, 3) : 0
        f.done = t >= 1.75 && t < 8
        f.finger = easeOut(seg(t, 0.85, 0.98)) * (1 - easeOut(seg(t, 1.14, 1.3)))
        f.fingerPress = easeOut(seg(t, 0.98, 1.08)) * (1 - easeOut(seg(t, 1.12, 1.26)))
        f.phase = t / loop * 6
        return f
    }
}

struct ChampagneRsvpLayout {
    let w: Double, h: Double
    let venueTop: Double, namesTop: Double, dateTop: Double
    let heroS: Double, feetY: Double, lineTop: Double, ctaTop: Double, linkTop: Double
    var left: Double { w / 2 - 0.85 * heroS }
    var right: Double { w / 2 + 0.85 * heroS }
    var stage: CGRect { CGRect(x: w / 2 - heroS * 2.4, y: feetY - heroS * 2.9, width: heroS * 4.8, height: heroS * 3) }
    init(_ size: CGSize, _ top: Double, _ bottom: Double) {
        w = size.width; h = size.height
        linkTop = h - max(bottom, 16) - 4 - 44
        ctaTop = linkTop - 4 - 56
        lineTop = ctaTop - 32 - 22
        venueTop = top + 32
        namesTop = venueTop + 16 + 8
        dateTop = namesTop + 48 + 4
        let room0 = dateTop + 22 + 24
        let room1 = lineTop - 24
        heroS = min(w * 0.26, (room1 - room0) / 3, 104)
        feetY = room1 - 8
    }
}

enum ChampagneRsvpTheme {
    static let page = Color(red: 250 / 255, green: 243 / 255, blue: 241 / 255)
    static let ink = Color(red: 42 / 255, green: 31 / 255, blue: 29 / 255)
    static let mute = Color(red: 143 / 255, green: 127 / 255, blue: 122 / 255)
    static let gold = Color(red: 180 / 255, green: 137 / 255, blue: 63 / 255)
    static let ctaInk = Color(red: 252 / 255, green: 248 / 255, blue: 246 / 255)
    static let idleLine = "Will you raise a glass with us?"
    static let doneLine = "See you there, Ana. Dancing till late."
    static let ease = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.26)
}
