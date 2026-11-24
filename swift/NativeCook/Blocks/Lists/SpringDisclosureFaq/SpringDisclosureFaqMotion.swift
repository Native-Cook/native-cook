import Foundation

// Spring Disclosure FAQ: the film and the spring, as pure functions (a port of faqMotion.ts).
//
// Every row has one number, `open` (0 closed, 1 open, a little past either while the spring
// overshoots). Everything on screen is drawn from the six `open` values sampled at a time:
//   - a row's answer is revealed by the rows below sliding down by H * open,
//   - each row below samples the same value followLag later than the row above it,
//   - line k of an answer samples it lineStagger * k later (the lines rise one after another),
//   - the "+" turns into a "−" with it.
// The film is a sum of spring steps at fixed times, so it is a function of the loop time alone and
// the frame at `loop` equals the frame at 0. A finger uses the same spring from wherever a row is.

/// The last transition of every row: from → to, released at t0 (seconds) with velocity v0.
struct SpringDisclosureFaqTransitions {
    var from: [Double]
    var to: [Double]
    var v0: [Double]
    var t0: [Double]
}

enum SpringDisclosureFaqMode {
    case auto // the film; `tau` is the loop time
    case manual // a finger; `tau` is the clock in seconds
}

enum SpringDisclosureFaqMotion {
    static let loop = 9.0

    static let rowCount = 6
    static let groupSize = 3 // two grouped cards of three rows
    static let restRow = 0 // the row that is open at rest

    // The spring: .spring(response: 0.46, dampingFraction: 0.72), about 4 % overshoot.
    static let springResponse = 0.46
    static let springDamping = 0.72
    private static let omega = 2 * Double.pi / springResponse
    private static let omegaD = omega * (1 - springDamping * springDamping).squareRoot()
    private static let decay = springDamping * omega

    static let followLag = 0.03 // s, each row below starts this much after the one above it
    static let lineStagger = 0.035 // s between the lines of an answer
    static let lineFrom = 0.15 // an answer line fades in while its sample of `open` goes
    static let lineTo = 0.85 //   from lineFrom to lineTo
    static let lineRise = 10.0 // pt a line travels up
    static let lineBlur = 6.0 // pt of blur a line starts from
    static let reducedFade = 0.18 // s, the plain linear change under Reduce Motion

    // The film: at eventT[k] the ghost finger taps eventRow[k]; that row opens and eventPrev[k] closes.
    static let eventT: [Double] = [1.1, 3.7, 6.3]
    static let eventRow = [2, 4, 0]
    static let eventPrev = [0, 2, 4]

