import SwiftUI
import simd

struct StitchWeavePreviewPose {
    var p: Double
    var rotation: SIMD3<Double>
    var lagRotation: SIMD3<Double>
    var lift: Double
    var hem = 0.0
    var anticipation = 0.0
    var light = 0.0
    var lightPosition = 8.3
    var crownElapsed = 0.0
    var topologySpeed = 1.0

    static let rest = StitchWeavePreviewPose(p: 0, rotation: SIMD3(30, -10, -7), lagRotation: SIMD3(30, -10, -7), lift: 0)
    static let finished = StitchWeavePreviewPose(p: 1, rotation: SIMD3(8, 6, -3), lagRotation: SIMD3(8, 6, -3), lift: 0, crownElapsed: 10)

    /// Retain every authored offset on Down, then let the finger change the same material pose.
    func moved(to progress: Double) -> StitchWeavePreviewPose {
        let original = StitchWeavePreviewMath.base(p)
        var next = StitchWeavePreviewMath.base(progress)
        next.rotation += rotation - original.rotation
        next.lagRotation += lagRotation - original.lagRotation
        next.lift += lift - original.lift
        next.hem = hem; next.anticipation = anticipation
        next.light = light; next.lightPosition = lightPosition
        next.crownElapsed = crownElapsed; next.topologySpeed = topologySpeed
        return next
    }
}

struct StitchWeavePreviewAction {
    let stamp: Date
    let from: StitchWeavePreviewPose
    let target: Double
    let velocity: Double
    let kick: Double
    let saving: Bool

    func pose(at date: Date) -> StitchWeavePreviewPose {
        let age = max(0, date.timeIntervalSince(stamp))
        let m = StitchWeavePreviewMath.self
        if age >= 2.65 { return .rest }
        if age >= 2 {
            return m.base(target * (1 - m.io((age - 2) / 0.65)))
        }
        let p = saving ? from.p + (target - from.p) * m.entrance(age / 0.48)
            : m.spring(from.p, target, velocity: velocity, time: age, response: 0.36, damping: 0.82)
        var result = m.base(m.clamp(p))
        let base = m.base(from.p)
        let decay = m.decay(age, response: 0.44, damping: 0.7)
        result.rotation += (from.rotation - base.rotation) * decay
        result.lagRotation += (from.lagRotation - base.lagRotation) * decay
        result.lift += (from.lift - base.lift) * decay + kick * exp(-10 * age) * sin(16 * age)
        result.hem = from.hem * m.decay(age, response: 0.25, damping: 0.82)
        result.anticipation = from.anticipation * exp(-14 * age)
        result.light = from.light * exp(-8 * age); result.lightPosition = from.lightPosition
        result.crownElapsed = from.crownElapsed + age
        result.topologySpeed = from.topologySpeed
        return result
    }
}

