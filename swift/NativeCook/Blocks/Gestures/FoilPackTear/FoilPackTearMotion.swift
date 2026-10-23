import Foundation

/// Fixed 600 Hz input and spring kernel, translated from the original block.
enum FoilPackTearMotion {
    enum FRAME {
        static let x: Double = 48
        static let y: Double = 128
        static let width: Double = 306
        static let height: Double = 492
        static let radius: Double = 28
        static let band: Double = 12
    }
    enum PACK {
        static let x: Double = 93
        static let y: Double = 176
        static let width: Double = 216
        static let height: Double = 376
        static let sealTop: Double = 26
        static let sealBottom: Double = 24
    }
    enum TEAR {
        static let y: Double = 34
        static let wander: Double = 10
        static let columns: Int = 73
        static let step: Double = 3
        static let notch: Double = 5
    }
    enum CARD {
        static let width: Double = 180
        static let height: Double = 252
        static let radius: Double = 9
        static let insideX: Double = 201
        static let insideY: Double = 384
        static let peekY: Double = 201
        static let revealY: Double = 364
        static let revealScale: Double = 250 / 180
    }
    enum MOTION {
        static let step: Double = 1 / 600
        static let maxElapsed: Double = 0.1
        static let loop: Double = 13
        static let idleSlow: Double = 6.5
        static let idleFast: Double = 13 / 3
        static let resumeDelay: Double = 3.5
        static let catchSpeed: Double = 1400
        static let tearTick: Double = 18
        static let coastTau: Double = 0.18
        static let lightTau: Double = 0.06
        static let tapTravel: Double = 8
        static let tapTime: Double = 0.3
        static let peekDelay: Double = 0.14
        static let newPackAt: Double = 0.38
        static let extractTime: Double = 0.2
        static let extractLift: Double = 20
        static let revealTravel: Double = 0.58
        static let revealPackFade: Double = 0.18
        static let flipAt: Double = 0.34
        static let revealDeadline: Double = 1.5
        static let peekFadeIn: Double = 0.3
        static let peekFadeOut: Double = 0.12
        static let stageKick = (stiffness: 260.0, damping: 16.0)
        static let tug = (stiffness: 300.0, damping: 24.0)
        static let mouth = (stiffness: 220.0, damping: 20.0)
        static let rise = (stiffness: 150.0, damping: 15.0)
        static let sink = (stiffness: 180.0, damping: 20.0)
        static let pullReturn = (stiffness: 260.0, damping: 22.0)
        static let scale = (stiffness: 170.0, damping: 17.0)
        static let flip = (stiffness: 62.0, damping: 10.0)
        static let tilt = (stiffness: 140.0, damping: 12.0)
        static let landing = (stiffness: 190.0, damping: 19.0)
    }
    enum HAPTIC {
        static let grab: Int = 1
        static let tooth: Int = 2
        static let open: Int = 3
        static let peek: Int = 4
        static let reveal: Int = 5
        static let face: Int = 6
        static let collect: Int = 7
        static let land: Int = 8
    }
    static let PEEK_SINK: Double = 16
    final class Pose {
        var lightX: Double = 0
        var lightY: Double = 0
        var swayX: Double = 0
        var swayY: Double = 0
        var calm: Double = 1
        var packX: Double = 0
        var packY: Double = 0
        var packRot: Double = 0
        var packScale: Double = 1
        var packOpacity: Double = 1
        var tearFront: Double = 0
        var tearLine: [Double] = flatTearLine()
        var stripFree: Double = 0
        var stripX: Double = 0
        var stripY: Double = 0
        var stripRot: Double = 0
        var stripOpacity: Double = 1
        var leak: Double = 0
        var burst: Double = 0
        var mouth: Double = 0
        var cardX: Double = CARD.insideX
        var cardY: Double = CARD.insideY
        var cardScale: Double = 1
        var cardFlip: Double = 0
        var cardTiltX: Double = 0
        var cardTiltY: Double = 0
        var cardOpacity: Double = 0
        var sheen: Double = -0.3
        var frameHeat: Double = 0
        var frameRing: Double = 0
        var tabGlow: Double = 0.45
        var sparkleAge: Double = -1
        var dust: Double = 0
    }
    final class State {
        var pose: Pose = Pose()
        var phase: String = "sealed"
        var age: Double = 1
        var clock: Double = 0
        var reduced: Bool = false
        var hold: String = "none"
        var down: Bool = false
        var downAt: Double = 0
        var downX: Double = 0
        var downY: Double = 0
        var fingerX: Double = 0
        var fingerY: Double = 0
        var vx: Double = 0
        var vy: Double = 0
        var travel: Double = 0
        var grabDy: Double = 0
        var catching: Bool = false
        var advanced: Bool = false
        var silent: Bool = false
        var coast: Double = 0
        var autoTear: Bool = false
        var lineEnd: Double = 0
        var lineX: Double = 0
        var lineY: Double = 0
        var idle: Double = 1
        var calmAge: Double = 1
        var peekHit: Bool = false
        var returning: Bool = false
        var pullStart: Double = CARD.peekY
        var peekSettled: Bool = false
        var peekLife: Double = 0
        var stageKick: Double = 0
        var stageVelocity: Double = 0
        var packVX: Double = 0
        var packVY: Double = 0
        var packVR: Double = 0
        var packVS: Double = 0
        var mouthV: Double = 0
        var cardVY: Double = 0
        var cardVS: Double = 0
        var flipV: Double = 0
        var tiltVX: Double = 0
        var tiltVY: Double = 0
        var stripVX: Double = 0
        var stripVY: Double = 0
        var openAge: Double = -1
        var faceAge: Double = -1
        var revealX: Double = CARD.insideX
        var revealY: Double = CARD.peekY
        var revealTiltX: Double = 0
        var revealTiltY: Double = 0
        var collectY: Double = CARD.revealY
        var collectScale: Double = CARD.revealScale
        var pressAge: Double = 0
        var tiltHomeAge: Double = 1
        var newPack: Bool = false
        var events: [Int] = []
        func reset(from other: State) {
            pose = other.pose
            phase = other.phase
            age = other.age
            clock = other.clock
            reduced = other.reduced
            hold = other.hold
            down = other.down
            downAt = other.downAt
            downX = other.downX
            downY = other.downY
            fingerX = other.fingerX
            fingerY = other.fingerY
            vx = other.vx
            vy = other.vy
            travel = other.travel
            grabDy = other.grabDy
            catching = other.catching
            advanced = other.advanced
            silent = other.silent
            coast = other.coast
            autoTear = other.autoTear
            lineEnd = other.lineEnd
            lineX = other.lineX
            lineY = other.lineY
            idle = other.idle
            calmAge = other.calmAge
            peekHit = other.peekHit
            returning = other.returning
            pullStart = other.pullStart
            peekSettled = other.peekSettled
            peekLife = other.peekLife
            stageKick = other.stageKick
            stageVelocity = other.stageVelocity
            packVX = other.packVX
            packVY = other.packVY
            packVR = other.packVR
            packVS = other.packVS
            mouthV = other.mouthV
            cardVY = other.cardVY
            cardVS = other.cardVS
            flipV = other.flipV
            tiltVX = other.tiltVX
            tiltVY = other.tiltVY
            stripVX = other.stripVX
            stripVY = other.stripVY
            openAge = other.openAge
            faceAge = other.faceAge
            revealX = other.revealX
            revealY = other.revealY
            revealTiltX = other.revealTiltX
            revealTiltY = other.revealTiltY
            collectY = other.collectY
            collectScale = other.collectScale
            pressAge = other.pressAge
            tiltHomeAge = other.tiltHomeAge
            newPack = other.newPack
            events = other.events
        }
    }
    final class Demo {
        var time: Double = 0
        var paused: Bool = false
        var realDown: Bool = false
        var quiet: Double = 0
        var segment: Int = 0
        var startFront: Double = 0
        var startX: Double = 0
        var startY: Double = 0
        var grabOffset: Double = 0
        var lastX: Double = 0
        var lastY: Double = 0
        var remainder: Double = 0
    }
    struct Input {
        var type: String
        var x: Double = 0
        var y: Double = 0
        var vx: Double = 0
        var vy: Double = 0
        var silent: Bool = false
        var cancelled: Bool = false
    }
    struct Engine {
        var sim: State
        var demo: Demo
    }
    static func initialTear(_ reduced: Bool = false) -> State {
        let state = State()
        state.reduced = reduced
        idlePose(state, 0)
        return state
    }
    static func initialDemo() -> Demo { Demo() }
    static func copyTear(_ state: State) -> State {
        state.events = []
        return state
    }

