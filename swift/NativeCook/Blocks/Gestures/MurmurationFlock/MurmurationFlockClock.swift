// Murmuration Flock — the film's clock: the loop, the finger and the hand-over between them.
//
// The Metal view calls `tick` once a frame on the main thread and hands what it returns to the GPU;
// the screen's labels only read the result, so nothing here is SwiftUI state.

import QuartzCore
import UIKit

final class MurmurationFlockClock {
    private typealias M = MurmurationFlockMotion
    private enum Mode { case auto, touch, returning }

    /// a value eased from where it was (the ghost's and the hint's fades)
    private struct Fade {
        var from: Double
        var to: Double
        var start = 0.0
        var duration = 0.0
        func value(at now: Double) -> Double {
            guard duration > 0, now < start + duration else { return to }
            // Reanimated's withTiming: ease in and out, quadratic
            let u = max(0, (now - start) / duration)
            let e = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
            return from + (to - from) * e
        }
    }

    /// what the compute pass needs for one frame
    struct Frame {
        var t: Double
        var dt: Double
        var clear: Bool
        var hold: Double
        var touchX: Double
        var touchY: Double
        var touchOn: Double
    }

    var width = 393.0
    var height = 852.0
    var auto = true
    var reduced = false

    /// the film's time, seconds into the loop
    private(set) var film = 0.0
    private(set) var ghostX = 0.0
    private(set) var ghostY = 0.0
    private var ghostFade = Fade(from: 0, to: 0)
    private var hintFade = Fade(from: 1, to: 1)

    private var lastWall: Double?
    private var clock = 0.0
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var prevFilm = 0.0
    private var epoch = 0
    private var shownEpoch = -1
    private var down = false
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var returnFrom = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var touchOn = 0.0
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    var ghostAlpha: Double { ghostFade.value(at: clock) }
    var hint: Double { hintFade.value(at: clock) }

    // MARK: the frame

    func tick(_ wall: Double = CACurrentMediaTime()) -> Frame {
        let dt = min(0.05, max(0, lastWall.map { wall - $0 } ?? (1.0 / 60)))
        lastWall = wall
        if reduced {
            // one still frame: the circle
            film = M.still
            ghostFade = Fade(from: 0, to: 0)
            return Frame(t: M.still, dt: 0, clear: true, hold: 1, touchX: 0, touchY: 0, touchOn: 0)
        }
        clock += dt
        let now = clock
        var t = film
        var hold = 1.0
        var aim = 0.0
        var tx = fingerX
        var ty = fingerY

        switch mode {
        case .auto:
            if !auto {
                autoStart = now
                t = 0
                ghostFade = Fade(from: 0, to: 0)
            } else {
                t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
                if t < prevFilm { epoch += 1 }
                let g = M.ghostAt(t)
                tx = g.x * width
                ty = g.y * height
                aim = g.alpha
                ghostX = tx
                ghostY = ty
                ghostFade = Fade(from: g.alpha, to: g.alpha)
                hold = 1 - M.smooth(M.seg(t, M.settle.0, M.settle.1))
            }
        case .touch:
            t = (t + dt * M.filmRate).truncatingRemainder(dividingBy: M.loop)
            aim = down ? 1 : 0
            if auto, !down, now - lastTouch >= M.idle {
                returnFrom = t
                returnAt = now
                mode = .returning
            }
        case .returning:
            let u = M.seg(now - returnAt, 0, M.returnTime)
            let to = returnFrom > M.loop / 2 ? M.loop : 0
            t = returnFrom + (to - returnFrom) * M.smooth(u)
            hold = 1 - M.smooth(u)
            if u >= 1 {
                t = 0
                autoStart = now
                epoch += 1
                mode = .auto
                hintFade = Fade(from: hint, to: 1, start: now, duration: 0.4)
            }
        }

        prevFilm = t
        film = t
        touchOn += (aim - touchOn) * (1 - exp(-dt * M.touchEase))
        let clear = epoch != shownEpoch
        shownEpoch = epoch
        return Frame(t: t, dt: dt, clear: clear, hold: hold, touchX: tx, touchY: ty, touchOn: touchOn)
    }

    // MARK: touch

    func touchBegan(x: Double, y: Double) {
        guard !reduced else { return }
        mode = .touch
        down = true
        lastTouch = clock
        fingerX = x
        fingerY = y
        ghostFade = Fade(from: ghostAlpha, to: 0, start: clock, duration: 0.15)
        hintFade = Fade(from: hint, to: 0, start: clock, duration: 0.25)
        haptic.impactOccurred()
    }

    func touchMoved(x: Double, y: Double) {
        guard down else { return }
        fingerX = x
        fingerY = y
        lastTouch = clock
    }

    func touchEnded() {
        down = false
        lastTouch = clock
    }
}
