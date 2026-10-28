import CoreMotion
import QuartzCore
import SwiftUI

private typealias M = HabitatSpecimenCarouselMotion
private typealias Spring = HabitatSpecimenCarouselSpringConfig

/// The carousel's state and its one clock. Mirrors useHabitatMotion in HabitatMotion.ts: every
/// value the screen draws is read from here, on the same frame. The display link steps the
/// animated values, runs the demo's timeline, then asks the renderer to draw.
@MainActor
final class HabitatSpecimenCarouselModel: ObservableObject {
    // MARK: Configuration
    let autoPlay: Bool
    let previewTime: Double?
    private let previewReduced: Bool?
    var systemReduced = false
    var reduced: Bool { previewReduced ?? systemReduced }
    /// Finger travel for one specimen, and the screen width (for the head's look towards the finger).
    var gap = 228.0
    var width = 393.0

    // MARK: Callbacks
    var onSignature: (() -> Void)?
    var onLand: (() -> Void)?
    var onPet: (() -> Void)?
    /// Runs after each tick: the engine draws.
    var onFrame: (() -> Void)?

    // MARK: State the screen draws
    let rawPosition: HabitatSpecimenCarouselValue
    let influence = HabitatSpecimenCarouselValue(0)
    /// Anticipation while a finger rests on the specimen, 0..1.
    let press = HabitatSpecimenCarouselValue(0)
    /// Where the head looks, -1 (left) .. 1 (right), springing back to 0.
    let look = HabitatSpecimenCarouselValue(0)
    /// Petting under a hold: amount 0..1.
    let pet = HabitatSpecimenCarouselValue(0)
    /// One landing squash (0 to 1 over landMS) for the specimen that just arrived.
    let landing = HabitatSpecimenCarouselValue(1)
    /// Signature progress 0..1 and the specimen performing it.
    let power: HabitatSpecimenCarouselValue
    /// Seconds since the move began, running past it while its particles land; -1 when idle.
    let fx: HabitatSpecimenCarouselValue
    private(set) var powerIndex: Int
    private(set) var time: Double
    /// Ambient loop time 0..1, frozen under Reduced Motion.
    private(set) var ambientTime: Double
    /// Seconds since a hold began, and the clock at its release.
    private(set) var petClock = 0.0
    private(set) var petUntil = -1.0
    private(set) var landingIndex: Int
    /// The finger on the stage (stage points): x, y, velocity x, y, down; and the last tap: x, y, frame ms.
    private(set) var touch: [Double] = [-999, -999, 0, 0, 0]
    private(set) var tapAt: [Double] = [0, 0, -1]
    /// The key light's tilt, from the phone's gravity.
    private(set) var tilt: (Double, Double) = (0, 0)

    @Published private(set) var selectedIndex: Int
    @Published private(set) var demoTab: Int
    /// True once a person has touched the carousel (the hint leaves for good).
    @Published private(set) var touched = false

    var position: Double { M.wrapPosition(rawPosition.value) }

    // MARK: Internals
    private var autoTab: Int
    private var manual = false { didSet { if manual && !touched { objectWillChange.send(); touched = true } } }
    private var dragging = false
    private var dragOrigin = 0.0
    private var nowMS = 0.0
    /// Milliseconds between the last two frames, and the last frame's clock.
    private(set) var frameDelta = 16.0
    var frameMS: Double { nowMS }
    private var resumeAt = 0.0
    private var foreground = true
    private var focused = false
    private var resumePending = false
    private var fxHeld = false
    private var landed: Int
    private var touchClock = 0.0
    private var lastFrame: CFTimeInterval = 0
    private var link: CADisplayLink?
    private var inTick = false
    private let motionManager = CMMotionManager()
    private var restGravity: (Double, Double)?

    // gesture state
    private enum Decision { case undecided, pan, hold, tap }
    private var decision = Decision.undecided
    private var touching = false
    private var downAt = 0.0
    private var downPoint = (0.0, 0.0)
    private var samples: [(t: Double, x: Double)] = []

