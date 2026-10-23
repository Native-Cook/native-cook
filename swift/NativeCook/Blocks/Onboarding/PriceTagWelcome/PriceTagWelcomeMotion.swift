import Foundation

/// Price Tag Welcome - motion, value for value from motion.ts. Distances in ref px (1206 px = the
/// screen's width), times in seconds. A tap freezes a price (frost grows, the price blurs, "Wait 24h"
/// and a countdown are etched); a second tap thaws it. autoPlay: every value is a pure function of
/// the loop time t, 0 <= t < loop.
enum PriceTagWelcomeMotion {
    static let loop = 8.0
    static let itemCount = 5
    static let resumeAfter = 2.0

    enum Script {
        static let pressIn = 0.8
        static let freeze = 0.9
        static let tapItem = 1
        static let thawPress = 4.3
        static let thaw = 4.4
        static let seam = (6.6, 7.4)
    }

    enum Freeze {
        static let grow = 1.25
        static let lockAt = 0.19
        static let unlockAt = 0.3
        static let melt = 1.4
        static let kick = 0.9
        static let drag = 0.55
    }

    enum Etch {
        static let priceOut = (0.05, 0.4)
        static let wait = (0.4, 0.8)
        static let timer = (0.85, 1.3)
        static let firstTick = 1.85
        static let out = (0.0, 0.4)
        static let priceIn = (0.35, 0.7)
        static let priceGhost = 0.22
        static let priceBlur = 3.5
    }

    enum Tick {
        static let omega = 22.0
        static let zeta = 0.62
    }

    static let stillFade = 0.2

    enum Swing {
        static let hertz = 1.35
        static let zeta = 0.13
        static let tune = [1.0, 0.94, 1.06, 0.97, 1.03]
        static let gain = 0.00095
        static let maxDegrees = 24.0
    }

    enum Press {
        static let scale = 0.97
        static let durationIn = 0.1
        static let durationOut = 0.2
    }

    static let decayTau = 0.36

    enum Home {
        static let base = 0.45
        static let perPixel = 0.0005
        static let maximum = 1.1
        static let thawStagger = 0.06
    }

    enum Glint {
        static let fullSpeed = 1500.0
        static let alpha = 0.5
    }

    static let tapSlop = 30.0
    static let mountLead = 60.0
    static let tableHertz = 120.0
    static let simulationHertz = 240.0

    struct Spring: Equatable {
        let x: Double
        let velocity: Double
    }

    struct SwingSample: Equatable {
        let theta: Double
        let velocity: Double
    }

    /// What a tag and its sticker show. `lost` 0...1: how far the price has gone under the ice.
    struct Look: Equatable {
        var on = false
        var grow = 0.0
        var melt = 0.0
        var fade = 0.0
        var priceAlpha = 1.0
        var lost = 0.0
        var wait = 0.0
        var timer = 0.0
        var out = 0.0
    }

    struct Frame: Equatable {
        var x: Double
        var velocity: Double
        var theta: [Double]
        var freezeTime: [Double]
        var thawTime: [Double]
        var press: [Double]
        var clock: Double
        var glint: Double
        var glintAlpha: Double
        var moving: Bool

        static let rest = Frame(
            x: 0, velocity: 0,
            theta: Array(repeating: 0, count: itemCount),
            freezeTime: Array(repeating: -1, count: itemCount),
            thawTime: Array(repeating: -1, count: itemCount),
            press: Array(repeating: 0, count: itemCount),
            clock: 0, glint: 0, glintAlpha: 0, moving: false
        )
    }

    // MARK: helpers

