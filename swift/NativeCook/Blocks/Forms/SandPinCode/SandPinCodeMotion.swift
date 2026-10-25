import SwiftUI

/*
 Sand PIN Code — digit strokes, the loop script and the grains, as pure functions of time.

 loop = 10 s
   0.0 – 1.25   rest: raked sand, six empty slots.
   1.25 – 3.81  the wrong code 4 8 2 7 1 5: each pebble squashes, a bamboo stylus carves the
                digit along its stroke in 0.26 s (ease-in-out), 0.46 s apart.
   3.95 – 4.40  the tray shakes (damped, 9 pt, 9 Hz).
   4.35 – 5.15  a seven-tine rake sweeps left to right over the slots; as it reaches each digit
                the groove crumbles into 50 grains that are pushed ahead, slide down and sink
                (0.9 s); fresh rake lines stay behind it.
   5.65 – 7.81  the right code 2 6 0 5 1 9, 0.38 s apart.
   7.85 – 8.85  unlock: a ring packet travels out through the sand from the slots' centre, the
                tray brightens, the grooves glow, the title turns to "Welcome back".
   8.80 – 9.50  the sand smooths over (grooves and rake lines fade), the light and title return.
   9.50 – 10    rest.
*/
enum SandPinCodeMotion {
    static let code = [2, 6, 0, 5, 1, 9]
    private static let wrong = [4, 8, 2, 7, 1, 5]
    private static let wrongAt = 1.25
    private static let wrongStep = 0.46
    private static let rightAt = 5.65
    private static let rightStep = 0.38
    static let carve = 0.26
    private static let pressLead = 0.08
    static let pressLen = 0.2
    static let shakeLen = 0.45
    private static let shakeAt = 3.95
    static let rakeLen = 0.8
    private static let rakeAt = 4.35
    static let grainLife = 0.9
    private static let unlockAt = 7.85
    private static let smoothAt = 8.8
    private static let smoothEnd = 9.5

    /// Slots: six, slotGap apart; a digit's stroke box is 24 × 34 scaled by digitScale.
    static let slots = 6
    static let slotGap = 50.0
    static let slotHalfH = 30.0
    static let digitScale = 1.05
    static let samples = 50
    /// The rake starts and ends this far outside the slot row.
    static let rakeOver = 80.0

    // Single-stroke digits in a 24 × 34 box, written the way a finger would draw them in sand.
    private static let digitSVG = [
        "M12 2 C4.5 2 2 10 2 17 C2 25 5 32 12 32 C19 32 22 25 22 17 C22 9 19.5 2 12 2",
        "M6.5 7.5 L13 2 L13 32",
        "M3 9 C3 4.5 7 2 12 2 C17 2 21 5 21 10 C21 16 15.5 20.5 3 32 L22 32",
        "M4 5 C7 2.8 10 2 12.5 2 C17.5 2 20.5 5 20.5 9 C20.5 13 17 16 11 16 C18 16 22 19.5 22 24.5 C22 29.5 17.5 32 12 32 C8 32 5 31 2.5 28.5",
        "M16 32 L16 2 L2.5 23 L23 23",
        "M20.5 2 L6.5 2 L4.5 15 C7 13.6 9.5 13 12 13 C18 13 22 16.5 22 22.5 C22 28.5 17.5 32 12 32 C8 32 5 30.6 3 28",
        "M19 3.6 C17 2.6 15 2 13 2 C6 2 3 9 3 19 C3 27 6.5 32 12.5 32 C18 32 21.5 28 21.5 22.5 C21.5 17 18 13.5 12.5 13.5 C8 13.5 4.6 16 3.2 19.5",
        "M2 2 L22 2 C15 11 11 20 9 32",
        "M12 16 C7 16 4 13 4 9 C4 5 7.5 2 12 2 C16.5 2 20 5 20 9 C20 13 17 16 12 16 C6 16 2.5 19.5 2.5 24 C2.5 28.5 6.5 32 12 32 C17.5 32 21.5 28.5 21.5 24 C21.5 19.5 18 16 12 16",
        "M21 14 C19.5 17.5 16 20 11.5 20 C6 20 2.5 16.5 2.5 11 C2.5 5.5 6.5 2 12 2 C18 2 21.5 6.5 21.5 14 C21.5 24 17.5 32 10 32 C8 32 6 31.6 4.5 30.6",
    ]

    /// Each digit's stroke, centred on (0, 0).
    static let paths: [Path] = digitSVG.map(parse)

    /// `samples` points evenly spaced along each stroke.
    static let samplePoints: [[CGPoint]] = paths.map { p in
        (0..<samples).map { i in
            let f = Double(i) / Double(samples - 1)
            if i == 0 { return p.trimmedPath(from: 0, to: 0.0001).currentPoint ?? .zero }
            return p.trimmedPath(from: 0, to: f).currentPoint ?? .zero
        }
    }

