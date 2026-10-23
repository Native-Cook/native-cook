import UIKit

/// One owner for the transfer, completion and drag. All times are active foreground
/// milliseconds; pauses freeze presentation values rather than replacing their targets.
@MainActor final class HorizonUpload {
  enum Step { case rest, transfer, hero, reset, drain }
  let clock = HorizonValue(), power = HorizonValue(0.2), crest = HorizonValue(), done = HorizonValue()
  let press = HorizonValue(), closePress = HorizonValue(), shiftX = HorizonValue(), shiftY = HorizonValue()
  let liftBase = HorizonValue(), stage = HorizonValue()
  private lazy var values = [clock, power, crest, done, press, closePress, shiftX, shiftY, liftBase, stage]
  let appearance = HorizonAppearance()
  private let haptics = HorizonHaptics()
  private(set) var phase = HorizonPhase.idle
  private(set) var step = Step.rest
  private(set) var automatic = true
  private(set) var active = true
  private(set) var elapsed = 0.0
  private(set) var time = 0.0
  private(set) var flipped = false
  private var rising = false, hovering = false, pulling = false
  private var hoverTime = 0.0, grabX = 0.0, grabY = 0.0
  private var resetDuration = 300.0, resetStartsUpload = false
  var reduced = false, opaque = false
  var lift: Double { liftBase.value + (hovering ? 0.06 * sin(2 * .pi * hoverTime / 4200) : 0) }
  var progress: Double { HorizonMath.upload(clock.value).progress }
  var percent: Int { min(100, Int(floor(progress * 100 + 1e-7))) }
  var pulse: Double { phase == .uploading && step == .transfer ? HorizonMath.upload(clock.value).pulse : 0 }
  var label: HorizonPhase { phase == .done && !flipped && step != .reset ? .uploading : phase }
  var retry: Bool { phase == .stopped || (phase == .done && (flipped || step == .reset)) }
  var closeEnabled: Bool { phase != .done || retry }
  var panelEnabled: Bool { !(phase == .done && step == .hero && crest.value < 1) }
  var cardScale: Double { reduced ? 1 - 0.008 * press.value : HorizonMath.cardScale(crest.value, press.value) }

