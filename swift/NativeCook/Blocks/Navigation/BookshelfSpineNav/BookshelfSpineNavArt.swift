import SwiftUI
import UIKit

/// The room and the shelf, drawn once with Canvas behind every view; grain made off the main thread.
enum BookshelfSpineNavArt {
    typealias M = BookshelfSpineNavMotion

    struct Baked {
        let fine: CGImage
        let panelGrain: CGImage
        let boardGrain: CGImage
    }

    static let shelfTop = 462.0
    static let boardH = 30.0

    static let shared: Task<Baked, Never> = Task.detached(priority: .userInitiated) {
        Baked(fine: noise(w: 100, h: 100, fx: 0.8, fy: 0.8, octaves: 2, seed: 4),
              panelGrain: noise(w: 400, h: 240, fx: 0.005, fy: 0.18, octaves: 3, seed: 9),
              boardGrain: noise(w: 400, h: 40, fx: 0.004, fy: 0.35, octaves: 3, seed: 23))
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

    static func lin(_ stops: [(Color, Double)], _ a: CGPoint, _ b: CGPoint) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops.map { .init(color: $0.0, location: $0.1) }), startPoint: a, endPoint: b)
    }

    static func hexes(_ v: [UInt32], _ locs: [Double]) -> [(Color, Double)] { zip(v, locs).map { (Color(hex: $0.0), $0.1) } }

    private static func grain(_ ctx: GraphicsContext, _ r: CGRect, _ image: CGImage?, _ alpha: Double) {
        guard let image else { return }
        var g = ctx
        g.clip(to: Path(r))
        g.blendMode = .overlay
        g.opacity = alpha
        g.draw(Image(decorative: image, scale: 1), in: r)
    }

    private static func blurred(_ ctx: GraphicsContext, _ sigma: Double, _ body: (inout GraphicsContext) -> Void) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            body(&l)
        }
    }

    /// The wall, the shelf's back panel, its uprights and the board the books stand on.
    static func drawRoom(_ ctx: GraphicsContext, size: CGSize, ox: Double, oy: Double, k: Double, art: Baked?) {
        let w = size.width, h = size.height
        let all = Path(CGRect(origin: .zero, size: size))
        ctx.fill(all, with: lin(hexes([0x231B16, 0x1B1511, 0x120E0B], [0, 0.5, 1]), .zero, CGPoint(x: 0, y: h)))
        if let art {
            var g = ctx
            g.blendMode = .softLight
            g.opacity = 0.22
            g.fill(all, with: .tiledImage(Image(decorative: art.fine, scale: 1)))
        }
        ctx.fill(all, with: .radialGradient(Gradient(stops: [.init(color: .rgba(255, 196, 130, 0.16), location: 0), .init(color: .rgba(255, 180, 110, 0.05), location: 0.5),
                                                             .init(color: .rgba(255, 180, 110, 0), location: 1)]),
                                            center: CGPoint(x: w * 0.62, y: oy + 140 * k), startRadius: 0, endRadius: w * 0.95))
        var c = ctx
        c.translateBy(x: ox, y: oy)
        c.scaleBy(x: k, y: k)
        let left = 4.0, right = M.designW - 4
        let top = shelfTop, board = M.boardY, bh = boardH
        blurred(c, 12) { l in
            l.fill(Path(CGRect(x: left - 6, y: top - 4, width: right - left + 12, height: board + bh - top + 22)), with: .color(.black.opacity(0.55)))
        }
        let panel = CGRect(x: left, y: top, width: right - left, height: board - top)
        c.fill(Path(panel), with: lin(hexes([0x140D08, 0x24170E, 0x2D1D12], [0, 0.45, 1]), CGPoint(x: 0, y: top), CGPoint(x: 0, y: board)))
        grain(c, panel, art?.panelGrain, 0.5)
        var p = c
        p.clip(to: Path(panel))
        p.fill(Path(panel), with: .radialGradient(Gradient(colors: [.rgba(255, 190, 120, 0.12), .rgba(255, 190, 120, 0)]),
                                                   center: CGPoint(x: M.designW * 0.55, y: board - 20), startRadius: 0, endRadius: 260))
        c.fill(Path(CGRect(x: left, y: top, width: right - left, height: 70)),
               with: lin([(.black.opacity(0.75), 0), (.black.opacity(0.3), 0.5), (.black.opacity(0), 1)], CGPoint(x: 0, y: top), CGPoint(x: 0, y: top + 70)))
        let above = CGRect(x: left - 4, y: top - 16, width: right - left + 8, height: 16)
        c.fill(Path(above), with: lin(hexes([0x6B4A2F, 0x553823, 0x3A2617], [0, 0.5, 1]), CGPoint(x: 0, y: top - 16), CGPoint(x: 0, y: top)))
        grain(c, above, art?.boardGrain, 0.6)
        c.fill(Path(CGRect(x: left - 4, y: top - 16, width: right - left + 8, height: 1)), with: .color(Color(hex: 0xE2B17E).opacity(0.35)))

        blurred(c, 8) { l in l.fill(Path(CGRect(x: left - 8, y: board + 6, width: right - left + 16, height: bh + 4)), with: .color(.black.opacity(0.6))) }
        let boardRect = CGRect(x: left - 8, y: board, width: right - left + 16, height: bh)
        c.fill(Path(boardRect), with: lin(hexes([0x9A6C45, 0x7A5233, 0x5A3B23, 0x40291A], [0, 0.18, 0.7, 1]), CGPoint(x: 0, y: board), CGPoint(x: 0, y: board + bh)))
        grain(c, boardRect, art?.boardGrain, 0.7)
        c.fill(Path(CGRect(x: left - 8, y: board + 5, width: right - left + 16, height: 1)), with: .color(Color(hex: 0xF2C796).opacity(0.45)))
        c.fill(Path(CGRect(x: left - 8, y: board, width: right - left + 16, height: 5)),
               with: lin(hexes([0xC08A5A, 0x8E6240], [0, 1]), CGPoint(x: 0, y: board), CGPoint(x: 0, y: board + 5)))
        c.fill(Path(CGRect(x: left, y: board - 2, width: right - left, height: 6)),
               with: lin([(.rgba(10, 5, 2, 0), 0), (.rgba(10, 5, 2, 0.55), 0.5), (.rgba(10, 5, 2, 0), 1)], CGPoint(x: 0, y: board - 2), CGPoint(x: 0, y: board + 4)))
        for x in [left - 10, right] {
            c.fill(Path(CGRect(x: x, y: top - 16, width: 10, height: board + bh - top + 16)),
                   with: lin(hexes([0x4A3220, 0x6E4B30, 0x3A2717], [0, 0.5, 1]), CGPoint(x: x, y: 0), CGPoint(x: x + 10, y: 0)))
        }
        c.fill(Path(CGRect(x: left - 8, y: board + bh - 1, width: right - left + 16, height: 1)), with: .color(.black.opacity(0.4)))

        ctx.fill(all, with: .radialGradient(Gradient(stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.55),
                                                             .init(color: .rgba(6, 3, 2, 0.6), location: 1)]),
                                            center: CGPoint(x: w / 2, y: h * 0.45), startRadius: 0, endRadius: h * 0.75))
    }
}
