// Split Amount Pad — the amount, the split and the clock.
//
// Nothing here is SwiftUI state. A key press edits the typed codes and retargets a handful of
// closed-form values (SplitAmountPadValue); the view reads them at the timeline's time. The clock
// plays the loop's script, and hands over to a finger and back.

import UIKit

/// the numbers that change with the screen: a phone under 740 pt tall gets the compact set
struct SplitAmountPadLayout: Equatable {
    static let side = 20.0
    /// the one-line labels' heights
    static let label = 16.0
    static let shareLine = 20.0

    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let compact: Bool
    let gap: Double
    let keyH: Double
    let keyW: Double
    /// the amount's font size and line height
    let amount: Double
    let line: Double
    let disc: Double
    let cta: Double
    let block: Double
    let column: Double
    /// tops, pt from the screen's top
    let ctaY: Double
    let padY: Double
    let seatsY: Double
    let betweenY: Double

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        self.top = top
        self.bottom = bottom
        compact = height < 740
        gap = compact ? 8 : 10
        keyH = compact ? 54 : 64
        keyW = (width - Self.side * 2 - gap * 2) / 3
        amount = compact ? 54 : 64
        line = (amount * 1.14).rounded()
        disc = compact ? 46 : 52
        cta = compact ? 48 : 54
        block = compact ? 10 : 16
        column = (width - Self.side * 2) / 4
        ctaY = height - (max(bottom, 12) + 8) - cta
        padY = ctaY - block - (keyH * 4 + gap * 3)
        seatsY = padY - block - (disc + 8 + Self.label + 2 + Self.shareLine)
        betweenY = seatsY - 10 - Self.label
    }
}

/// the amount's glyph widths, pt
struct SplitAmountPadMetrics: Equatable {
    var digit: Double
    var dot: Double
    var comma: Double
    var dollar: Double
}

final class SplitAmountPadModel {
    private typealias M = SplitAmountPadMotion

    static let pool = 12
    static let dollarGap = 4.0
    /// the small rolling share: cell widths at 15 pt semibold tabular digits
    static let shareDigit = 9.6
    static let shareMark = 4.4

    enum TileState { case hidden, live, leaving }
    struct Tile {
        var state = TileState.hidden
        var glyph = 0
        var born = -1
        var order = 0
        var centre = 0.0
        var seenCentre = 0.0
        var turn = 0.0
        var x = SplitAmountPadValue(0)
        var enter = SplitAmountPadValue(0)
        var leave = SplitAmountPadValue(0)
    }

    private enum Mode { case auto, touch, returning }
    private enum Outcome { case done, ignored, refused }

    // the amount
    private(set) var typed: [Int] = []
    /// the pooled tile showing each typed code
    private var seats: [Int] = []
    private(set) var tiles = [Tile](repeating: Tile(), count: SplitAmountPadModel.pool)
    private var cursor = 0
    private var births = 0
    private(set) var dollarX = SplitAmountPadValue(0)
    private(set) var commaX = SplitAmountPadValue(0)
    private(set) var commaOn = SplitAmountPadValue(0)
    private(set) var empty = SplitAmountPadValue(1)
    private(set) var shake = SplitAmountPadSequence()
    private(set) var limit = SplitAmountPadSequence()
    private var cents = 0

    // the split
    private var active = [1, 1, 1, 1]
    private var shares = [0, 0, 0, 0]
    private(set) var friendOn = [SplitAmountPadValue](repeating: SplitAmountPadValue(1), count: 4)
    private(set) var friendPress = [SplitAmountPadValue](repeating: SplitAmountPadValue(0), count: 4)
    /// per friend, six digit columns (0 the cents' ones … 5 the thousands) and whether each shows
    private(set) var digit = [[SplitAmountPadValue]](repeating: [SplitAmountPadValue](repeating: SplitAmountPadValue(0), count: 6), count: 4)
    private(set) var shown = [[SplitAmountPadValue]](
        repeating: (0..<6).map { SplitAmountPadValue($0 <= 2 ? 1 : 0) }, count: 4)
    /// the width of the leading columns that are hidden
    private(set) var hidden = [SplitAmountPadValue](
        repeating: SplitAmountPadValue(SplitAmountPadModel.shareDigit * 3 + SplitAmountPadModel.shareMark), count: 4)
    private(set) var thousands = [SplitAmountPadValue](repeating: SplitAmountPadValue(0), count: 4)

    // the keys
    private(set) var keyPress = [SplitAmountPadValue](repeating: SplitAmountPadValue(0), count: 12)
    private var keyDown = [Bool](repeating: false, count: 12)
    private var friendDown = [Bool](repeating: false, count: 4)
    private var holdAt: Double?

