import Foundation

/*
 Morse Key Input — timing, the tape and the decoder. Every frame of the self-playing loop is a
 pure function of the loop time t; under a finger a small simulation (Sim) is stepped from the
 timeline and read through the same Pose.

 loop = 9 s, unit u = 0.24 s (a dot is one unit, the gap inside a letter one, between letters three):
   0.0 – 1.3   rest (the poster: the key at rest, a blank leader of tape, two empty answer slots)
   1.3 – 2.98  H: · · · · (presses at 1.30, 1.78, 2.26, 2.74)     3.48  H is decoded into slot 1
   3.70 – 4.42 I: · · (presses at 3.70, 4.18)                       4.92  I is decoded into slot 2
   1.1 – 5.1   the tape runs at 34 pt/s (0.2 s to speed up and to stop), printing a mark per press
   5.3 – 6.8   hold: "Correct · +40"
   6.8 – 7.9   the printed tape tears off at the register and falls away; 7.0 – 7.5 the answer
               rolls out and the chip fades
   7.6 – 8.4   a fresh leader feeds out of the register      8.4 – 9.0 rest
*/
enum MorseKeyInputMotion {
    static let loop = 9.0
    static let unit = 0.24
    static let tapeSpeed = 34.0
    /// Touch: a press longer than this is a dash; a pause longer than letterGap ends the letter.
    static let dashAt = 0.2
    static let letterGap = 0.55
    static let idleBeforeBack = 2.0
    static let backFor = 0.9
    /// The lever's travel stops on the contact: depth 1.
    static let keyOmega = 60.0
    static let keyZeta = 0.5

    static let presses: [(Double, Double)] = [
        (1.3, 1.54), (1.78, 2.02), (2.26, 2.5), (2.74, 2.98), (3.7, 3.94), (4.18, 4.42),
    ]
    static let decode = [3.48, 4.92]
    static let answer = ["H", "I"]

    static let morse: [String: String] = [
        ".-": "A", "-...": "B", "-.-.": "C", "-..": "D", ".": "E", "..-.": "F", "--.": "G", "....": "H", "..": "I",
        ".---": "J", "-.-": "K", ".-..": "L", "--": "M", "-.": "N", "---": "O", ".--.": "P", "--.-": "Q", ".-.": "R",
        "...": "S", "-": "T", "..-": "U", "...-": "V", ".--": "W", "-..-": "X", "-.--": "Y", "--..": "Z",
        "-----": "0", ".----": "1", "..---": "2", "...--": "3", "....-": "4", ".....": "5", "-....": "6", "--...": "7", "---..": "8", "----.": "9",
    ]

    struct Slot {
        var ch = ""
        var inn = 0.0
        var out = 0.0
    }

    struct Strip {
        /// Tape length out of the register (pt), the tape run so far, and the marks as [from, to] in run coordinates.
        var len = 0.0
        var run = 0.0
        var marks: [Double] = []
    }

    struct Pose {
        var depth = 0.0
        /// Seconds since the contact last closed (sparks), and how hard it hums while held.
        var sparkAge = 99.0
        var hum = 0.0
        /// Pen on the tape (0 … 1).
        var pen = 0.0
        var tape = Strip()
        /// The torn strip falling away: progress 0 … 1 (0 = none).
        var fall = 0.0
        var torn = Strip()
        var slots = [Slot(), Slot()]
        /// The symbols keyed for the letter in progress, e.g. "..-".
        var buffer = ""
        var bufferAlpha = 0.0
        /// The result chip: 0 hidden … 1 shown, and whether the answer is right.
        var chip = 0.0
        var correct = 1.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    static func easeInOut(_ x: Double) -> Double {
        let v = clamp01(x)
        if v < 0.5 { return 4 * v * v * v }
        let u = -2 * v + 2
        return 1 - u * u * u / 2
    }
    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    /// Unit step response of an underdamped spring, 0 before τ = 0.
    static func springStep(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = keyOmega * (1 - keyZeta * keyZeta).squareRoot()
        let e = exp(-keyZeta * keyOmega * tau)
        return 1 - e * (cos(wd * tau) + keyZeta * keyOmega / wd * sin(wd * tau))
    }

    /// The tape's run (pt) at loop time t: it speeds up over 0.2 s, runs, and slows over 0.2 s.
    static func runAt(_ t: Double) -> Double {
        let a = 0.2
        var tau = 0.0
        if t <= 1.1 { tau = 0 }
        else if t < 1.1 + a { tau = (t - 1.1) * (t - 1.1) / (2 * a) }
        else if t < 4.9 { tau = a / 2 + (t - 1.1 - a) }
        else if t < 4.9 + a { tau = a / 2 + (4.9 - 1.1 - a) + (t - 4.9) - (t - 4.9) * (t - 4.9) / (2 * a) }
        else { tau = 3.8 }
        return tau * tapeSpeed
    }

