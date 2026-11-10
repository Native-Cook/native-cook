import MetalKit
import SwiftUI

struct PearlShellPremiumUniforms {
  var res: SIMD2<Float>, center: SIMD2<Float>
  var scale: Float, yaw: Float, open: Float, glow: Float, lift: Float, spin: Float, phase: Float, px: Float
  init(_ l: PearlShellPremiumTheme.Layout, _ f: PearlShellPremiumMotion.Frame, _ pixel: Float) {
    res = .init(Float(l.w),Float(l.h));center = .init(Float(l.heroX),Float(l.heroY))
    scale=Float(l.heroS);yaw=Float(f.yaw);open=Float(f.open);glow=Float(f.glow)
    lift=Float(f.lift);spin=Float(f.spin);phase=Float(f.phase);px=pixel
  }
}

struct PearlShellPremiumSurface: UIViewRepresentable {
  var layout: PearlShellPremiumTheme.Layout
  var frame: PearlShellPremiumMotion.Frame
  func makeCoordinator() -> PearlShellPremiumRenderer { PearlShellPremiumRenderer() }
  func makeUIView(context: Context) -> MTKView {
    let view=MTKView(frame:.zero,device:context.coordinator.device)
    view.isPaused=true;view.enableSetNeedsDisplay=true;view.framebufferOnly=true
    view.colorPixelFormat = .bgra8Unorm
    view.clearColor=MTLClearColor(red:0.04,green:0.06,blue:0.12,alpha:1)
    view.autoResizeDrawable=true
    view.isUserInteractionEnabled=false
    (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.displayP3)
    view.delegate=context.coordinator
    return view
  }
  func updateUIView(_ view: MTKView, context: Context) {
    let scale=view.window?.screen.scale ?? UIScreen.main.scale
    context.coordinator.uniforms = .init(layout,frame,Float(1/scale))
    view.setNeedsDisplay()
  }
}

final class PearlShellPremiumRenderer: NSObject, MTKViewDelegate {
  let device=MTLCreateSystemDefaultDevice()
  private var queue: MTLCommandQueue?
  private var pipeline: MTLRenderPipelineState?
  var uniforms: PearlShellPremiumUniforms?
  override init() {
    super.init()
    guard let device else { return }
    queue=device.makeCommandQueue()
    do {
      let library=try device.makeDefaultLibrary(bundle:.main)
      let descriptor=MTLRenderPipelineDescriptor()
      descriptor.vertexFunction=library.makeFunction(name:"pearlShellPremiumVertex")
      descriptor.fragmentFunction=library.makeFunction(name:"pearlShellPremiumFragment")
      descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
      pipeline=try device.makeRenderPipelineState(descriptor:descriptor)
    } catch { assertionFailure("PearlShellPremium pipeline: \(error)") }
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { view.setNeedsDisplay() }
  func draw(in view: MTKView) {
    guard let pipeline, var uniforms, let pass=view.currentRenderPassDescriptor,
      let drawable=view.currentDrawable, let command=queue?.makeCommandBuffer(),
      let encoder=command.makeRenderCommandEncoder(descriptor:pass) else { return }
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&uniforms,length:MemoryLayout<PearlShellPremiumUniforms>.stride,index:0)
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    encoder.endEncoding();command.present(drawable);command.commit()
  }
}

// Raw touch-down takes over autoplay even before the 2 pt pan threshold.
struct PearlShellPremiumTouchSurface: UIViewRepresentable {
  var action: (Int,CGPoint,Double)->Void
  func makeUIView(context: Context) -> PearlShellPremiumTouchView {
    let view=PearlShellPremiumTouchView();view.action=action;return view
  }
  func updateUIView(_ view: PearlShellPremiumTouchView, context: Context) { view.action=action }
}
final class PearlShellPremiumTouchView: UIView {
  var action: ((Int,CGPoint,Double)->Void)?
  private var origin=CGPoint.zero
  private let pan=PearlShellPremiumPanRecognizer()
  override init(frame: CGRect) {
    super.init(frame:frame)
    pan.cancelsTouchesInView=false
    pan.delaysTouchesBegan=false;pan.delaysTouchesEnded=false
    pan.maximumNumberOfTouches=1
    addGestureRecognizer(pan)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return }
    origin=t.location(in:self);action?(0,.zero,0)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return }; send(t,1)
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return };send(t,2)
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    action?(3,.zero,0)
  }
  private func send(_ t: UITouch, _ state: Int) {
    let point=t.location(in:self)
    // RNGH on iOS also obtains release velocity from UIPanGestureRecognizer.
    let velocity=pan.velocity(in:self).y
    action?(state,CGPoint(x:point.x-origin.x,y:point.y-origin.y),velocity)
  }
}

final class PearlShellPremiumPanRecognizer: UIPanGestureRecognizer {
  private var origin=CGPoint.zero
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    if let point=touches.first?.location(in:view) { origin=point }
    super.touchesBegan(touches,with:event)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesMoved(touches,with:event)
    if state == .possible, let point=touches.first?.location(in:view),
      hypot(point.x-origin.x,point.y-origin.y)>=2 { state = .began }
  }
}
