import CoreImage
import CoreText
import SwiftUI
import UIKit

/// Layout and static art, made once per screen size. The headline is measured with the font's own
/// glyph advances, so every letter's box, block and deboss line up on any device; its mask (crisp
/// in red, line 1 blurred in green, line 2 in blue), each type block's face and the second line are
/// drawn once into images.
struct LetterpressHeadlineLayout: Equatable {
    struct Glyph: Equatable {
        var ch: String
        var x: Double
        var baseline: Double
        var w: Double
    }

    static let headline = ["Made by", "hand."]
    static let second = ["Twelve prints on 600 gsm cotton,", "numbered and signed by the press."]
    static let margin = 28.0
    // The edition's spec list under the second line: three rows.
    static let specsGap = 30.0
    static let specsH = 3 * 38.0
    private static let pad = 14.0

    var width: Double
    var height: Double
    var chromeTop: Double
    var kickerTop: Double
    var size: Double
    var glyphs: [Glyph]
    /// The headline's region (its mask and the deboss overlay cover it).
    var region: CGRect
    /// Each glyph's box, relative to the region: x0, y0, x1, y1.
    var boxes: [[Double]]
    /// Where line 1's boxes end and line 2's begin, relative to the region.
    var split: Double
    var secondX: Double
    var secondBaselines: [Double]
    var secondSize: Double
    var secondWidth: Double
    var specsTop: Double
    var detailsTop: Double
    var buttonTop: Double
    var hintTop: Double

    static func headlineFont(_ size: Double) -> UIFont {
        UIFont(name: "Georgia-Bold", size: size) ?? .boldSystemFont(ofSize: size)
    }

    static func secondFont(_ size: Double) -> UIFont {
        UIFont(name: "Georgia-Italic", size: size) ?? .italicSystemFont(ofSize: size)
    }

    /// Each character's advance in `font`, as Skia's getGlyphWidths gives it.
    static func widths(_ font: UIFont, _ text: String) -> [Double] {
        let ct = font as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &advances, chars.count)
        return advances.map { Double($0.width) }
    }

    init(width: Double, height: Double, top: Double, bottom: Double) {
        let margin = Self.margin
        self.width = width
        self.height = height
        chromeTop = top + 12
        hintTop = height - max(bottom, 12) - 22
        buttonTop = hintTop - 14 - 56
        detailsTop = buttonTop - 18 - 22
        // Fit the headline to the width (measured at 100 pt, then scaled).
        let probe = Self.headlineFont(100)
        let widest = Self.headline.map { Self.widths(probe, $0).reduce(0, +) }.max() ?? 1
        let size = floor(min(92, (width - margin * 2) / widest * 100))
        self.size = size
        let font = Self.headlineFont(size)
        let secondSize = 16.0
        let sfont = Self.secondFont(secondSize)
        // The whole text block, centred between the chrome and the details.
        let blockH = 30 + size * 0.72 + size * 1.0 + 46 + 24 + 8 + Self.specsGap + Self.specsH
        let room0 = chromeTop + 48
        let room1 = detailsTop - 24
        kickerTop = max(room0, room0 + (room1 - room0 - blockH) / 2)
        let b1 = kickerTop + 30 + size * 0.72
        let b2 = b1 + size * 1.0
        var glyphs: [Glyph] = []
        for (row, line) in Self.headline.enumerated() {
            let ws = Self.widths(font, line)
            var x = margin
            for (i, ch) in line.enumerated() {
                if ch != " " { glyphs.append(Glyph(ch: String(ch), x: x, baseline: row == 0 ? b1 : b2, w: ws[i])) }
                x += ws[i]
            }
        }
        self.glyphs = glyphs
        let ry = b1 - size * 0.8 - Self.pad
        let region = CGRect(
            x: margin - Self.pad, y: ry,
            width: width - (margin - Self.pad) * 2, height: b2 + size * 0.24 + Self.pad - ry
        )
        self.region = region
        // Boxes of the two lines meet halfway between line 1's descenders and line 2's ascenders, so a
        // blurred edge always takes its own line's depth.
        let split = (b1 + size * 0.22 + b2 - size * 0.76) / 2
        boxes = glyphs.prefix(LetterpressHeadlineMotion.maxGlyphs).map { g in
            [
                g.x - region.minX,
                (g.baseline == b1 ? region.minY : split) - region.minY,
                g.x + g.w - region.minX,
                (g.baseline == b1 ? split : region.maxY) - region.minY,
            ]
        }
        self.split = split - region.minY
        let s1 = b2 + 46
        secondX = margin
        secondBaselines = [s1, s1 + 24]
        self.secondSize = secondSize
        secondWidth = Self.second.map { Self.widths(sfont, $0).reduce(0, +) }.max() ?? 0
        specsTop = s1 + 24 + 8 + Self.specsGap
    }

    var count: Int { min(glyphs.count, LetterpressHeadlineMotion.maxGlyphs) }

    /// The ink's band round the second line, and the roller's travel.
    var lineTop: Double { secondBaselines[0] - secondSize - 10 }
    var lineBottom: Double { secondBaselines[1] + 10 }
    var rollFrom: Double { secondX - 22 }
    var rollTo: Double { secondX + secondWidth + 22 }

    /// A type block's rectangle for glyph g.
    func blockRect(_ g: Glyph) -> CGRect {
        CGRect(x: g.x - 4, y: g.baseline - size * 0.82, width: g.w + 8, height: size * 1.08)
    }
}

