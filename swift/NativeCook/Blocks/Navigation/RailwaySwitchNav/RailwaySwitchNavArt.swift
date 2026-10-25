import SwiftUI

/// Geometry in screen points: the track diagram panel and the lever frame under it.
struct RailwaySwitchNavGeo {
    let w: Double
    let h: Double
    /// Diagram panel.
    let px: Double
    let py: Double
    let pw: Double
    let ph: Double
    /// Sidings (one per section, above its lever), the trunk line, platform top and curve radius.
    let xs: [Double]
    let trunk: Double
    let top: Double
    let rc: Double
    /// Size of the train, switches and signals (1 on a 150 pt diagram, up to 1.4 on a taller one).
    let k: Double
    /// Platform length beside each siding.
    let plat: Double
    /// Lever frame top, lever pivot and the lever's visible length at rest.
    let frame: Double
    let pivot: Double
    let lever: Double

    static let mapMin = 150.0
    static let frameH = 172.0
    /// A car's length and the gap between cars, before the diagram's scale k.
    static let car = 20.0
    static let gap = 2.0

    init(w: Double, h: Double, mapTop: Double, mapH: Double) {
        self.w = w
        self.h = h
        px = 16
        pw = w - 32
        xs = (0..<RailwaySwitchNavData.count).map { 16 + (w - 32) * (Double($0) + 0.5) / Double(RailwaySwitchNavData.count) }
        py = mapTop
        ph = mapH
        frame = mapTop + mapH + 12
        k = max(1, min(1.4, mapH / Self.mapMin))
        top = mapTop + 26 + 4 * k
        trunk = mapTop + mapH - 32
        rc = 20 * k
        plat = (trunk - rc - top) * 0.6
        pivot = frame + 124
        lever = 92
    }

    func routeLength(_ from: Int, _ to: Int) -> Double {
        let a = trunk - rc - top
        return 2 * a + Double.pi * rc + abs(xs[to] - xs[from]) - 2 * rc
    }

    /// A point on the route from one platform to another: down the first siding, round onto the
    /// trunk, along it, round and up the second siding.
    func routePoint(_ from: Int, _ to: Int, _ s: Double) -> CGPoint {
        let dir = to > from ? 1.0 : -1.0
        let a = trunk - rc - top
        let arc = Double.pi * rc / 2
        let xf = xs[from]
        let xt = xs[to]
        let run = abs(xt - xf) - 2 * rc
        let yc = trunk - rc
        var d = max(0, s)
        if d <= a { return CGPoint(x: xf, y: top + d) }
        d -= a
        if d <= arc {
            let f = d / rc
            return CGPoint(x: xf + dir * rc - dir * rc * cos(f), y: yc + rc * sin(f))
        }
        d -= arc
        if d <= run { return CGPoint(x: xf + dir * (rc + d), y: trunk) }
        d -= run
        if d <= arc {
            let f = d / rc
            return CGPoint(x: xt - dir * rc + dir * rc * sin(f), y: yc + rc * cos(f))
        }
        d -= arc
        return CGPoint(x: xt, y: max(top, yc - d))
    }

    func routePath(_ from: Int, _ to: Int) -> Path {
        var p = Path()
        let l = routeLength(from, to)
        for i in 0...48 {
            let q = routePoint(from, to, l * Double(i) / 48)
            if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        return p
    }
}

private func railwaySwitchNavColor(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
    Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
}

func railwaySwitchNavHex(_ hex: UInt32) -> Color {
    Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
}

private func railwaySwitchNavStops(_ colors: [Color], _ locations: [Double]) -> Gradient {
    Gradient(stops: zip(colors, locations).map { Gradient.Stop(color: $0, location: $1) })
}

private func railwaySwitchNavLine(_ ctx: GraphicsContext, _ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: Double, cap: CGLineCap = .butt) {
    var p = Path()
    p.move(to: a)
    p.addLine(to: b)
    ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: cap))
}

private func railwaySwitchNavCircle(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
}

enum RailwaySwitchNavArt {
    typealias G = RailwaySwitchNavGeo
    typealias D = RailwaySwitchNavData

    // MARK: Static art

