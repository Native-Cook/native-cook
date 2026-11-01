import SwiftUI
import UIKit

struct TrophyFanCaseTouchView: UIViewRepresentable {
  let hand: TrophyFanCaseMotion.Hand
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  var onOpen: (Int) -> Void
  func makeUIView(context: Context) -> TrophyFanCaseTouchSurface {
    let view = TrophyFanCaseTouchSurface()
    view.onEvent = onEvent
    view.onOpen = onOpen
    view.update(hand)
    return view
  }
  func updateUIView(_ view: TrophyFanCaseTouchSurface, context: Context) {
    view.onEvent = onEvent
    view.onOpen = onOpen
    view.update(hand)
  }
}

final class TrophyFanCaseAccessibilityCard: UIAccessibilityElement {
  var activate: (() -> Void)?
  override func accessibilityActivate() -> Bool { activate?(); return true }
}

// RN's Pan(minDistance: 0) begins on contact. Retain UIPan's native velocity estimator.
final class TrophyFanCasePanRecognizer: UIPanGestureRecognizer {
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesBegan(touches, with: event)
    if state == .possible { state = .began }
  }
}

final class TrophyFanCaseTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
  var onOpen: ((Int) -> Void)?
  private var cards: [TrophyFanCaseAccessibilityCard] = []
  private var lastTop = -1
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    let gesture = TrophyFanCasePanRecognizer(target: self, action: #selector(handle(_:)))
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
    for (i, d) in TrophyFanCaseTheme.trophies.enumerated() {
      let element = TrophyFanCaseAccessibilityCard(accessibilityContainer: self)
      element.accessibilityTraits = .button
      element.accessibilityLabel = "\(d.title), \(d.time)"
      element.accessibilityHint = "Turns the card over"
      element.activate = { [weak self] in self?.onOpen?(i) }
      cards.append(element)
    }
    accessibilityElements = cards
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func update(_ hand: TrophyFanCaseMotion.Hand) {
    for i in 0..<4 {
      cards[i].accessibilityFrameInContainerSpace = TrophyFanCaseArt.bounds.applying(TrophyFanCaseArt.transform(hand.cards[i]))
    }
    if lastTop != hand.top {
      lastTop = hand.top
      let order = (0..<4).filter { $0 != hand.top } + (hand.top >= 0 ? [hand.top] : [])
      accessibilityElements = order.map { cards[$0] }
    }
  }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                         shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ gesture: TrophyFanCasePanRecognizer) {
    let point = gesture.location(in: self)
    let translation = gesture.translation(in: self)
    let velocity = gesture.velocity(in: self)
    onEvent?(gesture.state, point, CGSize(width: translation.x, height: translation.y),
             CGVector(dx: velocity.x, dy: velocity.y))
  }
}
