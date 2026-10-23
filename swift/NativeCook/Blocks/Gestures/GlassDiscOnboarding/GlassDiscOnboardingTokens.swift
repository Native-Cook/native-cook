import Foundation

/// Values measured from the approved React Native reference. Keep these in sync with theme.ts.
enum GlassDiscOnboardingTheme {
    enum Color {
        static let bgTL = "#EAC5BE"
        static let bgTR = "#E0B8B3"
        static let bgBL = "#E9CDBE"
        static let bgBR = "#F5D2C2"
        static let ink = "#2A1A1C"
        static let inkSoft = "#2E1E20"
        static let hint = "#BE9C8F"
        static let lensInk = "#2A2226"
        static let shadow = "#5A3A36"
        static let buttonLight = "#F9E1D5"
        static let buttonLightEdge = "rgba(90,58,54,0.10)"
        static let buttonLightShadow = "#8A5A52"
        static let buttonLightInk = "#3A3236"
        static let buttonDark = "#1E0A0E"
        static let buttonDarkInk = "#FFFFFF"
        static let finger = "rgba(255,255,255,0.45)"
        static let fingerRing = "rgba(35,29,32,0.30)"
        static let ringInner = "#7FE0FF"
        static let ringCore = "#1E3BFF"
        static let ringOuter = "#B05CFF"
        static let ringBlush = "#FF8FCB"
        static let pulse = "#FFFFFF"
        static let fieldA = "rgba(70,90,255,0.16)"
        static let fieldB = "rgba(176,92,255,0.10)"
        static let fieldClear = "rgba(176,92,255,0)"
    }

    enum Typography {
        static let font = "System"
        static let introSize: Double = 31
        static let introLineHeight: Double = 37
        static let introWeight = "400"
        static let introLetterSpacing: Double = -0.3
        static let introColor = "#2A1A1C"
        static let introTextAlign = "center"
        static let hintSize: Double = 14.5
        static let hintWeight = "500"
        static let hintLetterSpacing: Double = -0.2
        static let hintColor = "#BE9C8F"
        static let hintTextAlign = "center"
        static let headlineSize: Double = 37
        static let headlineLineHeight: Double = 43.5
        static let headlineTracking: Double = -0.5
        static let buttonSize: Double = 15
        static let buttonLetterSpacing: Double = -0.2
        static let lensSize: Double = 23.5
    }

    enum Space {
        static let xs: Double = 4
        static let sm: Double = 8
        static let md: Double = 12
        static let lg: Double = 16
        static let xl: Double = 24
        static let xxl: Double = 32
    }

    struct Lens {
        let fold: Double
        let foldBack: Double
        let foldShape: Double
        let kY: Double
        let kX: Double
        let squeezeX: Double
        let tx: Double
        let ty: Double
        let spread: Double
        let fringeFrom: Double
        let fade: Double
        let mirrorFloor: Double
    }

    struct Bead {
        let from: Double
        let span: Double
        let frost: Double
    }

    struct Cloud {
        let x: Double
        let y: Double
        let r: Double
        let cell: Int
    }

    enum Layout {
        static let domeRadius: Double = 0.6
        static let domeTop: Double = 0.72
        static let pullT: [Double] = [0, 0.4, 0.6, 0.75, 1]
        static let pullR: [Double] = [0.6, 0.53, 0.43, 0.358, 0.29]
        static let pullY: [Double] = [0.996, 0.884, 0.79, 0.721, 0.655]
        static let pullX: [Double] = [0.5, 0.5, 0.5, 0.5, 0.5]
        static let lens = Lens(fold: 0.73, foldBack: 6.04, foldShape: 2.0, kY: 0.556, kX: 0.75, squeezeX: 1.091, tx: 0.017, ty: -0.113, spread: 0.5, fringeFrom: 0.35, fade: 3.19, mirrorFloor: 0.3)
        static let rimLight: Double = 0.04
        static let rimWidth: Double = 7
        static let shadowAt: Double = 4
        static let shadowWidth: Double = 6
        static let shadowAlpha: Double = 0.07
        static let bead = Bead(from: 90, span: 45, frost: 0.18)
        static let domeFrost: Double = 0.12
        static let ringFrom: Double = 0.5
        static let ringTo: Double = 0.9
        static let hintY: Double = 0.889
        static let introBaseline: Double = 0.4842
        static let headlineBaseline: Double = 0.5099
        static let beadY: Double = 0.397
        static let beadRadius: Double = 46
        static let beadRestY: Double = 0.3715
        static let beadRestRadius: Double = 39.5
        static let backRadius: Double = 0.37
        static let buttonsBottom: Double = 32
        static let buttonHeight: Double = 52
        static let buttonHeightDark: Double = 54
        static let buttonGap: Double = 14
        static let gutter: Double = 34
        static let buttonIcon: Double = 19
        static let buttonIconInset: Double = 22
        static let fingerRadius: Double = 22
    }

