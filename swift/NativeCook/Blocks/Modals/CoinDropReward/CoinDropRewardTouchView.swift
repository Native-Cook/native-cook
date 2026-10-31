import SwiftUI
import UIKit

struct CoinDropRewardAccessibilityItem {
  var label: String
  var hint: String? = nil
  var rect: CGRect
  var traits: UIAccessibilityTraits = .staticText
  var action: (() -> Void)? = nil
}

final class CoinDropRewardAccessibilityElement: UIAccessibilityElement {
  var action: (() -> Void)?
  override func accessibilityActivate() -> Bool {
    guard let action else { return false }; action(); return true
  }
}

struct CoinDropRewardTouchView: UIViewRepresentable {
  var items: [CoinDropRewardAccessibilityItem]
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> CoinDropRewardTouchSurface {
    let view = CoinDropRewardTouchSurface(); view.onEvent = onEvent; view.updateAccessibility(items); return view
  }
  func updateUIView(_ view: CoinDropRewardTouchSurface,context: Context) { view.onEvent = onEvent; view.updateAccessibility(items) }
}

// Zero-delay recognition cooperates with the gallery's simultaneous back-swipe recognizer.
final class CoinDropRewardTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State,CGPoint,CGSize,CGVector)->Void)?
  private var accessible: [CoinDropRewardAccessibilityElement] = []
  func updateAccessibility(_ items: [CoinDropRewardAccessibilityItem]) {
    while accessible.count < items.count { accessible.append(CoinDropRewardAccessibilityElement(accessibilityContainer:self)) }
    while accessible.count > items.count { accessible.removeLast() }
    for (element,item) in zip(accessible,items) {
      element.accessibilityLabel = item.label; element.accessibilityHint = item.hint
      element.accessibilityFrameInContainerSpace = item.rect
      element.accessibilityTraits = item.traits; element.action = item.action
    }
    accessibilityElements = accessible
  }
  private var origin = CGPoint.zero, previous = CGPoint.zero
  private var previousTime = 0.0
  private var velocity = CGVector.zero
  override init(frame: CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
    let recognizer = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    recognizer.minimumPressDuration = 0; recognizer.allowableMovement = .greatestFiniteMagnitude
    recognizer.cancelsTouchesInView = false; recognizer.delegate = self; addGestureRecognizer(recognizer)
  }
  @available(*,unavailable) required init?(coder:NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
  @objc private func handle(_ recognizer:UILongPressGestureRecognizer) {
    let point = recognizer.location(in:self), time = ProcessInfo.processInfo.systemUptime
    if recognizer.state == .began { origin = point; previous = point; previousTime = time; velocity = .zero }
    else if recognizer.state == .changed {
      let dt = max(0.001,time-previousTime)
      velocity = CGVector(dx:(point.x-previous.x)/dt,dy:(point.y-previous.y)/dt); previous = point; previousTime = time
    }
    onEvent?(recognizer.state,point,CGSize(width:point.x-origin.x,height:point.y-origin.y),velocity)
  }
}
