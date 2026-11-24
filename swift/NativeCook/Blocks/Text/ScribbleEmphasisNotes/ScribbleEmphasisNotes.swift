import SwiftUI
import UIKit

/// Scribble Emphasis Notes — "Folio": a page of study notes that a marker annotates by itself, on real measured
/// text. A red pen circles the key term, a highlighter swipes a phrase, a blue pen underlines a number with a
/// wave, a brace holds the last sentence in the margin, and "exam!" is handwritten under it with an arrow.
///
/// The geometry is ScribbleEmphasisNotesStrokes (built once per layout), the measured text
/// ScribbleEmphasisNotesPage, the numbers ScribbleEmphasisNotesMotion. One clock drives the 9 s loop; a tap
/// highlights the sentence under the finger (or lifts a highlight off) and a drag marks words. The loop carries
/// on 2 s after the last touch. Under Reduce Motion the finished page is shown, with no loop.
struct ScribbleEmphasisNotes: View {
    var autoPlay: Bool = true

    static let loop: Double = ScribbleEmphasisNotesMotion.loop

    private typealias M = ScribbleEmphasisNotesMotion
    private typealias S = ScribbleEmphasisNotesStrokes

    // Palette: warm paper, one ink, two pens.
    private static let desk = Color(hex: 0xECE7DD)
    private static let paper = Color(hex: 0xFBF8F1)
    private static let ink = Color(hex: 0x24211C)
    private static let inkSoft = Color(hex: 0x8A8273)
    private static let hairlineColor = Color.rgba(36, 33, 28, 0.09)
    private static let penRed = Color(hex: 0xD5432E)
    private static let penBlue = Color(hex: 0x2D4FCB)
    private static let hairline = 1 / UIScreen.main.scale

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = ScribbleEmphasisNotesModel()
    @State private var tint = 0
    /// Under Reduce Motion the timeline is paused; a touch bumps this to redraw.
    @State private var redraw = 0

