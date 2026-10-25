import Foundation

/*
 Prism Spectrum Player — timing and geometry. Every value the screen shows is a pure function of
 the loop time t (seconds, 0 ≤ t < loop), so the frame at t = loop equals the frame at t = 0.
 A step-for-step port of the React Native prismSpectrumMotion.ts.

 autoPlay timeline (loop = 8 s):
   0.0 – 1.2   rest: paused, a faint beam, short dim bars (the poster).
   1.08 – 1.32 the play button is pressed (scale 1 → 0.92 → 1).
   1.2 – 1.8   play: the beam brightens, the fan opens, the bars rise (ease-out cubic);
               the icon morphs play → pause over 1.2 – 1.45.
   1.8 – 6.4   playing; the elapsed time runs 1:12 → 1:17.
   3.0 – 5.6   the prism turns 0 → +18° → 0 → −18° → 0 (sin of an ease-in-out sweep); the fan
               follows 90 ms behind and swings 1.7× as far, opening wider as it turns.
   6.28 – 6.52 pause pressed; 6.4 – 7.0 the beam dims, bars fall (ease-in-out cubic).
   6.95 – 7.25 "back 5 s" pressed; 7.0 – 7.7 the scrubber glides back to 1:12.
   7.0 – 8.0   rest.
 */
enum PrismSpectrumPlayerMotion {
    static let loop = 8.0
    /// Beam strength while paused.
    static let restBeam = 0.4
    /// Elapsed seconds at rest (1:12) and the track's length (3:42).
    static let restElapsed = 72.0
    static let trackLength = 222.0
    /// How far the prism may turn (radians, ±25°) and how far the script turns it (±18°).
    static let rotLimit = 25 * Double.pi / 180
    static let sweepAngle = 18 * Double.pi / 180
    /// The fan's lag behind the prism in the script (s), and how much more it swings.
    static let fanLag = 0.09
    static let fanGain = 1.7

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }

    /// 0 before a, 1 after b, linear between.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }

    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(Double.pi * x) }

    /// 0 → 1 → 0 over [a, b].
    static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double { sin(Double.pi * seg(t, a, b)) }

    static func sweep(_ t: Double) -> Double {
        sweepAngle * sin(2 * Double.pi * easeInOutSine(seg(t, 3.0, 5.6)))
    }

    static func pose(_ t: Double) -> PrismSpectrumPlayerPose {
        let play = easeOutCubic(seg(t, 1.2, 1.8)) * (1 - easeInOutCubic(seg(t, 6.4, 7.0)))
        let icon = easeInOutCubic(seg(t, 1.2, 1.45)) * (1 - easeInOutCubic(seg(t, 6.4, 6.65)))
        let played = seg(t, 1.35, 6.55) * 5.2
        let back = easeInOutCubic(seg(t, 7.0, 7.7))
        return PrismSpectrumPlayerPose(
            rot: sweep(t),
            fan: sweep(t - fanLag),
            play: play,
            beam: restBeam + (1 - restBeam) * play,
            icon: icon,
            elapsed: restElapsed + played * (1 - back),
            pressPlay: bump(t, 1.08, 1.32) + bump(t, 6.28, 6.52),
            pressBack: bump(t, 6.95, 7.25)
        )
    }

    /// Past ±limit the prism resists: limit + a falling-off share of the overshoot.
    static func rubberBand(_ x: Double, _ limit: Double) -> Double {
        let a = abs(x)
        if a <= limit { return x }
        let over = a - limit
        return (x < 0 ? -1 : 1) * (limit + (over * 0.55) / (1 + over / 0.35))
    }

    /// Where the beam enters (middle of the left face) and leaves (middle of the right face).
    static func ports(_ g: PrismSpectrumPlayerGeo, _ rot: Double) -> (inX: Double, inY: Double, outX: Double, outY: Double) {
        let c = cos(rot)
        let s = sin(rot)
        let lx = -0.433 * g.r
        let ly = -0.25 * g.r
        let ex = 0.433 * g.r
        return (
            g.cx + c * lx - s * ly,
            g.cy + s * lx + c * ly,
            g.cx + c * ex - s * ly,
            g.cy + s * ex + c * ly
        )
    }

    /// The fan's centre direction and total spread (radians) for a fan angle and an opening.
    static func fanShape(_ fan: Double, _ open: Double) -> (dir: Double, spread: Double) {
        let dir = 0.4 + fanGain * fan
        let spread = 0.48 * (1 + (0.9 * abs(fan)) / rotLimit) * (0.78 + 0.22 * open)
        return (dir, spread)
    }

    static func formatTime(_ seconds: Double) -> String {
        let s = max(0, Int(floor(seconds)))
        let m = s / 60
        let r = s % 60
        return "\(m):\(r < 10 ? "0" : "")\(r)"
    }

    /// CSS-style cubic bezier easing (x1, y1, x2, y2) at x in 0…1.
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func coord(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let u = 1 - t
            return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
        }
        func slope(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let u = 1 - t
            return 3 * u * u * a + 6 * u * t * (b - a) + 3 * t * t * (1 - b)
        }
        var t = x
        for _ in 0..<8 {
            let d = slope(t, x1, x2)
            if abs(d) < 1e-6 { break }
            t -= (coord(t, x1, x2) - x) / d
            t = min(1, max(0, t))
        }
        // A few bisection steps make it safe where Newton stalls.
        var lo = 0.0
        var hi = 1.0
        if abs(coord(t, x1, x2) - x) > 1e-5 {
            t = x
            for _ in 0..<30 {
                let v = coord(t, x1, x2)
                if v < x { lo = t } else { hi = t }
                t = (lo + hi) / 2
            }
        }
        return coord(t, y1, y2)
    }

    static func easeInOut(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }
    static func easeOutStrong(_ x: Double) -> Double { bezier(0.23, 1, 0.32, 1, x) }
}

