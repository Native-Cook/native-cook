import Foundation
import SwiftUI

/// The Wax Seal Invite's clock. Every value is a closed-form function of the loop time t
/// (seconds), so t = loop draws the frame of t = 0. Lengths in points, angles in radians.
///
///   0.0 – 1.6   rest: the sealed envelope; a gleam of light slides across the wax (0.2 – 1.4).
///   1.6 – 2.4   a thumb presses the seal (scale 0.95) and pulls it down 22 pt (rubber-banded).
///   2.4 – 3.2   seven cracks run out from the middle, staggered 40 ms.
///   3.2 – 4.2   the seal breaks: its seven pieces pop out and fall, turning, under gravity.
///   4.2 – 5.2   the flap opens about its top edge (ease-in-out), dark as it goes edge-on, its red
///               liner lit once it is open.
///   5.2 – 6.0   the card rises out of the envelope (5.2 – 5.7), comes forward over it and settles,
///               growing to 1.1× with a small overshoot (5.7 – 6.0); the RSVP buttons fade in.
///   6.0 – 7.6   hold.
///   7.6 – 10    in reverse, one step after another: the buttons fade (7.6 – 7.8); the card lifts
///               off the envelope and goes behind it (7.7 – 8.1), then slides down into it
///               (8.1 – 8.6); the flap closes (8.6 – 9.2); only then the pieces fly back onto the
///               closed flap along their own paths (9.2 – 9.75) and the cracks heal
///               (9.65 – 9.9); rest (9.9 – 10).
enum WaxSealInviteMotion {
    static let loop = 10.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// Ease-out with a small overshoot (about 5 %).
    static func easeOutBack(_ x: Double) -> Double {
        let u = clamp01(x) - 1
        let s = 1.2
        return 1 + u * u * ((s + 1) * u + s)
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 4.37) * 43758.5453
        return s - floor(s)
    }

    static func rubber(_ x: Double, _ limit: Double) -> Double {
        limit * (1 - 1 / (1 + max(0, x) / limit))
    }

    /// How long the pieces fall before they are gone (s).
    static let fall = 1.0
    /// The scripted pull on the seal (pt, before the rubber band).
    private static let pull = 70.0
    static let pullLimit = 30.0

    static func loopPose(_ t: Double) -> WaxSealInvitePose {
        let gleam = -1.5 + 3 * easeInOut(seg(t, 0.2, 1.4))
        // Press and pull; let go as the pieces fall, and carried back to rest by the reform.
        let grip = easeInOut(seg(t, 1.6, 2.4))
        let back = easeInOut(seg(t, 9.2, 9.75))
        let press = grip - easeOut(seg(t, 3.2, 3.5)) * grip
        let pulled = rubber(pull * grip, pullLimit) * (1 - back)
        // Cracks out, then healed.
        let crack = easeOut(seg(t, 2.4, 3.1)) * (1 - easeInOut(seg(t, 9.65, 9.9)))
        // The fall, and the same path flown backwards.
        var fallen = 0.0
        if t >= 3.2 && t < 9.2 {
            fallen = min(fall, t - 3.2)
        } else if t >= 9.2 {
            fallen = fall * (1 - back)
        }
        let flap = Double.pi * (easeInOut(seg(t, 4.2, 5.2)) - easeInOut(seg(t, 8.6, 9.2)))
        var card = 0.0
        if t >= 5.2 && t < 7.7 {
            card = easeInOut(seg(t, 5.2, 5.7)) + easeOutBack(seg(t, 5.7, 6.05))
        } else if t >= 7.7 {
            card = 2 - easeInOut(seg(t, 7.7, 8.1)) - easeInOut(seg(t, 8.1, 8.6))
        }
        let buttons = easeOut(seg(t, 5.85, 6.2)) * (1 - easeOut(seg(t, 7.6, 7.8)))
        return WaxSealInvitePose(press: press, pull: pulled, crack: crack, fall: fallen, flap: flap, card: card, buttons: buttons, gleam: gleam)
    }

    /// The invitation open and still (reduced motion).
    static let openPose = WaxSealInvitePose(press: 0, pull: 0, crack: 1, fall: fall, flap: .pi, card: 2, buttons: 1, gleam: -1.5)

    // The cracks: `cracks` polylines of `crackPoints` points from the seal's centre outward, past
    // the wax's edge. Seeded, so they are the same every loop.
    static let cracks = 7
    static let crackPoints = 6

    /// The cracks' points (seal-centred), crack by crack.
    static func makeCracks(_ R: Double) -> [[CGPoint]] {
        (0..<cracks).map { i in
            let fi = Double(i)
            let a = fi / Double(cracks) * 2 * .pi + (hash(fi + 1) - 0.5) * 0.5 - .pi / 2
            return (0..<crackPoints).map { j in
                let f = Double(j) / Double(crackPoints - 1)
                let r = R * 1.3 * f
                let side = j == 0 || j == crackPoints - 1 ? 0 : (hash(fi * 7 + Double(j) + 3) - 0.5) * R * 0.16
                return CGPoint(x: cos(a) * r - sin(a) * side, y: sin(a) * r + cos(a) * side)
            }
        }
    }

    /// Angles of crack i's and crack i + 1's outer ends (the second one past the first).
    private static func span(_ c: [[CGPoint]], _ i: Int) -> (Double, Double) {
        let a = c[i].last ?? .zero
        let b = c[(i + 1) % cracks].last ?? .zero
        let a0 = atan2(a.y, a.x)
        var a1 = atan2(b.y, b.x)
        if a1 < a0 { a1 += 2 * .pi }
        return (a0, a1)
    }

    /// Piece i's middle direction (radians).
    static func pieceAngle(_ c: [[CGPoint]], _ i: Int) -> Double {
        let (a0, a1) = span(c, i)
        return (a0 + a1) / 2
    }

    /// Piece i's outline (seal-centred): from the centre out along crack i, round, back along i + 1.
    static func wedge(_ c: [[CGPoint]], _ i: Int, R: Double) -> Path {
        let a = c[i]
        let b = c[(i + 1) % cracks]
        var p = Path()
        p.move(to: a[0])
        for j in 1..<crackPoints { p.addLine(to: a[j]) }
        let (a0, a1) = span(c, i)
        for k in 1..<8 {
            let ang = a0 + (a1 - a0) * Double(k) / 8
            p.addLine(to: CGPoint(x: cos(ang) * R * 1.5, y: sin(ang) * R * 1.5))
        }
        for j in stride(from: crackPoints - 1, through: 1, by: -1) { p.addLine(to: b[j]) }
        p.closeSubpath()
        return p
    }

    /// The cracks grown to `amount` (each starts 40 ms after the last), seal-centred.
    static func crackPath(_ c: [[CGPoint]], amount: Double) -> Path {
        var p = Path()
        if amount <= 0.001 { return p }
        for i in 0..<cracks {
            let own = min(1, max(0, (amount - Double(i) * 0.05) / (1 - Double(cracks - 1) * 0.05)))
            if own <= 0 { continue }
            // The crack reaches only to the wax's edge (about R).
            let reach = own * Double(crackPoints - 1) * (1 / 1.3) * 0.9
            p.move(to: c[i][0])
            for j in 1..<crackPoints {
                let f = min(1, reach - Double(j - 1))
                if f <= 0 { break }
                let a = c[i][j - 1]
                let b = c[i][j]
                p.addLine(to: CGPoint(x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f))
                if f < 1 { break }
            }
        }
        return p
    }

    /// Piece i after falling `fall` s: offset, rotation (rad) and pivot, seal-centred.
    static func pieceMotion(_ i: Int, mid: Double, fall: Double, R: Double) -> (dx: Double, dy: Double, rot: Double, px: Double, py: Double) {
        let px = cos(mid) * R * 0.55
        let py = sin(mid) * R * 0.55
        if fall <= 0 { return (0, 0, 0, px, py) }
        let fi = Double(i)
        let speed = 45 + hash(fi + 20) * 55
        let vx = cos(mid) * speed
        let vy = sin(mid) * speed * 0.6 - 90 - hash(fi + 21) * 50
        let g = 1500.0
        return (vx * fall, vy * fall + 0.5 * g * fall * fall, (hash(fi + 22) - 0.5) * 7 * fall, px, py)
    }
}

