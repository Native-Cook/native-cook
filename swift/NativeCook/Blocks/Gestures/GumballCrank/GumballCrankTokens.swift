import Foundation

/// Immutable theme values ported from the React Native gumball-crank block.
///
/// Dimensions are points on the source's 402 pt-wide stage. CSS colour literals
/// intentionally remain strings so the Swift renderer can apply the same alpha
/// values without coupling this token file to a UI framework.
enum GumballCrankTheme {
    // RN theme.ts:3
    static let stageWidth: Double = 402.0

    enum Colors {
        // RN theme.ts:7
        static let wallTop: String = "#D9CCDD"
        // RN theme.ts:8
        static let wallBottom: String = "#D2C1D7"
        // RN theme.ts:9
        static let beam: String = "rgba(255, 255, 255, 0.28)"
        // RN theme.ts:10
        static let beamFar: String = "rgba(255, 255, 255, 0)"
        // RN theme.ts:11
        static let tableTop: String = "#C9AEDC"
        // RN theme.ts:12
        static let tableFront: String = "#B897CF"
        // RN theme.ts:13
        static let tableEdge: String = "rgba(255, 255, 255, 0.45)"
        // RN theme.ts:14
        static let shadow: String = "rgba(80, 40, 110, 0.22)"
        // RN theme.ts:15
        static let body: String = "#B57FDB"
        // RN theme.ts:16
        static let bodyLight: String = "#CFA4F0"
        // RN theme.ts:17
        static let bodyDeep: String = "#9A63C6"
        // RN theme.ts:18
        static let wave: String = "rgba(255, 255, 255, 0.22)"
        // RN theme.ts:19
        static let plate: String = "#C799EC"
        // RN theme.ts:20
        static let door: String = "#9C64CB"
        // RN theme.ts:21
        static let doorDeep: String = "#7E49AE"
        // RN theme.ts:22
        static let knob: String = "#F6B04A"
        // RN theme.ts:23
        static let knobLight: String = "#FFD58A"
        // RN theme.ts:24
        static let knobDeep: String = "#D98A26"
        // RN theme.ts:25
        static let slot: String = "#D07F1E"
        // RN theme.ts:26
        static let glassRim: String = "rgba(255, 255, 255, 0.85)"
        // RN theme.ts:27
        static let glassTint: String = "rgba(255, 255, 255, 0.12)"
        // RN theme.ts:28
        static let glassShade: String = "rgba(120, 70, 150, 0.16)"
        // RN theme.ts:29
        static let reflect: String = "rgba(255, 255, 255, 0.55)"
        // RN theme.ts:30
        static let ink: String = "#3B2350"
        // RN theme.ts:31
        static let mute: String = "rgba(59, 35, 80, 0.6)"
        // RN theme.ts:32
        static let capsuleTop: String = "rgba(255, 255, 255, 0.78)"
        // RN theme.ts:33
        static let capsuleEdge: String = "rgba(255, 255, 255, 0.95)"
        // RN theme.ts:34
        static let capsule: String = "#F0507E"
        // RN theme.ts:35
        static let capsuleDeep: String = "#C73460"
        // RN theme.ts:36
        static let card: String = "#FFFFFF"
        // RN theme.ts:37
        static let cardAccent: String = "#F0507E"
        // RN theme.ts:38
        static let button: String = "#3B2350"
        // RN theme.ts:39
        static let buttonInk: String = "#FFFFFF"
        // RN theme.ts:40
        static let scrim: String = "rgba(214, 196, 222, 0.55)"
        // RN theme.ts:41
        static let bag: String = "#9A63C6"
        // RN theme.ts:42
        static let bagLight: String = "#CFA4F0"
    }

    /// Gumball colours, soft and slightly dusty to sit with the lilac clay.
    // RN theme.ts:46
    static let balls: [String] = [
        "#FF9E7A", "#E8547A", "#FFD66B", "#7FD8B5",
        "#8B6CE0", "#6FB7FF", "#F7A6C8", "#5B3B6E"
    ]

