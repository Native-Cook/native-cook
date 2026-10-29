import SwiftUI

/*
 Glass Mood Bloom — the numbers: the seven moods, the loop's value V(t), the layer parameters and the touch driver.
 Everything the screen draws is a function of the mood value v ∈ [-1, 1] (−1 Very Unpleasant … 0 Neutral … 1 Very
 Pleasant), and v is a function of time: the loop's timeline, or the finger's samples, a detent spring and the ease
 back to Neutral after a touch.
*/
enum GlassMoodBloomModel {
    static let loop: Double = 10

    // MARK: Colour

    struct RGB {
        var r: Double, g: Double, b: Double
        init(_ hex: Int) {
            r = Double((hex >> 16) & 0xFF) / 255
            g = Double((hex >> 8) & 0xFF) / 255
            b = Double(hex & 0xFF) / 255
        }
        init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }
        func mix(_ o: RGB, _ t: Double) -> RGB {
            RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t)
        }
        func color(_ a: Double = 1) -> Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
        static let white = RGB(0xFFFFFF)
    }

    // MARK: Moods (anchors at v = −1, −2/3, −1/3, 0, 1/3, 2/3, 1)

    struct Mood {
        let name: String
        let rv: Double      // valley radius / tip radius
        let p: Double       // petal profile exponent (< 1 round lobes, > 1 thin spikes)
        let q: Double       // 0 lobed outline, 1 polygon outline
        let e: Double       // polygon side exponent (0 circle, 1 straight sides, > 1 concave star sides)
        let k: Double       // polygon corner sharpness (1 sharp, lower rounder)
        let twist: Double   // degrees the outer plate turns against the others
        let size: Double
        let deep: RGB, mid: RGB, pale: RGB
        let bgTop: RGB, bgBottom: RGB, button: RGB, label: RGB
    }

    static let moods: [Mood] = [
        Mood(name: "Very Unpleasant", rv: 0.5, p: 1.6, q: 0.5, e: 7, k: 0.975, twist: 12, size: 1.04,
             deep: RGB(0x7B45E6), mid: RGB(0xAE92F2), pale: RGB(0xECE6FC),
             bgTop: RGB(0xD6CBEE), bgBottom: RGB(0xB5A3DE), button: RGB(0x2C2382), label: RGB(0x2B1F66)),
        Mood(name: "Unpleasant", rv: 0.65, p: 1.2, q: 0.6, e: 4, k: 0.94, twist: 8, size: 1.0,
             deep: RGB(0x4E6AEE), mid: RGB(0x95A6F4), pale: RGB(0xE4E8FC),
             bgTop: RGB(0xD3D6F2), bgBottom: RGB(0xB7BCE8), button: RGB(0x1B3DBE), label: RGB(0x1E2A6E)),
        Mood(name: "Slightly Unpleasant", rv: 0.87, p: 0.8, q: 0, e: 0, k: 0.9, twist: 4, size: 0.97,
             deep: RGB(0x3B8EF0), mid: RGB(0x8CC0F7), pale: RGB(0xE2EEFC),
             bgTop: RGB(0xD7E0F2), bgBottom: RGB(0xBCCBEA), button: RGB(0x0E58C8), label: RGB(0x18346A)),
        Mood(name: "Neutral", rv: 1.0, p: 1.0, q: 0, e: 0, k: 0.9, twist: 0, size: 0.95,
             deep: RGB(0x47B4E4), mid: RGB(0x9DD7EF), pale: RGB(0xE9F6FB),
             bgTop: RGB(0xE1E8EC), bgBottom: RGB(0xC8D6DD), button: RGB(0x3591B8), label: RGB(0x22404E)),
        Mood(name: "Slightly Pleasant", rv: 1, p: 1, q: 1, e: 1, k: 0.88, twist: 3, size: 0.98,
             deep: RGB(0x5CCB24), mid: RGB(0xA7E36C), pale: RGB(0xE8F6D2),
             bgTop: RGB(0xD6E8BE), bgBottom: RGB(0xB3D595), button: RGB(0x2C8A0B), label: RGB(0x27461A)),
        Mood(name: "Pleasant", rv: 1, p: 1, q: 1, e: 3.2, k: 0.85, twist: 5, size: 1.0,
             deep: RGB(0xF0BE16), mid: RGB(0xF8DC66), pale: RGB(0xFFF4C8),
             bgTop: RGB(0xF1E5B6), bgBottom: RGB(0xE6CB82), button: RGB(0xD08F08), label: RGB(0x4C3A0C)),
        Mood(name: "Very Pleasant", rv: 0.55, p: 0.45, q: 0, e: 0, k: 0.85, twist: 10, size: 1.04,
             deep: RGB(0xFF6A2A), mid: RGB(0xFFAA6A), pale: RGB(0xFFE8D2),
             bgTop: RGB(0xFCE3CA), bgBottom: RGB(0xF6C298), button: RGB(0xFA6A2C), label: RGB(0x5A2A12)),
    ]

    /// The mood's parameters at v, interpolated between the two neighbouring anchors.
    struct Look {
        var n: Double, rv: Double, p: Double, q: Double, e: Double, k: Double, twist: Double, size: Double
        var deep: RGB, mid: RGB, pale: RGB, bgTop: RGB, bgBottom: RGB, button: RGB, label: RGB
    }

    static func look(_ v: Double) -> Look {
        let x = (min(max(v, -1), 1) + 1) * 3
        let i = min(Int(x), 5)
        let f = x - Double(i)
        let a = moods[i], b = moods[i + 1]
        func l(_ p: Double, _ q: Double) -> Double { p + (q - p) * f }
        return Look(n: v > 0 ? 5 : 8, rv: l(a.rv, b.rv), p: l(a.p, b.p),
                    q: l(a.q, b.q), e: l(a.e, b.e), k: l(a.k, b.k), twist: l(a.twist, b.twist),
                    size: l(a.size, b.size),
                    deep: a.deep.mix(b.deep, f), mid: a.mid.mix(b.mid, f), pale: a.pale.mix(b.pale, f),
                    bgTop: a.bgTop.mix(b.bgTop, f), bgBottom: a.bgBottom.mix(b.bgBottom, f),
                    button: a.button.mix(b.button, f), label: a.label.mix(b.label, f))
    }

    /// 0 … 6 into `moods`.
    static func bucket(_ v: Double) -> Int { Int((min(max(v, -1), 1) * 3).rounded()) + 3 }

    // MARK: Layers

    static let layerScale: [Double] = [1.00, 0.91, 0.64, 0.52, 0.40, 0.27]
    static let layerAlpha: [Double] = [0.16, 0.22, 0.42, 0.55, 0.68, 0.80]
    static let layerLag = 0.045

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(max((x - a) / (b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }

    static func layerColor(_ look: Look, _ k: Double) -> RGB {
        look.pale.mix(look.mid, smooth(0, 0.5, k)).mix(look.deep, smooth(0.4, 1, k))
    }

    /// Radius (tip = 1) at angle a from a petal tip: a mix of a lobed outline
    /// (rv + (1 − rv) · ((1 + cos na) / 2)^p) and a rounded polygon / star outline ((cos ψt / cos ψ)^e,
    /// ψ = acos(−k cos na) / n, the angle from the middle of a side, softened at the corners by k).
    static func radius(_ a: Double, _ l: Look) -> Double {
        let c = cos(l.n * a)
        let lobe = l.rv + (1 - l.rv) * pow((1 + c) / 2, l.p)
        let psiT = acos(-l.k) / l.n
        let psi = acos(-l.k * c) / l.n
        let poly = pow(cos(psiT) / cos(psi), l.e)
        return lobe + (poly - lobe) * l.q
    }

    /// The smallest radius of the outline (tip = 1), how deep its valleys are.
    static func valley(_ l: Look) -> Double {
        let lobe = l.rv
        let poly = pow(cos(acos(-l.k) / l.n), l.e)
        return lobe + (poly - lobe) * l.q
    }

    /// The closed outline of one layer.
    static func shape(center c: CGPoint, radius: Double, look: Look, rot: Double, samples: Int = 240) -> Path {
        var path = Path()
        for s in 0..<samples {
            let th = Double(s) / Double(samples) * 2 * .pi
            let r = radius * Self.radius(th - rot, look)
            let pt = CGPoint(x: c.x + r * cos(th), y: c.y + r * sin(th))
            if s == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
        }
        path.closeSubpath()
        return path
    }

    // MARK: Loop timeline

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - min(max(x, 0), 1), 3) }

    /// (start, end, from, to) moves; between them the value holds.
    static let moves: [(Double, Double, Double, Double)] = [
        (1.20, 1.75, 0, 1.0 / 3), (2.15, 2.70, 1.0 / 3, 2.0 / 3), (3.10, 3.65, 2.0 / 3, 1),
        (4.50, 5.30, 1, 0),
        (5.50, 6.00, 0, -1.0 / 3), (6.30, 6.80, -1.0 / 3, -2.0 / 3), (7.10, 7.60, -2.0 / 3, -1),
        (8.40, 9.30, -1, 0),
    ]

    /// The loop's mood value at loop time t (any real t; wrapped into 0 ..< loop).
    static func loopValue(_ time: Double) -> Double {
        var t = time.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        var v = 0.0
        for m in moves {
            if t < m.0 { return v }
            if t < m.1 { return m.2 + (m.3 - m.2) * easeInOut((t - m.0) / (m.1 - m.0)) }
            v = m.3
        }
        return v
    }

    /// Plate i's breathing and sway offsets: periodic in the loop and 0 at loop time 0.
    static func breathe(_ t: Double, _ i: Int) -> Double {
        sin(2 * .pi * 3 * t / loop + 0.7 * Double(i)) - sin(0.7 * Double(i))
    }

    static func wobble(_ t: Double, _ i: Int) -> Double {
        sin(2 * .pi * t / loop + 0.9 * Double(i)) - sin(0.9 * Double(i))
    }

    // MARK: Spring (release to the nearest mood)

    static let springOmega = 2 * Double.pi / 0.42
    static let springZeta = 0.8

    static func spring(_ dt: Double, from x0: Double, velocity v0: Double, to target: Double) -> Double {
        let a = x0 - target
        let wd = springOmega * sqrt(1 - springZeta * springZeta)
        let b = (v0 + springZeta * springOmega * a) / wd
        return target + exp(-springZeta * springOmega * dt) * (a * cos(wd * dt) + b * sin(wd * dt))
    }

    static let returnDelay = 2.0
    static let returnTime = 0.9
}

