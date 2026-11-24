import CoreText
import SwiftUI
import UIKit

private typealias M = SpringDisclosureFaqMotion

// ---------------------------------------------------------------- content

private struct SpringDisclosureFaqItem {
    let q: String
    let a: String
}

private let springDisclosureFaqGroups: [(title: String, items: [SpringDisclosureFaqItem])] = [
    (
        "Cards",
        [
            .init(q: "How do I freeze my card?", a: "Open Cards, pick the card and switch on Freeze. Payments stop straight away, and you can unfreeze it the same way when it turns up."),
            .init(q: "What are my daily limits?", a: "You can spend up to £5,000 and take out £400 in cash each day. Change either one under Cards, then Limits."),
            .init(q: "Why was my payment declined?", a: "Usually the card is frozen, a limit was reached, or the shop asked for a check we could not confirm. The reason sits next to the payment in Activity."),
        ]
    ),
    (
        "Transfers and account",
        [
            .init(q: "When will my transfer arrive?", a: "Transfers to UK accounts arrive within two hours, most of them in seconds. Payments abroad take one to three working days."),
            .init(q: "Are there fees abroad?", a: "Paying by card abroad is free, at the exchange rate of the day. Cash is free up to £200 a month, then 2% of what you take out."),
            .init(q: "How do I close my account?", a: "Move your money out, then go to Profile, Account, Close account. We keep your statements available for six years."),
        ]
    ),
]
private let springDisclosureFaqItems = springDisclosureFaqGroups.flatMap(\.items)

// ---------------------------------------------------------------- look and layout (pt)

private enum Look {
    static let page = Color(hex: 0xF1EFE9)
    static let card = Color(hex: 0xFCFBF8)
    static let ink = Color(hex: 0x1B211E)
    static let mute = Color(hex: 0x69706B)
    static let body = Color(hex: 0x414A45) // answers: darker than the labels, they are what gets read
    static let openTint = Color.rgba(43, 106, 77, 0.07) // behind the open question and its answer
    static let accent = Color(hex: 0x2B6A4D)
    static let hairline = Color.rgba(27, 33, 30, 0.10)
    static let field = Color.rgba(27, 33, 30, 0.06)

    static let side = 16.0 // screen margin
    static let rowH = 56.0 // a closed row
    static let labelH = 30.0 // a group's title above its card
    static let groupGap = 22.0 // between the first card and the second title
    static let footGap = 20.0 // between the second card and the contact row
    static let footH = 64.0
    static let cardBase = Double(M.groupSize) * rowH
    static let card2Top = labelH + cardBase + groupGap + labelH
    static let footTop = card2Top + cardBase + footGap
    static let answerX = 16.0 // answer text inset, left
    static let answerRight = 20.0
    static let answerBottom = 18.0 // space under the last line
    static let answerFont = 16.0
    static let answerLine = 22.0
    static let answerKern = -0.1
    static let dot = 44.0 // the ghost finger
    static let dotX = 0.62 // its place across the card

    static let idle = 2.0 // s after the last touch, before the rows ease back to rest
    static let returnS = 1.1 // the ease back, then the film restarts at t = 0

    static let hair = 1 / max(UIScreen.main.scale, 1)
}

/// Each answer broken into its lines once (Core Text, at the answer's width), so the lines can be
/// drawn and revealed one by one.
private enum SpringDisclosureFaqLines {
    private static var cache: [Int: [[String]]] = [:]

    static func lines(width: Double) -> [[String]] {
        let key = Int((width * 4).rounded())
        if let hit = cache[key] { return hit }
        let font = UIFont.systemFont(ofSize: Look.answerFont)
        let out = springDisclosureFaqItems.map { item -> [String] in
            let text = NSAttributedString(string: item.a, attributes: [.font: font, .kern: Look.answerKern])
            let setter = CTFramesetterCreateWithAttributedString(text)
            let path = CGPath(rect: CGRect(x: 0, y: 0, width: width, height: 10_000), transform: nil)
            let frame = CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil)
            let ns = item.a as NSString
            return (CTFrameGetLines(frame) as? [CTLine] ?? []).map { line in
                let r = CTLineGetStringRange(line)
                return ns.substring(with: NSRange(location: r.location, length: r.length)).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        cache[key] = out
        return out
    }
}

/// What the screen is drawn from at one instant.
private struct SpringDisclosureFaqFrame {
    var mode: SpringDisclosureFaqMode
    var tr: SpringDisclosureFaqTransitions
    var tau: Double
    var reduced: Bool
    var heights: [Double]

