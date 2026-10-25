import CoreGraphics
import CoreText
import SwiftUI
import UIKit

/*
 Drawer Cabinet Sheet — layout, the items, and the art drawn once (off the main thread):
 - the desk: warm paper, a green felt mat with stitching, a ruler, a to-do card, a succulent, a
   notebook and a mug;
 - the drawer (the sheet): an oak front with a brass bar pull, and behind it the box — birch floor,
   oak walls, walnut dividers, a navy felt pen tray, inner shadows — in one image;
 - the cabinet's top board the drawer slides out from under;
 - the supplies (pens, a marker, a pencil, paper clips, binder clips, an eraser, tape, sticky
   notes), one image each, and their soft shadows.
*/

enum DrawerCabinetSheetKind { case pen, marker, pencil, clip, binder, eraser, tape, notes }

struct DrawerCabinetSheetItem {
    let kind: DrawerCabinetSheetKind
    let name: String
    let w: Double
    let h: Double
    let x: Double
    let y: Double
    let rot: Double
    let color: UInt32
    let spring: DrawerCabinetSheetSpring
}

struct DrawerCabinetSheetLayout: Equatable {
    /// Front panel depth, interior depth, wall width, room above the sheet for its shadow (pt).
    static let panel = 60.0
    static let depth = 384.0
    static let wall = 10.0
    static let shadowPad = 30.0

    let W: Double
    let H: Double
    let top: Double
    let boardY: Double
    let half: Double
    let full: Double
    let sheetX: Double
    let sheetW: Double
    /// The interior's left edge on screen and its width.
    let ix: Double
    let iw: Double
    let matY: Double

    init(W: Double, H: Double, top: Double, bottom: Double) {
        self.W = W
        self.H = H
        self.top = top
        boardY = H - bottom - 64
        full = max(220, min(Self.depth, boardY - Self.panel - (top + 236)))
        half = (full * 0.6).rounded()
        sheetX = 12
        sheetW = W - 24
        ix = sheetX + Self.wall
        iw = sheetW - Self.wall * 2
        matY = top + 104
    }

    var sheetH: Double { Self.shadowPad + Self.panel + Self.depth }
}

enum DrawerCabinetSheetArt {
    private static let penSpring = DrawerCabinetSheetSpring(slack: 9, bounce: 0.3, w: 14, z: 0.35, turn: 0.04)
    private static let clipSpring = DrawerCabinetSheetSpring(slack: 14, bounce: 0.5, w: 10, z: 0.25, turn: 0.25)
    private static let binderSpring = DrawerCabinetSheetSpring(slack: 10, bounce: 0.35, w: 12, z: 0.3, turn: 0.15)
    private static let heavySpring = DrawerCabinetSheetSpring(slack: 7, bounce: 0.15, w: 17, z: 0.55, turn: 0.04)
    private static let tapeSpring = DrawerCabinetSheetSpring(slack: 12, bounce: 0.25, w: 11, z: 0.3, turn: 0.03)

