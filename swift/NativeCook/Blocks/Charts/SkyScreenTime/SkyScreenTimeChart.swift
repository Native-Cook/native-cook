import SwiftUI

struct SkyScreenTimeChart: View {
    var hours: [SkyScreenTimeHour]
    var frame: SkyScreenTimeFrame
    var scale: Double

    var body: some View {
        Canvas { context, _ in
            let s = scale
            context.translateBy(x: 0, y: 14 * s)
            let stride = 292.0 / 24
            let colors = [Color(hex: 0x858796), Color(hex: 0x57BF71), Color(hex: 0xF0B13F)]
            for index in 0..<24 {
                let x = (Double(index) * stride + 2) * s
                let track = CGRect(x: x, y: 0, width: 8 * s, height: 86 * s)
                context.fill(Path(roundedRect: track, cornerRadius: 3 * s), with: .color(Color(hex: 0xF5F5F7)))
                let hour = hours[index]
                let height = min(30, hour.minutes) / 30 * 86 * s
                guard height > 0 else { continue }
                let rect = CGRect(x: x, y: 86 * s - height, width: 8 * s, height: height)
                var layer = context
                layer.opacity = frame.selection > 0.05 && abs(Double(index) - frame.hour) > 0.65 ? 1 - 0.65 * frame.selection : 1
                layer.clip(to: Path(roundedRect: rect, cornerRadius: 3 * s))
                var bottom = 86 * s
                for (offset, minutes) in [hour.neutral, hour.productive, hour.distracted].enumerated() {
                    let segment = minutes / 30 * 86 * s
                    layer.fill(Path(CGRect(x: x, y: bottom - segment, width: 8 * s, height: segment)), with: .color(colors[offset]))
                    bottom -= segment
                }
            }
            for y in [0.0, 43, 86] {
                var line = Path()
                line.move(to: CGPoint(x: 0, y: y * s))
                line.addLine(to: CGPoint(x: 292 * s, y: y * s))
                context.stroke(line, with: .color(Color(hex: 0xE0E1E5)), lineWidth: 0.65 * s)
            }
            for (index, label) in ["30min", "15min", "0s"].enumerated() {
                context.draw(Text(label).font(.system(size: 10 * s)).foregroundColor(Color(hex: 0x8A8C94)),
                             at: CGPoint(x: 297 * s, y: Double(index) * 43 * s), anchor: .leading)
            }
            for (index, label) in ["12 AM", "6 AM", "12 PM", "6 PM"].enumerated() {
                let x = Double(index) * 73 * s
                var tick = Path()
                tick.move(to: CGPoint(x: x, y: 86 * s))
                tick.addLine(to: CGPoint(x: x, y: 102 * s))
                context.stroke(tick, with: .color(Color(hex: 0xD3D5DC)), lineWidth: 0.7 * s)
                context.draw(Text(label).font(.system(size: 10 * s)).foregroundColor(Color(hex: 0x8A8C94)),
                             at: CGPoint(x: x + 3 * s, y: 97 * s), anchor: .leading)
            }
            if frame.selection > 0.001 {
                var marker = context
                marker.opacity = frame.selection
                let x = (frame.hour * stride + 6) * s
                var line = Path()
                line.move(to: CGPoint(x: x, y: -8 * s))
                line.addLine(to: CGPoint(x: x, y: 86 * s))
                marker.stroke(line, with: .color(Color(hex: 0x3988DE)), style: StrokeStyle(lineWidth: 1.2 * s, dash: [3 * s, 3 * s]))
                marker.fill(Path(ellipseIn: CGRect(x: x - 3 * s, y: -12 * s, width: 6 * s, height: 6 * s)), with: .color(Color(hex: 0x3988DE)))
            }
        }
    }
}

struct SkyScreenTimeFace: View {
    var kind: Int

    var body: some View {
        Canvas { context, size in
            let s = size.width / 28
            let rect = CGRect(origin: .zero, size: size)
            let shape = Path(roundedRect: rect.insetBy(dx: 0.5 * s, dy: 0.5 * s), cornerRadius: 8 * s)
            context.fill(shape, with: .color(kind == 0 ? Color(hex: 0x60CA6A) : .white.opacity(0.04)))
            context.stroke(shape, with: .color(.white.opacity(kind == 0 ? 0.15 : 0.45)), lineWidth: s)
            if kind != 0 {
                var clipped = context
                clipped.clip(to: shape)
                clipped.fill(Path(CGRect(x: 0, y: 26 * s, width: 28 * s, height: 2 * s)),
                             with: .color(kind == 1 ? Color(hex: 0x8A98A9) : Color(hex: 0xF0B13F)))
            }
            var face = Path()
            for x in [9.0, 19] {
                face.move(to: CGPoint(x: x * s, y: 8 * s))
                face.addLine(to: CGPoint(x: x * s, y: 10 * s))
            }
            face.move(to: CGPoint(x: 14 * s, y: 9 * s))
            face.addLine(to: CGPoint(x: 14 * s, y: 14 * s))
            face.addQuadCurve(to: CGPoint(x: 12 * s, y: 15 * s), control: CGPoint(x: 14 * s, y: 16 * s))
            face.move(to: CGPoint(x: 9 * s, y: (kind == 2 ? 21 : 19) * s))
            face.addQuadCurve(to: CGPoint(x: 19 * s, y: (kind == 2 ? 21 : 19) * s),
                              control: CGPoint(x: 14 * s, y: (kind == 0 ? 24 : (kind == 1 ? 19 : 15)) * s))
            context.stroke(face, with: .color(.white), style: StrokeStyle(lineWidth: 1.7 * s, lineCap: .round, lineJoin: .round))
        }
        .accessibilityHidden(true)
    }
}
