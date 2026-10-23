import Foundation
import Observation

/// What SwiftUI draws of a pill each frame (the RN `useAnimatedStyle`s).
struct FlarePillVisual: Equatable {
  var opacity = 0.0
  var translateY = 0.0
  var scale = 1.0
  /// The note's own status line.
  var lineOpacity = 1.0
  var lineY = 0.0
  /// "Seen just now".
  var seenOpacity = 0.0
  var seenY = 0.0
}

/// One pill's state and choreography: `FlarePill` in `flare-notes.tsx`.
/// Shared values are `AnimatedValue`s stepped by `UIRuntime` (Reanimated 4.5.1
/// semantics); what React keeps in state (`readIn`, the measured copy widths)
/// is observable.
@MainActor
@Observable
final class FlarePillModel {
  let flare: Flare
  let index: Int
  let reduced: Bool
  let keys: [Double]
  let blobData: FlareBlobKeys

  // React state.
  /// The round the note was read in; a new delivery leaves it unread.
  var readIn = -1
  /// The copy block's laid-out width (title and status line) and the read
  /// line's. The flame stays faint left of the copy's right edge; both status
  /// lines are measured, so reading a note never moves that edge.
  var copyW = 0.0 { didSet { if copyW != oldValue { surface?.render() } } }
  var seenW = 0.0 { didSet { if seenW != oldValue { surface?.render() } } }
  /// The pill's width (from the page layout).
  var width = 0.0 { didSet { if width != oldValue { surface?.render() } } }

  /// What SwiftUI draws, refreshed after every frame in which it changed.
  private(set) var visual: FlarePillVisual

  // Shared values.
  let enter = FlareAnimatedValue(0)
  let reach: FlareAnimatedValue
  let heat: FlareAnimatedValue
  let cool = FlareAnimatedValue(0)
  /// 0 shows the note's line, 1 shows "Seen just now".
  let seenT = FlareAnimatedValue(0)
  let press = FlareAnimatedValue(0)
  let stoke = FlareAnimatedValue(0)
  /// 1 once the finger has stayed down past the hold threshold.
  let held = FlareAnimatedValue(0)
  /// Integrated flame time in seconds, so speeding the fire up never jumps
  /// the pattern. All three start together, as in the reference.
  let time = FlareAnimatedValue(0)

  let crackle = FlareHapticRhythm()
  let haptics = true
  @ObservationIgnored weak var surface: FlareSurfaceView?
  @ObservationIgnored private var flameClock: FlareFrameCallback?
  /// A touch is down on the pill (between onBegin and onFinalize).
  @ObservationIgnored var tracking = false
  /// The touch's latest translation from touch-down (for a cancelled touch).
  @ObservationIgnored var lastTranslation = CGSize.zero

  init(flare: Flare, index: Int, reduced: Bool, keys: FlareBlobKeys = .shared) {
    self.flare = flare
    self.index = index
    self.reduced = reduced
    self.keys = keys.keys(for: flare.id)
    blobData = keys
    reach = FlareAnimatedValue(reduced ? 1 : 0)
    heat = FlareAnimatedValue(reduced ? 1 : 0)
    visual = FlarePillVisual()
    visual = computeVisual()
  }

  func isRead(round: Int) -> Bool { readIn == round }

  /// `guard`: where the copy ends, measured from the pill's left edge.
  var guardX: Double {
    copyW > 0 ? FlareMotion.inset + max(copyW, seenW) + 8 : width * 0.45
  }

  // MARK: Frame work

  /// `useFrameCallback` integrating the flame time (not under reduced motion).
  func startFlameClock() {
    guard !reduced, flameClock == nil else { return }
    let clock = FlareFrameCallback { [weak self] timeSincePreviousFrame in
      guard let self else { return }
      let dt = min(timeSincePreviousFrame ?? 16, FlareBeats.maxFrameStep) / 1000
      let speed = 1 + stoke.get() * 1.1 + press.get() * 0.25 - cool.get() * 0.55
      time.set(time.get() + dt * speed)
    }
    flameClock = clock
    clock.setActive(true)
  }

  /// `useDerivedValue` → the shader's uniforms.
  var uniforms: FlareUniforms {
    let c = cool.get()
    let k = stoke.get()
    let p = press.get()
    return FlareUniforms(
      size: SIMD2(Float(width), Float(FlareMotion.height)),
      // 0 at ignition, 1 at rest, longer when stoked.
      stretch: Float(reach.get() * (1 + 0.55 * k + 0.08 * p)),
      heat: Float(heat.get() * (1 + 0.35 * k + 0.15 * p)),
      cool: Float(c * (1 - k * 0.5)),
      ember: Float(FlareMotion.ember),
      guardX: Float(guardX),
      body: flare.body.rgb,
      blobs: blobData.frame(keys, at: time.get())
    )
  }