    enum Machine {
        // RN theme.ts:50
        static let lidTop: Double = 196.0
        // RN theme.ts:51
        static let lidHeight: Double = 58.0
        // RN theme.ts:52
        static let lidWidth: Double = 188.0
        // RN theme.ts:53
        static let lidKnob: Double = 14.0
        // RN theme.ts:54
        static let globeY: Double = 348.0
        // RN theme.ts:55
        static let globeR: Double = 130.0
        // RN theme.ts:56
        static let glass: Double = 6.0
        // RN theme.ts:57
        static let collarY: Double = 436.0
        // RN theme.ts:58
        static let collarHeight: Double = 16.0
        // RN theme.ts:59
        static let collarWidth: Double = 204.0
        // RN theme.ts:60
        static let baseTop: Double = 452.0
        // RN theme.ts:61
        static let baseBottom: Double = 645.0
        // RN theme.ts:62
        static let baseTopWidth: Double = 158.0
        // RN theme.ts:63
        static let baseBottomWidth: Double = 224.0
        // RN theme.ts:64
        static let baseRadius: Double = 26.0
        // RN theme.ts:65
        static let plateY: Double = 514.0
        // RN theme.ts:66
        static let plateSize: Double = 72.0
        // RN theme.ts:67
        static let plateRadius: Double = 18.0
        // RN theme.ts:68
        static let knobR: Double = 22.0
        // RN theme.ts:69
        static let slotWidth: Double = 30.0
        // RN theme.ts:70
        static let slotHeight: Double = 7.0
        // RN theme.ts:71
        static let doorTop: Double = 560.0
        // RN theme.ts:72
        static let doorWidth: Double = 84.0
        // RN theme.ts:73
        static let doorHeight: Double = 78.0
        // RN theme.ts:74
        static let doorRadius: Double = 28.0
        // RN theme.ts:75
        static let tableY: Double = 600.0
        // RN theme.ts:76
        static let tableFrontY: Double = 812.0
        // RN theme.ts:77
        static let extraY: Double = 0.5
    }

    enum Ball {
        // RN theme.ts:80
        static let count: Int = 15
        // RN theme.ts:80
        static let rMin: Double = 18.0
        // RN theme.ts:80
        static let rMax: Double = 24.0
        // RN theme.ts:80
        static let seed: Int = 7
    }

    enum Capsule {
        // RN theme.ts:82
        static let r: Double = 20.0
        // RN theme.ts:82
        static let rollTo: Double = 726.0
        // RN theme.ts:82
        static let rollScale: Double = 1.7
    }

    enum Card {
        // RN theme.ts:84
        static let width: Double = 288.0
        // RN theme.ts:84
        static let height: Double = 236.0
        // RN theme.ts:84
        static let radius: Double = 28.0
        // RN theme.ts:84
        static let y: Double = 560.0
        // RN theme.ts:84
        static let pad: Double = 24.0
    }

    enum Bag {
        // RN theme.ts:86
        static let size: Double = 44.0
        // RN theme.ts:86
        static let right: Double = 24.0
    }

    enum Typography {
        // RN theme.ts:89
        static let side: Double = 24.0
        // RN theme.ts:90
        static let top: Double = 16.0
        // RN theme.ts:91
        static let titleSize: Double = 28.0
        // RN theme.ts:92
        static let titleFamily: String = "Georgia"
        // RN theme.ts:93
        static let subSize: Double = 14.0
        // RN theme.ts:94
        static let hintSize: Double = 15.0
        // RN theme.ts:95
        static let bottom: Double = 28.0
        // RN theme.ts:96
        static let amountSize: Double = 56.0
    }

    enum Copy {
        // RN theme.ts:100
        static let cardMeta: String = "Day 7 streak · added Oct 15"
        // RN theme.ts:101
        static let coinsWord: String = "coins"
        // RN theme.ts:102
        static let title: String = "Daily treat"
        // RN theme.ts:103
        static let sub: String = "Day 7 of your streak"
        // RN theme.ts:104
        static let hint: String = "Turn the knob a full circle"
        // RN motion.ts:105
        static let hintBusy: String = "Here it comes"
        // RN motion.ts:106
        static let amount: String = "+50"
        // RN theme.ts:107
        static let unit: String = "coins"
        // RN theme.ts:108
        static let prize: String = "Day 7 reward"
        // RN theme.ts:109
        static let collect: String = "Collect"
        // RN theme.ts:110
        static let knob: String = "Turn the knob"
        // RN theme.ts:111
        static let knobHint: String = "Dispenses today’s capsule"
        // RN theme.ts:112
        static let bag: String = "Rewards bag"
    }

