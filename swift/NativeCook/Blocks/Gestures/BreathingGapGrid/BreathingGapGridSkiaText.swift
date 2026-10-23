import CoreText
import SwiftUI

/// Text measured and spaced the way the RN version's Skia fonts do it, so tracked labels line up.
///
/// Skia's `measureText` returns the ink bounds of the glyphs, rounded out to whole points at the
/// font's size and padded by one on each side, not their advance; the RN code steps from letter to
/// letter by that width plus its tracking, which spaces letters wider than their advances.
enum BreathingGapGridSkiaText {
    private static let fonts = NSCache<NSString, CTFont>()

    static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let key = "\(name)-\(size)" as NSString
        if let hit = fonts.object(forKey: key) { return hit }
        let font = CTFontCreateWithName(name as CFString, size, nil)
        fonts.setObject(font, forKey: key)
        return font
    }

    /// Skia's measured width of `text`.
    static func width(_ text: String, _ font: CTFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        guard ink.width > 0 else { return 0 }
        return Double(ceil(ink.maxX) - floor(ink.minX)) + 2
    }

    /// A letter's step; a lone space has no ink, so it is taken from between two letters.
    static func advance(_ letter: String, _ font: CTFont) -> Double {
        if letter != " " { return width(letter, font) }
        return width("a a", font) - width("aa", font)
    }

    /// Each letter of `text` and where it starts, `tracking` pt apart, and the whole run's width.
    static func tracked(_ text: String, _ font: CTFont, tracking: Double) -> (letters: [String], at: [Double], width: Double) {
        var letters: [String] = []
        var at: [Double] = []
        var x = 0.0
        for letter in text {
            letters.append(String(letter))
            at.append(x)
            x += advance(String(letter), font) + tracking
        }
        return (letters, at, x - tracking)
    }
}

/// Text drawn a letter at a time, `tracking` pt apart, its first letter's baseline origin at the
/// view's own origin. It takes no room in the layout.
struct BreathingGapGridTrackedText: View {
    let text: String
    let fontName: String
    let size: CGFloat
    let tracking: Double
    let color: Color

    var body: some View {
        let run = BreathingGapGridSkiaText.tracked(text, BreathingGapGridSkiaText.font(fontName, size), tracking: tracking)
        Color.clear
            .frame(width: 0, height: 0)
            .overlay(alignment: Alignment(horizontal: .leading, vertical: .firstTextBaseline)) {
                ZStack(alignment: Alignment(horizontal: .leading, vertical: .firstTextBaseline)) {
                    ForEach(run.letters.indices, id: \.self) { i in
                        Text(run.letters[i])
                            .font(.custom(fontName, fixedSize: size))
                            .foregroundStyle(color)
                            .fixedSize()
                            .offset(x: run.at[i])
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(text)
            }
    }
}
