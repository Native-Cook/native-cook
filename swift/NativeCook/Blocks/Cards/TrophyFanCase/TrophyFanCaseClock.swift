import AVFoundation
import SwiftUI
import UIKit

final class TrophyFanCaseClock: ObservableObject {
  typealias M = TrophyFanCaseMotion
  @Published var running = false
  private(set) var hand = M.Hand()
  private var layout: M.Layout?
  private var want = M.Want()
  private var time = 0.0
  private var now = 0.0
  private var last = -10.0
  private var date: Date?
  private var replayHand: M.Hand?
  private var replayTime = 0.0
  // Same launch flag as Shared/DemoSound.inAutoPlay; this isolated port copy predates that helper.
  private let demoSound = !(UserDefaults.standard.string(forKey: "recordingSound") ?? "").isEmpty
  private var tracking = false
  private var reduced = false
  private var pinnedHand: M.Hand?
  private var pinnedTime: Double?
  private var players: [String: AVAudioPlayer] = [:]
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private struct Touch {
    var startX = 0.0, startY = 0.0, at = 0.0
    var card = -1, mode = 0, detent = -9
  }
  private var tc = Touch()

  func active(_ active: Bool, _ reduced: Bool, _ pinned: Bool) {
    date = nil
    self.reduced = reduced
    if active && !reduced && !pinned {
      if players.isEmpty {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for name in ["fan", "flip", "lift", "tick"] {
          if let url = TrophyFanCaseResources.url("tfc-\(name)", "wav"),
             let p = try? AVAudioPlayer(contentsOf: url) {
            p.volume = name == "tick" ? 0.45 : 0.75
            p.prepareToPlay()
            players[name] = p
          }
        }
      }
    } else {
      for p in players.values { p.stop() }
      if reduced { players.removeAll() }
    }
  }
  private func cue(_ name: String) {
    guard !reduced, let p = players[name] else { return }
    p.currentTime = 0
    p.play()
  }
  private func buzz(_ kind: Int) {
    if kind == 0 { selection.selectionChanged() }
    else if kind == 1 { light.impactOccurred() }
    else { medium.impactOccurred() }
  }
  func reset(_ reduced: Bool) {
    layout = nil
    pinnedHand = nil
    pinnedTime = nil
    hand = M.Hand()
    want = M.Want(spread: reduced ? 1 : 0)
    time = 0; now = 0; last = -10; date = nil
    tracking = false
    replayHand = nil
    replayTime = 0
    self.reduced = reduced
    if running { running = false }
  }
  func sample(_ at: Date, _ l: M.Layout, _ auto: Bool, _ reduced: Bool, _ pinned: Double?, active: Bool) -> M.Hand {
    if layout == nil {
      layout = l
      hand = M.settled(l, reduced)
      want = M.Want(spread: reduced ? 1 : 0)
    } else if layout != l {
      layout = l
      pinnedHand = nil
    }
    if let pinned {
      if pinnedHand == nil || pinnedTime != pinned {
        pinnedHand = M.at(pinned, l, reduced)
        pinnedTime = pinned
      }
      return pinnedHand ?? hand
    }
    guard active else { date = nil; return hand }
    // The script follows wall time even on Xcode Cloud's slow software renderer.
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.5)
    date = at
    guard auto || running else { return hand }
    now += dt
    let touched = hand.held >= 0 || now - last < 2
    if auto && !touched {
      let previous = time
      let previousFlip = hand.cards[1].f
      if replayHand == nil { replayHand = hand; replayTime = time }
      time += dt
      if time >= M.loop {
        time -= M.loop
        replayHand = M.settled(l)
        replayTime = 0
      }
      // Cache full fixed steps. A partial display step never feeds back into the replay.
      // This gives the same pose as the RN debug replay at any display frame rate.
      let step = 1.0 / 120
      var replay = replayHand ?? hand
      while replayTime + step <= time {
        M.step(&replay, step, M.script(replayTime), l)
        replayTime += step
      }
      replayHand = replay
      hand = replay
      if time > replayTime { M.step(&hand, time - replayTime, M.script(replayTime), l) }
      want = M.script(time)
      if demoSound {
        func crossed(_ beat: Double) -> Bool {
          previous <= time ? previous < beat && time >= beat : previous < beat || time >= beat
        }
        if crossed(0.45) || crossed(6.45) { cue("fan") }
        // The same detents the finger passes while scrubbing the fan.
        for beat in [1.4, 1.847924861456107, 2.05, 2.2520751385438933] where crossed(beat) { cue("tick") }
        if crossed(2.85) { cue("lift") }
        if previousFlip <= 0.1 && hand.cards[1].f > 0.1 { cue("flip") }
        if crossed(5.25) { cue("flip") }
      }
    } else {
      replayHand = nil
      M.step(&hand, min(dt, 0.05), want, l)
    }
    if !auto && !tracking && stable() && running {
      // Only publish an idle transition, never a per-frame hand/array update.
      DispatchQueue.main.async { [weak self] in
        guard let self, !self.tracking, self.stable() else { return }
        self.running = false
        self.date = nil
      }
    }
    return hand
  }
  private func stable() -> Bool {
    guard let layout, hand.held < 0 else { return false }
    var next = hand
    M.step(&next, 1.0 / 120, want, layout)
    if abs(next.spread - hand.spread) > 1e-8 || abs(next.focusOn - hand.focusOn) > 1e-8 { return false }
    for i in 0..<4 {
      let b = hand.cards[i], n = next.cards[i]
      if b.ph != n.ph || abs(n.x - b.x) > 1e-7 || abs(n.y - b.y) > 1e-7
        || abs(n.r - b.r) > 1e-8 || abs(n.s - b.s) > 1e-9 || abs(n.f - b.f) > 1e-7 { return false }
    }
    return true
  }
  private func wake() {
    if !running { date = nil; running = true }
  }
  private func choose(_ i: Int) {
    want.spread = 1; want.focusOn = 0; want.sel = i
  }
  func accessibilityOpen(_ i: Int) {
    last = now
    want.spread = 1; want.focusOn = 0; want.sel = want.sel == i ? -1 : i
    buzz(1)
    wake()
  }
  func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ translation: CGSize, _ velocity: CGVector) {
    guard let l = layout else { return }
    let x = point.x, y = point.y, dy = translation.height
    switch state {
    case .began:
      last = now
      tc = Touch(startX: x, startY: y, at: now, card: M.cardAt(hand, x, y))
      want.spread = hand.spread > 0.5 || hand.sel >= 0 ? 1 : 0
      tracking = true
      wake()
    case .changed:
      last = now
      if hand.sel >= 0 {
        if tc.card == hand.sel && dy > 0 { hand.cards[hand.sel].y = l.sy + dy * 0.6 }
        return
      }
      if tc.mode == 1 {
        hand.hx = x; hand.hy = y
        return
      }
      let wt = want
      if wt.spread < 0.5 {
        want.spread = 1
        cue("fan")
        return
      }
      let f = max(-0.4, min(3.4, ((x - l.cx) / 100) + 1.5))
      want.focus = f; want.focusOn = 1
      let d = Int(max(0, min(3, f)).rounded())
      if d != tc.detent {
        tc.detent = d
        cue("tick"); buzz(0)
      }
      if dy < -36 && tc.card >= 0 {
        hand.held = d; hand.hx = x; hand.hy = y
        tc.mode = 1; tc.card = d; tc.detent = d
        want = wt; want.focusOn = 0
        cue("lift"); buzz(1)
      }
    case .ended, .cancelled, .failed:
      last = now
      tracking = false
      let tap = hypot(translation.width, dy) < 8 && now - tc.at < 0.35
      if hand.sel >= 0 {
        if tap || dy > 60 || velocity.dy > 700 {
          want.sel = -1
          cue("flip"); buzz(1)
        }
        return
      }
      if tc.mode == 1 {
        let i = hand.held
        hand.held = -1
        guard i >= 0 else { return }
        if hand.cards[i].ph == 1 { hand.cards[i].ph = 0 }
        hand.cards[i].vx = velocity.dx
        hand.cards[i].vy = velocity.dy
        if dy < -90 || velocity.dy < -600 {
          choose(i)
          cue("flip"); buzz(2)
        } else { want.focusOn = 0 }
        return
      }
      if tap {
        if want.spread < 0.5 {
          want.spread = 1
          cue("fan"); buzz(1)
        } else if tc.card >= 0 {
          choose(tc.card)
          cue("lift"); buzz(2)
        }
        return
      }
      want.focusOn = 0
    default: break
    }
  }
}