struct WaxSealInvitePose {
    /// Seal pressed (0…1) and pulled down (pt, already rubber-banded).
    var press = 0.0
    var pull = 0.0
    /// Cracks grown (0…1), and seconds the pieces have been falling (0 = whole).
    var crack = 0.0
    var fall = 0.0
    /// The flap's angle (0 closed … π open).
    var flap = 0.0
    /// The card: 0 inside, 1 just clear above the envelope, 2 settled in front.
    var card = 0.0
    /// RSVP buttons shown (0…1).
    var buttons = 0.0
    /// The gleam's place across the seal (−1.5 … 1.5; off the seal at both ends).
    var gleam = -1.5

    /// Whether the seal is drawn as its pieces (cracked or falling) rather than whole.
    var broken: Bool { crack > 0.001 || fall > 0 }
}

/// A cubic Bézier easing curve, like Reanimated's `Easing.bezier`; `linear` and Reanimated's
/// default `Easing.inOut(Easing.quad)` as special cases.
struct WaxSealInviteCurve {
    let x1: Double, y1: Double, x2: Double, y2: Double

    static let easeInOut = WaxSealInviteCurve(x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    static let linear = WaxSealInviteCurve(x1: -1, y1: 0, x2: 0, y2: 0)
    static let quadInOut = WaxSealInviteCurve(x1: -2, y1: 0, x2: 0, y2: 0)

    func callAsFunction(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        if x1 == -1 { return u }
        if x1 == -2 { return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
        var s = u
        for _ in 0..<8 {
            let xs = bez(s, x1, x2) - u
            let d = dbez(s, x1, x2)
            if abs(xs) < 1e-5 || abs(d) < 1e-6 { break }
            s = min(1, max(0, s - xs / d))
        }
        return bez(s, y1, y2)
    }

    private func bez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * s * a + 3 * m * s * s * b + s * s * s
    }

    private func dbez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * a + 6 * m * s * (b - a) + 3 * s * s * (1 - b)
    }
}

