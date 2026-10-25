import SwiftUI
import UIKit

/*
 Morse Key Input — the drawing. Two static Canvases drawn once (the desk: green leather, the
 telegram card with its typed clue and answer boxes, the key's walnut base with its contact post,
 trunnion and binding posts, and the wires to the register; the register: the brass box the tape
 comes out of), and, every frame, the timeline's Canvases: the tape and its ink, the torn strip
 falling away, the register's reel, the lever with its knob and spring, contact sparks, and the
 decoded letters.
*/
enum MorseKeyInputArt {
    typealias M = MorseKeyInputMotion

    static let tilt = 3.3
    static let brass = [Color(hex: 0x6E4F22), Color(hex: 0xC9A35C), Color(hex: 0xF0D9A0), Color(hex: 0xB88E45), Color(hex: 0x5E421B)]
    static let brassPos = [0, 0.3, 0.45, 0.7, 1]
    static let red = Color(hex: 0x9A3B2A)

    struct Scene: Equatable {
        var w: Double
        var h: Double
        var cardX: Double
        var cardY: Double
        var cardW: Double
        var cardH: Double
        var slotY: Double
        var slotW: Double
        var slotH: Double
        var slotX: [Double]
        /// Tape: centre line, height; print head x (where the tape leaves the register).
        var tapeY: Double
        var tapeH: Double
        var headX: Double
        /// Resting tape length (a blank leader out of the register).
        var leader: Double
        var regX: Double
        var regY: Double
        var regW: Double
        var regH: Double
        var baseTop: Double
        var baseX: Double
        var baseW: Double
        var leverY: Double
        var pivotX: Double
        var knobX: Double
        var contactX: Double
        var springX: Double

        init(w: Double, h: Double, top: Double, bottom: Double) {
            self.w = w
            self.h = h
            cardX = 16
            cardY = top + 108
            cardW = w - 32
            cardH = 176
            slotW = 52
            slotH = 62
            slotY = cardY + cardH - slotH - 18
            slotX = [cardX + 20, cardX + 20 + slotW + 10]
            tapeY = cardY + cardH + 58
            tapeH = 30
            regW = 78
            regX = w - regW - 14
            regY = tapeY - 40
            regH = 94
            headX = regX + 2
            leader = headX - 22
            baseTop = h - bottom - 60 - 128
            baseX = 22
            baseW = w - 44
            leverY = baseTop - 56
            pivotX = w - 112
            knobX = 82
            contactX = (w * 0.46).rounded()
            springX = (w * 0.46).rounded() + 54
        }
    }

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

    static func text(_ ctx: GraphicsContext, _ r: GraphicsContext.ResolvedText, x: Double, baseline: Double, anchor: Double = 0) -> Double {
        let s = r.measure(in: CGSize(width: 2000, height: 2000))
        let b = r.firstBaseline(in: s)
        ctx.draw(r, in: CGRect(x: x - s.width * anchor, y: baseline - b, width: s.width, height: s.height))
        return s.width
    }

    static func mono(_ size: Double, bold: Bool) -> Font {
        .custom(bold ? "Menlo-Bold" : "Menlo-Regular", size: size)
    }

    // MARK: Grain tiles (periodic value noise, built once)

    static let leatherGrain = noiseTile(width: 128, height: 128, cellX: 1.43, cellY: 1.43, seed: 31)
    static let paperGrain = noiseTile(width: 128, height: 128, cellX: 0.71, cellY: 0.71, seed: 7)
    static let walnutGrain = noiseTile(width: 500, height: 126, cellX: 83, cellY: 2, seed: 41)
    static let topGrain = noiseTile(width: 500, height: 126, cellX: 83, cellY: 1.67, seed: 43)

