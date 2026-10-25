import SwiftUI
import UIKit

/*
 Elevator Floor Nav — the drawing. Static Canvases drawn once: the lobby (green marble, the floor
 dial's face, the button panel), one poster per floor (the store's section, drawn in code), the
 two brushed-steel doors (with their glass vision panels cut out), and the brass surround. Every
 frame the posters slide behind the doorway (blurred with the car's speed by a Metal layer
 effect), the doors slide, a highlight sweeps their brushed steel, then the needle, the lanterns
 and the lit buttons are drawn by the timeline's Canvases.
*/
enum ElevatorFloorNavArt {
    typealias M = ElevatorFloorNavMotion

    struct Floor {
        var name: String
        var tagline: String
        var colors: [Color]
        var items: [(name: String, price: String)]
    }

    static let store: [Floor] = [
        Floor(name: "Shoes", tagline: "Loafers, boots & the cobbler’s bench", colors: [Color(hex: 0x6B3127), Color(hex: 0x8C4434), Color(hex: 0x4A211B)],
              items: [("Oxford loafer", "£185"), ("Chelsea boot", "£240"), ("Suede mule", "£150")]),
        Floor(name: "Fashion", tagline: "Autumn tailoring & knitwear", colors: [Color(hex: 0x24324F), Color(hex: 0x344870), Color(hex: 0x18223A)],
              items: [("Wool overcoat", "£420"), ("Merino crew", "£95"), ("Pleated trouser", "£140")]),
        Floor(name: "Beauty", tagline: "The fragrance hall & apothecary", colors: [Color(hex: 0x7D4B56), Color(hex: 0x9E6673), Color(hex: 0x523039)],
              items: [("Fig eau de parfum", "£88"), ("Rose balm", "£24"), ("Cedar candle", "£42")]),
        Floor(name: "Home", tagline: "Linen, stoneware & lamps", colors: [Color(hex: 0x46523D), Color(hex: 0x5F7052), Color(hex: 0x2E3628)],
              items: [("Stoneware jug", "£48"), ("Linen throw", "£120"), ("Brass lamp", "£210")]),
        Floor(name: "Café", tagline: "The rooftop café · open till nine", colors: [Color(hex: 0x73492A), Color(hex: 0x946238), Color(hex: 0x4B2F1A)],
              items: [("Flat white", "£4"), ("Pear tart", "£7"), ("Afternoon tea", "£32")]),
    ]

    static let brass = [Color(hex: 0x6A4B1F), Color(hex: 0xC59E57), Color(hex: 0xF1DCA4), Color(hex: 0xB48A42), Color(hex: 0x5A3F18)]
    static let brassPos = [0, 0.3, 0.46, 0.7, 1]

    struct Scene: Equatable {
        var w: Double
        var h: Double
        var dialX: Double
        var dialY: Double
        var dialR: Double
        var doorX: Double
        var doorY: Double
        var doorW: Double
        var doorH: Double
        var panelY: Double
        var panelH: Double
        var buttonX: [Double]
        var buttonY: Double
        var buttonR: Double

        init(w: Double, h: Double, top: Double, bottom: Double) {
            self.w = w
            self.h = h
            dialR = min(90, (h - top - bottom) * 0.11)
            dialY = top + 86 + dialR
            dialX = w / 2
            doorX = 20
            doorY = dialY + 26
            doorW = w - 40
            panelH = 112
            panelY = h - bottom - panelH - 10
            doorH = panelY - 18 - doorY
            buttonR = 25
            let span = doorW - 44
            var bx: [Double] = []
            for i in 0..<M.floors { bx.append(doorX + 22 + buttonR + (span - buttonR * 2) * Double(i) / Double(M.floors - 1)) }
            buttonX = bx
            buttonY = panelY + 44
        }
    }

    /// The needle's angle (degrees from straight up) for a floor.
    static func needleAngle(_ floor: Double) -> Double { -66 + (floor - 1) * 132 / Double(M.floors - 1) }

