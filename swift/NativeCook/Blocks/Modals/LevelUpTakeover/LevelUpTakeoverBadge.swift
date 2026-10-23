import SwiftUI

/// The level badge: a white bezel round a glossy gold hexagon, the number on its face, a band of
/// light that sweeps across once and a few sparkles.
struct LevelUpTakeoverBadge: View {
    static let width = 152.0
    static let height = 132.0
    /// Room round the badge for its shadow and sparkles.
    static let margin = 28.0
    /// The badge's box, margin included.
    static let boxWidth = width + margin * 2
    static let boxHeight = height + margin * 2

    let level: Int
    /// 0 to 1: the band of light's trip from the left of the badge to its right.
    let sweep: Double
    /// The sparkles' opacity.
    let twinkle: Double

    private static let top = margin
    private static let bottom = margin + height
    private static let shineWidth = 30.0

    private static let bezel = hexagon(0, 22)
    private static let rim = hexagon(9, 17)
    private static let body = hexagon(14, 15)
    private static let face = hexagon(26, 11)
    private static let sparkles = [
        sparkle(margin + width - 4, margin + 8, 9),
        sparkle(margin + width + 12, margin + 30, 5),
        sparkle(margin + 8, margin + height - 10, 6),
    ]

    /// A flat-top hexagon `inset` points inside the badge, its corners rounded by `corner`.
    private static func hexagon(_ inset: Double, _ corner: Double) -> Path {
        let w = width - inset * 2
        let h = height - inset * 2
        let quarter = w / 4
        let ox = margin + inset
        let oy = margin + inset
        let points = [
            CGPoint(x: ox + quarter, y: oy),
            CGPoint(x: ox + w - quarter, y: oy),
            CGPoint(x: ox + w, y: oy + h / 2),
            CGPoint(x: ox + w - quarter, y: oy + h),
            CGPoint(x: ox + quarter, y: oy + h),
            CGPoint(x: ox, y: oy + h / 2),
        ]
        var path = Path()
        for i in points.indices {
            let p = points[(i + points.count - 1) % points.count]
            let c = points[i]
            let n = points[(i + 1) % points.count]
            // Cut each corner short along both edges and round it with a quad.
            let a = hypot(c.x - p.x, c.y - p.y)
            let b = hypot(n.x - c.x, n.y - c.y)
            let start = CGPoint(x: c.x + (p.x - c.x) / a * corner, y: c.y + (p.y - c.y) / a * corner)
            let end = CGPoint(x: c.x + (n.x - c.x) / b * corner, y: c.y + (n.y - c.y) / b * corner)
            if i == 0 { path.move(to: start) } else { path.addLine(to: start) }
            path.addQuadCurve(to: end, control: c)
        }
        path.closeSubpath()
        return path
    }

    /// A four-pointed sparkle centred on (x, y).
    private static func sparkle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        let k = r * 0.22
        var path = Path()
        path.move(to: CGPoint(x: x, y: y - r))
        path.addQuadCurve(to: CGPoint(x: x + r, y: y), control: CGPoint(x: x + k, y: y - k))
        path.addQuadCurve(to: CGPoint(x: x, y: y + r), control: CGPoint(x: x + k, y: y + k))
        path.addQuadCurve(to: CGPoint(x: x - r, y: y), control: CGPoint(x: x - k, y: y + k))
        path.addQuadCurve(to: CGPoint(x: x, y: y - r), control: CGPoint(x: x - k, y: y - k))
        path.closeSubpath()
        return path
    }

    private static func vertical(_ colors: [Color], _ from: Double, _ to: Double, stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = stops.map { Gradient(stops: zip(colors, $0).map { .init(color: $0, location: $1) }) } ?? Gradient(colors: colors)
        return .linearGradient(gradient, startPoint: CGPoint(x: 0, y: from), endPoint: CGPoint(x: 0, y: to))
    }

    var body: some View {
        ZStack {
            Canvas { context, _ in
                let bezel = Self.vertical([.white, Color(hex: 0xfff1c9)], Self.top, Self.bottom)
                // Two shadows under the bezel, a deep soft one and a close one, each drawn on its own.
                for (color, radius, y) in [(Color.rgba(214, 128, 0, 0.38), 16.0, 12.0), (.rgba(160, 90, 0, 0.25), 3, 2)] {
                    var shadowed = context
                    shadowed.addFilter(.shadow(color: color, radius: radius, x: 0, y: y))
                    shadowed.fill(Self.bezel, with: bezel)
                }
                context.fill(Self.bezel, with: bezel)
                context.fill(Self.rim, with: Self.vertical([Color(hex: 0xffb81a), Color(hex: 0xe47400)], Self.top + 9, Self.bottom - 9))
                context.fill(Self.body, with: Self.vertical([Color(hex: 0xffd84a), Color(hex: 0xffa812)], Self.top + 14, Self.bottom - 14))
                context.fill(
                    Self.face,
                    with: Self.vertical([Color(hex: 0xffe98a), Color(hex: 0xffc21a), Color(hex: 0xffb000)], Self.top + 26, Self.bottom - 26, stops: [0, 0.55, 1])
                )
                context.stroke(Self.face, with: .color(.white.opacity(0.55)), lineWidth: 2)

                // Gloss over the upper half, and the moving band of light.
                var inside = context
                inside.clip(to: Self.body)
                inside.fill(
                    Path(CGRect(x: Self.margin, y: Self.top, width: Self.width, height: Self.height * 0.48)),
                    with: Self.vertical([.white.opacity(0.5), .white.opacity(0.06)], Self.top, Self.top + Self.height * 0.48)
                )
                // The band runs from left of the badge to its right, leaning forward.
                inside.translateBy(x: Self.margin - Self.shineWidth * 2 + sweep * (Self.width + Self.shineWidth * 4), y: 0)
                inside.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.45, d: 1, tx: 0, ty: 0))
                inside.fill(Path(CGRect(x: 0, y: Self.top - 8, width: Self.shineWidth, height: Self.height + 16)), with: .color(.white.opacity(0.6)))

                var sparkling = context
                sparkling.opacity = twinkle
                for sparkle in Self.sparkles { sparkling.fill(sparkle, with: .color(.white)) }
            }
            Text("\(level)")
                .font(.system(size: 64, weight: .black)).kerning(-1).foregroundStyle(.white)
                .shadow(color: Color(hex: 0xd06f00), radius: 0.25, x: 0, y: 3)
                .frame(height: 72)
        }
        .frame(width: Self.boxWidth, height: Self.boxHeight)
    }
}
