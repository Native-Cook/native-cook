import SwiftUI
import Observation

/// Where everything sits on the screen, in points.
struct DateChipCalendarGeometry: Equatable {
    private typealias M = DateChipCalendarMotion

    var width: Double
    var height: Double
    var top: Double
    var bottomInset: Double

    var cardWidth: Double { width - M.side * 2 }
    var barHeight: Double { M.bar + max(bottomInset, 12) }
    /// The picture takes what is left, as long as the open calendar still clears the bar.
    var artHeight: Double {
        let room = height - top - M.header - M.gap - barHeight
        let fitsOpen = room - M.openHeight - 8
        let fillsClosed = room - M.chipHeight - M.gap - (M.option * 2 + M.facts + M.note + M.gap * 3) - 16
        return max(M.artMin, min(M.artMax, fitsOpen, fillsClosed))
    }
    var chipTop: Double { top + M.header + artHeight + M.gap }
    var pageWidth: Double { cardWidth - M.gridPad * 2 }
    var cell: Double { pageWidth / 7 }
    var gridX: Double { M.side + M.gridPad }
    var gridY: Double { chipTop + M.chipHeight + M.gridTop }
    var chipX: Double { M.side + cardWidth / 2 }
    var chipY: Double { chipTop + M.chipHeight / 2 }
    var doneX: Double { M.side + cardWidth - 42 }
    var clearX: Double { M.side + cardWidth - 27 }
}

/// Every animated value of the calendar at one instant.
struct DateChipCalendarFrame {
    var open = 0.0
    var reveal = 0.0
    var roll = 0.0
    var hasValue = 0.0
    var start = -1.0
    var head = -1.0
    var complete = false
    var capStart = 0.0
    var capEnd = 0.0
    var page = 1.0
    var press = 0.0
    var auto = false
    var ghost = CGPoint.zero
    var ghostOpacity = 0.0
    var ghostPress = 0.0
}

/// Who drives the calendar: the loop's clock, or a finger. What the controls show is observed; every
/// per-frame value is not, so a frame never invalidates the view.
@Observable
final class DateChipCalendarModel {
    private typealias M = DateChipCalendarMotion
    private typealias Anim = DateChipCalendarAnim
    private typealias Stay = DateChipCalendarStay
    private enum Mode { case auto, manual, returning }

    // MARK: Observed

    var isOpen = false
    var label = Stay.scriptLabel
    var hasRange = false
    var page = Stay.december
    var range: (start: Int, end: Int)? {
        didSet { if let range { shownNights = range.end - range.start } }
    }
    /// The total keeps the last range's nights while it rolls out, so a cleared range never flashes the script's.
    var shownNights = DateChipCalendarStay.scriptEnd - DateChipCalendarStay.scriptStart
    var guests = 2
    var breakfast = false

    let months = Stay.months

    // MARK: Not observed

    @ObservationIgnored private var mode = Mode.auto
    @ObservationIgnored private var epoch = Anim.now
    @ObservationIgnored private var last = Anim.now
    @ObservationIgnored private var idle = 0.0
    @ObservationIgnored var held = 0
    @ObservationIgnored var reducedMotion = false
    @ObservationIgnored var geometry = DateChipCalendarGeometry(width: 393, height: 852, top: 59, bottomInset: 34)

    @ObservationIgnored private var open = Anim(0)
    @ObservationIgnored private var reveal = Anim(0)
    @ObservationIgnored private var roll = Anim(0)
    @ObservationIgnored private var hasValue = Anim(0)
    @ObservationIgnored private var start = -1.0
    @ObservationIgnored private var head = Anim(-1)
    @ObservationIgnored private var complete = false
    @ObservationIgnored private var capStart = Anim(0)
    @ObservationIgnored private var capEnd = Anim(0)
    @ObservationIgnored private var pageV = Anim(Double(Stay.december))
    @ObservationIgnored private var press = Anim(0)

