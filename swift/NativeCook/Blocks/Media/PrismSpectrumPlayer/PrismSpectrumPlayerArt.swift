import SwiftUI

/*
 The player's controls, drawn in Canvases over the shader. The static ones (header icons, heart,
 previous / next / forward, the up-next card) sit in a Canvas outside the timeline, so they are
 drawn once; the moving ones (scrubber, times, back-5 press, play button with its play ↔ pause
 morph) are drawn every frame from the pose, which is a handful of draws. A port of
 prismSpectrumArt.ts.
 */
enum PrismSpectrumPlayerArt {
    typealias Geo = PrismSpectrumPlayerGeo

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    static let ink = rgb(0xEEEAF8)
    static let inkDim = rgb(0xEEEAF8, 0.55)
    static let track = rgb(0xEEEAF8, 0.16)
    static let background = rgb(0x0D0F1F)
    static let spectrum: [Color] = [0xFF6B8B, 0xFFB36B, 0xF6EF7A, 0x7AF0A6, 0x6BC8FF, 0xA18BFF].map { rgb($0) }

    static let playR = 34.0

    static let monoFont = Font.system(size: 12, weight: .medium, design: .monospaced)
    static let smallFont = Font.system(size: 9, weight: .bold)

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// Button centres along the controls row.
    static func controlX(_ g: Geo, _ i: Int) -> Double {
        let step = min(70, g.w * 0.178)
        return g.w / 2 + Double(i - 2) * step * (i == 0 || i == 4 ? 0.98 : 1)
    }

    /// Draws text with its baseline at y, starting at x (or ending at x when `trailing`).
    private static func text(_ c: GraphicsContext, _ string: String, font: Font, color: Color, x: Double, baseline y: Double, trailing: Bool = false, centered: Bool = false) {
        let resolved = c.resolve(Text(string).font(font).foregroundStyle(color))
        let size = resolved.measure(in: CGSize(width: 1000, height: 1000))
        let base = resolved.firstBaseline(in: CGSize(width: 1000, height: 1000))
        var left = x
        if trailing { left = x - size.width }
        if centered { left = x - size.width / 2 }
        c.draw(resolved, in: CGRect(x: left, y: y - base, width: size.width, height: size.height))
    }

    /// A skip icon: a bar and a triangle, pointing left (dir −1) or right (dir 1).
    private static func skip(_ c: GraphicsContext, _ x: Double, _ y: Double, _ dir: Double, _ s: Double) {
        var p = Path()
        p.move(to: CGPoint(x: x - dir * 7 * s, y: y - 9 * s))
        p.addLine(to: CGPoint(x: x + dir * 6 * s, y: y))
        p.addLine(to: CGPoint(x: x - dir * 7 * s, y: y + 9 * s))
        p.closeSubpath()
        c.fill(p, with: .color(ink))
        c.stroke(p, with: .color(ink), style: round(2.2 * s))
        let bar = CGRect(x: x + dir * 8.5 * s - 1.6 * s, y: y - 9 * s, width: 3.2 * s, height: 18 * s)
        c.fill(Path(roundedRect: bar, cornerRadius: 1.6 * s), with: .color(ink))
    }

    /// A circular "5 seconds" arrow: back (dir −1) or forward (dir 1).
    static func jump(_ c: GraphicsContext, _ x: Double, _ y: Double, _ dir: Double, _ s: Double, scale: Double) {
        var c = c
        c.translateBy(x: x, y: y)
        c.scaleBy(x: scale, y: scale)
        let r = 12 * s
        // The ring is open at the top, on the side the arrow points to.
        let start = dir < 0 ? -110.0 : -70.0
        let sweep = dir < 0 ? 300.0 : -300.0
        var arc = Path()
        arc.addRelativeArc(center: .zero, radius: r, startAngle: .degrees(start), delta: .degrees(sweep))
        c.stroke(arc, with: .color(ink), style: round(2 * s))
        let a = start * Double.pi / 180
        let px = cos(a) * r
        let py = sin(a) * r
        // Tangent against the sweep: the head points where the ring was opened.
        let tx = dir < 0 ? sin(a) : -sin(a)
        let ty = dir < 0 ? -cos(a) : cos(a)
        var head = Path()
        head.move(to: CGPoint(x: px + tx * 5.5 * s, y: py + ty * 5.5 * s))
        head.addLine(to: CGPoint(x: px - ty * 4 * s - tx * 0.5 * s, y: py + tx * 4 * s - ty * 0.5 * s))
        head.addLine(to: CGPoint(x: px + ty * 4 * s - tx * 0.5 * s, y: py - tx * 4 * s - ty * 0.5 * s))
        head.closeSubpath()
        c.fill(head, with: .color(ink))
        text(c, "5", font: smallFont, color: ink, x: 0, baseline: 3.6 * s, centered: true)
    }

