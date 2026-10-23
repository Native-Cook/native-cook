import SwiftUI
import UIKit

/// A ticket's prize: the photo under the foil and the line printed on the stub.
struct ScratchTicketPrize {
    let photo: String
    let prize: String
    let detail: String

    static let samples: [ScratchTicketPrize] = [
        .init(photo: "photo-1536633125620-8a3245c11ffa", prize: "A picnic in the sunflowers", detail: "Hollin Farm · for two"),
        .init(photo: "photo-1552160793-cbaf3ebcba72", prize: "Golden hour photo walk", detail: "Wren Valley · any Saturday"),
        .init(photo: "photo-1627891374362-c6ca6c675cb3", prize: "A year of Friday bouquets", detail: "Marrow Hill Flowers"),
        .init(photo: "photo-1530655937400-7f00928c51e1", prize: "Two nights at Alder Barn", detail: "Breakfast included"),
    ]

    /// Photos are drawn about 330 pt wide: 1000 px covers 3× screens.
    var url: URL? { URL(string: "https://images.unsplash.com/\(photo)?auto=format&fit=crop&w=1000&q=80") }
}

enum ScratchTicketMetrics {
    // Layout (pt): the ticket is at most `ticketMax` wide with `side` margins; the scratch window
    // is `scratchAspect` as tall as it is wide; the button sits `buttonGap` under the ticket.
    static let ticketMax = 370.0
    static let side = 20.0
    static let scratchTop = 114.0
    static let scratchAspect = 0.7
    static let stub = 100.0
    static let buttonGap = 26.0
    static let buttonHeight = 54.0
    static let radius = 20.0
    static let scratchRadius = 14.0
    // The card's canvas reaches this far past the ticket on every side, so its shadow is not cut off.
    static let bleed = 40.0
    // The photo sits this much zoomed in under the foil and settles to 1 as the foil dissolves.
    static let photoZoom = 0.1
    static let barWidth = 92.0
    static let barHeight = 28.0

    // Scratching: the foil is split into `gridX` × `gridY` cells; a cell counts as cleared when the
    // brush's middle passes over it, and at `revealAt` of them the rest dissolves over `dissolveTime`.
    static let brush = 46.0
    static let gridX = 16
    static let gridY = 12
    static let revealAt = 0.6
    static let dissolveTime = 1.1
    static let dissolveEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
    // A flake tears off for every `flakeEvery` pt the finger scratches through fresh foil.
    static let flakeEvery = 7.0
    static let flakeMax = 240
    // The finger's path is kept as points; past this many the rest of the foil dissolves, so a long
    // scrub over one corner never grows the path (and its per-frame rebuild) without bound.
    static let pointMax = 4000
    static let flakeGravity = 1300.0
    static let flakeDrag = 2.6
    static let confetti = 110
    static let confettiGravity = 1150.0
    static let confettiDrag = 1.8
    static let confettiColours: [UInt32] = [0xeea41c, 0xf7e9c6, 0xe2673d, 0x5f9c46, 0x8ec5e6]
    // The foil's light follows the finger, easing at this rate (1/s); left alone it drifts slowly.
    static let tiltRate = 6.0

