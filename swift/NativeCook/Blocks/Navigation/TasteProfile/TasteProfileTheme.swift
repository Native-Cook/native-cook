import SwiftUI

enum TasteProfileTheme {
    static let refW = 1206.0
    static let refPt = 402.0
    static let refInset = 186.0
    static let ascent = 0.795
    static let contentBottom = 2622.0

    enum FontToken {
        static let body = "AnybodyNC-Body"
        static let semi = "AnybodyNC-SemiBold"
        static let bold = "AnybodyNC-Bold"
        static let extra = "AnybodyNC-ExtraBold"
    }

    enum ColorToken {
        static let ink = color(0x000000)
        static let white = color(0xFFFFFF)
        static let hairline = color(0xD2D2D2)
        static let tileFill = color(0xF8F8F8)
        static let tileHairline = color(0xDEDEDE)
        static let chipFill = color(0xFEFEFE)
        static let chipText = color(0x8A8A89)
        static let pinIcon = color(0x525252)
        static let dotOn = color(0x1A1A18)
        static let dotOff = color(0x979895)
        static let pageStops: [(Double, Color)] = [
            (0, color(0xF6F6F6)), (90, color(0xF6F6F6)), (180, color(0xF3F3F3)),
            (270, color(0xF1F1F1)), (360, color(0xEFEFEF)), (450, color(0xECECEC)),
            (630, color(0xECECEC)), (900, color(0xEBEBEB)), (1500, color(0xEBEBEB)),
            (1800, color(0xE9EBE9)), (2250, color(0xE9EAE8)), (2520, color(0xE8E8E8)),
            (2622, color(0xE8E8E8)),
        ]
    }

    enum Space {
        static let edge = 36.5
        static let tileEdge = 38.5
        static let tileGap = 19.5
        static let navGap = 25.0
        static let photoGap = 21.0
        static let chipGap = 24.0
    }

    enum Radius {
        static let nav = 28.0
        static let tile = 39.0
        static let photo = 54.0
        static let pill = 999.0
    }

    struct TextSpec {
        let text: String
        let font: String
        let x: Double
        let baseline: Double
        let size: Double
        let tracking: Double
        let color: Color
    }

    enum TextToken {
        static let name = TextSpec(text: "Theo Hogan", font: FontToken.extra, x: 427.7, baseline: 431, size: 21.66, tracking: 0.32, color: ColorToken.ink)
        static let handle = TextSpec(text: "@theohogan", font: FontToken.body, x: 429.4, baseline: 503, size: 15.24, tracking: 0.62, color: color(0x505051))
        static let following = TextSpec(text: "following", font: FontToken.body, x: 497.4, baseline: 590, size: 14.35, tracking: 0.39, color: color(0x262527))
        static let followers = TextSpec(text: "followers", font: FontToken.body, x: 825.4, baseline: 590, size: 14.35, tracking: 0.07, color: color(0x262527))
        static let mutual2 = TextSpec(text: "2", font: FontToken.body, x: 550.7, baseline: 678, size: 12.3, tracking: 0, color: color(0x221F22))
        static let mutual = TextSpec(text: "mutual", font: FontToken.body, x: 583.2, baseline: 678, size: 12.28, tracking: 0.24, color: color(0x221F22))
        static let friends = TextSpec(text: "friends", font: FontToken.body, x: 725.4, baseline: 678, size: 12.24, tracking: 0, color: color(0x221F22))
        static let rankA = TextSpec(text: "new york", font: FontToken.body, x: 565.5, baseline: 785, size: 9.74, tracking: -0.04, color: color(0x474C4F))
        static let rankB = TextSpec(text: "rank", font: FontToken.body, x: 565.5, baseline: 822, size: 9.74, tracking: -0.24, color: color(0x474C4F))
        static let weekA = TextSpec(text: "week", font: FontToken.body, x: 978.1, baseline: 785, size: 9.74, tracking: -0.01, color: color(0x474C4F))
        static let weekB = TextSpec(text: "streak", font: FontToken.body, x: 977.8, baseline: 822, size: 9.74, tracking: -0.32, color: color(0x474C4F))
        static let location = TextSpec(text: "New York", font: FontToken.body, x: 109.2, baseline: 953, size: 13.97, tracking: 0.18, color: color(0x4E4F4E))
        static let bio = TextSpec(text: "Dabbling in the arts", font: FontToken.body, x: 39.2, baseline: 1033, size: 14.39, tracking: 0.16, color: color(0x262725))
        static let chipA = TextSpec(text: "new york", font: FontToken.extra, x: 328.1, baseline: 1206, size: 15.71, tracking: 0.11, color: ColorToken.chipText)
        static let chipB = TextSpec(text: "london", font: FontToken.extra, x: 702.5, baseline: 1206, size: 15.66, tracking: 0.20, color: ColorToken.chipText)
        static let chipC = TextSpec(text: "paris", font: FontToken.extra, x: 1006.1, baseline: 1206, size: 15.81, tracking: -0.86, color: ColorToken.chipText)
        static let favorites = TextSpec(text: "favorites", font: FontToken.body, x: 93.5, baseline: 1428, size: 11.39, tracking: -0.22, color: color(0x6A6A6A))
        static let recommend = TextSpec(text: "recommend", font: FontToken.body, x: 356.3, baseline: 1428, size: 11.43, tracking: 0.09, color: color(0x6A6A6A))
        static let okay = TextSpec(text: "okay", font: FontToken.body, x: 706.9, baseline: 1428, size: 11.43, tracking: 0.03, color: color(0x6A6A6A))
        static let disliked = TextSpec(text: "disliked", font: FontToken.body, x: 968.9, baseline: 1428, size: 11.43, tracking: 0.19, color: color(0x6A6A6A))
        static let viewAll = TextSpec(text: "view all", font: FontToken.body, x: 756.2, baseline: 1983, size: 12.28, tracking: 0.33, color: ColorToken.ink)
        static let photos = TextSpec(text: "photos", font: FontToken.body, x: 1013.2, baseline: 1983, size: 12.28, tracking: 0.03, color: ColorToken.ink)
        static let follow = TextSpec(text: "follow", font: FontToken.semi, x: 760.4, baseline: 263, size: 12.24, tracking: -0.35, color: ColorToken.ink)
        static let followIngX = 871.1
    }

