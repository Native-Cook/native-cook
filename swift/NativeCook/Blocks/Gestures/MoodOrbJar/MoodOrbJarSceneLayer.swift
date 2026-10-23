import SwiftUI

/// The approved React Native Skia scene, split into rasterized static layers and small dynamic
/// canvases. The paper, picker surface and glass body are rendered once; frames only redraw beads,
/// the landing ripple, moving reflection and lid.
struct MoodOrbJarSceneLayer: View {
    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry

    var body: some View {
        let frame = controller.frame
        let shake = MoodOrbJarScenePainter.jarShake(frame: frame, scale: geometry.scale)

        ZStack(alignment: .topLeading) {
            MoodOrbJarStaticCanvas(layer: .pageAndPicker, geometry: geometry).equatable()

            Canvas { context, size in
                MoodOrbJarScenePainter(context: context, size: size, geometry: geometry, frame: frame).paintSlots()
            }

            MoodOrbJarStaticCanvas(layer: .jarBack, geometry: geometry).equatable()
                .offset(x: shake)

            Canvas { context, size in
                MoodOrbJarScenePainter(context: context, size: size, geometry: geometry, frame: frame).paintBehindGlass()
            }
            .offset(x: shake)

            MoodOrbJarStaticCanvas(layer: .jarFront, geometry: geometry).equatable()
                .offset(x: shake)

            Canvas { context, size in
                MoodOrbJarScenePainter(context: context, size: size, geometry: geometry, frame: frame).paintFront()
            }
            .offset(x: shake)
        }
        .accessibilityHidden(true)
    }
}

/// Keeps the expensive paper and glass vectors out of the observed frame subtree. SwiftUI renders
/// each layer into its drawing group once per geometry, then reuses that bitmap while the sim ticks.
private struct MoodOrbJarStaticCanvas: View, Equatable {
    let layer: MoodOrbJarStaticLayer
    let geometry: MoodOrbJarGeometry

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.layer == rhs.layer && lhs.geometry == rhs.geometry
    }

    var body: some View {
        Canvas { context, size in
            let painter = MoodOrbJarScenePainter(context: context, size: size, geometry: geometry, frame: .empty)
            switch layer {
            case .pageAndPicker: painter.paintPageAndPicker()
            case .jarBack: painter.paintJarBack()
            case .jarFront: painter.paintJarFront()
            }
        }
        .drawingGroup()
    }
}

private enum MoodOrbJarStaticLayer: Equatable {
    case pageAndPicker
    case jarBack
    case jarFront
}

private struct MoodOrbJarScenePainter {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Motion = MoodOrbJarTokens.Motion
    typealias Palette = MoodOrbJarTokens.Palette

    let context: GraphicsContext
    let size: CGSize
    let g: MoodOrbJarGeometry
    let frame: MoodOrbJarFrame

    init(context: GraphicsContext, size: CGSize, geometry: MoodOrbJarGeometry, frame: MoodOrbJarFrame) {
        self.context = context
        self.size = size
        self.g = geometry
        self.frame = frame
    }

    private var s: Double { g.scale }
    private var halfWidth: Double { (g.right - g.left) / 2 }
    private var groundY: Double { g.floor + 14 * s }

    static func jarShake(frame: MoodOrbJarFrame, scale: Double) -> Double {
        guard let ripple = frame.ripple else { return 0 }
        let t = ripple.progress * Motion.Ripple.duration
        return Motion.JarKnock.amplitude * scale
            * exp(-Motion.JarKnock.decay * t)
            * sin(2 * .pi * Motion.JarKnock.hertz * t)
    }

    func paintPageAndPicker() {
        context.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .linearGradient(
                Gradient(colors: [Palette.background, Palette.backgroundBottom]),
                startPoint: .zero,
                endPoint: CGPoint(x: size.width * 0.3, y: size.height)
            )
        )

