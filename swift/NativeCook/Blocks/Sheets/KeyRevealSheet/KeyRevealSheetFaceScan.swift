import SwiftUI

/// A face-scan icon whose corners draw themselves and a line sweeps across while `scan` runs 0 to 1.
struct KeyRevealSheetFaceScan: View {
    var scan: Double
    var color: Color

    // The four corners of a face-scan frame in an 18 x 18 box, each about 12 points of line.
    private static let corners: [Path] = {
        func corner(_ from: CGPoint, _ turn: CGPoint, _ to: CGPoint) -> Path {
            var path = Path()
            path.move(to: from)
            path.addArc(tangent1End: turn, tangent2End: to, radius: 2)
            path.addLine(to: to)
            return path
        }
        return [
            corner(CGPoint(x: 1, y: 6), CGPoint(x: 1, y: 1), CGPoint(x: 6, y: 1)),
            corner(CGPoint(x: 12, y: 1), CGPoint(x: 17, y: 1), CGPoint(x: 17, y: 6)),
            corner(CGPoint(x: 17, y: 12), CGPoint(x: 17, y: 17), CGPoint(x: 12, y: 17)),
            corner(CGPoint(x: 6, y: 17), CGPoint(x: 1, y: 17), CGPoint(x: 1, y: 12)),
        ]
    }()
    private static let cornerLength = 12.0
    private static let size = 18.0
    // Scan 0 to 1: the corners draw in over the first part, a line sweeps across the rest.
    private static let drawBy = 0.4
    private static let lineFrom = 4.0
    private static let lineTo = 14.0

    var body: some View {
        Canvas { context, _ in
            let stroke = StrokeStyle(lineWidth: 1.8, lineCap: .round, dash: [Self.cornerLength, Self.cornerLength], dashPhase: Self.cornerLength * (1 - min(1, scan / Self.drawBy)))
            for path in Self.corners { context.stroke(path, with: .color(color), style: stroke) }
            let sweep = min(1, max(0, (scan - 0.15) / 0.8))
            let x = Self.lineFrom + (Self.lineTo - Self.lineFrom) * sweep
            var line = Path()
            line.move(to: CGPoint(x: x, y: 5))
            line.addLine(to: CGPoint(x: x, y: 13))
            context.opacity = scan >= 1 ? 0 : min(1, sweep * 6)
            context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        }
        .frame(width: Self.size, height: Self.size)
    }
}