/// Holds what a touch changed; every value is still read as a function of time.
final class GlassMoodBloomDriver {
    typealias M = GlassMoodBloomModel

    private var epoch: Double?
    private var samples: [(t: Double, v: Double)] = []
    private(set) var touching = false
    private var touchStart = Double.infinity
    private var release: (t: Double, x0: Double, vel: Double, target: Double)?
    private var rest = 0.0
    var auto = true
    var reduced = false

    func reset() {
        epoch = nil
        samples = []
        touching = false
        touchStart = .infinity
        release = nil
        rest = 0
    }

    /// Call once per frame with the frame's time, before reading values.
    func begin(_ now: Double) {
        if epoch == nil { epoch = now }
        if auto, !touching, let r = release, now > r.t + M.returnDelay + M.returnTime + 0.5 {
            epoch = r.t + M.returnDelay + M.returnTime
            release = nil
            samples = []
            touchStart = .infinity
        }
    }

    /// Loop time used for breathing and wobble (keeps running through a touch).
    func loopTime(_ T: Double) -> Double {
        let e = epoch ?? T
        if auto, let r = release, T >= r.t + M.returnDelay + M.returnTime {
            return T - (r.t + M.returnDelay + M.returnTime)
        }
        return T - e
    }

    /// 1 while the loop's breathing should show, fading to 0 during the ease back after a touch.
    func breatheWeight(_ T: Double) -> Double {
        guard auto, !reduced else { return 0 }
        if let r = release, !touching {
            let dt = T - r.t - M.returnDelay
            if dt >= 0 && dt < M.returnTime { return 1 - M.easeInOut(dt / M.returnTime) }
        }
        return 1
    }

