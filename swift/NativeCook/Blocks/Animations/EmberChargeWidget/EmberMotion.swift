import Observation
import SwiftUI
import UIKit

/// One UI-thread display clock owns the choreography, spring integration and
/// state transitions. Decorative integration stops under Reduce Motion.
@MainActor @Observable final class EmberMotion: NSObject {
  private typealias M = EmberMotionConstants
  private typealias E = EmberTheme.E

  // Published state

  /// The single published snapshot shared by native text, Canvas and Metal.
  var frame = EmberFrame()

  /// Quantized count currently on screen; only endpoints receive a native roll.
  private(set) var shown = E.restSteps

  /// Last displayed count, used as the initial glyphs of an endpoint landing.
  private(set) var previousShown = E.restSteps

  /// Last landed endpoint; remains unchanged while the count runs between endpoints.
  private(set) var reached = false

  /// Allows the loop when no manual interaction owns the card.
  var autoPlay = false

  /// Explicit tiers bypass the one-time automatic decision.
  var requestedQuality: EmberQuality = .auto

  /// Called once on completed manual charge, never by autoplay or loop takeover.
  var onIgnite: (() -> Void)?

  /// The current manual transition; the loop has a separate clock.
  private(set) var state: EmberMode = .idle

  // Clock

  /// Display callback retained only while this scene is active.
  @ObservationIgnored private var link: CADisplayLink?

  /// Weak target breaks the display-link retain cycle.
  @ObservationIgnored private var proxy: EmberDisplayProxy?

  /// Previous display timestamp; zero marks the first callback after starting.
  @ObservationIgnored private var last = 0.0

  /// Accumulated real seconds; timing deadlines use this unclamped clock.
  @ObservationIgnored private var elapsed = 0.0

  /// Current position in the autoplay loop, in seconds.
  @ObservationIgnored private var clock = 0.0

  /// Elapsed time at loop ownership; nil waits for the entrance to finish.
  @ObservationIgnored private var loopStart: Double?

  /// Elapsed time at the beginning of the current charge ramp.
  @ObservationIgnored private var stateStart = 0.0

  /// Remaining charge duration, scaled by the amount still to fill.
  @ObservationIgnored private var duration = 0.0

  /// Elapsed time of ignition; takeover backdates it instead of replaying the flash.
  @ObservationIgnored private var igniteStart = 0.0

  /// Elapsed deadline for cooldown, independent of whether a finger remains down.
  @ObservationIgnored private var coolStart = 0.0

  /// First uninterrupted manual rest; nil cancels the pending autoplay resume.
  @ObservationIgnored private var resumeStart: Double?

  /// Consecutive frames below the entrance gate threshold.
  @ObservationIgnored private var smooth = 0

  /// Elapsed time when the canvases first passed the smooth-frame gate.
  @ObservationIgnored private var enterStart: Double?

  // Touch and ownership

  /// A touch or accessibility activation owns charge until the resume delay finishes.
  @ObservationIgnored private var manual = false

  /// A finger is still down; controls recharge and selection haptics.
  @ObservationIgnored private var pressing = false

  /// Goal hold or cooldown is in progress, even if the finger has already left.
  @ObservationIgnored private var held = false

  /// Manual or loop charge before the published 0…1 clamp.
  @ObservationIgnored private var charge = 0.0

  /// Charge at the start of a manual ramp, preserving a mid-loop takeover.
  @ObservationIgnored private var fromCharge = 0.0

  /// Elapsed start of the current pressure easing segment.
  @ObservationIgnored private var pressureStart = 0.0

  /// Pressure at the start of the segment, so interruptions remain continuous.
  @ObservationIgnored private var pressureFrom = 0.0

  /// Pressure target; ignition releases it even with a finger still down.
  @ObservationIgnored private var pressureTo = 0.0

  /// Duration of the current pressure segment, in seconds.
  @ObservationIgnored private var pressureDuration = M.press

  /// Whether smoothed pressure is rising; this drives light independently of finger ownership.
  @ObservationIgnored private var touching = false

  /// Elapsed time at the latest pressure direction change, used for the light fade.
  @ObservationIgnored private var touchStart = 0.0

  /// Light strength at release; a new fade starts from the visible level.
  @ObservationIgnored private var touchFrom = 0.0

  /// Elapsed start of the outward touch-light spread.
  @ObservationIgnored private var spreadStart = 0.0

  /// Spread at the last rise; release fades light without shrinking its radius.
  @ObservationIgnored private var spreadFrom = 0.0

  // Springs

  /// Light-lean spring velocity; kicked by press, release and ignition.
  @ObservationIgnored private var sloshV = 0.0

