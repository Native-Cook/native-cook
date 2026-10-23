import CoreText
import SwiftUI

/// Literal price data, camera views, and the single clock of the lesson.
enum CandlePatternWalkthroughMotion {
    static let BASE: Int = 28
    static let SWING_HIGHS: [Int] = [3, 11, 19, 26]
    static let BREAK_FROM: Int = 27
    static let PEAK: Int = 31
    static let RETEST: Int = 34
    static let CONFIRM: Int = 35
    static let LAST: Int = 43
    static let ENTRY: Double = 42
    static let STOP: Double = 34
    static let RATIO: Int = 3
    static let ZONE_FROM: Double = 35.6
    static let ZONE_CANDLES: Int = 10
    static let LINE_TO: Int = 48
    static let LOOP: Double = 11
    static let SWING_AT: Double = 0.8
    static let SWING_STEP: Double = 0.3
    static let CIRCLE_IN: Double = 0.25
    static let SWING_OUT: [Double] = [2.9, 3.2]
    static let TREND_DRAW: [Double] = [2.3, 2.9]
    static let TREND_TYPE: [Double] = [2.9, 3.3]
    static let PRINT_GROW: Double = 0.1
    static let BREAK_PRINT: Double = 3.6
    static let PRINT_STEP: Double = 0.12
    static let ARROW_DRAW: [Double] = [4.1, 4.45]
    static let BREAK_OUT: [Double] = [4.6, 4.9]
    static let RETEST_PRINT: Double = 5
    static let ZIGZAG_DRAW: [Double] = [5.4, 5.85]
    static let RETEST_LABEL: Double = 5.6
    static let CONFIRM_PRINT: Double = 6
    static let CONFIRM_MARK: Double = 6.15
    static let NOTES_OUT: [Double] = [6.6, 6.85]
    static let BOX_IN: Double = 6.85
    static let ZONE_RIGHT: [Double] = [7.05, 7.3]
    static let ZONE_UP: [Double] = [7.3, 7.9]
    static let RALLY_PRINT: Double = 7.95
    static let RALLY_STEP: Double = 0.13
    static let BURST: Double = 8.95
    static let BURST_FOR: Double = 0.9
    static let FADE: [Double] = [9.7, 10.1]
    static let REPRINT: Double = 10.15
    static let REPRINT_STEP: Double = 0.022
    static let CHAPTER_STARTS: [Double] = [0.8, 2.3, 3.3, 4.6, 5.95, 6.6, 7.8]
    static let STOPS: [Double] = [2.3, 3.3, 4.6, 5.95, 6.6, 7.8, 9.7, 11]
    static let GRAVITY: Double = 1200
    static let CANDLES: [[Double]] = [
        [38, 42.8, 37.2, 42],
        [42, 47.6, 41.3, 47],
        [47, 48.2, 44.1, 45],
        [45, 58, 44.6, 56],
        [56, 56.8, 48.2, 49],
        [49, 50.1, 42, 43],
        [43, 44, 37.1, 38],
        [38, 41.2, 37.4, 40],
        [40, 40.9, 36.2, 37],
        [37, 42.9, 36.5, 42],
        [42, 47.8, 41.1, 47],
        [47, 51.74, 46.3, 50.5],
        [50.5, 50.6, 44.2, 45],
        [45, 46.1, 40.3, 41],
        [41, 41.8, 35.4, 36],
        [36, 39, 35.2, 38],
        [38, 38.7, 33.4, 34],
        [34, 39.8, 33.6, 39],
        [39, 42.9, 38.3, 42],
        [42, 45.48, 41.4, 44.5],
        [44.5, 44.6, 39.2, 40],
        [40, 40.7, 34.3, 35],
        [35, 35.6, 30.2, 31],
        [31, 34.8, 30.4, 34],
        [34, 34.9, 32.1, 33],
        [33, 37.9, 32.5, 37],
        [37, 40, 36.4, 38.5],
        [38.5, 38.9, 34.3, 35],
        [35, 38.2, 34.4, 37.8],
        [37.8, 43.2, 37.3, 42.6],
        [42.6, 46.9, 41.9, 46.2],
        [46.2, 49.8, 45.4, 49],
        [49, 49.6, 44.2, 44.8],
        [44.8, 45.4, 40.1, 40.6],
        [40.6, 41.2, 34.2, 36.4],
        [36.4, 42.6, 35.9, 42],
        [42, 47.8, 41.4, 47],
        [47, 47.9, 44.2, 45],
        [45, 52.6, 44.6, 52],
        [52, 58.7, 51.4, 58],
        [58, 58.6, 55.2, 56],
        [56, 62.8, 55.6, 62],
        [62, 67.4, 61.5, 66.8],
        [66.8, 71.2, 66.1, 70.5],
    ]
    static let VIEWS: [[Double]] = [
        [0, 13.5, 44, 1],
        [3.3, 13.5, 44, 1],
        [3.9, 21, 43, 1.08],
        [4.6, 21, 43, 1.08],
        [5.2, 28, 41, 1.45],
        [6.6, 28, 41, 1.45],
        [7.2, 34, 49, 1.12],
        [7.8, 34, 49, 1.12],
        [9.1, 38, 52, 1.2],
    ]
    static let CONFETTI: [[Double]] = [
        [-260, 580, 0, 0, 0, 16],
        [-180, 680, 1, 3, 0.6, 11],
        [-95, 740, 3, 1, -0.5, 13],
        [-20, 620, 2, 2, 0.4, 16],
        [70, 780, 1, 0, -0.7, 12],
        [140, 660, 0, 3, 0, 9],
        [220, 580, 3, 2, 0.5, 12],
        [300, 490, 2, 0, -0.4, 14],
        [-330, 450, 2, 1, 0.3, 12],
        [25, 440, 3, 3, -0.6, 9],
        [-140, 500, 1, 2, 0.5, 12],
        [180, 400, 0, 1, 0, 11],
    ]
    static let FIRST = SWING_HIGHS[0]
    static let FINAL = SWING_HIGHS[SWING_HIGHS.count - 1]
    static let SLOPE = (CANDLES[FINAL][1] - CANDLES[FIRST][1]) / Double(FINAL - FIRST)
    static let PRINT_AT = CANDLES.indices.map { index in
        if index < BASE { return 0.0 }
        if index <= PEAK { return BREAK_PRINT + Double(index - BASE) * PRINT_STEP }
        if index <= RETEST { return RETEST_PRINT + Double(index - PEAK - 1) * PRINT_STEP }
        if index == CONFIRM { return CONFIRM_PRINT }
        return RALLY_PRINT + Double(index - CONFIRM - 1) * RALLY_STEP
    }
    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ time: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((time - from) / (to - from))
        return u * u * (3 - 2 * u)
    }
    static func spring(_ elapsed: Double, _ omega: Double, _ damping: Double) -> Double {
        if elapsed <= 0 { return 0 }
        let decay = damping * omega
        let ringing = omega * sqrt(1 - damping * damping)
        return 1 - exp(-decay * elapsed) * (cos(ringing * elapsed) + decay / ringing * sin(ringing * elapsed))
    }
    static func lineAt(_ index: Double) -> Double {
        return CANDLES[FIRST][1] + SLOPE * (index - Double(FIRST))
    }
    static func chapterAt(_ time: Double) -> Int { CHAPTER_STARTS.lastIndex { time >= $0 } ?? 0 }
    static func camera(_ time: Double, _ still: Bool) -> [Double] {
        if time >= FADE[1] || time < VIEWS[1][0] { return Array(VIEWS[0].dropFirst()) }
        if time >= VIEWS.last![0] { return Array(VIEWS.last!.dropFirst()) }
        var k = 0
        while time >= VIEWS[k + 1][0] { k += 1 }
        let a = VIEWS[k], b = VIEWS[k + 1]
        let u = (time - a[0]) / (b[0] - a[0])
        let e = still ? (u < 0.5 ? 0.0 : 1.0) : u * u * (3 - 2 * u)
        return (1...3).map { a[$0] + (b[$0] - a[$0]) * e }
    }
    static func growth(_ index: Int, _ time: Double, _ still: Bool) -> Double {
        let at = PRINT_AT[index]
        let g = clamp01(index < BASE ? (time < FADE[1] ? 1 : (time - REPRINT - Double(index) * REPRINT_STEP) / PRINT_GROW) : (time >= FADE[1] ? 0 : (time - at) / PRINT_GROW))
        return still ? (g > 0 ? 1 : 0) : g
    }
}

