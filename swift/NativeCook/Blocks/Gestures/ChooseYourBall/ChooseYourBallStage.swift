import Combine
import RealityKit
import SwiftUI

// The ball itself: one model on a transparent RealityKit stage, lit by the warehouse and four
// lights, turned by the carousel and looked over by the tilt. Mirrors BallStage3D.tsx.

struct ChooseYourBallStage: UIViewRepresentable {
    let carousel: ChooseYourBallCarousel
    let tilt: ChooseYourBallTilt
    /// The ball whose paint the shell wears.
    let ball: ChooseYourBallID
    let reduceMotion: Bool

    func makeCoordinator() -> ChooseYourBallStageScene {
        ChooseYourBallStageScene(carousel: carousel, tilt: tilt)
    }

    func makeUIView(context: Context) -> ARView {
        context.coordinator.makeView()
    }

    func updateUIView(_ view: ARView, context: Context) {
        context.coordinator.reduceMotion = reduceMotion
        context.coordinator.show(ball)
    }

    static func dismantleUIView(_ view: ARView, coordinator: ChooseYourBallStageScene) {
        coordinator.tearDown()
    }
}

@MainActor
final class ChooseYourBallStageScene {
    typealias Contract = ChooseYourBallStageContract

    var reduceMotion = false

    private let carousel: ChooseYourBallCarousel
    private let tilt: ChooseYourBallTilt
    private weak var view: ARView?
    private let camera = PerspectiveCamera()
    /// Parent of the model: the carousel turns this, never the lights.
    private let turntable = Entity()
    private let rim = PointLight()
    private let rimBack = PointLight()
    private var parts: [Contract.Part: ModelEntity] = [:]
    private var paints: [ChooseYourBallID: TextureResource] = [:]
    private var upperMaterial = PhysicallyBasedMaterial()
    /// The ball asked for, and the one the shell is wearing.
    private var wanted: ChooseYourBallID?
    private var shown: ChooseYourBallID?
    /// Both cancel themselves when the scene object is released, and in `tearDown` before that.
    private var frames: AnyCancellable?
    private var loading: AnyCancellable?

    init(carousel: ChooseYourBallCarousel, tilt: ChooseYourBallTilt) {
        self.carousel = carousel
        self.tilt = tilt
    }

    func makeView() -> ARView {
        let view = ARView(frame: .zero, cameraMode: .nonAR, automaticallyConfigureSession: false)
        // Only the ball is drawn; the SwiftUI backdrop shows through everywhere else.
        view.environment.background = .color(.clear)
        view.backgroundColor = .clear
        view.isOpaque = false
        view.isUserInteractionEnabled = false
        view.renderOptions = [
            .disableMotionBlur, .disableDepthOfField, .disableCameraGrain, .disableGroundingShadows,
            .disablePersonOcclusion, .disableFaceMesh, .disableAREnvironmentLighting,
        ]
        // Hidden until the model, the room and the paints are in.
        view.alpha = 0
        self.view = view

        let anchor = AnchorEntity(world: .zero)
        view.scene.addAnchor(anchor)

        camera.camera.fieldOfViewInDegrees = Contract.cameraFieldOfView
        camera.camera.near = Contract.cameraNear
        camera.camera.far = Contract.cameraFar
        anchor.addChild(camera)

        let key = DirectionalLight()
        key.light.color = .white
        key.light.intensity = Contract.keyLux
        key.look(at: .zero, from: Contract.keyPosition, relativeTo: nil)
        anchor.addChild(key)

        let fill = DirectionalLight()
        fill.light.color = ChooseYourBallRGB(hex: Contract.fillColor).uiColor
        fill.light.intensity = Contract.fillLux
        fill.look(at: .zero, from: Contract.fillPosition, relativeTo: nil)
        anchor.addChild(fill)

        rim.position = Contract.rimPosition
        rim.light.intensity = Contract.rimLumens
        rim.light.attenuationRadius = Contract.rimFalloffRadius
        anchor.addChild(rim)

        rimBack.position = Contract.rimBackPosition
        rimBack.light.intensity = Contract.rimBackLumens
        rimBack.light.attenuationRadius = Contract.rimFalloffRadius
        anchor.addChild(rimBack)

        anchor.addChild(turntable)

        frames = AnyCancellable(
            view.scene.subscribe(to: SceneEvents.Update.self) { [weak self] _ in
                self?.frame()
            }
        )
        frame()
        load()
        return view
    }

    /// The view is going away or being rebuilt: no more frames, and a load still under way is
    /// dropped.
    func tearDown() {
        frames = nil
        loading = nil
    }

    /// Dresses the shell as `ball`: its paint, its raised markings, its rim-light colour.
    func show(_ ball: ChooseYourBallID) {
        wanted = ball
        guard ball != shown, let upper = parts[.upper], let paint = paints[ball] else { return }
        shown = ball

        var material = upperMaterial
        material.baseColor = .init(tint: .white, texture: .init(paint))
        upper.model?.materials = [material]

        let visible = Contract.marks(for: ball)
        for (part, entity) in parts where Contract.markPaint[part] != nil {
            entity.isEnabled = visible.contains(part)
        }

        let aura = ChooseYourBallRGB(hex: ball.spec.fx.aura).uiColor
        rim.light.color = aura
        rimBack.light.color = aura
    }

    // MARK: Per frame

