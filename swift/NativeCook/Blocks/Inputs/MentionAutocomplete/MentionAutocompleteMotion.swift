import Foundation

/*
 Mention Autocomplete — the loop as a pure function of time.

 The view follows the script's steps (what the composer holds: text, chips, which is selected);
 everything that moves between steps (the popover, the rows, the chip morph, the caret, the ghost
 finger) comes from `pose(t)`.

 Timeline (s)          what happens
 0.00 – 0.85           rest: empty composer
 0.55                  ghost tap on the composer
 0.85 – 1.21           "cc @" typed; the "@" opens the popover, which grows out of the caret
 1.60 – 1.96           "may" typed one letter at a time; rows filter and re-rank on springs
 2.45                  ghost tap on Maya Okafor
 2.50 – 2.95           the typed "@may" morphs into the chip "@Maya Okafor" and a space after it; the popover
                       shrinks into the caret
 3.10 – 4.00           "for review" typed after the space
 4.00 – 5.00           hold
 5.00 – 5.50           Backspace deletes "for review", then the space
 5.75                  Backspace on the chip selects it (filled accent)
 6.30 – 6.75           Backspace again breaks it back into text
 6.90 – 7.75           the line is deleted, faster and faster
 7.75 – 9.00           rest
*/
enum MentionAutocompleteMotion {
    typealias D = MentionAutocompleteData
    typealias Doc = MentionAutocompleteDoc

    static let loop: Double = 9
    static let idle: Double = 2
    static let returnLen = 0.7
    /// Loop time shown, still, under reduced motion: the composed message with its chip.
    static let tStill = 4.5

    static let picked = 2 // Maya Okafor
    static let prefix = "cc "
    static let token = "@may"
    static let suffix = "for review"
    static let label = D.people[picked].mention
    static let chipStart = prefix.count
    /// The typed letters the chip grows out of.
    static let morphFrom = token

    // MARK: Timeline
    static let fieldTap = 0.55
    static let focusAt = 0.6
    static let typeAt = 0.85
    static let typeGap = 0.12
    static let atAt = typeAt + 3 * typeGap
    static let queryAt = 1.6
    static let queryGap = 0.18
    static let pickAt = 2.45
    static let chipAt = 2.5
    static let morphLen = 0.45
    static let morphDone = 3.0
    static let suffixAt = 3.1
    static let suffixGap = 0.09
    static let deleteAt = 5.0
    static let deleteGap = 0.05
    static let selectAt = 5.75
    static let selectLen = 0.16
    static let breakAt = 6.3
    static let breakLen = 0.45
    static let clearAt = 6.9
    static let clearEnd = 7.75
    static let blurAt = 7.95

    static let rowH: Double = 54
    static let popHead: Double = 30
    static let popPad: Double = 6
    static let rowMove = 0.36
    /// How long the thread takes to make room for a sent message (and to give it back).
    static let sendLen = 0.3
    static let popMaxH = popHead + popPad * 2 + Double(D.maxRows) * rowH

    /// How the chip is drawn at a step: grown from letters, a chip, selected, or breaking into text.
    enum ChipMode { case none, morph, chip, selected, breaking }

    struct Step {
        let doc: Doc
        /// The mention being typed, for the popover; nil while it is closed.
        let query: String?
        let chip: ChipMode
    }

    private static func chipDoc(_ text: String, _ selected: Int) -> Doc {
        Doc(text: text, chips: [MentionAutocompleteChip(start: chipStart, len: label.count, person: picked)], selected: selected)
    }