    private var playing: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { tl in
                    let _ = redraw
                    screen(tl.date, model.layout(full.size, top: ins.top, bottom: ins.bottom))
                }
            }
            .ignoresSafeArea()
        }
        .background(Self.desk.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.reset(reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, now in model.reset(reduced: now) }
    }

    // MARK: Screen

    private func screen(_ date: Date, _ l: ScribbleEmphasisNotesLayout) -> some View {
        model.step(date, playing: playing, reduced: reduceMotion)
        let t = model.clock, cap = model.cap
        let amounts = (0..<M.markCount).map { M.markAt(t, cap, $0) }
        let slots = model.slots

        return ZStack(alignment: .topLeading) {
            Canvas(rendersAsynchronously: false) { ctx, _ in
                let card = Path(roundedRect: CGRect(x: l.left, y: l.cardTop, width: l.cardWidth, height: l.cardHeight), cornerRadius: 22, style: .circular)
                var far = ctx
                far.addFilter(.shadow(color: .rgba(64, 50, 28, 0.10), radius: 16, x: 0, y: 12))
                far.fill(card, with: .color(Self.paper))
                var near = ctx
                near.addFilter(.shadow(color: .rgba(64, 50, 28, 0.12), radius: 1.5, x: 0, y: 1))
                near.fill(card, with: .color(Self.paper))
                ctx.withCGContext { l.page.draw(in: $0) }

                // Highlighter first (multiplied over the printed text), pens on top.
                var marker = ctx
                marker.blendMode = .multiply
                for slot in slots where slot.used {
                    S.drawHighlight(marker, Color(hex: M.tints[slot.tint].hex), l.page.wordBands(slot.first, slot.last),
                                    fontSize: l.fontSize, seed: 100 + Double(slot.stamp) * 17, amount: M.easeInOutCubic(slot.amount))
                }
                S.drawHighlight(marker, Color(hex: M.tints[0].hex), l.highlight, fontSize: l.fontSize, seed: 7, amount: amounts[M.highlight])
                S.drawInk(ctx, Self.penRed, l.circle, amounts[M.circle])
                S.drawInk(ctx, Self.penBlue, l.underline, amounts[M.underline])
                S.drawInk(ctx, Self.penRed, l.bracket, amounts[M.bracket])
                S.drawInk(ctx, Self.penRed, l.note, amounts[M.note])
                S.drawInk(ctx, Self.penRed, l.arrow, amounts[M.arrow])
            }
            .frame(width: l.width, height: l.height)
            .allowsHitTesting(false)

            header
                .frame(width: l.cardWidth, alignment: .topLeading)
                .offset(x: l.left, y: l.headTop)
                .allowsHitTesting(false)

            HStack {
                Text(M.label).font(.system(size: 13, weight: .bold)).tracking(-0.1).foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Text(M.pageNumber).font(.system(size: 13, weight: .medium)).monospacedDigit().foregroundStyle(Self.inkSoft)
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .frame(width: l.cardWidth, height: 46)
            .offset(x: l.left, y: l.cardTop)
            .allowsHitTesting(false)

            Color.clear
                .frame(width: l.cardWidth, height: l.cardHeight)
                .contentShape(Rectangle())
                .gesture(mark(l))
                .accessibilityElement()
                .accessibilityLabel(M.text)
                .accessibilityHint(M.hint)
                .offset(x: l.left, y: l.cardTop)

            tray
                .frame(width: l.cardWidth, height: 58)
                .offset(x: l.left, y: l.trayTop)

            if l.rows > 0 {
                VStack(alignment: .leading, spacing: 0) {
                    Text(M.marked)
                        .font(.system(size: 13, weight: .bold)).tracking(-0.1).foregroundStyle(Self.inkSoft)
                        .frame(height: 32, alignment: .top)
                    ForEach(0..<l.rows, id: \.self) { i in
                        markedRow(M.markedRows[i], first: i == 0, on: amounts[M.markedRows[i].mark])
                    }
                }
                .padding(.horizontal, 4)
                .frame(width: l.cardWidth, alignment: .topLeading)
                .offset(x: l.left, y: l.listTop)
                .allowsHitTesting(false)
            }

            ghost(t, cap, l, amount: amounts[M.highlight])
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .coordinateSpace(name: "ScribbleEmphasisNotes")
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(M.brand).font(.system(size: 15, weight: .bold)).tracking(-0.2).foregroundStyle(Self.penRed)
                Spacer(minLength: 0)
                Text(M.course).font(.system(size: 13, weight: .medium)).foregroundStyle(Self.inkSoft)
            }
            Text(M.title)
                // RN's adjustsFontSizeToFit drops most of its -0.7 letter spacing; this matches the width it draws.
                .font(.system(size: 31, weight: .heavy)).tracking(-0.18).foregroundStyle(Self.ink)
                .lineLimit(1).minimumScaleFactor(0.6)
                .frame(height: 36)
                .padding(.top, 6)
                .accessibilityAddTraits(.isHeader)
            Text(M.meta)
                .font(.system(size: 14, weight: .medium)).monospacedDigit().foregroundStyle(Self.inkSoft)
                .padding(.top, 3)
        }
        .padding(.horizontal, 4)
    }

    private var tray: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 1) {
                Text(M.tray).font(.system(size: 15, weight: .bold)).tracking(-0.2).foregroundStyle(Self.ink)
                Text(M.hint).font(.system(size: 12.5, weight: .medium)).foregroundStyle(Self.inkSoft).lineLimit(1)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            .padding(.trailing, 8)
            HStack(spacing: 4) {
                ForEach(M.tints.indices, id: \.self) { i in
                    Button {
                        model.touched()
                        model.tint = i
                        tint = i
                        UISelectionFeedbackGenerator().selectionChanged()
                    } label: {
                        Circle()
                            .fill(Color(hex: M.tints[i].hex))
                            .overlay(Circle().strokeBorder(Color.rgba(36, 33, 28, 0.18), lineWidth: Self.hairline))
                            .frame(width: 26, height: 26)
                            .frame(width: 38, height: 38)
                            .overlay(Circle().strokeBorder(i == tint ? Self.ink : .clear, lineWidth: 2))
                            .contentShape(Circle().inset(by: -6))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(M.tints[i].name) highlighter")
                    .accessibilityAddTraits(i == tint ? .isSelected : [])
                }
            }
        }
        .padding(.leading, 20)
        .padding(.trailing, 12)
        .frame(maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Self.paper)
                .shadow(color: .rgba(64, 50, 28, 0.10), radius: 1, x: 0, y: 1)
                .shadow(color: .rgba(64, 50, 28, 0.07), radius: 9, x: 0, y: 8)
        )
    }

    /// One line of the summary: it is faint until the loop has drawn its mark.
    private func markedRow(_ row: (term: String, how: String, mark: Int), first: Bool, on: Double) -> some View {
        HStack(spacing: 0) {
            Circle()
                .strokeBorder(Color.rgba(36, 33, 28, 0.28), lineWidth: 1.5)
                .frame(width: 18, height: 18)
                .overlay(Circle().fill(Self.penRed).frame(width: 10, height: 10).scaleEffect(M.mix(0.5, 1, on)).opacity(on))
                .padding(.trailing, 12)
            Text(row.term)
                .font(.system(size: 16, weight: .semibold)).tracking(-0.2).foregroundStyle(Self.ink).lineLimit(1)
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            Text(row.how).font(.system(size: 14, weight: .medium)).foregroundStyle(Self.inkSoft)
        }
        .frame(height: 44)
        .overlay(alignment: .top) {
            if !first { Self.hairlineColor.frame(height: Self.hairline) }
        }
        .opacity(M.mix(0.42, 1, on))
    }

    /// The ghost finger rides the highlight's leading edge.
    private func ghost(_ t: Double, _ cap: Double, _ l: ScribbleEmphasisNotesLayout, amount: Double) -> some View {
        let head = S.bandHead(l.highlight, amount)
        let shown = model.auto && cap >= M.loop && playing ? M.ghostAt(t) : 0
        let pressed = M.seg(t, M.ghostShown, M.draw[M.highlight].0) * (1 - M.seg(t, M.ghostLift, M.ghostGone))
        return Circle()
            .fill(Color.rgba(36, 33, 28, 0.18))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.9), lineWidth: 1.5))
            .frame(width: M.ghostSize, height: M.ghostSize)
            .scaleEffect(1 - 0.14 * pressed)
            .opacity(shown)
            .offset(x: head.x - M.ghostSize / 2, y: head.base - l.fontSize * 0.3 - M.ghostSize / 2)
            .allowsHitTesting(false)
    }

    // MARK: Touch

    private func mark(_ l: ScribbleEmphasisNotesLayout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("ScribbleEmphasisNotes"))
            .onChanged { value in
                let haptic = model.drag(value.startLocation, value.location, l.page)
                if haptic == .impact { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                if haptic == .tick { UISelectionFeedbackGenerator().selectionChanged() }
                settle()
            }
            .onEnded { _ in
                if model.release(l.page) { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                settle()
            }
    }

    /// Reduce Motion: highlights appear and disappear at once, and the paused timeline is asked to redraw.
    private func settle() {
        guard reduceMotion else { return }
        model.stepMarks(0, snap: true)
        redraw += 1
    }
}

