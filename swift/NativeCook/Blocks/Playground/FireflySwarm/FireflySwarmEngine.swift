import SwiftUI
import UIKit

enum FireflySwarmMetrics {
    // autoPlay, one `loop` in seconds. Every periodic thing (blinks, wind, the hover of a resting
    // firefly, the stars) repeats a whole number of times per loop, so the loop closes cleanly.
    static let loop = 20.0
    static let tau = Double.pi * 2
    static let w0 = tau / loop

    // The swarm: `count` fireflies, simulated at a fixed `step` (s), at most `maxSteps` per frame.
    static let count = 150
    static let step = 1.0 / 60
    static let maxSteps = 3
    // Boids: neighbours within `sight` pt, kept `space` pt apart. Free flight tops out at `cruise`
    // pt/s, steering at `turn` pt/s². Weights for separation, alignment, cohesion and wander.
    static let sight = 46.0
    static let space = 16.0
    static let cruise = 58.0
    static let turn = 90.0
    static let separate = 1.6
    static let align = 0.5
    static let cohere = 0.35
    static let wander = 0.7
    // They keep to the air over the meadow, pushed back at `edgePush` per pt over.
    static let bandTop = 0.28
    static let bandBottom = 0.84
    static let margin = 36.0
    static let edgePush = 1.6
    // A finger: they orbit it at `orbitSpeed` on their own radius, up to `rush` pt/s, gathering in
    // over `gather` per s. A release faster than `flick` pt/s scatters them with up to `blast` pt/s.
    static let orbitSpeed = 150.0
    static let rush = 240.0
    static let gather = 4.0
    static let flick = 900.0
    static let blast = 520.0
    static let blastReach = 170.0
    // Holding still for `holdTime` calms them and pulls their blinks into a wave rolling out from
    // the finger: one pulse every 1 / `syncRate` s, travelling at `waveSpeed` pt/s.
    static let holdTime = 0.45
    static let holdSlop = 14.0
    static let hush = 1.3
    static let heldPull = 0.35
    static let heldReach = 150.0
    static let syncRate = 1.0
    static let waveSpeed = 240.0
    static let syncPull = 7.0
    static let syncIn = 1.6
    static let syncOut = 0.45
    static let driftBack = 1.2
    // Resting (autoPlay only): each firefly hovers over its own spot, held by a spring of `homeOmega`.
    static let homeOmega = 3.2
    // A touch pauses autoPlay; it comes back `resume` s after the finger lifts.
    static let resume = 3.0

    // The script.
    static let eightFrom = 1.6
    static let eightTime = 6.6
    static let eightWidth = 0.22
    static let eightHeight = 0.14
    static let eightY = 0.54
    static let swipeTime = 0.22
    static let swipeTo = CGPoint(x: 0.82, y: 0.4)
    static let pressFrom = 9.4
    static let pressTime = 3.8
    static let pressAt = CGPoint(x: 0.46, y: 0.5)
    static let homeFrom = 14.4
    static let homeTime = 2.6

    // Glow sprites: a soft halo and a hot core.
    static let sprite = 64.0
    static let ember = 0.1
    static let halo = [0.62, 0.95, 0.3]
    static let core = [1.0, 0.98, 0.74]

    // Meadow: three layers of grass blades, swaying in a wind that repeats per loop and bending
    // where fireflies pass.
    struct GrassLayer {
        let count: Int
        let base: Double
        let low: Double
        let high: Double
        let thin: Double
        let thick: Double
        let color: UInt32
        let tip: UInt32
        let give: Double
    }

    static let grass = [
        GrassLayer(count: 90, base: 0.74, low: 22, high: 60, thin: 2.5, thick: 4.5, color: 0x0f1028, tip: 0x4b4579, give: 0.5),
        GrassLayer(count: 70, base: 0.86, low: 50, high: 120, thin: 3, thick: 6, color: 0x08091b, tip: 0x3a3664, give: 0.8),
        GrassLayer(count: 48, base: 1.01, low: 96, high: 210, thin: 4, thick: 8.5, color: 0x030409, tip: 0x2b2950, give: 1),
    ]
    static let bucket = 24.0
    static let bendOmega = 6.0
    static let bendDamping = 0.35
    static let bendGain = 0.0045
    static let bendMax = 0.5
    static let stars = 56
    static let meadowTop: UInt32 = 0x14142e

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    static func smooth(_ t: Double) -> Double {
        let x = clamp01(t)
        return x * x * (3 - 2 * x)
    }

