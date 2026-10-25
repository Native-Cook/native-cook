import CoreImage
import SwiftUI
import UIKit

/*
 Zipper Unlock — the metal and leather, drawn once into bitmaps (the slider, its pull tab and the
 tab's soft shadow, the bottom stop, the leather patch), and the teeth, rebuilt as three batched
 paths per frame. A port of zipperArt.ts; Skia's blur masks are Core Image Gaussian blurs here.
*/

enum ZipperUnlockArt {
    typealias M = ZipperUnlockMotion

    /// A piece of art and the rectangle it covers around its own origin (pt).
    struct Piece {
        let image: Image
        let rect: CGRect
    }

    struct Pieces {
        let slider: Piece
        let tab: Piece
        let tabShadow: Piece
        let stop: Piece
        let patch: Piece
    }

    private static var made: [Double: Pieces] = [:]

    @MainActor
    static func make(scale: Double) -> Pieces {
        if let set = made[scale] { return set }
        let set = Pieces(
            slider: piece(CGRect(x: -40, y: -40, width: 80, height: 90), scale, drawSlider),
            tab: piece(CGRect(x: -30, y: -12, width: 60, height: M.tabLen + 40), scale, drawTab),
            tabShadow: piece(CGRect(x: -30, y: -20, width: 60, height: M.tabLen + 40), scale) { c, s in
                blurred(c, tabPath(), color: hex(0x040816), sigma: 4, scale: s, evenOdd: true)
            },
            stop: piece(CGRect(x: -20, y: -20, width: 40, height: 40), scale, drawStop),
            patch: piece(CGRect(x: -136, y: -80, width: 272, height: 160), scale, drawPatch)
        )
        made[scale] = set
        return set
    }

    // MARK: Helpers

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> UIColor {
        rgba(Double((v >> 16) & 0xff), Double((v >> 8) & 0xff), Double(v & 0xff), a)
    }

