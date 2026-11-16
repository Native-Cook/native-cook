import SwiftUI

struct HeatShapePickerPose {
    var p: Double, c: Double, k: Double, phi: Double, h: Double
}

struct HeatShapePickerLayer {
    var pose: HeatShapePickerPose
    var opacity: Double = 1
}

enum HeatShapePickerModel {
    static let names = ["Boost", "Warm", "Eco", "Breeze"]
    static let captions = ["Full power for ten minutes", "Steady 22° through the evening",
                           "Holds 18° and saves energy", "Fan only, no heat"]
    static let modes = [
        HeatShapePickerPose(p: 0.62, c: 0, k: 1.16, phi: 0, h: 0.50),
        HeatShapePickerPose(p: 2, c: 0, k: 0.96, phi: .pi / 4, h: 1.25),
        HeatShapePickerPose(p: 4.6, c: 0, k: 0.92, phi: .pi / 2, h: 2),
        HeatShapePickerPose(p: 2.6, c: 0.26, k: 1.04, phi: 3 * .pi / 4, h: 2.75),
        HeatShapePickerPose(p: 0.62, c: 0, k: 1.16, phi: .pi, h: 3.50),
    ]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ m: Double) -> Double { a + (b - a) * m }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func ease(_ x: Double) -> Double {
        let u = clamp(x); return u < 0.5 ? 4 * u * u * u : 1 - pow(2 - 2 * u, 3) / 2
    }
    static func out(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func back(_ x: Double) -> Double {
        let u = clamp(x) - 1; return 1 + 2.2 * u * u * u + 1.2 * u * u
    }
    static func spring(_ age: Double, response: Double, damping: Double) -> Double {
        guard age > 0 else { return 0 }
        let w = 2 * Double.pi / response, wd = w * sqrt(1 - damping * damping)
        let v = 1 - exp(-damping * w * age) * (cos(wd * age) + damping * w / wd * sin(wd * age))
        return mix(v, 1, smooth((age - 1.0) / 0.2))
    }
    static func interpolate(_ a: HeatShapePickerPose, _ b: HeatShapePickerPose, _ m: Double) -> HeatShapePickerPose {
        HeatShapePickerPose(p: exp(mix(log(a.p), log(b.p), m)), c: mix(a.c, b.c, m),
                            k: mix(a.k, b.k, m), phi: mix(a.phi, b.phi, m), h: mix(a.h, b.h, m))
    }

    /// The only outline function. A nil time disables wobble for glyphs and reduced motion.
    static func radius(_ theta: Double, _ p: Double, _ c: Double, _ k: Double,
                       _ phi: Double, _ t: Double?) -> Double {
        let a = theta - phi
        let ac = sqrt(pow(cos(a), 2) + 0.004), ass = sqrt(pow(sin(a), 2) + 0.004)
        let rp = pow(pow(ac, p) + pow(ass, p), -1 / p)
        let lobe = 1 - c * pow(1 - abs(sin(2 * a)), 2.2)
        let tau = 2 * Double.pi * (t ?? 0) / HeatShapePicker.loop
        let wob = t == nil ? 1 : 1 + 0.018 * sin(3 * theta + 2 * tau) + 0.012 * sin(5 * theta - 3 * tau + 1.7)
        return 110 * k * rp * lobe * wob
    }

    static let rampStops: [(Double, UInt32)] = [
        (0, 0x2B2BE8), (0.12, 0x14051F), (0.25, 0xE3210B), (0.38, 0xFF7A1A),
        (0.50, 0xFFD75A), (0.62, 0xFFFFFF), (0.78, 0xC9C4FF), (0.90, 0x6A63FF), (1, 0x2B2BE8),
    ]
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func ramp(_ value: Double, _ alpha: Double = 1) -> Color {
        let x = value - floor(value)
        let j = (0..<8).first { x < rampStops[$0 + 1].0 } ?? 7
        let (a, ca) = rampStops[j], (b, cb) = rampStops[j + 1], m = (x - a) / (b - a)
        func channel(_ shift: UInt32) -> Double { mix(Double((ca >> shift) & 255), Double((cb >> shift) & 255), m) / 255 }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: alpha)
    }
    static func gradient(_ h: Double) -> Gradient { Gradient(colors: [0.20, 0.10, 0, -0.10, -0.20].map { ramp(h + $0) }) }

    static func coalesced(_ layers: [HeatShapePickerLayer]) -> [HeatShapePickerLayer] {
        var result: [HeatShapePickerLayer] = []
        for layer in layers where layer.opacity > 0.001 {
            let p = layer.pose
            if let i = result.firstIndex(where: { item in
                let q = item.pose, dh = p.h - q.h
                return abs(p.p - q.p) + abs(p.c - q.c) + abs(p.k - q.k) < 0.00001
                    && abs(sin(2 * (p.phi - q.phi))) < 0.00001 && abs(dh - dh.rounded()) < 0.00001
            }) { result[i].opacity += layer.opacity }
            else { result.append(layer) }
        }
        return result
    }
}