    init(autoPlay: Bool, previewTime: Double?, previewReduced: Bool?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.previewReduced = previewReduced
        let initial = previewTime == nil ? 0 : M.clamp(previewTime! / M.loop).truncatingRemainder(dividingBy: 1)
        let startPosition = M.timelinePosition(initial)
        let index = Int(M.round(M.wrapPosition(startPosition))) % 3
        rawPosition = HabitatSpecimenCarouselValue(startPosition)
        time = initial
        ambientTime = initial
        power = HabitatSpecimenCarouselValue(previewTime == nil ? 0 : M.timelineSignature(initial))
        fx = HabitatSpecimenCarouselValue(previewTime == nil ? -1 : M.timelineEffect(initial))
        powerIndex = previewTime == nil ? index : M.timelinePerformer(initial)
        landingIndex = index
        landed = index
        selectedIndex = index
        autoTab = M.timelineTab(initial)
        demoTab = M.timelineTab(initial)
    }

    // MARK: Lifecycle

    func setFocused(_ value: Bool) {
        guard focused != value else { return }
        focused = value
        if value {
            if foreground { restoreManual() }
            start()
        } else {
            suspendManual()
            stop()
        }
    }

    func setForeground(_ value: Bool) {
        guard foreground != value else { return }
        foreground = value
        if !value { suspendManual() } else if focused { restoreManual() }
    }

    func setSystemReduced(_ value: Bool) {
        systemReduced = value
        applyReduced()
    }

    private func applyReduced() {
        if reduced { influence.cancel(); influence.set(0) }
    }

