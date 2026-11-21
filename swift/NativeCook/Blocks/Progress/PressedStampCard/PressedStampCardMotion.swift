import SwiftUI

struct PressedStampCardFrame {
    var time: Double = 0
    var count = 5
    var previousCount = 5
    var stampProgress: CGFloat = 1
    var roll: CGFloat = 1
    var target = -1
    var tool: CGFloat = 0
    var toolPoint = CGPoint.zero // card-local, like the RN fly seal
    var toolRadius: CGFloat = 37
    var toolRotation: CGFloat = 0
    var toolLift: CGFloat = 0
    var gold: CGFloat = 0
    var buttonGold: CGFloat = 0
    var flip: CGFloat = 0
    var dip: CGFloat = 0
    var foil: CGFloat = -1
    var rattle = -1
    var orders: CGFloat = 5
    var wet = Array(repeating: CGFloat(0), count: 8)
    var sealGold = Array(repeating: CGFloat(0), count: 8)
    var sealBounce = Array(repeating: CGFloat(0), count: 8)
    var sealFlash = Array(repeating: CGFloat(0), count: 8)
}

enum PressedStampCardMotion {
    static let loop = 10.0
    static let events = [0.6, 2.2, 3.9]
    static let impacts = [1.56, 3.16, 5.04]
    static let rotations: [CGFloat] = [-6, 4, -3, 7, -5, 2, -7, 5]
    static let offsets: [CGPoint] = [(0.6,-0.4),(-0.8,0.5),(0.3,0.9),(-0.5,-0.7),(1,0.2),(-0.3,-1),(0.7,0.6),(-1,0.3)].map { CGPoint(x: $0.0, y: $0.1) }
    static func well(_ index: Int) -> CGPoint { CGPoint(x: 60 + 77 * CGFloat(index % 4), y: 114 + 94 * CGFloat(index / 4)) }
    static func seal(_ index: Int) -> CGPoint { let p = well(index); return CGPoint(x: p.x + offsets[index].x, y: p.y + offsets[index].y) }
    static func clamp(_ value: CGFloat) -> CGFloat { min(max(value, 0), 1) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> CGFloat { clamp(CGFloat((t - a) / (b - a))) }
    static func mix(_ a: CGFloat, _ b: CGFloat, _ p: CGFloat) -> CGFloat { a + (b - a) * p }
    static func bezier(_ p: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) -> CGFloat {
        guard p > 0, p < 1 else { return clamp(p) }
        var lo: CGFloat = 0, hi: CGFloat = 1, u = p
        for _ in 0..<16 {
            u = (lo + hi) / 2
            let v = 1 - u
            if 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u < p { lo = u } else { hi = u }
        }
        let v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }
    static func out(_ p: CGFloat) -> CGFloat { bezier(p, 0.2, 0.8, 0.2, 1) }
    static func inOut(_ p: CGFloat) -> CGFloat { bezier(p, 0.65, 0, 0.35, 1) }
    static func spring(_ time: Double, from: CGFloat = 0, to: CGFloat = 1, velocity: CGFloat = 0, damping: CGFloat, stiffness: CGFloat) -> CGFloat {
        guard time > 0 else { return from }
        let t = CGFloat(time), w = sqrt(stiffness), z = damping / (2 * w), x = from - to
        if z >= 1 { return to + exp(-w * t) * (x + (velocity + w * x) * t) }
        let wd = w * sqrt(1 - z * z)
        return to + exp(-z * w * t) * (x * cos(wd * t) + (velocity + z * w * x) / wd * sin(wd * t))
    }
    static func impulse(_ time: Double, depth: CGFloat) -> CGFloat {
        time >= 0 && time < 0.7 ? spring(time, to: 0, velocity: depth / 0.0447, damping: 15, stiffness: 93) : 0
    }
    static func rest(count: Int = 5, time: Double = 0) -> PressedStampCardFrame {
        var f = PressedStampCardFrame(time: time, count: count, previousCount: count, gold: count == 8 ? 1 : 0, buttonGold: count == 8 ? 1 : 0, orders: CGFloat(count))
        f.sealGold = Array(repeating: count == 8 ? 1 : 0, count: 8)
        return f
    }
    static func autoplay(_ raw: Double) -> PressedStampCardFrame {
        let t = (raw.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        var f = rest(time: t)
        for index in 0..<3 where t >= events[index] { f = stamp(f, elapsed: t - events[index], target: 5 + index) }
        if t >= 6.9, t < 7.4 { f.foil = segment(t, 6.9, 7.4) }
        if t >= 7.6, t < 8.1 { f.foil = segment(t, 7.6, 8.1) }
        if t >= 8.2 { f = flip(t - 8.2); f.time = t }
        if t >= 8.9, t < 8.98 {
            let p = out(segment(t, 8.9, 8.98)), first = seal(0)
            f.tool = 1; f.toolPoint = CGPoint(x: mix(420, first.x, p), y: first.y - 14 * sin(.pi * p)); f.toolRadius = mix(52, 37, p); f.toolLift = 1 - p
        }
        if t >= 8.98 {
            let index = min(4, Int((t - 8.98) / 0.13)), age = t - 8.98 - Double(index) * 0.13
            f = rest(count: index + 1, time: t); f.orders = 5; f.previousCount = index; f.target = index; f.roll = out(segment(age, 0, 0.06)); f.stampProgress = f.roll
            f.dip = age < 0.13 ? 1.5 * sin(.pi * CGFloat(age / 0.13)) : 0
            if t < 9.6 {
                let p = clamp(CGFloat(age / (index == 4 ? 0.1 : 0.13))), hop = 14 * sin(.pi * p)
                let from = seal(index), next = seal(min(4, index + 1))
                f.tool = 1; f.toolPoint = CGPoint(x: mix(from.x, index == 4 ? 420 : next.x, index == 4 ? out(p) : p), y: mix(from.y, next.y, p) - hop)
                f.toolRadius = 37 + hop * 0.28; f.toolRotation = hop * 0.15; f.toolLift = hop / 14
            }
        }
        if t >= 9.6 { f = rest(time: t) }
        return f
    }
    static func stamp(_ base: PressedStampCardFrame, elapsed: Double, target: Int) -> PressedStampCardFrame {
        var f = base
        let eighth = target == 7, impact = eighth ? 1.14 : 0.96, age = elapsed - impact
        let centre = seal(target)
        f.target = target
        f.orders = CGFloat(target) + clamp(spring(elapsed, damping: 22.1, stiffness: 157.9))
        f.tool = 0
        if elapsed >= 0.25, age < 0 {
            let p = inOut(segment(elapsed, 0.25, 0.7)), fall = bezier(segment(elapsed, impact - 0.14, impact), 0.32, 0, 0.67, 0)
            f.tool = 1; f.toolPoint = CGPoint(x: mix(420, centre.x, p), y: mix(536.4, centre.y, p) - 120 * p * (1 - p))
            f.toolRadius = mix(52, 37, fall); f.toolRotation = 8 * (1 - fall); f.toolLift = 1 - fall
        }
        guard age >= -1e-9 else { return f }
        let dt = max(0, age)
        f.count = target + 1; f.previousCount = target
        f.stampProgress = out(segment(dt, 0.08, 0.38)); f.roll = clamp(spring(dt - 0.04, damping: 30.5, stiffness: 322.3))
        f.wet[target] = 1 - segment(dt, 0, 1.2); f.sealFlash[target] = 0.55 * (1 - out(segment(dt, 0, 0.44)))
        f.dip = impulse(dt, depth: eighth ? 9 : 6)
        for index in 0..<target {
            let p = seal(index), distance = hypot(p.x - centre.x, p.y - centre.y), hit = dt - Double(distance / 900)
            if hit >= 0, hit < 0.6 { f.sealBounce[index] = spring(hit, to: 0, velocity: -65, damping: 12, stiffness: 260) }
        }
        let dwell = eighth ? 0.06 : 0.15
        if dt < dwell + 0.32 {
            let p = segment(dt, dwell, dwell + 0.32), lift = out(p), fly = eighth ? pow(p, 1.85) : lift
            f.tool = 1; f.toolPoint = CGPoint(x: mix(centre.x, 420, fly), y: centre.y - (eighth ? 120 * fly : 92 * lift))
            f.toolRadius = dt < 0.06 ? 35.5 : mix(37, 55, pow(p, 0.6929)); f.toolRotation = 8 * lift; f.toolLift = lift
        }
        if eighth {
            f.gold = segment(dt, 0, 1.2); f.foil = segment(dt, 0.08, 0.78)
            for index in 0..<8 {
                let p = seal(index), distance = hypot(p.x - centre.x, p.y - centre.y)
                f.sealGold[index] = out(segment(dt - Double(distance / 500), 0, 0.11))
            }
            f.buttonGold = out(segment(dt, 1.26, 1.76))
        }
        return f
    }
    static func manual(elapsed: Double, target: Int, charge: CGFloat) -> PressedStampCardFrame {
        stamp(rest(count: target, time: elapsed), elapsed: elapsed, target: target)
    }
    static func flip(_ elapsed: Double) -> PressedStampCardFrame {
        let p = elapsed >= 0.7 ? CGFloat(1) : clamp(spring(elapsed, damping: 16, stiffness: 140)), swapped = p >= 0.5
        var f = rest(count: swapped ? 0 : 8, time: elapsed)
        f.flip = 180 * p - (swapped ? 180 : 0)
        f.orders = mix(8, 5, out(segment(elapsed, 0, 0.7)))
        f.buttonGold = 1 - out(segment(elapsed, 0, 0.5)); f.dip = impulse(elapsed - 0.48, depth: 3)
        return f
    }
}