    // RN theme.ts:117
    static let replica: Bool = false

    enum ReplicaArt {
        struct Globe {
            let x: Double
            let y: Double
            let r: Double
            let floor: Double
        }

        struct Knob {
            let x: Double
            let y: Double
            let size: Double
            let tilt: Double
        }

        struct Door {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let r: Double
            let top: String
            let bottom: String
            let shade: String
        }

        struct Glare {
            let r: Double
            let from: Double
            let sweep: Double
            let width: Double
            let blur: Double
            let color: String
        }

        struct Rim {
            let width: Double
            let color: String
        }

        struct Jostle {
            let amp: Double
            let freq: Double
            let decay: Double
        }

        struct Pot {
            let cx: Double
            let cy: Double
            let w: Double
            let h: Double
        }

        struct PotA {
            let cx: Double
            let cy: Double
            let scale: Double
            let rot: Double
        }

        struct Exit {
            let x: Double
            let y: Double
        }

        struct Fruit {
            let sprite: String
            let radius: Double
            let size: Double
        }

        // RN theme.ts:119
        static let wallFill: String = "#D6C8DA"
        // RN theme.ts:120
        static let globe = Globe(x: 198.0, y: 341.0, r: 160.0, floor: 462.0)
        // RN theme.ts:121
        static let knob = Knob(x: 500.5 / 3.0, y: 1553.5 / 3.0, size: 169.0 / 3.0, tilt: -0.35)
        // RN theme.ts:123
        static let knobMatrix: [Double] = [
            0.87, 0, 0, 0, 0,
            0, 0.97, 0, 0, 0,
            0, 0, 1.19, 0, 0,
            0, 0, 0, 1, 0
        ]
        // RN theme.ts:125
        static let door = Door(
            x: 443.0 / 3.0, y: 1786.0 / 3.0, w: 295.0 / 3.0, h: 290.0 / 3.0,
            r: 16.0, top: "#C27CE0", bottom: "#C583E1", shade: "rgba(110, 50, 150, 0.35)"
        )
        // RN theme.ts:126
        static let fruitBlur: Double = 1.2
        // RN theme.ts:127
        static let pushGrade: [Double] = [
            0.97, 0, 0, 0, 0,
            0, 0.93, 0, 0, 0,
            0, 0, 0.98, 0, 0,
            0, 0, 0, 1, 0
        ]
        // RN theme.ts:128
        static let defocus: Double = 1.2
        // RN theme.ts:129
        static let knobBlur: Double = 1.6
        // RN theme.ts:130
        static let fillBlur: Double = 0.0
        // RN theme.ts:131
        static let fruitPack: Double = 0.72
        // RN theme.ts:132
        static let haze: String = "rgba(255, 255, 255, 0.10)"
        // RN theme.ts:134
        static let glare = Glare(
            r: 138.0, from: 196.0, sweep: 62.0, width: 14.0, blur: 6.0,
            color: "rgba(255, 255, 255, 0)"
        )
        // RN theme.ts:135
        static let rim = Rim(width: 3.0, color: "rgba(255, 255, 255, 0)")
        // RN theme.ts:136
        static let jostle = Jostle(amp: 5.0, freq: 9.0, decay: 3.2)
        // RN theme.ts:138
        static let pot = Pot(cx: 201.0, cy: 420.0, w: 402.0, h: 1000.0 / 3.0)
        // RN theme.ts:140
        static let potA = PotA(cx: 201.0, cy: 450.0, scale: 1.0, rot: 0.0)
        // RN theme.ts:141
        static let exit = Exit(x: 199.0, y: 644.0)
        // RN theme.ts:144-170
        static let fruit: [Fruit] = [
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach-b", radius: 40.0, size: 112.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach-b", radius: 40.0, size: 112.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach-b", radius: 40.0, size: 112.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach-b", radius: 40.0, size: 112.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0),
            Fruit(sprite: "peach-b", radius: 40.0, size: 112.0),
            Fruit(sprite: "peach", radius: 38.0, size: 86.0),
            Fruit(sprite: "passion", radius: 24.0, size: 54.0)
        ]
    }

    enum ReplicaParallax {
        struct Offset {
            let a: [Double]
            let b: [Double]
        }

        struct FruitPatch {
            let a: [Double]
            let b: [Double]
            let c: [Double]
            let r: Double
            let blur: Double
        }

        // RN motion.ts:104, in screen pixels.
        static let globe = Offset(a: [-9.0, 15.0], b: [-7.0, -8.0])
        // RN motion.ts:105
        static let fruitR = FruitPatch(
            a: [-24.0, -43.0], b: [-8.0, -32.0], c: [1110.0, 2060.0], r: 420.0, blur: 3.5
        )
        // RN motion.ts:106
        static let fruitL = FruitPatch(
            a: [0.0, -20.0], b: [0.0, -6.0], c: [100.0, 2200.0], r: 300.0, blur: 2.0
        )
    }

    enum Up {
        struct Panel {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let r: Double
        }

        struct Card {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let r: Double
        }

        struct CouponText {
            let x: Double
            let y: Double
            let w: Double
        }

        struct Stub {
            let x: Double
            let y: Double
        }

        struct Counter {
            let top: Double
            let left: Double
        }

        // RN theme.ts:176
        static let panel = Panel(x: 119.3, y: 315.7, w: 188.0, h: 218.0, r: 14.0)
        // RN theme.ts:178
        static let card = Card(x: 41.0, y: 452.0, w: 320.0, h: 183.0, r: 10.0)
        // RN theme.ts:179
        static let couponText = CouponText(x: 0.36, y: 0.2, w: 0.3)
        // RN theme.ts:180
        static let stub = Stub(x: 0.83, y: 0.5)
        // RN theme.ts:181
        static let blur: Double = 14.0
        // RN theme.ts:182
        static let tubBack: Double = 0.9
        // RN theme.ts:183
        static let peel: Double = -0.14
        // RN theme.ts:184
        static let navy: String = "#293267"
        // RN theme.ts:185
        static let scrim: Double = 0.35
        // RN theme.ts:186
        static let scrimColor: String = "#2A1840"
        // RN theme.ts:187
        static let shadow: String = "rgba(70, 30, 100, 0.55)"
        // RN theme.ts:188
        static let shadowY: Double = 640.0
        // RN theme.ts:189
        static let sweep: String = "rgba(255, 255, 255, 0.45)"
        // RN theme.ts:190
        static let sweepW: Double = 34.0
        // RN theme.ts:191
        static let ring: Double = 34.0
        // RN theme.ts:192
        static let ringColor: String = "rgba(255, 240, 220, 0.9)"
        // RN theme.ts:193
        static let confettiKinds: Int = 3
        // RN theme.ts:194
        static let counter = Counter(top: 12.0, left: 16.0)
    }

    /// Values declared inline in `GumballUpgrade.tsx` and its StyleSheet.
    /// Keeping them here makes the Swift scene a renderer of the RN contract,
    /// rather than a second source of layout and styling truth.
    enum Presentation {
        static let machineAnchorY: Double = 645.0
        static let cameraReferenceWidth: Double = 1206.0
        static let machineSquash: Double = 0.02
        static let machineSquashX: Double = 0.5
        static let jostleScale: Double = 0.05
        static let globeClipInset: Double = 4.0
        static let globeRimInset: Double = 2.0
        static let globeHazeOpacity: Double = 0.10
        static let sweepPadding: Double = 40.0
        static let sweepTravelPadding: Double = 80.0
        static let sweepBlur: Double = 10.0
        static let sweepSkew: Double = -0.4
        static let doorStroke: Double = 6.0
        static let doorInnerBlur: Double = 5.0
        static let ringLineWidth: Double = 2.0
        static let ringBlur: Double = 2.0
        static let ringBaseOpacity: Double = 0.35
        static let ringOpacityAmplitude: Double = 0.25
        static let ringFrequency: Double = 2.4
        static let seamEase: Double = 0.35
        static let knobHitSize: Double = 72.0
        static let knobWobbleWeight: Double = 0.06
        static let knobBackWeight: Double = 0.35
        static let shadowWidth: Double = 240.0
        static let shadowHeight: Double = 28.0
        static let shadowBlur: Double = 14.0
        static let shadowOpacity: Double = 0.25
        static let initialTubScale: Double = 0.3
        static let tubFadeFraction: Double = 0.2
        static let collectingCouponDrop: Double = 40.0
        static let panelFadeSpan: Double = 0.5
        static let couponFadeStart: Double = 0.2
        static let couponFadeSpan: Double = 0.4
        static let stubFadeStart: Double = 0.6
        static let stubFadeSpan: Double = 0.4
        static let textFadeStart: Double = 0.75
        static let textFadeSpan: Double = 0.25
        static let confettiOpacityBase: Double = 1.8
        static let confettiOpacityRate: Double = 0.8
        static let confettiSpriteScale: Double = 1.6
        static let stubCoinSize: Double = 40.0
        static let flyingCoinSize: Double = 26.0
        static let flyingCoinShrink: Double = 0.45
        static let flyingCoinControlX: Double = 40.0
        static let flyingCoinControlXStep: Double = 6.0
        static let flyingCoinControlY: Double = 120.0
        static let flyingCoinControlYStep: Double = 8.0

        static let counterHeight: Double = 36.0
        static let counterRadius: Double = 18.0
        static let counterPadding: Double = 12.0
        static let counterCoinSize: Double = 20.0
        static let counterCoinGap: Double = 8.0
        static let counterWordGap: Double = 4.0
        static let counterWordSize: Double = 12.0
        static let counterNumberSize: Double = 16.0
        static let counterNumberLineHeight: Double = 22.0
        static let counterTracking: Double = -0.2
        static let counterBackgroundOpacity: Double = 0.72
        static let counterInkOpacity: Double = 0.55
        static let counterBumpScale: Double = 0.18
        static let counterScrimFade: Double = 0.7
        static let counterTargetX: Double = 22.0
        static let counterTargetY: Double = 18.0
        static let odometerIn: Double = 0.14
        static let odometerOut: Double = 0.12
        static let odometerOffset: Double = 25.0
        static let counterLocale: String = "en_US"

        static let eyebrowColor: String = "#F6C27A"
        static let eyebrowSize: Double = 10.0
        static let eyebrowTracking: Double = 1.6
        static let amountSize: Double = 44.0
        static let amountLineHeight: Double = 48.0
        static let amountTracking: Double = -1.2
        static let metaSize: Double = 11.0
        static let metaOpacity: Double = 0.60

        static let collectHeight: Double = 44.0
        static let collectRadius: Double = 22.0
        static let collectPadding: Double = 24.0
        static let collectTopGap: Double = 16.0
        static let collectGap: Double = 8.0
        static let collectCoinSize: Double = 18.0
        static let collectSize: Double = 16.0
        static let collectBackground: String = "#F2B26A"
        static let collectInk: String = "#293267"
        static let collectPressedScale: Double = 0.97
        static let collectPressDuration: Double = 0.10
        static let collectReleaseDuration: Double = 0.16
        static let tapTravelLimit: Double = 8.0
        static let easeOutBackOvershoot: Double = 1.4
    }

    // Lower-camel namespace aliases keep the React Native names available to the Swift port.
    static let colors = Colors.self
    static let machine = Machine.self
    static let ball = Ball.self
    static let capsule = Capsule.self
    static let card = Card.self
    static let bag = Bag.self
    static let type = Typography.self
    static let copy = Copy.self
    static let replicaArt = ReplicaArt.self
    static let replicaParallax = ReplicaParallax.self
    static let up = Up.self
    static let presentation = Presentation.self
}

