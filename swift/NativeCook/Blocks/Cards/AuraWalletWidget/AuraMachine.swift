// The UI clock conducts the score. Only choices, amount steps and milestones update words.
import QuartzCore
import UIKit

@MainActor final class AuraMachine {
  var drive = AuraDrive()
  var reduced = false
  var opaque = false
  var balanceCents = 5_200_250
  var receipt = AuraReceipt(friend: -1, cents: 6000)
  var previous: AuraReceipt?
  var sent: (name: String, amount: Int)?
  var receiptX = 0.0
  var landed = false
  weak var view: AuraWidgetView?

  let formed = AuraValue(0)
  let unformed = AuraValue(0)
  let pullX = AuraValue(0)
  let pullY = AuraValue(0)
  let wide = AuraValue(0)
  let weight = AuraValue(0.6)
  let shown = AuraValue(0)
  let identity = AuraValue(0)
  let receiptClock = AuraValue(AuraFlow.send.rippleLife)
  let presses = (0..<4).map { _ in AuraValue(0) }
  let facePresses = (0..<5).map { _ in AuraValue(0) }
  let offsets = (0..<20).map { _ in AuraValue(0) }
  private var values: [AuraValue] = []
  private var display: CADisplayLink?
  private var proxy: AuraDisplayProxy?
  private var cueTimer: Timer?
  private var sentTimer: Timer?
  private var touched = false
  private var warm = 0.0
  private var arrived = false
  private var enteringAt: Double?
  private var nudgeAt: Double?
  private var sendingAt: Double?
  private var sinkingAt: Double?
  private var wasArmed = false
  private var pullOwner = -1
  private var running = false
  private var lastShellPresentation: Double?
  private let tickHaptic = UISelectionFeedbackGenerator()
  private let nudgeHaptic = UIImpactFeedbackGenerator(style: .light)
  private let landingHaptic = UINotificationFeedbackGenerator()

  init() {
    values =
      [formed, unformed, pullX, pullY, wide, weight, shown, identity, receiptClock]
      + presses + facePresses + offsets
  }

  var preview: Bool {
    drive.phase != .rest && drive.amount > 0 && !(drive.phase == .sending && landed)
  }
  var previewCents: Int { balanceCents - (preview ? Int(drive.amount) * 100 : 0) }
  var activeRoute: Bool {
    drive.phase != .rest && drive.chosen >= 0 && !(drive.phase == .sending && landed)
  }
  var arriving: Bool { !arrived || enteringAt != nil || shown.active }

  var locked: Bool {
    arriving || sinkingAt != nil
      || (drive.phase == .sending && drive.clock < auraSendMoments(drive.fromY).unlock)
  }

