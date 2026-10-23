import SwiftUI

/// Owl Alarm Ruler — every colour, size and position of the screen, in pt.
///
/// The values mirror the React Native theme token for token. The layout is measured from the
/// pin's 402 × 874 pt reference screen and is anchored to the safe-area edges so the flexible
/// width can change without changing the measured gaps.
enum OwlAlarmRulerTheme {
    /// The pin's top safe area (pt): reference y values below are "pin y − this".
    static let PIN_INSET: Double = 62

    struct Space {
        let screenInset: Double
        let backInset: Double
        let headerTop: Double
        let ctaPadStart: Double
        let ctaPadEnd: Double
        let ctaBottom: Double
        let hit: Double
    }

    static let space = Space(
        screenInset: 26,
        backInset: 24,
        headerTop: 10,
        ctaPadStart: 19,
        ctaPadEnd: 22.3,
        ctaBottom: 20,
        hit: 44
    )

    struct Radius {
        let cta: Double
        let pill: Double
    }

    static let radius = Radius(cta: 18, pill: 999)

    struct Colors {
        let page: Color
        let band: Color
        let shadow: Color
        let backFill: Color
        let ink: Color
        let track: Color
        let caption: Color
        let cta: Color
        let white: Color
        let needle: Color
        let tickDark: [Double]
        let tickLight: [Double]
        let lid: Color
        let lash: Color
    }

    private static func color(_ hex: UInt32) -> Color {
        Color(
            red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255
        )
    }

    static let colors = Colors(
        page: color(0xEDEDED),
        band: color(0xE3E3E3),
        shadow: color(0xC4C4C5),
        backFill: color(0xDADADA),
        ink: color(0x000000),
        track: color(0xCCCCCC),
        caption: color(0x5F5F5F),
        cta: color(0xBC4A20),
        white: color(0xFFFFFF),
        needle: color(0xCC3F20),
        tickDark: [44, 41, 38],
        tickLight: [194, 193, 192],
        lid: color(0xF9F8F6),
        lash: color(0x3A3A3A)
    )

    struct Copy {
        let line1: String
        let line2: String
        let caption: String
        let cta: String
        let a11yRuler: String
        let a11yRulerHint: String
        let a11yBack: String
    }

    static let copy = Copy(
        line1: "You chose your goal.",
        line2: "Now set the alarm.",
        caption: "We’ll begin with one alarm. No backups.",
        cta: "Set alarm for",
        a11yRuler: "Alarm time",
        a11yRulerHint: "Swipe up or down to change by five minutes.",
        a11yBack: "Back"
    )

    /// SF Pro / SF Pro Rounded: the baseline sits 0.953 em under the top of a Text with no lineHeight.
    static let ASCENT: Double = 0.953

    struct TextStyleSpec {
        let size: Double
        let weight: String
        let color: Color
        let ls: Double
    }

    struct Typography {
        let headline: TextStyleSpec
        let time: TextStyleSpec
        let meridiem: TextStyleSpec
        let caption: TextStyleSpec
        let cta: TextStyleSpec
    }

    /// Swift cannot use the TypeScript object name as a type declaration; `type` remains the
    /// value-level token and `typography` is an equivalent descriptive alias for Swift callers.
    static let typography = Typography(
        headline: TextStyleSpec(size: 30, weight: "600", color: colors.ink, ls: -1.25),
        time: TextStyleSpec(size: 78, weight: "800", color: colors.ink, ls: 0),
        meridiem: TextStyleSpec(size: 25, weight: "600", color: colors.ink, ls: 0),
        caption: TextStyleSpec(size: 14.2, weight: "400", color: colors.caption, ls: 0),
        cta: TextStyleSpec(size: 20, weight: "500", color: colors.white, ls: 0.36)
    )

    /// Value-for-value alias for the exported TypeScript `type` object.
    static let type = typography

    struct Diagonal {
        let y0: Double
        let slope: Double
    }

    struct SundialPivot {
        let dx: Double
        let y: Double
    }

    struct BackArrow {
        let tipX: Double
        let armX: Double
        let armDy: Double
        let endX: Double
        let stroke: Double
    }

    struct Back {
        let d: Double
        let arrow: BackArrow
    }

    struct Track {
        let w: Double
        let h: Double
        let fill: Double
    }

    struct Mascot {
        let w: Double
        let h: Double
        let dx: Double
        let y: Double
        let pivotY: Double
    }

