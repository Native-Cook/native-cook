import CoreText
import SwiftUI

/// Bundled Outfit outlines and fractional advances; no platform font substitution or glyph rounding.
enum GlassPayMorphResources {
    static let register: Void = {
        for weight in ["Light", "Regular", "Medium", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "GlassPayMorph-Outfit-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    struct Line {
        let path: Path
        let glyphs: [Path]
        let positions: [Double]
        let width: Double
    }

    static func line(_ text: String, _ weight: String, _ size: Double, _ tracking: Double = 0) -> Line {
        _ = register
        let url = Bundle.main.url(forResource: "GlassPayMorph-Outfit-\(weight)", withExtension: "ttf")!
        let provider = CGDataProvider(url: url as CFURL)!
        let font = CTFontCreateWithGraphicsFont(CGFont(provider)!, size, nil, nil)
        let characters = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: characters.count)
        CTFontGetGlyphsForCharacters(font, characters, &ids, ids.count)
        var advances = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
        var paths: [Path] = [], positions: [Double] = [], x = 0.0
        var all = Path()
        for i in ids.indices {
            let raw = CTFontCreatePathForGlyph(font, ids[i], nil) ?? CGMutablePath()
            let path = Path(raw).applying(CGAffineTransform(scaleX: 1, y: -1))
            paths.append(path); positions.append(x)
            all.addPath(path, transform: CGAffineTransform(translationX: x, y: 0))
            x += advances[i].width + (i < ids.count - 1 ? tracking : 0)
        }
        return Line(path: all, glyphs: paths, positions: positions, width: x)
    }

    static let number = ["8104", "2267", "5390", "4417"].map { line($0, "Light", 18.4) }
    static let numberX: [Double] = {
        var x = 37.5, result: [Double] = []
        for group in number { result.append(x); x += group.width + 8.4 }
        return result
    }()
    static let name = line("NOA LINDGREN", "Regular", 12, 0.4)
    static let brand = line("solune", "SemiBold", 18, -0.2)
    static let last4 = line("•••• 4417", "Regular", 14, 0.6)
    static let captions = [line("Hold near reader", "Medium", 17), line("or tap the card", "Regular", 14),
                           line("Paid $24.80", "Medium", 17), line("Juniper & Rye · 09:41", "Regular", 14)]
    static let chip = Path(roundedRect: CGRect(x: 43.5, y: 23.5, width: 29.5, height: 29.5), cornerRadius: 8.5)
    static let chipLines: [[CGPoint]] = [
        [0.05, 0.24, 0.4, 0.24, 0.4, 1.0], [0.05, 0.47, 0.22, 0.47], [0.05, 0.75, 0.4, 0.75],
        [0.61, 0.0, 0.61, 0.58, 0.63, 0.65, 0.68, 0.69, 0.75, 0.7, 1.0, 0.7],
        [0.61, 0.24, 1.0, 0.24], [0.61, 0.47, 1.0, 0.47]
    ].map { points in stride(from: 0, to: points.count, by: 2).map { CGPoint(x: 43.5 + points[$0] * 29.5, y: 23.5 + points[$0 + 1] * 29.5) } }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func draw(_ context: GraphicsContext, _ line: Line, _ x: Double, _ base: Double, _ alpha: Double) {
        guard alpha > 0.003 else { return }
        var c = context
        c.translateBy(x: x, y: base)
        c.fill(line.path, with: .color(.white.opacity(alpha)))
    }
    static func arc(_ x: Double, _ y: Double, _ r: Double, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        p.addArc(center: CGPoint(x: x, y: y), radius: r, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
        return p
    }
    static func trimmed(_ points: [CGPoint], _ trim: Double) -> Path {
        var p = Path()
        p.move(to: points[0])
        var total = 0.0
        for i in 1..<points.count { total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y) }
        var left = total * trim
        for i in 1..<points.count where left > 0 {
            let a = points[i - 1], b = points[i], length = hypot(b.x - a.x, b.y - a.y)
            let k = min(1, left / length)
            p.addLine(to: CGPoint(x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k))
            left -= length
        }
        return p
    }
}