    /// A firefly's flash at blink phase `p`: a quick rise, a slower fade, an ember between.
    static func flash(_ p: Double) -> Double {
        let f = p - floor(p)
        if f < 0.1 { return ember + (1 - ember) * smooth(f / 0.1) }
        return ember + (1 - ember) * exp(-(f - 0.1) * 8)
    }
}

/// The seeded generator the React Native version uses (mulberry32), so the meadow and the swarm match.
struct FireflySwarmRandom {
    private var s: UInt32
    init(seed: UInt32) { s = seed }

    mutating func next() -> Double {
        s = s &+ 0x6d2b_79f5
        var t = s
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }
}

/// Each firefly's fixed traits: where it rests and how it hovers there, its blink (a whole number of
/// flashes per loop), its depth, its orbit and its wander.
struct FireflySwarmTraits {
    var homeX: [Double] = [], homeY: [Double] = [], bobX: [Double] = [], bobY: [Double] = []
    var bobXK: [Double] = [], bobYK: [Double] = [], bobXP: [Double] = [], bobYP: [Double] = []
    var blinkK: [Double] = [], blinkP: [Double] = [], depth: [Double] = [], orbit: [Double] = []
    var spin: [Double] = [], wanderA: [Double] = [], wanderB: [Double] = []

    static let shared: FireflySwarmTraits = {
        var r = FireflySwarmRandom(seed: 11)
        var t = FireflySwarmTraits()
        let tau = FireflySwarmMetrics.tau
        for _ in 0..<FireflySwarmMetrics.count {
            t.homeX.append(0.05 + 0.9 * r.next())
            t.homeY.append(0.36 + 0.48 * pow(r.next(), 0.8))
            t.bobX.append(6 + 12 * r.next())
            t.bobY.append(4 + 9 * r.next())
            t.bobXK.append(1 + floor(r.next() * 2))
            t.bobYK.append(1 + floor(r.next() * 3))
            t.bobXP.append(r.next() * tau)
            t.bobYP.append(r.next() * tau)
            t.blinkK.append(7 + floor(r.next() * 7))
            t.blinkP.append(r.next())
            t.depth.append(0.55 + 0.65 * r.next())
            t.orbit.append(30 + 95 * pow(r.next(), 0.7))
            t.spin.append(r.next() < 0.82 ? 1 : -1)
            t.wanderA.append(r.next() * tau)
            t.wanderB.append(r.next() * tau)
        }
        return t
    }()
}

/// Blades: x as a share of the width, height and width in pt, a phase for the wind, a lean.
struct FireflySwarmBlades {
    var x: [Double] = [], h: [Double] = [], w: [Double] = [], p: [Double] = [], lean: [Double] = []

    static let layers: [FireflySwarmBlades] = FireflySwarmMetrics.grass.enumerated().map { k, layer in
        var r = FireflySwarmRandom(seed: UInt32(31 + k))
        var b = FireflySwarmBlades()
        for i in 0..<layer.count {
            b.x.append((Double(i) + r.next() * 0.9) / Double(layer.count) * 1.08 - 0.04)
            b.h.append(layer.low + (layer.high - layer.low) * pow(r.next(), 1.4))
            b.w.append(layer.thin + (layer.thick - layer.thin) * r.next())
            b.p.append(r.next() * FireflySwarmMetrics.tau)
            b.lean.append((r.next() - 0.5) * 0.35)
        }
        return b
    }
}

struct FireflySwarmStars {
    var x: [Double] = [], y: [Double] = [], size: [Double] = [], k: [Double] = [], p: [Double] = []

    static let field: FireflySwarmStars = {
        var r = FireflySwarmRandom(seed: 5)
        var s = FireflySwarmStars()
        for _ in 0..<FireflySwarmMetrics.stars {
            s.x.append(r.next())
            s.y.append(0.02 + 0.42 * pow(r.next(), 1.3))
            s.size.append(0.5 + 1.1 * pow(r.next(), 3))
            s.k.append(1 + floor(r.next() * 5))
            s.p.append(r.next() * FireflySwarmMetrics.tau)
        }
        return s
    }()

