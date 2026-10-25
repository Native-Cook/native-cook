import Foundation
import SwiftUI

/// The Fuse Burn Button's clock and geometry. Every value is a closed-form function of the loop
/// time t (seconds), so t = loop draws the same frame as t = 0. Lengths in points.
///
///   0.0 – 1.4   rest: a braided fuse wrapped round the "Light it up" capsule.
///   1.4 – 1.8   a match flares at the fuse's start (the capsule's left end).
///   1.8 – 6.2   the spark runs clockwise round the capsule: it catches slowly, speeds up,
///               sputters at 40 % (dims and flickers, nearly stops), then runs on, easing in and
///               out. Behind it the cord turns to crinkled charcoal and smoke rises.
///   6.2 – 6.9   the spark meets the start: 30 sparks burst from the capsule, it lights up amber,
///               the label becomes "You're in", the count rolls 211 → 212.
///   6.9 – 7.8   hold.
///   7.8 – 8.7   the cord re-weaves from the start (drawn back on), the charcoal fades, the label
///               and the count go back.
///   8.7 – 9.0   rest.
enum FuseBurnButtonMotion {
    static let loop = 9.0

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

    /// Quadratic in-out: gentler top speed than the cubic.
    static func easeInOut2(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// JavaScript's `%` (keeps the sign of the dividend).
    static func jsMod(_ a: Double, _ b: Double) -> Double { a.truncatingRemainder(dividingBy: b) }

    /// Where the burn has reached along the fuse (0…1) at loop time t.
    static func burnAt(_ t: Double) -> Double {
        let u = seg(t, 1.8, 6.2)
        if u < 0.38 { return 0.4 * easeInOut2(u / 0.38) }
        if u < 0.5 { return 0.4 + 0.012 * ((u - 0.38) / 0.12) }
        return 0.412 + 0.588 * easeInOut2((u - 0.5) / 0.5)
    }

    static func loopPose(_ t: Double, length: Double) -> FuseBurnButtonPose {
        let b = burnAt(t)
        let speed = (burnAt(t + 0.01) - burnAt(t - 0.01)) / 0.02 * length
        let u = seg(t, 1.8, 6.2)
        // The sputter: around 40 % the spark dims and flickers at 5 Hz.
        let dip = sin(.pi * seg(u, 0.36, 0.52))
        let flicker = 0.6 + 0.4 * sin(2 * .pi * 45 * t / loop)
        var spark = easeOut(seg(t, 1.4, 1.55)) * (1 - 0.7 * dip * flicker)
        spark *= 1 - seg(t, 6.2, 6.32)
        let flare = sin(.pi * seg(t, 1.4, 1.8))
        let back = easeInOut(seg(t, 7.8, 8.4))
        return FuseBurnButtonPose(
            b: b,
            w: easeInOut(seg(t, 7.8, 8.7)),
            spark: spark,
            flare: flare,
            speed: speed,
            burstAge: t >= 6.2 ? t - 6.2 : -1,
            lit: easeOut(seg(t, 6.2, 6.5)) * (1 - back),
            label: easeOut(seg(t, 6.3, 6.8)) * (1 - back),
            count: easeInOut(seg(t, 6.35, 6.9)) * (1 - back),
            time: t
        )
    }
}

struct FuseBurnButtonPose {
    /// Burnt up to b (0…1 of the fuse); the cord re-woven over [0, w].
    var b = 0.0
    var w = 0.0
    /// The spark's brightness (0 off … 1), the match flare (0…1), its speed along the fuse (pt/s).
    var spark = 0.0
    var flare = 0.0
    var speed = 0.0
    /// Seconds since the burst (negative = none).
    var burstAge = -1.0
    /// Capsule lit amber (0…1), label "You're in" (0…1), count rolled to 212 (0…1).
    var lit = 0.0
    var label = 0.0
    var count = 0.0
    /// The time particles are animated by (loop time in the loop, the frame clock under a finger).
    var time = 0.0
}

/// The fuse: a stadium (capsule outline) round the capsule, sampled evenly, starting at its left
/// end and running clockwise (up over the top first).
struct FuseBurnButtonFuse {
    static let samples = 480

