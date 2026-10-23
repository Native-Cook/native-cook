import SwiftUI
import UIKit

enum PolaroidDevelopWelcomeMetrics {
    // The camera, front on, in pt (the whole hero shrinks on short screens).
    static let cameraWidth: CGFloat = 236
    static let cameraHeight: CGFloat = 170
    static let cameraRadius: CGFloat = 36
    static let lens: CGFloat = 50
    // The print: an instant-film card with a square photo and a deep bottom border.
    static let printWidth: CGFloat = 152
    static let printHeight: CGFloat = 184
    static let photoInset: CGFloat = 10
    static let photoSize: CGFloat = printWidth - photoInset * 2
    static let captionSize: CGFloat = 17
    static let heroHeight: CGFloat = cameraHeight + 22 + printHeight + 18

    // A snap: the shutter sinks, the flash fires, and after `ejectDelay` the motor pushes the print
    // out of the slot over `ejectTime`. Before it is all the way out it starts to settle onto the
    // pile below on a spring, so the two moves blend into one.
    static let ejectDelay = 0.18
    static let ejectTime = 1.05
    static let ejectEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 0), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static let ejectTuck: Double = 14 // pt still in the slot when the motor stops
    static let settleAt = ejectDelay + ejectTime * 0.72
    static let settleDuration = 0.95
    static let settleRatio = 0.78
    static let settleLift = 0.045
    static let shudder = 0.6 // pt
    static let snapGap = 1.6 // s

    // Flash: the window lights up and a warm bloom spreads from it.
    static let flashIn = 0.05
    static let flashOut = 0.65
    static let bloomPeak = 0.38

    // Developing: from a dark blank through a pale, washed-out ghost to full colour, over
    // `developTime`; shaking the held print speeds it up to `shakeBoost` + 1 times.
    static let developTime = 4.5
    static let shakeBoost = 4.0
    static let shakeSpeed = 320.0 // pt/s of finger speed per unit of boost
    static let developBlur = 5.0

    // The pile: each print under the top one sits 3 pt higher, a little smaller and darker; the
    // fourth fades out.
    static let depthDuration = 0.52
    static let depthRatio = 0.85
    static let depthScale = 0.025
    static let depthDim = 0.07
    static let maxPrints = 5
    // Holding the top print: it lifts, leans into its motion and resists being pushed up into the
    // camera. A throw sends it under the pile.
    static let holdLift = 0.05
    static let tossDistance = 96.0
    static let tossSpeed = 700.0
    static let tossOut = 0.43 // screen widths
    static let tossOutDuration = 0.38
    static let tossOutRatio = 1.0
    static let tossBackDuration = 0.64
    static let tossBackRatio = 0.82
    static let returnDuration = 0.52
    static let returnRatio = 0.72
    static let panSlop: Double = 4
    static let tapMaxDuration = 0.5
    // Clearing the table: the prints fade and sink, the top one first.
    static let clearTime = 0.7
    static let clearStagger = 0.12
    static let clearEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.4, y: 0), endControlPoint: UnitPoint(x: 0.6, y: 1))

    // Arrival on screen: the camera and each line of copy rise `enterRise` pt and fade in.
    static let enterTime = 0.7
    static let enterRise: Double = 14
    static let enterStagger = 0.09
    static let introSnap = 1.0 // s, the first snap when not on autoPlay

    // autoPlay, in seconds of a loop that starts and ends on an empty table.
    static let loop = 14.0
    static let autoSnaps = [0.6, 4.6]
    static let autoShake = (7.2, 8.4)
    static let autoToss = 9.6
    static let autoClear = 12.0
    static let userHold = 4.0
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    /// Where each print comes to rest on the pile: a tilt (degrees) and an offset (pt).
    static let landings: [(tilt: Double, x: Double, y: Double)] = [
        (-6, -8, 4), (5, 10, -2), (-2, -2, 8), (7.5, 13, 2), (-8.5, -13, -1), (3, 5, 6), (-4, -6, -4), (6, 8, 3),
    ]

    /// A Skia blur sigma as a SwiftUI blur radius, which spreads about half as far per unit.
    static func blurRadius(_ sigma: Double) -> CGFloat { sigma * 2 }
}

enum PolaroidDevelopWelcomeMath {
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func smooth(_ x: Double) -> Double {
        let t = clamp01(x)
        return t * t * (3 - 2 * t)
    }

    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool { before < at && now >= at }

