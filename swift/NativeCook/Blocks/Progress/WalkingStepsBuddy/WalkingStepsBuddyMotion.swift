import Foundation
import CoreGraphics

/// Time model of Walking Steps Buddy. Two clocks drive the screen:
/// - D, the distance walked (pt): the parallax layers and the walk cycle are functions of D.
/// - the loop time t (0 ..< loop): the step count, the bar, the milestone pop and the sun.
/// In autoPlay D = speed · t, so one loop is exactly one tile of the near layer and twelve strides.
enum WalkingStepsBuddyMotion {
    static let loop = 9.0
    /// Walking speed of the near layer (pt/s).
    static let speed = 96.0
    /// Distance after which every layer repeats (near tile).
    static let tile = 864.0
    /// Distance of one stride (two footfalls).
    static let stride = 72.0
    /// Seconds after the last touch before the loop clock resumes.
    static let idle = 2.0

    // Parallax factors and tile widths: factor · tile / width is a whole number for every layer.
    static let hillFactor = 0.2, hillTile = 172.8
    static let bushFactor = 0.5, bushTile = 432.0
    static let frontFactor = 1.25, frontTile = 360.0
    static let cloudDrift = 72.0

    /// Step count after each footfall of the loop; the last "0" is the next day.
    static let counts: [Int] = [0, 164, 352, 538, 712, 905, 1093, 1270, 1462, 1641, 1838, 2016, 2203, 2391, 2570, 2764, 2952, 3146, 0]
    static let firstFootfall = 1.125
    static let footfallGap = 0.375
    static let rollTime = 0.24
    static let resetStart = 7.8
    static let resetEnd = 8.4
    static let milestones: [Int] = [1000, 3000, 6000, 10000]
    static let milestoneLabels = ["1k steps", "3k steps", "6k steps", "10k steps"]
    static let rewards = ["+10", "+30", "+60", "+100"]
    static let barLeft = 20.0, barRight = 373.0
    static let tickX: [Double] = (0..<4).map { 20 + 353 * (Double($0) + 0.5) / 4 }

    static let mascotX = 176.0

