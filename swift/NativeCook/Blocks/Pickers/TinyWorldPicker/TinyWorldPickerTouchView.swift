import SwiftUI
import UIKit

struct TinyWorldPickerTouchView: UIViewRepresentable {
  var clock: TinyWorldPickerClock
  var width: Double
  var reduced: Bool
  func makeUIView(context: Context) -> TinyWorldPickerTouchSurface {
    TinyWorldPickerTouchSurface()
  }
  func updateUIView(_ v: TinyWorldPickerTouchSurface, context: Context) {
    v.clock = clock; v.screenWidth = width; v.reduced = reduced
  }
}

// Use UIPan's own velocity estimator and RNGH's custom activation rules. RNGH resets the
// translation when the horizontal threshold is crossed, so the initial eight points do not scrub.
final class TinyWorldPickerPanRecognizer: UIPanGestureRecognizer {
  var beganTouch: (() -> Void)?
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    minimumNumberOfTouches = 20
    super.touchesBegan(touches, with: event)
    beganTouch?()
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesMoved(touches, with: event)
    guard state == .possible else { return }
    let t = translation(in: view?.window)
    if t.y < -24 || t.y > 24 { state = .failed; return }
    if t.x < -8 || t.x > 8 {
      minimumNumberOfTouches = 1
      if numberOfTouches >= 1 {
        state = .began
        setTranslation(.zero, in: view)
      }
    }
  }
}

final class TinyWorldPickerTouchSurface: UIView {
  var clock: TinyWorldPickerClock?
  var screenWidth = 0.0
  var reduced = false
  private var startAt = 0.0
  private var panWon = false
  private let pan = TinyWorldPickerPanRecognizer()
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    pan.addTarget(self, action: #selector(panned))
    pan.cancelsTouchesInView = false
    pan.delaysTouchesBegan = false
    pan.beganTouch = { [weak self] in self?.clock?.begin() }
    addGestureRecognizer(pan)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  @objc private func panned() {
    switch pan.state {
    case .began:
      panWon = true; clock?.startDrag()
    case .changed:
      clock?.update(pan.translation(in: self).x, screenWidth, reduced)
    case .ended, .cancelled:
      clock?.end(pan.velocity(in: window).x, screenWidth, reduced)
    default: break
    }
  }
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    startAt = touch.timestamp; panWon = false
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    // Tap has maxDuration only in RN; its default maximum distance is unset.
    if !panWon && touch.timestamp - startAt <= 0.26 { clock?.tap(reduced) }
  }
}