    enum Atlas {
        static let cloudFrameWidth: Double = 402
        static let cloudFrameHeight: Double = 874
        static let cell: Double = 192
        static let columns = 6
        static let rows = 4
        static let cloud: [Cloud] = [
            Cloud(x: 375, y: 38, r: 25, cell: 9), Cloud(x: 120, y: 25, r: 18, cell: 3), Cloud(x: 84, y: 42, r: 35, cell: 12),
            Cloud(x: 250, y: 52, r: 27, cell: 4), Cloud(x: 203, y: 88, r: 35, cell: 14), Cloud(x: 145, y: 95, r: 22, cell: 13),
            Cloud(x: 94, y: 84, r: 33, cell: 2), Cloud(x: 350, y: 100, r: 25, cell: 15), Cloud(x: 292, y: 148, r: 20, cell: 7),
            Cloud(x: 322, y: 132, r: 32, cell: 10), Cloud(x: 67, y: 127, r: 13, cell: 20), Cloud(x: 140, y: 143, r: 28, cell: 0),
            Cloud(x: 261, y: 158, r: 25, cell: 23), Cloud(x: 200, y: 165, r: 26, cell: 5), Cloud(x: 207, y: 207, r: 19, cell: 18),
            Cloud(x: 147, y: 229, r: 17, cell: 21), Cloud(x: 172, y: 240, r: 29, cell: 8), Cloud(x: 195, y: 272, r: 22, cell: 16),
            Cloud(x: 239, y: 264, r: 11, cell: 1), Cloud(x: 247, y: 226, r: 8, cell: 17), Cloud(x: 279, y: 224, r: 8, cell: 11),
            Cloud(x: 243, y: 240, r: 4, cell: 19), Cloud(x: 225, y: 241, r: 4, cell: 22), Cloud(x: 270, y: 263, r: 2.5, cell: 6),
            Cloud(x: 60, y: 195, r: 1.8, cell: 15), Cloud(x: 80, y: 220, r: 1.8, cell: 7)
        ]
    }

    /// RN renderer constants which are not part of the measured layout itself.
    enum Render {
        static let ascent: Double = 0.94
        static let textureScaleMax: Double = 2
        static let lensHeadlineBaseline: Double = 1.5
        static let buttonLensWidth: Double = 120
        static let buttonLensLightAlpha: Double = 0.85
        static let buttonLensDarkAlpha: Double = 0.16
        static let buttonShadowAlpha: Double = 0.16
        static let buttonShadowRadius: Double = 6
        static let buttonShadowY: Double = 2
        static let buttonRimAlpha: Double = 0.9
        static let buttonRimHairlines: Double = 2
        static let buttonLabelBaseline: Double = 0.35
        static let fieldRadius: Double = 0.8
        static let fieldMiddle: Double = 0.45
        static let tiltMultiplier: Double = 1.5
        static let fingerLightThreshold: Double = 0.5
        static let pressureStrokeStart: Double = 2.2
        static let pressureStrokeEnd: Double = 0.4
        static let pressureAlpha: Double = 0.7
        static let fingerFillAlpha: Double = 0.45
        static let fingerStrokeAlpha: Double = 0.35
        static let fingerStrokeWidth: Double = 1.5
        static let minimumBubbleRadius: Double = 0.5
        static let minimumWobble: Double = 0.005
        static let tiltInterval: Double = 1 / 30
    }

    enum Touch {
        static let beadPadding: Double = 22
        static let domePadding: Double = 24
        static let velocityPositionEpsilon: Double = 0.01
        static let velocityStaleAfter: Double = 0.1
    }

    enum Copy {
        static let intro = ["A new era of", "software is here."]
        static let hint = "Swipe up to enter"
        static let lens = "Meet Wavo."
        static let headline = "Meet Wavo."
        static let sub = ["The first personal", "software platform."]
        static let email = "Continue with email"
        static let phone = "Continue with phone"
        static let back = "Start over"
        static let enter = "Enter"
    }
}

enum GlassDiscOnboardingMotion {
    enum Pull {
        static let threshold: Double = 260
        static let rubber: Double = 120
        static let commitAt: Double = 0.85
        static let flickVelocity: Double = -900
        static let flickMin: Double = 0.4
        static let ringPeakAt: Double = 0.42
        static let ringOutAt: Double = 0.62
        static let revealFrom: Double = 0.52
        static let revealTo: Double = 0.72
        static let introOutBy: Double = 0.62
        static let hintOutBy: Double = 0.2
        static let tickEvery: Double = 0.125
    }

