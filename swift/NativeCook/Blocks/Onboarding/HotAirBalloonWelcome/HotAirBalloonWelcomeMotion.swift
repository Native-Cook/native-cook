import Foundation
import UIKit

/// Hot Air Balloon Welcome — the autoPlay script. Each onboarding step is an altitude: 0 on the
/// ground (step 1), 1 and 2 aloft. Every value is a closed-form function of the loop time t, and
/// periodic motion uses whole multiples of the loop's frequency, so t = loop equals t = 0.
enum HotAirBalloonWelcomeData {
    static let loop = 10.0
    static let steps = 3
    /// Gores round the envelope; the colour pattern repeats every `pattern` gores.
    static let gores = 12
    static let pattern = 4
    /// The envelope turns one pattern (4 gores = 120°) per loop.
    static let patternAngle = 2 * Double.pi * Double(pattern) / Double(gores)

    static let titles = ["Plan trips that float", "Rise above the noise", "See the whole route"]
    static let bodies = [
        "Drift gathers flights, stays and day plans into one calm itinerary.",
        "We watch prices and delays, and only nudge you when it matters.",
        "Every leg on one offline map, from takeoff to landing.",
    ]
}

func hotAirBalloonWelcomeClamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

func hotAirBalloonWelcomeSeg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    hotAirBalloonWelcomeClamp01((t - a) / (b - a))
}

