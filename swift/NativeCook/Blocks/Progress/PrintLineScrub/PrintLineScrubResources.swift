import CoreText
import ImageIO
import SwiftUI

enum PrintLineScrubResources {
    static let register: Void = {
        for weight in ["Regular", "Medium"] {
            if let url = Bundle.main.url(forResource: "PrintLineScrub-Urbanist-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    struct Line {
        let path: Path
        let width: Double
        let positions: [Double]
    }
    static func font(_ weight: String, _ size: Double) -> CTFont {
        _ = register
        let url = Bundle.main.url(forResource: "PrintLineScrub-Urbanist-\(weight)", withExtension: "ttf")!
        let provider = CGDataProvider(url: url as CFURL)!
        return CTFontCreateWithGraphicsFont(CGFont(provider)!, size, nil, nil)
    }
    static let regular = font("Regular", 15)
    static let medium = font("Medium", 15)
    static let small = font("Medium", 12)

    /// CTFont fractional advances match getGlyphWidths; outlines retain Skia's baseline convention.
    static func line(_ text: String, _ font: CTFont, tracking: Double = 0) -> Line {
        let chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
        var advances = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
        var path = Path(), x = 0.0, positions: [Double] = []
        for i in ids.indices {
            positions.append(x)
            if let glyph = CTFontCreatePathForGlyph(font, ids[i], nil) {
                path.addPath(Path(glyph), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += advances[i].width + (i + 1 < ids.count ? tracking : 0)
        }
        return Line(path: path, width: x, positions: positions)
    }
    static let title = line("Printing Progress", regular)
    static let workspace = line("Workspace", medium)
    static let file = line("MSBK_KAIJU_01", small, tracking: 0.04 * 12)
    static let layers = (0...480).map { line("Layer \($0) / 480", regular) }
    static let percentages = (0...100).map { line("\($0)%", medium) }

    /// A one-time, unscaled RGBA atlas avoids SwiftUI's single shader-image argument limit.
    /// The bundled WebPs remain byte-identical to RN; there is no lossy image re-encoding.
    static let atlas: Image = {
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let bitmap = CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue
        let context = CGContext(data: nil, width: 2048, height: 1536, bitsPerComponent: 8,
                                bytesPerRow: 2048 * 4, space: colorSpace, bitmapInfo: bitmap)!
        context.interpolationQuality = .none
        context.setBlendMode(.copy)
        for (index, name) in ["solid", "slicer"].enumerated() {
            let url = Bundle.main.url(forResource: "PrintLineScrub-frillwarden-\(name)", withExtension: "webp")!
            let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
            let options = [kCGImageSourceShouldCacheImmediately: true] as CFDictionary
            let image = CGImageSourceCreateImageAtIndex(source, 0, options)!
            context.draw(image, in: CGRect(x: CGFloat(index * 1024), y: 0, width: 1024, height: 1536))
        }
        return Image(decorative: context.makeImage()!, scale: 1)
    }()

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func draw(_ context: GraphicsContext, _ line: Line, x: Double, baseline: Double,
                     scale: Double, color: Color, opacity: Double = 1) {
        var c = context
        c.translateBy(x: x, y: baseline); c.scaleBy(x: scale, y: scale)
        c.opacity *= opacity
        c.fill(line.path, with: .color(color))
    }
    static func segment(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path(); p.move(to: CGPoint(x: x0, y: y0)); p.addLine(to: CGPoint(x: x1, y: y1)); return p
    }
}