    // Tickets change places: the new one springs in from the right, the old one is thrown left.
    // Dragging the ticket left past `commitDistance` of the screen, or flicking faster than
    // `commitSpeed` (pt/s), throws it too; to the right it only gives a `rubber` band.
    static let enterSpring = (duration: 0.8, dampingRatio: 0.8)
    static let leaveSpring = (duration: 0.65, dampingRatio: 1.0)
    static let backSpring = (duration: 0.45, dampingRatio: 0.8)
    static let commitDistance = 0.3
    static let commitSpeed = 800.0
    static let rubber = 70.0
    static let fadeTime = 0.32
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    // autoPlay loop (seconds): a virtual finger scratches a zigzag over the foil, the prize shows,
    // then "New ticket" brings the next one in and it rests, unscratched, as the loop began.
    static let loop = 9.0
    static let fingerDownAt = 0.7
    static let fingerUpAt = 3.9
    static let zigzagLegs = 7
    static let zigzagInset = 22.0
    static let newAt = 6.5
    // A touch pauses the loop this long; then it resumes from a fresh ticket.
    static let userHold = 4.0

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }

    /// Resistance past an edge: follows the finger at first, never further than `rubber`.
    static func rubberBand(_ x: Double) -> Double { rubber * (1 - 1 / (1 + x / rubber)) }

    /// True when the clock went past `at` between `before` and `now`, wrapping round the loop.
    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : at > before || at <= now
    }

    /// The virtual finger's point at progress `u` (0…1): side to side, `zigzagLegs` times, going down.
    static func zigzag(_ u: Double, _ w: Double, _ h: Double) -> CGPoint {
        let legs = u * Double(zigzagLegs)
        let leg = min(zigzagLegs - 1, Int(floor(legs)))
        let f = legs - Double(leg)
        let across = leg % 2 == 0 ? f : 1 - f
        return CGPoint(x: zigzagInset + across * (w - zigzagInset * 2), y: zigzagInset + u * (h - zigzagInset * 2))
    }
}

/// Where things sit, in points. `ticket` is on screen; `scratch` and the rest are inside the ticket.
struct ScratchTicketGeometry: Equatable {
    var width: Double
    var height: Double
    var ticket: CGRect
    var scratch: CGRect
    var perfY: Double
    var buttonY: Double

    init(width: Double, height: Double, top insetTop: Double, bottom insetBottom: Double) {
        typealias M = ScratchTicketMetrics
        self.width = width
        self.height = height
        let w = min(width - M.side * 2, M.ticketMax)
        let sw = w - 44
        let sh = (sw * M.scratchAspect).rounded()
        perfY = M.scratchTop + sh + 24
        let h = perfY + M.stub
        let block = h + M.buttonGap + M.buttonHeight
        let room = height - insetTop - insetBottom
        let top = insetTop + max(12, (room - block) / 2)
        ticket = CGRect(x: (width - w) / 2, y: top, width: w, height: h)
        scratch = CGRect(x: 22, y: M.scratchTop, width: sw, height: sh)
        buttonY = top + h + M.buttonGap
    }
}

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct ScratchTicketSpring {
    let target: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.dampingRatio = dampingRatio
        self.velocity = velocity
        let x0 = value - target
        let v = velocity
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
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, velocity, k)
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
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by a sequence of
/// timings and duration springs stepped by the block's frame clock.
struct ScratchTicketValue {
    enum Step {
        case timing(to: Double, duration: Double, curve: (Double) -> Double)
        case spring(to: Double, duration: Double, dampingRatio: Double, velocity: Double)
    }

    private enum Motion {
        case timing(from: Double, to: Double, t: Double, duration: Double, curve: (Double) -> Double)
        case spring(ScratchTicketSpring)
    }

    private(set) var value: Double
    private var steps: [Step] = []
    private var motion: Motion?

    init(_ value: Double) { self.value = value }

    var moving: Bool { motion != nil }

    /// Puts the value somewhere at once, stopping whatever was moving it.
    mutating func set(_ next: Double) {
        value = next
        steps = []
        motion = nil
    }

    mutating func run(_ next: [Step]) {
        // A spring that takes over from a spring keeps its speed, as Reanimated's does.
        var carried = 0.0
        if case let .spring(spring)? = motion { carried = spring.velocity }
        steps = next
        motion = nil
        start(carried: carried)
    }

    private mutating func start(carried: Double) {
        guard !steps.isEmpty else { return }
        switch steps.removeFirst() {
        case let .timing(to, duration, curve):
            motion = .timing(from: value, to: to, t: 0, duration: duration, curve: curve)
        case let .spring(to, duration, dampingRatio, velocity):
            motion = .spring(ScratchTicketSpring(from: value, to: to, velocity: carried + velocity, duration: duration, dampingRatio: dampingRatio))
        }
    }

