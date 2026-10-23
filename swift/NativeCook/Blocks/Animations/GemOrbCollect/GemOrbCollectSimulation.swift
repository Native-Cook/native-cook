import CoreGraphics
import Foundation
import simd

enum GemOrbCollectPhase: Int {
    case before
    case collecting
    case hush
    case burst
    case after
    case rewind
}

struct GemOrbCollectLayout: Equatable {
    let width: Double
    let height: Double
    let safeTop: Double
    let safeBottom: Double
    let scale: Double
    let typeScale: Double
    let gemX: Double
    let gemY: Double
    let gemRadius: Double
    let tallyY: Double
    let titleTop: Double
    let bodyTop: Double
    let capsule: CGRect
    let miniGemX: Double
    let miniGemY: Double
    let button: CGRect

    init(size: CGSize, safeTop: Double, safeBottom: Double) {
        width = size.width
        height = size.height
        self.safeTop = safeTop
        self.safeBottom = safeBottom
        scale = min(size.width / 402, size.height / 874)
        typeScale = max(scale, 0.86)
        let usable = size.height - safeTop - safeBottom
        gemRadius = (104 * scale).rounded()
        gemX = size.width / 2
        gemY = (safeTop + usable * 0.296).rounded()
        tallyY = gemY + gemRadius + (54 * scale).rounded()
        titleTop = (safeTop + usable * 0.599).rounded()
        bodyTop = titleTop + (34 * typeScale).rounded() + 8
        let capsuleTop = bodyTop + (24 * typeScale).rounded() + 24
        capsule = CGRect(x: ((size.width - 120) / 2).rounded(), y: capsuleTop, width: 120, height: 44)
        miniGemX = capsule.minX + 24
        miniGemY = capsule.midY
        let buttonHeight = size.height < 700 ? 48.0 : 56.0
        button = CGRect(x: 24, y: size.height - safeBottom - 24 - buttonHeight, width: size.width - 48, height: buttonHeight)
    }
}

struct GemOrbCollectOrb {
    var home = SIMD3<Double>(repeating: 0)
    var start = SIMD3<Double>(repeating: 0)
    var target = SIMD3<Double>(repeating: 0)
    var position = SIMD3<Double>(repeating: 0)
    var size = 10.0
    var tint = 0
    var arc = 0.0
    var angle = 0.0
    var launch = -1.0
    var hit = -1.0
    var duration = 0.8
    var status = 0
    var slot = -1
    var silent = false
}

struct GemOrbCollectSpark {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var vx = 0.0
    var vy = 0.0
    var life = 0.0
    var age = 0.0
    var size = 0.0
    var tint = 0
}

struct GemOrbCollectInput {
    var gemDown = false
    var barDown = false
    var drag = CGVector.zero
    var release = CGVector.zero
    var tap = false
    var collect = false
    var replay = false
    var holdMoved = false
    var press = false
    var silent = false
    var touch = CGPoint(x: 0.25, y: 0.2)

    mutating func consumeEvents() {
        drag = .zero
        release = .zero
        tap = false
        collect = false
        replay = false
        press = false
    }
}

struct GemOrbCollectPresentation: Equatable {
    var phase = GemOrbCollectPhase.before
    var tally = 0
    var balance = 1400
    var balanceValue = 1400.0
    var copyProgress = 0.0
    var copyTarget = 0.0
    var bodyProgress = 0.0
    var buttonProgress = 0.0
    var tallyOpacity = 0.0
    var tallyScale = 1.0
    var barOpacity = 1.0
    var barScale = 1.0
    var capsuleScale = 1.0
    var rollFade = 1.0
}

struct GemOrbCollectSimulation {
    static let step = 1.0 / 240.0
    static let orbCount = 20
    static let loopDuration = 12.0
    static let resumeAfter = 3.5
    static let tau = Double.pi * 2
    static let eventPress = 1
    static let eventHit = 2
    static let eventBurst = 4
    static let eventLand = 8
    static let eventPing = 16
    static let eventRewind = 32

    private(set) var layout: GemOrbCollectLayout
    private(set) var phase = GemOrbCollectPhase.before
    private(set) var clock = 0.0
    private var phaseAt = 0.0
    private var accumulator = 0.0
    private var demoTick = 0
    private var demoPaused = false
    private var quietTime = 0.0
    private var automatic = false
    private var down = false
    private var previousBarDown = false
    private var phaseSilent = false
    private(set) var events = 0
    private(set) var audibleEvents = 0
    private var held = 0.0
    private var holdX = 0.0
    private var holdY = 0.0
    private var dragOnly = false
    private var holdAccepted = false
    private var cadence = 0.0
    private var nextLaunch = 0.0
    private(set) var launched = 0
    private(set) var hits = 0
    private var targetCharge = 0.0
    private(set) var charge = 0.0
    private(set) var flare = 0.0
    private(set) var hush = 0.0
    private(set) var shock = 0.0
    private(set) var beams = 0.0
    private var burstAt = -1.0
    private var firstHitAt = -1.0
    private var landed = false
    private var turn = 0.0
    private var tilt = 0.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var turnVelocity = 0.0
    private var tiltVelocity = 0.0
    private var scale = 1.0
    private var scaleVelocity = 0.0
    private var stretch = 1.0
    private var stretchVelocity = 0.0
    private var hushFromScale = 1.0
    private var hushFromCharge = 0.0
    private var spinFrom = 0.0
    private var copyFrom = 0.0
    private var copyTarget = 0.0
    private var copyAt = -10.0
    private var labelFrom = 0.0
    private var labelTarget = 0.0
    private var labelAt = -10.0
    private var bodyCopy = 0.0
    private var copy = 0.0
    private var label = 0.0
    private var barOpacity = 1.0
    private var barPress = 0.0
    private var barScale = 1.0
    private var barVelocity = 0.0
    private var barGlow = 0.0
    private var barSheen = -1.0
    private var capsuleScale = 1.0
    private var capsuleVelocity = 0.0
    private var capsuleGlow = 0.0
    private var capsuleSheen = -1.0
    private var miniYaw = -0.16
    private var miniFlare = 0.0
    private var rewindCharge = 0.0
    private var rewindValue = 1400.0
    private var value = 1400.0
    private var pingAt = -10.0
    private var pingPoint = SIMD2<Double>(0.25, 0.2)
    private(set) var orbs: [GemOrbCollectOrb] = []
    private var sparks = [GemOrbCollectSpark](repeating: GemOrbCollectSpark(), count: 50)
    private var lights = [SIMD4<Float>](repeating: .zero, count: 6)
    private var beadX = 0.0
    private var beadY = 0.0
    private var beadSize = 2.0
    private var beadAlpha = 0.0
    private var beadStretch = 1.0
    private var beadAngle = Double.pi / 2
    private var tallyScale = 1.0
    private var tallyVelocity = 0.0
    private var order: [Int] = []

