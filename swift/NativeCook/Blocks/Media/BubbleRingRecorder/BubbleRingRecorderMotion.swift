import Foundation

/// Everything that moves in the recorder is a function of a few times, so the loop and a finger
/// share the same drawing. All times in seconds.
enum BubbleRingRecorderMotion {
    /// autoPlay: tap at 1.2 s, record for 5.2 s, stop at 6.4 s, settled by 7.4 s, rest to 9 s.
    static let loop = 9.0
    static let recordAt = 1.2
    static let take = 5.2
    static let stopAt = recordAt + take
    /// The orb's glow breathes four times a loop.
    static let breath = 2.25
    /// Collapse of the orb into the dot, and the ring's arrival.
    static let collapse = 0.5
    static let ringIn = 0.45
    static let ringOut = 0.6
    /// The red dot while recording.
    static let dotRadius = 9.0
    /// The grey ghost of the outline trails the white one by this much.
    static let ghostLag = 0.22

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

    /// A spring from 0 to 1 (stiffness 170, damping 16, mass 1): about 8 % overshoot, settled in 0.8 s.
    static func spring(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        let w0 = sqrt(170.0)
        let zeta = 16 / (2 * w0)
        let wd = w0 * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
    }

    /// The jelly kick the ring gets when a take stops: up, down, gone in about a second.
    static func kick(_ u: Double) -> Double {
        u <= 0 ? 0 : exp(-4.5 * u) * sin(11 * u)
    }

    /// 0…1, four times a loop; 0 at the loop's start.
    static func breathing(_ clock: Double) -> Double {
        0.5 - 0.5 * cos(2 * .pi * clock / breath)
    }

    // MARK: The voice

    /// Syllables of the scripted voice: centre, height, width (s).
    static let syllables: [(Double, Double, Double)] = [
        (0.75, 0.55, 0.16), (1.05, 0.85, 0.2), (1.5, 0.6, 0.14), (1.95, 1.0, 0.22), (2.35, 0.7, 0.16),
        (3.0, 0.5, 0.14), (3.3, 0.9, 0.2), (3.75, 0.75, 0.18), (4.3, 0.6, 0.16),
    ]

    /// The input level s seconds into a take, 0…1. It repeats every `take` seconds, so a longer
    /// take made by hand keeps talking; it is at its quiet floor at both ends of the pattern.
    static func level(_ s: Double) -> Double {
        if s < 0 { return 0 }
        let p = s.truncatingRemainder(dividingBy: take)
        var v = 0.12
        for (c, h, w) in syllables {
            let d = (p - c) / w
            v += h * exp(-d * d)
        }
        // A fast tremble so the voice never sounds like a sine.
        v *= 1 + 0.12 * sin(37 * s) + 0.08 * sin(23 * s + 1)
        return clamp(v) * smoothstep(0.1, 0.5, s)
    }

    /// The ring's shape at angle θ: a few low harmonics drifting at their own speeds, -1…1.
    static func shape(_ theta: Double, _ s: Double) -> Double {
        0.5 * sin(2 * theta + 1.7 * s + 0.4) + 0.3 * sin(3 * theta - 2.3 * s + 1.1)
            + 0.2 * sin(5 * theta + 3.1 * s + 2.0) + 0.12 * sin(7 * theta - 4.2 * s)
    }

    // MARK: State at a moment

    /// When the take being shown started and stopped, relative to now (seconds ago); nil when not.
    struct Clock {
        var sinceStart: Double?
        var sinceStop: Double?
        /// The length of the last finished take, for the list row.
        var lastTake: Double
        /// For the breathing glow.
        var clock: Double
    }

    struct Frame {
        /// The orb's radius and how much of it is still the big orb (1) or the dot (0).
        var orbRadius: Double
        var orbness: Double
        /// The ring: how present (0 faint circle, 1 white line), its radius scale, wobble amplitude
        /// and the time its shape is read at.
        var presence: Double
        var ringScale: Double
        var wobble: Double
        var ghostWobble: Double
        var shapeTime: Double
        var level: Double
        var glow: Double
        /// Ripple from a tap: progress 0…1 (nil when none) and whether it is the stop's.
        var ripple: Double?
        var rippleIsStop: Bool
        /// Seconds shown on the timer, and whether it records.
        var seconds: Double
        var recording: Double
    }

