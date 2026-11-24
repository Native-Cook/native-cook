import SwiftUI
import UIKit

/*
 The hand-over between the loop and a person. While the loop plays everything comes from the
 script step and `MentionAutocompleteMotion.pose(t)`; a touch copies the frame at that moment into
 `MentionAutocompleteValue`s and the person types from there. Two seconds after the last touch,
 with the keyboard down, it eases back and the loop starts again.
*/
final class MentionAutocompleteModel: ObservableObject {
    typealias M = MentionAutocompleteMotion
    typealias D = MentionAutocompleteData
    typealias Doc = MentionAutocompleteDoc
    typealias V = MentionAutocompleteValue

    struct Message: Identifiable, Equatable {
        let id: String
        let author: MentionAutocompletePerson
        let time: String
        let doc: Doc
    }

    static let history: [Message] = [
        Message(id: "h1", author: D.people[0], time: "9:41", doc: Doc(text: "Hero copy v3 is in the doc. The second line still feels long to me.", chips: [], selected: -1)),
        Message(id: "h2", author: D.people[1], time: "9:44", doc: Doc(text: "Agreed. It wraps to three lines on the small phones.", chips: [], selected: -1)),
        Message(id: "h3", author: D.people[3], time: "9:46", doc: Doc(text: "Who signs off on copy before Thursday?", chips: [], selected: -1)),
    ]

    // Changes on keystrokes, picks and Backspace on chips only.
    @Published private(set) var user = false
    @Published private(set) var uDoc = Doc.empty
    @Published private(set) var uCaret = 0
    @Published private(set) var uMode: M.ChipMode = .none
    @Published private(set) var uActive = 0
    @Published private(set) var uQuery: (at: Int, query: String)?
    @Published private(set) var uFrom = M.morphFrom
    @Published private(set) var uBroke = (prefix: "", label: "")
    @Published private(set) var messages = history
    @Published private(set) var isPaused = false

    /// 0 = the loop, 1 = a person, 2 = easing back.
    private(set) var mode = 0
    private var loopStart = Date().timeIntervalSinceReferenceDate
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var editing = false
    private(set) var auto = true
    private(set) var still = false
    private(set) var reduced = false
    private var configured = false
    private var watcher: Task<Void, Never>?
    private var observers: [NSObjectProtocol] = []
    private var bottomInset: CGFloat = 34
    private var sent = 0

    private var focus = V(0), placeholder = V(1), send = V(0), pop = V(0), count = V(0)
    private var morph = V(1), select = V(0), breakUp = V(0)
    private var rowY = D.people.map { _ in V(0) }
    private var rowA = D.people.map { _ in V(0) }
    private var lift = V(0)
    /// The thread's height as drawn (its offset), as laid out, and with only the history in it.
    private var thread = V(0)
    private var threadFull = 0.0
    private var threadRest = 0.0
    private var pending: [DispatchWorkItem] = []

    static var clock: Double { Date().timeIntervalSinceReferenceDate }

