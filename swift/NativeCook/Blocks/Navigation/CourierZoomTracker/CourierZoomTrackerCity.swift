import SwiftUI

// The invented city the courier drives through, in metres (x east, y south, like the screen): the street grid, the
// river, the parks, about 4,600 building footprints with heights, the courier's route (along the streets, corners
// rounded, on the right-hand lane) and the static map's paths. Seeded, so every run and every port draws the same city.
enum CourierZoomTrackerCity {
    static let bx = 120.0
    static let by = 90.0
    static let i0 = -7, i1 = 33, j0 = -10, j1 = 44
    static let njt = j1 - j0
    static let cityX0 = Double(i0) * bx, cityY0 = Double(j0) * by, cityX1 = Double(i1) * bx, cityY1 = Double(j1) * by
    /// The street grid carries on past the buildings into the outskirts.
    static let oi0 = -22, oi1 = 48, oj0 = -24, oj1 = 58

    static let minor = 14.0
    static let avenue = 24.0
    static let aveI = [7, 17]
    static let aveJ = [14, 33]
    static let bridges = [3, 7, 12, 17, 22]
    static let riverHalf = 72.0

    static func c(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    enum Palette {
        static let land = c(0xF2EFE9)
        static let park = c(0xCFE5C2)
        static let parkPath = c(0xE4F0DA)
        static let water = c(0xAFD3EC)
        static let waterEdge = c(0x9DC6E2)
        static let casing = c(0xDDD7CC)
        static let minor = c(0xFFFFFF)
        static let aveCasing = c(0xE6D6A5)
        static let avenue = c(0xFFF0C4)
        static let aveLine = c(0xE9D79E)
        static let footprint = c(0xE5E0D7)
        static let garden = c(0xD9EBCB)
        static let tree = c(0xB8D9A4)
        static let treeDark = c(0xA5CC8F)
    }

    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static func riverY(_ x: Double) -> Double { 2310 + 120 * sin(x / 700) + 55 * sin(x / 260 + 1) }

    static func isAveI(_ i: Int) -> Bool { aveI.contains(i) }
    static func isAveJ(_ j: Int) -> Bool { aveJ.contains(j) }

    static func parkKind(_ i: Int, _ j: Int) -> Int {
        if i >= 2 && i <= 5 && j >= 30 && j <= 35 { return 1 }
        if (i == 10 && j == 20) || (i == 19 && j == 40) || (i == 14 && j == 5) || (i == 22 && j == 27) || (i == 18 && j == 11) || (i == 19 && j == 8) { return 1 }
        let cx = (Double(i) + 0.5) * bx, cy = (Double(j) + 0.5) * by
        let d = abs(cy - riverY(cx))
        if d < riverHalf + 50 { return 2 }
        if d < riverHalf + 120 { return 1 }
        return 0
    }

    // MARK: the courier's home stop

    static let home = (x0: 2313.0, y0: 698.0, x1: 2325.0, y1: 710.0, h: 10.0)
    static let doorX = 2319.0
    static let stop = (x: 2319.0, y: 716.5)
    static let hub = (x: 843.5, y: 2700.0)

    // MARK: buildings

    static func isTerrace(_ i: Int, _ j: Int) -> Bool { i >= 18 && i <= 20 && j >= 6 && j <= 8 }
    static let rowDepth = 12.0

    /// Footprints and heights, five numbers each (x0, y0, x1, y1, h), grouped by block; the block start table; the
    /// customer's house.
    static let buildings: (b: [Double], start: [Int], home: Int) = {
        var b: [Double] = []
        var start: [Int] = []
        var homeIndex = -1
        func push(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ h: Double) { b += [x0, y0, x1, y1, h] }
        func heightAt(_ cx: Double, _ cy: Double, _ seed: Double) -> Double {
            let dt = hypot(cx - 1500, cy - 2050)
            let town = max(0, 1 - dt / 1100)
            let r = hash(seed)
            let base = 7 + 8 * r
            return (base + town * town * (16 + 30 * hash(seed + 3.1))).rounded(.toNearestOrAwayFromZero)
        }
        func terraceRow(_ x0: Double, _ x1: Double, _ y0: Double, _ y1: Double, _ seed: Double) {
            var x = x0
            var k = 0.0
            while x1 - x > 7 {
                var w = 10 + 3 * hash(seed + k * 1.7)
                if x1 - x - w < 8 { w = x1 - x }
                push(x + 0.15, y0, x + w - 0.15, y1, ((8.5 + 3 * hash(seed + k * 2.3 + 0.5)) * 2).rounded(.toNearestOrAwayFromZero) / 2)
                x += w
                k += 1
            }
        }
        for i in i0..<i1 {
            for j in j0..<j1 {
                start.append(b.count / 5)
                if parkKind(i, j) > 0 { continue }
                func hw(_ s: Bool) -> Double { (s ? avenue / 2 : minor / 2) + 3 }
                let x0 = Double(i) * bx + hw(isAveI(i))
                let x1 = Double(i + 1) * bx - hw(isAveI(i + 1))
                let y0 = Double(j) * by + hw(isAveJ(j))
                let y1 = Double(j + 1) * by - hw(isAveJ(j + 1))
                if isTerrace(i, j) {
                    let seed = Double(i * 53 + j * 11)
                    terraceRow(x0, x1, y0, y0 + rowDepth, seed)
                    if i == 19 && j == 7 {
                        terraceRow(x0, home.x0, y1 - rowDepth, y1, seed + 40)
                        homeIndex = b.count / 5
                        push(home.x0 + 0.15, home.y0, home.x1 - 0.15, home.y1, home.h)
                        terraceRow(home.x1, x1, y1 - rowDepth, y1, seed + 80)
                    } else {
                        terraceRow(x0, x1, y1 - rowDepth, y1, seed + 40)
                    }
                    continue
                }
                let seed = Double(i * 131 + j * 17)
                let layout = hash(seed + 0.7)
                let cols = layout < 0.55 ? 2 : layout < 0.8 ? 3 : 1
                let rows = hash(seed + 1.9) < 0.7 ? 2 : 1
                let cw = (x1 - x0) / Double(cols)
                let ch = (y1 - y0) / Double(rows)
                for a in 0..<cols {
                    for bb in 0..<rows {
                        let k = seed * 7 + Double(a * 3 + bb * 5)
                        if hash(k + 4.4) < 0.07 { continue }
                        let g = 1.5
                        let sx0 = x0 + Double(a) * cw + (a == 0 ? 0 : g) + 2 * hash(k + 1.1)
                        let sx1 = x0 + Double(a + 1) * cw - (a == cols - 1 ? 0 : g) - 2 * hash(k + 2.2)
                        let sy0 = y0 + Double(bb) * ch + (bb == 0 ? 0 : g) + (rows == 2 && bb == 1 ? 3 * hash(k + 5.5) : 0)
                        let sy1 = y0 + Double(bb + 1) * ch - (bb == rows - 1 ? 0 : g) - (rows == 2 && bb == 0 ? 3 * hash(k + 6.6) : 0)
                        push(sx0, sy0, sx1, sy1, heightAt((sx0 + sx1) / 2, (sy0 + sy1) / 2, k))
                    }
                }
            }
        }
        start.append(b.count / 5)
        return (b, start, homeIndex)
    }()

    // MARK: the route

    static let routeStep = 3.0

    static let route: (x: [Double], y: [Double]) = {
        let way: [(Double, Double)] = [(7 * bx, 30 * by), (7 * bx, 14 * by), (17 * bx, 14 * by), (17 * bx, 10 * by),
                                       (21 * bx, 10 * by), (21 * bx, 8 * by), (doorX, 8 * by)]
        let fillet = 26.0, lane = 3.5
        var pts: [(Double, Double)] = [way[0]]
        for k in 1..<(way.count - 1) {
            let (ax, ay) = way[k - 1], (bx_, by_) = way[k], (cx, cy) = way[k + 1]
            let l1 = hypot(bx_ - ax, by_ - ay), l2 = hypot(cx - bx_, cy - by_)
            let p0 = (bx_ - (bx_ - ax) / l1 * fillet, by_ - (by_ - ay) / l1 * fillet)
            let p2 = (bx_ + (cx - bx_) / l2 * fillet, by_ + (cy - by_) / l2 * fillet)
            for s in 0...12 {
                let u = Double(s) / 12
                pts.append(((1 - u) * (1 - u) * p0.0 + 2 * (1 - u) * u * bx_ + u * u * p2.0,
                            (1 - u) * (1 - u) * p0.1 + 2 * (1 - u) * u * by_ + u * u * p2.1))
            }
        }
        pts.append(way[way.count - 1])
        var cum = [0.0]
        for k in 1..<pts.count { cum.append(cum[k - 1] + hypot(pts[k].0 - pts[k - 1].0, pts[k].1 - pts[k - 1].1)) }
        let total = cum[cum.count - 1]
        let n = Int(floor(total / routeStep))
        var cx: [Double] = [], cy: [Double] = []
        var sg = 0
        for s in 0...n {
            let d = s == n ? total : Double(s) * routeStep
            while sg < cum.count - 2 && cum[sg + 1] < d { sg += 1 }
            let k = (d - cum[sg]) / max(cum[sg + 1] - cum[sg], 1e-6)
            cx.append(pts[sg].0 + (pts[sg + 1].0 - pts[sg].0) * k)
            cy.append(pts[sg].1 + (pts[sg + 1].1 - pts[sg].1) * k)
        }
        var x: [Double] = [], y: [Double] = []
        for s in 0..<cx.count {
            let a = max(s - 2, 0), b = min(s + 2, cx.count - 1)
            let hx = cx[b] - cx[a], hy = cy[b] - cy[a]
            let hl = hypot(hx, hy) == 0 ? 1 : hypot(hx, hy)
            x.append(cx[s] + (-hy / hl) * lane)
            y.append(cy[s] + (hx / hl) * lane)
        }
        x[x.count - 1] = stop.x
        y[y.count - 1] = stop.y
        return (x, y)
    }()

    static var routeLen: Double { Double(route.x.count - 1) * routeStep }

    static let routePath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: route.x[0], y: route.y[0]))
        for k in 1..<route.x.count { p.addLine(to: CGPoint(x: route.x[k], y: route.y[k])) }
        return p
    }()

    // MARK: the static map

    static func streetInWater(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Bool {
        for s in 0...4 {
            let x = x0 + (x1 - x0) * Double(s) / 4, y = y0 + (y1 - y0) * Double(s) / 4
            if abs(y - riverY(x)) < riverHalf + 4 { return true }
        }
        return false
    }

    /// Side of a map tile (m): the big layers are cut into tiles so a close view fills only the few it sees.
    static let tile = 960.0

    struct Tiled {
        var tiles: [Int: Path] = [:]
        static func key(_ tx: Int, _ ty: Int) -> Int { (tx + 100) * 1000 + (ty + 100) }
        mutating func add(_ x: Double, _ y: Double, _ body: (inout Path) -> Void) {
            let k = Self.key(Int(floor(x / tile)), Int(floor(y / tile)))
            var p = tiles[k] ?? Path()
            body(&p)
            tiles[k] = p
        }
        func stroked(_ style: StrokeStyle) -> Tiled {
            var t = Tiled()
            for (k, p) in tiles { t.tiles[k] = p.strokedPath(style) }
            return t
        }
        /// Every tile that may hold geometry in the world box (padded by one tile for strokes past a tile's edge).
        func fill(_ ctx: inout GraphicsContext, _ box: CGRect, _ color: Color) {
            let tx0 = Int(floor(box.minX / tile)) - 1, tx1 = Int(floor(box.maxX / tile)) + 1
            let ty0 = Int(floor(box.minY / tile)) - 1, ty1 = Int(floor(box.maxY / tile)) + 1
            var all = Path()
            for tx in tx0...tx1 { for ty in ty0...ty1 { if let p = tiles[Self.key(tx, ty)] { all.addPath(p) } } }
            ctx.fill(all, with: .color(color))
        }
    }

    struct MapLayers {
        var parks = Path()
        var pond = Path()
        var parkPaths = Path()
        var river = Path()
        var riverEdge = Path()
        var minorCasing = Tiled(), minorFill = Tiled(), aveCasing = Tiled(), aveFill = Tiled(), aveLine = Tiled()
        var zebra = Path()
        var gardens = Path()
        var treesDark = Path()
        var trees = Path()
        var footprints = Tiled()
    }

    static let map: MapLayers = {
        var m = MapLayers()
        for i in i0..<i1 {
            for j in j0..<j1 where parkKind(i, j) > 0 {
                m.parks.addRect(CGRect(x: Double(i) * bx - 1, y: Double(j) * by - 1, width: bx + 2, height: by + 2))
            }
        }
        m.pond.addEllipse(in: CGRect(x: 3 * bx + 10, y: 31 * by + 20, width: 170, height: 110))
        var paths = Path()
        paths.move(to: CGPoint(x: 2 * bx, y: 33.6 * by))
        paths.addCurve(to: CGPoint(x: 6 * bx, y: 33 * by), control1: CGPoint(x: 3 * bx, y: 32 * by), control2: CGPoint(x: 4.6 * bx, y: 36 * by))
        paths.move(to: CGPoint(x: 4.4 * bx, y: 30 * by))
        paths.addCurve(to: CGPoint(x: 4.6 * bx, y: 36 * by), control1: CGPoint(x: 4 * bx, y: 32 * by), control2: CGPoint(x: 5.4 * bx, y: 34 * by))
        m.parkPaths = paths.strokedPath(StrokeStyle(lineWidth: 5))

        var river = Path()
        var xs: [Double] = []
        var x = Double(oi0) * bx
        while x <= Double(oi1) * bx { xs.append(x); x += 20 }
        river.move(to: CGPoint(x: xs[0], y: riverY(xs[0]) - riverHalf))
        for k in 1..<xs.count { river.addLine(to: CGPoint(x: xs[k], y: riverY(xs[k]) - riverHalf)) }
        for k in stride(from: xs.count - 1, through: 0, by: -1) { river.addLine(to: CGPoint(x: xs[k], y: riverY(xs[k]) + riverHalf)) }
        river.closeSubpath()
        m.river = river
        m.riverEdge = river.strokedPath(StrokeStyle(lineWidth: 3))

        var minorT = Tiled(), avesT = Tiled()
        for i in oi0...oi1 {
            for j in oj0..<oj1 {
                let x = Double(i) * bx
                if streetInWater(x, Double(j) * by, x, Double(j + 1) * by) && !bridges.contains(i) { continue }
                let seg: (inout Path) -> Void = { p in
                    p.move(to: CGPoint(x: x, y: Double(j) * by))
                    p.addLine(to: CGPoint(x: x, y: Double(j + 1) * by))
                }
                if isAveI(i) { avesT.add(x, (Double(j) + 0.5) * by, seg) } else { minorT.add(x, (Double(j) + 0.5) * by, seg) }
            }
        }
        for j in oj0...oj1 {
            for i in oi0..<oi1 {
                let y = Double(j) * by
                if streetInWater(Double(i) * bx, y, Double(i + 1) * bx, y) { continue }
                let seg: (inout Path) -> Void = { p in
                    p.move(to: CGPoint(x: Double(i) * bx, y: y))
                    p.addLine(to: CGPoint(x: Double(i + 1) * bx, y: y))
                }
                if isAveJ(j) { avesT.add((Double(i) + 0.5) * bx, y, seg) } else { minorT.add((Double(i) + 0.5) * bx, y, seg) }
            }
        }
        // strokes turned into fills once: the map only fills paths per frame
        m.minorCasing = minorT.stroked(StrokeStyle(lineWidth: minor + 2, lineCap: .square))
        m.minorFill = minorT.stroked(StrokeStyle(lineWidth: minor, lineCap: .square))
        m.aveCasing = avesT.stroked(StrokeStyle(lineWidth: avenue + 2.5, lineCap: .square))
        m.aveFill = avesT.stroked(StrokeStyle(lineWidth: avenue, lineCap: .square))
        m.aveLine = avesT.stroked(StrokeStyle(lineWidth: 0.7, lineCap: .butt, dash: [6, 7], dashPhase: 0))

        func zebraAt(_ x: Double, _ y: Double, _ vertical: Bool) {
            for s in -3...3 {
                let o = Double(s) * 1.8 - 0.45
                if vertical { m.zebra.addRect(CGRect(x: x + o, y: y - 2.5, width: 0.9, height: 5)) }
                else { m.zebra.addRect(CGRect(x: x - 2.5, y: y + o, width: 5, height: 0.9)) }
            }
        }
        for (i, j) in [(19, 8), (20, 8), (21, 8), (21, 9), (20, 7), (19, 9)] {
            let x = Double(i) * bx, y = Double(j) * by
            zebraAt(x - 12, y, false)
            zebraAt(x + 12, y, false)
            zebraAt(x, y - 12, true)
            zebraAt(x, y + 12, true)
        }
        func tree(_ x: Double, _ y: Double, _ r: Double, _ off: Double) {
            m.treesDark.addEllipse(in: CGRect(x: x + off - r, y: y + off - r, width: 2 * r, height: 2 * r))
            m.trees.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }
        for i in 16...23 {
            for j in 5...11 where parkKind(i, j) == 0 {
                let x0 = Double(i) * bx, y0 = Double(j) * by
                var s = 14.0
                while s < bx - 10 {
                    let k = Double(i * 977 + j * 31) + s
                    tree(x0 + s, y0 + minor / 2 + 1.3, 1.7 + 0.7 * hash(k), 0.6)
                    s += 13
                }
            }
        }
        for i in i0..<i1 {
            for j in j0..<j1 where isTerrace(i, j) && parkKind(i, j) == 0 {
                let gx0 = Double(i) * bx + minor / 2 + 3
                let gy0 = Double(j) * by + minor / 2 + 3 + rowDepth
                let gw = bx - minor - 6
                let gh = by - minor - 6 - rowDepth * 2
                m.gardens.addRect(CGRect(x: gx0, y: gy0, width: gw, height: gh))
                for k in 0..<9 {
                    let s = Double(i * 31 + j * 7 + k)
                    tree(gx0 + 5 + (gw - 10) * hash(s + 0.3), gy0 + 4 + (gh - 8) * hash(s + 0.9), 2 + 1.5 * hash(s + 1.7), 0.7)
                }
            }
        }
        for k in 0..<26 {
            let fk = Double(k)
            tree(19 * bx + 14 + (bx - 28) * hash(fk * 3.1 + 0.2), 8 * by + 14 + (by - 28) * hash(fk * 5.3 + 0.7), 2.6 + 1.8 * hash(fk * 7.7), 0.8)
        }
        let b = buildings.b
        var k = 0
        while k < b.count {
            let r = CGRect(x: b[k], y: b[k + 1], width: b[k + 2] - b[k], height: b[k + 3] - b[k + 1])
            m.footprints.add(b[k], b[k + 1]) { $0.addRect(r) }
            k += 5
        }
        return m
    }()
}
