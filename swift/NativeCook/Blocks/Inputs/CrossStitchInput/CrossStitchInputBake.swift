import SwiftUI
import UIKit

/* Cross Stitch Input — the table, hoop and cloth baked once off the main thread at 2x; the printed border is rendered once on top. */

enum CrossStitchInputBake {
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

    /// The table, hoop and cloth.
    static func scene(_ g: CrossStitchInputGeo) -> CGImage? {
        let w = CGFloat(g.w), h = CGFloat(g.h)
        guard w > 0, h > 0 else { return nil }
        let cx = CGFloat(g.cx), cy = CGFloat(g.cy), cell = CGFloat(g.cell)
        let fabricR = CGFloat(g.fabricR), hoopR = CGFloat(g.hoopR)
        let grain = CrossStitchInputNoise.image(w: 128, h: 128, fx: 0.9, fy: 0.9, octaves: 3, seed: 7, turbulence: false)
        let tooth = CrossStitchInputNoise.image(w: 128, h: 128, fx: 0.35, fy: 0.35, octaves: 3, seed: 3, turbulence: false)
        let side = Int(hoopR * 2 + 4)
        let ringGrain = CrossStitchInputNoise.image(w: side, h: side, fx: 0.02, fy: 0.45, octaves: 3, seed: 11, turbulence: false)
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format()).image { r in
            let c = r.cgContext
            // Warm paper backdrop with a three-stop wash and grain.
            c.drawLinearGradient(gradient([ui(0xF3EBDD), ui(0xEADFCB), ui(0xE2D3BC)], [0, 0.55, 1]), start: .zero, end: CGPoint(x: w * 0.3, y: h), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            if let grain {
                c.saveGState()
                c.setBlendMode(.multiply)
                c.setAlpha(0.05)
                c.draw(grain, in: CGRect(x: 0, y: 0, width: 128, height: 128), byTiling: true)
                c.restoreGState()
            }
            // The hoop's two shadows on the table.
            blurFill(c, CGPath(ellipseIn: circle(cx, cy + 16, hoopR + 2), transform: nil), ui(0x5A4128, 0.2), 18)
            blurFill(c, CGPath(ellipseIn: circle(cx, cy + 2, hoopR), transform: nil), ui(0x4A3220, 0.32), 2.5)

            // Cloth: aida's square blocks with holes at the corners, a slight sheen, grain and
            // dimming at the hoop's inner edge.
            let fr = fabricR + 3
            c.saveGState()
            c.addEllipse(in: circle(cx, cy, fr))
            c.clip()
            let sc = CGPoint(x: cx - fr * 0.35, y: cy - fr * 0.45)
            c.drawRadialGradient(gradient([ui(0xFBF7EE), ui(0xF2EBDD), ui(0xE6DCC8)], [0, 0.5, 1]), startCenter: sc, startRadius: 0, endCenter: sc, endRadius: fr * 1.6, options: [.drawsAfterEndLocation])
            let half = Int(CrossStitchInputGeo.cells / 2) + 1
            c.setLineWidth(cell * 0.18)
            c.setStrokeColor(ui(0xFFFFFF, 0.32).cgColor)
            for k in -half...half {
                for off: CGFloat in [-0.25, 0.25] {
                    let v = (CGFloat(k) + 0.5 + off) * cell
                    c.move(to: CGPoint(x: cx - fr, y: cy + v))
                    c.addLine(to: CGPoint(x: cx + fr, y: cy + v))
                    c.move(to: CGPoint(x: cx + v, y: cy - fr))
                    c.addLine(to: CGPoint(x: cx + v, y: cy + fr))
                }
            }
            c.strokePath()
            c.setFillColor(ui(0x8C7A62, 0.38).cgColor)
            for i in -half...half {
                for j in -half...half { c.addEllipse(in: circle(cx + CGFloat(i) * cell, cy + CGFloat(j) * cell, cell * 0.11)) }
            }
            c.fillPath()
            if let tooth {
                c.saveGState()
                c.setBlendMode(.multiply)
                c.setAlpha(0.08)
                c.draw(tooth, in: CGRect(x: 0, y: 0, width: 128, height: 128), byTiling: true)
                c.restoreGState()
            }
            c.drawRadialGradient(gradient([ui(0x5B4630, 0), ui(0x5B4630, 0), ui(0x5B4630, 0.24)], [0, 0.82, 1]), startCenter: CGPoint(x: cx, y: cy), startRadius: 0, endCenter: CGPoint(x: cx, y: cy), endRadius: fr, options: [.drawsAfterEndLocation])
            c.restoreGState()

            // Hoop: the inner ring just shows, the outer ring is a lathe-turned beech band with a bevel.
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
            ring(fabricR - 1, fabricR + 4, [0x9A6B3A, 0xC9995F, 0xD8AE76, 0xB07C45])
            ring(fabricR + 3, hoopR, [0x8E5F31, 0xD9AD73, 0xE8C38E, 0xA8733E])
            if let ringGrain {
                c.saveGState()
                c.addEllipse(in: circle(cx, cy, hoopR))
                c.addEllipse(in: circle(cx, cy, fabricR + 3))
                c.clip(using: .evenOdd)
                c.setBlendMode(.multiply)
                c.setAlpha(0.18)
                c.draw(ringGrain, in: circle(cx, cy, CGFloat(side) / 2))
                c.restoreGState()
            }
            c.setLineWidth(1)
            c.setStrokeColor(ui(0xFFF4E0, 0.45).cgColor)
            c.strokeEllipse(in: circle(cx, cy - 0.5, hoopR - 1.2))
            c.setStrokeColor(ui(0x5E3D1E, 0.45).cgColor)
            c.strokeEllipse(in: circle(cx, cy + 0.5, fabricR + 3.2))

            // The brass screw clamp at the top: two wooden lugs and a turned screw through them.
            let top = cy - hoopR
            for dx: CGFloat in [-11, 3] {
                let rect = CGRect(x: cx + dx, y: top - 13, width: 8, height: 22)
                let path = CGPath(roundedRect: rect, cornerWidth: 2.5, cornerHeight: 2.5, transform: nil)
                c.saveGState()
                c.translateBy(x: 0.5, y: 2.5)
                blurFill(c, path, ui(0x4A3220, 0.3), 3)
                c.restoreGState()
                c.saveGState()
                c.addPath(path)
                c.clip()
                c.drawLinearGradient(gradient([ui(0xE7C18B), ui(0xC4915A), ui(0x9C6B39)], [0, 0.55, 1]), start: CGPoint(x: 0, y: top - 12), end: CGPoint(x: 0, y: top + 12), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
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
            c.setLineWidth(0.7)
            c.setStrokeColor(ui(0x6E4E14, 0.6).cgColor)
            for k in 0..<9 {
                let x = cx - 20 + CGFloat(k) * 4.6
                c.move(to: CGPoint(x: x, y: top - 8.5))
                c.addLine(to: CGPoint(x: x + 1.6, y: top - 1.5))
            }
            c.strokePath()
        }.cgImage
    }

    /// The border motif, already worked, as a transparent image the size of the screen.
    @MainActor
    static func border(_ g: CrossStitchInputGeo) -> Image? {
        let b = CrossStitchInputFont.border()
        let renderer = ImageRenderer(content: Canvas { ctx, _ in
            CrossStitchInputArt.drawStitches(ctx, g, b.cx, b.cy, present: Double(b.cx.count), rgb: CrossStitchInputArt.borderRGB, alpha: 1)
        }.frame(width: g.w, height: g.h))
        renderer.scale = 2
        guard let cg = renderer.cgImage else { return nil }
        return Image(decorative: cg, scale: 2)
    }
}
