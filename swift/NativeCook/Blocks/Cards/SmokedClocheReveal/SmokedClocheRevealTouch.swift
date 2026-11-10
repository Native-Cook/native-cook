import UIKit

final class SmokedClocheRevealTouch: UIView {
  var event: ((Int,CGPoint,Double,Double)->Void)?
  var activate: (()->Void)?
  private var start=CGPoint.zero,previous=CGPoint.zero
  private var stamp=0.0,velocity=0.0,dragged=false
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return }
    start=t.location(in:window);previous=start;stamp=t.timestamp;velocity=0;dragged=false
    self.event?(0,start,0,0)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return }
    let p=t.location(in:window),dy=p.y-start.y
    velocity=(p.y-previous.y)/max(0.001,t.timestamp-stamp);previous=p;stamp=t.timestamp
    if !dragged && hypot(p.x-start.x,dy)>=4 { dragged=true;self.event?(1,p,dy,velocity) }
    else if dragged { self.event?(2,p,dy,velocity) }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let t=touches.first else { return }
    let p=t.location(in:window)
    if t.timestamp-stamp > 0.1 { velocity=0 }
    self.event?(3,p,p.y-start.y,velocity)
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { self.event?(4,previous,0,0) }
  override func accessibilityActivate() -> Bool { activate?();return true }
}