    func configure(autoPlay: Bool, reduced: Bool, bottom: CGFloat) {
        bottomInset = bottom
        guard !configured else { return }
        configured = true
        self.reduced = reduced
        auto = autoPlay && !reduced
        still = autoPlay && reduced
        loopStart = Self.clock
        isPaused = !auto
        watcher = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 100_000_000)
                self?.tick()
            }
        }
        let nc = NotificationCenter.default
        observers.append(nc.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { [weak self] n in
            guard let self else { return }
            let h = (n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect)?.height ?? 0
            let d = (n.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            self.lift.timing(max(0, h - max(self.bottomInset, 10)), self.reduced ? 0 : d, now: Self.clock, curve: M.easeOutCubic)
            self.isPaused = false
        })
        observers.append(nc.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { [weak self] n in
            guard let self else { return }
            let d = (n.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            self.lift.timing(0, self.reduced ? 0 : d, now: Self.clock, curve: M.easeOutCubic)
            self.isPaused = false
        })
    }

    func stop() {
        watcher?.cancel()
        watcher = nil
        observers.forEach(NotificationCenter.default.removeObserver)
        observers = []
        configured = false
    }

    // MARK: Time and what is shown

    func loopTime(_ now: Double) -> Double {
        if still { return M.tStill }
        if !auto { return 0 }
        let e = now - loopStart
        return e <= 0 ? 0 : e.truncatingRemainder(dividingBy: M.loop)
    }

    func pose(_ now: Double) -> M.Pose {
        if mode == 0 { return M.pose(loopTime(now)) }
        var p = M.Pose()
        p.focus = focus.value(now)
        p.caret = 0
        p.placeholder = placeholder.value(now)
        p.send = send.value(now)
        p.pop = pop.value(now)
        p.count = count.value(now)
        p.rowY = rowY.map { $0.value(now) }
        p.rowA = rowA.map { $0.value(now) }
        p.morph = morph.value(now)
        p.select = select.value(now)
        p.breakUp = breakUp.value(now)
        return p
    }

    func liftValue(_ now: Double) -> Double { lift.value(now) }

    func threadValue(_ now: Double) -> Double { thread.value(now) }

    /// The thread hangs from its top just under the composer's edge and is lifted by its own height, so a
    /// sent message is laid out out of sight and nothing in the layout moves: only this offset does.
    func threadLaidOut(_ h: Double) {
        let first = threadFull == 0
        threadFull = h
        if messages == Self.history { threadRest = h }
        if first || reduced {
            thread.set(h)
        } else if mode != 2 {
            thread.timing(h, M.sendLen, now: Self.clock, curve: { Ease.out.value(at: $0) })
        }
        if isPaused { isPaused = false }
    }

    func dot(_ now: Double) -> M.Dot { mode == 0 && auto ? M.dot(loopTime(now)) : M.Dot() }

    struct Content {
        var doc: Doc
        var mode: M.ChipMode
        var active: Int
        var query: (at: Int, query: String)?
        var from: String
        var broke: (prefix: String, label: String)
        var step: Int
    }

    func content(_ now: Double) -> Content {
        if user {
            return Content(doc: uDoc, mode: uMode, active: uActive, query: uQuery, from: uFrom, broke: uBroke, step: -1)
        }
        let k = mode == 0 ? M.stepAt(loopTime(now)) : 0
        let s = M.script[k]
        let q = s.query.map { (at: s.doc.text.lastIndex(of: "@").map { s.doc.text.distance(from: s.doc.text.startIndex, to: $0) } ?? 0, query: $0) }
        return Content(doc: s.doc, mode: s.chip, active: 0, query: q, from: M.morphFrom,
                       broke: (prefix: s.doc.slice(0, M.chipStart), label: M.label), step: k)
    }

    // MARK: Hand-over

    func takeOver() {
        let now = Self.clock
        if mode == 0 {
            let t = loopTime(now)
            let st = M.script[M.stepAt(t)]
            uMode = st.chip == .morph ? .chip : (st.chip == .breaking ? .none : st.chip)
            // Taken over with the caret right after a chip (the film's Backspace had taken its space): give the
            // space back, so what the person types next never runs into the name.
            var doc = st.doc
            if uMode == .chip, let last = doc.chips.last, last.start + last.len == doc.text.count { doc.text += " " }
            uDoc = doc
            uCaret = doc.text.count
            uActive = 0
            uQuery = st.query.map { (at: st.doc.count - $0.count - 1, query: $0) }
            let p = M.pose(t)
            focus.set(p.focus)
            placeholder.set(p.placeholder)
            send.set(p.send)
            pop.set(p.pop)
            count.set(p.count)
            morph.set(1)
            select.set(p.select)
            breakUp.set(0)
            for i in 0 ..< rowY.count {
                rowY[i].set(p.rowY[i])
                rowA[i].set(p.rowA[i])
            }
            mode = 1
            user = true
            updateRows()
        } else if mode == 2 {
            mode = 1
            thread.timing(threadFull, reduced ? 0 : M.sendLen, now: now, curve: { Ease.out.value(at: $0) })
        }
        lastTouch = now
        isPaused = false
    }

    private func easeToRest(_ d: Double) {
        let now = Self.clock
        let c = { (x: Double) in Ease.out.value(at: x) }
        focus.timing(0, d, now: now, curve: c)
        pop.timing(0, d, now: now, curve: c)
        send.timing(0, d, now: now, curve: c)
        placeholder.timing(1, d, now: now, curve: c)
        select.timing(0, d, now: now, curve: c)
        // A message the person sent slides back down into the composer; it is dropped once out of sight.
        if threadRest > 0 { thread.timing(threadRest, d, now: now, curve: c) }
    }

    private func resetToAuto() {
        pending.forEach { $0.cancel() }
        pending = []
        user = false
        uDoc = .empty
        uCaret = 0
        uMode = .none
        uQuery = nil
        messages = Self.history
        mode = 0
        loopStart = Self.clock
        isPaused = !auto
    }

    private func tick() {
        let now = Self.clock
        if mode == 1 {
            if !editing && now - lastTouch > M.idle {
                mode = 2
                returnAt = now
                easeToRest(reduced ? 0 : M.returnLen)
                isPaused = false
            } else if !isPaused && !moving(now) {
                isPaused = true
            }
        } else if mode == 2 && now - returnAt > M.returnLen {
            resetToAuto()
        }
    }

    private func moving(_ now: Double) -> Bool {
        ([focus, placeholder, send, pop, count, morph, select, breakUp, lift, thread] + rowY + rowA).contains { $0.isMoving(now) }
    }

    private func later(_ seconds: Double, _ fn: @escaping () -> Void) {
        let item = DispatchWorkItem(block: fn)
        pending.append(item)
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduced ? 0 : seconds), execute: item)
    }

    // MARK: The person's typing

    private func updateRows() {
        let now = Self.clock
        let list = uQuery.map { D.rank($0.query) } ?? []
        let open = uQuery != nil && !list.isEmpty
        for i in 0 ..< rowY.count {
            let slot = list.firstIndex(of: i) ?? -1
            let visible = open && slot >= 0
            if slot >= 0 { rowY[i].spring(Double(slot), reduced ? 0 : 0.38, damping: 0.72, now: now) }
            rowA[i].timing(visible ? 1 : 0, reduced ? 0 : (visible ? 0.22 : 0.15), now: now)
        }
    }

    private func popUI(show: Bool, n: Int, hasText: Bool) {
        let now = Self.clock
        let k = reduced ? 0.0 : 1.0
        pop.spring(show ? 1 : 0, (show ? 0.38 : 0.22) * k, damping: show ? 0.62 : 1, now: now)
        if show { count.spring(Double(n), 0.38 * k, damping: 0.8, now: now) }
        send.timing(hasText ? 1 : 0, 0.18 * k, now: now)
        placeholder.timing(hasText ? 0 : 1, 0.12 * k, now: now)
        select.timing(0, 0.12 * k, now: now)
        lastTouch = now
    }

    private enum ChipKind { case morph, select, breaking }

    private func chipUI(_ kind: ChipKind) {
        let now = Self.clock
        let k = reduced ? 0.0 : 1.0
        switch kind {
        case .morph:
            morph.set(0)
            morph.timing(1, M.morphLen * k, now: now)
            pop.timing(0, 0.22 * k, now: now, curve: { Ease.out.value(at: $0) })
            select.set(0)
        case .select:
            select.timing(1, M.selectLen * k, now: now, curve: { Ease.out.value(at: $0) })
        case .breaking:
            breakUp.set(0)
            breakUp.timing(1, M.breakLen * k, now: now)
            select.timing(0, M.breakLen * k, now: now)
        }
        lastTouch = now
    }

    func onText(_ next: String, caret: Int) {
        takeOver()
        let base = uDoc
        switch base.apply(next) {
        case let .select(chip):
            uDoc.selected = chip
            uMode = .selected
            uActive = chip
            UISelectionFeedbackGenerator().selectionChanged()
            chipUI(.select)
        case let .breakChip(chip, doc):
            let c = base.chips[chip]
            uBroke = (prefix: base.slice(0, c.start), label: base.slice(c.start, c.start + c.len))
            uDoc = doc
            uCaret = c.start + c.len
            uMode = .breaking
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            chipUI(.breaking)
            later(M.breakLen + 0.04) { [weak self] in
                if self?.uMode == .breaking { self?.uMode = .chip }
            }
        case let .text(doc, caret):
            uDoc = doc
            uCaret = caret
            if uMode == .selected { uMode = .chip }
            let q = doc.activeQuery(caret: caret)
            uQuery = q
            let n = q.map { D.rank($0.query).count } ?? 0
            popUI(show: n > 0, n: n, hasText: !doc.text.isEmpty)
            updateRows()
        }
    }

    func pick(_ person: Int) {
        takeOver()
        guard let q = uQuery else { return }
        let r = uDoc.insertMention(at: q.at, caret: q.at + 1 + q.query.count, person: person)
        uFrom = "@\(q.query)"
        uDoc = r.doc
        uCaret = r.caret
        uActive = r.chip
        uMode = .morph
        uQuery = nil
        UISelectionFeedbackGenerator().selectionChanged()
        chipUI(.morph)
        send.timing(1, reduced ? 0 : 0.18, now: Self.clock)
        updateRows()
        later(M.morphLen + 0.06) { [weak self] in
            if self?.uMode == .morph { self?.uMode = .chip }
        }
    }

    func sendMessage() {
        takeOver()
        let base = uDoc
        if base.text.trimmingCharacters(in: .whitespaces).isEmpty { return }
        sent += 1
        messages.append(Message(id: "m\(sent)", author: D.you, time: "now", doc: base))
        uDoc = .empty
        uCaret = 0
        uMode = .none
        uQuery = nil
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        popUI(show: false, n: 0, hasText: false)
        updateRows()
    }

    func onReturn() {
        if let q = uQuery {
            if let first = D.rank(q.query).first { pick(first) }
            return
        }
        sendMessage()
    }

    func focusChanged(_ on: Bool) {
        if on { takeOver() }
        editing = on
        focus.timing(on ? 1 : 0, 0.2, now: Self.clock)
        lastTouch = Self.clock
        isPaused = false
    }

    func attachPicked() {
        takeOver()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
