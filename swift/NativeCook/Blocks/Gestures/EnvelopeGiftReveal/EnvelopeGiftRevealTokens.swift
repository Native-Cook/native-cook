import Foundation

/// The single source of truth for values copied from the approved React Native block.
enum EnvelopeGiftRevealTokens {
    static let displayFont = "EnvelopeArchivoExpandedBlack"

    enum Colors {
        static let lime: UInt32 = 0xDAFF1C
        static let limeHigh: UInt32 = 0xF0FF72
        static let limeLow: UInt32 = 0xCCFA14
        static let limeEdge: UInt32 = 0xB2DB00
        static let limeInside: UInt32 = 0xA9CF00
        static let flapHigh: UInt32 = 0xEAFF66
        static let flapLow: UInt32 = 0xCEFF14
        static let sideHigh: UInt32 = 0xDFFF30
        static let sideLow: UInt32 = 0xD5FF1B
        static let sideRight: UInt32 = 0xCBFF0D
        static let bottomHigh: UInt32 = 0xD4FF22
        static let bottomLow: UInt32 = 0xCCFF18
        static let crease = (red: 120.0, green: 160.0, blue: 0.0, alpha: 0.28)
        static let ink: UInt32 = 0x0A0A0A
        static let white: UInt32 = 0xFFFFFF
        static let sealMark: UInt32 = 0xD4FF1C
        static let message: UInt32 = 0xFFFFFF
        static let messageInk: UInt32 = 0x151515
        static let shadow = (red: 60.0, green: 80.0, blue: 0.0, alpha: 0.2)
        static let cardPink: UInt32 = 0xF2A9BC
        static let confetti: [UInt32] = [0xE0359A, 0xF06BB5, 0xFFC1DF, 0xC9F23A, 0xFFFFFF, 0xFFE45C]
    }

    enum Space {
        static let xs = 4.0
        static let sm = 8.0
        static let md = 12.0
        static let lg = 16.0
        static let side = 20.0
        static let xl = 24.0
        static let xxl = 32.0
    }

    enum Layout {
        static let side = 33.0
        static let headline = 54.0
        static let headlineLine = 50.0
        static let headlineTop = 112.0
        static let title = 38.0
        static let titleLine = 36.0
        static let envW = 336.0
        static let envH = 258.0
        static let envTop = 260.0
        static let sealD = 60.0
        static let sealY = 0.66
        static let flapTip = 0.7
        static let buttonD = 134.0
        static let buttonCentre = 638.0
        static let cardW = 360.0
        static let cardH = 186.0
        static let cardR = 14.0
        static let cardInScale = 0.9
        static let titleGap = 30.0
        static let finalTop = 125.0
        static let finalSide = 20.0
        static let cardGap = 106.0
        static let ctaGap = 34.0
        static let messageH = 125.0
        static let buttonH = 52.0
    }

    enum Gift {
        static let amount = "$25"
        static let value = 25.0
        static let message = "Happy spring! From all of us at Petal :)"
        static let cta = "Start shopping"
        static let title = "gift\ncard"
        static let headline = "wow,\na gift!"
        static let sealText = "PT"
    }

    enum Open {
        static let travel = 320.0
        static let commit = 0.42
        static let flapShare = 0.33
        static let cardRise = 222.0
        static let tiltMax = 10.0
        static let breathDeg = 7.0
        static let breathMs = 4_000.0
        static let sealSplitDeg = 40.0
        static let settlePt = 23.0
        static let pocketApex = 0.38
        static let curlDeg = 28.0
    }

    enum Springs {
        static let close = (stiffness: 260.0, damping: 18.0, mass: 1.0)
        static let finish = (stiffness: 140.0, damping: 18.0, mass: 1.0)
        static let press = (stiffness: 500.0, damping: 28.0, mass: 0.6)
    }

    enum Reveal {
        static let finishBaseMs = 260.0
        static let finishMs = 420.0
        static let flyMs = 720.0
        static let flyEase = (x1: 0.77, y1: 0.0, x2: 0.175, y2: 1.0)
        static let envelopeDropMs = 520.0
        static let pageMs = 600.0
        static let textDelay = 520.0
        static let stagger = 80.0
        static let confetti = 70
        static let confettiLife = 2.6
        static let gravity = 520.0
        static let drag = 0.985
        static let buttonOpenMs = 520.0
        static let sweepMs = 900.0
        static let countMs = 760.0
    }