    struct BoxToken {
        let x: Double
        let y: Double
        let w: Double
        let h: Double
    }

    struct ChipToken {
        let x: Double
        let w: Double
    }

    enum Box {
        static let chevron = BoxToken(x: 45, y: 220, w: 33, h: 58)
        static let chevronStroke = 8.0
        static let follow = BoxToken(x: 648.5, y: 200, w: 262, h: 97.7)
        static let followIcon = BoxToken(x: 685, y: 229, w: 49, h: 41)
        static let send = BoxToken(x: 935.5, y: 200, w: 98, h: 97.7)
        static let sendIcon = BoxToken(x: 965, y: 228, w: 42, h: 42)
        static let share = BoxToken(x: 1058.7, y: 200, w: 97.5, h: 97.7)
        static let shareIcon = BoxToken(x: 1086, y: 228, w: 43, h: 43)
        static let capsule = BoxToken(x: 34.5, y: 365, w: 373, h: 500)
        static let capsuleBorder = 6.0
        static let avatars = [(d: 59.0, cx: 460.0, cy: 665.0), (d: 59.0, cx: 509.5, cy: 665.0)]
        static let rank = BoxToken(x: 425, y: 736, w: 120, h: 122)
        static let streak = BoxToken(x: 800, y: 731, w: 160, h: 125)
        static let pin = BoxToken(x: 47, y: 914, w: 42, h: 51)
        static let chips = [ChipToken(x: 36.5, w: 226), ChipToken(x: 286, w: 344), ChipToken(x: 660, w: 271), ChipToken(x: 962, w: 228)]
        static let chipY = 1136.0
        static let chipH = 105.0
        static let globe = BoxToken(x: 88, y: 1165, w: 50, h: 50)
        static let tiles = [38.5, 325.0, 612.0, 899.5]
        static let tileW = 267.5
        static let tileY = 1296.0
        static let tileH = 171.0
        static let tileIcons = [
            BoxToken(x: 101, y: 1334, w: 40, h: 40), BoxToken(x: 381, y: 1334, w: 40, h: 40),
            BoxToken(x: 676, y: 1334, w: 40, h: 40), BoxToken(x: 984, y: 1334, w: 39, h: 40),
        ]
        static let strip = BoxToken(x: 36.5, y: 1516, w: 1131, h: 419.5)
        static let dots = [(cx: 70.0, d: 19.0, color: ColorToken.dotOn), (cx: 100.5, d: 19.0, color: ColorToken.dotOff), (cx: 131.5, d: 19.0, color: color(0x959593)), (cx: 162.5, d: 11.0, color: color(0x9A9997))]
        static let dotsY = 1969.5
        static let photoL = BoxToken(x: 35, y: 2063.5, w: 557.5, h: 557.5)
        static let photoR = BoxToken(x: 614, y: 2063.5, w: 556, h: 557.5)
        static let peek = BoxToken(x: 1193.5, y: 2063.5, w: 80, h: 557.5)
    }

    struct Glyph {
        let character: Character
        let inkLeft: Double
        let inkWidth: Double
    }

    struct GlyphRun {
        let font: String
        let size: Double
        let baseline: Double
        let digitH: Double
        let color: Color
        let glyphs: [Glyph]
    }