    // the clock and the hand-over
    private(set) var now = 0.0
    private var base: Double?
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var prevT = -1.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var held = 0
    private(set) var ghostX = 0.0
    private(set) var ghostY = 0.0
    private var ghostAuto = 0.0
    private var ghostFade = SplitAmountPadValue(0)
    private(set) var autoKey = -1
    private(set) var autoFriend = -1
    private(set) var autoPress = 0.0

    private var layout: SplitAmountPadLayout?
    private var metrics = SplitAmountPadMetrics(digit: 41, dot: 19, comma: 19, dollar: 23)
    private var auto = false
    /// reduced motion: no springs
    private var instant = false
    private var finalShown = false

    private let selection = UISelectionFeedbackGenerator()
    private let notice = UINotificationFeedbackGenerator()

    var ghostAlpha: Double { mode == .auto ? ghostAuto : ghostFade.value(at: now) }

    // MARK: set-up

    func configure(layout next: SplitAmountPadLayout, metrics m: SplitAmountPadMetrics, auto playing: Bool, reduced: Bool) {
        let first = layout == nil
        layout = next
        if auto != playing || instant != reduced {
            auto = playing
            instant = reduced
            if !first, mode == .auto {
                rest()
                autoStart = now
                prevT = -1
            }
        }
        if first || m != metrics {
            metrics = m
            if first { dollarX.set(-(m.dollar + Self.dollarGap + m.digit) / 2) }
            // lay the amount out again with the real widths
            let was = instant
            instant = true
            show(typed, seats)
            instant = was
        }
        if reduced, !finalShown {
            finalShown = true
            if typed.isEmpty { show([1, 2, 8, M.keyDot, 4, 0], [Int](repeating: -1, count: 6)) }
        }
        if !reduced { finalShown = false }
    }

    // MARK: the amount

    /// where everything sits for these typed codes, x relative to the row's middle: the dollar's left
    /// edge, the comma's centre (nil without one), one centre per code
    private func amountLayout(_ codes: [Int]) -> (dollar: Double, comma: Double?, centres: [Double]) {
        let m = metrics
        let whole = M.dotAt(codes) < 0 ? codes.count : M.dotAt(codes)
        let hasComma = whole >= 4
        var total = m.dollar + Self.dollarGap + (hasComma ? m.comma : 0)
        // an empty amount shows a placeholder zero
        if codes.isEmpty { total += m.digit }
        for code in codes { total += code == M.keyDot ? m.dot : m.digit }
        var comma: Double?
        var centres: [Double] = []
        var x = -total / 2 + m.dollar + Self.dollarGap
        for (i, code) in codes.enumerated() {
            if hasComma, i == whole - 3 {
                comma = x + m.comma / 2
                x += m.comma
            }
            let w = code == M.keyDot ? m.dot : m.digit
            centres.append(x + w / 2)
            x += w
        }
        return (-total / 2, comma, centres)
    }

    /// the next pooled tile that is not on screen as a live digit
    private func takeTile() -> Int {
        for n in 0..<Self.pool {
            let i = (cursor + n) % Self.pool
            if tiles[i].state != .live {
                cursor = i + 1
                return i
            }
        }
        return cursor % Self.pool
    }

    /// put these codes on screen: tiles of codes that are gone leave, new codes (seat -1) are born, the rest slide
    private func show(_ codes: [Int], _ nextSeats: [Int]) {
        var next = nextSeats
        var leaving = 0
        for seat in seats.reversed() where !next.contains(seat) {
            // flicked away, last first
            tiles[seat].state = .leaving
            if instant {
                tiles[seat].leave.set(1)
            } else {
                tiles[seat].leave.timing(to: 1, at: now, delay: Double(leaving) * M.flickStagger, duration: M.flick, ease: M.outQuad)
            }
            leaving += 1
        }
        let lay = amountLayout(codes)
        var arriving = 0
        for i in codes.indices {
            let centre = lay.centres[i]
            if next[i] < 0 {
                // born: take the glyph, stand at the final place, drop in
                let t = takeTile()
                next[i] = t
                births += 1
                var tile = Tile()
                tile.state = .live
                tile.glyph = codes[i]
                tile.born = births
                tile.order = arriving
                tile.x.set(centre)
                tile.turn = (births % 2 == 0 ? 1 : -1) * Double(M.dropTurnMin + (births * 13) % M.dropTurnSpan)
                if instant {
                    tile.enter.set(1)
                } else {
                    tile.enter.spring(to: 1, at: now, delay: Double(arriving) * M.dropStagger, M.drop)
                }
                tiles[t] = tile
                arriving += 1
            } else if centre != tiles[next[i]].seenCentre {
                if instant { tiles[next[i]].x.set(centre) } else { tiles[next[i]].x.spring(to: centre, at: now, M.slide) }
            }
            tiles[next[i]].centre = centre
            tiles[next[i]].seenCentre = centre
        }
        typed = codes
        seats = next

        if instant {
            dollarX.set(lay.dollar)
            if let comma = lay.comma { commaX.set(comma) }
            commaOn.set(lay.comma == nil ? 0 : 1)
            empty.set(codes.isEmpty ? 1 : 0)
        } else {
            dollarX.spring(to: lay.dollar, at: now, M.slide)
            if let comma = lay.comma {
                // a comma that was hidden appears in place; one on screen slides with the digits
                if commaOn.value(at: now) < 0.05 { commaX.set(comma) } else { commaX.spring(to: comma, at: now, M.slide) }
                commaOn.spring(to: 1, at: now, M.drop)
            } else {
                commaOn.timing(to: 0, at: now, duration: 0.12)
            }
            empty.timing(to: codes.isEmpty ? 1 : 0, at: now, duration: codes.isEmpty ? 0.22 : 0.09)
        }
        cents = M.centsOf(codes)
        setShares(M.splitShares(cents, active))
    }

