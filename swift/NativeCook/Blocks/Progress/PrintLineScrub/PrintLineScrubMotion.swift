import Foundation

/// Value-for-value translation of pls-motion.ts; the screen derives the band from filtered speed, as RN does.
enum PrintLineScrubMotion {
    static let loop = 7.6, live = 0.44
    struct Spring {
        let damping: Double, stiffness: Double, mass: Double
    }
    static let catchUp = Spring(damping: 20, stiffness: 90, mass: 1.2)
    static let yaw = Spring(damping: 16, stiffness: 120, mass: 1)
    static let duck = Spring(damping: 18, stiffness: 260, mass: 1)
    static let touch = Spring(damping: 22, stiffness: 380, mass: 1)
    static let arrow = Spring(damping: 16, stiffness: 300, mass: 0.8)
    static let pill = Spring(damping: 12, stiffness: 300, mass: 1)

    struct Look {
        var h = live, duck = 0.0, yaw = 0.0, heat = 0.0, band = 14.0
        var press = 0.0, bead = 0.0, speed = 0.0, flash = -1.0, over = 0.0, held = 0.0
        var tail = 0.0, gizDir = 1.0
    }

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double { let k = clamp01(x); return k * k * (3 - 2 * k) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
    static func rubber(_ over: Double, _ slack: Double) -> Double { over / (1 + over / slack) }
    static func wrap(_ t: Double) -> Double { ((t.truncatingRemainder(dividingBy: loop)) + loop).truncatingRemainder(dividingBy: loop) }

    static func springDisp(_ t: Double, _ x0: Double, _ v0: Double, _ s: Spring) -> Double {
        if t <= 0 { return x0 }
        let w0 = sqrt(s.stiffness / s.mass), z = s.damping / (2 * sqrt(s.stiffness * s.mass))
        if z < 0.9999 {
            let wd = w0 * sqrt(1 - z * z)
            return exp(-z * w0 * t) * (x0 * cos(wd * t) + ((v0 + z * w0 * x0) / wd) * sin(wd * t))
        }
        if z > 1.0001 {
            let ss = w0 * sqrt(z * z - 1), r1 = -z * w0 + ss, r2 = -z * w0 - ss
            let c2 = (v0 - r1 * x0) / (r2 - r1), c1 = x0 - c2
            return c1 * exp(r1 * t) + c2 * exp(r2 * t)
        }
        return exp(-w0 * t) * (x0 + (v0 + w0 * x0) * t)
    }
    static func springVel(_ t: Double, _ x0: Double, _ v0: Double, _ s: Spring) -> Double {
        let e = 0.004
        return (springDisp(t + e, x0, v0, s) - springDisp(max(t - e, 0), x0, v0, s)) / (t < e ? t + e : 2 * e)
    }
    static func volumeBelow(_ h: Double) -> Double {
        let values = PrintLineScrubFigure.volume, n = values.count - 1
        let k = clamp01(h) * Double(n), i = min(Int(floor(k)), n - 1)
        return mix(values[i], values[i + 1], k - Double(i))
    }
    /// The layer's share of the job, so the label always equals round(layer / 480 * 100) (RN e24cee56).
    static func percentAt(_ h: Double) -> Double { Double(layerAt(h)) / 480 * 100 }
    static let percentLive = 44
    static func layerAt(_ h: Double) -> Int { Int(floor(clamp01(h) * 480 + 0.5)) }
    static func pathH(_ t: Double) -> Double {
        if t < 0.5 { return live }
        if t < 1.5 { return mix(live, 0.3, easeInOut(seg(t, 0.5, 1.5))) }
        if t < 2.3 { return 0.3 }
        return mix(0.3, 0.6, easeInOut(seg(t, 2.3, 3.6)))
    }
    static func lookAt(_ time: Double) -> Look {
        let t = wrap(time)
        var k = Look()
        if t < 3.6 {
            k.h = pathH(t)
            k.speed = abs(pathH(t + 0.01) - pathH(t - 0.01)) / 0.02 * 500
            k.press = easeOutCubic(seg(t, 0.4, 0.65))
            k.duck = 62 * (1 + springDisp(max(t - 0.4, 0), -1, 0, duck))
            if t < 0.4 { k.duck = 0 }
            k.held = seg(t, 0.4, 0.4 + 0.2)
            let target = t < 2.3 ? 3.5 : mix(3.5, 1, easeInOut(seg(t, 2.3, 3.6)))
            k.yaw = target * (1 + springDisp(max(t - 1, 0), -1, 0, yaw)) * .pi / 180
            if t < 1 { k.yaw = 0 }
            k.heat = clamp01(abs(k.h - live) * 1.6 + 0.12 * k.press)
        } else {
            let tr = t - 3.6, h0 = pathH(3.6 - 0.001)
            let v0 = (pathH(3.6 - 0.0005) - pathH(3.6 - 0.0015)) / 0.001
            k.h = live + springDisp(tr, h0 - live, v0, catchUp)
            k.speed = abs(springVel(tr, h0 - live, v0, catchUp)) * 500
            k.held = 1 - seg(t, 4.3 + 0.1, 4.3 + 0.35)
            k.press = 1 - seg(t, 3.6, 3.6 + 0.4)
            k.duck = t < 4.3 ? 62 : max(62 * springDisp(max(t - 4.3, 0), 1, 0, duck), -4)
            k.yaw = springDisp(tr, Double.pi / 180, 0, yaw)
            let heat0 = clamp01(abs(h0 - live) * 1.6 + 0.12), tau = 0.5 * (1 + abs(v0 * 500) / 1200)
            k.heat = heat0 * exp(-tr / tau) * (1 - seg(t, 6.2, 7.0))
            let fl = (t - 4.55) / 0.6
            if fl >= 0 && fl <= 1 { k.flash = fl }
            k.over = t < 4.55 ? 0 : 5 * seg(t, 4.55, 4.55 + 0.05) * exp(-(t - 4.55) * 7) * cos((t - 4.55) * 24) * (t < 5.3 ? 1 : 0)
        }
        k.band = mix(14, 40, max(clamp01(k.heat), clamp01(k.speed / 1400)))
        k.bead = (t / (loop / 5)) * .pi * 2
        return k
    }

    /// A settled RN __pls.pin(t) has zero speed/tail: useFrameCallback recomputes them from the unchanged cut height.
    static func pinned(_ t: Double, reduce: Bool) -> Look {
        var k = lookAt(t)
        k.speed = 0; k.tail = 0; k.gizDir = 1
        k.band = reduce ? 4 : mix(14, 40, clamp01(k.heat))
        return k
    }
}