    /// Advances by `dt` s; true on the frame the whole sequence finishes.
    mutating func step(_ dt: Double) -> Bool {
        guard let current = motion else { return false }
        var done = false
        switch current {
        case let .timing(from, to, t, duration, curve):
            let now = t + dt
            let p = duration > 0 ? min(1, now / duration) : 1
            value = from + (to - from) * curve(p)
            done = p >= 1
            motion = .timing(from: from, to: to, t: now, duration: duration, curve: curve)
        case var .spring(spring):
            done = spring.step(&value, dt: dt)
            motion = .spring(spring)
        }
        guard done else { return false }
        motion = nil
        start(carried: 0)
        return motion == nil
    }
}

/// Foil flakes and confetti: a piece's place, speed, turn, size, age and colour.
struct ScratchTicketPiece {
    var x: Double
    var y: Double
    var vx: Double
    var vy: Double
    var angle: Double
    var spin: Double
    var size: Double
    var age: Double
    var life: Double
    var colour: Int
    var flip: Double
}

enum ScratchTicketPieces {
    /// Moves every piece one step: drag, gravity, spin; drops the ones that are spent or off screen.
    static func step(_ list: inout [ScratchTicketPiece], dt: Double, gravity: Double, drag: Double, floor: Double) {
        guard !list.isEmpty else { return }
        let keep = exp(-drag * dt)
        list = list.compactMap { p in
            var p = p
            p.age += dt
            if p.age >= p.life { return nil }
            p.vx *= keep
            p.vy = p.vy * keep + gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if p.y > floor { return nil }
            p.angle += p.spin * dt
            return p
        }
    }

    /// Adds `count` foil flakes torn off around (x, y), thrown a little along the finger's motion.
    static func flakes(_ list: inout [ScratchTicketPiece], count: Int, x: Double, y: Double, vx: Double, vy: Double, spread: Double) {
        for _ in 0..<count {
            let a = Double.random(in: 0..<1) * tau
            let d = sqrt(Double.random(in: 0..<1)) * spread
            list.append(ScratchTicketPiece(
                x: x + cos(a) * d, y: y + sin(a) * d,
                vx: vx * 0.12 + (Double.random(in: 0..<1) - 0.5) * 140,
                vy: vy * 0.12 - 40 - Double.random(in: 0..<1) * 160,
                angle: Double.random(in: 0..<1) * tau, spin: (Double.random(in: 0..<1) - 0.5) * 14,
                size: 2 + Double.random(in: 0..<1) * 3.5, age: 0, life: 0.9 + Double.random(in: 0..<1) * 0.7,
                colour: 0, flip: Double.random(in: 0..<1) * 6
            ))
        }
    }

    /// A burst of confetti from a rectangle's centre, fanned upwards.
    static func confetti(_ list: inout [ScratchTicketPiece], count: Int, x: Double, y: Double, width: Double, colours: Int) {
        for k in 0..<count {
            let a = -Double.pi / 2 + (Double.random(in: 0..<1) - 0.5) * 2.1
            let speed = 420 + Double.random(in: 0..<1) * 720
            list.append(ScratchTicketPiece(
                x: x + (Double.random(in: 0..<1) - 0.5) * width * 0.6, y: y + (Double.random(in: 0..<1) - 0.5) * 30,
                vx: cos(a) * speed, vy: sin(a) * speed,
                angle: Double.random(in: 0..<1) * tau, spin: (Double.random(in: 0..<1) - 0.5) * 9,
                size: 6 + Double.random(in: 0..<1) * 5, age: 0, life: 1.5 + Double.random(in: 0..<1) * 0.9,
                colour: k % colours, flip: Double.random(in: 0..<1) * 6
            ))
        }
    }

    /// One path holding every piece of a colour (−1: all of them) as a small turning quad. A piece
    /// narrows as it flips over (cos of its flip phase) and shrinks away over its last quarter.
    static func path(_ list: [ScratchTicketPiece], colour: Int, aspect: Double) -> Path {
        var path = Path()
        for p in list where colour < 0 || p.colour == colour {
            let t = p.age / p.life
            let fade = t > 0.75 ? (1 - t) / 0.25 : 1
            let size = p.size * fade
            let w = size * (0.2 + 0.8 * abs(cos(p.flip + p.age * 7)))
            let h = size * aspect
            let c = cos(p.angle), s = sin(p.angle)
            let ax = c * w / 2, ay = s * w / 2
            let bx = -s * h / 2, by = c * h / 2
            path.move(to: CGPoint(x: p.x - ax - bx, y: p.y - ay - by))
            path.addLine(to: CGPoint(x: p.x + ax - bx, y: p.y + ay - by))
            path.addLine(to: CGPoint(x: p.x + ax + bx, y: p.y + ay + by))
            path.addLine(to: CGPoint(x: p.x - ax + bx, y: p.y - ay + by))
            path.closeSubpath()
        }
        return path
    }
}