  func touch() { automatic = false }
  private func impact(_ kind: String) { if active { haptics.fire(kind) } }
  private func setPhase(_ next: HorizonPhase) {
    if next != phase && (next == .done || next == .stopped) {
      UIAccessibility.post(notification: .announcement, argument: HorizonL10n.t(next == .done ? "complete" : "canceled"))
    }
    phase = next; power.timing(next.power, 400)
  }
  private func parkLift() {
    let height = lift
    liftBase.set(height); hovering = false; hoverTime = 0; rising = false
  }
  private func freeze() {
    for v in values where v !== shiftX && v !== shiftY { v.cancel() }
  }
  func sleep() {
    active = false; freeze(); press.set(0); closePress.set(0)
    shiftX.set(0); shiftY.set(0); pulling = false
  }
  func wake() { active = true; resumeClocks() }
  func preferences(reduced: Bool, opaque: Bool) {
    self.opaque = opaque
    guard reduced != self.reduced else { return }
    parkLift(); self.reduced = reduced
    if reduced { appearance.rotation = 0 }
    resumeClocks()
  }
  private func resumeClocks() {
    power.timing(phase.power, 400)
    guard active else { return }
    if phase == .paused { liftBase.timing(0, reduced ? 200 : 300); return }
    switch step {
    case .transfer: clock.timing(1, (1 - clock.value) * 5200, .linear)
    case .hero:
      if reduced { liftBase.timing(1, 200) }
      else if rising || crest.value >= 0.22 { rising = true; liftBase.spring(1, 620, 0.6) }
      else { liftBase.timing(-0.08, max(0, (0.12 - crest.value) * 1400)) }
      if reduced { crest.set(1) } else { crest.timing(1, (1 - crest.value) * 1400, .linear) }
      if flipped { if reduced { done.timing(1, 200) } else { done.spring(1, 520, 0.75) } }
    case .drain:
      let remaining = 420 * (1 - stage.value)
      liftBase.timing(0, min(reduced ? 200 : 300, remaining))
      clock.timing(0, remaining); stage.timing(1, remaining, .linear)
    case .reset:
      let remaining = resetDuration * (1 - stage.value)
      liftBase.timing(0, reduced ? min(200, remaining) : remaining)
      clock.timing(0, remaining); crest.timing(0, min(300, remaining))
      done.timing(0, min(300, remaining)); stage.timing(1, remaining, .linear)
    case .rest: break
    }
  }
  private func startTransfer() {
    parkLift(); liftBase.set(0); step = .transfer; setPhase(.uploading)
    if active { clock.timing(1, (1 - clock.value) * 5200, .linear) }
    impact("light")
  }
  private func beginReset(_ startAfter: Bool, _ duration: Double) {
    freeze(); parkLift(); flipped = false
    resetStartsUpload = startAfter; resetDuration = duration; stage.set(0)
    step = .reset; setPhase(startAfter ? .uploading : .done); resumeClocks()
  }
  func close() {
    touch(); guard closeEnabled else { return }
    if phase == .done || phase == .stopped { restart() } else { stop() }
  }
  func toggle() {
    touch(); guard panelEnabled else { return }
    if phase == .uploading {
      freeze(); parkLift(); liftBase.timing(0, reduced ? 200 : 300)
      setPhase(.paused); impact("light")
    } else if phase == .paused {
      setPhase(.uploading); resumeClocks(); impact("light")
    } else if phase == .done || phase == .stopped { beginReset(true, 300) }
    else { flipped = false; startTransfer() }
  }
  func restart() { touch(); beginReset(true, 300) }
  func stop() {
    touch(); guard phase == .uploading || phase == .paused else { return }
    freeze(); parkLift(); step = .drain; stage.set(0); flipped = false
    crest.timing(0, 300); done.timing(0, 300); setPhase(.stopped); resumeClocks(); impact("medium")
  }
  private func complete() {
    parkLift(); liftBase.timing(reduced ? 1 : -0.08, reduced ? 200 : 168)
    clock.set(1); step = .hero; setPhase(.done)
    if reduced { crest.set(1) } else { crest.timing(1, 1400, .linear) }
  }
  private func idle() {
    freeze(); parkLift(); liftBase.set(0); clock.set(0); crest.set(0); done.set(0)
    flipped = false; step = .rest; setPhase(.idle)
  }
  func advance(_ ms: Double) {
    guard active else { return }
    let previousChunk = HorizonMath.upload(clock.value).chunk
    for v in values { v.advance(ms) }
    time += ms
    if automatic {
      let before = elapsed; elapsed += ms
      let a = before.truncatingRemainder(dividingBy: 9700), b = elapsed.truncatingRemainder(dividingBy: 9700)
      let wrapped = floor(before / 9700) != floor(elapsed / 9700)
      if wrapped { idle() }
      if (a < 700 || wrapped) && b >= 700 {
        flipped = false; startTransfer(); clock.set(min(1, (b - 700) / 5200))
        clock.timing(1, max(0, 5900 - b), .linear)
      }
      if (a < 9100 || wrapped) && b >= 9100 { beginReset(false, 600) }
    }
    if clock.value == 1 && phase == .uploading && step == .transfer { complete() }
    if stage.value == 1 && step == .reset && phase != .paused {
      liftBase.set(0); clock.set(0); crest.set(0); done.set(0)
      if resetStartsUpload { startTransfer() } else { step = .rest; setPhase(.idle) }
    }
    if step == .hero && crest.value >= 0.4 && !flipped {
      flipped = true
      if reduced { done.timing(1, 200) } else { done.spring(1, 520, 0.75) }
      impact("success")
    }
    if !reduced && phase == .done && step == .hero && crest.value >= 0.22 && !rising {
      rising = true; liftBase.spring(1, 620, 0.6)
    }
    if !reduced && phase == .done && step == .hero && rising && crest.value >= 0.7 {
      if hovering { hoverTime = (hoverTime + ms).truncatingRemainder(dividingBy: 4200) }
      else { hovering = true; hoverTime = 0 }
    }
    let chunk = HorizonMath.upload(clock.value).chunk
    if chunk > 0 && chunk > previousChunk && !automatic && phase == .uploading && step == .transfer { impact("soft") }
    appearance.advance(ms, upload: self)
  }
  func beginDrag() {
    guard active else { return }
    touch(); shiftX.cancel(); shiftY.cancel()
    grabX = HorizonMath.pull(shiftX.value); grabY = HorizonMath.pull(shiftY.value); pulling = false
  }
  func startDrag() {
    guard active else { return }
    pulling = true; press.timing(0, 90); impact("light")
  }
  func moveDrag(_ x: Double, _ y: Double) {
    guard active && pulling else { return }
    shiftX.set(HorizonMath.rubber(grabX + x)); shiftY.set(HorizonMath.rubber(grabY + y))
  }
  func releaseDrag(_ x: Double, _ y: Double, _ vx: Double, _ vy: Double) {
    guard active else { return }
    if reduced { shiftX.timing(0, 200); shiftY.timing(0, 200) }
    else {
      shiftX.spring(0, 560, 0.62, velocity: HorizonMath.velocity(grabX + x, vx), bounded: true)
      shiftY.spring(0, 560, 0.62, velocity: HorizonMath.velocity(grabY + y, vy), bounded: true)
    }
    pulling = false
  }
  func finalizeDrag() {
    guard active else { return }
    if pulling || shiftX.value != 0 || shiftY.value != 0 {
      if reduced { shiftX.timing(0, 200); shiftY.timing(0, 200) }
      else { shiftX.spring(0, 560, 0.62); shiftY.spring(0, 560, 0.62) }
      pulling = false
    }
  }
  func releasePress() {
    if reduced { press.timing(0, 120); closePress.timing(0, 120) }
    else { press.spring(0, 300, 0.8); closePress.spring(0, 300, 0.7) }
  }
}
