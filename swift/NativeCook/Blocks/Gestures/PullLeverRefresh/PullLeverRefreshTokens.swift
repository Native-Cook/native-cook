import SwiftUI

/// Mechanical and visual tokens translated directly from the React Native block.
enum PullLeverRefreshTheme {
    enum ColorPalette {
        // RN theme.ts:3-17. The channel fractions preserve each CSS literal exactly.
        static let background = Color(red: 28.0 / 255.0, green: 28.0 / 255.0, blue: 28.0 / 255.0)
        static let track = Color(red: 44.0 / 255.0, green: 44.0 / 255.0, blue: 44.0 / 255.0)
        static let trackDot = Color(red: 35.0 / 255.0, green: 35.0 / 255.0, blue: 35.0 / 255.0)
        static let fill = Color(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0)
        static let fillDot = Color(red: 28.0 / 255.0, green: 28.0 / 255.0, blue: 28.0 / 255.0)
        static let pivot = Color(red: 28.0 / 255.0, green: 28.0 / 255.0, blue: 28.0 / 255.0)
        static let ink = Color(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0)
        static let mute = Color(red: 140.0 / 255.0, green: 140.0 / 255.0, blue: 140.0 / 255.0)
        static let finger = Color(red: 82.0 / 255.0, green: 82.0 / 255.0, blue: 82.0 / 255.0, opacity: 0.92)
        static let dimFill = Color(red: 44.0 / 255.0, green: 44.0 / 255.0, blue: 44.0 / 255.0)
        static let arcOff = Color(red: 51.0 / 255.0, green: 51.0 / 255.0, blue: 51.0 / 255.0)
        static let shadow = Color(red: 11.0 / 255.0, green: 11.0 / 255.0, blue: 11.0 / 255.0)
        static let shaftLight: [Color] = [
            Color(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, opacity: 0.0),
            Color(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, opacity: 0.9)
        ]
        static let specular = Color(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, opacity: 0.22)
        static let knobLit = Color(red: 189.0 / 255.0, green: 189.0 / 255.0, blue: 189.0 / 255.0)
    }

    enum Artboard {
        // Reference measurement only: source artboard is 720 × 540 px at 0.558 pt/px.
        // RN theme.ts:21 contains the already-scaled runtime values below.
        static let height: Double = 301.0
        static let leverLeft: Double = 115.0
        static let leverCentreY: Double = 150.0
    }

    enum Lever {
        // Reference measurement only: source lever is 307 × 30 px, pivot 12 px, pitch 30 px.
        // RN theme.ts:25-31 contains the already-scaled runtime values below.
        static let length: Double = 171.0
        static let thickness: Double = 17.0
        static let pivotDot: Double = 7.0
        static let dot: Double = 2.2
        static let pitch: Double = 16.75
        static let dots: Int = 9
        static let knob: Double = 33.0
    }

    enum Layout {
        // RN theme.ts:39-44
        static let spaceXS: Double = 4.0
        static let spaceSM: Double = 8.0
        static let spaceMD: Double = 12.0
        static let spaceLG: Double = 16.0
        static let spaceXL: Double = 24.0
        static let spaceXXL: Double = 32.0
        static let captionGap: Double = 8.0
        static let arcRadius: Double = 34.0
    }

    enum Typography {
        struct Caption {
            let fontSize: Double
            let lineHeight: Double
            let fontWeight: String
            let letterSpacing: Double
        }

        // RN theme.ts:39. Keep the source's numeric CSS weight as text for an exact token.
        static let caption = Caption(
            fontSize: 12.0,
            lineHeight: 16.0,
            fontWeight: "500",
            letterSpacing: 0.1
        )
    }
}

/// Motion values translated directly from the React Native block.
enum PullLeverRefreshMotion {
    /// QA-only parity switch from RN motion.ts. Shipping keeps the upgrade layers enabled.
    static let replicaOnly = false

    enum Pull {
        // RN motion.ts:6-14
        static let restAngle: Double = 45.0
        static let maxAngle: Double = 42.0
        static let leverPull: Double = 90.0
        static let overAngle: Double = 22.0
        static let overSoftness: Double = 70.0
        static let overTick: Double = 6.0
        static let degPerPt: Double = 0.335
        static let throwBoost: Double = 0.6
        static let maxThrow: Double = 900.0
    }

    struct Spring {
        let damping: Double
        let stiffness: Double
        let mass: Double

        // RN motion.ts:18-21
        static let home = Spring(damping: 18.0, stiffness: 300.0, mass: 1.0)
        static let throwUp = Spring(damping: 16.0, stiffness: 240.0, mass: 1.0)
        static let fall = Spring(damping: 12.0, stiffness: 260.0, mass: 1.0)
        static let bloom = Spring(damping: 9.0, stiffness: 160.0, mass: 0.8)
    }

    enum Light {
        struct Shadow {
            let x: Double
            let y: Double
            let blur: Double
            let opacity: Double
        }

