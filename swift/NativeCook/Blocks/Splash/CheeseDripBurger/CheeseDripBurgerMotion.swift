import Foundation

/// Timing and the cheese model of Cheese Drip Burger. Every value is a pure function of the loop
/// time `t` (0 ..< loop) and, for touch, of the clock; nothing accumulates per frame.
enum CheeseDripBurgerMotion {
    static let loop = 8.0

    // MARK: Helpers

    static func clamp(_ v: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(v, a), b) }
    static func mod(_ a: Double, _ b: Double) -> Double { let r = a.truncatingRemainder(dividingBy: b); return r < 0 ? r + b : r }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func lerp(_ a: Double, _ b: Double, _ f: Double) -> Double { a + (b - a) * f }
    static func hash(_ i: Double) -> Double { let s = sin(i * 12.9898 + 78.233) * 43758.5453; return s - floor(s) }

    // MARK: Cheese layout (burger space: points inside the 372 pt image square, origin top-left)

    static let imageSize = 372.0

    /// One slice of cheese: its front edge is the line y(x) = y + slope·(x − cx) (measured on the
    /// photo), with a slight bow of `sag`.
    struct Layer {
        let cx: Double, half: Double, y: Double, slope: Double, sag: Double
        let band: Double // drape thickness at rest (pt)
        let drips: [Drip]
        func edge(_ x: Double) -> Double { let u = (x - cx) / half; return y + slope * (x - cx) + sag * (1 - u * u) }
    }

    /// A drip hanging from the slice edge at u (−1 … 1): its width, rest and peak length.
    struct Drip {
        let u: Double, w: Double, rest: Double, peak: Double
    }

    /// Index 0: the lower slice (between the patties); 1: the upper slice (under the lettuce).
    static let layers: [Layer] = [
        Layer(cx: 173, half: 132, y: 261, slope: 0.06, sag: 1.5, band: 9, drips: [
            Drip(u: -0.98, w: 20, rest: 16, peak: 44),
            Drip(u: -0.66, w: 30, rest: 22, peak: 80),
            Drip(u: -0.36, w: 22, rest: 10, peak: 24),
            Drip(u: -0.06, w: 34, rest: 26, peak: 70),
            Drip(u: 0.26, w: 24, rest: 14, peak: 56),
            Drip(u: 0.52, w: 30, rest: 20, peak: 80),
            Drip(u: 0.76, w: 20, rest: 10, peak: 24),
            Drip(u: 0.98, w: 20, rest: 15, peak: 50),
        ]),
        Layer(cx: 196, half: 114, y: 218, slope: -0.026, sag: 1.5, band: 8, drips: [
            Drip(u: -0.97, w: 18, rest: 12, peak: 40),
            Drip(u: -0.60, w: 28, rest: 18, peak: 56),
            Drip(u: -0.25, w: 20, rest: 9, peak: 34),
            Drip(u: 0.08, w: 30, rest: 20, peak: 58),
            Drip(u: 0.42, w: 22, rest: 11, peak: 40),
            Drip(u: 0.72, w: 26, rest: 16, peak: 54),
            Drip(u: 0.98, w: 18, rest: 12, peak: 38),
        ]),
    ]

    /// The puddle at the base of the bun.
    static let puddleY = 358.0
    static let puddleCX = 187.0

    // MARK: Loop timeline

    /// Melt of one drip (0 rest … 1 peak): builds 1.6 → 4.3 s, holds, sets back 5.0 → 7.0 s.
    static func meltLoop(_ t: Double, delay: Double) -> Double {
        let up = easeInOutCubic(seg(t, 1.6 + delay, 4.3 + delay * 0.5))
        let down = easeInOutCubic(seg(t, 5.0 + delay * 0.6, 7.0))
        return up * (1 - down)
    }

    /// Per-drip delay, seeded.
    static func delay(layer: Int, drip: Int) -> Double { hash(Double(layer * 17 + drip) + 3) * 0.35 }

    static let rows = 3

    /// How lit row i of the giant word stack is (0 faint … 1 yellow), and how far it still slides.
    static func rowLit(_ t: Double, _ i: Int) -> Double {
        let up = easeOutCubic(seg(t, 1.2 + 0.09 * Double(i), 1.75 + 0.09 * Double(i)))
        let down = easeInOutCubic(seg(t, 3.5 + 0.07 * Double(i), 4.3 + 0.07 * Double(i)))
        return up * (1 - down)
    }

    static func rowSlide(_ t: Double, _ i: Int) -> Double {
        let up = easeOutCubic(seg(t, 1.2 + 0.09 * Double(i), 1.75 + 0.09 * Double(i)))
        return (1 - up) * 70 * (i % 2 == 0 ? -1 : 1)
    }

    /// A drop: the drip at `drip` of `layer` necks down and lets go of its bulb at `t0`.
    struct Event { let layer: Int; let drip: Int; let t0: Double }

    static let loopEvents = [
        Event(layer: 0, drip: 2, t0: 4.15),
        Event(layer: 0, drip: 6, t0: 4.6),
    ]

    static let pinchLead = 0.6
    static let eventTail = 1.4
    static let gravity = 1100.0

    // MARK: Drop-in (tap)

    static let fallTime = 0.42
    static let fallFrom = -560.0

    /// Burger offset (pt) and scale (x, y) at τ s after a tap; (0, 1, 1) at rest.
    static func dropIn(_ tau: Double) -> (y: Double, sx: Double, sy: Double) {
        if tau < 0 || tau > 2.5 { return (0, 1, 1) }
        if tau < fallTime {
            let f = tau / fallTime
            return (fallFrom * (1 - f * f), 1 - 0.04 * f, 1 + 0.05 * f)
        }
        let s = exp(-6.9 * (tau - fallTime)) * cos(10.5 * (tau - fallTime))
        return (0, 1 + 0.09 * s, 1 - 0.12 * s)
    }

    /// Extra drip length (pt) after the impact: the cheese jiggles.
    static func jiggle(_ tau: Double) -> Double {
        let d = tau - fallTime
        if d < 0 || d > 2.0 { return 0 }
        return 16 * exp(-4.5 * d) * sin(13 * d)
    }

    // MARK: Cheese geometry for one frame

    struct DripShape { var x, y0, w, length, r, neck: Double }
    struct DropShape { var x, y, rx, ry: Double }
    struct Bump { var layer: Int; var x, amp: Double } // layer 2 = the puddle

    struct Frame {
        var drips: [[DripShape]] = [[], []]
        var drops: [DropShape] = []
        var bumps: [Bump] = []
        var melt: Double = 0 // the mean melt, for the band and the puddle
    }

    /// Drip length at melt m with its ambient wobble.
    static func dripLength(_ d: Drip, m: Double, ambient: Double, seed: Double) -> Double {
        let k = 1 + floor(hash(seed) * 3)
        let wob = 1.2 * sin(2 * Double.pi * k * ambient / loop + seed * 6.1) * (0.4 + m)
        return d.rest + (d.peak - d.rest) * m + wob
    }

    /// The whole cheese at one moment. `melts[layer][drip]` comes from the loop and touch,
    /// `events` are (event, seconds since its t0).
    static func frame(melts: [[Double]], events: [(Event, Double)], ambient: Double, jiggle jig: Double) -> Frame {
        var f = Frame()
        var sum = 0.0, n = 0.0
        for (li, layer) in layers.enumerated() {
            var shapes: [DripShape] = []
            for (di, d) in layer.drips.enumerated() {
                let m = melts[li][di]
                sum += m; n += 1
                let seed = Double(li * 31 + di)
                let x = layer.cx + d.u * layer.half
                var length = dripLength(d, m: m, ambient: ambient, seed: seed) + jig * (0.5 + hash(seed + 9))
                var r = d.w * 0.5 * (0.8 + 0.12 * m)
                var neck = 0.35 * m
                for (e, dt) in events where e.layer == li && e.drip == di {
                    if dt < 0 {
                        let q = easeInCubic(clamp((dt + pinchLead) / pinchLead))
                        length += 18 * q
                        r *= 1 + 0.25 * q
                        neck = lerp(neck, 0.92, q)
                    } else {
                        // The bulb has gone: the drip snaps back and regrows.
                        let back = 0.5 * exp(-4.5 * dt) * cos(8 * dt)
                        length *= 1 - max(0, back)
                        neck = lerp(neck, 0.5, exp(-6 * dt))
                    }
                }
                shapes.append(DripShape(x: x, y0: layer.edge(x), w: d.w, length: max(length, 3), r: r, neck: neck))
            }
            f.drips[li] = shapes
        }
        f.melt = n > 0 ? sum / n : 0

        // Falling drops and their landing bumps.
        for (e, dt) in events where dt >= 0 {
            let layer = layers[e.layer]
            let d = layer.drips[e.drip]
            let m = melts[e.layer][e.drip]
            let seed = Double(e.layer * 31 + e.drip)
            let x = layer.cx + d.u * layer.half
            let r0 = d.w * 0.5 * (0.8 + 0.12 * m) * 1.18
            let start = layer.edge(x) + dripLength(d, m: m, ambient: ambient, seed: seed) + 18
            let target: Double
            let targetLayer: Int
            if e.layer == 1 {
                let below = layers[0]
                target = below.edge(x) + below.band + 4
                targetLayer = 0
            } else {
                target = puddleY + 2
                targetLayer = 2
            }
            // y = start + v0·dt + g·dt²/2 reaches target − r0 at dtLand.
            let v0 = 20.0
            let dist = max(0, target - r0 - start)
            let dtLand = (-v0 + sqrt(v0 * v0 + 2 * gravity * dist)) / gravity
            if dt < dtLand {
                let y = start + v0 * dt + 0.5 * gravity * dt * dt
                let v = v0 + gravity * dt
                let st = min(v / 1100, 0.45)
                f.drops.append(DropShape(x: x, y: y, rx: r0 / sqrt(1 + st), ry: r0 * (1 + st)))
            } else {
                let dl = dt - dtLand
                let sink = easeOutCubic(clamp(dl / 0.2))
                if sink < 1 {
                    let rr = r0 * (1 - sink)
                    f.drops.append(DropShape(x: x, y: target - r0 + sink * r0 * 0.6, rx: rr * (1 + 0.6 * sink), ry: rr))
                }
                let env = dl < 0.08 ? dl / 0.08 : exp(-(dl - 0.08) * 3.2)
                f.bumps.append(Bump(layer: targetLayer, x: x, amp: 6 * env * (e.layer == 0 ? 1.3 : 1)))
            }
        }
        return f
    }
}