func hotAirBalloonWelcomeEaseInOut(_ x: Double) -> Double {
    let t = hotAirBalloonWelcomeClamp01(x)
    return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

func hotAirBalloonWelcomeEaseOut(_ x: Double) -> Double {
    let t = hotAirBalloonWelcomeClamp01(x)
    return 1 - pow(1 - t, 3)
}

func hotAirBalloonWelcomeHash(_ n: Double) -> Double {
    let x = sin(n * 12.9898 + 78.233) * 43758.5453
    return x - floor(x)
}

struct HotAirBalloonWelcomePose {
    /// Altitude in steps (0 = ground).
    var alt: Double
    /// Burner heat 0..1 (flame, bloom, the glow inside the envelope, the swell).
    var heat: Double
    /// The step text (continuous index, rolls between steps).
    var step: Double
    /// Envelope turn (radians).
    var turn: Double
    /// Time the flicker and sway are drawn at (s).
    var time: Double

    /// A burst of the burner: roars in 0.15 s, holds, dies away in 0.35 s.
    private static func burst(_ t: Double, _ a: Double, _ b: Double, _ level: Double) -> Double {
        level * hotAirBalloonWelcomeEaseOut(hotAirBalloonWelcomeSeg(t, a, a + 0.15))
            * (1 - hotAirBalloonWelcomeEaseInOut(hotAirBalloonWelcomeSeg(t, b, b + 0.35)))
    }

    static func loop(_ t: Double) -> HotAirBalloonWelcomePose {
        typealias D = HotAirBalloonWelcomeData
        let e = hotAirBalloonWelcomeEaseInOut
        let s = hotAirBalloonWelcomeSeg
        let alt = e(s(t, 2.0, 4.0)) + e(s(t, 4.8, 7.0)) - 2 * e(s(t, 7.0, 8.8))
        let heat = burst(t, 1.6, 2.6, 1) + burst(t, 4.6, 5.4, 1) + burst(t, 8.0, 8.3, 0.55)
        let step = e(s(t, 2.4, 2.9)) + e(s(t, 5.2, 5.7)) - 2 * e(s(t, 7.6, 8.4))
        return HotAirBalloonWelcomePose(alt: alt, heat: heat, step: step, turn: D.patternAngle * t / D.loop, time: t)
    }

    /// Flame height factor: three sines (whole multiples of the loop) for a living flicker.
    static func flicker(_ time: Double) -> Double {
        let w = 2 * Double.pi / HotAirBalloonWelcomeData.loop
        return 1 + 0.09 * sin(w * 37 * time) + 0.06 * sin(w * 61 * time + 1.3) + 0.04 * sin(w * 89 * time + 2.1)
    }

    /// Aloft the balloon sways a little (degrees) and bobs (pt); on the ground it is still.
    static func sway(_ time: Double, _ alt: Double) -> (Double, Double) {
        let w = 2 * Double.pi / HotAirBalloonWelcomeData.loop
        let k = hotAirBalloonWelcomeClamp01(alt)
        return (1.3 * k * sin(w * time + 0.4), 2.2 * k * sin(w * 2 * time))
    }
}

/// cubic-bezier(0.77, 0, 0.175, 1), the ease-in-out of the return to rest.
func hotAirBalloonWelcomeBezier(_ x: Double) -> Double {
    let x1 = 0.77, x2 = 0.175
    func bx(_ t: Double) -> Double { 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t }
    func by(_ t: Double) -> Double { 3 * (1 - t) * t * t + t * t * t }
    var lo = 0.0, hi = 1.0, t = x
    for _ in 0..<24 {
        t = (lo + hi) / 2
        if bx(t) < x { lo = t } else { hi = t }
    }
    return by(t)
}

/// Reanimated's default timing curve, Easing.inOut(Easing.quad).
func hotAirBalloonWelcomeQuadInOut(_ x: Double) -> Double {
    let t = hotAirBalloonWelcomeClamp01(x)
    return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
}

/// A value that follows its target with a spring (duration-based, as Reanimated's), or a timed ease.
struct HotAirBalloonWelcomeSpring {
    var x: Double
    var v = 0.0
    var target: Double
    var omega = 10.0
    var zeta = 1.0
    var springing = false
    var tweenFrom = 0.0
    var tweenAt = 0.0
    var tweenFor = 0.0
    var tweenBezier = false

    init(_ x: Double) {
        self.x = x
        target = x
    }

    mutating func spring(to target: Double, duration: Double, damping: Double, velocity: Double = 0) {
        self.target = target
        zeta = damping
        omega = (damping >= 0.99 ? 5.8 : 4 / damping) / duration
        v = velocity
        tweenFor = 0
        springing = true
    }

    mutating func tween(to target: Double, duration: Double, now: Double, bezier: Bool = false) {
        self.target = target
        tweenFrom = x
        tweenAt = now
        tweenFor = duration
        tweenBezier = bezier
        springing = false
        v = 0
    }

    mutating func set(_ value: Double) {
        x = value
        target = value
        v = 0
        tweenFor = 0
        springing = false
    }

    var settled: Bool { tweenFor == 0 && !springing }

    /// Returns true when a tween ends on this step.
    mutating func step(_ dt: Double, now: Double) -> Bool {
        if tweenFor > 0 {
            let k = hotAirBalloonWelcomeClamp01((now - tweenAt) / tweenFor)
            let e = tweenBezier ? hotAirBalloonWelcomeBezier(k) : hotAirBalloonWelcomeQuadInOut(k)
            x = tweenFrom + (target - tweenFrom) * e
            if k >= 1 {
                tweenFor = 0
                x = target
                return true
            }
            return false
        }
        guard springing else { return false }
        var left = dt
        while left > 0 {
            let h = min(left, 1.0 / 240)
            let a = -omega * omega * (x - target) - 2 * zeta * omega * v
            v += a * h
            x += v * h
            left -= h
        }
        if abs(x - target) < 0.0005 && abs(v) < 0.005 {
            x = target
            v = 0
            springing = false
        }
        return false
    }
}

/// The loop clock and the finger's state, stepped once a frame from the timeline.
@MainActor
final class HotAirBalloonWelcomeModel {
    private typealias D = HotAirBalloonWelcomeData

    private(set) var auto = false
    private(set) var reduced = false
    private var configured = false
    private var t = 0.0
    private var manual = true
    private var clock = 0.0
    private var resumeAt = -1.0
    private var alt = HotAirBalloonWelcomeSpring(0)
    private var heat = HotAirBalloonWelcomeSpring(0)
    private var turn = HotAirBalloonWelcomeSpring(0)
    private var holding = false
    private var dragFrom = 0.0
    private var mtime = 0.0
    private var returning = false
    private var lastStep = 0
    private var last: Date?
    private var cached = HotAirBalloonWelcomePose.loop(0)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    func configure(auto: Bool, reduced: Bool) {
        guard !configured || auto != self.auto || reduced != self.reduced else { return }
        configured = true
        self.auto = auto
        self.reduced = reduced
        manual = !auto
        t = 0
        cached = HotAirBalloonWelcomePose.loop(0)
    }

    func pose(at date: Date) -> HotAirBalloonWelcomePose {
        if let last, date == last { return cached }
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let dt = min(max(raw, 0), 0.05)
        clock += dt
        if !manual {
            t = (t + dt).truncatingRemainder(dividingBy: D.loop)
            cached = HotAirBalloonWelcomePose.loop(t)
            return cached
        }
        mtime = (mtime + dt).truncatingRemainder(dividingBy: D.loop * 6)
        if holding {
            let h = min(1, heat.x + dt * 6.5)
            heat.set(h)
            if !reduced {
                let over = max(0, alt.x - Double(D.steps - 1))
                alt.set(alt.x + dt * 0.6 * h / (1 + over * 6))
            }
        }
        if resumeAt > 0 && clock >= resumeAt && !holding {
            resumeAt = -1
            backToRest()
        }
        _ = heat.step(dt, now: clock)
        _ = turn.step(dt, now: clock)
        if alt.step(dt, now: clock) && returning {
            returning = false
            if auto {
                t = 0
                turn.set(0)
                manual = false
                cached = HotAirBalloonWelcomePose.loop(0)
                return cached
            }
        }
        // A selection tick as a finger brings the balloon to each step.
        let st = Int(alt.x.rounded())
        if st != lastStep {
            lastStep = st
            tick.selectionChanged()
        }
        cached = HotAirBalloonWelcomePose(alt: alt.x, heat: heat.x, step: max(0, min(Double(D.steps - 1), alt.x)), turn: turn.x, time: mtime)
        return cached
    }

    var moving: Bool { !manual || holding || resumeAt > 0 || returning || !alt.settled || !heat.settled || !turn.settled }

    private func backToRest() {
        returning = true
        let k = (turn.x / D.patternAngle).rounded()
        turn.tween(to: k * D.patternAngle, duration: 0.9, now: clock, bezier: true)
        heat.tween(to: 0, duration: 0.3, now: clock)
        alt.tween(to: 0, duration: 0.9, now: clock, bezier: true)
    }

    private func settle(_ velocity: Double) {
        let target = max(0, min(Double(D.steps - 1), (alt.x + velocity * 0.2).rounded()))
        if reduced { alt.tween(to: target, duration: 0.2, now: clock) } else { alt.spring(to: target, duration: 0.7, damping: 0.85, velocity: velocity) }
    }

    private func takeOver() {
        resumeAt = -1
        returning = false
        if !manual {
            let p = HotAirBalloonWelcomePose.loop(t)
            alt.set(p.alt)
            heat.set(p.heat)
            heat.tween(to: 0, duration: 0.35, now: clock)
            turn.set(p.turn)
            mtime = t
            manual = true
        } else {
            alt.set(alt.x)
            turn.set(turn.x)
        }
        lastStep = Int(alt.x.rounded())
    }

    func burnerDown() {
        takeOver()
        holding = true
        if reduced {
            let next = min(Double(D.steps - 1), alt.x.rounded() + 1)
            alt.tween(to: next, duration: 0.2, now: clock)
        }
        light.impactOccurred()
    }

    func burnerUp() {
        holding = false
        heat.tween(to: 0, duration: 0.35, now: clock)
        if !reduced { settle(0.6) }
        if auto { resumeAt = clock + 2 }
    }

    func skyBegan() {
        takeOver()
        dragFrom = alt.x
    }

    func skyMoved(_ dy: Double, rise: Double) {
        var a = dragFrom - dy / rise
        if a < 0 { a = -(-a / (1 - a)) * 0.35 }
        let top = Double(D.steps - 1)
        if a > top {
            let over = a - top
            a = top + over / (1 + over) * 0.35
        }
        alt.set(reduced ? max(0, min(top, a)).rounded() : a)
    }

    func skyEnded(velocityY: Double, rise: Double) {
        settle(-velocityY / rise)
        if auto { resumeAt = clock + 2 }
    }
}
