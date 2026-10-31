import SwiftUI

struct PerfectMonthGemArt {
    typealias M = PerfectMonthGemMotion
    let resources = PerfectMonthGemResources.shared
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func rgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
        Color(.sRGB, red: r.rounded() / 255, green: g.rounded() / 255, blue: b.rounded() / 255, opacity: a)
    }
    static func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
    }
    static func polygon(_ points: [CGPoint]) -> Path {
        var p = Path()
        if let first = points.first { p.move(to: first) }
        for point in points.dropFirst() { p.addLine(to: point) }
        p.closeSubpath()
        return p
    }
    // Static topology and source paths are made once; only the projected coordinates change.
    struct Vector { var x, y, z: Double }
    struct Facet { var vertices: [Vector]; var kind, order: Int }
    static let facets: [Facet] = {
        let table = (0..<8).map { i -> Vector in
            let a = Double(i) / 8 * .pi * 2 + .pi / 8
            return Vector(x: cos(a) * 0.52, y: sin(a) * 0.52, z: 0.36)
        }
        let girdle = (0..<8).map { i -> Vector in
            let a = Double(i) / 8 * .pi * 2 + .pi / 8
            return Vector(x: cos(a), y: sin(a), z: 0)
        }
        var facets = [Facet(vertices: table, kind: 0, order: 0)]
        for i in 0..<8 {
            let j = (i + 1) % 8
            facets.append(Facet(vertices: [table[i], girdle[i], girdle[j], table[j]], kind: 1, order: 1 + i))
            facets.append(Facet(vertices: [girdle[i], Vector(x: 0, y: 0, z: -0.95), girdle[j]], kind: 2, order: 9 + i))
        }
        return facets
    }()
    static let rays: [Path] = (0..<12).map { i in
        let a = Double(i) / 12 * .pi * 2
        return polygon([.zero, CGPoint(x: cos(a - 0.11) * 230, y: sin(a - 0.11) * 230), CGPoint(x: cos(a + 0.11) * 230, y: sin(a + 0.11) * 230)])
    }
    static func rotate(_ v: Vector, _ yaw: Double, _ pitch: Double) -> Vector {
        let x = cos(yaw) * v.x + sin(yaw) * v.z
        let z = -sin(yaw) * v.x + cos(yaw) * v.z
        return Vector(x: x, y: cos(pitch) * v.y - sin(pitch) * z, z: sin(pitch) * v.y + cos(pitch) * z)
    }
    struct Face {
        var points: [CGPoint]
        var depth, shade, alpha, scale, x, y: Double
        var kind: Int
    }
    func gem(_ c: GraphicsContext, _ cx: Double, _ cy: Double, _ radius: Double, _ yaw: Double, _ pitch: Double, _ forge: Double) {
        if forge <= 0.001 { return }
        var drawn: [Face] = []
        for facet in Self.facets {
            let v = facet.vertices.map { Self.rotate($0, yaw, pitch) }
            let e1 = Vector(x: v[1].x - v[0].x, y: v[1].y - v[0].y, z: v[1].z - v[0].z)
            let e2 = Vector(x: v[2].x - v[0].x, y: v[2].y - v[0].y, z: v[2].z - v[0].z)
            var nx = e1.y * e2.z - e1.z * e2.y
            var ny = e1.z * e2.x - e1.x * e2.z
            var nz = e1.x * e2.y - e1.y * e2.x
            let length = max(0.000001, sqrt(nx * nx + ny * ny + nz * nz))
            nx /= length; ny /= length; nz /= length
            let count = Double(v.count)
            let mx = v.reduce(0) { $0 + $1.x } / count
            let my = v.reduce(0) { $0 + $1.y } / count
            let mz = v.reduce(0) { $0 + $1.z } / count
            if nx * mx + ny * my + nz * mz < 0 { nx = -nx; ny = -ny; nz = -nz }
            if nz <= 0.02 { continue }
            let dif = max(0, -0.45 * nx - 0.6 * ny + 0.66 * nz)
            let band = exp(-pow((nx + yaw * 0.9 - 0.25) * 3.2, 2))
            let shade = M.clamp(0.18 + 0.55 * dif + 0.5 * band * (facet.kind == 2 ? 0.6 : 1))
            let amount = (forge - Double(facet.order) * 0.03) / 0.3
            drawn.append(Face(points: v.map { CGPoint(x: cx + $0.x * radius, y: cy + $0.y * radius) }, depth: mz, shade: shade, alpha: M.clamp(amount), scale: M.back(amount), x: cx + mx * radius, y: cy + my * radius, kind: facet.kind))
        }
        // Stable insertion sort matches the source's ordering for equal-depth faces.
        if drawn.count > 1 {
            for i in 1..<drawn.count {
                let cur = drawn[i]
                var j = i - 1
                while j >= 0 && drawn[j].depth > cur.depth { drawn[j + 1] = drawn[j]; j -= 1 }
                drawn[j + 1] = cur
            }
        }
        for d in drawn where d.alpha > 0.001 {
            let path = Self.polygon(d.points.map { CGPoint(x: d.x + ($0.x - d.x) * d.scale, y: d.y + ($0.y - d.y) * d.scale) })
            let k = d.kind == 0 ? 0.25 + 0.75 * d.shade : d.shade
            c.fill(path, with: .color(Self.rgb(M.mix(58, 214, k), M.mix(23, 196, k * k), M.mix(160, 255, k), d.alpha)))
            c.stroke(path, with: .color(Self.color(d.shade > 0.7 ? 0xf3eeff : 0x3a178f, 0.55 * d.alpha)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
        }
        resources.sprite(c, resources.glint, cx - radius * (0.28 - yaw * 0.4) - 10, cy - radius * 0.3 - 10, 0.75 * M.clamp((forge - 0.6) / 0.4))
    }
    func tiles(_ c: GraphicsContext, _ cx: Double, _ cy: Double, _ gather: Double) {
        for d in 1...31 {
            let index = d - 1 + 3
            let x0 = -75.0 + 9 + Double(index % 7) * 22
            let y0 = -53.0 + 9 + Double(index / 7) * 22
            let k = M.ease((gather - Double(d - 1) * 0.012) / 0.64)
            if k >= 0.999 { continue }
            let r = hypot(x0, y0) * (1 - k)
            let a = atan2(y0, x0) + k * .pi * 1.65
            let scale = M.mix(1, 0.25, k)
            var ctx = c
            ctx.translateBy(x: cx + cos(a) * r, y: cy + sin(a) * r)
            ctx.rotate(by: .degrees(k * 220))
            ctx.scaleBy(x: scale, y: scale)
            let base: [Double] = d == 17 ? [255, 180, 196] : [201, 184, 255]
            ctx.fill(Self.rounded(-9, -9, 18, 18, 5), with: .color(Self.rgb(M.mix(base[0], 124, k), M.mix(base[1], 77, k), M.mix(base[2], 255, k), 1 - M.smooth((k - 0.8) / 0.2))))
            if k < 0.15 {
                let glyph = resources.days[d - 1]
                resources.draw(ctx, glyph, -glyph.advance / 2, 3.2, Self.color(d == 17 ? 0x8a1a3a : 0x3b2380), 1 - k / 0.15)
            }
        }
    }
    func draw(_ c: GraphicsContext, _ size: CGSize, _ cy: Double, _ f: M.Frame) {
        let p = f.artP
        let cx = size.width / 2
        let gather = M.clamp(p / 0.6)
        let forge = M.clamp((p - 0.3) / 0.62)
        let rayA = M.smooth((p - 0.7) / 0.3)
        if rayA > 0.01 {
            var ctx = c
            ctx.translateBy(x: cx, y: cy)
            ctx.rotate(by: .radians(f.turn / 4))
            ctx.opacity *= rayA * M.mix(0.6, 1, M.back((p - 0.7) / 0.3))
            let gradient = Gradient(colors: [Self.color(0xf88ce2, 0.45), Self.color(0xf88ce2, 0)])
            for ray in Self.rays { ctx.fill(ray, with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: 230)) }
        }
        let k = M.smooth((p - 0.3) / 0.55)
        let w = M.mix(186, 128, k), h = M.mix(150, 128, k), radius = M.mix(22, 64, k)
        resources.shadow(c, cx, cy, k)
        let gradient = Gradient(colors: [Self.rgb(255, M.mix(255, 143, k), M.mix(255, 230, k)), Self.rgb(M.mix(255, 233, k), M.mix(255, 76, k), M.mix(255, 214, k))])
        c.fill(Self.rounded(cx - w / 2, cy - h / 2, w, h, radius), with: .linearGradient(gradient, startPoint: CGPoint(x: cx, y: cy - h / 2), endPoint: CGPoint(x: cx, y: cy + h / 2)))
        if k < 0.6 {
            for i in 0..<7 { resources.draw(c, resources.weekdays[i], cx - 69 + Double(i) * 22, cy - 60, Self.color(0x9a93a8), 1 - k / 0.6) }
        }
        tiles(c, cx, cy + 6 * (1 - k), gather)
        let flash = exp(-pow((p - 0.62) / 0.07, 2))
        if flash > 0.02 && !f.reduced {
            resources.sprite(c, resources.flash, cx - 108, cy - 108, 0.85 * flash)
        }
        let yaw = 0.42 * sin(f.turn) * M.smooth((p - 0.9) / 0.1)
        gem(c, cx, cy, 46, yaw, -0.18, f.reduced ? (p > 0.5 ? 1 : 0) : forge)
        let tk = M.back((p - 0.82) / 0.18)
        if tk > 0.01 {
            var tag = c
            tag.translateBy(x: cx + 50, y: cy - 46)
            tag.scaleBy(x: tk, y: tk)
            tag.fill(Self.rounded(-17, -11, 34, 22, 11), with: .color(Self.color(0xffc93c)))
            resources.draw(tag, resources.tag, -10, 4.5, Self.color(0x3a2a00))
        }
    }
}
