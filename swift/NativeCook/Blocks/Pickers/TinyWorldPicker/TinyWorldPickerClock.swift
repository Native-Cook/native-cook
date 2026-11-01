import SwiftUI
import AVFoundation
import UIKit

// The wall clock drives the script; only the touch integrator has a 50 ms clamp, as in RN.
final class TinyWorldPickerClock: ObservableObject {
  typealias M = TinyWorldPickerMotion
  @Published var pressed = -1
  var frame = M.Frame()
  private var date: Date?
  private var now = 0.0
  private var mV = 0.0, target = -1.0, yawV = 0.0, sqV = 0.0
  private var drag = false, edge = 0.0, p0 = 0.0, yaw0 = 0.0
  private var lastTouch = -100.0, scripted = false, loopT = 0.0, yawOff = 0.0
  private var armed = false
  private var players: [AVAudioPlayer] = []
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  func prepare() {
    guard players.isEmpty else { return }
    try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
    for (index, name) in ["morph", "commit", "tap"].enumerated() {
      if let url = Bundle.main.url(forResource: "TinyWorldPicker-twp-\(name)", withExtension: "wav"),
         let player = try? AVAudioPlayer(contentsOf: url) {
        player.volume = index == 0 ? 0.45 : 0.6
        player.prepareToPlay()
        players.append(player)
      }
    }
  }
  private func cue(_ i: Int, _ reduced: Bool) {
    guard !reduced, players.indices.contains(i) else { return }
    players[i].currentTime = 0
    players[i].play()
  }
  private func buzz(_ i: Int) {
    if i == 0 { selection.selectionChanged() }
    else if i == 1 { light.impactOccurred() }
    else { medium.impactOccurred() }
  }
  func active(_ active: Bool) {
    date = nil
    if active { prepare() }
    else { players.forEach { $0.stop() } }
  }
  private func touched() {
    lastTouch = now
  }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
    if let pinned { return M.at(pinned) }
    let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
    date = at
    let dt = min(wall, 0.05)
    now += wall
    frame.tm = (frame.tm + wall).truncatingRemainder(dividingBy: M.loop)
    let canScript = auto && !reduced && !drag && now - lastTouch > 3.5
      && (scripted || (target < 0 && frame.m == 0))
    if canScript {
      if !scripted {
        scripted = true
        loopT = Double(frame.cur) * M.segment + 0.6
        yawOff = frame.yaw - M.at(loopT).yaw
      }
      loopT = (loopT + wall).truncatingRemainder(dividingBy: M.loop)
      let s = M.at(loopT)
      frame.cur = s.m >= 1 ? s.nxt : s.cur
      frame.nxt = s.m > 0 ? s.nxt : s.cur
      frame.m = s.m >= 1 ? 0 : s.m
      frame.yaw = s.yaw + yawOff
      frame.sq = s.sq
      sqV = 0; mV = 0; yawV = -M.idleSpin
    } else {
      scripted = false
      if !drag {
        if frame.m > 0 || target >= 0 {
          let tgt = target >= 0 ? target : (frame.m > 0.5 ? 1 : 0)
          let k = reduced ? 700.0 : 160.0
          let c = reduced ? 53 : 2 * 0.82 * sqrt(k)
          mV += (k * (tgt - frame.m) - c * mV) * dt
          frame.m += mV * dt
          if frame.m < 0 { frame.m = 0; mV = 0 }
          if frame.m > 1.03 { frame.m = 1.03 }
          let dir = frame.nxt > frame.cur ? 1.0 : -1.0
          if !reduced { frame.yaw -= dir * mV * dt * 1.9 }
          if tgt == 1 && abs(1 - frame.m) < 0.004 && abs(mV) < 0.08 {
            frame.cur = frame.nxt; frame.m = 0; mV = 0; target = -1
            if !reduced { sqV -= 2.4 }
            buzz(2); cue(1, reduced)
          } else if tgt == 0 && frame.m < 0.004 && abs(mV) < 0.08 {
            frame.m = 0; mV = 0; target = -1; frame.nxt = frame.cur
          }
        }
        let idle = reduced ? 0.0 : -M.idleSpin
        yawV += (idle - yawV) * (1 - exp(-1.6 * dt))
        frame.yaw += yawV * dt
      }
      var sqT = reduced ? 0 : 0.12 * sin(Double.pi * M.clamp(frame.m))
      if !reduced && drag && edge != 0 { sqT = 0.07 * tanh(abs(edge) * 3) }
      sqV += (260 * (sqT - frame.sq) - 9 * sqV) * dt
      frame.sq += sqV * dt
      frame.sq = min(0.3, max(-0.25, frame.sq))
    }
    frame.glow += ((drag ? 1.0 : 0.0) - frame.glow) * (1 - exp(-8 * dt))
    frame.t = now
    // RN's ambient stars and bob keep moving even with reduced motion; preserve that behaviour.
    return frame
  }
  func begin() { touched() }
  func startDrag() {
    let dir = frame.m > 0 ? (frame.nxt > frame.cur ? 1.0 : -1.0) : 0
    drag = true; scripted = false; target = -1; mV = 0
    p0 = dir * frame.m; yaw0 = frame.yaw; armed = abs(p0) > 0.42
  }
  func update(_ translation: Double, _ width: Double, _ reduced: Bool) {
    var p = p0 - translation / (width * 0.62)
    while p >= 1 && frame.cur < 3 { frame.cur += 1; p0 -= 1; p -= 1; buzz(2) }
    while p <= -1 && frame.cur > 0 { frame.cur -= 1; p0 += 1; p += 1; buzz(2) }
    edge = 0
    if p > 0 && frame.cur < 3 { frame.nxt = frame.cur + 1; frame.m = min(p, 1) }
    else if p < 0 && frame.cur > 0 { frame.nxt = frame.cur - 1; frame.m = min(-p, 1) }
    else { frame.nxt = frame.cur; frame.m = 0; edge = p }
    frame.yaw = yaw0 + (reduced ? 0 : translation * 0.0105)
    let value = frame.m > 0.42
    if value != armed { armed = value; buzz(0) }
    lastTouch = now
  }
  func end(_ velocity: Double, _ width: Double, _ reduced: Bool) {
    guard drag else { return }
    drag = false; edge = 0
    let dir = frame.nxt > frame.cur ? 1.0 : (frame.nxt < frame.cur ? -1.0 : 0)
    if dir != 0 {
      let along = (-velocity / (width * 0.62)) * dir
      let commit = (frame.m > 0.42 && along > -0.9) || (along > 0.9 && frame.m > 0.04)
      target = commit ? 1 : 0; mV = along
      if commit && frame.m < 0.85 { cue(0, reduced) }
    }
    yawV = reduced ? 0 : max(-12, min(12, velocity * 0.0105))
    touched()
  }
  func tap(_ reduced: Bool) {
    touched(); scripted = false
    if frame.m > 0 && target < 0 { target = frame.m > 0.5 ? 1 : 0 }
    if !reduced { sqV -= 2.6; yawV -= 3 }
    buzz(1); cue(2, reduced)
  }
  func goTo(_ i: Int, _ reduced: Bool) {
    touched(); scripted = false
    guard (0..<4).contains(i), i != frame.cur, !drag else { return }
    if frame.m > 0 { frame.cur = frame.m > 0.5 ? frame.nxt : frame.cur }
    frame.nxt = i; frame.m = 0; mV = 0; target = 1
    cue(0, reduced)
  }
}
