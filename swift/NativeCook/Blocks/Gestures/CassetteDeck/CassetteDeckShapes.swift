import SwiftUI

/// The speaker grille: a hexagonal field of dots clipped to a circle, centred in its frame.
struct CassetteDeckGrille: Shape {
    let scale: CGFloat

    func path(in rect: CGRect) -> Path {
        let g = CassetteDeckTheme.Layout.grille
        var path = Path()
        let rows = Int((g.radius / (g.pitch * 0.866)).rounded(.up))
        for r in -rows...rows {
            let y = Double(r) * g.pitch * 0.866
            let shift = Double(abs(r) % 2) * g.pitch * 0.5
            var x = -g.radius - shift
            while x <= g.radius + g.pitch {
                let px = x + shift
                x += g.pitch
                if px * px + y * y > g.radius * g.radius { continue }
                let radius = g.dot * scale
                let centre = CGPoint(x: rect.midX + px * scale, y: rect.midY + y * scale)
                path.addEllipse(in: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2))
            }
        }
        return path
    }
}

/// The glyph above one key (play, pause, next, rewind, fast-forward), centred in its frame.
struct CassetteDeckGlyph: Shape {
    let index: Int
    let scale: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        func polygon(_ points: [(Double, Double)]) {
            for (n, point) in points.enumerated() {
                let p = CGPoint(x: rect.midX + point.0 * scale, y: rect.midY + point.1 * scale)
                if n == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.closeSubpath()
        }
        switch index {
        case 0:
            polygon([(-3, -4), (4, 0), (-3, 4)])
        case 1:
            polygon([(-3.5, -4), (-1.2, -4), (-1.2, 4), (-3.5, 4)])
            polygon([(1.2, -4), (3.5, -4), (3.5, 4), (1.2, 4)])
        case 2:
            polygon([(-4, -4), (2, 0), (-4, 4)])
            polygon([(2.4, -4), (4.2, -4), (4.2, 4), (2.4, 4)])
        case 3:
            polygon([(0, -3.6), (-5, 0), (0, 3.6)])
            polygon([(5, -3.6), (0, 0), (5, 3.6)])
        default:
            polygon([(0, -3.6), (5, 0), (0, 3.6)])
            polygon([(-5, -3.6), (0, 0), (-5, 3.6)])
        }
        return path
    }
}

/// One part of the music note on the small button at the bottom left, in the button's own frame:
/// the head (0), the stem (1) or the flag (2). The parts overlap, so each is filled on its own.
struct CassetteDeckNote: Shape {
    let part: Int
    let scale: CGFloat

    func path(in rect: CGRect) -> Path {
        let s = scale
        let cx = rect.midX
        let cy = rect.midY
        var path = Path()
        switch part {
        case 0:
            path.addEllipse(in: CGRect(x: cx - 3.2 * s, y: cy + 1.2 * s, width: 3.4 * s, height: 2.6 * s))
        case 1:
            path.addRect(CGRect(x: cx - 0.2 * s, y: cy - 5 * s, width: 0.9 * s, height: 7.6 * s))
        default:
            path.move(to: CGPoint(x: cx + 0.7 * s, y: cy - 5 * s))
            path.addQuadCurve(
                to: CGPoint(x: cx + 3 * s, y: cy - 1.4 * s),
                control: CGPoint(x: cx + 2.6 * s, y: cy - 3.2 * s)
            )
            path.addLine(to: CGPoint(x: cx + 0.7 * s, y: cy - 2.6 * s))
            path.closeSubpath()
        }
        return path
    }
}