        struct Arc {
            let radius: Double
            let dots: Int
            let from: Double
            let to: Double
            let size: Double
        }

        // RN motion.ts:26-42
        static let direction: Double = -135.0
        static let highlightOffset: Double = 0.18
        static let highlightSize: Double = 0.55
        static let highlightRamp: Double = 8.0
        static let restAngle: Double = 45.0
        static let knobSoft: Double = 1.5
        static let haloSize: Double = 34.0
        static let haloBlur: Double = 18.0
        static let haloMin: Double = 0.25
        static let haloMax: Double = 0.6
        static let haloBreath: Double = 0.9
        static let cometHead: Double = 12.0
        static let cometBlur: Double = 14.0
        static let cometTrail: Int = 7
        static let cometTrailPt: Double = 52.0
        static let shaftLight: Double = 0.55
        static let ripple: Double = 160.0
        static let rippleMs: Double = 620.0
        static let haloBaseScale: Double = 0.7
        static let haloGlowScale: Double = 0.3
        static let haloBreathScale: Double = 0.12
        static let rippleOpacity: Double = 0.7
        static let ringWidth: Double = 1.5
        static let specularShadowOpacity: Double = 0.6
        static let specularShadowBlur: Double = 4.0
        static let trailShadowOpacity: Double = 0.8
        static let trailShadowBlur: Double = 6.0
        static let arcLitScale: Double = 0.5
        static let shadow = Shadow(x: 7.0, y: 11.0, blur: 10.0, opacity: 0.55)
        static let arc = Arc(
            radius: PullLeverRefreshTheme.Layout.arcRadius,
            dots: 7,
            from: 100.0,
            to: 168.0,
            size: 4.0
        )
    }

    enum Timing {
        // RN motion.ts:46-71
        static let dotMs: Double = 250.0
        static let upAngle: Double = -44.0
        static let relaxAngle: Double = -30.0
        static let relaxMs: Double = 300.0
        static let dimMs: Double = 800.0
        static let lightMs: Double = 160.0
        static let swayAmplitude: Double = 2.5
        static let swayHalfPeriod: Double = 550.0
        static let swayFade: Double = 240.0
        static let bloomScale: Double = 1.7
        static let glowRise: Double = 1800.0
        static let pulse: Double = 640.0
        static let easeInOut: (x1: Double, y1: Double, x2: Double, y2: Double) = (0.77, 0.0, 0.175, 1.0)
        static let easeOut: (x1: Double, y1: Double, x2: Double, y2: Double) = (0.23, 1.0, 0.32, 1.0)
        static let knobFlash: Double = 140.0
        static let knobFade: Double = 520.0
        static let shake: [Double] = [5.0, -4.0, 2.6, -1.3, 0.0]
        static let shakeStep: Double = 70.0
        static let charIn: Double = 220.0
        static let charOut: Double = 160.0
        static let charStagger: Double = 18.0
        static let idleAmplitude: Double = 1.2
        static let idlePeriod: Double = 2.75
        static let caption: Double = 180.0
        static let doneLabel: Double = 1600.0
        static let reduced: Double = 180.0
        static let bloomRise: Double = 90.0
    }

    enum Caption {
        // Reanimated FadeInDown/FadeOutUp defaults and springify().damping(16).
        static let travel: Double = 25.0
        static let springDamping: Double = 16.0
        static let springStiffness: Double = 100.0
        static let springMass: Double = 1.0
    }

    enum Interaction {
        static let activationDrop: Double = 6.0
        static let staleVelocityAfter: Double = 0.1
        static let scriptedVelocitySample: Double = 0.08
        static let scriptedReleaseBoost: Double = 600.0
        static let quantizationEpsilon: Double = 0.001
    }

    enum Clock {
        // RN clamps to 50 ms, safely below the port contract's 100 ms ceiling.
        static let maximumDelta: Double = 0.05
        static let nominalDelta: Double = 1.0 / 60.0
        static let springEnergyThreshold: Double = 6e-9
        static let idleAmplitudeResponse: Double = 4.0
    }

    enum Demo {
        struct Full {
            let down: Double
            let moveEnd: Double
            let up: Double
            let depth: Double
        }

        struct Partial {
            let down: Double
            let moveEnd: Double
            let up: Double
            let depth: Double
        }

        // RN motion.ts:81-83
        static let full = Full(down: 0.6, moveEnd: 1.75, up: 2.1, depth: 1.55)
        static let resolve: Double = 4.4
        static let partial = Partial(down: 6.4, moveEnd: 7.0, up: 7.2, depth: 0.5)
    }

    // RN motion.ts:75, 78-79. All durations remain milliseconds; autoplay timestamps remain seconds.
    static let fetchMS: Double = 2300.0
    static let loopMS: Double = 11_000.0
    static let resumeMS: Double = 3_500.0
}
