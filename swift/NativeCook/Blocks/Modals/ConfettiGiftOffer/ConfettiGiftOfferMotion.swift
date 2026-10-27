import SwiftUI

/// The story's screens, in the order the loop plays them (`Stage` in ConfettiGiftOffer.tsx).
enum ConfettiGiftOfferStage {
    case gift
    case opening
    case offer
    case exit
    case signup
    case complete
    case dismissed
}

/// Pure functions of loop time. Every number is copied from ConfettiGiftOffer.tsx and motion.ts so that a
/// frame of the loop is a function of `t` alone (that is what makes `previewTime` work).
enum ConfettiGiftOfferMotion {
    /// `LOOP` (ConfettiGiftOffer.tsx:17).
    static let loop = 9.5
    /// `SOURCE_END` (ConfettiGiftOffer.tsx:18): the account screen starts fading here.
    static let sourceEnd = 8.4
    /// The time Reduce Motion freezes the loop at: the settled offer (`useSceneClock(…, 1.5, …)`).
    static let settled = 1.5

    /// `Easing.bezierFn(0.23, 1, 0.32, 1)`: the strong ease-out every settle uses.
    private static let enterCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    static func enter(_ x: Double) -> Double {
        enterCurve.value(at: min(1, max(0, x)))
    }

    /// `phase()` in motion.ts: a smoothstep from `start` to `end`.
    static func phase(_ t: Double, _ start: Double, _ end: Double) -> Double {
        let x = min(1, max(0, (t - start) / (end - start)))
        return x * x * (3 - 2 * x)
    }

    /// `fraction()` in ConfettiGiftOffer.tsx:20: linear 0...1, exactly 1 at the end.
    static func fraction(_ t: Double, _ a: Double, _ b: Double) -> Double {
        if !t.isFinite { return 0 }
        if t >= b - 1e-9 { return 1 }
        return min(1, max(0, (t - a) / (b - a)))
    }

    /// `normalizeSceneTime()` in motion.ts.
    static func normalize(_ value: Double, period: Double) -> Double {
        guard value.isFinite, period.isFinite, period > 0 else { return 0 }
        let remainder = value.truncatingRemainder(dividingBy: period)
        return remainder < 0 ? remainder + period : remainder
    }

    /// Reanimated's `interpolate(…, 'clamp')`.
    static func interpolate(_ t: Double, _ inputs: [Double], _ outputs: [Double]) -> Double {
        guard let first = inputs.first, let last = inputs.last else { return 0 }
        if t <= first { return outputs[0] }
        if t >= last { return outputs[outputs.count - 1] }
        var index = 1
        while index < inputs.count - 1 && t > inputs[index] { index += 1 }
        let span = inputs[index] - inputs[index - 1]
        let along = span == 0 ? 0 : (t - inputs[index - 1]) / span
        return outputs[index - 1] + (outputs[index] - outputs[index - 1]) * along
    }

    // MARK: Measured source curves (ConfettiGiftOffer.tsx:21-36)

    private static let pushInputs: [Double] = [4.5, 4.5167, 4.5333, 4.55, 4.5667, 4.5833, 4.6, 4.6333, 4.6667, 4.7, 4.75, 4.8, 4.8333, 4.9]
    private static let pushOutputs: [Double] = [0, 0.068, 0.183, 0.294, 0.407, 0.503, 0.546, 0.731, 0.832, 0.894, 0.95, 0.978, 0.989, 1]

    /// `sourcePush`: the incoming-screen edge measured from the source frames 270-290 (60 fps).
    static func push(_ t: Double) -> Double {
        interpolate(t, pushInputs, pushOutputs)
    }

    private static let sheetInputs: [Double] = [3.167, 3.2, 3.2167, 3.2333, 3.25, 3.2667, 3.2833, 3.3, 3.3333, 3.35, 3.3833, 3.4167, 3.4333]
    private static let sheetOutputs: [Double] = [0, 0.30, 0.377, 0.499, 0.601, 0.685, 0.756, 0.815, 0.903, 0.935, 0.978, 0.998, 1]

    /// `sourceSheet`: how far the skip sheet has risen.
    static func sheet(_ t: Double) -> Double {
        interpolate(t, sheetInputs, sheetOutputs)
    }

    /// `sourceGiftAlpha`.
    static func giftAlpha(_ t: Double) -> Double {
        t < 0.583 ? 1 : phase(t, 8.75, 9.1)
    }

    private static let offerInputs: [Double] = [0.583, 0.633, 0.65, 0.667, 0.683, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 1, 1.05, 1.0833, 1.1]
    private static let offerOutputs: [Double] = [0, 0.097, 0.124, 0.171, 0.226, 0.263, 0.359, 0.479, 0.581, 0.682, 0.779, 0.871, 0.954, 1, 1]

    /// `sourceOfferAlpha`: the ticket's neutral luminance measured at source frames 38-65.
    static func offerAlpha(_ t: Double) -> Double {
        interpolate(t, offerInputs, offerOutputs) * (1 - phase(t, 4.9, 4.92))
    }

    /// `sourceSignupAlpha`.
    static func signupAlpha(_ t: Double) -> Double {
        (t >= 4.5 ? 1 : 0) * (1 - phase(t, sourceEnd, 8.75))
    }

    /// `stageAt` (ConfettiGiftOffer.tsx:40).
    static func stage(at time: Double) -> ConfettiGiftOfferStage {
        let t = time + 1e-9
        if t < 0.583 || t >= 8.75 { return .gift }
        if t < 1.1 { return .opening }
        if t < 3.167 { return .offer }
        if t < 4.9 { return .exit }
        return .signup
    }
}