    // The far tree line's crowns, drawn from a fixed seed.
    static let treeLine: [Double] = {
        var r = FireflySwarmRandom(seed: 77)
        return (0..<400).map { _ in r.next() }
    }()
}

/// The swarm's state, stepped at a fixed 60 Hz like the React Native frame callback.
final class FireflySwarmEngine {
    private typealias M = FireflySwarmMetrics
    private let traits = FireflySwarmTraits.shared

    private(set) var x: [Double] = []
    private(set) var y: [Double] = []
    private(set) var vx: [Double] = []
    private(set) var vy: [Double] = []
    private(set) var drift: [Double] = []
    private(set) var bend: [Double] = []
    private var bendSpeed: [Double] = []
    private(set) var elapsed = 0.0
    private var spare = 0.0
    private var lastDate: Date?
    private(set) var size = CGSize.zero
    var ready: Bool { x.count == M.count }

    // The finger: down or not, where, how strongly it pulls (0–1), and whether it is held still.
    private var fingerDown = false
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var pull = 0.0
    private var holding = false
    // Synchronised blinking: its strength (0–1) and the point its wave rolls out from.
    private(set) var sync = 0.0
    private(set) var syncX = 0.0
    private(set) var syncY = 0.0
    // How strongly the fireflies are held to their resting spots (autoPlay).
    private var home = 0.0
    private var clock = 0.0
    private var autoFinger = false
    private var hold = 0.0
    private var started = false

    // The touch under way.
    private var touchStart: Date?
    private var touchFrom = CGPoint.zero
    private var touchAt = CGPoint.zero
    private var panning = false
    private var pressFailed = false
    private var pressed = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    func step(to date: Date, size: CGSize, autoPlay: Bool, reducedMotion: Bool) {
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.05)
        guard dt > 0, size.width > 0 else { return }
        if !started {
            started = true
            home = autoPlay ? 1 : 0
        }
        if !ready || size != self.size {
            reset(size)
            return
        }
        let w = size.width
        let h = size.height

        // A finger held still long enough syncs the blinking.
        if let start = touchStart, !pressed, !pressFailed, date.timeIntervalSince(start) >= M.holdTime {
            pressed = true
            startSync(touchAt.x, touchAt.y)
            soft.impactOccurred()
        }

        // autoPlay moves a finger along the script; a touch hands control back for a while.
        if hold > 0 {
            if !fingerDown { hold -= dt }
            if hold <= 0 { clock = M.homeFrom }
        } else if autoPlay {
            let t = (clock + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            if let f = scriptFinger(t, w, h) {
                if !autoFinger {
                    autoFinger = true
                    fingerDown = true
                }
                fingerX = f.x
                fingerY = f.y
                if f.hold && !holding { startSync(f.x, f.y) }
            } else if autoFinger {
                autoFinger = false
                fingerDown = false
                holding = false
                // The script's swipe ends in a flick.
                if t < M.pressFrom {
                    scatter(M.swipeTo.x * w, M.swipeTo.y * h, M.swipeTo.x - 0.5, M.swipeTo.y - M.eightY, reducedMotion: reducedMotion)
                }
            }
            // Held home while resting; let go as the finger arrives; called home again at the end.
            var held = 0.0
            if t < M.eightFrom { held = 1 }
            else if t < M.eightFrom + 1 { held = 1 - M.smooth(t - M.eightFrom) }
            else if t >= M.homeFrom { held = M.smooth((t - M.homeFrom) / M.homeTime) }
            home = held
        }

        // The finger's pull and the sync come and go smoothly; held still, the finger keeps a
        // gentle pull so the calmed swarm drifts in round it.
        let pulling = fingerDown ? (holding ? M.heldPull : 1) : 0
        pull += (pulling - pull) * min(1, M.gather * dt)
        sync += ((holding ? 1 : 0) - sync) * min(1, (holding ? M.syncIn : M.syncOut) * dt)

        // Fixed steps of the swarm.
        var acc = spare + dt
        var steps = 0
        while acc >= M.step && steps < M.maxSteps {
            acc -= M.step
            steps += 1
            advance(w: w, h: h, reducedMotion: reducedMotion)
        }
        spare = acc
    }

