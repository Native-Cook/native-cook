import Foundation

private typealias Tokens = EnvelopeGiftRevealTokens

struct EnvelopeGiftRevealSpringConfig {
    let stiffness: Double
    let damping: Double
    let mass: Double
}

enum EnvelopeGiftRevealOpen {
    static let travel = Tokens.Open.travel
    static let commit = Tokens.Open.commit
    static let flapShare = Tokens.Open.flapShare
    static let cardRise = Tokens.Open.cardRise
    static let tiltMax = Tokens.Open.tiltMax
    static let breathDeg = Tokens.Open.breathDeg
    static let breathMs = Tokens.Open.breathMs
    static let sealSplitDeg = Tokens.Open.sealSplitDeg
    static let settlePt = Tokens.Open.settlePt
    static let pocketApex = Tokens.Open.pocketApex
    static let curlDeg = Tokens.Open.curlDeg
}

enum EnvelopeGiftRevealSprings {
    private static func config(_ values: (stiffness: Double, damping: Double, mass: Double)) -> EnvelopeGiftRevealSpringConfig {
        EnvelopeGiftRevealSpringConfig(
            stiffness: values.stiffness,
            damping: values.damping,
            mass: values.mass
        )
    }

    static let close = config(Tokens.Springs.close)
    static let finish = config(Tokens.Springs.finish)
    static let press = config(Tokens.Springs.press)
}

enum EnvelopeGiftRevealReveal {
    static let finishMs = Tokens.Reveal.finishMs
    static let flyMs = Tokens.Reveal.flyMs
    static let flyEase = Tokens.Reveal.flyEase
    static let envelopeDropMs = Tokens.Reveal.envelopeDropMs
    static let pageMs = Tokens.Reveal.pageMs
    static let textDelay = Tokens.Reveal.textDelay
    static let stagger = Tokens.Reveal.stagger
    static let confetti = Tokens.Reveal.confetti
    static let confettiLife = Tokens.Reveal.confettiLife
    static let gravity = Tokens.Reveal.gravity
    static let drag = Tokens.Reveal.drag
    static let buttonOpenMs = Tokens.Reveal.buttonOpenMs
    static let sweepMs = Tokens.Reveal.sweepMs
    static let countMs = Tokens.Reveal.countMs
}

enum EnvelopeGiftRevealReset {
    static let fadeMs = Tokens.Reset.fadeMs
}

enum EnvelopeGiftRevealHeadlineMotion {
    static let stagger = Tokens.Headline.stagger
    static let span = Tokens.Headline.span
    static let drop = Tokens.Headline.drop
}

enum EnvelopeGiftRevealAmbient {
    static let floatPt = Tokens.Ambient.floatPt
    static let floatMs = Tokens.Ambient.floatMs
    static let glowMs = Tokens.Ambient.glowMs
    static let glowAmount = Tokens.Ambient.glowAmount
}

enum EnvelopeGiftRevealMotion {
    enum Polish {
        static let easeOut = Tokens.Polish.easeOut
        static let pressScale = Tokens.Polish.pressScale
        static let pressInMs = Tokens.Polish.pressInMs
        static let pressOutMs = Tokens.Polish.pressOutMs
        static let textRise = Tokens.Polish.textRise
        static let finalBaseDelay = Tokens.Polish.finalBaseDelayMs
        static let finalDuration = Tokens.Polish.finalDurationMs
    }

    static let reducedMs = Tokens.reducedMs
    static let loopMs = Tokens.loopMs
    static let sourceResumeAfterMs = Tokens.sourceResumeAfterMs
    static let resumeAfterMs = Tokens.touchResumeAfterMs
    static let qaHoldAt = Tokens.qaHoldAt
}

enum EnvelopeGiftRevealScript {
    static let down = Tokens.Script.down
    static let up = Tokens.Script.up
    static let rise = Tokens.Script.rise
    static let reset = Tokens.Script.reset
    static let fingerOffset = Tokens.Script.fingerOffset
    static let xOffset = Tokens.Script.fingerXOffset
    static let leadMs = Tokens.Script.easingLeadMs
    static let tailMs = Tokens.Script.easingTailMs
    static let lateral = Tokens.Script.lateralDrift
}

enum EnvelopeGiftRevealEase {
    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    static func out(_ value: Double) -> Double {
        let t = clamp01(value)
        return 1 - pow(1 - t, 3)
    }

    static func smoothstep(_ value: Double) -> Double {
        let t = clamp01(value)
        return t * t * (3 - 2 * t)
    }

    static func inOutQuad(_ value: Double) -> Double {
        let t = clamp01(value)
        return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    static func inOutCubic(_ value: Double) -> Double {
        let t = clamp01(value)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func cubicBezier(_ value: Double, _ curve: (x1: Double, y1: Double, x2: Double, y2: Double)) -> Double {
        let target = clamp01(value)
        var lo = 0.0
        var hi = 1.0
        var u = target
        for _ in 0..<12 {
            u = (lo + hi) / 2
            let one = 1 - u
            let x = 3 * one * one * u * curve.x1 + 3 * one * u * u * curve.x2 + u * u * u
            if x < target { lo = u } else { hi = u }
        }
        let one = 1 - u
        return 3 * one * one * u * curve.y1 + 3 * one * u * u * curve.y2 + u * u * u
    }

    static func fly(_ value: Double) -> Double {
        cubicBezier(value, EnvelopeGiftRevealReveal.flyEase)
    }

    static func polishOut(_ value: Double) -> Double {
        cubicBezier(value, EnvelopeGiftRevealMotion.Polish.easeOut)
    }

    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double {
        from + (to - from) * amount
    }
}

fileprivate typealias Open = EnvelopeGiftRevealOpen
fileprivate typealias Reveal = EnvelopeGiftRevealReveal
typealias EnvelopeGiftRevealHeadline = EnvelopeGiftRevealHeadlineMotion
