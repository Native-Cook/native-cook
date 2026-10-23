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
    case bow
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
    let width: Double
    let height: Double
    let scale: Double
    let faceX: Double
    let lockTop: Double
    let lockBottom: Double
    let plugX: Double
    let plugY: Double
    let restX: Double
    let restY: Double
    let seatY: Double

    init(width: Double, height: Double) {
        self.width = width
        self.height = height
        scale = width / GlassLockKeyConstants.stageWidth
        lockTop = height * GlassLockKeyConstants.Lock.topRatio
        lockBottom = lockTop + GlassLockKeyConstants.Lock.height * scale
        plugY = lockBottom + GlassLockKeyConstants.Lock.plugHeight * scale
        faceX = width / 2 - GlassLockKeyConstants.Lock.face * scale / 2
        plugX = width / 2
        restX = width / 2
        restY = height * GlassLockKeyConstants.Key.restRatio
        seatY = plugY - GlassLockKeyConstants.Key.blade * scale
    }
}

struct GlassLockKeySimulationState: Equatable {
    var accumulator = 0.0
    var clock = 0.0
    var mode = GlassLockKeyMode.idle
    var keyX: Double
    var keyY: Double
    var keyVelocityX = 0.0
    var keyVelocityY = 0.0
    var lift = 0.0
    var insertionTime = 1.0
    var insertionFrom: Double
    var insertionTo: Double
    var insertionDuration = 1.0
    var turn = 0.0
    var turnVelocity = 0.0
    var detent = 0
    var shackle = 0.0
    var shackleVelocity = 0.0
    var bounce = 0.0
    var bounceVelocity = 0.0
    var shakeTime = -1.0
    var glintTime = -1.0
    var sheet = 0.0
    var sheetTime = 1.0
    var sheetFrom = 0.0
    var sheetTo = 0.0
    var sheetDuration = 1.0
    var sheetDelay = 0.0
    var grab = GlassLockKeyGrab.none
    var scripted = false
    var grabOffsetX = 0.0
    var grabOffsetY = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var turnStartX = 0.0
    var turnStart = 0.0
    var wrongHint = false
    var demoOn: Bool
    var demoTime = 0.0
    var quiet = 0.0
    var autoUnlock = -1.0
    var reduced: Bool
    var events: [GlassLockKeyHaptic] = []
    var humanTouching = false
    var resumePending = false

    var isOpen: Bool { mode == .open }

    init(geometry: GlassLockKeyGeometry, reduced: Bool, autoPlay: Bool) {
        keyX = geometry.restX
        keyY = geometry.restY
        insertionFrom = geometry.plugY
        insertionTo = geometry.plugY
        demoOn = autoPlay && !reduced
        self.reduced = reduced
    }
}

@MainActor
@Observable
final class GlassLockKeySimulation {
    private(set) var state: GlassLockKeySimulationState
    private(set) var geometry: GlassLockKeyGeometry

    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var displayLink: CADisplayLink?
    @ObservationIgnored private var displayLinkTarget: GlassLockKeyDisplayLinkTarget?
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?

    init(width: Double, height: Double, reducedMotion: Bool, autoPlay: Bool) {
        let geometry = GlassLockKeyGeometry(width: width, height: height)
        self.geometry = geometry
        self.autoPlay = autoPlay
        state = GlassLockKeySimulationState(geometry: geometry, reduced: reducedMotion, autoPlay: autoPlay)
    }

