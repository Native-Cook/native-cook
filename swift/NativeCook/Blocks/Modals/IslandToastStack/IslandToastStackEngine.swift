import SwiftUI
import UIKit

// MARK: - Constants

enum IslandToastStackMetrics {
    // A dark lock screen: deep ink with a few wide pools of colour, so the cards have something to
    // show through them. The island stays pure black, like the hardware it sits on.
    static let background = Color(hex: 0x07080e)
    static let wallTopHex: UInt32 = 0x07080e
    static let wallBottomHex: UInt32 = 0x0c0b13
    static let wallTop = Color(hex: wallTopHex)
    static let wallBottom = Color(hex: wallBottomHex)
    /// Pools of light in the wallpaper: centre (share of width, height), radius (share of width), rgb, alpha.
    static let pools: [(x: Double, y: Double, r: Double, rgb: UInt32, a: Double)] = [
        (0.92, 0.1, 1.0, 0x3a54c4, 0.55),
        (0.02, 0.34, 0.9, 0x7c3a8a, 0.42),
        (0.82, 0.62, 0.85, 0x288096, 0.22),
    ]
    static let ink = Color.white
    // The system's notification material: the wallpaper seen through the card under a grey tint, so
    // a card never shows the ones behind it. Cards further back in the closed stack are darker, and
    // the ones past the third melt away.
    static let material = Color(hex: 0x80808a, opacity: 0.4)
    static let backShade = 0.38 // black over the third card back (less over the second)
    static let materialBlur = 22.0 // pt of blur on the wallpaper the cards show
    static let secondary = Color(.sRGB, red: 235 / 255, green: 235 / 255, blue: 245 / 255, opacity: 0.6)
    static let control = Color(hex: 0x80808a, opacity: 0.4) // the header's Show less and clear buttons

    // Island geometry (pt). At rest the pill is the hardware Dynamic Island's own frame: 126 × 37 pt,
    // centred half way down the top safe area. Without an island it sits just under the notch.
    static let islandW = 126.0
    static let islandH = 37.0
    static let side = 10.0 // gap between an open toast and the screen's edges
    static let toastH = 99.0 // the island's own band (beside the camera) plus a 62 pt row under it
    static let bigH = 228.0
    static let maxRadius = 44.0 // continuous corners, never more than half the height
    static let smoothing = 0.6 // corner smoothing of the continuous corners (0 = circular)

    // Island springs: a lively one going out, a calmer one coming home. Width leads height a little.
    static let openW = (duration: 0.52, ratio: 0.7)
    static let openH = (duration: 0.6, ratio: 0.74)
    static let close = (duration: 0.5, ratio: 0.86)
    static let contentDelay = 0.21 // s the shape gets to settle before its content fades in
    static let contentIn = 0.24
    static let contentOut = 0.11
    static let showTime = 1.9 // s a toast stays open before it drops into the stack

    // Pulling the island down opens it into the big timer card.
    static let bigDrag = 170.0
    static let bigCommit = 0.42
    static let bigFlick = 600.0
    // The island sits in the system's top-edge swipe (Notification Center), which can take a pull
    // that starts on it. A pull also starts anywhere in this band under the closed island, the closed
    // stack's front card included, where the system leaves it alone.
    static let pullReach = 108.0 // pt below the closed island
    static let pullStart = 10.0 // pt down before a pull from the band takes over

    // Stack. Cards are the system's notifications: an icon, a title with the time beside it, and a
    // line or two.
    static let cardH = 80.0
    static let cardRadius = 22.0
    static let cardGap = 8.0
    static let deckGap = 12.0
    static let peek = 9.0 // pt each card behind shows under the one in front
    static let peekInset = 11.0 // pt each card behind is narrower on either side
    static let icon = 38.0
    static let header = 46.0
    static let arrive = (duration: 0.68, ratio: 0.76)
    static let slot = (duration: 0.48, ratio: 0.84)
    static let expand = (duration: 0.52, ratio: 0.82)
    // Overlapping, concave fillets round off the corners where the island meets a card; apart, a
    // neck joins them, thins and snaps at `bridgeBreak` pt.
    static let fillet = 9.0
    static let bridgeBreak = 24.0
    static let bridgeFlare = 0.35
    // Clearing: cards rise back into the island one after another and it gulps each one.
    static let absorbTime = 0.46
    static let absorbStagger = 0.12
    static let gulp = (duration: 0.42, ratio: 0.45, velocity: 320.0)
    static let dismissDistance = 0.32
    static let dismissFlick = 900.0

    static let uploadTime = 9.0
    static let timerSeconds = 25.0 * 60

    // autoPlay (s): four toasts, the stack opened and trimmed, the island pulled open, everything
    // drawn back into the island, at rest again before the loop ends.
    static let loop = 16.0
    static let userHold = 4.0
    static let rest = 1.2

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))
}

private typealias M = IslandToastStackMetrics

enum IslandToastStackKind: CaseIterable {
    case call, message, upload, timer
}

// MARK: - Animated values

/// The easing curves the values below use.
enum IslandToastStackEasing {
    case linear, quadInOut, easeOut, easeInOut

    func value(_ t: Double) -> Double {
        switch self {
        case .linear: return t
        // Reanimated's default for withTiming.
        case .quadInOut: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        case .easeOut: return M.easeOut.value(at: t)
        case .easeInOut: return M.easeInOut.value(at: t)
        }
    }
}

final class IslandToastStackValue {
    private(set) var value: Double
    private var animation: Running?

