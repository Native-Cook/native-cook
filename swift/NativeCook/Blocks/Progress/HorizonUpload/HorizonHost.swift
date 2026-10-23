import UIKit

/// The single display-link and touch owner. SwiftUI owns this view's lifetime;
/// native layers avoid per-frame SwiftUI diffing and keep gesture identity stable.
@MainActor final class HorizonHost: UIView, UIGestureRecognizerDelegate {
  let upload = HorizonUpload()
  private let scene = HorizonSurface(.scene), card = HorizonCard(), hint = UILabel()
  private var link: CADisplayLink?
  private var lastTime = 0.0
  private var layout = HorizonLayout()
  private var initial = CGPoint.zero, last = CGPoint.zero, velocity = CGPoint.zero
  private var touchTime = 0.0, lastTouchTime = 0.0
  private var samples = [(point: CGPoint, time: Double)](repeating: (.zero, 0), count: 5)
  private var sampleCount = 0, nextSample = 0
  private var onCard = false, dragging = false, target = 0
  private lazy var panelAX = HorizonAccessibility(accessibilityContainer: self)
  private lazy var closeAX = HorizonAccessibility(accessibilityContainer: self)
  private lazy var fileAX = UIAccessibilityElement(accessibilityContainer: self)
  private lazy var hintAX = UIAccessibilityElement(accessibilityContainer: self)
  private var lastPercent = -1, lastPhase: HorizonPhase?, lastRetry: Bool?
  var fontScale = 1.0 { didSet { if oldValue != fontScale { setNeedsLayout() } } }
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = UIColor(red: 5 / 255, green: 16 / 255, blue: 34 / 255, alpha: 1)
    isMultipleTouchEnabled = false
    // Raw touches are cancelled when the gallery's 24 pt back-swipe recognizer
    // begins. Track alongside it, as Ember does, without taking over its action.
    let finger = UILongPressGestureRecognizer(target: self, action: #selector(trackFinger(_:)))
    finger.minimumPressDuration = 0
    finger.allowableMovement = .greatestFiniteMagnitude
    finger.cancelsTouchesInView = false
    finger.delegate = self
    addGestureRecognizer(finger)
    addSubview(scene); addSubview(card); addSubview(hint)
    hint.text = HorizonL10n.t("hint"); hint.numberOfLines = 0; hint.textAlignment = .center
    hint.textColor = UIColor(red: 214 / 255, green: 224 / 255, blue: 1, alpha: 0.5)
    hint.isUserInteractionEnabled = false
    panelAX.accessibilityTraits = .button; closeAX.accessibilityTraits = .button
    panelAX.activate = { [weak self] in self?.upload.toggle() }
    closeAX.activate = { [weak self] in self?.upload.close() }
    fileAX.accessibilityTraits = .staticText; fileAX.accessibilityLabel = "Project Brief.txt, 97.23 KB"
    hintAX.accessibilityTraits = .staticText; hintAX.accessibilityLabel = HorizonL10n.t("hint")
    accessibilityElements = [fileAX, closeAX, panelAX, hintAX]
    NotificationCenter.default.addObserver(self, selector: #selector(sleep), name: UIApplication.willResignActiveNotification, object: nil)
    NotificationCenter.default.addObserver(self, selector: #selector(wake), name: UIApplication.didBecomeActiveNotification, object: nil)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  deinit { NotificationCenter.default.removeObserver(self) }
  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil { stopClock() } else { setNeedsLayout(); startClock() }
  }
  private func startClock() {
    guard link == nil else { return }
    lastTime = 0
    let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
    link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
    link.add(to: .main, forMode: .common); self.link = link
  }
  func stopClock() { link?.invalidate(); link = nil; lastTime = 0 }
  @objc private func sleep() {
    upload.sleep(); link?.isPaused = true; lastTime = 0
    onCard = false; dragging = false; target = 0
  }
  @objc private func wake() { upload.wake(); lastTime = 0; link?.isPaused = false }
  override func safeAreaInsetsDidChange() { super.safeAreaInsetsDidChange(); setNeedsLayout() }
  override func layoutSubviews() {
    super.layoutSubviews(); guard bounds.width > 0, bounds.height > 0 else { return }
    let next = HorizonLayout(size: bounds.size, safe: safeAreaInsets, fontScale: fontScale)
    guard layout != next else { return }
    layout = next
    scene.frame = bounds
    card.configure(layout, scale: traitCollection.displayScale)
    hint.font = .systemFont(ofSize: 13 * min(fontScale, 1.3))
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    paragraph.minimumLineHeight = 18 * min(fontScale, 1.3)
    paragraph.maximumLineHeight = paragraph.minimumLineHeight
    hint.attributedText = NSAttributedString(string: HorizonL10n.t("hint"), attributes: [
      .font: hint.font!, .paragraphStyle: paragraph, .foregroundColor: hint.textColor!])
    let size = hint.sizeThatFits(CGSize(width: bounds.width - 48, height: 200))
    hint.frame = CGRect(x: 24, y: bounds.height - safeAreaInsets.bottom - 28 - max(18, size.height), width: bounds.width - 48, height: max(18, size.height))
    hintAX.accessibilityFrameInContainerSpace = hint.frame
    scene.layoutIfNeeded(); card.layoutIfNeeded()
    draw(0)
  }
  @objc private func tick(_ link: CADisplayLink) {
    guard layout.size.width > 0 else { return }
    let now = link.timestamp
    let ms = lastTime == 0 ? 0 : max(0, (now - lastTime) * 1000)
    lastTime = now
    upload.advance(ms); draw(ms)
  }
  private func draw(_ ms: Double) {
    CATransaction.begin(); CATransaction.setDisableActions(true)
    card.update(upload, ms: ms)
    HorizonRenderer.shared.draw(scene, upload: upload, layout: layout)
    HorizonRenderer.shared.draw(card.bar, upload: upload, layout: layout)
    card.file.render(upload, layout: layout)
    updateAccessibility()
    CATransaction.commit()
  }
  private func updateAccessibility() {
    panelAX.accessibilityFrameInContainerSpace = card.convert(layout.panel, to: self)
    closeAX.accessibilityFrameInContainerSpace = card.convert(CGRect(x: layout.width - 48, y: 4, width: 44, height: 44), to: self)
    fileAX.accessibilityFrameInContainerSpace = card.convert(CGRect(x: layout.copyLeft, y: 68, width: layout.copyWidth, height: 44), to: self)
    if lastPercent != upload.percent { lastPercent = upload.percent; panelAX.accessibilityValue = "\(lastPercent) %" }
    if lastPhase != upload.phase { lastPhase = upload.phase; panelAX.accessibilityLabel = HorizonL10n.t(upload.phase.action) }
    if lastRetry != upload.retry { lastRetry = upload.retry; closeAX.accessibilityLabel = HorizonL10n.t(upload.retry ? "again" : "cancel") }
  }
  func gestureRecognizer(
    _ recognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
  ) -> Bool { true }

  @objc private func trackFinger(_ finger: UILongPressGestureRecognizer) {
    let point = finger.location(in: self)
    let now = CACurrentMediaTime()
    switch finger.state {
    case .began: beginTouch(point, timestamp: now)
    case .changed: moveTouch(point, timestamp: now)
    case .ended: endTouch(point, timestamp: now)
    case .cancelled, .failed: cancelTouch()
    default: break
    }
  }
  func beginTouch(_ point: CGPoint, timestamp: Double) {
    upload.touch()
    initial = point; last = initial; velocity = .zero
    lastTouchTime = timestamp; touchTime = timestamp
    sampleCount = 0; nextSample = 0
    sample(point, timestamp: timestamp)
    let p = card.convert(initial, from: self)
    onCard = card.bounds.contains(p); dragging = false; target = 0
    guard onCard else { return }
    upload.beginDrag()
    if CGRect(x: layout.width - 48, y: 4, width: 44, height: 44).contains(p) {
      target = 2; if upload.closeEnabled { upload.closePress.timing(1, 90, .out) }
    } else if layout.panel.contains(p) {
      target = 1; if upload.panelEnabled { upload.press.timing(1, 90, .out) }
    }
  }
  func moveTouch(_ p: CGPoint, timestamp: Double) {
    guard onCard else { return }
    let dx = p.x - initial.x, dy = p.y - initial.y
    sample(p, timestamp: timestamp)
    if !dragging && hypot(dx, dy) >= 8 {
      dragging = true; target = 0; upload.releasePress(); upload.startDrag()
    }
    if dragging { upload.moveDrag(dx, dy) }
  }
  func endTouch(_ p: CGPoint, timestamp: Double) {
    guard onCard else { return }
    // Include a final moved location, but do not replace a stationary finger's
    // last movement time: the 100 ms stale-velocity rule still applies below.
    if p != last { moveTouch(p, timestamp: timestamp) }
    let dx = p.x - initial.x, dy = p.y - initial.y
    if dragging {
      // A hold before lifting should release without an obsolete flick velocity.
      let stale = timestamp - lastTouchTime > 0.1
      upload.releaseDrag(dx, dy, stale ? 0 : velocity.x, stale ? 0 : velocity.y)
    } else {
      if hypot(dx, dy) <= 8 && timestamp - touchTime <= 60 {
        if target == 1 { upload.toggle() } else if target == 2 { upload.close() }
      }
      upload.finalizeDrag()
    }
    upload.releasePress(); onCard = false; dragging = false; target = 0
  }
  private func cancelTouch() {
    guard onCard else { return }
    upload.finalizeDrag(); upload.releasePress(); onCard = false; dragging = false; target = 0
  }

  private func sample(_ point: CGPoint, timestamp: Double) {
    samples[nextSample] = (point, timestamp)
    nextSample = (nextSample + 1) % samples.count
    sampleCount = min(sampleCount + 1, samples.count)
    // A long press has no velocity API. Average up to five recent samples over
    // at most 100 ms, reusing storage and ignoring zero-duration sample pairs.
    var oldest = (point: point, time: timestamp)
    for offset in 1..<sampleCount {
      let previous = samples[(nextSample - 1 - offset + samples.count) % samples.count]
      if timestamp - previous.time > 0.1 { break }
      if previous.time < timestamp { oldest = previous }
    }
    let dt = timestamp - oldest.time
    velocity = dt >= 0.001
      ? CGPoint(x: (point.x - oldest.point.x) / dt, y: (point.y - oldest.point.y) / dt) : .zero
    last = point; lastTouchTime = timestamp
  }
}
