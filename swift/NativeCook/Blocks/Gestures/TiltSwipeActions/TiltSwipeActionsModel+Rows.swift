import SwiftUI

/// The swipe of one row: begin, move, end, the same three calls the finger and the demo script make.
extension TiltSwipeActionsModel {
    private typealias Swipe = TiltSwipeActionsMotion.Swipe
    private typealias Tilt = TiltSwipeActionsMotion.Tilt
    private typealias Spring = TiltSwipeActionsMotion.Spring
    private typealias Timing = TiltSwipeActionsMotion.Timing

    private var width: Double { TiltSwipeActionsFlat.width }

    /// Another row took the touch: close this one.
    func setActiveKey(_ key: String) {
        guard key != activeKey else { return }
        activeKey = key
        for row in rows where row.key != key && !row.dragging && !row.removing && row.x.value != 0 {
            close(row)
        }
    }

    func begin(_ row: TiltSwipeActionsRow) {
        if row.removing { return }
        row.x.cancel()
        row.start = row.x.value
        row.dragging = true
        setActiveKey(row.key)
        if !reduceMotion { row.lift.spring(to: 1, Spring.lift) }
    }

    func move(_ row: TiltSwipeActionsRow, translation: Double, velocity: Double, silent: Bool) {
        guard row.dragging else { return }
        let limit = width * TiltSwipeActionsTokens.Row.limit
        let raw = row.start + translation
        var next = raw
        if raw > 0 {
            next = raw * Swipe.rightRubber
        } else if raw < -limit {
            next = -limit + (raw + limit) * Swipe.fullRubber
        }
        let wasOpen = -row.x.value >= Swipe.openAt
        row.x.set(next)
        if !reduceMotion {
            let lean = (-velocity * Tilt.perVelocity).tiltClamped(-Tilt.max, Tilt.max)
            row.tilt.spring(to: lean, Spring.follow)
        }
        if !wasOpen && -next >= Swipe.openAt { haptic(.open, silent: silent) }
        setFull(row, on: -next > width * Swipe.fullAt, silent: silent)
    }

    func end(_ row: TiltSwipeActionsRow, velocity: Double) {
        guard row.dragging else { return }
        row.dragging = false
        row.lift.spring(to: 0, Spring.lift)
        row.tilt.spring(to: 0, Spring.follow)
        let stripWidth = -row.x.value
        if row.full.value > 0.5 || (velocity < Swipe.deleteFlick && stripWidth > width * Swipe.deleteFlickAt) {
            remove(row)
        } else if (stripWidth > Swipe.openAt || velocity < Swipe.openFlick) && velocity < TiltSwipeActionsTokens.Row.releaseRightVelocity {
            row.opened = true
            snap(row, to: TiltSwipeActionsLayout.openX, velocity: velocity)
        } else {
            close(row, velocity: velocity)
        }
    }

    func close(_ row: TiltSwipeActionsRow, velocity: Double = 0) {
        row.opened = false
        snap(row, to: 0, velocity: velocity)
        setFull(row, on: false, silent: true)
    }

    /// The Delete tile, the accessibility action and a full swipe all end here.
    func remove(_ row: TiltSwipeActionsRow) {
        row.removing = true
        row.dragging = false
        row.lift.spring(to: 0, Spring.lift)
        let key = row.key
        let collapse = { [weak self, weak row] in
            guard let self, let row else { return }
            let done: (Bool) -> Void = { [weak self] finished in
                if finished { self?.deferred.append { [weak self] in self?.deleteRow(key) } }
            }
            if self.reduceMotion {
                row.height.timing(to: 0, duration: Timing.reducedGap, completion: done)
            } else {
                row.height.spring(to: 0, Spring.collapse, completion: done)
            }
        }
        if reduceMotion {
            row.fade.timing(to: 0, duration: Timing.reduced) { finished in
                if finished { collapse() }
            }
            return
        }
        // Keep leaning into the throw while it leaves.
        row.tilt.spring(to: Tilt.max, Spring.follow)
        row.x.timing(to: -width * TiltSwipeActionsTokens.Row.flyPast, duration: Timing.flyOut, easing: .out) { [weak row] finished in
            guard finished, let row else { return }
            row.strip.set(0)
            collapse()
        }
    }

    func removeNow(_ key: String) {
        guard let row = rows.first(where: { $0.key == key }), !row.removing else { return }
        remove(row)
    }

    private func snap(_ row: TiltSwipeActionsRow, to target: Double, velocity: Double) {
        if reduceMotion {
            row.x.timing(to: target, duration: Timing.reduced)
        } else {
            row.x.spring(to: target, Spring.snap, velocity: velocity)
        }
    }

    private func setFull(_ row: TiltSwipeActionsRow, on: Bool, silent: Bool) {
        if (row.full.value > 0.5) == on { return }
        if reduceMotion {
            row.full.timing(to: on ? 1 : 0, duration: Timing.reduced)
        } else {
            row.full.spring(to: on ? 1 : 0, Spring.tileStretch)
        }
        row.red.timing(to: on ? 1 : 0, duration: Timing.colour)
        haptic(on ? .arm : .disarm, silent: silent)
    }

    private func haptic(_ kind: TiltSwipeActionsHaptic, silent: Bool) {
        if !silent { kind.play() }
    }
}
