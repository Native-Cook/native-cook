import Foundation
import CoreGraphics

struct LumaRunStudioParams: Equatable, Hashable, Sendable {
    var q: Double
    var angle: Double
    var low: Double
    var high: Double
    var roiX: Double
    var roiY: Double
    static let rest = Self(q: 0, angle: 0, low: 0.44, high: 0.58, roiX: 0, roiY: 0)
    static let reduced = Self(q: 1, angle: 0, low: 0.28, high: 0.78, roiX: 0, roiY: 0)
}
struct LumaRunStudioKnot: Sendable {
    let time: Double
    let params: LumaRunStudioParams
}
struct LumaRunStudioSource: @unchecked Sendable {
    let master: CGImage
    let canonical: CGImage
    let rgba: Data
}
struct LumaRunStudioBitmap: @unchecked Sendable {
    let image: CGImage
    let params: LumaRunStudioParams
}
struct LumaRunStudioImagePair {
    let a: LumaRunStudioBitmap
    let b: LumaRunStudioBitmap
    var mix: Double
    init(a: LumaRunStudioBitmap, b: LumaRunStudioBitmap, mix: Double) {
        self.a = a; self.b = b; self.mix = mix
    }
    init(_ bitmap: LumaRunStudioBitmap) { a = bitmap; b = bitmap; mix = 0 }
}
struct LumaRunStudioAppearance: Equatable {
    var bracketAlpha: Double
    var handleScale: Double
    var anticipation: Double
    var lowOffset: Double = 0
    var highOffset: Double = 0
    static let editing = Self(bracketAlpha: 1, handleScale: 1, anticipation: 0)
}
struct LumaRunStudioScene {
    var pair: LumaRunStudioImagePair
    var params: LumaRunStudioParams
    var appearance: LumaRunStudioAppearance
    var compare: Double
    var kept: Bool
}
struct LumaRunStudioPublication {
    let generation: Int
    let sequence: Int
    let bitmap: LumaRunStudioBitmap
}
struct LumaRunStudioRecoveryPublication {
    let generation: Int
    let bitmaps: [LumaRunStudioBitmap]
}