    // Gesture bookkeeping.
    @ObservationIgnored private var dragKind = 0 // 0 none, 1 range, 2 page
    @ObservationIgnored private var lastDay = -1
    @ObservationIgnored private var pageBase = Stay.december

    func resting(at time: Double) -> Bool {
        guard mode == .manual, held == 0 else { return false }
        return [open, reveal, roll, hasValue, head, capStart, capEnd, pageV, press].allSatisfy { $0.done(at: time) }
    }

    // MARK: Frame

    func frame(at time: Double, playing: Bool) -> DateChipCalendarFrame {
        let dt = min(max(time - last, 0), 0.048)
        last = time

        if mode == .auto, !playing {
            mode = .manual
            restValues()
            DispatchQueue.main.async { self.resetAll() }
        }
        switch mode {
        case .auto:
            let t = (time - epoch).truncatingRemainder(dividingBy: M.loop)
            return pose(max(0, t), at: time)
        case .manual:
            guard playing else { break }
            if held > 0 {
                idle = 0
                break
            }
            idle += dt
            if idle >= M.resumeAfter {
                settleForReturn(at: time)
                mode = .returning
            }
        case .returning:
            if abs(open.value(at: time)) < 0.01, roll.value(at: time) < 0.01,
               abs(pageV.value(at: time) - Double(Stay.december)) < 0.01 {
                restValues()
                epoch = time
                mode = .auto
                DispatchQueue.main.async { self.resetAll() }
                return pose(0, at: time)
            }
        }
        return live(at: time)
    }

    private func live(at time: Double) -> DateChipCalendarFrame {
        var f = DateChipCalendarFrame()
        f.open = open.value(at: time)
        f.reveal = reveal.value(at: time)
        f.roll = roll.value(at: time)
        f.hasValue = hasValue.value(at: time)
        f.start = start
        f.head = head.value(at: time)
        f.complete = complete
        f.capStart = capStart.value(at: time)
        f.capEnd = capEnd.value(at: time)
        f.page = pageV.value(at: time)
        f.press = press.value(at: time)
        return f
    }

    /// Every animated value at loop time t: a pure function, so the last frame equals the first.
    private func pose(_ t: Double, at time: Double) -> DateChipCalendarFrame {
        var f = DateChipCalendarFrame()
        let up = M.springAt(t - M.open, M.openSpring)
        let down = M.springAt(t - M.fold, M.foldSpring)
        f.open = t < M.fold ? up : max(0, 1 - down)
        f.reveal = M.seg(t, M.open + M.revealDelay, M.revealIn) * (1 - M.seg(t, M.fold, M.revealOut))
        let picked = t >= M.pick && t < M.clear
        let s = Double(Stay.scriptStart), e = Double(Stay.scriptEnd)
        f.start = picked ? s : -1
        f.head = picked ? M.mix(M.easeInOutCubic(M.seg(t, M.dragStart, M.dragSpan)), s, e) : -1
        f.capStart = picked ? max(0, M.springAt(t - M.pick, M.capSpring)) : 0
        f.capEnd = picked ? max(0, M.springAt(t - M.dragStart, M.capSpring)) : 0
        f.complete = t >= M.commit && t < M.clear
        let rollIn = M.easeInOutCubic(M.seg(t, M.commit, M.rollSpan))
        let rollOut = M.easeInOutCubic(M.seg(t, M.clear, M.rollSpan))
        f.roll = rollIn * (1 - rollOut)
        f.hasValue = M.seg(t, M.commit, 0.16) * (1 - M.seg(t, M.clear, 0.16))
        f.press = t > M.open - 0.1 && t < M.open + 0.12 ? Self.ghostPress(t) : 0
        f.page = Double(Stay.december)
        f.auto = true

        // The ghost finger: chip, first day, along the range, Done, clear.
        let g = geometry
        let first = dayCentre(Stay.scriptStart, g)
        var x = g.chipX, y = g.chipY
        if t >= M.open + 0.3, t < M.dragStart {
            let u = M.easeInOutCubic(M.seg(t, M.pick - 0.42, 0.34))
            x = M.mix(u, g.chipX, first.x)
            y = M.mix(u, g.chipY, first.y)
        } else if t >= M.dragStart, t < M.commit {
            let h = dayCentre(f.head, g)
            x = h.x
            y = h.y
        } else if t >= M.commit, t < M.fold + 0.2 {
            let end = dayCentre(Stay.scriptEnd, g)
            let u = M.easeInOutCubic(M.seg(t, M.commit + 0.3, 0.44))
            x = M.mix(u, end.x, g.doneX)
            y = M.mix(u, end.y, g.chipY)
        } else if t >= M.clear - 0.5 {
            x = g.clearX
            y = g.chipY
        }
        f.ghost = CGPoint(x: x, y: y)
        f.ghostOpacity = Self.ghostOpacity(t)
        f.ghostPress = Self.ghostPress(t)
        return f
    }

