import CoreGraphics
import Observation
import QuartzCore
import UIKit

enum GlassLockKeyMode: Int, Equatable {
    case idle
    case carrying
    case inserting
    case seated
    case open
    case closing
}

enum GlassLockKeyGrab: Int, Equatable {
    case none
    case key
    case ring
}

enum GlassLockKeyCommand: Equatable {
    case unlock
    case lock
}

enum GlassLockKeyInput: Equatable {
    case down(CGPoint, scripted: Bool = false)
    case move(CGPoint)
    case up(velocity: CGVector)
    case command(GlassLockKeyCommand, scripted: Bool = false)
}

enum GlassLockKeyHaptic: Int, Equatable {
    case grab = 1
    case seat = 2
    case detent = 3
    case open = 4
    case wrong = 5
}

struct GlassLockKeyGeometry: Equatable {
    let s: Double
    let restX: Double
    let restY: Double
    let plugX: Double
    let plugY: Double
    let ringX: Double
    let ringY: Double

    init(width: Double) {
        s = width / 402
        restX = GlassLockKeyConstants.UpgradeLayout.keyRest.x * s
        restY = GlassLockKeyConstants.UpgradeLayout.keyRest.y * s
        plugX = GlassLockKeyConstants.UpgradeLayout.plug.x * s
        plugY = GlassLockKeyConstants.UpgradeLayout.plug.y * s
        ringX = GlassLockKeyConstants.UpgradeLayout.ring.x * s
        ringY = GlassLockKeyConstants.UpgradeLayout.ring.y * s
    }
}

/// One fixed-step snapshot of the active `REPLICA = false` React Native upgrade.
/// Field names intentionally mirror `GSim` in `glass-sim.ts` so every rendered value can be audited directly.
struct GlassLockKeySimulationState: Equatable {
    var acc = 0.0
    var clock = 0.0
    var mode = GlassLockKeyMode.idle
    var modeT = 0.0

    var kx: Double
    var ky: Double
    var kvx = 0.0
    var kvy = 0.0
    var lift = 0.0

    var th = 0.0
    var tv = 0.0
    var detent = 0
    var strain = 0.0

    var shackle = 0.0
    var shackleV = 0.0
    var sheet = 0.0
    var glint = -1.0
    var shake = -1.0
    var show = 0.0
    var idleT = 0.0
    var tick = -1.0

    var grab = GlassLockKeyGrab.none
    var scripted = false
    var gx = 0.0
    var gy = 0.0
    var fx = 0.0
    var fy = 0.0
    var x0 = 0.0
    var th0 = 0.0

    var demoOn: Bool
    var demoT = 0.0
    var quiet = 0.0
    var auto = -1.0
    var reduced: Bool
    var events: [GlassLockKeyHaptic] = []

    /// Unlike RN's `grab`, this remains true after magnetic seating until the recognizer actually ends.
    var touching = false

    var isOpen: Bool { mode == .open }

    init(geometry: GlassLockKeyGeometry, reduced: Bool, autoPlay: Bool) {
        kx = geometry.restX
        ky = geometry.restY
        demoOn = autoPlay
        self.reduced = reduced
    }
}

/// Main-thread fixed-step driver for the Glass Lock Key upgrade.
///
/// React Native retains `RESUME_MS = 3500` as a source token. The Swift port intentionally follows the owner
/// override: a pointer interaction waits two seconds after the real lift/cancel, physically returns to rest, then
/// restarts the loop. Scripted and accessibility-command inputs retain the RN choreography.
@MainActor
@Observable
final class GlassLockKeySimulation {
    private(set) var state: GlassLockKeySimulationState

    private(set) var geometry: GlassLockKeyGeometry
    @ObservationIgnored
    private var autoPlay: Bool
    @ObservationIgnored
    private var displayLink: CADisplayLink?
    @ObservationIgnored
    private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored
    private var returningToRest = false
    @ObservationIgnored
    private var shouldResumeAfterTouch = false
    @ObservationIgnored
    private var resumeWaitsForTerminalMode = false
    @ObservationIgnored
    private var displayLinkTarget: DisplayLinkTarget?

    init(width: Double, reducedMotion: Bool, autoPlay: Bool) {
        let geometry = GlassLockKeyGeometry(width: width)
        self.geometry = geometry
        self.autoPlay = autoPlay
        state = GlassLockKeySimulationState(
            geometry: geometry,
            reduced: reducedMotion,
            autoPlay: autoPlay
        )
    }

