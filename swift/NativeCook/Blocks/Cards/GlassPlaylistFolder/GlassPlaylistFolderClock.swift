import AVFoundation
import SwiftUI
import UIKit

final class GlassPlaylistFolderClock: ObservableObject {
  typealias M = GlassPlaylistFolderMotion
  @Published var running = false
  var f = M.Frame()
  private var date: Date?
  private var now = 0.0
  private var last = -10.0
  private var drag = -1.0
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let selection = UISelectionFeedbackGenerator()
  private var pressAt = 0.0
  private var pressStart = 0.0
  private var pressAim = 0.0
  private var pressStiffness = 0.0
  private var pressInitialEnergy = 0.0
  private var pressDamping = 1.0
  private var pressVelocity = 0.0
  private var pressInitialVelocity = 0.0
  private var pressBegan = 0.0
  private var pressInFlight = false
  private var pinnedTime: Double?
  private var pinnedFrame = M.Frame()

  func reset(_ reduced: Bool) {
    f = M.Frame(s: M.Springs(reduced ? 1 : 0))
    now = 0
    last = -10
    drag = -1
    date = nil
    running = false
    pressInFlight = false
    if !reduced && players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(
        .ambient, mode: .default, options: .mixWithOthers)
      for name in ["open", "close", "tap"] {
        if let url = Bundle.main.url(
          forResource: "GlassPlaylistFolder-gpf-\(name)", withExtension: "wav"),
          let p = try? AVAudioPlayer(contentsOf: url)
        {
          p.volume = name == "tap" ? 0.5 : 0.85
          p.prepareToPlay()
          players[name] = p
        }
      }
    }
    if reduced {
      for p in players.values { p.stop() }
      players.removeAll()
    }
  }
  func active() { date = nil }
  func sample(
    _ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?, _ preview: Double? = nil
  ) -> M.Frame {
    if let pinned {
      if pinnedTime != pinned {
        pinnedTime = pinned
        pinnedFrame = M.at(pinned)
      }
      return pinnedFrame
    }
    // The loop runs on wall-clock time (a slow recorder must not stretch it); springs take a clamped step.
    let raw = max(0, date.map { at.timeIntervalSince($0) } ?? 0.016)
    let dt = min(raw, 0.05)
    date = at
    now += dt
    if let preview { f.t = preview }
    if !reduced && preview == nil {
      let touched = drag >= 0 || now - last < 2
      if auto && !touched {
        let before = f.t
        f.t += min(raw, 0.5)
        if f.t >= M.loop { f.t -= M.loop }
        f.s.setTarget(f.t >= 2 && f.t < 5.35 ? 1 : 0, now)
        f.s.liftTarget = f.t >= 3.4 && f.t < 4.7 ? 1 : 0
        f.s.hovered = 1
        // Cover hover only has a haptic; opening and closing have sound cues.
        if DemoSound.inAutoPlay && pressAim == 0 {
          for (beat, name) in [(2.0, "open"), (5.35, "close")] {
            let crossed = before <= f.t
              ? before < beat && f.t >= beat : before < beat || f.t >= beat
            if crossed { cue(name, reduced) }
          }
        }
      }
      if drag >= 0 {
        f.s.open = drag
        f.s.ov = 0
      }
      f.s.step(dt, now)
    }
    if pressInFlight { stepPress() }
    f.dragging = drag >= 0
    if running && (!auto || reduced) && drag < 0 && !f.s.moving && !pressInFlight {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return f
  }
  private func cue(_ name: String, _ reduced: Bool) {
    guard !reduced, let p = players[name] else { return }
    p.currentTime = 0
    p.play()
  }
  // Reanimated 4.5 duration spring: mass 4, stiffness found by bisection,
  // perceptual duration * 1.5, and a relative energy threshold of 6e-9.
  private func stepPress() {
    let z = pressDamping
    let w = sqrt(pressStiffness / 4)
    let t = max(0, now - pressAt)
    let x = pressStart - pressAim
    if z < 1 {
      let wd = w * sqrt(1 - z * z)
      let b = (pressInitialVelocity + z * w * x) / wd
      let e = exp(-z * w * t)
      f.press = pressAim + e * (x * cos(wd * t) + b * sin(wd * t))
      pressVelocity =
        e * ((-z * w * x + b * wd) * cos(wd * t) + (-z * w * b - x * wd) * sin(wd * t))
    } else {
      let b = pressInitialVelocity + w * x
      let e = exp(-w * t)
      f.press = pressAim + (x + b * t) * e
      pressVelocity = (b - w * (x + b * t)) * e
    }
    let energy =
      0.5 * pressStiffness * pow(f.press - pressAim, 2) + 2 * pressVelocity * pressVelocity
    if pressInitialEnergy == 0 || energy / pressInitialEnergy <= 6e-9 {
      f.press = pressAim
      pressVelocity = 0
      pressInFlight = false
    }
  }
  private func press(_ aim: Double, _ duration: Double, _ damping: Double) {
    pressStart = f.press
    pressAim = aim
    pressAt = now
    pressDamping = damping
    pressInitialVelocity = pressVelocity
    let x = pressStart - aim
    let v = pressInitialVelocity
    let settling = duration * 1.5
    func fraction(_ k: Double) -> Double {
      let w = sqrt(k / 4) * damping
      let e = exp(-w * settling)
      let position = (x + (v + x * w) * settling) * e
      let velocity = -w * position + (v + x * w) * e
      let initial = 0.5 * k * x * x + 2 * v * v
      return initial == 0
        ? 0 : (0.5 * k * position * position + 2 * velocity * velocity) / initial - 6e-9
    }
    var low = Double.ulpOfOne
    var high = 8000.0
    let direction = fraction(high) >= fraction(low) ? 1.0 : -1.0
    var mid = (low + high) / 2
    var count = 100
    while abs(fraction(mid)) > 6e-12 && count > 0 {
      count -= 1
      if fraction(mid) * direction < 0 { low = mid } else { high = mid }
      mid = (low + high) / 2
    }
    pressStiffness = mid
    pressInitialEnergy = 0.5 * mid * x * x
    pressInFlight = true
    date = nil
    running = true
  }
  func playBegin(_ reduced: Bool) {
    pressBegan = ProcessInfo.processInfo.systemUptime
    if reduced {
      f.press = 1
      pressInFlight = false
      running = true
    } else {
      press(1, 0.15, 1)
    }
  }
  func playEnd(_ success: Bool, _ reduced: Bool) {
    if reduced {
      f.press = 0
      pressInFlight = false
      running = true
    } else {
      press(0, 0.32, 0.7)
    }
    if success && ProcessInfo.processInfo.systemUptime - pressBegan <= 2 {
      cue("tap", reduced)
      medium.impactOccurred()
    }
  }
  func touch(
    _ state: UIGestureRecognizer.State, _ p: CGPoint, _ d: CGSize, _ v: CGVector,
    _ cx: Double, _ cy: Double, _ reduced: Bool
  ) {
    guard !reduced else { return }
    switch state {
    case .began:
      guard abs(p.x - cx) <= 170 && abs(p.y - cy) <= 200 else { return }
      drag = f.s.open
      last = now
      date = nil
      running = true
    case .changed:
      if drag >= 0 {
        drag = M.clamp(f.s.target - d.height / 160)
        last = now
      }
    case .ended, .cancelled, .failed:
      guard drag >= 0 else { return }
      let amount = drag
      drag = -1
      last = now
      if hypot(d.width, d.height) <= 10 {
        if f.s.target == 1 && p.y < cy - 60 {
          f.s.hovered = max(0, min(3, Int(floor((p.x - (cx - 120)) / 60))))
          f.s.liftTarget = f.s.liftTarget == 1 ? 0 : 1
          selection.selectionChanged()
          return
        }
        f.s.liftTarget = 0
        f.s.setTarget(f.s.target == 1 ? 0 : 1, now)
      } else {
        f.s.ov = -v.dy / 160
        f.s.open = amount
        f.s.liftTarget = 0
        f.s.setTarget(amount > 0.4 || v.dy < -500 ? 1 : 0, now)
      }
      cue(f.s.target == 1 ? "open" : "close", reduced)
      light.impactOccurred()
    default: break
    }
  }
}
