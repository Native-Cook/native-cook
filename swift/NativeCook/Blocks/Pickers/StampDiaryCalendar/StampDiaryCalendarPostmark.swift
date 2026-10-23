import CoreGraphics
import CoreImage
import CoreText
import Foundation

/// Draws a postmark once per day into a small CPU image (postmark.ts): a double ring with
/// "MAY . 2026" and "STAMP DIARY" around it, the day in the middle and four wavy cancellation lines
/// to its left. Only the alpha is used; strokes carry a baked soft edge (a Gaussian blur of sigma
/// 2.2 px) so the shader can thin them (fresh, crisp ink) or thicken them (ink bleeding into the
/// paper) by moving one threshold.
enum StampDiaryCalendarPostmark {
    typealias P = StampDiaryCalendarPostmarkSpec

    private static var cache: [String: CGImage] = [:]
    private static let ciContext = CIContext(options: [.useSoftwareRenderer: false])

    static func image(day: Int, month: Int, year: Int) -> CGImage? {
        let key = "\(year)-\(month)-\(day)"
        if let hit = cache[key] { return hit }
        guard let image = make(day: day, month: month, year: year) else { return nil }
        cache[key] = image
        return image
    }

    private static func glyphWidth(_ font: CTFont, _ text: String) -> Double {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        guard CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count) else { return 0 }
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        return advances.reduce(0) { $0 + Double($1.width) }
    }

    /// One glyph run at the origin of the current (y-down) space, baseline `y`, left edge `x`.
    private static func drawText(_ ctx: CGContext, _ text: String, x: Double, y: Double, font: CTFont) {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        guard CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count) else { return }
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        var positions = [CGPoint]()
        var along = 0.0
        for a in advances {
            positions.append(CGPoint(x: along, y: 0))
            along += Double(a.width)
        }
        ctx.saveGState()
        // The context is y-down; glyphs are y-up, so flip around the baseline.
        ctx.translateBy(x: x, y: y)
        ctx.scaleBy(x: 1, y: -1)
        CTFontDrawGlyphs(font, glyphs, positions, chars.count, ctx)
        ctx.restoreGState()
    }

    private static func arcText(_ ctx: CGContext, _ text: String, radius: Double, centerAngle: Double, font: CTFont, bottom: Bool) {
        let chars = text.map { String($0) }
        let widths = chars.map { glyphWidth(font, $0) }
        let tracking = 2.0
        let total = widths.reduce(-tracking) { $0 + $1 + tracking }
        // Arc length -> angle. Top text runs clockwise, bottom text counter-clockwise, both upright.
        var along = -total / 2
        for (i, ch) in chars.enumerated() {
            let mid = along + widths[i] / 2
            let angle = centerAngle + (bottom ? -1 : 1) * (mid / radius)
            ctx.saveGState()
            ctx.translateBy(x: P.ringX + cos(angle) * radius, y: P.ringY + sin(angle) * radius)
            ctx.rotate(by: angle + (bottom ? -Double.pi / 2 : Double.pi / 2))
            let capHalf = CTFontGetSize(font) * 0.36
            drawText(ctx, ch, x: -widths[i] / 2, y: capHalf, font: font)
            ctx.restoreGState()
            along += widths[i] + tracking
        }
    }

    private static func make(day: Int, month: Int, year: Int) -> CGImage? {
        let space = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: nil, width: P.width, height: P.height, bitsPerComponent: 8, bytesPerRow: 0,
            space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        // Skia's space: origin top-left, y down.
        ctx.translateBy(x: 0, y: CGFloat(P.height))
        ctx.scaleBy(x: 1, y: -1)
        ctx.setAllowsAntialiasing(true)
        ctx.setShouldAntialias(true)
        ctx.setFillColor(CGColor(gray: 0, alpha: 1))
        ctx.setStrokeColor(CGColor(gray: 0, alpha: 1))
        ctx.setLineCap(.round)

        ctx.setLineWidth(P.outerStroke)
        ctx.strokeEllipse(in: CGRect(x: P.ringX - P.outer, y: P.ringY - P.outer, width: 2 * P.outer, height: 2 * P.outer))
        ctx.setLineWidth(P.innerStroke)
        ctx.strokeEllipse(in: CGRect(x: P.ringX - P.inner, y: P.ringY - P.inner, width: 2 * P.inner, height: 2 * P.inner))

        let arcFont = CTFontCreateWithName(P.fontName as CFString, P.arcFont, nil)
        let textRadius = (P.outer + P.inner) / 2
        let top = String(StampDiaryCalendarTheme.monthNames[month - 1].prefix(3)).uppercased() + " \u{00B7} " + String(year)
        arcText(ctx, top, radius: textRadius, centerAngle: -Double.pi / 2, font: arcFont, bottom: false)
        arcText(ctx, "STAMP DIARY", radius: textRadius, centerAngle: Double.pi / 2, font: arcFont, bottom: true)

        let dayFont = CTFontCreateWithName(P.fontName as CFString, P.dayFont, nil)
        let label = String(day)
        drawText(ctx, label, x: P.ringX - glyphWidth(dayFont, label) / 2, y: P.ringY + P.dayFont * 0.36, font: dayFont)

        ctx.setLineWidth(P.waveStroke)
        for i in 0..<P.waves {
            let y = P.ringY + (Double(i) - Double(P.waves - 1) / 2) * P.waveGap
            ctx.beginPath()
            ctx.move(to: CGPoint(x: P.waveFrom, y: y))
            var x = P.waveFrom
            while x <= P.waveTo {
                ctx.addLine(to: CGPoint(x: x, y: y + sin(((x - P.waveFrom) / P.waveLength) * 2 * Double.pi) * P.waveAmp))
                x += 4
            }
            ctx.strokePath()
        }
        guard let sharp = ctx.makeImage() else { return nil }

        // The soft edge baked into the alpha.
        let extent = CGRect(x: 0, y: 0, width: P.width, height: P.height)
        let blurred = CIImage(cgImage: sharp)
            .clampedToExtent()
            .applyingGaussianBlur(sigma: P.softness)
            .cropped(to: extent)
        return ciContext.createCGImage(blurred, from: extent, format: .RGBA8, colorSpace: space) ?? sharp
    }
}
