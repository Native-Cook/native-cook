import Foundation

/// The RN script, palette and 240 Hz integrator table, without platform animation substitutes.
enum SparkSphereScoreMotion {
    static let loop = 8.0
    static let rest = 0.8
    static let lag = [0.15, 0.4, 0.27, 0.2, 0.06] // rim, hollow, film, card, score
    static let restMem = [0.0257, -2.0402, 0.01437, 0.43109, 0.0061, 0.53582, 0, 0]
    static func clamp(_ x: Double, _ a: Double, _ b: Double) -> Double { min(max(x, a), b) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a), 0, 1)
        return t * t * (3 - 2 * t)
    }
    static func wrap(_ t: Double) -> Double {
        ((t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop))
    }
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }
    /// JS toFixed(1) rounds decimal ties away from zero on the exact binary value (printf uses ties-to-even).
    static func fixed1(_ value: Double) -> String {
        let x = abs(value), scaled = x * 10, low = floor(scaled)
        let fraction = scaled - low, error = fma(x, 10, -scaled)
        let up = fraction > 0.5 || (fraction == 0.5 && error >= 0)
        let n = Int(low + (up ? 1 : 0))
        return (value < 0 ? "-" : "") + String(n / 10) + "." + String(n % 10)
    }
    static func inOut(_ p: Double) -> Double {
        let cx = 3 * 0.65, bx = 3 * (0.35 - 0.65) - cx, ax = 1 - cx - bx
        var u = p
        for _ in 0..<6 {
            let x = ((ax * u + bx) * u + cx) * u - p
            let dx = (3 * ax * u + 2 * bx) * u + cx
            if abs(dx) < 1e-6 { break }
            u -= x / dx
        }
        u = clamp(u, 0, 1)
        return (-2 * u + 3) * u * u
    }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        guard t > 0 else { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func scriptV(_ t0: Double) -> Double {
        let t = wrap(t0)
        if t < 0.6 { return rest }
        if t < 0.72 { return rest - 0.04 * sin(.pi * (t - 0.6) / 0.12) }
        if t < 2 { return mix(rest, 2.4, inOut((t - 0.72) / 1.28)) }
        if t < 2.6 { return 2.4 }
        if t < 3.8 { return mix(2.4, -0.6, inOut((t - 2.6) / 1.2)) }
        if t < 4.6 { return -0.6 }
        return mix(-0.6, rest, springAt(t - 4.6, 2 * .pi / sqrt(60), 14 / (2 * sqrt(60))))
    }
    static func scriptGrab(_ t0: Double) -> Double {
        let t = wrap(t0)
        if t < 0.6 { return 0 }
        if t < 0.72 { return smooth(0.6, 0.72, t) }
        if t < 4.6 { return 1 }
        return 1 - smooth(4.6, 4.9, t)
    }
    static func heat(_ v: Double) -> Double { clamp((v - rest) / (3 - rest), 0, 1) }
    static func cold(_ v: Double) -> Double { smooth(0.35, -0.55, v) }
    static func fill(_ v: Double) -> Double { 0.85 * smooth(0.88, 2.3, v) }
    static func score(_ v: Double) -> Double { 32.2 + 11.5 * (v - 1) }
    static func band(_ v: Double) -> Int { v < 0 ? 0 : v < 1 ? 1 : v < 2 ? 2 : 3 }
    static func rubber(_ over: Double) -> Double { over <= 0 ? 0 : 18 * (1 - exp(-over * 0.3 / 18)) }
    static func stepStir(_ s: inout [Double], _ v: Double, _ vdot: Double, _ dt: Double, _ extra: Double = 0) {
        let c = cold(v), h = heat(v), target = clamp(0.9 * vdot + extra, -4, 4)
        s[0] += (target - s[0]) * (1 - exp(-dt / 0.9))
        s[0] = clamp(s[0], -4, 4)
        let omega = s[0] + 0.9 * smooth(rest, 2.4, v) - 0.9 * smooth(0, -0.6, v)
        s[1] += (omega - s[1] / mix(1.4, 0.5, c)) * dt
        s[1] = clamp(s[1], -4.5, 4.5)
        s[2] += 0.9 * h * dt + (1 - h) * (jsRound(s[2]) - s[2]) * (1 - exp(-dt * 1.6))
    }
    struct Script {
        var swirl: [Double], agit: [Double], cross: [Double]
    }
    static let script: Script = {
        let n = 1920, dt = 1.0 / 240
        var swirl = [Double](repeating: 0, count: n + 1), agit = swirl, cross: [Double] = []
        var s = [0.0, 0, 0], prev = scriptV(0)
        for i in 1...n {
            let t = Double(i) * dt, v = scriptV(t)
            stepStir(&s, v, (v - prev) / dt, dt)
            if (prev - 1) * (v - 1) < 0 { cross.append(t - dt * 0.5) }
            prev = v
            swirl[i] = s[1] * (1 - smooth(5.8, 7.4, t))
            agit[i] = s[2]
        }
        let total = agit[n], k = max(jsRound(total), 1) / max(total, 1e-6)
        for i in 0...n { agit[i] *= k }
        return Script(swirl: swirl, agit: agit, cross: cross)
    }()
    static func lut(_ tab: [Double], _ t: Double) -> Double {
        let x = wrap(t) * 240, i = min(Int(floor(x)), tab.count - 2)
        return tab[i] + (tab[i + 1] - tab[i]) * (x - Double(i))
    }
    static func pulseAge(_ t0: Double) -> Double {
        let t = wrap(t0)
        return script.cross.reduce(99) { min($0, t >= $1 ? t - $1 : t + loop - $1) }
    }
    static func membrane(_ cyc: Double, _ heat: Double, _ cold: Double, _ ringAmp: Double, _ ringPhase: Double) -> [Double] {
        let weights = [1.0, 0.7, 0.5, 0.35], phases = [0.6, 2.1, -1.2, 0.4], cycles = [1.0, -2, 3, -1]
        let live = (0.5 + 4.5 * heat) * (1 - cold)
        var out = [Double](repeating: 0, count: 8)
        for k in 0..<4 {
            let e0 = restMem[2 * k] * (1 - 0.6 * cold), p0 = restMem[2 * k + 1]
            var a = live * weights[k] / 163.6, p = phases[k] + 2 * .pi * cycles[k] * cyc
            if k == 0 { a += ringAmp; p += ringPhase }
            let re = e0 * cos(p0) + a * cos(p), im = e0 * sin(p0) + a * sin(p)
            out[2 * k] = sqrt(re * re + im * im); out[2 * k + 1] = atan2(im, re)
        }
        return out
    }
    static func srgbToLin(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
    static func linToSrgb(_ c: Double) -> Double {
        let x = max(c, 0)
        return x <= 0.0031308 ? x * 12.92 : 1.055 * pow(x, 1 / 2.4) - 0.055
    }
    static let palette: [[Double]] = [
        [-1, 0.812, 1, 0.941], [-0.35, 0.49, 0.96, 0.84], [0.8, 0.18, 0.941, 0.627],
        [1.5, 0.784, 0.941, 0.227], [2, 1, 0.69, 0.18], [3, 1, 0.541, 0.18]
    ]
    static let labs: [[Double]] = palette.map { p in
        let r = srgbToLin(p[1]), g = srgbToLin(p[2]), b = srgbToLin(p[3])
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s]
    }
    static func colour(_ v: Double) -> [Double] {
        var i = 0
        while i < palette.count - 2 && v > palette[i + 1][0] { i += 1 }
        let t = clamp((v - palette[i][0]) / (palette[i + 1][0] - palette[i][0]), 0, 1)
        let lab = (0..<3).map { mix(labs[i][$0], labs[i + 1][$0], t) }
        let l = pow(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2], 3)
        let m = pow(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2], 3)
        let s = pow(lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2], 3)
        return [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s].map { clamp(linToSrgb($0), 0, 1) }
    }
}
