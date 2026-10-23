import SwiftUI
import UIKit

// MARK: - Metrics

enum BakeryFlapBoardMetrics {
    // Colours: walnut case, charcoal flaps, cream letters, amber prices, brass fittings.
    static let background: UInt32 = 0x1a0f09
    static let flapTop: UInt32 = 0x2c2723
    static let flapBottom: UInt32 = 0x24201c
    static let split: UInt32 = 0x0b0908
    static let letter: UInt32 = 0xf2e8d2
    static let price: UInt32 = 0xf3b85e
    static let brass: UInt32 = 0xc9a25e
    static let brassDark: UInt32 = 0x6e4f24
    static let cream: UInt32 = 0xf4ead6
    static let cocoa: UInt32 = 0x2b1d12

    // The board: two lines for the time and four for the menu, sixteen flaps each. A flap is as tall
    // as flapAspect × its width; caseInset is the margin of the brass-edged case, pad the room inside.
    static let flapAspect = 1.74
    static let flapGap = 2.0
    static let rowGap = 9.0
    static let labelH = 24.0
    static let caseInset = 16.0
    static let pad = 14.0
    static let timeRows = 2
    static let boardAt = 0.32

    // A flip: each step of the drum takes `step` s; a falling half turns faster as it goes (a share
    // fallLinear of its turn is linear, the rest grows with the square). Columns start colDelay s
    // apart plus up to colJitter s. The last flap of a cell lands with a bounce of bounceAngle rad over
    // `bounce` s. `camera` is the distance of the eye, in flap heights.
    static let step = 0.05
    static let fallLinear = 0.35
    static let colDelay = 0.028
    static let colJitter = 0.018
    static let bounce = 0.17
    static let bounceAngle = 0.3
    static let camera = 3.4
    // Shade on a flap turned edge-on, and on the half it covers.
    static let flapShade = 0.55
    static let castShade = 0.4

    // The strip of photos drifts one set to the left every loop.
    static let tileW = 128.0
    static let tileH = 164.0
    static let tileGap = 10.0

    // The item card grows out of its line; its spring, and a drag down by `dismiss` pt closes it.
    static let cardDelay = 0.55
    static let cardFade = 0.18
    static let cardStiffness = 170.0
    static let cardDamping = 22.0
    static let dismiss = 360.0
    static let dismissSpeed = 600.0
    static let closeBelow = 0.6
    static let rubber = 0.2
    // The card is 4 pt wider than the case on each side; its photo is `cardPhoto` × its width tall
    // and the text under it takes `cardBody` pt.
    static let cardOverhang = 4.0
    static let cardPhoto = 0.7
    static let cardBody = 164.0

    // autoPlay (seconds): lines change one after another, the time gives way to a notice and comes
    // back, one line is "tapped" and its card opens and closes, and by the loop's end every line is back.
    static let loop = 18.0
    struct Cue {
        var at: Double
        var row: Int
        var item: Int? = nil
        var notice: Bool? = nil
        var card: Bool? = nil
    }
    static let script: [Cue] = [
        Cue(at: 0.6, row: 2, item: 1),
        Cue(at: 2.0, row: 5, item: 1),
        Cue(at: 3.4, row: 0, notice: true),
        Cue(at: 5.0, row: 4, item: 1, card: true),
        Cue(at: 8.8, row: -1, card: false),
        Cue(at: 9.8, row: 2, item: 0),
        Cue(at: 11.2, row: 3, item: 1),
        Cue(at: 12.4, row: 0, notice: false),
        Cue(at: 13.6, row: 5, item: 0),
        Cue(at: 14.4, row: 4, item: 0),
        Cue(at: 15.2, row: 3, item: 0),
    ]
    // The time the board shows while it plays by itself, so every loop is the same.
    static let demoTime = (7, 10)
    // After a touch the loop waits this long; longer when a card is open, so it can be read.
    static let userHold = 4.0
    static let cardHold = 7.0
}

struct BakeryFlapBoardStage: Equatable {
    var width = 0.0
    var height = 0.0
    var top = 0.0
    var bottom = 0.0
}

/// Where the board and each of its lines sit for a screen.
struct BakeryFlapBoardLayout {
    var x, y, w, h, left, pitch, cellW, cellH: Double
    var rows: [Double]
    var labels: [Double]

    private typealias M = BakeryFlapBoardMetrics

    static func stripBottom(_ room: BakeryFlapBoardStage) -> Double { room.top + 40 + M.tileH }

