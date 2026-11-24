import SwiftUI
import Observation

/// Where everything sits on the screen, in points.
struct QuickAddMorphGeometry: Equatable {
    private typealias M = QuickAddMorphMotion

    var width: Double
    var height: Double
    var top: Double
    var bottomInset: Double

    var listTop: Double { top + M.header }
    /// Distance from the button's bottom edge to the screen's.
    var bottom: Double { max(bottomInset, 16) + M.fabBottom }
    var fab: CGRect { CGRect(x: width - M.fabSide - M.fab, y: height - bottom - M.fab, width: M.fab, height: M.fab) }
    var cardWidth: Double { min(width - M.side * 2, M.cardMax) }
    var card: CGRect {
        CGRect(x: width - M.side - cardWidth, y: height - bottom - M.cardHeight, width: cardWidth, height: M.cardHeight)
    }
    var pill: CGRect {
        let c = card
        return CGRect(x: c.minX + (c.width - M.pillWidth) / 2, y: c.maxY - M.pillHeight - 14,
                      width: M.pillWidth, height: M.pillHeight)
    }
    var rowX: Double { M.side }
    var rowWidth: Double { width - M.side * 2 }
    var keyboardInset: Double { bottom - 8 }

    struct Point { var t: Double; var x: Double; var y: Double }

    /// The scripted finger's stops.
    var ghostPath: [Point] {
        let f = fab, c = card
        let fabX = f.midX, fabY = f.midY
        let chipY = c.minY + M.chipsTop + M.chipHeight / 2
        let priorityX = c.minX + M.pad + M.chipWidths[0] + M.chipGap + M.chipWidths[1] / 2
        let listX = c.minX + M.pad + M.chipWidths[0] + M.chipWidths[1] + M.chipGap * 2 + M.chipWidths[2] / 2
        let addY = c.minY + M.addTop + M.addHeight / 2
        let addX = c.midX
        let fieldY = c.minY + M.fieldTop + M.fieldHeight / 2
        let checkX = M.side + M.checkLeft + M.checkSize / 2
        let checkY = listTop + Double(M.insert) * M.row + M.rowHeight / 2
        return [
            Point(t: M.ghostIn, x: fabX + 26, y: fabY + 34),
            Point(t: M.open - 0.08, x: fabX, y: fabY),
            Point(t: M.open + 0.12, x: fabX, y: fabY),
            Point(t: M.open + 0.52, x: c.minX + 120, y: fieldY),
            Point(t: M.priority - 0.38, x: priorityX + 16, y: chipY + 26),
            Point(t: M.priority - 0.06, x: priorityX, y: chipY),
            Point(t: M.priority + 0.1, x: priorityX, y: chipY),
            Point(t: M.list - 0.06, x: listX, y: chipY),
            Point(t: M.list + 0.1, x: listX, y: chipY),
            Point(t: M.add - 0.06, x: addX, y: addY),
            Point(t: M.add + 0.34, x: addX, y: addY),
            Point(t: M.check - 0.45, x: checkX + 30, y: checkY + 34),
            Point(t: M.check - 0.06, x: checkX, y: checkY),
            Point(t: M.check + 0.4, x: checkX, y: checkY),
        ]
    }
}

/// Every animated value of the screen at one instant.
struct QuickAddMorphFrame {
    var active = 0.0
    var openW = 0.0
    var openH = 0.0
    var content = 0.0
    var collapse = 0.0
    var fly = 0.0
    var fabIn = 1.0
    var press = 0.0
    var addPress = 0.0
    var drag = 0.0
    var lift = 0.0
    var landed = 0.0
    var landId = -1
    var typed = 0
    var scripted = true
    var today = 1.0
    var priority = 0.0
    var listRoll = 0.0
    var part = 0.0
    var scriptDone = 0.0
    var rowY: [Double] = []
    var done: [String: Double] = [:]
    var auto = false
    var ghost = CGPoint.zero
    var ghostOpacity = 0.0
    var ghostPress = 0.0
}

