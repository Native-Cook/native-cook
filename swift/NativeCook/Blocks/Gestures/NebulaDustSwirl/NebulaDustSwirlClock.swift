// Nebula Dust Swirl — the clock: the film, the finger and the hand-over between them.
//
// A port of the React Native ticker. Once a frame the Metal renderer asks for a `Frame` (film time,
// step, whether to forget the stir, how much of it shows, the finger and its push); the page's views
// read the film time it left behind. It lives on the main thread and is not SwiftUI state.

import QuartzCore
import UIKit

final class NebulaDustSwirlClock {
    private typealias M = NebulaDustSwirlMotion
    private enum Mode { case auto, touch, returning }

    struct Frame {
        var t = 0.0
        var dt = 0.0
        /// a change forgets the stir
        var epoch = 0
        var hold = 1.0
        var touchX = 0.0
        var touchY = 0.0
        var touchOn = 0.0
        var pushX = 0.0
        var pushY = 0.0
    }

    var auto = true
    var centreX = 196.5
    var centreY = 340.8
    var unit = 196.5

    // what the views read
    private(set) var film = M.still
    private(set) var ghostX = 0.0
    private(set) var ghostY = 0.0

    private var lastWall: Double?
    private var clock = 0.0
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var prevFilm = 0.0
    private var epoch = 0
    private var down = false
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var returnFrom = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var fingerVX = 0.0
    private var fingerVY = 0.0
    private var touchOn = 0.0
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    // fades for the ghost and the hint (Reanimated's withTiming)
    private var ghostFade = Fade(value: 0)
    private var hintFade = Fade(value: 1)
    private var scriptedGhostA = 0.0

    var ghostA: Double { mode == .auto ? scriptedGhostA : ghostFade.at(clock) }
    var hint: Double { hintFade.at(clock) }

    private struct Fade {
        var from = 0.0
        var to = 0.0
        var start = 0.0
        var duration = 0.0
        init(value: Double) { from = value; to = value }
        func at(_ now: Double) -> Double {
            guard duration > 0, now < start + duration else { return to }
            let u = max(0, (now - start) / duration)
            // Reanimated's default timing curve, ease in-out (cubic bezier 0.42, 0, 0.58, 1) ≈ smoothstep
            return from + (to - from) * u * u * (3 - 2 * u)
        }
        mutating func go(_ value: Double, over d: Double, now: Double) {
            from = at(now)
            to = value
            start = now
            duration = d
        }
    }

    func showStill() {
        film = M.still
        scriptedGhostA = 0
    }

    func tick(_ wall: Double = CACurrentMediaTime()) -> Frame {
        let dt = min(0.05, max(0, lastWall.map { wall - $0 } ?? (1.0 / 60)))
        lastWall = wall
        clock += dt
        let now = clock
        var t = film
        var hold = 1.0
        var aim = 0.0
        var tx = fingerX
        var ty = fingerY
        var vx = fingerVX
        var vy = fingerVY

        switch mode {
        case .auto:
            if !auto {
                autoStart = now
                t = 0
                scriptedGhostA = 0
                vx = 0
                vy = 0
            } else {
                t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
                if t < prevFilm { epoch += 1 }
                let g = M.ghostAt(t)
                tx = centreX + g.x * unit
                ty = centreY + g.y * unit
                // the ghost's speed, so it drags the dust like a finger would
                let cap = M.maxSpeed
                vx = max(-cap, min(cap, (tx - ghostX) / max(dt, 0.001)))
                vy = max(-cap, min(cap, (ty - ghostY) / max(dt, 0.001)))
                if g.alpha < 0.001 {
                    vx = 0
                    vy = 0
                }
                aim = g.alpha
                ghostX = tx
                ghostY = ty
                scriptedGhostA = g.alpha
                hold = M.holdAt(t)
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
            let from = returnFrom
            let to = from > M.loop / 2 ? M.loop : 0
            t = from + (to - from) * M.smooth(u)
            hold = 1 - M.smooth(u)
            vx = 0
            vy = 0
            if u >= 1 {
                t = 0
                autoStart = now
                epoch += 1
                mode = .auto
                hintFade.go(1, over: 0.4, now: now)
            }
        }

        prevFilm = t
        film = t
        touchOn += (aim - touchOn) * (1 - exp(-dt * M.touchEase))
        return Frame(t: t, dt: dt, epoch: epoch, hold: hold, touchX: tx, touchY: ty, touchOn: touchOn, pushX: vx, pushY: vy)
    }

    // MARK: - the finger

    func began(x: Double, y: Double) {
        let wasAuto = mode == .auto
        mode = .touch
        down = true
        lastTouch = clock
        fingerX = x
        fingerY = y
        fingerVX = 0
        fingerVY = 0
        if wasAuto { ghostFade = Fade(value: scriptedGhostA) }
        ghostFade.go(0, over: 0.15, now: clock)
        hintFade.go(0, over: 0.25, now: clock)
        haptic.impactOccurred()
    }

    func moved(x: Double, y: Double, vx: Double, vy: Double) {
        let cap = M.maxSpeed
        fingerX = x
        fingerY = y
        fingerVX = max(-cap, min(cap, vx))
        fingerVY = max(-cap, min(cap, vy))
        lastTouch = clock
    }

    func ended() {
        down = false
        fingerVX = 0
        fingerVY = 0
        lastTouch = clock
    }
}
