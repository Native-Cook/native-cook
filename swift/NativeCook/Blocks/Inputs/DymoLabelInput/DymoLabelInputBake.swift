import UIKit

/* Dymo Label Input — wall and shelf, the jar, the embosser and its wheel, baked once off the main thread at 2x. */

enum DymoLabelInputBake {
    static func ui(_ hex: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
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

    private static func linear(_ c: CGContext, _ rect: CGRect, _ g: CGGradient, _ a: CGPoint, _ b: CGPoint) {
        c.saveGState()
        c.clip(to: rect)
        c.drawLinearGradient(g, start: a, end: b, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        c.restoreGState()
    }

    private static func noiseLayer(_ c: CGContext, _ rect: CGRect, _ img: CGImage?, alpha: CGFloat, tile: Bool) {
        guard let img else { return }
        c.saveGState()
        c.clip(to: rect)
        c.setBlendMode(.multiply)
        c.setAlpha(alpha)
        if tile {
            c.draw(img, in: CGRect(x: 0, y: 0, width: img.width, height: img.height), byTiling: true)
        } else {
            c.interpolationQuality = .high
            c.draw(img, in: rect)
        }
        c.restoreGState()
    }

    static func wall(_ g: DymoLabelInputGeo) -> CGImage? {
        let w = CGFloat(g.w), h = CGFloat(g.h), shelfY = CGFloat(g.shelfY)
        guard w > 0, h > 0 else { return nil }
        let grain = DymoLabelInputNoise.image(w: 128, h: 128, fx: 0.8, fy: 0.8, octaves: 3, seed: 2, turbulence: false)
        let wood = DymoLabelInputNoise.image(w: max(2, Int(w)), h: 28, fx: 0.01, fy: 0.35, octaves: 3, seed: 6, turbulence: false)
        let cgrain = DymoLabelInputNoise.image(w: 128, h: 128, fx: 0.6, fy: 0.6, octaves: 3, seed: 8, turbulence: false)
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format()).image { r in
            let c = r.cgContext
            let all = CGRect(x: 0, y: 0, width: w, height: h)
            linear(c, all, gradient([ui(0xF2EADF), ui(0xEADFD0), ui(0xDFD1BE)], [0, 0.6, 1]), .zero, CGPoint(x: 0, y: shelfY))
            noiseLayer(c, all, grain, alpha: 0.05, tile: true)
            // The shelf: a thick oak plank with a lit front edge, and the counter below it.
            blurFill(c, CGPath(rect: CGRect(x: -20, y: shelfY + 12, width: w + 40, height: 18), transform: nil), ui(0x5B3D22, 0.28), 12)
            let plank = CGRect(x: 0, y: shelfY - 8, width: w, height: 28)
            linear(c, plank, gradient([ui(0xD2A574), ui(0xB88653), ui(0x8E6238)], [0, 0.4, 1]), CGPoint(x: 0, y: shelfY - 10), CGPoint(x: 0, y: shelfY + 22))
            noiseLayer(c, plank, wood, alpha: 0.22, tile: false)
            c.setFillColor(ui(0xF3D3A7, 0.7).cgColor)
            c.fill(CGRect(x: 0, y: shelfY - 8, width: w, height: 1.2))
            c.setFillColor(ui(0x6E4826, 0.6).cgColor)
            c.fill(CGRect(x: 0, y: shelfY + 1, width: w, height: 1))
            // The counter where the embosser sits: warm linen-grey with a soft light pool.
            let counter = CGRect(x: 0, y: shelfY + 20, width: w, height: h)
            c.saveGState()
            c.clip(to: counter)
            let pool = CGPoint(x: g.wheelX, y: g.wheelY - 20)
            c.drawRadialGradient(gradient([ui(0xE7DDCF), ui(0xD9CCBA), ui(0xC5B59F)], [0, 0.55, 1]), startCenter: pool, startRadius: 0, endCenter: pool, endRadius: w * 0.9, options: [.drawsAfterEndLocation])
            c.restoreGState()
            noiseLayer(c, counter, cgrain, alpha: 0.06, tile: true)
        }.cgImage
    }

    /// A fresh jar of oats on its own: an image (jarW + 60) × (jarH + 50) pt, the jar's base 20 pt above its bottom edge.
    static func jar(_ g: DymoLabelInputGeo) -> CGImage? {
        let jarW = CGFloat(g.jarW), jarH = CGFloat(g.jarH)
        let W = jarW + 60, H = jarH + 50
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: format()).image { r in
            let c = r.cgContext
            let x0: CGFloat = 30
            let base = 30 + jarH
            let top: CGFloat = 30 + 22
            let bodyRect = CGRect(x: x0, y: top, width: jarW, height: base - top)
            let body = CGPath(roundedRect: bodyRect, cornerWidth: 18, cornerHeight: 18, transform: nil)
            blurFill(c, CGPath(ellipseIn: CGRect(x: x0 - 4, y: base - 6, width: jarW + 8, height: 12), transform: nil), ui(0x4A301A, 0.32), 6)
            // Oats inside, up to 80 %: a warm base with hundreds of flakes.
            c.saveGState()
            c.addPath(body)
            c.clip()
            let fillTop = top + (base - top) * 0.2
            linear(c, CGRect(x: x0, y: fillTop, width: jarW, height: base - fillTop), gradient([ui(0xE2CB9E), ui(0xCDAE7A), ui(0xB08F5D)], [0, 0.5, 1]), CGPoint(x: 0, y: fillTop), CGPoint(x: 0, y: base))
            var seed = 7
            func rnd() -> CGFloat {
                seed = (seed * 16807) % 2_147_483_647
                return CGFloat(seed) / 2_147_483_647
            }
            for _ in 0..<520 {
                let fx = x0 + rnd() * jarW
                let fy = fillTop - 2 + rnd() * (base - fillTop + 2)
                let fw = 4 + rnd() * 5
                let fh = 2.4 + rnd() * 2.6
                let shade = rnd()
                let color = shade > 0.7 ? ui(0xF4E3BF, 0.9) : shade > 0.35 ? ui(0xD8BC8A, 0.9) : ui(0xA9875A, 0.85)
                let rot = rnd() * .pi
                c.saveGState()
                c.translateBy(x: fx, y: fy)
                c.rotate(by: rot)
                c.setFillColor(color.cgColor)
                c.fillEllipse(in: CGRect(x: -fw / 2, y: -fh / 2, width: fw, height: fh))
                c.restoreGState()
            }
            // Glass: faint tint, thick base, edges and two tall highlights.
            c.drawLinearGradient(
                gradient([ui(0x6E8A86, 0.28), ui(0xFFFFFF, 0.02), ui(0xFFFFFF, 0), ui(0x5E7A76, 0.3)], [0, 0.25, 0.7, 1]),
                start: CGPoint(x: x0, y: 0), end: CGPoint(x: x0 + jarW, y: 0), options: []
            )
            c.setFillColor(ui(0xFFFFFF, 0.5).cgColor)
            c.addPath(CGPath(roundedRect: CGRect(x: x0 + jarW * 0.12, y: top + 10, width: 5, height: base - top - 26), cornerWidth: 2.5, cornerHeight: 2.5, transform: nil))
            c.fillPath()
            c.setFillColor(ui(0xFFFFFF, 0.25).cgColor)
            c.addPath(CGPath(roundedRect: CGRect(x: x0 + jarW * 0.82, y: top + 14, width: 3, height: base - top - 40), cornerWidth: 1.5, cornerHeight: 1.5, transform: nil))
            c.fillPath()
            c.restoreGState()
            c.setLineWidth(1.6)
            c.setStrokeColor(ui(0x8FA6A2, 0.55).cgColor)
            c.addPath(body)
            c.strokePath()
            // Screw lid: brushed brass with ridges.
            let lid = CGPath(roundedRect: CGRect(x: x0 + 6, y: 30, width: jarW - 12, height: 24), cornerWidth: 5, cornerHeight: 5, transform: nil)
            c.saveGState()
            c.addPath(lid)
            c.clip()
            c.drawLinearGradient(gradient([ui(0xF3DEA4), ui(0xC7A15A), ui(0x8F6E33), ui(0xB89250)], [0, 0.35, 0.75, 1]), start: CGPoint(x: 0, y: 30), end: CGPoint(x: 0, y: 54), options: [])
            c.restoreGState()
            c.setLineWidth(1)
            c.setLineCap(.round)
            var x = x0 + 10
            while x < x0 + jarW - 10 {
                c.setStrokeColor(ui(Int(x.rounded()) % 2 == 0 ? 0xFFF1C8 : 0x7C5C28, 0.45).cgColor)
                c.move(to: CGPoint(x: x, y: 33))
                c.addLine(to: CGPoint(x: x, y: 51))
                c.strokePath()
                x += 3.2
            }
        }.cgImage
    }

