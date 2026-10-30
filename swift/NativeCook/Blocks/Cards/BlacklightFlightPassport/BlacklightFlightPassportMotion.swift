import Foundation

// Analytic timeline: no platform springs are substituted for the reference easing functions.
enum BlacklightFlightPassportMotion {
    static let loop = 9.0
    static let resume = 2.0
    static let uvPeak = 5.2
    static let fade = (0.5, 0.85)
    static let draw = (0.9, 3.2)
    static let press = (3.55, 3.72)
    static let uvOn = (3.72, 4.55)
    static let glow = (4.4, 5.2)
    static let pressOff = (6.75, 6.9)
    static let uvOff = (6.9, 7.6)

    struct Frame {
        var t: Double
        var routes: [Double]
        var fade: Double
        var count: Double
        var press: Double
        var uv: Double
        var glow: Double
    }
    struct Driver {
        var t = 0.0
        var mode = 0
        var stop = 0.0
        var rate = 1.0
        var last = -10.0
        var amb = 0.0
        var tiltX = 0.0, tiltY = 0.0
        var torch = 0.0
        var torchAim = 0.0
        var tx = 200.0
        var ty = 400.0
        var pressAim = 0.0
        var pressP = 0.0
    }
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 5) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func easeInOut(_ x: Double) -> Double {
        let p = clamp(x)
        return p < 0.5 ? 16 * pow(p, 5) : 1 - pow(-2 * p + 2, 5) / 2
    }
    static func smooth(_ x: Double) -> Double { let p = clamp(x); return p * p * (3 - 2 * p) }
    static func routeAt(_ route: Int, _ t: Double) -> Double {
        if t < fade.0 || t >= draw.1 + 0.2 { return 1 }
        if t < draw.0 { return 1 }
        let start = draw.0 + Double(route) / 17 * (draw.1 - draw.0 - 0.55)
        return easeInOut(seg(t, start, start + 0.55))
    }
    static func routeFade(_ t: Double) -> Double {
        t >= fade.0 && t < draw.0 ? 1 - smooth(seg(t, fade.0, fade.1)) : 1
    }
    static func frameAt(_ t: Double) -> Frame {
        let counting = t >= fade.0 && t < draw.1 + 0.3
        let count = counting ? (t < draw.0 ? 1 - smooth(seg(t, fade.0, fade.1)) : easeOutCubic(seg(t, draw.0, draw.1))) : 1
        let pOn = seg(t, press.0, press.1), pOff = seg(t, pressOff.0, pressOff.1)
        let pressed = pOn > 0 && pOn < 1 ? sin(.pi * pOn) : pOff > 0 && pOff < 1 ? sin(.pi * pOff) : 0
        let uv = t < uvOff.0 ? easeInOut(seg(t, uvOn.0, uvOn.1)) : 1 - easeInOut(seg(t, uvOff.0, uvOff.1))
        let ink = t < uvOff.0 ? smooth(seg(t, glow.0, glow.1)) : 1 - smooth(seg(t, uvOff.0, uvOff.0 + 0.3))
        return Frame(t: t, routes: (0..<18).map { routeAt($0, t) }, fade: routeFade(t), count: count, press: pressed, uv: uv, glow: ink)
    }
    static func follow(_ current: Double, _ aim: Double, _ k: Double, _ dt: Double) -> Double {
        current + (aim - current) * (1 - exp(-dt * k))
    }
    static func step(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
        if d.mode == 1 {
            d.t += dt * d.rate
            if d.t >= d.stop {
                d.t = d.stop >= loop ? 0 : d.stop
                d.mode = 2; d.rate = 1; d.last = now
            }
        } else if d.mode == 2 {
            if auto && d.torchAim == 0 && now - d.last >= resume { d.mode = 0 }
        } else if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: loop) }
        d.amb = (d.amb + dt).truncatingRemainder(dividingBy: loop)
        d.torch = follow(d.torch, d.torchAim, d.torchAim > 0 ? 14 : 9, dt)
        d.pressP = follow(d.pressP, d.pressAim, d.pressAim > 0 ? 40 : 16, dt)
    }
    static func isUv(_ t: Double) -> Bool { t >= uvOn.0 + 0.3 && t < uvOff.0 }
    static func touched(_ d: inout Driver, _ now: Double) {
        d.last = now
        guard d.mode == 0 else { return }
        d.rate = 1
        if d.t >= press.0 && d.t < uvPeak { d.mode = 1; d.stop = uvPeak }
        else if d.t >= pressOff.0 && d.t < uvOff.1 { d.mode = 1; d.stop = loop }
        else { d.mode = 2 }
    }
    @discardableResult static func toggle(_ d: inout Driver) -> Bool {
        if isUv(d.t) || (d.t >= uvPeak && d.t < uvOff.0) {
            d.t = pressOff.0; d.stop = loop; d.rate = 1.25
        } else {
            d.t = d.t >= draw.1 + 0.3 || d.t < fade.0 ? press.0 : d.t
            d.stop = uvPeak; d.rate = 1.25
        }
        d.mode = 1
        return isUv(d.stop - 0.01)
    }
}
