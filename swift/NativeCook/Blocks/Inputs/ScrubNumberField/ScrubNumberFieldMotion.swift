import Foundation

// Scrub Number Field: the fields, the film and the odometer, as pure functions (a port of
// scrubMotion.ts).
//
// A field's value is one number, `pos`: the value counted in steps (Exposure +0.35 at 0.05 a step
// is pos 7). While a finger scrubs, pos is continuous; on release a spring snaps it to a whole
// step. The photo's shader, the ruler under the number and the rolling digits are all drawn from pos.

struct ScrubNumberFieldSpec {
    let label: String
    let symbol: String // SF Symbol
    let step: Double // value of one step
    let unitsPerStep: Int // one step in units of the last digit shown (0.05 = 5 hundredths)
    let decimals: Int
    let signed: Bool
    let min: Double // in steps
    let max: Double
    let rest: Double // in steps: what the screen opens on, and what a double tap returns to
    let peak: Double // in steps: where the film scrubs it to
    let unit: String
}

enum ScrubNumberFieldMotion {
    static let loop = 9.0

    static let fields: [ScrubNumberFieldSpec] = [
        .init(label: "Exposure", symbol: "plusminus.circle", step: 0.05, unitsPerStep: 5, decimals: 2, signed: true, min: -40, max: 40, rest: 7, peak: 23, unit: "stops"),
        .init(label: "Warmth", symbol: "thermometer.medium", step: 1, unitsPerStep: 1, decimals: 0, signed: true, min: -100, max: 100, rest: 12, peak: 46, unit: ""),
        .init(label: "Vignette", symbol: "circle.dashed", step: 1, unitsPerStep: 1, decimals: 0, signed: false, min: 0, max: 100, rest: 40, peak: 62, unit: "percent"),
    ]
    static let fieldCount = 3
    static let rest = fields.map(\.rest)
    private static let peak = fields.map(\.peak)

    /// The value a field shows at a whole step, e.g. "+0.35".
    static func formatValue(_ spec: ScrubNumberFieldSpec, _ pos: Double) -> String {
        let v = pos.rounded() * spec.step
        let body = String(format: "%.\(spec.decimals)f", abs(v))
        if !spec.signed || pos.rounded() == 0 { return body }
        return (v > 0 ? "+" : "−") + body
    }

    // ------------------------------------------------------------ scrubbing

    static let tick = 8.0 // pt of ruler per step; at a slow drag the ruler follows the finger 1:1
    static let majorEvery = 5 // a longer tick every five steps
    static let gainMax = 4.0 // a fast drag covers up to four steps per tick of travel
    static let gainFrom = 250.0 // pt/s where the drag starts to get coarser
    static let gainTo = 1100.0 // pt/s where it is coarsest
    static let rubberSteps = 5.0 // steps: how far past a limit the value can be pulled
    static let project = 0.1 // s of release velocity carried into the snap
    static let stretchSpeed = 900.0 // pt/s of finger speed for the full stretch
    static let stretchMax = 0.045 // the field is at most 4.5 % wider
    static let squashMax = 0.012 // and 1.2 % flatter
    static let blurFrom = 14.0 // steps/s where the digits start to smear
    static let blurTo = 90.0 // steps/s where the smear is full
    static let blurMax = 7.0 // pt, vertical sigma
    static let speedTau = 0.06 // s, smoothing of the measured speed
    static let lift = 8.0 // pt the number rises while the ruler shows

    // The snap on release: .spring(response: 0.38, dampingFraction: 0.72).
    static let snapResponse = 0.38
    static let snapDamping = 0.72
    private static let omega = 2 * Double.pi / snapResponse
    private static let omegaD = omega * (1 - snapDamping * snapDamping).squareRoot()
    private static let decay = snapDamping * omega

