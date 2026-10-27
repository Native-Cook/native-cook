import SwiftUI
import UIKit

// MARK: - Palette

/// The colours of the block (hex values from the React Native styles).
enum SpectrumNewsCarouselPalette {
    /// An opaque colour from `0xRRGGBB`, with an optional alpha.
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    /// A colour written the React Native way, `#RRGGBBAA`, as `0xRRGGBBAA`.
    static func rgba(_ hex: UInt32) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 24) & 0xFF) / 255,
            green: Double((hex >> 16) & 0xFF) / 255,
            blue: Double((hex >> 8) & 0xFF) / 255,
            opacity: Double(hex & 0xFF) / 255
        )
    }

    /// `styles.root` background and `warmBackdrop` base.
    static let ground = rgb(0xFCFBF8)
}

// MARK: - Copy

/// One page of the tour (`PAGES`): the headline, the words under it and the accessibility
/// description of its artwork, character for character as in the React Native component.
struct SpectrumNewsCarouselPageCopy {
    let title: String
    let body: String
    let art: String
}

enum SpectrumNewsCarouselCopy {
    static let pages: [SpectrumNewsCarouselPageCopy] = [
        SpectrumNewsCarouselPageCopy(
            title: "Spectrum",
            body: "An easier way to keep up\nwith the news",
            art: "Spectrum dot emblem on a warm glowing field."
        ),
        SpectrumNewsCarouselPageCopy(
            title: "Stories, not articles",
            body: "We show stories covered by\nmultiple sources, so you\nalways see varied perspectives.",
            art: "Ten fictional publication badges orbit a blue news icon."
        ),
        SpectrumNewsCarouselPageCopy(
            title: "News selected for you",
            body: "Spectrum gives you powerful\ncontrol over which stories you see,\nand which you don’t.",
            art: "A person silhouette above red and green topic preferences."
        ),
        SpectrumNewsCarouselPageCopy(
            title: "Understand more, faster",
            body: "Get a summary, cut straight to the\ndetails, listen on your drive, or dive\ninto the articles.",
            art: "A blue summary panel above an article, a 16-perspective pill and audio-preview icon."
        ),
        SpectrumNewsCarouselPageCopy(
            title: "Ask questions",
            body: "Spectrum finds answers.\nIf you need clarification, want further\ndetails, or sourced analysis…\njust ask.",
            art: "An original waterfront-garden photograph beside a question and a short answer."
        ),
    ]
}

// MARK: - Type styles

/// The weights the block uses, as both the SwiftUI and the UIKit value.
enum SpectrumNewsCarouselWeight {
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

/// One `StyleSheet` text style: size, line height and letter spacing in points before the user's
/// text size is applied.
struct SpectrumNewsCarouselTextStyle {
    var size: Double
    /// `lineHeight`; nil leaves the font's own line height, as React Native does.
    var lineHeight: Double?
    var kerning: Double = 0
    var weight: SpectrumNewsCarouselWeight = .regular
    var color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge the text; nil is no cap.
    var maxScale: Double?

    init(
        size: Double,
        lineHeight: Double? = nil,
        kerning: Double = 0,
        weight: SpectrumNewsCarouselWeight = .regular,
        color: Color,
        maxScale: Double? = nil
    ) {
        self.size = size
        self.lineHeight = lineHeight
        self.kerning = kerning
        self.weight = weight
        self.color = color
        self.maxScale = maxScale
    }

    private typealias P = SpectrumNewsCarouselPalette

    // The tour pages.
    /// `styles.heading`.
    static let heading = SpectrumNewsCarouselTextStyle(size: 16, lineHeight: 21, weight: .semibold, color: P.rgb(0x111111))
    /// `styles.body`.
    static let body = SpectrumNewsCarouselTextStyle(size: 15, lineHeight: 19.5, color: P.rgb(0x6B6B6B))
    /// `styles.tagline`.
    static let tagline = SpectrumNewsCarouselTextStyle(size: 18, lineHeight: 23, color: P.rgb(0x7A7A7A))

    // The footer.
    /// `styles.appleLabel`, capped at 1.6x like `maxFontSizeMultiplier={1.6}`.
    static let appleLabel = SpectrumNewsCarouselTextStyle(
        size: 19, kerning: -0.2, weight: .medium, color: P.rgb(0xFFFFFF), maxScale: 1.6)
    /// `styles.finePrint`.
    static let finePrint = SpectrumNewsCarouselTextStyle(size: 10, lineHeight: 13, color: P.rgb(0x8A8A8A))