    /// Pushing a print up past the top of the pile meets more and more resistance (60 pt at most).
    static func resistUp(_ dy: Double) -> Double { dy >= 0 ? dy : -60 * (1 - exp(dy / 60)) }

    /// Reanimated's default timing curve, an ease-in-out quad.
    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    /// The colour of a developing print as a colour matrix (rows of five, offsets in 0…1), `d` from
    /// 0 (just out) to 1 (done). It starts as a flat dark brown with no picture, turns into a pale,
    /// low-contrast, slightly cyan ghost of the photo at 42%, and then gains contrast and colour.
    static func developMatrix(_ d: Double) -> [Double] {
        // The ghost: a quarter of the colour, half the contrast, lifted towards a cool grey.
        let s = 0.25
        let c = 0.5
        let lr = 0.2126 * (1 - s)
        let lg = 0.7152 * (1 - s)
        let lb = 0.0722 * (1 - s)
        let ghost = [
            c * (lr + s), c * lg, c * lb, 0, 0.4,
            c * lr, c * (lg + s), c * lb, 0, 0.43,
            c * lr, c * lg, c * (lb + s), 0, 0.46,
            0, 0, 0, 1, 0,
        ]
        let blank: [Double] = [0, 0, 0, 0, 0.2, 0, 0, 0, 0, 0.17, 0, 0, 0, 0, 0.14, 0, 0, 0, 1, 0]
        let full: [Double] = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0]
        if d < 0.42 {
            let t = smooth(d / 0.42)
            return (0..<20).map { blank[$0] + (ghost[$0] - blank[$0]) * t }
        }
        let t = smooth((d - 0.42) / 0.58)
        return (0..<20).map { ghost[$0] + (full[$0] - ghost[$0]) * t }
    }
}

/// Where everything sits, in pt. Hero coordinates (camera and pile) are unscaled; the hero scales
/// by `scale` about the camera's top centre.
struct PolaroidDevelopWelcomeLayout: Equatable {
    var width: CGFloat
    var height: CGFloat
    var scale: CGFloat
    var cameraX: CGFloat
    var cameraY: CGFloat
    var slotY: CGFloat
    var pileX: CGFloat
    var pileY: CGFloat
    var headerTop: CGFloat
    var copyTop: CGFloat
    var primaryTop: CGFloat

    init(width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) {
        typealias M = PolaroidDevelopWelcomeMetrics
        self.width = width
        self.height = height
        // From the bottom up: the secondary action, the primary one, the sub line and the headline.
        let secondaryTop = height - max(bottom, 12) - 6 - 44
        primaryTop = secondaryTop - 8 - 56
        copyTop = primaryTop - 24 - 44 - 10 - 76
        cameraY = top + 56
        let room = copyTop - 18 - cameraY
        scale = max(0.5, min(1, room / M.heroHeight))
        let spare = max(0, room / scale - M.heroHeight) / 2
        cameraX = (width - M.cameraWidth) / 2
        slotY = cameraY + M.cameraHeight - 9
        pileX = width / 2
        pileY = cameraY + M.cameraHeight + 22 + spare + M.printHeight / 2
        headerTop = top + 8
    }

    /// A screen point in hero coordinates.
    func heroPoint(_ p: CGPoint) -> CGPoint {
        CGPoint(x: width / 2 + (p.x - width / 2) / scale, y: cameraY + (p.y - cameraY) / scale)
    }
}

