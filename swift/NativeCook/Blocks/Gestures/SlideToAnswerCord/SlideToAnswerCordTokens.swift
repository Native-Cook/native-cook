import CoreImage
import SwiftUI
import UIKit

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

        // RN theme.ts:7-8.
        static let sky: [Color] = [
            hex(0x050409), hex(0x050409), hex(0x111226),
            hex(0x1C1C3E), hex(0x2A2B6C), hex(0x262767)
        ]
        static let skyStops: [Double] = [0.0, 0.39, 0.47, 0.55, 0.78, 1.0]
        static let skyTop: Color = hex(0x050409)
        static let skyMid: Color = hex(0x050409)
        static let horizon: Color = hex(0x1C1D42)
        static let ground: Color = hex(0x2A2B6C)
        static let groundConnected: Color = hex(0x0E3D3A)
        static let groundEnded: Color = hex(0x3A1328)
        static let grain: Double = 0.07

        // RN theme.ts:17-35. Neon handset and control colours.
        static let ring: Color = hex(0xFF1F8E)
        static let ringDeep: Color = hex(0xC40F6A)
        static let connected: Color = hex(0x2BE36A)
        static let connectedDeep: Color = hex(0x12A548)
        static let off: Color = hex(0x5B2348)
        static let offDeep: Color = hex(0x3A1630)
        static let shell: Color = rgba(92.0, 52.0, 178.0, 0.78)
        static let shellEdge: Color = rgba(164.0, 128.0, 255.0, 0.55)
        static let grille: Color = rgba(60.0, 0.0, 40.0, 0.55)
        static let bloom: Color = hex(0xFFFFFF)
        static let cordBack: Color = hex(0x2C2178)
        static let cordFront: Color = hex(0x7448E6)
        static let cordEdge: Color = hex(0xFF4FAE)
        static let replicaCord: Color = hex(0x302A88)
        static let replicaRim: Color = hex(0xA5237D)
        static let replicaRimLeft: Color = hex(0x4C40B4)
        static let ink: Color = hex(0xF4F2FF)
        static let mute: Color = rgba(244.0, 242.0, 255.0, 0.58)
        static let decline: Color = hex(0xFF1450)
        static let accept: Color = hex(0x22E04A)
        static let glyph: Color = hex(0xFFFFFF)
        static let label: Color = rgba(244.0, 242.0, 255.0, 0.62)
    }

    enum Handset {
        // RN theme.ts:41-44. The source asset is 480 × 600 px at 0.348 pt/px.
        static let imageScale: Double = 0.348
        static let imageX: Double = -200.0 * 0.348
        static let imageY: Double = -20.0 * 0.348
        static let imageW: Double = 480.0 * 0.348
        static let imageH: Double = 600.0 * 0.348
        static let earX: Double = 12.0
        static let earY: Double = 23.0
        static let mouthX: Double = 12.0
        static let mouthY: Double = 172.0
        static let comX: Double = 2.0
        static let comY: Double = 98.0
        static let bloom: Double = 20.0
        static let bloomX: Double = 14.0
        static let bloomY: Double = -14.0
        static let hit: Double = 190.0
        static let restTilt: Double = -0.33
        static let offOpacity: Double = 0.42
    }

    enum Cord {
        // RN theme.ts:62-79.
        static let anchorY: Double = -12.0
        static let replicaAnchorDX: Double = -26.0
        static let replicaTurns: Double = 9.7
        static let replicaWave: Double = 7.0
        static let replicaPhase: Double = -3.98
        static let replicaRoll: [(time: Double, angle: Double)] = [
            (0, 0), (1, -0.39), (1.875, -0.22), (2.9, -0.39), (4, 0)
        ]
        static let replicaWidth: Double = 7.0
        static let replicaRimDX: Double = 2.7
        static let replicaRimWidthDelta: Double = 1.0
        static let replicaLeftRimDXMultiplier: Double = 0.8
        static let restRatio: Double = 0.335
        static let links: Int = 14
        static let turns: Int = 20
        static let wire: Double = 90.0
        static let width: Double = 2.6
        static let edge: Double = 1.1
        static let maxRadius: Double = 9.0
        static let samples: Int = 8
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
        static let nameTopRatio: Double = 0.655
        static let copyTopRatio: Double = 0.645
        static let nameSize: Double = 34.0
        static let nameLine: Double = 40.0
        static let statusSize: Double = 17.0
        static let statusLine: Double = 22.0
        static let gap: Double = 8.0
        static let buttonsBottom: Double = 40.0
        static let button: Double = 64.0
        static let buttonGap: Double = 96.0
        static let labelSize: Double = 13.0
        static let labelGap: Double = 8.0
        static let labelWidth: Double = 120.0
        static let glyph: Double = 26.0
        static let metaSize: Double = 13
        static let titleTracking: Double = -0.4
        static let controlMinimumWidth: Double = 64
        static let controlHeight: Double = 52
        static let controlRadius: Double = 26
        static let controlIconSize: Double = 20
        static let controlLabelSize: Double = 10
        static let controlLabelGap: Double = 4
        static let controlOn: Color = Colors.rgba(255, 255, 255, 0.9)
        static let controlInk: Color = Colors.hex(0x16122E)
        static let controlLabel: Color = Colors.rgba(255, 255, 255, 0.75)
    }

    enum Copy {
        struct Control {
            let name: String
            let label: String
            let symbol: String
        }

        // RN theme.ts:89-103.
        static let meta: String = "mobile · Stockholm"
        static let controls: [Control] = [
            Control(name: "mute", label: "Mute", symbol: "mic.slash.fill"),
            Control(name: "keypad", label: "Keypad", symbol: "circle.grid.3x3.fill"),
            Control(name: "speaker", label: "Speaker", symbol: "speaker.wave.2.fill")
        ]
        static let name: String = "Mara Lindqvist"
        static let ringing: String = "Incoming call · lift to answer"
        static let declined: String = "Call declined"
        static let ended: String = "Call ended"
        static let decline: String = "Decline"
        static let accept: String = "Accept"
        static let end: String = "End"
        static let a11yHandset: String = "Handset"
        static let a11yHint: String = "Swipe up to answer, swipe down to decline"
    }

    // QA switch from RN theme.ts:108. Upgrade layers remain enabled in the shipped state.
    static let replica: Bool = false

    enum Glass {
        // RN theme.ts:112-121.
        static let blur: Double = 18.0
        static let decline: Color = Colors.rgba(255.0, 20.0, 80.0, 0.55)
        static let accept: Color = Colors.rgba(34.0, 224.0, 74.0, 0.5)
        static let neutral: Color = Colors.rgba(255.0, 255.0, 255.0, 0.12)
        static let edgeTop: Color = Colors.rgba(255.0, 255.0, 255.0, 0.75)
        static let edgeBottom: Color = Colors.rgba(255.0, 255.0, 255.0, 0.08)
        static let pillY: Double = -96.0
        static let pillH: Double = 64.0
        static let field: Double = 300.0
        static let fieldAlpha: Double = 0.35
        static let fieldWashTop: Double = 0.85
        static let fieldWashAlpha: Double = 0.5
        static let haloExtra: Double = 6.0
        static let haloGrowth: Double = 8.0
        static let haloBlur: Double = 16.0
        static let haloBaseAlpha: Double = 0.35
        static let haloGrowthAlpha: Double = 0.65
        static let edgeWidth: Double = 1.2
        static let rimWidth: Double = 1.5
        static let rimAlpha: Double = 0.45
        static let disabledAlpha: Double = 0.55
        static let glyphFadeMultiplier: Double = 3.0
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
        static let resetAfter: Double = 2.0
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
        static let stroke: Double = 2.0
        static let blur: Double = 3.0
    }

    enum Haptic {
        // RN motion.ts:49.
        static let grab: Int = 1
        static let answer: Int = 2
        static let decline: Int = 3
        static let tick: Int = 4
    }

    static let loopMS: Double = 12_000.0
    static let resumeMS: Double = 3_500.0
    /// Spelled out for the fidelity matrix; this is the unchanged RN literal.
    static let resumeMilliseconds: Double = 3_500.0
    /// Swift port policy: interactive idle returns to the saved rest state after 2 s.
    /// The RN autoplay resume timer remains `resumeMS` above.
    static let interactiveIdleBeforeReturn: Double = 2.0
    static let prerollLoops: Int = 3
    static let maximumFrameDelta: Double = 0.1

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
        ScriptSegment(start: 2.6, end: 3.0, fromDX: 0.0, fromDY: 0.0, toDX: 0.0, toDY: -10.0, downAtStart: 1, upAtEnd: 0),
        ScriptSegment(start: 3.0, end: 3.55, fromDX: 0.0, fromDY: -10.0, toDX: 6.0, toDY: -84.0, downAtStart: 0, upAtEnd: 0),
        ScriptSegment(start: 3.55, end: 3.7, fromDX: 6.0, fromDY: -84.0, toDX: 6.0, toDY: -84.0, downAtStart: 0, upAtEnd: 1),
        ScriptSegment(start: 7.4, end: 7.75, fromDX: 0.0, fromDY: 0.0, toDX: 0.0, toDY: 14.0, downAtStart: 1, upAtEnd: 0),
        ScriptSegment(start: 7.75, end: 8.25, fromDX: 0.0, fromDY: 14.0, toDX: -4.0, toDY: 92.0, downAtStart: 0, upAtEnd: 0),
        ScriptSegment(start: 8.25, end: 8.4, fromDX: -4.0, fromDY: 92.0, toDX: -4.0, toDY: 92.0, downAtStart: 0, upAtEnd: 1)
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

