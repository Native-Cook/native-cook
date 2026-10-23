import CoreText
import SwiftUI
import UIKit

// A workout interval timer on near-black: a huge "01:02" in a heavy condensed face counts down.
// Each digit that changes rolls in its own window: the old digit slides up and out of the window
// while the new one rises from below into place (1.12 windows of travel), stretched up to 10 %
// while it moves (a hint of motion blur), the old one fading to 0.5 and the new one in from 0.5;
// when several digits change they go from the right, 0.05 s apart. The colon dips on every tick,
// and a lime bar under the time drains with it. Going up (a drag, or the restart) rolls the other
// way: old digits drop out of the bottom, new ones come from above.
//
// autoPlay timeline (RollingCountdownDigitsMotion.loop = 9 s), in seconds shown:
//   0.0-0.6   rest at 01:02
//   0.6, 1.6, 2.6, 3.6, 4.6, 5.6   ticks: 01:01, 01:00, 00:59 (three digits roll), 00:58, 00:57,
//             00:56; each roll 0.42 s, ease-out cubic; the colon dips to 0.45 over 0.5 s (a sine bump)
//   5.6-7.4   holds at 00:56
//   7.4-8.3   restart: back to 01:02 in one roll the other way (0.7 s, ease-in-out cubic; three
//             digits, 0.05 s apart)
//   8.3-9.0   rest at 01:02
// The lime dot beside the round breathes (0.35-1, a cosine every 1.5 s, full at 0), so the
// screen is never quite still.
//
// Touch: dragging up adds a second per 24 pt, dragging down takes one away (0:00 to 9:59); every
// step rolls the digits that change (0.3 s, ease-out cubic) with a selection tick. 2 s after the
// finger lifts the time rolls back to 01:02 like the restart and the loop starts over.

enum RollingCountdownDigitsMotion {
    static let loop = 9.0
    static let rest = 62
    /// The value the bar is full at.
    static let total = 90.0
    static let tickDuration = 0.42
    static let stepDuration = 0.3
    static let restartDuration = 0.7
    static let stagger = 0.05
    static let colonDip = 0.55
    static let colonDuration = 0.5
    /// A roll moves each digit this many windows; the digits stretch by up to `stretch` mid-roll.
    static let travel = 1.12
    static let stretch = 0.1
    /// The live dot breathes once every `pulse` seconds (six times a loop).
    static let pulse = 1.5
    /// Points of drag per second.
    static let dragStep = 24.0
    static let userHold = 2.0

    enum Curve { case easeOut, easeInOut }

    /// A change of the shown value, starting at `time` (seconds into the loop, or since a date).
    struct Change {
        var time: Double
        var from: Int
        var to: Int
        var duration: Double
        var curve: Curve
    }

    /// The autoPlay changes, in order.
    static let changes: [Change] = {
        var list: [Change] = []
        for i in 0..<6 {
            list.append(Change(time: 0.6 + Double(i), from: rest - i, to: rest - i - 1, duration: tickDuration, curve: .easeOut))
        }
        list.append(Change(time: 7.4, from: rest - 6, to: rest, duration: restartDuration, curve: .easeInOut))
        return list
    }()

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func ease(_ t: Double, _ curve: Curve) -> Double {
        switch curve {
        case .easeOut: return 1 - pow(1 - t, 3)
        case .easeInOut: return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
        }
    }

    /// The four digits of `value` seconds as m:ss with a leading zero: [m tens, m ones, s tens, s ones].
    static func digits(_ value: Int) -> [Int] {
        let m = value / 60, s = value % 60
        return [m / 10, m % 10, s / 10, s % 10]
    }

    /// One digit window: rolling from `from` to `to`, `progress` 0-1, `up` when the value grows.
    struct Cell: Equatable {
        var from: Int
        var to: Int
        var progress: Double
        var up: Bool
    }

    /// The window `cell` (0-3) under a change that started `elapsed` seconds ago, or nil when the
    /// change leaves that digit alone. Changed digits start from the right, `stagger` apart.
    static func cell(_ cell: Int, _ change: Change, elapsed: Double) -> Cell? {
        let a = digits(change.from), b = digits(change.to)
        guard a[cell] != b[cell] else { return nil }
        let rank = (cell + 1..<4).filter { a[$0] != b[$0] }.count
        let p = clamp((elapsed - Double(rank) * stagger) / change.duration)
        return Cell(from: a[cell], to: b[cell], progress: ease(p, change.curve), up: change.to > change.from)
    }

