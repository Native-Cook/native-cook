import Foundation
import Observation

/// What the view draws: thumb positions as shown (pushed apart when in contact), presses, ghost finger and bars.
struct DualRangeHistogramFrame {
    var low = 0.0
    var high = 1.0
    var pressLow = 0.0
    var pressHigh = 0.0
    var ghost = 0.0
    var ghostSide = 0
    var bins: [Double] = []
}

/// The count of homes in the range, rolled to whole numbers over 220 ms. Shared with whatever shows it (the button).
@Observable
final class DualRangeHistogramCount {
    private(set) var from = 0.0
    private(set) var to = 0.0
    private(set) var start = 0.0
    private(set) var duration = 0.0

    func value(at now: Double) -> Double {
        guard duration > 0 else { return to }
        return DualRangeHistogramMotion.mix(from, to, DualRangeHistogramMotion.easeMorph((now - start) / duration))
    }

    func busy(at now: Double) -> Bool { duration > 0 && now < start + duration }

    func set(_ v: Double, at now: Double, animated: Bool) {
        let current = value(at: now)
        from = animated ? current : v
        to = v
        start = now
        duration = animated ? DualRangeHistogramMotion.countS : 0
    }
}

/// The histogram's state machine: the film (a pure function of loop time), and under a finger two springs. Stepped by
/// the view's `TimelineView`; it never publishes per-frame state itself.
final class DualRangeHistogramEngine {
    typealias M = DualRangeHistogramMotion

    private enum Mode { case auto, manual, returning }

