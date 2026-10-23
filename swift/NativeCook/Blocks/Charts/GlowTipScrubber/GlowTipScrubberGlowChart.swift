import SwiftUI

let GlowTipScrubberGreen = Color(hex: 0x3ddc97)
private enum C {
    static let lit = Color(hex: 0xb8ffe3), rule = Color(hex: 0x5a5a5e), cursor = Color.white
    static let line = 2.0, litLine = 2.6, litReach = 26.0, bloomLine = 7.0, bloomBlur = 5.0, halo = 28.0, glowBlur = 6.0, glowAlpha = 0.35, dim = 0.55, dots: [CGFloat] = [1.5, 5]
}
func GlowTipScrubberDrawChart(_ context: GraphicsContext, _ range: GlowTipScrubberRange, _ width: Double, _ height: Double, _ pad: Double, _ f: GlowTipScrubberFilm) {
    let low = range.prices.min()!, delta = range.prices.max()! - low, span = delta == 0 ? 1 : delta, plot = height - pad * 2
    func y(_ p: Double) -> Double { pad + (1 - (p - low) / span) * plot }
    var path = Path()
    for (i, price) in range.prices.enumerated() { let point = CGPoint(x: Double(i) / Double(range.prices.count - 1) * width, y: y(price)); if i == 0 { path.move(to: point) } else { path.addLine(to: point) } }
    let x = GlowTipScrubberMotion.clamp(f.tip) * width, point = CGPoint(x: x, y: y(GlowTipScrubberMotion.priceAt(range.prices, f.tip)))
    var c = context; c.clip(to: Path(CGRect(x: 0, y: 0, width: width, height: height)))
    var rule = Path(); rule.move(to: CGPoint(x: 0, y: y(range.prices[0]))); rule.addLine(to: CGPoint(x: width, y: y(range.prices[0])))
    c.stroke(rule, with: .color(C.rule.opacity(f.marks)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: C.dots))
    var cursor = Path(); cursor.move(to: CGPoint(x: x, y: 0)); cursor.addLine(to: CGPoint(x: x, y: height)); c.stroke(cursor, with: .color(C.cursor.opacity(f.held)), lineWidth: 1)
    var revealed = c; revealed.clip(to: Path(CGRect(x: -C.halo, y: 0, width: C.halo + f.reveal * width, height: height)))
    let base = f.shown * (1 - C.dim * f.held)
    revealed.drawLayer { glow in glow.addFilter(.blur(radius: C.glowBlur)); glow.stroke(path, with: .color(GlowTipScrubberGreen.opacity(base * C.glowAlpha)), style: StrokeStyle(lineWidth: C.line, lineCap: .round, lineJoin: .round)) }
    revealed.stroke(path, with: .color(GlowTipScrubberGreen.opacity(base)), style: StrokeStyle(lineWidth: C.line, lineCap: .round, lineJoin: .round))
    let from = CGPoint(x: x - C.litReach, y: 0), to = CGPoint(x: x + C.litReach, y: 0)
    revealed.drawLayer { bloom in
        bloom.opacity *= f.lit; bloom.addFilter(.blur(radius: C.bloomBlur))
        bloom.stroke(path, with: .linearGradient(Gradient(colors: [GlowTipScrubberGreen.opacity(0), GlowTipScrubberGreen.opacity(0.9), GlowTipScrubberGreen.opacity(0)]), startPoint: from, endPoint: to), style: StrokeStyle(lineWidth: C.bloomLine, lineCap: .round, lineJoin: .round))
    }
    revealed.opacity *= f.lit
    revealed.stroke(path, with: .linearGradient(Gradient(colors: [C.lit.opacity(0), C.lit, C.lit.opacity(0)]), startPoint: from, endPoint: to), style: StrokeStyle(lineWidth: C.litLine, lineCap: .round, lineJoin: .round))
    c.fill(Path(ellipseIn: CGRect(x: point.x - C.halo, y: point.y - C.halo, width: C.halo * 2, height: C.halo * 2)), with: .radialGradient(Gradient(stops: [.init(color: .rgba(225, 255, 242, 0.6 * f.lit), location: 0), .init(color: .rgba(61, 220, 151, 0.28 * f.lit), location: 0.45), .init(color: .rgba(61, 220, 151, 0), location: 1)]), center: point, startRadius: 0, endRadius: C.halo))
}
