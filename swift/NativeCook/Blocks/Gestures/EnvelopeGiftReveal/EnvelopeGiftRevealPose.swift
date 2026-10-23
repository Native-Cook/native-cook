import SwiftUI

/// Where everything is for one frame: the derived values of `EnvelopeScene` (page colour, float, flap angle,
/// card flight, light sweep, price). Pure: the same frame always gives the same pose.
struct EnvelopeGiftRevealPose {
    private typealias M = EnvelopeGiftRevealMetrics
    private typealias Open = EnvelopeGiftRevealOpen
    private typealias Reveal = EnvelopeGiftRevealReveal
    private typealias Ease = EnvelopeGiftRevealEase

    /// The page's colour, lime to white (Reanimated's `interpolateColor`: RGB, gamma 2.2, whole channels).
    var page: Color
    var glowCentre: CGPoint
    var glowOpacity: Double
    /// The envelope group's own transform: down by `envY`, then scaled about its top-left corner.
    var envY: Double
    var envScale: Double
    var envOpacity: Double
    /// The flap's fold in degrees (0 closed, 180 folded back), the seal's crack, and the paper curl.
    var angle: Double
    var split: Double
    var curlDegrees: Double
    var flapFront: Double
    var flapBack: Double
    /// The light band's position on the card art (0.5 centred) and the price as shown.
    var sweep: Double
    var amount: String
    /// The card: its centre's y, scale and tilt (degrees), and whether the page's copy of it is the one in view.
    var cardY: Double
    var cardScale: Double
    var cardTilt: Double
    var cardFront: Bool
    var cardFrontOpacity: Double
    var cardInsideOpacity: Double

