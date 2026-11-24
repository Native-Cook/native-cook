// Ink Wash Page Turn — the turn's state: the loop, the finger and the hand-over between them.
//
// The renderer calls `tick` once a frame on the main thread; the page's views only read the result,
// so nothing here is SwiftUI state.

import MetalKit
import QuartzCore
import UIKit

final class InkWashPageTurnReader {
    private typealias M = InkWashPageTurnMotion
    private enum Mode { case auto, touch, returning }

    /// a value eased from where it was (the ghost's and the hint's fades)
    private struct Fade {
        var from: Double
        var to: Double
        var start = 0.0
        var duration = 0.0
        func value(at now: Double) -> Double {
            guard duration > 0, now < start + duration else { return to }
            return from + (to - from) * InkWashPageTurnMotion.inOutQuad(max(0, (now - start) / duration))
        }
    }

    var width = 393.0
    var height = 852.0
    var auto = true
    var reduced = false
    weak var view: MTKView?
    /// frames are needed again, or no longer
    var onLive: ((Bool) -> Void)?

    // the turn
    private(set) var from = 0
    private(set) var to = 1
    private(set) var progress = 0.0
    private(set) var originX = 393.0 * InkWashPageTurnMotion.first.x
    private(set) var originY = 852.0 * InkWashPageTurnMotion.first.y
    // the ghost finger
    private(set) var ghostPX = 0.0
    private(set) var ghostPY = 0.0
    private(set) var ghostP = 0.0
    private var ghostFade = Fade(from: 0, to: 0)
    private var hintFade = Fade(from: 1, to: 1)

    private var lastWall: Double?
    private var clock = 0.0
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var down = false
    private var grab = false
    private var travel = 0.0
    private var lastTouch = 0.0
    private var running = false
    private var runAt = 0.0
    private var runFrom = 0.0
    private var runSpeed = 0.0
    private var runTarget = 1.0
    private var runTap = false
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    var ghostAlpha: Double { ghostFade.value(at: clock) }
    var hint: Double { hintFade.value(at: clock) }
    /// whether anything on screen can still change by itself
    var live: Bool { auto || down || running }

    /// how much page `index` is the one on show: the number and the dots change over the middle of a turn
    func weight(_ index: Int) -> Double {
        let e = M.smooth(M.seg(progress, 0.25, 0.75))
        return (index == from ? 1 - e : 0) + (index == to ? e : 0)
    }

    var pillAt: Double {
        Double(from) + Double(to - from) * M.smooth(M.seg(progress, 0.25, 0.75))
    }

    var pillStretch: Double {
        1 + 0.35 * sin(.pi * M.smooth(M.seg(progress, 0.25, 0.75)))
    }

    /// the farthest corner from the origin, pt
    var reach: Double {
        let fx = max(originX, width - originX)
        let fy = max(originY, height - originY)
        return (fx * fx + fy * fy).squareRoot()
    }

    // MARK: the frame

