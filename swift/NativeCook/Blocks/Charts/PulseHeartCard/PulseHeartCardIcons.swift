import SwiftUI

/// The pin's small marks, drawn in code (the SVG icons of PulseHeartCard.tsx).
private typealias Colors = PulseHeartCardTheme.Colors

/// An arc as a polyline, so the winding convention of `Path.addArc` never matters. Angles in radians,
/// measured clockwise on screen from 3 o'clock.
private func pulseHeartCardArc(centre: CGPoint, radius: Double, from start: Double, sweep: Double) -> Path {
    var path = Path()
    let steps = max(2, Int(abs(sweep) / 0.05))
    for step in 0...steps {
        let angle = start + sweep * Double(step) / Double(steps)
        let point = CGPoint(x: centre.x + radius * cos(angle), y: centre.y + radius * sin(angle))
        if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
    }
    return path
}

/// Material-style heart on a 24 x 24 grid ("M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 ...").
struct PulseHeartCardHeartShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: pt(12, 21.35))
        path.addLine(to: pt(10.55, 20))
        path.addCurve(to: pt(2, 8.5), control1: pt(5.4, 15.36), control2: pt(2, 12.28))
        path.addCurve(to: pt(7.5, 3), control1: pt(2, 5.42), control2: pt(4.42, 3))
        path.addCurve(to: pt(12, 5.09), control1: pt(9.24, 3), control2: pt(10.91, 3.81))
        // A6.04 6.04 0 0 1 16.5 3: a clockwise arc of radius 6.04 from (12, 5.09) to (16.5, 3), in one cubic.
        let r = 6.04
        let p0 = (x: 12.0, y: 5.09)
        let p1 = (x: 16.5, y: 3.0)
        let mid = (x: (p0.x + p1.x) / 2, y: (p0.y + p1.y) / 2)
        let half = (x: (p1.x - p0.x) / 2, y: (p1.y - p0.y) / 2)
        let halfLength = hypot(half.x, half.y)
        let h = (r * r - halfLength * halfLength).squareRoot()
        // Centre below the chord (the lobe is convex upwards).
        let centre = (x: mid.x + h * (-half.y) / halfLength, y: mid.y + h * half.x / halfLength)
        let a0 = atan2(p0.y - centre.y, p0.x - centre.x)
        var a1 = atan2(p1.y - centre.y, p1.x - centre.x)
        if a1 < a0 { a1 += 2 * .pi }
        let k = 4.0 / 3.0 * tan((a1 - a0) / 4)
        path.addCurve(
            to: pt(p1.x, p1.y),
            control1: pt(p0.x - k * r * sin(a0), p0.y + k * r * cos(a0)),
            control2: pt(p1.x + k * r * sin(a1), p1.y - k * r * cos(a1))
        )
        path.addCurve(to: pt(22, 8.5), control1: pt(19.58, 3), control2: pt(22, 5.42))
        path.addCurve(to: pt(13.45, 20), control1: pt(22, 12.28), control2: pt(18.6, 15.36))
        path.addLine(to: pt(12, 21.35))
        path.closeSubpath()
        return path
    }
}

struct PulseHeartCardHeartIcon: View {
    let size: Double
    var color: Color = Colors.heart

    var body: some View {
        PulseHeartCardHeartShape()
            .fill(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Three activity rings.
struct PulseHeartCardFitnessIcon: View {
    let scale: Double

    var body: some View {
        let size = PulseHeartCardTheme.Layout.chipIcon * scale
        let rings: [(radius: Double, progress: Double, color: Color)] = [
            (12.8 * scale, 0.82, Colors.ringRed),
            (9 * scale, 0.92, Colors.ringYellow),
            (5.2 * scale, 0.88, Colors.metricGreen),
        ]
        Canvas { context, canvasSize in
            let centre = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
            let style = StrokeStyle(lineWidth: 3.3 * scale, lineCap: .round)
            for ring in rings {
                context.stroke(pulseHeartCardArc(centre: centre, radius: ring.radius, from: 0, sweep: 2 * .pi),
                               with: .color(ring.color.opacity(0.18)), style: style)
            }
            for ring in rings {
                context.stroke(pulseHeartCardArc(centre: centre, radius: ring.radius, from: -.pi / 2, sweep: 2 * .pi * ring.progress),
                               with: .color(ring.color), style: style)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Four dots on a diamond.
struct PulseHeartCardVitalsIcon: View {
    let scale: Double

    var body: some View {
        let size = PulseHeartCardTheme.Layout.chipIcon * scale
        Canvas { context, canvasSize in
            let c = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
            let points = [
                CGPoint(x: c.x, y: c.y - 8 * scale),
                CGPoint(x: c.x - 8 * scale, y: c.y),
                CGPoint(x: c.x + 8 * scale, y: c.y),
                CGPoint(x: c.x, y: c.y + 8 * scale),
            ]
            for (index, point) in points.enumerated() {
                let ring = Path(ellipseIn: CGRect(x: point.x - 5 * scale, y: point.y - 5 * scale, width: 10 * scale, height: 10 * scale))
                context.stroke(ring, with: .color(index == 0 ? Colors.metricPink : Colors.metricBlue), lineWidth: 3 * scale)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct PulseHeartCardChevron: View {
    let scale: Double
    var color: Color = Colors.faint

    var body: some View {
        Canvas { context, _ in
            var path = Path()
            path.move(to: CGPoint(x: 5 * scale, y: 3 * scale))
            path.addLine(to: CGPoint(x: 10 * scale, y: 9 * scale))
            path.addLine(to: CGPoint(x: 5 * scale, y: 15 * scale))
            context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.8 * scale, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 15 * scale, height: 18 * scale)
        .accessibilityHidden(true)
    }
}

/// The pin's Longevity mark: a dark aperture of eight petals around a small hole.
struct PulseHeartCardApertureIcon: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvasSize in
            let s = canvasSize.width / 24
            context.fill(Path(ellipseIn: CGRect(x: (12 - 10.4) * s, y: (12 - 10.4) * s, width: 20.8 * s, height: 20.8 * s)), with: .color(color))
            for index in 0..<8 {
                let angle = Double(index) * .pi / 4 + .pi / 8
                var line = Path()
                line.move(to: CGPoint(x: 12 * s, y: 12 * s))
                line.addLine(to: CGPoint(x: (12 + 11 * cos(angle)) * s, y: (12 + 11 * sin(angle)) * s))
                context.stroke(line, with: .color(Colors.card), style: StrokeStyle(lineWidth: 1.7 * s, lineCap: .round))
            }
            context.fill(Path(ellipseIn: CGRect(x: 8 * s, y: 8 * s, width: 8 * s, height: 8 * s)), with: .color(Colors.card))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct PulseHeartCardBrowseIcon: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvasSize in
            let s = canvasSize.width / 24
            for row in 0..<2 {
                for column in 0..<2 {
                    let rect = CGRect(x: (1.5 + Double(column) * 11) * s, y: (1.5 + Double(row) * 11) * s, width: 10 * s, height: 10 * s)
                    context.fill(Path(roundedRect: rect, cornerRadius: 2.3 * s), with: .color(color))
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
