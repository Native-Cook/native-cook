import SwiftUI
import UIKit

/// Owns one finger and supplies velocity-bearing release, cancellation and one accessible button.
///
/// The finger is tracked by a zero-delay press recogniser, not by raw touches. Raw touches are
/// cancelled the moment any other recogniser on the page begins, such as the gallery's back
/// swipe after 24 points of travel or a scroll view a host wraps around the block, and the hold
/// would end while the finger is still down. A recogniser that allows the others beside it keeps
/// its finger wherever it goes, as the reference's zero-distance pan does.
final class EmberTouchView: UIView, UIGestureRecognizerDelegate {

  override init(frame: CGRect) {
    super.init(frame: frame)
    let press = UILongPressGestureRecognizer(target: self, action: #selector(track))
    press.minimumPressDuration = 0
    press.allowableMovement = .greatestFiniteMagnitude
    press.cancelsTouchesInView = false
    press.delegate = self
    addGestureRecognizer(press)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("EmberTouchView is created in code")
  }

  /// Weak owner; the view never prolongs the display clock’s lifetime.
  weak var motion: EmberMotion?

  /// Responsive page scale used to map finger points back to card points.
  var cardScale = 1.0

  func updateAccessibility() {
    isAccessibilityElement = true
    accessibilityTraits = .button
    let value = String(
      format: EmberL10n.t("a11y.label"),
      EmberL10n.number(
        motion?.reached == true ? (motion?.frame.goal ?? EmberTheme.E.goal) : EmberTheme.E.restSteps
      ),
      motion?.reached == true ? EmberTheme.E.goalBpm : EmberTheme.E.restBpm)
    if accessibilityLabel != value { accessibilityLabel = value }
    accessibilityHint = EmberL10n.t("a11y.hint")
  }

  override func accessibilityActivate() -> Bool {
    motion?.activate()
    return motion != nil
  }

  /// Most recent finger sample in the untransformed wrapper.
  private var point = CGPoint.zero

  /// Time of the previous sample for velocity estimation.
  private var time = 0.0

  /// Finger velocity in wrapper points per second.
  private var velocity = CGSize.zero

  func gestureRecognizer(
    _ recognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
  ) -> Bool {
    true
  }

  @objc private func track(_ press: UILongPressGestureRecognizer) {
    let next = press.location(in: self)
    let now = CACurrentMediaTime()
    switch press.state {
    case .began:
      point = next
      time = now
      velocity = .zero
      motion?.begin(point, scale: cardScale)
    case .changed:
      // Minimum sample interval avoids unbounded velocity from same-timestamp moves.
      let dt = max(0.001, now - time)
      velocity = CGSize(width: (next.x - point.x) / dt, height: (next.y - point.y) / dt)
      point = next
      time = now
      motion?.follow(point, scale: cardScale)
    case .ended:
      // A stationary finger for 100 ms has no release momentum.
      if now - time > 0.1 { velocity = .zero }
      motion?.end(velocity: velocity, scale: cardScale)
    case .cancelled, .failed:
      motion?.end(velocity: .zero, scale: cardScale)
    default:
      break
    }
  }
}