    static let countStrings: [String] = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "en_US")
        return counts.map { f.string(from: NSNumber(value: $0)) ?? "\($0)" }
    }()

    // MARK: Curves

    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(max(x, lo), hi) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func backOut(_ x: Double) -> Double {
        let c1 = 1.70158, c3 = c1 + 1
        return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
    }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }
    static func fract(_ x: Double) -> Double { x - floor(x) }
    static func mod(_ x: Double, _ m: Double) -> Double { x - floor(x / m) * m }
    static func hash(_ i: Double) -> Double { fract(sin(i * 12.9898) * 43758.5453) }

    // MARK: Loop

    static func footfall(_ k: Int) -> Double { firstFootfall + footfallGap * Double(k - 1) }

    /// Drum position of the count: k + roll progress (0 … 18; 18 shows the next day's 0).
    static func countPhase(_ t: Double) -> Double {
        var p = 0.0
        for k in 1...17 { p += easeOut(seg(t, footfall(k), footfall(k) + rollTime)) }
        return p + easeInOut(seg(t, resetStart, resetEnd))
    }

    static func steps(phase: Double) -> Double {
        let i = Int(floor(phase))
        if i >= counts.count - 1 { return 0 }
        let f = phase - Double(i)
        return Double(counts[i]) + (Double(counts[i + 1]) - Double(counts[i])) * f
    }

    /// Loop time at which a milestone is reached (nil when the loop never reaches it).
    static func crossTime(_ milestone: Int) -> Double? {
        for k in 1...17 where counts[k] >= milestone { return footfall(k) + 0.05 }
        return nil
    }
    static let crossTimes: [Double?] = milestones.map { crossTime($0) }

    /// 0 → 1 (with overshoot) when milestone i pops, back to 0 at the next day.
    static func tickPop(_ i: Int, _ t: Double) -> Double {
        guard let c = crossTimes[i] else { return 0 }
        return backOut(seg(t, c, c + 0.4)) * (1 - easeInOut(seg(t, resetStart, resetStart + 0.5)))
    }

    static func pillMix(_ i: Int, _ t: Double) -> Double {
        guard let c = crossTimes[i] else { return 0 }
        return easeOut(seg(t, c + 0.1, c + 0.35)) * (1 - easeInOut(seg(t, resetStart, resetStart + 0.5)))
    }

    /// Bar position (pt) for a step value: each milestone sits under its column.
    static func barX(_ s: Double) -> Double {
        if s <= 0 { return barLeft }
        var prevS = 0.0, prevX = barLeft
        for i in 0..<4 {
            let m = Double(milestones[i])
            if s <= m { return prevX + (tickX[i] - prevX) * (s - prevS) / (m - prevS) }
            prevS = m; prevX = tickX[i]
        }
        return min(barRight, prevX + (barRight - prevX) * (s - prevS) / 2000)
    }

    /// 0 at day, 1 when the sun has set.
    static func dusk(_ t: Double) -> Double {
        easeInOut(seg(t, 6.2, 7.4)) * (1 - easeInOut(seg(t, resetStart, resetStart + 0.8)))
    }

    static let celebrate = 3.05
    static func happy(_ t: Double) -> Bool { t >= celebrate && t < celebrate + 0.85 }

    struct Bubble { let y: Double; let alpha: Double; let scale: Double }
    /// The "+10 coins" bubble above the 1k tick; y is relative to the bar centre.
    static func bubble(_ t: Double) -> Bubble {
        let rise = backOut(seg(t, 3.1, 3.5))
        let out = easeInOut(seg(t, 4.3, 4.7))
        return Bubble(y: -40 * rise - 8 * out, alpha: easeOut(seg(t, 3.1, 3.25)) * (1 - out), scale: 0.6 + 0.4 * rise)
    }

    struct Confetto { let x: Double; let y: Double; let angle: Double; let alpha: Double; let kind: Int; let color: Int }
    static let confettiColors = [0xFF6A1F, 0xFFC14D, 0x7CC95A, 0x5DB4F2, 0xFF8FB1]
    /// Confetti from the 1k tick, relative to the tick.
    static func confetti(_ t: Double) -> [Confetto] {
        let tau = t - celebrate
        if tau < 0 || tau > 1.3 { return [] }
        var out: [Confetto] = []
        for j in 0..<24 {
            let h1 = hash(Double(j) + 1), h2 = hash(Double(j) + 31.7), h3 = hash(Double(j) + 77.1)
            let a = -Double.pi / 2 + (h1 - 0.5) * 2.0
            let v = 170 + 150 * h2
            let x = cos(a) * v * tau
            let y = sin(a) * v * tau + 260 * tau * tau
            out.append(Confetto(x: x, y: y, angle: (h3 - 0.5) * 14 * tau + h1 * 6,
                                alpha: 1 - smooth(0.8, 1.3, tau), kind: j % 3, color: confettiColors[j % 5]))
        }
        return out
    }

    // MARK: Walk

    struct Foot { let x: Double; let lift: Double; let angle: Double }
    static func foot(_ u0: Double) -> Foot {
        let u = fract(u0)
        if u < 0.5 { return Foot(x: 18 - 72 * u, lift: 0, angle: 0) }
        let v = (u - 0.5) / 0.5
        return Foot(x: -18 + 36 * easeInOut(v), lift: 12 * sin(Double.pi * v), angle: -14 * sin(Double.pi * v))
    }

    struct Pose {
        var near: Foot, far: Foot
        var bob: Double, sx: Double, sy: Double
        var lean: Double, knot: Double, arm: Double, bag: Double
        var lift: Double = 0
    }

    static func pose(distance: Double) -> Pose {
        let w = fract(distance / stride)
        let s2 = pow(sin(2 * Double.pi * w), 2)
        let c = min(w, abs(w - 0.5), 1 - w)
        let g = exp(-pow(c / 0.07, 2))
        return Pose(
            near: foot(w), far: foot(w + 0.5),
            bob: -7 * s2,
            sx: 1 + 0.06 * g, sy: 1 - 0.07 * g + 0.025 * s2,
            lean: 4 + 1.5 * sin(4 * Double.pi * w + 0.5),
            knot: -4 - 9 * sin(4 * Double.pi * w - 0.9),
            arm: 16 * sin(2 * Double.pi * w),
            bag: 12 * sin(2 * Double.pi * w - 1.2)
        )
    }

    /// Hop after a tap: lift (pt) and vertical squash, τ s after the tap.
    static func hop(_ tau: Double) -> (lift: Double, squash: Double) {
        if tau < 0 || tau > 0.62 { return (0, 1) }
        if tau < 0.08 { return (0, 1 - 0.08 * sin(Double.pi / 2 * tau / 0.08)) }
        if tau < 0.5 {
            let u = (tau - 0.08) / 0.42
            let s = u < 0.2 ? 0.92 + 0.13 * (u / 0.2) : 1.05 - 0.05 * ((u - 0.2) / 0.8)
            return (36 * sin(Double.pi * u), s)
        }
        let u = (tau - 0.5) / 0.12
        return (0, 1 - 0.07 * sin(Double.pi * u))
    }

    // MARK: Layout

    static func ground(safeTop: Double) -> Double { safeTop + 362 }
}

