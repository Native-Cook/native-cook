import SwiftUI

// The two timing curves the reference's timed animations use, as the exact functions and not as
// Bézier approximations of them (Reanimated's Easing.inOut(Easing.quad) and
// Easing.out(Easing.cubic)).

struct ChooseYourBallEasedAnimation: CustomAnimation {
    enum Curve: Hashable {
        /// 2t² up to the middle, then 1 - 2(1 - t)². The default of the reference's timings.
        case inOutQuad
        /// 1 - (1 - t)³.
        case outCubic

        func value(at t: Double) -> Double {
            switch self {
            case .inOutQuad: t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
            case .outCubic: 1 - (1 - t) * (1 - t) * (1 - t)
            }
        }
    }

    let curve: Curve
    let duration: TimeInterval

    func animate<V: VectorArithmetic>(value: V, time: TimeInterval, context: inout AnimationContext<V>) -> V? {
        guard time < duration else { return nil }
        return value.scaled(by: curve.value(at: time / duration))
    }
}

extension Animation {
    static func chooseYourBallEased(_ curve: ChooseYourBallEasedAnimation.Curve, duration: TimeInterval) -> Animation {
        Animation(ChooseYourBallEasedAnimation(curve: curve, duration: duration))
    }
}
