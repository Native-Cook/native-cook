import Foundation
import Observation

/// The page: `FlareNotes` in `flare-notes.tsx`. Owns the three pills, the
/// "live" gate, the replay and the controls' fade.
@MainActor
@Observable
final class FlareNotesModel {
  /// `useReducedMotion()`: the system setting (read at launch, as Reanimated's
  /// `ReducedMotionManager` does).
  let reduced: Bool
  let pills: [FlarePillModel]

  /// Bumped by "Deliver again"; a new round leaves every note unread.
  private(set) var round = 0
  /// False until the stack's flames have drawn a few smooth frames; the
  /// arrival waits for it.
  private(set) var live = false
  /// The controls' entering FadeIn (0 before `live`).
  private(set) var controlsOpacity = 0.0

  let controls = FlareAnimatedValue(0)

  /// The first frame of three shader surfaces (and their pipeline) can hold
  /// the main thread for a long time; an arrival started on appear would play
  /// out unseen and the pills would pop in whole. So the arrival waits until
  /// the frame loop has run a few smooth frames in a row.
  @ObservationIgnored private var smooth = 0
  @ObservationIgnored private var watch: FlareFrameCallback?
  @ObservationIgnored private var started = false

  init(reduced: Bool? = nil, keys: FlareBlobKeys = .shared) {
    // Resolved here, on the main actor: a default argument is evaluated
    // outside it in the Swift 5 language mode.
    let reduced = reduced ?? FlareUIRuntime.shared.isReduceMotion
    self.reduced = reduced
    pills = FLARES.enumerated().map { FlarePillModel(flare: $1, index: $0, reduced: reduced, keys: keys) }
    FlareUIRuntime.shared.onAfterFrame { [weak self] _ in self?.afterFrame() }
  }

  /// The pills are on screen (their surfaces are mounted): start the flame
  /// clocks, all together, and count smooth frames from here.
  func pillsAppeared() {
    guard !started else { return }
    started = true
    for pill in pills { pill.startFlameClock() }
    smooth = 0
    let watch = FlareFrameCallback { [weak self] timeSincePreviousFrame in
      self?.watchFrame(timeSincePreviousFrame ?? 100)
    }
    self.watch = watch
    watch.setActive(!live)
  }

  /// One frame of the gate: `dt` ms since the previous frame.
  func watchFrame(_ dt: Double) {
    smooth = dt < FlareBeats.smoothFrame ? smooth + 1 : 0
    if smooth == FlareBeats.liveFrames { goLive() }
  }

  private func goLive() {
    guard !live else { return }
    live = true
    watch?.setActive(false)
    for pill in pills { pill.arrive(0) }
    // The controls wait for the last note to land: FadeIn
    // .delay(arrival + 120).duration(320).easing(EASE_OUT); none when reduced.
    if reduced {
      controls.set(1)
    } else {
      controls.set(flareWithDelay(FlareBeats.controlsDelay,
                             flareWithTiming(1, duration: FlareBeats.controlsFade, easing: FlareBeats.easeOut)))
    }
  }

  /// "Deliver again": every pill replays itself in place.
  func replay() {
    for pill in pills { pill.arrive(reduced ? FlareBeats.reducedLeave : FlareMotion.leave) }
    // Only the read state lives in React; it can catch up a frame late.
    round += 1
    FlareHaptics.impactLight()
  }

  func toggle(_ pill: FlarePillModel) { pill.toggle(round: round) }

  private func afterFrame() {
    for pill in pills { pill.afterFrame() }
    let c = controls.get()
    if c != controlsOpacity { controlsOpacity = c }
  }
}
