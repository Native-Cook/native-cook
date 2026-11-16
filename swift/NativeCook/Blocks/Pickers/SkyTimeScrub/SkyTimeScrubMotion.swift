import Foundation

enum SkyTimeScrubMotion {
    static let loop = 10.0
    static let rest = 1086.0
    struct Day {
        let sunrise: Double
        let noon: Double
        let sunset: Double
        let maxElevation: Double
        init(sunrise: Double = 372, noon: Double = 751, sunset: Double = 1098, maxElevation: Double = 62) {
            let rise = min(max(sunrise, 1), 1437), set = min(max(sunset, rise + 2), 1439)
            self.sunrise = rise; self.sunset = set; self.noon = min(max(noon, rise + 1), set - 1)
            self.maxElevation = min(89, max(1, maxElevation))
        }
        var night: Double { sunset + 71 }
        var lift: Double { sin(62 * .pi / 180) / sin(maxElevation * .pi / 180) }
    }
    struct Pose {
        var minutes = rest
        var ev = 0.0
        var held = 0.0
        var wake = 0.0
        var hint = 1.0
        var ticks = [99.0, 99, 99]
        var press = 0.0
    }
    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, v)) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func wrap(_ v: Double) -> Double { ((v.truncatingRemainder(dividingBy: 1440)) + 1440).truncatingRemainder(dividingBy: 1440) }
    // JS Math.round chooses the upper integer at a negative half, unlike Swift's default rounding.
    static func round(_ v: Double) -> Double { floor(v + 0.5) }
    static func smooth(_ v: Double) -> Double {
        let x = clamp(v)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func diff(_ a: Double, _ b: Double) -> Double { abs(wrap(a - b + 720) - 720) }
    static func dayParam(_ minutes: Double, _ day: Day) -> Double {
        let m = wrap(minutes)
        return m <= day.noon ? (m - day.sunrise) / (day.noon - day.sunrise) * 0.5
            : 0.5 + (m - day.noon) / (day.sunset - day.noon) * 0.5
    }
    static func fromDay(_ d: Double, _ day: Day) -> Double {
        d <= 0.5 ? day.sunrise + d / 0.5 * (day.noon - day.sunrise)
            : day.noon + (d - 0.5) / 0.5 * (day.sunset - day.noon)
    }
    static func solar(_ minutes: Double, _ day: Day) -> (elevation: Double, x: Double) {
        let m = wrap(minutes), d = dayParam(m, day)
        let elevation: Double
        if m >= day.sunrise && m <= day.sunset {
            elevation = day.maxElevation * sin(.pi * clamp(d))
        } else {
            let length = 1440 - (day.sunset - day.sunrise)
            let n = (m > day.sunset ? m - day.sunset : m + 1440 - day.sunset) / length
            elevation = -45 * sin(.pi * clamp(n))
        }
        return (elevation, 86 + 230 * d)
    }
    static func sunHeight(_ elevation: Double, _ day: Day, _ projection: Double) -> Double {
        103 * max(sin(elevation * .pi / 180) * day.lift, -0.5) * projection + 4 * exp(-pow(elevation / 3, 2))
    }
    static func semiHeight(_ elevation: Double) -> Double {
        let x = clamp(elevation / 2)
        return 7.7 * mix(0.72, 1, x * x * (3 - 2 * x))
    }
    static func arch(_ x: Double, _ centre: Double, _ amplitude: Double) -> Double {
        let q = clamp((x - 201) / 201, -1, 1), q2 = q * q
        return centre + amplitude - 2 * amplitude * pow(max(0.0001, cos(q * 1.57079633)), 1.75 - 0.75 * q2 + 1.63 * q2 * q2)
    }
    static func evTarget(_ elevation: Double) -> Double { mix(-0.35, 0.9, smooth((-elevation + 1) / 15)) }
    static func target(_ minutes: Double, _ day: Day) -> Double { evTarget(solar(minutes, day).elevation) }
    static func nearest(_ m: Double, _ detents: [Double]) -> (value: Double, distance: Double) {
        var best = m, distance = 1e9
        for d in detents {
            let candidate = d + round((m - d) / 1440) * 1440
            if abs(candidate - m) < distance { best = candidate; distance = abs(candidate - m) }
        }
        return (best, distance)
    }
    static func label(_ minutes: Double, _ day: Day, _ now: Double, named: Bool = true) -> String {
        let m = wrap(round(minutes)), clock = String(format: "%02d:%02d", Int(m) / 60, Int(m) % 60)
        guard named else { return clock }
        if diff(m, day.sunset) < 3 { return "Sunset · \(clock)" }
        if diff(m, day.sunrise) < 3 { return "Sunrise · \(clock)" }
        if diff(m, day.noon) < 3 { return "Solar noon · \(clock)" }
        if diff(m, now) < 3 { return "Now" }
        return clock
    }
    static func hintAt(_ t: Double) -> Double {
        t < 8.1 ? 1 - smooth((t - 0.6) / 0.35) : smooth((t - 8.1) / 0.6)
    }
    struct Timeline {
        let day: Day
        let now: Double
        let k: [Double]
        private(set) var ev: [Double] = []
        private(set) var ticks: [[Double]] = [[], [], []]
        init(day: Day = Day(), now: Double = rest, adapt: Bool = true, up: Double = 0.18, down: Double = 0.6) {
            self.day = day; self.now = now
            let events = (0..<3).flatMap { [day.sunrise + Double($0) * 1440, day.sunset + Double($0) * 1440] }.sorted()
            let next = events.filter { $0 > now + 0.5 }
            k = [now, next[0], min(next[0] + 172, (next[0] + next[1]) / 2), next[1], now + 1440]
            ev = [target(now, day)]
            let step = 1.0 / 120
            for i in 1...1200 {
                let target = SkyTimeScrubMotion.target(minutes(Double(i) * step), day), prev = ev[i - 1]
                let tau = target < prev ? up : down
                ev.append(adapt ? prev + (target - prev) * (1 - exp(-step / max(tau, 0.001))) : target)
            }
            let rowTimes = [day.noon, day.sunset, day.night]
            var prev = minutes(0)
            // The multiplication matches RN's 1/240 probe (rather than accumulating time).
            for i in 1...2400 {
                let t = Double(i) * (1.0 / 240), cur = minutes(t)
                for r in 0..<3 where floor((prev - rowTimes[r]) / 1440) != floor((cur - rowTimes[r]) / 1440) { ticks[r].append(t) }
                prev = cur
            }
        }
        func minutes(_ t: Double) -> Double {
            if t <= 0.9 { return k[0] }
            if t >= 7.6 { return k[4] }
            if t < 1.7 { return mix(k[0], k[1], smooth((t - 0.9) / (1.7 - 0.9))) }
            if t < 2.7 { return k[1] }
            if t < 3.5 { return mix(k[1], k[2], smooth((t - 2.7) / (3.5 - 2.7))) }
            if t < 4.1 { return k[2] }
            if t < 5.6 { let u = (t - 4.1) / (5.6 - 4.1); return mix(k[2], k[3], u * u) }
            let u = (t - 5.6) / (7.6 - 5.6)
            return mix(k[3], k[4], 1 - (1 - u) * (1 - u))
        }
        func exposure(_ t: Double) -> Double {
            let restEV = target(now, day)
            if t >= 8 || t <= 0.6 { return restEV }
            let p = clamp(t / (1.0 / 120), 0, Double(ev.count - 1)), i = Int(floor(p))
            let value = mix(ev[i], ev[min(i + 1, ev.count - 1)], p - Double(i))
            let target = SkyTimeScrubMotion.target(minutes(t), day)
            return clamp(mix(value, restEV, smooth((t - 7.6) / (8 - 7.6))), target - 0.8, target + 1.2)
        }
        func sample(_ time: Double) -> Pose {
            let t = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
            let held = smooth((t - 0.6) / (0.9 - 0.6)) * (1 - smooth((t - 7.6) / (8 - 7.6)))
            let wake = max(0, clamp(t - 0.75, 0, 0.6) - clamp((t - 7.8) * 2, 0, 0.6))
            let ages = ticks.map { list in list.last(where: { $0 <= t }).map { t - $0 } ?? 99 }
            return Pose(minutes: minutes(t), ev: exposure(t), held: held, wake: wake, hint: hintAt(time), ticks: ages)
        }
    }
}

