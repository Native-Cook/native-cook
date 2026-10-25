import Foundation

/// What a frame draws.
struct SlotReelListFrame: Equatable {
    var off: [Double] = SlotReelListMotion.restRows.map(Double.init)
    var blur: [Double] = [0, 0, 0]
    var glow = 0.0
    var sweep = -1.0
    var lever = 0.0
}

/*
 Slot Reel List — the machine, stepped once a frame from the TimelineView (like the RN frame
 callback). In the loop every value is a function of the loop time; under touch the drums keep an
 offset each (a spring after a drag, a spin after the lever), and 2 s after the last touch they
 roll back to their resting rows and the loop starts again from 0.
*/
@MainActor
final class SlotReelListEngine {
    private typealias M = SlotReelListMotion
    enum Mode { case auto, user, back }

    static let idle = 2.0
    static let returnTime = 0.9

    var reduced = false
    var rowH = 72.0
    /// The rows the card shows changed (only when the drums have settled).
    var onRows: (([Int]) -> Void)?
    /// 0: a tick (selection), 1: a reel stopped (light), 2: a win (success).
    var onFeel: ((Int) -> Void)?

    private(set) var frame = SlotReelListFrame()
    private(set) var moving = false
    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var last: Date?
    private var lastTouch = 0.0

    // Per drum under touch: a free offset (maybe on a spring), or a lever spin.
    private var free: [Double] = M.restRows.map(Double.init)
    private var springAt: [Double?] = [nil, nil, nil]
    private var springFrom = [0.0, 0.0, 0.0]
    private var springV = [0.0, 0.0, 0.0]
    private var springTo = [0.0, 0.0, 0.0]
    private var spinning = [false, false, false]
    private var spinStart = [0.0, 0.0, 0.0]
    private var spinDur = [1.0, 1.0, 1.0]
    private var spinBase = [0.0, 0.0, 0.0]
    private var spinDist = [0.0, 0.0, 0.0]
    private var spinCount = 0
    private var winAt = -10.0

    private var lever = 0.0
    private(set) var leverHeld = false
    private var leverFrom = 0.0
    private var leverAt = -10.0
    private(set) var dragging = -1
    private var returnAt = 0.0
    private var returnFrom = [0.0, 0.0, 0.0]
    private var shown = M.restRows

    // MARK: - Offsets

    private func freeValue(_ c: Int) -> Double {
        guard let at = springAt[c] else { return free[c] }
        let k = now - at
        if k > 1.4 {
            springAt[c] = nil
            free[c] = springTo[c]
            return free[c]
        }
        return M.snapSpring(k, from: springFrom[c], velocity: springV[c], to: springTo[c])
    }

    private func setFree(_ c: Int, _ v: Double) {
        springAt[c] = nil
        free[c] = v
    }

    private func offsetNow(_ c: Int) -> Double {
        if spinning[c] { return spinBase[c] + M.spinRows(now - spinStart[c], spinDur[c], spinDist[c]) }
        return freeValue(c)
    }

    /// Takes the machine from the loop where it is: drums keep their offsets, the lever its angle.
    private func touched() {
        lastTouch = now
        if mode == .user { return }
        for c in 0..<3 {
            let o = mode == .auto ? M.autoOffset(c, clock) : frame.off[c]
            setFree(c, o)
            spinning[c] = false
        }
        // A lever caught mid-swing keeps swinging from where it is.
        leverFrom = frame.lever
        leverAt = now
        winAt = -10
        mode = .user
    }

    private func startSpin() {
        let at = now
        spinCount += 1
        for c in 0..<3 {
            let o = offsetNow(c)
            let from = o.rounded()
            let to = floor(M.hash(Double(spinCount) * 3.1 + Double(c) * 7.7) * Double(M.rows))
            let final = from + M.spinDistance(M.wrap(from, Double(M.rows)), to, M.turns[c])
            if reduced {
                setFree(c, final)
                spinning[c] = false
                continue
            }
            spinning[c] = true
            spinBase[c] = o
            spinDist[c] = final - o
            spinStart[c] = at + 0.06 * Double(c)
            spinDur[c] = M.stops[c] - M.spinAt
        }
        winAt = reduced ? at : at + 0.12 + spinDur[2] + 0.2
    }

    // MARK: - Frame

