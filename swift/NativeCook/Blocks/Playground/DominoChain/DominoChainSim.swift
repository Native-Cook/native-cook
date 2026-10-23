import Foundation

// A run of dominoes standing on a table, each a rigid tile that tips about its leading bottom edge.
// A port, step for step, of the React Native `dominoSim.ts`.
//
// Everything is measured in table units (pt on the table plane). Tipping forward by φ, a domino
// turns about its front bottom edge under gravity: α = 3g / (2√(T² + H²)) · sin(φ − β₀), where
// β₀ = atan(T/H) is the lean it must pass before it falls by itself. A falling domino's top front edge
// meets the next one's back face when sin(φ − φₙ) = (d·cos φₙ − T) / H. If the next one is standing,
// the hit hands it a share of the falling one's spin and the falling one leans on it from then on.
// Laying a run, standing a fallen run back up and clearing it are animations on the same clock.

enum DominoChainSize {
    // A domino's thickness (along its path), height and width, in table units.
    static let t = 7.0
    static let h = 40.0
    static let w = 20.0
    // Distance between the centres of neighbouring dominoes on a path.
    static let spacing = 21.0
    static let maxDominoes = 96

    // Gravity, table units/s² (a gentle slow motion of the real thing at this scale).
    static let gravity = 2600.0
    // Share of the striking domino's spin the struck one starts with.
    static let transfer = 0.82
    // Share of a leaning domino's weight that pushes on the one it leans on.
    static let leanPush = 0.7
    // The last domino's bounce off the table: share of its spin it keeps, reversed.
    static let bounce = 0.18
    // A push that tips a domino over by itself, rad/s.
    static let push = 3.6

    // Popping up: each domino drops in from popDrop above over popTime s.
    static let popTime = 0.34
    static let popDrop = 34.0
    // Standing back up, in reverse: each domino takes riseTime s, riseStagger s after the one ahead of it.
    static let riseTime = 0.42
    static let riseStagger = 0.028
    // Clearing a run: each sinks over sinkTime s, sinkStagger s after the one before.
    static let sinkTime = 0.26
    static let sinkStagger = 0.012

    static let beta = atan(t / h)
    static let alpha = (3 * gravity) / (2 * (t * t + h * h).squareRoot())
    static let flat = Double.pi / 2
}

/// Ease out with a little overshoot.
func dominoChainEaseOutBack(_ t: Double) -> Double {
    let k = min(1, max(0, t))
    let c = 1.7
    let u = k - 1
    return 1 + (c + 1) * u * u * u + c * u * u
}

final class DominoChainRun {
    private typealias S = DominoChainSize

    /// 0 standing, 1 falling, 2 leaning on the next one, 3 flat on the table, 4 standing back up.
    static let standing: UInt8 = 0
    static let falling: UInt8 = 1
    static let leaning: UInt8 = 2
    static let down: UInt8 = 3
    static let rising: UInt8 = 4

    var count = 0
    var x = [Double](repeating: 0, count: S.maxDominoes)
    var z = [Double](repeating: 0, count: S.maxDominoes)
    /// Heading along the path (unit vector on the table).
    var hx = [Double](repeating: 0, count: S.maxDominoes)
    var hz = [Double](repeating: 0, count: S.maxDominoes)
    /// Distance to the next domino's centre.
    var gap = [Double](repeating: 0, count: S.maxDominoes)
    var tilt = [Double](repeating: 0, count: S.maxDominoes)
    var spin = [Double](repeating: 0, count: S.maxDominoes)
    var state = [UInt8](repeating: 0, count: S.maxDominoes)
    /// When it popped up, when it starts to sink (infinity when it stays), and its tilt when the rise began.
    var born = [Double](repeating: 0, count: S.maxDominoes)
    var sink = [Double](repeating: .infinity, count: S.maxDominoes)
    var riseFrom = [Double](repeating: 0, count: S.maxDominoes)
    var riseAt = [Double](repeating: 0, count: S.maxDominoes)
    /// The pips on each half of its face, 0…6.
    var pipsA = [Int](repeating: 0, count: S.maxDominoes)
    var pipsB = [Int](repeating: 0, count: S.maxDominoes)
    /// The run's own clock, s.
    var time = 0.0
    /// Hits so far (for a click every few).
    var hits = 0
    /// The last rise was gentle (reduced motion): no stagger, no overshoot.
    var gentle = false
    var seed: UInt32 = 0x2d0e1f

