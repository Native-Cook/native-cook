import SwiftUI
import UIKit

/*
 Slinky Stairs — the drawing. Static, drawn once into a rasterised view: a slate wall lit by a warm
 desk lamp from the upper left, a three-step maple staircase standing on a walnut desk (top faces,
 front faces, grain, lit edges, a vignette and grain) and the bottom panel's plate. Per frame (one
 Canvas): soft contact shadows, then the 40 rings as five batched strokes — the far half of every
 ring in dark steel, the near halves in steel, a cool highlight where the lamp catches them, a thin
 warm rim and a dark edge.
 */
enum SlinkyStairsArt {
    typealias Geo = SlinkyStairsMotion.Geo
    typealias Pose = SlinkyStairsMotion.Pose

    static func layout(w: Double, h: Double, top: Double, bottom: Double) -> Geo {
        let left = 12.0
        let tread = (w - left * 2) / 4
        let panelH = 120.0
        let panelY = h - max(bottom, 12) - panelH
        let R = min(36, tread * 0.4)
        let pitch = 1.7
        let H = Double(SlinkyStairsMotion.n - 1) * pitch
        var y0 = top + 120 + H + 56
        var y3 = panelY - 96
        let riser = max(60, min(92, (y3 - y0) / 3))
        // Centre the stairs in the room left between the header and the desk's front edge.
        y0 = max(y0, (y0 + y3) / 2 - riser * 1.5)
        y3 = y0 + riser * 3
        let stepY = [y0, y0 + riser, y0 + riser * 2, y3]
        let deskEdge = min(panelY - 24, y3 + 64)
        let baseX = (0..<4).map { left + tread * (Double($0) + 0.5) }
        let baseY = stepY.map { $0 - 8 }
        return Geo(w: w, h: h, baseX: baseX, baseY: baseY, R: R, pitch: pitch, tilt: 0.3, tread: tread, riser: riser,
                   stepY: stepY, left: left, panelY: panelY, panelH: panelH, deskEdge: deskEdge)
    }

    // MARK: Helpers

    /// "#RRGGBB" or "rgba(r,g,b,a)".
    static func col(_ s: String) -> Color {
        if s.hasPrefix("#") { return Color(hex: UInt32(s.dropFirst(), radix: 16) ?? 0) }
        let parts = s.drop { $0 != "(" }.dropFirst().prefix { $0 != ")" }.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == 4 else { return .clear }
        return Color.rgba(parts[0], parts[1], parts[2], parts[3])
    }