  func start() {
    guard !running else { return }
    running = true
    let proxy = AuraDisplayProxy()
    proxy.machine = self
    self.proxy = proxy
    let display = CADisplayLink(target: proxy, selector: #selector(AuraDisplayProxy.step(_:)))
    display.add(to: .main, forMode: .common)
    self.display = display
    scheduleCue(after: AuraFlow.cue.first)
  }

  func stop() {
    running = false
    lastShellPresentation = nil
    display?.invalidate()
    display = nil
    proxy = nil
    cueTimer?.invalidate()
    cueTimer = nil
    sentTimer?.invalidate()
    sentTimer = nil
  }

  func wake() {
    display?.isPaused = false
  }

  /// Called only for a presented closed-lip drawable (successful shell command
  /// completion on Simulator, where Metal omits presentation handlers).
  /// Submission, display-link ticks and pipeline readiness are not shown frames.
  func shellPresented(at time: Double) {
    guard running, !arrived else { return }
    if let last = lastShellPresentation {
      guard time > last else { return }
      let interval = time - last
      if interval < AuraFlow.enter.smooth { warm += interval }
    }
    lastShellPresentation = time
  }

  func touch() {
    guard !arriving, !touched else { return }
    touched = true
    cueTimer?.invalidate()
    cueTimer = nil
    nudgeAt = nil
    drive.nudge = -1
    wake()
  }

  private func scheduleCue(after delay: Double) {
    guard !touched, !reduced, running else { return }
    cueTimer?.invalidate()
    guard let proxy else { return }
    cueTimer = Timer.scheduledTimer(
      timeInterval: delay / 1000, target: proxy,
      selector: #selector(AuraDisplayProxy.cue), userInfo: nil, repeats: false)
  }

  func cueFired() {
    if !touched, drive.entered < 0, drive.phase == .rest {
      nudgeAt = CACurrentMediaTime() * 1000
      drive.nudge = 0
      wake()
    }
    scheduleCue(after: AuraFlow.cue.every)
  }

  func sentExpired() {
    sent = nil
    view?.syncWords()
  }

  func preferences(reduced: Bool, opaque: Bool) {
    let changed = self.reduced != reduced
    self.reduced = reduced
    self.opaque = opaque
    if changed {
      if reduced {
        cueTimer?.invalidate()
        cueTimer = nil
      } else {
        scheduleCue(after: AuraFlow.cue.first)
      }
    }
    view?.syncWords()
    wake()
  }

  private func available() -> Bool {
    if arriving || sinkingAt != nil { return false }
    if drive.phase != .sending { return drive.phase != .pulling }
    if drive.clock < auraSendMoments(drive.fromY).unlock { return false }
    reset()
    return true
  }

  private func reset() {
    sendingAt = nil
    sinkingAt = nil
    drive.phase = .rest
    drive.chosen = -1
    drive.leaving = -1
    drive.clock = 0
    formed.set(0)
    unformed.set(0)
    pullX.set(0)
    pullY.set(0)
    landed = false
    pullOwner = -1
  }

  private func setAmount(_ amount: Double) {
    guard drive.amount != amount else { return }
    drive.amount = amount
    weight.set(AuraAnimation.spring(amount / AuraFlow.pull.amountMax, 220, 0.6))
  }

  private func arm(_ index: Int) {
    let oldChosen = drive.chosen
    if oldChosen != index {
      let oldFormed = formed.value
      let oldLeaving = drive.leaving
      let oldUnformed = unformed.value
      let nextFormed = index == oldLeaving ? oldUnformed : 0
      for i in 0..<5 {
        let before = auraLipPose(
          Double(i), Double(oldChosen), oldFormed, Double(oldLeaving), oldUnformed)
        let after = auraLipPose(Double(i), Double(index), nextFormed, Double(oldChosen), oldFormed)
        let at = i * 4
        offsets[at].set(before.dx + offsets[at].value - after.dx)
        offsets[at + 1].set(before.dy + offsets[at + 1].value - after.dy)
        offsets[at + 2].set(before.scale + offsets[at + 2].value - after.scale)
        offsets[at + 3].set(before.opacity + offsets[at + 3].value - after.opacity)
        for j in 0..<4 { offsets[at + j].set(AuraAnimation.spring(0, 400, 0.7)) }
      }
      drive.leaving = oldChosen
      unformed.set(oldFormed)
      unformed.set(AuraAnimation.spring(0, 400, 0.7))
      drive.chosen = index
      formed.set(nextFormed)
    }
    formed.set(AuraAnimation.spring(1, 400, 0.7))
    wide.set(AuraAnimation.spring(1, 400, 0.85))
    setAmount(AuraFlow.pull.amountDefault)
    drive.phase = .armed
    changed()
  }

  func dismiss() {
    guard drive.phase == .armed, sinkingAt == nil else { return }
    drive.phase = .rest
    formed.set(AuraAnimation.spring(0, 400, 0.7))
    unformed.set(AuraAnimation.spring(0, 400, 0.7))
    wide.set(AuraAnimation.spring(0, 400, 0.85))
    changed()
  }

  func pick(_ index: Int) {
    touch()
    guard available() else { return }
    if drive.phase == .armed && drive.chosen == index { dismiss() } else { arm(index) }
  }

  func beginPull(_ index: Int) {
    guard available() else { return }
    wasArmed = drive.phase == .armed
    arm(index)
    drive.phase = .pulling
    pullOwner = index
    setAmount(0)
    pullX.set(0)
    pullY.set(0)
    changed()
  }

  func movePull(_ index: Int, dx: Double, dy: Double) {
    guard drive.phase == .pulling, pullOwner == index else { return }
    let y = auraRubber(max(dy, 0), auraPullLimit())
    pullX.set((dx < 0 ? -1 : 1) * auraRubber(abs(dx), AuraFlow.pull.sideways))
    pullY.set(y)
    let amount = auraAmountAt(y)
    if amount != drive.amount {
      setAmount(amount)
      tickHaptic.selectionChanged()
      changed()
    }
    wake()
  }

  func endPull(_ index: Int, vx: Double, vy: Double, cancelled: Bool) {
    guard drive.phase == .pulling, pullOwner == index else { return }
    pullOwner = -1
    if !cancelled && drive.amount > 0 {
      release()
      return
    }
    pullX.set(AuraAnimation.spring(0, 400, 0.7, velocity: vx))
    pullY.set(AuraAnimation.spring(0, 400, 0.7, velocity: vy))
    drive.phase = .armed
    if wasArmed {
      setAmount(AuraFlow.pull.amountDefault)
      changed()
    } else {
      dismiss()
    }
  }

  func send() {
    touch()
    guard available() else { return }
    if drive.phase == .rest {
      nudgeAt = CACurrentMediaTime() * 1000
      drive.nudge = 0
      nudgeHaptic.impactOccurred()
    } else {
      sinkingAt = CACurrentMediaTime() * 1000
      pullY.set(0)
    }
    wake()
  }

  private func release() {
    drive.fromX = auraSlotX(Double(drive.chosen)) + pullX.value
    drive.fromY = 164 + AuraFlow.drop.sag * formed.value + pullY.value
    pullX.set(0)
    pullY.set(0)
    sinkingAt = nil
    landed = false
    drive.phase = .sending
    drive.clock = 0
    sendingAt = AuraUIRuntime.shared.now()
    changed()
  }

  func press(_ index: Int, down: Bool, face: Bool = false) {
    guard !arriving else { return }
    let value = face ? facePresses[index] : presses[index]
    value.set(
      down
        ? AuraAnimation.timing(1, AuraMotion.pressIn)
        : AuraAnimation.spring(0, AuraMotion.releaseDuration, AuraMotion.releaseDampingRatio))
    wake()
  }

  private func changed() {
    if drive.phase == .armed || drive.phase == .pulling { sent = nil }
    identity.set(AuraAnimation.timing(activeRoute ? 1 : 0, AuraFlow.views.route.duration))
    view?.syncWords()
    wake()
  }

  private func commit() {
    landed = true
    wide.set(AuraAnimation.spring(0, 400, 0.85))
    formed.set(AuraAnimation.spring(0, 400, 0.7))
    receiptX = drive.fromX
    let late = drive.clock - auraSendMoments(drive.fromY).impact
    let duration = reduced ? AuraFlow.views.receiptFade : AuraFlow.send.rippleLife
    receiptClock.set(min(late, duration))
    receiptClock.set(AuraAnimation.timing(duration, max(0, duration - late), linear: true))
    previous = late < duration ? receipt : nil
    let dollars = Int(drive.amount)
    receipt = AuraReceipt(friend: drive.chosen, cents: dollars * 100)
    balanceCents -= dollars * 100
    let name = AuraPeople.names[drive.chosen]
    sent = (name, dollars)
    landingHaptic.notificationOccurred(.success)
    UIAccessibility.post(
      notification: .announcement,
      argument: AuraL10n.format("announcement", dollars, name))
    sentTimer?.invalidate()
    if let proxy {
      sentTimer = Timer.scheduledTimer(
        timeInterval: AuraFlow.views.hint.sentHold / 1000, target: proxy,
        selector: #selector(AuraDisplayProxy.confirmation), userInfo: nil, repeats: false)
    }
    changed()
  }

  /// Values are stepped in place. Idle glass and transforms have no display-link work.
  func step(_ display: CADisplayLink) {
    let now = display.timestamp * 1000
    AuraUIRuntime.shared.timestamp = now
    defer { AuraUIRuntime.shared.timestamp = nil }
    if !arrived {
      if warm >= AuraFlow.enter.hold {
        arrived = true
        view?.words.arrived = true
        shown.set(AuraAnimation.timing(1, AuraFlow.enter.reveal))
        if reduced { drive.entered = -1 } else { enteringAt = now }
      }
    }
    for value in values { value.tick(now) }
    if let start = enteringAt {
      drive.entered = now - start
      if drive.entered >= auraEnterLength() {
        drive.entered = -1
        enteringAt = nil
      }
    }
    if let start = nudgeAt {
      drive.nudge = now - start
      if drive.nudge >= auraNudgeLength() {
        drive.nudge = -1
        nudgeAt = nil
      }
    }
    if let start = sinkingAt {
      let elapsed = now - start
      pullY.set(auraSinkDepth(elapsed, auraDepthFor(drive.amount)))
      if elapsed >= AuraFlow.send.sink { release() }
    }
    if let start = sendingAt {
      drive.clock = now - start
      let moments = auraSendMoments(drive.fromY)
      if drive.clock >= moments.impact && !landed { commit() }
      if drive.clock >= moments.end {
        reset()
        changed()
      }
    }
    if previous != nil && !receiptClock.active {
      previous = nil
      view?.syncWords()
    }
    if drive.phase == .rest && formed.value == 0 && unformed.value == 0 {
      drive.chosen = -1
      drive.leaving = -1
    }
    drive.formed = formed.value
    drive.unformed = unformed.value
    drive.pullX = pullX.value
    drive.pullY = pullY.value
    drive.wide = wide.value
    drive.weight = weight.value
    for i in 0..<4 { drive.press[i] = presses[i].value }
    view?.renderFrame()
    if arrived && enteringAt == nil && nudgeAt == nil && sinkingAt == nil && sendingAt == nil
      && !values.contains(where: { $0.active })
    {
      display.isPaused = true
    }
  }
}
