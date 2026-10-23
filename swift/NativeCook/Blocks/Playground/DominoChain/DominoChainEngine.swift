import Foundation

/// The numbers of the block, as in the React Native version.
enum DominoChainTuning {
    // The camera looks down at the table at pitch, from distance away, with focal length focal (pt).
    // Its aim point sits aimZ into the table, and shows on screen at aimAt of the height.
    static let pitch = 52 * Double.pi / 180
    static let distance = 820.0
    static let focal = 960.0
    static let aimZ = 30.0
    static let aimAt = 0.56
    // The far rail of the table shows this far below the top inset, pt.
    static let railAt = 150.0
    static let railHeight = 12.0
    static let railDepth = 18.0

    // The autoPlay spiral, in table units: centre, outer and inner radius, turns and start angle.
    static let spiralX = 0.0
    static let spiralZ = -5.0
    static let spiralOuter = 146.0
    static let spiralInner = 32.0
    static let spiralTurns = 2.05
    static let spiralStart = Double.pi * 0.62

    // A finger counts as on a domino within this distance of its top, pt.
    static let hit = 30.0
    // A flick tips a domino when it moves along the domino's heading at least this fast, pt/s.
    static let flickSpeed = 260.0
    // A drag this long across a fallen run stands it back up, pt.
    static let sweepDistance = 90.0
    // While a path is drawn, its points are kept at least this far apart (table units), and the
    // finger is smoothed over this share of the way each move.
    static let pathStep = 3.0
    static let pathSmooth = 0.55
    // A drawn path never keeps more points than this (a full run needs about 700).
    static let maxPath = 1024
    // New dominoes pop up at least this long after the one before, s.
    static let popGap = 0.03
    // One click (light haptic) every this many hits.
    static let clickEvery = 3

    static let tick = 1.0 / 120
    static let maxTicks = 4

    // autoPlay, s: the spiral is drawn, tipped, swept back up and cleared.
    static let loop = 12.0
    static let drawFrom = 0.4
    static let drawTo = 2.6
    static let tapAt = 3.2
    static let sweepFrom = 8.0
    static let sweepTo = 8.5
    static let clearAt = 10.8
    // A touch pauses autoPlay this long, s.
    static let userHold = 4.0

    // What happened during a frame, for haptics and the hint.
    static let eventClick = 1
    static let eventTipped = 2
    static let eventRaised = 4
    static let eventFallen = 8

    static let sinP = sin(pitch)
    static let cosP = cos(pitch)
    static let camY = distance * sinP
    static let camZ = aimZ - distance * cosP

    /// Whether a time `at` was passed going from `before` to `t` on a looping clock.
    static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool {
        (before < at && t >= at) || (t < before && (at > before || at <= t))
    }
}

/// Where the table sits on the screen: the camera's aim point and the far rail.
struct DominoChainLayout: Equatable {
    var width = 0.0
    var height = 0.0
    var railY = 0.0

    var cx: Double { width / 2 }
    var cy: Double { height * DominoChainTuning.aimAt }

    /// The table's far edge, in table units, from where the rail shows on screen.
    var farZ: Double {
        typealias T = DominoChainTuning
        if width == 0 { return 400 }
        let ys = -(railY - cy) / T.focal
        let dy = ys * T.cosP - T.sinP
        let dz = ys * T.sinP + T.cosP
        if dy >= -1e-4 { return 400 }
        return T.camZ + dz * (-T.camY / dy)
    }

    /// Screen x, y and depth of a point on or above the table.
    func project(_ x: Double, _ y: Double, _ z: Double) -> (x: Double, y: Double, depth: Double) {
        typealias T = DominoChainTuning
        let py = y - T.camY
        let pz = z - T.camZ
        let yc = py * T.cosP + pz * T.sinP
        let zc = -py * T.sinP + pz * T.cosP
        return (cx + (T.focal * x) / zc, cy - (T.focal * yc) / zc, zc)
    }

    /// The point on the table under a screen point (nil above the horizon).
    func unproject(_ sx: Double, _ sy: Double) -> (x: Double, z: Double)? {
        typealias T = DominoChainTuning
        let xs = (sx - cx) / T.focal
        let ys = -(sy - cy) / T.focal
        let dx = xs
        let dy = ys * T.cosP - T.sinP
        let dz = ys * T.sinP + T.cosP
        if dy >= -1e-4 { return nil }
        let t = -T.camY / dy
        return (dx * t, T.camZ + dz * t)
    }
}

