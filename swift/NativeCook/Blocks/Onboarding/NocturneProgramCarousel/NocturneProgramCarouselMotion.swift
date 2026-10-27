import SwiftUI

/// The numbers and curves of the Nocturne Program Carousel timeline: `motion.ts` plus the top of
/// `NocturneProgramCarousel.tsx` and the shared maths of `NocturneArtwork.tsx`. Every function of the
/// loop time `t` here is a pure function, which is what lets `previewTime` freeze any frame.
enum NocturneProgramCarouselMotion {
    /// `LOOP`: one period of the autoplay timeline, in seconds (a 15 s three-part story and two
    /// seconds that close it).
    static let loop: Double = 17

    /// `ANCHORS`: the loop time at which each page is at rest. After a touch the loop resumes from
    /// the anchor of the page that was reached.
    static let anchors: [Double] = [4.3, 8.4, 12]

    /// `PAGES.length`.
    static let pageCount = 3

    // MARK: Small helpers

    /// `clamp`.
    static func clamp(_ value: Double, _ lower: Double = 0, _ upper: Double = 1) -> Double {
        min(upper, max(lower, value))
    }

    /// `segment`: where `time` lies between `start` and `end`, clamped to 0...1.
    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp((time - start) / (end - start))
    }

    /// JavaScript's `Math.round`: halves go up, also below zero.
    static func jsRound(_ value: Double) -> Double {
        floor(value + 0.5)
    }

    /// `enterCurve` of `motion.ts`: `Easing.bezierFn(.23, 1, .32, 1)`.
    private static let enterCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    /// `enter`.
    static func enter(_ value: Double) -> Double {
        enterCurve.value(at: clamp(value))
    }

    /// `rubber`: the finger's page position, stretched past either end of the pages.
    static func rubber(_ value: Double, max limit: Double) -> Double {
        if value < 0 {
            return value / (1 + abs(value) * 4)
        }
        if value > limit {
            return limit + (value - limit) / (1 + (value - limit) * 4)
        }
        return value
    }

    /// Reanimated's `interpolate(x, input, output, 'clamp')` for a rising `input`.
    static func interpolate(_ x: Double, _ input: [Double], _ output: [Double]) -> Double {
        guard input.count > 1, input.count == output.count else { return output.first ?? 0 }
        if x <= input[0] {
            return output[0]
        }
        let last = input.count - 1
        if x >= input[last] {
            return output[last]
        }
        var upper = 1
        while upper < last && x > input[upper] {
            upper += 1
        }
        let span = input[upper] - input[upper - 1]
        let fraction = span == 0 ? 0 : (x - input[upper - 1]) / span
        return output[upper - 1] + (output[upper] - output[upper - 1]) * fraction
    }

    // MARK: Page position over the loop

    /// `sourceCardSlide`'s edge samples: an incoming card's position 0 to 1 over 0.683 s, including
    /// the long settling tail observed at 60 fps.
    private static let slideTimes: [Double] = [0, 0.016, 0.033, 0.083, 0.116, 0.133, 0.15, 0.183, 0.25, 0.283, 0.35, 0.466, 0.683]
    private static let slideValues: [Double] = [0, 0.033, 0.104, 0.278, 0.481, 0.566, 0.632, 0.736, 0.868, 0.906, 0.953, 0.991, 1]

    /// `sourceCardSlide`: one card slide that starts at `start` seconds, from 0 to 1.
    private static func sourceCardSlide(_ time: Double, _ start: Double) -> Double {
        interpolate(time - start, slideTimes, slideValues)
    }

    /// `timelinePage`: the (fractional) page position at loop time `t`. Two slides, then the
    /// program fades out at 15.5 to 16.1 s and the position is back on page 0 for the next loop.
    static func timelinePage(_ t: Double) -> Double {
        if t >= 16.1 {
            return 0
        }
        return sourceCardSlide(t, 4.817) + sourceCardSlide(t, 9.834)
    }

    // MARK: Cards

    /// `CardFrame`: neighbours of the centred card stand back a little (down to 0.86) ...
    static func cardScale(distance: Double, reduced: Bool) -> Double {
        reduced ? 1 : 1 - 0.14 * clamp(abs(distance))
    }

    /// ... and turn toward the centre, up to 9 degrees about the vertical axis.
    static func cardAngle(distance: Double, reduced: Bool) -> Double {
        reduced ? 0 : clamp(distance, -1, 1) * -9
    }

    /// `CardFrame`'s shade: cards waiting at the edges sink into the night.
    static func cardShade(distance: Double) -> Double {
        0.58 * clamp(abs(distance))
    }

    /// `CardCopy`'s reveal samples (copy of the second and third card comes out of a soft focus).
    private static let copyTimes: [Double] = [5, 5.05, 5.1, 5.15, 5.2, 5.25, 5.3, 5.4]
    private static let copyValues: [Double] = [0, 0.117, 0.224, 0.384, 0.581, 0.733, 0.905, 1]

    /// `CardCopy`'s `shown`: 1 for the first card and under Reduce Motion.
    static func copyShown(t: Double, index: Int, reduced: Bool) -> Double {
        if reduced || index == 0 {
            return 1
        }
        let local = t - (index == 2 ? 5.017 : 0)
        return interpolate(local, copyTimes, copyValues)
    }

    // MARK: Welcome and program fades

    /// `welcomeStyle` opacity on the timeline.
    static func welcomeOpacity(t: Double) -> Double {
        enter(segment(t, 0, 0.5)) * (1 - enter(segment(t, 1.5, 2)))
    }

    /// `welcomeStyle` scale on the timeline.
    static func welcomeScale(t: Double) -> Double {
        1 + 0.06 * (1 - enter(segment(t, 0, 0.5)))
    }

    /// `programStyle` opacity on the timeline.
    static func programOpacity(t: Double) -> Double {
        enter(segment(t, 1.5, 2.25)) * (1 - enter(segment(t, 15.5, 16.1)))
    }

    /// The part of the loop in which the program is on screen (`programVisible`).
    static func programShows(t: Double) -> Bool {
        t >= 2.25 && t < 15.5
    }

    // MARK: Spring of a swipe

    /// `withSpring(target, { duration: 400, dampingRatio: .8 })`. Reanimated 4.5.1 reads `duration`
    /// as perceptual and solves the stiffness so that the spring settles after 1.5 x the duration
    /// (`calculateNewStiffnessToMatchDuration`, mass 1): for 400 ms and a ratio of 0.8 that is a
    /// stiffness of 653.4. The damping is 2 x ratio x sqrt(stiffness), so the angular frequency is
    /// sqrt(stiffness) and the damping ratio is the configured 0.8.
    private static let stiffness = 653.4
    private static let omega0 = stiffness.squareRoot()
    private static let zeta = 0.8

    /// After this long the spring has settled (its envelope, e^(-0.8 x 25.56 x t), is below 1e-8 of
    /// the distance), so it is taken as having reached its target and the timeline may pause.
    static let springSettle: Double = 1.0

    /// The position of the swipe spring `time` seconds after it was set off from `from` towards
    /// `target` with `velocity` (pages per second): the closed form of the under-damped oscillator
    /// that Reanimated integrates for `withSpring`.
    static func springValue(from: Double, target: Double, velocity: Double, time: Double) -> Double {
        if time >= springSettle {
            return target
        }
        let omega1 = omega0 * (1 - zeta * zeta).squareRoot()
        let displacement = from - target
        let envelope = exp(-zeta * omega0 * time)
        let cosine = cos(omega1 * time)
        let sine = sin(omega1 * time)
        let sineWeight = (velocity + zeta * omega0 * displacement) / omega1
        return target + envelope * (displacement * cosine + sineWeight * sine)
    }
}
