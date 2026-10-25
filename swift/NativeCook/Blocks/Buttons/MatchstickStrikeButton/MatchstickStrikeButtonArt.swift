import CoreText
import SwiftUI
import UIKit

/*
 Matchstick Strike Button — the drawing. Two static Canvases drawn once (the table: walnut
 planks, the product card with an amber candle jar; the matchbox: oxblood label, sandpaper
 striker, printing), with the match, the CTA's letters, sparks and the warm light drawn by the
 timeline's Canvases and the fire by a Metal shader (MatchstickStrikeButton.metal). A match going
 back into the box is drawn under the box.
*/
enum MatchstickStrikeButtonArt {
    typealias M = MatchstickStrikeButtonMotion

    static let cta = "Strike to start"
    static let ctaFont = UIFont.systemFont(ofSize: 31, weight: .semibold)
    static let flameGold = [1.0, 0.77, 0.42]
    static let flash = [1.0, 0.96, 0.84]
    static let print = [0.23, 0.075, 0.06]
    static let cream = Color(hex: 0xF1DEC2)

    struct Scene: Equatable {
        var w: Double
        var h: Double
        var cardX: Double
        var cardY: Double
        var cardW: Double
        var cardH: Double
        var boxX: Double
        var boxY: Double
        var boxW: Double
        var boxH: Double
        /// Letter left edges (the glyphs are drawn one by one so each can catch on its own).
        var letterX: [Double]
        var geo: M.Geo

        init(w: Double, h: Double, top: Double, bottom: Double) {
            self.w = w
            self.h = h
            cardX = 16
            cardY = top + 112
            cardW = w - 32
            cardH = (min(142, max(112, h * 0.165))).rounded()
            boxX = 20
            boxW = w - 40
            boxH = (min(196, max(160, h * 0.23))).rounded()
            let boxBottom = h - bottom - 92
            boxY = boxBottom - boxH
            let strikerTop = boxY + 14
            let strikerBottom = boxY + 40
            let strikerLeft = boxX + 18
            let strikerRight = boxX + boxW - 18
            let baseline = boxY + (boxH * 0.6).rounded()
            let widths = MatchstickStrikeButtonArt.glyphWidths(cta, ctaFont)
            let total = widths.reduce(0, +)
            var x = w / 2 - total / 2
            var lx: [Double] = []
            var letters: [Double] = []
            for g in widths {
                lx.append(x)
                letters.append(x + g / 2)
                x += g
            }
            letterX = lx
            let midStrip = (strikerTop + strikerBottom) / 2
            geo = M.Geo(
                rest: CGPoint(x: boxX + 34, y: boxY - 32),
                strikeFrom: CGPoint(x: strikerRight - 20, y: midStrip),
                strikeTo: CGPoint(x: strikerLeft + 36, y: midStrip),
                glideFrom: CGPoint(x: lx[0] - 12, y: baseline + 16),
                glideTo: CGPoint(x: x + 12, y: baseline + 16),
                strikerTop: strikerTop,
                strikerBottom: strikerBottom,
                strikerLeft: strikerLeft,
                strikerRight: strikerRight,
                boxTop: boxY,
                letters: letters,
                baseline: baseline,
                matchLength: min(270, w * 0.68)
            )
        }
    }

