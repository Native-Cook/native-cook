import SwiftUI

struct SendMoneyCardsGlyph: View {
    enum Kind {
        case arrowUpLeft
        case arrowDownLeft
        case arrowUpRight
        case ledger(incoming: Bool)
        case plus
        case close
        case grid
    }

    let kind: Kind
    let color: Color
    let size: Double
    var stroke = 1.6

    var body: some View {
        Canvas { context, canvasSize in
            let viewBox = kindViewBox
            let scale = min(canvasSize.width / viewBox, canvasSize.height / viewBox)
            var path = Path()
            func point(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: x * scale, y: y * scale)
            }
            switch kind {
            case .arrowUpLeft:
                path.move(to: point(15, 15)); path.addLine(to: point(4, 4))
                path.move(to: point(4, 4)); path.addLine(to: point(4, 12))
                path.move(to: point(4, 4)); path.addLine(to: point(12, 4))
            case .arrowDownLeft:
                path.move(to: point(15, 4)); path.addLine(to: point(4, 15))
                path.move(to: point(4, 15)); path.addLine(to: point(4, 7))
                path.move(to: point(4, 15)); path.addLine(to: point(12, 15))
            case .arrowUpRight:
                path.move(to: point(4, 15)); path.addLine(to: point(15, 4))
                path.move(to: point(15, 4)); path.addLine(to: point(7, 4))
                path.move(to: point(15, 4)); path.addLine(to: point(15, 12))
            case let .ledger(incoming):
                if incoming {
                    path.move(to: point(15, 3)); path.addLine(to: point(5, 13))
                    path.move(to: point(5, 13)); path.addLine(to: point(5, 6))
                    path.move(to: point(5, 13)); path.addLine(to: point(12, 13))
                } else {
                    path.move(to: point(15, 14)); path.addLine(to: point(5, 4))
                    path.move(to: point(5, 4)); path.addLine(to: point(5, 11))
                    path.move(to: point(5, 4)); path.addLine(to: point(12, 4))
                }
                path.move(to: point(3, 17.5)); path.addLine(to: point(17, 17.5))
            case .plus:
                path.move(to: point(9, 2)); path.addLine(to: point(9, 16))
                path.move(to: point(2, 9)); path.addLine(to: point(16, 9))
            case .close:
                path.move(to: point(2, 2)); path.addLine(to: point(14, 14))
                path.move(to: point(14, 2)); path.addLine(to: point(2, 14))
            case .grid:
                for row in 0..<3 {
                    for column in 0..<3 {
                        let center = point(3 + Double(column) * 6, 3 + Double(row) * 6)
                        let rect = CGRect(x: center.x - 1.9 * scale, y: center.y - 1.9 * scale,
                                          width: 3.8 * scale, height: 3.8 * scale)
                        context.fill(Path(ellipseIn: rect), with: .color(color))
                    }
                }
                return
            }
            context.stroke(path, with: .color(color),
                           style: StrokeStyle(lineWidth: stroke * scale, lineCap: .round, lineJoin: .round))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private var kindViewBox: Double {
        switch kind {
        case .ledger: 20
        case .close: 16
        case .plus, .grid: 18
        case .arrowUpLeft, .arrowDownLeft, .arrowUpRight: 19
        }
    }
}
