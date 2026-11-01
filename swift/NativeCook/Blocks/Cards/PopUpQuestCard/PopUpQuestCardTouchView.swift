import UIKit

final class PopUpQuestCardTouchView: UIView, UIGestureRecognizerDelegate {
  var clock: PopUpQuestCardClock?
  var layout: PopUpQuestCardMotion.Layout?
  var open = false
  var opening = 0.0
  var quest: PopUpQuestCardResources.Quest?
  private var accessibilityKey = ""
  private var pressTarget = -1
  private var pressRect = CGRect.zero
  private var pressBeganAt = 0.0
  private var pressOutTimer: Timer?
  private func activatePress(_ target: Int) {
    pressOutTimer?.invalidate(); pressBeganAt = ProcessInfo.processInfo.systemUptime
    clock?.pressed = target
  }
  private func deactivatePress() {
    pressOutTimer?.invalidate()
    // React Native Pressability keeps the pressed feedback visible for at least 130 ms.
    let delay = max(0, 0.13 - (ProcessInfo.processInfo.systemUptime - pressBeganAt))
    if delay > 0 {
      pressOutTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in self?.clock?.pressed = -1 }
    } else { clock?.pressed = -1 }
  }
  private func insidePress(_ p: CGPoint) -> Bool {
    p.x > pressRect.minX && p.x < pressRect.maxX && p.y > pressRect.minY && p.y < pressRect.maxY
  }
  override init(frame: CGRect) {
    super.init(frame: frame); backgroundColor = .clear
    let pan = PopUpQuestCardPanGesture(target: self, action: #selector(pan(_:))); pan.delegate = self; addGestureRecognizer(pan)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    guard let l = layout else { return false }
    let location = gestureRecognizer.location(in: self)
    let translation = (gestureRecognizer as? UIPanGestureRecognizer)?.translation(in: self) ?? .zero
    let p = CGPoint(x: location.x - translation.x, y: location.y - translation.y)
    let sheetY = l.sheetTop + (1 - PopUpQuestCardMotion.smooth(0.25, 1, opening)) * (l.h - l.sheetTop + 40)
    return open && pressTarget != 4 && p.y < min(l.heroH, sheetY) && !(CGRect(x: 16, y: l.top + 6, width: 44, height: 44).contains(p))
  }
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesBegan(touches, with: event)
    guard let l = layout, let p = touches.first?.location(in: self) else { return }
    pressTarget = -1
    func press(_ target: Int, _ rect: CGRect, scale: Double = 1) {
      if rect.contains(p) {
        pressTarget = target
        pressRect = CGRect(x: rect.minX - 20 * scale, y: rect.minY - 20 * scale, width: rect.width + 40 * scale, height: rect.height + 50 * scale)
        activatePress(target)
      }
    }
    if open {
      let shift = (1 - PopUpQuestCardMotion.smooth(0.25, 1, opening)) * (l.h - l.sheetTop + 40)
      press(4, CGRect(x: 16, y: l.top + 6, width: 44, height: 44))
      press(5, CGRect(x: 20, y: l.h - l.bottom - 68 + shift, width: l.w - 108, height: 56))
      press(6, CGRect(x: l.w - 76, y: l.h - l.bottom - 68 + shift, width: 56, height: 56))
    } else {
      let scale = 1 - 0.04 * PopUpQuestCardMotion.smooth(0, 0.55, opening)
      for i in 0..<4 {
        let c = l.card(i)
        press(i, CGRect(x: l.w / 2 + (c.minX - l.w / 2) * scale, y: l.h / 2 + (c.minY - l.h / 2) * scale, width: c.width * scale, height: c.height * scale), scale: scale)
      }
    }
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesMoved(touches, with: event)
    guard pressTarget >= 0, let p = touches.first?.location(in: self) else { return }
    if insidePress(p) { if clock?.pressed != pressTarget { activatePress(pressTarget) } }
    else if clock?.pressed == pressTarget { deactivatePress() }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesEnded(touches, with: event)
    let target = pressTarget
    let valid = touches.first.map { insidePress($0.location(in: self)) } ?? false
    pressTarget = -1
    if target >= 0 { deactivatePress() }
    if valid && target >= 0 && target < 4 { clock?.open(target) }
    else if valid && target == 4 { clock?.close() }
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesCancelled(touches, with: event)
    if pressTarget >= 0 { deactivatePress() }; pressTarget = -1
  }
  @objc private func pan(_ g: UIPanGestureRecognizer) {
    guard let l = layout else { return }
    clock?.pan(g.state, g.translation(in: self), g.velocity(in: self), l.heroH)
  }
  func updateAccessibility() {
    guard let l = layout, let q = quest else { return }
    let key = "\(open)-\(q.title)-\(l.size)"; if key == accessibilityKey { return }; accessibilityKey = key
    var elements: [UIAccessibilityElement] = []
    func add(_ label: String, _ frame: CGRect, _ traits: UIAccessibilityTraits = [], _ action: (() -> Void)? = nil) {
      let a = PopUpQuestCardAccessibilityElement(accessibilityContainer: self)
      a.accessibilityLabel = label; a.accessibilityFrameInContainerSpace = frame; a.accessibilityTraits = traits; a.activate = action; elements.append(a)
    }
    if !open {
      add("Hi, Mia & Theo", CGRect(x: 20, y: l.top + 8, width: 200, height: 20))
      add("Where to next?", CGRect(x: 20, y: l.top + 30, width: 240, height: 36))
      let headerH = PopUpQuestCardResources.shared.line(14) + 2 + PopUpQuestCardResources.shared.line(26, true)
      add("Profile", CGRect(x: l.w - 64, y: ((l.top + 8 + (headerH - 44) / 2) * UIScreen.main.scale).rounded() / UIScreen.main.scale, width: 44, height: 44))
      add("Search next adventure", CGRect(x: 16, y: l.top + 74, width: l.w - 32, height: 50), .searchField)
      add("Recommended for you!", CGRect(x: 20, y: l.top + 140, width: l.w - 40, height: 28), .header)
      for (i, q) in PopUpQuestCardResources.shared.quests.enumerated() {
        add(q.title + ", " + q.price, l.card(i), .button) { [weak self] in self?.clock?.open(i) }
      }
      for i in 0..<5 { add("", CGRect(x: 24 + Double(i) * (l.w - 48) / 5, y: l.h - l.bottom - 68, width: (l.w - 48) / 5, height: 60), i == 2 ? [.button, .selected] : .button) }
    } else {
      add(q.title + " pop-up scene. Drag sideways to look around, drag down to close", CGRect(x: 0, y: 0, width: l.w, height: l.heroH))
      add("Back to quests", CGRect(x: 16, y: l.top + 6, width: 44, height: 44), .button) { [weak self] in self?.clock?.close() }
      add(q.title + "!", CGRect(x: 20, y: l.sheetTop + 29, width: l.w - 40, height: 40), .header)
      let positions = PopUpQuestCardResources.shared.sheetPositions()
      for (i, c) in q.cats.enumerated() { add(c[1], CGRect(x: 20 + Double(i) * (l.w - 112) / 3, y: l.sheetTop + positions.cats, width: 72, height: 74)) }
      add("Activities:", CGRect(x: 20, y: l.sheetTop + positions.sub, width: l.w - 40, height: 26), .header)
      for (i, a) in q.acts.enumerated() { add(a["title"]! + ", " + a["sub"]!, CGRect(x: 20, y: l.sheetTop + positions.acts + 8 + Double(i) * 72, width: l.w - 40, height: 64)) }
      add("Book " + q.title, CGRect(x: 20, y: l.h - l.bottom - 68, width: l.w - 108, height: 56), .button)
      add("Pick dates", CGRect(x: l.w - 76, y: l.h - l.bottom - 68, width: 56, height: 56), .button)
    }
    accessibilityElements = elements
  }
}
