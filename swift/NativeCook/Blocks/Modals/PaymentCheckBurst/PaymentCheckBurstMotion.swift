import SwiftUI

/// The timeline of the screen, in seconds from the start of the loop. Everything on screen is a
/// function of one clock, so a touch and the autoPlay loop run the same motion.
enum PaymentCheckBurstMotion {
    static let loop = 10.0
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
struct PaymentCheckBurstClock {
    struct Touch {
        let start: Date
        let from: Double
        var remaining: Double { PaymentCheckBurstMotion.loop - from }
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
            return date.timeIntervalSince(auto).truncatingRemainder(dividingBy: PaymentCheckBurstMotion.loop)
        }
        return 0
    }
}

extension PaymentCheckBurstMotion {
    static let popAt = 0.6
    static let rippleFor = 0.6
    static let shrinkAt = 1.4
    static let shrinkIn = 0.55
    static let titleAt = 1.85
    static let titleIn = 0.35
    static let receiptAt = 2.0
    static let rowStagger = 0.08
    static let countAt = 3.2
    static let countFor = 0.9
    static let sheenAt = 4.4
    static let sheenFor = 1.0
    static let peelAt = 5.6
    static let peelFor = 1.0
    static let coinsAt = 5.9
    static let fadeAt = 8.7
    static let fadeIn = 0.8
    static let reducedFade = 0.3
    // Springs: the check pops with overshoot, the receipt settles.
    static let popStiffness = 260.0
    static let popDamping = 14.0
    static let receiptStiffness = 150.0
    static let receiptDamping = 20.0
    /// How far the screen rises as it fades out.
    static let rise = 80.0
}
