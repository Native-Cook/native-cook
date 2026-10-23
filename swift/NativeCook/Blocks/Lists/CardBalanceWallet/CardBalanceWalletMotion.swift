import Foundation
import SwiftUI

enum CardBalanceWalletMotion {
    static let loop = 7.0
    static let loopMS = 7_000.0
    static let resumeAfterMS = 3_500.0

    static let tiltMax = 6.0
    static let refraction = 0.06
    static let followInMS = 120.0
    static let liftScale = 0.025
    static let pressLift = -0.6
    static let pressMS = 180.0
    static let pickupResponse = 0.5
    static let pickupDamping = 0.75
    static let releaseResponse = 0.45
    static let releaseDamping = 0.7
    static let specular = 0.35
    static let specularTravel = 0.4
    static let shadow = 0.55
    static let shadowDrop = 4.0
    static let dotStretch = 4.0
    static let pressScale = 0.97

    static let touchMS = 600.0
    static let pickupMS = 780.0
    static let traceFromMS = 800.0
    static let traceToMS = 5_200.0
    static let pathX = 0.34
    static let pathY = 0.22
    static let glowInMS = 1_000.0
    static let glowOutMS = 3_000.0
    static let glowMS = 600.0
    static let glow = 0.25

    struct Pose: Equatable {
        var rx = 0.0
        var ry = 0.0
        var lift = 0.0
        var glow = 0.0
    }

    static func pose(milliseconds input: Double, reduced: Bool) -> Pose {
        let time = positiveRemainder(input, loopMS)
        if reduced {
            let fadeIn = bezier(0.77, 0, 0.175, 1, ramp(time, glowInMS, glowMS))
            let fadeOut = bezier(0.77, 0, 0.175, 1, ramp(time, glowOutMS, glowMS))
            return Pose(glow: glow * (fadeIn - fadeOut))
        }
        if time < touchMS { return Pose() }
        if time <= traceToMS {
            let point = finger(milliseconds: time)
            let tilt = tilt(x: point.x, y: point.y)
            return Pose(rx: tilt.rx, ry: tilt.ry, lift: heldLift(milliseconds: time))
        }

        let elapsed = (time - traceToMS) / 1_000
        let end = tiltAtFinger(milliseconds: traceToMS)
        let before = tiltAtFinger(milliseconds: traceToMS - 1)
        let lift = heldLift(milliseconds: traceToMS)
        return Pose(
            rx: spring(elapsed, end.rx, (end.rx - before.rx) * 1_000, releaseResponse, releaseDamping),
            ry: spring(elapsed, end.ry, (end.ry - before.ry) * 1_000, releaseResponse, releaseDamping),
            lift: spring(elapsed, lift, 0, releaseResponse, releaseDamping)
        )
    }

    static func pickupLift(milliseconds: Double) -> Double {
        if milliseconds < pressMS {
            return pressLift * bezier(0.23, 1, 0.32, 1, milliseconds / pressMS)
        }
        return 1 + spring(
            (milliseconds - pressMS) / 1_000,
            pressLift - 1,
            0,
            pickupResponse,
            pickupDamping
        )
    }

    static func releasedPose(
        milliseconds: Double,
        initial: Pose,
        velocityX: Double,
        velocityY: Double
    ) -> Pose {
        let elapsed = max(0, milliseconds) / 1_000
        return Pose(
            rx: spring(elapsed, initial.rx, velocityX, releaseResponse, releaseDamping),
            ry: spring(elapsed, initial.ry, velocityY, releaseResponse, releaseDamping),
            lift: spring(elapsed, initial.lift, 0, releaseResponse, releaseDamping)
        )
    }

    static func easeOut(_ value: Double) -> Double {
        bezier(0.23, 1, 0.32, 1, value)
    }

    static func tilt(x: Double, y: Double) -> (rx: Double, ry: Double) {
        (-y * 2 * tiltMax, x * 2 * tiltMax)
    }

    static func spring(
        _ elapsed: Double,
        _ initial: Double,
        _ velocity: Double,
        _ response: Double,
        _ damping: Double
    ) -> Double {
        if elapsed <= 0 { return initial }
        let frequency = 2 * Double.pi / response
        let decay = exp(-damping * frequency * elapsed)
        if damping >= 1 {
            return decay * (initial + (velocity + frequency * initial) * elapsed)
        }
        let damped = frequency * sqrt(1 - damping * damping)
        return decay * (
            initial * cos(damped * elapsed)
                + ((velocity + damping * frequency * initial) / damped) * sin(damped * elapsed)
        )
    }

    static func bezier(
        _ x1: Double,
        _ y1: Double,
        _ x2: Double,
        _ y2: Double,
        _ x: Double
    ) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var time = x
        for _ in 0..<8 {
            let oneMinus = 1 - time
            let error = 3 * x1 * time * oneMinus * oneMinus
                + 3 * x2 * time * time * oneMinus
                + time * time * time - x
            let derivative = 3 * x1 * oneMinus * (1 - 3 * time)
                + 3 * x2 * time * (2 - 3 * time)
                + 3 * time * time
            if abs(derivative) < 0.000001 { break }
            time = min(1, max(0, time - error / derivative))
        }
        let oneMinus = 1 - time
        return 3 * y1 * time * oneMinus * oneMinus
            + 3 * y2 * time * time * oneMinus
            + time * time * time
    }

    private static func positiveRemainder(_ value: Double, _ modulus: Double) -> Double {
        ((value.truncatingRemainder(dividingBy: modulus)) + modulus)
            .truncatingRemainder(dividingBy: modulus)
    }

    private static func ramp(_ time: Double, _ start: Double, _ duration: Double) -> Double {
        min(1, max(0, (time - start) / duration))
    }

    private static func finger(milliseconds: Double) -> CGPoint {
        if milliseconds < traceFromMS {
            let progress = bezier(0.23, 1, 0.32, 1, (milliseconds - touchMS) / pressMS)
            return CGPoint(x: -pathX * progress, y: 0)
        }
        let progress = (min(milliseconds, traceToMS) - traceFromMS) / (traceToMS - traceFromMS)
        return CGPoint(
            x: -pathX * cos(2 * Double.pi * progress),
            y: -pathY * sin(4 * Double.pi * progress)
        )
    }

    private static func tiltAtFinger(milliseconds: Double) -> (rx: Double, ry: Double) {
        let point = finger(milliseconds: milliseconds)
        return tilt(x: point.x, y: point.y)
    }

    private static func heldLift(milliseconds: Double) -> Double {
        if milliseconds < pickupMS {
            return pressLift * bezier(0.23, 1, 0.32, 1, (milliseconds - touchMS) / pressMS)
        }
        return 1 + spring(
            (milliseconds - pickupMS) / 1_000,
            pressLift - 1,
            0,
            pickupResponse,
            pickupDamping
        )
    }
}
