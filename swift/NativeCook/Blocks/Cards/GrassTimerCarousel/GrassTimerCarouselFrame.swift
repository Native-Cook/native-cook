import Foundation

/// Reference-space geometry for the local aperture layer. It intentionally uses `Double` only so
/// the timeline can be regression-tested without SwiftUI or UIKit.
struct GrassTimerCarouselRect: Equatable {
    var x: Double
    var y: Double
    var width: Double
    var height: Double
    var cornerRadius: Double

    static func interpolate(from: Self, to: Self, progress: Double) -> Self {
        let p = GrassTimerCarouselMath.clamp(progress)
        return Self(
            x: from.x + (to.x - from.x) * p,
            y: from.y + (to.y - from.y) * p,
            width: from.width + (to.width - from.width) * p,
            height: from.height + (to.height - from.height) * p,
            cornerRadius: from.cornerRadius + (to.cornerRadius - from.cornerRadius) * p
        )
    }
}

/// All values consumed by the SwiftUI hero are a single deterministic frame.
struct GrassTimerCarouselFrame: Equatable {
    var phase: GrassTimerCarouselPhase
    var selectedCard: Int
    var aperture: GrassTimerCarouselRect
    var morph: Double
    var pressProgress: Double
    var chipScale: Double
    var imageParallax: Double
    var secondCardOffset: Double
    var secondCardOpacity: Double
    var copyOpacity: Double
    var timerOpacity: Double
    var ringOpacity: Double
    var ringProgress: Double
    var landingHighlightOpacity: Double
    var remainingSeconds: Int
    var activationSequence: Int
    var carouselOffset: Double
    var neighborCardX: Double
    var selectionSequence: Int
}

enum GrassTimerCarouselPhase: Equatable {
    case idle
    case pressing
    case morphing
    case active
    case returning
}

enum GrassTimerCarouselTiming {
    static let loop = 8.0
    static let quickTapDuration = 0.16
    static let holdDuration = 0.65
    static let morphDuration = 0.80
    static let returnDuration = 0.75
    static let reducedFadeDuration = 0.15
    static let grassDuration = 8 * 60 * 60
    static let quietDuration = 24 * 60 * 60
    static let carouselSpacing = 249.0
    static let carouselSettleDuration = 0.65

    static let restingCard = GrassTimerCarouselRect(x: 14, y: 0, width: 232, height: 327, cornerRadius: 36)
    static let timerAperture = GrassTimerCarouselRect(x: 50, y: 50, width: 302, height: 302, cornerRadius: 151)
}

enum GrassTimerCarouselMath {
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func clamp(_ value: Double, lower: Double, upper: Double) -> Double {
        min(upper, max(lower, value))
    }

    /// The specified cubic-bezier curves are evaluated with a tiny deterministic binary solve for
    /// x. Keeping this in Foundation makes the recorded demo and the gesture path agree exactly.
    static func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let x = clamp(progress)
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let t = (low + high) * 0.5
            let sample = bezier(t, p1: x1, p2: x2)
            if sample < x { low = t } else { high = t }
        }
        return bezier((low + high) * 0.5, p1: y1, p2: y2)
    }

    static func strongOut(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    }

    static func strongInOut(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    }

    static func springBack(_ elapsed: Double, initial: Double) -> Double {
        guard elapsed > 0 else { return initial }
        // omega 15.7, damping ratio 0.8: analytic underdamped return to zero.
        let omega = 15.7
        let damping = 0.8
        let damped = omega * sqrt(1 - damping * damping)
        return max(0, initial * exp(-damping * omega * elapsed) * (cos(damped * elapsed) + damping * omega / damped * sin(damped * elapsed)))
    }

    private static func bezier(_ t: Double, p1: Double, p2: Double) -> Double {
        let inverse = 1 - t
        return 3 * inverse * inverse * t * p1 + 3 * inverse * t * t * p2 + t * t * t
    }
}
