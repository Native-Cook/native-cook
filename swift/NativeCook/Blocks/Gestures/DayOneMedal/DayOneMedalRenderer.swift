import SwiftUI
import MetalKit
import Metal

struct DayOneMedalMetalView: UIViewRepresentable {
    @ObservedObject var model: DayOneMedalModel

    func makeCoordinator() -> DayOneMedalRenderer { DayOneMedalRenderer() }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.autoResizeDrawable = false
        view.isUserInteractionEnabled = false
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        let renderer = context.coordinator
        renderer.layout = model.layout
        renderer.engine = model.engine
        renderer.reduced = model.reduced
        renderer.ready = { [weak model] in model?.rendererReady = true }
        let size = CGSize(width: model.layout.width * renderer.ratio, height: model.layout.height * renderer.ratio)
        if view.drawableSize != size { view.drawableSize = size }
        view.draw()
    }

    static func dismantleUIView(_ uiView: MTKView, coordinator: DayOneMedalRenderer) {
        uiView.delegate = nil
        coordinator.ready = nil
    }
}

@MainActor
final class DayOneMedalRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    let ratio = 3.0
    var layout = DayOneMedalLayout()
    var engine = DayOneMedalEngine()
    var reduced = false
    var ready: (() -> Void)?
    private let queue: MTLCommandQueue
    private var medal: MTLRenderPipelineState!
    private var directMedal: MTLRenderPipelineState!
    private var crossfade: MTLRenderPipelineState!
    private var directSurface: MTLTexture!
    private var resolvedSurface: MTLTexture!
    private var gpuArrival: Double?
    private var leaf: MTLRenderPipelineState!
    private var leafGlow: MTLRenderPipelineState!
    private var strike: MTLRenderPipelineState!
    private var post: MTLRenderPipelineState!
    private var resolve: MTLRenderPipelineState!
    private var compute: MTLComputePipelineState!
    private var linear: MTLSamplerState!
    private var depthWrite: MTLDepthStencilState!
    private var depthRead: MTLDepthStencilState!
    private var faces: [MTLTexture] = []
    private var hdr: MTLTexture!
    private var emission: MTLTexture!
    private var depth: MTLTexture!
    private var down: [MTLTexture] = []
    private var up: [MTLTexture] = []
    private var blur: [MTLTexture] = []
    private var particles: MTLBuffer!
    private var seed: MTLBuffer!
    private var targetSize = CGSize.zero
    private var lastTime = -Double.infinity
    private var lastStrike = -Double.infinity
    private var lastHeat = 0.0
    private var inFlight = 0
    private var didReady = false
    private let count = 360

    override init() {
        device = MTLCreateSystemDefaultDevice()!
        queue = device.makeCommandQueue()!
        super.init()
        do { try prepare() }
        catch { fatalError("Day One Medal Metal setup: \(error)") }
    }

    private func prepare() throws {
        let library = device.makeDefaultLibrary()!
        func pipeline(_ vertex: String, _ fragment: String, _ format: MTLPixelFormat = .rgba16Float, blending: Int = 0, depth: Bool = true) throws -> MTLRenderPipelineState {
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: vertex)
            descriptor.fragmentFunction = library.makeFunction(name: fragment)
            descriptor.colorAttachments[0].pixelFormat = format
            if depth { descriptor.depthAttachmentPixelFormat = .depth32Float }
            if blending > 0 {
                let attachment = descriptor.colorAttachments[0]!
                attachment.isBlendingEnabled = true
                attachment.sourceRGBBlendFactor = .one
                attachment.sourceAlphaBlendFactor = .one
                attachment.destinationRGBBlendFactor = blending == 2 ? .one : .oneMinusSourceAlpha
                attachment.destinationAlphaBlendFactor = blending == 2 ? .one : .oneMinusSourceAlpha
            }
            return try device.makeRenderPipelineState(descriptor: descriptor)
        }
        medal = try pipeline("dayOneMedalFullscreen", "dayOneMedalSurface")
        directMedal = try pipeline("dayOneMedalFullscreen", "dayOneMedalSurface", .bgra8Unorm)
        leaf = try pipeline("dayOneMedalLeafVertex", "dayOneMedalLeaf", blending: 1)
        leafGlow = try pipeline("dayOneMedalLeafVertex", "dayOneMedalLeafBloom", blending: 2)
        strike = try pipeline("dayOneMedalStrikeVertex", "dayOneMedalStrike", blending: 1)
        post = try pipeline("dayOneMedalFullscreen", "dayOneMedalPost", depth: false)
        resolve = try pipeline("dayOneMedalFullscreen", "dayOneMedalPost", .bgra8Unorm, depth: false)
        crossfade = try pipeline("dayOneMedalFullscreen", "dayOneMedalCrossfade", .bgra8Unorm, depth: false)
        compute = try device.makeComputePipelineState(function: library.makeFunction(name: "dayOneMedalSimulate")!)
        let sampler = MTLSamplerDescriptor()
        sampler.minFilter = .linear
        sampler.magFilter = .linear
        sampler.sAddressMode = .clampToEdge
        sampler.tAddressMode = .clampToEdge
        linear = device.makeSamplerState(descriptor: sampler)
        let depthDescriptor = MTLDepthStencilDescriptor()
        depthDescriptor.depthCompareFunction = .less
        depthDescriptor.isDepthWriteEnabled = true
        depthWrite = device.makeDepthStencilState(descriptor: depthDescriptor)
        depthDescriptor.isDepthWriteEnabled = false
        depthRead = device.makeDepthStencilState(descriptor: depthDescriptor)
        for name in ["medal-front-1", "medal-front-7", "medal-front-30", "medal-back"] {
            let url = Bundle.main.url(forResource: "DayOneMedal-" + name, withExtension: "bin")!
            let bytes = try Data(contentsOf: url)
            precondition(bytes.count == 3072 * 956)
            let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .r8Unorm, width: 3072, height: 956, mipmapped: false)
            descriptor.storageMode = .shared
            descriptor.usage = .shaderRead
            let texture = device.makeTexture(descriptor: descriptor)!
            bytes.withUnsafeBytes { data in
                texture.replace(region: MTLRegionMake2D(0, 0, 3072, 956), mipmapLevel: 0, withBytes: data.baseAddress!, bytesPerRow: 3072)
            }
            faces.append(texture)
        }
    }

    private func texture(_ width: Int, _ height: Int, _ format: MTLPixelFormat = .rgba16Float) -> MTLTexture {
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: format, width: max(1, width), height: max(1, height), mipmapped: false)
        descriptor.storageMode = .private
        descriptor.usage = [.renderTarget, .shaderRead]
        return device.makeTexture(descriptor: descriptor)!
    }

    private func resize(_ size: CGSize) {
        targetSize = size
        let w = Int(size.width)
        let h = Int(size.height)
        directSurface = texture(w, h, .bgra8Unorm)
        resolvedSurface = texture(w, h, .bgra8Unorm)
        hdr = texture(w, h)
        emission = texture(w, h)
        depth = texture(w, h, .depth32Float)
        down = (1...4).map { texture(Int(ceil(Double(w) / pow(2, Double($0)))), Int(ceil(Double(h) / pow(2, Double($0))))) }
        up = down.prefix(3).map { texture($0.width, $0.height) }
        blur = [texture(down[3].width, down[3].height), texture(down[3].width, down[3].height)]
        // The source always seeds 900 leaves, then draws the selected quality tier's prefix.
        let data = DayOneMedalLeafSeed.make(count: 900, width: layout.width * ratio, height: layout.height * ratio,
            half: layout.half * ratio, centreY: (layout.sheetTop + layout.stageY) * ratio, ratio: ratio, centreX: layout.width / 2 * ratio)
        seed = device.makeBuffer(bytes: data, length: data.count * 4, options: .storageModeShared)
        particles = device.makeBuffer(length: data.count * 4, options: .storageModePrivate)
        lastStrike = -.infinity
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard inFlight < 2, view.drawableSize.width > 0, let drawable = view.currentDrawable,
              let command = queue.makeCommandBuffer() else { return }
        if targetSize != view.drawableSize { resize(view.drawableSize) }
        let s = engine.state
        let p = engine.present
        let shown = engine.opened
        let alive = shown && s.forged && p == 1
        let flightOrReduced = !shown || p < 1 || reduced || engine.drag != 0
        if flightOrReduced { gpuArrival = nil }
        else if gpuArrival == nil && !s.down && !s.hold && s.mode == .rest && s.heat < 0.001 && s.morphPhase == .idle {
            gpuArrival = s.clock
        }
        let fraction = gpuArrival.map { DayOneMedalPhysics.easeInOut(min(1, max(0, (s.clock - $0) / 0.1))) } ?? 0
        let direct = flightOrReduced || gpuArrival == nil
        var matrix = engine.rotation
        let retreat = 1 - 0.08 * p
        let backShift = layout.safeTop + 8 - layout.height * 0.04
        let sx = layout.width / 2 + (layout.slotX - layout.width / 2) * retreat
        let sy = layout.height / 2 + (layout.slotY - layout.height / 2) * retreat + backShift * p
        let flight = reduced && shown ? 1 : p
        let cx = shown ? sx + (layout.width / 2 - sx) * flight + s.offsetX * flight : layout.slotX
        let cy = shown ? sy + (layout.sheetTop + layout.stageY - sy) * flight - sin(.pi * flight) * 48 * layout.scale + (s.offsetY + engine.drag) * flight : layout.slotY
        let punch = reduced ? 1 : (1 - 0.03 * s.pressed) * (1 + 0.05 * s.heat)
        let small = layout.slotHalf * (1 - 0.08 * flight)
        let half = shown ? (small + (layout.half - small) * flight) * (1 + (punch - 1) * flight) : layout.slotHalf
        if !shown { matrix = DayOneMedalPhysics.rotation(0, 0, 0) }
        else if flight < 1 { matrix = DayOneMedalPhysics.rotation(2 * .pi * flight + s.yaw * flight, s.pitch * flight, s.roll * flight) }
        var uniforms = Array(repeating: Float(0), count: 48)
        func put(_ offset: Int, _ values: [Double]) {
            for (i, value) in values.enumerated() { uniforms[offset + i] = Float(value) }
        }
        let changedStrike = s.struck != lastStrike
        if changedStrike || s.clock < lastTime {
            let blit = command.makeBlitCommandEncoder()!
            blit.copy(from: seed, sourceOffset: 0, to: particles, destinationOffset: 0, size: seed.length)
            blit.endEncoding()
            lastHeat = 0
        }
        let dt = alive && !reduced ? max(0, min(s.clock - lastTime, 1 / 30)) : 0
        put(0, [layout.width * ratio, layout.height * ratio, half * ratio, ratio])
        put(4, [cx * ratio, cy * ratio, dt, Double(count)])
        put(8, [0, 0, shown ? s.spin : 0, reduced ? 20 : max(0, s.clock - s.struck)])
        put(12, [shown ? s.heat : 0, shown ? s.sweep : 1.15, alive && s.clock - s.struck < 0.3 && !reduced ? max(0, s.heat - lastHeat) : 0, alive && !reduced ? s.clock - s.struck : -1])
        for column in 0..<3 { put(16 + column * 4, Array(matrix[column * 3..<column * 3 + 3])) }
        put(28, [shown ? s.lightX : 0, shown ? s.lightY : 0, shown ? s.heat * 1.2 : 0, 0])
        put(32, [shown ? s.charge : 0, shown ? s.stamp : s.forged ? 1 : 0, shown ? s.morph : 0, shown ? s.engrave : 0])
        let metals = [[0.86, 0.37, 0.085], [0.95, 0.93, 0.88], [0.92, 0.72, 0.34]]
        put(36, metals[s.from])
        put(40, metals[s.to])
        put(44, [(layout.sheetTop + layout.titleTop - 16) * ratio, layout.sheetTop * ratio, alive ? 1 : 0, direct ? 1 : 0])
        if direct {
            let encoder = command.makeRenderCommandEncoder(descriptor: pass(drawable.texture, depthLoad: .clear))!
            encoder.setRenderPipelineState(directMedal)
            encoder.setDepthStencilState(depthWrite)
            encoder.setFragmentBytes(uniforms, length: 192, index: 0)
            encoder.setFragmentTexture(faces[s.from], index: 0)
            encoder.setFragmentTexture(faces[s.to], index: 1)
            encoder.setFragmentTexture(faces[3], index: 2)
            encoder.setFragmentSamplerState(linear, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            encoder.endEncoding()
            submit(command, drawable: drawable, state: s)
            return
        }
        if alive && !reduced {
            let encoder = command.makeComputeCommandEncoder()!
            encoder.setComputePipelineState(compute)
            encoder.setBytes(uniforms, length: 192, index: 0)
            encoder.setBuffer(particles, offset: 0, index: 1)
            encoder.dispatchThreadgroups(MTLSize(width: (count + 63) / 64, height: 1, depth: 1), threadsPerThreadgroup: MTLSize(width: 64, height: 1, depth: 1))
            encoder.endEncoding()
        }
        let scene = pass(hdr, depthLoad: .clear)
        let encoder = command.makeRenderCommandEncoder(descriptor: scene)!
        encoder.setRenderPipelineState(medal)
        encoder.setDepthStencilState(depthWrite)
        encoder.setFragmentBytes(uniforms, length: 192, index: 0)
        encoder.setFragmentTexture(faces[s.from], index: 0)
        encoder.setFragmentTexture(faces[s.to], index: 1)
        encoder.setFragmentTexture(faces[3], index: 2)
        encoder.setFragmentSamplerState(linear, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        if alive && !reduced {
            encoder.setDepthStencilState(depthRead)
            encoder.setRenderPipelineState(leaf)
            encoder.setVertexBytes(uniforms, length: 192, index: 0)
            encoder.setVertexBuffer(particles, offset: 0, index: 1)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 18, instanceCount: count)
            if s.clock - s.struck < 1.31 {
                encoder.setRenderPipelineState(strike)
                encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: 73)
            }
        }
        encoder.endEncoding()
        let glow = command.makeRenderCommandEncoder(descriptor: pass(emission, depthLoad: .load))!
        if alive && !reduced {
            glow.setDepthStencilState(depthRead)
            glow.setRenderPipelineState(leafGlow)
            glow.setVertexBytes(uniforms, length: 192, index: 0)
            glow.setVertexBuffer(particles, offset: 0, index: 1)
            glow.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 18, instanceCount: count)
        }
        glow.endEncoding()
        for i in 0..<4 { renderPost(command, source: i == 0 ? hdr : down[i - 1], lower: emission, target: down[i], mode: i == 0 ? 0 : 1) }
        renderPost(command, source: down[3], lower: hdr, target: blur[0], mode: 2)
        renderPost(command, source: blur[0], lower: hdr, target: blur[1], mode: 3)
        for i in (0..<3).reversed() { renderPost(command, source: down[i], lower: i == 2 ? blur[1] : up[i + 1], target: up[i], mode: 4) }
        renderPost(command, source: hdr, lower: up[0], target: fraction < 1 ? resolvedSurface : drawable.texture, mode: 5)
        if fraction < 1 {
            uniforms[47] = 1
            let directPass = command.makeRenderCommandEncoder(descriptor: pass(directSurface, depthLoad: .clear))!
            directPass.setRenderPipelineState(directMedal)
            directPass.setDepthStencilState(depthWrite)
            directPass.setFragmentBytes(uniforms, length: 192, index: 0)
            directPass.setFragmentTexture(faces[s.from], index: 0)
            directPass.setFragmentTexture(faces[s.to], index: 1)
            directPass.setFragmentTexture(faces[3], index: 2)
            directPass.setFragmentSamplerState(linear, index: 0)
            directPass.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            directPass.endEncoding()
            let descriptor = MTLRenderPassDescriptor()
            descriptor.colorAttachments[0].texture = drawable.texture
            descriptor.colorAttachments[0].loadAction = .clear
            descriptor.colorAttachments[0].storeAction = .store
            let blend = command.makeRenderCommandEncoder(descriptor: descriptor)!
            blend.setRenderPipelineState(crossfade)
            blend.setFragmentTexture(directSurface, index: 0)
            blend.setFragmentTexture(resolvedSurface, index: 1)
            blend.setFragmentSamplerState(linear, index: 0)
            var amount = Float(fraction)
            blend.setFragmentBytes(&amount, length: 4, index: 0)
            blend.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            blend.endEncoding()
        }
        submit(command, drawable: drawable, state: s)
    }

    private func submit(_ command: MTLCommandBuffer, drawable: CAMetalDrawable, state: DayOneMedalState) {
        lastTime = state.clock
        lastStrike = state.struck
        lastHeat = state.heat
        inFlight += 1
        command.addCompletedHandler { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.inFlight -= 1
                if !self.didReady {
                    self.didReady = true
                    self.ready?()
                }
            }
        }
        command.present(drawable)
        command.commit()
    }

    private func pass(_ target: MTLTexture, depthLoad: MTLLoadAction) -> MTLRenderPassDescriptor {
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = target
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].storeAction = .store
        pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
        pass.depthAttachment.texture = depth
        pass.depthAttachment.loadAction = depthLoad
        pass.depthAttachment.storeAction = .store
        pass.depthAttachment.clearDepth = 1
        return pass
    }

    private func renderPost(_ command: MTLCommandBuffer, source: MTLTexture, lower: MTLTexture, target: MTLTexture, mode: Int32) {
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = target
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].storeAction = .store
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)!
        encoder.setRenderPipelineState(mode == 5 ? resolve : post)
        encoder.setFragmentTexture(source, index: 0)
        encoder.setFragmentTexture(lower, index: 1)
        encoder.setFragmentSamplerState(linear, index: 0)
        var mode = mode
        encoder.setFragmentBytes(&mode, length: 4, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
    }
}