    private func dayCentre<T: BinaryFloatingPoint>(_ day: T, _ g: DateChipCalendarGeometry) -> CGPoint {
        let month = months[Stay.december]
        let index = Double(day) - Double(month.first) + Double(month.offset)
        var row = floor(index / 7)
        var col = index - row * 7
        if col > 6.5 { row += 1; col = 0 } else if col > 6 { col = 6 }
        return CGPoint(x: g.gridX + (col + 0.5) * g.cell, y: g.gridY + (row + 0.5) * M.dayRow)
    }

    private func dayCentre(_ day: Int, _ g: DateChipCalendarGeometry) -> CGPoint {
        dayCentre(Double(day), g)
    }

    private static let taps = [M.open, M.pick, M.done, M.clear]

    static func ghostPress(_ t: Double) -> Double {
        var p = 0.0
        for tap in taps {
            let d = t - tap
            if d > -0.1, d < 0.12 { p = max(p, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12) }
        }
        // The finger stays down through the drag.
        if t >= M.dragStart - 0.08, t <= M.dragStart + M.dragSpan { p = 1 }
        return p
    }

    static func ghostOpacity(_ t: Double) -> Double {
        let a = M.seg(t, M.ghostIn, 0.2) * (1 - M.seg(t, M.open + 0.26, 0.2))
        let b = M.seg(t, M.pick - 0.42, 0.2) * (1 - M.seg(t, M.done + 0.16, 0.2))
        let c = M.seg(t, M.clear - 0.45, 0.2) * (1 - M.seg(t, M.clear + 0.18, 0.2))
        return max(a, b, c)
    }

    // MARK: Takeover and return

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

    private func takeOver(at time: Double) {
        let t = max(0, (time - epoch).truncatingRemainder(dividingBy: M.loop))
        let f = pose(t, at: time)
        open.set(f.open); reveal.set(f.reveal); roll.set(f.roll); hasValue.set(f.hasValue)
        start = f.start; head.set(f.head); complete = f.complete
        capStart.set(f.capStart); capEnd.set(f.capEnd); pageV.set(f.page)
        press.set(0)
        if f.head >= 0 { head.timing(f.head.rounded(), 0.1, at: time) }
        if start >= 0, abs(f.head - start) > 0.5 {
            complete = true
            capEnd.timing(1, 0.1, at: time)
            hasValue.timing(1, 0.16, at: time)
            roll.timing(1, 0.2, at: time)
        } else {
            hasValue.timing(0, 0.16, at: time)
            roll.timing(0, 0.2, at: time)
        }
        let opened = f.open > 0.5
        if opened {
            open.spring(1, M.openSpring, at: time)
            reveal.timing(1, 0.16, at: time)
        } else {
            open.spring(0, M.foldSpring, at: time)
            reveal.timing(0, 0.16, at: time)
        }
        // What the controls show becomes real.
        isOpen = opened
        let end = Int(f.head.rounded())
        if start >= 0, end != Int(start) {
            let s = Int(start)
            hasRange = true
            label = DateChipCalendarMonth.formatRange(min(s, end), max(s, end))
            range = (min(s, end), max(s, end))
        }
    }

