import CoreGraphics
import CoreText
import Foundation
import ImageIO
import SwiftUI

/// The block's bundled files: the display face and the gift card's art. Both are checked when first used:
/// a missing file stops the app in a debug build instead of drawing nothing.
enum EnvelopeGiftRevealAssets {
    private static func url(_ file: String, _ ext: String) -> URL {
        let name = "EnvelopeGiftReveal-" + file
        if let url = Bundle.main.url(forResource: name, withExtension: ext) { return url }
        if let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "Assets") { return url }
        preconditionFailure("Missing bundled asset \(name).\(ext)")
    }

    /// Archivo Expanded Black (OFL). Registered once for the process; the name is read from the file itself.
    static let displayFontName: String = {
        let fontURL = url("ArchivoExpanded-Black", "ttf")
        CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, nil)
        guard let provider = CGDataProvider(url: fontURL as CFURL),
              let font = CGFont(provider),
              let name = font.postScriptName else {
            preconditionFailure("Archivo Expanded Black did not load")
        }
        return name as String
    }()

    static func displayFont(_ size: Double) -> Font {
        Font.custom(displayFontName, fixedSize: size)
    }

    /// The card art cropped to the card's proportions (`fit="cover"`) at three pixels to the point, so the canvas
    /// draws a bitmap that is already the right size.
    static let cardArt: CGImage = {
        let pixelsPerPoint = EnvelopeGiftRevealTokens.Assets.pixelsPerPoint
        let width = Int(EnvelopeGiftRevealMetrics.cardW * pixelsPerPoint)
        let height = Int(EnvelopeGiftRevealMetrics.cardH * pixelsPerPoint)
        guard let source = CGImageSourceCreateWithURL(url("gift-card-art", "jpg") as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
              let context = CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
              ) else {
            preconditionFailure("The gift card art did not load")
        }
        let scale = max(Double(width) / Double(image.width), Double(height) / Double(image.height))
        let drawn = CGSize(width: Double(image.width) * scale, height: Double(image.height) * scale)
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(
            x: (Double(width) - drawn.width) / 2, y: (Double(height) - drawn.height) / 2,
            width: drawn.width, height: drawn.height
        ))
        guard let cover = context.makeImage() else { preconditionFailure("The gift card art did not render") }
        return cover
    }()
}

/// Text as outlines, so the canvas can put the baseline exactly where the source puts it (Skia's `Text` takes a
/// baseline origin, and lays glyphs out at their advances with no kerning).
enum EnvelopeGiftRevealGlyphs {
    struct Run {
        let path: Path
        let width: Double
    }

    private static var cache: [String: Run] = [:]
    private static let lock = NSLock()

    /// `text` set in the font at `size`, with its baseline on y = 0 and its left edge on x = 0.
    static func run(_ text: String, fontName: String, size: Double) -> Run {
        let key = "\(fontName)|\(size)|\(text)"
        lock.lock()
        defer { lock.unlock() }
        if let hit = cache[key] { return hit }
        let font = CTFontCreateWithName(fontName as CFString, size, nil)
        let characters = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: characters.count)
        CTFontGetGlyphsForCharacters(font, characters, &glyphs, characters.count)
        var advances = [CGSize](repeating: .zero, count: characters.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, characters.count)
        let combined = CGMutablePath()
        var x = 0.0
        for (glyph, advance) in zip(glyphs, advances) {
            if let outline = CTFontCreatePathForGlyph(font, glyph, nil) {
                // Glyph outlines are y-up; the canvas is y-down.
                combined.addPath(outline, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += advance.width
        }
        let run = Run(path: Path(combined), width: x)
        cache[key] = run
        return run
    }
}