/// Immutable motion values ported from the React Native gumball-crank block.
/// Times retain the source units: values ending in `MS` are milliseconds;
/// other temporal values are seconds. Distances are points and angles radians.
enum GumballCrankMotion {
    // RN motion.ts:3
    static let step: Double = 1.0 / 120.0
    // RN motion.ts:4
    static let maxSteps: Int = 30

    enum Pile {
        // RN motion.ts:7
        static let settleSteps: Int = 900
        // RN motion.ts:8
        static let settleGravity: Double = 1400.0
        // RN motion.ts:9
        static let home: Double = 40.0
        // RN motion.ts:10
        static let damping: Double = 5.0
        // RN motion.ts:11
        static let passes: Int = 3
        // RN motion.ts:12
        static let kick: Double = 260.0
        // RN motion.ts:13
        static let tremble: Double = 0.35
    }

    enum Crank {
        struct Overshoot {
            let stiffness: Double
            let damping: Double
            let kick: Double
        }

        // RN motion.ts:17
        static let grab: Double = 70.0
        // RN motion.ts:18
        static let detent: Double = .pi / 2.0
        // RN motion.ts:19
        static let turn: Double = .pi * 2.0
        // RN motion.ts:20
        static let back: Double = 0.1
        // RN motion.ts:21
        static let overshoot = Overshoot(stiffness: 300.0, damping: 14.0, kick: 1.6)
        // RN motion.ts:22
        static let press: Double = 1.06
    }

