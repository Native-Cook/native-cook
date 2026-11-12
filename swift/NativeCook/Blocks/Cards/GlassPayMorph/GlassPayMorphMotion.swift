import Foundation

/// Exact PCHIP tracks from the approved React Native gpm-motion.ts, in 402 × 874 pt space.
enum GlassPayMorphMotion {
    static let loop = 4.4
    static let collapse0 = 0.3, collapse1 = 1.3, dock = 1.34, pulse = 1.6
    static let paidHold = 2.2, expand0 = 2.8, expand1 = 3.9
    static let heroX = 207.0, heroY = 416.0
    static let referenceCardW = 282.0, referenceCardH = 168.0
    static let trail = 8

    struct Track {
        let t: [Double], v: [Double], m: [Double]
        init(_ keys: [[Double]]) {
            t = keys.map { $0[0] }; v = keys.map { $0[1] }
            let n = keys.count
            let d = (0..<(n - 1)).map { (keys[$0 + 1][1] - keys[$0][1]) / (keys[$0 + 1][0] - keys[$0][0]) }
            var slopes = [Double](repeating: 0, count: n)
            for i in 1..<(n - 1) where d[i - 1] * d[i] > 0 {
                let h0 = t[i] - t[i - 1], h1 = t[i + 1] - t[i]
                let w1 = 2 * h1 + h0, w2 = h1 + 2 * h0
                slopes[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
            }
            m = slopes
        }
        func at(_ x: Double) -> Double {
            if x <= t[0] { return v[0] }
            if x >= t[t.count - 1] { return v[v.count - 1] }
            var i = 0
            while i < t.count - 2 && x > t[i + 1] { i += 1 }
            let h = t[i + 1] - t[i], s = (x - t[i]) / h, s2 = s * s, s3 = s2 * s
            return (2 * s3 - 3 * s2 + 1) * v[i] + (s3 - 2 * s2 + s) * h * m[i]
                + (-2 * s3 + 3 * s2) * v[i + 1] + (s3 - s2) * h * m[i + 1]
        }
    }

