import SwiftUI

/// Everything the bar, the button and the pill do is a function of one number, the pose: 0 with the
/// button resting in its bowl, 1 with the pill open. It moves at a constant speed, so the numbers
/// below are fractions of `duration` as well as of the way.
enum LiquidActionPillMotion {
    /// Seconds of one autoPlay loop.
    static let loop = 9.0
    static let openAt = 1.0
    static let closeAt = 4.0
    static let profileAt = 6.0
    static let homeAt = 7.5
    /// Seconds after a touch before the loop starts again.
    static let userHold = 3.0

    /// Seconds from the button to the open pill, and back.
    static let duration = 1.0
    /// The same under reduced motion, where it is a fade out and a fade in.
    static let reducedDuration = 0.3
    /// Seconds the bowl takes to change over when the pill is turned round half way.
    static let turn = 0.15
    /// Seconds of the cross-fade between the pages.
    static let tabFade = 0.25

    static let barHeight = 70.0
    static let barRadius = 26.0
    static let barMargin = 24.0
    /// The least room under the bar; a larger safe area replaces it.
    static let barBottom = 34.0
    /// From the edges of the bar to the middle of its tabs.
    static let tabInset = 54.0

    /// The bowl in the bar's top edge: half its width, its depth, and how much deeper a press makes it.
    static let bowlHalf = 54.0
    static let bowlDepth = 28.0
    static let bowlPress = 5.0
    /// The bump the edge throws up after the button lets go of it.
    static let peak = -7.0
    static let peakHalf = 26.0
    /// Where the control points of the bowl's two curves sit, as fractions of its half width.
    static let bowlShoulder = 0.66
    static let bowlFloor = 0.46
    /// A quarter circle as a cubic curve.
    static let kappa = 0.5523

    static let button = 52.0
    /// The button's middle above the bar's top edge at rest.
    static let buttonRaise = 6.0
    /// How far a press pushes the button into the bowl, and how far it rises from rest to the pill's place.
    static let pressSink = 6.0
    static let rise = 34.0
    static let pillWidth = 232.0
    /// How much thinner the pill gets while it stretches.
    static let squeeze = 4.0
    static let iconGap = 54.0
    static let icon = 20.0
    static let tabIcon = 24.0
    /// Half an arm of the plus, and the radius of the dot it shrinks into and of the four it splits into.
    static let plusArm = 7.0
    static let seed = 5.0
    static let dot = 6.0
    /// Two dots stay joined until their middles are this far apart.
    static let neckSnap = 34.0
    /// Radians from the line between two dots to where a neck leaves them, when they touch.
    static let neckSpread = 1.3

    // The opening, in fractions of the way.
    static let sinkEnd = 0.2
    static let riseEnd = 0.45
    static let peakAt = 0.34
    static let flatAt = 0.52
    static let plusGone = 0.38
    static let seedFrom = 0.24
    static let seedFull = 0.42
    static let stretchFrom = 0.42
    static let stretchEnd = 0.74
    static let pairFrom = 0.46
    static let pairEnd = 0.72
    static let splitFrom = 0.54
    static let splitEnd = 0.8
    static let iconsFrom = 0.74
    static let iconStagger = 0.04
    static let iconFor = 0.14
    /// How far into its fall the button is when the bowl has opened under it again.
    static let reform = 0.36

    private static let back = 1.70158

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    /// How far `p` is from `a` to `b`, 0 to 1.
    static func span(_ p: Double, _ a: Double, _ b: Double) -> Double { clamp01((p - a) / (b - a)) }

    static func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    /// Leaves fast, passes the end by a tenth and comes back to it.
    static func backOut(_ t: Double) -> Double {
        1 + (back + 1) * (t - 1) * (t - 1) * (t - 1) + back * (t - 1) * (t - 1)
    }

    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// The button's middle below its resting place: a press down, then the jump up to the pill's place.
    static func lift(_ p: Double) -> Double {
        if p < sinkEnd { return pressSink * smooth(p / sinkEnd) }
        return pressSink - (pressSink + rise) * backOut(span(p, sinkEnd, riseEnd))
    }

    /// The bowl's depth. `opening` is 1 on the way up, where the edge follows the button, overshoots
    /// into a bump and lies flat, and 0 on the way down, where the bowl opens just ahead of the button.
    static func bowlDepth(at p: Double, opening: Double) -> Double {
        let pressed = bowlDepth + bowlPress
        if p < sinkEnd { return bowlDepth + bowlPress * smooth(p / sinkEnd) }
        let up = p < peakAt ? mix(pressed, peak, easeOut(span(p, sinkEnd, peakAt))) : mix(peak, 0, smooth(span(p, peakAt, flatAt)))
        let down = pressed * (1 - smooth(span(span(p, sinkEnd, riseEnd), 0, reform)))
        return mix(down, up, opening)
    }

