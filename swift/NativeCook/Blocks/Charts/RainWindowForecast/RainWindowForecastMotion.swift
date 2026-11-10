import Foundation

/// The reference's analytic 24-point stroke. All timing calculations use JS-sized Doubles.
enum RainWindowForecastMotion {
    static let loop = 10.0
    static let points = 24
    static let regrow = 5.8
    struct Point {
        var x = 0.0
        var y = 0.0
        var age = -1.0
    }
    struct Frame {
        var wipe = [Point](repeating: Point(), count: points)
        var finger = 0.0
        var fx = 0.0
        var fy = 0.0
        var fingerPress = 0.0
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func stroke(_ s: Double) -> (x: Double, y: Double) {
        (0.18 + 0.64 * s, 0.42 + 0.07 * sin(s * .pi * 2) - 0.04 * s)
    }
    static let passage: [Double] = (0..<points).map { i in
        let s = Double(i) / Double(points - 1)
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<14 {
            let mid = (lo + hi) / 2
            if easeInOut(mid) < s { lo = mid } else { hi = mid }
        }
        return 1 + 1.4 * lo
    }
    static func frameAt(_ t: Double, _ w: Double, _ h: Double) -> Frame {
        var f = Frame()
        for i in 0..<points {
            let p = stroke(Double(i) / Double(points - 1))
            f.wipe[i] = Point(x: p.x * w, y: p.y * h, age: t >= passage[i] ? t - passage[i] : -1)
        }
        let p = stroke(easeInOut(seg(t, 1, 2.4)))
        f.fx = p.x * w
        f.fy = p.y * h
        f.finger = easeOut(seg(t, 0.82, 0.97)) * (1 - easeOut(seg(t, 2.4, 2.58)))
        f.fingerPress = f.finger
        return f
    }
}
