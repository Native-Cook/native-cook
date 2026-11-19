import Foundation

/// Soft Weather: the forecast, the sky palettes and the film, as pure functions. Everything the screen shows is a
/// function of `hour` (a fractional index into `hours`, what the hourly row is scrolled to) and the ambient clock.
/// A port of the React Native `weatherMotion.ts`.
enum MeshGradientWeatherMotion {
    static let loop: Double = 16

    struct Hour { let label: String; let temp: Double; let symbol: String; let cond: Int; let rain: Double; let wind: Double }

    static let conditions = ["Clear", "Sunny", "Partly Cloudy", "Cloudy", "Light Rain", "Rain"]

    static let hours: [Hour] = [
        Hour(label: "Now", temp: 13, symbol: "sunrise.fill", cond: 0, rain: 0, wind: 6),
        Hour(label: "8 AM", temp: 14, symbol: "sun.max.fill", cond: 1, rain: 0, wind: 7),
        Hour(label: "9 AM", temp: 16, symbol: "sun.max.fill", cond: 1, rain: 0, wind: 8),
        Hour(label: "10 AM", temp: 18, symbol: "sun.max.fill", cond: 1, rain: 0, wind: 9),
        Hour(label: "11 AM", temp: 19, symbol: "cloud.sun.fill", cond: 2, rain: 5, wind: 11),
        Hour(label: "12 PM", temp: 20, symbol: "cloud.sun.fill", cond: 2, rain: 10, wind: 12),
        Hour(label: "1 PM", temp: 19, symbol: "cloud.fill", cond: 3, rain: 30, wind: 15),
        Hour(label: "2 PM", temp: 17, symbol: "cloud.drizzle.fill", cond: 4, rain: 70, wind: 18),
        Hour(label: "3 PM", temp: 16, symbol: "cloud.rain.fill", cond: 5, rain: 90, wind: 21),
        Hour(label: "4 PM", temp: 16, symbol: "cloud.rain.fill", cond: 5, rain: 80, wind: 19),
        Hour(label: "5 PM", temp: 16, symbol: "cloud.drizzle.fill", cond: 4, rain: 40, wind: 14),
        Hour(label: "6 PM", temp: 16, symbol: "cloud.sun.fill", cond: 2, rain: 10, wind: 11),
        Hour(label: "7 PM", temp: 15, symbol: "sunset.fill", cond: 0, rain: 0, wind: 9),
        Hour(label: "8 PM", temp: 14, symbol: "cloud.moon.fill", cond: 2, rain: 0, wind: 8),
        Hour(label: "9 PM", temp: 13, symbol: "moon.stars.fill", cond: 0, rain: 0, wind: 7),
        Hour(label: "10 PM", temp: 12, symbol: "moon.stars.fill", cond: 0, rain: 0, wind: 6),
        Hour(label: "11 PM", temp: 12, symbol: "moon.fill", cond: 0, rain: 0, wind: 6),
        Hour(label: "12 AM", temp: 11, symbol: "moon.fill", cond: 0, rain: 0, wind: 5),
    ]
    static let lastHour = Double(hours.count - 1)
    static let temps = hours.map(\.temp)
    static let rainPct = hours.map(\.rain)
    static let wind = hours.map(\.wind)

    // ---------------------------------------------------------------- colour