    static func clamp01(_ value: Double) -> Double { min(max(value, 0), 1) }

    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp01((time - start) / (end - start))
    }

    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    static func modulo(_ value: Double, _ divisor: Double) -> Double {
        (value.truncatingRemainder(dividingBy: divisor) + divisor).truncatingRemainder(dividingBy: divisor)
    }

    static func smoothstep(_ progress: Double) -> Double {
        let x = clamp01(progress)
        return x * x * (3 - 2 * x)
    }

    static func easeInOut(_ progress: Double) -> Double {
        let x = clamp01(progress)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func wrapX(restX: Double, offset: Double) -> Double {
        PriceTagWelcomeTheme.wrapLeft
            + modulo(restX + offset - PriceTagWelcomeTheme.wrapLeft, PriceTagWelcomeTheme.period)
    }

    /// Step response of an underdamped spring 0 -> 1 after `time` seconds.
    static func springStep(time: Double, omega: Double, zeta: Double) -> Spring {
        guard time > 0 else { return Spring(x: 0, velocity: 0) }
        let damped = omega * sqrt(1 - zeta * zeta)
        let envelope = exp(-zeta * omega * time)
        let cosine = cos(damped * time)
        let sine = sin(damped * time)
        return Spring(
            x: 1 - envelope * (cosine + zeta * omega / damped * sine),
            velocity: envelope * omega * omega / damped * sine
        )
    }

    // MARK: the freeze

    /// From tau (seconds since the freeze tap, -1: not frozen) and mu (seconds since the thaw tap, -1: not thawing).
    static func frostLook(freeze ft: Double, thaw mt: Double, still: Bool) -> Look {
        if ft < 0 { return Look() }
        let melt = mt < 0 ? 0 : clamp01(mt / Freeze.melt)
        if still {
            let inAlpha = clamp01(ft / stillFade) * (mt < 0 ? 1 : 1 - clamp01(mt / stillFade))
            return Look(on: true, grow: 1, melt: 0, fade: inAlpha,
                        priceAlpha: lerp(1, Etch.priceGhost, inAlpha), lost: inAlpha,
                        wait: inAlpha, timer: inAlpha, out: 0)
        }
        let g = clamp01(ft / Freeze.grow)
        let grow = 1 - (1 - g) * (1 - g) * (1 - g)
        let gone = smoothstep(segment(ft, Etch.priceOut.0, Etch.priceOut.1))
        let back = mt < 0 ? 0 : smoothstep(segment(mt, Etch.priceIn.0, Etch.priceIn.1))
        let lost = gone * (1 - back)
        return Look(
            on: true, grow: grow, melt: melt, fade: 1,
            priceAlpha: lerp(1, Etch.priceGhost, lost), lost: lost,
            wait: segment(ft, Etch.wait.0, Etch.wait.1),
            timer: segment(ft, Etch.timer.0, Etch.timer.1),
            out: mt < 0 ? 0 : smoothstep(segment(mt, Etch.out.0, Etch.out.1))
        )
    }

    /// The countdown at tau: seconds left now and before the last tick, and the roll between them.
    static func countdown(at ft: Double, still: Bool) -> (left: Int, previous: Int, roll: Double) {
        let start = PriceTagWelcomeTheme.Copy.countdown
        if ft < Etch.firstTick { return (start, start, 1) }
        let n = Int(floor(ft - Etch.firstTick)) + 1
        let since = ft - Etch.firstTick - Double(n - 1)
        return (start - n, start - n + 1,
                still ? 1 : springStep(time: since, omega: Tick.omega, zeta: Tick.zeta).x)
    }

    /// Digit `col` (0...5 of hh mm ss) of a number of seconds.
    static func digit(of secs: Int, col: Int) -> Int {
        let hh = secs / 3600
        let mm = (secs / 60) % 60
        let ss = secs % 60
        let value = col < 2 ? hh : col < 4 ? mm : ss
        return col % 2 == 0 ? (value / 10) % 10 : value % 10
    }

    /// Where a rolling digit column stands, as a line of the strip "0 1 2 ... 9 0".
    static func stripLine(from: Int, to: Int, roll: Double) -> Double {
        if from == to { return Double(to) }
        let a = from == 0 ? 10.0 : Double(from)
        return a + (Double(to) - a) * roll
    }

    // MARK: the script

    static func scriptFrost(index: Int, time t: Double) -> (freeze: Double, thaw: Double) {
        if index != Script.tapItem || t < Script.freeze { return (-1, -1) }
        let mt = t >= Script.thaw ? t - Script.thaw : -1
        if mt >= Freeze.melt { return (-1, -1) }
        return (t - Script.freeze, mt)
    }

    static func scriptPress(index: Int, time: Double) -> Double {
        guard index == Script.tapItem else { return 0 }
        func hold(_ start: Double, _ end: Double) -> Double {
            smoothstep(segment(time, start, start + Press.durationIn))
                * (1 - smoothstep(segment(time, end, end + Press.durationOut)))
        }
        return max(hold(Script.pressIn, Script.freeze), hold(Script.thawPress, Script.thaw))
    }

    static func omega(index: Int) -> Double {
        2 * .pi * Swing.hertz * Swing.tune[index]
    }

    static func swingStep(theta: Double, velocity: Double, index: Int,
                          acceleration: Double, delta: Double) -> SwingSample {
        let natural = omega(index: index)
        let angularAcceleration = -natural * natural * theta
            - 2 * Swing.zeta * natural * velocity
            + Swing.gain * acceleration
        let nextVelocity = velocity + angularAcceleration * delta
        return SwingSample(theta: theta + nextVelocity * delta, velocity: nextVelocity)
    }

    /// Is a tag held by its frost (frozen, the lock reached, not yet released by the thaw)?
    static func lockedBy(freeze ft: Double, thaw mt: Double) -> Bool {
        ft >= Freeze.lockAt && (mt < 0 || mt < Freeze.unlockAt)
    }

    private static let swingTable: [[Double]] = {
        let steps = Int((loop * simulationHertz).rounded())
        let sampleEvery = Int(simulationHertz / tableHertz)
        return (0..<itemCount).map { index in
            var theta = 0.0
            var velocity = 0.0
            var samples = [0.0]
            for step in 1...steps {
                let time = Double(step) / simulationHertz
                if index == Script.tapItem && time - 1 / simulationHertz < Script.freeze && time >= Script.freeze {
                    velocity += Freeze.kick
                }
                let f = scriptFrost(index: index, time: time)
                if lockedBy(freeze: f.freeze, thaw: f.thaw) {
                    velocity = 0
                } else {
                    let next = swingStep(theta: theta, velocity: velocity, index: index,
                                         acceleration: 0, delta: 1 / simulationHertz)
                    theta = next.theta
                    velocity = next.velocity
                }
                if step.isMultiple(of: sampleEvery) { samples.append(theta) }
            }
            return samples
        }
    }()

    static func scriptSwing(index: Int, time: Double) -> SwingSample {
        let table = swingTable[index]
        let exact = time * tableHertz
        let lower = min(Int(floor(exact)), table.count - 2)
        let progress = exact - Double(lower)
        let seam = 1 - smoothstep(segment(time, Script.seam.0, Script.seam.1))
        return SwingSample(
            theta: lerp(table[lower], table[lower + 1], progress) * seam,
            velocity: (table[lower + 1] - table[lower]) * tableHertz * seam
        )
    }

    static func softDegrees(_ radians: Double) -> Double {
        let degrees = radians * 180 / .pi
        return Swing.maxDegrees * tanh(degrees / Swing.maxDegrees)
    }

    static func glint(x: Double, velocity: Double) -> (progress: Double, alpha: Double) {
        (
            modulo(-x, PriceTagWelcomeTheme.period) / PriceTagWelcomeTheme.period,
            smoothstep(abs(velocity) / Glint.fullSpeed)
        )
    }

    /// The autoPlay frame at loop time t (the row stands still; the freeze is the show).
    static func frame(at rawTime: Double) -> Frame {
        let time = modulo(rawTime, loop)
        var theta: [Double] = []
        var freeze: [Double] = []
        var thaw: [Double] = []
        var press: [Double] = []
        for index in 0..<itemCount {
            theta.append(softDegrees(scriptSwing(index: index, time: time).theta))
            let f = scriptFrost(index: index, time: time)
            freeze.append(f.freeze)
            thaw.append(f.thaw)
            press.append(scriptPress(index: index, time: time))
        }
        return Frame(x: 0, velocity: 0, theta: theta, freezeTime: freeze, thawTime: thaw,
                     press: press, clock: time, glint: 0, glintAlpha: 0, moving: true)
    }

    // MARK: geometry

    static func hitItem(x: Double, y: Double, offset: Double) -> Int {
        for (index, item) in PriceTagWelcomeTheme.items.enumerated() {
            let originX = wrapX(restX: item.x, offset: offset)
            let inSticker = x >= originX + 12 && x <= originX + item.width - 12
                && y >= item.y + 12 && y <= item.y + item.height - 12
            let tagX = item.tag.centerX - item.x + originX
            let inTag = abs(x - tagX) <= item.tag.width / 2 + 12
                && abs(y - item.tag.centerY) <= item.tag.height / 2 + 12
            if inSticker || inTag { return index }
        }
        return -1
    }

    /// Bit i: item i's frost canvases (sticker + halo, with the mount lead) overlap the screen.
    static func visibleMask(offset: Double) -> Int {
        let reach = PriceTagWelcomeTheme.Space.frostMargin + mountLead
        var mask = 0
        for (index, item) in PriceTagWelcomeTheme.items.enumerated() {
            let x = wrapX(restX: item.x, offset: offset)
            if x + item.width + reach > 0 && x - reach < PriceTagWelcomeTheme.referenceWidth { mask |= 1 << index }
        }
        return mask
    }

    /// The pivot of tag i relative to its centre, as a unit point of the tag.
    static func pivotAnchor() -> (x: Double, y: Double) {
        (0.5 + PriceTagWelcomeTheme.pivotX, 0.5 + PriceTagWelcomeTheme.pivotY)
    }
}