enum CandlePatternWalkthroughStyle {
    static let TITLE_SIZE: Double = 22
    static let TITLE_HEIGHT: Double = 28
    static let TITLE_TRACKING: Double = -0.4
    static let HEADER_PAD: Double = 12
    static let HINT_SIZE: Double = 13
    static let HINT_HEIGHT: Double = 18
    static let HINT_MARGIN: Double = 4
    static let FOOTER_PAD: Double = 16
    static let SEGMENT_HEIGHT: Double = 3
    static let SEGMENT_RADIUS: Double = 1.5
    static let CAPTION_MARGIN: Double = 14
    static let CAPTION_HEIGHT: Double = 26
    static let STEP_WIDTH: Double = 32
    static let STEP_SIZE: Double = 13
    static let STEP_HEIGHT: Double = 18
    static let CHAPTER_SIZE: Double = 19
    static let CHAPTER_HEIGHT: Double = 24
    static let CHAPTER_IN: Double = 0.18
    static let CHAPTER_OUT: Double = 0.12
    static let PAN_SLOP: Double = 12
    static let TAP_SLOP: Double = 10
    static let STOP_EPSILON: Double = 0.01
    static let SCRUB_END_EPSILON: Double = 0.001
    static let UNBLURRED_GLOW: Double = 0.15