    private func frame() {
        // One whole turn per slot, so the button is home whenever the loop rests on a ball
        // (ballStageMotion.ts TURN_PER_SLOT). Folded first: the offset grows without bound.
        let turn = carousel.offset - carousel.offset.rounded(.down)
        turntable.orientation = simd_quatf(angle: Float(-turn * 2 * .pi), axis: [0, 1, 0])

        let position = Contract.cameraPosition(tilt: reduceMotion ? 0 : Float(tilt.value))
        camera.look(at: .zero, from: position, relativeTo: nil)
    }

    // MARK: Loading

    /// Everything is read off the main thread, and all 26 paints up front, so neither opening
    /// the screen nor a swipe ever waits for a file.
    ///
    /// The resources are the reference's, converted for RealityKit:
    /// - choose-your-ball.usdz is ball-filament.glb with its eight parts, scale and UVs.
    /// - choose-your-ball-warehouse.skybox is the reference's HDR room, turned to the way
    ///   RealityKit reads an equirectangular image so the lamps reflect where they do there.
    /// - choose-your-ball-shell-<id>.png are the reference's 26 shell paints, repainted. The
    ///   reference shows a paint through Filament's ACES tone mapper, which darkens and
    ///   desaturates it by an amount that depends on the colour; RealityKit's curve is fixed
    ///   and different, and a material's tint can only scale a colour. So each texel holds the
    ///   albedo that makes RealityKit show the colour the reference shows for that paint. They
    ///   are tagged sRGB: untagged, RealityKit takes them for the display's wider gamut.
    private func load() {
        let bundle = Bundle.main
        guard
            let modelURL = bundle.url(forResource: "choose-your-ball", withExtension: "usdz"),
            let rampURL = bundle.url(forResource: "choose-your-ball-occlusion-ramp", withExtension: "png")
        else { return }

        let model = Entity.loadAsync(contentsOf: modelURL)
        let room = EnvironmentResource.loadAsync(named: "choose-your-ball-warehouse")
        // The ramp is data, not colour: its texels are the occlusion values themselves.
        let ramp = TextureResource.loadAsync(contentsOf: rampURL, options: .init(semantic: .raw))
        let paints = Publishers.MergeMany(
            ChooseYourBallID.allCases.compactMap { ball in
                bundle.url(forResource: "choose-your-ball-shell-\(ball.rawValue)", withExtension: "png").map { url in
                    TextureResource.loadAsync(contentsOf: url, options: .init(semantic: .color)).map { (ball, $0) }
                }
            }
        ).collect()

        loading = Publishers.Zip4(model, room, ramp, paints)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] model, room, ramp, paints in
                    self?.assemble(model: model, room: room, ramp: ramp, paints: paints)
                }
            )
    }

    private func assemble(
        model: Entity, room: EnvironmentResource, ramp: TextureResource, paints: [(ChooseYourBallID, TextureResource)]
    ) {
        guard let view else { return }
        view.environment.lighting.resource = room
        view.environment.lighting.intensityExponent = Contract.environmentExponent
        self.paints = Dictionary(uniqueKeysWithValues: paints)

        for part in Contract.Part.allCases {
            parts[part] = model.findEntity(named: part.rawValue)?.chooseYourBallModel
        }

        // The model carries its baked ambient occlusion as texture coordinates into `ramp`
        // (the reference has it as vertex colours, which this material cannot read): the upper
        // shell in its second set, under the paint; every other part in its first, as its colour.
        upperMaterial = material(roughness: Contract.shellRoughness, metallic: Contract.shellMetallic)
        var occlusion = MaterialParameters.Texture(ramp)
        occlusion.uvIndex = 1
        upperMaterial.ambientOcclusion = .init(texture: occlusion)

        var hardware = material(roughness: Contract.shellRoughness, metallic: 0)
        hardware.baseColor = .init(tint: ChooseYourBallRGB(hex: Contract.hardwareLit).uiColor, texture: .init(ramp))
        hardware.emissiveColor = .init(color: .white, texture: .init(ramp))
        hardware.emissiveIntensity = Contract.hardwareGlow
        parts[.lower]?.model?.materials = [hardware]
        parts[.button]?.model?.materials = [hardware]

        var band = material(roughness: Contract.bandRoughness, metallic: 0)
        band.baseColor = .init(tint: .black)
        parts[.band]?.model?.materials = [band]

        for (part, paint) in Contract.markPaint {
            var mark = material(
                roughness: Contract.markRoughness,
                metallic: part == .markMasterEmblem ? 0 : Contract.markMetallic
            )
            mark.baseColor = .init(tint: ChooseYourBallRGB(hex: paint).uiColor, texture: .init(ramp))
            parts[part]?.model?.materials = [mark]
            parts[part]?.scale = SIMD3(repeating: Contract.markLift(part))
        }

        turntable.addChild(model)
        if let wanted { show(wanted) }
        UIView.animate(withDuration: 0.2) { view.alpha = 1 }
    }

    private func material(roughness: Float, metallic: Float) -> PhysicallyBasedMaterial {
        var material = PhysicallyBasedMaterial()
        material.roughness = .init(floatLiteral: roughness)
        material.metallic = .init(floatLiteral: metallic)
        material.specular = .init(floatLiteral: Contract.specular)
        return material
    }
}

private extension Entity {
    /// The entity that carries the mesh: this one, or the first below it.
    var chooseYourBallModel: ModelEntity? {
        if let model = self as? ModelEntity { return model }
        for child in children {
            if let model = child.chooseYourBallModel { return model }
        }
        return nil
    }
}
