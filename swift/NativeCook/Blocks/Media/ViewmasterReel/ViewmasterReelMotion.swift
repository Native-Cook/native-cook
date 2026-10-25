import Foundation

/*
 Viewmaster Reel — the autoPlay timeline and its helpers. Pure functions of the loop time t.

 The reel's position `pos` is in slots (7 per turn); slot k sits at the gate (12 o'clock) when
 pos = k. The disc is drawn turned by −pos × 360/7°, the window shows slot round(pos) mod 7, and
 the window's shutter closes as pos leaves a whole slot (fully shut halfway between two), so a
 change of slide is always a blink — under the finger as well as in the loop.

   0.0 – 1.3   rest on slot 1 (pos 0).
   1.3 – 2.1   advance: the lever is pulled down (0 – 0.3 of the step, ease-in-out), the disc
               turns one slot once the lever is most of the way down (0.28 – 0.72, ease-out-back:
               the clunk), the lever springs back (0.4 – 1.0, ease-out-back), the viewer shudders
               1.4 pt as the detent catches.
   3.3 – 4.1   the same to slot 3.          5.3 – 6.1   the same to slot 4.
   7.0 – 8.4   the disc is spun by hand through four slots back round to slot 1 (pos 3 → 7, ease
               in-out with a small dip and overshoot); the shutter flickers at every slot.
   8.4 – 9.0   rest. pos = 7 draws exactly as pos = 0.
 */
enum ViewmasterReelMotion {
    static let loop: Double = 9
    static let slots = 7
    static let stepDeg = 360.0 / 7
    static let stepRad = stepDeg * .pi / 180

    static let advances: [Double] = [1.3, 3.3, 5.3]
    static let advanceLen: Double = 0.8
    static let spin = (7.0, 8.4)

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }
    static func easeInOutCubic(_ u: Double) -> Double {
        if u < 0.5 { return 4 * u * u * u }
        let v = -2 * u + 2
        return 1 - v * v * v / 2
    }
    static func easeOutBack(_ u: Double, _ c1: Double) -> Double {
        let c3 = c1 + 1, v = u - 1
        return 1 + c3 * v * v * v + c1 * v * v
    }
    static func easeInOutBack(_ u: Double, _ c1: Double) -> Double {
        let c2 = c1 * 1.525
        if u < 0.5 { return (2 * u) * (2 * u) * ((c2 + 1) * 2 * u - c2) / 2 }
        let v = 2 * u - 2
        return (v * v * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2
    }
    /// Positive modulo.
    static func wrap(_ x: Double, _ m: Double) -> Double {
        let r = x.truncatingRemainder(dividingBy: m)
        return r < 0 ? r + m : r
    }
    /// The window's shutter for a reel position: 0 open on a whole slot, 1 shut halfway between.
    static func shutter(_ pos: Double) -> Double {
        smoothstep(0.05, 0.3, abs(pos - pos.rounded()))
    }

    struct Pose { var pos: Double; var lever: Double; var shake: Double }

    static func pose(_ t: Double) -> Pose {
        var pos = 0.0, lever = 0.0, shake = 0.0
        for a in advances {
            let u = seg(t, a, a + advanceLen)
            pos += easeOutBack(seg(u, 0.28, 0.72), 1.6)
            let down = easeInOutCubic(seg(u, 0, 0.3))
            let up = easeOutBack(seg(u, 0.4, 1), 1.2)
            lever += down * (1 - up)
            let tau = t - (a + advanceLen * 0.52)
            if tau > 0 && tau < 0.5 { shake += 1.4 * sin(2 * .pi * 16 * tau) * exp(-tau * 11) }
        }
        pos += 4 * easeInOutBack(seg(t, spin.0, spin.1), 0.5)
        return Pose(pos: pos, lever: lever, shake: shake)
    }

    /// Pulled past a limit, the value follows less and less.
    static func rubber(_ x: Double, _ lo: Double, _ hi: Double, _ k: Double) -> Double {
        if x < lo { let o = lo - x; return lo - o / (1 + o / k) }
        if x > hi { let o = x - hi; return hi + o / (1 + o / k) }
        return x
    }

    /// A cubic bézier easing (CSS style).
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> (Double) -> Double {
        func coord(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            ((((1 - 3 * p2 + 3 * p1) * s) + (3 * p2 - 6 * p1)) * s + 3 * p1) * s
        }
        return { x in
            if x <= 0 { return 0 }
            if x >= 1 { return 1 }
            var lo = 0.0, hi = 1.0, s = x
            for _ in 0..<40 {
                let v = coord(s, x1, x2)
                if abs(v - x) < 1e-6 { break }
                if v < x { lo = s } else { hi = s }
                s = (lo + hi) / 2
            }
            return coord(s, y1, y2)
        }
    }
    static let returnEase = bezier(0.65, 0, 0.35, 1)
    /// Reanimated's default withTiming curve.
    static func easeInOutQuad(_ u: Double) -> Double { u < 0.5 ? 2 * u * u : 1 - (-2 * u + 2) * (-2 * u + 2) / 2 }
}

/// One animated number driven by the finger: held, a spring (carrying velocity) or a timed tween.
final class ViewmasterReelChannel {
    private enum Kind {
        case hold
        case spring(response: Double, damping: Double)
        case tween(duration: Double, curve: (Double) -> Double)
    }

    private var kind: Kind = .hold
    private var from = 0.0
    private var target = 0.0
    private var v0 = 0.0
    private var start = Date.distantPast

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
        case let .tween(duration, curve):
            return from + (target - from) * curve(ViewmasterReelMotion.clamp01(t / duration))
        }
    }

    func velocity(at date: Date) -> Double {
        (value(at: date.addingTimeInterval(0.004)) - value(at: date)) / 0.004
    }

    func set(_ value: Double) { kind = .hold; from = value; target = value }

    func spring(to value: Double, response: Double, damping: Double, velocity: Double? = nil, at date: Date) {
        let v = velocity ?? self.velocity(at: date)
        from = self.value(at: date)
        target = value
        v0 = v
        start = date
        kind = .spring(response: response, damping: damping)
    }

    func tween(to value: Double, duration: Double, curve: @escaping (Double) -> Double, at date: Date) {
        from = self.value(at: date)
        target = value
        start = date
        kind = .tween(duration: duration, curve: curve)
    }
}
