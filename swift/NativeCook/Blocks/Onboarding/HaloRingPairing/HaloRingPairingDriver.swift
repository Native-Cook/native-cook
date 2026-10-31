import SwiftUI
import UIKit

private struct HaloRingPairingTrack {
  var from: Double, to: Double, start: Double, duration: Double
  var curve: Int = 0
  var omega0 = 0.0, velocity0 = 0.0
  func spring(_ t: Double) -> (Double, Double) {
    let x = max(0,t-start), x0 = from-to, zeta = 0.6, wd = omega0*0.8
    if wd == 0 { return (to,0) }
    let envelope = exp(-zeta*omega0*x)
    let position = envelope*(sin(wd*x)*(velocity0+zeta*omega0*x0)/wd+x0*cos(wd*x))
    let velocity = -zeta*omega0*position+envelope*(cos(wd*x)*(velocity0+zeta*omega0*x0)-wd*x0*sin(wd*x))
    let relativeEnergy = (omega0*omega0*position*position+velocity*velocity)/max(1e-30,omega0*omega0*x0*x0)
    return relativeEnergy <= 6e-9 ? (to,0) : (to+position,velocity)
  }
  func value(_ t: Double) -> Double {
    let u = HaloRingPairingMotion.seg(t,start,start+duration)
    let v: Double
    switch curve {
    case 1: v = HaloRingPairingMotion.bezier(u,0.77,0,0.175,1)
    case 2: v = HaloRingPairingMotion.bezier(u,0.23,1,0.32,1)
    case 3: v = u
    case 4: return spring(t).0
    default: v = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
    }
    return from+(to-from)*v
  }
}