    static func gradient(_ list: [String], _ pos: [Double]? = nil) -> Gradient {
        let colors = list.map(col)
        guard let pos, pos.count == colors.count else { return Gradient(colors: colors) }
        return Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(gradient(list, pos), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func radial(_ x: Double, _ y: Double, _ r: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .radialGradient(gradient(list, pos), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: max(0.1, r))
    }

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(CGRect(x: x, y: y, width: w, height: h))
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func stroke(_ c: GraphicsContext, _ path: Path, _ color: String, _ width: Double, _ alpha: Double = 1) {
        c.stroke(path, with: .color(col(color).opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static let grainTile: Image = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        var seed: UInt32 = 0x6C8E9CF5
        for i in 0..<(side * side) {
            seed = seed &* 1664525 &+ 1013904223
            let v = UInt8(truncatingIfNeeded: seed >> 24)
            bytes[i * 4] = v
            bytes[i * 4 + 1] = v
            bytes[i * 4 + 2] = v
        }
        let space = CGColorSpaceCreateDeviceRGB()
        let image = bytes.withUnsafeMutableBytes { raw -> CGImage? in
            CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                      space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)?.makeImage()
        }
        return image.map { Image(decorative: $0, scale: 3) } ?? Image(systemName: "circle")
    }()

    static func grain(_ c: GraphicsContext, _ r: CGRect, _ alpha: Double) {
        var g = c
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(r), with: .tiledImage(grainTile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    /// Wood grain: long, slightly wavy strokes inside a rect.
    static func woodGrain(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ seed: Double, vertical: Bool) {
        var cc = c
        cc.clip(to: rect(x, y, w, h))
        let count = Int(((vertical ? w : h) / 5).rounded())
        for i in 0..<count {
            let di = Double(i)
            var b = Path()
            let off = (di + hash(seed + di) * 0.8) * 5
            let amp = 1 + 2 * hash(seed + di * 3)
            let len = vertical ? h : w
            for s in 0...12 {
                let a = Double(s) / 12 * len
                let wv = sin(Double(s) / 12 * .pi * (1 + hash(seed + di * 5) * 2) + di) * amp
                let pt = vertical ? CGPoint(x: x + off + wv, y: y + a) : CGPoint(x: x + a, y: y + off + wv)
                if s == 0 { b.move(to: pt) } else { b.addLine(to: pt) }
            }
            stroke(cc, b, i % 3 == 0 ? "#5A3618" : "#7A4F28", 0.7, 0.18 + 0.12 * hash(seed + di * 7))
        }
    }

    // MARK: Static scene

    static func scene(_ c: GraphicsContext, _ g: Geo, top: Double) {
        let w = g.w, h = g.h
        let all = rect(0, 0, w, h)
        c.fill(all, with: linear(0, 0, 0, h, ["#344241", "#2A3635", "#212B2A", "#1A2221"], [0, 0.4, 0.75, 1]))
        c.fill(all, with: radial(w * 0.08, top + 40, w * 1.25, ["rgba(255,214,150,0.42)", "rgba(240,180,110,0.16)", "rgba(200,150,90,0)"], [0, 0.45, 1]))
        grain(c, CGRect(x: 0, y: 0, width: w, height: h), 0.06)
        let y3 = g.stepY[3]
        // The desk: a walnut top that runs towards us from under the stairs, then its front edge.
        let e = g.deskEdge
        let deskRect = rect(0, y3 - 18, w, e - y3 + 18)
        c.fill(deskRect, with: linear(0, y3 - 18, 0, e, ["#5E412C", "#6E4D34", "#7A563A"], [0, 0.5, 1]))
        woodGrain(c, 0, y3 - 18, w, e - y3 + 18, 40, vertical: false)
        c.fill(deskRect, with: radial(w * 0.3, y3 + 10, w * 0.7, ["rgba(255,210,150,0.16)", "rgba(255,210,150,0)"]))
        c.fill(rect(0, e, w, h - e), with: linear(0, e, 0, h, ["#3A271A", "#2B1C12", "#1E140D"]))
        stroke(c, line(0, e + 0.5, w, e + 0.5), "#D9A47A", 1.2, 0.5)
        stroke(c, line(0, y3 - 18, w, y3 - 18), "#2A1A0E", 1, 0.4)
        // Shadow the stairs cast to the right on the desk.
        c.fill(rect(g.left + g.tread * 3 - 6, y3 - 16, 40, 14),
               with: linear(g.left + g.tread * 3, 0, g.left + g.tread * 3 + 40, 0, ["rgba(10,6,3,0.45)", "rgba(10,6,3,0)"]))
        // Three steps, each a maple block from its tread down to the desk.
        for k in 0..<3 {
            let x = g.left + g.tread * Double(k)
            let yk = g.stepY[k]
            let tone = ["#C8915A", "#BD8650", "#B27C47"][k]
            c.fill(rect(x, yk, g.tread, y3 - yk), with: linear(x, yk, x, y3, [tone, "#9A6838", "#7E522A"], [0, 0.6, 1]))
            woodGrain(c, x, yk, g.tread, y3 - yk, 10 + Double(k) * 7, vertical: true)
            // Top face (seen from a little above), lit by the lamp.
            c.fill(rect(x, yk - 16, g.tread, 16), with: linear(x, yk - 16, x + g.tread, yk, ["#F0C48C", "#DDAA70", "#C99459"]))
            woodGrain(c, x, yk - 16, g.tread, 16, 30 + Double(k), vertical: false)
            stroke(c, line(x, yk + 0.5, x + g.tread, yk + 0.5), "#FFE4BC", 1.2, 0.7)
            stroke(c, line(x, yk - 16, x + g.tread, yk - 16), "#4A2E16", 1, 0.35)
            // The crease where this block meets the next, lower one.
            c.fill(rect(x + g.tread - 10, yk, 10, y3 - yk), with: linear(x + g.tread - 10, 0, x + g.tread, 0, ["rgba(40,20,8,0)", "rgba(40,20,8,0.45)"]))
            stroke(c, line(x + 0.5, yk - 16, x + 0.5, y3), "#FFE2B8", 1, k == 0 ? 0.5 : 0.18)
        }
        // Ambient occlusion where the steps meet the desk.
        c.fill(rect(g.left, y3 - 24, g.tread * 3, 24), with: linear(0, y3 - 24, 0, y3, ["rgba(20,10,4,0)", "rgba(20,10,4,0.35)"]))
        // The panel's plate.
        let px = 16.0
        let pw = w - 32
        var sh = c
        sh.addFilter(.blur(radius: 14))
        sh.fill(Path(roundedRect: CGRect(x: px, y: g.panelY + 10, width: pw, height: g.panelH), cornerRadius: 22), with: .color(col("#0A0705").opacity(0.5)))
        c.fill(Path(roundedRect: CGRect(x: px, y: g.panelY, width: pw, height: g.panelH), cornerRadius: 22),
               with: linear(0, g.panelY, 0, g.panelY + g.panelH, ["#2E2A27", "#24201D", "#1D1A17"]))
        stroke(c, Path(roundedRect: CGRect(x: px + 0.5, y: g.panelY + 0.5, width: pw - 1, height: g.panelH - 1), cornerRadius: 21.5), "#FFEBD2", 1, 0.1)
        c.fill(all, with: radial(w / 2, h * 0.45, hypot(w, h) * 0.62, ["rgba(8,10,10,0)", "rgba(8,10,10,0.25)", "rgba(6,8,8,0.6)"], [0, 0.6, 1]))
    }

    // MARK: Per frame

    /// The arc of every ring between angles a0 and a1, in one path.
    static func arcs(_ g: Geo, _ rings: [SlinkyStairsMotion.Ring], _ a0: Double, _ a1: Double, _ steps: Int) -> Path {
        var b = Path()
        let R = g.R
        let m = g.R * g.tilt
        let cs = (0...steps).map { s -> (Double, Double) in
            let a = a0 + (a1 - a0) * Double(s) / Double(steps)
            return (cos(a), sin(a))
        }
        for r in rings {
            for (s, (ca, sa)) in cs.enumerated() {
                let pt = CGPoint(x: r.x + r.nx * R * ca, y: r.y + r.ny * R * ca - m * sa)
                if s == 0 { b.move(to: pt) } else { b.addLine(to: pt) }
            }
        }
        return b
    }

    /// Contact shadows, the rings, and the touch marker.
    static func frame(_ c: GraphicsContext, _ g: Geo, _ p: Pose, touch: (Double, Double, Double)) {
        for s in p.shadows where s.alpha >= 0.01 {
            var sc = c
            sc.addFilter(.blur(radius: 4))
            sc.fill(Path(ellipseIn: CGRect(x: s.x - s.r * 1.08 + 3, y: s.y - s.r * g.tilt * 0.7 + 2, width: s.r * 2.16, height: s.r * g.tilt * 1.6)),
                    with: .color(col("#120905").opacity(0.5 * s.alpha)))
        }
        let rings = p.rings
        let pi = Double.pi
        stroke(c, arcs(g, rings, 0, pi, 10), "#68727A", 2.6)
        stroke(c, arcs(g, rings, pi, 2 * pi, 10), "#A6B0B9", 3)
        stroke(c, arcs(g, rings, pi * 1.18, pi * 1.62, 5), "#EEF3F7", 1.4, 0.9)
        stroke(c, arcs(g, rings, pi * 1.04, pi * 1.2, 3), "#F3C88E", 1.1, 0.65)
        stroke(c, arcs(g, rings, pi * 1.8, pi * 1.95, 3), "#2E343A", 1.2, 0.5)
        if touch.2 > 0.01 {
            var gc = c
            gc.addFilter(.blur(radius: 8))
            gc.fill(Path(ellipseIn: CGRect(x: touch.0 - 26, y: touch.1 - 26, width: 52, height: 52)), with: .color(col("#FFFFFF").opacity(0.14 * touch.2)))
            stroke(c, Path(ellipseIn: CGRect(x: touch.0 - 22, y: touch.1 - 22, width: 44, height: 44)), "#FFFFFF", 1.2, 0.3 * touch.2)
        }
    }
}

/// The room, drawn once and rasterised.
struct SlinkyStairsScene: View, Equatable {
    let g: SlinkyStairsMotion.Geo
    let top: Double

    var body: some View {
        Canvas { c, _ in SlinkyStairsArt.scene(c, g, top: top) }
            .frame(width: g.w, height: g.h)
            .drawingGroup()
    }
}
