import Foundation

struct GumballCrankGeometry: Equatable, Sendable {
    let scale: Double
    let knobX: Double
    let knobY: Double
    let doorX: Double
    let doorY: Double
}

enum GumballCrankPhase {
    static let ready = 0
    static let dispensing = 1
    static let reward = 2
    static let collecting = 3
}

enum GumballCrankInput: Equatable, Sendable {
    enum Action: Equatable, Sendable {
        case turn
        case collect
    }

    case down(x: Double, y: Double, scripted: Bool = false)
    case move(x: Double, y: Double)
    case up
    case command(Action)
}

/// A direct value-type port of `crank-sim.ts`. JavaScript number fields stay
/// `Double`; discrete counters and event identifiers use `Int`.
struct GumballCrankState: Equatable, Sendable {
    var acc: Double
    var clock: Double
    var phase: Int
    var phaseT: Double
    var angle: Double
    var turned: Double
    var detent: Int
    var w: Double
    var wob: Double
    var wobV: Double
    var back: Double
    var strain: Double
    var press: Double
    var grab: Bool
    var scripted: Bool
    var prevA: Double
    var lastT: Double
    var jx: Double
    var jxv: Double
    var jy: Double
    var jyv: Double
    var coins: Double
    var bump: Double
    var px: [Double]
    var py: [Double]
    var pvx: [Double]
    var pvy: [Double]
    var prot: [Double]
    var pcol: [Int]
    var demoOn: Bool
    var demoT: Double
    var quiet: Double
    var autoTurn: Double
    /// Swift-only bridge for the native gallery's return-to-rest transition.
    var resumeT: Double
    var resumeFromAngle: Double
    var reduced: Bool
    var events: [Int]

    var resumeProgress: Double {
        min(1, resumeT / GumballCrankSimulation.resumeEase)
    }
}

enum GumballCrankSimulation {
    private static var resumeDelay: Double { GumballCrankMotion.resumeMS / 1_000 }
    static var resumeEase: Double { GumballCrankMotion.dynamics.resumeEase }

    static func initialState(reduced: Bool, autoPlay: Bool) -> GumballCrankState {
        GumballCrankState(
            acc: 0,
            clock: 0,
            phase: GumballCrankPhase.ready,
            phaseT: 0,
            angle: 0,
            turned: 0,
            detent: 0,
            w: 0,
            wob: 0,
            wobV: 0,
            back: 0,
            strain: 0,
            press: 0,
            grab: false,
            scripted: false,
            prevA: 0,
            lastT: 0,
            jx: 0,
            jxv: 0,
            jy: 0,
            jyv: 0,
            coins: GumballCrankMotion.coins.from,
            bump: 0,
            px: [],
            py: [],
            pvx: [],
            pvy: [],
            prot: [],
            pcol: [],
            demoOn: autoPlay && !reduced,
            demoT: 0,
            quiet: 0,
            autoTurn: -1,
            resumeT: 0,
            resumeFromAngle: 0,
            reduced: reduced,
            events: []
        )
    }

    static func geometry(width: Double) -> GumballCrankGeometry {
        let scale = width / GumballCrankTheme.stageWidth
        let knob = GumballCrankTheme.replicaArt.knob
        let centre = knob.size / 2
        let exit = GumballCrankTheme.replicaArt.exit
        return GumballCrankGeometry(
            scale: scale,
            knobX: (knob.x + centre) * scale,
            knobY: (knob.y + centre) * scale,
            doorX: exit.x * scale,
            doorY: exit.y * scale
        )
    }

