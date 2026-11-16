import Foundation

/// The RN optics and analytic timeline, in design points. No accumulated autoplay state.
enum BeamShapeTorchMotion {
    static let loop = 9.0
    struct Look {
        var b = 1.0, w = 1.0, m = 1.0, mw = 1.0, press = 1.0, hot = 1.0, phase = 0.0, after = 0.0
    }
    struct Optics {
        var theta: Double, reach: Double, gain: Double, hw: Double, sag: Double
        var reachMax: Double, hwMax: Double, sagMax: Double, dots: Double, arcAlpha: Double, glyph: Double
    }
    struct Arc {
        var straight: Bool, cy: Double, r: Double, half: Double, ex: Double, ey: Double, tx: Double, ty: Double
    }
    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let k = clamp01((x - a) / (b - a)); return k * k * (3 - 2 * k)
    }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func rubber(_ x: Double) -> Double {
        if x < 0 { return -(0.55 * -x) / (1 + -x / 0.25) }
        if x > 1 { return 1 + 0.55 * (x - 1) / (1 + (x - 1) / 0.25) }
        return x
    }
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        var u = x
        for _ in 0..<6 {
            let cx = 3 * x1 * u * (1 - u) * (1 - u) + 3 * x2 * u * u * (1 - u) + u * u * u - x
            let dx = 3 * x1 * (1 - u) * (1 - 4 * u + 3 * u * u) + 3 * x2 * u * (2 - 3 * u) + 3 * u * u
            if abs(dx) < 1e-6 { break }
            u = clamp01(u - cx / dx)
        }
        return 3 * y1 * u * (1 - u) * (1 - u) + 3 * y2 * u * u * (1 - u) + u * u * u
    }
    static func inOut(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }
    static func spreadAt(_ t: Double) -> Double {
        if t <= 0.5 { return 1 }
        if t < 1.54 { return 1 - pow((t - 0.5) / 1.04, 1.8) }
        if t < 3.1 {
            let dt = t - 1.54, wd = sqrt(195 - 9.5 * 9.5)
            let hunt = -1.37 / wd * exp(-9.5 * dt) * sin(wd * dt)
            return hunt * (1 - smooth(2.05, 2.2, t))
        }
        return springAt(t - 3.1, 1, 0.8)
    }
    static func brightnessAt(_ t: Double) -> Double {
        if t < 4.3 { return 1 }
        if t < 6.8 { return 1 - inOut((t - 4.3) / 0.8) }
        return springAt(t - 6.8, 0.9, 1)
    }
    static func morphAt(_ t: Double) -> Double {
        if t < 6.3 { return 1 - springAt(t - 5.2, 0.8, 0.9) }
        return springAt(t - 6.3, 0.75, 0.8)
    }
    static func afterAt(_ t: Double) -> Double {
        let k = t - 3.1
        if k < 0 || k > 0.98 { return 0 }
        return smooth(0, 0.08, k) * (1 - smooth(0.08, 0.98, k))
    }
    static func lookAt(_ t: Double) -> Look {
        let tt = (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        if tt >= 7.95 { return Look() }
        let b = brightnessAt(tt), settle = smooth(7.65, 7.95, tt)
        let press = 1 + 0.012 * (springAt(tt - 0.45, 0.35, 0.85) - springAt(tt - 2, 0.35, 0.85))
        return Look(b: mix(b, 1, settle), w: mix(spreadAt(tt), 1, settle),
                    m: mix(morphAt(tt), 1, settle), mw: mix(morphAt(tt - 0.05), 1, settle),
                    press: mix(press, 1, settle), hot: mix(max(b, 0.85 * brightnessAt(tt - 0.14)), 1, settle),
                    phase: tt / loop, after: afterAt(tt))
    }
    static func opticsOf(_ b: Double, _ w: Double) -> Optics {
        let wc = min(max(w, -0.08), 1.06), deg = max(mix(4, 40, wc), 1.2), theta = deg * .pi / 180
        let w01 = clamp01(w), reachMax = 135 * mix(1.59, 1, w01), on = smooth(0, 0.3, b)
        let reach = mix(22, reachMax, clamp01(b)), kHw = mix(13.0 / 236, 72.0 / 135, w01)
        let curve = pow(w01, 1.5) * 34 / 135
        let ratio = (1 - cos(40 * Double.pi / 180)) / max(1 - cos(max(theta, 0.035)), 1e-5)
        return Optics(theta: theta, reach: reach, gain: min(pow(ratio, 0.45), 7.5),
                      hw: mix(20, reach * kHw, on), sag: reach * curve * on,
                      reachMax: reachMax, hwMax: reachMax * kHw, sagMax: reachMax * curve,
                      dots: (1 - smooth(0.86, 0.97, b)) * smooth(0.03, 0.14, b),
                      arcAlpha: mix(0.5, 1, on), glyph: mix(0.45, 1, smooth(0, 0.25, b)))
    }
    static func arcGeometry(_ cx: Double, _ apexY: Double, _ hw: Double, _ sag: Double) -> Arc {
        let h = max(hw, 0.5)
        if sag < 0.3 { return Arc(straight: true, cy: apexY, r: 0, half: 0, ex: cx + h, ey: apexY, tx: 1, ty: 0) }
        let r = (h * h + sag * sag) / (2 * sag), cy = apexY + r, half = asin(min(h / r, 1))
        return Arc(straight: false, cy: cy, r: r, half: half, ex: cx + r * sin(half), ey: cy - r * cos(half), tx: cos(half), ty: sin(half))
    }
    static func landing(_ reach: Double, _ w: Double, _ b: Double) -> Double {
        smooth(190, 212, reach) * smooth(0.93, 1, 1 - clamp01(w)) * clamp01(b)
    }
    static func sunOffset(_ b: Double, _ w: Double) -> CGPoint {
        let o = opticsOf(b, w), g = arcGeometry(0, 0, o.hw, o.sag)
        return CGPoint(x: g.ex + g.tx * 19, y: o.reach - g.ey - g.ty * 19)
    }
    static func solveSun(_ fx: Double, _ fy: Double, _ b0: Double, _ w0: Double) -> (b: Double, w: Double) {
        let grab = sunOffset(clamp01(b0), clamp01(w0)), o = opticsOf(b0, w0)
        let phi = o.theta * 0.89, rho = o.reach + 19
        let x = fx + rho * sin(phi) - grab.x, y = fy + rho * cos(phi) - grab.y
        let deg = atan2(abs(x), y) * 180 / (.pi * 0.89), w = (deg - 4) / 36
        let maxReach = opticsOf(1, clamp01(w)).reachMax, reach = hypot(x, y) - 19
        return ((reach - 22) / (maxReach - 22), w)
    }
}
