import CoreText
import SwiftUI

/// Font bytes are copied from RN. Drawing uses fractional CoreText advances and original outlines.
enum SparkSphereScoreResources {
    static let names = ["Figtree-Medium", "Figtree-SemiBold", "Figtree-Bold", "BarlowSemiCondensed-SemiBold", "BarlowSemiCondensed-Bold"]
    static let register: Void = {
        for name in names {
            if let url = Bundle.main.url(forResource: "SparkSphereScore-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static let fonts: [CGFont] = {
        _ = register
        return names.map { name in
            guard let url = Bundle.main.url(forResource: "SparkSphereScore-" + name, withExtension: "ttf"),
                  let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) else {
                preconditionFailure("Missing SparkSphereScore font: \(name)")
            }
            return font
        }
    }()
    static func font(_ face: Int, _ size: Double) -> CTFont { CTFontCreateWithGraphicsFont(fonts[face], size, nil, nil) }
    struct Line {
        var path: Path
        var width: Double
    }
    struct Glyphs {
        let font: CTFont
        let paths: [Character: Path]
        let widths: [Character: Double]
        init(_ face: Int, _ size: Double) {
            font = SparkSphereScoreResources.font(face, size)
            var paths: [Character: Path] = [:], widths: [Character: Double] = [:]
            for v in 32...126 {
                let character = Character(UnicodeScalar(v)!), codes = Array(String(character).utf16)
                var code = codes[0], glyph: CGGlyph = 0
                CTFontGetGlyphsForCharacters(font, &code, &glyph, 1)
                var advance = CGSize.zero
                CTFontGetAdvancesForGlyphs(font, .horizontal, &glyph, &advance, 1)
                paths[character] = Path(CTFontCreatePathForGlyph(font, glyph, nil) ?? CGMutablePath())
                    .applying(CGAffineTransform(scaleX: 1, y: -1))
                widths[character] = advance.width
            }
            self.paths = paths; self.widths = widths
        }
        func width(_ text: String) -> Double { text.reduce(0) { $0 + (widths[$1] ?? 0) } }
        func line(_ text: String) -> Line {
            var path = Path(), x = 0.0
            for ch in text {
                if let glyph = paths[ch] { path.addPath(glyph, transform: CGAffineTransform(translationX: x, y: 0)) }
                x += widths[ch] ?? 0
            }
            return Line(path: path, width: x)
        }
    }
    static func attributes(_ face: Int, _ size: Double, _ tracking: Double) -> [NSAttributedString.Key: Any] {
        [.init(kCTFontAttributeName as String): font(face, size), .init(kCTKernAttributeName as String): tracking]
    }
    static func shaped(_ text: String, _ face: Int, _ size: Double, _ tracking: Double = 0) -> Line {
        outlines(CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes(face, size, tracking))))
    }
    static func outlines(_ line: CTLine) -> Line {
        var result = Path()
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let count = CTRunGetGlyphCount(run), attrs = CTRunGetAttributes(run) as NSDictionary
            let font = attrs[kCTFontAttributeName] as! CTFont
            var glyphs = [CGGlyph](repeating: 0, count: count), positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            for i in 0..<count {
                if let raw = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                    let transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: positions[i].x, ty: -positions[i].y)
                    result.addPath(Path(raw), transform: transform)
                }
            }
        }
        return Line(path: result, width: CTLineGetTypographicBounds(line, nil, nil, nil))
    }
    static func bodyLines(_ text: String, size: Double, width: Double, count: Int) -> [Line] {
        let attrs = attributes(0, size, 0.018 * size)
        let attributed = NSAttributedString(string: text, attributes: attrs)
        let typesetter = CTTypesetterCreateWithAttributedString(attributed)
        var start = 0, result: [Line] = []
        while start < attributed.length && result.count < count {
            let length = CTTypesetterSuggestLineBreak(typesetter, start, width)
            guard length > 0 else { break }
            var line = CTTypesetterCreateLine(typesetter, CFRange(location: start, length: length))
            if result.count == count - 1 && start + length < attributed.length {
                let rest = CTTypesetterCreateLine(typesetter, CFRange(location: start, length: attributed.length - start))
                let token = CTLineCreateWithAttributedString(NSAttributedString(string: "…", attributes: attrs))
                line = CTLineCreateTruncatedLine(rest, width, .end, token) ?? line
            }
            result.append(outlines(line)); start += length
        }
        return result
    }
    static let titles = ["Turning back the clock", "Steady and Healthy", "Running fast", "Burning the candle"]
    static let bodies = [
        "At this pace your body is rewinding. Sleep, recovery and training would all have to line up for weeks.",
        "Your pace of aging is slower than the calendar, mostly thanks to steadier sleep and recovery. Keep your current habits to stay here.",
        "At this pace the years add up quicker than they should. Shorter nights and a heavy training load are the usual cause.",
        "Held for long, this pace costs you years. A few quieter days and an earlier night would bring it down fast."
    ]
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func color(_ rgb: [Double]) -> Color { Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2]) }
    static func draw(_ c: GraphicsContext, _ line: Line, _ x: Double, _ y: Double, _ color: Color, _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        var context = c
        context.translateBy(x: x, y: y)
        context.fill(line.path, with: .color(color.opacity(alpha)))
    }
}
