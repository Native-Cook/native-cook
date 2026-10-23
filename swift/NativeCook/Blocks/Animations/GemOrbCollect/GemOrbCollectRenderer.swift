import Metal
import MetalKit
import QuartzCore
import SwiftUI
import UIKit
import simd

struct GemOrbCollectMetalUniforms {
    var size = SIMD2<Float>(repeating: 0)
    var displayScale: Float = 1
    var time: Float = 0
    var gem = SIMD4<Float>(repeating: 0)
    var attitude = SIMD4<Float>(repeating: 0)
    var effects = SIMD4<Float>(repeating: 0)
    var capsule = SIMD4<Float>(repeating: 0)
    var button = SIMD4<Float>(repeating: 0)
    var mini = SIMD4<Float>(repeating: 0)
    var state = SIMD4<Float>(repeating: 0)
    var ping = SIMD4<Float>(repeating: 0)
    var row0 = SIMD4<Float>(repeating: 0)
    var row1 = SIMD4<Float>(repeating: 0)
    var row2 = SIMD4<Float>(repeating: 0)
    var miniRow0 = SIMD4<Float>(repeating: 0)
    var miniRow1 = SIMD4<Float>(repeating: 0)
    var miniRow2 = SIMD4<Float>(repeating: 0)
    var optics = SIMD4<Float>(repeating: 0)
    var backdrop = SIMD4<Float>(repeating: 0)
    var pillBar = SIMD4<Float>(repeating: 0)
    var pillCapsule = SIMD4<Float>(repeating: 0)
    var coloursTop = SIMD4<Float>(repeating: 0)
    var coloursMid = SIMD4<Float>(repeating: 0)
    var coloursBloom = SIMD4<Float>(repeating: 0)
    var coloursDust = SIMD4<Float>(repeating: 0)
}

@MainActor
final class GemOrbCollectModel: ObservableObject {
    @Published private(set) var presentation = GemOrbCollectPresentation()

    var autoPlay: Bool
    var reduceMotion = false
    @Published private(set) var layout: GemOrbCollectLayout?
    private var simulation: GemOrbCollectSimulation?
    private var input = GemOrbCollectInput()
    private var touching = false
    private var touchStart = CGPoint.zero
    private var previousTouch = CGPoint.zero
    private var previousTouchTime = 0.0
    private var touchStartTime = 0.0
    private var velocity = CGVector.zero
    private var isGemTouch = false
    private var isBarTouch = false
    private var previousPresentation = GemOrbCollectPresentation()
    private var lastHitHaptic = -10.0

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    func configure(size: CGSize, safeInsets: UIEdgeInsets) {
        guard size.width > 0, size.height > 0 else { return }
        let next = GemOrbCollectLayout(size: size, safeTop: safeInsets.top, safeBottom: safeInsets.bottom)
        layout = next
        if simulation == nil {
            simulation = GemOrbCollectSimulation(layout: next)
        } else {
            simulation?.resize(next, reduced: reduceMotion)
        }
    }

    func advance(seconds: Double) -> GemOrbCollectRenderState? {
        guard var simulation else { return nil }
        simulation.advance(seconds: seconds, input: &input, autoPlay: autoPlay, reduced: reduceMotion, touching: touching)
        self.simulation = simulation
        let render = simulation.renderState(reduced: reduceMotion)
        var events = simulation.audibleEvents
        if events & GemOrbCollectSimulation.eventHit != 0 {
            if render.time - lastHitHaptic < 0.045 {
                events &= ~GemOrbCollectSimulation.eventHit
            } else {
                lastHitHaptic = render.time
            }
        }
        if events != 0 {
            let style: UIImpactFeedbackGenerator.FeedbackStyle
            if events & GemOrbCollectSimulation.eventBurst != 0 { style = .heavy }
            else if events & GemOrbCollectSimulation.eventLand != 0 { style = .rigid }
            else if events & GemOrbCollectSimulation.eventRewind != 0 { style = .medium }
            else if events & GemOrbCollectSimulation.eventHit != 0 && simulation.hits > 8 { style = .soft }
            else { style = .light }
            UIImpactFeedbackGenerator(style: style).impactOccurred()
        }
        let next = simulation.presentation(reduced: reduceMotion)
        if next != previousPresentation {
            previousPresentation = next
            presentation = next
        }
        return render
    }