    static let PITCH: Double = 13
    static let BODY_RATIO: Double = 0.58
    static let WICK: Double = 1.5
    static let UNIT: Double = 11
    static let MIN_CANDLES_ACROSS: Double = 30
    static let MIN_PRICE_SPAN: Double = 54
    static let GLOW_BLUR: Double = 6
    static let GLOW_ALPHA: Double = 0.45
    static let LINE_WIDTH: Double = 2.5
    static let CIRCLE_RADIUS: Double = 7
    static let CIRCLE_WIDTH: Double = 1.5
    static let ARROW_WIDTH: Double = 2
    static let ARROW_HEAD: Double = 9
    static let DOT: Double = 3.5
    static let CONFETTI_WIDTH: Double = 2.6
    static let GRID_STEP: Double = 16
    static let GRID_DOT: Double = 0.9
    static let HEADER: Double = 72
    static let FOOTER: Double = 92
    static let EDGE_FADE: Double = 36
    static let MARGIN: Double = 20
    static let SEGMENT_GAP: Double = 4
    static let LABEL_SIZE: Double = 17
    static let LABEL_LINE: Double = 18
    static let BIG_SIZE: Double = 21
    static let SMALL_SIZE: Double = 13
    static let RATIO_SIZE: Double = 26
    static let POP_OMEGA: Double = 22
    static let POP_DAMPING: Double = 0.5
    static let SLIDE_OMEGA: Double = 11
    static let SLIDE_DAMPING: Double = 0.78
    static let LABEL_FADE: Double = 0.15
    static let POP_FADE: Double = 0.06
    static let USER_HOLD: Double = 3
    static let VEIL: Double = 0.35
    static let CONFETTI_BLUR: Double = 4
    static let BACKGROUND = Color(hex: 0x000000)
    static let INK = Color(hex: 0xffffff)
    static let MUTE = Color(hex: 0x7d7d7d)
    static let TRACK = Color(hex: 0x262626)
    static let GRID = Color(hex: 0x151515)
    static let RISK = Color(hex: 0x7a2e3e)
    static let REWARD = Color.rgba(31, 77, 69, 0.85)
    static let DASH = [7.0, 6.0]
    static let CONFETTI_COLORS = [Color(hex: 0x3de0ff), Color(hex: 0xd86bff), Color(hex: 0xffb03b), Color.white]
}

private typealias M = CandlePatternWalkthroughMotion
private typealias S = CandlePatternWalkthroughStyle
private typealias Pen = CandlePatternWalkthroughPen
private func choose<T>(_ condition: Bool, _ yes: T, _ no: T) -> T { condition ? yes : no }
private func f(_ value: Int) -> Double { Double(value) }
private func whole(_ value: Double) -> String { String(Int(value)) }
private func prefix(_ value: String, _ fraction: Double) -> String { String(value.prefix(Int(ceil(fraction * Double(value.count))))) }

