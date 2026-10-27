import CoreText
import SwiftUI
import UIKit

/// The colours of the React Native file's `styles`, all sRGB hex.
enum PaperDeckTutorialPalette {
    static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255,
            opacity: opacity
        )
    }

    /// `screen.backgroundColor`.
    static let ground = rgb(0x050505)
    /// `headerTitle.color`.
    static let headerTitle = rgb(0xF8F7F7)
    /// `progress.color`.
    static let progress = rgb(0x8E8A8D)
    /// `closeDisc.backgroundColor`.
    static let closeDisc = rgb(0xF4F2F4)
    /// The X inside the close disc.
    static let closeMark = rgb(0x0A090A)
    /// `chromeDisc.backgroundColor`.
    static let chromeDisc = rgb(0x2C2B2D)
    /// The gear and lock glyphs.
    static let chromeGlyph = rgb(0xD8D6D8)
    /// `cardTitle.color`.
    static let cardTitle = rgb(0x141215)
    /// `number.color`.
    static let cardNumber = rgb(0x1F1B1F)
    /// The paper's outline (`stroke="#E7E4EA"`).
    static let paperStroke = rgb(0xE7E4EA)
    /// `coverBack.backgroundColor`.
    static let coverBack = rgb(0x1B2240)
    /// `coverClip.backgroundColor`.
    static let coverFront = rgb(0x9260DC)
    /// `backShade`: `#00000024`.
    static let backShade = rgb(0x000000, opacity: 36.0 / 255.0)
    /// The progressive blur's solid fallback for the dark tint.
    static let blurFallback = rgb(0x20231F)
}

/// The bundled fonts and the four cover illustrations, loaded once.
enum PaperDeckTutorialAssets {
    /// Registers the fonts and decodes the images; call before the first frame.
    static func prepare() {
        _ = fontsRegistered
        _ = images
    }

    private static let fontsRegistered: Bool = {
        for name in ["SourceSerif4-Semibold", "Inter-Bold", "Inter-Regular", "Ionicons"] {
            guard let url = Bundle.main.url(forResource: "PaperDeckTutorial-\(name)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return true
    }()

    private static let images: [String: UIImage] = {
        var result: [String: UIImage] = [:]
        for name in ["cover-thinking", "choice-food", "cover-sunny", "cover-night"] {
            result[name] = load(name)
        }
        return result
    }()

    private static func load(_ name: String) -> UIImage {
        guard let url = Bundle.main.url(forResource: "PaperDeckTutorial-\(name)", withExtension: "webp"),
              let source = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return source.preparingForDisplay() ?? source
    }

    /// One of the 720 x 1080 covers: `cover-thinking`, `cover-sunny`, `cover-night` or `choice-food`.
    static func image(_ name: String) -> Image {
        Image(uiImage: images[name] ?? UIImage()).interpolation(.high)
    }
}

/// The type of the file: `PaperDeckSerif-Semibold`, `PaperDeckInter-Bold` and `PaperDeckInter-Regular`.
enum PaperDeckTutorialFonts {
    /// Source Serif 4 Semibold (PostScript name `SourceSerif4-Semibold`).
    static func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Semibold", size: size) ?? .systemFont(ofSize: size, weight: .semibold)
    }

    /// Inter at its 24 pt optical size (PostScript names `Inter24pt-Bold` and `Inter24pt-Regular`).
    static func inter(bold: Bool, size: CGFloat) -> UIFont {
        let name = bold ? "Inter24pt-Bold" : "Inter24pt-Regular"
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: bold ? .bold : .regular)
    }

    /// The Ionicons font that `@expo/vector-icons` draws its glyphs with.
    static func ionicons(_ size: CGFloat) -> Font {
        .custom("Ionicons", fixedSize: size)
    }
}

/// A line of text that sits in a box exactly `lineHeight` tall, the way React Native lays out a
/// `Text` with a `lineHeight` on iOS: TextKit puts the baseline `lineHeight - descent` below the top
/// of the box, and React Native only moves the text to the middle of the box (by half of the
/// difference) when the box is at least as tall as the font's own line.
struct PaperDeckTutorialLineStyle {
    let font: UIFont
    let lineHeight: CGFloat
    var kern: CGFloat = 0
    let color: Color
    var monospacedDigits = false
}

struct PaperDeckTutorialLine: View {
    let text: String
    let style: PaperDeckTutorialLineStyle

    var body: some View {
        let natural = style.font.lineHeight
        let base = Text(text)
            .font(Font(style.font as CTFont))
            .kerning(style.kern)
        let digits = style.monospacedDigits ? base.monospacedDigit() : base
        digits
            .foregroundStyle(style.color)
            .lineLimit(1)
            .fixedSize()
            .frame(height: style.lineHeight, alignment: style.lineHeight < natural ? .bottom : .center)
    }
}

/// Word-wraps a title the way TextKit does, so each line can get its own `lineHeight` box.
enum PaperDeckTutorialTypography {
    /// The lines of `text` (explicit `\n` included) when set in `font` with letter spacing `kern`
    /// inside `width` points.
    static func lines(_ text: String, font: UIFont, kern: CGFloat, width: CGFloat) -> [String] {
        var result: [String] = []
        for paragraph in text.components(separatedBy: "\n") {
            let attributed = NSAttributedString(string: paragraph, attributes: [.font: font, .kern: kern])
            let typesetter = CTTypesetterCreateWithAttributedString(attributed as CFAttributedString)
            let length = attributed.length
            var start = 0
            while start < length {
                let suggested = CTTypesetterSuggestLineBreak(typesetter, start, Double(width))
                let count = min(max(1, suggested), length - start)
                let range = NSRange(location: start, length: count)
                let line = attributed.attributedSubstring(from: range).string.trimmingCharacters(in: .whitespaces)
                result.append(line)
                start += count
            }
        }
        return result
    }
}
