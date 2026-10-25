import Foundation

/*
 Tide Gauge Chart — the numbers and pure functions shared by the screen, the shader and the buoy
 (a port of tideGaugeMotion.ts).

 Everything the loop shows is a function of the loop time t (seconds, 0 ..< loop):

   0.0 – 1.6   rest: the buoy floats at "now" (10:40), ripples travel along the surface.
   1.6 – 2.5   scrub back to 06:00 (ease-in-out); the water leans against the move.
   2.5 – 5.0   scrub forward through the high tide to 18:00; the surface sloshes at the turn.
   5.0 – 5.5   back to the high tide at 14:12 (ease-in-out), the slosh rings out.
   5.5 – 6.6   hold on the high tide: the reading glows.
   6.6 – 8.4   return to now (ease-in-out).
   8.4 – 9.0   rest; the last ripple of the slosh is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

enum TideGaugeChartMotion {
    static let loop = 9.0

    /// The chart spans one day, in hours.
    static let day = 24.0
    /// "Now" on the chart: 10:40.
    static let now = 10 + 40.0 / 60
    /// Water levels the plot shows, in metres (bottom and top of the plot).
    static let hMin = -0.4
    static let hMax = 3.0

    /// A semi-diurnal tide: high 2.4 m at 14:12, low 0.3 m at 08:05 (half a period earlier).
    static let tideMean = 1.35
    static let tideAmp = 1.05
    static let tideHighAt = 14.2
    static let tidePeriod = 12.23

    /// Extremes of the day (hours), where scrubbing past gives a selection tick.
    static let extremes = [tideHighAt - tidePeriod, tideHighAt - tidePeriod / 2, tideHighAt, tideHighAt + tidePeriod / 2]

    static let sunrise = 6 + 12.0 / 60
    static let sunset = 18 + 47.0 / 60

    /// Slosh: a damped oscillator (rad/s, damping ratio) driven by the scrub speed (pt of tilt per pt/s).
    static let sloshOmega = 9.0
    static let sloshZeta = 0.3
    static let sloshGain = 0.018
    static let sloshMax = 14.0

    /// After a touch: wait, then ease back to the loop's rest (s).
    static let idleBeforeReturn = 2.0
    static let returnFor = 0.8

    struct Plot {
        var x = 0.0
        var y = 0.0
        var w = 1.0
        var h = 1.0
    }

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }

    static func tide(_ hour: Double) -> Double {
        tideMean + tideAmp * cos(2 * Double.pi * (hour - tideHighAt) / tidePeriod)
    }

    /// Rising (+1) or falling (-1) at an hour.
    static func tideTrend(_ hour: Double) -> Double {
        sin(2 * Double.pi * (hour - tideHighAt) / tidePeriod) < 0 ? 1 : -1
    }

    /// The scrubbed hour of the loop at t.
    static func scriptHour(_ t: Double) -> Double {
        if t < 1.6 { return now }
        if t < 2.5 { return now + (6 - now) * easeInOut(seg(t, 1.6, 2.5)) }
        if t < 5.0 { return 6 + 12 * easeInOutSine(seg(t, 2.5, 5.0)) }
        if t < 5.5 { return 18 + (tideHighAt - 18) * easeInOut(seg(t, 5.0, 5.5)) }
        if t < 6.6 { return tideHighAt }
        if t < 8.4 { return tideHighAt + (now - tideHighAt) * easeInOut(seg(t, 6.6, 8.4)) }
        return now
    }

    /// The glow on the reading while the loop holds on the high tide.
    static func scriptGlow(_ t: Double) -> Double {
        easeInOutSine(seg(t, 5.3, 5.8)) * (1 - easeInOutSine(seg(t, 6.3, 6.9)))
    }

    /// The loop's slosh at t: tilt (pt) and chop (0..1). Integrated from t = 0 with a fixed step
    /// every frame, so it is a pure function of t; the tail is faded out before the loop closes.
    static func scriptSlosh(_ t: Double, plotW: Double) -> (tilt: Double, chop: Double) {
        let step = 1.0 / 60
        let pxPerHour = plotW / day
        var tilt = 0.0
        var v = 0.0
        var prev = scriptHour(0)
        var speed = 0.0
        let n = Int(floor(t / step))
        if n >= 1 {
            for i in 1...n {
                let h = scriptHour(Double(i) * step)
                speed = (h - prev) / step * pxPerHour
                prev = h
                let drive = clamp(-speed * sloshGain, -sloshMax, sloshMax)
                let a = -sloshOmega * sloshOmega * (tilt - drive) - 2 * sloshZeta * sloshOmega * v
                v += a * step
                tilt += v * step
            }
        }
        let fade = 1 - easeInOutSine(seg(t, 8.5, 9.0))
        return (tilt * fade, clamp(abs(speed) / 700, 0, 1) * fade)
    }

    static func hourToX(_ hour: Double, _ plot: Plot) -> Double { plot.x + hour / day * plot.w }

    static func levelToY(_ h: Double, _ plot: Plot) -> Double {
        plot.y + (1 - (h - hMin) / (hMax - hMin)) * plot.h
    }

    /// The water surface at x, exactly as the shader draws it: the tide curve, three travelling
    /// ripples (whole numbers of cycles per loop), and the slosh tilt about the scrubber.
    static func surfaceY(_ x: Double, _ plot: Plot, phase: Double, scrubX: Double, tilt: Double, chop: Double) -> Double {
        let hour = (x - plot.x) / plot.w * day
        var y = levelToY(tide(hour), plot)
        let T = 2 * Double.pi * phase
        let amp = 1 + 1.6 * chop
        y += amp * (1.1 * sin(x * 0.071 - 3 * T) + 0.7 * sin(x * 0.13 + 5 * T + 1.3) + 0.4 * sin(x * 0.29 - 8 * T + 2.1))
        let d = clamp((x - scrubX) / (plot.w * 0.5), -1, 1)
        return y + tilt * d
    }

    /// "14:12" for an hour of the day.
    static func clockText(_ hour: Double) -> String {
        let m = Int((clamp(hour, 0, day - 1.0 / 60) * 60).rounded())
        return String(format: "%02d:%02d", m / 60, m % 60)
    }
}
