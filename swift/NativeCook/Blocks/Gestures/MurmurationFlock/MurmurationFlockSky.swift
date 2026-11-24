// Murmuration Flock — the Metal canvas: twenty thousand birds, stepped and drawn in one command buffer.
//
// An untouchable, transparent MTKView over the sky. Each frame it asks the clock for the film's time
// and the finger, runs the compute pass over every bird and draws them as instanced darts. The birds'
// buffer starts zeroed: nobody has been pushed yet. Under reduced motion it draws one still frame.

import MetalKit
import SwiftUI

struct MurmurationFlockSky: UIViewRepresentable {
    let clock: MurmurationFlockClock
    let width: Double
    let height: Double
    let still: Bool

    func makeCoordinator() -> MurmurationFlockRenderer {
        MurmurationFlockRenderer(clock: clock)
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
        // the birds' colours are sRGB numbers
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        clock.width = width
        clock.height = height
        view.isPaused = still
        if still { view.draw() }
    }
}

final class MurmurationFlockRenderer: NSObject, MTKViewDelegate {
    private typealias M = MurmurationFlockMotion

    /// MurmurationFlockFrame in the shader
    private struct Uniforms {
        var sim: SIMD4<Float>
        var view: SIMD4<Float>
        var look: SIMD4<Float>
        var touch: SIMD4<Float>
    }

    let device = MTLCreateSystemDefaultDevice()
    private let clock: MurmurationFlockClock
    private var queue: MTLCommandQueue?
    private var step: MTLComputePipelineState?
    private var draw: MTLRenderPipelineState?
    private var birds: MTLBuffer?

    init(clock: MurmurationFlockClock) {
        self.clock = clock
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Murmuration Flock: Metal library unavailable")
            return
        }
        queue = device.makeCommandQueue()
        do {
            if let kernel = library.makeFunction(name: "murmurationFlockStep") {
                step = try device.makeComputePipelineState(function: kernel)
            }
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "murmurationFlockBirdVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "murmurationFlockBirdFragment")
            let colour = descriptor.colorAttachments[0]!
            colour.pixelFormat = .bgra8Unorm
            // premultiplied over
            colour.isBlendingEnabled = true
            colour.rgbBlendOperation = .add
            colour.alphaBlendOperation = .add
            colour.sourceRGBBlendFactor = .one
            colour.sourceAlphaBlendFactor = .one
            colour.destinationRGBBlendFactor = .oneMinusSourceAlpha
            colour.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            draw = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            assertionFailure("Murmuration Flock: Metal set-up failed: \(error)")
        }
        // 32 bytes a bird (pos, head, disp, vel), zeroed
        let length = M.count * 32
        birds = device.makeBuffer(length: length, options: .storageModePrivate)
        if let birds, let command = queue?.makeCommandBuffer(), let blit = command.makeBlitCommandEncoder() {
            blit.fill(buffer: birds, range: 0..<length, value: 0)
            blit.endEncoding()
            command.commit()
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        let frame = clock.tick()
        guard let step, let draw, let birds, view.bounds.width > 0,
              let command = queue?.makeCommandBuffer(),
              let descriptor = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable else { return }
        let w = clock.width, h = clock.height
        let unit = min(w * M.scaleW, h * M.scaleH)
        var uniforms = Uniforms(
            sim: SIMD4(Float(frame.t), Float(frame.dt), frame.clear ? 1 : 0, Float(frame.hold)),
            view: SIMD4(Float(w), Float(h), Float(w * M.centreX), Float(h * M.centreY)),
            look: SIMD4(Float(unit), 0, 0, 0),
            touch: SIMD4(Float(frame.touchX), Float(frame.touchY), Float(frame.touchOn), 0)
        )
        if let compute = command.makeComputeCommandEncoder() {
            compute.setComputePipelineState(step)
            compute.setBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
            compute.setBuffer(birds, offset: 0, index: 1)
            let groups = (M.count + M.group - 1) / M.group
            compute.dispatchThreadgroups(MTLSize(width: groups, height: 1, depth: 1), threadsPerThreadgroup: MTLSize(width: M.group, height: 1, depth: 1))
            compute.endEncoding()
        }
        guard let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
        encoder.setRenderPipelineState(draw)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
        encoder.setVertexBuffer(birds, offset: 0, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: M.count)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
