import CoreGraphics
import CoreText
import SwiftUI
import UIKit

/*
 Slot Reel List — the art drawn once, off the main thread, with Core Graphics:
 - the reel strip: the three lists (mains, sides, drinks) printed on cream paper, one column per
   reel, one row per dish; the shader wraps it round the cylinders;
 - the back: the dark diner wall, the cherry-enamel cabinet with its chrome rim, the marquee, the
   reel labels, the lever's hub — one screen-sized image;
 - the front: the window's inner shadows, the glass streak and the brass payline arrows.
*/

enum SlotReelListMenu {
    static let items: [[(name: String, meta: String)]] = [
        [
            ("Miso\nSalmon", "25 MIN"), ("Lemon\nChicken", "40 MIN"), ("Mushroom\nRisotto", "35 MIN"), ("Beef\nTacos", "20 MIN"),
            ("Gnocchi\nPesto", "15 MIN"), ("Green\nCurry", "30 MIN"), ("Shakshuka", "25 MIN"), ("Crispy\nTofu", "20 MIN"),
        ],
        [
            ("Charred\nGreens", "90 KCAL"), ("Garlic\nRice", "210 KCAL"), ("Smashed\nPotatoes", "240 KCAL"), ("Corn\nElote", "180 KCAL"),
            ("Kimchi\nSlaw", "70 KCAL"), ("Herb\nCouscous", "190 KCAL"), ("Roast\nCarrots", "110 KCAL"), ("Miso\nSoup", "60 KCAL"),
        ],
        [
            ("Yuzu\nSpritz", "ZERO PROOF"), ("Iced\nHojicha", "TEA"), ("Hibiscus\nCooler", "ZERO PROOF"), ("Ginger\nBeer", "FIZZ"),
            ("Mint\nLemonade", "FIZZ"), ("Cold\nBrew", "COFFEE"), ("Sparkling\nPlum", "FIZZ"), ("Chai\nLatte", "TEA"),
        ],
    ]
    /// Minutes and kcal of each main and side, for the result card.
    static let minutes = [25, 40, 35, 20, 15, 30, 25, 20]
    static let kcal = [[520, 610, 560, 640, 590, 540, 430, 470], [90, 210, 240, 180, 70, 190, 110, 60], [80, 20, 60, 120, 110, 10, 90, 150]]

    struct Pick: Equatable {
        let key: String
        let main: String
        let line: String
        let minutes: Int
        let kcal: Int
    }

    static func pick(_ rows: [Int]) -> Pick {
        let main = items[0][rows[0]].name.replacingOccurrences(of: "\n", with: " ")
        let side = items[1][rows[1]].name.replacingOccurrences(of: "\n", with: " ").lowercased()
        let drink = items[2][rows[2]].name.replacingOccurrences(of: "\n", with: " ").lowercased()
        let k = kcal[0][rows[0]] + kcal[1][rows[1]] + kcal[2][rows[2]]
        return Pick(
            key: rows.map(String.init).joined(separator: "-"),
            main: main,
            line: "with \(side) and a \(drink)",
            minutes: minutes[rows[0]] + 10,
            kcal: k
        )
    }
}

struct SlotReelListLayout: Equatable {
    let W: Double
    let H: Double
    let top: Double
    let machine: CGRect
    let win: CGRect
    let colW: Double
    let gap: Double
    let rowH: Double
    let radius: Double
    let pivot: CGPoint
    let arm: Double
    let cardY: Double
    /// Text scale for the strip (1 at the 393 × 852 design).
    let k: Double

    /// Each row turns this much of the cylinder (28°).
    static let rowAngle = 28 * Double.pi / 180
    static let frontPad = 14.0

    init(W: Double, H: Double, top: Double, bottom: Double) {
        self.W = W
        self.H = H
        self.top = top
        let headerH = 112.0
        let cardH = 214.0
        let avail = H - top - bottom - headerH - cardH - 36
        let winH = max(170, min(290, avail - 96))
        let mx = 14.0
        let mw = W - mx - 50
        machine = CGRect(x: mx, y: top + headerH, width: mw, height: winH + 96)
        win = CGRect(x: mx + 18, y: machine.minY + 70, width: mw - 36, height: winH)
        gap = 5
        colW = (win.width - gap * 2) / 3
        // The window shows ±72° of the cylinder.
        radius = winH / 2 / sin(72 * Double.pi / 180)
        rowH = radius * Self.rowAngle
        pivot = CGPoint(x: W - 30, y: win.minY + win.height * 0.56)
        arm = winH * 0.42
        cardY = machine.maxY + 16
        k = min(1, rowH / 72)
    }

