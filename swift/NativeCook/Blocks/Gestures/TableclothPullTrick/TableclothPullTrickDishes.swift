import SwiftUI
import UIKit

/*
 The place settings for six, drawn every frame (a port of clothDishes.ts) in table-plane
 coordinates (each through the plane's affine transform at its spot, so a round plate is a true
 perspective ellipse) or, for upright things (glasses, candles), in screen space from their
 projected base. Every item carries its own jiggle. Soft shadows and glows come from baked
 Gaussian-disc sprites instead of a blur per item per frame.
 */

/// Baked soft discs: a Gaussian-edged disc whose blur is `q` × its radius, dark or white.
struct TableclothPullTrickSprites {
    let dark05: UIImage
    let dark15: UIImage
    let dark50: UIImage
    let white50: UIImage

    static func make(scale: CGFloat) -> TableclothPullTrickSprites {
        func disc(_ q: CGFloat, _ hex: UInt32) -> UIImage {
            let side: CGFloat = 64
            let r0 = side / 2 / (1 + 3 * q)
            return TableclothPullTrickPaint.bake(CGSize(width: side, height: side), scale: scale) { c in
                TableclothPullTrickPaint.fill(c, TableclothPullTrickPaint.circle(side / 2, side / 2, r0), hex, 1, blur: q * r0)
            }
        }
        return TableclothPullTrickSprites(dark05: disc(0.05, 0x2A1508), dark15: disc(0.15, 0x2A1508), dark50: disc(0.5, 0x2A1508), white50: disc(0.5, 0xFFFFFF))
    }

    static let qs: [CGFloat] = [0.05, 0.15, 0.5]

    /// Draws a soft ellipse (centre, radii) from a sprite of blur ratio q.
    static func draw(_ ctx: GraphicsContext, _ image: UIImage, q: CGFloat, cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, opacity: Double) {
        guard opacity > 0.002 else { return }
        var c = ctx
        c.opacity = opacity
        let k = 1 + 3 * q
        c.draw(Image(uiImage: image), in: CGRect(x: cx - rx * k, y: cy - ry * k, width: rx * 2 * k, height: ry * 2 * k))
    }
}

enum TableclothPullTrickDishes {
    private typealias M = TableclothPullTrickMotion
    private typealias P = TableclothPullTrickPaint
    private typealias S = TableclothPullTrickSprites

    /// kind: 0 plate, 1 glass, 2 fork, 3 knife, 4 candle.
    struct Item {
        let kind: Int
        let x: Double
        let y: Double
        let id: Int
    }

    static let settings: [(Double, Double)] = [(-110, 250), (0, 250), (110, 250), (-106, 82), (0, 82), (106, 82)]

    static let items: [Item] = {
        var items: [Item] = []
        for (i, s) in settings.enumerated() {
            items.append(Item(kind: 0, x: s.0, y: s.1, id: i))
            items.append(Item(kind: 1, x: s.0 + 34, y: s.1 + 36, id: 10 + i))
            items.append(Item(kind: 2, x: s.0 - 44, y: s.1, id: 20 + i))
            items.append(Item(kind: 3, x: s.0 + 44, y: s.1 - 4, id: 30 + i))
        }
        items.append(Item(kind: 4, x: -55, y: 168, id: 40))
        items.append(Item(kind: 4, x: 55, y: 162, id: 41))
        // Farthest first (a stable sort, as JavaScript's).
        return items.enumerated().sorted { a, b in
            a.element.y == b.element.y ? a.offset < b.offset : a.element.y > b.element.y
        }.map(\.element)
    }()

    /// Where a dish sits for a pose; the shadow keeps to the table and trails the jiggle.
    private static func placed(_ it: Item, _ p: TableclothPullTrickPose)
        -> (x: Double, y: Double, spin: Double, lean: Double, hop: Double, sx: Double, sy: Double) {
        let j = M.jiggle(p, it.id, it.y)
        let weight = it.kind == 4 ? 0.4 : 1
        let y0 = it.y - p.slide * (0.8 + 0.2 * Double((it.id * 7) % 5) / 4)
        return (it.x + j.0 * weight, y0 + j.1 * weight, j.2 * weight, j.3 * weight + (it.kind == 1 ? p.tip : 0), j.4 * weight,
                it.x + j.0 * weight * 0.4, y0 + j.1 * weight * 0.4)
    }

    private static func gradient(_ stops: [(UInt32, Double, Double)]) -> Gradient {
        Gradient(stops: stops.map { .init(color: P.color($0.0, $0.1), location: $0.2) })
    }

