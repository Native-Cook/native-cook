import Foundation

/*
 Wind Scatter Letters — where every glyph and leaf is, as a pure function of the loop time, and
 the small simulation a finger drives.

 loop = 9 s
   0.0 – 1.6   rest: the headline set, six leaves lying on the ridge.
   1.6 – 2.4   the gust builds: wind streaks sweep in, the glyphs tremble (up to 3°), eight
               leaves blow in from the left edge.
   2.2 – 3.2   the glyphs lift off one by one from the left (0.45 s across the line): each
               eases out (cubic, 1 s) toward its own drift (60–300 pt right, −250…+190 pt
               vertical) while circling an eddy (20–60 pt, 1.5–3 rad/s), tumbling (0.8–2.2 rad/s
               plus a 25° flutter) and swelling or shrinking with depth (−30 %…+45 %).
   5.0 – 7.2   they come home on an underdamped spring (ζ 0.55, ω 9 rad/s, ~12 % overshoot),
               30 ms apart left to right, the offset spiralling in as it shrinks.
   7.2 – 9.0   rest.

 Bodies are the headline's glyphs followed by the leaves on the ridge; the eight leaves that
 cross the screen are separate and always analytic.
*/
enum WindScatterLettersMotion {
    static let groundLeaves = 6
    static let flyingLeaves = 8
    private static let liftAt = 2.2
    private static let liftSpread = 0.45
    private static let liftLen = 1.0
    private static let returnAt = 5.0
    private static let returnStagger = 0.03
    private static let zeta = 0.55
    private static let omega = 9.0
    private static let settled = 2.1

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Unit step response of the return spring.
    static func springStep(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        if tau >= settled { return 1 }
        let wd = omega * sqrt(1 - zeta * zeta)
        let e = exp(-zeta * omega * tau)
        return 1 - e * (cos(wd * tau) + (zeta / sqrt(1 - zeta * zeta)) * sin(wd * tau))
    }

    /// How strong the gust is (0…1): streaks, tremble, trails.
    static func gustAt(_ t: Double) -> Double {
        easeInOutCubic(seg(t, 1.6, 2.4)) * (1 - easeInOutCubic(seg(t, 4.6, 5.6)))
    }

    /// Depth of body i: every third glyph sits behind the others (shrinks, blurs), the rest come forward.
    static func depthOf(_ i: Int) -> Double {
        let h = hash(Double(i) * 9.13 + 4)
        return i % 3 == 1 ? -(0.15 + 0.15 * h) : 0.1 + 0.35 * h
    }

    /// dx, dy, rotation (rad), scale of body i at loop time t, relative to its home.
    static func autoOffset(_ t: Double, _ i: Int, home: WindScatterLettersHome, w W: Double, h H: Double) -> WindScatterLettersPose {
        let di = Double(i)
        let h1 = hash(di * 3.17 + 1)
        let h2 = hash(di * 5.31 + 2)
        let h3 = hash(di * 7.77 + 3)
        let h4 = hash(di * 9.13 + 4)
        // Tremble before lift-off.
        let lift = liftAt + liftSpread * clamp01(home.x / W) + 0.05 * h1
        let tremble = (3 * .pi) / 180 * easeInOutCubic(seg(t, 1.6, lift)) * sin(t * 23 + di * 1.9)
        if t < lift { return WindScatterLettersPose(x: 0, y: 0, r: tremble, s: 1) }

        // The drift: toward a spot spread over the screen, kept on it.
        let tx = min(W - 40, max(40, home.x + (60 + 240 * h1) * home.reach))
        let ty = min(H * 0.82, max(H * 0.12, home.y + (-250 + 440 * h2) * home.reach))
        let ax = tx - home.x
        let ay = ty - home.y
        let r = (20 + 40 * h3) * home.reach
        let w = (1.5 + 1.5 * h4) * (h1 > 0.5 ? 1 : -1)
        let spin = (0.8 + 1.4 * h3) * (h2 > 0.5 ? 1 : -1)
        let depth = depthOf(i)

        let ret = returnAt + returnStagger * di
        let tf = min(t, ret) - lift
        let e = easeOutCubic(clamp01(tf / liftLen))
        let th = h1 * 6.283 + w * tf
        let sx = e * ax * (1 - exp(-tf / 0.9)) + e * r * (cos(th) - cos(h1 * 6.283))
        let sy = e * ay * (1 - exp(-tf / 0.9)) + e * r * (sin(th) - sin(h1 * 6.283))
        let rot = e * (spin * tf + 0.44 * sin(tf * 5 + di))
        let sc = 1 + e * depth * (1 + 0.1 * sin(tf * 3 + di))
        if t < ret { return WindScatterLettersPose(x: sx, y: sy, r: rot + tremble * (1 - e), s: sc) }

        // Home on the spring; the offset turns as it shrinks so the way back curves.
        let k = 1 - springStep(t - ret)
        let a = 0.6 * k
        let c = cos(a)
        let s = sin(a)
        return WindScatterLettersPose(x: k * (sx * c - sy * s), y: k * (sx * s + sy * c), r: k * rot, s: 1 + k * (sc - 1))
    }