    static func input(
        _ state: inout GumballCrankState,
        geometry: GumballCrankGeometry,
        event: GumballCrankInput
    ) {
        switch event {
        case let .command(action):
            state.demoOn = false
            state.quiet = 0
            state.resumeT = 0
            switch action {
            case .turn where state.phase == GumballCrankPhase.ready:
                if state.reduced {
                    setPhase(&state, GumballCrankPhase.dispensing)
                } else {
                    state.autoTurn = 0
                }
            case .collect where state.phase == GumballCrankPhase.reward:
                setPhase(&state, GumballCrankPhase.collecting)
                emit(&state, GumballCrankMotion.haptic.collect)
            default:
                break
            }

        case let .down(x, y, scripted):
            state.scripted = scripted
            if !scripted {
                state.demoOn = false
                state.autoTurn = -1
                state.quiet = 0
                state.resumeT = 0
            }
            guard state.phase == GumballCrankPhase.ready else { return }
            let distance = hypot(x - geometry.knobX, y - geometry.knobY)
            guard distance < GumballCrankMotion.knob.grab * geometry.scale else { return }
            state.grab = true
            state.w = 0
            state.prevA = atan2(y - geometry.knobY, x - geometry.knobX)
            state.lastT = state.clock
            emit(&state, GumballCrankMotion.haptic.grab)

        case let .move(x, y):
            guard state.grab else { return }
            if !state.scripted { state.quiet = 0 }
            let angle = atan2(y - geometry.knobY, x - geometry.knobX)
            var delta = angle - state.prevA
            if delta > .pi { delta -= 2 * .pi }
            if delta < -.pi { delta += 2 * .pi }
            state.prevA = angle
            let deltaTime = max(GumballCrankMotion.dynamics.velocitySampleFloor, state.clock - state.lastT)
            state.lastT = state.clock

            if delta > 0, state.strain >= 0 {
                state.w = state.w * GumballCrankMotion.dynamics.velocityOldWeight
                    + delta / deltaTime * GumballCrankMotion.dynamics.velocityNewWeight
                advance(&state, by: delta)
            } else if delta > 0 {
                state.strain = min(0, state.strain + delta)
                state.back = rubberBand(state.strain)
            } else {
                let previousBack = state.back
                state.strain = min(0, state.strain + delta)
                state.back = rubberBand(state.strain)
                if previousBack > GumballCrankMotion.dynamics.ratchetHapticThreshold,
                   state.back <= GumballCrankMotion.dynamics.ratchetHapticThreshold {
                    emit(&state, GumballCrankMotion.haptic.ratchet)
                }
            }

        case .up:
            let wasScripted = state.scripted
            if state.grab {
                state.grab = false
                state.w = min(GumballCrankMotion.knob.maxSpin, max(0, state.w))
                if state.turned > .pi, state.w > GumballCrankMotion.knob.commitSpin {
                    let remaining = GumballCrankMotion.crank.turn - state.turned
                    state.w = max(
                        state.w,
                        remaining / GumballCrankMotion.knob.finish + GumballCrankMotion.knob.friction
                    )
                }
            }
            state.back = 0
            state.strain = 0
            state.scripted = false
            if !wasScripted { state.quiet = 0 }
        }
    }

    static func step(
        _ state: inout GumballCrankState,
        geometry: GumballCrankGeometry,
        frameDelta: Double,
        autoPlay: Bool
    ) {
        var accumulator = min(
            state.acc + min(GumballCrankMotion.dynamics.maximumFrameDelta, max(0, frameDelta)),
            GumballCrankMotion.step * Double(GumballCrankMotion.maxSteps)
        )
        while accumulator >= GumballCrankMotion.step {
            fixedStep(&state, geometry: geometry, autoPlay: autoPlay)
            accumulator -= GumballCrankMotion.step
        }
        state.acc = accumulator
    }

    private static func emit(_ state: inout GumballCrankState, _ event: Int) {
        if !state.scripted, !state.demoOn, state.autoTurn < 0 {
            state.events.append(event)
        }
    }

    private static func setPhase(_ state: inout GumballCrankState, _ phase: Int) {
        state.phase = phase
        state.phaseT = 0
    }

    private static func rubberBand(_ strain: Double) -> Double {
        let maximum = GumballCrankMotion.knob.backMax
        return -maximum * (1 - exp(strain / maximum))
    }

    private static func burst(_ state: inout GumballCrankState, geometry: GumballCrankGeometry) {
        state.px.removeAll(keepingCapacity: true)
        state.py.removeAll(keepingCapacity: true)
        state.pvx.removeAll(keepingCapacity: true)
        state.pvy.removeAll(keepingCapacity: true)
        state.prot.removeAll(keepingCapacity: true)
        state.pcol.removeAll(keepingCapacity: true)

        for index in 0..<GumballCrankMotion.confetti.count {
            let dynamics = GumballCrankMotion.dynamics
            let r1 = sin(Double(index) * dynamics.confettiSeedA) * dynamics.confettiSeedAMultiplier
            let r2 = sin(Double(index) * dynamics.confettiSeedB) * dynamics.confettiSeedBMultiplier
            let u1 = r1 - floor(r1)
            let u2 = r2 - floor(r2)
            let angle = -.pi / 2 + (u1 - 0.5) * GumballCrankMotion.confetti.spread * 2
            let velocity = GumballCrankMotion.confetti.speed
                * (dynamics.confettiMinimumSpeed + dynamics.confettiSpeedRange * u2)
                * geometry.scale
            state.px.append(geometry.doorX)
            state.py.append(geometry.doorY)
            state.pvx.append(cos(angle) * velocity)
            state.pvy.append(sin(angle) * velocity)
            state.prot.append(u2 * dynamics.confettiInitialRotation)
            state.pcol.append(index % 5)
        }
    }

