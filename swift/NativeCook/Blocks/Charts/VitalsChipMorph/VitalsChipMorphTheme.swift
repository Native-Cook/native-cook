import CoreGraphics
import SwiftUI

/// Reference palette, typography, spacing and layout for Vitals Chip Morph.
///
/// The reference design is measured in ref px at 1206 × 2622 (402 pt wide). The
/// view scales these values by `screenWidth / REF_W` when drawing on a device.
enum VitalsChipMorphTheme {
    static let REF_W = 1206.0
    /// The pin's phone has a 62 pt top safe area (186 ref px).
    static let REF_INSET = 186.0
    /// Font scale reference: 1206 ref px = 402 pt.
    static let REF_PT = 402.0

    enum Colors {
        private static func rgb(_ value: UInt32, opacity: Double = 1) -> Color {
            Color(
                .sRGB,
                red: Double((value >> 16) & 0xff) / 255,
                green: Double((value >> 8) & 0xff) / 255,
                blue: Double(value & 0xff) / 255,
                opacity: opacity
            )
        }

        static let page = rgb(0xF2F1F7)
        static let chip = rgb(0xF4F3F8)
        static let chipBorder = rgb(0xD2D1D8)
        static let chipActive = rgb(0xFFFFFF)
        static let ink = rgb(0x000000)
        static let muted = rgb(0x88878D)
        static let faint = rgb(0xB9B8C0)
        static let hairline = rgb(0xD9D8DF)

        // Readiness
        static let track = rgb(0xDFDFE9)
        static let segment = rgb(0xB2D3F2)
        static let blue = rgb(0x1592EA)
        static let blueGlow = rgb(0xD4E9FB)
        static let link = rgb(0x1E88E5)
        static let check = rgb(0x1E8DE6)
        static let iconGrey = rgb(0x6E6E73)
        static let band = rgb(0xCFE3F8)
        static let bandLine = rgb(0xD7D7DD)

        // Fitness rings: start → end of each progress gradient and its track.
        static let moveStart = rgb(0xFA0232)
        static let moveEnd = rgb(0xFD0172)
        static let moveTrack = rgb(0xF6C0CE)
        static let exerciseStart = rgb(0x05DD04)
        static let exerciseEnd = rgb(0x78E402)
        static let exerciseTrack = rgb(0xC4EDC4)
        static let standStart = rgb(0x02C1DB)
        static let standEnd = rgb(0x02D4B9)
        static let standTrack = rgb(0xB5E5F1)
        static let glyph = rgb(0x14060B)
        static let movePink = rgb(0xE4365F)
        static let standCyan = rgb(0x14B4CF)
        static let exerciseText = rgb(0x4BD12D)

        // Heart
        static let heart = rgb(0xF42E59)
        static let heartFlat = rgb(0xFB4F78)
        static let heartHalo = rgb(0xF7C3CF)
        static let heartDark = rgb(0xE52650)
        static let chartPink = rgb(0xF09CAF)
        static let bpmUnit = rgb(0x55343D)
        static let supportValue = rgb(0x55545A)

        // Chip icons
        static let sleepTeal = rgb(0x1FCFB0)
        static let sleepCoral = rgb(0xF47A5C)
        static let sleepBlue = rgb(0x3D63F0)
        static let vitalsPink = rgb(0xDF61D2)
        static let vitalsBlue = rgb(0x378DDD)
        static let summaryHeartTop = rgb(0xFF5C95)
        static let summaryHeartBottom = rgb(0xF0246A)

        // Tab bar
        static let tabGlass = rgb(0xF8F8FC, opacity: 0.78)
        static let tabGlassTint = rgb(0xFAFAFD, opacity: 0.6)
        static let tabRim = rgb(0xFFFFFF, opacity: 0.9)
        static let tabActive = rgb(0x767680, opacity: 0.14)
        static let tabBlue = rgb(0x1F8BF0)
    }

    /// The RN source exposes `colors`; keep the same lower-case access path.
    static let colors = Colors.self

    /// A text style measured at the 402 pt reference width.
    struct TextSpec {
        let size: Double
        let weight: String
        let color: Color
        let ls: Double
        let rounded: Bool

        init(size: Double, weight: String, color: Color, ls: Double, rounded: Bool = false) {
            self.size = size
            self.weight = weight
            self.color = color
            self.ls = ls
            self.rounded = rounded
        }

