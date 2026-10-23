import Foundation

/// The complete mechanical contract for the Pinch to Summarize block.
///
/// Values mirror `react-native/src/blocks/gestures/pinch-to-summarize/motion.ts`.
/// Durations are milliseconds unless a source value is explicitly a ratio or speed.
enum PinchToSummarizeConstants {
    enum Pinch {
        static let fullIn: Double = 0.5
        static let fullOut: Double = 1.8
        static let commitAt: Double = 0.32
        static let restoreAt: Double = 0.7
        static let deceleration: Double = 0.998
        static let rubber: Double = 0.55
        static let follow: Double = 0.6
        static let velocityToProgress: Double = 1.6
    }

    enum Springs {
        struct Spring {
            let damping: Double
            let stiffness: Double
            let mass: Double
        }

        static let collapse = Spring(damping: 22, stiffness: 210, mass: 1)
        static let restore = Spring(damping: 30, stiffness: 230, mass: 1)
        static let lift = Spring(damping: 24, stiffness: 180, mass: 1)
        static let drift = Spring(damping: 20, stiffness: 240, mass: 1)
        static let pop = Spring(damping: 12, stiffness: 260, mass: 0.7)
    }

    enum Timing {
        static let thinkMs: Double = 1400
        static let thinkIn: Double = 260
        static let rimOut: Double = 420
        static let rimFlashIn: Double = 120
        static let rimFlashOut: Double = 520
        static let rimLetGo: Double = 300
        static let tick: Double = 16
        static let lineStagger: Double = 70
        static let lineMs: Double = 420
        static let lineRise: Double = 8
        static let summaryOutStart: Double = 0.95
        static let toolbarFadeSpan: Double = 0.3
        static let rimTurnMs: Double = 1600
        static let breathe: Double = 0.012
        static let breathePeriod: Double = 1600
        static let bloomMs: Double = 900
        static let bloomReach: Double = 70
        static let sweepMs: Double = 700
        static let sweepDelay: Double = 200
        static let ghostOffset: Double = 6
        static let ghostAlpha: Double = 0.35
        static let discFrom: Double = 0.8
        static let motes: Double = 22
        static let moteSpeedMin: Double = 18
        static let moteSpeedMax: Double = 40
        static let pressScale: Double = 0.97
        static let scanMs: Double = 1100
        static let shimmerMs: Double = 1300
        static let fieldBreath: Double = 0.015
        static let fieldPeriod: Double = 11000
        static let fingerFade: Double = 140
        static let maxFrameMs: Double = 250
        static let reducedFade: Double = 180
    }

    enum Rim {
        static let pinchMax: Double = 0.55
        static let armedBoost: Double = 0.3
        static let think: Double = 1
        static let scan: Double = 0.9
    }

    enum Autoplay {
        static let resumeDelayMs: Double = 3500
        static let qaHoldAt: Double = 6500
        static let pinchInStart: Double = 1000
        static let pinchInEnd: Double = 1900
        static let pinchInScale: Double = 0.52
        static let pinchOutStart: Double = 8200
        static let pinchOutEnd: Double = 9050
        static let pinchOutScale: Double = 1.85
        static let resumeReading: Double = 0
        static let resumeSummary: Double = 7000
        static let spreadIn: Double = 150
        static let spreadOut: Double = 70
    }

    static let loopMS: Double = 11000
    static let qaHold: String? = nil
    static let qaReplica: Bool = false
}
