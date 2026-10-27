import SwiftUI
import UIKit

/// The colours of the block, hex as in the `StyleSheet` of the React Native file.
enum SilverScreenCarouselPalette {
    static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        return Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// `styles.screen` background.
    static let screen = rgb(0x000000)
    static let title = rgb(0xD9D9D7)
    static let subtitle = rgb(0x6E6E6E)
    /// `styles.card` background and border.
    static let cardFill = rgb(0x0B0B0B)
    static let cardBorder = rgb(0x1F1F1F)
    /// The backdrop of the photograph's box.
    static let photoBackdrop = rgb(0x161616)
    /// The shutter flash.
    static let shutter = rgb(0xFFFDF4)
    /// `photoEdge`: `#FFFFFF14`, white at 0x14 / 255.
    static let photoEdge = rgb(0xFFFFFF, opacity: 20.0 / 255.0)
    static let code = rgb(0xF2F2F0)
    static let profileName = rgb(0xE6E6E4)
    static let profileDescription = rgb(0x8A8A88)
    /// `styles.arrow` and `styles.arrowApplied`.
    static let arrow = rgb(0x1A1A1A)
    static let arrowApplied = rgb(0xE7ECE2)
    static let arrowGlyph = rgb(0xD9DED5)
    static let arrowGlyphApplied = rgb(0x121612)
    /// `styles.dot` and `styles.activeDot`.
    static let dot = rgb(0x3A3A3A)
    static let dotActive = rgb(0xF2F2F0)
    /// `styles.apply` and `styles.applyLabel`.
    static let apply = rgb(0xE7ECE2)
    static let applyLabel = rgb(0x192019)
    static let status = rgb(0xA3ADA0)
    /// The solid edge that replaces the blur under Reduce Transparency (`tint="dark"`).
    static let blurFallback = rgb(0x20231F)
}

/// Where everything sits: `SilverScreenCarousel`'s measurements from the window size and the text size.
struct SilverScreenCarouselLayout {
    /// The window in points, safe areas included (`useWindowDimensions`).
    let size: CGSize
    /// `inset.top` of `useSafeAreaInsets`.
    let safeTop: CGFloat
    /// React Native's `fontScale`.
    let fontScale: CGFloat

    /// `referenceLayout`: the reference's screen, used at standard text sizes on a normal phone.
    var referenceLayout: Bool {
        return fontScale <= 1.3 && size.width >= 350
    }

    /// `unit = height / 874`.
    var unit: CGFloat {
        return size.height / 874
    }

    var cardWidth: CGFloat {
        return min(380, size.width * 342 / 402)
    }

    var gap: CGFloat {
        return size.width * 10 / 402
    }

    var step: CGFloat {
        return cardWidth + gap
    }

    var cardHeight: CGFloat {
        return cardWidth * 364 / 342
    }

    var headingTop: CGFloat {
        return 191 * unit
    }

    var cardTop: CGFloat {
        return 353 * unit
    }

    /// `headingMargin`.
    var headingMargin: CGFloat {
        guard referenceLayout else { return 27 }
        return max(24, headingTop - safeTop - 16)
    }

    /// `cardMargin`.
    var cardMargin: CGFloat {
        guard referenceLayout else { return 24 }
        return max(18, cardTop - headingTop - 116 * fontScale)
    }

    /// `footerMargin`.
    var footerMargin: CGFloat {
        guard referenceLayout else { return 16 }
        return max(18, 795 * unit - cardTop - cardHeight - 29)
    }