/// Touch state, read by the timeline; a plain class, so writing it never re-renders the view.
final class CheeseDripBurgerTouch {
    typealias M = CheeseDripBurgerMotion

    var touching = false
    private var running = true // the loop runs (autoPlay and no touch in the last 2 s)
    private var base = 0.0 // clock at loop time 0
    private var pressClock = -100.0
    private var releaseClock = -100.0
    private var carry: [[Double]] = M.layers.map { $0.drips.map { _ in 0 } }
    private var released: [[Double]] = M.layers.map { $0.drips.map { _ in 0 } }
    private var rowCarry: [Double] = Array(repeating: 0, count: M.rows)
    private(set) var events: [M.Event] = [] // clock-based drops
    private var nextDrop = 0.0
    private var dropCursor = 0
    private(set) var dropInClock = -100.0

    static let heatMax = 1.35
    static let idle = 2.0
    /// The long drips that take turns letting go while the finger holds.
    static let holdOrder: [(Int, Int)] = [(0, 2), (0, 5), (0, 6), (0, 1), (0, 4), (0, 0), (0, 7), (0, 3)]

    /// Loop time; resumes at 0 two seconds after the last touch.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double? {
        guard autoPlay else { return nil }
        if !running {
            if touching || clock < releaseClock + Self.idle { return nil }
            running = true
            base = releaseClock + Self.idle
        }
        return M.mod(clock - base, M.loop)
    }

    private func heat(_ clock: Double) -> Double {
        touching ? Self.heatMax * (1 - exp(-0.8 * (clock - pressClock))) : 0
    }

    private func setDecay(_ clock: Double) -> Double { exp(-1.1 * max(0, clock - releaseClock)) }

    /// Melt of every drip at a clock time.
    func melts(_ clock: Double, t: Double?) -> [[Double]] {
        let h = heat(clock)
        let decay = setDecay(clock)
        return M.layers.enumerated().map { li, layer in
            layer.drips.indices.map { di in
                let loopM = t.map { M.meltLoop($0, delay: M.delay(layer: li, drip: di)) } ?? 0
                if touching { return max(carry[li][di], h) }
                return max(loopM, released[li][di] * decay)
            }
        }
    }

    func rowLit(_ clock: Double, t: Double?, _ i: Int) -> Double {
        let loopL = t.map { M.rowLit($0, i) } ?? 0
        return max(loopL, rowCarry[i] * exp(-2.5 * max(0, clock - pressClock)))
    }

    /// Active drops: loop events from the loop time, touch events from the clock.
    func activeEvents(_ clock: Double, t: Double?) -> [(M.Event, Double)] {
        var out: [(M.Event, Double)] = []
        if let t {
            for e in M.loopEvents {
                let dt = t - e.t0
                if dt > -M.pinchLead && dt < M.eventTail { out.append((e, dt)) }
            }
        }
        for e in events {
            let dt = clock - e.t0
            if dt > -M.pinchLead && dt < M.eventTail { out.append((e, dt)) }
        }
        return out
    }

    /// Schedules the next drop while the finger holds; returns true when one was just scheduled.
    func update(_ clock: Double) -> Bool {
        events.removeAll { clock - $0.t0 > M.eventTail }
        guard touching, clock - pressClock > 0.9, clock >= nextDrop - M.pinchLead else { return false }
        let (l, d) = Self.holdOrder[dropCursor % Self.holdOrder.count]
        dropCursor += 1
        events.append(M.Event(layer: l, drip: d, t0: nextDrop))
        nextDrop += 0.85
        return true
    }

    func begin(clock: Double, autoPlay: Bool) {
        guard !touching else { return }
        let t = loopTime(clock, autoPlay: autoPlay)
        carry = melts(clock, t: t)
        rowCarry = (0..<M.rows).map { rowLit(clock, t: t, $0) }
        // Drops already under way keep going on the clock.
        if let t {
            for e in M.loopEvents where t - e.t0 > -M.pinchLead && t - e.t0 < M.eventTail {
                events.append(M.Event(layer: e.layer, drip: e.drip, t0: clock + (e.t0 - t)))
            }
        }
        touching = true
        running = false
        pressClock = clock
        nextDrop = clock + 1.5
    }

    func end(clock: Double) {
        guard touching else { return }
        released = melts(clock, t: nil)
        touching = false
        releaseClock = clock
    }

    func dropIn(clock: Double) {
        dropInClock = clock
    }

    /// Clock time after which nothing moves any more (outside autoPlay).
    var restsAfter: Double {
        max(releaseClock + 6, dropInClock + 2.6, (events.map(\.t0).max() ?? 0) + M.eventTail)
    }
}
