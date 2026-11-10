import SwiftUI
import UIKit

struct SapphireWatchCrownPressControl<Content: View>: View {
  var reduced: Bool
  var scale = 0.96
  @ViewBuilder var content: () -> Content
  @State private var pressed=false
  var body: some View {
    content().scaleEffect(pressed && !reduced ? scale:1).opacity(pressed && reduced ? 0.72:1)
      .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:pressed)
      .overlay(PressView { pressed=$0 })
      .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton).accessibilityAction {}
  }
  struct PressView: UIViewRepresentable {
    var action: (Bool)->Void
    func makeUIView(context: Context) -> Surface { let v=Surface(); v.action=action; return v }
    func updateUIView(_ view: Surface,context: Context) { view.action=action }
    final class Surface: UIControl {
      var action: ((Bool)->Void)?
      override init(frame: CGRect) { super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false }
      @available(*,unavailable) required init?(coder: NSCoder) { nil }
      override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) { action?(true) }
      override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent?) {
        if let p=touches.first?.location(in:self) { action?(bounds.insetBy(dx:-16,dy:-16).contains(p)) }
      }
      override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) { action?(false) }
      override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) { action?(false) }
    }
  }
}