    // The ghost finger around an event at time e.
    static let touchIn = 0.3 // s before e the dot starts to appear
    static let touchDown = 0.08 // s before e it presses
    static let touchUp = 0.12 // s after e it lifts
    static let touchOut = 0.4 // s after e it is gone

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }

    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ x: Double) -> Double {
        let c = clamp01(x)
        return c * c * (3 - 2 * c)
    }

    /// An underdamped spring from `from` towards `to`, `s` seconds after it was released with
    /// velocity `v0` (units per second). Before the release (s <= 0) it is still at `from`.
    static func springTo(_ from: Double, _ to: Double, _ v0: Double, _ s: Double) -> Double {
        if s <= 0 { return from }
        let a = from - to
        let b = (v0 + decay * a) / omegaD
        return to + exp(-decay * s) * (a * cos(omegaD * s) + b * sin(omegaD * s))
    }

    /// `open` of row j at loop time t (t may be negative for a lagged sample: the rest state).
    static func filmOpen(_ j: Int, _ t: Double) -> Double {
        var v: Double = j == restRow ? 1 : 0
        for k in 0..<eventT.count {
            let s = t - eventT[k]
            if s <= 0 { continue }
            let step = springTo(0, 1, 0, s)
            if eventRow[k] == j { v += step } else if eventPrev[k] == j { v -= step }
        }
        return v
    }

    /// Where row j is heading at loop time t: 1 when it is the open row, else 0.
    static func filmTarget(_ j: Int, _ t: Double) -> Double {
        var row = restRow
        for k in 0..<eventT.count where t >= eventT[k] { row = eventRow[k] }
        return row == j ? 1 : 0
    }

    /// Opacity of the ghost finger's dot for event k at loop time t.
    static func touchAlpha(_ k: Int, _ t: Double) -> Double {
        let e = eventT[k]
        return smooth(seg(t, e - touchIn, e - touchDown)) * (1 - smooth(seg(t, e + touchUp, e + touchOut)))
    }

    /// 0..1, how far the ghost finger is pressed for event k at loop time t.
    static func touchPress(_ k: Int, _ t: Double) -> Double {
        let e = eventT[k]
        return smooth(seg(t, e - touchDown, e)) * (1 - smooth(seg(t, e + touchUp, e + touchUp + 0.16)))
    }

    /// How visible answer line k is, from its (lagged) sample of the row's `open`.
    static func lineReveal(_ open: Double) -> Double { smooth((open - lineFrom) / (lineTo - lineFrom)) }

    // ------------------------------------------------------------ a finger's transitions

    static func restTransitions() -> SpringDisclosureFaqTransitions {
        let v = (0..<rowCount).map { $0 == restRow ? 1.0 : 0.0 }
        return SpringDisclosureFaqTransitions(from: v, to: v, v0: Array(repeating: 0, count: rowCount), t0: Array(repeating: 0, count: rowCount))
    }

    /// `open` of row j at time tau, in the film or under a finger.
    static func openAt(_ mode: SpringDisclosureFaqMode, _ tr: SpringDisclosureFaqTransitions, _ reduced: Bool, _ j: Int, _ tau: Double) -> Double {
        if mode == .auto { return filmOpen(j, tau) }
        let s = tau - tr.t0[j]
        if reduced { return tr.from[j] + (tr.to[j] - tr.from[j]) * clamp01(s / reducedFade) }
        return springTo(tr.from[j], tr.to[j], tr.v0[j], s)
    }

    /// How far (pt) the element at row position `target` is pushed down by the answers of rows
    /// first...last above it: each answer's height times its `open`, sampled followLag later per
    /// row of distance, so the rows below follow one after another.
    static func pushAt(
        _ mode: SpringDisclosureFaqMode, _ tr: SpringDisclosureFaqTransitions, _ reduced: Bool, _ heights: [Double],
        _ first: Int, _ last: Int, _ target: Int, _ tau: Double
    ) -> Double {
        var sum = 0.0
        if last < first { return 0 }
        for j in first...last {
            let lag = reduced ? 0 : followLag * Double(target - j)
            sum += heights[j] * openAt(mode, tr, reduced, j, tau - lag)
        }
        return sum
    }

    /// The transitions after a finger toggles row `tapped` at time `now` (tapped < 0: back to
    /// rest): every row whose target changes starts a new spring from where it is, with the
    /// velocity it has. `mode`/`tau` describe the state being left.
    static func retarget(
        _ mode: SpringDisclosureFaqMode, _ tr: SpringDisclosureFaqTransitions, _ reduced: Bool, _ tau: Double, _ now: Double, _ tapped: Int
    ) -> SpringDisclosureFaqTransitions {
        let dt = 1.0 / 120
        var out = SpringDisclosureFaqTransitions(from: [], to: [], v0: [], t0: [])
        var openRow = -1
        for j in 0..<rowCount {
            let heading = mode == .auto ? filmTarget(j, tau) : tr.to[j]
            if heading > 0.5 { openRow = j }
        }
        // Tapping the open row closes it (nothing open).
        let next = tapped < 0 ? restRow : tapped == openRow ? -1 : tapped
        for j in 0..<rowCount {
            let heading = mode == .auto ? filmTarget(j, tau) : tr.to[j]
            let goal: Double = j == next ? 1 : 0
            let cur = openAt(mode, tr, reduced, j, tau)
            if goal == heading && mode != .auto {
                // untouched: keep the spring it is on
                out.from.append(tr.from[j]); out.to.append(tr.to[j]); out.v0.append(tr.v0[j]); out.t0.append(tr.t0[j])
            } else {
                let before = openAt(mode, tr, reduced, j, tau - dt)
                out.from.append(cur); out.to.append(goal); out.v0.append((cur - before) / dt); out.t0.append(now)
            }
        }
        return out
    }
}
