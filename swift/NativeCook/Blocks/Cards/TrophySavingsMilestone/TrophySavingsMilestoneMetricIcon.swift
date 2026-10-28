import SwiftUI

/// The three reference icons share one 32-point circle and one-point white strokes.
struct TrophySavingsMilestoneMetricIcon: View {
    var kind: Int
    var color: Color

    var body: some View {
        Canvas { context, _ in
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 32, height: 32)), with: .color(color))
            var path = Path()
            switch kind {
            case 0:
                path.addEllipse(in: CGRect(x: 10, y: 10, width: 12, height: 12))
                path.addEllipse(in: CGRect(x: 12.5, y: 12.5, width: 7, height: 7))
                path.move(to: CGPoint(x: 16, y: 16))
                path.addLine(to: CGPoint(x: 22.5, y: 9.5))
                path.move(to: CGPoint(x: 19.5, y: 9.5))
                path.addLine(to: CGPoint(x: 22.5, y: 9.5))
                path.addLine(to: CGPoint(x: 22.5, y: 12.5))
            case 1:
                path.addEllipse(in: CGRect(x: 10, y: 10, width: 12, height: 12))
                path.move(to: CGPoint(x: 16, y: 12.5))
                path.addLine(to: CGPoint(x: 16, y: 16.5))
                path.addLine(to: CGPoint(x: 18.5, y: 18))
            default:
                path.move(to: CGPoint(x: 11, y: 13))
                path.addLine(to: CGPoint(x: 21, y: 13))
                path.move(to: CGPoint(x: 18, y: 10))
                path.addLine(to: CGPoint(x: 21, y: 13))
                path.addLine(to: CGPoint(x: 18, y: 16))
                path.move(to: CGPoint(x: 21, y: 19))
                path.addLine(to: CGPoint(x: 11, y: 19))
                path.move(to: CGPoint(x: 14, y: 16))
                path.addLine(to: CGPoint(x: 11, y: 19))
                path.addLine(to: CGPoint(x: 14, y: 22))
            }
            context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: 1, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 32, height: 32)
        .accessibilityHidden(true)
    }
}
