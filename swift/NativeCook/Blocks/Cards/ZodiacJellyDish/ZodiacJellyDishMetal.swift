import MetalKit
import SwiftUI

struct ZodiacJellyDishMetal: UIViewRepresentable {
  var frame: ZodiacJellyDishMotion.Frame
  var center: CGPoint
  var scale: CGFloat
  func makeCoordinator() -> ZodiacJellyDishRenderer { ZodiacJellyDishRenderer(scale) }
  func makeUIView(context: Context) -> MTKView {
    let view = MTKView(frame:.zero, device:context.coordinator.device)
    view.isOpaque = false; view.backgroundColor = .clear
    view.clearColor = MTLClearColorMake(0,0,0,0)
    view.colorPixelFormat = .bgra8Unorm
    // Skia's onscreen surface is Display P3; procedural shader values use that gamut.
    (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name:CGColorSpace.displayP3)
    view.isPaused = true; view.enableSetNeedsDisplay = true
    view.autoResizeDrawable = true; view.contentScaleFactor = scale
    view.isUserInteractionEnabled = false
    view.delegate = context.coordinator
    return view
  }
  func updateUIView(_ view: MTKView, context: Context) {
    context.coordinator.frame = frame; context.coordinator.center = center
    context.coordinator.scale = scale
    view.contentScaleFactor = scale
    view.setNeedsDisplay()
  }
}

final class ZodiacJellyDishRenderer: NSObject, MTKViewDelegate {
  let device: MTLDevice?
  private let queue: MTLCommandQueue?
  private var pipeline: MTLRenderPipelineState?
  private var dish: MTLTexture?, label: MTLTexture?
  var frame = ZodiacJellyDishMotion.Frame()
  var center = CGPoint.zero
  var scale: CGFloat
  init(_ scale: CGFloat) {
    self.scale = scale
    let d = MTLCreateSystemDefaultDevice(); device = d; queue = d?.makeCommandQueue()
    super.init()
    guard let d, let library = d.makeDefaultLibrary() else { return }
    let desc = MTLRenderPipelineDescriptor()
    desc.vertexFunction = library.makeFunction(name:"zodiacJellyDishVertex")
    desc.fragmentFunction = library.makeFunction(name:"zodiacJellyDishFragment")
    let attachment = desc.colorAttachments[0]!
    attachment.pixelFormat = .bgra8Unorm
    attachment.isBlendingEnabled = true
    attachment.sourceRGBBlendFactor = .one; attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
    attachment.sourceAlphaBlendFactor = .one; attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    pipeline = try? d.makeRenderPipelineState(descriptor:desc)
    let loader = MTKTextureLoader(device:d), resources = ZodiacJellyDishResources.shared
    let options: [MTKTextureLoader.Option:Any] = [.SRGB:false, .origin:MTKTextureLoader.Origin.topLeft.rawValue, .generateMipmaps:false]
    if let cg = resources.dish { dish = try? loader.newTexture(cgImage:cg,options:options) }
    if let cg = resources.label(scale) { label = try? loader.newTexture(cgImage:cg,options:options) }
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
  func draw(in view: MTKView) {
    guard let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
          let pipeline, let queue, let dish, let label,
          let command = queue.makeCommandBuffer(), let encoder = command.makeRenderCommandEncoder(descriptor:pass) else { return }
    let j = frame.jelly
    let u: [SIMD4<Float>] = [
      SIMD4(Float(center.x),Float(center.y),143.5,209.5),
      SIMD4(64,30,14,Float(frame.t)),
      SIMD4(-46,-90,124,145),
      SIMD4(Float(j.gx),Float(j.gy),Float(j.px),Float(j.py)),
      SIMD4(Float(j.sq),Float(frame.swirl),Float(frame.lx),Float(frame.ly)),
      SIMD4(Float(dish.width),Float(dish.height),Float(scale),8),
      SIMD4(-143.5,-209.5,Float(frame.pulse),0),
      SIMD4(Float(view.bounds.width),Float(view.bounds.height),Float(scale),0)
    ]
    encoder.setRenderPipelineState(pipeline)
    u.withUnsafeBytes { bytes in
      if let base = bytes.baseAddress { encoder.setFragmentBytes(base,length:bytes.count,index:0) }
    }
    encoder.setFragmentTexture(dish,index:0); encoder.setFragmentTexture(label,index:1)
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    encoder.endEncoding(); command.present(drawable); command.commit()
  }
}
