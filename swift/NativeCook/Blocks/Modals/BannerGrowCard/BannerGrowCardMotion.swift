import SwiftUI

/// The timeline of the screen, in seconds from the start of the loop. Everything on screen is a
/// function of one clock, so a touch and the autoPlay loop run the same motion.
enum BannerGrowCardMotion {
    static let loop = 9.0
    /// A touch starts here.
    static let tapAt = 0.7
    /// Seconds a touch is left alone before the autoPlay loop starts again.
    static let resumeAfter = 3.0

    /// 0 before `from`, 1 after `to`, a straight line between.
    static func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
        min(1, max(0, (t - from) / (to - from)))
    }

    static func easeOut(_ p: Double) -> Double { 1 - (1 - p) * (1 - p) * (1 - p) }

    static func easeIn(_ p: Double) -> Double { p * p * p }

    static func easeInOut(_ p: Double) -> Double {
        p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
    }

    /// A spring's position in closed form, from 0 at `elapsed` = 0 towards 1, for a mass of 1.
    static func spring(_ elapsed: Double, stiffness: Double, damping: Double) -> Double {
        if elapsed <= 0 { return 0 }
        let omega = stiffness.squareRoot()
        let zeta = damping / (2 * omega)
        let decay = exp(-zeta * omega * elapsed)
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return 1 - decay * (cos(damped * elapsed) + (zeta * omega) / damped * sin(damped * elapsed))
    }
}

/// The screen's clock, in seconds from 0 to the loop. With autoPlay it runs by itself; a touch takes
/// over, jumps to a time of the loop, runs to the end and rests there; autoPlay then starts again
/// after a pause. The state at 0 and at the loop's end is the same.
struct BannerGrowCardClock {
    struct Touch {
        let start: Date
        let from: Double
        var remaining: Double { BannerGrowCardMotion.loop - from }
    }

    var touch: Touch?
    var auto: Date?

    var isPlaying: Bool { touch != nil || auto != nil }

    func busy(at date: Date) -> Bool {
        touch.map { date.timeIntervalSince($0.start) < $0.remaining } ?? false
    }

    func time(at date: Date) -> Double {
        if let touch {
            let elapsed = date.timeIntervalSince(touch.start)
            if elapsed < touch.remaining { return touch.from + elapsed }
        }
        if let auto, date >= auto {
            return date.timeIntervalSince(auto).truncatingRemainder(dividingBy: BannerGrowCardMotion.loop)
        }
        return 0
    }
}

extension BannerGrowCardMotion {
    static let outlineAt = 0.8
    static let outlineIn = 0.5
    static let bannerAt = 1.3
    static let bannerIn = 0.6
    static let warmAt = 1.9
    static let warmIn = 0.8
    static let growAt = 2.7
    static let linesOutAt = 2.9
    static let linesOutIn = 0.25
    static let captionAt = 3.35
    static let captionIn = 0.45
    static let breatheAt = 3.9
    static let breatheFor = 2.2
    static let collapseAt = 6.1
    static let collapseIn = 0.8
    static let bannerOutAt = 6.9
    static let bannerOutIn = 0.5
    static let outlineOutAt = 7.0
    static let outlineOutIn = 0.6
    static let reducedFade = 0.3
    /// The card grows on a spring with a damping ratio of 0.9: 2 * 0.9 * sqrt(stiffness).
    static let growStiffness = 110.0
    static let growDamping = 18.9
    static let breathe = 0.02
    /// The photo shows through the warming banner this much before the card grows.
    static let peek = 0.4
}
