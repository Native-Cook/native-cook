import SwiftUI
import UIKit

/// The parts of the barometer that move, drawn every frame over the baked art in one Canvas.
enum BarometerNeedleChartFrame {
    private typealias M = BarometerNeedleChartMotion
    private typealias P = BarometerNeedleChartPaint

    static let readoutUI = P.sans(54, .bold)
    static let tipUI = P.sans(12, .semibold)
    static let readout = Font.system(size: 54, weight: .bold)
    static let unit = Font.system(size: 17, weight: .medium)
    static let word = Font.system(size: 22, weight: .semibold)
    static let small = Font.system(size: 13, weight: .medium)
    static let tip = Font.system(size: 12, weight: .semibold)

    /// The widest digit's advance in the readout font.
    static let digitW: CGFloat = (0...9).map { P.width("\($0)", readoutUI) }.max() ?? 30
    static let digitWidths: [CGFloat] = (0...9).map { P.width("\($0)", readoutUI) }

    /// Draws text with its baseline at `baseline`; `anchorX` 0 = left edge at x, 1 = right edge at x.
    @discardableResult
    static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color,
                     x: CGFloat, baseline: CGFloat, anchorX: CGFloat = 0) -> CGFloat {
        let r = ctx.resolve(Text(s).font(font).foregroundColor(color))
        let big = CGSize(width: 10_000, height: 10_000)
        let size = r.measure(in: big)
        let b = r.firstBaseline(in: big)
        ctx.draw(r, at: CGPoint(x: x - size.width * anchorX, y: baseline - b), anchor: .topLeading)
        return size.width
    }

    /// An odometer: each column rolls from the one below it, so a held value rests on whole digits.
    /// Returns the drawn width.
    private static func odometer(_ ctx: GraphicsContext, _ value: Double, x: CGFloat, base: CGFloat, lineH: CGFloat, color: UInt32) -> CGFloat {
        let digitW = Self.digitW
        let r = M.softRound(value)
        var pos = [r, 0, 0, 0]
        for k in 1..<4 {
            let below = pos[k - 1]
            let m = (below.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
            pos[k] = floor(below / 10) + max(0, m - 9)
        }
        // The thousands column only shows from 1000 up; the others slide over to close the gap.
        let lead = M.clamp(pos[3], 0, 1)
        let shift = -digitW * CGFloat(1 - lead)
        let top = base - lineH * 0.78
        var clip = ctx
        clip.clip(to: Path(CGRect(x: x - 4, y: top - 4, width: digitW * 4 + 8, height: lineH + 8)))
        for k in 0..<4 {
            let col = 3 - k
            let cx = x + shift + CGFloat(col) * digitW
            let d = (pos[k].truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
            let i = Int(floor(d))
            let f = d - Double(i)
            let alphaCol = k == 3 ? lead : 1
            for j in 0..<2 {
                let digit = (i + j) % 10
                if k == 3 && digit == 0 { continue }
                let off = CGFloat(Double(j) - f) * lineH
                let a = alphaCol * (1 - min(1, abs(Double(j) - f)))
                if a <= 0.01 { continue }
                let w = digitWidths[digit]
                text(clip, "\(digit)", readout, P.color(color, a), x: cx + (digitW - w) / 2, baseline: base + off)
            }
        }
        return digitW * 4 + shift
    }

    /// Two words rolling: index i shown, rolling up to i + 1 as the fraction grows. Right-aligned at x.
    private static func rollWords(_ ctx: GraphicsContext, _ words: [String], _ index: Double, x: CGFloat, base: CGFloat,
                                  font: Font, color: UInt32, travel: CGFloat) {
        let i = Int(M.clamp(floor(index), 0, Double(words.count - 1)))
        let f = index - Double(i)
        for j in 0..<2 {
            guard i + j < words.count else { continue }
            let a = 1 - min(1, abs(Double(j) - f))
            if a <= 0.01 { continue }
            text(ctx, words[i + j], font, P.color(color, a), x: x, baseline: base + CGFloat(Double(j) - f) * travel, anchorX: 1)
        }
    }

    private static func header(_ ctx: GraphicsContext, _ g: BarometerNeedleChartGeo, hpa: Double) {
        let base = g.readoutBase
        let width = odometer(ctx, hpa, x: 24, base: base, lineH: 60, color: 0xF4ECD8)
        text(ctx, "hPa", unit, P.color(0xE9E0CC, 0.6), x: 24 + width + 8, baseline: base)
        // The forecast word and the trend against the set hand, rolling as they change.
        let right = g.w - 24
        var clip = ctx
        clip.clip(to: Path(CGRect(x: right - 160, y: base - 52, width: 160, height: 64)))
        rollWords(clip, M.words, M.wordIndex(hpa), x: right, base: base - 22, font: word, color: 0xF4ECD8, travel: 26)
        let trend = M.trendIndex(hpa)
        let trendColor: UInt32 = trend > 1.5 ? 0xA9D5C9 : trend < 0.5 ? 0xE39A7F : 0xD7D0BF
        rollWords(clip, M.trends, trend, x: right, base: base + 1, font: small, color: trendColor, travel: 18)
        // Trend arrow left of the trend word, turning with the change.
        let delta = M.clamp((hpa - M.nowHpa) / 12, -1, 1)
        let label = M.trends[Int(floor(trend + 0.5))]
        let tw = ctx.resolve(Text(label).font(small)).measure(in: CGSize(width: 1000, height: 100)).width
        var arrowCtx = ctx
        arrowCtx.translateBy(x: right - tw - 12, y: base - 4)
        arrowCtx.rotate(by: .degrees(-delta * 45))
        var arrow = Path()
        arrow.move(to: CGPoint(x: -5, y: 0))
        arrow.addLine(to: CGPoint(x: 4, y: 0))
        arrow.move(to: CGPoint(x: 1, y: -3))
        arrow.addLine(to: CGPoint(x: 4.5, y: 0))
        arrow.addLine(to: CGPoint(x: 1, y: 3))
        arrowCtx.stroke(arrow, with: .color(P.color(0xE9E0CC, 0.75)),
                        style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
    }

    private static let needleShading = Gradient(stops: [
        .init(color: P.color(0x0C1626), location: 0),
        .init(color: P.color(0x3A5680), location: 0.5),
        .init(color: P.color(0x101B2E), location: 1),
    ])

    private static let capShading = Gradient(stops: [
        .init(color: P.color(0xFDE9B6), location: 0),
        .init(color: P.color(0xBF913F), location: 0.6),
        .init(color: P.color(0x6A4818), location: 1),
    ])

    static func needle(_ ctx: GraphicsContext, _ g: BarometerNeedleChartGeo, angle: Double) {
        let len = g.rf * 0.84
        let tail = g.rf * 0.22
        var body = Path()
        body.move(to: CGPoint(x: -2.2, y: tail))
        body.addLine(to: CGPoint(x: -0.7, y: -len * 0.86))
        body.addLine(to: CGPoint(x: -3.4, y: -len * 0.86))
        body.addLine(to: CGPoint(x: 0, y: -len))
        body.addLine(to: CGPoint(x: 3.4, y: -len * 0.86))
        body.addLine(to: CGPoint(x: 0.7, y: -len * 0.86))
        body.addLine(to: CGPoint(x: 2.2, y: tail))
        body.closeSubpath()
        let ring = Path(ellipseIn: CGRect(x: -7, y: tail - 3 - 7, width: 14, height: 14))
        // Shadow on the face first (the needle stands a few points off it).
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 2.2))
            l.translateBy(x: g.cx + 2.5, y: g.cy + 4.5)
            l.rotate(by: .degrees(angle))
            l.fill(body, with: .color(P.color(0x2A1C0A, 0.28)))
            l.stroke(ring, with: .color(P.color(0x2A1C0A, 0.22)), lineWidth: 2.4)
        }
        var n = ctx
        n.translateBy(x: g.cx, y: g.cy)
        n.rotate(by: .degrees(angle))
        // Blued steel: dark at the edges, a cool sheen down the middle.
        n.fill(body, with: .linearGradient(needleShading, startPoint: CGPoint(x: -3.4, y: 0), endPoint: CGPoint(x: 3.4, y: 0)))
        n.stroke(ring, with: .color(P.color(0x15233A)), lineWidth: 2.4)
        n.stroke(ring, with: .color(P.color(0x5A7AA6, 0.5)), lineWidth: 0.6)
        // Brass arbor cap.
        ctx.fill(
            Path(ellipseIn: CGRect(x: g.cx - 6.5, y: g.cy - 6.5, width: 13, height: 13)),
            with: .radialGradient(capShading, center: CGPoint(x: g.cx - 2, y: g.cy - 2), startRadius: 0, endRadius: 8)
        )
    }

    private static func cursor(_ ctx: GraphicsContext, _ g: BarometerNeedleChartGeo, hour: Double) {
        let h = M.clamp(hour, -24, 24)
        let x = g.plotX(h)
        let hpa = M.pressureAt(h)
        let y = g.plotY(hpa)
        let plot = g.plot
        var line = Path()
        line.move(to: CGPoint(x: x, y: plot.minY - 4))
        line.addLine(to: CGPoint(x: x, y: plot.maxY))
        ctx.stroke(line, with: .color(P.color(0xF4ECD8, 0.35)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        let color: UInt32 = h > 0 ? 0x9FC6C8 : BarometerNeedleChartArt.amber
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 4))
            l.fill(Path(ellipseIn: CGRect(x: x - 10, y: y - 10, width: 20, height: 20)), with: .color(P.color(color, 0.22)))
        }
        let dot = Path(ellipseIn: CGRect(x: x - 5.5, y: y - 5.5, width: 11, height: 11))
        ctx.fill(dot, with: .color(P.color(0x0F1C22)))
        ctx.stroke(dot, with: .color(P.color(color)), lineWidth: 2.2)
        // Tooltip above the dot (below it near the top), kept inside the plot.
        let label = "\(M.timeLabel(h)) · \(Int(floor(hpa + 0.5))) hPa"
        let tw = P.width(label, tipUI)
        let bw = tw + 20
        let bx = CGFloat(M.clamp(Double(x - bw / 2), Double(plot.minX - 4), Double(plot.maxX + 40 - bw)))
        let above = y - 38 >= plot.minY - 12
        let by = above ? y - 38 : y + 14
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 6))
            l.fill(Path(roundedRect: CGRect(x: bx, y: by + 3, width: bw, height: 24), cornerRadius: 12),
                   with: .color(P.color(0x000000, 0.35)))
        }
        ctx.fill(Path(roundedRect: CGRect(x: bx, y: by, width: bw, height: 24), cornerRadius: 12), with: .color(P.color(0xF2EAD6)))
        text(ctx, label, tip, P.color(0x1D2A2F), x: bx + 10, baseline: by + 16)
    }

    /// Header readout and chart cursor, over everything else.
    static func overlay(_ ctx: GraphicsContext, _ g: BarometerNeedleChartGeo, hour: Double, hpaNeedle: Double) {
        header(ctx, g, hpa: hpaNeedle)
        cursor(ctx, g, hour: hour)
    }
}
