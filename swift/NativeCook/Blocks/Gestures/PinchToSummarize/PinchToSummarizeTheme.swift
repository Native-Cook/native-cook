import SwiftUI

/// The visual contract for the Pinch to Summarize block.
///
/// Values mirror `react-native/src/blocks/gestures/pinch-to-summarize/theme.ts`.
/// Layout measurements are points on the 402 × 874 reference screen.
enum PinchToSummarizeTheme {
    enum Colors {
        static let page: Color = Color(hex: 0xFFFFFF)
        static let pageClear: Color = Color.rgba(255, 255, 255, 0)
        static let ink: Color = Color(hex: 0x111216)
        static let inkSoft: Color = Color(hex: 0x5B5E68)
        static let rule: Color = Color(hex: 0xE7E7EC)
        static let panel: Color = Color(hex: 0x8E90A8)
        static let panelLow: Color = Color(hex: 0x3D4049)
        static let panelInk: Color = Color(hex: 0xFFFFFF)
        static let panelInkSoft: Color = Color.rgba(255, 255, 255, 0.78)
        static let pill: Color = Color(hex: 0xFFFFFF)
        static let pillInk: Color = Color(hex: 0x111216)
        static let toolbar: Color = Color(hex: 0xF1F1F4)
        static let toolbarInk: Color = Color(hex: 0x2A2C33)
        static let stackIcon: [Color] = [
            Color(hex: 0xFF9A3D),
            Color(hex: 0xFF4F8B),
            Color(hex: 0x3D63F2)
        ]
        static let accent: Color = Color(hex: 0x3D63F2)
        static let fieldCobalt: Color = Color(hex: 0x2F45F0)
        static let fieldIndigo: Color = Color(hex: 0x4A35D8)
        static let fieldViolet: Color = Color(hex: 0x8A43D6)
        static let fieldMagenta: Color = Color(hex: 0xE0569A)
        static let fieldDeep: Color = Color(hex: 0x1E2BB8)
        static let rim: [Color] = [
            Color(hex: 0x7FD4FF),
            Color(hex: 0x7C8CFF),
            Color(hex: 0xC77DFF),
            Color(hex: 0xFF7EB6),
            Color(hex: 0xFFC58A),
            Color(hex: 0x7FD4FF)
        ]
        static let summaryInk: Color = Color(hex: 0xFFFFFF)
        static let summaryInkSoft: Color = Color.rgba(255, 255, 255, 0.82)
        static let summaryArrive: Color = Color(hex: 0xD3C6FF)
        static let iconDisc: Color = Color(hex: 0x3D63F2)
        static let iconGlyph: Color = Color(hex: 0xFFFFFF)
        static let closeFill: Color = Color.rgba(255, 255, 255, 0.16)
        static let sweep: [Color] = [
            Color.rgba(255, 255, 255, 0),
            Color.rgba(255, 255, 255, 0.55),
            Color.rgba(255, 255, 255, 0)
        ]
        static let ghost: Color = Color.rgba(255, 255, 255, 0.55)
        static let pageShadow: Color = Color(hex: 0x120A40)
        static let cardGlow: Color = Color(hex: 0xB9A6FF)
        static let mote: Color = Color(hex: 0xFFFFFF)
        static let bloom: Color = Color(hex: 0xE9E2FF)
        static let creditIcon: [Color] = [
            Color(hex: 0xFF7A9C),
            Color(hex: 0xFF4F8B),
            Color(hex: 0x8E5BFF)
        ]
        static let closeInk: Color = Color(hex: 0xFFFFFF)
        static let finger: Color = Color.rgba(255, 255, 255, 0.34)
        static let fingerRing: Color = Color.rgba(255, 255, 255, 0.75)
    }

    enum Layout {
        struct Size {
            let width: CGFloat
            let height: CGFloat
        }

        static let gutter: CGFloat = 16
        static let mastheadHeight: CGFloat = 42
        static let panelInset: CGFloat = 8
        static let panelPadTop: CGFloat = 27
        static let panelPadX: CGFloat = 20
        static let pillHeight: CGFloat = 16
        static let pillPadX: CGFloat = 8
        static let photoAspect: CGFloat = 262.0 / 386.0
        static let pillGap: CGFloat = 21
        static let dekGap: CGFloat = 12
        static let photoGap: CGFloat = 17
        static let dotGap: CGFloat = 46
        static let bylineGap: CGFloat = 12
        static let panelPadBottom: CGFloat = 42
        static let panelRadius: CGFloat = 12
        static let bodyGap: CGFloat = 96
        static let toolbarCenterFromBottom: CGFloat = 45
        static let toolbarPill = Size(width: 56, height: 30)
        static let toolbarRound: CGFloat = 30
        static let photoInset: CGFloat = 12
        static let toolbarHeight: CGFloat = 52
        static let toolbarFade: CGFloat = 28
        static let toolbarPillHeight: CGFloat = 36
        static let toolbarIcon: CGFloat = 44
        static let thinkWidthRatio: CGFloat = 0.55
        static let thinkHeight: CGFloat = 222
        static let cardWidthRatio: CGFloat = 0.64
        static let cardHeight: CGFloat = 270
        static let cardRadius: CGFloat = 18
        static let cardTopTrim: CGFloat = 6
        static let thinkTop: CGFloat = 134
        static let summaryTop: CGFloat = 125
        static let titleRowGap: CGFloat = 22
        static let thinkingGap: CGFloat = 39
        static let ghostInset: CGFloat = 12
        static let shadowBlur: CGFloat = 18
        static let glowBlur: CGFloat = 30
        static let glowSpread: CGFloat = 22
        static let glowRest: CGFloat = 0.35
        static let glowThink: CGFloat = 0.35
        static let shadowAlpha: CGFloat = 0.35
        static let shadowDrop: CGFloat = 14
        static let ghostPeek: CGFloat = 12
        static let footerBottom: CGFloat = 14
        static let creditPill = Size(width: 176, height: 40)
        static let creditPad: CGFloat = 8
        static let sweepWidth: CGFloat = 90
        static let summaryGap: CGFloat = 26
        static let textInset: CGFloat = 16
        static let itemIndent: CGFloat = 34
        static let iconDisc: CGFloat = 24
        static let itemGap: CGFloat = 27
        static let leadGap: CGFloat = 42
        static let footerHeight: CGFloat = 44
        static let closeSize: CGFloat = 32
        static let deviceRadius: CGFloat = 48
        static let rimWidth: CGFloat = 2.5
        static let rimGlow: CGFloat = 10
        static let scanHeight: CGFloat = 64
        static let fingerRadius: CGFloat = 22
    }

