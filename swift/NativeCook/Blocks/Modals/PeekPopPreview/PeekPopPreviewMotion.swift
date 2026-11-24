import Foundation

/// Every number the peek and the pop use, the same as React Native's `motion.ts`.
///
/// State (all 0…1): `press` (the finger is down on a row), `peek` (row → preview card), `sink` (the preview pulled
/// back toward its row; the card is drawn at peek − sink), `pop` (preview card → full page), `reveal` (the excerpt
/// and the quick actions), `scroll` (points the open page is scrolled).
enum PeekPopPreviewMotion {
    /// Seconds. One autoPlay loop; rest at both ends.
    static let loop: Double = 10.5

    struct Spring {
        let response: Double
        let ratio: Double
        var stiffness: Double { let w = 2 * .pi / response; return w * w }
        var damping: Double { 2 * ratio * 2 * .pi / response }
    }

    static let peekSpring = Spring(response: 0.46, ratio: 0.78)
    static let popSpring = Spring(response: 0.5, ratio: 0.84)
    static let closeSpring = Spring(response: 0.44, ratio: 0.88)

    /// Long press: the row swells for this long, then the peek begins.
    static let press: Double = 0.32
    static let pressScale: Double = 0.03
    /// Once the peek is under way the swell lets go.
    static let pressRelease: (Double, Double) = (0.1, 0.4)

    static let popUpSpan: Double = 240
    static let popDownSpan: Double = 300
    static let popDownRubber: Double = 500
    static let sinkSpan: Double = 280
    static let sinkRubber: Double = 200
    static let popCommit: Double = 0.4
    static let popVelocity: Double = 500
    static let sinkClose: Double = 40
    static let sinkVelocity: Double = 500
    static let dismissPull: Double = 110
    static let dismissVelocity: Double = 600
    /// Closing from the open page: the pop leads and the peek follows this much later, so the card shape shows.
    static let closeFollow: Double = 0.07
    static let scrollRubber: Double = 120

    static let revealIn: Double = 0.6
    static let revealOut: Double = 0.18
    static let revealDelay: Double = 0.1
    static let excerptAt: Double = 0.05
    static let actionAt: [Double] = [0.28, 0.4, 0.52]
    static let revealSpan: Double = 0.48
    static let excerptBlur: Double = 8
    static let actionRise: Double = 12
    static let panelRise: Double = 16
    static let panelScale: Double = 0.94

    static let radiusRow: Double = 18
    static let radiusCard: Double = 28

    static let sceneScale: Double = 0.955
    static let sceneDim: Double = 0.3
    static let sceneBlur: Double = 16

    static let navAt: (Double, Double) = (0.55, 1)
    static let navDrop: Double = 14

    static let resumeAfter: Double = 2
    static let reducedDuration: Double = 0.2

    static let rowA = 1
    static let rowB = 3

    // The loop's events, in seconds.
    static let appearA = 0.5
    static let downA = 0.85
    static let swipeA = 2.55
    static let releaseA = 2.95
    static let scrollStart = 4.0
    static let scrollEnd = 4.75
    static let pullStart = 5.6
    static let releaseD = 6.3
    static let switchRow = 7.0
    static let appearB = 7.05
    static let downB = 7.4
    static let pullB = 8.55
    static let releaseB = 8.8

