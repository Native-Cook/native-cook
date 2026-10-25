import Foundation

/*
 Film Negative Card — the autoPlay timeline and the small easing helpers it is built from.

 Every value is a pure function of the loop time t (seconds), so the frame at t = loop is the
 frame at t = 0.

   0.0 – 1.3   rest: the strip lies half over the top of the light table, the lower part of
               frame 7 already glowing as a positive, the rest of the roll dimmed on the desk.
   1.3 – 3.0   it slides fully onto the light (a 4 % dip back first, a 3 % overshoot at the end),
               lifting off the desk on the way (bigger, softer shadow). The light's edge sweeps
               across the frame and turns the negative into the positive as it goes.
   3.0 – 5.0   hold on the light. The loupe fades in over the face (3.05 – 3.45, ease-out),
               wanders a little (one whole sine), and fades out (4.6 – 4.95).
   5.0 – 6.8   it slides back to its resting place, the positive sweeping back into the negative.
   6.8 – 8.0   rest.
 */
enum FilmNegativeCardMotion {
    static let loop: Double = 8
    /// Where the strip rests, between the desk (0) and fully on the light (1).
    static let restOn: Double = 0.45

    static let slideOn = (1.3, 3.0)
    static let slideOff = (5.0, 6.8)
    static let loupeIn = (3.05, 3.45)
    static let loupeOut = (4.6, 4.95)
    static let loupeWander = (3.0, 5.0)
    static let wanderX: Double = 26
    static let wanderY: Double = 9
    static let back: Double = 0.75

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ u: Double) -> Double { let v = 1 - u; return 1 - v * v * v }
    static func easeInOutCubic(_ u: Double) -> Double {
        if u < 0.5 { return 4 * u * u * u }
        let v = -2 * u + 2
        return 1 - v * v * v / 2
    }
    /// Ease in-out with a small dip before the move and a small overshoot after it.
    static func easeInOutBack(_ u: Double, _ c1: Double) -> Double {
        let c2 = c1 * 1.525
        if u < 0.5 { return (2 * u) * (2 * u) * ((c2 + 1) * 2 * u - c2) / 2 }
        let v = 2 * u - 2
        return (v * v * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2
    }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }

    struct Pose {
        /// 0 = on the desk, 1 = on the light; rests at restOn.
        var on: Double
        /// 0 = flat on a surface, 1 = held up mid-move.
        var lift: Double
        var loupe: Double
        var loupeDx: Double
        var loupeDy: Double
    }

    static func pose(_ t: Double) -> Pose {
        let u1 = seg(t, slideOn.0, slideOn.1)
        let u2 = seg(t, slideOff.0, slideOff.1)
        let on = restOn + (1 - restOn) * (easeInOutBack(u1, back) - easeInOutBack(u2, back))
        let lift = sin(.pi * u1) + sin(.pi * u2)
        let a = easeOutCubic(seg(t, loupeIn.0, loupeIn.1)) * (1 - easeInOutCubic(seg(t, loupeOut.0, loupeOut.1)))
        let w = seg(t, loupeWander.0, loupeWander.1)
        return Pose(on: on, lift: lift, loupe: a,
                    loupeDx: wanderX * sin(2 * .pi * w), loupeDy: wanderY * sin(4 * .pi * w))
    }

    /// Pulled past a limit, the strip follows less and less (rubber band).
    static func rubber(_ x: Double, _ lo: Double, _ hi: Double) -> Double {
        if x < lo { let o = lo - x; return lo - o / (1 + o / 60) }
        if x > hi { let o = x - hi; return hi + o / (1 + o / 60) }
        return x
    }

    /// A cubic bézier easing (CSS style), solved for x by Newton steps then bisection.
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> (Double) -> Double {
        func coord(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            let a = 1 - 3 * p2 + 3 * p1, b = 3 * p2 - 6 * p1, c = 3 * p1
            return ((a * s + b) * s + c) * s
        }
        func slope(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            let a = 1 - 3 * p2 + 3 * p1, b = 3 * p2 - 6 * p1, c = 3 * p1
            return (3 * a * s + 2 * b) * s + c
        }
        return { x in
            if x <= 0 { return 0 }
            if x >= 1 { return 1 }
            var s = x
            for _ in 0..<6 {
                let d = slope(s, x1, x2)
                if abs(d) < 1e-6 { break }
                s -= (coord(s, x1, x2) - x) / d
            }
            if s < 0 || s > 1 || abs(coord(s, x1, x2) - x) > 1e-4 {
                var lo = 0.0, hi = 1.0
                s = x
                for _ in 0..<30 {
                    let v = coord(s, x1, x2)
                    if abs(v - x) < 1e-6 { break }
                    if v < x { lo = s } else { hi = s }
                    s = (lo + hi) / 2
                }
            }
            return coord(s, y1, y2)
        }
    }

    static let returnEase = bezier(0.65, 0, 0.35, 1)
    static let loupeEase = bezier(0.23, 1, 0.32, 1)
    /// Reanimated's default withTiming curve.
    static func easeInOutQuad(_ u: Double) -> Double { u < 0.5 ? 2 * u * u : 1 - (-2 * u + 2) * (-2 * u + 2) / 2 }
}

/// One animated number driven by the finger: held, a spring (carrying velocity) or a timed tween.
/// Evaluated from the timeline's date, so nothing is written per frame.
final class FilmNegativeCardChannel {
    private enum Kind {
        case hold
        case spring(response: Double, damping: Double)
        case tween(duration: Double, delay: Double, curve: (Double) -> Double)
    }

    private var kind: Kind = .hold
    private var from = 0.0
    private var target = 0.0
    private var v0 = 0.0
    private var start = Date.distantPast

    init(_ value: Double = 0) { from = value; target = value }

    var goal: Double { target }

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        switch kind {
        case .hold:
            return target
        case let .spring(response, damping):
            let w = 2 * Double.pi / response
            let a = from - target
            if damping < 1 {
                let wd = w * (1 - damping * damping).squareRoot()
                let b = (v0 + damping * w * a) / wd
                return target + exp(-damping * w * t) * (a * cos(wd * t) + b * sin(wd * t))
            }
            return target + exp(-w * t) * (a + (v0 + w * a) * t)
        case let .tween(duration, delay, curve):
            let u = FilmNegativeCardMotion.clamp01((t - delay) / duration)
            return from + (target - from) * curve(u)
        }
    }

    func velocity(at date: Date) -> Double {
        (value(at: date.addingTimeInterval(0.004)) - value(at: date)) / 0.004
    }

    func finished(at date: Date) -> Bool {
        let t = date.timeIntervalSince(start)
        switch kind {
        case .hold: return true
        case let .spring(response, _): return t > response * 2.5
        case let .tween(duration, delay, _): return t >= duration + delay
        }
    }

    func set(_ value: Double) {
        kind = .hold
        from = value
        target = value
    }

    func spring(to value: Double, response: Double, damping: Double, velocity: Double? = nil, at date: Date) {
        let v = velocity ?? self.velocity(at: date)
        from = self.value(at: date)
        target = value
        v0 = v
        start = date
        kind = .spring(response: response, damping: damping)
    }

    func tween(to value: Double, duration: Double, delay: Double = 0, curve: @escaping (Double) -> Double, at date: Date) {
        from = self.value(at: date)
        target = value
        start = date
        kind = .tween(duration: duration, delay: delay, curve: curve)
    }
}
