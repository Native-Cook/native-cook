// Louver Theme Switch — the loop, the slats' numbers and the scripted finger, in closed form.
//
// A theme change is one number, `amount`: 0 is the light screen, 1 the dark one. `dir` says which way
// the turn left its rest (+1 towards dark, −1 towards light), so the top slat leads both ways.
// LouverThemeSwitch.metal turns those two numbers into the picture.

import Foundation

enum LouverThemeSwitchMotion {
    static let loop: Double = 8

    /// slats across the screen's height (the shader has the rest of the look's numbers)
    static let slats = 14
    static let spread = 0.6
    static let back = 1.3725

    static let toDark = (0.5, 2.0)
    static let toLight = (5.0, 6.5)
    /// the ghost finger: shows, presses, leaves, relative to the start of each turn
    static let ghostIn = -0.42
    static let ghostPress = -0.12
    static let ghostOut = 0.3

    /// a tap's turn takes this long, seconds, for the whole way
    static let tapTurn = 1.25
    /// a drag of this fraction of the screen's height is a whole turn
    static let scrub = 0.62
    /// a release projects the drag this far ahead, seconds
    static let project = 0.18
    /// the critically damped return after a release, rad/s
    static let omega = 9.0
    /// a touch hands the screen back this long after it ends, seconds
    static let idle = 2.0
    /// below this distance from a rest the turn counts as finished
    static let rest = 0.0005

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }

    /// the film's amount at loop time t
    static func filmAmount(_ t: Double) -> Double {
        seg(t, toDark.0, toDark.1) - seg(t, toLight.0, toLight.1)
    }

    /// the film's direction at loop time t
    static func filmDir(_ t: Double) -> Double {
        t < (toDark.1 + toLight.0) / 2 ? 1 : -1
    }

    private static func tapAlpha(_ s: Double) -> Double {
        smooth(seg(s, ghostIn, ghostIn + 0.2)) * (1 - smooth(seg(s, ghostOut, ghostOut + 0.22)))
    }

    private static func tapPress(_ s: Double) -> Double {
        smooth(seg(s, ghostPress - 0.1, ghostPress)) * (1 - smooth(seg(s, 0.04, 0.2)))
    }

    /// the ghost finger: opacity, press 0…1, and whether it is on Dark (true) or Light
    static func ghostAt(_ t: Double) -> (alpha: Double, press: Double, onDark: Bool) {
        let first = t < (toDark.1 + toLight.0) / 2
        let s = t - (first ? toDark.0 : toLight.0)
        return (tapAlpha(s), tapPress(s), first)
    }

    /// a critically damped approach from x0 with speed v0 to `to`
    static func settleAt(_ x0: Double, _ v0: Double, _ to: Double, _ tau: Double) -> Double {
        let a = x0 - to
        let b = v0 + omega * a
        return to + (a + b * tau) * exp(-omega * tau)
    }
}
