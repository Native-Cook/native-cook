import CoreGraphics
import simd

enum PullCordThemeTokens {
    static let stageWidth = 402.0

    struct Palette {
        let paper: SIMD3<Float>
        let ink: SIMD3<Float>
        let mute: SIMD3<Float>
        let accent: SIMD3<Float>
        let track: SIMD3<Float>
    }

    static let day = Palette(
        paper: rgb(0xF1EBE0), ink: rgb(0x27231E), mute: rgb(0x6E6558),
        accent: rgb(0x2F5D48), track: rgb(0xDCD3C4)
    )
    static let night = Palette(
        paper: rgb(0x121211), ink: rgb(0xD8D0C2), mute: rgb(0x8E877C),
        accent: rgb(0x83B097), track: rgb(0x2A2926)
    )

    enum Lamp {
        static let x = 312.0
        static let capTop = 60.0
        static let cord = 640.0
        static let capHalf = 7.0
        static let capHeight = 12.0
        static let rimY = 72.0
        static let rimHalf = 68.0
        static let rimRY = 9.0
        static let lip = 2.4
        static let bulbY = 70.0
        static let bulbR = 16.0
        static let depth = 120.0
        static let coneDegrees = 64.0
        static let penumbraDegrees = 8.0
    }

    enum Light {
        static let blackbodyHeat = [0.0, 0.3, 0.5, 0.72, 1.0]
        static let blackbody: [UInt32] = [0x3A0800, 0xB32204, 0xF2600F, 0xFFA94F, 0xFFE4BC]
        static let bounce = 0.03
        static let ambient = 0.83
        static let roomBounce = 0.06
        static let direct = 0.15
        static let dim = 0.5
        static let adaptLift = 0.35
        static let adaptTime = 0.4
        static let nightPaperGlow = 0.5
        static let nightTypeGlow = 0.12
    }

    enum Shadow {
        static let projection = 2.0
        static let penumbra = 10.0
        static let chain = 0.45
        static let pull = 0.7
        static let shadeUp = 30.0
        static let shadeRX = 110.0
        static let shadeRY = 60.0
        static let shadeDepth = 0.7
    }

    enum Air {
        static let coneInnerDegrees = 56.0
        static let coneOuterDegrees = 70.0
        static let reach = 140.0
        static let haze = 0.07
        static let motes = 12
        static let mote = 0.32
        static let moteMinimum = 0.7
        static let moteMaximum = 1.3
        static let drift = 6.0
    }

    enum Legibility {
        static let floor = 3.0
        static let rest = 4.2
        static let accentShift = 0.5
    }

    enum Shade {
        struct Shell {
            let high: UInt32
            let middle: UInt32
            let low: UInt32
            let specular: UInt32
            let specularAlpha: Double
            let lip: UInt32
            let cord: UInt32
        }

        static let day = Shell(
            high: 0x4E7362, middle: 0x23402F, low: 0x0E1B14,
            specular: 0xFFE8C4, specularAlpha: 0.30, lip: 0xE2DACB, cord: 0x1F1D1A
        )
        static let night = Shell(
            high: 0x2A3A32, middle: 0x121A16, low: 0x090D0B,
            specular: 0xC4DCD0, specularAlpha: 0.16, lip: 0x363A36, cord: 0x2A2926
        )
        static let litCentre: UInt32 = 0xFBEBD0
        static let litEdge: UInt32 = 0xD9AD72
        static let coldCentre: UInt32 = 0x34322D
        static let coldEdge: UInt32 = 0x1C1B18
        static let glassCold: UInt32 = 0x3A3833
        static let glassColdEdge: UInt32 = 0x1A1916
        static let lipLit: UInt32 = 0xF7D49E
        static let bounce: UInt32 = 0xFFCC96
    }

    enum Chain {
        struct Metal {
            let rim: UInt32
            let body: UInt32
            let specular: UInt32
            let specularAlpha: Double
            let wire: UInt32
        }

        static let links = 24
        static let pitch = 4.6
        static let ball = 1.75
        static let wire = 0.6
        static let pullWidth = 10.0
        static let pullHeight = 22.0
        static let hit = 56.0
        static let day = Metal(rim: 0x3F3D39, body: 0xA8A196, specular: 0xFFF4DE, specularAlpha: 0.95, wire: 0x5C5A55)
        static let night = Metal(rim: 0x1F1E1C, body: 0x5E5B55, specular: 0xE8E2D8, specularAlpha: 0.80, wire: 0x3A3935)
    }

    enum SwitchBody {
        static let x = -30.0
        static let top = 60.0
        static let height = 26.0
        static let width = 9.0
        static let knurls = 4
        static let day: [UInt32] = [0x4A4844, 0xE2D6C2, 0x8E8A82, 0x3E3C38]
        static let night: [UInt32] = [0x22211F, 0x6E6A63, 0x45423D, 0x1C1B19]
    }

