import Foundation
import CoreGraphics

/// Geometry, the Hollerith code, the self-playing script and the pure timing functions of the Punch
/// Card Form. Every frame of the loop is a pure function of the loop time.
enum PunchCardFormMotion {
    /// Loop period in seconds.
    static let loop = 9.0

    static let designW = 393.0
    static let designH = 759.0

    /// The card: 80 columns of 12 rows, drawn 716 × 315 pt (the real card's 7⅜ × 3¼ in).
    static let cols = 80
    static let rows = 12
    static let pitch = 8.4
    static let rowPitch = 22.4
    static let marginX = 22.0
    static let marginTop = 34.0
    static let cardW = marginX * 2 + Double(cols) * pitch
    static let cardH = marginTop + Double(rows) * rowPitch + 12
    static let holeW = 4.6
    static let holeH = 11.0

    /// The canvas (design coordinates) and, inside it, the card's top, the punch station and the reader's slot.
    static let canvasY = 164.0
    static let canvasH = 456.0
    static let cardY = 22.0
    static let stationX = 112.0
    static let slotY = cardY + cardH + 12

    struct Field {
        let label: String
        let first: Int
        let size: Int
    }

    static let fields = [Field(label: "NAME", first: 1, size: 20), Field(label: "YEAR", first: 21, size: 4), Field(label: "WING", first: 25, size: 10)]

    /// The demo types these, one per field.
    static let script = ["ADA", "2026", "WEST"]

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// Ease-out with a small overshoot, for the card advancing a column.
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.6
        return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
    }

    static func hash(_ n: Double) -> Double {
        let s = sin(n * 127.1 + 311.7) * 43758.5453
        return s - floor(s)
    }

    /// The rows punched for a character, as a bit mask over rows 0–11 top to bottom (12, 11, 0, 1 … 9).
    static func holeMask(_ code: Int) -> Int {
        if code >= 48 && code <= 57 { return 1 << (code - 48 + 2) }
        if code >= 65 && code <= 73 { return (1 << 0) | (1 << (code - 65 + 1 + 2)) }
        if code >= 74 && code <= 82 { return (1 << 1) | (1 << (code - 74 + 1 + 2)) }
        if code >= 83 && code <= 90 { return (1 << 2) | (1 << (code - 83 + 2 + 2)) }
        if code == 45 { return 1 << 1 }
        return 0
    }

    /// Centre of a column (1-based) and of a row (0-based), in card points.
    static func colX(_ col: Double) -> Double { marginX + (col - 0.5) * pitch }
    static func rowY(_ row: Int) -> Double { marginTop + (Double(row) + 0.5) * rowPitch }
    /// The card's left edge on the canvas when column `col` (fractional) sits under the punch.
    static func cardLeft(_ col: Double) -> Double { stationX - colX(col) }

    /// The demo's keystrokes: (time, column, character code).
    static let autoKeys: [(time: Double, col: Int, code: Int)] = {
        let times: [[Double]] = [[1.5, 2.2, 2.9], [3.62, 3.82, 4.02, 4.22], [4.46, 4.64, 4.82, 5.0]]
        var keys: [(Double, Int, Int)] = []
        for (f, word) in script.enumerated() {
            for (i, ch) in word.unicodeScalars.enumerated() {
                keys.append((times[f][i], fields[f].first + i, Int(ch.value)))
            }
        }
        return keys
    }()

    /// Per column (index col - 1): the character the demo punches there and when (or -1).
    static let autoChar: [Int] = {
        var a = [Int](repeating: 0, count: cols)
        for k in autoKeys { a[k.col - 1] = k.code }
        return a
    }()

    static let autoTime: [Double] = {
        var a = [Double](repeating: -1, count: cols)
        for k in autoKeys { a[k.col - 1] = k.time }
        return a
    }()

    private static let step = 0.16

    /// The column under the punch at loop time t (fractional while the card moves).
    static func autoColumn(_ t: Double) -> Double {
        var col = 1.0
        for (i, k) in autoKeys.enumerated() {
            col += easeOutBack(seg(t, k.time + 0.07, k.time + 0.07 + step))
            if i == 2 { col += Double(fields[1].first - 4) * easeInOutCubic(seg(t, 3.15, 3.5)) }
        }
        return col
    }

    /// How many of the demo's keys have been typed by t.
    static func autoTyped(_ t: Double) -> Int {
        if t >= 6.0 { return 0 }
        return autoKeys.filter { t >= $0.time }.count
    }

    /// The typed card slides down into the reader (5.25–6.1 s) while a fresh one registers from the right.
    static func autoFeed(_ t: Double) -> Double { easeInOutCubic(seg(t, 5.25, 6.1)) }

    static func autoRegister(_ t: Double) -> Double {
        if t < 5.7 { return 1 }
        return 1 - easeOutBack(seg(t, 5.7, 7.2))
    }

    /// How far, in points, a registering card starts to the right of its place.
    static let registerFrom = 430.0

    /// The reader is busy (lights running) from the feed until the new card is out.
    static func autoBusy(_ t: Double) -> Double { seg(t, 5.25, 5.4) * (1 - seg(t, 6.6, 6.9)) }

    /// The button's label: 0 "Submit card", 1 "Reading…", 2 "Welcome, member" (fractional while rolling).
    static func autoLabel(_ t: Double) -> Double {
        easeInOutCubic(seg(t, 5.2, 5.45)) + easeInOutCubic(seg(t, 6.1, 6.35)) - 2 * easeInOutCubic(seg(t, 7.3, 7.6))
    }

    /// Which field the caret is in during the demo (0, 1, 2), or -1.
    static func autoField(_ t: Double) -> Int {
        if t < 1.2 || t >= 5.2 { return -1 }
        if t < 3.3 { return 0 }
        if t < 4.35 { return 1 }
        return 2
    }

    /// Height of a quick punch stroke: down in 30 ms, back up by 90 ms.
    static func strokeAt(_ dt: Double) -> Double {
        if dt < 0 || dt > 0.09 { return 0 }
        return dt < 0.03 ? dt / 0.03 : 1 - (dt - 0.03) / 0.06
    }

    /// One chad's place, turn and flip `age` seconds after it was punched out of (col, row).
    static func chadPose(_ col: Int, _ row: Int, _ age: Double) -> (x: Double, y: Double, spin: Double, flip: Double) {
        let id = Double(col * 16 + row)
        let vx = (hash(id) - 0.5) * 90
        let vy = -70 - hash(id + 7) * 90
        let spin = (hash(id + 13) - 0.5) * 26
        let g = 1500.0
        return (stationX + vx * age, cardY + rowY(row) + vy * age + 0.5 * g * age * age, spin * age, cos((5 + hash(id + 3) * 9) * age))
    }
}