    func start() {
        guard displayLink == nil else { return }
        let target = DisplayLinkTarget { [weak self] link in
            self?.displayLinkDidFire(link)
        }
        displayLinkTarget = target
        let link = CADisplayLink(target: target, selector: #selector(DisplayLinkTarget.fire(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        displayLink = link
        previousTimestamp = nil
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        displayLinkTarget = nil
        previousTimestamp = nil
    }

    func update(width: Double, reducedMotion: Bool, autoPlay: Bool) {
        let nextGeometry = GlassLockKeyGeometry(width: width)
        if nextGeometry != geometry {
            if geometry.s <= 0 {
                state = GlassLockKeySimulationState(
                    geometry: nextGeometry,
                    reduced: reducedMotion,
                    autoPlay: autoPlay
                )
                returningToRest = false
                shouldResumeAfterTouch = false
                resumeWaitsForTerminalMode = false
            } else {
                let ratio = nextGeometry.s / geometry.s
                state.kx *= ratio
                state.ky *= ratio
                state.kvx *= ratio
                state.kvy *= ratio
                state.gx *= ratio
                state.gy *= ratio
                state.fx *= ratio
                state.fy *= ratio
                state.x0 *= ratio
            }
            geometry = nextGeometry
        }
        state.reduced = reducedMotion
        if autoPlay != self.autoPlay {
            self.autoPlay = autoPlay
            if autoPlay && !state.demoOn && !state.touching {
                resetForLoopStart()
            } else if !autoPlay {
                state.demoOn = false
            }
        }
    }

    func send(_ input: GlassLockKeyInput) {
        apply(input)
        publishAndPlayHaptics()
    }

    func command(_ action: GlassLockKeyCommand) {
        send(.command(action))
    }

    /// Deterministic entry point used by `CADisplayLink` and pure parity checks.
    func advance(by frameDuration: Double) {
        guard frameDuration.isFinite, frameDuration > 0 else { return }
        state.acc = min(
            state.acc + frameDuration * GlassLockKeyConstants.gQASpeed,
            GlassLockKeyConstants.step * Double(GlassLockKeyConstants.maxSteps)
        )
        while state.acc >= GlassLockKeyConstants.step {
            fixedStep()
            state.acc -= GlassLockKeyConstants.step
        }
        publishAndPlayHaptics()
    }

    private func apply(_ input: GlassLockKeyInput) {
        switch input {
        case let .command(action, scripted):
            if !scripted {
                state.demoOn = false
                state.quiet = 0
                returningToRest = false
                shouldResumeAfterTouch = autoPlay
                resumeWaitsForTerminalMode = true
            }
            if action == .lock && state.mode == .open {
                setMode(.closing)
            } else if action == .unlock && state.mode == .idle {
                state.auto = 0
            }

        case let .down(point, scripted):
            state.scripted = scripted
            state.touching = true
            if !scripted {
                state.demoOn = false
                state.auto = -1
                state.quiet = 0
                returningToRest = false
                shouldResumeAfterTouch = false
                resumeWaitsForTerminalMode = false
            }
            state.fx = point.x
            state.fy = point.y
            state.grab = .none

            let motion = GlassLockKeyConstants.UpgradeMotion.self
            if (state.mode == .idle || state.mode == .carrying)
                && abs(point.x - state.kx) < motion.keyHitW * geometry.s
                && abs(point.y - state.ky) < motion.keyHitH * geometry.s {
                state.grab = .key
                setMode(.carrying)
                state.gx = state.kx - point.x
                state.gy = state.ky - point.y
                emit(.grab)
            } else if state.mode == .seated
                && hypot(point.x - geometry.ringX, point.y - geometry.ringY) < motion.ringHit * geometry.s {
                state.grab = .ring
                state.x0 = point.x
                state.th0 = state.th
                state.strain = 0
            }

        case let .move(point):
            state.fx = point.x
            state.fy = point.y

        case let .up(velocity):
            if state.grab == .key {
                state.grab = .none
                let distance = hypot(state.kx - geometry.plugX, state.ky - geometry.plugY)
                let toward = (geometry.plugX - state.kx) * velocity.dx
                    + (geometry.plugY - state.ky) * velocity.dy
                let speed = hypot(velocity.dx, velocity.dy)
                let motion = GlassLockKeyConstants.UpgradeMotion.self
                if distance < motion.magnet * geometry.s
                    || (toward > 0
                        && speed > motion.flick * geometry.s
                        && distance < motion.magnet * 2.2 * geometry.s) {
                    seat()
                } else {
                    setMode(.idle)
                    state.kvx = velocity.dx
                    state.kvy = velocity.dy
                }
            } else if state.grab == .ring {
                state.grab = .none
                if state.strain < -0.05 && !state.reduced {
                    state.shake = 0
                    emit(.wrong)
                }
            }
            let wasScripted = state.scripted
            state.scripted = false
            state.touching = false
            if !wasScripted {
                state.quiet = 0
                shouldResumeAfterTouch = autoPlay
                resumeWaitsForTerminalMode = false
            }
        }
    }

    private func fixedStep() {
        let dt = GlassLockKeyConstants.step

        if state.demoOn {
            let t0 = state.demoT
            let t1 = t0 + dt
            runDemo(from: t0, to: t1)
            state.demoOn = true
            if t1 * 1_000 + 0.000_001 >= Double(GlassLockKeyConstants.upgradeLoopMS) {
                // RN resets only the loose key here, leaving a small materialisation residue. Resetting every
                // visual field makes the required 12-second recording seam exactly equal to the first frame.
                resetForLoopStart()
                return
            }
            state.demoT = t1
        } else if state.auto >= 0 {
            let t0 = state.auto + GlassLockKeyConstants.UpgradeScript.carry[0]
            runDemo(from: t0, to: t0 + dt)
            state.auto += dt
            if t0 > GlassLockKeyConstants.UpgradeScript.turn[1] + 0.1 {
                state.auto = -1
            }
        } else if shouldResumeAfterTouch && !state.touching {
            let terminal = state.mode == .idle || state.mode == .open
            if !resumeWaitsForTerminalMode || terminal {
                resumeWaitsForTerminalMode = false
                state.quiet += dt
                if state.quiet >= 2, !returningToRest {
                    beginReturnToRest()
                }
            }
        } else if autoPlay && state.grab == .none && !state.touching {
            // Retained RN behavior for non-pointer interruptions. The source constant stays 3.5 seconds.
            state.quiet += dt
            if state.quiet * 1_000 >= Double(GlassLockKeyConstants.resumeMS)
                && (state.mode == .idle || state.mode == .open) {
                state.demoOn = true
                state.demoT = state.mode == .open
                    ? GlassLockKeyConstants.UpgradeScript.resumeOpen
                    : 0
            }
        }

        state.modeT += dt
        updateKey(dt: dt)
        updateTurn(dt: dt)
        updateShackleAndSheet(dt: dt)
        updateEffects(dt: dt)
        state.clock += dt

        if returningToRest && isAtRest {
            resetForLoopStart()
        }
    }

    private func updateKey(dt: Double) {
        let motion = GlassLockKeyConstants.UpgradeMotion.self
        if state.mode == .carrying && state.grab == .key {
            var targetX = state.fx + state.gx
            var targetY = state.fy + state.gy
            let distance = hypot(targetX - geometry.plugX, targetY - geometry.plugY)
            let radius = motion.magnet * geometry.s
            if distance < radius && !state.reduced {
                let pull = motion.magnetPull * pow(1 - distance / radius, 0.6)
                targetX += (geometry.plugX - targetX) * pull
                targetY += (geometry.plugY - targetY) * pull
            }
            state.kvx = (targetX - state.kx) / dt
            state.kvy = (targetY - state.ky) / dt
            state.kx = targetX
            state.ky = targetY
            if hypot(targetX - geometry.plugX, targetY - geometry.plugY) < motion.seat * geometry.s {
                seat()
            }
        } else if state.mode == .idle {
            (state.kx, state.kvx) = spring(
                state.kx,
                state.kvx,
                toward: geometry.restX,
                stiffness: motion.home.stiffness,
                damping: motion.home.damping,
                dt: dt
            )
            (state.ky, state.kvy) = spring(
                state.ky,
                state.kvy,
                toward: geometry.restY,
                stiffness: motion.home.stiffness,
                damping: motion.home.damping,
                dt: dt
            )
        } else if state.mode == .inserting
            && state.modeT >= (state.reduced ? dt : motion.insert) {
            setMode(.seated)
        }

        state.lift += ((state.grab == .key ? 1 : 0) - state.lift) * (1 - exp(-14 * dt))
    }

    private func updateTurn(dt: Double) {
        let motion = GlassLockKeyConstants.UpgradeMotion.self
        if state.mode == .seated {
            if state.grab == .ring {
                let raw = state.th0 + (state.fx - state.x0) / (motion.turnPx * geometry.s)
                if raw < 0 {
                    state.strain = raw
                    state.th = -motion.wrong * (1 - exp(raw / motion.wrong))
                } else {
                    state.th = raw
                }
                if state.th >= 1 {
                    openLock()
                }
                if state.fy - geometry.ringY > motion.pullOut * geometry.s && state.th < 0.05 {
                    setMode(.carrying)
                    state.grab = .key
                    state.th = 0
                    state.kx = geometry.plugX
                    state.ky = geometry.plugY
                    state.gx = state.kx - state.fx
                    state.gy = state.ky - state.fy
                }
            } else {
                (state.th, state.tv) = spring(
                    state.th,
                    state.tv,
                    toward: 0,
                    stiffness: motion.back.stiffness,
                    damping: motion.back.damping,
                    dt: dt
                )
            }
        } else if state.mode == .closing, state.sheet < 0.02, state.shackle < 0.05 {
            (state.th, state.tv) = spring(
                state.th,
                state.tv,
                toward: 0,
                stiffness: motion.back.stiffness,
                damping: motion.back.damping,
                dt: dt
            )
            if abs(state.th) < 0.02 {
                state.th = 0
                state.tv = 0
                setMode(.idle)
                state.kx = geometry.plugX
                state.ky = geometry.plugY + motion.outPull * geometry.s
                state.kvy = motion.outSpeed * geometry.s
            }
        }

        var reachedDetents = 0
        for detent in motion.detents where state.th >= detent - 0.0001 {
            reachedDetents += 1
        }
        if reachedDetents > state.detent {
            emit(.detent)
            if !state.reduced { state.tick = 0 }
        }
        state.detent = reachedDetents
    }

    private func updateShackleAndSheet(dt: Double) {
        let motion = GlassLockKeyConstants.UpgradeMotion.self
        let target = state.mode == .open
            || (state.mode == .closing && state.sheet > motion.shackleAfter) ? 1.0 : 0.0
        if state.reduced {
            state.shackle = target
            state.shackleV = 0
        } else {
            (state.shackle, state.shackleV) = spring(
                state.shackle,
                state.shackleV,
                toward: target,
                stiffness: target == 1 ? motion.shackle.stiffness : motion.shackle.closeStiffness,
                damping: target == 1 ? motion.shackle.damping : motion.shackle.closeDamping,
                dt: dt
            )
        }

        let sheetTarget = state.mode == .open ? 1.0 : 0.0
        let delay = state.mode == .open ? motion.sheetDelay : 0
        if state.modeT >= delay {
            let rate = sheetTarget == 1 ? motion.sheetIn : motion.sheetOut
            state.sheet += (sheetTarget - state.sheet) * (1 - exp(-rate * dt))
        }
    }

    private func updateEffects(dt: Double) {
        let motion = GlassLockKeyConstants.UpgradeMotion.self
        let resting = state.mode == .idle && state.grab == .none
        state.idleT = resting ? state.idleT + dt : 0
        let showTarget: Double
        if resting && state.idleT > motion.show.hold {
            showTarget = 0
        } else if resting && state.show < 0.01 {
            showTarget = 0
        } else {
            showTarget = 1
        }
        let showRate = showTarget == 1 ? motion.show.inRate : motion.show.outRate
        state.show += (showTarget - state.show) * (1 - exp(-showRate * dt))

        advanceEffect(&state.glint, dt: dt, duration: motion.glint)
        advanceEffect(&state.shake, dt: dt, duration: motion.shake.duration)
        advanceEffect(&state.tick, dt: dt, duration: motion.tick.duration)
    }

    private func runDemo(from t0: Double, to t1: Double) {
        let script = GlassLockKeyConstants.UpgradeScript.self
        let motion = GlassLockKeyConstants.UpgradeMotion.self
        let carry = script.carry
        let fromX = geometry.restX
        let fromY = geometry.restY + motion.grabBelow * geometry.s

        if t0 <= carry[0], carry[0] < t1 {
            apply(.down(CGPoint(x: fromX, y: fromY), scripted: true))
        }
        if t1 > carry[0], t0 < carry[1], state.grab == .key {
            let progress = cosineEase((min(t1, carry[1]) - carry[0]) / (carry[1] - carry[0]))
            let targetX = geometry.plugX + 6 * geometry.s
            let targetY = geometry.plugY + (motion.grabBelow + 10) * geometry.s
            apply(.move(CGPoint(
                x: fromX + (targetX - fromX) * progress,
                y: fromY + (targetY - fromY) * progress
            )))
        }
        if t0 < carry[1], carry[1] <= t1 {
            apply(.up(velocity: .zero))
        }

        let turn = script.turn
        if t0 <= turn[0], turn[0] < t1 {
            apply(.down(CGPoint(x: geometry.ringX, y: geometry.ringY), scripted: true))
        }
        if t1 > turn[0], t0 < turn[1], state.grab == .ring {
            let progress = cosineEase((min(t1, turn[1]) - turn[0]) / (turn[1] - turn[0]))
            apply(.move(CGPoint(
                x: geometry.ringX + script.turnBy * geometry.s * progress,
                y: geometry.ringY
            )))
        }
        if t0 < turn[1], turn[1] <= t1 {
            apply(.up(velocity: .zero))
        }
        if t0 <= script.lockAt, script.lockAt < t1 {
            apply(.command(.lock, scripted: true))
        }
    }

    private func setMode(_ mode: GlassLockKeyMode) {
        state.mode = mode
        state.modeT = 0
    }

    private func seat() {
        setMode(.inserting)
        state.grab = .none
        state.kx = geometry.plugX
        state.ky = geometry.plugY
        emit(.seat)
    }

    private func openLock() {
        setMode(.open)
        state.grab = .none
        state.th = 1
        state.tv = 0
        if !state.reduced {
            state.shackleV += GlassLockKeyConstants.UpgradeMotion.shackleKick
        }
        state.glint = 0
        emit(.open)
    }

    private func beginReturnToRest() {
        returningToRest = true
        state.grab = .none
        state.scripted = false
        state.auto = -1
        state.demoOn = false
        state.detent = 0
        state.strain = 0
        state.glint = -1
        state.shake = -1
        state.tick = -1
        state.idleT = GlassLockKeyConstants.UpgradeMotion.show.hold
        if state.mode == .inserting
            || state.mode == .seated
            || state.mode == .open
            || state.mode == .closing {
            setMode(.closing)
        } else {
            setMode(.idle)
        }
    }

    private var isAtRest: Bool {
        state.mode == .idle
            && abs(state.kx - geometry.restX) < 0.5
            && abs(state.ky - geometry.restY) < 0.5
            && abs(state.kvx) < 2
            && abs(state.kvy) < 2
            && abs(state.th) < 0.01
            && abs(state.shackle) < 0.01
            && state.sheet < 0.01
            && state.show < 0.01
            && state.lift < 0.01
    }

    private func resetForLoopStart() {
        let remainder = state.acc
        state = GlassLockKeySimulationState(
            geometry: geometry,
            reduced: state.reduced,
            autoPlay: autoPlay
        )
        state.acc = remainder
        returningToRest = false
        shouldResumeAfterTouch = false
        resumeWaitsForTerminalMode = false
    }

    private func emit(_ haptic: GlassLockKeyHaptic) {
        if !state.scripted && !state.demoOn && state.auto < 0 {
            state.events.append(haptic)
        }
    }

    private func publishAndPlayHaptics() {
        let events = state.events
        state.events.removeAll(keepingCapacity: true)
        events.forEach(playHaptic)
    }

    private func playHaptic(_ haptic: GlassLockKeyHaptic) {
        switch haptic {
        case .grab:
            UISelectionFeedbackGenerator().selectionChanged()
        case .seat:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .detent:
            UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        case .open:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .wrong:
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
    }

    private func displayLinkDidFire(_ link: CADisplayLink) {
        defer { previousTimestamp = link.timestamp }
        guard let previousTimestamp else { return }
        advance(by: link.timestamp - previousTimestamp)
    }

    private func spring(
        _ value: Double,
        _ velocity: Double,
        toward target: Double,
        stiffness: Double,
        damping: Double,
        dt: Double
    ) -> (Double, Double) {
        let nextVelocity = velocity + (-stiffness * (value - target) - damping * velocity) * dt
        return (value + nextVelocity * dt, nextVelocity)
    }

    private func advanceEffect(_ value: inout Double, dt: Double, duration: Double) {
        guard value >= 0 else { return }
        value = value + dt > duration ? -1 : value + dt
    }

    private func cosineEase(_ value: Double) -> Double {
        (1 - cos(.pi * clamp(value))) / 2
    }

    private func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        min(max(value, lower), upper)
    }

    private final class DisplayLinkTarget: NSObject {
        let action: (CADisplayLink) -> Void

        init(action: @escaping (CADisplayLink) -> Void) {
            self.action = action
        }

        @objc func fire(_ link: CADisplayLink) {
            action(link)
        }
    }
}
