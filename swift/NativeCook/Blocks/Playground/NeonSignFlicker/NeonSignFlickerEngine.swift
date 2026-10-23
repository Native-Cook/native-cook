import Foundation

// The sign's life: which words are switched on, how bright each tube is, and how the sign swings
// (a port of the React Native block's neonSignSim.ts, step for step).
//
// Everything advances on one fixed tick from one clock, so the flicker, the swing and the
// self-playing script run the same, step for step, on any platform. A tube's brightness follows
// a target that is a pure function of the time since its word was switched (ignition buzz, then a
// warm-up from dim to full), plus a brown-out the script can trigger, plus the stutter of the one
// faulty letter. The "random" in the buzz is a hash of the tube and the time slot, so it is the
// same on every run.

enum NeonSignFlickerTiming {
    /// The simulation tick, in seconds, and the most ticks one slow frame may run (a 50 ms cap).
    static let tick = 1.0 / 60
    static let maxTicks = 3

    // autoPlay (seconds): the sign powers on word by word, browns out, is pushed to swing, "late"
    // is switched off and on, then everything powers down and the sign hangs still again.
    static let loop = 12.0
    static let scriptOpenOn = 0.5
    static let scriptGlassOn = 1.45
    static let scriptLateOn = 2.15
    static let scriptSag = 4.7
    static let scriptPush = 5.7
    static let scriptLateOff = 8.3
    static let scriptLateBack = 8.9
    static let scriptAllOff = [10.7, 10.85, 11.0]
    // From here to the end of the loop the swing is damped harder, so it is still at the seam.
    static let scriptSettle = 9.5
    // The push, in rad/s: about 10° of swing.
    static let scriptPushSpeed = 0.55

    // Tubes of a word light one after another, this far apart (s), and go out almost together.
    static let letterStep = 0.16
    static let letterStepOff = 0.03
    // Ignition: the gas buzzes on and off in slots of 1/buzzRate s for ignite s, more often lit
    // as it catches; then it warms from warmFrom to full with a time constant of warmTime.
    static let ignite = 0.42
    static let buzzRate = 30.0
    static let warmFrom = 0.58
    static let warmTime = 0.55
    // The faulty letter: every stutterEvery s it may stutter (chance stutterChance) for
    // stutterLength s, dropping out in slots of 1/stutterRate s; it also burns a little dimmer.
    static let stutterEvery = 1.6
    static let stutterChance = 0.7
    static let stutterLength = 0.42
    static let stutterRate = 22.0
    static let faultyDim = 0.86
    // A brown-out lasts sagTime s.
    static let sagTime = 0.75
    // The gas answers in a few ms: brightness chases its target at these rates (1/s).
    static let riseRate = 55.0
    static let fallRate = 28.0
    // Reduced motion: no buzz or stutter, words fade in over this long (s).
    static let calmFade = 0.45

    // The sign hangs on two chains from one hook: a pendulum with a period of `period` s, its
    // swing damped at `damping` /s, and much faster while the loop settles.
    static let period = 2.0
    static let w0 = 2 * Double.pi / period
    static let damping = 1.0
    static let dampingSettle = 2.4

    static let words = 3
}

/// The simulation state, mutated in place one tick at a time.
final class NeonSignFlickerSim {
    private typealias T = NeonSignFlickerTiming

    /// Simulation time (s) and the script's clock (s, wraps at the loop).
    var time = 0.0
    var clock = 0.0
    /// Seconds left before the script resumes after a touch.
    var hold = 0.0
    var looping: Bool
    var reduced: Bool
    /// Per word: switched on (1) or off (0), and the time it was last switched.
    var on = [0, 0, 0]
    var switchedAt = [-100.0, -100.0, -100.0]
    /// Per piece: its word, its place in the word, and its brightness 0…1.
    let word: [Int]
    let order: [Int]
    var level: [Double]
    /// The piece that stutters.
    let faulty: Int
    /// Time of the last brown-out.
    var sagAt = -100.0
    /// The swing: angle (rad, clockwise) and its speed (rad/s).
    var theta = 0.0
    var omega = 0.0
    var dragging = false

    init(word: [Int], order: [Int], faulty: Int, looping: Bool, reduced: Bool) {
        self.word = word
        self.order = order
        self.faulty = faulty
        self.looping = looping
        self.reduced = reduced
        level = [Double](repeating: 0, count: word.count)
    }

