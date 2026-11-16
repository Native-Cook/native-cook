import Foundation

/// All values and phase functions are transcribed from gpm-motion.ts and gpm-theme.ts.
enum GlassPourMenuMotion {
    static let loop = 7.2
    static let pinch = 2.510094355743, merge = 5.539456060903
    struct Spring {
        var damping: Double, stiffness: Double, mass: Double
        var omega: Double { sqrt(stiffness / mass) }
        var zeta: Double { damping / (2 * sqrt(stiffness * mass)) }
    }
    static let snap = Spring(damping: 30.5, stiffness: 322.3, mass: 1)
    static let glide = Spring(damping: 22.1, stiffness: 157.9, mass: 1)
    static let open = Spring(damping: 18, stiffness: 170, mass: 1)
    static let close = Spring(damping: 18.3, stiffness: 130.5, mass: 1)
    static let recoil = Spring(damping: 12, stiffness: 300, mass: 0.6)
    static let dimple = Spring(damping: 14, stiffness: 260, mass: 0.8)
    static let twist = Spring(damping: 12, stiffness: 120, mass: 1)
    static let satellite = Spring(damping: 16, stiffness: 240, mass: 0.5)
    static let lens = Spring(damping: 26, stiffness: 420, mass: 0.6)
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = seg(x, a, b); return t * t * (3 - 2 * t)
    }
    static func ease(_ x: Double) -> Double {
        let t = clamp(x); return t * t * t * (t * (t * 6 - 15) + 10)
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func step(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let w = s.omega, z = s.zeta
        if z >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - z * z)
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }
    static func impulse(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let decay = s.damping / (2 * s.mass)
        let wd = sqrt(s.stiffness / s.mass - decay * decay)
        let peakT = atan(wd / decay) / wd
        return exp(-decay * t) * sin(wd * t) / (exp(-decay * peakT) * sin(wd * peakT))
    }
    struct Geometry {
        var radius = 39.0, half = 125.0, pitch = 48.0, fontSize = 17.0, count = 4
        var beadY: Double { -107.76 * radius / 39 }
        var beadR: Double { radius * 0.98 }
        var top: Double { -54.75 * radius / 39 }
        var rows: [Double] { (0..<count).map { top + 27 + Double($0) * pitch } }
        // menuGeometry's bottom includes 21 pt below the final half-row: 161.25 by default.
        var bottom: Double { rows[count - 1] + pitch / 2 + 21 }
    }
    struct Frame {
        var p = 0.0, press = 0.0, lensY = -27.75, lensA = 0.0, flash = 0.0
        var dwell = 0.0, time = 0.0, twist = 0.0
        var event = -100.0, dip = 0.0
        var satelliteAge = -100.0, satelliteProgress = 0.0
    }
    static func timeline(_ raw: Double) -> Frame {
        let t = (raw.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let press = step(t - 0.5, snap) * (1 - step(t - 2.45, snap))
        var p = 0.62 * ease(seg(t, 0.55, 1.45))
        if t >= 2.45 { p = 0.62 + 0.38 * step(t - 2.45, open) }
        if t >= 5.45 { p = 1 - step(t - 5.45, close) }
        if t >= 6.55 { p = 0 }
        let rows = Geometry().rows
        var y = rows[0], from = 0
        for (start, to) in zip([3.5, 3.85, 4.2, 4.6], [1, 2, 3, 1]) {
            y += (rows[to] - rows[from]) * step(t - start, lens); from = to
        }
        let a = easeOut(seg(t, 3.15, 3.37)) * (1 - seg(t, 5.45, 5.61))
        let f = seg(t, 5.1, 5.45)
        let flash = f <= 0 || f >= 1 ? 0 : sin(.pi * sqrt(f))
        let event = t > 6.55 ? -1 : t - (t >= 5.45 ? merge : pinch)
        let turn = 8 * Double.pi / 180 * ease(clamp(progress(max(0, t - 0.07))))
        return Frame(p: p, press: press, lensY: y, lensA: a, flash: flash, time: t,
                     twist: turn + 1.2 * .pi / 180 * impulse(event, twist), event: event)
    }
    private static func progress(_ t: Double) -> Double {
        if t >= 6.55 { return 0 }
        if t >= 5.45 { return 1 - step(t - 5.45, close) }
        if t >= 2.45 { return 0.62 + 0.38 * step(t - 2.45, open) }
        return 0.62 * ease(seg(t, 0.55, 1.45))
    }
    struct Glass {
        var beadY: Double, beadR: Double, boxY: Double, boxW: Double, boxH: Double, boxR: Double
        var k: Double, thick: Double, hot: Double, frost: Double, open: Double, glyphK: Double
        var rows: [Double], rim: Double, neckA: Double, neckB: Double, neckR: Double
    }
    static func glass(_ f: Frame, _ g: Geometry, reduced: Bool = false, auto: Bool = false) -> Glass {
        let p = clamp(f.p), r = g.radius, scale = r / 39
        let held = smooth(0.55, 0.6, f.p) * (1 - smooth(0.64, 0.7, f.p))
        var sag = reduced ? 0 : 1 - cos(2 * .pi * f.time / 4.8)
        if auto && f.time > 6.55 { sag *= 1 - smooth(6.55, 6.9, f.time) }
        sag *= 1 - smooth(0.05, 0.35, p)
        let wave = reduced ? 0 : sin(2 * .pi * 1.5 * f.time) * held
        let lift = ease(seg(p, 0, 0.62))
        let beadR = mix(r, g.beadR, lift) * (1 - 0.06 * f.press * (1 - lift))
        let early = -6 * scale - beadR
        let base = p < 0.35 ? early * ease(seg(p, 0, 0.35)) : mix(early, g.beadY, easeOut(seg(p, 0.35, 0.62)))
        let beadY = base + sag + wave + (reduced ? 0 : 5 * impulse(f.event, recoil))
        let gather = ease(seg(p, 0, 0.35)), level = ease(seg(p, 0.35, 1))
        let sagR = mix(r, 52 * scale, gather), sagY = 46 * scale * gather + sag
        let bottom = mix(sagY + sagR, g.bottom, level) + max(f.p - 1, 0) * 175 * 0.35 + f.dip
        let gap = scale * (28 * easeOut(seg(p, 0.35, 0.62)) + 6 * smooth(0.62, 0.7, p))
        let linked = beadY + beadR + gap
        let top = mix(sagY - sagR, linked, smooth(0.18, 0.35, p)) * (1 - smooth(0.7, 1, p))
            + g.top * smooth(0.7, 1, p) + (reduced ? 0 : -3 * impulse(f.event, dimple))
        let bh = max(1, (bottom - top) / 2)
        let bw = mix(sagR, g.half, easeOut(seg(p, 0.35, 0.94)))
        let br = mix(min(bw, bh), 21.3, smooth(0.7, 1, p))
        let nr = (mix(22 * scale, 4.5, easeOut(seg(p, 0.35, 0.62))) + 0.6 * wave)
            * smooth(0.18, 0.35, p) * (1 - smooth(0.68, 0.7, p))
        let area = 4 * bw * bh - (4 - .pi) * br * br + .pi * beadR * beadR * lift
        let frost = smooth(0.84, 0.95, p)
        let rows = (0..<g.count).map { i in
            frost >= 1 ? smooth(0.95 + 0.028 * Double(i) / Double(max(1, g.count - 1)),
                                0.985 + 0.015 * Double(i) / Double(max(1, g.count - 1)), p) : 0
        }
        return Glass(beadY: beadY, beadR: beadR, boxY: (top + bottom) / 2, boxW: bw, boxH: bh, boxR: br,
                     k: 16 * smooth(0, 0.12, p), thick: min(.pi * r * r / max(area, 1), 1),
                     hot: 0.55 * smooth(0.04, 0.3, p) * (1 - smooth(0.7, 1, p)), frost: frost,
                     open: smooth(0.15, 0.95, p), glyphK: smooth(0.3, 0.9, p), rows: rows,
                     rim: mix(r, 16, smooth(0.6, 1, p)), neckA: beadY + beadR - 9, neckB: top + 9, neckR: nr)
    }
}
