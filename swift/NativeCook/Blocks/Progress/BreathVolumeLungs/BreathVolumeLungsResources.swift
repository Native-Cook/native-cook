import CoreText
import SwiftUI
import UIKit

enum BreathVolumeLungsResources {
    static let register: Void = {
        for weight in ["Regular", "Medium"] {
            if let url = Bundle.main.url(forResource: "BreathVolumeLungs-Urbanist-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func rgb(_ hex: UInt32) -> [Double] {
        [Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255, Double(hex & 255) / 255]
    }
    static func color(_ hex: UInt32) -> Color {
        let c = rgb(hex)
        return Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: 1)
    }
    static func font(_ size: Double, medium: Bool = false, tabular: Bool = false) -> UIFont {
        _ = register
        let base = UIFont(name: medium ? "Urbanist-Medium" : "UrbanistRoman-Regular", size: size) ?? .systemFont(ofSize: size)
        guard tabular else { return base }
        let settings: [[UIFontDescriptor.FeatureKey: Int]] = [[.type: kNumberSpacingType, .selector: kMonospacedNumbersSelector]]
        return UIFont(descriptor: base.fontDescriptor.addingAttributes([.featureSettings: settings]), size: size)
    }
    /// RN's iOS Text/TextInput use Urbanist's native ascender and fractional advances, not a synthetic baseline.
    static func text(_ context: GraphicsContext, _ text: String, x: Double, top: Double,
                     width: Double, size: Double, align: Int = 0, medium: Bool = false,
                     ink: UInt32 = 0xF7F7FA, alpha: Double = 1, tabular: Bool = false, height: Double? = nil) {
        guard alpha > 0 else { return }
        var f = font(size, medium: medium, tabular: tabular)
        var line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: f]))
        var advance = CTLineGetTypographicBounds(line, nil, nil, nil)
        let widthFit = width > 0 && advance > width ? width / advance : 1
        let heightFit = height.map { min(1, $0 / f.lineHeight) } ?? 1
        let fit = min(widthFit, heightFit)
        if fit < 1 {
            f = font(size * fit, medium: medium, tabular: tabular)
            line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: f]))
            advance = CTLineGetTypographicBounds(line, nil, nil, nil)
        }
        let dx = align == 1 ? (width - advance) / 2 : align == 2 ? width - advance : 0
        var path = Path()
        for value in CTLineGetGlyphRuns(line) as NSArray {
            let run = value as! CTRun
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            let runFont = (CTRunGetAttributes(run) as NSDictionary)[kCTFontAttributeName] as! CTFont
            for i in 0..<count {
                if let raw = CTFontCreatePathForGlyph(runFont, glyphs[i], nil) {
                    let transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1,
                                                      tx: x + dx + positions[i].x,
                                                      ty: top + f.ascender - positions[i].y)
                    path.addPath(Path(raw), transform: transform)
                }
            }
        }
        context.fill(path, with: .color(color(ink).opacity(alpha)))
    }
}