    init(layout: GemOrbCollectLayout) {
        self.layout = layout
        makeOrbs()
        beadX = layout.gemX
        beadY = layout.tallyY
        sampleRest(reduced: false)
    }

    mutating func resize(_ next: GemOrbCollectLayout, reduced: Bool) {
        guard next != layout else { return }
        layout = next
        reset(reduced: reduced)
    }

    mutating func interruptDemo() {
        demoPaused = true
        quietTime = 0
    }

    mutating func advance(seconds: Double, input realInput: inout GemOrbCollectInput, autoPlay: Bool, reduced: Bool, touching: Bool) {
        events = 0
        audibleEvents = 0
        var input = realInput
        if autoPlay, demoPaused {
            quietTime = touching ? 0 : quietTime + min(max(seconds, 0), 0.05)
            if !touching, quietTime >= Self.resumeAfter {
                if phase != .before {
                    input.replay = phase != .rewind
                } else {
                    reset(reduced: reduced)
                    demoPaused = false
                    demoTick = 0
                    realInput.consumeEvents()
                    return
                }
            }
        }

        accumulator += min(max(seconds, 0), 0.05)
        let count = Int((accumulator + 1e-9) / Self.step)
        guard count > 0 else { return }
        let drag = CGVector(dx: input.drag.dx / Double(count), dy: input.drag.dy / Double(count))
        for index in 0..<count {
            var tick = autoPlay && !demoPaused
                ? demoInput(from: Double(demoTick) * Self.step, seconds: Self.step, reduced: reduced)
                : input
            if !autoPlay || demoPaused {
                tick.drag = drag
            }
            if index > 0, !autoPlay || demoPaused {
                tick.tap = false
                tick.collect = false
                tick.replay = false
                tick.release = .zero
                tick.press = false
            }
            step(input: tick, reduced: reduced)
            accumulator -= Self.step
            if autoPlay, !demoPaused {
                demoTick += 1
                if demoTick >= Int(Self.loopDuration / Self.step) {
                    let remaining = accumulator
                    reset(reduced: reduced)
                    accumulator = remaining
                    demoTick = 0
                }
            }
        }
        if accumulator < 1e-9 { accumulator = 0 }
        realInput.consumeEvents()
    }

    mutating func reset(reduced: Bool) {
        phase = .before
        clock = 0
        phaseAt = 0
        accumulator = 0
        automatic = false
        down = false
        previousBarDown = false
        phaseSilent = false
        events = 0
        audibleEvents = 0
        held = 0
        holdX = 0
        holdY = 0
        dragOnly = false
        holdAccepted = false
        cadence = 0
        nextLaunch = 0
        launched = 0
        hits = 0
        targetCharge = 0
        charge = 0
        flare = 0
        hush = 0
        shock = 0
        beams = 0
        burstAt = -1
        firstHitAt = -1
        landed = false
        turn = 0
        tilt = 0
        offsetX = 0
        offsetY = 0
        velocityX = 0
        velocityY = 0
        turnVelocity = 0
        tiltVelocity = 0
        scale = 1
        scaleVelocity = 0
        stretch = 1
        stretchVelocity = 0
        copyFrom = 0
        copyTarget = 0
        copyAt = -10
        labelFrom = 0
        labelTarget = 0
        labelAt = -10
        bodyCopy = 0
        copy = 0
        label = 0
        barOpacity = 1
        barPress = 0
        barScale = 1
        barVelocity = 0
        barGlow = 0
        barSheen = -1
        capsuleScale = 1
        capsuleVelocity = 0
        capsuleGlow = 0
        capsuleSheen = -1
        miniYaw = -0.16
        miniFlare = 0
        value = 1400
        sparks = [GemOrbCollectSpark](repeating: GemOrbCollectSpark(), count: 50)
        lights = [SIMD4<Float>](repeating: .zero, count: 6)
        beadX = layout.gemX
        beadY = layout.tallyY
        beadSize = 2
        beadAlpha = 0
        beadStretch = 1
        beadAngle = .pi / 2
        tallyScale = 1
        tallyVelocity = 0
        makeOrbs()
        sampleRest(reduced: reduced)
    }

    mutating func collect() {
        guard phase == .before else { return }
        automatic = true
        changePhase(.collecting)
    }

