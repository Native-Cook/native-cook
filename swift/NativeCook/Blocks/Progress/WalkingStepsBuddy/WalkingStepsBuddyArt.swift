import SwiftUI

/// Everything of Walking Steps Buddy drawn in code: the parallax street, Bao the bun buddy, the
/// goal bar and the small icons. All coordinates are in the 393-pt-wide design space.
enum WalkingStepsBuddyArt {
    typealias M = WalkingStepsBuddyMotion

    // MARK: Colour

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }

    static func mix(_ a: Int, _ b: Int, _ f: Double, _ alpha: Double = 1) -> Color {
        func ch(_ h: Int, _ s: Int) -> Double { Double((h >> s) & 255) / 255 }
        let g = M.clamp(f)
        return Color(.sRGB, red: ch(a, 16) + (ch(b, 16) - ch(a, 16)) * g,
                     green: ch(a, 8) + (ch(b, 8) - ch(a, 8)) * g,
                     blue: ch(a, 0) + (ch(b, 0) - ch(a, 0)) * g, opacity: alpha)
    }

    static let page = 0xFCFCFA
    static let ink = 0x1D2733
    static let label = 0x4A5A6C
    static let muted = 0x8A95A3
    static let orange = 0xFF6A1F
    static let orangeLight = 0xFF9A4D

    // MARK: Helpers

    static func ellipse(_ cx: Double, _ cy: Double, _ w: Double, _ h: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h))
    }

    /// A soft 3D ball: highlight up-left, shade down-right.
    static func blob(_ ctx: GraphicsContext, _ cx: Double, _ cy: Double, _ r: Double, _ hi: Int, _ base: Int, _ shade: Int) {
        ctx.fill(ellipse(cx, cy, 2 * r, 2 * r), with: .radialGradient(
            Gradient(stops: [.init(color: color(hi), location: 0), .init(color: color(base), location: 0.5), .init(color: color(shade), location: 1)]),
            center: CGPoint(x: cx - 0.35 * r, y: cy - 0.45 * r), startRadius: 0, endRadius: 1.35 * r))
    }

    static func softShadow(_ ctx: GraphicsContext, _ cx: Double, _ cy: Double, _ w: Double, _ h: Double, _ alpha: Double, _ hex: Int = 0x6B5A3E) {
        var c = ctx
        c.translateBy(x: cx, y: cy)
        c.scaleBy(x: 1, y: h / w)
        c.fill(ellipse(0, 0, w, w), with: .radialGradient(
            Gradient(stops: [.init(color: color(hex, alpha), location: 0), .init(color: color(hex, alpha * 0.6), location: 0.5), .init(color: color(hex, 0), location: 1)]),
            center: .zero, startRadius: 0, endRadius: w / 2))
    }

    static func tuft(_ ctx: GraphicsContext, _ x: Double, _ base: Double, _ h: Double, dark: Int = 0x6CBE4C, light: Int = 0xA8E07E) {
        var p = Path()
        for (dx, dh, lean) in [(-5.0, 0.72, -5.0), (5.0, 0.8, 6.0), (0.0, 1.0, 1.0)] {
            let bx = x + dx * h / 20, w = 7 * h / 20, top = base - h * dh
            p.move(to: CGPoint(x: bx - w / 2, y: base))
            p.addQuadCurve(to: CGPoint(x: bx + lean * h / 20, y: top), control: CGPoint(x: bx - w / 4 + lean * h / 40, y: base - h * dh * 0.55))
            p.addQuadCurve(to: CGPoint(x: bx + w / 2, y: base), control: CGPoint(x: bx + w / 4 + lean * h / 40, y: base - h * dh * 0.5))
            p.closeSubpath()
        }
        ctx.fill(p, with: .linearGradient(Gradient(colors: [color(light), color(dark)]),
                                          startPoint: CGPoint(x: x, y: base - h), endPoint: CGPoint(x: x, y: base)))
    }

    /// Repeats a tile across the screen: calls `draw` with each tile's origin x.
    static func tiled(_ distance: Double, factor: Double, width: Double, _ draw: (Double) -> Void) {
        let off = M.mod(distance * factor, width)
        var x = -off
        while x < 393 + 140 {
            if x + width > -140 { draw(x) }
            x += width
        }
    }

    // MARK: Scene

    static func drawScene(_ ctx: GraphicsContext, distance d: Double, t: Double, ground g: Double, pose: M.Pose,
                          happy: Bool) {
        let dusk = M.dusk(t)
        let tint = 0.85 * dusk
        // Sky.
        ctx.fill(Path(CGRect(x: 0, y: 0, width: 393, height: g + 8)), with: .linearGradient(
            Gradient(stops: [.init(color: mix(0x8CCBF7, 0xB3A6EA, tint), location: 0),
                             .init(color: mix(0xB8E0FA, 0xF6BCB4, tint), location: 0.55),
                             .init(color: mix(0xE6F4FC, 0xFFE4C8, tint), location: 1)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: g + 8)))
        // Sun: sets behind the hills at dusk and rises again for the next day.
        let sunY = g - 210 + 250 * dusk
        var sun = ctx
        sun.clip(to: Path(CGRect(x: 0, y: 0, width: 393, height: g)))
        sun.fill(ellipse(318, sunY, 128, 128), with: .radialGradient(
            Gradient(stops: [.init(color: mix(0xFFF3B0, 0xFFC27A, dusk, 0.55), location: 0), .init(color: mix(0xFFF3B0, 0xFFC27A, dusk, 0), location: 1)]),
            center: CGPoint(x: 318, y: sunY), startRadius: 0, endRadius: 64))
        sun.fill(ellipse(318, sunY, 40, 40), with: .radialGradient(
            Gradient(colors: [mix(0xFFFBE0, 0xFFE2B0, dusk), mix(0xFFD66B, 0xFF9A5A, dusk)]),
            center: CGPoint(x: 312, y: sunY - 6), startRadius: 0, endRadius: 24))
        drawClouds(ctx, d, g, dusk)
        drawHills(ctx, d, g, dusk)
        // Bushes and trees (0.5x).
        tiled(d, factor: M.bushFactor, width: M.bushTile) { x in drawBushTile(ctx, x, g) }
        // Lawn verge and the sandy path.
        ctx.fill(Path(CGRect(x: 0, y: g - 5, width: 393, height: 8)), with: .linearGradient(
            Gradient(colors: [color(0xC4E9A4), color(0xA9DC86)]), startPoint: CGPoint(x: 0, y: g - 5), endPoint: CGPoint(x: 0, y: g + 3)))
        let s = g + 38
        ctx.fill(Path(CGRect(x: 0, y: g, width: 393, height: 40)), with: .linearGradient(
            Gradient(stops: [.init(color: color(0xF3DDB4), location: 0), .init(color: color(0xF8EAD0), location: 0.5),
                             .init(color: color(0xFAF1E1), location: 0.82), .init(color: color(page), location: 1)]),
            startPoint: CGPoint(x: 0, y: g), endPoint: CGPoint(x: 0, y: s + 2)))
        ctx.fill(Path(CGRect(x: 0, y: g, width: 393, height: 1.2)), with: .color(.white.opacity(0.55)))
        // Props on the path (1x).
        tiled(d, factor: 1, width: M.tile) { x in drawPropTile(ctx, x, g) }
        drawBao(ctx, x: M.mascotX, ground: g + 22, pose: pose, happy: happy)
        // Front grass (1.25x).
        tiled(d, factor: M.frontFactor, width: M.frontTile) { x in
            tuft(ctx, x + 40, s - 1, 20, dark: 0x58AE40, light: 0x93D56E)
            tuft(ctx, x + 196, s - 2, 26, dark: 0x58AE40, light: 0x93D56E)
            tuft(ctx, x + 300, s - 1, 15, dark: 0x58AE40, light: 0x93D56E)
        }
    }

    static func cloudPath(_ cx: Double, _ cy: Double, _ s: Double) -> Path {
        var p = Path()
        p.addEllipse(in: CGRect(x: cx - 34 * s, y: cy - 22 * s, width: 44 * s, height: 44 * s))
        p.addEllipse(in: CGRect(x: cx - 6 * s, y: cy - 30 * s, width: 50 * s, height: 50 * s))
        p.addEllipse(in: CGRect(x: cx - 54 * s, y: cy - 6 * s, width: 34 * s, height: 30 * s))
        p.addEllipse(in: CGRect(x: cx + 26 * s, y: cy - 4 * s, width: 32 * s, height: 28 * s))
        p.addRoundedRect(in: CGRect(x: cx - 46 * s, y: cy + 2 * s, width: 98 * s, height: 22 * s), cornerSize: CGSize(width: 11 * s, height: 11 * s))
        return p
    }

    static let clouds: [(x: Double, y: Double, s: Double)] = [(60, -200, 1.0), (300, -250, 0.78), (190, -150, 0.62), (380, -292, 0.9)]

    static func drawClouds(_ ctx: GraphicsContext, _ d: Double, _ g: Double, _ dusk: Double) {
        for (i, c) in clouds.enumerated() {
            let tau = M.fract(d / M.tile + Double(i) / 4)
            let alpha = M.smooth(0, 0.18, tau) * (1 - M.smooth(0.82, 1, tau))
            if alpha < 0.01 { continue }
            let x = c.x - M.cloudDrift * tau, y = g + c.y
            let path = cloudPath(x, y, c.s)
            var layer = ctx
            layer.opacity = alpha
            layer.fill(path, with: .linearGradient(
                Gradient(colors: [mix(0xFFFFFF, 0xFFF0EA, dusk), mix(0xE4F1FB, 0xF3CBCB, dusk)]),
                startPoint: CGPoint(x: x, y: y - 10 * c.s), endPoint: CGPoint(x: x, y: y + 24 * c.s)))
        }
    }

    static func drawHills(_ ctx: GraphicsContext, _ d: Double, _ g: Double, _ dusk: Double) {
        let off = M.mod(d * M.hillFactor, M.hillTile)
        var p = Path()
        p.move(to: CGPoint(x: -4, y: g + 2))
        var x = -4.0
        while x <= 397 {
            let u = 2 * Double.pi * (x + off) / M.hillTile
            p.addLine(to: CGPoint(x: x, y: g - 22 - 10 * sin(u) - 5 * sin(2 * u + 1.3)))
            x += 4
        }
        p.addLine(to: CGPoint(x: 397, y: g + 2))
        p.closeSubpath()
        ctx.fill(p, with: .linearGradient(Gradient(colors: [mix(0xD3EEDB, 0xDCD4E4, dusk * 0.6), mix(0xB9E0C5, 0xC2D3BE, dusk * 0.6)]),
                                          startPoint: CGPoint(x: 0, y: g - 40), endPoint: CGPoint(x: 0, y: g)))
    }

    static func drawBushTile(_ ctx: GraphicsContext, _ x: Double, _ g: Double) {
        // A round tree.
        ctx.fill(Path(roundedRect: CGRect(x: x + 66, y: g - 40, width: 9, height: 40), cornerRadius: 3),
                 with: .linearGradient(Gradient(colors: [color(0xD5A877), color(0xB98A5C)]), startPoint: CGPoint(x: x + 66, y: 0), endPoint: CGPoint(x: x + 75, y: 0)))
        blob(ctx, x + 52, g - 52, 20, 0xC7EDA9, 0x9BD67E, 0x76BD5F)
        blob(ctx, x + 90, g - 50, 21, 0xC7EDA9, 0x9BD67E, 0x76BD5F)
        blob(ctx, x + 71, g - 70, 29, 0xCDF0B0, 0xA2DA84, 0x7CC164)
        // Bushes.
        blob(ctx, x + 190, g - 12, 17, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(ctx, x + 232, g - 11, 15, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(ctx, x + 211, g - 19, 22, 0xC9EEAB, 0x9AD57C, 0x73BC5D)
        blob(ctx, x + 322, g - 11, 15, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(ctx, x + 342, g - 17, 19, 0xC9EEAB, 0x9AD57C, 0x73BC5D)
        // A slim tree.
        ctx.fill(Path(roundedRect: CGRect(x: x + 396, y: g - 30, width: 7, height: 30), cornerRadius: 3), with: .color(color(0xC99A6B)))
        blob(ctx, x + 399, g - 48, 18, 0xCDF0B0, 0xA2DA84, 0x7CC164)
    }

    static func drawPropTile(_ ctx: GraphicsContext, _ x: Double, _ g: Double) {
        let base = g + 4
        // Pebbles.
        for (px, py, w) in [(150.0, 14.0, 8.0), (340, 28, 10), (520, 18, 7), (700, 30, 9), (810, 12, 6), (40, 26, 7)] {
            ctx.fill(ellipse(x + px, g + py, w, w * 0.5), with: .color(color(0xE6CB9C)))
            ctx.fill(ellipse(x + px - w * 0.12, g + py - w * 0.1, w * 0.5, w * 0.2), with: .color(.white.opacity(0.6)))
        }
        drawBench(ctx, x + 70, base)
        drawSign(ctx, x + 470, base)
        drawPlanter(ctx, x + 660, base)
        for (tx, h) in [(30.0, 16.0), (300, 20), (408, 14), (610, 18), (822, 22)] {
            tuft(ctx, x + tx, g + 3, h)
        }
    }

    static func drawBench(_ ctx: GraphicsContext, _ x: Double, _ base: Double) {
        softShadow(ctx, x + 80, base, 176, 12, 0.16)
        let leg = Gradient(colors: [color(0xDDB57A), color(0xC59A60)])
        for lx in [22.0, 132] {
            ctx.fill(Path(roundedRect: CGRect(x: x + lx + 8, y: base - 30, width: 7, height: 24), cornerRadius: 2), with: .color(color(0xE2C390)))
        }
        // Backrest supports and slats.
        for sx in [22.0, 134] {
            ctx.fill(Path(roundedRect: CGRect(x: x + sx, y: base - 70, width: 7, height: 44), cornerRadius: 3),
                     with: .linearGradient(leg, startPoint: CGPoint(x: x + sx, y: 0), endPoint: CGPoint(x: x + sx + 7, y: 0)))
        }
        for (sy, h) in [(68.0, 13.0), (50, 13)] {
            let r = CGRect(x: x + 4, y: base - sy, width: 152, height: h)
            ctx.fill(Path(roundedRect: r, cornerRadius: 5), with: .linearGradient(
                Gradient(colors: [color(0xF8E2B6), color(0xE9C88E)]), startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
            ctx.fill(Path(roundedRect: CGRect(x: r.minX + 4, y: r.minY + 1.5, width: r.width - 8, height: 2), cornerRadius: 1), with: .color(.white.opacity(0.45)))
        }
        // Seat.
        let seat = CGRect(x: x, y: base - 34, width: 160, height: 12)
        ctx.fill(Path(roundedRect: seat, cornerRadius: 5), with: .linearGradient(
            Gradient(colors: [color(0xFBE7BF), color(0xEFD09A)]), startPoint: CGPoint(x: 0, y: seat.minY), endPoint: CGPoint(x: 0, y: seat.maxY)))
        ctx.fill(Path(roundedRect: CGRect(x: x + 2, y: base - 24, width: 156, height: 6), cornerRadius: 3), with: .color(color(0xD9B479)))
        for lx in [10.0, 140] {
            ctx.fill(Path(roundedRect: CGRect(x: x + lx, y: base - 22, width: 9, height: 22), cornerRadius: 2.5),
                     with: .linearGradient(leg, startPoint: CGPoint(x: x + lx, y: 0), endPoint: CGPoint(x: x + lx + 9, y: 0)))
        }
    }

    static func drawSign(_ ctx: GraphicsContext, _ x: Double, _ base: Double) {
        softShadow(ctx, x, base, 34, 8, 0.18)
        ctx.fill(Path(roundedRect: CGRect(x: x - 3.5, y: base - 128, width: 7, height: 128), cornerRadius: 3), with: .linearGradient(
            Gradient(colors: [color(0xF1F4F7), color(0xC5CED8)]), startPoint: CGPoint(x: x - 3.5, y: 0), endPoint: CGPoint(x: x + 3.5, y: 0)))
        let cy = base - 150
        ctx.fill(ellipse(x + 1.5, cy + 2.5, 56, 56), with: .color(color(0x2F6FB0, 0.18)))
        ctx.fill(ellipse(x, cy, 56, 56), with: .color(.white))
        ctx.fill(ellipse(x, cy, 48, 48), with: .radialGradient(
            Gradient(colors: [color(0x7CC2F7), color(0x3E95E6)]), center: CGPoint(x: x - 8, y: cy - 10), startRadius: 0, endRadius: 32))
        var fig = Path()
        fig.addEllipse(in: CGRect(x: x - 4.5, y: cy - 17, width: 7, height: 7))
        ctx.fill(fig, with: .color(.white))
        var limbs = Path()
        limbs.move(to: CGPoint(x: x - 1, y: cy - 7)); limbs.addLine(to: CGPoint(x: x + 1, y: cy + 4))
        limbs.move(to: CGPoint(x: x + 1, y: cy + 4)); limbs.addLine(to: CGPoint(x: x - 5, y: cy + 13))
        limbs.move(to: CGPoint(x: x + 1, y: cy + 4)); limbs.addLine(to: CGPoint(x: x + 7, y: cy + 12))
        limbs.move(to: CGPoint(x: x - 7, y: cy + 1)); limbs.addLine(to: CGPoint(x: x, y: cy - 4)); limbs.addLine(to: CGPoint(x: x + 7, y: cy - 1))
        ctx.stroke(limbs, with: .color(.white), style: StrokeStyle(lineWidth: 3.2, lineCap: .round, lineJoin: .round))
        var gleam = Path()
        gleam.addArc(center: CGPoint(x: x, y: cy), radius: 19, startAngle: .degrees(200), endAngle: .degrees(250), clockwise: false)
        ctx.stroke(gleam, with: .color(.white.opacity(0.45)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
    }

    static func drawPlanter(_ ctx: GraphicsContext, _ x: Double, _ base: Double) {
        softShadow(ctx, x + 50, base, 118, 10, 0.16)
        blob(ctx, x + 22, base - 36, 15, 0xB9E794, 0x8FD06E, 0x68B44E)
        blob(ctx, x + 78, base - 36, 15, 0xB9E794, 0x8FD06E, 0x68B44E)
        blob(ctx, x + 50, base - 42, 19, 0xB9E794, 0x8FD06E, 0x68B44E)
        for (fx, fy, petal, heart) in [(24.0, 48.0, 0xFF9FB8, 0xFFE38A), (52, 58, 0xFFD25C, 0xFF9A4D), (78, 47, 0xFF9FB8, 0xFFE38A), (40, 40, 0xFFFFFF, 0xFFD25C), (64, 42, 0xFF9FB8, 0xFFE38A)] {
            for k in 0..<5 {
                let a = Double(k) * 2 * Double.pi / 5
                ctx.fill(ellipse(x + fx + 4 * cos(a), base - fy + 4 * sin(a), 5.5, 5.5), with: .color(color(petal)))
            }
            ctx.fill(ellipse(x + fx, base - fy, 4, 4), with: .color(color(heart)))
        }
        let box = CGRect(x: x, y: base - 30, width: 100, height: 30)
        ctx.fill(Path(roundedRect: box, cornerRadius: 7), with: .linearGradient(
            Gradient(colors: [color(0xF1AE86), color(0xD9825C)]), startPoint: CGPoint(x: 0, y: box.minY), endPoint: CGPoint(x: 0, y: box.maxY)))
        ctx.fill(Path(roundedRect: CGRect(x: x - 3, y: base - 33, width: 106, height: 9), cornerRadius: 4.5), with: .linearGradient(
            Gradient(colors: [color(0xF8C2A0), color(0xE79A73)]), startPoint: CGPoint(x: 0, y: base - 33), endPoint: CGPoint(x: 0, y: base - 24)))
    }

    // MARK: Bao

    /// Bao's bun-shaped body, bottom centre at the origin.
    static let bun: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0))
        p.addCurve(to: CGPoint(x: -75, y: -52), control1: CGPoint(x: -58, y: 0), control2: CGPoint(x: -76, y: -18))
        p.addCurve(to: CGPoint(x: 0, y: -136), control1: CGPoint(x: -74, y: -102), control2: CGPoint(x: -42, y: -136))
        p.addCurve(to: CGPoint(x: 75, y: -52), control1: CGPoint(x: 42, y: -136), control2: CGPoint(x: 74, y: -102))
        p.addCurve(to: CGPoint(x: 0, y: 0), control1: CGPoint(x: 76, y: -18), control2: CGPoint(x: 58, y: 0))
        p.closeSubpath()
        return p
    }()

    static func drawFoot(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ angle: Double, far: Bool) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        c.fill(ellipse(0, 0, 38, 20), with: .linearGradient(
            Gradient(colors: far ? [color(0xF3E6D2), color(0xD6BF9F)] : [color(0xFFF8EE), color(0xE6D2B6)]),
            startPoint: CGPoint(x: 0, y: -10), endPoint: CGPoint(x: 0, y: 10)))
        c.fill(ellipse(-4, -5, 18, 5), with: .color(.white.opacity(far ? 0.25 : 0.55)))
    }

    static func drawArm(_ ctx: GraphicsContext, pivot: CGPoint, angle: Double, far: Bool) {
        var c = ctx
        c.translateBy(x: pivot.x, y: pivot.y)
        c.rotate(by: .degrees(angle))
        let r = CGRect(x: -9.5, y: -6, width: 19, height: 34)
        let shape = Path(roundedRect: r, cornerRadius: 9.5)
        c.fill(shape, with: .linearGradient(
            Gradient(colors: far ? [color(0xF1E3CD), color(0xD9C3A3)] : [color(0xFFFCF6), color(0xE8D6BC)]),
            startPoint: CGPoint(x: -9.5, y: 0), endPoint: CGPoint(x: 9.5, y: 0)))
        c.stroke(shape, with: .color(color(0xDCC6A6, 0.8)), lineWidth: 1.2)
        if !far { c.fill(ellipse(-3.5, 6, 5, 14), with: .color(.white.opacity(0.6))) }
    }

    /// The tote's strap, drawn over the hand that holds it.
    static func drawStrap(_ ctx: GraphicsContext, hand: CGPoint, angle: Double) {
        var c = ctx
        c.translateBy(x: hand.x, y: hand.y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: 0.84, y: 0.84)
        var strap = Path()
        strap.move(to: CGPoint(x: -13, y: 18))
        strap.addCurve(to: CGPoint(x: 13, y: 18), control1: CGPoint(x: -13, y: -8), control2: CGPoint(x: 13, y: -8))
        c.stroke(strap, with: .color(color(0xE2911F)), style: StrokeStyle(lineWidth: 4.4, lineCap: .round))
        var shine = Path()
        shine.move(to: CGPoint(x: -9, y: 4))
        shine.addQuadCurve(to: CGPoint(x: -2, y: -4), control: CGPoint(x: -8, y: -3))
        c.stroke(shine, with: .color(color(0xFFD27A)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
    }

    static func drawBag(_ ctx: GraphicsContext, hand: CGPoint, angle: Double) {
        var c = ctx
        c.translateBy(x: hand.x, y: hand.y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: 0.84, y: 0.84)
        // A baguette peeking out.
        var bread = c
        bread.translateBy(x: 10, y: 10)
        bread.rotate(by: .degrees(24))
        bread.fill(Path(roundedRect: CGRect(x: -7, y: -26, width: 14, height: 46), cornerRadius: 7), with: .linearGradient(
            Gradient(colors: [color(0xF3C784), color(0xC98B45)]), startPoint: CGPoint(x: -7, y: 0), endPoint: CGPoint(x: 7, y: 0)))
        for sy in [-19.0, -11, -3] {
            var cut = Path()
            cut.move(to: CGPoint(x: -3.5, y: sy + 3)); cut.addLine(to: CGPoint(x: 3.5, y: sy))
            bread.stroke(cut, with: .color(color(0xFBE3B8)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
        var body = Path()
        body.move(to: CGPoint(x: -22, y: 16))
        body.addLine(to: CGPoint(x: 22, y: 16))
        body.addQuadCurve(to: CGPoint(x: 26, y: 50), control: CGPoint(x: 25, y: 18))
        body.addQuadCurve(to: CGPoint(x: 18, y: 58), control: CGPoint(x: 27, y: 58))
        body.addLine(to: CGPoint(x: -18, y: 58))
        body.addQuadCurve(to: CGPoint(x: -26, y: 50), control: CGPoint(x: -27, y: 58))
        body.addQuadCurve(to: CGPoint(x: -22, y: 16), control: CGPoint(x: -25, y: 18))
        body.closeSubpath()
        c.fill(body, with: .linearGradient(Gradient(colors: [color(0xFFD272), color(0xF6B23E), color(0xE69523)]),
                                           startPoint: CGPoint(x: 0, y: 16), endPoint: CGPoint(x: 0, y: 58)))
        c.fill(Path(roundedRect: CGRect(x: -16, y: 21, width: 5, height: 30), cornerRadius: 2.5), with: .color(.white.opacity(0.35)))
        c.fill(Path(roundedRect: CGRect(x: -22, y: 15, width: 44, height: 5), cornerRadius: 2.5), with: .color(color(0xFFE29A)))
        // A small heart label.
        var heart = Path()
        heart.move(to: CGPoint(x: 6, y: 42))
        heart.addCurve(to: CGPoint(x: 6, y: 32), control1: CGPoint(x: -2, y: 37), control2: CGPoint(x: 1, y: 29))
        heart.addCurve(to: CGPoint(x: 6, y: 42), control1: CGPoint(x: 11, y: 29), control2: CGPoint(x: 14, y: 37))
        c.fill(heart, with: .color(color(0xFF7A59)))
    }

    /// Bao at a ground point, in a walk pose.
    static func drawBao(_ ctx: GraphicsContext, x: Double, ground: Double, pose p: M.Pose, happy: Bool) {
        let air = p.lift
        softShadow(ctx, x, ground, 128 * (1 - air / 110) - 10 * (-p.bob / 7), 15, 0.24 * (1 - air / 80))
        var feet = ctx
        feet.translateBy(x: x, y: ground - air)
        drawFoot(feet, 14 + p.far.x, -9 - p.far.lift, p.far.angle, far: true)
        drawFoot(feet, -12 + p.near.x, -9 - p.near.lift, p.near.angle, far: false)

        var b = ctx
        b.translateBy(x: x, y: ground - 15 + p.bob - air)
        b.rotate(by: .degrees(p.lean))
        b.scaleBy(x: p.sx, y: p.sy)
        drawArm(b, pivot: CGPoint(x: 66, y: -72), angle: p.arm, far: true)
        // The near arm comes out from behind the body and holds the tote away from it.
        let pivot = CGPoint(x: -62, y: -68)
        let armAngle = 42 - 0.8 * p.arm
        let a = armAngle * Double.pi / 180
        let hand = CGPoint(x: pivot.x - 26 * sin(a), y: pivot.y + 26 * cos(a))
        let bagAngle = -p.arm * 0.35 + p.bag
        drawArm(b, pivot: pivot, angle: armAngle, far: false)
        b.fill(bun, with: .radialGradient(
            Gradient(stops: [.init(color: color(0xFFFFFF), location: 0), .init(color: color(0xFFF8EE), location: 0.45), .init(color: color(0xE6D3B8), location: 1)]),
            center: CGPoint(x: -22, y: -98), startRadius: 0, endRadius: 150))
        var occ = b
        occ.clip(to: bun)
        occ.fill(Path(CGRect(x: -80, y: -40, width: 160, height: 42)), with: .linearGradient(
            Gradient(colors: [color(0xD8C2A2, 0), color(0xD8C2A2, 0.45)]), startPoint: CGPoint(x: 0, y: -40), endPoint: CGPoint(x: 0, y: 0)))
        occ.fill(ellipse(-30, -104, 46, 26), with: .radialGradient(
            Gradient(colors: [.white.opacity(0.9), .white.opacity(0)]), center: CGPoint(x: -30, y: -104), startRadius: 0, endRadius: 24))
        // Pleats around the knot.
        var pleats = Path()
        for a in [-70.0, -35, 0, 35, 70] {
            let r = a * Double.pi / 180
            let r2 = r + 0.35
            pleats.move(to: CGPoint(x: 7 * sin(r), y: -129 + 4 * cos(r)))
            pleats.addQuadCurve(to: CGPoint(x: 26 * sin(r2), y: -129 + 17 * cos(r2)),
                                control: CGPoint(x: 18 * sin(r) , y: -129 + 13 * cos(r)))
        }
        b.stroke(pleats, with: .color(color(0xE8D6BC)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        // The knot lags behind the bob.
        var knot = b
        knot.translateBy(x: 0, y: -130)
        knot.rotate(by: .degrees(p.knot))
        var twist = Path()
        twist.move(to: CGPoint(x: -12, y: 2))
        twist.addCurve(to: CGPoint(x: 2, y: -20), control1: CGPoint(x: -14, y: -10), control2: CGPoint(x: -6, y: -18))
        twist.addCurve(to: CGPoint(x: 9, y: -12), control1: CGPoint(x: 8, y: -22), control2: CGPoint(x: 11, y: -16))
        twist.addCurve(to: CGPoint(x: 12, y: 2), control1: CGPoint(x: 14, y: -8), control2: CGPoint(x: 15, y: -2))
        twist.closeSubpath()
        knot.fill(twist, with: .radialGradient(
            Gradient(colors: [color(0xFFFFFF), color(0xFFF6EA), color(0xE4CFB0)]), center: CGPoint(x: -4, y: -12), startRadius: 0, endRadius: 20))
        var curl = Path()
        curl.move(to: CGPoint(x: -4, y: -4))
        curl.addQuadCurve(to: CGPoint(x: 5, y: -14), control: CGPoint(x: -3, y: -14))
        knot.stroke(curl, with: .color(color(0xE3CFB2)), style: StrokeStyle(lineWidth: 2, lineCap: .round))

        // Face, turned to the right.
        for cx in [-4.0, 58] {
            b.fill(ellipse(cx, -54, 20, 12), with: .radialGradient(
                Gradient(colors: [color(0xFF9C87, 0.7), color(0xFF9C87, 0)]), center: CGPoint(x: cx, y: -54), startRadius: 0, endRadius: 11))
        }
        for ex in [12.0, 44] {
            if happy {
                var arc = Path()
                arc.move(to: CGPoint(x: ex - 6, y: -66))
                arc.addQuadCurve(to: CGPoint(x: ex + 6, y: -66), control: CGPoint(x: ex, y: -77))
                b.stroke(arc, with: .color(color(0x2A2420)), style: StrokeStyle(lineWidth: 3.2, lineCap: .round))
            } else {
                b.fill(ellipse(ex, -69, 10.5, 14), with: .color(color(0x2A2420)))
                b.fill(ellipse(ex - 1.8, -72.5, 3.6, 4.2), with: .color(.white))
            }
        }
        if happy {
            var mouth = Path()
            mouth.move(to: CGPoint(x: 21, y: -58))
            mouth.addQuadCurve(to: CGPoint(x: 35, y: -58), control: CGPoint(x: 28, y: -45))
            mouth.closeSubpath()
            b.fill(mouth, with: .color(color(0x9A4232)))
        } else {
            var mouth = Path()
            mouth.move(to: CGPoint(x: 21, y: -58))
            mouth.addQuadCurve(to: CGPoint(x: 28, y: -57), control: CGPoint(x: 24.5, y: -53))
            mouth.addQuadCurve(to: CGPoint(x: 35, y: -58), control: CGPoint(x: 31.5, y: -53))
            b.stroke(mouth, with: .color(color(0x2A2420)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
        }
        // Near arm with the tote.
        drawBag(b, hand: hand, angle: bagAngle)
        drawStrap(b, hand: hand, angle: bagAngle)
    }

    // MARK: Bar

    static func drawBar(_ ctx: GraphicsContext, t: Double, steps: Double, top: Double) {
        let cy = top + 6
        ctx.fill(Path(roundedRect: CGRect(x: 20, y: top, width: 353, height: 12), cornerRadius: 6), with: .color(color(0xEDF0F4)))
        let end = max(34, M.barX(steps))
        let fill = CGRect(x: 20, y: top, width: end - 20, height: 12)
        ctx.fill(Path(roundedRect: fill, cornerRadius: 6), with: .linearGradient(
            Gradient(colors: [color(orangeLight), color(orange)]), startPoint: CGPoint(x: 20, y: 0), endPoint: CGPoint(x: end, y: 0)))
        ctx.fill(Path(roundedRect: CGRect(x: 24, y: top + 2, width: max(0, end - 30), height: 3), cornerRadius: 1.5), with: .color(.white.opacity(0.35)))
        for i in 0..<4 {
            let x = M.tickX[i]
            let pop = M.tickPop(i, t)
            if pop > 0.02 {
                let r = 3.5 + 6 * pop
                ctx.fill(ellipse(x, cy + 1.5, 2 * r + 2, 2 * r + 2), with: .color(color(0xC9471A, 0.25)))
                ctx.fill(ellipse(x, cy, 2 * r + 4, 2 * r + 4), with: .color(.white))
                ctx.fill(ellipse(x, cy, 2 * r, 2 * r), with: .linearGradient(
                    Gradient(colors: [color(orangeLight), color(orange)]), startPoint: CGPoint(x: x, y: cy - r), endPoint: CGPoint(x: x, y: cy + r)))
                var check = Path()
                let s = r / 9.5
                check.move(to: CGPoint(x: x - 4 * s, y: cy + 0.2 * s))
                check.addLine(to: CGPoint(x: x - 1 * s, y: cy + 3.2 * s))
                check.addLine(to: CGPoint(x: x + 4.5 * s, y: cy - 3 * s))
                ctx.stroke(check, with: .color(.white), style: StrokeStyle(lineWidth: 2.2 * s, lineCap: .round, lineJoin: .round))
            } else {
                ctx.fill(ellipse(x, cy, 6, 6), with: .color(.white))
            }
        }
        // The walker's own marker at the end of the fill.
        ctx.fill(ellipse(end - 6, cy, 6, 6), with: .color(.white))
    }

    // MARK: Celebration

    static func drawCelebration(_ ctx: GraphicsContext, t: Double, barCentre: Double) {
        let x0 = M.tickX[0]
        for c in M.confetti(t) {
            var p = ctx
            p.opacity = c.alpha
            p.translateBy(x: x0 + c.x, y: barCentre + c.y)
            p.rotate(by: .radians(c.angle))
            if c.kind == 0 {
                p.fill(ellipse(0, 0, 6, 6), with: .color(color(c.color)))
            } else {
                p.fill(Path(roundedRect: CGRect(x: -4, y: -2, width: 8, height: 4), cornerRadius: 1), with: .color(color(c.color)))
            }
        }
        let b = M.bubble(t)
        if b.alpha > 0.01 {
            var p = ctx
            p.opacity = b.alpha
            p.translateBy(x: x0 + 22, y: barCentre + b.y)
            p.scaleBy(x: b.scale, y: b.scale)
            let r = CGRect(x: -42, y: -13, width: 84, height: 26)
            p.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: 2), cornerRadius: 13), with: .color(color(0xC9471A, 0.25)))
            p.fill(Path(roundedRect: r, cornerRadius: 13), with: .linearGradient(
                Gradient(colors: [color(orangeLight), color(orange)]), startPoint: CGPoint(x: 0, y: -13), endPoint: CGPoint(x: 0, y: 13)))
            var tail = Path()
            tail.move(to: CGPoint(x: -28, y: 12)); tail.addLine(to: CGPoint(x: -22, y: 19)); tail.addLine(to: CGPoint(x: -16, y: 12))
            p.fill(tail, with: .color(color(orange)))
            drawCoin(p, CGPoint(x: -27, y: 0), 7, mixF: 1)
            p.draw(Text("+10 coins").font(.system(size: 12, weight: .bold)).foregroundStyle(.white), at: CGPoint(x: 6, y: 0))
        }
    }

    // MARK: Icons

    static func drawCoin(_ ctx: GraphicsContext, _ c: CGPoint, _ r: Double, mixF: Double) {
        ctx.fill(ellipse(c.x, c.y, 2 * r, 2 * r), with: .linearGradient(
            Gradient(colors: [mix(0xD9DEE4, 0xFFD76A, mixF), mix(0xBEC6CF, 0xF0A12E, mixF)]),
            startPoint: CGPoint(x: c.x, y: c.y - r), endPoint: CGPoint(x: c.x, y: c.y + r)))
        ctx.stroke(ellipse(c.x, c.y, 1.25 * r, 1.25 * r), with: .color(mix(0xEEF1F4, 0xFFEBA8, mixF)), lineWidth: max(1, r * 0.22))
    }

    static func drawShoe(_ ctx: GraphicsContext) {
        softShadow(ctx, 18, 25, 32, 5, 0.25, 0xC9471A)
        var upper = Path()
        upper.move(to: CGPoint(x: 3, y: 19))
        upper.addCurve(to: CGPoint(x: 13, y: 5), control1: CGPoint(x: 2, y: 10), control2: CGPoint(x: 7, y: 5))
        upper.addLine(to: CGPoint(x: 18, y: 6))
        upper.addCurve(to: CGPoint(x: 33, y: 17), control1: CGPoint(x: 24, y: 10), control2: CGPoint(x: 33, y: 11))
        upper.addLine(to: CGPoint(x: 33, y: 19))
        upper.closeSubpath()
        ctx.fill(upper, with: .linearGradient(Gradient(colors: [color(0xFFB067), color(0xFF6A1F)]),
                                              startPoint: CGPoint(x: 0, y: 5), endPoint: CGPoint(x: 0, y: 19)))
        ctx.fill(ellipse(8, 8, 8, 5), with: .color(color(0xD9531A)))
        var laces = Path()
        laces.move(to: CGPoint(x: 15, y: 8.5)); laces.addLine(to: CGPoint(x: 18.5, y: 11.5))
        laces.move(to: CGPoint(x: 18.5, y: 7.5)); laces.addLine(to: CGPoint(x: 22, y: 10.5))
        ctx.stroke(laces, with: .color(.white), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        ctx.fill(Path(roundedRect: CGRect(x: 1.5, y: 17.5, width: 33, height: 6.5), cornerRadius: 3.2), with: .linearGradient(
            Gradient(colors: [color(0xFFFFFF), color(0xF0D8C4)]), startPoint: CGPoint(x: 0, y: 17.5), endPoint: CGPoint(x: 0, y: 24)))
    }

    static func drawCalendar(_ ctx: GraphicsContext) {
        softShadow(ctx, 22, 41, 34, 5, 0.18, 0x3A4A5C)
        let r = CGRect(x: 5, y: 8, width: 34, height: 31)
        ctx.fill(Path(roundedRect: r, cornerRadius: 8), with: .linearGradient(
            Gradient(colors: [color(0xFFFFFF), color(0xE5EBF2)]), startPoint: CGPoint(x: 0, y: 8), endPoint: CGPoint(x: 0, y: 39)))
        var band = ctx
        band.clip(to: Path(roundedRect: r, cornerRadius: 8))
        band.fill(Path(CGRect(x: 5, y: 8, width: 34, height: 10)), with: .linearGradient(
            Gradient(colors: [color(0xFF8C6B), color(0xF25F3C)]), startPoint: CGPoint(x: 0, y: 8), endPoint: CGPoint(x: 0, y: 18)))
        for rx in [13.0, 27] {
            ctx.fill(Path(roundedRect: CGRect(x: rx, y: 4, width: 4, height: 9), cornerRadius: 2), with: .color(color(0x5A6676)))
        }
        var check = Path()
        check.move(to: CGPoint(x: 15, y: 28)); check.addLine(to: CGPoint(x: 20, y: 33)); check.addLine(to: CGPoint(x: 29, y: 23))
        ctx.stroke(check, with: .color(color(0x37B26C)), style: StrokeStyle(lineWidth: 3.6, lineCap: .round, lineJoin: .round))
    }

    static func drawGift(_ ctx: GraphicsContext) {
        softShadow(ctx, 22, 41, 36, 5, 0.18, 0x7A2E1A)
        ctx.fill(Path(roundedRect: CGRect(x: 7, y: 20, width: 30, height: 20), cornerRadius: 4), with: .linearGradient(
            Gradient(colors: [color(0xFF8A65), color(0xE8552F)]), startPoint: CGPoint(x: 0, y: 20), endPoint: CGPoint(x: 0, y: 40)))
        ctx.fill(Path(roundedRect: CGRect(x: 4, y: 13, width: 36, height: 10), cornerRadius: 3.5), with: .linearGradient(
            Gradient(colors: [color(0xFFA486), color(0xF46C47)]), startPoint: CGPoint(x: 0, y: 13), endPoint: CGPoint(x: 0, y: 23)))
        ctx.fill(Path(CGRect(x: 19, y: 13, width: 6, height: 27)), with: .linearGradient(
            Gradient(colors: [color(0xFFE08A), color(0xF5B93A)]), startPoint: CGPoint(x: 0, y: 13), endPoint: CGPoint(x: 0, y: 40)))
        for (lx, rot) in [(15.0, -28.0), (29, 28)] {
            var loop = ctx
            loop.translateBy(x: lx, y: 9)
            loop.rotate(by: .degrees(rot))
            loop.fill(ellipse(0, 0, 12, 8), with: .color(color(0xFFD15C)))
            loop.fill(ellipse(0, 0, 5, 3), with: .color(color(0xE9A72A)))
        }
        ctx.fill(ellipse(22, 11, 6, 6), with: .color(color(0xF5B93A)))
        ctx.fill(Path(roundedRect: CGRect(x: 9, y: 25, width: 3, height: 10), cornerRadius: 1.5), with: .color(.white.opacity(0.35)))
    }

    static func drawPlane(_ ctx: GraphicsContext) {
        softShadow(ctx, 24, 41, 30, 5, 0.16, 0x1E4F86)
        var wing = Path()
        wing.move(to: CGPoint(x: 40, y: 6)); wing.addLine(to: CGPoint(x: 5, y: 21)); wing.addLine(to: CGPoint(x: 17, y: 25)); wing.closeSubpath()
        ctx.fill(wing, with: .linearGradient(Gradient(colors: [color(0x8FD0FA), color(0x4AA3EC)]), startPoint: CGPoint(x: 5, y: 6), endPoint: CGPoint(x: 40, y: 25)))
        var lower = Path()
        lower.move(to: CGPoint(x: 40, y: 6)); lower.addLine(to: CGPoint(x: 17, y: 25)); lower.addLine(to: CGPoint(x: 29, y: 36)); lower.closeSubpath()
        ctx.fill(lower, with: .linearGradient(Gradient(colors: [color(0x5DB4F2), color(0x2F82D6)]), startPoint: CGPoint(x: 17, y: 6), endPoint: CGPoint(x: 40, y: 36)))
        var fold = Path()
        fold.move(to: CGPoint(x: 40, y: 6)); fold.addLine(to: CGPoint(x: 17, y: 25)); fold.addLine(to: CGPoint(x: 19, y: 34)); fold.closeSubpath()
        ctx.fill(fold, with: .color(color(0x2369B8)))
        var dash = Path()
        dash.move(to: CGPoint(x: 3, y: 31)); dash.addLine(to: CGPoint(x: 10, y: 29))
        dash.move(to: CGPoint(x: 6, y: 38)); dash.addLine(to: CGPoint(x: 15, y: 35))
        ctx.stroke(dash, with: .color(color(0x9CCBF2)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
    }

    static func drawChevron(_ ctx: GraphicsContext) {
        var p = Path()
        p.move(to: CGPoint(x: 9, y: 2)); p.addLine(to: CGPoint(x: 2, y: 9)); p.addLine(to: CGPoint(x: 9, y: 16))
        ctx.stroke(p, with: .color(color(ink)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
    }

    static func drawShare(_ ctx: GraphicsContext) {
        var p = Path()
        p.move(to: CGPoint(x: 7, y: 8)); p.addLine(to: CGPoint(x: 4, y: 8)); p.addLine(to: CGPoint(x: 4, y: 20))
        p.addLine(to: CGPoint(x: 18, y: 20)); p.addLine(to: CGPoint(x: 18, y: 8)); p.addLine(to: CGPoint(x: 15, y: 8))
        p.move(to: CGPoint(x: 11, y: 2)); p.addLine(to: CGPoint(x: 11, y: 13))
        p.move(to: CGPoint(x: 7, y: 5.5)); p.addLine(to: CGPoint(x: 11, y: 1.5)); p.addLine(to: CGPoint(x: 15, y: 5.5))
        ctx.stroke(p, with: .color(color(ink)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    static func drawGear(_ ctx: GraphicsContext) {
        var p = Path()
        for i in 0..<8 {
            let a = Double(i) * Double.pi / 4
            let pts: [(Double, Double)] = [(7.6, a - 0.25), (10, a - 0.15), (10, a + 0.15), (7.6, a + 0.25)]
            for (j, q) in pts.enumerated() {
                let pt = CGPoint(x: 11 + q.0 * cos(q.1), y: 11 + q.0 * sin(q.1))
                if i == 0 && j == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
        }
        p.closeSubpath()
        ctx.stroke(p, with: .color(color(ink)), style: StrokeStyle(lineWidth: 1.9, lineJoin: .round))
        ctx.stroke(ellipse(11, 11, 6, 6), with: .color(color(ink)), lineWidth: 1.9)
    }
}
