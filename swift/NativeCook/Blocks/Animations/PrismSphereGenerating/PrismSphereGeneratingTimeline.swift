import Foundation
import simd

/// The reference uses row-major arrays; SIMD matrices below store the same rotation as columns.
enum PrismSphereGeneratingTimeline {
    static let identity = matrix_identity_double3x3
    static let keys: [Double] = [
        0.29652, -0.71587, 0.24191, 0.58403,
        0.21481, -0.19785, 0.51165, 0.80804,
        0.28132, -0.02138, 0.83629, 0.47013,
        0.49844, -0.04916, 0.86135, -0.08496,
        0.61271, 0.25379, 0.69147, -0.28642,
        0.66980, 0.14530, 0.61236, -0.39404,
        0.69015, -0.15393, 0.37917, -0.59685,
        0.86123, -0.20013, 0.43839, -0.16136,
        0.73369, -0.22904, 0.62698, -0.12700,
        0.74419, -0.21550, 0.63197, 0.01908,
    ]
    static let fans = [1.0, 1, 1, 1, 1, 1.35, 1.975, 1, 1, 1]

    static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
    static func modulo(_ x: Double, _ period: Double) -> Double {
        x - floor(x / period) * period
    }
    static func ease(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func smooth(_ low: Double, _ high: Double, _ value: Double) -> Double {
        let t = clamp((value - low) / (high - low))
        return t * t * (3 - 2 * t)
    }
    static func spline(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ u: Double) -> Double {
        b + 0.5 * u * (c - a + u * (2 * a - 5 * b + 4 * c - d + u * (3 * (b - c) + d - a)))
    }
    static func axis(_ x: Double, _ y: Double, _ z: Double, _ angle: Double) -> simd_double3x3 {
        let vector = SIMD3(x, y, z)
        guard simd_length(vector) >= 1e-9, angle != 0 else { return identity }
        return simd_double3x3(simd_quatd(angle: angle, axis: simd_normalize(vector)))
    }
    static func pose(_ phase: Double) -> (rotation: simd_double3x3, fan: Double) {
        let at = modulo(phase, 1) * 10
        let index = min(9, Int(floor(at)))
        let u = at - Double(index)
        let indices = [(index + 9) % 10, index, (index + 1) % 10, (index + 2) % 10]
        let q = (0..<4).map { component in
            spline(keys[indices[0] * 4 + component], keys[indices[1] * 4 + component],
                   keys[indices[2] * 4 + component], keys[indices[3] * 4 + component], u)
        }
        let quaternion = simd_normalize(simd_quatd(ix: q[0], iy: q[1], iz: q[2], r: q[3]))
        let fan = Double.pi * min(1.975, max(1, spline(fans[indices[0]], fans[indices[1]], fans[indices[2]], fans[indices[3]], u)))
        return (simd_double3x3(quaternion) * axis(0, 1, 0, (fan - .pi) / 2), fan)
    }
    static func trackball(_ offset: simd_double3x3, dx: Double, dy: Double, radius: Double) -> simd_double3x3 {
        let matrix = axis(-dy, dx, 0, hypot(dx, dy) / radius) * offset
        let rows = matrix.transpose
        let a = simd_normalize(rows.columns.0)
        let b = simd_normalize(rows.columns.1 - simd_dot(a, rows.columns.1) * a)
        return simd_double3x3(columns: (a, b, simd_cross(a, b))).transpose
    }
    static func returning(_ offset: simd_double3x3, dt: Double) -> simd_double3x3 {
        var quaternion = simd_normalize(simd_quatd(offset))
        if quaternion.real < 0 { quaternion = simd_quatd(vector: -quaternion.vector) }
        let angle = 2 * acos(max(-1, min(1, quaternion.real)))
        let next = angle * exp(-dt / 0.35)
        guard next >= 0.001 else { return identity }
        return axis(quaternion.imag.x, quaternion.imag.y, quaternion.imag.z, next)
    }
    static func caption(_ clock: Double, reduced: Bool) -> (opacity: Double, progress: Double) {
        let t = modulo(clock, 5)
        let away = clamp((t - 2.8) / 1.4)
        let opacity = t < 0.25 || t >= 4.2 ? 0 : ease((t - 0.25) / 1.2) * (1 - away)
        return (opacity, reduced ? 0 : away)
    }
    static func surge(_ s: Double, poseFan: Double, gain: Double = 1, releaseCharge: Double = 1) -> PrismSphereGeneratingValues {
        var value = PrismSphereGeneratingValues(fan: poseFan)
        value.energy = 0
        let elapsed = s - 0.55
        let b = abs(elapsed) < 1e-9 ? 0 : elapsed
        guard s >= 0 else { return value }
        if b < 0 {
            value.charge = ease(s / 0.55)
            value.scale = 1 - 0.035 * value.charge
            value.spin = -0.3 * value.charge
            value.fan = poseFan + (.pi - poseFan) * 0.6 * value.charge
            value.energy = 0.3 * value.charge
        } else {
            value.charge = releaseCharge * exp(-b / 0.08)
            if value.charge < 0.002 { value.charge = 0 }
            let kick = exp(-7 * b) * (-0.035 * releaseCharge * cos(11 * b) + 0.09 * gain * sin(11 * b))
            value.scale = exp(-7 * b) * hypot(0.035 * releaseCharge, 0.09 * gain) < 0.0005 ? 1 : 1 + kick
            value.flash = gain * exp(-b / 0.28)
            if value.flash < 0.004 { value.flash = 0 }
            value.ring = b < 0.9 - 1e-9 ? max(0.0001, ease(b / 0.9)) : 0
            value.energy = 1.1 * value.flash + 0.3 * gain * exp(-b / 0.9)
            value.spin = b < 2.6 - 1e-9 ? -0.3 * releaseCharge + (2 * .pi + 0.3 * releaseCharge) * (1 - pow(1 - clamp(b / 2.6), 4)) : 0
            let opening = b < 0.35 ? ease(b / 0.35) : 1 - smooth(1.1, 2.2, b)
            value.fan = poseFan + (max(poseFan, 1.82 * .pi) - poseFan) * opening
            value.sweep = clamp((b - 0.35) / 1)
            value.sweepPower = b > 0.35 && b < 1.35 ? 0.5 * pow(sin(.pi * value.sweep), 2) : 0
        }
        if s >= 0.2 && s < 0.7 {
            let power = clamp((s - 0.2) / 0.1) * clamp((0.7 - s) / 0.2)
            if power > value.sweepPower {
                value.sweep = clamp((s - 0.2) / 0.5)
                value.sweepPower = power
            }
        }
        value.fan = min(1.975 * .pi, value.fan)
        return value
    }
    static func values(_ clock: Double, poseFan: Double, surgeTime: Double = -1, gain: Double = 1,
                       reduced: Bool = false, heldCharge: Double = -1, heldTime: Double = 0,
                       releaseCharge: Double = 1) -> PrismSphereGeneratingValues {
        let t = modulo(clock, 5)
        let bump = t >= 2.2 && t <= 3.6 ? pow(sin(.pi * (t - 2.2) / 1.4), 2) : 0
        let sweep = clamp((t - 1.9) / 1.6)
        let sweepPower = t > 1.9 && t < 3.5 ? 0.62 * pow(sin(.pi * sweep), 2) : 0
        var value = surge(surgeTime, poseFan: poseFan, gain: gain, releaseCharge: releaseCharge)
        if heldCharge >= 0 {
            value.charge = heldCharge
            value.scale = 1 - 0.035 * heldCharge
            value.spin = -0.3 * heldCharge
            value.fan = poseFan + (.pi - poseFan) * 0.6 * heldCharge
            value.energy = 0.3 * heldCharge
            value.sweep = clamp((heldTime - 0.2) / 0.5)
            value.sweepPower = clamp((heldTime - 0.2) / 0.1) * clamp((0.7 - heldTime) / 0.2)
        }
        if sweepPower >= value.sweepPower { value.sweep = sweep }
        value.sweepPower = max(sweepPower, value.sweepPower)
        value.energy = min(1.5, 0.4 + 0.2 * bump + (reduced ? 1.1 * value.flash : value.energy))
        if reduced {
            value.charge = 0
            value.scale = 1
            value.ring = 0
            value.sweep = 0
            value.sweepPower = 0
            value.spin = 0
            value.fan = poseFan
        }
        return value
    }
    static func canvasOpacity(_ clock: Double) -> Double {
        let t = clamp(clock / 0.6)
        return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    static func entrance(_ t: Double) -> Double {
        let target = clamp(t)
        var low = 0.0
        var high = 1.0
        for _ in 0..<16 {
            let u = (low + high) / 2
            let x = 3 * (1 - u) * (1 - u) * u * 0.23 + 3 * (1 - u) * u * u * 0.32 + u * u * u
            if x < target { low = u } else { high = u }
        }
        return 1 - pow(1 - (low + high) / 2, 3)
    }
}

struct PrismSphereGeneratingValues {
    var fan: Double
    var charge = 0.0
    var scale = 1.0
    var flash = 0.0
    var ring = 0.0
    var energy = 0.4
    var spin = 0.0
    var sweep = 0.0
    var sweepPower = 0.0
}
