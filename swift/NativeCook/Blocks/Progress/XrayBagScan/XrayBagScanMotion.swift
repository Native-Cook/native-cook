import Foundation

/// Every moving value of the bag scan as a pure function of the loop time `t` (0 ..< loop) or of the
/// belt travel `d`. The belt carries every bag the same distance: bag k sits at `restX + d − spacing·k`,
/// so one loop moves the belt exactly one bag spacing (16 slats) and frame P equals frame 0.
enum XrayBagScanMotion {
    static let loop = 9.0

    // Layout (pt on the 393-wide canvas).
    static let restX = 57.0
    static let centreX = 196.5
    static let spacing = 400.0
    static let leg1 = centreX - restX // 139.5: rest → centred in the window
    static let pitch = 25.0 // belt slat pitch; spacing / pitch = 16
    static let winL = 126.5, winR = 266.5, winT = 332.0, winB = 556.0
    static let beltTop = 528.0, beltY = 542.0, skirtTop = 552.0, skirtBottom = 580.0
    static let bagW = 98.0, bagH = 184.0

    // Timeline (s).
    static let go1 = 1.0, stop1 = 3.4, go2 = 5.6, stop2 = 8.2
    static let statusTimes = [1.0, 3.5, 5.2, 7.6]
    static let boxTimes = [3.8, 4.1, 4.4]
    static let clearTime = 5.2

    // MARK: Helpers

    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(x, a), b) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func mod(_ x: Double, _ m: Double) -> Double { let r = x.truncatingRemainder(dividingBy: m); return r < 0 ? r + m : r }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double { let u = clamp((x - a) / (b - a)); return u * u * (3 - 2 * u) }

    static func inverseEaseInOutCubic(_ y: Double) -> Double {
        let v = clamp(y)
        return v < 0.5 ? cbrt(v / 4) : 1 - cbrt(2 * (1 - v)) / 2
    }

    /// Under-damped spring step 0 → 1 (ζ 0.55, ω 14), 0 before `τ` = 0. Peaks about 12 % over.
    static func pop(_ tau: Double) -> Double {
        guard tau > 0 else { return 0 }
        let z = 0.55, w = 14.0
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    // MARK: Belt and bags

    /// Belt travel at loop time t: 0 → leg1 (into the window), dwell, → spacing (out, next bag in).
    static func belt(_ t: Double) -> Double {
        leg1 * easeInOutCubic(seg(t, go1, stop1)) + (spacing - leg1) * easeInOutCubic(seg(t, go2, stop2))
    }

    /// The loop time whose belt travel is `d` (0 ... spacing), for a release after a scrub.
    static func timeForBelt(_ d: Double) -> Double {
        if d <= 0 { return go1 }
        if d < leg1 { return go1 + (stop1 - go1) * inverseEaseInOutCubic(d / leg1) }
        if d < leg1 + 0.5 { return stop1 }
        return go2 + (stop2 - go2) * inverseEaseInOutCubic((d - leg1) / (spacing - leg1))
    }

    /// One damped rock of the bag after the belt starts or stops; 0 outside 0 ..< 0.8 s.
    static func rock(_ tau: Double) -> Double {
        guard tau > 0, tau < 0.8 else { return 0 }
        return sin(11 * tau) * exp(-4 * tau) * (1 - smoothstep(0.5, 0.8, tau))
    }

    /// Lean of the bags in degrees (clockwise positive): the top lags when the belt starts, runs on when it stops.
    static func lean(_ t: Double) -> Double {
        2.6 * (-rock(t - go1) + rock(t - stop1) - rock(t - go2) + rock(t - stop2))
    }

    // MARK: Scanner

    /// The beacon and the LEDs run while the scan does (1.0 … 5.4 s).
    static func scanning(_ t: Double) -> Double {
        easeInOutCubic(seg(t, go1, go1 + 0.2)) * (1 - easeInOutCubic(seg(t, clearTime, clearTime + 0.2)))
    }

    /// Beacon brightness, pulsing 18 times a loop.
    static func beacon(_ t: Double) -> Double {
        scanning(t) * (0.55 + 0.45 * sin(2 * Double.pi * 18 * t / loop))
    }

    /// Cleared state (green LEDs, green boxes, green bar): 5.2 → 5.4 in, 7.6 → 8.2 out.
    static func cleared(_ t: Double) -> Double {
        easeInOutCubic(seg(t, clearTime, clearTime + 0.25)) * (1 - easeInOutCubic(seg(t, 7.6, 8.2)))
    }

    /// The horizontal sweep while the bag dwells: y in the window and its opacity.
    static func sweep(_ t: Double) -> (y: Double, alpha: Double) {
        let u = seg(t, 3.5, 4.9)
        return (winT + (winB - winT) * easeInOutSine(u), sin(Double.pi * seg(t, 3.45, 4.95)))
    }

    /// Glow of a window edge at `x`: 0.35 at rest, up to 1 where a bag crosses it.
    static func curtain(edge x: Double, bagX: Double) -> Double {
        let left = bagX - bagW / 2, right = bagX + bagW / 2
        let inside = clamp(min(x - left, right - x) / 14)
        return 0.35 + 0.65 * inside
    }

    /// Detection boxes: (scale, alpha) per item. While scrubbing they follow the item's place in the window.
    static func boxes(t: Double, bagX: Double, scrubbing: Bool, items: [Double]) -> [(scale: Double, alpha: Double)] {
        items.indices.map { i in
            if scrubbing {
                let x = bagX + items[i]
                let a = clamp((x - winL - 12) / 18) * clamp((winR - 12 - x) / 18)
                return (0.85 + 0.15 * a, a)
            }
            let tau = t - boxTimes[i]
            let fade = 1 - easeInOutCubic(seg(t, 5.9, 6.3))
            return (0.6 + 0.4 * pop(tau), clamp(tau / 0.12) * fade)
        }
    }

    // MARK: Status card

    static let statuses = ["Ready to scan", "Scanning…", "Checking contents", "Bag cleared"]

    /// Status stage 0 … 4 (4 ≡ 0), each step a 0.25 s ease.
    static func stage(_ t: Double) -> Double {
        statusTimes.reduce(0) { $0 + easeOutCubic(seg(t, $1, $1 + 0.25)) }
    }

    /// Bar fill 0 … 1: a third per step, back to 0 when the next bag arrives.
    static func fill(_ t: Double) -> Double {
        let up = 0.333 * easeInOutCubic(seg(t, go1, stop1))
            + 0.334 * easeInOutCubic(seg(t, stop1, clearTime))
            + 0.333 * easeOutCubic(seg(t, clearTime, clearTime + 0.4))
        return up * (1 - easeInOutCubic(seg(t, 7.6, 8.2)))
    }

    /// Check badge pop (0 … ~1.1 … 1), and the stroke's drawn fraction.
    static func check(_ t: Double) -> (scale: Double, stroke: Double) {
        let out = 1 - easeInOutCubic(seg(t, 7.6, 7.85))
        return (pop(t - clearTime) * out, easeOutCubic(seg(t, clearTime + 0.08, clearTime + 0.4)) * out)
    }

    /// Spinner angle (radians), 4 turns a loop so it repeats.
    static func spin(_ t: Double) -> Double { 2 * Double.pi * 4 * t / loop }
}

