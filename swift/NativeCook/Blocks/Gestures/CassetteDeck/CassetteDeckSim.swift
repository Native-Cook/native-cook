import Foundation

enum CassetteDeckHaptic {
    case click, latch, release, commit, tick
}

/// Autoplay runs the script; fingers own the deck after a touch; returning eases it to rest before the script resumes.
enum CassetteDeckMode {
    case autoplay, fingers, returning
}

/// The deck as one fixed-step simulation, shared by the fingers and the autoplay script.
/// A plain value, so the same code runs live and in the dry run that times the loop.
struct CassetteDeckSim {
    private typealias Motion = CassetteDeckMotion

    static let keyCount = 5
    static let slotCount = 3
    static let autoplayStepCount = Int(jsRound(Motion.loopMS / 1000 / Motion.step))
    /// A hub has six teeth: it looks the same every 60°.
    private static let sector = Double.pi / 3
    private static let startProgress = [0.32, 0.58, 0.18]

    // Pager, in cassette widths (one screen width each).
    var p = 0.0
    var pv = 0.0
    var target = 0.0
    var dragging = false
    var dragStart = 0.0
    // Keys.
    var d = [Double](repeating: 0, count: Self.keyCount)
    var dv = [Double](repeating: 0, count: Self.keyCount)
    /// True while a finger (or the script) holds the key.
    var held = [Bool](repeating: false, count: Self.keyCount)
    /// True while the key can click at the bottom.
    var armed = [Bool](repeating: true, count: Self.keyCount)
    /// True when the finger has left but the stroke has not reached the bottom yet (a quick tap still clicks).
    var lift = [Bool](repeating: false, count: Self.keyCount)
    var latched = false
    // Transport and reels.
    var v = 0.0
    var w = [Double](repeating: 0, count: Self.slotCount)
    var ang = [Double](repeating: 0, count: Self.slotCount * 2)
    var k = [Double](repeating: 1, count: Self.slotCount * 2)
    var prog = Self.startProgress
    // Lights.
    var led = 0.0
    var lit = [Double](repeating: 0, count: Self.keyCount)
    // Scrubbing a reel by hand.
    var scrubbing = false
    var scrubV = 0.0
    var tooth = 0.0
    // Chassis dip and cassette rattle (pt).
    var dip = 0.0
    var dipV = 0.0
    var rattle = 0.0
    var rattleV = 0.0
    var clock = 0.0
    /// Slot that last seated in the tray, and the simulation time of the last scrub haptic.
    var seated = 0
    var tickAt = -1.0
    /// Reduce Motion cross-fade after a track change.
    var fade = 1.0
    var reduced = false
    // Clock.
    var acc = 0.0
    /// Steps into the autoplay loop.
    var step = 0
    var mode = CassetteDeckMode.autoplay
    /// Seconds since the last touch.
    var idle = 0.0
    var events: [CassetteDeckHaptic] = []

    /// Puts every moving part back at rest, keeping the reels' angles (the loop's seam).
    mutating func resetToRest() {
        p = 0
        pv = 0
        target = 0
        dragging = false
        dragStart = 0
        for i in 0..<Self.keyCount {
            d[i] = 0
            dv[i] = 0
            held[i] = false
            armed[i] = true
            lift[i] = false
            lit[i] = 0
        }
        latched = false
        v = 0
        led = 0
        fade = 1
        scrubbing = false
        scrubV = 0
        dip = 0
        dipV = 0
        rattle = 0
        rattleV = 0
        clock = 0
        seated = 0
        tickAt = -1
        for j in 0..<Self.slotCount {
            w[j] = 0
            prog[j] = Self.startProgress[j]
        }
        for i in 0..<(Self.slotCount * 2) {
            ang[i] = ang[i].truncatingRemainder(dividingBy: Double.pi * 2)
        }
    }

    static func tapeRadius(_ progress: Double) -> Double {
        let r = CassetteDeckTheme.Layout.tapePack
        return r.min + (r.max - r.min) * progress
    }