/// The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
/// step: two runs (the live one and the one sinking away), the path being drawn, the finger and the
/// autoPlay clock on one fixed tick.
final class DominoChainEngine {
    private typealias T = DominoChainTuning
    private typealias S = DominoChainSize

    let reduced: Bool
    private(set) var layout = DominoChainLayout()
    private(set) var started = false

    let chains = [DominoChainRun(), DominoChainRun()]
    /// Which run is the live one; the other may still be sinking away.
    private(set) var live = 0
    /// The path being drawn (table units), the smoothed finger and the arc length placed so far.
    private(set) var pathX: [Double] = []
    private(set) var pathZ: [Double] = []
    private(set) var drawing = false
    private var smoothX = 0.0
    private var smoothZ = 0.0
    private var placed = 0.0
    /// Arc length of the path drawn so far (table units).
    private var pathLength = 0.0
    private var lastBorn = 0.0
    /// When the drawn path was finished, for its fading trace.
    private(set) var traceEnd = -10.0

    private var acc = 0.0
    private var clicks = 0
    private(set) var wasFallen = false

    // The finger: what it is doing (0 none, 1 drawing, 2 flicking, 3 sweeping), where it is, and the
    // domino a flick started on. autoPlay drives the same finger, and shows it.
    private var mode = 0
    private(set) var fingerX = 0.0
    private(set) var fingerY = 0.0
    private var flickOn = -1
    private var swept = false
    private(set) var ghost = 0.0
    private var ghostOn = false

    private var clock = 0.0
    private var hold = 0.0
    private var looping: Bool

    /// What happened since the events were last read (`DominoChainTuning.event…` bits).
    var events = 0

    private static let spiral = DominoChainPath.spiral(
        cx: T.spiralX, cz: T.spiralZ, outer: T.spiralOuter, inner: T.spiralInner, turns: T.spiralTurns, start: T.spiralStart
    )
    private static let spiralLength = DominoChainPath.length(spiral.xs, spiral.zs)

    init(auto: Bool, reduced: Bool) {
        looping = auto
        self.reduced = reduced
    }

    var liveRun: DominoChainRun { chains[live] }

    func setLayout(_ layout: DominoChainLayout) {
        self.layout = layout
    }

    /// Restarts the autoPlay clock (autoPlay switched on or off).
    func setAuto(_ auto: Bool) {
        looping = auto
        clock = 0
        hold = 0
    }

    /// Lays the spiral at once (no pop).
    private func laySpiral(_ c: DominoChainRun) {
        let sp = Self.spiral
        let total = Self.spiralLength
        var s = 0.0
        while s <= total {
            let a = DominoChainPath.point(sp.xs, sp.zs, max(0, s - 3))
            let b = DominoChainPath.point(sp.xs, sp.zs, s + 3)
            let p = DominoChainPath.point(sp.xs, sp.zs, s)
            let hx = b.x - a.x
            let hz = b.z - a.z
            let l = Self.orOne(hypot(hx, hz))
            c.add(p.x, p.z, hx / l, hz / l, delay: -1)
            s += S.spacing
        }
    }

    /// `x || 1` of JavaScript, for a length that may be zero.
    private static func orOne(_ v: Double) -> Double {
        v == 0 || v.isNaN ? 1 : v
    }

    // MARK: Actions, shared by the finger and the script

