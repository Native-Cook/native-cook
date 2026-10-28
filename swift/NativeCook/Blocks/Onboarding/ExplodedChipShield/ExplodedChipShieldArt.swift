import SwiftUI

/// Draws the six isometric layers of the chip, its shadow, glow and callouts. Every shape is built
/// flat in the layer's plane (centred on 0) and projected: turn by the yaw, squash y by 0.58, lift
/// by the layer's height. Side faces are the convex hull of the top and bottom outlines.
enum ExplodedChipShieldArt {
    typealias M = ExplodedChipShieldModel

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: a)
    }

    // MARK: Projection

    struct Proj {
        var cx: CGFloat
        var cy: CGFloat
        var yaw: Double
        var scale: CGFloat = 1

        func point(_ p: CGPoint, _ z: CGFloat) -> CGPoint {
            let c = CGFloat(cos(yaw)) * scale, s = CGFloat(sin(yaw)) * scale
            return CGPoint(x: cx + p.x * c - p.y * s, y: cy + M.squash * (p.x * s + p.y * c) - z * scale)
        }

        /// The plane → screen map at height z; `turn` rotates the drawing inside the plane first.
        func transform(_ z: CGFloat, turn: Double = 0) -> CGAffineTransform {
            let a = yaw + turn
            let c = CGFloat(cos(a)) * scale, s = CGFloat(sin(a)) * scale
            return CGAffineTransform(a: c, b: M.squash * s, c: -s, d: M.squash * c, tx: cx, ty: cy - z * scale)
        }

        /// Screen-space y of a plane normal: > 0 when that side faces the viewer.
        func facing(_ n: CGPoint) -> Bool {
            CGFloat(sin(yaw)) * n.x + CGFloat(cos(yaw)) * n.y > 0.05
        }
    }

    // MARK: Outlines (plane)

    struct Outline {
        var points: [CGPoint]
        var normals: [CGPoint]
    }

    static func roundedSquare(_ side: CGFloat, _ radius: CGFloat, perCorner n: Int = 9) -> Outline {
        let h = side / 2, r = radius
        let centres = [CGPoint(x: h - r, y: -h + r), CGPoint(x: h - r, y: h - r),
                       CGPoint(x: -h + r, y: h - r), CGPoint(x: -h + r, y: -h + r)]
        var pts: [CGPoint] = [], nrm: [CGPoint] = []
        for (k, c) in centres.enumerated() {
            let a0 = -Double.pi / 2 + Double(k) * .pi / 2
            for j in 0 ... n {
                let a = a0 + Double(j) / Double(n) * .pi / 2
                let d = CGPoint(x: cos(a), y: sin(a))
                nrm.append(d)
                pts.append(CGPoint(x: c.x + r * d.x, y: c.y + r * d.y))
            }
        }
        return Outline(points: pts, normals: nrm)
    }

    static func circle(_ d: CGFloat, count n: Int = 48) -> Outline {
        var pts: [CGPoint] = [], nrm: [CGPoint] = []
        for j in 0 ..< n {
            let a = Double(j) / Double(n) * 2 * .pi
            let v = CGPoint(x: cos(a), y: sin(a))
            nrm.append(v)
            pts.append(CGPoint(x: v.x * d / 2, y: v.y * d / 2))
        }
        return Outline(points: pts, normals: nrm)
    }

    /// Convex hull (monotone chain).
    static func hull(_ input: [CGPoint]) -> [CGPoint] {
        let p = input.sorted { $0.x == $1.x ? $0.y < $1.y : $0.x < $1.x }
        guard p.count > 2 else { return p }
        func cross(_ o: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        }
        var lower: [CGPoint] = [], upper: [CGPoint] = []
        for q in p {
            while lower.count >= 2 && cross(lower[lower.count - 2], lower[lower.count - 1], q) <= 0 { lower.removeLast() }
            lower.append(q)
        }
        for q in p.reversed() {
            while upper.count >= 2 && cross(upper[upper.count - 2], upper[upper.count - 1], q) <= 0 { upper.removeLast() }
            upper.append(q)
        }
        return Array(lower.dropLast() + upper.dropLast())
    }

    static func poly(_ pts: [CGPoint]) -> Path {
        var p = Path()
        p.addLines(pts)
        p.closeSubpath()
        return p
    }

    static func bounds(_ pts: [CGPoint]) -> CGRect {
        var r = CGRect.null
        for q in pts { r = r.union(CGRect(origin: q, size: .zero)) }
        return r
    }

    typealias Stops = [(Double, UInt32, Double)]

    static func gradient(_ stops: Stops) -> Gradient {
        Gradient(stops: stops.map { Gradient.Stop(color: color($0.1, $0.2), location: $0.0) })
    }

    static func across(_ stops: Stops, _ r: CGRect) -> GraphicsContext.Shading {
        .linearGradient(gradient(stops), startPoint: CGPoint(x: r.minX, y: r.midY), endPoint: CGPoint(x: r.maxX, y: r.midY))
    }

    static func diagonal(_ stops: Stops, _ r: CGRect) -> GraphicsContext.Shading {
        .linearGradient(gradient(stops), startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.maxX, y: r.maxY))
    }

    /// Where the light band sits along a face's diagonal; it travels with the view angle.
    static func specular(_ yaw: Double) -> Double {
        0.42 + (yaw - M.restYaw) / (10 * .pi / 180) * 0.32
    }

    // MARK: Materials

    static let chromeSide: Stops = [(0, 0x868C94, 1), (0.22, 0xEEF0F2, 1), (0.48, 0x9AA0A8, 1), (0.70, 0xF5F6F7, 1), (1, 0x6A7078, 1)]
    static let darkChromeSide: Stops = [(0, 0x1E2227, 1), (0.30, 0x9097A0, 1), (0.55, 0x2A2E34, 1), (0.80, 0xC8CDD2, 1), (1, 0x2B3036, 1)]
    static let lidTop: Stops = [(0, 0xF7F8F9, 1), (0.55, 0xD9DDE1, 1), (1, 0xBCC1C8, 1)]
    static let baseTop: Stops = [(0, 0xD6DADF, 1), (0.5, 0xA9AFB7, 1), (1, 0xC9CDD3, 1)]
    static let trayTop: Stops = [(0, 0x3A3F46, 1), (0.30, 0xE3E6EA, 1), (0.55, 0x4E545C, 1), (0.82, 0xD0D4D9, 1), (1, 0x30353B, 1)]

    // MARK: Pieces

    struct Slab {
        var top: Path
        var topPts: [CGPoint]
        var box: CGRect
    }

    /// Fills the side faces (hull) and returns the top face for the caller to finish.
    @discardableResult
    static func slab(_ c: inout GraphicsContext, _ pr: Proj, _ o: Outline, z: CGFloat, thick: CGFloat,
                     side: Stops, sideAlpha: Double = 1, edge: Double = 0.14) -> Slab {
        let top = o.points.map { pr.point($0, z + thick) }
        let bottom = o.points.map { pr.point($0, z) }
        let h = hull(top + bottom)
        let hb = bounds(h)
        var s = c
        s.opacity = sideAlpha
        s.fill(poly(h), with: across(side, hb))
        if edge > 0 { s.stroke(poly(h), with: .color(color(0x1A1D21, edge)), lineWidth: 0.6) }
        return Slab(top: poly(top), topPts: top, box: bounds(top))
    }

    /// Fine brushed lines across a face, clipped to it.
    static func brushed(_ c: inout GraphicsContext, _ pr: Proj, side: CGFloat, z: CGFloat, clip: Path, light: Double, dark: Double) {
        var lightP = Path(), darkP = Path()
        let n = 34
        let h = side / 2
        let t = pr.transform(z)
        for j in 1 ..< n {
            let y = -h + side * CGFloat(j) / CGFloat(n)
            let hsh = fract(sin(Double(j) * 12.9898) * 43758.5453)
            var seg = Path()
            seg.move(to: CGPoint(x: -h, y: y))
            seg.addLine(to: CGPoint(x: h, y: y))
            if hsh < 0.55 { lightP.addPath(seg, transform: t) } else { darkP.addPath(seg, transform: t) }
        }
        var k = c
        k.clip(to: clip)
        k.stroke(lightP, with: .color(color(0xFFFFFF, light)), lineWidth: 0.7)
        k.stroke(darkP, with: .color(color(0x2A2F35, dark)), lineWidth: 0.6)
    }

    static func fract(_ x: Double) -> Double { x - floor(x) }

    static func band(_ c: inout GraphicsContext, clip: Path, box: CGRect, u: Double, alpha: Double, width: Double = 0.14) {
        var k = c
        k.clip(to: clip)
        k.fill(clip, with: diagonal([(0, 0xFFFFFF, 0), (max(0, u - width), 0xFFFFFF, 0), (min(max(u, 0), 1), 0xFFFFFF, alpha),
                                     (min(1, u + width), 0xFFFFFF, 0), (1, 0xFFFFFF, 0)].sorted { $0.0 < $1.0 }, box))
    }

    /// The shield of the mark, centred on 0, its point towards +y.
    static func shield(_ w: CGFloat, _ h: CGFloat) -> Path {
        var p = Path()
        let c = w * 0.16
        p.move(to: CGPoint(x: -w / 2, y: -h / 2 + c))
        p.addQuadCurve(to: CGPoint(x: -w / 2 + c, y: -h / 2), control: CGPoint(x: -w / 2, y: -h / 2))
        p.addLine(to: CGPoint(x: w / 2 - c, y: -h / 2))
        p.addQuadCurve(to: CGPoint(x: w / 2, y: -h / 2 + c), control: CGPoint(x: w / 2, y: -h / 2))
        p.addLine(to: CGPoint(x: w / 2, y: -h * 0.02))
        p.addCurve(to: CGPoint(x: 0, y: h / 2), control1: CGPoint(x: w / 2, y: h * 0.26), control2: CGPoint(x: w * 0.22, y: h * 0.42))
        p.addCurve(to: CGPoint(x: -w / 2, y: -h * 0.02), control1: CGPoint(x: -w * 0.22, y: h * 0.42), control2: CGPoint(x: -w / 2, y: h * 0.26))
        p.closeSubpath()
        return p
    }

    /// The keyhole inside the shield (relative to the shield's size).
    static func keyhole(_ w: CGFloat, _ h: CGFloat) -> Path {
        var p = Path()
        let r = w * 0.15
        let cy = -h * 0.10
        p.addEllipse(in: CGRect(x: -r, y: cy - r, width: 2 * r, height: 2 * r))
        p.move(to: CGPoint(x: -w * 0.06, y: cy + r * 0.4))
        p.addLine(to: CGPoint(x: w * 0.06, y: cy + r * 0.4))
        p.addLine(to: CGPoint(x: w * 0.10, y: h * 0.22))
        p.addLine(to: CGPoint(x: -w * 0.10, y: h * 0.22))
        p.closeSubpath()
        return p
    }

    // MARK: Layers

    static func base(_ c: inout GraphicsContext, _ pr: Proj, _ f: ExplodedChipShieldFrame) {
        let l = M.layers[0], u = M.unit
        let o = roundedSquare(l.side * u, l.radius * u)
        let s = slab(&c, pr, o, z: 0, thick: l.thick, side: darkChromeSide)
        c.fill(s.top, with: diagonal(baseTop, s.box))
        brushed(&c, pr, side: l.side * u, z: l.thick, clip: s.top, light: 0.30, dark: 0.07)
        band(&c, clip: s.top, box: s.box, u: specular(f.yaw) + 0.1, alpha: 0.35, width: 0.18)
        c.stroke(s.top, with: .color(color(0xFFFFFF, 0.55)), lineWidth: 0.8)
    }

    static func tray(_ c: inout GraphicsContext, _ pr: Proj, z: CGFloat, open: Double) {
        let l = M.layers[1], u = M.unit
        let outer = roundedSquare(l.side * u, l.radius * u)
        let inner = roundedSquare(M.trayHole * u, 0.11 * u)
        let s = slab(&c, pr, outer, z: z, thick: l.thick, side: darkChromeSide)
        let innerTop = inner.points.map { pr.point($0, z + l.thick) }
        let innerBottom = inner.points.map { pr.point($0, z) }
        var ring = s.top
        ring.addPath(poly(innerTop))
        c.fill(ring, with: across(trayTop, s.box), style: FillStyle(eoFill: true))
        // The far inner wall, seen through the hole.
        var wall = poly(innerTop)
        wall.addPath(poly(innerBottom))
        var k = c
        k.clip(to: poly(innerTop))
        k.fill(wall, with: across([(0, 0x5A6068, 1), (0.5, 0x1F2328, 1), (1, 0x8D939B, 1)], bounds(innerTop)), style: FillStyle(eoFill: true))
        c.stroke(s.top, with: .color(color(0xFFFFFF, 0.7)), lineWidth: 0.8)
        c.stroke(poly(innerTop), with: .color(color(0xFFFFFF, 0.5)), lineWidth: 0.7)
    }

    static func core(_ c: inout GraphicsContext, _ pr: Proj, z: CGFloat, f: ExplodedChipShieldFrame) {
        let l = M.layers[2], u = M.unit
        let o = roundedSquare(l.side * u, l.radius * u)
        // Teal glow under the glass.
        let glowPts = o.points.map { pr.point(CGPoint(x: $0.x * 1.05, y: $0.y * 1.05), z) }
        var g = c
        g.addFilter(.blur(radius: 16))
        g.fill(poly(glowPts), with: .color(color(M.glow, 0.55 * f.open)))

        let s = slab(&c, pr, o, z: z, thick: l.thick, side: [(0, 0x1C8577, 1), (0.45, 0x5BD0BE, 1), (1, 0x1F8C7D, 1)], edge: 0.1)
        c.fill(s.top, with: diagonal([(0, 0x9BEBDD, 1), (0.5, 0x5CC9B8, 1), (1, 0x2FA594, 1)], s.box))
        // Inner bevel and a fine grid.
        let insetPts = roundedSquare(l.side * u * 0.86, l.radius * u * 0.7).points.map { pr.point($0, z + l.thick) }
        c.stroke(poly(insetPts), with: .color(color(0xFFFFFF, 0.35)), lineWidth: 0.8)
        var grid = Path()
        let h = l.side * u * 0.43
        for j in 1 ..< 8 {
            let v = -h + 2 * h * CGFloat(j) / 8
            grid.move(to: pr.point(CGPoint(x: v, y: -h), z + l.thick))
            grid.addLine(to: pr.point(CGPoint(x: v, y: h), z + l.thick))
            grid.move(to: pr.point(CGPoint(x: -h, y: v), z + l.thick))
            grid.addLine(to: pr.point(CGPoint(x: h, y: v), z + l.thick))
        }
        var k = c
        k.clip(to: poly(insetPts))
        k.stroke(grid, with: .color(color(0xFFFFFF, 0.16)), lineWidth: 0.6)
        c.stroke(s.top, with: .color(color(0xD9FFF8, 0.9)), lineWidth: 0.8)

        // The raised metal disc.
        let dz = z + l.thick
        let d = circle(M.discSide * u)
        let ds = slab(&c, pr, d, z: dz, thick: M.discThick, side: chromeSide, edge: 0.18)
        let centre = pr.point(.zero, dz + M.discThick)
        c.fill(ds.top, with: .radialGradient(gradient([(0, 0xFBFCFC, 1), (0.7, 0xD5D9DE, 1), (1, 0xAFB5BC, 1)]),
                                              center: CGPoint(x: centre.x - 8, y: centre.y - 4), startRadius: 0, endRadius: M.discSide * u * 0.6))
        var rings = Path()
        for r in [0.82, 0.64, 0.46] as [CGFloat] {
            rings.addPath(Path(ellipseIn: CGRect(x: -M.discSide * u * r / 2, y: -M.discSide * u * r / 2,
                                                 width: M.discSide * u * r, height: M.discSide * u * r)),
                          transform: pr.transform(dz + M.discThick))
        }
        c.stroke(rings, with: .color(color(0x5A6068, 0.16)), lineWidth: 0.7)
        band(&c, clip: ds.top, box: ds.box, u: specular(f.yaw), alpha: 0.5, width: 0.16)
        let kh = keyhole(M.discSide * u * 0.34, M.discSide * u * 0.40).applying(pr.transform(dz + M.discThick, turn: -.pi / 2))
        c.fill(kh, with: .color(color(0x3B4148, 0.8)))
        c.stroke(ds.top, with: .color(color(0xFFFFFF, 0.8)), lineWidth: 0.8)
    }

    static func frosted(_ c: inout GraphicsContext, _ pr: Proj, z: CGFloat, f: ExplodedChipShieldFrame) {
        let l = M.layers[3], u = M.unit
        let o = roundedSquare(l.side * u, l.radius * u)
        let hole = circle(M.holeSide * u)
        let s = slab(&c, pr, o, z: z, thick: l.thick,
                     side: [(0, 0xE4EFED, 0.80), (0.5, 0xFFFFFF, 0.92), (1, 0xCFE2DE, 0.80)], edge: 0.06)
        let holeTop = hole.points.map { pr.point($0, z + l.thick) }
        let holeBottom = hole.points.map { pr.point($0, z) }
        var face = s.top
        face.addPath(poly(holeTop))
        c.fill(face, with: diagonal([(0, 0xFFFFFF, 0.90), (0.6, 0xF1F7F6, 0.82), (1, 0xDDEBE8, 0.80)], s.box), style: FillStyle(eoFill: true))
        // Frosted dots, as in a print on the glass.
        var dots = Path()
        let n = 22
        let half = l.side * u / 2 - 8
        let t = pr.transform(z + l.thick)
        for i in 0 ... n {
            for j in 0 ... n {
                let x = -half + 2 * half * CGFloat(i) / CGFloat(n)
                let y = -half + 2 * half * CGFloat(j) / CGFloat(n)
                let r2 = x * x + y * y
                if r2 < pow(M.holeSide * u / 2 + 7, 2) { continue }
                let hsh = fract(sin(Double(i * 31 + j * 17) * 12.9898) * 43758.5453)
                if hsh > 0.42 { continue }
                dots.addPath(Path(ellipseIn: CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8)), transform: t)
            }
        }
        var k = c
        k.clip(to: s.top)
        k.fill(dots, with: .color(color(0x8FA6A2, 0.5)))
        // The hole's far wall and its teal rim.
        var wall = poly(holeTop)
        wall.addPath(poly(holeBottom))
        var w = c
        w.clip(to: poly(holeTop))
        w.fill(wall, with: .color(color(0xD7EEEA, 0.75)), style: FillStyle(eoFill: true))
        c.stroke(poly(holeTop), with: .color(color(0x4FC7B5, 0.35 + 0.45 * f.open)), lineWidth: 1.1)
        c.stroke(s.top, with: .color(color(0xFFFFFF, 1)), lineWidth: 1.2)
        band(&c, clip: s.top, box: s.box, u: specular(f.yaw) - 0.05, alpha: 0.45, width: 0.12)
    }

    static func glass(_ c: inout GraphicsContext, _ pr: Proj, z: CGFloat, f: ExplodedChipShieldFrame) {
        let l = M.layers[4], u = M.unit
        let o = roundedSquare(l.side * u, l.radius * u)
        let s = slab(&c, pr, o, z: z, thick: l.thick, side: [(0, 0xA9E3DA, 0.55), (0.5, 0xF2FCFA, 0.75), (1, 0x9CDDD2, 0.55)], edge: 0)
        c.fill(s.top, with: diagonal([(0, 0xFFFFFF, 0.42), (0.6, 0xF2FAF9, 0.22), (1, 0xDDF3EF, 0.20)], s.box))
        let tz = z + l.thick
        let t = pr.transform(tz)
        // Circuit: traces with round pads, and a field of dots.
        var traces = Path(), pads = Path(), dots = Path()
        let a = l.side * u / 2
        let routes: [[CGPoint]] = [
            [CGPoint(x: -0.80, y: -0.55), CGPoint(x: -0.35, y: -0.55), CGPoint(x: -0.20, y: -0.35)],
            [CGPoint(x: 0.80, y: -0.30), CGPoint(x: 0.45, y: -0.30), CGPoint(x: 0.30, y: -0.15)],
            [CGPoint(x: -0.55, y: 0.80), CGPoint(x: -0.55, y: 0.40), CGPoint(x: -0.30, y: 0.22)],
            [CGPoint(x: 0.30, y: 0.80), CGPoint(x: 0.30, y: 0.45), CGPoint(x: 0.12, y: 0.30)],
            [CGPoint(x: -0.80, y: 0.10), CGPoint(x: -0.42, y: 0.10)],
            [CGPoint(x: 0.62, y: 0.62), CGPoint(x: 0.42, y: 0.42)],
        ]
        for r in routes {
            var p = Path()
            p.addLines(r.map { CGPoint(x: $0.x * a, y: $0.y * a) })
            traces.addPath(p, transform: t)
            for q in [r.first!, r.last!] {
                pads.addPath(Path(ellipseIn: CGRect(x: q.x * a - 2.2, y: q.y * a - 2.2, width: 4.4, height: 4.4)), transform: t)
            }
        }
        let n = 18
        for i in 0 ... n {
            for j in 0 ... n {
                let x = -a + 10 + (2 * a - 20) * CGFloat(i) / CGFloat(n)
                let y = -a + 10 + (2 * a - 20) * CGFloat(j) / CGFloat(n)
                let hsh = fract(sin(Double(i * 13 + j * 57) * 78.233) * 43758.5453)
                let ring = x * x + y * y
                if hsh > 0.34 || ring < pow(a * 0.34, 2) { continue }
                dots.addPath(Path(ellipseIn: CGRect(x: x - 0.8, y: y - 0.8, width: 1.6, height: 1.6)), transform: t)
            }
        }
        c.fill(dots, with: .color(color(0x7D9894, 0.55)))
        c.stroke(traces, with: .color(color(0x3FB5A3, 0.75)), lineWidth: 1)
        c.fill(pads, with: .color(color(0x3FB5A3, 0.85)))
        // Centre ring of the mesh.
        let ringPath = Path(ellipseIn: CGRect(x: -a * 0.28, y: -a * 0.28, width: a * 0.56, height: a * 0.56)).applying(t)
        c.stroke(ringPath, with: .color(color(0x3FB5A3, 0.6)), style: StrokeStyle(lineWidth: 1, dash: [2, 2.5]))
        c.stroke(s.top, with: .color(color(0x5CC6B5, 0.9)), lineWidth: 1)
        let inner = roundedSquare(l.side * u - 3, l.radius * u - 1.5).points.map { pr.point($0, tz) }
        c.stroke(poly(inner), with: .color(color(0xFFFFFF, 0.9)), lineWidth: 0.8)
        band(&c, clip: s.top, box: s.box, u: specular(f.yaw) + 0.05, alpha: 0.35, width: 0.10)
    }

    static func lid(_ c: inout GraphicsContext, _ pr: Proj, z: CGFloat, f: ExplodedChipShieldFrame) {
        let l = M.layers[5], u = M.unit
        let o = roundedSquare(l.side * u, l.radius * u)
        let s = slab(&c, pr, o, z: z, thick: l.thick, side: chromeSide)
        // Knurled edge: fine ticks on the sides that face the viewer.
        let dense = roundedSquare(l.side * u, l.radius * u, perCorner: 14)
        var ticks = Path()
        for (p, n) in denseOutline(dense, every: 2.4) {
            if !pr.facing(n) { continue }
            ticks.move(to: pr.point(p, z + 1.5))
            ticks.addLine(to: pr.point(p, z + l.thick - 1.5))
        }
        c.stroke(ticks, with: .color(color(0x3A4047, 0.28)), lineWidth: 0.6)

        let tz = z + l.thick
        c.fill(s.top, with: diagonal(lidTop, s.box))
        brushed(&c, pr, side: l.side * u, z: tz, clip: s.top, light: 0.32, dark: 0.04)
        band(&c, clip: s.top, box: s.box, u: specular(f.yaw), alpha: 0.6, width: 0.16)

        // Engraved inner frame.
        let frame = roundedSquare(0.70 * u, 0.15 * u).points
        let fTop = frame.map { pr.point($0, tz) }
        var g1 = c
        g1.translateBy(x: 0, y: -0.6)
        g1.stroke(poly(fTop), with: .color(color(0x5A6068, 0.30)), lineWidth: 1.1)
        var g2 = c
        g2.translateBy(x: 0, y: 0.7)
        g2.stroke(poly(fTop), with: .color(color(0xFFFFFF, 0.95)), lineWidth: 1)

        // Raised shield with a keyhole; the mark's "up" runs along the lid's edge.
        let sw = 0.40 * u, sh = 0.47 * u
        let mt = pr.transform(tz + 1.5, turn: -.pi / 2)
        let sp = shield(sw, sh).applying(mt)
        var sh1 = c
        sh1.addFilter(.blur(radius: 1.6))
        sh1.fill(sp.applying(CGAffineTransform(translationX: 0.6, y: 2.4)), with: .color(color(0x2A2F35, 0.30)))
        let sb = sp.boundingRect
        c.fill(sp, with: diagonal([(0, 0xFDFDFE, 1), (0.5, 0xDCE0E4, 1), (1, 0xB4BAC1, 1)], sb))
        // Fine hatching on the raised face.
        var hatch = Path()
        var v = -sw
        while v < sw {
            hatch.move(to: CGPoint(x: v, y: -sh / 2))
            hatch.addLine(to: CGPoint(x: v + sh, y: sh / 2))
            v += 2.6
        }
        var hk = c
        hk.clip(to: sp)
        hk.stroke(hatch.applying(mt), with: .color(color(0x4A5057, 0.10)), lineWidth: 0.6)
        band(&hk, clip: sp, box: sb, u: specular(f.yaw) + 0.08, alpha: 0.55, width: 0.12)
        c.stroke(sp, with: .color(color(0xFFFFFF, 0.95)), lineWidth: 0.9)
        var se = c
        se.clip(to: sp)
        se.translateBy(x: 0, y: 1)
        se.stroke(sp, with: .color(color(0x5A6068, 0.35)), lineWidth: 1)
        let kh = keyhole(sw, sh).applying(mt)
        c.fill(kh, with: diagonal([(0, 0x6A7078, 1), (1, 0x3A3F46, 1)], kh.boundingRect))
        var ke = c
        ke.translateBy(x: 0, y: 0.7)
        ke.stroke(kh, with: .color(color(0xFFFFFF, 0.8)), lineWidth: 0.7)
        c.stroke(s.top, with: .color(color(0xFFFFFF, 0.9)), lineWidth: 0.9)
    }

    /// Outline points resampled about every `every` pt along the perimeter, with their normals.
    static func denseOutline(_ o: Outline, every: CGFloat) -> [(CGPoint, CGPoint)] {
        var out: [(CGPoint, CGPoint)] = []
        let n = o.points.count
        for k in 0 ..< n {
            let a = o.points[k], b = o.points[(k + 1) % n]
            let na = o.normals[k], nb = o.normals[(k + 1) % n]
            let len = hypot(b.x - a.x, b.y - a.y)
            let m = max(1, Int(len / every))
            for j in 0 ..< m {
                let t = CGFloat(j) / CGFloat(m)
                out.append((CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t),
                            CGPoint(x: na.x + (nb.x - na.x) * t, y: na.y + (nb.y - na.y) * t)))
            }
        }
        return out
    }

    /// Thin glass halo around a layer.
    static func halo(_ c: inout GraphicsContext, _ pr: Proj, side: CGFloat, z: CGFloat, alpha: Double) {
        guard alpha > 0.005 else { return }
        let pts = roundedSquare(side * M.unit, 0.30 * M.unit).points.map { pr.point($0, z) }
        c.stroke(poly(pts), with: .color(color(0x6FD3C3, alpha)), lineWidth: 1)
    }

    // MARK: Stack

    struct Geo {
        var w: CGFloat
        var h: CGFloat
        var top: CGFloat
        var textTop: CGFloat
    }

    static func proj(_ g: Geo, _ f: ExplodedChipShieldFrame) -> Proj {
        let open = CGFloat(f.open)
        return Proj(cx: g.w / 2 - M.shift * open,
                    cy: g.textTop - M.closedLift + M.openDrop * open + CGFloat(f.dip),
                    yaw: f.yaw,
                    scale: 1 + M.closedZoom * CGFloat(1 - min(f.open, 1)))
    }

    static func background(_ c: inout GraphicsContext, _ g: Geo, _ f: ExplodedChipShieldFrame) {
        let r = CGRect(x: 0, y: 0, width: g.w, height: g.h)
        c.fill(Path(r), with: .linearGradient(gradient([(0, M.bgTop, 1), (1, M.bgBottom, 1)]),
                                              startPoint: .zero, endPoint: CGPoint(x: 0, y: g.h)))
        if f.open > 0.01 {
            let pr = proj(g, f)
            let p = pr.point(.zero, M.z(2, f))
            c.fill(Path(r), with: .radialGradient(gradient([(0, M.glow, 0.20 * f.open), (1, M.glow, 0)]),
                                                  center: p, startRadius: 0, endRadius: 170))
        }
    }

    static func stack(_ c: inout GraphicsContext, _ g: Geo, _ f: ExplodedChipShieldFrame) {
        let pr = proj(g, f)
        let u = M.unit
        // Contact and ambient shadows under the base.
        let foot = roundedSquare(u, 0.22 * u).points.map { pr.point($0, -1) }
        var amb = c
        amb.addFilter(.blur(radius: 18))
        amb.fill(poly(foot).applying(CGAffineTransform(translationX: 0, y: 16)), with: .color(color(0x1A2026, 0.13)))
        var con = c
        con.addFilter(.blur(radius: 4))
        con.fill(poly(foot).applying(CGAffineTransform(translationX: 0, y: 4)), with: .color(color(0x1A2026, 0.22)))

        base(&c, pr, f)
        halo(&c, pr, side: 1.14, z: M.z(1, f) + 3, alpha: 0.45 * f.open)
        tray(&c, pr, z: M.z(1, f), open: f.open)
        core(&c, pr, z: M.z(2, f), f: f)
        frosted(&c, pr, z: M.z(3, f), f: f)
        halo(&c, pr, side: 1.20, z: M.z(4, f) + 1, alpha: 0.5 * f.open)
        glass(&c, pr, z: M.z(4, f), f: f)
        lid(&c, pr, z: M.z(5, f), f: f)
    }

    /// Plane point the callout lines start from, on each layer's top face.
    static let calloutAnchor = CGPoint(x: 0.26 * M.unit, y: -0.26 * M.unit)

    static func calloutPoint(_ g: Geo, _ f: ExplodedChipShieldFrame, _ k: Int) -> CGPoint {
        let i = M.callouts[k].layer
        return proj(g, f).point(calloutAnchor, M.z(i, f) + M.layers[i].thick)
    }

    static func calloutX(_ g: Geo, labelWidth: CGFloat) -> CGFloat {
        min(g.w / 2 - M.shift + 0.707 * M.unit + 16, g.w - 18 - labelWidth)
    }

    static func callouts(_ c: inout GraphicsContext, _ g: Geo, _ f: ExplodedChipShieldFrame) {
        var titles: [GraphicsContext.ResolvedText] = [], captions: [GraphicsContext.ResolvedText] = []
        var widest: CGFloat = 0
        for co in M.callouts {
            let a = c.resolve(Text(co.title).font(.system(size: 14, weight: .semibold)).foregroundColor(color(M.ink)))
            let b = c.resolve(Text(co.caption).font(.system(size: 12)).foregroundColor(color(M.caption)))
            widest = max(widest, a.measure(in: CGSize(width: 400, height: 40)).width, b.measure(in: CGSize(width: 400, height: 40)).width)
            titles.append(a)
            captions.append(b)
        }
        let lx = calloutX(g, labelWidth: widest)
        for k in 0 ..< M.callouts.count {
            let v = f.callout[k]
            guard v > 0.002 else { continue }
            let p = calloutPoint(g, f, k)
            let endX = lx - 8
            let x1 = p.x + (endX - p.x) * CGFloat(v)
            var line = Path()
            line.move(to: p)
            line.addLine(to: CGPoint(x: x1, y: p.y))
            c.stroke(line, with: .color(color(M.line, 0.95)), lineWidth: 1)
            let r = 3.2 * CGFloat(min(1, v * 1.6))
            let dot = Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r))
            c.fill(dot, with: .color(color(0xFFFFFF)))
            c.stroke(dot, with: .color(color(M.teal, min(1, v * 1.6))), lineWidth: 1.4)
            var t = c
            t.opacity = M.easeOut(M.seg(v, 0.35, 1))
            let dx = 10 * CGFloat(1 - v)
            t.draw(titles[k], at: CGPoint(x: lx + dx, y: p.y - 2), anchor: .bottomLeading)
            t.draw(captions[k], at: CGPoint(x: lx + dx, y: p.y + 2), anchor: .topLeading)
        }
    }
}