/// Touch state, read by the timeline; a plain class, so writing it never re-renders the view.
final class XrayBagScanTouch {
    typealias M = XrayBagScanMotion

    var touching = false
    private var started = false
    private var base = 0.0 // loop time at baseClock
    private var baseClock = 0.0
    private var startD = 0.0
    private var liveD = 0.0
    private var offset = 0.0 // belt travel still to absorb after a release
    private var offsetClock = -10.0
    private var velocity = 0.0
    private var leanAtRelease = 0.0
    private var wasInside = false

    /// Loop time (seconds) at a clock time.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double {
        if touching { return M.timeForBelt(M.mod(liveD, M.spacing)) }
        if !autoPlay && !started { return 0 }
        let raw = base + max(0, clock - baseClock)
        return M.mod(autoPlay ? raw : min(raw, M.loop), M.loop)
    }

    /// Belt travel (pt) at a clock time; wraps at one spacing (the next bag takes the first one's place).
    func belt(_ clock: Double, autoPlay: Bool) -> Double {
        if touching { return liveD < 0 ? liveD : M.mod(liveD, M.spacing) }
        let tau = max(0, clock - offsetClock)
        return M.belt(loopTime(clock, autoPlay: autoPlay)) + offset * (1 + 10 * tau) * exp(-10 * tau)
    }

    /// Lean (degrees): from the finger's speed while dragging, settling after the release.
    func lean(_ clock: Double, t: Double) -> Double {
        if touching { return M.clamp(-velocity / 260, -1, 1) * 4 }
        let tau = max(0, clock - offsetClock)
        return M.lean(t) + leanAtRelease * exp(-5 * tau) * cos(11 * tau)
    }

    var scrubbing: Bool { touching }

    func begin(clock: Double, autoPlay: Bool) {
        guard !touching else { return }
        startD = belt(clock, autoPlay: autoPlay)
        liveD = startD
        velocity = 0
        wasInside = false
        touching = true
    }

    /// Moves the belt with the finger; returns true when the bag has just come fully into the window.
    func move(dx: Double, velocityX: Double) -> Bool {
        let raw = startD + dx
        liveD = raw < 0 ? -(-raw) / (1 + -raw / 120) : raw
        velocity = velocityX
        let d = liveD < 0 ? liveD : M.mod(liveD, M.spacing)
        let inside = abs(d - M.leg1) < 16
        defer { wasInside = inside }
        return inside && !wasInside
    }

    func end(clock: Double) {
        guard touching else { return }
        let d = liveD < 0 ? liveD : M.mod(liveD, M.spacing)
        let t = M.timeForBelt(d)
        leanAtRelease = M.clamp(-velocity / 260, -1, 1) * 4
        touching = false
        started = true
        base = t
        baseClock = clock
        offset = d - M.belt(t)
        offsetClock = clock
    }

    /// "Scan again": the pass starts over from the rest frame.
    func restart(clock: Double) {
        touching = false
        started = true
        base = 0
        baseClock = clock
        offset = 0
        leanAtRelease = 0
    }

    /// Clock time after which nothing moves any more (outside autoPlay).
    var restsAfter: Double { started ? baseClock + (M.loop - base) + 0.3 : 0 }
}
