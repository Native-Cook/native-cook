import Foundation

/*
 Dymo Label Input — the autoPlay script and the pose it produces. Every value is a pure function of
 the loop time t.

   0.0 – 1.2   rest: a blank stub of red tape out of the head, the wheel on A, an unlabelled jar.
   1.2 – 4.6   "OATS" is typed, 0.85 s a letter: the wheel spins to the letter (0.32 s, ease-in-out,
               a 1.5° click past it and back), the trigger is squeezed (0.16 s down, 0.2 s up), the
               letter whitens as it is pressed into the tape, and the tape advances one pitch (0.2 s).
   4.6 – 5.0   Return: the cutter snaps and the strip comes free.
   5.0 – 6.2   the strip flies onto the jar along an arc with a turn and a lift, and sticks with a
               squash (spring).
   6.2 – 7.4   hold.
   7.4 – 8.4   the jar slides off along the shelf and a fresh one slides in; new tape feeds out; the
               wheel spins back to A; the field clears.
   8.4 – 9.0   rest.
 */

typealias DymoLabelInputRGB = (Double, Double, Double)

struct DymoLabelInputPose {
    /// Wheel rotation (deg): slot j is under the head when wheel = -j * step.
    var wheel: Double
    var trigger: Double
    /// Letters on the tape in the machine, how white each is (0..1), how far each pops (0..1).
    var tape: [Character]
    var whiten: [Double]
    var pop: [Double]
    /// Tape advance in pitches, and blank tape beyond the first letter (pt).
    var advance: Double
    var lead: Double
    /// The cut strip: its letters, flight 0 (at the head) → 1 (on the jar), squash, cutter lever.
    var strip: [Character]
    var fly: Double
    var squash: Double
    var cutter: Double
    /// The jar swap 0 → 1 (the labelled jar leaves, a fresh one arrives).
    var slide: Double
    var tapeRGB: DymoLabelInputRGB
    var stripRGB: DymoLabelInputRGB
}

struct DymoLabelInputTape {
    let name: String
    let rgb: DymoLabelInputRGB
}

enum DymoLabelInputMotion {
    static let word: [Character] = Array("OATS")
    /// The wheel's characters, clockwise.
    static let wheelChars: [Character] = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-&. ")
    static let step = 360 / Double(wheelChars.count)
    private static let letterFrom = 1.2
    private static let letterEvery = 0.85

