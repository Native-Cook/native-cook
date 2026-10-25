import Foundation

/*
 Barometer Needle Chart — data, timeline and needle physics, a line-for-line port of the React
 Native barometerMotion.ts. Everything is a pure function of its inputs, so the loop is exact.

 The chart spans 48 hours: −24 h (yesterday) to +24 h (forecast), "now" in the middle. The dial
 maps 960 → 1060 hPa onto a 270° arc, 1010 hPa at twelve o'clock.

 autoPlay (loop = 9 s):
   0.0 – 1.4   rest: the cursor at now (1012 hPa, Change).
   1.4 – 4.0   scrub back to −14 h through the storm dip (985 hPa, Stormy), ease-in-out.
   4.0 – 4.8   hold.
   4.8 – 7.0   scrub forward to +24 h (1024 hPa, Fair), ease-in-out.
   7.0 – 8.4   back to now, ease-in-out.
   8.4 – 9.0   rest (the needle table is periodic, so t = 9 equals t = 0).
 */
enum BarometerNeedleChartMotion {
    static let loop = 9.0
    /// The chart's range in hours either side of now.
    static let span = 24.0
    static let nowHpa = 1012.0
    static let scaleMin = 960.0
    static let scaleMax = 1060.0
    /// Degrees of dial arc for the whole scale; 0° is twelve o'clock, clockwise positive.
    static let sweep = 270.0
    static let needleOmega = 10.0
    static let needleZeta = 0.38
    static let tableRate = 120.0

    // Readings every 2 h from −24 h to +24 h (25 knots), hPa. The storm bottoms out at −14 h.
    static let knots: [Double] = [
        1005, 1002, 998, 993, 988, 985, 986, 990, 995, 1000, 1004.5, 1008.5, 1012, 1014.5, 1016.5, 1018, 1019.5,
        1020.5, 1021.5, 1022.2, 1022.8, 1023.3, 1023.7, 1024, 1024,
    ]

    static let words = ["Stormy", "Rain", "Change", "Fair", "Very Dry"]
    static let wordAt: [Double] = [974, 997.5, 1011.5, 1026.5, 1047.5]
    static let bounds: [Double] = [990, 1005, 1018, 1035]
    static let trends = ["Falling", "Steady", "Rising"]

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let y = -2 * x + 2
        return 1 - y * y * y / 2
    }

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a), 0, 1)
        return t * t * (3 - 2 * t)
    }

    /// The reading at hour h (−24 … 24), a Catmull-Rom spline through the knots.
    static func pressureAt(_ h: Double) -> Double {
        let x = (clamp(h, -span, span) + span) / 2
        let i = min(Int(floor(x)), knots.count - 2)
        let f = x - Double(i)
        let p0 = knots[max(i - 1, 0)]
        let p1 = knots[i]
        let p2 = knots[i + 1]
        let p3 = knots[min(i + 2, knots.count - 1)]
        let f2 = f * f
        let f3 = f2 * f
        let a = 2 * p1 + (-p0 + p2) * f
        let b = (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2
        let c = (-p0 + 3 * p1 - 3 * p2 + p3) * f3
        return 0.5 * (a + b + c)
    }

    static func angleOf(_ hpa: Double) -> Double {
        (hpa - (scaleMin + scaleMax) / 2) / (scaleMax - scaleMin) * sweep
    }

    static func hpaOf(_ angle: Double) -> Double {
        (scaleMin + scaleMax) / 2 + angle / sweep * (scaleMax - scaleMin)
    }

    /// The chart cursor (hours from now) at loop time t.
    static func cursorAt(_ t: Double) -> Double {
        if t < 1.4 { return 0 }
        if t < 4.0 { return -14 * easeInOut(seg(t, 1.4, 4.0)) }
        if t < 4.8 { return -14 }
        if t < 7.0 { return mix(-14, span, easeInOut(seg(t, 4.8, 7.0))) }
        if t < 8.4 { return mix(span, 0, easeInOut(seg(t, 7.0, 8.4))) }
        return 0
    }

    /// A faint tremor of the movement while the reading changes (whole frequencies of the loop).
    static func jitterAt(_ t: Double) -> Double {
        let speed = clamp(abs(cursorAt(t + 0.02) - cursorAt(t - 0.02)) / 0.04 / 30, 0, 1)
        let w = 2 * Double.pi / loop
        return 0.45 * speed * (sin(37 * w * t) + 0.6 * sin(53 * w * t + 1.3))
    }

    /// The needle angle over one loop: the spring integrated over four loops, the last one kept
    /// (a linear system driven by a periodic input settles on a periodic answer).
    static let needleTable: [Double] = {
        let n = Int(loop * tableRate)
        let sub = 4
        let dt = 1 / (tableRate * Double(sub))
        var x = angleOf(pressureAt(0))
        var v = 0.0
        var out = [Double](repeating: 0, count: n)
        for pass in 0..<4 {
            for i in 0..<n {
                for s in 0..<sub {
                    let t = Double(i * sub + s) * dt
                    let target = angleOf(pressureAt(cursorAt(t)))
                    let a = -needleOmega * needleOmega * (x - target) - 2 * needleZeta * needleOmega * v
                    v += a * dt
                    x += v * dt
                }
                if pass == 3 { out[i] = x }
            }
        }
        return out
    }()

    static func sampleTable(_ t: Double) -> Double {
        let table = needleTable
        let n = table.count
        let x = (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop) * tableRate
        let i = Int(floor(x))
        let f = x - Double(i)
        return mix(table[i % n], table[(i + 1) % n], f)
    }

    /// One step of the touch-mode needle spring.
    static func stepNeedle(_ x0: Double, _ v0: Double, target: Double, dt: Double) -> (Double, Double) {
        let steps = max(1, Int(ceil(dt * 240)))
        let h = dt / Double(steps)
        var x = x0
        var v = v0
        for _ in 0..<steps {
            let a = -needleOmega * needleOmega * (x - target) - 2 * needleZeta * needleOmega * v
            v += a * h
            x += v * h
        }
        return (x, v)
    }

    /// The forecast word as a continuous index, rolling over ±2 hPa around each boundary.
    static func wordIndex(_ hpa: Double) -> Double {
        var k = 0.0
        for b in bounds { k += smooth(b - 2, b + 2, hpa) }
        return k
    }

    /// Trend against the set hand: 0 falling, 1 steady, 2 rising, continuous between.
    static func trendIndex(_ hpa: Double) -> Double {
        smooth(nowHpa - 4, nowHpa - 1.5, hpa) + smooth(nowHpa + 1.5, nowHpa + 4, hpa)
    }

    /// A reading rounded softly: rests on whole hPa, rolls to the next across the middle 30 %.
    static func softRound(_ v: Double) -> Double {
        let f = floor(v)
        return f + smooth(0.35, 0.65, v - f)
    }

    /// Clock label for an hour offset from now (now = Sat 14:00).
    static func timeLabel(_ h: Double) -> String {
        let days = ["Fri", "Sat", "Sun"]
        // JavaScript's Math.round rounds halves up.
        let r = Int(floor(h + 0.5))
        if r == 0 { return "Now" }
        let total = 24 + 14 + r
        let day = Int(clamp(floor(Double(total) / 24), 0, 2))
        let hour = ((total % 24) + 24) % 24
        return "\(days[day]) \(hour < 10 ? "0" : "")\(hour):00"
    }
}
