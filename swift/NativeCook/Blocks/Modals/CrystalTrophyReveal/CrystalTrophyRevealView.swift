import MetalKit
import QuartzCore
import UIKit

/// Metal owns the frame clock. All material layers and text consume the same simulation snapshot.
final class CrystalTrophyRevealView: UIView, MTKViewDelegate, UIGestureRecognizerDelegate {
  var motion: CrystalTrophyRevealMotion
  var onClose: (() -> Void)?
  var onShare: (() -> Void)?
  private let metal: MTKView
  private let typography = CrystalTrophyRevealType()
  private let pipeline: MTLRenderPipelineState
  private let queue: MTLCommandQueue
  private let engraving: MTLTexture
  private let marble: MTLTexture
  private var lastTime: CFTimeInterval?
  private var warmFrames = 0
  private var pointer = CGPoint.zero
  private var startPoint = CGPoint.zero
  private var pointerTime = 0.0
  private var velocity = 0.0
  private var pointerActive = false
  private var mode = "background"
  private var lastPhase: CrystalTrophyRevealMotion.Phase?
  private var semanticElements: [CrystalTrophyRevealAccessibilityElement] = []
  private var layout: CrystalTrophyRevealLayout {
    CrystalTrophyRevealLayout(size: bounds.size, insets: safeAreaInsets)
  }
  private let softHaptic = UIImpactFeedbackGenerator(style: .soft)
  private let mediumHaptic = UIImpactFeedbackGenerator(style: .medium)
  private let lightHaptic = UIImpactFeedbackGenerator(style: .light)
  private let selectionHaptic = UISelectionFeedbackGenerator()
  private let successHaptic = UINotificationFeedbackGenerator()