    /// Heart outline.
    private static func heart(_ c: GraphicsContext, _ x: Double, _ y: Double, _ s: Double) {
        var p = Path()
        p.move(to: CGPoint(x: x, y: y + 8 * s))
        p.addCurve(to: CGPoint(x: x - 5 * s, y: y - 9 * s), control1: CGPoint(x: x - 12 * s, y: y), control2: CGPoint(x: x - 10.5 * s, y: y - 9.5 * s))
        p.addCurve(to: CGPoint(x: x, y: y - 5.2 * s), control1: CGPoint(x: x - 2.2 * s, y: y - 8.8 * s), control2: CGPoint(x: x - 0.6 * s, y: y - 6.8 * s))
        p.addCurve(to: CGPoint(x: x + 5 * s, y: y - 9 * s), control1: CGPoint(x: x + 0.6 * s, y: y - 6.8 * s), control2: CGPoint(x: x + 2.2 * s, y: y - 8.8 * s))
        p.addCurve(to: CGPoint(x: x, y: y + 8 * s), control1: CGPoint(x: x + 10.5 * s, y: y - 9.5 * s), control2: CGPoint(x: x + 12 * s, y: y))
        p.closeSubpath()
        c.stroke(p, with: .color(ink), style: round(1.8 * s))
    }

