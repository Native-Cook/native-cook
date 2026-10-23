import SwiftUI

/// The heart of `Heart` in `ReactionMarks.tsx`: a `react-native-svg` view with
/// `viewBox="0 0 32 30"` that fills its square box (the default `xMidYMid meet`), the heart path
/// filled with a bounding-box gradient and a short rounded highlight stroked over its upper left.
struct DonationFanCarouselHeartArtwork: View {
    private typealias Theme = DonationFanCarouselTheme

    /// The heart path's bounding box in the 32 x 30 view box (`M16 28C12.8 23.8 2 17.5 ...`).
    private static let boundsX: CGFloat = 2
    private static let boundsY: CGFloat = 2
    private static let boundsWidth: CGFloat = 28
    private static let boundsHeight: CGFloat = 26

    var body: some View {
        Canvas { context, size in
            let viewBoxWidth = Theme.Hearts.viewBoxWidth
            let viewBoxHeight = Theme.Hearts.viewBoxHeight
            // `preserveAspectRatio="xMidYMid meet"`.
            let fit = min(size.width / viewBoxWidth, size.height / viewBoxHeight)
            let originX = (size.width - viewBoxWidth * fit) / 2
            let originY = (size.height - viewBoxHeight * fit) / 2

            // `<LinearGradient x1="0" y1="0" x2="1" y2="1">` is in the path's bounding-box units, so
            // the heart is drawn in a unit square that is stretched over the box.
            var filled = context
            filled.translateBy(
                x: originX + Self.boundsX * fit,
                y: originY + Self.boundsY * fit
            )
            filled.scaleBy(x: Self.boundsWidth * fit, y: Self.boundsHeight * fit)
            filled.fill(
                Self.heart { x, y in
                    CGPoint(x: (x - Self.boundsX) / Self.boundsWidth, y: (y - Self.boundsY) / Self.boundsHeight)
                },
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Theme.Colors.heartLight, location: 0),
                        .init(color: Theme.Colors.heart, location: 0.42),
                        .init(color: Theme.Colors.heartDark, location: 1),
                    ]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: 1, y: 1)
                )
            )

            var stroked = context
            stroked.translateBy(x: originX, y: originY)
            stroked.scaleBy(x: fit, y: fit)
            stroked.stroke(
                Self.highlight,
                with: .color(Theme.Colors.heartHighlight),
                style: StrokeStyle(lineWidth: Theme.Hearts.highlightWidth, lineCap: .round)
            )
        }
    }

    /// `M16 28C12.8 23.8 2 17.5 2 9.4C2 4.9 5.2 2 9.1 2C12 2 14.3 3.7 16 6.2C17.7 3.7 20 2 22.9 2C26.8 2
    /// 30 4.9 30 9.4C30 17.5 19.2 23.8 16 28Z`, every point passed through `map`.
    private static func heart(_ map: (CGFloat, CGFloat) -> CGPoint) -> Path {
        var path = Path()
        path.move(to: map(16, 28))
        path.addCurve(to: map(2, 9.4), control1: map(12.8, 23.8), control2: map(2, 17.5))
        path.addCurve(to: map(9.1, 2), control1: map(2, 4.9), control2: map(5.2, 2))
        path.addCurve(to: map(16, 6.2), control1: map(12, 2), control2: map(14.3, 3.7))
        path.addCurve(to: map(22.9, 2), control1: map(17.7, 3.7), control2: map(20, 2))
        path.addCurve(to: map(30, 9.4), control1: map(26.8, 2), control2: map(30, 4.9))
        path.addCurve(to: map(16, 28), control1: map(30, 17.5), control2: map(19.2, 23.8))
        path.closeSubpath()
        return path
    }

    /// `M7.3 6.8C9 4.9 11.5 5.1 13.1 6.7`, stroked with width 1.7 and round caps, no fill.
    private static let highlight: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 7.3, y: 6.8))
        path.addCurve(
            to: CGPoint(x: 13.1, y: 6.7),
            control1: CGPoint(x: 9, y: 4.9),
            control2: CGPoint(x: 11.5, y: 5.1)
        )
        return path
    }()
}
