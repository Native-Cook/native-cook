import Foundation

// Fifteen pendulums tuned so that in one cycle of `cycle` seconds the longest swings `baseSwings`
// times and each shorter one swings once more than its neighbour. Released together they drift out
// of phase into travelling waves, snakes and apparent chaos, and come back into line exactly at the
// end of the cycle. A line-for-line port of the React Native simulation.
//
// World units: the table is y = 0, y is up, the row of pendulums runs along z, and they swing along
// x. A pendulum's state is its bob's sideways offset x and its speed; a free pendulum is a harmonic
// oscillator advanced exactly (a rotation in phase space by ω·dt), so the phases do not drift
// however long it runs. While the comb holds them the bobs are pulled onto it by a stiff spring,
// stepped in fixed substeps.
enum PendulumWaveTuning {
    /// Seconds the autoPlay script takes before it starts over.
    static let loop = 36.0
    /// Pendulums in the row.
    static let count = 15
    /// Seconds after which every pendulum is back in line.
    static let cycle = 30.0
    /// Swings of the longest pendulum in one cycle; pendulum k makes baseSwings + k.
    static let baseSwings = 24.0
    /// The beam's height, the longest pendulum's length, the spacing along the row (world units).
    static let beamY = 12.0
    static let longest = 10.0
    static let spacing = 1.0
    /// Half the spread of each pendulum's V of string along the beam.
    static let vHalf = 0.42
    /// The bob's radius.
    static let bobR = 0.36
    /// The furthest the comb takes the bobs aside, and its soft give past that.
    static let amplitudeMax = 2.0
    static let give = 0.6
    // The comb's grip: a stiff spring (1/s²) and damping (1/s).
    static let gripK = 520.0
    static let gripDamp = 41.0
    static let substep = 1.0 / 240
    static let maxFrame = 1.0 / 30
    /// Light trails: one sample every trailStep s, `trail` samples long.
    static let trail = 24
    static let trailStep = 1.0 / 60
    /// The comb fades in over combIn s and away over combOut s.
    static let combIn = 0.22
    static let combOut = 0.35

    static let eventGrab = 1
    static let eventRelease = 2

    // Views and the camera. A view is an orbit around the scene's middle: yaw (degrees around the
    // vertical, 0 = looking down the row from its end), pitch (degrees above the horizon), distance.
    static let viewThreeQuarter = 0
    static let viewTop = 1
    static let viewSide = 2
    static let viewNames = ["3/4", "Top", "Side"]
    static let views: [PendulumWaveCamera] = [
        PendulumWaveCamera(yaw: 36, pitch: 20, dist: 34),
        PendulumWaveCamera(yaw: 0, pitch: 76, dist: 29),
        PendulumWaveCamera(yaw: 20, pitch: 6, dist: 31),
    ]
    /// In the 3/4 view the camera sways round by `orbit` degrees over each loop.
    static let orbit = 16.0
    /// A change of view takes `morph` s on an ease-in-out curve.
    static let morph = 1.5

    // autoPlay: the comb pulls the bobs aside and lets go, the camera visits each view while one
    // whole cycle plays, and when the bobs come back into line the comb catches them and brings
    // them home.
    static let amplitude = 1.7
    static let grabAt = 0.6
    static let pulledAt = 2.2
    static let releaseAt = 2.4
    static let catchAt = releaseAt + cycle
    static let homeAt = catchAt + 2.1
    static let setDownAt = homeAt + 0.4
    /// The view the camera turns to at each time.
    static let viewScript: [(Double, Int)] = [(9.5, viewTop), (17.5, viewSide), (25.5, viewThreeQuarter)]

    /// The row position of pendulum k.
    static func rowZ(_ k: Int) -> Double {
        (Double(k) - Double(count - 1) / 2) * spacing
    }

    /// A sideways offset limited to amplitudeMax with a soft give.
    static func rubber(_ x: Double) -> Double {
        let a = abs(x)
        if a <= amplitudeMax { return x }
        let sign: Double = x > 0 ? 1 : -1
        return sign * (amplitudeMax + give * (1 - exp(-(a - amplitudeMax) / give)))
    }

    static func easeInOut(_ p: Double) -> Double {
        let t = min(1, max(0, p))
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// Where view `index` puts the camera at loop time t.
    static func viewCamera(_ index: Int, _ t: Double) -> PendulumWaveCamera {
        let v = views[index]
        let sway = index == viewThreeQuarter ? orbit * sin(2 * .pi * t / loop) : 0
        return PendulumWaveCamera(yaw: v.yaw + sway, pitch: v.pitch, dist: v.dist)
    }

    /// The camera partway (`p`, 0…1) from one view to another, eased; a morph arcs out a little.
    static func blendCamera(_ a: PendulumWaveCamera, _ b: PendulumWaveCamera, _ p: Double) -> PendulumWaveCamera {
        let e = easeInOut(p)
        let lift = sin(.pi * e) * 3
        return PendulumWaveCamera(
            yaw: a.yaw + (b.yaw - a.yaw) * e,
            pitch: a.pitch + (b.pitch - a.pitch) * e,
            dist: a.dist + (b.dist - a.dist) * e + lift
        )
    }

    /// True when the loop's clock went past `at` between `before` and `now` (wrapping at the loop).
    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : at > before || at <= now
    }
}

