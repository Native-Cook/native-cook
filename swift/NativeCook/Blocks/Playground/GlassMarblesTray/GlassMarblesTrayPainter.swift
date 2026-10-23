import SwiftUI

/// Draws the table, the tray and its marbles as the React Native picture does. The perspective is
/// split into three layers: what lies on screen under the tray (the table, the tray's shadow, its
/// sides), the floor plane (the baked tray top and the marbles' shadows, drawn flat and projected by
/// the view), and what stands up off the floor (the marbles and their glints).
enum GlassMarblesTrayPainter {
    typealias RGB = (Double, Double, Double)
    typealias E = GlassMarblesTrayEngine
    typealias T = GlassMarblesTrayTuning

    static let ink = Color(red: 0xF4 / 255, green: 0xE9 / 255, blue: 0xDC / 255)
    static let muted = Color(red: 0xA8 / 255, green: 0x92 / 255, blue: 0x7E / 255)
    static let table: [Color] = [
        Color(red: 0x3B / 255, green: 0x2C / 255, blue: 0x22 / 255),
        Color(red: 0x1B / 255, green: 0x13 / 255, blue: 0x0E / 255),
        Color(red: 0x0D / 255, green: 0x09 / 255, blue: 0x07 / 255),
    ]
    /// The floor layer reaches this far (pt) beyond the tray, so shadows near the rim are not cut.
    static let floorPad = 30.0

    static func rgba(_ c: RGB, _ a: Double) -> Color {
        Color(.sRGB, red: c.0 / 255, green: c.1 / 255, blue: c.2 / 255, opacity: max(0, min(1, a)))
    }

    static func mix(_ a: RGB, _ b: RGB, _ t: Double) -> RGB {
        (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t)
    }

    private static func soft(_ context: GraphicsContext, _ sigma: Double) -> GraphicsContext {
        var c = context
        c.addFilter(.blur(radius: sigma))
        return c
    }

    // MARK: Under the tray

