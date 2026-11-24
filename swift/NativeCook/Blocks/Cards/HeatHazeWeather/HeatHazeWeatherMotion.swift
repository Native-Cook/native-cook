// Heat Haze Weather — the loop, the forecast's numbers and the scripted finger, in closed form.
//
// A port of the React Native `hhw-motion.ts`. Everything on screen is a function of one number, the
// hour H (12 … 21, a float). The film moves H with the hour slider; the scene shader
// (HeatHazeWeather.metal) reads the sun's place, the heat and the dusk derived from H here.

import Foundation

enum HeatHazeWeatherMotion {
    static let loop: Double = 10

    /// the slider's range, hours (12 PM … 9 PM)
    static let hMin = 12.0
    static let hMax = 21.0
    /// the hour the screen rests at, the hottest hour the film visits, and the evening it ends on
    static let hRest = 13.0
    static let hPeak = 15.5
    static let hEvening = 20.0

    /// the loop's phases, seconds
    static let toPeak = (1.2, 3.4)
    static let shimmer = (3.7, 4.75)
    static let toEvening = (5.0, 7.6)
    static let back = (8.3, 9.5)

    /// the horizon, as a fraction of the screen's height
    static let horizon = 0.54

    /// the haze's flow repeats every `flow` seconds (the loop is a whole multiple of it)
    static let flow = 2.5

    /// a touch hands the screen back this long after it ends, seconds
    static let idle = 2.0
    /// then the hour eases back to rest over this long, seconds
    static let returnTime = 1.2
    /// the finger's shimmer comes and goes at this rate, 1/s
    static let touchEase = 9.0
    /// the slider's thumb, pt
    static let thumb = 28.0

    struct Forecast {
        /// whole hours from hMin to hMax, in order
        var hours: [Double]
        /// °, one per hour
        var temps: [Double]
        /// UV index 0 … 11, one per hour
        var uv: [Double]
        /// sunset as an hour, e.g. 19.87 = 7:52 PM
        var sunset: Double
    }

    static let forecast = Forecast(
        hours: [12, 13, 14, 15, 16, 17, 18, 19, 20, 21],
        temps: [33, 36, 39, 41, 41, 39, 36, 33, 31, 29],
        uv: [10, 10, 9, 8, 6, 4, 2, 1, 0, 0],
        sunset: 19.87
    )

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }

    static func easeInOutCubic(_ v: Double) -> Double {
        v < 0.5 ? 4 * v * v * v : 1 - pow(-2 * v + 2, 3) / 2
    }

    /// the film's hour at loop time t
    static func hourAt(_ t: Double) -> Double {
        let up = easeInOutCubic(seg(t, toPeak.0, toPeak.1))
        let eve = easeInOutCubic(seg(t, toEvening.0, toEvening.1))
        let bk = easeInOutCubic(seg(t, back.0, back.1))
        return hRest + (hPeak - hRest) * up + (hEvening - hPeak) * eve - (hEvening - hRest) * bk
    }

    /// a forecast column read at hour h, linear between whole hours
    static func sample(_ values: [Double], _ h: Double) -> Double {
        let x = min(max(h - hMin, 0), Double(values.count - 1))
        let i = min(Int(floor(x)), values.count - 2)
        let f = x - Double(i)
        return values[i] + (values[i + 1] - values[i]) * f
    }

    /// how far into the evening the light is, 0 … 1
    static func duskAt(_ h: Double, _ sunset: Double) -> Double {
        smooth(seg(h, sunset - 3.4, sunset + 0.6))
    }

    /// the haze's strength, 0 … 1: the heat of the air, calmed by the evening
    static func heatAt(_ temp: Double, _ dusk: Double) -> Double {
        pow(clamp01((temp - 29) / 12), 1.2) * (1 - 0.8 * dusk)
    }

    /// the sun's place in pt: high in the early afternoon, on the hills at sunset
    static func sunAt(_ h: Double, _ sunset: Double, _ width: Double, _ height: Double) -> (Double, Double) {
        let e = clamp01((sunset + 0.15 - h) / (sunset + 0.15 - 12.5))
        let lift = sin(e * .pi / 2)
        let hy = height * horizon
        return (width * (0.72 - 0.34 * (h - hMin) / (hMax - hMin)), hy - lift * (hy - height * 0.13))
    }

    /// a counter that rolls to the next whole number in the last part of each step
    static func rolled(_ v: Double, _ from: Double = 0.6) -> Double {
        let whole = floor(v)
        return whole + smooth(seg(v - whole, from, 1))
    }

    /// the ghost finger on the slider: how much shows, and how hard it presses
    static func sliderGhostAt(_ t: Double) -> (alpha: Double, press: Double) {
        var alpha = 0.0
        var press = 0.0
        for (a, b) in [toPeak, toEvening, back] {
            alpha += smooth(seg(t, a - 0.35, a - 0.12)) * (1 - smooth(seg(t, b + 0.05, b + 0.28)))
            press += smooth(seg(t, a - 0.15, a)) * (1 - smooth(seg(t, b, b + 0.15)))
        }
        return (clamp01(alpha), clamp01(press))
    }

    /// the ghost finger on the road: x, y as fractions of the screen, how much shows, and its shimmer
    static func sceneGhostAt(_ t: Double) -> (x: Double, y: Double, alpha: Double, on: Double) {
        let (a, b) = shimmer
        let u = smooth(seg(t, a + 0.15, b - 0.15))
        let alpha = smooth(seg(t, a, a + 0.2)) * (1 - smooth(seg(t, b - 0.2, b)))
        let on = smooth(seg(t, a + 0.12, a + 0.4)) * (1 - smooth(seg(t, b - 0.35, b + 0.05)))
        return (0.57 - 0.14 * u, horizon + 0.032, alpha, on)
    }

    /// the UV index's band, 0 Low … 4 Extreme, crossing each boundary over a quarter of a point
    static func uvBandAt(_ uv: Double) -> Double {
        smooth(seg(uv, 2.25, 2.75)) + smooth(seg(uv, 5.25, 5.75)) + smooth(seg(uv, 7.25, 7.75)) + smooth(seg(uv, 10.25, 10.75))
    }

    /// a digit's place on its strip; the tens roll while the ones go from 9 to 10
    static func stripOf(_ v: Double, tens: Bool) -> Double {
        let ones = (v.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        return tens ? floor(v / 10).truncatingRemainder(dividingBy: 10) + max(0, ones - 9) : ones
    }

    /// how much of condition i shows at hour h
    static func conditionAt(_ h: Double, _ i: Int) -> Double {
        let scorch = smooth(seg(h, 14.3, 14.9)) * (1 - smooth(seg(h, 16.6, 17.2)))
        let cool = smooth(seg(h, 16.6, 17.2))
        return i == 0 ? clamp01(1 - scorch - cool) : i == 1 ? scorch : cool
    }

    static func formatHour(_ h: Double) -> String {
        let whole = Int(floor(h))
        let minutes = Int(((h - Double(whole)) * 60).rounded())
        return "\(whole % 12 == 0 ? 12 : whole % 12):\(String(format: "%02d", minutes)) \(whole < 12 ? "AM" : "PM")"
    }
}
