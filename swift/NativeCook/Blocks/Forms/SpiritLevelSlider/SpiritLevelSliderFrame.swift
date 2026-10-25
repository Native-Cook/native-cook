import SwiftUI
import UIKit

/// What the spirit level draws every frame over its baked art, in one Canvas: the roof at the
/// current pitch, the readouts, the level's shadow and image under its rotation, the bubble and the
/// vial's glass. A port of the per-frame half of spiritArt.ts.
enum SpiritLevelSliderFrame {
    private typealias M = SpiritLevelSliderMotion
    private typealias P = SpiritLevelSliderPaint

    static let degreesUI = P.sans(58, .bold)
    static let riseUI = P.sans(17, .semibold)
    static let pillUI = P.sans(13, .semibold)
    static let degrees = Font.system(size: 58, weight: .bold)
    static let rise = Font.system(size: 17, weight: .semibold)
    static let pill = Font.system(size: 13, weight: .semibold)

    static let degreeWidths: [CGFloat] = (0...9).map { P.width("\($0)", degreesUI) }
    static let riseWidths: [CGFloat] = (0...9).map { P.width("\($0)", riseUI) }
    static let pillWidths: [CGFloat] = M.pitchWords.map { P.width($0, pillUI) }

    @discardableResult
    static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color, x: CGFloat, baseline: CGFloat) -> CGFloat {
        let r = ctx.resolve(Text(s).font(font).foregroundColor(color))
        let big = CGSize(width: 10_000, height: 10_000)
        let b = r.firstBaseline(in: big)
        ctx.draw(r, at: CGPoint(x: x, y: baseline - b), anchor: .topLeading)
        return r.measure(in: big).width
    }

    /// Two-digit odometer; the tens column hides when zero. Returns the width drawn.
    private static func odometer(_ ctx: GraphicsContext, _ value: Double, x: CGFloat, base: CGFloat, font: Font,
                                 widths: [CGFloat], lineH: CGFloat, color: UInt32) -> CGFloat {
        let digitW = widths.max() ?? 10
        let units = M.softRound(max(0, value))
        let um = (units.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        let tens = floor(units / 10) + max(0, um - 9)
        let lead = M.clamp(tens, 0, 1)
        let shift = -digitW * CGFloat(1 - lead)
        var clip = ctx
        clip.clip(to: Path(CGRect(x: x - 4, y: base - lineH * 0.8, width: digitW * 2 + 8, height: lineH)))
        let cols = [tens, units]
        for c in 0..<2 {
            let d = (cols[c].truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
            let i = Int(floor(d))
            let fr = d - Double(i)
            let cx = x + shift + CGFloat(c) * digitW
            for j in 0..<2 {
                let digit = (i + j) % 10
                if c == 0 && digit == 0 && cols[0] < 1.5 { continue }
                let a = (c == 0 ? lead : 1) * (1 - min(1, abs(Double(j) - fr)))
                if a <= 0.01 { continue }
                text(clip, "\(digit)", font, P.color(color, a), x: cx + (digitW - widths[digit]) / 2,
                     baseline: base + CGFloat(Double(j) - fr) * lineH * 0.8)
            }
        }
        return digitW * 2 + shift
    }

    static func readout(_ ctx: GraphicsContext, _ g: SpiritLevelSliderGeo, angle: Double) {
        let card = g.card
        let ink = SpiritLevelSliderArt.ink
        let a = max(0, angle)
        let base = card.minY + 82
        let w = odometer(ctx, a, x: card.minX + 18, base: base, font: degrees, widths: degreeWidths, lineH: 64, color: ink)
        text(ctx, "°", degrees, P.color(ink, 0.9), x: card.minX + 18 + w + 1, baseline: base)
        let b2 = card.minY + 110
        let rw = odometer(ctx, M.riseOf(a), x: card.minX + 20, base: b2, font: rise, widths: riseWidths, lineH: 26, color: ink)
        text(ctx, "/12 pitch", rise, P.color(ink, 0.6), x: card.minX + 20 + rw + 1, baseline: b2)
        // The pitch word in a pill whose width follows the rolling word.
        let k = M.pitchIndex(a)
        let i = Int(M.clamp(floor(k), 0, Double(M.pitchWords.count - 1)))
        let fr = k - Double(i)
        let w0 = pillWidths[i]
        let w1 = i + 1 < M.pitchWords.count ? pillWidths[i + 1] : w0
        let pw = w0 + (w1 - w0) * CGFloat(fr) + 22
        let py = card.maxY - 44
        let pillPath = Path(roundedRect: CGRect(x: card.minX + 18, y: py, width: pw, height: 26), cornerRadius: 13)
        ctx.fill(pillPath, with: .color(P.color(0xE8862A, 0.14)))
        var clip = ctx
        clip.clip(to: pillPath)
        for j in 0..<2 {
            guard i + j < M.pitchWords.count else { continue }
            let al = 1 - min(1, abs(Double(j) - fr))
            if al <= 0.01 { continue }
            text(clip, M.pitchWords[i + j], pill, P.color(0xA8520F, al),
                 x: card.minX + 29, baseline: py + 17.5 + CGFloat(Double(j) - fr) * 18)
        }
    }

    private static let roofShading = Gradient(stops: [
        .init(color: P.color(0x5A6470), location: 0),
        .init(color: P.color(0x434C57), location: 0.5),
        .init(color: P.color(0x2F363E), location: 1),
    ])

    /// Gable, roof slab and the pitch arc, for the current angle.
    static func roof(_ ctx: GraphicsContext, _ g: SpiritLevelSliderGeo, angle: Double) {
        let a = M.clamp(angle, 0, 50) * .pi / 180
        let tanA = CGFloat(min(tan(a), Double((g.card.height - 38 - g.houseWH) / g.houseHW)))
        let hx = g.houseX, hw = g.houseHW
        let top = g.houseY - g.houseWH
        let ridgeY = top - hw * tanA
        let over: CGFloat = 12
        let lx = hx - hw - over
        let rx = hx + hw + over
        let ey = top + over * tanA
        let slab = 7 / CGFloat(cos(a))
        var gable = Path()
        gable.move(to: CGPoint(x: hx - hw, y: top))
        gable.addLine(to: CGPoint(x: hx, y: ridgeY))
        gable.addLine(to: CGPoint(x: hx + hw, y: top))
        gable.closeSubpath()
        ctx.fill(gable, with: .color(P.color(0xE4D5BB)))
        var roof = Path()
        roof.move(to: CGPoint(x: lx, y: ey - slab))
        roof.addLine(to: CGPoint(x: hx, y: ridgeY - slab))
        roof.addLine(to: CGPoint(x: rx, y: ey - slab))
        roof.addLine(to: CGPoint(x: rx, y: ey))
        roof.addLine(to: CGPoint(x: hx, y: ridgeY))
        roof.addLine(to: CGPoint(x: lx, y: ey))
        roof.closeSubpath()
        ctx.fill(roof, with: .linearGradient(roofShading, startPoint: CGPoint(x: 0, y: ridgeY - slab), endPoint: CGPoint(x: 0, y: ey)))
        let round = StrokeStyle(lineWidth: 1, lineCap: .round)
        ctx.stroke(segment(lx, ey - slab, hx, ridgeY - slab), with: .color(P.color(0xA6B0BB, 0.7)), style: round)
        ctx.stroke(segment(hx, ridgeY - slab, rx, ey - slab), with: .color(P.color(0x8994A0, 0.5)), style: round)
        // Pitch arc and its run line at the left eave.
        let ax = hx - hw
        let arcR: CGFloat = 26
        ctx.stroke(segment(ax, top, ax + arcR + 14, top), with: .color(P.color(SpiritLevelSliderArt.ink, 0.35)),
                   style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [3, 3]))
        if angle > 0.5 {
            var arc = Path()
            let n = 24
            for k in 0...n {
                let th = -a + a * Double(k) / Double(n)
                let p = CGPoint(x: ax + arcR * CGFloat(cos(th)), y: top + arcR * CGFloat(sin(th)))
                if k == 0 { arc.move(to: p) } else { arc.addLine(to: p) }
            }
            ctx.stroke(arc, with: .color(P.color(0xE8862A, 0.95)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        }
    }

    private static func segment(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static let bubbleShading = Gradient(stops: [
        .init(color: P.color(0xFBFFE9, 0.98), location: 0),
        .init(color: P.color(0xEEF9B8, 0.95), location: 0.55),
        .init(color: P.color(0xD2EA6A, 0.9), location: 1),
    ])

    /// Shadow, body image, bubble and glass, in the level's rotated frame.
    static func level(_ ctx: GraphicsContext, _ g: SpiritLevelSliderGeo, body: UIImage, shadow: UIImage,
                      angle: Double, bubble: Double, speed: Double) {
        let rot = Angle.degrees(-angle)
        var s = ctx
        s.translateBy(x: g.cx, y: g.cy + 18)
        s.rotate(by: rot)
        let sp = SpiritLevelSliderGeo.shadowPad
        s.draw(Image(uiImage: shadow), in: CGRect(x: -g.len / 2 - sp, y: -g.thick / 2 - sp, width: g.len + sp * 2, height: g.thick + sp * 2))
        var l = ctx
        l.translateBy(x: g.cx, y: g.cy)
        l.rotate(by: rot)
        let pad = SpiritLevelSliderGeo.pad
        let box = g.levelBox
        l.draw(Image(uiImage: body), in: CGRect(x: -g.len / 2 - pad, y: -g.thick / 2 - pad, width: box.width, height: box.height))
        // Bubble: travels to the higher end, stretched by speed, squashed against the ends.
        var x = CGFloat(bubble) * g.travel
        var len = g.bubbleLen
        var h = g.bubbleH
        let stretch = CGFloat(M.clamp(abs(speed) * Double(g.travel) / 700, 0, 0.32))
        len *= 1 + stretch
        h *= 1 - stretch * 0.4
        let limit = g.vialHalf - 2
        if abs(x) + len / 2 > limit {
            let over = abs(x) + len / 2 - limit
            let squash = min(over, len * 0.32)
            len -= squash
            h *= 1 + squash / g.bubbleLen * 0.35
            x = (x < 0 ? -1 : 1) * (limit - len / 2)
        }
        let y = g.tubeY(x) - g.thick * 0.2 + h / 2 + 1
        let r = Path(roundedRect: CGRect(x: x - len / 2, y: y - h / 2, width: len, height: h), cornerRadius: h / 2)
        l.fill(r, with: .linearGradient(bubbleShading, startPoint: CGPoint(x: 0, y: y - h / 2), endPoint: CGPoint(x: 0, y: y + h / 2)))
        l.stroke(r, with: .color(P.color(0x56700F, 0.75)), lineWidth: 1.3)
        l.fill(Path(roundedRect: CGRect(x: x - len * 0.32, y: y - h / 2 + 1.6, width: len * 0.5, height: h * 0.3), cornerRadius: h * 0.15),
               with: .color(P.color(0xFFFFFF, 0.9)))
        l.stroke(segment(x - len / 2 + h * 0.4, y + h / 2 + 1.2, x + len / 2 - h * 0.4, y + h / 2 + 1.2),
                 with: .color(P.color(0x4D6410, 0.35)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        // Glass over the tube: a bright streak along the top.
        let th = g.thick * 0.4
        var streak = Path()
        for i in 0...16 {
            let sx = -g.vialHalf * 0.86 + CGFloat(i) / 16 * g.vialHalf * 1.72
            let p = CGPoint(x: sx, y: g.tubeY(sx) - th / 2 + 2.4)
            if i == 0 { streak.move(to: p) } else { streak.addLine(to: p) }
        }
        l.stroke(streak, with: .color(P.color(0xFFFFFF, 0.6)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
    }
}
