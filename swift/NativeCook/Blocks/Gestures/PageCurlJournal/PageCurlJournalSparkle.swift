import SwiftUI

/// A four-point sparkle; the source tints it with four hues around its centre (JournalPages.tsx Sparkle).
struct PageCurlJournalSparkle: View {
    let size: CGFloat
    var colored = false

    var body: some View {
        let colors = PageCurlJournalTheme.Icon.sparkle
        Canvas { context, _ in
            let r = size / 2
            let path = Self.path(radius: r)
            guard colored else {
                context.fill(path, with: .color(PageCurlJournalTheme.Icon.markInk))
                return
            }
            let vertical = Gradient(stops: [
                .init(color: colors[0], location: 0),
                .init(color: Color(hex: 0xF2A2B8), location: 0.5),
                .init(color: colors[2], location: 1),
            ])
            context.fill(path, with: .linearGradient(vertical, startPoint: CGPoint(x: r, y: 0), endPoint: CGPoint(x: r, y: size)))
            let horizontal = Gradient(stops: [
                .init(color: colors[3].opacity(0.9), location: 0),
                .init(color: Color.white.opacity(0), location: 0.5),
                .init(color: colors[1].opacity(0.95), location: 1),
            ])
            context.fill(path, with: .linearGradient(horizontal, startPoint: CGPoint(x: 0, y: r), endPoint: CGPoint(x: size, y: r)))
        }
        .frame(width: size, height: size)
    }

    private static func path(radius r: CGFloat) -> Path {
        let k = r * 0.17
        return Path { path in
            path.move(to: CGPoint(x: r, y: 0))
            path.addQuadCurve(to: CGPoint(x: 2 * r, y: r), control: CGPoint(x: r + k, y: r - k))
            path.addQuadCurve(to: CGPoint(x: r, y: 2 * r), control: CGPoint(x: r + k, y: r + k))
            path.addQuadCurve(to: CGPoint(x: 0, y: r), control: CGPoint(x: r - k, y: r + k))
            path.addQuadCurve(to: CGPoint(x: r, y: 0), control: CGPoint(x: r - k, y: r - k))
            path.closeSubpath()
        }
    }
}
