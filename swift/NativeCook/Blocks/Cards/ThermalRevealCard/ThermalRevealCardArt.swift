import SwiftUI
import UIKit

// The secret map under the ink, drawn once in code into an image the size of the card (at the
// screen's scale) and then shown as the layer the card's shader reads. A port of thermalArt.ts:
// positions are fractions of the card, so the map fits any card size. A teal sea, three islands
// (reef glow, sand, jungle, contour rings), a dashed route between three coves marked with an X,
// a compass rose and hand-lettered place names.

enum ThermalRevealCardArt {
    @MainActor private static var cache: [String: UIImage] = [:]

    /// The map for a card of w × h points, rendered once per size and decoded for display.
    @MainActor static func map(width w: Double, height h: Double, scale: Double) -> UIImage? {
        guard w > 0, h > 0 else { return nil }
        let key = "\(Int(w * 10))x\(Int(h * 10))@\(scale)"
        if let image = cache[key] { return image }
        let renderer = ImageRenderer(content:
            Canvas { context, size in draw(&context, w: size.width, h: size.height) }
                .frame(width: w, height: h)
        )
        renderer.scale = scale
        renderer.isOpaque = true
        guard let image = renderer.uiImage else { return nil }
        let ready = image.preparingForDisplay() ?? image
        cache[key] = ready
        return ready
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    private static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(hex: hex, opacity: alpha)
    }

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// A soft irregular island outline: a ring of points whose radius wobbles, joined with quads.
    private static func island(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ seed: Double, _ scale: Double = 1) -> Path {
        let n = 18
        var pts: [CGPoint] = []
        for i in 0..<n {
            let a = Double(i) / Double(n) * .pi * 2
            let wobble = 1 + 0.16 * sin(a * 3 + seed * 1.7) + 0.09 * sin(a * 5 + seed * 3.1)
                + 0.06 * (hash(seed * 13 + Double(i)) - 0.5)
            pts.append(CGPoint(x: cx + cos(a) * rx * wobble * scale, y: cy + sin(a) * ry * wobble * scale))
        }
        func mid(_ i: Int) -> CGPoint {
            let a = pts[i % n]
            let b = pts[(i + 1) % n]
            return CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        }
        var path = Path()
        path.move(to: mid(0))
        for i in 1...n {
            path.addQuadCurve(to: mid(i), control: pts[i % n])
        }
        path.closeSubpath()
        return path
    }