    private func setShares(_ next: [Int]) {
        for friend in 0..<M.friends {
            let value = next[friend]
            // someone who sits out keeps the digits they had
            guard value != shares[friend], value >= 0 else { continue }
            var power = 1
            for place in 0..<6 {
                let d = Double((value / power) % 10)
                let on: Double = place <= 2 || value >= power ? 1 : 0
                if instant {
                    digit[friend][place].set(d)
                    shown[friend][place].set(on)
                } else {
                    digit[friend][place].spring(to: d, at: now, delay: Double(friend) * M.rollFriend + Double(place) * M.rollPlace, M.roll)
                    shown[friend][place].timing(to: on, at: now, duration: 0.14)
                }
                power *= 10
            }
            let gone = (value < 1000 ? 1 : 0) + (value < 10000 ? 1 : 0) + (value < 100_000 ? 1 : 0)
            let w = Double(gone) * Self.shareDigit + (value < 100_000 ? Self.shareMark : 0)
            let comma: Double = value >= 100_000 ? 1 : 0
            if instant {
                hidden[friend].set(w)
                thousands[friend].set(comma)
            } else {
                hidden[friend].spring(to: w, at: now, M.slide)
                thousands[friend].timing(to: comma, at: now, duration: 0.14)
            }
        }
        shares = next
    }

    private func setActive(_ next: [Int]) {
        for i in 0..<M.friends where next[i] != active[i] {
            if instant { friendOn[i].set(Double(next[i])) } else { friendOn[i].spring(to: Double(next[i]), at: now, M.toggle) }
        }
        active = next
        setShares(M.splitShares(cents, next))
    }

    private func refuse(hard: Bool) {
        guard !instant else { return }
        let d = M.shakeLeg
        shake.run((hard ? M.shakeHard : M.shakeSoft).map { ($0, d, 0) }, at: now)
        if hard { limit.run([(1, 0.12, 0), (0, 0.4, 0.9)], at: now) }
    }

    /// one key: the amount's rules
    private func pressKey(_ code: Int) -> Outcome {
        let codes = typed
        if code == M.keyClear {
            if codes.isEmpty { return .ignored }
            show([], [])
            return .done
        }
        if code == M.keyBack {
            if codes.isEmpty {
                refuse(hard: false)
                return .ignored
            }
            show(Array(codes.dropLast()), Array(seats.dropLast()))
            return .done
        }
        let dot = M.dotAt(codes)
        if code == M.keyDot {
            if dot >= 0 {
                refuse(hard: false)
                return .ignored
            }
            // a dot on an empty amount brings its zero with it
            if codes.isEmpty { show([0, M.keyDot], [-1, -1]) } else { show(codes + [M.keyDot], seats + [-1]) }
            return .done
        }
        var nextCodes: [Int]
        var nextSeats: [Int]
        if dot < 0, codes == [0] {
            // a lone zero is replaced, not followed
            if code == 0 {
                refuse(hard: false)
                return .ignored
            }
            nextCodes = [code]
            nextSeats = [-1]
        } else {
            if dot >= 0, codes.count - dot - 1 >= M.maxDecimals {
                refuse(hard: false)
                return .ignored
            }
            if dot < 0, codes.count >= M.maxIntDigits {
                refuse(hard: true)
                return .refused
            }
            nextCodes = codes + [code]
            nextSeats = seats + [-1]
        }
        if M.centsOf(nextCodes) > M.limitCents {
            refuse(hard: true)
            return .refused
        }
        show(nextCodes, nextSeats)
        return .done
    }

    /// tap a friend in or out of the split; the payer (seat 0) always stays in
    private func toggleFriend(_ index: Int) -> Outcome {
        guard index != 0 else { return .ignored }
        var next = active
        next[index] = next[index] > 0 ? 0 : 1
        setActive(next)
        return .done
    }

