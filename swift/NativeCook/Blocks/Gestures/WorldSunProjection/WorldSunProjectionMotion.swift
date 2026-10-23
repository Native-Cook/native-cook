import Foundation
import CoreGraphics

struct WorldSunProjectionVector {
    var x: Double
    var y: Double
    var z: Double
    func dot(_ other: Self) -> Double { x * other.x + y * other.y + z * other.z }
    static func + (a: Self, b: Self) -> Self { .init(x: a.x + b.x, y: a.y + b.y, z: a.z + b.z) }
    static func * (a: Self, b: Double) -> Self { .init(x: a.x * b, y: a.y * b, z: a.z * b) }
    var unit: Self { self * (1 / max(1e-12, sqrt(dot(self)))) }
}

struct WorldSunProjectionPose {
    var yaw = 0.0
    var pitch = -8.5
    var q = 0.0
    static let rest = Self()
    static let reduced = Self(yaw: 4, pitch: 20, q: 0.5)
}

struct WorldSunProjectionCamera {
    let forward: WorldSunProjectionVector
    let right: WorldSunProjectionVector
    let up: WorldSunProjectionVector
    init(_ pose: WorldSunProjectionPose) {
        let yaw = pose.yaw * .pi / 180, pitch = pose.pitch * .pi / 180
        forward = .init(x: sin(yaw) * cos(pitch), y: sin(pitch), z: cos(yaw) * cos(pitch))
        right = .init(x: cos(yaw), y: 0, z: -sin(yaw))
        up = .init(x: -sin(yaw) * sin(pitch), y: cos(pitch), z: -cos(yaw) * sin(pitch))
    }
    func project(_ direction: WorldSunProjectionVector) -> CGPoint? {
        let z = direction.dot(forward)
        guard z > 0.06 else { return nil }
        return CGPoint(x: 196.5 + 510 * direction.dot(right) / z,
                       y: 426 - 510 * direction.dot(up) / z)
    }
    func ray(_ point: CGPoint) -> WorldSunProjectionVector {
        (forward + right * ((point.x - 196.5) / 510) + up * (-(point.y - 426) / 510)).unit
    }
}

enum WorldSunProjectionMath {
    static let loop = 8.0
    static let yawSamples = [-22.0, -12, 0, 4, 8, 15, 22]
    static let pitchSamples = [-14.0, -8.5, 4, 20, 34, 48, 58]
    static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double { min(high, max(low, x)) }
    static func smooth(_ value: Double) -> Double { let u = clamp(value); return u * u * (3 - 2 * u) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func mix(_ a: WorldSunProjectionPose, _ b: WorldSunProjectionPose, _ u: Double) -> WorldSunProjectionPose {
        .init(yaw: mix(a.yaw, b.yaw, u), pitch: mix(a.pitch, b.pitch, u), q: mix(a.q, b.q, u))
    }
    static func constrained(_ pose: WorldSunProjectionPose) -> WorldSunProjectionPose {
        .init(yaw: clamp(pose.yaw, -22, 22), pitch: clamp(pose.pitch, -14, 58), q: clamp(pose.q))
    }
    static func authored(_ seconds: Double) -> WorldSunProjectionPose {
        let t = max(0, seconds).truncatingRemainder(dividingBy: loop)
        if t < 1 || t >= 7.55 { return .rest }
        if t < 3 { let e = smooth((t - 1) / 2); return .init(yaw: 8 * e, pitch: mix(-8.5, 48, e), q: 0.34 * e) }
        if t < 4.9 { return .init(yaw: 8, pitch: 48, q: mix(0.34, 1, smooth((t - 3) / 1.9))) }
        if t < 5.75 { return .init(yaw: 8, pitch: 48, q: 1) }
        return mix(.init(yaw: 8, pitch: 48, q: 1), .rest, smooth((t - 5.75) / 1.8))
    }
    static func sun(_ q: Double) -> WorldSunProjectionVector {
        let azimuth = (-6 + 30 * q) * .pi / 180
        let elevation = (-0.5 + 64 * sin(.pi * q / 2)) * .pi / 180
        return .init(x: sin(azimuth) * cos(elevation), y: sin(elevation), z: cos(azimuth) * cos(elevation))
    }
    static func time(_ q: Double) -> String {
        let minute = 309 + Int((clamp(q) * 350).rounded())
        return String(format: "%02d:%02d", minute / 60, minute % 60)
    }
    // Carry displacement is bounded to two degrees and has an exact finite end.
    static func carry(_ velocity: Double, age: Double) -> Double {
        guard age > 0, age < 0.6 else { return 0 }
        let omega = 2 * Double.pi / 0.35, zeta = 0.85, wd = omega * sqrt(1 - zeta * zeta)
        let initial = 2 * tanh(velocity / (2 * omega)) * omega
        let gate = age < 0.42 ? 1 : 1 - smooth((age - 0.42) / 0.18)
        return initial / wd * exp(-zeta * omega * age) * sin(wd * age) * gate
    }
    static func released(_ pose: WorldSunProjectionPose, yawVelocity: Double, pitchVelocity: Double,
                         age: Double, reduced: Bool) -> WorldSunProjectionPose {
        var result = pose
        if !reduced {
            result.yaw += carry(yawVelocity, age: age)
            result.pitch += carry(pitchVelocity, age: age)
        }
        return constrained(result)
    }
    static func recovery(_ from: WorldSunProjectionPose, age: Double) -> WorldSunProjectionPose {
        mix(from, .rest, smooth(age / 0.6))
    }
    static func atlasWeights(_ pose: WorldSunProjectionPose) -> [(index: Int, weight: Double)] {
        func bracket(_ samples: [Double], _ value: Double) -> (Int, Int, Double) {
            let x = clamp(value, samples[0], samples[samples.count - 1])
            if x <= samples[0] { return (0, 0, 0) }
            for index in 1..<samples.count where x <= samples[index] {
                return (index - 1, index, (x - samples[index - 1]) / (samples[index] - samples[index - 1]))
            }
            return (samples.count - 1, samples.count - 1, 0)
        }
        let x = bracket(yawSamples, pose.yaw), y = bracket(pitchSamples, pose.pitch)
        return [(y.0 * 7 + x.0, (1 - x.2) * (1 - y.2)),
                (y.0 * 7 + x.1, x.2 * (1 - y.2)),
                (y.1 * 7 + x.0, (1 - x.2) * y.2),
                (y.1 * 7 + x.1, x.2 * y.2)].filter { $0.1 > 0 }
    }
}