@Observable final class HaloRingPairingDriver {
  private(set) var revision = 0
  var holding = false
  private var epoch = Date()
  private var auto = false
  private var reduced = false
  private var manual = false
  private var base = HaloRingPairingFrame()
  private var tracks: [WritableKeyPath<HaloRingPairingFrame, Double>: HaloRingPairingTrack] = [:]
  private var lastTouch = 0.0
  private var foundAt = 1.8
  private var phaseOffset = 0.0
  private var manualStep = 0
  private var dragYaw = 0.0
  private var spinning = false
  private var decayStart = 0.0
  @ObservationIgnored private var decayVelocity = 0.0
  @ObservationIgnored private var decayPosition = 0.0
  @ObservationIgnored private var decayLast = 0.0
  @ObservationIgnored private var decayDone = false
  @ObservationIgnored private var hasDecay = false
  @ObservationIgnored private var foundHaptic: Task<Void,Never>?
  @ObservationIgnored private var inactiveSince: Date?
  var elapsed: Double { Date().timeIntervalSince(epoch) }
  func idle(at date: Date) -> Bool {
    reduced && date.timeIntervalSince(epoch) > max(foundAt+0.6,lastTouch+1.5) && !holding
  }
  func setActive(_ active: Bool) {
    if active {
      if let stopped = inactiveSince { epoch = epoch.addingTimeInterval(Date().timeIntervalSince(stopped)); inactiveSince = nil }
    } else if inactiveSince == nil { inactiveSince = Date() }
    revision += 1
  }
  func cancel() { foundHaptic?.cancel() }
  private func scheduleFound(_ delay: Double) {
    foundHaptic?.cancel()
    foundHaptic = Task { @MainActor in
      try? await Task.sleep(for:.seconds(max(0,delay)))
      guard !Task.isCancelled else { return }
      UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
  }
  func configure(autoPlay: Bool, reduced: Bool) {
    foundHaptic?.cancel(); hasDecay = false; inactiveSince = nil
    epoch = Date(); self.auto = autoPlay && !reduced; self.reduced = reduced
    manual = !auto; base = HaloRingPairingFrame(); tracks = [:]
    manualStep = 0; lastTouch = 0; foundAt = 1.8; phaseOffset = 0
    if manual { searching(at:0) }
    revision += 1
  }
  private func animate(_ key: WritableKeyPath<HaloRingPairingFrame, Double>, _ to: Double, _ at: Double, _ duration: Double, _ curve: Int = 0, _ delay: Double = 0) {
    let old = tracks[key]?.value(at) ?? base[keyPath:key]
    var v0 = tracks[key]?.curve == 4 ? tracks[key]!.spring(at).1 : 0
    if (to > old && v0 < 0) || (to < old && v0 > 0) { v0 = 0 }
    tracks[key] = HaloRingPairingTrack(from:old,to:to,start:at+delay,duration:duration,curve:curve,
      omega0:curve == 4 ? HaloRingPairingMotion.springOmega(old-to,v0,duration) : 0,velocity0:v0)
  }
  private func searching(at t: Double) {
    foundAt = t+1.8
    scheduleFound(1.8)
    animate(\.condense,1,foundAt,reduced ? 0.25 : 1.2,1)
    animate(\.search,0,foundAt,0.6,2)
    animate(\.found,1,foundAt,0.4,2,reduced ? 0 : 0.7)
  }
  private func manualFrame(_ t: Double) -> HaloRingPairingFrame {
    var f = base
    for (key,track) in tracks { f[keyPath:key] = track.value(t) }
    if hasDecay {
      if t > decayLast && !decayDone {
        let dt = min(max(t-decayLast,0),0.064)
        decayVelocity *= exp(-(1-0.991)*(t-decayStart)*1000*0.1)
        decayPosition += decayVelocity*dt
        decayLast = t
        decayDone = abs(decayVelocity) < 1
      }
      f.yaw = decayPosition
    }
    f.phase = reduced ? 0 : phaseOffset+2*Double.pi*t/11
    return f
  }
  func frame(at date: Date) -> HaloRingPairingFrame {
    let t = max(0,(inactiveSince ?? date).timeIntervalSince(epoch))
    if !manual {
      var loopTime = t.truncatingRemainder(dividingBy:11)
      #if DEBUG
      if UserDefaults.standard.object(forKey:"ncPinTime") != nil { loopTime = UserDefaults.standard.double(forKey:"ncPinTime") }
      #endif
      return HaloRingPairingMotion.frameAt(loopTime)
    }
    let resume = max(lastTouch,manualStep == 0 ? foundAt : lastTouch)+2
    if auto && !holding && !spinning && t >= resume {
      let u = t-resume
      if u >= 1.7 { return HaloRingPairingMotion.frameAt((u-1.7).truncatingRemainder(dividingBy:11)) }
      var f = manualFrame(resume)
      f.fly *= 1-HaloRingPairingMotion.bezier(u/0.7,0.77,0,0.175,1)
      f.paired *= 1-HaloRingPairingMotion.defaultEase(u/0.3)
      f.found *= 1-HaloRingPairingMotion.defaultEase(u/0.3)
      let goalYaw = 0.3+2*Double.pi*floor((f.yaw-0.3)/(2*Double.pi)+0.5)
      f.yaw += (goalYaw-f.yaw)*HaloRingPairingMotion.bezier(u/0.9,0.77,0,0.175,1)
      f.scale += (1-f.scale)*HaloRingPairingMotion.defaultEase(u/0.5)
      f.size = 9
      f.condense *= 1-HaloRingPairingMotion.bezier((u-0.6)/0.8,0.77,0,0.175,1)
      f.search += (1-f.search)*HaloRingPairingMotion.defaultEase((u-1)/0.7)
      return f
    }
    return manualFrame(t)
  }
  func takeOver() {
    let t = elapsed
    let f = frame(at:Date())
    let resume = max(lastTouch,manualStep == 0 ? foundAt : lastTouch)+2
    if manual && !(auto && !holding && !spinning && t >= resume) {
      if manualStep == 0 && t >= foundAt { manualStep = 1 }
      lastTouch = t; revision += 1; return
    }
    let wasManual = manual
    hasDecay = false
    phaseOffset = f.phase-2*Double.pi*t/11
    base = f; tracks = [:]; decayVelocity = 0; manual = true
    manualStep = f.step; lastTouch = t
    animate(\.finger,0,t,0.15); animate(\.pressPair,0,t,0.15)
    if manualStep == 0 {
      // Search keeps its deadline in manual mode; auto search is found immediately on touch.
      let delay = wasManual ? max(0,foundAt-t) : 0
      foundAt = t+delay
      scheduleFound(delay)
      animate(\.condense,1,t,reduced ? 0.25 : 1.2,1,delay)
      animate(\.search,0,t,0.6,2,delay)
      animate(\.found,1,t,0.4,2,delay+(reduced ? 0 : 0.7))
    } else {
      animate(\.condense,1,t,0.3)
      animate(\.search,0,t,0.3)
      animate(\.fly,manualStep == 2 ? 1 : 0,t,0.3)
      animate(\.paired,manualStep == 2 ? 1 : 0,t,0.3)
      if manualStep == 1 { animate(\.found,1,t,0.3) }
    }
    revision += 1
  }
  func beginScreen() { if !holding { takeOver(); holding = true } }
  func endScreen() { holding = false; lastTouch = elapsed; revision += 1 }
  func drag(_ state: UIGestureRecognizer.State,_ translation: CGSize,_ velocity: CGVector) {
    let t = elapsed
    switch state {
    case .began:
      takeOver(); holding = true; dragYaw = frame(at:Date()).yaw; spinning = false
    case .changed:
      guard manualStep == 1 || frame(at:Date()).step == 1 else { return }
      if !spinning && hypot(translation.width,translation.height) < 3 { return }
      if !spinning {
        dragYaw = frame(at:Date()).yaw
        base.yaw = dragYaw; tracks[\.yaw] = nil; hasDecay = false; decayVelocity = 0
      }
      spinning = true; manualStep = 1; lastTouch = t
      tracks[\.yaw] = nil; base.yaw = dragYaw+translation.width*0.012
    case .ended, .cancelled, .failed:
      holding = false; lastTouch = t
      if spinning && !reduced {
        decayVelocity = max(-12,min(12,velocity.dx*0.012)); decayStart = t
        decayPosition = base.yaw; decayLast = t; decayDone = false; hasDecay = true
      }
      spinning = false
    default: break
    }
    revision += 1
  }
  func choose(_ size: Int) {
    takeOver(); guard manualStep == 1, base.size != size else { return }
    let t = elapsed; base.size = size
    animate(\.scale,HaloRingPairingMotion.scaleOf(size),t,reduced ? 0.2 : 0.45,reduced ? 0 : 4)
    if !reduced { tracks[\.bumpProgress] = nil; base.bumpProgress = 0; animate(\.bumpProgress,1,t,0.7,3) }
    UISelectionFeedbackGenerator().selectionChanged(); revision += 1
  }
  func pair() {
    takeOver(); guard manualStep == 1 else { return }
    let t = elapsed; manualStep = 2
    base.yaw = frame(at:Date()).yaw; tracks[\.yaw] = nil; hasDecay = false; decayVelocity = 0
    animate(\.fly,1,t,reduced ? 0.3 : 0.8,1)
    animate(\.paired,1,t,0.5,2,reduced ? 0 : 0.3)
    animate(\.found,0,t,0.25)
    UIImpactFeedbackGenerator(style:.medium).impactOccurred(); revision += 1
  }
  func unpair() {
    takeOver(); guard manualStep == 2 else { return }
    let t = elapsed; manualStep = 1
    animate(\.fly,0,t,reduced ? 0.3 : 0.7,1)
    animate(\.paired,0,t,0.3)
    animate(\.found,1,t,0.4,0,reduced ? 0 : 0.4); revision += 1
  }
}