private final class CandlePatternWalkthroughPen {
    var path = Path()
    var roundCaps = true
    var roundJoin = false
    func move(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
    func line(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
    func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) { if w > 0 && h > 0 { path.addRect(CGRect(x: x, y: y, width: w, height: h)) } }
    func circle(_ x: Double, _ y: Double, _ r: Double) { path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
    func arc(_ x: Double, _ y: Double, _ r: Double, _ start: Double, _ sweep: Double) {
        var part = Path()
        part.addArc(center: CGPoint(x: x, y: y), radius: r, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
        path.addPath(part)
    }
    func close() { path.closeSubpath() }
}

private struct CandlePatternWalkthroughInk {
    var context: GraphicsContext
    func fill(_ path: Pen, _ color: Color, _ alpha: Double) {
        var layer = context; layer.opacity *= alpha
        layer.fill(path.path, with: .color(color))
    }
    func stroke(_ path: Pen, _ color: Color, _ width: Double, _ alpha: Double, _ trim: Double, _ dash: [Double]) {
        guard trim > 0, alpha > 0 else { return }
        var layer = context; layer.opacity *= alpha
        layer.stroke(path.path.trimmedPath(from: 0, to: trim), with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: path.roundCaps ? .round : .butt, lineJoin: path.roundJoin ? .round : .miter, dash: dash.map { CGFloat($0) }))
    }
    func label(_ lines: [String], _ typed: String?, _ x: Double, _ y: Double, _ rotation: Double, _ scale: Double, _ alpha: Double, _ size: Double, _ black: Bool) {
        guard alpha > 0, scale > 0 else { return }
        let font = UIFont.systemFont(ofSize: size, weight: black ? .black : .heavy) as CTFont
        var layer = context; layer.opacity *= alpha
        layer.translateBy(x: x, y: y); layer.rotate(by: .radians(rotation)); layer.scaleBy(x: scale, y: scale)
        for (index, line) in lines.enumerated() {
            let full = CTLineCreateWithAttributedString(NSAttributedString(string: line, attributes: [.font: font]))
            let width = CTLineGetTypographicBounds(full, nil, nil, nil)
            let baseline = -Double(lines.count - 1 - index) * S.LABEL_LINE
            layer.draw(Text(typed ?? line).font(.system(size: size, weight: black ? .black : .heavy)).foregroundStyle(S.INK), at: CGPoint(x: -width / 2, y: baseline + CTFontGetDescent(font)), anchor: .bottomLeading)
        }
    }
}

struct CandlePatternWalkthroughDrawing {
    var swingHighLabel: String
    var trendLineLabel: String
    var breakoutLabel: String
    var retestLabel: String
    var confirmationLabel: String
    var ratioPrefix: String
    var upColor: Color
    var downColor: Color

    func draw(_ context: GraphicsContext, _ width: Double, _ height: Double, _ t: Double, _ veil: Double, _ still: Bool, _ pass: Int) {
        var base = context
        if pass == 1 || pass == 4 { base.addFilter(.blur(radius: S.GLOW_BLUR)) }
        if pass == 6 { base.addFilter(.blur(radius: S.CONFETTI_BLUR)) }
        let ink = CandlePatternWalkthroughInk(context: base)
        let swingLines = swingHighLabel.components(separatedBy: "\n")
        let view = M.camera(t, still)
        let px = min(S.PITCH, width / S.MIN_CANDLES_ACROSS)
        let py = min(S.UNIT, height / S.MIN_PRICE_SPAN)
        func x(_ index: Double) -> Double { width / 2 + (index - view[0]) * px * view[2] }
        func y(_ price: Double) -> Double { height / 2 - (price - view[1]) * py * view[2] }
        let v = M.camera(t, still)
        let pitch = min(S.PITCH, width / S.MIN_CANDLES_ACROSS)
        let unit = min(S.UNIT, height / S.MIN_PRICE_SPAN)
        let ratioOf = pitch / S.PITCH
        let scale = v[2] * ratioOf
        let chartOpacity = choose(t >= M.FADE[1], 1, 1 - M.smooth(t, M.FADE[0], M.FADE[1])) * veil
        let notesOpacity = choose(t >= M.FADE[1], 0, 1 - M.smooth(t, M.FADE[0], M.FADE[1])) * veil
        let notesOut = 1 - M.smooth(t, M.NOTES_OUT[0], M.NOTES_OUT[1])
        let angle = atan2(-(M.lineAt(1) - M.lineAt(0)) * unit, pitch)
        if (pass == 0) {
            let grid = Pen()
            var gx = S.GRID_STEP / 2
            while (gx < width) {
                var gy = S.GRID_STEP / 2
                while (gy < height) { grid.circle(gx, gy, S.GRID_DOT); gy += S.GRID_STEP }
                gx += S.GRID_STEP
            }
            ink.fill(grid, S.GRID, 1)
        }
        if (pass == 0 || pass == 5) {
            let slide = choose(still, 1, M.spring(t - M.BOX_IN, S.SLIDE_OMEGA, S.SLIDE_DAMPING))
            let home = x(M.ZONE_FROM)
            let bx = home + (1 - slide) * max(0, width - home)
            let bw = f(M.ZONE_CANDLES) * pitch * v[2]
            let right = M.smooth(t, M.ZONE_RIGHT[0], M.ZONE_RIGHT[1])
            let up = M.smooth(t, M.ZONE_UP[0], M.ZONE_UP[1])
            let multiple = 1 + f(M.RATIO - 1) * up
            let entry = y(M.ENTRY)
            let top = y(M.ENTRY + (M.ENTRY - M.STOP) * multiple)
            let shown = choose(still, M.smooth(t, M.BOX_IN, M.BOX_IN + S.LABEL_FADE), choose(t >= M.BOX_IN, 1, 0)) * notesOpacity
            if (pass == 0) {
                let risk = Pen(); risk.rect(bx, entry, bw, y(M.STOP) - entry)
                let reward = Pen(); reward.rect(bx, top, bw * right, entry - top)
                ink.fill(reward, S.REWARD, shown)
                ink.fill(risk, S.RISK, shown)
            } else {
                let count = min(f(M.RATIO), 1 + floor(up * f(M.RATIO - 1) + 0.5))
                ink.label([ratioPrefix + whole(f(M.RATIO))], ratioPrefix + whole(count), bx + bw / 2, (entry + y(M.STOP)) / 2 + S.RATIO_SIZE * 0.36 * scale, 0, scale, shown, S.RATIO_SIZE, true)
            }
        }
        if (pass == 1 || pass == 2) {
            let ups = Pen(); let downs = Pen()
            for i in 0..<M.CANDLES.count {
                let c = M.CANDLES[i]
                let g = M.growth(i, t, still)
                let cx = x(f(i))
                let reach = pitch * v[2]
                if (g > 0 && cx >= -reach && cx <= width + reach) {
                    let p = choose(c[3] >= c[0], ups, downs)
                    let now = c[0] + (c[3] - c[0]) * g
                    let top = y(max(c[0], now))
                    let bottom = y(min(c[0], now))
                    let high = y(c[0] + (c[1] - c[0]) * g)
                    let low = y(c[0] + (c[2] - c[0]) * g)
                    let body = reach * S.BODY_RATIO
                    let wick = max(1, S.WICK * ratioOf * v[2])
                    p.rect(cx - body / 2, top, body, max(1, bottom - top))
                    p.rect(cx - wick / 2, high, wick, low - high)
                }
            }
            let alpha = chartOpacity * choose(pass == 1, S.GLOW_ALPHA, 1)
            ink.fill(ups, upColor, alpha); ink.fill(downs, downColor, alpha)
        }
        if (pass == 3) {
            let circles = Pen(); circles.roundCaps = false
            let off = 1 - M.smooth(t, M.SWING_OUT[0], M.SWING_OUT[1])
            for k in 0..<M.SWING_HIGHS.count {
                let i = M.SWING_HIGHS[k]
                let at = M.SWING_AT + f(k) * M.SWING_STEP
                let p = M.clamp01((t - at) / M.CIRCLE_IN)
                if (p > 0) { circles.arc(x(f(i)), y(M.CANDLES[i][1]), S.CIRCLE_RADIUS * scale, -90, choose(still, 359.9, min(359.9, 360 * p))) }
                let shown = choose(still, M.smooth(t, at, at + S.LABEL_FADE), M.clamp01((t - at) / S.POP_FADE))
                let pop = choose(still, 1, M.spring(t - at, S.POP_OMEGA, S.POP_DAMPING))
                ink.label(swingLines, nil, x(f(i)), y(M.CANDLES[i][1]) - (S.CIRCLE_RADIUS + 7) * scale, 0, scale * (0.4 + 0.6 * pop), shown * off * notesOpacity, S.LABEL_SIZE, false)
            }
            ink.stroke(circles, S.INK, S.CIRCLE_WIDTH, off * notesOpacity, 1, [])
        }
        if (pass == 4 || pass == 5) {
            let line = Pen(); line.move(x(f(M.SWING_HIGHS[0])), y(M.lineAt(f(M.SWING_HIGHS[0])))); line.line(x(f(M.LINE_TO)), y(M.lineAt(f(M.LINE_TO))))
            let u = M.clamp01((t - M.TREND_DRAW[0]) / (M.TREND_DRAW[1] - M.TREND_DRAW[0]))
            let trim = choose(still, choose(t >= M.TREND_DRAW[0], 1, 0), 1 - (1 - u) * (1 - u))
            let shown = choose(still, M.smooth(t, M.TREND_DRAW[0], M.TREND_DRAW[0] + S.LABEL_FADE), 1)
            ink.stroke(line, S.INK, S.LINE_WIDTH * choose(pass == 4, 2, 1), shown * notesOpacity * choose(pass == 4, 0.35, 1), trim, [])
        }
        if (pass == 5) {
            let trendShown = choose(still, M.smooth(t, M.TREND_TYPE[0], M.TREND_TYPE[0] + S.LABEL_FADE), choose(t >= M.TREND_TYPE[0], 1, 0)) * notesOpacity
            let typed = prefix(trendLineLabel, choose(still, 1, M.clamp01((t - M.TREND_TYPE[0]) / (M.TREND_TYPE[1] - M.TREND_TYPE[0]))))
            ink.label([trendLineLabel], typed, x(7.4) + sin(angle) * 10 * scale, y(M.lineAt(7.4)) - cos(angle) * 10 * scale, angle, scale, trendShown, S.BIG_SIZE, false)
            let ax = x(f(M.BREAK_FROM) + 0.4); let ay = y(M.CANDLES[M.BREAK_FROM][2] - 1.6)
            let bx = x(f(M.PEAK) + 1.4); let by = y(M.CANDLES[M.PEAK][1] + 3.5)
            let length = hypot(bx - ax, by - ay)
            let ux = (bx - ax) / length; let uy = (by - ay) / length
            let head = S.ARROW_HEAD * scale
            let arrow = Pen(); arrow.roundJoin = true; arrow.move(ax, ay); arrow.line(bx, by)
            arrow.move(bx - head * (ux - uy * 0.7), by - head * (uy + ux * 0.7)); arrow.line(bx, by); arrow.line(bx - head * (ux + uy * 0.7), by - head * (uy - ux * 0.7))
            let arrowEnd = choose(still, choose(t >= M.ARROW_DRAW[0], 1, 0), M.smooth(t, M.ARROW_DRAW[0], M.ARROW_DRAW[1]))
            let breakShown = choose(still, M.smooth(t, M.ARROW_DRAW[0], M.ARROW_DRAW[0] + S.LABEL_FADE), choose(t >= M.ARROW_DRAW[0], 1, 0)) * (1 - M.smooth(t, M.BREAK_OUT[0], M.BREAK_OUT[1]))
            ink.stroke(arrow, S.INK, S.ARROW_WIDTH, breakShown * notesOpacity, arrowEnd, [])
            let dot = Pen(); dot.circle(ax, ay, S.DOT * scale); ink.fill(dot, S.INK, breakShown * notesOpacity)
            let pop = choose(still, 1, M.spring(t - M.ARROW_DRAW[0] - 0.1, S.POP_OMEGA, S.POP_DAMPING))
            ink.label([breakoutLabel], nil, ax, ay + 28 * scale, 0, scale * (0.4 + 0.6 * pop), breakShown * notesOpacity * choose(still, 1, M.clamp01((t - M.ARROW_DRAW[0] - 0.1) / S.POP_FADE)), S.BIG_SIZE, false)
            let zig = Pen(); zig.move(x(f(M.BREAK_FROM) + 1.6), y(M.lineAt(f(M.BREAK_FROM) + 1.6))); zig.line(x(f(M.PEAK)), y(M.CANDLES[M.PEAK][1])); zig.line(x(f(M.RETEST)), y(M.CANDLES[M.RETEST][2]))
            let zigEnd = choose(still, choose(t >= M.ZIGZAG_DRAW[0], 1, 0), M.smooth(t, M.ZIGZAG_DRAW[0], M.ZIGZAG_DRAW[1]))
            let zigShown = choose(still, M.smooth(t, M.ZIGZAG_DRAW[0], M.ZIGZAG_DRAW[0] + S.LABEL_FADE), 1) * notesOut * notesOpacity
            ink.stroke(zig, S.INK, S.ARROW_WIDTH, zigShown, zigEnd, S.DASH)
            let retestShown = choose(still, M.smooth(t, M.RETEST_LABEL, M.RETEST_LABEL + S.LABEL_FADE), M.clamp01((t - M.RETEST_LABEL) / 0.12)) * notesOut * notesOpacity
            ink.label([retestLabel], nil, x(f(M.PEAK) - 0.6) - sin(angle) * 14 * scale, y(M.lineAt(f(M.PEAK) - 0.6)) + cos(angle) * 14 * scale, angle, scale, retestShown, S.SMALL_SIZE, false)
            let c = M.CANDLES[M.CONFIRM]; let pad = 3 * scale
            let half = pitch * v[2] * S.BODY_RATIO / 2 + pad
            let top = y(min(c[1], c[3])) - pad
            let box = Pen(); box.roundCaps = false; box.rect(x(f(M.CONFIRM)) - half, top, half * 2, y(c[0]) + pad - top)
            let confirmShown = choose(still, M.smooth(t, M.CONFIRM_MARK, M.CONFIRM_MARK + S.LABEL_FADE), M.clamp01((t - M.CONFIRM_MARK) / 0.1)) * notesOut * notesOpacity
            ink.stroke(box, S.INK, S.CIRCLE_WIDTH, confirmShown, 1, [])
            let rise = choose(still, 0, (1 - M.smooth(t, M.CONFIRM_MARK, M.CONFIRM_MARK + 0.25)) * 8)
            ink.label([confirmationLabel], nil, x(f(M.CONFIRM)), y(c[1]) - (10 - rise) * scale, 0, scale, confirmShown, S.SMALL_SIZE, false)
        }
        if (pass == 6 || pass == 7) {
            let u = t - M.BURST
            if (u >= 0 && u <= M.BURST_FOR) {
                let elapsed = choose(still, M.BURST_FOR * 0.45, u)
                let shown = choose(still, min(M.smooth(u, 0, 0.15), 1 - M.smooth(u, M.BURST_FOR - 0.2, M.BURST_FOR)), 1 - M.smooth(u, M.BURST_FOR * 0.55, M.BURST_FOR)) * notesOpacity
                for slot in 0..<S.CONFETTI_COLORS.count {
                    let path = Pen(); path.roundJoin = true; path.roundCaps = pass == 7
                    for k in 0..<M.CONFETTI.count {
                        let p = M.CONFETTI[k]
                        if (p[3] == f(slot)) {
                            let cx = x(f(M.LAST)) + p[0] * elapsed
                            let cy = y(M.CANDLES[M.LAST][1]) - p[1] * elapsed + 0.5 * M.GRAVITY * elapsed * elapsed
                            let turn = p[4] * elapsed * tau
                            let r = p[5] / 2
                            if (p[2] == 0) { path.circle(cx, cy, r) }
                            else {
                                let corners = choose(p[2] == 2, 3, 4)
                                let start = choose(p[2] == 2, -Double.pi / 2, Double.pi / 4)
                                for c in 0..<corners {
                                    let a = turn + start + f(c) * tau / f(corners)
                                    let px = cx + cos(a) * r * choose(p[2] == 3, 1.2, 1)
                                    let py = cy + sin(a) * r * choose(p[2] == 3, 1.2, 1)
                                    if (p[2] == 1) { if (c < 2) { path.move(px, py); path.line(2 * cx - px, 2 * cy - py) } }
                                    else if (c == 0) { path.move(px, py) }
                                    else { path.line(px, py) }
                                }
                                if (p[2] != 1) { path.close() }
                            }
                        }
                    }
                    ink.stroke(path, S.CONFETTI_COLORS[slot], S.CONFETTI_WIDTH * choose(pass == 6, 2, 1), shown * choose(pass == 6, 0.5, 1), 1, [])
                }
            }
        }
    }
}
