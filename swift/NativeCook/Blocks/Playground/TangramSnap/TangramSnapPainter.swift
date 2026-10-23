import SwiftUI

/// Draws the board, the figure, the pieces, the flood of colour and the figures to choose from,
/// in the order and with the paints of the React Native picture.
enum TangramSnapPainter {
    typealias M = TangramSnapModel
    typealias E = TangramSnapEngine
    typealias RGB = (Double, Double, Double)

    // Bevel width (pt), the gap between pieces (pt) and how much a lifted piece grows.
    private static let bevel = 3.4
    private static let gap = 0.9
    private static let liftGrow = 0.05

    static let ink = Color(red: 0xF2 / 255, green: 0xE9 / 255, blue: 0xDC / 255)
    static let muted = Color(red: 0xA2 / 255, green: 0x94 / 255, blue: 0x83 / 255)
    static let board: [Color] = [
        Color(red: 0x2A / 255, green: 0x23 / 255, blue: 0x1D / 255),
        Color(red: 0x17 / 255, green: 0x13 / 255, blue: 0x10 / 255),
        Color(red: 0x0C / 255, green: 0x0A / 255, blue: 0x08 / 255),
    ]
    /// Lacquer colours, in piece order.
    private static let lacquer: [RGB] = [
        (196, 64, 42),
        (44, 62, 116),
        (210, 154, 44),
        (46, 126, 98),
        (122, 50, 88),
        (42, 136, 152),
        (222, 200, 160),
    ]
    private static let light = (-0.6, -0.8)

    private static func rgba(_ c: RGB, _ a: Double) -> Color {
        Color(.sRGB, red: c.0 / 255, green: c.1 / 255, blue: c.2 / 255, opacity: max(0, min(1, a)))
    }

    private static func mix(_ a: RGB, _ b: RGB, _ t: Double) -> RGB {
        (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t)
    }

    private static func addPolygon(_ path: inout Path, _ p: [Double]) {
        path.move(to: CGPoint(x: p[0], y: p[1]))
        for i in stride(from: 2, to: p.count, by: 2) { path.addLine(to: CGPoint(x: p[i], y: p[i + 1])) }
        path.closeSubpath()
    }

    private static func polygon(_ p: [Double]) -> Path {
        var path = Path()
        addPolygon(&path, p)
        return path
    }

    static func draw(in ctx: inout GraphicsContext, engine: E, insetTop: Double) {
        let l = engine.layout
        guard engine.ready, l.width > 0 else { return }
        let unit = l.unit
        let p = engine.pieces
        drawBackdrop(in: &ctx, l)

        // The figure: the union of its slots, wherever the morph has them.
        var figure = Path()
        for k in 0..<7 {
            let at = engine.slotNow(k)
            addPolygon(&figure, E.corners(k, at.x, at.y, at.angle, at.flip, unit, 1))
        }
        let fillShow = E.smooth(engine.fill)
        let from = M.silhouettes[engine.from].color
        let to = M.silhouettes[engine.puzzle].color
        let solidRGB = engine.morph < 1 ? mix(from, to, E.easeInOut(engine.morph)) : to

        // The empty outline: a faint inlay and a fine line around its outer edge only.
        let outlineA = 1 - fillShow
        if outlineA > 0.01 {
            ctx.fill(figure, with: .color(rgba((255, 238, 214), 0.06 * outlineA)))
            ctx.drawLayer { layer in
                layer.opacity = outlineA
                layer.stroke(figure, with: .color(rgba((255, 232, 204), 0.55)), style: StrokeStyle(lineWidth: 2.6, lineJoin: .round))
                layer.blendMode = .clear
                layer.fill(figure, with: .color(.black))
            }
        }
        // The slot a held piece is being drawn into glows.
        if engine.magnetSlot >= 0 {
            let at = engine.slotNow(engine.magnetSlot)
            var glow = ctx
            glow.addFilter(.blur(radius: 6))
            glow.fill(
                polygon(E.corners(engine.magnetSlot, at.x, at.y, at.angle, at.flip, unit, 1)),
                with: .color(rgba(lacquer[engine.held >= 0 ? engine.held : 0], 0.28))
            )
        }

        // Pieces, bottom first.
        for o in 0..<7 {
            let i = p.order[o]
            let grow = 1 + liftGrow * p.lift[i] - (gap * (1 - fillShow)) / unit
            let pts = E.corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, grow)
            drawPiece(in: &ctx, i, pts, p.x[i], p.y[i], p.angle[i], p.flip[i], p.lift[i], p.slot[i] >= 0 ? fillShow : 0, solidRGB)
        }

