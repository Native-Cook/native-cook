import SwiftUI
import UIKit

// MARK: - Palette

/// The colours of the block (hex from the React Native styles). `rgba` takes React Native's
/// `#RRGGBBAA` order, so `0x2A4C9440` is `#2A4C9440`.
enum NocturneProgramCarouselPalette {
    static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    static func rgba(_ hex: UInt32) -> Color {
        rgb(hex >> 8, opacity: Double(hex & 0xFF) / 255)
    }

    /// `root` background.
    static let ground = rgb(0x010A16)
    /// Colours of the root `LinearGradient` (locations 0, 0.3 and 1).
    static let groundTop = rgb(0x0A1A3A)
    static let groundMid = rgb(0x03101F)
    static let groundBottom = rgb(0x010A16)
    /// The dark blur's solid fallback (`tint === 'dark'` of `ProgressiveBlurViewport`).
    static let blurFallback = rgb(0x20231F)
    static let moonTeal = rgb(0x6FE8D6)
    static let sparkBlue = rgb(0x6493FF)
    static let shade = rgb(0x050C1A)
}

// MARK: - Type styles

/// One `StyleSheet` text style: size, line height and letter spacing in points before the user's
/// text size is applied.
struct NocturneProgramCarouselTextStyle {
    var size: Double
    var lineHeight: Double?
    var kerning: Double = 0
    var weight: Font.Weight
    var uiWeight: UIFont.Weight
    var color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge this text; nil is no cap.
    var maxScale: Double?

    /// `styles.heading`: 22/28, -0.5 tracking, bold; `maxFontSizeMultiplier={1.5}`.
    static let heading = NocturneProgramCarouselTextStyle(
        size: 22, lineHeight: 28, kerning: -0.5, weight: .bold, uiWeight: .bold,
        color: NocturneProgramCarouselPalette.rgb(0xF2F5FD), maxScale: 1.5)
    /// `styles.subheading`: 14/19; `maxFontSizeMultiplier={1.8}`.
    static let subheading = NocturneProgramCarouselTextStyle(
        size: 14, lineHeight: 19, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0x9AA8C6), maxScale: 1.8)
    /// `styles.cardTitle`: 20/26, -0.4 tracking, bold; `maxFontSizeMultiplier={1.5}`.
    static let cardTitle = NocturneProgramCarouselTextStyle(
        size: 20, lineHeight: 26, kerning: -0.4, weight: .bold, uiWeight: .bold,
        color: NocturneProgramCarouselPalette.rgb(0xFFFFFF), maxScale: 1.5)
    /// `styles.cardBody`: 14/19; `maxFontSizeMultiplier={1.7}`.
    static let cardBody = NocturneProgramCarouselTextStyle(
        size: 14, lineHeight: 19, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0xA3B3D4), maxScale: 1.7)
    /// `styles.welcomeText`: 19/25 bold; `maxFontSizeMultiplier={1.6}`.
    static let welcome = NocturneProgramCarouselTextStyle(
        size: 19, lineHeight: 25, weight: .bold, uiWeight: .bold,
        color: NocturneProgramCarouselPalette.rgb(0xF2F5FD), maxScale: 1.6)
    /// `TourButton`'s label: 16/22 semibold; `maxFontSizeMultiplier={1.6}`. The colour is the
    /// button's `color` prop (`#EAF1FF` by default).
    static let button = NocturneProgramCarouselTextStyle(
        size: 16, lineHeight: 22, weight: .semibold, uiWeight: .semibold,
        color: NocturneProgramCarouselPalette.rgb(0xEAF1FF), maxScale: 1.6)
    /// `styles.resultEyebrow`: 11, 2 tracking.
    static let resultEyebrow = NocturneProgramCarouselTextStyle(
        size: 11, kerning: 2, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0x78CFC8))
    /// `styles.resultTitle`: 36 bold, -1 tracking.
    static let resultTitle = NocturneProgramCarouselTextStyle(
        size: 36, kerning: -1, weight: .bold, uiWeight: .bold,
        color: NocturneProgramCarouselPalette.rgb(0xF1F5FC))
    /// `styles.resultBody`: 16/24.
    static let resultBody = NocturneProgramCarouselTextStyle(
        size: 16, lineHeight: 24, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0x9AAEC9))
    /// `styles.planTime`: 13, tabular figures.
    static let planTime = NocturneProgramCarouselTextStyle(
        size: 13, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0x79DAD0))
    /// `styles.planTitle`: 18 semibold.
    static let planTitle = NocturneProgramCarouselTextStyle(
        size: 18, weight: .semibold, uiWeight: .semibold,
        color: NocturneProgramCarouselPalette.rgb(0xDCE8F7))
    /// `styles.planBody`: 14/22.
    static let planBody = NocturneProgramCarouselTextStyle(
        size: 14, lineHeight: 22, weight: .regular, uiWeight: .regular,
        color: NocturneProgramCarouselPalette.rgb(0x8CA0BD))
}

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
/// multipliers), so a heading grows by the same amount in both stacks.
func nocturneProgramCarouselFontScale(_ size: DynamicTypeSize) -> Double {
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

// MARK: - Text

/// Gives a text the exact line height React Native sets with `lineHeight`. From iOS 26 the system
/// has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split
/// above and below (a line cannot be made shorter than the font's own). Without a line height the
/// text keeps its natural one, as a React Native text without `lineHeight` does.
struct NocturneProgramCarouselLineHeight: ViewModifier {
    let points: Double?
    let fontSize: Double
    let weight: UIFont.Weight

    func body(content: Content) -> some View {
        if let points {
            if #available(iOS 26.0, *) {
                content.lineHeight(.exact(points: points))
            } else {
                let natural = ceil(UIFont.systemFont(ofSize: fontSize, weight: weight).lineHeight)
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
/// (`fontScale`, up to the style's `maxFontSizeMultiplier`), line height included. The size is
/// exactly the React Native `fontSize` at the default text size.
struct NocturneProgramCarouselLabel: View {
    let text: String
    let style: NocturneProgramCarouselTextStyle
    let fontScale: Double
    var alignment: TextAlignment = .center
    var tabularFigures = false
    /// Overrides the style's colour (`TourButton`'s `color` prop).
    var color: Color?

    var body: some View {
        let scale = min(fontScale, style.maxScale ?? .infinity)
        let size = style.size * scale
        let base = Text(verbatim: text)
            .font(.system(size: size, weight: style.weight))
            .kerning(style.kerning)
        let figures = tabularFigures ? base.monospacedDigit() : base
        figures
            .foregroundStyle(color ?? style.color)
            .multilineTextAlignment(alignment)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(NocturneProgramCarouselLineHeight(
                points: style.lineHeight.map { $0 * scale },
                fontSize: size,
                weight: style.uiWeight))
    }
}

/// A fixed-size text of the artwork (`allowFontScaling={false}`, no line height): the size does not
/// follow the user's text size.
func nocturneProgramCarouselArtText(
    _ text: String,
    size: Double,
    weight: Font.Weight = .regular,
    color: Color,
    kerning: Double = 0
) -> Text {
    Text(verbatim: text)
        .font(.system(size: size, weight: weight))
        .kerning(kerning)
        .foregroundStyle(color)
}