    enum Back {
        static let growBy: Double = 300
        static let commitBy: Double = 200
        static let flickVelocity: Double = 900
        static let flickMin: Double = 90
    }

    enum Rise {
        static let ms: Double = 1600
    }

    struct SpringValue {
        let duration: Double
        let dampingRatio: Double
    }

    enum Spring {
        static let back = SpringValue(duration: 400, dampingRatio: 0.8)
        static let bead = SpringValue(duration: 400, dampingRatio: 0.8)
        static let dome = SpringValue(duration: 400, dampingRatio: 0.8)
    }

    static let easeOut: [Double] = [0.23, 1, 0.32, 1]

    enum Fade {
        static let settle: Double = 260
        static let landingTail: Double = 160
        static let headlineMs: Double = 260
        static let restDelay: Double = 250
        static let restMs: Double = 750
        static let outMs: Double = 200
        static let introBack: Double = 280
        static let reducedMs: Double = 200
    }

    enum Burst {
        static let delayMs: Double = 650
        static let staggerMs: Double = 26
        static let startScale: Double = 0.12
        static let growOver: Double = 150
        static let growMs: Double = 520
        static let launchSpeed: Double = 320
    }

    enum Stickers {
        static let step: Double = 1 / 120
        static let stiffness: Double = 14
        static let damping: Double = 5.2
        static let separation: Double = 0.5
        static let overlap: Double = 0.62
        static let push: Double = 56
        static let float: Double = 2.2
        static let floatPeriodMs: Double = 4200
        static let exitLift: Double = 1.1
        static let exitStiffness: Double = 5
        static let exitDamping: Double = 3.5
        static let exitStaggerMs: Double = 30
        static let maxFrameMs: Double = 250
        static let fingerVelocityMinimumMs: Double = 8
        static let integrationEpsilon: Double = 0.000001
        static let exitVisibilityY: Double = -2
        static let fingerVelocityDecay: Double = 0.9
        static let launchDistanceMinimum: Double = 1
        static let floatPhaseStep: Double = 1.7
        static let distanceMinimum: Double = 0.001
        static let fingerShove: Double = 0.35
        static let fingerImpulse: Double = 0.2
        static let fingerCarryStepScale: Double = 8
    }

    static let loopMS: Double = 12500
    static let debugFreezeMS: Double = -1
    static let maximumFrameMS: Double = 100

    enum Autoplay {
        static let resumeDelayMs: Double = 3500
        /// The port contract supersedes the RN recovery delay for all native ports.
        static let restartAfterTouchMS: Double = 2000
        static let startX: Double = 0.66
        static let startY: Double = 0.84
        static let pullStart: Double = 600
        static let pullEnd: Double = 3400
        static let pullBy: Double = 236
        static let sweepStart: Double = 6700
        static let sweepEnd: Double = 7600
        static let backStart: Double = 8200
        static let backEnd: Double = 9500
        static let backBy: Double = 250
        static let sweepX: Double = 0.1
        static let sweepWidth: Double = 0.8
        static let sweepY: Double = 0.2
        static let sweepArc: Double = 0.06
        static let backXOffset: Double = 6
    }

    enum Upgrade {
        static let nudge: Double = 4
        static let nudgeSpring = SpringValue(duration: 300, dampingRatio: 0.8)
        static let stretchPerSpeed: Double = 1 / 9000
        static let stretchMax: Double = 0.14
        static let stretchFollow: Double = 0.25
        static let dispersionPerSpeed: Double = 1 / 1400
        static let dispersionMax: Double = 1.6
        static let glintEveryMs: Double = 6000
        static let glintMs: Double = 1300
        static let glintFrom: Double = -2.7
        static let glintTo: Double = -0.45
        static let glow: Double = 0.35
        static let pulses: [Double] = [0, 230, 460]
        static let pulseMs: Double = 760
        static let pulseGrow: Double = 96
        static let pulseSquash: Double = 0.06
        static let pulseWaveSpeed: Double = 3
        static let wobblePerSpeed: Double = 1 / 2600
        static let wobbleMax: Double = 0.16
        static let fingerCarry: Double = 0.35
        static let rise: Double = 8
        static let lineStagger: Double = 60
        static let fieldDrift: Double = 8
        static let fieldPeriodMs: Double = 8000
        static let hintBase: Double = 0.82
        static let pressIn: Double = 100
        static let pressOut: Double = 220
        static let pressScale: Double = 0.97
    }
}