/// Who drives the screen: the loop's clock, or a finger. The draft (what the composer shows as real
/// controls) is observed; every per-frame value is not, so a frame never invalidates the view.
@Observable
final class QuickAddMorphModel {
    private typealias M = QuickAddMorphMotion
    private typealias Anim = QuickAddMorphAnim
    private enum Mode { case auto, manual, returning }

    // MARK: Draft and list (observed)

    var title = ""
    var isOpen = false
    var today = true
    var priority = false
    var listIndex = 0
    var labels: (String, String)
    var tasks: [QuickAddMorphTask]
    var checked: Set<String>
    var doneCount: Int
    var pillTitle = QuickAddMorphTask.script.title
    var scrollRequest = 0

    let lists: [String]
    let initialCount: Int

    // MARK: Motion (not observed)

    @ObservationIgnored private var mode = Mode.auto
    @ObservationIgnored private var epoch = Anim.now
    @ObservationIgnored private var last = Anim.now
    @ObservationIgnored private var idle = 0.0
    @ObservationIgnored var held = 0
    @ObservationIgnored var focused = false
    @ObservationIgnored var scrollY = 0.0
    @ObservationIgnored var reducedMotion = false
    @ObservationIgnored var geometry = QuickAddMorphGeometry(width: 393, height: 852, top: 59, bottomInset: 34)

    @ObservationIgnored private var active = 0.0
    @ObservationIgnored private var openW = Anim(0)
    @ObservationIgnored private var openH = Anim(0)
    @ObservationIgnored private var content = Anim(0)
    @ObservationIgnored private var collapse = Anim(0)
    @ObservationIgnored private var fly = Anim(0)
    @ObservationIgnored private var fabIn = Anim(1)
    @ObservationIgnored private var press = Anim(0)
    @ObservationIgnored private var addPress = Anim(0)
    @ObservationIgnored private var drag = Anim(0)
    @ObservationIgnored private var lift = Anim(0)
    @ObservationIgnored private var landed = Anim(0)
    @ObservationIgnored private var landId = -1
    @ObservationIgnored private var typed = 0
    @ObservationIgnored private var scripted = true
    @ObservationIgnored private var todayV = Anim(1)
    @ObservationIgnored private var priorityV = Anim(0)
    @ObservationIgnored private var listRoll = Anim(0)
    @ObservationIgnored private var part = Anim(0)
    @ObservationIgnored private var scriptDone = Anim(0)
    @ObservationIgnored private var rowY: [Anim] = []
    @ObservationIgnored private var rowDone: [String: Anim] = [:]
    @ObservationIgnored private var slots: [Int] = []
    @ObservationIgnored private var pendingSlots: [Int]?
    @ObservationIgnored private var finishOnClose = false
    @ObservationIgnored private var finishOnFly = false
    @ObservationIgnored private var flyPast = false
    @ObservationIgnored private var nextId = 0

    init(tasks: [QuickAddMorphTask] = QuickAddMorphTask.all, lists: [String] = QuickAddMorphTask.lists) {
        self.tasks = tasks
        self.lists = lists
        initialCount = tasks.count
        labels = (lists[0], lists.count > 1 ? lists[1] : lists[0])
        checked = Set(tasks.filter(\.done).map(\.id))
        doneCount = tasks.filter(\.done).count
        slots = Array(tasks.indices)
        rowY = tasks.indices.map { Anim(Double($0) * M.row) }
        for task in tasks { rowDone[task.id] = Anim(task.done ? 1 : 0) }
    }

    /// Nothing moves and no finger is down: the timeline may sleep.
    func resting(at time: Double) -> Bool {
        guard mode == .manual, held == 0 else { return false }
        let anims = [openW, openH, content, collapse, fly, fabIn, press, addPress, drag, lift, landed,
                     todayV, priorityV, listRoll, part, scriptDone] + rowY + Array(rowDone.values)
        return anims.allSatisfy { $0.done(at: time) } && !finishOnClose && !finishOnFly
    }

    // MARK: Frame