/// The state of one ticket on the table. Two of these take turns: the one in play and the next.
struct ScratchTicketSlot {
    /// Which prize it holds.
    var ticket: Int
    /// Finger path inside the scratch area; nil splits the strokes.
    var points: [CGPoint?] = []
    /// Which cells of the coverage grid the finger has cleared, and how many.
    var grid = [Bool](repeating: false, count: ScratchTicketMetrics.gridX * ScratchTicketMetrics.gridY)
    var cleared = 0
    var revealed = false
    /// When it was revealed (engine seconds), for the prize line's entrance.
    var revealedAt: Double?
    /// 0 → 1 as the rest of the foil dissolves.
    var dissolve = ScratchTicketValue(0)
    /// A short bump on reveal, 0 at rest.
    var pop = ScratchTicketValue(0)
    /// Horizontal travel (pt) and opacity while tickets change places.
    var offset: ScratchTicketValue
    var shown: ScratchTicketValue

    init(ticket: Int, offset: Double, shown: Double) {
        self.ticket = ticket
        self.offset = ScratchTicketValue(offset)
        self.shown = ScratchTicketValue(shown)
    }

    /// The finger's strokes as one smooth path: each point is the control of a curve through midpoints.
    var strokes: Path {
        var path = Path()
        var open = false
        var last = CGPoint.zero
        for point in points {
            guard let p = point else {
                open = false
                continue
            }
            if !open {
                path.move(to: p)
                // A dot even if the finger never moves.
                path.addLine(to: CGPoint(x: p.x + 0.1, y: p.y))
                open = true
            } else {
                path.addQuadCurve(to: CGPoint(x: (last.x + p.x) / 2, y: (last.y + p.y) / 2), control: last)
            }
            last = p
        }
        return path
    }
}

struct ScratchTicketSettings {
    var geo: ScratchTicketGeometry
    var autoPlay: Bool
    var reducedMotion: Bool
    var onReveal: ((String) -> Void)?
}

/// The table's state, stepped once a frame like the RN frame callback, and the gestures that drive it.
@MainActor
final class ScratchTicketEngine {
    private typealias M = ScratchTicketMetrics
    let prizes = ScratchTicketPrize.samples

    private(set) var slots = [
        ScratchTicketSlot(ticket: 0, offset: 0, shown: 1),
        ScratchTicketSlot(ticket: 1, offset: 10000, shown: 0),
    ]
    // How many tickets have been dealt, and which slot is in play.
    private var dealt = 1
    private(set) var active = 0
    private var switching = false
    private(set) var phase = 0.0
    private(set) var tilt = CGPoint.zero
    private var tiltGoal = CGPoint.zero
    private(set) var flakes: [ScratchTicketPiece] = []
    private(set) var confetti: [ScratchTicketPiece] = []
    // What the finger is doing: 0 nothing, 1 scratching, 2 dragging the ticket.
    private var mode = 0
    private(set) var finger = CGPoint.zero
    private var fingerOn = false
    private var fingerFresh = false
    private(set) var fingerAlpha = ScratchTicketValue(0)
    private(set) var press = ScratchTicketValue(1)
    private(set) var emphasis = ScratchTicketValue(0)
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private(set) var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var parked = false
    // The touch under way.
    private var touching = false
    private var lastLocation = CGPoint.zero