  /// Bar-head spring velocity, integrated with its own smaller substeps.
  @ObservationIgnored private var meniscusV = 0.0

  /// Previous normalized bar head for estimating velocity drag.
  @ObservationIgnored private var previousProgress: Double?

  /// Horizontal aim response, preserving follow velocity between samples.
  @ObservationIgnored private var aimXSpring: EmberSpring?

  /// Vertical aim response, preserving follow velocity between samples.
  @ObservationIgnored private var aimYSpring: EmberSpring?

  /// Overshoot-clamped early-release response back to resting charge.
  @ObservationIgnored private var releaseSpring: EmberSpring?

  // Quality

  /// First fully visible frame; warm-up and entrance are excluded from measurement.
  @ObservationIgnored private var qualityStart: Double?

  /// Slow frames inside the single quality measurement window.
  @ObservationIgnored private var slow = 0

  /// Latches the automatic tier decision; the card never upgrades or measures twice.
  @ObservationIgnored private var measured = false

  /// Starts high and may drop once to medium; low is always explicit.
  @ObservationIgnored private var autoQuality: EmberQuality = .high

  // Haptics

  /// Previous 500-step bucket; only upward crossings with a finger down tick.
  @ObservationIgnored private var lastBucket = Int(
    floor(Double(E.restSteps) / M.count.hapticStride))

  /// Prepared on touch-down; fires for upward step buckets.
  @ObservationIgnored private let selection = UISelectionFeedbackGenerator()

  /// Goal notification for manual charge and accessibility activation.
  @ObservationIgnored private let success = UINotificationFeedbackGenerator()

  /// Heavy impact paired with the manual goal notification.
  @ObservationIgnored private let impact = UIImpactFeedbackGenerator(style: .heavy)

  func start() {
    guard link == nil else { return }
    let target = EmberDisplayProxy(self)
    proxy = target
    let displayLink = CADisplayLink(target: target, selector: #selector(EmberDisplayProxy.tick(_:)))
    // Platform scheduling request; integration remains based on real display timestamps.
    displayLink.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 60)
    displayLink.add(to: .main, forMode: .common)
    link = displayLink
    last = 0
  }

  func stop() {
    link?.invalidate()
    link = nil
    proxy = nil
    last = 0
  }

  func setReduced(_ value: Bool) {
    guard frame.reduced != value else { return }
    frame.reduced = value
    // Stop/restart autoplay at rest, just as the source's playing reaction.
    loopStart = nil
    clock = 0
    if value {
      frame.time = 0
      frame.aimX = 0
      frame.aimY = 0
      frame.slosh = 0
      if !manual {
        charge = 0
        frame.charge = 0
        frame.ignition = 0
        frame.glow = 0
        updateNumbers()
      }
    }
  }

  private func pressure(_ to: Double, _ seconds: Double) {
    pressureFrom = frame.press
    pressureTo = to
    pressureStart = elapsed
    pressureDuration = seconds
  }

  private func aim(_ target: SIMD2<Double>, duration: Double, velocity: SIMD2<Double> = .zero) {
    let vx = duration == M.aim.follow.duration ? (aimXSpring?.velocity ?? 0) : velocity.x
    let vy = duration == M.aim.follow.duration ? (aimYSpring?.velocity ?? 0) : velocity.y
    aimXSpring = EmberSpring(
      from: frame.aimX, to: target.x, duration: duration, damping: M.aim.follow.dampingRatio,
      velocity: vx)
    aimYSpring = EmberSpring(
      from: frame.aimY, to: target.y, duration: duration, damping: M.aim.follow.dampingRatio,
      velocity: vy)
  }

  func follow(_ p: CGPoint, scale: Double) {
    guard !frame.reduced else { return }
    aim(
      SIMD2(
        EmberMath.clamp(p.x / (scale * (E.size / 2)) - 1, -1, 1),
        EmberMath.clamp(p.y / (scale * (E.size / 2)) - 1, -1, 1)), duration: M.aim.follow.duration)
  }

  func begin(_ p: CGPoint, scale: Double) {
    resumeStart = nil
    follow(p, scale: scale)
    pressing = true
    sloshV += M.slosh.press
    pressure(1, M.press)
    selection.prepare()
    success.prepare()
    impact.prepare()
    if autoPlay && !frame.reduced && !manual {
      charge = EmberMath.charge(clock)
      manual = true
      loopStart = nil
      // Take over the remaining goal hold without replaying success or restarting ignition.
      if clock >= M.loop.charged && clock < M.loop.held {
        held = true
        state = .ignited
        igniteStart = elapsed - (clock - M.loop.charged)
        coolStart = elapsed + M.loop.held - clock
        pressure(0, M.pressRelease)
        return
      }
      held = false
      frame.ignition = 0
    }
    manual = true
    if held {
      pressure(0, M.pressRelease)
      return
    }
    chargeUp()
  }