    private func reset(_ size: CGSize) {
        self.size = size
        let w = size.width
        let h = size.height
        let t = traits
        // Everyone starts on their resting spot, moving as the hover moves.
        x = (0..<M.count).map { t.homeX[$0] * w + t.bobX[$0] * sin(t.bobXP[$0]) }
        y = (0..<M.count).map { t.homeY[$0] * h + t.bobY[$0] * sin(t.bobYP[$0]) }
        vx = (0..<M.count).map { t.bobX[$0] * t.bobXK[$0] * M.w0 * cos(t.bobXP[$0]) }
        vy = (0..<M.count).map { t.bobY[$0] * t.bobYK[$0] * M.w0 * cos(t.bobYP[$0]) }
        drift = Array(repeating: 0, count: M.count)
        bend = Array(repeating: 0, count: Int(ceil(w / M.bucket)) + 1)
        bendSpeed = bend
    }

    /// A grid or bucket index from a position in cell units: `Int(_:)` traps on NaN or infinity, so a
    /// position that ever went bad lands in cell 0 instead of crashing.
    @inline(__always)
    private static func cellIndex(_ v: Double) -> Int {
        guard v.isFinite else { return 0 }
        return Int(max(-1_000_000, min(1_000_000, floor(v))))
    }

    /// Steering towards a desired velocity, capped at `turn`.
    @inline(__always)
    private func steer(_ dx: Double, _ dy: Double, _ vx: Double, _ vy: Double, _ speed: Double) -> (Double, Double) {
        let l = hypot(dx, dy)
        if l < 1e-6 { return (0, 0) }
        var sx = dx / l * speed - vx
        var sy = dy / l * speed - vy
        let m = hypot(sx, sy)
        if m > M.turn {
            sx = sx / m * M.turn
            sy = sy / m * M.turn
        }
        return (sx, sy)
    }