    func tick(_ wall: Double = CACurrentMediaTime()) {
        let dt = min(0.05, max(0, lastWall.map { wall - $0 } ?? (1.0 / 60)))
        lastWall = wall
        clock += dt
        let now = clock

        if mode == .auto {
            guard auto else {
                autoStart = now
                return
            }
            let t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
            let k = M.turnAt(t)
            from = k
            to = (k + 1) % M.pages
            switch k {
            case 0:
                originX = width * M.first.x
                originY = height * M.first.y
                progress = M.swipeAt(t, M.first.down, M.first.up)
            case 1:
                originX = width * M.second.x
                originY = height * M.second.y
                progress = M.swipeAt(t, M.second.down, M.second.up)
            default:
                originX = width * M.tap.x
                originY = height * M.tap.y
                progress = M.tapAt(t)
            }
            ghostPX = M.ghostX(t) * width
            ghostPY = M.ghostY(t) * height
            let alpha = M.ghostAlpha(t)
            ghostFade = Fade(from: alpha, to: alpha)
            ghostP = M.ghostPress(t)
            return
        }

        if running {
            let tau = now - runAt
            let blend = runTap ? M.tapBlend : M.settleBlend
            progress = M.settle(runFrom, runSpeed, runTarget, tau, runTap ? M.tapOmega : M.omega, blend.from, blend.to)
            if tau >= blend.to {
                running = false
                if runTarget == 1 {
                    // the turn is done: the page that arrived is the page on show
                    from = to
                    to = (to + 1) % M.pages
                }
                progress = 0
                if mode == .returning {
                    autoStart = now
                    mode = .auto
                    hintFade = Fade(from: hint, to: 1, start: now, duration: 0.4)
                }
            }
            return
        }

        if mode == .touch, auto, !down, now - lastTouch >= M.idle {
            if from == 0 {
                // already on page 1: the loop picks up from its rest
                autoStart = now
                mode = .auto
                hintFade = Fade(from: hint, to: 1, start: now, duration: 0.4)
            } else {
                mode = .returning
                to = 0
                originX = width * M.tap.x
                originY = height * M.tap.y
                run(from: 0, speed: 0, target: 1, tap: true, at: now)
            }
        }
    }

    private func run(from x: Double, speed: Double, target: Double, tap: Bool, at now: Double) {
        runFrom = x
        runSpeed = speed
        runTarget = target
        runTap = tap
        runAt = now
        running = true
    }

    private func wake() {
        // the clock must not count the time the view was paused
        if view?.isPaused == true { lastWall = CACurrentMediaTime() }
        guard !reduced else { return }
        view?.isPaused = false
        onLive?(true)
    }

    // MARK: the finger

    func touchBegan(x: Double, y: Double) {
        wake()
        let wasAuto = mode == .auto
        mode = .touch
        down = true
        grab = false
        travel = 0
        lastTouch = clock
        ghostFade = Fade(from: ghostAlpha, to: 0, start: clock, duration: 0.15)
        hintFade = Fade(from: hint, to: 0, start: clock, duration: 0.25)
        if running { return }
        if wasAuto, progress > 0, progress < 1 {
            // the loop was mid-turn: let that turn finish by itself
            run(from: progress, speed: 0, target: 1, tap: false, at: clock)
            return
        }
        if progress >= 1 {
            from = to
            progress = 0
        }
        grab = true
        originX = x
        originY = y
        haptic.impactOccurred()
    }

    func touchMoved(dx: Double, dy: Double) {
        lastTouch = clock
        guard grab else { return }
        travel = max(travel, abs(dx), abs(dy))
        // left for the next page, right for the one before; crossing back over the start swaps them
        to = (from + (dx <= 0 ? 1 : M.pages - 1)) % M.pages
        if reduced { return }
        progress = M.scrub(abs(dx) / (M.scrubLength * width))
    }

    func touchEnded(x: Double, dx: Double, velocityX: Double) {
        defer {
            down = false
            lastTouch = clock
        }
        guard grab else { return }
        grab = false
        let tapped = travel < M.tapTravel
        if tapped { to = (from + (x < width * M.backZone ? M.pages - 1 : 1)) % M.pages }
        let speed = ((dx <= 0 ? -velocityX : velocityX) / (M.scrubLength * width)) * (tapped ? 0 : 1)
        let commit = tapped || progress + M.project * speed > M.commit || (reduced && abs(dx) > 40)
        if reduced {
            if commit {
                from = to
                to = (from + 1) % M.pages
                haptic.impactOccurred()
            }
            progress = 0
            return
        }
        run(from: tapped ? 0 : progress, speed: max(-4, min(4, speed)), target: commit ? 1 : 0, tap: tapped, at: clock)
        if commit, !tapped { haptic.impactOccurred() }
    }

    /// the system took the touch away mid-drag: draw back
    func touchCancelled() {
        if grab {
            grab = false
            if progress > 0 { run(from: progress, speed: 0, target: 0, tap: false, at: clock) }
        }
        down = false
        lastTouch = clock
    }
}
