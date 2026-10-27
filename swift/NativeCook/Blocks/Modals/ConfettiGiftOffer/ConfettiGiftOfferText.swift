import SwiftUI
import UIKit

/// A piece of a line with its own colour (`<Text style={styles.accent}>` nested in a headline).
struct ConfettiGiftOfferRun {
    var text: String
    var color: Color?

    init(_ text: String, color: Color? = nil) {
        self.text = text
        self.color = color
    }
}

/// A React Native text style: size, line height, weight, letter spacing, colour and
/// `maxFontSizeMultiplier`. All sizes are in the reference's points and are multiplied by the font scale,
/// capped at `cap` where the React Native text sets `maxFontSizeMultiplier`.
struct ConfettiGiftOfferTextStyle {
    var size: CGFloat
    var weight: Font.Weight = .regular
    /// The bundled Barlow Condensed ExtraBold instead of the system font.
    var headline = false
    var lineHeight: CGFloat?
    var tracking: CGFloat = 0
    var color: Color
    /// `maxFontSizeMultiplier`: the most the font scale may enlarge this text.
    var cap: CGFloat = .infinity
    var tabular = false
    var strikethrough = false

    func multiplier(_ fontScale: CGFloat) -> CGFloat {
        min(fontScale, cap)
    }

    func font(_ fontScale: CGFloat) -> Font {
        let points = size * multiplier(fontScale)
        if headline { return Font.custom(ConfettiGiftOfferAssets.headlineFontName, fixedSize: points) }
        return Font.system(size: points, weight: weight)
    }

    /// The same font in UIKit, only to read its natural line height.
    func uiFont(_ fontScale: CGFloat) -> UIFont {
        let points = size * multiplier(fontScale)
        if headline, let font = UIFont(name: ConfettiGiftOfferAssets.headlineFontName, size: points) { return font }
        return UIFont.systemFont(ofSize: points, weight: uiWeight)
    }

    private var uiWeight: UIFont.Weight {
        switch weight {
        case .ultraLight: return .ultraLight
        case .thin: return .thin
        case .light: return .light
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        case .heavy: return .heavy
        case .black: return .black
        default: return .regular
        }
    }
}

/// Text laid out the way React Native lays it out on iOS: every line sits in a box of exactly `lineHeight`
/// (scaled with the font), and the glyphs are centred in it when the line box is taller than the font's
/// natural height, or sit on its bottom edge (descender kept) when it is shorter. SwiftUI's `lineSpacing`
/// cannot do either, so each explicit line is its own box.
struct ConfettiGiftOfferText: View {
    let lines: [[ConfettiGiftOfferRun]]
    let style: ConfettiGiftOfferTextStyle
    var alignment: HorizontalAlignment = .center

    @Environment(\.confettiGiftOfferFontScale) private var fontScale

    init(_ text: String, style: ConfettiGiftOfferTextStyle, alignment: HorizontalAlignment = .center) {
        self.lines = text.components(separatedBy: "\n").map { [ConfettiGiftOfferRun($0)] }
        self.style = style
        self.alignment = alignment
    }

    init(lines: [[ConfettiGiftOfferRun]], style: ConfettiGiftOfferTextStyle, alignment: HorizontalAlignment = .center) {
        self.lines = lines
        self.style = style
        self.alignment = alignment
    }

    var body: some View {
        VStack(alignment: alignment, spacing: 0) {
            ForEach(lines.indices, id: \.self) { index in
                line(lines[index])
            }
        }
    }

    private func attributed(_ runs: [ConfettiGiftOfferRun]) -> AttributedString {
        var result = AttributedString()
        for run in runs {
            var piece = AttributedString(run.text)
            piece.foregroundColor = run.color ?? style.color
            result.append(piece)
        }
        return result
    }

    @ViewBuilder
    private func line(_ runs: [ConfettiGiftOfferRun]) -> some View {
        let multiplier = style.multiplier(fontScale)
        let glyphs = glyphView(runs)
        if let lineHeight = style.lineHeight {
            let box = lineHeight * multiplier
            let natural = style.uiFont(fontScale).lineHeight
            // Centred when the box is taller than the font, bottom-anchored when it is shorter.
            let shift = box >= natural ? (box - natural) / 2 : box - natural
            ZStack(alignment: .top) {
                glyphs.offset(y: shift)
            }
            .frame(height: box, alignment: .top)
        } else {
            glyphs
        }
    }

