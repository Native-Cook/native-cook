// Nebula Dust Swirl — the Metal dust.
//
// An untouchable full-screen MTKView, a port of the React Native WebGPU canvas (gpu/NdsGpu.tsx).
// Each frame the clock hands over one Frame; the renderer turns it into the uniforms, runs the compute
// pass over every grain and draws them as instanced soft quads with additive blending, in one command
// buffer. Under reduced motion it draws the rest pose once.

import MetalKit
import SwiftUI

struct NebulaDustSwirlDust: UIViewRepresentable {
    let clock: NebulaDustSwirlClock
    let still: Bool
    let width: Double
    let height: Double

    func makeCoordinator() -> NebulaDustSwirlRenderer {
        NebulaDustSwirlRenderer(clock: clock)
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
        let renderer = context.coordinator
        renderer.still = still
        renderer.size = SIMD2(Float(width), Float(height))
        view.isPaused = still
        if still { view.draw() }
    }
}

final class NebulaDustSwirlRenderer: NSObject, MTKViewDelegate {
    private typealias M = NebulaDustSwirlMotion

    let device = MTLCreateSystemDefaultDevice()
    var still = false
    var size = SIMD2<Float>(393, 852)
    private let clock: NebulaDustSwirlClock
    private var queue: MTLCommandQueue?
    private var stepPipeline: MTLComputePipelineState?
    private var drawPipeline: MTLRenderPipelineState?
    private var grains: MTLBuffer?
    private var epoch = -1

    init(clock: NebulaDustSwirlClock) {
        self.clock = clock
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Nebula Dust Swirl: Metal library unavailable")
            return
        }
        queue = device.makeCommandQueue()
        do {
            if let step = library.makeFunction(name: "nebulaDustSwirlStep") {
                stepPipeline = try device.makeComputePipelineState(function: step)
            }
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "nebulaDustSwirlGrainVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "nebulaDustSwirlGrainFragment")
            let colour = descriptor.colorAttachments[0]!
            colour.pixelFormat = .bgra8Unorm
            // additive light: grains add up, so the core burns where thousands overlap
            colour.isBlendingEnabled = true
            colour.rgbBlendOperation = .add
            colour.alphaBlendOperation = .add
            colour.sourceRGBBlendFactor = .one
            colour.destinationRGBBlendFactor = .one
            colour.sourceAlphaBlendFactor = .one
            colour.destinationAlphaBlendFactor = .one
            drawPipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            assertionFailure("Nebula Dust Swirl: Metal set-up failed: \(error)")
        }
        // pos, disp, vel, look: 32 bytes a grain, zeroed: nothing has been stirred yet
        let length = M.count * 32
        grains = device.makeBuffer(length: length, options: .storageModeShared)
        if let grains { memset(grains.contents(), 0, length) }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        if view.isPaused, size.width > 0 { view.draw() }
    }

    func draw(in view: MTKView) {
        guard view.drawableSize.width > 0, let stepPipeline, let drawPipeline, let grains,
              let command = queue?.makeCommandBuffer() else { return }
        var frame: NebulaDustSwirlClock.Frame
        if still {
            frame = NebulaDustSwirlClock.Frame(t: M.still, dt: 0, epoch: epoch, hold: 1)
        } else {
            frame = clock.tick()
        }
        let clear = frame.epoch != epoch
        epoch = frame.epoch
        var u: [SIMD4<Float>] = [
            SIMD4(Float(frame.t), Float(frame.dt), clear ? 1 : 0, Float(frame.hold)),
            SIMD4(size.x, size.y, Float(clock.centreX), Float(clock.centreY)),
            SIMD4(Float(clock.unit), 0, 0, 0),
            SIMD4(Float(frame.touchX), Float(frame.touchY), Float(frame.touchOn), 0),
            SIMD4(Float(frame.pushX), Float(frame.pushY), 0, 0),
        ]
        let length = MemoryLayout<SIMD4<Float>>.stride * u.count

        if let compute = command.makeComputeCommandEncoder() {
            compute.setComputePipelineState(stepPipeline)
            compute.setBytes(&u, length: length, index: 0)
            compute.setBuffer(grains, offset: 0, index: 1)
            let groups = (M.count + M.group - 1) / M.group
            compute.dispatchThreadgroups(MTLSize(width: groups, height: 1, depth: 1), threadsPerThreadgroup: MTLSize(width: M.group, height: 1, depth: 1))
            compute.endEncoding()
        }
        guard let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor,
              let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else {
            command.commit()
            return
        }
        encoder.setRenderPipelineState(drawPipeline)
        encoder.setVertexBytes(&u, length: length, index: 0)
        encoder.setVertexBuffer(grains, offset: 0, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: M.count)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