    static func hexToLab(_ hex: String) -> [Double] {
        let n = Int(hex.dropFirst(), radix: 16) ?? 0
        func lin(_ v: Int) -> Double {
            let c = Double(v) / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let r = lin((n >> 16) & 255), g = lin((n >> 8) & 255), b = lin(n & 255)
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return [
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
        ]
    }

    static func palette(_ rows: [String]) -> [Double] { rows.flatMap(hexToLab) }

    // Six skies, each a 4 x 4 grid read row by row from the top of the screen.
    static let dawn = palette([
        "#7C86C8", "#8D8BCB", "#A592C8", "#B79BC6",
        "#A49ACB", "#D6A3BE", "#EBAFAE", "#D9A6B9",
        "#E9B6AA", "#F4C3A6", "#F6C59F", "#EDB7A8",
        "#F7D3B6", "#FADBBD", "#F5CFB5", "#EEC6B8",
    ])
    static let day = palette([
        "#5E97DA", "#6FA3DE", "#93B9E0", "#E9CF9E",
        "#73A7E0", "#8BB6E6", "#A9C8E9", "#DCD3B8",
        "#9CC0E8", "#B1CDEC", "#C3D7EE", "#CCDBEA",
        "#BFD6EE", "#CBDDF0", "#D4E2F0", "#D9E4EE",
    ])
    static let overcast = palette([
        "#6F7F99", "#7A89A0", "#8693A8", "#929CAE",
        "#8291A8", "#8E9BB0", "#9AA5B6", "#A2ABBA",
        "#9AA6B8", "#A6B0BF", "#AEB6C3", "#B3BAC5",
        "#AEB7C3", "#B6BEC8", "#BBC2CB", "#BEC4CC",
    ])
    static let rainy = palette([
        "#4B586E", "#536077", "#5B677D", "#636D82",
        "#5A677E", "#647189", "#6C788E", "#6A7489",
        "#6E7B91", "#78849A", "#7F8A9D", "#7A8598",
        "#7D889B", "#8590A1", "#8A94A4", "#86909F",
    ])
    static let dusk = palette([
        "#4F4E8E", "#5E5396", "#705797", "#7F5B95",
        "#7C5F9D", "#B06E93", "#C77D88", "#A06E94",
        "#D38C83", "#E2A07F", "#E6A87B", "#D99383",
        "#EAB585", "#EEBF8C", "#EAB185", "#E0A388",
    ])
    static let night = palette([
        "#161B3E", "#1A2048", "#202552", "#262551",
        "#1E2854", "#283669", "#30356A", "#2A2A5A",
        "#263563", "#30447A", "#38467A", "#2F3769",
        "#212E57", "#283962", "#2E3D69", "#262F59",
    ])

    /// Sky keyframes on the hour index. Between two, an eased blend in OKLab.
    static let skyH: [Double] = [0, 3, 5, 6.5, 8, 9.2, 10.6, 12, 14]
    static let skyP: [[Double]] = [dawn, day, day, overcast, rainy, rainy, overcast, dusk, night]

    /// Rain on the glass, by hour.
    static let rainH: [Double] = [6, 7, 8, 9.3, 10.3, 11]
    static let rainV: [Double] = [0, 0.45, 1, 1, 0.35, 0]

    // ---------------------------------------------------------------- easing

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }

