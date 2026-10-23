import Foundation

/// The block's clock and the deck's geometry as functions; every value on screen comes from here.
enum RecordStackDeckMotion {
    static let loop = 10.0
    /// Records in the crate; the deck wraps around, so scrolling by `count` is the same picture.
    static let count = 10
    /// Points of finger travel per record.
    static let dragUnit = 44.0
    /// The record the loop picks.
    static let picked = 3

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let t = clamp01(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
    static func easeOutQuart(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 4) }
    /// Ease-out with a small overshoot (back, s = 1.4).
    static func easeOutBack(_ x: Double) -> Double {
        let t = clamp01(x), s = 1.4
        return 1 + (s + 1) * pow(t - 1, 3) + s * pow(t - 1, 2)
    }

    /// A critically damped spring (ω 12) from x0 with velocity v0 towards `to`: value and velocity after t.
    static func settle(from x0: Double, velocity v0: Double, to target: Double, _ t: Double) -> (Double, Double) {
        let w = 12.0, d = x0 - target
        let e = exp(-w * t)
        let value = target + (d + (v0 + w * d) * t) * e
        let velocity = (v0 - w * (v0 + w * d) * t) * e
        return (value, velocity)
    }

    // MARK: Opening a record

    /// How far a record is out: 0 in the deck, 1 lifted flat, 2 its vinyl half out of the sleeve.
    static let openLength = 1.4
    static func opening(_ e: Double) -> Double {
        e < 0.8 ? easeOutBack(e / 0.8) : 1 + easeInOut((e - 0.8) / 0.6)
    }
    static func closing(_ e: Double) -> Double {
        e < 0.6 ? 2 - easeInOut(e / 0.6) : 1 - easeInOut((e - 0.6) / 0.8)
    }

    // MARK: The loop

    /// Deck position (records scrolled past the focus) at loop time t.
    static func loopScroll(_ t: Double) -> Double {
        if t < 0.4 { return 0 }
        if t < 1.1 { return easeOutBack((t - 0.4) / 0.7) }
        if t < 8.0 { return 1 + 2 * easeOut((t - 1.1) / 0.8) }
        // A long flick through seven records back to the first one: the crate has turned once.
        return 3 + 7 * easeOutQuart((t - 8.0) / 1.7)
    }

    /// The hold ring around the focus: 0 to 1.
    static func loopRing(_ t: Double) -> Double {
        if t < 1.9 || t >= 2.8 + 0.25 { return 0 }
        return t < 2.8 ? easeInOut((t - 1.9) / 0.9) : 1
    }

    static func loopOpen(_ t: Double) -> Double {
        if t < 2.8 || t >= 8.0 { return 0 }
        if t < 2.8 + openLength { return opening(t - 2.8) }
        if t < 6.6 { return 2 }
        return closing(t - 6.6)
    }

    // MARK: The deck

    /// A record's place relative to the focus, wrapped to −count/2 … count/2.
    static func offset(_ index: Int, scroll: Double) -> Double {
        let n = Double(count)
        var d = (Double(index) - scroll).truncatingRemainder(dividingBy: n)
        if d < -n / 2 { d += n }
        if d >= n / 2 { d -= n }
        return d
    }

    /// Distance of a strip's centre from the focus, in points (down positive).
    static func y(_ d: Double) -> Double {
        let a = abs(d)
        return (d < 0 ? -1 : 1) * (26 * a + 11 * a * a)
    }

    /// How open a sleeve is (the cosine of its tilt): a thin strip at the focus, more of its face further out.
    static func openness(_ d: Double) -> Double {
        let a = min(abs(d), 5)
        return 0.02 + 0.08 * a - 0.0065 * a * a
    }

    /// The sleeve's tilt about its horizontal axis, in degrees: the far edge leans away from the focus.
    static func tilt(_ d: Double) -> Double {
        let t = acos(openness(d)) * 180 / .pi
        return d >= 0 ? t : -t
    }

    static func fade(_ d: Double) -> Double { 1 - smoothstep(3.0, 4.2, abs(d)) }

    /// The deck's focus, as a fraction of the screen height.
    static let focusY = 0.42

    /// Sleeves fade out before they reach the header.
    static func topFade(_ y: Double, safeTop: Double) -> Double { smoothstep(safeTop + 40, safeTop + 120, y) }

    /// The camera's distance for the sleeves' perspective, in points.
    static let depth = 1400.0

    /// Where a sleeve of side s tilted by `tilt` degrees shows its top edge, relative to its centre.
    static func topEdge(_ s: Double, _ tilt: Double) -> Double {
        let t = tilt * .pi / 180
        return -s / 2 * cos(t) / (1 + s / 2 * sin(t) / depth)
    }

    // The open record: the sleeve lies flat at 0.8 × its deck size and moves left, the vinyl
    // (0.92 × the flat sleeve across) slides out to the right.
    static let flatScale = 0.8
    static let sleeveShift = -0.26
    static let vinylShift = 0.32
    static let vinylRadius = 0.46
}
