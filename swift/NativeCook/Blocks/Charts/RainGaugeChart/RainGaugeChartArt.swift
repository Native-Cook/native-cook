import SwiftUI

/// Hero geometry in screen points.
struct RainGaugeChartGeo {
    let ox: Double
    let oy: Double
    let w: Double
    let h: Double
    /// Tube centres, half width, top (y0) and bottom (y1).
    let cx: [Double]
    let pitch: Double
    let hw: Double
    let y0: Double
    let y1: Double
    /// The rack: top board's top edge, the boards' left and right ends, the bed.
    let board: Double
    let left: Double
    let right: Double
    let ground: Double

    static let liftPt = 40.0
    static let liftScale = 0.1

    init(_ r: CGRect) {
        ox = r.minX
        oy = r.minY
        w = r.width
        h = r.height
        let x0 = ox + 52
        let x1 = ox + w - 22
        let step = (x1 - x0) / Double(RainGaugeChartData.tubes)
        pitch = step
        cx = (0..<RainGaugeChartData.tubes).map { x0 + step * (Double($0) + 0.5) }
        hw = min(15, pitch * 0.3)
        y1 = oy + h - 50
        let tubeH = max(150, min(300, h - 200))
        y0 = y1 - tubeH
        board = y0 + tubeH * 0.22
        left = x0 - 16
        right = x1 + 10
        ground = y1 + 12
    }

    /// The water's top (screen y, before any lift) for a level in mm.
    func levelY(_ mm: Double) -> Double {
        y1 - 4 - (mm / RainGaugeChartData.capacity) * (y1 - y0 - 26)
    }
}

private func rainGaugeChartColor(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
    Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
}

private func rainGaugeChartHex(_ hex: UInt32) -> Color {
    Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
}

enum RainGaugeChartArt {
    typealias D = RainGaugeChartData
    typealias G = RainGaugeChartGeo

    static func tubePath(_ hw: Double, top: Double, closed: Bool) -> Path {
        let k = 0.5523
        var p = Path()
        p.move(to: CGPoint(x: -hw, y: top))
        p.addLine(to: CGPoint(x: -hw, y: -hw))
        p.addCurve(to: CGPoint(x: 0, y: 0), control1: CGPoint(x: -hw, y: -hw + hw * k), control2: CGPoint(x: -hw * k, y: 0))
        p.addCurve(to: CGPoint(x: hw, y: -hw), control1: CGPoint(x: hw * k, y: 0), control2: CGPoint(x: hw, y: -hw + hw * k))
        p.addLine(to: CGPoint(x: hw, y: top))
        if closed { p.closeSubpath() }
        return p
    }

    // MARK: Static layers