enum LumaRunStudioMath {
    static let loop = 8.0
    static let zeroVelocity = LumaRunStudioParams(q: 0, angle: 0, low: 0, high: 0, roiX: 0, roiY: 0)
    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func cubic(_ x: Double) -> Double {
        let u = clamp(x); return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func mix(_ a: LumaRunStudioParams, _ b: LumaRunStudioParams, _ u: Double) -> LumaRunStudioParams {
        .init(q: a.q + (b.q - a.q) * u, angle: a.angle + (b.angle - a.angle) * u,
              low: a.low + (b.low - a.low) * u, high: a.high + (b.high - a.high) * u,
              roiX: a.roiX + (b.roiX - a.roiX) * u, roiY: a.roiY + (b.roiY - a.roiY) * u)
    }
    static func bounded(_ p: LumaRunStudioParams) -> LumaRunStudioParams {
        let low = clamp(p.low, 0.08, 0.84), high = clamp(p.high, low + 0.08, 0.92)
        return .init(q: clamp(p.q), angle: clamp(p.angle, -90, 90), low: low, high: high,
                     roiX: clamp(p.roiX, -30, 30), roiY: clamp(p.roiY, -42, 42))
    }
    static func quantized(_ p: LumaRunStudioParams) -> LumaRunStudioParams {
        func round(_ x: Double, _ step: Double) -> Double { floor(x / step + 0.5) * step }
        let p = bounded(p)
        return bounded(.init(q: round(p.q, 1 / 64), angle: round(p.angle, 1), low: round(p.low, 0.005),
                             high: round(p.high, 0.005), roiX: round(p.roiX, 1), roiY: round(p.roiY, 1)))
    }
    static func authoredParams(_ time: Double) -> LumaRunStudioParams {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        let window = cubic((t - 1.30) / 1.35)
        var p = LumaRunStudioParams(q: 1 - pow(1 - clamp((t - 1.30) / 1.88), 3),
            angle: 90 * cubic((t - 2.85) / 2), low: 0.44 - 0.16 * window,
            high: 0.58 + 0.20 * window, roiX: 0, roiY: 0)
        if t >= 5.55 {
            p.q = 1 - cubic((t - 5.55) / 1.40)
            let back = cubic((t - 5.55) / 1.60)
            p.angle = 90 * (1 - back); p.low = 0.28 + 0.16 * back; p.high = 0.78 - 0.20 * back
        }
        if t >= 7.15 || t <= 1.30 { return .rest }
        if t >= 6.95 { p.q = 0 }
        return p
    }
    static let knots: [LumaRunStudioKnot] = {
        var times = [0.0]
        for k in 0...15 { times.append(k == 15 ? 2.65 : 1.30 + 1.35 * Double(k) / 15.0) }
        for k in 1...3 { times.append(k == 3 ? 2.85 : 2.65 + 0.20 * Double(k) / 3.0) }
        for k in 1...4 { times.append(k == 4 ? 3.18 : 2.85 + 0.33 * Double(k) / 4.0) }
        for k in 1...21 { times.append(k == 21 ? 4.85 : 3.18 + 1.67 * Double(k) / 21.0) }
        times += [5.55]
        for k in 1...17 { times.append(k == 17 ? 6.95 : 5.55 + 1.40 * Double(k) / 17.0) }
        times += [7.15]
        return times.map { .init(time: $0, params: authoredParams($0)) }
    }()
    static func appearance(_ time: Double) -> LumaRunStudioAppearance {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        if t <= 1.10 || t >= 7.40 { return .editing }
        if t < 1.30 { return .init(bracketAlpha: 1, handleScale: 1, anticipation: 2 * sin(.pi * (t - 1.10) / 0.20)) }
        if t < 7.15 {
            let u = 1 - pow(1 - clamp((t - 1.30) / 1.88), 3)
            return .init(bracketAlpha: 1 - 0.78 * u, handleScale: 1 + 0.02 * u, anticipation: 0)
        }
        let alpha = spring(0.22, velocity: 0, target: 1, elapsed: t - 7.15, response: 0.32, damping: 0.85, end: 0.25, tail: 0.18)
        let scale = spring(1.02, velocity: 0, target: 1, elapsed: t - 7.15, response: 0.32, damping: 0.85, end: 0.25, tail: 0.18)
        return .init(bracketAlpha: clamp(alpha), handleScale: scale, anticipation: 0)
    }
    static func sample(_ images: [LumaRunStudioBitmap], times: [Double], age: Double) -> (LumaRunStudioImagePair, LumaRunStudioParams, LumaRunStudioParams) {
        guard images.count > 1, age > times[0] else { return (.init(images[0]), images[0].params, zeroVelocity) }
        guard age < times[times.count - 1] else { return (.init(images.last!), images.last!.params, zeroVelocity) }
        var i = 0
        while i + 1 < times.count && age >= times[i + 1] { i += 1 }
        let span = times[i + 1] - times[i], u = clamp((age - times[i]) / span)
        let a = images[i], b = images[i + 1]
        let p = mix(a.params, b.params, u)
        let velocity = mix(zeroVelocity, .init(q: b.params.q - a.params.q, angle: b.params.angle - a.params.angle,
            low: b.params.low - a.params.low, high: b.params.high - a.params.high,
            roiX: b.params.roiX - a.params.roiX, roiY: b.params.roiY - a.params.roiY), 1 / span)
        return (.init(a: a, b: b, mix: p.q == 0 ? 0 : u), p, velocity)
    }
    static func authored(_ time: Double, images: [LumaRunStudioBitmap], kept: Bool = false) -> (LumaRunStudioScene, LumaRunStudioParams) {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        let s = sample(images, times: knots.map(\.time), age: t)
        return (.init(pair: s.0, params: s.1, appearance: appearance(t), compare: 0, kept: kept), s.2)
    }
    static func settledTarget(_ p: LumaRunStudioParams, velocityY: Double) -> LumaRunStudioParams {
        var target = p
        target.q = p.q > 0.5 || velocityY < -600 ? 1 : 0
        target.angle = floor(p.angle / 15 + 0.5) * 15
        target.low = floor(p.low * 100 + 0.5) / 100
        target.high = floor(p.high * 100 + 0.5) / 100
        return bounded(target)
    }
    static func spring(_ start: Double, velocity: Double, target: Double, elapsed: Double,
                       response: Double = 0.42, damping: Double = 0.82, end: Double = 0.60, tail: Double = 0.42) -> Double {
        if elapsed <= 0 { return start }; if elapsed >= end { return target }
        let w = 2 * Double.pi / response, c = damping * w, wd = w * sqrt(1 - damping * damping)
        let a = start - target, b = (velocity + c * a) / wd
        let raw = exp(-c * elapsed) * (a * cos(wd * elapsed) + b * sin(wd * elapsed))
        return target + raw * (1 - smooth((elapsed - tail) / (end - tail)))
    }
    static func settlement(from: LumaRunStudioParams, velocity: LumaRunStudioParams,
                           target: LumaRunStudioParams, elapsed: Double) -> LumaRunStudioParams {
        if elapsed <= 0 { return from }; if elapsed >= 0.60 { return target }
        return bounded(.init(q: spring(from.q, velocity: velocity.q, target: target.q, elapsed: elapsed),
            angle: spring(from.angle, velocity: velocity.angle, target: target.angle, elapsed: elapsed),
            low: spring(from.low, velocity: velocity.low, target: target.low, elapsed: elapsed),
            high: spring(from.high, velocity: velocity.high, target: target.high, elapsed: elapsed),
            roiX: spring(from.roiX, velocity: velocity.roiX, target: target.roiX, elapsed: elapsed),
            roiY: spring(from.roiY, velocity: velocity.roiY, target: target.roiY, elapsed: elapsed)))
    }
    static func recoveryParams(from: LumaRunStudioParams, elapsed: Double) -> LumaRunStudioParams {
        elapsed <= 0 ? from : (elapsed >= 0.60 ? .rest : mix(from, .rest, cubic(elapsed / 0.60)))
    }
    static func rubber(_ over: Double) -> Double { over / (1 + abs(over) / 80) * 0.55 }
}