    var xs: [Double] = []
    var ys: [Double] = []
    /// Outward normal.
    var nx: [Double] = []
    var ny: [Double] = []
    var length = 0.0

    init(x: Double, y: Double, w: Double, h: Double, offset: Double) {
        let r = h / 2 + offset
        let cy = y + h / 2
        let left = x + h / 2
        let right = x + w - h / 2
        let straight = right - left
        let arc = Double.pi * r
        length = 2 * straight + 2 * arc
        let n = Self.samples
        xs.reserveCapacity(n)
        ys.reserveCapacity(n)
        nx.reserveCapacity(n)
        ny.reserveCapacity(n)
        for i in 0..<n {
            let (a, px, py) = Self.point(
                Double(i) / Double(n - 1) * length,
                r: r, cy: cy, left: left, right: right, straight: straight, arc: arc
            )
            xs.append(px)
            ys.append(py)
            nx.append(cos(a))
            ny.append(sin(a))
        }
    }

    /// The angle of the normal and the point at arc length s along the stadium.
    private static func point(
        _ s0: Double, r: Double, cy: Double, left: Double, right: Double, straight: Double, arc: Double
    ) -> (Double, Double, Double) {
        var s = s0
        if s < arc / 2 {
            let a = Double.pi + s / r
            return (a, left + cos(a) * r, cy + sin(a) * r)
        }
        s -= arc / 2
        if s < straight { return (-Double.pi / 2, left + s, cy - r) }
        s -= straight
        if s < arc {
            let a = -Double.pi / 2 + s / r
            return (a, right + cos(a) * r, cy + sin(a) * r)
        }
        s -= arc
        if s < straight { return (Double.pi / 2, right - s, cy + r) }
        s -= straight
        let a = Double.pi / 2 + s / r
        return (a, left + cos(a) * r, cy + sin(a) * r)
    }

    /// A point on the fuse at u (0…1): position and normal.
    func at(_ u: Double) -> (x: Double, y: Double, nx: Double, ny: Double) {
        let k = FuseBurnButtonMotion.clamp01(u) * Double(Self.samples - 1)
        let i = min(Self.samples - 2, Int(floor(k)))
        let fr = k - Double(i)
        return (
            xs[i] + (xs[i + 1] - xs[i]) * fr,
            ys[i] + (ys[i + 1] - ys[i]) * fr,
            nx[i] + (nx[i + 1] - nx[i]) * fr,
            ny[i] + (ny[i + 1] - ny[i]) * fr
        )
    }

    /// The fuse's u nearest to (x, y), and the distance to it.
    func nearest(_ x: Double, _ y: Double) -> (u: Double, d: Double) {
        var best = 0
        var bestD = 1e9
        var i = 0
        while i < Self.samples {
            let dx = xs[i] - x
            let dy = ys[i] - y
            let d = dx * dx + dy * dy
            if d < bestD {
                bestD = d
                best = i
            }
            i += 2
        }
        return (Double(best) / Double(Self.samples - 1), bestD.squareRoot())
    }

    // MARK: Paths for one frame

    /// Small per-sample crinkle of the charcoal, so the burnt cord looks curled, not drawn.
    private func crinkle(_ i: Int) -> Double {
        let d = Double(i)
        return sin(d * 0.9) * 0.9 + sin(d * 2.3 + 1) * 0.5
    }