    init(_ room: BakeryFlapBoardStage, rowCount: Int) {
        x = M.caseInset
        w = room.width - M.caseInset * 2
        left = x + M.pad
        pitch = (w - M.pad * 2) / Double(BakeryFlapBoardText.columns)
        cellW = pitch - M.flapGap
        cellH = (cellW * M.flapAspect).rounded()
        h = M.pad * 2 + M.labelH * 2 + Double(rowCount) * (cellH + M.rowGap) - M.rowGap + 6
        let regionTop = Self.stripBottom(room) + 18
        let regionBottom = room.height - room.bottom - 44
        // The board sits a little above the middle of the room under the strip.
        y = max(regionTop, regionTop + (regionBottom - regionTop - h) * M.boardAt)
        var rows: [Double] = []
        var labels: [Double] = []
        var cursor = y + M.pad
        for r in 0..<rowCount {
            if r == 0 || r == M.timeRows {
                if r == M.timeRows { cursor += 6 }
                labels.append(cursor + 15)
                cursor += M.labelH
            }
            rows.append(cursor)
            cursor += cellH + M.rowGap
        }
        self.rows = rows
        self.labels = labels
    }

    func cellX(_ i: Int) -> Double { left + Double(i) * pitch + M.flapGap / 2 }

    /// The open card: 4 pt wider than the case on each side and centred on it, so none of the case
    /// shows around it (where the screen leaves the card taller than the case).
    /// The open card, centred on the screen between the safe areas (the scrim hides the board, so the
    /// screen is what it is seen against). On a short screen it shrinks to fit, keeping its photo's
    /// proportions.
    static func card(_ room: BakeryFlapBoardStage) -> CGRect {
        let x = M.caseInset - M.cardOverhang
        let top = room.top + 12
        let bottom = room.height - room.bottom - 12
        let space = max(1, bottom - top)
        let w = max(1, min(room.width - x * 2, (space - M.cardBody) / M.cardPhoto))
        let h = w * M.cardPhoto + M.cardBody
        return CGRect(x: (room.width - w) / 2, y: top + max(0, space - h) / 2, width: w, height: h)
    }
}

/// A line of flaps: what it showed when its last change began, what it is going to, and when.
struct BakeryFlapBoardRow {
    var from: [Int]
    var to: [Int]
    var text: String
    var at: Double
}

/// The state of one cell: the character showing (`cur`), the one coming (`next`) and how far the
/// falling half has turned (0…1), or −1 once it has settled; `bounce` runs 0…1 just after it lands.
struct BakeryFlapBoardCell {
    var cur: Int
    var next: Int
    var p: Double
    var bounce: Double
}

enum BakeryFlapBoardMath {
    private typealias M = BakeryFlapBoardMetrics
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// True when the loop clock went from `before` to `now` across `at`, wrapping at the loop.
    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// A column's own small delay, fixed per column, so a line never starts as one block.
    static let jitter: [Double] = (0..<BakeryFlapBoardText.columns).map { i in
        let s = sin(Double(i) * 12.9898 + 4.1) * 43758.5453
        return (s - s.rounded(.down)) * M.colJitter
    }

    static func indices(_ text: String) -> [Int] {
        text.map(BakeryFlapBoardText.drumIndex)
    }

    static func cell(_ row: BakeryFlapBoardRow, _ i: Int, _ now: Double) -> BakeryFlapBoardCell {
        let n = BakeryFlapBoardText.drum.count
        let from = i < row.from.count ? row.from[i] : 0
        let to = i < row.to.count ? row.to[i] : 0
        let steps = (to - from + n) % n
        if steps == 0 { return .init(cur: to, next: to, p: -1, bounce: -1) }
        let t = now - row.at - Double(i) * M.colDelay - jitter[i]
        if t < 0 { return .init(cur: from, next: from, p: -1, bounce: -1) }
        let k = Int((t / M.step).rounded(.down))
        if k >= steps {
            let after = (t - Double(steps) * M.step) / M.bounce
            return .init(cur: to, next: to, p: -1, bounce: after < 1 ? after : -1)
        }
        return .init(cur: (from + k) % n, next: (from + k + 1) % n, p: (t - Double(k) * M.step) / M.step, bounce: -1)
    }

    /// The characters a line shows `now`, mid-flip or not.
    static func showing(_ row: BakeryFlapBoardRow, _ now: Double) -> [Int] {
        (0..<BakeryFlapBoardText.columns).map { cell(row, $0, now).cur }
    }
}