    private enum Planned {
        case spring(target: Double, duration: Double, ratio: Double, velocity: Double, clamp: Bool)
        case timing(target: Double, duration: Double, easing: IslandToastStackEasing)
    }

    private indirect enum Running {
        case spring(Spring)
        case timing(target: Double, from: Double, elapsed: Double, duration: Double, easing: IslandToastStackEasing)
        case delay(remaining: Double, next: Planned, previous: Running?)
    }

    private var onDone: (() -> Void)?

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool { animation != nil }

    /// Puts the value somewhere at once, stopping whatever was moving it.
    func set(_ next: Double) {
        value = next
        animation = nil
        onDone = nil
    }

    func spring(to target: Double, duration: Double, ratio: Double, velocity: Double = 0, clamp: Bool = false, then done: (() -> Void)? = nil) {
        animation = start(.spring(target: target, duration: duration, ratio: ratio, velocity: velocity, clamp: clamp), previous: animation)
        onDone = done
    }

    func timing(to target: Double, duration: Double, easing: IslandToastStackEasing = .quadInOut, then done: (() -> Void)? = nil) {
        animation = start(.timing(target: target, duration: duration, easing: easing), previous: animation)
        onDone = done
    }

    /// Runs `plan` after `seconds`; until then whatever moved the value keeps moving it.
    func delayedTiming(_ seconds: Double, to target: Double, duration: Double, easing: IslandToastStackEasing = .quadInOut) {
        animation = .delay(remaining: seconds, next: .timing(target: target, duration: duration, easing: easing), previous: animation)
        onDone = nil
    }

    private func start(_ plan: Planned, previous: Running?) -> Running {
        switch plan {
        case let .spring(target, duration, ratio, velocity, clamp):
            if case let .spring(old)? = previous, old.target == target, old.duration == duration, old.ratio == ratio {
                return .spring(old)
            }
            var carried = velocity
            if let previous {
                // A spring still running hands its speed on; anything else has none to give.
                if case let .spring(old) = previous { carried = old.velocity + velocity } else { carried = 0 }
            }
            return .spring(Spring(from: value, to: target, velocity: carried, configured: velocity, duration: duration, ratio: ratio, clamp: clamp))
        case let .timing(target, duration, easing):
            if case let .timing(oldTarget, from, elapsed, _, _)? = previous, oldTarget == target {
                return .timing(target: target, from: from, elapsed: elapsed, duration: duration, easing: easing)
            }
            return .timing(target: target, from: value, elapsed: 0, duration: duration, easing: easing)
        }
    }

    /// Moves the value on by `dt` seconds.
    func step(_ dt: Double) {
        guard let running = animation else { return }
        let (next, finished) = advance(running, dt)
        animation = finished ? nil : next
        if finished {
            let done = onDone
            onDone = nil
            done?()
        }
    }

    private func advance(_ running: Running, _ dt: Double) -> (Running?, Bool) {
        switch running {
        case var .spring(spring):
            let done = spring.step(&value, dt: dt)
            return (.spring(spring), done)
        case let .timing(target, from, elapsed, duration, easing):
            let t = elapsed + dt
            if t >= duration || duration <= 0 {
                value = target
                return (nil, true)
            }
            value = from + (target - from) * easing.value(t / duration)
            return (.timing(target: target, from: from, elapsed: t, duration: duration, easing: easing), false)
        case let .delay(remaining, next, previous):
            let left = remaining - dt
            if left <= 0 {
                return (start(next, previous: previous), false)
            }
            var kept = previous
            if let previous {
                let (moved, done) = advance(previous, dt)
                kept = done ? nil : moved
            }
            return (.delay(remaining: left, next: next, previous: kept), false)
        }
    }

    /// Reanimated's `withSpring` with a duration and damping ratio: the stiffness is solved so the
    /// spring's energy falls to the threshold after 1.5 × the duration.
    struct Spring {
        let target: Double
        let duration: Double
        let ratio: Double
        private(set) var velocity: Double
        private let omega0: Double
        private let omega1: Double
        private let stiffness: Double
        private let initialEnergy: Double
        private let startOffset: Double
        private let clamp: Bool
        private static let threshold = 6e-9

        init(from value: Double, to target: Double, velocity: Double, configured: Double, duration: Double, ratio: Double, clamp: Bool) {
            self.target = target
            self.duration = duration
            self.ratio = ratio
            self.clamp = clamp
            var v = velocity
            // Velocity that would carry the first frame away from the target is dropped.
            if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
            self.velocity = v
            let x0 = value - target
            startOffset = x0
            let zeta = ratio
            let settling = duration * 1.5
            func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
            func difference(_ k: Double) -> Double {
                let w = sqrt(k) * zeta
                let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
                let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
                return energy(xt, vt, k) / energy(x0, v, k) - Self.threshold
            }
            // Bisection, as Reanimated solves it (its mass of 4 only scales the stiffness it finds).
            var low = Double.ulpOfOne
            var high = 2e3
            let rising = difference(high) >= difference(low)
            var k = (low + high) / 2
            var left = 100
            while abs(difference(k)) > Self.threshold * 1e-3 && left > 0 {
                left -= 1
                if (difference(k) < 0) == rising { low = k } else { high = k }
                k = (low + high) / 2
            }
            if !k.isFinite { k = 170 }
            stiffness = k
            omega0 = sqrt(k)
            omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
            // Reanimated measures the starting energy with the configured speed alone.
            initialEnergy = energy(x0, configured, k)
        }