    mutating func replay() {
        guard phase != .before, phase != .rewind else { return }
        rewindCharge = charge
        rewindValue = value
        emit(Self.eventRewind, silent: phaseSilent)
        for index in sparks.indices { sparks[index].life = 0 }
        setCopy(0)
        changePhase(.rewind)
    }

    mutating func ping(at point: CGPoint, silent: Bool, reduced: Bool) {
        pingAt = clock
        pingPoint = SIMD2(point.x, point.y)
        scaleVelocity += reduced ? 0 : 1.6
        emit(Self.eventPing, silent: silent)
    }

    func presentation(reduced: Bool) -> GemOrbCollectPresentation {
        let firstAge = clock - firstHitAt
        var tallyOpacity = hits > 0 && !reduced ? ease(firstAge / 0.16) : 0
        if phase == .burst || phase == .after {
            let transferAge = clock - burstAt - 0.25
            tallyOpacity *= 1 - clamp(transferAge / 0.16)
        }
        if phase == .rewind || phase == .before { tallyOpacity = 0 }
        let tallyPulse = hits > 0 ? 1 + 0.12 * exp(-max(0, clock - latestHit()) * 10) : 1
        let rollFade: Double
        if reduced, phase == .collecting || phase == .rewind {
            rollFade = abs(2 * clamp((clock - phaseAt) / 0.6) - 1)
        } else {
            rollFade = 1
        }
        return GemOrbCollectPresentation(
            phase: phase,
            tally: hits * 10,
            balance: Int(value.rounded()),
            balanceValue: value,
            copyProgress: copy,
            copyTarget: copyTarget,
            bodyProgress: bodyCopy,
            buttonProgress: label,
            tallyOpacity: tallyOpacity,
            tallyScale: tallyPulse,
            barOpacity: barOpacity,
            barScale: barScale,
            capsuleScale: capsuleScale,
            rollFade: rollFade
        )
    }

    func renderState(reduced: Bool) -> GemOrbCollectRenderState {
        let idle = clock.truncatingRemainder(dividingBy: Self.loopDuration)
        let sway = phase == .after ? 0.28 : 0.22
        let afterSway = phase == .after ? 0.18 * sin(.pi * clamp((clock - phaseAt) / 1.8)) : 0
        let yaw = -0.16 + turn + (reduced ? 0 : sin(Self.tau * idle / 12) * sway * (1 - hush)
                                          + afterSway - (phase == .hush ? 0.18 * hush : 0))
        let pitch = 0.1 + tilt + (reduced ? 0 : cos(Self.tau * idle / 6) * 0.07 * (1 - hush))
        let centreY = layout.gemY + offsetY + (reduced ? 0 : 5 * sin(Self.tau * idle / 6) * (1 - hush))
        var displayOrbs: [SIMD4<Float>] = []
        for (index, orb) in orbs.enumerated() {
            let perspective = 7 / (7 - orb.position.z)
            let x = layout.gemX + orb.position.x * layout.gemRadius * perspective
            let y = layout.gemY - orb.position.y * layout.gemRadius * perspective
            var alpha = 0.0
            if orb.status == 0 { alpha = 0.55 + 0.18 * sin(Self.tau * idle / 3 + Double(index)) }
            if orb.status == 1 { alpha = 0.65 + 0.35 * clamp((clock - orb.launch) / orb.duration) }
            if orb.status == 2 { alpha = 1 - clamp((clock - orb.hit) / 0.05) }
            if orb.status >= 3 { alpha = 0 }
            if reduced, phase != .before {
                let t = clock - phaseAt
                alpha *= phase == .rewind ? clamp((t - Double(index) * 0.015) / 0.3)
                    : phase == .collecting ? 1 - clamp((t - Double(index) * 0.015) / 0.3) : 0
            }
            let progress = orb.status == 1 ? clamp((clock - orb.launch) / orb.duration) : 0
            var stretch = orb.status == 1 ? 1 + 4 * pow(progress, 3) : orb.status == 2 ? 5 : 1
            if phase == .rewind, !reduced {
                let t = clock - phaseAt
                let u = clamp((t - Double(index) * 0.012) / (0.9 - Double(index) * 0.012))
                alpha = clamp((t - Double(index) * 0.012) / 0.04)
                stretch = 1 + 2 * (1 - u)
            }
            displayOrbs.append(SIMD4(Float(x), Float(y), Float(orb.position.z), Float(orb.size * perspective)))
            displayOrbs.append(SIMD4(Float(alpha), Float(orb.tint), Float(stretch), Float(orb.angle)))
        }
        var live = 0
        for spark in sparks where spark.age < spark.life {
            if live < 50, !reduced {
                let alpha = pow(1 - spark.age / spark.life, 2)
                displayOrbs.append(SIMD4(Float(spark.x), Float(spark.y), Float(spark.z), Float(spark.size)))
                displayOrbs.append(SIMD4(Float(alpha), Float(spark.tint), 2.5, Float(atan2(spark.vy, spark.vx))))
            }
            live += 1
        }
        while displayOrbs.count < 70 * 2 { displayOrbs.append(.zero) }
        displayOrbs.append(SIMD4(Float(beadX), Float(beadY), 0.3, Float(beadSize)))
        displayOrbs.append(SIMD4(Float(beadAlpha), 1, Float(beadStretch), Float(beadAngle)))
        let flashAge = clock - burstAt
        let flashAlpha = !reduced && burstAt >= 0 && flashAge < 0.5
            ? 0.12 * sin(.pi * flashAge / 0.5) : 0
        displayOrbs.append(SIMD4(Float(layout.gemX + offsetX), Float(centreY), 0.6,
                                  Float(layout.gemRadius * 1.5)))
        displayOrbs.append(SIMD4(Float(flashAlpha), 0, 1, 0))
        return GemOrbCollectRenderState(
            layout: layout,
            yaw: yaw,
            pitch: pitch,
            radius: layout.gemRadius * scale,
            centreX: layout.gemX + offsetX,
            centreY: centreY,
            stretch: stretch,
            charge: charge,
            flare: flare,
            hush: hush,
            shock: shock,
            beams: beams,
            time: clock,
            phase: phase,
            hits: hits,
            value: value,
            barPress: barPress,
            barGlow: barGlow,
            barSheen: barSheen,
            barOpacity: barOpacity,
            barScale: barScale,
            capsuleScale: capsuleScale,
            capsuleGlow: capsuleGlow,
            capsuleSheen: capsuleSheen,
            miniYaw: miniYaw,
            miniFlare: miniFlare,
            ping: clock - pingAt < 0.25 ? SIMD4(Float(pingPoint.x), Float(pingPoint.y), Float(1 - (clock - pingAt) / 0.25), 1) : .zero,
            orbs: displayOrbs,
            lights: lights
        )
    }