  /// The pill's and the status lines' `useAnimatedStyle`s.
  func computeVisual() -> FlarePillVisual {
    let e = enter.get()
    let s = seenT.get()
    let p = press.get()
    return FlarePillVisual(
      opacity: e,
      translateY: reduced ? 0 : (1 - e) * -10,
      scale: (reduced ? 1 : 0.97 + 0.03 * e) - p * 0.03,
      lineOpacity: 1 - s,
      lineY: reduced ? 0 : s * -4,
      seenOpacity: s,
      seenY: reduced ? 0 : (1 - s) * 4
    )
  }

  /// After every frame: restyle (only when something changed) and redraw
  /// the flame.
  func afterFrame() {
    let next = computeVisual()
    if next != visual { visual = next }
    surface?.render()
  }

  // MARK: Choreography

  /// The arrival: the note fades in from above while its fire catches, licks
  /// out past its rest length and settles, the core flashing white. On a
  /// replay the old note first fades out over `out` ms and is reset unseen.
  func arrive(_ out: Double) {
    let delay = Double(index) * FlareMotion.stagger
    let ease = FlareBeats.easeOut
    func reset() -> FlareAnimationObject { flareWithDelay(out, flareWithTiming(0, duration: 0)) }
    enter.set(
      flareWithSequence(
        flareWithTiming(0, duration: out, easing: ease),
        flareWithDelay(delay, flareWithTiming(1, duration: reduced ? FlareBeats.reducedEnter : FlareMotion.enter, easing: ease))
      )
    )
    cool.set(reset())
    seenT.set(reset())
    if reduced { return }
    reach.set(
      flareWithSequence(reset(), flareWithDelay(delay + FlareMotion.igniteDelay, flareWithSpring(1, FlareBeats.reachSpring)))
    )
    heat.set(
      flareWithSequence(
        reset(),
        flareWithDelay(
          delay + FlareMotion.igniteDelay,
          flareWithSequence(
            flareWithTiming(FlareBeats.flashPeak, duration: FlareMotion.flash, easing: ease),
            flareWithTiming(1, duration: FlareMotion.settle, easing: ease)
          )
        )
      )
    )
  }

  /// Tap (or the VoiceOver activate action): read ⇄ relight.
  func toggle(round: Int) {
    let next = !isRead(round: round)
    readIn = next ? round : -1
    seenT.set(flareWithTiming(next ? 1 : 0, duration: reduced ? FlareBeats.reducedSwap : FlareMotion.swap,
                         easing: FlareBeats.easeOut))
    cool.set(flareWithTiming(next ? 1 : 0,
                        duration: reduced ? FlareBeats.reducedCool : next ? FlareMotion.cool : FlareMotion.relight,
                        easing: FlareBeats.easeInOut))
    if !next && !reduced {
      heat.set(
        flareWithSequence(
          flareWithTiming(FlareBeats.flashPeak, duration: FlareBeats.relightFlash, easing: FlareBeats.easeOut),
          flareWithTiming(1, duration: FlareBeats.relightSettle, easing: FlareBeats.easeOut)
        )
      )
    }
    if !haptics { return }
    if next { FlareHaptics.selection() } else { FlareHaptics.impactLight() }
  }

  // MARK: Gesture (RNGH `Pan().minDistance(0)`)

  /// `onBegin`: the finger touched down.
  func touchBegan() {
    press.set(flareWithTiming(1, duration: FlareBeats.pressIn, easing: FlareBeats.easeOut))
    held.set(0)
    // The hold timer must run its full 260 ms even with Reduce Motion on:
    // ReduceMotion.System skips the delay and jumps to the end, which made
    // every press a hold and no tap ever read a note.
    held.set(flareWithDelay(FlareMotion.holdAfter, flareWithTiming(1, duration: 1), .never))
    if !reduced {
      stoke.set(flareWithDelay(FlareMotion.holdAfter, flareWithTiming(1, duration: FlareMotion.stoke, easing: FlareEasing.outQuad)))
    }
    beginHold()
  }

  /// `onFinalize`: released or cancelled, `translation` from the touch-down.
  func touchEnded(translation: CGSize, round: Int) {
    // A release before the hold threshold, close to where it started, is a tap.
    let tap = Self.isTap(held: held.get(), translation: translation)
    held.set(0)
    press.set(flareWithSpring(0, FlareBeats.pressRelease))
    stoke.set(flareWithSpring(0, FlareBeats.stokeRelease))
    endHold(tap, round: round)
  }

  static func isTap(held: Double, translation: CGSize) -> Bool {
    held < 1 && hypot(translation.width, translation.height) < FlareBeats.tapSlop
  }

  private func beginHold() {
    // The crackle waits for the press to become a hold.
    if haptics && !reduced { crackle.play(FlareHapticRhythm.crackle) }
  }

  private func endHold(_ tap: Bool, round: Int) {
    crackle.stop()
    if tap { toggle(round: round) }
  }
}