        mutating func step(_ value: inout Double, dt: Double) -> Bool {
            if initialEnergy == 0 {
                value = target
                return true
            }
            let x0 = value - target
            let v0 = velocity
            let zeta = ratio
            if zeta < 1 {
                let sin1 = sin(omega1 * dt)
                let cos1 = cos(omega1 * dt)
                let envelope = exp(-zeta * omega0 * dt)
                let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                value = target + frag
                velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
            } else {
                let envelope = exp(-omega0 * dt)
                value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
                velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
            }
            if clamp {
                // overshootClamping: done as soon as it passes the target.
                let lower = startOffset >= 0 ? target : target + startOffset
                let upper = lower + abs(startOffset)
                if value < lower || value > upper {
                    value = target
                    velocity = 0
                    return true
                }
            }
            let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
            if now / initialEnergy <= Self.threshold {
                value = target
                velocity = 0
                return true
            }
            return false
        }
    }
}

// MARK: - Shapes

/// The geometry every frame is drawn from, shared by the shapes, the content and the gestures.
enum IslandToastStackShapes {
    static func clamp01(_ x: Double) -> Double { x.isNaN ? 0 : min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func smoothstep(_ t: Double) -> Double {
        let k = clamp01(t)
        return k * k * (3 - 2 * k)
    }

    static func radius(_ w: Double, _ h: Double) -> Double { max(0, min(M.maxRadius, h / 2, w / 2)) }

    /// A rounded rectangle with continuous ("squircle") corners, as iOS draws them: each corner eases
    /// from the straight edge into a shorter circular arc over (1 + smoothing) × radius. When the
    /// sides are too short for that, the smoothing gives way, down to a plain capsule.
    static func squircle(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double) -> Path {
        var path = Path()
        let width = max(0, w)
        let height = max(0, h)
        guard (x + y + width + height).isFinite else { return path }
        let budget = min(width, height) / 2
        let r = max(0, min(radius, budget))
        if !(r > 0.5) {
            path.addRect(CGRect(x: x, y: y, width: width, height: height))
            return path
        }
        let smooth = max(0, min(M.smoothing, budget / r - 1))
        let p = min((1 + smooth) * r, budget)
        let arc = 90 * (1 - smooth) * Double.pi / 180
        let arcLen = sin(arc / 2) * r * 2.0.squareRoot()
        let alpha = (Double.pi / 2 - arc) / 2
        let p34 = r * tan(alpha / 2)
        let beta = Double.pi / 4 * smooth
        let c = p34 * cos(beta)
        let d = c * tan(beta)
        let bb = (p - arcLen - c - d) / 3
        let a = 2 * bb
        let k = 4.0 / 3.0 * tan(arc / 4) * r
        // One corner in its own frame: u along the edge arriving at the corner, v along the next edge.
        let pts: [Double] = [
            a, 0, a + bb, 0, a + bb + c, d,
            a + bb + c + cos(beta) * k, d + sin(beta) * k,
            a + bb + c + arcLen - cos(beta + arc) * k, d + arcLen - sin(beta + arc) * k,
            a + bb + c + arcLen, d + arcLen,
            a + bb + c + arcLen + d, d + arcLen + c,
            a + bb + c + arcLen + d, d + arcLen + bb + c,
            p, p,
        ]
        // Corners clockwise from the top right: corner x, corner y, u, v.
        let corners: [(Double, Double, Double, Double, Double, Double)] = [
            (x + width, y, 1, 0, 0, 1),
            (x + width, y + height, 0, 1, -1, 0),
            (x, y + height, -1, 0, 0, -1),
            (x, y, 0, -1, 1, 0),
        ]
        path.move(to: CGPoint(x: x + p, y: y))
        for (cx, cy, ux, uy, vx, vy) in corners {
            let sx = cx - ux * p
            let sy = cy - uy * p
            func at(_ j: Int) -> CGPoint {
                CGPoint(x: sx + pts[j] * ux + pts[j + 1] * vx, y: sy + pts[j] * uy + pts[j + 1] * vy)
            }
            path.addLine(to: CGPoint(x: sx, y: sy))
            path.addCurve(to: at(4), control1: at(0), control2: at(2))
            path.addCurve(to: at(10), control1: at(6), control2: at(8))
            path.addCurve(to: at(16), control1: at(12), control2: at(14))
        }
        path.closeSubpath()
        return path
    }

    /// How far the island reaches either side of its centre at height `y`.
    static func islandHalf(at y: Double, top: Double, half: Double, h: Double, r: Double) -> Double {
        let k = min(max(y, top), top + h)
        let corner = k < top + r ? top + r : (k > top + h - r ? top + h - r : k)
        let dy = k - corner
        return half - r + max(0, r * r - dy * dy).squareRoot()
    }

    /// The shape that joins the island to a card under it: concave fillets where they overlap, a
    /// neck that leaves the island's bottom edge and lands on the card's top edge (both
    /// tangentially) once they part, pinching in at the waist and snapping at `bridgeBreak` pt.
    static func bridge(cx: Double, top: Double, ih: Double, hh: Double, ir: Double, ct: Double, ch: Double, chh: Double, cr: Double, cl: Double) -> Path {
        var path = Path()
        let bottom = top + hh
        let gap = ct - bottom
        let cardFlat = ch - cr
        guard (gap + cx + ih + ch).isFinite, cl <= cx, chh > 0 else { return path }
        if gap < 0 {
            let depth = -gap
            let side = islandHalf(at: ct, top: top, half: ih, h: hh, r: ir)
            let f = M.fillet * min(1, depth / M.fillet, max(0, (cardFlat - side) / M.fillet))
            guard f >= 0.5, ct > top else { return path }
            let upper = islandHalf(at: ct - f, top: top, half: ih, h: hh, r: ir)
            for s in [-1.0, 1.0] {
                path.move(to: CGPoint(x: cx + s * upper, y: ct - f))
                path.addCurve(
                    to: CGPoint(x: cx + s * (side + f), y: ct),
                    control1: CGPoint(x: cx + s * upper, y: ct - f * 0.45),
                    control2: CGPoint(x: cx + s * (side + f * 0.45), y: ct)
                )
                path.addLine(to: CGPoint(x: cx + s * (side + f), y: ct + 2))
                path.addLine(to: CGPoint(x: cx + s * (side - 2), y: ct + 2))
                path.addLine(to: CGPoint(x: cx + s * (upper - 2), y: ct - f))
                path.closeSubpath()
            }
            return path
        }
        guard gap < M.bridgeBreak else { return path }
        let p = gap / M.bridgeBreak
        let anchor = max(0, ih - ir)
        let land = min(cardFlat, anchor + M.fillet + gap * M.bridgeFlare)
        let waist = anchor * pow(1 - p, 1.3) * 0.82
        guard anchor >= 1, land >= 1 else { return path }
        let pull = max(0, ((anchor + land) / 2 - waist) / 0.75)
        path.move(to: CGPoint(x: cx - anchor, y: bottom - 2))
        path.addLine(to: CGPoint(x: cx - anchor, y: bottom))
        path.addCurve(to: CGPoint(x: cx - land, y: ct), control1: CGPoint(x: cx - anchor + pull, y: bottom), control2: CGPoint(x: cx - land + pull, y: ct))
        path.addLine(to: CGPoint(x: cx - land, y: ct + 2))
        path.addLine(to: CGPoint(x: cx + land, y: ct + 2))
        path.addLine(to: CGPoint(x: cx + land, y: ct))
        path.addCurve(to: CGPoint(x: cx + anchor, y: bottom), control1: CGPoint(x: cx + land - pull, y: ct), control2: CGPoint(x: cx + anchor - pull, y: bottom))
        path.addLine(to: CGPoint(x: cx + anchor, y: bottom - 2))
        path.closeSubpath()
        return path
    }

    /// How far toward the background a card's black is mixed, `slot` places behind the front.
    static func slotFade(_ slot: Double) -> Double {
        if slot <= 1 { return slot * 0.2 }
        if slot <= 2 { return mix(0.2, 0.42, slot - 1) }
        return mix(0.42, 1, clamp01(slot - 2))
    }
}

/// A card rectangle with its corner radius and how far its colour is mixed toward the background.
struct IslandToastStackRect {
    var x: Double
    var y: Double
    var w: Double
    var h: Double
    var r: Double
    var fade: Double