    enum Typography {
        static let family = "GleamLineIntro-Newsreader"
        static let margin = 24.0
        static let eyebrowTop = 124.0
        static let eyebrowSize = 12.0
        static let eyebrowLine = 16.0
        static let eyebrowTracking = 2.2
        static let titleTop = 144.0
        static let titleSize = 42.0
        static let titleLine = 48.0
        static let ruleTop = 192.0
        static let ruleWidth = 28.0
        static let ruleHeight = 2.0
        static let bodyTop = 296.0
        static let bodySize = 18.0
        static let bodyLine = 29.0
        static let footBottom = 48.0
        static let footSize = 13.0
        static let footLine = 16.0
        static let barGap = 12.0
        static let barHeight = 2.0
    }

    enum Book {
        static let chapter = "CHAPTER SEVEN"
        static let heading = "Low Water"
        static let body = [
            "By the time the tide turned, Mara had counted the boats twice. Eleven out, ten back. She wrote the number in the ledger anyway, in pencil, the way her father had taught her: nothing in ink until morning.",
            "The lamp above the desk swung a little whenever the door opened, and the light slid across the page and back. Outside, fog had taken the breakwater. The last ferry’s horn came through it late and flattened, like a voice from another room.",
            "She left the window open. If the eleventh boat came in, she wanted to hear it before she saw it. Her father used to say the sea kept its own books, and that it was never late with a payment, only with the news."
        ]
        static let page = 112
        static let pages = 304
        static let left = "9 min left in chapter"
    }

    enum Copy {
        static let accessibilityLabel = "Reading lamp"
        static let accessibilityHint = "Pull the chain to switch between the Day and Night page"
        static let day = "Day page"
        static let night = "Night page"
    }

    enum Motion {
        static let step = 1.0 / 120.0
        static let maximumSteps = 30
        static let loop = 12.0
        static let resume = 3.5
        static let nightRestAt = 5.0

        enum Rope {
            static let gravity = 4_000.0
            static let damping = 0.995
            static let passes = 14
            static let pullWeight = 0.3
            static let bend = 1.7
            static let slack = 0.86
            static let lift = 0.6
            static let releaseSpeed = 1_500.0
            static let snapRest = 1.5
            static let settleSteps = 900
        }

        enum Switch {
            static let travel = 9.0
            static let detent = 7.0
            static let give = 2.5
            static let rearm = 2.0
            static let stretch = 3.0
            static let springDuration = 0.4
            static let springDampingRatio = 0.8
            static let kick = 1.6
            static let flickSpeed = 900.0
            static let flickMinimum = 3.0
        }

        enum Swing {
            static let period = 2.4
            static let dampingRatio = 0.3
            static let couple = 0.016
            static let kick = 0.02
            static let maximumThrow = 0.05
            static let maximumAngle = 0.06
            static let grabRadius = 80.0
        }

        enum Filament {
            static let warmUp = 0.06
            static let coolDown = 0.12
            static let reduced = 0.04
            static let power = 4.0
            static let flicker = 0.3
            static let flickerDepth = 0.07
        }

        enum Front {
            static let onDelay = 0.08
            static let on = 1.2
            static let off = 0.45
            static let reduced = 0.2
            static let band = 0.24
            static let easeOut = [0.23, 1.0, 0.32, 1.0]
            static let easeInOut = [0.77, 0.0, 0.175, 1.0]
        }
    }

    struct ScriptSegment {
        let start: Double
        let end: Double
        let fromX: Double
        let fromY: Double
        let toX: Double
        let toY: Double
        let begins: Bool
        let ends: Bool
    }

    static let autoplayScript = [
        ScriptSegment(start: 1.0, end: 1.55, fromX: 0, fromY: 0, toX: -16, toY: 2, begins: true, ends: false),
        ScriptSegment(start: 1.55, end: 1.95, fromX: -16, fromY: 2, toX: -11, toY: 14, begins: false, ends: false),
        ScriptSegment(start: 1.95, end: 2.1, fromX: -11, fromY: 14, toX: -11, toY: 14, begins: false, ends: true),
        ScriptSegment(start: 7.0, end: 7.5, fromX: 0, fromY: 0, toX: 14, toY: 3, begins: true, ends: false),
        ScriptSegment(start: 7.5, end: 7.85, fromX: 14, fromY: 3, toX: 10, toY: 14, begins: false, ends: false),
        ScriptSegment(start: 7.85, end: 8.0, fromX: 10, fromY: 14, toX: 10, toY: 14, begins: false, ends: true)
    ]

    static let tapScript = [
        ScriptSegment(start: 0, end: 0.22, fromX: 0, fromY: 0, toX: 0, toY: 14, begins: true, ends: false),
        ScriptSegment(start: 0.22, end: 0.32, fromX: 0, fromY: 14, toX: 0, toY: 14, begins: false, ends: true)
    ]

    private static func rgb(_ hex: UInt32) -> SIMD3<Float> {
        SIMD3(
            Float((hex >> 16) & 0xFF) / 255,
            Float((hex >> 8) & 0xFF) / 255,
            Float(hex & 0xFF) / 255
        )
    }
}
