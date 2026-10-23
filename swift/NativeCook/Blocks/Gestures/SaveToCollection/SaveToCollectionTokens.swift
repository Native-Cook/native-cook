import SwiftUI

enum SaveToCollectionTokens {
    enum Palette {
        static let background = Color.black
        static let ink = Color.white
        static let mute = Color(red: 142 / 255, green: 142 / 255, blue: 147 / 255)
        static let card = Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255)
        static let cardEdge = Color.white.opacity(0.18)
        static let sheenTop = Color.white.opacity(0.10)
        static let sheenBottom = Color.white.opacity(0)
        static let glow = Color.white.opacity(0.18)
        static let finger = Color.white.opacity(0.35)
        static let fingerEdge = Color.white.opacity(0.75)
        static let glassEdge = Color.white.opacity(0.35)
    }

    enum Glow {
        static let blur = 28.0
        static let opacity = 0.75
        static let drop = 18.0
        static let scale = 1.05
        static let cornerRadius = 40.0
    }

    enum Glass {
        static let intensity = 40.0
        static let edgePixels = 2.0
    }

    enum Space {
        static let xs = 4.0
        static let s = 8.0
        static let m = 12.0
        static let l = 16.0
        static let xl = 24.0
        static let xxl = 32.0
    }

    enum Layout {
        static let gutter = 16.0
        static let gap = 8.0
        static let headerTop = 8.0
        static let header = 44.0
        static let cardRadius = 12.0
        static let bookmarkWidth = 62.0
        static let bookmarkHeight = 75.0
        static let bookmarkY = 766.0
        static let bookmarkRiseY = 393.0
        static let dockFade = 160.0
        static let badge = 20.0
        static let corner = 22.0
        static let check = 26.0
        static let saveButtonHeight = 36.0
        static let saveButtonRadius = 18.0
        static let finger = 44.0
        static let bookmarkHitRadius = 44.0
        static let fingerPressScaleLoss = 0.18
        static let fingerBorderWidth = 1.0
    }

    enum Typography {
        static let titleSize = 28.0
        static let titleLineHeight = 34.0
        static let titleWeight = Font.Weight.bold
        static let titleTracking = 0.2
        static let actionSize = 17.0
        static let actionLineHeight = 22.0
        static let actionWeight = Font.Weight.semibold
        static let badgeSize = 12.0
        static let badgeLineHeight = 14.0
        static let badgeWeight = Font.Weight.bold
        static let countSize = 15.0
        static let countLineHeight = 20.0
        static let countWeight = Font.Weight.semibold
        static let countTracking = -0.2
        static let saveSize = 15.0
        static let saveLineHeight = 20.0
        static let saveWeight = Font.Weight.semibold
    }

    enum Easing {
        static let easeOut = (0.23, 1.0, 0.32, 1.0)
        static let easeInOut = (0.77, 0.0, 0.175, 1.0)
        static let rise = (0.2, 0.0, 0.5, 1.0)
    }

    enum Save {
        static let hold = 0.300
        static let liftScale = 1.06
        static let magnetRadius = 150.0
        static let magnetPull = 0.45
        static let magnetShrink = 0.45
        static let arc = 0.08
        static let endScale = 0.06
        static let tiltPerVelocity = 0.012
        static let tiltMax = 14.0
        static let flickSpeed = 900.0
        static let flickAim = 0.8
        static let commitOpenThreshold = 0.05
        static let floatAmplitude = 2.5
        static let floatPeriod = 3.25
        static let trailCount = 8
        static let trailStep = 0.022
        static let streak = 1.4
        static let streakBlur = 16.0
    }

    enum Stream {
        static let count = 46
        static let total = 2.100
        static let pour = 0.75
        static let fall = 0.30
        static let spread = 125.0
        static let stretch = 0.60
        static let seed = 7
        static let assetWidth = 76.0
        static let assetHeight = 188.0
        static let cardWidth = 60.0
        static let bigProbability = 0.20
        static let smallWidth = 40.0 ... 80.0
        static let bigWidth = 90.0 ... 140.0
        static let yRange = -100.0 ... 240.0
        static let fadeInMultiplier = 6.0
        static let fadeOutStart = 0.85
        static let endScaleLoss = 0.85
        static let fallMinFactor = 0.75
        static let fallVariation = 0.50
        static let randomMultiplier = 16_807
        static let randomModulus = 2_147_483_647
        static let randomDivisor = 2_147_483_646.0
    }

    struct SpringToken {
        let damping: Double
        let stiffness: Double
        let mass: Double
    }

    enum Spring {
        static let lift = SpringToken(damping: 18, stiffness: 320, mass: 0.7)
        static let back = SpringToken(damping: 20, stiffness: 220, mass: 1)
        static let bounce = SpringToken(damping: 8, stiffness: 420, mass: 0.6)
        static let check = SpringToken(damping: 16, stiffness: 380, mass: 0.6)
        static let tilt = SpringToken(damping: 16, stiffness: 220, mass: 0.6)
    }

    enum Timing {
        static let pressIn = 0.100
        static let pressOut = 0.160
        static let flight = 0.560
        static let dropFlight = 0.300
        static let flockStagger = 0.070
        static let badgeRoll = 0.160
        static let dim = 0.140
        static let reduced = 0.200
        static let cornerIn = 0.220
        static let deal = 0.380
        static let dealStagger = 0.110
        static let dealDrop = 18.0
        static let nextPile = 0.600
        static let landRing = 0.560
        static let countIn = 0.260
        static let flickFlight = 0.360
        static let rise = 1.700
        static let riseDown = 0.850
        static let riseHold = 0.500
        static let gulpUp = 0.070
        static let demoRelease = 0.120
        static let bounceSettle = 2.000
    }

    enum Demo {
        static let fingerFade = 0.150
        static let dragCard = "lemon"
        static let dragDown = 1.600
        static let dragLifted = 1.900
        static let dragEnd = 3.000
        static let dragUp = 3.250
        static let saveAll = 4.200
        static let rise = 5.050
        static let saveAllAgain = 8.600
        static let riseAgain = 9.450
        static let tapLead = 0.250
        static let dragBow = 40.0
    }

    enum CardStyle {
        static let softPad = 24.0
        static let phaseStep = 1.7
        static let blurAmplitudeDivisor = 10.0
        static let liftedOpacityLoss = 0.65
        static let dealStartScale = 0.90
        static let pressScaleLoss = 0.03
        static let shadowOpacity = 0.55
        static let shadowRadius = 18.0
        static let shadowY = 12.0
        static let dragShadowOpacity = 0.50
        static let dragShadowRadius = 24.0
        static let dragShadowY = 16.0
    }

    enum Bookmark {
        static let radiusFraction = 0.16
        static let notchFraction = 0.26
        static let sideInset = 4.6
        static let bottomRadius = 3.0
        static let bottomInset = 0.6
        static let halo = 140.0
        static let ring = 70.0
        static let ringLineWidth = 1.5
        static let ringOpacity = 0.55
        static let ringStartScale = 0.60
        static let ringScaleTravel = 1.80
        static let haloScaleTravel = 0.60
        static let openScale = 0.18
        static let bounceScale = 0.22
        static let riseScale = 0.14
        static let pressScaleLoss = 0.06
        static let countWidth = 160.0
        static let countStartY = 10.0
        static let countRevealMultiplier = 2.0
        static let pillOpacity = 0.06
    }

    enum FlightStyle {
        static let leadFadeStart = 0.92
        static let ghostOpacity = 0.55
        static let ghostPower = 0.70
        static let ghostFadeInMultiplier = 4.0
        static let speedSample = 0.02
        static let smearMultiplier = 1.20
        static let leadStretchMultiplier = 0.50
        static let lagStretchMultiplier = 0.40
        static let smearFadeMultiplier = 2.50
        static let speedDivisor = 3.0
    }

    enum Clock {
        static let loop = 13.0
        static let resumeDelay = 3.5
        static let maxFrameDelta = 0.1
        static let integrationStep = 1.0 / 120.0
        static let returnTolerance = 0.25
        static let minimumVectorLength = 0.0001
        static let minimumVelocityDelta = 0.0001
    }

    static let replicaOnly = false
    static let qaHoldAt: Double? = nil
    static let designSize = CGSize(width: 402, height: 874)
    static let streamAssets = [
        "SaveToCollection-streak-orange-woman",
        "SaveToCollection-streak-violet-flowers",
        "SaveToCollection-streak-pastel-bloom",
        "SaveToCollection-streak-blue-figure",
        "SaveToCollection-streak-white-cat",
        "SaveToCollection-streak-lemon",
        "SaveToCollection-streak-white-van",
        "SaveToCollection-streak-slow-jazz",
        "SaveToCollection-streak-valley",
        "SaveToCollection-streak-pop-bust",
    ]

    struct CardToken: Identifiable {
        let id: String
        let art: String
        let soft: String
        let streak: String
        let label: String
        let frame: CGRect
        let blur: Double
        let pile: Int
    }

    static let cards: [CardToken] = [
        .init(id: "woman", art: "SaveToCollection-art-orange-woman", soft: "SaveToCollection-soft-woman", streak: "SaveToCollection-streak-orange-woman", label: "Orange afternoon", frame: .init(x: 100, y: 25, width: 98, height: 102), blur: 30, pile: 0),
        .init(id: "violet", art: "SaveToCollection-art-violet-flowers", soft: "SaveToCollection-soft-violet", streak: "SaveToCollection-streak-violet-flowers", label: "Violet meadow", frame: .init(x: 143, y: 153, width: 100, height: 72), blur: 4, pile: 0),
        .init(id: "bloom", art: "SaveToCollection-art-pastel-bloom", soft: "SaveToCollection-soft-bloom", streak: "SaveToCollection-streak-pastel-bloom", label: "Pastel bloom", frame: .init(x: 35, y: 250, width: 95, height: 88), blur: 2, pile: 0),
        .init(id: "figure", art: "SaveToCollection-art-blue-figure", soft: "SaveToCollection-soft-figure", streak: "SaveToCollection-streak-blue-figure", label: "Blue figure", frame: .init(x: 132, y: 255, width: 159, height: 185), blur: 1, pile: 0),
        .init(id: "cat", art: "SaveToCollection-art-white-cat", soft: "SaveToCollection-soft-cat", streak: "SaveToCollection-streak-white-cat", label: "White cat", frame: .init(x: 27, y: 370, width: 125, height: 147), blur: 1, pile: 0),
        .init(id: "lemon", art: "SaveToCollection-art-lemon", soft: "SaveToCollection-soft-lemon", streak: "SaveToCollection-streak-lemon", label: "Lemon", frame: .init(x: 182, y: 368, width: 188, height: 150), blur: 1, pile: 0),
        .init(id: "van", art: "SaveToCollection-art-white-van", soft: "SaveToCollection-soft-van", streak: "SaveToCollection-streak-white-van", label: "White van", frame: .init(x: 105, y: 495, width: 178, height: 162), blur: 0, pile: 0),
        .init(id: "woman2", art: "SaveToCollection-art-orange-woman", soft: "SaveToCollection-soft-woman2", streak: "SaveToCollection-streak-orange-woman", label: "Orange afternoon", frame: .init(x: 72, y: 20, width: 148, height: 160), blur: 30, pile: 1),
        .init(id: "violet2", art: "SaveToCollection-art-violet-flowers", soft: "SaveToCollection-soft-violet2", streak: "SaveToCollection-streak-violet-flowers", label: "Violet meadow", frame: .init(x: 100, y: 161, width: 185, height: 106), blur: 1, pile: 1),
        .init(id: "bloom2", art: "SaveToCollection-art-pastel-bloom", soft: "SaveToCollection-soft-bloom2", streak: "SaveToCollection-streak-pastel-bloom", label: "Pastel bloom", frame: .init(x: 0, y: 255, width: 172, height: 175), blur: 2, pile: 1),
        .init(id: "jazz", art: "SaveToCollection-art-slow-jazz", soft: "SaveToCollection-soft-jazz", streak: "SaveToCollection-streak-slow-jazz", label: "Slow jazz", frame: .init(x: 75, y: 310, width: 128, height: 177), blur: 0, pile: 1),
        .init(id: "valley", art: "SaveToCollection-art-valley", soft: "SaveToCollection-soft-valley", streak: "SaveToCollection-streak-valley", label: "Green valley", frame: .init(x: 152, y: 269, width: 200, height: 138), blur: 0, pile: 1),
        .init(id: "bust", art: "SaveToCollection-art-pop-bust", soft: "SaveToCollection-soft-bust", streak: "SaveToCollection-streak-pop-bust", label: "Pop bust", frame: .init(x: 203, y: 379, width: 150, height: 169), blur: 6, pile: 1),
    ]
}
