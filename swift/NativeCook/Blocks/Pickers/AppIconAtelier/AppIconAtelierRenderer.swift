import MetalKit
import ImageIO
import SwiftUI

struct AppIconAtelierRenderer: UIViewRepresentable {
  let layout: AppIconAtelierTheme.Layout
  let frame: AppIconAtelierMotion.Frame
  func makeCoordinator() -> AppIconAtelierMetal { AppIconAtelierMetal() }
  func makeUIView(context: Context) -> MTKView {
    let view=MTKView(frame: .zero, device: context.coordinator.device)
    view.colorPixelFormat = .bgra8Unorm
    view.isOpaque=true; view.backgroundColor = .clear
    view.isPaused=true; view.enableSetNeedsDisplay=true
    view.framebufferOnly=true
    (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name: CGColorSpace.displayP3)
    view.delegate=context.coordinator
    return view
  }
  func updateUIView(_ view: MTKView, context: Context) {
    context.coordinator.layout=layout; context.coordinator.frame=frame
    view.setNeedsDisplay()
  }
}

final class AppIconAtelierMetal: NSObject, MTKViewDelegate {
  typealias M=AppIconAtelierMotion
  let device: MTLDevice
  let queue: MTLCommandQueue
  let pipeline: MTLRenderPipelineState
  let wallpaper: MTLTexture
  var layout=AppIconAtelierTheme.Layout(402,874,62,34)
  var frame=M.Frame()
  override init() {
    guard let device=MTLCreateSystemDefaultDevice(), let queue=device.makeCommandQueue(), let library=device.makeDefaultLibrary() else { fatalError("App Icon Atelier: Metal unavailable") }
    self.device=device; self.queue=queue
    let descriptor=MTLRenderPipelineDescriptor()
    descriptor.vertexFunction=library.makeFunction(name:"appIconAtelierVertex")
    descriptor.fragmentFunction=library.makeFunction(name:"appIconAtelierFragment")
    descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
    do {
      pipeline=try device.makeRenderPipelineState(descriptor:descriptor)
      wallpaper=Self.makeWallpaper(device)
    } catch { fatalError("App Icon Atelier: \(error)") }
    super.init()
  }
  private static func makeWallpaper(_ device: MTLDevice) -> MTLTexture {
    let source=CGImageSourceCreateWithURL(AppIconAtelierResources.url("wallpaper","jpg")! as CFURL,nil)!
    let image=CGImageSourceCreateImageAtIndex(source,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary)!
    let width=image.width,height=image.height
    var pixels=[UInt8](repeating:0,count:width*height*4)
    // Skia's ImageShader evaluates its image in the canvas's Display P3 working space.
    // Convert the input once; the source shader's final sRGB→P3 operation stays unchanged.
    pixels.withUnsafeMutableBytes { bytes in
      let context=CGContext(data:bytes.baseAddress,width:width,height:height,bitsPerComponent:8,bytesPerRow:width*4,space:CGColorSpace(name:CGColorSpace.displayP3)!,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)!
      context.draw(image,in:CGRect(x:0,y:0,width:width,height:height))
    }
    let descriptor=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm,width:width,height:height,mipmapped:false)
    descriptor.usage = .shaderRead
    let texture=device.makeTexture(descriptor:descriptor)!
    texture.replace(region:MTLRegionMake2D(0,0,width,height),mipmapLevel:0,withBytes:pixels,bytesPerRow:width*4)
    return texture
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
  func draw(in view: MTKView) {
    guard let pass=view.currentRenderPassDescriptor, let drawable=view.currentDrawable, let command=queue.makeCommandBuffer(), let encoder=command.makeRenderCommandEncoder(descriptor:pass) else { return }
    var uniforms=makeUniforms(view.contentScaleFactor)
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&uniforms, length:MemoryLayout<SIMD4<Float>>.stride*uniforms.count, index:0)
    encoder.setFragmentTexture(wallpaper,index:0)
    encoder.drawPrimitives(type:.triangle, vertexStart:0, vertexCount:3)
    encoder.endEncoding(); command.present(drawable); command.commit()
  }
  private func makeUniforms(_ ratio: Double) -> [SIMD4<Float>] {
    let L=layout, f=frame, A=AppIconAtelierTheme.glasses[f.a], B=AppIconAtelierTheme.glasses[f.b], S=AppIconAtelierTheme.glasses[f.applied]
    let pk=M.clamp(f.peek), at=L.flight(f.peek)
    let size=(L.heroS+(31.5-L.heroS)*f.peek)*(1-0.04*f.press)
    let yaw = -0.42+f.lean+(-0.06+0.42-f.lean)*pk
    let tint=A.tint+(B.tint-A.tint)*M.progress(f.front,f.dir)
    func v(_ x: Double=0,_ y: Double=0,_ z: Double=0,_ w: Double=0) -> SIMD4<Float> { .init(Float(x),Float(y),Float(z),Float(w)) }
    func g0(_ g: AppIconAtelierTheme.Glass) -> SIMD4<Float> { v(g.tint.x,g.tint.y,g.tint.z,g.density) }
    func g1(_ g: AppIconAtelierTheme.Glass) -> SIMD4<Float> { v(g.frost,g.film,g.glow,0) }
    let smooth=M.seg(f.peek,0.7,0.95)
    var u=[SIMD4<Float>](repeating:.zero,count:29)
    u[0]=v(L.w,L.h); u[1]=v(L.cardX,L.cardY,L.cardW,168); u[2]=v(28)
    u[3]=v(at.x,at.y-f.bob*size); u[4]=v(size); u[5]=v(yaw,-0.26+0.21*pk,-0.12+0.12*pk)
    u[6]=v(1+f.land,1-f.land); u[7]=g0(A); u[8]=g1(A); u[9]=g0(B); u[10]=g1(B)
    u[11]=v(f.front); u[12]=v(f.dir); u[13]=v(M.meniscus(f.front)*(f.a==f.b ? 0.6 : 1)); u[14]=v(f.phase)
    u[15]=v(at.x,at.y+size*(1.32+(1.14-1.32)*pk)); u[16]=v((1-0.3*pk)*(1-3*f.bob)); u[17]=v(tint.x,tint.y,tint.z)
    u[18]=v(L.appX[2],L.appsY); u[19]=v(31.5); u[20]=v(1-smooth*smooth*(3-2*smooth)); u[21]=v(f.pop)
    u[22]=g0(S); u[23]=g1(S); u[24]=v(1/ratio); u[28]=v(ratio)
    return u
  }
}