// MARK: - Animations

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct PolaroidDevelopWelcomeSpring {
    let target: Double
    let duration: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.duration = duration
        self.dampingRatio = dampingRatio
        var v = carried + velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite || k <= 0 { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, v, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
        let zeta = dampingRatio
        let x0 = value - target
        let v0 = velocity
        let t = dt
        if zeta < 1 {
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold || !value.isFinite {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven by one animation at a time, like a shared value: a spring, or a queue of waits
/// and timed moves (`withDelay`, `withSequence`, `withTiming`), with a callback when it finishes.
struct PolaroidDevelopWelcomeValue {
    enum Step {
        case wait(Double)
        case timing(to: Double, duration: Double, curve: (Double) -> Double)
    }

    var value: Double
    private var spring: PolaroidDevelopWelcomeSpring?
    private var steps: [Step] = []
    private var stepFrom = 0.0
    private var stepTime = 0.0
    private var pendingSpring: (target: Double, duration: Double, ratio: Double)?
    private var done: (() -> Void)?

    init(_ value: Double) { self.value = value }

    var moving: Bool { spring != nil || !steps.isEmpty || pendingSpring != nil }
    var springTarget: Double? { spring?.target ?? pendingSpring?.target }

    mutating func set(_ v: Double) {
        spring = nil
        steps = []
        pendingSpring = nil
        done = nil
        value = v
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, then: (() -> Void)? = nil) {
        let carried = spring?.velocity ?? 0
        steps = []
        pendingSpring = nil
        done = then
        spring = PolaroidDevelopWelcomeSpring(from: value, to: target, velocity: velocity, carried: carried, duration: duration, dampingRatio: dampingRatio)
    }

    /// Timed moves one after another, starting now.
    mutating func sequence(_ next: [Step], then: (() -> Void)? = nil) {
        spring = nil
        pendingSpring = nil
        steps = next
        stepFrom = value
        stepTime = 0
        done = then
    }

    mutating func timing(to target: Double, duration: Double, curve: @escaping (Double) -> Double = PolaroidDevelopWelcomeMath.inOutQuad, then: (() -> Void)? = nil) {
        sequence([.timing(to: target, duration: duration, curve: curve)], then: then)
    }

    /// A spring that starts after a wait (`withDelay(withSpring)`).
    mutating func delayedSpring(after delay: Double, to target: Double, duration: Double, dampingRatio: Double, then: (() -> Void)? = nil) {
        sequence([.wait(delay)], then: then)
        pendingSpring = (target, duration, dampingRatio)
    }

    /// Moves the value on by `dt`; returns the finished moves' callback for the caller to run.
    @discardableResult
    mutating func step(dt: Double) -> (() -> Void)? {
        if var running = spring {
            if running.step(&value, dt: dt) {
                spring = nil
                let callback = done
                done = nil
                return callback
            }
            spring = running
            return nil
        }
        var left = dt
        while left > 0, let first = steps.first {
            switch first {
            case let .wait(duration):
                let use = min(left, duration - stepTime)
                stepTime += use
                left -= use
                if stepTime >= duration - 1e-9 {
                    steps.removeFirst()
                    stepFrom = value
                    stepTime = 0
                } else {
                    return nil
                }
            case let .timing(to, duration, curve):
                stepTime += left
                left = 0
                let p = min(1, stepTime / max(duration, 0.001))
                value = stepFrom + (to - stepFrom) * curve(p)
                if p >= 1 {
                    steps.removeFirst()
                    stepFrom = value
                    stepTime = 0
                }
            }
        }
        if steps.isEmpty {
            if let next = pendingSpring {
                pendingSpring = nil
                spring = PolaroidDevelopWelcomeSpring(from: value, to: next.target, velocity: 0, carried: 0, duration: next.duration, dampingRatio: next.ratio)
            } else if let callback = done {
                done = nil
                return callback
            }
        }
        return nil
    }
}

// MARK: - Prints

/// One print: it slides out of the camera's slot and, still moving, settles onto the pile and
/// develops; it sinks a level each time a newer print lands on it.
@MainActor
final class PolaroidDevelopWelcomePrint {
    let key: Int
    let shot: Int
    let byUser: Bool
    var eject: PolaroidDevelopWelcomeValue
    var settle: PolaroidDevelopWelcomeValue
    var appear: PolaroidDevelopWelcomeValue
    var level = PolaroidDevelopWelcomeValue(0)
    var develop = 0.0
    var landed = false

    init(key: Int, shot: Int, byUser: Bool, reduced: Bool) {
        typealias M = PolaroidDevelopWelcomeMetrics
        self.key = key
        self.shot = shot
        self.byUser = byUser
        eject = PolaroidDevelopWelcomeValue(reduced ? 1 : 0)
        settle = PolaroidDevelopWelcomeValue(reduced ? 1 : 0)
        appear = PolaroidDevelopWelcomeValue(reduced ? 0 : 1)
        if reduced {
            appear.timing(to: 1, duration: 0.42)
        } else {
            eject.sequence([.wait(M.ejectDelay), .timing(to: 1, duration: M.ejectTime, curve: { M.ejectEase.value(at: $0) })])
            settle.delayedSpring(after: M.settleAt, to: 1, duration: M.settleDuration, dampingRatio: M.settleRatio)
        }
    }

    var resting: Bool { settle.value > 0.94 && appear.value > 0.9 }
}

/// Where a print sits this frame, in hero coordinates.
struct PolaroidDevelopWelcomePose {
    var x: Double
    var y: Double
    var tilt: Double
    var lift: Double
    var alpha: Double
    var dim: Double
}

// MARK: - The engine

/// The table's state, stepped once a frame like the RN frame callbacks, and the gestures that move it.
@MainActor
final class PolaroidDevelopWelcomeEngine {
    private typealias M = PolaroidDevelopWelcomeMetrics
    private typealias X = PolaroidDevelopWelcomeMath

    let shots: Int
    private(set) var prints: [PolaroidDevelopWelcomePrint] = []
    private var taken = 0
    private var nextKey = 0

    private(set) var heldKey = -1
    private(set) var heldX = PolaroidDevelopWelcomeValue(0)
    private(set) var heldY = PolaroidDevelopWelcomeValue(0)
    private(set) var heldTilt = PolaroidDevelopWelcomeValue(0)
    private(set) var holding = PolaroidDevelopWelcomeValue(0)
    private(set) var boost = PolaroidDevelopWelcomeValue(0)
    private(set) var tossKey = -1
    private(set) var tossX = PolaroidDevelopWelcomeValue(0)
    private(set) var tossY = PolaroidDevelopWelcomeValue(0)
    private(set) var tossTilt = PolaroidDevelopWelcomeValue(0)
    private(set) var tossFade = PolaroidDevelopWelcomeValue(0)
    private(set) var clearing = PolaroidDevelopWelcomeValue(0)
    private(set) var shutter = PolaroidDevelopWelcomeValue(0)
    private(set) var flash = PolaroidDevelopWelcomeValue(0)
    private(set) var cameraPress = PolaroidDevelopWelcomeValue(0)
    private(set) var readyGlow = 0.0

    private(set) var time = 0.0
    private var lastSnap = -10.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var introDone = false
    private var autoShaking = false
    private var reduced = false
    private var lastDate: Date?
    private var lastAuto: Bool?
    private(set) var layout: PolaroidDevelopWelcomeLayout?
    private(set) var photos: [UIImage?]
    private(set) var grain: CGImage?

    // The touch under way.
    private enum Touch { case pending, panning }
    private var touch: Touch?
    private var touchStart: Date?
    private var grabbing = false
    private var grabX = 0.0
    private var grabY = 0.0

    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    init(shots: Int) {
        self.shots = max(1, shots)
        photos = Array(repeating: nil, count: self.shots)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = image
    }

    func grainArrived(_ image: CGImage) { grain = image }

    /// 0…1, how far an element that starts arriving `delay` s after the screen opened has come.
    func entered(after delay: Double) -> Double {
        M.easeOut.value(at: X.clamp01((time - delay) / M.enterTime))
    }

    /// Key of the top print once it has settled on the pile (it can be picked up), else -1.
    var readyKey: Int {
        guard let top = prints.last, top.resting else { return -1 }
        return top.key
    }

    func step(to date: Date, layout: PolaroidDevelopWelcomeLayout, autoPlay: Bool, reduced: Bool) {
        self.layout = layout
        self.reduced = reduced
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
            introDone = introDone || autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.064)
        guard dt > 0 else { return }
        var finished: [() -> Void] = []
        let paths: [ReferenceWritableKeyPath<PolaroidDevelopWelcomeEngine, PolaroidDevelopWelcomeValue>] = [
            \.heldX, \.heldY, \.heldTilt, \.holding, \.boost, \.tossX, \.tossY, \.tossTilt, \.tossFade,
            \.clearing, \.shutter, \.flash, \.cameraPress,
        ]
        for value in paths {
            if let callback = self[keyPath: value].step(dt: dt) { finished.append(callback) }
        }
        for print in prints {
            print.eject.step(dt: dt)
            print.settle.step(dt: dt)
            print.appear.step(dt: dt)
            print.level.step(dt: dt)
            // Developing runs once the print starts to show; shaking the held one speeds it up.
            if print.develop < 1 && print.eject.value >= 0.2 {
                let rate = 1 + (heldKey == print.key ? boost.value : 0)
                print.develop = min(1, print.develop + dt / M.developTime * rate)
            }
            // A print the user took lands with a light haptic.
            let down = print.resting
            if down && !print.landed && print.byUser { light.impactOccurred() }
            print.landed = down
        }
        finished.forEach { $0() }
        frameCallback(dt: dt)
    }

    private func frameCallback(dt: Double) {
        time += dt
        let ready = time - lastSnap >= M.snapGap && clearing.value == 0 ? 1.0 : 0.0
        readyGlow += (ready - readyGlow) * (1 - exp(-dt * 4))
        if !introDone && time >= M.introSnap {
            introDone = true
            snap(byUser: false)
        }
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // Start over from an empty table: clear what the user left first.
                if !prints.isEmpty {
                    clock = M.autoClear
                    clear()
                } else {
                    clock = 0
                }
            }
            return
        }
        guard looping else { return }
        let before = clock
        let now = before + dt
        for at in M.autoSnaps where X.crossed(before, now, at) { snap(byUser: false) }
        // Shaking the top print: it is held and wiggled, which develops it faster.
        if X.crossed(before, now, M.autoShake.0) && readyKey >= 0 && tossKey < 0 {
            autoShaking = true
            heldKey = readyKey
            holding.timing(to: 1, duration: 0.14)
        }
        if autoShaking && now < M.autoShake.1 {
            let s = (now - M.autoShake.0) / (M.autoShake.1 - M.autoShake.0)
            let envelope = sin(.pi * X.clamp01(s))
            heldX.set(sin(now * .pi * 2 * 3.2) * 12 * envelope)
            heldY.set(-6 * envelope)
            heldTilt.set(cos(now * .pi * 2 * 3.2) * 5 * envelope)
            boost.set(M.shakeBoost * envelope)
        }
        if X.crossed(before, now, M.autoShake.1) && autoShaking {
            autoShaking = false
            heldX.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio)
            heldY.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio)
            heldTilt.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio)
            holding.timing(to: 0, duration: 0.22)
            boost.timing(to: 0, duration: 0.6)
        }
        if X.crossed(before, now, M.autoToss) && readyKey >= 0 && prints.count > 1 && tossKey < 0 {
            toss(readyKey, direction: 1, velocity: 900)
        }
        if X.crossed(before, now, M.autoClear) { clear() }
        clock = now >= M.loop ? now - M.loop : now
    }

    /// Every print springs to its level in the pile (0 on top).
    private func relevel() {
        for (rank, print) in prints.enumerated() {
            let depth = Double(prints.count - 1 - rank)
            if print.level.springTarget == depth || (!print.level.moving && print.level.value == depth) { continue }
            if reduced {
                print.level.timing(to: depth, duration: 0.26)
            } else {
                print.level.spring(to: depth, duration: M.depthDuration, dampingRatio: M.depthRatio)
            }
        }
    }

    /// Fires the shutter: flash, a jolt, and a new print on its way out of the camera.
    private func snap(byUser: Bool) {
        if time - lastSnap < M.snapGap || clearing.value > 0 { return }
        lastSnap = time
        shutter.sequence([.timing(to: 1, duration: 0.07, curve: X.inOutQuad), .timing(to: 0, duration: 0.2, curve: X.inOutQuad)])
        let peak = reduced ? 0.35 : 1
        flash.sequence([.timing(to: peak, duration: M.flashIn, curve: X.inOutQuad), .timing(to: 0, duration: M.flashOut) { M.easeOut.value(at: $0) }])
        if byUser { medium.impactOccurred() }
        nextKey += 1
        prints.append(PolaroidDevelopWelcomePrint(key: nextKey, shot: taken % shots, byUser: byUser, reduced: reduced))
        taken += 1
        if prints.count > M.maxPrints { prints.removeFirst(prints.count - M.maxPrints) }
        relevel()
    }

    private func clear() {
        if prints.isEmpty { return }
        clearing.timing(to: 1, duration: reduced ? 0.4 : M.clearTime, curve: { M.clearEase.value(at: $0) }) { [weak self] in
            guard let self else { return }
            self.prints = []
            self.taken = 0
            self.clearing.set(0)
        }
    }

    /// Throws the top print aside (`direction` ±1), from where it slides back under the pile.
    private func toss(_ key: Int, direction: Double, velocity: Double) {
        tossKey = key
        tossX.set(heldKey == key ? heldX.value : 0)
        tossY.set(heldKey == key ? X.resistUp(heldY.value) : 0)
        tossTilt.set(heldKey == key ? heldTilt.value : 0)
        tossFade.set(0)
        heldKey = -1
        heldX.set(0)
        heldY.set(0)
        heldTilt.set(0)
        holding.set(0)
        if reduced {
            tossFade.timing(to: 1, duration: 0.2, then: { [weak self] in self?.tossedOut(key) })
            return
        }
        let width = Double(layout?.width ?? 400)
        tossY.spring(to: 22, duration: M.tossOutDuration, dampingRatio: M.tossOutRatio)
        tossTilt.spring(to: direction * 14, duration: M.tossOutDuration, dampingRatio: M.tossOutRatio)
        tossX.spring(to: direction * width * M.tossOut, duration: M.tossOutDuration, dampingRatio: M.tossOutRatio, velocity: velocity) { [weak self] in
            self?.tossedOut(key)
        }
    }

    /// The thrown print is out to the side: it goes under the pile and slides back in.
    private func tossedOut(_ key: Int) {
        if let index = prints.firstIndex(where: { $0.key == key }) {
            let thrown = prints.remove(at: index)
            prints.insert(thrown, at: 0)
            relevel()
        }
        let back: () -> Void = { [weak self] in self?.tossKey = -1 }
        if reduced {
            tossX.set(0)
            tossY.set(0)
            tossTilt.set(0)
            tossFade.sequence([.wait(0.06), .timing(to: 0, duration: 0.22, curve: X.inOutQuad)], then: back)
            return
        }
        tossX.delayedSpring(after: 0.06, to: 0, duration: M.tossBackDuration, dampingRatio: M.tossBackRatio, then: back)
        tossY.delayedSpring(after: 0.06, to: 0, duration: M.tossBackDuration, dampingRatio: M.tossBackRatio)
        tossTilt.delayedSpring(after: 0.06, to: 0, duration: M.tossBackDuration, dampingRatio: M.tossBackRatio)
    }

    func pose(of print: PolaroidDevelopWelcomePrint) -> PolaroidDevelopWelcomePose {
        guard let l = layout else { return PolaroidDevelopWelcomePose(x: 0, y: 0, tilt: 0, lift: 0, alpha: 0, dim: 0) }
        let landing = M.landings[print.shot % M.landings.count]
        let lv = print.level.value
        // Its place on the pile, a little higher and smaller for every print on top of it.
        let restX = Double(l.pileX) + landing.x
        let restY = Double(l.pileY) + landing.y - lv * 3
        // Coming out of the slot, pushed by the motor with a faint shudder.
        let e = print.eject.value
        let shudder = e > 0 && e < 1 ? sin(e * 80) * M.shudder : 0
        let outEnd = Double(l.slotY + M.printHeight / 2) - M.ejectTuck
        let outY = X.lerp(Double(l.slotY - M.printHeight / 2), outEnd, e)
        let st = print.settle.value
        var x = X.lerp(Double(l.pileX) + shudder, restX, st)
        var y = outY + (restY - outEnd) * st
        var tilt = landing.tilt * st
        var lift = sin(.pi * X.clamp01(st)) * M.settleLift - lv * M.depthScale
        var alpha = X.clamp01(3.6 - lv)
        if heldKey == print.key {
            x += heldX.value
            y += X.resistUp(heldY.value)
            tilt += heldTilt.value
            lift += holding.value * M.holdLift
        }
        if tossKey == print.key {
            x += tossX.value
            y += tossY.value
            tilt += tossTilt.value
            alpha *= 1 - tossFade.value
        }
        // Clearing the table: fade and sink, the top print first.
        let c = X.smooth(clearing.value * (1 + M.clearStagger * 4) - lv * M.clearStagger)
        y += c * 18
        lift -= c * 0.04
        alpha *= (1 - c) * print.appear.value
        return PolaroidDevelopWelcomePose(x: x, y: y, tilt: tilt, lift: lift, alpha: alpha, dim: min(0.3, max(0, lv) * M.depthDim))
    }

    // MARK: Touch

    private func onCamera(_ p: CGPoint) -> Bool {
        guard let l = layout else { return false }
        let h = l.heroPoint(p)
        return h.x > l.cameraX - 8 && h.x < l.cameraX + M.cameraWidth + 8 && h.y > l.cameraY - 8 && h.y < l.cameraY + M.cameraHeight + 8
    }

    private func onPile(_ p: CGPoint) -> Bool {
        guard let l = layout else { return false }
        let h = l.heroPoint(p)
        return abs(h.x - l.pileX) < M.printWidth * 0.62 && abs(h.y - l.pileY) < M.printHeight * 0.6
    }

    // One recogniser stands in for the race of a pan (after 4 pt) and a tap (a touch lifted within
    // 500 ms that never turned into a pan).
    func touchMoved(_ value: DragGesture.Value) {
        hold = M.userHold
        let scale = Double(layout?.scale ?? 1)
        if touch == nil {
            touch = .pending
            touchStart = value.time
            if onCamera(value.startLocation) { cameraPress.timing(to: 1, duration: 0.09) }
        }
        let tx = Double(value.translation.width)
        let ty = Double(value.translation.height)
        if touch == .pending && hypot(tx, ty) >= M.panSlop {
            touch = .panning
            cameraPress.timing(to: 0, duration: 0.2)
            // The top print can be picked up once it has settled, from where it is now.
            let top = readyKey
            if top >= 0 && tossKey < 0 && clearing.value == 0 && onPile(value.location) {
                autoShaking = false
                if heldKey != top {
                    heldX.set(0)
                    heldY.set(0)
                    heldTilt.set(0)
                }
                heldKey = top
                grabbing = true
                grabX = heldX.value - tx / scale
                grabY = heldY.value - ty / scale
                holding.timing(to: 1, duration: 0.14)
            }
        }
        guard touch == .panning, grabbing, heldKey >= 0, heldKey != tossKey else { return }
        let vx = Double(value.velocity.width)
        let vy = Double(value.velocity.height)
        heldX.set(grabX + tx / scale)
        heldY.set(grabY + ty / scale)
        // The print leans into the way it is moving, a little like a card held by its edge.
        heldTilt.set(max(-16, min(16, heldX.value * 0.05 + vx * 0.006)))
        // Shaking speeds up developing.
        let speed = hypot(vx, vy)
        boost.set(boost.value + (min(M.shakeBoost, speed / M.shakeSpeed) - boost.value) * 0.2)
    }

    func touchEnded(_ value: DragGesture.Value) {
        let state = touch
        let start = touchStart
        touch = nil
        touchStart = nil
        hold = M.userHold
        cameraPress.timing(to: 0, duration: 0.2)
        if state == .panning {
            release(vx: Double(value.velocity.width), vy: Double(value.velocity.height))
        } else if state == .pending, let start, value.time.timeIntervalSince(start) <= M.tapMaxDuration, onCamera(value.location) {
            snap(byUser: true)
        }
        finalize()
    }

    /// A touch the system cancelled: a held print goes back where it was.
    func touchLost() {
        guard let state = touch else { return }
        touch = nil
        touchStart = nil
        cameraPress.timing(to: 0, duration: 0.2)
        if state == .panning { release(vx: 0, vy: 0) }
        finalize()
    }

    /// The accessibility action: a snap, as a tap on the shutter.
    func snapFromAccessibility() {
        hold = M.userHold
        snap(byUser: true)
    }

    /// A touch on the buttons below pauses the autoPlay too.
    func touched() {
        hold = M.userHold
    }

    private func release(vx: Double, vy: Double) {
        guard grabbing, heldKey >= 0 else { return }
        let scale = Double(layout?.scale ?? 1)
        let throwIt = prints.count > 1 && (abs(heldX.value) > M.tossDistance || abs(vx) > M.tossSpeed)
        if throwIt {
            let direction: Double = (abs(vx) > M.tossSpeed ? vx : heldX.value) > 0 ? 1 : -1
            toss(heldKey, direction: direction, velocity: vx / scale)
            selection.selectionChanged()
            return
        }
        // Back to its place, carrying the finger's speed.
        heldX.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio, velocity: vx / scale)
        heldY.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio, velocity: vy / scale)
        heldTilt.spring(to: 0, duration: M.returnDuration, dampingRatio: M.returnRatio)
    }

    private func finalize() {
        grabbing = false
        if !autoShaking { holding.timing(to: 0, duration: 0.22) }
        boost.timing(to: 0, duration: 0.6)
    }
}