/// Where things sit. The paragraph is laid out and measured once per size.
struct ScribbleEmphasisNotesLayout {
    typealias Node = ScribbleEmphasisNotesStrokes.Node
    private typealias M = ScribbleEmphasisNotesMotion
    private typealias S = ScribbleEmphasisNotesStrokes

    let width: Double
    let height: Double
    let left: Double
    let cardWidth: Double
    let headTop: Double
    let cardTop: Double
    let cardHeight: Double
    let trayTop: Double
    let listTop: Double
    let rows: Int
    let fontSize: Double
    let page: ScribbleEmphasisNotesPage
    let circle: [Node]
    let highlight: [ScribbleEmphasisNotesPage.Band]
    let underline: [Node]
    let bracket: [Node]
    let note: [Node]
    let arrow: [Node]

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        let column = min(width, 560)
        left = (width - column) / 2 + 16
        cardWidth = column - 32
        headTop = top + 8
        cardTop = headTop + 98
        let large = column >= 390
        fontSize = large ? 18 : 17
        let lineHeight: Double = large ? 29 : 27
        let textX = left + 20
        let textY = cardTop + 46
        let textWidth = cardWidth - 20 - 34 - 6
        let page = ScribbleEmphasisNotesPage(text: M.text, x: textX, y: textY, width: textWidth, fontSize: fontSize, lineHeight: lineHeight,
                                             color: UIColor(red: 0x24 / 255.0, green: 0x21 / 255.0, blue: 0x1C / 255.0, alpha: 1).cgColor)
        self.page = page
        cardHeight = 46 + page.height + 60
        trayTop = cardTop + cardHeight + 14
        listTop = trayTop + 58 + 14
        let fit = Int(floor((height - max(bottom, 12) - listTop - 32 - 8) / 44))
        rows = fit >= 2 ? min(fit, M.markedRows.count) : 0