    // MARK: Helpers

    static func lin(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        return .linearGradient(Gradient(stops: zip(colors, pos).map { .init(color: $0, location: $1) }), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func rad(_ x: Double, _ y: Double, _ r: Double, _ colors: [Color], _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let pos = stops ?? colors.indices.map { colors.count > 1 ? Double($0) / Double(colors.count - 1) : 0 }
        return .radialGradient(Gradient(stops: zip(colors, pos).map { .init(color: $0, location: $1) }), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: max(0, w), height: max(0, h)), cornerRadius: min(r, max(0, w) / 2, max(0, h) / 2), style: .circular)
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    static func blurred(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ alpha: Double, _ sigma: Double) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            l.fill(path, with: .color(color.opacity(alpha)))
        }
    }

    static func grain(_ ctx: GraphicsContext, _ rect: CGRect, _ tile: Image, _ alpha: Double) {
        var g = ctx
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(rect), with: .tiledImage(tile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func text(_ ctx: GraphicsContext, _ r: GraphicsContext.ResolvedText, x: Double, baseline: Double, anchor: Double = 0) {
        let s = r.measure(in: CGSize(width: 2000, height: 2000))
        let b = r.firstBaseline(in: s)
        ctx.draw(r, in: CGRect(x: x - s.width * anchor, y: baseline - b, width: s.width, height: s.height))
    }

    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    // MARK: Grain tiles (periodic value noise, built once)

    static let marbleGrain = noiseTile(width: 498, height: 500, cellX: 83, cellY: 50, seed: 51, pxPerPt: 1)
    static let plateGrain = noiseTile(width: 128, height: 200, cellX: 1.1, cellY: 100, seed: 61, pxPerPt: 2)
    static let posterGrain = noiseTile(width: 128, height: 128, cellX: 0.91, cellY: 0.91, seed: 70, pxPerPt: 2)
    static let brushGrain = noiseTile(width: 500, height: 128, cellX: 250, cellY: 1.1, seed: 81, pxPerPt: 2)

    /// A tile of two-octave periodic value noise, grey around 0.5.
    static func noiseTile(width: Int, height: Int, cellX: Double, cellY: Double, seed: UInt32, pxPerPt: Int) -> Image {
        let scale = pxPerPt
        let wPx = width * scale, hPx = height * scale
        let nx = max(1, Int((Double(width) / cellX).rounded()))
        let ny = max(1, Int((Double(height) / cellY).rounded()))
        func hash(_ x: Int, _ y: Int, _ s: UInt32) -> Double {
            var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263) &+ s &* 2_246_822_519
            h = (h ^ (h >> 13)) &* 1_274_126_177
            h ^= h >> 16
            return Double(h & 0xFFFF) / 65535
        }
        func noise(_ u: Double, _ v: Double, _ px: Int, _ py: Int, _ s: UInt32) -> Double {
            let x = u * Double(px), y = v * Double(py)
            let ix = Int(floor(x)), iy = Int(floor(y))
            let fx = x - Double(ix), fy = y - Double(iy)
            let sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy)
            let x0 = ((ix % px) + px) % px, x1 = (((ix + 1) % px) + px) % px
            let y0 = ((iy % py) + py) % py, y1 = (((iy + 1) % py) + py) % py
            let a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s)
            return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
        }
        var bytes = [UInt8](repeating: 128, count: wPx * hPx)
        for j in 0..<hPx {
            let v = (Double(j) + 0.5) / Double(hPx)
            for i in 0..<wPx {
                let u = (Double(i) + 0.5) / Double(wPx)
                let n = noise(u, v, nx, ny, seed) * 0.67 + noise(u, v, nx * 2, ny * 2, seed &+ 17) * 0.33
                bytes[j * wPx + i] = UInt8(max(0, min(255, (0.5 + (n - 0.5) * 1.6) * 255)))
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(width: wPx, height: hPx, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: wPx,
                         space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                         provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return Image(decorative: cg, scale: CGFloat(scale))
    }

    /// The upper half of a circle, closed along its diameter.
    static func halfDisc(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        var p = Path()
        p.addArc(center: CGPoint(x: cx, y: cy), radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        p.closeSubpath()
        return p
    }

    // MARK: Static layers

    /// Marble lobby, the floor dial's face and numerals, and the button panel with unlit buttons.
    static func lobby(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w, h = s.h
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        c.fill(Path(full), with: lin(0, 0, w * 0.3, h, [Color(hex: 0x17312B), Color(hex: 0x12271F), Color(hex: 0x0E1F19), Color(hex: 0x0A1813)], [0, 0.4, 0.75, 1]))
        grain(c, full, marbleGrain, 0.2)
        // Marble veins: a few long seeded curves, pale and soft.
        c.drawLayer { l in
            l.addFilter(.blur(radius: 0.8))
            for i in 0..<7 {
                let fi = Double(i)
                var path = Path()
                var x = hash(fi) * w
                var y = -20.0
                path.move(to: CGPoint(x: x, y: y))
                while y < h + 20 {
                    let nx = x + (hash(fi * 13 + y) - 0.5) * 90
                    let ny = y + 60 + hash(fi * 7 + y) * 60
                    path.addQuadCurve(to: CGPoint(x: nx, y: ny), control: CGPoint(x: x + (hash(fi + y * 3) - 0.5) * 60, y: (y + ny) / 2))
                    x = nx
                    y = ny
                }
                l.stroke(path, with: .color(Color(hex: 0xCFE3D8).opacity(0.05 + 0.05 * hash(fi * 5))), lineWidth: 0.8 + hash(fi * 9) * 1.4)
            }
        }
        c.fill(Path(full), with: rad(w / 2, h * 0.45, h * 0.75, [.rgba(0, 0, 0, 0), .rgba(0, 0, 0, 0), .rgba(1, 6, 4, 0.25), .rgba(1, 6, 4, 0.6)], [0, 0.4, 0.75, 1]))

        // Sunburst rays behind the dial.
        var rays = Path()
        for i in 0...16 {
            let a = (-80 + 160 * Double(i) / 16) * .pi / 180
            rays.move(to: CGPoint(x: s.dialX + sin(a) * (s.dialR + 6), y: s.dialY - cos(a) * (s.dialR + 6)))
            rays.addLine(to: CGPoint(x: s.dialX + sin(a) * (s.dialR + 26), y: s.dialY - cos(a) * (s.dialR + 26)))
        }
        c.stroke(rays, with: .color(Color(hex: 0xC59E57).opacity(0.22)), lineWidth: 1)
        // Dial: a brass half-moon bezel round a cream enamel face, numerals on the arc.
        let R = s.dialR
        let half = halfDisc(s.dialX, s.dialY, R + 8)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 10))
            l.translateBy(x: 0, y: 8)
            l.fill(half, with: .color(.black.opacity(0.5)))
        }
        c.fill(half, with: lin(s.dialX - R, 0, s.dialX + R, 0, brass, brassPos))
        c.fill(halfDisc(s.dialX, s.dialY, R), with: rad(s.dialX, s.dialY - R * 0.6, R * 1.3, [Color(hex: 0xF6EDD8), Color(hex: 0xEADCBE), Color(hex: 0xD5C29E)]))
        for i in 0...16 {
            let a = (-66 + 132 * Double(i) / 16) * .pi / 180
            let long = i % 4 == 0
            let r0 = R - 6
            let r1 = R - (long ? 16 : 11)
            c.stroke(line(s.dialX + sin(a) * r0, s.dialY - cos(a) * r0, s.dialX + sin(a) * r1, s.dialY - cos(a) * r1),
                     with: .color(Color(hex: 0x5A4526).opacity(0.7)), style: StrokeStyle(lineWidth: long ? 1.6 : 0.9, lineCap: .round))
        }
        for f in 1...M.floors {
            let a = needleAngle(Double(f)) * .pi / 180
            let r = R - 31
            let label = c.resolve(Text("\(f)").font(.custom("Georgia-Bold", size: 16)).foregroundStyle(Color(hex: 0x3A2C18)))
            text(c, label, x: s.dialX + sin(a) * r, baseline: s.dialY - cos(a) * r + 6, anchor: 0.5)
        }
        c.stroke(line(s.dialX - R - 8, s.dialY, s.dialX + R + 8, s.dialY), with: .color(Color(hex: 0x3A2C18).opacity(0.5)), lineWidth: 1)

        // Button panel: a brass plate with a bevel, five recessed rings, the sections' names.
        let py = s.panelY, ph = s.panelH
        let plate = rr(s.doorX, py, s.doorW, ph, 18)
        blurred(c, rr(s.doorX, py + 10, s.doorW, ph, 18), .black, 0.5, 14)
        blurred(c, rr(s.doorX, py + 1.5, s.doorW, ph, 18), .black, 0.6, 1.5)
        c.fill(plate, with: lin(s.doorX, py, s.doorX + s.doorW * 0.6, py + ph, [Color(hex: 0xB08944), Color(hex: 0x8C6A31), Color(hex: 0x6E5124), Color(hex: 0x5A4220)], [0, 0.35, 0.7, 1]))
        var pc = c
        pc.clip(to: plate)
        grain(pc, CGRect(x: s.doorX, y: py, width: s.doorW, height: ph), plateGrain, 0.3)
        c.stroke(rr(s.doorX + 0.5, py + 0.5, s.doorW - 1, ph - 1, 17.5), with: .color(Color(hex: 0xFFE7B0).opacity(0.45)), lineWidth: 1)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 2))
            for i in 0..<M.floors {
                l.fill(circle(s.buttonX[i], s.buttonY + 1, s.buttonR + 4), with: .color(Color(hex: 0x2A1C08).opacity(0.6)))
            }
        }
        for i in 0..<M.floors {
            let x = s.buttonX[i], y = s.buttonY, r = s.buttonR
            c.fill(circle(x, y, r + 3), with: lin(x - r, y - r, x + r, y + r, [Color(hex: 0xF5E0A8), Color(hex: 0xB28B45), Color(hex: 0x6B4E1F)]))
            c.fill(circle(x, y, r - 1), with: .color(Color(hex: 0x2A2116)))
            let label = c.resolve(Text(store[i].name.uppercased()).font(.system(size: 10, weight: .bold)).foregroundStyle(Color(hex: 0x2B1E0C).opacity(0.82)))
            text(c, label, x: x, baseline: y + r + 22, anchor: 0.5)
        }
    }

    /// One floor of the store, as the doorway shows it (in its own W × H frame).
    static func poster(_ c: GraphicsContext, _ s: Scene, _ f: Int) {
        let fl = store[f]
        let W = s.doorW, H = s.doorH
        let full = CGRect(x: 0, y: 0, width: W, height: H)
        c.fill(Path(full), with: lin(0, 0, W * 0.4, H, fl.colors))
        c.fill(Path(full), with: rad(W * 0.3, H * 0.15, H * 0.8, [.rgba(255, 240, 215, 0.18), .rgba(255, 240, 215, 0)]))
        grain(c, full, posterGrain, 0.12)
        let ink = Color(hex: 0xF7EEDD)
        let pad = 24.0
        text(c, c.resolve(Text("FLOOR \(f + 1)").font(.system(size: 10, weight: .bold)).foregroundStyle(ink.opacity(0.7))), x: pad, baseline: 40)
        text(c, c.resolve(Text(fl.name).font(.custom("Georgia", size: 50)).foregroundStyle(ink)), x: pad - 2, baseline: 92)
        text(c, c.resolve(Text(fl.tagline).font(.system(size: 14)).foregroundStyle(ink.opacity(0.75))), x: pad, baseline: 118)
        // Three arched display windows with a still life each.
        let gap = 10.0
        let tw = (W - pad * 2 - gap * 2) / 3
        let ty = 146.0
        let th = max(110, H - ty - 64)
        for i in 0..<3 {
            let x = pad + Double(i) * (tw + gap)
            var arch = Path()
            arch.move(to: CGPoint(x: x, y: ty + th))
            arch.addLine(to: CGPoint(x: x, y: ty + tw / 2))
            arch.addArc(center: CGPoint(x: x + tw / 2, y: ty + tw / 2), radius: tw / 2, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            arch.addLine(to: CGPoint(x: x + tw, y: ty + th))
            arch.closeSubpath()
            c.fill(arch, with: lin(0, ty, 0, ty + th, [.rgba(255, 244, 226, 0.16), .rgba(255, 244, 226, 0.08), .rgba(0, 0, 0, 0.12)]))
            c.stroke(arch, with: .color(Color(hex: 0xF7E3B8).opacity(0.35)), lineWidth: 1)
            var ac = c
            ac.clip(to: arch)
            let cx = x + tw / 2
            let floorY = ty + th - 18
            // A plinth and an object: shapes vary by floor and window, lit from the upper left.
            ac.fill(Path(CGRect(x: cx - tw * 0.32, y: floorY - 10, width: tw * 0.64, height: 28)), with: lin(0, floorY - 10, 0, floorY + 18, [Color(hex: 0xF2E6D0), Color(hex: 0xCDBB9A), Color(hex: 0xA8926E)]))
            blurred(ac, Path(ellipseIn: CGRect(x: cx - tw * 0.3, y: floorY - 16, width: tw * 0.6, height: 10)), .black, 0.25, 4)
            let kind = (f + i) % 4
            let o = rad(cx - tw * 0.12, floorY - tw * 0.45, tw * 0.95, [Color(hex: 0xFFF7EA), Color(hex: 0xE6D2B2), Color(hex: 0xA88E6A), fl.colors[2]], [0, 0.35, 0.75, 1])
            if kind == 0 {
                ac.fill(circle(cx, floorY - tw * 0.27, tw * 0.25), with: o)
            } else if kind == 1 {
                var v = Path()
                let vh = th * 0.5
                v.move(to: CGPoint(x: cx - tw * 0.08, y: floorY - 10))
                v.addCurve(to: CGPoint(x: cx - tw * 0.07, y: floorY - vh), control1: CGPoint(x: cx - tw * 0.36, y: floorY - vh * 0.45), control2: CGPoint(x: cx - tw * 0.1, y: floorY - vh * 0.75))
                v.addLine(to: CGPoint(x: cx + tw * 0.07, y: floorY - vh))
                v.addCurve(to: CGPoint(x: cx + tw * 0.08, y: floorY - 10), control1: CGPoint(x: cx + tw * 0.1, y: floorY - vh * 0.75), control2: CGPoint(x: cx + tw * 0.36, y: floorY - vh * 0.45))
                v.closeSubpath()
                ac.fill(v, with: o)
            } else if kind == 2 {
                for k in 0..<3 {
                    let r = tw * (0.3 - Double(k) * 0.07)
                    ac.fill(rr(cx - r, floorY - 10 - Double(k + 1) * th * 0.13, r * 2, th * 0.13, 4), with: o)
                }
            } else {
                ac.fill(rr(cx - tw * 0.13, floorY - th * 0.55, tw * 0.26, th * 0.55 - 10, tw * 0.13), with: o)
                ac.fill(circle(cx, floorY - th * 0.55, tw * 0.16), with: o)
            }
            let item = fl.items[i]
            text(c, c.resolve(Text(item.name).font(.system(size: 12, weight: .semibold)).foregroundStyle(ink.opacity(0.92))), x: x, baseline: ty + th + 22)
            text(c, c.resolve(Text(item.price).font(.system(size: 12)).foregroundStyle(ink.opacity(0.6))), x: x, baseline: ty + th + 40)
        }
    }

    /// The shaft between two floors: a dark gap with a brass floor sill (30 pt tall).
    static func gap(_ c: GraphicsContext, _ s: Scene) {
        c.fill(Path(CGRect(x: 0, y: 0, width: s.doorW, height: 30)), with: .color(Color(hex: 0x0B0D0C)))
        c.fill(Path(CGRect(x: 0, y: 26, width: s.doorW, height: 4)), with: lin(0, 26, 0, 30, brass, brassPos))
    }

    /// The doors' vision panels, relative to each door.
    static func vision(_ s: Scene) -> (lx: Double, rx: Double, y: Double, h: Double) {
        (s.doorW / 2 - 30, 14, s.doorH * 0.12, s.doorH * 0.34)
    }

    /// One brushed-steel door (left or right), its vision panel cut out, in its own W/2 × H frame.
    static func door(_ c: GraphicsContext, _ s: Scene, left: Bool) {
        let W = s.doorW / 2, H = s.doorH
        let full = CGRect(x: 0, y: 0, width: W, height: H)
        c.fill(Path(full), with: lin(0, 0, W, 0, [Color(hex: 0x7E858B), Color(hex: 0xB6BCC1), Color(hex: 0x9DA4AA), Color(hex: 0x868D93)], [0, 0.35, 0.7, 1]))
        // Brushing: long horizontal streaks.
        var gc = c
        if !left { gc.translateBy(x: -137, y: -53) }
        grain(gc, full.offsetBy(dx: left ? 0 : 137, dy: left ? 0 : 53), brushGrain, 0.5)
        // Deco engraving: nested chevrons in the middle, a brass trim on the meeting edge.
        let edgeX = left ? W : 0
        let dir = left ? -1.0 : 1.0
        for k in 0..<4 {
            var p = Path()
            let y = H * 0.56 + Double(k) * 12
            p.move(to: CGPoint(x: edgeX + dir * 22, y: y))
            p.addLine(to: CGPoint(x: edgeX + dir * (W * 0.55), y: y - 26))
            p.addLine(to: CGPoint(x: edgeX + dir * (W - 18), y: y - 26))
            c.stroke(p, with: .color(Color(hex: 0x4E555B).opacity(0.55)), lineWidth: 1)
            c.stroke(p.offsetBy(dx: 0, dy: 1), with: .color(.white.opacity(0.25)), lineWidth: 1)
        }
        c.fill(Path(CGRect(x: left ? W - 5 : 0, y: 0, width: 5, height: H)), with: lin(left ? W - 5 : 0, 0, left ? W : 5, 0, brass, brassPos))
        c.fill(Path(CGRect(x: left ? 0 : W - 1, y: 0, width: 1, height: H)), with: .color(Color(hex: 0x2C3135).opacity(0.8)))
        // Vision panel: a tall slot near the meeting edge.
        let v = vision(s)
        let vx = left ? v.lx : v.rx
        c.fill(rr(vx - 3, v.y - 3, 22, v.h + 6, 4), with: lin(vx - 3, 0, vx + 19, 0, brass, brassPos))
        var hole = c
        hole.blendMode = .clear
        hole.fill(rr(vx, v.y, 16, v.h, 2), with: .color(.black))
    }

    /// The brass surround of the doorway, transparent inside.
    static func frame(_ c: GraphicsContext, _ s: Scene) {
        let x = s.doorX, y = s.doorY, w = s.doorW, h = s.doorH
        let t = 10.0
        let outer = rr(x - t, y - t, w + t * 2, h + t * 2, 8)
        let inner = rr(x, y, w, h, 2)
        var ring = outer
        ring.addPath(inner)
        // Its shadow falls over the doorway too, which dims the store a little (as in React Native).
        blurred(c, rr(x - t, y - t + 12, w + t * 2, h + t * 2, 8), .black, 0.5, 14)
        c.fill(ring, with: lin(x, y, x + w, y + h, brass, brassPos), style: FillStyle(eoFill: true))
        c.stroke(rr(x - t + 0.5, y - t + 0.5, w + t * 2 - 1, h + t * 2 - 1, 7.5), with: .color(Color(hex: 0xFFF0C8).opacity(0.4)), lineWidth: 1)
        c.stroke(inner, with: .color(Color(hex: 0x2A1D0A).opacity(0.7)), lineWidth: 1.5)
        // An inner shadow where the frame meets the doorway.
        var ic = c
        ic.clip(to: inner)
        ic.drawLayer { l in
            l.addFilter(.blur(radius: 6))
            l.stroke(rr(x - 3, y - 3, w + 6, h + 6, 4), with: .color(.black.opacity(0.55)), lineWidth: 8)
        }
        // Stepped lintel ornament at the top centre.
        for k in 0..<3 {
            let sw = 70 - Double(k) * 20
            c.fill(Path(CGRect(x: s.w / 2 - sw / 2, y: y - t - 6 - Double(k) * 5, width: sw, height: 6)), with: lin(s.w / 2 - sw / 2, 0, s.w / 2 + sw / 2, 0, brass, brassPos))
        }
    }

    // MARK: Moving parts

    /// Over the doors (inside the doorway): the glass tint and the sweeping highlight.
    static func doorGlass(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        guard pose.doors > 0.002 else { return }
        let half = s.doorW / 2
        let travel = (1 - pose.doors) * (half + 6)
        let lx = s.doorX - travel
        let rx = s.doorX + half + travel
        let v = vision(s)
        for gx in [lx + v.lx, rx + v.rx] {
            c.fill(Path(CGRect(x: gx, y: s.doorY + v.y, width: 16, height: v.h)), with: .color(Color(hex: 0x1A2A26).opacity(0.35)))
            c.fill(Path(CGRect(x: gx + 3, y: s.doorY + v.y, width: 3, height: v.h)), with: .color(.white.opacity(0.12)))
        }
        // A broad highlight fixed in the room, so it sweeps across the steel as the doors move.
        let spec = lin(s.doorX + s.doorW * 0.2, 0, s.doorX + s.doorW * 0.6, 0, [.rgba(255, 255, 255, 0), .rgba(255, 255, 255, 0.22), .rgba(255, 255, 255, 0)])
        var steel = Path(CGRect(x: lx, y: s.doorY, width: half, height: s.doorH))
        steel.addRect(CGRect(x: rx, y: s.doorY, width: half, height: s.doorH))
        c.fill(steel, with: spec)
    }

    /// The doors' shadow on the store where they meet.
    static func doorShadow(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        guard pose.doors > 0.002 else { return }
        let half = s.doorW / 2
        let travel = (1 - pose.doors) * (half + 6)
        blurred(c, Path(CGRect(x: s.doorX - travel + half - 2, y: s.doorY, width: 10, height: s.doorH)), .black, 0.35, 6)
    }

    static func lantern(_ c: GraphicsContext, _ x: Double, _ y: Double, up: Bool, glow: Double) {
        let d = up ? -1.0 : 1.0
        var p = Path()
        p.move(to: CGPoint(x: x, y: y + d * 9))
        p.addLine(to: CGPoint(x: x - 10, y: y - d * 7))
        p.addLine(to: CGPoint(x: x + 10, y: y - d * 7))
        p.closeSubpath()
        c.fill(p, with: .color(Color(hex: 0x3A2E1E)))
        if glow > 0.01 {
            var gc = c
            gc.blendMode = .plusLighter
            gc.drawLayer { l in
                l.addFilter(.blur(radius: 9))
                l.fill(circle(x, y, 18), with: .color(Color(hex: 0xFFB547).opacity(0.9 * glow)))
            }
            c.fill(p, with: .color(Color(hex: 0xFFD58A).opacity(glow)))
        }
        c.stroke(p, with: .color(Color(hex: 0xC59E57)), lineWidth: 1.2)
    }

    /// Needle, lanterns, the bell's ring and the buttons.
    static func instruments(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        // Needle, with its shadow on the enamel and a brass cap.
        let a = needleAngle(pose.needle) * .pi / 180
        let R = s.dialR
        let tipX = s.dialX + sin(a) * (R - 12)
        let tipY = s.dialY - cos(a) * (R - 12)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 2))
            l.stroke(line(s.dialX + 2, s.dialY + 3, tipX + 2, tipY + 3), with: .color(.black.opacity(0.35)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
        c.stroke(line(s.dialX - sin(a) * 10, s.dialY + cos(a) * 10, tipX, tipY), with: .color(Color(hex: 0x1E1812)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
        c.fill(circle(s.dialX, s.dialY, 8), with: rad(s.dialX - 2, s.dialY - 2, 9, [Color(hex: 0xF7E4B0), Color(hex: 0xB68C42), Color(hex: 0x5E4219)]))
        // Hall lanterns either side of the dial.
        lantern(c, s.dialX - R - 30, s.dialY - 22, up: true, glow: pose.ding[0])
        lantern(c, s.dialX + R + 30, s.dialY - 22, up: false, glow: pose.ding[1])
        // A ring of light from the dial when the bell rings.
        let bell = max(pose.ding[0], pose.ding[1])
        if bell > 0.01 {
            var rc = c
            rc.blendMode = .plusLighter
            rc.drawLayer { l in
                l.addFilter(.blur(radius: 4))
                l.stroke(circle(s.dialX, s.dialY, R + 14 + (1 - bell) * 40), with: .color(Color(hex: 0xFFC867).opacity(0.5 * bell)), lineWidth: 2)
            }
        }

        // Buttons: glass discs that dip when pressed and glow amber while their floor is called.
        if pose.lit.contains(where: { $0 > 0.01 }) {
            var hc = c
            hc.blendMode = .plusLighter
            hc.drawLayer { l in
                l.addFilter(.blur(radius: 12))
                for i in 0..<M.floors where pose.lit[i] > 0.01 {
                    l.fill(circle(s.buttonX[i], s.buttonY, s.buttonR + 8), with: .color(Color(hex: 0xFFB547).opacity(0.6 * M.clamp01(pose.lit[i]))))
                }
            }
        }
        let unlit = [Color(hex: 0xEFE6D4), Color(hex: 0xD4C6AA), Color(hex: 0xA8977A)]
        let on = [Color(hex: 0xFFF4D6), Color(hex: 0xFFC66B), Color(hex: 0xE08A2E)]
        for i in 0..<M.floors {
            let x = s.buttonX[i]
            let y = s.buttonY
            let lit = M.clamp01(pose.lit[i])
            let r = (s.buttonR - 3) * (1 - 0.06 * pose.dip[i])
            let by = y + 1.5 * pose.dip[i]
            c.fill(circle(x, by, r), with: rad(x - r * 0.35, y - r * 0.4, r * 1.6, unlit))
            if lit > 0.01 {
                var gc = c
                gc.opacity = lit
                gc.fill(circle(x, by, r), with: rad(x - r * 0.35, y - r * 0.4, r * 1.6, on))
            }
            c.fill(Path(ellipseIn: CGRect(x: x - r * 0.6, y: y - r * 0.8, width: r * 0.9, height: r * 0.45)), with: .color(.white.opacity(0.35)))
            let label = c.resolve(Text("\(i + 1)").font(.custom("Georgia-Bold", size: 19)).foregroundStyle(lit > 0.5 ? Color(hex: 0x5A2E05) : Color(hex: 0x3A2C18)))
            text(c, label, x: x, baseline: y + 7 + 1.5 * pose.dip[i], anchor: 0.5)
        }
    }
}
