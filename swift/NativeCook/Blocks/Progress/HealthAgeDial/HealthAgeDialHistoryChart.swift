import SwiftUI

struct HealthAgeDialHistoryChart: View {
    static let width = HealthAgeDialTheme.Layout.Chart.baseRight - HealthAgeDialTheme.Layout.Chart.left
    static let height = HealthAgeDialTheme.Layout.Chart.bottom - HealthAgeDialTheme.Layout.Chart.top + 4

    let progress: Double
    let opacity: Double

    var body: some View {
        Canvas { context, _ in
            let chart = HealthAgeDialTheme.Layout.Chart.self
            let gridWidth = chart.right - chart.left
            let gridHeight = chart.bottom - chart.top
            let baseY = chart.base - chart.top
            let points = HealthAgeDialTheme.historyPoints.map { CGPoint(x: $0.x, y: baseY + $0.y) }
            guard let last = points.last else { return }

            for column in 0...chart.columns {
                let x = gridWidth * Double(column) / Double(chart.columns) + 0.5
                var line = Path()
                line.move(to: CGPoint(x: x, y: 0))
                line.addLine(to: CGPoint(x: x, y: gridHeight))
                context.stroke(line, with: .color(HealthAgeDialTheme.Colors.grid),
                               style: StrokeStyle(lineWidth: 1, dash: [1.6, 3.4]))
            }
            var upper = Path()
            upper.move(to: CGPoint(x: 0, y: 0.5))
            upper.addLine(to: CGPoint(x: gridWidth, y: 0.5))
            context.stroke(upper, with: .color(HealthAgeDialTheme.Colors.grid),
                           style: StrokeStyle(lineWidth: 1, dash: [1.6, 3.4]))
            var lower = Path()
            lower.move(to: CGPoint(x: 0, y: gridHeight))
            lower.addLine(to: CGPoint(x: gridWidth, y: gridHeight))
            context.stroke(lower, with: .color(HealthAgeDialTheme.Colors.grid), lineWidth: 1)

            var line = Path()
            for (index, point) in points.enumerated() {
                if index == 0 { line.move(to: point) } else { line.addLine(to: point) }
            }
            var area = line
            area.addLine(to: CGPoint(x: last.x, y: baseY))
            area.addLine(to: CGPoint(x: 0, y: baseY))
            area.closeSubpath()

            let headX = HealthAgeDialMotion.clamp(progress) * last.x
            if progress > 0 {
                var clipped = context
                let clipWidth = headX + 4 + (progress >= 1 ? 8 : 0)
                clipped.clip(to: Path(CGRect(x: -4, y: 0, width: clipWidth, height: Self.height)))
                clipped.opacity = opacity
                let lowest = Double(points.map(\.y).max() ?? CGFloat(baseY))
                clipped.fill(area, with: .linearGradient(
                    Gradient(colors: [HealthAgeDialTheme.Colors.fillTop,
                                      HealthAgeDialTheme.Colors.fillBottom]),
                    startPoint: CGPoint(x: 0, y: baseY), endPoint: CGPoint(x: 0, y: lowest)
                ))
                clipped.stroke(line, with: .linearGradient(
                    Gradient(colors: [HealthAgeDialTheme.Colors.line, HealthAgeDialTheme.Colors.lineEnd]),
                    startPoint: .zero, endPoint: CGPoint(x: last.x, y: 0)
                ), style: StrokeStyle(lineWidth: chart.lineWidth, lineCap: .round, lineJoin: .round))
            }

            var baseline = Path()
            baseline.move(to: CGPoint(x: 0, y: baseY))
            baseline.addLine(to: CGPoint(x: Self.width, y: baseY))
            context.stroke(baseline, with: .color(HealthAgeDialTheme.Colors.baseline), lineWidth: 1.3)

            let dotOpacity = HealthAgeDialMotion.clamp(progress / 0.06) * opacity
            if dotOpacity > 0 {
                let y = lineY(at: headX, points: points)
                context.opacity = dotOpacity
                context.fill(Path(ellipseIn: CGRect(x: headX - chart.dotRadius - chart.dotRing,
                                                     y: y - chart.dotRadius - chart.dotRing,
                                                     width: 2 * (chart.dotRadius + chart.dotRing),
                                                     height: 2 * (chart.dotRadius + chart.dotRing))), with: .color(.white))
                context.fill(Path(ellipseIn: CGRect(x: headX - chart.dotRadius, y: y - chart.dotRadius,
                                                     width: 2 * chart.dotRadius, height: 2 * chart.dotRadius)),
                             with: .color(HealthAgeDialTheme.Colors.chartDot))
            }
        }
        .frame(width: Self.width, height: Self.height, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func lineY(at x: Double, points: [CGPoint]) -> Double {
        guard let first = points.first, let last = points.last else { return 0 }
        if x <= first.x { return first.y }
        for index in 1..<points.count where x <= points[index].x {
            let previous = points[index - 1]
            let next = points[index]
            return previous.y + (next.y - previous.y) * (x - previous.x) / (next.x - previous.x)
        }
        return last.y
    }
}
