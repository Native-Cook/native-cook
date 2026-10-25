import SwiftUI
import UIKit

/*
 Drawing for Lighthouse Beam Welcome: the tower on its rocks (a Canvas outside the timeline, so it
 is drawn once), the three pages of onboarding text drawn once into an image the shader reveals,
 and the per-frame bits (the lamp's glow and flare, the paging dots). A port of lighthouseArt.ts.
 */
enum LighthouseBeamWelcomeArt {
    typealias Geo = LighthouseBeamWelcomeGeo

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    static let background = rgb(0x0A1430)
    static let cream = rgb(0xF2E6C8)

    static let pageCopy: [(kicker: String, title: String, body: String)] = [
        ("SAFE PASSAGE", "Light the way home", "Safe routes around shoals and wrecks, even in fog."),
        ("ONE-TAP SOS", "Raise the alarm fast", "One tap sends your position to every boat nearby."),
        ("TIDES & SWELL", "Read the water first", "Live tide, wind and swell from 1,200 stations."),
    ]

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    // MARK: - The pages

    /// The page icons, 30 pt boxes: a compass rose, a life ring, a tide wave.
    private static func icon(_ c: GraphicsContext, _ kind: Int, _ x: Double, _ y: Double) {
        let ink = GraphicsContext.Shading.color(.white)
        let cx = x + 15
        let cy = y + 15
        if kind == 0 {
            c.stroke(circle(cx, cy, 13), with: ink, style: round(2))
            var needle = Path()
            needle.move(to: CGPoint(x: cx, y: cy - 9))
            needle.addLine(to: CGPoint(x: cx + 4, y: cy))
            needle.addLine(to: CGPoint(x: cx, y: cy + 9))
            needle.addLine(to: CGPoint(x: cx - 4, y: cy))
            needle.closeSubpath()
            c.fill(needle, with: ink)
        } else if kind == 1 {
            c.stroke(circle(cx, cy, 13), with: ink, style: round(2))
            c.stroke(circle(cx, cy, 6), with: ink, style: round(2))
            for i in 0..<4 {
                var arc = Path()
                arc.addRelativeArc(
                    center: CGPoint(x: cx, y: cy),
                    radius: 9.5,
                    startAngle: .degrees(45 + Double(i) * 90 - 14),
                    delta: .degrees(28)
                )
                c.stroke(arc, with: ink, style: round(5))
            }
        } else {
            for r in 0..<2 {
                var wave = Path()
                let wy = cy - 2 + Double(r) * 8
                wave.move(to: CGPoint(x: x + 1, y: wy))
                for i in 0..<3 {
                    let sx = x + 1 + Double(i) * 9.4
                    wave.addCurve(to: CGPoint(x: sx + 4.7, y: wy), control1: CGPoint(x: sx + 2.4, y: wy - 5), control2: CGPoint(x: sx + 4.7, y: wy - 5))
                    wave.addCurve(to: CGPoint(x: sx + 9.4, y: wy), control1: CGPoint(x: sx + 4.7, y: wy + 5), control2: CGPoint(x: sx + 7, y: wy + 5))
                }
                c.stroke(wave, with: ink, style: round(2))
            }
            c.stroke(circle(cx + 7, y + 3, 3), with: ink, style: round(2))
        }
    }

    private static let unbounded = CGSize(width: 10_000, height: 1_000)

    /// Draws a line of text with its baseline at y.
    private static func line(_ c: GraphicsContext, _ text: Text, x: Double, baseline y: Double) {
        let resolved = c.resolve(text)
        let size = resolved.measure(in: unbounded)
        let base = resolved.firstBaseline(in: unbounded)
        c.draw(resolved, in: CGRect(x: x, y: y - base, width: size.width + 1, height: size.height))
    }