    static let ghostSwipeUp: Double = 150
    static let ghostScroll: Double = 190
    static let ghostDismiss: Double = 330
    static let ghostSink: Double = 46
    static let ghostSize: Double = 46
    static let ghostFade: Double = 0.18
    static let ghostPress: Double = 0.16

    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double { min(max(value, low), high) }
    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double { from + (to - from) * amount }
    /// Row → preview card → full page.
    static func mix3(_ row: Double, _ card: Double, _ full: Double, _ peek: Double, _ pop: Double) -> Double {
        mix(mix(row, card, peek), full, pop)
    }
    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOutCubic(_ u: Double) -> Double { let k = 1 - u; return 1 - k * k * k }
    static func easeInOutCubic(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    /// Rubber band: follows at first, then resists.
    static func rubber(_ distance: Double, _ limit: Double) -> Double { distance / (1 + distance / limit) }
    static func rubberSlope(_ distance: Double, _ limit: Double) -> Double { let k = 1 + distance / limit; return 1 / (k * k) }

    /// An under-damped spring in closed form: where it is `time` after leaving `from` at `velocity` for `to`.
    static func springAt(_ time: Double, _ from: Double, _ to: Double, _ velocity: Double, _ spring: Spring) -> Double {
        if time <= 0 { return from }
        let w = 2 * .pi / spring.response
        let wd = w * sqrt(1 - spring.ratio * spring.ratio)
        let a = to - from
        let b = (spring.ratio * w * a - velocity) / wd
        return to - exp(-spring.ratio * w * time) * (a * cos(wd * time) + b * sin(wd * time))
    }

    /// The ghost finger's travel 0…1 over a drag: it speeds up, so the release carries velocity.
    static func ghostPull(_ u: Double) -> Double { 0.35 * u + 0.65 * u * u }
    static func ghostPullSpeed(_ u: Double) -> Double { 0.35 + 1.3 * u }

    // MARK: The loop, as functions of its time t. The finger drives the same values with real springs.

    static func scriptRow(_ t: Double) -> Int { t < switchRow ? rowA : rowB }

    private static func pressFrom(_ t: Double, _ down: Double) -> Double {
        let start = down + press
        return easeOutCubic(seg(t, down, start)) * (1 - seg(t, start + pressRelease.0, start + pressRelease.1))
    }

    static func scriptPress(_ t: Double) -> Double { t < switchRow ? pressFrom(t, downA) : pressFrom(t, downB) }

    /// Banded pull on the open page at the moment the ghost lets go.
    private static func dismissPullAt(_ t: Double) -> Double {
        max(0, ghostDismiss * ghostPull(seg(t, pullStart, releaseD)) - ghostScroll)
    }

    static func scriptPop(_ t: Double) -> Double {
        if t < swipeA { return 0 }
        if t < releaseA { return ghostSwipeUp * ghostPull(seg(t, swipeA, releaseA)) / popUpSpan }
        if t < pullStart {
            let from = ghostSwipeUp / popUpSpan
            let velocity = ghostSwipeUp * ghostPullSpeed(1) / (releaseA - swipeA) / popUpSpan
            let value = springAt(t - releaseA, from, 1, velocity, popSpring)
            // The spring's last thousandth is pinned, so the page is exactly open before the next touch.
            return mix(value, 1, seg(t, scrollStart - 0.4, scrollStart - 0.1))
        }
        if t < releaseD { return 1 - rubber(dismissPullAt(t), popDownRubber) / popDownSpan }
        let pull = ghostDismiss - ghostScroll
        let from = 1 - rubber(pull, popDownRubber) / popDownSpan
        let speed = ghostDismiss * ghostPullSpeed(1) / (releaseD - pullStart)
        let velocity = -speed * rubberSlope(pull, popDownRubber) / popDownSpan
        return springAt(t - releaseD, from, 0, velocity, closeSpring) * (1 - seg(t, switchRow - 0.15, switchRow))
    }

    /// Peek minus sink: how far the row has become the preview card.
    static func scriptPeek(_ t: Double) -> Double {
        if t < switchRow {
            let start = downA + press
            if t < start { return 0 }
            let close = releaseD + closeFollow
            if t < close {
                return mix(springAt(t - start, 0, 1, 0, peekSpring), 1, seg(t, swipeA - 0.4, swipeA - 0.1))
            }
            return springAt(t - close, 1, 0, 0, closeSpring) * (1 - seg(t, switchRow - 0.15, switchRow))
        }
        let start = downB + press
        if t < start { return 0 }
        if t < pullB {
            return mix(springAt(t - start, 0, 1, 0, peekSpring), 1, seg(t, pullB - 0.4, pullB - 0.1))
        }
        if t < releaseB { return 1 - rubber(ghostSink * ghostPull(seg(t, pullB, releaseB)), sinkRubber) / sinkSpan }
        let from = 1 - rubber(ghostSink, sinkRubber) / sinkSpan
        let speed = ghostSink * ghostPullSpeed(1) / (releaseB - pullB)
        let velocity = -speed * rubberSlope(ghostSink, sinkRubber) / sinkSpan
        return springAt(t - releaseB, from, 0, velocity, closeSpring) * (1 - seg(t, loop - 1.05, loop - 0.9))
    }

    static func scriptReveal(_ t: Double) -> Double {
        if t < switchRow {
            let on = downA + press + revealDelay
            return seg(t, on, on + revealIn) * (1 - seg(t, releaseD, releaseD + revealOut))
        }
        let on = downB + press + revealDelay
        return seg(t, on, on + revealIn) * (1 - seg(t, releaseB, releaseB + revealOut))
    }

    /// Points the open page is scrolled.
    static func scriptScroll(_ t: Double) -> Double {
        if t < scrollStart { return 0 }
        if t < pullStart { return ghostScroll * easeInOutCubic(seg(t, scrollStart, scrollEnd)) }
        return max(0, ghostScroll - ghostDismiss * ghostPull(seg(t, pullStart, releaseD)))
    }

    /// The ghost finger at loop time t. a and b are where it lands on rows A and B, s where it starts to scroll,
    /// d where it starts the pull down.
    static func ghostAt(_ t: Double, a: CGPoint, b: CGPoint, s: CGPoint, d: CGPoint) -> (x: Double, y: Double, shown: Double, pressed: Double) {
        if t < releaseA + 0.45 {
            let shown = seg(t, appearA, appearA + ghostFade) * (1 - seg(t, releaseA, releaseA + ghostFade))
            return (a.x, a.y - ghostSwipeUp * ghostPull(seg(t, swipeA, releaseA)), shown, seg(t, downA - 0.08, downA))
        }
        if t < scrollEnd + 0.4 {
            let shown = seg(t, scrollStart - 0.3, scrollStart - 0.12) * (1 - seg(t, scrollEnd + 0.05, scrollEnd + 0.05 + ghostFade))
            return (s.x, s.y - ghostScroll * easeInOutCubic(seg(t, scrollStart, scrollEnd)), shown, seg(t, scrollStart - 0.1, scrollStart))
        }
        if t < releaseD + 0.45 {
            let shown = seg(t, pullStart - 0.3, pullStart - 0.12) * (1 - seg(t, releaseD, releaseD + ghostFade))
            return (d.x, d.y + ghostDismiss * ghostPull(seg(t, pullStart, releaseD)), shown, seg(t, pullStart - 0.1, pullStart))
        }
        let shown = seg(t, appearB, appearB + ghostFade) * (1 - seg(t, releaseB, releaseB + ghostFade))
        return (b.x, b.y + ghostSink * ghostPull(seg(t, pullB, releaseB)), shown, seg(t, downB - 0.08, downB))
    }
}