    /// Advance of each character (no kerning: each letter is drawn on its own).
    static func glyphWidths(_ s: String, _ font: UIFont) -> [Double] {
        let ct = font as CTFont
        let chars = Array(s.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &adv, chars.count)
        return adv.map { Double($0.width) }
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

    static func blurred(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ alpha: Double, _ sigma: Double) {
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: sigma))
            l.fill(path, with: .color(color.opacity(alpha)))
        }
    }

    static func grain(_ ctx: GraphicsContext, _ rect: CGRect, _ tile: Image, _ alpha: Double, _ mode: GraphicsContext.BlendMode = .overlay) {
        var g = ctx
        g.blendMode = mode
        g.opacity = alpha
        g.fill(Path(rect), with: .tiledImage(tile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func text(_ ctx: GraphicsContext, _ r: GraphicsContext.ResolvedText, x: Double, baseline: Double, centered: Bool) {
        let s = r.measure(in: CGSize(width: 2000, height: 2000))
        let b = r.firstBaseline(in: s)
        ctx.draw(r, in: CGRect(x: centered ? x - s.width / 2 : x, y: baseline - b, width: s.width, height: s.height))
    }

    static func rgb(_ c: [Double], _ a: Double = 1) -> Color {
        Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: a)
    }

    // MARK: Grain tiles (periodic value noise, built once)

    static let fineGrain = noiseTile(width: 128, height: 128, cellX: 1.1, cellY: 1.1, seed: 4)
    static let plankGrain = noiseTile(width: 498, height: 129, cellX: 166, cellY: 3, seed: 9)
    static let labelGrain = noiseTile(width: 128, height: 128, cellX: 0.77, cellY: 0.77, seed: 21)
    static let gritGrain = noiseTile(width: 96, height: 96, cellX: 0.42, cellY: 0.42, seed: 13)
    static let speckle = noiseTile(width: 96, height: 96, cellX: 1.1, cellY: 1.1, seed: 17)

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

    /// Walnut table, the product card and its candle jar.
    static func table(_ c: GraphicsContext, _ s: Scene) {
        let w = s.w, h = s.h
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        c.fill(Path(full), with: lin(0, 0, 0, h, [Color(hex: 0x21160F), Color(hex: 0x1A110C), Color(hex: 0x150E0A), Color(hex: 0x100B08)], [0, 0.4, 0.75, 1]))
        // Long grain along the planks, and the seams between them.
        grain(c, full, plankGrain, 0.22)
        grain(c, full, fineGrain, 0.06)
        var y = 96.0
        while y < h {
            c.stroke(line(0, y, w, y), with: .color(Color(hex: 0x070403).opacity(0.55)), lineWidth: 1)
            c.stroke(line(0, y + 1, w, y + 1), with: .color(Color(hex: 0xFFD9B0).opacity(0.05)), lineWidth: 1)
            y += 118
        }
        c.fill(Path(full), with: rad(w * 0.5, h * 0.55, h * 0.72, [.rgba(0, 0, 0, 0), .rgba(0, 0, 0, 0), .rgba(5, 3, 2, 0.25), .rgba(5, 3, 2, 0.68)], [0, 0.35, 0.72, 1]))

        // Card: two shadows, a warm three-stop body, hairline highlight.
        let r = 22.0
        blurred(c, rr(s.cardX, s.cardY + 14, s.cardW, s.cardH, r), .black, 0.5, 16)
        blurred(c, rr(s.cardX, s.cardY + 1.5, s.cardW, s.cardH, r), .black, 0.55, 1.5)
        let card = rr(s.cardX, s.cardY, s.cardW, s.cardH, r)
        c.fill(card, with: lin(0, s.cardY, 0, s.cardY + s.cardH, [Color(hex: 0x3A2A20), Color(hex: 0x2D2119), Color(hex: 0x261B15)]))
        var cc = c
        cc.clip(to: card)
        grain(cc, CGRect(x: s.cardX, y: s.cardY, width: s.cardW, height: s.cardH), fineGrain, 0.08)
        c.stroke(rr(s.cardX + 0.5, s.cardY + 0.5, s.cardW - 1, s.cardH - 1, r - 0.5), with: .color(Color(hex: 0xFFE2C2).opacity(0.12)), lineWidth: 1)

        // The candle jar: amber glass, cream wax with three wicks, a printed paper band.
        let jh = s.cardH - 32
        let jw = (jh * 0.8).rounded()
        let jx = s.cardX + 18
        let jy = s.cardY + 16
        blurred(c, Path(ellipseIn: CGRect(x: jx - 4, y: jy + jh - 8, width: jw + 8, height: 14)), .black, 0.55, 5)
        c.fill(rr(jx, jy + 6, jw, jh - 6, 12), with: lin(jx, 0, jx + jw, 0, [Color(hex: 0x4A240C), Color(hex: 0x9C5A22), Color(hex: 0xC98A45), Color(hex: 0x7A4316), Color(hex: 0x3B1C09)], [0, 0.28, 0.42, 0.75, 1]))
        c.fill(Path(ellipseIn: CGRect(x: jx + 4, y: jy + 2, width: jw - 8, height: 14)), with: lin(0, jy, 0, jy + 14, [Color(hex: 0xF4E6CC), Color(hex: 0xE2CDA8), Color(hex: 0xC9B08A)]))
        var wicks = Path()
        for i in -1...1 {
            let wx = jx + jw / 2 + Double(i) * jw * 0.2
            wicks.move(to: CGPoint(x: wx, y: jy + 9))
            wicks.addLine(to: CGPoint(x: wx + 1, y: jy + 3))
        }
        c.stroke(wicks, with: .color(Color(hex: 0x2A1B12)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        let by = jy + jh * 0.42
        c.fill(Path(CGRect(x: jx + 1, y: by, width: jw - 2, height: jh * 0.3)), with: lin(jx, 0, jx + jw, 0, [Color(hex: 0xCDB894), Color(hex: 0xEBDDC2), Color(hex: 0xD9C6A3), Color(hex: 0xB29C78)], [0, 0.35, 0.7, 1]))
        let label = c.resolve(Text("FIG & CEDAR").font(.custom("Georgia-Bold", size: 7.5)).foregroundStyle(Color(hex: 0x4A2A18).opacity(0.9)))
        text(c, label, x: jx + jw / 2, baseline: by + jh * 0.15 + 3, centered: true)
        c.stroke(line(jx + 10, by + jh * 0.24, jx + jw - 10, by + jh * 0.24), with: .color(Color(hex: 0x4A2A18).opacity(0.4)), lineWidth: 1)
        // Glass highlights: a long specular strip and a rim.
        c.fill(rr(jx + jw * 0.16, jy + 12, jw * 0.1, jh - 26, 3), with: lin(0, jy + 10, 0, jy + jh, [.rgba(255, 236, 206, 0.42), .rgba(255, 236, 206, 0.1), .rgba(255, 236, 206, 0)]))
        c.stroke(rr(jx + 0.5, jy + 6.5, jw - 1, jh - 7, 11.5), with: .color(Color(hex: 0xFFE0B0).opacity(0.3)), lineWidth: 1)
    }

    /// The matchbox, transparent around it, so a match can pass behind it.
    static func box(_ c: GraphicsContext, _ s: Scene) {
        let x = s.boxX, y = s.boxY, w = s.boxW, h = s.boxH
        let g = s.geo
        // Shadows on the table, then the drawer's edge (the box's thickness) under the label.
        blurred(c, rr(x, y + 22, w, h, 10), .black, 0.55, 20)
        blurred(c, rr(x, y + 4, w, h + 10, 8), .black, 0.6, 2)
        c.fill(rr(x, y + h - 12, w, 24, 8), with: lin(0, y + h - 10, 0, y + h + 12, [Color(hex: 0x5A2019), Color(hex: 0x3E140F), Color(hex: 0x2A0D0A)]))
        // The label: oxblood paper with grain and a soft sheen from the upper left.
        let face = rr(x, y, w, h, 8)
        c.fill(face, with: lin(x, y, x + w * 0.4, y + h, [Color(hex: 0x8A3328), Color(hex: 0x74281F), Color(hex: 0x621F18), Color(hex: 0x561A14)], [0, 0.35, 0.7, 1]))
        var fc = c
        fc.clip(to: face)
        grain(fc, CGRect(x: x, y: y, width: w, height: h), labelGrain, 0.12)
        fc.fill(Path(CGRect(x: x, y: y, width: w, height: h)), with: rad(x + w * 0.2, y, w * 0.9, [.rgba(255, 214, 180, 0.14), .rgba(255, 214, 180, 0)]))
        c.stroke(line(x + 8, y + 0.5, x + w - 8, y + 0.5), with: .color(Color(hex: 0xFFC9B0).opacity(0.3)), lineWidth: 1)
        // A cream double border printed inside the label.
        c.stroke(rr(x + 8, y + 52, w - 16, h - 62, 5), with: .color(cream.opacity(0.55)), lineWidth: 1.2)
        c.stroke(rr(x + 12, y + 56, w - 24, h - 70, 3), with: .color(cream.opacity(0.55)), lineWidth: 0.6)
        // Sandpaper striker: red-brown grit with dark speckle, a pressed edge.
        let sx = g.strikerLeft
        let sw = g.strikerRight - g.strikerLeft
        let sh = g.strikerBottom - g.strikerTop
        let strip = rr(sx, g.strikerTop, sw, sh, 4)
        c.fill(strip, with: lin(0, g.strikerTop, 0, g.strikerBottom, [Color(hex: 0x5E3222), Color(hex: 0x4A2518), Color(hex: 0x3A1C12)]))
        var sc = c
        sc.clip(to: strip)
        let sr = CGRect(x: sx, y: g.strikerTop, width: sw, height: sh)
        grain(sc, sr, gritGrain, 0.55)
        grain(sc, sr, speckle, 0.35, .multiply)
        c.stroke(strip, with: .color(Color(hex: 0x1A0905).opacity(0.6)), lineWidth: 1)
        c.stroke(line(sx + 4, g.strikerBottom + 0.8, sx + sw - 4, g.strikerBottom + 0.8), with: .color(Color(hex: 0xFFC9B0).opacity(0.18)), lineWidth: 1)
        // Printing: the brand line in caps, the hint and the count.
        let caps = Font.system(size: 9, weight: .bold)
        text(c, c.resolve(Text("HEARTH · SAFETY MATCHES").font(caps).foregroundStyle(cream.opacity(0.7))), x: x + w / 2, baseline: y + 72, centered: true)
        text(c, c.resolve(Text("Swipe the match across the strip").font(.system(size: 12)).foregroundStyle(cream.opacity(0.55))), x: x + w / 2, baseline: g.baseline + 30, centered: true)
        text(c, c.resolve(Text("No. 7  ·  40 long matches").font(caps).foregroundStyle(cream.opacity(0.45))), x: x + w / 2, baseline: y + h - 22, centered: true)
    }

    // MARK: Moving parts

    /// One match in its own frame: head at the origin, the stick along +x.
    private static func matchBody(_ c: GraphicsContext, _ m: M.Match, _ len: Double, _ lit: Double) {
        c.fill(rr(2, -3.6, len, 7.2, 2.5), with: lin(0, -3.6, 0, 3.6, [Color(hex: 0xF3E0B6), Color(hex: 0xDEC08A), Color(hex: 0xB98F58)]))
        var fibre = line(14, -1.2, len - 10, -1.4)
        fibre.addPath(line(30, 1.6, len - 30, 1.3))
        c.stroke(fibre, with: .color(Color(hex: 0x9C7444).opacity(0.22)), lineWidth: 0.6)
        // Char creeping down from the head, with a glowing edge while it burns.
        if m.burnt > 0.01 {
            let cl = 6 + 28 * m.char
            var ch = c
            ch.opacity = M.clamp01(m.burnt)
            ch.fill(Path(CGRect(x: 2, y: -3.7, width: cl, height: 7.4)), with: lin(cl - 8, 0, cl, 0, [Color(hex: 0x241710), .rgba(36, 23, 16, 0)]))
            if lit > 0.02 {
                blurred(c, Path(CGRect(x: cl - 4, y: -3.6, width: 3, height: 7.2)), Color(hex: 0xFF8A3A), 0.7 * lit, 1.5)
            }
        }
        // The head: a red bulb going to charcoal when struck, with a highlight.
        c.fill(Path(ellipseIn: CGRect(x: -9, y: -6.2, width: 18, height: 12.4)), with: rad(-3, -2.5, 11, [Color(hex: 0xD2564A), Color(hex: 0xA3291F), Color(hex: 0x6E1712)]))
        if m.burnt > 0.01 {
            var coal = c
            coal.opacity = M.clamp01(m.burnt)
            coal.fill(Path(ellipseIn: CGRect(x: -9.5, y: -6.6, width: 19, height: 13.2)), with: rad(-2, -2, 10, [Color(hex: 0x4A3328), Color(hex: 0x261A14), Color(hex: 0x140E0B)]))
        }
        c.fill(Path(ellipseIn: CGRect(x: -5.5, y: -4.6, width: 6, height: 3)), with: .color(Color(hex: 0xFFE6D6).opacity(0.42 * (1 - M.clamp01(m.burnt)))))
    }

    static func match(_ ctx: GraphicsContext, _ m: M.Match, _ len: Double, _ lit: Double) {
        if m.alpha < 0.005 { return }
        let draw: (GraphicsContext) -> Void = { c in
            // The shadow falls down-screen; held up, it is further, larger and softer.
            var sh = c
            sh.translateBy(x: m.x + 2 + 8 * m.lift, y: m.y + 4 + 18 * m.lift)
            sh.rotate(by: .degrees(m.angle))
            blurred(sh, rr(-8, -5, len + 10, 10, 5), .black, 0.5 - 0.18 * m.lift, 1.8 + 5 * m.lift)
            var b = c
            b.translateBy(x: m.x, y: m.y)
            b.rotate(by: .degrees(m.angle))
            let s = 1 + 0.05 * m.lift
            b.scaleBy(x: s, y: s)
            matchBody(b, m, len, lit)
        }
        if m.alpha < 0.999 {
            var l = ctx
            l.opacity = m.alpha
            l.drawLayer { draw($0) }
        } else {
            draw(ctx)
        }
    }

    static func sparks(_ c: GraphicsContext, _ sparks: [M.Spark]) {
        if sparks.isEmpty { return }
        var young = Path(), old = Path()
        for s in sparks {
            let a = s.age
            let a0 = max(0, a - 0.03)
            let p0 = CGPoint(x: s.x + s.vx * a0, y: s.y + s.vy * a0 + 450 * a0 * a0)
            let p1 = CGPoint(x: s.x + s.vx * a, y: s.y + s.vy * a + 450 * a * a)
            if a / s.life < 0.5 {
                young.move(to: p0)
                young.addLine(to: p1)
            } else {
                old.move(to: p0)
                old.addLine(to: p1)
            }
        }
        var pc = c
        pc.blendMode = .plusLighter
        let sets: [(Path, Color, Double)] = [(young, Color(hex: 0xFFE7A8), 1.6), (old, Color(hex: 0xFF9A45), 1.2)]
        pc.drawLayer { l in
            l.addFilter(.blur(radius: 3))
            for (path, color, width) in sets {
                l.stroke(path, with: .color(color.opacity(0.55)), style: StrokeStyle(lineWidth: width * 3.5, lineCap: .round))
            }
        }
        for (path, color, width) in sets {
            pc.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    static func letters(_ c: GraphicsContext, _ s: Scene, _ heat: [Double]) {
        let y = s.geo.baseline
        let font = Font(ctaFont as CTFont)
        var resolved: [Int: GraphicsContext.ResolvedText] = [:]
        let chars = Array(cta)
        for i in chars.indices where chars[i] != " " {
            resolved[i] = c.resolve(Text(String(chars[i])).font(font))
        }
        // Printed (debossed): a light lower lip, then the dark ink.
        for (i, r) in resolved {
            let h = i < heat.count ? heat[i] : 0
            var lip = r
            lip.shading = .color(Color(hex: 0xFFC7B0).opacity(0.16 * (1 - M.clamp01(h))))
            text(c, lip, x: s.letterX[i], baseline: y + 0.9, centered: false)
        }
        if heat.contains(where: { $0 > 0.01 }) {
            var gc = c
            gc.blendMode = .screen
            gc.drawLayer { l in
                l.addFilter(.blur(radius: 8))
                for (i, r) in resolved {
                    let h = i < heat.count ? heat[i] : 0
                    guard h > 0.01 else { continue }
                    var g = r
                    g.shading = .color(rgb([1, 0.5, 0.18], M.clamp01(0.85 * min(1.2, h))))
                    text(l, g, x: s.letterX[i], baseline: y, centered: false)
                }
            }
        }
        for (i, r) in resolved {
            let h = i < heat.count ? heat[i] : 0
            let k = M.clamp01(h)
            let f = M.clamp01((h - 1) / 0.6)
            let col = (0..<3).map { j in print[j] + (flameGold[j] - print[j]) * k + (flash[j] - flameGold[j]) * f }
            var ink = r
            ink.shading = .color(rgb(col))
            text(c, ink, x: s.letterX[i], baseline: y, centered: false)
        }
    }

    /// The rectangle the fire shader needs this frame (flame and both smoke columns).
    static func fireBounds(_ pose: M.Pose, _ base: CGPoint) -> CGRect {
        var l = base.x - 90, r = base.x + 90, t = base.y - 130, b = base.y + 40
        for sm in [pose.puff, pose.curl] where sm.strength > 0.01 && sm.age >= 0 {
            l = min(l, sm.x - 110)
            r = max(r, sm.x + 110)
            t = min(t, sm.y - 330)
            b = max(b, sm.y + 10)
        }
        return CGRect(x: l, y: t, width: r - l, height: b - t)
    }

    /// Everything between the box and the fire: scrape, letters, the matches, light, sparks.
    static func front(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        let len = s.geo.matchLength
        // A scrape on the strip: darker, polished grit.
        if pose.scrape.2 > 0.005 {
            let x0 = min(pose.scrape.0, pose.scrape.1)
            let x1 = max(pose.scrape.0, pose.scrape.1)
            let g = s.geo
            blurred(c, rr(x0, (g.strikerTop + g.strikerBottom) / 2 - 3, max(4, x1 - x0), 6, 3), Color(hex: 0x140805), pose.scrape.2, 2)
        }
        letters(c, s, pose.heat)
        if pose.spare > 0.005 {
            let g = s.geo
            match(c, M.Match(x: g.rest.x, y: g.rest.y, angle: 0, lift: 0, burnt: 0, char: 0, alpha: pose.spare, front: 1), len, 0)
        }
        if pose.match.front >= 0.5 { match(c, pose.match, len, pose.lit) }
        // Warm light from the flame over everything it reaches.
        if pose.light > 0.01 {
            var lc = c
            lc.blendMode = .screen
            lc.opacity = M.clamp01(pose.light)
            lc.fill(Path(CGRect(x: 0, y: 0, width: s.w, height: s.h)), with: rad(pose.match.x, pose.match.y - 30, 360, [.rgba(255, 150, 70, 0.42), .rgba(255, 120, 50, 0.16), .rgba(255, 110, 40, 0)], [0, 0.4, 1]))
        }
        sparks(c, pose.sparks)
    }

    /// A match on its way into (or out of) the box, under the box.
    static func behind(_ c: GraphicsContext, _ s: Scene, _ pose: M.Pose) {
        guard pose.match.front < 0.5 else { return }
        var bc = c
        bc.clip(to: Path(CGRect(x: 0, y: 0, width: s.w, height: s.boxY + s.boxH - 12)))
        match(bc, pose.match, s.geo.matchLength, pose.lit)
    }
}
