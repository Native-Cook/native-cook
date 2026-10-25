import Foundation
import UIKit

/// Rain Gauge Chart — data and the autoPlay script. Every value is a closed-form function of the
/// loop time t (seconds); the rain's drops are pure functions of t and a per-drop hash, falling a
/// whole number of times per loop, so the frame at t = loop equals the frame at t = 0.
enum RainGaugeChartData {
    static let loop = 9.0
    static let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    static let fullDays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    /// This week's rainfall per day (mm); a tube holds `capacity` mm.
    static let mm: [Double] = [3.2, 0.8, 6.4, 11.6, 7.9, 2.1, 6.6]
    static let notes = [
        "Drizzle through the morning",
        "A passing shower at 09:40",
        "Steady rain after lunch",
        "Heaviest 15:00 – 17:00",
        "Showers on and off",
        "Light rain at dusk",
        "Overnight rain, easing at 06:00",
    ]
    static let capacity = 15.0
    static let tubes = 7
    /// Thursday, the tube the loop lifts.
    static let loopLift = 3

    static let drainAt = 1.4
    static let fillAt = 2.5
    static let fillStep = 0.45
    static let fillFor = 0.9
    static let liftAt = 6.0
    static let lowerAt = 7.0

    static let drops = 170
}

func rainGaugeChartClamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

func rainGaugeChartSeg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    rainGaugeChartClamp01((t - a) / (b - a))
}

