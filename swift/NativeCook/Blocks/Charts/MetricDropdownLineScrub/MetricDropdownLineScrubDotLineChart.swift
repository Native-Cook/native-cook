import SwiftUI

struct MetricDropdownLineScrubPlot { var left: Double; var top: Double; var width: Double; var height: Double }
private enum C {
    static let grid = Color(hex: 0x2c2c30), line = Color(hex: 0xf4f4f5), hairline = Color(hex: 0x4a4a50), halo = Color.white.opacity(0.14), ring = Color(hex: 0x17171a)
    static let lineWidth = 2.0, dot = 2.6, picked = 3.0, grown = 5.0, pickedHalo = 11.0, pickedRing = 1.5, dimmed = 0.35
}
func MetricDropdownLineScrubDrawChart(_ context: GraphicsContext, _ plot: MetricDropdownLineScrubPlot, _ grid: [Double], _ points: [Double], _ x: Double, _ y: Double, _ scrub: Double, _ hairlineTop: Double, _ opacity: Double) {
    var gridPath = Path(), line = Path(), dots = Path()
    for at in grid { let y = floor(plot.top + plot.height * (1 - at) + 0.5) + 0.5; gridPath.move(to: CGPoint(x: plot.left, y: y)); gridPath.addLine(to: CGPoint(x: plot.left + plot.width, y: y)) }
    context.stroke(gridPath, with: .color(C.grid), lineWidth: 1)
    for i in stride(from: 0, to: points.count, by: 2) { let p = CGPoint(x: points[i], y: points[i + 1]); if i == 0 { line.move(to: p) } else { line.addLine(to: p) }; dots.addEllipse(in: CGRect(x: p.x - C.dot, y: p.y - C.dot, width: C.dot * 2, height: C.dot * 2)) }
    var c = context; c.opacity *= opacity
    var hairline = Path(); hairline.move(to: CGPoint(x: x, y: hairlineTop)); hairline.addLine(to: CGPoint(x: x, y: plot.top + plot.height)); c.stroke(hairline, with: .color(C.hairline.opacity(scrub)), lineWidth: 1)
    let rest = 1 - (1 - C.dimmed) * scrub
    c.stroke(line, with: .color(C.line.opacity(rest)), style: StrokeStyle(lineWidth: C.lineWidth, lineCap: .round, lineJoin: .round)); c.fill(dots, with: .color(C.line.opacity(rest)))
    let radius = C.picked + (C.grown - C.picked) * scrub
    for (r, color) in [(C.pickedHalo * scrub, C.halo), (radius + C.pickedRing, C.ring), (radius, C.line)] { c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color.opacity(scrub))) }
}
