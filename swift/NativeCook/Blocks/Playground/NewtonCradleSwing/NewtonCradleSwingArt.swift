import SwiftUI

/*
 Everything drawn in the cradle's canvas. The room (wall, walnut desk, the cradle's base, the back
 posts and rail) and the front posts and rail are rendered once into bitmaps; one chrome ball is
 rendered once and drawn five times. Per frame only the strings, the balls' positions, their
 contact shadows, the neighbour reflections and the fingertip are drawn.

 Light: a lamp off screen at the upper left. Highlights sit at the balls' upper left, shadows fall
 to the right on the base.
 */
struct NewtonCradleSwingGeo: Equatable {
    let w: Double
    let h: Double
    /// Ball radius and string length (pivot to ball centre), pt.
    let R: Double
    let L: Double
    let cx: Double
    /// Rest x of each ball's pivot.
    let xs: [Double]
    let pivotY: Double
    let restY: Double
    /// Front and back rails (y), and where their posts stand (x from each side).
    let yF: Double
    let yB: Double
    let frontX = 8.0
    let backX = 22.0
    /// Top of the base's top face, its front face, and the desk's far edge.
    let baseTop: Double
    let baseFront: Double
    let baseBottom: Double
    let deskY: Double

    init(w: Double, h: Double) {
        // Big chrome balls; the strings and the pull angle are chosen so the outer ball, at the
        // furthest pull, still clears the front posts on a 375 pt screen.
        let R = min(max(w * 0.066, 22), 30)
        let L = min(max(w * 0.5, 170), 200)
        let baseTop = h * 0.68
        let restY = baseTop - 30 - R
        let pivotY = restY - L
        self.w = w
        self.h = h
        self.R = R
        self.L = L
        cx = w / 2
        xs = (0..<NewtonCradleSwingMotion.balls).map { w / 2 + Double($0 - 2) * 2 * R }
        self.pivotY = pivotY
        self.restY = restY
        yF = pivotY + 7
        yB = pivotY + 7 - 14
        self.baseTop = baseTop
        baseFront = baseTop + 16
        baseBottom = baseTop + 30
        deskY = baseTop - 22
    }
}

enum NewtonCradleSwingArt {
    struct Images {
        let back: Image
        let front: Image
        let ball: Image
    }