    func mixed(to o: IslandToastStackRect, _ t: Double, fadeT: Double? = nil) -> IslandToastStackRect {
        let m = IslandToastStackShapes.mix
        return .init(x: m(x, o.x, t), y: m(y, o.y, t), w: m(w, o.w, t), h: m(h, o.h, t), r: m(r, o.r, t), fade: m(fade, o.fade, fadeT ?? t))
    }
}

// MARK: - Engine

/// One toast's motion: whether its card is drawn, its place in the stack, how far it has come
/// down from the island, a sideways throw, how far it has gone back up, and the island's content.
final class IslandToastStackMotion {
    var vis = 0.0
    let slot = IslandToastStackValue(0)
    let arrive = IslandToastStackValue(1)
    let tx = IslandToastStackValue(0)
    let absorb = IslandToastStackValue(0)
    let content = IslandToastStackValue(0)

    var values: [IslandToastStackValue] { [slot, arrive, tx, absorb, content] }
}

/// The controller: every action the script and the gestures share, stepped once a frame. Nothing in
/// it is observed: the timeline redraws every frame anyway.
final class IslandToastStackEngine {
    // Layout, refreshed from the view.
    private(set) var width = 0.0
    private(set) var toastW = M.islandW
    private(set) var cardW = M.islandW
    private(set) var islandTop = 11.0
    var deckTop: Double { islandTop + M.islandH + M.deckGap }
    private var reduced = false

    // What renders.
    private(set) var now = 0.0
    private(set) var stack: [IslandToastStackKind] = []
    private(set) var presenting: IslandToastStackKind?
    private(set) var bigMounted = false
    private(set) var expandedOpen = false
    private(set) var timerStart: Double?
    private(set) var uploadStart: Double?

    let islandW = IslandToastStackValue(M.islandW)
    let islandH = IslandToastStackValue(M.islandH)
    let bigOpacity = IslandToastStackValue(0)
    let expanded = IslandToastStackValue(0)
    let upload = IslandToastStackValue(0)
    let motion: [IslandToastStackKind: IslandToastStackMotion] = Dictionary(uniqueKeysWithValues: IslandToastStackKind.allCases.map { ($0, IslandToastStackMotion()) })

    // Bookkeeping.
    private var bigOpen = false
    private var clearing = false
    private var dragging = false
    private var dragFrom = 0.0
    private var playing = false
    private var lastDate: Date?
    private var timers: [(at: Double, run: () -> Void)] = []
    private var script: [(at: Double, run: () -> Void)] = []

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)

    private typealias S = IslandToastStackShapes

    func layout(width: Double, top: Double, reduced: Bool) {
        self.width = width
        toastW = max(M.islandW, min(420, width - M.side * 2))
        cardW = max(M.islandW, min(410, width - 24))
        // With a Dynamic Island the safe area is 59–68 pt and the island is centred in it.
        let hasIsland = top >= 51
        islandTop = max(6, hasIsland ? top / 2 + 1 - M.islandH / 2 : top + 6)
        self.reduced = reduced
    }