    enum Dispense {
        // RN motion.ts:27
        static let drop: Double = 0.34
        // RN motion.ts:28
        static let bounce: Double = 0.18
        // RN motion.ts:29
        static let rollStart: Double = 0.56
        // RN motion.ts:30
        static let roll: Double = 0.5
        // RN motion.ts:31
        static let wobbleStart: Double = 1.1
        // RN motion.ts:32
        static let wobble: Double = 0.3
        // RN motion.ts:33
        static let split: Double = 1.4
        // RN motion.ts:34
        static let splitLength: Double = 0.5
        // RN motion.ts:35
        static let cardRise: Double = 0.45
        // RN motion.ts:36
        static let done: Double = 1.9
    }

    enum Collect {
        struct BagBounce {
            let stiffness: Double
            let damping: Double
            let kick: Double
        }

        // RN motion.ts:39
        static let duration: Double = 0.6
        // RN motion.ts:39
        static let bagBounce = BagBounce(stiffness: 380.0, damping: 12.0, kick: -3.0)
    }

    enum Haptic {
        // RN motion.ts:41
        static let grab: Int = 1
        // RN motion.ts:41
        static let detent: Int = 2
        // RN motion.ts:41
        static let ratchet: Int = 3
        // RN motion.ts:41
        static let dispense: Int = 4
        // RN motion.ts:41
        static let open: Int = 5
        // RN motion.ts:41
        static let collect: Int = 6
    }

