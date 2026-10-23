import SwiftUI

/// Literal theme values for the slide-to-answer cord block.
///
/// The React Native source is the visual contract.  Colours are retained as
/// explicit sRGB channels (including every alpha value), while dimensions are
/// points on a 402 pt-wide stage and are scaled by the renderer.
enum SlideToAnswerCordTheme {
    static let stageWidth: Double = 402.0

    enum Colors {
        /// Convert a source hex literal to a SwiftUI sRGB colour.
        static func hex(_ value: UInt32, opacity: Double = 1.0) -> Color {
            let red = Double((value >> 16) & 0xFF) / 255.0
            let green = Double((value >> 8) & 0xFF) / 255.0
            let blue = Double(value & 0xFF) / 255.0
            return Color(.sRGB, red: red, green: green, blue: blue, opacity: opacity)
        }

        /// Convert an rgba() source literal without rounding its alpha.
        static func rgba(_ red: Double, _ green: Double, _ blue: Double, _ alpha: Double = 1.0) -> Color {
            Color(.sRGB, red: red / 255.0, green: green / 255.0, blue: blue / 255.0, opacity: alpha)
        }

        static let page: Color = hex(0x0C0A09)
        static let pageTop: Color = hex(0x120F0D)
        static let pageBottom: Color = hex(0x070606)
        static let light: Color = rgba(255, 214, 170, 0.20)
        static let lightEdge: Color = rgba(255, 214, 170, 0)
        static let lightConnected: Color = rgba(170, 255, 200, 0.12)
        static let ivoryHi: Color = hex(0xFFF9EE)
        static let ivory: Color = hex(0xEDE2CC)
        static let ivoryMid: Color = hex(0xD8CAAE)
        static let ivoryLo: Color = hex(0xB6A584)
        static let ivoryEdge: Color = rgba(92, 72, 44, 0.45)
        static let cupLo: Color = hex(0xB9A889)
        static let cupHi: Color = hex(0xF6EEDC)
        static let hole: Color = hex(0x4A3D2B)
        static let specular: Color = rgba(255, 255, 255, 0.85)
        static let shadow: Color = rgba(0, 0, 0, 0.55)
        static let cordFront: Color = hex(0xE2D6BE)
        static let cordBack: Color = hex(0x7E7058)
        static let cordHi: Color = rgba(255, 250, 238, 0.75)
        static let cordEdge: Color = rgba(60, 46, 28, 0.55)
        static let ink: Color = hex(0xF5F3F0)
        static let mute: Color = rgba(245, 243, 240, 0.60)
        static let faint: Color = rgba(245, 243, 240, 0.40)
        static let decline: Color = hex(0xFF3B30)
        static let accept: Color = hex(0x34C759)
        static let glyph: Color = hex(0xFFFFFF)
        static let glass: Color = rgba(255, 255, 255, 0.14)
        static let glassEdge: Color = rgba(255, 255, 255, 0.20)
        static let controlOn: Color = rgba(255, 255, 255, 0.92)
        static let controlOnInk: Color = hex(0x16120F)
    }

    enum Handset {
        static let cupRadius: Double = 30
        static let topCupY: Double = 30
        static let bottomCupY: Double = 166
        static let waist: Double = 13
        static let inner: Double = 21
        static let comX: Double = 0
        static let comY: Double = 98.0
        static let hit: Double = 200.0
        static let restTilt: Double = -0.33
    }

    enum Cord {
        // RN theme.ts:62-79.
        static let anchorY: Double = -12.0
        static let replicaAnchorDX: Double = -26.0
        static let restRatio: Double = 0.335
        static let links: Int = 14
        static let turns: Int = 26
        static let wire: Double = 46
        static let tube: Double = 4.2
        static let maxRadius: Double = 6.5
        static let samples: Int = 7
    }

    enum Space {
        static let xs: Double = 4
        static let sm: Double = 8
        static let md: Double = 12
        static let lg: Double = 16
        static let xl: Double = 24
        static let xxl: Double = 32
        static let xxxl: Double = 40
    }

    enum Layout {
        // RN theme.ts:72-85.
        static let side: Double = 32.0
        static let copyTopRatio: Double = 0.64
        static let nameSize: Double = 34.0
        static let nameLine: Double = 41.0
        static let metaSize: Double = 15
        static let metaLine: Double = 20
        static let metaGap: Double = 2
        static let statusSize: Double = 15.0
        static let statusLine: Double = 20.0
        static let gap: Double = 12.0
        static let copyToPill: Double = 24
        static let buttonsBottom: Double = 40.0
        static let button: Double = 72.0
        static let buttonGap: Double = 104.0
        static let labelSize: Double = 13.0
        static let labelGap: Double = 10.0
        static let labelWidth: Double = 120.0
        static let glyph: Double = 30.0
        static let titleTracking: Double = 0.2
        static let controlMinimumWidth: Double = 80
        static let controlHeight: Double = 52
        static let controlRadius: Double = 26
        static let controlIconSize: Double = 22
        static let controlLabelSize: Double = 12
        static let controlLabelGap: Double = 8
    }