    static func loopMarksAll() -> [Double] {
        presses.flatMap { [runAt($0.0 + 0.02), runAt($0.1)] }
    }

    /// The pose of the self-playing loop at loop time t; `leader` is the resting tape length.
    static func loopPose(_ t: Double, _ leader: Double) -> Pose {
        // The lever: superposed spring steps, stopped by the contact at depth 1.
        var depth = 0.0
        var downAt = -99.0
        var held = false
        for (d, u) in presses {
            depth += springStep(t - d) - springStep(t - u)
            if t >= d {
                downAt = d
                held = t < u
            }
        }
        depth = max(0, min(1, depth))
        let run = runAt(t)
        var marks: [Double] = []
        for (d, u) in presses {
            if t < d + 0.02 { break }
            marks.append(runAt(d + 0.02))
            marks.append(runAt(min(t, u)))
        }
        var buffer = ""
        for (d, _) in presses {
            if t < d { break }
            if d < decode[0] ? t < decode[0] : t < decode[1] { buffer += "." }
        }
        var p = Pose()
        for i in 0..<2 {
            let out = easeInOut(seg(t, 7.0 + Double(i) * 0.07, 7.4 + Double(i) * 0.07))
            p.slots[i] = Slot(ch: t >= decode[i] && t < 7.6 ? answer[i] : "", inn: easeOut(seg(t, decode[i], decode[i] + 0.3)), out: out)
        }
        let tear = t >= 6.8
        p.tape = tear ? Strip(len: leader * easeOut(seg(t, 7.6, 8.4)), run: 0, marks: []) : Strip(len: leader + run, run: run, marks: marks)
        p.torn = Strip(len: leader + runAt(loop), run: runAt(loop), marks: tear ? loopMarksAll() : [])
        p.fall = tear ? seg(t, 6.8, 7.9) : 0
        p.depth = depth
        p.sparkAge = t - downAt - 0.02
        p.hum = held ? 1 : 0
        p.pen = depth > 0.85 ? 1 : 0
        p.buffer = buffer
        p.bufferAlpha = buffer.isEmpty ? 0 : 1
        p.chip = easeOut(seg(t, 5.3, 5.6)) * (1 - easeInOut(seg(t, 6.8, 7.1)))
        p.correct = 1
        return p
    }
}

/// The finger's simulation, stepped from the timeline.
final class MorseKeyInputSim {
    typealias M = MorseKeyInputMotion

    var depth = 0.0, vel = 0.0
    var held = false
    var downAt = -99.0, upAt = -99.0
    var run = 0.0, speed = 0.0
    var marks: [Double] = []
    var buffer = ""
    var decoded: [String] = []
    var slotAt: [Double] = []
    var chipAt = -1.0
    var correct = 0.0
    var backAt = -1.0
    var torn: M.Strip? = nil
    /// Tape length when the finger took over (the leader may still be feeding out).
    var base: Double

    init(leader: Double) {
        base = leader
    }

    /// Steps the lever and the tape, and decodes a finished letter. Returns true when a letter landed.
    func step(_ now: Double, _ dt: Double) -> Bool {
        let target = held ? 1.0 : 0.0
        // Semi-implicit spring; the contact stops the lever with a small bounce.
        vel += (-M.keyOmega * M.keyOmega * (depth - target) - 2 * M.keyZeta * M.keyOmega * vel) * dt
        depth += vel * dt
        if depth > 1 {
            depth = 1
            vel = -0.25 * vel
        }
        if depth < 0 {
            depth = 0
            vel = 0
        }
        // The tape runs while keying and stops about a second after the last release.
        let busy = backAt < 0 && (held || now - upAt < 1.1)
        let want = busy ? M.tapeSpeed : 0
        speed += (want - speed) * (1 - exp(-dt / 0.08))
        run += speed * dt
        if held && !marks.isEmpty { marks[marks.count - 1] = run }
        if !held && !buffer.isEmpty && now - upAt > M.letterGap {
            let ch = M.morse[buffer] ?? "?"
            buffer = ""
            if decoded.count >= 2 {
                decoded = []
                slotAt = []
                chipAt = -1
            }
            decoded.append(ch)
            slotAt.append(now)
            if decoded.count == 2 {
                chipAt = now + 0.2
                correct = decoded[0] == "H" && decoded[1] == "I" ? 1 : 0
            }
            return true
        }
        return false
    }

    func keyDown(_ now: Double) {
        held = true
        downAt = now
        marks.append(run)
        marks.append(run)
    }