        let cardX = Layout.side * s
        let cardY = g.top + Layout.pickerTop * s
        let card = CGRect(x: cardX, y: cardY, width: size.width - cardX * 2, height: Layout.pickerHeight * s)
        let radius = Layout.cardRadius * s
        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(roundedRect: card.offsetBy(dx: 0, dy: 6 * s), cornerRadius: radius),
            with: .color(Palette.cardShadow)
        )
        context.fill(Path(roundedRect: card, cornerRadius: radius), with: .color(Palette.card))
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: radius),
            with: .color(Palette.cardEdge),
            lineWidth: 1
        )
    }

    func paintSlots() {
        for index in MoodOrbJarTokens.moods.indices {
            let slot = frame.slots[index]
            let radius = Layout.pickerOrb * s
            var orb = context
            orb.opacity = slot
            orb.translateBy(x: g.slotX[index], y: g.slotY)
            orb.scaleBy(x: 0.9 + 0.1 * slot, y: 0.9 + 0.1 * slot)

            var shadow = orb
            shadow.addFilter(.blur(radius: 2))
            shadow.fill(
                Path(ellipseIn: CGRect(x: -radius * 0.8, y: radius * 0.78, width: radius * 1.6, height: radius * 0.36)),
                with: .color(Color.rgba(60, 45, 25, 0.16))
            )
            MoodOrbJarBeadPainter.draw(in: orb, radius: radius, mood: index)
        }
    }

    func paintJarBack() {
        let outer = jarPath(outset: 5 * s, base: 14 * s, neck: 8 * s)
        let mouthRect = CGRect(
            x: g.centerX - g.mouthHalf,
            y: g.mouthY - 5 * s,
            width: g.mouthHalf * 2,
            height: 10 * s
        )

        var shadow = context
        shadow.addFilter(.blur(radius: 10 * s))
        shadow.fill(
            Path(ellipseIn: CGRect(
                x: g.centerX - halfWidth * 1.02,
                y: groundY - 9 * s,
                width: halfWidth * 2.3,
                height: 22 * s
            )),
            with: .color(Palette.floorShadow)
        )
        var caustic = context
        caustic.addFilter(.blur(radius: 5 * s))
        caustic.fill(
            Path(ellipseIn: CGRect(
                x: g.centerX - halfWidth * 0.35,
                y: groundY - 2 * s,
                width: halfWidth * 1.05,
                height: 9 * s
            )),
            with: .color(Palette.caustic)
        )

        context.fill(outer, with: .color(Palette.glassBody))
        context.stroke(Path(ellipseIn: mouthRect), with: .color(Palette.glassEdgeSoft), lineWidth: 1.5)
    }

    func paintBehindGlass() {
        paintOrbs(front: false)
        guard let ripple = frame.ripple else { return }
        let radius = (Layout.orb + Motion.Ripple.radius * ripple.progress) * s
        var ring = context
        ring.opacity = (1 - ripple.progress) * 0.55
        ring.stroke(
            Path(ellipseIn: CGRect(x: ripple.x - radius, y: ripple.y - radius, width: radius * 2, height: radius * 2)),
            with: .color(Color(hex: MoodOrbJarTokens.moods[ripple.mood].light)),
            lineWidth: 2 * s
        )
    }

    func paintJarFront() {
        let inner = jarPath(outset: 0, base: 0, neck: 8 * s)
        let outer = jarPath(outset: 5 * s, base: 14 * s, neck: 8 * s)

        // The wall band is the outer glass minus the hollow inner volume.
        context.drawLayer { band in
            band.fill(outer, with: .color(Palette.glassEdgeSoft))
            band.blendMode = .destinationOut
            band.fill(inner, with: .color(.white))
        }
        context.stroke(outer, with: .color(Palette.glassEdge), lineWidth: 1.2)
        context.stroke(inner, with: .color(Color.rgba(255, 255, 255, 0.5)), lineWidth: 0.8)

        var base = context
        base.clip(to: outer)
        base.fill(
            Path(CGRect(
                x: g.centerX - halfWidth - 5 * s,
                y: g.floor,
                width: (halfWidth + 5 * s) * 2,
                height: 14 * s
            )),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color.rgba(255, 255, 255, 0.55), location: 0),
                    .init(color: Color.rgba(150, 172, 166, 0.26), location: 0.25),
                    .init(color: Color.rgba(110, 132, 126, 0.36), location: 1)
                ]),
                startPoint: CGPoint(x: 0, y: g.floor),
                endPoint: CGPoint(x: 0, y: groundY)
            )
        )
        context.stroke(
            Path { path in
                path.move(to: CGPoint(x: g.centerX - halfWidth + g.corner, y: g.floor + 0.5))
                path.addLine(to: CGPoint(x: g.centerX + halfWidth - g.corner, y: g.floor + 0.5))
            },
            with: .color(Color.rgba(255, 255, 255, 0.8)),
            lineWidth: 1
        )
    }

    func paintFront() {
        paintGlassReflection()
        paintNearMouthRim()
        paintAim()
        paintOrbs(front: true)
        paintLid()
    }

    private func paintGlassReflection() {
        let sideTop = g.shoulderEnd + 10 * s
        let sideHeight = g.floor - g.corner - sideTop
        var shine = context
        shine.translateBy(x: frame.tilt * Motion.Tilt.glass * s, y: 0)

        var broad = shine
        broad.addFilter(.blur(radius: 1.5))
        broad.fill(
            Path(roundedRect: CGRect(x: g.centerX - halfWidth + 10 * s, y: sideTop, width: 8 * s, height: sideHeight), cornerRadius: 4 * s),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: Color.rgba(255, 255, 255, 0.78), location: 0.18),
                    .init(color: Color.rgba(255, 255, 255, 0.5), location: 0.7),
                    .init(color: .clear, location: 1)
                ]),
                startPoint: CGPoint(x: 0, y: sideTop),
                endPoint: CGPoint(x: 0, y: sideTop + sideHeight)
            )
        )
        shine.fill(
            Path(roundedRect: CGRect(x: g.centerX - halfWidth + 22 * s, y: sideTop + 30 * s, width: 2 * s, height: sideHeight * 0.6), cornerRadius: s),
            with: .color(Color.rgba(255, 255, 255, 0.55))
        )
        shine.fill(
            Path(roundedRect: CGRect(x: g.centerX + halfWidth - 15 * s, y: sideTop + 20 * s, width: 4 * s, height: sideHeight * 0.75), cornerRadius: 2 * s),
            with: .linearGradient(
                Gradient(colors: [.clear, Color.rgba(255, 255, 255, 0.32), .clear]),
                startPoint: CGPoint(x: 0, y: sideTop),
                endPoint: CGPoint(x: 0, y: sideTop + sideHeight)
            )
        )
    }

    private func paintNearMouthRim() {
        var path = Path()
        let radius = g.mouthHalf + 5 * s
        path.move(to: CGPoint(x: g.centerX + radius, y: g.mouthY))
        path.addQuadCurve(
            to: CGPoint(x: g.centerX - radius, y: g.mouthY),
            control: CGPoint(x: g.centerX, y: g.mouthY + 12 * s)
        )
        context.stroke(path, with: .color(Color.rgba(255, 255, 255, 0.85)), lineWidth: 1.5)
    }

    private func paintAim() {
        guard !frame.aim.isEmpty else { return }
        var dots = Path()
        for (index, point) in frame.aim.enumerated() {
            let radius = (2.2 - Double(index * 2) * 0.04) * s
            dots.addEllipse(in: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2))
        }
        context.fill(dots, with: .color(Palette.aim))
    }

    private func paintOrbs(front: Bool) {
        let radius = Layout.orb * s
        for sprite in frame.sprites {
            // In the RN scene the trail lives in the front pass even while the bead has fallen behind the rim.
            if front, sprite.isFlying { paintTrail(of: sprite, radius: radius) }
            guard sprite.isFront == front, sprite.opacity > 0 else { continue }

            let inside = !front && sprite.y > g.mouthY + radius
            let u = max(-1, min(1, (sprite.x - g.centerX) / halfWidth))
            let lens = inside ? 1 - 0.22 * u * u : 1
            var orb = context
            orb.opacity = sprite.opacity
            orb.translateBy(x: sprite.x + (inside ? 0.05 * (sprite.x - g.centerX) : 0), y: sprite.y)
            orb.translateBy(x: 0, y: sprite.squash * radius)
            orb.scaleBy(x: lens, y: 1)
            orb.scaleBy(x: sprite.scale, y: sprite.scale)
            orb.scaleBy(x: 1 + sprite.squash, y: 1 - sprite.squash)
            MoodOrbJarBeadPainter.draw(in: orb, radius: radius, mood: sprite.mood)
        }
    }

    private func paintTrail(of sprite: MoodOrbJarFrame.Sprite, radius: Double) {
        let gravity = Motion.Throw.gravity * s
        for (index, alpha) in Motion.Trail.alpha.enumerated() {
            let tau = Double(index + 1) * Motion.Trail.gap
            var copy = context
            copy.opacity = alpha
            copy.translateBy(
                x: sprite.x - sprite.vx * tau,
                y: sprite.y - sprite.vy * tau + 0.5 * gravity * tau * tau
            )
            copy.scaleBy(x: 1 - tau * 3, y: 1 - tau * 3)
            copy.fill(
                Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)),
                with: .color(Color(hex: MoodOrbJarTokens.moods[sprite.mood].base))
            )
        }
    }

    private func paintLid() {
        let width = Layout.lidWidth * s
        let height = Layout.lidHeight * s
        let hingeY = g.lidY + height
        let centerX = g.lidX + width / 2
        var lid = context
        lid.translateBy(x: 0, y: -Motion.Lid.lift * s * frame.lid)
        lid.translateBy(x: centerX, y: hingeY)
        lid.rotate(by: .radians(-Motion.Lid.tilt * frame.lid))
        lid.translateBy(x: -centerX, y: -hingeY)

        var shadow = lid
        shadow.addFilter(.blur(radius: 3))
        shadow.fill(
            Path(roundedRect: CGRect(x: g.lidX + 2 * s, y: g.lidY + height - 2 * s, width: width - 4 * s, height: 6 * s), cornerRadius: 3 * s),
            with: .color(Color.rgba(40, 30, 20, 0.18))
        )
        let stops = zip(Palette.lid, Palette.lidStops).map { Gradient.Stop(color: $0.0, location: $0.1) }
        lid.fill(
            Path(roundedRect: CGRect(x: g.lidX, y: g.lidY, width: width, height: height), cornerRadius: 7 * s),
            with: .linearGradient(
                Gradient(stops: stops),
                startPoint: CGPoint(x: g.lidX, y: 0),
                endPoint: CGPoint(x: g.lidX + width, y: 0)
            )
        )
        let ridgeCount = max(0, Int(floor(width / (4 * s))) - 3)
        for index in 0..<ridgeCount {
            let x = g.lidX + (8 + Double(index) * 4) * s
            lid.stroke(
                Path { path in
                    path.move(to: CGPoint(x: x, y: g.lidY + 8 * s))
                    path.addLine(to: CGPoint(x: x, y: g.lidY + height - 5 * s))
                },
                with: .color(Palette.lidRidge),
                lineWidth: 0.75
            )
        }
        lid.stroke(
            Path { path in
                path.move(to: CGPoint(x: g.lidX + 6 * s, y: g.lidY + 1.5))
                path.addLine(to: CGPoint(x: g.lidX + width - 6 * s, y: g.lidY + 1.5))
            },
            with: .color(Color.rgba(255, 255, 255, 0.7)),
            lineWidth: 1
        )
        lid.stroke(
            Path(roundedRect: CGRect(x: g.lidX, y: g.lidY, width: width, height: height), cornerRadius: 7 * s),
            with: .color(Color.rgba(30, 24, 16, 0.25)),
            lineWidth: 0.75
        )
    }

    /// The jar's outline, grown outward by `outset`; `base` extends its thick bottom slab.
    private func jarPath(outset: Double, base: Double, neck: Double) -> Path {
        let half = halfWidth + outset
        let mouth = g.mouthHalf + outset
        let top = g.mouthY - neck
        let bottom = g.floor + base
        let corner = g.corner + outset
        let control = (g.shoulderEnd - g.mouthY) * 0.55
        var path = Path()
        path.move(to: CGPoint(x: g.centerX - mouth, y: top))
        path.addLine(to: CGPoint(x: g.centerX - mouth, y: g.mouthY))
        path.addCurve(
            to: CGPoint(x: g.centerX - half, y: g.shoulderEnd),
            control1: CGPoint(x: g.centerX - mouth, y: g.mouthY + control),
            control2: CGPoint(x: g.centerX - half, y: g.shoulderEnd - control)
        )
        path.addLine(to: CGPoint(x: g.centerX - half, y: bottom - corner))
        path.addQuadCurve(
            to: CGPoint(x: g.centerX - half + corner, y: bottom),
            control: CGPoint(x: g.centerX - half, y: bottom)
        )
        path.addLine(to: CGPoint(x: g.centerX + half - corner, y: bottom))
        path.addQuadCurve(
            to: CGPoint(x: g.centerX + half, y: bottom - corner),
            control: CGPoint(x: g.centerX + half, y: bottom)
        )
        path.addLine(to: CGPoint(x: g.centerX + half, y: g.shoulderEnd))
        path.addCurve(
            to: CGPoint(x: g.centerX + mouth, y: g.mouthY),
            control1: CGPoint(x: g.centerX + half, y: g.shoulderEnd - control),
            control2: CGPoint(x: g.centerX + mouth, y: g.mouthY + control)
        )
        path.addLine(to: CGPoint(x: g.centerX + mouth, y: top))
        return path
    }
}