    /// Everything that never moves: header icons, heart, previous / next / forward 5, up-next card.
    static func drawStatic(_ c: GraphicsContext, _ g: Geo, top: Double, bottom: Double) {
        let s = max(0.85, min(1, g.u))
        // Header: chevron down on the left, ellipsis on the right.
        let hy = top + 24
        var chev = Path()
        chev.move(to: CGPoint(x: 24, y: hy - 3.5))
        chev.addLine(to: CGPoint(x: 31, y: hy + 3.5))
        chev.addLine(to: CGPoint(x: 38, y: hy - 3.5))
        c.stroke(chev, with: .color(ink), style: round(2.2))
        for i in 0..<3 {
            let cx = g.w - 38 + Double(i) * 7
            c.fill(Path(ellipseIn: CGRect(x: cx - 1.8, y: hy - 1.8, width: 3.6, height: 3.6)), with: .color(ink))
        }
        heart(c, g.w - 36, g.titleTop + 20, 1)
        skip(c, controlX(g, 1), g.controlsY, -1, s)
        skip(c, controlX(g, 3), g.controlsY, 1, s)
        jump(c, controlX(g, 4), g.controlsY, 1, s, scale: 1)
        // The "up next" card: a glassy row with a small cover (a spectrum in a dark square).
        let cardY = g.h - bottom - 76
        c.fill(Path(roundedRect: CGRect(x: 16, y: cardY, width: g.w - 32, height: 64), cornerRadius: 18), with: .color(rgb(0xEEEAF8, 0.06)))
        c.stroke(Path(roundedRect: CGRect(x: 16.5, y: cardY + 0.5, width: g.w - 33, height: 63), cornerRadius: 17.5), with: .color(rgb(0xEEEAF8, 0.10)), lineWidth: 1)
        let cover = Path(roundedRect: CGRect(x: 26, y: cardY + 10, width: 44, height: 44), cornerRadius: 10)
        c.fill(cover, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x2B2160), location: 0),
                .init(color: rgb(0x151530), location: 0.5),
                .init(color: rgb(0x0E1A2E), location: 1),
            ]),
            startPoint: CGPoint(x: 26, y: cardY + 10),
            endPoint: CGPoint(x: 70, y: cardY + 54)
        ))
        for (i, band) in spectrum.enumerated() {
            var b = Path()
            b.move(to: CGPoint(x: 34, y: cardY + 34))
            b.addLine(to: CGPoint(x: 66, y: cardY + 22 + Double(i) * 4.2))
            c.stroke(b, with: .color(band), style: round(2))
        }
        // Queue icon on the card.
        let qy = cardY + 32
        for i in 0..<3 {
            let ly = qy - 6 + Double(i) * 6
            c.fill(Path(ellipseIn: CGRect(x: g.w - 44 - 1.4, y: ly - 1.4, width: 2.8, height: 2.8)), with: .color(inkDim))
            var line = Path()
            line.move(to: CGPoint(x: g.w - 39, y: ly))
            line.addLine(to: CGPoint(x: g.w - 26, y: ly))
            c.stroke(line, with: .color(inkDim), style: round(1.6))
        }
    }

    /// Play ↔ pause: two quads, each vertex moving from the play triangle's half to a pause bar.
    private static let playQuads: [[Double]] = [
        [-7, -11, 1.5, -5.5, 1.5, 5.5, -7, 11],
        [1.5, -5.5, 10, 0, 10, 0, 1.5, 5.5],
    ]
    private static let pauseQuads: [[Double]] = [
        [-9, -10, -3, -10, -3, 10, -9, 10],
        [3, -10, 9, -10, 9, 10, 3, 10],
    ]

    private static func playPause(_ c: GraphicsContext, _ x: Double, _ y: Double, _ m: Double, _ s: Double) {
        let color = rgb(0x1B1830)
        for q in 0..<2 {
            var p = Path()
            for v in 0..<4 {
                let ax = playQuads[q][v * 2] + 1.5
                let ay = playQuads[q][v * 2 + 1]
                let bx = pauseQuads[q][v * 2]
                let by = pauseQuads[q][v * 2 + 1]
                let pt = CGPoint(x: x + (ax + (bx - ax) * m) * s, y: y + (ay + (by - ay) * m) * s)
                if v == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
            p.closeSubpath()
            c.fill(p, with: .color(color))
            c.stroke(p, with: .color(color), style: round(2.4 * s))
        }
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    /// Scrubber, times, back-5 and the play button, from the pose.
    static func drawDynamic(_ c: GraphicsContext, _ g: Geo, _ pose: PrismSpectrumPlayerPose, pressPlay: Double) {
        let s = max(0.85, min(1, g.u))
        // Scrubber.
        let x0 = 24.0
        let x1 = g.w - 24
        let frac = min(1, max(0, pose.elapsed / PrismSpectrumPlayerMotion.trackLength))
        let kx = x0 + (x1 - x0) * frac
        c.fill(Path(roundedRect: CGRect(x: x0, y: g.scrubY - 2, width: x1 - x0, height: 4), cornerRadius: 2), with: .color(track))
        c.fill(
            Path(roundedRect: CGRect(x: x0, y: g.scrubY - 2, width: max(4, kx - x0), height: 4), cornerRadius: 2),
            with: .linearGradient(
                Gradient(colors: [rgb(0xC4BAFF, 0.75), rgb(0xEEEAF8, 0.95)]),
                startPoint: CGPoint(x: x0, y: 0),
                endPoint: CGPoint(x: max(x0 + 0.5, kx), y: 0)
            )
        )
        c.drawLayer { l in
            l.addFilter(.blur(radius: 2.5))
            l.fill(circle(kx, g.scrubY + 1.5, 6.5), with: .color(rgb(0x060618, 0.45)))
        }
        c.fill(circle(kx, g.scrubY, 6.5), with: .color(rgb(0xF4F1FC)))
        let elapsed = floor(pose.elapsed)
        let left = PrismSpectrumPlayerMotion.formatTime(elapsed)
        let right = "-" + PrismSpectrumPlayerMotion.formatTime(PrismSpectrumPlayerMotion.trackLength - elapsed)
        let ty = g.scrubY + 24
        text(c, left, font: monoFont, color: inkDim, x: x0, baseline: ty)
        text(c, right, font: monoFont, color: inkDim, x: x1, baseline: ty, trailing: true)

        // Back 5 s, which dips when pressed.
        jump(c, controlX(g, 0), g.controlsY, -1, s, scale: 1 - 0.1 * pose.pressBack)

        // The play button: two shadows, a three-stop face, a top edge highlight, a rainbow rim while playing.
        let px = g.w / 2
        let py = g.controlsY
        let r = playR * s * (1 - 0.08 * pressPlay)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 14))
            l.fill(circle(px, py + 10 * (1 - 0.5 * pressPlay), r * 0.92), with: .color(rgb(0x463AAA, 0.42)))
        }
        c.drawLayer { l in
            l.addFilter(.blur(radius: 2))
            l.fill(circle(px, py + 1.5, r), with: .color(rgb(0x060618, 0.5)))
        }
        if pose.play > 0.01 {
            c.drawLayer { l in
                l.opacity = 0.55 * pose.play
                l.addFilter(.blur(radius: 3))
                l.stroke(
                    circle(px, py, r + 3),
                    with: .conicGradient(Gradient(colors: spectrum + [spectrum[0]]), center: CGPoint(x: px, y: py), angle: .zero),
                    lineWidth: 2.4
                )
            }
        }
        c.fill(circle(px, py, r), with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0xF7F5FD), location: 0),
                .init(color: rgb(0xDCD7EF), location: 0.55),
                .init(color: rgb(0xBDB5DC), location: 1),
            ]),
            startPoint: CGPoint(x: px, y: py - r),
            endPoint: CGPoint(x: px, y: py + r)
        ))
        c.stroke(circle(px, py, r - 0.6), with: .color(Color.white.opacity(0.7)), lineWidth: 1.2)
        playPause(c, px, py, pose.icon, s * (1 - 0.08 * pressPlay))
    }
}
