import SwiftUI

struct EmberFrame: Equatable {
  private typealias M = EmberMotionConstants
  private typealias E = EmberTheme.E

  /// Visible charge, clamped to 0…1.
  var charge = 0.0

  /// Filtered heat; follows charge immediately under Reduce Motion.
  var glow = 0.0

  /// Ignition age as a fraction of M.ignite.
  var ignition = 0.0

  /// Decorative elapsed seconds; frozen at zero under Reduce Motion.
  var time = 0.0

  /// Stripe displacement in points, wrapped at E.stripe.period.
  var drift = 0.0

  /// Integrated heart beat phase, wrapped to 0…1.
  var phase = 0.0

  /// Eased pressure driving card compression and touch light.
  var press = 0.0

  /// Spring displacement of the liquid-light lean.
  var slosh = 0.0

  /// Horizontal light aim in -1…1 card coordinates.
  var aimX = 0.0

  /// Vertical light aim in -1…1 card coordinates.
  var aimY = 0.0

  /// Shared opacity/materialisation fraction after the live gate.
  var enter = 0.0

  /// Spring displacement of the bar head, in points.
  var meniscus = 0.0

  /// Touch-light strength; instant onset, eased release.
  var touchLight = 0.0

  /// Touch-light radius growth fraction; does not shrink on release.
  var touchSpread = 0.0

  /// Resolved tier sent to both Canvas and Metal.
  var quality: EmberQuality = .high

  /// System preference; intentional charge and cooldown still run.
  var reduced = false

  /// Daily step goal, kept above the resting count by the component.
  var goal = E.goal

  var steps: Double { Double(E.restSteps) + charge * Double(goal - E.restSteps) }
  var progress: Double { steps / Double(goal) }
  var age: Double { ignition * M.ignite }
  var beat: Double { reduced ? 1 : EmberMath.beat(phase) }
  var fuse: Double {
    reduced || age <= 0
      ? 0 : min(1, age / M.bar.fuseIn) * exp(-max(0, age - M.bar.fuseIn) / M.bar.fuseOut)
  }
  // Analytic coefficients below are the original card pop/compression and
  // count-slam curves in ember-charge.tsx; they are motion, not layout offsets.
  var cardScale: Double {
    if reduced { return 1 }
    let compress = 0.012 * charge * (1 - EmberMath.seg(ignition, 0, 0.055))
    let pop = ignition <= 0 ? 0 : 0.1 * exp(-9 * age) * sin(12 * age)
    return (1 - compress + pop) * (1 - 0.04 * press)
  }
  /// Tremble frequencies (28/31 Hz) and sub-point amplitudes from the reference.
  var shake: CGSize {
    let v = reduced ? 0 : charge * charge * (1 - EmberMath.seg(ignition, 0, 0.02))
    return CGSize(
      width: sin(time * 28 * 2 * .pi) * 0.6 * v, height: cos(time * 31 * 2 * .pi) * 0.45 * v)
  }
  var slam: Double { reduced || ignition <= 0 ? 1 : 1 + 0.18 * exp(-9 * age) * cos(12 * age) }
}