    /// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
    /// multipliers), so the copy grows by the same amount in both stacks.
    static func fontScale(for size: DynamicTypeSize) -> CGFloat {
        switch size {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
}

/// One `StyleSheet` text style: size, line height and letter spacing in points before the user's
/// text size is applied.
struct SilverScreenCarouselTextStyle {
    var size: CGFloat
    /// `lineHeight`; nil lets the font set its own.
    var lineHeight: CGFloat?
    var kerning: CGFloat = 0
    var weight: Font.Weight = .regular
    var uiWeight: UIFont.Weight = .regular
    var color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge this text; nil is no cap.
    var maxScale: CGFloat?
    /// Source Serif 4 instead of the system font.
    var serif = false
    /// `allowFontScaling`.
    var scales = true

    /// `styles.title`: 28 / 32, -0.4 tracking, capped at 1.5x like `maxFontSizeMultiplier={1.5}`.
    static let title = SilverScreenCarouselTextStyle(
        size: 28, lineHeight: 32, kerning: -0.4, color: SilverScreenCarouselPalette.title, maxScale: 1.5, serif: true)
    /// `styles.subtitle`: 13 / 20.
    static let subtitle = SilverScreenCarouselTextStyle(
        size: 13, lineHeight: 20, color: SilverScreenCarouselPalette.subtitle)
    /// `styles.code`: 26 semibold, -0.6 tracking.
    static let code = SilverScreenCarouselTextStyle(
        size: 26, kerning: -0.6, weight: .semibold, uiWeight: .semibold, color: SilverScreenCarouselPalette.code)
    /// `styles.profileName`: 11.5, weight 500.
    static let profileName = SilverScreenCarouselTextStyle(
        size: 11.5, weight: .medium, uiWeight: .medium, color: SilverScreenCarouselPalette.profileName)
    /// `styles.profileDescription`: 10 / 14.
    static let profileDescription = SilverScreenCarouselTextStyle(
        size: 10, lineHeight: 14, color: SilverScreenCarouselPalette.profileDescription)
    /// `styles.arrowGlyph`: 22 / 26.
    static let arrowGlyph = SilverScreenCarouselTextStyle(
        size: 22, lineHeight: 26, color: SilverScreenCarouselPalette.arrowGlyph)
    /// The same glyph with `allowFontScaling={false}`, on the previous and next arrows.
    static let arrowGlyphFixed = SilverScreenCarouselTextStyle(
        size: 22, lineHeight: 26, color: SilverScreenCarouselPalette.arrowGlyph, scales: false)
    /// `styles.applyLabel`: 14 semibold.
    static let applyLabel = SilverScreenCarouselTextStyle(
        size: 14, weight: .semibold, uiWeight: .semibold, color: SilverScreenCarouselPalette.applyLabel)
    /// `styles.status`: 11 / 16.
    static let status = SilverScreenCarouselTextStyle(
        size: 11, lineHeight: 16, color: SilverScreenCarouselPalette.status)

    func font(size: CGFloat) -> Font {
        if serif {
            return Font.custom(SilverScreenCarouselAssets.serifName, fixedSize: size)
        }
        return Font.system(size: size, weight: weight)
    }

    func uiFont(size: CGFloat) -> UIFont {
        if serif, let font = UIFont(name: SilverScreenCarouselAssets.serifName, size: size) {
            return font
        }
        return UIFont.systemFont(ofSize: size, weight: uiWeight)
    }

    func with(color: Color) -> SilverScreenCarouselTextStyle {
        var copy = self
        copy.color = color
        return copy
    }
}

/// Gives a text the exact line height React Native sets with `lineHeight`. From iOS 26 the system
/// has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split
/// above and below (a line cannot be made shorter than the font's own).
struct SilverScreenCarouselLineHeight: ViewModifier {
    /// The wanted line height, or nil for the font's own.
    let points: CGFloat?
    /// The font's own line height at this size.
    let natural: CGFloat

    func body(content: Content) -> some View {
        if let points {
            if #available(iOS 26.0, *) {
                content.lineHeight(.exact(points: points))
            } else {
                let extra = max(0, points - natural)
                content
                    .lineSpacing(extra)
                    .padding(.vertical, extra / 2)
            }
        } else {
            content
        }
    }
}

/// A text in one of the block's styles. The user's text size scales it like React Native does
/// (`fontScale`, up to the style's cap), line height included. Lines may wrap, never shrink.
struct SilverScreenCarouselLabel: View {
    let text: String
    let style: SilverScreenCarouselTextStyle
    let fontScale: CGFloat
    var alignment: TextAlignment = .leading

    var body: some View {
        let cap = style.maxScale ?? CGFloat.infinity
        let scale = style.scales ? min(fontScale, cap) : 1
        let size = style.size * scale
        let natural = ceil(style.uiFont(size: size).lineHeight)
        let wanted = style.lineHeight.map { $0 * scale }
        Text(verbatim: text)
            .font(style.font(size: size))
            .kerning(style.kerning)
            .foregroundStyle(style.color)
            .multilineTextAlignment(alignment)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(SilverScreenCarouselLineHeight(points: wanted, natural: natural))
    }
}
