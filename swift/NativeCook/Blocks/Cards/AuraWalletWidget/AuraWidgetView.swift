// Glass below, native type and cached portrait layers above. The display clock
// writes transforms directly; SwiftUI observes only discrete content changes.
import SwiftUI
import UIKit

@MainActor final class AuraWidgetView: UIView, UIGestureRecognizerDelegate {
  let machine: AuraMachine
  let words: AuraWords
  let shell = AuraSurfaceView()
  private let content = UIView()
  private let owner = AuraPortraitView(image: AuraPeople.owner)
  private let route = AuraPortraitView(image: AuraPeople.friends[0])
  private let ownerName = AuraText.label("Ana", size: 12, tracking: 0, opacity: 0.85)
  private let info = AuraIconView("info", color: .white.withAlphaComponent(0.72))
  private let lastLabel = AuraText.label(AuraL10n.t("last"), size: 11, opacity: 0.62)
  private let allLabel = AuraText.label(AuraL10n.t("all"), size: 11, opacity: 0.9)
  private var currentReceipt = AuraReceiptView()
  private var previousReceipt = AuraReceiptView()
  private var displayedReceipt: AuraReceipt?
  private let receiptAccessibility = UIView()
  private let balanceAccessibility = UIView()
  private let balance: UIHostingController<AuraNumber>
  private let chip: UIHostingController<AuraChip>
  private let cushionTouch = AuraTouchView()
  private let infoTouch = AuraTouchView()
  private let allTouch = AuraTouchView()
  private let chipTouch = AuraTouchView()
  private var faces: [AuraPortraitView] = []
  private var friends: [AuraTouchView] = []
  private var pillows: [AuraPillowView] = []
  private var actions: [AuraTouchView] = []
  private var scale = 1.0
  private var laidOutWidth = 0.0