    private func advance(w: Double, h: Double, reducedMotion: Bool) {
        let t = traits
        let now = elapsed + M.step
        elapsed = now
        let A = reducedMotion ? pull * 0.4 : pull
        let H = home
        let hush = holding ? M.hush : 0
        let fx = fingerX
        let fy = fingerY
        // Bin everyone by sight-sized cells for the neighbour search (the same keys as the RN Map).
        let cols = Int(ceil(w / M.sight)) + 2
        var grid: [Int: [Int]] = [:]
        grid.reserveCapacity(64)
        for i in 0..<M.count {
            let key = Self.cellIndex(y[i] / M.sight + 1) * cols + Self.cellIndex(x[i] / M.sight + 1)
            grid[key, default: []].append(i)
        }
        let maxSpeed = M.cruise + (M.rush - M.cruise) * A
        let sight2 = M.sight * M.sight
        let space2 = M.space * M.space
        let S = sync
        for i in 0..<M.count {
            let px = x[i]
            let py = y[i]
            let pvx = vx[i]
            let pvy = vy[i]
            var sepX = 0.0, sepY = 0.0, aliX = 0.0, aliY = 0.0, cohX = 0.0, cohY = 0.0
            var seen = 0.0
            let cx = Self.cellIndex(px / M.sight + 1)
            let cy = Self.cellIndex(py / M.sight + 1)
            for oy in -1...1 {
                for ox in -1...1 {
                    guard let cell = grid[(cy + oy) * cols + cx + ox] else { continue }
                    for j in cell where j != i {
                        let dx = px - x[j]
                        let dy = py - y[j]
                        let d2 = dx * dx + dy * dy
                        if d2 > sight2 || d2 < 1e-6 { continue }
                        seen += 1
                        aliX += vx[j]
                        aliY += vy[j]
                        cohX += x[j]
                        cohY += y[j]
                        if d2 < space2 {
                            sepX += dx / d2
                            sepY += dy / d2
                        }
                    }
                }
            }
            var ax = 0.0
            var ay = 0.0
            let flock = 1 - 0.6 * A
            if sepX != 0 || sepY != 0 {
                let s = steer(sepX, sepY, pvx, pvy, maxSpeed)
                ax += s.0 * M.separate
                ay += s.1 * M.separate
            }
            if seen > 0 {
                let a = steer(aliX / seen, aliY / seen, pvx, pvy, maxSpeed)
                ax += a.0 * M.align * flock
                ay += a.1 * M.align * flock
                let c = steer(cohX / seen - px, cohY / seen - py, pvx, pvy, maxSpeed)
                ax += c.0 * M.cohere * flock
                ay += c.1 * M.cohere * flock
            }
            // Wander: a heading that drifts on two slow sines of its own.
            let heading = t.wanderA[i] + 1.7 * sin(2 * M.w0 * now + t.wanderB[i]) + 0.9 * sin(5 * M.w0 * now + t.wanderA[i])
            let wd = steer(cos(heading), sin(heading) * 0.7, pvx, pvy, M.cruise)
            ax += wd.0 * M.wander * (1 - A)
            ay += wd.1 * M.wander * (1 - A)
            // Stay over the meadow.
            if py < M.bandTop * h { ay += (M.bandTop * h - py) * M.edgePush }
            if py > M.bandBottom * h { ay -= (py - M.bandBottom * h) * M.edgePush }
            if px < M.margin { ax += (M.margin - px) * M.edgePush }
            if px > w - M.margin { ax -= (px - (w - M.margin)) * M.edgePush }
            // Orbit the finger, each on its own radius.
            if A > 0.001 && hush == 0 {
                let dx = px - fx
                let dy = py - fy
                let r = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
                let spin = t.spin[i]
                let radial = max(-M.rush, min(M.rush, (t.orbit[i] - r) * 2.4))
                let wantX = (-dy / r) * M.orbitSpeed * spin + (dx / r) * radial
                let wantY = (dx / r) * M.orbitSpeed * spin + (dy / r) * radial
                ax += (wantX - pvx) * 3 * A
                ay += (wantY - pvy) * 3 * A
            } else if hush > 0 {
                // Held still: only the far ones drift in, so the swarm stays spread for the wave.
                let dx = fx - px
                let dy = fy - py
                let r = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
                let inward = max(0, r - M.heldReach) * 0.6
                ax += dx / r * inward
                ay += dy / r * inward
            }
            ax -= pvx * hush
            ay -= pvy * hush
            // Held home: a critically damped spring to the hovering spot.
            if H > 0.001 {
                let kx = t.bobXK[i] * M.w0
                let ky = t.bobYK[i] * M.w0
                let tx = t.homeX[i] * w + t.bobX[i] * sin(kx * now + t.bobXP[i])
                let ty = t.homeY[i] * h + t.bobY[i] * sin(ky * now + t.bobYP[i])
                let tvx = t.bobX[i] * kx * cos(kx * now + t.bobXP[i])
                let tvy = t.bobY[i] * ky * cos(ky * now + t.bobYP[i])
                let hx = M.homeOmega * M.homeOmega * (tx - px) + 2 * M.homeOmega * (tvx - pvx)
                let hy = M.homeOmega * M.homeOmega * (ty - py) + 2 * M.homeOmega * (tvy - pvy)
                ax = ax * (1 - H) + hx * H
                ay = ay * (1 - H) + hy * H
            }
            var nvx = pvx + ax * M.step
            var nvy = pvy + ay * M.step
            // A flick may throw them faster for a moment; drag brings them back under the cap.
            let speed = hypot(nvx, nvy)
            let cap = maxSpeed + (1000 - maxSpeed) * H
            if speed > cap {
                let k = max(cap / speed, exp(-3 * M.step))
                nvx *= k
                nvy *= k
            }
            vx[i] = nvx
            vy[i] = nvy
            x[i] = px + nvx * M.step
            y[i] = py + nvy * M.step
            // Blink drift: pulled towards the travelling wave while synced, back to its own rhythm after.
            let own = t.blinkP[i] + (t.blinkK[i] / M.loop) * now
            var d = drift[i]
            if S > 0.001 {
                let dist = hypot(px - syncX, py - syncY)
                let wave = M.syncRate * now - dist / (M.waveSpeed / M.syncRate) * M.syncRate
                d += (S * M.syncPull * sin(M.tau * (wave - own - d))) / M.tau * M.step
            }
            // JavaScript's Math.round: halves go up.
            d -= (1 - S) * M.driftBack * (d - floor(d + 0.5)) * M.step
            drift[i] = d
        }

        // The grass under the swarm bends with the fireflies passing low over it.
        var push = Array(repeating: 0.0, count: bend.count)
        for i in 0..<M.count {
            let low = M.clamp01((y[i] - h * 0.6) / (h * 0.2))
            if low <= 0 { continue }
            let k = Self.cellIndex(x[i] / M.bucket)
            if k < 0 || k >= bend.count { continue }
            push[k] += vx[i] * low * M.bendGain
        }
        for k in bend.indices {
            let left = k > 0 ? push[k - 1] : 0
            let right = k + 1 < push.count ? push[k + 1] : 0
            let target = max(-M.bendMax, min(M.bendMax, push[k] + 0.4 * (left + right)))
            bendSpeed[k] += (-M.bendOmega * M.bendOmega * (bend[k] - target) - 2 * M.bendDamping * M.bendOmega * bendSpeed[k]) * M.step
            bend[k] += bendSpeed[k] * M.step
        }
    }

