import CoreText
import SwiftUI
import UIKit

// Shared pieces of Cipher Seam: colours, the icon font, text that scales the way React Native's
// does, the press feedback of `PressScale` and a little maths.

// MARK: - Colours

enum CipherSeamPalette {
    /// `#RRGGBB`, or `#RRGGBBAA` when `alpha` (the `AA` byte) is given.
    static func rgb(_ hex: UInt32, alpha: UInt32 = 255) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255,
            opacity: Double(alpha) / 255
        )
    }

    /// `styles.screen`.
    static let screen = rgb(0x070411)
    /// The dark ink on the white buttons (`styles.ctaText`).
    static let buttonInk = rgb(0x120A22)
}

// MARK: - Maths

enum CipherSeamMath {
    /// Reanimated's `interpolate(value, input, output, 'clamp')`.
    static func interpolate(_ value: Double, _ input: [Double], _ output: [Double]) -> Double {
        guard let first = input.first, let last = input.last, input.count == output.count, input.count > 1 else {
            return output.first ?? value
        }
        if value <= first { return output[0] }
        if value >= last { return output[output.count - 1] }
        var index = 1
        while index < input.count - 1 && value > input[index] {
            index += 1
        }
        let span = input[index] - input[index - 1]
        let fraction = span > 0 ? (value - input[index - 1]) / span : 1
        return output[index - 1] + (output[index] - output[index - 1]) * fraction
    }

    /// `hash(i)` of the React Native file: the fractional part of a sine, for seeded points.
    static func hash(_ i: Int) -> Double {
        let v = sin(Double(i) * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
        min(high, max(low, value))
    }

    static func smoothstep(_ value: Double, _ start: Double, _ end: Double) -> Double {
        let t = clamp((value - start) / (end - start), 0, 1)
        return t * t * (3 - 2 * t)
    }
}

// MARK: - Fonts

enum CipherSeamFonts {
    /// The family of the bundled icon font (`@expo/vector-icons` Ionicons).
    static let ionicons = "Ionicons"

    /// Registers the bundled icon font once; touch it before any icon is drawn.
    static let registered: Bool = {
        if let url = Bundle.main.url(forResource: "CipherSeam-Ionicons", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return true
    }()
}

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
/// multipliers), so text grows by the same amount in both stacks.
func cipherSeamFontScale(_ size: DynamicTypeSize) -> Double {
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

/// A font weight in both the SwiftUI and the UIKit spelling (the UIKit one measures the line).
enum CipherSeamWeight {
    case regular
    case medium
    case semibold
    case bold
    case heavy

    var font: Font.Weight {
        switch self {
        case .regular: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        case .heavy: return .heavy
        }
    }

    var ui: UIFont.Weight {
        switch self {
        case .regular: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        case .heavy: return .heavy
        }
    }
}

/// Gives a text the exact line height React Native sets with `lineHeight`. From iOS 26 the system
/// has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split
/// above and below (a line cannot be made shorter than the font's own).
struct CipherSeamLineHeight: ViewModifier {
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

/// A React Native `<Text>`: system font at an exact size, scaled by the user's text size like
/// `fontScale` (up to `maxScale`, the `maxFontSizeMultiplier`; `scales: false` is
/// `allowFontScaling={false}`), with the line height scaled too.
struct CipherSeamLabel: View {
    let text: String
    let size: Double
    var weight: CipherSeamWeight = .regular
    var color: Color = .white
    var lineHeight: Double?
    var kerning: Double = 0
    var maxScale: Double?
    var scales = true
    var alignment: TextAlignment = .leading

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let fontScale = scales ? cipherSeamFontScale(dynamicTypeSize) : 1
        let scale = min(fontScale, maxScale ?? .infinity)
        let pointSize = size * scale
        let label = Text(verbatim: text)
            .font(.system(size: pointSize, weight: weight.font))
            .kerning(kerning)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .fixedSize(horizontal: false, vertical: true)
        if let lineHeight {
            label.modifier(CipherSeamLineHeight(points: lineHeight * scale, fontSize: pointSize, weight: weight.ui))
        } else {
            label
        }
    }
}

// MARK: - Icons

/// An Ionicons glyph drawn from the bundled font, as `@expo/vector-icons` draws it: a `Text` of
/// one character at `size`, which scales with the user's text size like any React Native text.
struct CipherSeamIcon: View {
    enum Glyph: String {
        case wifi = "\u{F631}"
        case menu = "\u{F451}"
        case chevronForward = "\u{F23B}"
        case reorderThree = "\u{F529}"
        case sparkles = "\u{F58C}"
        case radioButtonOn = "\u{F503}"
        case chatbox = "\u{F20B}"
    }

    let glyph: Glyph
    let size: Double
    let color: Color

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let pointSize = size * cipherSeamFontScale(dynamicTypeSize)
        Text(verbatim: glyph.rawValue)
            .font(.custom(CipherSeamFonts.ionicons, fixedSize: pointSize))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
    }
}

// MARK: - Press feedback

/// `PressScale`: the whole surface settles to 0.96 in 150 ms on cubic-bezier(0.23, 1, 0.32, 1) and
/// returns on the same curve; under Reduce Motion it dims to 0.72 instead.
struct CipherSeamPressStyle: ButtonStyle {
    let reduced: Bool

    private static let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.15)

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(reduced && configuration.isPressed ? 0.72 : 1)
            .animation(Self.curve, value: configuration.isPressed)
    }
}