    /// back to the resting screen: nothing typed, everyone in
    private func rest() {
        if !typed.isEmpty { show([], []) }
        if active.contains(0) { setActive([1, 1, 1, 1]) }
    }

    // MARK: fingers

    private func takeOver() {
        lastTouch = now
        guard mode != .touch else { return }
        ghostFade.set(ghostAlpha)
        ghostFade.timing(to: 0, at: now, duration: 0.15)
        mode = .touch
        autoPress = 0
        autoKey = -1
        autoFriend = -1
    }

    private func userKey(_ code: Int) {
        takeOver()
        if code != M.keyClear { held += 1 }
        switch pressKey(code) {
        case .done: selection.selectionChanged()
        case .refused: notice.notificationOccurred(.warning)
        case .ignored: break
        }
    }

    /// the finger came down on a pad cell: the digit is committed now, not when it lifts
    func touchDown(cell: Int, code: Int, at date: Date) {
        guard !keyDown[cell] else { return }
        sync(date)
        keyDown[cell] = true
        keyPress[cell].timing(to: 1, at: now, duration: M.keyDown)
        if code == M.keyBack { holdAt = now + M.hold }
        userKey(code)
    }

    func touchUp(cell: Int, code: Int, at date: Date) {
        guard keyDown[cell] else { return }
        sync(date)
        keyDown[cell] = false
        keyPress[cell].timing(to: 0, at: now, duration: M.keyUp)
        if code == M.keyBack { holdAt = nil }
        release()
    }

    func touchDown(friend: Int, at date: Date) {
        guard !friendDown[friend] else { return }
        sync(date)
        friendDown[friend] = true
        friendPress[friend].timing(to: 1, at: now, duration: M.keyDown)
        takeOver()
        held += 1
        if toggleFriend(friend) == .done { selection.selectionChanged() }
    }

    func touchUp(friend: Int, at date: Date) {
        guard friendDown[friend] else { return }
        sync(date)
        friendDown[friend] = false
        friendPress[friend].timing(to: 0, at: now, duration: M.keyUp)
        release()
    }

    private func release() {
        held = max(0, held - 1)
        lastTouch = now
    }

    // MARK: the clock

    private func sync(_ date: Date) {
        let stamp = date.timeIntervalSinceReferenceDate
        if base == nil { base = stamp }
        now = max(now, stamp - (base ?? stamp))
    }

    /// where the ghost finger aims for a script row, pt
    private func aim(_ row: Int, _ l: SplitAmountPadLayout) -> (Double, Double) {
        let r = M.script[row]
        let side = SplitAmountPadLayout.side
        if r.act == .friend { return (side + (Double(r.arg) + 0.5) * l.column, l.seatsY + l.disc / 2) }
        let cell = M.cellOf(r.arg)
        return (side + Double(cell % 3) * (l.keyW + l.gap) + l.keyW / 2, l.padY + Double(cell / 3) * (l.keyH + l.gap) + l.keyH / 2)
    }

    /// once a frame, from the timeline
    func advance(to date: Date) {
        sync(date)
        guard let l = layout else { return }
        if let at = holdAt, now >= at {
            holdAt = nil
            userKey(M.keyClear)
        }
        switch mode {
        case .auto:
            guard auto else {
                // not playing: the loop waits at t = 0
                ghostAuto = 0
                autoPress = 0
                autoKey = -1
                autoFriend = -1
                autoStart = now
                prevT = -1
                return
            }
            let t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
            if t < prevT {
                rest()
                prevT = -1
            }
            for row in M.script where prevT < row.at && t >= row.at {
                switch row.act {
                case .key: _ = pressKey(row.arg)
                case .friend: _ = toggleFriend(row.arg)
                case .clear: _ = pressKey(M.keyClear)
                }
            }
            // the ghost finger: glides to its next target, presses on the beat
            let row = M.ghostRow(t)
            let from = aim(M.ghostPrev(row), l)
            let to = aim(row, l)
            let g = M.ghostGlide(t, row)
            ghostX = from.0 + (to.0 - from.0) * g
            ghostY = from.1 + (to.1 - from.1) * g
            ghostAuto = M.ghostAlpha(t)
            autoPress = M.ghostPress(t, row)
            autoKey = M.script[row].act == .key ? M.cellOf(M.script[row].arg) : -1
            autoFriend = M.script[row].act == .friend ? M.script[row].arg : -1
            prevT = t
        case .touch:
            if auto, held == 0, now - lastTouch >= M.idle {
                mode = .returning
                returnAt = now
                rest()
            }
        case .returning:
            if now - returnAt >= M.returnTime {
                autoStart = now
                prevT = -1
                mode = .auto
            }
        }
    }
}