    /// The four windows `t` seconds into the loop.
    static func cells(at t: Double) -> [Cell] {
        let start = digits(rest)
        var cells = start.map { Cell(from: $0, to: $0, progress: 1, up: false) }
        for change in changes where change.time <= t {
            for i in 0..<4 {
                if let c = cell(i, change, elapsed: t - change.time) { cells[i] = c }
            }
        }
        return cells
    }

    /// The colon's opacity `t` seconds into the loop: it dips on every tick.
    static func colon(at t: Double) -> Double {
        var dip = 0.0
        for change in changes where change.curve == .easeOut {
            let u = (t - change.time) / colonDuration
            if u > 0, u < 1 { dip = max(dip, sin(.pi * u)) }
        }
        return 1 - colonDip * dip
    }

    /// The bar's fill: the shown value, moving with the ones digit's roll.
    static func value(at t: Double) -> Double {
        var v = Double(rest)
        for change in changes where change.time <= t {
            let p = ease(clamp((t - change.time) / change.duration), change.curve)
            v = Double(change.from) + Double(change.to - change.from) * p
        }
        return v
    }
}

enum RollingCountdownDigitsPalette {
    static let background = Color(hex: 0x0B0B0C)
    static let ink = Color(hex: 0xF5F5F2)
    static let lime = Color(hex: 0xD4FF3F)
}

/// The digits' outlines and the window they roll in, for one font size.
struct RollingCountdownDigitsType {
    let size: CGFloat
    /// Each digit's outline with its origin on the baseline, and its advance.
    let digits: [(path: Path, advance: CGFloat)]
    let colon: (path: Path, advance: CGFloat)
    /// Every digit window is this wide (the widest digit's advance).
    let cell: CGFloat
    /// The window, from the baseline: its top (negative) and its height.
    let top: CGFloat
    let height: CGFloat
    var width: CGFloat { 4 * cell + colon.advance }

    static let fontName = "Anton-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "RollingCountdownDigits-Anton-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static func glyph(_ ch: Character, _ font: CTFont) -> (path: Path, advance: CGFloat) {
        var unichar = Array(String(ch).utf16)[0]
        var glyph = CGGlyph(0)
        CTFontGetGlyphsForCharacters(font, &unichar, &glyph, 1)
        var advance = CGSize.zero
        CTFontGetAdvancesForGlyphs(font, .horizontal, &glyph, &advance, 1)
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        let path = CTFontCreatePathForGlyph(font, glyph, &flip).map { Path($0) } ?? Path()
        return (path, advance.width)
    }

    /// The size at which "00:00" spans `width`, at most `limit`.
    init(width: CGFloat, limit: CGFloat) {
        _ = Self.registered
        func font(_ s: CGFloat) -> CTFont {
            (UIFont(name: Self.fontName, size: s) ?? .systemFont(ofSize: s, weight: .heavy)) as CTFont
        }
        let probe = font(100)
        let probeCell = (0...9).map { Self.glyph(Character("\($0)"), probe).advance }.max() ?? 50
        let probeWidth = 4 * probeCell + Self.glyph(":", probe).advance
        size = min(limit, floor(100 * width / probeWidth))
        let f = font(size)
        digits = (0...9).map { Self.glyph(Character("\($0)"), f) }
        colon = Self.glyph(":", f)
        cell = digits.map(\.advance).max() ?? size / 2
        let ink = digits[0].path.boundingRect
        let pad = 0.04 * size
        top = ink.minY - pad
        height = ink.height + 2 * pad
    }
}

struct RollingCountdownDigits: View {
    var round = "Round 3 of 5"
    var session = "Core · 20 min"
    var title = "PLANK HOLD"
    var next = "Next: Side plank"
    var nextTime = "0:45"
    var pause = "Pause"
    var skip = "Skip"
    var hint = "Drag the time up or down to change it"
    /// Counts down and restarts by itself, every `RollingCountdownDigitsMotion.loop` seconds.
    var autoPlay = false

