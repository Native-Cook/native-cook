import SwiftUI

/// The numbers and curves of the Spectrum News Carousel timeline: `motion.ts` plus the top of
/// `SpectrumNewsCarousel.tsx` in the React Native block. Every function of the loop time `t` here is a
/// pure function, which is what lets `previewTime` freeze any frame.
enum SpectrumNewsCarouselMotion {
    /// `LOOP`: one period of the autoplay timeline, in seconds.
    static let loop: Double = 22

    /// `ANCHORS`: the loop time at which each page is at rest. After a touch the loop resumes from
    /// the anchor of the page that was reached.
    static let anchors: [Double] = [2.5, 7.5, 10, 11.75, 14]

    /// The number of pages (`PAGES.length`).
    static let pageCount = 5

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

    /// The normalised positions sampled from the source's 60 fps swipes (`sourceSwipe`).
    private static let swipeTimes: [Double] = [0, 0.033, 0.05, 0.067, 0.1, 0.133, 0.167, 0.2, 0.233, 0.267, 0.3, 0.333, 0.383]
    private static let swipeValues: [Double] = [0, 0.104, 0.13, 0.17, 0.333, 0.51, 0.672, 0.791, 0.876, 0.931, 0.963, 0.985, 1]

    /// The three reverse swipes of `timelinePage`, each rising from 0 to 1.
    private static let backOneTimes: [Double] = [16.2, 16.25, 16.3, 16.35, 16.4, 16.45, 16.5, 16.55, 16.65]
    private static let backOneValues: [Double] = [0, 0.093, 0.304, 0.563, 0.77, 0.893, 0.959, 0.989, 1]
    private static let backTwoTimes: [Double] = [17.1, 17.167, 17.25, 17.5, 17.75, 18, 18.05, 18.1, 18.15, 18.2, 18.25, 18.3, 18.35, 18.45]
    private static let backTwoValues: [Double] = [0, 0.026, 0.059, 0.119, 0.2, 0.252, 0.281, 0.33, 0.46, 0.667, 0.822, 0.911, 0.963, 1]
    private static let backThreeTimes: [Double] = [19, 19.083, 19.167, 19.3, 19.5, 19.75, 20, 20.05, 20.1, 20.15, 20.2, 20.25, 20.3, 20.4]
    private static let backThreeValues: [Double] = [0, 0.011, 0.043, 0.074, 0.119, 0.23, 0.319, 0.47, 0.574, 0.707, 0.844, 0.93, 0.974, 1]

    /// `sourceSwipe`: one forward swipe that starts at `start` seconds, from 0 to 1.
    private static func sourceSwipe(_ time: Double, _ start: Double) -> Double {
        interpolate(time - start, swipeTimes, swipeValues)
    }

    /// `timelinePage`: the (fractional) page position at loop time `t`. Four forward swipes, then
    /// three backward ones, so the loop ends on page 0 again.
    static func timelinePage(_ t: Double) -> Double {
        let forward = sourceSwipe(t, 6.45) + sourceSwipe(t, 8.7) + sourceSwipe(t, 11.117) + sourceSwipe(t, 12.833)
        let firstBack = sourceSwipe(t, 15.4)
        let secondBack = interpolate(t, backOneTimes, backOneValues)
        let thirdBack = interpolate(t, backTwoTimes, backTwoValues)
        let fourthBack = interpolate(t, backThreeTimes, backThreeValues)
        return forward - firstBack - secondBack - thirdBack - fourthBack
    }

    // MARK: Welcome window

    /// The welcome card as fractions of the window (`WelcomeFrame`): it grows from a small rounded
    /// window to the whole screen.
    struct WelcomeFrame {
        var x: Double
        var y: Double
        var width: Double
        var height: Double

        /// The whole screen: what every state other than the autoplay timeline shows.
        static let full = WelcomeFrame(x: 0, y: 0, width: 1, height: 1)
    }

    private static let windowKnots: [Double] = [0, 0.75, 1, 1.25, 1.5, 1.75]
    private static let windowX: [Double] = [0.39, 0.3593, 0.2185, 0.0926, 0.0259, 0]
    private static let windowY: [Double] = [0.2621, 0.2586, 0.1575, 0.0634, 0.0274, 0]
    private static let windowWidth: [Double] = [0.22, 0.2815, 0.563, 0.8148, 0.9481, 1]
    private static let windowHeight: [Double] = [0.13, 0.1575, 0.4846, 0.7705, 0.9264, 1]

    /// `welcomeFrame` on the timeline: open by 1.75 s, and over 21.4 to 22 s it closes again to the
    /// first frame so the loop ends where it began.
    static func welcomeFrame(at t: Double) -> WelcomeFrame {
        let sample = t > 21.4 ? interpolate(t, [21.4, 22], [1.75, 0]) : t
        return WelcomeFrame(
            x: interpolate(sample, windowKnots, windowX),
            y: interpolate(sample, windowKnots, windowY),
            width: interpolate(sample, windowKnots, windowWidth),
            height: interpolate(sample, windowKnots, windowHeight)
        )
    }

    /// `welcomeGlow` on the timeline: the warm field fades in at 0.6 to 0.75 s and out at 21.6 to 22 s.
    static func welcomeGlow(at t: Double) -> Double {
        enter(segment(t, 0.6, 0.75)) * (1 - enter(segment(t, 21.6, 22)))
    }

    /// The first page's own fade in (0 to 0.5 s) and out (21.6 to 22 s) on the timeline.
    static func introOpacity(at t: Double) -> Double {
        enter(segment(t, 0, 0.5)) * (1 - enter(segment(t, 21.6, 22)))
    }
}