    /// The embosser's body, centred on the wheel: an image of side wheelR × 3.6 pt.
    static func body(_ g: DymoLabelInputGeo) -> CGImage? {
        let side = CGFloat(g.bodySide)
        let o = side / 2
        let R = CGFloat(g.wheelR)
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format()).image { r in
            let c = r.cgContext
            let round = CGPath(ellipseIn: CGRect(x: o - (R + 18), y: o - (R + 18), width: (R + 18) * 2, height: (R + 18) * 2), transform: nil)
            let handle = CGPath(roundedRect: CGRect(x: o, y: o - R * 0.35, width: R * 2.9, height: R * 1.05), cornerWidth: R * 0.5, cornerHeight: R * 0.5, transform: nil)
            let path = round.union(handle)
            c.saveGState()
            c.translateBy(x: 4, y: 14)
            blurFill(c, path, ui(0x3D2A18, 0.28), 14)
            c.restoreGState()
            c.saveGState()
            c.translateBy(x: 1, y: 3)
            blurFill(c, path, ui(0x2E1F12, 0.35), 3)
            c.restoreGState()
            c.saveGState()
            c.addPath(path)
            c.clip()
            c.drawLinearGradient(gradient([ui(0x4E6C66), ui(0x3B5752), ui(0x2B403C), ui(0x22332F)], [0, 0.35, 0.75, 1]), start: CGPoint(x: 0, y: o - R - 18), end: CGPoint(x: 0, y: o + R + 18), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            let sc = CGPoint(x: o - R * 0.4, y: o - R * 0.8)
            c.drawRadialGradient(gradient([ui(0xFFFFFF, 0.2), ui(0xFFFFFF, 0.04), ui(0xFFFFFF, 0)], [0, 0.5, 1]), startCenter: sc, startRadius: 0, endCenter: sc, endRadius: R * 2.2, options: [])
            c.restoreGState()
            c.setLineWidth(1)
            c.setStrokeColor(ui(0x9FC1BA, 0.45).cgColor)
            c.addPath(path)
            c.strokePath()
            // The wheel's well.
            c.saveGState()
            c.addEllipse(in: CGRect(x: o - R - 5, y: o - R - 5, width: (R + 5) * 2, height: (R + 5) * 2))
            c.clip()
            c.drawRadialGradient(gradient([ui(0x101816), ui(0x18231F), ui(0x0C1210)], [0, 0.85, 1]), startCenter: CGPoint(x: o, y: o), startRadius: 0, endCenter: CGPoint(x: o, y: o), endRadius: R + 6, options: [.drawsAfterEndLocation])
            c.restoreGState()
            // A small embossed brand plate on the handle.
            c.setFillColor(ui(0xC9D6D2, 0.18).cgColor)
            c.addPath(CGPath(roundedRect: CGRect(x: o + R * 1.45, y: o - R * 0.08, width: R * 1.1, height: R * 0.32), cornerWidth: R * 0.16, cornerHeight: R * 0.16, transform: nil))
            c.fillPath()
        }.cgImage
    }

