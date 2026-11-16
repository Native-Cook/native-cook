import Foundation

/// Value-for-value osd-motion.ts. All autoplay poses are analytic, not accumulated.
enum ObjectSigninDissolveMotion {
    static let loop = 8.0
    static let yaw0 = -18.0
    static let span = 410.0
    struct Look: Equatable {
        var yaw = -18.0, c = 0.0, roll = 0.0, pitchP = 0.0, scale = 1.0
        var wipeP = 0.0, rate = 0.0, seam = 0.0, key = 0.0
    }
    struct Live {
        var yaw: Double, yawV: Double, yawTarget: Double
        var c: Double, cV = 0.0, roll: Double, rollV = 0.0, pitchP: Double, pitchV = 0.0
        var scale: Double, scaleV = 0.0, q: Double, qTarget: Double
        init(_ k: Look) {
            yaw = k.yaw; yawV = k.rate; yawTarget = heroNear(k.yaw, k.rate)
            c = k.c; roll = k.roll; pitchP = k.pitchP; scale = k.scale
            q = k.wipeP; qTarget = k.wipeP > 0.5 ? 1 : 0
        }
        func look(_ t: Double) -> Look {
            Look(yaw: yaw, c: c, roll: roll, pitchP: pitchP, scale: scale,
                 wipeP: q, rate: yawV, seam: seamOf(c), key: keyDrift(t))
        }
    }
    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(x, a), b) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t)
    }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func ease(_ t: Double) -> Double {
        let x = clamp(t); return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func spring(_ t: Double, _ x0: Double, _ v0: Double, _ to: Double, _ k: Double, _ c: Double) -> Double {
        if t <= 0 { return x0 }
        let w = sqrt(k), z = c / (2 * w), e = x0 - to
        if z < 1 {
            let wd = w * sqrt(1 - z * z)
            return to + exp(-z * w * t) * (e * cos(wd * t) + ((v0 + z * w * e) / wd) * sin(wd * t))
        }
        return to + exp(-w * t) * (e + (v0 + w * e) * t)
    }
    static func velocity(_ t: Double, _ x: Double, _ v: Double, _ to: Double, _ k: Double, _ c: Double) -> Double {
        let h = 1.0 / 240
        return (spring(t + h, x, v, to, k, c) - spring(t - h, x, v, to, k, c)) / (2 * h)
    }
    static func step(_ t: Double, _ k: Double, _ c: Double) -> Double { spring(t, 0, 0, 1, k, c) }
    // JavaScript Math.round ties toward positive infinity, including negative turns.
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }
    static func heroNear(_ yaw: Double, _ v: Double) -> Double {
        let proj = yaw + clamp(v / 6.02, -1080, 1080)
        return yaw0 + jsRound((proj - yaw0) / 180) * 180
    }
    static func spinShell(_ v: Double) -> Double { smooth(540, 1260, abs(v)) }
    static func rollOf(_ v: Double) -> Double { clamp(v * 0.012, -12, 12) * .pi / 180 }
    static func pitchOf(_ v: Double) -> Double { clamp(v * 0.03, -8, 8) * .pi / 180 }
    static func rubber(_ c: Double) -> Double {
        if c >= 0 && c <= 1 { return c }
        let over = (c < 0 ? -c : c - 1) * span
        let give = 0.35 * over / (1 + 0.35 * over / 20)
        return c < 0 ? -give / span : 1 + give / span
    }
    static let target1 = heroNear(182, 520)
    static let target2 = heroNear(target1, 4800)
    static let flick2At = 4.0 + 0.1
    static func yawAt(_ t: Double) -> Double {
        if t < 0.7 { return yaw0 }
        if t < 2.3 { return yaw0 + 200 * ease((min(t, 1.8) - 0.7) / (1.8 - 0.7)) }
        if t < flick2At { return spring(t - 2.3, 182, 520, target1, 90, 16) }
        return spring(t - flick2At, target1, 4800, target2, 90, 16)
    }
    static func cutAt(_ t: Double) -> Double {
        if t < 0.6 { return 0 }
        if t < 2.3 {
            let finger = mix(0.25, 0.55, ease((min(t, 1.8) - 0.7) / (1.8 - 0.7)))
            return finger * clamp(step(t - 0.6, 240, 20), 0, 1.2)
        }
        if t < flick2At - 0.1 { return max(0.55 * (1 - step(t - 2.3, 160, 22)), 0) }
        if t < 4 { return 0 }
        var acc = 0.0, wsum = 0.0
        for j in 0..<36 {
            let tau = t - Double(j) * 0.0125, w = exp(-(Double(j) * 0.0125) / 0.11)
            wsum += w
            if tau < 4 { continue }
            let rate = abs(velocity(tau - flick2At, target1, 4800, target2, 90, 16))
            acc += w * (tau < flick2At ? 0 : spinShell(rate))
        }
        return acc / wsum
    }
    static func seamOf(_ c: Double) -> Double { smooth(0.012, 0.07, c) * (1 - smooth(0.93, 0.99, c)) }
    static func keyDrift(_ t: Double) -> Double { 3 * .pi / 180 * sin(2 * .pi * t / loop) }
    static func lookAt(_ tt: Double) -> Look {
        let t = ((tt.truncatingRemainder(dividingBy: loop)) + loop).truncatingRemainder(dividingBy: loop)
        let h = 1.0 / 120, yaw = yawAt(t), c = cutAt(t)
        let rate = (yawAt(t + h) - yawAt(t - h)) / (2 * h)
        let planeV = (cutAt(t + h) - cutAt(t - h)) / (2 * h) * span
        let wipe = t < 5.6 ? clamp((t - 3) / (3.9 - 3)) : 1 - clamp((t - 5.6) / (6.5 - 5.6))
        let pressed = t >= 0.6 && t < 2.3 ? step(t - 0.6, 300, 18) : t >= 2.3 ? max(1 - step(t - 2.3, 300, 18), 0) : 0
        let pressed2 = t >= 4 && t < flick2At ? step(t - 4, 300, 18) : 0
        return Look(yaw: yaw, c: c, roll: rollOf(rate), pitchP: pitchOf(planeV),
                    scale: 1 + (1.02 - 1) * max(pressed, pressed2), wipeP: wipe,
                    rate: rate, seam: seamOf(c), key: keyDrift(t))
    }
    static func zipSince(_ t: Double) -> Double {
        let a = t - 2.62, b = t - 4.78
        if b >= 0 && b < 1 { return b }
        if a >= 0 && a < 1 { return a }
        return -1
    }
    static func spring1(_ x: inout Double, _ v: inout Double, _ to: Double, _ k: Double, _ c: Double, _ dt: Double) {
        let a = -k * (x - to) - c * v
        v += a * dt; x += v * dt
    }
    static func stepLive(_ s: inout Live, _ dt: Double, _ held: Bool, _ finger: Double, _ reduce: Bool, _ breathe: Double) {
        let n = max(1, Int(ceil(dt / (1.0 / 240)))), h = dt / Double(n)
        for _ in 0..<n {
            if !held { spring1(&s.yaw, &s.yawV, s.yawTarget, reduce ? 900 : 90, reduce ? 60 : 16, h) }
            let spin = reduce ? 0 : spinShell(s.yawV)
            let target = max(held ? rubber(finger) + breathe / span : 0, spin)
            let growing = target > s.c
            spring1(&s.c, &s.cV, target, growing ? 240 : 160, growing ? 20 : 22, h)
            spring1(&s.roll, &s.rollV, reduce ? 0 : rollOf(s.yawV), 160, 12, h)
            spring1(&s.pitchP, &s.pitchV, reduce ? 0 : pitchOf(s.cV * span), 160, 12, h)
            spring1(&s.scale, &s.scaleV, held && !reduce ? 1.02 : 1, 300, 18, h)
        }
        let diff = s.qTarget - s.q
        let dq = diff == 0 ? 0 : (diff > 0 ? 1.0 : -1.0) * dt / 0.88
        s.q += dq
        if (dq > 0 && s.q > s.qTarget) || (dq < 0 && s.q < s.qTarget) { s.q = s.qTarget }
    }
}
