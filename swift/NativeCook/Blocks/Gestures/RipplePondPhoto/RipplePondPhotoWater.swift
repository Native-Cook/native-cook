// Ripple Pond Photo — the Metal water.
//
// An untouchable MTKView under the page's text. Each frame the pond's clock hands over how many
// simulation steps to run, the damping and up to four drops; the renderer runs those steps on two
// ping-ponged height textures (RipplePondPhoto.metal) and draws the photo through the result, all in
// one command buffer. The frame's drops go in with its first step only.

import MetalKit
import SwiftUI

struct RipplePondPhotoWater: UIViewRepresentable {
    let pond: RipplePondPhotoPond
    let photo: CGImage
    /// the screen, pt
    let width: Double
    let height: Double

    func makeCoordinator() -> RipplePondPhotoRenderer {
        RipplePondPhotoRenderer(pond: pond, photo: photo)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isOpaque = true
        view.isUserInteractionEnabled = false
        view.preferredFramesPerSecond = 120
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.resize(width: width, height: height)
    }
}

final class RipplePondPhotoRenderer: NSObject, MTKViewDelegate {
    private typealias M = RipplePondPhotoMotion

    let device = MTLCreateSystemDefaultDevice()
    private let pond: RipplePondPhotoPond
    private var queue: MTLCommandQueue?
    private var stepPipeline: MTLRenderPipelineState?
    private var viewPipeline: MTLRenderPipelineState?
    private var sampler: MTLSamplerState?
    private var photo: MTLTexture?
    /// the two height textures; `current` holds the water now
    private var fields: [MTLTexture] = []
    private var current = 0
    private var rows = 0
    private var epoch = 0
    private var width = 0.0
    private var height = 0.0

    init(pond: RipplePondPhotoPond, photo image: CGImage) {
        self.pond = pond
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Ripple Pond Photo: Metal library unavailable")
            return
        }
        queue = device.makeCommandQueue()
        let vertex = library.makeFunction(name: "ripplePondPhotoVertex")
        let step = MTLRenderPipelineDescriptor()
        step.vertexFunction = vertex
        step.fragmentFunction = library.makeFunction(name: "ripplePondPhotoStep")
        step.colorAttachments[0].pixelFormat = .rgba16Float
        let view = MTLRenderPipelineDescriptor()
        view.vertexFunction = vertex
        view.fragmentFunction = library.makeFunction(name: "ripplePondPhotoView")
        view.colorAttachments[0].pixelFormat = .bgra8Unorm
        do {
            stepPipeline = try device.makeRenderPipelineState(descriptor: step)
            viewPipeline = try device.makeRenderPipelineState(descriptor: view)
            // the photo's bytes as they are: the view pass does its own light maths
            photo = try MTKTextureLoader(device: device).newTexture(cgImage: image, options: [.SRGB: false])
        } catch {
            assertionFailure("Ripple Pond Photo: Metal set-up failed: \(error)")
        }
        let linear = MTLSamplerDescriptor()
        linear.minFilter = .linear
        linear.magFilter = .linear
        linear.sAddressMode = .clampToEdge
        linear.tAddressMode = .clampToEdge
        sampler = device.makeSamplerState(descriptor: linear)
    }

    /// the grid follows the screen: 256 columns, rows so that a cell is square
    func resize(width w: Double, height h: Double) {
        guard w > 0, h > 0, w != width || h != height else { return }
        width = w
        height = h
        pond.width = w
        pond.height = h
        let next = M.gridRows(width: w, height: h)
        guard next != rows, let device else { return }
        rows = next
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float, width: M.gridW, height: rows, mipmapped: false)
        descriptor.usage = [.renderTarget, .shaderRead]
        descriptor.storageMode = .private
        fields = (0..<2).compactMap { _ in device.makeTexture(descriptor: descriptor) }
        current = 0
        // both textures start as still water
        epoch = -1
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    private func pass(_ target: MTLTexture, clear: Bool) -> MTLRenderPassDescriptor {
        let descriptor = MTLRenderPassDescriptor()
        descriptor.colorAttachments[0].texture = target
        descriptor.colorAttachments[0].loadAction = clear ? .clear : .dontCare
        descriptor.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        descriptor.colorAttachments[0].storeAction = .store
        return descriptor
    }

    func draw(in view: MTKView) {
        guard fields.count == 2, let stepPipeline, let viewPipeline, let sampler, let photo,
              let command = queue?.makeCommandBuffer() else { return }
        let frame = pond.tick()

        if frame.epoch != epoch {
            epoch = frame.epoch
            // still water: both textures cleared to zero height
            for field in fields { command.makeRenderCommandEncoder(descriptor: pass(field, clear: true))?.endEncoding() }
        }

        var plain = [SIMD4<Float>](repeating: .zero, count: 5)
        plain[0] = SIMD4(Float(M.c2), frame.damp, Float(M.gridW), Float(rows))
        var withDrops = plain
        for i in 0..<4 { withDrops[1 + i] = frame.drops[i] }
        let length = MemoryLayout<SIMD4<Float>>.stride * 5
        for s in 0..<frame.steps {
            guard let encoder = command.makeRenderCommandEncoder(descriptor: pass(fields[1 - current], clear: false)) else { break }
            encoder.setRenderPipelineState(stepPipeline)
            // the frame's drops go in with its first step only
            if s == 0 { encoder.setFragmentBytes(&withDrops, length: length, index: 0) } else { encoder.setFragmentBytes(&plain, length: length, index: 0) }
            encoder.setFragmentTexture(fields[current], index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            encoder.endEncoding()
            current = 1 - current
        }

        guard let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor,
              let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else {
            command.commit()
            return
        }
        // the photo covers the screen: the axis that overflows is cropped evenly
        let screen = width / height
        let picture = Double(photo.width) / Double(photo.height)
        let px = view.drawableSize
        var uniforms: [SIMD4<Float>] = [
            SIMD4(Float(px.width), Float(px.height), Float(px.width / max(width, 1)), 0),
            SIMD4(Float(M.gridW), Float(rows), Float(screen > picture ? 1 : screen / picture), Float(screen > picture ? picture / screen : 1)),
            SIMD4(Float(M.refract), Float(M.refractMax), Float(M.chroma), Float(M.normal)),
            SIMD4(Float(M.specPower), Float(M.spec), Float(M.caustic), Float(M.shade)),
        ]
        encoder.setRenderPipelineState(viewPipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<SIMD4<Float>>.stride * 4, index: 0)
        encoder.setFragmentTexture(fields[current], index: 0)
        encoder.setFragmentTexture(photo, index: 1)
        encoder.setFragmentSamplerState(sampler, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