    static func mixLab(_ a: [Double], _ b: [Double], _ f: Double) -> [Double] {
        var out = [Double](repeating: 0, count: a.count)
        for i in 0..<(a.count / 3) {
            let k = i * 3
            let ca = (a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2]).squareRoot()
            let cb = (b[k + 1] * b[k + 1] + b[k + 2] * b[k + 2]).squareRoot()
            let L = a[k] + (b[k] - a[k]) * f
            let x = a[k + 1] + (b[k + 1] - a[k + 1]) * f
            let y = a[k + 2] + (b[k + 2] - a[k + 2]) * f
            let c = (x * x + y * y).squareRoot()
            let s = c > 0.0001 ? (ca + (cb - ca) * f) / c : 1
            out[k] = L; out[k + 1] = x * s; out[k + 2] = y * s
        }
        return out
    }

    /// Linear lookup in a table of fractional-index values.
    static func sample(_ table: [Double], _ h: Double) -> Double {
        let n = table.count - 1
        let x = min(max(h, 0), Double(n))
        let i = Int(floor(x))
        if i >= n { return table[n] }
        return table[i] + (table[i + 1] - table[i]) * (x - Double(i))
    }

    static func keyed(_ xs: [Double], _ ys: [Double], _ x: Double) -> Double {
        if x <= xs[0] { return ys[0] }
        let n = xs.count - 1
        if x >= xs[n] { return ys[n] }
        var i = 0
        while i < n - 1 && x > xs[i + 1] { i += 1 }
        return ys[i] + (ys[i + 1] - ys[i]) * smooth((x - xs[i]) / (xs[i + 1] - xs[i]))
    }

    static func rain(at h: Double) -> Double { keyed(rainH, rainV, h) }

    /// The sky's 48 OKLab numbers at hour h.
    static func sky(at h: Double) -> [Double] {
        let n = skyH.count - 1
        var i = 0
        if h >= skyH[n] { i = n - 1 } else { while i < n - 1 && h > skyH[i + 1] { i += 1 } }
        let span = skyH[i + 1] - skyH[i]
        let f = h <= skyH[0] ? 0 : h >= skyH[n] ? 1 : smooth(clamp01((h - skyH[i]) / span))
        return mixLab(skyP[i], skyP[i + 1], f)
    }

    /// Control-point offsets (32 numbers) at hour h and ambient time amb.
    static func offsets(_ h: Double, _ amb: Double, _ rain: Double) -> [Double] {
        let tau = amb / loop * .pi * 2
        let sun = h < 3 ? h / 3 : h < 6 ? 1 : h < 8 ? 1 - (h - 6) / 2 : 0
        var out = [Double](repeating: 0, count: 32)
        for j in 0..<4 {
            for i in 0..<4 {
                let k = (j * 4 + i) * 2
                let edgeX = i == 0 || i == 3, edgeY = j == 0 || j == 3
                let fi = Double(i), fj = Double(j)
                let s = Double(i * 7 + j * 13) * 0.37
                var dx = 0.05 * sin(tau * Double(1 + ((i + j) & 1)) + s) + 0.055 * sin(h * 0.75 + fi * 1.3 + fj * 0.6)
                var dy = 0.03 * cos(tau * Double(1 + (i & 1)) + s * 1.7) + 0.035 * cos(h * 0.6 + fj * 1.7 + fi * 0.4)
                dx += -0.07 * sun * (fi / 3) * (1 - fj / 3)
                dy += 0.05 * sun * (fi / 3) * (1 - fj / 3) + 0.04 * rain * (edgeY ? 0 : 1)
                if edgeX { dx = 0 }
                if edgeY { dy = 0 }
                out[k] = dx; out[k + 1] = dy
            }
        }
        return out
    }

    // ---------------------------------------------------------------- the film

    static let toNoon = (2.0, 4.6), noon = (4.6, 5.6), toRain = (5.6, 7.6), rainLook = (7.6, 8.8)
    static let toNight = (8.8, 11.2), back = (13.0, 14.6)

    static func filmHour(_ t: Double) -> Double {
        if t < toNoon.0 { return 0 }
        if t < toNoon.1 { return 4 * easeInOut(seg(t, toNoon.0, toNoon.1)) }
        if t < noon.1 { return 4 + smooth(seg(t, noon.0, noon.1)) }
        if t < toRain.1 { return 5 + 3 * easeInOut(seg(t, toRain.0, toRain.1)) }
        if t < rainLook.1 { return 8 + smooth(seg(t, rainLook.0, rainLook.1)) }
        if t < back.0 { return 9 + 6 * easeInOut(seg(t, toNight.0, toNight.1)) }
        return 15 * (1 - easeInOut(seg(t, back.0, back.1)))
    }

    /// Rain is kept off the glass while the row rewinds to Now.
    static func filmRainGate(_ t: Double) -> Double {
        1 - smooth(seg(t, back.0 - 0.2, back.0 + 0.1)) * (1 - smooth(seg(t, back.1 - 0.1, back.1 + 0.2)))
    }

    /// Weight of condition label c at hour h: a tent around each hour that has it.
    static func condWeight(_ c: Int, _ h: Double) -> Double {
        var w = 0.0
        for (i, hr) in hours.enumerated() where hr.cond == c {
            let d = abs(h - Double(i))
            if d < 1 { w += 1 - d }
        }
        return smooth(clamp01(w))
    }
}