    func open(_ j: Int, lag: Double = 0) -> Double { M.openAt(mode, tr, reduced, j, tau - lag) }
    func push(_ first: Int, _ last: Int, _ target: Int, at time: Double? = nil) -> Double {
        M.pushAt(mode, tr, reduced, heights, first, last, target, time ?? tau)
    }

    /// Top of row `index` inside the stage at `time` (pt).
    func rowTop(_ index: Int, at time: Double) -> Double {
        let first = index < M.groupSize ? 0 : M.groupSize
        let cardTop = index < M.groupSize ? Look.labelH : Look.card2Top + push(0, M.groupSize - 1, M.groupSize, at: time)
        return cardTop + Double(index - first) * Look.rowH + push(first, index - 1, index, at: time)
    }
}

/// A help centre whose questions open on a spring. One row is open at a time: its answer's lines
/// rise out of a blur one after another, the "+" turns into a "−", the rows below are pushed down
/// each a beat after the one above, and the row that was open closes in the same move.
struct SpringDisclosureFaq: View {
    static let loop: Double = SpringDisclosureFaqMotion.loop

    var autoPlay = true

    init(autoPlay: Bool = true) { self.autoPlay = autoPlay }

    /// The last thing a finger did. Everything after it (the idle wait, the ease back to rest and
    /// the film starting over) is derived from the clock, so no state is written per frame.
    private struct Session {
        var manual = false
        var tr = M.restTransitions()
        var lastTouch = 0.0
        var loopStart = Date.now.timeIntervalSinceReferenceDate
        var held = false // a finger is down on a row: the idle wait starts when it lifts
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var session = Session()
    @State private var presses = 0
    @State private var pressAt = 0.0 // the last press-in and lift, to refuse a tap held longer than RN's 500 ms
    @State private var liftAt = 0.0
    @State private var settled = true // without the film: nothing is moving, the clock may stop
    @State private var settleTask: Task<Void, Never>?
    private let haptic = UISelectionFeedbackGenerator()

    private var film: Bool { autoPlay && !reduceMotion }

    private func resolve(_ now: Double) -> (mode: SpringDisclosureFaqMode, tr: SpringDisclosureFaqTransitions, tau: Double) {
        guard film else {
            return (.manual, session.manual ? session.tr : M.restTransitions(), now)
        }
        if !session.manual {
            let t = (now - session.loopStart).truncatingRemainder(dividingBy: M.loop)
            return (.auto, session.tr, max(0, t))
        }
        if session.held { return (.manual, session.tr, now) }
        let returnAt = session.lastTouch + Look.idle
        if now <= returnAt { return (.manual, session.tr, now) }
        let restart = returnAt + Look.returnS
        if now <= restart { return (.manual, M.retarget(.manual, session.tr, false, returnAt, returnAt, -1), now) }
        return (.auto, session.tr, (now - restart).truncatingRemainder(dividingBy: M.loop))
    }