    private typealias M = RollingCountdownDigitsMotion
    private typealias P = RollingCountdownDigitsPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the time is back at rest and the loop has started over.
    @State private var manual = false
    /// The value set by touch, and the last change of each digit window (with when it started).
    @State private var shown = M.rest
    @State private var changes: [(change: M.Change, date: Date)?] = [nil, nil, nil, nil]
    @State private var dragFrom: Int? = nil
    /// Changes on every touch, which restarts the loop behind a pause.
    @State private var touches = 0
    @State private var tick = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let type = RollingCountdownDigitsType(width: full.width - 48, limit: 180)
            TimelineView(.animation(paused: reduceMotion && !manual)) { context in
                let now = context.date
                let cells = cells(at: now)
                let colon = colonOpacity(at: now)
                let value = barValue(at: now)
                let live = liveOpacity(at: now)
                ZStack(alignment: .top) {
                    P.background

                    VStack(spacing: 0) {
                        HStack {
                            HStack(spacing: 8) {
                                Circle().fill(P.lime).frame(width: 8, height: 8).opacity(live)
                                Text(round).foregroundStyle(P.ink)
                            }
                            Spacer()
                            Text(session).foregroundStyle(P.ink.opacity(0.5))
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .padding(.top, safe.top + 14)
                        .padding(.horizontal, 24)

                        Spacer(minLength: 24)

                        Text(title)
                            .font(.system(size: 13, weight: .bold))
                            .tracking(2.4)
                            .foregroundStyle(P.lime)

                        digitsView(type, cells: cells, colon: colon, width: full.width)
                            .padding(.top, 20)
                            .accessibilityElement()
                            .accessibilityLabel(label(cells))

                        VStack(spacing: 12) {
                            GeometryReader { bar in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(P.ink.opacity(0.14))
                                    Capsule().fill(P.lime).frame(width: bar.size.width * M.clamp(value / M.total))
                                }
                            }
                            .frame(height: 4)
                            HStack {
                                Text(next)
                                Spacer()
                                Text(nextTime).monospacedDigit()
                            }
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(P.ink.opacity(0.55))
                        }
                        .frame(width: type.width)
                        .padding(.top, 28)

                        Spacer(minLength: 24)

                        HStack(spacing: 12) {
                            Text(pause)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(P.background)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(Capsule().fill(P.ink))
                                .accessibilityAddTraits(.isButton)
                            Text(skip)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(P.ink)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .overlay(Capsule().strokeBorder(P.ink.opacity(0.25), lineWidth: 1.5))
                                .accessibilityAddTraits(.isButton)
                        }
                        .padding(.horizontal, 24)

                        Text(hint)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(P.ink.opacity(0.4))
                            .padding(.top, 16)
                            .padding(.bottom, safe.bottom + 12)
                    }
                    .frame(width: full.width, height: full.height)
                }
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .gesture(drag, including: reduceMotion ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            tick.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private func digitsView(_ type: RollingCountdownDigitsType, cells: [M.Cell], colon: Double, width: CGFloat) -> some View {
        Canvas { context, size in
            let left = (size.width - type.width) / 2
            let baseline = -type.top
            for (i, cell) in cells.enumerated() {
                let x = left + CGFloat(i) * type.cell + (i >= 2 ? type.colon.advance : 0)
                var c = context
                c.clip(to: Path(CGRect(x: x, y: 0, width: type.cell, height: type.height)))
                let p = cell.progress
                // The old digit leaves upwards (downwards when the value grows); the new one follows
                // a little more than a window behind, so the stretched digits never overlap.
                let travel = M.travel * type.height
                let dir: CGFloat = cell.up ? 1 : -1
                if p < 1 { draw(&c, type, cell.from, x: x, baseline: baseline + dir * CGFloat(p) * travel, stretch: p, opacity: 1 - 0.5 * p) }
                draw(&c, type, cell.to, x: x, baseline: baseline - dir * CGFloat(1 - p) * travel, stretch: p, opacity: 0.5 + 0.5 * p)
            }
            let colonX = left + 2 * type.cell
            var c = context
            c.opacity = colon
            c.translateBy(x: colonX, y: baseline)
            c.fill(type.colon.path, with: .color(P.ink))
        }
        .frame(width: width, height: type.height)
    }

    /// A digit centred in its window, stretched vertically by up to 10 % mid-roll.
    private func draw(_ context: inout GraphicsContext, _ type: RollingCountdownDigitsType, _ digit: Int, x: CGFloat, baseline: CGFloat, stretch p: Double, opacity: Double) {
        var c = context
        let glyph = type.digits[digit]
        let s = 1 + M.stretch * sin(Double.pi * p)
        let middle = baseline + type.top + type.height / 2
        c.opacity = opacity
        c.translateBy(x: x + (type.cell - glyph.advance) / 2, y: middle)
        c.scaleBy(x: 1, y: s)
        c.translateBy(x: 0, y: baseline - middle)
        c.fill(glyph.path, with: .color(P.ink))
    }

    private func label(_ cells: [M.Cell]) -> String {
        let d = cells.map(\.to)
        return "\(d[0])\(d[1]) minutes \(d[2])\(d[3]) seconds"
    }

    private func cells(at date: Date) -> [M.Cell] {
        if manual {
            let start = M.digits(shown)
            return (0..<4).map { i in
                guard let last = changes[i] else { return M.Cell(from: start[i], to: start[i], progress: 1, up: false) }
                return M.cell(i, last.change, elapsed: date.timeIntervalSince(last.date))
                    ?? M.Cell(from: start[i], to: start[i], progress: 1, up: false)
            }
        }
        guard autoPlay, !reduceMotion else {
            return M.digits(M.rest).map { M.Cell(from: $0, to: $0, progress: 1, up: false) }
        }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return M.cells(at: max(0, t))
    }

    /// The live dot next to the round: 0.35-1, a cosine that starts full at the loop's start. It
    /// keeps the screen moving between ticks.
    private func liveOpacity(at date: Date) -> Double {
        guard autoPlay, !reduceMotion else { return 1 }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return 0.35 + 0.65 * (0.5 + 0.5 * cos(2 * Double.pi * t / M.pulse))
    }

    private func colonOpacity(at date: Date) -> Double {
        guard !manual, autoPlay, !reduceMotion else { return 1 }
        return M.colon(at: max(0, date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)))
    }

    private func barValue(at date: Date) -> Double {
        if manual {
            // The ones digit's roll carries the bar between whole seconds.
            guard let last = changes[3] else { return Double(shown) }
            let c = last.change
            let p = M.ease(M.clamp(date.timeIntervalSince(last.date) / c.duration), c.curve)
            return Double(c.from) + Double(c.to - c.from) * p
        }
        guard autoPlay, !reduceMotion else { return Double(M.rest) }
        return M.value(at: max(0, date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)))
    }

