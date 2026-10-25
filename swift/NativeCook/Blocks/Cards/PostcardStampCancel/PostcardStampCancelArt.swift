import SwiftUI
import UIKit

/// Static art of the Postcard Stamp Cancel: grain tiles (made once off the main thread), the desk,
/// the rubber face the ink shader reads, the handstamp seen from above, the stamp and the photo.
enum PostcardStampCancelArt {
    typealias M = PostcardStampCancelMotion

    struct Baked {
        let weave: CGImage
        let weft: CGImage
        let fibres: CGImage
        let woodGrain: CGImage
        let photo: UIImage?
    }

    /// Made once; the view starts its loop on the first frame that has it.
    static let shared: Task<Baked, Never> = Task.detached(priority: .userInitiated) { make() }

    static func make() -> Baked {
        let photo = Bundle.main.url(forResource: "PostcardStampCancel-lisbon", withExtension: "jpg")
            .flatMap { UIImage(contentsOfFile: $0.path) }
            .map { $0.preparingForDisplay() ?? $0 }
        return Baked(
            weave: noise(w: 100, h: 100, fx: 0.9, fy: 0.08, octaves: 2, seed: 3),
            weft: noise(w: 100, h: 100, fx: 0.08, fy: 0.9, octaves: 2, seed: 5),
            fibres: noise(w: 140, h: 140, fx: 0.7, fy: 0.7, octaves: 3, seed: 41),
            woodGrain: noise(w: 112, h: 72, fx: 0.006, fy: 0.28, octaves: 3, seed: 17),
            photo: photo
        )
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
                var v = 0.0
                var a = 0.5
                var total = 0.0
                for o in 0..<octaves {
                    let f = Double(1 << o)
                    let px = max(1, Int((Double(w) * fx * f).rounded()))
                    let py = max(1, Int((Double(h) * fy * f).rounded()))
                    let gx = Double(x) / Double(w) * Double(px)
                    let gy = Double(y) / Double(h) * Double(py)
                    let ix = Int(floor(gx)), iy = Int(floor(gy))
                    let ux = gx - Double(ix), uy = gy - Double(iy)
                    let sx = ux * ux * (3 - 2 * ux), sy = uy * uy * (3 - 2 * uy)
                    let s = seed + o * 101
                    let a00 = hash(ix % px, iy % py, s), a10 = hash((ix + 1) % px, iy % py, s)
                    let a01 = hash(ix % px, (iy + 1) % py, s), a11 = hash((ix + 1) % px, (iy + 1) % py, s)
                    let n = (a00 + (a10 - a00) * sx) + ((a01 + (a11 - a01) * sx) - (a00 + (a10 - a00) * sx)) * sy
                    v += a * n
                    total += a
                    a *= 0.5
                }
                let g = min(max(0.5 + (v / total - 0.5) * 1.7, 0), 1)
                let b = UInt8(g * 255)
                let i = (y * w + x) * 4
                bytes[i] = b; bytes[i + 1] = b; bytes[i + 2] = b
            }
        }
        let data = CFDataCreate(nil, bytes, bytes.count)!
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                       space: CGColorSpace(name: CGColorSpace.sRGB)!,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: CGDataProvider(data: data)!, decode: nil, shouldInterpolate: true,
                       intent: .defaultIntent)!
    }

    static func tile(_ image: CGImage) -> GraphicsContext.Shading {
        .tiledImage(Image(decorative: image, scale: 1), origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1)
    }

    // MARK: Helpers

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color { Color.rgba(r, g, b, a) }

    static func blurred(_ ctx: GraphicsContext, _ sigma: Double, _ body: (inout GraphicsContext) -> Void) {
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: sigma))
            body(&layer)
        }
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    static func radial(_ x: Double, _ y: Double, _ r: Double, _ stops: [Gradient.Stop]) -> GraphicsContext.Shading {
        .radialGradient(Gradient(stops: stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ stops: [Gradient.Stop]) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func even(_ colors: [Color]) -> [Gradient.Stop] {
        colors.enumerated().map { Gradient.Stop(color: $0.element, location: Double($0.offset) / Double(max(1, colors.count - 1))) }
    }

    // MARK: The desk

    /// Indigo linen with a weave, a lamp, a spare card under the postcard, the ink pad in its tin.
    static func drawDesk(_ ctx: GraphicsContext, size: CGSize, ox: Double, oy: Double, k: Double, art: Baked?) {
        let w = size.width, h = size.height
        let all = Path(CGRect(origin: .zero, size: size))
        ctx.fill(all, with: linear(0, 0, w * 0.4, h, [
            .init(color: Color(hex: 0x1E3150), location: 0), .init(color: Color(hex: 0x18263F), location: 0.5),
            .init(color: Color(hex: 0x101A2C), location: 1),
        ]))
        if let art {
            var g = ctx
            g.blendMode = .softLight
            g.opacity = 0.35
            g.fill(all, with: tile(art.weave))
            g.fill(all, with: tile(art.weft))
        }
        ctx.fill(all, with: radial(w * 0.62, oy + 200 * k, w, [
            .init(color: rgba(255, 214, 160, 0.14), location: 0), .init(color: rgba(255, 214, 160, 0.04), location: 0.5),
            .init(color: rgba(255, 214, 160, 0), location: 1),
        ]))

        var d = ctx
        d.translateBy(x: ox, y: oy)
        d.scaleBy(x: k, y: k)
        // A spare card peeking out under the postcard, turned a little.
        var s = d
        s.translateBy(x: M.card.midX, y: M.card.midY + 10)
        s.rotate(by: .degrees(-3.2))
        let cw = M.card.width, ch = M.card.height
        blurred(s, 10) { l in
            l.fill(Path(roundedRect: CGRect(x: -cw / 2 + 2, y: -ch / 2 + 8, width: cw, height: ch), cornerRadius: 8),
                   with: .color(.black.opacity(0.45)))
        }
        s.fill(Path(roundedRect: CGRect(x: -cw / 2, y: -ch / 2, width: cw, height: ch), cornerRadius: 6),
               with: linear(0, -ch / 2, 0, ch / 2, even([Color(hex: 0xE7DCC4), Color(hex: 0xDCCDB0)])))

        // The ink pad: the lid set aside, the tin rim, a felt pad soaked in ultramarine, a sheen.
        let x = M.pad.x, y = M.pad.y, r = M.pad.r
        blurred(d, 9) { l in l.fill(circle(x + 64, y + 52, r - 2), with: .color(.black.opacity(0.5))) }
        d.fill(circle(x + 62, y + 48, r - 4), with: radial(x + 44, y + 30, r * 1.3, [
            .init(color: Color(hex: 0xD9DEE6), location: 0), .init(color: Color(hex: 0x8E97A6), location: 0.55),
            .init(color: Color(hex: 0x566072), location: 1),
        ]))
        d.stroke(circle(x + 62, y + 48, r - 12), with: .color(.white.opacity(0.35)), lineWidth: 1.2)
        blurred(d, 10) { l in l.fill(circle(x + 3, y + 8, r + 2), with: .color(.black.opacity(0.55))) }
        blurred(d, 1.5) { l in l.fill(circle(x, y + 1.5, r + 1), with: .color(.black.opacity(0.6))) }
        d.fill(circle(x, y, r), with: .conicGradient(
            Gradient(colors: [Color(hex: 0xC8CFDA), Color(hex: 0x6E7889), Color(hex: 0xE8ECF2), Color(hex: 0x596374), Color(hex: 0xC8CFDA)]),
            center: CGPoint(x: x, y: y), angle: .zero))
        let feltPath = circle(x, y, r - 7)
        d.fill(feltPath, with: radial(x - 14, y - 16, r * 1.1, [
            .init(color: Color(hex: 0x34479A), location: 0), .init(color: Color(hex: 0x22306E), location: 0.55),
            .init(color: Color(hex: 0x151D45), location: 1),
        ]))
        d.drawLayer { f in
            f.clip(to: feltPath)
            if let art {
                var g = f
                g.blendMode = .overlay
                g.opacity = 0.55
                g.draw(Image(decorative: art.fibres, scale: 1), in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
            f.fill(circle(x - 18, y - 22, 26), with: radial(x - 18, y - 22, 26, even([rgba(190, 205, 255, 0.35), rgba(190, 205, 255, 0)])))
            blurred(f, 3) { l in l.stroke(feltPath, with: .color(.black.opacity(0.45)), lineWidth: 6) }
        }

        ctx.fill(all, with: radial(w / 2, h * 0.45, h * 0.75, [
            .init(color: .clear, location: 0), .init(color: .clear, location: 0.55),
            .init(color: rgba(3, 6, 14, 0.6), location: 1),
        ]))
    }

    // MARK: The rubber face

    /// Glyphs set on a circle, centred on `mid` (radians), upright for the reader.
    private static func ringText(_ ctx: GraphicsContext, _ text: String, cx: Double, cy: Double, r: Double, mid: Double, bottom: Bool) {
        let font = Font.system(size: 8.4, weight: .bold)
        let glyphs = text.map { ctx.resolve(Text(String($0)).font(font).foregroundStyle(.white)) }
        let widths = glyphs.map { Double($0.measure(in: CGSize(width: 100, height: 100)).width) }
        let track = 1.4
        let total = widths.reduce(0) { $0 + $1 + track } - track
        let dir = bottom ? -1.0 : 1.0
        var a = mid - dir * total / 2 / r
        for (i, g) in glyphs.enumerated() {
            let w = widths[i]
            let at = a + dir * w / 2 / r
            var c = ctx
            c.translateBy(x: cx + cos(at) * r, y: cy + sin(at) * r)
            c.rotate(by: .radians(at + (bottom ? -Double.pi / 2 : Double.pi / 2)))
            c.draw(g, at: .zero, anchor: .center)
            a += dir * (w + track) / r
        }
    }

    /// The postmark's rubber face in white on clear: a date ring "LISBOA · 24 OCT" and five wavy bars.
    static func drawMask(_ ctx: GraphicsContext) {
        let ring = M.ring
        let white = GraphicsContext.Shading.color(.white)
        ctx.stroke(circle(ring.x, ring.y, ring.r), with: white, lineWidth: 2.2)
        ctx.stroke(circle(ring.x, ring.y, ring.inner), with: white, lineWidth: 1.5)
        let mid = (ring.r + ring.inner) / 2
        ringText(ctx, "LISBOA", cx: ring.x, cy: ring.y, r: mid, mid: -Double.pi / 2, bottom: false)
        ringText(ctx, "24 · OCT · 26", cx: ring.x, cy: ring.y, r: mid, mid: Double.pi / 2, bottom: true)
        for side in [-1.0, 1.0] {
            let sx = ring.x + side * mid, sy = ring.y
            var star = Path()
            for k in 0..<10 {
                let rr = k % 2 == 0 ? 2.6 : 1.1
                let a = -Double.pi / 2 + Double(k) * Double.pi / 5
                let p = CGPoint(x: sx + cos(a) * rr, y: sy + sin(a) * rr)
                if k == 0 { star.move(to: p) } else { star.addLine(to: p) }
            }
            star.closeSubpath()
            ctx.fill(star, with: white)
        }
        // The hour, centred on the ring (its baseline 3.6 pt below the centre).
        let hour = ctx.resolve(Text("13·40").font(.system(size: 10, weight: .bold)).foregroundStyle(.white))
        ctx.draw(hour, at: CGPoint(x: ring.x, y: ring.y + 3.6 - 10 * 0.36), anchor: .center)
        for i in 0..<5 {
            let y0 = ring.y + Double(i - 2) * 9.4
            let reach = sqrt(max(0, ring.r * ring.r - (y0 - ring.y) * (y0 - ring.y)))
            let x0 = ring.x + reach + 4
            var path = Path()
            var x = x0
            while x <= M.inkBox.width - 4 {
                let y = y0 + sin((x - x0) / 18 * Double.pi * 2) * 2.6
                if x == x0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                x += 1.5
            }
            ctx.stroke(path, with: white, lineWidth: 2.3)
        }
    }

    // MARK: The handstamp

    static let toolPad = 6.0

    /// The handstamp from above: a rubber-faced block on a walnut mount, a turned knob, its edges lit.
    static func drawTool(_ ctx: GraphicsContext, art: Baked?) {
        var c = ctx
        c.translateBy(x: toolPad, y: toolPad)
        let tw = M.tool.w, th = M.tool.h
        c.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: tw, height: th), cornerRadius: 12),
               with: linear(0, 0, 0, th, even([Color(hex: 0x2B2E3A), Color(hex: 0x1B1D26), Color(hex: 0x11131A)])))
        let mount = Path(roundedRect: CGRect(x: 4, y: 3, width: tw - 8, height: th - 8), cornerRadius: 10)
        c.fill(mount, with: linear(0, 0, tw, th, [
            .init(color: Color(hex: 0x9A6740), location: 0), .init(color: Color(hex: 0x7A4D2D), location: 0.4),
            .init(color: Color(hex: 0x5E3A21), location: 0.75), .init(color: Color(hex: 0x7C5132), location: 1),
        ]))
        c.drawLayer { m in
            m.clip(to: mount)
            if let art {
                var g = m
                g.blendMode = .overlay
                g.opacity = 0.7
                g.draw(Image(decorative: art.woodGrain, scale: 1), in: CGRect(x: 0, y: 0, width: tw, height: th))
            }
            m.fill(Path(CGRect(x: 0, y: 3, width: tw, height: 11)),
                   with: linear(0, 3, 0, 14, even([rgba(255, 226, 190, 0.45), rgba(255, 226, 190, 0)])))
        }
        c.fill(Path(roundedRect: CGRect(x: 12, y: th - 26, width: 36, height: 12), cornerRadius: 2),
               with: linear(12, th - 24, 52, th - 12, even([Color(hex: 0xF1D592), Color(hex: 0xB98A3E), Color(hex: 0xE3C27A)])))
        let kx = tw / 2 + 6, ky = th / 2 - 2, kr = M.tool.knob
        blurred(c, 5) { l in l.fill(circle(kx + 2, ky + 4, kr), with: .color(.black.opacity(0.5))) }
        c.fill(circle(kx, ky, kr), with: radial(kx - 9, ky - 11, kr * 1.5, [
            .init(color: Color(hex: 0xE3B07A), location: 0), .init(color: Color(hex: 0xA86D3E), location: 0.35),
            .init(color: Color(hex: 0x6E4223), location: 0.75), .init(color: Color(hex: 0x4A2B16), location: 1),
        ]))
        for rr in [kr * 0.42, kr * 0.7, kr - 1.5] {
            c.stroke(circle(kx, ky, rr), with: .color(Color(hex: 0x3A2212).opacity(0.45)), lineWidth: 1)
        }
        c.fill(circle(kx - 10, ky - 12, 12), with: radial(kx - 10, ky - 12, 12, even([rgba(255, 240, 215, 0.75), rgba(255, 240, 215, 0)])))
    }

    // MARK: The stamp

    /// A colourful invented stamp: a yellow tram climbing past tiles at sunset, with perforations.
    static func drawStamp(_ ctx: GraphicsContext) {
        let w = 70.0, h = 84.0
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .color(Color(hex: 0xFBF7EE)))
        let paper = GraphicsContext.Shading.color(Color(hex: 0xF4ECDA))
        var x = 3.5
        while x < w { ctx.fill(circle(x, 0, 2.5), with: paper); ctx.fill(circle(x, h, 2.5), with: paper); x += 7 }
        var y = 3.5
        while y < h { ctx.fill(circle(0, y, 2.5), with: paper); ctx.fill(circle(w, y, 2.5), with: paper); y += 7 }
        ctx.drawLayer { c in
            c.clip(to: Path(CGRect(x: 6, y: 6, width: w - 12, height: h - 12)))
            c.fill(Path(CGRect(x: 6, y: 6, width: w - 12, height: h - 12)), with: linear(0, 6, 0, h - 6, [
                .init(color: Color(hex: 0xF6CE7A), location: 0), .init(color: Color(hex: 0xEE8B50), location: 0.55),
                .init(color: Color(hex: 0xC4523F), location: 1),
            ]))
            c.fill(circle(47, 30, 9), with: .color(Color(hex: 0xFFE3A0).opacity(0.9)))
            c.fill(poly([(6, 52), (16, 44), (24, 49), (33, 41), (43, 48), (52, 43), (64, 50), (64, 62), (6, 62)]), with: .color(Color(hex: 0xB4463A)))
            c.fill(poly([(6, 56), (18, 50), (30, 55), (42, 49), (64, 56), (64, 64), (6, 64)]), with: .color(Color(hex: 0x8E3530)))
            c.stroke(poly([(40, 37), (33, 47)], close: false), with: .color(Color(hex: 0x3A2A2A)), lineWidth: 0.8)
            c.fill(Path(roundedRect: CGRect(x: 17, y: 47, width: 32, height: 15), cornerRadius: 3), with: .color(Color(hex: 0xF2C230)))
            c.fill(Path(CGRect(x: 17, y: 56, width: 32, height: 2.2)), with: .color(Color(hex: 0xE3A51E)))
            for wx in [20.0, 27, 34, 41] {
                c.fill(Path(roundedRect: CGRect(x: wx, y: 49.5, width: 5, height: 5), cornerRadius: 1), with: .color(Color(hex: 0xFFF4D6)))
            }
            c.fill(Path(CGRect(x: 6, y: 64, width: w - 12, height: 14)), with: .color(Color(hex: 0x2C5AA6)))
            for tx in [9.0, 21, 33, 45, 57] {
                c.fill(Path(CGRect(x: tx, y: 66, width: 9, height: 9)), with: .color(Color(hex: 0xF4F1E8)))
                c.fill(circle(tx + 4.5, 70.5, 3), with: .color(Color(hex: 0x2C5AA6)))
                c.fill(circle(tx + 4.5, 70.5, 1.2), with: .color(Color(hex: 0xF4F1E8)))
            }
            let cream = Color(hex: 0xFFF6E2)
            let name = c.resolve(Text("POSTA LUSA").font(.system(size: 6.2, weight: .bold)).kerning(0.6).foregroundStyle(cream))
            c.draw(name, at: CGPoint(x: 9, y: 15 + 6.2 * 0.22), anchor: .bottomLeading)
            let price = c.resolve(Text("0,85 €").font(.system(size: 8, weight: .heavy)).foregroundStyle(cream))
            c.draw(price, at: CGPoint(x: w - 9, y: 42 + 8 * 0.22), anchor: .bottomTrailing)
        }
    }

    static func poly(_ pts: [(Double, Double)], close: Bool = true) -> Path {
        var p = Path()
        for (i, q) in pts.enumerated() {
            let pt = CGPoint(x: q.0, y: q.1)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        if close { p.closeSubpath() }
        return p
    }

    /// The bottom-right corner, folded back: a triangle of the photo side with a crease shadow.
    static func drawDogEar(_ ctx: GraphicsContext, photo: UIImage?) {
        let s = 34.0
        let tri = poly([(s, 0), (s, s), (0, s)])
        ctx.fill(tri, with: .color(Color(hex: 0x1E3150)))
        if let photo {
            var c = ctx
            c.clip(to: tri)
            // The photo laid out as the SVG's 110 × 74 box at (-60, -30), cropped to fill (slice).
            let box = CGRect(x: -60, y: -30, width: 110, height: 74)
            let ratio = photo.size.width / photo.size.height
            var r = box
            if ratio > box.width / box.height {
                r.size.width = box.height * ratio
                r.origin.x = box.midX - r.width / 2
            } else {
                r.size.height = box.width / ratio
                r.origin.y = box.midY - r.height / 2
            }
            c.draw(Image(uiImage: photo), in: r)
        }
        ctx.fill(poly([(s, 0), (0, s), (4, s), (s, 4)]), with: .linearGradient(
            Gradient(stops: [
                .init(color: .black.opacity(0), location: 0.42), .init(color: .black.opacity(0.35), location: 0.5),
                .init(color: .black.opacity(0), location: 0.56),
            ]), startPoint: CGPoint(x: 0, y: s), endPoint: CGPoint(x: s, y: 0)))
        ctx.stroke(poly([(s, 0), (0, s)], close: false), with: .color(.white.opacity(0.7)), lineWidth: 0.8)
    }
}
