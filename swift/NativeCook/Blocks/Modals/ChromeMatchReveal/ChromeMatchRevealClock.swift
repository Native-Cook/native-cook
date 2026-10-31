import AVFoundation
import SwiftUI
import UIKit

final class ChromeMatchRevealClock: ObservableObject {
  typealias M = ChromeMatchRevealMotion
  struct Sample {
    var frame: M.Frame
    var gazeX = 0.0
    var gazeY = 0.0
    var gazeK = 0.0
  }
  @Published var touching = false
  @Published var running = false
  private var date: Date?
  private var now = 0.0
  private var t = 0.0
  private var last = -10.0
  private var a0 = -100.0
  private var b0 = -100.0
  private var met = false
  private var gazeX = 0.0
  private var gazeY = 0.0
  private var gazeK = 0.0
  private var wake: DispatchWorkItem?
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let success = UINotificationFeedbackGenerator()

  func active(_ value: Bool, _ reduced: Bool) {
    date = nil
    if value && !reduced && players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
      for name in ["slide", "clink", "part"] {
        if let url = Bundle.main.url(forResource: "ChromeMatchReveal-cmr-\(name)", withExtension: "wav"),
           let player = try? AVAudioPlayer(contentsOf: url) {
          player.volume = name == "slide" ? 0.55 : 0.75
          player.prepareToPlay()
          players[name] = player
        }
      }
    }
    if !value { players.values.forEach { $0.stop() }; wake?.cancel() }
  }
  func cue(_ name: String, _ reduced: Bool) {
    guard !reduced, let player = players[name] else { return }
    player.currentTime = 0
    player.play()
  }
  func message() { medium.impactOccurred() }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Sample {
    if let pinned {
      var f = M.at(pinned)
      if reduced { f.squash = 0; f.look = 0 }
      return Sample(frame: f)
    }
    // The recording clock follows elapsed wall time, including slow GPU-less frames.
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.5)
    date = at
    now += dt
    let before = t
    var a = 10.0
    var b = -1.0
    let autoPlaying = auto && !touching && now - last >= 2 && a0 < -50
    if autoPlaying {
      t = (t + dt).truncatingRemainder(dividingBy: M.loop)
      a = t
      b = t >= 6.6 ? t - 6.6 : -1
    } else {
      a = a0 > -50 ? now - a0 : 10
      b = b0 > -50 ? now - b0 : -1
      if b0 > -50 && b > 1.2 { a0 = now; b0 = -100 }
      if a0 > -50 && a > 3 && b0 < -50 { a0 = -100 }
    }
    var f = M.channels(a, b)
    if reduced { f.squash = 0; f.look = 0 }
    if f.glint >= 0 && f.glint < 0.1 && !met {
      met = true
      if !(auto && a0 < -50) { cue("clink", reduced); success.notificationOccurred(.success) }
    } else if f.glint < 0 { met = false }
    if DemoSound.inAutoPlay && autoPlaying {
      if before < 0.95 && t >= 0.95 { cue("clink", reduced) }
      if before < 6.6 && t >= 6.6 { cue("part", reduced) }
    }
    gazeK += ((touching ? 1.0 : 0.0) - gazeK) * (1 - exp(-min(dt, 0.05) * 8))
    if !auto && !touching && b0 < -50 && a0 < -50 && gazeK < 0.0001 && running {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return Sample(frame: f, gazeX: gazeX, gazeY: gazeY, gazeK: gazeK)
  }
  func look(_ point: CGPoint) {
    wake?.cancel()
    if !touching { date = nil; touching = true }
    gazeX = point.x; gazeY = point.y
    last = now
    running = true
  }
  func release() {
    touching = false
    last = now
    running = true
  }
  func dislike(_ reduced: Bool) {
    last = now
    guard b0 < -50 else { return }
    if a0 < -50 { a0 = now - 10 }
    b0 = now
    cue("part", reduced)
    light.impactOccurred()
    date = nil
    running = true
  }
}