    private let success = UINotificationFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, settings: ScratchTicketSettings) {
        let geo = settings.geo
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        // The waiting slot sits just off the right edge once the screen is measured.
        if !parked {
            parked = true
            if !settings.reducedMotion && active != 1 { slots[1].offset.set(geo.width * 1.15) }
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = M.clamp(raw, 0, 0.05)
        guard dt > 0 else { return }
        elapsed += dt
        phase = (phase + dt * tau / M.loop).truncatingRemainder(dividingBy: tau)
        let p = phase

        for k in slots.indices {
            _ = slots[k].dissolve.step(dt)
            _ = slots[k].pop.step(dt)
            _ = slots[k].shown.step(dt)
            if slots[k].offset.step(dt), k == active { switching = false }
        }
        if settings.reducedMotion, switching, !slots[active].shown.moving { switching = false }
        _ = fingerAlpha.step(dt)
        _ = press.step(dt)
        _ = emphasis.step(dt)

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                if slots[active].cleared > 0 {
                    newTicket(user: false, velocity: 0, settings)
                    clock = M.newAt
                } else {
                    clock = 0
                }
            }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            if M.passed(before, t, M.fingerDownAt) && !switching {
                fingerOn = true
                fingerFresh = true
                fingerAlpha.run([.timing(to: 1, duration: 0.16, curve: M.inOutQuad)])
            }
            if fingerOn {
                let s = geo.scratch
                let u = M.clamp((t - M.fingerDownAt) / (M.fingerUpAt - M.fingerDownAt), 0, 1)
                let at = M.zigzag(u, s.width, s.height)
                let x = geo.ticket.minX + s.minX + at.x
                let y = geo.ticket.minY + s.minY + at.y
                let was = finger
                let fresh = fingerFresh
                finger = CGPoint(x: x, y: y)
                fingerFresh = false
                scratch(at: finger, vx: fresh ? 0 : (x - was.x) / dt, vy: fresh ? 0 : (y - was.y) / dt, begin: fresh, user: false, settings)
                if u >= 1 || slots[active].revealed {
                    fingerOn = false
                    fingerAlpha.run([.timing(to: 0, duration: 0.22, curve: M.inOutQuad)])
                }
            }
            if M.passed(before, t, M.newAt) {
                press.run([.timing(to: 0.97, duration: 0.11, curve: M.inOutQuad), .timing(to: 1, duration: 0.18, curve: M.inOutQuad)])
                newTicket(user: false, velocity: 0, settings)
            }
        }

        // The foil's light: towards the finger while scratching, otherwise a slow drift on the loop.
        if mode != 1 && !fingerOn { tiltGoal = CGPoint(x: sin(p) * 0.45, y: cos(p) * 0.3) }
        let ease = 1 - exp(-dt * M.tiltRate)
        tilt = CGPoint(x: tilt.x + (tiltGoal.x - tilt.x) * ease, y: tilt.y + (tiltGoal.y - tilt.y) * ease)

        ScratchTicketPieces.step(&flakes, dt: dt, gravity: M.flakeGravity, drag: M.flakeDrag, floor: geo.height + 20)
        ScratchTicketPieces.step(&confetti, dt: dt, gravity: M.confettiGravity, drag: M.confettiDrag, floor: geo.height + 20)
    }

    // MARK: Tickets

    /// Deals the next ticket into the waiting slot and swaps the two.
    private func deal(user: Bool, velocity: Double, _ settings: ScratchTicketSettings) {
        let geo = settings.geo
        let from = active
        let to = 1 - from
        let next = (dealt + 1) % prizes.count
        dealt += 1
        var coming = ScratchTicketSlot(ticket: next, offset: slots[to].offset.value, shown: slots[to].shown.value)
        coming.offset = slots[to].offset
        coming.shown = slots[to].shown
        slots[to] = coming
        active = to
        emphasis.run([.timing(to: 0, duration: 0.24, curve: M.inOutQuad)])
        if user { light.impactOccurred() }
        if settings.reducedMotion {
            slots[to].offset.set(0)
            slots[to].shown.run([.timing(to: 1, duration: M.fadeTime, curve: M.inOutQuad)])
            slots[from].shown.run([.timing(to: 0, duration: M.fadeTime, curve: M.inOutQuad)])
            return
        }
        slots[to].shown.set(1)
        // Parked off the right edge first, so it always comes in from there.
        slots[to].offset.set(geo.width * 1.15)
        slots[to].offset.run([.spring(to: 0, duration: M.enterSpring.duration, dampingRatio: M.enterSpring.dampingRatio, velocity: 0)])
        slots[from].offset.run([.spring(to: -geo.width * 1.25, duration: M.leaveSpring.duration, dampingRatio: M.leaveSpring.dampingRatio, velocity: velocity)])
    }

    private func newTicket(user: Bool, velocity: Double, _ settings: ScratchTicketSettings) {
        if switching { return }
        switching = true
        fingerOn = false
        fingerAlpha.run([.timing(to: 0, duration: 0.16, curve: M.inOutQuad)])
        deal(user: user, velocity: velocity, settings)
    }

    private func reveal(_ k: Int, user: Bool, _ settings: ScratchTicketSettings) {
        let geo = settings.geo
        slots[k].revealed = true
        slots[k].revealedAt = elapsed
        slots[k].dissolve.run([.timing(to: 1, duration: M.dissolveTime, curve: { M.dissolveEase.value(at: $0) })])
        emphasis.run([.timing(to: 1, duration: 0.3, curve: M.inOutQuad)])
        if !settings.reducedMotion {
            slots[k].pop.run([
                .timing(to: 1, duration: 0.14, curve: { M.easeOut.value(at: $0) }),
                .spring(to: 0, duration: 0.6, dampingRatio: 0.45, velocity: 0),
            ])
            let s = geo.scratch
            ScratchTicketPieces.confetti(
                &confetti, count: M.confetti,
                x: geo.ticket.minX + s.midX, y: geo.ticket.minY + s.midY,
                width: s.width, colours: M.confettiColours.count
            )
        }
        if user { success.notificationOccurred(.success) }
        // The autoPlay script reveals from inside the TimelineView's body; the callback runs after it.
        if let onReveal = settings.onReveal {
            let prize = prizes[slots[k].ticket].prize
            DispatchQueue.main.async { onReveal(prize) }
        }
    }

    /// The finger at (x, y) on screen: rubs the foil there, counts what it cleared, sheds flakes.
    private func scratch(at point: CGPoint, vx: Double, vy: Double, begin: Bool, user: Bool, _ settings: ScratchTicketSettings) {
        let geo = settings.geo
        guard !switching else { return }
        let k = active
        guard !slots[k].revealed else { return }
        let s = geo.scratch
        let lx = point.x - geo.ticket.minX - s.minX - slots[k].offset.value
        let ly = point.y - geo.ticket.minY - s.minY
        let points = slots[k].points
        let last = points.last.flatMap { $0 }
        let joined = !begin && last != nil
        let from = joined ? last! : CGPoint(x: lx, y: ly)
        if joined && hypot(lx - from.x, ly - from.y) < 2.5 { return }
        if begin && !points.isEmpty { slots[k].points.append(nil) }
        slots[k].points.append(CGPoint(x: lx, y: ly))
        tiltGoal = CGPoint(x: M.clamp(lx / s.width * 2 - 1, -1, 1), y: M.clamp(ly / s.height * 2 - 1, -1, 1))

        let added = markCells(&slots[k].grid, from: from, to: CGPoint(x: lx, y: ly), w: s.width, h: s.height)
        let full = slots[k].points.count >= M.pointMax
        guard added > 0 else {
            if full { reveal(k, user: user, settings) }
            return
        }
        slots[k].cleared += added
        if !settings.reducedMotion && flakes.count < M.flakeMax {
            let count = min(5, 1 + Int(floor(hypot(lx - from.x, ly - from.y) / M.flakeEvery)))
            ScratchTicketPieces.flakes(&flakes, count: count, x: point.x, y: point.y, vx: vx, vy: vy, spread: M.brush * 0.35)
        }
        if Double(slots[k].cleared) >= M.revealAt * Double(M.gridX * M.gridY) || full { reveal(k, user: user, settings) }
    }

    /// Marks the grid cells the brush covers between two points; returns how many were new.
    private func markCells(_ grid: inout [Bool], from a: CGPoint, to b: CGPoint, w: Double, h: Double) -> Int {
        let cw = w / Double(M.gridX)
        let ch = h / Double(M.gridY)
        // Int(floor(x / 0)) traps; a window with no size has nothing to clear.
        guard cw > 0, ch > 0, a.x.isFinite, a.y.isFinite, b.x.isFinite, b.y.isFinite else { return 0 }
        let reach = M.brush * 0.45
        let steps = max(1, Int(ceil(hypot(b.x - a.x, b.y - a.y) / 6)))
        var added = 0
        for k in 0...steps {
            let x = a.x + (b.x - a.x) * Double(k) / Double(steps)
            let y = a.y + (b.y - a.y) * Double(k) / Double(steps)
            let c0 = max(0, Int(floor((x - reach) / cw)))
            let c1 = min(M.gridX - 1, Int(floor((x + reach) / cw)))
            let r0 = max(0, Int(floor((y - reach) / ch)))
            let r1 = min(M.gridY - 1, Int(floor((y + reach) / ch)))
            guard c0 <= c1, r0 <= r1 else { continue }
            for r in r0...r1 {
                for c in c0...c1 {
                    let at = r * M.gridX + c
                    if !grid[at] && hypot((Double(c) + 0.5) * cw - x, (Double(r) + 0.5) * ch - y) <= reach {
                        grid[at] = true
                        added += 1
                    }
                }
            }
        }
        return added
    }

    private func touched() {
        hold = M.userHold
        if fingerOn {
            fingerOn = false
            fingerAlpha.run([.timing(to: 0, duration: 0.16, curve: M.inOutQuad)])
        }
    }

    // MARK: Button

    func newTicketPressed(_ settings: ScratchTicketSettings) {
        hold = M.userHold
        if fingerOn {
            fingerOn = false
            fingerAlpha.run([.timing(to: 0, duration: 0.16, curve: M.inOutQuad)])
        }
        if switching { return }
        switching = true
        deal(user: true, velocity: 0, settings)
    }

    func buttonPressing(_ down: Bool) {
        press.run([.timing(to: down ? 0.97 : 1, duration: down ? 0.11 : 0.16, curve: M.inOutQuad)])
    }

    // MARK: Gestures

    func touchMoved(_ value: DragGesture.Value, settings: ScratchTicketSettings) {
        let geo = settings.geo
        if !touching {
            touching = true
            touched()
            mode = 0
            guard !switching else { return }
            let t = geo.ticket
            let s = geo.scratch
            let window = CGRect(x: t.minX + s.minX, y: t.minY + s.minY, width: s.width, height: s.height).insetBy(dx: -6, dy: -6)
            let at = value.startLocation
            if window.contains(at) && !slots[active].revealed {
                mode = 1
                scratch(at: at, vx: 0, vy: 0, begin: true, user: true, settings)
            } else if t.contains(at) {
                mode = 2
            }
        }
        if mode == 1 {
            scratch(at: value.location, vx: value.velocity.width, vy: value.velocity.height, begin: false, user: true, settings)
        } else if mode == 2 && !switching {
            // A deal that started mid-drag (the button, or autoPlay resuming) owns the offsets now.
            let tx = value.translation.width
            slots[active].offset.set(tx < 0 ? tx : M.rubberBand(tx))
        }
    }

    func touchEnded(_ value: DragGesture.Value, settings: ScratchTicketSettings) {
        defer {
            touching = false
            mode = 0
        }
        guard mode == 2, !switching else { return }
        let vx = value.velocity.width
        if value.translation.width < -settings.geo.width * M.commitDistance || vx < -M.commitSpeed {
            newTicket(user: true, velocity: vx, settings)
        } else {
            slots[active].offset.run([.spring(to: 0, duration: M.backSpring.duration, dampingRatio: M.backSpring.dampingRatio, velocity: vx)])
        }
    }

    /// The system took the touch away (no onEnded): let go of the ticket so the next touch starts afresh.
    func touchCancelled() {
        guard touching else { return }
        if mode == 2 && !switching {
            slots[active].offset.run([.spring(to: 0, duration: M.backSpring.duration, dampingRatio: M.backSpring.dampingRatio, velocity: 0)])
        }
        touching = false
        mode = 0
    }
}
