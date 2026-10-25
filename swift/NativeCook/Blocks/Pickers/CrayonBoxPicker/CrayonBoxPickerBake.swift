import UIKit

/* Crayon Box Picker — desk, paper, box, crayons and the paper's tooth, baked once off the main thread at 2x. */

enum CrayonBoxPickerBake {
    static func ui(_ hex: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
    }

    private static func tone(_ c: (Double, Double, Double), _ k: Double, _ a: Double = 1) -> UIColor {
        if k < 0 { return UIColor(red: c.0 * (1 + k), green: c.1 * (1 + k), blue: c.2 * (1 + k), alpha: a) }
        return UIColor(red: c.0 + (1 - c.0) * k, green: c.1 + (1 - c.1) * k, blue: c.2 + (1 - c.2) * k, alpha: a)
    }

    static func gradient(_ colors: [UIColor], _ locations: [CGFloat]) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: locations)!
    }

    /// Fills `path` blurred like a Skia normal blur of `sigma`, drawing only its shadow.
    static func blurFill(_ c: CGContext, _ path: CGPath, _ color: UIColor, _ sigma: CGFloat) {
        c.saveGState()
        let k = max(1, abs(c.ctm.a))
        let off: CGFloat = 4000
        c.setShadow(offset: CGSize(width: off * k, height: 0), blur: sigma * 2 * k, color: color.cgColor)
        c.translateBy(x: -off, y: 0)
        c.addPath(path)
        c.setFillColor(UIColor.black.cgColor)
        c.fillPath()
        c.restoreGState()
    }

    static func format() -> UIGraphicsImageRendererFormat {
        let f = UIGraphicsImageRendererFormat()
        f.scale = 2
        f.opaque = false
        f.preferredRange = .standard
        return f
    }

    private static func fill(_ c: CGContext, _ path: CGPath, _ g: CGGradient, _ a: CGPoint, _ b: CGPoint) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawLinearGradient(g, start: a, end: b, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        c.restoreGState()
    }

    private static func multiply(_ c: CGContext, _ img: CGImage?, _ alpha: CGFloat, clip: CGPath) {
        guard let img else { return }
        c.saveGState()
        c.addPath(clip)
        c.clip()
        c.setBlendMode(.multiply)
        c.setAlpha(alpha)
        c.draw(img, in: CGRect(x: 0, y: 0, width: img.width, height: img.height), byTiling: true)
        c.restoreGState()
    }

    /// The desk, the sheet of paper, and the open box's back and inside.
    static func desk(_ g: CrayonBoxPickerGeo) -> CGImage? {
        let w = CGFloat(g.w), h = CGFloat(g.h)
        guard w > 0, h > 0 else { return nil }
        let grain = CrayonBoxPickerNoise.image(w: 128, h: 128, fx: 0.8, fy: 0.8, octaves: 3, seed: 5, turbulence: false)
        let tooth = CrayonBoxPickerNoise.image(w: 128, h: 128, fx: 0.9, fy: 0.9, octaves: 2, seed: 13, turbulence: false)
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format()).image { r in
            let c = r.cgContext
            let all = CGPath(rect: CGRect(x: 0, y: 0, width: w, height: h), transform: nil)
            fill(c, all, gradient([ui(0xBFD8D2), ui(0xA9C9C2), ui(0x93B5AE)], [0, 0.55, 1]), .zero, CGPoint(x: w * 0.3, y: h))
            multiply(c, grain, 0.06, clip: all)
            // Paper with two shadows and a slight tooth.
            let pw = w - 24
            let px = CGFloat(g.cx) - pw / 2
            let paperY = CGFloat(g.paperY), paperH = CGFloat(g.paperH)
            let paper = CGPath(roundedRect: CGRect(x: px, y: paperY, width: pw, height: paperH), cornerWidth: 6, cornerHeight: 6, transform: nil)
            c.saveGState()
            c.translateBy(x: 0, y: 8)
            blurFill(c, paper, ui(0x2F4A44, 0.22), 12)
            c.restoreGState()
            c.saveGState()
            c.translateBy(x: 0, y: 1.5)
            blurFill(c, paper, ui(0x2F4A44, 0.3), 2)
            c.restoreGState()
            fill(c, paper, gradient([ui(0xFFFDF8), ui(0xFBF6EC), ui(0xF3ECDF)], [0, 0.6, 1]), CGPoint(x: 0, y: paperY), CGPoint(x: 0, y: paperY + paperH))
            multiply(c, tooth, 0.06, clip: paper)
            // The box: its back flap, open and leaning away, and the dark inside above the feet.
            let boxX = CGFloat(g.boxX), boxW = CGFloat(g.boxW), rimY = CGFloat(g.rimY)
            let backTop = rimY - CGFloat(g.showBack) - 18
            let flap = CGMutablePath()
            flap.move(to: CGPoint(x: boxX + 4, y: rimY))
            flap.addLine(to: CGPoint(x: boxX + 14, y: backTop))
            flap.addLine(to: CGPoint(x: boxX + boxW - 14, y: backTop))
            flap.addLine(to: CGPoint(x: boxX + boxW - 4, y: rimY))
            flap.closeSubpath()
            c.saveGState()
            c.translateBy(x: 0, y: 6)
            blurFill(c, flap, ui(0x2F4A44, 0.25), 10)
            c.restoreGState()
            fill(c, flap, gradient([ui(0x2F6B52), ui(0x245641), ui(0x173B2C)], [0, 0.5, 1]), CGPoint(x: 0, y: backTop), CGPoint(x: 0, y: rimY))
            c.setFillColor(ui(0xF4C64E, 0.22).cgColor)
            for k in 0..<9 {
                let x = boxX + 36 + CGFloat(k) * ((boxW - 72) / 8)
                c.fillEllipse(in: CGRect(x: x - 3, y: backTop + 12 - 3, width: 6, height: 6))
            }
            fill(c, CGPath(rect: CGRect(x: boxX + 6, y: rimY - 46, width: boxW - 12, height: 50), transform: nil),
                 gradient([ui(0x173B2C, 0), ui(0x0F271D, 0.7), ui(0x0B1D16, 0.9)], [0, 0.6, 1]), CGPoint(x: 0, y: rimY - 46), CGPoint(x: 0, y: rimY + 4))
        }.cgImage
    }

    /// The printed box front: an image of (boxW + 40) × (frontH + 40) pt drawn at (boxX − 20, rimY − 20).
    static func front(_ g: CrayonBoxPickerGeo) -> CGImage? {
        let boxW = CGFloat(g.boxW), frontH = CGFloat(g.frontH)
        let W = boxW + 40, H = frontH + 40
        let toothImg = CrayonBoxPickerNoise.image(w: 128, h: 128, fx: 0.7, fy: 0.7, octaves: 3, seed: 6, turbulence: false)
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: format()).image { r in
            let c = r.cgContext
            let rect = CGRect(x: 20, y: 20, width: boxW, height: frontH)
            let card = CGPath(roundedRect: rect, cornerWidth: 6, cornerHeight: 6, transform: nil)
            c.saveGState()
            c.translateBy(x: 0, y: 8)
            blurFill(c, card, ui(0x2F4A44, 0.3), 9)
            c.restoreGState()
            fill(c, card, gradient([ui(0xFAD465), ui(0xF2C04A), ui(0xDFA834)], [0, 0.5, 1]), CGPoint(x: 0, y: 20), CGPoint(x: 0, y: 20 + frontH))
            c.saveGState()
            c.addPath(card)
            c.clip()
            // A green band, a row of printed colour bars, the brand.
            fill(c, CGPath(rect: CGRect(x: 20, y: 20 + frontH * 0.42, width: boxW, height: frontH * 0.4), transform: nil),
                 gradient([ui(0x2F7356), ui(0x24604A), ui(0x1B4E3B)], [0, 0.5, 1]), CGPoint(x: 0, y: 20 + frontH * 0.42), CGPoint(x: 0, y: 20 + frontH * 0.82))
            let barW = (boxW - 40) / 12
            for i in 0..<12 {
                let rgb = CrayonBoxPickerMotion.crayons[i].rgb
                c.setFillColor(UIColor(red: rgb.0, green: rgb.1, blue: rgb.2, alpha: 1).cgColor)
                c.addPath(CGPath(roundedRect: CGRect(x: 40 + CGFloat(i) * barW + 1.5, y: 20 + frontH * 0.16, width: barW - 3, height: frontH * 0.14), cornerWidth: 2, cornerHeight: 2, transform: nil))
                c.fillPath()
            }
            let brand = UIFont.systemFont(ofSize: 24, weight: .heavy)
            let title = "DOODLEBUG"
            let widths = title.map { (String($0) as NSString).size(withAttributes: [.font: brand]).width }
            let tw = widths.reduce(0, +) + 2.5 * CGFloat(title.count - 1)
            var x = 20 + (boxW - tw) / 2
            let by = 20 + frontH * 0.62 + 24 * 0.36
            for (i, ch) in title.enumerated() {
                let s = String(ch) as NSString
                s.draw(at: CGPoint(x: x + 1, y: by + 1.5 - brand.ascender), withAttributes: [.font: brand, .foregroundColor: ui(0x0F2C21, 0.5)])
                s.draw(at: CGPoint(x: x, y: by - brand.ascender), withAttributes: [.font: brand, .foregroundColor: ui(0xFFF4D6)])
                x += widths[i] + 2.5
            }
            let small = UIFont.systemFont(ofSize: 9, weight: .bold)
            let line = "12 WAX CRAYONS · WASHABLE" as NSString
            let lw = line.size(withAttributes: [.font: small]).width
            line.draw(at: CGPoint(x: 20 + (boxW - lw) / 2, y: 20 + frontH * 0.93 - small.ascender), withAttributes: [.font: small, .foregroundColor: ui(0x6B4A12, 0.85)])
            multiply(c, toothImg, 0.08, clip: CGPath(rect: rect, transform: nil))
            // The folds into the side panels and the lit top edge.
            c.drawLinearGradient(
                gradient([ui(0x7A5410, 0.28), ui(0x7A5410, 0), ui(0x7A5410, 0), ui(0x7A5410, 0.3)], [0, 0.06, 0.94, 1]),
                start: CGPoint(x: 20, y: 0), end: CGPoint(x: 20 + boxW, y: 0), options: []
            )
            c.restoreGState()
            c.setFillColor(ui(0xFFF5D2, 0.85).cgColor)
            c.addPath(CGPath(roundedRect: CGRect(x: 21, y: 20.5, width: boxW - 2, height: 2), cornerWidth: 1, cornerHeight: 1, transform: nil))
            c.fillPath()
        }.cgImage
    }

    /// Crayon i, standing, tip up: an image of cw × cl pt.
    static func crayon(_ g: CrayonBoxPickerGeo, _ i: Int) -> CGImage? {
        let W = CGFloat(g.cw), H = CGFloat(g.cl)
        guard W > 0, H > 0 else { return nil }
        let rgb = CrayonBoxPickerMotion.crayons[i].rgb
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: format()).image { r in
            let c = r.cgContext
            // Each tip is worn its own way: height, flat top and lean.
            let h1 = CGFloat((sin(Double(i) * 12.9898) * 43758.5453).truncatingRemainder(dividingBy: 1))
            let h2 = CGFloat((sin(Double(i) * 78.233) * 43758.5453).truncatingRemainder(dividingBy: 1))
            let tipH = W * (0.95 + 0.35 * abs(h1))
            let flat = W * (0.12 + 0.22 * abs(h2))
            let lean = W * 0.12 * (h1 > 0 ? 1 : -1) * abs(h2)
            let wax = tone(rgb, 0)
            let tip = CGMutablePath()
            tip.move(to: CGPoint(x: W / 2 - flat / 2 + lean, y: 1.2))
            tip.addQuadCurve(to: CGPoint(x: W / 2 + flat / 2 + lean, y: 1.2), control: CGPoint(x: W / 2 + lean, y: 0))
            tip.addLine(to: CGPoint(x: W * 0.9, y: tipH))
            tip.addLine(to: CGPoint(x: W * 0.1, y: tipH))
            tip.closeSubpath()
            c.setFillColor(wax.cgColor)
            c.addPath(tip)
            c.fillPath()
            c.fill(CGRect(x: W * 0.04, y: tipH - 0.5, width: W * 0.92, height: H - tipH - 2))
            c.addPath(CGPath(roundedRect: CGRect(x: W * 0.04, y: H - 8, width: W * 0.92, height: 8), cornerWidth: 2, cornerHeight: 2, transform: nil))
            c.fillPath()
            // Wrapper: printed paper in the crayon's colour with two black bands and the name.
            let top = tipH + W * 0.4
            let bottom = H - W * 0.5
            c.setFillColor(tone(rgb, 0.12).cgColor)
            c.fill(CGRect(x: 0, y: top, width: W, height: bottom - top))
            c.setFillColor(ui(0x16120E, 0.85).cgColor)
            for y in [top + 4, top + 8.5, bottom - 10, bottom - 5.5] { c.fill(CGRect(x: 0, y: y, width: W, height: 1.6)) }
            let font = UIFont.systemFont(ofSize: 8, weight: .bold)
            let name = CrayonBoxPickerMotion.crayons[i].name.uppercased() as NSString
            let nw = name.size(withAttributes: [.font: font]).width
            c.saveGState()
            c.translateBy(x: W / 2 + 8 * 0.36, y: top + 16 + nw)
            c.rotate(by: -.pi / 2)
            name.draw(at: CGPoint(x: 0, y: -font.ascender), withAttributes: [.font: font, .foregroundColor: ui(0x16120E, 0.9)])
            c.restoreGState()
            // Round it: dark edges, a soft highlight a third of the way across, a crisp specular line.
            c.saveGState()
            c.clip(to: CGRect(x: 0, y: tipH * 0.4, width: W, height: H - tipH * 0.4))
            c.drawLinearGradient(
                gradient([ui(0x000000, 0.42), ui(0x000000, 0), ui(0xFFFFFF, 0.22), ui(0x000000, 0.05), ui(0x000000, 0.5)], [0, 0.18, 0.34, 0.62, 1]),
                start: .zero, end: CGPoint(x: W, y: 0), options: []
            )
            c.restoreGState()
            fill(c, tip, gradient([ui(0x000000, 0.35), ui(0xFFFFFF, 0.25), ui(0x000000, 0.4)], [0, 0.35, 1]), .zero, CGPoint(x: W, y: 0))
            c.setFillColor(ui(0xFFFFFF, 0.4).cgColor)
            c.fill(CGRect(x: W * 0.3, y: tipH + 2, width: W * 0.06, height: H - tipH - 6))
        }.cgImage
    }

    /// The paper's tooth over the swatch: black where the wax skips (noise × 5 − 3.1), clear elsewhere.
    static func tooth(_ rect: CGRect) -> CGImage? {
        let scale = 2.0
        let w = max(2, Int(rect.width * scale)), h = max(2, Int(rect.height * scale))
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let f = 0.55 / scale
        for y in 0..<h {
            for x in 0..<w {
                var sum = 0.0
                var amp = 1.0
                var fr = 1.0
                for o in 0..<2 {
                    // Sampled in screen space, so the tooth stays put on the paper.
                    let sx = (Double(x) + rect.minX * scale) * f * fr
                    let sy = (Double(y) + rect.minY * scale) * f * fr
                    sum += CrayonBoxPickerNoise.noise(sx, sy, 9 + o * 31) * amp
                    amp *= 0.5
                    fr *= 2
                }
                let n = 0.5 + sum * 0.7
                let a = max(0, min(1, 5 * n - 3.1))
                px[(y * w + x) * 4 + 3] = UInt8(a * 255)
            }
        }
        guard let provider = CGDataProvider(data: Data(px) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}