    private static let plateShading = gradient([(0xF6F2EA, 1, 0), (0xFBF9F3, 1, 0.6), (0xE2DACB, 1, 0.66), (0xFAF7F0, 1, 0.82), (0xD8CFBF, 1, 1)])
    private static let napkinShading = gradient([(0xD27A52, 1, 0), (0xC0603A, 1, 0.5), (0x9E4A2A, 1, 1)])
    private static let steelShading = gradient([(0x8D939B, 1, 0), (0xF2F4F6, 1, 0.45), (0xA9AFB7, 1, 1)])
    private static let brassShading = gradient([(0x7A5420, 1, 0), (0xF0CF86, 1, 0.35), (0xA77A34, 1, 0.7), (0x5E3E14, 1, 1)])
    private static let waxShading = gradient([(0xE9DCC2, 1, 0), (0xFFF6E2, 1, 0.4), (0xD2C19F, 1, 1)])
    private static let flameShading = gradient([(0xFF8A2A, 1, 0), (0xFFD36A, 1, 0.4), (0xFFF6D8, 1, 1)])
    private static let glowShading = gradient([(0xFFB35A, 0.32, 0), (0xFF8A3A, 0.1, 0.4), (0xFF8A3A, 0, 1)])
    private static let poolShading = gradient([(0xFFB35A, 0.2, 0), (0xFFB35A, 0.06, 0.45), (0xFFB35A, 0, 1)])
    private static let wineShading = gradient([(0x7A1424, 0.92, 0), (0x4A0812, 0.95, 1)])