    /// A hash of two integers to [0, 1), with 32-bit wrapping maths like JavaScript's `Math.imul`:
    /// the same numbers give the same value on every platform.
    static func hash(_ a: Int, _ b: Int) -> Double {
        var h = Int32(truncatingIfNeeded: a) &* 374_761_393 &+ Int32(truncatingIfNeeded: b) &* 668_265_263
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1_274_126_177
        h ^= Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Double(UInt32(bitPattern: h)) / 4_294_967_296
    }

    /// Switches a word on or off; does nothing when it already is. Returns whether it changed.
    @discardableResult
    func setWord(_ w: Int, _ value: Int) -> Bool {
        if on[w] == value { return false }
        on[w] = value
        switchedAt[w] = time
        return true
    }

    /// How bright the stuttering letter is, `age` s after its word lit.
    private func stutter(_ i: Int, _ age: Double) -> Double {
        let episode = Int((age / T.stutterEvery).rounded(.down))
        if Self.hash(i * 7 + 3, episode) > T.stutterChance { return 1 }
        let start = 0.15 + 0.9 * Self.hash(i * 7 + 5, episode)
        let into = age - Double(episode) * T.stutterEvery - start
        if into < 0 || into > T.stutterLength { return 1 }
        return Self.hash(i * 13 + 1, Int((age * T.stutterRate).rounded(.down))) > 0.45 ? 0.95 : 0.07
    }

    /// The brown-out's dip, `dt` s after it began: two blinks and a slow recovery.
    private func sag(_ dt: Double) -> Double {
        if dt < 0 || dt > T.sagTime { return 1 }
        if dt < 0.07 { return 0.22 }
        if dt < 0.15 { return 0.9 }
        if dt < 0.24 { return 0.12 }
        return 0.5 + 0.5 * ((dt - 0.24) / (T.sagTime - 0.24))
    }

    /// What piece `i` is heading for right now.
    private func target(_ i: Int) -> Double {
        let w = word[i]
        if on[w] == 0 {
            let age = time - switchedAt[w] - Double(order[i]) * T.letterStepOff
            if age < 0 { return level[i] }
            // A last gasp as the current dies.
            if !reduced && age < 0.06 { return 0.12 }
            if !reduced && age < 0.11 { return 0.5 }
            return 0
        }
        let age = time - switchedAt[w] - Double(order[i]) * T.letterStep
        if age < 0 { return 0 }
        if reduced { return min(1, age / T.calmFade) }
        var b: Double
        if age < T.ignite {
            // Lit more often as the gas catches.
            let slot = Int((age * T.buzzRate).rounded(.down))
            let lit = Self.hash(i * 31 + 17, slot) > 0.85 - 0.7 * age / T.ignite
            b = lit ? 0.45 + 0.35 * Self.hash(i * 31 + 19, slot) : 0.03
        } else {
            b = T.warmFrom + (1 - T.warmFrom) * (1 - exp(-(age - T.ignite) / T.warmTime))
        }
        if i == faulty && age >= T.ignite { b *= T.faultyDim * stutter(i, age - T.ignite) }
        return b * sag(time - sagAt)
    }

    private func crossed(_ before: Double, _ after: Double, _ at: Double) -> Bool {
        before < at && after >= at
    }

    /// Runs the script from `before` to `after` on the loop's clock.
    private func script(_ before: Double, _ after: Double) {
        if crossed(before, after, T.scriptOpenOn) { setWord(0, 1) }
        if crossed(before, after, T.scriptGlassOn) { setWord(2, 1) }
        if crossed(before, after, T.scriptLateOn) { setWord(1, 1) }
        if crossed(before, after, T.scriptSag) { sagAt = time }
        if crossed(before, after, T.scriptPush) && !reduced { omega += T.scriptPushSpeed }
        if crossed(before, after, T.scriptLateOff) { setWord(1, 0) }
        if crossed(before, after, T.scriptLateBack) { setWord(1, 1) }
        if crossed(before, after, T.scriptAllOff[0]) { setWord(2, 0) }
        if crossed(before, after, T.scriptAllOff[1]) { setWord(0, 0) }
        if crossed(before, after, T.scriptAllOff[2]) { setWord(1, 0) }
    }