    /// The character wheel: a dark disc with raised white characters round the rim, slot 0 at the top.
    static func wheel(_ g: DymoLabelInputGeo) -> CGImage? {
        let side = CGFloat(g.wheelSide)
        let o = side / 2
        let R = CGFloat(g.wheelR)
        let font = UIFont.systemFont(ofSize: 12, weight: .bold)
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format()).image { r in
            let c = r.cgContext
            func disc(_ radius: CGFloat, _ colors: [UIColor], _ locs: [CGFloat], _ center: CGPoint, _ gr: CGFloat) {
                c.saveGState()
                c.addEllipse(in: CGRect(x: o - radius, y: o - radius, width: radius * 2, height: radius * 2))
                c.clip()
                c.drawRadialGradient(gradient(colors, locs), startCenter: center, startRadius: 0, endCenter: center, endRadius: gr, options: [.drawsAfterEndLocation])
                c.restoreGState()
            }
            disc(R, [ui(0x3A3E45), ui(0x2A2D33), ui(0x1B1D21)], [0, 0.75, 1], CGPoint(x: o, y: o), R)
            c.setLineWidth(1)
            c.setStrokeColor(ui(0xFFFFFF, 0.12).cgColor)
            c.strokeEllipse(in: CGRect(x: o - (R - 0.8), y: o - (R - 0.8), width: (R - 0.8) * 2, height: (R - 0.8) * 2))
            c.strokeEllipse(in: CGRect(x: o - R * 0.62, y: o - R * 0.62, width: R * 1.24, height: R * 1.24))
            for (j, ch) in DymoLabelInputMotion.wheelChars.enumerated() where ch != " " {
                let a = Double(j) * DymoLabelInputMotion.step * .pi / 180
                let s = String(ch) as NSString
                let w = s.size(withAttributes: [.font: font]).width
                c.saveGState()
                c.translateBy(x: o, y: o)
                c.rotate(by: CGFloat(a))
                c.translateBy(x: -o, y: -o)
                let baseY = o - R * 0.73
                s.draw(at: CGPoint(x: o - w / 2 + 0.5, y: baseY + 0.8 - font.ascender), withAttributes: [.font: font, .foregroundColor: ui(0x000000, 0.5)])
                s.draw(at: CGPoint(x: o - w / 2, y: baseY - font.ascender), withAttributes: [.font: font, .foregroundColor: ui(0xECE8E0)])
                c.restoreGState()
            }
            // Grip cut-outs in the hub.
            disc(R * 0.5, [ui(0x5A5F68), ui(0x33363C), ui(0x202226)], [0, 0.6, 1], CGPoint(x: o - 6, y: o - 8), R * 0.55)
            c.setFillColor(ui(0x16181B).cgColor)
            for k in 0..<6 {
                let a = CGFloat(k) / 6 * .pi * 2
                let px = o + cos(a) * R * 0.32, py = o + sin(a) * R * 0.32, pr = R * 0.075
                c.fillEllipse(in: CGRect(x: px - pr, y: py - pr, width: pr * 2, height: pr * 2))
            }
            c.strokeEllipse(in: CGRect(x: o - R * 0.1, y: o - R * 0.1, width: R * 0.2, height: R * 0.2))
        }.cgImage
    }
}
