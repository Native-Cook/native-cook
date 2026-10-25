import SwiftUI
import UIKit

/// Everything the Punch Card Form draws. The blank card (stock, grain, 960 printed digits) is
/// baked once off the main thread; the bed, the reader and the punch head are static Canvas views
/// drawn once; per frame only the holes, the printed letters, the chads and the lamps are drawn.
enum PunchCardFormArt {
    typealias M = PunchCardFormMotion

    struct Baked {
        let card: CGImage
        let grain: CGImage
        let bedBrush: CGImage
        let readerBrush: CGImage
    }

    static let bake = 2.0

    static let shared: Task<Baked, Never> = Task.detached(priority: .userInitiated) { make() }

    static func make() -> Baked {
        let grain = noise(w: 100, h: 100, fx: 0.9, fy: 0.9, octaves: 2, seed: 5)
        let fibre = noise(w: 150, h: 100, fx: 0.02, fy: 0.6, octaves: 2, seed: 9)
        return Baked(card: bakeCard(grain: grain, fibre: fibre),
                     grain: noise(w: 100, h: 100, fx: 0.85, fy: 0.85, octaves: 2, seed: 3),
                     bedBrush: noise(w: 393, h: 360, fx: 0.003, fy: 0.5, octaves: 3, seed: 12),
                     readerBrush: noise(w: 393, h: 110, fx: 0.003, fy: 0.6, octaves: 3, seed: 31))
    }

