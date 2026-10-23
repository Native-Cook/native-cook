import SwiftUI

// Reanimated's `Easing` as ReferralReward uses it. Cubic-bezier easings are
// evaluated by SwiftUI's own `UnitCurve` (iOS 17) instead of a port of
// Reanimated's `Bezier.ts` solver: the same curve, solved natively.
// `UnitCurve` solves in single precision, so it agrees with Reanimated's
// double-precision Newton solver to within 4.9e-6 of the eased value (the
// largest gap over the Glowknob console's five curves, which include both
// of ReferralReward's, sampled every 1e-5 of progress).

typealias ReferralRewardEasingFunction = @Sendable (Double) -> Double

enum ReferralRewardEasing {
  /// `Easing.linear`.
  static let linear = curve(.linear)

  /// `Easing.bezier(x1, y1, x2, y2)`: the CSS cubic-bezier from (0, 0) to
  /// (1, 1) with these control points.
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double)
    -> ReferralRewardEasingFunction
  {
    precondition(
      x1 >= 0 && x1 <= 1 && x2 >= 0 && x2 <= 1, "Bezier x values must be in [0, 1] range.")
    return curve(
      .bezier(startControlPoint: UnitPoint(x: x1, y: y1), endControlPoint: UnitPoint(x: x2, y: y2)))
  }

  /// `Easing.inOut(Easing.quad)` — withTiming's default easing. Two quadratic
  /// halves, not a cubic bezier, so there is no `UnitCurve` for it.
  static let inOutQuad: ReferralRewardEasingFunction = { t in
    t < 0.5 ? (t * t * 2) / 1 : 1 - (((1 - t) * (1 - t)) * 2) / 1
  }

  /// `Easing.out(Easing.quad)`: `out(easing) = t => 1 - easing(1 - t)` with
  /// `quad = t => t * t`. The stoke ramp.
  static let outQuad: ReferralRewardEasingFunction = { t in 1 - (1 - t) * (1 - t) }

  private static func curve(_ curve: UnitCurve) -> ReferralRewardEasingFunction {
    { curve.value(at: $0) }
  }
}