    /// One of the leaves crossing the screen: x, y, rotation, flip (−1…1 as scaleX), scale; nil when off screen.
    static func flyingLeaf(_ t: Double, _ j: Int, w W: Double, h H: Double) -> (Double, Double, Double, Double, Double)? {
        let dj = Double(j)
        let h1 = hash(dj * 11.3 + 21)
        let h2 = hash(dj * 4.7 + 22)
        let start = 1.75 + 1.9 * h1
        let dur = 2.2 + 1.2 * h2
        let u = seg(t, start, start + dur)
        if u <= 0 || u >= 1 { return nil }
        let x = -50 + (W + 100) * easeInOutCubic(u)
        let y0 = H * (0.18 + 0.55 * h2)
        let y = y0 + sin(u * 6.283 * (1 + h1)) * 40 + u * 90
        let rot = u * (5 + 6 * h1) * (h2 > 0.5 ? 1 : -1)
        let flip = cos(u * 6.283 * (2 + h2 * 2))
        return (x, y, rot, flip, 0.85 + 0.5 * h1)
    }
}

/// A body's home: the centre of its glyph or leaf, and how far it may scatter (1 for glyphs, less for leaves).
struct WindScatterLettersHome {
    var x: Double
    var y: Double
    var reach: Double
}

struct WindScatterLettersPose {
    var x = 0.0, y = 0.0, r = 0.0, s = 1.0
}

/// Finger wind: at (fx, fy) moving at (fvx, fvy) pt/s, `on` while touching.
struct WindScatterLettersWind {
    var fx = 0.0, fy = 0.0, fvx = 0.0, fvy = 0.0
    var on = false
}

/// The touch simulation: per body offset, velocity, rotation and its speed, plus history for trails.
struct WindScatterLettersSim {
    var x: [Double], y: [Double], vx: [Double], vy: [Double], r: [Double], vr: [Double]
    var px: [[Double]], py: [[Double]]

    init(_ n: Int) {
        let z = [Double](repeating: 0, count: n)
        x = z; y = z; vx = z; vy = z; r = z; vr = z
        px = [z, z]
        py = [z, z]
    }

    /// One step. `home` springs are soft while the wind blows, firm when `settle` is set.
    mutating func step(homes: [WindScatterLettersHome], wind: WindScatterLettersWind, dt: Double, settle: Bool) {
        let k = settle ? 70.0 : 9
        let c = settle ? 2 * sqrt(70.0) : 2.2
        for i in 0..<homes.count {
            px[1][i] = px[0][i]
            py[1][i] = py[0][i]
            px[0][i] = x[i]
            py[0][i] = y[i]
            var ax = -k * x[i] - c * vx[i]
            var ay = -k * y[i] - c * vy[i]
            var ar = -k * r[i] - c * vr[i]
            if wind.on {
                let dx = homes[i].x + x[i] - wind.fx
                let dy = homes[i].y + y[i] - wind.fy
                let d2 = dx * dx + dy * dy
                let near = exp(-d2 / (2 * 120 * 120)) * homes[i].reach
                let sp = min(3000, hypot(wind.fvx, wind.fvy))
                let on = sp > 40 ? 1.0 : 0
                ax += (wind.fvx * (0.15 + 4.5 * near)) * on
                ay += (wind.fvy * (0.15 + 4.5 * near) - 60 * near) * on
                ar += ((dx * wind.fvy - dy * wind.fvx) / 4000) * near
            }
            vx[i] += ax * dt
            vy[i] += ay * dt
            vr[i] += ar * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            r[i] += vr[i] * dt
        }
    }

    var energy: Double {
        var e = 0.0
        for i in 0..<x.count {
            e = max(e, abs(x[i]) + abs(y[i]) + abs(vx[i]) * 0.05 + abs(vy[i]) * 0.05 + abs(r[i]) * 30)
        }
        return e
    }
}
