import CoreImage
import CoreText
import SwiftUI
import UIKit

/// Where everything sits, from the screen size and its safe area (as in React Native).
struct WaxSealInviteLayout: Equatable {
    static let sealPad = 10.0

    var width: Double
    var height: Double
    var headerTop: Double
    /// The envelope's frame and the flap's depth.
    var env: CGRect
    var flap: Double
    var seal: CGPoint
    var sealR: Double
    /// The card: size, left edge, and its top inside the envelope, clear above it and settled.
    var cardW: Double
    var cardH: Double
    var cardX: Double
    var insideTop: Double
    var clearTop: Double
    var finalTop: Double
    var chipsTop: Double
    var hintTop: Double

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        let w = min(width - 32, 380)
        let h = (w * 0.7).rounded()
        let cardH = h - 14
        let hint = height - max(bottom, 16) - 34
        // The envelope sits low, with room above it for the card to rise clear and the detail chips
        // and the hint below it.
        let y = max(top + 74 + cardH, min(height * 0.5, hint - 92 - h)).rounded()
        let flap = (h * 0.6).rounded()
        let x = (width - w) / 2
        let cw = w - 26
        headerTop = top + 22
        env = CGRect(x: x, y: y, width: w, height: h)
        self.flap = flap
        seal = CGPoint(x: width / 2, y: y + flap - 6)
        sealR = min(42, w * 0.115).rounded()
        cardW = cw
        self.cardH = cardH
        cardX = (width - cw) / 2
        insideTop = y + 8
        clearTop = y - cardH - 10
        finalTop = min(y + 6 - cardH / 2, y - flap + 8)
        chipsTop = y + h + 28
        hintTop = max(hint, y + h + 68)
    }

    var sealSide: Double { (sealR + Self.sealPad) * 2 }

    /// The card's top and scale for a pose's `card` (0 inside, 1 clear above, 2 settled in front).
    func cardPlace(_ c: Double) -> (top: Double, scale: Double) {
        let top = c <= 1 ? insideTop + (clearTop - insideTop) * c : clearTop + (finalTop - clearTop) * (c - 1)
        return (top, c <= 1 ? 1 : 1 + 0.1 * (c - 1))
    }
}

enum WaxSealInviteArt {
    @MainActor private static var cache: [String: UIImage] = [:]

    static let cream = (0.93, 0.886, 0.81)
    static let creamFront = (0.953, 0.914, 0.845)
    static let creamFlap = (0.972, 0.94, 0.88)
    static let liner = (0.43, 0.09, 0.11)

    /// The flap's outline from the envelope's top edge (hinge at y = 0).
    static func flapPath(w: Double, fh: Double) -> Path {
        var p = Path()
        p.move(to: .zero)
        p.addLine(to: CGPoint(x: w, y: 0))
        p.addLine(to: CGPoint(x: w / 2 + 16, y: fh - 8))
        p.addQuadCurve(to: CGPoint(x: w / 2 - 16, y: fh - 8), control: CGPoint(x: w / 2, y: fh + 2))
        p.closeSubpath()
        return p
    }

