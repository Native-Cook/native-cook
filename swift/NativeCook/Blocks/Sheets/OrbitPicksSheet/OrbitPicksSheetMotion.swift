import SwiftUI

/// Seconds since the banner was tapped. The sheet rises first, so the loader starts a little in.
enum OrbitPicksSheetClock {
    static let sheetRise = 0.45
    static let badgeIn = sheetRise
    static let badgePop = 0.7
    static let ringIn = 0.6
    static let ringDraw = 1.0
    static let captionIn = 0.9
    static let captionOut = 3.9
    static let discIn = 1.3
    static let discStagger = 0.05
    static let discFly = 0.9
    static let suckAt = 4.0
    static let suckFor = 0.6
    static let suckStagger = 0.015
    static let ringOut = 4.0
    static let badgeUp = 4.3
    static let badgeUpFor = 0.7
    static let resultIn = 4.9
    static let resultFor = 0.7
    /// The clock stops here; everything after it is a still result.
    static let end = 5.7
    /// Seconds one disc takes for a full turn at speed 1.
    static let period = 3.0

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double { min(1, max(0, (value - from) / (to - from))) }
    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }
    static func easeIn(_ t: Double) -> Double { t * t * t }

    /// 0 → 1 with a small overshoot, for a pop.
    static func pop(_ t: Double) -> Double {
        let c = 1.70158
        return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
    }
}
