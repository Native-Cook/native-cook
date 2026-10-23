import CoreText
import SwiftUI
import UIKit

/// Downloads a photo once and keeps it decoded and already printed: black and white, or black to
/// the accent, with a little more contrast, as the RN colour matrix prints it at draw time.
enum DepthFieldGalleryPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String, print: DepthFieldGalleryCard.Print, accent: UInt32, cached: Bool = true) async -> UIImage? {
        let key = "\(address)|\(print)|\(accent)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Printed off the main thread, so the first frame that draws it does not stall.
        let tint: (Double, Double, Double) = print == .lime
            ? (Double((accent >> 16) & 0xff) / 255, Double((accent >> 8) & 0xff) / 255, Double(accent & 0xff) / 255)
            : (1, 1, 1)
        guard let ready = await Task.detached(priority: .userInitiated, operation: { printed(image, tint: tint) }).value else { return nil }
        if cached { cache.setObject(ready, forKey: key) }
        return ready
    }

    /// Each channel becomes the tint's channel × (1.2 × luminance − 0.1), clamped: the RN matrix.
    private static func printed(_ image: UIImage, tint: (Double, Double, Double)) -> UIImage? {
        guard let cg = image.cgImage else { return nil }
        let w = cg.width
        let h = cg.height
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                  data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space,
                  bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
              )
        else { return nil }
        context.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        guard let data = context.data else { return nil }
        let pixels = data.bindMemory(to: UInt8.self, capacity: w * h * 4)
        let c = DepthFieldGalleryMetrics.contrast
        let bias = 0.5 * (1 - c)
        let kr = 0.2126 * c / 255
        let kg = 0.7152 * c / 255
        let kb = 0.0722 * c / 255
        for i in 0..<(w * h) {
            let o = i * 4
            let level = kr * Double(pixels[o]) + kg * Double(pixels[o + 1]) + kb * Double(pixels[o + 2])
            pixels[o] = UInt8(max(0, min(1, tint.0 * level + bias * tint.0)) * 255 + 0.5)
            pixels[o + 1] = UInt8(max(0, min(1, tint.1 * level + bias * tint.1)) * 255 + 0.5)
            pixels[o + 2] = UInt8(max(0, min(1, tint.2 * level + bias * tint.2)) * 255 + 0.5)
        }
        guard let out = context.makeImage() else { return nil }
        return UIImage(cgImage: out)
    }
}

/// The cards that are not photos, set once as images: their designs only scale with their width.
enum DepthFieldGalleryFaces {
    private typealias M = DepthFieldGalleryMetrics
    private static let cache = NSCache<NSString, UIImage>()
    /// Points per unit the faces are set at, and the pixels per point.
    static let unit: Double = 400
    private static let scale: CGFloat = 2

    static func image(card: DepthFieldGalleryCard, index: Int, accent: UInt32) -> UIImage? {
        switch card.kind {
        case .photo: return nil
        default: break
        }
        let key = "\(index)-\(card.title)-\(accent)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let w = card.size.width * unit
        let h = w * card.size.aspect
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { context in
            draw(card, accent: accent, in: context.cgContext, w: w, h: h)
        }
        cache.setObject(image, forKey: key)
        return image
    }

    /// The corner of a card face `w` by `h`.
    static func corner(w: Double, h: Double) -> Double { min(w, h) * 0.045 }

    /// Where a poster's portrait sits on a face `w` by `h`.
    static func portrait(w: Double, h: Double) -> CGRect {
        let inset = w * 0.07
        let paper = CGRect(x: inset, y: inset, width: w - inset * 2, height: h - inset * 2)
        return CGRect(x: paper.minX + paper.width * 0.22, y: paper.minY + paper.height * 0.36, width: paper.width * 0.56, height: paper.height * 0.5)
    }