    private mutating func step(input: GemOrbCollectInput, reduced: Bool) {
        clock += Self.step
        if input.press || (input.gemDown && !down) || (input.barDown && !previousBarDown) {
            emit(Self.eventPress, silent: input.silent)
        }
        if !down || !input.gemDown {
            held = 0
            holdX = 0
            holdY = 0
            dragOnly = false
            holdAccepted = false
        }
        if input.gemDown {
            holdX += input.drag.dx
            holdY += input.drag.dy
            if !holdAccepted, input.holdMoved || hypot(holdX, holdY) > 8 { dragOnly = true }
            held += Self.step
            if !dragOnly, held >= 0.28 - 1e-8 { holdAccepted = true }
        }
        down = input.gemDown
        previousBarDown = input.barDown
        if input.collect, phase == .before {
            phaseSilent = input.silent
            collect()
        }
        if phase == .before, holdAccepted {
            phaseSilent = input.silent
            changePhase(.collecting)
        }
        if input.replay { phaseSilent = input.silent; replay() }
        if input.tap {
            ping(at: input.touch, silent: input.silent, reduced: reduced)
        }

        let frozen = phase == .hush || phase == .burst
        if !frozen {
            if input.gemDown {
                turn += input.drag.dx / layout.gemRadius
                if input.drag.dy != 0 { tilt = tanh((tilt + input.drag.dy / layout.gemRadius) / 1.1) * 1.1 }
                if input.drag != .zero {
                    turnVelocity = 0
                    tiltVelocity = 0
                }
                turn += turnVelocity * Self.step
                tilt += tiltVelocity * Self.step
                turnVelocity *= exp(-12.25 * Self.step)
                tiltVelocity *= exp(-12.25 * Self.step)
            } else if !reduced {
                if input.release.dx != 0 { turnVelocity = input.release.dx / layout.gemRadius }
                if input.release.dy != 0 { tiltVelocity = input.release.dy / layout.gemRadius }
            }
        }

        flare = 0
        hush = 0
        shock = 0
        beams = 0
        let age = clock - phaseAt
        switch phase {
        case .before:
            break
        case .collecting:
            if reduced {
                charge = clamp(age / 0.5)
                flare = 0.35 * sin(.pi * clamp(age / 0.6))
                value = age >= 0.3 ? 1600 : 1400
                if age < Self.step * 1.5 { setCopy(1) }
                if age >= 0.6 - 1e-8 {
                    hits = Self.orbCount
                    targetCharge = 1
                    setLabel(1)
                    changePhase(.after)
                    emit(Self.eventLand, silent: phaseSilent)
                }
            } else if automatic || holdAccepted {
                cadence += Self.step
                if launched < Self.orbCount, cadence + 1e-8 >= nextLaunch {
                    let n = launched
                    let index = order[n]
                    orbs[index].status = 1
                    orbs[index].launch = clock
                    orbs[index].duration = 0.95 + (0.55 - 0.95) * Double(n) / 19
                    orbs[index].start = orbs[index].position
                    var arc = atan2(orbs[index].target.y, orbs[index].target.x) - atan2(orbs[index].start.y, orbs[index].start.x)
                    if arc > .pi { arc -= Self.tau }
                    if arc < -.pi { arc += Self.tau }
                    orbs[index].arc = arc
                    launched += 1
                    nextLaunch += max(0.05, 0.26 * pow(0.9, Double(n))) + (0.95 - 0.55) / 19
                }
            }
        case .hush:
            let progress = ease(age / 0.32, 0.77, 0, 0.175, 1)
            hush = progress
            scale = hushFromScale + (0.86 - hushFromScale) * progress
            scaleVelocity = 0
            stretch = 1 - 0.06 * progress
            charge = hushFromCharge + (0.35 - hushFromCharge) * progress
            if age >= 0.32 - 1e-8 {
                changePhase(.burst)
                emit(Self.eventBurst, silent: phaseSilent)
                burstAt = clock
                spinFrom = turn
                scale = 0.86
                scaleVelocity = 2.5
                stretch = 1.06
                stretchVelocity = 0
                charge = 1
            }
        case .burst, .after:
            let burstAge = clock - burstAt
            charge = 1
            if !reduced, burstAt >= 0 {
                flare = burstAge < 1.9 ? (burstAge < 0.09 ? burstAge / 0.09 : exp(-(burstAge - 0.09) / 0.42) * clamp((1.9 - burstAge) / 0.2)) : 0
                hush = 1 - clamp(burstAge / 0.15)
                shock = burstAge <= 0.9 ? 1 - pow(1 - clamp(burstAge / 0.9), 3) : 0
                beams = clamp(burstAge / 0.12) * (1 - ease((burstAge - 0.65) / 0.95))
                if burstAge < 1.5 {
                    turn = spinFrom - 0.18 + (Self.tau + 0.18) * ease(burstAge / 1.5, 0.16, 1, 0.3, 1)
                    tilt = -0.25 * sin(.pi * clamp(burstAge / 1.5))
                    turnVelocity = 0
                } else if burstAge < 1.5 + Self.step * 1.1 {
                    turn = spinFrom + Self.tau
                    turnVelocity = 0.35
                }
                if burstAge >= 0.2, copyTarget != 1 { setCopy(1) }
                let landAge = burstAge - 0.25 - 0.16 - 0.5
                if landAge >= 0, !landed {
                    landed = true
                    emit(Self.eventLand, silent: phaseSilent)
                    capsuleScale = 0.94
                    capsuleVelocity = 0
                }
                if landed {
                    value = 1400 + 200 * ease(landAge / 1.1)
                    capsuleGlow = sin(.pi * clamp(landAge / 0.9))
                    capsuleSheen = -1 + 3 * clamp(landAge / 0.9)
                    miniYaw = -0.16 + Self.tau * ease(landAge / 1.1)
                    miniFlare = 0.7 * clamp(landAge / 0.06) * (1 - ease((landAge - 0.06) / 0.5))
                }
                if phase == .burst, burstAge >= 2 - 1e-8 {
                    changePhase(.after)
                    setLabel(1)
                }
            }
        case .rewind:
            charge = rewindCharge * (1 - ease(age / 0.6))
            miniFlare = 0
            value = reduced ? (age < 0.3 ? rewindValue : 1400) : rewindValue + (1400 - rewindValue) * ease(age / 0.6)
            if age >= 0.9 - 1e-8 {
                changePhase(.before)
                setLabel(0)
                launched = 0
                hits = 0
                cadence = 0
                nextLaunch = 0
                targetCharge = 0
                charge = 0
                burstAt = -1
                landed = false
                capsuleGlow = 0
                capsuleSheen = -1
                miniYaw = -0.16
                miniFlare = 0
                firstHitAt = -1
                for index in orbs.indices {
                    orbs[index].status = 0
                    orbs[index].launch = -1
                    orbs[index].hit = -1
                    orbs[index].slot = -1
                }
            }
        }

        if phase == .before || (phase == .collecting && !reduced) {
            charge += (targetCharge - charge) * (1 - exp(-Self.step / 0.25))
        }
        if !down, !(phase == .burst && clock - burstAt < 1.5) {
            Self.spring(value: &turn, velocity: &turnVelocity, target: (turn / Self.tau).rounded() * Self.tau, stiffness: 66.7, damping: frozen ? 24 : 12.25)
            Self.spring(value: &tilt, velocity: &tiltVelocity, target: 0, stiffness: 66.7, damping: frozen ? 24 : 12.25)
        }
        Self.spring(value: &offsetX, velocity: &velocityX, target: 0,
                    stiffness: 420, damping: frozen ? 42 : 20)
        Self.spring(value: &offsetY, velocity: &velocityY, target: 0,
                    stiffness: 420, damping: frozen ? 42 : 20)
        Self.spring(value: &scale, velocity: &scaleVelocity, target: down && !frozen ? 0.97 : 1, stiffness: down ? 420 : 220, damping: down ? 30 : 11)
        if phase != .hush {
            Self.spring(value: &stretch, velocity: &stretchVelocity, target: 1,
                        stiffness: 220, damping: 15)
        }
        updateOrbs(reduced: reduced)
        updateLightsAndSparks()
        Self.spring(value: &tallyScale, velocity: &tallyVelocity, target: 1,
                    stiffness: 500, damping: 22)
        updateBead(reduced: reduced)
        copy = swapProgress(from: copyFrom, target: copyTarget, age: clock - copyAt, reduced: reduced)
        bodyCopy = swapProgress(from: copyFrom, target: copyTarget, age: clock - copyAt - 0.08, reduced: reduced)
        label = swapProgress(from: labelFrom, target: labelTarget, age: clock - labelAt, reduced: reduced)
        let enabled = phase == .before || phase == .after
        barOpacity += ((enabled ? 1.0 : 0.45) - barOpacity) * (1 - exp(-Self.step / 0.055))
        barPress += (((input.barDown && enabled) ? 1.0 : 0.0) - barPress) * (1 - exp(-Self.step / 0.04))
        Self.spring(value: &barScale, velocity: &barVelocity,
                    target: input.barDown && enabled ? 0.97 : 1,
                    stiffness: 420, damping: 24)
        Self.spring(value: &capsuleScale, velocity: &capsuleVelocity,
                    target: 1, stiffness: 420, damping: 22)
        let availableAge = clock - labelAt
        barGlow = phase == .after ? sin(.pi * clamp(availableAge / 0.9)) : 0
        barSheen = phase == .after ? -1 + 3 * clamp(availableAge / 0.9) : -1
    }

