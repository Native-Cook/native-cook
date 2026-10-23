import SwiftUI
import simd

struct GeodeFocusRewardPiecePose: Equatable {
    var center: SIMD3<Double>
    var rotation: SIMD3<Double>
    var alpha: Double
}

struct GeodeFocusRewardHeroPose: Equatable {
    var p: Double
    var globalScale: Double
    var globalY: Double
    var glowGain: Double
    var coreYaw: Double
    var coreScale: Double
    var manualYaw: Double
    var crack: Double
    var pieces: [GeodeFocusRewardPiecePose]
    var crumbAmount: Double
}

struct GeodeFocusRewardCopyPose: Equatable {
    var restAlpha: Double
    var resultAlpha: [Double]
    var resultY: [Double]
}

struct GeodeFocusRewardFrame: Equatable {
    var hero: GeodeFocusRewardHeroPose
    var copy: GeodeFocusRewardCopyPose
}

/// All visible values are pure functions of one authored time or a complete touch capture.
enum GeodeFocusRewardMath {
    static let loop = 8.0
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func smooth(_ x: Double) -> Double { let x = clamp(x); return x * x * (3 - 2 * x) }
    static func segment(_ x: Double, _ a: Double, _ b: Double) -> Double { clamp((x - a) / (b - a)) }
    static func sm(_ x: Double, _ a: Double, _ b: Double) -> Double { smooth(segment(x, a, b)) }
    static func mix(_ a: Double, _ b: Double, _ q: Double) -> Double { a + (b - a) * q }
    static func mix(_ a: SIMD3<Double>, _ b: SIMD3<Double>, _ q: Double) -> SIMD3<Double> { a + (b - a) * q }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        var lo = 0.0, hi = 1.0
        for _ in 0..<18 {
            let u = (lo + hi) * 0.5, a = 1 - u
            let px = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u
            if px < x { lo = u } else { hi = u }
        }
        let u = (lo + hi) * 0.5, a = 1 - u
        return 3 * a * a * u * y1 + 3 * a * u * u * y2 + u * u * u
    }
    static func e(_ x: Double) -> Double { bezier(x, 0.2, 0.8, 0.2, 1) }
    static func io(_ x: Double) -> Double { bezier(x, 0.65, 0, 0.35, 1) }
    static func spring(_ from: Double, _ to: Double, velocity: Double, time: Double, response: Double, damping: Double) -> Double {
        let w = 2 * Double.pi / response, a = damping * w, b = w * sqrt(1 - damping * damping), d = from - to
        return to + exp(-a * time) * (d * cos(b * time) + (velocity + a * d) * sin(b * time) / b)
    }
    static func decay(_ age: Double, response: Double, damping: Double) -> Double {
        spring(1, 0, velocity: 0, time: max(0, age), response: response, damping: damping)
    }
    static func rotate(_ p: SIMD3<Double>, _ angles: SIMD3<Double>) -> SIMD3<Double> {
        let y = angles.x * .pi / 180, x = angles.y * .pi / 180, z = angles.z * .pi / 180
        let a = SIMD3(p.x * cos(y) + p.z * sin(y), p.y, -p.x * sin(y) + p.z * cos(y))
        let b = SIMD3(a.x, a.y * cos(x) - a.z * sin(x), a.y * sin(x) + a.z * cos(x))
        return SIMD3(b.x * cos(z) - b.y * sin(z), b.x * sin(z) + b.y * cos(z), b.z)
    }
    static var rest: GeodeFocusRewardFrame { steady(0) }
    static var reduced: GeodeFocusRewardFrame {
        var frame = steady(1)
        frame.hero.glowGain = 0.72; frame.hero.crumbAmount = 0
        return frame
    }
    private static func steady(_ progress: Double) -> GeodeFocusRewardFrame {
        let p = clamp(progress), mesh = GeodeFocusRewardGeometry.shared
        let alpha = 1 - sm(p, 0.88, 1)
        let pieces = mesh.pieces.enumerated().map { i, piece -> GeodeFocusRewardPiecePose in
            let delay = piece.delay / 1.6
            let q = smooth((segment(p, 0.18, 0.94) - delay) / (1 - delay))
            let rotation = piece.finalRotation * q
            let pivot = piece.pivot - piece.center
            let hinge = pivot - rotate(pivot, rotation)
            let peel = 1 - sm(q, 0.60, 1)
            let end = sm(q, 0.60, 1)
            let radial = simd_normalize(SIMD3(piece.center.x, piece.center.y, 0) + SIMD3(0.001, 0, 0))
            let overshoot = 5 * sin(.pi * end) * (1 - end)
            let center = mix(piece.center, piece.finalCenter, q) + hinge * peel + radial * overshoot
            return .init(center: center, rotation: rotation, alpha: alpha)
        }
        let result = sm(p, 0.78, 0.96)
        let hero = GeodeFocusRewardHeroPose(p: p, globalScale: 1, globalY: 0,
            glowGain: 0.35 + 0.37 * sm(p, 0.18, 1), coreYaw: -8 + 14 * sm(p, 0.18, 1),
            coreScale: 0.94 + 0.06 * p, manualYaw: 0, crack: sm(p, 0, 0.18), pieces: pieces,
            crumbAmount: sin(.pi * sm(p, 0.55, 0.98)) * (1 - sm(p, 0.88, 1)))
        return .init(hero: hero, copy: .init(restAlpha: 1 - sm(p, 0.12, 0.25),
            resultAlpha: Array(repeating: result, count: 4), resultY: Array(repeating: 10 * (1 - result), count: 4)))
    }
    static func authored(_ time: Double) -> GeodeFocusRewardFrame {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        let p: Double
        if t < 1.23 { p = 0 }
        else if t < 2.60 { p = 0.18 * io(segment(t, 1.23, 2.60)) }
        else if t < 3.50 { p = mix(0.18, 0.65, e(segment(t, 2.60, 3.50))) }
        else if t < 4.20 { p = mix(0.65, 1, e(segment(t, 3.50, 4.20))) }
        else if t < 5.50 { p = 1 }
        else { p = 1 - io(segment(t, 5.50, 7.10)) }
        var f = steady(p)
        if t >= 1.10 && t < 1.23 {
            let q = e(segment(t, 1.10, 1.23)); f.hero.globalScale = 1 - 0.035 * q; f.hero.globalY = 2 * q
        } else if t >= 1.23 && t < 2.60 {
            let q = sm(t, 1.23, 2.60); f.hero.globalScale = mix(0.965, 1, q); f.hero.globalY = 2 * (1 - q)
        } else if t >= 5.50 && t < 7.10 {
            let q = sm(t, 5.50, 7.10); f.hero.globalScale = 1 - 0.015 * q; f.hero.globalY = q
        } else if t >= 7.10 && t < 7.30 {
            let u = t - 7.10, r = decay(u, response: 0.25, damping: 0.82) * (1 - smooth(u / 0.20))
            f.hero.globalScale = 1 - 0.015 * r; f.hero.globalY = r
        }
        if t < 3.80 { f.hero.glowGain = 0.35 }
        else if t < 4.075 { f.hero.glowGain = mix(0.35, 1, sm(t, 3.80, 4.075)) }
        else if t < 4.35 { f.hero.glowGain = mix(1, 0.72, sm(t, 4.075, 4.35)) }
        else if t < 5.50 { f.hero.glowGain = 0.72 }
        else { f.hero.glowGain = mix(0.72, 0.35, sm(t, 5.50, 7.10)) }
        f.copy.restAlpha = (1 - sm(t, 2.85, 3.25)) + sm(t, 6.75, 7.12)
        for i in 0..<4 {
            let q = e(segment(t, 4.05 + Double(i) * 0.04, 4.36 + Double(i) * 0.04)) * (1 - sm(t, 5.50, 5.77))
            f.copy.resultAlpha[i] = q; f.copy.resultY[i] = 10 * (1 - q)
        }
        return f
    }
    /// Difference of complete steady poses preserves every visible residual at zero movement.
    private static func moved(from f: GeodeFocusRewardFrame, to p: Double, residual: Double = 1, copyResidual: Double = 1) -> GeodeFocusRewardFrame {
        let old = steady(f.hero.p); var next = steady(p)
        next.hero.globalScale = 1 + (f.hero.globalScale - 1) * residual
        next.hero.globalY = f.hero.globalY * residual
        next.hero.glowGain = clamp(next.hero.glowGain + (f.hero.glowGain - old.hero.glowGain) * residual, 0.35, 1)
        next.hero.coreYaw += (f.hero.coreYaw - old.hero.coreYaw) * residual
        next.hero.coreScale += (f.hero.coreScale - old.hero.coreScale) * residual
        next.hero.manualYaw = f.hero.manualYaw
        next.hero.crack = clamp(next.hero.crack + (f.hero.crack - old.hero.crack) * residual)
        next.hero.crumbAmount = clamp(next.hero.crumbAmount + (f.hero.crumbAmount - old.hero.crumbAmount) * residual)
        for i in 0..<12 {
            next.hero.pieces[i].center += (f.hero.pieces[i].center - old.hero.pieces[i].center) * residual
            next.hero.pieces[i].rotation += (f.hero.pieces[i].rotation - old.hero.pieces[i].rotation) * residual
            next.hero.pieces[i].alpha = clamp(next.hero.pieces[i].alpha + (f.hero.pieces[i].alpha - old.hero.pieces[i].alpha) * residual)
        }
        // Preserve the exact captured copy at Down, then hand it to the destination.
        // An authored title can lag its geometry; retaining that lag at p1 overlaps the result.
        let distanceToEdge = p >= f.hero.p ? 1 - f.hero.p : f.hero.p
        let copyTravel = distanceToEdge > 0 ? abs(p - f.hero.p) / min(0.18, distanceToEdge) : 0
        let copyWeight = residual * copyResidual * (1 - smooth(copyTravel))
        next.copy.restAlpha = clamp(next.copy.restAlpha + (f.copy.restAlpha - old.copy.restAlpha) * copyWeight)
        for i in 0..<4 {
            next.copy.resultAlpha[i] = clamp(next.copy.resultAlpha[i] + (f.copy.resultAlpha[i] - old.copy.resultAlpha[i]) * copyWeight)
            next.copy.resultY[i] += (f.copy.resultY[i] - old.copy.resultY[i]) * copyWeight
        }
        return next
    }
    static func held(from: GeodeFocusRewardFrame, elapsed: Double, translation: CGSize, yawDeflection: Double) -> GeodeFocusRewardFrame {
        if elapsed <= 0.12 && translation == .zero && yawDeflection == 0 { return from }
        let p = clamp(from.hero.p + 0.62 * max(0, elapsed - 0.12) - Double(translation.height) / 210)
        var f = moved(from: from, to: p, copyResidual: 1 - sm(elapsed, 0.12, 0.43))
        let base = clamp(from.hero.manualYaw + 0.09 * Double(translation.width), -12, 12)
        let capturedTransient = from.hero.manualYaw - clamp(from.hero.manualYaw, -12, 12)
        f.hero.manualYaw = clamp(base + capturedTransient * decay(max(0, elapsed - 0.12), response: 0.4, damping: 0.8) + yawDeflection, -17, 17)
        return f
    }
    static func released(from: GeodeFocusRewardFrame, opens: Bool, velocity: Double, elapsed: Double, cancelled: Bool, yawTarget: Double? = nil) -> GeodeFocusRewardFrame {
        let age = max(0, elapsed)
        if age == 0 { return from }
        let target = opens && !cancelled ? 1.0 : 0.0, response = target == 1 ? 0.45 : 0.38, damping = target == 1 ? 0.78 : 0.85
        let p = clamp(spring(from.hero.p, target, velocity: cancelled ? 0 : clamp(velocity, -1.6, 1.6), time: age, response: response, damping: damping))
        var f = moved(from: from, to: p, residual: decay(age, response: response, damping: damping))
        if cancelled { f.hero.manualYaw = from.hero.manualYaw * (1 - sm(age, 0, 0.24)) }
        else if let yawTarget {
            f.hero.manualYaw = yawTarget + (from.hero.manualYaw - yawTarget) * decay(age, response: 0.4, damping: 0.8)
        }
        return f
    }
    static func replay(from: GeodeFocusRewardFrame, elapsed: Double) -> GeodeFocusRewardFrame {
        var frame = steady(e(segment(max(0, elapsed), 0, 1.25)))
        frame.hero.manualYaw = from.hero.manualYaw
        return frame
    }
    static func recovering(from: GeodeFocusRewardFrame, elapsed: Double) -> GeodeFocusRewardFrame {
        if elapsed <= 0 { return from }; if elapsed >= 0.65 { return rest }
        let q = io(elapsed / 0.65), r = rest
        var f = from
        f.hero.p = mix(from.hero.p, 0, q)
        f.hero.globalScale = mix(from.hero.globalScale, 1, q); f.hero.globalY = from.hero.globalY * (1 - q)
        f.hero.glowGain = mix(from.hero.glowGain, 0.35, q); f.hero.coreYaw = mix(from.hero.coreYaw, -8, q)
        f.hero.coreScale = mix(from.hero.coreScale, 0.94, q); f.hero.manualYaw = from.hero.manualYaw * (1 - q)
        f.hero.crack = from.hero.crack * (1 - q); f.hero.crumbAmount = from.hero.crumbAmount * (1 - q)
        for i in 0..<12 {
            f.hero.pieces[i].center = mix(from.hero.pieces[i].center, r.hero.pieces[i].center, q)
            f.hero.pieces[i].rotation = mix(from.hero.pieces[i].rotation, .zero, q)
            f.hero.pieces[i].alpha = mix(from.hero.pieces[i].alpha, 1, q)
        }
        f.copy.restAlpha = mix(from.copy.restAlpha, 1, q)
        for i in 0..<4 { f.copy.resultAlpha[i] = from.copy.resultAlpha[i] * (1 - q); f.copy.resultY[i] = mix(from.copy.resultY[i], 10, q) }
        return f
    }
}
