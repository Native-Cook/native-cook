import UIKit
import UIKit.UIGestureRecognizerSubclass

/// react-native-gesture-handler's Pan on iOS (RNPanHandler.m, 2.32): a
/// UIPanGestureRecognizer whose activation is decided by RNGH's own criteria.
/// With custom criteria (active offsets, min distance) UIKit's own activation
/// is blocked (`minimumNumberOfTouches = 20`) until they are met; fail
/// offsets fail it while it is still possible; `activateAfterLongPress`
/// activates it from a timer and fails it if the finger moves more than 10 pt
/// first (RNGH also sets minDist to 10, so it never activates by moving).
/// Translation and velocity are measured in the window, as RNGH reports them.
@MainActor
final class GlowknobRNPanRecognizer: UIPanGestureRecognizer {
  var activeOffsetX: (Double, Double)?
  var failOffsetY: (Double, Double)?
  var activateAfterLongPress: Double? {
    didSet { minDistSq = activateAfterLongPress != nil ? 100 : nil }
  }
  private var minDistSq: Double?
  var maxPointers = 10

  /// onBegin: a finger touched down (RNGH BEGAN).
  var onBegin: (() -> Void)?
  /// onStart: the gesture activated (RNGH ACTIVE).
  var onStart: ((GlowknobRNPanRecognizer) -> Void)?
  /// onUpdate: an ACTIVE event (also sent on activation, as RNGH does).
  var onUpdate: ((GlowknobRNPanRecognizer) -> Void)?
  /// onEnd: ACTIVE → END/CANCELLED; `success` only for END.
  var onEnd: ((GlowknobRNPanRecognizer, Bool) -> Void)?
  /// onFinalize: the gesture finished, whatever happened after BEGAN.
  var onFinalize: (() -> Void)?

  private var begun = false
  private var wasActive = false
  private var longPressTimer: Timer?

  private var hasCustomActivationCriteria: Bool { activeOffsetX != nil || minDistSq != nil }

  init() {
    super.init(target: nil, action: nil)
    addTarget(self, action: #selector(handle))
    cancelsTouchesInView = false
    delaysTouchesBegan = false
    delaysTouchesEnded = false
  }

  var windowTranslation: CGPoint { translation(in: view?.window) }
  var windowVelocity: CGPoint { velocity(in: view?.window) }
  var locationInView: CGPoint { location(in: view) }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    // maxPointers / onTouchesDown(numberOfTouches > 1 → fail).
    let total = (event.touches(for: self)?.count ?? touches.count)
    minimumNumberOfTouches = hasCustomActivationCriteria ? 20 : 1
    maximumNumberOfTouches = maxPointers
    super.touchesBegan(touches, with: event)
    if total > 1 {
      state = state == .possible ? .failed : .cancelled
      return
    }
    if !begun {
      begun = true
      onBegin?()
    }
    if let delay = activateAfterLongPress {
      longPressTimer?.invalidate()
      longPressTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
        MainActor.assumeIsolated {
          guard let self, self.state == .possible else { return }
          self.state = .began
        }
      }
    }
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesMoved(touches, with: event)
    if state == .possible && shouldFail() {
      state = .failed
      return
    }
    if hasCustomActivationCriteria && state == .possible && shouldActivate() {
      minimumNumberOfTouches = 1
      if numberOfTouches >= 1 {
        state = .began
        setTranslation(.zero, in: view?.window)
      }
    }
  }

  override func reset() {
    longPressTimer?.invalidate()
    longPressTimer = nil
    super.reset()
    if begun {
      begun = false
      wasActive = false
      onFinalize?()
    }
  }

  private func shouldFail() -> Bool {
    let t = windowTranslation
    if activateAfterLongPress != nil && t.x * t.x + t.y * t.y > 100 {
      longPressTimer?.invalidate()
      return true
    }
    if let (lo, hi) = failOffsetY, t.y < lo || t.y > hi { return true }
    return false
  }

  private func shouldActivate() -> Bool {
    let t = windowTranslation
    if let (lo, hi) = activeOffsetX, t.x < lo || t.x > hi { return true }
    if let minDistSq, t.x * t.x + t.y * t.y >= minDistSq { return true }
    return false
  }

  @objc private func handle() {
    switch state {
    case .began:
      wasActive = true
      onStart?(self)
      onUpdate?(self)
    case .changed:
      onUpdate?(self)
    case .ended:
      if wasActive { onEnd?(self, true) }
    case .cancelled, .failed:
      if wasActive { onEnd?(self, false) }
    default:
      break
    }
  }
}

