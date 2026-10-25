import SwiftUI

/// Screen geometry.
struct HotAirBalloonWelcomeGeo: Equatable {
    let w: Double
    let h: Double
    /// The meadow's ridge where the basket rests, the screen rise per step and the world scroll.
    let ground: Double
    let rise: Double
    let scroll: Double
    /// Envelope height, widest radius, mouth radius.
    let e: Double
    let r: Double
    let rm: Double

    /// Mouth to basket bottom (pt).
    static let below = 66.0
    static let hillTops: [Double] = [-150, -112, -80, -36]
    static let hillPar: [Double] = [0.18, 0.4, 0.7, 1.0]

    init(w: Double, h: Double, ground: Double, ceiling: Double) {
        self.w = w
        self.h = h
        self.ground = ground
        e = min(190, h * 0.22)
        let top = ground - Self.below - e
        rise = max(30, (top - ceiling) / 2)
        scroll = 120
        r = e * 0.44
        rm = e * 0.085
    }

    var horizon: Double { ground + Self.hillTops[0] + 60 }
    func hillY(_ i: Int) -> Double { ground + Self.hillTops[i] - 60 }
    func hillHeight(_ i: Int) -> Double { h - hillY(i) + scroll * 2 * Self.hillPar[i] + 40 }
}

struct HotAirBalloonWelcomeCloud {
    let x: Double
    let y: Double
    let par: Double
    let scale: Double
    let alpha: Double
    let front: Bool
    let amp: Double
    let phase: Double
    let sprite: Int

    static let all = [
        HotAirBalloonWelcomeCloud(x: 0.12, y: 150, par: 0.25, scale: 0.7, alpha: 0.5, front: false, amp: 10, phase: 0.3, sprite: 1),
        HotAirBalloonWelcomeCloud(x: 0.78, y: 215, par: 0.32, scale: 0.85, alpha: 0.6, front: false, amp: 12, phase: 2.1, sprite: 0),
        HotAirBalloonWelcomeCloud(x: 0.02, y: 40, par: 0.9, scale: 1.15, alpha: 0.85, front: false, amp: 14, phase: 4.0, sprite: 2),
        HotAirBalloonWelcomeCloud(x: 0.7, y: -60, par: 1.1, scale: 1.35, alpha: 0.9, front: false, amp: 16, phase: 1.2, sprite: 0),
        HotAirBalloonWelcomeCloud(x: 0.3, y: -150, par: 1.25, scale: 1.5, alpha: 0.72, front: true, amp: 18, phase: 5.1, sprite: 1),
        HotAirBalloonWelcomeCloud(x: 0.92, y: -230, par: 1.3, scale: 1.05, alpha: 0.8, front: true, amp: 12, phase: 3.3, sprite: 2),
    ]
}

func hotAirBalloonWelcomeHex(_ hex: UInt32) -> Color {
    Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
}

func hotAirBalloonWelcomeRGBA(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
    Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
}

private func hotAirBalloonWelcomeStops(_ colors: [Color], _ locations: [Double]) -> Gradient {
    Gradient(stops: zip(colors, locations).map { Gradient.Stop(color: $0, location: $1) })
}

private func hotAirBalloonWelcomeCircle(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
}

private func hotAirBalloonWelcomeLine(_ ctx: GraphicsContext, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ color: Color, _ width: Double) {
    var p = Path()
    p.move(to: CGPoint(x: x0, y: y0))
    p.addLine(to: CGPoint(x: x1, y: y1))
    ctx.stroke(p, with: .color(color), lineWidth: width)
}

// MARK: Static scenery (each layer an Equatable view, so it is drawn once and only moved)

/// The dawn sky: gradient, the low sun with its bloom, the last stars, grain.
struct HotAirBalloonWelcomeSky: View, Equatable {
    let geo: HotAirBalloonWelcomeGeo

