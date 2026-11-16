import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Original bundled fonts/atlas. Glyph paths keep fractional advances and RN's measured baselines.
@MainActor
final class FlipFocusBadgeResources {
    static let shared = FlipFocusBadgeResources()
    static let register: Void = {
        for weight in ["Medium", "SemiBold", "Bold"] {
            if let url = Bundle.main.url(forResource: "FlipFocusBadge-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    struct Line {
        var path: Path
        var width: Double
    }
    let atlas: Image
    private var lines: [String: Line] = [:]
    private var fonts: [String: CTFont] = [:]
    private init() {
        _ = Self.register
        for weight in ["Medium", "SemiBold", "Bold"] {
            guard let url = Bundle.main.url(forResource: "FlipFocusBadge-Inter-\(weight)", withExtension: "ttf"),
                  let provider = CGDataProvider(url: url as CFURL), let cgFont = CGFont(provider) else {
                preconditionFailure("FlipFocusBadge's bundled Inter font is missing")
            }
            fonts[weight] = CTFontCreateWithGraphicsFont(cgFont, 1, nil, nil)
        }
        // No RGB color conversion may touch the SDF/dome bytes. Carry grayscale in
        // the alpha channel of a black premultiplied image; Metal samples .a.
        guard let url = Bundle.main.url(forResource: "FlipFocusBadge-ffb-glyphs", withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
              image.bitsPerPixel == 8, image.bitsPerComponent == 8,
              let data = image.dataProvider?.data, let bytes = CFDataGetBytePtr(data) else {
            preconditionFailure("FlipFocusBadge's original grayscale atlas is missing")
        }
        let w = image.width, h = image.height
        var rgba = [UInt8](repeating: 0, count: w * h * 4)
        for y in 0..<h { for x in 0..<w { rgba[(y * w + x) * 4 + 3] = bytes[y * image.bytesPerRow + x] } }
        let provider = CGDataProvider(data: Data(rgba) as CFData)!
        let packed = CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                             space: CGColorSpace(name: CGColorSpace.sRGB)!,
                             bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                             provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        atlas = Image(decorative: packed, scale: 1)
    }
    func line(_ text: String, _ weight: String, _ size: Double, tracking: Double = 0, tabular: Bool = false) -> Line {
        let key = "\(text)|\(weight)|\(size)|\(tracking)|\(tabular)"
        if let line = lines[key] { return line }
        var font = CTFontCreateCopyWithAttributes(fonts[weight]!, size, nil, nil)
        if tabular {
            let descriptor = CTFontDescriptorCreateWithAttributes([
                kCTFontFeatureSettingsAttribute as String: [[kCTFontFeatureTypeIdentifierKey as String: kNumberSpacingType,
                                                  kCTFontFeatureSelectorIdentifierKey as String: kMonospacedNumbersSelector]]
            ] as CFDictionary)
            font = CTFontCreateCopyWithAttributes(font, size, nil, descriptor)
        }
        let text = NSAttributedString(string: text, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font,
                                                                NSAttributedString.Key(kCTKernAttributeName as String): tracking])
        let line = CTLineCreateWithAttributedString(text)
        var path = Path()
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count), points = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &points)
            for i in 0..<count {
                if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                    path.addPath(Path(glyph), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: points[i].x, ty: -points[i].y))
                }
            }
        }
        let result = Line(path: path, width: CTLineGetTypographicBounds(line, nil, nil, nil))
        lines[key] = result
        return result
    }
    nonisolated static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    func draw(_ c: GraphicsContext, _ text: String, _ weight: String, _ size: Double, _ x: Double, _ baseline: Double,
              color: UInt32 = 0xFFFFFF, alpha: Double = 1, align: Double = 0.5, tracking: Double = 0, tabular: Bool = false) {
        guard alpha > 0 else { return }
        let l = line(text, weight, size, tracking: tracking, tabular: tabular)
        var context = c
        context.translateBy(x: x - l.width * align, y: baseline)
        context.fill(l.path, with: .color(Self.color(color, alpha)))
    }
}
