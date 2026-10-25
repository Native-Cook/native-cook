import CoreImage
import SwiftUI
import UIKit

/// Where everything sits, from the screen size and its safe area (as in React Native).
struct FoilStampButtonLayout: Equatable {
    var width: Double
    var height: Double
    var headerTop: Double
    var card: CGRect
    var seal: CGPoint
    var sealR: Double
    var rowsTop: Double
    var button: CGPoint
    var buttonR: Double
    var hintTop: Double

    static let maskPad = 14.0
    private static let header = 84.0
    private static let rows = 124.0
    private static let buttonRadius = 52.0
    private static let buttonBlock = buttonRadius * 2 + 30

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        headerTop = top + 14
        let bottomEdge = height - max(bottom, 12) - 10
        let room = bottomEdge - (headerTop + Self.header)
        // Gaps: card, rows and the stamp share the room; the card takes what is left, within limits.
        let gap = room > 560 ? 22.0 : 14.0
        let cardH = max(196, min(330, room - Self.rows - Self.buttonBlock - gap * 3))
        let spare = max(0, room - cardH - Self.rows - Self.buttonBlock - gap * 3)
        let cardY = headerTop + Self.header + gap + spare * 0.35
        let cardW = min(width - 48, 400)
        card = CGRect(x: (width - cardW) / 2, y: cardY, width: cardW, height: cardH)
        sealR = min(70, cardH * 0.28)
        seal = CGPoint(x: width / 2, y: cardY + cardH * 0.47)
        rowsTop = cardY + cardH + gap + spare * 0.2
        let buttonY = rowsTop + Self.rows + gap + spare * 0.3 + Self.buttonRadius
        button = CGPoint(x: width / 2, y: buttonY)
        buttonR = Self.buttonRadius
        hintTop = buttonY + Self.buttonRadius + 12
    }

    /// The side of the seal's mask, in points.
    var maskSide: Double { (sealR + Self.maskPad) * 2 }
}

enum FoilStampButtonArt {
    @MainActor private static var cache: [String: UIImage] = [:]

    /// The seal's mask: red = crisp monogram, green = blurred 1.5 pt (the height map). Square,
    /// R + pad from the centre to each side, on black. Made once per radius.
    @MainActor
    static func sealMask(radius R: Double, scale: Double) -> UIImage {
        let key = "\(R)-\(scale)"
        if let image = cache[key] { return image }
        let side = (R + FoilStampButtonLayout.maskPad) * 2
        let size = CGSize(width: side, height: side)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)

        let soft = renderer.image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            drawMonogram(ctx.cgContext, color: UIColor(red: 0, green: 1, blue: 0, alpha: 1), R: R, side: side)
        }
        let blurred = blur(soft, sigma: 1.5 * scale)

        let image = renderer.image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            drawMonogram(ctx.cgContext, color: UIColor(red: 1, green: 0, blue: 0, alpha: 1), R: R, side: side)
            // The blurred copy is added on top, so it fills green without touching red.
            if let blurred {
                blurred.draw(in: CGRect(origin: .zero, size: size), blendMode: .plusLighter, alpha: 1)
            }
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

    /// The monogram, centred in the square: a rope border, a beaded ring, a thin ring, "E" & "V".
    private static func drawMonogram(_ c: CGContext, color: UIColor, R: Double, side: Double) {
        c.saveGState()
        c.translateBy(x: side / 2, y: side / 2)
        c.setFillColor(color.cgColor)
        c.setStrokeColor(color.cgColor)

        c.setLineWidth(R * 0.045)
        c.strokeEllipse(in: CGRect(x: -R * 0.94, y: -R * 0.94, width: R * 1.88, height: R * 1.88))
        c.setLineWidth(R * 0.016)
        c.strokeEllipse(in: CGRect(x: -R * 0.7, y: -R * 0.7, width: R * 1.4, height: R * 1.4))
        // Beads between the two rings.
        let beads = 36
        for i in 0..<beads {
            let a = Double(i) / Double(beads) * 2 * .pi
            let br = R * 0.028
            c.fillEllipse(in: CGRect(x: cos(a) * R * 0.82 - br, y: sin(a) * R * 0.82 - br, width: br * 2, height: br * 2))
        }
        // Two small four-point stars above and below the letters.
        for y in [-R * 0.5, R * 0.52] {
            let s = R * 0.07
            let star = CGMutablePath()
            star.move(to: CGPoint(x: 0, y: y - s))
            star.addQuadCurve(to: CGPoint(x: s, y: y), control: CGPoint(x: s * 0.18, y: y - s * 0.18))
            star.addQuadCurve(to: CGPoint(x: 0, y: y + s), control: CGPoint(x: s * 0.18, y: y + s * 0.18))
            star.addQuadCurve(to: CGPoint(x: -s, y: y), control: CGPoint(x: -s * 0.18, y: y + s * 0.18))
            star.addQuadCurve(to: CGPoint(x: 0, y: y - s), control: CGPoint(x: -s * 0.18, y: y - s * 0.18))
            star.closeSubpath()
            c.addPath(star)
            c.fillPath()
        }

        let letters = UIFont(name: "Georgia-Bold", size: R * 0.6) ?? .boldSystemFont(ofSize: R * 0.6)
        let amp = UIFont(name: "Georgia-Italic", size: R * 0.34) ?? .italicSystemFont(ofSize: R * 0.34)
        let la: [NSAttributedString.Key: Any] = [.font: letters, .foregroundColor: color]
        let aa: [NSAttributedString.Key: Any] = [.font: amp, .foregroundColor: color]
        let ew = ("E" as NSString).size(withAttributes: la).width
        let vw = ("V" as NSString).size(withAttributes: la).width
        let aw = ("&" as NSString).size(withAttributes: aa).width
        let base = R * 0.2
        let gap = R * 0.02
        let total = ew + aw + vw + gap * 2
        var x = -total / 2
        UIGraphicsPushContext(c)
        // NSString draws from the top of the line; Skia's drawText from the baseline.
        ("E" as NSString).draw(at: CGPoint(x: x, y: base - letters.ascender), withAttributes: la)
        x += ew + gap
        ("&" as NSString).draw(at: CGPoint(x: x, y: base - R * 0.04 - amp.ascender), withAttributes: aa)
        x += aw + gap
        ("V" as NSString).draw(at: CGPoint(x: x, y: base - letters.ascender), withAttributes: la)
        UIGraphicsPopContext()
        c.restoreGState()
    }

    /// A check mark about 22 pt wide, centred on (cx, cy), drawn as one stroke left to right.
    static func checkPath(cx: Double, cy: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: cx - 10, y: cy + 0.5))
        p.addCurve(to: CGPoint(x: cx - 3.5, y: cy + 7.5), control1: CGPoint(x: cx - 7, y: cy + 3), control2: CGPoint(x: cx - 5, y: cy + 5.5))
        p.addCurve(to: CGPoint(x: cx + 11, y: cy - 9), control1: CGPoint(x: cx + 1, y: cy + 0.5), control2: CGPoint(x: cx + 5.5, y: cy - 5))
        return p
    }
}