    /// One tick: the script, every tube's brightness, then the swing.
    func step() {
        time += T.tick
        if hold > 0 {
            hold -= T.tick
            if hold <= 0 { clock = 0 }
        } else if looping {
            let before = clock
            var after = before + T.tick
            if after >= T.loop {
                script(before, T.loop)
                after -= T.loop
                script(-1, after)
            } else {
                script(before, after)
            }
            clock = after
        }

        let rise = 1 - exp(-T.riseRate * T.tick)
        let fall = 1 - exp(-T.fallRate * T.tick)
        for i in level.indices {
            let goal = target(i)
            let l = level[i]
            level[i] = l + (goal - l) * (goal > l ? rise : fall)
        }

        if dragging { return }
        if reduced {
            theta = 0
            omega = 0
            return
        }
        let damping = looping && hold <= 0 && clock > T.scriptSettle ? T.dampingSettle : T.damping
        let accel = -T.w0 * T.w0 * sin(theta) - damping * omega
        omega += accel * T.tick
        theta += omega * T.tick
    }

    /// Mean brightness of each word's tubes, for the light it throws.
    func wordLevels() -> [Double] {
        var out = [0.0, 0.0, 0.0]
        var count = [0, 0, 0]
        for i in level.indices {
            out[word[i]] += level[i]
            count[word[i]] += 1
        }
        for w in 0..<T.words { out[w] = count[w] > 0 ? out[w] / Double(count[w]) : 0 }
        return out
    }
}

/// What one frame draws: every tube's brightness, each word's, and the swing.
struct NeonSignFlickerFrame {
    var level: [Double]
    var words: [Double]
    var theta: Double
}

/// Drives the simulation from the display clock, like the React Native frame callback: at most
/// `maxTicks` fixed ticks per frame, the remainder carried (and capped at one tick).
final class NeonSignFlickerEngine {
    private typealias T = NeonSignFlickerTiming

    private(set) var sim: NeonSignFlickerSim?
    private var acc = 0.0
    private var last: Date?

    /// The hook, in screen points, and the angle of the finger about it when a drag began.
    private var grabTheta = 0.0
    private var grabPhi = 0.0

    func step(to date: Date, pieces: [NeonSignFlickerPiece], faulty: Int, looping: Bool, reduced: Bool) -> NeonSignFlickerFrame {
        let st: NeonSignFlickerSim
        if let existing = sim {
            st = existing
        } else {
            st = NeonSignFlickerSim(word: pieces.map(\.word), order: pieces.map(\.order), faulty: faulty, looping: looping, reduced: reduced)
            sim = st
        }
        st.looping = looping
        st.reduced = reduced
        let elapsed = last.map { date.timeIntervalSince($0) } ?? 0.016
        last = date
        let dt = min(max(0, elapsed), Double(T.maxTicks) * T.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= T.tick && ticks < T.maxTicks {
            st.step()
            pending -= T.tick
            ticks += 1
        }
        acc = min(pending, T.tick)
        return NeonSignFlickerFrame(level: st.level, words: st.wordLevels(), theta: st.theta)
    }

    /// The script restarts from its beginning (autoPlay switched).
    func restartScript() {
        sim?.clock = 0
        sim?.hold = 0
    }

    /// A touch pauses the script for `hold` s, after which it starts over.
    func touched(hold: Double) {
        sim?.hold = hold
    }

    /// Begins swinging the sign by hand from a finger at `angle` about the hook.
    func grab(angle: Double, hold: Double) {
        guard let st = sim else { return }
        st.hold = hold
        st.dragging = true
        st.omega = 0
        grabTheta = st.theta
        grabPhi = angle
    }

    /// Turns the sign with the finger, resisting past `maxSwing`.
    func drag(angle: Double, maxSwing: Double, rubber: Double) {
        guard let st = sim, st.dragging else { return }
        var d = angle - grabPhi
        if d > Double.pi { d -= 2 * Double.pi }
        if d < -Double.pi { d += 2 * Double.pi }
        let raw = grabTheta + d
        let over = abs(raw) - maxSwing
        st.theta = over > 0 ? (raw < 0 ? -1 : 1) * (maxSwing + over * rubber) : raw
    }

    /// Lets go of the sign: it swings on at `spin` rad/s (clamped).
    func release(spin: Double, maxSpin: Double, hold: Double) {
        guard let st = sim, st.dragging else { return }
        st.dragging = false
        st.omega = max(-maxSpin, min(maxSpin, spin))
        st.hold = hold
    }
}
