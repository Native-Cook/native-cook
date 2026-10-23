import SwiftUI

/// Canvas: page, picker card and its orbs, the glass jar (back, orbs, front reflections) and its hinged
/// lid (MoodScene.tsx). The jar, lid and orbs are images; only the card and the shadows are drawn.
/// This is the one view that reads the per-frame values.
struct MoodOrbJarSceneLayer: View {
    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry

    var body: some View {
        let frame = controller.frame
        let geometry = geometry
        Canvas { context, size in
            MoodOrbJarScenePainter(context: context, size: size, geometry: geometry, frame: frame).paint()
        }
        .accessibilityHidden(true)
    }
}

private struct MoodOrbJarScenePainter {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Motion = MoodOrbJarTokens.Motion
    typealias Palette = MoodOrbJarTokens.Palette

    let context: GraphicsContext
    let size: CGSize
    let g: MoodOrbJarGeometry
    let frame: MoodOrbJarFrame
    private let jarPlate: GraphicsContext.ResolvedImage
    private let lid: GraphicsContext.ResolvedImage
    private let orbs: [GraphicsContext.ResolvedImage]
    private let pileOrbs: [GraphicsContext.ResolvedImage]

    init(context: GraphicsContext, size: CGSize, geometry: MoodOrbJarGeometry, frame: MoodOrbJarFrame) {
        self.context = context
        self.size = size
        self.g = geometry
        self.frame = frame
        jarPlate = context.resolve(Image(uiImage: MoodOrbJarAssets.jarPlate))
        lid = context.resolve(Image(uiImage: MoodOrbJarAssets.lid))
        orbs = MoodOrbJarAssets.orbs.map { context.resolve(Image(uiImage: $0)) }
        pileOrbs = MoodOrbJarAssets.pileOrbs.map { context.resolve(Image(uiImage: $0)) }
    }

    func paint() {
        paintPage()
        paintPicker()
        paintJar()
        paintOrbs()
        paintRipple()
        paintAim()
        paintLid()
    }

    private var s: Double { g.scale }

    /// A landing moves the complete jar group, as in MoodScene's `jarShake` transform.
    private var jarContext: GraphicsContext {
        var group = context
        if let ripple = frame.ripple {
            let t = ripple.progress * Motion.Ripple.duration
            let x = Motion.JarKnock.amplitude * s
                * exp(-Motion.JarKnock.decay * t)
                * sin(2 * .pi * Motion.JarKnock.hertz * t)
            group.translateBy(x: x, y: 0)
        }
        return group
    }

    /// The orb art is 144 px with a 132 px orb: draw it 12/11 of the orb's diameter.
    private func orbDiameter(radius: Double) -> Double {
        radius * 2 * MoodOrbJarTokens.orbImageScale
    }

    private func orbRect(radius: Double) -> CGRect {
        let d = orbDiameter(radius: radius)
        return CGRect(x: -d / 2, y: -d / 2, width: d, height: d)
    }

