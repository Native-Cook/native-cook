import Foundation

enum BreathVolumeLungsMotion {
    static let loop = 8.5
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double {
        max(lo, min(hi, x.isFinite ? x : lo))
    }
    static func smooth(_ x: Double) -> Double { let a = clamp(x); return a * a * (3 - 2 * a) }
    static func wrap(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop) }
    static func progress(_ time: Double) -> Double {
        let t = wrap(time)
        if t < 0.7 { return 0 }
        if t < 3.9 { return 1 - pow(1 - smooth((t - 0.7) / 3.2), 1.45) }
        if t < 6.3 { return 1 }
        if t < 7.9 { return 1 - smooth((t - 6.3) / 1.6) }
        return 0
    }
    static func flow(_ t: Double) -> Double { clamp((progress(t + 0.001) - progress(t - 0.001)) / 0.002 * 5.5, -1.13, 1.13) }
    static func elapsed(_ time: Double) -> Double {
        let t = wrap(time)
        return clamp(t - 0.7, 0, 3.2) * (1 - smooth((t - 6.3) / 1.6))
    }
    static func result(_ time: Double) -> Double {
        let t = wrap(time)
        return smooth((t - 4.3) / 0.2) * (1 - smooth((t - 6.3) / 0.2))
    }
    static func lastAir(_ time: Double) -> Double {
        let t = wrap(time)
        return smooth((t - 3.75) / 0.15) * (1 - smooth((t - 3.9) / 0.4))
    }
    static func breathe(_ t: Double) -> Double { 0.5 - 0.5 * cos(4 * .pi * t / loop) }
    static func springAt(_ t: Double, _ response: Double, _ ratio: Double) -> Double {
        guard t > 0 else { return 0 }
        let omega = 2 * .pi / response, wd = omega * sqrt(max(0.0001, 1 - ratio * ratio))
        return 1 - exp(-ratio * omega * t) * (cos(wd * t) + ratio * omega / wd * sin(wd * t))
    }
    static func held(_ start: Double, _ t: Double) -> Double {
        let integral = t < 0.25 ? t * t / 0.5 : t - 0.125
        return clamp(1 - pow(max(0, pow(1 - clamp(start), 0.4) - 0.4 * 0.42 * integral), 2.5))
    }
    static func coast(_ start: Double, _ velocity: Double, _ t: Double) -> Double {
        let travel = velocity * 0.6 * (1 - exp(-t / 0.6))
        if velocity < 0 { return clamp(start + travel) }
        return clamp(1 - pow(max(0, pow(1 - clamp(start), 0.4) - 0.4 * travel), 2.5))
    }
    static func rubber(_ p: Double, _ travel: Double) -> Double {
        let bound = clamp(p)
        return bound + 14 * tanh((p - bound) * travel * 0.3 / 14) / travel
    }
    /// Closed-form mass=1 spring used by the reference's Reanimated physics springs.
    static func spring(_ age: Double, from: Double, to: Double, velocity: Double = 0,
                       stiffness: Double, damping: Double) -> (value: Double, velocity: Double) {
        let a = damping / 2, w = sqrt(stiffness - a * a), x = from - to
        let b = (velocity + a * x) / w, e = exp(-a * max(0, age))
        let c = cos(w * max(0, age)), s = sin(w * max(0, age))
        return (to + e * (x * c + b * s), e * ((b * w - a * x) * c - (x * w + a * b) * s))
    }
    static let crossings: [Double] = (1...5).map { n in
        var a = 0.7, b = 3.9
        for _ in 0..<24 { let m = (a + b) / 2; if progress(m) * 5.5 < Double(n) { a = m } else { b = m } }
        return (a + b) / 2
    }
    static func heat(_ p: Double) -> [Double] {
        func lin(_ x: Double) -> Double { x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
        func gamma(_ x: Double) -> Double { clamp(x <= 0.0031308 ? 12.92 * x : 1.055 * pow(max(0, x), 1 / 2.4) - 0.055) }
        func lab(_ c: [Double]) -> [Double] {
            let r = lin(c[0]), g = lin(c[1]), b = lin(c[2])
            let l = cbrt(0.41222147 * r + 0.53633254 * g + 0.05144599 * b)
            let m = cbrt(0.2119035 * r + 0.68069955 * g + 0.10739696 * b)
            let s = cbrt(0.08830246 * r + 0.28171884 * g + 0.6299787 * b)
            return [0.21045426 * l + 0.79361779 * m - 0.00407205 * s,
                    1.9779985 * l - 2.42859221 * m + 0.45059371 * s,
                    0.02590404 * l + 0.78277177 * m - 0.80867577 * s]
        }
        let a = lab(BreathVolumeLungsResources.rgb(p < 0.5 ? 0x3A2F8F : 0xB5308F))
        let b = lab(BreathVolumeLungsResources.rgb(p < 0.5 ? 0xB5308F : 0xE0382B))
        let f = p < 0.5 ? p * 2 : (p - 0.5) * 2
        let ha = atan2(a[2], a[1]), hb = atan2(b[2], b[1])
        let dh = (hb - ha + .pi * 3).truncatingRemainder(dividingBy: .pi * 2) - .pi
        let h = ha + dh * f, chroma = hypot(a[1], a[2]) * (1 - f) + hypot(b[1], b[2]) * f
        let light = a[0] * (1 - f) + b[0] * f, aa = chroma * cos(h), bb = chroma * sin(h)
        let l = pow(light + 0.39633778 * aa + 0.21580376 * bb, 3)
        let m = pow(light - 0.10556135 * aa - 0.06385417 * bb, 3)
        let s = pow(light - 0.08948418 * aa - 1.29148555 * bb, 3)
        return [gamma(4.07674166 * l - 3.30771159 * m + 0.23096993 * s),
                gamma(-1.268438 * l + 2.6097574 * m - 0.3413194 * s),
                gamma(-0.00419609 * l - 0.70341861 * m + 1.7076147 * s)]
    }
}
