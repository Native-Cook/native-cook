import Foundation

/// The voices, the loop and the curves of the voice picker. Times in seconds.
enum WatercolorVoiceOrbsModel {
    struct Voice {
        var name: String
        var line: String
        /// Deep, mid and light colours of the orb (0…1 RGB).
        var deep: SIMD3<Double>
        var mid: SIMD3<Double>
        var light: SIMD3<Double>
        /// The direction the orb's bright side faces (y down), where in the noise it sits, and how
        /// much its middle is twisted.
        var band: SIMD2<Double>
        var offset: SIMD2<Double>
        var swirl: Double
    }

    static let voices: [Voice] = [
        Voice(name: "Lagoon", line: "Calm and clear",
              deep: [0.063, 0.341, 0.890], mid: [0.267, 0.659, 0.969], light: [0.925, 0.976, 1.0],
              band: [0, -1], offset: [0, 0], swirl: 0),
        Voice(name: "Dune", line: "Warm and unhurried",
              deep: [0.878, 0.345, 0.212], mid: [0.973, 0.643, 0.400], light: [1.0, 0.953, 0.890],
              band: [0.7, 0.7], offset: [3.1, 1.7], swirl: 1.2),
        Voice(name: "Moss", line: "Bright and curious",
              deep: [0.039, 0.494, 0.400], mid: [0.341, 0.831, 0.627], light: [0.937, 1.0, 0.961],
              band: [-1, 0.2], offset: [-2.3, 4.2], swirl: -0.9),
        Voice(name: "Dusk", line: "Low and thoughtful",
              deep: [0.329, 0.220, 0.859], mid: [0.749, 0.475, 0.937], light: [1.0, 0.925, 0.984],
              band: [0.3, 1], offset: [5.2, -3.3], swirl: 1.6),
    ]

    static let loop = 10.0
    /// autoPlay steps: start, from, to, duration. The last one glides back to the first voice.
    static let steps: [(at: Double, from: Double, to: Double, dur: Double)] = [
        (1.4, 0, 1, 0.85), (3.6, 1, 2, 0.85), (5.8, 2, 3, 0.85), (7.8, 3, 0, 1.0),
    ]
    /// The orb breathes four times a loop.
    static let breath = 2.5
    /// The preview a voice "speaks" after it lands.
    static let speakLength = 1.1

    // MARK: Curves

    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(x, a), b) }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    static func easeInOut(_ x: Double) -> Double {
        let t = clamp(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        let t = clamp(x)
        return 1 - pow(1 - t, 3)
    }

    /// 1 → 1.02 → 1, four times a loop.
    static func breathing(_ clock: Double) -> Double {
        1 + 0.02 * (0.5 - 0.5 * cos(2 * .pi * clock / breath))
    }

    /// The orb's squash after a swipe starts (u seconds ago): it gives to about 0.94, then
    /// overshoots to about 1.02 and settles.
    static func squash(_ u: Double) -> Double {
        u <= 0 ? 1 : 1 - 0.11 * exp(-2.4 * u) * sin(7.5 * u)
    }

    /// A short spoken preview: four syllables over 1.1 s, 0…1.
    static func speak(_ u: Double) -> Double {
        if u <= 0 || u >= speakLength { return 0 }
        let syl: [(Double, Double, Double)] = [(0.15, 0.8, 0.08), (0.38, 1.0, 0.1), (0.62, 0.7, 0.08), (0.85, 0.9, 0.1)]
        var v = 0.0
        for (c, h, w) in syl {
            let d = (u - c) / w
            v += h * exp(-d * d)
        }
        return min(1, v)
    }

    // MARK: autoPlay

    struct Auto {
        var v: Double
        /// Seconds since the last swipe started, and since the last voice landed.
        var sinceSwipe: Double
        var sinceLanding: Double
    }

    static func auto(_ e: Double) -> Auto {
        var v = 0.0
        // Before the first step of a loop, the last step of the previous one is the latest.
        var last = steps[steps.count - 1]
        var lastAt = last.at - loop
        for s in steps where e >= s.at {
            last = s
            lastAt = s.at
        }
        let u = e - lastAt
        v = last.from + (last.to - last.from) * easeInOut(u / last.dur)
        return Auto(v: v, sinceSwipe: u, sinceLanding: u - last.dur)
    }

    // MARK: Blending two voices

    struct Blend {
        /// The two voices the picker is between and how far (0…1); the shader seeps one into the other.
        var a: Voice
        var b: Voice
        var f: Double
        /// The mid colour, mixed, for the glow.
        var mid: SIMD3<Double>
        var band: SIMD2<Double>
        var offset: SIMD2<Double>
        var swirl: Double
    }

    static func blend(_ v: Double) -> Blend {
        let x = clamp(v, 0, Double(voices.count - 1))
        let i = min(Int(floor(x)), voices.count - 2)
        let f = x - Double(i)
        let a = voices[i], b = voices[i + 1]
        func mix2(_ p: SIMD2<Double>, _ q: SIMD2<Double>) -> SIMD2<Double> { p + (q - p) * f }
        return Blend(a: a, b: b, f: f, mid: a.mid + (b.mid - a.mid) * smoothstep(0, 1, f),
                     band: mix2(a.band, b.band), offset: mix2(a.offset, b.offset), swirl: a.swirl + (b.swirl - a.swirl) * f)
    }
}