    static let resetS = 0.8 // a double tap rolls the digits back, ease in-out cubic
    static let resetStagger = 0.06 // s between the fields when all three go back

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { x < lo ? lo : x > hi ? hi : x }

    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func smooth(_ x: Double) -> Double {
        let c = clamp01(x)
        return c * c * (3 - 2 * c)
    }

    static func easeInOutCubic(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeInOutQuad(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 2 * c * c : 1 - pow(-2 * c + 2, 2) / 2
    }

    /// The snap spring from `from` towards `to`, s seconds after a release with velocity v0.
    static func snapTo(_ from: Double, _ to: Double, _ s: Double, v0: Double = 0) -> Double {
        if s <= 0 { return from }
        let a = from - to
        let b = (v0 + decay * a) / omegaD
        return to + exp(-decay * s) * (a * cos(omegaD * s) + b * sin(omegaD * s))
    }

    /// How many steps one tick of finger travel is worth at this finger speed (pt/s).
    static func scrubGain(_ speed: Double) -> Double {
        1 + (gainMax - 1) * smooth((speed - gainFrom) / (gainTo - gainFrom))
    }

    /// A value pulled past its limits gives way less and less.
    static func rubber(_ raw: Double, _ min: Double, _ max: Double) -> Double {
        if raw > max {
            let over = raw - max
            return max + over / (1 + over / rubberSteps)
        }
        if raw < min {
            let over = min - raw
            return min - over / (1 + over / rubberSteps)
        }
        return raw
    }

    /// Vertical blur of the rolling digits (pt) for a value moving at `speed` steps per second.
    static func digitBlur(_ speed: Double) -> Double {
        blurMax * clamp01((speed - blurFrom) / (blurTo - blurFrom))
    }

    // ------------------------------------------------------------ the odometer

    /// Position of the last digit's strip for |value| = x (in units of that digit): continuous, in
    /// glyphs. Its strip holds 10 / unitsPerStep glyphs (0 and 5 for Exposure, 0-9 otherwise).
    static func odoLow(_ x: Double, _ unitsPerStep: Int) -> Double {
        let n = 10.0 / Double(unitsPerStep)
        let p = x / Double(unitsPerStep)
        return p - floor(p / n) * n
    }

    /// Position of the strip of digit k >= 1, unwrapped: it rests on a whole digit and only turns
    /// during the last step before the digits below it wrap.
    static func odoHigh(_ x: Double, _ k: Int, _ unitsPerStep: Int) -> Double {
        let m = pow(10, Double(k))
        let base = floor(x / m)
        let rem = x - base * m
        let start = m - Double(unitsPerStep)
        return base + (rem > start ? (rem - start) / Double(unitsPerStep) : 0)
    }

    static let rollFrom = 0.25 // a digit strip turns while its position's fraction goes
    static let rollTo = 0.75 //   from rollFrom to rollTo, and rests on a whole digit otherwise

    /// A strip position with the turn squeezed into the middle of each step.
    static func rollEase(_ v: Double) -> Double {
        let base = floor(v)
        return base + smooth((v - base - rollFrom) / (rollTo - rollFrom))
    }

    // ------------------------------------------------------------ the film

    // The ghost finger presses field i at dragA[i] - pressLead, drags from dragA[i] to dragB[i]
    // (ease in-out cubic) and lifts; it lets go releaseOff steps past a detent, and the spring snaps back.
    static let dragA: [Double] = [0.95, 3.0, 4.75]
    static let dragB: [Double] = [2.35, 3.75, 5.75]
    static let dragPx: [Double] = [128, 170, 140] // pt the finger travels
    static let pressLead = 0.14 // s
    static let releaseOff = 0.4 // steps
    static let tap1 = 6.75 // the double tap on the photo
    static let tap2 = 6.95
    static let resetT = 7.0 // the digits start rolling back

    /// pos of field i at loop time t.
    static func filmPos(_ i: Int, _ t: Double) -> Double {
        let b = dragB[i]
        let p: Double
        if t <= b { p = rest[i] + (peak[i] + releaseOff - rest[i]) * easeInOutCubic(seg(t, dragA[i], b)) }
        else { p = snapTo(peak[i] + releaseOff, peak[i], t - b) }
        let start = resetT + Double(i) * resetStagger
        let back = easeInOutCubic(seg(t, start, start + resetS))
        return p + (rest[i] - p) * back
    }

    /// 0..1: field i is pressed (ruler shown, number lifted).
    static func filmActive(_ i: Int, _ t: Double) -> Double {
        let a = dragA[i], b = dragB[i]
        return smooth(seg(t, a - pressLead, a - 0.02)) * (1 - smooth(seg(t, b, b + 0.3)))
    }

    /// pt the ghost finger has travelled on field i.
    static func filmFinger(_ i: Int, _ t: Double) -> Double {
        dragPx[i] * easeInOutCubic(seg(t, dragA[i], dragB[i]))
    }

    /// -1..1: the field's stretch, from the ghost finger's speed.
    static func filmStretch(_ i: Int, _ t: Double) -> Double {
        let dt = 1.0 / 120
        let v = (filmFinger(i, t) - filmFinger(i, t - dt)) / dt
        return clamp(v / stretchSpeed, -1, 1)
    }

    /// The smoothed speed of field i (steps per second) at loop time t: the same one-pole filter the
    /// frame clock runs under a finger (time constant speedTau), replayed over the last 0.4 s so it
    /// is a function of t alone.
    static func filmSpeed(_ i: Int, _ t: Double) -> Double {
        let dt = 1.0 / 120
        let k = 1 - exp(-dt / speedTau)
        var s = 0.0
        var prev = filmPos(i, t - 48 * dt)
        for n in stride(from: 47, through: 0, by: -1) {
            let p = filmPos(i, t - Double(n) * dt)
            s += (abs(p - prev) / dt - s) * k
            prev = p
        }
        return s < 0.05 ? 0 : s
    }

    /// Opacity of the ghost finger over field i.
    static func filmDotAlpha(_ i: Int, _ t: Double) -> Double {
        let a = dragA[i] - pressLead, b = dragB[i]
        return smooth(seg(t, a - 0.2, a)) * (1 - smooth(seg(t, b + 0.05, b + 0.3)))
    }

    /// Opacity of the ghost finger over the photo, for the double tap.
    static func filmTapAlpha(_ t: Double) -> Double {
        smooth(seg(t, tap1 - 0.3, tap1 - 0.08)) * (1 - smooth(seg(t, tap2 + 0.1, tap2 + 0.35)))
    }

    private static func tapBump(_ t: Double, _ e: Double) -> Double {
        smooth(seg(t, e - 0.07, e)) * (1 - smooth(seg(t, e + 0.03, e + 0.12)))
    }

    /// 0..1: how far the ghost finger is pressed during the double tap.
    static func filmTapPress(_ t: Double) -> Double { max(tapBump(t, tap1), tapBump(t, tap2)) }
}

/// A number on its way somewhere: read against the clock, so nothing is written per frame.
struct ScrubNumberFieldTrack {
    enum Kind { case hold, spring, cubic, quad }

    var kind = Kind.hold
    var from = 0.0
    var to = 0.0
    var v0 = 0.0
    var t0 = 0.0 // may lie ahead: the value waits at `from`
    var duration = 0.0

    static func hold(_ v: Double) -> Self { .init(kind: .hold, from: v, to: v) }

    func value(at now: Double) -> Double {
        let s = now - t0
        switch kind {
        case .hold: return to
        case .spring: return ScrubNumberFieldMotion.snapTo(from, to, s, v0: v0)
        case .cubic: return from + (to - from) * ScrubNumberFieldMotion.easeInOutCubic(duration > 0 ? s / duration : 1)
        case .quad: return from + (to - from) * ScrubNumberFieldMotion.easeInOutQuad(duration > 0 ? s / duration : 1)
        }
    }
}
