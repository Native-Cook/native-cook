import SwiftUI

enum MemoCardFanLayout {
    static let scale = 0.51
    static let pinCenterX = 545.0
    static let pinCenterY = 655.0
    static let cardWidth = 435.0
    static let cardHeight = 434.0
    static let cardRadius = 40.0
    static let photoWidth = 327.0
    static let photoHeight = 330.0
    static let photoRadius = 26.0
    static let photoFrame = 8.0
    static let pullRise = 118.0
}

enum MemoCardFanColor {
    static let page = Color(red: 1 / 255, green: 1 / 255, blue: 1 / 255)
    static let folder = Color(red: 36 / 255, green: 33 / 255, blue: 31 / 255)
    static let ink = Color.white
    static let mute = Color(red: 145 / 255, green: 143 / 255, blue: 141 / 255)
    static let frame = Color(red: 1, green: 254 / 255, blue: 251 / 255)
}

enum MemoCardFanMotion {
    static let resumeAfter = 3_500.0
    static let dragRange = 140.0
    static let dragResistance = 0.3
    static let dragCommit = 0.5
    static let dragVelocity = 600.0
    static let pressScale = 0.97
    static let pressIn = 120.0
    static let pressOut = 150.0
    static let openDuration = 520.0
    static let openDamping = 0.84
    static let closeDuration = 420.0
    static let closeDamping = 0.9
    static let flingDuration = 430.0
    static let flingDamping = 0.82
    static let reduceMotionFade = 200.0
    static let stagger = 0.12

    static func rubberBanded(_ value: Double) -> Double {
        if value < 0 { return value * dragResistance }
        if value > 1 { return 1 + (value - 1) * dragResistance }
        return value
    }

    static func pull(_ input: Double) -> Double {
        let time = wrap(input)
        let dip: Double
        if time > 900, time < 1_140 {
            dip = -0.05 * sin(min(1, (time - 900) / 240) * .pi)
        } else {
            dip = 0
        }
        return dip
            + springStep(time, at: 1_020, omega: 12.5, zeta: 0.78)
            - springStep(time, at: 2_700, omega: 13, zeta: 0.92)
    }

    static func spread(_ input: Double) -> Double {
        let time = wrap(input)
        return springStep(time, at: 1_120, omega: 15, zeta: 0.6)
            - springStep(time, at: 2_850, omega: 14, zeta: 1)
    }

    static func topOpen(_ input: Double) -> Double {
        let time = wrap(input) - 2_800
        if time < 900 { return 0 }
        if time < 1_050 {
            return -0.025 * sin(((time - 900) / 150) * .pi)
        }
        if time < 1_740 {
            return springRise((time - 1_050) / 690)
        }
        if time < 2_600 { return 1 }
        if time < 3_240 {
            return 1 - smoother((time - 2_600) / 640)
        }
        return 0
    }

    static func bottomOpen(_ input: Double) -> Double {
        1
    }

    private static func springStep(
        _ time: Double,
        at: Double,
        omega: Double,
        zeta: Double
    ) -> Double {
        let seconds = (time - at) / 1_000
        guard seconds > 0 else { return 0 }
        if zeta >= 1 {
            return 1 - (1 + omega * seconds) * exp(-omega * seconds)
        }
        let damped = omega * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * omega * seconds)
            * (cos(damped * seconds) + ((zeta * omega) / damped) * sin(damped * seconds))
    }

    private static func springRise(_ value: Double) -> Double {
        let x = min(1, max(0, value))
        let end = 1 - exp(-6.4) * cos(9.4)
        return (1 - exp(-6.4 * x) * cos(9.4 * x)) / end
    }

    private static func smoother(_ value: Double) -> Double {
        let x = min(1, max(0, value))
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    private static func wrap(_ input: Double) -> Double {
        let period = MemoCardFan.loop * 1_000
        return ((input.truncatingRemainder(dividingBy: period)) + period)
            .truncatingRemainder(dividingBy: period)
    }
}
