import Foundation

struct WovenSpeakerPlayerLook {
    var pos = 0.0, glyph = 0.0, press = 1.0, bloom = 0.65, bloomR = 60.0
    var lamp = 0.12, reach = 18.0, title = 0.0, line = 0.0, pill = 0.0, refl = 0.0
    var turn = 0.0, psiO = 0.0, psiI = 0.0, omO = 0.0, omI = 0.0, shear = 0.0, finger = 0.0
    var dentX = 0.0, dentY = 0.0, dent = 0.0
    var r0 = 0.0, a0 = 0.0, r1 = 0.0, a1 = 0.0, r2 = 0.0, a2 = 0.0, r3 = 0.0, a3 = 0.0
    var wash = 0.0, pulse = 0.0, pull = 0.0, pullAt = 0.0, handle = 0.0, head = 0.0
    mutating func ring(_ i: Int, _ r: Double, _ a: Double) {
        switch i { case 0: r0 = r; a0 = a; case 1: r1 = r; a1 = a; case 2: r2 = r; a2 = a; default: r3 = r; a3 = a }
    }
    var uniforms: [Float] {
        [psiO, psiI, omO, omI, shear, finger, turn, turn,
         r0, a0, r1, a1, r2, a2, r3, a3, dentX, dentY, dent,
         -.pi / 2 + pos / 200 * 2 * .pi, reach, lamp, pullAt, pull, wash].map(Float.init)
    }
}