    private struct Spring1 {
        var value: Double
        var velocity = 0.0
        var target: Double
        var spring = M.releaseSpring
        init(_ v: Double) { value = v; target = v }
        mutating func set(_ v: Double) { value = v; target = v; velocity = 0 }
        mutating func to(_ t: Double, _ s: M.Spring, velocity v: Double? = nil) {
            target = t; spring = s
            if let v { velocity = v }
        }
        var moving: Bool { value != target || velocity != 0 }
        mutating func step(_ dt: Double) {
            guard moving else { return }
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 480)
                velocity += (-spring.stiffness * (value - target) - spring.damping * velocity) * h
                value += velocity * h
                left -= h
            }
            if abs(value - target) < 0.00002 && abs(velocity) < 0.0002 { value = target; velocity = 0 }
        }
    }

    let gap: Double
    let stepU: Double
    let restLow: Double
    let restHigh: Double
    let film: M.Film

    var playing = true
    var span = 1.0
    var inset = M.thumb / 2

    var counter: DualRangeHistogramCount?
    var onTick: (() -> Void)?
    var onPress: (() -> Void)?
    var onSettle: ((Double, Double) -> Void)?

    private var mode = Mode.auto
    private var loopStart: Double?
    private var clock = 0.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var holding = false

    private var low: Spring1
    private var high: Spring1
    private var pressLow = Spring1(0)
    private var pressHigh = Spring1(0)

    private var grabbed = -1
    private var nearest = 0
    private var startX = 0.0
    private var grabOffset = 0.0
    private var lastTick = -1.0
    private var tickKey = -1

    private var binsFrom: [Double]
    private var binsTo: [Double]
    private var morphStart = 0.0
    private var morphing = false

    private var countTarget = -1

    /// DEBUG only: `-dualRangeHistogramTime <s>` pins the film to one moment, for stills of a phase.
    private let pin: Double? = {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-dualRangeHistogramTime"), args.indices.contains(i + 1) { return Double(args[i + 1]) }
        #endif
        return nil
    }()

    init(bins: [Double], min: Double, max: Double, step: Double, minGap: Double, defaultValue: (Double, Double)) {
        let range = max - min
        gap = minGap / range
        stepU = step / range
        restLow = (defaultValue.0 - min) / range
        restHigh = (defaultValue.1 - min) / range
        film = M.Film(restLow: restLow, restHigh: restHigh, gap: gap)
        low = Spring1(restLow)
        high = Spring1(restHigh)
        binsFrom = bins
        binsTo = bins
    }

    /// True when nothing moves and nothing is scheduled: the timeline may pause.
    var asleep: Bool {
        if playing || mode != .manual || holding || morphing { return false }
        return !(low.moving || high.moving || pressLow.moving || pressHigh.moving)
    }

    var lowValue: Double { low.value }
    var highValue: Double { high.value }

    private func morph(_ now: Double) -> Double {
        guard morphing else { return 1 }
        let k = M.clamp((now - morphStart) / M.morphS, 0, 1)
        if k >= 1 { morphing = false; binsFrom = binsTo; return 1 }
        return M.easeMorph(k)
    }

    private func bins(_ now: Double) -> [Double] {
        let k = morph(now)
        if k >= 1 { return binsTo }
        return zip(binsFrom, binsTo).map { M.mix($0, $1, k) }
    }

    /// A new array morphs the bars over 420 ms.
    func setBins(_ next: [Double], at now: Double) {
        guard next != binsTo else { return }
        binsFrom = bins(now)
        binsTo = next
        morphStart = now
        morphing = true
    }

    // MARK: - Frame

    func frame(at now: Double) -> DualRangeHistogramFrame {
        let dt = M.clamp(now - clock, 1.0 / 240, 0.05)
        clock = now
        var out = DualRangeHistogramFrame()
        if mode == .auto && !playing {
            mode = .manual
            pressLow.set(0); pressHigh.set(0)
            low.set(restLow); high.set(restHigh)
        }
        if mode == .auto {
            if loopStart == nil { loopStart = now }
            let t = pin ?? (now - loopStart!).truncatingRemainder(dividingBy: M.loop)
            low.set(M.filmLow(t, film))
            high.set(M.filmHigh(t, film))
            pressLow.set(M.filmPressLow(t))
            pressHigh.set(M.filmPressHigh(t))
            out.ghost = M.clamp(M.filmGhost(t), 0, 1)
            out.ghostSide = M.filmGhostSide(t)
        } else {
            if mode == .manual {
                if playing && !holding && now - lastTouch > M.idle {
                    mode = .returning
                    returnAt = now + M.returnS
                    low.to(restLow, M.releaseSpring)
                    high.to(restHigh, M.releaseSpring)
                    settle(restLow, restHigh)
                }
            } else if now >= returnAt {
                low.set(restLow); high.set(restHigh)
                loopStart = now
                mode = .auto
                return frame(at: now)
            }
            if !(holding && grabbed == 0) { low.step(dt) }
            if !(holding && grabbed == 1) { high.step(dt) }
            pressLow.step(dt)
            pressHigh.step(dt)
            ticks(now)
        }
        out.low = M.shownLow(low.value, high.value, gap)
        out.high = M.shownHigh(low.value, high.value, gap)
        out.pressLow = pressLow.value
        out.pressHigh = pressHigh.value
        out.bins = bins(now)

        let n = Int(M.rangeCount(out.bins, out.low, out.high).rounded())
        if n != countTarget {
            let first = countTarget < 0
            countTarget = n
            if let counter {
                DispatchQueue.main.async { counter.set(Double(n), at: now, animated: !first) }
            }
        }
        return out
    }

    /// A selection tick each $250 under a held thumb and one at the limit.
    private func ticks(_ now: Double) {
        guard holding, mode == .manual, grabbed >= 0 else { tickKey = -1; return }
        let atLimit = grabbed == 0 ? (low.value < 0 || low.value > high.value - gap) : (high.value > 1 || high.value < low.value + gap)
        let u = grabbed == 0 ? M.shownLow(low.value, high.value, gap) : M.shownHigh(low.value, high.value, gap)
        let price = DualRangeHistogramData.priceMin + M.clamp(u, 0, 1) * (DualRangeHistogramData.priceMax - DualRangeHistogramData.priceMin)
        let key = Int(floor(price / M.tickPrice)) * 2 + (atLimit ? 1 : 0)
        defer { tickKey = key }
        guard tickKey >= 0, key != tickKey, now - lastTick >= M.tickMinS else { return }
        lastTick = now
        call(onTick)
    }

    private func call(_ f: (() -> Void)?) {
        guard let f else { return }
        DispatchQueue.main.async { f() }
    }

    private func settle(_ a: Double, _ b: Double) {
        guard let f = onSettle else { return }
        DispatchQueue.main.async { f(a, b) }
    }

    // MARK: - Touch (x in the histogram's space)

    func touchBegan(x: Double, at now: Double) {
        clock = max(clock, now)
        mode = .manual
        holding = true
        lastTouch = now
        low.set(low.value)
        high.set(high.value)
        let lx = inset + M.shownLow(low.value, high.value, gap) * span
        let hx = inset + M.shownHigh(low.value, high.value, gap) * span
        let dl = abs(x - lx), dh = abs(x - hx)
        // Thumbs in contact: the side the finger is on decides.
        let side = abs(dl - dh) < 2 ? (x > (lx + hx) / 2 ? 1 : 0) : (dl < dh ? 0 : 1)
        nearest = side
        startX = x
        pressLow.set(0)
        pressHigh.set(0)
        tickKey = -1
        if min(dl, dh) <= M.grabR {
            grab(side)
            grabOffset = (side == 0 ? lx : hx) - x
        } else {
            grabbed = -1
            grabOffset = 0
        }
    }

    private func grab(_ side: Int) {
        grabbed = side
        if side == 0 { pressLow.to(1, M.pressSpring) } else { pressHigh.to(1, M.pressSpring) }
        call(onPress)
    }

    func touchMoved(x: Double, at now: Double) {
        lastTouch = now
        if grabbed < 0 {
            if abs(x - startX) <= M.tapSlop { return }
            grab(nearest)
        }
        let u = (x + grabOffset - inset) / span
        if grabbed == 0 { low.set(M.constrain(u, 0, high.value - gap)) }
        else { high.set(M.constrain(u, low.value + gap, 1)) }
    }

    func touchEnded(x: Double, velocityX: Double, binCount: Int, at now: Double) {
        holding = false
        lastTouch = now
        pressLow.to(0, M.pressSpring)
        pressHigh.to(0, M.pressSpring)
        var side = grabbed
        var target: Double
        var velocity = 0.0
        if side < 0 {
            // A tap on the chart: the nearest thumb jumps to the bar under the finger.
            side = nearest
            let n = Double(binCount)
            let bin = M.clamp(floor((x - inset) / span * n), 0, n - 1)
            target = (bin + 0.5) / n
            call(onPress)
        } else {
            velocity = velocityX / span
            target = (side == 0 ? low.value : high.value) + velocity * M.flingS
        }
        target = (target / stepU).rounded() * stepU
        let lowNow = low.value, highNow = high.value
        if side == 0 {
            target = M.clamp(target, 0, floor((highNow - gap) / stepU + 1e-6) * stepU)
            low.to(target, M.releaseSpring, velocity: velocity)
            settle(target, highNow)
        } else {
            target = M.clamp(target, ceil((lowNow + gap) / stepU - 1e-6) * stepU, 1)
            high.to(target, M.releaseSpring, velocity: velocity)
            settle(lowNow, target)
        }
        grabbed = -1
    }

    /// VoiceOver's increment and decrement: two steps.
    func nudge(_ side: Int, _ direction: Double) {
        mode = .manual
        lastTouch = clock
        if side == 0 {
            let next = M.clamp(low.target + direction * stepU * 2, 0, high.target - gap)
            low.to(next, M.releaseSpring)
            settle(next, high.target)
        } else {
            let next = M.clamp(high.target + direction * stepU * 2, low.target + gap, 1)
            high.to(next, M.releaseSpring)
            settle(low.target, next)
        }
    }

    /// Back to the default range on a spring (Clear all).
    func reset() {
        if mode == .auto { return }
        mode = .manual
        lastTouch = clock
        low.to(restLow, M.releaseSpring)
        high.to(restHigh, M.releaseSpring)
        settle(restLow, restHigh)
    }
}