    private static func color(_ hex: UInt32, _ a: Double = 1, dim: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255 * dim,
              green: Double((hex >> 8) & 0xFF) / 255 * dim,
              blue: Double(hex & 0xFF) / 255 * dim,
              opacity: a)
    }

    private static func gradient(_ stops: [(UInt32, Double, Double)], dim: Double = 1) -> Gradient {
        Gradient(stops: stops.map { .init(color: color($0.0, $0.1, dim: dim), location: $0.2) })
    }

    private static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ stops: [(UInt32, Double, Double)], dim: Double = 1) -> GraphicsContext.Shading {
        .linearGradient(gradient(stops, dim: dim), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    private static func radial(_ x: Double, _ y: Double, _ r: Double, _ stops: [(UInt32, Double, Double)], dim: Double = 1) -> GraphicsContext.Shading {
        .radialGradient(gradient(stops, dim: dim), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    private static func blurred(_ c: GraphicsContext, _ sigma: Double) -> GraphicsContext {
        var copy = c
        copy.addFilter(.blur(radius: sigma))
        return copy
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    @MainActor static func render(_ g: NewtonCradleSwingGeo, scale: Double) -> Images {
        func image<V: View>(_ w: Double, _ h: Double, _ content: V) -> Image {
            let renderer = ImageRenderer(content: content.frame(width: w, height: h))
            renderer.scale = scale
            return renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "circle")
        }
        let side = 2 * g.R + 8
        return Images(
            back: image(g.w, g.h, Canvas { c, _ in drawBack(c, g) }),
            front: image(g.w, g.h, Canvas { c, _ in drawFront(c, g) }),
            ball: image(side, side, Canvas { c, _ in
                var c = c
                c.translateBy(x: side / 2, y: side / 2)
                drawBall(c, g.R)
            })
        )
    }

    /// A brushed-steel bar: a cylinder lit from the upper left, along x (horizontal) or y (vertical).
    private static func steelBar(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, vertical: Bool, dim: Double) {
        let stops: [(UInt32, Double, Double)] = [(0x3C4047, 1, 0), (0xDFE3E8, 1, 0.28), (0xA7ADB5, 1, 0.5), (0x6B7078, 1, 0.78), (0x2C2F34, 1, 1)]
        let shading = vertical ? linear(x, 0, x + w, 0, stops, dim: dim) : linear(0, y, 0, y + h, stops, dim: dim)
        c.fill(rr(x, y, w, h, min(w, h) / 2), with: shading)
        // Brushing: a few faint lines along the bar.
        for k in 0..<3 {
            let f = 0.3 + Double(k) * 0.18
            let p = vertical ? line(x + w * f, y + 2, x + w * f, y + h - 2) : line(x + 2, y + h * f, x + w - 2, y + h * f)
            c.stroke(p, with: .color(.white.opacity(0.06 + 0.04 * Double(k))), lineWidth: 0.5)
        }
    }

    private static func joint(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, dim: Double) {
        c.fill(circle(x, y, r), with: radial(x - r * 0.4, y - r * 0.45, r * 1.6, [(0xF4F6F8, 1, 0), (0x9AA0A8, 1, 0.45), (0x2A2D32, 1, 1)], dim: dim))
    }

    /// The wall, the desk, the base, and the back half of the frame.
    private static func drawBack(_ c: GraphicsContext, _ g: NewtonCradleSwingGeo) {
        let w = g.w, h = g.h, deskY = g.deskY
        let wall = Path(CGRect(x: 0, y: 0, width: w, height: deskY))
        // Wall: warm charcoal, lit from the upper left.
        c.fill(wall, with: linear(0, 0, 0, deskY, [(0x221B16, 1, 0), (0x18130F, 1, 0.55), (0x100D0B, 1, 1)]))
        c.fill(wall, with: radial(w * 0.06, h * 0.1, w * 1.05, [(0xFFC184, 0.2, 0), (0xFFA860, 0.07, 0.45), (0xFF9A50, 0, 1)]))

        // Walnut desk: darker far away, grain lines spreading out toward the viewer.
        let desk = Path(CGRect(x: 0, y: deskY, width: w, height: h - deskY))
        c.fill(desk, with: linear(0, deskY, 0, h, [(0x24160E, 1, 0), (0x3A2417, 1, 0.18), (0x4A2E1C, 1, 0.45), (0x3B2416, 1, 0.75), (0x1F130C, 1, 1)]))
        let lines = 72
        for k in 0..<lines {
            let kk = Double(k)
            let f = pow(kk / Double(lines), 1.55)
            let y0 = deskY + 3 + f * (h - deskY)
            let a = hash(kk + 1) * 6.28
            let b = hash(kk + 31) * 6.28
            var path = Path()
            var x = -10.0
            while x <= w + 10 {
                let y = y0 + sin(x * 0.012 + a) * (1 + f * 2.5) + sin(x * 0.047 + b) * (0.4 + f)
                if x == -10 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                x += 14
            }
            let col = k % 3 == 0 ? color(0xB07A4C, 0.08 + 0.05 * f) : color(0x120904, 0.22 + 0.18 * hash(kk + 3))
            c.stroke(path, with: .color(col), lineWidth: 0.5 + f * 1.4 * hash(kk + 7))
        }
        // The lamp's pool on the desk, and the shade where the desk meets the wall.
        c.fill(desk, with: radial(w * 0.22, deskY + 40, w * 0.9, [(0xFFC28A, 0.17, 0), (0xFFB070, 0.05, 0.5), (0xFFB070, 0, 1)]))
        c.fill(Path(CGRect(x: 0, y: deskY, width: w, height: 18)), with: linear(0, deskY, 0, deskY + 18, [(0, 0.4, 0), (0, 0, 1)]))

        // Base: a smoked-steel slab with a soft shadow on the desk.
        let left = 6.0
        let right = w - 6
        blurred(c, 9).fill(rr(left + 6, g.baseBottom - 6, right - left - 12, 16, 8), with: .color(.black.opacity(0.55)))
        blurred(c, 2).fill(rr(left + 2, g.baseBottom - 3, right - left - 4, 5, 3), with: .color(.black.opacity(0.6)))
        c.fill(rr(left, g.baseTop, right - left, g.baseBottom - g.baseTop, 5), with: linear(0, g.baseTop, 0, g.baseBottom, [(0x2B2D31, 1, 0), (0x3A3D43, 1, 0.4), (0x33363B, 1, 0.53), (0x1A1B1E, 1, 0.56), (0x121315, 1, 1)]))
        c.stroke(line(left + 4, g.baseFront + 0.5, right - 4, g.baseFront + 0.5), with: .color(.white.opacity(0.16)), lineWidth: 0.8)
        c.stroke(line(left + 5, g.baseTop + 0.5, right - 5, g.baseTop + 0.5), with: .color(.white.opacity(0.08)), lineWidth: 0.8)

        // Back posts and rail (dimmer: further from the lamp).
        for x in [g.backX, w - g.backX] {
            steelBar(c, x - 2.5, g.yB, 5, g.baseTop + 4 - g.yB, vertical: true, dim: 0.72)
        }
        steelBar(c, g.backX - 2.5, g.yB - 2.5, w - 2 * g.backX + 5, 5, vertical: false, dim: 0.72)
        joint(c, g.backX, g.yB, 3.6, dim: 0.72)
        joint(c, w - g.backX, g.yB, 3.6, dim: 0.72)

        // Film grain over everything (Skia's fractal noise in RN), as two batches of tiny specks.
        var light = Path()
        var dark = Path()
        for i in 0..<Int(w * h / 18) {
            let ii = Double(i)
            let r = CGRect(x: hash(ii * 1.37 + 0.5) * w, y: hash(ii * 2.11 + 7.3) * h, width: 1.1, height: 1.1)
            if hash(ii * 0.73 + 3.1) > 0.5 { light.addRect(r) } else { dark.addRect(r) }
        }
        c.fill(light, with: .color(.white.opacity(0.04)))
        c.fill(dark, with: .color(.black.opacity(0.05)))
        // Vignette.
        c.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: radial(w * 0.45, h * 0.45, hypot(w, h) * 0.62, [(0, 0, 0), (0, 0, 0.6), (0, 0.45, 1)]))
    }

    /// The front posts and rail, drawn over the strings.
    private static func drawFront(_ c: GraphicsContext, _ g: NewtonCradleSwingGeo) {
        let w = g.w
        for x in [g.frontX, w - g.frontX] {
            // Foot plate and post.
            blurred(c, 1.5).fill(Path(ellipseIn: CGRect(x: x - 7, y: g.baseFront - 4, width: 14, height: 6)), with: .color(.black.opacity(0.45)))
            steelBar(c, x - 3, g.yF, 6, g.baseFront - g.yF, vertical: true, dim: 1)
        }
        steelBar(c, g.frontX - 3, g.yF - 3, w - 2 * g.frontX + 6, 6, vertical: false, dim: 1)
        joint(c, g.frontX, g.yF, 4.4, dim: 1)
        joint(c, w - g.frontX, g.yF, 4.4, dim: 1)
    }

    /// One chrome ball centred on the origin.
    private static func drawBall(_ c: GraphicsContext, _ R: Double) {
        let disc = circle(0, 0, R)
        // The room reflected: lit ceiling and wall above, the dark horizon, the walnut desk below.
        c.fill(disc, with: linear(0, -R, 0, R, [(0xF3F5F7, 1, 0), (0xD2D7DD, 1, 0.2), (0x8D939B, 1, 0.38), (0x3E3D3C, 1, 0.47), (0x221C18, 1, 0.52)]))
        var inside = c
        inside.clip(to: disc)
        // The desk's reflection is bounded by a curved horizon (a sphere bends straight lines).
        inside.fill(Path(ellipseIn: CGRect(x: -R * 1.3, y: R * 0.02, width: R * 2.6, height: R * 2.1)),
                    with: linear(0, R * 0.02, 0, R, [(0x2A1B12, 1, 0), (0x6C4630, 1, 0.14), (0x8A5A3A, 1, 0.32), (0x3A2416, 1, 0.7), (0x150D08, 1, 1)]))
        // Rim: the reflection compresses and darkens toward the edge.
        inside.fill(disc, with: radial(-R * 0.08, -R * 0.1, R * 1.08, [(0, 0, 0), (0, 0, 0.68), (0x0A0806, 0.42, 0.9), (0x050403, 0.7, 1)]))
        // Warm bounce from the desk along the lower right rim.
        var arc = Path()
        arc.addArc(center: .zero, radius: R * 0.9, startAngle: .degrees(10), endAngle: .degrees(80), clockwise: false)
        blurred(inside, R * 0.06).stroke(arc, with: .color(color(0xFFBF8A, 0.32)), lineWidth: R * 0.13)
        // The window: a soft glow, then four sharp panes, tilted with the sphere's curvature.
        var win = c
        win.translateBy(x: -R * 0.36, y: -R * 0.44)
        win.rotate(by: .degrees(-26))
        blurred(win, R * 0.14).fill(rr(-R * 0.3, -R * 0.2, R * 0.6, R * 0.42, R * 0.12), with: .color(.white.opacity(0.42)))
        let pw = R * 0.2
        let ph = R * 0.14
        var panes = Path()
        for r in 0..<2 {
            for col in 0..<2 {
                panes.addPath(rr(-R * 0.22 + Double(col) * (pw + R * 0.04), -R * 0.16 + Double(r) * (ph + R * 0.04), pw, ph, R * 0.03))
            }
        }
        blurred(win, 0.45).fill(panes, with: .color(color(0xFFFDF8, 0.95)))
        // A pin-point of the lamp itself.
        blurred(c, 0.6).fill(circle(-R * 0.12, -R * 0.66, R * 0.05), with: .color(.white.opacity(0.85)))
        // A hairline where the ball meets the dark.
        c.stroke(circle(0, 0, R - 0.35), with: .color(.black.opacity(0.55)), lineWidth: 0.7)
    }

    private static let eyelet = Gradient(stops: [
        .init(color: color(0x5A5F66), location: 0),
        .init(color: color(0xEEF0F3), location: 0.4),
        .init(color: color(0x7D838B), location: 1),
    ])

    /// Draws one frame. `angles` are the five balls' angles from the vertical (radians); `finger`
    /// (0–1) shows the scripted fingertip on ball 1.
    static func draw(_ c: GraphicsContext, _ g: NewtonCradleSwingGeo, _ art: Images, _ angles: [Double], _ finger: Double) {
        c.draw(art.back, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
        let R = g.R
        let n = NewtonCradleSwingMotion.balls
        let bx = (0..<n).map { g.xs[$0] + g.L * sin(angles[$0]) }
        let by = (0..<n).map { g.pivotY + g.L * cos(angles[$0]) }

        // Contact shadows on the base's top face: softer and fainter the higher the ball.
        for i in 0..<n {
            let lift = max(0, g.baseTop - (by[i] + R))
            let sigma = 2.2 + lift * 0.09
            let alpha = 0.5 * min(1, 34 / (lift + 4))
            blurred(c, sigma).fill(Path(ellipseIn: CGRect(x: bx[i] - R * 1.02 + lift * 0.25, y: g.baseTop + 4, width: R * 2.04, height: 8)), with: .color(.black.opacity(alpha)))
        }

        // Strings: two per ball, to the back rail (behind) and the front rail (in front).
        var attach: [CGPoint] = []
        for i in 0..<n {
            let ux = g.xs[i] - bx[i]
            let uy = g.pivotY - by[i]
            let ul = hypot(ux, uy)
            attach.append(CGPoint(x: bx[i] + ux / ul * (R + 2), y: by[i] + uy / ul * (R + 2)))
        }
        var backStrings = Path()
        for i in 0..<n {
            backStrings.move(to: attach[i])
            backStrings.addLine(to: CGPoint(x: g.cx + (g.xs[i] - g.cx) * 0.93, y: g.yB))
        }
        c.stroke(backStrings, with: .color(color(0xB9B2A6, 0.42)), lineWidth: 0.8)

        // Balls, with a dark mirrored neighbour where another ball is close, and the string's eyelet.
        let ball = c.resolve(art.ball)
        let side = 2 * R + 8
        for i in 0..<n {
            var b = c
            b.translateBy(x: bx[i], y: by[i])
            b.draw(ball, in: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
            var near = b
            near.clip(to: circle(0, 0, R * 0.97))
            for j in [i - 1, i + 1] where j >= 0 && j < n {
                let dx = bx[j] - bx[i]
                let dy = by[j] - by[i]
                let d = hypot(dx, dy)
                let k = min(max((3.4 * R - d) / (1.4 * R), 0), 1)
                if k <= 0.01 { continue }
                var m = near
                m.rotate(by: .radians(atan2(dy, dx)))
                m.fill(Path(ellipseIn: CGRect(x: R * 0.68, y: -R * 0.34, width: R * 0.3, height: R * 0.68)), with: .color(color(0x14100D, 0.72 * k)))
                m.fill(circle(R * 0.8, -R * 0.12, R * 0.045 + 0.3), with: .color(.white.opacity(0.55 * k)))
            }
            // Eyelet, along the string: rotate so the local −y axis points at the pivot.
            b.rotate(by: .radians(atan2(g.xs[i] - bx[i], by[i] - g.pivotY)))
            b.fill(rr(-R * 0.11, -R - R * 0.2, R * 0.22, R * 0.26, R * 0.06),
                   with: .linearGradient(eyelet, startPoint: CGPoint(x: -2.5, y: 0), endPoint: CGPoint(x: 2.5, y: 0)))
        }

        var frontStrings = Path()
        for i in 0..<n {
            frontStrings.move(to: attach[i])
            frontStrings.addLine(to: CGPoint(x: g.xs[i], y: g.yF))
        }
        c.stroke(frontStrings, with: .color(color(0xD9D2C6, 0.58)), lineWidth: 0.8)
        c.draw(art.front, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))

        // The scripted fingertip on ball 1.
        if finger > 0.001 {
            let r = R * 1.1 * (1.12 - 0.12 * finger)
            let tip = circle(bx[0] - 2, by[0], r)
            c.fill(tip, with: .color(color(0xFFF1E2, 0.16 * finger)))
            c.stroke(tip, with: .color(color(0xFFF1E2, 0.5 * finger)), lineWidth: 1.4)
        }
    }
}