    /// Advances everything to `date`.
    func step(to date: Date, autoPlay: Bool) {
        if autoPlay != playing && width > 0 {
            if autoPlay { start() } else { stop() }
        }
        let elapsed = lastDate.map { date.timeIntervalSince($0) } ?? (1.0 / 60)
        lastDate = date
        let dt = min(max(elapsed, 0), 0.1)
        now += dt
        for value in [islandW, islandH, bigOpacity, expanded, upload] { value.step(dt) }
        for m in motion.values { for value in m.values { value.step(dt) } }
        // Timers fire after the values moved, so what they start is first drawn at rest.
        while let index = timers.indices.min(by: { timers[$0].at < timers[$1].at }), timers[index].at <= now {
            timers.remove(at: index).run()
        }
        while let index = script.indices.min(by: { script[$0].at < script[$1].at }), script[index].at <= now {
            script.remove(at: index).run()
        }
    }

    private func later(_ seconds: Double, _ run: @escaping () -> Void) {
        timers.append((now + seconds, run))
    }

    // MARK: Geometry

    func islandRect() -> IslandToastStackRect {
        let w = islandW.value
        let h = islandH.value
        return .init(x: (width - w) / 2, y: islandTop, w: w, h: h, r: S.radius(w, h), fade: 0)
    }

    private func slotRect(_ slot: Double, _ e: Double, _ off: Double) -> IslandToastStackRect {
        let s = max(0, slot)
        // Closed, the cards behind are narrower and only their bottom edge shows under the front one.
        let closedW = cardW - 2 * M.peekInset * min(s, 3)
        let closedY = deckTop + off + M.peek * min(s, 2.4)
        let openY = deckTop + off + M.header + s * (M.cardH + M.cardGap)
        let w = S.mix(closedW, cardW, e)
        return .init(x: (width - w) / 2, y: S.mix(closedY, openY, e), w: w, h: M.cardH, r: M.cardRadius, fade: S.mix(S.slotFade(s), 0, e))
    }

    /// Where a card is drawn: from the open toast to its slot, and from its slot into the island.
    func cardRect(_ kind: IslandToastStackKind) -> IslandToastStackRect {
        guard let m = motion[kind] else { return .init(x: 0, y: 0, w: 0, h: 0, r: 0, fade: 0) }
        let off = max(0, islandH.value - M.islandH)
        let rest = slotRect(m.slot.value, expanded.value, off)
        let toast = IslandToastStackRect(x: (width - toastW) / 2, y: islandTop, w: toastW, h: M.toastH, r: S.radius(toastW, M.toastH), fade: 0)
        var out = toast.mixed(to: rest, m.arrive.value)
        let q = m.absorb.value
        if q > 0 {
            let island = IslandToastStackRect(x: (width - M.islandW) / 2, y: islandTop, w: M.islandW, h: M.islandH, r: M.islandH / 2, fade: 0)
            // It darkens to the island's black well before it gets there.
            out = out.mixed(to: island, q, fadeT: min(1, q * 2.5))
        }
        return out
    }

    /// The fillets or neck joining the island to a card on its way out of it or back in.
    func bridge(_ kind: IslandToastStackKind) -> Path? {
        guard let m = motion[kind], !reduced, m.vis > 0, m.arrive.value < 0.999 || m.absorb.value > 0.001 else { return nil }
        let c = cardRect(kind)
        let i = islandRect()
        return S.bridge(cx: width / 2, top: islandTop, ih: i.w / 2, hh: i.h, ir: i.r, ct: c.y, ch: c.w / 2, chh: c.h, cr: c.r, cl: c.x)
    }

    // MARK: Actions

    private func morph(_ w: Double, _ h: Double, open: Bool, velocity: Double = 0) {
        if reduced {
            islandW.timing(to: w, duration: 0.22, easing: .easeOut)
            islandH.timing(to: h, duration: 0.22, easing: .easeOut)
            return
        }
        let sw = open ? M.openW : M.close
        let sh = open ? M.openH : M.close
        islandW.spring(to: w, duration: sw.duration, ratio: sw.ratio)
        islandH.spring(to: h, duration: sh.duration, ratio: sh.ratio, velocity: velocity)
    }

    private func commitStack(_ next: [IslandToastStackKind]) {
        stack = next
        for (index, kind) in next.enumerated() {
            guard let slot = motion[kind]?.slot else { continue }
            if reduced { slot.timing(to: Double(index), duration: 0.2) } else {
                slot.spring(to: Double(index), duration: M.slot.duration, ratio: M.slot.ratio)
            }
        }
    }

    private func startTimer() {
        if timerStart == nil { timerStart = now }
    }

    func expand(_ open: Bool) {
        if open && stack.isEmpty { return }
        expandedOpen = open
        if reduced { expanded.timing(to: open ? 1 : 0, duration: 0.22) } else {
            expanded.spring(to: open ? 1 : 0, duration: M.expand.duration, ratio: M.expand.ratio)
        }
    }

