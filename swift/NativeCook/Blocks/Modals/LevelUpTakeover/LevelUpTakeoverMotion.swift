import SwiftUI

/// The timeline of the screen, in seconds from the start of the loop. Everything on screen is a
/// function of one clock, so a touch and the autoPlay loop run the same motion.
enum LevelUpTakeoverMotion {
    static let loop = 9.0
    /// A touch starts here.
    static let tapAt = 0.5
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

    /// A number in [0, 1) that is always the same for the same pair: the particle tables are built from it.
    static func hash(_ index: Int, _ salt: Int) -> Double {
        let x = sin(Double(index) * 127.1 + Double(salt) * 311.7) * 43758.5453
        return x - x.rounded(.down)
    }
}

/// The screen's clock, in seconds from 0 to the loop. With autoPlay it runs by itself; a touch takes
/// over, jumps to a time of the loop, runs to the end and rests there; autoPlay then starts again
/// after a pause. The state at 0 and at the loop's end is the same.
struct LevelUpTakeoverClock {
    struct Touch {
        let start: Date
        let from: Double
        var remaining: Double { LevelUpTakeoverMotion.loop - from }
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
            return date.timeIntervalSince(auto).truncatingRemainder(dividingBy: LevelUpTakeoverMotion.loop)
        }
        return 0
    }
}

extension LevelUpTakeoverMotion {
    static let burstAt = 0.6
    static let dotOutIn = 0.2
    static let glowAt = 1.0
    static let glowFor = 0.6
    static let titleAt = 1.5
    static let titleIn = 0.4
    static let statsAt = 1.9
    static let statsCount = 0.6
    static let statsStagger = 0.1
    static let ctaAt = 2.5
    static let linkAt = 3.1
    static let linkIn = 0.4
    static let shineAt = 3.3
    static let shineFor = 0.6
    static let secondBurstAt = 4.0
    static let secondBurstCount = 18
    static let pressAt = 6.0
    static let pressDown = 0.15
    static let pressUp = 0.25
    static let outAt = 7.0
    static let outIn = 1.0
    static let dotBackAt = 8.2
    static let dotBackIn = 0.4
    static let reducedFade = 0.3
    /// The badge bursts out on a bouncy spring: damping ratio 0.55 is 2 * 0.55 * sqrt(stiffness).
    static let badgeStiffness = 220.0
    static let badgeDamping = 16.3
    static let badgeFrom = 0.2
    static let ctaStiffness = 200.0
    static let ctaDamping = 22.0
    static let slide = 16.0
    static let ctaSlide = 40.0
    static let outScale = 0.96
    static let pressScale = 0.96
}
