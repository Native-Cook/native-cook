import CoreText
import SwiftUI
import UIKit

/// Drawing for Cheese Drip Burger: the red poster, the word stack, the steam and the molten cheese.
/// The cheese is drawn analytically: every drip is one closed path (column → neck → bulb), and the
/// band, the drips, the drops and the puddle of a slice are one path filled once, so they read as
/// one goo. Burger space is the 372 pt square the photo is drawn in.
enum CheeseDripBurgerArt {
    typealias M = CheeseDripBurgerMotion

    // MARK: Palette

    static let red = 0xAF0002
    static let redGlow = 0xC70D08
    static let redDeep = 0x6E0002
    static let rowFaint = 0xBC0805
    static let yellow = 0xF9C93C
    static let cream = 0xFFF2D6
    static let sand = 0xFFE9C2

    static let cheeseStops: [(Double, Int)] = [(0, 0xF0A010), (0.22, 0xFFB71A), (0.6, 0xFFCB30), (1, 0xFFDA5C)]
    static let rim = 0xC86A04
    static let shade_ = 0xD9730A
    static let shadow = 0x3B1200
    static let gloss = 0xFFFBEA

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: alpha)
    }

    static func mix(_ a: Int, _ b: Int, _ f: Double) -> Color {
        func ch(_ h: Int, _ s: Int) -> Double { Double((h >> s) & 0xFF) / 255 }
        let g = M.clamp(f)
        return Color(.sRGB,
                     red: ch(a, 16) + (ch(b, 16) - ch(a, 16)) * g,
                     green: ch(a, 8) + (ch(b, 8) - ch(a, 8)) * g,
                     blue: ch(a, 0) + (ch(b, 0) - ch(a, 0)) * g,
                     opacity: 1)
    }

    // MARK: Resources

    static let burger: UIImage = {
        guard let path = Bundle.main.path(forResource: "CheeseDripBurger-burger", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()

    static let fontName = "Anton-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "CheeseDripBurger-Anton-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func anton(_ size: CGFloat) -> Font {
        _ = registered
        return Font.custom(fontName, fixedSize: size)
    }

    /// A static 128 px grain tile (seeded), drawn tiled at 5 %.
    static let grain: UIImage = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n * 4)
        for i in 0..<(n * n) {
            let v = UInt8(M.hash(Double(i) * 0.731 + 0.5) * 255)
            bytes[i * 4] = v; bytes[i * 4 + 1] = v; bytes[i * 4 + 2] = v; bytes[i * 4 + 3] = 255
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                         space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                         provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
        return UIImage(cgImage: cg)
    }()

    // MARK: Layout (design points, 393 wide)

    static let wordSize = 128.0
    static let rowSize = 150.0
    static let rowPitch = 133.0
    static let imageDraw = 350.0
    static var burgerScale: Double { imageDraw / M.imageSize }

    struct Layout {
        let width: Double, height: Double, top: Double
        let brandY: Double, topWordY: Double, bottomWordY: Double, labelY: Double, productY: Double
        let burgerCY: Double

        init(width: Double, height: Double, top: Double, bottom: Double) {
            self.width = width; self.height = height; self.top = top
            brandY = top + 22
            topWordY = top + 101
            productY = height - max(bottom, 16) - 20
            labelY = productY - 40
            bottomWordY = labelY - 76
            burgerCY = (topWordY + bottomWordY) / 2 + 6
        }

        var imageOrigin: CGPoint { CGPoint(x: 196.5 - imageDraw / 2, y: burgerCY - imageDraw / 2) }
        /// The bottom of the bun, where the squash pivots and the floor shadow sits.
        var baseY: Double { imageOrigin.y + M.puddleY * burgerScale }
    }

    // MARK: Background

    static func drawBackground(_ ctx: GraphicsContext, _ l: Layout) {
        let rect = CGRect(x: 0, y: 0, width: l.width, height: l.height)
        ctx.fill(Path(rect), with: .color(color(red)))
        ctx.fill(Path(rect), with: .radialGradient(
            Gradient(colors: [color(redGlow), color(redGlow, 0)]),
            center: CGPoint(x: 196.5, y: l.burgerCY), startRadius: 0, endRadius: 330))
        ctx.fill(Path(rect), with: .radialGradient(
            Gradient(stops: [.init(color: color(redDeep, 0), location: 0.55), .init(color: color(redDeep, 0.6), location: 1)]),
            center: CGPoint(x: 196.5, y: l.height * 0.48), startRadius: 0, endRadius: max(l.height * 0.62, 420)))
        var g = ctx
        g.opacity = 0.05
        g.blendMode = .overlay
        let tile = g.resolve(Image(uiImage: grain))
        var y = 0.0
        while y < l.height {
            var x = 0.0
            while x < l.width {
                g.draw(tile, in: CGRect(x: x, y: y, width: 128, height: 128))
                x += 128
            }
            y += 128
        }
    }

    /// The giant word stack behind the burger: faint at rest, yellow rows slide in when lit.
    static func drawRows(_ ctx: GraphicsContext, _ l: Layout, lit: [Double], slide: [Double], word: String) {
        let faint = ctx.resolve(Text(word).font(anton(rowSize)).foregroundStyle(color(rowFaint)))
        let lit0 = ctx.resolve(Text(word).font(anton(rowSize)).foregroundStyle(color(yellow)))
        for i in 0..<M.rows {
            let cy = l.burgerCY + Double(i - 1) * rowPitch
            ctx.draw(faint, at: CGPoint(x: 196.5, y: cy), anchor: .center)
            if lit[i] > 0.002 {
                var c = ctx
                c.opacity = lit[i]
                c.draw(lit0, at: CGPoint(x: 196.5 + slide[i], y: cy), anchor: .center)
            }
        }
    }

    /// A soft floor shadow under the bun; it shrinks and fades as the burger lifts.
    static func drawFloorShadow(_ ctx: GraphicsContext, _ l: Layout, lift: Double) {
        let f = M.clamp(1 - lift / 420)
        let w = 300 * (0.55 + 0.45 * f)
        var c = ctx
        c.opacity = 0.55 * f
        c.translateBy(x: 196.5, y: l.baseY + 8)
        c.scaleBy(x: 1, y: 0.16)
        c.fill(Path(ellipseIn: CGRect(x: -w / 2, y: -w / 2, width: w, height: w)), with: .radialGradient(
            Gradient(colors: [color(0x3A0000, 0.9), color(0x3A0000, 0)]),
            center: .zero, startRadius: 0, endRadius: w / 2))
    }

    // MARK: Steam

    /// Three soft wisps rising from the bun (burger space), periodic in the loop.
    static func drawSteam(_ ctx: GraphicsContext, ambient: Double, strength: Double) {
        var c = ctx
        c.addFilter(.blur(radius: 5))
        for i in 0..<3 {
            let x0 = 140.0 + Double(i) * 46
            let k = Double(i + 1)
            let phase = 2 * Double.pi * k * ambient / M.loop
            var p = Path()
            for j in 0...22 {
                let s = Double(j) / 22
                let y = 34 - s * 120
                let x = x0 + (4 + 10 * s) * sin(s * 5.2 - phase + Double(i) * 1.9)
                if j == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
            }
            let breathe = 0.6 + 0.4 * sin(2 * Double.pi * ambient / M.loop * 2 + Double(i) * 2.1)
            c.stroke(p, with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(0xFFFFFF, 0), location: 0),
                    .init(color: color(0xFFFFFF, 0.22 * breathe * strength), location: 0.35),
                    .init(color: color(0xFFFFFF, 0), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: 34), endPoint: CGPoint(x: 0, y: -86)),
                style: StrokeStyle(lineWidth: 11 - Double(i) * 1.5, lineCap: .round))
        }
    }

    // MARK: Cheese

    static func bumpAt(_ bumps: [M.Bump], layer: Int, x: Double) -> Double {
        var s = 0.0
        for b in bumps where b.layer == layer {
            let d = (x - b.x) / 14
            s += b.amp * exp(-d * d)
        }
        return s
    }

    /// Band thickness below the slice edge at x.
    static func bandThick(_ li: Int, x: Double, melt: Double, bumps: [M.Bump]) -> Double {
        let layer = M.layers[li]
        let u = abs((x - layer.cx) / layer.half)
        let taper = 0.3 + 0.7 * sqrt(M.clamp((1 - u) * 10))
        let wave = 1.6 * sin(x * 0.11 + Double(li) * 1.7) + 1.0 * sin(x * 0.27 + Double(li))
        return (layer.band * (1 + 0.45 * melt) + wave) * taper + bumpAt(bumps, layer: li, x: x)
    }

    /// The drape: from just above the slice edge (it covers the hard edge of the photo) down.
    static func addBand(_ p: inout Path, _ li: Int, melt: Double, bumps: [M.Bump]) {
        let layer = M.layers[li]
        let x0 = layer.cx - layer.half, x1 = layer.cx + layer.half
        let steps = 48
        p.move(to: CGPoint(x: x0, y: layer.edge(x0) - 6))
        for s in 1...steps {
            let x = x0 + (x1 - x0) * Double(s) / Double(steps)
            p.addLine(to: CGPoint(x: x, y: layer.edge(x) - 6))
        }
        for s in stride(from: steps, through: 0, by: -1) {
            let x = x0 + (x1 - x0) * Double(s) / Double(steps)
            p.addLine(to: CGPoint(x: x, y: layer.edge(x) + bandThick(li, x: x, melt: melt, bumps: bumps)))
        }
        p.closeSubpath()
    }

    struct DripGeometry {
        let x, w, wn, r, yb, yn, y1, top, fillet: Double
    }

    static func geometry(_ d: M.DripShape, bandBottom yj: Double) -> DripGeometry {
        let wn = M.lerp(d.w * 0.8, d.w * 0.18, d.neck)
        let r = max(d.r, wn * 0.5 + 0.5)
        let yb = d.y0 + d.length
        let top = yj - 5
        let fillet = 0.35 * d.w + 3
        let yn = max(yb - r * 0.72, top + 1)
        let y1 = min(top + fillet * 0.9, yn)
        return DripGeometry(x: d.x, w: d.w, wn: wn, r: r, yb: yb, yn: yn, y1: y1, top: top, fillet: fillet)
    }

    /// One drip: a filleted skirt into the band, a column that narrows to a neck, a round bulb.
    /// Every subpath of the goo runs clockwise on screen, so the nonzero fill unites them.
    static func addDrip(_ p: inout Path, _ g: DripGeometry) {
        let x = g.x, hw = g.w / 2, hn = g.wn / 2, r = g.r, yb = g.yb, yn = g.yn, y1 = g.y1, top = g.top, fr = g.fillet
        func pt(_ a: Double, _ b: Double) -> CGPoint { CGPoint(x: a, y: b) }
        p.move(to: pt(x + hw + fr, top))
        p.addCurve(to: pt(x + hw, y1), control1: pt(x + hw + fr * 0.35, top), control2: pt(x + hw, top + fr * 0.4))
        p.addCurve(to: pt(x + hn, yn), control1: pt(x + hw, M.lerp(y1, yn, 0.5)), control2: pt(x + hn, M.lerp(y1, yn, 0.55)))
        p.addCurve(to: pt(x + r, yb), control1: pt(x + hn, yn + r * 0.22), control2: pt(x + r, yb - r * 0.5))
        p.addCurve(to: pt(x, yb + r), control1: pt(x + r, yb + r * 0.552), control2: pt(x + r * 0.552, yb + r))
        p.addCurve(to: pt(x - r, yb), control1: pt(x - r * 0.552, yb + r), control2: pt(x - r, yb + r * 0.552))
        p.addCurve(to: pt(x - hn, yn), control1: pt(x - r, yb - r * 0.5), control2: pt(x - hn, yn + r * 0.22))
        p.addCurve(to: pt(x - hw, y1), control1: pt(x - hn, M.lerp(y1, yn, 0.55)), control2: pt(x - hw, M.lerp(y1, yn, 0.5)))
        p.addCurve(to: pt(x - hw - fr, top), control1: pt(x - hw, top + fr * 0.4), control2: pt(x - hw - fr * 0.35, top))
        p.closeSubpath()
    }

    /// An ellipse drawn clockwise on screen (see addDrip).
    static func addBlob(_ p: inout Path, x: Double, y: Double, rx: Double, ry: Double) {
        let k = 0.552
        func pt(_ a: Double, _ b: Double) -> CGPoint { CGPoint(x: a, y: b) }
        p.move(to: pt(x, y - ry))
        p.addCurve(to: pt(x + rx, y), control1: pt(x + rx * k, y - ry), control2: pt(x + rx, y - ry * k))
        p.addCurve(to: pt(x, y + ry), control1: pt(x + rx, y + ry * k), control2: pt(x + rx * k, y + ry))
        p.addCurve(to: pt(x - rx, y), control1: pt(x - rx * k, y + ry), control2: pt(x - rx, y + ry * k))
        p.addCurve(to: pt(x, y - ry), control1: pt(x - rx, y - ry * k), control2: pt(x - rx * k, y - ry))
        p.closeSubpath()
    }

    static func puddleHalfWidth(melt: Double, bumps: [M.Bump]) -> Double {
        4 + 94 * melt + bumpAt(bumps, layer: 2, x: M.puddleCX) * 2
    }

    /// The pool at the base: a low, lumpy blob that spreads with the melt and swells where drops land.
    static func addPuddle(_ p: inout Path, melt: Double, bumps: [M.Bump]) {
        let hw = puddleHalfWidth(melt: melt, bumps: bumps)
        let h = 1.5 + 8.5 * melt
        let steps = 40
        func lump(_ x: Double) -> Double {
            let a = (x - (M.puddleCX - hw * 0.45)) / 16, b = (x - (M.puddleCX + hw * 0.4)) / 20
            return 2.4 * exp(-a * a) + 1.8 * exp(-b * b)
        }
        for s in 0...steps {
            let f = Double(s) / Double(steps)
            let x = M.puddleCX - hw + 2 * hw * f
            let e = sqrt(max(0, 1 - pow(2 * f - 1, 2)))
            let y = M.puddleY - (h * pow(e, 0.7) + lump(x) * e + bumpAt(bumps, layer: 2, x: x) * e)
            if s == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
        }
        for s in stride(from: steps, through: 0, by: -1) {
            let f = Double(s) / Double(steps)
            let x = M.puddleCX - hw + 2 * hw * f
            let e = sqrt(max(0, 1 - pow(2 * f - 1, 2)))
            p.addLine(to: CGPoint(x: x, y: M.puddleY + h * 0.75 * pow(e, 0.8)))
        }
        p.closeSubpath()
    }

    static func cheeseShading(top: Double) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: cheeseStops.map { .init(color: color($0.1), location: $0.0) }),
                        startPoint: CGPoint(x: 0, y: top - 6), endPoint: CGPoint(x: 0, y: top + 110))
    }

    /// Draws both slices, lower first so the upper one runs over it. `ctx` is in burger space.
    /// One path per slice, always filled (never stroked), so overlapping parts read as one goo.
    static func drawCheese(_ ctx: GraphicsContext, _ f: M.Frame) {
        for li in 0..<M.layers.count {
            let layer = M.layers[li]
            var goo = Path()
            addBand(&goo, li, melt: f.melt, bumps: f.bumps)
            var geos: [DripGeometry] = []
            for d in f.drips[li] {
                let g = geometry(d, bandBottom: layer.edge(d.x) + bandThick(li, x: d.x, melt: f.melt, bumps: f.bumps))
                geos.append(g)
                addDrip(&goo, g)
            }
            if li == 0 {
                if f.melt > 0.02 { addPuddle(&goo, melt: f.melt, bumps: f.bumps) }
                for drop in f.drops {
                    addBlob(&goo, x: drop.x, y: drop.y, rx: drop.rx, ry: drop.ry)
                }
            }

            // Soft shadow on the patty below.
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 3.5))
                c.translateBy(x: 2, y: 4)
                c.fill(goo, with: .color(color(shadow, 0.45)))
            }
            // The rim colour, then the body nudged up-left over it: a darker lower-right edge.
            ctx.fill(goo, with: .color(color(rim)))
            ctx.drawLayer { c in
                c.clip(to: goo)
                var body = c
                body.translateBy(x: -1.1, y: -1.7)
                body.fill(goo, with: cheeseShading(top: layer.y))
                // Each drip is rounder on its right side.
                c.drawLayer { s in
                    s.addFilter(.blur(radius: 2.4))
                    for g in geos where g.yb + g.r > g.top + 10 {
                        var shade = Path()
                        shade.move(to: CGPoint(x: g.x + g.w * 0.36, y: g.y1))
                        shade.addLine(to: CGPoint(x: g.x + g.wn * 0.32, y: g.yn))
                        shade.addLine(to: CGPoint(x: g.x + g.r * 0.6, y: g.yb + g.r * 0.25))
                        s.stroke(shade, with: .color(color(shade_, 0.38)), style: StrokeStyle(lineWidth: max(2, g.w * 0.26), lineCap: .round, lineJoin: .round))
                    }
                }
                // A thin light crescent on every upper-left edge.
                c.drawLayer { h in
                    h.addFilter(.blur(radius: 0.7))
                    h.fill(goo, with: .color(color(gloss, 0.7)))
                    var cut = h
                    cut.blendMode = .destinationOut
                    cut.translateBy(x: 1.2, y: 1.8)
                    cut.fill(goo, with: .color(.black))
                }
            }
            drawGloss(ctx, li, geos: geos, drops: li == 0 ? f.drops : [], melt: f.melt, bumps: f.bumps)
        }
    }

    static func drawGloss(_ ctx: GraphicsContext, _ li: Int, geos: [DripGeometry], drops: [M.DropShape], melt: Double, bumps: [M.Bump]) {
        ctx.drawLayer { c in
            c.addFilter(.blur(radius: 0.6))
            let white = color(gloss, 0.9)
            for g in geos {
                let len = g.yb - g.y1
                if len > 8 {
                    var streak = Path()
                    streak.move(to: CGPoint(x: g.x - g.w * 0.22, y: g.y1 + 1))
                    streak.addQuadCurve(to: CGPoint(x: g.x - g.r * 0.5, y: g.yb - g.r * 0.45),
                                        control: CGPoint(x: g.x - g.wn * 0.28, y: g.yn))
                    c.stroke(streak, with: .color(color(gloss, 0.62)), style: StrokeStyle(lineWidth: max(1.2, g.w * 0.1), lineCap: .round))
                }
                let gw = g.r * 0.4, gh = g.r * 0.55
                c.fill(Path(ellipseIn: CGRect(x: g.x - g.r * 0.48 - gw / 2, y: g.yb - g.r * 0.34 - gh / 2, width: gw, height: gh)), with: .color(white))
            }
            for d in drops where d.rx > 1 {
                let gw = d.rx * 0.42, gh = d.ry * 0.5
                c.fill(Path(ellipseIn: CGRect(x: d.x - d.rx * 0.46 - gw / 2, y: d.y - d.ry * 0.38 - gh / 2, width: gw, height: gh)), with: .color(white))
            }
            if li == 0 && melt > 0.15 {
                let hw = puddleHalfWidth(melt: melt, bumps: bumps)
                var arc = Path()
                arc.move(to: CGPoint(x: M.puddleCX - hw * 0.7, y: M.puddleY - 2 - 2 * melt))
                arc.addQuadCurve(to: CGPoint(x: M.puddleCX - hw * 0.05, y: M.puddleY - 3.5 - 3 * melt),
                                 control: CGPoint(x: M.puddleCX - hw * 0.4, y: M.puddleY - 6 - 4 * melt))
                c.stroke(arc, with: .color(color(gloss, 0.6)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
            }
        }
    }

    // MARK: Brand mark

    /// A tiny burger glyph: bun dome, patty, bun base (22 × 15).
    static func drawMark(_ ctx: GraphicsContext, tint: Color) {
        var dome = Path()
        dome.move(to: CGPoint(x: 1, y: 7))
        dome.addCurve(to: CGPoint(x: 21, y: 7), control1: CGPoint(x: 1, y: -1.5), control2: CGPoint(x: 21, y: -1.5))
        dome.closeSubpath()
        ctx.fill(dome, with: .color(tint))
        ctx.fill(Path(roundedRect: CGRect(x: 0, y: 8.4, width: 22, height: 2.6), cornerRadius: 1.3), with: .color(tint))
        ctx.fill(Path(roundedRect: CGRect(x: 1.5, y: 12, width: 19, height: 3), cornerRadius: 1.5), with: .color(tint))
        for (x, y) in [(7.0, 3.6), (11.0, 2.4), (15.0, 3.6)] {
            ctx.fill(Path(ellipseIn: CGRect(x: x - 0.9, y: y - 0.6, width: 1.8, height: 1.2)), with: .color(color(red)))
        }
    }
}
