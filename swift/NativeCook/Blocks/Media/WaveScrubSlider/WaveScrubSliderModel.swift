import Foundation

/// The numbers of the Wave Scrub Slider: the autoPlay script, the wave's shape and the photo's grade.
enum WaveScrubSliderModel {
    /// The self-playing loop, in seconds.
    static let loop = 8.0
    /// How many times a second the wave's crests travel to the right (a whole number per loop).
    static let drift = 1.0

    /// The scripted finger: (time, value) keys, eased in and out between them.
    static let keys: [(Double, Double)] = [
        (0.0, 0), (0.6, 0), (2.2, 0.62), (2.9, 0.62), (3.9, 1), (5.0, 1), (6.6, 0), (8.0, 0),
    ]

    static func scripted(_ t: Double) -> Double {
        for i in 1..<keys.count where t <= keys[i].0 {
            let (t0, v0) = keys[i - 1], (t1, v1) = keys[i]
            let u = easeInOut((t - t0) / (t1 - t0))
            return v0 + (v1 - v0) * u
        }
        return 0
    }

    static func easeInOut(_ x: Double) -> Double {
        let u = min(max(x, 0), 1)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = min(max((x - a) / (b - a), 0), 1)
        return u * u * (3 - 2 * u)
    }

    /// Cycles of the wave from the left end of the track to u (0…1): the waves tighten to the right,
    /// and tighten further as the value rises.
    static func cycles(_ u: Double, value v: Double) -> Double {
        (0.6 + 0.4 * v) * (2.0 * u + 6.5 * u * u)
    }

    /// The wave's height above the centre line at u, for a thumb at v and a drift phase (in cycles).
    /// Left of the thumb (the active, orange part) it grows with the value up to 15 pt; right of it
    /// (the grey part) it is a faint ripple of 1 to 6 pt. It meets the line at the thumb and at both ends.
    static func height(_ u: Double, value v: Double, phase: Double, width: Double) -> Double {
        let x = u * width, thumb = v * width
        let amp: Double
        if x <= thumb {
            amp = 15 * v * smoothstep(0, 0.3, u)
        } else {
            amp = 1.2 + 5 * v
        }
        let taper = smoothstep(3, 18, abs(x - thumb)) * smoothstep(0, 10, width - x) * smoothstep(0, 6, x)
        return amp * taper * sin(2 * .pi * (cycles(u, value: v) - phase))
    }

    /// The photo's grade for a value: contrast 1 → 1.45 around mid grey, saturation 1 → 1.3.
    /// A 4 × 5 colour matrix, row by row; the last column is an offset in 0…1.
    static func grade(_ v: Double) -> [Double] {
        let c = 1 + 0.45 * v, s = 1 + 0.3 * v
        let lr = 0.2126 * (1 - s), lg = 0.7152 * (1 - s), lb = 0.0722 * (1 - s)
        let o = 0.5 * (1 - c)
        return [
            c * (lr + s), c * lg, c * lb, 0, o,
            c * lr, c * (lg + s), c * lb, 0, o,
            c * lr, c * lg, c * (lb + s), 0, o,
            0, 0, 0, 1, 0,
        ]
    }

    /// The value as the panel reads it: 0, then +1 … +100.
    static func label(_ v: Double) -> String {
        let n = Int((v * 100).rounded())
        return n == 0 ? "0" : "+\(n)"
    }
}
