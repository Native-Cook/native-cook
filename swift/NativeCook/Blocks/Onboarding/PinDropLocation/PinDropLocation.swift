import MetalKit
import SwiftUI

struct PinDropLocation: View {
  static let loop = PinDropLocationMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var phase
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"pinDropLocationTime") {
      return (value as? NSNumber)?.doubleValue ?? (value as? String).flatMap(Double.init)
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      PinDropLocationSurface(autoPlay:autoPlay,reduced:reduced,active:phase == .active,pinned:pinned)
        .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
    }
    .ignoresSafeArea()
    .preferredColorScheme(.dark)
  }
}
struct PinDropLocationSurface: UIViewRepresentable {
  var autoPlay: Bool, reduced: Bool, active: Bool
  var pinned: Double?
  func makeUIView(context:Context) -> PinDropLocationSurfaceView { PinDropLocationSurfaceView() }
  func updateUIView(_ view:PinDropLocationSurfaceView,context:Context) {
    view.driver.configure(autoPlay,reduced)
    view.active=active; view.pinned=pinned; view.refresh(); view.wake()
  }
  static func dismantleUIView(_ view:PinDropLocationSurfaceView,coordinator:()) { view.stop() }
}
struct PinDropLocationUniforms {
  var view, disc, spin, pin: SIMD4<Float>
  init(_ l:PinDropLocationLayout,_ s:PinDropLocationFrame,_ scale:Double,_ time:Double) {
    view = .init(Float(l.w),Float(l.h),Float(scale),Float(time.truncatingRemainder(dividingBy:600)))
    disc = .init(Float(l.cx),Float(l.cy),Float(l.radius),Float(s.bend))
    spin = .init(Float(s.lon),Float(s.lat),Float(s.zoom),Float(s.places))
    // RN sends zero for hover, despite frameAt's unused hover channel.
    pin = .init(Float(s.fall),0,Float(s.since),Float(s.lights))
  }
}
final class PinDropLocationDisplayTarget: NSObject {
  weak var view: PinDropLocationSurfaceView?
  @objc func tick(_ link:CADisplayLink) { view?.tick(link) }
}
final class PinDropLocationSurfaceView: UIView,MTKViewDelegate,UIGestureRecognizerDelegate {
  let driver=PinDropLocationDriver()
  var pinned:Double?
  var active=true
  private let metal:MTKView
  private let background=UIImageView()
  private let ink=PinDropLocationInkView()
  private let queue:MTLCommandQueue?
  private let mapPipeline:MTLRenderPipelineState?
  private let sparkPipeline:MTLRenderPipelineState?
  private let computePipeline:MTLComputePipelineState?
  private let sparkBuffer:MTLBuffer?
  private var link:CADisplayLink?
  private let target=PinDropLocationDisplayTarget()
  private let velocityPan=UIPanGestureRecognizer()
  private var previous=0.0
  private var cachedLayout:PinDropLocationLayout?
  private var accessibilityAllowed:Bool?
  private var layout:PinDropLocationLayout {
    .init(bounds.width,bounds.height,Double(window?.safeAreaInsets.top ?? safeAreaInsets.top),Double(window?.safeAreaInsets.bottom ?? safeAreaInsets.bottom))
  }
  override init(frame:CGRect) {
    let device=MTLCreateSystemDefaultDevice()
    metal=MTKView(frame:.zero,device:device); queue=device?.makeCommandQueue()
    metal.colorPixelFormat = .bgra8Unorm
    metal.isOpaque=false; metal.backgroundColor = .clear
    metal.clearColor=MTLClearColorMake(0,0,0,0)
    metal.isPaused=true; metal.enableSetNeedsDisplay=true; metal.framebufferOnly=true
    (metal.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
    let library=device?.makeDefaultLibrary()
    func pipeline(_ vertex:String,_ fragment:String) -> MTLRenderPipelineState? {
      let d=MTLRenderPipelineDescriptor()
      d.vertexFunction=library?.makeFunction(name:vertex); d.fragmentFunction=library?.makeFunction(name:fragment)
      let color=d.colorAttachments[0]!
      color.pixelFormat = .bgra8Unorm; color.isBlendingEnabled=true
      color.rgbBlendOperation = .add; color.alphaBlendOperation = .add
      color.sourceRGBBlendFactor = .one; color.destinationRGBBlendFactor = .oneMinusSourceAlpha
      color.sourceAlphaBlendFactor = .one; color.destinationAlphaBlendFactor = .oneMinusSourceAlpha
      do { return try device?.makeRenderPipelineState(descriptor:d) }
      catch { assertionFailure("PinDropLocation render pipeline: \(error)"); return nil }
    }
    mapPipeline=pipeline("pinDropLocationFullVs","pinDropLocationMapFs")
    sparkPipeline=pipeline("pinDropLocationSparkVs","pinDropLocationSparkFs")
    if let fn=library?.makeFunction(name:"pinDropLocationSparksCs") {
      computePipeline=try? device?.makeComputePipelineState(function:fn)
    } else { computePipeline=nil }
    sparkBuffer=device?.makeBuffer(length:240*16,options:.storageModeShared)
    super.init(frame:frame)
    clipsToBounds=true; backgroundColor=PinDropLocationResources.color(0x0A071A)
    addSubview(background); addSubview(metal); addSubview(ink)
    for view in [background,metal,ink] { view.isUserInteractionEnabled=false }
    metal.delegate=self; target.view=self; isMultipleTouchEnabled=false
    // RNGH reads release velocity from UIPanGestureRecognizer. Observe it without consuming touches.
    velocityPan.addTarget(self,action:#selector(observePan(_:)))
    velocityPan.cancelsTouchesInView=false; velocityPan.delaysTouchesBegan=false; velocityPan.delaysTouchesEnded=false
    velocityPan.maximumNumberOfTouches=1; velocityPan.delegate=self; addGestureRecognizer(velocityPan)
  }
  @objc private func observePan(_ pan:UIPanGestureRecognizer) {}
  func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer) -> Bool { true }
  required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
  override func layoutSubviews() {
    super.layoutSubviews(); background.frame=bounds; metal.frame=bounds; ink.frame=bounds
    metal.contentScaleFactor=window?.screen.scale ?? traitCollection.displayScale
    metal.drawableSize=CGSize(width:bounds.width*metal.contentScaleFactor,height:bounds.height*metal.contentScaleFactor)
    refresh()
  }
  override func didMoveToWindow() { super.didMoveToWindow(); refresh(); wake() }
  func stop() { link?.invalidate(); link=nil; previous=0 }
  func wake() {
    guard active,window != nil,pinned == nil,driver.moving else { stop(); return }
    if link == nil {
      let next=CADisplayLink(target:target,selector:#selector(PinDropLocationDisplayTarget.tick(_:)))
      next.add(to:.main,forMode:.common); link=next; previous=0
    }
  }
  func tick(_ sender:CADisplayLink) {
    let dt=previous == 0 ? 0 : min(0.5,max(0,sender.timestamp-previous)); previous=sender.timestamp
    let before=visibleFrame, b0=driver.buttonAmount(0), b1=driver.buttonAmount(1)
    driver.step(dt,layout,pinned)
    // The Metal view is paused throughout. Static holds advance the clock without drawing.
    if before != visibleFrame || b0 != driver.buttonAmount(0) || b1 != driver.buttonAmount(1)
      || (driver.state.since >= 0 && driver.state.lights > 0 && !driver.reduced) { render() }
    if !driver.moving { stop() }
  }
  private var visibleFrame:PinDropLocationFrame {
    var f=driver.state
    f.hover=0; f.allowed=driver.allowed ? 1 : 0
    if f.since >= 1.6 && f.lights == 0 { f.since = -1 }
    return f
  }
  func refresh() {
    guard bounds.width>0,bounds.height>0 else { return }
    let l=layout
    if l != cachedLayout { background.image=PinDropLocationResources.shared.background(l,Double(metal.contentScaleFactor)); cachedLayout=l }
    driver.step(0,l,pinned); render()
  }
  private func render() {
    metal.setNeedsDisplay(); ink.configure(layout,driver,Double(metal.contentScaleFactor)); updateAccessibility()
  }
  func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize) { view.setNeedsDisplay() }
  func draw(in view:MTKView) {
    guard let mapPipeline,let sparkPipeline,let computePipeline,let sparkBuffer,
      let pass=view.currentRenderPassDescriptor,let drawable=view.currentDrawable,let command=queue?.makeCommandBuffer() else { return }
    var u=PinDropLocationUniforms(layout,driver.state,Double(view.contentScaleFactor),pinned ?? driver.now)
    if driver.reduced { u.pin.z = -1 }
    if let compute=command.makeComputeCommandEncoder() {
      compute.setComputePipelineState(computePipeline)
      compute.setBytes(&u,length:MemoryLayout<PinDropLocationUniforms>.stride,index:0)
      compute.setBuffer(sparkBuffer,offset:0,index:1)
      compute.dispatchThreadgroups(MTLSize(width:4,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1))
      compute.endEncoding()
    }
    guard let e=command.makeRenderCommandEncoder(descriptor:pass) else { return }
    e.setRenderPipelineState(mapPipeline)
    e.setFragmentBytes(&u,length:MemoryLayout<PinDropLocationUniforms>.stride,index:0)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    e.setRenderPipelineState(sparkPipeline)
    e.setVertexBytes(&u,length:MemoryLayout<PinDropLocationUniforms>.stride,index:0)
    e.setVertexBuffer(sparkBuffer,offset:0,index:1)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:240)
    e.endEncoding(); command.present(drawable); command.commit()
  }
  override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard pinned == nil,let p=touches.first?.location(in:self) else { return }
    driver.begin(p,layout); refresh(); wake()
  }
  override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard pinned == nil,let p=touches.first?.location(in:self) else { return }
    driver.move(p,layout); refresh(); wake()
  }
  override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard pinned == nil,let p=touches.first?.location(in:self) else { return }
    let velocity=velocityPan.state == .possible || velocityPan.state == .failed ? nil : Double(velocityPan.velocity(in:self).x)
    driver.end(p,layout,true,velocity); refresh(); wake()
  }
  override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard pinned == nil,let p=touches.first?.location(in:self) else { return }
    driver.end(p,layout,false); refresh(); wake()
  }
  private var accessibilityLayout:PinDropLocationLayout?
  private var accessibilityCount = -1
  private func updateAccessibility() {
    let l=layout, count=(0..<3).filter { driver.state.places*1.6-Double($0)*0.25>0 }.count
    if accessibilityAllowed == driver.allowed && accessibilityLayout == l && accessibilityCount == count { return }
    accessibilityAllowed=driver.allowed; accessibilityLayout=l; accessibilityCount=count
    func element(_ label:String,_ rect:CGRect,_ traits:UIAccessibilityTraits = [],_ action:(()->Void)? = nil) -> PinDropLocationAccessibilityElement {
      let e=PinDropLocationAccessibilityElement(accessibilityContainer:self)
      e.accessibilityLabel=label; e.accessibilityFrameInContainerSpace=rect; e.accessibilityTraits=traits; e.action=action; return e
    }
    let map=element(driver.allowed ? "Map zoomed in on your pin, with three places nearby" : "Map with your pin",l.map,.adjustable)
    map.accessibilityHint="Drag to spin the globe, tap to drop the pin again"
    map.action={ [weak self] in self?.adjust(0) }
    map.adjust={ [weak self] direction in self?.adjust(direction) }
    var elements:[UIAccessibilityElement]=[map]
    for i in 0..<count {
      let p=PinDropLocationResources.positions[i], side=PinDropLocationResources.sides[i]
      elements.append(element(PinDropLocationResources.names[i],CGRect(x:l.cx+p.x*l.radius+(side<0 ? -190 : 10),y:l.cy+p.y*l.radius-13,width:180,height:26)))
    }
    elements.append(element("Skip",l.skip,.button))
    elements.append(element("LOCATION",CGRect(x:0,y:l.y(482)-11,width:l.w,height:22),.header))
    elements.append(element(PinDropLocationResources.body,CGRect(x:11*l.sx,y:l.y(539)-14,width:380*l.sx,height:84)))
    let button=element(driver.allowed ? "Location on" : "Allow",l.allow,driver.allowed ? [.button,.notEnabled] : .button) { [weak self] in
      guard let self,self.pinned == nil,!self.driver.allowed else { return }; self.driver.allow(); self.refresh(); self.wake()
    }
    elements.append(button); accessibilityElements=elements
  }
  private func adjust(_ direction:Int) {
    guard pinned == nil else { return }; driver.accessibility(direction); refresh(); wake()
  }
}
final class PinDropLocationAccessibilityElement:UIAccessibilityElement {
  var action:(()->Void)?
  var adjust:((Int)->Void)?
  override func accessibilityActivate() -> Bool { action?(); return true }
  override func accessibilityIncrement() { adjust?(1) }
  override func accessibilityDecrement() { adjust?(-1) }
}