    private func settleForReturn(at time: Double) {
        open.spring(0, M.foldSpring, at: time)
        reveal.timing(0, M.revealOut, at: time)
        roll.timing(0, M.rollSpan, at: time)
        hasValue.timing(0, 0.16, at: time)
        pageV.spring(Double(Stay.december), M.foldSpring, at: time)
    }

    private func restValues() {
        open.set(0); reveal.set(0); roll.set(0); hasValue.set(0)
        pageV.set(Double(Stay.december))
        start = -1; head.set(-1); complete = false
        capStart.set(0); capEnd.set(0); press.set(0)
    }

    private func resetAll() {
        isOpen = false
        hasRange = false
        page = Stay.december
        label = Stay.scriptLabel
        range = nil
        shownNights = Stay.scriptEnd - Stay.scriptStart
    }

    // MARK: Chip actions

    private func go(_ anim: inout Anim, _ value: Double, _ s: M.Spring, at time: Double) {
        if reducedMotion { anim.timing(value, 0.18, at: time) } else { anim.spring(value, s, at: time) }
    }

    func openCalendar() {
        let time = Anim.now
        bump()
        DateChipCalendarHaptics.select()
        isOpen = true
        go(&open, 1, M.openSpring, at: time)
        reveal.timing(1, reducedMotion ? 0.12 : M.revealIn, at: time)
    }

    func fold() {
        let time = Anim.now
        bump()
        DateChipCalendarHaptics.select()
        isOpen = false
        go(&open, 0, M.foldSpring, at: time)
        reveal.timing(0, reducedMotion ? 0.12 : M.revealOut, at: time)
    }

    private func commit(_ a: Int, _ b: Int) {
        let time = Anim.now
        bump()
        let s = min(a, b), e = max(a, b)
        label = DateChipCalendarMonth.formatRange(s, e)
        hasRange = true
        range = (s, e)
        hasValue.timing(1, 0.16, at: time)
        roll.timing(1, reducedMotion ? 0.12 : M.rollSpan, at: time)
    }

    func clear() {
        let time = Anim.now
        bump()
        DateChipCalendarHaptics.select()
        hasRange = false
        range = nil
        start = -1
        head.set(-1)
        complete = false
        capStart.set(0)
        capEnd.set(0)
        hasValue.timing(0, 0.16, at: time)
        roll.timing(0, reducedMotion ? 0.12 : M.rollSpan, at: time)
    }

    func changePage(_ next: Int) {
        bump()
        let target = max(0, min(months.count - 1, next))
        page = target
        if reducedMotion { pageV.set(Double(target)) } else { pageV.spring(Double(target), M.pageSpring) }
    }

    /// A new first day replaces a finished stay: the chip and the total go back to empty until it has an end.
    private func dropRange(at time: Double) {
        hasRange = false
        range = nil
        hasValue.timing(0, 0.16, at: time)
        roll.timing(0, reducedMotion ? 0.12 : M.rollSpan, at: time)
    }

    private func isBlocked(_ day: Int) -> Bool { Stay.blocked.contains(day) }

    private func blockedBetween(_ a: Int, _ b: Int) -> Bool {
        let lo = min(a, b), hi = max(a, b)
        return Stay.blocked.contains { $0 >= lo && $0 <= hi }
    }

    /// The furthest day from `anchor` towards `day` that a stay can reach without crossing a blocked night.
    private func reach(_ anchor: Int, _ day: Int) -> Int {
        let step = day > anchor ? 1 : -1
        var out = anchor
        while out != day {
            let next = out + step
            if isBlocked(next) { break }
            out = next
        }
        return out
    }