    enum Reset { static let fadeMs = 420.0 }

    enum Headline {
        static let stagger = 0.006
        static let span = 0.05
        static let drop = Space.xl
        static let reducedSpan = 0.08
        static let rowOrderStride = 8
    }

    enum Ambient {
        static let floatPt = Space.xs
        static let floatMs = 3_200.0
        static let glowMs = 9_000.0
        static let glowAmount = 0.12
    }

    enum Polish {
        static let easeOut = (x1: 0.23, y1: 1.0, x2: 0.32, y2: 1.0)
        static let pressScale = 0.96
        static let pressInMs = 100.0
        static let pressOutMs = 160.0
        static let textRise = Space.lg
        static let finalBaseDelayMs = 400.0
        static let finalDurationMs = 320.0
    }

    static let reducedMs = 200.0
    static let loopMs = 8_000.0
    static let sourceResumeAfterMs = 3_500.0
    /// The port contract overrides the RN quiet period: autoplay resumes two seconds after touch.
    static let touchResumeAfterMs = 2_000.0
    static let qaHoldAt: Double? = nil

    enum Script {
        static let down = 700.0
        static let up = 2_300.0
        static let rise = 300.0
        static let reset = 6_000.0
        static let fingerOffset = 170.0
        static let fingerXOffset = 10.0
        static let easingLeadMs = 80.0
        static let easingTailMs = 150.0
        static let lateralDrift = 34.0
    }

    enum Interaction {
        static let overscrollResistance = 0.2
        static let tiltPerPoint = 0.08
        static let tiltRamp = 2.0
        static let crackShare = 0.22
        static let flickVelocity = -900.0
        static let flickMinimumProgress = 0.15
        static let closeHapticProgress = 0.08
        static let staleVelocitySeconds = 0.1
        static let velocityFloorSeconds = 1.0 / 240.0
        static let velocitySmoothingSeconds = 0.035
        static let autoplayRestThreshold = 0.01
    }

    enum Clock {
        static let initialDeltaMs = 16.0
        /// RN clamps at 64 ms; this is also below the port contract's 100 ms maximum.
        static let maximumDeltaMs = 64.0
        static let springEnergyThreshold = 6e-9
    }

    enum Scene {
        static let referenceHeight = 874.0
        static let safeTopGap = 8.0
        static let trailingInset = 8.0
        static let pageLeadMs = 100.0
        static let envelopeDropDelayMs = 60.0
        static let breathWindowMs = 700.0
        static let confettiTriggerProgress = 0.45
        static let floatStillProgress = 0.05
        static let envelopeDropDistance = 460.0
        static let resetScale = 0.96
        static let rollLimit = 0.35
        static let reducedSealSplitDegrees = 30.0
        static let flapFullDegrees = 180.0
        static let pageColorGamma = 2.2
        static let sweepDelayFactor = 0.7
        static let countDelayFactor = 0.8
        static let sweepStart = -0.4
        static let sweepLinearTravel = 1.8
        static let sweepCompletedPosition = 1.4
        static let sweepRollFactor = 0.8
        static let sweepDrift = 0.12
        static let sweepDriftMs = 6_000.0
        static let glowX = 0.5
        static let glowXAmplitude = 0.35
        static let glowY = 0.35
        static let glowYAmplitude = 0.12
        static let glowYRate = 0.7
        static let glowRadiusWidth = 0.8
        static let glowWhiteAlpha = 0.32
        static let cardInsideTop = 33.0
        static let cardArc = 36.0