    private static func gradient(_ colors: [UIColor], _ locations: [CGFloat]) -> CGGradient? {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: locations)
    }

    private static let brassColors = [hex(0xF6E2A6), hex(0xD3A453), hex(0x8C6326), hex(0xE2BE72)]

    private static func piece(_ rect: CGRect, _ scale: Double, _ draw: (CGContext, Double) -> Void) -> Piece {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let ui = UIGraphicsImageRenderer(size: rect.size, format: format).image { ctx in
            let c = ctx.cgContext
            c.translateBy(x: -rect.minX, y: -rect.minY)
            draw(c, scale)
        }
        return Piece(image: Image(uiImage: ui), rect: rect)
    }

    private static let ci = CIContext(options: [.useSoftwareRenderer: false])

    /// A shape filled with a colour and blurred by σ (in points), drawn into the context.
    private static func blurred(_ c: CGContext, _ path: CGPath, color: UIColor, sigma: Double, scale: Double, evenOdd: Bool = false) {
        let pad = ceil(sigma * 3 + 2)
        let box = path.boundingBox.insetBy(dx: -pad, dy: -pad).integral
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let shape = UIGraphicsImageRenderer(size: box.size, format: format).image { ctx in
            let g = ctx.cgContext
            g.translateBy(x: -box.minX, y: -box.minY)
            g.addPath(path)
            g.setFillColor(color.cgColor)
            g.fillPath(using: evenOdd ? .evenOdd : .winding)
        }
        guard let cg = shape.cgImage else { return }
        let input = CIImage(cgImage: cg)
        guard let filter = CIFilter(name: "CIGaussianBlur") else { return }
        filter.setValue(input.clampedToExtent(), forKey: kCIInputImageKey)
        filter.setValue(sigma * scale, forKey: kCIInputRadiusKey)
        // Clamping keeps the edges from darkening; the shape is padded, so its border is clear.
        guard let out = filter.outputImage?.cropped(to: input.extent), let result = ci.createCGImage(out, from: input.extent) else { return }
        UIImage(cgImage: result, scale: scale, orientation: .up).draw(in: box)
    }

    private static func brass(_ c: CGContext, _ path: CGPath, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, evenOdd: Bool = false) {
        c.saveGState()
        c.addPath(path)
        if evenOdd { c.clip(using: .evenOdd) } else { c.clip() }
        if let g = gradient(brassColors, [0, 0.38, 0.72, 1]) {
            c.drawLinearGradient(g, start: CGPoint(x: x0, y: y0), end: CGPoint(x: x1, y: y1), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        c.restoreGState()
    }

    private static func edgeLines(_ c: CGContext, _ path: CGPath) {
        c.setLineWidth(0.8)
        c.setStrokeColor(rgba(70, 44, 12, 0.65).cgColor)
        c.addPath(path)
        c.strokePath()
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
        CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil)
    }

    // MARK: Pieces

    /// The slider, centred on (0, 0): 32 pt wide at the top, 21 at the bottom, 36 tall.
    private static func drawSlider(_ c: CGContext, _ scale: Double) {
        blurred(c, rr(-15, -14, 32, 38, 9), color: rgba(5, 10, 24, 0.55), sigma: 5, scale: scale)
        blurred(c, rr(-15.5, -17, 31, 35, 8), color: rgba(5, 10, 24, 0.6), sigma: 1.5, scale: scale)
        let body = CGMutablePath()
        body.move(to: CGPoint(x: -16, y: -12))
        body.addCurve(to: CGPoint(x: -9, y: -18), control1: CGPoint(x: -16, y: -17), control2: CGPoint(x: -13, y: -18))
        body.addLine(to: CGPoint(x: 9, y: -18))
        body.addCurve(to: CGPoint(x: 16, y: -12), control1: CGPoint(x: 13, y: -18), control2: CGPoint(x: 16, y: -17))
        body.addLine(to: CGPoint(x: 10.5, y: 13))
        body.addCurve(to: CGPoint(x: 4, y: 18), control1: CGPoint(x: 9.5, y: 17), control2: CGPoint(x: 7, y: 18))
        body.addLine(to: CGPoint(x: -4, y: 18))
        body.addCurve(to: CGPoint(x: -10.5, y: 13), control1: CGPoint(x: -7, y: 18), control2: CGPoint(x: -9.5, y: 17))
        body.closeSubpath()
        brass(c, body, -16, -18, 14, 18)
        edgeLines(c, body)
        // Raised centre ridge and the bridge the tab hangs from.
        let ridge = rr(-3.2, -15, 6.4, 24, 3)
        brass(c, ridge, 3, -15, -3, 9)
        edgeLines(c, ridge)
        let bridge = rr(-5.5, 8, 11, 7, 3.5)
        brass(c, bridge, -5, 8, 5, 15)
        edgeLines(c, bridge)
        // Top edge highlight.
        c.setLineWidth(1)
        c.setLineCap(.round)
        c.setStrokeColor(rgba(255, 248, 222, 0.85).cgColor)
        c.move(to: CGPoint(x: -11, y: -16.6))
        c.addLine(to: CGPoint(x: 11, y: -16.6))
        c.strokePath()
    }

    /// The tab's outline, hanging from its pivot at (0, 0) down +y, with the ring hole cut out
    /// (even-odd).
    static func tabPath() -> CGPath {
        let l = M.tabLen
        let p = CGMutablePath()
        p.move(to: CGPoint(x: -7, y: -4))
        p.addCurve(to: CGPoint(x: 7, y: -4), control1: CGPoint(x: -7, y: -7), control2: CGPoint(x: 7, y: -7))
        p.addLine(to: CGPoint(x: 10, y: l - 12))
        p.addCurve(to: CGPoint(x: 0, y: l), control1: CGPoint(x: 10, y: l - 4), control2: CGPoint(x: 6, y: l))
        p.addCurve(to: CGPoint(x: -10, y: l - 12), control1: CGPoint(x: -6, y: l), control2: CGPoint(x: -10, y: l - 4))
        p.closeSubpath()
        p.addPath(rr(-4.5, l - 15, 9, 8, 3.5))
        p.addPath(rr(-3, -2.5, 6, 4, 2))
        return p
    }

    /// The pull tab with its split ring, pivot at (0, 0).
    private static func drawTab(_ c: CGContext, _ scale: Double) {
        let l = M.tabLen
        let tab = tabPath()
        brass(c, tab, -10, 0, 12, l, evenOdd: true)
        edgeLines(c, tab)
        // A pressed border on the tab's face.
        c.setLineWidth(1)
        c.setStrokeColor(rgba(120, 80, 24, 0.55).cgColor)
        c.move(to: CGPoint(x: -4.5, y: 3))
        c.addLine(to: CGPoint(x: -7, y: l - 18))
        c.move(to: CGPoint(x: 4.5, y: 3))
        c.addLine(to: CGPoint(x: 7, y: l - 18))
        c.strokePath()
        c.setStrokeColor(rgba(255, 245, 210, 0.6).cgColor)
        c.move(to: CGPoint(x: -6, y: -3.5))
        c.addLine(to: CGPoint(x: 6, y: -3.5))
        c.strokePath()
        // The ring through the hole.
        c.saveGState()
        c.addEllipse(in: CGRect(x: -9.5, y: l + 4 - 9.5, width: 19, height: 19))
        c.setLineWidth(3.2)
        c.replacePathWithStrokedPath()
        c.clip()
        if let g = gradient(brassColors, [0, 0.35, 0.7, 1]) {
            c.drawLinearGradient(g, start: CGPoint(x: -10, y: l - 4), end: CGPoint(x: 10, y: l + 18), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        c.restoreGState()
        let arc = UIBezierPath(arcCenter: CGPoint(x: 0, y: l + 4), radius: 9.5, startAngle: 200 * .pi / 180, endAngle: 280 * .pi / 180, clockwise: true)
        c.setLineWidth(0.8)
        c.setStrokeColor(rgba(255, 248, 222, 0.7).cgColor)
        c.addPath(arc.cgPath)
        c.strokePath()
    }

    /// The bottom stop: a small box, centred on (0, 0).
    private static func drawStop(_ c: CGContext, _ scale: Double) {
        blurred(c, rr(-8, -5, 17, 13, 3), color: rgba(5, 10, 24, 0.5), sigma: 2, scale: scale)
        let box = rr(-8.5, -6, 17, 12, 2.5)
        brass(c, box, -8, -6, 8, 6)
        edgeLines(c, box)
    }

    /// Letters spaced apart and centred on x, baseline at y.
    private static func spaced(_ text: String, _ font: UIFont, _ x: Double, _ y: Double, _ spacing: Double, _ color: UIColor) {
        let s = NSAttributedString(string: text, attributes: [.font: font, .kern: spacing, .foregroundColor: color])
        let total = s.size().width - spacing
        s.draw(at: CGPoint(x: x - total / 2, y: y - font.ascender))
    }

    /// The leather patch, 136 × 80 pt, centred on (0, 0).
    private static func drawPatch(_ c: CGContext, _ scale: Double) {
        let w = 136.0
        let h = 80.0
        let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        let shape = CGPath(roundedRect: rect, cornerWidth: 10, cornerHeight: 10, transform: nil)
        // Two shadows: soft ambient and tight contact.
        blurred(c, rr(-w / 2 + 2, -h / 2 + 8, w, h, 10), color: rgba(4, 8, 20, 0.45), sigma: 9, scale: scale)
        blurred(c, rr(-w / 2, -h / 2 + 1.5, w, h, 10), color: rgba(4, 8, 20, 0.5), sigma: 1.4, scale: scale)
        c.saveGState()
        c.addPath(shape)
        c.clip()
        if let g = gradient([hex(0xC48D5E), hex(0x9C6438), hex(0x7A4826), hex(0x93603A)], [0, 0.4, 0.8, 1]) {
            c.drawLinearGradient(g, start: CGPoint(x: -w / 2, y: -h / 2), end: CGPoint(x: w / 2, y: h / 2), options: [])
        }
        // Leather pores: seeded specks, darker and lighter.
        var seed = 7.0
        func rnd() -> Double {
            seed = (seed * 16807).truncatingRemainder(dividingBy: 2_147_483_647)
            return seed / 2_147_483_647
        }
        for _ in 0..<220 {
            let x = -w / 2 + rnd() * w
            let y = -h / 2 + rnd() * h
            let dark = rnd() > 0.5
            let r = 0.5 + rnd() * 0.9
            c.setFillColor((dark ? rgba(60, 30, 12, 0.18) : rgba(255, 220, 180, 0.10)).cgColor)
            c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        // Worn, lighter rim (a 5 pt stroke blurred by σ 3, as three soft strokes).
        for (lw, a) in [(11.0, 0.03), (8.0, 0.035), (5.0, 0.04)] {
            c.setLineWidth(lw)
            c.setStrokeColor(rgba(255, 214, 170, a).cgColor)
            c.addPath(shape)
            c.strokePath()
        }
        c.restoreGState()
        // Stitched border.
        c.saveGState()
        c.setLineWidth(1.4)
        c.setLineCap(.round)
        c.setLineDash(phase: 0, lengths: [4, 3])
        c.setStrokeColor(hex(0xE9C98E).cgColor)
        c.addPath(rr(-w / 2 + 6, -h / 2 + 6, w - 12, h - 12, 6))
        c.strokePath()
        c.restoreGState()
        // Debossed lettering: a light lower edge under a dark fill.
        let title = UIFont.systemFont(ofSize: 24, weight: .heavy)
        let small = UIFont.systemFont(ofSize: 8.5, weight: .bold)
        let lightInk = rgba(255, 222, 184, 0.35)
        let darkInk = hex(0x5A3216)
        spaced("POCKET", title, 0, 7.8, 3, lightInk)
        spaced("POCKET", title, 0, 6.6, 3, darkInk)
        spaced("NOTES FOR LATER", small, 0, 25, 1.6, lightInk)
        spaced("NOTES FOR LATER", small, 0, 24, 1.6, darkInk)
        c.setFillColor(rgba(70, 36, 14, 0.6).cgColor)
        c.fill(CGRect(x: -30, y: 12.5, width: 60, height: 1))
    }

    // MARK: Teeth

    /// Every tooth of both tapes for this frame: shadow, body, highlight. Each tooth sits across
    /// its tape's edge (cx ∓ gap), turned square to the edge; closed teeth interlock across the
    /// centre.
    static func teeth(_ z: M.Zip, ys: Double, o: Double) -> (shadow: Path, body: Path, light: Path) {
        var shadow = Path()
        var body = Path()
        var light = Path()
        let hw = M.toothW / 2
        let hh = M.toothH / 2
        func quad(_ p: inout Path, _ x: Double, _ y: Double, _ hw: Double, _ hh: Double, _ ca: Double, _ sa: Double) {
            p.move(to: CGPoint(x: x + -hw * ca - -hh * sa, y: y + -hw * sa + -hh * ca))
            p.addLine(to: CGPoint(x: x + hw * ca - -hh * sa, y: y + hw * sa + -hh * ca))
            p.addLine(to: CGPoint(x: x + hw * ca - hh * sa, y: y + hw * sa + hh * ca))
            p.addLine(to: CGPoint(x: x + -hw * ca - hh * sa, y: y + -hw * sa + hh * ca))
            p.closeSubpath()
        }
        for s in [-1.0, 1.0] {
            var y = z.yTop + 2 + (s > 0 ? M.toothPitch / 2 : 0)
            while y < z.yEnd - 6 {
                let g = z.gapAt(y, ys: ys, o: o)
                let x = z.cx + s * g - s * 0.3
                if x > -12 && x < z.width + 12 {
                    let slope = (z.gapAt(y + 1.5, ys: ys, o: o) - z.gapAt(y - 1.5, ys: ys, o: o)) / 3
                    let a = -atan(s * slope)
                    let ca = cos(a)
                    let sa = sin(a)
                    quad(&shadow, x + 0.6, y + 1.4, hw, hh, ca, sa)
                    quad(&body, x, y, hw, hh, ca, sa)
                    // The lit upper face of the scoop.
                    quad(&light, x + sa * hh * 0.45, y - ca * hh * 0.45, hw - 1, hh * 0.32, ca, sa)
                }
                y += M.toothPitch
            }
        }
        return (shadow, body, light)
    }
}