        // A snap sends a ring of light out from the slot.
        for k in 0..<7 where engine.pulse[k] >= 0 {
            let u = engine.pulse[k] / 0.6
            let at = engine.slotNow(k)
            ctx.stroke(
                polygon(E.corners(k, at.x, at.y, at.angle, at.flip, unit, 1 + 0.18 * E.smooth(u))),
                with: .color(rgba((255, 240, 214), 0.6 * (1 - u))),
                style: StrokeStyle(lineWidth: 2, lineJoin: .round)
            )
        }

        // Solved: the colour floods out from the last piece and the figure becomes one shape.
        if fillShow > 0.001 {
            let reach = max(M.maxHalfW, M.maxHalfH) * unit * 2.6
            var flood = ctx
            if fillShow < 1 {
                let r = reach * fillShow
                flood.clip(to: Path(ellipseIn: CGRect(x: engine.revealX - r, y: engine.revealY - r, width: r * 2, height: r * 2)))
            }
            var shade = flood
            shade.addFilter(.blur(radius: 9))
            shade.translateBy(x: 0, y: 6)
            shade.fill(figure, with: .color(rgba((0, 0, 0), 0.4 * fillShow)))
            let a = E.smooth(fillShow * 1.4)
            let topLeft = CGPoint(x: l.boardX - M.maxHalfW * unit, y: l.boardY - M.maxHalfH * unit)
            flood.fill(figure, with: .linearGradient(
                Gradient(colors: [rgba(mix(solidRGB, (255, 255, 255), 0.16), a), rgba(mix(solidRGB, (0, 0, 0), 0.18), a)]),
                startPoint: topLeft,
                endPoint: CGPoint(x: l.boardX + M.maxHalfW * unit, y: l.boardY + M.maxHalfH * unit)
            ))
            // A lacquer sheen over the whole figure.
            flood.fill(figure, with: .linearGradient(
                Gradient(colors: [rgba((255, 255, 255), 0.22), rgba((255, 255, 255), 0)]),
                startPoint: topLeft,
                endPoint: CGPoint(x: l.boardX, y: l.boardY)
            ))
        }