    /// The absolute M / L / C commands the digits use, scaled and centred like the Skia matrix.
    private static func parse(_ d: String) -> Path {
        let tokens = d.split(separator: " ")
        var path = Path()
        var i = 0
        var cmd: Character = "M"
        while i < tokens.count {
            var t = tokens[i]
            if let c = t.first, c.isLetter {
                cmd = c
                t = t.dropFirst()
                if t.isEmpty {
                    i += 1
                    continue
                }
            }
            // A number glued to its command letter, e.g. "M12".
            var nums: [Double] = [Double(t) ?? 0]
            let need = cmd == "C" ? 6 : 2
            var j = i + 1
            while nums.count < need && j < tokens.count {
                nums.append(Double(tokens[j]) ?? 0)
                j += 1
            }
            func p(_ k: Int) -> CGPoint { CGPoint(x: (nums[k] - 12) * digitScale, y: (nums[k + 1] - 17) * digitScale) }
            switch cmd {
            case "M": path.move(to: p(0))
            case "L": path.addLine(to: p(0))
            case "C": path.addCurve(to: p(4), control1: p(0), control2: p(2))
            default: break
            }
            i = j
        }
        return path
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// The tray's damped shake τ seconds after a wrong code.
    static func shake(_ tau: Double) -> Double {
        if tau < 0 || tau > shakeLen { return 0 }
        return 9 * sin(2 * .pi * 9 * tau) * exp(-tau * 6.5) * (1 - tau / shakeLen)
    }

    /// A key's squash τ seconds after it is pressed: in fast, out on a soft bounce.
    static func press(_ tau: Double) -> Double {
        if tau < 0 || tau > pressLen + 0.25 { return 0 }
        if tau < 0.06 { return easeOutCubic(tau / 0.06) }
        let u = (tau - 0.06) / (pressLen + 0.19)
        return exp(-u * 4) * cos(u * 7.5)
    }

    static func keyIndex(_ d: Int) -> Int { d == 0 ? 10 : d - 1 }

    /// The loop at time t. `x0` is the first slot's centre x.
    static func autoSand(_ t: Double, x0: Double) -> SandPinCodeSand {
        var s = SandPinCodeSand()
        let from = x0 - rakeOver
        let to = x0 + Double(slots - 1) * slotGap + rakeOver
        func passOf(_ i: Int) -> Double { rakeAt + (rakeLen * (x0 + Double(i) * slotGap - from)) / (to - from) }

        let right = t >= rightAt - 0.1
        for i in 0..<slots {
            if !right {
                s.digits[i] = wrong[i]
                s.carveAt[i] = wrongAt + Double(i) * wrongStep
                s.passAt[i] = passOf(i)
            } else if t < smoothEnd {
                s.digits[i] = code[i]
                s.carveAt[i] = rightAt + Double(i) * rightStep
            }
            s.grainDigits[i] = wrong[i]
            s.grainAt[i] = passOf(i)
        }
        // Pebble presses.
        let at = right ? rightAt : wrongAt
        let step = right ? rightStep : wrongStep
        let digits = right ? code : wrong
        for i in 0..<slots {
            let p = press(t - (at + Double(i) * step - pressLead))
            if p != 0 {
                s.key = keyIndex(digits[i])
                s.press = p
            }
        }
        s.shake = shake(t - shakeAt)
        let r = seg(t, rakeAt, rakeAt + rakeLen)
        s.rakeX = from + (to - from) * easeInOutCubic(r)
        s.rakeFrom = from
        s.rakeOn = t < rakeAt ? 0 : (t < rakeAt + rakeLen ? clamp01((t - rakeAt) / 0.12) : 1 - seg(t, rakeAt + rakeLen, rakeAt + rakeLen + 0.25))
        if t > rakeAt + rakeLen + 0.25 { s.rakeX = to + 200 }
        s.linesOn = t >= rakeAt ? 1 : 0
        s.ripple = t >= unlockAt ? t - unlockAt : -1
        s.bright = easeOutCubic(seg(t, unlockAt, unlockAt + 0.4)) * (1 - easeInOutCubic(seg(t, smoothAt + 0.1, smoothEnd)))
        s.unlock = easeOutCubic(seg(t, unlockAt + 0.1, unlockAt + 0.5)) * (1 - easeInOutCubic(seg(t, smoothAt + 0.15, smoothAt + 0.6)))
        s.fade = 1 - easeInOutCubic(seg(t, smoothAt, smoothEnd))
        if t >= smoothEnd { s.linesOn = 0 }
        return s
    }
}

/// The screen's state at one moment; arrays have one entry per slot.
struct SandPinCodeSand {
    var digits = [-1, -1, -1, -1, -1, -1]
    var carveAt = [-1.0, -1, -1, -1, -1, -1]
    /// When the rake swept over each slot's digit (−1: not swept).
    var passAt = [-1.0, -1, -1, -1, -1, -1]
    /// The digits that crumble into grains, and when (kept apart so a new code can be drawn
    /// while grains still settle).
    var grainDigits = [-1, -1, -1, -1, -1, -1]
    var grainAt = [-1.0, -1, -1, -1, -1, -1]
    var rakeX = -999.0
    var rakeOn = 0.0
    var rakeFrom = 0.0
    var linesOn = 0.0
    var shake = 0.0
    var ripple = -1.0
    var bright = 0.0
    var unlock = 0.0
    /// Groove opacity (the sand smoothing over).
    var fade = 1.0
    /// Which key is squashed (−1 none) and how much.
    var key = -1
    var press = 0.0
}

/// The user's take on the tray: digits and the times things happened, on the `now` clock.
struct SandPinCodeUser {
    var sand = SandPinCodeSand()
    var entered: [Int] = []
    var keyAt = -9.0
    var shakeAt = -9.0
    var rakeStart = -1.0
    var fingerRake = false
    var checkAt = -1.0
    var unlockAt = -1.0
    var returnAt = -1.0
}