    var body: some View {
        Canvas(rendersAsynchronously: true) { c, _ in
            let w = geo.w
            let h = geo.h
            let horizon = geo.horizon
            let full = Path(CGRect(x: 0, y: 0, width: w, height: h))
            c.fill(full, with: .linearGradient(
                hotAirBalloonWelcomeStops(
                    [0x262B5A, 0x4B3F7E, 0x9A5F93, 0xE58A98, 0xF8C49A].map(hotAirBalloonWelcomeHex),
                    [0, 0.32, 0.6, 0.84, 1]
                ),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: horizon + 40)
            ))
            let sx = w * 0.72
            let sy = horizon - 6
            c.fill(full, with: .radialGradient(
                hotAirBalloonWelcomeStops([
                    hotAirBalloonWelcomeRGBA(255, 214, 160, 0.55), hotAirBalloonWelcomeRGBA(255, 170, 140, 0.18), hotAirBalloonWelcomeRGBA(255, 150, 140, 0),
                ], [0, 0.35, 1]),
                center: CGPoint(x: sx, y: sy), startRadius: 0, endRadius: w * 0.9
            ))
            c.fill(hotAirBalloonWelcomeCircle(sx, sy, 26), with: .radialGradient(
                hotAirBalloonWelcomeStops([hotAirBalloonWelcomeHex(0xFFF6DE), hotAirBalloonWelcomeHex(0xFFE2A6), hotAirBalloonWelcomeRGBA(255, 200, 140, 0)], [0, 0.75, 1]),
                center: CGPoint(x: sx, y: sy), startRadius: 0, endRadius: 26
            ))
            for i in 0..<40 {
                let fi = Double(i)
                let y = hotAirBalloonWelcomeHash(fi * 3.7) * horizon * 0.42
                let a = 0.15 + 0.45 * hotAirBalloonWelcomeHash(fi * 1.3) * (1 - y / (horizon * 0.42))
                c.fill(hotAirBalloonWelcomeCircle(hotAirBalloonWelcomeHash(fi * 9.1) * w, y, 0.5 + hotAirBalloonWelcomeHash(fi * 5.3) * 0.8),
                       with: .color(hotAirBalloonWelcomeRGBA(255, 246, 230, a)))
            }
            if let grain = HotAirBalloonWelcomeGrain.image {
                var g = c
                g.blendMode = .overlay
                g.opacity = 0.06
                g.fill(full, with: .tiledImage(Image(decorative: grain, scale: 2)))
            }
        }
        .frame(width: geo.w, height: geo.h)
    }
}

/// One layer of hills: a ridge filled with a three-stop gradient, a rim of dawn light on top,
/// trees on the nearer ones. Drawn with its ridge at y = 60 of the layer.
struct HotAirBalloonWelcomeHill: View, Equatable {
    let geo: HotAirBalloonWelcomeGeo
    let i: Int

    private static let tops: [[UInt32]] = [[0xC291A4, 0xA57D9B, 0x8A6D90], [0x93708F, 0x735C82, 0x5A4C72], [0x4D5672, 0x3D4A62, 0x2C3850], [0x33463F, 0x283A34, 0x1C2A26]]
    private static let amps: [Double] = [46, 34, 26, 14]