    private mutating func updateOrbs(reduced: Bool) {
        let idle = clock.truncatingRemainder(dividingBy: Self.loopDuration)
        for index in orbs.indices {
            let driftX = reduced ? 0 : sin(Self.tau * idle / 6 + Double(index)) * (4 + hash(index) * 5) / layout.gemRadius
            let driftY = reduced ? 0 : sin(Self.tau * idle / 4 + Double(index) * 1.7) * (4 + hash(index + 5) * 5) / layout.gemRadius
            if orbs[index].status == 0 {
                orbs[index].position = orbs[index].home + SIMD3(driftX, driftY, reduced ? 0 : sin(Self.tau * idle / 3 + Double(index)) * 0.035)
            } else if orbs[index].status == 1 {
                let progress = (clock - orbs[index].launch) / orbs[index].duration
                let next = orbPoint(orbs[index], progress: progress)
                orbs[index].angle = atan2(-(next.y - orbs[index].position.y), next.x - orbs[index].position.x)
                orbs[index].position = next
                if progress >= 1 - 1e-8 { strike(index) }
            } else if orbs[index].status == 2, clock - orbs[index].hit >= 0.37 {
                orbs[index].status = 3
                orbs[index].slot = -1
            }
            if phase == .rewind, !reduced {
                let age = clock - phaseAt
                let progress = clamp((age - Double(index) * 0.012) / max(0.01, 0.9 - Double(index) * 0.012))
                let settle = 1 - exp(-7 * progress) * cos(8 * progress)
                let twist = sin(.pi * progress) * 0.055 * (index.isMultiple(of: 2) ? -1.0 : 1.0)
                let home = orbs[index].home
                let target = orbs[index].target
                orbs[index].position = SIMD3(
                    target.x + (home.x + driftX - target.x) * settle - home.y * twist,
                    target.y + (home.y + driftY - target.y) * settle + home.x * twist,
                    target.z + (home.z - target.z) * settle
                )
            }
        }
    }