    /// Greedy word wrap with measured widths.
    private static func wrap(_ c: GraphicsContext, _ text: String, font: Font, max: Double) -> [String] {
        var lines: [String] = []
        var current = ""
        for word in text.split(separator: " ").map(String.init) {
            let next = current.isEmpty ? word : current + " " + word
            let width = c.resolve(Text(next).font(font)).measure(in: unbounded).width
            if !current.isEmpty && width > max {
                lines.append(current)
                current = word
            } else {
                current = next
            }
        }
        if !current.isEmpty { lines.append(current) }
        return lines
    }

    /// The three pages side by side (page i starts at i × width), white on transparent.
    private static func drawPages(_ c: GraphicsContext, _ g: Geo) {
        let s = min(1, g.u)
        let titleSize = (25 * s).rounded()
        let kickerFont = Font.system(size: 11, weight: .bold)
        let titleFont = Font.system(size: titleSize, weight: .bold)
        let bodyFont = Font.system(size: 15, weight: .regular)
        let soft = Color.white.opacity(0.78)
        for i in 0..<LighthouseBeamWelcomeMotion.pages {
            let copy = pageCopy[i]
            let x = Double(i) * g.w + g.textX
            var y = g.textY - g.contentY
            icon(c, i, x, y)
            line(c, Text("0\(i + 1)  ·  \(copy.kicker)").font(kickerFont).foregroundStyle(soft), x: x + 40, baseline: y + 19)
            y += 40
            for text in wrap(c, copy.title, font: titleFont, max: g.textW) {
                y += titleSize * 1.18
                line(c, Text(text).font(titleFont).foregroundStyle(Color.white), x: x, baseline: y)
            }
            y += 6
            for text in wrap(c, copy.body, font: bodyFont, max: g.textW) {
                y += 15 * 1.4
                line(c, Text(text).font(bodyFont).foregroundStyle(soft), x: x, baseline: y)
            }
        }
    }

    @MainActor private static var cache: (geo: Geo, scale: Double, image: Image)?

    /// The pages' image, rendered once per layout at the screen's scale.
    @MainActor
    static func content(_ g: Geo, scale: Double) -> Image {
        if let cache, cache.geo == g, cache.scale == scale { return cache.image }
        let width = g.w * Double(LighthouseBeamWelcomeMotion.pages)
        let renderer = ImageRenderer(content:
            Canvas { c, _ in drawPages(c, g) }
                .frame(width: width, height: g.contentH)
        )
        renderer.scale = scale
        renderer.isOpaque = false
        let image: Image
        if let ui = renderer.uiImage {
            image = Image(uiImage: ui.preparingForDisplay() ?? ui)
        } else {
            image = Image(systemName: "circle").resizable()
        }
        cache = (g, scale, image)
        return image
    }

    // MARK: - The tower

