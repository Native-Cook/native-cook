import Foundation

/// Animation tracks the finger drives, evaluated against the clock (timings, springs, delays).
extension CrayonRubbingCardMotion {
    // MARK: Tracks (the finger's animations, evaluated against the clock)

    enum Curve {
        case linear, inOutQuad, outQuad
        case bezier(Double, Double, Double, Double)

        func y(_ x: Double) -> Double {
            let x = min(max(x, 0), 1)
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
