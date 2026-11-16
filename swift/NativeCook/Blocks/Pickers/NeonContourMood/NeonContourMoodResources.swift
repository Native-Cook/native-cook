import CoreText
import SwiftUI

/// Glyph outlines and unrounded advances match SkFont.getGlyphWidths, with no kerning added.
enum NeonContourMoodResources {
    struct Line {
        let path: Path
        let glyphs: [Path]
        let positions: [Double]
        let width: Double
    }
    static let register: Void = {
        for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Nunito-ExtraBold"] {
            if let url = Bundle.main.url(forResource: "NeonContourMood-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func line(_ text: String, _ name: String, _ size: Double, _ tracking: Double = 0) -> Line {
        _ = register
        let url = Bundle.main.url(forResource: "NeonContourMood-\(name)", withExtension: "ttf")!
        let font = CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
        let chars = Array(text.utf16)
        var ids = Array(repeating: CGGlyph(), count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
        var advances = Array(repeating: CGSize.zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
        var paths: [Path] = [], positions: [Double] = [], x = 0.0, all = Path()
        for i in ids.indices {
            let raw = CTFontCreatePathForGlyph(font, ids[i], nil) ?? CGMutablePath()
            let path = Path(raw).applying(CGAffineTransform(scaleX: 1, y: -1))
            paths.append(path); positions.append(x)
            all.addPath(path, transform: CGAffineTransform(translationX: x, y: 0))
            x += advances[i].width + (i < ids.count - 1 ? tracking : 0)
        }
        return Line(path: all, glyphs: paths, positions: positions, width: x)
    }
    static let words = NeonContourMoodTheme.moods.map { line($0.word, "Nunito-ExtraBold", 54, 1.4) }
    static func draw(_ context: GraphicsContext, _ line: Line, x: Double, y: Double, color: Color) {
        var c = context
        c.translateBy(x: x, y: y)
        c.fill(line.path, with: .color(color))
    }
}