struct HeatShapePickerFrame {
    typealias M = HeatShapePickerModel
    var pose = M.modes[0]
    var layers = [HeatShapePickerLayer(pose: M.modes[0])]
    var time = 0.0, morph = 0.0, glow = 1.0, ring = 0.0
    var selected = 0
    var labels = [1.0, 0, 0, 0], labelY = [0.0, 0, 0, 0], chips = [1.0, 0, 0, 0]

    static func automatic(_ time: Double, reduced: Bool) -> Self {
        let t = max(0, time).truncatingRemainder(dividingBy: HeatShapePicker.loop)
        let i = Int(t / 2), next = (i + 1) % 4, s = t - Double(i) * 2
        let u = M.seg(s, 1.1, 2), m = M.ease(u)
        var f = Self(); f.time = t; f.selected = s < 1.55 ? i : next
        f.ring = M.mix(Double(i), Double(next), reduced ? M.seg(s, 1.25, 1.75) : M.back(M.seg(s, 1.25, 1.75)))
        f.chips = Array(repeating: 0, count: 4)
        let chip = M.seg(s, 1.45, 1.65); f.chips[i] = 1 - chip; f.chips[next] = chip
        f.labels = Array(repeating: 0, count: 4); f.labelY = Array(repeating: 0, count: 4)
        if reduced {
            let fade = M.smooth(M.seg(s, 1.55, 1.85))
            f.pose = M.modes[f.selected]
            f.layers = [.init(pose: M.modes[i], opacity: 1 - fade), .init(pose: M.modes[next], opacity: fade)]
            f.labels[i] = 1 - fade; f.labels[next] = fade
            return f
        }
        f.pose = M.interpolate(M.modes[i], M.modes[i + 1], m)
        if s < 1.1 {
            f.pose.k *= 1 + 0.022 * sin(2 * .pi * s / 1.1)
            f.pose.h += 0.045 * sin(2 * .pi * s / 1.1)
        } else {
            let scale: Double
            if u < 0.28 { scale = M.mix(1, 0.90, M.smooth(u / 0.28)) }
            else if u < 0.72 { scale = M.mix(0.90, 1.07, M.smooth((u - 0.28) / 0.44)) }
            else { scale = M.mix(1.07, 1, M.smooth((u - 0.72) / 0.28)) }
            f.pose.k *= scale
        }
        f.layers = [.init(pose: f.pose)]; f.morph = m
        f.glow = s < 1.1 ? 1 + 0.08 * sin(.pi * s) : 1 + 0.5 * sin(.pi * m)
        let old = pow(M.seg(s, 1.35, 1.55), 3), new = M.out(M.seg(s, 1.55, 1.85))
        f.labels[i] = 1 - old; f.labelY[i] = -10 * old
        f.labels[next] = new; f.labelY[next] = 10 * (1 - new)
        return f
    }
}

struct HeatShapePickerTransition {
    typealias M = HeatShapePickerModel
    var source: HeatShapePickerFrame
    var target: Int
    var at: Double
    var settling = false

