import Foundation
import UIKit

/// Constellation Steps Welcome — the sky's script and the three constellations. Every value is a
/// closed-form function of the loop time t; twinkles use whole multiples of the loop's frequency.
///
/// Constellations are laid out upright around the hero centre (pt, y down, before scaling); in the
/// sky, constellation k sits k steps of `stepAngle` round the pole, so turning the sky by k steps
/// brings it to the centre.
enum ConstellationStepsWelcomeData {
    static let loop = 10.0
    static let steps = 3
    static let stepAngle = 48 * Double.pi / 180

    struct Constellation {
        let name: String
        /// Stars: x, y, size (pt).
        let stars: [(Double, Double, Double)]
        /// Lines between stars, in drawing order.
        let edges: [(Int, Int)]
        /// The brightest star, labelled.
        let alpha: Int
        let label: String
    }

    static let constellations = [
        // Deneb, Sadr, η, Albireo, Gienah, left wing tip, δ, right wing tip.
        Constellation(
            name: "Cygnus",
            stars: [(0, -112, 3.4), (2, -22, 2.6), (6, 42, 1.9), (12, 108, 2.4), (-78, -38, 2.4), (-124, -2, 1.8), (74, -48, 2.2), (118, -24, 1.7)],
            edges: [(0, 1), (1, 2), (2, 3), (1, 4), (4, 5), (1, 6), (6, 7)],
            alpha: 0, label: "Deneb"
        ),
        // Caph, Schedar, Navi, Ruchbah, Segin.
        Constellation(
            name: "Cassiopeia",
            stars: [(112, -30, 2.4), (58, 30, 3.0), (2, -14, 2.7), (-56, 38, 2.4), (-110, -8, 2.0)],
            edges: [(0, 1), (1, 2), (2, 3), (3, 4)],
            alpha: 1, label: "Schedar"
        ),
        // Meissa, Betelgeuse, Bellatrix, Alnitak, Alnilam, Mintaka, Saiph, Rigel.
        Constellation(
            name: "Orion",
            stars: [(0, -122, 1.7), (-62, -86, 3.4), (56, -78, 2.6), (-24, 2, 2.4), (0, -6, 2.5), (24, -14, 2.3), (-46, 98, 2.3), (62, 104, 3.2)],
            edges: [(0, 1), (0, 2), (1, 3), (2, 5), (3, 4), (4, 5), (3, 6), (5, 7)],
            alpha: 7, label: "Rigel"
        ),
    ]

    static let titles = ["Meet the Swan", "Find the Queen", "Follow the Hunter"]
    static let bodies = [
        "Cygnus flies down the Milky Way. Its brightest star, Deneb, marks the tail.",
        "Five bright stars draw Cassiopeia's W, circling the pole all year.",
        "Orion's belt of three stars points the way to the brightest star of all.",
    ]
    static let captions = ["CYGNUS · HIGH IN THE EAST", "CASSIOPEIA · NORTH, ALL NIGHT", "ORION · RISING AFTER 22:00"]
}

func constellationStepsWelcomeClamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

func constellationStepsWelcomeSeg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    constellationStepsWelcomeClamp01((t - a) / (b - a))
}

