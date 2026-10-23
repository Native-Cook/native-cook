import Foundation

extension DragMultiSelectEngine {
    /// Payoff: the selected rows stream out to the right, one after another, toward the trash; the gap closes.
    func deleteSelected() {
        let goneIndices = sel.indices.filter { sel[$0] == 1 && $0 < live.count }
        guard !goneIndices.isEmpty else { return }
        haptic(.commit, silent: false)
        kickTrash()
        // The rows that leave keep the look they had when they were picked.
        let goneRows = goneIndices.map { live[$0] }
        for row in goneRows { row.freeze() }
        live.removeAll { $0.isExiting }
        sel = Array(repeating: 0, count: live.count)
        leave()
        for (order, row) in goneRows.enumerated() { beginExit(row, order: order) }
        reindexLiveRows(animated: !reduceMotion)
        state.selectedCount = 0
    }

    private func beginExit(_ row: DragMultiSelectRow, order: Int) {
        if reduceMotion {
            // FadeOut.duration(180)
            row.appear.timing(to: 0, ms: DragMultiSelectTiming.reduced)
        } else {
            // SlideOutRight.duration(300).delay(order * 55): off the screen's right edge.
            row.x.timing(
                to: width,
                ms: DragMultiSelectTiming.streamOut,
                delay: Double(order) * DragMultiSelectTiming.streamStagger
            )
        }
    }

    /// The demo puts the deleted rows back where they were, growing in.
    func restoreAll() {
        let present = Set(live.map(\.id))
        var added: [DragMultiSelectRow] = []
        for (order, task) in DragMultiSelectTasks.all.enumerated() where !present.contains(task.id) {
            let row = makeRow(task, order: order, liveIndex: order)
            beginEntrance(row)
            added.append(row)
        }
        guard !added.isEmpty else { return }
        live = (live + added).sorted { $0.order < $1.order }
        state.mounted = (state.mounted + added).sorted { $0.order < $1.order }
        sel = Array(repeating: 0, count: live.count)
        reindexLiveRows(animated: !reduceMotion)
        for row in added { row.y.set(row.targetY) }
    }

    private func beginEntrance(_ row: DragMultiSelectRow) {
        row.appear.set(0)
        if reduceMotion {
            // FadeIn.duration(180)
            row.appear.timing(to: 1, ms: DragMultiSelectTiming.reduced)
        } else {
            // `duration(320).springify().damping(18)`: duration mode uses the default mass 4 and
            // dampingRatio 1; the explicit damping is retained in the token but ignored by RN.
            row.lift.set(DragMultiSelectTokens.Component.restoreLift)
            row.appear.durationSpring(to: 1, ms: DragMultiSelectTiming.restore)
            row.lift.durationSpring(to: 0, ms: DragMultiSelectTiming.restore)
        }
    }

    /// Rows take their new places in the list: on the collapse spring, or at once under Reduce Motion.
    private func reindexLiveRows(animated: Bool) {
        for (index, row) in live.enumerated() {
            row.liveIndex = index
            let place = listTop + DragMultiSelectLayout.row * Double(index)
            guard row.targetY != place else { continue }
            row.targetY = place
            if animated {
                // A layout animation starts from the current place at rest, whatever the row was doing.
                row.y.freeze()
                // Reanimated's layout builder keeps its default mass 4 when only damping and
                // stiffness are supplied.
                row.y.spring(to: place, DragMultiSelectSprings.layoutCollapse)
            } else {
                row.y.set(place)
            }
        }
        contentChanged?()
    }

    /// A row that has finished leaving comes off the screen.
    func sweepExitedRows() {
        guard state.mounted.contains(where: { $0.isExiting }) else { return }
        let gone = state.mounted.filter { $0.isExiting && !$0.x.isRunning && !$0.appear.isRunning }
        guard !gone.isEmpty else { return }
        state.mounted.removeAll { row in gone.contains { $0 === row } }
    }

    /// Restores the complete task list without an entrance animation. Used only where the script
    /// must have a deterministic seam or restart after user interruption.
    func resetRowsToRest() {
        // An interruption may land between adding a restored row and sweeping its exiting predecessor.
        // Prefer the live instance and avoid assuming mounted IDs are momentarily unique.
        var existing: [String: DragMultiSelectRow] = [:]
        for row in state.mounted where existing[row.id] == nil || existing[row.id]?.isExiting == true {
            existing[row.id] = row
        }
        live = DragMultiSelectTasks.all.enumerated().map { index, task in
            existing[task.id] ?? makeRow(task, order: index, liveIndex: index)
        }
        for (index, row) in live.enumerated() {
            row.liveIndex = index
            row.isSelected = false
            row.seam = false
            row.origin = 0
            row.isExiting = false
            row.topTarget = DragMultiSelectLayout.bandRadius
            row.bottomTarget = DragMultiSelectLayout.bandRadius
            row.targetY = listTop + DragMultiSelectLayout.row * Double(index)
            row.band.set(0)
            row.gloss.set(1)
            row.ring.set(1)
            row.topRadius.set(DragMultiSelectLayout.bandRadius)
            row.bottomRadius.set(DragMultiSelectLayout.bandRadius)
            row.circleIn.set(0)
            row.y.set(row.targetY)
            row.x.set(0)
            row.appear.set(1)
            row.lift.set(0)
        }
        state.mounted = live
        contentChanged?()
    }
}
