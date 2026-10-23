import SwiftUI

/// Line icons in a 24-point box: open strokes, closed shapes and circles.
struct FilmBoxUnboxingDrawing {
    var lines: [[Double]] = []
    var shapes: [[Double]] = []
    var circles: [[Double]] = []

    static let arrow = FilmBoxUnboxingDrawing(lines: [[5, 12, 19, 12], [13, 6, 19, 12, 13, 18]])
    static let timer = FilmBoxUnboxingDrawing(lines: [[12, 13.5, 12, 9.5], [9.5, 3, 14.5, 3], [12, 3, 12, 6]], circles: [[12, 13.5, 7.5]])
    static let flashOff = FilmBoxUnboxingDrawing(lines: [[4, 4, 20, 20]], shapes: [[13, 3, 6, 13.5, 11.5, 13.5, 10.5, 21, 18, 10.5, 12.5, 10.5]])
    static let flashOn = FilmBoxUnboxingDrawing(shapes: [[13, 3, 6, 13.5, 11.5, 13.5, 10.5, 21, 18, 10.5, 12.5, 10.5]])
    static let download = FilmBoxUnboxingDrawing(lines: [[12, 4, 12, 15], [7.5, 10.5, 12, 15, 16.5, 10.5], [5, 19.5, 19, 19.5]])
    static let share = FilmBoxUnboxingDrawing(lines: [[12, 3, 12, 14], [8, 7, 12, 3, 16, 7], [8.5, 10, 6, 10, 6, 20.5, 18, 20.5, 18, 10, 15.5, 10]])
}

/// `fill` fills the closed shapes (the flash when it is on).
struct FilmBoxUnboxingIcon: View {
    let drawing: FilmBoxUnboxingDrawing
    let size: Double
    let color: Color
    let stroke: Double
    var fill: Color?

    var body: some View {
        Canvas { context, _ in
            let k = size / 24
            func point(_ list: [Double], _ i: Int) -> CGPoint { CGPoint(x: list[i] * k, y: list[i + 1] * k) }
            let style = StrokeStyle(lineWidth: stroke * k, lineCap: .round, lineJoin: .round)
            for c in drawing.circles {
                let r = c[2] * k
                context.stroke(Path(ellipseIn: CGRect(x: c[0] * k - r, y: c[1] * k - r, width: r * 2, height: r * 2)), with: .color(color), style: style)
            }
            for shape in drawing.shapes {
                var path = Path()
                path.move(to: point(shape, 0))
                for i in stride(from: 2, to: shape.count, by: 2) { path.addLine(to: point(shape, i)) }
                path.closeSubpath()
                if let fill { context.fill(path, with: .color(fill)) }
                context.stroke(path, with: .color(color), style: style)
            }
            for line in drawing.lines {
                var path = Path()
                path.move(to: point(line, 0))
                for i in stride(from: 2, to: line.count, by: 2) { path.addLine(to: point(line, i)) }
                context.stroke(path, with: .color(color), style: style)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
