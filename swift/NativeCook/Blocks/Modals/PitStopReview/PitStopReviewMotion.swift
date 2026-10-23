import Foundation

/// Pit Stop Review: the whole choreography as pure functions of one scene time `s` (seconds), value for value
/// from the React Native `motion.ts`. The autoplay loop samples s = loop time; a tap plays the same timeline.
enum PitStopReviewMotion {
    static let loop = 10.0

    /// Scene times (s). Frames before `press` and from `rest` on are the rest frame (the pin).
    enum T {
        static let press = 0.8
        static let tap = 0.9
        static let whipEnd = 1.3
        static let carGone = 1.22
        static let enter = 1.38
        static let stop = 1.92
        static let lightsOn = 2.05
        static let lightStep = 0.1
        static let panelOpen = 2.55
        static let panelDur = 0.28
        static let flyStart = 2.62
        static let flyStagger = 0.045
        static let flyDur = 0.46
        static let hold = 3.3
        static let close = 6.0
        static let backStagger = 0.04
        static let backDur = 0.34
        static let panelClose = 6.08
        static let lightsOut = 6.72
        static let rest = 7.8
    }

    /// Distances in the hero's design points (402 × 220), velocities in pt/s.
    enum M {
        static let whipCarTravel = 480.0
        static let entryDistance = 440.0
        static let stopLurch = 3.2
        static let launchLead = 34.0
        static let pitchBrake = 3.0
        static let pitchSquat = -1.5
        static let blurRest = 220.0
        static let blurWhip = 300.0
        static let whipDistance = 3000.0
        static let worldSpeed = 2545.0
        static let carBlurPerVelocity = 0.0055
        static let carBlurMax = 22.0
        static let carRestBlur = 2.0
        static let carStreaks = 0.75
        static let discEmber = 0.28
        static let manualRate = 1.5
        static let rush = 4.0
        static let closeRush = 4.0
        static let panelRise = 18.0
        static let panelScale = 0.96
        static let backdrop = 0.4
        static let ctaPressed = 0.97
    }

