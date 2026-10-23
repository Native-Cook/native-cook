// A simultaneous zero-delay recognizer keeps its finger when the gallery starts a back swipe.
import UIKit

@MainActor final class AuraTouchView: UIView, UIGestureRecognizerDelegate {
  weak var machine: AuraMachine?
  weak var coordinateView: UIView?
  var index = 0
  var face = false
  var scale = 1.0
  var activate: (() -> Void)?
  private var start = CGPoint.zero
  private var pullStart = CGPoint.zero
  private var previous = CGPoint.zero
  private var previousTime = 0.0
  private var velocity = CGPoint.zero
  private var pulling = false
  private var accepted = false
  private var held = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    let press = UILongPressGestureRecognizer(target: self, action: #selector(track(_:)))
    press.minimumPressDuration = 0
    press.allowableMovement = .greatestFiniteMagnitude
    press.numberOfTouchesRequired = 1
    press.cancelsTouchesInView = false
    press.delegate = self
    addGestureRecognizer(press)
    isAccessibilityElement = true
    accessibilityTraits = .button
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    true
  }

  override func accessibilityActivate() -> Bool {
    guard let machine, !machine.arriving else { return false }
    machine.touch()
    if face { machine.pick(index) } else { activate?() }
    return true
  }

  @objc private func track(_ recognizer: UILongPressGestureRecognizer) {
    guard let machine else { return }
    let point = recognizer.location(in: coordinateView)
    let now = CACurrentMediaTime()
    switch recognizer.state {
    case .began:
      machine.touch()
      accepted = !(face || index == 3) || !machine.locked
      start = point
      previous = point
      previousTime = now
      velocity = .zero
      pulling = false
      held = accepted && index >= 0
      if held { machine.press(index, down: true, face: face) }
    case .changed:
      let dx = point.x - start.x
      let dy = point.y - start.y
      let elapsed = max(0.001, now - previousTime)
      velocity = CGPoint(x: (point.x - previous.x) / elapsed, y: (point.y - previous.y) / elapsed)
      previous = point
      previousTime = now
      if accepted && !face && index >= 0 {
        let inside = bounds.insetBy(
          dx: -AuraDesign.touch.retention, dy: -AuraDesign.touch.retention
        )
        .contains(recognizer.location(in: self))
        if held != inside {
          held = inside
          machine.press(index, down: inside)
        }
      }
      if accepted && face {
        if !pulling && hypot(dx, dy) > AuraFlow.views.pullSlop {
          pulling = true
          // Match the pan's translation origin when it becomes active.
          pullStart = point
          machine.beginPull(index)
        }
        if pulling {
          machine.movePull(
            index, dx: (point.x - pullStart.x) / scale, dy: (point.y - pullStart.y) / scale)
        }
      }
    case .ended, .cancelled, .failed:
      let cancelled = recognizer.state != .ended
      if now - previousTime > 0.1 { velocity = .zero }
      if index >= 0 { machine.press(index, down: false, face: face) }
      if face && pulling {
        machine.endPull(index, vx: velocity.x / scale, vy: velocity.y / scale, cancelled: cancelled)
      } else if accepted && !cancelled {
        if face {
          if hypot(point.x - start.x, point.y - start.y) <= AuraFlow.views.pullSlop {
            machine.pick(index)
          }
        } else if bounds.insetBy(dx: -AuraDesign.touch.retention, dy: -AuraDesign.touch.retention)
          .contains(recognizer.location(in: self))
        {
          activate?()
        }
      }
      accepted = false
      held = false
    default:
      break
    }
  }
}