    private static func screw(_ ctx: GraphicsContext, _ x: Double, _ y: Double) {
        ctx.fill(railwaySwitchNavCircle(x, y, 3.2), with: .radialGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0xD9C38E), railwaySwitchNavHex(0x8A6B33), railwaySwitchNavHex(0x4A3816)], [0, 0.6, 1]),
            center: CGPoint(x: x - 1, y: y - 1), startRadius: 0, endRadius: 4
        ))
        railwaySwitchNavLine(ctx, CGPoint(x: x - 2, y: y + 1.2), CGPoint(x: x + 2, y: y - 1.2), railwaySwitchNavColor(40, 28, 10, 0.8), 0.9)
    }

    /// The enamel diagram panel with every track drawn dim, and the wooden lever frame.
    static func drawStatic(_ ctx: GraphicsContext, _ g: G) {
        // Diagram panel: two shadows, a painted face with a lit top edge, grain.
        let panelRect = CGRect(x: g.px, y: g.py, width: g.pw, height: g.ph)
        let panel = Path(roundedRect: panelRect, cornerRadius: 18)
        var amb = ctx
        amb.addFilter(.blur(radius: 14))
        amb.fill(Path(roundedRect: CGRect(x: g.px + 2, y: g.py + 12, width: g.pw - 4, height: g.ph), cornerRadius: 18),
                 with: .color(railwaySwitchNavColor(4, 8, 7, 0.45)))
        ctx.fill(panel, with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0x2A3D37), railwaySwitchNavHex(0x22332E), railwaySwitchNavHex(0x1B2925)], [0, 0.5, 1]),
            startPoint: CGPoint(x: 0, y: g.py), endPoint: CGPoint(x: 0, y: g.py + g.ph)
        ))
        if let grain = RailwaySwitchNavGrain.image {
            var gr = ctx
            gr.clip(to: panel)
            gr.blendMode = .overlay
            gr.opacity = 0.07
            gr.fill(panel, with: .tiledImage(Image(decorative: grain, scale: 2)))
        }
        ctx.stroke(Path(roundedRect: panelRect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 17.5),
                   with: .color(railwaySwitchNavColor(214, 236, 222, 0.16)), lineWidth: 1)
        screw(ctx, g.px + 12, g.py + 12)
        screw(ctx, g.px + g.pw - 12, g.py + 12)
        screw(ctx, g.px + 12, g.py + g.ph - 12)
        screw(ctx, g.px + g.pw - 12, g.py + g.ph - 12)

        // Tracks: the trunk, each siding and its two curves (a wye), dim cream enamel.
        var track = Path()
        track.move(to: CGPoint(x: g.px + 24, y: g.trunk))
        track.addLine(to: CGPoint(x: g.px + g.pw - 24, y: g.trunk))
        let yc = g.trunk - g.rc
        let kq = 0.5523 * g.rc
        for x in g.xs {
            track.move(to: CGPoint(x: x, y: g.top))
            track.addLine(to: CGPoint(x: x, y: yc))
            track.addCurve(to: CGPoint(x: x - g.rc, y: yc + g.rc), control1: CGPoint(x: x, y: yc + kq), control2: CGPoint(x: x - g.rc + kq, y: yc + g.rc))
            track.move(to: CGPoint(x: x, y: yc))
            track.addCurve(to: CGPoint(x: x + g.rc, y: yc + g.rc), control1: CGPoint(x: x, y: yc + kq), control2: CGPoint(x: x + g.rc - kq, y: yc + g.rc))
        }
        ctx.stroke(track, with: .color(railwaySwitchNavColor(232, 224, 200, 0.26)),
                   style: StrokeStyle(lineWidth: 3 * g.k, lineCap: .round, lineJoin: .round))
        // Insulated joints between track circuits and buffer stops at the platforms.
        for i in 0..<(D.count - 1) {
            let x = (g.xs[i] + g.xs[i + 1]) / 2
            railwaySwitchNavLine(ctx, CGPoint(x: x, y: g.trunk - 3), CGPoint(x: x, y: g.trunk + 3), railwaySwitchNavColor(20, 30, 27, 0.95), 1.2)
        }
        for x in g.xs {
            railwaySwitchNavLine(ctx, CGPoint(x: x - 6, y: g.top - 4), CGPoint(x: x + 6, y: g.top - 4), railwaySwitchNavColor(232, 224, 200, 0.45), 3, cap: .round)
        }
        // Platforms beside the sidings.
        for x in g.xs {
            ctx.fill(Path(roundedRect: CGRect(x: x - 9 - 8 * g.k, y: g.top + 4, width: 8 * g.k, height: g.plat), cornerRadius: 2),
                     with: .color(railwaySwitchNavColor(232, 224, 200, 0.13)))
            railwaySwitchNavLine(ctx, CGPoint(x: x - 8, y: g.top + 6), CGPoint(x: x - 8, y: g.top + 2 + g.plat), railwaySwitchNavColor(255, 214, 120, 0.35), 1)
        }

        // Lever frame: a mahogany plinth with a brass rail, one dark slot per lever.
        let frameTop = g.frame
        let frameR = Path(roundedRect: CGRect(x: 0, y: frameTop, width: g.w, height: g.h - frameTop + 30), cornerRadius: 26)
        var fs = ctx
        fs.addFilter(.blur(radius: 12))
        fs.fill(Path(roundedRect: CGRect(x: 0, y: frameTop - 6, width: g.w, height: 40), cornerRadius: 26), with: .color(.black.opacity(0.45)))
        ctx.fill(frameR, with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0x5A2E1E), railwaySwitchNavHex(0x47231A), railwaySwitchNavHex(0x2C1610)], [0, 0.35, 1]),
            startPoint: CGPoint(x: 0, y: frameTop), endPoint: CGPoint(x: 0, y: g.h)
        ))
        var inFrame = ctx
        inFrame.clip(to: frameR)
        for i in 0..<14 {
            let fi = Double(i)
            let y = frameTop + 8 + fi * 13
            var b = Path()
            b.move(to: CGPoint(x: 0, y: y))
            b.addCurve(to: CGPoint(x: g.w, y: y + 2 * sin(fi * 0.7)),
                       control1: CGPoint(x: g.w * 0.3, y: y + 4 * sin(fi)),
                       control2: CGPoint(x: g.w * 0.6, y: y - 3 * cos(fi * 1.3)))
            inFrame.stroke(b, with: .color(railwaySwitchNavColor(20, 8, 4, 0.18 + 0.1 * sin(fi * 2.1))), lineWidth: 0.7)
        }
        if let grain = RailwaySwitchNavGrain.streaks {
            var gr = inFrame
            gr.blendMode = .overlay
            gr.opacity = 0.08
            gr.fill(Path(CGRect(x: 0, y: frameTop, width: g.w, height: g.h - frameTop)), with: .tiledImage(Image(decorative: grain, scale: 2)))
        }
        railwaySwitchNavLine(ctx, CGPoint(x: 24, y: frameTop + 0.8), CGPoint(x: g.w - 24, y: frameTop + 0.8), railwaySwitchNavColor(255, 210, 170, 0.28), 1)
        // Brass rail behind the levers.
        ctx.fill(Path(roundedRect: CGRect(x: 16, y: frameTop + 40, width: g.w - 32, height: 8), cornerRadius: 4), with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0xF0D79A), railwaySwitchNavHex(0xB08A42), railwaySwitchNavHex(0x6E5220)], [0, 0.5, 1]),
            startPoint: CGPoint(x: 0, y: frameTop + 40), endPoint: CGPoint(x: 0, y: frameTop + 48)
        ))
        for x in g.xs {
            ctx.fill(Path(roundedRect: CGRect(x: x - 9, y: frameTop + 30, width: 18, height: g.pivot - frameTop - 22), cornerRadius: 9), with: .linearGradient(
                railwaySwitchNavStops([railwaySwitchNavHex(0x0E0605), railwaySwitchNavHex(0x1E0F0B), railwaySwitchNavHex(0x0E0605)], [0, 0.5, 1]),
                startPoint: CGPoint(x: x - 9, y: 0), endPoint: CGPoint(x: x + 9, y: 0)
            ))
            // The brass name plate under the lever (its text is a view).
            ctx.fill(Path(roundedRect: CGRect(x: x - 36, y: g.pivot + 12, width: 72, height: 24), cornerRadius: 6), with: .linearGradient(
                railwaySwitchNavStops([railwaySwitchNavHex(0xEAD08F), railwaySwitchNavHex(0xC29C52), railwaySwitchNavHex(0x8A6A2E)], [0, 0.45, 1]),
                startPoint: CGPoint(x: 0, y: g.pivot + 12), endPoint: CGPoint(x: 0, y: g.pivot + 36)
            ))
            ctx.stroke(Path(roundedRect: CGRect(x: x - 35.5, y: g.pivot + 12.5, width: 71, height: 23), cornerRadius: 5.5),
                       with: .color(railwaySwitchNavColor(70, 48, 14, 0.7)), lineWidth: 1)
            // Lamp bezel above the slot.
            ctx.fill(railwaySwitchNavCircle(x, frameTop + 18, 8.5), with: .radialGradient(
                railwaySwitchNavStops([railwaySwitchNavHex(0xF3DDA2), railwaySwitchNavHex(0x9C7A38), railwaySwitchNavHex(0x4E3A14)], [0, 0.65, 1]),
                center: CGPoint(x: x - 2, y: frameTop + 16), startRadius: 0, endRadius: 10
            ))
        }
    }

    // MARK: Moving art

    /// Lever colours, as in a real frame: signals red, routes blue, points black, the last yellow.
    private static let paint: [UInt32] = [0xB5352B, 0x2D5E9E, 0x2A2D31, 0xC9961F]
    private static let paintDark: [UInt32] = [0x5E1712, 0x132B4D, 0x0E0F11, 0x6B4C09]

    private static func lamp(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, green: Double) {
        let gr = railwaySwitchNavClamp01(green)
        let red = 1 - gr
        let c1 = railwaySwitchNavColor((255 * red + 150 * gr).rounded(), (90 * red + 255 * gr).rounded(), (70 * red + 150 * gr).rounded())
        let c2 = railwaySwitchNavColor((150 * red + 20 * gr).rounded(), (20 * red + 140 * gr).rounded(), (16 * red + 70 * gr).rounded())
        ctx.fill(railwaySwitchNavCircle(x, y, r), with: .radialGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0xFFFBEA), c1, c2], [0, 0.35, 1]),
            center: CGPoint(x: x - r * 0.3, y: y - r * 0.3), startRadius: 0, endRadius: r * 1.3
        ))
        var glow = ctx
        glow.blendMode = .plusLighter
        glow.fill(railwaySwitchNavCircle(x, y, r * 4), with: .radialGradient(
            Gradient(colors: [red > 0.5 ? railwaySwitchNavColor(255, 80, 50, 0.35) : railwaySwitchNavColor(110, 255, 150, 0.35), .clear]),
            center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r * 4
        ))
    }

    private static func drawCar(_ ctx: GraphicsContext, _ g: G, _ from: Int, _ to: Int, _ s: Double, lead: Bool, tail: Bool) {
        let car = G.car
        let a = g.routePoint(from, to, s)
        let b = g.routePoint(from, to, s - (car - 2) * g.k)
        var c = ctx
        c.translateBy(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        c.rotate(by: .radians(atan2(a.y - b.y, a.x - b.x)))
        c.scaleBy(x: g.k, y: g.k)
        // Along +x is forward. Body, roof stripe, windows, lights at the leading/trailing end.
        var sh = c
        sh.addFilter(.blur(radius: 2.2))
        sh.fill(Path(roundedRect: CGRect(x: -car / 2 + 1, y: -4.6 + 1.5, width: car, height: 9.2), cornerRadius: 3.5), with: .color(.black.opacity(0.45)))
        c.fill(Path(roundedRect: CGRect(x: -car / 2, y: -4.6, width: car, height: 9.2), cornerRadius: 3.5), with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0xFFF1C9), railwaySwitchNavHex(0xF2C14E), railwaySwitchNavHex(0xB9861E)], [0, 0.45, 1]),
            startPoint: CGPoint(x: 0, y: -4.6), endPoint: CGPoint(x: 0, y: 4.6)
        ))
        c.fill(Path(CGRect(x: -car / 2 + 3, y: -1.2, width: car - 6, height: 2.4)), with: .color(railwaySwitchNavHex(0x2B3A36)))
        var win = Path()
        for i in 0..<4 { win.addRect(CGRect(x: -car / 2 + 4 + Double(i) * 3.4, y: -0.7, width: 2.2, height: 1.4)) }
        c.fill(win, with: .color(railwaySwitchNavColor(210, 240, 236, 0.9)))
        if lead {
            var head = c
            head.blendMode = .plusLighter
            head.fill(railwaySwitchNavCircle(car / 2 + 2, 0, 12), with: .radialGradient(
                Gradient(colors: [railwaySwitchNavColor(255, 244, 200, 0.55), .clear]),
                center: CGPoint(x: car / 2 + 2, y: 0), startRadius: 0, endRadius: 12
            ))
            var lamps = railwaySwitchNavCircle(car / 2 - 0.8, -2.6, 0.9)
            lamps.addPath(railwaySwitchNavCircle(car / 2 - 0.8, 2.6, 0.9))
            c.fill(lamps, with: .color(railwaySwitchNavHex(0xFFFDF2)))
        }
        if tail {
            var lamps = railwaySwitchNavCircle(-car / 2 + 0.8, -2.6, 0.9)
            lamps.addPath(railwaySwitchNavCircle(-car / 2 + 0.8, 2.6, 0.9))
            c.fill(lamps, with: .color(railwaySwitchNavHex(0xFF4A36)))
        }
    }

    private static func drawLever(_ ctx: GraphicsContext, _ g: G, _ k: Int, pull: Double) {
        let x = g.xs[k]
        let p = max(-0.1, min(1.1, pull))
        // Pulled toward the viewer: the visible length shortens and the handle comes closer (larger).
        let len = g.lever * (1 - 0.42 * p)
        let topY = g.pivot - len
        let sc = 1 + 0.14 * p
        var sh = ctx
        sh.addFilter(.blur(radius: 3 + 4 * p))
        sh.fill(Path(roundedRect: CGRect(x: x - 4 + 2 + 3 * p, y: topY + 6 + 4 * p, width: 8, height: len), cornerRadius: 4),
                with: .color(.black.opacity(0.35 + 0.15 * p)))
        // Painted rod, tapering toward the top, lit more as it turns to the light.
        var rod = Path()
        rod.move(to: CGPoint(x: x - 5, y: g.pivot))
        rod.addLine(to: CGPoint(x: x - 3.4, y: topY + 22))
        rod.addLine(to: CGPoint(x: x + 3.4, y: topY + 22))
        rod.addLine(to: CGPoint(x: x + 5, y: g.pivot))
        rod.closeSubpath()
        ctx.fill(rod, with: .linearGradient(
            railwaySwitchNavStops([
                railwaySwitchNavHex(paintDark[k]), railwaySwitchNavHex(paint[k]), railwaySwitchNavColor(255, 245, 230, 0.35 + 0.25 * p),
                railwaySwitchNavHex(paint[k]), railwaySwitchNavHex(paintDark[k]),
            ], [0, 0.3, 0.42, 0.6, 1]),
            startPoint: CGPoint(x: x - 5, y: 0), endPoint: CGPoint(x: x + 5, y: 0)
        ))
        // Polished steel handle and the catch handle behind it.
        var c = ctx
        c.translateBy(x: x, y: topY + 22)
        c.scaleBy(x: sc, y: sc)
        let steel = GraphicsContext.Shading.linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0x6F757C), railwaySwitchNavHex(0xE8ECEF), railwaySwitchNavHex(0x5A6066)], [0, 0.45, 1]),
            startPoint: CGPoint(x: 4, y: 0), endPoint: CGPoint(x: 8, y: 0)
        )
        c.fill(Path(roundedRect: CGRect(x: 4.2, y: -22, width: 3.4, height: 18), cornerRadius: 1.7), with: steel)
        c.fill(Path(CGRect(x: 2, y: -6, width: 4, height: 2.4)), with: steel)
        c.fill(Path(roundedRect: CGRect(x: -4, y: -26, width: 8, height: 28), cornerRadius: 4), with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0x4F555B), railwaySwitchNavHex(0xC9CFD4), .white, railwaySwitchNavHex(0x9BA2A8), railwaySwitchNavHex(0x3E4348)], [0, 0.3, 0.42, 0.7, 1]),
            startPoint: CGPoint(x: -4, y: 0), endPoint: CGPoint(x: 4, y: 0)
        ))
        c.fill(Path(roundedRect: CGRect(x: -5, y: 0, width: 10, height: 4), cornerRadius: 1.5), with: .linearGradient(
            railwaySwitchNavStops([railwaySwitchNavHex(0x6E5220), railwaySwitchNavHex(0xF0D79A), railwaySwitchNavHex(0x6E5220)], [0, 0.45, 1]),
            startPoint: CGPoint(x: -5, y: 0), endPoint: CGPoint(x: 5, y: 0)
        ))
    }

    /// Everything that moves: route light, switch blades, signals, the train, lamps and levers.
    static func drawMoving(_ ctx: GraphicsContext, _ g: G, _ p: RailwaySwitchNavPose) {
        if p.route > 0.002 && p.from != p.to {
            let path = g.routePath(p.from, p.to)
            var glow = ctx
            glow.addFilter(.blur(radius: 4))
            glow.stroke(path, with: .color(railwaySwitchNavColor(255, 214, 120, 0.32 * p.route)),
                        style: StrokeStyle(lineWidth: 9 * g.k, lineCap: .round, lineJoin: .round))
            ctx.stroke(path, with: .color(railwaySwitchNavColor(255, 236, 184, 0.95 * p.route)),
                       style: StrokeStyle(lineWidth: 3 * g.k, lineCap: .round, lineJoin: .round))
        }
        // Switch blades at the foot of each siding, and the point motor's lamp.
        let yc = g.trunk - g.rc
        for k in 0..<D.count {
            let b = p.blades[k]
            let ang = b * 34 * Double.pi / 180
            let x0 = g.xs[k]
            let y0 = yc - 10 * g.k
            let set = abs(b)
            railwaySwitchNavLine(
                ctx, CGPoint(x: x0, y: y0), CGPoint(x: x0 + sin(ang) * 17 * g.k, y: y0 + cos(ang) * 17 * g.k),
                railwaySwitchNavColor((150 + 105 * set).rounded(), (150 + 61 * set).rounded(), (140 - 40 * set).rounded(), 0.7 + 0.3 * set),
                2.6 * g.k, cap: .round
            )
            ctx.fill(railwaySwitchNavCircle(x0, y0, 2.2 * g.k), with: .color(railwaySwitchNavHex(0xE8E0C8)))
            lamp(ctx, x0 - 14 * g.k, yc - 4, 2.2 * g.k, green: set > 0.5 ? 1 : 0)
        }
        // Platform signals.
        for k in 0..<D.count {
            ctx.fill(Path(roundedRect: CGRect(x: g.xs[k] + 6 + 3 * g.k, y: g.top - 2, width: 9 * g.k, height: 14 * g.k), cornerRadius: 4.5 * g.k),
                     with: .color(railwaySwitchNavHex(0x0F1715)))
            lamp(ctx, g.xs[k] + 6 + 7.5 * g.k, g.top - 2 + 7 * g.k, 3 * g.k, green: p.signals[k])
        }
        // The train: two cars, headlights ahead, tail lights behind.
        let car = G.car * g.k
        let gap = G.gap * g.k
        let span = 2 * car + gap
        if p.from == p.to {
            // At rest on a platform: reuse a route that ends there.
            let other = p.to == 0 ? 1 : 0
            let lr = g.routeLength(other, p.to)
            drawCar(ctx, g, other, p.to, lr, lead: true, tail: false)
            drawCar(ctx, g, other, p.to, lr - car - gap, lead: false, tail: true)
        } else {
            let l = g.routeLength(p.from, p.to)
            let lead = span + (l - span) * p.run
            drawCar(ctx, g, p.from, p.to, lead, lead: true, tail: false)
            drawCar(ctx, g, p.from, p.to, lead - car - gap, lead: false, tail: true)
        }
        // Lever-frame lamps (repeaters) and the levers.
        for k in 0..<D.count {
            lamp(ctx, g.xs[k], g.frame + 18, 5, green: p.signals[k])
            drawLever(ctx, g, k, pull: p.levers[k])
        }
    }
}

/// Seeded grey noise tiles: fine grain for the enamel, horizontal streaks for the mahogany.
enum RailwaySwitchNavGrain {
    private static func make(_ w: Int, _ h: Int, seed start: UInt32, streak: Bool) -> CGImage? {
        var bytes = [UInt8](repeating: 0, count: w * h)
        var seed = start
        var row = [UInt8](repeating: 0, count: w)
        for y in 0..<h {
            for x in 0..<w {
                seed = seed &* 1_664_525 &+ 1_013_904_223
                let v = UInt8(truncatingIfNeeded: seed >> 24)
                if streak {
                    // Long along x, short across: each pixel leans on its left neighbour.
                    row[x] = x == 0 ? v : UInt8((Int(row[x - 1]) * 7 + Int(v)) / 8)
                    bytes[y * w + x] = row[x]
                } else {
                    bytes[y * w + x] = v
                }
            }
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }

    static let image: CGImage? = make(128, 128, seed: 0x1234567, streak: false)
    static let streaks: CGImage? = make(256, 64, seed: 0x7654321, streak: true)
}
