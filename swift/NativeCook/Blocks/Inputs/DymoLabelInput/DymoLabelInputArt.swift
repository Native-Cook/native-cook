import SwiftUI

/*
 Dymo Label Input — what is drawn per frame: the jars where the swap has them, the wheel image at
 its angle (its gloss stays put), the trigger, the tape with its embossed letters, the cutter, and
 the strip in flight. Letters are pressed in: a dark lower-right edge, a bright upper-left edge and
 a stress-whitened face that turns from the tape's colour to near white.
 */

struct DymoLabelInputGeo: Equatable {
    var w: Double
    var h: Double
    var cx: Double
    var shelfY: Double
    var jarW: Double
    var jarH: Double
    /// Centre of the label on the jar.
    var labelX: Double
    var labelY: Double
    var wheelX: Double
    var wheelY: Double
    var wheelR: Double
    /// The head (where letters are pressed) and the tape's centre line.
    var headX: Double
    var tapeY: Double
    var tapeH: Double = 24
    var pitch: Double = 15
    /// How far a jar travels when it is swapped.
    var swap: Double

    init(w: Double, h: Double, hero: CGRect) {
        self.w = w
        self.h = h
        shelfY = hero.minY + hero.height * 0.43
        jarW = min(146, hero.width * 0.4)
        jarH = min(jarW * 1.18, hero.height * 0.37)
        wheelR = min(76, hero.width * 0.2, hero.height * 0.175)
        wheelX = hero.minX + hero.width * 0.4
        wheelY = hero.minY + hero.height * 0.8
        tapeY = wheelY - wheelR - 17
        cx = hero.midX
        labelX = hero.midX
        labelY = shelfY - jarH * 0.42
        headX = wheelX
        swap = w * 0.62 + jarW
    }

    /// Side of the body image (pt), centred on the wheel.
    var bodySide: Double { wheelR * 3.6 }
    var wheelSide: Double { wheelR * 2 + 8 }
}

struct DymoLabelInputImages {
    let wall: Image
    let jar: Image
    let body: Image
    let wheel: Image
}

enum DymoLabelInputArt {
    static let letterFont = Font.system(size: 17, weight: .bold)

    static func col(_ c: DymoLabelInputRGB, _ a: Double = 1) -> Color { Color(red: c.0, green: c.1, blue: c.2, opacity: a) }
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color { Color(red: r, green: g, blue: b, opacity: a) }