    static let kc = [0.0, 0.6, 0.77, 0.83, 0.9, 1.0, 1.1, 1.17, 1.23, 1.3]
    static let ke = [2.8, 3.0, 3.1, 3.17, 3.23, 3.3, 3.37, 3.47, 3.57, 3.67, 3.9, loop]
    static func zip(_ values: [Double]) -> Track { Track((kc + ke).enumerated().map { [$0.element, values[$0.offset]] }) }
    static let aKeys: [[Double]] = [
  [0, -116, -47, 86], [0.3, -116, -47, 86], [0.4, -110, -51, 86], [0.5, -105, -55, 86], [0.57, -98, -59, 85],
  [0.63, -83, -63, 83], [0.7, -54, -66, 81], [0.73, -30, -64, 81], [0.77, 5, -60, 81], [0.8, 39, -49, 70],
  [0.83, 84, -58, 36], [0.87, 112, -52, 25], [0.9, 132, -45, 24], [0.93, 141, -35, 24], [0.97, 145, -24, 25],
  [1.0, 141, -15, 25], [1.03, 135, -10, 26], [1.07, 128, -7, 28], [1.1, 119, -5, 30], [1.13, 106, -4, 33],
  [1.17, 85, -3, 36], [1.23, 44, 0, 40], [1.3, 15, 2, 43], [1.4, 8, 2, 44],
  [2.8, 8, 2, 44], [2.97, 8 + 2, 2 + 1, 43], [3.03, 20, 7, 35], [3.07, 31, 12, 31], [3.13, 36, 26, 29],
  [3.17, 37, 38, 25], [3.2, 35, 52, 23], [3.23, 24, 70, 24], [3.27, 2, 88, 34], [3.3, -24, 93, 40],
  [3.33, -46, 95, 46], [3.37, -72, 88, 62], [3.4, -96, 70, 76], [3.43, -110, 53, 80], [3.47, -118, 35, 84],
  [3.53, -128, 8, 86], [3.57, -131, -4, 86], [3.63, -130, -23, 86], [3.7, -126, -34, 86], [3.8, -120, -44, 86],
  [3.9, -116, -47, 86], [4.4, -116, -47, 86],
]
    static let rKeys: [[Double]] = [
  [0, 131, 72, 58], [0.3, 131, 72, 58], [0.4, 124, 80, 57], [0.5, 109, 91, 57], [0.57, 92, 99, 57],
  [0.63, 70, 108, 55], [0.7, 40, 116, 46], [0.73, 14, 124, 38], [0.77, -22, 123, 30], [0.8, -55, 118, 27],
  [0.83, -98, 101, 25], [0.87, -124, 86, 25], [0.9, -141, 69, 25], [0.93, -150, 54, 26], [0.97, -152, 41, 28],
  [1.0, -151, 31, 30], [1.03, -148, 23, 32], [1.07, -144, 17, 35], [1.1, -136, 11, 37], [1.13, -122, 4, 39],
  [1.17, -106, -1, 39], [1.23, -76, -7, 36], [1.3, -52, -12, 32], [1.4, -40, -14, 30],
  [2.8, -40, -14, 30], [2.93, -40 - 1, -14 - 2, 30], [3.0, -42, -24, 27], [3.03, -44, -34, 25],
  [3.07, -36, -56, 24], [3.1, -18, -72, 26], [3.13, 8, -84, 27], [3.17, 36, -90, 27], [3.2, 62, -89, 27],
  [3.23, 92, -82, 28], [3.27, 130, -64, 38], [3.3, 152, -40, 46], [3.33, 160, -18, 46], [3.37, 162, -5, 50],
  [3.4, 164, 10, 52], [3.47, 160, 34, 52], [3.53, 153, 49, 56], [3.57, 150, 56, 58], [3.67, 142, 65, 58],
  [3.8, 134, 69, 58], [3.9, 131, 72, 58], [4.4, 131, 72, 58],
]
    static let aSpan = Track([[0, 0.06], [1.0, 0.06], [1.12, 0.02], [2.9, 0.02], [2.98, 0.06], [3.1, 0.11], [3.28, 0.14], [3.47, 0.06], [loop, 0.06]])
    static let rSpan = Track([[0, 0.075], [1.0, 0.075], [1.12, 0.025], [2.88, 0.025], [2.95, 0.075], [3.06, 0.16], [3.2, 0.16], [3.32, 0.07], [loop, 0.075]])
    static func heads(_ keys: [[Double]], _ span: Track, _ k: Int) -> Track {
        var sh = keys.map { [$0[0] - 0.25 * span.at($0[0]), $0[1 + k]] }
        for i in 1..<sh.count where sh[i][0] <= sh[i - 1][0] { sh[i][0] = sh[i - 1][0] + 1e-3 }
        return Track(sh)
    }
    static let ax = heads(aKeys, aSpan, 0), ay = heads(aKeys, aSpan, 1)
    static let rx = heads(rKeys, rSpan, 0), ry = heads(rKeys, rSpan, 1)
    static let ar = Track(aKeys.map { [$0[0], $0[3]] }), rr = Track(rKeys.map { [$0[0], $0[3]] })
    static let cardX = zip([0, 0, 0, 0, 0, -1, -2, -4, -5, -6, -6, -6, -6, -7, -10, -12, 6, 7, 5, 2, 0, 0])
    static let cardW = zip([282, 282, 265, 233, 218, 208, 206, 204, 196, 192, 192, 192, 196, 200, 205, 240, 250, 262, 280, 282, 282, 282])
    static let cardH = zip([168, 168, 160, 141, 132, 126, 124, 123, 123, 123, 123, 123, 123, 125, 135, 143, 150, 158, 167, 168, 168, 168])
    static let cardR = zip([15, 15, 16, 18, 20, 23, 28, 45, 60, 123 / 2, 123 / 2, 123 / 2, 61, 58, 44, 28, 18, 15, 15, 15, 15, 15])
    static let roll = zip([-3, -3, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.5, -1.5, -2.5, -3, -3, -3])
    static let frostA = Track([[0, 1], [0.94, 1], [1.0, 0], [3.19, 0], [3.27, 1], [loop, 1]])
    static let frostR = Track([[0, 1], [0.94, 1], [1.0, 0], [3.4, 0], [3.49, 1], [loop, 1]])
    static let turn = Track([[2.8, 180], [3.2, 180], [3.23, 162], [3.27, 140], [3.3, 114], [3.315, 90], [3.33, 74], [3.37, 55], [3.4, 47], [3.47, 33], [3.57, 21], [3.67, 12], [3.77, 5], [3.9, 0], [loop, 0]])

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let k = clamp01((x - a) / (b - a)); return k * k * (3 - 2 * k)
    }
    static func wrap(_ t: Double) -> Double {
        let m = t.truncatingRemainder(dividingBy: loop); return m < 0 ? m + loop : m
    }
    static func springBump(_ u: Double, _ response: Double, _ damping: Double) -> Double {
        if u <= 0 { return 0 }
        let w = 2 * Double.pi / response, wd = w * sqrt(1 - damping * damping)
        let tp = atan(wd / (damping * w)) / wd
        let peak = exp(-damping * w * tp) * sin(wd * tp)
        return exp(-damping * w * u) * sin(wd * u) / peak
    }
    static func restWeight(_ t: Double) -> Double { 1 - smooth(0.12, 0.34, t) + smooth(3.6, 3.9, t) }
    static func lightAt(_ tr: Double, _ xs: Track, _ ys: Track, _ amber: Bool, _ gain: Double, _ sign: Double, _ ux: Double, _ uy: Double) -> [Double] {
        let t = wrap(tr), w = 2 * Double.pi / loop
        let dx = amber ? 4 * sin(w * t) : 5 * sin(2 * w * t)
        let dy = amber ? 3 * sin(2 * w * t) : 4 * (1 - cos(2 * w * t))
        let k = restWeight(t) * gain
        let j = t < dock || t > expand0 ? 0 : 3 * springBump(t - dock, 0.4, 0.55) * (1 - smooth(2.5, 2.8, t)) * sign
        return [heroX + xs.at(t) + dx * k + j * ux, heroY + ys.at(t) + dy * k + j * uy]
    }
    static func sceneAt(_ tau: Double, _ m: Mods = Mods()) -> Scene {
        let t = wrap(tau), rw = restWeight(t) * m.driftGain, w0 = 2 * Double.pi / loop
        let float = sin(w0 * t)
        var turn = t < collapse1 ? 0 : t < expand0 ? 180 : Self.turn.at(t)
        turn += m.yaw
        let pulse = t >= Self.pulse && t < expand0 ? t - Self.pulse : -1
        let breath = pulse >= 0 ? 0.04 * springBump(pulse, 0.4, 0.6) : 0
        let dl = hypot(48.0, 16.0), ux = 48.0 / dl, uy = 16.0 / dl
        var a: [Double] = [], rd: [Double] = []
        let ar = Self.ar.at(t), rr = Self.rr.at(t)
        let aSpan = Self.aSpan.at(t) * m.nu, rSpan = Self.rSpan.at(t) * m.nu
        for k in 0..<trail {
            let f = Double(k) / Double(trail - 1), taper = 1 - 0.3 * f, ta = f * 0.056
            let pa = lightAt(t - f * aSpan, ax, ay, true, m.driftGain, -1, ux, uy)
            let pr = lightAt(t - f * rSpan, rx, ry, false, m.driftGain, 1, ux, uy)
            a.append(pa[0] + m.parallax + m.aOff[0] - m.aOff[2] * ta)
            a.append(pa[1] + m.aOff[1] - m.aOff[3] * ta)
            a.append(ar * taper)
            rd.append(pr[0] + m.parallax * 1.3 + m.rOff[0] - m.rOff[2] * ta)
            rd.append(pr[1] + m.rOff[1] - m.rOff[3] * ta)
            rd.append(rr * taper)
        }
        var face = 1.0, textName = 1.0, textNumber = 1.0, textChip = 1.0, chipLines = 1.0, rings = 1.0, contactless = 1.0
        if t >= collapse0 && t < expand0 {
            textName = 1 - seg(t, 1.06, 1.15); contactless = 1 - seg(t, 1.08, 1.17)
            textChip = 1 - seg(t, 1.1, 1.19); textNumber = 1 - seg(t, 1.14, 1.25)
        } else if t >= expand0 && t < expand1 {
            face = turn < 90 ? clamp01((cos(turn * .pi / 180) - 0.05) / 0.25) : 0
            rings = easeInOutCubic(seg(t, 3.37, 3.56)); chipLines = easeInOutCubic(seg(t, 3.5, 3.72))
            contactless = easeOutCubic(seg(t, 3.6, 3.76))
        }
        if m.yaw != 0 && t < collapse0 { face = clamp01((cos(turn * .pi / 180) - 0.15) / 0.4) }
        let caption = t < expand0 ? easeOutCubic(seg(t, 1.3, 1.5)) : 1 - easeOutCubic(seg(t, 3.57, 3.77))
        let dock = t < expand0 ? smooth(1, 1.3, t) : 1 - smooth(3, 3.45, t)
        let morph = t < expand0 ? smooth(0.6, 1.3, t) : 1 - smooth(3.1, 3.6, t)
        return Scene(t: t, cx: heroX + cardX.at(t), cy: heroY + 2 * float * rw,
                     w: cardW.at(t), h: cardH.at(t), r: cardR.at(t), roll: roll.at(t) + 0.5 * float * rw,
                     turn: turn, scale: m.press * (1 + breath), a: a, rd: rd,
                     frostA: frostA.at(t), frostR: frostR.at(t), dock: dock, tint: 0.062 + 0.045 * morph,
                     face: face, textName: textName, textNumber: textNumber, textChip: textChip,
                     chipLines: chipLines, rings: rings, contactless: contactless, caption: caption, pulse: pulse,
                     flash: pulse >= 0 ? exp(-pulse / 0.09) * seg(pulse, -0.001, 0.03) : 0,
                     bright: pulse >= 0 ? exp(-pulse / 0.08) : 0,
                     sheen: t >= 3.37 && t <= 3.67 ? (t - 3.37) / 0.3 : -1,
                     glint: t >= 3.62 && t <= 3.8 ? sin(.pi * seg(t, 3.62, 3.8)) : 0, crawl: t / loop)
    }
    /// Card top-left pt → screen pt, row-major 3 × 3, identical to RN.
    static func cardMatrix(_ s: Scene) -> [Double] {
        let th = s.turn * .pi / 180, ro = s.roll * .pi / 180
        let ct = cos(th), st = sin(th), cr = cos(ro), sr = sin(ro), g = st / 900
        let a = cr * ct + s.cx * g, b = -sr, d = sr * ct + s.cy * g, e = cr
        let k = s.scale, hx = -k * s.w / 2, hy = -k * s.h / 2
        return [a * k, b * k, a * hx + b * hy + s.cx, d * k, e * k, d * hx + e * hy + s.cy, g * k, 0, g * hx + 1]
    }
    /// Screen pt → centred card-local pt, identical adjugate and evaluation order to RN.
    static func cardInverse(_ s: Scene) -> [Double] {
        let th = s.turn * .pi / 180, ro = s.roll * .pi / 180
        let ct = cos(th), st = sin(th), cr = cos(ro), sr = sin(ro), g = st / 900, k = s.scale
        let m00 = (cr * ct + s.cx * g) * k, m01 = -sr * k, m02 = s.cx
        let m10 = (sr * ct + s.cy * g) * k, m11 = cr * k, m12 = s.cy, m20 = g * k
        let c00 = m11, c01 = -m01, c02 = m01 * m12 - m02 * m11
        let c10 = m12 * m20 - m10, c11 = m00 - m02 * m20, c12 = m02 * m10 - m00 * m12
        let c20 = -m11 * m20, c21 = m01 * m20, c22 = m00 * m11 - m01 * m10
        let inv = 1 / (m00 * c00 + m01 * c10 + m02 * c20)
        return [c00 * inv, c01 * inv, c02 * inv, c10 * inv, c11 * inv, c12 * inv, c20 * inv, c21 * inv, c22 * inv]
    }
    static func progressOf(_ tau: Double) -> Double {
        let t = wrap(tau)
        if t < collapse0 || t >= expand1 { return 0 }
        if t <= collapse1 { return (t - collapse0) / (collapse1 - collapse0) }
        if t < expand0 { return 1 }
        return 1 - (t - expand0) / (expand1 - expand0)
    }

    struct Mods {
        var nu = 1.0, driftGain = 1.0, yaw = 0.0, press = 1.0, parallax = 0.0
        var aOff = [0.0, 0, 0, 0], rOff = [0.0, 0, 0, 0]
    }
    struct Scene {
        var t: Double, cx: Double, cy: Double, w: Double, h: Double, r: Double, roll: Double, turn: Double, scale: Double
        var a: [Double], rd: [Double]
        var frostA: Double, frostR: Double, dock: Double, tint: Double, face: Double
        var textName: Double, textNumber: Double, textChip: Double, chipLines: Double, rings: Double, contactless: Double
        var caption: Double, pulse: Double, flash: Double, bright: Double, sheen: Double, glint: Double, crawl: Double
    }
}