    init(frame f: EnvelopeGiftRevealFrame, width: Double, height: Double, lay: EnvelopeGiftRevealScreenLayout, reduced: Bool, roll: Double) {
        let clamped = reduced ? 0 : max(-EnvelopeGiftRevealTokens.Scene.rollLimit, min(EnvelopeGiftRevealTokens.Scene.rollLimit, roll))
        let resetting = f.reset > 0

        page = Self.pageColour(f.page * (1 - f.reset))

        // Ambient: the sealed envelope floats ±4 pt; it settles while it is being opened.
        let still = 1 - Ease.clamp01(f.p / EnvelopeGiftRevealTokens.Scene.floatStillProgress)
        let ambientWeight = 1 - f.ambientSeam
        let float = reduced ? 0 : EnvelopeGiftRevealAmbient.floatPt
            * sin(2 * Double.pi * f.now / EnvelopeGiftRevealAmbient.floatMs) * still * ambientWeight
        let drop = resetting ? 0 : f.drop
        // As the flap opens the envelope settles down a little (source frames 001 to 010).
        let settle = resetting ? 0 : Open.settlePt * Ease.clamp01(f.p / Open.flapShare)
        envY = lay.envTop + settle + drop * drop * EnvelopeGiftRevealTokens.Scene.envelopeDropDistance + float
        envScale = resetting
            ? EnvelopeGiftRevealTokens.Scene.resetScale + (1 - EnvelopeGiftRevealTokens.Scene.resetScale) * f.reset
            : 1
        envOpacity = resetting ? f.reset : 1 - f.drop

        // A slow highlight drifts across the lime page, kept very faint so the page colour at rest matches the source.
        let glowT = f.now / EnvelopeGiftRevealAmbient.glowMs
        let glowX = EnvelopeGiftRevealTokens.Scene.glowX
            + EnvelopeGiftRevealTokens.Scene.glowXAmplitude * sin(2 * Double.pi * glowT)
        let glowY = EnvelopeGiftRevealTokens.Scene.glowY
            + EnvelopeGiftRevealTokens.Scene.glowYAmplitude
            * cos(2 * Double.pi * glowT * EnvelopeGiftRevealTokens.Scene.glowYRate)
        // At t=0 the source glow is (0.5, 0.47). Blend back there under the reset cross-fade so t=8s
        // is pixel-identical even though the source's 9 s glow period does not divide the recording loop.
        glowCentre = CGPoint(
            x: width * (glowX + (EnvelopeGiftRevealTokens.Scene.glowX - glowX) * f.ambientSeam),
            y: height * (
                glowY
                    + (EnvelopeGiftRevealTokens.Scene.glowY + EnvelopeGiftRevealTokens.Scene.glowYAmplitude - glowY)
                    * f.ambientSeam
            )
        )
        glowOpacity = reduced ? 0 : EnvelopeGiftRevealAmbient.glowAmount * (1 - f.page)

        let folded = resetting
            ? 0
            : Ease.clamp01(f.p / Open.flapShare) * EnvelopeGiftRevealTokens.Scene.flapFullDegrees
        let flapAngle = resetting ? 0 : folded + f.breath
        angle = flapAngle
        // The seal cracks only when the finger lifts the flap (not on the idle breath), so the rest state stays the source's.
        split = Ease.clamp01(
            folded / (reduced ? EnvelopeGiftRevealTokens.Scene.reducedSealSplitDegrees : Open.sealSplitDeg)
        )
        // The flap is paper: its lower part lags the fold, so it curls as it lifts.
        curlDegrees = reduced
            ? 0
            : Open.curlDeg * sin(
                Double.pi
                    * min(EnvelopeGiftRevealTokens.Scene.flapFullDegrees, flapAngle)
                    / EnvelopeGiftRevealTokens.Scene.flapFullDegrees
            )
        flapFront = flapAngle < EnvelopeGiftRevealTokens.Scene.flapBackThreshold ? 1 : 0
        // Past 90° the flap folds away behind the envelope (source): it fades as it goes over.
        flapBack = flapAngle >= EnvelopeGiftRevealTokens.Scene.flapBackThreshold
            ? 1 - Ease.clamp01(
                (flapAngle - EnvelopeGiftRevealTokens.Scene.flapBackThreshold)
                    / EnvelopeGiftRevealTokens.Scene.flapBackFadeDegrees
            )
            : 0

        let drift = reduced
            ? 0
            : EnvelopeGiftRevealTokens.Scene.sweepDrift
                * sin(2 * Double.pi * f.now / EnvelopeGiftRevealTokens.Scene.sweepDriftMs)
                * ambientWeight
        let resting = 0.5 + EnvelopeGiftRevealTokens.Scene.sweepRollFactor * clamped + drift
        if f.after < 0 || reduced {
            sweep = resting
        } else {
            let u = Ease.clamp01(
                (f.after - Reveal.flyMs * EnvelopeGiftRevealTokens.Scene.sweepDelayFactor) / Reveal.sweepMs
            )
            sweep = u < 1
                ? EnvelopeGiftRevealTokens.Scene.sweepStart
                    + EnvelopeGiftRevealTokens.Scene.sweepLinearTravel * u
                    + (resting - EnvelopeGiftRevealTokens.Scene.sweepCompletedPosition) * u * u
                : resting
        }
        if f.after < 0 || reduced {
            amount = EnvelopeGiftRevealCopy.amount
        } else {
            let u = Ease.clamp01(
                (f.after - Reveal.flyMs * EnvelopeGiftRevealTokens.Scene.countDelayFactor) / Reveal.countMs
            )
            let eased = 1 - pow(1 - u, 3)
            amount = "$\(Int((EnvelopeGiftRevealCopy.value * eased).rounded()))"
        }

        let out = resetting ? 0 : Ease.clamp01((f.p - Open.flapShare) / (1 - Open.flapShare))
        let insideY = lay.envTop + Open.settlePt * Ease.clamp01(f.p / Open.flapShare) * (resetting ? 0 : 1)
            + EnvelopeGiftRevealTokens.Scene.cardInsideTop + (M.cardH * M.cardInScale) / 2 - out * Open.cardRise
        let finalY = lay.cardTop + M.cardH / 2
        // A small extra lift mid-flight so the card arcs instead of sliding.
        let arc = -EnvelopeGiftRevealTokens.Scene.cardArc * sin(Double.pi * f.fly)
        cardY = insideY + (finalY - insideY) * f.fly + arc
        cardScale = M.cardInScale + (1 - M.cardInScale) * Ease.inOutCubic(f.fly)
        cardTilt = f.tilt0 * (1 - f.fly)
        cardFront = f.after >= 0 && f.reset == 0
        cardFrontOpacity = cardFront ? 1 - f.reset : 0
        cardInsideOpacity = cardFront ? 0 : 1
    }

    private static func pageColour(_ t: Double) -> Color {
        let gamma = EnvelopeGiftRevealTokens.Scene.pageColorGamma
        let lime = EnvelopeGiftRevealColors.limeRGB
        let white = EnvelopeGiftRevealColors.whiteRGB
        func channel(_ i: Int) -> Double {
            let a = pow(lime[i] / 255, gamma)
            let b = pow(white[i] / 255, gamma)
            return (pow(a + (b - a) * t, 1 / gamma) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(0), green: channel(1), blue: channel(2), opacity: 1)
    }
}