        static let envelopeCorner = 10.0
        static let envelopeShadowX = 14.0
        static let envelopeShadowY = 30.0
        static let envelopeShadowWidthInset = 28.0
        static let envelopeShadowHeightInset = 10.0
        static let envelopeShadowCorner = 14.0
        static let envelopeShadowBlur = 26.0
        static let sideInnerX = 0.25
        static let sideOuterX = 0.75
        static let sideTopY = 0.38
        static let leftGradientX = 0.3
        static let leftGradientY = 0.4
        static let pocketLightX = 0.28
        static let pocketLightY = 0.5
        static let pocketLightRadius = 0.42
        static let pocketLight = (red: 246.0, green: 255.0, blue: 150.0, alpha: 0.55)
        static let creaseWidth = 1.5
        static let creaseBlur = 1.2

        static let flapShoulder = 22.0
        static let flapTipInset = 10.0
        static let flapTipControl = 4.0
        static let flapFoldShare = 0.55
        static let flapCurveSteps = 12
        static let flapPerspective = 900.0
        static let curlPerspective = 700.0
        static let flapBackThreshold = 90.0
        static let flapBackFadeDegrees = 25.0
        static let flapShadowY = 4.0
        static let flapShadowBlur = 5.0
        static let flapShadow = (red: 80.0, green: 100.0, blue: 0.0, alpha: 0.18)
        static let flapLightX = 0.24
        static let flapLightY = 0.14
        static let flapLightRadius = 0.38
        static let flapLight = (red: 246.0, green: 255.0, blue: 140.0, alpha: 0.6)

        static let sealFontSize = 22.0
        static let sealBaseline = 8.0
        static let sealTextXCorrection = 1.0
        static let sealShadowY = 3.0
        static let sealShadowBlur = 4.0
        static let sealShadow = (red: 60.0, green: 80.0, blue: 0.0, alpha: 0.25)
        static let sealPop = 0.12
        static let sealPopRate = 2.5
        static let sealHalfFadeStart = 0.55
        static let sealHalfFadeSpan = 0.45
        static let sealSplitX = 14.0
        static let sealLeftY = 10.0
        static let sealRightY = 12.0
        static let sealLeftRotation = -0.35
        static let sealRightRotation = 0.4

        static let cardShadowX = 10.0
        static let cardShadowY = 18.0
        static let cardShadowWidthInset = 20.0
        static let cardShadowHeightInset = 8.0
        static let cardShadowBlur = 16.0
        static let cardShadow = (red: 120.0, green: 30.0, blue: 60.0, alpha: 0.35)
        static let cardHighlightAlpha = 0.18
        static let sweepTranslation = 1.6
        static let sweepRectX = -0.7
        static let sweepRectWidth = 1.4
        static let sweepGradientStartX = -0.2
        static let sweepGradientEndX = 0.15
        static let sweepAlpha = 0.95
        static let amountFontSize = 30.0
        static let amountBaselineInset = 22.0
        static let amountSideInset = 20.0

        static let petalFadeLife = 0.5
        static let petalHalfWidth = 7.0
        static let petalHalfHeight = 3.6
        static let petalMinimumSquash = 0.25
        static let petalSpinShape = 1.3
    }

    enum Text {
        static let headlineTracking = -1.0
        static let titleTracking = -0.5
        static let bodySize = 16.0
        static let messagePadding = 20.0
        static let messageCorner = 4.0
        static let messageShadowAlpha = 0.08
        static let messageShadowRadius = 18.0
        static let messageShadowY = 8.0
        static let buttonSize = 13.0
        static let openTracking = 1.8
        static let ctaTracking = 1.2
        static let openShadow: UInt32 = 0x3A4A00
        static let openShadowAlpha = 0.35
        static let openShadowRadius = 22.0
        static let openShadowY = 14.0
        static let openLeavingProgress = 0.06
        static let openLeavingScale = 0.08
    }

    enum Confetti {
        static let hashN = 91.7
        static let hashSalt = 47.3
        static let hashScale = 43_758.5453
        static let angleSpread = 1.5
        static let speedBase = 260.0
        static let speedRange = 620.0
        static let originWidth = 120.0
        static let originHeight = 60.0
        static let spinRange = 16.0
        static let minimumLife = 0.6
        static let lifeRange = 0.4
        static let flutter = 0.4
        static let flutterFrequency = 2.0
    }

    enum Assets {
        static let pixelsPerPoint = 3.0
        static let motionIntervalSeconds = 0.016
    }
}