    private func paintPage() {
        context.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .linearGradient(
                Gradient(colors: [Palette.background, Palette.backgroundBottom]),
                startPoint: .zero,
                endPoint: CGPoint(x: 0, y: size.height)
            )
        )
    }

    /// The picker card (flat UI in the reference) with its five orbs.
    private func paintPicker() {
        let cardX = Layout.side * s
        let cardY = g.top + Layout.pickerTop * s
        let cardRect = CGRect(x: cardX, y: cardY, width: size.width - cardX * 2, height: Layout.pickerHeight * s)
        let radius = Layout.cardRadius * s

        var shadow = context
        shadow.addFilter(.blur(radius: 10))
        shadow.fill(
            Path(roundedRect: cardRect.offsetBy(dx: 0, dy: 4 * s), cornerRadius: radius),
            with: .color(Palette.cardShadow)
        )
        context.fill(Path(roundedRect: cardRect, cornerRadius: radius), with: .color(Palette.card))

        for index in 0..<MoodOrbJarTokens.moods.count {
            // Regrows from 0.9 with a fade, never from scale 0.
            let slot = frame.slots[index]
            var orb = context
            orb.opacity = slot
            orb.translateBy(x: g.slotX[index], y: g.slotY)
            orb.scaleBy(x: 0.9 + 0.1 * slot, y: 0.9 + 0.1 * slot)
            orb.draw(orbs[index], in: orbRect(radius: Layout.pickerOrb * s))
        }
    }

    private var jarRect: CGRect {
        CGRect(
            x: 0,
            y: MoodOrbJarTokens.replicaPlateY / 1206 * g.width,
            width: g.width,
            height: MoodOrbJarTokens.replicaPlateHeight / 1206 * g.width
        )
    }

    private func paintJar() {
        var jar = jarContext
        jar.draw(jarPlate, in: jarRect)
    }

    private func paintOrbs() {
        for (index, sprite) in frame.sprites.enumerated() {
            // Motion trail while flying: copies where the orb was a few hundredths of a second ago.
            if sprite.isFlying {
                paintTrail(of: sprite)
            }
            guard sprite.opacity > 0 else { continue }
            let radius = Layout.orb * s
            let squash = sprite.squash
            var orb = jarContext
            orb.opacity = sprite.opacity
            orb.translateBy(x: sprite.x, y: sprite.y)
            // Soft body: wider and shorter, or the spring's stretch.
            orb.translateBy(x: 0, y: squash * radius)
            orb.rotate(by: .radians(sprite.spin))
            orb.scaleBy(x: sprite.scale, y: sprite.scale)
            orb.scaleBy(x: 1 + squash, y: 1 - squash)
            let image = index < pileOrbs.count ? pileOrbs[index] : orbs[sprite.mood]
            orb.draw(image, in: orbRect(radius: radius))
        }
    }

    private func paintTrail(of sprite: MoodOrbJarFrame.Sprite) {
        let gravity = Motion.Throw.gravity * s
        for (n, alpha) in Motion.Trail.alpha.enumerated() {
            let tau = Double(n + 1) * Motion.Trail.gap
            var copy = jarContext
            copy.opacity = alpha
            copy.translateBy(
                x: sprite.x - sprite.vx * tau,
                y: sprite.y - sprite.vy * tau + 0.5 * gravity * tau * tau
            )
            copy.scaleBy(x: 1 - tau * 3, y: 1 - tau * 3)
            copy.draw(orbs[sprite.mood], in: orbRect(radius: Layout.orb * s))
        }
    }

    /// Landing ripple in the mood's colour.
    private func paintRipple() {
        guard let ripple = frame.ripple else { return }
        let radius = (Layout.orb + Motion.Ripple.radius * ripple.progress) * s
        var ring = jarContext
        ring.opacity = (1 - ripple.progress) * 0.8
        ring.stroke(
            Path(ellipseIn: CGRect(x: ripple.x - radius, y: ripple.y - radius, width: radius * 2, height: radius * 2)),
            with: .color(Color(hex: MoodOrbJarTokens.moods[ripple.mood].base)),
            lineWidth: 3 * s
        )
    }

    /// Aim arc while an orb is held: one dot per sampled point, shrinking.
    private func paintAim() {
        guard !frame.aim.isEmpty else { return }
        var dots = Path()
        for (n, point) in frame.aim.enumerated() {
            // The React Native path indexes the flat x, y list, so the radius shrinks by 0.08 a dot.
            let radius = (2.2 - Double(n * 2) * 0.04) * s
            dots.addEllipse(in: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2))
        }
        var aim = jarContext
        aim.fill(dots, with: .color(Palette.aim))
    }

    /// Lid, lifted straight up and tilted a little about its bottom centre.
    private func paintLid() {
        let hingeY = g.lidY + Layout.lidHeight * s
        let centerX = g.lidX + (Layout.lidWidth * s) / 2
        var hinged = jarContext
        hinged.translateBy(x: 0, y: -Motion.Lid.lift * s * frame.lid)
        hinged.translateBy(x: centerX, y: hingeY)
        hinged.rotate(by: .radians(-Motion.Lid.tilt * frame.lid))
        hinged.translateBy(x: -centerX, y: -hingeY)
        hinged.draw(
            lid,
            in: CGRect(x: g.lidX, y: g.lidY, width: Layout.lidWidth * s, height: Layout.lidHeight * s)
        )
    }
}