    /// The supplies at their places, in interior coordinates (x from the left wall, y from the front panel).
    static func items(_ Wi: Double) -> [DrawerCabinetSheetItem] {
        let pw = min(250, Wi - 70)
        let cx = Wi / 2
        let L = Wi * 0.52
        let clipSpecs: [(Double, Double, Double, UInt32)] = [
            (0.18, 152, 0.4, 0xB8BEC4), (0.42, 146, -0.7, 0xF07C67), (0.28, 182, 1.2, 0xB8BEC4),
            (0.62, 176, 0.1, 0x6CC4A8), (0.2, 218, -0.3, 0xF2C14E), (0.55, 222, 0.9, 0xB8BEC4),
        ]
        let clips = clipSpecs.map {
            DrawerCabinetSheetItem(kind: .clip, name: "Paper clip", w: 30, h: 11, x: L * $0.0, y: $0.1, rot: $0.2, color: $0.3, spring: clipSpring)
        }
        var all: [DrawerCabinetSheetItem] = [
            .init(kind: .pen, name: "Blue fineliner", w: pw, h: 15, x: cx - 6, y: 30, rot: -0.02, color: 0x2F5BD8, spring: penSpring),
            .init(kind: .pen, name: "Black gel pen", w: pw - 14, h: 15, x: cx + 4, y: 60, rot: 0.025, color: 0x22252B, spring: penSpring),
            .init(kind: .marker, name: "Coral marker", w: pw - 40, h: 19, x: cx - 10, y: 91, rot: -0.015, color: 0xE8573F, spring: penSpring),
        ]
        all += clips
        all += [
            .init(kind: .eraser, name: "Eraser", w: 58, h: 26, x: Wi * 0.66, y: 156, rot: 0.18, color: 0xF3A6B2, spring: heavySpring),
            .init(kind: .binder, name: "Binder clip", w: 28, h: 24, x: Wi * 0.87, y: 160, rot: -0.35, color: 0x2A2C31, spring: binderSpring),
            .init(kind: .binder, name: "Binder clip", w: 28, h: 24, x: Wi * 0.9, y: 204, rot: 0.5, color: 0x2A2C31, spring: binderSpring),
            .init(kind: .pencil, name: "Pencil", w: min(124, Wi * 0.4), h: 11, x: Wi * 0.71, y: 222, rot: -0.06, color: 0xF2B632, spring: penSpring),
            .init(kind: .tape, name: "Tape", w: 76, h: 76, x: Wi * 0.25, y: 315, rot: 0, color: 0xE9C9A0, spring: tapeSpring),
            .init(kind: .notes, name: "Sticky notes", w: 88, h: 88, x: Wi * 0.7, y: 314, rot: 0.07, color: 0xF9DC6A, spring: heavySpring),
        ]
        return all
    }

    /// Sprites carry this much room round each item for its shadow's blur.
    static let pad = 12.0

    struct Bitmaps: @unchecked Sendable {
        let px: Double
        let desk: CGImage
        let sheet: CGImage
        let board: CGImage
        let faces: [CGImage]
        let shadows: [CGImage]
    }

