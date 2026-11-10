import SwiftUI
import UIKit

struct PerfumeInkBloomTouch: UIViewRepresentable {
  var layout: PerfumeInkBloomTheme.Layout
  var clock: PerfumeInkBloomClock
  var disabled=false
  func makeUIView(context: Context) -> PerfumeInkBloomTouchView { PerfumeInkBloomTouchView() }
  func updateUIView(_ v: PerfumeInkBloomTouchView,context: Context) {
    v.clock=clock; v.isUserInteractionEnabled = !disabled; v.update(layout)
  }
}
final class PerfumeInkBloomTouchView: UIView, UIGestureRecognizerDelegate {
  var layout=PerfumeInkBloomTheme.Layout(.init(width:402,height:874),62,34)
  weak var clock: PerfumeInkBloomClock?
  private var oldLayout: PerfumeInkBloomTheme.Layout?
  private var start=CGPoint.zero, dragging=false, controlDown=false, target = -1
  override init(frame: CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isMultipleTouchEnabled=false
    let pan=PerfumeInkBloomPan(target:self,action:#selector(lean(_:)))
    pan.maximumNumberOfTouches=1; pan.cancelsTouchesInView=false
    pan.delaysTouchesBegan=false; pan.delaysTouchesEnded=false; pan.delegate=self
    addGestureRecognizer(pan)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,shouldReceive touch: UITouch) -> Bool {
    boxes().firstIndex { $0.contains(touch.location(in:self)) }==4
  }
  @objc private func lean(_ pan: UIPanGestureRecognizer) {
    let dx=pan.translation(in:self).x
    switch pan.state {
    case .began:
      dragging=true; clock?.beginDrag()
    case .changed:
      clock?.drag(dx,layout.sx)
    case .ended,.cancelled,.failed:
      clock?.release(dx,pan.velocity(in:self).x,layout.sx)
    default: break
    }
  }
  func boxes() -> [CGRect] {
    let l=layout
    return [.init(x:39*l.sx-22,y:l.y(83)-22,width:44,height:44),.init(x:358*l.sx-22,y:l.y(85)-22,width:44,height:44),.init(x:31*l.sx,y:l.y(754),width:77*l.sx,height:76*l.k),.init(x:135*l.sx,y:l.y(754),width:235*l.sx,height:76*l.k),l.touchBox]
  }
  func update(_ l: PerfumeInkBloomTheme.Layout) {
    layout=l
    if oldLayout==l { return }; oldLayout=l
    let b=boxes()
    func element(_ name: String,_ rect: CGRect,_ traits: UIAccessibilityTraits,_ action: Bool=false) -> PerfumeInkBloomAccessibilityElement {
      let e=PerfumeInkBloomAccessibilityElement(accessibilityContainer:self)
      e.accessibilityLabel=name; e.accessibilityFrameInContainerSpace=rect; e.accessibilityTraits=traits
      if action { e.action={ [weak self] in self?.clock?.spray(); return true } }
      return e
    }
    let bottle=element("Azure Tide Bloom. Tap to spray it",b[4],.button,true)
    bottle.accessibilityHint="Drag sideways to tilt the bottle"
    accessibilityElements=[bottle,element("Back",b[0],.button),element("Bag, 1 item",b[1],.button),element("Azure Tide Bloom",.init(x:32*l.sx,y:l.y(476)-21,width:l.w-32*l.sx,height:42),[.header,.staticText]),element("Eau de Parfum",.init(x:32*l.sx,y:l.y(513)-14,width:200,height:30),.staticText),element("Reviews",.init(x:32*l.sx,y:l.y(568)-12,width:120,height:24),.staticText),element("4 out of 5 stars",.init(x:32*l.sx,y:l.y(603)-10.5,width:140,height:21),.staticText),element("Price",.init(x:288*l.sx,y:l.y(567)-12,width:80,height:24),.staticText),element("$148",.init(x:288*l.sx,y:l.y(603)-15,width:80,height:32),.staticText),element(PerfumeInkBloomTheme.body,.init(x:32*l.sx,y:l.y(657)-12.75,width:312*l.sx,height:80),.staticText),element("Save",b[2],.button),element("Buy now",b[3],.button)]
  }
  override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) {
    guard let t=touches.first else { return }
    start=t.location(in:self); dragging=false
    target=boxes().firstIndex { $0.contains(start) } ?? -1
    if target==4 { clock?.takeOver() }
    else if target>=0 { controlDown=true; clock?.setControl(target,true) }
  }
  override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent?) {
    guard let p=touches.first?.location(in:self) else { return }
    if target>=0 && target<4 {
      let down=boxes()[target].insetBy(dx:-16,dy:-16).contains(p)
      if down != controlDown { controlDown=down; clock?.setControl(target,down) }
      return
    }
  }
  override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) {
    if target==4 {
      if !dragging,let p=touches.first?.location(in:self),hypot(p.x-start.x,p.y-start.y)<=10 { clock?.spray() }
    } else if target>=0 { clock?.setControl(target,false) }
    target = -1
  }
  override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) {
    if target>=0 && target<4 { clock?.setControl(target,false) }; target = -1
  }
}
// RNGH's iOS pan uses UIKit velocity, with the block's explicit 6 pt activation distance.
final class PerfumeInkBloomPan: UIPanGestureRecognizer {
  override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent) {
    let pending=state == .possible
    super.touchesMoved(touches,with:event)
    let delta=translation(in:view)
    if pending && (state == .possible || state == .began) && hypot(delta.x,delta.y)>=6 {
      if state == .possible { state = .began }
      // RNGH starts translation at zero when the pan crosses minDistance.
      setTranslation(.zero,in:view)
    }
  }
}
final class PerfumeInkBloomAccessibilityElement: UIAccessibilityElement {
  var action: (() -> Bool)?
  override func accessibilityActivate() -> Bool { action?() ?? true }
}