    /// JavaScript's `Math.round`: halves go up.
    static func jsRound(_ x: Double) -> Double {
        (x + 0.5).rounded(.down)
    }

    /// The slot whose cassette sits in the deck, or -1 while none is close enough.
    var loadedSlot: Int {
        let n = Self.jsRound(p)
        if !n.isFinite || abs(p - n) > Motion.Pager.loadedWithin { return -1 }
        return Self.wrap(Int(n))
    }

    /// The slot nearest the deck, loaded or not.
    var nearestSlot: Int {
        let n = Self.jsRound(p)
        return n.isFinite ? Self.wrap(Int(n)) : 0
    }

    private static func wrap(_ n: Int) -> Int {
        ((n % slotCount) + slotCount) % slotCount
    }

    /// x of a slot in cassette widths, wrapped into [-1, 2) so three cassettes make an endless strip.
    func slotX(_ j: Int) -> Double {
        let n = Double(Self.slotCount)
        let wrapped = (((Double(j) - p + 1).truncatingRemainder(dividingBy: n)) + n).truncatingRemainder(dividingBy: n)
        return wrapped - 1
    }

    private mutating func act(_ i: Int) {
        if i == 0 && !latched {
            latched = true
            events.append(.latch)
            return
        }
        if i == 1 && latched {
            latched = false
            events.append(.release)
        }
        if i == 2 {
            target = Self.jsRound(target) + 1
            if reduced {
                p = target
                pv = 0
                fade = 0
            }
        }
        events.append(.click)
    }

    private static func relax(_ x: Double, to goal: Double, tau: Double, dt: Double) -> Double {
        x + (goal - x) * (1 - exp(-dt / tau))
    }

    private static func sign(_ x: Double) -> Double {
        x > 0 ? 1 : (x < 0 ? -1 : 0)
    }

    mutating func advance(_ dt: Double, live: Bool) {
        stepKeys(dt)
        stepTransport(dt)
        // The slot is found once per step, before the pager moves, and the tooth tick below uses the same one.
        let loaded = loadedSlot
        // An arriving cassette seats with a small clack driven by its page velocity.
        if loaded != seated {
            if loaded >= 0 && !reduced {
                rattleV += min(1, abs(pv)) * Motion.Chassis.seatKick
            }
            seated = loaded
        }
        stepReels(dt, loaded: loaded, live: live)

        // Pager spring (the finger owns it while dragging).
        if !dragging {
            pv += (Motion.Pager.stiffness * (target - p) - Motion.Pager.damping * pv) * dt
            p += pv * dt
        }
        let slots = Double(Self.slotCount)
        if target >= slots && p >= slots - 1 {
            target -= slots
            p -= slots
            dragStart -= slots
        }
        if target <= -slots && p <= 1 - slots {
            target += slots
            p += slots
            dragStart += slots
        }

        // A tick per hub tooth while the finger turns the reel.
        if loaded >= 0 {
            let current = (ang[loaded * 2] / Self.sector).rounded(.down)
            if current != tooth {
                if scrubbing && clock - tickAt >= Motion.Scrub.tickMinGap {
                    events.append(.tick)
                    tickAt = clock
                }
                tooth = current
            }
        }

        // Chassis dip and cassette rattle: two springs excited by key impacts.
        dipV += (-Motion.Chassis.dipStiffness * dip - Motion.Chassis.dipDamping * dipV) * dt
        dip += dipV * dt
        rattleV += (-Motion.Chassis.rattleStiffness * rattle - Motion.Chassis.rattleDamping * rattleV) * dt
        rattle += rattleV * dt
        clock += dt

        // Lights.
        led = Self.relax(led, to: latched || abs(v) > 0.05 ? 1 : 0, tau: Motion.Light.ledTau, dt: dt)
        for i in 0..<Self.keyCount {
            let on = i == 0 ? latched : (d[i] > 0.5 && !armed[i])
            let tau = on ? Motion.Light.glyphOnTau : Motion.Light.glyphOffTau
            lit[i] = Self.relax(lit[i], to: on ? 1 : 0, tau: tau, dt: dt)
        }
        fade = min(1, fade + dt / 0.18)
    }

