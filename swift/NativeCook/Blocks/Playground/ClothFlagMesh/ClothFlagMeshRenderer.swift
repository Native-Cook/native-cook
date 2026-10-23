import MetalKit
import SwiftUI

/// The banner's mesh, drawn with Metal: SwiftUI has no textured-triangle API. Each triangle shows
/// the weave (evaluated per pixel in the flat banner's coordinates, like the React Native shader)
/// multiplied by its corners' shade, then the satin sheen is screened over it. The view draws
/// only when SwiftUI updates it, inside the same transaction, so the cloth never lags the rings
/// and the shadow drawn around it.
struct ClothFlagMeshMeshView: UIViewRepresentable {
    let engine: ClothFlagMeshEngine
    /// Changes every frame the cloth moved: the cue to draw.
    let frame: Int

    func makeCoordinator() -> ClothFlagMeshRenderer {
        ClothFlagMeshRenderer(engine: engine)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.colorPixelFormat = .bgra8Unorm
        view.sampleCount = ClothFlagMeshRenderer.samples
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.isUserInteractionEnabled = false
        view.delegate = context.coordinator
        (view.layer as? CAMetalLayer)?.presentsWithTransaction = true
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.engine = engine
        if context.coordinator.drawn != frame, view.bounds.width > 0 {
            context.coordinator.drawn = frame
            view.draw()
        }
    }
}

@MainActor
final class ClothFlagMeshRenderer: NSObject, MTKViewDelegate {
    static let samples = 4
    // Buffers in flight: the GPU reads one while the next frame is written into another.
    private static let inFlight = 3

    let device = MTLCreateSystemDefaultDevice()
    var engine: ClothFlagMeshEngine
    var drawn = -1
    private let queue: MTLCommandQueue?
    private var cloth: MTLRenderPipelineState?
    private var sheen: MTLRenderPipelineState?
    private var buffers: [MTLBuffer] = []
    private var indices: MTLBuffer?
    private var indexCount = 0
    private var slot = 0
    private let semaphore = DispatchSemaphore(value: inFlight)

    init(engine: ClothFlagMeshEngine) {
        self.engine = engine
        queue = device?.makeCommandQueue()
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else { return }
        func pipeline(_ fragment: String, screen: Bool) -> MTLRenderPipelineState? {
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = library.makeFunction(name: "clothFlagMeshVertex")
            d.fragmentFunction = library.makeFunction(name: fragment)
            d.rasterSampleCount = Self.samples
            let target = d.colorAttachments[0]!
            target.pixelFormat = .bgra8Unorm
            if screen {
                // Screen: src · (1 − dst) + dst, keeping the cloth's coverage.
                target.isBlendingEnabled = true
                target.rgbBlendOperation = .add
                target.sourceRGBBlendFactor = .oneMinusDestinationColor
                target.destinationRGBBlendFactor = .one
                target.alphaBlendOperation = .add
                target.sourceAlphaBlendFactor = .zero
                target.destinationAlphaBlendFactor = .one
            }
            return try? device.makeRenderPipelineState(descriptor: d)
        }
        cloth = pipeline("clothFlagMeshCloth", screen: false)
        sheen = pipeline("clothFlagMeshSheen", screen: true)
        let length = ClothFlagMeshCloth.count * ClothFlagMeshEngine.floatsPerVertex * MemoryLayout<Float>.stride
        buffers = (0..<Self.inFlight).compactMap { _ in device.makeBuffer(length: length, options: .storageModeShared) }
        let list = engine.indices
        indexCount = list.count
        indices = list.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count, options: .storageModeShared) }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let queue, let cloth, let sheen, let indices, buffers.count == Self.inFlight,
              let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable else { return }
        semaphore.wait()
        let buffer = buffers[slot]
        slot = (slot + 1) % Self.inFlight
        let floats = ClothFlagMeshCloth.count * ClothFlagMeshEngine.floatsPerVertex
        buffer.contents().copyMemory(from: engine.vertices, byteCount: floats * MemoryLayout<Float>.stride)

        guard let commands = queue.makeCommandBuffer(), let encoder = commands.makeRenderCommandEncoder(descriptor: pass) else {
            semaphore.signal()
            return
        }
        var viewSize = SIMD2<Float>(Float(view.bounds.width), Float(view.bounds.height))
        var flagSize = SIMD2<Float>(Float(ClothFlagMeshCloth.flagW), Float(ClothFlagMeshCloth.flagH))
        encoder.setVertexBuffer(buffer, offset: 0, index: 0)
        encoder.setVertexBytes(&viewSize, length: MemoryLayout<SIMD2<Float>>.stride, index: 1)
        encoder.setRenderPipelineState(cloth)
        encoder.setFragmentBytes(&flagSize, length: MemoryLayout<SIMD2<Float>>.stride, index: 0)
        encoder.drawIndexedPrimitives(type: .triangle, indexCount: indexCount, indexType: .uint16, indexBuffer: indices, indexBufferOffset: 0)
        encoder.setRenderPipelineState(sheen)
        encoder.drawIndexedPrimitives(type: .triangle, indexCount: indexCount, indexType: .uint16, indexBuffer: indices, indexBufferOffset: 0)
        encoder.endEncoding()
        let semaphore = semaphore
        commands.addCompletedHandler { _ in semaphore.signal() }
        commands.commit()
        // Presented with the SwiftUI transaction that drew the rings and the shadow.
        commands.waitUntilScheduled()
        drawable.present()
    }
}