    struct Eye {
        let cx: Double
        let cy: Double
        let r: Double
    }

    struct PupilOffset {
        let x: Double
        let y: Double
    }

    struct Pupil {
        let d: Double
        let rest: [PupilOffset]
    }

    struct GroundShadow {
        let dx: Double
        let cy: Double
        let rx: Double
        let ry: Double
    }

    struct Baseline {
        let base: Double
    }

    struct Time {
        let base: Double
        let dx: Double
        let colonGap: Double
        let tight: Double
        let colonRise: Double
    }

    struct Meridiem {
        let base: Double
        let dx: Double
        let w: Double
    }

    struct Needle {
        let w: Double
        let h: Double
        let cy: Double
    }

    struct Band {
        let top: Double
        let h: Double
    }

    struct Touch {
        let top: Double
        let h: Double
    }

    struct Ruler {
        let spacing: Double
        let base: Double
        let tickW: Double
        let fade: Double
        let needle: Needle
        let band: Band
        let touch: Touch
    }

    struct Caption {
        let base: Double
    }

    struct CTA {
        let h: Double
        let base: Double
    }

    struct CTAArrow {
        let shaft: Double
        let stroke: Double
        let headW: Double
        let headH: Double
        let headR: Double
    }

    struct Layout {
        let diagonal: Diagonal
        let sundialPivot: SundialPivot
        let back: Back
        let track: Track
        let mascot: Mascot
        let eyes: [Eye]
        let pupil: Pupil
        let lash: Double
        let groundShadow: GroundShadow
        let headline: [Baseline]
        let time: Time
        let meridiem: Meridiem
        let ruler: Ruler
        let caption: Caption
        let cta: CTA
        let ctaArrow: CTAArrow
    }

    private static func pt(_ px: Double) -> Double { px / 3 }
    private static func y(_ px: Double) -> Double { px / 3 - PIN_INSET }

    static let layout = Layout(
        diagonal: Diagonal(y0: y(584), slope: 400.0 / 1206.0),
        sundialPivot: SundialPivot(dx: 0, y: y(584.0 + 603.0 * (400.0 / 1206.0))),
        back: Back(
            d: pt(126),
            arrow: BackArrow(
                tipX: pt(110.5 - 72),
                armX: pt(129.5 - 72),
                armDy: pt(19.5),
                endX: pt(158.9 - 72),
                stroke: pt(6.3)
            )
        ),
        track: Track(w: 180, h: pt(33), fill: pt(196)),
        mascot: Mascot(w: pt(660), h: pt(576), dx: pt(275) + pt(660) / 2 - 201, y: y(414), pivotY: 563.0 / 576.0),
        eyes: [
            Eye(cx: pt(224), cy: pt(246), r: pt(70.5)),
            Eye(cx: pt(454), cy: pt(248), r: pt(72.5))
        ],
        pupil: Pupil(
            d: pt(60),
            rest: [PupilOffset(x: pt(22), y: pt(24)), PupilOffset(x: pt(-22), y: pt(24))]
        ),
        lash: pt(12),
        groundShadow: GroundShadow(dx: pt(588) - 201, cy: y(947), rx: pt(307), ry: pt(50)),
        headline: [Baseline(base: y(1155)), Baseline(base: y(1253))],
        time: Time(base: y(1564), dx: pt(17.5), colonGap: 1.3, tight: -5.4, colonRise: pt(21)),
        meridiem: Meridiem(base: y(1692), dx: -0.5, w: 60),
        ruler: Ruler(
            spacing: pt(34.7),
            base: y(1849),
            tickW: 3,
            fade: 0.6,
            needle: Needle(w: 7, h: pt(93), cy: y(1816)),
            band: Band(top: y(1730), h: pt(170)),
            touch: Touch(top: y(1700), h: pt(230))
        ),
        caption: Caption(base: y(2046)),
        cta: CTA(h: pt(196), base: pt(2384.0 - 2265.0)),
        ctaArrow: CTAArrow(shaft: pt(1036.0 - 997.5), stroke: pt(7.5), headW: pt(30.5), headH: pt(37.5), headR: 1)
    )

    /// The time the pin shows: 1:05 PM, in minutes after midnight.
    static let REST_MINUTES: Double = 13 * 60 + 5
    static let STEP_MINUTES: Double = 5
    static let TICKS_PER_DAY: Double = (24 * 60) / STEP_MINUTES
}