/// Everything one frame of the moving parts needs.
struct BakeryFlapBoardFrame {
    var now: Double
    var rows: [BakeryFlapBoardRow]
    var cardOpen: Double
    var cardRow: Int
    var cardItem: BakeryFlapBoardItem?
    var reducedMotion: Bool
}

// MARK: - The engine

/// The board's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class BakeryFlapBoardEngine {
    private typealias M = BakeryFlapBoardMetrics
    private typealias X = BakeryFlapBoardMath

    var menu: [[BakeryFlapBoardItem]] = BakeryFlapBoardItem.menu
    var noticeLines: [String] = []
    var stage = BakeryFlapBoardStage()
    private var rowCount: Int { M.timeRows + menu.count }

    private var items: [Int] = []
    private var showNotice = false
    private var time: [String] = []
    private var rows: [BakeryFlapBoardRow] = []
    private(set) var elapsed = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    // The item card: 0 folded into its line … 1 open, its velocity and target, the line it came from,
    // and a line waiting to open once its flip is under way (with the time it opens).
    private var cardOpen = 0.0
    private var cardVelocity = 0.0
    private var cardTarget = 0.0
    private var cardRow = -1
    private var cardItem: BakeryFlapBoardItem?
    private var cardDragging = false
    private var cardFrom = 0.0
    private var pending: (row: Int, at: Double, user: Bool)?
    private var lastDate: Date?
    private var configuredAuto: Bool?
    private var lastLiveCheck = -Double.infinity
    private var reducedMotion = false

    // Touch tracking: a pan starts after 6 pt of travel; a touch lifted within 450 ms that never got
    // that far is a tap.
    private var touchStart: Date?
    private var panning = false

    var onTick: (() -> Void)?
    var onSoft: (() -> Void)?
    var onOpen: ((BakeryFlapBoardItem) -> Void)?

    private func liveTime() -> [String] {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: Date())
        return BakeryFlapBoardText.timeInWords(parts.hour ?? 0, parts.minute ?? 0).map(BakeryFlapBoardText.centred)
    }

    private func demoTime() -> [String] {
        BakeryFlapBoardText.timeInWords(M.demoTime.0, M.demoTime.1).map(BakeryFlapBoardText.centred)
    }

    private func line(_ row: Int, _ item: Int) -> String {
        let entry = menu[row][item]
        return BakeryFlapBoardText.menuLine(entry.board, entry.price)
    }

    private func configure(auto: Bool) {
        configuredAuto = auto
        looping = auto
        clock = 0
        hold = 0
        time = auto ? demoTime() : liveTime()
        if rows.isEmpty {
            items = menu.map { _ in 0 }
            let blank = Array(repeating: 0, count: BakeryFlapBoardText.columns)
            rows = (0..<rowCount).map { r in
                // On first appearance every line flips in from blank.
                let to = r < M.timeRows ? time[r] : line(r - M.timeRows, 0)
                return BakeryFlapBoardRow(from: blank, to: X.indices(to), text: to, at: 0.35 + Double(r) * 0.16)
            }
        }
    }

    /// Moves menu line `row` to `item` (or the next one), and asks for its card if `card`.
    private func advance(_ row: Int, _ item: Int, card: Bool, user: Bool) {
        if row < M.timeRows {
            showNotice.toggle()
            return
        }
        let list = menu[row - M.timeRows]
        let next = item < 0 ? (items[row - M.timeRows] + 1) % list.count : item
        items[row - M.timeRows] = next
        if card { pending = (row, elapsed + M.cardDelay, user) }
    }

    private func closeCard(_ velocity: Double) {
        cardTarget = 0
        cardVelocity = velocity
        pending = nil
    }

    func step(to date: Date, stage room: BakeryFlapBoardStage, autoPlay: Bool, reducedMotion: Bool) -> BakeryFlapBoardFrame {
        stage = room
        self.reducedMotion = reducedMotion
        let auto = autoPlay && !reducedMotion
        if configuredAuto != auto { configure(auto: auto) }
        let dt = lastDate.map { min(max(date.timeIntervalSince($0), 0), 0.064) } ?? 0.016
        lastDate = date
        if dt > 0 { advance(dt) }
        return BakeryFlapBoardFrame(now: elapsed, rows: rows, cardOpen: cardOpen, cardRow: cardRow, cardItem: cardItem, reducedMotion: reducedMotion)
    }

    private func advance(_ dt: Double) {
        let now = elapsed + dt
        elapsed = now

        // The time in words, kept current while the board is not playing by itself.
        if configuredAuto == false && now - lastLiveCheck >= 15 {
            lastLiveCheck = now
            time = liveTime()
        }

        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // Back to the loop's resting board before it starts over.
                clock = 0
                closeCard(0)
                showNotice = false
                items = items.map { _ in 0 }
            }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for cue in M.script where X.passed(before, t, cue.at) {
                if let notice = cue.notice {
                    showNotice = notice
                } else if cue.card == false {
                    closeCard(0)
                } else if let item = cue.item {
                    advance(cue.row, item, card: cue.card == true, user: false)
                }
            }
        }

        // Each line chases the text it should show; a change starts from whatever it shows now.
        for r in 0..<rowCount {
            let wanted = r < M.timeRows ? (showNotice && r < noticeLines.count ? noticeLines[r] : time[r]) : line(r - M.timeRows, items[r - M.timeRows])
            if rows[r].text == wanted { continue }
            rows[r] = BakeryFlapBoardRow(from: X.showing(rows[r], now), to: X.indices(wanted), text: wanted, at: now)
        }

        if let ask = pending, now >= ask.at {
            pending = nil
            cardRow = ask.row
            cardTarget = 1
            let entry = menu[ask.row - M.timeRows][items[ask.row - M.timeRows]]
            cardItem = entry
            // Reported on the next turn of the main loop: this runs while the timeline draws its frame.
            if ask.user {
                let onOpen = onOpen
                DispatchQueue.main.async { onOpen?(entry) }
            }
        }

        if !cardDragging {
            if reducedMotion {
                let gap = cardTarget - cardOpen
                cardOpen += (gap > 0 ? 1 : gap < 0 ? -1 : 0) * min(abs(gap), dt / 0.22)
            } else {
                cardVelocity += (M.cardStiffness * (cardTarget - cardOpen) - M.cardDamping * cardVelocity) * dt
                cardOpen += cardVelocity * dt
            }
            if cardTarget == 0 && cardOpen < 0.002 && abs(cardVelocity) < 0.05 {
                cardOpen = 0
                cardVelocity = 0
                cardRow = -1
            }
        }
    }

    // MARK: Touch

    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        let dx = value.translation.width
        let dy = value.translation.height
        if !panning {
            guard dx * dx + dy * dy >= 36 else { return }
            panning = true
            panStart()
        }
        if cardDragging {
            let p = cardFrom - dy / M.dismiss
            cardOpen = p > 1 ? 1 + (p - 1) * M.rubber : max(0, p)
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        let start = touchStart ?? value.time
        touchStart = nil
        if panning {
            panning = false
            panEnd(velocityY: value.velocity.height)
        } else if value.time.timeIntervalSince(start) <= 0.45 {
            tap(x: value.location.x, y: value.location.y)
        }
    }

    /// The system took the touch away (onEnded never fires for that): a card under the finger is
    /// let go where it is, as the Compose version does.
    func touchCancelled() {
        guard touchStart != nil else { return }
        touchStart = nil
        if panning {
            panning = false
            panEnd(velocityY: 0)
        }
    }

    private func panStart() {
        guard cardRow >= 0, cardTarget == 1 else { return }
        hold = M.cardHold
        cardDragging = true
        cardFrom = cardOpen
    }

    private func panEnd(velocityY: Double) {
        guard cardDragging else { return }
        cardDragging = false
        let velocity = -velocityY / M.dismiss
        if velocityY > M.dismissSpeed || (cardOpen < M.closeBelow && velocityY > -M.dismissSpeed) {
            closeCard(velocity)
            onSoft?()
        } else {
            cardTarget = 1
            cardVelocity = velocity
        }
    }

    private func tap(x: Double, y: Double) {
        if cardRow >= 0 || pending != nil {
            hold = M.userHold
            closeCard(0)
            return
        }
        let layout = BakeryFlapBoardLayout(stage, rowCount: rowCount)
        for r in 0..<rowCount {
            let top = layout.rows[r]
            if y < top - M.rowGap / 2 || y > top + layout.cellH + M.rowGap / 2 { continue }
            if x < layout.x || x > layout.x + layout.w { continue }
            hold = r < M.timeRows ? M.userHold : M.cardHold
            advance(r, -1, card: r >= M.timeRows, user: true)
            onTick?()
            return
        }
    }
}
