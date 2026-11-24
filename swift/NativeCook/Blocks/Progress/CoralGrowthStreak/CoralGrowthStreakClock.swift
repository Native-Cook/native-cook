// Coral Growth Streak — the clock that turns time into simulation steps and seeds.
//
// Once a frame the renderer asks for a `Frame`: how many fixed steps to run, the open radius, the
// look and up to eight seeds. The script, the finger and the hand-over between them live here, on
// the main thread; the page's views only read the result, so nothing here is SwiftUI state.

import QuartzCore
import simd

final class CoralGrowthStreakClock {
    private typealias M = CoralGrowthStreakMotion
    private enum Mode { case auto, touch, returning }

    struct Frame {
        /// a change clears the reef
        var epoch = 0
        var steps = 0
        /// open radius, cells
        var radius = 0.0
        var level = 1.0
        var bloom = 0.0
        var phase = 0.0
        /// x, y (0…1), strength (0 = unused), -
        var seeds = [SIMD4<Float>](repeating: .zero, count: CoralGrowthStreakMotion.seedSlots)
    }

    /// the reef window's diameter, pt
    var reef = 307.0
    var auto = true

    // what the views read
    private(set) var time = 0.0
    private(set) var level = 1.0
    private(set) var bloom = 0.0

    private var lastWall: Double?
    private var clock = 0.0
    private var phase = 0.0
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var prevT = -1.0
    private var stepsDone = 0
    private var nextDay = 0
    private var epoch = 0
    private var returnAt = 0.0
    private var lastTouch = 0.0
    private var down = false
    private var penX = 0.0
    private var penY = 0.0
    private var plantX = -100.0
    private var plantY = -100.0
    private var hintFrom = 1.0
    private var hintTo = 1.0
    private var hintStart = 0.0
    private var hintDuration = 0.0

    var hint: Double {
        guard hintDuration > 0, clock < hintStart + hintDuration else { return hintTo }
        return hintFrom + (hintTo - hintFrom) * M.inOutQuad(max(0, (clock - hintStart) / hintDuration))
    }

    private func fadeHint(to value: Double, duration: Double) {
        hintFrom = hint
        hintTo = value
        hintStart = clock
        hintDuration = duration
    }

    func showStill() {
        time = M.still
        level = 1
        bloom = 0
    }

    func tick(_ wall: Double = CACurrentMediaTime()) -> Frame {
        let dt = min(0.05, max(0, lastWall.map { wall - $0 } ?? (1.0 / 60)))
        lastWall = wall
        let before = clock
        clock += dt
        let now = clock
        phase = (now / M.loop).truncatingRemainder(dividingBy: 1)

        var steps = 0
        var open = M.ringFrac(M.days)
        var seedDay = -1
        var plant = false
        if mode == .auto {
            if !auto {
                autoStart = now
                prevT = -1
                time = 0
                level = 1
                bloom = 0
                open = 0
            } else {
                let t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
                if t < prevT {
                    // the cut: the view shows the bare sea bed here whatever the field holds
                    epoch += 1
                    stepsDone = 0
                    nextDay = 0
                }
                // fixed steps, however long the frame was, counted from the cut
                let done = stepsDone
                steps = max(0, min(M.maxSteps, Int(floor(t * M.stepHz)) - done))
                if nextDay < M.days, steps > 0 {
                    let at = M.dayStep[nextDay]
                    if done >= at {
                        // the day's seeds go in with this frame's first step, which is step `at` exactly
                        seedDay = nextDay
                        nextDay += 1
                    } else if done + steps > at {
                        steps = at - done
                    }
                }
                stepsDone = done + steps
                open = M.ringFrac(nextDay)
                time = t
                level = M.levelAt(t)
                bloom = M.bloomAt(t)
                prevT = t
            }
        } else {
            steps = min(M.maxSteps, Int(floor(now * M.stepHz)) - Int(floor(before * M.stepHz)))
            stepsDone += steps
            if mode == .touch {
                level = min(1, level + dt * 4)
                bloom = 0
                if down, steps > 0 {
                    let dx = penX - plantX
                    let dy = penY - plantY
                    // one seed where the finger lands, then one every 5 pt of travel
                    if dx * dx + dy * dy > 25 {
                        plant = true
                        plantX = penX
                        plantY = penY
                    }
                }
                if auto, !down, now - lastTouch >= M.idle {
                    mode = .returning
                    returnAt = now
                }
            } else {
                let since = now - returnAt
                level = 1 - M.smooth(M.seg(since, 0, M.returnEase))
                if since >= M.returnTotal {
                    epoch += 1
                    stepsDone = 0
                    nextDay = 0
                    autoStart = now
                    prevT = -1
                    time = 0
                    level = 1
                    mode = .auto
                    fadeHint(to: 1, duration: 0.4)
                    steps = 0
                    open = 0
                }
            }
        }

        var frame = Frame(epoch: epoch, steps: steps, radius: open * Double(M.grid) / 2, level: level, bloom: bloom, phase: phase)
        if seedDay >= 0 {
            let seeds = M.seeds[seedDay]
            for i in 0..<M.seedSlots where i * 2 < seeds.count {
                frame.seeds[i] = SIMD4(Float(seeds[i * 2]), Float(seeds[i * 2 + 1]), 1, 0)
            }
        } else if plant {
            frame.seeds[0] = SIMD4(Float(penX / reef), Float(penY / reef), 1, 0)
        }
        return frame
    }

    // MARK: the finger

    func touchBegan(x: Double, y: Double) {
        mode = .touch
        down = true
        lastTouch = clock
        fadeHint(to: 0, duration: 0.25)
        penX = x
        penY = y
        plantX = -100
        plantY = -100
    }

    func touchMoved(x: Double, y: Double) {
        guard down else { return }
        penX = x
        penY = y
        lastTouch = clock
    }

    func touchEnded() {
        guard down else { return }
        down = false
        lastTouch = clock
    }
}