    /// The day under a point of the grid view (origin at the card's left edge, under the chip).
    private func dayAt(_ x: Double, _ y: Double, at time: Double) -> Int {
        let index = Int(pageV.value(at: time).rounded())
        guard index >= 0, index < months.count else { return -1 }
        let month = months[index]
        let cell = geometry.cell
        let col = Int(floor((x - M.gridPad) / cell))
        let row = Int(floor((y - M.gridTop) / M.dayRow))
        if col < 0 || col > 6 || row < 0 || row >= M.gridRows { return -1 }
        let day = row * 7 + col - month.offset
        if day < 0 || day >= month.days { return -1 }
        let value = month.first + day
        if value < Stay.today || isBlocked(value) { return -1 }
        return value
    }

    private func beginAt(_ day: Int, at time: Double) {
        if complete { dropRange(at: time) }
        start = Double(day)
        head.set(Double(day))
        complete = false
        capEnd.set(0)
        capStart.set(0)
        capStart.spring(1, M.capSpring, at: time)
        lastDay = day
        DateChipCalendarHaptics.select()
    }

    func tap(_ x: Double, _ y: Double) {
        let time = Anim.now
        bump()
        let day = dayAt(x, y, at: time)
        guard day >= 0 else { return }
        let s = Int(start)
        if start < 0 || complete || day == s || blockedBetween(s, day) {
            beginAt(day, at: time)
            return
        }
        head.timing(Double(day), M.dragDay * 2, at: time)
        capEnd.spring(1, M.capSpring, at: time)
        complete = true
        DateChipCalendarHaptics.select()
        commit(s, day)
    }

    func panBegan(startX: Double, startY: Double, dx: Double, dy: Double) {
        let time = Anim.now
        bump()
        let day = dayAt(startX, startY, at: time)
        let s = Int(start)
        let h = Int(head.value(at: time).rounded())
        let onCap = day >= 0 && start >= 0 && (day == s || day == h)
        if !onCap, abs(dx) > abs(dy) * 1.2 {
            dragKind = 2
            pageBase = Int(pageV.value(at: time).rounded())
            return
        }
        guard day >= 0 else {
            dragKind = 0
            return
        }
        dragKind = 1
        if onCap, complete {
            // Grab either end: the other one becomes the anchor.
            if day == s {
                start = Double(h)
                head.set(Double(s))
            }
            lastDay = day
            complete = false
        } else {
            beginAt(day, at: time)
        }
    }

    func panMoved(x: Double, y: Double, dx: Double) {
        let time = Anim.now
        bump()
        if dragKind == 2 {
            var next = Double(pageBase) - dx / geometry.pageWidth
            let maxPage = Double(months.count - 1)
            if next < 0 { next *= M.pageRubber } else if next > maxPage { next = maxPage + (next - maxPage) * M.pageRubber }
            pageV.set(next)
            return
        }
        guard dragKind == 1 else { return }
        let raw = dayAt(x, y, at: time)
        guard raw >= 0 else { return }
        let day = reach(Int(start), raw)
        guard day != lastDay else { return }
        lastDay = day
        head.timing(Double(day), M.dragDay, at: time)
        capEnd.spring(day == Int(start) ? 0 : 1, M.capSpring, at: time)
        DateChipCalendarHaptics.select()
    }

    func panEnded(vx: Double) {
        let time = Anim.now
        bump()
        if dragKind == 2 {
            let moved = pageV.value(at: time) - Double(pageBase)
            var next = pageBase
            if moved > M.pageThreshold || vx < -M.pageVelocity { next = pageBase + 1 }
            else if moved < -M.pageThreshold || vx > M.pageVelocity { next = pageBase - 1 }
            next = max(0, min(months.count - 1, next))
            pageV.spring(Double(next), M.pageSpring, velocity: -vx / geometry.pageWidth, at: time)
            if next != pageBase { page = next }
        } else if dragKind == 1 {
            let s = Int(start)
            if lastDay != s {
                complete = true
                commit(s, lastDay)
            }
        }
        dragKind = 0
    }

    // MARK: Form

    func stepGuests(_ delta: Int) {
        bump()
        guests = max(1, min(4, guests + delta))
    }
}
