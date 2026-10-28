import SwiftUI
import simd

private typealias M = HabitatSpecimenCarouselMotion
private typealias Rig = HabitatSpecimenCarouselRig

/// Where everything sits on a screen of a given size. Mirrors the top of HabitatSpecimenCarousel.
struct HabitatSpecimenCarouselLayout: Equatable {
    var width: Double
    var height: Double
    var insetTop: Double
    var insetBottom: Double

    var scale: Double { width / 393 }
    var identityHeight: Double { 60 * scale }
    var heroHeight: Double {
        let available = height - insetTop - insetBottom
        return max(190 * scale, min(340 * scale, available - 415 * scale - max(0, identityHeight - 60 * scale)))
    }
    var heroTop: Double { insetTop + 78 * scale }
    var creatureSize: Double { min(357 * scale, heroHeight - 4 * scale) }
    /// About half the screen of finger travel per specimen: the front leads the finger, so one
    /// deliberate swipe turns one page, as on Choose Your Ball.
    var gap: Double { M.round(width * 0.58) }
    var identityTop: Double { heroTop + heroHeight + 29 * scale }
    var tabsTop: Double { identityTop + identityHeight + 48 * scale }
    var descriptionTop: Double { tabsTop + 44 * scale }
    /// Weight and height sit on the specimen strip, which takes the footer's centre.
    var factsTop: Double { height - insetBottom - 84 * scale }
    /// The effect layers span the screen above the hero, so plumes and beams are never clipped.
    var overlayHeight: Double { heroTop + heroHeight + 40 * scale }
    var creatureLeft: Double { (width - creatureSize) / 2 }
    var creatureTop: Double { (heroHeight - creatureSize) / 2 }
    var pad: Double { (creatureSize * Self.creaturePad).rounded() }
    /// Margin around the art square, so a hop, a squash or a wing beat is never cut by the canvas.
    static let creaturePad = 0.14
}

/// Owns the motion model and the Metal layers, and turns one into the other every frame.
@MainActor
final class HabitatSpecimenCarouselEngine: ObservableObject {
    let model: HabitatSpecimenCarouselModel
    let renderer: HabitatSpecimenCarouselRenderer?
    private(set) var layout: HabitatSpecimenCarouselLayout?
    private var lastFx = -1.0
    private var contact = 0.0
    private var frameNumber = 0.0
    private var drawnOnce = false

    init(autoPlay: Bool, previewTime: Double?, previewReduced: Bool?) {
        model = HabitatSpecimenCarouselModel(autoPlay: autoPlay, previewTime: previewTime, previewReduced: previewReduced)
        renderer = HabitatSpecimenCarouselRenderer()
        model.onFrame = { [weak self] in self?.renderFrame() }
        renderer?.onViewReady = { [weak self] in self?.renderFrame() }
    }

    func configure(_ next: HabitatSpecimenCarouselLayout) {
        guard layout != next else { return }
        layout = next
        model.gap = next.gap
        model.width = next.width
        renderFrame()
    }

    /// The GPU particle world runs live, but not on a frozen review frame or under Reduced Motion.
    var gpuParticles: Bool { renderer != nil && model.previewTime == nil && !model.reduced }

    // MARK: A frame

    /// One creature's place in the exchange and its pose this frame (shared with its signature effects).
    private func creaturePose(_ index: Int) -> (exchange: M.Exchange, pose: Rig.Pose, performing: Bool) {
        let reduced = model.reduced
        let exchange = M.exchangePose(index, model.position, reduced)
        let performing = model.powerIndex == index
        let pose = Rig.lifePose(Rig.LifeInput(
            t: model.ambientTime, reduced: reduced, role: exchange.role, f: exchange.f,
            press: model.press.value, look: model.look.value, pet: model.pet.value, petClock: model.petClock,
            land: !reduced && model.landingIndex == index ? Rig.landEnvelope(model.landing.value) : 0,
            signature: performing ? model.power.value : 0, fx: performing ? model.fx.value : -1, index: index
        ))
        return (exchange, pose, performing)
    }