    static let tapes: [DymoLabelInputTape] = [
        .init(name: "Red", rgb: (0.76, 0.19, 0.23)),
        .init(name: "Black", rgb: (0.17, 0.17, 0.19)),
        .init(name: "Blue", rgb: (0.18, 0.37, 0.65)),
        .init(name: "Green", rgb: (0.18, 0.48, 0.35)),
        .init(name: "Yellow", rgb: (0.9, 0.71, 0.21)),
    ]

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func easeInOut(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - u, 3) }

    /// Spin with a detent click: ease-in-out to the target, then a small damped overshoot.
    static func click(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        let base = easeInOut(min(1, u / 0.75))
        let after = max(0, (u - 0.75) / 0.25)
        return base + (u > 0.75 ? 0.06 * sin(after * .pi) * (1 - after) : 0)
    }

    /// Squash on landing: 0 → 1 → settles back to 0 with a spring.
    static func squash(_ u: Double) -> Double {
        if u <= 0 || u >= 1 { return 0 }
        return exp(-5 * u) * sin(u * .pi * 2.5)
    }

    /// The shortest signed turn (deg) from slot a to slot b.
    static func slotTurn(_ a: Int, _ b: Int) -> Double {
        var d = Double(b - a) * step
        while d > 180 { d -= 360 }
        while d < -180 { d += 360 }
        return d
    }

    static func slotOf(_ ch: Character) -> Int {
        let up = Character(String(ch).uppercased())
        return wheelChars.firstIndex(of: up) ?? wheelChars.count - 1
    }

    static func mix(_ a: DymoLabelInputRGB, _ b: DymoLabelInputRGB, _ t: Double) -> DymoLabelInputRGB {
        (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t)
    }

    /// The loop's pose at t.
    static func loopPose(_ t: Double) -> DymoLabelInputPose {
        let n = word.count
        // Wheel: A → O → A → T → S, each spin in the first 0.32 s of its letter's slot; home to A at 7.4.
        var wheel = 0.0
        var from = 0
        for i in 0..<n {
            let to = slotOf(word[i])
            let a = letterFrom + Double(i) * letterEvery
            wheel += slotTurn(from, to) * click(seg(t, a, a + 0.32))
            from = to
        }
        wheel += slotTurn(from, 0) * click(seg(t, 7.4, 7.95))
        wheel = -wheel

        var trigger = 0.0
        var whiten: [Double] = []
        var pop: [Double] = []
        var advance = 0.0
        var pressed = 0
        for i in 0..<n {
            let a = letterFrom + Double(i) * letterEvery + 0.36
            trigger = max(trigger, easeOut(seg(t, a, a + 0.16)) * (1 - easeInOut(seg(t, a + 0.16, a + 0.36))))
            let w = easeOut(seg(t, a + 0.08, a + 0.2))
            if t >= a + 0.08 { pressed = i + 1 }
            whiten.append(w)
            pop.append(sin(.pi * seg(t, a + 0.1, a + 0.34)))
            advance += easeInOut(seg(t, a + 0.3, a + 0.5))
        }
        let cut = t >= 4.7
        let cutter = easeOut(seg(t, 4.6, 4.7)) * (1 - easeInOut(seg(t, 4.7, 4.9)))
        let fly = easeInOut(seg(t, 5.0, 6.0))
        let sq = squash(seg(t, 5.95, 6.45))
        let slide = easeInOut(seg(t, 7.4, 8.4))
        // After the cut, the stub left in the machine is short; new tape feeds out with the jar swap.
        let feed = easeInOut(seg(t, 7.5, 8.3))
        return DymoLabelInputPose(
            wheel: wheel, trigger: trigger,
            tape: cut ? [] : Array(word.prefix(pressed)),
            whiten: cut ? [] : Array(whiten.prefix(pressed)),
            pop: cut ? [] : Array(pop.prefix(pressed)),
            advance: cut ? 0 : advance,
            lead: cut ? 4 + 14 * feed : 18,
            strip: cut && slide < 1 ? word : [],
            fly: fly, squash: sq, cutter: cutter, slide: slide,
            tapeRGB: tapes[0].rgb, stripRGB: tapes[0].rgb
        )
    }

    /// How many letters of the word the field shows over the loop.
    static func loopText(_ t: Double) -> Int {
        if t >= 7.4 { return 0 }
        var n = 0
        for i in 0..<word.count where t >= letterFrom + Double(i) * letterEvery + 0.44 { n = i + 1 }
        return n
    }

    /// The wheel angle (deg) nearest `from` that puts `slot` under the head.
    static func angleFor(_ slot: Int, from: Double) -> Double {
        let base = -Double(slot) * step
        let k = ((from - base) / 360).rounded()
        return base + k * 360
    }

    static func slotAt(_ angle: Double) -> Int {
        let n = wheelChars.count
        let r = Int((-angle / step).rounded())
        return ((r % n) + n) % n
    }

    /// cubic-bezier(0.65, 0, 0.35, 1), the wheel's spin.
    static func spinEase(_ u: Double) -> Double {
        let x1 = 0.65, y1 = 0.0, x2 = 0.35, y2 = 1.0
        if u <= 0 { return 0 }
        if u >= 1 { return 1 }
        func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
        func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
        var lo = 0.0, hi = 1.0, s = u
        for _ in 0..<24 {
            s = (lo + hi) / 2
            if bx(s) < u { lo = s } else { hi = s }
        }
        return by(s)
    }
}

/// The user's tape: what is on it, when each letter was struck, the cut strip.
struct DymoLabelInputUserTape {
    var text: [Character] = []
    var e: [Double] = []
    var colour = 0
    var colourFrom = 0
    var colourAt = -100.0
    var strip: [Character] = []
    var stripColour = 0
    var cutAt = -100.0

    func pose(_ now: Double, wheel: Double, reduced: Bool) -> DymoLabelInputPose {
        typealias M = DymoLabelInputMotion
        var trigger = 0.0
        var advance = 0.0
        var whiten: [Double] = []
        var pop: [Double] = []
        for e in e {
            trigger = max(trigger, M.easeOut(M.seg(now, e - 0.08, e)) * (1 - M.easeInOut(M.seg(now, e, e + 0.2))))
            whiten.append(reduced ? 1 : M.easeOut(M.seg(now, e, e + 0.12)))
            pop.append(reduced ? 0 : sin(.pi * M.seg(now, e + 0.02, e + 0.26)))
            advance += reduced ? 1 : M.easeInOut(M.seg(now, e + 0.22, e + 0.42))
        }
        let c = cutAt
        let live = c > 0 && now < c + 3.6
        return DymoLabelInputPose(
            wheel: wheel, trigger: trigger, tape: text, whiten: whiten, pop: pop, advance: advance,
            lead: !text.isEmpty || c < 0 ? 18 : 4 + 14 * M.easeInOut(M.seg(now, c + 0.6, c + 1.3)),
            strip: live ? strip : [],
            fly: reduced ? 1 : M.easeInOut(M.seg(now, c + 0.25, c + 1.25)),
            squash: reduced ? 0 : M.squash(M.seg(now, c + 1.2, c + 1.7)),
            cutter: M.easeOut(M.seg(now, c, c + 0.1)) * (1 - M.easeInOut(M.seg(now, c + 0.1, c + 0.3))),
            slide: live ? M.easeInOut(M.seg(now, c + 2.6, c + 3.6)) : 0,
            tapeRGB: M.mix(M.tapes[colourFrom].rgb, M.tapes[colour].rgb, M.seg(now, colourAt, colourAt + 0.25)),
            stripRGB: M.tapes[stripColour].rgb
        )
    }
}