        /// Maps the RN numeric weight strings to SwiftUI's system weights.
        var fontWeight: Font.Weight {
            switch weight {
            case "700": return .bold
            case "600": return .semibold
            case "500": return .medium
            case "300": return .light
            case "200": return .ultraLight
            case "100": return .thin
            default: return .regular
            }
        }
    }

    enum Typography {
        static let chipTitle = TextSpec(size: 15.2, weight: "400", color: Colors.muted, ls: 0)
        static let chipValue = TextSpec(size: 15, weight: "600", color: Colors.ink, ls: 0)
        static let readiness = TextSpec(size: 77, weight: "600", color: Colors.blue, ls: 0, rounded: true)
        static let bpm = TextSpec(size: 76, weight: "600", color: Colors.ink, ls: -2.2)
        static let bpmUnit = TextSpec(size: 16, weight: "500", color: Colors.bpmUnit, ls: 0)
        static let heading = TextSpec(size: 30.8, weight: "700", color: Colors.ink, ls: 0)
        static let body = TextSpec(size: 21.8, weight: "400", color: Colors.muted, ls: 0)
        static let section = TextSpec(size: 21.1, weight: "700", color: Colors.ink, ls: 0)
        static let link = TextSpec(size: 15.2, weight: "600", color: Colors.link, ls: 0)
        static let value = TextSpec(size: 18.4, weight: "700", color: Colors.ink, ls: 0)
        static let stat = TextSpec(size: 15.5, weight: "600", color: Colors.movePink, ls: 0)
        static let metricLink = TextSpec(size: 14.5, weight: "600", color: Colors.heartDark, ls: 0)
        static let metricValue = TextSpec(size: 20.5, weight: "600", color: Colors.ink, ls: -0.2)
        static let metricSub = TextSpec(size: 14, weight: "400", color: Colors.faint, ls: 0)
        static let axis = TextSpec(size: 11, weight: "400", color: Colors.faint, ls: 0)
        static let chartTitle = TextSpec(size: 15, weight: "600", color: Colors.ink, ls: 0)
        static let tab = TextSpec(size: 11.2, weight: "500", color: Colors.ink, ls: 0)
    }

    /// The RN source exposes `type`; keep the same lower-case access path.
    static let type = Typography.self
    /// Pages and the Swift port commonly call the same scale `typeScale`.
    static let typeScale = Typography.self

    enum Space {
        /// Left edge of every text column (32 pt).
        static let screenInset = 96.0
        /// Gap between two chips (7.5 pt, measured; not on the grid).
        static let chipGap = 22.5
        /// Chip padding before the icon centre (28 pt) and before the label (54.7 pt).
        static let chipIconCentre = 84.0
        static let chipLabel = 161.0
        /// Cap tops of a chip's title and value, from the chip's top.
        static let chipTitleCap = 39.0
        static let chipValueCap = 97.0
        /// A link row: the check's left edge to the label.
        static let linkLabel = 50.0
        /// Body line height (26 pt) and heading line height (36 pt).
        static let bodyLine = 78.0
        static let headingLine = 108.0
        /// A metric block: label cap top → value cap top → sub-label cap top.
        static let metricValue = 69.0
        static let metricSub = 146.0
        /// Distance a page slides while it hands over to the next one (24 pt).
        static let pageSlide = 72.0
    }

    static let space = Space.self

    enum Radius {
        /// Radii in ref px. Nested radii are outer − padding.
        static let chip = 66.0
        static let tabBar = 93.0
        static let tabPill = 81.0
    }

    static let radius = Radius.self

    struct ChipSpec {
        let key: String
        let title: String
        let value: String
        let x: Double
        let w: Double
    }

    static let CHIP_W: [Double] = [175, 315.5, 420, 446, 355, 339]
    static let CHIP_X: [Double] = {
        var result: [Double] = []
        result.reserveCapacity(CHIP_W.count)
        var x = 51.0
        for width in CHIP_W {
            result.append(x)
            x += width + Space.chipGap
        }
        return result
    }()

