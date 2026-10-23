import Foundation

enum EventPassSharePhase: Equatable {
    case front
    case dragging
    case settling
    case turningBack
    case back
    case turningFront
}

struct EventPassShareFrame: Equatable {
    var phase: EventPassSharePhase
    var flipAngle: Double
    var tiltX: Double
    var tiltY: Double
    var pressScale: Double
    var frontOpacity: Double
    var backOpacity: Double
    var quietCrossfade: Bool
    var copyConfirmed: Bool

    static let resting = Self(
        phase: .front,
        flipAngle: 0,
        tiltX: 0,
        tiltY: 0,
        pressScale: 1,
        frontOpacity: 1,
        backOpacity: 0,
        quietCrossfade: false,
        copyConfirmed: false
    )
}

enum EventPassShareTiming {
    static let loop = 8.0
    static let foilStart = 0.8
    static let foilEnd = 3.0
    static let foilTiltY = 11.0
    static let foilTiltX = -5.0
    static let turnBackStart = 3.3
    static let demoTurnDuration = 0.75
    static let turnFrontStart = 5.4
    static let nodStart = 6.15
    static let nodEnd = 6.9
    static let nodTiltY = -4.5
    static let nodTiltX = 1.5
    static let flipDuration = 0.75
    static let quietFadeDuration = 0.15
    static let pressDuration = 0.09
    static let settleDuration = 0.70
    static let resumeDelay = 2.0
    static let copyConfirmationDuration = 1.5
}

enum EventPassShareSheetDismiss {
    static let activeOffset = 8.0
    static let commitShare = 0.25
    static let commitVelocity = 800.0
    static let rubber = 0.55
    static let rubberFalloff = 120.0
    static let closeDuration = 0.30
    static let closeDampingRatio = 1.0
    static let returnDuration = 0.40
    static let returnDampingRatio = 0.80
    static let overshoot = 72.0

    static func rubberBand(_ over: Double) -> Double {
        (over * rubber) / (1 + over / rubberFalloff)
    }
}

struct EventPassShareFoilUniform: Equatable {
    var tiltX: Double
    var shadeY: Double
    var amount: Double
    var scale: Double

    static func sample(tiltX: Double, tiltY: Double, flipAngle: Double, scale: Double) -> Self {
        let turn = EventPassShareMath.clamp(flipAngle, lower: 0, upper: 90)
        let shadeY = tiltY + turn * 0.2
        let tilt = min(1, hypot(tiltX / 6, tiltY / 7.5))
        let lit = min(1, tilt + sin(turn * .pi / 180) * 0.8)
        let amount = lit * lit * (3 - 2 * lit)
        return Self(tiltX: tiltX, shadeY: shadeY, amount: amount, scale: scale)
    }
}

enum EventPassShareMath {
    static func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        min(upper, max(lower, value))
    }

    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * clamp(progress)
    }

    static func strongOut(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    }

    static func strongInOut(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    }

    static func spring(elapsed: Double, position: Double, velocity: Double, omega: Double = 18, damping: Double = 0.82) -> Double {
        guard elapsed > 0 else { return position }
        let damped = omega * sqrt(1 - damping * damping)
        let coefficient = (velocity + damping * omega * position) / damped
        return exp(-damping * omega * elapsed) *
            (position * cos(damped * elapsed) + coefficient * sin(damped * elapsed))
    }

    static func pressRelease(elapsed: Double, depth: Double) -> Double {
        max(0, spring(elapsed: elapsed, position: depth, velocity: 0, omega: 17, damping: 0.82))
    }

    private static func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let x = clamp(progress)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let t = (low + high) * 0.5
            if bezier(t, p1: x1, p2: x2) < x { low = t } else { high = t }
        }
        return bezier((low + high) * 0.5, p1: y1, p2: y2)
    }

    private static func bezier(_ t: Double, p1: Double, p2: Double) -> Double {
        let inverse = 1 - t
        return 3 * inverse * inverse * t * p1 + 3 * inverse * t * t * p2 + t * t * t
    }
}
