import MetalKit
import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// Hosts the Metal view, runs the block's clock from a display link and turns touches into
/// the pan and tap the experience understands.
final class BoosterPackSceneView: UIView, MTKViewDelegate {
  private let experience: BoosterPackExperience
  private let renderer = BoosterPackRenderer()
  private let metalView = MTKView()
  private var link: CADisplayLink?

  init(experience: BoosterPackExperience) {
    self.experience = experience
    super.init(frame: .zero)
    backgroundColor = UIColor(red: 0.667, green: 0.761, blue: 0.898, alpha: 1)
    isAccessibilityElement = true
    accessibilityLabel = "Booster pack"
    accessibilityHint = "Swipe across the top of the pack to tear it open."

    metalView.device = renderer?.device
    metalView.colorPixelFormat = BoosterPackRenderer.colorFormat
    metalView.depthStencilPixelFormat = BoosterPackRenderer.stencilFormat
    metalView.sampleCount = BoosterPackRenderer.sampleCount
    metalView.isPaused = true
    metalView.enableSetNeedsDisplay = false
    metalView.isUserInteractionEnabled = false
    metalView.presentsWithTransaction = true
    metalView.delegate = self
    metalView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(metalView)
    addGestureRecognizer(BoosterPackTouchRecognizer { [weak self] recognizer, phase in
      self?.touched(recognizer, phase)
    })
    experience.setLoaded(renderer?.loaded ?? false)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

  override func layoutSubviews() {
    super.layoutSubviews()
    metalView.frame = bounds
    experience.layout(width: bounds.width, height: bounds.height)
    renderer?.prepare(width: bounds.width, height: bounds.height)
    // Show the sealed pack at once, before the clock starts.
    experience.refreshScene()
    metalView.draw()
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil { stop() } else { run() }
  }

  private func run() {
    guard link == nil else { return }
    let link = CADisplayLink(target: BoosterPackLinkTarget(self), selector: #selector(BoosterPackLinkTarget.tick(_:)))
    link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
    link.add(to: .main, forMode: .common)
    self.link = link
  }

  func stop() {
    link?.invalidate()
    link = nil
  }

  fileprivate func tick(_ link: CADisplayLink) {
    guard experience.focused else { return }
    experience.step(link.timestamp * 1000)
    let value = experience.interface.opacity > 0
      ? BoosterPackCardReveal.CARD_NAMES[experience.interface.labelIndex] : "Sealed"
    if accessibilityValue != value { accessibilityValue = value }
    // The drawable is presented with a Core Animation transaction, so there has to be one
    // on every frame, also when no layer changed.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    metalView.draw()
    CATransaction.commit()
  }

  func draw(in view: MTKView) {
    renderer?.draw(experience.scene, in: view)
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

  // MARK: Touches

  private func event(_ recognizer: BoosterPackTouchRecognizer) -> BoosterPackDragEvent {
    let point = recognizer.point
    return BoosterPackDragEvent(
      x: point.x, y: point.y, translationX: point.x - recognizer.start.x, translationY: point.y - recognizer.start.y,
      velocityX: recognizer.velocity.x, velocityY: recognizer.velocity.y)
  }

  private func touched(_ recognizer: BoosterPackTouchRecognizer, _ phase: BoosterPackTouchRecognizer.Phase) {
    experience.touchTime(CACurrentMediaTime() * 1000)
    let drag = event(recognizer)
    switch phase {
    case .down:
      experience.panBegan(drag)
    case .moved:
      experience.panChanged(drag)
    case .up, .cancelled:
      experience.panEnded(drag, moved: recognizer.moved, cancelled: phase == .cancelled)
      if phase == .up && !recognizer.moved && recognizer.duration < 0.5 {
        experience.tapped(recognizer.point)
      }
    }
  }
}

/// Follows one finger from touch-down to lift, the way the pan and the tap of the React
/// Native block see it between them: a touch is a pan once it has travelled a point, and a
/// tap if it lifts within half a second without having done so. It recognises alongside
/// other gestures, so a back swipe from the screen's edge still works over the block.
final class BoosterPackTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
  enum Phase { case down, moved, up, cancelled }

  private let handler: (BoosterPackTouchRecognizer, Phase) -> Void
  private var finger: UITouch?
  private var startTime = 0.0
  private var lastTime = 0.0
  private var samples: [(time: Double, point: CGPoint)] = []

  private(set) var start = CGPoint.zero
  private(set) var point = CGPoint.zero
  private(set) var velocity = CGPoint.zero
  private(set) var moved = false
  var duration: Double { lastTime - startTime }

  init(handler: @escaping (BoosterPackTouchRecognizer, Phase) -> Void) {
    self.handler = handler
    super.init(target: nil, action: nil)
    cancelsTouchesInView = false
    delaysTouchesBegan = false
    delaysTouchesEnded = false
    delegate = self
  }

  func gestureRecognizer(
    _ recognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
  ) -> Bool { true }

  private func sample(_ touch: UITouch) {
    point = touch.location(in: view)
    lastTime = touch.timestamp
    samples.append((lastTime, point))
    samples.removeAll { lastTime - $0.time > 0.1 }
    if let first = samples.first, lastTime - first.time > 0.005 {
      let elapsed = lastTime - first.time
      velocity = CGPoint(x: (point.x - first.point.x) / elapsed, y: (point.y - first.point.y) / elapsed)
    }
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    for touch in touches {
      // One finger at a time; any other is left alone.
      guard finger == nil else {
        ignore(touch, for: event)
        continue
      }
      finger = touch
      samples = []
      velocity = .zero
      moved = false
      startTime = touch.timestamp
      sample(touch)
      start = point
      state = .began
      handler(self, .down)
    }
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    guard let finger, touches.contains(finger) else { return }
    sample(finger)
    if !moved && hypot(point.x - start.x, point.y - start.y) >= 1 { moved = true }
    state = .changed
    if moved { handler(self, .moved) }
  }

  private func finish(_ touches: Set<UITouch>, _ phase: Phase) {
    guard let finger, touches.contains(finger) else { return }
    sample(finger)
    self.finger = nil
    state = phase == .up ? .ended : .cancelled
    handler(self, phase)
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches, .up) }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches, .cancelled) }

  override func reset() {
    super.reset()
    // The system can end a gesture without delivering the touch's last event.
    if finger != nil {
      finger = nil
      handler(self, .cancelled)
    }
  }
}

/// A display link keeps its target alive; this keeps the view out of that cycle.
private final class BoosterPackLinkTarget {
  private weak var view: BoosterPackSceneView?
  init(_ view: BoosterPackSceneView) { self.view = view }
  @objc func tick(_ link: CADisplayLink) { view?.tick(link) }
}

/// The scene view in SwiftUI.
struct BoosterPackStage: UIViewRepresentable {
  let experience: BoosterPackExperience

  func makeUIView(context: Context) -> BoosterPackSceneView { BoosterPackSceneView(experience: experience) }
  func updateUIView(_ view: BoosterPackSceneView, context: Context) {}
  static func dismantleUIView(_ view: BoosterPackSceneView, coordinator: ()) { view.stop() }
}