    /// The table, the tray's shadow on it and the tray's sides, all on screen.
    static func drawUnder(in context: inout GraphicsContext, size: CGSize, engine: E, scale: Double) {
        let width = size.width
        let height = size.height
        // The table under the tray: warm dark wood lit from above.
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            Gradient(stops: [
                .init(color: table[0], location: 0),
                .init(color: table[1], location: 0.55),
                .init(color: table[2], location: 1),
            ]),
            center: CGPoint(x: width * 0.42, y: engine.cy - 40), startRadius: 0, endRadius: max(width, height) * 0.75
        ))
        guard let w = engine.world, let pose = engine.pose else { return }
        let halfW = engine.trayW / 2
        let halfH = engine.trayH / 2

        // The tray's shadow on the table, pushed away from the light and spread by the tilt. Its
        // outline is projected point by point and blurred on screen: React Native's blur ignores the
        // canvas matrix, density included, so its sigma is in pixels.
        let tiltSize = hypot(w.tiltX, w.tiltY)
        let m = pose.floorMatrix()
        var shadow = Path()
        let r = E.outerCorner
        let x0 = -halfW + 10
        let y0 = -halfH + 22
        let corners: [(Double, Double, Double)] = [
            (x0 + engine.trayW - r, y0 + r, -Double.pi / 2),
            (x0 + engine.trayW - r, y0 + engine.trayH - r, 0),
            (x0 + r, y0 + engine.trayH - r, Double.pi / 2),
            (x0 + r, y0 + r, Double.pi),
        ]
        var first = true
        for (cx, cy, a0) in corners {
            for k in 0...8 {
                let a = a0 + (Double.pi / 2) * Double(k) / 8
                let u = cx + cos(a) * r
                let v = cy + sin(a) * r
                let q = m[6] * u + m[7] * v + m[8]
                let p = CGPoint(x: (m[0] * u + m[1] * v + m[2]) / q, y: (m[3] * u + m[4] * v + m[5]) / q)
                if first { shadow.move(to: p); first = false } else { shadow.addLine(to: p) }
            }
        }
        shadow.closeSubpath()
        soft(context, (16 + tiltSize * 40) / scale).fill(shadow, with: .color(rgba((4, 2, 1), 0.6)))

        drawSides(context, pose, rimLoop(halfW, halfH))
    }

    /// The tray's outer edge, as a loop of points (pt, tray frame) for its sides.
    private static func rimLoop(_ halfW: Double, _ halfH: Double) -> [(Double, Double)] {
        var pts: [(Double, Double)] = []
        let r = E.outerCorner
        let corners: [(Double, Double, Double)] = [
            (halfW - r, -halfH + r, -Double.pi / 2),
            (halfW - r, halfH - r, 0),
            (-halfW + r, halfH - r, Double.pi / 2),
            (-halfW + r, -halfH + r, Double.pi),
        ]
        for (x, y, a0) in corners {
            for k in 0...6 {
                let a = a0 + (Double.pi / 2) * (Double(k) / 6)
                pts.append((x + cos(a) * r, y + sin(a) * r))
            }
        }
        return pts
    }

    /// The tray's sides: the faces that turn towards the eye as it tilts, shaded by the light.
    private static func drawSides(_ context: GraphicsContext, _ p: GlassMarblesTrayPose, _ rim: [(Double, Double)]) {
        let n = rim.count
        let light = GlassMarblesTrayPose.light
        for i in 0..<n {
            let j = (i + 1) % n
            let (ax, ay) = rim[i]
            let (bx, by) = rim[j]
            // The face's outward normal, turned with the tray (a direction: turn it about the origin).
            var l = hypot(by - ay, bx - ax)
            if l == 0 { l = 1 }
            let normal = p.turn((by - ay) / l, -(bx - ax) / l, 0)
            // Seen when it faces the eye, which sits focal pt above the tray's centre.
            let mid = p.turn((ax + bx) / 2, (ay + by) / 2, -E.trayDepth / 2)
            if normal.x * -mid.x + normal.y * -mid.y + normal.z * (GlassMarblesTrayPose.focal - mid.z) <= 0 { continue }
            let a0 = p.project(ax, ay, 0)
            let b0 = p.project(bx, by, 0)
            let b1 = p.project(bx, by, -E.trayDepth)
            let a1 = p.project(ax, ay, -E.trayDepth)
            var face = Path()
            face.move(to: CGPoint(x: a0.x, y: a0.y))
            face.addLine(to: CGPoint(x: b0.x, y: b0.y))
            face.addLine(to: CGPoint(x: b1.x, y: b1.y))
            face.addLine(to: CGPoint(x: a1.x, y: a1.y))
            face.closeSubpath()
            let lit = max(0, normal.x * light.0 + normal.y * light.1 + normal.z * light.2)
            let shade = 0.4 + 0.6 * lit
            context.fill(face, with: .color(rgba((96 * shade + 18, 56 * shade + 10, 30 * shade + 6), 1)))
        }
    }

    /// The floor layer's projection: tray-frame homography after the layer's own offset.
    static func floorProjection(engine: E) -> ProjectionTransform {
        guard let pose = engine.pose else { return ProjectionTransform() }
        let m = pose.floorMatrix()
        let ox = engine.trayW / 2 + floorPad
        let oy = engine.trayH / 2 + floorPad
        var t = ProjectionTransform()
        t.m11 = m[0]
        t.m21 = m[1]
        t.m31 = m[2] - m[0] * ox - m[1] * oy
        t.m12 = m[3]
        t.m22 = m[4]
        t.m32 = m[5] - m[3] * ox - m[4] * oy
        t.m13 = m[6]
        t.m23 = m[7]
        t.m33 = m[8] - m[6] * ox - m[7] * oy
        return t
    }

    // MARK: The floor plane

    /// The marbles' shadows and caustics, in the floor layer (tray frame shifted by its pad).
    static func drawFloor(in context: inout GraphicsContext, engine: E) {
        guard let w = engine.world, let pose = engine.pose else { return }
        context.translateBy(x: engine.trayW / 2 + floorPad, y: engine.trayH / 2 + floorPad)
        let light = pose.trayLight()
        for i in 0..<w.n { drawShadow(context, w, i, E.marbles[i], light) }
    }

    /// A marble's shadow and the coloured light its glass focuses into it, on the floor.
    private static func drawShadow(_ context: GraphicsContext, _ w: GlassMarblesTrayWorld, _ i: Int,
                                   _ set: GlassMarblesTrayMarble, _ light: (x: Double, y: Double, z: Double)) {
        let r = w.r[i]
        let lift = r + w.z[i]
        let ox = (-light.x / light.z) * lift
        let oy = (-light.y / light.z) * lift
        let x = w.x[i]
        let y = w.y[i]
        let blur = 2.5 + w.z[i] * 0.08
        let fade = 1 / (1 + w.z[i] * 0.02)
        // Contact shade right under it.
        let cr = r * 0.62
        let cc = CGPoint(x: x + ox * 0.25, y: y + oy * 0.25)
        soft(context, 2 + w.z[i] * 0.1).fill(
            Path(ellipseIn: CGRect(x: cc.x - cr, y: cc.y - cr, width: cr * 2, height: cr * 2)),
            with: .color(rgba((40, 18, 4), 0.4 * fade))
        )
        // The cast shadow, tinted by the glass.
        let tint = mix((46, 22, 6), set.glass, 0.35)
        var cast = soft(context, blur)
        cast.translateBy(x: x + ox * 0.9, y: y + oy * 0.9)
        cast.rotate(by: .radians(atan2(oy, ox)))
        cast.fill(Path(ellipseIn: CGRect(x: -r * 1.25, y: -r * 0.95, width: r * 2.5, height: r * 1.9)),
                  with: .color(rgba(tint, 0.58 * fade)))
        // The caustic: the glass gathers light into a bright, coloured spot inside its shadow.
        var caustic = context
        caustic.blendMode = .plusLighter
        let c = CGPoint(x: x + ox * 1.05, y: y + oy * 1.05)
        let rr = r * 0.62
        caustic.fill(
            Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: rr * 2, height: rr * 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba(mix(set.glass, (255, 255, 255), 0.5), 0.8 * fade), location: 0),
                    .init(color: rgba(set.glass, 0.35 * fade), location: 0.4),
                    .init(color: rgba(set.glass, 0), location: 1),
                ]),
                center: c, startRadius: 0, endRadius: rr
            )
        )
    }

    // MARK: Above the floor

    /// The marbles, lowest first so a hopping one passes over the rest, their glints, and the readout.
    static func drawOver(in context: inout GraphicsContext, size: CGSize, engine: E) {
        guard let w = engine.world, let pose = engine.pose else { return }
        var order: [Int] = []
        for i in 0..<w.n {
            // Insertion by height: the list is short and nearly always already in order.
            var at = order.count
            while at > 0 && w.z[order[at - 1]] > w.z[i] { at -= 1 }
            order.insert(i, at: at)
        }
        for i in order {
            let p = pose.project(w.x[i], w.y[i], w.r[i] + w.z[i])
            drawMarble(context, p.x, p.y, w.r[i] * p.s, E.marbles[i], w.rot, i * 9)
        }
        for f in 0..<T.flashes where w.flashAge[f] < T.flashLife {
            let p = pose.project(w.flashX[f], w.flashY[f], 12)
            drawFlash(context, p.x, p.y, w.flashAge[f] / T.flashLife, w.flashPower[f])
        }

        // The tilt readout, top right.
        let degrees = (hypot(w.tiltX, w.tiltY) * 180) / Double.pi
        let right = size.width - 24
        let readTop = engine.insetTop + 12
        drawText(context, String(format: "%.1f°", degrees), font: .custom("AvenirNext-DemiBold", size: 22),
                 color: ink, right: right, baseline: readTop + 38)
        drawText(context, "TILT", font: .custom("AvenirNext-DemiBold", size: 10.5), color: muted, right: right, baseline: readTop + 11)
    }

    /// Text set right-aligned on a baseline, like Skia's drawText.
    private static func drawText(_ context: GraphicsContext, _ string: String, font: Font, color: Color, right: Double, baseline: Double) {
        let text = context.resolve(Text(string).font(font).foregroundColor(color))
        let measured = text.measure(in: CGSize(width: 1000, height: 1000))
        let ascent = text.firstBaseline(in: measured)
        context.draw(text, at: CGPoint(x: right - measured.width, y: baseline - ascent), anchor: .topLeading)
    }

    private static func circle(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }

    /// One marble on screen: the glass, the swirl that rolls with it, the inner glow and the shine.
    private static func drawMarble(_ context: GraphicsContext, _ cx: Double, _ cy: Double, _ rs: Double,
                                   _ set: GlassMarblesTrayMarble, _ rot: [Double], _ o: Int) {
        let disc = circle(cx, cy, rs)
        // The glass body: clear in the middle, the tint thick at its edges.
        context.fill(disc, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgba(mix(set.glass, (255, 255, 255), 0.4), 0.5), location: 0),
                .init(color: rgba(set.glass, 0.62), location: 0.62),
                .init(color: rgba(mix(set.glass, (20, 20, 30), 0.55), 0.92), location: 1),
            ]),
            center: CGPoint(x: cx + rs * 0.18, y: cy + rs * 0.22), startRadius: 0, endRadius: rs * 1.08
        ))

        var inside = context
        inside.clip(to: disc)
        // The swirl: leaf-shaped vanes through the centre, each in a plane through the marble's own
        // axis, turned by its orientation and seen straight on. Far halves sit behind the glass.
        for k in 0..<set.vanes {
            let psi = (Double.pi * Double(k)) / Double(set.vanes)
            let ex = cos(psi) * 0.74
            let ey = sin(psi) * 0.74
            // Two tips on the equator and two on the axis, slightly twisted.
            let tips = [ex, ey, 0, -sin(psi) * 0.16, cos(psi) * 0.16, 0.72, -ex, -ey, 0, sin(psi) * 0.16, -cos(psi) * 0.16, -0.72]
            var px: [Double] = []
            var py: [Double] = []
            var depth = 0.0
            for t in 0..<4 {
                let lx = tips[t * 3]
                let ly = tips[t * 3 + 1]
                let lz = tips[t * 3 + 2]
                px.append(cx + (rot[o] * lx + rot[o + 1] * ly + rot[o + 2] * lz) * rs)
                py.append(cy + (rot[o + 3] * lx + rot[o + 4] * ly + rot[o + 5] * lz) * rs)
                depth += rot[o + 6] * lx + rot[o + 7] * ly + rot[o + 8] * lz
            }
            var leaf = Path()
            leaf.move(to: CGPoint(x: px[0], y: py[0]))
            for t in 1...4 {
                let a = t - 1
                let b = t % 4
                // Bulge each edge outward from the centre, so the vane is a leaf, not a diamond.
                let mx = (px[a] + px[b]) / 2
                let my = (py[a] + py[b]) / 2
                leaf.addQuadCurve(to: CGPoint(x: px[b], y: py[b]), control: CGPoint(x: cx + (mx - cx) * 1.45, y: cy + (my - cy) * 1.45))
            }
            leaf.closeSubpath()
            let color = k % 2 == 0 ? set.vane : set.vane2
            soft(inside, 0.7).fill(leaf, with: .color(rgba(color, 0.7 + 0.1 * max(-1, min(1, depth)))))
            inside.stroke(leaf, with: .color(rgba(mix(color, (255, 255, 255), 0.5), 0.5)), lineWidth: 0.6)
        }
        // The front of the glass lies over the swirl: a faint milky haze towards the light.
        inside.fill(disc, with: .radialGradient(
            Gradient(colors: [rgba((255, 255, 255), 0.22), rgba(set.glass, 0.06)]),
            center: CGPoint(x: cx - rs * 0.3, y: cy - rs * 0.35), startRadius: 0, endRadius: rs * 1.1
        ))
        // Light through the glass gathers on the far side, low right.
        var glow = inside
        glow.blendMode = .screen
        glow.fill(disc, with: .radialGradient(
            Gradient(colors: [rgba(mix(set.glass, (255, 255, 255), 0.6), 0.6), rgba(set.glass, 0)]),
            center: CGPoint(x: cx + rs * 0.38, y: cy + rs * 0.44), startRadius: 0, endRadius: rs * 0.7
        ))
        // Thick glass at the rim: a darker ring.
        inside.fill(disc, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgba(set.glass, 0), location: 0.72),
                .init(color: rgba(mix(set.glass, (10, 10, 20), 0.6), 0.55), location: 1),
            ]),
            center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: rs
        ))

        // The shine: a sharp highlight towards the light and a soft window reflection around it.
        context.fill(circle(cx - rs * 0.36, cy - rs * 0.4, rs * 0.55), with: .radialGradient(
            Gradient(colors: [Color.white.opacity(0.5), Color.white.opacity(0)]),
            center: CGPoint(x: cx - rs * 0.36, y: cy - rs * 0.4), startRadius: 0, endRadius: rs * 0.55
        ))
        var spec = soft(context, 0.6)
        spec.translateBy(x: cx - rs * 0.4, y: cy - rs * 0.44)
        spec.rotate(by: .degrees(-38))
        spec.fill(Path(ellipseIn: CGRect(x: -rs * 0.2, y: -rs * 0.11, width: rs * 0.4, height: rs * 0.22)), with: .color(Color.white.opacity(0.95)))
        soft(context, 1).fill(circle(cx + rs * 0.46, cy + rs * 0.5, rs * 0.09), with: .color(Color.white.opacity(0.35)))
        context.stroke(circle(cx, cy, rs - 0.4), with: .color(rgba(mix(set.glass, (0, 0, 0), 0.6), 0.45)), lineWidth: 0.8)
    }

    /// A glint where two marbles clicked: a tiny four-pointed star that flares and fades.
    private static func drawFlash(_ context: GraphicsContext, _ x: Double, _ y: Double, _ life: Double, _ power: Double) {
        let k = 1 - life
        let size = 3 + 9 * power * sin(Double.pi * min(1, life * 1.6 + 0.2))
        var add = context
        add.blendMode = .plusLighter
        var star = Path()
        star.move(to: CGPoint(x: x - size, y: y))
        star.addLine(to: CGPoint(x: x + size, y: y))
        star.move(to: CGPoint(x: x, y: y - size))
        star.addLine(to: CGPoint(x: x, y: y + size))
        add.stroke(star, with: .color(rgba((255, 246, 225), 0.9 * k * power)), lineWidth: 1.1)
        soft(add, 2.5).fill(circle(x, y, 2.5 + 2 * power), with: .color(rgba((255, 240, 210), 0.7 * k * power)))
    }

    // MARK: The baked tray top

    /// The tray's top: a mitred walnut frame with bevelled edges and brass pins, an oak floor, and
    /// the carved cups. Drawn once into an image (see `GlassMarblesTrayBake`).
    static func drawTrayTop(in context: inout GraphicsContext, trayW: Double, trayH: Double, tray: GlassMarblesTrayTray) {
        let f = E.frame
        let outer = Path(roundedRect: CGRect(x: 0, y: 0, width: trayW, height: trayH), cornerRadius: E.outerCorner)
        let innerRect = CGRect(x: f, y: f, width: trayW - f * 2, height: trayH - f * 2)
        let inner = Path(roundedRect: innerRect, cornerRadius: tray.corner)
        let walnutLight: RGB = (138, 88, 52)
        let walnutDark: RGB = (62, 34, 18)

        func wood(_ light: RGB, _ dark: RGB, _ seed: Double, _ across: Bool, _ scale: Double) -> GraphicsContext.Shading {
            .shader(ShaderLibrary.glassMarblesTrayWood(
                .float2(0.012 * scale, 0.05 * scale),
                .float3(light.0 / 255, light.1 / 255, light.2 / 255),
                .float3(dark.0 / 255, dark.1 / 255, dark.2 / 255),
                .float(seed),
                .float(across ? 1 : 0)
            ))
        }

        // The frame: four mitred boards, the long ones with the grain along them.
        var frame = context
        frame.clip(to: outer)
        let boards: [([Double], Bool)] = [
            ([0, 0, trayW, 0, trayW - f, f, f, f], false),
            ([0, trayH, trayW, trayH, trayW - f, trayH - f, f, trayH - f], false),
            ([0, 0, f, f, f, trayH - f, 0, trayH], true),
            ([trayW, 0, trayW - f, f, trayW - f, trayH - f, trayW, trayH], true),
        ]
        for (k, board) in boards.enumerated() {
            let (p, across) = board
            var b = Path()
            b.move(to: CGPoint(x: p[0], y: p[1]))
            b.addLine(to: CGPoint(x: p[2], y: p[3]))
            b.addLine(to: CGPoint(x: p[4], y: p[5]))
            b.addLine(to: CGPoint(x: p[6], y: p[7]))
            b.closeSubpath()
            frame.fill(b, with: wood(walnutLight, walnutDark, 3.1 + Double(k) * 5.7, across, 1))
            // The mitre joints: a hairline at both ends of each long board, so every corner gets one.
            if across { continue }
            for (a, b) in [(0, 6), (2, 4)] {
                var joint = Path()
                joint.move(to: CGPoint(x: p[a], y: p[a + 1]))
                joint.addLine(to: CGPoint(x: p[b], y: p[b + 1]))
                frame.stroke(joint, with: .color(rgba((30, 14, 6), 0.55)), lineWidth: 0.6)
            }
        }

        // Bevels: a lit outer edge on the upper left, a shaded one on the lower right.
        context.stroke(
            Path(roundedRect: CGRect(x: 1.25, y: 1.25, width: trayW - 2.5, height: trayH - 2.5), cornerRadius: E.outerCorner - 1),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgba((255, 226, 190), 0.5), location: 0),
                    .init(color: rgba((255, 226, 190), 0.05), location: 0.5),
                    .init(color: rgba((20, 8, 2), 0.5), location: 1),
                ]),
                startPoint: .zero, endPoint: CGPoint(x: trayW, y: trayH)
            ),
            lineWidth: 2.5
        )
        // The inner chamfer falls towards the floor: shaded on the lit side, lit on the far side.
        context.stroke(
            Path(roundedRect: CGRect(x: f - 2.5, y: f - 2.5, width: innerRect.width + 5, height: innerRect.height + 5), cornerRadius: tray.corner + 2.5),
            with: .linearGradient(
                Gradient(colors: [rgba((28, 12, 4), 0.6), rgba((255, 220, 180), 0.35)]),
                startPoint: CGPoint(x: f, y: f), endPoint: CGPoint(x: trayW - f, y: trayH - f)
            ),
            lineWidth: 5
        )

        // The floor: pale oak, grain running the long way.
        var floor = context
        floor.clip(to: inner)
        floor.fill(Path(innerRect), with: wood((228, 190, 140), (186, 136, 88), 11.3, true, 0.6))
        // The walls shade the floor along the lit side.
        soft(floor, 6).stroke(
            Path(roundedRect: CGRect(x: f - 6, y: f - 6, width: innerRect.width + 12, height: innerRect.height + 12), cornerRadius: tray.corner + 6),
            with: .linearGradient(
                Gradient(colors: [rgba((50, 24, 8), 0.55), rgba((50, 24, 8), 0.12)]),
                startPoint: CGPoint(x: f, y: f), endPoint: CGPoint(x: trayW - f, y: trayH - f)
            ),
            lineWidth: 22
        )

        // Cups: shallow bowls, dark on the side towards the light and lit on the far rim.
        let cupR = T.cupR
        for c in 0..<tray.cupX.count {
            let x = trayW / 2 + tray.cupX[c]
            let y = trayH / 2 + tray.cupY[c]
            floor.fill(circle(x, y, cupR), with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba((255, 236, 205), 0.32), location: 0),
                    .init(color: rgba((120, 70, 30), 0.12), location: 0.62),
                    .init(color: rgba((70, 36, 12), 0.5), location: 1),
                ]),
                center: CGPoint(x: x + 3, y: y + 3.5), startRadius: 0, endRadius: cupR * 1.15
            ))
            floor.stroke(circle(x, y, cupR), with: .linearGradient(
                Gradient(colors: [rgba((60, 30, 10), 0.55), rgba((255, 240, 215), 0.55)]),
                startPoint: CGPoint(x: x - cupR, y: y - cupR), endPoint: CGPoint(x: x + cupR, y: y + cupR)
            ), lineWidth: 1.2)
        }
        // A satin varnish sheen across the floor.
        floor.fill(Path(innerRect), with: .linearGradient(
            Gradient(colors: [rgba((255, 250, 235), 0.14), rgba((255, 250, 235), 0)]),
            startPoint: CGPoint(x: f, y: f), endPoint: CGPoint(x: trayW * 0.7, y: trayH * 0.6)
        ))

        // Brass pins at the mitres.
        let pins = [(f / 2, f / 2), (trayW - f / 2, f / 2), (f / 2, trayH - f / 2), (trayW - f / 2, trayH - f / 2)]
        for (x, y) in pins {
            context.fill(circle(x, y, 2.8), with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(red: 0xFF / 255, green: 0xF2 / 255, blue: 0xC2 / 255), location: 0),
                    .init(color: Color(red: 0xC9 / 255, green: 0x9A / 255, blue: 0x45 / 255), location: 0.5),
                    .init(color: Color(red: 0x6B / 255, green: 0x4A / 255, blue: 0x1C / 255), location: 1),
                ]),
                center: CGPoint(x: x - 1.2, y: y - 1.2), startRadius: 0, endRadius: 3.6
            ))
        }
    }
}
