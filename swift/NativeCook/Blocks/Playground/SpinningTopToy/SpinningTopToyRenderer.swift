import MetalKit
import ImageIO

final class SpinningTopToyRenderer {
    let device = MTLCreateSystemDefaultDevice()!
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let photo: MTLTexture
    init() throws {
        queue=device.makeCommandQueue()!
        let library=try device.makeDefaultLibrary(bundle: .main)
        let desc=MTLRenderPipelineDescriptor()
        desc.vertexFunction=library.makeFunction(name: "SpinningTopToyShader::vertexMain")
        desc.fragmentFunction=library.makeFunction(name: "SpinningTopToyShader::fragmentMain")
        desc.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline=try device.makeRenderPipelineState(descriptor: desc)
        let url=Bundle.main.url(forResource: "SpinningTopToy-top", withExtension: "webp")!
        let src=CGImageSourceCreateWithURL(url as CFURL,nil)!
        let image=CGImageSourceCreateImageAtIndex(src,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary)!
        let w=image.width,h=image.height
        let ctx=CGContext(data:nil,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,
                          space:CGColorSpace(name:CGColorSpace.sRGB)!,
                          bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)!
        ctx.setBlendMode(.copy); ctx.draw(image,in:CGRect(x:0,y:0,width:w,height:h))
        let td=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm,width:w,height:h,mipmapped:false)
        td.usage = .shaderRead; photo=device.makeTexture(descriptor:td)!
        photo.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:ctx.data!,bytesPerRow:w*4)
    }
    func draw(_ view: MTKView, _ f: SpinningTopToyFrame) {
        guard view.bounds.width > 0, let drawable=view.currentDrawable, let pass=view.currentRenderPassDescriptor,
              let buffer=queue.makeCommandBuffer(),let encoder=buffer.makeRenderCommandEncoder(descriptor:pass) else { return }
        var u:[Float]=[Float(view.bounds.width),Float(view.bounds.height),Float(view.drawableSize.width),Float(view.drawableSize.height),
                       Float(f.x),Float(f.y),Float(f.angle),Float(f.spin),Float(f.speed),Float(f.glow),Float(f.alpha),Float(f.fall)]
        for t in f.trail { u += [t.x,t.y,t.z,0] }
        encoder.setRenderPipelineState(pipeline); encoder.setFragmentBytes(&u,length:u.count*4,index:0)
        encoder.setFragmentTexture(photo,index:0); encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
        encoder.endEncoding(); buffer.present(drawable); buffer.commit()
    }
}