/// One value the finger drives, as Reanimated's `withTiming` / `withSpring` would.
struct WaxSealInviteTrack {
    private var from: Double
    private var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var curve = WaxSealInviteCurve.quadInOut
    private var spring: (omega: Double, zeta: Double)?

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    func value(at t: Double) -> Double {
        let u = t - start
        if let spring {
            if u <= 0 { return from }
            let a = from - to
            let z = min(spring.zeta, 0.999)
            let wd = spring.omega * (1 - z * z).squareRoot()
            let b = z * spring.omega * a / wd
            return to + exp(-z * spring.omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
        if duration <= 0 || u >= duration { return to }
        if u <= 0 { return from }
        return from + (to - from) * curve(u / duration)
    }

    func done(at t: Double) -> Bool {
        if let spring { return t - start >= 6 / (max(spring.zeta, 0.1) * spring.omega) }
        return t - start >= duration
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        duration = 0
        spring = nil
    }

    mutating func timing(_ target: Double, duration: Double, curve: WaxSealInviteCurve = .quadInOut, now: Double) {
        from = value(at: now)
        to = target
        start = now
        self.duration = duration
        self.curve = curve
        spring = nil
    }

    /// Reanimated's duration-based spring, approximated by ω = 6.05 / duration.
    mutating func spring(_ target: Double, duration: Double, dampingRatio: Double, now: Double) {
        from = value(at: now)
        to = target
        start = now
        spring = (6.05 / duration, dampingRatio)
    }
}