    private func ridge(w: Double, top: Double, amp: Double, seed: Double, bottom: Double, n: Int) -> Path {
        var b = Path()
        b.move(to: CGPoint(x: -12, y: bottom))
        var px = -12.0
        var py = top + amp * hotAirBalloonWelcomeHash(seed)
        b.addLine(to: CGPoint(x: px, y: py))
        for k in 1...n {
            let fk = Double(k)
            let x = -12 + (w + 24) * fk / Double(n)
            let y = top + amp * (0.5 + 0.5 * sin(fk * 1.7 + seed) * hotAirBalloonWelcomeHash(seed + fk)) + amp * 0.3 * hotAirBalloonWelcomeHash(seed + fk * 2.3)
            b.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: (px + x) / 2, y: (py + y) / 2 - amp * 0.25))
            px = x
            py = y
        }
        b.addLine(to: CGPoint(x: w + 12, y: bottom))
        b.closeSubpath()
        return b
    }

    var body: some View {
        let height = geo.hillHeight(i)
        return Canvas(rendersAsynchronously: true) { c, _ in
            let w = geo.w
            let amp = Self.amps[i]
            let path = ridge(w: w, top: 60, amp: amp, seed: 3 + Double(i) * 11, bottom: height, n: 6 + i * 2)
            c.fill(path, with: .linearGradient(
                hotAirBalloonWelcomeStops(Self.tops[i].map(hotAirBalloonWelcomeHex), [0, 0.4, 1]),
                startPoint: CGPoint(x: 0, y: 40), endPoint: CGPoint(x: 0, y: 280)
            ))
            var inner = c
            inner.clip(to: path)
            var rim = inner
            rim.addFilter(.blur(radius: 2))
            rim.stroke(path, with: .linearGradient(
                hotAirBalloonWelcomeStops([
                    hotAirBalloonWelcomeRGBA(255, 200, 170, 0.05), hotAirBalloonWelcomeRGBA(255, 200, 160, 0.22), hotAirBalloonWelcomeRGBA(255, 214, 160, 0.45),
                ], [0, 0.6, 1]),
                startPoint: .zero, endPoint: CGPoint(x: w, y: 0)
            ), lineWidth: 3 + Double(i))
            if i >= 2 {
                // Tree clusters and hedgerows.
                var trees = Path()
                let fi = Double(i)
                for k in 0..<(i == 2 ? 26 : 18) {
                    let fk = Double(k)
                    let x = hotAirBalloonWelcomeHash(fk * 7.1 + fi) * w
                    let y = 60 + amp * 0.9 + hotAirBalloonWelcomeHash(fk * 3.3 + fi) * 40
                    let r = (i == 2 ? 4 : 6) + hotAirBalloonWelcomeHash(fk * 1.7) * 5
                    trees.addPath(hotAirBalloonWelcomeCircle(x, y, r))
                    trees.addPath(hotAirBalloonWelcomeCircle(x + r * 0.8, y + 1, r * 0.8))
                }
                inner.fill(trees, with: .color(i == 2 ? hotAirBalloonWelcomeRGBA(30, 40, 58, 0.75) : hotAirBalloonWelcomeRGBA(16, 28, 24, 0.85)))
                for k in 0..<6 {
                    let fk = Double(k)
                    var b = Path()
                    let y = 60 + amp + 30 + fk * 18
                    b.move(to: CGPoint(x: 0, y: y))
                    b.addQuadCurve(to: CGPoint(x: w, y: y - 6), control: CGPoint(x: w / 2, y: y + 10 * sin(fk)))
                    inner.stroke(b, with: .color(hotAirBalloonWelcomeRGBA(255, 220, 190, 0.06)), lineWidth: 1)
                }
            }
        }
        .frame(width: geo.w, height: height)
    }
}

/// One of three cloud sprites (160 × 70 pt), shown at its cloud's scale.
struct HotAirBalloonWelcomeCloudSprite: View, Equatable {
    let sprite: Int
    let scale: Double

    private static let puffs: [[(Double, Double, Double)]] = [
        [(40, 44, 22), (66, 34, 28), (96, 40, 24), (120, 48, 18), (24, 52, 14)],
        [(30, 46, 18), (56, 38, 24), (86, 34, 26), (114, 44, 20), (138, 52, 12)],
        [(36, 48, 16), (60, 40, 22), (84, 44, 18), (104, 50, 12)],
    ]

    static func == (a: Self, b: Self) -> Bool { a.sprite == b.sprite && a.scale == b.scale }

    var body: some View {
        Canvas(rendersAsynchronously: true) { c, _ in
            var c = c
            c.scaleBy(x: scale, y: scale)
            var shape = Path()
            for p in Self.puffs[sprite] { shape.addPath(hotAirBalloonWelcomeCircle(p.0, p.1, p.2)) }
            shape.addRoundedRect(in: CGRect(x: 18, y: 44, width: 128, height: 16), cornerSize: CGSize(width: 8, height: 8))
            var body = c
            body.addFilter(.blur(radius: 3.5))
            body.fill(shape, with: .linearGradient(
                hotAirBalloonWelcomeStops([0xFFF1E4, 0xF7C9C2, 0xB898B8].map(hotAirBalloonWelcomeHex), [0, 0.5, 1]),
                startPoint: CGPoint(x: 0, y: 10), endPoint: CGPoint(x: 0, y: 62)
            ))
            var lit = c
            lit.clip(to: shape)
            lit.blendMode = .screen
            lit.fill(Path(CGRect(x: 0, y: 0, width: 160, height: 70)), with: .radialGradient(
                Gradient(colors: [hotAirBalloonWelcomeRGBA(255, 226, 180, 0.6), hotAirBalloonWelcomeRGBA(255, 226, 180, 0)]),
                center: CGPoint(x: 120, y: 24), startRadius: 0, endRadius: 70
            ))
        }
        .frame(width: 160 * scale, height: 70 * scale)
    }
}