    /// A tile of two-octave periodic value noise, grey around 0.5, at 2 px per point.
    static func noiseTile(width: Int, height: Int, cellX: Double, cellY: Double, seed: UInt32) -> Image {
        let scale = 2
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

    // MARK: Static layers

    /// Desk, card, key base and its fixed parts, wires.
    static func desk(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w, h = s.h
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        // Green leather desk with a lamp's pool of light and a vignette.
        c.fill(Path(full), with: lin(0, 0, 0, h, [Color(hex: 0x1A2A25), Color(hex: 0x14221E), Color(hex: 0x101B18), Color(hex: 0x0C1513)], [0, 0.4, 0.75, 1]))
        c.fill(Path(full), with: rad(w * 0.42, s.baseTop - 60, h * 0.55, [.rgba(214, 190, 130, 0.16), .rgba(214, 190, 130, 0.05), .rgba(0, 0, 0, 0)], [0, 0.5, 1]))
        grain(c, full, leatherGrain, 0.14)
        c.fill(Path(full), with: rad(w * 0.5, h * 0.5, h * 0.72, [.rgba(0, 0, 0, 0), .rgba(0, 0, 0, 0), .rgba(2, 6, 5, 0.25), .rgba(2, 6, 5, 0.62)], [0, 0.4, 0.75, 1]))

        // The telegram card: cream paper, two shadows, a red-brown printed header and typed clue.
        let x = s.cardX, y = s.cardY, cw = s.cardW, ch = s.cardH
        blurred(c, rr(x, y + 14, cw, ch, 14), .black, 0.45, 16)
        blurred(c, rr(x, y + 1.5, cw, ch, 14), .black, 0.5, 1.5)
        let card = rr(x, y, cw, ch, 14)
        c.fill(card, with: lin(x, y, x + cw * 0.3, y + ch, [Color(hex: 0xF3EAD6), Color(hex: 0xEADFC6), Color(hex: 0xE0D2B5)]))
        var cc = c
        cc.clip(to: card)
        grain(cc, CGRect(x: x, y: y, width: cw, height: ch), paperGrain, 0.16)
        _ = text(c, c.resolve(Text("TELEGRAM").font(mono(11, bold: true)).foregroundStyle(red)), x: x + 20, baseline: y + 28)
        _ = text(c, c.resolve(Text("PUZZLE No. 27").font(mono(11, bold: true)).foregroundStyle(red)), x: x + cw - 20, baseline: y + 28, anchor: 1)
        c.stroke(line(x + 20, y + 38, x + cw - 20, y + 38), with: .color(red.opacity(0.7)), lineWidth: 1.2)
        c.stroke(line(x + 20, y + 41, x + cw - 20, y + 41), with: .color(red.opacity(0.7)), lineWidth: 0.6)
        let ink = Color(hex: 0x2D2A26).opacity(0.88)
        _ = text(c, c.resolve(Text("THE OPERATOR IS WAITING STOP").font(mono(12.5, bold: false)).foregroundStyle(ink)), x: x + 20, baseline: y + 62)
        _ = text(c, c.resolve(Text("SAY HELLO IN TWO LETTERS STOP").font(mono(12.5, bold: false)).foregroundStyle(ink)), x: x + 20, baseline: y + 80)
        // Answer boxes: typed outlines with a baseline.
        for sx in s.slotX {
            let box = rr(sx, s.slotY, s.slotW, s.slotH, 8)
            c.fill(box, with: .color(Color(hex: 0xD9CBAE).opacity(0.55)))
            c.stroke(box, with: .color(Color(hex: 0x8C7A5C).opacity(0.6)), lineWidth: 1)
            c.stroke(line(sx + 12, s.slotY + s.slotH - 12, sx + s.slotW - 12, s.slotY + s.slotH - 12), with: .color(Color(hex: 0x8C7A5C).opacity(0.8)), lineWidth: 1.5)
        }

        // Wires from the binding posts up into the register: cloth-covered, one red, one green.
        let postA = s.baseX + s.baseW - 44
        let postB = s.baseX + s.baseW - 24
        let regBottom = s.regY + s.regH
        for (px, col, dx) in [(postA, Color(hex: 0x7A2E24), -10.0), (postB, Color(hex: 0x2F5A43), 8.0)] {
            var path = Path()
            path.move(to: CGPoint(x: px, y: s.baseTop - 20))
            path.addCurve(to: CGPoint(x: s.regX + s.regW / 2 + dx, y: regBottom - 4),
                          control1: CGPoint(x: px - 30 + dx, y: s.baseTop - 120),
                          control2: CGPoint(x: s.regX + s.regW / 2 + dx - 20, y: regBottom + 90))
            c.drawLayer { l in
                l.addFilter(.blur(radius: 3))
                l.translateBy(x: 3, y: 5)
                l.stroke(path, with: .color(.black.opacity(0.45)), lineWidth: 4)
            }
            c.stroke(path, with: .color(col), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
            var hc = c
            hc.translateBy(x: -0.8, y: -0.8)
            hc.stroke(path, with: .color(.white.opacity(0.18)), lineWidth: 1)
        }

        // The key's base: a walnut block seen from the front, its top face catching the lamp.
        let bx = s.baseX, bw = s.baseW, top = s.baseTop
        blurred(c, rr(bx + 4, top + 30, bw - 8, 44, 10), .black, 0.6, 14)
        blurred(c, rr(bx, top + 2, bw, 58, 6), .black, 0.6, 1.5)
        let face = rr(bx, top + 12, bw, 46, 6)
        c.fill(face, with: lin(0, top + 12, 0, top + 58, [Color(hex: 0x5A3720), Color(hex: 0x45291A), Color(hex: 0x2F1C12)]))
        var fc = c
        fc.clip(to: face)
        grain(fc, CGRect(x: bx, y: top + 12, width: bw, height: 46), walnutGrain, 0.4)
        let topFace = rr(bx + 3, top, bw - 6, 14, 5)
        c.fill(topFace, with: lin(0, top, 0, top + 14, [Color(hex: 0x8A5C3A), Color(hex: 0x734A2D), Color(hex: 0x5E3B23)]))
        var tc = c
        tc.clip(to: topFace)
        grain(tc, CGRect(x: bx, y: top, width: bw, height: 14), topGrain, 0.35)
        c.stroke(line(bx + 8, top + 0.6, bx + bw - 8, top + 0.6), with: .color(Color(hex: 0xFFD9AE).opacity(0.35)), lineWidth: 1)
        // A small engraved brass plate on the front.
        let plateW = 92.0
        let px0 = bx + bw / 2 - plateW / 2
        c.fill(rr(px0, top + 26, plateW, 18, 3), with: lin(px0, 0, px0 + plateW, 0, brass, brassPos))
        _ = text(c, c.resolve(Text("DOT DASH  No. 3").font(mono(8.5, bold: true)).foregroundStyle(Color(hex: 0x3B2A12).opacity(0.85))), x: px0 + plateW / 2, baseline: top + 38.5, anchor: 0.5)
        // Lower contact: a silver post with a domed tip.
        let cx = s.contactX
        let tipY = s.leverY + 16
        c.fill(Path(CGRect(x: cx - 5, y: tipY + 3, width: 10, height: top - tipY)), with: lin(cx - 5, 0, cx + 5, 0, [Color(hex: 0x6F7378), Color(hex: 0xE4E7EA), Color(hex: 0x9EA3A8), Color(hex: 0x55595E)], [0, 0.35, 0.65, 1]))
        c.fill(Path(ellipseIn: CGRect(x: cx - 7, y: top - 4, width: 14, height: 7)), with: lin(cx - 7, 0, cx + 7, 0, brass, brassPos))
        c.fill(Path(ellipseIn: CGRect(x: cx - 4, y: tipY, width: 8, height: 6)), with: rad(cx - 1, tipY + 2, 5, [.white, Color(hex: 0xB9BEC4), Color(hex: 0x6B7075)]))
        // Trunnion: a brass upright with the axle's screw head.
        let tx = s.pivotX
        c.fill(rr(tx - 9, s.leverY - 12, 18, top - s.leverY + 14, 4), with: lin(tx - 9, 0, tx + 9, 0, brass, brassPos))
        // Binding posts: knurled brass with a dark collar.
        for px in [postA, postB] {
            c.fill(Path(CGRect(x: px - 6, y: top - 22, width: 12, height: 24)), with: lin(px - 6, 0, px + 6, 0, brass, brassPos))
            var knurl = Path()
            for k in stride(from: -4.0, through: 4.0, by: 2.0) {
                knurl.move(to: CGPoint(x: px + k, y: top - 20))
                knurl.addLine(to: CGPoint(x: px + k, y: top - 12))
            }
            c.stroke(knurl, with: .color(Color(hex: 0x4A3412).opacity(0.5)), lineWidth: 0.8)
            c.fill(Path(CGRect(x: px - 7, y: top - 6, width: 14, height: 5)), with: .color(Color(hex: 0x2A1E12)))
        }
    }

    /// The register the tape comes out of.
    static func register(_ c: GraphicsContext, _ s: Scene) {
        let x = s.regX, y = s.regY, w = s.regW, h = s.regH
        blurred(c, rr(x - 2, y + 16, w + 4, h, 10), .black, 0.55, 14)
        blurred(c, rr(x, y + 1.5, w, h, 10), .black, 0.6, 1.5)
        c.fill(rr(x, y, w, h, 10), with: lin(0, y, 0, y + h, [Color(hex: 0x2B3330), Color(hex: 0x1C2321), Color(hex: 0x141A18)]))
        c.stroke(rr(x + 1, y + 1, w - 2, h - 2, 9), with: lin(x, 0, x + w, 0, brass, brassPos), lineWidth: 2)
        // The window over the paper reel (the reel itself is drawn each frame).
        let wy = y + 10
        c.fill(circle(x + w / 2, wy + 24, 25), with: .color(Color(hex: 0x0A0E0D)))
        c.stroke(circle(x + w / 2, wy + 24, 26), with: lin(x, 0, x + w, 0, brass, brassPos), lineWidth: 3)
        // The slot the tape comes out of, at the tape's height on the left edge.
        c.fill(Path(CGRect(x: x - 1, y: s.tapeY - s.tapeH / 2 - 3, width: 8, height: s.tapeH + 6)), with: .color(Color(hex: 0x0A0E0D)))
        _ = text(c, c.resolve(Text("REGISTER").font(mono(8.5, bold: true)).foregroundStyle(Color(hex: 0xD8BC80).opacity(0.75))), x: x + w / 2, baseline: y + h - 12, anchor: 0.5)
    }

    // MARK: Moving parts

    /// One strip of tape whose free end is `len` to the left of the head, with its ink.
    static func strip(_ c: GraphicsContext, _ s: Scene, _ strip: M.Strip, torn: Bool) {
        if strip.len < 0.5 { return }
        let x1 = s.headX + 4
        let x0 = s.headX - strip.len
        let top = s.tapeY - s.tapeH / 2
        let bottom = s.tapeY + s.tapeH / 2
        // The free end is torn: a jagged edge, seeded so it never flickers.
        var path = Path()
        path.move(to: CGPoint(x: x1, y: top))
        path.addLine(to: CGPoint(x: x0 + 2, y: top))
        for i in 0...8 {
            let y = top + s.tapeH * Double(i) / 8
            let fi = Double(i)
            path.addLine(to: CGPoint(x: x0 + (M.hash(fi + 3.3) - 0.5) * 4 + (torn ? (M.hash(fi * 7.1) - 0.5) * 3 : 0), y: y))
        }
        path.addLine(to: CGPoint(x: x1, y: bottom))
        path.closeSubpath()
        c.drawLayer { l in
            l.addFilter(.blur(radius: 4))
            l.translateBy(x: 0, y: 5)
            l.fill(path, with: .color(.black.opacity(0.45)))
        }
        c.fill(path, with: lin(0, top, 0, bottom, [Color(hex: 0xF4ECD9), Color(hex: 0xEDE3CC), Color(hex: 0xE2D5B8)]))
        var edges = line(max(x0, -10), top + 0.5, x1, top + 0.5)
        edges.addPath(line(max(x0, -10), bottom - 0.5, x1, bottom - 0.5))
        c.stroke(edges, with: .color(Color(hex: 0xB9A984).opacity(0.6)), lineWidth: 0.8)
        // Ink: each mark sits where its run has carried it, a little bleed around the stroke.
        var ink = Path()
        let m = strip.marks
        var i = 0
        while i + 1 < m.count {
            let a = s.headX - (strip.run - m[i])
            let e = s.headX - (strip.run - m[i + 1])
            let mid = (a + e) / 2
            let half = max(3.8, abs(e - a) / 2)
            i += 2
            if mid + half < -10 || mid - half > s.headX { continue }
            ink.addPath(rr(mid - half, s.tapeY - 3.8, half * 2, 7.6, 3.8))
        }
        if ink.isEmpty { return }
        let inkColor = Color(hex: 0x1D2230)
        blurred(c, ink, inkColor, 0.3, 1.6)
        c.fill(ink, with: .color(inkColor.opacity(0.92)))
    }

    /// The lever group, rotated about the trunnion: bar, knob, upper contact screw.
    static func lever(_ ctx: GraphicsContext, _ s: Scene, _ depth: Double) {
        let angle = tilt * depth
        let y = s.leverY
        // Its shadow on the base's top face.
        blurred(ctx, rr(s.knobX - 20, s.baseTop - 3, s.pivotX - s.knobX + 40, 8, 4), .black, 0.4, 6)
        var c = ctx
        c.translateBy(x: s.pivotX, y: y)
        c.rotate(by: .degrees(-angle))
        c.translateBy(x: -s.pivotX, y: -y)
        // Bar: brass with a bright upper edge.
        c.fill(rr(s.knobX - 18, y - 7, s.pivotX - s.knobX + 46, 14, 4), with: lin(0, y - 7, 0, y + 7, [Color(hex: 0xF2DDA6), Color(hex: 0xC9A15A), Color(hex: 0x94702F), Color(hex: 0x5F451B)], [0, 0.35, 0.7, 1]))
        c.stroke(line(s.knobX - 14, y - 6.4, s.pivotX + 24, y - 6.4), with: .color(Color(hex: 0xFFF3D0).opacity(0.5)), lineWidth: 1)
        // Axle boss at the pivot.
        c.fill(circle(s.pivotX, y, 8), with: rad(s.pivotX - 2, y - 2, 9, [Color(hex: 0xF5E2AE), Color(hex: 0xB08A44), Color(hex: 0x5E451B)]))
        c.fill(circle(s.pivotX, y, 2.2), with: .color(Color(hex: 0x3A2A10)))
        // Upper contact screw under the bar.
        c.fill(Path(CGRect(x: s.contactX - 3.5, y: y + 7, width: 7, height: 9)), with: lin(s.contactX - 3.5, 0, s.contactX + 3.5, 0, [Color(hex: 0x7A7F84), Color(hex: 0xE8EBEE), Color(hex: 0x62666B)]))
        c.fill(Path(ellipseIn: CGRect(x: s.contactX - 4, y: y + 13, width: 8, height: 4)), with: .color(Color(hex: 0xC8CDD2)))
        // Knob: a shaft and a black bakelite mushroom with a soft top highlight.
        let kx = s.knobX
        c.fill(Path(CGRect(x: kx - 5, y: y - 24, width: 10, height: 20)), with: lin(kx - 5, 0, kx + 5, 0, brass, brassPos))
        c.fill(rr(kx - 29, y - 52, 58, 30, 13), with: lin(kx - 29, 0, kx + 29, 0, [Color(hex: 0x0F0D0C), Color(hex: 0x2C2724), Color(hex: 0x3A3430), Color(hex: 0x1A1715), Color(hex: 0x0B0A09)], [0, 0.3, 0.42, 0.7, 1]))
        c.fill(Path(ellipseIn: CGRect(x: kx - 33, y: y - 64, width: 66, height: 22)), with: rad(kx - 10, y - 59, 36, [Color(hex: 0x4A433E), Color(hex: 0x24201D), Color(hex: 0x121010)]))
        c.fill(Path(ellipseIn: CGRect(x: kx - 20, y: y - 62, width: 26, height: 7)), with: .color(.white.opacity(0.16)))
    }

    static func spring(_ c: GraphicsContext, _ s: Scene, _ depth: Double) {
        let drop = (s.pivotX - s.springX) * sin(tilt * depth * .pi / 180)
        let top = s.leverY + 6 + drop
        let bottom = s.baseTop + 1
        let turns = 9
        var path = Path()
        path.move(to: CGPoint(x: s.springX, y: top))
        for i in 0...(turns * 2) {
            let yy = top + (bottom - top) * Double(i) / Double(turns * 2)
            path.addLine(to: CGPoint(x: s.springX + (i % 2 == 0 ? -6 : 6), y: yy))
        }
        c.stroke(path, with: .color(Color(hex: 0xB8934E)), lineWidth: 1.6)
        c.stroke(path, with: .color(Color(hex: 0xFFF0C8).opacity(0.45)), lineWidth: 0.6)
    }

    static func spark(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        let x = s.contactX
        let y = s.leverY + 17
        let age = pose.sparkAge
        var pc = c
        pc.blendMode = .plusLighter
        if age >= 0 && age < 0.25 {
            let flash = exp(-age * 24)
            var gc = pc
            gc.opacity = flash
            gc.fill(circle(x, y, 34), with: rad(x, y, 34, [.rgba(200, 225, 255, 0.95), .rgba(120, 170, 255, 0.35), .rgba(80, 120, 255, 0)], [0, 0.35, 1]))
            var lines = Path()
            for i in 0..<9 {
                let fi = Double(i)
                let ang = (-170 + 160 * M.hash(fi * 2.3)) * .pi / 180
                let sp = 70 + 160 * M.hash(fi * 5.1)
                let life = 0.1 + 0.12 * M.hash(fi * 9.7)
                if age > life { continue }
                let a0 = max(0, age - 0.025)
                lines.move(to: CGPoint(x: x + cos(ang) * sp * a0, y: y + sin(ang) * sp * a0 + 300 * a0 * a0))
                lines.addLine(to: CGPoint(x: x + cos(ang) * sp * age, y: y + sin(ang) * sp * age + 300 * age * age))
            }
            pc.stroke(lines, with: .color(Color(hex: 0xE6F0FF)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        }
        if pose.hum > 0 {
            pc.fill(circle(x, y, 12), with: rad(x, y, 12, [.rgba(170, 200, 255, 0.55), .rgba(170, 200, 255, 0)]))
        }
    }

    static func reel(_ c: GraphicsContext, _ s: Scene, _ run: Double) {
        let cx = s.regX + s.regW / 2
        let cy = s.regY + 34
        c.fill(circle(cx, cy, 21), with: rad(cx - 4, cy - 4, 24, [Color(hex: 0xF2EAD8), Color(hex: 0xE2D5BA), Color(hex: 0xBFAF8E)]))
        // Paper layers and three spokes turning as the tape pays out.
        var rings = Path()
        for r in stride(from: 9.0, to: 21.0, by: 3.0) { rings.addPath(circle(cx, cy, r)) }
        c.stroke(rings, with: .color(Color(hex: 0xA8987A).opacity(0.45)), lineWidth: 0.5)
        var spokes = Path()
        let turn = -run / 21
        for i in 0..<3 {
            let a = Double(i) * 2 * .pi / 3 + turn
            spokes.move(to: CGPoint(x: cx, y: cy))
            spokes.addLine(to: CGPoint(x: cx + cos(a) * 8, y: cy + sin(a) * 8))
        }
        c.stroke(spokes, with: .color(Color(hex: 0x6E5C3E)), lineWidth: 2)
        c.fill(circle(cx, cy, 3), with: rad(cx - 1, cy - 1, 4, [Color(hex: 0xF5E2AE), Color(hex: 0x8A6A2E)]))
        // Glass reflection over the window.
        c.fill(circle(cx, cy, 24), with: lin(cx - 24, cy - 24, cx + 10, cy + 10, [.rgba(255, 255, 255, 0.22), .rgba(255, 255, 255, 0.04), .rgba(255, 255, 255, 0)], [0, 0.45, 1]))
    }

    static func answer(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        // Decoded letters roll up into their boxes; clearing, they roll on up and out.
        for i in 0..<2 {
            let sl = pose.slots[i]
            if sl.ch.isEmpty || sl.inn <= 0.001 { continue }
            let base = s.slotY + s.slotH - 17
            let dy = (1 - sl.inn) * 18 - sl.out * 20
            let a = M.clamp01(sl.inn * 1.4) * (1 - sl.out)
            if a <= 0.001 { continue }
            var lc = c
            lc.clip(to: Path(CGRect(x: s.slotX[i] + 2, y: s.slotY + 2, width: s.slotW - 4, height: s.slotH - 4)))
            let stamp = 1 + 0.12 * (1 - sl.inn)
            lc.translateBy(x: s.slotX[i] + s.slotW / 2, y: base + dy)
            lc.scaleBy(x: stamp, y: stamp)
            lc.opacity = a
            _ = text(lc, lc.resolve(Text(sl.ch).font(mono(36, bold: true)).foregroundStyle(Color(hex: 0x23201C))), x: 0, baseline: 0, anchor: 0.5)
        }
        // The letter in progress: its dots and dashes next to the boxes.
        if pose.bufferAlpha > 0 && !pose.buffer.isEmpty {
            let y = s.slotY + s.slotH / 2
            var x = s.slotX[1] + s.slotW + 18
            var marks = Path()
            for ch in pose.buffer {
                if ch == "." {
                    marks.addPath(circle(x + 3, y, 3.4))
                    x += 14
                } else {
                    marks.addPath(rr(x, y - 3, 18, 6, 3))
                    x += 26
                }
            }
            c.fill(marks, with: .color(red.opacity(0.9)))
        }
        // Result chip on the right of the row.
        if pose.chip > 0.005 {
            let label = c.resolve(Text(pose.correct > 0.5 ? "Correct · +40" : "Not quite").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color(hex: 0xF6EEDC)))
            let tw = label.measure(in: CGSize(width: 1000, height: 100)).width
            let w = tw + 28
            let x = s.cardX + s.cardW - 20 - w
            let y = s.slotY + s.slotH / 2 - 15 + (1 - pose.chip) * 6
            let col = pose.correct > 0.5 ? Color(hex: 0x2F6B4A) : red
            c.fill(rr(x, y, w, 30, 15), with: .color(col.opacity(0.92 * pose.chip)))
            var tc = c
            tc.opacity = pose.chip
            _ = text(tc, label, x: x + 14, baseline: y + 19.5)
        }
    }

    /// Behind the register: the answer row, the tape and the torn strip falling away.
    static func under(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        answer(c, s, pose)
        strip(c, s, pose.tape, torn: true)
        if pose.fall > 0 && pose.fall < 1 {
            // Torn off at the head: it drops, swings away from the tear and fades.
            let k = pose.fall
            var fc = c
            fc.opacity = 1 - M.clamp01((k - 0.45) / 0.55)
            fc.drawLayer { l in
                l.translateBy(x: -30 * k, y: 300 * k * k)
                l.translateBy(x: s.headX, y: s.tapeY)
                l.rotate(by: .degrees(-10 * k))
                l.translateBy(x: -s.headX, y: -s.tapeY)
                strip(l, s, pose.torn, torn: true)
            }
        }
    }

    /// Over the register: the reel, the pen, the spring, the lever and the sparks.
    static func over(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        reel(c, s, pose.tape.run)
        // The pen: an inked wheel in the slot, down on the tape while the contact is closed.
        let penY = s.tapeY - 2 + 2 * pose.pen
        c.fill(circle(s.headX + 4, penY, 4), with: rad(s.headX + 3, penY - 1, 5, [Color(hex: 0x8A8F96), Color(hex: 0x2A2E36)]))
        spring(c, s, pose.depth)
        lever(c, s, pose.depth)
        spark(c, s, pose)
    }
}