    private static func advance(_ state: inout GumballCrankState, by delta: Double) {
        state.angle += delta
        state.turned += delta
        let detent = Int(floor(
            state.turned / GumballCrankMotion.crank.detent + GumballCrankMotion.dynamics.detentEpsilon
        ))
        if detent > state.detent {
            state.detent = detent
            if !state.reduced {
                state.wobV += GumballCrankMotion.crank.overshoot.kick
                state.jxv += (detent.isMultiple(of: 2) ? 1 : -1)
                    * GumballCrankMotion.dynamics.jostleKickX
                state.jyv -= GumballCrankMotion.dynamics.jostleKickY
            }
            emit(&state, GumballCrankMotion.haptic.detent)
        }
        if state.turned >= GumballCrankMotion.crank.turn - GumballCrankMotion.dynamics.detentEpsilon {
            state.turned = 0
            state.detent = 0
            state.w = 0
            state.grab = false
            state.angle = (state.angle / GumballCrankMotion.crank.turn).rounded()
                * GumballCrankMotion.crank.turn
            setPhase(&state, GumballCrankPhase.dispensing)
            emit(&state, GumballCrankMotion.haptic.dispense)
        }
    }

    private static func scriptedTurn(
        _ state: inout GumballCrankState,
        geometry: GumballCrankGeometry,
        from time0: Double,
        to time1: Double,
        startingAt start: Double
    ) {
        let end = start + GumballCrankMotion.upScript.turn[1] - GumballCrankMotion.upScript.turn[0]
        func point(at time: Double) -> (x: Double, y: Double) {
            let unit = min(1, max(0, (time - start) / (end - start)))
            let eased = (1 - cos(.pi * unit)) / 2
            let angle = -.pi / 2 + GumballCrankMotion.upScript.angle * eased
            return (
                geometry.knobX + cos(angle) * GumballCrankMotion.upScript.radius * geometry.scale,
                geometry.knobY + sin(angle) * GumballCrankMotion.upScript.radius * geometry.scale
            )
        }

        if time0 <= start, start < time1 {
            let position = point(at: start)
            input(
                &state,
                geometry: geometry,
                event: .down(x: position.x, y: position.y, scripted: true)
            )
        }
        if time1 > start, time0 < end, state.grab {
            let position = point(at: min(time1, end))
            input(&state, geometry: geometry, event: .move(x: position.x, y: position.y))
        }
        if time0 < end, end <= time1 {
            input(&state, geometry: geometry, event: .up)
        }
    }