    func start() {
        guard displayLink == nil else { return }
        let target = GlassLockKeyDisplayLinkTarget { [weak self] link in self?.displayLinkDidFire(link) }
        displayLinkTarget = target
        let link = CADisplayLink(target: target, selector: #selector(GlassLockKeyDisplayLinkTarget.fire(_:)))
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

    func update(width: Double, height: Double, reducedMotion: Bool, autoPlay: Bool) {
        let nextGeometry = GlassLockKeyGeometry(width: width, height: height)
        if nextGeometry != geometry {
            geometry = nextGeometry
            state = GlassLockKeySimulationState(geometry: nextGeometry, reduced: reducedMotion, autoPlay: autoPlay)
        }
        self.autoPlay = autoPlay
        if reducedMotion != state.reduced {
            state.reduced = reducedMotion
            state.demoOn = autoPlay && !reducedMotion
            state.demoTime = 0
            state.resumePending = false
        } else if !autoPlay {
            state.demoOn = false
            state.resumePending = false
        }
    }

    func send(_ input: GlassLockKeyInput) {
        apply(input)
        publishHaptics()
    }

    func command(_ command: GlassLockKeyCommand) {
        send(.command(command))
    }

    func advance(by frameDuration: Double) {
        guard frameDuration.isFinite, frameDuration > 0 else { return }
        state.accumulator = min(
            state.accumulator + frameDuration,
            GlassLockKeyConstants.step * Double(GlassLockKeyConstants.maxSteps)
        )
        while state.accumulator >= GlassLockKeyConstants.step {
            fixedStep()
            state.accumulator -= GlassLockKeyConstants.step
        }
        publishHaptics()
    }

    private func apply(_ input: GlassLockKeyInput) {
        switch input {
        case let .command(action, scripted):
            if !scripted {
                stopDemoForHumanInput()
                state.resumePending = autoPlay && !state.reduced
            }
            if action == .lock, state.mode == .open {
                state.mode = .closing
                startSheet(to: 0, delay: 0)
            } else if action == .unlock, state.mode == .idle {
                state.autoUnlock = 0
            }

        case let .down(point, scripted):
            state.scripted = scripted
            state.humanTouching = !scripted
            if !scripted {
                stopDemoForHumanInput()
                state.resumePending = false
            }
            state.fingerX = point.x
            state.fingerY = point.y
            state.grab = .none
            let bowY = state.keyY + (GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring / 2) * geometry.scale
            if state.mode == .idle || state.mode == .carrying {
                let withinX = abs(point.x - state.keyX) < GlassLockKeyConstants.Key.hit / 2 * geometry.scale
                let withinY = point.y > state.keyY - 20 * geometry.scale
                    && point.y < bowY + (GlassLockKeyConstants.Key.ring / 2 + 16) * geometry.scale
                if withinX && withinY {
                    state.grab = .key
                    state.mode = .carrying
                    state.grabOffsetX = state.keyX - point.x
                    state.grabOffsetY = state.keyY - point.y
                    emit(.grab)
                }
            } else if state.mode == .seated {
                let hitRadius = (GlassLockKeyConstants.Key.hit / 2 + 12) * geometry.scale
                if hypot(point.x - state.keyX, point.y - bowY) < hitRadius {
                    state.grab = .bow
                    state.turnStartX = point.x
                    state.grabOffsetY = point.y
                    state.turnStart = state.turn
                }
            }

        case let .move(point):
            state.fingerX = point.x
            state.fingerY = point.y
            if state.humanTouching { state.quiet = 0 }

        case let .up(velocity):
            if state.grab == .key {
                state.grab = .none
                let distance = hypot(state.keyX - geometry.plugX, state.keyY - geometry.plugY)
                if distance < GlassLockKeyConstants.Carry.magnet * geometry.scale {
                    seat()
                } else {
                    state.mode = .idle
                    state.keyVelocityX = velocity.dx
                    state.keyVelocityY = velocity.dy
                }
            } else if state.grab == .bow {
                state.grab = .none
                if state.turn < -0.1 && !state.reduced {
                    state.shakeTime = 0
                    emit(.wrong)
                }
                if state.turn < -0.1 { state.wrongHint = true }
            }
            let wasHuman = state.humanTouching
            state.scripted = false
            state.humanTouching = false
            if wasHuman {
                state.quiet = 0
                state.resumePending = autoPlay && !state.reduced
            }
        }
    }

    private func stopDemoForHumanInput() {
        state.demoOn = false
        state.autoUnlock = -1
        state.quiet = 0
    }

    private func fixedStep() {
        let dt = GlassLockKeyConstants.step
        if state.demoOn {
            let start = state.demoTime
            var end = start + dt
            runDemo(from: start, to: end)
            if end * 1_000 >= GlassLockKeyConstants.loopMS {
                end -= GlassLockKeyConstants.loopMS / 1_000
                if state.mode == .idle, hypot(state.keyX - geometry.restX, state.keyY - geometry.restY) < 0.5 {
                    state.keyX = geometry.restX
                    state.keyY = geometry.restY
                    state.keyVelocityX = 0
                    state.keyVelocityY = 0
                }
            }
            state.demoTime = end
        } else if state.autoUnlock >= 0 {
            let start = state.autoUnlock + GlassLockKeyConstants.Script.carry[0]
            runDemo(from: start, to: start + dt)
            state.autoUnlock += dt
            if start > GlassLockKeyConstants.Script.turn[1] + 0.1 { state.autoUnlock = -1 }
        } else if state.resumePending, autoPlay, !state.humanTouching, state.grab == .none {
            state.quiet += dt
            if state.quiet * 1_000 >= GlassLockKeyConstants.resumeMS {
                if state.mode == .idle || state.mode == .open {
                    state.demoOn = true
                    state.demoTime = state.mode == .open ? GlassLockKeyConstants.Script.resumeOpen : 0
                    state.resumePending = false
                } else if state.mode != .closing {
                    state.mode = .closing
                    startSheet(to: 0, delay: 0)
                }
            }
        }

        updateKey(dt: dt)
        updateTurn(dt: dt)
        updateShackleAndBody(dt: dt)
        updateSheet(dt: dt)
        state.clock += dt
    }

    private func updateKey(dt: Double) {
        if state.mode == .carrying, state.grab == .key {
            var targetX = state.fingerX + state.grabOffsetX
            var targetY = state.fingerY + state.grabOffsetY
            let distance = hypot(targetX - geometry.plugX, targetY - geometry.plugY)
            let magnetRadius = GlassLockKeyConstants.Carry.magnet * geometry.scale
            if distance < magnetRadius, !state.reduced {
                let pull = GlassLockKeyConstants.Carry.magnetPull * pow(1 - distance / magnetRadius, 0.6)
                targetX += (geometry.plugX - targetX) * pull
                targetY += (geometry.plugY - targetY) * pull
            }
            state.keyVelocityX = (targetX - state.keyX) / dt
            state.keyVelocityY = (targetY - state.keyY) / dt
            state.keyX = targetX
            state.keyY = targetY
            if hypot(targetX - geometry.plugX, targetY - geometry.plugY) < GlassLockKeyConstants.Carry.seat * geometry.scale {
                seat()
            }
        } else if state.mode == .inserting {
            state.insertionTime = min(state.insertionDuration, state.insertionTime + dt)
            let progress = easeOutCubic(state.insertionTime / state.insertionDuration)
            state.keyY = state.insertionFrom + (state.insertionTo - state.insertionFrom) * progress
            state.keyX = geometry.plugX
            if state.insertionTime >= state.insertionDuration {
                if state.insertionTo == geometry.seatY {
                    state.mode = .seated
                } else {
                    state.mode = .idle
                    state.keyVelocityY = 0
                }
            }
        } else if state.mode == .idle {
            (state.keyX, state.keyVelocityX) = spring(
                state.keyX, state.keyVelocityX, toward: geometry.restX,
                stiffness: GlassLockKeyConstants.Carry.homeStiffness,
                damping: GlassLockKeyConstants.Carry.homeDamping, dt: dt
            )
            (state.keyY, state.keyVelocityY) = spring(
                state.keyY, state.keyVelocityY, toward: geometry.restY,
                stiffness: GlassLockKeyConstants.Carry.homeStiffness,
                damping: GlassLockKeyConstants.Carry.homeDamping, dt: dt
            )
        }
        let targetLift = state.grab == .key ? 1.0 : 0.0
        state.lift += (targetLift - state.lift) * (1 - exp(-14 * dt))
    }

    private func updateTurn(dt: Double) {
        if state.mode == .seated {
            if state.grab == .bow {
                var turn = state.turnStart + (state.fingerX - state.turnStartX) / (GlassLockKeyConstants.Turn.radius * geometry.scale)
                if turn < 0 { turn = -rubber(-turn, limit: GlassLockKeyConstants.Turn.wrong) }
                state.turnVelocity = (turn - state.turn) / dt
                state.turn = turn
                if state.fingerY - state.grabOffsetY > GlassLockKeyConstants.Carry.pullOut * geometry.scale, abs(state.turn) < 0.1 {
                    state.grab = .key
                    state.mode = .carrying
                    state.grabOffsetX = state.keyX - state.fingerX
                    state.grabOffsetY = state.keyY - state.fingerY
                    state.turn = 0
                    state.turnVelocity = 0
                }
                if state.turn >= GlassLockKeyConstants.Turn.open { openLock() }
            } else {
                (state.turn, state.turnVelocity) = spring(
                    state.turn, state.turnVelocity, toward: 0,
                    stiffness: GlassLockKeyConstants.Turn.backStiffness,
                    damping: GlassLockKeyConstants.Turn.backDamping, dt: dt
                )
            }
        } else if state.mode == .closing, state.shackle < 0.06 {
            (state.turn, state.turnVelocity) = spring(
                state.turn, state.turnVelocity, toward: 0,
                stiffness: GlassLockKeyConstants.Turn.closeStiffness,
                damping: GlassLockKeyConstants.Turn.closeDamping, dt: dt
            )
            if state.sheet < 0.02, abs(state.turn) < 0.02, state.shackle < 0.02 {
                state.turn = 0
                state.turnVelocity = 0
                unseat()
            }
        }

        if state.turn > 0.3, state.mode == .seated { state.wrongHint = false }
        var detents = 0
        for threshold in GlassLockKeyConstants.Turn.detents where state.turn >= threshold - 0.0001 { detents += 1 }
        if detents != state.detent {
            if detents > state.detent {
                state.bounceVelocity += GlassLockKeyConstants.Bounce.detentKick
                emit(.detent)
            }
            state.detent = detents
        }
    }

    private func updateShackleAndBody(dt: Double) {
        let up = state.mode == .open
            || (state.mode == .closing && state.sheet > GlassLockKeyConstants.SheetMotion.shackleAfter) ? 1.0 : 0.0
        if state.reduced {
            state.shackle += (up - state.shackle) * (1 - exp(-18 * dt))
            state.shackleVelocity = 0
        } else {
            (state.shackle, state.shackleVelocity) = spring(
                state.shackle, state.shackleVelocity, toward: up,
                stiffness: up == 1 ? GlassLockKeyConstants.Shackle.stiffness : GlassLockKeyConstants.Shackle.closeStiffness,
                damping: up == 1 ? GlassLockKeyConstants.Shackle.damping : GlassLockKeyConstants.Shackle.closeDamping,
                dt: dt
            )
        }

        if state.reduced {
            state.bounce = 0
            state.bounceVelocity = 0
        } else {
            (state.bounce, state.bounceVelocity) = spring(
                state.bounce, state.bounceVelocity, toward: 0,
                stiffness: GlassLockKeyConstants.Bounce.stiffness,
                damping: GlassLockKeyConstants.Bounce.damping, dt: dt
            )
        }
        if state.shakeTime >= 0 {
            state.shakeTime += dt
            if state.shakeTime > GlassLockKeyConstants.Shake.duration { state.shakeTime = -1 }
        }
        if state.glintTime >= 0 {
            state.glintTime += dt
            if state.glintTime > GlassLockKeyConstants.Glint.duration { state.glintTime = -1 }
        }
    }

    private func updateSheet(dt: Double) {
        guard state.sheetTime < state.sheetDuration + state.sheetDelay else { return }
        state.sheetTime += dt
        let progress = clamp((state.sheetTime - state.sheetDelay) / state.sheetDuration)
        let eased: Double
        if state.reduced {
            eased = progress
        } else if state.sheetTo == 1 {
            eased = 1 - pow(1 - progress, 4)
        } else {
            eased = progress * progress * (3 - 2 * progress)
        }
        state.sheet = state.sheetFrom + (state.sheetTo - state.sheetFrom) * eased
    }

    private func runDemo(from start: Double, to end: Double) {
        let carry = GlassLockKeyConstants.Script.carry
        if start <= carry[0], carry[0] < end {
            apply(.down(CGPoint(x: geometry.restX, y: geometry.restY + 40 * geometry.scale), scripted: true))
        }
        if end > carry[0], start < carry[1], state.grab == .key {
            let progress = cosineEase((min(end, carry[1]) - carry[0]) / (carry[1] - carry[0]))
            apply(.move(CGPoint(
                x: geometry.restX + GlassLockKeyConstants.Script.carryTo[0] * geometry.scale * progress,
                y: geometry.restY + 40 * geometry.scale + GlassLockKeyConstants.Script.carryTo[1] * geometry.scale * progress
            )))
        }
        if start < carry[1], carry[1] <= end, state.grab == .key { apply(.up(velocity: .zero)) }

        let bowY = geometry.seatY + (GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring / 2) * geometry.scale
        let turn = GlassLockKeyConstants.Script.turn
        if start <= turn[0], turn[0] < end {
            apply(.down(CGPoint(x: geometry.plugX - 8 * geometry.scale, y: bowY), scripted: true))
        }
        if end > turn[0], start < turn[1], state.grab == .bow {
            let progress = cosineEase((min(end, turn[1]) - turn[0]) / (turn[1] - turn[0]))
            apply(.move(CGPoint(
                x: geometry.plugX - 8 * geometry.scale + GlassLockKeyConstants.Script.turnBy * geometry.scale * progress,
                y: bowY
            )))
        }
        if start < turn[1], turn[1] <= end, state.grab == .bow { apply(.up(velocity: .zero)) }
        if start <= GlassLockKeyConstants.Script.lockAt, GlassLockKeyConstants.Script.lockAt < end {
            apply(.command(.lock, scripted: true))
        }
    }

    private func seat() {
        state.mode = .inserting
        state.grab = .none
        state.keyX = geometry.plugX
        state.insertionFrom = geometry.plugY
        state.insertionTo = geometry.seatY
        state.insertionTime = 0
        state.insertionDuration = state.reduced ? GlassLockKeyConstants.step : GlassLockKeyConstants.Insert.duration
        emit(.seat)
    }

    private func unseat() {
        state.mode = .inserting
        state.insertionFrom = state.keyY
        state.insertionTo = geometry.plugY + 2 * geometry.scale
        state.insertionTime = 0
        state.insertionDuration = state.reduced ? GlassLockKeyConstants.step : GlassLockKeyConstants.Insert.outDuration
    }

    private func openLock() {
        state.mode = .open
        state.grab = .none
        state.turn = GlassLockKeyConstants.Turn.open
        state.turnVelocity = 0
        state.bounceVelocity += GlassLockKeyConstants.Bounce.openKick
        state.shackleVelocity += state.reduced ? 0 : 4
        state.glintTime = 0
        startSheet(to: 1, delay: state.reduced ? 0 : GlassLockKeyConstants.SheetMotion.delay)
        emit(.open)
    }

    private func startSheet(to target: Double, delay: Double) {
        state.sheetFrom = state.sheet
        state.sheetTo = target
        state.sheetTime = 0
        state.sheetDelay = delay
        let baseDuration = state.reduced
            ? GlassLockKeyConstants.SheetMotion.reducedFade
            : target == 1 ? GlassLockKeyConstants.SheetMotion.open : GlassLockKeyConstants.SheetMotion.close
        state.sheetDuration = max(0.05, baseDuration * abs(target - state.sheet))
    }

    private func emit(_ haptic: GlassLockKeyHaptic) {
        if !state.scripted, !state.demoOn, state.autoUnlock < 0 { state.events.append(haptic) }
    }

    private func publishHaptics() {
        let events = state.events
        state.events.removeAll(keepingCapacity: true)
        for event in events {
            switch event {
            case .grab: UISelectionFeedbackGenerator().selectionChanged()
            case .seat: UIImpactFeedbackGenerator(style: .light).impactOccurred()
            case .detent: UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            case .open: UINotificationFeedbackGenerator().notificationOccurred(.success)
            case .wrong: UINotificationFeedbackGenerator().notificationOccurred(.error)
            }
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

    private func rubber(_ over: Double, limit: Double) -> Double {
        limit * (1 - exp(-over / limit))
    }

    private func easeOutCubic(_ value: Double) -> Double {
        1 - pow(1 - clamp(value), 3)
    }

    private func cosineEase(_ value: Double) -> Double {
        (1 - cos(.pi * clamp(value))) / 2
    }

    private func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        min(max(value, lower), upper)
    }
}

@MainActor
private final class GlassLockKeyDisplayLinkTarget: NSObject {
    let action: (CADisplayLink) -> Void

    init(action: @escaping (CADisplayLink) -> Void) {
        self.action = action
    }

    @objc func fire(_ link: CADisplayLink) {
        action(link)
    }
}