    @ViewBuilder
    private func glyphView(_ runs: [ConfettiGiftOfferRun]) -> some View {
        let base = Text(attributed(runs))
            .font(style.font(fontScale))
            .kerning(style.tracking)
            .lineLimit(1)
            // Never shrink: a line box shorter than the font (49 pt for the 50 pt headline) would otherwise make
            // `minimumScaleFactor` scale the glyphs down to fit the proposed height.
            .fixedSize(horizontal: true, vertical: true)
        if style.tabular {
            base.monospacedDigit().strikethrough(style.strikethrough)
        } else {
            base.strikethrough(style.strikethrough)
        }
    }
}

/// A paragraph that wraps (the finish screen's copy): lines keep the font's natural pitch plus the extra
/// `lineHeight` asks for, since SwiftUI cannot fix a wrapped line's height.
struct ConfettiGiftOfferParagraph: View {
    let text: String
    let style: ConfettiGiftOfferTextStyle

    @Environment(\.confettiGiftOfferFontScale) private var fontScale

    var body: some View {
        let extra = max(0, (style.lineHeight ?? 0) * style.multiplier(fontScale) - style.uiFont(fontScale).lineHeight)
        Text(text)
            .font(style.font(fontScale))
            .foregroundStyle(style.color)
            .multilineTextAlignment(.center)
            .lineSpacing(extra)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The text styles of `styles` in ConfettiGiftOffer.tsx (lines 258-261).
enum ConfettiGiftOfferType {
    private static let ink = ConfettiGiftOfferPalette.ink

    /// `headline`: 50 / 49, letter spacing -0.6, `maxFontSizeMultiplier` 1.35.
    static let headline = ConfettiGiftOfferTextStyle(size: 50, headline: true, lineHeight: 49, tracking: -0.6, color: ink, cap: 1.35)
    /// `buttonLabel`: 17 / 22, 600.
    static func buttonLabel(_ color: Color) -> ConfettiGiftOfferTextStyle {
        ConfettiGiftOfferTextStyle(size: 17, weight: .semibold, lineHeight: 22, color: color)
    }
    /// `finePrint`: 14 / 19.
    static let finePrint = ConfettiGiftOfferTextStyle(size: 14, lineHeight: 19, color: ConfettiGiftOfferPalette.grey)
    /// `countdownText`: 14, 600, tabular.
    static let countdown = ConfettiGiftOfferTextStyle(size: 14, weight: .semibold, color: ConfettiGiftOfferPalette.hex(0xFF4D4D), tabular: true)
    /// `oldPrice`: 17 / 22, 600, struck out.
    static let oldPrice = ConfettiGiftOfferTextStyle(size: 17, weight: .semibold, lineHeight: 22, color: ConfettiGiftOfferPalette.hex(0x8C8C8C), strikethrough: true)
    /// `price`: 60 / 66, 800, letter spacing -1.5, tabular.
    static let price = ConfettiGiftOfferTextStyle(size: 60, weight: .heavy, lineHeight: 66, tracking: -1.5, color: .white, tabular: true)
    /// `perMonth`: 18 / 22, 500.
    static let perMonth = ConfettiGiftOfferTextStyle(size: 18, weight: .medium, lineHeight: 22, color: ConfettiGiftOfferPalette.hex(0xC2C2C2))
    /// `percent`: 30, 800 (the ticket's watermark).
    static let percent = ConfettiGiftOfferTextStyle(size: 30, weight: .heavy, color: ConfettiGiftOfferPalette.hex(0x2D2D2D))
    /// `signupCopy`: 17 / 24.
    static let signupCopy = ConfettiGiftOfferTextStyle(size: 17, lineHeight: 24, color: ConfettiGiftOfferPalette.hex(0x8E8E9A))
    /// `sheetTitle`: 30 / 37, 800, letter spacing -0.5, `maxFontSizeMultiplier` 1.5.
    static let sheetTitle = ConfettiGiftOfferTextStyle(size: 30, weight: .heavy, lineHeight: 37, tracking: -0.5, color: ink, cap: 1.5)
    /// `finishCopy`: 15 / 23.
    static let finishCopy = ConfettiGiftOfferTextStyle(size: 15, lineHeight: 23, color: ConfettiGiftOfferPalette.hex(0x787188))
}
