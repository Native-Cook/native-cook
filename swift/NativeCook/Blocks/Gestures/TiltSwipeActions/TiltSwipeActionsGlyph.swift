import SwiftUI

enum TiltSwipeActionsGlyphName: String {
    case bag, bolt, dining, tram, ticket, dumbbell, users, flag, trash, undo, plus, home, chart, card
}

private struct TiltSwipeActionsGlyphShape: Shape {
    let name: TiltSwipeActionsGlyphName

    func path(in rect: CGRect) -> Path {
        var path = Path()
        func svg(_ data: String) { path.addPath(TiltSwipeActionsSVGPath.path(data)) }
        func rounded(_ x: CGFloat, _ y: CGFloat, _ width: CGFloat, _ height: CGFloat, _ radius: CGFloat) {
            path.addRoundedRect(in: CGRect(x: x, y: y, width: width, height: height), cornerSize: CGSize(width: radius, height: radius))
        }
        switch name {
        case .bag:
            svg("M5.5 8.5h13l-.95 10.9a1.7 1.7 0 0 1-1.7 1.6H8.15a1.7 1.7 0 0 1-1.7-1.6z")
            svg("M9 11V7a3 3 0 0 1 6 0v4")
        case .bolt:
            svg("M13.4 2.8 5.6 13.1h5.7l-1.1 8.1 7.8-10.3h-5.7z")
        case .dining:
            svg("M6.5 3v5a2.5 2.5 0 0 0 5 0V3M9 3v4.5M9 10.5V21M17.5 21V3c-2.1 1-3.5 3.4-3.5 6.4V14h3.5")
        case .tram:
            rounded(6, 4.5, 12, 12.5, 3)
            svg("M6 11h12M9.6 20.5 8.4 17M14.4 20.5l1.2-3.5M10 4.5V3h4v1.5M9.25 14h.01M14.75 14h.01")
        case .ticket:
            svg("M3.5 7.6A1.6 1.6 0 0 1 5.1 6h13.8a1.6 1.6 0 0 1 1.6 1.6v2.1a2.4 2.4 0 0 0 0 4.6v2.1a1.6 1.6 0 0 1-1.6 1.6H5.1a1.6 1.6 0 0 1-1.6-1.6v-2.1a2.4 2.4 0 0 0 0-4.6zM14.5 6.5v1.5M14.5 11.25v1.5M14.5 16v1.5")
        case .dumbbell:
            rounded(5, 6.5, 3, 11, 1.2)
            rounded(16, 6.5, 3, 11, 1.2)
            svg("M8 12h8M2.75 10v4M21.25 10v4")
        case .users:
            svg("M15.5 20v-1.5a3.5 3.5 0 0 0-3.5-3.5H6.5A3.5 3.5 0 0 0 3 18.5V20M9.25 11.5a3.25 3.25 0 1 0 0-6.5 3.25 3.25 0 0 0 0 6.5zM21 20v-1.5a3.5 3.5 0 0 0-2.6-3.4M15.4 5.1a3.25 3.25 0 0 1 0 6.3")
        case .flag:
            svg("M5.5 21V4M5.5 4.6c4.2-2 7.4 2 13 0v9c-5.6 2-8.8-2-13 0")
        case .trash:
            svg("M4 6.5h16M9.5 6.5v-2h5v2M6.5 6.5l.9 12.6A1.6 1.6 0 0 0 9 20.5h6a1.6 1.6 0 0 0 1.6-1.4l.9-12.6M10.2 10.5v6M13.8 10.5v6")
        case .undo:
            svg("M8.5 14 4 9.5 8.5 5M4 9.5h10.5a5 5 0 0 1 0 10H11")
        case .plus:
            svg("M12 5.5v13M5.5 12h13")
        case .home:
            svg("M4 10.4 12 4l8 6.4v8.6a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19z")
        case .chart:
            svg("M4 20.5h16M7.5 16.5v-5M12 16.5V6.5M16.5 16.5v-8")
        case .card:
            rounded(3, 5.5, 18, 13, 2.5)
            svg("M3 10h18M7 15h3.5")
        }
        let scale = min(rect.width, rect.height) / 24
        return path.applying(CGAffineTransform(scaleX: scale, y: scale))
    }
}

struct TiltSwipeActionsGlyph: View {
    let name: TiltSwipeActionsGlyphName
    let color: Color
    var size: CGFloat = 20
    var stroke: CGFloat = 1.5

    var body: some View {
        TiltSwipeActionsGlyphShape(name: name)
            .stroke(color, style: StrokeStyle(lineWidth: stroke * size / 24, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
    }
}
