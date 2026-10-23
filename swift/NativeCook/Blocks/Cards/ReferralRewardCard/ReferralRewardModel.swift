import Observation
import QuartzCore
import SwiftUI
import UIKit

@MainActor @Observable final class ReferralRewardModel {
  typealias Motion = ReferralRewardAnimation
  let railClock = ReferralRewardInvalidation()
  let pointsClock = ReferralRewardInvalidation()
  let marksClock = ReferralRewardInvalidation()
  let footerClock = ReferralRewardInvalidation()
  let flightClock = ReferralRewardInvalidation()
  let arrivalClock = ReferralRewardInvalidation()
  var joined = [true, true, false, false, false, false, false]
  var focus = 2
  var shownTier = 0
  var peekOpen = false
  var capsuleIndex = 2
  var capsuleJoined = false
  private(set) var capsuleDescription = ""
  private(set) var capsuleActionLabel = ""
  private(set) var capsuleCopyWidth = 144.0
  private(set) var capsuleCopyHeight = 46.0
  private(set) var capsuleStatusHeight = 20.0
  private(set) var friendValue = ReferralRewardL10n.format("friendUnjoined", "Otto", 3)
  private(set) var pointsLabel = ReferralRewardL10n.format("points", 50)
  private(set) var rewardsLabel = ReferralRewardL10n.format("rewards", 0)
  var reduced = false
  var opaque = false
  var layout = ReferralRewardLayout(width: 370, fontScale: 1)
  var count: Int { joined.reduce(0) { $0 + ($1 ? 1 : 0) } }
  var done: Bool { count == 7 }
  @ObservationIgnored let scroll = ReferralRewardValue(2)
  @ObservationIgnored let peek = ReferralRewardValue(0)
  @ObservationIgnored let actionPress = ReferralRewardValue(0)
  @ObservationIgnored let capsuleShape = ReferralRewardValue(0)
  @ObservationIgnored let capsuleLabel = ReferralRewardValue(0)
  @ObservationIgnored var capsuleLaunching = -1.0
  @ObservationIgnored let enter = ReferralRewardValue(0)
  @ObservationIgnored let pop = ReferralRewardValue(0)
  @ObservationIgnored let kick = ReferralRewardValue(0)
  @ObservationIgnored let wave = ReferralRewardValue(1)
  @ObservationIgnored let crest = ReferralRewardValue(1)
  @ObservationIgnored let ripple = ReferralRewardValue(1)
  @ObservationIgnored let glow = ReferralRewardValue(0)
  @ObservationIgnored let flash = ReferralRewardValue(0)
  @ObservationIgnored let arcStart = ReferralRewardValue(0)
  @ObservationIgnored let arcEnd = ReferralRewardValue(2.0 / 3)
  @ObservationIgnored let lock = ReferralRewardValue(0)
  @ObservationIgnored let follower = ReferralRewardValue(2)
  @ObservationIgnored let shape = ReferralRewardValue(0)
  @ObservationIgnored let label = ReferralRewardValue(0)
  @ObservationIgnored let press = ReferralRewardValue(0)
  @ObservationIgnored let swap = ReferralRewardValue(0)
  @ObservationIgnored let comet = ReferralRewardValue(1.2)
  @ObservationIgnored let flight = ReferralRewardValue(1.2)
  @ObservationIgnored let level = ReferralRewardValue(0)
  @ObservationIgnored let bump = ReferralRewardValue(0)
  @ObservationIgnored let columns = ReferralRewardValue(2)
  @ObservationIgnored let turns = [
    ReferralRewardValue(0), ReferralRewardValue(5), ReferralRewardValue(0),
  ]
  @ObservationIgnored let shown = [
    ReferralRewardValue(1), ReferralRewardValue(1), ReferralRewardValue(0),
  ]
  @ObservationIgnored let marks = (0..<7).map { ReferralRewardValue($0 < 2 ? 1 : 0) }
  @ObservationIgnored var targets = [0.0, 5.0, 0.0]
  @ObservationIgnored var digits: [Int?] = [0, 5, nil]
  @ObservationIgnored var path: ReferralRewardPath?
  @ObservationIgnored var basePath: ReferralRewardPath?
  @ObservationIgnored var fromCapsule = false
  @ObservationIgnored var rewardIndex = 0
  @ObservationIgnored var phase = 0.0
  @ObservationIgnored var phaseStart = 0.0
  @ObservationIgnored weak var surface: ReferralRewardSurfaceView?
  @ObservationIgnored private var link: CADisplayLink?
  @ObservationIgnored private var proxy: ReferralRewardLinkProxy?
  @ObservationIgnored private var live = false
  @ObservationIgnored private var started = 0.0
  @ObservationIgnored private var last = 0.0
  @ObservationIgnored private var smoothFrames = 0
  @ObservationIgnored private var nextBeat = Double.infinity
  @ObservationIgnored private var touched = false
  @ObservationIgnored private var autoPlay = true
  @ObservationIgnored private var caught = false
  @ObservationIgnored private var rewarded = false
  @ObservationIgnored private var catchArc = 2.0 / 3
  @ObservationIgnored var hand = false
  @ObservationIgnored var down = false
  @ObservationIgnored private var wasRest = true
  @ObservationIgnored private var lastScroll = 2.0
  @ObservationIgnored private var followerFrom = 2.0
  @ObservationIgnored private var followerTo = 2.0
  @ObservationIgnored private var followerTime = 0.0
  @ObservationIgnored private var dragOrigin = 2.0
  @ObservationIgnored private var dragFrom = 2.0
  @ObservationIgnored private var dragged = false
  @ObservationIgnored private var verticalMoved = false
  @ObservationIgnored private var canPeek = false
  @ObservationIgnored private let selection = UISelectionFeedbackGenerator()
  @ObservationIgnored private let impact = UIImpactFeedbackGenerator(style: .light)
  @ObservationIgnored private let success = UINotificationFeedbackGenerator()
  // Each drawing owns an invalidation clock. The glass can turn indefinitely
  // without asking SwiftUI to rebuild resting portraits, digits or ribbons.
  @ObservationIgnored private lazy var railValues =
    [
      scroll, peek, actionPress, capsuleShape, capsuleLabel, pop, kick, wave, crest, ripple, glow,
      flash, arcStart, arcEnd, lock, follower,
    ] + marks
  @ObservationIgnored private lazy var pointValues = turns + shown + [columns, bump]
  @ObservationIgnored private lazy var footerValues = [shape, label, press, swap]

