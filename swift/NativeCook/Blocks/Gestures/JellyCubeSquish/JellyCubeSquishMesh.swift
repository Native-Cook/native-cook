import MetalKit
import SwiftUI

/// The cube's vertex-coloured meshes, drawn with Metal: SwiftUI cannot shade a triangle from its
/// corners' colours. The view draws only when SwiftUI updates it, inside the same transaction, so
/// the cube never lags the shadow under it or the gloss over it.
struct JellyCubeSquishMeshView: UIViewRepresentable {
    let frame: JellyCubeSquishMeshFrame
    /// Changes every frame the cube changed: the cue to draw.
    let number: Int

    func makeCoordinator() -> JellyCubeSquishRenderer {
        JellyCubeSquishRenderer()
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.colorPixelFormat = .bgra8Unorm
        view.sampleCount = JellyCubeSquishRenderer.samples
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
final class JellyCubeSquishRenderer: NSObject, MTKViewDelegate {
    static let samples = 4
    private static let inFlight = 3

    let device = MTLCreateSystemDefaultDevice()
    var frame: JellyCubeSquishMeshFrame?
    var drawn = -1
    private let queue: MTLCommandQueue?
    private var over: MTLRenderPipelineState?
    private var add: MTLRenderPipelineState?
    private var vertexBuffers: [MTLBuffer] = []
    private var indexBuffers: [MTLBuffer] = []
    private var slot = 0
    private let semaphore = DispatchSemaphore(value: inFlight)

    override init() {
        queue = device?.makeCommandQueue()
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else { return }
        func pipeline(additive: Bool) -> MTLRenderPipelineState? {
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = library.makeFunction(name: "jellyCubeSquishVertex")
            d.fragmentFunction = library.makeFunction(name: "jellyCubeSquishFragment")
            d.rasterSampleCount = Self.samples
            let target = d.colorAttachments[0]!
            target.pixelFormat = .bgra8Unorm
            target.isBlendingEnabled = true
            target.rgbBlendOperation = .add
            target.alphaBlendOperation = .add
            target.sourceRGBBlendFactor = .one
            target.sourceAlphaBlendFactor = .one
            target.destinationRGBBlendFactor = additive ? .one : .oneMinusSourceAlpha
            target.destinationAlphaBlendFactor = additive ? .one : .oneMinusSourceAlpha
            return try? device.makeRenderPipelineState(descriptor: d)
        }
        over = pipeline(additive: false)
        add = pipeline(additive: true)
        let vLength = JellyCubeSquishMeshFrame.maxVertices * 8 * MemoryLayout<Float>.stride
        let iLength = JellyCubeSquishMeshFrame.maxIndices * MemoryLayout<UInt16>.stride
        vertexBuffers = (0..<Self.inFlight).compactMap { _ in device.makeBuffer(length: vLength, options: .storageModeShared) }
        indexBuffers = (0..<Self.inFlight).compactMap { _ in device.makeBuffer(length: iLength, options: .storageModeShared) }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let queue, let over, let add, let frame, vertexBuffers.count == Self.inFlight, indexBuffers.count == Self.inFlight,
              let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable else { return }
        semaphore.wait()
        let vb = vertexBuffers[slot]
        let ib = indexBuffers[slot]
        slot = (slot + 1) % Self.inFlight
        let vBytes = min(frame.vertices.count * MemoryLayout<Float>.stride, vb.length)
        let iCount = min(frame.indices.count, JellyCubeSquishMeshFrame.maxIndices)
        if vBytes > 0 { frame.vertices.withUnsafeBytes { vb.contents().copyMemory(from: $0.baseAddress!, byteCount: vBytes) } }
        if iCount > 0 { frame.indices.withUnsafeBytes { ib.contents().copyMemory(from: $0.baseAddress!, byteCount: iCount * 2) } }
        guard let commands = queue.makeCommandBuffer(), let encoder = commands.makeRenderCommandEncoder(descriptor: pass) else {
            semaphore.signal()
            return
        }
        var viewSize = SIMD2<Float>(Float(view.bounds.width), Float(view.bounds.height))
        encoder.setVertexBuffer(vb, offset: 0, index: 0)
        encoder.setVertexBytes(&viewSize, length: MemoryLayout<SIMD2<Float>>.stride, index: 1)
        for d in frame.draws where d.count > 0 && d.start + d.count <= iCount {
            encoder.setRenderPipelineState(d.additive ? add : over)
            encoder.drawIndexedPrimitives(type: .triangle, indexCount: d.count, indexType: .uint16, indexBuffer: ib,
                                          indexBufferOffset: d.start * MemoryLayout<UInt16>.stride)
        }
        encoder.endEncoding()
        let semaphore = semaphore
        commands.addCompletedHandler { _ in semaphore.signal() }
        commands.commit()
        // Presented with the SwiftUI transaction that drew the plate and the gloss.
        commands.waitUntilScheduled()
        drawable.present()
    }
}
