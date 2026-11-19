import Foundation

// Product Relight: one 16-second film, every value a pure function of the loop time t. It is the tour of the Light
// tool: every control on the screen is used once, in the order a person would.
//
//   0.00 –  1.00  poster: the watch on a white sweep, the Studio softbox high on the left
//   1.00 –  3.80  the softbox is dragged over the top and round to the right; the highlights and the shadow follow
//   4.10          Golden hour is tapped; 4.2 – 5.0 the paper warms and the sun drops low
//   4.40 –  7.00  the low sun is dragged across from the right to the left: the shadow swings over the floor
//   7.30          Moody blue is tapped; 7.4 – 8.2 the sweep goes slate blue, a cool key, a warm hair light
//   8.30 –  9.70  the intensity slider goes up and back
//  10.00          Compare is tapped: the divider slides in (10.0 – 10.6) and is dragged right and left (10.7 – 11.75)
//  11.90          Compare is tapped again: the divider slides away (11.9 – 12.5)
//  12.70          Studio is tapped; 12.8 – 13.6 the rig turns back, 12.9 – 14.8 the softbox is dragged home
//  15.00 – 16.00  rest = the poster
enum ProductRelightStudioMotion {
    static let loop: Double = 16
    static let idle = 2.0
    static let returnDur = 1.2
    static let rigDur = 0.8

    static let lightReach = 0.42
    static let level = 0.5
    static let maxIntensity = 1.6

    static let drag1A = 1.0, drag1B = 3.8
    static let golden = 4.1, goldenA = 4.2, goldenB = 5.0
    static let drag2A = 4.4, drag2B = 7.0
    static let moody = 7.3, moodyA = 7.4, moodyB = 8.2
    static let levelUpA = 8.3, levelUpB = 8.9, levelDownA = 9.1, levelDownB = 9.7
    static let compareOn = 10.0, splitInB = 10.6
    static let splitRightA = 10.7, splitRightB = 11.2, splitLeftA = 11.25, splitLeftB = 11.75
    static let compareOff = 11.9, splitOutB = 12.5
    static let studio = 12.7, studioA = 12.8, studioB = 13.6
    static let drag3A = 12.9, drag3B = 14.8

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * clamp01(x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { easeInOutSine((t - a) / (b - a)) }

    /// Cubic bezier (0.65, 0, 0.35, 1), the RN `EASE_IN_OUT`.
    static func easeBezier(_ x: Double) -> Double {
        func bx(_ u: Double) -> Double { 3 * (1 - u) * (1 - u) * u * 0.65 + 3 * (1 - u) * u * u * 0.35 + u * u * u }
        func by(_ u: Double) -> Double { 3 * (1 - u) * u * u + u * u * u }
        let xx = clamp01(x)
        var lo = 0.0, hi = 1.0
        for _ in 0..<30 { let m = (lo + hi) / 2; if bx(m) < xx { lo = m } else { hi = m } }
        return by((lo + hi) / 2)
    }

    // MARK: the three rigs

    static let presetNames = ["Studio", "Golden hour", "Moody blue"]
    static let presetHints = ["A large softbox on white paper.", "A low, warm sun and long shadows.", "Slate paper, a cool key, a warm edge."]

    /// Each rig: key colour (3), key power, light height, hair light colour (3), its power, ambient (3), paper (3, sRGB),
    /// shadow softness.
    static let rigs: [[Double]] = [
        [1.0, 0.97, 0.93, 6.2, 0.8, 0.9, 0.94, 1.0, 0.5, 0.25, 0.25, 0.26, 0.93, 0.92, 0.9, 1.0],
        [1.0, 0.8, 0.58, 6.0, 0.34, 1.0, 0.88, 0.7, 0.6, 0.12, 0.125, 0.15, 0.93, 0.88, 0.8, 0.55],
        [0.86, 0.9, 1.0, 5.0, 0.6, 1.0, 0.78, 0.55, 0.9, 0.05, 0.055, 0.07, 0.22, 0.26, 0.31, 0.85],
    ]

    static func weightsAt(_ t: Double) -> [Double] {
        let a = seg(t, goldenA, goldenB), b = seg(t, moodyA, moodyB), c = seg(t, studioA, studioB)
        return [1 - a + c, a * (1 - b), b * (1 - c)]
    }

    static func rigMix(_ w: [Double]) -> [Double] {
        let s = max(1e-4, w[0] + w[1] + w[2])
        return (0..<16).map { i in (rigs[0][i] * w[0] + rigs[1][i] * w[1] + rigs[2][i] * w[2]) / s }
    }

    // MARK: the light's path (x right, y up, photo widths)

    private static let keys: [[Double]] = [
        [0.0, -0.26, 0.26], [1.0, -0.26, 0.26], [2.4, 0.02, 0.36], [3.8, 0.3, 0.12], [4.4, 0.3, 0.12], [5.4, 0.34, -0.1],
        [7.0, -0.34, 0.02], [12.9, -0.34, 0.02], [13.9, -0.36, 0.14], [14.8, -0.26, 0.26], [16.0, -0.26, 0.26],
    ]

    private static func keyAt(_ t: Double, _ c: Int) -> Double {
        var i = 0
        while i < keys.count - 2 && t >= keys[i + 1][0] { i += 1 }
        let k0 = keys[max(0, i - 1)], k1 = keys[i], k2 = keys[i + 1], k3 = keys[min(keys.count - 1, i + 2)]
        let x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]))
        let p0 = k0[c], p1 = k1[c], p2 = k2[c], p3 = k3[c]
        var m1 = (p2 - p0) / 2, m2 = (p3 - p1) / 2
        if (p1 - p0) * (p2 - p1) <= 0 { m1 = 0 }
        if (p2 - p1) * (p3 - p2) <= 0 { m2 = 0 }
        let x2 = x * x, x3 = x2 * x
        return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2
    }

    static func lightAt(_ t: Double) -> (Double, Double) { (keyAt(t, 1), keyAt(t, 2)) }

    private static func hold(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp01((t - a + 0.2) / 0.2) * clamp01((b - t + 0.15) / 0.2)
    }

    static func grabAt(_ t: Double) -> Double {
        max(hold(t, drag1A, drag1B), hold(t, drag2A, drag2B), hold(t, drag3A, drag3B))
    }

    static func splitGrabAt(_ t: Double) -> Double { hold(t, splitRightA, splitLeftB) }

    static func compareDipAt(_ t: Double) -> Double {
        max(clamp01(1 - abs(t - compareOn) / 0.16), clamp01(1 - abs(t - compareOff) / 0.16))
    }

    static func levelAt(_ t: Double) -> Double {
        level + 0.35 * seg(t, levelUpA, levelUpB) * (1 - seg(t, levelDownA, levelDownB))
    }

    static func splitAt(_ t: Double) -> Double {
        let inK = seg(t, compareOn, splitInB)
        let right = seg(t, splitRightA, splitRightB)
        let left = seg(t, splitLeftA, splitLeftB)
        let out = seg(t, compareOff, splitOutB)
        return (0.5 * inK + 0.2 * right - 0.35 * left) * (1 - out)
    }
}