    // A card's face, w by h from its top left; the RN version draws it about its middle.
    private static func draw(_ card: DepthFieldGalleryCard, accent: UInt32, in cg: CGContext, w: Double, h: Double) {
        let rect = CGRect(x: 0, y: 0, width: w, height: h)
        let r = corner(w: w, h: h)
        let rounded = UIBezierPath(roundedRect: rect, cornerRadius: r).cgPath
        let medium = CTFontCreateWithName("HelveticaNeue-Medium" as CFString, 100, nil)
        let bold = CTFontCreateWithName("HelveticaNeue-Bold" as CFString, 100, nil)
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        switch card.kind {
        case .photo:
            return
        case let .poster(_, headline):
            // A print in a brushed grey frame: two lines of type over a portrait, the wordmark below.
            cg.saveGState()
            cg.addPath(rounded)
            cg.clip()
            let frame = CGGradient(colorsSpace: space, colors: [ui(0xe4e4e2).cgColor, ui(0x8d8d8b).cgColor] as CFArray, locations: [0, 1])!
            cg.drawLinearGradient(frame, start: .zero, end: CGPoint(x: 0, y: h), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            cg.restoreGState()
            let inset = w * 0.07
            let paper = CGRect(x: inset, y: inset, width: w - inset * 2, height: h - inset * 2)
            cg.setFillColor(ui(0xfafaf8).cgColor)
            cg.fill(paper)
            let size = paper.width * 0.11
            for (i, line) in headline.enumerated() {
                text(line, font: medium, size: size, color: 0x151515, x: paper.minX + paper.width * 0.07, baseline: paper.minY + paper.width * 0.07 + size * (0.9 + Double(i) * 1.05), in: cg)
            }
            text("aloft", font: bold, size: paper.width * 0.07, color: 0x151515, x: paper.minX + paper.width * 0.74, baseline: paper.minY + paper.height * 0.95, in: cg)
        case .dots:
            cg.addPath(rounded)
            cg.setFillColor(ui(accent).cgColor)
            cg.fillPath()
            dots(DepthFieldGalleryArt.dots(for: card), color: M.cardInk, in: cg, w: w, h: h)
        case .glyph:
            cg.addPath(rounded)
            cg.setFillColor(ui(M.cardInk).cgColor)
            cg.fillPath()
            dots(DepthFieldGalleryArt.dots(for: card), color: M.dotInk, in: cg, w: w, h: h)
        case let .chip(rows):
            // A dark, faintly green photograph's worth of ground under two glassy rows.
            cg.saveGState()
            cg.addPath(rounded)
            cg.clip()
            let ground = CGGradient(colorsSpace: space, colors: [ui(0x2c3329).cgColor, ui(0x0b0c0b).cgColor] as CFArray, locations: [0, 1])!
            let centre = CGPoint(x: w / 2 - w * 0.15, y: h / 2 - h * 0.25)
            cg.drawRadialGradient(ground, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: max(w, h), options: [.drawsAfterEndLocation])
            cg.restoreGState()
            for (i, row) in rows.enumerated() {
                let pill = CGRect(x: w / 2 - w * 0.36, y: h / 2 - h * 0.2 + Double(i) * h * 0.26, width: w * 0.72, height: h * 0.19)
                cg.addPath(UIBezierPath(roundedRect: pill, cornerRadius: pill.height / 2).cgPath)
                cg.setFillColor(UIColor(white: 1, alpha: 0.1).cgColor)
                cg.fillPath()
                let cy = pill.midY
                cg.setFillColor(ui(accent).cgColor)
                let dot = pill.height * 0.32
                cg.fillEllipse(in: CGRect(x: pill.minX + pill.height / 2 - dot, y: cy - dot, width: dot * 2, height: dot * 2))
                text(row, font: medium, size: pill.height * 0.42, color: M.dotInk, x: pill.minX + pill.height * 1.05, baseline: cy + pill.height * 0.15, in: cg)
            }
        case let .mark(word):
            cg.addPath(rounded)
            cg.setFillColor(ui(M.markInk).cgColor)
            cg.fillPath()
            // The wordmark, with a square of dots after it.
            text(word, font: bold, size: h * 0.32, color: M.dotInk, x: w * 0.09, baseline: h / 2 + h * 0.11, in: cg)
            cg.setFillColor(ui(accent).cgColor)
            let dot = h * 0.022
            for row in 0..<3 {
                for col in 0..<3 {
                    let x = w - w * 0.2 + Double(col) * w * 0.045
                    let y = h / 2 - h * 0.09 + Double(row) * h * 0.09
                    cg.fillEllipse(in: CGRect(x: x - dot, y: y - dot, width: dot * 2, height: dot * 2))
                }
            }
        }
    }

    private static func dots(_ dots: [DepthFieldGalleryDot], color: UInt32, in cg: CGContext, w: Double, h: Double) {
        cg.setFillColor(ui(color).cgColor)
        for dot in dots {
            let r = dot.r * w
            cg.fillEllipse(in: CGRect(x: w / 2 + dot.x * w - r, y: h / 2 + dot.y * w - r, width: r * 2, height: r * 2))
        }
    }

    /// Text set at `size` from a 100 pt font, its baseline's left end at (x, baseline).
    private static func text(_ string: String, font: CTFont, size: Double, color: UInt32, x: Double, baseline: Double, in cg: CGContext) {
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: ui(color)]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: attributes))
        cg.saveGState()
        // Core Text draws upward from its baseline; flip about it.
        cg.translateBy(x: x, y: baseline)
        cg.scaleBy(x: size / 100, y: -size / 100)
        cg.textPosition = .zero
        CTLineDraw(line, cg)
        cg.restoreGState()
    }

    static func ui(_ hex: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 8) & 0xff) / 255,
            blue: CGFloat(hex & 0xff) / 255,
            alpha: 1
        )
    }
}

/// Text measured and spaced the way the RN version's Skia fonts do it, so tracked labels line up.
///
/// Skia's `measureText` returns the ink bounds of the glyphs, rounded out to whole points at the
/// font's size and padded by one on each side, not their advance; the RN code steps from letter to
/// letter by that width plus its tracking.
enum DepthFieldGallerySkiaText {
    private static let fonts = NSCache<NSString, CTFont>()

    static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let key = "\(name)-\(size)" as NSString
        if let hit = fonts.object(forKey: key) { return hit }
        let font = CTFontCreateWithName(name as CFString, size, nil)
        fonts.setObject(font, forKey: key)
        return font
    }

    /// Skia's measured width of `text`.
    static func width(_ text: String, _ font: CTFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        guard ink.width > 0 else { return 0 }
        return Double(ceil(ink.maxX) - floor(ink.minX)) + 2
    }

    /// A letter's step; a lone space has no ink, so it is taken from between two letters.
    static func advance(_ letter: String, _ font: CTFont) -> Double {
        if letter != " " { return width(letter, font) }
        return width("a a", font) - width("aa", font)
    }

    /// Each letter of `text` and where it starts, `tracking` pt apart, and the whole run's width.
    static func tracked(_ text: String, _ font: CTFont, tracking: Double) -> (letters: [String], at: [Double], width: Double) {
        var letters: [String] = []
        var at: [Double] = []
        var x = 0.0
        for letter in text {
            letters.append(String(letter))
            at.append(x)
            x += advance(String(letter), font) + tracking
        }
        return (letters, at, x - tracking)
    }
}
