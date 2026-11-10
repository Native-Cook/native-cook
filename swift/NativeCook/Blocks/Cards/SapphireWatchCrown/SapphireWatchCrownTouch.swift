import SwiftUI
import UIKit

struct SapphireWatchCrownTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State,CGSize,CGVector) -> Void
  func makeUIView(context: Context) -> Surface { let v=Surface(); v.onEvent=onEvent; return v }
  func updateUIView(_ view: Surface,context: Context) { view.onEvent=onEvent }
  final class Surface: UIView {
    var onEvent: ((UIGestureRecognizer.State,CGSize,CGVector) -> Void)?
    private var start=CGPoint.zero, previous=CGPoint.zero
    private var stamp=0.0
    private var velocity=CGVector.zero
    override init(frame: CGRect) { super.init(frame:frame); isOpaque=false; backgroundColor = .clear; isAccessibilityElement=false; isMultipleTouchEnabled=false }
    @available(*,unavailable) required init?(coder: NSCoder) { nil }
    override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) {
      guard let t=touches.first else { return }; start=t.location(in:self); previous=start; stamp=t.timestamp; velocity = .zero
      onEvent?(.began,.zero,.zero)
    }
    override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent?) { send(touches,.changed) }
    override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) { send(touches,.ended) }
    override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) { send(touches,.cancelled) }
    private func send(_ touches: Set<UITouch>,_ phase: UIGestureRecognizer.State) {
      guard let t=touches.first else { return }; let p=t.location(in:self),dt=t.timestamp-stamp
      if dt > 0 && phase == .changed { velocity=CGVector(dx:(p.x-previous.x)/dt,dy:(p.y-previous.y)/dt) }
      previous=p; stamp=t.timestamp
      onEvent?(phase,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
    }
  }
}