    private func start() {
        guard previewTime == nil, link == nil else { return }
        let target = DisplayLinkTarget { [weak self] link in self?.tick(link) }
        let created = CADisplayLink(target: target, selector: #selector(DisplayLinkTarget.fire(_:)))
        created.add(to: .main, forMode: .common)
        link = created
        lastFrame = 0
        if motionManager.isDeviceMotionAvailable {
            motionManager.deviceMotionUpdateInterval = 1.0 / 60
            motionManager.startDeviceMotionUpdates()
        }
    }

    private func stop() {
        link?.invalidate()
        link = nil
        motionManager.stopDeviceMotionUpdates()
    }

    private func suspendManual() {
        rawPosition.cancel()
        influence.cancel(); influence.set(0)
        dragging = false
        resumePending = true
    }

    private func restoreManual() {
        resumePending = true
        if !manual { return }
        rawPosition.spring(to: M.round(rawPosition.value), config: .timed(400, 1).reduced(reduced), now: clockMS())
    }

    /// The clock an animation starts on: the frame's inside a tick, now between frames.
    private func clockMS() -> Double { inTick ? nowMS : CACurrentMediaTime() * 1000 }

    // MARK: The frame

    private func tick(_ link: CADisplayLink) {
        let timestamp = link.timestamp * 1000
        let previous = lastFrame == 0 ? 0 : (link.timestamp - lastFrame) * 1000
        lastFrame = link.timestamp
        frameDelta = previous > 0 ? previous : 16
        inTick = true
        defer { inTick = false }
        stepAnimations(timestamp)
        holdCheck(timestamp)
        advance(timestamp: timestamp, sincePrevious: previous)
        reactions()
        updateKeyLight(sincePrevious: previous)
        onFrame?()
    }

    private func stepAnimations(_ now: Double) {
        for value in [rawPosition, influence, press, look, pet, landing, power, fx] { value.step(now: now) }
    }

    private func advance(timestamp: Double, sincePrevious: Double) {
        guard focused, foreground, previewTime == nil else { return }
        let elapsed = min(sincePrevious, 64) / (M.loop * 1000)
        if !reduced { ambientTime = (ambientTime + elapsed).truncatingRemainder(dividingBy: 1) }
        nowMS = timestamp
        // Hearts follow the wall clock (slow frames must not slow the stream), within reason.
        let seconds = min(sincePrevious, 250) / 1000
        if pet.value > 0 || petClock < petUntil + 1.4 { petClock += seconds }
        guard autoPlay else { return }
        if resumePending {
            resumePending = false
            if manual { resumeAt = timestamp + 2000 }
            return
        }
        if manual {
            if dragging || timestamp < resumeAt { return }
            let index = Int(M.round(M.wrapPosition(rawPosition.value))) % 3
            // Resume after this specimen's demo move, so a person never sees it replayed out of turn.
            rawPosition.set(Double(index))
            time = M.snapTimes[index] + 2.4 / M.loop
            manual = false
            fxHeld = true
            return
        }
        let p = (time + elapsed).truncatingRemainder(dividingBy: 1)
        time = p
        rawPosition.set(M.timelinePosition(p))
        let tab = M.timelineTab(p)
        if tab != autoTab { autoTab = tab }
        if reduced { power.set(0); fx.set(-1); return }
        power.set(M.timelineSignature(p))
        let effect = M.timelineEffect(p)
        // A person's move keeps its performer and clock until the demo's next move begins.
        if fxHeld {
            if effect < 0 { fxHeld = false }
        } else {
            if effect != fx.value { fx.set(effect) }
            powerIndex = M.timelinePerformer(p)
        }
    }

    /// useAnimatedReaction blocks: the semantic selection, the demo's panel, and the landing squash.
    private func reactions() {
        let index = Int(M.round(M.wrapPosition(rawPosition.value))) % 3
        if index != selectedIndex { objectWillChange.send(); selectedIndex = index }
        if autoTab != demoTab { objectWillChange.send(); demoTab = autoTab }
        // A specimen lands when its hop ends (8% before the slot), whether the demo, a fling or a slow drag brought it.
        let near = abs(M.specimenDistance(index, rawPosition.value)) < 0.08 ? index : -1
        if near >= 0 && near != landed {
            landed = near
            if reduced { return }
            landingIndex = near
            landing.set(0)
            landing.timing(to: 1, durationMS: M.landMS, easing: .linear, now: clockMS())
            if manual { onLand?() }
        }
    }

    /// The key light leans with the phone (gravity, high-passed so it re-centres on however the phone
    /// is held). Still under Reduced Motion.
    private func updateKeyLight(sincePrevious: Double) {
        guard let gravity = motionManager.deviceMotion?.gravity else { return }
        if reduced || (gravity.x == 0 && gravity.y == 0) { return }
        let gx = gravity.x, gy = gravity.y
        let base = restGravity ?? (gx, gy)
        let a = min(1, (sincePrevious == 0 ? 16 : sincePrevious) / 1000 / 2.5)
        restGravity = (base.0 + (gx - base.0) * a, base.1 + (gy - base.1) * a)
        let target = (max(-1, min(1, (gx - base.0) * 3)), max(-1, min(1, (base.1 - gy) * 3)))
        tilt = (tilt.0 + (target.0 - tilt.0) * 0.18, tilt.1 + (target.1 - tilt.1) * 0.18)
    }

    /// The key light over the baked volume: it leans with the phone and drifts slowly on its own.
    var keyLight: (Double, Double, Double) {
        let still = reduced
        let drift = still ? 0 : sin(ambientTime * Double.pi * 2 * 2)
        let t = still ? (0.0, 0.0) : tilt
        return (-0.5 + drift * 0.12 + t.0 * 0.55, -0.6 + t.1 * 0.45, 1)
    }

    /// The habitat front's slow current, held still outside a crossing.
    var frontPhase: Double {
        let p = position
        return p - p.rounded(.down) > 0.0005 && !reduced ? Double.pi * 2 * ambientTime.truncatingRemainder(dividingBy: 1) : 0
    }

    // MARK: Gestures (Pan, Tap and LongPress in a race, as in the reference)

    private func clampLook(_ x: Double) -> Double { M.clamp((x - width / 2) / (width * 0.4), -1, 1) }

    func touchBegan(x: Double, y: Double) {
        touching = true
        decision = .undecided
        downAt = clockMS()
        downPoint = (x, y)
        samples = [(downAt, x)]
        // onTouchesDown: every finger on the stage stirs the particle world.
        touch = [x, y, 0, 0, 1]
        touchClock = nowMS
        // Pan.onBegin
        if focused && foreground {
            rawPosition.cancel(); influence.cancel()
            manual = true; dragging = true; dragOrigin = rawPosition.value; resumeAt = .greatestFiniteMagnitude
        }
        // Tap.onBegin: feedback lands with the finger.
        if focused && !reduced {
            press.timing(to: 1, durationMS: 140, easing: .pressIn, now: clockMS())
            look.spring(to: clampLook(x), config: Self.lookSpring, now: clockMS())
        }
    }

    func touchMoved(x: Double, y: Double) {
        guard touching else { return }
        let now = clockMS()
        samples.append((now, x))
        while let first = samples.first, now - first.t > 120 { samples.removeFirst() }
        // onTouchesMove
        let elapsed = max(8, nowMS - touchClock) / 1000
        touch = [x, y,
                 touch[2] + ((x - touch[0]) / elapsed - touch[2]) * 0.5,
                 touch[3] + ((y - touch[1]) / elapsed - touch[3]) * 0.5, 1]
        touchClock = nowMS
        if decision == .undecided {
            let dx = x - downPoint.0, dy = y - downPoint.1
            if hypot(dx, dy) >= 3 {
                decision = .pan
                tapFinalize()
            } else { return }
        }
        if decision == .pan { panUpdate(translation: x - downPoint.0, x: x) }
    }

    func touchEnded(x: Double, y: Double, cancelled: Bool) {
        guard touching else { return }
        touching = false
        // onTouchesUp
        touch = [touch[0], touch[1], touch[2], touch[3], 0]
        let now = clockMS()
        switch decision {
        case .pan:
            if !cancelled { panEnd(velocity: velocity(now)) }
            panFinalize()
        case .hold:
            holdFinalize()
        case .undecided:
            let quick = now - downAt <= 450
            // The tap wins the race; the pan is cancelled first.
            panFinalize()
            if !cancelled && quick && hypot(x - downPoint.0, y - downPoint.1) <= 10 { tapEnd(x: x, y: y, now: now) }
            tapFinalize()
            holdFinalize()
        case .tap:
            break
        }
        decision = .undecided
    }

    private func velocity(_ now: Double) -> Double {
        guard let first = samples.first, let last = samples.last, last.t - first.t > 10 else { return 0 }
        return (last.x - first.x) / ((last.t - first.t) / 1000)
    }

    /// The hold wins the race 380 ms into a touch that has not moved.
    private func holdCheck(_ now: Double) {
        guard touching, decision == .undecided, now - downAt >= 380 else { return }
        decision = .hold
        // The pan and the tap are cancelled first.
        panFinalize()
        tapFinalize()
        holdStart()
    }

    static let lookSpring = Spring.timed(520, 0.9)

    private func panUpdate(translation: Double, x: Double) {
        guard dragging, focused, foreground else { return }
        // Choose Your Ball's rule: one wipe, one specimen. The drag tracks the finger 1:1 up to one
        // slot from where it began, then resists asymptotically instead of stopping dead.
        let home = M.round(dragOrigin)
        let drift = dragOrigin - translation / gap - home
        let over = abs(drift) - M.panReach
        let sign: Double = drift < 0 ? -1 : (drift > 0 ? 1 : 0)
        rawPosition.set(over <= 0 ? home + drift : home + sign * (M.panReach + over / (1 + over * M.panResist)))
        influence.set(reduced ? 0 : M.gestureInfluence(translation, velocity(clockMS()), gap))
        if !reduced { look.spring(to: clampLook(x), config: Self.lookSpring, now: clockMS()) }
    }

    private func panEnd(velocity v: Double) {
        guard dragging else { return }
        let velocity = -v / gap
        let home = M.round(dragOrigin)
        let projected = rawPosition.value + velocity * M.flingProjection
        let target = M.round(max(home - M.panReach, min(home + M.panReach, projected)))
        // The same landing spring as the ball: settles without a visible bounce but overshoots once.
        var config = Spring.snap
        config.velocity = velocity
        rawPosition.spring(to: target, config: config.reduced(reduced), now: clockMS())
        influence.spring(to: 0, config: Spring.timed(650, 1).reduced(reduced), now: clockMS())
        dragging = false
        resumeAt = nowMS + 2000
    }

    private func panFinalize() {
        look.spring(to: 0, config: Self.lookSpring, now: clockMS())
        guard dragging else { return }
        rawPosition.spring(to: M.round(rawPosition.value), config: Spring.timed(400, 1).reduced(reduced), now: clockMS())
        influence.spring(to: 0, config: Spring.timed(650, 1).reduced(reduced), now: clockMS())
        dragging = false
        resumeAt = nowMS + 2000
    }

    private func tapFinalize() {
        press.timing(to: 0, durationMS: 200, easing: .pressIn, now: clockMS())
        if pet.value == 0 { look.spring(to: 0, config: Self.lookSpring, now: clockMS()) }
    }

    /// A tap on a resting specimen performs its signature move.
    private func tapEnd(x: Double, y: Double, now: Double) {
        guard focused, foreground, !reduced else { return }
        // The release sends a ring of air through the particles.
        tapAt = [x, y, nowMS]
        let current = rawPosition.value
        let index = Int(M.round(M.wrapPosition(current))) % 3
        if abs(M.specimenDistance(index, current)) > 0.04 { return }
        let running = power.value
        if running > 0 && running < 0.72 { return }
        rawPosition.cancel(); rawPosition.set(M.round(current))
        manual = true; dragging = false; resumeAt = nowMS + M.signatureMS + 2000
        powerIndex = index
        power.set(0)
        power.timing(to: 1, durationMS: M.signatureMS, easing: .linear, now: now)
        fx.set(0)
        fx.timing(to: M.fxSeconds, durationMS: M.fxSeconds * 1000, easing: .linear, now: now)
        onSignature?()
    }

    /// A hold pets the specimen: it squints, wiggles and hearts rise for as long as the finger stays.
    private func holdStart() {
        guard focused, foreground else { return }
        let current = rawPosition.value
        if abs(M.specimenDistance(Int(M.round(M.wrapPosition(current))) % 3, current)) > 0.04 { return }
        rawPosition.cancel(); rawPosition.set(M.round(current))
        manual = true; dragging = false; resumeAt = .greatestFiniteMagnitude
        petClock = 0; petUntil = .greatestFiniteMagnitude
        pet.timing(to: 1, durationMS: 220, easing: .pressIn, now: clockMS())
        onPet?()
    }

    private func holdFinalize() {
        // Only a hold that became a pet releases one; every failed hold (each tap) ends here too.
        if petUntil <= 1e9 { return }
        petUntil = petClock
        pet.timing(to: 0, durationMS: 260, easing: .pressIn, now: clockMS())
        look.spring(to: 0, config: Self.lookSpring, now: clockMS())
        resumeAt = nowMS + 2000
    }

    /// Any other control the person touches keeps the demo paused for `ms`.
    func holdDemo(_ ms: Double) {
        guard focused else { return }
        if !manual { rawPosition.cancel(); rawPosition.set(M.round(rawPosition.value)) }
        manual = true
        resumeAt = nowMS + ms
    }

    func select(_ index: Int) {
        guard focused, foreground else { return }
        rawPosition.cancel()
        let current = rawPosition.value
        let target = current + M.specimenDistance(index, current)
        influence.spring(to: 0, config: Spring.timed(650, 1).reduced(reduced), now: clockMS())
        manual = true; dragging = false; resumeAt = nowMS + 2000
        rawPosition.spring(to: target, config: Spring.snap.reduced(reduced), now: clockMS())
    }

    /// Draws one frame outside the display link (a frozen preview, or when the layout changes).
    func refresh() { onFrame?() }
}

private extension HabitatSpecimenCarouselSpringConfig {
    /// ReduceMotion.Always when Reduced Motion is on: the spring is skipped.
    func reduced(_ on: Bool) -> HabitatSpecimenCarouselSpringConfig {
        var copy = self
        copy.skip = on
        return copy
    }
}

/// CADisplayLink retains its target; this one holds the model weakly through a closure.
private final class DisplayLinkTarget: NSObject {
    private let handler: @MainActor (CADisplayLink) -> Void
    init(_ handler: @escaping @MainActor (CADisplayLink) -> Void) { self.handler = handler }
    @objc func fire(_ link: CADisplayLink) { MainActor.assumeIsolated { handler(link) } }
}