  var arrival: Double { ReferralRewardMath.out((enter.value - 80) / 380) }
  var loose: Double { ReferralRewardMath.smooth(abs(scroll.value - floor(scroll.value + 0.5)) * 2) }
  var rush: Double {
    reduced
      ? 0
      : max(abs(scroll.value - follower.value) / 0.09 - 1.5, 0)
        + (!live ? 13 * max(1 - (ReferralRewardUIRuntime.shared.now() - started) / 320, 0) : 0)
  }
  var tint: Color { Color(referralReward: ReferralRewardFriend.blend(scroll.value)) }
  var capsuleStatus: String {
    ReferralRewardL10n.t(
      capsuleJoined ? "joined" : layout.width < 320 ? "shortUnjoined" : "unjoined")
  }
  var capsuleShift: Double { (layout.width / 2 - 66) * (1 - peek.value) }

  func configure(_ layout: ReferralRewardLayout) {
    guard self.layout != layout || basePath == nil else { return }
    self.layout = layout
    measureCapsule()
    basePath = ReferralRewardPath(origin: layout.origin, centre: layout.centre)
    railClock.frame &+= 1
    footerClock.frame &+= 1
    surface?.render()
  }
  func start(autoPlay: Bool) {
    guard link == nil else { return }
    self.autoPlay = autoPlay
    started = CACurrentMediaTime() * 1000
    last = started
    phaseStart = started
    let proxy = ReferralRewardLinkProxy(self)
    self.proxy = proxy
    let link = CADisplayLink(target: proxy, selector: #selector(ReferralRewardLinkProxy.tick(_:)))
    link.preferredFrameRateRange = .init(minimum: 60, maximum: 120, preferred: 120)
    link.add(to: .main, forMode: .common)
    self.link = link
    selection.prepare()
    impact.prepare()
    success.prepare()
  }
  func stop() {
    link?.invalidate()
    link = nil
    proxy = nil
    nextBeat = .infinity
  }
  func touch() {
    touched = true
    nextBeat = .infinity
  }
  func preferences(reduced: Bool, opaque: Bool) {
    if self.reduced != reduced {
      self.reduced = reduced
      if !reduced { phaseStart = CACurrentMediaTime() * 1000 }
      if reduced {
        // Cancel travelling effects, preserving every committed join.
        resetEffects()
        scroll.set(Motion.timing(floor(scroll.value + 0.5), 200))
        arcStart.set(0)
        arcEnd.set(Motion.timing(ReferralRewardMath.arc(count), 240))
        level.set(Motion.timing(Double(ReferralRewardMath.tier(count)), 240))
        updatePoints(count * 25, delay: 0)
        for i in 0..<7 { marks[i].set(Motion.timing(joined[i] ? 1 : 0, 200)) }
      }
    }
    self.opaque = opaque
    surface?.render()
  }
  func tick(_ now: Double) {
    let rt = ReferralRewardUIRuntime.shared
    rt.timestamp = now
    defer {
      last = now
      rt.timestamp = nil
    }
    if !live {
      if reduced || now - started >= 320 {
        smoothFrames = now - last < 34 ? smoothFrames + 1 : 0
        if reduced || smoothFrames >= 6 || now - started > 1520 {
          live = true
          phaseStart = now
          enter.set(Motion.timing(540, reduced ? 200 : 540, linear: !reduced))
          if autoPlay && !touched { nextBeat = now + 1400 }
        }
      }
    }
    if live && !reduced { phase = ReferralRewardMath.wrap((now - phaseStart) / 14800, 1) }
    let arrivalChanged = enter.tick(now)
    // Advance the old follower before retargeting it to this frame's row.
    // This is the same 90 ms trailing timing used by the reference blur.
    if !reduced {
      follower.set(
        followerFrom + (followerTo - followerFrom)
          * ReferralRewardMath.clamp((now - followerTime) / 90))
    }
    var railChanged = false
    var pointChanged = false
    var footerChanged = false
    for v in railValues { if v.tick(now) { railChanged = true } }
    for v in pointValues { if v.tick(now) { pointChanged = true } }
    for v in footerValues { if v.tick(now) { footerChanged = true } }
    let levelChanged = level.tick(now)
    let cometChanged = comet.tick(now)
    let flightChanged = flight.tick(now)
    if scroll.value != lastScroll {
      if !reduced {
        followerFrom = follower.value
        followerTo = scroll.value
        followerTime = now
      }
      lastScroll = scroll.value
    }
    let who = ReferralRewardMath.index(scroll.value)
    if who != focus {
      focus = who
      updateAccessibility()
      if hand { selection.selectionChanged() }
    }
    let rest = !down && abs(scroll.value - floor(scroll.value + 0.5)) < 0.05
    if rest && !wasRest && hand && !reduced {
      lock.set(Motion.sequence(Motion.timing(1, 90), Motion.spring(0, 420, 0.55)))
    }
    wasRest = rest
    if let path, !caught, comet.value >= path.launch / path.total, comet.value < 1.2 {
      caught = true
      pop.set(1)
      pop.set(Motion.spring(0, 520, 0.8))
      wave.set(0)
      wave.set(Motion.timing(1, 420))
      kick.set(Motion.sequence(Motion.timing(1, 120), Motion.spring(0, 560, 0.5)))
      glow.set(Motion.sequence(Motion.timing(1, 120), Motion.timing(0, 720)))
      arcEnd.set(Motion.timing(catchArc, path.orbit))
      impact.impactOccurred()
      railChanged = true
    }
    if !rewarded && flash.value > 0.05 {
      rewarded = true
      success.notificationOccurred(.success)
    }
    let tier = Int(level.value.rounded())
    if tier != shownTier {
      shownTier = tier
      rewardsLabel = ReferralRewardL10n.format("rewards", tier)
    }
    if arrivalChanged { arrivalClock.frame &+= 1 }
    if railChanged || arrivalChanged || !live { railClock.frame &+= 1 }
    if pointChanged { pointsClock.frame &+= 1 }
    if levelChanged || pointChanged { marksClock.frame &+= 1 }
    if footerChanged || cometChanged { footerClock.frame &+= 1 }
    if cometChanged || flightChanged || footerChanged { flightClock.frame &+= 1 }
    if !reduced || railChanged || footerChanged || arrivalChanged { surface?.render() }
    if now >= nextBeat {
      let c = count
      let gap: Double =
        c == 7
        ? 2000
        : c + 1 == 7
          ? 3200 : ReferralRewardMath.tier(c + 1) > ReferralRewardMath.tier(c) ? 2800 : 2000
      activate()
      nextBeat = now + gap
    }
  }
  private func resetEffects() {
    pop.set(0)
    kick.set(0)
    glow.set(0)
    flash.set(0)
    bump.set(0)
    wave.set(1)
    crest.set(1)
    ripple.set(1)
    comet.set(1.2)
    flight.set(1.2)
    shape.set(0)
    label.set(0)
    capsuleShape.set(0)
    capsuleLabel.set(0)
    capsuleLaunching = -1
    caught = false
    rewarded = false
  }
  func activate() { if done { startOver() } else { invite() } }
  func invite(selected: Int? = nil) {
    if let selected, joined[selected] { return }
    guard !done, let basePath else { return }
    let count = count
    let at = floor(scroll.value + 0.5)
    var steps = 0
    if let selected {
      steps = Int(ReferralRewardMath.wrap(Double(selected) - at + 3)) - 3
    } else {
      while joined[ReferralRewardMath.index(at + Double(steps))] { steps += 1 }
    }
    let who = ReferralRewardMath.index(at + Double(steps))
    let now = count + 1
    let filled = ReferralRewardMath.tier(now) > ReferralRewardMath.tier(count)
    let capsule = selected != nil
    let route =
      capsule
      ? ReferralRewardPath(
        origin: .init(x: layout.actionX - (reduced ? 0 : capsuleShift), y: layout.lensY),
        centre: layout.centre) : basePath
    let closing = peek.value > 0 ? 540.0 : 0
    if capsule { capsuleLaunching = peek.value }
    joined[who] = true
    updateAccessibility()
    UIAccessibility.post(
      notification: .announcement,
      argument: ReferralRewardL10n.format(
        "joinAnnouncement", ReferralRewardFriend.all[who].name, now * 25))
    changePeek(false)
    hand = false
    swap.set(Motion.timing(now == 7 ? 1 : 0, reduced ? 120 : 240))
    if reduced {
      scroll.set(Motion.timing(at + Double(steps), 200))
      updatePoints(now * 25, delay: 0)
      marks[who].set(Motion.timing(1, 200))
      arcStart.set(0)
      arcEnd.set(Motion.timing(filled && now != 7 ? 0 : ReferralRewardMath.arc(now), 240))
      level.set(Motion.timing(Double(ReferralRewardMath.tier(now)), 240))
      if filled { success.notificationOccurred(.success) } else { impact.impactOccurred() }
      return
    }
    // A single press clock owns collapse, travel and launch. Cancelling the
    // old slots above prevents a delayed catch from surviving an interruption.
    let travel = steps == 0 ? 0 : 300 + Double(steps - 1) * 60
    let launch = max(travel, 140, closing - route.launch)
    let caughtAt = launch + route.launch
    let landed = launch + route.total
    pop.set(0)
    kick.set(0)
    glow.set(0)
    flash.set(0)
    bump.set(0)
    wave.set(1)
    crest.set(1)
    ripple.set(1)
    flight.set(1.2)
    level.set(Double(ReferralRewardMath.tier(count)))
    arcStart.set(0)
    arcEnd.cancel()
    shape.cancel()
    label.cancel()
    capsuleShape.cancel()
    capsuleLabel.cancel()
    fromCapsule = capsule
    path = route
    caught = false
    rewarded = false
    if capsule {
      capsuleShape.set(0)
      capsuleLabel.set(0)
      shape.set(0)
      label.set(0)
    } else {
      capsuleLaunching = -1
    }
    scroll.set(Motion.spring(at + Double(steps), 440, 1))
    comet.set(-launch / route.total)
    comet.set(Motion.timing(1.2, launch + route.total * 1.2, linear: true))
    if capsule {
      capsuleShape.set(Motion.timing(1, 140))
      capsuleLabel.set(Motion.timing(1, 100))
    } else {
      shape.set(
        Motion.sequence(
          Motion.timing(1, 140), Motion.delay(launch + 120 - 140, Motion.spring(0, 420, 0.8))))
      label.set(
        Motion.sequence(
          Motion.timing(1, 100), Motion.delay(launch + 120 - 100, Motion.timing(0, 220))))
    }
    marks[who].set(Motion.delay(caughtAt + 40, Motion.spring(1, 480, 0.6)))
    updatePoints(now * 25, delay: landed - 40)
    ripple.set(
      Motion.delay(
        landed - 30, Motion.sequence(Motion.timing(0, 0), Motion.timing(1, 420, linear: true))))
    bump.set(
      Motion.delay(landed, Motion.sequence(Motion.timing(1, 110), Motion.spring(0, 520, 0.55))))
    if ReferralRewardMath.arc(count) == 0 { arcEnd.set(0) }
    catchArc = filled ? 1 : ReferralRewardMath.arc(now)
    if filled {
      rewardIndex = ReferralRewardMath.tier(now) - 1
      let rewardLaunch = landed + 200
      let arrive = rewardLaunch + 440
      flight.set(
        Motion.delay(
          rewardLaunch, Motion.sequence(Motion.timing(0, 0), Motion.timing(1.2, 528, linear: true)))
      )
      flash.set(Motion.delay(arrive, Motion.sequence(Motion.timing(1, 100), Motion.timing(0, 640))))
      crest.set(Motion.delay(arrive, Motion.sequence(Motion.timing(0, 0), Motion.timing(1, 620))))
      level.set(
        Motion.delay(arrive, Motion.spring(Double(ReferralRewardMath.tier(now)), 560, 0.58)))
      if now != 7 { arcStart.set(Motion.delay(arrive + 260, Motion.timing(1, 520, inOut: true))) }
    }
  }
  func startOver() {
    let at = floor(scroll.value + 0.5)
    let back = ReferralRewardMath.wrap(2 - at + 3) - 3
    joined = [true, true, false, false, false, false, false]
    updateAccessibility()
    changePeek(false)
    resetEffects()
    hand = false
    for i in 0..<7 { marks[i].set(Motion.timing(i < 2 ? 1 : 0, 180)) }
    updatePoints(50, delay: reduced ? 0 : 120)
    selection.selectionChanged()
    arcStart.set(0)
    scroll.set(reduced ? Motion.timing(at + back, 200) : Motion.spring(at + back, 560, 0.9))
    arcEnd.set(Motion.timing(2.0 / 3, reduced ? 240 : 520, inOut: !reduced))
    level.set(Motion.timing(0, reduced ? 240 : 420))
    swap.set(Motion.timing(0, reduced ? 120 : 240))
  }
  private func updatePoints(_ value: Int, delay: Double) {
    let old = (digits[0] ?? 0) + (digits[1] ?? 0) * 10 + (digits[2] ?? 0) * 100
    let rising = value > old
    columns.set(
      Motion.delay(
        delay,
        reduced
          ? Motion.timing(value >= 100 ? 3 : 2, 200)
          : Motion.spring(value >= 100 ? 3 : 2, 460, 0.85)))
    for i in 0..<3 {
      let d: Int? = i == 2 && value < 100 ? nil : (value / Int(pow(10, Double(i)))) % 10
      let from = digits[i]
      guard from != d else { continue }
      digits[i] = d
      turns[i].cancel()
      shown[i].cancel()
      let wait = delay + Double(i) * 45
      shown[i].set(Motion.delay(wait, Motion.timing(d == nil ? 0 : 1, 240)))
      guard let d else { continue }
      if let from {
        targets[i] += rising ? Double((d - from + 10) % 10) : -Double((from - d + 10) % 10)
        turns[i].set(
          Motion.delay(wait, Motion.timing(targets[i], reduced ? 220 : 520, inOut: !reduced)))
      } else {
        targets[i] = Double(d)
        turns[i].set(Double(d))
      }
    }
  }
  private func updateAccessibility() {
    friendValue = ReferralRewardL10n.format(
      joined[focus] ? "friendJoined" : "friendUnjoined", ReferralRewardFriend.all[focus].name,
      focus + 1)
    let nextPointsLabel = ReferralRewardL10n.format("points", count * 25)
    if pointsLabel != nextPointsLabel { pointsLabel = nextPointsLabel }
  }
  private func measureCapsule() {
    let name = ReferralRewardFriend.all[capsuleIndex].name
    capsuleDescription = "\(name). \(capsuleStatus)."
    capsuleActionLabel = "\(ReferralRewardL10n.t("invite")) \(name)"
    capsuleCopyWidth = max(
      layout.width - 48 - 90 - 20 - (capsuleJoined ? 0 : layout.actionWidth + 8), 0)
    capsuleStatusHeight = min(
      40 * layout.fontScale,
      layout.textHeight(
        capsuleStatus, size: 13, weight: .regular, line: 20, width: capsuleCopyWidth))
    capsuleCopyHeight = 2 * ceil((22 * layout.fontScale + 4 + capsuleStatusHeight) / 2)
  }
  func changePeek(_ open: Bool) {
    if open {
      capsuleLaunching = -1
      capsuleIndex = focus
      capsuleJoined = joined[focus]
      measureCapsule()
      selection.selectionChanged()
      UIAccessibility.post(
        notification: .announcement,
        argument: "\(ReferralRewardFriend.all[focus].name). \(capsuleStatus).")
    }
    peekOpen = open
    peek.cancel()
    peek.set(
      reduced
        ? Motion.timing(open ? 1 : 0, 200)
        : Motion.spring(open ? 1 : 0, open ? 520 : 360, open ? 0.78 : 1, clamp: !open))
  }
  func step(_ by: Int) {
    touch()
    changePeek(false)
    hand = true
    settle(floor(scroll.value + 0.5) + Double(by), velocity: 0, flick: false)
  }
  func accessibilityPeek() {
    touch()
    if peekOpen {
      changePeek(false)
    } else if abs(scroll.value - floor(scroll.value + 0.5)) < 0.05 {
      changePeek(true)
    }
  }
  func hitsAction(_ p: CGPoint) -> Bool {
    guard peekOpen, peek.value > 0.5, !joined[focus] else { return false }
    let scale = reduced ? 1 : 0.92 + 0.08 * ReferralRewardMath.clamp((peek.value - 0.5) / 0.4)
    return abs(p.x - (layout.actionX - (reduced ? 0 : capsuleShift))) <= max(
      44, layout.actionWidth * scale) / 2 && abs(p.y - 112) <= 22
  }
  func gestureChanged(_ value: DragGesture.Value) {
    gestureChanged(
      start: value.startLocation, location: value.location, translation: value.translation)
  }
  // Keep touch arithmetic independent of SwiftUI's opaque gesture value so
  // cancellation, tiny drags and projected flicks can be tested directly.
  func gestureChanged(start: CGPoint, location: CGPoint, translation: CGSize) {
    if !down {
      touch()
      down = true
      hand = true
      dragged = false
      verticalMoved = false
      canPeek =
        abs(scroll.value - floor(scroll.value + 0.5)) < 0.005
        && (reduced || abs(scroll.value - follower.value) < 0.005)
      scroll.cancel()
      dragOrigin = floor(scroll.value + 0.5)
      dragFrom = scroll.value
      actionPress.set(hitsAction(start) ? 1 : 0)
    }
    if !hitsAction(location) || abs(translation.width) > 4 || abs(translation.height) > 4 {
      actionPress.set(0)
    }
    if abs(translation.height) > 4 { verticalMoved = true }
    // Track every point immediately. The four-point threshold classifies the
    // release as a drag; it must not subtract movement from the finger.
    if !dragged && abs(translation.width) > 4 {
      changePeek(false)
      dragged = true
    }
    if translation.width != 0 { scroll.set(dragFrom - translation.width / 56) }
  }
  func gestureEnded(_ value: DragGesture.Value) {
    gestureEnded(location: value.location, velocity: value.velocity)
  }
  func gestureEnded(location: CGPoint, velocity: CGSize) {
    actionPress.set(0)
    down = false
    if dragged {
      let v = -velocity.width / 56
      var to = floor(scroll.value + ReferralRewardMath.clamp(v * 0.16, -3, 3) + 0.5)
      if abs(v) > 1.5 && to == dragOrigin { to += v > 0 ? 1 : -1 }
      settle(to, velocity: v, flick: true)
      return
    }
    if verticalMoved {
      changePeek(false)
      settle(floor(scroll.value + 0.5), velocity: 0, flick: false)
      return
    }
    if peekOpen {
      if hitsAction(location) { invite(selected: focus) } else { changePeek(false) }
      return
    }
    let dx = location.x - layout.width / 2
    let dy = location.y - 112
    let half = 42 + (layout.width / 2 - 66) * peek.value
    let end = max(abs(dx) - (half - 42), 0)
    if end * end + dy * dy <= 42 * 42 && (canPeek || peek.value > 0) {
      settle(floor(scroll.value + 0.5), velocity: 0, flick: false)
      changePeek(true)
      return
    }
    changePeek(false)
    let steps =
      abs(dx) < 42 ? 0 : (dx > 0 ? 1.0 : -1.0) * min(max(floor((abs(dx) - 6) / 56 + 0.5), 1), 3)
    settle(floor(scroll.value + 0.5) + steps, velocity: 0, flick: false)
  }
  func cancelGesture() {
    guard down else { return }
    down = false
    actionPress.set(0)
    settle(floor(scroll.value + 0.5), velocity: 0, flick: false)
  }
  private func settle(_ to: Double, velocity: Double, flick: Bool) {
    scroll.set(
      reduced
        ? Motion.timing(to, 200)
        : Motion.spring(to, flick ? 400 : 380, flick ? 0.86 : 1, velocity: velocity))
  }
  func buttonDown(_ value: Bool) {
    if value {
      touch()
      press.set(Motion.timing(1, 120))
    } else {
      press.set(reduced ? Motion.timing(0, 120) : Motion.spring(0, 300, 0.8))
    }
  }
}

@MainActor private final class ReferralRewardLinkProxy: NSObject {
  weak var model: ReferralRewardModel?
  init(_ model: ReferralRewardModel) { self.model = model }
  @objc func tick(_ link: CADisplayLink) { model?.tick(link.targetTimestamp * 1000) }
}