    static func frontPath(w: Double, h: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 2))
        p.addLine(to: CGPoint(x: w / 2, y: h * 0.5))
        p.addLine(to: CGPoint(x: w, y: 2))
        p.addLine(to: CGPoint(x: w, y: h - 10))
        p.addQuadCurve(to: CGPoint(x: w - 10, y: h), control: CGPoint(x: w, y: h))
        p.addLine(to: CGPoint(x: 10, y: h))
        p.addQuadCurve(to: CGPoint(x: 0, y: h - 10), control: CGPoint(x: 0, y: h))
        p.closeSubpath()
        return p
    }

    // MARK: Seal mask

    /// The monogram's mask (green = blurred height), centred in a square of side 2 (R + pad).
    @MainActor
    static func sealMask(R: Double, scale: Double) -> UIImage {
        let key = "mask-\(R)-\(scale)"
        if let image = cache[key] { return image }
        let side = (R + WaxSealInviteLayout.sealPad) * 2
        let size = CGSize(width: side, height: side)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let crisp = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let c = ctx.cgContext
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            let green = UIColor(red: 0, green: 1, blue: 0, alpha: 1)
            c.translateBy(x: side / 2, y: side / 2)
            c.setStrokeColor(green.cgColor)
            c.setLineWidth(R * 0.035)
            c.strokeEllipse(in: CGRect(x: -R * 0.6, y: -R * 0.6, width: R * 1.2, height: R * 1.2))
            let letters = UIFont(name: "Georgia-Bold", size: R * 0.64) ?? .boldSystemFont(ofSize: R * 0.64)
            let amp = UIFont(name: "Georgia-Italic", size: R * 0.4) ?? .italicSystemFont(ofSize: R * 0.4)
            let la: [NSAttributedString.Key: Any] = [.font: letters, .foregroundColor: green]
            let aa: [NSAttributedString.Key: Any] = [.font: amp, .foregroundColor: green]
            let lw = ("L" as NSString).size(withAttributes: la).width
            let tw = ("T" as NSString).size(withAttributes: la).width
            let aw = ("&" as NSString).size(withAttributes: aa).width
            let gap = R * 0.02
            var x = -(lw + aw + tw + gap * 2) / 2
            let base = R * 0.2
            UIGraphicsPushContext(c)
            ("L" as NSString).draw(at: CGPoint(x: x, y: base - letters.ascender), withAttributes: la)
            x += lw + gap
            ("&" as NSString).draw(at: CGPoint(x: x, y: base - R * 0.02 - amp.ascender), withAttributes: aa)
            x += aw + gap
            ("T" as NSString).draw(at: CGPoint(x: x, y: base - letters.ascender), withAttributes: la)
            UIGraphicsPopContext()
        }
        // React Native draws the whole mask blurred 1.2 pt.
        let image = blur(crisp, sigma: 1.2 * scale) ?? crisp
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

    // MARK: Card

    private static func hexUI(_ v: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: a)
    }

    private static func advances(_ font: UIFont, _ text: String) -> [Double] {
        let ct = font as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &adv, chars.count)
        return adv.map { Double($0.width) }
    }

    /// Text centred at x with its baseline at y, with extra tracking between the letters.
    private static func centred(_ text: String, x: Double, y: Double, font: UIFont, color: UIColor, tracking: Double = 0) {
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let ws = advances(font, text)
        let total = ws.reduce(0, +) + tracking * Double(max(0, ws.count - 1))
        var cx = x - total / 2
        if tracking == 0 {
            (text as NSString).draw(at: CGPoint(x: cx, y: y - font.ascender), withAttributes: attrs)
            return
        }
        for (i, ch) in text.utf16.enumerated() {
            let s = String(utf16CodeUnits: [ch], count: 1)
            (s as NSString).draw(at: CGPoint(x: cx, y: y - font.ascender), withAttributes: attrs)
            cx += ws[i] + tracking
        }
    }

    /// The invitation card, in its own coordinates (0, 0 … w, h). The RSVP buttons are views.
    @MainActor
    static func card(w: Double, h: Double, scale: Double) -> UIImage {
        let key = "card-\(w)x\(h)-\(scale)"
        if let image = cache[key] { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
            let c = ctx.cgContext
            c.saveGState()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: 8).addClip()
            let colors = [hexUI(0xFDF8EF).cgColor, hexUI(0xF8F0E2).cgColor, hexUI(0xF3E8D6).cgColor] as CFArray
            if let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.55, 1]) {
                c.drawLinearGradient(g, start: .zero, end: CGPoint(x: w, y: h), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            }
            c.restoreGState()
            let gold = hexUI(0xB48F58)
            gold.setStroke()
            let frame = UIBezierPath(roundedRect: CGRect(x: 8, y: 8, width: w - 16, height: h - 16), cornerRadius: 4)
            frame.lineWidth = 0.8
            frame.stroke()
            let ink = hexUI(0x4A2A22)
            let red = hexUI(0x7C1A20)
            let kicker = UIFont(name: "Georgia-Bold", size: 8.5) ?? .boldSystemFont(ofSize: 8.5)
            let titleSize = min(31, w * 0.095)
            let title = UIFont(name: "Georgia-BoldItalic", size: titleSize) ?? .italicSystemFont(ofSize: titleSize)
            let body = UIFont(name: "Georgia", size: 12.5) ?? .systemFont(ofSize: 12.5)
            let strong = UIFont(name: "Georgia-Bold", size: 12.5) ?? .boldSystemFont(ofSize: 12.5)
            let mid = w / 2
            centred("TOGETHER WITH THEIR FAMILIES", x: mid, y: 30, font: kicker, color: ink, tracking: 1.6)
            centred("Lena & Theo", x: mid, y: 66, font: title, color: red)
            centred("invite you to celebrate their wedding", x: mid, y: 87, font: body, color: ink)
            // A small ornament: two lines and a diamond.
            let lines = UIBezierPath()
            lines.move(to: CGPoint(x: mid - 46, y: 101))
            lines.addLine(to: CGPoint(x: mid - 9, y: 101))
            lines.move(to: CGPoint(x: mid + 9, y: 101))
            lines.addLine(to: CGPoint(x: mid + 46, y: 101))
            lines.lineWidth = 0.9
            lines.stroke()
            let diamond = UIBezierPath()
            diamond.move(to: CGPoint(x: mid, y: 97))
            diamond.addLine(to: CGPoint(x: mid + 4, y: 101))
            diamond.addLine(to: CGPoint(x: mid, y: 105))
            diamond.addLine(to: CGPoint(x: mid - 4, y: 101))
            diamond.close()
            gold.setFill()
            diamond.fill()
            centred("Saturday, 14 June 2027 · 4 pm", x: mid, y: 124, font: strong, color: ink)
            centred("Villa Amara · Lake Orta", x: mid, y: 142, font: body, color: ink)
        }
        let prepared = image.preparingForDisplay() ?? image
        cache[key] = prepared
        return prepared
    }
}