    func frame(at time: Double, playing: Bool) -> QuickAddMorphFrame {
        let dt = min(max(time - last, 0), 0.048)
        last = time

        if mode == .auto, !playing {
            mode = .manual
            scripted = false
            restPose()
            DispatchQueue.main.async { self.resetDraft() }
        }

        switch mode {
        case .auto:
            let t = (time - epoch).truncatingRemainder(dividingBy: M.loop)
            return pose(max(0, t), at: time)
        case .manual:
            settle(at: time)
            guard playing else { break }
            if focused || held > 0 {
                idle = 0
                break
            }
            // A flight in progress finishes first.
            if fly.value(at: time) > 0 { break }
            idle += dt
            if idle >= M.resumeAfter {
                if active == 1 { closeForReturn(at: time) }
                mode = .returning
            }
        case .returning:
            settle(at: time)
            if active == 0, part.value(at: time) < 0.01 {
                epoch = time
                scripted = true
                mode = .auto
                DispatchQueue.main.async {
                    self.resetDraft()
                    self.pillTitle = QuickAddMorphTask.script.title
                }
                return pose(0, at: time)
            }
        }
        return live(at: time)
    }

    /// Finish callbacks and the rows' parting, as the RN reactions do.
    private func settle(at time: Double) {
        if finishOnClose, openH.done(at: time) { finishMorph() }
        if finishOnFly, fly.done(at: time) { finishMorph() }
        let past = fly.value(at: time) > 0.2
        if past, !flyPast, let next = pendingSlots {
            applySlots(next, at: time)
            pendingSlots = nil
        }
        flyPast = past
    }

    private func live(at time: Double) -> QuickAddMorphFrame {
        var f = QuickAddMorphFrame()
        f.active = active
        f.openW = openW.value(at: time)
        f.openH = openH.value(at: time)
        f.content = content.value(at: time)
        f.collapse = collapse.value(at: time)
        f.fly = fly.value(at: time)
        f.fabIn = fabIn.value(at: time)
        f.press = press.value(at: time)
        f.addPress = addPress.value(at: time)
        f.drag = drag.value(at: time)
        f.lift = lift.value(at: time)
        f.landed = landed.value(at: time)
        f.landId = landId
        f.typed = typed
        f.scripted = scripted
        f.today = todayV.value(at: time)
        f.priority = priorityV.value(at: time)
        f.listRoll = listRoll.value(at: time)
        f.part = part.value(at: time)
        f.scriptDone = scriptDone.value(at: time)
        f.rowY = rowY.map { $0.value(at: time) }
        f.done = rowDone.mapValues { $0.value(at: time) }
        f.auto = false
        return f
    }

    /// Every animated value at loop time t: a pure function, so the last frame equals the first.
    private func pose(_ t: Double, at time: Double) -> QuickAddMorphFrame {
        var f = live(at: time)
        f.auto = true
        let on = t >= M.open && t < M.fly + M.flySpan
        f.active = on ? 1 : 0
        if on {
            let since = t - M.open
            f.openW = M.springAt(since, M.widthSpring)
            f.openH = M.springAt(since - M.heightDelay, M.heightSpring)
            f.collapse = M.clamp01(M.springAt(t - M.add - 0.06, M.collapseSpring))
            f.content = M.easeOutCubic(M.seg(since, M.contentDelay, M.contentIn)) * (1 - M.seg(t, M.add + 0.02, M.contentOut))
            f.fly = M.easeInOutCubic(M.seg(t, M.fly, M.flySpan))
        } else {
            f.openW = 0; f.openH = 0; f.collapse = 0; f.content = 0; f.fly = 0
        }
        f.fabIn = t < M.open ? 1 : t < M.fly + M.fabDelay ? 0
            : min(1.04, M.springAt(t - M.fly - M.fabDelay, (M.fabSpan, 0.8)))
        f.press = t < M.open + 0.05 ? Self.ghostPress(t) : 0
        f.addPress = t > M.add - 0.2 && t < M.add + 0.2 ? Self.ghostPress(t) : 0
        f.drag = 0
        let fall = M.easeInOutCubic(M.seg(t, M.remove, M.removeSpan))
        f.part = M.springAt(t - M.fly - M.partDelay, M.partSpring) * (1 - fall)
        f.landed = M.easeOutCubic(M.seg(t, M.fly + M.landDelay, M.landSpan)) * (1 - fall)
        f.landId = -1
        f.scriptDone = t < M.remove + M.removeSpan ? M.easeInOutCubic(M.seg(t, M.check, M.checkSpan)) : 0
        let chars = QuickAddMorphTask.script.title.count
        f.typed = t < M.typeStart || t >= M.fly ? 0 : min(chars, Int(floor((t - M.typeStart) / M.typeStep)) + 1)
        f.scripted = true
        f.today = 1
        f.priority = t >= M.fly ? 0 : M.easeOutCubic(M.seg(t, M.priority, 0.22))
        f.listRoll = t >= M.fly ? 0 : M.easeInOutCubic(M.seg(t, M.list, 0.26))

        let path = geometry.ghostPath
        f.ghost = CGPoint(x: Self.ghostAt(path, t, \.x), y: Self.ghostAt(path, t, \.y))
        f.ghostOpacity = Self.ghostOpacity(t)
        f.ghostPress = Self.ghostPress(t)
        return f
    }