    enum Reduced {
        // RN motion.ts:43
        static let fade: Double = 0.2
    }

    struct Script {
        let turn: [Double]
        let turnAngle: Double
        let turnRadius: Double
        let collectAt: Double
    }

    // RN motion.ts:46
    static let loopMS: Double = 12_000.0
    // Swift interaction contract: autoplay resumes two seconds after touch.
    static let resumeMS: Double = 2_000.0
    // RN motion.ts:48-53
    static let script = Script(turn: [1.0, 2.7], turnAngle: .pi * 2.0 + 0.18, turnRadius: 52.0, collectAt: 7.8)

    struct ReplicaT {
        let turn: [Double]
        let kick: Double
        let out: [Double]
        let back: [Double]
    }

    // RN motion.ts:57-58
    static let replicaLoopMS: Double = 10_000.0
    // RN motion.ts:58
    static let replicaT = ReplicaT(turn: [2.8, 3.6], kick: 3.0, out: [3.8, 5.0], back: [9.0, 9.6])

    struct ReplicaCamera {
        let a: [Double]
        let b: [Double]
        let t: [Double]
    }

    // RN motion.ts:62
    static let replicaCamera = ReplicaCamera(a: [1.0946, -15.3, 13.9], b: [1.117, -49.3, -18.1], t: [3.8, 4.3, 5.5])
    // RN motion.ts:66
    static let replicaHold: Double = -1.0

    enum Knob {
        // RN motion.ts:70
        static let grab: Double = 80.0
        // RN motion.ts:71
        static let friction: Double = 2.2
        // RN motion.ts:72
        static let catchSpin: Double = 0.8
        // RN motion.ts:73
        static let commitSpin: Double = 3.0
        // RN motion.ts:74
        static let finish: Double = 0.35
        // RN motion.ts:75
        static let maxSpin: Double = 9.0
        // RN motion.ts:76
        static let press: Double = 1.06
        // RN motion.ts:77
        static let backMax: Double = 0.3
    }

