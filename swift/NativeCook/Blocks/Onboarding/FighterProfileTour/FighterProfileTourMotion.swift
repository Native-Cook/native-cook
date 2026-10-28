import Foundation

/// Scene time and easing of the Fighter Profile Tour, ported value for value from `motion.ts`.
/// The progress (0...1) is the one clock; every layer reads the scene time derived from it.
enum FighterProfileTourMotion {
    static let loop = 10.0
    static let width = 393.0

    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        max(low, min(high, value))
    }

    static func mix(_ a: Double, _ b: Double, _ progress: Double) -> Double {
        a + (b - a) * progress
    }

    /// `cubic-bezier(x1, y1, x2, y2)` solved by 17 bisection steps, as in motion.ts.
    static func curve(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var low = 0.0
        var high = 1.0
        var t = x
        for _ in 0..<17 {
            let q = 1 - t
            let position = 3 * q * q * t * x1 + 3 * q * t * t * x2 + t * t * t
            if position < x { low = t } else { high = t }
            t = (low + high) / 2
        }
        let q = 1 - t
        return 3 * q * q * t * y1 + 3 * q * t * t * y2 + t * t * t
    }

    /// The strong ease-out (0.23, 1, 0.32, 1) used for every state change.
    static func easeOut(_ x: Double) -> Double { curve(x, 0.23, 1, 0.32, 1) }

    static func ramp(_ time: Double, _ start: Double, _ end: Double, moving: Bool = false) -> Double {
        let t = clamp((time - start) / (end - start))
        return moving ? curve(t, 0.77, 0, 0.175, 1) : curve(t, 0.23, 1, 0.32, 1)
    }

    static func sceneTime(_ progress: Double, reduced: Bool) -> Double {
        let p = clamp(progress)
        let t = p < 0.84 ? p : ((1 - p) / 0.16) * 0.68
        return reduced ? (t < 0.22 ? 0 : t < 0.575 ? 0.4 : 0.72) : t
    }

    static func stepAt(_ time: Double) -> Int {
        time < 0.22 ? 1 : time < 0.575 ? 2 : 3
    }

    struct Actor {
        var x: Double
        var y: Double
        var scale: Double
        var clip: Double
    }

    static func actorAt(_ time: Double) -> Actor {
        let hero = ramp(time, 0.12, 0.32, moving: true)
        let pinned = ramp(time, 0.47, 0.68, moving: true)
        return Actor(
            x: mix(mix(205, 77, hero), 172, pinned),
            y: mix(mix(157, 200, hero), 42, pinned),
            scale: mix(mix(0.455, 0.835, hero), 0.16, pinned),
            clip: 310 - 79 * (1 - ramp(time, 0.12, 0.185))
        )
    }

    /// A cancelled interaction always settles to a complete, readable composition.
    static func nearestHold(_ progress: Double, velocity: Double = 0) -> Double {
        let projectedDistance = ((velocity / 1000) * 0.998) / (1 - 0.998)
        let projected = clamp(sceneTime(progress, reduced: false) + projectedDistance, 0, 0.72)
        return projected < 0.20 ? 0 : projected < 0.56 ? 0.4 : 0.72
    }
}