struct PendulumWaveCamera {
    var yaw: Double
    var pitch: Double
    var dist: Double
}

final class PendulumWaveSim {
    private typealias T = PendulumWaveTuning

    var x = Array(repeating: 0.0, count: PendulumWaveTuning.count)
    var v = Array(repeating: 0.0, count: PendulumWaveTuning.count)
    /// Angular frequency (rad/s) and length of each pendulum.
    let w: [Double]
    let length: [Double]
    /// Trail ring buffer: `trail` past x per pendulum, newest at `trailHead`.
    var trail = Array(repeating: Array(repeating: 0.0, count: PendulumWaveTuning.trail), count: PendulumWaveTuning.count)
    var trailHead = 0
    var trailClock = 0.0
    /// The comb: held or not, where it is, how visible.
    var held = false
    var combX = 0.0
    var combShown = 0.0
    /// Seconds since the last release, for the cycle readout (-1 before the first).
    var sinceRelease = -1.0
    var time = 0.0
    var events = 0
    var eventsByUser = false

    init() {
        var w: [Double] = []
        var length: [Double] = []
        for k in 0..<T.count {
            let swings = T.baseSwings + Double(k)
            w.append(2 * .pi * swings / T.cycle)
            // Length goes as the period squared: L = g / ω², scaled so the longest is `longest`.
            length.append(T.longest * (T.baseSwings / swings) * (T.baseSwings / swings))
        }
        self.w = w
        self.length = length
    }

    func grab(_ x: Double, byUser: Bool) {
        held = true
        combX = x
        events |= T.eventGrab
        if byUser { eventsByUser = true }
    }

    func move(_ x: Double) {
        if held { combX = T.rubber(x) }
    }

    /// Lets the bobs go; `timed` starts the cycle readout (a comb set down at rest does not).
    func release(byUser: Bool, timed: Bool) {
        if !held { return }
        held = false
        sinceRelease = timed ? 0 : -1
        events |= T.eventRelease
        if byUser { eventsByUser = true }
    }

    func step(_ frame: Double) {
        let dt = min(frame, T.maxFrame)
        time += dt
        if sinceRelease >= 0 && !held { sinceRelease += dt }
        if held {
            combShown = min(1, combShown + dt / T.combIn)
            let n = max(1, Int(ceil(dt / T.substep - 1e-6)))
            let h = dt / Double(n)
            for _ in 0..<n {
                for k in 0..<T.count {
                    let a = T.gripK * (combX - x[k]) - T.gripDamp * v[k]
                    v[k] += a * h
                    x[k] += v[k] * h
                }
            }
        } else {
            combShown = max(0, combShown - dt / T.combOut)
            // Free: each pendulum turns through ω·dt in phase space, exactly.
            for k in 0..<T.count {
                let wk = w[k]
                let c = cos(wk * dt)
                let sn = sin(wk * dt)
                let xk = x[k]
                let u = v[k] / wk
                x[k] = xk * c + u * sn
                v[k] = (u * c - xk * sn) * wk
            }
        }
        // Trails, sampled on a fixed clock.
        trailClock += dt
        while trailClock >= T.trailStep {
            trailClock -= T.trailStep
            trailHead = (trailHead + 1) % T.trail
            for k in 0..<T.count { trail[k][trailHead] = x[k] }
        }
    }

    /// The bob of pendulum k at sideways offset x, in the world.
    func bobAt(_ k: Int, _ x: Double) -> (Double, Double, Double) {
        let L = length[k]
        let off = max(-L * 0.95, min(L * 0.95, x))
        return (off, T.beamY - (L * L - off * off).squareRoot(), T.rowZ(k))
    }

    /// Drives the comb from the loop's clock; returns the view the script wants now.
    func script(before: Double, now: Double) -> Int {
        if T.passed(before, now, T.grabAt) { grab(0, byUser: false) }
        if now >= T.grabAt && now < T.releaseAt && held {
            move(T.amplitude * T.easeInOut((now - T.grabAt) / (T.pulledAt - T.grabAt)))
        }
        if T.passed(before, now, T.releaseAt) { release(byUser: false, timed: true) }
        // The bobs are back in line at catchAt: the comb meets them there.
        if T.passed(before, now, T.catchAt) {
            grab(T.amplitude, byUser: false)
            sinceRelease = -1
        }
        if now >= T.catchAt && held { move(T.amplitude * (1 - T.easeInOut((now - T.catchAt) / (T.homeAt - T.catchAt)))) }
        // Home and still: the comb is lifted away, so the loop starts from bare rest.
        if T.passed(before, now, T.setDownAt) { release(byUser: false, timed: false) }
        var view = T.viewThreeQuarter
        for (at, index) in T.viewScript where now >= at { view = index }
        return view
    }
}
