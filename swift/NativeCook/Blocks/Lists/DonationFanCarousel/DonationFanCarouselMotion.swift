import Foundation

/// The cues, windows and curves of the React Native block's `motion.ts`. Times are milliseconds on
/// the loop clock; the functions are pure, like the worklets they come from.
enum DonationFanCarouselMotion {
    /// `LOOP_MS`.
    static let loopMs = 5366.0
    /// `RESUME_MS`: how long after the last touch the loop starts again from rest.
    static let resumeMs = 3500.0

    /// `cue`.
    enum Cue {
        static let firstCoinIn = 700.0
        static let firstCoinOut = 1480.0
        static let advanceStart = 980.0
        static let advanceEnd = 1680.0
        static let heartsIn = 1880.0
        static let heartsOut = 3150.0
        static let secondCoinIn = 3380.0
        static let secondCoinOut = 4320.0
        static let returnStart = 3670.0
        static let returnEnd = 4460.0
    }

    /// `snap`: the spring that settles the fan when a drag is released.
    static let snapDurationMs = 430.0
    static let snapDampingRatio = 0.86
    /// `projectSeconds`: how far ahead of the release the velocity is projected.
    static let projectSeconds = 0.14
    /// `dragPointsPerPage`: points of drag per donor, at stage scale 1.
    static let dragPointsPerPage = 112.0

    /// The loop time wrapped into [0, LOOP_MS): `((input % LOOP_MS) + LOOP_MS) % LOOP_MS`.
    static func wrapped(_ input: Double) -> Double {
        (input.truncatingRemainder(dividingBy: loopMs) + loopMs).truncatingRemainder(dividingBy: loopMs)
    }

    /// `smoother`: the quintic ease (6x^5 - 15x^4 + 10x^3) of a clamped input.
    static func smoother(_ value: Double) -> Double {
        let x = DonationFanCarouselMath.clamp01(value)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    /// `window`: 0 before `start`, 1 between the two edges, 0 again after `end`.
    static func window(_ t: Double, start: Double, end: Double, edge: Double = 130) -> Double {
        smoother((t - start) / edge) * (1 - smoother((t - (end - edge)) / edge))
    }

    /// `pageAt`: the fan position at loop time `input`, 0...3.
    static func pageAt(_ input: Double) -> Double {
        let t = wrapped(input)
        if t < Cue.advanceStart { return 0 }
        if t < Cue.advanceEnd {
            return 3 * smoother((t - Cue.advanceStart) / (Cue.advanceEnd - Cue.advanceStart))
        }
        if t < Cue.returnStart { return 3 }
        if t < Cue.returnEnd {
            return 3 * (1 - smoother((t - Cue.returnStart) / (Cue.returnEnd - Cue.returnStart)))
        }
        return 0
    }

    /// `coinAt`: how present the contribution coin is, 0...1.
    static func coinAt(_ input: Double) -> Double {
        let t = wrapped(input)
        return max(
            window(t, start: Cue.firstCoinIn, end: Cue.firstCoinOut, edge: 100),
            window(t, start: Cue.secondCoinIn, end: Cue.secondCoinOut, edge: 110)
        )
    }

    /// `heartsAt`: how present the hearts are, 0...1.
    static func heartsAt(_ input: Double) -> Double {
        let t = wrapped(input)
        return window(t, start: Cue.heartsIn, end: Cue.heartsOut, edge: 180)
    }

    /// `borderAt`: the lime keyline is hidden while the fan is travelling, otherwise 1.
    static func borderAt(_ input: Double) -> Double {
        let t = wrapped(input)
        let advancing = t >= Cue.advanceStart && t <= Cue.advanceEnd
        let returning = t >= Cue.returnStart && t <= Cue.returnEnd
        return advancing || returning ? 0 : 1
    }
}
