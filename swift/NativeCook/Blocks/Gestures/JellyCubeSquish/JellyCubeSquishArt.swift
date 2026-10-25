import CoreGraphics
import SwiftUI
import UIKit

/*
 The jelly's art (a port of jellyArt.ts). Baked once, off the main thread: the dessert bar
 backdrop (a blush wall, a marble counter) and the ceramic plate. Per frame: the cube's contact
 shadow and coloured caustic on the plate (Canvas); the cube itself as vertex-coloured meshes in
 Metal (far faces, an inner glow, suspended bubbles, the near faces lit per vertex — diffuse, a
 denser core, a Fresnel rim — and an additive specular mesh); then the reflected window, the
 streak, the demo finger, the flavour's name and the chips' highlight (Canvas).
 */

struct JellyCubeSquishGeo: Equatable {
    var w: CGFloat
    var h: CGFloat
    var cx: CGFloat
    var cy: CGFloat
    var top: CGFloat
    var nameBase: CGFloat
    var chipsTop: CGFloat
    var ctaTop: CGFloat

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        let ctaTop = h - max(bottom, 16) - 8 - 56
        let chipsTop = ctaTop - 16 - 44
        let nameBase = chipsTop - 22
        self.w = w
        self.h = h
        self.top = top
        self.cx = w / 2
        // The cube's foot sits so the cube centres between the header and the name.
        self.cy = top + 112 + (nameBase - 70 - (top + 112)) * 0.66
        self.nameBase = nameBase
        self.chipsTop = chipsTop
        self.ctaTop = ctaTop
    }

    var chipW: CGFloat { (w - 32 - 16) / 3 }

    func project(_ p: JellyCubeSquishV3) -> (x: CGFloat, y: CGFloat, s: CGFloat) {
        let s = JellyCubeSquishMath.project(Double(cx), Double(cy), p)
        return (CGFloat(s.x), CGFloat(s.y), CGFloat(s.s))
    }

    func point(_ p: JellyCubeSquishV3) -> CGPoint {
        let s = project(p)
        return CGPoint(x: s.x, y: s.y)
    }

    /// An affine transform drawing the plate's plane at height z around (x, y).
    func plane(_ x: Double, _ y: Double, _ z: Double) -> CGAffineTransform {
        let p = project(JellyCubeSquishV3(x, y, z))
        let px = project(JellyCubeSquishV3(x + 1, y, z))
        let py = project(JellyCubeSquishV3(x, y + 1, z))
        return CGAffineTransform(a: px.x - p.x, b: px.y - p.y, c: py.x - p.x, d: py.y - p.y, tx: p.x, ty: p.y)
    }
}

/// What the cube's Metal view draws in one frame: vertices (8 floats: x, y, unused × 2, r, g, b, a,
/// colours not premultiplied), indices and the draws over them in order.
final class JellyCubeSquishMeshFrame {
    var vertices: [Float] = []
    var indices: [UInt16] = []
    var draws: [(start: Int, count: Int, additive: Bool)] = []
    var number = 0

    static let maxVertices = 4096
    static let maxIndices = 16384
}

enum JellyCubeSquishArt {
    private typealias P = JellyCubeSquishPaint
    private typealias J = JellyCubeSquishMath
    typealias V3 = JellyCubeSquishV3

    // MARK: Baked