    /// The toast drops out of the island as a card at the front of the stack.
    private func drop(_ kind: IslandToastStackKind) {
        // A tap can drop a toast just before its own timer does: the second drop is a no-op.
        guard let m = motion[kind], presenting == kind, !stack.contains(kind) else { return }
        m.content.timing(to: 0, duration: M.contentOut)
        later(M.contentOut - 0.02) { [self] in
            guard presenting == kind, !stack.contains(kind) else { return }
            m.tx.set(0)
            m.absorb.set(0)
            m.slot.set(0)
            m.arrive.set(0)
            m.vis = 1
            if reduced { m.arrive.timing(to: 1, duration: 0.26, easing: .easeOut) } else {
                m.arrive.spring(to: 1, duration: M.arrive.duration, ratio: M.arrive.ratio)
            }
            morph(M.islandW, M.islandH, open: false)
            commitStack([kind] + stack.filter { $0 != kind })
            later(0.32) { [self] in presenting = nil }
        }
    }

    func present(_ kind: IslandToastStackKind, user: Bool = false) {
        if presenting != nil || bigOpen || clearing || stack.contains(kind) || width == 0 { return }
        presenting = kind
        if kind == .timer { startTimer() }
        if kind == .upload {
            uploadStart = now
            upload.set(0)
            upload.timing(to: 1, duration: M.uploadTime, easing: .linear)
        }
        if user { light.impactOccurred() }
        morph(toastW, M.toastH, open: true)
        motion[kind]?.content.delayedTiming(reduced ? 0.06 : M.contentDelay, to: 1, duration: M.contentIn, easing: .easeOut)
        later(M.showTime) { [self] in drop(kind) }
    }

    private func remove(_ kind: IslandToastStackKind) {
        motion[kind]?.vis = 0
        motion[kind]?.tx.set(0)
        let next = stack.filter { $0 != kind }
        commitStack(next)
        if next.isEmpty && expandedOpen { expand(false) }
        if kind == .upload { uploadStart = nil }
    }

    func dismiss(_ kind: IslandToastStackKind, direction: Double, user: Bool = false, velocity: Double = 0) {
        if !stack.contains(kind) || clearing { return }
        if user { light.impactOccurred() }
        if reduced {
            remove(kind)
            return
        }
        motion[kind]?.tx.spring(to: direction * width * 1.2, duration: 0.38, ratio: 1, velocity: velocity, clamp: true) { [weak self] in
            self?.remove(kind)
        }
    }

    func openBig(user: Bool = false, velocity: Double = 0) {
        if presenting != nil || clearing || width == 0 { return }
        bigOpen = true
        bigMounted = true
        startTimer()
        if user { medium.impactOccurred() }
        morph(toastW, M.bigH, open: true, velocity: velocity)
        bigOpacity.delayedTiming(reduced ? 0.06 : M.contentDelay - 0.04, to: 1, duration: M.contentIn, easing: .easeOut)
    }

    func closeBig(velocity: Double = 0) {
        if !bigOpen { return }
        bigOpen = false
        bigOpacity.timing(to: 0, duration: M.contentOut)
        later(M.contentOut - 0.03) { [self] in morph(M.islandW, M.islandH, open: false, velocity: velocity) }
        later(0.42) { [self] in
            if !bigOpen { bigMounted = false }
        }
    }

    private func gulp() {
        if reduced { return }
        islandW.spring(to: M.islandW, duration: M.gulp.duration, ratio: M.gulp.ratio, velocity: M.gulp.velocity)
        islandH.spring(to: M.islandH, duration: M.gulp.duration, ratio: M.gulp.ratio, velocity: M.gulp.velocity * 0.25)
    }

    /// Every card rises back into the island, front first, and the island gulps each one.
    func clearAll() {
        if clearing { return }
        let cards = stack
        if expandedOpen { expand(false) }
        if cards.isEmpty { return }
        clearing = true
        for (i, kind) in cards.enumerated() {
            later(Double(i) * M.absorbStagger) { [self] in
                guard let m = motion[kind] else { return }
                if reduced {
                    m.absorb.timing(to: 1, duration: 0.22)
                } else {
                    m.absorb.timing(to: 1, duration: M.absorbTime, easing: .easeInOut) { [weak self] in self?.gulp() }
                }
            }
        }
        later(Double(cards.count - 1) * M.absorbStagger + M.absorbTime + 0.06) { [self] in
            for kind in cards {
                motion[kind]?.vis = 0
                motion[kind]?.absorb.set(0)
                motion[kind]?.content.set(0)
            }
            commitStack([])
            clearing = false
            timerStart = nil
            uploadStart = nil
        }
    }

    // MARK: Script

    private func stopScript() {
        script.removeAll()
    }

    private func runScript() {
        stopScript()
        let steps: [(Double, () -> Void)] = [
            (0.6, { [self] in present(.call) }),
            (3.0, { [self] in present(.message) }),
            (5.4, { [self] in present(.upload) }),
            (7.8, { [self] in present(.timer) }),
            (10.3, { [self] in expand(true) }),
            (11.4, { [self] in dismiss(.message, direction: 1) }),
            (12.3, { [self] in expand(false) }),
            (12.9, { [self] in openBig() }),
            (14.2, { [self] in closeBig() }),
            (14.7, { [self] in clearAll() }),
            (M.loop, { [self] in runScript() }),
        ]
        script = steps.map { (now + $0.0, $0.1) }
    }

    private func start() {
        playing = true
        runScript()
    }

    private func stop() {
        playing = false
        stopScript()
    }

    /// A touch pauses the script; a while later everything settles back to rest and it starts over.
    private func touched() {
        if !playing { return }
        stopScript()
        script.append((now + M.userHold, { [self] in
            closeBig()
            expand(false)
            later(0.45) { [self] in clearAll() }
            script.append((now + 0.45 + M.rest, { [self] in runScript() }))
        }))
    }

    // MARK: Touch