    // MARK: Easing

    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, v)) }
    static func smooth(_ v: Double) -> Double { let t = clamp(v); return t * t * (3 - 2 * t) }
    static func span(_ s: Double, _ a: Double, _ b: Double) -> Double { clamp((s - a) / max(1e-4, b - a)) }
    static func easeOutCubic(_ v: Double) -> Double { let t = 1 - clamp(v); return 1 - t * t * t }
    static func easeInOutCubic(_ v: Double) -> Double {
        let t = clamp(v)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func cubicBezier(_ value: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(value)
        func coordinate(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let i = 1 - t
            return 3 * i * i * t * a + 3 * i * t * t * b + t * t * t
        }
        var low = 0.0, high = 1.0, t = x
        for _ in 0..<18 {
            let c = coordinate(t, x1, x2)
            if abs(c - x) < 1e-5 { break }
            if c < x { low = t } else { high = t }
            t = (low + high) * 0.5
        }
        return coordinate(t, y1, y2)
    }
    /// Damped oscillation that starts at 1 (cos form).
    static func ring(_ tau: Double, _ f: Double, _ zeta: Double) -> Double {
        if tau <= 0 { return 1 }
        let w = 2 * Double.pi * f, wd = w * (1 - zeta * zeta).squareRoot()
        return exp(-zeta * w * tau) * cos(wd * tau)
    }
    /// Damped oscillation that starts at 0 (sin form), peak about 1.
    static func kick(_ tau: Double, _ f: Double, _ zeta: Double) -> Double {
        if tau <= 0 { return 0 }
        let w = 2 * Double.pi * f, wd = w * (1 - zeta * zeta).squareRoot()
        return exp(-zeta * w * tau) * sin(wd * tau)
    }
    static func wrap(_ s: Double) -> Double {
        let r = s.truncatingRemainder(dividingBy: loop)
        return (r + loop).truncatingRemainder(dividingBy: loop)
    }
    /// Frames from `rest` on are the rest frame; treat them as 0 so every function has one rest branch.
    private static func scene(_ s: Double) -> Double { s >= T.rest ? 0 : s }

    // MARK: Car

    /// Horizontal offset of the car from its rest place (design pt, + = towards the nose).
    static func carX(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.tap { return 0 }
        if s < T.carGone { let u = span(s, T.tap, T.carGone); return M.whipCarTravel * u * u * u }
        if s < T.enter { return M.whipCarTravel }
        if s < T.stop { let u = span(s, T.enter, T.stop); return -M.entryDistance * (1 - u) * (1 - u) }
        if s < T.lightsOut {
            return M.stopLurch * kick(s - T.stop, 1.8, 0.45) * (1 - span(s, T.stop + 0.6, T.stop + 0.9))
        }
        let tau = s - T.lightsOut
        return tau < 0.4 ? M.launchLead * easeOutCubic(tau / 0.4) : M.launchLead * (1 - smooth((tau - 0.4) / 0.66))
    }

    /// Speed of the car across the frame (pt/s), for its motion blur.
    static func carVelocity(_ raw: Double) -> Double {
        let s = scene(raw)
        if s >= T.tap && s < T.carGone {
            let d = T.carGone - T.tap, u = span(s, T.tap, T.carGone)
            return (3 * M.whipCarTravel * u * u) / d
        }
        if s >= T.enter && s < T.stop {
            let d = T.stop - T.enter, u = span(s, T.enter, T.stop)
            return (2 * M.entryDistance * (1 - u)) / d
        }
        if s >= T.lightsOut && s < T.lightsOut + 0.4 {
            let u = (s - T.lightsOut) / 0.4
            return (3 * M.launchLead * (1 - u) * (1 - u)) / 0.4
        }
        return 0
    }

    /// Body pitch in degrees, + = nose down. The renders cover −1.5 … +3.
    static func pitch(_ raw: Double) -> Double {
        let s = scene(raw)
        if s >= T.enter && s < T.stop { return M.pitchBrake * smooth(span(s, T.enter + 0.04, T.enter + 0.2)) }
        if s >= T.stop && s < T.hold {
            return clamp(M.pitchBrake * ring(s - T.stop, 2.0, 0.32), M.pitchSquat, M.pitchBrake) * (1 - span(s, T.hold - 0.35, T.hold))
        }
        if s >= T.lightsOut {
            let tau = s - T.lightsOut
            let squat = M.pitchSquat * smooth(tau / 0.12)
            let release = tau < 0.33 ? 1 : ring(tau - 0.33, 1.6, 0.45)
            return squat * release * (1 - span(s, T.rest - 0.2, T.rest))
        }
        return 0
    }

    /// Brake disc heat 0…1 (drives the glow layer).
    static func disc(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.enter + 0.12 { return 0 }
        if s < T.stop { return smooth(span(s, T.enter + 0.12, T.stop)) }
        if s < T.lightsOut {
            return M.discEmber + (1 - M.discEmber) * exp(-3 * (s - T.stop)) * (1 - span(s, T.stop + 1.1, T.hold))
        }
        return M.discEmber * (1 - smooth(span(s, T.lightsOut, T.rest - 0.2)))
    }

    // MARK: World (track / pit lane shader)

    /// 1 = racing speed (the pin's streaks), 0 = parked in the pit box.
    static func speed(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.tap + 0.05 { return 1 }
        if s < T.lightsOut + 0.13 { return 1 - smooth(span(s, T.tap + 0.05, T.whipEnd - 0.05)) }
        return smooth(span(s, T.lightsOut + 0.13, T.rest - 0.15))
    }
    /// Horizontal blur of the car (pt): its own speed across the frame, plus the pin's speed blur while racing.
    static func carBlur(_ s: Double) -> Double {
        min(M.carBlurMax, abs(carVelocity(s)) * M.carBlurPerVelocity + M.carRestBlur * speed(s))
    }
    /// Light streaks over the car body, 0…1.
    static func carStreaks(_ s: Double) -> Double { M.carStreaks * speed(s) }
    /// Whip pan: 0 → 1 → 0 between TAP and WHIP_END.
    static func whip(_ raw: Double) -> Double {
        let s = scene(raw)
        if s <= T.tap || s >= T.whipEnd { return 0 }
        let v = sin(Double.pi * span(s, T.tap, T.whipEnd))
        return v * v
    }
    /// Motion-blur length of the world (pt).
    static func worldBlur(_ s: Double) -> Double { M.blurRest * speed(s) + M.blurWhip * whip(s) }
    /// World offset (pt): the world sample x is screen x + this. The pit box sits at −whipDistance.
    static func worldX(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.tap { return 0 }
        if s < T.lightsOut + 0.13 { return -M.whipDistance * smooth(span(s, T.tap, T.whipEnd)) }
        let a = T.lightsOut + 0.13, b = T.rest - 0.15, d = b - a
        let u = span(s, a, b)
        return -M.whipDistance + M.worldSpeed * (d * (u * u * u - (u * u * u * u) / 2) + max(0, s - b))
    }
    /// Heat haze behind the rear wing, 0…1.
    static func haze(_ raw: Double) -> Double {
        let s = scene(raw)
        if s >= T.enter && s < T.lightsOut {
            return smooth(span(s, T.enter, T.enter + 0.1)) * (1 - 0.45 * span(s, T.stop - 0.1, T.stop)) * (1 - smooth(span(s, T.stop + 0.3, T.hold - 0.1)))
        }
        if s >= T.lightsOut {
            return smooth(span(s, T.lightsOut, T.lightsOut + 0.15)) * (1 - smooth(span(s, T.lightsOut + 0.5, T.rest - 0.1)))
        }
        return 0
    }
    /// Exhaust / rain-light trail behind the car, 0…1 (1 = the pin).
    static func trail(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.enter { return 1 }
        if s < T.lightsOut {
            let u = span(s, T.enter, T.stop)
            return (0.45 + 0.55 * (1 - u)) * (1 - smooth(span(s, T.stop - 0.08, T.stop + 0.3)))
        }
        return smooth(span(s, T.lightsOut + 0.1, T.rest - 0.2))
    }
    /// Speed lines across the frame, 0…1.
    static func speedLines(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.tap { return 0 }
        if s < T.whipEnd { return whip(s) }
        if s < T.stop { return smooth(span(s, T.enter - 0.02, T.enter + 0.08)) * (1 - smooth(span(s, T.enter + 0.12, T.stop - 0.12))) }
        if s >= T.lightsOut {
            let v = sin(Double.pi * span(s, T.lightsOut + 0.15, T.rest - 0.25))
            return 0.6 * v * v
        }
        return 0
    }

    // MARK: Start lights → stars

    /// Gantry housing under the car, 0…1.
    static func gantry(_ raw: Double) -> Double {
        let s = scene(raw)
        return smooth(span(s, T.stop + 0.03, T.stop + 0.16)) * (1 - smooth(span(s, T.lightsOut + 0.06, T.lightsOut + 0.24)))
    }
    /// Light i switched on (0…1), before it flies or after it returns.
    static func lightOn(_ raw: Double, _ i: Int) -> Double {
        let s = scene(raw)
        let on = T.lightsOn + Double(i) * T.lightStep
        return span(s, on, on + 0.05) * (1 - span(s, T.lightsOut, T.lightsOut + 0.03))
    }
    /// Flight of light i from the gantry to its star slot, 0…1 (eased).
    static func flyIn(_ raw: Double, _ i: Int) -> Double {
        let s = scene(raw)
        let a = T.flyStart + Double(i) * T.flyStagger
        return easeInOutCubic(span(s, a, a + T.flyDur))
    }
    /// Flight of star i back up to the gantry, 0…1 (eased). Stars leave right to left.
    static func flyBack(_ raw: Double, _ i: Int) -> Double {
        let s = scene(raw)
        let a = T.close + Double(4 - i) * T.backStagger
        return easeInOutCubic(span(s, a, a + T.backDur))
    }
    /// Where light i is: 0 in the gantry, 1 in the composer; between them it is flying.
    static func lightAway(_ raw: Double, _ i: Int) -> Double { flyIn(raw, i) * (1 - flyBack(raw, i)) }

    // MARK: Composer

    static func panel(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.panelOpen { return 0 }
        if s < T.panelClose { return cubicBezier((s - T.panelOpen) / T.panelDur, 0.32, 0.72, 0, 1) }
        return 1 - cubicBezier((s - T.panelClose) / T.panelDur, 0.23, 1, 0.32, 1)
    }
    static func ctaScale(_ raw: Double) -> Double {
        let s = scene(raw)
        if s < T.press || s > T.tap + 0.15 { return 1 }
        if s < T.tap { return 1 - (1 - M.ctaPressed) * cubicBezier((s - T.press) / (T.tap - T.press), 0.23, 1, 0.32, 1) }
        return M.ctaPressed + (1 - M.ctaPressed) * cubicBezier((s - T.tap) / 0.15, 0.23, 1, 0.32, 1)
    }
    /// Star i shown in the composer itself (landed and not yet lifted off).
    static func starLanded(_ raw: Double, _ i: Int) -> Double { flyIn(raw, i) >= 1 && flyBack(raw, i) <= 0 ? 1 : 0 }
    /// Landing squash of star i: 1.06 → 1 over 180 ms after it lands.
    static func starScale(_ raw: Double, _ i: Int) -> Double {
        let s = scene(raw)
        let land = T.flyStart + Double(i) * T.flyStagger + T.flyDur
        if s < land || s > land + 0.18 { return 1 }
        return 1 + 0.06 * (1 - cubicBezier((s - land) / 0.18, 0.23, 1, 0.32, 1))
    }

    // MARK: Smoke (seeded particles)

    static func hash(_ n: Double) -> Double {
        let x = sin(n * 127.1 + 311.7) * 43758.5453
        return x - floor(x)
    }

    struct Puff { var x: Double; var y: Double; var r: Double; var a: Double }
    enum SmokeKind { case front, rear }
    private struct SmokeSpec { var count: Int; var from: Double; var to: Double; var life: Double; var alpha: Double; var drag: Double; var grow: Double; var spread: Double }
    private static let smokeFront = SmokeSpec(count: 16, from: 1.66, to: 1.92, life: 1.3, alpha: 0.42, drag: 5, grow: 40, spread: 44)
    private static let smokeRear = SmokeSpec(count: 16, from: 6.72, to: 7.22, life: 1.0, alpha: 0.42, drag: 3, grow: 46, spread: 40)

    /// Puff k of a tyre (`tyre` = its contact point in design pt at rest, `seed` distinct per tyre). Front lock-up smoke
    /// keeps the car's speed and drags to a stop; rear wheelspin smoke is thrown back and is carried away by the world.
    static func puff(_ raw: Double, _ kind: SmokeKind, tyre: (Double, Double), seed: Double, k: Int, side: Double = 1) -> Puff? {
        let s = scene(raw)
        let P = kind == .front ? smokeFront : smokeRear
        if k >= P.count { return nil }
        let kd = Double(k)
        let born = P.from + (P.to - P.from) * ((kd + hash(seed + kd * 7.3)) / Double(P.count))
        let age = s - born
        if age <= 0 || age >= P.life { return nil }
        let jitterX = (hash(seed + kd * 3.1) - 0.5) * 8, jitterY = (hash(seed + kd * 5.7) - 0.5) * 10
        let x0 = tyre.0 + carX(born) + jitterX, y0 = tyre.1 + jitterY
        let spread = side * (0.25 + 0.75 * hash(seed + kd * 9.9)) * P.spread
        let ease = 1 - exp(-P.drag * age)
        let x: Double
        if kind == .front { x = x0 + (carVelocity(born) * ease) / P.drag }
        else { x = x0 - 70 * (1 - exp(-3 * age)) - (worldX(s) - worldX(born)) }
        let y = y0 + spread * (1 - exp(-2.4 * age))
        let r = 6 + P.grow * (1 - exp(-1.8 * age)) * (0.7 + 0.6 * hash(seed + kd * 1.7))
        let life = age / P.life
        let a = P.alpha * smooth(age / 0.06) * (1 - life) * (1 - life) * (kind == .rear ? 1 - span(s, T.rest - 0.35, T.rest) : 1)
        return Puff(x: x, y: y, r: r, a: a)
    }

    static let puffSlots = 32
    /// A tyre of the smoke shader: contact point (x, y), seed, kind (0 front, 1 rear) and side (+1 below the car, −1 above).
    struct Tyre { var x: Double; var y: Double; var seed: Double; var kind: Int; var side: Double }

    /// Every live puff of the four tyres packed for the smoke shader: (x, y, r, a) × 32 (zeros after `count`).
    static func packPuffs(_ raw: Double, _ tyres: [Tyre]) -> (data: [Float], count: Int) {
        var data: [Float] = []
        data.reserveCapacity(puffSlots * 4)
        for ty in tyres {
            let kind: SmokeKind = ty.kind == 0 ? .front : .rear
            var k = 0
            while k < 16 && data.count < puffSlots * 4 {
                defer { k += 1 }
                guard let q = puff(raw, kind, tyre: (ty.x, ty.y), seed: ty.seed, k: k, side: ty.side), q.a > 0.003 else { continue }
                data.append(contentsOf: [Float(q.x), Float(q.y), Float(q.r), Float(q.a)])
            }
        }
        let count = data.count / 4
        while data.count < puffSlots * 4 { data.append(0) }
        return (data, count)
    }

    // MARK: Reduced motion & loop helpers

    /// Scene frames the clock must run through (anything else is a still).
    static func moving(_ raw: Double) -> Bool {
        let s = wrap(raw)
        return (s >= T.press && s < T.hold) || (s >= T.close && s < T.rest)
    }

    /// 20-vertex circle → five-point star, sampled at `progress` (0 circle, 1 star).
    static func morphPoint(index: Int, progress: Double, center: (Double, Double), radius: Double) -> (Double, Double) {
        let i = ((index % 20) + 20) % 20
        let angle = -Double.pi / 2 + Double(i) * 2 * Double.pi / 20
        let p = clamp(progress), segment = i / 2, fraction = i % 2 == 0 ? 0.0 : 0.5
        func star(_ v: Int) -> (Double, Double) {
            let n = ((v % 10) + 10) % 10
            let a = -Double.pi / 2 + Double(n) * Double.pi / 5
            let r = n % 2 == 0 ? radius : radius * 0.46
            return (cos(a) * r, sin(a) * r)
        }
        let (ax, ay) = star(segment), (bx, by) = star(segment + 1)
        let cx = cos(angle) * radius, cy = sin(angle) * radius
        return (center.0 + cx + (ax + (bx - ax) * fraction - cx) * p, center.1 + cy + (ay + (by - ay) * fraction - cy) * p)
    }
}
