// Ink Wash Page Turn — the Metal canvas: three page textures and one bleed shader.
//
// The view's frame callback advances the reader; a frame whose picture would not change draws nothing.

import MetalKit
import SwiftUI

struct InkWashPageTurnPaper: UIViewRepresentable {
    let reader: InkWashPageTurnReader
    let layout: InkWashPageTurnPages.Layout

    func makeCoordinator() -> InkWashPageTurnRenderer {
        InkWashPageTurnRenderer(reader: reader)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isOpaque = true
        view.isUserInteractionEnabled = false
        view.preferredFramesPerSecond = 120
        view.enableSetNeedsDisplay = false
        // the pages and the shader's colours are sRGB numbers
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        view.delegate = context.coordinator
        reader.view = view
        view.isPaused = !reader.live
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.configure(layout, scale: view.window?.screen.scale ?? view.traitCollection.displayScale)
        if view.isPaused { view.draw() }
    }
}

final class InkWashPageTurnRenderer: NSObject, MTKViewDelegate {
    let device = MTLCreateSystemDefaultDevice()
    private let reader: InkWashPageTurnReader
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var sampler: MTLSamplerState?
    private var pages: [MTLTexture] = []
    private var layout: InkWashPageTurnPages.Layout?
    /// what the drawable shows now
    private var shown: [Float] = []

    init(reader: InkWashPageTurnReader) {
        self.reader = reader
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Ink Wash Page Turn: Metal library unavailable")
            return
        }
        queue = device.makeCommandQueue()
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "inkWashPageTurnVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "inkWashPageTurnBleed")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        do {
            pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            assertionFailure("Ink Wash Page Turn: Metal set-up failed: \(error)")
        }
        let linear = MTLSamplerDescriptor()
        linear.minFilter = .linear
        linear.magFilter = .linear
        linear.sAddressMode = .clampToEdge
        linear.tAddressMode = .clampToEdge
        sampler = device.makeSamplerState(descriptor: linear)
    }

    func configure(_ next: InkWashPageTurnPages.Layout, scale: CGFloat) {
        guard next != layout, next.width > 0, next.height > 0, let device else { return }
        layout = next
        reader.width = next.width
        reader.height = next.height
        let loader = MTKTextureLoader(device: device)
        // the pages' bytes as they are: the shader mixes them as written, like the RN effect
        pages = InkWashPageTurnPages.bake(next, scale: scale).compactMap {
            try? loader.newTexture(cgImage: $0, options: [.SRGB: false, .textureUsage: MTLTextureUsage.shaderRead.rawValue, .textureStorageMode: MTLStorageMode.private.rawValue])
        }
        shown = []
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        shown = []
        if view.isPaused, size.width > 0 { view.draw() }
    }

    func draw(in view: MTKView) {
        reader.tick()
        defer {
            if !reader.live, !view.isPaused {
                view.isPaused = true
                reader.onLive?(false)
            }
        }
        guard pages.count == 3, let layout, let pipeline, let sampler else { return }
        let px = view.drawableSize
        let key = [Float(reader.from), Float(reader.to), Float(reader.progress), Float(reader.originX), Float(reader.originY), Float(px.width)]
        // at rest only the page on show matters
        let same = !shown.isEmpty && shown == key
        let bothRest = !shown.isEmpty && shown[2] == 0 && key[2] == 0 && shown[0] == key[0] && shown[5] == key[5]
        guard !same, !bothRest else { return }
        guard let command = queue?.makeCommandBuffer(), let drawable = view.currentDrawable,
              let descriptor = view.currentRenderPassDescriptor,
              let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
        var uniforms: [SIMD4<Float>] = [
            SIMD4(Float(layout.width), Float(layout.height), Float(px.width / layout.width), Float(reader.reach)),
            SIMD4(Float(reader.from), Float(reader.to), Float(reader.progress), 0),
            SIMD4(Float(reader.originX), Float(reader.originY), 0, 0),
        ]
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<SIMD4<Float>>.stride * 3, index: 0)
        for (i, page) in pages.enumerated() { encoder.setFragmentTexture(page, index: i) }
        encoder.setFragmentSamplerState(sampler, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
        shown = key
    }
}