    private mutating func stepKeys(_ dt: Double) {
        for i in 0..<Self.keyCount {
            if lift[i] && !armed[i] {
                held[i] = false
                lift[i] = false
            }
            let down = held[i]
            let goal = down ? 1.0 : (i == 0 && latched ? Motion.Key.latchDepth : 0.0)
            let stiffness = down ? Motion.Key.pressStiffness : Motion.Key.returnStiffness
            let damping = down ? Motion.Key.pressDamping : Motion.Key.returnDamping
            dv[i] += (stiffness * (goal - d[i]) - damping * dv[i]) * dt
            d[i] += dv[i] * dt
            if d[i] >= 1 {
                d[i] = 1
                if dv[i] > 0 {
                    if armed[i] && down {
                        armed[i] = false
                        act(i)
                    }
                    // The impact pushes the chassis down and shakes the cassette in its tray.
                    if !reduced {
                        let hit = dv[i] * CassetteDeckTheme.Layout.keys.travel
                        dipV += hit * Motion.Chassis.dipImpulse * Motion.Chassis.dipStiffness * 0.05
                        rattleV += hit * Motion.Chassis.rattleImpulse * Motion.Chassis.rattleStiffness * 0.05
                    }
                    dv[i] = -dv[i] * Motion.Key.bottomRestitution
                }
            }
            if d[i] <= 0 {
                d[i] = 0
                if dv[i] < 0 { dv[i] = -dv[i] * Motion.Key.topRestitution }
            }
            if !down && d[i] < Motion.Key.rearm { armed[i] = true }
        }
    }

    /// Winding while rewind / fast-forward is held at the bottom, else play speed while latched.
    private mutating func stepTransport(_ dt: Double) {
        let rewinding = held[3] && !armed[3]
        let forwarding = held[4] && !armed[4]
        let goal = forwarding ? Motion.Transport.wind : (rewinding ? -Motion.Transport.wind : (latched ? 1.0 : 0.0))
        let speeding = abs(goal) > abs(v) && goal * v >= 0
        if scrubbing {
            v = scrubV
        } else if speeding {
            v = Self.relax(v, to: goal, tau: Motion.Transport.spinUpTau, dt: dt)
        } else {
            let slowing = (Motion.Transport.brake + Motion.Transport.friction * abs(v)) * dt
            let gap = goal - v
            v += abs(gap) <= slowing ? gap : Self.sign(gap) * slowing
        }
    }

    /// The loaded cassette's hubs follow the capstan, ejected ones coast down. ω = v / r.
    private mutating func stepReels(_ dt: Double, loaded: Int, live: Bool) {
        for j in 0..<Self.slotCount {
            if j == loaded {
                w[j] = Self.relax(w[j], to: v, tau: Motion.Transport.engageTau, dt: dt)
            } else {
                let slowing = (Motion.Transport.brake + Motion.Transport.friction * abs(w[j])) * dt
                w[j] = abs(w[j]) <= slowing ? 0 : w[j] - Self.sign(w[j]) * slowing
            }
            if reduced { continue }
            let leftRadius = Self.tapeRadius(1 - prog[j])
            let rightRadius = Self.tapeRadius(prog[j])
            let base = w[j] * Motion.Transport.baseOmega * Motion.Transport.refRadius
            ang[j * 2] += (base / leftRadius) * dt * k[j * 2]
            ang[j * 2 + 1] += (base / rightRadius) * dt * k[j * 2 + 1]
            if live { prog[j] = max(0, min(1, prog[j] + w[j] * Motion.Transport.progressRate * dt)) }
        }
    }

