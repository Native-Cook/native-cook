import MetalKit
import UIKit
import simd

/// Everything the three Metal layers draw in one frame, computed once from the motion state.
struct HabitatSpecimenCarouselScene {
    struct Creature {
        var visible = false
        var uniforms = HabitatSpecimenCarouselCreatureUniforms()
        /// The canvas' top-left on screen (points) and its side, before the exchange's zoom.
        var origin = SIMD2<Float>(0, 0)
        var side: Float = 0
        var zoom: Float = 1
    }

    /// The screen, and the height of the layers that cover only the top of it.
    var width = 393.0
    var height = 852.0
    var overlayHeight = 400.0
    var env = [SIMD4<Float>](repeating: .zero, count: 4)
    var creatures = [Creature](repeating: Creature(), count: 3)
    var signatureBack: [SIMD4<Float>]?
    var signatureFront: [SIMD4<Float>]?
    var affection: (uniforms: [SIMD4<Float>], rect: SIMD4<Float>)?
    /// The particle world's frame uniform (48 floats); nil while the world is not running.
    var particles: [Float]?
}

/// Which of the three stacked layers a Metal view draws.
enum HabitatSpecimenCarouselLayer {
    /// Habitat, particles behind the creatures, leaves that fall behind Bulbasaur.
    case environment
    /// The three creatures.
    case creatures
    /// Hearts, what a move throws in front of the body, particles in front of the creatures.
    case overlay
}

/// One CAMetalLayer; the engine tells it when to draw.
final class HabitatSpecimenCarouselMetalView: MTKView, MTKViewDelegate {
    let layerRole: HabitatSpecimenCarouselLayer
    var encode: ((HabitatSpecimenCarouselMetalView) -> Void)?
    var onLayout: (() -> Void)?
    private var lastSize = CGSize.zero

    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds.size != lastSize { lastSize = bounds.size; onLayout?() }
    }

    init(role: HabitatSpecimenCarouselLayer, device: MTLDevice?) {
        layerRole = role
        super.init(frame: .zero, device: device)
        isOpaque = role == .environment
        backgroundColor = role == .environment ? UIColor(red: 0.067, green: 0.114, blue: 0.098, alpha: 1) : .clear
        clearColor = MTLClearColorMake(0, 0, 0, role == .environment ? 1 : 0)
        colorPixelFormat = .bgra8Unorm
        framebufferOnly = true
        isPaused = true
        enableSetNeedsDisplay = false
        autoResizeDrawable = true
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        delegate = self
        contentScaleFactor = UIScreen.main.scale
        (layer as? CAMetalLayer)?.colorspace = HabitatSpecimenCarouselRenderer.colorSpace
    }

    required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) { encode?(self) }

    var pixelScale: Float { bounds.width > 0 ? Float(drawableSize.width / bounds.width) : Float(contentScaleFactor) }
}

@MainActor
final class HabitatSpecimenCarouselRenderer {
    /// What the shaders' numbers mean as colours. The reference draws through Skia, whose output on
    /// iOS is read as Display P3.
    static let colorSpace: CGColorSpace? = CGColorSpace(name: CGColorSpace.displayP3)

    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let environment: MTLRenderPipelineState
    private let creature: MTLRenderPipelineState
    private let affection: MTLRenderPipelineState
    private let signature: MTLRenderPipelineState
    private let mote: MTLRenderPipelineState
    private let simulate: MTLComputePipelineState
    private let particleBuffer: MTLBuffer
    private let textures: HabitatSpecimenCarouselTextures

    /// Called once a layer's view exists and has a size, so a frozen frame gets drawn.
    var onViewReady: (() -> Void)?
    var environmentView: HabitatSpecimenCarouselMetalView?
    var creatureView: HabitatSpecimenCarouselMetalView?
    var overlayView: HabitatSpecimenCarouselMetalView?
    private(set) var scene = HabitatSpecimenCarouselScene()
    /// The overlay has been cleared since it last had something to show.
    private var overlayBlank = false
    private var particlesStepped = false