// MARK: The balloon, drawn every frame

enum HotAirBalloonWelcomeArt {
    typealias G = HotAirBalloonWelcomeGeo
    typealias D = HotAirBalloonWelcomeData

    private static let goreRGB: [(Double, Double, Double)] = [(243, 230, 207), (229, 102, 79), (47, 127, 134), (232, 176, 74)]

    /// Envelope radius at v (0 = crown, 1 = mouth).
    static func radiusAt(_ g: G, _ v: Double) -> Double {
        if v <= 0.55 {
            let u = (0.55 - v) / 0.55
            return g.r * sqrt(max(0, 1 - u * u))
        }
        let u = (v - 0.55) / 0.45
        return g.r - (g.r - g.rm) * pow(u, 1.25)
    }

    private static func envelopePath(_ g: G) -> Path {
        var b = Path()
        let n = 28
        b.move(to: CGPoint(x: 0, y: -g.e))
        for i in 1...n {
            let v = Double(i) / Double(n)
            b.addLine(to: CGPoint(x: radiusAt(g, v), y: -g.e * (1 - v)))
        }
        for i in stride(from: n, through: 0, by: -1) {
            let v = Double(i) / Double(n)
            b.addLine(to: CGPoint(x: -radiusAt(g, v), y: -g.e * (1 - v)))
        }
        b.closeSubpath()
        return b
    }

    private static func gorePath(_ g: G, _ a: Double, _ b2: Double) -> Path {
        var b = Path()
        let n = 20
        for i in 0...n {
            let v = Double(i) / Double(n)
            let x = radiusAt(g, v) * sin(a)
            if i == 0 { b.move(to: CGPoint(x: x, y: -g.e)) } else { b.addLine(to: CGPoint(x: x, y: -g.e * (1 - v))) }
        }
        for i in stride(from: n, through: 0, by: -1) {
            let v = Double(i) / Double(n)
            b.addLine(to: CGPoint(x: radiusAt(g, v) * sin(b2), y: -g.e * (1 - v)))
        }
        b.closeSubpath()
        return b
    }

    private static func rgb(_ c: (Double, Double, Double), _ k: Double) -> Color {
        hotAirBalloonWelcomeRGBA(min(255, c.0 * k).rounded(), min(255, c.1 * k).rounded(), min(255, c.2 * k).rounded())
    }

