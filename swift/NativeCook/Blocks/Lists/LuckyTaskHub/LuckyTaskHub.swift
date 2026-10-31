import AVFoundation
import SwiftUI
import UIKit

struct LuckyTaskHub: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = LuckyTaskHubClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "luckyHubTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let frame = clock.sample(
          timeline.date, auto: autoPlay && !reduced, pinned: pinned, reduced: reduced)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          LuckyTaskHubArt.drawScene(context, size, frame)
        }
        .overlay {
          LuckyTaskHubTouchView { point, state in
            guard pinned == nil else { return }
            clock.touch(point, state: state, width: proxy.size.width, reduced: reduced)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Rewards. Ranking: Bronze. Lucky Flip: 26 of 30 left. Lucky Spin. Newbie tasks: 3 of 14."
        )
        .accessibilityHint("Spins the lucky wheel.")
        .accessibilityAction { clock.accessibilitySpin() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .background(Color.white)
    .ignoresSafeArea()
    .preferredColorScheme(.light)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: reduced) { _, _ in clock.reset() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
  }
}

// TimelineView owns sampling. Only gesture entry/reset publishes, so no whole-screen state writes
// are needed for each animation frame. The clock freezes with the application's scene lifecycle.
final class LuckyTaskHubClock: ObservableObject {
  typealias M = LuckyTaskHubMotion
  @Published private var revision = 0
  private var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var zone = 0
  private var start = CGPoint.zero
  private var lastAngle = 0.0
  private var lastTime = 0.0
  private var billsSeen = -10.0
  private var landedSeen = 0
  private let audio = LuckyTaskHubAudio()

  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
    billsSeen = -10
    landedSeen = 0
    revision += 1
  }
  func sample(_ at: Date, auto: Bool, pinned: Double?, reduced: Bool) -> M.Frame {
    if let pinned { return M.frameAt(pinned) }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    let before = driver.t
    M.stepDriver(&driver, dt, now, auto)
    // The site's recording plays the sounds a touch would make (DemoSound): the wheel's
    // spin, the first bill landing, the card flip and the trophy hop.
    if DemoSound.inAutoPlay && auto && driver.mode == 0 {
      func crossed(_ at: Double) -> Bool { before < at && driver.t >= at }
      if crossed(M.T.spin[0]) { audio.play("spin", enabled: !reduced) }
      if crossed(M.T.bills + 0.89) { audio.play("cash", enabled: !reduced) }
      if crossed(M.T.flip) { audio.play("flip", enabled: !reduced) }
      if crossed(M.T.hop) { audio.play("flip", enabled: !reduced) }
    }
    if driver.mode == 2 && driver.billsAt > 0 {
      if billsSeen != driver.billsAt {
        billsSeen = driver.billsAt
        landedSeen = 0
      }
      let n = Int(floor(M.landed(now - driver.billsAt) + 0.5))
      if n > landedSeen {
        if landedSeen == 0 { audio.play("cash", enabled: !reduced) }
        landedSeen = n
        buzz(n == 5 ? 3 : 2)
      }
    }
    return M.frameOf(driver, now)
  }
  private func buzz(_ kind: Int) {
    if kind == 2 {
      UISelectionFeedbackGenerator().selectionChanged()
    } else if kind == 3 {
      UINotificationFeedbackGenerator().notificationOccurred(.success)
    } else {
      UIImpactFeedbackGenerator(style: kind == 1 ? .medium : .light).impactOccurred()
    }
  }
  private func rect(
    _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ p: CGPoint, pad: Double = 0
  ) -> Bool {
    p.x > x - pad && p.x < x + w + pad && p.y > y - pad && p.y < y + h + pad
  }
  private func wheelAngle(_ p: CGPoint) -> Double { atan2(p.y - 398, p.x - 626) * 180 / Double.pi }
  private func kick(_ reduced: Bool) {
    if M.kickWheel(&driver) {
      audio.play("spin", enabled: !reduced)
      buzz(0)
    }
  }
  func accessibilitySpin() {
    M.takeOver(&driver, now)
    M.kickWheel(&driver)
    revision += 1
  }
  func touch(_ raw: CGPoint, state: UIGestureRecognizer.State, width: Double, reduced: Bool) {
    let scale = width / 402
    let p = CGPoint(x: raw.x / scale / (402.0 / 720), y: (raw.y / scale - 96) / (402.0 / 720))
    switch state {
    case .began:
      M.takeOver(&driver, now)
      start = p
      driver.press = -1
      let goes: [(Double, Double, Double, Double)] = [
        (66, 388, 86, 36), (392, 180, 86, 36), (392, 387, 86, 36),
      ]
      for (i, g) in goes.enumerated() where rect(g.0, g.1, g.2, g.3, p, pad: 8) {
        driver.press = Double(i)
        driver.pressLast = Double(i)
        driver.pressAt = now
      }
      if driver.press < 0 && hypot(p.x - 626, p.y - 398) < 88
        && rect(368, 258, 320, 192, p, pad: 10)
      {
        zone = 1
        if !reduced && driver.billsAt < 0 {
          driver.dragging = 1
          driver.spinning = 0
          driver.wheelV = 0
          lastAngle = wheelAngle(p)
          lastTime = now
        }
      } else if rect(368, 258, 320, 192, p) {
        zone = 2
      } else if rect(368, 50, 320, 193, p) {
        zone = 3
      } else if rect(40, 50, 310, 395, p) {
        zone = 4
      } else if rect(624, 1060, 96, 96, p, pad: 10) {
        zone = 5
      } else {
        zone = 0
      }
      revision += 1
    case .changed:
      guard zone == 1 && driver.dragging == 1 else { return }
      driver.last = now
      let angle = wheelAngle(p)
      var delta = angle - lastAngle
      if delta > 180 { delta -= 360 }
      if delta < -180 { delta += 360 }
      let dt = max(1.0 / 240, now - lastTime)
      driver.wheel += delta
      driver.wheelV = driver.wheelV * 0.5 + (delta / dt) * 0.5
      lastAngle = angle
      lastTime = now
    case .ended, .cancelled, .failed:
      driver.last = now
      if driver.press >= 0 {
        driver.press = -1
        driver.releaseAt = now
      }
      let tap = hypot(p.x - start.x, p.y - start.y) < 16
      if zone == 1 && driver.dragging == 1 {
        driver.dragging = 0
        if tap {
          kick(reduced)
        } else {
          driver.spinning = 1
          driver.spun = 0
          driver.wheelV = max(-2400, min(2400, driver.wheelV))
          if abs(driver.wheelV) > 300 { audio.play("spin", enabled: !reduced) }
        }
        return
      }
      guard tap else { return }
      if zone == 1 || zone == 2 {
        if reduced {
          driver.balance += 500
          driver.balFrom = driver.balance
          driver.progTo = min(14, driver.progTo + 1)
          driver.progFrom = driver.progTo
          driver.progAt = now - 1
          audio.play("cash", enabled: false)
          buzz(3)
        } else {
          kick(reduced)
        }
      } else if zone == 3 {
        if M.flipCard(&driver, now) {
          if reduced { driver.flipAt = now - 1 }
          audio.play("flip", enabled: !reduced)
          buzz(0)
        }
      } else if zone == 4 {
        if M.hopTrophy(&driver, now) {
          if reduced { driver.hopAt = now - 2 }
          audio.play("flip", enabled: !reduced)
          buzz(1)
        }
      } else if zone == 5 && !reduced {
        driver.chestAt = now
        buzz(0)
      }
    default: break
    }
  }
}