    static let (script, times): ([Step], [Double]) = {
        var steps: [Step] = [Step(doc: .empty, query: nil, chip: .none)]
        var times: [Double] = [-1]
        func push(_ at: Double, _ s: Step) {
            steps.append(s)
            times.append(at)
        }
        let typed = Array(prefix + token)
        for k in 1 ... typed.count {
            let text = String(typed[0 ..< k])
            let at = k <= prefix.count + 1 ? typeAt + Double(k - 1) * typeGap : queryAt + Double(k - prefix.count - 2) * queryGap
            let open = k >= prefix.count + 1
            push(at, Step(doc: Doc(text: text, chips: [], selected: -1), query: open ? String(text.dropFirst(prefix.count + 1)) : nil, chip: .none))
        }
        let base = prefix + label
        // A pick puts a space after the chip, so the caret waits after "@Maya Okafor " and the next word stands apart.
        let spaced = base + " "
        push(chipAt, Step(doc: chipDoc(spaced, -1), query: nil, chip: .morph))
        push(morphDone, Step(doc: chipDoc(spaced, -1), query: nil, chip: .chip))
        let suf = Array(suffix)
        for k in 1 ... suf.count {
            push(suffixAt + Double(k - 1) * suffixGap, Step(doc: chipDoc(spaced + String(suf[0 ..< k]), -1), query: nil, chip: .chip))
        }
        // Backspace takes the words, then the space; only the Backspace after that reaches the chip.
        for k in stride(from: suf.count, through: 0, by: -1) {
            let text = k == 0 ? base : spaced + String(suf[0 ..< k - 1])
            push(deleteAt + Double(suf.count - k) * deleteGap, Step(doc: chipDoc(text, -1), query: nil, chip: .chip))
        }
        push(selectAt, Step(doc: chipDoc(base, 0), query: nil, chip: .selected))
        push(breakAt, Step(doc: Doc(text: base, chips: [], selected: -1), query: nil, chip: .breaking))
        // The line goes, one character at a time, each gap shorter than the last.
        let n = base.count
        for k in 1 ... n {
            let u = Double(k) / Double(n)
            let at = clearAt + (clearEnd - clearAt) * (1 - pow(1 - u, 1.6))
            push(at, Step(doc: Doc(text: String(base.prefix(n - k)), chips: [], selected: -1), query: nil, chip: .none))
        }
        return (steps, times)
    }()

    // Popover rows per step: slot (kept while hidden) and visibility.
    private static let tables: (y: [[Double]], a: [[Double]], counts: [Double], text: [Double]) = {
        let lists: [[Int]] = script.map { $0.query == nil ? [] : D.rank($0.query!) }
        var lastOpen: [Int] = []
        let held: [[Int]] = lists.map { l in
            if !l.isEmpty { lastOpen = l }
            return l.isEmpty ? lastOpen : l
        }
        var yAll: [[Double]] = [], aAll: [[Double]] = []
        for i in 0 ..< D.people.count {
            var ys: [Int] = held.map { $0.firstIndex(of: i) ?? -1 }
            let as_: [Double] = lists.map { $0.contains(i) ? 1 : 0 }
            let known = ys
            for k in 0 ..< ys.count where known[k] < 0 {
                var fill = -1
                var j = k - 1
                while j >= 0 && fill < 0 { if known[j] >= 0 { fill = known[j] }; j -= 1 }
                j = k + 1
                while j < ys.count && fill < 0 { if known[j] >= 0 { fill = known[j] }; j += 1 }
                ys[k] = fill < 0 ? 0 : fill
            }
            yAll.append(ys.map(Double.init))
            aAll.append(as_)
        }
        return (yAll, aAll, held.map { Double($0.count) }, script.map { $0.doc.text.isEmpty ? 0 : 1 })
    }()

