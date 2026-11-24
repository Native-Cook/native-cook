// Murmuration Flock — the loop, the flock's numbers and the scripted hand, in closed form.
//
// A bird's place is a pure function of its seeds and the loop time: the pose in MurmurationFlock.metal.
// Only what a finger does to a bird is state (a displacement on a spring), and that is blended out in
// the rest stretch and cleared at the cut, so frame 12 s equals frame 0.

import Foundation

enum MurmurationFlockMotion {
    static let loop: Double = 12
    static let tau = Double.pi * 2

    /// birds in the compute buffer
    static let count = 20000
    /// the compute pass's threadgroup
    static let group = 64
    /// the flock's centre, as fractions of the screen
    static let centreX = 0.5
    static let centreY = 0.4
    /// one pose unit in points = min(width × scaleW, height × scaleH)
    static let scaleW = 0.5
    static let scaleH = 0.3

    /// the inhale's circle (for the guide ring)
    static let ringRadius = 0.78

    static let inhale = (1.4, 5.4)
    static let exhale = (6.4, 10.4)
    /// what a finger moved is blended out here
    static let settle = (10.7, 11.7)
    /// the session timer rolls back here
    static let rollback = (11.2, 11.9)
    /// the ghost finger
    static let ghost = (7.0, 9.2)

    /// how quickly the touch comes and goes, 1/s
    static let touchEase = 14.0
    /// the film runs at this rate under a finger
    static let filmRate = 0.25
    /// a touch hands the flock back this long after it ends, seconds
    static let idle = 2.0
    /// then the film runs to the nearer end of the loop over this long, seconds
    static let returnTime = 1.4
    /// the timer's start, seconds (4:12)
    static let session = 252.0
    /// the frozen moment under reduced motion: the circle, held
    static let still = 5.9

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }

    /// the breath's word at t: how much of Settle, Inhale and Exhale shows
    static func wordsAt(_ t: Double) -> [Double] {
        let inhaleK = smooth(seg(t, inhale.0 - 0.15, inhale.0 + 0.25)) * (1 - smooth(seg(t, exhale.0 - 0.2, exhale.0 + 0.15)))
        let exhaleK = smooth(seg(t, exhale.0 - 0.05, exhale.0 + 0.3)) * (1 - smooth(seg(t, exhale.1 - 0.2, exhale.1 + 0.2)))
        return [clamp01(1 - inhaleK - exhaleK), inhaleK, exhaleK]
    }

    /// the faint guide circle's strength at t
    static func guideAt(_ t: Double) -> Double {
        smooth(seg(t, inhale.0 + 0.6, inhale.1)) * (1 - smooth(seg(t, exhale.0, exhale.0 + 1.2)))
    }

    /// the session timer in seconds, a real number; it counts down and rolls back in the rest
    static func sessionAt(_ t: Double) -> Double {
        let ran = min(t, rollback.0)
        let v = session - ran * (1 - smooth(seg(t, rollback.0, rollback.1)))
        let up = v.rounded(.up)
        return up - smooth(seg(up - v, 0, 0.3))
    }

    /// the ghost finger at t: x, y as fractions of the screen, and how much of it shows
    static func ghostAt(_ t: Double) -> (x: Double, y: Double, alpha: Double) {
        let u = seg(t, ghost.0, ghost.1)
        let e = smooth(u)
        let alpha = smooth(seg(u, 0, 0.14)) * (1 - smooth(seg(u, 0.86, 1)))
        return (0.2 + 0.6 * e, centreY + 0.1 - 0.16 * e + 0.03 * sin(u * tau), alpha)
    }

    /// the ones digit's place on its strip
    static func onesOf(_ s: Double) -> Double {
        (s.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
    }

    /// the tens digit's place: it rolls while the ones go from 9 to 10
    static func tensOf(_ s: Double) -> Double {
        let ones = onesOf(s)
        return (floor(s / 10).truncatingRemainder(dividingBy: 10)) + max(0, ones - 9)
    }
}