    private static func drawIsland(_ c: inout GraphicsContext, _ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ seed: Double) {
        // Reef: a pale glow in the shallows around the island.
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 9))
            layer.fill(island(cx, cy, rx, ry, seed, 1.32), with: .color(color(0x8fe6d6, 0.55)))
        }
        c.stroke(island(cx, cy, rx, ry, seed, 1.16), with: .color(color(0xd9fff6, 0.45)), style: round(1))
        // Sand, then jungle, then contour rings up to a small peak.
        c.fill(island(cx, cy, rx, ry, seed, 1.04), with: .color(color(0xf1d39a)))
        c.fill(
            island(cx, cy, rx, ry, seed, 0.86),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: color(0x5ea866), location: 0),
                    .init(color: color(0x3c8152), location: 0.6),
                    .init(color: color(0x2b6243), location: 1),
                ]),
                center: CGPoint(x: cx - rx * 0.2, y: cy - ry * 0.25),
                startRadius: 0,
                endRadius: max(rx, ry)
            )
        )
        for k in 1...3 {
            let dk = Double(k)
            c.stroke(
                island(cx + rx * 0.05 * dk, cy - ry * 0.04 * dk, rx, ry, seed + dk, 0.86 - dk * 0.17),
                with: .color(color(0x1f4b33, 0.45)),
                style: round(1)
            )
        }
        c.stroke(island(cx, cy, rx, ry, seed, 1.04), with: .color(color(0xb78b4a, 0.6)), style: round(1.2))
    }

    private static func line(_ a: CGPoint, _ b: CGPoint) -> Path {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        return p
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private static func drawX(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ s: Double) {
        let a = line(CGPoint(x: x - s, y: y - s), CGPoint(x: x + s, y: y + s))
        let b = line(CGPoint(x: x - s, y: y + s), CGPoint(x: x + s, y: y - s))
        for p in [a, b] { c.stroke(p, with: .color(color(0xfff4dc, 0.85)), style: round(7)) }
        for p in [a, b] { c.stroke(p, with: .color(color(0xc8302b)), style: round(4)) }
        c.stroke(circle(x, y, s * 1.9), with: .color(color(0xc8302b, 0.55)), style: round(1.2))
    }

    /// Draws `text` with its left end at x and its baseline at y, as Skia's drawText does.
    private static func text(_ c: inout GraphicsContext, _ text: Text, x: Double, y: Double, clampWidth w: Double? = nil) {
        let resolved = c.resolve(text)
        let size = resolved.measure(in: CGSize(width: 1000, height: 100))
        let baseline = resolved.firstBaseline(in: size)
        var lx = x
        if let w { lx = min(max(x, 10), w - size.width - 10) }
        c.draw(resolved, in: CGRect(x: lx, y: y - baseline, width: size.width, height: size.height))
    }

    private static func drawCompass(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double) {
        c.stroke(circle(x, y, r * 1.05), with: .color(color(0xfff1d0, 0.8)), style: round(1.2))
        c.stroke(circle(x, y, r * 0.82), with: .color(color(0xfff1d0, 0.5)), style: round(0.8))
        for i in 0..<4 {
            let a = Double(i) * .pi / 2 - .pi / 2
            let tip = CGPoint(x: x + cos(a) * r, y: y + sin(a) * r)
            let left = CGPoint(x: x + cos(a - .pi / 4) * r * 0.22, y: y + sin(a - .pi / 4) * r * 0.22)
            let right = CGPoint(x: x + cos(a + .pi / 4) * r * 0.22, y: y + sin(a + .pi / 4) * r * 0.22)
            var lit = Path()
            lit.move(to: CGPoint(x: x, y: y))
            lit.addLine(to: tip)
            lit.addLine(to: left)
            lit.closeSubpath()
            var dark = Path()
            dark.move(to: CGPoint(x: x, y: y))
            dark.addLine(to: tip)
            dark.addLine(to: right)
            dark.closeSubpath()
            c.fill(lit, with: .color(color(i == 0 ? 0xffe9b8 : 0xfff4dc)))
            c.fill(dark, with: .color(color(i == 0 ? 0xc8302b : 0x8a6a3c)))
        }
        let n = Text("N").font(small).foregroundStyle(color(0xfff4dc))
        let resolved = c.resolve(n)
        let size = resolved.measure(in: CGSize(width: 100, height: 100))
        text(&c, n, x: x - size.width / 2, y: y - r * 1.2)
    }

    private static let label = Font.custom("Georgia-BoldItalic", fixedSize: 12)
    private static let small = Font.custom("Georgia-Italic", fixedSize: 10)

    static func draw(_ c: inout GraphicsContext, w: Double, h: Double) {
        let full = CGRect(x: 0, y: 0, width: w, height: h)
        // Sea: deep at the top, turquoise lower down, with lighter shallows.
        c.fill(
            Path(full),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(0x0b4f6c), location: 0),
                    .init(color: color(0x127f97), location: 0.55),
                    .init(color: color(0x2bb3b0), location: 1),
                ]),
                startPoint: .zero,
                endPoint: CGPoint(x: w * 0.3, y: h)
            )
        )
        // Wave hatching and a chart grid, each batched into one path.
        var waves = Path()
        for i in 0..<70 {
            let di = Double(i)
            let x = hash(di * 3 + 1) * w
            let y = hash(di * 5 + 2) * h
            waves.move(to: CGPoint(x: x, y: y))
            waves.addQuadCurve(to: CGPoint(x: x + 10, y: y), control: CGPoint(x: x + 5, y: y - 3))
            waves.addQuadCurve(to: CGPoint(x: x + 20, y: y), control: CGPoint(x: x + 15, y: y + 3))
        }
        c.stroke(waves, with: .color(color(0xbff3ee, 0.22)), style: round(1))
        var grid = Path()
        var gx = 28.0
        while gx < w { grid.move(to: CGPoint(x: gx, y: 0)); grid.addLine(to: CGPoint(x: gx, y: h)); gx += 34 }
        var gy = 28.0
        while gy < h { grid.move(to: CGPoint(x: 0, y: gy)); grid.addLine(to: CGPoint(x: w, y: gy)); gy += 34 }
        c.stroke(grid, with: .color(color(0xe8fffb, 0.12)), style: round(0.6))

        drawIsland(&c, w * 0.27, h * 0.23, w * 0.2, h * 0.11, 1)
        drawIsland(&c, w * 0.74, h * 0.5, w * 0.17, h * 0.13, 2)
        drawIsland(&c, w * 0.3, h * 0.79, w * 0.22, h * 0.1, 3)
        drawIsland(&c, w * 0.83, h * 0.85, w * 0.07, h * 0.04, 4)

        // The route: white underlay, then red dashes, from the top-left corner through the coves.
        let coves = ThermalRevealCardTuning.coves
        let pts = ([(0.08, 0.06)] + coves + [(0.9, 0.95)]).map { CGPoint(x: $0.0 * w, y: $0.1 * h) }
        var route = Path()
        route.move(to: pts[0])
        for i in 1..<pts.count {
            let a = pts[i - 1]
            let b = pts[i]
            let bend = i % 2 == 0 ? 0.28 : -0.28
            route.addCurve(
                to: b,
                control1: CGPoint(x: a.x + (b.x - a.x) * 0.3 + (b.y - a.y) * bend, y: a.y + (b.y - a.y) * 0.3 - (b.x - a.x) * bend),
                control2: CGPoint(x: a.x + (b.x - a.x) * 0.7 + (b.y - a.y) * bend, y: a.y + (b.y - a.y) * 0.7 - (b.x - a.x) * bend)
            )
        }
        c.stroke(route, with: .color(color(0xfff4dc, 0.55)), style: round(5))
        c.stroke(
            route,
            with: .color(color(0xd23a2f)),
            style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round, dash: [9, 7], dashPhase: 0)
        )

        for (x, y) in coves { drawX(&c, x * w, y * h, 8) }

        let names: [(String, Double, Double)] = [
            ("Cove of Lanterns", coves[0].0 * w + 16, coves[0].1 * h - 14),
            ("Seal Rock", coves[1].0 * w - 78, coves[1].1 * h + 26),
            ("Glass Lagoon", coves[2].0 * w + 18, coves[2].1 * h + 4),
        ]
        for (name, x, y) in names {
            let resolved = c.resolve(Text(name).font(label))
            let tw = resolved.measure(in: CGSize(width: 1000, height: 100)).width
            let lx = min(max(x, 10), w - tw - 10)
            text(&c, Text(name).font(label).foregroundStyle(color(0x06303f, 0.7)), x: lx + 0.6, y: y + 0.8)
            text(&c, Text(name).font(label).foregroundStyle(color(0xfff4dc)), x: lx, y: y)
        }
        text(&c, Text("Aruna Sound").font(small).foregroundStyle(color(0xd6fbf4, 0.7)), x: w * 0.52, y: h * 0.31)
        text(&c, Text("depth 12 fathoms").font(small).foregroundStyle(color(0xd6fbf4, 0.55)), x: w * 0.06, y: h * 0.62)
        drawCompass(&c, w - 40, 54, 20)

        // An aged border: darker at the very edge.
        c.fill(
            Path(full),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .clear, location: 0.72),
                    .init(color: Color.rgba(4, 24, 32, 0.45), location: 1),
                ]),
                center: CGPoint(x: w / 2, y: h / 2),
                startRadius: 0,
                endRadius: hypot(w, h) * 0.62
            )
        )
    }
}