    /// What a touch started on. One recogniser over the whole block routes every touch: views
    /// rebuilt by the timeline every frame can't keep a gesture of their own alive.
    private enum Target: Equatable {
        case island, band, card(IslandToastStackKind), header, clear, background, nothing
    }

    private var target: Target?
    private var targetStart = CGPoint.zero

    private func hit(_ p: CGPoint) -> Target {
        let i = islandRect()
        // The island's touch area reaches up to the screen's edge and a little past its sides.
        if p.y <= i.y + i.h + 14, abs(p.x - width / 2) <= i.w / 2 + 24 { return .island }
        if expandedOpen {
            let off = max(0, islandH.value - M.islandH)
            let y = deckTop + off
            if p.y >= y - 6, p.y <= y + 40 {
                // The clear button at the header's right end (30 pt, 8 pt from Show less), and a
                // little round it.
                let right = (width + cardW) / 2 - 6
                if p.x >= right - 34, p.x <= right + 8 { return .clear }
                if abs(p.x - width / 2) <= cardW / 2 { return .header }
            }
        }
        for kind in stack where expandedOpen || kind == stack.first {
            guard let m = motion[kind] else { continue }
            let r = cardRect(kind)
            let x = p.x - m.tx.value
            if x >= r.x, x <= r.x + r.w, p.y >= r.y, p.y <= r.y + r.h { return .card(kind) }
        }
        if expandedOpen { return .background }
        // The band under the closed island, where a pull down starts too.
        let bandTop = islandTop + M.islandH
        if p.y >= bandTop, p.y <= bandTop + M.pullReach, abs(p.x - width / 2) <= toastW / 2 { return .band }
        return .nothing
    }

