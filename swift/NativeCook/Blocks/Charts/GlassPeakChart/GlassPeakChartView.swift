import MetalKit
import UIKit
import simd

/// A single display clock publishes the same picture to Metal and native vector type.
final class GlassPeakChartView: UIView, MTKViewDelegate, UIGestureRecognizerDelegate {
  typealias Simulation = GlassPeakChartSimulation
  var state: GlassPeakChartState
  private let metal: MTKView
  private let typeView = GlassPeakChartTypeView()
  private let commandQueue: MTLCommandQueue
  private let pipeline: MTLRenderPipelineState
  private var lastTime: CFTimeInterval?
  private var presentationAge = 0.0
  private var reported = [-1.0, 0, 0, 0, 0, 0]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let selection = UISelectionFeedbackGenerator()
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let heavy = UIImpactFeedbackGenerator(style: .heavy)
  private var action: String?
  private var draggingCard = false
  private var startPoint = CGPoint.zero
  private var previousPoint = CGPoint.zero
  private var previousTime = 0.0
  private var touchStartTime = 0.0
  private var velocity = 0.0
  private lazy var refreshElement = GlassPeakChartAccessibilityElement(accessibilityContainer: self)
  private lazy var periodElement = GlassPeakChartAccessibilityElement(accessibilityContainer: self)
  private lazy var chartElement = GlassPeakChartAccessibilityElement(accessibilityContainer: self)