    private func body(_ index: Int, _ layout: HabitatSpecimenCarouselLayout, _ state: (exchange: M.Exchange, pose: Rig.Pose, performing: Bool)) -> Rig.BodyFrame {
        Rig.BodyFrame(
            index: index, size: layout.creatureSize, pad: layout.pad, left: layout.creatureLeft, top: layout.creatureTop,
            heroTop: layout.heroTop, width: layout.width, screenHeight: layout.height, scale: layout.scale,
            exchange: state.exchange, driftPx: M.exchangeDrift * layout.width, pose: state.pose
        )
    }

    /// The incoming specimen's hop height in points, and the landed specimen's contact squash, for the shadows.
    private func lift(_ size: Double) -> (Double, Double) {
        if model.reduced { return (0, 0) }
        let p = M.wrapPosition(model.position)
        let f = p - p.rounded(.down)
        let from = Int(p.rounded(.down)) % 3
        return (f > 0.0005 ? Rig.hopLift(f) * Rig.hopHeight * size : 0, model.landingIndex == from ? max(0, Rig.landEnvelope(model.landing.value)) : 0)
    }

    private static func f4(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> SIMD4<Float> { SIMD4(Float(a), Float(b), Float(c), Float(d)) }

    func renderFrame() {
        guard let renderer, let layout, layout.width > 0 else { return }
        let reduced = model.reduced
        let position = model.position
        var scene = HabitatSpecimenCarouselScene()
        scene.width = layout.width
        scene.height = layout.height
        scene.overlayHeight = layout.overlayHeight

        // The habitat.
        let hop = lift(layout.creatureSize)
        scene.env = [
            Self.f4(layout.creatureSize, M.exchangeDrift * layout.width, (layout.heroHeight - layout.creatureSize) / 2, 0),
            Self.f4(layout.width, layout.height, layout.heroTop, layout.heroHeight),
            Self.f4(position, model.ambientTime, reduced ? 1 : 0, model.influence.value),
            Self.f4(M.signatureEnvelope(model.power.value), Double(model.powerIndex), hop.0, hop.1),
        ]

        // The creatures.
        let light = model.keyLight
        let phase = model.frontPhase
        let element = (Int(position.rounded(.down)) + 1) % 3
        var poses: [(exchange: M.Exchange, pose: Rig.Pose, performing: Bool)] = []
        for index in 0..<3 {
            let state = creaturePose(index)
            poses.append(state)
            let frame = body(index, layout, state)
            let tint = HabitatSpecimenCarouselSpecimen.rimLight[index]
            let uniforms = Rig.lifeUniforms(Rig.LifeFrame(
                body: frame, phase: phase, element: element, light: light, tint: tint,
                glow: state.performing ? M.signatureEnvelope(model.power.value) : 0,
                seconds: model.ambientTime * M.loop, reduced: reduced
            ))
            let view = layout.creatureSize + layout.pad * 2
            scene.creatures[index] = .init(
                visible: state.exchange.role >= 0, uniforms: uniforms,
                origin: SIMD2(uniforms.lidOrigin.z, uniforms.lidOrigin.w), side: Float(view), zoom: Float(state.exchange.scale)
            )
        }

        // The signature move's attached effects, one layer behind the creatures and one in front.
        let secs = model.fx.value
        let moving = secs > 0 && secs < M.fxSeconds
        let gpu = gpuParticles
        if moving {
            scene.signatureFront = signature(layer: 1, secs: secs, layout: layout, poses: poses, gpu: gpu)
            if model.powerIndex == 2 && !gpu {
                scene.signatureBack = signature(layer: 0, secs: secs, layout: layout, poses: poses, gpu: gpu)
            }
        }

        // Hearts that rise from a petted creature.
        let alive = model.pet.value > 0 || (model.petUntil >= 0 && model.petClock < model.petUntil + 1.4)
        if alive {
            let headroom = 70.0
            let top = max(0, layout.heroTop - headroom)
            let height = layout.heroTop + layout.heroHeight - top
            let index = Int(M.round(M.wrapPosition(position))) % 3
            let head = Rig.rigs[index].joints[0].center
            let k = layout.creatureSize / 512
            let color = HabitatSpecimenCarouselSpecimen.hearts[index]
            scene.affection = (
                [
                    Self.f4(layout.width, height, (layout.width - layout.creatureSize) / 2 + head.0 * k, layout.heroTop - top + (layout.heroHeight - layout.creatureSize) / 2 + head.1 * k),
                    Self.f4(alive ? model.petClock : 0, min(model.petUntil, 1e6), layout.width / 393, reduced ? 1 : 0),
                    Self.f4(color.0, color.1, color.2, 0),
                ],
                SIMD4(0, Float(top), Float(layout.width), Float(height))
            )
        }

        // The particle world.
        if gpu { scene.particles = particleFrame(layout: layout, poses: poses) }
        renderer.render(scene)
        drawnOnce = true
    }

    /// Anatomy in source-art pixels: where each move leaves the body and where it aims.
    /// Charizard: the base of the drawn tail flame (embers leave its tip). Blastoise: the centre of
    /// each cannon's mouth, aimed down its barrel. Bulbasaur: the middle of the bulb's crown, and the bulb.
    private static let anchors: [(a: (Double, Double), b: (Double, Double), aimA: (Double, Double), aimB: (Double, Double))] = [
        ((452, 402), (452, 402), (0, -1), (0, -1)),
        ((99, 154), (409, 196), (-0.67, -0.74), (1, -0.06)),
        ((377, 152), (352, 215), (0, -1), (0, -1)),
    ]

    private func signature(layer: Double, secs: Double, layout: HabitatSpecimenCarouselLayout, poses: [(exchange: M.Exchange, pose: Rig.Pose, performing: Bool)], gpu: Bool) -> [SIMD4<Float>] {
        let index = model.powerIndex
        let unit = layout.creatureSize / 344
        let anchor = Self.anchors[index]
        let state = poses[index]
        let frame = body(index, layout, state)
        // Attached effects follow the posed body; their aim turns with it.
        let a = Rig.bodyPoint(frame, anchor.a.0, anchor.a.1)
        let b = Rig.bodyPoint(frame, anchor.b.0, anchor.b.1)
        let ta = Rig.bodyPoint(frame, anchor.a.0 + anchor.aimA.0 * 24, anchor.a.1 + anchor.aimA.1 * 24)
        let tb = Rig.bodyPoint(frame, anchor.b.0 + anchor.aimB.0 * 24, anchor.b.1 + anchor.aimB.1 * 24)
        // Particles are born at the resting body and travel with its world.
        let k = layout.creatureSize / 512
        let shift = state.exchange.drift * (M.exchangeDrift * layout.width)
        let emitA = (layout.creatureLeft + anchor.a.0 * k + shift, layout.heroTop + layout.creatureTop + anchor.a.1 * k)
        let emitB = (layout.creatureLeft + anchor.b.0 * k + shift, layout.heroTop + layout.creatureTop + anchor.b.1 * k)
        let floorY = layout.heroTop + layout.creatureTop + Rig.rigs[index].feet * k
        var leaves = (leaf: [SIMD4<Float>](repeating: .zero, count: 12), state: [SIMD4<Float>](repeating: .zero, count: 12))
        if index == 2 && !gpu {
            leaves = HabitatSpecimenCarouselParticles.leafUniforms(seconds: secs, originX: emitA.0, originY: emitA.1, floorY: floorY, unit: unit)
        }
        let incoming = (Int(M.wrapPosition(model.position).rounded(.down)) + 1) % 3
        var values: [SIMD4<Float>] = [
            Self.f4(layout.width, layout.overlayHeight, secs, Double(index)),
            Self.f4(layer, floorY, unit, layout.heroTop * 0.55),
            Self.f4(a.0, a.1, b.0, b.1),
            Self.f4(ta.0 - a.0, ta.1 - a.1, tb.0 - b.0, tb.1 - b.1),
            Self.f4(emitA.0, emitA.1, emitB.0, emitB.1),
            Self.f4(layout.width, layout.height, state.exchange.f, model.frontPhase),
            Self.f4(Double(state.exchange.role), Double(incoming), gpu ? 0 : 1, 0),
        ]
        values.append(contentsOf: leaves.leaf)
        values.append(contentsOf: leaves.state)
        return values
    }

    // MARK: The particle world's frame uniform (HabitatParticleField's frame callback)

    private static let emitters = anchors

    private func particleFrame(layout: HabitatSpecimenCarouselLayout, poses: [(exchange: M.Exchange, pose: Rig.Pose, performing: Bool)]) -> [Float] {
        var v = [Float](repeating: 0, count: 48)
        let scale = layout.scale
        let unit = layout.creatureSize / 344
        let k = layout.creatureSize / 512
        let stageTop = layout.heroTop - 22 * scale
        let stageBottom = layout.heroTop + layout.heroHeight + 8 * scale
        let drift = M.exchangeDrift * layout.width
        let dt = min(model.frameDelta, 50) / 1000
        let index = model.powerIndex
        let fx = model.fx.value
        let finger = model.touch
        contact += ((finger[4] > 0 ? 1 : 0) - contact) * min(1, dt * 14)
        frameNumber = (frameNumber + 1).truncatingRemainder(dividingBy: 1_000_000)
        func set(_ at: Int, _ values: Double...) { for (i, value) in values.enumerated() { v[at + i] = Float(value) } }
        let pixelScale = Double(renderer?.overlayView?.pixelScale ?? Float(UIScreen.main.scale))
        set(0, layout.width, layout.overlayHeight, pixelScale, dt)
        set(4, model.ambientTime * M.loop, model.position, frameNumber, Double.pi * 2 * model.ambientTime.truncatingRemainder(dividingBy: 1))
        set(8, layout.height, stageTop, stageBottom, drift)
        set(12, finger[0], finger[1] + layout.heroTop, finger[2], finger[3])
        set(16, contact, model.pet.value, 70 * scale, 1)
        let tapped = model.tapAt
        set(20, tapped[0], tapped[1] + layout.heroTop, tapped[2] > 0 ? (model.frameMS - tapped[2]) / 1000 : -1, 0)
        let landing = model.landing.value
        let landed = model.landingIndex
        set(24, layout.width / 2, layout.heroTop + layout.creatureTop + Rig.rigs[landed].feet * k, landing < 1 ? landing * M.landMS / 1000 : -1, 0)
        // Down follows the phone's tilt, so everything buoyant rises away from the ground it feels.
        let gx = model.tilt.0 * 0.8
        let length = hypot(gx, 1)
        set(28, gx / length, 1 / length, 0, 0)
        set(32, lastFx > fx ? -1 : lastFx, fx, Double(index), unit)
        lastFx = fx
        // Particles are born from the posed body at the moment they leave it.
        let emitter = Self.emitters[index]
        let state = poses[index]
        let frame = body(index, layout, state)
        let a = Rig.bodyPoint(frame, emitter.a.0, emitter.a.1)
        let b = Rig.bodyPoint(frame, emitter.b.0, emitter.b.1)
        let ta = Rig.bodyPoint(frame, emitter.a.0 + emitter.aimA.0 * 24, emitter.a.1 + emitter.aimA.1 * 24)
        let tb = Rig.bodyPoint(frame, emitter.b.0 + emitter.aimB.0 * 24, emitter.b.1 + emitter.aimB.1 * 24)
        set(36, a.0, a.1, ta.0 - a.0, ta.1 - a.1)
        set(40, b.0, b.1, tb.0 - b.0, tb.1 - b.1)
        var tip = a
        if let flame = Rig.rigs[0].flame, index == 0 {
            tip = Rig.bodyPoint(frame, flame.base.0, flame.base.1 - flame.height * (1 + 1.25 * state.pose.flameGrow) * 0.95)
        }
        set(44, tip.0, tip.1, 15 * unit * (1 + 0.6 * state.pose.flameGrow), layout.heroTop + layout.creatureTop + Rig.rigs[index].feet * k)
        return v
    }
}