    init?() {
        guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue(), let library = device.makeDefaultLibrary(),
              let textures = HabitatSpecimenCarouselTextures(device: device, queue: queue, library: library) else { return nil }
        func pipeline(vertex: String, fragment: String, blend: Bool) -> MTLRenderPipelineState? {
            guard let v = library.makeFunction(name: vertex), let f = library.makeFunction(name: fragment) else { return nil }
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = v
            d.fragmentFunction = f
            d.colorAttachments[0].pixelFormat = .bgra8Unorm
            if blend {
                d.colorAttachments[0].isBlendingEnabled = true
                d.colorAttachments[0].sourceRGBBlendFactor = .one
                d.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
                d.colorAttachments[0].sourceAlphaBlendFactor = .one
                d.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            }
            return try? device.makeRenderPipelineState(descriptor: d)
        }
        guard let environment = pipeline(vertex: "habitatSCRegionVertex", fragment: "habitatSCEnvironment", blend: false),
              let creature = pipeline(vertex: "habitatSCCreatureVertex", fragment: "habitatSCCreature", blend: true),
              let affection = pipeline(vertex: "habitatSCRegionVertex", fragment: "habitatSCAffection", blend: true),
              let signature = pipeline(vertex: "habitatSCRegionVertex", fragment: "habitatSCSignature", blend: true),
              let mote = pipeline(vertex: "habitatSCMoteVertex", fragment: "habitatSCMoteFragment", blend: true),
              let function = library.makeFunction(name: "habitatSCParticleSimulate"),
              let simulate = try? device.makeComputePipelineState(function: function),
              let buffer = device.makeBuffer(length: Self.particleCount * Self.particleStride, options: .storageModeShared) else { return nil }
        memset(buffer.contents(), 0, buffer.length)
        self.device = device
        self.queue = queue
        self.environment = environment
        self.creature = creature
        self.affection = affection
        self.signature = signature
        self.mote = mote
        self.simulate = simulate
        self.particleBuffer = buffer
        self.textures = textures
    }

    static let particleCount = 520
    /// 18 floats per particle (HabitatSCParticle).
    static let particleStride = 72

    func makeView(_ role: HabitatSpecimenCarouselLayer) -> HabitatSpecimenCarouselMetalView {
        let view = HabitatSpecimenCarouselMetalView(role: role, device: device)
        view.encode = { [weak self] view in self?.encode(view) }
        view.onLayout = { [weak self] in self?.onViewReady?() }
        switch role {
        case .environment: environmentView = view
        case .creatures: creatureView = view
        case .overlay: overlayView = view
        }
        return view
    }

    /// Takes the frame's scene and draws all three layers from it.
    func render(_ next: HabitatSpecimenCarouselScene) {
        scene = next
        particlesStepped = false
        environmentView?.draw()
        creatureView?.draw()
        let overlayHasContent = next.signatureFront != nil || next.affection != nil || next.particles != nil
        if overlayHasContent || !overlayBlank {
            overlayView?.draw()
            overlayBlank = !overlayHasContent
        }
    }

    // MARK: Encoding

