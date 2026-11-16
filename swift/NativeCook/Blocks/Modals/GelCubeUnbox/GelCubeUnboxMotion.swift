import Foundation

/// gcu-motion.ts, in reference points, seconds and radians. No accumulated autoplay state.
enum GelCubeUnboxMotion {
    static let loop = 7.0
    static let gather = 0.8, lift = 1.1, gasp = 1.6, open = 2.7, holdEnd = 5.2, closeEnd = 6.3
    static let drag = 150.0, seal = 6.0, resistance = 0.35, projection = 0.15, threshold = 0.45
    static let flick = 900.0, angle = 58.0, overshoot = 66.0 / 58.0, rubberLimit = 120.0
    static let resume = 2.0, reset = 0.6, causticLag = 0.05, fanLead = 0.04
    static let flashDuration = 2.0 / 60.0, flashGain = 0.2, fade = 0.2, pressCore = 1.25
    static let contact = 0.004, hairline = 0.03, cueInterval = 0.06, callbackInterval = 0.1

    struct GelCubeUnboxSpring {
        var damping: Double, stiffness: Double, mass: Double
    }
    static let sealSpring = GelCubeUnboxSpring(damping: 18, stiffness: 380, mass: 0.8)
    static let lidSpring = GelCubeUnboxSpring(damping: 14, stiffness: 120, mass: 1.4)
    static let flickSpring = GelCubeUnboxSpring(damping: 10, stiffness: 120, mass: 1.4)
    static let closeSpring = GelCubeUnboxSpring(damping: 16, stiffness: 150, mass: 1.4)
    static let glideSpring = GelCubeUnboxSpring(damping: 22.1, stiffness: 157.9, mass: 1)
    static let coreSpring = GelCubeUnboxSpring(damping: 30.5, stiffness: 322.3, mass: 1)

    static func clamp01(_ x: Double) -> Double { max(0, min(1, x)) }
    static func wrap(_ time: Double) -> Double {
        let t = time.truncatingRemainder(dividingBy: loop)
        return (t + loop).truncatingRemainder(dividingBy: loop)
    }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x - a) / max(0.00001, b - a))
        return u * u * (3 - 2 * u)
    }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(max(0, 1 - zeta * zeta))
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func rubber(_ x: Double) -> Double {
        if x < 0 { return -((-x * drag) / (1 + (-x * drag) / rubberLimit)) / drag }
        if x > 1 { return 1 + (x - 1) * drag / (1 + (x - 1) * drag / rubberLimit) / drag }
        return x
    }
    static func progressAt(_ time: Double) -> Double {
        let t = wrap(time)
        if t < lift { return 0.016 * sin(.pi * smooth(gather, 1.05, t)) * (1 - smooth(1.02, lift, t)) }
        if t < gasp { return 0.14 * smooth(lift, gasp, t) }
        if t < 2.35 { return 0.14 + 0.64 * smooth(gasp, 2.35, t) }
        if t < open { return 0.78 + 0.22 * springAt((t - 2.35) * 2, 0.65, 0.78) }
        if t < holdEnd { return 1 }
        if t < closeEnd { return 1 - smooth(holdEnd, closeEnd, t) }
        return 0
    }
    static func coreAt(_ time: Double) -> Double {
        let t = wrap(time)
        return 1 + 0.03 * (1 - cos(4 * .pi * t / loop)) + 0.19 * smooth(gather, 1.05, t) * (1 - smooth(1.1, 1.65, t))
    }
    static func fanAt(_ time: Double) -> Double {
        let t = wrap(time)
        return progressAt(t >= holdEnd ? min(closeEnd, t + fanLead) : t)
    }
    static func flashAt(_ time: Double) -> Double {
        let t = wrap(time)
        return t >= closeEnd - flashDuration && t < closeEnd ? flashGain : 0
    }
    static func contentAt(_ p: Double) -> Double { smooth(0.8, 0.98, p) }
    static func lightAt(_ p: Double, _ threshold: Double) -> Double { smooth(threshold - 0.04, threshold + 0.04, p) }
    static func angleAt(_ p: Double) -> Double { max(-0.02, min(overshoot, p)) * angle * .pi / 180 }
    static func timing(_ x: Double) -> Double {
        let t = clamp01(x)
        return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    struct GelCubeUnboxScene {
        var progress = 0.0, core = 1.0, fan = 0.0, caustic = 1.0, angle = 0.0, flash = 0.0
        var content = 0.0, textProgress = 0.0, actionScale = 1.0
        static func film(_ time: Double, reduced: Bool = false) -> Self {
            let p = progressAt(time)
            return Self(progress: reduced ? 0 : p, core: reduced ? 1 + clamp01(p) * 0.25 : coreAt(time),
                        fan: reduced ? 0 : fanAt(time), caustic: coreAt(time - causticLag),
                        angle: reduced ? 0 : angleAt(p), flash: reduced ? 0 : flashAt(time),
                        content: reduced ? clamp01(p) : contentAt(p), textProgress: p)
        }
    }
}