    /// Rolls every digit window that changes from the shown value to `value`.
    private func roll(to value: Int, at date: Date, duration: Double, curve: M.Curve) {
        guard value != shown else { return }
        let change = M.Change(time: 0, from: shown, to: value, duration: duration, curve: curve)
        // A window still rolling starts again from the digit it is heading for.
        for i in 0..<4 where M.digits(shown)[i] != M.digits(value)[i] {
            changes[i] = (change, date)
        }
        // The ones digit always carries the bar.
        changes[3] = (change, date)
        shown = value
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if dragFrom == nil {
                    // Take over from the loop at the value it shows (a roll in flight lands at once).
                    let current = cells(at: now).map(\.to)
                    let start = current[0] * 600 + current[1] * 60 + current[2] * 10 + current[3]
                    manual = true
                    shown = start
                    changes = [nil, nil, nil, nil]
                    dragFrom = start
                    touches += 1
                    tick.selectionChanged()
                }
                let steps = Int((-value.translation.height / M.dragStep).rounded(.towardZero))
                let target = min(599, max(0, (dragFrom ?? M.rest) + steps))
                if target != shown {
                    roll(to: target, at: now, duration: M.stepDuration, curve: .easeOut)
                    tick.selectionChanged()
                }
            }
            .onEnded { _ in
                dragFrom = nil
                touches += 1
            }
    }

    /// 2 s after the last touch the time rolls back to 01:02, and the loop starts over.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, dragFrom == nil else { return }
        roll(to: M.rest, at: Date(), duration: M.restartDuration, curve: .easeInOut)
        try? await Task.sleep(for: .seconds(M.restartDuration + 3 * M.stagger))
        guard !Task.isCancelled, dragFrom == nil else { return }
        origin = .now
        manual = false
    }
}

#Preview {
    RollingCountdownDigits(autoPlay: true)
}
