import Foundation
import CoreGraphics

/// Layout, timeline and poses of the Postcard Stamp Cancel, plus the small animation tracks the
/// finger drives. Every frame of the loop is a pure function of the loop time.
enum PostcardStampCancelMotion {
    /// Loop period in seconds.
    static let loop = 9.0

    /// The design box (393 × 759); the screen scales it to fit.
    static let designW = 393.0
    static let designH = 759.0

    /// The postcard (writing side up), in design points.
    static let card = CGRect(x: 24, y: 116, width: 345, height: 230)
    /// The ink of the cancellation lives in this box on the card (card points).
    static let inkBox = CGRect(x: 196, y: 6, width: 146, height: 104)
    /// Centre of the date ring, in ink-box points; the wavy bars run to its right, over the stamp.
    static let ring = (x: 56.0, y: 52.0, r: 33.0, inner: 22.0)
    /// The duplex handstamp's rubber block and knob (design points, centred on the tool).
    static let tool = (w: 112.0, h: 72.0, knob: 26.0)
    /// The ink pad's centre: the tool rests here.
    static let pad = (x: 116.0, y: 522.0, r: 70.0)
    /// Where the tool's centre sits when it prints (the middle of the ring and bars).
    static let target = CGPoint(x: card.minX + inkBox.minX + 76, y: card.minY + inkBox.minY + ring.y)

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// A damped wobble starting at t0: 0 before it, settling to 0 after about `len` seconds.
    static func kick(_ t: Double, _ t0: Double, _ len: Double, _ freq: Double) -> Double {
        if t < t0 || t > t0 + len { return 0 }
        let u = t - t0
        return exp(-u * 5 / len) * sin(u * freq) * (1 - seg(t, t0 + len * 0.7, t0 + len))
    }

    struct Pose {
        /// Tool centre (design points) and height above the paper, 0 (resting) … 1 (carried).
        var x: Double
        var y: Double
        var h: Double
        /// Squash of the rubber while it presses (0 … 1).
        var squash: Double
        /// Ink transferred (0 … 1) and its opacity.
        var ink: Double
        var fade: Double
        /// Card flip in degrees (0 writing side, 180 photo side, 360 back) and its tilt at the press.
        var flip: Double
        var tilt: Double
        /// The trip counter: 0 shows 12, 1 shows 13 (fractional while rolling).
        var count: Double
    }

    /// Rest 0–1.4 s; lift 1.4–1.75 s and carry over the stamp 1.6–2.4 s; press 2.4–3.2 s; lift
    /// 3.2–3.55 s; carry home 3.6–4.5 s and set down 4.5–4.85 s; the card turns to its photo and
    /// back as a fresh card 5.8–7.6 s; rest.
    static func autoPose(_ t: Double) -> Pose {
        let go = easeInOutCubic(seg(t, 1.6, 2.4))
        let back = easeInOutCubic(seg(t, 3.6, 4.5))
        let u = go * (1 - back)
        let arc = sin(Double.pi * (go < 1 ? go : back)) * 46
        let x = pad.x + (target.x - pad.x) * u
        let y = pad.y + (target.y - pad.y) * u - arc
        var h = easeOutCubic(seg(t, 1.4, 1.75))
        h += 0.1 * sin(Double.pi * seg(t, 2.4, 2.52))
        h -= easeInOutCubic(seg(t, 2.5, 2.72))
        h += easeOutCubic(seg(t, 3.2, 3.55))
        h -= easeInOutCubic(seg(t, 4.5, 4.85))
        let squash = sin(Double.pi * seg(t, 2.72, 3.2)) * 0.9 + 0.35 * sin(Double.pi * seg(t, 4.85, 5.1))
        let ink = easeOutCubic(seg(t, 2.72, 3.15))
        let fade = t < 6.5 ? 1.0 : 0.0
        let turn = easeInOutCubic(seg(t, 5.8, 6.5)) + easeInOutCubic(seg(t, 6.9, 7.6))
        let tilt = -1.6 * kick(t, 2.72, 1.1, 16)
        let count = easeInOutCubic(seg(t, 3.6, 3.95)) * (1 - easeInOutCubic(seg(t, 6.55, 6.85)))
        return Pose(x: x, y: y, h: h, squash: squash, ink: ink, fade: fade, flip: 180 * turn, tilt: tilt, count: count)
    }

    /// Reduced motion: the card stamped, the tool back on its pad.
    static func stillPose() -> Pose {
        Pose(x: pad.x, y: pad.y, h: 0, squash: 0, ink: 1, fade: 1, flip: 0, tilt: 0, count: 1)
    }

    // MARK: Tracks (the finger's animations, evaluated against the clock)

    enum Curve {
        case linear, inOutQuad, outQuad
        case bezier(Double, Double, Double, Double)