/// Flat bundle resources for the block. Every name is prefixed because Swift
/// compiles all blocks into the same application bundle namespace.
enum SlideToAnswerCordAssets {
    static let handset: UIImage = image(named: "SlideToAnswerCord-handset.png")
    static let handsetGreen: UIImage = image(named: "SlideToAnswerCord-handset-green.png")
    static let handsetShell: UIImage = image(named: "SlideToAnswerCord-handset-shell.png")
    static let handsetShellGreen: UIImage = image(named: "SlideToAnswerCord-handset-shell-green.png")
    static let sourceA: UIImage = image(named: "SlideToAnswerCord-handset-src-a.png")
    static let sourceRest: UIImage = image(named: "SlideToAnswerCord-handset-src-rest.png")
    static let sourceB: UIImage = image(named: "SlideToAnswerCord-handset-src-b.png")
    static let sourceAGreen: UIImage = green(image: sourceA)
    static let sourceRestGreen: UIImage = green(image: sourceRest)
    static let sourceBGreen: UIImage = green(image: sourceB)
    static let grain: UIImage = image(named: "SlideToAnswerCord-grain.png")

    static func image(_ name: String) -> UIImage {
        image(named: name)
    }

    static func assertAllLoaded() {
        #if DEBUG
        [
            ("SlideToAnswerCord-handset.png", handset),
            ("SlideToAnswerCord-handset-green.png", handsetGreen),
            ("SlideToAnswerCord-handset-shell.png", handsetShell),
            ("SlideToAnswerCord-handset-shell-green.png", handsetShellGreen),
            ("SlideToAnswerCord-handset-src-a.png", sourceA),
            ("SlideToAnswerCord-handset-src-rest.png", sourceRest),
            ("SlideToAnswerCord-handset-src-b.png", sourceB),
            ("SlideToAnswerCord-grain.png", grain)
        ].forEach { name, resource in
            assert(resource.size != .zero, "Missing Slide To Answer Cord asset: \(name)")
        }
        #endif
    }

    private static func image(named name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Slide To Answer Cord asset: \(name)")
            return UIImage()
        }
        return image
    }

    private static func green(image: UIImage) -> UIImage {
        guard let input = CIImage(image: image),
              let filter = CIFilter(name: "CIColorMatrix")
        else { return image }
        filter.setValue(input, forKey: kCIInputImageKey)
        filter.setValue(CIVector(x: -0.599, y: 1.142, z: 0.457, w: 0), forKey: "inputRVector")
        filter.setValue(CIVector(x: 0.462, y: 0.495, z: 0.043, w: 0), forKey: "inputGVector")
        filter.setValue(CIVector(x: 0.144, y: 1.631, z: -0.775, w: 0), forKey: "inputBVector")
        filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        guard let output = filter.outputImage,
              let cgImage = CIContext().createCGImage(output, from: input.extent)
        else { return image }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
    }
}
