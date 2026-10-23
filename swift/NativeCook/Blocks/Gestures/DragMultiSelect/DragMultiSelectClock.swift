import Foundation
import QuartzCore

/// The frame clock every animated number reads. The engine sets `now` on each display-link frame
/// and just before it handles a touch, and `step()` advances whatever is moving.
@MainActor
final class DragMultiSelectClock {
    /// Seconds, on the `CACurrentMediaTime` timeline.
    var now = 0.0
    private var running: [DragMultiSelectNumber] = []
    /// Where a touch reads the time from, between two display-link frames.
    var source: () -> Double = { CACurrentMediaTime() }
    var onEnqueue: (() -> Void)?

    var hasRunningAnimations: Bool { !running.isEmpty }

    /// Brings `now` up to the moment of a touch.
    func sync() {
        now = source()
    }

    func enqueue(_ number: DragMultiSelectNumber) {
        guard !number.isQueued else { return }
        number.isQueued = true
        running.append(number)
        onEnqueue?()
    }

    func step() {
        guard !running.isEmpty else { return }
        let moving = running
        for number in moving { number.advance(to: now) }
        running.removeAll { number in
            let done = !number.isRunning
            if done { number.isQueued = false }
            return done
        }
    }
}