func constellationStepsWelcomeEaseInOut(_ x: Double) -> Double {
    let t = constellationStepsWelcomeClamp01(x)
    return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

struct ConstellationStepsWelcomePose {
    /// Sky turn round the pole (radians).
    var theta: Double
    /// How far each constellation's lines are drawn (0..1).
    var drawn: [Double]
    /// Lines and outlines fade with this (the return home).
    var fade: Double
    /// The step text (continuous index).
    var step: Double
    /// Twinkle time (s).
    var time: Double

    static func loop(_ t: Double) -> ConstellationStepsWelcomePose {
        typealias D = ConstellationStepsWelcomeData
        let e = constellationStepsWelcomeEaseInOut
        let s = constellationStepsWelcomeSeg
        let theta = D.stepAngle * (e(s(t, 3.6, 4.4)) + e(s(t, 6.2, 7.0)) - 2 * e(s(t, 8.4, 9.4)))
        let drawn = [s(t, 1.6, 3.6), s(t, 4.4, 6.2), s(t, 7.0, 8.4)]
        let step = e(s(t, 3.7, 4.3)) + e(s(t, 6.3, 6.9)) - 2 * e(s(t, 8.5, 9.3))
        return ConstellationStepsWelcomePose(theta: theta, drawn: drawn, fade: 1 - e(s(t, 8.4, 9.2)), step: step, time: t)
    }
}

/// cubic-bezier(0.77, 0, 0.175, 1), the ease-in-out of the return to rest.
func constellationStepsWelcomeBezier(_ x: Double) -> Double {
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

/// Easing.inOut(Easing.quad), Reanimated's default timing curve.
func constellationStepsWelcomeQuadInOut(_ x: Double) -> Double {
    let t = constellationStepsWelcomeClamp01(x)
    return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
}

/// A value that follows its target with a spring (duration-based, as Reanimated's), or a timed ease.
struct ConstellationStepsWelcomeSpring {
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
            let k = constellationStepsWelcomeClamp01((now - tweenAt) / tweenFor)
            let e = tweenBezier ? constellationStepsWelcomeBezier(k) : constellationStepsWelcomeQuadInOut(k)
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
        if abs(x - target) < 0.0002 && abs(v) < 0.002 {
            x = target
            v = 0
            springing = false
        }
        return false
    }
}

/// The loop clock and the finger's state, stepped once a frame from the timeline.
@MainActor
final class ConstellationStepsWelcomeModel {
    private typealias D = ConstellationStepsWelcomeData

    private(set) var auto = false
    private(set) var reduced = false
    private var configured = false
    private var t = 0.0
    private var manual = true
    private var clock = 0.0
    private var resumeAt = -1.0
    private var mtime = 0.0
    private var theta = ConstellationStepsWelcomeSpring(0)
    private var drawn = [ConstellationStepsWelcomeSpring(0), ConstellationStepsWelcomeSpring(0), ConstellationStepsWelcomeSpring(0)]
    private var fade = ConstellationStepsWelcomeSpring(1)
    private var dragFrom = 0.0
    private var returning = false
    private var lastStep = 0
    private var last: Date?
    private var cached = ConstellationStepsWelcomePose.loop(0)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    func configure(auto: Bool, reduced: Bool) {
        guard !configured || auto != self.auto || reduced != self.reduced else { return }
        configured = true
        self.auto = auto
        self.reduced = reduced
        manual = !auto
        t = 0
        // Reduced motion shows step 1 drawn.
        drawn[0].set(reduced ? 1 : 0)
        cached = manual ? manualPose() : ConstellationStepsWelcomePose.loop(0)
    }

    private func manualPose() -> ConstellationStepsWelcomePose {
        ConstellationStepsWelcomePose(
            theta: theta.x,
            drawn: drawn.map(\.x),
            fade: fade.x,
            step: max(0, min(Double(D.steps - 1), theta.x / D.stepAngle)),
            time: mtime
        )
    }

    func pose(at date: Date) -> ConstellationStepsWelcomePose {
        if let last, date == last { return cached }
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let dt = min(max(raw, 0), 0.05)
        clock += dt
        if !manual {
            t = (t + dt).truncatingRemainder(dividingBy: D.loop)
            cached = ConstellationStepsWelcomePose.loop(t)
            return cached
        }
        mtime = (mtime + dt).truncatingRemainder(dividingBy: D.loop * 6)
        if resumeAt > 0 && clock >= resumeAt {
            resumeAt = -1
            backToRest()
        }
        for k in drawn.indices { _ = drawn[k].step(dt, now: clock) }
        _ = fade.step(dt, now: clock)
        if theta.step(dt, now: clock) && returning {
            returning = false
            if auto {
                for k in drawn.indices { drawn[k].set(0) }
                fade.set(1)
                t = 0
                manual = false
                cached = ConstellationStepsWelcomePose.loop(0)
                return cached
            }
        }
        let st = Int((theta.x / D.stepAngle).rounded())
        if st != lastStep {
            lastStep = st
            tick.selectionChanged()
        }
        cached = manualPose()
        return cached
    }

    var moving: Bool { !manual || resumeAt > 0 || returning || !theta.settled || !fade.settled || drawn.contains { !$0.settled } }

    private func drawStep(_ k: Int) {
        if drawn[k].x >= 0.999 { return }
        drawn[k].tween(to: 1, duration: reduced ? 0.25 : 1.4, now: clock)
    }

    private func settleOn(_ k: Int, velocity: Double) {
        if reduced {
            theta.tween(to: Double(k) * D.stepAngle, duration: 0.25, now: clock)
        } else {
            theta.spring(to: Double(k) * D.stepAngle, duration: 0.8, damping: 0.85, velocity: velocity)
        }
        drawStep(k)
    }

    private func backToRest() {
        returning = true
        fade.tween(to: 0, duration: 0.7, now: clock)
        theta.tween(to: 0, duration: 0.9, now: clock, bezier: true)
    }

    private func takeOver() {
        resumeAt = -1
        returning = false
        if !manual {
            let p = ConstellationStepsWelcomePose.loop(t)
            theta.set(p.theta)
            for k in drawn.indices { drawn[k].set(p.drawn[k]) }
            fade.set(p.fade)
            mtime = t
            manual = true
        } else {
            theta.set(theta.x)
        }
        if fade.x < 1 { fade.tween(to: 1, duration: 0.3, now: clock) }
        lastStep = Int((theta.x / D.stepAngle).rounded())
    }

    func swipeBegan() {
        takeOver()
        dragFrom = theta.x
    }

    func swipeMoved(_ dx: Double, radius: Double) {
        // In steps, rubber-banding to at most 0.35 of a step past either end.
        var u = (dragFrom - dx / radius) / D.stepAngle
        if u < 0 { u = -(-u / (1 - u) * 0.35) }
        let top = Double(D.steps - 1)
        if u > top {
            let over = u - top
            u = top + over / (1 + over) * 0.35
        }
        if !reduced { theta.set(u * D.stepAngle) }
    }

    func swipeEnded(_ dx: Double, velocityX: Double, radius: Double) {
        let v = -velocityX / radius
        let raw = reduced ? dragFrom - dx / radius : theta.x + v * 0.2
        let k = max(0, min(D.steps - 1, Int((raw / D.stepAngle).rounded())))
        settleOn(k, velocity: v)
        if auto { resumeAt = clock + 2 }
    }

    func next() {
        light.impactOccurred()
        takeOver()
        let now = Int((theta.x / D.stepAngle).rounded())
        settleOn(now >= D.steps - 1 ? 0 : now + 1, velocity: 0)
        if auto { resumeAt = clock + 2 + 1.2 }
    }
}