enum WovenSpeakerPlayerMotion {
    static let loop = 8.0, track = 200.0, beat = 60.0 / 118
    static let forward = (99.0 / 200) * 2 * Double.pi
    static let coast = 12.0 * Double.pi / 180
    static let snapFrom = 100 + coast / (2 * .pi) * 200 * (1 - exp(-0.5 / 0.18))
    static let snapped = snapFrom.rounded()
    static let pos2 = snapped + (5.6 - 3.75)
    static let psi2 = 0.22 * (forward + coast)
    static let reverseStart = Double.pi + 0.2
    static let reverseTravel = pos2 / 200 * 2 * Double.pi
    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double { let k = seg(x, a, b); return k * k * (3 - 2 * k) }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        let cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
        let cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
        var s = x
        for _ in 0..<6 {
            let e = ((ax * s + bx) * s + cx) * s - x, d = (3 * ax * s + 2 * bx) * s + cx
            if abs(e) < 1e-5 || abs(d) < 1e-6 { break }; s -= e / d
        }
        s = clamp(s); return ((ay * s + by) * s + cy) * s
    }
    static func out(_ x: Double) -> Double { bezier(x, 0.2, 0.8, 0.2, 1) }
    static func inOut(_ x: Double) -> Double { bezier(x, 0.65, 0, 0.35, 1) }
    static func spring(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }; let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func raw(_ t: Double, _ c: Double, _ k: Double, _ mass: Double) -> Double {
        let w = sqrt(k / mass); return spring(t, 2 * .pi / w, c / (2 * sqrt(k * mass)))
    }
    static func wrapPi(_ a: Double) -> Double {
        var x = a; while x > .pi { x -= 2 * .pi }; while x < -.pi { x += 2 * .pi }; return x
    }
    static func ring(_ now: Double, _ t0: Double, _ amp: Double) -> (Double, Double) {
        let u = (now - t0) / 0.55
        if u < 0 || u >= 1 { return (0, 0) }
        return (104 + 61 * u, amp * smooth(0, 0.1, u) * (1 - smooth(0.72, 1, u)))
    }
    static func pulse(_ now: Double, _ t0: Double) -> Double {
        let u = now - (t0 + 0.55); if u < 0 || u > 0.6 { return 0 }
        return (1 - exp(-u / 0.03)) * exp(-u / 0.12) * 1.6
    }
    static let launches: [(Double, Double)] = (0..<20).compactMap { k in
        let t = 0.52 + Double(k) * beat
        if t >= 6.6 || (t >= 1.4 && t < 3.15) || (t >= 5.6 && t < 6.55) { return nil }
        return (t, k % 4 == 0 ? 4 : 2.2)
    }
    static func position(_ t: Double) -> Double {
        if t < 0.4 { return 0 }; if t < 1.5 { return min(t - 0.4, 1) }
        if t < 2.9 { return 1 + 99 * bezier(seg(t, 1.5, 2.6), 0.45, 0, 0.25, 1) }
        if t < 3.4 { return 100 + coast / (2 * .pi) * 200 * (1 - exp(-(t - 2.9) / 0.18)) }
        if t < 5.6 { return snapFrom + (snapped - snapFrom) * spring(t - 3.4, 0.3, 1) + max(0, t - 3.75) }
        if t < 5.68 { return pos2 }
        return pos2 * (1 - bezier(seg(t, 5.68, 6.4), 0.4, 0, 0.2, 1))
    }
    static func turn(_ t: Double) -> (outer: Double, delta: Double, finger: Double) {
        if t < 1.4 { return (0, 0, 0) }
        if t < 2.9 {
            let e = bezier(seg(t, 1.5, 2.6), 0.45, 0, 0.25, 1), o = 0.22 * forward * e
            return (o, 0.4 * o, forward * e)
        }
        let since = t - 2.9, co = coast * (1 - exp(-since / 0.18)), o = 0.22 * (forward + co)
        if t < 5.6 {
            let d = 0.4 * 0.22 * forward * (1 - raw(since, 14, 60, 1.4)) * (1 - smooth(4, 4.3, t))
            return (o, d, forward)
        }
        if t < 5.68 { return (psi2, 0, reverseStart) }
        let e = bezier(seg(t, 5.68, 6.4), 0.4, 0, 0.2, 1), end = -0.4 * psi2
        if t < 6.45 { return (psi2 * (1 - e), end * e, reverseStart - reverseTravel * e) }
        let d = end * (1 - spring(t - 6.45, 0.55, 0.8)) * (1 - smooth(6.95, 7.25, t))
        return (0, d, reverseStart - reverseTravel)
    }
    static func look(_ time: Double) -> WovenSpeakerPlayerLook {
        let t = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        var k = WovenSpeakerPlayerLook(); let playing = t >= 0.4 && t < 6.6
        func rise(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
            playing ? inOut(seg(t, a, b)) : t >= 6.6 ? 1 - inOut(seg(t, c, d)) : 0
        }
        func lamp(_ a: Double, _ b: Double, _ end: Double) -> Double {
            playing ? out(seg(t, a, b)) : t >= 6.6 ? 1 - out(seg(t, 6.6, end)) : 0
        }
        k.glyph = rise(0.4, 0.66, 6.6, 6.82)
        let lp = lamp(0.52, 0.74, 6.9), bl = lamp(0.46, 0.76, 7)
        k.lamp = mix(0.12, 1, lp); k.reach = mix(18, 46, lp)
        k.bloom = mix(0.65, 1, bl); k.bloomR = mix(60, 84, bl)
        k.title = rise(0.56, 0.8, 6.72, 6.96); k.line = rise(0.6, 0.84, 6.68, 6.92); k.pill = rise(0.64, 0.88, 6.64, 6.88)
        k.refl = lamp(0.73, 0.93, 6.8)
        func press(_ t0: Double) -> Double {
            let u = t - (t0 + 0.08)
            return u < 0 ? 1 - 0.1 * out(seg(t, t0, t0 + 0.08)) : 1 - 0.1 * (1 - spring(u, 0.35, 0.85))
        }
        k.press = t >= 6.6 ? press(6.6) : t >= 0.4 ? press(0.4) : 1
        if t >= 7.1 { k.press = 1 }
        k.handle = playing ? out(seg(t, 0.9, 1.15)) : t >= 6.6 ? 1 - out(seg(t, 6.6, 7.12)) : 0
        k.pos = position(t)
        let tr = turn(t), dt = 1.0 / 120, ahead = turn(t + dt), behind = turn(t - dt)
        k.psiO = tr.outer; k.psiI = tr.outer - tr.delta; k.shear = max(-1, min(1, tr.delta / 0.25)); k.finger = tr.finger
        k.omO = abs(ahead.outer - behind.outer) / (2 * dt) / 60
        k.omI = abs(ahead.outer - ahead.delta - (behind.outer - behind.delta)) / (2 * dt) / 60
        let on1 = t >= 1.4 && t < 2.9, on2 = t >= 5.6 && t < 6.45
        var dent = 0.0, engagement = 0.0
        if t >= 1.4 && t < 5.6 {
            dent = on1 ? out(seg(t, 1.4, 1.48)) : 1 - inOut(seg(t, 2.9, 3.2))
            engagement = on1 ? spring(t - 1.4, 0.35, 0.85) : 1 - inOut(seg(t, 2.9, 3.3))
        } else if t >= 5.6 {
            dent = on2 ? out(seg(t, 5.6, 5.68)) : 1 - inOut(seg(t, 6.45, 6.75))
            engagement = on2 ? spring(t - 5.6, 0.35, 0.85) : 1 - inOut(seg(t, 6.45, 6.85))
        }
        k.dent = 5 * max(0, dent); k.turn = max(0, min(1.05, engagement))
        k.dentX = 135 * cos(tr.finger); k.dentY = 135 * sin(tr.finger)
        if t >= 2.9 && t < 5.6 { k.pull = 0.8 * (1 - smooth(2.9, 4.1, t)); k.pullAt = forward }
        else if t >= 6.45 { k.pull = 0.8 * (1 - smooth(6.45, 7.05, t)); k.pullAt = reverseStart - reverseTravel }
        if t >= 7.25 { k.finger = 0; k.pullAt = 0; k.dentX = 135; k.dentY = 0 }
        var n = 0
        for (at, amp) in launches.reversed() {
            if n >= 4 { break }
            if amp >= 4 - 1e-6 { k.pulse = max(k.pulse, pulse(t, at)) }
            let (r, a) = ring(t, at, amp); if a <= 0 { continue }; k.ring(n, r, a); n += 1
        }
        let playE = playing ? inOut(seg(t, 0.4, 0.85)) : t >= 6.6 ? 1 - inOut(seg(t, 6.6, 7.1)) : 0
        k.wash = playE * (0.05 + 0.03 * min(k.pulse, 1)); return k
    }
}
