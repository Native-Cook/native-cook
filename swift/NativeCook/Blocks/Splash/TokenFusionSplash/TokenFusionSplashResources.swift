import CoreText
import SwiftUI
import UIKit

/// The original Outfit face and the reference's 7 × 128 SDF atlas, baked without changing its samples.
enum TokenFusionSplashResources {
    static let register: Void = {
        if let url = Bundle.main.url(forResource: "TokenFusionSplash-Outfit-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static let atlas: Image = {
        let url = Bundle.main.url(forResource: "TokenFusionSplash-glyph-atlas", withExtension: "png")!
        return Image(uiImage: UIImage(contentsOfFile: url.path)!)
    }()
    struct TokenFusionSplashLine {
        let path: Path, width: Double, ascent: Double
    }
    static let word: TokenFusionSplashLine = {
        _ = register
        let url = Bundle.main.url(forResource: "TokenFusionSplash-Outfit-Regular", withExtension: "ttf")!
        let provider = CGDataProvider(url: url as CFURL)!
        let font = CTFontCreateWithGraphicsFont(CGFont(provider)!, 30, nil, nil)
        let text = NSAttributedString(string: "korra", attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTKernAttributeName as String): -0.6
        ])
        let line = CTLineCreateWithAttributedString(text)
        var path = Path()
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let n = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: n), positions = [CGPoint](repeating: .zero, count: n)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            for i in glyphs.indices {
                if let p = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                    path.addPath(Path(p), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: positions[i].x, ty: -positions[i].y))
                }
            }
        }
        return TokenFusionSplashLine(path: path, width: CTLineGetTypographicBounds(line, nil, nil, nil), ascent: CTFontGetAscent(font))
    }()
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    static func rgb(_ hex: UInt32) -> [Double] {
        [Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255, Double(hex & 255) / 255]
    }
    static let ids = ["ember", "prism", "tide", "arc"]
    static let labels = ["Ember token", "Prism token", "Tide token", "Arc token"]
    static let colors: [[Double]] = [0xD67C16, 0x3B97E3, 0x26A17B, 0x1B2A8F].map { rgb(UInt32($0)) }
    static let palette: [Double] = [0x9B7BEF, 0x6A5CE6, 0xEDE4FF, 0x232325, 0x2C2C2F, 0xFDFBFF].flatMap { rgb(UInt32($0)) }
}
