import AVFoundation
import SwiftUI
import UIKit

final class CoinCardOnboardingClock: ObservableObject {
  typealias M = CoinCardOnboardingMotion
  @Published var running = false
  private var pager = M.Pager()
  private var time = 0.0, now = 0.0, last = -10.0
  private var date: Date?
  private var idle: DispatchWorkItem?
  private var idleAt: Date?
  private var idleAutoplay = false
  private var players: [String: AVAudioPlayer] = [:]
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private var pinnedTime: Double?
  private var pinnedPage = 0.0
  private var configured = false
  private var active = true
  private var auto = false
  private var reduced = false
  // Same recording switch as Shared/DemoSound; this port also typechecks with older Shared files.
  private var recordingSound: Bool {
    !(UserDefaults.standard.string(forKey: "recordingSound") ?? "").isEmpty
  }

  func configure(_ autoPlay: Bool, _ reduced: Bool, _ active: Bool, _ pinned: Double?) {
    finishIdle()
    configured = true
    self.auto = autoPlay && !reduced
    self.reduced = reduced
    self.active = active
    if pinnedTime != pinned {
      pinnedTime = pinned
      if let pinned { pinnedPage = M.at(pinned).p }
    }
    date = nil
    if !reduced && active && pinned == nil && players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
      for cue in ["flip", "page", "tap"] {
        if let url = Bundle.main.url(forResource: "CoinCardOnboarding-cco-\(cue)", withExtension: "wav"),
           let player = try? AVAudioPlayer(contentsOf: url) {
          player.volume = cue == "tap" ? 0.5 : 0.7
          player.prepareToPlay()
          players[cue] = player
        }
      }
    }
    if reduced || !active { for p in players.values { p.stop() } }
    running = active && pinned == nil && (auto || pager.drag || abs(pager.p - pager.target) > 1e-9 || abs(pager.v) > 1e-9)
  }
  private func cue(_ name: String) {
    guard !reduced, active, pinnedTime == nil, let player = players[name] else { return }
    player.currentTime = 0
    player.play()
  }
  func buzz(_ kind: Int) {
    if kind == 0 { selection.selectionChanged() } else { light.impactOccurred() }
  }
  private func finishIdle() {
    if let idleAt {
      let elapsed = max(0, Date().timeIntervalSince(idleAt))
      now += elapsed
      if idleAutoplay { advanceScript(elapsed) }
    }
    idle?.cancel(); idle = nil; idleAt = nil
  }
  private func sleepUntilChange(_ delay: Double, _ advance: Bool, _ at: Date) {
    guard idle == nil else { return }
    // Only the scalar driver is mutable per frame. A rest timer suspends TimelineView.
    idleAt = at
    idleAutoplay = advance
    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.finishIdle()
      self.date = Date()
      self.running = self.active && self.pinnedTime == nil
    }
    idle = work
    DispatchQueue.main.async { [weak self] in
      guard let self, self.idle != nil else { return }
      self.running = false
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
  }
  private func advanceScript(_ elapsed: Double) {
    // Resolve script boundaries even when a recording frame takes half a second.
    var remaining = elapsed
    while remaining > 0 {
      let dt = min(1.0 / 120, remaining)
      time = (time + dt).truncatingRemainder(dividingBy: M.loop)
      let target = M.script(time)
      if target != pager.target && recordingSound { cue("page") }
      pager.target = target
      let before = floor(pager.p + 0.5)
      pager.spring(dt, 60)
      if floor(pager.p + 0.5) != before {
        if recordingSound { cue("flip") }
        buzz(0)
      }
      remaining -= dt
    }
  }
  func sample(_ at: Date) -> Double {
    if !configured { return 0 }
    if pinnedTime != nil { return pinnedPage }
    if !active || idle != nil { return pager.p }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.5)
    date = at
    now += dt
    let before = floor(pager.p + 0.5)
    if pager.drag {
      pager.v = dt > 0 ? pager.v * 0.6 + ((pager.finger - pager.p) / dt) * 0.4 : 0
      pager.p = pager.finger
    } else if auto && now - last >= 2 {
      advanceScript(dt)
    } else { pager.spring(dt, reduced ? 300 : 150) }
    if !(auto && now - last >= 2) && floor(pager.p + 0.5) != before {
      cue("flip"); buzz(0)
    }
    if !pager.drag && abs(pager.p - pager.target) < 1e-9 && abs(pager.v) < 1e-9 {
      if auto && now - last >= 2 {
        let next = time < 1.6 ? 1.6 : time < 4.2 ? 4.2 : time < 6.8 ? 6.8 : M.loop + 1.6
        sleepUntilChange(next - time, true, at)
      } else if auto {
        sleepUntilChange(max(0.001, 2 - (now - last)), false, at)
      } else {
        DispatchQueue.main.async { [weak self] in
          guard let self, !self.auto, !self.pager.drag, abs(self.pager.p - self.pager.target) < 1e-9, abs(self.pager.v) < 1e-9 else { return }
          self.running = false
        }
      }
    }
    return pager.p
  }
  private func wake() {
    let wasPaused = !running || idle != nil
    finishIdle()
    if wasPaused { date = Date() }
    if !running { running = true }
  }
  func begin() {
    guard pinnedTime == nil else { return }
    wake(); last = now
  }
  func drag(_ translation: Double, _ width: Double, _ starting: Bool) {
    guard pinnedTime == nil else { return }
    wake()
    if starting { pager.drag = true; pager.start = pager.p; pager.finger = pager.p }
    last = now
    var f = pager.start - translation / (width * 0.8)
    if f < 0 { f = -0.3 * tanh(-f / 0.3) }
    if f > 2 { f = 2 + 0.3 * tanh((f - 2) / 0.3) }
    pager.finger = f
  }
  func end(_ velocity: Double, _ width: Double) {
    guard pinnedTime == nil else { return }
    wake(); last = now
    if pager.drag {
      pager.drag = false
      pager.v = -velocity / (width * 0.8)
      pager.target = min(2, max(0, floor(pager.p + pager.v * 0.2 + 0.5)))
      cue("page")
    }
  }
  func goTo(_ n: Int) {
    guard pinnedTime == nil else { return }
    begin()
    pager.target = Double(min(2, max(0, n)))
    buzz(1); cue("page")
  }
  deinit { idle?.cancel() }
}

