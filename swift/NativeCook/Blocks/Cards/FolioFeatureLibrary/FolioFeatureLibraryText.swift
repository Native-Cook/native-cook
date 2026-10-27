import SwiftUI
import UIKit

/// The user's text size as React Native sees it: `PixelRatio.getFontScale()`, which on iOS is the
/// `UIContentSizeCategory` table of RCTAccessibilityManager. Sizes in this block are set in points and
/// multiplied by this factor by hand (a fixed-size SwiftUI font does not follow Dynamic Type), capped
/// where React Native sets `maxFontSizeMultiplier` and left alone where it sets `allowFontScaling={false}`.
struct FolioFeatureLibraryType: Equatable {
    var scale: Double = 1

    init(scale: Double = 1) {
        self.scale = scale
    }

    init(_ size: DynamicTypeSize) {
        switch size {
        case .xSmall: scale = 0.823
        case .small: scale = 0.882
        case .medium: scale = 0.941
        case .large: scale = 1.0
        case .xLarge: scale = 1.118
        case .xxLarge: scale = 1.235
        case .xxxLarge: scale = 1.353
        case .accessibility1: scale = 1.786
        case .accessibility2: scale = 2.143
        case .accessibility3: scale = 2.643
        case .accessibility4: scale = 3.143
        case .accessibility5: scale = 3.571
        @unknown default: scale = 1.0
        }
    }

    /// The multiplier one text gets.
    func factor(maxScale: Double?, scales: Bool) -> Double {
        if !scales { return 1 }
        if let maxScale = maxScale { return min(scale, maxScale) }
        return scale
    }
}

/// One React Native text style: `fontSize`, `fontWeight`, `fontFamily`, `lineHeight`, `letterSpacing`, colour and
/// `maxFontSizeMultiplier` / `allowFontScaling`.
struct FolioFeatureLibraryTextStyle {
    enum Face {
        /// The system font (San Francisco), what React Native draws without a `fontFamily`.
        case system
        /// `SERIF` on iOS is Georgia; bold and italic are its own faces.
        case georgia
        case georgiaBold
        case georgiaItalic
        /// `Menlo`, the monospace of `sourceText`.
        case menlo
    }

    var size: Double
    var weight: UIFont.Weight = .regular
    var face: Face = .system
    var lineHeight: Double?
    var tracking: Double = 0
    var color: Color = Color(folio: 0x111111)
    var maxScale: Double?
    var scales = true

    func uiFont(size points: CGFloat) -> UIFont {
        switch face {
        case .system:
            return UIFont.systemFont(ofSize: points, weight: weight)
        case .georgia:
            return UIFont(name: "Georgia", size: points) ?? UIFont.systemFont(ofSize: points)
        case .georgiaBold:
            return UIFont(name: "Georgia-Bold", size: points) ?? UIFont.boldSystemFont(ofSize: points)
        case .georgiaItalic:
            return UIFont(name: "Georgia-Italic", size: points) ?? UIFont.italicSystemFont(ofSize: points)
        case .menlo:
            return UIFont(name: "Menlo-Regular", size: points) ?? UIFont.monospacedSystemFont(ofSize: points, weight: .regular)
        }
    }
}

/// A `<Text>` of React Native: the style's size and line box, at the user's text size. React Native centres the glyphs
/// in a fixed `lineHeight`; SwiftUI's `lineSpacing` only adds room between lines, so half of the difference to the
/// font's own line height is added above and below as well.
struct FolioFeatureLibraryLabel: View {
    let text: String
    let style: FolioFeatureLibraryTextStyle
    let type: FolioFeatureLibraryType
    var alignment: TextAlignment = .leading
    var lines: Int?
    /// A bold, darker first run, as in `<Text><Text style={{ fontWeight: '700' }}>name</Text> is ...</Text>`.
    var boldPrefix: String?

    var body: some View {
        let factor = style.scales ? type.factor(maxScale: style.maxScale, scales: true) : 1.0
        let points = CGFloat(style.size * factor)
        let font = style.uiFont(size: points)
        let natural = Double(font.lineHeight)
        let boxed = style.lineHeight.map { $0 * factor }
        let extra = boxed.map { $0 - natural } ?? 0
        runs(font: font)
            .multilineTextAlignment(alignment)
            .lineLimit(lines)
            .lineSpacing(CGFloat(extra))
            .padding(.vertical, CGFloat(extra / 2))
            .fixedSize(horizontal: false, vertical: true)
    }

