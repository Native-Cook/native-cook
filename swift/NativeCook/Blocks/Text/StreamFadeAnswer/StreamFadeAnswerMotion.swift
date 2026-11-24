import Foundation

/// Stream Fade Answer — the clocks. A port of the React Native `streamFadeMotion.ts`.
///
/// The stream clock S (seconds) is the only thing the answer depends on: every word, row and the chip has a start
/// time on it and is a pure function of S. S < 0 is "nothing yet", S ≥ end is the finished answer. The self-playing
/// loop and a scrubbing finger both just move S.
///
/// The loop clock t (LOOP = 10 s) maps to S:
///   0.00 – 0.70   rest: the finished answer (S = end).
///   0.70 – 1.00   the answer fades out (ease-in), the question stays.
///   0.90 – 1.50   the thread scrolls back so the question sits where a new turn starts.
///   1.25 – 2.60   "thinking": one grey line with a light band sweeping across it twice (0.7 s a sweep).
///   2.60 – 8.14   the stream: S = t − 2.6, linear. Words arrive 28 ms apart on average.
///   8.14 – 10.0   rest with the finished answer; t = 10 is t = 0.
enum StreamFadeAnswerMotion {
    static let loop: Double = 10

    static let holdEnd = 0.7
    static let fadeEnd = 1.0
    static let scrollBackA = 0.9
    static let scrollBackB = 1.5
    static let thinkA = 1.25
    static let thinkIn = 0.2
    static let streamAt = 2.6
    static let thinkOut = 0.25
    static let thinkSweep = 0.7

    /// A word: 28 ms after the one before on average, 0.42 s to arrive from 6 pt below and a 4 pt blur.
    static let wordGap = 0.028
    static let wordRate = 1 / wordGap
    static let wordDur = 0.42
    static let wordRise = 6.0
    static let wordBlur = 4.0
    /// The newest line fades toward the front of the stream over this many points.
    static let feather = 96.0

    /// Between two blocks of the answer the stream pauses, as a model does at a paragraph.
    static let blockPause = 0.28
    static let rowGap = 0.34
    static let rowDur = 0.46
    static let rowRise = 10.0
    static let lineDur = 0.4
    static let chipDur = 0.36
    static let chipCount = 0.7

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeOutQuad(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) }
    static func easeInQuad(_ x: Double) -> Double { x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// When each of `count` words starts, in seconds from the first: each gap is 0.4–1.6 × the mean (seeded).
    static func wordStarts(_ count: Int, rate: Double, seed: Double) -> [Double] {
        var out: [Double] = []
        var at = 0.0
        for i in 0..<count {
            out.append(at)
            at += (0.4 + 1.2 * hash(Double(i) * 1.37 + seed * 17.1 + 3)) / rate
        }
        return out
    }

    /// S for the self-playing loop at loop time t; `end` is the S of the finished answer.
    static func stream(_ t: Double, end: Double) -> Double { t < fadeEnd ? end : t - streamAt }

    /// Opacity of the whole answer in the loop: it fades out before the next turn.
    static func answerAlpha(_ t: Double) -> Double { t < fadeEnd ? 1 - easeInQuad(seg(t, holdEnd, fadeEnd)) : 1 }

    /// The thinking line in the loop: opacity, and the band's position 0…1 across the line.
    static func thinking(_ t: Double) -> (alpha: Double, band: Double) {
        let alpha = seg(t, thinkA, thinkA + thinkIn) * (1 - seg(t, streamAt - thinkOut, streamAt))
        let u = (t - thinkA) / thinkSweep
        return (alpha, easeInOutCubic(clamp01(u - floor(max(0, u)))))
    }

    /// Rubber band past the ends of the stream while scrubbing: `over` seconds become fewer.
    static func rubber(_ over: Double) -> Double { (over * 0.55) / (1 + abs(over) / 0.6) }
}
