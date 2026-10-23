import Foundation
import QuartzCore

extension DragMultiSelectEngine {
    /// What a finger on the list can still become. Three gestures race: a drag that starts on the
    /// circles (in selection), a long press that keeps dragging, and a tap.
    struct Session {
        enum Winner { case circles, press }
        var startX: Double
        var startY: Double
        var lastY: Double
        var downTime: Double
        var onCircles: Bool
        var undecided = true
        var winner: Winner?
    }

    /// How far a finger may wander before a long press or a tap gives up.
    private var holdSlop: Double { DragMultiSelectTokens.Component.holdSlop }

    // MARK: Fingers

    func touchBegan(x: Double, y: Double) {
        userTouchBegan()
        // Selection started on the circles column: a vertical drag there is a range drag at once.
        let onCircles = mode == 1 && x >= width - DragMultiSelectLayout.circleColumn
        if onCircles { lockScroll = true }
        session = Session(startX: x, startY: y, lastY: y, downTime: clock.now, onCircles: onCircles)
        anticipate(rowAt(y))
    }

    func touchMoved(x: Double, y: Double) {
        guard var current = session else { return }
        current.lastY = y
        if current.winner == nil && current.onCircles && abs(y - current.startY) > DragMultiSelectSelect.dragSlop {
            // The press and the tap give up; the range drag takes over.
            current.undecided = false
            current.winner = .circles
            session = current
            releasePress()
            coast = 0
            fingerY = current.startY
            dragBegin(rowAt(current.startY), forceTarget: -1)
        } else if current.undecided && hypot(x - current.startX, y - current.startY) > holdSlop {
            // The finger left the row: no long press, no tap.
            current.undecided = false
            session = current
            releasePress()
        } else {
            session = current
        }
        if session?.winner != nil {
            fingerY = y
            dragMove(silent: false)
        }
    }

    func touchEnded(x: Double, y: Double, cancelled: Bool) {
        guard let current = session else { return }
        session = nil
        switch current.winner {
        case .press, .circles:
            releasePress()
            dragEnd()
        case nil:
            releasePress()
            dragEnd()
            if current.undecided && !cancelled && mode == 1 {
                toggle(rowAt(y), silent: false)
            }
        }
        lockScroll = false
        userTouchEnded()
    }

    /// The long press is over (it won, lost or ended): the held row's band lets go.
    private func releasePress() {
        pressP.timing(to: 0, ms: DragMultiSelectTiming.press, easing: .out)
    }

    /// A finger that stays put for the long-press time turns into a range drag, from the row it holds.
    func checkLongPress(at timestamp: Double) {
        guard var current = session, current.undecided else { return }
        guard clock.now - current.downTime >= DragMultiSelectSelect.longPress / 1000 else { return }
        current.undecided = false
        current.winner = .press
        session = current
        pressP.set(0)
        coast = 0
        lockScroll = true
        let row = rowAt(current.lastY)
        fingerY = current.lastY
        if mode == 0 {
            enter(row, silent: false)
            dragBegin(row, forceTarget: 1)
        } else {
            dragBegin(row, forceTarget: -1)
        }
    }
}