    /// A small seeded random in [0, 1): xorshift32 on the run's seed.
    private func random() -> Double {
        var s = seed
        s ^= s << 13
        s ^= s >> 17
        s ^= s << 5
        seed = s
        return Double(s) / 4294967296
    }

    /// Sinks every domino into the table, first to last; returns when the last one is gone.
    @discardableResult
    func sinkAll() -> Double {
        var end = time
        for i in 0..<count {
            let at = time + Double(i) * S.sinkStagger
            if sink[i] > at { sink[i] = at }
            end = max(end, sink[i] + S.sinkTime)
        }
        return end
    }

    /// Stands a domino up at (x, z) facing (hx, hz), popping up `delay` s from now.
    @discardableResult
    func add(_ x: Double, _ z: Double, _ hx: Double, _ hz: Double, delay: Double) -> Bool {
        if count >= S.maxDominoes { return false }
        let i = count
        self.x[i] = x
        self.z[i] = z
        self.hx[i] = hx
        self.hz[i] = hz
        tilt[i] = 0
        spin[i] = 0
        state[i] = 0
        born[i] = time + delay
        sink[i] = .infinity
        gap[i] = .infinity
        pipsA[i] = Int((random() * 7).rounded(.down))
        pipsB[i] = Int((random() * 7).rounded(.down))
        if i > 0 { gap[i - 1] = hypot(x - self.x[i - 1], z - self.z[i - 1]) }
        count = i + 1
        return true
    }

    /// Re-aims a domino's heading (used while a path is drawn, once the next point is known).
    func aim(_ i: Int, _ hx: Double, _ hz: Double) {
        self.hx[i] = hx
        self.hz[i] = hz
    }

    /// Tips domino `i` forward with spin `w`, if it is standing.
    func push(_ i: Int, _ w: Double) -> Bool {
        if i < 0 || i >= count || state[i] != 0 { return false }
        state[i] = 1
        spin[i] = w
        return true
    }

    /// Whether any domino is down or on its way down.
    var anyFallen: Bool {
        for i in 0..<count where state[i] != 0 { return true }
        return false
    }

    /// Whether something is still moving (falling, rising, popping up or sinking).
    var busy: Bool {
        for i in 0..<count {
            let s = state[i]
            if s == 1 || s == 4 { return true }
            if time < born[i] + S.popTime { return true }
            if sink[i] != .infinity { return true }
        }
        return false
    }

    /// Stands every fallen domino back up, in reverse: the last one first (all at once when `gentle`).
    func raise(gentle: Bool) {
        var k = 0
        self.gentle = gentle
        var i = count - 1
        while i >= 0 {
            if state[i] != 0 {
                state[i] = 4
                riseFrom[i] = tilt[i]
                riseAt[i] = time + (gentle ? 0 : Double(k) * S.riseStagger)
                spin[i] = 0
                k += 1
            }
            i -= 1
        }
    }

    /// The tilt at which domino i, leaning forward, touches the next one tilted by `next`.
    private func leanOn(_ i: Int, _ next: Double) -> Double {
        let s = (gap[i] * cos(next) - S.t) / S.h
        if s >= 1 { return S.flat + 1 }
        return next + asin(max(-1, s))
    }