    mutating func dragTo(_ page: Double, velocity vp: Double, done: Bool) {
        if !done {
            if !dragging {
                dragging = true
                dragStart = Self.jsRound(p)
            }
            p = page
            pv = vp
            return
        }
        dragging = false
        pv = vp
        let projected = p + vp * Motion.Pager.momentum
        let t = max(dragStart - 1, min(dragStart + 1, Self.jsRound(projected)))
        if t != dragStart { events.append(.commit) }
        target = t
        if reduced {
            p = t
            pv = 0
            if t != dragStart { fade = 0 }
        }
    }

    /// A finger on a key. Lifting before the cap reaches the bottom finishes the stroke first, so a quick tap clicks.
    mutating func pressKey(_ i: Int, down: Bool) {
        if down {
            held[i] = true
            lift[i] = false
            return
        }
        if !armed[i] || d[i] >= 1 {
            held[i] = false
            lift[i] = false
        } else {
            lift[i] = true
        }
    }

    /// The finger turning a reel: ω (rad/s) converted to tape speed at that reel's pack radius.
    mutating func scrubTo(omega: Double, radius: Double, active: Bool) {
        scrubbing = active
        let speed = (omega * radius) / (Motion.Transport.baseOmega * Motion.Transport.refRadius)
        scrubV = max(-Motion.Scrub.maxSpeed, min(Motion.Scrub.maxSpeed, speed))
        if !active { v = scrubV }
    }

    private static func within(_ t: Double, _ range: (start: Double, end: Double)) -> Bool {
        t >= range.start && t < range.end
    }

    /// The scripted finger: the same inputs a hand gives.
    mutating func scriptInput(_ t: Double) {
        held[0] = Self.within(t, Motion.Script.play)
        held[1] = Self.within(t, Motion.Script.pause)
        held[2] = Self.within(t, Motion.Script.next)
        held[3] = false
        held[4] = Self.within(t, Motion.Script.ff)
        let g = Motion.Script.drag
        let u = (t - g.start) / g.time
        if u >= 0 && u < 1 {
            let page = 1 - g.dist * pow(u, g.power)
            let vp = (-g.dist * g.power * pow(u, g.power - 1)) / g.time
            dragTo(page, velocity: vp, done: false)
        } else if dragging && u >= 1 {
            dragTo(1 - g.dist, velocity: (-g.dist * g.power) / g.time, done: true)
        }
    }

    /// Advances the autoplay one fixed step; wraps at the loop's end.
    mutating func advanceAutoplay() {
        if step >= Self.autoplayStepCount {
            resetToRest()
            step = 0
        }
        scriptInput(Double(step) * Motion.step)
        advance(Motion.step, live: false)
        step += 1
    }

    /// The RN reels are tooth-equivalent at 9 s; Swift also publishes the exact initial pose at that boundary.
    mutating func canonicalizeAutoplayBoundary() {
        resetToRest()
        for i in 0..<ang.count { ang[i] = 0 }
        step = 0
    }

    /// Runs one loop of the script ahead of time and scales each reel's turn so it ends on a whole tooth (60°):
    /// the reels then look the same at the seam and the preview video loops without a jump.
    static func loopScales() -> [Double] {
        var sim = CassetteDeckSim()
        for _ in 0..<autoplayStepCount { sim.advanceAutoplay() }
        return sim.ang.map { a in
            a < 0.01 ? 1 : max(1, jsRound(a / sector)) * sector / a
        }
    }

    /// True when nothing moves any more, so the display link can pause until the next touch.
    var isSettled: Bool {
        if dragging || scrubbing || latched || abs(v) > 1e-5 { return false }
        for i in 0..<Self.keyCount {
            if held[i] || lift[i] { return false }
            if abs(d[i]) > 1e-4 || abs(dv[i]) > 1e-3 || abs(lit[i]) > 1e-3 { return false }
        }
        for j in 0..<Self.slotCount where abs(w[j]) > 1e-4 { return false }
        if abs(p - target) > 1e-4 || abs(pv) > 1e-3 { return false }
        if abs(dip) > 1e-4 || abs(dipV) > 1e-3 || abs(rattle) > 1e-4 || abs(rattleV) > 1e-3 { return false }
        return led < 1e-3 && fade >= 1
    }
}