    var stripSize: CGSize { CGSize(width: colW * 3, height: Double(SlotReelListMotion.rows) * rowH) }
}

struct SlotReelListArt {
    let strip: Image
    let back: Image
    let front: Image

    struct Bitmaps: @unchecked Sendable {
        let px: Double
        let strip: CGImage
        let back: CGImage
        let front: CGImage
    }

    init(_ b: Bitmaps) {
        strip = Image(decorative: b.strip, scale: b.px)
        back = Image(decorative: b.back, scale: b.px)
        front = Image(decorative: b.front, scale: b.px)
    }

    static func bake(_ L: SlotReelListLayout, px: Double) -> Bitmaps {
        Bitmaps(px: px, strip: bakeStrip(L, px: px), back: bakeBack(L, px: px), front: bakeFront(L, px: px))
    }

    // MARK: - Helpers

    static func cg(_ hex: UInt32, _ a: Double = 1) -> CGColor {
        CGColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> CGColor {
        CGColor(srgbRed: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    static func gradient(_ colors: [CGColor], _ pos: [CGFloat]?) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: pos)!
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
        let k = min(r, w / 2, h / 2)
        return CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: k, cornerHeight: k, transform: nil)
    }

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGPath {
        CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil)
    }

    static func fill(_ c: CGContext, _ path: CGPath, _ color: CGColor) {
        c.setFillColor(color)
        c.addPath(path)
        c.fillPath()
    }

    static func fillLinear(_ c: CGContext, _ path: CGPath, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawLinearGradient(gradient(colors, pos), start: p0, end: p1, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        c.restoreGState()
    }

    static func fillRadial(_ c: CGContext, _ path: CGPath, _ center: CGPoint, _ r: Double, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawRadialGradient(gradient(colors, pos), startCenter: center, startRadius: 0, endCenter: center, endRadius: r, options: [.drawsAfterEndLocation])
        c.restoreGState()
    }

    static func strokeLinear(_ c: CGContext, _ path: CGPath, width: Double, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        fillLinear(c, path.copy(strokingWithWidth: width, lineCap: .butt, lineJoin: .miter, miterLimit: 10), p0, p1, colors, pos)
    }

    static func stroke(_ c: CGContext, _ path: CGPath, width: Double, _ color: CGColor) {
        c.setStrokeColor(color)
        c.setLineWidth(width)
        c.addPath(path)
        c.strokePath()
    }

    /// Fills `path` blurred (a Gaussian of `sigma` pt): drawn far off to the left, its shadow falls
    /// back into place. `px` is the context's pixels per point.
    static func blurred(_ c: CGContext, _ path: CGPath, sigma: Double, px: Double, color: CGColor) {
        let far = 40000.0
        c.saveGState()
        c.setShadow(offset: CGSize(width: far, height: 0), blur: CGFloat(sigma * 2 * px), color: color)
        let m = c.ctm.inverted()
        let d = CGPoint(x: -far, y: 0).applying(CGAffineTransform(a: m.a, b: m.b, c: m.c, d: m.d, tx: 0, ty: 0))
        c.translateBy(x: d.x, y: d.y)
        c.setFillColor(CGColor(gray: 0, alpha: 1))
        c.addPath(path)
        c.fillPath()
        c.restoreGState()
    }

    static func render(width: Double, height: Double, px: Double, _ draw: (CGContext) -> Void) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        format.preferredRange = .standard
        let size = CGSize(width: ceil(width * px), height: ceil(height * px))
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            ctx.cgContext.scaleBy(x: px, y: px)
            draw(ctx.cgContext)
        }.cgImage!
    }

    static func font(_ size: Double, _ weight: UIFont.Weight) -> CTFont {
        UIFont.systemFont(ofSize: size, weight: weight) as CTFont
    }

    static func line(_ s: String, _ font: CTFont, _ color: CGColor, track: Double) -> (CTLine, Double) {
        var attrs: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
        ]
        if track != 0 { attrs[NSAttributedString.Key(kCTKernAttributeName as String)] = track }
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: s, attributes: attrs))
        let w = CTLineGetTypographicBounds(line, nil, nil, nil) - (track != 0 ? track : 0)
        return (line, w)
    }

    /// Text centred on cx with its baseline at y (the context is y-down).
    static func centred(_ c: CGContext, _ s: String, _ cx: Double, _ y: Double, _ font: CTFont, _ color: CGColor, track: Double = 0) {
        let (l, w) = line(s, font, color, track: track)
        c.saveGState()
        c.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
        c.textPosition = CGPoint(x: cx - w / 2, y: y)
        CTLineDraw(l, c)
        c.restoreGState()
    }

    static let hash = SlotReelListMotion.hash

    // MARK: - Bakes

    /// The three printed lists, side by side: colW × (rows · rowH) each.
    static func bakeStrip(_ L: SlotReelListLayout, px: Double) -> CGImage {
        let colW = L.colW, rowH = L.rowH, k = L.k
        let name = font(14.5 * k, .bold)
        let meta = font(9.5 * k, .semibold)
        let rows = SlotReelListMotion.rows
        return render(width: colW * 3, height: Double(rows) * rowH, px: px) { c in
            for col in 0..<3 {
                let x = Double(col) * colW
                for r in 0..<rows {
                    let y = Double(r) * rowH
                    let cd = Double(col), rd = Double(r)
                    // Paper: cream, a touch warmer in alternate rows, with fibre grain.
                    fillLinear(c, rect(x, y, colW, rowH), CGPoint(x: 0, y: y), CGPoint(x: 0, y: y + rowH),
                               r % 2 == 1 ? [cg(0xF7EFE2), cg(0xF1E6D4), cg(0xEADCC6)] : [cg(0xFAF4E9), cg(0xF4EBDC), cg(0xECE0CD)], [0, 0.6, 1])
                    c.setFillColor(cg(0x8C6A4A, 0.06))
                    for g in 0..<70 {
                        let gd = Double(g)
                        c.fill(CGRect(x: x + hash(gd + rd * 91 + cd * 7) * colW, y: y + hash(gd * 3.3 + rd * 13 + cd) * rowH, width: 1.2, height: 0.5))
                    }
                    let item = SlotReelListMenu.items[col][r]
                    let lines = item.name.components(separatedBy: "\n")
                    let lh = 16.5 * k
                    let block = Double(lines.count) * lh + 14 * k
                    var ty = y + (rowH - block) / 2 + 12.5 * k
                    for ln in lines {
                        centred(c, ln, x + colW / 2, ty, name, cg(0x2B1A14))
                        ty += lh
                    }
                    centred(c, item.meta, x + colW / 2, ty + 2 * k, meta, cg(0xA0623C), track: 1.1)
                    // The seam between rows: a fine dark line and a light one under it.
                    c.setFillColor(cg(0xB89A78, 0.7))
                    c.fill(CGRect(x: x, y: y, width: colW, height: 0.8))
                    c.setFillColor(cg(0xFFFFFF, 0.6))
                    c.fill(CGRect(x: x, y: y + 0.8, width: colW, height: 0.8))
                }
            }
        }
    }

    /// The wall, the cabinet, its marquee, the reel labels and the lever's hub.
    static func bakeBack(_ L: SlotReelListLayout, px: Double) -> CGImage {
        let W = L.W, H = L.H, m = L.machine, win = L.win
        return render(width: W, height: H, px: px) { c in
            let full = rect(0, 0, W, H)
            // A warm dark wall, a pool of light behind the machine, a vignette, grain.
            fillLinear(c, full, .zero, CGPoint(x: 0, y: H), [cg(0x2A1A13), cg(0x1E140F), cg(0x140D0A)], [0, 0.5, 1])
            fillRadial(c, full, CGPoint(x: W / 2, y: m.minY + m.height * 0.45), W * 0.95,
                       [rgba(214, 120, 70, 0.26), rgba(150, 70, 40, 0.08), rgba(0, 0, 0, 0)], [0, 0.55, 1])
            fillRadial(c, full, CGPoint(x: W / 2, y: H * 0.45), H * 0.75, [rgba(0, 0, 0, 0), rgba(0, 0, 0, 0), rgba(6, 3, 2, 0.55)], [0, 0.6, 1])
            let warm = cg(0xFFE8D0, 0.035)
            let dark = cg(0x000000, 0.035)
            for g in 0..<4200 {
                let gd = Double(g)
                c.setFillColor(hash(gd * 4.1) > 0.5 ? warm : dark)
                c.fill(CGRect(x: hash(gd * 1.3) * W, y: hash(gd * 2.7 + 5) * H, width: 0.8, height: 0.8))
            }

            // The cabinet: two shadows, cherry enamel with three stops, a chrome rim, an edge highlight.
            blurred(c, rr(m.minX, m.minY + 18, m.width, m.height, 32), sigma: 22, px: px, color: cg(0x000000, 0.45))
            blurred(c, rr(m.minX, m.minY + 2, m.width, m.height, 32), sigma: 3, px: px, color: cg(0x000000, 0.5))
            let body = rr(m.minX, m.minY, m.width, m.height, 32)
            fillLinear(c, body, CGPoint(x: m.minX, y: m.minY), CGPoint(x: m.minX + m.width * 0.3, y: m.maxY), [cg(0xD5463A), cg(0xA9232A), cg(0x7A141C)], [0, 0.5, 1])
            fillRadial(c, body, CGPoint(x: m.minX + m.width * 0.3, y: m.minY + 10), m.width * 0.7, [rgba(255, 200, 170, 0.32), rgba(255, 170, 140, 0)])
            strokeLinear(c, rr(m.minX + 1.5, m.minY + 1.5, m.width - 3, m.height - 3, 30.5), width: 3, CGPoint(x: 0, y: m.minY), CGPoint(x: 0, y: m.maxY),
                         [cg(0xFFF6EA), cg(0xC9B9A9), cg(0x8D7B6E), cg(0xE8DCCF)], [0, 0.3, 0.7, 1])
            stroke(c, rr(m.minX + 4, m.minY + 4, m.width - 8, m.height - 8, 28), width: 1, cg(0x3A0A0E, 0.55))

            // Marquee: a cream sign with small warm bulbs and the name.
            let sx = m.minX + 18
            let sy = m.minY + 14
            let sw = m.width - 36
            fillLinear(c, rr(sx, sy, sw, 30, 15), CGPoint(x: 0, y: sy), CGPoint(x: 0, y: sy + 30), [cg(0xFFF4DF), cg(0xF1DDB8), cg(0xDCC092)], [0, 0.6, 1])
            stroke(c, rr(sx, sy, sw, 30, 15), width: 1, cg(0x7A4A22, 0.5))
            for b in 0..<2 {
                let bx = b == 0 ? sx + 16 : sx + sw - 16
                let glowPath = CGPath(ellipseIn: CGRect(x: bx - 9, y: sy + 15 - 9, width: 18, height: 18), transform: nil)
                fillRadial(c, glowPath, CGPoint(x: bx, y: sy + 15), 9, [rgba(255, 214, 140, 0.9), rgba(255, 170, 80, 0)])
                let bulb = CGPath(ellipseIn: CGRect(x: bx - 4, y: sy + 15 - 4, width: 8, height: 8), transform: nil)
                fillRadial(c, bulb, CGPoint(x: bx - 1, y: sy + 14), 4, [cg(0xFFFBEA), cg(0xFFC86A), cg(0xD88A2A)], [0, 0.5, 1])
            }
            centred(c, "SUPPER SLOTS", sx + sw / 2, sy + 20.5, font(13, .heavy), cg(0x7A1C1E), track: 2.2)

            // Labels over the reels.
            let lab = font(10, .bold)
            for (i, s) in ["MAIN", "SIDE", "DRINK"].enumerated() {
                centred(c, s, win.minX + Double(i) * (L.colW + L.gap) + L.colW / 2, win.minY - 9, lab, cg(0xFFE3CF, 0.75), track: 1.6)
            }

            // The window's well, sunk into the enamel.
            let well = rr(win.minX - 6, win.minY - 6, win.width + 12, win.height + 12, 16)
            fillLinear(c, well, CGPoint(x: 0, y: win.minY - 6), CGPoint(x: 0, y: win.maxY + 6), [cg(0x5E0E14), cg(0x2C0709), cg(0x6F1A1E)], [0, 0.5, 1])
            strokeLinear(c, well, width: 2.2, CGPoint(x: 0, y: win.minY), CGPoint(x: 0, y: win.maxY), [cg(0xF6EADB), cg(0x9C8A7C), cg(0xF0E2D2)], [0, 0.5, 1])
            fill(c, rr(win.minX, win.minY, win.width, win.height, 10), cg(0x170A08))

            // Under the window: a small brass plaque.
            let py = win.maxY + 12
            fillLinear(c, rr(m.midX - 54, py, 108, 16, 8), CGPoint(x: 0, y: py), CGPoint(x: 0, y: py + 16), [cg(0xF7D58C), cg(0xC8913C), cg(0x8E5E22)], [0, 0.5, 1])
            centred(c, "PULL TO PICK", m.midX, py + 11.5, font(8.5, .bold), cg(0x4A2A0E), track: 1.4)

            // The lever's hub on the cabinet's flank: a chrome boss with a dark socket.
            let hx = L.pivot.x, hy = L.pivot.y
            blurred(c, rr(hx - 18, hy - 42, 26, 84, 13), sigma: 6, px: px, color: cg(0x000000, 0.45))
            fillLinear(c, rr(hx - 16, hy - 40, 24, 80, 12), CGPoint(x: hx - 16, y: 0), CGPoint(x: hx + 8, y: 0), [cg(0x6A1016), cg(0xB8323A), cg(0x5C0C12)], [0, 0.45, 1])
            blurred(c, CGPath(ellipseIn: CGRect(x: hx - 15, y: hy - 15, width: 30, height: 30), transform: nil), sigma: 3, px: px, color: cg(0x000000, 0.5))
            fillRadial(c, CGPath(ellipseIn: CGRect(x: hx - 13, y: hy - 13, width: 26, height: 26), transform: nil), CGPoint(x: hx - 4, y: hy - 5), 16,
                       [cg(0xFFFFFF), cg(0xC9CED2), cg(0x6B7176), cg(0x2C3034)], [0, 0.3, 0.75, 1])
            fill(c, CGPath(ellipseIn: CGRect(x: hx - 5, y: hy - 5, width: 10, height: 10), transform: nil), cg(0x1A1C1F))
        }
    }

    /// Over the reels: the window's inner shadows, a glass streak, the payline's brass arrows.
    static func bakeFront(_ L: SlotReelListLayout, px: Double) -> CGImage {
        let win = L.win
        let pad = SlotReelListLayout.frontPad
        return render(width: win.width + pad * 2, height: win.height + pad * 2, px: px) { c in
            c.translateBy(x: pad, y: pad)
            let w = win.width, h = win.height
            c.saveGState()
            c.addPath(rr(0, 0, w, h, 10))
            c.clip()
            fillLinear(c, rect(0, 0, w, 26), .zero, CGPoint(x: 0, y: 26), [rgba(20, 6, 4, 0.75), rgba(20, 6, 4, 0)])
            fillLinear(c, rect(0, h - 30, w, 30), CGPoint(x: 0, y: h - 30), CGPoint(x: 0, y: h), [rgba(20, 6, 4, 0), rgba(20, 6, 4, 0.8)])
            // Glass: a soft diagonal streak and a sharp thin one.
            c.saveGState()
            c.translateBy(x: w / 2, y: h / 2)
            c.rotate(by: -24 * .pi / 180)
            c.translateBy(x: -w / 2, y: -h / 2)
            blurred(c, rect(-w, h * 0.12, w * 3, h * 0.16), sigma: 16, px: px, color: cg(0xFFFFFF, 0.09))
            blurred(c, rect(-w, h * 0.33, w * 3, 2.5), sigma: 1.5, px: px, color: cg(0xFFFFFF, 0.12))
            c.restoreGState()
            c.restoreGState()
            // Payline arrows on both sides, pointing in.
            let y = h / 2
            for side in [-1.0, 1.0] {
                let x = side < 0 ? -5 : w + 5
                let tri = CGMutablePath()
                tri.move(to: CGPoint(x: x - side * 7, y: y - 8))
                tri.addLine(to: CGPoint(x: x + side * 4, y: y))
                tri.addLine(to: CGPoint(x: x - side * 7, y: y + 8))
                tri.closeSubpath()
                blurred(c, tri, sigma: 2, px: px, color: cg(0x000000, 0.5))
                fillLinear(c, tri, CGPoint(x: 0, y: y - 8), CGPoint(x: 0, y: y + 8), [cg(0xFFE6A6), cg(0xE0A548), cg(0x9B6522)], [0, 0.5, 1])
            }
        }
    }
}