        func y(_ x: Double) -> Double {
            let x = clamp01(x)
            switch self {
            case .linear: return x
            case .inOutQuad: return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
            case .outQuad: return 1 - (1 - x) * (1 - x)
            case let .bezier(x1, y1, x2, y2):
                // Solve x(s) = x for s by Newton steps, then return y(s).
                func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
                func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
                var s = x
                for _ in 0..<8 {
                    let d = (bx(s + 1e-4) - bx(s - 1e-4)) / 2e-4
                    if abs(d) < 1e-6 { break }
                    s = min(max(s - (bx(s) - x) / d, 0), 1)
                }
                return by(s)
            }
        }
    }

    enum Kind {
        case timing(Double, Curve)
        /// Physical spring (Reanimated's damping, stiffness, mass) with an optional start velocity.
        case spring(damping: Double, stiffness: Double, mass: Double, velocity: Double?)
    }

    struct Step {
        var delay = 0.0
        var to: Double
        var kind: Kind
    }

    /// A value animated in steps from a moment on (a sequence of timings, springs and delays).
    struct Track {
        private struct Seg {
            var start: Double
            var end: Double
            var from: Double
            var to: Double
            var v0: Double
            var kind: Kind?
        }

        private var segs: [Seg]

        init(_ v: Double) { segs = [Seg(start: -.infinity, end: -.infinity, from: v, to: v, v0: 0, kind: nil)] }

        /// When the last step has settled.
        var end: Double { segs.last?.end ?? 0 }
        var target: Double { segs.last?.to ?? 0 }

        func value(_ t: Double) -> Double {
            guard let s = segs.last(where: { $0.start <= t }) else { return segs.first?.from ?? 0 }
            return Self.eval(s, t)
        }

        func velocity(_ t: Double) -> Double { (value(t + 0.001) - value(t - 0.001)) / 0.002 }

        mutating func set(_ v: Double) { segs = [Seg(start: -.infinity, end: -.infinity, from: v, to: v, v0: 0, kind: nil)] }

        /// Replaces whatever runs with `steps`, starting from the value (and speed) at `now`.
        mutating func run(_ steps: [Step], at now: Double) {
            let v = value(now)
            let vel = velocity(now)
            var list = [Seg(start: -.infinity, end: now, from: v, to: v, v0: 0, kind: nil)]
            var at = now
            var from = v
            for (i, step) in steps.enumerated() {
                let start = at + step.delay
                if step.delay > 0 { list.append(Seg(start: at, end: start, from: from, to: from, v0: 0, kind: nil)) }
                var v0 = 0.0
                var end = start
                switch step.kind {
                case let .timing(d, _): end = start + d
                case let .spring(c, k, m, velocity):
                    v0 = velocity ?? (i == 0 && step.delay == 0 ? vel : 0)
                    end = start + Self.settle(from - step.to, v0, c, k, m)
                }
                list.append(Seg(start: start, end: end, from: from, to: step.to, v0: v0, kind: step.kind))
                from = step.to
                at = end
            }
            segs = list
        }

        mutating func timing(_ to: Double, _ d: Double, _ curve: Curve = .inOutQuad, delay: Double = 0, at now: Double) {
            run([Step(delay: delay, to: to, kind: .timing(d, curve))], at: now)
        }

        mutating func spring(_ to: Double, damping: Double = 10, stiffness: Double = 100, mass: Double = 1,
                             velocity: Double? = nil, delay: Double = 0, at now: Double) {
            run([Step(delay: delay, to: to, kind: .spring(damping: damping, stiffness: stiffness, mass: mass, velocity: velocity))], at: now)
        }

        private static func settle(_ x0: Double, _ v0: Double, _ c: Double, _ k: Double, _ m: Double) -> Double {
            let w0 = sqrt(k / m)
            let z = c / (2 * sqrt(k * m))
            let rate = z < 1 ? z * w0 : w0 * (z - sqrt(z * z - 1))
            let amp = abs(x0) + abs(v0) / w0
            if amp < 1e-4 || rate <= 0 { return 0 }
            return min(6, max(0, log(amp / 0.0005) / rate))
        }

        private static func eval(_ s: Seg, _ t: Double) -> Double {
            guard let kind = s.kind else { return s.to }
            if t >= s.end { return s.to }
            let u = t - s.start
            switch kind {
            case let .timing(d, curve):
                return s.from + (s.to - s.from) * curve.y(d > 0 ? u / d : 1)
            case let .spring(c, k, m, _):
                let x0 = s.from - s.to
                let v0 = s.v0
                let w0 = sqrt(k / m)
                let z = c / (2 * sqrt(k * m))
                var x: Double
                if z < 0.999 {
                    let wd = w0 * sqrt(1 - z * z)
                    x = exp(-z * w0 * u) * (x0 * cos(wd * u) + (v0 + z * w0 * x0) / wd * sin(wd * u))
                } else if z <= 1.001 {
                    x = exp(-w0 * u) * (x0 + (v0 + w0 * x0) * u)
                } else {
                    let r = sqrt(z * z - 1)
                    let r1 = -w0 * (z - r)
                    let r2 = -w0 * (z + r)
                    let b = (v0 - r1 * x0) / (r2 - r1)
                    let a = x0 - b
                    x = a * exp(r1 * u) + b * exp(r2 * u)
                }
                return s.to + x
            }
        }
    }
}
