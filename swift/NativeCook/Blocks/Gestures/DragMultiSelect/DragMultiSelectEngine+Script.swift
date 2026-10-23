import Foundation

extension DragMultiSelectEngine {
    /// 0 to 1, a cosine ease.
    private func ease(_ u: Double) -> Double {
        let k = min(max(u, 0), 1)
        return (1 - cos(Double.pi * k)) / 2
    }

    private func show(_ on: Bool) {
        ghostO.timing(to: on ? 1 : 0, ms: Script.fingerFade * 1000)
    }

    private func setGhost(x: Double? = nil, y: Double? = nil) {
        if let x, state.ghostX != x { state.ghostX = x }
        if let y, state.ghostY != y { state.ghostY = y }
    }

    private func rowY(_ row: Int) -> Double {
        listTop + DragMultiSelectLayout.row * (Double(row) + 0.5) - scrollY
    }

    /// The demo's finger touches the screen, or a real one does: the demo stops where it is.
    func userTouchBegan() {
        clock.sync()
        wakeDisplayLink()
        touching = true
        lastTouchEndedAt = nil
        tidyStartedAt = nil
        tidy = -1
        // Interruption is immediate, including momentum left by an autoplay edge drag.
        coast = 0
        if paused { return }
        paused = true
        show(false)
        if scriptDown {
            scriptDown = false
            dragEnd()
        }
        coast = 0
    }

    func userTouchEnded() {
        clock.sync()
        touching = false
        lastTouchEndedAt = clock.now
    }