    func touchBegan(_ point: CGPoint, time: TimeInterval) {
        guard let layout else { return }
        touching = true
        simulation?.interruptDemo()
        touchStart = point
        previousTouch = point
        touchStartTime = time
        previousTouchTime = time
        velocity = .zero
        isGemTouch = hypot(point.x - layout.gemX, point.y - layout.gemY) <= layout.gemRadius * 1.25
        isBarTouch = layout.button.contains(point)
        if isGemTouch {
            input.gemDown = true
            input.holdMoved = false
            input.touch = CGPoint(x: (point.x - layout.gemX) / layout.gemRadius, y: -(point.y - layout.gemY) / layout.gemRadius)
        }
        if isBarTouch {
            input.barDown = true
        }
    }

    func touchMoved(_ point: CGPoint, time: TimeInterval) {
        guard isGemTouch else { return }
        let delta = CGVector(dx: point.x - previousTouch.x, dy: point.y - previousTouch.y)
        input.drag.dx += delta.dx
        input.drag.dy += delta.dy
        input.holdMoved = input.holdMoved || hypot(point.x - touchStart.x, point.y - touchStart.y) > 8
        let dt = max(1.0 / 240, time - previousTouchTime)
        velocity = CGVector(dx: delta.dx / dt, dy: delta.dy / dt)
        previousTouch = point
        previousTouchTime = time
    }

    func touchEnded(_ point: CGPoint, time: TimeInterval, cancelled: Bool) {
        guard let layout else { return }
        if isGemTouch {
            let moved = hypot(point.x - touchStart.x, point.y - touchStart.y)
            let age = time - touchStartTime
            input.gemDown = false
            input.release = reduceMotion || time - previousTouchTime > 0.1 ? .zero : velocity
            if !cancelled, age < 0.28, moved <= 8 {
                input.tap = true
                input.touch = CGPoint(x: (point.x - layout.gemX) / layout.gemRadius, y: -(point.y - layout.gemY) / layout.gemRadius)
            }
        }
        if isBarTouch {
            input.barDown = false
            if !cancelled, layout.button.contains(point) {
                if presentation.phase == .before { input.collect = true }
                if presentation.phase == .after { input.replay = true }
            }
        }
        touching = false
        isGemTouch = false
        isBarTouch = false
    }

    func accessibilityActivateGem() {
        simulation?.interruptDemo()
        if presentation.phase == .before {
            input.collect = true
        } else {
            input.tap = true
        }
    }

    func accessibilityActivateBar() {
        simulation?.interruptDemo()
        if presentation.phase == .before { input.collect = true }
        if presentation.phase == .after { input.replay = true }
    }


}

final class GemOrbCollectMetalView: MTKView, MTKViewDelegate {
    weak var model: GemOrbCollectModel?
    private var commandQueue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var orbAtlas: MTLTexture?
    private var lastFrameTime: CFTimeInterval?
    private var previousAttitude: (yaw: Double, pitch: Double, time: Double)?
    private var opticalSpeed = 0.0
    private var displayScale: CGFloat = 1

    init(model: GemOrbCollectModel) {
        let device = MTLCreateSystemDefaultDevice()
        super.init(frame: .zero, device: device)
        self.model = model
        commandQueue = device?.makeCommandQueue()
        colorPixelFormat = .bgra8Unorm
        framebufferOnly = true
        isOpaque = true
        backgroundColor = .black
        preferredFramesPerSecond = 60
        enableSetNeedsDisplay = false
        isPaused = false
        delegate = self
        isMultipleTouchEnabled = false
        accessibilityIgnoresInvertColors = true
        buildPipeline()
        if let device, let url = Bundle.main.url(forResource: "orb-atlas", withExtension: "png") {
            orbAtlas = try? MTKTextureLoader(device: device).newTexture(
                URL: url,
                options: [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft]
            )
        }
    }

