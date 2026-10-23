import SwiftUI

/// The Feather glyphs the block uses (the React Native block draws them from the Feather font):
/// 24 x 24 grid, 2 pt round strokes.
enum TiltSwipeActionsFeather {
    case shoppingCart, home, coffee, navigation, film, activity, bookOpen, plusSquare
    case plus, minus, arrowUpRight, flag, trash2, rotateCcw

    fileprivate enum Element {
        case path(String)
        case line(Double, Double, Double, Double)
        case polyline([Double], closed: Bool)
        case rect(Double, Double, Double, Double, radius: Double)
        case circle(Double, Double, Double)
    }

    fileprivate var elements: [Element] {
        switch self {
        case .shoppingCart:
            return [
                .circle(9, 21, 1),
                .circle(20, 21, 1),
                .path("M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"),
            ]
        case .home:
            return [
                .path("M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
                .polyline([9, 22, 9, 12, 15, 12, 15, 22], closed: false),
            ]
        case .coffee:
            return [
                .path("M18 8h1a4 4 0 0 1 0 8h-1"),
                .path("M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"),
                .line(6, 1, 6, 4),
                .line(10, 1, 10, 4),
                .line(14, 1, 14, 4),
            ]
        case .navigation:
            return [.polyline([3, 11, 22, 2, 13, 21, 11, 13, 3, 11], closed: true)]
        case .film:
            return [
                .rect(2, 2, 20, 20, radius: 2.18),
                .line(7, 2, 7, 22),
                .line(17, 2, 17, 22),
                .line(2, 12, 22, 12),
                .line(2, 7, 7, 7),
                .line(2, 17, 7, 17),
                .line(17, 17, 22, 17),
                .line(17, 7, 22, 7),
            ]
        case .activity:
            return [.polyline([22, 12, 18, 12, 15, 21, 9, 3, 6, 12, 2, 12], closed: false)]
        case .bookOpen:
            return [
                .path("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"),
                .path("M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"),
            ]
        case .plusSquare:
            return [
                .rect(3, 3, 18, 18, radius: 2),
                .line(12, 8, 12, 16),
                .line(8, 12, 16, 12),
            ]
        case .plus:
            return [.line(12, 5, 12, 19), .line(5, 12, 19, 12)]
        case .minus:
            return [.line(5, 12, 19, 12)]
        case .arrowUpRight:
            return [
                .line(7, 17, 17, 7),
                .polyline([7, 7, 17, 7, 17, 17], closed: false),
            ]
        case .flag:
            return [
                .path("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"),
                .line(4, 22, 4, 15),
            ]
        case .trash2:
            return [
                .polyline([3, 6, 5, 6, 21, 6], closed: false),
                .path("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
                .line(10, 11, 10, 17),
                .line(14, 11, 14, 17),
            ]
        case .rotateCcw:
            return [
                .polyline([1, 4, 1, 10, 7, 10], closed: false),
                .path("M3.51 15a9 9 0 1 0 2.13-9.36L1 10"),
            ]
        }
    }
}

private struct TiltSwipeActionsFeatherShape: Shape {
    let icon: TiltSwipeActionsFeather

    func path(in rect: CGRect) -> Path {
        var path = Path()
        for element in icon.elements {
            switch element {
            case let .path(data):
                path.addPath(TiltSwipeActionsSVGPath.path(data))
            case let .line(x1, y1, x2, y2):
                path.move(to: CGPoint(x: x1, y: y1))
                path.addLine(to: CGPoint(x: x2, y: y2))
            case let .polyline(values, closed):
                for index in stride(from: 0, to: values.count, by: 2) {
                    let point = CGPoint(x: values[index], y: values[index + 1])
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
                if closed { path.closeSubpath() }
            case let .rect(x, y, width, height, radius):
                path.addRoundedRect(in: CGRect(x: x, y: y, width: width, height: height), cornerSize: CGSize(width: radius, height: radius))
            case let .circle(cx, cy, radius):
                path.addEllipse(in: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2))
            }
        }
        let scale = min(rect.width, rect.height) / 24
        return path.applying(CGAffineTransform(scaleX: scale, y: scale))
    }
}

/// A Feather glyph at `size` in `color`; the stroke scales with the glyph like the font's does.
struct TiltSwipeActionsFeatherIcon: View {
    let icon: TiltSwipeActionsFeather
    let size: CGFloat
    let color: Color

    var body: some View {
        TiltSwipeActionsFeatherShape(icon: icon)
            .stroke(color, style: StrokeStyle(lineWidth: 2 * size / 24, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
    }
}

/// The three tab glyphs on a 26 x 26 grid: a pie, a wallet with a clasp, a person with a badge.
struct TiltSwipeActionsTabIcon: View {
    enum Kind {
        case pie, wallet, person
    }

    let kind: Kind
    let color: Color
    var size: CGFloat = 26

    var body: some View {
        outline.stroke(color, style: StrokeStyle(lineWidth: 2.5 * size / 26, lineCap: .round, lineJoin: .round))
        .frame(width: size, height: size)
    }

    private var outline: Path {
        var path = Path()
        switch kind {
        case .pie:
            path.addPath(TiltSwipeActionsSVGPath.path("M20.72 14.38A9 9 0 1 1 10.97 4.63"))
            path.addPath(TiltSwipeActionsSVGPath.path("M12.4 13V3.2A9.8 9.8 0 0 1 22.2 13Z"))
        case .wallet:
            var wallet = Path()
            wallet.addPath(TiltSwipeActionsSVGPath.path("M15.5 4H6.5A3.5 3.5 0 0 0 3 7.5V20.1A3.5 3.5 0 0 0 6.5 23.6H20.7A3.5 3.5 0 0 0 24.2 20.1V11.8"))
            wallet.addPath(TiltSwipeActionsSVGPath.path("M3 11.6C9 14.6 17 14.6 24.2 11.8"))
            wallet.addEllipse(in: CGRect(x: 18.2, y: 3.2, width: 4.6, height: 4.6))
            path.addPath(wallet.applying(CGAffineTransform(translationX: 0, y: -3.4)))
        case .person:
            path.addEllipse(in: CGRect(x: 11.9, y: 3.8, width: 7.2, height: 7.2))
            path.addPath(TiltSwipeActionsSVGPath.path("M7.6 21.4H23A2.6 2.6 0 0 0 25.6 18.8A3 3 0 0 0 22.6 15.8C21 15.4 19.6 13.6 19 11"))
        }
        return path.applying(CGAffineTransform(scaleX: size / 26, y: size / 26))
    }
}