struct SkyTimeScrubLayout {
    let width: Double, height: Double, s: Double, textScale: Double
    let controlsY: Double, controlsH: Double, rulerY: Double, horizon: Double
    let arcCentre: Double, arcAmplitude: Double, chipY: Double, hintX: Double, hintW: Double, hintH: Double
    let hintY: Double, tipY: Double, scheduleY: Double, scheduleH: Double, rowH: Double
    let largeText: Bool
    var projection: Double { arcAmplitude / s / 87 }
    init(width: Double, height: Double, top: Double, bottom: Double, fontScale: Double) {
        self.width = width; self.height = height
        let s = width / 402, topShift = max(0, top - 62 * s), available = max(0, height - bottom - 16 * s)
        self.s = s; textScale = fontScale; largeText = fontScale > 1.5
        controlsH = largeText ? (20 + 24 + 18 * fontScale) * s : 62 * s
        controlsY = min(765 * s + topShift, available - controlsH)
        let compact = min(1, max(0.64, (controlsY - top) / ((765 - 62) * s)))
        func instrument(_ y: Double) -> Double { top + (y - 62) * s * compact }
        hintX = largeText ? 16 * s : 67 * s; hintW = width - hintX * 2
        let lines = largeText ? ceil(238 * s * fontScale / (hintW - 48 * s)) : 1
        hintH = max(28 * s, (lines * 18 * fontScale + 8) * s)
        rulerY = min(instrument(481), controlsY - (3 * 48 + 44) * s)
        horizon = min(instrument(358), rulerY - 123 * s); arcCentre = horizon - 7 * s
        arcAmplitude = min(87 * s * compact, rulerY - 43 * s - arcCentre)
        scheduleY = largeText ? rulerY + 52 * s : max(min(instrument(525), controlsY - 236 * s - 8 * s), rulerY + 36 * s)
        scheduleH = max(48 * s, min(236 * s, controlsY - scheduleY - 8 * s))
        rowH = largeText ? 78 * s : max(40 * s, min(78 * s, scheduleH / 3))
        chipY = rulerY - 76 * s; hintY = rulerY - 48 * s - hintH; tipY = rulerY - 22 * s
    }
}