    private static func drawEnvelope(_ ctx: GraphicsContext, _ g: G, turn: Double, heat: Double) {
        let step = 2 * Double.pi / Double(D.gores)
        let light = 0.75
        let outline = envelopePath(g)
        var seams: [Double] = []
        for k in 0..<D.gores {
            var a = Double(k) * step + turn
            a -= 2 * Double.pi * floor((a + Double.pi) / (2 * Double.pi))
            var b = a + step
            let lo = max(a, -Double.pi / 2)
            let hi = min(b, Double.pi / 2)
            if hi <= lo { continue }
            a = lo
            b = hi
            let mid = (a + b) / 2
            let shade = 0.5 + 0.62 * max(0, cos(mid - light)) + 0.08 * cos(mid)
            let col = goreRGB[k % D.pattern]
            ctx.fill(gorePath(g, a, b), with: .linearGradient(
                hotAirBalloonWelcomeStops([rgb(col, shade * 1.06), rgb(col, shade), rgb(col, shade * 0.62)], [0, 0.5, 1]),
                startPoint: CGPoint(x: 0, y: -g.e), endPoint: .zero
            ))
            if a > -Double.pi / 2 + 0.01 { seams.append(a) }
        }
        var seamPath = Path()
        for s in seams {
            for i in 0...20 {
                let v = Double(i) / 20
                let x = radiusAt(g, v) * sin(s)
                if i == 0 { seamPath.move(to: CGPoint(x: x, y: -g.e)) } else { seamPath.addLine(to: CGPoint(x: x, y: -g.e * (1 - v))) }
            }
        }
        ctx.stroke(seamPath, with: .color(hotAirBalloonWelcomeRGBA(50, 24, 20, 0.28)), lineWidth: 0.7)

        var inside = ctx
        inside.clip(to: outline)
        let box = Path(CGRect(x: -g.r - 2, y: -g.e - 2, width: g.r * 2 + 4, height: g.e + 4))
        // Cool shadow on the side away from the sun, warm highlight toward it.
        inside.fill(box, with: .linearGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(40, 30, 80, 0.5), hotAirBalloonWelcomeRGBA(40, 30, 80, 0.12), hotAirBalloonWelcomeRGBA(40, 30, 80, 0)], [0, 0.5, 1]),
            startPoint: CGPoint(x: -g.r, y: 0), endPoint: CGPoint(x: g.r * 0.4, y: 0)
        ))
        var hl = inside
        hl.blendMode = .screen
        hl.fill(box, with: .radialGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(255, 236, 214, 0.42), hotAirBalloonWelcomeRGBA(255, 220, 190, 0.1), hotAirBalloonWelcomeRGBA(255, 220, 190, 0)], [0, 0.5, 1]),
            center: CGPoint(x: g.r * 0.36, y: -g.e * 0.7), startRadius: 0, endRadius: g.r * 0.8
        ))
        // Load tapes round the envelope and the dark skirt at the mouth.
        var tapes = Path()
        for v in [0.22, 0.38, 0.55, 0.7, 0.84] {
            let r = radiusAt(g, v)
            let y = -g.e * (1 - v)
            for j in 0...16 {
                let x = -r + 2 * r * Double(j) / 16
                let yy = y + 0.07 * r * sqrt(max(0, 1 - (x / r) * (x / r)))
                if j == 0 { tapes.move(to: CGPoint(x: x, y: yy)) } else { tapes.addLine(to: CGPoint(x: x, y: yy)) }
            }
        }
        inside.stroke(tapes, with: .color(hotAirBalloonWelcomeRGBA(60, 30, 24, 0.22)), lineWidth: 0.8)
        inside.fill(Path(CGRect(x: -g.r, y: -g.e * 0.09, width: g.r * 2, height: g.e * 0.1)), with: .color(hotAirBalloonWelcomeRGBA(48, 28, 30, 0.6)))
        // The burner lights the fabric from inside.
        if heat > 0.01 {
            var glow = inside
            glow.blendMode = .plusLighter
            glow.fill(box, with: .radialGradient(
                hotAirBalloonWelcomeStops([
                    hotAirBalloonWelcomeRGBA(255, 160, 70, 0.55 * heat), hotAirBalloonWelcomeRGBA(255, 120, 50, 0.18 * heat), hotAirBalloonWelcomeRGBA(255, 110, 40, 0),
                ], [0, 0.45, 1]),
                center: CGPoint(x: 0, y: -g.e * 0.12), startRadius: 0, endRadius: g.e * 0.9
            ))
        }
        // Rim light on the sunward edge and the mouth.
        ctx.stroke(outline, with: .linearGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(255, 200, 160, 0), hotAirBalloonWelcomeRGBA(255, 200, 160, 0.1), hotAirBalloonWelcomeRGBA(255, 214, 170, 0.75)], [0, 0.6, 1]),
            startPoint: CGPoint(x: -g.r, y: 0), endPoint: CGPoint(x: g.r, y: 0)
        ), lineWidth: 1.2)
        let mouth = heat > 0.01
            ? hotAirBalloonWelcomeRGBA((60 + 180 * heat).rounded(), (30 + 90 * heat).rounded(), (30 + 20 * heat).rounded())
            : hotAirBalloonWelcomeHex(0x2A1A1C)
        ctx.fill(Path(ellipseIn: CGRect(x: -g.rm, y: -3, width: g.rm * 2, height: 6)), with: .color(mouth))
    }

    private static func drawFlame(_ ctx: GraphicsContext, heat: Double, time: Double) {
        if heat < 0.01 { return }
        let base = 30.0
        let f = HotAirBalloonWelcomePose.flicker(time)
        let hf = (8 + 42 * heat) * f
        let wf = 4 + 4 * heat
        var add = ctx
        add.blendMode = .plusLighter
        let br = 26 + 46 * heat
        add.fill(hotAirBalloonWelcomeCircle(0, base - hf * 0.5, br), with: .radialGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(255, 180, 90, 0.55 * heat), hotAirBalloonWelcomeRGBA(255, 130, 60, 0.18 * heat), hotAirBalloonWelcomeRGBA(255, 120, 50, 0)], [0, 0.4, 1]),
            center: CGPoint(x: 0, y: base - hf * 0.5), startRadius: 0, endRadius: br
        ))
        var outer = Path()
        outer.move(to: CGPoint(x: -wf, y: base))
        outer.addCurve(to: CGPoint(x: 0, y: base - hf), control1: CGPoint(x: -wf * 1.3, y: base - hf * 0.45), control2: CGPoint(x: -wf * 0.2, y: base - hf * 0.75))
        outer.addCurve(to: CGPoint(x: wf, y: base), control1: CGPoint(x: wf * 0.2, y: base - hf * 0.75), control2: CGPoint(x: wf * 1.3, y: base - hf * 0.45))
        outer.closeSubpath()
        add.fill(outer, with: .linearGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(255, 120, 40, 0.95), hotAirBalloonWelcomeRGBA(255, 190, 80, 0.85), hotAirBalloonWelcomeRGBA(255, 220, 140, 0)], [0, 0.45, 1]),
            startPoint: CGPoint(x: 0, y: base), endPoint: CGPoint(x: 0, y: base - hf)
        ))
        let hc = hf * 0.55
        var core = Path()
        core.move(to: CGPoint(x: -wf * 0.45, y: base))
        core.addCurve(to: CGPoint(x: 0, y: base - hc), control1: CGPoint(x: -wf * 0.6, y: base - hc * 0.5), control2: CGPoint(x: -wf * 0.1, y: base - hc * 0.8))
        core.addCurve(to: CGPoint(x: wf * 0.45, y: base), control1: CGPoint(x: wf * 0.1, y: base - hc * 0.8), control2: CGPoint(x: wf * 0.6, y: base - hc * 0.5))
        core.closeSubpath()
        ctx.fill(core, with: .linearGradient(
            hotAirBalloonWelcomeStops([hotAirBalloonWelcomeRGBA(150, 190, 255, 0.95), hotAirBalloonWelcomeRGBA(255, 250, 230, 0.95), hotAirBalloonWelcomeRGBA(255, 240, 200, 0)], [0, 0.3, 1]),
            startPoint: CGPoint(x: 0, y: base), endPoint: CGPoint(x: 0, y: base - hc)
        ))
    }

    private static func drawRig(_ ctx: GraphicsContext, _ g: G) {
        let cable = hotAirBalloonWelcomeRGBA(40, 26, 30, 0.85)
        hotAirBalloonWelcomeLine(ctx, -g.rm, 0, -14, 43, cable, 0.9)
        hotAirBalloonWelcomeLine(ctx, g.rm, 0, 14, 43, cable, 0.9)
        hotAirBalloonWelcomeLine(ctx, -g.rm * 0.4, 1, -5, 43, cable, 0.9)
        hotAirBalloonWelcomeLine(ctx, g.rm * 0.4, 1, 5, 43, cable, 0.9)
        // Burner frame.
        let steel = GraphicsContext.Shading.linearGradient(
            hotAirBalloonWelcomeStops([0x4A4E55, 0xC8CDD2, 0x3A3D42].map(hotAirBalloonWelcomeHex), [0, 0.45, 1]),
            startPoint: CGPoint(x: -9, y: 0), endPoint: CGPoint(x: 9, y: 0)
        )
        ctx.fill(Path(roundedRect: CGRect(x: -9, y: 30, width: 18, height: 6), cornerRadius: 2), with: steel)
        ctx.fill(Path(CGRect(x: -1.2, y: 36, width: 2.4, height: 7)), with: steel)
        // Two passengers and the wicker basket with a leather rim.
        var heads = hotAirBalloonWelcomeCircle(-6, 41, 3.2)
        heads.addPath(hotAirBalloonWelcomeCircle(5.5, 40.5, 3))
        ctx.fill(heads, with: .color(hotAirBalloonWelcomeHex(0x3A2A2E)))
        let basket = Path(roundedRect: CGRect(x: -15, y: 44, width: 30, height: 22), cornerRadius: 4)
        ctx.fill(basket, with: .linearGradient(
            hotAirBalloonWelcomeStops([0x6B4420, 0xA8763E, 0xC08A4C, 0x5E3B1C].map(hotAirBalloonWelcomeHex), [0, 0.45, 0.7, 1]),
            startPoint: CGPoint(x: -15, y: 0), endPoint: CGPoint(x: 15, y: 0)
        ))
        var weave = Path()
        var y = 47.0
        while y < 66 {
            weave.move(to: CGPoint(x: -15, y: y))
            weave.addLine(to: CGPoint(x: 15, y: y))
            y += 3
        }
        var x = -13.0
        while x < 15 {
            var yy = 44.0
            while yy < 66 {
                let off = (yy / 6).truncatingRemainder(dividingBy: 2) * 2
                weave.move(to: CGPoint(x: x + off, y: yy))
                weave.addLine(to: CGPoint(x: x + off, y: yy + 3))
                yy += 6
            }
            x += 4
        }
        var inBasket = ctx
        inBasket.clip(to: basket)
        inBasket.stroke(weave, with: .color(hotAirBalloonWelcomeRGBA(50, 28, 10, 0.4)), lineWidth: 0.6)
        ctx.fill(Path(roundedRect: CGRect(x: -16, y: 42, width: 32, height: 4), cornerRadius: 2), with: .color(hotAirBalloonWelcomeHex(0x4B2C18)))
        hotAirBalloonWelcomeLine(ctx, -14, 42.6, 14, 42.6, hotAirBalloonWelcomeRGBA(255, 210, 170, 0.35), 0.8)
    }

    /// Where the balloon's mouth is on screen.
    static func mouth(_ g: G, _ p: HotAirBalloonWelcomePose) -> CGPoint {
        let sw = HotAirBalloonWelcomePose.sway(p.time, p.alt)
        return CGPoint(x: g.w * 0.5, y: g.ground - G.below - p.alt * g.rise + sw.1)
    }

    /// The ground shadow and the balloon (envelope, rig and flame).
    static func drawBalloon(_ ctx: GraphicsContext, _ g: G, _ p: HotAirBalloonWelcomePose) {
        let sw = HotAirBalloonWelcomePose.sway(p.time, p.alt)
        let m = mouth(g, p)
        let ground = g.ground + p.alt * g.scroll
        if ground < g.h + 20 {
            let k = hotAirBalloonWelcomeClamp01(p.alt)
            var sh = ctx
            sh.addFilter(.blur(radius: 3 + 10 * k))
            sh.fill(Path(ellipseIn: CGRect(x: m.x - 20 - 30 * k, y: ground - 3, width: 40 + 60 * k, height: 6 + 4 * k)),
                    with: .color(hotAirBalloonWelcomeRGBA(10, 14, 20, 0.4 * (1 - k))))
        }
        var c = ctx
        c.translateBy(x: m.x, y: m.y)
        c.translateBy(x: 0, y: -g.e * 0.5)
        c.rotate(by: .degrees(sw.0))
        c.translateBy(x: 0, y: g.e * 0.5)
        var env = c
        env.scaleBy(x: 1 + 0.03 * p.heat, y: 1 + 0.015 * p.heat)
        drawEnvelope(env, g, turn: p.turn, heat: p.heat)
        drawRig(c, g)
        drawFlame(c, heat: p.heat, time: p.time)
    }

    /// A bloom of warm light round the burner reaches the screen at full heat.
    static func drawBloom(_ ctx: GraphicsContext, _ g: G, _ p: HotAirBalloonWelcomePose) {
        guard p.heat > 0.01 else { return }
        let m = mouth(g, p)
        var glow = ctx
        glow.blendMode = .plusLighter
        glow.fill(hotAirBalloonWelcomeCircle(m.x, m.y + 20, 150), with: .radialGradient(
            Gradient(colors: [hotAirBalloonWelcomeRGBA(255, 150, 70, 0.16 * p.heat), hotAirBalloonWelcomeRGBA(255, 150, 70, 0)]),
            center: CGPoint(x: m.x, y: m.y + 20), startRadius: 0, endRadius: 150
        ))
    }
}

/// A small tile of seeded grey noise, tiled over the sky as grain.
enum HotAirBalloonWelcomeGrain {
    static let image: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n)
        var seed: UInt32 = 0x51ED270B
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
