import SwiftUI

/// The timeline of the screen, in seconds from the start of the loop. Everything on screen is a
/// function of one clock, so a touch and the autoPlay loop run the same motion.
enum TaskToastConfettiMotion {
    static let loop = 9.0
    /// A touch starts here, a little before the tick.
    static let tapAt = 0.95
    /// Seconds a touch is left alone before the autoPlay loop starts again.
    static let resumeAfter = 3.0

    static let tickAt = 1.1
    static let tickIn = 0.3
    static let strikeIn = 0.28
    static let hintOutAt = 1.1
    static let hintOutIn = 0.25
    static let nextAt = 1.5
    static let toastAt = 2.0
    /// The confetti bursts out of the toast as it lands.
    static let confettiAt = 2.12
    static let toastAwayAt = 5.8
    static let toastAwayIn = 0.8
    static let nextAwayAt = 5.8
    static let nextAwayIn = 0.6
    static let untickAt = 6.6
    static let untickIn = 0.5
    static let hintBackAt = 7.0
    static let hintBackIn = 0.45
    /// The first hint bobs by this much, once every period; the loop holds a whole number of periods.
    static let bob = 3.0
    static let bobPeriod = 0.9
    /// The toast drops on a spring; the next hint and the mascot pop on a softer one.
    static let toastStiffness = 300.0
    static let toastDamping = 18.0
    static let popStiffness = 260.0
    static let popDamping = 15.0
    static let reducedFade = 0.3

    /// 0 before `from`, 1 after `to`, a straight line between.
    static func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
        min(1, max(0, (t - from) / (to - from)))
    }

    static func easeOut(_ p: Double) -> Double { 1 - (1 - p) * (1 - p) * (1 - p) }

    static func easeIn(_ p: Double) -> Double { p * p * p }

    /// A spring's position in closed form, from 0 at `elapsed` = 0 towards 1, for a mass of 1.
    static func spring(_ elapsed: Double, stiffness: Double, damping: Double) -> Double {
        if elapsed <= 0 { return 0 }
        let omega = stiffness.squareRoot()
        let zeta = damping / (2 * omega)
        let decay = exp(-zeta * omega * elapsed)
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return 1 - decay * (cos(damped * elapsed) + (zeta * omega) / damped * sin(damped * elapsed))
    }

    /// A number in [0, 1) that is always the same for the same pair: the confetti table is built from it.
    static func hash(_ index: Int, _ salt: Int) -> Double {
        let x = sin(Double(index) * 127.1 + Double(salt) * 311.7) * 43758.5453
        return x - x.rounded(.down)
    }
}

/// The screen's clock, in seconds from 0 to the loop. With autoPlay it runs by itself; a touch takes
/// over, jumps to `tapAt`, runs to the end and rests there; autoPlay then starts again after a pause.
struct TaskToastConfettiClock {
    var touch: Date?
    var auto: Date?

    private typealias M = TaskToastConfettiMotion

    /// Seconds from a touch to the end of the loop.
    static let remaining = M.loop - M.tapAt

    var isPlaying: Bool { touch != nil || auto != nil }

    func busy(at date: Date) -> Bool {
        touch.map { date.timeIntervalSince($0) < Self.remaining } ?? false
    }

    func time(at date: Date) -> Double {
        if let touch {
            let elapsed = date.timeIntervalSince(touch)
            if elapsed < Self.remaining { return M.tapAt + elapsed }
        }
        if let auto, date >= auto {
            return date.timeIntervalSince(auto).truncatingRemainder(dividingBy: M.loop)
        }
        return 0
    }
}