  func end(velocity: CGSize, scale: Double) {
    aim(
      .zero, duration: M.aim.release.duration,
      velocity: SIMD2(
        velocity.width / (scale * (E.size / 2)), velocity.height / (scale * (E.size / 2))))
    pressure(0, M.finalizePress)
    guard pressing else { return }
    pressing = false
    if held { return }
    sloshV += M.slosh.release
    // Early release → releasing; a completed goal keeps its own hold/cooldown.
    state = .releasing
    releaseSpring = EmberSpring(
      from: charge, to: 0, duration: M.release.duration, damping: M.release.dampingRatio,
      clamped: true)
  }

  func activate() {
    resumeStart = nil
    if autoPlay && !frame.reduced && !manual { charge = EmberMath.charge(clock) }
    manual = true
    loopStart = nil
    aim(.zero, duration: M.aim.release.duration)
    chargeUp()
  }
  /// Continue from visible charge; a new finger never resets the ramp to zero.

  private func chargeUp() {
    held = false
    frame.ignition = 0
    state = .charging
    stateStart = elapsed
    fromCharge = charge
    duration = max(M.minimumDuration, (1 - charge) * (frame.reduced ? M.chargeReduced : M.charge))
  }
  /// Charging → ignited: latch the goal and schedule an independent hold/cooldown.

  private func ignite() {
    charge = 1
    held = true
    state = .ignited
    igniteStart = elapsed
    coolStart = elapsed + M.hold
    pressure(0, M.pressRelease)
    success.notificationOccurred(.success)
    impact.impactOccurred()
    onIgnite?()
  }

