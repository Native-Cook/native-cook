import Foundation

enum ThermalGlassTilesMotion {
    static let loop = 8.0
    static let peakStart = 2.0225737763349363
    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, v)) }
    static func mix(_ a: Double, _ b: Double, _ v: Double) -> Double { a + (b - a) * v }
    static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
        let x = clamp((v - a) / (b - a)); return x * x * (3 - 2 * x)
    }
    static func wrap(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop) }
    static func progress(_ time: Double) -> Double {
        let t = wrap(time)
        if t < 0.4 || t >= 6.8 { return 1 }
        if t < 0.8 { return mix(1, 0.05, smooth(0.4, 0.8, t)) }
        if t < 2.3 { return mix(0.05, 0.62, smooth(0.8, 2.3, t)) }
        if t < 3.1 { return 0.62 }
        if t < 4 { return mix(0.62, 0.98, smooth(3.1, 4, t)) }
        if t < 4.6 { return mix(0.98, 0.75, smooth(4, 4.6, t)) }
        if t < 5 { return mix(0.75, 0.8, 1 - pow(1 - clamp((t - 4.6) / 0.4), 3)) }
        if t < 5.6 { return 0.8 }
        return mix(0.8, 1, smooth(5.6, 6.8, t))
    }
    static func stride(_ p: Double, _ t: Double, reduced: Bool = false) -> Double {
        if reduced { return 0.72 }
        let slow = 0.25 * sin(2 * .pi * t / loop) * smooth(0, 0.2, t) * (1 - smooth(6.6, 6.8, t))
        return 0.72 + 2 * .pi * ((p - 1) * 300 / 12 + slow)
    }
    static func sceneTime(_ time: Double) -> Double {
        let t = wrap(time)
        if t < 0.4 || t >= 6.8 { return 0 }
        if t < 5.6 { return t - 0.4 }
        return mix(5.2, 8, smooth(5.6, 6.8, t))
    }
    static func peak(_ t: Double) -> Double {
        smooth(peakStart, peakStart + 0.08, t) * (1 - smooth(peakStart + 0.62, peakStart + 0.7, t))
    }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * .pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func bpmPulse(_ age: Double) -> Double {
        if age <= 0 || age >= 0.7 { return 1 }
        let w = sqrt(320.0), zeta = 14 / (2 * sqrt(320.0))
        return 1 + 0.06 * clamp(springAt(age, 2 * .pi / w, zeta)) * (1 - smooth(0.24, 0.7, age))
    }
    static func rubber(_ v: Double) -> Double {
        let edge = clamp(v), over = v - edge
        return edge + over * 0.25 / (1 + abs(over) * 300 / 10)
    }
    static func coast(_ origin: Double, _ velocity: Double, _ age: Double) -> (position: Double, velocity: Double) {
        let lambda = -log(0.992) * 1000, attenuation = exp(-lambda * max(0, age))
        return (origin + velocity * (1 - attenuation) / lambda, velocity * attenuation)
    }

    struct Sample {
        var distance: Double, elapsed: Double, bpm: Double, pace: Double, contact: Double, oscillation: Double
        var effort: Double { ThermalGlassTilesMotion.clamp((bpm - 118) / 58) }
        var streak: Double { ThermalGlassTilesMotion.clamp((376 - pace) / 96) }
    }
    static let run: [Sample] = (0...296).map { i in
        let p = Double(i) / 296, hill = exp(-pow((p - 0.62) / 0.12, 2))
        return Sample(distance: p * 29.6, elapsed: p * 10061, bpm: 118 + 23 * p + 40 * hill,
                      pace: i == 296 ? 376 : 376 - 96 * exp(-pow((p - 0.69) / 0.17, 2)),
                      contact: 270 + 24 * exp(-pow((p - 0.39) / 0.11, 2)) - 19 * hill,
                      oscillation: 10 + 1.7 * exp(-pow((p - 0.76) / 0.09, 2)) - 0.5 * hill)
    }
    static func sample(_ p: Double) -> Sample {
        let at = clamp(p) * 296, i = Int(floor(at)), f = at - Double(i), a = run[i], b = run[min(296, i + 1)]
        return Sample(distance: mix(a.distance, b.distance, f), elapsed: mix(a.elapsed, b.elapsed, f),
                      bpm: mix(a.bpm, b.bpm, f), pace: mix(a.pace, b.pace, f),
                      contact: mix(a.contact, b.contact, f), oscillation: mix(a.oscillation, b.oscillation, f))
    }
    static let contactExtreme = Double(run.indices.max(by: { run[$0].contact < run[$1].contact })!) / 296
    static let verticalExtreme = Double(run.indices.max(by: { run[$0].oscillation < run[$1].oscillation })!) / 296
    static func timeText(_ seconds: Double) -> String {
        let s = Int(max(0, floor(seconds + 0.5))); return "\(s / 60):\(String(format: "%02d", s % 60))"
    }

    /// Reanimated 4 physics springs, including its critical fallback for zeta >= 1,
    /// 64 ms maximum step and relative energy termination (6e-9).
    struct Spring {
        var value: Double, velocity: Double = 0, target: Double
        let damping: Double, stiffness: Double, mass: Double
        private var initialEnergy: Double
        init(_ value: Double, to target: Double, damping: Double, stiffness: Double, mass: Double = 1,
             velocity: Double = 0, inheritedVelocity: Double = 0) {
            self.value = value; self.target = target; self.damping = damping; self.stiffness = stiffness; self.mass = mass
            let combined = velocity + inheritedVelocity
            self.velocity = (target > value && combined < 0) || (target < value && combined > 0) ? 0 : combined
            initialEnergy = 0.5 * mass * velocity * velocity + 0.5 * stiffness * pow(value - target, 2)
        }
        mutating func step(_ dt: Double) -> Bool {
            let t = min(max(dt, 0), 0.064), x = value - target, v = velocity
            let w = sqrt(stiffness / mass), z = damping / (2 * sqrt(stiffness * mass))
            if z < 1 {
                let wd = w * sqrt(1 - z * z), si = sin(wd * t), co = cos(wd * t), e = exp(-z * w * t)
                let f = e * (si * (v + z * w * x) / wd + x * co)
                value = target + f
                velocity = -z * w * f + e * (co * (v + z * w * x) - wd * x * si)
            } else {
                let e = exp(-w * t), f = x + (v + w * x) * t
                value = target + e * f; velocity = e * (-w * f + v + w * x)
            }
            let energy = 0.5 * mass * velocity * velocity + 0.5 * stiffness * pow(target - value, 2)
            if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { value = target; velocity = 0; return true }
            return false
        }
    }
}
