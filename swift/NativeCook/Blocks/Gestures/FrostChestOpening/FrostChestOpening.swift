import SwiftUI
import UIKit

struct FrostChestOpening: View {
  static let loop = 9.6
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = FrostChestOpeningClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "frostChestTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }

  var body: some View {
    GeometryReader { safe in
      GeometryReader { proxy in
        let top = safe.safeAreaInsets.top
        TimelineView(
          .animation(
            paused: pinned != nil || reducedMotion || scenePhase != .active
              || (!autoPlay && !clock.running))
        ) { timeline in
          let f = clock.sample(
            timeline.date, auto: autoPlay, reduced: reducedMotion, pinned: pinned)
          Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
            context, size in
            FrostChestOpeningArt(size: size, top: top).drawScene(context, f)
          }
          .background(Color(.sRGB, red: 195 / 255, green: 215 / 255, blue: 246 / 255))
          .contentShape(Rectangle())
          .gesture(
            DragGesture(minimumDistance: 0)
              .onChanged { value in
                guard pinned == nil, !reducedMotion else { return }
                clock.begin(value.startLocation, size: proxy.size)
              }
              .onEnded { value in
                guard pinned == nil, !reducedMotion else { return }
                clock.end(value.translation)
              }
          )
          .accessibilityElement(children: .ignore)
          .accessibilityAddTraits(.isButton)
          .accessibilityLabel("A big surprise awaits. Rare chest with one rare drop.")
          .accessibilityHint(
            "Hold to open the chest. Then claim the Winter Special Costume and other rewards."
          )
          .accessibilityAction { if !reducedMotion { clock.accessibilityOpen() } }
        }
        .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        .onChange(of: scenePhase) { _, _ in clock.resetDate() }
        .onChange(of: autoPlay) { _, _ in clock.reset() }
        .onChange(of: reducedMotion) { _, _ in clock.reset() }
      }
      .ignoresSafeArea()
    }
    .preferredColorScheme(.light)
  }
}

// The same five-mode touch driver as fco-motion.ts. Only gestures / mode transitions notify
// SwiftUI; TimelineView samples the non-published timeline without writing view state each frame.
final class FrostChestOpeningClock: ObservableObject {
  typealias M = FrostChestOpeningMotion
  @Published var running = false
  struct Driver {
    var mode = 0
    var t = 0.0
    var stop = M.LOOP
    var last = -100.0
  }
  var driver = Driver()
  private var date: Date?
  private var now = 0.0
  private var holding = false
  private var claiming = false
  private var finger = false
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let success = UINotificationFeedbackGenerator()

  func resetDate() { date = nil }
  func reset() {
    driver = Driver()
    date = nil
    now = 0
    running = false
  }
  func stepDriver(_ dt: Double, _ now: Double, _ auto: Bool) -> Double {
    let step = min(dt, 0.05)
    switch driver.mode {
    case 0: driver.t = auto ? (driver.t + step).truncatingRemainder(dividingBy: M.LOOP) : 0
    case 1:
      driver.t = max(driver.t, M.T.press) + step
      if driver.t >= M.T.fillEnd + 0.08 {
        driver.mode = 3
        driver.stop = M.T.settled
      }
    case 2:
      driver.t -= step * M.DRAIN
      if driver.t <= M.T.press - 0.01 {
        driver.t = 0
        driver.mode = 4
        driver.last = now
      }
    case 3:
      let next = driver.t + step
      if driver.stop >= M.LOOP && next >= M.LOOP {
        driver.t = 0
        driver.mode = 4
        driver.last = now
      } else if driver.stop < M.LOOP && next >= driver.stop {
        driver.t = driver.stop
        driver.mode = 4
        driver.last = now
      } else {
        driver.t = next
      }
    case 4:
      if auto && now - driver.last > M.RESUME { driver.mode = 0 }
    default: break
    }
    return driver.t
  }
  func holdBegin(_ now: Double) -> Bool {
    if driver.t < M.T.flyStart && [0, 2, 4].contains(driver.mode) {
      if driver.t > M.T.fillEnd { return false }
      if driver.mode == 0 && driver.t > M.T.press {
        // Preserve the charge when the finger takes over autoPlay.
      } else if driver.t < M.T.press {
        driver.t = M.T.press - 0.02
      }
      driver.mode = 1
      driver.last = now
      return true
    }
    return false
  }
  func holdEnd(_ now: Double) {
    if driver.mode == 1 {
      driver.mode = 2
      driver.last = now
    }
  }
  func canClaim() -> Bool { driver.t >= M.T.claim + 0.25 && driver.t < M.T.claimPress[0] }
  @discardableResult func claimTap(_ now: Double) -> Bool {
    guard canClaim() else { return false }
    driver.t = M.T.claimPress[0]
    driver.mode = 3
    driver.stop = M.LOOP
    driver.last = now
    return true
  }
  func touched(_ now: Double) {
    driver.last = now
    if driver.mode == 0 {
      if driver.t >= M.T.flyStart && driver.t < M.T.settled {
        driver.mode = 3
        driver.stop = M.T.settled
      } else if driver.t >= M.T.settled && driver.t < M.T.claimPress[0] {
        driver.mode = 4
      }
    }
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
    if let pinned { return M.frameAt(pinned, pinned.truncatingRemainder(dividingBy: M.LOOP)) }
    if reduced { return M.frameAt(M.T.settled, M.T.settled) }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0), 0.05)
    date = at
    now += dt
    let before = driver.t
    let t = stepDriver(dt, now, auto)
    if driver.mode != 0 {
      if before < M.T.press + 0.02 && t >= M.T.press + 0.02 && driver.mode == 1 {
        light.impactOccurred()
      }
      for q in [0.25, 0.5, 0.75] {
        let threshold = M.T.fillKnee + (M.T.fillEnd - M.T.fillKnee) * q
        if before < threshold && t >= threshold && driver.mode == 1 { light.impactOccurred() }
      }
      if before < M.T.impact && t >= M.T.impact { success.notificationOccurred(.success) }
      if before < 3.68 && t >= 3.68 { light.impactOccurred() }
    }
    if !auto && running && (driver.mode == 4 || driver.mode == 0) {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return M.frameAt(t, (auto ? now : t).truncatingRemainder(dividingBy: M.LOOP))
  }
  func begin(_ point: CGPoint, size: CGSize) {
    guard !finger else { return }
    finger = true
    let sx = size.width / 402
    let sy = size.height / 874
    let pill = CGRect(
      x: 92 * sx - 16, y: 583.7 * sy - 30, width: 219 * sx + 32, height: (774.5 - 583.7) * sy + 46)
    let claim = CGRect(x: 13 * sx, y: 759 * sy - 6, width: 376 * sx, height: 69 * sy + 12)
    touched(now)
    holding = pill.contains(point) && holdBegin(now)
    claiming = claim.contains(point) && canClaim()
    date = nil
    running = true
    light.prepare()
    success.prepare()
  }
  func end(_ translation: CGSize) {
    if holding { holdEnd(now) }
    if claiming && hypot(translation.width, translation.height) < 12 && claimTap(now) {
      light.impactOccurred()
    }
    holding = false
    claiming = false
    finger = false
    driver.last = now
  }
  func accessibilityOpen() {
    if !claimTap(now) {
      driver.t = M.T.fillEnd
      driver.mode = 3
      driver.stop = M.T.settled
      driver.last = now
    }
    date = nil
    running = true
  }
}
