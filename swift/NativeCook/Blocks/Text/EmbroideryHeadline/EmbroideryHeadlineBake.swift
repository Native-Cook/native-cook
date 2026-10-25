import CoreText
import UIKit

/* Embroidery Headline — the satin, cut from the rasterised headline, and the table, hoop and linen; built once off the main thread. */

enum EmbroideryHeadlineBake {
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

    private static func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGRect {
        CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)
    }

    // MARK: Satin

    private static func font(_ size: CGFloat) -> CTFont {
        (UIFont(name: "Georgia-Bold", size: size) ?? UIFont.systemFont(ofSize: size, weight: .bold)) as CTFont
    }

    /// Per character advance widths (no kerning, like Skia's glyph widths).
    private static func widths(_ f: CTFont, _ text: String) -> [CGFloat] {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(f, chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(f, .horizontal, glyphs, &adv, chars.count)
        return adv.map(\.width)
    }

    /// Turns the two lines into satin, fitted into the hoop.
    static func satin(_ g: EmbroideryHeadlineGeo, lines: (String, String) = ("Slow", "made")) -> EmbroideryHeadlineSatin {
        let probe = font(100)
        let widest = max(widths(probe, lines.0).reduce(0, +), widths(probe, lines.1 + ".").reduce(0, +))
        let size = min(112, 100 * CGFloat(g.fabricR) * 1.5 / widest)
        let f = font(size)
        let px: CGFloat = 2
        let big = font(size * px)
        let base1 = CGFloat(g.cy) - size * 0.14
        let base2 = base1 + size
        var glyphs: [EmbroideryHeadlineGlyph] = []
        var knot: (Double, Double, Double) = (0, 0, 0)
        for (li, line) in [lines.0, lines.1].enumerated() {
            let w = widths(f, line)
            let full = w.reduce(0, +) + (li == 1 ? size * 0.3 : 0)
            var x = CGFloat(g.cx) - full / 2
            let base = li == 0 ? base1 : base2
            for (i, ch) in line.enumerated() {
                let pad = size * 0.25
                let wpt = w[i] + pad * 2
                let hpt = size * 1.3
                let top = base - size * 0.95
                let iw = Int(ceil(wpt * px)), ih = Int(ceil(hpt * px))
                var pixels = [UInt8](repeating: 0, count: iw * ih)
                pixels.withUnsafeMutableBytes { buf in
                    guard let ctx = CGContext(
                        data: buf.baseAddress, width: iw, height: ih, bitsPerComponent: 8, bytesPerRow: iw,
                        space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
                    ) else { return }
                    ctx.setShouldAntialias(true)
                    let attr = NSAttributedString(string: String(ch), attributes: [
                        NSAttributedString.Key(kCTFontAttributeName as String): big,
                        NSAttributedString.Key(kCTForegroundColorAttributeName as String): CGColor(gray: 1, alpha: 1),
                    ])
                    let ctLine = CTLineCreateWithAttributedString(attr)
                    // Rows of the buffer run top to bottom; CG's y runs up from the bottom.
                    ctx.textPosition = CGPoint(x: pad * px, y: CGFloat(ih) - (base - top) * px)
                    CTLineDraw(ctLine, ctx)
                }
                let angle = EmbroideryHeadlineArt.angles[ch] ?? 50
                glyphs.append(cut(pixels, iw, ih, px, Double(x - pad), Double(top), angle, li == 0 ? EmbroideryHeadlineArt.madder : EmbroideryHeadlineArt.indigo))
                x += w[i]
            }
            if li == 1 { knot = (Double(x + size * 0.14), Double(base - size * 0.095), Double(size * 0.088)) }
        }
        return EmbroideryHeadlineSatin(
            glyphs: glyphs, counts: glyphs.map { Double($0.count) }, spacing: EmbroideryHeadlineArt.spacing,
            knot: knot, knotRGB: EmbroideryHeadlineArt.indigo
        )
    }

    private static func cut(_ pixels: [UInt8], _ iw: Int, _ ih: Int, _ px: CGFloat, _ ox: Double, _ oy: Double, _ angleDeg: Double, _ rgb: EmbroideryHeadlineRGB) -> EmbroideryHeadlineGlyph {
        var segs: [Double] = []
        let p = Double(px)
        func inside(_ x: Double, _ y: Double) -> Bool {
            let i = Int((x * p).rounded()), j = Int((y * p).rounded())
            if i < 0 || j < 0 || i >= iw || j >= ih { return false }
            return Double(pixels[j * iw + i]) > 0.45 * 255
        }
        let a = angleDeg * .pi / 180
        let dx = cos(a), dy = -sin(a)
        let nx = sin(a), ny = cos(a)
        let w = Double(iw) / p, h = Double(ih) / p
        let cx = w / 2, cy = h / 2
        let R = hypot(w, h) / 2
        let step = 0.5
        var o = -R
        while o <= R {
            var runStart = -1e9
            var s = -R
            while s <= R + step {
                let x = cx + nx * o + dx * s
                let y = cy + ny * o + dy * s
                let isIn = s <= R && inside(x, y)
                if isIn && runStart == -1e9 { runStart = s }
                if !isIn && runStart != -1e9 {
                    let s0 = runStart - 0.35
                    let s1 = s - step + 0.35
                    if s1 - s0 > 1.2 {
                        segs.append(contentsOf: [ox + cx + nx * o + dx * s0, oy + cy + ny * o + dy * s0, ox + cx + nx * o + dx * s1, oy + cy + ny * o + dy * s1])
                    }
                    runStart = -1e9
                }
                s += step
            }
            o += EmbroideryHeadlineArt.spacing
        }
        let d = (angleDeg - EmbroideryHeadlineArt.light) * .pi / 180
        return EmbroideryHeadlineGlyph(segs: segs, count: segs.count / 4, rgb: rgb, sheen: cos(d) * cos(d))
    }

    // MARK: Scene

    /// Table, hoop and linen.
    static func scene(_ g: EmbroideryHeadlineGeo) -> CGImage? {
        let w = CGFloat(g.w), h = CGFloat(g.h)
        guard w > 0, h > 0 else { return nil }
        let cx = CGFloat(g.cx), cy = CGFloat(g.cy), hoopR = CGFloat(g.hoopR), fabricR = CGFloat(g.fabricR)
        let grain = EmbroideryHeadlineNoise.image(w: 128, h: 128, fx: 0.8, fy: 0.8, octaves: 3, seed: 5, turbulence: false)
        let fr = fabricR + 3
        let slub = EmbroideryHeadlineNoise.image(w: Int(fr * 2), h: Int(fr * 2), fx: 0.04, fy: 0.6, octaves: 3, seed: 9, turbulence: false)
        let side = Int(hoopR * 2 + 4)
        let woodGrain = EmbroideryHeadlineNoise.image(w: side, h: side, fx: 0.02, fy: 0.45, octaves: 3, seed: 11, turbulence: false)
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format()).image { r in
            let c = r.cgContext
            // A dark sage tablecloth with a vignette and grain.
            let bc = CGPoint(x: cx, y: cy - hoopR * 0.2)
            c.drawRadialGradient(gradient([ui(0x3A4A3F), ui(0x2C3A31), ui(0x1B241F)], [0, 0.5, 1]), startCenter: bc, startRadius: 0, endCenter: bc, endRadius: max(w, h) * 0.75, options: [.drawsAfterEndLocation])
            if let grain {
                c.saveGState()
                c.setBlendMode(.overlay)
                c.setAlpha(0.07)
                c.draw(grain, in: CGRect(x: 0, y: 0, width: 128, height: 128), byTiling: true)
                c.restoreGState()
            }
            blurFill(c, CGPath(ellipseIn: circle(cx, cy + 18, hoopR + 4), transform: nil), ui(0x0B100D, 0.45), 22)
            blurFill(c, CGPath(ellipseIn: circle(cx, cy + 2.5, hoopR), transform: nil), ui(0x0B100D, 0.5), 3)

            // Linen: warm oatmeal with slubby weft and warp threads.
            c.saveGState()
            c.addEllipse(in: circle(cx, cy, fr))
            c.clip()
            let lc = CGPoint(x: cx - fr * 0.3, y: cy - fr * 0.5)
            c.drawRadialGradient(gradient([ui(0xF1E8D8), ui(0xE6DAC4), ui(0xD4C5AC)], [0, 0.55, 1]), startCenter: lc, startRadius: 0, endCenter: lc, endRadius: fr * 1.7, options: [.drawsAfterEndLocation])
            var seed = 1
            func rnd() -> CGFloat {
                seed = (seed * 16807) % 2_147_483_647
                return CGFloat(seed) / 2_147_483_647
            }
            var k = -fr
            while k <= fr {
                for vertical in [false, true] {
                    c.setLineWidth(0.4 + rnd() * 0.45)
                    let light = rnd() > 0.5
                    c.setStrokeColor((light ? ui(0xFFFAF0, Double(0.12 + rnd() * 0.14)) : ui(0x9C8A6C, Double(0.05 + rnd() * 0.08))).cgColor)
                    if vertical {
                        c.move(to: CGPoint(x: cx + k, y: cy - fr))
                        c.addLine(to: CGPoint(x: cx + k, y: cy + fr))
                    } else {
                        c.move(to: CGPoint(x: cx - fr, y: cy + k))
                        c.addLine(to: CGPoint(x: cx + fr, y: cy + k))
                    }
                    c.strokePath()
                }
                k += 1.15
            }
            if let slub {
                c.saveGState()
                c.setBlendMode(.multiply)
                c.setAlpha(0.1)
                c.draw(slub, in: circle(cx, cy, fr))
                c.restoreGState()
            }
            c.drawRadialGradient(gradient([ui(0x4A3A26, 0), ui(0x4A3A26, 0), ui(0x4A3A26, 0.3)], [0, 0.8, 1]), startCenter: CGPoint(x: cx, y: cy), startRadius: 0, endCenter: CGPoint(x: cx, y: cy), endRadius: fr, options: [.drawsAfterEndLocation])
            c.restoreGState()

            func ring(_ r0: CGFloat, _ r1: CGFloat, _ stops: [UInt32]) {
                c.saveGState()
                c.addEllipse(in: circle(cx, cy, r1))
                c.addEllipse(in: circle(cx, cy, r0))
                c.clip(using: .evenOdd)
                c.drawRadialGradient(
                    gradient(stops.map { ui($0) }, [r0 / r1, (r0 + (r1 - r0) * 0.3) / r1, (r0 + (r1 - r0) * 0.7) / r1, 1]),
                    startCenter: CGPoint(x: cx, y: cy), startRadius: 0, endCenter: CGPoint(x: cx, y: cy), endRadius: r1, options: [.drawsAfterEndLocation]
                )
                c.restoreGState()
            }
            ring(fabricR - 1, fabricR + 4, [0x8F6235, 0xC29257, 0xD3A970, 0xA87540])
            ring(fabricR + 3, hoopR, [0x7F5329, 0xD2A46A, 0xE3BC86, 0x9E6A37])
            if let woodGrain {
                c.saveGState()
                c.addEllipse(in: circle(cx, cy, hoopR))
                c.addEllipse(in: circle(cx, cy, fabricR + 3))
                c.clip(using: .evenOdd)
                c.setBlendMode(.multiply)
                c.setAlpha(0.2)
                c.draw(woodGrain, in: circle(cx, cy, CGFloat(side) / 2))
                c.restoreGState()
            }
            c.setLineWidth(1)
            c.setStrokeColor(ui(0xFFF1DA, 0.4).cgColor)
            c.strokeEllipse(in: circle(cx, cy - 0.5, hoopR - 1.2))
            c.setStrokeColor(ui(0x4E3218, 0.5).cgColor)
            c.strokeEllipse(in: circle(cx, cy + 0.5, fabricR + 3.2))
            // Brass screw clamp.
            let top = cy - hoopR
            for dx: CGFloat in [-11, 3] {
                c.saveGState()
                c.addPath(CGPath(roundedRect: CGRect(x: cx + dx, y: top - 13, width: 8, height: 22), cornerWidth: 2.5, cornerHeight: 2.5, transform: nil))
                c.clip()
                c.drawLinearGradient(gradient([ui(0xE2B880), ui(0xBD8A52), ui(0x936234)], [0, 0.55, 1]), start: CGPoint(x: 0, y: top - 12), end: CGPoint(x: 0, y: top + 12), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                c.restoreGState()
            }
            let brass = gradient([ui(0xFFF0B8), ui(0xD9A93E), ui(0x8D6418), ui(0xC99733)], [0, 0.35, 0.75, 1])
            for rect in [CGRect(x: cx - 22, y: top - 9, width: 44, height: 8), CGRect(x: cx + 20, y: top - 12, width: 9, height: 14)] {
                c.saveGState()
                c.addPath(CGPath(roundedRect: rect, cornerWidth: 3, cornerHeight: 3, transform: nil))
                c.clip()
                c.drawLinearGradient(brass, start: CGPoint(x: 0, y: top - 9), end: CGPoint(x: 0, y: top - 1), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                c.restoreGState()
            }
        }.cgImage
    }
}
