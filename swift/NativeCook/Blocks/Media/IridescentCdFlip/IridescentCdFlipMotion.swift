import Foundation

/// The block's clock: the disc's turn and the captions as functions of time.
enum IridescentCdFlipMotion {
    static let loop = 8.0
    /// The two flips of the loop: to the data side, then on round to the printed side.
    static let flips = [1.2, 4.2]
    /// The camera's distance for the disc's perspective, in points.
    static let depth = 1000.0

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    // The flip spring: ω 6.5, ζ 0.6 (about 9 % overshoot, a wobble on landing).
    private static let springA = 0.6 * 6.5
    private static let springB = 6.5 * (1 - 0.6 * 0.6).squareRoot()

    /// A spring from x0 (velocity v0) to `target`: value and velocity after t.
    static func spring(from x0: Double, velocity v0: Double, to target: Double, _ t: Double) -> (Double, Double) {
        let a = springA, b = springB
        let c1 = x0 - target, c2 = (v0 + a * c1) / b
        let e = exp(-a * t)
        let value = target + e * (c1 * cos(b * t) + c2 * sin(b * t))
        let velocity = e * ((-a * c1 + b * c2) * cos(b * t) + (-a * c2 - b * c1) * sin(b * t))
        return (value, velocity)
    }

    /// The idle sway: ±4° once a loop, so the light keeps sliding across the disc.
    static func sway(_ t: Double) -> Double { 4 * sin(2 * .pi * t / loop) }

    /// The disc's turn in degrees at loop time t (0 printed side, 180 data side, 360 printed again).
    static func loopAngle(_ t: Double) -> Double {
        // Each flip springs from the previous rest to the next; it has settled long before the next one.
        var angle = 0.0
        for f in flips where t >= f { angle += 180 + spring(from: -180, velocity: 0, to: 0, t - f).0 }
        return angle + sway(t)
    }

    /// The side the loop is heading for at t, and since when (the start of the last flip).
    static func loopCaption(_ t: Double) -> (printed: Bool, since: Double) {
        if t >= flips[1] { return (true, t - flips[1]) }
        if t >= flips[0] { return (false, t - flips[0]) }
        return (true, 10)
    }

    /// Which side faces the viewer for a turn (degrees): true for the printed side.
    static func printedSide(_ angle: Double) -> Bool { cos(angle * .pi / 180) >= 0 }

    // MARK: Captions

    static let glyphs = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*/<>")

    /// A small integer hash (PCG), the same in every stack: 0 to 1.
    static func hash(_ x: Int, _ salt: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x) &* 747_796_405 &+ UInt32(truncatingIfNeeded: salt) &* 2_891_336_453 &+ 1
        h = ((h >> ((h >> 28) &+ 4)) ^ h) &* 277_803_737
        h = (h >> 22) ^ h
        return Double(h) / 4_294_967_295
    }

    /// `text` typed in over 0.45 s after a change: unresolved letters flicker through random glyphs
    /// (a new one every 1/30 s), resolving left to right.
    static func glitch(_ text: String, since e: Double, salt: Int) -> String {
        guard e < 0.45 else { return text }
        let chars = Array(text)
        let frame = Int(max(0, e) * 30)
        var out = ""
        for (i, ch) in chars.enumerated() {
            let resolve = 0.45 * Double(i) / Double(max(1, chars.count))
            if e >= resolve || ch == " " {
                out.append(ch)
            } else {
                out.append(glyphs[Int(hash(frame * 131 + i, salt) * Double(glyphs.count)) % glyphs.count])
            }
        }
        return out
    }
}
