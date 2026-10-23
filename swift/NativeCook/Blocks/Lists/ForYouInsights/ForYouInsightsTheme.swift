import SwiftUI
import UIKit

/// Every colour, type style, spacing token and measured position (theme.ts), pt at a 402 pt screen with a 62 pt safe top.
enum ForYouInsightsTheme {
    static let refWidth = 402.0
    static let refSafeTop = 62.0

    enum Colors {
        static let page = Color(hex: 0xF2F1F6)
        static let ink = Color(hex: 0x111113)
        static let secondary = Color.rgba(60, 60, 67, 0.60)
        static let tertiary = Color.rgba(60, 60, 67, 0.58)
        static let meta = Color.rgba(60, 60, 67, 0.66)
        static let railTitle = Color.rgba(60, 60, 67, 0.72)
        static let separator = Color.rgba(60, 60, 67, 0.13)
        static let pink = Color(hex: 0xE648A5)
        static let violet = Color(hex: 0x8B5CF6)
        static let blue = Color(hex: 0x1E8BEA)
        static let white = Color.white
        static let tile = Color(hex: 0xF5F4FA)
        static let tileBorder = Color.rgba(60, 60, 67, 0.22)
        static let heart = Color(hex: 0xF23A7E)
        static let heartLine = Color.rgba(60, 60, 67, 0.20)
        static let sleepTeal = Color(hex: 0x21C7B4)
        static let sleepOrange = Color(hex: 0xF3826A)
        static let sleepBlue = Color(hex: 0x4E63F8)
        static let readyTrack = Color.rgba(60, 60, 67, 0.13)
        static let readyLight = Color(hex: 0xA9CCEB)
        static let readyStrong = Color(hex: 0x3C8FDA)
        static let fitMove = Color(hex: 0xEE2A6B)
        static let fitExercise = Color(hex: 0x64D23C)
        static let fitStand = Color(hex: 0x21C7D9)
        static let ellipsisFill = Color.rgba(60, 60, 67, 0.08)
        static let pillFill = Color.rgba(118, 118, 128, 0.12)
        static let pillEdge = Color.rgba(255, 255, 255, 0.70)
        static let tabGlassTint = Color.rgba(255, 255, 255, 0.62)
        static let tabFallback = Color.rgba(250, 250, 252, 0.82)
        static let tabEdge = Color.rgba(255, 255, 255, 0.80)
        static let closeDisc = Color.rgba(255, 255, 255, 0.86)
        static let shadow = Color(hex: 0x1B1530)
        static let caretGlow = Color(hex: 0xF062B8)
        static let shimmer: [Color] = [
            .rgba(255, 255, 255, 0), .rgba(255, 214, 240, 0.85), .rgba(255, 255, 255, 0.95),
            .rgba(205, 226, 255, 0.85), .rgba(255, 255, 255, 0),
        ]
    }

    enum Weight {
        case regular, medium, semibold, bold
        var font: Font.Weight {
            switch self {
            case .regular: .regular
            case .medium: .medium
            case .semibold: .semibold
            case .bold: .bold
            }
        }
        var ui: UIFont.Weight {
            switch self {
            case .regular: .regular
            case .medium: .medium
            case .semibold: .semibold
            case .bold: .bold
            }
        }
    }

    struct Style {
        var size: Double
        var line: Double? = nil
        var weight: Weight
        var tracking: Double
    }

    enum Typo {
        static let heading = Style(size: 28, weight: .semibold, tracking: -0.2)
        static let label = Style(size: 12, weight: .bold, tracking: 0.46)
        static let tip = Style(size: 21, line: 27, weight: .bold, tracking: -0.24)
        static let rowTitle = Style(size: 18, line: 22.2, weight: .semibold, tracking: -0.5)
        static let rowSummary = Style(size: 15.5, line: 20, weight: .regular, tracking: -0.15)
        static let rowMeta = Style(size: 14, weight: .medium, tracking: 0)
        static let railTitle = Style(size: 14, line: 18, weight: .regular, tracking: -0.1)
        static let railValue = Style(size: 14.5, line: 19, weight: .bold, tracking: -0.3)
        static let railNumber = Style(size: 12.5, weight: .bold, tracking: 0)
        static let tab = Style(size: 10.5, weight: .semibold, tracking: 0)
        static let readerTitle = Style(size: 30, line: 36, weight: .bold, tracking: -0.6)
        static let readerMeta = Style(size: 13, weight: .semibold, tracking: 0.3)
        static let readerLead = Style(size: 18, line: 26, weight: .medium, tracking: -0.2)
        static let readerHeading = Style(size: 19, line: 25, weight: .bold, tracking: -0.3)
        static let readerBody = Style(size: 16, line: 24, weight: .regular, tracking: -0.1)
        static let sectionSubhead = Style(size: 15, weight: .medium, tracking: -0.1)
        static let habitTitle = Style(size: 18, weight: .semibold, tracking: -0.2)
        static let habitBody = Style(size: 15, line: 21, weight: .regular, tracking: -0.1)
    }

