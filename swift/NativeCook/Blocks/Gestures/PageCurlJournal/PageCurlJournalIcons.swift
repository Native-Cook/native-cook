import SwiftUI

/// A point on an icon's own grid, scaled to the icon's size.
private func point(_ x: CGFloat, _ y: CGFloat, _ k: CGFloat) -> CGPoint {
    CGPoint(x: x * k, y: y * k)
}

/// The preface's two row icons and the mark's curling corner, drawn on the same grids as the source's SVGs.
enum PageCurlJournalIcons {
    /// A round badge with a check (28 × 28 grid).
    struct CheckBadge: View {
        let size: CGFloat

        var body: some View {
            let k = size / 28
            let colors = PageCurlJournalTheme.Icon.check
            Canvas { context, _ in
                let badge = Path(ellipseIn: CGRect(x: 0, y: 0, width: 28 * k, height: 28 * k))
                let gradient = Gradient(stops: [
                    .init(color: colors[0], location: 0),
                    .init(color: colors[1], location: 0.55),
                    .init(color: colors[2], location: 1),
                ])
                context.fill(badge, with: .linearGradient(gradient, startPoint: point(24, 3, k), endPoint: point(5, 26, k)))
                let check = Path { path in
                    path.move(to: point(8.2, 14.6, k))
                    path.addLine(to: point(12.4, 18.6, k))
                    path.addLine(to: point(20, 9.6, k))
                }
                context.stroke(check, with: .color(.white), style: StrokeStyle(lineWidth: 2.6 * k, lineCap: .round, lineJoin: .round))
            }
            .frame(width: size, height: size)
        }
    }

    /// Four bars on a base line (30 × 30 grid).
    struct BarsIcon: View {
        let size: CGFloat

        private static let bars: [(x: CGFloat, h: CGFloat, o: Double)] = [
            (3, 15, 0.95), (9.6, 20, 0.85), (16.2, 16, 0.7), (22.8, 10, 0.5),
        ]

        var body: some View {
            let k = size / 30
            let colors = PageCurlJournalTheme.Icon.bars
            Canvas { context, _ in
                let gradient = Gradient(colors: colors)
                let shading = GraphicsContext.Shading.linearGradient(gradient, startPoint: point(0, 3, k), endPoint: point(0, 25, k))
                for bar in Self.bars {
                    var layer = context
                    layer.opacity = bar.o
                    let rect = CGRect(x: bar.x * k, y: (24 - bar.h) * k, width: 4.6 * k, height: bar.h * k)
                    layer.fill(Path(roundedRect: rect, cornerRadius: 1 * k), with: shading)
                }
                let base = CGRect(x: 1 * k, y: 26.2 * k, width: 28 * k, height: 2.2 * k)
                context.fill(Path(roundedRect: base, cornerRadius: 1.1 * k), with: .color(PageCurlJournalTheme.Icon.barsBase))
            }
            .frame(width: size, height: size)
        }
    }

    /// The mark's bottom-left corner curling up like a page (100 × 100 grid).
    struct MarkFlap: View {
        let size: CGFloat

        var body: some View {
            let k = size / 100
            let icon = PageCurlJournalTheme.Icon.self
            Canvas { context, _ in
                let flap = Path { path in
                    path.move(to: point(0, 60, k))
                    path.addCurve(to: point(41, 100, k), control1: point(10, 70, k), control2: point(30, 88, k))
                    path.addLine(to: point(0, 100, k))
                    path.closeSubpath()
                }
                let flapGradient = Gradient(stops: [
                    .init(color: icon.curlDark, location: 0),
                    .init(color: icon.curlMid, location: 0.45),
                    .init(color: icon.curlLight, location: 1),
                ])
                context.fill(flap, with: .linearGradient(flapGradient, startPoint: point(22, 78, k), endPoint: point(4, 96, k)))
                let lip = Path { path in
                    path.move(to: point(0, 60, k))
                    path.addCurve(to: point(41, 100, k), control1: point(10, 70, k), control2: point(30, 88, k))
                    path.addLine(to: point(46, 100, k))
                    path.addCurve(to: point(0, 57, k), control1: point(34, 88, k), control2: point(14, 70, k))
                    path.closeSubpath()
                }
                let lipGradient = Gradient(colors: [Color.white.opacity(0), Color.white.opacity(0.95)])
                context.fill(lip, with: .linearGradient(lipGradient, startPoint: point(20, 80, k), endPoint: point(30, 90, k)))
            }
            .frame(width: size, height: size)
        }
    }
}