    private static func line(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private static func plate(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ sp: TableclothPullTrickSprites,
                              _ x: Double, _ y: Double, _ spin: Double, _ hop: Double, _ sx: Double, _ sy: Double) {
        let lift = CGFloat(M.clamp(hop / 9, 0, 1))
        var s = ctx
        s.concatenate(g.plane(sx, sy, 0))
        S.draw(s, sp.dark15, q: 0.15 + 0.08 * lift, cx: 3, cy: -5, rx: 35 + 2 * lift, ry: 35 + 2 * lift, opacity: Double(0.3 - 0.1 * lift))
        S.draw(s, lift > 0.3 ? sp.dark15 : sp.dark05, q: lift > 0.3 ? 0.15 : 0.05, cx: 1, cy: -1.5, rx: 34.5, ry: 34.5, opacity: Double(0.3 * (1 - lift)))
        var c = ctx
        c.concatenate(g.plane(x, y, 1.6 + hop))
        c.fill(circle(0, 0, 34), with: .radialGradient(plateShading, center: .zero, startRadius: 0, endRadius: 34))
        c.stroke(circle(0, 0, 30.5), with: .color(P.color(0xC9A45A, 0.9)), lineWidth: 0.9)
        S.draw(c, sp.white50, q: 0.5, cx: -10, cy: 12, rx: 9, ry: 9, opacity: 0.55)
        // A folded napkin on the plate: its turn shows the plate's spin.
        c.rotate(by: .radians(spin + 18 * .pi / 180))
        c.fill(Path(roundedRect: CGRect(x: -14, y: -10.5, width: 30, height: 18), cornerRadius: 2.5), with: .color(P.color(0x5A2A12, 0.16)))
        c.fill(Path(roundedRect: CGRect(x: -15, y: -9, width: 30, height: 18), cornerRadius: 2.5),
               with: .linearGradient(napkinShading, startPoint: CGPoint(x: 0, y: -9), endPoint: CGPoint(x: 0, y: 9)))
        c.stroke(line(-15, 1, 15, 1), with: .color(P.color(0x8A3A1E, 0.5)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        c.stroke(line(-14, -7.5, 14, -7.5), with: .color(P.color(0xF0A882, 0.6)), style: StrokeStyle(lineWidth: 0.7, lineCap: .round))
    }

    private static let knifePath: Path = {
        var p = Path(roundedRect: CGRect(x: -1.9, y: -24, width: 3.8, height: 26), cornerRadius: 1.9)
        p.move(to: CGPoint(x: -1.6, y: 2))
        p.addLine(to: CGPoint(x: 1.9, y: 2))
        p.addLine(to: CGPoint(x: 1.9, y: 24))
        p.addQuadCurve(to: CGPoint(x: -1.6, y: 22), control: CGPoint(x: 0, y: 27))
        p.closeSubpath()
        return p
    }()

    private static let forkPath: Path = {
        var p = Path(roundedRect: CGRect(x: -1.9, y: -24, width: 3.8, height: 26), cornerRadius: 1.9)
        p.addRoundedRect(in: CGRect(x: -3.6, y: 2, width: 7.2, height: 6), cornerSize: CGSize(width: 2, height: 2))
        for k in 0..<4 { p.addRect(CGRect(x: -3.6 + CGFloat(k) * 2.2, y: 7, width: 1.2, height: 12)) }
        return p
    }()

    private static func cutlery(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ x: Double, _ y: Double, _ spin: Double,
                                knife: Bool, _ hop: Double, _ sx: Double, _ sy: Double) {
        let shape = knife ? knifePath : forkPath
        var s = ctx
        s.concatenate(g.plane(sx + 1.5, sy - 2.5, 0))
        s.rotate(by: .radians(spin))
        s.fill(shape, with: .color(P.color(0x1A0C04, 0.22)))
        var c = ctx
        c.concatenate(g.plane(x, y, 0.8 + hop))
        c.rotate(by: .radians(spin))
        c.fill(shape, with: .linearGradient(steelShading, startPoint: CGPoint(x: -3, y: 0), endPoint: CGPoint(x: 3, y: 0)))
    }

    private static func glass(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ sp: TableclothPullTrickSprites,
                              _ x: Double, _ y: Double, _ lean: Double, _ hop: Double, _ sx: Double, _ sy: Double) {
        let b = g.project(x, y, hop)
        let s = b.s
        let c = 0.72 * s
        // Shadow on the cloth (it stays down when the glass hops), then the glass leaning about its foot.
        var sh = ctx
        sh.concatenate(g.plane(sx + 4, sy - 10, 0))
        S.draw(sh, sp.dark50, q: 0.5 + CGFloat(hop) * 0.02, cx: 0, cy: -1, rx: 12, ry: 15, opacity: 0.22)
        var l = ctx
        l.translateBy(x: b.x, y: b.y)
        l.rotate(by: .degrees(lean))
        let ry: CGFloat = 0.72
        let foot = Path(ellipseIn: CGRect(x: -11 * s, y: -11 * s * ry, width: 22 * s, height: 22 * s * ry))
        l.fill(foot, with: .linearGradient(gradient([(0xFFFFFF, 0.35, 0), (0xD9E4E8, 0.18, 0.5), (0xFFFFFF, 0.4, 1)]),
                                           startPoint: CGPoint(x: -11 * s, y: 0), endPoint: CGPoint(x: 11 * s, y: 0)))
        l.stroke(foot, with: .color(P.color(0xFFFFFF, 0.5)), lineWidth: 0.8)
        l.fill(Path(CGRect(x: -1.1 * s, y: -26 * c, width: 2.2 * s, height: 26 * c)),
               with: .linearGradient(gradient([(0xFFFFFF, 0.75, 0), (0xBFD0D6, 0.35, 1)]),
                                     startPoint: CGPoint(x: -1.1 * s, y: 0), endPoint: CGPoint(x: 1.1 * s, y: 0)))
        let top = 66 * c
        let bot = 26 * c
        let R = 15 * s
        var bowl = Path()
        bowl.move(to: CGPoint(x: -R, y: -top))
        bowl.addCurve(to: CGPoint(x: 0, y: -bot), control1: CGPoint(x: -R * 1.05, y: -top + 22 * c), control2: CGPoint(x: -R * 0.55, y: -bot))
        bowl.addCurve(to: CGPoint(x: R, y: -top), control1: CGPoint(x: R * 0.55, y: -bot), control2: CGPoint(x: R * 1.05, y: -top + 22 * c))
        bowl.closeSubpath()
        // Wine: clipped to the bowl, its surface kept level against the lean.
        var w = l
        w.clip(to: bowl)
        w.translateBy(x: 0, y: -44 * c)
        w.rotate(by: .degrees(-lean * 0.85))
        w.fill(Path(CGRect(x: -40 * s, y: 0, width: 80 * s, height: 40 * s)),
               with: .linearGradient(wineShading, startPoint: .zero, endPoint: CGPoint(x: 0, y: 20 * c)))
        w.fill(Path(ellipseIn: CGRect(x: -11 * s, y: -3.5 * s * ry, width: 22 * s, height: 7 * s * ry)), with: .color(P.color(0x9C2436, 0.9)))
        l.fill(bowl, with: .color(P.color(0xE8F2F4, 0.14)))
        l.stroke(bowl, with: .color(P.color(0xFFFFFF, 0.55)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round, lineJoin: .round))
        l.stroke(Path(ellipseIn: CGRect(x: -R, y: -top - R * ry * 0.5, width: R * 2, height: R * ry)), with: .color(P.color(0xFFFFFF, 0.75)), lineWidth: 1)
        l.stroke(line(-R * 0.62, -top + 6 * c, -R * 0.45, -bot - 8 * c), with: .color(P.color(0xFFFFFF, 0.6)),
                 style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
    }

    private static func candle(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ sp: TableclothPullTrickSprites,
                               _ x: Double, _ y: Double, _ lean: Double, _ time: Double, _ id: Int) {
        let b = g.project(x, y, 0)
        let s = b.s
        let c = 0.72 * s
        var sh = ctx
        sh.concatenate(g.plane(x + 6, y - 14, 0))
        S.draw(sh, sp.dark50, q: 0.5, cx: 0, cy: -2, rx: 16, ry: 20, opacity: 0.28)
        var l = ctx
        l.translateBy(x: b.x, y: b.y)
        l.rotate(by: .degrees(lean * 0.5))
        let brass = GraphicsContext.Shading.linearGradient(brassShading, startPoint: CGPoint(x: -14 * s, y: 0), endPoint: CGPoint(x: 14 * s, y: 0))
        l.fill(Path(ellipseIn: CGRect(x: -14 * s, y: -14 * s * 0.36, width: 28 * s, height: 28 * s * 0.72)), with: brass)
        l.fill(Path(CGRect(x: -3 * s, y: -30 * c, width: 6 * s, height: 30 * c)), with: brass)
        l.fill(Path(ellipseIn: CGRect(x: -8 * s, y: -32 * c - 4 * s, width: 16 * s, height: 8 * s)), with: brass)
        l.fill(Path(CGRect(x: -5 * s, y: -96 * c, width: 10 * s, height: 64 * c)),
               with: .linearGradient(waxShading, startPoint: CGPoint(x: -5 * s, y: 0), endPoint: CGPoint(x: 5 * s, y: 0)))
        l.fill(Path(ellipseIn: CGRect(x: -5 * s, y: -96 * c - 2 * s, width: 10 * s, height: 4 * s)), with: .color(P.color(0xFFF1D2)))
        // Flame: a teardrop that breathes and leans with whole-loop sines.
        let tau = 2 * Double.pi
        let flick = CGFloat(1 + 0.08 * sin(tau * 27 * time / M.loop + Double(id)) + 0.05 * sin(tau * 41 * time / M.loop + 1 + Double(id)))
        let sway = CGFloat(1.2 * sin(tau * 7 * time / M.loop + Double(id) * 2))
        let fy = -96 * c - 3 * s
        let fh = 15 * s * flick
        var flame = Path()
        flame.move(to: CGPoint(x: 0, y: fy))
        flame.addCurve(to: CGPoint(x: sway, y: fy - fh), control1: CGPoint(x: -4.5 * s, y: fy - fh * 0.3), control2: CGPoint(x: -1.5 * s + sway, y: fy - fh * 0.8))
        flame.addCurve(to: CGPoint(x: 0, y: fy), control1: CGPoint(x: 1.5 * s + sway, y: fy - fh * 0.8), control2: CGPoint(x: 4.5 * s, y: fy - fh * 0.3))
        flame.closeSubpath()
        l.fill(flame, with: .linearGradient(flameShading, startPoint: CGPoint(x: 0, y: fy), endPoint: CGPoint(x: 0, y: fy - fh)))
        l.fill(Path(ellipseIn: CGRect(x: -1.3 * s, y: fy - 4 * s, width: 2.6 * s, height: 4 * s)), with: .color(P.color(0x3A6AD8, 0.4)))
        var glow = l
        glow.blendMode = .plusLighter
        let gr = 70 * s * flick
        glow.fill(circle(0, fy - fh * 0.5, gr), with: .radialGradient(glowShading, center: CGPoint(x: 0, y: fy - fh * 0.5), startRadius: 0, endRadius: gr))
    }

    /// Candlelight pooled on the cloth, under the dishes.
    static func candlePools(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, time: Double) {
        let tau = 2 * Double.pi
        for i in 0..<2 {
            let flick = CGFloat(1 + 0.06 * sin(tau * 27 * time / M.loop + 40 + Double(i)))
            var c = ctx
            c.concatenate(g.plane(i == 0 ? -55 : 55, i == 0 ? 168 : 162, 0))
            c.blendMode = .plusLighter
            let r = 150 * flick
            c.fill(circle(0, 0, r), with: .radialGradient(poolShading, center: .zero, startRadius: 0, endRadius: r))
        }
    }

    static func draw(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ sp: TableclothPullTrickSprites, _ p: TableclothPullTrickPose) {
        for it in items {
            let q = placed(it, p)
            switch it.kind {
            case 0: plate(ctx, g, sp, q.x, q.y, q.spin, q.hop, q.sx, q.sy)
            case 1: glass(ctx, g, sp, q.x, q.y, M.clamp(q.lean, -24, 24), q.hop, q.sx, q.sy)
            case 2, 3: cutlery(ctx, g, q.x, q.y, q.spin * 1.6, knife: it.kind == 3, q.hop, q.sx, q.sy)
            default: candle(ctx, g, sp, q.x, q.y, q.lean, p.time, it.id)
            }
        }
    }
}