    private func addRange(_ p: inout Path, _ i0: Int, _ i1: Int, wobble: Bool) {
        guard i1 - i0 >= 1 else { return }
        for i in i0...i1 {
            let o = wobble ? crinkle(i) : 0
            let pt = CGPoint(x: xs[i] + nx[i] * o, y: ys[i] + ny[i] * o)
            if i == i0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
    }

    private func addTicks(_ p: inout Path, _ i0: Int, _ i1: Int) {
        var i = i0 - (i0 % 3)
        while i <= i1 {
            if i >= i0 {
                let nxi = nx[i]
                let nyi = ny[i]
                // Along the cord is the normal turned a quarter; a twist runs diagonally across it.
                let tx = -nyi
                let ty = nxi
                p.move(to: CGPoint(x: xs[i] - nxi * 2 - tx * 1.4, y: ys[i] - nyi * 2 - ty * 1.4))
                p.addLine(to: CGPoint(x: xs[i] + nxi * 2 + tx * 1.4, y: ys[i] + nyi * 2 + ty * 1.4))
            }
            i += 3
        }
    }

    /// The cord for a burn at b with the start re-woven up to w.
    func cordPaths(b: Double, w: Double) -> (cord: Path, ticks: Path, charcoal: Path, ember: Path) {
        let last = Self.samples - 1
        let ib = Int((FuseBurnButtonMotion.clamp01(b) * Double(last)).rounded())
        let iw = Int((FuseBurnButtonMotion.clamp01(w) * Double(last)).rounded())
        var cord = Path()
        var ticks = Path()
        var charcoal = Path()
        var ember = Path()
        addRange(&cord, ib, last, wobble: false)
        addTicks(&ticks, ib, last)
        if iw > 0 {
            addRange(&cord, 0, min(iw, last), wobble: false)
            addTicks(&ticks, 0, min(iw, ib))
        }
        addRange(&charcoal, iw, ib, wobble: true)
        addRange(&ember, max(iw, ib - 18), ib, wobble: true)
        return (cord, ticks, charcoal, ember)
    }

    // Sparks thrown by the burning fuse: each is a pure function of the time, its seed and the
    // spark's place, and lives loop / k s with a whole k, so the loop repeats exactly.
    static let sparks = 96
    private static let gravity = 300.0

    /// Hot and warm streak paths of the sparks; a burst adds 30 radial ones from the capsule.
    func sparkPaths(_ pose: FuseBurnButtonPose, cx: Double, cy: Double) -> (hot: Path, warm: Path) {
        typealias M = FuseBurnButtonMotion
        var hot = Path()
        var warm = Path()
        let g = Self.gravity
        if pose.spark > 0.01 {
            let s = at(pose.b)
            // Along the fuse (clockwise): the normal turned a quarter.
            let tx = -s.ny
            let ty = s.nx
            for i in 0..<Self.sparks {
                let fi = Double(i)
                if M.hash(fi + 5.5) > pose.spark { continue }
                let k = 12 + floor(M.hash(fi + 0.7) * 12)
                let life = M.loop / k
                let phase = M.jsMod(pose.time / life + M.hash(fi + 2.1), 1)
                let age = phase * life
                // Thrown outward and up, a little back along the fuse; emitted from where the spark was.
                let spread = (M.hash(fi + 3.3) - 0.5) * 2.2
                let speed = 50 + M.hash(fi + 8.8) * 110
                let dirX = s.nx * cos(spread) - s.ny * sin(spread) - tx * 0.35
                let dirY = s.ny * cos(spread) + s.nx * sin(spread) - ty * 0.35 - 0.45
                let ex = s.x - tx * pose.speed * age * 0.6
                let ey = s.y - ty * pose.speed * age * 0.6
                let vx = dirX * speed
                let vy = dirY * speed
                let x = ex + vx * age
                let y = ey + vy * age + 0.5 * g * age * age
                let len = 0.018 + (1 - phase) * 0.02
                let a = CGPoint(x: x, y: y)
                let b = CGPoint(x: x - vx * len, y: y - (vy + g * age) * len)
                if phase < 0.45 {
                    hot.move(to: a)
                    hot.addLine(to: b)
                } else {
                    warm.move(to: a)
                    warm.addLine(to: b)
                }
            }
        }
        if pose.burstAge >= 0 && pose.burstAge < 0.9 {
            let a = pose.burstAge
            for i in 0..<30 {
                let fi = Double(i)
                let ang = fi / 30 * 2 * .pi + M.hash(fi + 40) * 0.2
                let speed = 170 + M.hash(fi + 41) * 170
                let travel = speed * (1 - exp(-3.2 * a)) / 3.2
                let x = cx + cos(ang) * (travel + 20) * 1.6
                let y = cy + sin(ang) * (travel + 8) + 60 * a * a
                let tail = 10 * exp(-2.5 * a)
                if a > 0.75 && M.hash(fi + 42) > (0.9 - a) * 6 { continue }
                let p0 = CGPoint(x: x, y: y)
                let p1 = CGPoint(x: x - cos(ang) * tail * 1.6, y: y - sin(ang) * tail)
                if a < 0.35 {
                    hot.move(to: p0)
                    hot.addLine(to: p1)
                } else {
                    warm.move(to: p0)
                    warm.addLine(to: p1)
                }
            }
        }
        return (hot, warm)
    }

    /// Soft smoke puffs rising from the burnt cord (drawn blurred, as one path).
    func smokePath(_ pose: FuseBurnButtonPose) -> Path {
        typealias M = FuseBurnButtonMotion
        var p = Path()
        let amount = min(1, pose.b * 8) * (1 - pose.w)
        if amount < 0.01 { return p }
        for j in 0..<9 {
            let fj = Double(j)
            let k = 3 + floor(M.hash(fj + 60) * 3)
            let life = M.loop / k
            let phase = M.jsMod(pose.time / life + M.hash(fj + 61), 1)
            // Born along the burnt part, nearer the spark.
            let u = pose.b * (1 - M.hash(fj + 62) * 0.5 * (1 - phase * 0.3))
            let s = at(u)
            let rise = phase * 70
            let drift = sin(phase * 5 + fj) * 10 * phase
            let r = (6 + phase * 12) * sin(.pi * phase) * amount
            if r > 0.5 {
                p.addEllipse(in: CGRect(x: s.x + drift - r, y: s.y - rise - r, width: r * 2, height: r * 2))
            }
        }
        return p
    }

    /// Opacity of the smoke (fades in with the burn, out with the re-weave).
    static func smokeOpacity(_ pose: FuseBurnButtonPose) -> Double {
        0.2 * min(1, pose.b * 8) * (1 - pose.w)
    }
}

/// A cubic Bézier easing curve, like Reanimated's `Easing.bezier`.
struct FuseBurnButtonCurve {
    let x1: Double, y1: Double, x2: Double, y2: Double

