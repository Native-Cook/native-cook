import Foundation

/// All the motion of the Dual Range Histogram as pure functions of loop time, ported one to one from
/// `dualRangeMotion.ts`. Positions are `u`, 0 … 1 across the price axis.
enum DualRangeHistogramMotion {
    static let loop = 8.0

    static let idle = 2.0 // after the last touch
    static let returnS = 0.7

    static let thumb = 30.0
    static let trackH = 4.0
    static let trackArea = 44.0 // height of the band holding the track and the thumbs
    static let tagW = 72.0
    static let tagH = 30.0
    static let tagGap = 6.0
    static let grabR = 38.0 // pt around a thumb where a touch picks it up
    static let tapSlop = 8.0

    static let barGap = 2.0
    static let barRadius = 2.5
    static let sink = 0.58 // height of a bar outside the range
    static let liftPt = 7.0 // bars next to a held thumb rise
    static let liftSigma = 20.0

    static let squashX = 1.18
    static let squashY = 0.86
    static let contactShare = 0.3
    static let flingS = 0.12

    static let morphS = 0.42
    static let countS = 0.22
    static let tickPrice = 250.0
    static let tickMinS = 0.07

    struct Spring { let stiffness: Double; let damping: Double }
    /// response 0.42 s, damping ratio 0.68
    static let releaseSpring = Spring(stiffness: 223.8, damping: 20.35)
    static let pressSpring = Spring(stiffness: 520, damping: 30)

    private static let filmResponse = 0.42
    private static let filmRatio = 0.62

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ k: Double) -> Double { k * k * (3 - 2 * k) }
    static func easeInOutCubic(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }

    /// cubic-bezier(0.65, 0, 0.35, 1), the morph and count easing.
    static func easeMorph(_ x: Double) -> Double { bezier(x, 0.65, 0, 0.35, 1) }

    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
        func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
        var lo = 0.0, hi = 1.0, s = x
        for _ in 0..<30 {
            s = (lo + hi) / 2
            if bx(s) < x { lo = s } else { hi = s }
        }
        return by(s)
    }

    /// Up over a → b, held, down over c → d.
    static func win(_ t: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
        smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)))
    }

    /// A damped spring released at rest from `from`, s seconds ago.
    static func springAt(_ s: Double, _ from: Double, _ to: Double) -> Double {
        if s <= 0 { return from }
        let w = 2 * Double.pi / filmResponse
        let zw = filmRatio * w
        let wd = w * sqrt(1 - filmRatio * filmRatio)
        let a = from - to
        return to + exp(-zw * s) * (a * cos(wd * s) + (zw * a / wd) * sin(wd * s))
    }

    /// How far a finger's overshoot `over` (in u) moves a thumb past its limit.
    static func rubber(_ over: Double) -> Double { over * 0.55 / (1 + over / 0.09) }

    static func constrain(_ u: Double, _ lo: Double, _ hi: Double) -> Double {
        if u < lo { return lo - rubber(lo - u) }
        if u > hi { return hi + rubber(u - hi) }
        return u
    }

    /// Two thumbs pressed together push each other apart instead of crossing.
    static func shownLow(_ low: Double, _ high: Double, _ gap: Double) -> Double {
        low - contactShare * max(0, low + gap - high)
    }

    static func shownHigh(_ low: Double, _ high: Double, _ gap: Double) -> Double {
        high + contactShare * max(0, low + gap - high)
    }

    struct Film {
        let restLow: Double, restHigh: Double, lowHold: Double, lowOver: Double
        let highLimit: Double, highFinger: Double, highOver: Double

        init(restLow: Double, restHigh: Double, gap: Double) {
            self.restLow = restLow
            self.restHigh = restHigh
            lowHold = max(0.02, restLow - 0.1)
            lowOver = lowHold - 0.012
            highLimit = lowHold + gap
            highFinger = highLimit - 0.055
            highOver = min(1, restHigh + 0.02)
        }
    }

    static func filmLow(_ t: Double, _ f: Film) -> Double {
        if t < 0.85 { return f.restLow }
        if t < 1.75 { return mix(f.restLow, f.lowOver, easeInOutCubic(seg(t, 0.85, 1.75))) }
        if t < 5.9 { return springAt(t - 1.75, f.lowOver, f.lowHold) }
        if t < 6.7 { return mix(f.lowHold, f.restLow, easeInOutCubic(seg(t, 5.9, 6.7))) }
        return f.restLow
    }

    static func filmHigh(_ t: Double, _ f: Film) -> Double {
        let held = f.highLimit - rubber(f.highLimit - f.highFinger)
        if t < 2.3 { return f.restHigh }
        if t < 3.55 {
            let finger = mix(f.restHigh, f.highFinger, easeInOutCubic(seg(t, 2.3, 3.55)))
            return finger < f.highLimit ? f.highLimit - rubber(f.highLimit - finger) : finger
        }
        if t < 3.7 { return held }
        if t < 4.35 { return springAt(t - 3.7, held, f.highLimit) }
        if t < 5.4 { return mix(f.highLimit, f.highOver, easeInOutCubic(seg(t, 4.35, 5.4))) }
        if t < 6.85 { return springAt(t - 5.4, f.highOver, f.restHigh) }
        return f.restHigh
    }

    static func filmPressLow(_ t: Double) -> Double { win(t, 0.65, 0.8, 1.75, 1.9) + win(t, 5.75, 5.9, 6.7, 6.85) }
    static func filmPressHigh(_ t: Double) -> Double { win(t, 2.15, 2.3, 3.7, 3.85) + win(t, 4.2, 4.35, 5.4, 5.55) }

    static func filmGhost(_ t: Double) -> Double {
        win(t, 0.45, 0.65, 1.8, 2.0) + win(t, 1.98, 2.15, 3.75, 3.95) + win(t, 4.02, 4.2, 5.45, 5.62) + win(t, 5.6, 5.75, 6.75, 6.95)
    }

    /// 0: the ghost finger is on the low thumb, 1: on the high one.
    static func filmGhostSide(_ t: Double) -> Int { t >= 1.99 && t < 5.61 ? 1 : 0 }

    /// Odometer position of one digit column: the lowest column turns freely, the others only as the one below wraps.
    static func digitAt(_ value: Double, _ place: Double, _ lowest: Bool) -> Double {
        let v = max(0, value)
        if lowest { return (v / place).truncatingRemainder(dividingBy: 10) }
        let below = v.truncatingRemainder(dividingBy: place) / place
        return floor(v / place).truncatingRemainder(dividingBy: 10) + smooth(seg(below, 0.88, 1))
    }

    /// Listings between two positions, counting a bin by the share of it inside the range.
    static func rangeCount(_ bins: [Double], _ low: Double, _ high: Double) -> Double {
        let n = Double(bins.count)
        var sum = 0.0
        for (i, v) in bins.enumerated() {
            let share = clamp(min(high * n, Double(i) + 1) - max(low * n, Double(i)), 0, 1)
            if share > 0 { sum += v * share }
        }
        return sum
    }
}