    private static let taps = [M.open, M.priority, M.list, M.add, M.check]

    static func ghostPress(_ t: Double) -> Double {
        var p = 0.0
        for tap in taps {
            let d = t - tap
            if d > -0.1, d < 0.12 { p = max(p, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12) }
        }
        return p
    }

    static func ghostOpacity(_ t: Double) -> Double {
        let a = M.seg(t, M.ghostIn, 0.2) * (1 - M.seg(t, M.open + 0.3, 0.22))
        let b = M.seg(t, M.priority - 0.38, 0.2) * (1 - M.seg(t, M.add + 0.14, 0.2))
        let c = M.seg(t, M.check - 0.45, 0.22) * (1 - M.seg(t, M.check + 0.2, 0.2))
        return max(a, b, c)
    }

    static func ghostAt(_ path: [QuickAddMorphGeometry.Point], _ t: Double,
                        _ axis: KeyPath<QuickAddMorphGeometry.Point, Double>) -> Double {
        if t <= path[0].t { return path[0][keyPath: axis] }
        for i in 1..<path.count where t <= path[i].t {
            let u = M.easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t))
            return M.mix(u, path[i - 1][keyPath: axis], path[i][keyPath: axis])
        }
        return path[path.count - 1][keyPath: axis]
    }

    // MARK: Takeover and return

    /// A touch anywhere: the loop hands over. An open composer stays open and becomes real.
    func touch(fingers: Int) {
        held = fingers
        bump()
    }

    func bump() {
        idle = 0
        guard mode != .manual else { return }
        if mode == .auto { takeOver(at: Anim.now) }
        mode = .manual
    }

    private func adoptPose(_ f: QuickAddMorphFrame) {
        active = f.active
        openW.set(f.openW); openH.set(f.openH); content.set(f.content); collapse.set(f.collapse)
        fly.set(f.fly); fabIn.set(f.fabIn); press.set(f.press); addPress.set(f.addPress); drag.set(0)
        landed.set(f.landed); typed = f.typed; todayV.set(f.today); priorityV.set(f.priority)
        listRoll.set(f.listRoll); part.set(f.part); scriptDone.set(f.scriptDone)
        landId = -1
        finishOnFly = false
        finishOnClose = false
    }

    private func takeOver(at time: Double) {
        let t = max(0, (time - epoch).truncatingRemainder(dividingBy: M.loop))
        let f = pose(t, at: time)
        adoptPose(f)
        scripted = false
        press.set(0)
        addPress.set(0)
        part.spring(0, M.partSpring, at: time)
        landed.timing(0, 0.16, at: time)
        scriptDone.timing(0, 0.16, at: time)
        let composerOpen = active == 1 && f.collapse < 0.05 && f.openH > 0.5
        if composerOpen {
            openW.spring(1, M.widthSpring, at: time)
            openH.spring(1, M.heightSpring, at: time)
            content.timing(1, 0.16, at: time)
            // A chip caught mid-change settles to the state the draft adopts, not a half tint.
            priorityV.timing(f.priority > 0.5 ? 1 : 0, 0.16, at: time)
            listRoll.timing(f.listRoll > 0.5 ? 1 : 0, 0.16, at: time)
            // The draft becomes real: what was typed, the chips as they are.
            title = String(QuickAddMorphTask.script.title.prefix(f.typed))
            isOpen = true
            today = f.today > 0.5
            priority = f.priority > 0.5
            listIndex = f.listRoll > 0.5 ? 1 : 0
        } else if active == 1 {
            content.timing(0, M.contentOut, at: time)
            collapse.timing(0, 0.16, at: time)
            fly.timing(0, 0.16, at: time)
            openW.spring(0, M.heightSpring, at: time)
            springClosed(at: time)
            typed = 0
            priorityV.timing(0, 0.16, at: time)
            listRoll.timing(0, 0.16, at: time)
            resetDraft()
        } else {
            fabIn.timing(1, 0.16, at: time)
            typed = 0
            priorityV.timing(0, 0.16, at: time)
            listRoll.timing(0, 0.16, at: time)
            resetDraft()
        }
    }

    private func closeForReturn(at time: Double) {
        content.timing(0, M.contentOut, at: time)
        openW.spring(0, M.heightSpring, at: time)
        springClosed(at: time)
        priorityV.timing(0, 0.16, at: time)
        listRoll.timing(0, 0.16, at: time)
    }

    private func restPose() {
        active = 0
        for anim in [\QuickAddMorphModel.openW, \.openH, \.content, \.collapse, \.fly, \.press, \.addPress,
                     \.landed, \.priorityV, \.listRoll, \.part, \.scriptDone] {
            self[keyPath: anim].set(0)
        }
        fabIn.set(1)
        todayV.set(1)
        typed = 0
        finishOnFly = false
        finishOnClose = false
    }

    private func resetDraft() {
        title = ""
        isOpen = false
        today = true
        priority = false
        listIndex = 0
        labels = (lists[0], lists.count > 1 ? lists[1] : lists[0])
    }

    // MARK: Composer actions

    private func to(_ anim: inout Anim, _ value: Double, _ s: M.Spring, delay: Double = 0, at time: Double) {
        if reducedMotion { anim.timing(value, 0.18, delay: 0, at: time) }
        else { anim.spring(value, s, delay: delay, at: time) }
    }

    /// Back to the resting button: a flight landed or a close finished.
    private func finishMorph() {
        active = 0
        openW.set(0); openH.set(0); content.set(0); collapse.set(0); fly.set(0); drag.set(0)
        fabIn.set(1)
        landId = -1
        landed.set(0)
        finishOnClose = false
        finishOnFly = false
    }

    private func springClosed(at time: Double) {
        if reducedMotion { openH.timing(0, 0.18, at: time) }
        else { openH.spring(0, M.widthSpring, clamp: true, at: time) }
        finishOnClose = true
    }

    func open() {
        let time = Anim.now
        bump()
        QuickAddMorphHaptics.light()
        isOpen = true
        active = 1
        fabIn.set(0)
        drag.set(0)
        finishOnClose = false
        to(&openW, 1, M.widthSpring, at: time)
        to(&openH, 1, M.heightSpring, delay: reducedMotion ? 0 : M.heightDelay, at: time)
        content.timing(1, M.contentIn, delay: reducedMotion ? 0 : M.contentDelay, at: time)
    }

    func close() {
        let time = Anim.now
        bump()
        isOpen = false
        content.timing(0, M.contentOut, at: time)
        to(&drag, 0, M.heightSpring, at: time)
        to(&openW, 0, M.heightSpring, at: time)
        springClosed(at: time)
    }

    func submit() {
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        let time = Anim.now
        QuickAddMorphHaptics.light()
        add(QuickAddMorphDraft(title: clean, today: today, priority: priority, list: lists[listIndex]), at: time)
        isOpen = false
        title = ""
        today = true
        priority = false
        todayV.timing(1, 0.2, at: time)
        priorityV.timing(0, 0.2, at: time)
        content.timing(0, M.contentOut, at: time)
        finishOnClose = false
        to(&collapse, 1, M.collapseSpring, at: time)
        let lead = reducedMotion ? 0 : M.fly - M.add
        fly.timing(1, reducedMotion ? 0.18 : M.flySpan, delay: lead, at: time)
        finishOnFly = true
        landed.timing(1, M.landSpan, delay: lead + M.landDelay, at: time)
        if reducedMotion { fabIn.timing(1, 0.18, delay: lead + M.fabDelay, at: time) }
        else { fabIn.spring(1, M.widthSpring, delay: lead + M.fabDelay, at: time) }
    }

    func toggleToday() {
        bump()
        QuickAddMorphHaptics.light()
        today.toggle()
        to(&todayV, today ? 1 : 0, M.chipSpring, at: Anim.now)
    }

    func togglePriority() {
        bump()
        QuickAddMorphHaptics.light()
        priority.toggle()
        to(&priorityV, priority ? 1 : 0, M.chipSpring, at: Anim.now)
    }

    /// The roll has two slots: the next name goes into the hidden one, then the roll moves to it.
    func cycleList() {
        let time = Anim.now
        bump()
        QuickAddMorphHaptics.light()
        let next = (listIndex + 1) % lists.count
        let showingSecond = listRoll.value(at: time) > 0.5
        listIndex = next
        labels = showingSecond ? (lists[next], labels.1) : (labels.0, lists[next])
        to(&listRoll, showingSecond ? 0 : 1, M.chipSpring, at: time)
    }

    func typedByHand(_ value: String) {
        bump()
        title = String(value.prefix(40))
    }

    func dragChanged(_ y: Double) {
        bump()
        drag.set(y > 0 ? y : y * M.dragUpRubber)
    }

    func dragEnded(_ y: Double, velocity: Double) {
        bump()
        if y > M.dragCloseDistance || velocity > M.dragCloseVelocity {
            close()
        } else {
            drag.spring(0, M.heightSpring, velocity: velocity, at: Anim.now)
        }
    }

    func keyboard(height: Double, duration: Double) {
        let target = height > 0 ? max(0, height - geometry.keyboardInset) : 0
        lift.timing(target, duration > 0 ? duration : 0.25, at: Anim.now)
    }

    func toggleRow(_ id: String) {
        bump()
        QuickAddMorphHaptics.light()
        let next = !checked.contains(id)
        if next { checked.insert(id) } else { checked.remove(id) }
        rowDone[id, default: Anim(next ? 0 : 1)].spring(next ? 1 : 0, M.doneSpring, at: Anim.now)
        doneCount += next ? 1 : -1
    }

    // MARK: List

    private func add(_ draft: QuickAddMorphDraft, at time: Double) {
        nextId += 1
        let task = QuickAddMorphTask(id: "new-\(nextId)", title: draft.title,
                                     meta: draft.today ? "\(draft.list) · Today" : draft.list,
                                     priority: draft.priority)
        let current = pendingSlots ?? slots
        let index = tasks.count
        pendingSlots = current.map { $0 >= M.insert ? $0 + 1 : $0 } + [M.insert]
        if pendingSlots != nil, flyPast {
            // A flight already under way when another one starts: its rows part now.
            flyPast = false
        }
        landId = index
        pillTitle = draft.title
        rowY.append(Anim(Double(M.insert) * M.row))
        rowDone[task.id] = Anim(0)
        tasks.append(task)
        scrollRequest += 1
    }

    private func applySlots(_ next: [Int], at time: Double) {
        for i in next.indices where i < rowY.count {
            let old = i < slots.count ? slots[i] : M.insert
            if next[i] != old { rowY[i].spring(Double(next[i]) * M.row, M.partSpring, at: time) }
        }
        slots = next
    }

    func slot(of index: Int) -> Int { index < slots.count ? slots[index] : M.insert }
}
