import Foundation

/// The particles of the listening ring. Every value is a function of the loop time and a few
/// seeded numbers per particle, so the loop is seamless and the stacks draw the same ring.
enum ParticleListeningRingMotion {
    static let loop = 6.0
    /// Particles on the ring, and particles that fall into the middle when the voice is loud.
    static let ringCount = 96
    static let innerCount = 16
    static var count: Int { ringCount + innerCount }

    /// Palette: coral, violet, teal, sun.
    static let colors: [(Double, Double, Double)] = [
        (1.0, 0.420, 0.341), (0.424, 0.298, 0.961), (0.098, 0.702, 0.604), (1.0, 0.690, 0.125),
    ]

    /// Three swells of the scripted voice: centre (s), width (s), height, and how much each colour
    /// leads it (violet, then teal with sun, then coral).
    static let bursts: [(c: Double, w: Double, h: Double, mix: [Double])] = [
        (1.1, 0.42, 0.9, [0.1, 1.0, 0.15, 0.1]),
        (2.45, 0.5, 1.0, [0.1, 0.1, 1.0, 0.8]),
        (3.85, 0.52, 0.9, [1.0, 0.15, 0.1, 0.2]),
    ]

    // MARK: Helpers

    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(x, a), b) }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    static func easeOut(_ x: Double) -> Double {
        let t = clamp(x)
        return 1 - pow(1 - t, 3)
    }

    /// A seeded number in 0..<1 for particle i and channel k (the same in every stack).
    static func rnd(_ i: Int, _ k: Int) -> Double {
        let v = sin(Double(i) * 12.9898 + Double(k) * 78.233) * 43758.5453
        return v - floor(v)
    }

    /// 2π · cycles · t / loop: a phase that comes back to itself at the end of the loop.
    static func wave(_ cycles: Double, _ t: Double) -> Double { 2 * .pi * cycles * t / loop }

    // MARK: The voice

    /// The voice's envelope: silent at both ends of the loop.
    static func window(_ t: Double) -> Double {
        smoothstep(0.3, 0.8, t) * (1 - smoothstep(4.7, 5.4, t))
    }

    /// A quick flutter on the level, so the swells sound like a voice.
    static func flutter(_ t: Double) -> Double {
        1 + 0.18 * sin(wave(11, t)) + 0.1 * sin(wave(17, t) + 1)
    }

    static func bump(_ b: Int, _ t: Double) -> Double {
        let d = (t - bursts[b].c) / bursts[b].w
        return bursts[b].h * exp(-d * d)
    }

    /// The scripted level at loop time t, 0…~1.
    static func level(_ t: Double) -> Double {
        var a = 0.0
        for b in 0..<bursts.count { a += bump(b, t) }
        return a * window(t) * flutter(t)
    }

    /// How much a particle of colour c grows from the scripted voice at loop time t.
    static func growth(_ c: Int, _ t: Double) -> Double {
        var g = 0.0
        for b in 0..<bursts.count { g += bump(b, t) * (0.25 + 0.75 * bursts[b].mix[c]) }
        return g * window(t) * flutter(t)
    }

    /// Weights of the four colours for a finger at angle a: the colour of that quarter leads.
    static func touchMix(_ a: Double, _ c: Int) -> Double {
        let quarter = (a / (2 * .pi) + 1).truncatingRemainder(dividingBy: 1) * 4
        var d = abs(quarter - (Double(c) + 0.5))
        d = min(d, 4 - d)
        return clamp(1.2 - d)
    }

    // MARK: A particle

    struct Dot {
        var x: Double
        var y: Double
        var radius: Double
        var alpha: Double
        var color: Int
    }

    /// Everything that drives the ring at a moment: the loop time, the script's gain (0 while a
    /// finger has taken over), and the finger's own level, angle and push.
    struct Drive {
        var t: Double
        var gain: Double
        var touch: Double
        var touchAngle: Double
    }

    static func dot(_ i: Int, drive d: Drive, center: (Double, Double), R: Double) -> Dot {
        let t = d.t
        let inner = i >= ringCount
        let theta = inner ? 2 * .pi * rnd(i, 1) : 2 * .pi * (Double(i) + 0.6 * (rnd(i, 1) - 0.5)) / Double(ringCount)
        let c = Int(rnd(i, 2) * 4) % 4
        let restSize = 1.0 + 1.4 * rnd(i, 3)
        let bigSize = 2.6 + 4.6 * pow(rnd(i, 4), 1.6)
        let k = 2 + Double(Int(rnd(i, 5) * 4))
        let phi = 2 * .pi * rnd(i, 6)
        let j = rnd(i, 7) - 0.5
        let j2 = rnd(i, 8) * 2 - 1

        let a = d.gain * level(t) + d.touch
        let swell = 1 + 0.12 * min(a, 1.2)
        let n = 0.6 * sin(3 * theta + wave(2, t) + 0.3) + 0.4 * sin(5 * theta - wave(3, t) + 1.7)
        let angle = theta + 0.18 * sin(wave(1, t)) + 0.05 * sin(wave(k, t) + phi)

        // The finger pushes the dots near it outward.
        var da = angle - d.touchAngle
        da = atan2(sin(da), cos(da))
        let push = d.touch * exp(-(da / 0.6) * (da / 0.6))

        var r = R * (1 + 0.03 * j) * swell + R * min(a, 1.2) * (0.16 * n + 0.12 * j2) + R * 0.22 * push
        if inner {
            let fall = smoothstep(0.2, 0.8, a) * (0.75 - 0.5 * rnd(i, 9))
            r *= 1 - fall
        }
        let pulse = 0.75 + 0.25 * sin(wave(k, t) + phi)
        let grow = d.gain * growth(c, t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c))
        let size = restSize * (1 - 0.35 * min(a, 1)) * (1 + 0.15 * sin(wave(k, t) + phi)) + grow * bigSize * pulse

        // In the quiet dips of the voice, half the ring empties, the empty half turning once a loop.
        let quiet = d.gain * window(t) * (1 - smoothstep(0.15, 0.55, level(t)))
        let sector = smoothstep(-0.1, 0.5, sin(angle - wave(1, t) - 0.6))
        let alpha = 1 - 0.9 * quiet * sector

        return Dot(x: center.0 + r * cos(angle), y: center.1 + r * sin(angle), radius: size * (0.2 + 0.8 * alpha), alpha: alpha, color: c)
    }

    /// The colour that leads at a moment, as weights of the four (for the glow behind the ring).
    static func lead(_ d: Drive, _ c: Int) -> Double {
        d.gain * growth(c, d.t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c))
    }
}
