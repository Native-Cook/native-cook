import SwiftUI
import UIKit

/// Static art for the Card Catalog List: wood grain made once off the main thread, the desk and the
/// drawer's inside (behind the cards), and the drawer's front panel (over the cards' far ends),
/// drawn with Canvas in views that never redraw.
enum CardCatalogListArt {
    typealias M = CardCatalogListMotion

    struct Baked {
        /// Long grain, stretched over whatever it covers.
        let grain: CGImage
        /// Fine fibres, tiled.
        let fine: CGImage
    }

    static let shared: Task<Baked, Never> = Task.detached(priority: .userInitiated) {
        Baked(grain: noise(w: 400, h: 400, fx: 0.004, fy: 0.24, octaves: 3, seed: 3),
              fine: noise(w: 100, h: 100, fx: 0.9, fy: 0.9, octaves: 2, seed: 10))
    }

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

    static func lin(_ colors: [Color], _ locs: [Double], _ a: CGPoint, _ b: CGPoint) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: zip(colors, locs).map { .init(color: $0.0, location: $0.1) }), startPoint: a, endPoint: b)
    }

    static func hex(_ v: [UInt32]) -> [Color] { v.map { Color(hex: $0) } }

    static func blurred(_ ctx: GraphicsContext, _ sigma: Double, _ body: (inout GraphicsContext) -> Void) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            body(&l)
        }
    }

    /// Wood: a three-stop base, long stretched grain and fine fibres.
    static func wood(_ ctx: GraphicsContext, _ r: CGRect, _ base: [UInt32], _ grainAlpha: Double, art: Baked?) {
        ctx.fill(Path(r), with: lin(hex(base), [0, 0.55, 1], CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.minX, y: r.maxY)))
        guard let art else { return }
        var g = ctx
        g.clip(to: Path(r))
        g.blendMode = .overlay
        g.opacity = grainAlpha
        g.draw(Image(decorative: art.grain, scale: 1), in: r)
        g.blendMode = .softLight
        g.opacity = 0.18
        g.fill(Path(r), with: .tiledImage(Image(decorative: art.fine, scale: 1)))
    }

    /// The desk around the drawer and the drawer's dark inside, where the cards stand.
    static func drawBackdrop(_ ctx: GraphicsContext, size: CGSize, ox: Double, oy: Double, k: Double, art: Baked?) {
        let w = size.width, h = size.height
        wood(ctx, CGRect(origin: .zero, size: size), [0x3A281C, 0x2B1D14, 0x1C130D], 0.55, art: art)
        var c = ctx
        c.translateBy(x: ox, y: oy)
        c.scaleBy(x: k, y: k)
        let d = M.drawer
        let inside = Path(roundedRect: CGRect(x: d.x, y: d.y, width: d.w, height: d.bottom - d.y), cornerRadius: 10)
        blurred(c, 14) { l in
            l.fill(Path(roundedRect: CGRect(x: d.x - 4, y: d.y + 6, width: d.w + 8, height: d.bottom - d.y), cornerRadius: 14),
                   with: .color(Color(hex: 0x0A0604).opacity(0.7)))
        }
        c.fill(inside, with: lin(hex([0x140C07, 0x22150D, 0x2E1D12]), [0, 0.6, 1], CGPoint(x: 0, y: d.y), CGPoint(x: 0, y: d.bottom)))
        c.drawLayer { i in
            i.clip(to: inside)
            let wall = 12.0
            wood(i, CGRect(x: d.x, y: d.y, width: wall, height: d.bottom - d.y), [0x5A3F2B, 0x4A3323, 0x3B281B], 0.5, art: art)
            wood(i, CGRect(x: d.x + d.w - wall, y: d.y, width: wall, height: d.bottom - d.y), [0x4A3323, 0x3B281B, 0x2E1F15], 0.5, art: art)
            i.fill(Path(CGRect(x: d.x, y: d.y, width: d.w, height: 90)),
                   with: lin([.rgba(4, 2, 1, 0.85), .rgba(4, 2, 1, 0.35), .rgba(4, 2, 1, 0)], [0, 0.5, 1], CGPoint(x: 0, y: d.y), CGPoint(x: 0, y: d.y + 90)))
            blurred(i, 8) { l in l.stroke(inside, with: .color(.black.opacity(0.35)), lineWidth: 10) }
        }
        c.stroke(Path(roundedRect: CGRect(x: d.x + 0.5, y: d.y + 0.5, width: d.w - 1, height: d.bottom - d.y - 1), cornerRadius: 10),
                 with: .color(Color(hex: 0xC89A6A).opacity(0.35)), lineWidth: 1)
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            Gradient(stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.55), .init(color: .rgba(8, 4, 2, 0.55), location: 1)]),
            center: CGPoint(x: w / 2, y: h * 0.42), startRadius: 0, endRadius: h * 0.78))
    }

    private static func brass(_ c: GraphicsContext, _ r: CGRect, _ radius: Double) {
        blurred(c, 3) { l in l.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: 2), cornerRadius: radius), with: .color(.black.opacity(0.45))) }
        c.fill(Path(roundedRect: r, cornerRadius: radius),
               with: lin(hex([0xF6DC98, 0xC89A45, 0x8C6326, 0xD7AE5C]), [0, 0.35, 0.7, 1], CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.minX + r.width * 0.4, y: r.maxY)))
        var hi = Path()
        hi.move(to: CGPoint(x: r.minX + radius, y: r.minY + 0.8))
        hi.addLine(to: CGPoint(x: r.maxX - radius, y: r.minY + 0.8))
        c.stroke(hi, with: .color(Color(hex: 0xFFF2C8).opacity(0.6)), lineWidth: 1)
    }

    private static func radial(_ colors: [Color], _ locs: [Double], _ c: CGPoint, _ r: Double) -> GraphicsContext.Shading {
        .radialGradient(Gradient(stops: zip(colors, locs).map { .init(color: $0.0, location: $0.1) }), center: c, startRadius: 0, endRadius: r)
    }

    /// The drawer's front: a bevelled oak panel, a brass label holder and a brass rod knob (front points).
    static func drawFront(_ c: GraphicsContext, art: Baked?) {
        let f = M.front
        let panel = Path(roundedRect: CGRect(x: 0, y: 0, width: f.width, height: f.height), cornerRadius: 10)
        blurred(c, 12) { l in l.fill(Path(roundedRect: CGRect(x: 4, y: 12, width: f.width - 8, height: f.height), cornerRadius: 12), with: .color(.black.opacity(0.45))) }
        blurred(c, 2) { l in l.fill(Path(roundedRect: CGRect(x: 1, y: 2, width: f.width - 2, height: f.height), cornerRadius: 10), with: .color(.black.opacity(0.5))) }
        c.drawLayer { p in
            p.clip(to: panel)
            wood(p, CGRect(x: 0, y: 0, width: f.width, height: f.height), [0x8A5E3B, 0x6E4A2E, 0x55381F], 0.6, art: art)
            p.stroke(Path(roundedRect: CGRect(x: 14.5, y: 12.5, width: f.width - 29, height: f.height - 25), cornerRadius: 6),
                     with: .color(Color(hex: 0xE2B07C).opacity(0.35)), lineWidth: 1.5)
            p.stroke(Path(roundedRect: CGRect(x: 13.5, y: 13.5, width: f.width - 27, height: f.height - 27), cornerRadius: 6),
                     with: .color(Color(hex: 0x2A180C).opacity(0.55)), lineWidth: 1.5)
            p.fill(Path(CGRect(x: 0, y: 0, width: f.width, height: 6)),
                   with: lin([.rgba(255, 214, 170, 0.45), .rgba(255, 214, 170, 0)], [0, 1], .zero, CGPoint(x: 0, y: 6)))
            p.fill(Path(CGRect(x: 0, y: f.height - 8, width: f.width, height: 8)),
                   with: lin([.rgba(20, 10, 4, 0), .rgba(20, 10, 4, 0.5)], [0, 1], CGPoint(x: 0, y: f.height - 8), CGPoint(x: 0, y: f.height)))
        }
        let lw = 172.0, lh = 46.0
        let lx = (f.width - lw) / 2, ly = 18.0
        brass(c, CGRect(x: lx, y: ly, width: lw, height: lh), 5)
        let label = Path(roundedRect: CGRect(x: lx + 6, y: ly + 6, width: lw - 12, height: lh - 12), cornerRadius: 2)
        c.fill(label, with: lin(hex([0xF4ECD8, 0xEADFC4, 0xDCCDAA]), [0, 0.5, 1], CGPoint(x: 0, y: ly + 6), CGPoint(x: 0, y: ly + lh - 6)))
        c.stroke(label, with: .color(Color(hex: 0x5C4320).opacity(0.6)), lineWidth: 1)
        for rx in [lx - 10, lx + lw + 10] {
            let y = ly + lh / 2
            c.fill(Path(ellipseIn: CGRect(x: rx - 3.5, y: y - 3.5, width: 7, height: 7)),
                   with: radial(hex([0xFFF0C0, 0xB88A3C, 0x5E3F14]), [0, 0.55, 1], CGPoint(x: rx - 1, y: y - 1.5), 4.5))
        }
        let pullY = ly + lh + 12
        var pull = Path()
        pull.move(to: CGPoint(x: f.width / 2 - 34, y: pullY))
        pull.addCurve(to: CGPoint(x: f.width / 2 + 34, y: pullY), control1: CGPoint(x: f.width / 2 - 30, y: pullY + 22), control2: CGPoint(x: f.width / 2 + 30, y: pullY + 22))
        pull.closeSubpath()
        blurred(c, 3) { l in
            l.translateBy(x: 0, y: 3)
            l.fill(pull, with: .color(.black.opacity(0.45)))
        }
        c.fill(pull, with: lin(hex([0xF3D58C, 0xB98B3A, 0x7A5520, 0xC79B4B]), [0, 0.45, 0.75, 1],
                               CGPoint(x: f.width / 2 - 34, y: pullY), CGPoint(x: f.width / 2 + 34, y: pullY + 18)))
        let kx = f.width - 34, ky = f.height / 2
        blurred(c, 3) { l in l.fill(Path(ellipseIn: CGRect(x: kx + 1 - 9, y: ky + 3 - 9, width: 18, height: 18)), with: .color(.black.opacity(0.5))) }
        c.fill(Path(ellipseIn: CGRect(x: kx - 9, y: ky - 9, width: 18, height: 18)),
               with: radial(hex([0xFBE7B0, 0xC3933F, 0x6E4A1A]), [0, 0.5, 1], CGPoint(x: kx - 3, y: ky - 4), 12))
        c.fill(Path(ellipseIn: CGRect(x: kx - 2.5, y: ky - 2.5, width: 5, height: 5)), with: .color(Color(hex: 0x3A2610)))
    }
}