enum StitchWeavePreviewMath {
    static let yarns: [UInt32] = [0xDEDCD3, 0x889A8C, 0x7D8C92]
    static var initialMaterial: [Float] { solidMaterial(pattern: 0, yarn: 0) }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ x: Double) -> Double { let x = clamp(x); return x * x * (3 - 2 * x) }
    static func hash(_ i: Int) -> Double { let n = sin(Double(i) * 12.9898 + 4.1414) * 43758.5453; return n - floor(n) }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        var lo = 0.0, hi = 1.0
        for _ in 0..<18 {
            let u = (lo + hi) / 2, a = 1 - u
            let value = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u
            if value < x { lo = u } else { hi = u }
        }
        let u = (lo + hi) / 2, a = 1 - u
        return 3 * a * a * u * y1 + 3 * a * u * u * y2 + u * u * u
    }
    static func io(_ x: Double) -> Double { bezier(x, 0.65, 0, 0.35, 1) }
    static func entrance(_ x: Double) -> Double { bezier(x, 0.2, 0.8, 0.2, 1) }
    static func progress(_ t: Double) -> Double {
        if t < 1.23 { return 0 }
        if t < 3.95 { return io((t - 1.23) / 2.72) }
        if t < 5.2 { return 1 }
        return 1 - io((t - 5.2) / 1.9)
    }
    static func orientation(_ t: Double) -> SIMD3<Double> {
        let q = t < 5.2 ? io((t - 1.23) / 2.82) : 1 - io((t - 5.2) / 1.9)
        return SIMD3(30 - 22 * q, -10 + 16 * q, -7 + 4 * q)
    }
    static func base(_ p: Double) -> StitchWeavePreviewPose {
        let p = clamp(p), r = SIMD3(30 - 22 * p, -10 + 16 * p, -7 + 4 * p)
        return .init(p: p, rotation: r, lagRotation: r, lift: 18 * sin(.pi * p))
    }
    static func authored(_ time: Double) -> StitchWeavePreviewPose {
        let t = max(0, time).truncatingRemainder(dividingBy: 8)
        var pose = base(progress(t))
        pose.rotation = orientation(t); pose.lagRotation = orientation(max(0, t - 0.07))
        pose.lift = 0
        if t >= 1.23 && t < 3.95 { pose.lift = 18 * entrance((t - 1.23) / 0.32) }
        if t >= 3.95 && t < 4.45 { pose.lift = 18 * decay(t - 3.95, response: 0.45, damping: 0.76) * (1 - smooth((t - 4.30) / 0.15)) }
        if t >= 5.2 && t < 7.1 { pose.lift = 18 * sin(.pi * pose.p) }
        if t >= 1.1 && t < 1.23 { pose.anticipation = sin(.pi * smooth((t - 1.1) / 0.13)); pose.hem = -4 * pose.anticipation }
        if t >= 6.85 && t < 7.1 { pose.hem = 2 * io((t - 6.85) / 0.25) }
        if t >= 7.1 && t < 7.3 { pose.hem = 2 * decay(t - 7.1, response: 0.25, damping: 0.82) * (1 - smooth((t - 7.2) / 0.1)) }
        if t >= 4.15 && t < 4.9 {
            let q = (t - 4.15) / 0.75
            pose.light = 0.14 * sin(.pi * q); pose.lightPosition = 8.3 + 376.4 * smooth(q)
        }
        pose.crownElapsed = t >= 3.95 && t < 5.2 ? t - 3.95 : 0
        pose.topologySpeed = t < 5.2 ? 1.9 / 2.72 : 1
        return pose
    }
    /// A canonical inverse clock keeps the stitch order identical on knitting and unknitting.
    static func canonicalTime(_ p: Double) -> Double {
        if p <= 0 { return 0 }; if p >= 1 { return 1.9 }
        var lo = 0.0, hi = 1.0
        for _ in 0..<18 { let u = (lo + hi) / 2; if u * u * (3 - 2 * u) < p { lo = u } else { hi = u } }
        let u = (lo + hi) / 2, a = 1 - u
        return 1.9 * (3 * a * a * u * 0.65 + 3 * a * u * u * 0.35 + u * u * u)
    }
    static func delay(_ column: Int) -> Double { 0.0025 * Double(column % 4) + 0.004 * (2 * hash(column) - 1) }
    static func columnRows(p: Double, canonical: Double, column: Int) -> Double {
        if p <= 0 { return 3 }; if p >= 1 { return 48 }
        return 3 + 45 * io((canonical - delay(column)) / 1.9)
    }
    static func spring(_ from: Double, _ to: Double, velocity: Double, time: Double, response: Double, damping: Double) -> Double {
        let w = 2 * Double.pi / response, a = damping * w, b = w * sqrt(1 - damping * damping)
        let delta = from - to
        return to + exp(-a * time) * (delta * cos(b * time) + (velocity + a * delta) / b * sin(b * time))
    }
    static func decay(_ time: Double, response: Double, damping: Double) -> Double { spring(1, 0, velocity: 0, time: time, response: response, damping: damping) }
    static func solidMaterial(pattern: Int, yarn: Int) -> [Float] {
        var values = [Float](repeating: 0, count: 48 * 6)
        for row in 0..<48 { values[row * 6 + pattern] = 1; values[row * 6 + 3 + yarn] = 1 }
        return values
    }
    static func materialMix(row: Int, age: Double, rows: Double, reduced: Bool) -> Double {
        if age < 0 { return 0 }
        if reduced { return smooth(age / 0.18) }
        let lag = clamp((rows - 1 - Double(row)) / max(1, rows - 1)) * 0.22
        return smooth((age - lag) / 0.16)
    }
    static func rotationMatrix(_ degrees: SIMD3<Double>) -> [Double] {
        let x = degrees.x * .pi / 180, y = degrees.y * .pi / 180, z = degrees.z * .pi / 180
        let cx = cos(x), sx = sin(x), cy = cos(y), sy = sin(y), cz = cos(z), sz = sin(z)
        return [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx]
    }
}
