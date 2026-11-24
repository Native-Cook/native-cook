// Ripple Pond Photo — the clock that turns time into simulation steps and drops.
//
// Once a frame the renderer asks for a `Frame`: how many fixed steps to run, the damping and up to
// four drops (two taps pressing in, the wake's midpoint and head). The script, the finger and the
// hand-over between them all live here, on the main thread; nothing is SwiftUI state.

import QuartzCore
import simd

final class RipplePondPhotoPond {
    private typealias M = RipplePondPhotoMotion

    /// what one frame does to the water
    struct Frame {
        /// a change clears the water
        var epoch = 0
        var steps = 0
        var damp = Float(RipplePondPhotoMotion.damp)
        /// x, y (0…1), radius (cells), depth
        var drops = [SIMD4<Float>](repeating: .zero, count: 4)
    }

    struct Ghost {
        var x = 0.0
        var y = 0.0
        var alpha = 0.0
        var press = 0.0
    }

    private enum Mode { case auto, touch, returning }
    /// a value eased from where it was (the ghost's and the hint's fades)
    private struct Fade {
        var from: Double
        var to: Double
        var start = 0.0
        var duration = 0.0
        func value(at now: Double) -> Double {
            guard duration > 0, now < start + duration else { return to }
            return from + (to - from) * RipplePondPhotoMotion.inOutQuad(max(0, (now - start) / duration))
        }
    }

    /// the screen, pt
    var width = 393.0
    var height = 852.0
    var auto = true

    private var base: Double?
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var prevT = -1.0
    private var stepsDone = -1
    private var epoch = 0
    private var returnAt = 0.0
    private var lastTouch = 0.0
    private var down = false
    /// the pointer that cuts the wake (the finger, or the ghost during the scripted drag), pt
    private var penX = 0.0
    private var penY = 0.0
    private var wakeX = -1.0
    private var wakeY = -1.0
    /// taps that can be pressing into the water at once: the step each began on (or -1), and where
    private var taps: [(born: Int, u: Double, v: Double)] = [(-1, 0, 0), (-1, 0, 0)]
    private var ghostFade = Fade(from: 0, to: 0)
    private var hintFade = Fade(from: 1, to: 1)
    /// where the ghost finger was when a real one took over
    private var ghostHeld = Ghost()

    private func clock(_ wall: Double) -> Double {
        if base == nil { base = wall }
        return wall - (base ?? wall)
    }

    private func loopTime(_ now: Double) -> Double {
        (now - autoStart).truncatingRemainder(dividingBy: M.loop)
    }

    /// start a tap at (u, v) on step `at`, in a free slot or over the oldest
    private func addTap(at step: Int, _ u: Double, _ v: Double) {
        let slot = taps[0].born <= taps[1].born ? 0 : 1
        taps[slot] = (step, u, v)
    }

    private func still() {
        epoch += 1
        taps = [(-1, 0, 0), (-1, 0, 0)]
        wakeX = -1
    }

    // MARK: the frame