    static func bake(_ L: DrawerCabinetSheetLayout, _ items: [DrawerCabinetSheetItem], px: Double) -> Bitmaps {
        Bitmaps(
            px: px,
            desk: bakeDesk(L, px: px),
            sheet: bakeSheet(L, px: px),
            board: bakeBoard(L, px: px),
            faces: items.map { it in render(it.w + pad * 2, it.h + pad * 2, px, opaque: false) { drawItem($0, it, pad, pad) } },
            shadows: items.map { it in
                render(it.w + pad * 2, it.h + pad * 2, px, opaque: false) { c in
                    let r = it.kind == .tape ? it.w / 2 : min(it.h / 2, 6)
                    blurred(c, rr(pad, pad, it.w, it.h, r), sigma: it.kind == .clip ? 1.5 : 3, px: px, color: cg(0x2A1806, it.kind == .clip ? 0.3 : 0.42))
                }
            }
        )
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

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGPath {
        CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil)
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> CGPath {
        CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), transform: nil)
    }

    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
        let k = max(0, min(r, w / 2, h / 2))
        return CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: k, cornerHeight: k, transform: nil)
    }

    static func fill(_ c: CGContext, _ p: CGPath, _ color: CGColor) {
        c.setFillColor(color)
        c.addPath(p)
        c.fillPath()
    }

    static func stroke(_ c: CGContext, _ p: CGPath, _ width: Double, _ color: CGColor, round: Bool = true) {
        c.saveGState()
        c.setStrokeColor(color)
        c.setLineWidth(width)
        c.setLineCap(round ? .round : .butt)
        c.addPath(p)
        c.strokePath()
        c.restoreGState()
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

    static func strokeLinear(_ c: CGContext, _ path: CGPath, _ width: Double, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        fillLinear(c, path.copy(strokingWithWidth: width, lineCap: .round, lineJoin: .round, miterLimit: 10), p0, p1, colors, pos)
    }

    /// Fills `path` blurred (a Gaussian of `sigma` pt): drawn far off to the left, its shadow falls back.
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

    static func render(_ w: Double, _ h: Double, _ px: Double, opaque: Bool, _ draw: (CGContext) -> Void) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = opaque
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: ceil(w * px), height: ceil(h * px)), format: format).image { ctx in
            ctx.cgContext.scaleBy(x: px, y: px)
            draw(ctx.cgContext)
        }.cgImage!
    }

    static func text(_ c: CGContext, _ s: String, _ x: Double, _ y: Double, _ font: CTFont, _ color: CGColor) {
        let attrs: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: s, attributes: attrs))
        c.saveGState()
        c.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
        c.textPosition = CGPoint(x: x, y: y)
        CTLineDraw(line, c)
        c.restoreGState()
    }

    static let hash: (Double) -> Double = { n in
        let v = sin(n * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    static func rotate(_ c: CGContext, degrees: Double, about x: Double, _ y: Double) {
        c.translateBy(x: x, y: y)
        c.rotate(by: degrees * .pi / 180)
        c.translateBy(x: -x, y: -y)
    }

    /// Wood: a three-stop base and long, slightly wavy grain lines.
    static func wood(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ base: [UInt32], _ grain: UInt32, _ seed: Double, alongX: Bool = true) {
        let colors = base.map { cg($0) }
        if alongX {
            fillLinear(c, rect(x, y, w, h), CGPoint(x: 0, y: y), CGPoint(x: 0, y: y + h), colors, [0, 0.5, 1])
        } else {
            fillLinear(c, rect(x, y, w, h), CGPoint(x: x, y: 0), CGPoint(x: x + w, y: 0), colors, [0, 0.5, 1])
        }
        let n = Int(((alongX ? h : w) / 2.2).rounded())
        let len = alongX ? w : h
        for i in 0..<n {
            let id = Double(i)
            let p = CGMutablePath()
            let o = (id + hash(id + seed) * 0.8) * 2.2
            let amp = 0.6 + hash(id * 3 + seed) * 1.6
            var s = 0.0
            while s <= len {
                let d = o + sin(s * 0.02 + id * 0.7 + seed) * amp
                let pt = alongX ? CGPoint(x: x + s, y: y + d) : CGPoint(x: x + d, y: y + s)
                if s == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                s += 12
            }
            stroke(c, p, 0.6, cg(grain, 0.05 + hash(id * 7 + seed) * 0.12))
        }
    }

    // MARK: - Desk

    static func bakeDesk(_ L: DrawerCabinetSheetLayout, px: Double) -> CGImage {
        let W = L.W, H = L.H, matY = L.matY
        return render(W, H, px, opaque: true) { c in
            fillLinear(c, rect(0, 0, W, H), .zero, CGPoint(x: 0, y: H), [cg(0xF3ECE1), cg(0xEDE3D4), cg(0xE4D7C4)], [0, 0.5, 1])
            let speckL = cg(0xFFFFFF, 0.05), speckD = cg(0x6B5238, 0.05)
            for i in 0..<4000 {
                let id = Double(i)
                c.setFillColor(hash(id * 5.1) > 0.5 ? speckL : speckD)
                c.fill(CGRect(x: hash(id * 1.3) * W, y: hash(id * 2.9) * H, width: 0.7, height: 0.7))
            }
            let mx = 16.0
            let mw = W - 32
            let mh = L.boardY - DrawerCabinetSheetLayout.panel - 14 - matY
            blurred(c, rr(mx, matY + 10, mw, mh, 22), sigma: 14, px: px, color: cg(0x3B2A18, 0.22))
            blurred(c, rr(mx, matY + 1, mw, mh, 22), sigma: 1.5, px: px, color: cg(0x3B2A18, 0.3))
            fillLinear(c, rr(mx, matY, mw, mh, 22), CGPoint(x: 0, y: matY), CGPoint(x: 0, y: matY + mh), [cg(0x3E6152), cg(0x2F5042), cg(0x27443A)], [0, 0.5, 1])
            let feltL = cg(0x9FC2B1, 0.09), feltD = cg(0x13261F, 0.09)
            for i in 0..<9000 {
                let id = Double(i)
                c.setFillColor(hash(id * 3.3) > 0.5 ? feltL : feltD)
                c.fill(CGRect(x: mx + hash(id * 1.7) * mw, y: matY + hash(id * 2.3) * mh, width: 0.8, height: 0.8))
            }
            c.saveGState()
            c.setLineDash(phase: 0, lengths: [5, 4])
            stroke(c, rr(mx + 8, matY + 8, mw - 16, mh - 16, 16), 1, cg(0xB9D3C4, 0.5))
            c.restoreGState()
            let label = UIFont.systemFont(ofSize: 10, weight: .bold) as CTFont
            text(c, "DESK", mx + 20, matY + 28, label, cg(0xCFE3D8, 0.55))
            // A clear ruler along the top, a to-do card, a succulent, a notebook and a mug: the desk is in use.
            let ry = matY + 36
            blurred(c, rr(mx + 40, ry + 4, 210, 26, 4), sigma: 4, px: px, color: cg(0x0B1712, 0.25))
            fill(c, rr(mx + 40, ry, 210, 26, 4), cg(0xDDEFF2, 0.32))
            stroke(c, rr(mx + 40, ry, 210, 26, 4), 0.8, cg(0xFFFFFF, 0.55))
            let ticks = CGMutablePath()
            for i in 0...40 {
                ticks.move(to: CGPoint(x: mx + 46 + Double(i) * 5, y: ry))
                ticks.addLine(to: CGPoint(x: mx + 46 + Double(i) * 5, y: ry + (i % 5 == 0 ? 9 : 5)))
            }
            stroke(c, ticks, 0.6, cg(0x14261F, 0.55))
            let cy0 = matY + 196
            c.saveGState()
            rotate(c, degrees: 3, about: mx + 100, cy0 + 46)
            blurred(c, rect(mx + 32, cy0 + 6, 136, 94), sigma: 6, px: px, color: cg(0x0B1712, 0.35))
            fillLinear(c, rect(mx + 30, cy0, 136, 94), CGPoint(x: 0, y: cy0), CGPoint(x: 0, y: cy0 + 94), [cg(0xFBF7EE), cg(0xF3ECDD)])
            fill(c, rect(mx + 30, cy0 + 22, 136, 1), cg(0xE07A6A, 0.6))
            text(c, "TODAY", mx + 40, cy0 + 16, label, cg(0x7A6A58))
            for i in 0..<3 {
                let id = Double(i)
                let ly = cy0 + 40 + id * 18
                stroke(c, rect(mx + 40, ly - 6, 8, 8), 1, cg(0x5A6E9A, 0.8))
                let p = CGMutablePath()
                p.move(to: CGPoint(x: mx + 56, y: ly))
                p.addQuadCurve(to: CGPoint(x: mx + 104 + id * 10, y: ly), control: CGPoint(x: mx + 80 + id * 6, y: ly - 4))
                p.addQuadCurve(to: CGPoint(x: mx + 136 - id * 8, y: ly - 1), control: CGPoint(x: mx + 120, y: ly + 2))
                stroke(c, p, 1.2, cg(0x3B5BA8, 0.7))
            }
            c.restoreGState()
            let px0 = W - 74
            let py0 = cy0 + 40
            blurred(c, circle(px0 + 4, py0 + 7, 32), sigma: 8, px: px, color: cg(0x0B1712, 0.4))
            fillLinear(c, circle(px0, py0, 30), CGPoint(x: px0 - 30, y: 0), CGPoint(x: px0 + 30, y: 0), [cg(0xB9643C), cg(0xD98457), cg(0x9A4E2C)], [0, 0.4, 1])
            fill(c, circle(px0, py0, 25), cg(0x4A3426))
            for i in 0..<9 {
                let a = Double(i) / 9 * Double.pi * 2
                let lx = px0 + cos(a) * 11
                let ly = py0 + sin(a) * 11
                c.saveGState()
                rotate(c, degrees: a * 180 / .pi, about: lx, ly)
                fillLinear(c, CGPath(ellipseIn: CGRect(x: lx - 11, y: ly - 5.5, width: 22, height: 11), transform: nil),
                           CGPoint(x: lx - 11, y: 0), CGPoint(x: lx + 11, y: 0), [cg(0x5E9C6E), cg(0x8CCB94), cg(0x4C8A5C)], [0, 0.6, 1])
                c.restoreGState()
            }
            fillRadial(c, circle(px0, py0, 6), CGPoint(x: px0, y: py0), 6, [cg(0xB6E3B4), cg(0x6FAF7A)])
            let nx = mx + 26
            let ny = matY + mh - 236
            c.saveGState()
            rotate(c, degrees: -4, about: nx + 80, ny + 100)
            blurred(c, rr(nx + 4, ny + 10, 164, 214, 10), sigma: 10, px: px, color: cg(0x0B1712, 0.45))
            fill(c, rr(nx + 2, ny + 3, 164, 212, 9), cg(0xF4EEE2))
            fillLinear(c, rr(nx, ny, 164, 212, 9), CGPoint(x: nx, y: ny), CGPoint(x: nx + 164, y: ny + 212), [cg(0x2D3E63), cg(0x22314F), cg(0x1A2640)], [0, 0.5, 1])
            fillLinear(c, rect(nx + 132, ny, 8, 212), CGPoint(x: nx + 132, y: 0), CGPoint(x: nx + 140, y: 0), [cg(0x9B2F2A), cg(0xC9453C), cg(0x8A2925)], [0, 0.5, 1])
            text(c, "IDEAS · 2026", nx + 16, ny + 32, label, cg(0xD9C9A3, 0.8))
            c.restoreGState()
            let gx = W - 92
            let gy = ny + 70
            blurred(c, circle(gx + 4, gy + 8, 36), sigma: 9, px: px, color: cg(0x0B1712, 0.4))
            fillLinear(c, circle(gx, gy, 34), CGPoint(x: gx - 34, y: 0), CGPoint(x: gx + 34, y: 0), [cg(0xE9E4DA), cg(0xFFFFFF), cg(0xCFC7B9)], [0, 0.4, 1])
            fillRadial(c, circle(gx, gy, 27), CGPoint(x: gx - 6, y: gy - 6), 30, [cg(0x7A4A2A), cg(0x4B2A16), cg(0x2E190D)], [0, 0.6, 1])
            blurred(c, CGPath(ellipseIn: CGRect(x: gx - 14, y: gy - 16, width: 14, height: 7), transform: nil), sigma: 2, px: px, color: cg(0xFFFFFF, 0.35))
            stroke(c, rr(gx + 32, gy - 6, 18, 12, 6), 5, cg(0xE2DBCF))
        }
    }

    // MARK: - Drawer

    /// The drawer: front panel and box, with room above for its shadow on the desk.
    static func bakeSheet(_ L: DrawerCabinetSheetLayout, px: Double) -> CGImage {
        let w = L.sheetW
        let P = DrawerCabinetSheetLayout.panel, D = DrawerCabinetSheetLayout.depth, WALL = DrawerCabinetSheetLayout.wall
        let y0 = DrawerCabinetSheetLayout.shadowPad
        return render(w, L.sheetH, px, opaque: false) { c in
            blurred(c, rr(4, y0 - 10, w - 8, P + 30, 14), sigma: 12, px: px, color: cg(0x3B2A18, 0.3))
            // The box: walls, birch floor, the navy felt pen tray, walnut dividers, inner shadows.
            let iy = y0 + P
            let iw = w - WALL * 2
            wood(c, 0, iy, w, D, [0xA87B4D, 0x94693F, 0x7E5733], 0x3E2612, 3)
            wood(c, WALL, iy, iw, D, [0xEAD7B6, 0xE2CCA6, 0xD8BF95], 0x9A7448, 7)
            fillLinear(c, rr(WALL + 6, iy + 4, iw - 12, 110, 8), CGPoint(x: 0, y: iy), CGPoint(x: 0, y: iy + 114), [cg(0x2B3452), cg(0x323C5E), cg(0x283050)], [0, 0.5, 1])
            let fL = cg(0x8C97C2, 0.12), fD = cg(0x141A2E, 0.12)
            for i in 0..<2200 {
                let id = Double(i)
                c.setFillColor(hash(id * 3.9) > 0.5 ? fL : fD)
                c.fill(CGRect(x: WALL + 6 + hash(id * 1.1) * (iw - 12), y: iy + 4 + hash(id * 2.7) * 110, width: 0.8, height: 0.8))
            }
            func div(_ x: Double, _ y: Double, _ dw: Double, _ dh: Double) {
                blurred(c, rect(x, y + 3, dw, dh), sigma: 3, px: px, color: cg(0x3A240F, 0.35))
                let cols = [cg(0x8A5A33), cg(0x6B4426), cg(0x4E301A)]
                if dw > dh {
                    fillLinear(c, rect(x, y, dw, dh), CGPoint(x: 0, y: y), CGPoint(x: 0, y: y + dh), cols, [0, 0.4, 1])
                } else {
                    fillLinear(c, rect(x, y, dw, dh), CGPoint(x: x, y: 0), CGPoint(x: x + dw, y: 0), cols, [0, 0.4, 1])
                }
                fill(c, rect(x, y, dw, 1), cg(0xC79A6A, 0.7))
            }
            div(WALL, iy + 118, iw, 10)
            div(WALL, iy + 246, iw, 10)
            div(WALL + iw * 0.52, iy + 128, 10, 118)
            div(WALL + iw * 0.5 + 6, iy + 256, 10, 118)
            div(0, iy + D - 10, w, 10)
            // Shade along the walls and under the front panel's lip.
            fillLinear(c, rect(WALL, iy, 14, D), CGPoint(x: WALL, y: 0), CGPoint(x: WALL + 14, y: 0), [rgba(60, 36, 14, 0.35), rgba(60, 36, 14, 0)])
            fillLinear(c, rect(w - WALL - 14, iy, 14, D), CGPoint(x: w - WALL - 14, y: 0), CGPoint(x: w - WALL, y: 0), [rgba(60, 36, 14, 0), rgba(60, 36, 14, 0.35)])
            fillLinear(c, rect(0, iy, w, 22), CGPoint(x: 0, y: iy), CGPoint(x: 0, y: iy + 22), [rgba(40, 24, 10, 0.45), rgba(40, 24, 10, 0)])
            // The front: an oak plank with a bevelled top edge, a brass label holder and the bar pull.
            c.saveGState()
            c.addPath(rr(0, y0, w, P, 12))
            c.clip()
            wood(c, 0, y0, w, P, [0xC48F5A, 0xB07B48, 0x93633A], 0x4A2C12, 11)
            c.restoreGState()
            stroke(c, rr(0.5, y0 + 0.5, w - 1, P - 1, 12), 1, cg(0xF1C894, 0.5))
            fill(c, rect(8, y0 + P - 3, w - 16, 3), cg(0x5A3618, 0.35))
            let cx = w / 2
            let py = y0 + P / 2
            blurred(c, rr(cx - 66, py + 3, 132, 16, 8), sigma: 4, px: px, color: cg(0x3A2108, 0.5))
            for sx in [cx - 56, cx + 56] {
                fillRadial(c, circle(sx, py, 6.5), CGPoint(x: sx - 2, y: py - 2), 8, [cg(0xFFF0C2), cg(0xD7A85A), cg(0x8A5E22)], [0, 0.5, 1])
            }
            fillLinear(c, rr(cx - 64, py - 6, 128, 12, 6), CGPoint(x: 0, y: py - 6), CGPoint(x: 0, y: py + 6), [cg(0xFBE3A2), cg(0xE0B25E), cg(0xB4843A), cg(0x7E561C)], [0, 0.35, 0.7, 1])
            fill(c, rr(cx - 58, py - 4.5, 116, 2, 1), cg(0xFFFFFF, 0.55))
            let lx = 26.0
            fillLinear(c, rr(lx, py - 11, 70, 22, 4), CGPoint(x: 0, y: py - 11), CGPoint(x: 0, y: py + 11), [cg(0xF2D08A), cg(0xB8893E)])
            fill(c, rect(lx + 3, py - 8, 64, 16), cg(0xF7F1E4))
            let mono = (UIFont(name: "Menlo-Bold", size: 8) ?? UIFont.monospacedSystemFont(ofSize: 8, weight: .bold)) as CTFont
            text(c, "SUPPLIES", lx + 13, py + 3, mono, cg(0x4A3A2A))
        }
    }

    /// The cabinet's top board, from its edge to the bottom of the screen.
    static func bakeBoard(_ L: DrawerCabinetSheetLayout, px: Double) -> CGImage {
        let w = L.W
        let h = L.H - L.boardY + 20
        return render(w, h, px, opaque: false) { c in
            fillLinear(c, rect(0, 0, w, 20), .zero, CGPoint(x: 0, y: 20), [rgba(40, 22, 8, 0), rgba(40, 22, 8, 0.28)])
            wood(c, 0, 20, w, h - 20, [0xB98552, 0xA47142, 0x8A5C34], 0x4A2C12, 23)
            fill(c, rect(0, 20, w, 2.5), cg(0x2A1708))
            fill(c, rect(0, 22.5, w, 1.2), cg(0xF3CF9A, 0.8))
            fillLinear(c, rect(0, 23.7, w, 10), CGPoint(x: 0, y: 23.7), CGPoint(x: 0, y: 34), [rgba(255, 226, 180, 0.25), rgba(255, 226, 180, 0)])
        }
    }

    // MARK: - Supplies

    static func drawItem(_ c: CGContext, _ it: DrawerCabinetSheetItem, _ x: Double, _ y: Double) {
        let w = it.w, h = it.h
        func tri(_ a: (Double, Double), _ b: (Double, Double), _ d: (Double, Double)) -> CGPath {
            let p = CGMutablePath()
            p.move(to: CGPoint(x: a.0, y: a.1)); p.addLine(to: CGPoint(x: b.0, y: b.1)); p.addLine(to: CGPoint(x: d.0, y: d.1)); p.closeSubpath()
            return p
        }
        let top = CGPoint(x: 0, y: y)
        let bottom = CGPoint(x: 0, y: y + h)
        switch it.kind {
        case .pen, .marker:
            let cap = it.kind == .marker ? w * 0.3 : w * 0.26
            fillLinear(c, rr(x, y, w, h, h / 2), top, bottom, [cg(0xFFFFFF), cg(0xE9E6E0), cg(0xBDB7AD)], [0, 0.35, 1])
            fillLinear(c, rr(x + w - cap, y - 0.5, cap, h + 1, h / 2), top, bottom, [cg(it.color), cg(it.color), cg(0x0E0E14)], [0, 0.45, 1])
            fill(c, rect(x + w - cap - 10, y + h * 0.18, 26, 2.4), cg(0xC9CED4))
            fill(c, rect(x + w - cap * 0.8, y + h * 0.5 - 1.2, cap * 0.75, 2.4), cg(0xFFFFFF, 0.25))
            fillLinear(c, tri((x + 2, y + h / 2), (x + 14, y + 1.5), (x + 14, y + h - 1.5)), top, bottom, [cg(0x5A5A60), cg(0x2A2A30)])
            fill(c, rect(x + 14, y + h * 0.5 - 0.8, w * 0.5, 1.6), cg(it.color, 0.55))
            fill(c, rect(x + h / 2, y + 2.5, w - h, 2), cg(0xFFFFFF, 0.7))
        case .pencil:
            fillLinear(c, rect(x + 14, y, w - 30, h), top, bottom, [cg(0xFFD66E), cg(0xF2B632), cg(0xC98A16)], [0, 0.5, 1])
            fill(c, rect(x + 14, y + h / 3, w - 30, 0.8), cg(0x9A6A10, 0.5))
            fill(c, tri((x, y + h / 2), (x + 14, y), (x + 14, y + h)), cg(0xE8C9A0))
            fill(c, tri((x, y + h / 2), (x + 5, y + h * 0.32), (x + 5, y + h * 0.68)), cg(0x3A3A40))
            fillLinear(c, rect(x + w - 16, y, 7, h), top, bottom, [cg(0xE6E8EA), cg(0xA8ADB2)])
            fillLinear(c, rr(x + w - 10, y, 10, h, 3), top, bottom, [cg(0xF7B5BE), cg(0xD97E8C)])
        case .clip:
            let p = CGMutablePath()
            let r1 = h / 2
            p.move(to: CGPoint(x: x + w - 6, y: y + h - 2.5))
            p.addLine(to: CGPoint(x: x + r1, y: y + h - 2.5))
            p.addArc(tangent1End: CGPoint(x: x + 1, y: y + h - 2.5), tangent2End: CGPoint(x: x + 1, y: y + h / 2), radius: r1 - 1.5)
            p.addArc(tangent1End: CGPoint(x: x + 1, y: y + 1), tangent2End: CGPoint(x: x + r1, y: y + 1), radius: r1 - 1.5)
            p.addLine(to: CGPoint(x: x + w - r1, y: y + 1))
            p.addArc(tangent1End: CGPoint(x: x + w - 1, y: y + 1), tangent2End: CGPoint(x: x + w - 1, y: y + h / 2), radius: r1 - 1.5)
            p.addArc(tangent1End: CGPoint(x: x + w - 1, y: y + h - 1), tangent2End: CGPoint(x: x + w - r1, y: y + h - 1), radius: r1 - 1.5)
            p.addLine(to: CGPoint(x: x + 7, y: y + h - 1))
            strokeLinear(c, p, 1.6, top, bottom, [cg(0xFFFFFF), cg(it.color), cg(0x5E646A)], [0, 0.45, 1])
        case .binder:
            let body = CGMutablePath()
            body.move(to: CGPoint(x: x + 2, y: y + h)); body.addLine(to: CGPoint(x: x + w - 2, y: y + h))
            body.addLine(to: CGPoint(x: x + w - 6, y: y + h * 0.45)); body.addLine(to: CGPoint(x: x + 6, y: y + h * 0.45)); body.closeSubpath()
            fillLinear(c, body, CGPoint(x: 0, y: y + h * 0.45), bottom, [cg(0x4A4C52), cg(0x1E2024)])
            fill(c, rect(x + 6, y + h * 0.45, w - 12, 1), cg(0x8A8E96))
            let wire = CGMutablePath()
            wire.move(to: CGPoint(x: x + 8, y: y + h * 0.5)); wire.addLine(to: CGPoint(x: x + 6, y: y + 1))
            wire.addLine(to: CGPoint(x: x + w - 6, y: y + 1)); wire.addLine(to: CGPoint(x: x + w - 8, y: y + h * 0.5))
            stroke(c, wire, 1.6, cg(0xD6DADE))
        case .eraser:
            fillLinear(c, rr(x, y, w, h, 5), top, bottom, [cg(0xFFD6DC), cg(it.color), cg(0xD9808E)], [0, 0.5, 1])
            fillLinear(c, rect(x + w * 0.38, y, w * 0.62, h), top, bottom, [cg(0xFFFFFF), cg(0xE8EEF4), cg(0xB9C4CF)], [0, 0.5, 1])
            fill(c, rect(x + w * 0.38, y, 1.2, h), cg(0x8E9AA6, 0.6))
            text(c, "SOFT", x + w * 0.5, y + h / 2 + 3, UIFont.systemFont(ofSize: 8, weight: .bold) as CTFont, cg(0x2F5BD8))
        case .tape:
            let cx = x + w / 2
            let cy = y + h / 2
            fillRadial(c, circle(cx, cy, w / 2), CGPoint(x: cx - 8, y: cy - 8), w * 0.6, [cg(0xFBE8CC), cg(0xE9C9A0), cg(0xC69A68)], [0, 0.6, 1])
            for i in 0..<9 {
                stroke(c, circle(cx, cy, w / 2 - 3 - Double(i) * 2.2), 0.6, cg(0xFFFFFF, 0.08 + Double(i % 2) * 0.06))
            }
            fillLinear(c, circle(cx, cy, w * 0.3), CGPoint(x: cx - 20, y: 0), CGPoint(x: cx + 20, y: 0), [cg(0xB48A5C), cg(0xD7B184), cg(0xA47B4E)], [0, 0.5, 1])
            fill(c, circle(cx, cy, w * 0.25), cg(0xE2CCA6))
            stroke(c, circle(cx, cy, w * 0.25), 1.2, cg(0x6E4C2A, 0.35))
        case .notes:
            for k in stride(from: 3, through: 1, by: -1) {
                fill(c, rect(x + Double(k) * 0.8, y + Double(k) * 1.2, w, h), cg(k % 2 == 1 ? 0xE3C24E : 0xEFD25E))
            }
            fillLinear(c, rect(x, y, w, h), CGPoint(x: x, y: y), CGPoint(x: x + w, y: y + h), [cg(0xFFEB8A), cg(it.color), cg(0xEFCB4A)], [0, 0.5, 1])
            let a = CGMutablePath()
            a.move(to: CGPoint(x: x + 12, y: y + 26))
            a.addQuadCurve(to: CGPoint(x: x + 44, y: y + 26), control: CGPoint(x: x + 30, y: y + 18))
            a.addQuadCurve(to: CGPoint(x: x + 72, y: y + 24), control: CGPoint(x: x + 58, y: y + 32))
            stroke(c, a, 1.3, cg(0x3B5BA8, 0.75))
            let b = CGMutablePath()
            b.move(to: CGPoint(x: x + 12, y: y + 44))
            b.addQuadCurve(to: CGPoint(x: x + 40, y: y + 45), control: CGPoint(x: x + 26, y: y + 38))
            stroke(c, b, 1.3, cg(0x3B5BA8, 0.75))
            fill(c, tri((x + w - 18, y + h), (x + w, y + h - 18), (x + w, y + h)), cg(0xC9A63A, 0.6))
        }
    }
}
