import MetalKit

final class PasskeyGlassSealRenderer: NSObject, MTKViewDelegate {
    let device = MTLCreateSystemDefaultDevice()
    private var queue: MTLCommandQueue?
    private var card: MTLRenderPipelineState?
    private var thread: MTLRenderPipelineState?
    private var points: MTLBuffer?
    private var mask: MTLTexture?
    private var maskSize = CGSize.zero
    var uniforms = [Float](repeating:0,count:16)
    override init() {
        super.init()
        guard let device, let library = device.makeDefaultLibrary() else { assertionFailure("Passkey Metal library unavailable"); return }
        queue = device.makeCommandQueue()
        mask = device.makeTexture(descriptor:.texture2DDescriptor(pixelFormat:.r8Unorm,width:1,height:1,mipmapped:false))
        var zero: UInt8 = 0
        mask?.replace(region:MTLRegionMake2D(0,0,1,1),mipmapLevel:0,withBytes:&zero,bytesPerRow:1)
        let data = PasskeyGlassSealPrint.points
        points = data.withUnsafeBytes { device.makeBuffer(bytes:$0.baseAddress!,length:$0.count,options:.storageModeShared) }
        func make(_ vertex: String, _ fragment: String) -> MTLRenderPipelineState? {
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = library.makeFunction(name:vertex); d.fragmentFunction = library.makeFunction(name:fragment)
            let a = d.colorAttachments[0]!
            a.pixelFormat = .bgra8Unorm; a.isBlendingEnabled = true
            a.sourceRGBBlendFactor = .one; a.destinationRGBBlendFactor = .oneMinusSourceAlpha
            a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            do { return try device.makeRenderPipelineState(descriptor:d) }
            catch { assertionFailure("Passkey Metal pipeline: \(error)"); return nil }
        }
        card = make("passkeyGlassSealFullVs","passkeyGlassSealCardFs")
        thread = make("passkeyGlassSealThreadVs","passkeyGlassSealThreadFs")
    }
    func prepareMask(_ l: PasskeyGlassSealTheme.Layout, _ scale: Double) {
        let size = CGSize(width:l.cw*scale,height:l.ch*scale)
        guard size != maskSize, let device else { return }
        maskSize = size; mask = PasskeyGlassSealArt.printMask(device,l,scale)
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { }
    func draw(in view: MTKView) {
        guard let card, let thread, let points, let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor, let command = queue?.makeCommandBuffer(),
              let e = command.makeRenderCommandEncoder(descriptor:pass) else { return }
        uniforms.withUnsafeBytes { bytes in
            e.setVertexBytes(bytes.baseAddress!,length:64,index:0)
            e.setFragmentBytes(bytes.baseAddress!,length:64,index:0)
        }
        e.setFragmentTexture(mask,index:0)
        e.setRenderPipelineState(card)
        e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
        if uniforms[10] < 0.5 {
            e.setRenderPipelineState(thread); e.setVertexBuffer(points,offset:0,index:1)
            e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:PasskeyGlassSealPrint.count)
        }
        e.endEncoding(); command.present(drawable); command.commit()
    }
}
