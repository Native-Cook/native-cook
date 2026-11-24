// Nebula Dust Swirl — the loop, the nebula's numbers and the scripted finger, in closed form.
//
// A port of the React Native `nds-motion.ts`. A grain's place is a pure function of its seeds and the
// loop time: `pose` in NebulaDustSwirl.metal (the same maths as the React Native WGSL). Grains come in
// pairs (index 2j and 2j + 1) that share their seeds and sit on opposite arms, half a turn apart. The
// whole nebula turns half a turn per loop, so at t = loop each grain stands exactly where its twin
// stood at t = 0 and the frame repeats. Only what a finger does to a grain is state (a displacement on
// a spring), blended out in the rest stretch and cleared at the cut.

import Foundation

enum NebulaDustSwirlMotion {
    static let loop: Double = 10
    static let tau = Double.pi * 2

    /// grains in the compute buffer (even: they come in pairs)
    static let count = 40_000
    /// the threadgroup's size
    static let group = 64
    /// the nebula's centre, as fractions of the screen
    static let centreX = 0.5
    static let centreY = 0.4
    /// one pose unit in points = min(width × scaleW, height × scaleH)
    static let scaleW = 0.5
    static let scaleH = 0.27
    /// the disc's tilt, radians
    static let tilt = 0.9

    /// the loop's phases, seconds
    static let ghost = (1.2, 6.0)
    static let settle = (8.6, 9.4)
    static let rollback = (9.2, 9.8)

    /// the finger
    static let touchEase = 12.0
    /// the film runs at this rate under a finger
    static let filmRate = 0.4
    /// the finger's speed is capped here, pt/s
    static let maxSpeed = 2400.0

    /// a touch hands the nebula back this long after it ends, seconds
    static let idle = 2.0
    /// then the film runs to the nearer end of the loop over this long, seconds
    static let returnTime = 1.6
    /// the sleep timer's start, seconds (38:12 left) and the whole session (45 min)
    static let session = 2292.0
    static let sessionTotal = 2700.0
    /// the frozen moment under reduced motion
    static let still = 0.0

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }

    /// the ghost finger at t: x, y in pose units around the centre, how much shows
    static func ghostAt(_ t: Double) -> (x: Double, y: Double, alpha: Double) {
        let u = seg(t, ghost.0, ghost.1)
        let e = smooth(u)
        let alpha = smooth(seg(u, 0, 0.1)) * (1 - smooth(seg(u, 0.9, 1)))
        // an arc round the core, along the arms, in the disc's tilt
        let a = -0.3 + 3.4 * e
        let rad = 0.64 - 0.14 * sin(Double.pi * e)
        return (rad * cos(a), rad * sin(a) * cos(tilt) * 1.15, alpha)
    }

    /// how much of the finger's push is shown at t
    static func holdAt(_ t: Double) -> Double { 1 - smooth(seg(t, settle.0, settle.1)) }

    /// the sleep timer in seconds, a real number; it counts down and rolls back in the rest
    static func sessionAt(_ t: Double, _ start: Double = session) -> Double {
        let ran = min(t, rollback.0)
        let v = start - ran * (1 - smooth(seg(t, rollback.0, rollback.1)))
        let up = ceil(v)
        return up - smooth(seg(up - v, 0, 0.3))
    }

    /// the core's breath, 0…1, twice per loop
    static func glowAt(_ t: Double) -> Double { 0.5 - 0.5 * cos(tau * 2 * t / loop) }

    /// a digit's place on its strip; the tens roll while the ones go from 9 to 10
    static func stripOf(_ v: Double, tens: Bool) -> Double {
        let ones = (v.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        return tens ? floor(v / 10).truncatingRemainder(dividingBy: 10) + max(0, ones - 9) : ones
    }
}