    enum Space {
        static let xxs = 4.0, xs = 8.0, sm = 12.0, md = 16.0, lg = 20.0, xl = 24.0, xxl = 28.0, screen = 32.0
    }

    enum Radius {
        static let tile = 20.0, image = 26.0, hero = 28.0, tabBar = 31.0, tabPill = 26.0
        static let ellipsis = 10.0, close = 18.0, habitIcon = 14.0
    }

    enum Layout {
        static let railTop = 62.0, railHeight = 58.0, railInset = Space.md, railGap = Space.xxs
        static let railDivider = 127.7
        static let tileWidths = [62.7, 108.3, 134.0, 112.0]
        static let railRing = 33.0, railRingPad = Space.sm, railTextGap = 10.0
        static let railTitleTop = 71.8, railValueTop = 90.0
        static let heartTop = 76.0, heartSize = 20.0
        static let heartLines: [(width: Double, top: Double)] = [(21, 97.5), (16, 104)]
        static let column = Space.screen, columnWidth = 338.0
        static let headingTop = 150.7, headingDivider = 200.7
        static let labelTop = 218.0, labelHeight = 20.0, labelIcon = 12.0, labelInset = 1.5, labelIconGap = Space.xxs
        static let ellipsisCenter = CGPoint(x: 358.8, y: 227.2)
        static let ellipsisSize = 20.0, ellipsisGlyph = 15.0
        static let tipTop = 243.7, tipLines = 5, tipDivider = 394.7
        static let rowTop = 411.0, browseRowTop = 254.0
        static let habitTop = 254.0, habitPitch = 120.0, habitIcon = 44.0
        static let rowPitch = 187.0, rowTextWidth = 151.0, rowImageX = 216.0, rowImageSize = 154.0
        static let rowSummaryOffset = 70.3, rowMetaOffset = 135.7, rowMetaIcon = 14.0, rowMetaGap = 5.0
        static let rowDividerOffset = 170.7
        static let tabBarTop = 791.0, tabBarWidth = 290.0, tabBarHeight = 62.0
        static let tabPillWidth = 97.0, tabPillHeight = 52.0, tabPitch = 91.5
        static let tabIcon = 27.0, tabIconCenter = 25.0, tabLabelCenter = 47.3
        static let heroTop = 70.0, heroInset = Space.lg, heroHeight = 300.0
        static let closeSize = 36.0, closeInset = Space.sm
        static let readerTextInset = Space.xxl, readerTitleGap = Space.xl, readerMetaGap = Space.sm
        static let readerSectionGap = Space.xl, readerParagraphGap = Space.xs, readerBottom = Space.screen
        static let divider = 1.0
    }
}

/// Text in the RN sense: a box whose top is the given y, glyphs centred in `line` when a line height is set.
struct ForYouInsightsText: View {
    var text: String
    var style: ForYouInsightsTheme.Style
    var color: Color
    var k: Double
    var width: Double? = nil
    var lines: Int? = nil

    var body: some View {
        let font = UIFont.systemFont(ofSize: style.size * k, weight: style.weight.ui)
        let extra = max(0, (style.line.map { $0 * k } ?? font.lineHeight) - font.lineHeight)
        Text(text)
            .font(.system(size: style.size * k, weight: style.weight.font))
            .kerning(style.tracking * k)
            .foregroundStyle(color)
            .lineSpacing(extra)
            .lineLimit(lines)
            .fixedSize(horizontal: width == nil, vertical: true)
            .frame(width: width.map { $0 * k }, alignment: .topLeading)
            .padding(.top, extra / 2)
    }
}