        // The figures to choose from, along the bottom.
        let scale = 40 / (2 * max(M.maxHalfW, M.maxHalfH))
        for f in 0..<M.silhouettes.count {
            let cx = l.width / 2 + (Double(f) - 1.5) * 64
            let sil = M.silhouettes[f]
            var mini = Path()
            for k in 0..<7 {
                let slot = sil.slots[k]
                addPolygon(&mini, E.corners(
                    k, cx + slot.x * scale, l.thumbY + slot.y * scale, (Double(slot.turns[0]) * Double.pi) / 4, Double(slot.flips[0]), scale, 1
                ))
            }
            let current = f == engine.puzzle
            ctx.fill(mini, with: .color(current ? rgba(sil.color, 0.95) : rgba((242, 233, 220), 0.28)))
            if current {
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 2, y: l.thumbY + 27 - 2, width: 4, height: 4)), with: .color(rgba((242, 233, 220), 0.8)))
            }
        }

        // Pieces in place, top right: right-aligned, on the React Native baselines.
        let right = l.width - 24
        let readTop = insetTop + 12
        drawRight(in: &ctx, Text("\(engine.placedCount) / 7").font(.custom("AvenirNext-DemiBold", size: 22)).foregroundStyle(ink), right, readTop + 38)
        drawRight(in: &ctx, Text("IN PLACE").font(.custom("AvenirNext-DemiBold", size: 10.5)).foregroundStyle(muted), right, readTop + 11)
    }

    /// Draws `text` with its right edge at `right` and its baseline at `baseline`.
    private static func drawRight(in ctx: inout GraphicsContext, _ text: Text, _ right: Double, _ baseline: Double) {
        let resolved = ctx.resolve(text)
        let big = CGSize(width: 1000, height: 1000)
        let size = resolved.measure(in: big)
        let ascent = resolved.firstBaseline(in: big)
        ctx.draw(resolved, at: CGPoint(x: right - size.width, y: baseline - ascent), anchor: .topLeading)
    }

    /// The board: dark lacquered wood, lit in a soft pool over the figure, and a felt-lined well.
    private static func drawBackdrop(in ctx: inout GraphicsContext, _ l: TangramSnapLayout) {
        ctx.fill(
            Path(CGRect(x: 0, y: 0, width: l.width, height: l.height)),
            with: .radialGradient(Gradient(colors: board), center: CGPoint(x: l.boardX, y: l.boardY), startRadius: 0, endRadius: max(l.width, l.height) * 0.8)
        )
        // The well that holds the loose pieces.
        let w = (M.looseHalfW + 0.55) * l.unit
        let h = (M.looseHalfH + 0.4) * l.unit
        let rect = CGRect(x: l.looseX - w, y: l.looseY - h, width: w * 2, height: h * 2)
        let well = Path(roundedRect: rect, cornerRadius: 22, style: .circular)
        ctx.fill(well, with: .color(rgba((0, 0, 0), 0.22)))
        var inner = ctx
        inner.clip(to: well)
        inner.addFilter(.blur(radius: 8))
        inner.stroke(
            Path(roundedRect: rect.insetBy(dx: -4, dy: -4), cornerRadius: 26, style: .circular),
            with: .color(rgba((0, 0, 0), 0.35)),
            lineWidth: 14
        )
        ctx.stroke(well, with: .color(rgba((255, 236, 210), 0.07)), lineWidth: 1)
    }

    /// One lacquered piece: its shadow, the wood under the lacquer, bevels, gloss and edge.
    private static func drawPiece(
        in ctx: inout GraphicsContext, _ k: Int, _ points: [Double], _ x: Double, _ y: Double, _ angle: Double, _ flip: Double, _ lift: Double, _ solid: Double, _ solidRGB: RGB
    ) {
        let body = polygon(points)
        // The shadow, further and softer as the piece lifts.
        var shadow = ctx
        shadow.addFilter(.blur(radius: 2.2 + 7 * lift))
        shadow.translateBy(x: 2 + 8 * lift, y: 3 + 13 * lift)
        shadow.fill(body, with: .color(rgba((0, 0, 0), 0.5 - 0.15 * lift)))

        let tint = mix(lacquer[k], solidRGB, solid)
        ctx.fill(body, with: .shader(ShaderLibrary.tangramSnapLacquer(
            .float2(x, y), .float(angle), .float3(tint.0 / 255, tint.1 / 255, tint.2 / 255), .float(Double(k) * 3.7 + 1.3), .float(1 - solid)
        )))

        // Bevels: each edge's slope lit or shaded by how it faces the light.
        let fade = 1 - solid
        // A piece seen edge-on mid-flip is too thin for a bevel (the inset would fold over itself).
        if fade > 0.01 && abs(cos(Double.pi * flip)) > 0.2 {
            let inner = M.inset(points, bevel)
            let n = points.count / 2
            var area = 0.0
            for i in 0..<n {
                let j = (i + 1) % n
                area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
            }
            let side = area > 0 ? 1.0 : -1.0
            for i in 0..<n {
                let j = (i + 1) % n
                let ex = points[j * 2] - points[i * 2]
                let ey = points[j * 2 + 1] - points[i * 2 + 1]
                let len = hypot(ex, ey)
                let l = len == 0 ? 1 : len
                // Outward normal.
                let nx = (ey / l) * side
                let ny = (-ex / l) * side
                let facing = nx * light.0 + ny * light.1
                let strip = polygon([points[i * 2], points[i * 2 + 1], points[j * 2], points[j * 2 + 1], inner[j * 2], inner[j * 2 + 1], inner[i * 2], inner[i * 2 + 1]])
                ctx.fill(strip, with: .color(facing > 0 ? rgba((255, 244, 226), 0.36 * facing * fade) : rgba((20, 8, 2), 0.42 * -facing * fade)))
            }
        }
        // Gloss: the lacquer catches the light across its upper left.
        let b = body.boundingRect
        ctx.fill(body, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgba((255, 255, 255), 0.24), location: 0),
                .init(color: rgba((255, 255, 255), 0.04), location: 0.45),
                .init(color: rgba((255, 255, 255), 0), location: 1),
            ]),
            startPoint: CGPoint(x: b.minX, y: b.minY),
            endPoint: CGPoint(x: b.minX + b.width * 0.7, y: b.minY + b.height * 0.7)
        ))
        ctx.stroke(body, with: .color(rgba((12, 6, 2), 0.45 * fade + 0.1)), style: StrokeStyle(lineWidth: 0.8, lineJoin: .round))
    }
}
