// Ripple Pond Photo — the loop, the simulation's constants and the scripted hand, in closed form.
//
// The water itself is the one thing that is not a function of time: it is a height field stepped at
// `stepHz` from the drops below, cleared at t = 0 while it is provably calm (the steepest slope is
// 0.00002 a second before the cut).

import Foundation

enum RipplePondPhotoMotion {
    static let loop: Double = 10

    // the wave simulation
    /// grid columns; rows follow the screen's aspect so a cell is square
    static let gridW = 256
    /// fixed steps a second, however fast the display refreshes
    static let stepHz: Double = 240
    /// (wave speed × dt / dx)²: 0.469 cells a step, about 173 pt/s at 393 pt wide
    static let c2 = 0.22
    /// amplitude kept per step: 0.34 of it is left after a second
    static let damp = 0.9955
    /// the damping the water is eased to before the loop's cut and before autoplay resumes
    static let calmDamp = 0.94
    /// most steps one frame may run (a stall is not caught up with a spike)
    static let maxSteps = 12

    // what a touch does to the water
    /// a tap: a zero-mean dip (1 − q) exp(−q), q = r² / radius², radius in cells
    static let tapRadius = 10.0
    static let tapDepth = -2.2
    /// a tap is pressed into the field over this many steps with a sin² envelope
    static let tapSteps = 8
    /// a drag: the same dip, smaller, laid along the path
    static let wakeRadius = 6.0
    /// depth per point of travel, so a faster finger cuts a deeper wake
    static let wakePerPt = -0.07
    /// deepest wake one frame may add
    static let wakeMax = -0.9

    // how the field is shown (pt and per unit of slope, slope = height difference across one cell)
    static let refract = 90.0
    static let refractMax = 26.0
    /// red and blue are refracted a little more and less than green
    static let chroma = 0.08
    static let normal = 9.0
    static let specPower = 48.0
    static let spec = 1.2
    static let caustic = 14.0
    static let shade = 7.0

    /// scripted taps: time, x and y as fractions of the screen
    static let taps: [(at: Double, x: Double, y: Double)] = [(0.9, 0.3, 0.72), (2.3, 0.72, 0.3), (5.9, 0.42, 0.8)]
    /// the scripted drag
    static let drag = (from: 3.5, to: 5.0)
    /// damping eases from `damp` to `calmDamp`, and stays there until the cut
    static let calm = (from: 7.9, to: 8.4)
    /// the ghost finger leads a tap by this much, and lingers this long after it
    static let ghostIn = 0.22
    static let ghostOut = 0.3

    /// a touch hands the water back this long after it ends
    static let idle: Double = 2
    /// then the water is calmed for this long (damping eased over the first part) and the loop restarts
    static let returnEase = 0.45
    static let returnTotal = 1.0

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }
    static func easeOutCubic(_ v: Double) -> Double {
        let u = 1 - v
        return 1 - u * u * u
    }
    /// Reanimated's default timing curve (the ghost's and the hint's fades)
    static func inOutQuad(_ v: Double) -> Double { v < 0.5 ? 2 * v * v : 1 - 2 * (1 - v) * (1 - v) }

    /// the share of a tap's depth pressed in by the time x (0…1) of its envelope has passed
    static func tapShare(_ x: Double) -> Double {
        let v = clamp01(x)
        return v - sin(2 * .pi * v) / (2 * .pi)
    }

    /// the scripted drag's path, fractions of the screen: a shallow arc from the lower left to the right
    static func dragX(_ u: Double) -> Double { 0.16 + 0.68 * smooth(clamp01(u)) }
    static func dragY(_ u: Double) -> Double {
        let e = smooth(clamp01(u))
        return 0.7 - 0.1 * sin(.pi * e) - 0.02 * e
    }

    /// which scripted tap the ghost finger is busy with at t, or -1
    static func ghostTap(_ t: Double) -> Int {
        for (i, tap) in taps.enumerated() where t >= tap.at - ghostIn && t < tap.at + ghostOut { return i }
        return -1
    }

    /// where the ghost finger is at t, fractions of the screen
    static func ghostAt(_ t: Double) -> (x: Double, y: Double) {
        let which = ghostTap(t)
        if which >= 0 { return (taps[which].x, taps[which].y) }
        let u = seg(t, drag.from, drag.to)
        return (dragX(u), dragY(u))
    }

    /// the ghost finger's opacity at t
    static func ghostAlpha(_ t: Double) -> Double {
        let i = ghostTap(t)
        if i >= 0 {
            let at = taps[i].at
            return easeOutCubic(seg(t, at - ghostIn, at - 0.06)) * (1 - smooth(seg(t, at + 0.08, at + ghostOut)))
        }
        return easeOutCubic(seg(t, drag.from - ghostIn, drag.from)) * (1 - smooth(seg(t, drag.to, drag.to + ghostOut)))
    }

    /// how hard the ghost finger presses at t, 0…1 (it lands on the water at a tap's time)
    static func ghostPress(_ t: Double) -> Double {
        let i = ghostTap(t)
        if i >= 0 {
            let at = taps[i].at
            return smooth(seg(t, at - 0.07, at)) * (1 - smooth(seg(t, at + 0.06, at + 0.24)))
        }
        return smooth(seg(t, drag.from - 0.08, drag.from)) * (1 - smooth(seg(t, drag.to, drag.to + 0.2)))
    }

    /// the damping of a step at loop time t
    static func dampAt(_ t: Double) -> Double { damp + (calmDamp - damp) * seg(t, calm.from, calm.to) }

    /// grid rows for a screen, even, so a cell is as tall as it is wide
    static func gridRows(width: Double, height: Double) -> Int {
        max(256, min(640, Int((Double(gridW) * height / width / 2).rounded()) * 2))
    }
}