    func keyUp(_ now: Double) {
        if !held { return }
        held = false
        upAt = now
        buffer += now - downAt > M.dashAt ? "-" : "."
        if buffer.count > 6 { buffer = String(buffer.suffix(6)) }
    }

    /// The pose under (or after) a finger at clock time `now`.
    func pose(_ now: Double, _ leader: Double) -> M.Pose {
        var p = M.Pose()
        let back = backAt >= 0 ? now - backAt : -1
        for i in 0..<2 {
            let at = i < slotAt.count ? slotAt[i] : -1
            p.slots[i] = M.Slot(
                ch: i < decoded.count ? decoded[i] : "",
                inn: at < 0 ? 0 : M.easeOut((now - at) / 0.3),
                out: back < 0 ? 0 : M.easeInOut(M.seg(back, 0.05 + Double(i) * 0.07, 0.45 + Double(i) * 0.07))
            )
        }
        p.chip = chipAt < 0 ? 0 : M.easeOut((now - chipAt) / 0.3) * (back < 0 ? 1 : 1 - M.easeInOut(back / 0.3))
        p.tape = back >= 0 ? M.Strip(len: leader * M.easeOut(M.seg(back, 0.25, M.backFor)), run: 0, marks: []) : M.Strip(len: base + run, run: run, marks: marks)
        p.depth = depth
        p.sparkAge = now - downAt - 0.02
        p.hum = held ? 1 : 0
        p.pen = depth > 0.85 ? 1 : 0
        p.fall = back >= 0 ? M.seg(back, 0, M.backFor) : 0
        p.torn = torn ?? M.Strip()
        p.buffer = buffer
        p.bufferAlpha = !buffer.isEmpty && back < 0 ? 1 : 0
        p.correct = correct
        return p
    }
}

/// The mode of the block and the clock: the loop plays, a finger plays, or the screen eases back.
final class MorseKeyInputEngine {
    typealias M = MorseKeyInputMotion

    enum Mode { case auto, touch, back }

    var mode = Mode.auto
    var loopStart: Double? = nil
    var lastFrame: Double? = nil
    var lastTouch = 0.0
    var sim = MorseKeyInputSim(leader: 0)
    var lastPose = M.Pose()
    /// Set when a letter landed this frame: 1 a letter, 2 the right answer (for haptics).
    var landed = 0

    /// The resting tape length of the current layout.
    var leader = 0.0

    func pose(at now: Double, leader: Double, auto: Bool) -> M.Pose {
        self.leader = leader
        if loopStart == nil { loopStart = now }
        let gap = lastFrame.map { now - $0 } ?? 0
        // A stalled frame (or a paused timeline) holds the loop where it was instead of skipping.
        if gap > 0.25, mode == .auto, let s = loopStart { loopStart = s + gap }
        let dt = min(max(gap, 0), 1.0 / 20)
        lastFrame = now
        if mode != .auto {
            let s = sim
            if s.step(now, dt) {
                landed = s.decoded.count == 2 && s.correct == 1 ? 2 : 1
            }
            if mode == .touch && !s.held && s.buffer.isEmpty && now - lastTouch > M.idleBeforeBack {
                mode = .back
                s.torn = M.Strip(len: s.base + s.run, run: s.run, marks: s.marks)
                s.backAt = now
            } else if mode == .back && now >= s.backAt + M.backFor {
                sim = MorseKeyInputSim(leader: leader)
                mode = .auto
                loopStart = now
            }
        }
        let p: M.Pose
        if mode != .auto {
            p = sim.pose(now, leader)
        } else if !auto {
            p = M.loopPose(0, leader)
        } else {
            p = M.loopPose(max(0, (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)), leader)
        }
        lastPose = p
        return p
    }

    /// Hands whatever the loop shows to the simulation, so a finger never makes anything jump.
    func takeOver(_ at: Double) {
        if mode == .touch { return }
        let p = lastPose
        let s = MorseKeyInputSim(leader: leader)
        if mode == .auto && p.fall == 0 {
            s.base = p.tape.len - p.tape.run
            s.run = p.tape.run
            s.marks = p.tape.marks
            s.depth = p.depth
            for i in 0..<2 where !p.slots[i].ch.isEmpty && p.slots[i].inn > 0.5 && p.slots[i].out < 0.5 {
                s.decoded.append(p.slots[i].ch)
                s.slotAt.append(at - 1)
            }
            // A letter the loop was in the middle of keying is finished as dots.
            s.buffer = p.buffer
            s.upAt = at
            if s.decoded.count == 2 {
                s.chipAt = at - 1
                s.correct = 1
            }
        } else {
            s.base = p.tape.len
        }
        sim = s
        mode = .touch
    }
}