struct PrismSpectrumPlayerPose {
    /// Prism angle and the fan's (lagging) angle, radians.
    var rot: Double
    var fan: Double
    /// 0 paused … 1 playing (bars), the beam's strength, and the play/pause icon morph.
    var play: Double
    var beam: Double
    var icon: Double
    /// Elapsed seconds of the track.
    var elapsed: Double
    /// Press amounts of the play and back-5 buttons (0 … 1).
    var pressPlay: Double
    var pressBack: Double
}

/// Screen layout, in points, from the canvas size and the safe area.
struct PrismSpectrumPlayerGeo {
    let w: Double
    let h: Double
    let u: Double
    /// Prism centroid and circumradius.
    let cx: Double
    let cy: Double
    let r: Double
    /// Shortest and longest bar.
    let lmin: Double
    let lmax: Double
    let heroBottom: Double
    let titleTop: Double
    let scrubY: Double
    let controlsY: Double
    let side: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let heroTop = top + 56
        let titleTop = (h * 0.585).rounded()
        let heroBottom = titleTop - 12
        let u = min(w / 393, (heroBottom - heroTop) / 371)
        let side = 204 * u
        let r = side / sqrt(3)
        let cx = w * 0.32
        let cy = heroTop + (heroBottom - heroTop) * 0.5
        // The exit point at rest sits at cx + 0.433 r; the bars must stop short of the right edge.
        let exitX = cx + 0.433 * r
        let scrubY = titleTop + 88
        self.w = w
        self.h = h
        self.u = u
        self.cx = cx
        self.cy = cy
        self.r = r
        self.lmin = 48 * u
        self.lmax = min(w - exitX - 18, 214 * u)
        self.heroBottom = heroBottom
        self.titleTop = titleTop
        self.scrubY = scrubY
        self.controlsY = min(scrubY + 86, h - bottom - 128)
        self.side = side
    }
}

/// One animatable value, like a Reanimated shared value: set at once, eased over a duration, or
/// sprung, each a pure function of wall-clock time once started.
struct PrismSpectrumPlayerChannel {
    private enum Kind {
        case still
        case timing(from: Double, t0: Double, duration: Double, curve: (Double) -> Double)
        case spring(from: Double, velocity: Double, t0: Double, omega: Double, zeta: Double)
    }

    private(set) var target: Double
    private var kind: Kind = .still

    init(_ value: Double) { target = value }

    func value(at n: Double) -> Double {
        switch kind {
        case .still:
            return target
        case let .timing(from, t0, duration, curve):
            let x = duration <= 0 ? 1 : min(1, max(0, (n - t0) / duration))
            return from + (target - from) * curve(x)
        case let .spring(from, velocity, t0, omega, zeta):
            return Self.spring(from: from, velocity: velocity, to: target, omega: omega, zeta: zeta, t: max(0, n - t0)).x
        }
    }

    func isMoving(at n: Double) -> Bool {
        switch kind {
        case .still:
            return false
        case let .timing(_, t0, duration, _):
            return n < t0 + duration
        case let .spring(from, velocity, t0, omega, zeta):
            let s = Self.spring(from: from, velocity: velocity, to: target, omega: omega, zeta: zeta, t: max(0, n - t0))
            return abs(s.x - target) > 1e-4 || abs(s.v) > 1e-3
        }
    }

    mutating func set(_ goal: Double) {
        target = goal
        kind = .still
    }

    mutating func timing(to goal: Double, duration: Double, at n: Double, curve: @escaping (Double) -> Double) {
        let from = value(at: n)
        target = goal
        kind = .timing(from: from, t0: n, duration: duration, curve: curve)
    }

    /// A spring that settles in about `duration` seconds (SwiftUI's response), with a damping ratio.
    mutating func spring(to goal: Double, duration: Double, dampingRatio: Double, velocity: Double, at n: Double) {
        let from = value(at: n)
        target = goal
        kind = .spring(from: from, velocity: velocity, t0: n, omega: 2 * Double.pi / duration, zeta: dampingRatio)
    }

    /// Settles a finished animation into a plain value.
    mutating func settle(at n: Double) {
        if !isMoving(at: n) { kind = .still }
    }

    static func spring(from x0: Double, velocity v0: Double, to x1: Double, omega w: Double, zeta z: Double, t: Double) -> (x: Double, v: Double) {
        let a = x0 - x1
        if z < 1 {
            let wd = w * sqrt(1 - z * z)
            let b = (v0 + z * w * a) / wd
            let e = exp(-z * w * t)
            let c = cos(wd * t)
            let s = sin(wd * t)
            let x = e * (a * c + b * s)
            let v = e * ((-z * w) * (a * c + b * s) + (-a * wd * s + b * wd * c))
            return (x1 + x, v)
        }
        let b = v0 + w * a
        let e = exp(-w * t)
        return (x1 + e * (a + b * t), e * (b - w * (a + b * t)))
    }
}