    static func backdrop(_ g: JellyCubeSquishGeo, scale: CGFloat) -> UIImage {
        P.bake(CGSize(width: g.w, height: g.h), scale: scale) { c in
            let foot = g.point(V3(0, 0, 0))
            let horizon = foot.y - 70
            P.linear(c, P.rect(0, 0, g.w, horizon), from: .zero, to: CGPoint(x: 0, y: horizon),
                     [P.cg(0xF6E6E1), P.cg(0xF1D9D3), P.cg(0xE9CBC4)], [0, 0.6, 1])
            P.radial(c, P.rect(0, 0, g.w, horizon), center: CGPoint(x: g.cx, y: horizon - 120), radius: g.w * 0.8,
                     [P.cg(0xFFF6F2, 0.7), P.cg(0xFFF6F2, 0)], [0, 1])
            // Marble counter: pale stone with soft grey veins, lit from the top.
            let counter = CGRect(x: 0, y: horizon, width: g.w, height: g.h - horizon)
            P.linear(c, CGPath(rect: counter, transform: nil), from: CGPoint(x: 0, y: horizon), to: CGPoint(x: 0, y: g.h),
                     [P.cg(0xF3EEEA), P.cg(0xE7E0DB), P.cg(0xD6CDC7)], [0, 0.4, 1])
            c.saveGState()
            c.clip(to: counter)
            c.setBlendMode(.multiply)
            c.translateBy(x: 0, y: horizon)
            c.rotate(by: 0.08)
            c.scaleBy(x: 10, y: 5)
            P.brush(c, CGRect(x: -20, y: -2, width: g.w / 10 + 40, height: (g.h - horizon) / 5 + 6), alpha: 0.18)
            c.restoreGState()
            P.stroke(c, P.line(0, horizon, g.w, horizon), 0xFFFFFF, width: 1.2, 0.8)
            P.linear(c, P.rect(0, horizon, g.w, 24), from: CGPoint(x: 0, y: horizon), to: CGPoint(x: 0, y: horizon + 24),
                     [P.cg(0x8A6A62, 0.18), P.cg(0x8A6A62, 0)], nil)
            P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.08, seed: 9)
            // The plate: a shallow ceramic dish.
            c.saveGState()
            c.concatenate(g.plane(6, -10, 0))
            P.fill(c, P.circle(0, 0, 140), 0x6A4A44, 0.22, blur: 16)
            c.restoreGState()
            c.saveGState()
            c.concatenate(g.plane(0, 0, 0))
            P.radial(c, P.circle(0, 0, 134), center: CGPoint(x: -30, y: -40), radius: 170, [P.cg(0xFFFFFF), P.cg(0xF7F3F0), P.cg(0xE2DAD5)], [0, 0.6, 1])
            P.stroke(c, P.circle(0, 0, 112), 0xD7CEC9, width: 2, 0.7)
            P.radial(c, P.circle(0, 0, 110), center: .zero, radius: 112, [P.cg(0xFDFBFA), P.cg(0xF1ECE8), P.cg(0xE6DFDA)], [0, 0.85, 1])
            P.stroke(c, P.circle(0, 0, 131), 0xFFFFFF, width: 1.5, 0.9)
            c.restoreGState()
        }
    }

    // MARK: Colour

    /// The jelly's colour at a rest point while a new flavour fills from the plate up.
    static func colourAt(_ colour: JellyCubeSquishColour, _ x: Double, _ y: Double, _ z: Double) -> V3 {
        let from = JellyCubeSquishMotion.flavours[colour.from].rgb
        let to = JellyCubeSquishMotion.flavours[colour.to].rgb
        if colour.from == colour.to || colour.fill <= 0 { return from }
        if colour.fill >= 1 { return to }
        let front = -24 + colour.fill * (2 * J.half + 48)
        let wave = 7 * sin(x / 15 + colour.fill * 9) + 5 * sin(y / 19 - colour.fill * 6)
        let k = J.clamp((front + wave - z + 12) / 24, 0, 1)
        return from + (to - from) * (k * k * (3 - 2 * k))
    }

    // MARK: Per frame, under the cube

    private static func color(_ c: V3, _ a: Double) -> Color {
        Color(.sRGB, red: c.x, green: c.y, blue: c.z, opacity: a)
    }

    /// Contact shadow and the light that came through the jelly, on the plate.
    static func plateLight(_ ctx: GraphicsContext, _ g: JellyCubeSquishGeo, _ d: JellyCubeSquishDeform, mid: V3) {
        let bulge = CGFloat(1 + max(0, d.sq) * 0.6)
        let hw = CGFloat(J.half) * bulge + 6
        let shadow = Path(roundedRect: CGRect(x: -hw, y: -hw, width: hw * 2, height: hw * 2), cornerRadius: 34).applying(g.plane(10, -8, 0))
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 15))
            l.fill(shadow, with: .color(P.color(0x5A2A2A, 0.32)))
        }
        var c = ctx
        c.concatenate(g.plane(58, 70, 0))
        var m = c
        m.blendMode = .multiply
        let caustic = Gradient(stops: [
            .init(color: color(mid, 0.55), location: 0),
            .init(color: color(mid, 0.18), location: 0.5),
            .init(color: color(mid, 0), location: 1),
        ])
        m.fill(Path(ellipseIn: CGRect(x: -96, y: -96, width: 192, height: 192)),
               with: .radialGradient(caustic, center: .zero, startRadius: 0, endRadius: 96))
        let hot = Gradient(colors: [Color(.sRGB, white: 1, opacity: 0.35), Color(.sRGB, white: 1, opacity: 0)])
        c.fill(Path(ellipseIn: CGRect(x: -50, y: -50, width: 80, height: 80)),
               with: .radialGradient(hot, center: CGPoint(x: -10, y: -10), startRadius: 0, endRadius: 40))
    }

    // MARK: The cube's meshes

    /// Builds the cube's vertex-coloured meshes for Metal into `frame`.
    static func build(_ frame: JellyCubeSquishMeshFrame, _ g: JellyCubeSquishGeo, _ d: JellyCubeSquishDeform,
                      _ colour: JellyCubeSquishColour, time: Double) {
        let n = J.n
        let per = (n + 1) * (n + 1)
        let rest = J.restPoints
        let total = per * J.faceCount
        var world = [V3](repeating: .zero, count: total)
        var screen = [CGPoint](repeating: .zero, count: total)
        for i in 0..<total {
            let p = J.deform(d, rest[i])
            world[i] = p
            screen[i] = g.point(p)
        }
        var verts: [Float] = []
        verts.reserveCapacity(JellyCubeSquishMeshFrame.maxVertices * 8)
        var idx: [UInt16] = []
        idx.reserveCapacity(JellyCubeSquishMeshFrame.maxIndices)
        var vcount = 0
        func vertex(_ p: CGPoint, _ c: V3, _ a: Double) {
            verts += [Float(p.x), Float(p.y), 0, 0, Float(c.x), Float(c.y), Float(c.z), Float(a)]
            vcount += 1
        }
        func grid(_ offset: Int) {
            for j in 0..<n {
                for i in 0..<n {
                    let a = offset + j * (n + 1) + i
                    idx += [UInt16(a), UInt16(a + 1), UInt16(a + n + 1), UInt16(a + 1), UInt16(a + n + 2), UInt16(a + n + 1)]
                }
            }
        }
        let eye = J.toEye
        let light = J.toLight
        let hv = light + eye
        let hl = (hv * hv).sum().squareRoot()
        // Per-vertex normals; faces split by which way they face.
        var nearFaces: [(base: Int, normals: [V3])] = []
        var farFaces: [(base: Int, normals: [V3])] = []
        for f in 0..<J.faceCount {
            let base = f * per
            var normals: [V3] = []
            normals.reserveCapacity(per)
            var facing = 0.0
            for j in 0...n {
                for i in 0...n {
                    let a = base + j * (n + 1) + min(i + 1, n)
                    let b = base + j * (n + 1) + max(i - 1, 0)
                    let c = base + min(j + 1, n) * (n + 1) + i
                    let e = base + max(j - 1, 0) * (n + 1) + i
                    let u = world[a] - world[b]
                    let v = world[c] - world[e]
                    var nn = V3(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x)
                    let l0 = (nn * nn).sum().squareRoot()
                    nn /= l0 == 0 ? 1 : l0
                    // Point outward: away from the cube's axis.
                    let k = base + j * (n + 1) + i
                    let o = V3(world[k].x, world[k].y, world[k].z - J.half)
                    if (nn * o).sum() < 0 { nn = -nn }
                    normals.append(nn)
                    facing += (nn * eye).sum()
                }
            }
            if facing > 0 { nearFaces.append((base, normals)) } else { farFaces.append((base, normals)) }
        }
        var draws: [(start: Int, count: Int, additive: Bool)] = []
        // Far faces: darker, see-through.
        var start = idx.count
        for face in farFaces {
            let offset = vcount
            for k in 0..<per {
                let r = rest[face.base + k]
                let rgb = colourAt(colour, r.x, r.y, r.z)
                vertex(screen[face.base + k], V3(rgb.x * 0.7, rgb.y * 0.62, rgb.z * 0.66), 0.5)
            }
            grid(offset)
        }
        if idx.count > start { draws.append((start, idx.count - start, false)) }
        // Inside: a soft lighter core and a few suspended bubbles drifting on whole-loop sines.
        let mid = colourAt(colour, 0, 0, J.half)
        start = idx.count
        let core = g.project(J.deform(d, 0, 0, J.half))
        let gc = CGPoint(x: core.x, y: core.y - 10)
        let gr = CGFloat(J.half * 1.1) * core.s
        disc(&verts, &idx, &vcount, gc, gr, segments: 48,
             inner: (V3(min(1, mid.x + 0.3), min(1, mid.y + 0.3), min(1, mid.z + 0.3)), 0.55), outer: (mid, 0))
        for b in 0..<9 {
            let bd = Double(b)
            let h1 = sin(bd * 12.9898 + 4.1) * 43758.5453
            let r1 = h1 - floor(h1)
            let h2 = sin(bd * 78.233 + 1.7) * 43758.5453
            let r2 = h2 - floor(h2)
            let h3 = sin(bd * 39.425 + 9.2) * 43758.5453
            let r3 = h3 - floor(h3)
            let drift = 3 * sin(2 * Double.pi * Double(1 + b % 2) * time / JellyCubeSquishMotion.loop + bd)
            let p = g.project(J.deform(d, (r1 - 0.5) * J.half * 1.2, (r2 - 0.5) * J.half * 1.2, 26 + r3 * (2 * J.half - 52) + drift))
            let r = CGFloat(1.6 + 3.4 * Double((b * 7) % 5) / 4) * p.s
            let c = CGPoint(x: p.x, y: p.y)
            let white = V3(1, 1, 1)
            disc(&verts, &idx, &vcount, c, r, segments: 20, inner: (white, 0.16), outer: (white, 0.16))
            ring(&verts, &idx, &vcount, c, r, width: 0.8, segments: 20, colour: white, alpha: 0.5)
            disc(&verts, &idx, &vcount, CGPoint(x: c.x - r * 0.35, y: c.y - r * 0.35), r * 0.3, segments: 12, inner: (white, 0.8), outer: (white, 0.8))
        }
        draws.append((start, idx.count - start, false))
        // Near faces: looking straight in, the jelly is thinner and glows; at grazing angles it is
        // deep and saturated, with a white Fresnel sheen on top. Then the additive gloss.
        start = idx.count
        var specs: [(offset: Int, base: Int, spec: [Double])] = []
        for face in nearFaces {
            let offset = vcount
            var spec: [Double] = []
            spec.reserveCapacity(per)
            for k in 0..<per {
                let r = rest[face.base + k]
                let nn = face.normals[k]
                let rgb = colourAt(colour, r.x, r.y, r.z)
                let diff = max(0, (nn * light).sum())
                let view = max(0, (nn * eye).sum())
                let fres = (1 - view) * (1 - view) * (1 - view)
                let lit = 0.42 + 0.38 * diff + 0.3 * view
                vertex(screen[face.base + k],
                       V3(min(1, rgb.x * lit + 0.42 * fres + 0.03), min(1, rgb.y * lit + 0.42 * fres + 0.03), min(1, rgb.z * lit + 0.42 * fres + 0.04)),
                       0.58 + 0.34 * fres + 0.08 * (1 - diff))
                spec.append(pow(max(0, (nn * hv).sum() / hl), 70) * 1.4)
            }
            grid(offset)
            specs.append((offset, face.base, spec))
        }
        if idx.count > start { draws.append((start, idx.count - start, false)) }
        start = idx.count
        for s in specs {
            let offset = vcount
            for k in 0..<per {
                let v = s.spec[k]
                vertex(screen[s.base + k], V3(v, v, v), min(1, v))
            }
            grid(offset)
        }
        if idx.count > start { draws.append((start, idx.count - start, true)) }
        frame.vertices = verts
        frame.indices = idx
        frame.draws = draws
        frame.number += 1
    }

    /// A disc as a fan: the centre takes `inner`, the rim `outer` (a linear radial gradient).
    private static func disc(_ verts: inout [Float], _ idx: inout [UInt16], _ vcount: inout Int, _ c: CGPoint, _ r: CGFloat,
                             segments: Int, inner: (V3, Double), outer: (V3, Double)) {
        let centre = vcount
        verts += [Float(c.x), Float(c.y), 0, 0, Float(inner.0.x), Float(inner.0.y), Float(inner.0.z), Float(inner.1)]
        vcount += 1
        for k in 0..<segments {
            let a = Double(k) / Double(segments) * 2 * .pi
            verts += [Float(c.x + r * CGFloat(cos(a))), Float(c.y + r * CGFloat(sin(a))), 0, 0,
                      Float(outer.0.x), Float(outer.0.y), Float(outer.0.z), Float(outer.1)]
            vcount += 1
        }
        for k in 0..<segments {
            idx += [UInt16(centre), UInt16(centre + 1 + k), UInt16(centre + 1 + (k + 1) % segments)]
        }
    }

    /// A thin ring (a stroked circle) as a triangle strip of quads.
    private static func ring(_ verts: inout [Float], _ idx: inout [UInt16], _ vcount: inout Int, _ c: CGPoint, _ r: CGFloat,
                             width: CGFloat, segments: Int, colour: V3, alpha: Double) {
        let first = vcount
        for k in 0..<segments {
            let a = Double(k) / Double(segments) * 2 * .pi
            for rr in [r - width / 2, r + width / 2] {
                verts += [Float(c.x + rr * CGFloat(cos(a))), Float(c.y + rr * CGFloat(sin(a))), 0, 0,
                          Float(colour.x), Float(colour.y), Float(colour.z), Float(alpha)]
                vcount += 1
            }
        }
        for k in 0..<segments {
            let a = first + k * 2
            let b = first + ((k + 1) % segments) * 2
            idx += [UInt16(a), UInt16(a + 1), UInt16(b), UInt16(a + 1), UInt16(b + 1), UInt16(b)]
        }
    }

    // MARK: Per frame, over the cube

    /// A window reflected in the top face and a streak down the front: they ride the deformation.
    static func gloss(_ ctx: GraphicsContext, _ g: JellyCubeSquishGeo, _ d: JellyCubeSquishDeform) {
        let pts: [(Double, Double)] = [(-0.62, -0.2), (-0.1, 0.05), (-0.18, 0.62), (-0.66, 0.4)]
        var win = Path()
        win.addLines(pts.map { g.point(J.deform(d, $0.0 * J.half * 0.8, $0.1 * J.half * 0.8, 2 * J.half)) })
        win.closeSubpath()
        var streak = Path()
        streak.addLines((0...8).map { i in g.point(J.deform(d, -J.half * 0.62, -J.half, J.half * (0.32 + Double(i) / 8 * 1.4))) })
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 3))
            l.fill(win, with: .color(Color(.sRGB, white: 1, opacity: 0.32)))
            l.stroke(streak, with: .color(Color(.sRGB, white: 1, opacity: 0.22)), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
        }
        ctx.stroke(streak, with: .color(Color(.sRGB, white: 1, opacity: 0.35)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
    }

    /// The demo finger: a soft translucent fingertip.
    static func finger(_ ctx: GraphicsContext, _ g: JellyCubeSquishGeo, _ at: V3, alpha: Double) {
        guard alpha > 0.01 else { return }
        let p = g.point(at)
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 6))
            l.fill(Path(ellipseIn: CGRect(x: p.x - 22, y: p.y + 3 - 22, width: 44, height: 44)), with: .color(P.color(0x3A1A1A, 0.18 * alpha)))
        }
        let tip = Path(ellipseIn: CGRect(x: p.x - 20, y: p.y - 20, width: 40, height: 40))
        ctx.fill(tip, with: .color(Color(.sRGB, white: 1, opacity: 0.42 * alpha)))
        ctx.stroke(tip, with: .color(Color(.sRGB, white: 1, opacity: 0.85 * alpha)), lineWidth: 1.5)
    }

    static let nameFont = Font.system(size: 22, weight: .bold)
    static let priceFont = Font.system(size: 22, weight: .semibold)
    static let priceUI = P.sans(22, .semibold)

    private static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color, x: CGFloat, baseline: CGFloat) {
        let r = ctx.resolve(Text(s).font(font).foregroundColor(color))
        let b = r.firstBaseline(in: CGSize(width: 10_000, height: 10_000))
        ctx.draw(r, at: CGPoint(x: x, y: baseline - b), anchor: .topLeading)
    }

    /// "Strawberry Wobble" rolling to the next flavour's name, and the price.
    static func name(_ ctx: GraphicsContext, _ g: JellyCubeSquishGeo, _ colour: JellyCubeSquishColour) {
        let names = [JellyCubeSquishMotion.flavours[colour.from].name, JellyCubeSquishMotion.flavours[colour.to].name]
        var clip = ctx
        clip.clip(to: Path(CGRect(x: 0, y: g.nameBase - 30, width: g.w, height: 40)))
        for j in 0..<2 {
            let a = 1 - min(1, abs(Double(j) - colour.roll))
            if a <= 0.01 { continue }
            text(clip, "\(names[j]) Wobble", nameFont, P.color(0x3A1F22, a), x: 24, baseline: g.nameBase + CGFloat(Double(j) - colour.roll) * 28)
        }
        let price = "$6.50"
        text(ctx, price, priceFont, P.color(0x3A1F22), x: g.w - 24 - P.width(price, priceUI), baseline: g.nameBase)
    }

    /// The chips' white highlight, sliding with the colour's name.
    static func chipHighlight(_ ctx: GraphicsContext, _ g: JellyCubeSquishGeo, _ colour: JellyCubeSquishColour) {
        let at = CGFloat(Double(colour.from) + Double(colour.to - colour.from) * colour.roll)
        let rect = CGRect(x: 16 + at * (g.chipW + 8), y: g.chipsTop, width: g.chipW, height: 44)
        let pill = Path(roundedRect: rect, cornerRadius: 22)
        ctx.drawLayer { l in
            l.addFilter(.shadow(color: P.color(0x3A1F22, 0.12), radius: 8, x: 0, y: 6))
            l.addFilter(.shadow(color: P.color(0x3A1F22, 0.16), radius: 1, x: 0, y: 1))
            l.fill(pill, with: .color(P.color(0xFFFDFB)))
        }
    }
}