    static func tone(_ c: DymoLabelInputRGB, _ k: Double) -> DymoLabelInputRGB {
        if k < 0 { return (c.0 * (1 + k), c.1 * (1 + k), c.2 * (1 + k)) }
        return (c.0 + (1 - c.0) * k, c.1 + (1 - c.1) * k, c.2 + (1 - c.2) * k)
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func grad(_ stops: [(Color, Double)]) -> Gradient {
        Gradient(stops: stops.map { .init(color: $0.0, location: $0.1) })
    }

    /// A run of glossy tape from x0 to x1 on centre line y.
    private static func drawTape(_ ctx: GraphicsContext, _ x0: Double, _ x1: Double, _ y: Double, _ h: Double, _ rgb: DymoLabelInputRGB) {
        if x1 <= x0 { return }
        let r = rr(x0, y - h / 2, x1 - x0, h, 2.5)
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.6))
            l.translateBy(x: 0.6, y: 1.6)
            l.fill(r, with: .color(rgba(0.1, 0.06, 0.04, 0.3)))
        }
        ctx.fill(r, with: .linearGradient(grad([
            (col(tone(rgb, 0.22)), 0), (col(rgb), 0.3), (col(tone(rgb, -0.12)), 0.75), (col(tone(rgb, -0.32)), 1),
        ]), startPoint: CGPoint(x: 0, y: y - h / 2), endPoint: CGPoint(x: 0, y: y + h / 2)))
        // The gloss: a bright band near the top edge and a hairline on the lower edge.
        ctx.fill(rr(x0 + 1.5, y - h / 2 + 2, x1 - x0 - 3, 2.6, 1.3), with: .color(rgba(1, 1, 1, 0.32)))
        ctx.fill(Path(CGRect(x: x0 + 1, y: y + h / 2 - 1.6, width: x1 - x0 - 2, height: 0.8)), with: .color(rgba(1, 1, 1, 0.12)))
    }

    /// Resolved glyphs for this frame, one per character.
    final class Glyphs {
        private var cache: [Character: GraphicsContext.ResolvedText] = [:]
        private let ctx: GraphicsContext
        init(_ ctx: GraphicsContext) { self.ctx = ctx }
        func get(_ ch: Character) -> GraphicsContext.ResolvedText {
            if let r = cache[ch] { return r }
            let r = ctx.resolve(Text(String(ch)).font(letterFont).foregroundColor(.white))
            cache[ch] = r
            return r
        }
    }

    /// One embossed letter centred at (x, y): w = how white it has become, s = scale.
    private static func drawLetter(_ ctx: GraphicsContext, _ glyphs: Glyphs, _ ch: Character, _ x: Double, _ y: Double, _ rgb: DymoLabelInputRGB, _ w: Double, _ s: Double) {
        if w <= 0.01 || ch == " " { return }
        var c = ctx
        c.translateBy(x: x, y: y)
        c.scaleBy(x: 0.84 * s, y: s)
        var t = glyphs.get(ch)
        // Baseline 6 pt below the centre, like the Skia text.
        let size = t.measure(in: CGSize(width: 100, height: 100))
        let base = t.firstBaseline(in: CGSize(width: 100, height: 100))
        let anchor = UnitPoint(x: 0.5, y: size.height > 0 ? base / size.height : 0.8)
        t.shading = .color(col(tone(rgb, -0.55), 0.7 * w))
        c.draw(t, at: CGPoint(x: 0.9, y: 6 + 1.1), anchor: anchor)
        t.shading = .color(rgba(1, 1, 1, 0.75 * w))
        c.draw(t, at: CGPoint(x: -0.55, y: 6 - 0.65), anchor: anchor)
        t.shading = .color(col(DymoLabelInputMotion.mix(rgb, (0.97, 0.95, 0.93), 0.88 * w)))
        c.draw(t, at: CGPoint(x: 0, y: 6), anchor: anchor)
    }

    static func stripLength(_ g: DymoLabelInputGeo, _ n: Int) -> Double { 2 * 14 + Double(n) * g.pitch }

    /// The whole scene for a pose.
    static func drawScene(_ ctx: GraphicsContext, _ g: DymoLabelInputGeo, _ art: DymoLabelInputImages, _ pose: DymoLabelInputPose) {
        let glyphs = Glyphs(ctx)
        ctx.draw(art.wall, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))

        // Jars: the one on the shelf (labelled once the strip lands) and, during the swap, the next one.
        let jarSide = g.jarW + 60
        let jarTop = g.shelfY - g.jarH - 30
        let oldX = g.cx - pose.slide * g.swap
        let newX = g.cx + (1 - pose.slide) * g.swap
        let jar = ctx.resolve(art.jar)
        if pose.slide < 1 { ctx.draw(jar, in: CGRect(x: oldX - jarSide / 2, y: jarTop, width: jarSide, height: g.jarH + 50)) }
        if pose.slide > 0 { ctx.draw(jar, in: CGRect(x: newX - jarSide / 2, y: jarTop, width: jarSide, height: g.jarH + 50)) }

        // The embosser.
        let bs = g.bodySide
        ctx.draw(art.body, in: CGRect(x: g.wheelX - bs / 2, y: g.wheelY - bs / 2, width: bs, height: bs))
        // The trigger lever under the handle, pivoting at its left end.
        let tx = g.wheelX + g.wheelR * 0.45
        let ty = g.wheelY + g.wheelR * 0.62
        do {
            var c = ctx
            c.translateBy(x: tx, y: ty)
            c.rotate(by: .degrees(-7 * pose.trigger))
            c.translateBy(x: -tx, y: -ty)
            let lr = rr(tx - 6, ty - 10, g.wheelR * 2.55, 22, 11)
            c.drawLayer { l in
                l.addFilter(.blur(radius: 4))
                l.translateBy(x: 1, y: 4)
                l.fill(lr, with: .color(rgba(0.1, 0.06, 0.03, 0.3)))
            }
            c.fill(lr, with: .linearGradient(grad([
                (rgba(0.36, 0.38, 0.42, 1), 0), (rgba(0.22, 0.23, 0.26, 1), 0.5), (rgba(0.12, 0.12, 0.14, 1), 1),
            ]), startPoint: CGPoint(x: 0, y: ty - 11), endPoint: CGPoint(x: 0, y: ty + 11)))
            var grip = Path()
            for k in 0..<7 { grip.addRect(CGRect(x: tx + g.wheelR * 1.1 + Double(k) * 7, y: ty - 6, width: 2, height: 12)) }
            c.fill(grip, with: .color(rgba(1, 1, 1, 0.1)))
        }

        // Wheel, turned; its gloss and the head's index stay put.
        do {
            let ws = g.wheelSide
            var c = ctx
            c.translateBy(x: g.wheelX, y: g.wheelY)
            c.rotate(by: .degrees(pose.wheel))
            c.draw(art.wheel, in: CGRect(x: -ws / 2, y: -ws / 2, width: ws, height: ws))
        }
        ctx.fill(
            Path(ellipseIn: CGRect(x: g.wheelX - g.wheelR, y: g.wheelY - g.wheelR, width: g.wheelR * 2, height: g.wheelR * 2)),
            with: .radialGradient(grad([(rgba(1, 1, 1, 0.22), 0), (rgba(1, 1, 1, 0.05), 0.5), (rgba(1, 1, 1, 0), 1)]),
                                  center: CGPoint(x: g.wheelX - g.wheelR * 0.35, y: g.wheelY - g.wheelR * 0.45), startRadius: 0, endRadius: g.wheelR * 1.1)
        )
        var index = Path()
        let iy = g.wheelY - g.wheelR - 2
        index.move(to: CGPoint(x: g.headX - 5, y: iy - 6))
        index.addLine(to: CGPoint(x: g.headX + 5, y: iy - 6))
        index.addLine(to: CGPoint(x: g.headX, y: iy + 1))
        index.closeSubpath()
        ctx.fill(index, with: .color(rgba(0.85, 0.2, 0.2, 1)))

        // Tape: blank lead, then the pressed letters; the newest sits under the head and pops as it is
        // struck, then the tape steps one pitch to the left.
        let half = g.pitch / 2
        let leading = g.headX - pose.advance * g.pitch - half - pose.lead
        drawTape(ctx, leading, g.headX + 70, g.tapeY, g.tapeH, pose.tapeRGB)
        for (i, ch) in pose.tape.enumerated() {
            let x = g.headX - (pose.advance - Double(i)) * g.pitch
            let w = i < pose.whiten.count ? pose.whiten[i] : 1
            let p = i < pose.pop.count ? pose.pop[i] : 0
            drawLetter(ctx, glyphs, ch, x, g.tapeY, pose.tapeRGB, w, 1 + 0.12 * p)
        }
        // Tape guide over the tape inside the machine, the punch head and the cutter.
        let guideR = rr(g.headX + 11, g.tapeY - 17, 92, 34, 9)
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 3))
            l.translateBy(x: -1.5, y: 2.5)
            l.fill(guideR, with: .color(rgba(0.1, 0.06, 0.03, 0.28)))
        }
        ctx.fill(guideR, with: .linearGradient(grad([
            (rgba(0.92, 0.9, 0.86, 1), 0), (rgba(0.8, 0.77, 0.72, 1), 0.5), (rgba(0.62, 0.59, 0.55, 1), 1),
        ]), startPoint: CGPoint(x: 0, y: g.tapeY - 18), endPoint: CGPoint(x: 0, y: g.tapeY + 18)))
        let head = GraphicsContext.Shading.linearGradient(grad([
            (rgba(0.62, 0.64, 0.67, 1), 0), (rgba(0.93, 0.94, 0.95, 1), 0.45), (rgba(0.55, 0.57, 0.6, 1), 1),
        ]), startPoint: CGPoint(x: g.headX - 10, y: 0), endPoint: CGPoint(x: g.headX + 10, y: 0))
        let hy = g.tapeY - 19 + 3 * pose.trigger
        ctx.fill(rr(g.headX - 9, hy, 18, 9, 2.5), with: head)
        do {
            var c = ctx
            let px = g.headX - 30, py = g.tapeY - 24
            c.translateBy(x: px, y: py)
            c.rotate(by: .degrees(-16 * pose.cutter))
            c.translateBy(x: -px, y: -py)
            c.fill(rr(g.headX - 46, g.tapeY - 30, 30, 9, 4.5), with: head)
        }

        // The cut strip: from where it was cut, along an arc with a turn and a lift, onto the jar.
        if !pose.strip.isEmpty {
            let len = stripLength(g, pose.strip.count)
            let sx = g.headX - half - 4 - len / 2
            let sy = g.tapeY
            let ex = g.labelX - pose.slide * g.swap
            let ey = g.labelY
            let f = pose.fly
            let mx = (sx + ex) / 2
            let my = min(sy, ey) - 120
            let x = (1 - f) * (1 - f) * sx + 2 * f * (1 - f) * mx + f * f * ex
            let y = (1 - f) * (1 - f) * sy + 2 * f * (1 - f) * my + f * f * ey
            let lift = sin(.pi * f)
            let rot = -18 * lift - 2 * f
            let scale = 1 + 0.16 * lift
            let body = rr(-len / 2, -g.tapeH / 2, len, g.tapeH, 3)
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 2 + 8 * lift))
                l.translateBy(x: x + 4 + 16 * lift, y: y + 5 + 26 * lift)
                l.rotate(by: .degrees(rot))
                l.fill(body, with: .color(rgba(0.1, 0.06, 0.03, 0.25 - 0.1 * lift)))
            }
            var c = ctx
            c.translateBy(x: x, y: y)
            c.rotate(by: .degrees(rot))
            c.scaleBy(x: scale * (1 + 0.05 * pose.squash), y: scale * (1 - 0.1 * pose.squash))
            let x0 = -len / 2
            let lead = 14.0
            drawTape(c, x0, x0 + lead * 2 + Double(pose.strip.count) * g.pitch, 0, g.tapeH, pose.stripRGB)
            for (i, ch) in pose.strip.enumerated() {
                drawLetter(c, glyphs, ch, x0 + lead + (Double(i) + 0.5) * g.pitch, 0, pose.stripRGB, 1, 1)
            }
            // On the jar the strip wraps round the glass: darker toward its ends.
            if f > 0.9 {
                var wc = c
                wc.opacity = (f - 0.9) / 0.1
                wc.fill(rr(-len / 2, -g.tapeH / 2, len, g.tapeH, 2.5), with: .linearGradient(grad([
                    (rgba(0, 0, 0, 0.3), 0), (rgba(0, 0, 0, 0), 0.3), (rgba(1, 1, 1, 0.08), 0.55), (rgba(0, 0, 0, 0.32), 1),
                ]), startPoint: CGPoint(x: -len / 2, y: 0), endPoint: CGPoint(x: len / 2, y: 0)))
            }
        }
    }
}
