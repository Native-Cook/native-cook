import Foundation

/*
 The tape's value (cm under the red hairline) and its wobble.

 autoPlay (loop = 8 s). The value is 178 plus a sum of spring steps, each a closed-form damped
 response that starts at rest, so the value and its speed are pure functions of t:
   0.0 – 1.3   rest at 178 cm.
   1.3 – 1.42  anticipation: the tape slips back 0.5 cm.
   1.42 – 3.8  pulled out to 192 (ω 6 rad/s, ζ 0.72: ~3 % overshoot), wobbling while it moves.
   3.8 – 5.4   let go: it retracts to 165 with a whip (ω 11, ζ 0.6: ~2 cm overshoot, the free end
               waves hardest right after the stop).
   6.6 – 7.6   pulled back to 178 (ω 7.5, ζ 0.8).
   7.6 – 8.0   rest (the last residual is faded out so t = 8 is exactly t = 0).

 Wobble: a travelling wave along the tape (3 Hz, a whole number of waves per loop), its height
 growing toward the free end; its amplitude follows the tape's recent speed (a short decaying
 memory, so a sudden stop keeps the tape waving for ~0.3 s).
 */
enum TapeMeasurePickerMotion {
    static let loop = 8.0
    static let rest = 178.0
    static let minValue = 120.0
    static let maxValue = 230.0
    /// Points per centimetre on screen.
    static let ppcm = 20.0
    /// Wave frequency (Hz) and wavelength (pt).
    static let waveHz = 3.0
    static let waveLength = 170.0
    static let waveMax = 6.0

    /// (start, change, ω, ζ)
    private static let steps: [(Double, Double, Double, Double)] = [
        (1.3, -0.5, 18, 0.9),
        (1.42, 14.5, 6, 0.72),
        (3.8, -27, 11, 0.6),
        (6.6, 13, 7.5, 0.8),
    ]

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Step response of a spring released from rest: (position 0 → 1, speed).
    static func springStep(_ tau: Double, _ w: Double, _ z: Double) -> (Double, Double) {
        if tau <= 0 { return (0, 0) }
        let wd = w * (1 - z * z).squareRoot()
        let e = exp(-z * w * tau)
        let c = cos(wd * tau)
        let s = sin(wd * tau)
        return (1 - e * (c + z * w / wd * s), e * (w * w / wd) * s)
    }

    /// The loop's value and speed (cm, cm/s) at loop time t.
    static func loopValue(_ t: Double) -> (Double, Double) {
        var v = rest
        var speed = 0.0
        for st in steps {
            let r = springStep(t - st.0, st.2, st.3)
            v += st.1 * r.0
            speed += st.1 * r.1
        }
        let fade = 1 - seg(t, 7.6, 7.95)
        return (rest + (v - rest) * fade, speed * fade)
    }

    static func wobble(speed: Double) -> Double {
        waveMax * (1 - exp(-speed / 70))
    }

    /// The wobble's amplitude: the strongest recent speed, remembered with a 0.3 s decay.
    static func loopWobble(_ t: Double) -> Double {
        var s = 0.0
        for j in 0..<7 {
            let tau = Double(j) * 0.06
            let sp = abs(loopValue(max(0, t - tau)).1) * exp(-tau / 0.3)
            if sp > s { s = sp }
        }
        return wobble(speed: s)
    }

    /// Rubber band past the ends of the scale (cm).
    static func band(_ v: Double) -> Double {
        if v > maxValue {
            let o = v - maxValue
            return maxValue + o / (1 + o / 8)
        }
        if v < minValue {
            let o = minValue - v
            return minValue - o / (1 + o / 8)
        }
        return v
    }

    /// The inverse of `band`, so a drag that starts past an end keeps its place.
    static func unband(_ v: Double) -> Double {
        if v > maxValue {
            let o = min(v - maxValue, 7.9)
            return maxValue + o / (1 - o / 8)
        }
        if v < minValue {
            let o = min(minValue - v, 7.9)
            return minValue - o / (1 - o / 8)
        }
        return v
    }

    /// One centimetre of height in feet and inches, e.g. "5 ft 10 in".
    static func feetInches(_ cm: Int) -> String {
        let inches = Int((Double(cm) / 2.54).rounded())
        return "\(inches / 12) ft \(inches % 12) in"
    }
}
