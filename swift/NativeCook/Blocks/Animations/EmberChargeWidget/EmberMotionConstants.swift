import Foundation

/// `M` in ember-motion.ts. Durations are seconds here (RN uses milliseconds
/// except for ignite and loop); spring stiffness, damping and rates are unchanged.
enum EmberMotionConstants {

  /// Four loop marks: rest, charged, end of hold, and wrap, in seconds.
  static let loop = (rest: 0.8, charged: 3.0, held: 5.4, end: 6.2)

  /// Full manual charge; a partial takeover scales this by the remaining charge.
  static let charge = 2.2

  /// Intentional charge duration with decorative motion disabled.
  static let chargeReduced = 0.9

  /// The manual goal hold matches the loop’s charged-to-held interval.
  static let hold = loop.held - loop.charged

  /// Cooldown from goal to rest.
  static let cool = 0.8

  /// Early-release spring, with overshoot clamped by its caller.
  static let release = (duration: 0.45, dampingRatio: 0.75)

  /// Shared linear clock for flash, waves, sparks, pop and count slam.
  static let ignite = 1.1

  /// Touch-down pressure easing.
  static let press = 0.12

  /// Pressure releases before the ignition pop peaks, even while held.
  static let pressRelease = 0.06

  /// Critically damped follow/return springs and the loop figure-eight amplitudes.
  static let aim = (
    follow: (duration: 0.24, dampingRatio: 1.0),
    release: (duration: 0.48, dampingRatio: 1.0), x: 0.3, y: 0.2
  )

  /// One post-entrance measurement window and its slow-frame allowance.
  static let quality = (window: 2.0, frame: 0.024, allowed: 6)

  /// Stripe drift in points per second at rest.
  static let stripeIdle = 18.0

  /// Stripe drift in points per second at full charge.
  static let stripeMax = 260.0

  /// Unit-mass meniscus spring, velocity drag, fuse attack/decay and ringing.
  static let bar = (
    stiffness: 360.0, damping: 16.0, drag: 0.04,
    fuseIn: 0.065, fuseOut: 0.24, ringDecay: 9.0, ringFrequency: 15.0
  )

  /// Multiplier on the four inner-shadow offsets, from resting to hot.
  static let rise = (rest: 1.0, hot: 1.28)

  /// Light-lean spring and its press, release and ignition velocity kicks.
  static let slosh = (stiffness: 90.0, damping: 7.0, press: 0.35, release: -0.5, ignite: 1.1)

  /// Opacity and bevel materialisation share the entrance easing.
  static let enter = 0.32

  /// Uninterrupted manual rest before autoplay can regain ownership.
  static let resume = 2.5

  // Choreography outside M in ember-charge.tsx and ember-surface.tsx.

  /// Six consecutive smooth frames exclude shader warm-up from the entrance.
  static let liveGate = (frames: 6, smoothFrame: 0.034, missingDelta: 0.1)
  static let enterReduced = 0.2
  static let minimumDuration = 0.001

  /// The RN resume delay ends with a one-millisecond timing completion.
  static let resumeCompletion = 0.001
  static let finalizePress = 0.16
  static let touch = (spread: 0.24, fade: 0.32)
  static let heat = (rise: 16.0, fall: 5.0)

  /// Integration bounds prevent a delayed display callback from exploding a spring.
  static let integration = (minimum: 0.001, maximum: 0.05, barHz: 120.0, velocityHz: 240.0)
  static let count = (
    stride: 97.0, endpointTolerance: 0.5, hapticStride: 500.0,
    landingFrames: 2, duration: 0.24, bounce: 0.15
  )
  static let bpm = (duration: 0.32, bounce: 0.2)

  /// Fractions of ignite, not seconds: the flash peaks at 60.5 ms.
  static let flash = (peak: 0.055, end: 0.44)
  static let rippleEnd = 0.73
  static let waveEnd = 0.58
  static let sweep = 0.5

  /// Lub-dub envelope coordinates; the actual beat period comes from bpm.
  static let beat = (
    span: 430.0, lubUp: 90.0, lubDown: 110.0, dubUp: 70.0, dubDown: 160.0,
    lub: 0.12, dub: 0.07
  )
}