  init(autoPlay: Bool, reduced: Bool) {
    motion = CrystalTrophyRevealMotion(auto: autoPlay, reduced: reduced)
    guard let device = MTLCreateSystemDefaultDevice(),
      let queue = device.makeCommandQueue(),
      let library = device.makeDefaultLibrary()
    else {
      fatalError("Crystal Trophy Reveal requires Metal")
    }
    self.queue = queue
    metal = MTKView(frame: .zero, device: device)
    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = library.makeFunction(name: "crystalTrophyRevealVertex")
    descriptor.fragmentFunction = library.makeFunction(name: "crystalTrophyRevealFragment")
    descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
    do {
      pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
      let loader = MTKTextureLoader(device: device)
      // The packed greyscale strip must never be sampled through sRGB decoding.
      let options: [MTKTextureLoader.Option: Any] = [
        .SRGB: false, .generateMipmaps: false, .origin: MTKTextureLoader.Origin.topLeft,
      ]
      engraving = try loader.newTexture(
        URL: Bundle.main.url(forResource: "trophy-engrave", withExtension: "png")!, options: options
      )
      marble = try loader.newTexture(
        URL: Bundle.main.url(forResource: "trophy-marble", withExtension: "png")!, options: options)
    } catch {
      fatalError("Crystal Trophy Reveal resources: \(error)")
    }
    super.init(frame: .zero)
    backgroundColor = .black
    metal.colorPixelFormat = .bgra8Unorm
    metal.preferredFramesPerSecond = 60
    metal.framebufferOnly = true
    metal.autoResizeDrawable = true
    metal.isUserInteractionEnabled = false
    metal.delegate = self
    addSubview(metal)
    addSubview(typography)
    let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handleGesture(_:)))
    gesture.minimumPressDuration = 0
    gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
    accessibilityViewIsModal = true
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  func setActive(_ active: Bool) {
    metal.isPaused = !active
    if !active { lastTime = nil }
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    metal.frame = bounds
    typography.frame = bounds
    updateAccessibility()
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

  func draw(in view: MTKView) {
    guard bounds.width > 0, bounds.height > 0,
      let drawable = view.currentDrawable,
      let pass = view.currentRenderPassDescriptor,
      let command = queue.makeCommandBuffer(),
      let encoder = command.makeRenderCommandEncoder(descriptor: pass)
    else { return }
    let now = CACurrentMediaTime()
    let dt = lastTime.map { now - $0 } ?? 1 / 60
    lastTime = now
    let f = layout
    if warmFrames >= 2 {
      motion.advance(dt, width: f.width)
    } else {
      warmFrames += 1
    }
    if pointerActive && mode == "trophy" { move(pointer, velocity: velocity) }
    reportEvents()
    if lastPhase != motion.phase {
      lastPhase = motion.phase
      updateAccessibility()
    }
    for element in semanticElements {
      if element.target == "trophy" {
        element.accessibilityValue = "\(Int(motion.yaw * 180 / .pi)) degrees"
      }
      if element.target == "share" {
        element.accessibilityValue = motion.copied > 0.5 ? "Link copied" : ""
      }
    }
    var uniforms = f.uniforms(motion, pixelRatio: view.drawableSize.width / max(1, f.width))
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(
      &uniforms, length: uniforms.count * MemoryLayout<SIMD4<Float>>.stride, index: 0)
    encoder.setFragmentTexture(engraving, index: 0)
    encoder.setFragmentTexture(marble, index: 1)
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    command.present(drawable)
    command.commit()
    typography.motion = motion
    typography.composition = f
    typography.setNeedsDisplay()
  }

  private func reportEvents() {
    let events = motion.events
    motion.events = 0
    if events & 1 != 0 { softHaptic.impactOccurred() }
    if events & 2 != 0 { mediumHaptic.impactOccurred() }
    if events & 4 != 0 { lightHaptic.impactOccurred() }
    if events & 8 != 0 { successHaptic.notificationOccurred(.success) }
    if events & 16 != 0 { selectionHaptic.selectionChanged() }
    if events & 32 != 0 { onClose?() }
    if events & 64 != 0 { onShare?() }
  }

  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }

  @objc private func handleGesture(_ gesture: UILongPressGestureRecognizer) {
    let location = gesture.location(in: self)
    let now = CACurrentMediaTime()
    switch gesture.state {
    case .began:
      begin(location)
      pointerTime = now
    case .changed:
      let dt = max(0.001, now - pointerTime)
      let nextVelocity = (location.x - pointer.x) / dt
      move(location, velocity: nextVelocity)
      pointerTime = now
    case .ended:
      if now - pointerTime > 0.1 { velocity = 0 }
      end(cancelled: false)
    case .cancelled, .failed:
      end(cancelled: true)
    default: break
    }
  }

  // These handlers are also exercised by the temporary verification driver.
  func begin(_ point: CGPoint) {
    guard !pointerActive else { return }
    motion.interrupt(true)
    pointer = point
    startPoint = point
    velocity = 0
    pointerActive = true
    mode = "background"
    let f = layout
    if motion.phase == .closed {
      mode = "curtain"
    } else if motion.phase == .open {
      if f.share.contains(point) {
        mode = "share"
      } else if f.close.insetBy(dx: -2, dy: -2).contains(point) {
        mode = "close"
      } else if f.hitBox.contains(point) {
        mode = "trophy"
      }
    }
    if mode == "trophy" || mode == "curtain" {
      let p = f.stagePoint(point, motion: motion)
      motion.touchDown((p.x - f.originX) / f.unit, (f.originY - p.y) / f.unit)
    }
    pressControls()
  }

  func move(_ point: CGPoint, velocity: Double) {
    guard pointerActive else { return }
    pointer = point
    self.velocity = velocity
    let f = layout
    if mode == "trophy" || mode == "curtain" {
      let p = f.stagePoint(point, motion: motion)
      motion.dragTo(
        point.x - startPoint.x, point.y - startPoint.y, (p.x - f.originX) / f.unit,
        (f.originY - p.y) / f.unit, f.width)
    }
    pressControls()
  }

  private func pressControls() {
    let f = layout
    if mode == "share" {
      motion.pressShare(
        true, x: (pointer.x - f.share.minX) / f.share.width,
        y: (pointer.y - f.share.minY) / f.share.height)
    }
    if mode == "close" {
      motion.pressClose(
        true, x: (pointer.x - f.close.minX - 2) / 36, y: (pointer.y - f.close.minY - 2) / 36)
    }
  }

  func end(cancelled: Bool) {
    guard pointerActive else { return }
    motion.interrupt(false)
    pointerActive = false
    let f = layout
    if mode == "trophy" || mode == "curtain" { motion.release(velocity, cancelled: cancelled) }
    if mode == "share" {
      motion.pressShare(false, activate: !cancelled && f.share.contains(pointer))
    }
    if mode == "close" {
      motion.pressClose(
        false, activate: !cancelled && f.close.insetBy(dx: -2, dy: -2).contains(pointer))
    }
    reportEvents()
  }

  func access(_ target: String, delta: Double? = nil) {
    motion.interrupt(false)
    if target == "curtain" { motion.reveal() }
    if target == "close" { motion.close() }
    if target == "share" { motion.pressShare(false, activate: true) }
    if target == "trophy" && motion.phase == .open {
      if let delta {
        motion.touchDown(0, 1.8)
        motion.dragTo(delta / 0.0062, 0, 0, 1.8, layout.width)
        motion.maxDrag = 20
        motion.release(0)
      } else {
        motion.tapAt(0, 1.8)
      }
    }
    reportEvents()
  }

  private func updateAccessibility() {
    let f = layout
    var items: [CrystalTrophyRevealAccessibilityElement] = []
    func add(_ target: String, _ label: String, _ rect: CGRect, _ traits: UIAccessibilityTraits) {
      let item = CrystalTrophyRevealAccessibilityElement(accessibilityContainer: self)
      item.owner = self
      item.target = target
      item.accessibilityLabel = label
      item.accessibilityTraits = traits
      item.accessibilityFrameInContainerSpace = rect
      items.append(item)
    }
    if motion.phase == .closed {
      add("curtain", "Closed curtain. Tap or pull it apart to reveal the trophy.", bounds, .button)
    } else if motion.phase == .open {
      add(
        "title", "Perfect Month. Won on Oct 13, 2026",
        CGRect(x: 32, y: f.titleY - f.titleSize, width: f.width - 64, height: f.titleSize * 2.2),
        .header)
      add(
        "trophy", "Crystal trophy engraved with 30. Drag to turn it, tap to make it flash.",
        f.hitBox, .adjustable)
      add(
        "body", "Thirty days, thirty sessions. Not a single one missed.",
        CGRect(x: 24, y: f.bodyTop, width: f.width - 48, height: 44), .staticText)
      add("share", "Share this trophy", f.share, .button)
      add("close", "Close", f.close.insetBy(dx: -2, dy: -2), .button)
    }
    semanticElements = items
    accessibilityElements = items
  }
}

final class CrystalTrophyRevealAccessibilityElement: UIAccessibilityElement {
  weak var owner: CrystalTrophyRevealView?
  var target = ""
  override func accessibilityActivate() -> Bool {
    guard ["curtain", "trophy", "share", "close"].contains(target) else { return false }
    owner?.access(target)
    return true
  }
  override func accessibilityIncrement() { owner?.access(target, delta: .pi / 12) }
  override func accessibilityDecrement() { owner?.access(target, delta: -.pi / 12) }
}