    /// One frame of the autoplay script. Every event is crossed against the absolute display-link
    /// phase, so refresh rate and dropped frames cannot stretch the 13 s period.
    func playScript(at timestamp: Double) {
        let layout = DragMultiSelectLayout.self
        if paused {
            guard !touching, let lastTouchEndedAt else { return }
            if tidyStartedAt == nil,
               timestamp - lastTouchEndedAt >= Script.userResumeSeconds,
               !dragging {
                tidy = 0
                tidyStartedAt = timestamp
                tidyFrom = scrollY
                exit(silent: true)
            }
            if let tidyStartedAt {
                tidy = max(timestamp - tidyStartedAt, 0)
                let y = tidyFrom * (1 - ease(tidy / Script.tidy))
                scrollY = y
                scrollTo(y)
                if tidy >= Script.tidy {
                    resetScriptToRest()
                    scriptEpoch = timestamp
                    scriptCycle = 0
                    paused = false
                }
            }
            return
        }

        if scriptEpoch == nil {
            scriptEpoch = timestamp
            scriptCycle = 0
            t = 0
            return
        }
        let loop = Script.loopMS / 1000
        let elapsed = max(timestamp - (scriptEpoch ?? timestamp), 0)
        let cycle = Int((elapsed / loop).rounded(.down))
        let phase = elapsed - Double(cycle) * loop
        var t0 = t
        if cycle != scriptCycle {
            // The source has restored every row by 11.8 s and is motionless before 13 s. Force the
            // same canonical state at the boundary so phase 0 and phase 13 are byte-for-byte equal.
            resetScriptToRest()
            scriptCycle = cycle
            t0 = 0
        }
        let t1 = phase
        func cross(_ at: Double) -> Bool { t0 < at && t1 >= at }
        let longPress = DragMultiSelectSelect.longPress / 1000
        let midX = width * 0.42
        let circleX = width - layout.circleRight - layout.bandInset - layout.circle / 2

        // 1. Long press, then drag the range down, back up, and down again.
        let hold = Script.hold
        if cross(hold.down) {
            setGhost(x: midX, y: rowY(hold.row))
            show(true)
            ghostPress.timing(to: 1, ms: DragMultiSelectSelect.longPress)
            anticipate(hold.row)
        }
        if cross(hold.down + longPress) {
            pressP.set(0)
            enter(hold.row, silent: true)
            dragBegin(hold.row, forceTarget: 1)
            scriptDown = true
            fingerY = rowY(hold.row)
        }
        let drag = Script.drag
        if scriptDown && t1 >= drag[0].at && t1 < Script.release {
            var y = rowY(drag[drag.count - 1].row)
            for index in 0..<(drag.count - 1) where t1 >= drag[index].at && t1 < drag[index + 1].at {
                let u = ease((t1 - drag[index].at) / (drag[index + 1].at - drag[index].at))
                y = rowY(drag[index].row) + (rowY(drag[index + 1].row) - rowY(drag[index].row)) * u
            }
            fingerY = y
            setGhost(y: y)
            dragMove(silent: true)
        }
        if cross(Script.release) {
            scriptDown = false
            dragEnd()
            ghostPress.timing(to: 0, ms: DragMultiSelectTiming.press)
            show(false)
        }

        // 2. Tap one more row's circle: a second block.
        let tap = Script.tap
        if cross(tap.at - 0.25) {
            setGhost(x: circleX, y: rowY(tap.row))
            show(true)
        }
        if cross(tap.at) {
            toggle(tap.row, silent: true)
            show(false)
        }

        // 3. Drag the circles into the toolbar edge and hold: the list scrolls under the finger.
        let edge = Script.edge
        let edgeY = viewport - bottomInset - layout.toolbarHeight - layout.toolbarBottom - 8 - layout.edge * 0.3
        if cross(edge.down) {
            setGhost(x: circleX, y: rowY(edge.row))
            show(true)
            fingerY = rowY(edge.row)
            dragBegin(rowAt(rowY(edge.row)), forceTarget: -1)
            scriptDown = true
        }
        if scriptDown && t1 >= edge.down && t1 < edge.up {
            let start = listTop + layout.row * (Double(edge.row) + 0.5)
            let y = start + (edgeY - start) * ease((t1 - edge.down) / (edge.reach - edge.down))
            fingerY = y
            setGhost(y: y)
            dragMove(silent: true)
        }
        if cross(edge.up) {
            scriptDown = false
            dragEnd()
            show(false)
        }

        // 4. Done, and the list goes home.
        if cross(Script.doneAt - 0.3) {
            setGhost(x: width - layout.gutter - layout.doneWidth / 2, y: listTop + layout.row / 2)
            show(true)
        }
        if cross(Script.doneAt) {
            exit(silent: true)
            show(false)
            tidyFrom = scrollY
        }
        let home = Script.scrollHome
        let homeSeconds = DragMultiSelectTiming.scrollHome / 1000
        if t1 >= home && t1 < home + homeSeconds {
            coast = 0
            let y = tidyFrom * (1 - ease((t1 - home) / homeSeconds))
            scrollY = y
            scrollTo(y)
        }
        if cross(home + homeSeconds) {
            coast = 0
            scrollY = 0
            scrollTo(0)
        }

        // 5. Select two rows and delete them: they stream out to the trash (the payoff); later they come back.
        let remove = Script.remove
        if cross(remove.down) {
            setGhost(x: midX, y: rowY(remove.from))
            show(true)
            ghostPress.timing(to: 1, ms: DragMultiSelectSelect.longPress)
            anticipate(remove.from)
        }
        if cross(remove.down + longPress) {
            pressP.set(0)
            enter(remove.from, silent: true)
            dragBegin(remove.from, forceTarget: 1)
            scriptDown = true
            fingerY = rowY(remove.from)
        }
        if scriptDown && t1 >= remove.down + longPress && t1 < remove.up {
            let s0 = remove.down + longPress
            let y = rowY(remove.from) + (rowY(remove.to) - rowY(remove.from)) * ease((t1 - s0) / (remove.up - 0.1 - s0))
            fingerY = y
            setGhost(y: y)
            dragMove(silent: true)
        }
        if cross(remove.up) {
            scriptDown = false
            dragEnd()
            ghostPress.timing(to: 0, ms: DragMultiSelectTiming.press)
            show(false)
        }
        if cross(remove.trash - 0.3) {
            let trashX = width / 2 + layout.toolbarWidth / 2 - 8 - 44 - 22
            let trashY = windowHeight - bottomInset - layout.toolbarBottom - layout.toolbarHeight / 2
            setGhost(x: trashX, y: trashY)
            show(true)
        }
        if cross(remove.trash) {
            show(false)
            kickTrash()
            deleteSelected()
        }
        if cross(remove.restore) { restoreAll() }
        t = phase
    }

    /// The one canonical resting state used at launch, at the loop seam, after Reduce Motion is
    /// enabled, and after a real finger's 2 s interruption timeout.
    func resetScriptToRest() {
        resetRowsToRest()
        mode = 0
        sel = Array(repeating: 0, count: live.count)
        state.isSelecting = false
        state.selectedCount = 0
        state.pressRow = -1
        state.breath = 0
        pressP.set(0)
        shown.set(0)
        navFade.set(0)
        ghostO.set(0)
        ghostPress.set(0)
        trashKick.set(0)
        dragging = false
        lockScroll = false
        lastSpeed = 0
        coast = 0
        scrollY = 0
        scrollTo(0)
        fingerY = 0
        base = []
        current = -1
        scriptDown = false
        tidy = -1
        tidyFrom = 0
        tidyStartedAt = nil
        lastTouchEndedAt = nil
        t = 0
    }
}