    // MARK: Curves
    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double) -> Double { let c = 1.5, u = x - 1; return 1 + (c + 1) * u * u * u + c * u * u }

    private static func superpose(_ values: [Double], _ t: Double, _ len: Double) -> Double {
        var v = values[0]
        for j in 1 ..< values.count {
            let d = values[j] - values[j - 1]
            if d == 0 { continue }
            v += d * easeOutCubic(seg(t, times[j], times[j] + len))
        }
        return v
    }

    /// Spring-like settle for row slots: a little overshoot, as the person's springs have.
    private static func superposeSpring(_ values: [Double], _ t: Double, _ len: Double) -> Double {
        var v = values[0]
        for j in 1 ..< values.count {
            let d = values[j] - values[j - 1]
            if d == 0 { continue }
            v += d * easeOutBack(seg(t, times[j], times[j] + len))
        }
        return v
    }

    static func stepAt(_ t: Double) -> Int {
        var k = 0
        for j in 1 ..< times.count where t >= times[j] { k = j }
        return k
    }

    // MARK: The pose
    struct Pose {
        var focus = 0.0
        var caret = 0.0
        var placeholder = 1.0
        /// The send button wakes when there is text.
        var send = 0.0
        /// Popover: 0 → 1 grows out of the caret (overshoots a little), back to 0 when it closes.
        var pop = 0.0
        var count = 0.0
        var rowY: [Double] = []
        var rowA: [Double] = []
        /// Chip: grown from the typed letters 0 → 1; selected 0 → 1; breaking into text 0 → 1.
        var morph = 1.0
        var select = 0.0
        var breakUp = 0.0
    }

    struct Dot {
        var target = 0
        var alpha = 0.0
        var scale = 1.0
    }

    static func pose(_ t: Double) -> Pose {
        let k = stepAt(t)
        var p = Pose()
        for i in 0 ..< D.people.count {
            p.rowY.append(superposeSpring(tables.y[i], t, rowMove))
            p.rowA.append(superpose(tables.a[i], t, rowMove * 0.7))
        }
        let open = easeOutBack(seg(t, atAt, atAt + 0.36))
        let close = easeInOutCubic(seg(t, chipAt, chipAt + 0.25))
        let lastKey = times[k] > 0 ? times[k] : 0
        let since = t - lastKey
        let blink = since < 0.5 ? 1 : clamp01(0.5 + 1.6 * cos(2 * Double.pi * (since - 0.5) / 1.06))
        p.focus = easeOutCubic(seg(t, focusAt, focusAt + 0.2)) * (1 - seg(t, blurAt, blurAt + 0.25))
        p.caret = seg(t, focusAt + 0.15, focusAt + 0.25) * (1 - seg(t, blurAt - 0.05, blurAt)) * blink
        p.placeholder = 1 - superpose(tables.text, t, 0.12)
        p.send = superpose(tables.text, t, 0.2)
        p.pop = t < chipAt ? open : 1 - close
        p.count = superpose(tables.counts, t, rowMove)
        p.morph = t < chipAt + morphLen + 0.6 ? seg(t, chipAt, chipAt + morphLen) : 1
        p.select = easeOutCubic(seg(t, selectAt, selectAt + selectLen))
        p.breakUp = seg(t, breakAt, breakAt + breakLen)
        return p
    }

    /// The ghost finger: taps the composer, then Maya's row.
    static func dot(_ t: Double) -> Dot {
        var d = Dot()
        for (k, at) in [fieldTap, pickAt].enumerated() {
            let a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36))
            if a > d.alpha {
                d.alpha = a
                d.target = k
                d.scale = 1 - 0.22 * sin(Double.pi * seg(t, at - 0.06, at + 0.16))
            }
        }
        return d
    }
}

/// A number a person drives: set at once, eased over a duration, or sprung. Sampled against the
/// TimelineView clock, so nothing writes state per frame.
struct MentionAutocompleteValue {
    private enum Kind {
        case timing((Double) -> Double)
        case spring(omega: Double, zeta: Double, v0: Double)
    }

    private var from: Double
    private var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var kind: Kind = .timing({ $0 })

    init(_ v: Double) {
        from = v
        to = v
    }

    mutating func set(_ v: Double) {
        from = v
        to = v
        duration = 0
    }

    func value(_ now: Double) -> Double {
        if duration <= 0 { return to }
        let e = now - start
        if e <= 0 { return from }
        if e >= duration { return to }
        switch kind {
        case let .timing(curve):
            return from + (to - from) * curve(e / duration)
        case let .spring(w, z, v0):
            let x0 = from - to
            if z < 1 {
                let wd = w * sqrt(1 - z * z)
                return to + exp(-z * w * e) * (x0 * cos(wd * e) + (v0 + z * w * x0) / wd * sin(wd * e))
            }
            return to + (x0 + (v0 + w * x0) * e) * exp(-w * e)
        }
    }

    func isMoving(_ now: Double) -> Bool { duration > 0 && now < start + duration }

    mutating func timing(_ target: Double, _ dur: Double, now: Double, curve: @escaping (Double) -> Double = { $0 }) {
        if dur <= 0 { set(target); return }
        from = value(now)
        to = target
        start = now
        duration = dur
        kind = .timing(curve)
    }

    /// A spring that settles in about `dur` seconds with the given damping ratio.
    mutating func spring(_ target: Double, _ dur: Double, damping: Double, now: Double) {
        if dur <= 0 { set(target); return }
        let v0 = (value(now + 0.004) - value(now)) / 0.004
        from = value(now)
        to = target
        start = now
        duration = dur * 2.2
        kind = .spring(omega: 2 * Double.pi / dur, zeta: damping, v0: v0)
    }
}