    enum Copy {
        struct Control {
            let name: String
            let label: String
            let symbol: String
        }

        // RN theme.ts:89-103.
        static let meta: String = "mobile"
        static let controls: [Control] = [
            Control(name: "mute", label: "Mute", symbol: "mic.slash.fill"),
            Control(name: "keypad", label: "Keypad", symbol: "circle.grid.3x3.fill"),
            Control(name: "speaker", label: "Speaker", symbol: "speaker.wave.2.fill")
        ]
        static let name: String = "Mara Lindqvist"
        static let ringing: String = "Lift the receiver to answer"
        static let declined: String = "Call declined"
        static let ended: String = "Call ended"
        static let decline: String = "Decline"
        static let accept: String = "Accept"
        static let end: String = "End"
        static let a11yHandset: String = "Receiver"
        static let a11yHint: String = "Lift to answer or pull down to decline"
    }

    // QA switch from RN theme.ts:108. Upgrade layers remain enabled in the shipped state.
    static let replica: Bool = false

    enum Glass {
        static let pillY: Double = -82
        static let pillH: Double = 60
    }
}
/// Literal motion values for the slide-to-answer cord block.
///
/// Durations and simulation values retain the React Native units: seconds
/// unless a property is explicitly named `MS`; distances are points unless
/// noted. All scalar values are `Double`; only counts and event IDs are `Int`.
enum SlideToAnswerCordMotion {
    static let step: Double = 1.0 / 120.0
    static let maxSteps: Int = 30

    enum Chain {
        // RN motion.ts:7-15.
        static let gravity: Double = 900.0
        static let hangStretch: Double = 150.0
        static let linkMass: Double = 0.05
        static let handsetMass: Double = 1.0
        static let damping: Double = 0.35
        static let handsetDamping: Double = 0.6
        static let idleDrive: Double = 75.0
        static let drivePeriod: Double = 4.0
        static let straighten: Double = 160.0
        static let lookEase: Double = 0.2
        static let toSource: Double = 0.3
    }

    enum Tilt {
        // RN motion.ts:19-20.
        static let stiffness: Double = 42.0
        static let damping: Double = 5.5
    }

    enum Drag {
        // RN motion.ts:24-27.
        static let answer: Double = 64.0
        static let decline: Double = 72.0
        static let reach: Double = 110.0
        static let releaseSpeed: Double = 1600.0
    }

    enum State {
        // RN motion.ts:31-35.
        static let pickUp: Double = 72.0
        static let drop: Double = 24.0
        static let lengthEase: Double = 7.0
        static let colorEase: Double = 14.0
        static let resetAfter: Double = 1.4
    }

    enum Ring {
        // RN motion.ts:39-46.
        static let every: Double = 2.0
        static let offset: Double = 1.2
        static let buzz: [Double] = [0.0, 0.2]
        static let buzzLength: Double = 0.09
        static let buzzAngle: Double = 0.05
        static let buzzHz: Double = 38.0
        static let pulse: Double = 1.3
        static let pulseFrom: Double = 44.0
        static let pulseTo: Double = 170.0
    }

    enum Haptic {
        // RN motion.ts:49.
        static let grab: Int = 1
        static let answer: Int = 2
        static let decline: Int = 3
        static let tick: Int = 4
    }

    static let loopMS: Double = 8_000.0
    static let resumeMS: Double = 2_000.0
    static let resumeInCall: Double = 3.9
    static let interactiveIdleBeforeReturn: Double = 2.0
    static let prerollLoops: Int = 3
    static let maximumFrameDelta: Double = 0.25

    struct ScriptSegment {
        let start: Double
        let end: Double
        let fromDX: Double
        let fromDY: Double
        let toDX: Double
        let toDY: Double
        /// Kept as Int 0/1 to preserve the source tuple literal exactly.
        let downAtStart: Int
        let upAtEnd: Int
    }

    // RN motion.ts:55-63. Coordinates are offsets from the handset's grab point.
    static let script: [ScriptSegment] = [
        ScriptSegment(start: 1.6, end: 2.0, fromDX: 0, fromDY: 0, toDX: 0, toDY: -10, downAtStart: 1, upAtEnd: 0),
        ScriptSegment(start: 2.0, end: 2.55, fromDX: 0, fromDY: -10, toDX: 6, toDY: -84, downAtStart: 0, upAtEnd: 0),
        ScriptSegment(start: 2.55, end: 2.7, fromDX: 6, fromDY: -84, toDX: 6, toDY: -84, downAtStart: 0, upAtEnd: 1),
        ScriptSegment(start: 4.4, end: 4.75, fromDX: 0, fromDY: 0, toDX: 0, toDY: 14, downAtStart: 1, upAtEnd: 0),
        ScriptSegment(start: 4.75, end: 5.25, fromDX: 0, fromDY: 14, toDX: -4, toDY: 92, downAtStart: 0, upAtEnd: 0),
        ScriptSegment(start: 5.25, end: 5.4, fromDX: -4, fromDY: 92, toDX: -4, toDY: 92, downAtStart: 0, upAtEnd: 1)
    ]

