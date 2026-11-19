import SwiftUI

/// The per-frame geometry of the 3D layer: the buildings in view, raised by the camera's tilt, and the van among them,
/// in painter's order, as screen polygons. Pure functions of the camera.
struct CourierZoomTrackerPolys {
    var colors: [UInt32] = []
    var counts: [Int] = []
    var pts: [CGPoint] = []

    mutating func add(_ color: UInt32, _ q: [CGPoint]) {
        colors.append(color)
        counts.append(q.count)
        pts.append(contentsOf: q)
    }
}

enum CourierZoomTrackerDraw {
    typealias City = CourierZoomTrackerCity
    typealias M = CourierZoomTrackerMotion
    typealias Cam = CourierZoomTrackerCam

    private static let maxBuildings = 240
    private static let minHeightScale = 0.22
    private static let floorH = 3.2

    static func argb(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> UInt32 {
        let ia = UInt32((min(max(a, 0), 1) * 255).rounded())
        func c(_ v: Double) -> UInt32 { UInt32(min(max(v, 0), 255).rounded()) }
        return (ia << 24) | (c(r) << 16) | (c(g) << 8) | c(b)
    }

    static func color(_ v: UInt32) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255,
              opacity: Double(v >> 24) / 255)
    }

    /// A box from its four clockwise screen corners, raised by (ex, ey) per metre from z0 to z1: the walls that face the
    /// viewer (shaded by a light from the upper left, with windows when `unit` > 0), then the top.
    private static func box(_ out: inout CourierZoomTrackerPolys, _ c: [CGPoint], _ ex: Double, _ ey: Double, _ z0: Double, _ z1: Double,
                            _ wall: [Double], _ top: [Double], _ a: Double, _ unit: Double) {
        for e in 0..<4 {
            let A = c[e], B = c[(e + 1) % 4]
            let dx = B.x - A.x, dy = B.y - A.y
            let dl = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
            let nx = dy / dl, ny = -dx / dl
            if nx * M.extrudeX - ny >= 0 { continue }
            let lit = 0.92 + 0.08 * (nx * -0.6 + ny * -0.8)
            out.add(argb(wall[0] * lit, wall[1] * lit, wall[2] * lit, a), [
                CGPoint(x: A.x + ex * z0, y: A.y + ey * z0), CGPoint(x: B.x + ex * z0, y: B.y + ey * z0),
                CGPoint(x: B.x + ex * z1, y: B.y + ey * z1), CGPoint(x: A.x + ex * z1, y: A.y + ey * z1),
            ])
            if unit > 0 {
                let lenM = dl / unit
                let cols = min(Int(floor(lenM / 3.2)), 10)
                let floors = min(Int(floor((z1 - z0) / floorH)), 6)
                if cols < 1 || floors < 1 { continue }
                let g = argb(wall[0] * lit * 0.58 + 40, wall[1] * lit * 0.62 + 46, wall[2] * lit * 0.68 + 58, a)
                let hw = 0.62 / lenM
                for f in 0..<floors {
                    let za = z0 + Double(f) * floorH + 1.0, zb = za + 1.45
                    for w in 0..<cols {
                        let m = (Double(w) + 0.5) / Double(cols)
                        let u0 = m - hw, u1 = m + hw
                        out.add(g, [
                            CGPoint(x: A.x + dx * u0 + ex * za, y: A.y + dy * u0 + ey * za),
                            CGPoint(x: A.x + dx * u1 + ex * za, y: A.y + dy * u1 + ey * za),
                            CGPoint(x: A.x + dx * u1 + ex * zb, y: A.y + dy * u1 + ey * zb),
                            CGPoint(x: A.x + dx * u0 + ex * zb, y: A.y + dy * u0 + ey * zb),
                        ])
                    }
                }
            }
        }
        out.add(argb(top[0], top[1], top[2], a), c.map { CGPoint(x: $0.x + ex * z1, y: $0.y + ey * z1) })
    }

    private static func rectCorners(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ cam: Cam) -> [CGPoint] {
        [cam.toScreen(x0, y0), cam.toScreen(x1, y0), cam.toScreen(x1, y1), cam.toScreen(x0, y1)]
    }

    private static func orientedCorners(_ x: Double, _ y: Double, _ hx: Double, _ hy: Double, _ back: Double, _ front: Double, _ half: Double,
                                        _ cam: Cam) -> [CGPoint] {
        let rx = -hy, ry = hx
        let al = [back, front, front, back], ac = [-half, -half, half, half]
        var c = (0..<4).map { k in cam.toScreen(x + hx * al[k] + rx * ac[k], y + hy * al[k] + ry * ac[k]) }
        let cross = (c[1].x - c[0].x) * (c[2].y - c[0].y) - (c[1].y - c[0].y) * (c[2].x - c[0].x)
        if cross < 0 { c.swapAt(1, 3) }
        return c
    }

    private static let vanBody = [64.0, 118, 255]
    private static let vanSide = [38.0, 88, 222]
    private static let vanGlass = [28.0, 36, 52]

    private static func van(_ out: inout CourierZoomTrackerPolys, _ x: Double, _ y: Double, _ hx: Double, _ hy: Double, _ cam: Cam,
                            _ ex: Double, _ ey: Double, _ a: Double) {
        let c = orientedCorners(x, y, hx, hy, -3.1, 1.3, 1.1, cam)
        let cab = orientedCorners(x, y, hx, hy, 1.3, 3.0, 1.05, cam)
        let kb = (c[0].x + c[2].x) * M.extrudeX - (c[0].y + c[2].y)
        let kc = (cab[0].x + cab[2].x) * M.extrudeX - (cab[0].y + cab[2].y)
        if kc > kb {
            box(&out, cab, ex, ey, 0.35, 2.05, vanSide, vanBody, a, -1)
            box(&out, c, ex, ey, 0.35, 2.7, vanSide, vanBody, a, -1)
        } else {
            box(&out, c, ex, ey, 0.35, 2.7, vanSide, vanBody, a, -1)
            box(&out, cab, ex, ey, 0.35, 2.05, vanSide, vanBody, a, -1)
        }
        let glass = orientedCorners(x, y, hx, hy, 1.75, 2.85, 0.92, cam).map { CGPoint(x: $0.x + ex * 2.06, y: $0.y + ey * 2.06) }
        out.add(argb(vanGlass[0], vanGlass[1], vanGlass[2], a), glass)
        let stripe = orientedCorners(x, y, hx, hy, -2.8, 1.0, 0.22, cam).map { CGPoint(x: $0.x + ex * 2.71, y: $0.y + ey * 2.71) }
        out.add(argb(255, 255, 255, a), stripe)
    }

    /// The 3D layer for one frame. `van` is x, y, heading, visibility, model 0..1.
    static func cityFrame(_ cam: Cam, _ lay: CourierZoomTrackerLay, van vs: (x: Double, y: Double, hx: Double, hy: Double, a: Double, model: Double))
        -> CourierZoomTrackerPolys {
        var out = CourierZoomTrackerPolys()
        let h0 = cam.heightScale
        let hs = h0 * M.smooth01((h0 - minHeightScale) / 0.35)
        let showVan = vs.a * vs.model > 0.003
        if h0 < minHeightScale && !showVan { return out }
        let ex = M.extrudeX * hs, ey = -hs
        let W = lay.w
        let bottom = min(lay.panel + 60, lay.h)
        var x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9
        let sxs = [-30, W + 30, W + 30, -30], sys = [-30, -30, bottom + 50 * hs, bottom + 50 * hs]
        for k in 0..<4 {
            let w = cam.toWorld(sxs[k], sys[k])
            x0 = min(x0, w.0); x1 = max(x1, w.0); y0 = min(y0, w.1); y1 = max(y1, w.1)
        }
        let ia = max(City.i0, Int(floor(x0 / City.bx))), ib = min(City.i1 - 1, Int(floor(x1 / City.bx)))
        let ja = max(City.j0, Int(floor(y0 / City.by))), jb = min(City.j1 - 1, Int(floor(y1 / City.by)))
        let B = City.buildings.b, start = City.buildings.start
        var idx: [Int] = [], key: [Double] = [], cor: [[CGPoint]] = []
        if h0 > minHeightScale && ia <= ib && ja <= jb {
            outer: for i in ia...ib {
                for j in ja...jb {
                    let bi = (i - City.i0) * City.njt + (j - City.j0)
                    for b in start[bi]..<start[bi + 1] {
                        if idx.count >= maxBuildings { break outer }
                        let corner = rectCorners(B[b * 5], B[b * 5 + 1], B[b * 5 + 2], B[b * 5 + 3], cam)
                        let h = B[b * 5 + 4]
                        var mnx = 1e9, mxx = -1e9, mny = 1e9, mxy = -1e9
                        for p in corner { mnx = min(mnx, p.x); mxx = max(mxx, p.x); mny = min(mny, p.y); mxy = max(mxy, p.y) }
                        if mxx + ex * h < -2 || mnx > W + 2 || mxy < -2 || mny + ey * h > bottom { continue }
                        idx.append(b)
                        key.append((corner[0].x + corner[2].x) / 2 * M.extrudeX - (corner[0].y + corner[2].y) / 2)
                        cor.append(corner)
                    }
                }
            }
        }
        let n = idx.count
        if showVan {
            let vc = orientedCorners(vs.x, vs.y, vs.hx, vs.hy, -3.1, 3.0, 1.1, cam)
            idx.append(-1)
            key.append((vc[0].x + vc[2].x) / 2 * M.extrudeX - (vc[0].y + vc[2].y) / 2)
            cor.append(vc)
        }
        // painter's order: the furthest along the raise direction first (stable, like the insertion sort it ports)
        let order = (0..<idx.count).sorted { a, b in key[a] != key[b] ? key[a] > key[b] : a < b }
        let unit = hs > 1.9 && n <= 28 ? cam.s : -1
        let tallWall = [208.0, 214, 224], tallTop = [234.0, 238, 243]
        let homeWall = [238.0, 196, 160], homeTop = [250.0, 222, 196]
        for k in order {
            let b = idx[k]
            if b < 0 {
                van(&out, vs.x, vs.y, vs.hx, vs.hy, cam, ex, ey, vs.a * vs.model)
                continue
            }
            let h = B[b * 5 + 4]
            let v = Double((b * 7919) % 97) / 97
            var wall: [Double], top: [Double]
            if b == City.buildings.home {
                wall = homeWall; top = homeTop
            } else if h > 24 {
                wall = tallWall.map { $0 - 6 * v }; top = tallTop.map { $0 - 4 * v }
            } else if h <= 12 && B[b * 5 + 2] - B[b * 5] < 14 {
                let f = (b * 3) % 4
                wall = [f == 0 ? 240 : f == 1 ? 232 : f == 2 ? 220 : 236, f == 0 ? 232 : f == 1 ? 218 : f == 2 ? 228 : 226,
                        f == 0 ? 220 : f == 1 ? 204 : f == 2 ? 232 : 218]
                top = [246 - 3 * v, 243 - 3 * v, 238 - 2 * v]
            } else {
                wall = [229 - 6 * v, 223 - 6 * v, 213 - 3 * v]
                top = [250 - 4 * v, 248 - 4 * v, 243 - 2 * v]
            }
            let c = cor[k]
            box(&out, c, ex, ey, 0, h, wall, top, 1, unit)
            if b == City.buildings.home {
                // the front door on the south wall (corner 2 → corner 3)
                let dx0 = B[b * 5], dx1 = B[b * 5 + 2]
                let fa = (City.doorX - 0.75 - dx0) / (dx1 - dx0), fb = (City.doorX + 0.75 - dx0) / (dx1 - dx0)
                let sx = c[3].x - c[2].x, sy = c[3].y - c[2].y
                let ba = 1 - fa, bb = 1 - fb
                let q0 = CGPoint(x: c[2].x + sx * bb, y: c[2].y + sy * bb)
                let q1 = CGPoint(x: c[2].x + sx * ba, y: c[2].y + sy * ba)
                out.add(argb(44, 78, 74, 1), [q0, q1, CGPoint(x: q1.x + ex * 2.3, y: q1.y + ey * 2.3), CGPoint(x: q0.x + ex * 2.3, y: q0.y + ey * 2.3)])
            }
        }
        return out
    }

    /// How much the van is drawn as a 3D model rather than the location dot.
    static func vanModelK(_ s: Double) -> Double { M.smooth01((s - 1.5) / 1.1) }

    static let routeBlue = City.c(0x2F6BFF)
    static let travelled = City.c(0xA9B0BD)

    /// The whole map for one frame.
    static func map(_ ctx: inout GraphicsContext, cam: Cam, lay: CourierZoomTrackerLay, vanD: Double, vanA: Double, travEnd: Double,
                    travA: Double, doorPulse: Double) {
        let m = City.map
        // the ground in view (padded) for the tiled layers
        var x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9
        for (sx, sy) in [(-40.0, -200.0), (lay.w + 40, -200), (lay.w + 40, lay.h + 40), (-40, lay.h + 40)] {
            let w = cam.toWorld(sx, sy)
            x0 = min(x0, w.0); x1 = max(x1, w.0); y0 = min(y0, w.1); y1 = max(y1, w.1)
        }
        let box = CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)
        var w = ctx
        w.translateBy(x: cam.fx, y: cam.fy)
        w.scaleBy(x: 1, y: cam.squash)
        w.concatenate(CGAffineTransform(a: cam.cosB, b: cam.sinB, c: -cam.sinB, d: cam.cosB, tx: 0, ty: 0))
        w.scaleBy(x: cam.s, y: cam.s)
        w.translateBy(x: -cam.cx, y: -cam.cy)
        let P = City.Palette.self
        w.fill(m.parks, with: .color(P.park))
        w.fill(m.pond, with: .color(P.water))
        w.fill(m.parkPaths, with: .color(P.parkPath))
        w.fill(m.river, with: .color(P.water))
        w.fill(m.riverEdge, with: .color(P.waterEdge))
        m.minorCasing.fill(&w, box, P.casing)
        m.minorFill.fill(&w, box, P.minor)
        m.aveCasing.fill(&w, box, P.aveCasing)
        m.aveFill.fill(&w, box, P.avenue)
        m.aveLine.fill(&w, box, P.aveLine)
        w.fill(m.zebra, with: .color(.white))
        w.fill(m.gardens, with: .color(P.garden))
        w.fill(m.treesDark, with: .color(P.treeDark))
        w.fill(m.trees, with: .color(P.tree))
        m.footprints.fill(&w, box, P.footprint)

        // the route, and the travelled part greyed
        let zoom = cam.s
        let round = { (lw: Double) in StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round) }
        w.stroke(City.routePath, with: .color(.white), style: round(10 / zoom))
        w.stroke(City.routePath, with: .color(routeBlue), style: round(6.5 / zoom))
        if travEnd > 0.0005 && travA > 0.001 {
            var g = w
            g.opacity = M.clamp01(travA)
            g.stroke(City.routePath.trimmedPath(from: 0, to: travEnd), with: .color(travelled), style: round(6.5 / zoom))
        }
        // a ring on the pavement at the door once close, and one wide pulse on arrival
        let ringC = CGPoint(x: City.doorX, y: City.home.y1 + 2.2)
        let ringOpacity = M.smooth01((zoom - 1.6) / 1.2)
        if ringOpacity > 0.001 {
            var g = w
            g.opacity = ringOpacity
            g.stroke(Path(ellipseIn: CGRect(x: ringC.x - 3.2, y: ringC.y - 3.2, width: 6.4, height: 6.4)), with: .color(routeBlue), lineWidth: 1.6 / zoom)
        }
        if doorPulse > 0 {
            var g = w
            g.opacity = 1 - doorPulse
            let r = 3.2 + 10 * doorPulse
            g.stroke(Path(ellipseIn: CGRect(x: ringC.x - r, y: ringC.y - r, width: 2 * r, height: 2 * r)), with: .color(routeBlue), lineWidth: 1.6 / zoom)
        }

        // the raised buildings and the van, in screen space
        let r = M.routeAt(vanD)
        let va = M.clamp01(vanA)
        let model = vanModelK(cam.s)
        let list = cityFrame(cam, lay, van: (r.x, r.y, r.hx, r.hy, va, model))
        if va * model > 0.01 {
            let al = [-3.3, 3.2, 3.2, -3.3], ac = [-1.25, -1.25, 1.25, 1.25]
            var sh = Path()
            for k in 0..<4 {
                let p = cam.toScreen(r.x + r.hx * al[k] - r.hy * ac[k] + 0.5, r.y + r.hy * al[k] + r.hx * ac[k] + 0.4)
                if k == 0 { sh.move(to: p) } else { sh.addLine(to: p) }
            }
            sh.closeSubpath()
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: max(1.5, 0.9 * cam.s)))
                l.fill(sh, with: .color(Color(.sRGB, red: 40 / 255, green: 44 / 255, blue: 52 / 255, opacity: 0.28 * va * model)))
            }
        }
        var k = 0, p = 0
        while k < list.colors.count {
            let col = list.colors[k]
            var path = Path()
            while k < list.colors.count && list.colors[k] == col {
                let n = list.counts[k]
                path.move(to: list.pts[p])
                for q in 1..<n { path.addLine(to: list.pts[p + q]) }
                path.closeSubpath()
                p += n
                k += 1
            }
            ctx.fill(path, with: .color(color(col)))
        }
        // the location dot while the van is far: a white disc, a blue core and a heading tip
        let dot = va * (1 - model)
        if dot > 0.01 {
            let c = cam.toScreen(r.x, r.y)
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 2))
                l.fill(Path(ellipseIn: CGRect(x: c.x - 12, y: c.y + 1.5 - 12, width: 24, height: 24)),
                       with: .color(Color(.sRGB, red: 20 / 255, green: 30 / 255, blue: 60 / 255, opacity: 0.25 * dot)))
            }
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 11.5, y: c.y - 11.5, width: 23, height: 23)), with: .color(.white.opacity(dot)))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 8, y: c.y - 8, width: 16, height: 16)), with: .color(routeBlue.opacity(dot)))
            let tip = cam.toScreen(r.x + r.hx * 50, r.y + r.hy * 50)
            let dx = tip.x - c.x, dy = tip.y - c.y
            let dl = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
            let ux = dx / dl, uy = dy / dl
            var tri = Path()
            tri.move(to: CGPoint(x: c.x + ux * 6, y: c.y + uy * 6))
            tri.addLine(to: CGPoint(x: c.x - ux * 3 - uy * 4, y: c.y - uy * 3 + ux * 4))
            tri.addLine(to: CGPoint(x: c.x - ux * 1.2, y: c.y - uy * 1.2))
            tri.addLine(to: CGPoint(x: c.x - ux * 3 + uy * 4, y: c.y - uy * 3 - ux * 4))
            tri.closeSubpath()
            ctx.fill(tri, with: .color(.white.opacity(dot)))
        }
    }

    /// The film's ghost finger: a soft shadow, a translucent white body, a thin rim; pressed, it sits lower.
    static func finger(_ ctx: inout GraphicsContext, _ x: Double, _ y: Double, _ vis: Double, _ press: Double) {
        guard vis >= 0.004 else { return }
        let lift = 1 - press
        let r = 21 * (1 + 0.12 * lift)
        let sc = CGPoint(x: x, y: y + 3 + 4 * lift)
        ctx.fill(Path(ellipseIn: CGRect(x: sc.x - r * 1.5, y: sc.y - r * 1.5, width: r * 3, height: r * 3)),
                 with: .radialGradient(Gradient(stops: [.init(color: .black.opacity(0.22 * vis), location: 0.4), .init(color: .black.opacity(0), location: 1)]),
                                       center: sc, startRadius: 0, endRadius: r * 1.5))
        ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(0.6 * vis)))
        ctx.fill(Path(ellipseIn: CGRect(x: x - r * 0.6, y: y - r * 0.6, width: r * 1.2, height: r * 1.2)), with: .color(.white.opacity(0.35 * vis)))
        ctx.stroke(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
                   with: .color(Color(.sRGB, red: 150 / 255, green: 160 / 255, blue: 180 / 255, opacity: 0.9 * vis)), lineWidth: 1.2)
    }
}
