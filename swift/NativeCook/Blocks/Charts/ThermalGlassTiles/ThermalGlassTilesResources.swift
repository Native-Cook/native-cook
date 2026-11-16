import CoreText
import SwiftUI
import UIKit

@MainActor
final class ThermalGlassTilesResources {
    static let register: Void = {
        for weight in ["Light", "Regular"] {
            if let url = Bundle.main.url(forResource: "ThermalGlassTiles-Urbanist-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    struct Line {
        let path: Path
        let width: Double
    }
    private var lines: [String: Line] = [:]
    private var fonts: [String: CTFont] = [:]
    let atlas: Image

    init() {
        _ = Self.register
        // SwiftUI accepts one shader image. Pack the three source images without resampling,
        // with two empty guard rows. Shader sampling clamps to each original image's bounds.
        let width = 1536, height = 1671
        let c = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                          space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        c.translateBy(x: 0, y: Double(height)); c.scaleBy(x: 1, y: -1)
        c.interpolationQuality = .none
        c.setBlendMode(.copy)
        UIGraphicsPushContext(c)
        for (name, y) in [("runner-heat-atlas", 0), ("runner-heat-atlas-blur", 1282), ("thermal-palette", 1604)] {
            let url = Bundle.main.url(forResource: "ThermalGlassTiles-\(name)", withExtension: "png")!
            let image = UIImage(contentsOfFile: url.path)!
            image.draw(in: CGRect(x: 0, y: Double(y), width: image.size.width, height: image.size.height))
        }
        UIGraphicsPopContext()
        atlas = Image(decorative: c.makeImage()!, scale: 1)
    }

    func font(_ size: Double, light: Bool) -> CTFont {
        let key = "\(size)|\(light)"
        if let font = fonts[key] { return font }
        let url = Bundle.main.url(forResource: "ThermalGlassTiles-Urbanist-\(light ? "Light" : "Regular")", withExtension: "ttf")!
        let provider = CGDataProvider(url: url as CFURL)!
        let font = CTFontCreateWithGraphicsFont(CGFont(provider)!, size, nil, nil)
        fonts[key] = font
        return font
    }

    /// Raw glyph advances match Skia getGlyphWidths; no shaping, kerning or integer rounding.
    func line(_ text: String, _ size: Double, light: Bool = false) -> Line {
        let key = "\(text)|\(size)|\(light)"
        if let line = lines[key] { return line }
        let font = font(size, light: light), chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
        var advances = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
        var path = Path(), x = 0.0
        for i in ids.indices {
            if let outline = CTFontCreatePathForGlyph(font, ids[i], nil) {
                path.addPath(Path(outline), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += advances[i].width
        }
        let line = Line(path: path, width: x)
        lines[key] = line
        return line
    }

    nonisolated static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    func draw(_ c: GraphicsContext, _ text: String, x: Double, y: Double, size: Double,
              light: Bool = false, ink: Color, opacity: Double = 1) {
        guard !text.isEmpty, opacity > 0 else { return }
        var context = c
        context.translateBy(x: x, y: y); context.opacity *= opacity
        context.fill(line(text, size, light: light).path, with: .color(ink))
    }
}