struct CoinCardOnboardingTouchView: UIViewRepresentable {
  var clock: CoinCardOnboardingClock
  func makeUIView(context: Context) -> CoinCardOnboardingTouchSurface {
    let v = CoinCardOnboardingTouchSurface(); v.clock = clock; return v
  }
  func updateUIView(_ view: CoinCardOnboardingTouchSurface, context: Context) { view.clock = clock }
}

final class CoinCardOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
  weak var clock: CoinCardOnboardingClock?
  private var dragging = false
  private var start = CGPoint.zero
  private var velocity = 0.0
  private let velocityPan = UIPanGestureRecognizer()
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false; backgroundColor = .clear; isAccessibilityElement = false
    let pan = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    pan.minimumPressDuration = 0
    pan.allowableMovement = .greatestFiniteMagnitude
    pan.delegate = self
    addGestureRecognizer(pan)
    // RNGH's iOS Pan also uses UIPanGestureRecognizer's native velocity estimator.
    velocityPan.addTarget(self, action: #selector(trackVelocity(_:)))
    velocityPan.delegate = self
    velocityPan.cancelsTouchesInView = false
    addGestureRecognizer(velocityPan)
  }
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(_ gesture: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
  @objc private func trackVelocity(_ gesture: UIPanGestureRecognizer) {
    velocity = gesture.velocity(in: self).x
  }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    let point = gesture.location(in: self)
    if gesture.state == .began {
      start = point; velocity = 0; dragging = false
      clock?.begin()
    } else if gesture.state == .changed {
      let dx = point.x - start.x
      if dragging || abs(dx) > 8 {
        clock?.drag(dx, bounds.width, !dragging)
        dragging = true
      }
    } else if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed {
      let state = velocityPan.state
      let vx = state == .began || state == .changed || state == .ended ? velocityPan.velocity(in: self).x : velocity
      clock?.end(vx, bounds.width)
      dragging = false
    }
  }
}
