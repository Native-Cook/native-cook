import SwiftUI

/// Autoplay: a scripted finger drives the same begin / move / end as a real pan. Any real touch stops
/// it; it starts again from rest `resume` seconds after the last one.
extension TiltSwipeActionsModel {
    private typealias Demo = TiltSwipeActionsMotion.Demo

    private func ease(_ u: Double) -> Double {
        let k = u.tiltClamped(0, 1)
        return (1 - cos(Double.pi * k)) / 2
    }

    private func easeIn(_ u: Double) -> Double {
        let k = u.tiltClamped(0, 1)
        return k * k * k
    }

    private func showFinger(_ on: Bool) {
        ghostOpacity.timing(to: on ? 1 : 0, duration: Demo.fingerFade)
    }

    private func send(_ key: String, down: Bool, tx: Double, vx: Double) {
        script = TiltSwipeActionsScriptTouch(seq: script.seq + 1, key: key, down: down, tx: tx, vx: vx)
        scriptDirty = true
    }

    /// The rows' reaction to the script: only the last write of a frame is seen.
    private func flushScript() {
        guard scriptDirty else { return }
        scriptDirty = false
        let touch = script
        guard let row = rows.first(where: { $0.key == touch.key }) else { return }
        if touch.down {
            if !row.dragging { begin(row) }
            move(row, translation: touch.tx, velocity: touch.vx, silent: true)
        } else if row.dragging {
            move(row, translation: touch.tx, velocity: touch.vx, silent: true)
            end(row, velocity: touch.vx)
        }
    }

    /// One drag on a row: begin at `downAt`, follow `path(time)` until `upAt`, release with the path's velocity.
    private func drag(from t0: Double, to t1: Double, dt: Double, row index: Int, down downAt: Double, up upAt: Double, x0: Double, path: (Double) -> Double) {
        let key = TiltSwipeActionsData.spending[index].key
        let y = TiltSwipeActionsFlat.rowsTop + TiltSwipeActionsLayout.row * (Double(index) + 0.5)
        if t0 < downAt && t1 >= downAt {
            scriptedDown = key
            scriptedLastTx = 0
            showFinger(true)
            send(key, down: true, tx: 0, vx: 0)
        }
        if scriptedDown != key { return }
        if t1 >= downAt && t1 < upAt {
            let tx = path(t1)
            send(key, down: true, tx: tx, vx: (tx - scriptedLastTx) / dt)
            scriptedLastTx = tx
            ghostX = x0 + tx
            ghostY = y
        }
        if t0 < upAt && t1 >= upAt {
            let tx = path(upAt)
            send(key, down: false, tx: tx, vx: (tx - scriptedLastTx) / dt)
            scriptedDown = ""
            showFinger(false)
        }
    }

    func demoFrame(_ rawDt: Double) {
        guard autoPlay else { return }
        let wallDt = max(rawDt, 0)
        let dt = min(wallDt, 0.1)
        if paused {
            if !touching { quiet += dt }
            if tidy < 0 && quiet >= TiltSwipeActionsMotion.resume {
                tidy = 0
                setActiveKey("")
                deferred.append { [weak self] in self?.reset() }
            }
            if tidy >= 0 {
                tidy += dt
                if tidy >= Demo.tidy {
                    tidy = -1
                    time = 0
                    paused = false
                }
            }
            return
        }
        let t0 = time
        let t1 = t0 + wallDt
        let open = Demo.open
        drag(from: t0, to: t1, dt: dt, row: open.row, down: open.down, up: open.up, x0: open.x) { time in
            open.to * self.ease((time - open.down) / (open.moveEnd - open.down))
        }
        let removal = Demo.remove
        drag(from: t0, to: t1, dt: dt, row: removal.row, down: removal.down, up: removal.up, x0: removal.x) { time in
            removal.to * self.easeIn((time - removal.down) / (removal.moveEnd - removal.down))
        }
        let lean = Demo.lean
        drag(from: t0, to: t1, dt: dt, row: lean.row, down: lean.down, up: lean.up, x0: lean.x) { time in
            time < lean.turn
                ? lean.to * self.ease((time - lean.down) / (lean.turn - lean.down))
                : lean.to * (1 - self.ease((time - lean.turn) / (lean.moveEnd - lean.turn)))
        }
        let undoTap = Demo.undo
        if t0 < undoTap.down && t1 >= undoTap.down {
            ghostX = TiltSwipeActionsFlat.width - TiltSwipeActionsLayout.gutter - TiltSwipeActionsLayout.fab - undoTap.fabGap - undoTap.xInset
            ghostY = TiltSwipeActionsFlat.height - TiltSwipeActionsLayout.pillBottom - TiltSwipeActionsLayout.pillHeight / 2
            showFinger(true)
            ghostPress.timing(to: 1, duration: undoTap.up - undoTap.down)
        }
        if t0 < undoTap.up && t1 >= undoTap.up {
            ghostPress.timing(to: 0, duration: undoTap.up - undoTap.down)
            showFinger(false)
            deferred.append { [weak self] in self?.undo() }
        }
        flushScript()
        if t1 >= TiltSwipeActionsMotion.loop {
            resetForLoopBoundary()
            time = t1 - TiltSwipeActionsMotion.loop
        } else {
            time = t1
        }
    }

    /// A real touch: cancel the scripted one and pause.
    func demoTouchStarted() {
        touching = true
        quiet = 0
        tidy = -1
        if paused { return }
        paused = true
        showFinger(false)
        if scriptedDown != "" {
            send(scriptedDown, down: false, tx: 0, vx: 0)
            scriptedDown = ""
            flushScript()
        }
    }

    func demoTouchEnded() {
        touching = false
        quiet = 0
    }
}