    static let chips: [ChipSpec] = [
        ChipSpec(key: "summary", title: "Summary", value: "", x: CHIP_X[0], w: CHIP_W[0]),
        ChipSpec(key: "sleep", title: "Sleep", value: "High", x: CHIP_X[1], w: CHIP_W[1]),
        ChipSpec(key: "readiness", title: "Readiness", value: "Ready", x: CHIP_X[2], w: CHIP_W[2]),
        ChipSpec(key: "fitness", title: "Fitness", value: "5,253 steps", x: CHIP_X[3], w: CHIP_W[3]),
        ChipSpec(key: "heart", title: "Heart", value: "62 BPM", x: CHIP_X[4], w: CHIP_W[4]),
        ChipSpec(key: "vitals", title: "Vitals", value: "Typical", x: CHIP_X[5], w: CHIP_W[5]),
    ]

    /// Chip index of each screen (Readiness, Fitness, Heart).
    static let SCREEN_CHIP: [Int] = [2, 3, 4]

    /// The screen each chip opens. Summary and Sleep show Readiness (its "Sleep Score" row and summary copy),
    /// Vitals shows Heart; the selection then lands on that screen's chip, as a tap on Heart does.
    static let CHIP_SCREEN: [Int] = [0, 0, 0, 1, 2, 2]
    static let SCREEN_NAMES: [String] = ["Readiness", "Fitness", "Heart"]

    enum Layout {
        struct Gauge {
            let cx: Double
            let cy: Double
            let r: Double
            let stroke: Double
            let from: Double
            let to: Double
        }

        struct Segment {
            let t0: Double
            let t1: Double
            let corner: Double
        }

        struct Dot {
            let lag: Double
            let d: Double
            let glow: Double
        }

        struct Seven {
            let cx: Double
            let capTop: Double
        }

        struct Rings {
            let cx: Double
            let cy: Double
            let stroke: Double
            let r: [Double]
            let progress: [Double]
        }

        struct Heart {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let cx: Double
            let cy: Double
        }

        struct BPM {
            let capTop: Double
            let unitCapTop: Double
        }

        struct CheckGroup {
            let icon: Double
            let check: Double
        }

        struct Checks {
            let cy: Double
            let groups: [CheckGroup]
            let dividers: [Double]
            let checkD: Double
            let iconD: Double
            let dividerH: Double
        }

        struct MiniChart {
            let x: Double
            let w: Double
            let lineTop: Double
            let bandTop: Double
            let bandH: Double
            let lineBottom: Double
            let iconsCy: Double
            let dots: [CGPoint]
            let dotD: Double
        }

        struct SleepRing {
            let cx: Double
            let cy: Double
            let r: Double
            let stroke: Double
        }

        struct FitChart {
            let x: Double
            let w: Double
            let rowsCy: [Double]
            let labelsTop: Double
            let base: Double
            let barX: Double
            let barPitch: Double
            let barW: Double
            let dotX: Double
            let dotPitch: Double
            let dotR: Double
            let labelPitch: Double
            let gridW: Double
        }

        struct FitStats {
            let x: Double
            let capTops: [Double]
        }

        struct HeartChart {
            let titleTop: Double
            let axisCapTop: Double
            let axisRightX: Double
        }

        struct TabPill {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
        }

        struct TabBar {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let pill: TabPill
            let iconCy: Double
            let labelCapTop: Double
            let centres: [Double]
        }

        static let chipTop = 186.0
        static let chipH = 174.0
        /// Row scroll offset per screen; the active chip sits at x 585.5 / 377 / 427.
        static let rowOffset: [Double] = [0, 377 - VitalsChipMorphTheme.CHIP_X[3], 427 - VitalsChipMorphTheme.CHIP_X[4]]

        /// The hero canvas spans the full width from y 400 to 1420 ref px.
        static let heroTop = 400.0
        static let heroH = 1020.0
        /// The hero's touch area stops above the headline.
        static let heroTouchH = 800.0

        // Readiness gauge (screen coordinates)
        static let gauge = Gauge(cx: 603, cy: 1072, r: 455, stroke: 130, from: 167.5, to: 12.5)
        /// The light segment uses butt ends with 40 px corners.
        static let segment = Segment(t0: 0.4510, t1: 0.7303, corner: 40)
        static let dot = Dot(lag: 0.0335, d: 76, glow: 150)
        static let seven = Seven(cx: 601.5, capTop: 861)

        // Fitness rings (screen coordinates); progress starts at 12 o'clock clockwise.
        static let rings = Rings(cx: 608, cy: 851.5, stroke: 88, r: [359, 262, 165], progress: [0.75, 0.633, 0.333])

