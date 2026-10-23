import SwiftUI

struct HealthAgeDialPetalMark: View {
    let size: Double
    let fills: [Color?]
    var tabShape = false

    var body: some View {
        Canvas { context, canvas in
            let scale = size / 36
            let center = CGPoint(x: canvas.width / 2, y: canvas.height / 2)
            let inner = (tabShape ? 4.2 : 5.4) * scale
            let outer = (tabShape ? 14.8 : 14.6) * scale
            let half = (tabShape ? 15.5 : 13.5) * .pi / 180
            for index in fills.indices {
                guard let fill = fills[index] else { continue }
                let rotation = Double(index) * .pi / 4
                func point(radius: Double, angle: Double) -> CGPoint {
                    CGPoint(x: center.x + sin(angle + rotation) * radius,
                            y: center.y - cos(angle + rotation) * radius)
                }
                var path = Path()
                path.move(to: point(radius: inner, angle: -half * 0.55))
                path.addLine(to: point(radius: outer, angle: -half))
                path.addArc(center: center, radius: outer,
                            startAngle: .radians(-.pi / 2 - half + rotation),
                            endAngle: .radians(-.pi / 2 + half + rotation), clockwise: false)
                path.addLine(to: point(radius: inner, angle: half * 0.55))
                path.closeSubpath()
                context.fill(path, with: .color(fill))
                context.stroke(path, with: .color(fill),
                               style: StrokeStyle(lineWidth: 2.2 * scale, lineJoin: .round))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    static let overviewFills: [Color?] = [
        Color(red: 0.263, green: 0.725, blue: 0.227), Color(red: 0.933, green: 0.353, blue: 0.173),
        Color(red: 0.898, green: 0.157, blue: 0.357), Color(red: 0.812, green: 0.176, blue: 0.753),
        nil, Color(red: 0.416, green: 0.247, blue: 0.816),
        Color(red: 0.118, green: 0.49, blue: 0.886), Color(red: 0.094, green: 0.682, blue: 0.612),
    ]
}

struct HealthAgeDialHeartGlyph: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvas in
            let sx = canvas.width / 24
            let sy = canvas.height / 24
            func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * sx, y: y * sy) }
            var path = Path()
            path.move(to: p(12, 21.2))
            path.addCurve(to: p(11.2, 20.9), control1: p(11.7, 21.2), control2: p(11.4, 21.1))
            path.addCurve(to: p(2.2, 8.9), control1: p(6.2, 16.8), control2: p(2.2, 13.4))
            path.addCurve(to: p(7.4, 3.6), control1: p(2.2, 5.9), control2: p(4.5, 3.6))
            path.addCurve(to: p(12, 6.2), control1: p(9.3, 3.6), control2: p(10.9, 4.6))
            path.addCurve(to: p(16.6, 3.6), control1: p(13.1, 4.6), control2: p(14.7, 3.6))
            path.addCurve(to: p(21.8, 8.9), control1: p(19.5, 3.6), control2: p(21.8, 5.9))
            path.addCurve(to: p(12.8, 20.9), control1: p(21.8, 13.4), control2: p(17.8, 16.8))
            path.addCurve(to: p(12, 21.2), control1: p(12.6, 21.1), control2: p(12.3, 21.2))
            path.closeSubpath()
            context.fill(path, with: .color(color))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct HealthAgeDialGridGlyph: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvas in
            let scale = canvas.width / 24
            for point in [CGPoint(x: 2.5, y: 2.5), CGPoint(x: 12.9, y: 2.5),
                          CGPoint(x: 2.5, y: 12.9), CGPoint(x: 12.9, y: 12.9)] {
                let rect = CGRect(x: point.x * scale, y: point.y * scale, width: 8.6 * scale, height: 8.6 * scale)
                context.fill(Path(roundedRect: rect, cornerRadius: 2 * scale), with: .color(color))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct HealthAgeDialBedGlyph: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvas in
            let scale = canvas.width / 24
            context.fill(Path(roundedRect: CGRect(x: 3.6 * scale, y: 4.4 * scale, width: 16.8 * scale, height: 8 * scale), cornerRadius: 1.8 * scale), with: .color(color))
            context.fill(Path(roundedRect: CGRect(x: 2 * scale, y: 11 * scale, width: 20 * scale, height: 6.2 * scale), cornerRadius: 1.6 * scale), with: .color(color))
            context.fill(Path(roundedRect: CGRect(x: 2 * scale, y: 17.2 * scale, width: 2.4 * scale, height: 2.6 * scale), cornerRadius: 0.8 * scale), with: .color(color))
            context.fill(Path(roundedRect: CGRect(x: 19.6 * scale, y: 17.2 * scale, width: 2.4 * scale, height: 2.6 * scale), cornerRadius: 0.8 * scale), with: .color(color))
            context.fill(Path(roundedRect: CGRect(x: 5.4 * scale, y: 7.5 * scale, width: 5.4 * scale, height: 3.5 * scale), cornerRadius: 1.4 * scale), with: .color(HealthAgeDialTheme.Colors.sleepTint))
            context.fill(Path(roundedRect: CGRect(x: 13.2 * scale, y: 7.5 * scale, width: 5.4 * scale, height: 3.5 * scale), cornerRadius: 1.4 * scale), with: .color(HealthAgeDialTheme.Colors.sleepTint))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct HealthAgeDialPulseGlyph: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvas in
            let scale = canvas.width / 24
            context.fill(Path(roundedRect: CGRect(x: 2 * scale, y: 4 * scale, width: 20 * scale, height: 16 * scale), cornerRadius: 4 * scale), with: .color(color))
            var pulse = Path()
            let points = [(4.6, 12.4), (8.2, 12.4), (9.7, 9.8), (11.7, 15.2), (13.6, 8.8), (15.2, 12.4), (19.4, 12.4)]
            for (index, point) in points.enumerated() {
                let value = CGPoint(x: point.0 * scale, y: point.1 * scale)
                index == 0 ? pulse.move(to: value) : pulse.addLine(to: value)
            }
            context.stroke(pulse, with: .color(.white), style: StrokeStyle(lineWidth: 1.9 * scale, lineCap: .round, lineJoin: .round))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct HealthAgeDialInfoGlyph: View {
    let size: Double

    var body: some View {
        ZStack {
            Circle().fill(HealthAgeDialTheme.Colors.info)
            Circle().fill(HealthAgeDialTheme.Colors.infoGlyph).frame(width: size * 0.124, height: size * 0.124).offset(y: -size * 0.224)
            Text("i")
                .font(.system(size: size * 0.52, weight: .black, design: .serif))
                .foregroundStyle(HealthAgeDialTheme.Colors.infoGlyph)
                .offset(y: size * 0.12)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct HealthAgeDialMiniDial: View {
    let size: Double

    var body: some View {
        let radius = (HealthAgeDialTheme.Layout.Chip.ring - 6) / 2
        let angle = HealthAgeDialTheme.restYears * HealthAgeDialTheme.Layout.Dial.degreesPerYear * .pi / 180
        ZStack {
            Circle()
                .trim(from: 0, to: (HealthAgeDialTheme.Layout.Dial.end - HealthAgeDialTheme.Layout.Dial.start) / 360)
                .stroke(AngularGradient(colors: HealthAgeDialTheme.ringStops.map(\.color), center: .center),
                        style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(HealthAgeDialTheme.Layout.Dial.start - 90))
                .frame(width: radius * 2, height: radius * 2)
                .opacity(0.78)
            Circle()
                .fill(Color(red: 74 / 255, green: 143 / 255, blue: 219 / 255))
                .frame(width: 6.6, height: 6.6)
                .offset(x: radius * sin(angle), y: -radius * cos(angle))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