    private func runs(font: UIFont) -> Text {
        let base = Text(text)
            .tracking(CGFloat(style.tracking))
            .font(Font(font as CTFont))
            .foregroundColor(style.color)
        guard let prefix = boldPrefix else { return base }
        let bold = UIFont.systemFont(ofSize: font.pointSize, weight: .bold)
        let head = Text(prefix)
            .tracking(CGFloat(style.tracking))
            .font(Font(bold as CTFont))
            .foregroundColor(Color(folio: 0x1F1F1F))
        return head + base
    }
}

/// Text measures React Native's layout needs outside of a view.
enum FolioFeatureLibraryMetrics {
    /// Rounds up to a whole device pixel, as React Native's `RCTCeilPixelValue` does for measured text.
    static func ceilToPixel(_ value: Double, scale: Double) -> Double {
        let pixel = scale > 0 ? scale : 3
        return (value * pixel).rounded(.up) / pixel
    }

    /// The width of one line of system text.
    static func textWidth(_ text: String, size: Double, weight: UIFont.Weight, pixelScale: Double) -> Double {
        let font = UIFont.systemFont(ofSize: CGFloat(size), weight: weight)
        let width = (text as NSString).size(withAttributes: [.font: font]).width
        return ceilToPixel(Double(width), scale: pixelScale)
    }

    /// The height of one line of system text.
    static func lineHeight(size: Double, weight: UIFont.Weight, pixelScale: Double) -> Double {
        let font = UIFont.systemFont(ofSize: CGFloat(size), weight: weight)
        return ceilToPixel(Double(font.lineHeight), scale: pixelScale)
    }
}

/// The text styles of the block's StyleSheet (FolioFeatureLibrary.tsx:583-692), by name.
enum FolioFeatureLibraryStyles {
    static func color(_ hex: UInt32) -> Color {
        Color(folio: hex)
    }