    /// The lighthouse on its rocks, seen at dusk: rim light from the sky on the right, lamp-lit top.
    static func drawTower(_ c: GraphicsContext, _ g: Geo) {
        let u = g.u
        let x = g.lampX
        let baseW = 25 * u
        let topW = 15 * u
        let top = g.galleryY + 6 * u
        let base = g.baseY

        // Rocks: a low dark mass with a lighter top edge.
        var rocks = Path()
        rocks.move(to: CGPoint(x: x - 92 * u, y: g.horizon + 34 * u))
        rocks.addCurve(to: CGPoint(x: x - 30 * u, y: base - 8 * u), control1: CGPoint(x: x - 80 * u, y: g.horizon + 4 * u), control2: CGPoint(x: x - 52 * u, y: g.horizon - 6 * u))
        rocks.addCurve(to: CGPoint(x: x + 38 * u, y: base - 4 * u), control1: CGPoint(x: x - 10 * u, y: base - 16 * u), control2: CGPoint(x: x + 22 * u, y: base - 14 * u))
        rocks.addCurve(to: CGPoint(x: x + 96 * u, y: g.horizon + 34 * u), control1: CGPoint(x: x + 58 * u, y: g.horizon + 6 * u), control2: CGPoint(x: x + 78 * u, y: g.horizon + 18 * u))
        rocks.closeSubpath()
        c.fill(rocks, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x2A2B4D), location: 0),
                .init(color: rgb(0x141936), location: 0.4),
                .init(color: rgb(0x0A0E22), location: 1),
            ]),
            startPoint: CGPoint(x: x, y: base - 16 * u),
            endPoint: CGPoint(x: x, y: g.horizon + 34 * u)
        ))

        // Tower: tapered, shaded as a cylinder (dark left, dusk-lit right edge), two dim red bands.
        var tower = Path()
        tower.move(to: CGPoint(x: x - baseW, y: base))
        tower.addLine(to: CGPoint(x: x - topW, y: top))
        tower.addLine(to: CGPoint(x: x + topW, y: top))
        tower.addLine(to: CGPoint(x: x + baseW, y: base))
        tower.closeSubpath()
        c.fill(tower, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x1E2140), location: 0),
                .init(color: rgb(0x45466C), location: 0.45),
                .init(color: rgb(0x6E6A92), location: 0.82),
                .init(color: rgb(0x8D7FA6), location: 1),
            ]),
            startPoint: CGPoint(x: x - baseW, y: 0),
            endPoint: CGPoint(x: x + baseW, y: 0)
        ))
        var inside = c
        inside.clip(to: tower)
        let span = base - top
        for f in [0.22, 0.56] {
            inside.fill(Path(CGRect(x: x - baseW - 2, y: top + span * f, width: baseW * 2 + 4, height: span * 0.14)), with: .color(Color(.sRGB, red: 150 / 255, green: 52 / 255, blue: 72 / 255, opacity: 0.55)))
        }
        // Lamp light falling down the tower from the gallery.
        inside.fill(Path(CGRect(x: x - baseW, y: top, width: baseW * 2, height: span * 0.4)), with: .linearGradient(
            Gradient(colors: [rgb(0xFFD696, 0.28), rgb(0xFFD696, 0)]),
            startPoint: CGPoint(x: 0, y: top),
            endPoint: CGPoint(x: 0, y: top + span * 0.4)
        ))
        // Two small lit windows.
        for f in [0.42, 0.74] {
            let wy = top + span * f
            c.fill(Path(roundedRect: CGRect(x: x - 2.2 * u, y: wy, width: 4.4 * u, height: 7 * u), cornerRadius: 2 * u), with: .color(rgb(0xFFCE82, 0.85)))
        }

        // Gallery deck and railing.
        let deckY = g.galleryY
        c.fill(Path(roundedRect: CGRect(x: x - 23 * u, y: deckY + 3 * u, width: 46 * u, height: 4 * u), cornerRadius: 2 * u), with: .color(rgb(0x24264A)))
        let rail = GraphicsContext.Shading.color(rgb(0xAAA0CD, 0.75))
        var rails = Path()
        rails.move(to: CGPoint(x: x - 21 * u, y: deckY - 4 * u))
        rails.addLine(to: CGPoint(x: x + 21 * u, y: deckY - 4 * u))
        for i in 0...6 {
            let rx = x - 21 * u + Double(i) * 7 * u
            rails.move(to: CGPoint(x: rx, y: deckY - 4 * u))
            rails.addLine(to: CGPoint(x: rx, y: deckY + 3 * u))
        }
        c.stroke(rails, with: rail, style: round(1.1 * u))

        // Lantern room: glass panes around the lamp, then the cap and a vane.
        let roomTop = g.lampY - 13 * u
        c.fill(Path(CGRect(x: x - 11 * u, y: roomTop, width: 22 * u, height: deckY - roomTop - 4 * u)), with: .color(rgb(0xFFDEA0, 0.18)))
        var mullions = Path()
        for dx in [-11.0, -4, 4, 11] {
            mullions.move(to: CGPoint(x: x + dx * u, y: roomTop))
            mullions.addLine(to: CGPoint(x: x + dx * u, y: deckY - 4 * u))
        }
        c.stroke(mullions, with: .color(rgb(0x1E203C, 0.9)), style: round(1.4 * u))
        var cap = Path()
        cap.move(to: CGPoint(x: x - 15 * u, y: roomTop))
        cap.addCurve(to: CGPoint(x: x + 15 * u, y: roomTop), control1: CGPoint(x: x - 12 * u, y: roomTop - 12 * u), control2: CGPoint(x: x + 12 * u, y: roomTop - 12 * u))
        cap.closeSubpath()
        c.fill(cap, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x15172F), location: 0),
                .init(color: rgb(0x2E3056), location: 0.6),
                .init(color: rgb(0x6A5F8C), location: 1),
            ]),
            startPoint: CGPoint(x: x - 15 * u, y: 0),
            endPoint: CGPoint(x: x + 15 * u, y: 0)
        ))
        var vane = Path()
        vane.move(to: CGPoint(x: x, y: roomTop - 9 * u))
        vane.addLine(to: CGPoint(x: x, y: roomTop - 18 * u))
        c.stroke(vane, with: .color(rgb(0x2A2C4E)), style: round(1.4 * u))
        c.fill(circle(x, roomTop - 18 * u, 1.6 * u), with: .color(rgb(0x2A2C4E)))
    }

    // MARK: - Per frame

    /// The lamp's steady glow and its flare when the beam swings towards the viewer.
    static func drawLamp(_ c: GraphicsContext, _ g: Geo, flare: Double) {
        let u = g.u
        c.drawLayer { l in
            l.addFilter(.blur(radius: 5 * u))
            l.fill(circle(g.lampX, g.lampY, 5.5 * u), with: .color(rgb(0xFFE0A0, 0.9)))
        }
        c.fill(circle(g.lampX, g.lampY, 3 * u), with: .color(rgb(0xFFF6E2)))
        if flare > 0.01 {
            let r = (40 + 90 * flare) * u
            c.drawLayer { l in
                l.opacity = min(1, flare * 1.1)
                l.fill(circle(g.lampX, g.lampY, r), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: rgb(0xFFECC4, 0.95), location: 0),
                        .init(color: rgb(0xFFD696, 0.35), location: 0.25),
                        .init(color: rgb(0xFFC88C, 0), location: 1),
                    ]),
                    center: CGPoint(x: g.lampX, y: g.lampY),
                    startRadius: 0,
                    endRadius: r
                ))
            }
            // A short horizontal streak, like a lens flare.
            c.drawLayer { l in
                l.addFilter(.blur(radius: 2 * u))
                l.fill(
                    Path(roundedRect: CGRect(x: g.lampX - r * 1.4, y: g.lampY - 1.2 * u, width: r * 2.8, height: 2.4 * u), cornerRadius: u),
                    with: .color(rgb(0xFFECC8, flare * 0.7))
                )
            }
        }
    }

    /// Paging dots: the active one is a pill; `page` is fractional while sliding.
    static func drawDots(_ c: GraphicsContext, cx: Double, y: Double, page: Double) {
        let gap = 8.0
        let dot = 7.0
        let wide = 22.0
        var widths: [Double] = []
        var total = 0.0
        for i in 0..<LighthouseBeamWelcomeMotion.pages {
            let near = max(0, 1 - abs(page - Double(i)))
            let wdt = dot + (wide - dot) * near
            widths.append(wdt)
            total += wdt + (i > 0 ? gap : 0)
        }
        var x = cx - total / 2
        for i in 0..<LighthouseBeamWelcomeMotion.pages {
            let near = max(0, 1 - abs(page - Double(i)))
            c.fill(
                Path(roundedRect: CGRect(x: x, y: y - dot / 2, width: widths[i], height: dot), cornerRadius: dot / 2),
                with: .color(cream.opacity(0.32 + 0.63 * near))
            )
            x += widths[i] + gap
        }
    }
}