    /// Where autoPlay's finger is at `t` s into the loop (nil while it is up) and whether it is held still.
    private func scriptFinger(_ t: Double, _ w: Double, _ h: Double) -> (x: Double, y: Double, hold: Bool)? {
        let eight = (t - M.eightFrom) / M.eightTime
        if eight >= 0 && eight <= 1 {
            let a = eight * M.tau
            return (w * (0.5 + M.eightWidth * sin(a)), h * (M.eightY + M.eightHeight * sin(2 * a)), false)
        }
        let swipe = (t - M.eightFrom - M.eightTime) / M.swipeTime
        if swipe > 0 && swipe <= 1 {
            let e = swipe * swipe
            return (w * (0.5 + (M.swipeTo.x - 0.5) * e), h * (M.eightY + (M.swipeTo.y - M.eightY) * e), false)
        }
        let press = (t - M.pressFrom) / M.pressTime
        if press >= 0 && press <= 1 {
            return (w * M.pressAt.x, h * M.pressAt.y, t - M.pressFrom > M.holdTime)
        }
        return nil
    }

    /// Throws every firefly away from (x, y), harder the closer it is, a little along (dx, dy).
    private func scatter(_ ox: Double, _ oy: Double, _ dx: Double, _ dy: Double, reducedMotion: Bool) {
        let l = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
        for i in 0..<M.count {
            let px = x[i] - ox
            let py = y[i] - oy
            let r = hypot(px, py) == 0 ? 1 : hypot(px, py)
            let power = M.blast * exp(-r / M.blastReach) * (reducedMotion ? 0.3 : 1)
            vx[i] += px / r * power + dx / l * power * 0.35
            vy[i] += py / r * power + dy / l * power * 0.35
        }
        pull = 0
    }

    private func startSync(_ x: Double, _ y: Double) {
        holding = true
        syncX = x
        syncY = y
    }

    private func touched() {
        hold = M.resume
        home = 0
        if autoFinger {
            autoFinger = false
            holding = false
        }
    }

    // MARK: Touches

    /// A touch gathers the swarm round the finger at once; a long press (0.45 s within 14 pt)
    /// syncs the blinking; a release faster than 900 pt/s after a drag scatters it.
    func touchChanged(_ value: DragGesture.Value) {
        // A new touch while the last one never ended (a cancelled drag): start this one afresh.
        if touchStart != nil && value.startLocation != touchFrom { touchStart = nil }
        if touchStart == nil {
            touchStart = value.time
            touchFrom = value.startLocation
            panning = false
            pressFailed = false
            pressed = false
            touched()
            fingerDown = true
            light.impactOccurred()
        }
        touchAt = value.location
        fingerX = value.location.x
        fingerY = value.location.y
        let moved = hypot(value.location.x - touchFrom.x, value.location.y - touchFrom.y)
        if moved >= 2 { panning = true }
        if moved > M.holdSlop { pressFailed = true }
    }

    /// The drag's state reset: if no `onEnded` came for this touch (the system cancelled it), lift the
    /// finger so the swarm is not held round it forever and autoPlay can come back.
    func touchMaybeCancelled() {
        guard let start = touchStart else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.touchStart == start else { return }
            self.fingerDown = false
            self.holding = false
            self.touchStart = nil
            self.panning = false
        }
    }

    func touchEnded(_ value: DragGesture.Value, reducedMotion: Bool) {
        let speed = hypot(value.velocity.width, value.velocity.height)
        if panning && speed > M.flick && !holding {
            scatter(value.location.x, value.location.y, value.velocity.width, value.velocity.height, reducedMotion: reducedMotion)
            medium.impactOccurred()
        }
        fingerDown = false
        holding = false
        touchStart = nil
        panning = false
    }
}