        // Heart: rendered artwork canvas registered to the pin.
        static let heart = Heart(x: 213, y: 488, w: 780, h: 750, cx: 601.5, cy: 863)
        static let bpm = BPM(capTop: 718, unitCapTop: 963)

        // Readiness page
        static let checks = Checks(
            cy: 1206,
            groups: [
                CheckGroup(icon: 280, check: 370),
                CheckGroup(icon: 562, check: 642),
                CheckGroup(icon: 836, check: 915),
            ],
            dividers: [465, 742],
            checkD: 50,
            iconD: 56,
            dividerH: 60
        )
        static let pageHeadingCapTop = 1366.0
        static let readyBodyTop = 1480.0
        static let raisingCapTop = 1891.0
        static let vitalsLinkCapTop = 2021.0
        static let vitalsChevronX = 520.0
        static let sleepChevronX = 412.0
        static let typicalCapTop = 2085.0
        static let miniChart = MiniChart(
            x: 668,
            w: 444,
            lineTop: 2006,
            bandTop: 2023,
            bandH: 106,
            lineBottom: 2137,
            iconsCy: 2186,
            dots: [
                CGPoint(x: 720, y: 2099),
                CGPoint(x: 802, y: 2059),
                CGPoint(x: 887, y: 2097),
                CGPoint(x: 972, y: 2087),
                CGPoint(x: 1057, y: 2074),
            ],
            dotD: 26
        )
        static let sleepLinkCapTop = 2284.0
        static let highCapTop = 2342.0
        static let sleepRing = SleepRing(cx: 1003, cy: 2372, r: 80, stroke: 30)

        // Fitness page
        static let fitBodyTop = 1590.0
        static let fitChart = FitChart(
            x: 96,
            w: 450,
            rowsCy: [1852, 1940, 2006],
            labelsTop: 2052,
            base: 28,
            barX: 130,
            barPitch: 10,
            barW: 4.2,
            dotX: 146,
            dotPitch: 20,
            dotR: 7,
            labelPitch: 135,
            gridW: 0.88
        )
        static let fitStats = FitStats(x: 620, capTops: [1852, 1919, 1987])
        static let fitMetricsCapTop = 2160.0

        // Heart page (copied by value from pulse-heart-card)
        static let heartBodyTop = 1578.0
        static let heartChart = HeartChart(titleTop: 1907, axisCapTop: 2169, axisRightX: 1040)
        static let heartRightColumn = 636.0
        static let heartMetricsTop = 2247.0

        /// Content slid under the tab bar's glass is soft (Gaussian σ in ref px).
        static let underGlassBlur = 7.0
        static let tabBar = TabBar(
            x: 169,
            y: 2369,
            w: 867,
            h: 189,
            pill: TabPill(x: 177, y: 2386, w: 295, h: 164),
            iconCy: 2441,
            labelCapTop: 2499,
            centres: [326, 600, 876]
        )
    }

    /// The RN source exposes `layout`; keep the same lower-case access path.
    static let layout = Layout.self

    /// The liquid material's studio, bevel, wobble, melt and heartbeat values.
    enum LiquidLook {
        static let skyTop = SIMD3<Double>(255, 255, 255)
        static let skyLow = SIMD3<Double>(216, 220, 232)
        static let horizon = SIMD3<Double>(118, 122, 140)
        static let floorNear = SIMD3<Double>(198, 201, 214)
        static let floorFar = SIMD3<Double>(240, 241, 246)
        static let softSigma = 15.0
        static let gooCentre = CGPoint(x: 605, y: 1195)
        static let gooRadius = 260.0
        static let sampleRadius = 64.0
        static let slopeStep = 3.0
        static let slope = 70.0
        static let refractPx = 12.0
        static let wobblePx = 7.0
        static let ripSpeed = 820.0
        static let ripWidth = 70.0
        static let ripDecay = 0.5
        static let ripBirth = 60.0
        static let ripK = 0.0982
        static let ripPx = 3.5
        static let ripSlope = 0.95
        static let ripLight = 1.0
    }

    static let liquidLook = LiquidLook.self

    /// The chip row's Liquid Glass lens.
    enum Lens {
        static let liftScale = 1.06
        static let tint = Color(.sRGB, white: 1, opacity: 0.28)
        static let onFrom = 0.02
        static let offAt = 0.97
        static let fade = 0.22
    }

    static let lens = Lens.self
}
