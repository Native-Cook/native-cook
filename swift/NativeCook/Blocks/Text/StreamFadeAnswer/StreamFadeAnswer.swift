import SwiftUI
import UIKit

/// Stream Fade Answer — one turn of a chat with a travel assistant. The question sits in its bubble; the answer
/// streams in word by word (StreamFadeAnswerStreamingText: every word fades up from a blur, 6 pt below, 28 ms
/// after the one before, in a layout that is final before the first word shows): a heading, two paragraphs, three
/// itinerary rows that slide in behind a hairline, a closing tip and a cost chip that counts up. A grey line with
/// a moving light band stands in while the assistant thinks, and the thread scrolls to keep the newest line in view.
///
/// Everything is a function of one stream clock S (StreamFadeAnswerMotion.swift). The 10 s loop moves it; a tap
/// jumps it to the end; a horizontal drag scrubs it. 2 s after the last touch S eases to the end and the loop
/// restarts there. Under Reduce Motion the finished answer is shown, with no loop and no gestures.
struct StreamFadeAnswer: View {
    var autoPlay: Bool = true

    static let loop: Double = StreamFadeAnswerMotion.loop

    private typealias M = StreamFadeAnswerMotion
    private typealias T = StreamFadeAnswerThread

    private static func rgb(_ hex: Int, _ a: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255).opacity(a)
    }
    private static let page = rgb(0xF6F4EF)
    private static let ink = rgb(0x1D1C1A)
    private static let soft = rgb(0x55524C)
    private static let mute = rgb(0x8A867E)
    private static let line = rgb(0x1D1C1A, 0.12)
    private static let bubble = rgb(0x2B2A27)
    private static let accent = rgb(0x2F6B4F)
    private static let accentSoft = rgb(0x2F6B4F, 0.11)
    private static let field = rgb(0xFDFCFA)
    private static let hairline = 1 / UIScreen.main.scale

    private static let headerH = 44.0
    private static let composerH = 64.0
    private static let side = 20.0
    private static let maxContent = 560.0
    /// Past either end the answer leans this many points per (rubber-banded) second.
    private static let lean = 36.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = StreamFadeAnswerModel()

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { tl in
                    screen(tl.date, size: full.size, ins: ins)
                }
            }
            .ignoresSafeArea()
        }
        .background(Self.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.restart(Date()) }
    }

    // MARK: - Screen

    private func screen(_ date: Date, size: CGSize, ins: EdgeInsets) -> some View {
        let W = size.width, H = size.height
        let th = model.thread(W)
        let top = ins.top + Self.headerH
        let bottomPad = max(ins.bottom, 10)
        let viewH = max(0, H - top - (Self.composerH + bottomPad))
        let f = model.frame(date.timeIntervalSinceReferenceDate, th, viewH: viewH, film: film)

        return ZStack(alignment: .topLeading) {
            thread(th, f)
                .frame(width: W, height: th.height, alignment: .topLeading)
                .offset(y: -f.scroll)
                .frame(width: W, height: viewH, alignment: .topLeading)
                .clipped()
                .contentShape(Rectangle())
                .gesture(scrub(th, W: W, viewH: viewH), including: reduceMotion ? .none : .all)
                .offset(y: top)

            header(ins)
                .frame(width: W, height: top)

            composer(W: W, bottomPad: bottomPad)
                .frame(width: W, height: H, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: W, height: H, alignment: .topLeading)
    }

    // MARK: - Thread

    private func thread(_ th: T, _ f: StreamFadeAnswerModel.Frame) -> some View {
        let S = f.stream
        return ZStack(alignment: .topLeading) {
            // The question.
            StreamFadeAnswerStreamingText(layout: th.question, color: Self.page)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(width: th.bubble.width, height: th.bubble.height, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Self.bubble)
                        .shadow(color: Self.rgb(0x1D1C1A, 0.22), radius: 1, y: 1)
                        .shadow(color: Self.rgb(0x1D1C1A, 0.12), radius: 12, y: 10)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.1))
                        .mask(
                            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.black)
                                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).offset(y: 1).blendMode(.destinationOut))
                                .compositingGroup()
                        )
                )
                .offset(x: th.bubble.minX, y: th.bubble.minY)

            // The assistant.
            HStack(spacing: 8) {
                Circle()
                    .fill(LinearGradient(stops: [
                        .init(color: Self.rgb(0x4F9272), location: 0),
                        .init(color: Self.accent, location: 0.55),
                        .init(color: Self.rgb(0x214F3A), location: 1),
                    ], startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1)))
                    .frame(width: 22, height: 22)
                    .overlay(Circle().fill(Self.rgb(0xF6F4EF, 0.92)).frame(width: 7, height: 7))
                Text("Juniper")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Self.soft)
            }
            .frame(height: 22)
            .offset(x: th.left, y: th.labelY)

            if f.think.alpha > 0.001 {
                thinking(th, band: f.think.band)
                    .opacity(f.think.alpha)
                    .offset(x: th.left, y: th.answerY)
            }

            // The answer: one group, so it fades and leans as one.
            ZStack(alignment: .topLeading) {
                StreamFadeAnswerStreamingText(layout: th.heading, time: S - th.headingAt, color: Self.ink)
                    .offset(x: th.left, y: th.headingY)
                ForEach(th.paragraphs.indices, id: \.self) { i in
                    StreamFadeAnswerStreamingText(layout: th.paragraphs[i].layout, time: S - th.paragraphs[i].at, color: Self.ink)
                        .offset(x: th.left, y: th.paragraphs[i].y)
                }
                ForEach(th.rows.indices, id: \.self) { i in
                    row(T.rows[i], S: S, at: th.rows[i].at, width: th.width)
                        .offset(x: th.left, y: th.rows[i].y)
                }
                StreamFadeAnswerStreamingText(layout: th.tip, time: S - th.tipAt, color: Self.soft)
                    .offset(x: th.left, y: th.tipY)
                chip(S: S, at: th.chipAt)
                    .offset(x: th.left, y: th.chipY)
            }
            .frame(width: th.left + th.width, height: th.height, alignment: .topLeading)
            .opacity(f.answerAlpha)
            .offset(x: f.over * Self.lean)
        }
    }

    /// "Checking fares…": grey text with a lighter band travelling across its letters.
    private func thinking(_ th: T, band: Double) -> some View {
        let L = th.thinking
        let c = -70 + (L.widest + 70) * band
        return Canvas { ctx, _ in
            // Baseline 20 pt below the top, as in the React Native build.
            ctx.translateBy(x: 0, y: 20 - L.firstBase)
            ctx.fill(L.linePaths[0], with: .linearGradient(
                Gradient(colors: [Self.rgb(0xA5A199), Self.rgb(0xE9E6DF), Self.rgb(0xA5A199)]),
                startPoint: CGPoint(x: c - 70, y: 0), endPoint: CGPoint(x: c + 70, y: 0)))
        }
        .frame(width: th.width, height: 28)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// One day of the itinerary: a hairline draws in from the left, the row rises in behind it.
    private func row(_ r: T.Row, S: Double, at: Double, width: Double) -> some View {
        let lp = M.easeOutCubic(M.seg(S, at, at + M.lineDur))
        let p = M.easeOutCubic(M.seg(S, at + 0.06, at + 0.06 + M.rowDur))
        return HStack(spacing: 12) {
            Text(r.day)
                .font(.system(size: 16, weight: .bold).monospacedDigit())
                .foregroundStyle(Self.accent)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Self.accentSoft))
            VStack(alignment: .leading, spacing: 1) {
                Text(r.title).font(.system(size: 16, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1)
                Text(r.detail).font(.system(size: 14)).foregroundStyle(Self.mute).lineLimit(1)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            Text(r.cost)
                .font(.system(size: 16, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.ink)
        }
        .frame(width: width, height: T.rowH)
        .opacity(p)
        .offset(y: M.rowRise * (1 - p))
        .overlay(alignment: .topLeading) {
            Rectangle().fill(Self.line).frame(width: width * lp, height: Self.hairline)
        }
    }

    /// The total: pops in, and the number counts up to it.
    private func chip(S: Double, at: Double) -> some View {
        let p = M.easeOutCubic(M.seg(S, at, at + M.chipDur))
        let amount = Int((Double(T.total) * M.easeOutCubic(M.seg(S, at + 0.05, at + 0.05 + M.chipCount))).rounded())
        let strong = Font.system(size: 15, weight: .bold).monospacedDigit()
        return HStack(spacing: 0) {
            Text("€").font(.system(size: 15, weight: .bold)).foregroundStyle(Self.ink)
            Text("\(amount)").font(strong).foregroundStyle(Self.ink)
                .frame(width: 32, alignment: .leading)
            Text("of €400").font(.system(size: 15, weight: .medium).monospacedDigit()).foregroundStyle(Self.mute)
            Circle().fill(Self.mute.opacity(0.7)).frame(width: 3, height: 3).padding(.horizontal, 9)
            Text("€28 to spare").font(.system(size: 15, weight: .semibold).monospacedDigit()).foregroundStyle(Self.accent)
        }
        .fixedSize()
        .padding(.horizontal, 14)
        .frame(height: T.chipH)
        .background(
            Capsule().fill(Self.field)
                .shadow(color: Self.rgb(0x1D1C1A, 0.08), radius: 1, y: 1)
                .shadow(color: Self.rgb(0x1D1C1A, 0.07), radius: 10, y: 8)
        )
        .overlay(Capsule().strokeBorder(Self.line, lineWidth: Self.hairline))
        .scaleEffect(0.94 + 0.06 * p)
        .offset(y: 6 * (1 - p))
        .opacity(p)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("€\(T.total) of €400, €28 to spare")
    }

    // MARK: - Header and composer

    private func header(_ ins: EdgeInsets) -> some View {
        HStack(spacing: 0) {
            Image(systemName: "chevron.left")
                .resizable().scaledToFit().fontWeight(.semibold)
                .frame(width: 16, height: 16)
                .frame(width: 44, height: 44, alignment: .leading)
            Spacer(minLength: 0)
            Text("Lisbon trip").font(.system(size: 17, weight: .semibold))
            Spacer(minLength: 0)
            Image(systemName: "square.and.pencil")
                .resizable().scaledToFit().fontWeight(.medium)
                .frame(width: 16.5, height: 16.5)
                .frame(width: 44, height: 44, alignment: .trailing)
        }
        .foregroundStyle(Self.ink)
        .padding(.horizontal, 14)
        .padding(.top, ins.top)
        .background(Self.page)
        .overlay(alignment: .bottom) { Rectangle().fill(Self.line).frame(height: Self.hairline) }
    }

    private func composer(W: Double, bottomPad: Double) -> some View {
        VStack(spacing: 0) {
            LinearGradient(stops: [
                .init(color: Self.rgb(0xF6F4EF, 0), location: 0),
                .init(color: Self.rgb(0xF6F4EF, 0.86), location: 0.5),
                .init(color: Self.page, location: 1),
            ], startPoint: .top, endPoint: .bottom)
            .frame(height: 26)
            Self.page.frame(height: Self.composerH - 26 + 8)
                .overlay(alignment: .top) {
                    HStack {
                        Text("Ask a follow-up").font(.system(size: 16)).foregroundStyle(Self.mute)
                        Spacer(minLength: 0)
                        Circle().fill(Self.accent).frame(width: 32, height: 32)
                            .overlay(
                                Image(systemName: "arrow.up")
                                    .resizable().scaledToFit().fontWeight(.bold)
                                    .frame(width: 12.5, height: 12.5)
                                    .foregroundStyle(Self.page)
                            )
                    }
                    .padding(.leading, 18)
                    .padding(.trailing, 7)
                    .frame(width: min(W - Self.side * 2, Self.maxContent), height: 46)
                    .background(
                        Capsule().fill(Self.field)
                            .shadow(color: Self.rgb(0x1D1C1A, 0.07), radius: 1, y: 1)
                            .shadow(color: Self.rgb(0x1D1C1A, 0.09), radius: 13, y: 10)
                    )
                    .overlay(Capsule().strokeBorder(Self.line, lineWidth: Self.hairline))
                    .offset(y: -8)
                }
        }
        .padding(.bottom, bottomPad)
    }

    // MARK: - Touch: tap to finish, drag sideways to scrub

    private func scrub(_ th: T, W: Double, viewH: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if model.drag(dx: v.translation.width, dy: v.translation.height, now: now, th, W: W, viewH: viewH, film: film) {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                if model.release(dx: v.translation.width, dy: v.translation.height, vx: v.velocity.width, now: now, th, W: W, viewH: viewH, film: film) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }
}

// MARK: - The thread, measured before anything is shown

/// Positions (pt) and start times (s) of everything in the turn.
struct StreamFadeAnswerThread {
    struct Row { let day, title, detail, cost: String }

    static let question = "Plan a 3-day trip to Lisbon under €400"
    static let thinkingText = "Checking fares and places to stay"
    static let headingText = "Three days in Lisbon"
    static let paragraphTexts = [
        "Here is a plan that comes to €372, flights from Berlin included. I picked the second week of May: fares drop once the holidays are over, and the hills are still cool enough to walk all day.",
        "You would stay three nights in a guesthouse in Alfama at €52 a night, a short climb from the river and two minutes from the tram.",
    ]
    static let rows = [
        Row(day: "1", title: "Alfama and the river", detail: "Tram 28, Santa Luzia lookout, sardines", cost: "€118"),
        Row(day: "2", title: "Belém by bike", detail: "Riverside path, custard tarts, the tower", cost: "€131"),
        Row(day: "3", title: "Sintra day trip", detail: "Train from Rossio, Pena Palace, sunset", cost: "€123"),
    ]
    static let tipText = "Buy the 24-hour transit pass each morning. It covers the trams, the metro and the lift up to Carmo."
    static let total = 372

    static let rowH = 56.0
    static let chipH = 38.0
    /// Room kept under the newest line while the thread follows the stream.
    private static let followPad = 16.0
    /// The scroll follows the stream through a short running average, so a new line never jerks it.
    private static let followLag = 0.12

    let screenW: Double
    let left: Double
    let width: Double
    let question: StreamFadeAnswerTextLayout
    let thinking: StreamFadeAnswerTextLayout
    let bubble: CGRect
    let labelY: Double
    let answerY: Double
    let heading: StreamFadeAnswerTextLayout
    let headingY: Double
    let headingAt: Double
    var paragraphs: [(layout: StreamFadeAnswerTextLayout, y: Double, at: Double)] = []
    var rows: [(y: Double, at: Double)] = []
    let tip: StreamFadeAnswerTextLayout
    let tipY: Double
    let tipAt: Double
    let chipY: Double
    let chipAt: Double
    let height: Double
    /// Stream time of the finished answer.
    let end: Double
    /// Where each block starts on the stream clock: scrubbing ticks at these.
    var marks: [Double] = []
    /// Bottom of what has been revealed, as (stream time, y) key frames.
    var keyS: [Double] = []
    var keyY: [Double] = []

    init(_ W: Double) {
        typealias M = StreamFadeAnswerMotion
        screenW = W
        width = min(W - 40, 560)
        left = (W - width) / 2
        var y = 14.0

        let textMax = min(width * 0.86, 340) - 28
        question = StreamFadeAnswerTextLayout(Self.question, width: textMax, fontSize: 16, lineHeight: 22)
        thinking = StreamFadeAnswerTextLayout(Self.thinkingText, width: 10_000, fontSize: 16, weight: .medium)
        let bw = question.widest.rounded(.up) + 28
        bubble = CGRect(x: left + width - bw, y: y, width: bw, height: question.height + 20)
        y += bubble.height + 22
        labelY = y
        y += 22 + 10
        answerY = y

        var at = 0.0
        keyS.append(-1)
        keyY.append(answerY + 28)

        heading = StreamFadeAnswerTextLayout(Self.headingText, width: width, fontSize: 22, weight: .bold, lineHeight: 28, seed: 1)
        headingY = y
        headingAt = at
        keyS += [at, at + heading.lastStart]
        keyY += [y + 28, y + heading.height]
        y += heading.height + 6
        at += heading.lastStart + M.blockPause

        for (i, text) in Self.paragraphTexts.enumerated() {
            let m = StreamFadeAnswerTextLayout(text, width: width, fontSize: 16, lineHeight: 23, seed: Double(2 + i))
            paragraphs.append((m, y, at))
            marks.append(at)
            keyS += [at, at + m.lastStart]
            keyY += [y + 23, y + m.height]
            y += m.height + (i == Self.paragraphTexts.count - 1 ? 12 : 10)
            at += m.lastStart + M.blockPause
        }

        for _ in Self.rows {
            rows.append((y, at))
            marks.append(at)
            keyS.append(at + M.rowDur * 0.5)
            keyY.append(y + Self.rowH)
            y += Self.rowH
            at += M.rowGap
        }
        y += 12
        at += M.rowDur * 0.5

        tip = StreamFadeAnswerTextLayout(Self.tipText, width: width, fontSize: 16, lineHeight: 23, seed: 7)
        tipY = y
        tipAt = at
        marks.append(at)
        keyS += [at, at + tip.lastStart]
        keyY += [y + 23, y + tip.height]
        y += tip.height + 14
        at += tip.lastStart + M.blockPause

        chipY = y
        chipAt = at
        marks.append(at)
        keyS.append(at + M.chipDur)
        keyY.append(y + Self.chipH)
        y += Self.chipH
        height = y
        end = at + 0.05 + M.chipCount + 0.2
    }

    /// Bottom edge of what the stream has revealed at S: linear between the key frames.
    func revealed(_ S: Double) -> Double {
        if S <= keyS[0] { return keyY[0] }
        for i in 1..<keyS.count where S < keyS[i] {
            let u = (S - keyS[i - 1]) / max(0.0001, keyS[i] - keyS[i - 1])
            return keyY[i - 1] + (keyY[i] - keyY[i - 1]) * u
        }
        return keyY[keyY.count - 1]
    }

    /// How far the thread is scrolled up at S so the newest line stays in the viewport.
    func scroll(_ S: Double, viewH: Double) -> Double {
        var sum = 0.0
        for k in 0..<4 { sum += revealed(S - Self.followLag * Double(k)) }
        return max(0, sum / 4 + Self.followPad - viewH)
    }

    /// The scroll the loop shows at loop time t: at rest, gliding back for the next turn, then following the stream.
    func loopScroll(_ t: Double, playing: Bool, viewH: Double) -> Double {
        typealias M = StreamFadeAnswerMotion
        if !playing || t < M.scrollBackA { return scroll(end, viewH: viewH) }
        if t < M.scrollBackB { return scroll(end, viewH: viewH) * (1 - M.easeInOutCubic(M.seg(t, M.scrollBackA, M.scrollBackB))) }
        return scroll(t - M.streamAt, viewH: viewH)
    }

    func blockIndex(_ S: Double) -> Int { marks.reduce(0) { $0 + (S >= $1 ? 1 : 0) } }
}

// MARK: - Clocks

/// Holds the loop clock and, under a finger, the stream time the hand moves. A plain reference type on purpose:
/// the screen is drawn from the timeline's date, and nothing here invalidates the view.
final class StreamFadeAnswerModel {
    private typealias M = StreamFadeAnswerMotion

    struct Frame {
        var stream = 0.0
        var scroll = 0.0
        var answerAlpha = 1.0
        /// Seconds past either end while scrubbing (rubber-banded).
        var over = 0.0
        var think = (alpha: 0.0, band: 0.0)
    }

    private static let idle = 2.0
    /// A full-width drag covers this share of the stream.
    private static let scrubSpan = 0.8

    private var cached: StreamFadeAnswerThread?
    private var loopStart = Date().timeIntervalSinceReferenceDate
    private var last = 0.0
    private var touching = false
    /// Under touch: the stream time, its speed after a release (s/s), and the state of the hand.
    private var manual = 0.0
    private var speed = 0.0
    private var dragging = false
    private var failed = false
    private var skipping = false
    private var dragFrom = 0.0
    private var idleFor = 0.0
    /// What the loop was showing when the finger arrived, eased away so nothing pops.
    private var heldAlpha = 1.0
    private var heldThink = 0.0
    private var heldScroll = 0.0

    func restart(_ date: Date) {
        loopStart = date.timeIntervalSinceReferenceDate
        touching = false
    }

    func thread(_ W: Double) -> StreamFadeAnswerThread {
        if let c = cached, c.screenW == W { return c }
        let made = StreamFadeAnswerThread(W)
        cached = made
        return made
    }

    private func clock(_ now: Double) -> Double {
        max(0, now - loopStart).truncatingRemainder(dividingBy: M.loop)
    }

    /// Everything the screen draws at `now`.
    func frame(_ now: Double, _ T: StreamFadeAnswerThread, viewH: Double, film: Bool) -> Frame {
        let dt = min(max(0, now - last), 0.05)
        last = now
        if touching { step(dt, T, now) }

        var f = Frame()
        if touching {
            let s = manual
            f.stream = s
            f.scroll = T.scroll(s, viewH: viewH) + heldScroll
            f.answerAlpha = heldAlpha
            f.over = s < 0 ? s : (s > T.end ? s - T.end : 0)
            f.think = (heldThink, 0.5)
        } else if film {
            let t = clock(now)
            f.stream = M.stream(t, end: T.end)
            f.scroll = T.loopScroll(t, playing: true, viewH: viewH)
            f.answerAlpha = M.answerAlpha(t)
            f.think = M.thinking(t)
        } else {
            f.stream = T.end
            f.scroll = T.scroll(T.end, viewH: viewH)
        }
        return f
    }

    private func step(_ dt: Double, _ T: StreamFadeAnswerThread, _ now: Double) {
        let ease = min(1, dt * 10)
        heldAlpha += (1 - heldAlpha) * ease
        heldThink *= 1 - ease
        heldScroll *= 1 - ease
        if dragging { return }
        idleFor += dt
        var s = manual, v = speed
        if idleFor > Self.idle {
            // Back to rest, which is the first frame of the loop.
            s += (T.end - s) * min(1, dt * 7)
            v = 0
            if abs(T.end - s) < 0.01 {
                manual = T.end
                speed = 0
                skipping = false
                loopStart = now
                touching = false
                return
            }
        } else if skipping {
            s += (T.end - s) * min(1, dt * 9)
            if T.end - s < 0.01 {
                s = T.end
                skipping = false
            }
        } else {
            // A release keeps its speed, slowing against drag; past an end a spring brings it back.
            s += v * dt
            v *= exp(-dt * 4.5)
            if s < 0 {
                s += (0 - s) * min(1, dt * 12)
                v = 0
            } else if s > T.end {
                s += (T.end - s) * min(1, dt * 12)
                v = 0
            }
        }
        manual = s
        speed = v
    }

    private func takeOver(_ now: Double, _ T: StreamFadeAnswerThread, viewH: Double, film: Bool) {
        if !touching {
            let t = clock(now)
            let s = film ? M.stream(t, end: T.end) : T.end
            let shown = T.loopScroll(t, playing: film, viewH: viewH)
            let start = max(0, min(T.end, s))
            heldAlpha = film ? M.answerAlpha(t) : 1
            heldThink = film ? M.thinking(t).alpha : 0
            heldScroll = shown - T.scroll(start, viewH: viewH)
            manual = start
            speed = 0
            touching = true
        }
        idleFor = 0
    }

    /// A drag sample (translation from touch-down). The scrub starts after 8 pt sideways and gives way to a 14 pt
    /// vertical move. Returns true when the scrub crosses the start of a block (a selection tick).
    func drag(dx: Double, dy: Double, now: Double, _ T: StreamFadeAnswerThread, W: Double, viewH: Double, film: Bool) -> Bool {
        if failed { return false }
        if !dragging {
            if abs(dy) >= 14 { failed = true; return false }
            if abs(dx) < 8 { return false }
            takeOver(now, T, viewH: viewH, film: film)
            dragging = true
            skipping = false
            dragFrom = max(0, min(T.end, manual))
        }
        let before = T.blockIndex(manual)
        let raw = dragFrom + (dx / (W * Self.scrubSpan)) * T.end
        manual = raw < 0 ? M.rubber(raw) : (raw > T.end ? T.end + M.rubber(raw - T.end) : raw)
        idleFor = 0
        return T.blockIndex(manual) != before
    }

    /// The finger lifts. Returns true for a tap, which runs the stream to its end.
    func release(dx: Double, dy: Double, vx: Double, now: Double, _ T: StreamFadeAnswerThread, W: Double, viewH: Double, film: Bool) -> Bool {
        defer { failed = false }
        if dragging {
            dragging = false
            idleFor = 0
            speed = manual < 0 || manual > T.end ? 0 : (vx / (W * Self.scrubSpan)) * T.end
            return false
        }
        if failed || hypot(dx, dy) > 10 { return false }
        takeOver(now, T, viewH: viewH, film: film)
        speed = 0
        skipping = true
        return true
    }
}