    // The briefing.
    /// `styles.briefingWordmark` (`allowFontScaling={false}`, so it is used with a scale of 1).
    static let briefingWordmark = SpectrumNewsCarouselTextStyle(
        size: 22, kerning: -0.7, weight: .heavy, color: P.rgb(0x25242A))
    /// `styles.date`.
    static let date = SpectrumNewsCarouselTextStyle(size: 10, kerning: 1.7, color: P.rgb(0xA19B91))
    /// `styles.briefingTitle`.
    static let briefingTitle = SpectrumNewsCarouselTextStyle(
        size: 39, lineHeight: 44, kerning: -1.3, weight: .bold, color: P.rgb(0x2B292D))
    /// `styles.briefingIntro`.
    static let briefingIntro = SpectrumNewsCarouselTextStyle(size: 15, lineHeight: 23, color: P.rgb(0x8D877D))
    /// `styles.storyCategory`.
    static let storyCategory = SpectrumNewsCarouselTextStyle(
        size: 9, kerning: 1.2, weight: .bold, color: P.rgb(0x8B9478))
    /// `styles.storyHeadline`.
    static let storyHeadline = SpectrumNewsCarouselTextStyle(
        size: 25, lineHeight: 31, kerning: -0.6, weight: .semibold, color: P.rgb(0x35322F))
    /// `styles.storyBody`.
    static let storyBody = SpectrumNewsCarouselTextStyle(size: 15, lineHeight: 24, color: P.rgb(0x898278))
    /// `styles.perspectiveLabel`.
    static let perspectiveLabel = SpectrumNewsCarouselTextStyle(size: 9, kerning: 1.3, color: P.rgb(0xA29B90))
    /// `styles.perspective`.
    static let perspective = SpectrumNewsCarouselTextStyle(size: 13, lineHeight: 21, color: P.rgb(0x716A61))

    /// `TourButton`'s label: 16/22 (13/18 when subtle), semibold, in `color`.
    static func tourLabel(subtle: Bool, color: Color) -> SpectrumNewsCarouselTextStyle {
        SpectrumNewsCarouselTextStyle(
            size: subtle ? 13 : 16,
            lineHeight: subtle ? 18 : 22,
            weight: .semibold,
            color: color
        )
    }
}

// MARK: - Font scale

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
/// multipliers), so a heading grows by the same amount in both stacks.
enum SpectrumNewsCarouselFontScale {
    static func value(_ size: DynamicTypeSize) -> Double {
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

// MARK: - Text

/// Gives a text the exact line height React Native sets with `lineHeight`. From iOS 26 the system
/// has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split above
/// and below (a line cannot be made shorter than the font's own). A nil `points` leaves the font's
/// own line height.
struct SpectrumNewsCarouselLineHeight: ViewModifier {
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

/// A text in one of the block's styles. `scale` is the user's text size as React Native's
/// `fontScale` (already capped where the style has a cap), 1 for the artwork's fixed-size text
/// (`allowFontScaling={false}`). Size and line height scale together, letter spacing does not.
struct SpectrumNewsCarouselLabel: View {
    let text: Text
    let style: SpectrumNewsCarouselTextStyle
    let scale: Double
    var alignment: TextAlignment = .center

    init(_ string: String, style: SpectrumNewsCarouselTextStyle, scale: Double, alignment: TextAlignment = .center) {
        self.text = Text(verbatim: string)
        self.style = style
        self.scale = scale
        self.alignment = alignment
    }

    init(text: Text, style: SpectrumNewsCarouselTextStyle, scale: Double, alignment: TextAlignment = .center) {
        self.text = text
        self.style = style
        self.scale = scale
        self.alignment = alignment
    }

    var body: some View {
        let size = style.size * scale
        text
            .font(.system(size: size, weight: style.weight.font))
            .kerning(style.kerning)
            .foregroundStyle(style.color)
            .multilineTextAlignment(alignment)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(
                SpectrumNewsCarouselLineHeight(
                    points: style.lineHeight.map { $0 * scale },
                    fontSize: size,
                    weight: style.weight.ui
                )
            )
    }
}

extension SpectrumNewsCarouselTextStyle {
    /// The scale to hand to a label of this style for a given `fontScale`: the user's size, up to
    /// the style's cap.
    func scale(for fontScale: Double) -> Double {
        min(fontScale, maxScale ?? .infinity)
    }
}
