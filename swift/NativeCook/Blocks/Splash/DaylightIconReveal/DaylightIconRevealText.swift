import CoreText
import SwiftUI
import UIKit

// MARK: - Palette and copy

/// The colours of the block (hex from the React Native styles).
enum DaylightIconRevealPalette {
    static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// `styles.screen` background.
    static let ground = rgb(0xF0EFF5)
    /// `styles.title`.
    static let title = rgb(0x181820)
    /// `styles.subtitle`.
    static let subtitle = rgb(0x8E8C96)
    /// `styles.note`.
    static let note = rgb(0x9A98A2)
    /// `styles.cta` background.
    static let button = rgb(0x503AD4)
}

/// The words on screen, character for character as in the React Native component. Once Continue
/// has been pressed they change, and the button becomes "Replay welcome".
enum DaylightIconRevealCopy {
    static func title(began: Bool) -> String {
        began ? "Make room for\nyour rhythm." : "Find your energy.\nFollow your rhythm."
    }

    static func subtitle(began: Bool) -> String {
        began
            ? "Daylight is ready. Begin with a quiet moment and notice how you feel."
            : "Meet Daylight, a gentle guide to the daily patterns that help you feel your best."
    }

    static func button(began: Bool) -> String {
        began ? "Replay welcome" : "Continue"
    }

    static func note(began: Bool) -> String {
        began
            ? "A fresh start, whenever you need one."
            : "By continuing you agree to the Terms and Conditions and confirm that you have read and understood the Privacy Notice. ›"
    }
}

// MARK: - Type styles

/// One `StyleSheet` text style: size, line height and letter spacing in points before the
/// user's text size is applied.
struct DaylightIconRevealTextStyle {
    var size: Double
    var lineHeight: Double
    var kerning: Double = 0
    var weight: Font.Weight
    var uiWeight: UIFont.Weight
    var color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge this text; nil is no cap.
    var maxScale: Double?

    /// `styles.title`: 31/36, -0.65 tracking, bold; capped at 1.6x like `maxFontSizeMultiplier={1.6}`.
    static let title = DaylightIconRevealTextStyle(
        size: 31, lineHeight: 36, kerning: -0.65, weight: .bold, uiWeight: .bold,
        color: DaylightIconRevealPalette.title, maxScale: 1.6)
    /// `styles.subtitle`: 15/20.
    static let subtitle = DaylightIconRevealTextStyle(
        size: 15, lineHeight: 20, weight: .regular, uiWeight: .regular,
        color: DaylightIconRevealPalette.subtitle)
    /// `styles.ctaText`: 16/22 semibold, white.
    static let button = DaylightIconRevealTextStyle(
        size: 16, lineHeight: 22, weight: .semibold, uiWeight: .semibold, color: .white)
    /// `styles.note`: 9.5/12.5.
    static let note = DaylightIconRevealTextStyle(
        size: 9.5, lineHeight: 12.5, weight: .regular, uiWeight: .regular,
        color: DaylightIconRevealPalette.note)
}

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
/// multipliers), so a heading grows by the same amount in both stacks.
func daylightIconRevealFontScale(_ size: DynamicTypeSize) -> Double {
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
/// above and below (a line cannot be made shorter than the font's own).
struct DaylightIconRevealLineHeight: ViewModifier {
    let points: Double
    let fontSize: Double
    let weight: UIFont.Weight

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.lineHeight(.exact(points: points))
        } else {
            let natural = ceil(UIFont.systemFont(ofSize: fontSize, weight: weight).lineHeight)
            let extra = max(0, points - natural)
            content
                .lineSpacing(extra)
                .padding(.vertical, extra / 2)
        }
    }
}

/// A centred text in one of the block's styles. The user's text size scales it like React Native
/// does (`fontScale`, up to the style's cap), line height included.
struct DaylightIconRevealLabel: View {
    let text: String
    let style: DaylightIconRevealTextStyle
    let fontScale: Double

    var body: some View {
        let scale = min(fontScale, style.maxScale ?? .infinity)
        let size = style.size * scale
        Text(verbatim: text)
            .font(.system(size: size, weight: style.weight))
            .kerning(style.kerning)
            .foregroundStyle(style.color)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(DaylightIconRevealLineHeight(points: style.lineHeight * scale, fontSize: size, weight: style.uiWeight))
    }
}

/// `SwapText`: text that changes in place. An invisible copy of the new text sizes the box; the
/// new words sit on top of it and the old ones on top of that while they fade out.
struct DaylightIconRevealSwapText: View {
    let text: String
    /// The text that was here before the last change, while it is still fading out.
    let previousText: String
    /// Seconds since the text changed; nil when it has not changed since the screen appeared.
    let elapsed: Double?
    let reduced: Bool
    /// The text's own extra delay in seconds (`delay` / 1000 in the React Native call).
    let delay: Double
    let style: DaylightIconRevealTextStyle
    let fontScale: Double
    var isHeader = false

    var body: some View {
        let layers = DaylightIconRevealScript.swap(elapsed: elapsed, reduced: reduced, delay: delay)
        DaylightIconRevealLabel(text: text, style: style, fontScale: fontScale)
            .opacity(0)
            .accessibilityHidden(true)
            .overlay(alignment: .top) {
                DaylightIconRevealLabel(text: text, style: style, fontScale: fontScale)
                    .opacity(layers.incoming)
                    .offset(y: layers.lift)
                    .accessibilityAddTraits(isHeader ? .isHeader : [])
            }
            .overlay(alignment: .top) {
                if layers.outgoing > 0 {
                    DaylightIconRevealLabel(text: previousText, style: style, fontScale: fontScale)
                        .opacity(layers.outgoing)
                        .accessibilityHidden(true)
                        .allowsHitTesting(false)
                }
            }
    }
}