    static func flatTearLine() -> [Double] {
        var line: [Double] = []
        do {
            var i: Int = 0
            while i < TEAR.columns {
                line.append(0)
                i += 1
            }
        }
        return line
    }

    static func sampleTearLine() -> [Double] {
        var line: [Double] = []
        do {
            var i: Int = 0
            while i < TEAR.columns {
                let u = Double(i) / Double(TEAR.columns - 1)
                let slow = 5.5 * sin(u * 4.1 + 0.6) - 3.2 * sin(u * 9.3 + 1.9)
                let jitter = 0.9 * sin(Double(i) * 12.9898) * cos(Double(i) * 4.1414)
                line.append(max(-TEAR.wander, min(TEAR.wander, slow + jitter)))
                i += 1
            }
        }
        return line
    }

    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        return max(low, min(high, value))
    }

    static func flatLine() -> [Double] {
        var line: [Double] = []
        do {
            var i: Int = 0
            while i < TEAR.columns {
                line.append(0)
                i += 1
            }
        }
        return line
    }

    static func toward(_ value: Double, _ target: Double, _ amount: Double) -> Double {
        return value + clamp(target - value, -amount, amount)
    }

    static func spring(_ position: Double, _ velocity: Double, _ target: Double, _ stiffness: Double, _ damping: Double, _ dt: Double) -> (position: Double, velocity: Double) {
        let decay = damping / 2
        let frequency = sqrt(stiffness - decay * decay)
        let delta = position - target
        let envelope = exp(-decay * dt)
        let cosine = cos(frequency * dt)
        let sine = sin(frequency * dt)
        return (position: target + envelope * (delta * cosine + (velocity + decay * delta) * sine / frequency), velocity: envelope * (velocity * cosine - (decay * velocity + stiffness * delta) * sine / frequency))
    }

    static func idlePose(_ s: State, _ dt: Double) {
        let p = s.pose
        let resting = !s.down && (s.phase == "sealed" || s.phase == "revealed")
        s.idle = toward(s.idle, (resting ? 1 : 0), dt / ((resting ? 0.5 : 0.15)))
        let slow = 2 * Double.pi * s.clock / MOTION.idleSlow
        let fast = 2 * Double.pi * s.clock / MOTION.idleFast
        p.swayY = (s.reduced ? 0 : s.idle * 0.055 * sin(slow))
        if !s.reduced && (s.stageKick != 0 || s.stageVelocity != 0) {
            let kick = spring(s.stageKick, s.stageVelocity, 0, MOTION.stageKick.stiffness, MOTION.stageKick.damping, dt)
            s.stageKick = kick.position
            s.stageVelocity = kick.velocity
        }
        p.swayX = (s.reduced ? 0 : s.idle * 0.025 * sin(fast + 0.8) + s.stageKick)
        let finger = s.down && s.hold != "none"
        let peekLamp = (s.phase == "peek" ? 0.3 * s.peekLife * sin(2 * Double.pi * s.age / 2.6) : 0)
        let lx = (finger ? clamp((s.fingerX - 201) / 130, -1, 1) : (s.reduced ? 0 : s.idle * 0.25 * sin(slow + 2) + peekLamp))
        let ly = (finger ? clamp((s.fingerY - 364) / 200, -1, 1) : (s.reduced ? 0 : s.idle * 0.15 * sin(fast)))
        if s.phase != "revealing" {
            let blend = 1 - exp(-dt / MOTION.lightTau)
            p.lightX = (!finger && s.idle == 1 ? lx : p.lightX + (lx - p.lightX) * blend)
            p.lightY = (!finger && s.idle == 1 ? ly : p.lightY + (ly - p.lightY) * blend)
        }
        let calmGoal: Double = (s.phase == "sealed" && !s.down ? 1 : 0)
        p.calm = toward(p.calm, calmGoal, dt / ((calmGoal != 0 ? 0.6 : 0.15)))
    }

    static func emit(_ s: State, _ event: Int) {
        if !s.silent {
            s.events.append(event)
        }
    }

    static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if t <= 0 {
            return 0
        }
        if t >= 1 {
            return 1
        }
        var low: Double = 0
        var high: Double = 1
        var u = t
        do {
            var i: Int = 0
            while i < 18 {
                let one = 1 - u
                let x = 3 * one * one * u * x1 + 3 * one * u * u * x2 + u * u * u
                if x < t {
                    low = u
                } else {
                    high = u
                }
                u = (low + high) / 2
                i += 1
            }
        }
        let one = 1 - u
        return 3 * one * one * u * y1 + 3 * one * u * u * y2 + u * u * u
    }

    static func ease(_ t: Double) -> Double {
        return bezier(clamp(t), 0.23, 1, 0.32, 1)
    }

    static func hero(_ t: Double) -> Double {
        return bezier(clamp(t), 0.77, 0, 0.175, 1)
    }

    static func phase(_ s: State, _ next: String) {
        s.phase = next
        s.age = 0
    }

    static func openPack(_ s: State) {
        let p = s.pose
        p.tearFront = PACK.width
        p.stripFree = 1
        s.stripVX = clamp(s.vx * 0.5, 40, 320)
        s.stripVY = -260
        p.burst = (s.reduced ? 0.4 : 1)
        s.openAge = 0
        s.coast = 0
        s.autoTear = false
        s.hold = "none"
        phase(s, "opened")
        emit(s, HAPTIC.open)
    }

    static func advanceFront(_ s: State, _ goal: Double, _ localY: Double) {
        let p = s.pose
        let before = p.tearFront
        let next = clamp(max(before, goal), 0, PACK.width)
        if next <= before {
            return
        }
        let wanted = TEAR.wander * tanh((localY - TEAR.y - s.grabDy) / TEAR.wander)
        let end = clamp(wanted, s.lineEnd - 0.8 * (next - before), s.lineEnd + 0.8 * (next - before))
        do {
            var i: Int = 0
            while i < TEAR.columns {
                let column = Double(i) * TEAR.step
                if column > before && column <= next {
                    let value = s.lineEnd + (end - s.lineEnd) * (column - before) / (next - before)
                    p.tearLine[Int(i)] = clamp(value, p.tearLine[Int(Double(i) - 1)] - 0.8 * TEAR.step, p.tearLine[Int(Double(i) - 1)] + 0.8 * TEAR.step)
                } else {
                    if column > next {
                        p.tearLine[Int(i)] = end
                    }
                }
                i += 1
            }
        }
        s.lineEnd = end
        s.lineX = next
        s.lineY = localY
        p.tearFront = next
        if !s.advanced {
            s.advanced = true
            emit(s, HAPTIC.grab)
        }
        do {
            var tooth: Double = floor(before / MOTION.tearTick) + 1
            while tooth <= floor(next / MOTION.tearTick) {
                emit(s, HAPTIC.tooth)
                tooth += 1
            }
        }
        if next > 6 {
            p.leak = 0.15 + 0.85 * next / PACK.width
        }
        if next >= PACK.width - 2 {
            openPack(s)
        }
    }

    static func reveal(_ s: State) {
        s.revealX = s.pose.cardX
        s.revealY = s.pose.cardY
        s.revealTiltX = s.pose.cardTiltX
        s.revealTiltY = s.pose.cardTiltY
        s.packVY = -60
        s.faceAge = -1
        s.hold = "none"
        phase(s, "revealing")
        emit(s, HAPTIC.reveal)
    }

    static func face(_ s: State) {
        if s.faceAge >= 0 {
            return
        }
        s.faceAge = 0
        s.pose.sparkleAge = (s.reduced ? -1 : 0)
        if !s.reduced {
            s.cardVS += 0.9
            s.stageKick += 0.03
            s.pose.swayX += 0.03
        }
        emit(s, HAPTIC.face)
    }

    static func collect(_ s: State) {
        s.collectY = s.pose.cardY
        s.collectScale = s.pose.cardScale
        s.newPack = false
        s.hold = "none"
        if !s.reduced {
            s.stageKick -= 0.02
            s.pose.swayX -= 0.02
        }
        phase(s, "collecting")
        emit(s, HAPTIC.collect)
    }

    static func inputTear(_ s: State, _ input: Input) {
        let p = s.pose
        if input.type == "activate" {
            s.silent = false
            if s.phase == "sealed" || s.phase == "tearing" {
                s.autoTear = true
                s.grabDy = -s.lineEnd
                s.advanced = false
                phase(s, "tearing")
            } else {
                if s.phase == "peek" {
                    reveal(s)
                } else {
                    if s.phase == "revealed" {
                        collect(s)
                    }
                }
            }
            return
        }
        if input.type == "down" {
            s.down = true
            s.downAt = s.clock
            s.downX = input.x
            s.fingerX = input.x
            s.downY = input.y
            s.fingerY = input.y
            s.vx = 0
            s.vy = 0
            s.travel = 0
            s.silent = input.silent
            s.advanced = false
            s.autoTear = false
            s.hold = "none"
            let frontX = PACK.x + p.tearFront
            if (s.phase == "sealed" || s.phase == "tearing") && input.y >= PACK.y - 24 && input.y <= PACK.y + 96 && input.x >= frontX - 44 && input.x <= frontX + 64 {
                s.hold = "tear"
                s.coast = 0
                s.grabDy = input.y - PACK.y - TEAR.y - s.lineEnd
                s.catching = input.x - PACK.x > p.tearFront + 0.01
                phase(s, "tearing")
            } else {
                if s.phase == "peek" && abs(input.x - p.cardX) <= CARD.width / 2 + 12 && input.y >= p.cardY - CARD.height / 2 - 12 && input.y <= min(p.cardY + CARD.height / 2, PACK.y + TEAR.y + p.packY) + 12 {
                    s.hold = "card"
                    s.pullStart = p.cardY
                    s.returning = true
                    s.cardVY = 0
                } else {
                    if s.phase == "revealed" && abs(input.x - p.cardX) <= CARD.width * p.cardScale / 2 + 12 && abs(input.y - p.cardY) <= CARD.height * p.cardScale / 2 + 12 {
                        s.hold = "tilt"
                        s.pressAge = 0
                        s.tiltVX = 0
                        s.tiltVY = 0
                    } else {
                        if input.x >= FRAME.x && input.x <= FRAME.x + FRAME.width && input.y >= FRAME.y && input.y <= FRAME.y + FRAME.height {
                            s.hold = "look"
                        }
                    }
                }
            }
            return
        }
        if !s.down {
            return
        }
        if input.type == "move" {
            s.fingerX = input.x
            s.fingerY = input.y
            s.vx = input.vx
            s.vy = input.vy
            let dx = input.x - s.downX
            let dy = input.y - s.downY
            s.travel = max(s.travel, hypot(dx, dy))
            if s.hold == "tear" && !s.catching {
                advanceFront(s, input.x - PACK.x, input.y - PACK.y)
            } else {
                if s.hold == "card" {
                    let proposed = s.pullStart + dy
                    p.cardY = (proposed < CARD.peekY - 48 ? CARD.peekY - 48 - 24 * tanh((CARD.peekY - 48 - proposed) / 24) : (proposed > CARD.peekY ? CARD.peekY + 10 * tanh((proposed - CARD.peekY) / 10) : proposed))
                } else {
                    if s.hold == "tilt" && !s.reduced {
                        p.cardTiltY = clamp(dx / 110, -0.32, 0.32)
                        p.cardTiltX = clamp(-dy / 140, -0.26, 0.26)
                    }
                }
            }
            return
        }
        s.down = false
        s.vx = (input.cancelled ? 0 : input.vx)
        s.vy = (input.cancelled ? 0 : input.vy)
        let tap = !input.cancelled && s.travel < MOTION.tapTravel && s.clock - s.downAt < MOTION.tapTime
        if s.hold == "tear" {
            s.coast = (!input.cancelled && s.vx > 900 && p.tearFront > PACK.width * 0.6 ? s.vx : 0)
        } else {
            if s.hold == "card" {
                if !input.cancelled && (CARD.peekY - p.cardY > 24 || s.vy < -500 || tap) {
                    reveal(s)
                } else {
                    s.cardVY = s.vy
                }
            } else {
                if s.hold == "tilt" {
                    s.tiltHomeAge = 0
                    s.tiltVX = clamp(-s.vy / 140, -3, 3)
                    s.tiltVY = clamp(s.vx / 110, -3, 3)
                    if tap {
                        collect(s)
                    }
                }
            }
        }
        s.hold = "none"
    }

    static func freeStrip(_ s: State, _ dt: Double) {
        if s.openAge < 0 || s.phase == "collecting" || s.phase == "sealed" {
            return
        }
        let p = s.pose
        s.openAge += dt
        if s.reduced {
            p.stripOpacity = 1 - clamp(s.openAge / 0.15)
        } else {
            if p.stripOpacity > 0 {
                p.stripX += s.stripVX * dt
                p.stripY += s.stripVY * dt + 1300 * dt * dt
                s.stripVY += 2600 * dt
                p.stripRot += 9 * dt
                p.stripOpacity = 1 - clamp((p.stripY - 380) / 140)
            }
        }
        p.burst = ((s.reduced ? 0.4 : 1)) * exp(-s.openAge / 0.18)
        if p.burst < 0.0001 {
            p.burst = 0
        }
    }

    static func tug(_ s: State, _ dt: Double) {
        let p = s.pose
        let pulling = s.hold == "tear"
        let progress = p.tearFront / PACK.width
        let hanging = s.phase == "tearing"
        let x = (pulling ? 7 * tanh(s.vx / 600) + 4 * progress : (hanging ? 2 * progress : 0))
        let rotation = (pulling ? 0.045 * tanh(s.vx / 500) + 0.015 * progress : (hanging ? 0.008 * progress : 0))
        if s.reduced {
            p.packX = 0
            p.packRot = 0
            return
        }
        let offset = spring(p.packX, s.packVX, x, MOTION.tug.stiffness, MOTION.tug.damping, dt)
        let turn = spring(p.packRot, s.packVR, rotation, MOTION.tug.stiffness, MOTION.tug.damping, dt)
        p.packX = offset.position
        s.packVX = offset.velocity
        p.packRot = turn.position
        s.packVR = turn.velocity
    }

    static func stepPeek(_ s: State, _ dt: Double) {
        let p = s.pose
        p.dust = clamp(s.age / 0.4)
        p.tabGlow = 0.75 + ((s.reduced ? 0 : 0.1 * sin(2 * Double.pi * s.age / 1.2)))
        p.leak += (0.8 - p.leak) * (1 - exp(-dt / 0.12))
        if s.reduced {
            p.packY = PEEK_SINK
            if s.hold != "card" {
                p.cardY = CARD.peekY
            }
            p.cardOpacity = clamp(s.age / 0.2)
        } else {
            p.cardOpacity = 1
            let sink = spring(p.packY, s.packVY, PEEK_SINK, MOTION.sink.stiffness, MOTION.sink.damping, dt)
            p.packY = sink.position
            s.packVY = sink.velocity
            if s.hold != "card" {
                let config = (s.returning ? MOTION.pullReturn : MOTION.rise)
                let rise = spring(p.cardY, s.cardVY, CARD.peekY, config.stiffness, config.damping, dt)
                p.cardY = rise.position
                s.cardVY = rise.velocity
            }
            if !s.peekSettled && !s.down && abs(p.cardY - CARD.peekY) < 0.5 && abs(s.cardVY) < 3 {
                s.peekSettled = true
            }
            let alive = s.peekSettled && !s.down
            s.peekLife = toward(s.peekLife, (alive ? 1 : 0), dt / ((alive ? MOTION.peekFadeIn : MOTION.peekFadeOut)))
            p.cardTiltY = s.peekLife * 0.06 * sin(2 * Double.pi * s.age / 2.6)
            p.cardTiltX = s.peekLife * 0.03 * sin(2 * Double.pi * s.age / 3.4 + 1)
        }
        if !s.peekHit && ((s.reduced ? s.age >= 0.2 : p.cardY <= CARD.peekY)) {
            s.peekHit = true
            emit(s, HAPTIC.peek)
        }
    }

    static func stepReveal(_ s: State, _ dt: Double) {
        let p = s.pose
        let t = s.age
        p.leak = 0.8 * (1 - clamp(t / 0.25))
        if s.reduced {
            p.cardOpacity = (t < 0.16 ? 1 - clamp(t / 0.16) : clamp((t - 0.16) / 0.2))
            p.packOpacity = 1 - clamp(t / 0.2)
            if t >= 0.16 {
                p.cardX = CARD.insideX
                p.cardY = CARD.revealY
                p.cardScale = CARD.revealScale
                p.cardFlip = Double.pi
                face(s)
            }
            if t >= 0.36 {
                phase(s, "revealed")
            }
            return
        }
        let extractedY = CARD.peekY - MOTION.extractLift
        let progress = hero((t - MOTION.extractTime) / MOTION.revealTravel)
        p.cardX = s.revealX + (CARD.insideX - s.revealX) * progress
        p.cardY = (t < MOTION.extractTime ? s.revealY + (extractedY - s.revealY) * ease(t / MOTION.extractTime) : extractedY + (CARD.revealY - extractedY) * progress)
        let peekFade = 1 - clamp(t / MOTION.peekFadeOut)
        p.cardTiltY = s.revealTiltY * peekFade
        p.cardTiltX = s.revealTiltX * peekFade - 0.1 * ease(t / 0.08) * (1 - ease((t - MOTION.extractTime) / 0.22))
        p.packY += s.packVY * dt + 1100 * dt * dt
        s.packVY += 2200 * dt
        p.packRot = -0.22 * ease(t / 0.6)
        p.packOpacity = min(1 - clamp((p.packY - 120) / 140), 1 - clamp((t - MOTION.extractTime) / MOTION.revealPackFade))
        if t >= MOTION.flipAt {
            let scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt)
            p.cardScale = scale.position
            s.cardVS = scale.velocity
        }
        if t >= MOTION.flipAt {
            let turn = spring(p.cardFlip, s.flipV, Double.pi, MOTION.flip.stiffness, MOTION.flip.damping, dt)
            p.cardFlip = turn.position
            s.flipV = turn.velocity
            if p.cardFlip >= Double.pi * 0.92 {
                face(s)
            }
        }
        p.lightX = -0.4 + 0.7 * clamp(p.cardFlip / Double.pi)
        if t >= MOTION.revealDeadline - 1e-9 || (t > MOTION.extractTime + MOTION.revealTravel && abs(p.cardFlip - Double.pi) < 0.005 && abs(s.flipV) < 0.015) {
            p.cardFlip = Double.pi
            p.cardScale = CARD.revealScale
            p.cardY = CARD.revealY
            s.tiltHomeAge = 1
            phase(s, "revealed")
        }
    }

    static func arrival(_ s: State, _ dt: Double) {
        if s.faceAge < 0 || s.phase == "collecting" {
            return
        }
        let p = s.pose
        s.faceAge += dt
        let t = s.faceAge
        p.frameHeat = clamp(t / ((s.reduced ? 0.2 : 0.42)))
        p.frameRing = (s.reduced ? 1 : ease(t / 0.52))
        p.tabGlow = (t < 0.9 ? 1 : 0.85)
        p.sparkleAge = (s.reduced || t > 1.6 ? -1 : t)
        p.sheen = (s.reduced ? -0.3 : -0.3 + 1.6 * clamp((t - 0.08) / 0.7))
    }

    static func stepRevealed(_ s: State, _ dt: Double) {
        let p = s.pose
        s.tiltHomeAge += dt
        let fast = 2 * Double.pi * s.clock / MOTION.idleFast
        let slow = 2 * Double.pi * s.clock / MOTION.idleSlow
        p.cardY = CARD.revealY + ((s.reduced ? 0 : s.idle * 3 * sin(fast)))
        if s.hold == "tilt" {
            s.pressAge += dt
            p.cardScale = CARD.revealScale * (1 - 0.03 * ease(s.pressAge / 0.12))
        } else {
            if s.reduced {
                p.cardTiltX = 0
                p.cardTiltY = 0
                p.cardScale = CARD.revealScale
            } else {
                let tx = s.idle * 0.05 * sin(fast + 1)
                let ty = s.idle * 0.07 * sin(slow)
                let x = spring(p.cardTiltX, s.tiltVX, tx, MOTION.tilt.stiffness, MOTION.tilt.damping, dt)
                let y = spring(p.cardTiltY, s.tiltVY, ty, MOTION.tilt.stiffness, MOTION.tilt.damping, dt)
                p.cardTiltX = x.position
                p.cardTiltY = y.position
                s.tiltVX = x.velocity
                s.tiltVY = y.velocity
                let scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt)
                p.cardScale = scale.position
                s.cardVS = scale.velocity
            }
        }
    }

    static func resetPack(_ s: State) {
        let p = s.pose
        p.tearFront = 0
        p.tearLine = flatLine()
        s.lineEnd = 0
        p.stripFree = 0
        p.stripX = 0
        p.stripY = 0
        p.stripRot = 0
        p.stripOpacity = 1
        p.mouth = 0
        p.leak = 0
        p.burst = 0
        s.openAge = -1
        p.packY = (s.reduced ? 0 : 72)
        p.packScale = (s.reduced ? 1 : 0.94)
        p.packRot = 0
        p.packX = 0
        p.packOpacity = 0
        s.packVY = 0
        s.packVS = 0
        s.packVX = 0
        s.packVR = 0
        s.newPack = true
    }

    static func resetCard(_ s: State) {
        let p = s.pose
        p.cardX = CARD.insideX
        p.cardY = CARD.insideY
        p.cardScale = 1
        p.cardFlip = 0
        p.cardTiltX = 0
        p.cardTiltY = 0
        p.cardOpacity = 0
        p.sheen = -0.3
        p.sparkleAge = -1
    }

    static func stepCollect(_ s: State, _ dt: Double) {
        let p = s.pose
        let t = s.age
        let departureEnd = (s.reduced ? MOTION.newPackAt : 0.54)
        if t < departureEnd {
            if s.reduced {
                p.cardOpacity = 1 - clamp(t / 0.2)
            } else {
                let travel = hero((t - 0.08) / 0.46)
                p.cardY = s.collectY + (-260 - s.collectY) * travel
                p.cardScale = (t < 0.08 ? s.collectScale + (CARD.revealScale * 1.02 - s.collectScale) * ease(t / 0.08) : CARD.revealScale * (1.02 - 0.3 * travel))
                p.cardTiltX = 0.5 * travel
                p.cardOpacity = 1 - clamp((t - 0.4) / 0.14)
                p.sheen = -0.3 + 1.6 * clamp(t / 0.38)
            }
        } else {
            resetCard(s)
        }
        if !s.newPack && t >= MOTION.newPackAt {
            resetPack(s)
        }
        p.frameHeat = 1 - clamp((t - 0.2) / 0.6)
        p.tabGlow = 0.85 - 0.4 * clamp((t - 0.2) / 0.6)
        p.dust = 1 - clamp((t - 0.2) / 0.4)
        if p.frameHeat == 0 {
            p.frameRing = 0
        }
        if s.newPack {
            let age = t - MOTION.newPackAt
            p.packOpacity = clamp(age / 0.22)
            if !s.reduced {
                let y = spring(p.packY, s.packVY, 0, MOTION.landing.stiffness, MOTION.landing.damping, dt)
                let scale = spring(p.packScale, s.packVS, 1, MOTION.landing.stiffness, MOTION.landing.damping, dt)
                p.packY = y.position
                s.packVY = y.velocity
                p.packScale = scale.position
                s.packVS = scale.velocity
            }
            if t >= 1.1 && (s.reduced || (abs(p.packY) < 0.1 && abs(s.packVY) < 1)) {
                let clock = s.clock
                let silent = s.silent
                let events = s.events
                let fresh = initialTear(s.reduced)
                s.reset(from: fresh)
                s.clock = clock
                s.age = 0
                s.silent = silent
                s.events = events
                s.pose.calm = 0
                s.idle = 0
                emit(s, HAPTIC.land)
            }
        }
    }

    static func tickTear(_ s: State, _ dt: Double) {
        s.clock += dt
        s.age += dt
        let p = s.pose
        idlePose(s, dt)
        if s.phase == "sealed" || s.phase == "tearing" {
            p.cardOpacity = 0
            tug(s, dt)
            if s.autoTear {
                s.vx = 420
                advanceFront(s, p.tearFront + 420 * dt, TEAR.y)
            } else {
                if s.hold == "tear" && s.catching {
                    let goal = clamp(s.fingerX - PACK.x, p.tearFront, PACK.width)
                    advanceFront(s, min(goal, p.tearFront + MOTION.catchSpeed * dt), s.fingerY - PACK.y)
                    if p.tearFront >= goal - 0.001 {
                        s.catching = false
                    }
                } else {
                    if s.coast > 0.1 {
                        let decay = exp(-dt / MOTION.coastTau)
                        s.vx = s.coast
                        advanceFront(s, p.tearFront + s.coast * MOTION.coastTau * (1 - decay), s.lineY)
                        s.coast *= decay
                    } else {
                        if !s.down && p.tearFront > 6 {
                            p.leak = 0.15 + 0.85 * p.tearFront / PACK.width + ((s.reduced ? 0 : 0.05 * sin(2 * Double.pi * s.clock / 1.6)))
                        }
                    }
                }
            }
        }
        freeStrip(s, dt)
        if s.phase == "opened" || s.phase == "peek" {
            tug(s, dt)
            if s.reduced {
                p.mouth = clamp(s.openAge / 0.2)
            } else {
                let mouth = spring(p.mouth, s.mouthV, 1, MOTION.mouth.stiffness, MOTION.mouth.damping, dt)
                p.mouth = mouth.position
                s.mouthV = mouth.velocity
            }
            if s.phase == "opened" && s.openAge >= MOTION.peekDelay - 1e-9 {
                phase(s, "peek")
            }
            if s.phase == "peek" {
                stepPeek(s, dt)
            }
        } else {
            if s.phase == "revealing" {
                stepReveal(s, dt)
            } else {
                if s.phase == "revealed" {
                    stepRevealed(s, dt)
                } else {
                    if s.phase == "collecting" {
                        stepCollect(s, dt)
                    }
                }
            }
        }
        arrival(s, dt)
    }

    static func initialEngine(_ reduced: Bool = false) -> Engine {
        return Engine(sim: initialTear(reduced), demo: initialDemo())
    }

    static func lineHeight(_ front: Double) -> Double {
        let at = clamp(front / TEAR.step, 0, Double(TEAR.columns - 1))
        let low = floor(at)
        let high = min(low + 1, Double(TEAR.columns - 1))
        let value = DEMO_LINE[Int(low)] + (DEMO_LINE[Int(high)] - DEMO_LINE[Int(low)]) * (at - low)
        return TEAR.wander * atanh(clamp(value / TEAR.wander, -0.99, 0.99))
    }

    static func segmentAt(_ t: Double) -> Int {
        if t >= 1.4 && t < 2.15 {
            return 1
        }
        if t >= 2.7 && t < 3.55 {
            return 2
        }
        if t >= 5.3 && t < 5.48 {
            return 3
        }
        if t >= 7.2 && t < 10.2 {
            return 4
        }
        if t >= 10.5 && t < 10.56 {
            return 5
        }
        return 0
    }

    static func finger(_ s: State, _ d: Demo, _ dt: Double) {
        let t = d.time
        let segment = segmentAt(t + 1e-9)
        if segment != d.segment {
            if d.segment != 0 && s.down {
                let x = (d.segment == 1 ? PACK.x + PACK.width * 0.46 : (d.segment == 2 ? PACK.x + PACK.width : d.startX))
                let y = (d.segment <= 2 ? PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x) : (d.segment == 3 ? d.startY - 48 : d.startY))
                inputTear(s, Input(type: "move", x: x, y: y, vx: (x - d.lastX) / dt, vy: (y - d.lastY) / dt))
                inputTear(s, Input(type: "up", vx: s.vx, vy: s.vy))
            }
            d.segment = segment
            if segment == 0 {
                return
            }
            if segment == 1 || segment == 2 {
                d.startFront = s.pose.tearFront
                d.startX = PACK.x + d.startFront + ((segment == 1 ? 2 : 0))
                d.startY = PACK.y + TEAR.y + s.lineEnd
                d.grabOffset = d.startY - PACK.y - TEAR.y - s.lineEnd
            } else {
                d.startX = s.pose.cardX
                d.startY = (segment == 3 ? min(s.pose.cardY, PACK.y + TEAR.y - 24) : s.pose.cardY)
            }
            d.lastX = d.startX
            d.lastY = d.startY
            inputTear(s, Input(type: "down", x: d.startX, y: d.startY, silent: true))
        }
        if segment == 0 {
            return
        }
        var x = d.startX
        var y = d.startY
        if segment == 1 {
            let u = clamp((t - 1.4) / 0.75)
            x += (PACK.x + PACK.width * 0.46 - x) * (1 - cos(Double.pi * u)) / 2
            y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x) * clamp(u / 0.12)
        } else {
            if segment == 2 {
                let u = clamp((t - 2.7) / 0.85)
                let remaining = PACK.width - d.startFront
                let power = max(1, 500 * 0.85 / max(remaining, 1))
                x += remaining * pow(u, power)
                y = PACK.y + TEAR.y + d.grabOffset + lineHeight(x - PACK.x)
            } else {
                if segment == 3 {
                    y -= 48 * clamp((t - 5.3) / 0.18)
                } else {
                    if segment == 4 {
                        let angle = 2 * Double.pi * clamp((t - 7.2) / 3)
                        x += 110 * 0.24 * sin(angle)
                        y -= 140 * 0.16 * sin(2 * angle)
                    }
                }
            }
        }
        inputTear(s, Input(type: "move", x: x, y: y, vx: (x - d.lastX) / dt, vy: (y - d.lastY) / dt))
        d.lastX = x
        d.lastY = y
    }

    static func actEngine(_ engine: Engine, _ input: Input) -> Engine {
        let wasPaused = engine.demo.paused
        let sim = copyTear(engine.sim)
        let demo = engine.demo
        demo.paused = true
        demo.quiet = 0
        if !wasPaused && sim.down {
            inputTear(sim, Input(type: "up", vx: 0, vy: 0, cancelled: true))
        }
        demo.segment = 0
        if input.type == "down" {
            demo.realDown = true
        }
        if input.type == "up" {
            demo.realDown = false
        }
        inputTear(sim, input)
        return Engine(sim: sim, demo: demo)
    }

    static func resumeAt(_ s: State) -> Double {
        if s.phase == "sealed" {
            return 0.8
        }
        if s.phase == "tearing" {
            return 2.7
        }
        if s.phase == "peek" {
            return 5
        }
        if s.phase == "revealed" {
            return 9.9
        }
        return -1
    }

    static func stepEngine(_ engine: Engine, _ elapsed: Double, _ autoPlay: Bool) -> Engine {
        let sim = copyTear(engine.sim)
        let demo = engine.demo
        demo.remainder += clamp(elapsed, 0, MOTION.maxElapsed)
        while demo.remainder >= MOTION.step - 1e-9 {
            let dt = MOTION.step
            if demo.paused {
                if !demo.realDown {
                    demo.quiet += dt
                }
                let next = resumeAt(sim)
                if autoPlay && !demo.realDown && demo.quiet >= MOTION.resumeDelay && next >= 0 {
                    demo.paused = false
                    demo.time = next
                    demo.segment = 0
                    sim.clock = next
                }
            }
            if autoPlay && !demo.paused {
                finger(sim, demo, dt)
                tickTear(sim, dt)
                demo.time += dt
                if demo.time >= MOTION.loop - 1e-9 {
                    demo.time = 0
                    demo.segment = 0
                    sim.clock = 0
                }
            } else {
                tickTear(sim, dt)
            }
            demo.remainder = max(0, demo.remainder - dt)
        }
        return Engine(sim: sim, demo: demo)
    }

    static let DEMO_LINE = sampleTearLine()
}
