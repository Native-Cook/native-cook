import SwiftUI

struct BreathVolumeLungsControls: View {
    let layout: BreathVolumeLungsLayout
    let fontScale: Double
    let reset: () -> Void
    let finish: () -> Void
    typealias R = BreathVolumeLungsResources
    var body: some View {
        let s = layout.scale, h = 58 * s * layout.k
        HStack(spacing: 4 * s) {
            Button(action: reset) {
                Canvas { c, _ in
                    c.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 60 * s, height: h), cornerRadius: 30 * s), with: .color(R.color(0x191B38)))
                    var icon = c
                    icon.translateBy(x: 17 * s, y: h / 2 - 13 * s + 3 * s)
                    icon.scaleBy(x: 26 * s / 28, y: 26 * s / 28)
                    icon.stroke(Self.refresh, with: .color(R.color(0xF7F7FA)), style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                }
                .frame(width: 60 * s, height: h)
            }
            .accessibilityLabel("Reset breath test")
            Button(action: finish) {
                Canvas { c, _ in
                    c.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 194 * s, height: h), cornerRadius: 29 * s), with: .color(R.color(0x1B1C37)))
                    let size = 15 * s * fontScale, font = R.font(size)
                    R.text(c, "Finish Test", x: 40 * s, top: (h - font.lineHeight) / 2,
                           width: 106 * s, size: size)
                    let cx = 164 * s, cy = h / 2 + 3 * s
                    c.fill(Path(ellipseIn: CGRect(x: cx - 18 * s, y: cy - 18 * s, width: 36 * s, height: 36 * s)), with: .color(R.color(0xE9E9F0)))
                    var x = Path()
                    // The reference's 18 pt SVG has a 20-unit view box.
                    let a = 5.4 * s
                    x.move(to: CGPoint(x: cx - a, y: cy - a)); x.addLine(to: CGPoint(x: cx + a, y: cy + a))
                    x.move(to: CGPoint(x: cx + a, y: cy - a)); x.addLine(to: CGPoint(x: cx - a, y: cy + a))
                    c.stroke(x, with: .color(R.color(0x1F1F2B)), style: StrokeStyle(lineWidth: 1.4 * 0.9 * s, lineCap: .round))
                }
                .frame(width: 194 * s, height: h)
            }
            .accessibilityLabel("Finish breath test")
        }
        .buttonStyle(BreathVolumeLungsButtonStyle())
        .offset(x: 73 * s, y: layout.buttonY)
    }
    // SVG: M23 11 A9 9 0 0 0 7 7 L4 10 M4 4 v6 h6 M5 17 a9 9 0 0 0 16 4 l3-3 m0 6 v-6 h-6
    private static let refresh: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 23, y: 11))
        // Endpoint-parameterized SVG arcs, converted without replacing the source's geometry.
        addArc(&p, start: CGPoint(x: 23, y: 11), end: CGPoint(x: 7, y: 7), radius: 9)
        p.addLine(to: CGPoint(x: 4, y: 10))
        p.move(to: CGPoint(x: 4, y: 4)); p.addLine(to: CGPoint(x: 4, y: 10)); p.addLine(to: CGPoint(x: 10, y: 10))
        p.move(to: CGPoint(x: 5, y: 17))
        addArc(&p, start: CGPoint(x: 5, y: 17), end: CGPoint(x: 21, y: 21), radius: 9)
        p.addLine(to: CGPoint(x: 24, y: 18))
        p.move(to: CGPoint(x: 24, y: 24)); p.addLine(to: CGPoint(x: 24, y: 18)); p.addLine(to: CGPoint(x: 18, y: 18))
        return p
    }()
    private static func addArc(_ p: inout Path, start: CGPoint, end: CGPoint, radius: Double) {
        let dx = (start.x - end.x) / 2, dy = (start.y - end.y) / 2
        let f = sqrt(max(0, (radius * radius - dx * dx - dy * dy) / (dx * dx + dy * dy)))
        let cx = (start.x + end.x) / 2 - f * dy, cy = (start.y + end.y) / 2 + f * dx
        let a = atan2(start.y - cy, start.x - cx), b = atan2(end.y - cy, end.x - cx)
        p.addArc(center: CGPoint(x: cx, y: cy), radius: radius, startAngle: .radians(a), endAngle: .radians(b), clockwise: true)
    }
}