    private static func fixedStep(
        _ state: inout GumballCrankState,
        geometry: GumballCrankGeometry,
        autoPlay: Bool
    ) {
        let delta = GumballCrankMotion.step
        if state.demoOn {
            let time0 = state.demoT
            let time1 = time0 + delta
            scriptedTurn(
                &state,
                geometry: geometry,
                from: time0,
                to: time1,
                startingAt: GumballCrankMotion.upScript.turn[0]
            )
            let collectAt = GumballCrankMotion.upScript.collectAt
            if time0 <= collectAt, collectAt < time1 {
                input(&state, geometry: geometry, event: .command(.collect))
            }
            state.demoOn = true
            let loopSeconds = GumballCrankMotion.upLoopMS / 1_000
            if time1 >= loopSeconds - GumballCrankMotion.dynamics.loopEpsilon {
                let reduced = state.reduced
                state = initialState(reduced: reduced, autoPlay: autoPlay)
                return
            }
            state.demoT = time1
        } else if state.autoTurn >= 0 {
            scriptedTurn(
                &state,
                geometry: geometry,
                from: state.autoTurn,
                to: state.autoTurn + delta,
                startingAt: 0
            )
            state.autoTurn += delta
            let turnDuration = GumballCrankMotion.upScript.turn[1] - GumballCrankMotion.upScript.turn[0]
            if state.autoTurn > turnDuration + GumballCrankMotion.dynamics.autoTurnTail {
                state.autoTurn = -1
            }
        }

        if !state.grab, state.w > 0, state.phase == GumballCrankPhase.ready {
            advance(&state, by: state.w * delta)
            state.w = max(0, state.w - GumballCrankMotion.knob.friction * state.w * delta)
            if state.w < GumballCrankMotion.knob.catchSpin {
                state.w = 0
            }
        }

        let overshoot = GumballCrankMotion.crank.overshoot
        state.wobV += (-overshoot.stiffness * state.wob - overshoot.damping * state.wobV) * delta
        state.wob += state.wobV * delta
        if !state.grab { state.back *= exp(-GumballCrankMotion.dynamics.backDecay * delta) }
        state.press += ((state.grab ? 1 : 0) - state.press)
            * (1 - exp(-GumballCrankMotion.dynamics.pressResponse * delta))

        state.jxv += (-GumballCrankMotion.dynamics.jostleStiffness * state.jx
            - GumballCrankMotion.dynamics.jostleDamping * state.jxv) * delta
        state.jx += state.jxv * delta
        state.jyv += (-GumballCrankMotion.dynamics.jostleStiffness * state.jy
            - GumballCrankMotion.dynamics.jostleDamping * state.jyv) * delta
        state.jy += state.jyv * delta

        let phaseBeforeStep = state.phaseT
        state.phaseT += delta
        if state.phase == GumballCrankPhase.dispensing {
            if phaseBeforeStep < GumballCrankMotion.upT.confetti,
               state.phaseT >= GumballCrankMotion.upT.confetti,
               !state.reduced {
                burst(&state, geometry: geometry)
            }
            if state.phaseT >= GumballCrankMotion.upT.done {
                setPhase(&state, GumballCrankPhase.reward)
                state.coins -= GumballCrankMotion.coins.add
                emit(&state, GumballCrankMotion.haptic.open)
            }
        } else if state.phase == GumballCrankPhase.collecting,
                  state.phaseT >= GumballCrankMotion.upT.collect + GumballCrankMotion.upT.back {
            setPhase(&state, GumballCrankPhase.ready)
        }

        if state.phase == GumballCrankPhase.collecting,
           phaseBeforeStep < GumballCrankMotion.upT.collect,
           state.phaseT >= GumballCrankMotion.upT.collect {
            state.coins += GumballCrankMotion.coins.add
            state.bump = 1
        }
        state.bump = max(0, state.bump - delta * GumballCrankMotion.dynamics.bumpDecay)

        for index in state.px.indices {
            state.pvy[index] += GumballCrankMotion.confetti.gravity * geometry.scale * delta
            state.px[index] += state.pvx[index] * delta
            state.py[index] += state.pvy[index] * delta
            state.prot[index] += (index.isMultiple(of: 2) ? 1 : -1)
                * GumballCrankMotion.confetti.spin * delta
        }
        if !state.px.isEmpty,
           state.phase != GumballCrankPhase.dispensing,
           state.phaseT > GumballCrankMotion.dynamics.confettiClearDelay {
            state.px.removeAll(keepingCapacity: true)
            state.py.removeAll(keepingCapacity: true)
            state.pvx.removeAll(keepingCapacity: true)
            state.pvy.removeAll(keepingCapacity: true)
            state.prot.removeAll(keepingCapacity: true)
            state.pcol.removeAll(keepingCapacity: true)
        }
        state.clock += delta
        if autoPlay, !state.demoOn, !state.reduced {
            resumeAutoplayIfNeeded(&state)
        }
    }

    /// After two seconds without manual input, ease the active presentation
    /// back to rest for 350 ms, then restart the deterministic autoplay loop.
    private static func resumeAutoplayIfNeeded(_ state: inout GumballCrankState) {
        guard !state.grab else {
            state.resumeT = 0
            return
        }
        state.quiet += GumballCrankMotion.step
        guard state.quiet >= resumeDelay else { return }
        if state.resumeT == 0 {
            state.resumeFromAngle = state.angle
        }
        state.resumeT += GumballCrankMotion.step
        let unit = min(1, state.resumeT / resumeEase)
        let eased = 1 - pow(1 - unit, 3)
        state.angle = state.resumeFromAngle * (1 - eased)
        state.turned = max(0, state.angle.truncatingRemainder(dividingBy: GumballCrankMotion.crank.turn))
        state.detent = Int(floor(
            state.turned / GumballCrankMotion.crank.detent + GumballCrankMotion.dynamics.detentEpsilon
        ))
        state.wob *= 1 - eased
        state.wobV *= 1 - eased
        state.back *= 1 - eased
        state.strain *= 1 - eased
        state.press *= 1 - eased
        state.jx *= 1 - eased
        state.jxv *= 1 - eased
        state.jy *= 1 - eased
        state.jyv *= 1 - eased
        guard unit >= 1 else { return }

        let reduced = state.reduced
        state = initialState(reduced: reduced, autoPlay: true)
    }
}