  init(machine: AuraMachine, words: AuraWords) {
    self.machine = machine
    self.words = words
    balance = UIHostingController(rootView: AuraNumber(words: words, balance: true))
    chip = UIHostingController(rootView: AuraChip(words: words))
    super.init(frame: .zero)
    balance.safeAreaRegions = []
    chip.safeAreaRegions = []
    machine.view = self
    shell.onShown = { [weak machine] time in machine?.shellPresented(at: time) }
    backgroundColor = .clear
    isOpaque = false
    content.isOpaque = false
    addSubview(shell)
    addSubview(content)
    content.alpha = 0
    isUserInteractionEnabled = false
    content.addSubview(cushionTouch)
    content.addSubview(route)
    content.addSubview(owner)
    content.addSubview(ownerName)
    content.addSubview(info)
    info.backgroundColor = UIColor(red: 38 / 255, green: 6 / 255, blue: 112 / 255, alpha: 0.42)
    for controller in [balance as UIViewController, chip as UIViewController] {
      controller.view.backgroundColor = .clear
      controller.view.isUserInteractionEnabled = false
      content.addSubview(controller.view)
    }
    content.addSubview(balanceAccessibility)
    balanceAccessibility.isUserInteractionEnabled = false
    balanceAccessibility.isAccessibilityElement = true
    balance.view.accessibilityElementsHidden = true
    chip.view.accessibilityElementsHidden = true
    content.addSubview(lastLabel)
    content.addSubview(allLabel)
    allLabel.textAlignment = .right
    AuraText.set(allLabel, AuraL10n.t("all"), underline: true)
    content.addSubview(previousReceipt)
    content.addSubview(currentReceipt)
    content.addSubview(receiptAccessibility)
    receiptAccessibility.isUserInteractionEnabled = false
    receiptAccessibility.isAccessibilityElement = true
    for i in 0..<5 {
      let face = AuraPortraitView(
        image: AuraPeople.friends[i], zoom: AuraDesign.faceZoom, lift: AuraDesign.faceLift)
      faces.append(face)
      content.addSubview(face)
      let touch = AuraTouchView()
      touch.face = true
      touch.index = i
      touch.machine = machine
      touch.coordinateView = self
      touch.accessibilityLabel = AuraPeople.names[i]
      touch.accessibilityHint = AuraL10n.t("friendHint")
      friends.append(touch)
      content.addSubview(touch)
    }
    for i in 0..<4 {
      let pillow = AuraPillowView(index: i, words: words)
      pillows.append(pillow)
      content.addSubview(pillow)
      let touch = AuraTouchView()
      touch.index = i
      touch.machine = machine
      touch.coordinateView = self
      if i == 3 { touch.activate = { [weak machine] in machine?.send() } }
      actions.append(touch)
      content.addSubview(touch)
    }
    configureQuietTouch(cushionTouch, key: nil)
    cushionTouch.activate = { [weak machine] in machine?.dismiss() }
    configureQuietTouch(infoTouch, key: "account")
    configureQuietTouch(allTouch, key: "allAccessible")
    configureQuietTouch(chipTouch, key: nil)
    chipTouch.isAccessibilityElement = true
    chipTouch.accessibilityTraits = .staticText
    content.addSubview(infoTouch)
    content.addSubview(allTouch)
    content.addSubview(chipTouch)
    let anyTouch = UILongPressGestureRecognizer(target: self, action: #selector(firstTouch(_:)))
    anyTouch.minimumPressDuration = 0
    anyTouch.cancelsTouchesInView = false
    anyTouch.delegate = self
    addGestureRecognizer(anyTouch)
    syncWords()
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  private func configureQuietTouch(_ touch: AuraTouchView, key: String?) {
    touch.index = -1
    touch.machine = machine
    touch.coordinateView = self
    touch.isAccessibilityElement = key != nil
    if let key { touch.accessibilityLabel = AuraL10n.t(key) }
  }

  @objc private func firstTouch(_ recognizer: UILongPressGestureRecognizer) {
    if recognizer.state == .began { machine.touch() }
  }

  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    true
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil { machine.stop() } else { machine.start() }
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    guard bounds.width > 0, bounds.width != laidOutWidth else { return }
    laidOutWidth = bounds.width
    scale = bounds.width / AuraDesign.design
    let u = scale
    words.scale = u
    shell.cardWidth = bounds.width
    shell.frame = bounds.insetBy(dx: -64, dy: -64)
    content.frame = bounds
    cushionTouch.frame = CGRect(x: 12 * u, y: 12 * u, width: 338 * u, height: 156 * u)
    owner.frame = CGRect(x: 29 * u, y: 26 * u, width: 22 * u, height: 22 * u)
    route.bounds = owner.bounds
    route.center = owner.center
    ownerName.font = .systemFont(ofSize: 12 * u)
    ownerName.bounds = CGRect(x: 0, y: 0, width: 259 * u, height: 16 * u)
    info.frame = CGRect(x: 315 * u, y: 26 * u, width: 22 * u, height: 22 * u)
    info.layer.cornerRadius = 11 * u
    info.stroke = 1.5 * u
    infoTouch.frame = CGRect(x: 326 * u - 22, y: 37 * u - 22, width: 44, height: 44)
    chip.view.frame = CGRect(x: 12 * u, y: 47 * u, width: 338 * u, height: 22 * u)
    chipTouch.frame = CGRect(x: 126 * u, y: 47 * u, width: 110 * u, height: 22 * u)
    balance.view.frame = CGRect(x: 12 * u, y: 77 * u, width: 338 * u, height: 42 * u)
    balanceAccessibility.frame = balance.view.frame
    lastLabel.font = .systemFont(ofSize: 11 * u)
    allLabel.font = .systemFont(ofSize: 11 * u)
    lastLabel.frame = CGRect(x: 26 * u, y: 212 * u, width: 155 * u, height: 14 * u)
    allLabel.frame = CGRect(x: 181 * u, y: 212 * u, width: 155 * u, height: 14 * u)
    allLabel.isAccessibilityElement = false
    allTouch.frame = CGRect(x: 181 * u, y: 219 * u - 22, width: 155 * u, height: 44)
    previousReceipt.frame = bounds
    currentReceipt.frame = bounds
    receiptAccessibility.frame = CGRect(x: 28 * u, y: 234 * u, width: 306 * u, height: 36 * u)
    for i in 0..<5 {
      faces[i].transform = .identity
      faces[i].bounds = CGRect(x: 0, y: 0, width: 36 * u, height: 36 * u)
      faces[i].center = CGPoint(x: auraSlotX(Double(i)) * u, y: 164 * u)
      let side = max(36 * u, 44)
      friends[i].frame = CGRect(
        x: auraSlotX(Double(i)) * u - side / 2, y: 164 * u - side / 2, width: side, height: side)
      friends[i].scale = u
    }
    for touch in actions { touch.scale = u }
    syncWords()
    renderFrame()
  }

  /// The only place that formats strings or remeasures a changed amount.
  func syncWords() {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    defer { CATransaction.commit() }
    words.reduced = machine.reduced
    words.balance = "$ \(AuraL10n.cents(machine.previewCents))"
    words.amount = machine.drive.amount > 0 ? "$\(Int(machine.drive.amount))" : ""
    words.after = machine.preview
    chipTouch.accessibilityLabel = AuraL10n.t(machine.preview ? "after" : "balance")
    let chosen = machine.drive.chosen
    let name = chosen >= 0 ? AuraPeople.names[chosen] : ""
    AuraText.set(ownerName, machine.activeRoute ? "Ana › \(name)" : "Ana", tracking: 0)
    if chosen >= 0 { route.setImage(AuraPeople.friends[chosen]) }
    let digits = AuraL10n.cents(machine.previewCents).split(separator: ".").map(String.init)
    balanceAccessibility.accessibilityLabel = AuraL10n.format(
      machine.preview ? "afterAccessible" : "balanceAccessible", digits[0], digits[1])
    if displayedReceipt != machine.receipt {
      if machine.previous != nil {
        // The outgoing row keeps its already-drawn image, pearl and text.
        // Reuse the spare row for incoming ink, rather than rebuilding old ink.
        swap(&currentReceipt, &previousReceipt)
        content.insertSubview(currentReceipt, aboveSubview: previousReceipt)
      }
      displayedReceipt = machine.receipt
    }
    currentReceipt.configure(machine.receipt, scale: scale)
    if let previous = machine.previous { previousReceipt.configure(previous, scale: scale) }
    previousReceipt.isHidden = machine.previous == nil
    currentReceipt.isHidden = false
    renderReceipt()
    receiptAccessibility.accessibilityLabel =
      machine.receipt.friend < 0
      ? AuraL10n.t("initialReceiptAccessible")
      : AuraL10n.format(
        "receiptAccessible", AuraPeople.names[machine.receipt.friend],
        String(format: "%.2f", Double(machine.receipt.cents) / 100))
    let expanded = machine.drive.phase != .rest && !machine.landed
    for i in 0..<4 {
      let priced = i == 3 && expanded && machine.drive.amount > 0
      pillows[i].configure(scale: scale, priced: priced)
      let label = AuraL10n.t(["settings", "swap", "receive", "send"][i])
      actions[i].accessibilityLabel = priced ? "\(label) $\(Int(machine.drive.amount))" : label
    }
    for i in 0..<5 {
      friends[i].accessibilityTraits =
        machine.drive.chosen == i && machine.drive.phase != .rest ? [.button, .selected] : .button
    }
    updateHint(name: name)
  }

  private func updateHint(name: String) {
    var kind = "rest"
    var text = AuraL10n.t(kind)
    if let sent = machine.sent {
      kind = "sent"
      text = AuraL10n.format(kind, sent.amount, sent.name)
    } else if machine.drive.phase == .sending {
      kind = "sending"
      text = AuraL10n.format(kind, Int(machine.drive.amount), name)
    } else if machine.drive.phase == .armed {
      kind = "armed"
      text = AuraL10n.t(kind)
    } else if machine.drive.phase == .pulling {
      kind = machine.drive.amount > 0 ? "pulling" : "idle-pull"
      text =
        machine.drive.amount > 0
        ? AuraL10n.format(kind, Int(machine.drive.amount), name) : AuraL10n.t(kind)
    }
    words.hint = text
    words.hintKind = kind
  }

  /// The score is sampled once per shown frame, with implicit layer animations disabled.
  func renderFrame() {
    guard laidOutWidth > 0 else { return }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    let d = machine.drive
    let u = scale
    isUserInteractionEnabled = !machine.arriving
    accessibilityElementsHidden = machine.arriving
    content.alpha = machine.shown.value
    let identity = machine.reduced ? (machine.activeRoute ? 1.0 : 0.0) : machine.identity.value
    route.center = CGPoint(x: (40 + 14 * identity) * u, y: 37 * u)
    route.alpha = machine.identity.value
    ownerName.center = CGPoint(x: (56 + 259 / 2.0 + 14 * identity) * u, y: 37 * u)
    for i in 0..<5 { renderFriend(i, drive: d) }
    let trade = machine.reduced ? (d.wide < 0.5 ? 0.0 : 1.0) : d.wide
    let frames = auraActionFrames(trade)
    for i in 0..<4 {
      pillows[i].render(d, reduced: machine.reduced)
      actions[i].frame = CGRect(
        x: frames.xs[i] * u, y: 298 * u, width: frames.widths[i] * u, height: 52 * u)
    }
    renderReceipt()
    // Present the shell in the same transaction as this frame's native layers.
    shell.drive = d
    shell.reduceTransparency = machine.opaque
    shell.render()
    CATransaction.commit()
  }

  private func renderReceipt() {
    let writing = machine.previous != nil
    let radius = max(0, machine.receiptClock.value * AuraFlow.send.rippleSpeed / 1000) * scale
    let circle = writing && !machine.reduced
      ? CGPath(
        ellipseIn: CGRect(
          x: machine.receiptX * scale - radius, y: auraLandY * scale - radius,
          width: radius * 2, height: radius * 2), transform: nil)
      : nil
    // One edge clips the entire mark, title, note and amount on both rows.
    currentReceipt.reveal(circle: circle)
    previousReceipt.reveal(circle: circle, inverse: true)
    let fade = min(1, machine.receiptClock.value / AuraFlow.views.receiptFade)
    previousReceipt.alpha = machine.reduced ? 1 - fade : 1
    currentReceipt.alpha = machine.reduced && writing ? fade : 1
  }

  private func renderFriend(_ index: Int, drive d: AuraDrive) {
    let pose = auraLipPose(Double(index), Double(d.chosen), d.formed, Double(d.leaving), d.unformed)
    let at = index * 4
    var x = pose.dx + machine.offsets[at].value
    var y = pose.dy + machine.offsets[at + 1].value + auraNudgeDip(Double(index), d.nudge)
    var scaleX = pose.scale + machine.offsets[at + 2].value
    var scaleY = scaleX
    var opacity = pose.opacity + machine.offsets[at + 3].value
    if index == d.chosen {
      x += d.pullX
      y += d.pullY
      if d.phase == .sending {
        let drop = auraSendPose(d.clock, d.fromY)
        let bud = d.clock > auraSendMoments(d.fromY).bud
        x = bud ? 0 : d.fromX - auraSlotX(Double(index))
        y = bud ? 0 : drop.y - 164
        scaleX = bud ? drop.bud : drop.wide
        scaleY = bud ? drop.bud : drop.tall
        opacity = bud ? drop.budOpacity : drop.presence
        if machine.reduced {
          opacity = bud ? drop.budOpacity : max(0, 1 - d.clock / AuraFlow.views.departureFade)
        }
      }
    }
    let compression =
      d.phase == .sending && index == d.chosen ? 1 : 1 - 0.04 * machine.facePresses[index].value
    if !machine.reduced && d.entered >= 0 {
      let growth = auraEnterScale(Double(index), d.entered)
      scaleX *= growth
      scaleY *= growth
      opacity *= auraEnterOpacity(Double(index), d.entered)
    }
    faces[index].alpha = auraClamp(opacity)
    faces[index].transform =
      machine.reduced
      ? .identity
      : CGAffineTransform(
        a: scaleX * compression, b: 0, c: 0, d: scaleY * compression, tx: x * scale, ty: y * scale)
  }
}
