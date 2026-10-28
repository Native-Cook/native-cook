import SwiftUI

/*
 Sauce Squeeze Cart — everything drawn in code: the squeeze bottle (a pillowy body with three bulges
 per side, a two-tier cap, a label with a tomato), the sauce stream, the red flood and the stickers.
 The bottle is drawn in its own coordinates: origin at the body's centre, cap pointing down (+y).
*/
enum SauceSqueezeCartArt {
    typealias M = SauceSqueezeCartMotion

    static let halfW = 86.0
    static let halfH = 114.0
    /// The nozzle's tip below the body's centre.
    static let nozzleY = halfH + 38

    static let pink: UInt32 = 0xF792DC
    static let sauce: UInt32 = 0xE90002
    static let plum: UInt32 = 0x3C063E
    static let mutedPlum: UInt32 = 0xA0528E

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: a)
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    /// A closed path through points, smoothed with quadratic curves through their midpoints.
    static func smoothPath(_ p: [CGPoint]) -> Path {
        var path = Path()
        let n = p.count
        guard n > 2 else { return path }
        func mid(_ a: CGPoint, _ b: CGPoint) -> CGPoint { CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2) }
        path.move(to: mid(p[n - 1], p[0]))
        for i in 0..<n {
            path.addQuadCurve(to: mid(p[i], p[(i + 1) % n]), control: p[i])
        }
        path.closeSubpath()
        return path
    }

    // MARK: Bottle

    /// Width and height scale of a squash q (anchored at the cap), and the waist's pinch.
    static func squash(_ q: Double) -> (sx: Double, sy: Double, pinch: Double) {
        (1 + 0.18 * q, 1 - 0.14 * q, 0.12 * max(0, q))
    }

    /// The body's outline for a squash q.
    static func bodyPath(q: Double) -> Path {
        let a = halfW, b = halfH
        let (sx, sy, pinch) = squash(q)
        let n = 120
        var pts: [CGPoint] = []
        pts.reserveCapacity(n)
        let e = 2 / 4.2
        for i in 0..<n {
            let th = 2 * Double.pi * Double(i) / Double(n)
            let c = cos(th), s = sin(th)
            var x = a * (c < 0 ? -1 : 1) * pow(abs(c), e)
            var y = b * (s < 0 ? -1 : 1) * pow(abs(s), e)
            // Three bulges down each side.
            let v = (y + b) / (2 * b)
            let side = M.smooth(0.55, 0.92, abs(x) / a)
            let env = M.smooth(0.06, 0.2, v) * (1 - M.smooth(0.8, 0.95, v))
            let sw = sin(3 * Double.pi * v)
            x += (x < 0 ? -1 : 1) * (9 * sw * sw - 3.5) * side * env
            // A pillowy top, a shoulder narrowing into the cap.
            if y < 0 { y -= 7 * max(0, 1 - (x / a) * (x / a)) * M.smooth(0.6, 0.95, -y / b) }
            if y > 0 { x *= 1 - 0.08 * M.smooth(0.7, 1, y / b) }
            // Squeeze: wider and shorter, the waist pinched by the fingers, the top swelling.
            let waist = 1 - pinch * exp(-pow(y / (0.42 * b), 2))
            let swell = 1 + 0.4 * pinch * exp(-pow((y + 0.7 * b) / (0.3 * b), 2))
            pts.append(CGPoint(x: x * sx * waist * swell, y: b + (y - b) * sy))
        }
        return smoothPath(pts)
    }

    static func roundRect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .continuous)
    }

    /// The whole bottle, in its own coordinates (the caller translates and rotates the context).
    static func bottle(_ c: inout GraphicsContext, _ f: SauceSqueezeCartFrame) {
        let b = halfH
        let (sx, sy, pinch) = squash(f.q)
        let body = bodyPath(q: f.q)

        // Soft shadow on the page.
        var sh = c
        sh.addFilter(.blur(radius: 18))
        sh.opacity = 0.22
        sh.fill(body.offsetBy(dx: 8, dy: 22), with: .color(color(0x9A1E6E)))

        // Cap: a white collar and a smaller tip, tucked under the body.
        let cw = 1 + 0.06 * f.q
        let collar = roundRect(-56 * cw, b - 10, 112 * cw, 30, 10)
        let tip = roundRect(-38 * cw, b + 16, 76 * cw, 22, 8)
        let capShade = GraphicsContext.Shading.linearGradient(
            Gradient(stops: [.init(color: color(0xFFFFFF), location: 0),
                             .init(color: color(0xF3EEF1), location: 0.5),
                             .init(color: color(0xCFC4CB), location: 1)]),
            startPoint: CGPoint(x: -56, y: 0), endPoint: CGPoint(x: 56, y: 0))
        c.fill(tip, with: capShade)
        c.fill(roundRect(-38 * cw, b + 33, 76 * cw, 5, 2.5), with: .color(color(0xBDB1B9)))
        c.fill(collar, with: capShade)
        c.fill(roundRect(-56 * cw, b + 14, 112 * cw, 6, 3), with: .color(color(0xD8CED5)))
        c.fill(Path(ellipseIn: CGRect(x: -6, y: b + 35.5, width: 12, height: 3)), with: .color(color(0x9E2030)))

        // Body: a red gradient across, darker towards the cap.
        c.fill(body, with: .linearGradient(
            Gradient(stops: [.init(color: color(0xF3302E), location: 0),
                             .init(color: color(0xD80C12), location: 0.45),
                             .init(color: color(0xA30008), location: 1)]),
            startPoint: CGPoint(x: -halfW, y: 0), endPoint: CGPoint(x: halfW, y: 0)))
        var inside = c
        inside.clip(to: body)
        inside.fill(body, with: .linearGradient(
            Gradient(colors: [color(0x7A0006, 0), color(0x7A0006, 0.32)]),
            startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: b)))
        // Inner shading: dark along the right and bottom, light along the top left.
        var rim = inside
        rim.addFilter(.blur(radius: 7))
        rim.stroke(body.offsetBy(dx: -9, dy: -7), with: .color(color(0x6E0005, 0.55)), lineWidth: 12)
        var glow = inside
        glow.addFilter(.blur(radius: 5))
        glow.stroke(body.offsetBy(dx: 6, dy: 7), with: .color(color(0xFF8A7A, 0.35)), lineWidth: 6)

        // Label: scaled with the waist so it squeezes with the body.
        var lab = inside
        lab.translateBy(x: 0, y: b)
        lab.scaleBy(x: sx * (1 - pinch * 0.9), y: sy)
        lab.translateBy(x: 0, y: -b)
        label(&lab)

        // Specular streaks that slide a little as the bottle turns.
        let hx = -halfW * 0.66 + f.spin * 0.25
        var spec = inside
        spec.translateBy(x: 0, y: b)
        spec.scaleBy(x: sx, y: sy)
        spec.translateBy(x: 0, y: -b)
        var s1 = spec
        s1.addFilter(.blur(radius: 3))
        s1.fill(roundRect(hx - 6, -b * 0.62, 12, b * 1.04, 6), with: .color(.white.opacity(0.5)))
        s1.fill(roundRect(hx + 13, -b * 0.5, 4, b * 0.55, 2), with: .color(.white.opacity(0.28)))
        var s2 = spec
        s2.addFilter(.blur(radius: 6))
        s2.fill(Path(ellipseIn: CGRect(x: -halfW * 0.2 - 36 + f.spin * 0.3, y: -b - 2, width: 72, height: 18)),
                with: .color(.white.opacity(0.38)))
    }

    static func label(_ c: inout GraphicsContext) {
        c.fill(roundRect(-52, -74, 104, 140, 18), with: .color(color(0xE8553A)))
        c.fill(roundRect(-42, -64, 84, 120, 12), with: .color(color(0xF6B4B0, 0.9)))
        tomato(&c, 0, -14, 1)
        c.draw(Text("PLUMPO").font(.system(size: 10, weight: .heavy)).tracking(1.4)
                .foregroundColor(color(0xC22018)),
               at: CGPoint(x: 0, y: 36), anchor: .center)
    }

    static func tomato(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ k: Double) {
        var t = c
        t.translateBy(x: x, y: y)
        t.scaleBy(x: k, y: k)
        var lobes = Path()
        lobes.addEllipse(in: CGRect(x: -25, y: -16, width: 30, height: 34))
        lobes.addEllipse(in: CGRect(x: -5, y: -16, width: 30, height: 34))
        lobes.addEllipse(in: CGRect(x: -16, y: -18, width: 32, height: 37))
        t.fill(lobes, with: .radialGradient(
            Gradient(colors: [color(0xF2473A), color(0xE3201B), color(0xB5100E)]),
            center: CGPoint(x: -6, y: -6), startRadius: 0, endRadius: 30))
        t.stroke(Path { p in p.move(to: CGPoint(x: -5, y: -12)); p.addQuadCurve(to: CGPoint(x: -6, y: 14), control: CGPoint(x: -10, y: 0)) },
                 with: .color(color(0xB5100E, 0.6)), lineWidth: 1.4)
        t.stroke(Path { p in p.move(to: CGPoint(x: 6, y: -12)); p.addQuadCurve(to: CGPoint(x: 7, y: 14), control: CGPoint(x: 11, y: 0)) },
                 with: .color(color(0xB5100E, 0.6)), lineWidth: 1.4)
        t.fill(Path(ellipseIn: CGRect(x: -15, y: -11, width: 9, height: 6)), with: .color(.white.opacity(0.45)))
        // Calyx: a five-point green star with a stem.
        var star = Path()
        for i in 0..<10 {
            let a = -Double.pi / 2 + Double(i) * Double.pi / 5
            let r = i % 2 == 0 ? 12.0 : 4.0
            let p = CGPoint(x: r * cos(a), y: -16 + r * sin(a) * 0.55)
            if i == 0 { star.move(to: p) } else { star.addLine(to: p) }
        }
        star.closeSubpath()
        t.fill(star, with: .color(color(0x2E8B3A)))
        t.fill(roundRect(-1.5, -24, 3, 8, 1.5), with: .color(color(0x2E8B3A)))
    }

    /// The bead of sauce swelling at the nozzle while squeezed.
    static func bead(_ c: inout GraphicsContext, _ s: Double) {
        let r = 7 * s
        guard r > 0.3 else { return }
        let cy = nozzleY + r * 0.7
        c.fill(circle(0, cy, r), with: .color(color(sauce)))
        c.fill(circle(-r * 0.35, cy - r * 0.3, r * 0.28), with: .color(.white.opacity(0.5)))
    }

    // MARK: Stream, drops and the red

    /// The ribbon of sauce from the nozzle (screen coordinates).
    static func stream(_ c: inout GraphicsContext, root: CGPoint, tau: Double, h: Double) {
        let head = M.streamHead(tau)
        let width = M.streamWidth(tau)
        guard head > 0.001, width > 0.5 else { return }
        let sway = M.streamSway(tau)
        let len = (h + 60 - root.y) * head
        let n = 48
        var center: [CGPoint] = []
        for k in 0...n {
            let u = Double(k) / Double(n)
            let dy = len * u
            let x = root.x + sway * pow(u, 0.8) * sin(2 * Double.pi * (dy / 300 - 1.6 * tau))
            center.append(CGPoint(x: x, y: root.y + dy))
        }
        func halfWidth(_ u: Double) -> Double { width / 2 * (0.55 + 0.45 * M.smooth(0, 0.25, u)) }
        var left: [CGPoint] = [], right: [CGPoint] = []
        for k in 0...n {
            let a = center[max(0, k - 1)], b = center[min(n, k + 1)]
            var nx = -(b.y - a.y), ny = b.x - a.x
            let l = max(0.0001, (nx * nx + ny * ny).squareRoot())
            nx /= l; ny /= l
            let hw = halfWidth(Double(k) / Double(n))
            left.append(CGPoint(x: center[k].x + nx * hw, y: center[k].y + ny * hw))
            right.append(CGPoint(x: center[k].x - nx * hw, y: center[k].y - ny * hw))
        }
        var p = Path()
        p.move(to: left[0])
        for pt in left.dropFirst() { p.addLine(to: pt) }
        // A round head.
        let end = center[n], prev = center[n - 1]
        let ang = atan2(end.y - prev.y, end.x - prev.x)
        p.addArc(center: end, radius: halfWidth(1), startAngle: .radians(ang + .pi / 2),
                 endAngle: .radians(ang - .pi / 2), clockwise: true)
        for pt in right.reversed() { p.addLine(to: pt) }
        p.closeSubpath()
        c.fill(p, with: .color(color(sauce)))

        // Gloss along the left of the ribbon.
        var gloss = Path()
        let k0 = 3, k1 = Int(Double(n) * 0.9)
        for k in k0...k1 {
            let pt = CGPoint(x: left[k].x * 0.62 + center[k].x * 0.38, y: left[k].y * 0.62 + center[k].y * 0.38)
            if k == k0 { gloss.move(to: pt) } else { gloss.addLine(to: pt) }
        }
        var g = c
        g.addFilter(.blur(radius: 1.5))
        g.stroke(gloss, with: .color(color(0xFF6A58, 0.6)),
                 style: StrokeStyle(lineWidth: max(2, width * 0.1), lineCap: .round, lineJoin: .round))
    }

    /// Droplets thrown from the nozzle as the stream starts.
    static func drops(_ c: inout GraphicsContext, root: CGPoint, tau: Double) {
        for i in 0..<6 {
            let di = Double(i)
            let t = tau - 0.02 * di
            guard t > 0, t < 0.9 else { continue }
            let vx = (M.hash(di + 1) - 0.5) * 520
            let vy = -160 + M.hash(di + 7) * 300
            let x = root.x + vx * t
            let y = root.y + vy * t + 0.5 * 1600 * t * t
            let r = (4 + 5 * M.hash(di + 13)) * (1 - M.smooth(0.6, 0.9, t))
            c.fill(circle(x, y, r), with: .color(color(sauce)))
            c.fill(circle(x - r * 0.3, y - r * 0.3, r * 0.3), with: .color(.white.opacity(0.45)))
        }
    }

    /// The red that floods up from the bottom and later retracts upwards, dripping.
    static func red(_ c: inout GraphicsContext, tau: Double, w: Double, h: Double) {
        let flooding = tau < M.coveredAt + 0.1
        let draining = M.drain(tau) > 0
        guard tau > 0.3, M.drain(tau) < 1 else { return }
        var top: [CGPoint] = [], bottom: [CGPoint] = []
        var x = -20.0
        while x <= w + 20 {
            top.append(CGPoint(x: x, y: flooding ? M.redTop(x, tau: tau, h: h) : -400))
            bottom.append(CGPoint(x: x, y: draining ? M.redBottom(x, tau: tau, w: w, h: h) : h + 400))
            x += 3
        }
        var p = Path()
        p.move(to: top[0])
        for pt in top.dropFirst() { p.addLine(to: pt) }
        for pt in bottom.reversed() { p.addLine(to: pt) }
        p.closeSubpath()
        c.fill(p, with: .color(color(sauce)))

        // A glossy line just inside the moving edge.
        var line = Path()
        let edge = flooding ? top : bottom
        let dy = flooding ? 7.0 : -7.0
        for (i, pt) in edge.enumerated() {
            let q = CGPoint(x: pt.x, y: pt.y + dy)
            if i == 0 { line.move(to: q) } else { line.addLine(to: q) }
        }
        guard flooding || draining else { return }
        var g = c
        g.clip(to: p)
        g.addFilter(.blur(radius: 2))
        g.stroke(line, with: .color(color(0xFF5444, 0.55)), lineWidth: 3)
    }

    // MARK: Stickers

    struct Sticker {
        var x: Double
        var y: Double
        var rot: Double
    }

    /// Positions relative to the middle of the red (the scale is 1.15).
    static let stickers: [Sticker] = [
        .init(x: -124, y: -118, rot: 0),
        .init(x: 112, y: -92, rot: 12),
        .init(x: -12, y: -34, rot: -10),
        .init(x: -74, y: 64, rot: 16),
        .init(x: 104, y: 52, rot: -68),
        .init(x: 14, y: 150, rot: -45),
    ]

    static func stickers(_ c: inout GraphicsContext, tau: Double, cx: Double, cy: Double, reduced: Bool) {
        for (i, s) in stickers.enumerated() {
            var k = M.sticker(i, tau: tau)
            var alpha = 1.0
            if reduced {
                alpha = M.redFade(tau)
                k = alpha > 0.001 ? 1 : 0
            }
            guard k > 0.01 else { continue }
            let float = reduced ? 0 : 3 * sin(2 * Double.pi * (tau / 1.6) + Double(i) * 1.7)
            var l = c
            l.opacity = alpha
            l.translateBy(x: cx + s.x, y: cy + s.y + float)
            l.rotate(by: .degrees(s.rot + (reduced ? 0 : 30 * (1 - min(1, k)))))
            l.scaleBy(x: 1.15 * k, y: 1.15 * k)
            l.addFilter(.shadow(color: color(0x5A0010, 0.25), radius: 10, x: 0, y: 6))
            l.drawLayer { d in
                sticker(&d, i)
            }
        }
    }

    static func sticker(_ c: inout GraphicsContext, _ i: Int) {
        let plumC = color(plum)
        switch i {
        case 0:
            var star = Path()
            for j in 0..<16 {
                let a = -Double.pi / 2 + Double(j) * Double.pi / 8
                let r = j % 2 == 0 ? 22.0 : 8.5
                let p = CGPoint(x: r * cos(a), y: r * sin(a))
                if j == 0 { star.move(to: p) } else { star.addLine(to: p) }
            }
            star.closeSubpath()
            c.fill(star, with: .color(.white))
        case 1:
            c.fill(circle(0, 0, 38), with: .color(.white))
            c.fill(circle(0, 0, 34), with: .color(color(0xF8D3EF)))
            cart(&c, 0, 0, 1.15, plumC)
        case 2:
            let text = c.resolve(Text("IN YOUR CART").font(.system(size: 18, weight: .heavy)).foregroundColor(plumC))
            let size = text.measure(in: CGSize(width: 400, height: 60))
            let w = size.width + 32, h = 42.0
            c.fill(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 14), with: .color(.white))
            c.fill(roundRect(-w / 2, -h / 2, w, h, 10), with: .linearGradient(
                Gradient(colors: [color(0x9EC2FA), color(0xD3E1FF)]),
                startPoint: CGPoint(x: -w / 2, y: 0), endPoint: CGPoint(x: w / 2, y: 0)))
            c.draw(text, at: .zero, anchor: .center)
        case 3:
            c.fill(circle(0, 0, 44), with: .color(.white))
            c.fill(circle(0, 0, 40), with: .radialGradient(
                Gradient(stops: [.init(color: color(0xFBF79A), location: 0),
                                 .init(color: color(0xF0E54A), location: 0.5),
                                 .init(color: color(0xD4A92A), location: 1)]),
                center: CGPoint(x: -10, y: -12), startRadius: 0, endRadius: 52))
            c.fill(roundRect(-15.5, -14, 5, 10, 2.5), with: .color(plumC))
            c.fill(roundRect(9, -15, 5, 10, 2.5), with: .color(plumC))
            var smile = Path()
            smile.addArc(center: CGPoint(x: 0, y: -4), radius: 22, startAngle: .degrees(25),
                         endAngle: .degrees(155), clockwise: false)
            c.stroke(smile, with: .color(plumC), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
        case 4:
            let text = c.resolve(Text("+1 ADDED").font(.system(size: 15, weight: .heavy)).foregroundColor(plumC))
            let size = text.measure(in: CGSize(width: 400, height: 60))
            let w = size.width + 40, h = 40.0
            c.fill(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 24), with: .color(.white))
            c.fill(roundRect(-w / 2, -h / 2, w, h, 20), with: .color(color(0x22C32C)))
            c.fill(circle(-w / 2 + 12, 0, 3.5), with: .color(plumC))
            c.draw(text, at: CGPoint(x: 5, y: 0), anchor: .center)
        default:
            var arrow = Path()
            arrow.move(to: CGPoint(x: -24, y: 0))
            arrow.addLine(to: CGPoint(x: 20, y: 0))
            arrow.move(to: CGPoint(x: 6, y: -13))
            arrow.addLine(to: CGPoint(x: 21, y: 0))
            arrow.addLine(to: CGPoint(x: 6, y: 13))
            c.stroke(arrow, with: .color(.white), style: StrokeStyle(lineWidth: 17, lineCap: .round, lineJoin: .round))
            c.stroke(arrow, with: .color(plumC), style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
        }
    }

    /// A line-drawn shopping cart centred at (x, y).
    static func cart(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ k: Double, _ ink: Color) {
        var p = Path()
        p.move(to: CGPoint(x: x - 13 * k, y: y - 9 * k))
        p.addLine(to: CGPoint(x: x - 9 * k, y: y - 9 * k))
        p.addLine(to: CGPoint(x: x - 5 * k, y: y + 5 * k))
        p.addLine(to: CGPoint(x: x + 9 * k, y: y + 5 * k))
        p.addLine(to: CGPoint(x: x + 12 * k, y: y - 5 * k))
        p.addLine(to: CGPoint(x: x - 7.6 * k, y: y - 5 * k))
        c.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 2.2 * k, lineCap: .round, lineJoin: .round))
        c.fill(circle(x - 3 * k, y + 10 * k, 2 * k), with: .color(ink))
        c.fill(circle(x + 7 * k, y + 10 * k, 2 * k), with: .color(ink))
    }
}