    @available(*, unavailable)
    required init(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        displayScale = window?.screen.scale ?? UIScreen.main.scale
        contentScaleFactor = displayScale
        drawableSize = CGSize(width: bounds.width * displayScale, height: bounds.height * displayScale)
        configureModel()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        drawableSize = CGSize(width: bounds.width * displayScale, height: bounds.height * displayScale)
        configureModel()
    }

    private func configureModel() {
        let insets = safeAreaInsets
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.model?.configure(size: self.bounds.size, safeInsets: insets)
        }
    }

    private func buildPipeline() {
        guard let device,
              let library = device.makeDefaultLibrary(),
              let vertex = library.makeFunction(name: "gemOrbCollectVertex"),
              let fragment = library.makeFunction(name: "gemOrbCollectFragment") else {
            assertionFailure("Gem Orb Collect Metal functions are unavailable")
            return
        }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        descriptor.colorAttachments[0].pixelFormat = colorPixelFormat
        do {
            pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            assertionFailure("Gem Orb Collect pipeline failed: \(error)")
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let drawable = currentDrawable,
              let pass = currentRenderPassDescriptor,
              let pipeline,
              let commandQueue,
              let commandBuffer = commandQueue.makeCommandBuffer(),
              let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: pass) else { return }
        let now = CACurrentMediaTime()
        let dt = min(max(lastFrameTime.map { now - $0 } ?? (1.0 / 60), 0), 0.05)
        lastFrameTime = now
        guard let render = model?.advance(seconds: dt) else { return }
        var uniforms = metalUniforms(render)
        var orbs = render.orbs
        while orbs.count < 72 * 2 { orbs.append(.zero) }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<GemOrbCollectMetalUniforms>.stride, index: 0)
        orbs.withUnsafeBytes { bytes in
            guard let baseAddress = bytes.baseAddress else { return }
            encoder.setFragmentBytes(baseAddress, length: bytes.count, index: 1)
        }
        let planes = GemOrbCollectCut.planes
        planes.withUnsafeBytes { bytes in
            if let address = bytes.baseAddress {
                encoder.setFragmentBytes(address, length: bytes.count, index: 2)
            }
        }
        let outline = GemOrbCollectCut.outline(row0: uniforms.row0, row1: uniforms.row1, row2: uniforms.row2)
        outline.withUnsafeBytes { bytes in
            if let address = bytes.baseAddress {
                encoder.setFragmentBytes(address, length: bytes.count, index: 3)
            }
        }
        let miniOutline = GemOrbCollectCut.outline(
            row0: uniforms.miniRow0, row1: uniforms.miniRow1, row2: uniforms.miniRow2
        )
        miniOutline.withUnsafeBytes { bytes in
            if let address = bytes.baseAddress {
                encoder.setFragmentBytes(address, length: bytes.count, index: 6)
            }
        }
        let lights = render.lights
        lights.withUnsafeBytes { bytes in
            if let address = bytes.baseAddress {
                encoder.setFragmentBytes(address, length: bytes.count, index: 4)
            }
        }
        let glints = GemOrbCollectCut.glints(
            row0: uniforms.row0, row1: uniforms.row1, row2: uniforms.row2,
            centreX: render.centreX, centreY: render.centreY, radius: render.radius,
            stretch: render.stretch, charge: render.charge, flare: render.flare,
            time: render.time
        )
        glints.withUnsafeBytes { bytes in
            if let address = bytes.baseAddress {
                encoder.setFragmentBytes(address, length: bytes.count, index: 5)
            }
        }
        encoder.setFragmentTexture(orbAtlas, index: 0)
        encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        encoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()
    }

    private func metalUniforms(_ render: GemOrbCollectRenderState) -> GemOrbCollectMetalUniforms {
        let layout = render.layout
        if let previousAttitude {
            let elapsed = render.time - previousAttitude.time
            if elapsed > 0, elapsed < 0.15 {
                let velocity = hypot(render.yaw - previousAttitude.yaw,
                                     render.pitch - previousAttitude.pitch) / elapsed
                opticalSpeed += (velocity - opticalSpeed) * (1 - exp(-elapsed * 12))
            } else if elapsed < 0 {
                opticalSpeed = 0
            }
        }
        previousAttitude = (render.yaw, render.pitch, render.time)
        let opticalExtra = max(render.flare, min(1, max(0, (opticalSpeed - 0.8) / 3)))
        let rows = GemOrbCollectCut.rows(yaw: render.yaw, pitch: render.pitch, roll: 0.22)
        let miniRows = GemOrbCollectCut.rows(yaw: render.miniYaw, pitch: 0.1, roll: 0.22)
        let outsideLights = render.lights.filter { $0.w > 0 }.count
        let insideLights = render.lights.filter { $0.w < 0 }.count
        return GemOrbCollectMetalUniforms(
            size: SIMD2(Float(layout.width), Float(layout.height)),
            displayScale: Float(displayScale),
            time: Float(render.time),
            gem: SIMD4(Float(render.centreX), Float(render.centreY), Float(render.radius), Float(render.yaw)),
            attitude: SIMD4(Float(render.pitch), 0.22, Float(render.phase.rawValue), Float(render.hits) / 20),
            effects: SIMD4(Float(render.charge), Float(render.flare), Float(render.hush), Float(render.shock)),
            capsule: SIMD4(Float(layout.capsule.minX), Float(layout.capsule.minY), Float(layout.capsule.width), Float(layout.capsule.height)),
            button: SIMD4(Float(layout.button.minX), Float(layout.button.minY), Float(layout.button.width), Float(layout.button.height)),
            mini: SIMD4(Float(layout.miniGemX), Float(layout.miniGemY), 12, Float(render.capsuleScale)),
            state: SIMD4(Float(render.beams), Float(render.value), Float(render.barScale), model?.reduceMotion == true ? 1 : 0),
            ping: render.ping,
            row0: rows.0,
            row1: rows.1,
            row2: rows.2,
            miniRow0: miniRows.0,
            miniRow1: miniRows.1,
            miniRow2: miniRows.2,
            optics: SIMD4(Float(render.stretch), 1, Float(2 + 3 * opticalExtra), Float(outsideLights)),
            backdrop: SIMD4(Float(layout.gemRadius), Float(render.time * Double.pi / 6), Float(insideLights), Float(render.miniFlare)),
            pillBar: SIMD4(Float(render.barPress), Float(render.barGlow), Float(render.barSheen), Float(render.barOpacity)),
            pillCapsule: SIMD4(0, Float(render.capsuleGlow), Float(render.capsuleSheen), 1),
            coloursTop: SIMD4(23 / 255, 23 / 255, 94 / 255, 0),
            coloursMid: SIMD4(10 / 255, 10 / 255, 48 / 255, 0),
            coloursBloom: SIMD4(42 / 255, 44 / 255, 224 / 255, 0),
            coloursDust: SIMD4(123 / 255, 92 / 255, 255 / 255, 0)
        )
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        model?.touchBegan(touch.location(in: self), time: touch.timestamp)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        model?.touchMoved(touch.location(in: self), time: touch.timestamp)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        model?.touchEnded(touch.location(in: self), time: touch.timestamp, cancelled: false)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        let touch = touches.first
        model?.touchEnded(touch?.location(in: self) ?? .zero, time: touch?.timestamp ?? CACurrentMediaTime(), cancelled: true)
    }
}

struct GemOrbCollectMetalSurface: UIViewRepresentable {
    @ObservedObject var model: GemOrbCollectModel

    func makeUIView(context: Context) -> GemOrbCollectMetalView {
        GemOrbCollectMetalView(model: model)
    }

    func updateUIView(_ view: GemOrbCollectMetalView, context: Context) {
        view.model = model
    }
}
