import MetalKit

final class GoldEmblemMembershipRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice?
    private let queue: MTLCommandQueue?
    private let pipeline: MTLRenderPipelineState?
    var uniforms = [SIMD4<Float>](repeating: .zero, count: 4)
    override init() {
        let device = MTLCreateSystemDefaultDevice()
        self.device = device; queue = device?.makeCommandQueue()
        let descriptor = MTLRenderPipelineDescriptor()
        let library = device?.makeDefaultLibrary()
        descriptor.vertexFunction = library?.makeFunction(name: "goldEmblemMembershipVertex")
        descriptor.fragmentFunction = library?.makeFunction(name: "goldEmblemMembershipFragment")
        let attachment = descriptor.colorAttachments[0]!
        attachment.pixelFormat = .bgra8Unorm
        attachment.isBlendingEnabled = true
        attachment.sourceRGBBlendFactor = .one; attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
        attachment.sourceAlphaBlendFactor = .one; attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        pipeline = device.flatMap { try? $0.makeRenderPipelineState(descriptor: descriptor) }
        super.init()
        if pipeline == nil { assertionFailure("Gold Emblem Membership: Metal pipeline unavailable") }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let pipeline, let queue, let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor, let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { data in
            if let base = data.baseAddress { encoder.setFragmentBytes(base, length: data.count, index: 0) }
        }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding(); command.present(drawable); command.commit()
    }
}