    // QA-only hold from RN motion.ts:65; -1 disables the freeze.
    static let replicaHold: Double = -1.0
    static let qaSpeed: Double = 1.0

    enum ReplicaSwing {
        static let period: Double = 4
        static let sourceWidth: Double = 1_206
        static let harmonics: Int = 4
        static let keys: [(time: Double, take: Int)] = [(0, 0), (1, 1), (1.875, 2), (2.9, 1), (4, 0)]
        static let x: [Double] = [578.19, -256.06, 44.02, -0.52, 0.09, 5.88, -3.23, 0, -0.12]
        static let y: [Double] = [1262.69, 8.17, -1.38, -19.1, 6.54, -0.36, -0.25, 1.13, -0.5]
        static let lean: [Double] = [9.82, -11.66, 1.96, 0.1, -0.05, 0.22, -0.16, 0.03, -0.01]
        static let centroid: CGPoint = CGPoint(x: 16.5, y: 251.3)
        static let leanPerRadian: Double = -0.9426
        static let leanAtZero: Double = -2.078
        static let attach: CGPoint = CGPoint(x: 3, y: -22)
        static let sourceBox = CGRect(x: -100, y: -70, width: 260, height: 350)
    }

    enum ReplicaCord {
        static let frameDuration: Double = 0.25
        static let y: [Double] = [300, 360, 420, 480, 540, 600, 660, 720, 780]
        static let x: [[Double]] = [
            [449.9, 442.3, 430.5, 415.6, 399.0, 382.0, 365.8, 351.7, 341.0],
            [464.8, 453.7, 441.3, 427.9, 414.0, 400.1, 386.6, 373.9, 362.4],
            [488.3, 473.8, 461.4, 450.6, 440.9, 431.7, 422.6, 412.8, 402.0],
            [514.2, 504.5, 496.5, 489.9, 484.3, 479.5, 475.0, 470.6, 465.7],
            [537.1, 532.4, 529.3, 527.5, 526.6, 526.4, 526.7, 527.1, 527.4],
            [554.3, 561.4, 565.8, 568.5, 570.6, 573.0, 576.8, 582.9, 592.4],
            [561.5, 579.9, 590.5, 596.0, 598.8, 601.6, 607.1, 617.8, 636.4],
            [566.2, 590.6, 604.7, 611.8, 615.5, 619.0, 625.9, 639.5, 663.3],
            [566.1, 591.2, 605.6, 612.8, 616.4, 619.9, 626.8, 640.6, 664.7],
            [562.4, 581.9, 593.2, 598.9, 601.8, 604.7, 610.3, 621.5, 641.0],
            [555.4, 564.5, 570.0, 573.1, 575.3, 577.7, 581.8, 588.6, 599.6],
            [540.1, 536.2, 533.8, 532.7, 532.5, 533.1, 534.0, 535.1, 536.0],
            [517.0, 508.2, 501.0, 495.1, 490.3, 486.1, 482.3, 478.5, 474.5],
            [491.5, 477.2, 465.2, 454.9, 445.7, 437.1, 428.5, 419.4, 409.1],
            [467.4, 456.2, 443.8, 430.6, 417.1, 403.5, 390.4, 378.1, 367.2],
            [450.9, 443.0, 431.1, 416.4, 400.0, 383.2, 367.2, 353.2, 342.3]
        ]
    }

    enum Flick {
        // RN motion.ts:68.
        static let speed: Double = 900.0
        static let min: Double = 24.0
    }

    // RN motion.ts:69-72.
    static let tick: Double = 22.0

    enum Wash {
        static let duration: Double = 0.7
    }

    enum Morph {
        static let duration: Double = 0.5
    }

    enum Chevron {
        static let period: Double = 1.5
        static let rise: Double = 18.0
        static let count: Int = 3
        static let handsetOffset: Double = 26.0
        static let halfWidth: Double = 7.0
        static let lowerY: Double = 4.0
        static let upperY: Double = -2.0
        static let stroke: Double = 2.2
        static let alpha: Double = 0.7
    }

    enum Status {
        static let fadeOut: Double = 0.12
        static let fadeIn: Double = 0.2
    }

    enum Press {
        static let scale: Double = 0.95
        static let inDuration: Double = 0.12
        static let outDuration: Double = 0.16
        static let control1 = CGPoint(x: 0.23, y: 1)
        static let control2 = CGPoint(x: 0.32, y: 1)
    }

    /// Convenience alias for simulation code that groups QA flags with motion.
    static let replicaOnly: Bool = SlideToAnswerCordTheme.replica
}