    /// The live run's standing domino nearest a screen point (its top), or -1.
    private func dominoAt(_ sx: Double, _ sy: Double) -> Int {
        let c = chains[live]
        var best = -1
        var bestD = T.hit
        for i in 0..<c.count where c.sink[i] == .infinity {
            let p = layout.project(c.x[i], S.h * 0.6, c.z[i])
            let d = hypot(p.x - sx, p.y - sy)
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return best
    }

    @discardableResult
    private func beginDraw(_ sx: Double, _ sy: Double) -> Bool {
        guard let at = layout.unproject(sx, sy), at.z <= layout.farZ - S.h else { return false }
        // The old run sinks away; the new one is laid in the other run.
        chains[live].sinkAll()
        live = 1 - live
        let c = chains[live]
        c.count = 0
        c.hits = 0
        pathX = [at.x]
        pathZ = [at.z]
        smoothX = at.x
        smoothZ = at.z
        placed = 0
        pathLength = 0
        lastBorn = c.time - 1
        drawing = true
        traceEnd = .infinity
        clicks = 0
        return true
    }

    private func extendDraw(_ sx: Double, _ sy: Double) {
        if !drawing { return }
        guard let at = layout.unproject(sx, sy) else { return }
        let z = min(at.z, layout.farZ - S.h)
        smoothX += (at.x - smoothX) * T.pathSmooth
        smoothZ += (z - smoothZ) * T.pathSmooth
        let n = min(pathX.count, pathZ.count)
        if n == 0 { return }
        let c = chains[live]
        // The run is full: the path (and its cost per move) stops growing.
        if c.count >= S.maxDominoes || n >= T.maxPath { return }
        let step = hypot(smoothX - pathX[n - 1], smoothZ - pathZ[n - 1])
        if !(step >= T.pathStep) { return }
        pathX.append(smoothX)
        pathZ.append(smoothZ)
        pathLength += step
        let total = pathLength
        // Place a domino every spacing along the path, a little behind the finger so its heading is known.
        while placed + 4 <= total {
            let p = DominoChainPath.point(pathX, pathZ, placed)
            let a = DominoChainPath.point(pathX, pathZ, max(0, placed - 4))
            let b = DominoChainPath.point(pathX, pathZ, placed + 4)
            let hx = b.x - a.x
            let hz = b.z - a.z
            let l = Self.orOne(hypot(hx, hz))
            let delay = max(0, lastBorn + T.popGap - c.time)
            if !c.add(p.x, p.z, hx / l, hz / l, delay: delay) { break }
            lastBorn = c.time + delay
            // The one before now knows where its path goes next.
            if c.count > 1 {
                let k = c.count - 2
                let dx = c.x[k + 1] - c.x[k]
                let dz = c.z[k + 1] - c.z[k]
                let dl = Self.orOne(hypot(dx, dz))
                let mx = (c.hx[k] + dx / dl) / 2
                let mz = (c.hz[k] + dz / dl) / 2
                let hl = hypot(mx, mz)
                // A hairpin can cancel the two headings out: then it faces the next one.
                if hl > 1e-6 {
                    c.aim(k, mx / hl, mz / hl)
                } else {
                    c.aim(k, dx / dl, dz / dl)
                }
            }
            placed += S.spacing
        }
    }

    private func endDraw() {
        if !drawing { return }
        drawing = false
        traceEnd = chains[live].time
        // Too short to be a run: it sinks away again.
        if chains[live].count < 2 { chains[live].sinkAll() }
    }

    private func push(_ i: Int, _ spin: Double) {
        if chains[live].push(i, spin) { events |= T.eventTipped }
    }

    private func sweep() {
        let c = chains[live]
        if !c.anyFallen { return }
        c.raise(gentle: reduced)
        c.hits = 0
        clicks = 0
        events |= T.eventRaised
    }

    /// One step of the autoPlay script, from `before` to `t` s into the loop.
    private func script(_ before: Double, _ t: Double) {
        if t >= T.drawFrom && t <= T.drawTo {
            // The finger traces the spiral from its outer end inwards.
            let sp = Self.spiral
            let total = Self.spiralLength
            let k = (t - T.drawFrom) / (T.drawTo - T.drawFrom)
            let e = k < 0.5 ? 2 * k * k : 1 - 2 * (1 - k) * (1 - k)
            let at = DominoChainPath.point(sp.xs, sp.zs, e * (total + 6))
            let p = layout.project(at.x, 0, at.z)
            fingerX = p.x
            fingerY = p.y
            ghostOn = true
            if T.crossed(before, t, T.drawFrom) || !drawing {
                // Start exactly on the spiral, with no smoothing lag.
                beginDraw(p.x, p.y)
                smoothX = at.x
                smoothZ = at.z
                pathX = [at.x]
                pathZ = [at.z]
                pathLength = 0
            } else {
                extendDraw(p.x, p.y)
            }
        } else if drawing && t > T.drawTo {
            // Finish the tail exactly, then lift.
            let sp = Self.spiral
            let end = DominoChainPath.point(sp.xs, sp.zs, Self.spiralLength + 6)
            let p = layout.project(end.x, 0, end.z)
            extendDraw(p.x, p.y)
            endDraw()
            ghostOn = false
        }
        let c = chains[live]
        if t >= T.tapAt - 0.35 && t < T.tapAt + 0.2 && c.count > 0 {
            let p = layout.project(c.x[0], S.h * 0.6, c.z[0])
            fingerX = p.x
            fingerY = p.y
            ghostOn = t < T.tapAt
        }
        if T.crossed(before, t, T.tapAt) { push(0, S.push) }
        if t >= T.sweepFrom && t <= T.sweepTo {
            let k = (t - T.sweepFrom) / (T.sweepTo - T.sweepFrom)
            fingerX = layout.width * (0.12 + 0.76 * k)
            fingerY = layout.height * 0.78 - 30 * sin(k * Double.pi)
            ghostOn = true
            if k > 0.45 && c.anyFallen && !c.busy { sweep() }
        } else if t > T.sweepTo && t < T.sweepTo + 0.1 {
            ghostOn = false
        }
        if T.crossed(before, t, T.clearAt) { c.sinkAll() }
    }

    /// One frame `frameDt` s after the last one.
    func frame(_ frameDt: Double) {
        if layout.width == 0 { return }
        if !started {
            started = true
            // Without autoPlay the table starts with the spiral standing, ready to tip.
            if !looping { laySpiral(chains[0]) }
        }
        let dt = min(frameDt, Double(T.maxTicks) * T.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= T.tick && ticks < T.maxTicks {
            pending -= T.tick
            ticks += 1
            if hold > 0 {
                if mode == 0 { hold -= T.tick }
                if hold <= 0 { clock = 0 }
            } else if looping {
                let before = clock
                let t = (before + T.tick).truncatingRemainder(dividingBy: T.loop)
                clock = t
                script(before, t)
            }
            chains[0].step(T.tick)
            chains[1].step(T.tick)
        }
        acc = min(pending, T.tick)
        ghost += ((ghostOn ? 1 : 0) - ghost) * min(1, dt * 16)

        // A click every few hits, on the frame the hit lands.
        let c = chains[live]
        let due = c.hits / T.clickEvery
        if due > clicks {
            clicks = due
            events |= T.eventClick
        }
        let down = c.anyFallen
        if down != wasFallen {
            wasFallen = down
            events |= T.eventFallen
        }
    }

    // MARK: Gestures

    private func touched() {
        hold = T.userHold
        ghostOn = false
        if drawing && mode == 0 { endDraw() }
    }

    /// A drag began at (x0, y0) and is now at (x, y).
    func panStart(x0: Double, y0: Double, x: Double, y: Double) {
        guard started else { return }
        touched()
        let c = chains[live]
        let falling = c.busy
        if c.anyFallen {
            mode = falling ? 0 : 3
            swept = false
            return
        }
        let hit = dominoAt(x0, y0)
        if hit >= 0 {
            mode = 2
            flickOn = hit
            return
        }
        if beginDraw(x0, y0) {
            mode = 1
            extendDraw(x, y)
        } else {
            mode = 0
        }
    }

    func panMove(x: Double, y: Double, translationX: Double, translationY: Double) {
        guard started else { return }
        if mode == 1 {
            extendDraw(x, y)
        } else if mode == 3 && !swept && hypot(translationX, translationY) > T.sweepDistance {
            swept = true
            sweep()
        }
    }

    /// The drag ended with the finger moving at (vx, vy) pt/s (`cancelled` when it did not end cleanly).
    func panEnd(velocityX: Double, velocityY: Double, cancelled: Bool = false) {
        guard started else { return }
        if mode == 2 && !cancelled {
            // A flick along the domino's heading tips it, harder for a faster flick.
            let c = chains[live]
            let i = flickOn
            guard i >= 0 && i < c.count else {
                mode = 0
                return
            }
            let a = layout.project(c.x[i], 0, c.z[i])
            let b = layout.project(c.x[i] + c.hx[i] * 10, 0, c.z[i] + c.hz[i] * 10)
            let dx = b.x - a.x
            let dy = b.y - a.y
            let l = Self.orOne(hypot(dx, dy))
            let along = (velocityX * dx + velocityY * dy) / l
            if along > T.flickSpeed { push(i, S.push * min(1.8, max(1, along / 700))) }
        }
        if mode == 1 { endDraw() }
        mode = 0
    }

    func tap(x: Double, y: Double) {
        guard started else { return }
        touched()
        let i = dominoAt(x, y)
        if i >= 0 { push(i, S.push) }
    }
}