    func frame(_ now: Double, reduced: Bool) -> HeatShapePickerFrame {
        let age = max(0, now - at), fade = M.smooth(age / 0.3)
        var f = source, end = M.modes[target]
        if settling {
            // A released drag settles locally; it does not trigger another mode change.
            end.h += (source.pose.h - end.h).rounded()
            end.phi += ((source.pose.phi - end.phi) / (.pi / 2)).rounded() * (.pi / 2)
        } else {
            // Both light and orientation always travel forward for a mode selection.
            while end.h < source.pose.h { end.h += 1 }
            while end.phi < source.pose.phi { end.phi += .pi }
        }
        let m = reduced ? fade : M.spring(age, response: 0.55, damping: 0.68)
        f.selected = target; f.pose = M.interpolate(source.pose, end, m)
        f.time = M.mix(source.time, Double(target) * 2, M.smooth(age / 1.2))
        f.morph = reduced ? 0 : settling ? M.mix(source.morph, 0, fade) : M.clamp(m)
        f.glow = M.mix(source.glow, 1, fade) + (reduced || settling ? 0 : 0.5 * sin(.pi * f.morph))
        if reduced {
            f.pose = end
            f.layers = source.layers.map { .init(pose: $0.pose, opacity: $0.opacity * (1 - fade)) }
            f.layers.append(.init(pose: end, opacity: fade))
            f.layers = M.coalesced(f.layers)
        } else {
            if !settling { f.pose.k *= 1 + 0.8 * max(0, m - 1) }
            // One idle heat cycle ends at the two-second autoplay resume boundary.
            let heatTime = max(0, age - 0.9)
            f.pose.h += 0.045 * sin(2 * .pi * heatTime / 1.1)
            f.layers = [.init(pose: f.pose)]
        }
        let ring = reduced ? fade : M.spring(age, response: 0.4, damping: 0.72)
        f.ring = M.mix(source.ring, Double(target), ring)
        for i in 0..<4 {
            let to = i == target ? 1.0 : 0
            f.chips[i] = M.mix(source.chips[i], to, M.clamp(age / 0.2))
            f.labels[i] = M.mix(source.labels[i], to, fade)
            f.labelY[i] = reduced ? 0 : (i == target ? M.mix(source.labels[i] > 0 ? source.labelY[i] : 10, 0, M.out(age / 0.3)) : source.labelY[i] - 10 * fade)
        }
        return f
    }
}

/// An event-retargeted analytical spring; no position is integrated per frame.
struct HeatShapePickerSpring {
    var from = 0.0, target = 0.0, velocity = 0.0, at = 0.0
    var response = 0.5, damping = 0.5
    func state(_ now: Double) -> (Double, Double) {
        let age = max(0, now - at)
        if age >= 1.4 { return (target, 0) }
        let w = 2 * Double.pi / response, a = damping * w, b = w * sqrt(1 - damping * damping)
        let d = from - target, k = (velocity + a * d) / b, e = exp(-a * age)
        let tail = 1 - HeatShapePickerModel.smooth((age - 1.2) / 0.2)
        return (target + e * (d * cos(b * age) + k * sin(b * age)) * tail,
                e * ((k * b - a * d) * cos(b * age) + (-d * b - a * k) * sin(b * age)) * tail)
    }
    mutating func retarget(_ value: Double, _ now: Double, releasing: Bool = false) {
        let current = state(now); from = current.0; velocity = current.1; target = value; at = now
        response = releasing ? 0.5 : 0.35; damping = releasing ? 0.5 : 0.68
    }
}

struct HeatShapePickerLayout {
    let width: Double, height: Double, top: Double, bottom: Double
    var sx: Double { width / 393 }
    var sy: Double { height / 852 }
    var shift: Double { max(0, top - 59) - max(0, bottom - 34) }
    func x(_ value: Double) -> Double { value * sx }
    func y(_ value: Double) -> Double { value * sy + shift }
    var center: CGPoint { CGPoint(x: width / 2, y: y(372)) }
}
