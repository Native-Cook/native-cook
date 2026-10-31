import SwiftUI
import UIKit

struct ClayPromptTouchView:UIViewRepresentable {
  var onEvent:(UIGestureRecognizer.State,CGSize,CGVector)->Void
  func makeUIView(context:Context)->ClayPromptTouchSurface { let v=ClayPromptTouchSurface();v.onEvent=onEvent;return v }
  func updateUIView(_ view:ClayPromptTouchSurface,context:Context) { view.onEvent=onEvent }
}
final class ClayPromptTouchSurface:UIView,UIGestureRecognizerDelegate {
  var onEvent:((UIGestureRecognizer.State,CGSize,CGVector)->Void)?
  var start=CGPoint.zero,last=CGPoint.zero,lastTime=0.0,velocity=CGVector.zero
  override init(frame:CGRect) {
    super.init(frame:frame);backgroundColor = .clear;isAccessibilityElement=false
    let g=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    g.minimumPressDuration=0;g.allowableMovement = .greatestFiniteMagnitude;g.cancelsTouchesInView=false;g.delegate=self;addGestureRecognizer(g)
  }
  @available(*,unavailable) required init?(coder:NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
  @objc func handle(_ g:UILongPressGestureRecognizer) {
    let p=g.location(in:self),now=ProcessInfo.processInfo.systemUptime
    if g.state == .began { start=p;last=p;lastTime=now;velocity = .zero }
    else if g.state == .changed {
      let dt=max(0.001,now-lastTime);velocity=CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt);last=p;lastTime=now
    }
    onEvent?(g.state,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
  }
}

struct ClayPromptInput:UIViewRepresentable {
  var value:String
  var onChange:(String)->Void
  var onFocus:(Bool)->Void
  func makeCoordinator()->Coordinator { Coordinator(self) }
  func makeUIView(context:Context)->ClayPromptTextView {
    let v=ClayPromptTextView();v.backgroundColor = .clear;v.textColor = .clear
    v.font=UIFont(name:ClayPromptTheme.fonts["Regular"] ?? "Inter24pt-Regular",size:17)
    v.textContainerInset=UIEdgeInsets(top:2,left:2,bottom:0,right:2);v.textContainer.lineFragmentPadding=0
    v.autocorrectionType = .no;v.autocapitalizationType = .none;v.isScrollEnabled=false
    v.tintColor=UIColor(ClayPromptTheme.accent);v.delegate=context.coordinator
    v.accessibilityLabel="Prompt";v.accessibilityHint="Describe a scene. A cat, a sofa, the moon or a plant become clay."
    return v
  }
  func updateUIView(_ view:ClayPromptTextView,context:Context) { context.coordinator.parent=self;if view.text != value { view.text=value } }
  final class Coordinator:NSObject,UITextViewDelegate {
    var parent:ClayPromptInput
    init(_ parent:ClayPromptInput) { self.parent=parent }
    func textViewDidChange(_ textView:UITextView) { parent.onChange(textView.text) }
    func textViewDidBeginEditing(_ textView:UITextView) { parent.onFocus(true) }
    func textViewDidEndEditing(_ textView:UITextView) { parent.onFocus(false) }
  }
}
final class ClayPromptTextView:UITextView {
  override func caretRect(for position:UITextPosition)->CGRect { .zero }
}