    /// The radius of the orb a recording started s seconds ago, before it stops.
    static func recordingOrb(_ s: Double, R: Double, reduced: Bool) -> Double {
        if reduced { return dotRadius }
        let c = easeInOut(s / collapse)
        let dot = dotRadius * (1 + 0.35 * level(s))
        return R + (dot - R) * c
    }

    static func frame(_ k: Clock, R: Double, reduced: Bool) -> Frame {
        let b = reduced ? 0 : breathing(k.clock)
        var f = Frame(orbRadius: R * (1 + 0.012 * b), orbness: 1, presence: 0, ringScale: 1, wobble: 0, ghostWobble: 0,
                      shapeTime: 0, level: 0, glow: b, ripple: nil, rippleIsStop: false, seconds: 0, recording: 0)
        guard let s = k.sinceStart else { return f }
        if let u = k.sinceStop {
            // Stopping: the orb springs back out of the dot, the ring jiggles and fades to the faint circle.
            let len = s - u
            let from = recordingOrb(len, R: R, reduced: reduced)
            let presAtStop = reduced ? 1 : easeInOut(len / ringIn)
            if reduced {
                let x = clamp(u / 0.25)
                f.orbRadius = R
                f.orbness = x
                f.presence = presAtStop * (1 - x)
                return f
            }
            f.orbRadius = from + (R * (1 + 0.012 * b) - from) * spring(u)
            f.orbness = smoothstep(dotRadius * 2, R, f.orbRadius)
            f.presence = presAtStop * (1 - easeInOut(u / ringOut))
            f.ringScale = 1 - 0.07 * kick(u)
            f.wobble = 0.08 * kick(u)
            f.ghostWobble = 0.08 * kick(u - ghostLag)
            f.shapeTime = len
            f.glow = b * smoothstep(0.3, 1.0, u)
            f.ripple = u < 0.6 ? u / 0.6 : nil
            f.rippleIsStop = true
            f.seconds = 0
            return f
        }
        // Recording.
        let lv = level(s)
        f.level = lv
        f.recording = 1
        f.seconds = s
        f.orbRadius = recordingOrb(s, R: R, reduced: reduced)
        f.orbness = reduced ? 0 : smoothstep(dotRadius * 2, R, f.orbRadius)
        f.presence = reduced ? 1 : easeInOut(s / ringIn)
        if !reduced {
            f.ringScale = 1 + 0.05 * lv
            f.wobble = 0.14 * lv * f.presence
            f.ghostWobble = 0.14 * level(s - ghostLag) * f.presence
            f.shapeTime = s
            f.ripple = s < 0.7 ? s / 0.7 : nil
        }
        f.glow = 0
        return f
    }

    /// Clock for autoPlay at loop time e of a loop that started `cycles` loops after the resume.
    static func autoClock(_ e: Double, clock: Double, lastTake: Double, afterFirstStop: Bool) -> Clock {
        if e >= stopAt {
            return Clock(sinceStart: e - recordAt, sinceStop: e - stopAt, lastTake: take, clock: clock)
        }
        if e >= recordAt {
            return Clock(sinceStart: e - recordAt, sinceStop: nil, lastTake: afterFirstStop ? take : lastTake, clock: clock)
        }
        return Clock(sinceStart: nil, sinceStop: nil, lastTake: afterFirstStop ? take : lastTake, clock: clock)
    }

    /// The take's waveform for the list row: n bars spread over 5.2 s, filled left to right while
    /// a take of `length` seconds grows (a longer one scrolls); bars not recorded yet are nil.
    static func bars(_ n: Int, length: Double) -> [Double?] {
        let from = max(0, length - take)
        return (0..<n).map { i in
            let t = from + Double(i) / Double(n - 1) * take
            return t <= length + 0.0001 ? level(t) : nil
        }
    }
}
