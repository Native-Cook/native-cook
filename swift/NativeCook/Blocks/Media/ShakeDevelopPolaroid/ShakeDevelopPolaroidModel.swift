import SwiftUI

/*
 The motion of Shake Develop Polaroid, as functions of time.

 Development d (0 = just ejected, 1 = developed) drives the photo's look:
   colour     a 4 × 5 matrix: saturation 0.1 → 1, contrast 0.25 → 1 about mid-grey, a milky lift of
              0.18 and a cool cast (−0.02, +0.01, +0.03) that both fade out; all with e = smoothstep(d).
   haze       a milky #D7DDDA veil over the photo that lifts from the bottom up: at height v (0 at the
              bottom, 1 at the top) its opacity is 0.9 · smoothstep(f − 0.25, f + 0.25, v), f = 1.4 d − 0.2.

 The shake, as the print feels it in the hand: two bursts, each a sine of frequency f and amplitude A
 (breathing ±20 % at 0.9 Hz), that fades in over 0.15 s and dies out as e^(−7 s) after the burst:
   x = A·env·sin(2π f τ),  rotation = 6°·env·sin(2π f τ − 0.5),  y = 3·env·sin(4π f τ).

 autoPlay timeline (loop = 8 s):
   0.00–0.70  rest: an undeveloped print, "Shake to develop", an empty bar.
   0.70–2.30  burst 1 (A 13 pt, 3.2 Hz); d 0 → 0.42 (ease in-out). 2.30–2.80 the print settles; d creeps
              to 0.46.
   2.80–4.60  burst 2 (A 17 pt, 3.5 Hz); d 0.46 → 1 by 4.70 (ease-out).
   4.70–5.00  the label turns to "Developed" with a check; 4.75–5.45 a glint sweeps the photo.
   5.90–6.55  the print drops into the roll thumbnail at the bottom (ease-in: scale → 0.11, fades in its
              last 30 %); the bar fades 5.90–6.20; 6.50–6.95 the thumbnail bumps (+16 %).
   6.70–7.50  a new undeveloped print slides down from 110 pt above (ease-out-back, fade 0.15 s,
              scale 0.96 → 1); the label turns back 6.80–7.10; the bar returns 7.00–7.30.
   7.50–8.00  rest, identical to t = 0.
 */

struct ShakeDevelopPolaroidScene {
    var x = 0.0, y = 0.0, rotation = 0.0   // degrees
    var scale = 1.0, alpha = 1.0
    /// Moves the print from its place to the roll thumbnail (0 … 1).
    var toRoll = 0.0
    var develop = 0.0
    /// Glint progress 0 … 1, or −1 when none.
    var glint = -1.0
    /// 0: "Shake to develop", 1: "Developed".
    var label = 0.0
    var bar = 1.0
    var bump = 0.0
}

enum ShakeDevelopPolaroidMotion {
    static let loop = 8.0

    struct Burst { var start: Double, end: Double, amp: Double, freq: Double }
    static let bursts = [Burst(start: 0.70, end: 2.30, amp: 13, freq: 3.2),
                         Burst(start: 2.80, end: 4.60, amp: 17, freq: 3.5)]

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func inOut(_ x: Double) -> Double {
        let t = clamp(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func out(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func easeIn(_ x: Double) -> Double { let t = clamp(x); return t * t * t }
    static func outBack(_ x: Double) -> Double {
        let t = clamp(x), c1 = 1.70158, c3 = c1 + 1
        return 1 + c3 * pow(t - 1, 3) + c1 * pow(t - 1, 2)
    }

    /// The shake's offset at loop time t: x, y in points and rotation in degrees.
    static func shake(_ t: Double) -> (x: Double, y: Double, rotation: Double, env: Double) {
        var x = 0.0, y = 0.0, r = 0.0, envMax = 0.0
        for b in bursts where t >= b.start {
            let tau = t - b.start
            let dur = b.end - b.start
            let env = tau < dur ? smoothstep(0, 0.15, tau) : exp(-7 * (tau - dur))
            if env < 0.002 { continue }
            let a = b.amp * (0.8 + 0.2 * sin(2 * .pi * 0.9 * tau))
            let ph = 2 * .pi * b.freq * tau
            x += a * env * sin(ph)
            r += 6 * env * sin(ph - 0.5)
            y += 3 * env * sin(2 * ph)
            envMax = max(envMax, env)
        }
        return (x, y, r, envMax)
    }

    static func develop(_ t: Double) -> Double {
        if t < 0.7 { return 0 }
        if t < 2.3 { return 0.42 * inOut((t - 0.7) / 1.6) }
        if t < 2.8 { return 0.42 + 0.04 * smoothstep(2.3, 2.8, t) }
        if t < 6.7 { return 0.46 + 0.54 * out((t - 2.8) / 1.9) }
        return 0
    }

    static func scene(_ t: Double) -> ShakeDevelopPolaroidScene {
        var s = ShakeDevelopPolaroidScene()
        let sh = shake(t)
        s.x = sh.x; s.y = sh.y; s.rotation = sh.rotation
        s.develop = develop(t)
        if t >= 4.75 && t < 5.45 { s.glint = (t - 4.75) / 0.7 }
        s.label = smoothstep(4.7, 5.0, t) * (1 - smoothstep(6.8, 7.1, t))
        s.bar = 1 - smoothstep(5.9, 6.2, t) + smoothstep(7.0, 7.3, t)
        if t >= 5.9 && t < 6.7 {
            let q = easeIn((t - 5.9) / 0.65)
            s.toRoll = q
            s.scale = 1 - 0.89 * q
            s.alpha = 1 - smoothstep(0.7, 1, q)
        }
        if t >= 6.5 && t < 6.95 { s.bump = 0.16 * sin(.pi * (t - 6.5) / 0.45) }
        if t >= 6.7 {
            let u = (t - 6.7) / 0.8
            s.toRoll = 0
            s.y = -110 * (1 - outBack(u))
            s.alpha = smoothstep(6.7, 6.85, t)
            s.scale = 0.96 + 0.04 * out(u)
        }
        return s
    }

    /// The photo's colour matrix at development d (offsets in 0 … 1).
    static func matrix(_ d: Double) -> [Double] {
        let e = smoothstep(0, 1, d)
        let sat = 0.1 + 0.9 * e
        let con = 0.25 + 0.75 * e
        let lift = 0.18 * (1 - e)
        let cast = [-0.02, 0.01, 0.03].map { $0 * (1 - e) }
        let lw = [0.2126, 0.7152, 0.0722]
        var m = [Double](repeating: 0, count: 20)
        for row in 0..<3 {
            for col in 0..<3 {
                let s = (1 - sat) * lw[col] + (row == col ? sat : 0)
                m[row * 5 + col] = con * s
            }
            m[row * 5 + 4] = 0.5 * (1 - con) + lift + cast[row]
        }
        m[18] = 1
        return m
    }

    /// The haze's opacity at height v (0 at the bottom of the photo, 1 at the top).
    static func haze(_ d: Double, _ v: Double) -> Double {
        let f = 1.4 * d - 0.2
        return 0.9 * smoothstep(f - 0.25, f + 0.25, v)
    }
}
