import SwiftUI

let LineToCandlesSwitchLineGreen = Color(hex: 0x1fa463)
private typealias M = LineToCandlesSwitchMotion
private enum C {
    static let up = Color(hex: 0x00c076), down = Color(hex: 0xf6465d), cursor = Color(hex: 0x71767b)
    static let line = 1.6, wick = 1.0, dot = 4.5, dash: [CGFloat] = [3, 4]
}
func LineToCandlesSwitchCandleProgress(_ a: LineToCandlesSwitchFrame, _ b: LineToCandlesSwitchFrame, _ x: Double, _ time: Double) -> Double {
    if a.lineAlpha > 0.5 && b.lineAlpha < 0.5 { return M.ease((time - M.fade - x * M.spread) / M.grow) }
    if a.lineAlpha < 0.5 && b.lineAlpha > 0.5 { return M.ease((time - x * M.spread) / M.grow) }
    return M.ease(time / M.move)
}
func LineToCandlesSwitchLineAlphaAt(_ a: LineToCandlesSwitchFrame, _ b: LineToCandlesSwitchFrame, _ time: Double, _ length: Double) -> Double {
    if a.lineAlpha > b.lineAlpha { return a.lineAlpha * (1 - M.clamp(time / M.fade)) }
    if a.lineAlpha < b.lineAlpha { return a.lineAlpha + (b.lineAlpha - a.lineAlpha) * M.clamp((time - (length - M.fade)) / M.fade) }
    return b.lineAlpha
}
func LineToCandlesSwitchFrameAt(_ a: LineToCandlesSwitchFrame, _ b: LineToCandlesSwitchFrame, _ time: Double, _ length: Double) -> LineToCandlesSwitchFrame {
    if time >= length { return b }
    let e = M.ease(time / length)
    var f = LineToCandlesSwitchFrame(o: [], h: [], l: [], c: [], x: [], w: [], line: [], low: a.low + (b.low - a.low) * e, high: a.high + (b.high - a.high) * e, lineAlpha: LineToCandlesSwitchLineAlphaAt(a, b, time, length), candles: max(a.candles, b.candles))
    for i in 0..<M.points {
        let u = LineToCandlesSwitchCandleProgress(a, b, a.x[i], time)
        f.o.append(a.o[i] + (b.o[i] - a.o[i]) * u); f.h.append(a.h[i] + (b.h[i] - a.h[i]) * u); f.l.append(a.l[i] + (b.l[i] - a.l[i]) * u); f.c.append(a.c[i] + (b.c[i] - a.c[i]) * u); f.x.append(a.x[i] + (b.x[i] - a.x[i]) * u); f.w.append(a.w[i] + (b.w[i] - a.w[i]) * u); f.line.append(a.line[i] + (b.line[i] - a.line[i]) * e)
    }
    return f
}
func LineToCandlesSwitchDrawChart(_ context: GraphicsContext, _ a: LineToCandlesSwitchFrame, _ b: LineToCandlesSwitchFrame, _ time: Double, _ length: Double, _ width: Double, _ height: Double, _ pad: Double, _ cursor: Double, _ held: Double) {
    let f = LineToCandlesSwitchFrameAt(a, b, time, length), delta = f.high - f.low, span = delta == 0 ? 1 : delta
    func y(_ value: Double) -> Double { pad + (1 - (value - f.low) / span) * (height - 2 * pad) }
    var line = Path(), area = Path(), upBodies = Path(), downBodies = Path(), upWicks = Path(), downWicks = Path()
    for i in 0..<M.points { let x = (Double(i) + 0.5) / Double(M.points) * width, point = CGPoint(x: x, y: y(f.line[i])); if i == 0 { line.move(to: point); area.move(to: CGPoint(x: x, y: height)) } else { line.addLine(to: point) }; area.addLine(to: point) }
    area.addLine(to: CGPoint(x: (Double(M.points) - 0.5) / Double(M.points) * width, y: height)); area.closeSubpath()
    if f.candles > 0 {
        let floor = a.candles > 0 && b.candles > 0 ? 1.0 : 0.0
        for i in 0..<M.points {
            let top = y(max(f.o[i], f.c[i])), bottom = max(top + floor, y(min(f.o[i], f.c[i]))), wickTop = y(f.h[i]), wickBottom = y(f.l[i])
            if wickBottom - wickTop < 0.3 { continue }
            let x = f.x[i] * width, half = f.w[i] * width / 2, wick = CGRect(x: x - C.wick / 2, y: wickTop, width: C.wick, height: wickBottom - wickTop), body = CGRect(x: x - half, y: top, width: half * 2, height: bottom - top)
            if f.c[i] >= f.o[i] { upWicks.addRect(wick); if bottom - top >= 0.3 { upBodies.addRect(body) } }
            else { downWicks.addRect(wick); if bottom - top >= 0.3 { downBodies.addRect(body) } }
        }
    }
    var c = context; c.clip(to: Path(CGRect(x: 0, y: 0, width: width, height: height)))
    var lines = c; lines.opacity *= f.lineAlpha
    lines.fill(area, with: .linearGradient(Gradient(stops: [.init(color: .rgba(31, 164, 99, 0.45), location: 0), .init(color: .rgba(31, 164, 99, 0.12), location: 0.6), .init(color: .rgba(31, 164, 99, 0), location: 1)]), startPoint: CGPoint(x: 0, y: pad), endPoint: CGPoint(x: 0, y: height)))
    lines.stroke(line, with: .color(LineToCandlesSwitchLineGreen), style: StrokeStyle(lineWidth: C.line, lineCap: .round, lineJoin: .round))
    c.fill(upWicks, with: .color(C.up)); c.fill(downWicks, with: .color(C.down)); c.fill(upBodies, with: .color(C.up)); c.fill(downBodies, with: .color(C.down))
    var dash = Path(); dash.move(to: CGPoint(x: cursor * width, y: 0)); dash.addLine(to: CGPoint(x: cursor * width, y: height)); c.stroke(dash, with: .color(C.cursor.opacity(held)), style: StrokeStyle(lineWidth: 1, dash: C.dash))
    let point = CGPoint(x: cursor * width, y: y(M.lineAt(f.line, cursor)))
    c.fill(Path(ellipseIn: CGRect(x: point.x - C.dot, y: point.y - C.dot, width: C.dot * 2, height: C.dot * 2)), with: .color(LineToCandlesSwitchLineGreen.opacity(held * f.lineAlpha)))
}