  func tick(_ timestamp: Double) {
    var next = frame
    let delta = last == 0 ? M.liveGate.missingDelta : timestamp - last
    last = timestamp
    let dt = EmberMath.clamp(delta, M.integration.minimum, M.integration.maximum)
    elapsed += delta
    if smooth < M.liveGate.frames {
      smooth = delta < M.liveGate.smoothFrame ? smooth + 1 : 0
      if smooth == M.liveGate.frames { enterStart = elapsed }
    }
    if let start = enterStart {
      next.enter = EmberMath.bezier(
        EmberMath.seg(elapsed - start, 0, next.reduced ? M.enterReduced : M.enter), out: true)
    }
    // Numerical snap preserves the original entrance gate at effectively full opacity.
    if next.enter > 0.99999 { next.enter = 1 }
    if next.enter == 1 && !measured && requestedQuality == .auto {
      if qualityStart == nil { qualityStart = elapsed }
      if elapsed - qualityStart! <= M.quality.window {
        if delta > M.quality.frame { slow += 1 }
      } else {
        measured = true
        if slow > M.quality.allowed { autoQuality = .medium }
      }
    }
    next.quality = requestedQuality == .auto ? autoQuality : requestedQuality
    let previousIgn = next.ignition
    let looping = autoPlay && !next.reduced && !manual
    if looping {
      if loopStart == nil && next.enter == 1 { loopStart = elapsed }
      clock = loopStart.map { (elapsed - $0).truncatingRemainder(dividingBy: M.loop.end) } ?? 0
      charge = EmberMath.charge(clock)
      next.ignition = EmberMath.ignition(clock)
      let a = clock / M.loop.end * 2 * .pi
      let envelope = pow(sin(a / 2), 2)
      next.aimX = M.aim.x * envelope * sin(a)
      next.aimY = M.aim.y * envelope * sin(2 * a)
    } else {
      let age = elapsed - stateStart
      switch state {
      case .charging:
        charge = fromCharge + (1 - fromCharge) * EmberMath.bezier(EmberMath.seg(age, 0, duration))
        if age >= duration { ignite() }
      case .ignited, .cooling:
        next.ignition = min(1, max(0, (elapsed - igniteStart) / M.ignite))
        if elapsed >= coolStart {
          // Ignited → cooling preserves the ignition clock until the floor is at rest.
          state = .cooling
          charge = 1 - EmberMath.bezier(EmberMath.seg(elapsed - coolStart, 0, M.cool))
          if elapsed >= coolStart + M.cool {
            charge = 0
            next.ignition = 0
            held = false
            state = .idle
            // Cooling → idle, then immediately recharge if the original finger is still down.
            if pressing { chargeUp() }
          }
        }
      case .releasing:
        releaseSpring?.step(delta)
        charge = releaseSpring?.value ?? 0
        if releaseSpring?.finished != false {
          charge = 0
          state = .idle
        }
      case .idle: break
      }
      if !next.reduced {
        aimXSpring?.step(delta)
        aimYSpring?.step(delta)
        next.aimX = EmberMath.clamp(aimXSpring?.value ?? next.aimX, -1, 1)
        next.aimY = EmberMath.clamp(aimYSpring?.value ?? next.aimY, -1, 1)
      }
      if manual && !pressing && !held && charge <= 0.001 && autoPlay && !next.reduced {
        if resumeStart == nil { resumeStart = elapsed }
        if elapsed - resumeStart! >= M.resume + M.resumeCompletion {
          manual = false
          loopStart = nil
          resumeStart = nil
        }
      } else {
        resumeStart = nil
      }
    }
    next.charge = EmberMath.clamp(charge)
    let oldPress = next.press
    next.press =
      pressureFrom + (pressureTo - pressureFrom)
      * EmberMath.bezier(EmberMath.seg(elapsed - pressureStart, 0, pressureDuration), out: true)
    // Ignore floating-point noise when detecting pressure direction changes.
    if abs(next.press - oldPress) > 0.0000001 {
      let down = next.press > oldPress
      if down != touching {
        touching = down
        touchStart = elapsed
        touchFrom = next.touchLight
        if down {
          if next.touchLight == 0 { next.touchSpread = 0 }
          spreadFrom = next.touchSpread
          spreadStart = elapsed
          next.touchLight = 1
        }
      }
    }
    if touching {
      next.touchLight = 1
      next.touchSpread =
        spreadFrom + (1 - spreadFrom)
        * (1 - pow(1 - EmberMath.seg(elapsed - spreadStart, 0, M.touch.spread), 3))
    } else {
      next.touchLight = touchFrom * pow(1 - EmberMath.seg(elapsed - touchStart, 0, M.touch.fade), 3)
    }
    if !next.reduced {
      next.time = elapsed
      next.glow +=
        (charge - next.glow) * (1 - exp(-dt * (charge > next.glow ? M.heat.rise : M.heat.fall)))
      next.drift =
        (next.drift + (M.stripeIdle + (M.stripeMax - M.stripeIdle) * pow(next.charge, 1.5)) * dt)
        .truncatingRemainder(
          dividingBy: E.stripe.period)
      let bpm = Double(E.restBpm) + Double(E.goalBpm - E.restBpm) * EmberMath.seg(charge, 0.9, 1)
      next.phase = (next.phase + dt * bpm / 60).truncatingRemainder(dividingBy: 1)
      if previousIgn == 0 && next.ignition > 0 { sloshV += M.slosh.ignite }
      sloshV += (-M.slosh.stiffness * next.slosh - M.slosh.damping * sloshV) * dt
      next.slosh += sloshV * dt
      if let p = previousProgress {
        // The reference limits meniscus lag to -8…10 pt and snaps tiny residual motion.
        let target = EmberMath.clamp(
          (next.progress - p) * E.bar.w / max(dt, 1 / M.integration.velocityHz) * M.bar.drag, -8, 10
        )
        let n = Int(ceil(dt * M.integration.barHz))
        let h = dt / Double(n)
        for _ in 0..<n {
          meniscusV += (M.bar.stiffness * (target - next.meniscus) - M.bar.damping * meniscusV) * h
          next.meniscus += meniscusV * h
        }
        if abs(next.meniscus) < 0.001 && abs(meniscusV) < 0.01 { next.meniscus = 0 }
      }
      previousProgress = next.progress
    } else {
      next.glow = charge
      next.time = 0
      next.aimX = 0
      next.aimY = 0
      next.slosh = 0
    }
    if next != frame { frame = next }
    updateNumbers()
  }

  private func updateNumbers() {
    let steps = frame.steps
    let goal = frame.goal
    let value: Int
    if steps >= Double(goal) - M.count.endpointTolerance {
      value = goal
    } else if steps <= Double(E.restSteps) + M.count.endpointTolerance {
      value = E.restSteps
    } else {
      // Absolute stride multiples remain strictly inside the two landing endpoints.
      value = min(
        goal - 1, max(E.restSteps + 1, Int(floor(steps / M.count.stride)) * Int(M.count.stride)))
    }
    if value != shown {
      previousShown = shown
      shown = value
    }
    if value == goal { reached = true }
    if value == E.restSteps { reached = false }
    let bucket = Int(floor(steps / M.count.hapticStride))
    if bucket > lastBucket && pressing { selection.selectionChanged() }
    lastBucket = bucket
  }
}