    func tick(_ wall: Double = CACurrentMediaTime()) -> Frame {
        let now = clock(wall)

        // fixed steps, however long the frame was
        let target = Int((now * M.stepHz).rounded(.down))
        if stepsDone < 0 || Double(target - stepsDone) > M.stepHz / 4 { stepsDone = target - 1 }
        let a = stepsDone
        let steps = min(M.maxSteps, target - a)
        let b = a + steps
        stepsDone = b

        var damp = M.damp
        var wake = false
        switch mode {
        case .auto:
            if !auto {
                autoStart = now
                prevT = -1
            } else {
                let t = loopTime(now)
                if t < prevT {
                    // the cut: the water is flat here, so clearing it changes nothing on screen
                    still()
                }
                for tap in M.taps where prevT < tap.at && t >= tap.at {
                    addTap(at: a, tap.x, tap.y)
                }
                if t >= M.drag.from, t < M.drag.to {
                    let g = M.ghostAt(t)
                    penX = g.x * width
                    penY = g.y * height
                    wake = true
                } else {
                    wakeX = -1
                }
                damp = M.dampAt(t)
                prevT = t
            }
        case .touch:
            wake = down
            if !down { wakeX = -1 }
            if auto, !down, now - lastTouch >= M.idle {
                mode = .returning
                returnAt = now
            }
        case .returning:
            let since = now - returnAt
            damp = M.damp + (M.calmDamp - M.damp) * M.smooth(M.seg(since, 0, M.returnEase))
            if since >= M.returnTotal {
                still()
                autoStart = now
                prevT = -1
                mode = .auto
                hintFade = Fade(from: hintFade.value(at: now), to: 1, start: now, duration: 0.4)
            }
        }

        // the frame's drops are two tap slots, then the wake's midpoint and head
        var frame = Frame(epoch: epoch, steps: steps, damp: Float(damp))
        for i in taps.indices {
            let tap = taps[i]
            var depth = 0.0
            if tap.born >= 0, steps > 0 {
                let span = Double(M.tapSteps)
                depth = M.tapDepth * (M.tapShare(Double(b - tap.born) / span) - M.tapShare(Double(a - tap.born) / span))
                if b - tap.born >= M.tapSteps { taps[i].born = -1 }
            }
            frame.drops[i] = SIMD4(Float(tap.u), Float(tap.v), Float(M.tapRadius), Float(depth))
        }
        var cut = 0.0
        var fromX = penX
        var fromY = penY
        if wake, steps > 0 {
            if wakeX >= 0 {
                fromX = wakeX
                fromY = wakeY
                let travel = ((penX - fromX) * (penX - fromX) + (penY - fromY) * (penY - fromY)).squareRoot()
                // a faster finger cuts deeper
                if travel > 0.5 { cut = max(M.wakeMax, M.wakePerPt * travel) }
            }
            if cut != 0 || wakeX < 0 {
                wakeX = penX
                wakeY = penY
            }
        }
        frame.drops[2] = SIMD4(Float((fromX + penX) / 2 / width), Float((fromY + penY) / 2 / height), Float(M.wakeRadius), Float(cut / 2))
        frame.drops[3] = SIMD4(Float(penX / width), Float(penY / height), Float(M.wakeRadius), Float(cut / 2))
        return frame
    }

    // MARK: what the page draws over the water

    func ghost(_ wall: Double = CACurrentMediaTime()) -> Ghost {
        let now = clock(wall)
        guard mode == .auto else { return Ghost(x: ghostHeld.x, y: ghostHeld.y, alpha: ghostFade.value(at: now), press: ghostHeld.press) }
        guard auto else { return Ghost() }
        let t = loopTime(now)
        let at = M.ghostAt(t)
        return Ghost(x: at.x * width, y: at.y * height, alpha: M.ghostAlpha(t), press: M.ghostPress(t))
    }

    func hint(_ wall: Double = CACurrentMediaTime()) -> Double {
        hintFade.value(at: clock(wall))
    }

    // MARK: the finger

    func touchBegan(x: Double, y: Double) {
        let now = clock(CACurrentMediaTime())
        if mode == .auto {
            let was = ghost()
            ghostHeld = was
            ghostFade = Fade(from: was.alpha, to: 0, start: now, duration: 0.15)
        }
        hintFade = Fade(from: hintFade.value(at: now), to: 0, start: now, duration: 0.25)
        mode = .touch
        down = true
        lastTouch = now
        penX = x
        penY = y
        wakeX = -1
        addTap(at: max(0, stepsDone), x / width, y / height)
    }

    func touchMoved(x: Double, y: Double) {
        penX = x
        penY = y
        lastTouch = clock(CACurrentMediaTime())
    }

    func touchEnded() {
        down = false
        lastTouch = clock(CACurrentMediaTime())
    }
}