    // MARK: Noise

    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ seed &* 1_442_695_041)
        h = (h ^ (h >> 13)) &* 1_274_126_177
        h ^= h >> 16
        return Double(h & 0xFFFF) / 65535
    }

    /// Grey fractal value noise, 1 px per point, tiling where each octave's cells fit the size.
    static func noise(w: Int, h: Int, fx: Double, fy: Double, octaves: Int, seed: Int) -> CGImage {
        var bytes = [UInt8](repeating: 255, count: w * h * 4)
        for y in 0..<h {
            for x in 0..<w {
                var v = 0.0, a = 0.5, total = 0.0
                for o in 0..<octaves {
                    let f = Double(1 << o)
                    let px = max(1, Int((Double(w) * fx * f).rounded()))
                    let py = max(1, Int((Double(h) * fy * f).rounded()))
                    let gx = Double(x) / Double(w) * Double(px), gy = Double(y) / Double(h) * Double(py)
                    let ix = Int(floor(gx)), iy = Int(floor(gy))
                    let ux = gx - Double(ix), uy = gy - Double(iy)
                    let sx = ux * ux * (3 - 2 * ux), sy = uy * uy * (3 - 2 * uy)
                    let s = seed + o * 101
                    let a0 = hash(ix % px, iy % py, s), a1 = hash((ix + 1) % px, iy % py, s)
                    let b0 = hash(ix % px, (iy + 1) % py, s), b1 = hash((ix + 1) % px, (iy + 1) % py, s)
                    let top = a0 + (a1 - a0) * sx, bottom = b0 + (b1 - b0) * sx
                    v += a * (top + (bottom - top) * sy)
                    total += a
                    a *= 0.5
                }
                let b = UInt8(min(max(0.5 + (v / total - 0.5) * 1.7, 0), 1) * 255)
                let i = (y * w + x) * 4
                bytes[i] = b; bytes[i + 1] = b; bytes[i + 2] = b
            }
        }
        let data = CFDataCreate(nil, bytes, bytes.count)!
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                       space: CGColorSpace(name: CGColorSpace.sRGB)!,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: CGDataProvider(data: data)!, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }

    // MARK: The card

    /// The card's outline: a cut top-left corner and small radii elsewhere (card points).
    static func outline() -> Path {
        let w = M.cardW, h = M.cardH, cut = 16.0
        var p = Path()
        p.move(to: CGPoint(x: cut, y: 0))
        p.addLine(to: CGPoint(x: w - 4, y: 0))
        p.addQuadCurve(to: CGPoint(x: w, y: 4), control: CGPoint(x: w, y: 0))
        p.addLine(to: CGPoint(x: w, y: h - 4))
        p.addQuadCurve(to: CGPoint(x: w - 4, y: h), control: CGPoint(x: w, y: h))
        p.addLine(to: CGPoint(x: 4, y: h))
        p.addQuadCurve(to: CGPoint(x: 0, y: h - 4), control: CGPoint(x: 0, y: h))
        p.addLine(to: CGPoint(x: 0, y: cut * 0.9))
        p.closeSubpath()
        return p
    }

    private static func centred(_ text: String, x: Double, y: Double, font: UIFont, color: UIColor) {
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let w = (text as NSString).size(withAttributes: attrs).width
        (text as NSString).draw(at: CGPoint(x: x - w / 2, y: y - font.ascender), withAttributes: attrs)
    }

    private static func ui(_ hex: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: a)
    }

    /// The blank card: manila stock with a cut corner, printed digits, field rules and labels, grain.
    static func bakeCard(grain: CGImage, fibre: CGImage) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = bake
        format.opaque = false
        format.preferredRange = .standard
        let size = CGSize(width: M.cardW, height: M.cardH)
        let image = UIGraphicsImageRenderer(size: size, format: format).image { r in
            let c = r.cgContext
            let path = outline().cgPath
            c.saveGState()
            c.addPath(path)
            c.clip()
            let space = CGColorSpace(name: CGColorSpace.sRGB)!
            let stock = CGGradient(colorsSpace: space, colors: [ui(0xEADCB6).cgColor, ui(0xE2D0A4).cgColor, ui(0xD5BF8F).cgColor] as CFArray, locations: [0, 0.55, 1])!
            c.drawLinearGradient(stock, start: .zero, end: CGPoint(x: M.cardW * 0.3, y: M.cardH), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            // Paper grain: fine noise plus long faint fibres.
            c.setBlendMode(.multiply)
            c.setAlpha(0.1)
            c.draw(grain, in: CGRect(x: 0, y: 0, width: 100, height: 100), byTiling: true)
            c.setBlendMode(.softLight)
            c.setAlpha(0.35)
            c.draw(fibre, in: CGRect(x: 0, y: 0, width: 150, height: 100), byTiling: true)
            c.setBlendMode(.normal)
            c.setAlpha(1)
            // A printed band along the bottom, and field rules down the card.
            c.setFillColor(ui(0xB4553F, 0.14).cgColor)
            c.fill(CGRect(x: 0, y: M.cardH - 10, width: M.cardW, height: 10))
            c.setStrokeColor(ui(0x9A4433, 0.55).cgColor)
            c.setLineWidth(0.7)
            let label = UIFont(name: "Menlo-Bold", size: 7) ?? .monospacedSystemFont(ofSize: 7, weight: .bold)
            for f in M.fields {
                for col in [f.first, f.first + f.size] {
                    let x = M.colX(Double(col)) - 4.2
                    c.move(to: CGPoint(x: x, y: M.marginTop - 4))
                    c.addLine(to: CGPoint(x: x, y: M.cardH - 10))
                    c.strokePath()
                }
                centred(f.label, x: (M.colX(Double(f.first)) + M.colX(Double(f.first + f.size - 1))) / 2, y: M.cardH - 2.6, font: label, color: ui(0x8A3B2C, 0.85))
            }
            centred("ANALYTICAL SOCIETY · MEMBER CARD · FORM 29-A", x: M.colX(57), y: M.cardH - 2.6, font: label, color: ui(0x8A3B2C, 0.7))
            let digit = UIFont(name: "Menlo-Regular", size: 6.2) ?? .monospacedSystemFont(ofSize: 6.2, weight: .medium)
            let small = UIFont(name: "Menlo-Regular", size: 4.6) ?? .monospacedSystemFont(ofSize: 4.6, weight: .medium)
            let ink = ui(0x5C3A2A, 0.62), faint = ui(0x5C3A2A, 0.5)
            for col in 1...M.cols {
                let x = M.colX(Double(col))
                for d in 0...9 { centred("\(d)", x: x, y: M.rowY(d + 2) + 2.2, font: digit, color: ink) }
                centred("\(col)", x: x, y: M.rowY(2) + 9.6, font: small, color: faint)
                centred("\(col)", x: x, y: M.rowY(11) + 9.6, font: small, color: faint)
            }
            // The top edge's lit lip.
            c.setStrokeColor(UIColor(white: 1, alpha: 0.5).cgColor)
            c.setLineWidth(1)
            c.move(to: CGPoint(x: 16, y: 0.6))
            c.addLine(to: CGPoint(x: M.cardW, y: 0.6))
            c.strokePath()
            c.restoreGState()
            c.setStrokeColor(ui(0x8C7449, 0.6).cgColor)
            c.setLineWidth(0.8)
            c.addPath(path)
            c.strokePath()
        }
        return image.cgImage!
    }

    // MARK: Static pieces drawn with Canvas

    static func lin(_ stops: [(UInt32, Double)], _ a: CGPoint, _ b: CGPoint) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops.map { .init(color: Color(hex: $0.0), location: $0.1) }), startPoint: a, endPoint: b)
    }

    static func blurred(_ ctx: GraphicsContext, _ sigma: Double, _ body: (inout GraphicsContext) -> Void) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            body(&l)
        }
    }

    /// The room: deep green-black enamel with grain, a lamp's warmth and a vignette.
    static func drawRoom(_ ctx: GraphicsContext, size: CGSize, art: Baked?) {
        let w = size.width, h = size.height
        let all = Path(CGRect(origin: .zero, size: size))
        ctx.fill(all, with: lin([(0x1A201C, 0), (0x141915, 0.5), (0x0E120F, 1)], .zero, CGPoint(x: 0, y: h)))
        if let art {
            var g = ctx
            g.blendMode = .softLight
            g.opacity = 0.25
            g.fill(all, with: .tiledImage(Image(decorative: art.grain, scale: 1)))
        }
        ctx.fill(all, with: .radialGradient(Gradient(colors: [.rgba(255, 206, 140, 0.10), .rgba(255, 206, 140, 0)]),
                                            center: CGPoint(x: w * 0.3, y: h * 0.38), startRadius: 0, endRadius: w * 1.1))
        ctx.fill(all, with: .radialGradient(Gradient(stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.55),
                                                             .init(color: .rgba(2, 4, 3, 0.6), location: 1)]),
                                            center: CGPoint(x: w / 2, y: h * 0.45), startRadius: 0, endRadius: h * 0.75))
    }

    /// The bed the card slides on: dark machine enamel with brushed grain and two steel guides.
    static func drawBed(_ ctx: GraphicsContext, art: Baked?) {
        let bed = Path(roundedRect: CGRect(x: 6, y: 6, width: M.designW - 12, height: M.slotY + 4), cornerRadius: 18)
        blurred(ctx, 10) { l in l.fill(bed, with: .color(.black.opacity(0.55))) }
        ctx.fill(bed, with: lin([(0x3A423C, 0), (0x2C332E, 0.5), (0x232925, 1)], CGPoint(x: 0, y: 6), CGPoint(x: 0, y: M.slotY)))
        ctx.drawLayer { c in
            c.clip(to: bed)
            if let art {
                var g = c
                g.blendMode = .overlay
                g.opacity = 0.4
                g.draw(Image(decorative: art.bedBrush, scale: 1), in: CGRect(x: 0, y: 0, width: M.designW, height: 360))
            }
            c.fill(Path(CGRect(x: 0, y: 0, width: M.designW, height: M.canvasH)),
                   with: .radialGradient(Gradient(colors: [.rgba(255, 214, 150, 0.16), .rgba(255, 214, 150, 0)]),
                                         center: CGPoint(x: M.stationX + 40, y: M.cardY + M.cardH * 0.45), startRadius: 0, endRadius: 260))
        }
        ctx.stroke(bed, with: .color(Color(hex: 0x9FB0A4).opacity(0.18)), lineWidth: 1)
        for y in [M.cardY - 9, M.cardY + M.cardH + 2] {
            ctx.fill(Path(roundedRect: CGRect(x: 14, y: y, width: M.designW - 28, height: 7), cornerRadius: 3.5),
                     with: lin([(0xC9D0CC, 0), (0x7E8884, 0.5), (0x4A524E, 1)], CGPoint(x: 0, y: y), CGPoint(x: 0, y: y + 7)))
        }
    }

    /// The reader across the bottom: a slot, an enamel front with a vent, a plate and four lamp bezels.
    static func drawReader(_ ctx: GraphicsContext, art: Baked?) {
        let top = M.slotY
        let h = M.canvasH - top - 4
        let body = Path(roundedRect: CGRect(x: 4, y: top, width: M.designW - 8, height: h), cornerRadius: 16)
        blurred(ctx, 8) { l in
            l.fill(Path(roundedRect: CGRect(x: 4, y: top - 4, width: M.designW - 8, height: h), cornerRadius: 16), with: .color(.black.opacity(0.5)))
        }
        ctx.fill(body, with: lin([(0x58625B, 0), (0x434C46, 0.35), (0x2F3632, 1)], CGPoint(x: 0, y: top), CGPoint(x: 0, y: top + h)))
        ctx.drawLayer { c in
            c.clip(to: body)
            if let art {
                var g = c
                g.blendMode = .overlay
                g.opacity = 0.45
                g.draw(Image(decorative: art.readerBrush, scale: 1), in: CGRect(x: 0, y: top, width: M.designW, height: 110))
            }
            c.fill(Path(CGRect(x: 4, y: top, width: M.designW - 8, height: 10)),
                   with: lin([(0x050605, 0), (0x141815, 0.5), (0x2A302C, 1)], CGPoint(x: 0, y: top), CGPoint(x: 0, y: top + 12)))
            c.fill(Path(CGRect(x: 4, y: top + 10, width: M.designW - 8, height: 1)), with: .color(Color(hex: 0xD7E0DA).opacity(0.35)))
            for i in 0..<6 {
                c.fill(Path(roundedRect: CGRect(x: M.designW - 120 + Double(i) * 16, y: top + 34, width: 8, height: 40), cornerRadius: 4),
                       with: .color(Color(hex: 0x141815).opacity(0.85)))
            }
        }
        ctx.fill(Path(roundedRect: CGRect(x: 24, y: top + 30, width: 148, height: 30), cornerRadius: 4),
                 with: lin([(0xE9CF8A, 0), (0xB98E45, 0.4), (0x7E5A24, 0.75), (0xC9A35C, 1)], CGPoint(x: 24, y: top + 30), CGPoint(x: 170, y: top + 70)))
        let plate = ctx.resolve(Text("CARD READER · MODEL 2").font(.custom("Menlo-Bold", fixedSize: 7)).foregroundStyle(Color(hex: 0x3A2810).opacity(0.9)))
        ctx.draw(plate, at: CGPoint(x: 98, y: top + 48 + 7 * 0.22), anchor: .bottom)
        for i in 0..<4 {
            let x = 36 + Double(i) * 30
            ctx.fill(Path(ellipseIn: CGRect(x: x - 7.5, y: top + 80 - 7.5, width: 15, height: 15)),
                     with: .radialGradient(Gradient(stops: [.init(color: Color(hex: 0xD6DDD8), location: 0), .init(color: Color(hex: 0x6D7671), location: 0.6),
                                                            .init(color: Color(hex: 0x262B28), location: 1)]),
                                           center: CGPoint(x: x - 1, y: top + 79), startRadius: 0, endRadius: 9))
            ctx.fill(Path(ellipseIn: CGRect(x: x - 5, y: top + 80 - 5, width: 10, height: 10)), with: .color(Color(hex: 0x101311)))
        }
    }

    /// The punch head: a steel die block with a red column pointer, centred on (40, 40).
    static func drawHead(_ ctx: GraphicsContext) {
        var c = ctx
        c.translateBy(x: 40, y: 40)
        blurred(c, 4) { l in l.fill(Path(roundedRect: CGRect(x: -17, y: -22, width: 34, height: 24), cornerRadius: 5), with: .color(.black.opacity(0.55))) }
        c.fill(Path(roundedRect: CGRect(x: -17, y: -26, width: 34, height: 24), cornerRadius: 5),
               with: lin([(0x5E6763, 0), (0xD5DCD8, 0.35), (0x9AA39F, 0.65), (0x4C5450, 1)], CGPoint(x: -17, y: 0), CGPoint(x: 17, y: 0)))
        var hi = Path()
        hi.move(to: CGPoint(x: -13, y: -25.4))
        hi.addLine(to: CGPoint(x: 13, y: -25.4))
        c.stroke(hi, with: .color(.white.opacity(0.5)), lineWidth: 1)
        var tri = Path()
        tri.move(to: CGPoint(x: -4, y: -4))
        tri.addLine(to: CGPoint(x: 4, y: -4))
        tri.addLine(to: CGPoint(x: 0, y: 1))
        tri.closeSubpath()
        c.fill(tri, with: .color(Color(hex: 0xD8483A)))
    }
}