  init(autoPlay: Bool, reduced: Bool) {
    state = GlassPeakChartState(autoPlay: autoPlay, reduced: reduced)
    guard let device = MTLCreateSystemDefaultDevice(),
      let queue = device.makeCommandQueue(),
      let library = device.makeDefaultLibrary()
    else {
      preconditionFailure("Glass Peak Chart requires Metal")
    }
    commandQueue = queue
    metal = MTKView(frame: .zero, device: device)
    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = library.makeFunction(name: "glassPeakChartVertex")
    descriptor.fragmentFunction = library.makeFunction(name: "glassPeakChartFragment")
    descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
    do {
      pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
    } catch {
      preconditionFailure("Glass Peak Chart material: \(error)")
    }
    super.init(frame: .zero)
    backgroundColor = .black
    metal.colorPixelFormat = .bgra8Unorm
    (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
    metal.clearColor = MTLClearColorMake(0, 0, 0, 1)
    metal.preferredFramesPerSecond = 60
    metal.isUserInteractionEnabled = false
    metal.delegate = self
    addSubview(metal)
    typeView.isUserInteractionEnabled = false
    addSubview(typeView)
    let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
    touch.minimumPressDuration = 0
    touch.allowableMovement = .greatestFiniteMagnitude
    touch.cancelsTouchesInView = false
    touch.delegate = self
    addGestureRecognizer(touch)
    refreshElement.accessibilityLabel = "Refresh data"
    refreshElement.accessibilityTraits = .button
    refreshElement.activate = { [weak self] in self?.activate("refresh") }
    periodElement.accessibilityTraits = .button
    periodElement.accessibilityHint = "Shows the next period"
    periodElement.activate = { [weak self] in self?.activate("period") }
    chartElement.accessibilityLabel = "Conversion rate chart"
    chartElement.accessibilityTraits = .adjustable
    chartElement.adjust = { [weak self] direction in
      guard let self else { return }
      Simulation.adjust(state, direction)
    }
    accessibilityElements = [refreshElement, periodElement, chartElement]
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  func setReducedMotion(_ reduced: Bool) {
    guard state.reduced != reduced else { return }
    let period = state.f.period
    state.reduced = reduced
    Simulation.home(state, period)
    state.f.presented = 1
    state.queue = nil
    state.contact = false
    state.pressPill = false
    state.pressRefresh = false
    state.revision += 1
    if !reduced { Simulation.resetLoop(state) }
  }

  func stop() {
    metal.isPaused = true
    metal.delegate = nil
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    metal.isPaused = window == nil
    lastTime = nil
  }

  private var scale: CGFloat { min(bounds.width / 402, bounds.height / 874) }
  private var origin: CGPoint {
    CGPoint(x: (bounds.width - 402 * scale) / 2, y: (bounds.height - 874 * scale) / 2)
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    metal.frame = bounds
    typeView.bounds = CGRect(x: 0, y: 0, width: 402, height: 874)
    typeView.layer.anchorPoint = .zero
    typeView.layer.position = origin
    typeView.contentScaleFactor = (window?.screen.scale ?? 3) * scale
    for (element, rect) in [
      (refreshElement, CGRect(x: 40, y: 210, width: 168, height: 44)),
      (periodElement, CGRect(x: 238, y: 210, width: 116, height: 44)),
      (chartElement, CGRect(x: 28, y: 258, width: 346, height: 236)),
    ] {
      let height = max(44, rect.height * scale)
      element.accessibilityFrameInContainerSpace = CGRect(
        x: origin.x + rect.minX * scale, y: origin.y + rect.midY * scale - height / 2,
        width: max(44, rect.width * scale), height: height)
    }
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

  func draw(in view: MTKView) {
    guard bounds.width > 0, let drawable = view.currentDrawable,
      let pass = view.currentRenderPassDescriptor,
      let command = commandQueue.makeCommandBuffer(),
      let encoder = command.makeRenderCommandEncoder(descriptor: pass)
    else { return }
    let now = CACurrentMediaTime()
    let dt = min(0.05, max(0, now - (lastTime ?? now)))
    lastTime = now
    if presentationAge >= 0.3 { Simulation.stepDemo(state, dt) }
    presentationAge += dt
    let picture = Simulation.toFrame(state)
    picture.presented *= Simulation.easeOut(presentationAge / 0.3)
    let matrix = Self.matrix(picture)
    let inverse = matrix.inverse
    let pixelScale = view.drawableSize.width / bounds.width
    var data = Self.uniforms(picture, pixelScale * scale)
    data += [Float(origin.x * pixelScale), Float(origin.y * pixelScale), Float(scale * pixelScale)]
    for row in 0..<3 {
      for column in 0..<3 { data.append(Float(inverse[column][row])) }
    }
    encoder.setRenderPipelineState(pipeline)
    data.withUnsafeBytes { bytes in
      encoder.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
    }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    command.present(drawable)
    command.commit()
    var transform = CATransform3DIdentity
    transform.m11 = matrix[0][0] * scale
    transform.m21 = matrix[1][0] * scale
    transform.m41 = matrix[2][0] * scale
    transform.m12 = matrix[0][1] * scale
    transform.m22 = matrix[1][1] * scale
    transform.m42 = matrix[2][1] * scale
    transform.m14 = matrix[0][2]
    transform.m24 = matrix[1][2]
    transform.m44 = matrix[2][2]
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    typeView.layer.transform = transform
    typeView.picture = picture
    typeView.setNeedsDisplay()
    CATransaction.commit()
    report()
  }

  static func matrix(_ f: GlassPeakChartFrame) -> simd_double3x3 {
    let sx = sin(f.tiltX)
    let cx = cos(f.tiltX)
    let sy = sin(f.tiltY)
    let cy = cos(f.tiltY)
    let a = cy * f.cardScale
    let b = sy * sx * f.cardScale
    let e = cx * f.cardScale
    let g = sy * f.cardScale / 900
    let h = -cy * sx * f.cardScale / 900
    return simd_double3x3(rows: [
      SIMD3(a + 201 * g, b + 201 * h, 201 - (a + 201 * g) * 201 - (b + 201 * h) * 416),
      SIMD3(416 * g, e + 416 * h, 416 - 416 * g * 201 - (e + 416 * h) * 416),
      SIMD3(g, h, 1 - g * 201 - h * 416),
    ])
  }

  static func uniforms(_ f: GlassPeakChartFrame, _ px: Double) -> [Float] {
    var values = [px, f.t, f.presented]
    values += f.ys
    values += [f.lit, f.tip, f.tube, f.beam, f.beamReach, f.orbX, f.orbY, f.orbR, f.orbGain]
    values += f.nodeX + f.nodeY + f.nodeLit + f.nodeScale + f.drop + f.nodeBottom
    values += [f.poolLevel, f.poolHumpX, f.poolHump, f.poolSlope, f.poolGain, f.poolStir]
    values += f.splashX + f.splashY + f.splashR
    values += [f.ringX, f.ringY, f.ringR, f.ringA, f.flash, f.rimGlint, f.tiltX, f.tiltY]
    values += [f.touchX, f.touchY, f.touchA, f.pillPress, f.refreshPress, f.chevron, f.spin]
    return values.map(Float.init)
  }

  private func report() {
    let counters = [
      state.revision, state.light, state.selection, state.medium, state.heavy, state.announcements,
    ]
    if counters[1] != reported[1] { light.impactOccurred() }
    if counters[2] != reported[2] { selection.selectionChanged() }
    if counters[3] != reported[3] { medium.impactOccurred() }
    if counters[4] != reported[4] { heavy.impactOccurred() }
    if counters[0] != reported[0] {
      let period = Simulation.PERIODS[Int(state.f.period)]
      let u = state.settling ? state.nodes[Int(state.node)] : state.orbU
      let node =
        abs(u - state.nodes[Int(state.node)]) < 0.002
        ? ["point 1", "valley", "peak", "point 4"][Int(state.node)] : "between points"
      let value: Double
      if state.mode == "morph" {
        let curve = Simulation.CURVES[Int(state.f.period)]
        value =
          period.value
          * min(
            1,
            Simulation.heightAt(curve, period.nodes[Int(state.node)])
              / Simulation.heightAt(curve, period.nodes[2]))
      } else {
        value = state.mode == "rest" ? period.value * Simulation.ratio(state, u) : state.f.value
      }
      let text = "\(Int(value.rounded())) percent, \(node), \(period.label)"
      chartElement.accessibilityValue = text
      periodElement.accessibilityLabel = "Period: \(period.label)"
      refreshElement.accessibilityValue =
        (state.mode == "refresh" || state.mode == "opening"
          || (state.mode == "fade" && state.fadePeriod == state.from.period))
        ? "Updating" : "Data updated just now"
      if counters[5] != reported[5] {
        UIAccessibility.post(notification: .announcement, argument: text)
      }
    }
    reported = counters
  }

  func activate(_ action: String) {
    Simulation.press(state, action, false, true)
    Simulation.request(state, action, true)
  }

  private func controlRect(_ action: String) -> CGRect {
    let height = max(44, 44 / max(scale, 0.01))
    return CGRect(
      x: action == "period" ? 238 : 40, y: 232 - height / 2,
      width: action == "period" ? 116 : 168, height: height)
  }

  func beginTouch(_ point: CGPoint, time: Double) {
    startPoint = point
    touchStartTime = time
    previousPoint = point
    previousTime = time
    velocity = 0
    Simulation.contact(state, true)
    if controlRect("period").contains(point) {
      action = "period"
    } else if controlRect("refresh").contains(point) {
      action = "refresh"
    } else {
      action = nil
    }
    draggingCard = action == nil && CGRect(x: 28, y: 176, width: 346, height: 480).contains(point)
    if let action {
      Simulation.press(state, action, true, true)
    } else if draggingCard {
      Simulation.fingerDown(state, point.x, point.y, true)
    }
  }

  func moveTouch(_ point: CGPoint, time: Double) {
    velocity = (point.x - previousPoint.x) / max(0.001, time - previousTime)
    previousPoint = point
    previousTime = time
    if draggingCard { Simulation.fingerMove(state, point.x, point.y, velocity) }
  }

  func endTouch(_ point: CGPoint, time: Double, cancelled: Bool) {
    if time - previousTime > 0.1 { velocity = 0 }
    if let action {
      Simulation.press(state, action, false, true)
      if !cancelled && time - touchStartTime <= 0.5 && controlRect(action).contains(point)
        && hypot(point.x - startPoint.x, point.y - startPoint.y) < 12
      {
        activate(action)
      }
    } else if draggingCard {
      Simulation.fingerUp(state, velocity, true)
    }
    Simulation.contact(state, false)
    action = nil
    draggingCard = false
  }

  @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
    let raw = recognizer.location(in: self)
    let point = CGPoint(x: (raw.x - origin.x) / scale, y: (raw.y - origin.y) / scale)
    let time = CACurrentMediaTime()
    switch recognizer.state {
    case .began: beginTouch(point, time: time)
    case .changed: moveTouch(point, time: time)
    case .ended, .cancelled, .failed:
      endTouch(point, time: time, cancelled: recognizer.state != .ended)
    default: break
    }
  }

  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
}
