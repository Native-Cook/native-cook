import Foundation

// Native translation of the 480 Hz contact solver and scene controller.
enum ChromePrizeWheelMotion {
    // Darwin and V8 transcendental rounding differ at the last peg's contact discontinuity.
    // This 0.000057344 rad/s autoplay-only adjustment keeps the original box landing.
    static let nativeSecondOmega = -9.888076492781599

    struct ThrowSpec { let x, y, drag, duration, omega, admire: Double }
    static let H: Double = 0.0020833333333333333
    static let TAU: Double = 6.283185307179586
    struct MOTIONData {
        let out: [Double] = [0.23, 1, 0.32, 1]
        let travel: [Double] = [0.77, 0, 0.175, 1]
        let holdSpring: [Double] = [400, 30]
        let liftSpring: [Double] = [260, 17]
        let wheelSpring: [Double] = [180, 22]
        let heroSpring: [Double] = [150, 13]
        let squashSpring: [Double] = [300, 12]
        let toySpring: [Double] = [220, 16]
        let sinkSpring: [Double] = [220, 24]
        let swallowSpring: [Double] = [300, 14]
        let restockSpring: [Double] = [320, 16]
        let hubSpring: [Double] = [400, 30]
        let heldScale: Double = 0.985
        let revealScale: Double = 0.94
        let tiltGain: Double = 0.07
        let wobbleBase: Double = 0.012
        let wobbleSpeed: Double = 0.02
        let wobblePhase: Double = 0.6
        let environmentPeriod: Double = 7
        let environmentAmount: Double = 0.1
        let swirlGain: Double = 0.05
        let swirlDrag: Double = 0.8
        let hapticGap: Double = 0.045
        let tickDecay: Double = 0.09
        let hubPress: Double = 0.12
        let hubSpeed: Double = 17
        let hubVariation: Double = 1.5
        let hintSpinning: Double = 0.35
        let hintDuration: Double = 0.2
        let nudgeDuration: Double = 0.36
        let heroPull: Double = 0.6
        let heroTiltGain: Double = 0.00025
        let heroTiltCap: Double = 0.2
        let fingerFade: Double = 0.15
        let takeoverDelay: Double = 3.5
        let homeDuration: Double = 0.5
        let reducedFade: Double = 0.16
        let reducedReveal: Double = 0.24
        let reducedCollect: Double = 0.24
        let hubHitScale: Double = 1.25
        let velocityBlend: Double = 0.7
        let trackedBlend: Double = 0.35
        struct revealData {
            let glow: Double = 0.42
            let comet: Double = 0.52
            let dim: Double = 0.5
            let copy: Double = 0.24
            let crouch: Double = 0.11
            let launchAt: Double = 0.12
            let stretch: Double = 0.09
            let rays: Double = 0.6
            let badgeAt: Double = 0.28
            let badge: Double = 0.52
            let captionAt: Double = 0.52
            let caption: Double = 0.3
            let countBase: Double = 0.3
            let countPerPrize: Double = 0.012
            let countCap: Double = 0.9
            let buttonAt: Double = 0.58
            let button: Double = 0.38
        }
        let reveal = revealData()
        struct collectingData {
            let press: Double = 0.11
            let flight: Double = 0.46
            let caption: Double = 0.18
            let rays: Double = 0.24
            let copy: Double = 0.24
            let sinkAt: Double = 0.3
            let glow: Double = 0.4
            let dim: Double = 0.45
            let swallowAt: Double = 0.4
            let swallow: Double = 0.09
            let restockAt: Double = 0.46
            let buttonAt: Double = 0.52
            let button: Double = 0.34
            let done: Double = 0.9
        }
        let collecting = collectingData()
    }
    static let MOTION = MOTIONData()
    struct DEMOData {
        let periodSteps: Double = 10265
        let rest: Double = 0.9
        let fingerWait: Double = 0.27
        let first = ThrowSpec(
            x: 0.62, y: -0.42, drag: 1.2217304763960306, duration: 0.3, omega: 16.17000000000024, admire: 3.4)
        let between: Double = 0.7
        // Source launch parameters remain available alongside the native calibration.
        let second = ThrowSpec(
            x: -0.55, y: 0.5, drag: -0.8726646259971648, duration: 0.32, omega: -9.888019148781598, admire: 2.4)
        let press: Double = 0.14
        let tail: Double = 0.8
    }
    static let DEMO = DEMOData()
    struct PEGSData {
        let count: Double = 18
        let phase: Double = 0.17453292519943295
        let spacing: Double = 0.3490658503988659
        let radius: Double = 0.905
        let size: Double = 0.026
    }
    static let PEGS = PEGSData()
    struct POINTERData {
        let angle: Double = 3.141592653589793
        let pivot: Double = 1.12
        let length: Double = 0.25
        let halfBase: Double = 0.095
    }
    static let POINTER = POINTERData()
    struct PROFILEData {
        let bandInner: Double = 0.955
        let ringInner: Double = 0.855
        let faceOuter: Double = 0.825
        let wedgeInner: Double = 0.3
        let channel: Double = 0.036
        let wedgeCorner: Double = 0.045
        let hub: Double = 0.215
        let hubDisc: Double = 0.168
    }
    static let PROFILE = PROFILEData()
    struct PHASEData {
        let idle: Double = 0
        let held: Double = 1
        let spinning: Double = 2
        let landed: Double = 3
        let reveal: Double = 4
        let collecting: Double = 5
    }
    static let PHASE = PHASEData()
    static let SEGMENT_ANGLE: Double = 1.0471975511965976
    static let SEGMENTS: Double = 6
    static let JACKPOT_INDEX: Double = 1
    struct MOTION_EVENTData {
        let light: Double = 1
        let rigid: Double = 2
        let medium: Double = 4
        let success: Double = 8
    }
    static let MOTION_EVENT = MOTION_EVENTData()
    static let PHYSICS = Physics()
    struct Prize {
        let key: String
        let count: Double
        let label: String
    }
    static let PRIZES = [
        Prize(key: "plate", count: 5, label: "A plate of donuts"),
        Prize(key: "chest", count: 50, label: "The treasure chest"),
        Prize(key: "tray", count: 15, label: "A baker's tray"),
        Prize(key: "box", count: 10, label: "A box of ten"),
        Prize(key: "coffee", count: 1, label: "A coffee on us"),
        Prize(key: "stack", count: 3, label: "A triple stack"),
    ]
    final class Sim {
        var theta: Double
        var omega: Double
        var swing: Double
        var swingVelocity: Double
        var swayA: Double
        var swayB: Double
        var swayVelocityA: Double
        var swayVelocityB: Double
        var contact: Double
        var lastStrike: Double
        var side: Double
        var steps: Double
        var accumulator: Double
        var strikes: Double
        var strikeStrength: Double
        var travel: Double
        var still: Double
        var dissipated: Double
        var numericalLoss: Double
        init(
            theta: Double = 0.0, omega: Double = 0.0, swing: Double = 0.0, swingVelocity: Double = 0.0,
            swayA: Double = 0.0, swayB: Double = 0.0, swayVelocityA: Double = 0.0, swayVelocityB: Double = 0.0,
            contact: Double = -1.0, lastStrike: Double = 1000000000.0, side: Double = 0.0, steps: Double = 0.0,
            accumulator: Double = 0.0, strikes: Double = 0.0, strikeStrength: Double = 0.0, travel: Double = 0.0,
            still: Double = 0.0, dissipated: Double = 0.0, numericalLoss: Double = 0.0
        ) {
            self.theta = theta
            self.omega = omega
            self.swing = swing
            self.swingVelocity = swingVelocity
            self.swayA = swayA
            self.swayB = swayB
            self.swayVelocityA = swayVelocityA
            self.swayVelocityB = swayVelocityB
            self.contact = contact
            self.lastStrike = lastStrike
            self.side = side
            self.steps = steps
            self.accumulator = accumulator
            self.strikes = strikes
            self.strikeStrength = strikeStrength
            self.travel = travel
            self.still = still
            self.dissipated = dissipated
            self.numericalLoss = numericalLoss
        }
    }
    final class Cam {
        var active: Bool
        var beta: Double
        var gamma: Double
        var g: Double
        var slope: Double
        var curvature: Double
        init(
            active: Bool = false, beta: Double = 0.0, gamma: Double = 0.0, g: Double = 0.0, slope: Double = 0.0,
            curvature: Double = 0.0
        ) {
            self.active = active
            self.beta = beta
            self.gamma = gamma
            self.g = g
            self.slope = slope
            self.curvature = curvature
        }
    }
    final class Values {
        var theta: Double
        var omega: Double
        var tiltX: Double
        var tiltY: Double
        var envTurn: Double
        var swayA: Double
        var swayB: Double
        var winner: Double
        var lift: Double
        var glow: Double
        var winnerPrize: Double
        var winnerPrizeScale: Double
        var dim: Double
        var comet: Double
        var hubPress: Double
        var phase: Double
        var swing: Double
        var tick: Double
        var strikes: Double
        var wheelScale: Double
        var wheelOpacity: Double
        var swirl: Double
        var hero: Double
        var heroSquash: Double
        var heroX: Double
        var heroY: Double
        var heroTilt: Double
        var rays: Double
        var burst: Double
        var burstPower: Double
        var badge: Double
        var caption: Double
        var count: Double
        var button: Double
        var buttonPress: Double
        var collect: Double
        var copySwap: Double
        var hint: Double
        var hintNudge: Double
        var touchX: Double
        var touchY: Double
        var touchOn: Double
        init(
            theta: Double = 0.0, omega: Double = 0.0, tiltX: Double = 0.0, tiltY: Double = 0.0, envTurn: Double = 0.0,
            swayA: Double = 0.0, swayB: Double = 0.0, winner: Double = -1.0, lift: Double = 0.0, glow: Double = 0.0,
            winnerPrize: Double = 1.0, winnerPrizeScale: Double = 1.0, dim: Double = 0.0, comet: Double = 0.0,
            hubPress: Double = 0.0, phase: Double = 0.0, swing: Double = 0.0, tick: Double = 0.0, strikes: Double = 0.0,
            wheelScale: Double = 1.0, wheelOpacity: Double = 1.0, swirl: Double = 0.0, hero: Double = 0.0,
            heroSquash: Double = 0.0, heroX: Double = 0.0, heroY: Double = 0.0, heroTilt: Double = 0.0,
            rays: Double = 0.0, burst: Double = -1.0, burstPower: Double = 0.6, badge: Double = 0.0,
            caption: Double = 0.0, count: Double = 0.0, button: Double = 0.0, buttonPress: Double = 0.0,
            collect: Double = 0.0, copySwap: Double = 0.0, hint: Double = 1.0, hintNudge: Double = 0.0,
            touchX: Double = 0.0, touchY: Double = 0.0, touchOn: Double = 0.0
        ) {
            self.theta = theta
            self.omega = omega
            self.tiltX = tiltX
            self.tiltY = tiltY
            self.envTurn = envTurn
            self.swayA = swayA
            self.swayB = swayB
            self.winner = winner
            self.lift = lift
            self.glow = glow
            self.winnerPrize = winnerPrize
            self.winnerPrizeScale = winnerPrizeScale
            self.dim = dim
            self.comet = comet
            self.hubPress = hubPress
            self.phase = phase
            self.swing = swing
            self.tick = tick
            self.strikes = strikes
            self.wheelScale = wheelScale
            self.wheelOpacity = wheelOpacity
            self.swirl = swirl
            self.hero = hero
            self.heroSquash = heroSquash
            self.heroX = heroX
            self.heroY = heroY
            self.heroTilt = heroTilt
            self.rays = rays
            self.burst = burst
            self.burstPower = burstPower
            self.badge = badge
            self.caption = caption
            self.count = count
            self.button = button
            self.buttonPress = buttonPress
            self.collect = collect
            self.copySwap = copySwap
            self.hint = hint
            self.hintNudge = hintNudge
            self.touchX = touchX
            self.touchY = touchY
            self.touchOn = touchOn
        }
        subscript(key: String) -> Double {
            get {
                switch key {
                case "theta": return theta
                case "omega": return omega
                case "tiltX": return tiltX
                case "tiltY": return tiltY
                case "envTurn": return envTurn
                case "swayA": return swayA
                case "swayB": return swayB
                case "winner": return winner
                case "lift": return lift
                case "glow": return glow
                case "winnerPrize": return winnerPrize
                case "winnerPrizeScale": return winnerPrizeScale
                case "dim": return dim
                case "comet": return comet
                case "hubPress": return hubPress
                case "phase": return phase
                case "swing": return swing
                case "tick": return tick
                case "strikes": return strikes
                case "wheelScale": return wheelScale
                case "wheelOpacity": return wheelOpacity
                case "swirl": return swirl
                case "hero": return hero
                case "heroSquash": return heroSquash
                case "heroX": return heroX
                case "heroY": return heroY
                case "heroTilt": return heroTilt
                case "rays": return rays
                case "burst": return burst
                case "burstPower": return burstPower
                case "badge": return badge
                case "caption": return caption
                case "count": return count
                case "button": return button
                case "buttonPress": return buttonPress
                case "collect": return collect
                case "copySwap": return copySwap
                case "hint": return hint
                case "hintNudge": return hintNudge
                case "touchX": return touchX
                case "touchY": return touchY
                case "touchOn": return touchOn
                default: preconditionFailure("Unknown wheel value")
                }
            }
            set {
                switch key {
                case "theta": theta = newValue
                case "omega": omega = newValue
                case "tiltX": tiltX = newValue
                case "tiltY": tiltY = newValue
                case "envTurn": envTurn = newValue
                case "swayA": swayA = newValue
                case "swayB": swayB = newValue
                case "winner": winner = newValue
                case "lift": lift = newValue
                case "glow": glow = newValue
                case "winnerPrize": winnerPrize = newValue
                case "winnerPrizeScale": winnerPrizeScale = newValue
                case "dim": dim = newValue
                case "comet": comet = newValue
                case "hubPress": hubPress = newValue
                case "phase": phase = newValue
                case "swing": swing = newValue
                case "tick": tick = newValue
                case "strikes": strikes = newValue
                case "wheelScale": wheelScale = newValue
                case "wheelOpacity": wheelOpacity = newValue
                case "swirl": swirl = newValue
                case "hero": hero = newValue
                case "heroSquash": heroSquash = newValue
                case "heroX": heroX = newValue
                case "heroY": heroY = newValue
                case "heroTilt": heroTilt = newValue
                case "rays": rays = newValue
                case "burst": burst = newValue
                case "burstPower": burstPower = newValue
                case "badge": badge = newValue
                case "caption": caption = newValue
                case "count": count = newValue
                case "button": button = newValue
                case "buttonPress": buttonPress = newValue
                case "collect": collect = newValue
                case "copySwap": copySwap = newValue
                case "hint": hint = newValue
                case "hintNudge": hintNudge = newValue
                case "touchX": touchX = newValue
                case "touchY": touchY = newValue
                case "touchOn": touchOn = newValue
                default: preconditionFailure("Unknown wheel value")
                }
            }
        }
    }
    final class Scene {
        var sim: Sim
        var values: Values
        var tweens: [Tween]
        var accumulator: Double
        var steps: Double
        var phaseStep: Double
        var releaseSpeed: Double
        var releaseTravel: Double
        var targetTheta: Double
        var tiltTargetX: Double
        var tiltTargetY: Double
        var tiltBaseX: Double
        var tiltBaseY: Double
        var tiltVX: Double
        var tiltVY: Double
        var touching: Bool
        var heroOriginX: Double
        var heroOriginY: Double
        var held: Bool
        var hubHeld: Bool
        var heroHeld: Bool
        var fingerAngle: Double
        var trackedRate: Double
        var moveStep: Double
        var events: Double
        var lastHaptic: Double
        var demoStage: Double
        var demoStep: Double
        var demoStartTheta: Double
        var takeover: Bool
        var lastTouch: Double
        var homeStep: Double
        var homeFrom: Double
        var homeTo: Double
        var reducedPick: Double
        var reducedStep: Double
        var loops: Double
        var loopStep: Double
        var lastPeriod: Double
        init(
            sim: Sim = Sim(), values: Values = Values(), tweens: [Tween] = [], accumulator: Double = 0.0,
            steps: Double = 0.0, phaseStep: Double = 0.0, releaseSpeed: Double = 0.0, releaseTravel: Double = 0.0,
            targetTheta: Double = 0.0, tiltTargetX: Double = 0.0, tiltTargetY: Double = 0.0, tiltBaseX: Double = 0.0,
            tiltBaseY: Double = 0.0, tiltVX: Double = 0.0, tiltVY: Double = 0.0, touching: Bool = false,
            heroOriginX: Double = 0.0, heroOriginY: Double = 0.0, held: Bool = false, hubHeld: Bool = false,
            heroHeld: Bool = false, fingerAngle: Double = 0.0, trackedRate: Double = 0.0, moveStep: Double = 0.0,
            events: Double = 0.0, lastHaptic: Double = -100.0, demoStage: Double = 0.0, demoStep: Double = 0.0,
            demoStartTheta: Double = 0.0, takeover: Bool = false, lastTouch: Double = 0.0, homeStep: Double = -1.0,
            homeFrom: Double = 0.0, homeTo: Double = 0.0, reducedPick: Double = -1.0, reducedStep: Double = -1.0,
            loops: Double = 0.0, loopStep: Double = 0.0, lastPeriod: Double = 0.0
        ) {
            self.sim = sim
            self.values = values
            self.tweens = tweens
            self.accumulator = accumulator
            self.steps = steps
            self.phaseStep = phaseStep
            self.releaseSpeed = releaseSpeed
            self.releaseTravel = releaseTravel
            self.targetTheta = targetTheta
            self.tiltTargetX = tiltTargetX
            self.tiltTargetY = tiltTargetY
            self.tiltBaseX = tiltBaseX
            self.tiltBaseY = tiltBaseY
            self.tiltVX = tiltVX
            self.tiltVY = tiltVY
            self.touching = touching
            self.heroOriginX = heroOriginX
            self.heroOriginY = heroOriginY
            self.held = held
            self.hubHeld = hubHeld
            self.heroHeld = heroHeld
            self.fingerAngle = fingerAngle
            self.trackedRate = trackedRate
            self.moveStep = moveStep
            self.events = events
            self.lastHaptic = lastHaptic
            self.demoStage = demoStage
            self.demoStep = demoStep
            self.demoStartTheta = demoStartTheta
            self.takeover = takeover
            self.lastTouch = lastTouch
            self.homeStep = homeStep
            self.homeFrom = homeFrom
            self.homeTo = homeTo
            self.reducedPick = reducedPick
            self.reducedStep = reducedStep
            self.loops = loops
            self.loopStep = loopStep
            self.lastPeriod = lastPeriod
        }
    }
    final class Tween {
        var key: String
        var from: Double
        var to: Double
        var elapsed: Double
        var duration: Double
        var k: Double
        var c: Double
        var velocity: Double
        var travel: Bool
        init(
            key: String = "", from: Double = 0.0, to: Double = 0.0, elapsed: Double = 0.0, duration: Double = 0.0,
            k: Double = 0.0, c: Double = 0.0, velocity: Double = 0.0, travel: Bool = false
        ) {
            self.key = key
            self.from = from
            self.to = to
            self.elapsed = elapsed
            self.duration = duration
            self.k = k
            self.c = c
            self.velocity = velocity
            self.travel = travel
        }
    }
    final class Config {
        var reduced: Bool
        var autoPlay: Bool
        var cx: Double
        var cy: Double
        var radius: Double
        var buttonX: Double
        var buttonY: Double
        init(
            reduced: Bool = false, autoPlay: Bool = true, cx: Double = 201.0, cy: Double = 424.0,
            radius: Double = 164.0, buttonX: Double = 201.0, buttonY: Double = 788.0
        ) {
            self.reduced = reduced
            self.autoPlay = autoPlay
            self.cx = cx
            self.cy = cy
            self.radius = radius
            self.buttonX = buttonX
            self.buttonY = buttonY
        }
    }
    final class Action {
        var type: String
        var x: Double
        var y: Double
        var vx: Double
        var vy: Double
        var cancelled: Bool
        var random: Double
        init(
            type: String = "", x: Double = 0.0, y: Double = 0.0, vx: Double = 0.0, vy: Double = 0.0,
            cancelled: Bool = false, random: Double = 0.5
        ) {
            self.type = type
            self.x = x
            self.y = y
            self.vx = vx
            self.vy = vy
            self.cancelled = cancelled
            self.random = random
        }
    }
    final class Physics {
        var stiffness: Double
        var inertia: Double
        var damping: Double
        var coulomb: Double
        var viscous: Double
        var restitution: Double
        var contactFrictionFraction: Double
        var speedCap: Double
        var countedSpeed: Double
        var countedTravel: Double
        var stillSpeed: Double
        var stillSwing: Double
        var stillPointerSpeed: Double
        var stillBeat: Double
        var derivativeStep: Double
        var swayFrequencyA: Double
        var swayFrequencyB: Double
        var swayDampingA: Double
        var swayDampingB: Double
        var swayGainA: Double
        var swayGainB: Double
        var swayLean: Double
        var swayLimit: Double
        init(
            stiffness: Double = 1.55, inertia: Double = 0.00048471553902970237, damping: Double = 0.01534961006708502,
            coulomb: Double = 1.15, viscous: Double = 0.025, restitution: Double = 0.15,
            contactFrictionFraction: Double = 0.15, speedCap: Double = 22.0, countedSpeed: Double = 5.0,
            countedTravel: Double = 6.283185307179586, stillSpeed: Double = 0.002, stillSwing: Double = 0.0008,
            stillPointerSpeed: Double = 0.02, stillBeat: Double = 0.18, derivativeStep: Double = 0.00001,
            swayFrequencyA: Double = 16.336281798666924, swayFrequencyB: Double = 24.504422698000386,
            swayDampingA: Double = 0.2, swayDampingB: Double = 0.25, swayGainA: Double = -0.045,
            swayGainB: Double = -0.035, swayLean: Double = -0.008, swayLimit: Double = 0.4
        ) {
            self.stiffness = stiffness
            self.inertia = inertia
            self.damping = damping
            self.coulomb = coulomb
            self.viscous = viscous
            self.restitution = restitution
            self.contactFrictionFraction = contactFrictionFraction
            self.speedCap = speedCap
            self.countedSpeed = countedSpeed
            self.countedTravel = countedTravel
            self.stillSpeed = stillSpeed
            self.stillSwing = stillSwing
            self.stillPointerSpeed = stillPointerSpeed
            self.stillBeat = stillBeat
            self.derivativeStep = derivativeStep
            self.swayFrequencyA = swayFrequencyA
            self.swayFrequencyB = swayFrequencyB
            self.swayDampingA = swayDampingA
            self.swayDampingB = swayDampingB
            self.swayGainA = swayGainA
            self.swayGainB = swayGainB
            self.swayLean = swayLean
            self.swayLimit = swayLimit
        }
    }
    static func sign(_ x: Double) -> Double { x == 0 ? 0 : (x > 0 ? 1 : -1) }
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }
    static func demoSteps(_ seconds: Double) -> Double { jsRound(seconds / H) }
    static func demoDrag(_ step: Double, _ duration: Double, _ angle: Double) -> Double {
        let u = clamp(step / demoSteps(duration), 0, 1)
        return angle * u * u * u
    }
    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
        return max(low, min(high, value))
    }
    static func wrap(_ angle: Double) -> Double {
        return angle - TAU * floor((angle + Double.pi) / TAU)
    }
    static func winningWedge(_ theta: Double) -> Double {
        var angle = ((POINTER.angle - theta)).truncatingRemainder(dividingBy: TAU)
        if angle < 0.0 {
            angle += TAU
        }
        return (floor((angle + SEGMENT_ANGLE / 2.0) / SEGMENT_ANGLE)).truncatingRemainder(dividingBy: SEGMENTS)
    }
    static func initialSim(_ theta: Double = 0.0, _ omega: Double = 0.0) -> Sim {
        return Sim(
            theta: theta, omega: omega, swing: 0.0, swingVelocity: 0.0, swayA: 0.0, swayB: 0.0, swayVelocityA: 0.0,
            swayVelocityB: 0.0, contact: -1.0, lastStrike: 1000000000.0, side: 0.0, steps: 0.0, accumulator: 0.0,
            strikes: 0.0, strikeStrength: 0.0, travel: 0.0, still: 0.0, dissipated: 0.0, numericalLoss: 0.0)
    }
    static func contactGamma(_ d: Double) -> Double {
        let l = POINTER.length
        let b = POINTER.halfBase
        let r = PEGS.size
        if d > l + r {
            return -1.0
        }
        let s = sqrt(l * l + b * b)
        let seam = sqrt(l * l + r * r + 2.0 * l * r * b / s)
        if d <= seam {
            return asin(clamp((b * l / s + r) / d, -1.0, 1.0)) - atan2(b, l)
        }
        return acos(clamp((l * l + d * d - r * r) / (2.0 * l * d), -1.0, 1.0))
    }
    static func camPosition(_ theta: Double, _ peg: Double, _ side: Double) -> Cam {
        let a = theta + PEGS.phase + peg * PEGS.spacing
        let x = POINTER.pivot + PEGS.radius * cos(a)
        let y = PEGS.radius * sin(a)
        let gamma = contactGamma(sqrt(x * x + y * y))
        let beta = atan2(y, x)
        return Cam(
            active: gamma >= 0.0, beta: beta, gamma: gamma, g: beta + side * max(0.0, gamma), slope: 0.0, curvature: 0.0
        )
    }
    static func camAt(_ theta: Double, _ peg: Double, _ side: Double) -> Cam {
        let c = camPosition(theta, peg, side)
        if !c.active {
            return c
        }
        let e = PHYSICS.derivativeStep
        let before = camPosition(theta - e, peg, side)
        let after = camPosition(theta + e, peg, side)
        if !before.active || !after.active {
            return Cam(active: false, beta: c.beta, gamma: c.gamma, g: c.g, slope: c.slope, curvature: c.curvature)
        }
        c.slope = (after.g - before.g) / (2.0 * e)
        c.curvature = (after.g - 2.0 * c.g + before.g) / (e * e)
        return c
    }
    static func simEnergy(_ s: Sim, _ physics: Physics? = nil) -> Double {
        let p = physics ?? PHYSICS
        return 0.5
            * (s.omega * s.omega + p.inertia * s.swingVelocity * s.swingVelocity + p.stiffness * s.swing * s.swing)
    }
    static func oscillator(_ x: Double, _ v: Double, _ k: Double, _ c: Double, _ dt: Double, _ force: Double = 0.0)
        -> [Double]
    {
        let vn =
            (v * (1.0 - c * dt / 2.0 - k * dt * dt / 4.0) - k * dt * x + dt * force)
            / (1.0 + c * dt / 2.0 + k * dt * dt / 4.0)
        return [x + dt * (v + vn) / 2.0, vn]
    }
    static func stepWheel(_ s: Sim, _ drivenTheta: Double? = nil, _ physics: Physics? = nil) {
        let p = physics ?? PHYSICS
        let oldTheta = s.theta
        let oldOmega = s.omega
        let oldSwing = s.swing
        let oldEnergy = simEnergy(s, p)
        s.strikeStrength = 0.0
        let driven = drivenTheta != nil
        var attached = false
        if s.contact >= 0.0 {
            let cam = camAt(s.theta, s.contact, s.side)
            if cam.active {
                let springTorque = -p.stiffness * cam.g * cam.slope
                let friction = min(p.coulomb, abs(springTorque) * p.contactFrictionFraction)
                let torque =
                    springTorque - p.damping * cam.slope * cam.slope * s.omega - p.inertia * cam.slope * cam.curvature
                    * s.omega * s.omega - ((abs(s.omega) > 0.000001 ? sign(s.omega) * friction : 0.0)) - p.viscous
                    * s.omega
                let acceleration = torque / (1.0 + p.inertia * cam.slope * cam.slope)
                let reaction =
                    p.inertia * (cam.slope * acceleration + cam.curvature * s.omega * s.omega) + p.damping * cam.slope
                    * s.omega + p.stiffness * cam.g
                if driven || s.side * reaction >= 0.0 {
                    let predictedOmega = clamp(s.omega + acceleration * H, -p.speedCap, p.speedCap)
                    let nextTheta = (driven ? drivenTheta! : s.theta + (s.omega + predictedOmega) * H / 2.0)
                    let next = camAt(nextTheta, s.contact, s.side)
                    if next.active {
                        s.theta = nextTheta
                        s.omega = (driven ? (nextTheta - oldTheta) / H : predictedOmega)
                        s.swing = next.g
                        s.swingVelocity = next.slope * s.omega
                        attached = true
                    }
                }
            }
            if !attached {
                s.contact = -1.0
            }
        }
        if !attached {
            let speed = max(0.0, (abs(s.omega) - p.coulomb * H) / (1.0 + p.viscous * H))
            s.omega = (driven ? (drivenTheta! - s.theta) / H : sign(s.omega) * speed)
            s.theta = (driven ? drivenTheta! : s.theta + (oldOmega + s.omega) * H / 2.0)
            let free = oscillator(s.swing, s.swingVelocity, p.stiffness / p.inertia, p.damping / p.inertia, H)
            s.swing = free[0]
            s.swingVelocity = free[1]
            let pegId = jsRound((Double.pi - s.theta - PEGS.phase) / PEGS.spacing)
            let peg = ((((pegId).truncatingRemainder(dividingBy: PEGS.count)) + PEGS.count)).truncatingRemainder(
                dividingBy: PEGS.count)
            let c = camPosition(s.theta, peg, 0.0)
            if c.active && abs(s.swing - c.beta) < c.gamma {
                let prev = camPosition(oldTheta, peg, 0.0)
                let side = (oldSwing < prev.beta ? -1.0 : 1.0)
                let boundary = camAt(s.theta, peg, side)
                if boundary.active {
                    let relative = s.swingVelocity - boundary.slope * s.omega
                    let impulse =
                        -(1.0 + p.restitution) * relative
                        / (1.0 / p.inertia + ((driven ? 0.0 : boundary.slope * boundary.slope)))
                    s.swingVelocity += impulse / p.inertia
                    if !driven {
                        s.omega -= impulse * boundary.slope
                    }
                    s.swing = boundary.g
                    s.contact = (abs(relative) < 2.0 || s.lastStrike == pegId ? peg : -1.0)
                    s.side = side
                    if s.contact >= 0.0 {
                        s.swingVelocity = boundary.slope * s.omega
                    }
                    if s.lastStrike != pegId {
                        s.strikes += 1.0
                        s.strikeStrength = abs(impulse)
                        s.lastStrike = pegId
                    }
                }
            }
        }
        s.omega = clamp(s.omega, -p.speedCap, p.speedCap)
        if !driven {
            let energy = simEnergy(s, p)
            if energy > oldEnergy {
                let potential = 0.5 * p.stiffness * s.swing * s.swing
                let kinetic = energy - potential
                if potential <= oldEnergy && kinetic > 0.0 {
                    let ratio = sqrt(max(0.0, (oldEnergy - potential) / kinetic))
                    s.omega *= ratio
                    s.swingVelocity *= ratio
                } else {
                    s.theta = oldTheta
                    s.swing = oldSwing
                    s.omega = -oldOmega * p.restitution
                    s.swingVelocity = (s.contact >= 0.0 ? camAt(s.theta, s.contact, s.side).slope * s.omega : 0.0)
                }
                s.numericalLoss += max(0.0, energy - simEnergy(s, p))
            }
            s.dissipated += max(0.0, oldEnergy - simEnergy(s, p))
        }
        let acceleration = (s.omega - oldOmega) / H
        let a = oscillator(
            s.swayA, s.swayVelocityA, pow(p.swayFrequencyA, 2.0), 2.0 * p.swayDampingA * p.swayFrequencyA, H,
            p.swayGainA * acceleration * pow(p.swayFrequencyA, 2.0))
        let b = oscillator(
            s.swayB, s.swayVelocityB, pow(p.swayFrequencyB, 2.0), 2.0 * p.swayDampingB * p.swayFrequencyB, H,
            p.swayGainB * acceleration * pow(p.swayFrequencyB, 2.0))
        s.swayA = clamp(a[0], -p.swayLimit, p.swayLimit)
        s.swayB = clamp(b[0], -p.swayLimit, p.swayLimit)
        s.swayVelocityA = (s.swayA == a[0] ? a[1] : 0.0)
        s.swayVelocityB = (s.swayB == b[0] ? b[1] : 0.0)
        s.travel += abs(s.theta - oldTheta)
        s.steps += 1.0
        let still =
            abs(s.omega) < p.stillSpeed && abs(s.swing) < p.stillSwing && abs(s.swingVelocity) < p.stillPointerSpeed
            && s.contact < 0.0
        s.still = (still ? s.still + H : 0.0)
    }
    static func advanceWheel(_ s: Sim, _ elapsed: Double, _ physics: Physics? = nil) {
        let p = physics ?? PHYSICS
        s.accumulator += max(0.0, elapsed)
        let steps = floor((s.accumulator + 1e-12) / H)
        s.accumulator -= steps * H
        var i: Double = 0.0
        while i < steps {
            stepWheel(s, nil, p)
            i += 1
        }
    }
    static func initialValues() -> Values {
        return Values(
            theta: 0.0, omega: 0.0, tiltX: 0.0, tiltY: 0.0, envTurn: 0.0, swayA: 0.0, swayB: 0.0, winner: -1.0,
            lift: 0.0, glow: 0.0, winnerPrize: 1.0, winnerPrizeScale: 1.0, dim: 0.0, comet: 0.0, hubPress: 0.0,
            phase: PHASE.idle, swing: 0.0, tick: 0.0, strikes: 0.0, wheelScale: 1.0, wheelOpacity: 1.0, swirl: 0.0,
            hero: 0.0, heroSquash: 0.0, heroX: 0.0, heroY: 0.0, heroTilt: 0.0, rays: 0.0, burst: -1.0, burstPower: 0.6,
            badge: 0.0, caption: 0.0, count: 0.0, button: 0.0, buttonPress: 0.0, collect: 0.0, copySwap: 0.0, hint: 1.0,
            hintNudge: 0.0, touchX: 0.0, touchY: 0.0, touchOn: 0.0)
    }
    static func initialScene() -> Scene {
        return Scene(
            sim: initialSim(), values: initialValues(), tweens: [], accumulator: 0.0, steps: 0.0, phaseStep: 0.0,
            releaseSpeed: 0.0, releaseTravel: 0.0, targetTheta: 0.0, tiltTargetX: 0.0, tiltTargetY: 0.0, tiltBaseX: 0.0,
            tiltBaseY: 0.0, tiltVX: 0.0, tiltVY: 0.0, touching: false, heroOriginX: 0.0, heroOriginY: 0.0, held: false,
            hubHeld: false, heroHeld: false, fingerAngle: 0.0, trackedRate: 0.0, moveStep: 0.0, events: 0.0,
            lastHaptic: -100.0, demoStage: 0.0, demoStep: 0.0, demoStartTheta: 0.0, takeover: false, lastTouch: 0.0,
            homeStep: -1.0, homeFrom: 0.0, homeTo: 0.0, reducedPick: -1.0, reducedStep: -1.0, loops: 0.0, loopStep: 0.0,
            lastPeriod: 0.0)
    }
    static func cubic(_ t: Double, _ curve: [Double]) -> Double {
        let x = clamp(t, 0.0, 1.0)
        var lo = 0.0
        var hi = 1.0
        var u = x
        var i: Double = 0.0
        while i < 16.0 {
            u = (lo + hi) / 2.0
            let v = 1.0 - u
            let bx = 3.0 * v * v * u * curve[0] + 3.0 * v * u * u * curve[2] + u * u * u
            if bx < x {
                lo = u
            } else {
                hi = u
            }
            i += 1
        }
        return
            (x <= 0.0
            ? 0.0
            : (x >= 1.0
                ? 1.0
                : 3.0 * (1.0 - u) * (1.0 - u) * u * curve[1] + 3.0 * (1.0 - u) * u * u * curve[3] + u * u
                    * u))
    }
    static func animateValue(
        _ s: Scene, _ key: String, _ to: Double, _ duration: Double, _ spring: [Double]? = nil, _ travel: Bool = false
    ) {
        var velocity = 0.0
        var i: Double = Double(s.tweens.count) - 1.0
        while i >= 0.0 {
            if s.tweens[Int(i)].key == key {
                velocity = s.tweens[Int(i)].velocity
                s.tweens.remove(at: Int(i))
            }
            i -= 1
        }
        s.tweens.append(
            Tween(
                key: key, from: s.values[key], to: to, elapsed: 0.0, duration: duration, k: spring?[0] ?? 0.0,
                c: spring?[1] ?? 0.0, velocity: velocity, travel: travel))
    }
    static func setValue(_ s: Scene, _ key: String, _ to: Double) {
        var i: Double = Double(s.tweens.count) - 1.0
        while i >= 0.0 {
            if s.tweens[Int(i)].key == key {
                s.tweens.remove(at: Int(i))
            }
            i -= 1
        }
        s.values[key] = to
    }
    static func stepTweens(_ s: Scene) {
        var i: Double = Double(s.tweens.count) - 1.0
        while i >= 0.0 {
            let a = s.tweens[Int(i)]
            a.elapsed += H
            if a.k > 0.0 {
                let result = oscillator(s.values[a.key] - a.to, a.velocity, a.k, a.c, H)
                s.values[a.key] = a.to + result[0]
                a.velocity = result[1]
                if abs(result[0]) < 0.00001 && abs(result[1]) < 0.0001 {
                    s.values[a.key] = a.to
                    s.tweens.remove(at: Int(i))
                }
            } else {
                s.values[a.key] =
                    a.from + (a.to - a.from) * cubic(a.elapsed / a.duration, (a.travel ? MOTION.travel : MOTION.out))
                if a.elapsed + 1e-12 >= a.duration {
                    s.values[a.key] = a.to
                    s.tweens.remove(at: Int(i))
                }
            }
            i -= 1
        }
    }
    static func phaseTo(_ s: Scene, _ phase: Double) {
        s.values.phase = phase
        s.phaseStep = s.steps
    }
    static func startReveal(_ s: Scene, _ reduced: Bool) {
        phaseTo(s, PHASE.reveal)
        if s.takeover {
            s.lastTouch = s.steps
        }
        s.values.winner = winningWedge(s.sim.theta)
        let v = s.values
        if reduced {
            animateValue(s, "lift", 1.0, MOTION.reducedReveal)
            animateValue(s, "glow", 1.0, MOTION.reducedReveal)
            animateValue(s, "dim", 1.0, MOTION.reducedReveal)
            animateValue(s, "hero", 1.0, MOTION.reducedReveal)
            animateValue(s, "caption", 1.0, MOTION.reducedReveal)
            animateValue(s, "button", 1.0, MOTION.reducedReveal)
            animateValue(s, "copySwap", 1.0, MOTION.reducedReveal)
            v.winnerPrize = 0.0
            v.count = PRIZES[Int(v.winner)].count
            v.badge = 1.0
            v.rays = 0.6
            v.burst = -1.0
            v.heroSquash = 0.0
            s.events = Double(Int(s.events) | Int(MOTION_EVENT.success))
            return
        }
        animateValue(s, "lift", 1.0, 0.0, MOTION.liftSpring)
        animateValue(s, "glow", 1.0, MOTION.reveal.glow)
        animateValue(s, "comet", 1.0, MOTION.reveal.comet)
        animateValue(s, "dim", 1.0, MOTION.reveal.dim)
        animateValue(s, "wheelScale", MOTION.revealScale, 0.0, MOTION.wheelSpring)
        animateValue(s, "copySwap", 1.0, MOTION.reveal.copy)
        animateValue(s, "heroSquash", -0.5, MOTION.reveal.crouch)
    }
    static func startCollect(_ s: Scene, _ reduced: Bool) {
        if s.values.phase != PHASE.reveal {
            return
        }
        phaseTo(s, PHASE.collecting)
        s.heroHeld = false
        s.events = Double(Int(s.events) | Int(MOTION_EVENT.light))
        s.values.burst = -1.0
        if reduced {
            animateValue(s, "hero", 0.0, MOTION.reducedCollect)
            animateValue(s, "lift", 0.0, MOTION.reducedCollect)
            animateValue(s, "glow", 0.0, MOTION.reducedCollect)
            animateValue(s, "dim", 0.0, MOTION.reducedCollect)
            animateValue(s, "caption", 0.0, MOTION.reducedCollect)
            animateValue(s, "button", 0.0, MOTION.reducedCollect)
            animateValue(s, "rays", 0.0, MOTION.reducedCollect)
            animateValue(s, "copySwap", 0.0, MOTION.reducedCollect)
            animateValue(s, "buttonPress", 0.0, MOTION.reducedCollect)
            return
        }
        animateValue(s, "buttonPress", 0.0, 0.0, MOTION.swallowSpring)
        animateValue(s, "collect", 1.0, MOTION.collecting.flight, nil, true)
        animateValue(s, "caption", 0.0, MOTION.collecting.caption)
        animateValue(s, "rays", 0.0, MOTION.collecting.rays)
        animateValue(s, "copySwap", 0.0, MOTION.collecting.copy)
        animateValue(s, "heroX", 0.0, 0.0, MOTION.toySpring)
        animateValue(s, "heroY", 0.0, 0.0, MOTION.toySpring)
        animateValue(s, "heroTilt", 0.0, 0.0, MOTION.toySpring)
    }
    static func reducedSpin(_ s: Scene, _ random: Double) {
        let current = winningWedge(s.sim.theta)
        s.reducedPick = ((current + 1.0 + floor(clamp(random, 0.0, 0.999999) * (Double(PRIZES.count) - 1.0))))
            .truncatingRemainder(dividingBy: Double(PRIZES.count))
        s.reducedStep = s.steps
        s.sim.omega = 0.0
        s.held = false
        s.hubHeld = false
        phaseTo(s, PHASE.spinning)
        animateValue(s, "wheelOpacity", 0.0, MOTION.reducedFade)
    }
    static func releaseWheel(_ s: Scene, _ omega: Double) {
        s.held = false
        s.hubHeld = false
        s.tiltTargetX = 0.0
        s.tiltTargetY = 0.0
        let launchChange = clamp(omega, -PHYSICS.speedCap, PHYSICS.speedCap) - s.sim.omega
        s.sim.swayVelocityA += PHYSICS.swayGainA * pow(PHYSICS.swayFrequencyA, 2.0) * launchChange
        s.sim.swayVelocityB += PHYSICS.swayGainB * pow(PHYSICS.swayFrequencyB, 2.0) * launchChange
        s.sim.omega = clamp(omega, -PHYSICS.speedCap, PHYSICS.speedCap)
        s.values.winner = -1.0
        s.sim.still = 0.0
        s.releaseSpeed = abs(s.sim.omega)
        s.releaseTravel = s.sim.travel
        phaseTo(s, PHASE.spinning)
        animateValue(s, "wheelScale", 1.0, 0.0, MOTION.holdSpring)
        animateValue(s, "hubPress", 0.0, 0.0, MOTION.hubSpring)
        animateValue(s, "hint", MOTION.hintSpinning, MOTION.hintDuration)
    }
    static func takeOver(_ s: Scene) {
        if !s.takeover && s.held {
            releaseWheel(s, s.sim.omega)
        }
        s.takeover = true
        s.lastTouch = s.steps
        s.homeStep = -1.0
        animateValue(s, "touchOn", 0.0, MOTION.fingerFade)
    }
    static func tickScene(_ s: Scene, _ before: Double) {
        if s.sim.strikes > before {
            s.values.strikes += s.sim.strikes - before
            s.values.tick = 1.0
            if (s.steps - s.lastHaptic) * H >= MOTION.hapticGap {
                s.events = Double(
                    Int(s.events) | Int((abs(s.sim.omega) < 4.0 ? MOTION_EVENT.rigid : MOTION_EVENT.light)))
                s.lastHaptic = s.steps
            }
        }
    }
    static func stepReveal(_ s: Scene, _ reduced: Bool) {
        let step = s.steps - s.phaseStep
        let r = MOTION.reveal
        let c = MOTION.collecting
        let v = s.values
        if v.phase == PHASE.reveal && !reduced {
            if step == demoSteps(r.launchAt) {
                v.winnerPrize = 0.0
                animateValue(s, "hero", 1.0, 0.0, MOTION.heroSpring)
                animateValue(s, "heroSquash", 0.6, r.stretch)
                animateValue(s, "rays", 1.0, r.rays)
                v.burst = 0.0
                v.burstPower =
                    (v.winner == JACKPOT_INDEX ? 1.0 : min(0.85, 0.6 + 0.2 * PRIZES[Int(v.winner)].count / 15.0))
                s.events = Double(Int(s.events) | Int(MOTION_EVENT.success))
            }
            if step == demoSteps(r.launchAt + r.stretch) {
                animateValue(s, "heroSquash", 0.0, 0.0, MOTION.squashSpring)
            }
            if step == demoSteps(r.badgeAt) {
                animateValue(s, "badge", 1.0, r.badge, nil, true)
            }
            if step == demoSteps(r.captionAt) {
                animateValue(s, "caption", 1.0, r.caption)
                let n = PRIZES[Int(v.winner)].count
                animateValue(s, "count", n, min(r.countCap, r.countBase + r.countPerPrize * n))
            }
            if step == demoSteps(r.buttonAt) {
                animateValue(s, "button", 1.0, r.button, nil, true)
            }
        }
        if v.phase == PHASE.collecting {
            if !reduced && step == demoSteps(c.sinkAt) {
                animateValue(s, "lift", 0.0, 0.0, MOTION.sinkSpring)
                animateValue(s, "glow", 0.0, c.glow)
                animateValue(s, "dim", 0.0, c.dim)
                animateValue(s, "wheelScale", 1.0, 0.0, MOTION.wheelSpring)
            }
            if !reduced && step == demoSteps(c.swallowAt) {
                animateValue(s, "buttonPress", -1.0, c.swallow)
            }
            if !reduced && step == demoSteps(c.swallowAt + c.swallow) {
                animateValue(s, "buttonPress", 0.0, 0.0, MOTION.swallowSpring)
            }
            if step == demoSteps((reduced ? MOTION.reducedCollect : c.restockAt)) {
                setValue(s, "hero", 0.0)
                setValue(s, "collect", 0.0)
                setValue(s, "badge", 0.0)
                setValue(s, "count", 0.0)
                v.winnerPrize = 1.0
                if !reduced {
                    setValue(s, "winnerPrizeScale", 1.28)
                    animateValue(s, "winnerPrizeScale", 1.0, 0.0, MOTION.restockSpring)
                }
            }
            if !reduced && step == demoSteps(c.buttonAt) {
                animateValue(s, "button", 0.0, c.button, nil, true)
            }
            if step >= demoSteps((reduced ? MOTION.reducedCollect : c.done)) {
                v.winner = -1.0
                v.comet = 0.0
                phaseTo(s, PHASE.idle)
                animateValue(s, "hint", 1.0, MOTION.hintDuration)
            }
        }
    }
    static func stepDemo(_ s: Scene, _ config: Config) {
        let stage = s.demoStage
        let elapsed = s.steps - s.demoStep
        let second = stage >= 5.0
        let throwSpec = (second ? DEMO.second : DEMO.first)
        let v = s.values
        if stage == 0.0 || stage == 4.0 || stage == 8.0 {
            // Keep the measured loop length despite sub-frame native contact timing differences.
            let wait =
                (stage == 0.0
                    ? DEMO.rest
                    : (stage == 4.0 ? DEMO.between : max(0, (DEMO.periodSteps - (s.demoStep - s.loopStep)) * H)))
            if elapsed < demoSteps(wait) {
                return
            }
            if stage == 8.0 {
                s.lastPeriod = s.steps - s.loopStep
                s.loopStep = s.steps
                s.loops += 1.0
                s.sim = initialSim()
                s.targetTheta = 0.0
                let strikes = v.strikes
                s.values = initialValues()
                s.values.strikes = strikes
                s.tweens = []
                s.tiltBaseX = 0.0
                s.tiltBaseY = 0.0
                s.tiltVX = 0.0
                s.tiltVY = 0.0
                s.demoStage = 0.0
                s.demoStep = s.steps
                return
            }
            s.demoStage = (stage == 0.0 ? 1.0 : 5.0)
            s.demoStep = s.steps
            s.demoStartTheta = s.sim.theta
            s.sim = initialSim(s.sim.theta)
            s.targetTheta = s.sim.theta
            let spec = (stage == 0.0 ? DEMO.first : DEMO.second)
            v.touchX = config.cx + spec.x * config.radius
            v.touchY = config.cy + spec.y * config.radius
            animateValue(s, "touchOn", 1.0, MOTION.fingerFade)
            s.held = true
            phaseTo(s, PHASE.held)
            animateValue(s, "wheelScale", MOTION.heldScale, 0.0, MOTION.holdSpring)
        } else {
            if stage == 1.0 || stage == 5.0 {
                let dragStep = elapsed - demoSteps(DEMO.fingerWait)
                if dragStep > 0.0 {
                    let angle = demoDrag(dragStep, throwSpec.duration, throwSpec.drag)
                    s.targetTheta = s.demoStartTheta + angle
                    let c = cos(angle)
                    let n = sin(angle)
                    let x = throwSpec.x * c - throwSpec.y * n
                    let y = throwSpec.x * n + throwSpec.y * c
                    v.touchX = config.cx + x * config.radius
                    v.touchY = config.cy + y * config.radius
                    s.tiltTargetX = MOTION.tiltGain * x
                    s.tiltTargetY = MOTION.tiltGain * y
                    if dragStep > demoSteps(throwSpec.duration) {
                        releaseWheel(s, second ? nativeSecondOmega : throwSpec.omega)
                        animateValue(s, "touchOn", 0.0, MOTION.fingerFade)
                        s.demoStage = stage + 1.0
                        s.demoStep = s.steps
                    }
                }
            } else {
                if stage == 2.0 || stage == 6.0 {
                    if v.phase == PHASE.reveal {
                        let age = s.steps - s.phaseStep
                        if age == demoSteps(throwSpec.admire - MOTION.fingerFade) {
                            v.touchX = config.buttonX
                            v.touchY = config.buttonY
                            animateValue(s, "touchOn", 1.0, MOTION.fingerFade)
                        }
                        if age == demoSteps(throwSpec.admire) {
                            animateValue(s, "buttonPress", 1.0, MOTION.collecting.press)
                        }
                        if age >= demoSteps(throwSpec.admire + DEMO.press) {
                            startCollect(s, false)
                            animateValue(s, "touchOn", 0.0, MOTION.fingerFade)
                            s.demoStage = stage + 1.0
                            s.demoStep = s.steps
                        }
                    }
                } else {
                    if (stage == 3.0 || stage == 7.0) && v.phase == PHASE.idle {
                        s.demoStage = stage + 1.0
                        s.demoStep = s.steps
                    }
                }
            }
        }
    }
    static func stepScene(_ s: Scene, _ config: Config) {
        s.steps += 1.0
        if config.autoPlay && !config.reduced && !s.takeover {
            stepDemo(s, config)
        }
        if config.autoPlay && !config.reduced && s.takeover && !s.touching && !s.held && !s.hubHeld && !s.heroHeld
            && (s.steps - s.lastTouch) * H >= MOTION.takeoverDelay
        {
            if s.values.phase == PHASE.reveal {
                startCollect(s, false)
            }
            if s.values.phase == PHASE.idle && s.homeStep < 0.0 {
                s.homeStep = s.steps
                s.homeFrom = s.sim.theta
                s.homeTo = jsRound(s.sim.theta / TAU) * TAU
            }
        }
        let previousStrikes = s.sim.strikes
        if s.homeStep >= 0.0 {
            let progress = (s.steps - s.homeStep) * H / MOTION.homeDuration
            stepWheel(s.sim, s.homeFrom + (s.homeTo - s.homeFrom) * cubic(progress, MOTION.travel))
            if progress >= 1.0 {
                s.sim.theta = 0.0
                s.sim.omega = 0.0
                s.sim.contact = -1.0
                s.targetTheta = 0.0
                s.homeStep = -1.0
                s.takeover = false
                s.demoStage = 0.0
                s.demoStep = s.steps
                s.loopStep = s.steps
            }
        } else {
            if s.held && !config.reduced {
                stepWheel(s.sim, s.targetTheta)
            } else {
                if !config.reduced && (s.values.phase == PHASE.spinning || s.values.phase == PHASE.landed) {
                    stepWheel(s.sim)
                    let counted =
                        s.releaseSpeed >= PHYSICS.countedSpeed
                        && s.sim.travel - s.releaseTravel >= PHYSICS.countedTravel
                    if s.sim.still > 0.0 && s.values.phase == PHASE.spinning && counted {
                        s.values.winner = winningWedge(s.sim.theta)
                        phaseTo(s, PHASE.landed)
                    }
                    if s.sim.still > PHYSICS.stillBeat {
                        if counted {
                            startReveal(s, false)
                        } else {
                            phaseTo(s, PHASE.idle)
                            animateValue(s, "hint", 1.0, MOTION.hintDuration)
                            s.values.hintNudge = 1.0
                        }
                    } else {
                        if s.sim.still == 0.0 && s.values.phase == PHASE.landed {
                            phaseTo(s, PHASE.spinning)
                        }
                    }
                } else {
                    if !config.reduced && !s.hubHeld {
                        stepWheel(s.sim)
                    }
                }
            }
        }
        tickScene(s, previousStrikes)
        if config.reduced && s.reducedStep >= 0.0 {
            let elapsed = s.steps - s.reducedStep
            if elapsed == demoSteps(MOTION.reducedFade) {
                s.sim = initialSim(POINTER.angle - s.reducedPick * SEGMENT_ANGLE)
                animateValue(s, "wheelOpacity", 1.0, MOTION.reducedFade)
            }
            if elapsed >= demoSteps(2.0 * MOTION.reducedFade) {
                s.reducedStep = -1.0
                startReveal(s, true)
            }
        }
        stepReveal(s, config.reduced)
        stepTweens(s)
        let v = s.values
        v.theta = s.sim.theta
        v.omega = s.sim.omega
        v.swing = s.sim.swing
        v.swayA =
            (config.reduced
                ? 0.0 : clamp(s.sim.swayA + PHYSICS.swayLean * s.sim.omega, -PHYSICS.swayLimit, PHYSICS.swayLimit))
        v.swayB = (config.reduced ? 0.0 : s.sim.swayB)
        v.tick *= exp(-H / MOTION.tickDecay)
        v.swirl += H * (MOTION.swirlGain * v.omega - MOTION.swirlDrag * v.swirl)
        let tx = oscillator(
            s.tiltBaseX - s.tiltTargetX, s.tiltVX, MOTION.holdSpring[0], MOTION.holdSpring[1], H)
        let ty = oscillator(
            s.tiltBaseY - s.tiltTargetY, s.tiltVY, MOTION.holdSpring[0], MOTION.holdSpring[1], H)
        s.tiltBaseX = tx[0] + s.tiltTargetX
        s.tiltVX = tx[1]
        s.tiltBaseY = ty[0] + s.tiltTargetY
        s.tiltVY = ty[1]
        let wobble = (config.reduced ? 0.0 : MOTION.wobbleBase + MOTION.wobbleSpeed * min(abs(v.omega) / 20.0, 1.0))
        v.tiltX = s.tiltBaseX + wobble * cos(v.theta + MOTION.wobblePhase)
        v.tiltY = s.tiltBaseY + wobble * sin(v.theta + MOTION.wobblePhase)
        let closing =
            (config.autoPlay && !s.takeover && s.demoStage == 8.0
                ? 1.0 - cubic((s.steps - s.demoStep) * H / DEMO.tail, MOTION.out) : 1.0)
        v.envTurn =
            (config.reduced
                ? 0.0
                : closing * MOTION.environmentAmount * sin(TAU * (s.steps - s.loopStep) * H / MOTION.environmentPeriod))
        if v.burst >= 0.0 {
            v.burst += H
        }
        if v.phase == PHASE.idle && v.hintNudge != 0.0 {
            let t = (s.steps - s.phaseStep) * H
            v.hintNudge =
                (t >= MOTION.nudgeDuration
                    ? 0.0 : cos(TAU * 2.0 * t / MOTION.nudgeDuration) * (1.0 - t / MOTION.nudgeDuration))
        }
    }
    static func advanceScene(_ s: Scene, _ elapsed: Double, _ config: Config) {
        s.events = 0.0
        s.accumulator += max(0.0, elapsed)
        let count = floor((s.accumulator + 1e-12) / H)
        s.accumulator -= count * H
        var i: Double = 0.0
        while i < count {
            stepScene(s, config)
            i += 1
        }
    }
    static func actWheel(_ s: Scene, _ action: Action, _ config: Config) {
        s.events = 0.0
        let v = s.values
        if action.type == "release" {
            s.touching = false
            s.lastTouch = s.steps
        }
        if action.type == "down" {
            s.touching = true
            takeOver(s)
            if v.phase != PHASE.idle && v.phase != PHASE.spinning && v.phase != PHASE.landed && v.phase != PHASE.held {
                return
            }
            let d = sqrt(action.x * action.x + action.y * action.y)
            if d > config.radius {
                return
            }
            s.hubHeld = d < config.radius * PROFILE.hub * MOTION.hubHitScale
            s.held = !s.hubHeld
            v.winner = -1.0
            s.targetTheta = s.sim.theta
            s.fingerAngle = atan2(action.y, action.x)
            s.trackedRate = 0.0
            s.moveStep = s.steps
            s.sim.omega = 0.0
            phaseTo(s, PHASE.held)
            if s.hubHeld {
                animateValue(s, "hubPress", 1.0, MOTION.hubPress)
            } else {
                if !config.reduced {
                    animateValue(s, "wheelScale", MOTION.heldScale, 0.0, MOTION.holdSpring)
                    s.tiltTargetX = MOTION.tiltGain * action.x / config.radius
                    s.tiltTargetY = MOTION.tiltGain * action.y / config.radius
                }
            }
        } else {
            if action.type == "move" && s.held {
                s.lastTouch = s.steps
                if config.reduced {
                    return
                }
                let angle = atan2(action.y, action.x)
                let delta = wrap(angle - s.fingerAngle)
                s.trackedRate += MOTION.trackedBlend * (delta / (max(1.0, s.steps - s.moveStep) * H) - s.trackedRate)
                s.targetTheta += delta
                s.fingerAngle = angle
                s.moveStep = s.steps
                s.tiltTargetX = MOTION.tiltGain * clamp(action.x / config.radius, -1.0, 1.0)
                s.tiltTargetY = MOTION.tiltGain * clamp(action.y / config.radius, -1.0, 1.0)
            } else {
                if action.type == "release" && (s.held || s.hubHeld) {
                    s.lastTouch = s.steps
                    if action.cancelled {
                        s.held = false
                        s.hubHeld = false
                        s.tiltTargetX = 0.0
                        s.tiltTargetY = 0.0
                        if config.reduced {
                            phaseTo(s, PHASE.idle)
                        } else {
                            releaseWheel(s, 0.0)
                        }
                        animateValue(
                            s, "hubPress", 0.0, (config.reduced ? MOTION.reducedReveal : 0.0),
                            (config.reduced ? nil : MOTION.hubSpring))
                        return
                    }
                    if config.reduced {
                        reducedSpin(s, action.random)
                        animateValue(s, "hubPress", 0.0, MOTION.reducedReveal)
                        return
                    }
                    if s.held && s.targetTheta != s.sim.theta {
                        let before = s.sim.strikes
                        stepWheel(s.sim, s.targetTheta)
                        tickScene(s, before)
                    }
                    if s.hubHeld {
                        s.events = Double(Int(s.events) | Int(MOTION_EVENT.medium))
                        releaseWheel(s, MOTION.hubSpeed + (action.random * 2.0 - 1.0) * MOTION.hubVariation)
                    } else {
                        let d2 = max(config.radius * config.radius * 0.01, action.x * action.x + action.y * action.y)
                        let cross = (action.x * action.vy - action.y * action.vx) / d2
                        releaseWheel(s, MOTION.velocityBlend * cross + (1.0 - MOTION.velocityBlend) * s.trackedRate)
                    }
                } else {
                    if action.type == "hub" {
                        takeOver(s)
                        if v.phase != PHASE.idle && v.phase != PHASE.spinning && v.phase != PHASE.landed {
                            return
                        }
                        if config.reduced {
                            reducedSpin(s, action.random)
                        } else {
                            s.events = Double(Int(s.events) | Int(MOTION_EVENT.medium))
                            releaseWheel(s, MOTION.hubSpeed + (action.random * 2.0 - 1.0) * MOTION.hubVariation)
                        }
                    } else {
                        if action.type == "heroDown" {
                            s.touching = true
                            takeOver(s)
                            if v.phase == PHASE.reveal && v.hero > 0.9 && !config.reduced {
                                s.heroHeld = true
                                s.heroOriginX = v.heroX
                                s.heroOriginY = v.heroY
                                setValue(s, "heroX", v.heroX)
                                setValue(s, "heroY", v.heroY)
                                setValue(s, "heroTilt", v.heroTilt)
                            }
                        } else {
                            if action.type == "heroMove" && s.heroHeld {
                                s.lastTouch = s.steps
                                v.heroX = s.heroOriginX + action.x * MOTION.heroPull
                                v.heroY = s.heroOriginY + action.y * MOTION.heroPull
                                v.heroTilt = clamp(
                                    action.vx * MOTION.heroTiltGain, -MOTION.heroTiltCap, MOTION.heroTiltCap)
                            } else {
                                if action.type == "heroRelease" {
                                    s.touching = false
                                    s.heroHeld = false
                                    s.lastTouch = s.steps
                                    if !config.reduced {
                                        animateValue(s, "heroX", 0.0, 0.0, MOTION.toySpring)
                                        animateValue(s, "heroY", 0.0, 0.0, MOTION.toySpring)
                                        animateValue(s, "heroTilt", 0.0, 0.0, MOTION.toySpring)
                                    }
                                } else {
                                    if action.type == "pressIn" {
                                        s.touching = true
                                        takeOver(s)
                                        if v.phase == PHASE.reveal {
                                            animateValue(s, "buttonPress", 1.0, MOTION.collecting.press)
                                        }
                                    } else {
                                        if action.type == "pressOut" {
                                            s.touching = false
                                            s.lastTouch = s.steps
                                            animateValue(
                                                s, "buttonPress", 0.0, (config.reduced ? MOTION.reducedReveal : 0.0),
                                                (config.reduced ? nil : MOTION.swallowSpring))
                                        } else {
                                            if action.type == "collect" {
                                                s.touching = false
                                                takeOver(s)
                                                startCollect(s, config.reduced)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