    static func bowlHalf(at p: Double, opening: Double) -> Double {
        mix(bowlHalf, mix(bowlHalf, peakHalf, smooth(span(p, sinkEnd, peakAt))), opening)
    }

    static func pillWidth(at p: Double) -> Double {
        mix(button, pillWidth, smooth(span(p, stretchFrom, stretchEnd)))
    }

    static func pillHeight(at p: Double) -> Double {
        button - squeeze * sin(.pi * span(p, stretchFrom, stretchEnd))
    }

    /// The plus: 1 whole, 0 gone into the dot.
    static func plusScale(_ p: Double) -> Double { 1 - smooth(span(p, sinkEnd, plusGone)) }

    static func dotRadius(_ p: Double) -> Double {
        mix(seed * easeOut(span(p, seedFrom, seedFull)), dot, span(p, pairFrom, splitFrom))
    }

    /// Where dot `index` is, from the middle: one dot parts into two halves and each half into two.
    static func dotX(_ p: Double, _ index: Int) -> Double {
        let half = iconGap * smooth(span(p, pairFrom, pairEnd))
        let quarter = (iconGap / 2) * smooth(span(p, splitFrom, splitEnd))
        return (index < 2 ? -half : half) + (index % 2 == 0 ? -quarter : quarter)
    }

    /// How far dot `index` has turned into its icon.
    static func iconIn(_ p: Double, _ index: Int) -> Double {
        let from = iconsFrom + iconStagger * Double(index)
        return smooth(span(p, from, from + iconFor))
    }

    /// The bar's outline around the middle of its top edge, with the bowl in that edge.
    static func bar(width: Double, depth: Double, half: Double) -> Path {
        let w = width / 2
        let r = barRadius
        let k = r * kappa
        let h = barHeight
        var path = Path()
        path.move(to: CGPoint(x: -w + r, y: 0))
        path.addLine(to: CGPoint(x: -half, y: 0))
        path.addCurve(to: CGPoint(x: 0, y: depth), control1: CGPoint(x: -half * bowlShoulder, y: 0), control2: CGPoint(x: -half * bowlFloor, y: depth))
        path.addCurve(to: CGPoint(x: half, y: 0), control1: CGPoint(x: half * bowlFloor, y: depth), control2: CGPoint(x: half * bowlShoulder, y: 0))
        path.addLine(to: CGPoint(x: w - r, y: 0))
        path.addCurve(to: CGPoint(x: w, y: r), control1: CGPoint(x: w - r + k, y: 0), control2: CGPoint(x: w, y: r - k))
        path.addLine(to: CGPoint(x: w, y: h - r))
        path.addCurve(to: CGPoint(x: w - r, y: h), control1: CGPoint(x: w, y: h - r + k), control2: CGPoint(x: w - r + k, y: h))
        path.addLine(to: CGPoint(x: -w + r, y: h))
        path.addCurve(to: CGPoint(x: -w, y: h - r), control1: CGPoint(x: -w + r - k, y: h), control2: CGPoint(x: -w, y: h - r + k))
        path.addLine(to: CGPoint(x: -w, y: r))
        path.addCurve(to: CGPoint(x: -w + r, y: 0), control1: CGPoint(x: -w, y: r - k), control2: CGPoint(x: -w + r - k, y: 0))
        path.closeSubpath()
        return path
    }

    /// What joins two dots that have not parted far: necks that thin as they move apart, and snap.
    static func necks(_ p: Double) -> Path {
        let r = dotRadius(p)
        var path = Path()
        for i in 0..<3 {
            let from = dotX(p, i)
            let to = dotX(p, i + 1)
            let gap = to - from
            if gap <= 0.5 || gap >= neckSnap { continue }
            let joined = 1 - gap / neckSnap
            let x = r * cos(neckSpread * joined)
            let y = r * sin(neckSpread * joined)
            // The curve's control point, for a waist `joined` times as thick as the neck's ends.
            let c = y * (2 * joined - 1)
            let middle = (from + to) / 2
            path.move(to: CGPoint(x: from + x, y: -y))
            path.addQuadCurve(to: CGPoint(x: to - x, y: -y), control: CGPoint(x: middle, y: -c))
            path.addLine(to: CGPoint(x: to - x, y: y))
            path.addQuadCurve(to: CGPoint(x: from + x, y: y), control: CGPoint(x: middle, y: c))
            path.closeSubpath()
        }
        return path
    }
}