        let right = textX + textWidth
        let sentence = min(M.bracketSentence, page.sentenceFirst.count - 1)
        let firstLine = page.wordLine[page.sentenceFirst[sentence]]
        let lastLine = page.wordLine[page.sentenceLast[sentence]]
        let brace = S.bracketMark(x: right + M.braceGap, top: page.lineBase[firstLine] - fontSize * 0.86, bottom: page.lineBase[lastLine] + fontSize * 0.26, seed: 29)
        let noteX = right - 66
        let noteBase = textY + page.height + 40
        let word = S.noteMark("exam!", x: noteX, baseline: noteBase, seed: 37)
        let noteEnd = CGPoint(x: noteX + word.width * cos(M.noteTilt), y: noteBase + word.width * sin(M.noteTilt))
        let from = CGPoint(x: noteEnd.x + 3, y: noteEnd.y - M.noteXHeight * 2.1)
        let to = CGPoint(x: brace.nose.x + 4.5, y: brace.nose.y + 6)
        let circleBand = page.bands(of: M.termCircle, in: M.text).first
            ?? ScribbleEmphasisNotesPage.Band(x0: textX, x1: textX + 80, base: page.lineBase.first ?? textY + fontSize)
        circle = S.circleMark(circleBand, fontSize: fontSize, seed: 11)
        highlight = page.bands(of: M.termHighlight, in: M.text)
        underline = S.underlineMark(page.bands(of: M.termUnderline, in: M.text), seed: 23)
        bracket = brace.data
        note = word.data
        arrow = S.arrowMark([from, CGPoint(x: to.x + 6, y: M.mix(from.y, to.y, 0.5)), CGPoint(x: to.x + 4, y: M.mix(from.y, to.y, 0.82)), to], seed: 53)
    }
}

/// The clock, what a touch does to it, and the finger's own highlights. A plain reference type: the timeline
/// steps it once per frame, so nothing here is observed.
final class ScribbleEmphasisNotesModel {
    private typealias M = ScribbleEmphasisNotesMotion

    /// A finger-made highlight: first and last word, amount on the page, where it is heading, ink, age.
    struct Slot {
        var first = 0
        var last = 0
        var amount = 0.0
        var target = 0.0
        var tint = 0
        var used = false
        var stamp = 0
    }

    enum Haptic { case none, impact, tick }

    private(set) var clock = 0.0
    /// Freezes the drawing where a finger took over (`loop` = no cap).
    private(set) var cap = M.loop
    private(set) var auto = true
    private(set) var slots = [Slot](repeating: Slot(), count: M.userMarks)
    var tint = 0

    private var idle = 0.0
    private var held = false
    private var pending = false
    private var stamp = 1
    private var startWord = -1
    private var dragSlot = -1
    private var dragWord = -1
    private var touching = false
    private var last: Date?
    private var cached: (key: [Double], layout: ScribbleEmphasisNotesLayout)?

    func layout(_ size: CGSize, top: Double, bottom: Double) -> ScribbleEmphasisNotesLayout {
        let key = [size.width, size.height, top, bottom]
        if let cached, cached.key == key { return cached.layout }
        let made = ScribbleEmphasisNotesLayout(width: size.width, height: size.height, top: top, bottom: bottom)
        cached = (key, made)
        return made
    }

    /// Reduced motion: the finished page, loop stopped.
    func reset(reduced: Bool) {
        clock = reduced ? M.hold : 0
        cap = M.loop
        auto = true
        last = nil
    }

    func touched() { pending = true }

