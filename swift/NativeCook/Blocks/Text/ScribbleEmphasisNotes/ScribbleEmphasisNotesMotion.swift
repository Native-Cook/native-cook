import Foundation

/// Every number the annotations use, and the copy. Ported from the React Native block's `motion.ts` and `data.ts`.
enum ScribbleEmphasisNotesMotion {
    /// Seconds. One autoPlay loop: clean page, six marks draw in order, hold, lift off in reverse, clean page.
    static let loop = 9.0

    /// The marks, in the order they are drawn.
    static let circle = 0, highlight = 1, underline = 2, bracket = 3, note = 4, arrow = 5
    static let markCount = 6

    /// When each mark draws (ease-in-out cubic from its start to its end), in seconds.
    static let draw: [(Double, Double)] = [(0.9, 1.7), (1.95, 2.75), (2.95, 3.7), (3.9, 4.35), (4.45, 5.4), (5.4, 5.8)]
    /// When each mark lifts off: the stroke retracts from its end back to its start. Last drawn lifts first.
    static let lift: [(Double, Double)] = [(7.75, 8.1), (7.6, 7.9), (7.45, 7.75), (7.3, 7.55), (7.12, 7.47), (7.0, 7.25)]
    /// Everything is on the page between these two.
    static let hold = 6.4
    static let liftStart = 7.0
    /// While a finger has the page, unfinished marks finish at this multiple of the loop's speed.
    static let catchUp = 2.5
    /// autoPlay: 2 s after the last touch the finger's marks lift off and the loop carries on into its own lift-off.
    static let resumeAfter = 2.0

    /// The ghost finger that drags the highlighter in the loop.
    static let ghostAppear = 1.62, ghostShown = 1.82, ghostLift = 2.78, ghostGone = 3.0, ghostSize = 40.0

    /// A finger's own highlight: seconds to swipe on after a tap, and to lift off.
    static let userDraw = 0.42
    static let userLift = 0.26
    /// A touch that travels less than this is a tap (toggle the sentence); further and it marks words.
    static let tapSlop = 8.0
    /// At most this many finger-made highlights are kept; the oldest goes when one more is made.
    static let userMarks = 8

    /// Pen. Widths in points; a stroke swells from `taperIn` of its width and thins to `taperOut` at the flick.
    static let penWidth = 2.1
    static let noteWidth = 1.9
    static let taperIn = 0.55
    static let taperOut = 0.32
    static let taperInSpan = 0.12
    static let taperOutSpan = 0.22
    static let jitter = 0.55
    static let widthNoise = 0.14
    static let step = 1.6
    static let penLift = 10.0

    /// Circle round the term: 1.1 turns from the upper left, spiralling out so the end overlaps the start.
    static let circlePadX = 10.0, circleRy = 0.86, circleTurns = 1.1, circleStart = -2.5, circleSpiral = 0.09, circleTilt = -0.07
    /// Wavy underline: amplitude and wavelength in points, below the baseline.
    static let waveDrop = 5.5, waveAmp = 2.1, waveLength = 13.0, waveOvershoot = 3.0, waveRise = 1.2
    /// Highlighter band, as shares of the font size above and below the baseline; multiplied over the text.
    static let bandUp = 0.92, bandDown = 0.3, bandPadX = 3.0, bandWobble = 1.1, bandStep = 9.0, bandSlant = 3.0, bandOpacity = 0.62, bandStreak = 0.2
    /// Margin bracket: a brace, in points from the text's right edge.
    static let braceGap = 7.0, braceDepth = 6.0, braceNose = 6.0, braceCurl = 9.0
    /// The note: x-height in points and its tilt in radians.
    static let noteXHeight = 12.5, noteTilt = -0.1, noteGap = 0.28
    /// Arrow head: two strokes of this length at this angle off the shaft.
    static let headLength = 8.5, headSpread = 0.5

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(max(v, lo), hi) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeInOutCubic(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }

    /// Seeded 0…1.
    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898) * 43758.5453
        return s - floor(s)
    }

    /// How much of mark `index` is on the page at loop time t: 0 none, 1 all of it, drawn from its start.
    /// `cap` freezes the drawing at an earlier time (a finger took the page mid-loop) while the lift-off still
    /// runs on t; pass `loop` for no cap.
    static func markAt(_ t: Double, _ cap: Double, _ index: Int) -> Double {
        let d = draw[index], l = lift[index]
        return max(0, easeInOutCubic(seg(min(t, cap), d.0, d.1)) - easeInOutCubic(seg(t, l.0, l.1)))
    }

    /// Where the clock parks when a finger takes the page at t: the pen finishes the mark it is on, and stops.
    static func parkAt(_ t: Double) -> Double {
        for d in draw where t > d.0 && t < d.1 { return d.1 }
        return t
    }

    /// Opacity of the ghost finger.
    static func ghostAt(_ t: Double) -> Double {
        seg(t, ghostAppear, ghostShown) * (1 - seg(t, ghostLift, ghostGone))
    }

    // MARK: Copy ("Folio" and the course are invented)

    static let brand = "Folio"
    static let course = "Biology · Unit 4"
    static let title = "Cellular respiration"
    static let meta = "Lecture 7 · Thu 12 Nov · 4 min read"
    static let label = "Key idea"
    static let pageNumber = "p. 142"
    static let tray = "Highlighter"
    static let hint = "Tap a sentence, or drag across words"
    static let marked = "Marked on this page"

    /// Plain ASCII, so character offsets are the same in every stack.
    static let text = "Cellular respiration turns glucose into energy the cell can spend. "
        + "Most of it is released in the mitochondria, where the electron transport chain pumps protons across the inner membrane. "
        + "That gradient drives ATP synthase, making about 30 ATP per glucose. "
        + "Without oxygen the chain backs up and the cell falls back on fermentation."

    /// What the loop marks. Each phrase is found in `text` by its first occurrence.
    static let termCircle = "mitochondria"
    static let termHighlight = "electron transport chain"
    static let termUnderline = "about 30 ATP per glucose"
    /// The sentence the margin bracket holds (0-based).
    static let bracketSentence = 3

    /// The summary rows under the tray; `mark` is the loop's mark that lights the row.
    static let markedRows: [(term: String, how: String, mark: Int)] = [
        ("mitochondria", "Circled", 0),
        ("electron transport chain", "Highlighted", 1),
        ("30 ATP per glucose", "Underlined", 2),
    ]

    /// Highlighter inks (sRGB). Multiplied over the page.
    static let tints: [(name: String, hex: UInt32)] = [("Lemon", 0xFFE14A), ("Rose", 0xFF9FB6), ("Mint", 0x8FE6B4)]
}
