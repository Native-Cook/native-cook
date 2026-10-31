import SwiftUI
import UIKit

final class WavyFrameAuctionClock: ObservableObject {
  typealias M = WavyFrameAuctionMotion
  struct Sample { var s: Double; var phase: Double; var lift: Double; var seconds: Int }
  @Published var running = false
  @Published var open = false
  private var date: Date?
  private var now = 0.0, t = 0.0, last = -10.0
  private var s = 0.0, v = 0.0, target = 0.0, start = 0.0, lift = 0.0
  private var held = false
  private var remaining = 82354
  private var nextSecond: Double?
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  func active(_ active: Bool) {
    date = nil
    if !active { WavyFrameAuctionResources.shared.stop() }
  }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Sample {
    if let pinned {
      // The RN debug hook sets the analytic script pose. No physics are integrated here.
      return Sample(s: M.script(pinned), phase: 2 * .pi * pinned / M.loop, lift: M.lift(pinned), seconds: remaining)
    }
    // Only the physical spring uses the RN 50 ms integration cap. The loop uses wall time.
    let wall = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.5)
    date = at; now += wall
    let dt = min(wall, 0.05)
    let autoPlaying = auto && !held && now - last >= 2
    if autoPlaying {
      let before = t
      t = (t + wall).truncatingRemainder(dividingBy: M.loop)
      s = M.script(t); target = s > 0.5 ? 1 : 0; v = 0
      if DemoSound.inAutoPlay {
        if before < 2.2 && t >= 2.2 { WavyFrameAuctionResources.shared.cue("unfold", reduced) }
        if before < 6.8 && t >= 6.8 { WavyFrameAuctionResources.shared.cue("fold", reduced) }
      }
    } else if !held {
      let k = reduced ? 600.0 : 170.0
      v += (k * (target - s) - 2 * sqrt(k) * 0.72 * v) * dt
      s += v * dt
    }
    if held { lift += (1 - lift) * (1 - exp(-dt * 10)) }
    else if !reduced && auto && now - last >= 2 { lift = M.lift(t) }
    else { lift += -lift * (1 - exp(-dt * 10)) }
    let isOpen = s > 0.6
    if isOpen {
      if nextSecond == nil { nextSecond = now + 1 }
      if let tick = nextSecond, now >= tick { remaining = max(0, remaining - 1); nextSecond = tick + 1 }
    } else { nextSecond = nil }
    if open != isOpen { DispatchQueue.main.async { [weak self] in self?.open = isOpen } }
    if reduced && !held && abs(target - s) < 0.00001 && abs(v) < 0.0001 && lift < 0.0001 {
      s = target; v = 0; lift = 0
      if running { DispatchQueue.main.async { [weak self] in self?.running = false } }
    }
    return Sample(s: s, phase: 2 * .pi * (auto ? t : now) / M.loop, lift: lift, seconds: remaining)
  }
  func touch(_ state: UIGestureRecognizer.State, _ dy: Double, _ vy: Double, _ reduced: Bool) {
    switch state {
    case .began: held = true; start = s; last = now; running = true; date = nil
    case .changed: last = now; s = M.clamp(start - dy / 360)
    case .ended, .cancelled, .failed:
      last = now; held = false; v = -vy / 360
      let to = s + v * 0.18 > 0.5 ? 1.0 : 0.0
      if to != target { WavyFrameAuctionResources.shared.cue(to == 1 ? "unfold" : "fold", reduced); medium.impactOccurred() }
      target = to
    default: break
    }
  }
  func toggle(_ reduced: Bool) {
    last = now; target = target > 0.5 ? 0 : 1; v = 0; date = nil; running = true
    WavyFrameAuctionResources.shared.cue(target == 1 ? "unfold" : "fold", reduced)
    medium.impactOccurred()
  }
}