    private mutating func updateLightsAndSparks() {
        lights = [SIMD4<Float>](repeating: .zero, count: 6)
        for index in orbs.indices where orbs[index].status == 2 && orbs[index].slot < 0 {
            var occupied = 0
            var weakest: Int?
            var strength = 0.01
            for candidate in orbs.indices where orbs[candidate].slot >= 0 {
                occupied += 1
                let orb = orbs[candidate]
                let age = clock - orb.hit
                if orb.status != 2 || age < 0.09 { continue }
                let tail = 2 * (1 - ease((age - 0.09) / 0.28))
                if tail < strength {
                    strength = tail
                    weakest = candidate
                }
            }
            if occupied == 6, let weakest {
                orbs[weakest].slot = -1
                orbs[weakest].status = 3
            }
        }
        var pingPlaced = false
        for slot in 0..<6 {
            var selected = orbs.firstIndex(where: { $0.slot == slot })
            if selected == nil {
                var remaining = max(0.012, max(0.05, 0.26 * pow(0.9, Double(hits))) * 6 - 0.09 - 0.28 - 0.01)
                for index in orbs.indices {
                    let orb = orbs[index]
                    let timeToImpact = orb.launch + orb.duration - clock
                    if (orb.status == 1 || orb.status == 2), orb.slot < 0, timeToImpact < remaining {
                        selected = index
                        remaining = timeToImpact
                    }
                }
                if let selected { orbs[selected].slot = slot }
            }
            if let selected, phase != .rewind {
                let orb = orbs[selected]
                let age = clock - orb.hit
                let absorb = 1 - ease((age - 0.09) / 0.28)
                let factor = orb.status == 2 && age >= 0.09 ? absorb : 1
                let strength: Double
                if orb.status == 2 {
                    strength = age < 0.09 ? 3 * (1 - age / 0.09) : -2 * absorb
                } else {
                    strength = 0.3 + 0.7 * clamp((1.5 - sqrt(orb.position.x * orb.position.x
                        + orb.position.y * orb.position.y + orb.position.z * orb.position.z)) / 0.68)
                }
                lights[slot] = SIMD4(Float(orb.position.x * factor), Float(orb.position.y * factor),
                                     Float(orb.position.z * factor), Float(strength))
            } else if !pingPlaced, clock - pingAt < 0.25 {
                lights[slot] = SIMD4(Float(pingPoint.x), Float(pingPoint.y), 0.85,
                                     Float(2.5 * (1 - (clock - pingAt) / 0.25)))
                pingPlaced = true
            }
        }
        for index in sparks.indices where sparks[index].age < sparks[index].life {
            sparks[index].age += Self.step
            if sparks[index].age >= sparks[index].life { continue }
            sparks[index].vx *= exp(-5 * Self.step)
            sparks[index].vy *= exp(-5 * Self.step)
            sparks[index].x += sparks[index].vx * Self.step
            sparks[index].y += sparks[index].vy * Self.step
        }
    }

    private mutating func updateBead(reduced: Bool) {
        beadAlpha = 0
        guard !reduced, (phase == .burst || phase == .after), burstAt >= 0 else { return }
        let transferAge = clock - burstAt - 0.25
        guard transferAge >= 0 else { return }
        let morph = clamp(transferAge / 0.16)
        tallyScale = 1 - 0.3 * morph
        let flightAge = transferAge - 0.16
        let progress = ease(flightAge / 0.5, 0.77, 0, 0.175, 1)
        let x = layout.gemX + (layout.miniGemX - layout.gemX) * progress + 24 * sin(.pi * progress)
        let y = layout.tallyY + (layout.miniGemY - layout.tallyY) * progress
        let dx = x - beadX
        let dy = y - beadY
        let speed = flightAge > 0 ? hypot(dx, dy) / Self.step : 0
        beadX = x
        beadY = y
        beadSize = 4 + 9 * morph
        beadAlpha = morph * (1 - clamp((flightAge - 0.5) / 0.06))
        beadStretch = 1 + 2.5 * clamp(speed / 850)
        if speed > 0.01 { beadAngle = atan2(dy, dx) }
    }

