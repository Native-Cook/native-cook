import SwiftUI

/// The Feather glyphs the block uses, drawn from their 24-unit outlines with the same 2-unit round stroke.
struct DragMultiSelectIcon: View {
    enum Kind {
        case chevronLeft, chevronDown, plus, calendar, arrowRight, trash, moreHorizontal, fileText
    }

    let kind: Kind
    let size: Double
    let color: Color

    var body: some View {
        DragMultiSelectIconShape(kind: kind)
            .stroke(color, style: StrokeStyle(lineWidth: size / 12, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
    }
}

private struct DragMultiSelectIconShape: Shape {
    let kind: DragMultiSelectIcon.Kind

    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 24
        func point(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: rect.minX + x * unit, y: rect.minY + y * unit)
        }
        var path = Path()
        func move(_ x: Double, _ y: Double) { path.move(to: point(x, y)) }
        func line(to x: Double, _ y: Double) { path.addLine(to: point(x, y)) }
        func segment(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
            move(x1, y1)
            line(to: x2, y2)
        }
        func polyline(_ points: [(Double, Double)]) {
            guard let first = points.first else { return }
            move(first.0, first.1)
            for next in points.dropFirst() { line(to: next.0, next.1) }
        }
        /// A radius-2 corner: the curve cuts the sharp corner `(cx, cy)` on its way to `(x, y)`.
        func corner(_ cx: Double, _ cy: Double, to x: Double, _ y: Double) {
            path.addQuadCurve(to: point(x, y), control: point(cx, cy))
        }
        switch kind {
        case .chevronLeft:
            polyline([(15, 18), (9, 12), (15, 6)])
        case .chevronDown:
            polyline([(6, 9), (12, 15), (18, 9)])
        case .plus:
            segment(12, 5, 12, 19)
            segment(5, 12, 19, 12)
        case .calendar:
            path.addRoundedRect(
                in: CGRect(x: rect.minX + 3 * unit, y: rect.minY + 4 * unit, width: 18 * unit, height: 18 * unit),
                cornerSize: CGSize(width: 2 * unit, height: 2 * unit)
            )
            segment(16, 2, 16, 6)
            segment(8, 2, 8, 6)
            segment(3, 10, 21, 10)
        case .arrowRight:
            segment(5, 12, 19, 12)
            polyline([(12, 5), (19, 12), (12, 19)])
        case .trash:
            polyline([(3, 6), (5, 6), (21, 6)])
            // M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2
            move(19, 6)
            line(to: 19, 20)
            corner(19, 22, to: 17, 22)
            line(to: 7, 22)
            corner(5, 22, to: 5, 20)
            line(to: 5, 6)
            move(8, 6)
            line(to: 8, 4)
            corner(8, 2, to: 10, 2)
            line(to: 14, 2)
            corner(16, 2, to: 16, 4)
            line(to: 16, 6)
            segment(10, 11, 10, 17)
            segment(14, 11, 14, 17)
        case .moreHorizontal:
            for x in [12.0, 19.0, 5.0] {
                path.addEllipse(in: CGRect(x: rect.minX + (x - 1) * unit, y: rect.minY + 11 * unit, width: 2 * unit, height: 2 * unit))
            }
        case .fileText:
            // M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z
            move(14, 2)
            line(to: 6, 2)
            corner(4, 2, to: 4, 4)
            line(to: 4, 20)
            corner(4, 22, to: 6, 22)
            line(to: 18, 22)
            corner(20, 22, to: 20, 20)
            line(to: 20, 8)
            path.closeSubpath()
            polyline([(14, 2), (14, 8), (20, 8)])
            segment(16, 13, 8, 13)
            segment(16, 17, 8, 17)
            polyline([(10, 9), (9, 9), (8, 9)])
        }
        return path
    }
}