    static let easeOut = FuseBurnButtonCurve(x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    static let easeInOut = FuseBurnButtonCurve(x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    /// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static let quadInOut = FuseBurnButtonCurve(x1: -1, y1: 0, x2: 0, y2: 0)

    func callAsFunction(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        if x1 < 0 { return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
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

/// One value the finger drives, as Reanimated's `withTiming` / `withSequence` would: a closed-form
/// function of the time it was set.
struct FuseBurnButtonTrack {
    private var from: Double
    private var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var curve = FuseBurnButtonCurve.quadInOut
    /// A pulse: up to `peak` over `up` s (default curve), then down to 0 with `curve`.
    private var peak: Double?
    private var up = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at t: Double) -> Double {
        let u = t - start
        if let peak {
            if u <= 0 { return from }
            if u < up { return from + (peak - from) * FuseBurnButtonCurve.quadInOut(u / up) }
            if u < up + duration { return peak + (to - peak) * curve((u - up) / duration) }
            return to
        }
        if duration <= 0 || u >= duration { return to }
        if u <= 0 { return from }
        return from + (to - from) * curve(u / duration)
    }

    func done(at t: Double) -> Bool { t - start >= up + duration }

    mutating func set(_ value: Double) {
        from = value
        to = value
        duration = 0
        up = 0
        peak = nil
    }

    mutating func timing(_ target: Double, duration: Double, curve: FuseBurnButtonCurve = .quadInOut, now: Double) {
        from = value(at: now)
        to = target
        start = now
        self.duration = duration
        self.curve = curve
        peak = nil
        up = 0
    }

    mutating func pulse(peak: Double, up: Double, down: Double, downCurve: FuseBurnButtonCurve, now: Double) {
        from = value(at: now)
        to = 0
        start = now
        self.peak = peak
        self.up = up
        duration = down
        curve = downCurve
    }
}