    private static func woodBoard(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, seed: Double) {
        let rect = CGRect(x: x, y: y, width: w, height: h)
        let r = Path(roundedRect: rect, cornerRadius: 3)
        var sh = ctx
        sh.addFilter(.blur(radius: 5))
        sh.fill(Path(roundedRect: rect.offsetBy(dx: 2, dy: 5), cornerRadius: 3), with: .color(rainGaugeChartColor(6, 12, 12, 0.45)))
        ctx.fill(r, with: .linearGradient(
            Gradient(stops: [
                .init(color: rainGaugeChartHex(0xB07B4C), location: 0),
                .init(color: rainGaugeChartHex(0x8E5D36), location: 0.5),
                .init(color: rainGaugeChartHex(0x6A4226), location: 1),
            ]),
            startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + h)
        ))
        var inner = ctx
        inner.clip(to: r)
        for i in 0..<9 {
            let fi = Double(i)
            let gy = y + 2 + (h - 4) * rainGaugeChartHash(seed + fi * 1.7)
            var b = Path()
            b.move(to: CGPoint(x: x, y: gy))
            let n = 6.0
            for j in 1...6 {
                let fj = Double(j)
                let px = x + w * fj / n
                b.addQuadCurve(
                    to: CGPoint(x: px, y: gy + (rainGaugeChartHash(seed + fi * 5.3 + fj) - 0.5) * 1.6),
                    control: CGPoint(x: px - w / n / 2, y: gy + (rainGaugeChartHash(seed + fi * 3.1 + fj) - 0.5) * 3)
                )
            }
            inner.stroke(b, with: .color(rainGaugeChartColor(58, 32, 16, 0.18 + 0.22 * rainGaugeChartHash(seed + fi * 9.1))),
                         lineWidth: 0.5 + rainGaugeChartHash(seed + fi) * 0.9)
        }
        inner.stroke(
            Path(ellipseIn: CGRect(x: x + w * (0.2 + 0.6 * rainGaugeChartHash(seed + 40)), y: y + h * 0.3, width: 7, height: h * 0.36)),
            with: .color(rainGaugeChartColor(60, 34, 18, 0.4)), lineWidth: 0.8
        )
        var top = Path()
        top.move(to: CGPoint(x: x + 3, y: y + 0.6))
        top.addLine(to: CGPoint(x: x + w - 3, y: y + 0.6))
        ctx.stroke(top, with: .color(rainGaugeChartColor(255, 222, 176, 0.42)), lineWidth: 1)
        var bottom = Path()
        bottom.move(to: CGPoint(x: x + 3, y: y + h - 0.6))
        bottom.addLine(to: CGPoint(x: x + w - 3, y: y + h - 0.6))
        ctx.stroke(bottom, with: .color(rainGaugeChartColor(40, 22, 10, 0.55)), lineWidth: 1)
    }

    /// The rack's posts and boards, drawn over the tubes (the tubes stand in holes through them).
    static func drawRackFront(_ ctx: GraphicsContext, _ g: G) {
        let postW = 10.0
        woodBoard(ctx, g.left - 2, g.board - 4, postW, g.ground - g.board + 4, seed: 3)
        woodBoard(ctx, g.right - postW + 2, g.board - 4, postW, g.ground - g.board + 4, seed: 7)
        woodBoard(ctx, g.left, g.board, g.right - g.left, 18, seed: 11)
        woodBoard(ctx, g.left, g.y1 - 4, g.right - g.left, 16, seed: 23)
        for i in 0..<D.tubes {
            ctx.fill(Path(ellipseIn: CGRect(x: g.cx[i] - g.hw - 2.5, y: g.board - 2, width: g.hw * 2 + 5, height: 4.5)),
                     with: .color(rainGaugeChartColor(30, 16, 8, 0.75)))
            ctx.stroke(Path(ellipseIn: CGRect(x: g.cx[i] - g.hw - 2.5, y: g.board - 1.6, width: g.hw * 2 + 5, height: 4.5)),
                       with: .color(rainGaugeChartColor(255, 220, 170, 0.3)), lineWidth: 0.8)
        }
    }

    /// The garden bed under the rack, a far hedge, grain and a vignette.
    static func drawBackdrop(_ ctx: GraphicsContext, _ g: G, size: CGSize) {
        let width = size.width
        let height = size.height
        var hedge = Path()
        hedge.move(to: CGPoint(x: 0, y: g.ground - 40))
        for i in 0...12 {
            let fi = Double(i)
            let x = width * fi / 12
            hedge.addQuadCurve(
                to: CGPoint(x: x, y: g.ground - 44 - 14 * rainGaugeChartHash(fi * 4.1)),
                control: CGPoint(x: x - width / 24, y: g.ground - 64 - 22 * rainGaugeChartHash(fi * 2.3))
            )
        }
        hedge.addLine(to: CGPoint(x: width, y: height))
        hedge.addLine(to: CGPoint(x: 0, y: height))
        hedge.closeSubpath()
        ctx.fill(hedge, with: .linearGradient(
            Gradient(stops: [
                .init(color: rainGaugeChartColor(28, 52, 46, 0), location: 0),
                .init(color: rainGaugeChartColor(24, 46, 40, 0.7), location: 0.5),
                .init(color: rainGaugeChartHex(0x172B26), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: g.ground - 90), endPoint: CGPoint(x: 0, y: g.ground)
        ))
        ctx.fill(Path(CGRect(x: 0, y: g.ground, width: width, height: height - g.ground)), with: .linearGradient(
            Gradient(stops: [
                .init(color: rainGaugeChartHex(0x2B231C), location: 0),
                .init(color: rainGaugeChartHex(0x211A15), location: 0.4),
                .init(color: rainGaugeChartHex(0x17120F), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: g.ground), endPoint: CGPoint(x: 0, y: height)
        ))
        let full = Path(CGRect(origin: .zero, size: size))
        if let grain = RainGaugeChartGrain.image {
            var gr = ctx
            gr.blendMode = .overlay
            gr.opacity = 0.07
            gr.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
        }
        ctx.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: rainGaugeChartColor(2, 8, 10, 0.2), location: 0.62),
                .init(color: rainGaugeChartColor(2, 8, 10, 0.55), location: 1),
            ]),
            center: CGPoint(x: width / 2, y: height * 0.45), startRadius: 0, endRadius: max(width, height) * 0.7
        ))
    }

    // MARK: Moving layers

    /// One glass gauge with its water, in local coordinates (bottom centre = 0, 0).
    static func drawTube(_ ctx: GraphicsContext, _ g: G, _ i: Int, level: Double, slosh: Double) {
        let hw = g.hw
        let top = g.y0 - g.y1
        let outer = tubePath(hw, top: top, closed: true)
        let inner = tubePath(hw - 2, top: top, closed: true)
        ctx.fill(outer, with: .linearGradient(
            Gradient(stops: [
                .init(color: rainGaugeChartColor(214, 238, 246, 0.2), location: 0),
                .init(color: rainGaugeChartColor(214, 238, 246, 0.05), location: 0.25),
                .init(color: rainGaugeChartColor(214, 238, 246, 0.02), location: 0.55),
                .init(color: rainGaugeChartColor(214, 238, 246, 0.08), location: 0.8),
                .init(color: rainGaugeChartColor(214, 238, 246, 0.24), location: 1),
            ]),
            startPoint: CGPoint(x: -hw, y: 0), endPoint: CGPoint(x: hw, y: 0)
        ))

        // Water, clipped to the bore; the surface climbs the walls (meniscus) and tilts with the slosh.
        let wy = g.levelY(level) - g.y1
        if level > 0.02 {
            var wc = ctx
            wc.clip(to: inner)
            let wl = wy - slosh - 2.4
            let wr = wy + slosh - 2.4
            var surface = Path()
            surface.move(to: CGPoint(x: -hw, y: wl))
            surface.addQuadCurve(to: CGPoint(x: hw, y: wr), control: CGPoint(x: 0, y: wy + 2.4))
            var water = surface
            water.addLine(to: CGPoint(x: hw, y: 2))
            water.addLine(to: CGPoint(x: -hw, y: 2))
            water.closeSubpath()
            var wp = wc
            wp.opacity = 0.92
            wp.fill(water, with: .linearGradient(
                Gradient(stops: [
                    .init(color: rainGaugeChartHex(0x1C5468), location: 0),
                    .init(color: rainGaugeChartHex(0x3D8BA6), location: 0.22),
                    .init(color: rainGaugeChartHex(0x86CBDF), location: 0.38),
                    .init(color: rainGaugeChartHex(0x4C9DB7), location: 0.72),
                    .init(color: rainGaugeChartHex(0x1A4C60), location: 1),
                ]),
                startPoint: CGPoint(x: -hw, y: 0), endPoint: CGPoint(x: hw, y: 0)
            ))
            wc.fill(water, with: .linearGradient(
                Gradient(stops: [
                    .init(color: rainGaugeChartColor(200, 245, 255, 0.28), location: 0),
                    .init(color: rainGaugeChartColor(10, 40, 52, 0), location: 0.18),
                    .init(color: rainGaugeChartColor(6, 26, 36, 0.4), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: wy), endPoint: CGPoint(x: 0, y: 0)
            ))
            // Two small bubbles stuck to the glass.
            for k in 0..<2 {
                let fk = Double(k)
                let fi = Double(i)
                let by = -6 - rainGaugeChartHash(fi * 7 + fk) * 40
                if by > wy + 4 {
                    let r = 0.9 + rainGaugeChartHash(fi + fk) * 0.7
                    let bx = (rainGaugeChartHash(fi * 3 + fk * 5) - 0.5) * hw
                    wc.stroke(Path(ellipseIn: CGRect(x: bx - r, y: by - r, width: 2 * r, height: 2 * r)),
                              with: .color(rainGaugeChartColor(235, 252, 255, 0.45)), lineWidth: 0.7)
                }
            }
            wc.stroke(surface, with: .color(rainGaugeChartColor(228, 250, 255, 0.9)), lineWidth: 1.2)
        }

        // Front glass: the bore's thickness, a bright left stripe, a thin right one, ticks and the rim.
        ctx.stroke(inner, with: .color(rainGaugeChartColor(8, 26, 34, 0.22)), lineWidth: 1.4)
        ctx.fill(
            Path(roundedRect: CGRect(x: -hw + 3, y: top + 6, width: 2.4, height: -top - hw - 4), cornerRadius: 1.2),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.62), location: 0),
                    .init(color: .white.opacity(0.22), location: 0.5),
                    .init(color: .white.opacity(0.08), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: -hw)
            )
        )
        ctx.stroke(tubePath(hw, top: top, closed: false), with: .color(rainGaugeChartColor(226, 244, 250, 0.42)), lineWidth: 1)
        var right = Path()
        right.move(to: CGPoint(x: hw - 3, y: top + 10))
        right.addLine(to: CGPoint(x: hw - 3, y: -hw - 2))
        ctx.stroke(right, with: .color(.white.opacity(0.18)), lineWidth: 1)
        var ticks = Path()
        for mm in 1...Int(D.capacity) {
            let ty = g.levelY(Double(mm)) - g.y1
            let len = mm % 5 == 0 ? 7.0 : 3.5
            ticks.move(to: CGPoint(x: hw - 1.5 - len, y: ty))
            ticks.addLine(to: CGPoint(x: hw - 1.5, y: ty))
        }
        ctx.stroke(ticks, with: .color(rainGaugeChartColor(240, 250, 252, 0.55)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        ctx.stroke(Path(ellipseIn: CGRect(x: -hw - 0.8, y: top - 2.2, width: hw * 2 + 1.6, height: 4.4)),
                   with: .color(rainGaugeChartColor(236, 248, 252, 0.6)), lineWidth: 1.3)
    }

    /// The tubes still standing in the rack (behind its boards).
    static func drawRackTubes(_ ctx: GraphicsContext, _ g: G, _ p: RainGaugeChartPose) {
        for i in 0..<D.tubes {
            if i == p.liftIdx && p.lift > 0.001 { continue }
            var c = ctx
            c.translateBy(x: g.cx[i], y: g.y1)
            drawTube(c, g, i, level: p.levels[i], slosh: p.slosh[i])
        }
    }

    /// Where a drop falling at x stops: the water in a tube, the rack's top board, or the bed.
    private static func landing(_ g: G, _ x: Double, _ p: RainGaugeChartPose) -> (Double, Bool) {
        for i in 0..<D.tubes where abs(x - g.cx[i]) < g.hw - 2.5 {
            let up = i == p.liftIdx ? p.lift * G.liftPt : 0
            return (g.levelY(p.levels[i]) - up, true)
        }
        if x > g.left && x < g.right { return (g.board, false) }
        return (g.ground + 4, false)
    }

    /// The lifted tube over the rack, then the rain.
    static func drawFront(_ ctx: GraphicsContext, _ g: G, _ p: RainGaugeChartPose) {
        if p.liftIdx >= 0 && p.lift > 0.001 {
            let i = p.liftIdx
            let sc = 1 + G.liftScale * p.lift
            // Its shadow on the base board grows softer as it rises.
            var sh = ctx
            sh.addFilter(.blur(radius: 3 + 5 * p.lift))
            sh.fill(Path(ellipseIn: CGRect(x: g.cx[i] - g.hw - 4, y: g.y1 - 6, width: g.hw * 2 + 8, height: 5)),
                    with: .color(rainGaugeChartColor(10, 6, 4, 0.35 * p.lift)))
            var c = ctx
            c.translateBy(x: g.cx[i], y: g.y1 - G.liftPt * p.lift)
            c.scaleBy(x: sc, y: sc)
            drawTube(c, g, i, level: p.levels[i], slosh: p.slosh[i])
        }
        drawRain(ctx, g, p)
    }

    private static func drawRain(_ ctx: GraphicsContext, _ g: G, _ p: RainGaugeChartPose) {
        if p.rain < 0.01 { return }
        let i0 = max(0, min(D.tubes - 1, Int(floor(p.cursor))))
        let i1 = min(D.tubes - 1, i0 + 1)
        let xc = g.cx[i0] + (g.cx[i1] - g.cx[i0]) * (p.cursor - Double(i0))
        let spread = g.pitch * 1.25
        var buckets = [Path(), Path(), Path()]
        var splash = Path()
        var ripple = Path()
        for i in 0..<D.drops {
            let fi = Double(i)
            let x = g.ox + 10 + rainGaugeChartHash(fi * 3.1) * (g.w - 20)
            let w = exp(-pow((x - xc) / spread, 2))
            let alpha = min(1, max(0, (p.rain * (0.1 + 0.9 * w) - rainGaugeChartHash(fi * 5.3) * 0.85) * 5))
            if alpha < 0.03 { continue }
            let k = 13 + floor(rainGaugeChartHash(fi * 7.7) * 4)
            let cyc = rainGaugeChartHash(fi * 1.9) + k * p.rainT / D.loop
            let f = cyc - floor(cyc)
            let land = landing(g, x, p)
            let start = g.oy - 40
            let y = start + f * (land.0 - start)
            let len = 12 + 9 * rainGaugeChartHash(fi * 2.7)
            let b = alpha > 0.66 ? 2 : alpha > 0.33 ? 1 : 0
            buckets[b].move(to: CGPoint(x: x + len * 0.12, y: max(start, y - len)))
            buckets[b].addLine(to: CGPoint(x: x, y: y))
            // The drop that landed a moment ago (this lane's previous fall) splashes or ripples.
            let e = f * D.loop / k
            if e < 0.16 {
                let u = e / 0.16
                if land.1 {
                    ripple.addEllipse(in: CGRect(x: x - 2 - 4 * u, y: land.0 - 0.8 - 0.6 * u, width: 4 + 8 * u, height: 1.6 + 1.2 * u))
                } else {
                    let hgt = 7 * 4 * u * (1 - u)
                    let r1 = 0.9 * (1 - 0.5 * u)
                    let r2 = 0.8 * (1 - 0.5 * u)
                    let c1 = CGPoint(x: x - 2 - 6 * u, y: land.0 - hgt)
                    let c2 = CGPoint(x: x + 2 + 5 * u, y: land.0 - hgt * 0.8)
                    splash.addEllipse(in: CGRect(x: c1.x - r1, y: c1.y - r1, width: 2 * r1, height: 2 * r1))
                    splash.addEllipse(in: CGRect(x: c2.x - r2, y: c2.y - r2, width: 2 * r2, height: 2 * r2))
                    splash.addEllipse(in: CGRect(x: x - 1.5 - 3 * u, y: land.0 - 0.8, width: 3 + 6 * u, height: 1.6))
                }
            }
        }
        for b in 0..<3 {
            ctx.stroke(buckets[b], with: .color(rainGaugeChartColor(206, 232, 244, (0.3 + 0.24 * Double(b)) * p.rain)),
                       style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
        }
        ctx.fill(splash, with: .color(rainGaugeChartColor(214, 238, 248, 0.55 * p.rain)))
        ctx.stroke(ripple, with: .color(rainGaugeChartColor(220, 248, 255, 0.6 * p.rain)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
    }
}

/// A small tile of seeded grey noise, tiled over the screen as grain.
enum RainGaugeChartGrain {
    static let image: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n)
        var seed: UInt32 = 0x2545F491
        for i in 0..<(n * n) {
            seed = seed &* 1_664_525 &+ 1_013_904_223
            bytes[i] = UInt8(truncatingIfNeeded: seed >> 24)
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: n,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }()
}