final class LuckyTaskHubAudio {
  private var players: [String: AVAudioPlayer] = [:]
  func play(_ cue: String, enabled: Bool) {
    guard enabled else { return }
    if players[cue] == nil {
      guard let url = Bundle.main.url(forResource: "LuckyTaskHub-ltk-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      else { return }
      try? AVAudioSession.sharedInstance().setCategory(
        .ambient, mode: .default, options: .mixWithOthers)
      player.volume = cue == "spin" ? 0.6 : 0.85
      player.prepareToPlay()
      players[cue] = player
    }
    players[cue]?.currentTime = 0
    players[cue]?.play()
  }
}

struct LuckyTaskHubTouchView: UIViewRepresentable {
  var onTouch: (CGPoint, UIGestureRecognizer.State) -> Void
  func makeUIView(context: Context) -> LuckyTaskHubTouchSurface {
    let view = LuckyTaskHubTouchSurface()
    view.onTouch = onTouch
    return view
  }
  func updateUIView(_ view: LuckyTaskHubTouchSurface, context: Context) { view.onTouch = onTouch }
}
final class LuckyTaskHubTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onTouch: ((CGPoint, UIGestureRecognizer.State) -> Void)?
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    gesture.minimumPressDuration = 0
    gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    onTouch?(gesture.location(in: self), gesture.state)
  }
}