    enum UpT {
        // RN motion.ts:80
        static let squash: Double = 0.18
        // RN motion.ts:81
        static let out: [Double] = [0.18, 1.18]
        // RN motion.ts:82
        static let confetti: Double = 0.35
        // RN motion.ts:83
        static let morph: [Double] = [1.7, 2.25]
        // RN motion.ts:84
        static let done: Double = 2.4
        // RN motion.ts:85
        static let collect: Double = 0.8
        // RN motion.ts:86
        static let coinFly: Double = 0.45
        // RN motion.ts:87
        static let coinStagger: Double = 0.04
        // RN motion.ts:88
        static let coins: Int = 8
        // RN motion.ts:89
        static let couponOut: [Double] = [0.45, 0.8]
        // RN motion.ts:90
        static let back: Double = 0.5
        // RN motion.ts:91
        static let sweep: Double = 0.9
        // RN motion.ts:92
        static let idleSweep: Double = 4.0
    }

    struct Confetti {
        let count: Int
        let speed: Double
        let spread: Double
        let gravity: Double
        let life: Double
        let size: [Double]
        let spin: Double
    }

    // RN motion.ts:94
    static let confetti = Confetti(count: 24, speed: 560.0, spread: 1.2, gravity: 1100.0, life: 1.6, size: [8.0, 16.0], spin: 7.0)

    struct Coins {
        let from: Double
        let add: Double
        let countSteps: Int
        let countStep: Double
    }

    // RN motion.ts:97
    static let coins = Coins(from: 1290.0, add: 50.0, countSteps: 5, countStep: 70.0)
    // RN motion.ts:98
    static let upLoopMS: Double = 12_000.0

    struct UpScript {
        let turn: [Double]
        let angle: Double
        let radius: Double
        let collectAt: Double
    }

    // RN motion.ts:99
    static let upScript = UpScript(turn: [1.0, 2.5], angle: .pi * 2.0 + 0.2, radius: 48.0, collectAt: 7.8)
    // RN motion.ts:100
    static let qaSpeed: Double = 1.0

    /// Fixed-step implementation values declared inline in `crank-sim.ts`.
    enum Dynamics {
        static let maximumFrameDelta: Double = 0.1
        static let velocitySampleFloor: Double = 1.0 / 240.0
        static let velocityOldWeight: Double = 0.6
        static let velocityNewWeight: Double = 0.4
        static let ratchetHapticThreshold: Double = -0.02
        static let detentEpsilon: Double = 0.000_001
        static let jostleKickX: Double = 90.0
        static let jostleKickY: Double = 60.0
        static let jostleStiffness: Double = 160.0
        static let jostleDamping: Double = 9.0
        static let backDecay: Double = 14.0
        static let pressResponse: Double = 18.0
        static let bumpDecay: Double = 3.0
        static let resumeEase: Double = 0.35
        static let autoTurnTail: Double = 0.1
        static let confettiSeedA: Double = 12.9898
        static let confettiSeedAMultiplier: Double = 43_758.5453
        static let confettiSeedB: Double = 78.233
        static let confettiSeedBMultiplier: Double = 12_345.678
        static let confettiMinimumSpeed: Double = 0.55
        static let confettiSpeedRange: Double = 0.45
        static let confettiInitialRotation: Double = 6.0
        static let confettiClearDelay: Double = 0.5
        static let restingThreshold: Double = 0.000_1
        static let loopEpsilon: Double = 0.000_000_001
        static let preferredMinimumFPS: Float = 30
        static let preferredMaximumFPS: Float = 60
        static let preferredFPS: Float = 60
    }

    // Lower-camel namespace aliases keep the React Native names available to the Swift port.
    static let pile = Pile.self
    static let crank = Crank.self
    static let dispense = Dispense.self
    static let collect = Collect.self
    static let haptic = Haptic.self
    static let reduced = Reduced.self
    static let knob = Knob.self
    static let upT = UpT.self
    static let dynamics = Dynamics.self
}