/// react-native-gesture-handler's Tap on iOS (RNTapHandler.m): recognizes on
/// touch up; fails after `maxDuration` (0.5 s), when the finger leaves the
/// view (shouldCancelWhenOutside, on by default for Tap), or moves farther
/// than `maxDist` from where it went down (in window points).
@MainActor
final class GlowknobRNTapRecognizer: UIGestureRecognizer {
  var maxDist: Double?
  var maxDuration = 0.5
  var onEnd: ((GlowknobRNTapRecognizer, Bool) -> Void)?

  private var initial: CGPoint = .zero
  private var timer: Timer?

  init() {
    super.init(target: nil, action: nil)
    addTarget(self, action: #selector(handle))
    cancelsTouchesInView = false
    delaysTouchesEnded = false
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesBegan(touches, with: event)
    if (event.touches(for: self)?.count ?? touches.count) > 1 {
      state = .failed
      return
    }
    initial = location(in: view?.window)
    timer?.invalidate()
    timer = Timer.scheduledTimer(withTimeInterval: maxDuration, repeats: false) { [weak self] _ in
      MainActor.assumeIsolated {
        guard let self, self.state == .possible else { return }
        self.state = .failed
      }
    }
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesMoved(touches, with: event)
    if state == .possible && shouldFail() { state = .failed }
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesEnded(touches, with: event)
    if state == .possible { state = shouldFail() ? .failed : .ended }
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
    super.touchesCancelled(touches, with: event)
    state = .cancelled
  }

  override func reset() {
    timer?.invalidate()
    timer = nil
    super.reset()
  }

  private func shouldFail() -> Bool {
    if let view, !view.bounds.contains(location(in: view)) { return true }
    if let maxDist {
      let p = location(in: view?.window)
      let dx = p.x - initial.x, dy = p.y - initial.y
      if dx * dx + dy * dy > maxDist * maxDist { return true }
    }
    return false
  }

  var locationInView: CGPoint { location(in: view) }

  @objc private func handle() {
    if state == .ended { onEnd?(self, true) }
  }
}

/// The two gestures of a `Gesture.Race`: whichever activates first wins; the
/// other is failed/cancelled (UIKit's default exclusivity on one view).
@MainActor
final class GlowknobRaceDelegate: NSObject, UIGestureRecognizerDelegate {
  func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
    false
  }
}

/// React Native `<Pressable>`: pressIn on touch-down, pressOut on release or
/// when the finger leaves the press rect (hit slop + pressRetentionOffset
/// {20, 20, 20, 30}), onPress on release inside it. Like Pressability, a
/// press-in lasts at least `minPressDuration` (130 ms): a quick tap's pressOut
/// is deferred until then, and pressOut fires before onPress.
@MainActor
final class GlowknobPressableView: UIView {
  var hitSlop: Double = 8
  var onPressIn: (() -> Void)?
  var onPressOut: (() -> Void)?
  var onPress: (() -> Void)?
  var enabled = true
  private var pressed = false
  private var tracking = false
  private var pressedAt: CFTimeInterval = 0
  private let minPressDuration = 0.130

  private func pressIn() {
    pressedAt = CACurrentMediaTime()
    onPressIn?()
  }

  private func pressOut() {
    let wait = minPressDuration - (CACurrentMediaTime() - pressedAt)
    if wait > 0 {
      DispatchQueue.main.asyncAfter(deadline: .now() + wait) { [weak self] in self?.onPressOut?() }
    } else {
      onPressOut?()
    }
  }

  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    bounds.insetBy(dx: -hitSlop, dy: -hitSlop).contains(point)
  }

  private func inPressRect(_ p: CGPoint) -> Bool {
    let r = bounds.insetBy(dx: -hitSlop, dy: -hitSlop)
    return CGRect(x: r.minX - 20, y: r.minY - 20, width: r.width + 40, height: r.height + 50).contains(p)
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard enabled, !tracking, let t = touches.first else { return }
    tracking = true
    _ = t
    pressed = true
    pressIn()
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard tracking, let t = touches.first else { return }
    let inside = inPressRect(t.location(in: self))
    if inside != pressed {
      pressed = inside
      if inside { pressIn() } else { pressOut() }
    }
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard tracking else { return }
    tracking = false
    if pressed {
      pressed = false
      pressOut()
      onPress?()
    }
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard tracking else { return }
    tracking = false
    if pressed {
      pressed = false
      pressOut()
    }
  }
}