    /// `sectionTitle` (line 585).
    static let sectionTitle = FolioFeatureLibraryTextStyle(size: 22, weight: .semibold, tracking: -0.4, color: color(0x111111), maxScale: 1.7)
    /// `featureTitle` (line 591): the colour is the feature's ink.
    static func featureTitle(ink: UInt32) -> FolioFeatureLibraryTextStyle {
        FolioFeatureLibraryTextStyle(size: 20, weight: .semibold, lineHeight: 23, tracking: -0.4, color: color(ink), maxScale: 1.5)
    }
    /// `featureBody` (line 592).
    static let featureBody = FolioFeatureLibraryTextStyle(size: 14, lineHeight: 17, color: color(0x555555))
    /// `inviteTitle` (line 594).
    static let inviteTitle = FolioFeatureLibraryTextStyle(size: 20, weight: .semibold, tracking: -0.4, color: color(0x111111), maxScale: 1.6)
    /// `inviteBody` (line 595).
    static let inviteBody = FolioFeatureLibraryTextStyle(size: 13, lineHeight: 16, color: color(0x3A3A3A))
    /// `categoryLabel` (line 603): white when the category is active.
    static func categoryLabel(active: Bool) -> FolioFeatureLibraryTextStyle {
        FolioFeatureLibraryTextStyle(size: 14, weight: .medium, color: active ? color(0xFFFFFF) : color(0x111111))
    }
    /// `appName`, `appHandle`, `appDescription` (lines 607-609).
    static let appName = FolioFeatureLibraryTextStyle(size: 16, weight: .medium, color: color(0x111111))
    static let appHandle = FolioFeatureLibraryTextStyle(size: 13, color: color(0x555555))
    static let appDescription = FolioFeatureLibraryTextStyle(size: 14, lineHeight: 17, color: color(0x555555))
    /// `navLabel` and `navInactive` (line 615).
    static let navLabel = FolioFeatureLibraryTextStyle(size: 15, weight: .medium, color: color(0x111111))
    static let navInactive = FolioFeatureLibraryTextStyle(size: 15, weight: .regular, color: color(0x333333))
    /// `loaderTitle` plus the inline overrides at line 116, and `loaderCopy` at line 117.
    static let loaderTitle = FolioFeatureLibraryTextStyle(size: 19, weight: .semibold, tracking: -0.3, color: color(0x111111))
    static let loaderCopy = FolioFeatureLibraryTextStyle(size: 13, color: color(0x8A8A8A))
    /// `statusText` (line 623).
    static let statusText = FolioFeatureLibraryTextStyle(size: 15, weight: .medium, color: color(0x1F1F1F))
    /// `chipText` (line 630).
    static let chipText = FolioFeatureLibraryTextStyle(size: 13, weight: .medium, color: color(0x1F1F1F))
    /// `libraryTitle`, `libraryBody` (lines 631-632).
    static let libraryTitle = FolioFeatureLibraryTextStyle(size: 20, weight: .semibold, tracking: -0.3, color: color(0x111111))
    static let libraryBody = FolioFeatureLibraryTextStyle(size: 13.5, lineHeight: 17, color: color(0x3A3A3A))
    /// `filterLabel`, `allBooksText` (lines 636, 638).
    static let filterLabel = FolioFeatureLibraryTextStyle(size: 12, weight: .medium, tracking: 0.8, color: color(0x7A7A7A))
    static let allBooksText = FolioFeatureLibraryTextStyle(size: 14, weight: .medium, color: color(0x1F1F1F))
    /// `bookName`, `bookMeta`, `bookNote` (line 640).
    static let bookName = FolioFeatureLibraryTextStyle(size: 16, weight: .medium, lineHeight: 20, color: color(0x111111))
    static let bookMeta = FolioFeatureLibraryTextStyle(size: 13, color: color(0x555555))
    static let bookNote = FolioFeatureLibraryTextStyle(size: 13, lineHeight: 16, color: color(0x555555))
    /// `buttonLabel` (line 661): the colour is set by the button.
    static func buttonLabel(color ink: Color) -> FolioFeatureLibraryTextStyle {
        FolioFeatureLibraryTextStyle(size: 16, weight: .medium, color: ink)
    }
    /// `detailTitle`, `detailAuthor` (lines 664-665): Georgia.
    static let detailTitle = FolioFeatureLibraryTextStyle(size: 27, face: .georgia, lineHeight: 33, color: color(0x111111))
    static let detailAuthor = FolioFeatureLibraryTextStyle(size: 17, face: .georgiaItalic, color: color(0x555555))
    /// `contentTitle`, `paragraph` (lines 668-669).
    static let contentTitle = FolioFeatureLibraryTextStyle(size: 17, weight: .semibold, color: color(0x111111))
    static let paragraph = FolioFeatureLibraryTextStyle(size: 15, lineHeight: 22, color: color(0x2A2A2A))
    /// `sourceText` (line 675): Menlo.
    static let sourceText = FolioFeatureLibraryTextStyle(size: 12, face: .menlo, tracking: 0.8, color: color(0x6A6A6A))
    /// `sourceDotText` (line 674).
    static let sourceDotText = FolioFeatureLibraryTextStyle(size: 8, weight: .bold, color: color(0xFFFFFF))
    /// `sheetTitle`, `sheetCopy` (lines 678-679).
    static let sheetTitle = FolioFeatureLibraryTextStyle(size: 20, weight: .semibold, tracking: -0.3, color: color(0x111111))
    static let sheetCopy = FolioFeatureLibraryTextStyle(size: 13, lineHeight: 17, color: color(0x555555))
    /// `topicText` (line 683): white when selected.
    static func topicText(selected: Bool) -> FolioFeatureLibraryTextStyle {
        FolioFeatureLibraryTextStyle(size: 15, weight: .medium, color: selected ? color(0xFFFFFF) : color(0x1F1F1F))
    }
    /// `addTopicText` (line 686).
    static let addTopicText = FolioFeatureLibraryTextStyle(size: 15, weight: .medium, color: color(0x1E6FEF))
    /// `topicCount` (line 688).
    static let topicCount = FolioFeatureLibraryTextStyle(size: 13, color: color(0x6A6A6A))
    /// `noticeTitle` (line 691).
    static let noticeTitle = FolioFeatureLibraryTextStyle(size: 20, weight: .semibold, color: color(0x111111))
}