    func step(to date: Date, auto: Bool) -> SlotReelListFrame {
        let dt = min(max(last.map { date.timeIntervalSince($0) } ?? 0, 0), 0.05)
        last = date
        let at = now + dt
        now = at
        let prev = frame
        var off = [0.0, 0.0, 0.0]
        var lv = 0.0
        var glow = 0.0
        var sweep = -1.0
        var settled = true

        switch mode {
        case .auto:
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: SlotReelList.loop) : 0
            clock = t
            for c in 0..<3 { off[c] = M.autoOffset(c, t) }
            lv = M.autoLever(t)
            let w = M.autoWin(t)
            glow = w.glow
            sweep = w.sweep
            let pick = t >= M.sweepAt && t < 8.4 ? M.winRows : M.restRows
            if pick != shown {
                shown = pick
                onRows?(pick)
            }
        case .back:
            let p = M.easeInOutCubic((at - returnAt) / Self.returnTime)
            let n = Double(M.rows)
            for c in 0..<3 {
                // To the nearest offset that shows the resting row.
                let d = M.wrap(Double(M.restRows[c]) - returnFrom[c] + n / 2, n) - n / 2
                off[c] = returnFrom[c] + d * p
            }
            lv = M.leverSpring(at - leverAt, leverFrom)
            glow = prev.glow * (1 - min(1, dt * 8))
            if p >= 1 {
                mode = .auto
                clock = 0
            }
        case .user:
            var stopped = false
            for c in 0..<3 {
                if spinning[c] {
                    let end = spinStart[c] + spinDur[c]
                    if at - dt < end && at >= end { stopped = true }
                    if at > end + 0.9 {
                        setFree(c, spinBase[c] + spinDist[c])
                        spinning[c] = false
                    } else {
                        settled = false
                    }
                }
                off[c] = offsetNow(c)
            }
            if stopped { onFeel?(1) }
            lv = leverHeld ? lever : M.leverSpring(at - leverAt, leverFrom)
            let k = at - winAt
            if k >= 0 && k < 2.6 {
                if k < dt { onFeel?(2) }
                glow = M.easeOutCubic(k / 0.2) * (1 - M.easeOutCubic((k - 2.2) / 0.3))
                sweep = !reduced && k <= 1 ? M.easeInOutCubic(k) : -1
                settled = false
            }
            for c in 0..<3 where abs(off[c] - off[c].rounded()) > 0.01 { settled = false }
            if dragging >= 0 || leverHeld { settled = false }
            if settled {
                let pick = (0..<3).map { Int(M.wrap(off[$0].rounded(), Double(M.rows))) }
                if pick != shown {
                    shown = pick
                    onRows?(pick)
                }
                if auto && at - lastTouch > Self.idle {
                    returnFrom = off
                    returnAt = at
                    leverFrom = lv
                    leverAt = at
                    mode = .back
                }
            }
        }

        // Blur from each drum's speed: the distance it covers in ~1/30 s.
        var blur = [0.0, 0.0, 0.0]
        var same = abs(lv - prev.lever) < 1e-4 && abs(glow - prev.glow) < 1e-4 && sweep == prev.sweep
        for c in 0..<3 {
            if dt > 0 {
                let v = (off[c] - prev.off[c]) / max(dt, 0.001)
                blur[c] = reduced ? 0 : min(0.9, abs(v) * 0.033)
            } else {
                blur[c] = prev.blur[c]
            }
            if abs(off[c] - prev.off[c]) > 1e-5 || abs(blur[c] - prev.blur[c]) > 1e-4 { same = false }
        }
        frame = SlotReelListFrame(off: off, blur: blur, glow: glow, sweep: sweep, lever: lv)
        moving = !same || mode != .user || !settled
        return frame
    }

    // MARK: - Touch

    func leverBegan() {
        touched()
        leverHeld = true
        lever = frame.lever
        onFeel?(0)
    }

    func leverMoved(translationY: Double, arm: Double) {
        lastTouch = now
        let raw = max(0, translationY) / (arm * 1.6)
        // Past the bottom it resists (rubber band).
        let over = max(0, raw - 1)
        lever = M.leverMax * (min(raw, 1) + over / (1 + over * 4) * 0.15)
    }

    func leverEnded() {
        guard leverHeld else { return }
        leverHeld = false
        lastTouch = now
        leverFrom = lever
        leverAt = now
        if lever > M.leverMax * 0.55 { startSpin() }
    }

    func reelBegan(column c: Int) {
        touched()
        if spinning[c] {
            setFree(c, offsetNow(c))
            spinning[c] = false
        }
        setFree(c, freeValue(c))
        dragging = c
    }

    func reelMoved(deltaY: Double) {
        let c = dragging
        guard c >= 0 else { return }
        lastTouch = now
        setFree(c, free[c] - deltaY / rowH)
    }

    func reelEnded(velocityY: Double) {
        let c = dragging
        guard c >= 0 else { return }
        dragging = -1
        lastTouch = now
        let v = -velocityY / rowH
        let o = free[c]
        let target = (o + v * 0.22).rounded()
        if reduced {
            setFree(c, target)
        } else {
            springAt[c] = now
            springFrom[c] = o
            springV[c] = v
            springTo[c] = target
        }
        if target != o.rounded() { onFeel?(0) }
    }
}