enum LetterpressHeadlineArt {
    @MainActor private static var cache: [String: Any] = [:]

    private static func renderer(_ size: CGSize, scale: Double, opaque: Bool) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = opaque
        return UIGraphicsImageRenderer(size: size, format: format)
    }

    /// Draws `text` with its baseline at `baseline`.
    private static func draw(_ text: String, x: Double, baseline: Double, font: UIFont, color: UIColor) {
        (text as NSString).draw(
            at: CGPoint(x: x, y: baseline - font.ascender),
            withAttributes: [.font: font, .foregroundColor: color]
        )
    }

    /// The headline's mask over its region: red = crisp letters, green / blue = line 1 / 2 blurred.
    @MainActor
    static func mask(_ l: LetterpressHeadlineLayout, scale: Double) -> UIImage {
        let key = "mask-\(l.width)x\(l.height)-\(scale)"
        if let image = cache[key] as? UIImage { return image }
        let region = l.region
        let r = renderer(region.size, scale: scale, opaque: true)
        let font = LetterpressHeadlineLayout.headlineFont(l.size)
        let first = l.glyphs.first?.baseline ?? 0
        // Each line's blur in its own channel, so a blurred edge reaching into the other line never
        // borrows that line's depth. The blur works per channel, so one pass does both.
        let soft = r.image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: region.size))
            for g in l.glyphs {
                let color = g.baseline == first ? UIColor(red: 0, green: 1, blue: 0, alpha: 1) : UIColor(red: 0, green: 0, blue: 1, alpha: 1)
                draw(g.ch, x: g.x - region.minX, baseline: g.baseline - region.minY, font: font, color: color)
            }
        }
        let blurred = blur(soft, sigma: 1.3 * scale)
        let image = r.image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: region.size))
            for g in l.glyphs {
                draw(g.ch, x: g.x - region.minX, baseline: g.baseline - region.minY, font: font, color: UIColor(red: 1, green: 0, blue: 0, alpha: 1))
            }
            blurred?.draw(in: CGRect(origin: .zero, size: region.size), blendMode: .plusLighter, alpha: 1)
        }
        let prepared = image.preparingForDisplay() ?? image
        cache[key] = prepared
        return prepared
    }

    private static func blur(_ image: UIImage, sigma: Double) -> UIImage? {
        guard let cg = image.cgImage else { return nil }
        let input = CIImage(cgImage: cg)
        guard let filter = CIFilter(name: "CIGaussianBlur") else { return nil }
        filter.setValue(input.clampedToExtent(), forKey: kCIInputImageKey)
        filter.setValue(sigma, forKey: kCIInputRadiusKey)
        guard let output = filter.outputImage?.cropped(to: input.extent) else { return nil }
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB) as Any])
        guard let out = context.createCGImage(output, from: input.extent) else { return nil }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
    }

    /// Each type block's face: steel with a bevel and the letter in reverse, in its own rectangle.
    @MainActor
    static func blockFaces(_ l: LetterpressHeadlineLayout, scale: Double) -> [UIImage] {
        let key = "faces-\(l.width)x\(l.height)-\(scale)"
        if let images = cache[key] as? [UIImage] { return images }
        let font = LetterpressHeadlineLayout.headlineFont(l.size)
        let images = l.glyphs.prefix(l.count).map { g -> UIImage in
            let rect = l.blockRect(g)
            let image = renderer(rect.size, scale: scale, opaque: false).image { ctx in
                let c = ctx.cgContext
                let w = rect.width
                let h = rect.height
                let body = UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: 3)
                c.saveGState()
                body.addClip()
                gradient(c, ["#6b6f78", "#43464d", "#2b2d32", "#3a3d43"].map { hexUI($0) }, from: .zero, to: CGPoint(x: w, y: h))
                c.restoreGState()
                // The bevel: a lit top-left edge fading to a dark bottom-right.
                c.saveGState()
                let rim = UIBezierPath(roundedRect: CGRect(x: 0.5, y: 0.5, width: w - 1, height: h - 1), cornerRadius: 2.5)
                c.addPath(rim.cgPath)
                c.setLineWidth(1)
                c.replacePathWithStrokedPath()
                c.clip()
                gradient(c, [UIColor(white: 1, alpha: 0.35), UIColor(white: 1, alpha: 0.04), UIColor(white: 0, alpha: 0.4)],
                         from: .zero, to: CGPoint(x: w, y: h))
                c.restoreGState()
                // The letter in reverse: mirrored round the block's centre.
                c.saveGState()
                c.translateBy(x: w / 2, y: 0)
                c.scaleBy(x: -1, y: 1)
                c.translateBy(x: -w / 2, y: 0)
                let tx = g.x - rect.minX
                let base = g.baseline - rect.minY
                draw(g.ch, x: tx + 0.8, baseline: base + 0.8, font: font, color: hexUI("#1d1e22"))
                draw(g.ch, x: tx, baseline: base, font: font, color: hexUI("#b9bdc4"))
                c.restoreGState()
            }
            return image.preparingForDisplay() ?? image
        }
        cache[key] = images
        return images
    }

    /// The second line in ink, full width, over its band (lineTop … lineBottom).
    @MainActor
    static func secondLine(_ l: LetterpressHeadlineLayout, scale: Double) -> UIImage {
        let key = "second-\(l.width)x\(l.height)-\(scale)"
        if let image = cache[key] as? UIImage { return image }
        let top = l.lineTop
        let size = CGSize(width: l.width, height: l.lineBottom - top)
        let font = LetterpressHeadlineLayout.secondFont(l.secondSize)
        let image = renderer(size, scale: scale, opaque: false).image { _ in
            for (i, line) in LetterpressHeadlineLayout.second.enumerated() {
                draw(line, x: l.secondX, baseline: l.secondBaselines[i] - top, font: font, color: hexUI("#2a3140"))
            }
        }
        let prepared = image.preparingForDisplay() ?? image
        cache[key] = prepared
        return prepared
    }

    private static func gradient(_ c: CGContext, _ colors: [UIColor], from: CGPoint, to: CGPoint) {
        let cgColors = colors.map(\.cgColor) as CFArray
        guard let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: cgColors, locations: nil) else { return }
        c.drawLinearGradient(g, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    static func hexUI(_ s: String) -> UIColor {
        let v = UInt32(s.dropFirst(), radix: 16) ?? 0
        return UIColor(
            red: CGFloat((v >> 16) & 0xFF) / 255,
            green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255,
            alpha: 1
        )
    }
}
