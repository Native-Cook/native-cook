// Ink Wash Page Turn — the loop, the finger's rules and the scripted hand, in closed form.
//
// A page turn is one number, `progress` 0…1, with the point the ink starts from;
// InkWashPageTurn.metal turns it into the picture and holds the bleed's own numbers.

import Foundation

enum InkWashPageTurnMotion {
    static let loop = 10.0
    static let pages = 3
    /// a touch hands the pages back this long after it ends
    static let idle = 2.0

    // how a finger turns the page
    static let scrubLength = 0.62
    static let band = 0.6
    static let bandMax = 0.9
    static let project = 0.18
    static let commit = 0.3
    static let tapTravel = 8.0
    static let backZone = 0.3
    static let omega = 4.2
    static let tapOmega = 3.0
    static let settleBlend = (from: 1.2, to: 1.6)
    static let tapBlend = (from: 1.7, to: 2.2)

    // the loop's three turns
    static let first = (down: 0.7, up: 1.5, x: 0.8, y: 0.74)
    static let second = (down: 3.5, up: 4.3, x: 0.46, y: 0.36)
    static let tap = (at: 6.3, x: 0.5, y: 0.52)
    static let travel = 0.36
    static let ghostIn = 0.22
    static let ghostOut = 0.3

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }
    static func easeOutCubic(_ v: Double) -> Double {
        let u = 1 - v
        return 1 - u * u * u
    }
    /// the default curve of a timed fade
    static func inOutQuad(_ v: Double) -> Double {
        v < 0.5 ? 2 * v * v : 1 - pow(-2 * v + 2, 2) / 2
    }

    /// progress for a drag of `x` scrub lengths: 1:1 up to `band`, then a rubber band
    static func scrub(_ x: Double) -> Double {
        if x <= band { return max(0, x) }
        let room = bandMax - band
        return band + room * (1 - exp(-(x - band) / room))
    }

    /// the settle: from x0 with speed v0 (1/s) to target, tau seconds after the release, exact at `until`
    static func settle(_ x0: Double, _ v0: Double, _ target: Double, _ tau: Double, _ omega: Double, _ from: Double, _ until: Double) -> Double {
        let a = x0 - target
        let b = v0 + omega * a
        let x = target + (a + b * tau) * exp(-omega * tau)
        let k = smooth(seg(tau, from, until))
        return clamp01(x + (target - x) * k)
    }

    /// progress of a scripted swipe at loop time t, 0 before and 1 once settled
    static func swipeAt(_ t: Double, _ down: Double, _ up: Double) -> Double {
        if t <= down { return 0 }
        if t < up {
            let u = (t - down) / (up - down)
            return scrub(travel * u * u / scrubLength)
        }
        // released at the swipe's final speed
        let x0 = scrub(travel / scrubLength)
        let v0 = 2 * travel / (up - down) / scrubLength
        return settle(x0, v0, 1, t - up, omega, settleBlend.from, settleBlend.to)
    }

    static func tapAt(_ t: Double) -> Double {
        if t <= tap.at { return 0 }
        return settle(0, 0, 1, t - tap.at, tapOmega, tapBlend.from, tapBlend.to)
    }

    /// which turn the loop is on at t (a turn lasts until the next begins)
    static func turnAt(_ t: Double) -> Int {
        t < second.down ? 0 : t < tap.at ? 1 : 2
    }

    static func ghostAlpha(_ t: Double) -> Double {
        let a = easeOutCubic(seg(t, first.down - ghostIn, first.down)) * (1 - smooth(seg(t, first.up, first.up + ghostOut)))
        let b = easeOutCubic(seg(t, second.down - ghostIn, second.down)) * (1 - smooth(seg(t, second.up, second.up + ghostOut)))
        let c = easeOutCubic(seg(t, tap.at - ghostIn, tap.at - 0.06)) * (1 - smooth(seg(t, tap.at + 0.08, tap.at + ghostOut)))
        return max(a, b, c)
    }

    static func ghostPress(_ t: Double) -> Double {
        let a = smooth(seg(t, first.down - 0.08, first.down)) * (1 - smooth(seg(t, first.up, first.up + 0.2)))
        let b = smooth(seg(t, second.down - 0.08, second.down)) * (1 - smooth(seg(t, second.up, second.up + 0.2)))
        let c = smooth(seg(t, tap.at - 0.07, tap.at)) * (1 - smooth(seg(t, tap.at + 0.06, tap.at + 0.24)))
        return max(a, b, c)
    }

    static func ghostX(_ t: Double) -> Double {
        let k = turnAt(t)
        if k == 2 { return tap.x }
        let s = k == 0 ? first : second
        let u = seg(t, s.down, s.up)
        return s.x - travel * u * u
    }

    static func ghostY(_ t: Double) -> Double {
        let k = turnAt(t)
        return k == 2 ? tap.y : k == 0 ? first.y : second.y
    }
}