func rainGaugeChartEaseInOut(_ x: Double) -> Double {
    let t = rainGaugeChartClamp01(x)
    return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

func rainGaugeChartEaseOut(_ x: Double) -> Double {
    let t = rainGaugeChartClamp01(x)
    return 1 - pow(1 - t, 3)
}

/// Reanimated's default timing curve, Easing.inOut(Easing.quad).
func rainGaugeChartQuadInOut(_ x: Double) -> Double {
    let t = rainGaugeChartClamp01(x)
    return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
}

func rainGaugeChartHash(_ n: Double) -> Double {
    let x = sin(n * 12.9898 + 78.233) * 43758.5453
    return x - floor(x)
}

struct RainGaugeChartPose {
    /// Water in each tube (mm).
    var levels: [Double]
    /// Surface tilt of each tube's water (pt at the walls, signed).
    var slosh: [Double]
    /// The tube lifted out of the rack and how far (0..1).
    var liftIdx: Int
    var lift: Double
    /// Where the rain falls (continuous tube index) and how hard (0..1).
    var cursor: Double
    var rain: Double
    /// The time the drops are drawn at (s).
    var rainT: Double

    static func loop(_ t: Double) -> RainGaugeChartPose {
        typealias D = RainGaugeChartData
        var levels: [Double] = []
        var slosh: [Double] = []
        let cursor = min(Double(D.tubes - 1), max(0, (t - D.fillAt - 0.2) / D.fillStep))
        let rain = rainGaugeChartSeg(t, 2.25, 2.7) * (1 - rainGaugeChartSeg(t, 5.7, 6.2))
        let lift = rainGaugeChartEaseOut(rainGaugeChartSeg(t, D.liftAt, D.liftAt + 0.45))
            * (1 - rainGaugeChartEaseInOut(rainGaugeChartSeg(t, D.lowerAt, D.lowerAt + 0.4)))
        for i in 0..<D.tubes {
            let fi = Double(i)
            var level = D.mm[i]
            if t >= D.drainAt && t < D.fillAt {
                level = D.mm[i] * (1 - rainGaugeChartEaseInOut(rainGaugeChartSeg(t, D.drainAt + fi * 0.04, D.drainAt + 0.8 + fi * 0.04)))
            } else if t >= D.fillAt {
                level = D.mm[i] * rainGaugeChartEaseOut(rainGaugeChartSeg(t, D.fillAt + fi * D.fillStep, D.fillAt + fi * D.fillStep + D.fillFor))
            }
            // The water rocks while the rain hits it (11 rocks a loop) and when Thursday is lifted.
            let near = exp(-pow((fi - cursor) / 0.9, 2)) * rain
            var tilt = 1.6 * near * sin(2 * Double.pi * 11 * t / D.loop + fi * 1.3)
            if i == D.loopLift && t > D.liftAt {
                let e = t - D.liftAt
                tilt += 3.2 * exp(-e * 2.2) * sin(e * 2 * Double.pi * 1.7) * (1 - rainGaugeChartSeg(t, 8.0, 8.6))
            }
            levels.append(level)
            slosh.append(tilt)
        }
        return RainGaugeChartPose(levels: levels, slosh: slosh, liftIdx: D.loopLift, lift: lift, cursor: cursor, rain: rain, rainT: t)
    }

    var total: Double { levels.reduce(0, +) }
}

/// A finger's slosh: a kick that rocks and dies away (seconds since the kick).
func rainGaugeChartKick(_ e: Double, _ size: Double) -> Double {
    if e < 0 || e > 4 { return 0 }
    return size * exp(-e * 2.4) * sin(e * 2 * Double.pi * 1.7)
}

/// A value that follows its target with a spring (duration-based, as Reanimated's), or a timed ease.
struct RainGaugeChartSpring {
    var x: Double
    var v = 0.0
    var target: Double
    var omega = 10.0
    var zeta = 1.0
    var tweenFrom = 0.0
    var tweenAt = 0.0
    var tweenFor = 0.0

    init(_ x: Double) {
        self.x = x
        target = x
    }

    mutating func spring(to target: Double, duration: Double, damping: Double) {
        self.target = target
        zeta = damping
        omega = (damping >= 0.99 ? 5.8 : 4 / damping) / duration
        tweenFor = 0
    }

    mutating func tween(to target: Double, duration: Double, now: Double) {
        self.target = target
        tweenFrom = x
        tweenAt = now
        tweenFor = duration
        v = 0
    }

    mutating func set(_ value: Double) {
        x = value
        target = value
        v = 0
        tweenFor = 0
    }

    var settled: Bool { tweenFor == 0 && abs(x - target) < 0.001 && abs(v) < 0.01 }

    /// Returns true when a tween ends on this step.
    mutating func step(_ dt: Double, now: Double) -> Bool {
        if tweenFor > 0 {
            let k = rainGaugeChartClamp01((now - tweenAt) / tweenFor)
            x = tweenFrom + (target - tweenFrom) * rainGaugeChartQuadInOut(k)
            if k >= 1 {
                tweenFor = 0
                x = target
                return true
            }
            return false
        }
        if settled { x = target; v = 0; return false }
        var left = dt
        while left > 0 {
            let h = min(left, 1.0 / 240)
            let a = -omega * omega * (x - target) - 2 * zeta * omega * v
            v += a * h
            x += v * h
            left -= h
        }
        return false
    }
}

/// The loop clock and the finger's state, stepped once a frame from the timeline.
@MainActor
final class RainGaugeChartModel {
    private typealias D = RainGaugeChartData

    private(set) var auto = false
    private(set) var reduced = false
    private var configured = false
    private var t = 0.0
    private var manual = true
    private var clock = 0.0
    private var resumeAt = -1.0
    private var levels = RainGaugeChartData.mm
    private var kicks = [Double](repeating: -10, count: RainGaugeChartData.tubes)
    private var liftIdx = -1
    private var lift = RainGaugeChartSpring(0)
    private var cursor = 0.0
    private var rain = RainGaugeChartSpring(0)
    private var rainT = 0.0
    private var back = RainGaugeChartSpring(0)
    private var last: Date?
    private var cached = RainGaugeChartPose.loop(0)
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    /// Tells the view which day the callout shows.
    var onCallout: (Int) -> Void = { _ in }

    func configure(auto: Bool, reduced: Bool) {
        guard !configured || auto != self.auto || reduced != self.reduced else { return }
        configured = true
        self.auto = auto
        self.reduced = reduced
        manual = !auto
        t = 0
        cached = RainGaugeChartPose.loop(0)
    }

    /// Steps once per distinct date; several timelines can read the same frame.
    func pose(at date: Date) -> RainGaugeChartPose {
        if let last, date == last { return cached }
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let dt = min(max(raw, 0), 0.05)
        clock += dt
        if !manual {
            t = (t + dt).truncatingRemainder(dividingBy: D.loop)
            cached = RainGaugeChartPose.loop(t)
            return cached
        }
        rainT = (rainT + dt).truncatingRemainder(dividingBy: D.loop)
        _ = lift.step(dt, now: clock)
        _ = rain.step(dt, now: clock)
        if rain.x > 0.01 && back.x == 0 && back.tweenFor == 0 {
            for i in 0..<D.tubes {
                let near = exp(-pow((Double(i) - cursor) / 0.7, 2))
                levels[i] = min(D.capacity, levels[i] + dt * 1.1 * rain.x * near)
            }
        }
        if resumeAt > 0 && clock >= resumeAt {
            resumeAt = -1
            back.tween(to: 1, duration: 0.8, now: clock)
        }
        if back.step(dt, now: clock) && auto {
            t = 0
            manual = false
            back.set(0)
            lift.set(0)
            liftIdx = -1
            rain.set(0)
            levels = D.mm
            onCallout(D.loopLift)
            cached = RainGaugeChartPose.loop(0)
            return cached
        }
        var slosh: [Double] = []
        for i in 0..<D.tubes {
            let near = exp(-pow((Double(i) - cursor) / 0.9, 2)) * rain.x
            slosh.append(rainGaugeChartKick(clock - kicks[i], 3.2) + 1.6 * near * sin(clock * 2 * Double.pi * 1.25 + Double(i) * 1.3))
        }
        let b = back.x
        cached = RainGaugeChartPose(
            levels: (0..<D.tubes).map { levels[$0] + (D.mm[$0] - levels[$0]) * b },
            slosh: slosh.map { $0 * (1 - b) },
            liftIdx: liftIdx,
            lift: lift.x * (1 - b),
            cursor: cursor,
            rain: rain.x * (1 - b),
            rainT: rainT
        )
        return cached
    }

    /// True while something still moves without a finger (for pausing the timeline).
    var moving: Bool {
        !manual || resumeAt > 0 || back.tweenFor > 0 || !lift.settled || rain.x > 0.001 || !rain.settled
            || kicks.contains { clock - $0 < 4 }
    }

    func takeOver() {
        resumeAt = -1
        back.set(0)
        if !manual {
            let p = RainGaugeChartPose.loop(t)
            levels = p.levels
            liftIdx = p.lift > 0.001 ? p.liftIdx : -1
            lift.set(p.lift)
            cursor = p.cursor
            rain.set(p.rain)
            rainT = p.rainT
            manual = true
        }
    }

    func startRain(cursor c: Double) {
        cursor = c
        rain.tween(to: 1, duration: 0.25, now: clock)
    }

    func moveRain(cursor c: Double) { cursor = c }

    func stopRain() { rain.tween(to: 0, duration: 0.6, now: clock) }

    func tapTube(_ i: Int) {
        kicks[i] = clock
        if liftIdx == i && lift.x > 0.5 {
            if reduced { lift.tween(to: 0, duration: 0.2, now: clock) } else { lift.spring(to: 0, duration: 0.42, damping: 0.85) }
        } else {
            if liftIdx >= 0 && liftIdx != i { kicks[liftIdx] = clock }
            liftIdx = i
            lift.set(0)
            if reduced { lift.tween(to: 1, duration: 0.2, now: clock) } else { lift.spring(to: 1, duration: 0.42, damping: 0.7) }
            onCallout(i)
        }
        haptic.impactOccurred()
    }

    func released() {
        if auto { resumeAt = clock + 2 }
    }
}