    private mutating func strike(_ index: Int) {
        orbs[index].status = 2
        orbs[index].hit = clock
        hits += 1
        emit(Self.eventHit, silent: phaseSilent)
        if firstHitAt < 0 { firstHitAt = clock }
        targetCharge = Double(hits) / Double(Self.orbCount)
        turnVelocity += orbs[index].target.x * 1.4
        tiltVelocity -= orbs[index].target.y * 1.4
        let norm = max(0.01, hypot(orbs[index].target.x, orbs[index].target.y))
        velocityX -= 120 * orbs[index].target.x / norm
        velocityY += 120 * orbs[index].target.y / norm
        scaleVelocity += 0.975
        tallyScale = 1.12
        tallyVelocity = 0
        let target = orbs[index].target
        let perspective = 7 / (7 - target.z)
        let impactX = layout.gemX + target.x * layout.gemRadius * perspective
        let impactY = layout.gemY - target.y * layout.gemRadius * perspective
        for sparkIndex in 0..<(4 + index % 2) {
            let angle = atan2(-target.y, target.x) + .pi / 2
                + (hash(index * 7 + sparkIndex) - 0.5) * 1.8
                + (sparkIndex.isMultiple(of: 2) ? 0 : .pi)
            let speed = 120 + hash(index * 9 + sparkIndex) * 140
            if let slot = sparks.firstIndex(where: { $0.age >= $0.life }) {
                sparks[slot] = GemOrbCollectSpark(
                    x: impactX, y: impactY, z: target.z,
                    vx: cos(angle) * speed, vy: sin(angle) * speed,
                    life: 0.25 + hash(index * 4 + sparkIndex) * 0.15,
                    age: 0, size: 3 + hash(index + sparkIndex) * 1.5,
                    tint: orbs[index].tint
                )
            }
        }
        if hits == Self.orbCount {
            hushFromCharge = charge
            hushFromScale = scale
            changePhase(.hush)
        }
    }

    private func orbPoint(_ orb: GemOrbCollectOrb, progress: Double) -> SIMD3<Double> {
        let p = clamp(progress)
        let homeRadius = hypot(orb.start.x, orb.start.y)
        let startAngle = atan2(orb.start.y, orb.start.x)
        if p < 0.14 {
            let push = sin(p / 0.14 * .pi / 2) * 12 / layout.gemRadius
            return SIMD3(orb.start.x + cos(startAngle) * push, orb.start.y + sin(startAngle) * push, orb.start.z)
        }
        let u = (p - 0.14) / 0.86
        let inward = pow(u, 3.5)
        let endRadius = hypot(orb.target.x, orb.target.y)
        let radius = (homeRadius + 12 / layout.gemRadius) * (1 - inward) + endRadius * inward
        let angle = startAngle + orb.arc * u * u
        let point = SIMD3(
            radius * cos(angle),
            radius * sin(angle),
            orb.start.z * (1 - inward) + orb.target.z * inward + sin(.pi * u) * (orb.start.z < 0 ? -0.22 : 0.15)
        )
        let projection = layout.gemRadius * 7 / (7 - point.z)
        let px = point.x * projection
        let py = -point.y * projection
        let halo = orb.size * 7 / (7 - point.z) * 2
        let rx = abs(px) / max(1, layout.gemX - 20 - halo)
        let ry = abs(py) / max(1, py > 0 ? layout.tallyY - 24 - halo - layout.gemY
                                        : layout.gemY - 20 - halo)
        let cap = 1 / pow(1 + pow(rx, 8) + pow(ry, 8), 0.125)
        let weight = clamp((sqrt(point.x * point.x + point.y * point.y + point.z * point.z) - 0.9) / 0.35)
        let fitted = 1 + (cap - 1) * weight * ease(u / 0.35)
        return SIMD3(point.x * fitted, point.y * fitted, point.z)
    }

    private mutating func makeOrbs() {
        orbs = []
        for index in 0..<Self.orbCount {
            let angle = Self.tau * (Double(index) + (hash(index) - 0.5) * 0.38) / Double(Self.orbCount)
            let z = index < 5 ? -1.6 + Double(index) * 0.18 : index > 17 ? 1.85 + Double(index - 18) * 0.3 : 0.12 + hash(index + 52) * 1.32
            let perspective = 7 / (7 - z)
            let distance = 1.45 + hash(index + 20) * 0.9
            let core = index > 17 ? 22 + hash(index + 66) * 6 : 9 + hash(index + 66) * 5
            let margin = 16 + core * 2 + 34
            var px = clamp(cos(angle) * distance * layout.gemRadius * perspective, -layout.gemX + margin, layout.width - layout.gemX - margin)
            var py = clamp(sin(angle) * distance * layout.gemRadius * perspective * 1.12, -(layout.tallyY - layout.gemY - margin - 4), layout.gemY - margin)
            if hypot(px, py) < layout.gemRadius * 1.25, abs(z) <= 1.2 {
                py = sqrt(max(0, pow(layout.gemRadius * 1.29, 2) - px * px))
            }
            if index > 17 {
                px = (index == 18 ? -1 : 1) * (layout.gemX - margin)
                py = layout.gemRadius * (index == 18 ? 1.1 : 1.4)
            }
            let home = SIMD3(px / (layout.gemRadius * perspective), py / (layout.gemRadius * perspective), z)
            let strikeAngle = atan2(home.y, home.x) + (index.isMultiple(of: 2) ? -1 : 1) * (0.87 + hash(index + 80) * 1.05)
            let targetZ = index.isMultiple(of: 6) ? -0.08 : 0.38 + 0.3 * hash(index + 40)
            let targetRadius = sqrt(0.82 * 0.82 - targetZ * targetZ)
            let target = SIMD3(cos(strikeAngle) * targetRadius, sin(strikeAngle) * targetRadius, targetZ)
            orbs.append(GemOrbCollectOrb(home: home, start: home, target: target, position: home, size: core / perspective, tint: index % 3))
        }
        order = []
        var side = 1.0
        while order.count < Self.orbCount {
            var best = -1
            var score = Double.greatestFiniteMagnitude
            for index in orbs.indices where !order.contains(index) {
                let rank = abs(orbs[index].home.z) + (orbs[index].home.x * side < 0 ? 0.32 : 0)
                if rank < score { best = index; score = rank }
            }
            order.append(best)
            side = orbs[best].home.x > 0 ? -1 : 1
        }
    }

