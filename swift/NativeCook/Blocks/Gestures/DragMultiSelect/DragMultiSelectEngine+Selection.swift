import Foundation

extension DragMultiSelectEngine {
    // MARK: Mode

    /// The toolbar, the nav bar and the circles follow the mode: a spring (or a short fade under
    /// Reduce Motion) from wherever they are.
    private func setMode(_ newMode: Int) {
        mode = newMode
        let on = Double(newMode)
        if reduceMotion {
            shown.timing(to: on, ms: DragMultiSelectTiming.reduced)
        } else {
            shown.spring(to: on, DragMultiSelectSprings.toolbar)
        }
        navFade.timing(to: on, ms: DragMultiSelectTiming.reduced, easing: .out)
        for row in live { moveCircle(of: row) }
        state.isSelecting = newMode == 1
    }

    /// A short stagger down the list, capped so long lists do not wait.
    func moveCircle(of row: DragMultiSelectRow) {
        let on = mode == 1 ? 1.0 : 0.0
        if reduceMotion {
            row.circleIn.timing(to: on, ms: DragMultiSelectTiming.reduced)
        } else {
            let stagger = on == 1 ? Double(min(row.liveIndex, 12)) * DragMultiSelectTiming.circleStagger : 0
            row.circleIn.spring(to: on, DragMultiSelectSprings.circles, delay: stagger)
        }
    }

    // MARK: Selection

    /// Hands the selection to the rows: which bands show, where they join and what plays on a join.
    func publish(_ next: [Int]) {
        let previous = sel
        sel = next
        var count = 0
        for value in next where value == 1 { count += 1 }
        if state.selectedCount != count { state.selectedCount = count }
        let rows = min(live.count, next.count)
        for index in 0..<rows {
            let row = live[index]
            let on = next[index] == 1
            let was = index < previous.count && previous[index] == 1
            let above = index > 0 && next[index - 1] == 1
            let below = index + 1 < next.count && next[index + 1] == 1
            let top = on && above ? 0 : DragMultiSelectLayout.bandRadius
            let bottom = on && below ? 0 : DragMultiSelectLayout.bandRadius
            if row.isSelected != on { row.isSelected = on }
            let seam = top == 0
            if row.seam != seam { row.seam = seam }
            if row.topTarget != top {
                row.topTarget = top
                row.topRadius.timing(to: top, ms: DragMultiSelectTiming.corner, easing: .inOut)
            }
            if row.bottomTarget != bottom {
                row.bottomTarget = bottom
                row.bottomRadius.timing(to: bottom, ms: DragMultiSelectTiming.corner, easing: .inOut)
            }
            if on != was { join(row, on: on, above: above, below: below) }
        }
    }

    /// Grow out of (or shrink back into) the neighbour that is already selected.
    private func join(_ row: DragMultiSelectRow, on: Bool, above: Bool, below: Bool) {
        row.origin = above ? -1 : below ? 1 : 0
        if reduceMotion {
            row.band.timing(to: on ? 1 : 0, ms: DragMultiSelectTiming.reduced)
        } else if on {
            row.band.spring(to: 1, DragMultiSelectSprings.band)
        } else {
            row.band.timing(to: 0, ms: DragMultiSelectTiming.bandFade, easing: .out)
        }
        if on && !reduceMotion {
            row.gloss.set(0)
            row.gloss.timing(to: 1, ms: DragMultiSelectTiming.gloss, easing: .inOut)
            row.ring.set(0)
            row.ring.timing(to: 1, ms: DragMultiSelectTiming.ripple, easing: .out)
        }
    }

    func rowAt(_ y: Double) -> Int {
        let index = Int(((y + scrollY - listTop) / DragMultiSelectLayout.row).rounded(.down))
        return min(max(index, 0), max(sel.count - 1, 0))
    }

    func enter(_ row: Int, silent: Bool) {
        if mode == 1 { return }
        setMode(1)
        var next = sel
        if row < next.count { next[row] = 1 }
        publish(next)
        haptic(.enter, silent: silent)
    }

    func exit(silent: Bool) {
        if mode == 0 { return }
        setMode(0)
        dragging = false
        publish(Array(repeating: 0, count: sel.count))
        haptic(.exit, silent: silent)
    }

    func toggle(_ row: Int, silent: Bool) {
        guard mode == 1, row < sel.count else { return }
        var next = sel
        next[row] = next[row] == 1 ? 0 : 1
        publish(next)
        haptic(.tick, silent: silent)
    }

    func dragBegin(_ row: Int, forceTarget: Int) {
        dragging = true
        anchor = row
        current = -1
        base = sel
        target = forceTarget >= 0 ? forceTarget : (row < sel.count && sel[row] == 1 ? 0 : 1)
    }

    func dragMove(silent: Bool) {
        guard dragging else { return }
        let row = rowAt(fingerY)
        if row == current { return }
        current = row
        let from = min(anchor, row)
        let to = max(anchor, row)
        var next = base
        for index in from...to where index < next.count { next[index] = target }
        if next == sel { return }
        publish(next)
        haptic(.tick, silent: silent)
    }

    func dragEnd() {
        if dragging && lastSpeed != 0 && !reduceMotion {
            // Velocity handoff: let go while the list is auto-scrolling and it keeps gliding.
            coast = lastSpeed * DragMultiSelectSelect.autoScrollMax
        }
        lastSpeed = 0
        dragging = false
        lockScroll = false
    }

    /// Edge auto-scroll, every frame while a drag holds the finger near the top or the toolbar.
    func autoScroll(dt: Double, silent: Bool) {
        guard dragging else { return }
        let layout = DragMultiSelectLayout.self
        let y = fingerY
        let bottomEdge = viewport - bottomInset - layout.toolbarHeight - layout.toolbarBottom - 8
        var speed = 0.0
        if y > bottomEdge - layout.edge {
            speed = min((y - (bottomEdge - layout.edge)) / layout.edge, 1)
        } else if y < listTop + layout.edge {
            speed = -min((listTop + layout.edge - y) / layout.edge, 1)
        }
        if speed != 0 && lastSpeed == 0 { haptic(.edge, silent: silent) }
        lastSpeed = speed
        if speed == 0 { return }
        let maxScroll = max(contentHeight - viewport, 0)
        let next = min(max(scrollY + speed * DragMultiSelectSelect.autoScrollMax * dt, 0), maxScroll)
        if next == scrollY { return }
        scrollY = next
        scrollTo(next)
        dragMove(silent: silent)
    }

    // MARK: Press and trash

    /// Anticipation: a band grows in from the left under a finger that is holding a row.
    func anticipate(_ row: Int) {
        if reduceMotion || mode == 1 { return }
        state.pressRow = row
        pressP.set(0)
        pressP.timing(to: 1, ms: DragMultiSelectSelect.longPress, easing: .out)
    }

    func kickTrash() {
        if reduceMotion { return }
        trashKick.animate([
            .timing(to: 1, ms: DragMultiSelectTiming.trashUp, easing: .out),
            .spring(to: 0, DragMultiSelectSprings.trash),
        ])
    }

    /// VoiceOver's activate on a row: start selecting with it, or toggle it.
    func selectFromAccessibility(_ row: DragMultiSelectRow) {
        guard row.liveIndex != DragMultiSelectRow.noIndex else { return }
        userTouchBegan()
        if mode == 0 {
            enter(row.liveIndex, silent: false)
        } else {
            toggle(row.liveIndex, silent: false)
        }
        userTouchEnded()
    }

    /// Leaving selection: Done, When, Move, More.
    func leave() {
        exit(silent: false)
    }
}