/// Touch state, read by the timeline; a plain class, so writing it never re-renders the view.
final class WalkingStepsBuddyTouch {
    typealias M = WalkingStepsBuddyMotion

    var touching = false
    private var liveD = 0.0
    private var startD = 0.0
    private var downClock = 0.0
    private var moved = 0.0
    private var released = false
    private var releaseClock = 0.0
    private var releaseD = 0.0
    private var releaseV = 0.0
    private var frozen = 0.0
    private var resume = 0.0
    var hopClock = -10.0

    /// Distance walked at a clock time (seconds since the view appeared).
    func distance(_ clock: Double, autoPlay: Bool) -> Double {
        if touching { return liveD }
        if !released { return autoPlay ? M.speed * clock : 0 }
        let tau = max(0, clock - releaseClock)
        if autoPlay {
            return releaseD + M.speed * tau + (releaseV - M.speed) * (1 - exp(-6 * tau)) / 6
        }
        // Without autoPlay the walk settles on the nearest footfall pose (critically damped).
        let w = 8.0
        let target = ((releaseD + releaseV / w) / (M.stride / 2)).rounded() * (M.stride / 2)
        let a = releaseD - target
        return target + (a + (releaseV + w * a) * tau) * exp(-w * tau)
    }

    /// Loop time: paused while touching and for `idle` s after, then it resumes where it stopped.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double {
        guard autoPlay else { return 0 }
        let t = touching ? frozen : frozen + max(0, clock - resume)
        return M.mod(t, M.loop)
    }

    /// Clock time after which nothing moves any more (outside autoPlay).
    var restsAfter: Double { max(releaseClock + 1.2, hopClock + 0.7) }

    func begin(clock: Double, autoPlay: Bool) {
        guard !touching else { return }
        frozen = loopTime(clock, autoPlay: autoPlay)
        startD = distance(clock, autoPlay: autoPlay)
        liveD = startD
        touching = true
        downClock = clock
        moved = 0
    }

    func move(dx: Double, dy: Double) {
        moved = max(moved, (dx * dx + dy * dy).squareRoot())
        guard moved >= 8 else { return }
        liveD = startD - dx
    }

    /// Ends a touch; returns true when it was a tap on the mascot (a hop).
    func end(clock: Double, velocityX: Double, point: CGPoint, ground: Double, autoPlay: Bool) -> Bool {
        var hopped = false
        if moved < 8 && clock - downClock < 0.4 {
            releaseV = autoPlay ? M.speed : 0
            let lift = M.hop(clock - hopClock).lift
            let x = Double(point.x), y = Double(point.y)
            if abs(x - M.mascotX) < 84 && y > ground + 22 - 178 - lift && y < ground + 30 {
                hopClock = clock
                hopped = true
            }
        } else {
            releaseV = M.clamp(-velocityX, -700, 700)
        }
        releaseD = liveD
        releaseClock = clock
        released = true
        resume = clock + M.idle
        touching = false
        return hopped
    }
}