    /// One fixed step of the run.
    func step(_ dt: Double) {
        time += dt
        // Back to front, so each domino sees where the one ahead of it is this step.
        var i = count - 1
        while i >= 0 {
            defer { i -= 1 }
            let st = state[i]
            if st == 1 {
                // Its own weight, plus the weight of the one leaning on it from behind, which pushes it on.
                var torque = sin(tilt[i] - S.beta)
                if i > 0 && state[i - 1] == 2 { torque += S.leanPush * max(0, sin(tilt[i - 1] - S.beta)) }
                var w = spin[i] + S.alpha * torque * dt
                var a = tilt[i] + w * dt
                // Not pushed hard enough: it rocks back onto its base.
                if a <= 0 {
                    a = 0
                    w = 0
                    state[i] = 0
                }
                let hasNext = i + 1 < count && sink[i + 1] == .infinity
                let contact = hasNext ? leanOn(i, tilt[i + 1]) : S.flat + 1
                if hasNext && contact <= S.flat && a >= contact {
                    a = contact
                    state[i] = 2
                    hits += 1
                    if state[i + 1] == 0 {
                        state[i + 1] = 1
                        spin[i + 1] = max(w * S.transfer, 1)
                    }
                    w = 0
                } else if a >= S.flat {
                    a = S.flat
                    w = -w * S.bounce
                    if abs(w) < 0.3 {
                        w = 0
                        state[i] = 3
                    }
                    hits += 1
                }
                tilt[i] = a
                spin[i] = w
            } else if st == 2 {
                if i + 1 < count {
                    tilt[i] = min(S.flat, leanOn(i, tilt[i + 1]))
                } else {
                    // Nothing left to lean on: it falls the rest of the way.
                    state[i] = 1
                }
            } else if st == 4 {
                let p = (time - riseAt[i]) / S.riseTime
                if p >= 1 {
                    tilt[i] = 0
                    state[i] = 0
                } else if p > 0 {
                    tilt[i] = riseFrom[i] * (1 - (gentle ? p * (2 - p) : dominoChainEaseOutBack(p)))
                }
            }
        }
        // Drop the dominoes that have sunk away for good.
        if count > 0 && sink[0] != .infinity && time >= sink[count - 1] + S.sinkTime { count = 0 }
    }

    /// Height a domino is drawn at (popping in from above), its scale and opacity.
    func pop(_ i: Int) -> (lift: Double, scale: Double, alpha: Double) {
        let p = (time - born[i]) / S.popTime
        var lift = 0.0
        var scale = 1.0
        var alpha = 1.0
        if p < 1 {
            let k = max(0, p)
            let e = dominoChainEaseOutBack(k)
            lift = S.popDrop * (1 - k) * (1 - k)
            scale = 0.7 + 0.3 * e
            alpha = min(1, k * 3)
        }
        let q = (time - sink[i]) / S.sinkTime
        if q > 0 {
            let k = min(1, q)
            scale *= 1 - 0.3 * k
            alpha *= 1 - k
            lift -= 6 * k
        }
        return (lift, scale, alpha)
    }
}

// MARK: - Paths

enum DominoChainPath {
    /// Points of a spiral on the table, from its outer end inwards (table units).
    static func spiral(cx: Double, cz: Double, outer: Double, inner: Double, turns: Double, start: Double) -> (xs: [Double], zs: [Double]) {
        var xs: [Double] = []
        var zs: [Double] = []
        let steps = 360
        for k in 0...steps {
            let t = Double(k) / Double(steps)
            let r = outer + (inner - outer) * t
            let a = start + t * turns * Double.pi * 2
            xs.append(cx + cos(a) * r)
            zs.append(cz + sin(a) * r)
        }
        return (xs, zs)
    }

    /// Where along a polyline arc length `s` falls: the point and the heading there.
    static func point(_ xs: [Double], _ zs: [Double], _ s: Double) -> (x: Double, z: Double, hx: Double, hz: Double) {
        let n = min(xs.count, zs.count)
        if n == 0 { return (0, 0, 1, 0) }
        var left = s
        var k = 1
        while k < n {
            let dx = xs[k] - xs[k - 1]
            let dz = zs[k] - zs[k - 1]
            let len = hypot(dx, dz)
            if left <= len || k == n - 1 {
                let u = len > 0 ? min(1, left / len) : 0
                return (xs[k - 1] + dx * u, zs[k - 1] + dz * u, len > 0 ? dx / len : 1, len > 0 ? dz / len : 0)
            }
            left -= len
            k += 1
        }
        return (xs[0], zs[0], 1, 0)
    }

    /// Length of a polyline.
    static func length(_ xs: [Double], _ zs: [Double]) -> Double {
        var total = 0.0
        var k = 1
        let n = min(xs.count, zs.count)
        while k < n {
            total += hypot(xs[k] - xs[k - 1], zs[k] - zs[k - 1])
            k += 1
        }
        return total
    }
}