/// The listings of Nook, an invented rental search app.
enum DualRangeHistogramData {
    static let priceMin = 1000.0
    static let priceMax = 5000.0
    static let priceStep = 50.0
    static let priceGap = 300.0
    static let priceDefault = (1800.0, 3600.0)
    static let binCount = 36

    static let placeTypes = ["Entire home", "Private room"]
    static let bedrooms = ["Any", "1", "2", "3+"]
    static let amenities: [(label: String, share: Double)] = [
        ("Pets allowed", 0.46),
        ("Furnished", 0.62),
        ("Balcony or garden", 0.38),
    ]

    private struct Shape { let peak: Double, wide: Double, total: Double }

    // Where the prices of each kind of listing cluster, as shares of the axis.
    private static let shapes: [[Shape]] = [
        [
            Shape(peak: 0.34, wide: 0.13, total: 940),
            Shape(peak: 0.26, wide: 0.08, total: 410),
            Shape(peak: 0.38, wide: 0.1, total: 460),
            Shape(peak: 0.62, wide: 0.13, total: 310),
        ],
        [
            Shape(peak: 0.13, wide: 0.08, total: 520),
            Shape(peak: 0.11, wide: 0.06, total: 340),
            Shape(peak: 0.2, wide: 0.07, total: 130),
            Shape(peak: 0.29, wide: 0.07, total: 50),
        ],
    ]

    private static func hash(_ i: Double) -> Double {
        let x = sin(i * 12.9898 + 4.1) * 43758.5453
        return x - floor(x)
    }

    /// A believable, slightly ragged price distribution: a main hump, a smaller expensive one and a seeded jitter.
    static func makeBins(type: Int, bedrooms: Int, share: Double) -> [Double] {
        let s = shapes[type][bedrooms]
        var raw: [Double] = []
        for i in 0..<binCount {
            let x = (Double(i) + 0.5) / Double(binCount)
            let main = exp(-pow((x - s.peak) / s.wide, 2))
            let tail = 0.34 * exp(-pow((x - (s.peak + 0.3)) / (s.wide * 1.5), 2))
            let jitter = 0.72 + 0.56 * hash(Double(i + type * 40 + bedrooms * 7))
            raw.append((main + tail + 0.025) * jitter)
        }
        let sum = raw.reduce(0, +)
        return raw.map { $0 / sum * s.total * share }
    }
}
