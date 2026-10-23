import SwiftUI
import UIKit
import CoreText

/// Bundle-local images and glyph outlines keep the canvas independent of app font registration.
enum SpikeToStairChartAssets {
    static let regular = font("ArchivoExpanded-Regular", size: 15)
    static let small = font("ArchivoExpanded-Regular", size: 9)
    static let digits = font("ArchivoExpanded-Black", size: 13)
    static let labels = ["MOTIVATION", "DISCIPLINE"].map { text($0, font: regular, tracking: 2.6) }
    static let days = (0...30).map { text("D\($0)", font: small) }
    static let values = (0...100).map { text(String($0), font: digits) }
    static let streaks = image("floor-streaks")
    static let glints = image("floor-glints")
    static let shards: [Image] = {
        guard let url = Bundle.main.url(forResource: "SpikeToStairChart-shards", withExtension: "png"),
              let cg = UIImage(contentsOfFile: url.path)?.cgImage else { return [] }
        return (0..<6).compactMap { i in
            cg.cropping(to: CGRect(x: i*256, y: 0, width: 256, height: 256)).map {
                Image(decorative: $0, scale: 1)
            }
        }
    }()

    struct GlyphRun {
        var path: Path
        var width: Double
    }

    static func image(_ name: String) -> Image {
        guard let url = Bundle.main.url(forResource: "SpikeToStairChart-" + name, withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else {
            assertionFailure("Missing chart image: \(name)")
            return Image(uiImage: UIImage())
        }
        return Image(uiImage: image)
    }

    static func font(_ name: String, size: Double) -> CTFont {
        guard let url = Bundle.main.url(forResource: "SpikeToStairChart-" + name, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let cg = CGFont(provider) else {
            assertionFailure("Missing chart font: \(name)")
            return CTFontCreateWithName("Helvetica" as CFString, size, nil)
        }
        return CTFontCreateWithGraphicsFont(cg, size, nil, nil)
    }

    static func text(_ word: String, font: CTFont, tracking: Double = 0) -> GlyphRun {
        let chars = Array(word.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        var path = Path()
        var x = 0.0
        for i in glyphs.indices {
            if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                path.addPath(Path(glyph), transform: CGAffineTransform(a: 1,b: 0,c: 0,d: -1,tx: x,ty: 0))
            }
            x += advances[i].width + (i < glyphs.count-1 ? tracking : 0)
        }
        return GlyphRun(path: path, width: x)
    }
}