    func step(_ date: Date, playing: Bool, reduced: Bool) {
        let dt = min(last.map { date.timeIntervalSince($0) } ?? 0, 0.048)
        last = date
        guard dt > 0 else { return }
        stepMarks(dt, snap: reduced)
        if reduced { return }

        // A touch takes the page: the pen finishes the mark it is on and stops.
        if pending {
            pending = false
            idle = 0
            if auto {
                auto = false
                cap = min(cap, M.parkAt(clock))
            }
        }
        var t = clock
        if !auto {
            let limit = cap
            if t >= M.liftStart {
                // Caught lifting off: let it finish, then wait on the clean page.
                t += dt
                if t >= M.loop {
                    t = 0
                    cap = 0
                }
                clock = t
            } else if t < limit {
                clock = min(limit, t + dt * M.catchUp)
            }
            if !playing { return }
            if !held { idle += dt }
            if idle >= M.resumeAfter && t < M.liftStart {
                for i in slots.indices { slots[i].target = 0 }
                if limit > M.draw[0].0 && t > 0 {
                    // Something is on the page: lift it off. The jump cannot be seen, since the drawing reads the cap.
                    clock = M.liftStart
                } else {
                    cap = M.loop
                }
                auto = true
            }
            return
        }
        if !playing { return }
        t += dt
        if t >= M.loop {
            t -= M.loop
            cap = M.loop
        }
        clock = t
    }

    /// Moves every highlight toward its target; one that has lifted off frees its slot.
    func stepMarks(_ dt: Double, snap: Bool) {
        for i in slots.indices where slots[i].used {
            let target = slots[i].target
            var amount = slots[i].amount
            if snap { amount = target }
            else if amount < target { amount = min(target, amount + dt / M.userDraw) }
            else if amount > target { amount = max(target, amount - dt / M.userLift) }
            slots[i].amount = amount
            if target == 0 && amount <= 0 { slots[i].used = false }
        }
    }

    /// A free slot, or the oldest one.
    private func takeSlot() -> Int {
        var oldest = 0
        for i in slots.indices {
            if !slots[i].used { return i }
            if slots[i].stamp < slots[oldest].stamp { oldest = i }
        }
        return oldest
    }

    private func put(_ slot: Int, _ first: Int, _ last: Int, amount: Double) {
        stamp += 1
        slots[slot] = Slot(first: first, last: last, amount: amount, target: 1, tint: tint, used: true, stamp: stamp)
    }

    /// The finger is down or moving. Points are in screen coordinates.
    func drag(_ start: CGPoint, _ now: CGPoint, _ page: ScribbleEmphasisNotesPage) -> Haptic {
        if !touching {
            touching = true
            pending = true
            held = true
            dragSlot = -1
            let inside = start.y > page.y - 10 && start.y < page.y + page.height + 10
            startWord = inside ? page.wordAt(start.x, start.y) : -1
            dragWord = startWord
        }
        let from = startWord
        guard from >= 0 else { return .none }
        var haptic = Haptic.none
        if dragSlot < 0 {
            if hypot(now.x - start.x, now.y - start.y) < M.tapSlop { return .none }
            // The touch has become a drag: a highlight starts under the first word and follows the finger.
            let slot = takeSlot()
            put(slot, from, from, amount: 1)
            dragSlot = slot
            haptic = .impact
        }
        let word = page.wordAt(now.x, now.y)
        if word < 0 || word == dragWord { return haptic }
        dragWord = word
        slots[dragSlot].first = min(from, word)
        slots[dragSlot].last = max(from, word)
        return haptic == .none ? .tick : haptic
    }

    /// The finger lifted. Answers true when it was a tap that marked or cleared a sentence.
    func release(_ page: ScribbleEmphasisNotesPage) -> Bool {
        touching = false
        held = false
        idle = 0
        let word = startWord
        startWord = -1
        if word < 0 || dragSlot >= 0 { return false }
        // A tap: the sentence's highlight is swiped on, or the highlight under the finger lifts off.
        var over = -1
        var newest = -1
        for i in slots.indices where slots[i].used && slots[i].target == 1 && word >= slots[i].first && word <= slots[i].last && slots[i].stamp > newest {
            newest = slots[i].stamp
            over = i
        }
        if over >= 0 {
            slots[over].target = 0
        } else {
            let sentence = page.wordSentence[word]
            put(takeSlot(), page.sentenceFirst[sentence], page.sentenceLast[sentence], amount: 0)
        }
        return true
    }
}