    func touchChanged(_ value: DragGesture.Value) {
        if target == nil || value.startLocation != targetStart {
            // A touch the system took away never ended: settle it before starting the new one.
            if target != nil { touchCancelled() }
            targetStart = value.startLocation
            target = hit(value.startLocation)
            bandFailed = false
            bandTouchStart = nil
        }
        switch target {
        case .island: islandChanged(value)
        case .band: bandChanged(value)
        case let .card(kind): cardChanged(kind, value)
        default: break
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        if target == nil || value.startLocation != targetStart { touchChanged(value) }
        let ended = target
        target = nil
        switch ended {
        case .island: islandEnded(value)
        case .band: bandEnded(value)
        case let .card(kind): cardEnded(kind, value)
        case .header, .background:
            if hypot(value.translation.width, value.translation.height) < 10 { toggleStack() }
        case .clear:
            if hypot(value.translation.width, value.translation.height) < 10 { clearStack() }
        default: break
        }
    }

    private func touchCancelled() {
        switch target {
        case .island, .band: islandCancelled()
        case .card: cardCancelled()
        default: break
        }
        target = nil
    }

    private var islandTouchStart: Date?
    private var islandPanning = false
    private var cardTouch: (kind: IslandToastStackKind, start: Date, panning: Bool, failed: Bool)?

    func islandChanged(_ value: DragGesture.Value) {
        if islandTouchStart == nil {
            islandTouchStart = value.time
            islandPanning = false
        }
        let ty = value.translation.height
        if !islandPanning {
            guard abs(ty) >= 8 else { return }
            islandPanning = true
            if presenting == nil && !clearing {
                dragging = true
                dragFrom = bigOpen ? 1 : 0
                touched()
                bigMounted = true
            }
        }
        guard dragging else { return }
        pullMoved(ty)
    }

    /// Pulling down opens the island with the finger; past fully open it stretches on a band.
    private func pullMoved(_ ty: Double) {
        let raw = dragFrom + ty / M.bigDrag
        let p = raw <= 1 ? max(0, raw) : 1 + 0.22 * tanh((raw - 1) * 2)
        let q = min(1, p)
        islandW.set(S.mix(M.islandW, toastW, min(1, q * 1.6)))
        islandH.set(S.mix(M.islandH, M.bigH, p))
        bigOpacity.set(S.clamp01((p - 0.5) / 0.4))
    }

    func islandEnded(_ value: DragGesture.Value) {
        let started = islandTouchStart ?? value.time
        islandTouchStart = nil
        if islandPanning {
            islandPanning = false
            pullEnded(value)
            return
        }
        guard value.time.timeIntervalSince(started) <= 0.35 else { return }
        tapIsland()
    }

    /// A pull let go: the big card opens past the commit point or on a downward flick, else it closes.
    private func pullEnded(_ value: DragGesture.Value) {
        guard dragging else { return }
        dragging = false
        let p = dragFrom + value.translation.height / M.bigDrag
        let vy = value.velocity.height.isFinite ? value.velocity.height : 0
        let open = vy > M.bigFlick || (vy > -M.bigFlick && p > (dragFrom > 0.5 ? 1 - M.bigCommit : M.bigCommit))
        if presenting != nil || clearing { return }
        if open {
            if !bigOpen { openBig(user: true, velocity: vy) } else { morph(toastW, M.bigH, open: true, velocity: vy) }
            bigOpacity.timing(to: 1, duration: 0.16)
        } else {
            bigOpen = true
            closeBig(velocity: vy)
        }
    }

    /// A drag in the band under the island (or down on the closed stack's front card): only a pull
    /// down counts, and a sideways one there does nothing.
    private var bandFailed = false
    private var bandTouchStart: Date?

    func bandChanged(_ value: DragGesture.Value) {
        if bandTouchStart == nil { bandTouchStart = value.time }
        let dx = value.translation.width
        let dy = value.translation.height
        if !islandPanning {
            guard !bandFailed else { return }
            if abs(dx) >= 16 {
                bandFailed = true
                return
            }
            guard dy >= M.pullStart else { return }
            islandPanning = true
            if presenting == nil && !clearing {
                dragging = true
                dragFrom = bigOpen ? 1 : 0
                touched()
                bigMounted = true
            }
        }
        guard dragging else { return }
        pullMoved(dy)
    }

    func bandEnded(_ value: DragGesture.Value) {
        let started = bandTouchStart ?? value.time
        let failed = bandFailed
        bandTouchStart = nil
        bandFailed = false
        islandTouchStart = nil
        if islandPanning {
            islandPanning = false
            pullEnded(value)
            return
        }
        // The island itself sits where the system takes touches, so a tap just under it counts as
        // a tap on it.
        let moved = hypot(value.translation.width, value.translation.height)
        if !failed && moved < 10 && value.time.timeIntervalSince(started) <= 0.35 { tapIsland() }
    }

    /// A tap on the island: closes the big card, sends an open toast to the stack, or brings the next.
    func tapIsland() {
        touched()
        if bigOpen {
            closeBig()
        } else if let kind = presenting {
            // A tap on an open toast sends it to the stack straight away.
            if (motion[kind]?.content.value ?? 0) > 0.5 { drop(kind) }
        } else if let next = IslandToastStackKind.allCases.first(where: { !stack.contains($0) }) {
            present(next, user: true)
        }
    }

    /// A touch the system took away from the island: a pull in progress settles where it is going.
    func islandCancelled() {
        islandTouchStart = nil
        islandPanning = false
        if dragging {
            dragging = false
            if islandH.value > (M.islandH + M.bigH) / 2 {
                openBig(user: false)
            } else {
                bigOpen = true
                closeBig()
            }
        }
    }

    /// A card touch the system took away: a card mid-swipe springs back.
    func cardCancelled() {
        guard let touch = cardTouch else { return }
        cardTouch = nil
        if touch.panning { motion[touch.kind]?.tx.spring(to: 0, duration: 0.42, ratio: 0.72) }
    }

    func toggleStack() {
        touched()
        expand(!expandedOpen)
    }

    /// The header's clear button: every card goes back into the island, as the script clears it.
    func clearStack() {
        touched()
        clearAll()
    }

    func cardChanged(_ kind: IslandToastStackKind, _ value: DragGesture.Value) {
        if cardTouch == nil || cardTouch?.kind != kind {
            cardTouch = (kind, value.time, false, false)
        }
        guard var touch = cardTouch else { return }
        let dx = value.translation.width
        let dy = value.translation.height
        if !touch.panning && !touch.failed {
            if dy >= 12 && !expandedOpen && kind == stack.first {
                // Down on the closed stack's front card: the island's pull takes the touch over.
                cardTouch = nil
                target = .band
                bandFailed = false
                bandChanged(value)
                return
            } else if abs(dy) >= 12 {
                touch.failed = true
            } else if abs(dx) >= 14 {
                touch.panning = true
                touched()
            }
            cardTouch = touch
        }
        if touch.panning { motion[kind]?.tx.set(dx) }
    }

    func cardEnded(_ kind: IslandToastStackKind, _ value: DragGesture.Value) {
        guard let touch = cardTouch, touch.kind == kind else { return }
        cardTouch = nil
        if touch.panning {
            let x = value.translation.width
            let vx = value.velocity.width.isFinite ? value.velocity.width : 0
            let far = abs(x) > width * M.dismissDistance
            let fast = abs(vx) > M.dismissFlick
            let lead = fast ? vx : x
            let direction: Double = lead == 0 ? 0 : (lead < 0 ? -1 : 1)
            let along = x != 0 ? x : vx
            let alongSign: Double = along == 0 ? 0 : (along < 0 ? -1 : 1)
            if (far || fast) && direction != 0 && direction == alongSign {
                dismiss(kind, direction: direction, user: true, velocity: vx)
            } else {
                motion[kind]?.tx.spring(to: 0, duration: 0.42, ratio: 0.72, velocity: vx)
            }
            return
        }
        if !touch.failed && value.time.timeIntervalSince(touch.start) <= 0.3 { toggleStack() }
    }
}

// MARK: - Content

enum IslandToastStackPeople {
    static func avatar(_ id: String) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=144&h=144&q=80"
    }

    static let caller = (name: "Theo Marsh", initials: "TM", photo: avatar("photo-1587397845856-e6cf49176c70"))
    static let sender = (
        name: "Rafael Ortiz",
        initials: "RO",
        body: "Running 5 min late, grab us the corner table?",
        photo: avatar("photo-1600603406200-5b2a104684ac")
    )
    static let upload = (album: "Lisbon, spring", count: 24)
    static let timer = (title: "Sourdough proof", place: "Kitchen")

    static func label(_ kind: IslandToastStackKind) -> String {
        switch kind {
        case .call: return "Missed call from \(caller.name)"
        case .message: return "\(sender.name): \(sender.body)"
        case .upload: return "Uploading \(upload.count) photos to \(upload.album)"
        case .timer: return "\(timer.title) timer"
        }
    }

    static func format(_ seconds: Double) -> String {
        let s = max(0, Int(ceil(seconds.isFinite ? seconds : 0)))
        return "\(s / 60):\(String(format: "%02d", s % 60))"
    }
}

/// Downloads a photo once and keeps it decoded.
enum IslandToastStackPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    /// The photo, once it has loaded.
    static func cached(_ address: String) -> UIImage? {
        cache.object(forKey: address as NSString)
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
