import MetalKit
import SwiftUI

/// What the cloth's Metal view draws in one frame: up to three copies of the mesh (two fading
/// ghosts behind the yank, then the cloth), each with its linen alpha.
final class TableclothPullTrickMeshFrame {
    var layers: [(vertices: [Float], alpha: Float)] = []
    var number = 0
}

/// The cloth's mesh, drawn with Metal: SwiftUI has no textured-triangle API. Each triangle shows
/// the baked linen through the mesh's linen coordinates, with the fold's shade laid over it. The
/// view draws only when SwiftUI updates it, inside the same transaction, so the cloth never lags
/// the dishes drawn over it.
struct TableclothPullTrickMeshView: UIViewRepresentable {
    let frame: TableclothPullTrickMeshFrame
    let linen: CGImage
    /// Changes every frame the cloth moved: the cue to draw.
    let number: Int

    func makeCoordinator() -> TableclothPullTrickRenderer {
        TableclothPullTrickRenderer(linen: linen)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.colorPixelFormat = .bgra8Unorm
        view.sampleCount = TableclothPullTrickRenderer.samples
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.isUserInteractionEnabled = false
        view.delegate = context.coordinator
        (view.layer as? CAMetalLayer)?.presentsWithTransaction = true
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.frame = frame
        if context.coordinator.drawn != number, view.bounds.width > 0 {
            context.coordinator.drawn = number
            view.draw()
        }
    }
}

@MainActor
final class TableclothPullTrickRenderer: NSObject, MTKViewDelegate {
    static let samples = 4
    private static let inFlight = 3
    private static let maxLayers = 3

    let device = MTLCreateSystemDefaultDevice()
    var frame: TableclothPullTrickMeshFrame?
    var drawn = -1
    private let queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var texture: MTLTexture?
    /// One buffer per layer per frame in flight.
    private var buffers: [[MTLBuffer]] = []
    private var indices: MTLBuffer?
    private var indexCount = 0
    private var slot = 0
    private let semaphore = DispatchSemaphore(value: inFlight)

    init(linen: CGImage) {
        queue = device?.makeCommandQueue()
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else { return }
        let d = MTLRenderPipelineDescriptor()
        d.vertexFunction = library.makeFunction(name: "tableclothPullTrickVertex")
        d.fragmentFunction = library.makeFunction(name: "tableclothPullTrickCloth")
        d.rasterSampleCount = Self.samples
        let target = d.colorAttachments[0]!
        target.pixelFormat = .bgra8Unorm
        // Premultiplied source-over.
        target.isBlendingEnabled = true
        target.rgbBlendOperation = .add
        target.alphaBlendOperation = .add
        target.sourceRGBBlendFactor = .one
        target.sourceAlphaBlendFactor = .one
        target.destinationRGBBlendFactor = .oneMinusSourceAlpha
        target.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        pipeline = try? device.makeRenderPipelineState(descriptor: d)
        texture = try? MTKTextureLoader(device: device).newTexture(cgImage: linen, options: [
            .SRGB: false,
            .generateMipmaps: true,
            .textureUsage: MTLTextureUsage.shaderRead.rawValue,
            .textureStorageMode: MTLStorageMode.private.rawValue,
        ])
        let length = TableclothPullTrickScene.vertexCount * TableclothPullTrickScene.floatsPerVertex * MemoryLayout<Float>.stride
        buffers = (0..<Self.inFlight).map { _ in
            (0..<Self.maxLayers).compactMap { _ in device.makeBuffer(length: length, options: .storageModeShared) }
        }
        let list = TableclothPullTrickScene.indices
        indexCount = list.count
        indices = list.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count, options: .storageModeShared) }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let queue, let pipeline, let texture, let indices, let frame,
              let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable else { return }
        semaphore.wait()
        let set = buffers[slot]
        slot = (slot + 1) % Self.inFlight
        guard let commands = queue.makeCommandBuffer(), let encoder = commands.makeRenderCommandEncoder(descriptor: pass) else {
            semaphore.signal()
            return
        }
        var viewSize = SIMD2<Float>(Float(view.bounds.width), Float(view.bounds.height))
        encoder.setRenderPipelineState(pipeline)
        encoder.setVertexBytes(&viewSize, length: MemoryLayout<SIMD2<Float>>.stride, index: 1)
        encoder.setFragmentTexture(texture, index: 0)
        for (i, layer) in frame.layers.prefix(set.count).enumerated() {
            let buffer = set[i]
            let bytes = min(layer.vertices.count * MemoryLayout<Float>.stride, buffer.length)
            layer.vertices.withUnsafeBytes { buffer.contents().copyMemory(from: $0.baseAddress!, byteCount: bytes) }
            var alpha = layer.alpha
            encoder.setVertexBuffer(buffer, offset: 0, index: 0)
            encoder.setFragmentBytes(&alpha, length: MemoryLayout<Float>.stride, index: 0)
            encoder.drawIndexedPrimitives(type: .triangle, indexCount: indexCount, indexType: .uint16, indexBuffer: indices, indexBufferOffset: 0)
        }
        encoder.endEncoding()
        let semaphore = semaphore
        commands.addCompletedHandler { _ in semaphore.signal() }
        commands.commit()
        // Presented with the SwiftUI transaction that drew the table and the dishes.
        commands.waitUntilScheduled()
        drawable.present()
    }
}