    private func toggle(_ index: Int) {
        let now = Date.now.timeIntervalSinceReferenceDate
        let cur = resolve(now)
        let next = M.retarget(cur.mode, cur.tr, reduceMotion, cur.tau, now, index)
        session = Session(manual: true, tr: next, lastTouch: now, loopStart: session.loopStart, held: presses > 0)
        haptic.selectionChanged()
        guard !film else { return }
        settled = false
        settleTask?.cancel()
        settleTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.6))
            if !Task.isCancelled { settled = true }
        }
    }

    /// Press-in holds the rows where they are for as long as the finger stays; the 2 s run from the lift.
    private func pressChanged(_ pressed: Bool) {
        let now = Date.now.timeIntervalSinceReferenceDate
        if pressed {
            pressAt = now
            presses += 1
            if session.manual, now <= session.lastTouch + Look.idle { session.held = true }
        } else {
            liftAt = now
            presses = max(0, presses - 1)
            if presses == 0, session.held {
                session.held = false
                session.lastTouch = now
            }
        }
    }

    var body: some View {
        GeometryReader { geo in
            let fullH = geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom
            let compact = fullH < 740
            let contentW = min(geo.size.width, 480)
            let cardW = contentW - Look.side * 2
            let answerW = cardW - Look.answerX - Look.answerRight
            let lines = SpringDisclosureFaqLines.lines(width: answerW)
            let heights = lines.map { Double($0.count) * Look.answerLine + Look.answerBottom }
            let stageH = Look.footTop + Look.footH + (heights.max() ?? 0) + 8

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    nav(compact: compact)
                    if !compact {
                        Text("Help")
                            .font(.system(size: 34, weight: .bold))
                            .tracking(0.3)
                            .foregroundStyle(Look.ink)
                            .padding(.top, 6)
                            .accessibilityAddTraits(.isHeader)
                    }
                    search.padding(.top, compact ? 8 : 14)

                    TimelineView(.animation(paused: !film && settled)) { context in
                        let r = resolve(context.date.timeIntervalSinceReferenceDate)
                        let frame = SpringDisclosureFaqFrame(mode: r.mode, tr: r.tr, tau: r.tau, reduced: reduceMotion, heights: heights)
                        stage(frame, cardW: cardW, answerW: answerW, lines: lines)
                            .frame(width: cardW, height: stageH, alignment: .topLeading)
                    }
                    .frame(width: cardW, height: stageH, alignment: .topLeading)
                    .padding(.top, compact ? 14 : 20)
                }
                .padding(.top, compact ? 2 : 6)
                .padding(.bottom, 16)
                .padding(.horizontal, Look.side)
                .frame(width: contentW)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
        .background {
            Rectangle()
                .fill(Look.page)
                .colorEffect(ShaderLibrary.springDisclosureFaqGrain(.float(Float(UIScreen.main.scale))))
                .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            session = Session()
            haptic.prepare()
        }
        .onDisappear { settleTask?.cancel() }
    }

    // ------------------------------------------------------------ the still parts

    private func nav(compact: Bool) -> some View {
        HStack {
            navButton("chevron.left")
            Spacer(minLength: 0)
            if compact {
                Text("Help").font(.system(size: 17, weight: .semibold)).foregroundStyle(Look.ink)
                Spacer(minLength: 0)
            }
            navButton("ellipsis")
        }
        .frame(height: 44)
    }

    private func navButton(_ symbol: String) -> some View {
        Circle()
            .fill(Look.card)
            .frame(width: 36, height: 36)
            .shadow(color: .rgba(27, 33, 30, 0.10), radius: 1, y: 1)
            .shadow(color: .rgba(27, 33, 30, 0.06), radius: 8, y: 6)
            .overlay {
                Image(systemName: symbol)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Look.ink)
            }
            .accessibilityHidden(true)
    }

    private var search: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14.5, weight: .medium))
                .frame(width: 16, height: 16)
            Text("Search Alder help").font(.system(size: 17))
            Spacer(minLength: 0)
        }
        .foregroundStyle(Look.mute)
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(Look.field, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Look.mute)
            .padding(.leading, 4)
    }

    private func foot(width: Double) -> some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Look.accent)
                .frame(width: 36, height: 36)
                .overlay {
                    Image(systemName: "bubble.left.fill").font(.system(size: 15)).foregroundStyle(Look.card)
                }
            VStack(alignment: .leading, spacing: 1) {
                Text("Chat with us").font(.system(size: 16, weight: .semibold)).tracking(-0.2).foregroundStyle(Look.ink)
                Text("Replies in about 4 minutes").font(.system(size: 13)).foregroundStyle(Look.mute)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.system(size: 12.5, weight: .semibold)).foregroundStyle(Look.mute)
        }
        .padding(.horizontal, 14)
        .frame(width: width, height: Look.footH)
        .background { plate }
        .accessibilityElement(children: .combine)
    }

    /// A card's plate: paper, a hairline white edge and two shadows.
    private var plate: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return shape
            .fill(Look.card)
            .shadow(color: .rgba(27, 33, 30, 0.09), radius: 1, y: 1)
            .shadow(color: .rgba(27, 33, 30, 0.07), radius: 14, y: 12)
            .overlay { shape.strokeBorder(Color.white.opacity(0.9), lineWidth: Look.hair) }
    }

    // ------------------------------------------------------------ the stage

    private func stage(_ f: SpringDisclosureFaqFrame, cardW: Double, answerW: Double, lines: [[String]]) -> some View {
        let shift2 = f.push(0, M.groupSize - 1, M.groupSize)
        let shiftFoot = shift2 + f.push(M.groupSize, M.rowCount - 1, M.rowCount)
        return ZStack(alignment: .topLeading) {
            label(springDisclosureFaqGroups[0].title)
            card(0, f, width: cardW, answerW: answerW, lines: lines)

            label(springDisclosureFaqGroups[1].title)
                .offset(y: Look.labelH + Look.cardBase + Look.groupGap + shift2)
            card(1, f, width: cardW, answerW: answerW, lines: lines)

            foot(width: cardW).offset(y: Look.footTop + shiftFoot)

            ghost(f, x: cardW * Look.dotX)
        }
    }

    /// One grouped card. Its plate and its clip grow with the answers inside it; the second card is
    /// also pushed down by the first one's.
    private func card(_ group: Int, _ f: SpringDisclosureFaqFrame, width: Double, answerW: Double, lines: [[String]]) -> some View {
        let first = group * M.groupSize
        let last = first + M.groupSize - 1
        let top = group == 0 ? Look.labelH : Look.card2Top
        let shift = group == 0 ? 0 : f.push(0, M.groupSize - 1, M.groupSize)
        // the bottom edge travels with the last row: its own answer plus what pushes it
        let grown = f.push(first, last - 1, last) + f.heights[last] * f.open(last)
        let h = max(Look.cardBase, Look.cardBase + grown)
        return ZStack(alignment: .topLeading) {
            ForEach(first...last, id: \.self) { index in
                row(index, f, width: width, answerW: answerW, lines: lines[index])
                    .offset(y: Double(index - first) * Look.rowH + f.push(first, index - 1, index))
            }
        }
        .frame(width: width, height: h, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .background { plate }
        .offset(y: top + shift)
    }

    // ------------------------------------------------------------ a question

    private func row(_ index: Int, _ f: SpringDisclosureFaqFrame, width: Double, answerW: Double, lines: [String]) -> some View {
        let item = springDisclosureFaqItems[index]
        let local = index % M.groupSize
        let o = f.open(index)
        let oc = M.clamp01(o)
        let openH = Look.rowH + f.heights[index]
        var press = 0.0
        if f.mode == .auto {
            for k in 0..<M.eventT.count where M.eventRow[k] == index { press = max(press, M.touchPress(k, f.tau)) }
        }
        let expanded = f.tr.to[index] > 0.5 && f.mode == .manual || f.mode == .auto && M.filmTarget(index, f.tau) > 0.5

        return Look.card
            .frame(width: width, height: Look.rowH)
            // the open row's plate and accent bar reach down behind its answer; the row below covers what is not open
            .overlay(alignment: .topLeading) {
                Look.openTint.frame(width: width, height: openH).opacity(oc)
            }
            .overlay(alignment: .topLeading) {
                // the bar grows down from the question's top edge with the same spring (overshoot included)
                UnevenRoundedRectangle(bottomTrailingRadius: 2, topTrailingRadius: 2)
                    .fill(Look.accent)
                    .frame(width: 3, height: openH)
                    .scaleEffect(x: 1, y: max(0, o), anchor: .top)
                    .opacity(M.clamp01(o * 2))
            }
            .overlay(alignment: .topLeading) {
                if local > 0 {
                    Look.hairline.frame(width: width - Look.answerX, height: Look.hair).offset(x: Look.answerX)
                }
            }
            .overlay { Color.rgba(27, 33, 30, 0.05).opacity(press) }
            .overlay(alignment: .leading) {
                Text(item.q)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.2)
                    .lineLimit(1)
                    .foregroundStyle(springDisclosureFaqMix(oc))
                    .padding(.leading, Look.answerX)
                    .frame(width: width - 56, alignment: .leading)
            }
            .overlay(alignment: .trailing) {
                // the upright stroke of the "+" lies down onto the other one and fades: "−"
                ZStack {
                    Circle().fill(Look.field)
                    Capsule().fill(Look.ink).frame(width: 12, height: 2)
                    Capsule().fill(Look.ink).frame(width: 2, height: 12).rotationEffect(.degrees(90 * o)).opacity(1 - oc)
                }
                .frame(width: 28, height: 28)
                .padding(.trailing, 14)
            }
            .overlay(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { k, text in
                        answerLine(text, k: k, row: index, f, width: answerW)
                    }
                }
                .frame(width: answerW, height: Look.answerLine, alignment: .topLeading)
                .offset(x: Look.answerX, y: Look.rowH)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .overlay {
                Button {
                    // like RN's Tap (maxDuration 500 ms): a press held longer is not a tap
                    let now = Date.now.timeIntervalSinceReferenceDate
                    let fresh = now - liftAt < 0.3 || presses > 0
                    if fresh, (presses > 0 ? now : liftAt) - pressAt > 0.5 { return }
                    toggle(index)
                } label: {
                    Color.clear.contentShape(Rectangle())
                }
                .buttonStyle(SpringDisclosureFaqPressStyle(onPress: pressChanged))
                .accessibilityLabel(expanded ? "\(item.q) \(item.a)" : item.q)
                .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            }
    }

    /// One line of an answer: it samples its row's `open` a little later than the line above.
    @ViewBuilder
    private func answerLine(_ text: String, k: Int, row: Int, _ f: SpringDisclosureFaqFrame, width: Double) -> some View {
        let lag = f.reduced ? 0 : M.lineStagger * Double(k)
        let c = M.lineReveal(f.open(row, lag: lag))
        if c > 0 {
            let moving = !f.reduced && c < 1
            Text(text)
                .font(.system(size: Look.answerFont))
                .kerning(Look.answerKern)
                .lineLimit(1)
                .fixedSize()
                .foregroundStyle(Look.body)
                .frame(width: width, height: Look.answerLine, alignment: .leading)
                .blur(radius: moving ? (1 - c) * M.lineBlur : 0)
                .opacity(c)
                .offset(y: Double(k) * Look.answerLine + (f.reduced ? 0 : (1 - c) * M.lineRise))
        }
    }

    // ------------------------------------------------------------ the ghost finger

    /// In the film a soft dot shows where the finger taps; it stays where it landed while the rows move.
    @ViewBuilder
    private func ghost(_ f: SpringDisclosureFaqFrame, x: Double) -> some View {
        if f.mode == .auto {
            let alphas = (0..<M.eventT.count).map { M.touchAlpha($0, f.tau) }
            let best = alphas.indices.max { alphas[$0] < alphas[$1] } ?? 0
            let alpha = alphas[best]
            if alpha > 0 {
                let y = f.rowTop(M.eventRow[best], at: M.eventT[best]) + Look.rowH / 2
                Circle()
                    .fill(Color.rgba(27, 33, 30, 0.14))
                    .overlay { Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1.5) }
                    .frame(width: Look.dot, height: Look.dot)
                    .scaleEffect(1 - 0.16 * M.touchPress(best, f.tau))
                    .opacity(alpha)
                    .offset(x: x - Look.dot / 2, y: y - Look.dot / 2)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }
}

/// Ink → green as a row opens (mixed in sRGB, like interpolateColor).
private func springDisclosureFaqMix(_ o: Double) -> Color {
    Color(
        .sRGB,
        red: (27 + (43 - 27) * o) / 255,
        green: (33 + (106 - 33) * o) / 255,
        blue: (30 + (77 - 30) * o) / 255
    )
}

/// Row highlight under a real finger: in over 90 ms, out over 240 ms.
private struct SpringDisclosureFaqPressStyle: ButtonStyle {
    var onPress: (Bool) -> Void = { _ in }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Color.rgba(27, 33, 30, 0.05).opacity(configuration.isPressed ? 1 : 0))
            .animation(.linear(duration: configuration.isPressed ? 0.09 : 0.24), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in onPress(pressed) }
    }
}