    private func encode(_ view: HabitatSpecimenCarouselMetalView) {
        guard view.bounds.width > 0, let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let commands = queue.makeCommandBuffer() else { return }
        let scale = view.pixelScale
        let viewport = SIMD4<Float>(Float(view.bounds.width), Float(view.bounds.height), scale, 0)
        // One compute pass steps the particle world per frame, ahead of the first layer that draws it.
        if let frame = scene.particles, !particlesStepped, let compute = commands.makeComputeCommandEncoder() {
            particlesStepped = true
            compute.setComputePipelineState(simulate)
            var values = frame
            compute.setBytes(&values, length: values.count * MemoryLayout<Float>.stride, index: 0)
            compute.setBuffer(particleBuffer, offset: 0, index: 1)
            let width = 64
            compute.dispatchThreadgroups(MTLSize(width: (Self.particleCount + width - 1) / width, height: 1, depth: 1),
                                         threadsPerThreadgroup: MTLSize(width: width, height: 1, depth: 1))
            compute.endEncoding()
        }
        guard let encoder = commands.makeRenderCommandEncoder(descriptor: pass) else { return }
        switch view.layerRole {
        case .environment:
            drawRegion(encoder, environment, SIMD4(0, 0, Float(scene.width), Float(scene.height)), viewport, scene.env)
            if let frame = scene.particles { drawMotes(encoder, frame, layer: 0, viewport: viewport) }
            if let back = scene.signatureBack {
                drawRegion(encoder, signature, SIMD4(0, 0, Float(scene.width), Float(scene.overlayHeight)), viewport, back)
            }
        case .creatures:
            drawCreatures(encoder, viewport)
        case .overlay:
            if let hearts = scene.affection { drawRegion(encoder, affection, hearts.rect, viewport, hearts.uniforms) }
            if let front = scene.signatureFront {
                drawRegion(encoder, signature, SIMD4(0, 0, Float(scene.width), Float(scene.overlayHeight)), viewport, front)
            }
            if let frame = scene.particles { drawMotes(encoder, frame, layer: 1, viewport: viewport) }
        }
        encoder.endEncoding()
        commands.present(drawable)
        commands.commit()
    }

    private func drawRegion(_ encoder: MTLRenderCommandEncoder, _ pipeline: MTLRenderPipelineState, _ rect: SIMD4<Float>, _ viewport: SIMD4<Float>, _ uniforms: [SIMD4<Float>]) {
        var region = (rect, viewport)
        encoder.setRenderPipelineState(pipeline)
        encoder.setVertexBytes(&region, length: MemoryLayout.size(ofValue: region), index: 0)
        encoder.setFragmentBytes(&region, length: MemoryLayout.size(ofValue: region), index: 0)
        var values = uniforms
        encoder.setFragmentBytes(&values, length: values.count * MemoryLayout<SIMD4<Float>>.stride, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
    }

    private func drawCreatures(_ encoder: MTLRenderCommandEncoder, _ viewport: SIMD4<Float>) {
        encoder.setRenderPipelineState(creature)
        for (index, body) in scene.creatures.enumerated() where body.visible {
            var quad = (SIMD4<Float>(body.origin.x, body.origin.y, body.side, body.side), SIMD4<Float>(viewport.x, viewport.y, viewport.z, body.zoom))
            encoder.setVertexBytes(&quad, length: MemoryLayout.size(ofValue: quad), index: 0)
            encoder.setFragmentBytes(&quad, length: MemoryLayout.size(ofValue: quad), index: 0)
            var uniforms = body.uniforms
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<HabitatSpecimenCarouselCreatureUniforms>.stride, index: 1)
            encoder.setFragmentTexture(textures.images[index], index: 0)
            encoder.setFragmentTexture(textures.normals[index], index: 1)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
        }
    }

    /// The instanced particle quads of one layer; the canvas is the screen's top, `overlayHeight` tall.
    private func drawMotes(_ encoder: MTLRenderCommandEncoder, _ frame: [Float], layer: Float, viewport: SIMD4<Float>) {
        let scale = Double(viewport.z)
        encoder.setViewport(MTLViewport(originX: 0, originY: 0, width: Double(scene.width) * scale, height: Double(scene.overlayHeight) * scale, znear: 0, zfar: 1))
        encoder.setRenderPipelineState(mote)
        var values = frame
        encoder.setVertexBytes(&values, length: values.count * MemoryLayout<Float>.stride, index: 0)
        var which = SIMD4<Float>(layer, 0, 0, 0)
        encoder.setVertexBytes(&which, length: MemoryLayout<SIMD4<Float>>.stride, index: 1)
        encoder.setVertexBuffer(particleBuffer, offset: 0, index: 2)
        encoder.setFragmentBytes(&values, length: values.count * MemoryLayout<Float>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: Self.particleCount)
        encoder.setViewport(MTLViewport(originX: 0, originY: 0, width: Double(viewport.x) * scale, height: Double(viewport.y) * scale, znear: 0, zfar: 1))
    }
}
