import SwiftUI
import UIKit

struct AppIconAtelierTouch: UIViewRepresentable {
  enum Event { case down,dragStart,drag,hold,up,cancel }
  var action: (Event,Double,Double) -> Void
  func makeUIView(context: Context) -> AppIconAtelierTouchSurface { let v=AppIconAtelierTouchSurface(); v.action=action; return v }
  func updateUIView(_ view: AppIconAtelierTouchSurface,context: Context) { view.action=action }
}
final class AppIconAtelierTouchSurface: UIView {
  var action: (AppIconAtelierTouch.Event,Double,Double) -> Void = { _,_,_ in }
  private var origin=CGPoint.zero, previous=CGPoint.zero, started=0.0, previousTime=0.0, velocity=0.0
  private var drag=false, hold=false, failed=false
  private var timer: Timer?
  override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) {
    guard let touch=touches.first else { return }; origin=touch.location(in:self); previous=origin
    started=touch.timestamp; previousTime=started; velocity=0; drag=false; hold=false; failed=false
    action(.down,0,0)
    timer=Timer.scheduledTimer(withTimeInterval:0.38,repeats:false) { [weak self] _ in
      guard let self,!self.drag,!self.failed else { return }; self.hold=true; self.action(.hold,0,0)
    }
  }
  override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent?) {
    guard let touch=touches.first else { return }; let p=touch.location(in:self), dx=p.x-origin.x, dy=p.y-origin.y
    velocity=(p.x-previous.x)/max(0.001,touch.timestamp-previousTime); previous=p; previousTime=touch.timestamp
    if hypot(dx,dy)>14 { timer?.invalidate() }
    if !drag && !hold && abs(dy)>24 { failed=true }
    if !drag && !hold && !failed && abs(dx)>10 { drag=true; timer?.invalidate(); action(.dragStart,dx,velocity) }
    if drag { action(.drag,dx,velocity) }
  }
  override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) {
    timer?.invalidate(); if let touch=touches.first { action(.up,touch.location(in:self).x-origin.x,velocity) }
  }
  override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) { timer?.invalidate(); action(.cancel,0,0) }
}