    func value(_ T: Double) -> Double {
        if T < touchStart || samples.isEmpty {
            guard auto, !reduced else { return rest }
            return M.loopValue(T - (epoch ?? T))
        }
        if touching || release == nil || T < release!.t {
            if T <= samples[0].t { return samples[0].v }
            for k in stride(from: samples.count - 1, through: 0, by: -1) where samples[k].t <= T {
                if k == samples.count - 1 { return samples[k].v }
                let a = samples[k], b = samples[k + 1]
                let f = (T - a.t) / max(b.t - a.t, 1e-6)
                return a.v + (b.v - a.v) * f
            }
            return samples[0].v
        }
        let r = release!
        let dt = T - r.t
        if reduced { return r.target }
        if !auto || dt < M.returnDelay { return M.spring(dt, from: r.x0, velocity: r.vel, to: r.target) }
        if dt < M.returnDelay + M.returnTime {
            let a = M.spring(M.returnDelay, from: r.x0, velocity: r.vel, to: r.target)
            return a * (1 - M.easeInOut((dt - M.returnDelay) / M.returnTime))
        }
        return M.loopValue(dt - M.returnDelay - M.returnTime)
    }

    /// The finger went down: keep the last 0.3 s of the value as history so the lagging layers stay continuous.
    func touchDown(_ now: Double) -> Double {
        let current = value(now)
        var pre: [(t: Double, v: Double)] = []
        for k in 0...8 {
            let t = now - 0.3 + Double(k) * 0.0375
            pre.append((t, value(t)))
        }
        samples = pre
        touchStart = pre[0].t
        touching = true
        release = nil
        return current
    }

    func touchMove(_ now: Double, _ v: Double) {
        let c = min(max(v, -1), 1)
        samples.append((now, c))
        if samples.count > 240 { samples.removeFirst(samples.count - 240) }
    }

    func touchUp(_ now: Double) {
        touching = false
        let last = samples.last?.v ?? 0
        var vel = 0.0
        if let old = samples.last(where: { $0.t <= now - 0.06 }), let newest = samples.last, newest.t > old.t {
            vel = (newest.v - old.v) / max(now - old.t, 0.016)
        }
        vel = min(max(vel, -6), 6)
        let target = Double(M.bucket(last) - 3) / 3
        samples.append((now, last))
        release = (now, last, reduced ? 0 : vel, target)
        if !auto { rest = target }
    }
}
