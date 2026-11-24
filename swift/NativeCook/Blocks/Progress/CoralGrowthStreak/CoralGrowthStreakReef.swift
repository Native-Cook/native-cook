// Coral Growth Streak — the Metal reef.
//
// An untouchable round MTKView. Each frame the clock hands over how many simulation steps to run, the
// open radius, the look and up to eight seeds; the renderer runs those steps on two ping-ponged
// textures (CoralGrowthStreak.metal) and draws the result, all in one command buffer. The frame's
// seeds go in with its first step only. Under reduced motion it draws one still labyrinth.

import MetalKit
import SwiftUI

struct CoralGrowthStreakReef: UIViewRepresentable {
    let clock: CoralGrowthStreakClock
    let still: Bool

    func makeCoordinator() -> CoralGrowthStreakRenderer {
        CoralGrowthStreakRenderer(clock: clock)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isOpaque = false
        view.layer.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.isUserInteractionEnabled = false
        view.preferredFramesPerSecond = 120
        view.enableSetNeedsDisplay = false
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.still = still
        view.isPaused = still
        if still { view.draw() }
    }
}

final class CoralGrowthStreakRenderer: NSObject, MTKViewDelegate {
    private typealias M = CoralGrowthStreakMotion

    let device = MTLCreateSystemDefaultDevice()
    var still = false
    private let clock: CoralGrowthStreakClock
    private var queue: MTLCommandQueue?
    private var stepPipeline: MTLRenderPipelineState?
    private var viewPipeline: MTLRenderPipelineState?
    private var stillPipeline: MTLRenderPipelineState?
    private var sampler: MTLSamplerState?
    /// the two field textures; `current` holds the reef now
    private var fields: [MTLTexture] = []
    private var current = 0
    private var epoch = -1

    init(clock: CoralGrowthStreakClock) {
        self.clock = clock
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Coral Growth Streak: Metal library unavailable")
            return
        }
        queue = device.makeCommandQueue()
        let vertex = library.makeFunction(name: "coralGrowthStreakVertex")
        func pipeline(_ name: String, _ format: MTLPixelFormat) throws -> MTLRenderPipelineState {
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = vertex
            descriptor.fragmentFunction = library.makeFunction(name: name)
            descriptor.colorAttachments[0].pixelFormat = format
            return try device.makeRenderPipelineState(descriptor: descriptor)
        }
        do {
            stepPipeline = try pipeline("coralGrowthStreakStep", .rgba16Float)
            viewPipeline = try pipeline("coralGrowthStreakView", .bgra8Unorm)
            stillPipeline = try pipeline("coralGrowthStreakStill", .bgra8Unorm)
        } catch {
            assertionFailure("Coral Growth Streak: Metal set-up failed: \(error)")
        }
        let linear = MTLSamplerDescriptor()
        linear.minFilter = .linear
        linear.magFilter = .linear
        linear.sAddressMode = .clampToEdge
        linear.tAddressMode = .clampToEdge
        sampler = device.makeSamplerState(descriptor: linear)
        let texture = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float, width: M.grid, height: M.grid, mipmapped: false)
        texture.usage = [.renderTarget, .shaderRead]
        texture.storageMode = .private
        fields = (0..<2).compactMap { _ in device.makeTexture(descriptor: texture) }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        if view.isPaused, size.width > 0 { view.draw() }
    }

    private func pass(_ target: MTLTexture, clear: Bool) -> MTLRenderPassDescriptor {
        let descriptor = MTLRenderPassDescriptor()
        descriptor.colorAttachments[0].texture = target
        descriptor.colorAttachments[0].loadAction = clear ? .clear : .dontCare
        // an empty reef: u = 1, v = 0
        descriptor.colorAttachments[0].clearColor = MTLClearColor(red: 1, green: 0, blue: 0, alpha: 1)
        descriptor.colorAttachments[0].storeAction = .store
        return descriptor
    }

    func draw(in view: MTKView) {
        guard view.drawableSize.width > 0, let command = queue?.makeCommandBuffer() else { return }
        let px = view.drawableSize
        let ratio = Float(px.width / max(view.bounds.width, 1))
        var look: [SIMD4<Float>]
        if still {
            look = [SIMD4(Float(px.width), Float(px.height), Float(M.grid), ratio), SIMD4(1, 0, 0, Float(M.ringFrac(M.days)))]
        } else {
            guard fields.count == 2, let stepPipeline else { return }
            let frame = clock.tick()
            if frame.epoch != epoch {
                epoch = frame.epoch
                for field in fields { command.makeRenderCommandEncoder(descriptor: pass(field, clear: true))?.endEncoding() }
            }
            if frame.steps > 0 {
                var plain = [SIMD4<Float>](repeating: .zero, count: 2 + M.seedSlots)
                plain[0] = SIMD4(Float(M.feed), Float(M.kill), Float(M.grid), Float(M.seedRadius))
                plain[1] = SIMD4(Float(frame.radius), Float(M.wallKill), Float(M.wallWidth), 0)
                var seeded = plain
                for i in 0..<M.seedSlots { seeded[2 + i] = frame.seeds[i] }
                let length = MemoryLayout<SIMD4<Float>>.stride * (2 + M.seedSlots)
                for s in 0..<frame.steps {
                    guard let encoder = command.makeRenderCommandEncoder(descriptor: pass(fields[1 - current], clear: false)) else { break }
                    encoder.setRenderPipelineState(stepPipeline)
                    // the frame's seeds go in with its first step only
                    if s == 0 { encoder.setFragmentBytes(&seeded, length: length, index: 0) } else { encoder.setFragmentBytes(&plain, length: length, index: 0) }
                    encoder.setFragmentTexture(fields[current], index: 0)
                    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
                    encoder.endEncoding()
                    current = 1 - current
                }
            }
            look = [SIMD4(Float(px.width), Float(px.height), Float(M.grid), ratio), SIMD4(Float(frame.level), Float(frame.bloom), Float(frame.phase), 0)]
        }
        guard let pipeline = still ? stillPipeline : viewPipeline, let sampler,
              let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor,
              let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else {
            command.commit()
            return
        }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&look, length: MemoryLayout<SIMD4<Float>>.stride * 2, index: 0)
        if !still, fields.count == 2 {
            encoder.setFragmentTexture(fields[current], index: 0)
            encoder.setFragmentSamplerState(sampler, index: 0)
        }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