    private mutating func sampleRest(reduced: Bool) {
        let idle = 0.0
        for index in orbs.indices {
            orbs[index].position = orbs[index].home + SIMD3(
                reduced ? 0 : sin(Double(index)) * (4 + hash(index) * 5) / layout.gemRadius,
                reduced ? 0 : sin(Double(index) * 1.7) * (4 + hash(index + 5) * 5) / layout.gemRadius,
                reduced ? 0 : sin(Double(index)) * 0.035 + idle
            )
        }
    }

    private func demoInput(from time: Double, seconds: Double, reduced: Bool) -> GemOrbCollectInput {
        let end = time + min(max(seconds, 0), 0.05)
        let epsilon = 1e-8
        var input = GemOrbCollectInput()
        input.gemDown = (end >= 1.2 - epsilon && end < 4.6 - epsilon)
            || (!reduced && end >= 7.2 - epsilon && end < 8.9 - epsilon)
        if !reduced {
            input.drag.dx = 120 * (clamp((end - 7.2) / 0.6) - clamp((time - 7.2) / 0.6))
                - 190 * (clamp((end - 8.2) / 0.7) - clamp((time - 8.2) / 0.7))
            if time < 8.9 - epsilon, end >= 8.9 - epsilon { input.release.dx = -190 / 0.7 }
        }
        input.barDown = end >= 9.6 - epsilon && end < 9.75 - epsilon
        input.replay = time < 9.75 - epsilon && end >= 9.75 - epsilon
        input.silent = true
        return input
    }

    private mutating func changePhase(_ next: GemOrbCollectPhase) {
        phase = next
        phaseAt = clock
    }

    private mutating func emit(_ event: Int, silent: Bool) {
        events |= event
        if !silent { audibleEvents |= event }
    }

    private mutating func setCopy(_ target: Double) {
        copyFrom = copy
        copyTarget = target
        copyAt = clock
    }

    private mutating func setLabel(_ target: Double) {
        labelFrom = label
        labelTarget = target
        labelAt = clock
    }

    private static func spring(value: inout Double, velocity: inout Double, target: Double, stiffness: Double, damping: Double) {
        velocity += (stiffness * (target - value) - damping * velocity) * Self.step
        value += velocity * Self.step
    }

    private func latestHit() -> Double {
        orbs.reduce(-10) { max($0, $1.hit) }
    }
}

struct GemOrbCollectRenderState {
    let layout: GemOrbCollectLayout
    let yaw: Double
    let pitch: Double
    let radius: Double
    let centreX: Double
    let centreY: Double
    let stretch: Double
    let charge: Double
    let flare: Double
    let hush: Double
    let shock: Double
    let beams: Double
    let time: Double
    let phase: GemOrbCollectPhase
    let hits: Int
    let value: Double
    let barPress: Double
    let barGlow: Double
    let barSheen: Double
    let barOpacity: Double
    let barScale: Double
    let capsuleScale: Double
    let capsuleGlow: Double
    let capsuleSheen: Double
    let miniYaw: Double
    let miniFlare: Double
    let ping: SIMD4<Float>
    let orbs: [SIMD4<Float>]
    let lights: [SIMD4<Float>]
}

private func GemOrbCollectHash(_ index: Int) -> Double {
    let number = sin(Double(index) * 127.1 + 43.7) * 43_758.5453
    return number - floor(number)
}

private extension GemOrbCollectSimulation {
    func hash(_ index: Int) -> Double { GemOrbCollectHash(index) }
}

private func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
    min(high, max(low, value))
}

private func ease(_ value: Double, _ x1: Double = 0.23, _ y1: Double = 1, _ x2: Double = 0.32, _ y2: Double = 1) -> Double {
    let progress = clamp(value)
    if progress == 0 || progress == 1 { return progress }
    var low = 0.0
    var high = 1.0
    for _ in 0..<12 {
        let unit = (low + high) / 2
        let inverse = 1 - unit
        let sample = 3 * inverse * inverse * unit * x1 + 3 * inverse * unit * unit * x2 + unit * unit * unit
        if sample < progress { low = unit } else { high = unit }
    }
    let unit = (low + high) / 2
    let inverse = 1 - unit
    return 3 * inverse * inverse * unit * y1 + 3 * inverse * unit * unit * y2 + unit * unit * unit
}

private func swapProgress(from: Double, target: Double, age: Double, reduced: Bool) -> Double {
    from + (target - from) * (reduced ? clamp(age / 0.2) : ease(age / 0.3, 0.77, 0, 0.175, 1))
}
