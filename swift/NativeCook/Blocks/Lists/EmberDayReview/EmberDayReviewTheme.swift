import SwiftUI
import UIKit

/// Colours of Ember Day Review. Every hex comes from `styles` in EmberDayReview.tsx.
enum EmberDayReviewPalette {
    static func rgb(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255,
            opacity: opacity
        )
    }

    static let screen = rgb(0xFFFFFF)
    static let reviewScreen = rgb(0xF4F3F5)
    static let ember = rgb(0xE8693A)
    static let emberText = rgb(0xD66235)
    static let emberArrow = rgb(0xDC6539)
    static let emberCheck = rgb(0xE96739)
    static let badge = rgb(0xE9E9EC)
    static let badgeToday = rgb(0xFFEDE5)
    static let moreDisc = rgb(0xF2F2F4)
    static let ink = rgb(0x1F201E)
    static let dateActive = rgb(0x292A27)
    static let dateHint = rgb(0x77746F)
    static let dateInactive = rgb(0x74796F)
    static let tabMuted = rgb(0xBDBDBB)
    static let body = rgb(0x2E2F2C)
    static let smallValue = rgb(0x70766A)
    static let smallLabel = rgb(0x73796E)
    static let total = rgb(0x4A4D45)
    static let stamp = rgb(0x8C8C88)
    static let dot = rgb(0xF07951)
    static let rail = rgb(0xF4B59B)
    static let laurelStem = rgb(0xB9BCB1)
    static let laurelLeaf = rgb(0xBFC2B7)
    static let summaryShadow = rgb(0x614D3A, 0.08)
    static let handle = rgb(0xD9D8D4)
    static let notice = rgb(0x72776D)
    static let reviewEmpty = rgb(0xC4C4C8)
    static let checkRing = rgb(0xE3E3E6)
    static let checkWell = rgb(0xEFEFF1)
    static let taskDone = rgb(0xA2A79C)
    static let saved = rgb(0xC75B30)
    static let placeholder = rgb(0xC9C2BE)
    static let input = rgb(0x4A4F46)
    static let addBorder = rgb(0xFFC4A8)
    static let addShadow = rgb(0xFF8C5A, 0.28)
    static let solidBlur = rgb(0xE5E9DF)
}

/// Texts and numbers of the demo content.
enum EmberDayReviewContent {
    static let history: [(stamp: String, title: String)] = [
        ("2:11 PM", "Plan a weekend by the coast"),
        ("2:41 PM", "Make time for a long walk"),
        ("3:27 PM", "Finish the first chapter"),
    ]
    static let today = [
        "Reply to 10 messages",
        "Evening run with the river club",
        "Record the podcast intro",
        "Dentist appointment @ 2:00",
        "Call Mira",
        "Water the plants",
        "Notes for Theo + 30 min review",
    ]
}

/// React Native's `fontScale` for each Dynamic Type size (RCTAccessibilityManager's multipliers).
enum EmberDayReviewScale {
    static func fontScale(_ size: DynamicTypeSize) -> Double {
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

    /// The multiplier a text gets: the font scale, held at `maxFontSizeMultiplier` when React Native caps it.
    static func factor(_ size: DynamicTypeSize, cap: Double?) -> Double {
        let scale = fontScale(size)
        guard let cap else { return scale }
        return min(scale, cap)
    }
}

/// Screen measurements: React Native's `useWindowDimensions` (the whole screen) and the safe-area insets.
struct EmberDayReviewMetrics: Equatable {
    var width: CGFloat
    var height: CGFloat
    var top: CGFloat
    var bottom: CGFloat
    var fontScale: Double

    /// `const compact = height < 740 || fontScale > 1.35`.
    var compact: Bool { height < 740 || fontScale > 1.35 }
    /// Large-text layout switches at `fontScale > 1.4` throughout the file.
    var largeText: Bool { fontScale > 1.4 }
    /// Height of the hero slot: `compact ? 200 : 262`.
    var heroHeight: CGFloat { compact ? 200 : 262 }
    /// The top bar is `insets.top - 4` of padding over a 48 pt button.
    var topBarHeight: CGFloat { top - 4 + 48 }
    var viewportHeight: CGFloat { height - topBarHeight }
}

/// One text in the block's type: the React Native size, weight, line height, tracking and
/// `maxFontSizeMultiplier`, scaled by the Dynamic Type size the way React Native scales it.
struct EmberDayReviewLabel: View {
    let text: String
    let size: CGFloat
    var weight: Font.Weight = .regular
    var color: Color
    var lineHeight: CGFloat?
    var tracking: CGFloat = 0
    var cap: Double?
    var tabular = false
    var alignment: TextAlignment = .leading
    var lineLimit: Int?
    var strikethrough = false

    @Environment(\.dynamicTypeSize) private var typeSize

    init(
        _ text: String,
        size: CGFloat,
        weight: Font.Weight = .regular,
        color: Color,
        lineHeight: CGFloat? = nil,
        tracking: CGFloat = 0,
        cap: Double? = nil,
        tabular: Bool = false,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil,
        strikethrough: Bool = false
    ) {
        self.text = text
        self.size = size
        self.weight = weight
        self.color = color
        self.lineHeight = lineHeight
        self.tracking = tracking
        self.cap = cap
        self.tabular = tabular
        self.alignment = alignment
        self.lineLimit = lineLimit
        self.strikethrough = strikethrough
    }

    var body: some View {
        let factor = EmberDayReviewScale.factor(typeSize, cap: cap)
        let pointSize = size * factor
        let natural = UIFont.systemFont(ofSize: pointSize, weight: Self.uiWeight(weight)).lineHeight
        // React Native centres the glyphs in the line box; SwiftUI adds the spacing below, so half of
        // the difference goes above the first line and half below the last.
        let extra = lineHeight.map { $0 * factor - natural } ?? 0
        Text(text)
            .font(.system(size: pointSize, weight: weight))
            .tracking(tracking)
            .strikethrough(strikethrough, color: color)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .lineLimit(lineLimit)
            .lineSpacing(extra)
            .padding(.vertical, extra / 2)
            .modifier(EmberDayReviewTabular(enabled: tabular))
            .fixedSize(horizontal: false, vertical: true)
    }

    private static func uiWeight(_ weight: Font.Weight) -> UIFont.Weight {
        switch weight {
        case .bold: return .bold
        case .semibold: return .semibold
        case .medium: return .medium
        default: return .regular
        }
    }
}

/// `fontVariant: ['tabular-nums']`.
private struct EmberDayReviewTabular: ViewModifier {
    let enabled: Bool

    func body(content: Content) -> some View {
        if enabled {
            content.monospacedDigit()
        } else {
            content
        }
    }
}

/// The artwork bundled with the block.
enum EmberDayReviewArt {
    /// The glossy four-point star, decoded once.
    static let star: UIImage? = {
        guard let url = Bundle.main.url(forResource: "EmberDayReview-four-point-star", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path)
        else { return nil }
        return image.preparingForDisplay() ?? image
    }()
}
