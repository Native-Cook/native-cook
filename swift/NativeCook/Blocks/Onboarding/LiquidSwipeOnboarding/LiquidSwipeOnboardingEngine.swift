import SwiftUI
import UIKit

enum LiquidSwipeOnboardingMetrics {
    // Layout, pt.
    static let side: CGFloat = 24
    static let photoTop: CGFloat = 60 // below the top inset
    static let photoHeight: CGFloat = 0.4 // share of the screen's height
    static let titleSize: CGFloat = 56
    static let titleLeading: CGFloat = 53
    static let bodySize: CGFloat = 16
    static let bodyLeading: CGFloat = 23
    static let dotsLeft: CGFloat = 46
    static let buttonHeight: CGFloat = 52

    // The tab: a smooth bump of the next page sticking out of the right edge (the previous page's
    // on the left), `restReach` deep and `tabRadius` tall either side of its centre, which rests
    // `tabAbove` pt above the bottom inset.
    static let restReach = 26.0
    static let tabRadius = 52.0
    static let tabAbove = 40.0
    // While dragged, the bump's tip follows the finger and it widens as it goes.
    static let pullReach = 0.96 // pt of reach per pt dragged
    static let pullRadius = 0.78 // pt of half-height per pt dragged
    static let follow = 16.0 // 1/s, how quickly the bump's centre follows the finger up and down
    // Past `commitShare` of the width, or flicked faster than `commitSpeed`, the liquid sweeps across.
    static let commitShare = 0.34
    static let commitSpeed = 500.0 // pt/s
    static let releaseFade = 70.0 // ms; a finger held still this long before letting go has lost most of its speed
    static let samples = 44
    // The sweep: a spring with a little overshoot, carried by the release speed; the new page's
    // content rides in `parallax` of the width behind the liquid and bounces into place.
    static let sweepSpring = (duration: 1.0, dampingRatio: 0.7)
    static let returnSpring = (duration: 0.52, dampingRatio: 0.55)
    static let tabSpring = (duration: 0.42, dampingRatio: 0.6)
    static let parallax = 0.55
    // Share of the release speed (in widths per second) the sweep carries on with.
    static let sweepCarry = 0.5
    static let push = 0.18 // how far the covered page is pushed away, share of the width
    static let reducedSweep = 0.28 // s, the crossfade under reduced motion

    // autoPlay (seconds): three swipes forward and three back, then a rest. Each scripted drag
    // runs `dragTime` s and speeds up as it goes, so it lets go with a flick.
    static let loop = 15.0
    static let forward = [1.0, 3.2, 5.4]
    static let back = [8.6, 10.8, 13.0]
    static let starts = forward + back
    static let dragTime = 0.62
    static let dragShare = 0.5 // of the width
    static let dragRise = 110.0 // pt the scripted finger rises over a drag
    static let userHold = 4.0
    // A touch is a tap when it lifts within this long without dragging sideways past `panSlop`.
    static let tapMax = 0.4
    static let panSlop = 8.0
}

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct LiquidSwipeOnboardingSpring {
    let target: Double
    private(set) var velocity: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        var v = velocity
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
        if !k.isFinite { k = 170 }
        stiffness = k
        self.zeta = zeta
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
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A value driven like a Reanimated shared value: set directly, or by a spring or a timing, with
/// a callback that runs when the animation ends or is replaced, as Reanimated's does.
struct LiquidSwipeOnboardingDriven {
    private enum Motion {
        case spring(LiquidSwipeOnboardingSpring)
        case timing(from: Double, to: Double, time: Double, duration: Double)
    }

    private(set) var value: Double
    private var motion: Motion?
    private var done: (() -> Void)?

    init(_ value: Double) { self.value = value }

    /// Puts the value somewhere at once, stopping whatever was moving it.
    mutating func set(_ next: Double) -> (() -> Void)? {
        value = next
        motion = nil
        return takeDone()
    }

    mutating func spring(to target: Double, velocity: Double, duration: Double, dampingRatio: Double, then: (() -> Void)? = nil) -> (() -> Void)? {
        let replaced = takeDone()
        motion = .spring(LiquidSwipeOnboardingSpring(from: value, to: target, velocity: velocity, duration: duration, dampingRatio: dampingRatio))
        done = then
        return replaced
    }

    /// A timing on Reanimated's default curve, an ease-in-out quad.
    mutating func time(to target: Double, duration: Double, then: (() -> Void)? = nil) -> (() -> Void)? {
        let replaced = takeDone()
        motion = .timing(from: value, to: target, time: 0, duration: duration)
        done = then
        return replaced
    }

    /// Steps the animation; returns the callback to run if it ended this frame.
    mutating func step(_ dt: Double) -> (() -> Void)? {
        guard let current = motion else { return nil }
        switch current {
        case .spring(var spring):
            let finished = spring.step(&value, dt: dt)
            motion = finished ? nil : .spring(spring)
            return finished ? takeDone() : nil
        case let .timing(from, to, time, duration):
            let t = time + dt
            let p = min(1, t / max(duration, 0.0001))
            let eased = p < 0.5 ? 2 * p * p : 1 - (-2 * p + 2) * (-2 * p + 2) / 2
            value = from + (to - from) * eased
            motion = p >= 1 ? nil : .timing(from: from, to: to, time: t, duration: duration)
            return p >= 1 ? takeDone() : nil
        }
    }

    private mutating func takeDone() -> (() -> Void)? {
        let callback = done
        done = nil
        return callback
    }
}

struct LiquidSwipeOnboardingStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var restY: Double { height - bottom - LiquidSwipeOnboardingMetrics.tabAbove }
}

struct LiquidSwipeOnboardingSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    /// Where the button on the last page sits.
    var button: CGRect?
    var onChange: ((Int) -> Void)?
    var onDone: (() -> Void)?
}

/// The liquid's shape on one side: its edge from that side, the bump's depth, half-height and centre.
struct LiquidSwipeOnboardingShape {
    var edge: Double
    var reach: Double
    var radius: Double
    var cy: Double
}

/// Everything one frame draws.
struct LiquidSwipeOnboardingFrame {
    var stage: LiquidSwipeOnboardingStage
    var current: Int
    var forward: LiquidSwipeOnboardingShape?
    var back: LiquidSwipeOnboardingShape?
    var baseShift: Double
    var forwardShift: Double
    var backShift: Double
    var forwardOpacity: Double
    var backOpacity: Double
    var forwardChevron: Double
    var backChevron: Double
    var buttonShade: Double
}

/// The pages' state, stepped once a frame like the React Native frame callback, and the touch.
@MainActor
final class LiquidSwipeOnboardingEngine {
    private typealias M = LiquidSwipeOnboardingMetrics

    private var current = 0
    // Which side is moving: 1 the next page from the right, -1 the previous one from the left, 0 none.
    private var side = 0
    private var drag = LiquidSwipeOnboardingDriven(0) // pt dragged toward the middle
    private var sweep = LiquidSwipeOnboardingDriven(0) // 0 … 1 as the liquid fills the screen
    private var busy = false
    private var tab = LiquidSwipeOnboardingDriven(1) // the tabs grow back in after a page lands
    private var fingerY = 0.0
    private var tabY = 0.0
    private var dragging = false
    private var pressed = LiquidSwipeOnboardingDriven(0)
    // Where the finger was last seen moving, when (ms), and how fast (pt/s).
    private var track = (x: 0.0, t: 0.0, v: 0.0)
    // The frame clock, ms, for timing the finger.
    private var elapsed = 0.0

    // The scripted finger.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var scripted = -1.0
    private var stage = LiquidSwipeOnboardingStage(width: 0, height: 0, top: 0, bottom: 0)
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var settings = LiquidSwipeOnboardingSettings(count: 4, autoPlay: false, reducedMotion: false)