    enum Copy {
        struct Item {
            let icon: String
            let title: String
            let body: String
        }

        static let wordmark = "MERIDIAN"
        static let kicker = "REVIEW"
        static let headline = "My Week Living Inside the Halo One Visor"
        static let dek = "Mara Okafor gets hands-on — and then some — in this in-depth Halo One review."
        static let byline = "BY MARA OKAFOR"
        static let date = "FEB 2, 2026"
        static let section = "GEAR REVIEWS"
        static let body = [
            "Years before the Halo One went on sale, its makers promised a computer you could wear all afternoon. After a week of working, watching and travelling with it, I can report that they are closer than anyone has been, and still not quite there.",
            "The first surprise is the picture. Text is as sharp as on a good laptop, and a film on the virtual screen in a dark room is better than my television. The second is how little I thought about controllers: I never had any.",
            "Then there is the weight. The split strap helps, but by the third hour the visor makes itself known on the bridge of the nose, and the battery pack in a pocket is a cable you never forget."
        ]
        static let summarize = "Summarize"
        static let pageTitle = "Halo One review: my week inside the visor"
        static let thinking = "Summarizing for you…"
        static let credit = [
            "Summarized for you",
            "by Meridian Reader · 14 min → 40 s"
        ]
        static let lead = "The Halo One is a game-changing headset computer with a handful of killer apps, including a private movie theater experience and the ability to work anywhere. It offers a razor-sharp virtual display and immersive spatial sound, but needs a personalized fit to be comfortable."
        static let items: [Item] = [
            Item(icon: "pricetag", title: "Price", body: "The Halo One starts at $1,899; the travel case is extra."),
            Item(icon: "star", title: "Review sentiment", body: "Mara calls it the best screen she has worn, though not for everyone yet."),
            Item(icon: "body", title: "Comfort and fit", body: "The split strap spreads the weight; two hours before she needed a break.")
        ]
    }

    enum Typography {
        struct Style {
            let size: CGFloat
            let lineHeight: CGFloat
            let tracking: CGFloat
            let weight: Font.Weight
            let family: String?
            let color: Color

            init(
                size: CGFloat,
                lineHeight: CGFloat = 0,
                tracking: CGFloat = 0,
                weight: Font.Weight = .regular,
                family: String? = nil,
                color: Color
            ) {
                self.size = size
                self.lineHeight = lineHeight
                self.tracking = tracking
                self.weight = weight
                self.family = family
                self.color = color
            }
        }

        private static let serif = "Georgia"

        static let wordmark = Style(size: 25, tracking: 0.4, weight: .black, color: Colors.ink)
        static let pill = Style(size: 8, tracking: 0.8, weight: .heavy, color: Colors.pillInk)
        static let headline = Style(size: 32, lineHeight: 36, weight: .bold, family: serif, color: Colors.panelInk)
        static let dek = Style(size: 15, lineHeight: 19, tracking: -0.3, weight: .bold, family: serif, color: Colors.panelInkSoft)
        static let byline = Style(size: 8, tracking: 0.8, weight: .bold, color: Colors.panelInkSoft)
        static let section = Style(size: 13, tracking: 1.1, weight: .heavy, color: Colors.ink)
        static let dropCap = Style(size: 56, lineHeight: 56, weight: .bold, family: serif, color: Colors.ink)
        static let body = Style(size: 17, lineHeight: 26, family: serif, color: Colors.ink)
        static let toolbarLabel = Style(size: 13, weight: .semibold, color: Colors.toolbarInk)
        static let cardTitle = Style(size: 11, weight: .bold, color: Colors.summaryInk)
        static let lead = Style(size: 19, lineHeight: 26, tracking: -0.3, weight: .semibold, color: Colors.summaryInk)
        static let itemTitle = Style(size: 18, lineHeight: 30, tracking: -0.2, weight: .bold, color: Colors.summaryInk)
        static let itemBody = Style(size: 17, lineHeight: 22, weight: .regular, color: Colors.summaryInkSoft)
        static let credit = Style(size: 10, lineHeight: 13, weight: .semibold, color: Colors.summaryInkSoft)
        static let thinking: CGFloat = 14
    }
}
