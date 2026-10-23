import Foundation

struct GlassBladeDrawFrame {
    var pos = 0.0
    var charge = 1.0
    var flare = 0.0
    var press = 0.0
    var ctaEnergy = 1.0
    var intro = 1.0
    var still = 0.0
}

/// Pure functions shared by the automatic loop and a finger-triggered draw.
enum GlassBladeDrawTimeline {
    static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        max(low, min(high, x))
    }

    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<24 {
            let u = (lo + hi) / 2
            let v = 1 - u
            let at = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if at < x { lo = u } else { hi = u }
        }
        let u = (lo + hi) / 2
        let v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }

    static func entrance(_ t: Double) -> Double { bezier(t, 0.23, 1, 0.32, 1) }
    static func move(_ t: Double) -> Double { bezier(t, 0.62, 0, 0.3, 1) }
    static func pourEase(_ t: Double) -> Double { bezier(t, 0.3, 0.7, 0.4, 1) }
    static func drawScale(_ n: Double) -> Double { sqrt(max(0.01, abs(n)) / 4) }
    static func pourStart(_ n: Double) -> Double { 0.8 * 1.2 * drawScale(n) }

    static func spinOffset(_ tau: Double, _ n: Double) -> Double {
        if tau <= 0 { return 0 }
        let t = tau / (1.2 * drawScale(n))
        if t >= 1 { return n }
        let left = 1 - t
        let bump = sin(.pi * clamp((t - 0.62) / 0.38))
        return n * (1 - left * left * left) + (n < 0 ? -1 : 1) * 0.07 * bump * bump
    }

    // Solved once; exactly the same 24-iteration inversion as the RN timeline.
    static let flareFraction: Double = {
        let level = (1.0 - 0.2) / (1.15 - 0.2)
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if pourEase(mid) < level { lo = mid } else { hi = mid }
        }
        return (lo + hi) / 2
    }()

    static func flareStart(_ n: Double) -> Double { pourStart(n) + 0.7 * flareFraction }
    static func duration(_ n: Double) -> Double { max(pourStart(n) + 0.7 + 0.38, flareStart(n) + 0.74) }

    static func releasedPress(_ tau: Double, from: Double = 1) -> Double {
        if tau <= 0 { return from }
        if tau >= 0.8 { return 0 }
        let w = sqrt(380.0 - 13 * 13)
        return from * exp(-13 * tau) * (cos(w * tau) + 13 / w * sin(w * tau))
    }

    static func draw(_ tau: Double, _ n: Double, gain: Double = 1,
                     initial: GlassBladeDrawFrame = GlassBladeDrawFrame(press: 1)) -> GlassBladeDrawFrame {
        let t = max(0, tau)
        if t >= duration(n) { return GlassBladeDrawFrame(pos: n) }
        let pour = pourStart(n)
        let spark = flareStart(n)
        let speed = abs(spinOffset(t + 0.008, n) - spinOffset(t - 0.008, n)) / 0.016
        let spinning = 0.2 + 0.5 * clamp((speed - 0.5) / 8)
        var charge = initial.charge + (spinning - initial.charge) * entrance(t / 0.22)
        if t >= pour { charge = 0.2 + 0.95 * pourEase((t - pour) / 0.7) }
        if t >= pour + 0.7 { charge = 1.15 - 0.15 * entrance((t - pour - 0.7) / 0.38) }
        var flare = initial.flare * (1 - entrance(t / 0.22))
        var energy = initial.ctaEnergy + (0.12 - initial.ctaEnergy) * entrance(t / 0.28)
        if t >= spark {
            flare = gain * entrance((t - spark) / 0.09)
            if t >= spark + 0.09 { flare = gain * (1 - entrance((t - spark - 0.09) / 0.65)) }
            energy = 0.12 + 0.88 * entrance((t - spark) / 0.52)
        }
        return GlassBladeDrawFrame(pos: spinOffset(t, n), charge: charge, flare: flare,
                                   press: releasedPress(t, from: initial.press), ctaEnergy: energy)
    }

    static func loop(_ time: Double, base: Double = 0) -> GlassBladeDrawFrame {
        let cycles = floor(max(0, time) / 10)
        let t = max(0, time) - cycles * 10
        let origin = base + cycles * 9
        if t < 3.44 {
            var state = GlassBladeDrawFrame(pos: origin)
            if t >= 1 { state.pos += move((t - 1) / 0.7) }
            if t >= 2.25 { state.pos += move((t - 2.25) / 0.7) }
            if t >= 3.3 { state.press = entrance((t - 3.3) / 0.11) }
            return state
        }
        if t < 5.74 {
            var state = draw(t - 3.44, 4)
            state.pos += origin + 2
            if t >= 5.6 { state.press = entrance((t - 5.6) / 0.11) }
            return state
        }
        var state = draw(t - 5.74, 3, gain: 0.55)
        state.pos += origin + 6
        return state
    }
}