    enum GlyphToken {
        static let em: [Character: (bearing: Double, width: Double)] = [
            "0": (0.028, 0.572), "2": (0.035, 0.582), "3": (0.032, 0.590),
            "4": (0.022, 0.598), "5": (0.028, 0.580), "6": (0.028, 0.585),
            "7": (0.012, 0.560), "8": (0.032, 0.575), "9": (0.035, 0.588),
            "a": (0.028, 0.625), "l": (0.052, 0.200),
        ]
        static let n39 = GlyphRun(font: FontToken.extra, size: 15.64, baseline: 590, digitH: 32, color: color(0x2E2D30), glyphs: [Glyph(character: "3", inkLeft: 431, inkWidth: 25), Glyph(character: "9", inkLeft: 459, inkWidth: 25)])
        static let n60 = GlyphRun(font: FontToken.extra, size: 15.64, baseline: 590, digitH: 32, color: color(0x2E2D30), glyphs: [Glyph(character: "6", inkLeft: 760, inkWidth: 24), Glyph(character: "0", inkLeft: 787, inkWidth: 25)])
        static let t125 = GlyphRun(font: FontToken.extra, size: 17.58, baseline: 1370, digitH: 36, color: ColorToken.ink, glyphs: [Glyph(character: "1", inkLeft: 156, inkWidth: 17), Glyph(character: "2", inkLeft: 175, inkWidth: 31), Glyph(character: "5", inkLeft: 209, inkWidth: 33)])
        static let t764 = GlyphRun(font: FontToken.extra, size: 17.65, baseline: 1370, digitH: 36, color: ColorToken.ink, glyphs: [Glyph(character: "7", inkLeft: 435, inkWidth: 31), Glyph(character: "6", inkLeft: 467, inkWidth: 34), Glyph(character: "4", inkLeft: 503, inkWidth: 35)])
        static let t183 = GlyphRun(font: FontToken.extra, size: 17.58, baseline: 1370, digitH: 36, color: ColorToken.ink, glyphs: [Glyph(character: "1", inkLeft: 731, inkWidth: 17), Glyph(character: "8", inkLeft: 750, inkWidth: 33), Glyph(character: "3", inkLeft: 786, inkWidth: 33)])
        static let t17 = GlyphRun(font: FontToken.extra, size: 17.71, baseline: 1370, digitH: 36, color: ColorToken.ink, glyphs: [Glyph(character: "1", inkLeft: 1038, inkWidth: 17), Glyph(character: "7", inkLeft: 1056, inkWidth: 30)])
        static let c1470 = GlyphRun(font: FontToken.extra, size: 13.7, baseline: 1983, digitH: 28, color: ColorToken.ink, glyphs: [Glyph(character: "1", inkLeft: 909, inkWidth: 20), Glyph(character: "4", inkLeft: 932, inkWidth: 22), Glyph(character: "7", inkLeft: 958, inkWidth: 20), Glyph(character: "0", inkLeft: 982, inkWidth: 21)])
        static let chipAll = GlyphRun(font: FontToken.extra, size: 16.14, baseline: 1207, digitH: 38, color: ColorToken.white, glyphs: [Glyph(character: "a", inkLeft: 155, inkWidth: 28), Glyph(character: "l", inkLeft: 187, inkWidth: 9), Glyph(character: "l", inkLeft: 200, inkWidth: 8)])
        static let statNumbers = [t125, t764, t183, t17]
        static let digitWidth: [Character: Double] = ["0": 33, "1": 17, "2": 31, "3": 33, "4": 35, "5": 33, "6": 34, "7": 31, "8": 33, "9": 33]
        static let digitGap = 3.0
    }

    enum Sprite {
        static let coin = (x: 401.0, y: 712.0, w: 168.0, h: 170.0, frames: 28)
        static let gem = (x: 776.0, y: 707.0, w: 208.0, h: 173.0, frames: 12)
        static let coinShadow = (cx: 485.0, cy: 866.0, w: 96.0, h: 16.0)
    }

    static func layoutNumber(_ value: String, x0: Double, digitH: Double) -> [Glyph] {
        let factor = digitH / 36
        var x = x0
        return value.map { character in
            let width = (GlyphToken.digitWidth[character, default: 33] * factor).rounded()
            defer { x += width + GlyphToken.digitGap }
            return Glyph(character: character, inkLeft: x, inkWidth: width)
        }
    }

    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }
}

extension TasteProfileTheme {
    /// The bar chart a held tile grows into (ref px): two tiles wide, built from the tile's tokens.
    enum Chart {
        static let w = 267.5 * 2 + 19.5
        static let h = 560.0
        static let pad = 38.5
        static let barW = 54.0
        static let barMax = 196.0
        static let barsBottom = 560.0 - 84
        static let monthBaseline = 560.0 - 40
        static let captionBaseline = 228.0
        static let valueGap = 14.0
        static let barColor = color(0xDCDCDC)
        static let barOn = color(0x000000)
        static let caption = color(0x6A6A6A)
        static let veil = color(0xEBEBEB, opacity: 0.55)
    }

    static let tileLabels = [TextToken.favorites, TextToken.recommend, TextToken.okay, TextToken.disliked]
    static let cardTop = color(0x202020)
    static let cardBottom = color(0x141414)
    static let cardGap = color(0xF8F8F8)
}