    // The touch under way.
    private var touchStart: Date?
    private var panning = false
    private var tapAlive = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)

    // MARK: Frame

    func step(to date: Date, stage: LiquidSwipeOnboardingStage, settings: LiquidSwipeOnboardingSettings) -> LiquidSwipeOnboardingFrame {
        if stage != self.stage {
            self.stage = stage
            if !dragging {
                fingerY = stage.restY
                tabY = stage.restY
            }
        }
        self.settings = settings
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 { advance(dt) }
        return frame()
    }

    private func advance(_ dt: Double) {
        elapsed += dt * 1000
        // The animations, each running its callback when it ends.
        drag.step(dt)?()
        sweep.step(dt)?()
        tab.step(dt)?()
        pressed.step(dt)?()

        let s = stage
        guard s.width > 0 else { return }

        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for (k, at) in M.starts.enumerated() where Self.crossed(before, t, at) {
                if grab(k < M.forward.count ? 1 : -1) { scripted = at }
            }
            if scripted >= 0 {
                // The finger speeds up as it drags, rising a little like a thumb's arc.
                let u = min(1, max(0, (t - scripted) / M.dragTime))
                setDrag(s.width * M.dragShare * u * u)
                fingerY = s.restY - M.dragRise * sin(u * Double.pi / 2)
                if u >= 1 {
                    scripted = -1
                    letGo(2 * s.width * M.dragShare / M.dragTime, user: false)
                }
            }
        }

        // The bump's centre follows the finger, and drifts home when let go.
        let target = dragging ? fingerY : s.restY
        tabY += (target - tabY) * (1 - exp(-dt * M.follow))
    }

    private func setDrag(_ value: Double) {
        drag.set(value)?()
    }

    private func frame() -> LiquidSwipeOnboardingFrame {
        let s = stage
        let reduced = settings.reducedMotion
        let w = s.width
        let d = drag.value
        let f = sweep.value
        let forward = shape(1)
        let back = shape(-1)
        return LiquidSwipeOnboardingFrame(
            stage: s,
            current: current,
            forward: forward,
            back: back,
            // The covered page is pushed away from the liquid; the incoming page rides in behind its edge.
            baseShift: reduced ? 0 : -Double(side) * (d * 0.1 + w * M.push * min(1, f)),
            forwardShift: reduced ? 0 : w * M.parallax * (1 - (side == 1 ? f : 0)),
            backShift: reduced ? 0 : -w * M.parallax * (1 - (side == -1 ? f : 0)),
            forwardOpacity: reduced && side == 1 && f > 0 ? f : 1,
            backOpacity: reduced && side == -1 && f > 0 ? f : 1,
            forwardChevron: chevronShown(1),
            backChevron: chevronShown(-1),
            buttonShade: current == settings.count - 1 && side == 0 ? pressed.value * 0.22 : 0
        )
    }

    /// How much of a resting tab shows: it grows back after a page lands, and the tab on the far
    /// side tucks away while the other side's liquid sweeps in, since it will bring another page.
    private func tabShown(_ direction: Int) -> Double {
        let other = side != 0 && side != direction
        return tab.value * (other ? max(0, 1 - sweep.value * 2.5) : 1)
    }

    /// The liquid's shape on one side: its tab at rest, pulled after the finger, then sweeping across.
    private func shape(_ direction: Int) -> LiquidSwipeOnboardingShape? {
        let s = stage
        let next = current + direction
        if next < 0 || next >= settings.count || s.width <= 0 { return nil }
        let active = side == direction
        let d = active ? drag.value : 0
        let f = active ? sweep.value : 0
        let cy = active ? tabY : s.restY
        let reach = max(active ? 3 : 0, M.restReach * (active ? 1 : tabShown(direction)) + d * M.pullReach)
        let radius = M.tabRadius + max(0, d) * M.pullRadius
        if settings.reducedMotion && f > 0 { return LiquidSwipeOnboardingShape(edge: s.width + 4, reach: 0, radius: s.height, cy: cy) }
        return LiquidSwipeOnboardingShape(
            edge: (s.width + 60) * f,
            reach: reach * (1 - f),
            radius: radius + s.height * 1.3 * f,
            cy: cy + (s.height / 2 - cy) * f
        )
    }

    /// The chevron fades as its tab is pulled, and with the tab.
    private func chevronShown(_ direction: Int) -> Double {
        let active = side == direction
        return max(0, 1 - (active ? drag.value / 60 + sweep.value * 4 : 0)) * tabShown(direction)
    }

    // MARK: Pages

    /// The page under the liquid has filled the screen: it becomes the current page.
    private func settle() {
        current += side
        side = 0
        setDrag(0)
        sweep.set(0)?()
        busy = false
        tab.set(0)?()
        tab.spring(to: 1, velocity: 0, duration: M.tabSpring.duration, dampingRatio: M.tabSpring.dampingRatio)?()
        settings.onChange?(current)
    }

    /// Lets go of the liquid: it sweeps across, or falls back into its tab.
    private func letGo(_ velocity: Double, user: Bool) {
        let s = stage
        let d = drag.value
        dragging = false
        if side == 0 { return }
        if d > s.width * M.commitShare || (velocity > M.commitSpeed && d > 24) {
            // Nothing else moves the sweep while it runs, so it always ends in `settle`.
            busy = true
            if user { light.impactOccurred() }
            if settings.reducedMotion {
                sweep.time(to: 1, duration: M.reducedSweep) { [weak self] in self?.settle() }?()
            } else {
                sweep.spring(
                    to: 1,
                    velocity: max(0, velocity) / s.width * M.sweepCarry,
                    duration: M.sweepSpring.duration,
                    dampingRatio: M.sweepSpring.dampingRatio
                ) { [weak self] in self?.settle() }?()
            }
        } else {
            drag.spring(to: 0, velocity: -velocity, duration: M.returnSpring.duration, dampingRatio: M.returnSpring.dampingRatio) { [weak self] in
                guard let self else { return }
                if !self.dragging { self.side = 0 }
            }?()
        }
    }

    private func grab(_ direction: Int) -> Bool {
        let next = current + direction
        if busy || next < 0 || next >= settings.count { return false }
        side = direction
        dragging = true
        return true
    }

    private func touched() {
        hold = M.userHold
        scripted = -1
    }

    // MARK: Touch

    /// One recogniser stands in for the race of a pan (after 8 pt of sideways travel) and a tap (a
    /// touch lifted within 400 ms that never turned into a pan).
    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            tapAlive = true
            light.prepare()
            // The button darkens as soon as it is pressed.
            if let b = settings.button, current == settings.count - 1,
               value.startLocation.x >= b.minX, value.startLocation.x <= b.maxX,
               abs(value.startLocation.y - b.midY) < b.height {
                pressed.time(to: 1, duration: 0.1)?()
            }
        }
        if !panning {
            guard abs(value.translation.width) > M.panSlop else { return }
            panning = true
            if tapAlive {
                tapAlive = false
                pressed.time(to: 0, duration: 0.18)?()
            }
            panStart(translationX: value.translation.width, y: value.location.y)
        }
        panUpdate(translationX: value.translation.width, y: value.location.y)
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            panEnd()
            return
        }
        if tapAlive {
            tapAlive = false
            pressed.time(to: 0, duration: 0.18)?()
            if let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMax {
                tap(at: value.location)
            }
        }
    }

    /// A touch the system cancelled: a drag lets go where it is, like the RN pan's onFinalize; a
    /// tap never happened.
    func touchLost() {
        guard touchStart != nil else { return }
        touchStart = nil
        if tapAlive {
            tapAlive = false
            pressed.time(to: 0, duration: 0.18)?()
        }
        if panning {
            panning = false
            if dragging { letGo(0, user: true) }
        }
    }

    private func panStart(translationX: Double, y: Double) {
        touched()
        if busy { return }
        // A drag can start anywhere: to the left brings the next page, to the right the previous.
        let direction = translationX < 0 ? 1 : -1
        // Catch a tab that is still falling back.
        if side != 0 && side != direction { return }
        if !grab(direction) { return }
        // Setting the drag stops a spring that was taking it back.
        setDrag(drag.value)
        fingerY = y
        track = (translationX, elapsed, 0)
    }

    private func panUpdate(translationX: Double, y: Double) {
        guard dragging else { return }
        let s = stage
        let raw = Double(side) * -translationX
        // Pulled the wrong way, the tab gives a little; far past the screen it resists.
        setDrag(raw < 0 ? -Self.rubber(-raw, 0) * 0.3 : Self.rubber(raw, s.width * 0.85))
        fingerY = min(s.height - M.tabRadius * 0.6, max(M.tabRadius * 0.6, y))
        // The finger's own speed, smoothed, so a drag that stopped before letting go counts as still.
        let last = track
        let now = elapsed
        let dt = (now - last.t) / 1000
        // Measured across whole frames: two touches within one frame wait for the next.
        if dt > 0.004 && translationX != last.x {
            track = (translationX, now, last.v + ((translationX - last.x) / dt - last.v) * 0.6)
        }
    }

    private func panEnd() {
        guard dragging else { return }
        hold = M.userHold
        let last = track
        let still = exp(-(elapsed - last.t) / M.releaseFade)
        letGo(Double(side) * -last.v * still, user: true)
    }

    private func tap(at point: CGPoint) {
        touched()
        let s = stage
        let near = abs(point.y - s.restY) < M.tabRadius + 24
        if current == settings.count - 1, let b = settings.button, point.x >= b.minX, near {
            medium.impactOccurred()
            settings.onDone?()
            return
        }
        // Tapping a tab pours its page in.
        let direction = point.x > s.width - 64 ? 1 : point.x < 64 ? -1 : 0
        if direction == 0 || !near || !grab(direction) { return }
        fingerY = s.restY
        setDrag(s.width * 0.12)
        letGo(M.commitSpeed * 2, user: true)
    }

    /// The next or previous page, for VoiceOver.
    func page(_ direction: Int) {
        guard grab(direction) else { return }
        setDrag(stage.width * 0.12)
        letGo(M.commitSpeed * 2, user: true)
    }

    // MARK: Helpers

    /// Whether a clock that went from `before` to `now`, wrapping at the loop, passed `at`.
    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// Diminishing pull past a limit.
    static func rubber(_ x: Double, _ limit: Double) -> Double {
        if x <= limit { return x }
        let over = x - limit
        return limit + over / (1 + over / 120)
    }
}
