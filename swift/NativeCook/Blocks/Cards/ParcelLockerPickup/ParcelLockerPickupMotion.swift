import Foundation

/// Shared timing functions for the autoplay script and event-driven interaction.
/// Times and spring durations are seconds; angles are degrees; normalized progress is unitless.
enum ParcelLockerPickupMotion {
    static let loop = 10.0

    /// Clamps time t to progress 0...1 over the interval a...b (all three share the same units).
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }

    /// Smoothstep between two thresholds, with zero slope at both ends.
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = seg(x, a, b)
        return t * t * (3 - 2 * t)
    }

    /// Cubic easing for normalized flight and fold progress.
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Unit-step spring response; duration is seconds and ratio is the damping ratio.
    static func springStep(_ t: Double, _ duration: Double, _ ratio: Double) -> Double {
        if t <= 0 {
            return 0
        }
        let w = 2 * Double.pi / duration
        if ratio >= 1 {
            return 1 - exp(-w * t) * (1 + w * t)
        }
        let wd = w * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * w * t) * (cos(wd * t) + ratio * w / wd * sin(wd * t))
    }

    /// Decaying impulse for the latch kick: a 0.22 s spring with damping ratio 0.35.
    static func springKick(_ t: Double) -> Double {
        if t <= 0 {
            return 0
        }
        let w = 2 * Double.pi / 0.22
        let wd = w * sqrt(1 - 0.35 * 0.35)
        return exp(-0.35 * w * t) * sin(wd * t) * 1.6
    }

    /// Lit digit count, 0...4, fractional while a cell lights; t is seconds since Open.
    static func digitsAt(_ t: Double) -> Double {
        t < 0 ? 0 : min(4, max(0, (t - 0.15) / 0.12 + 1))
    }

    /// Tube-light strike: off, a deterministic stutter from 1.05...1.6 s after Open, then on.
    static func lightAt(_ t: Double) -> Double {
        if t < 1.05 {
            return 0
        }
        if t >= 1.6 {
            return 1
        }
        let u = (t - 1.05) / 0.55
        if u < 0.12 {
            return 1
        }
        if u < 0.28 {
            return 0.08
        }
        if u < 0.36 {
            return 0.7
        }
        if u < 0.5 {
            return 0.15
        }
        if u < 0.58 {
            return 0.9
        }
        if u < 0.66 {
            return 0.4
        }
        return 1
    }

    /// Door angle in degrees after Open: latch kick at 0.72 s, hinge swing from 0.95 s.
    static func openAngle(_ t: Double) -> Double {
        if t < 0.72 {
            return 0
        }
        return max(0, 4 * springKick(t - 0.72) * (1 - seg(t, 0.95, 1.15)) + 104 * springStep(t - 0.95, 0.95, 0.7))
    }

    /// One frame: angle is degrees, takeAge is seconds and digits is a fractional count out of four.
    /// Other scalars are normalized progress/intensity; spring-driven values may overshoot.
    /// led is 0 for amber pulse, 1 for green and 2 for off.
    struct Pose {
        var press = 0.0
        var digits = 0.0
        var angle = 0.0
        var light = 0.0
        var parcel = 0.0
        var sheet = 0.0
        var takeAge = -10.0
        var fold = 0.0
        var led = 0
    }

    /// Whole pose at loop time in seconds; the 10 s loop starts and ends at rest.
    /// Beats: press 1.2...1.4, Open 1.4, latch 2.12, swing 2.35, light 2.45...3.0,
    /// Take 4.0, receipt spring 4.45, door slam 4.62, Done/fold 8.4...8.9, rest from 9.2.
    static func script(_ time: Double) -> Pose {
        if time >= 9.2 || time < 1.2 {
            return Pose()
        }
        let openAge = time - 1.4
        let takeAge = time - 4
        let doneAge = time - 8.4
        let opened = openAngle(openAge)
        let angle = takeAge >= 0.62 ? abs(opened * (1 - springStep(takeAge - 0.62, 0.55, 0.42))) : opened
        let closed = takeAge > 0.87 && angle < 3
        return Pose(
            press: sin(Double.pi * seg(time, 1.2, 1.4)), digits: digitsAt(openAge) * (1 - seg(doneAge, 0, 0.3)),
            angle: angle,
            light: lightAt(openAge) * (takeAge > 0.77 ? 1 - smooth(0.77, 1.02, takeAge) : 1),
            parcel: takeAge < 0 ? 0 : easeInOut(seg(takeAge, 0, 0.92)),
            sheet: springStep(takeAge - 0.45, 0.6, 0.86) * (1 - easeInOut(seg(doneAge, 0, 0.5))), takeAge: takeAge,
            fold: easeInOut(seg(doneAge, 0, 0.5)),
            led: openAge < 0.72 ? 0 : closed && takeAge > 1.32 ? 2 : 1)
    }
}
