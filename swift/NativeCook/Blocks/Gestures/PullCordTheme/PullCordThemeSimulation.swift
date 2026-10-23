import Foundation

/// Fixed-step pull-cord physics ported directly from `cord-sim.ts`.
///
/// The kernel has no UI or device dependencies, so the same input stream can be
/// run through the TypeScript and Swift versions for numeric comparison.
enum PullCordThemeSimulation {
    struct Point: Equatable, Sendable {
        var x: Double
        var y: Double
    }

    struct Geometry: Equatable, Sendable {
        let width: Double
        let height: Double
        let scale: Double
        let pivotX: Double
        let pivotY: Double
        let socketTop: Double
        let anchorX: Double
        let anchorY: Double
        let restY: Double
        let length: Double
        let segmentLength: Double
    }

    enum Input: Equatable, Sendable {
        case down(x: Double, y: Double, scripted: Bool = false)
        case move(x: Double, y: Double, velocityX: Double, velocityY: Double)
        case up(velocityX: Double, velocityY: Double, cancelled: Bool = false)
        case toggle
    }

    enum HapticEvent: Int, Equatable, Sendable {
        case grab = 1
        case detent = 2
        case home = 3
        case tick = 4
    }

    struct State: Equatable, Sendable {
        var accumulator = 0.0
        var clock = 0.0

        var positionX: [Double]
        var positionY: [Double]
        var oldPositionX: [Double]
        var oldPositionY: [Double]

        var extensionAmount = 0.0
        var extensionVelocity = 0.0
        var isArmed = true

        /// 0 = none, 1 = bead, 2 = bulb.
        var grab = 0
        var isScripted = false
        var fingerX = 0.0
        var fingerY = 0.0
        var grabDeltaX = 0.0
        var grabDeltaY = 0.0
        var velocityX = 0.0
        var velocityY = 0.0

        var downX = 0.0
        var downY = 0.0
        var downTime = 0.0
        var movedDistance = 0.0

        /// 0 = dark, 1 = light.
        var light = 0
        var revealValue = 0.0
        var revealFrom = 0.0
        var revealTo = 0.0
        var revealTime = 1.0
        var revealDuration = 1.0

        var level = 0.0
        var ignitionTime = -1.0
        var commitTime = 99.0
        var tickIndex = 0

        var bulbDisplacement = 0.0
        var bulbVelocity = 0.0
        var grabAngle = 0.0

        var isDemoRunning: Bool
        var demoTime = 0.0
        var quietTime = 0.0

        var isTapRunning = false
        var tapTime = 0.0

        var reducedMotion: Bool
        var events: [HapticEvent] = []
        var restX: [Double] = []
        var restY: [Double] = []
    }

    static func makeGeometry(width: Double, height: Double) -> Geometry {
        let scale = width / PullCordThemeTokens.stageWidth
        let fit = PullCordThemeTokens.Fit.k * scale
        let socketTop = PullCordThemeTokens.ReplicaArt.socketTopSrc * fit
        let anchorY = PullCordThemeTokens.Cord.top * scale
        let restY = height * PullCordThemeTokens.Cord.restYRatio
        let length = restY - anchorY
        let pointCount = Int(PullCordThemeTokens.Cord.points)

        return Geometry(
            width: width,
            height: height,
            scale: scale,
            pivotX: PullCordThemeTokens.ReplicaArt.centreSrc * fit + PullCordThemeTokens.Fit.cropX * scale,
            pivotY: socketTop - PullCordThemeTokens.ReplicaArt.wire * scale,
            socketTop: socketTop,
            anchorX: width * PullCordThemeTokens.Cord.xRatio,
            anchorY: anchorY,
            restY: restY,
            length: length,
            segmentLength: length / Double(pointCount - 1)
        )
    }

    static func initialState(
        geometry: Geometry,
        reducedMotion: Bool,
        autoPlay: Bool
    ) -> State {
        let pointCount = Int(PullCordThemeTokens.Cord.points)
        var positionX: [Double] = []
        var positionY: [Double] = []
        positionX.reserveCapacity(pointCount)
        positionY.reserveCapacity(pointCount)

        for index in 0..<pointCount {
            positionX.append(geometry.anchorX)
            positionY.append(geometry.anchorY + geometry.segmentLength * Double(index))
        }

        var state = State(
            positionX: positionX,
            positionY: positionY,
            oldPositionX: positionX,
            oldPositionY: positionY,
            isDemoRunning: autoPlay,
            reducedMotion: reducedMotion
        )

        // Settle under gravity once and preserve the exact loop-rest pose.
        for _ in 0..<Int(PullCordThemeMotion.Rope.settleSteps) {
            ropeStep(state: &state, geometry: geometry)
        }
        state.restX = state.positionX
        state.restY = state.positionY
        state.oldPositionX = state.positionX
        state.oldPositionY = state.positionY
        return state
    }

    static func input(state: inout State, geometry: Geometry, input: Input) {
        switch input {
        case .toggle:
            state.isDemoRunning = false
            state.quietTime = 0
            state.isTapRunning = true
            state.tapTime = 0

        case let .down(x, y, scripted):
            state.isScripted = scripted
            if !scripted {
                state.isDemoRunning = false
                state.isTapRunning = false
                state.quietTime = 0
            }

            let lastIndex = Int(PullCordThemeTokens.Cord.points) - 1
            let beadX = state.positionX[lastIndex]
            let beadY = state.positionY[lastIndex]
                + PullCordThemeTokens.Cord.beadHeight * geometry.scale / 2
            let hit = PullCordThemeTokens.Cord.hit * geometry.scale / 2 + 8
            let center = glassCenter(state: state, geometry: geometry)

            if abs(x - beadX) < hit,
               abs(y - beadY) < hit + PullCordThemeTokens.Cord.beadHeight * geometry.scale / 2 {
                state.grab = 1
                state.grabDeltaX = state.positionX[lastIndex] - x
                state.grabDeltaY = state.positionY[lastIndex] - y
                if !scripted {
                    state.events.append(.grab)
                }
            } else if hypot(x - center.x, y - center.y)
                        < PullCordThemeTokens.ReplicaArt.grabRadius * geometry.scale {
                state.grab = 2
                state.grabAngle = atan2(x - geometry.pivotX, y - geometry.pivotY)
                    - bulbAngle(state: state, geometry: geometry)
            } else {
                state.grab = 0
            }

            state.fingerX = x + state.grabDeltaX
            state.fingerY = y + state.grabDeltaY
            state.downX = x
            state.downY = y
            state.downTime = state.clock
            state.movedDistance = 0
            state.velocityX = 0
            state.velocityY = 0

        case let .move(x, y, velocityX, velocityY):
            guard state.grab != 0 else { return }
            state.movedDistance = max(state.movedDistance, hypot(x - state.downX, y - state.downY))
            state.fingerX = x + state.grabDeltaX
            state.fingerY = y + state.grabDeltaY
            state.velocityX = velocityX
            state.velocityY = velocityY

        case let .up(velocityX, velocityY, cancelled):
            if state.grab == 1 {
                let lastIndex = Int(PullCordThemeTokens.Cord.points) - 1
                let isTap = !state.isScripted
                    && !cancelled
                    && state.movedDistance < PullCordThemeMotion.Tap.slop
                    && state.clock - state.downTime < PullCordThemeMotion.Tap.time
                let limit = PullCordThemeMotion.Rope.releaseSpeed
                let clampedVelocityX = clamp(velocityX, lower: -limit, upper: limit)
                let clampedVelocityY = clamp(velocityY, lower: -limit, upper: limit)
                state.oldPositionX[lastIndex] = state.positionX[lastIndex]
                    - clampedVelocityX * PullCordThemeMotion.step
                state.oldPositionY[lastIndex] = state.positionY[lastIndex]
                    - clampedVelocityY * PullCordThemeMotion.step
                state.grab = 0

                if state.isArmed,
                   velocityY > PullCordThemeMotion.Feel.flickSpeed * geometry.scale,
                   state.extensionAmount > PullCordThemeMotion.Feel.flickMin * geometry.scale {
                    toggle(state: &state)
                }
                if isTap {
                    state.isTapRunning = true
                    state.tapTime = 0
                }
            } else if state.grab == 2 {
                let arm = glassArm(geometry: geometry)
                state.bulbVelocity = clamp(
                    velocityX / arm,
                    lower: -PullCordThemeMotion.BulbMotion.maxThrow,
                    upper: PullCordThemeMotion.BulbMotion.maxThrow
                )
                state.grab = 0
            }
            state.isScripted = false
        }
    }

    static func step(
        state: inout State,
        geometry: Geometry,
        frameDelta: Double,
        scriptDelta: Double,
        autoPlay: Bool
    ) {
        let wallDelta = max(0, scriptDelta)
        stepAutoplay(state: &state, geometry: geometry, autoPlay: autoPlay, delta: wallDelta)
        state.clock += wallDelta
        let maximumAccumulation = PullCordThemeMotion.step
            * Double(Int(PullCordThemeMotion.maxSteps))
        state.accumulator = min(state.accumulator + frameDelta, maximumAccumulation)
        while state.accumulator >= PullCordThemeMotion.step {
            fixedStep(state: &state, geometry: geometry)
            state.accumulator -= PullCordThemeMotion.step
        }
    }

    static func glassArm(geometry: Geometry) -> Double {
        geometry.socketTop - geometry.pivotY
            + PullCordThemeTokens.ReplicaArt.glassCenterSrc
            * PullCordThemeTokens.Fit.k
            * geometry.scale
    }

    static func bulbAngle(state: State, geometry: Geometry) -> Double {
        let amplitude = state.reducedMotion
            ? 0
            : PullCordThemeTokens.ReplicaArt.swingSrc
                * PullCordThemeTokens.Fit.k
                * geometry.scale
        return amplitude / glassArm(geometry: geometry)
            * sin(
                2 * Double.pi * state.clock / PullCordThemeMotion.BulbMotion.period
                    + PullCordThemeMotion.replicaSwingPhase
            )
            + state.bulbDisplacement
    }

    static func glassCenter(state: State, geometry: Geometry) -> Point {
        let angle = bulbAngle(state: state, geometry: geometry)
        let arm = glassArm(geometry: geometry)
        return Point(
            x: geometry.pivotX + sin(angle) * arm,
            y: geometry.pivotY + cos(angle) * arm
        )
    }

    static func ignitionValue(state: State) -> Double {
        guard state.ignitionTime >= 0 else { return 1 }
        let times = PullCordThemeMotion.Ignite.times
        let values = PullCordThemeMotion.Ignite.values
        for index in 0..<(times.count - 1) where state.ignitionTime <= times[index + 1] {
            let progress = (state.ignitionTime - times[index]) / (times[index + 1] - times[index])
            return values[index] + (values[index + 1] - values[index]) * progress
        }
        return 1
    }

    static func revealReach(center: Point, geometry: Geometry) -> Double {
        let deltaX = max(center.x, geometry.width - center.x)
        let deltaY = max(center.y, geometry.height - center.y)
        return hypot(deltaX, deltaY) + PullCordThemeMotion.Reveal.margin * geometry.scale
    }

    static func revealEase(_ progress: Double, toLight: Bool) -> Double {
        if toLight {
            return 1 - pow(1 - progress, 5)
        }
        return progress < 0.5
            ? 4 * progress * progress * progress
            : 1 - pow(-2 * progress + 2, 3) / 2
    }

    private static func solveRope(state: inout State, geometry: Geometry) {
        let pointCount = Int(PullCordThemeTokens.Cord.points)
        for _ in 0..<Int(PullCordThemeMotion.Rope.passes) {
            for index in 0..<(pointCount - 1) {
                let next = index + 1
                let deltaX = state.positionX[next] - state.positionX[index]
                let deltaY = state.positionY[next] - state.positionY[index]
                let distance = max(hypot(deltaX, deltaY), 1e-6)
                guard distance > geometry.segmentLength else { continue }
                let firstWeight = index == 0 ? 0.0 : 1.0
                let nextWeight = next == pointCount - 1
                    ? (state.grab == 1 ? 0 : PullCordThemeMotion.Rope.beadWeight)
                    : 1
                let weightSum = firstWeight + nextWeight
                guard weightSum != 0 else { continue }
                let correction = (distance - geometry.segmentLength) / distance / weightSum
                state.positionX[index] += deltaX * correction * firstWeight
                state.positionY[index] += deltaY * correction * firstWeight
                state.positionX[next] -= deltaX * correction * nextWeight
                state.positionY[next] -= deltaY * correction * nextWeight
            }

            // Two links apart stay at least 1.6 links apart to prevent kinks.
            for index in 1..<(pointCount - 2) {
                let next = index + 2
                let deltaX = state.positionX[next] - state.positionX[index]
                let deltaY = state.positionY[next] - state.positionY[index]
                let distance = max(hypot(deltaX, deltaY), 1e-6)
                let minimumDistance = 1.6 * geometry.segmentLength
                guard distance < minimumDistance else { continue }
                let nextWeight = next == pointCount - 1
                    ? (state.grab == 1 ? 0 : PullCordThemeMotion.Rope.beadWeight)
                    : 1
                let correction = ((distance - minimumDistance) / distance / (1 + nextWeight)) * 0.5
                state.positionX[index] += deltaX * correction
                state.positionY[index] += deltaY * correction
                state.positionX[next] -= deltaX * correction * nextWeight
                state.positionY[next] -= deltaY * correction * nextWeight
            }
        }
    }

    private static func ropeStep(state: inout State, geometry: Geometry) {
        let pointCount = Int(PullCordThemeTokens.Cord.points)
        state.positionX[0] = geometry.anchorX
        state.positionY[0] = geometry.anchorY + state.extensionAmount
        state.oldPositionX[0] = state.positionX[0]
        state.oldPositionY[0] = state.positionY[0]

        let gravityDelta = PullCordThemeMotion.Rope.gravity
            * geometry.scale
            * PullCordThemeMotion.step
            * PullCordThemeMotion.step
        let end = state.grab == 1 ? pointCount - 1 : pointCount
        if end > 1 {
            for index in 1..<end {
                let velocityX = (state.positionX[index] - state.oldPositionX[index])
                    * PullCordThemeMotion.Rope.damping
                let velocityY = (state.positionY[index] - state.oldPositionY[index])
                    * PullCordThemeMotion.Rope.damping
                state.oldPositionX[index] = state.positionX[index]
                state.oldPositionY[index] = state.positionY[index]
                state.positionX[index] += velocityX
                state.positionY[index] += velocityY + gravityDelta
            }
        }
        solveRope(state: &state, geometry: geometry)
    }

    private static func startReveal(state: inout State, target: Int) {
        state.revealFrom = state.revealValue
        state.revealTo = Double(target)
        state.revealTime = 0
        let fullDuration: Double
        if state.reducedMotion {
            fullDuration = PullCordThemeMotion.Reveal.reducedFade
        } else if target == 1 {
            fullDuration = PullCordThemeMotion.Reveal.toLight
        } else {
            fullDuration = PullCordThemeMotion.Reveal.toDark
        }
        state.revealDuration = max(0.05, fullDuration * abs(Double(target) - state.revealValue))
    }

    private static func toggle(state: inout State) {
        state.light = 1 - state.light
        state.isArmed = false
        if !state.reducedMotion {
            if state.light == 1 {
                state.revealValue = max(state.revealValue, PullCordThemeMotion.Feel.preview)
            } else {
                state.revealValue = min(state.revealValue, 1 - PullCordThemeMotion.Feel.preview)
            }
        }
        startReveal(state: &state, target: state.light)
        if state.light == 0, !state.reducedMotion {
            state.ignitionTime = 0
        }
        state.commitTime = 0
        if !state.reducedMotion {
            state.bulbVelocity += PullCordThemeMotion.Feel.kick
        }
        if !state.isScripted {
            state.events.append(.detent)
        }
    }

    private static func runScript(
        state: inout State,
        geometry: Geometry,
        track: [[Double]],
        startTime: Double,
        endTime: Double
    ) {
        let beadX = geometry.anchorX
        let beadY = geometry.restY
        for segment in track {
            let start = segment[0]
            let end = segment[1]
            if segment[6] == 1, startTime <= start, start < endTime {
                input(
                    state: &state,
                    geometry: geometry,
                    input: .down(
                        x: beadX + segment[2] * geometry.scale,
                        y: beadY + segment[3] * geometry.scale,
                        scripted: true
                    )
                )
            }
            if endTime > start, startTime < end {
                let progress = easeInOutSine(
                    clamp((min(endTime, end) - start) / (end - start))
                )
                let x = beadX + (segment[2] + (segment[4] - segment[2]) * progress) * geometry.scale
                let y = beadY + (segment[3] + (segment[5] - segment[3]) * progress) * geometry.scale
                if state.isScripted, state.grab != 0 {
                    input(
                        state: &state,
                        geometry: geometry,
                        input: .move(x: x, y: y, velocityX: 0, velocityY: 0)
                    )
                }
            }
            if segment[7] == 1, startTime < end, end <= endTime, state.isScripted {
                input(
                    state: &state,
                    geometry: geometry,
                    input: .up(velocityX: 0, velocityY: 0)
                )
            }
        }
    }

    private static func snapRestIfStill(state: inout State, geometry: Geometry) {
        var worst = abs(state.extensionAmount)
        for index in 0..<Int(PullCordThemeTokens.Cord.points) {
            worst = max(
                worst,
                abs(state.positionX[index] - state.restX[index]),
                abs(state.positionY[index] - state.restY[index])
            )
        }
        guard worst <= PullCordThemeMotion.Rope.snapRest * geometry.scale, state.grab == 0 else {
            return
        }

        for index in 0..<Int(PullCordThemeTokens.Cord.points) {
            state.positionX[index] = state.restX[index]
            state.positionY[index] = state.restY[index]
            state.oldPositionX[index] = state.positionX[index]
            state.oldPositionY[index] = state.positionY[index]
        }
        state.extensionAmount = 0
        state.extensionVelocity = 0
        state.bulbDisplacement = 0
        state.bulbVelocity = 0
        state.level = Double(state.light)
    }

    private static func fixedStep(state: inout State, geometry: Geometry) {
        let delta = PullCordThemeMotion.step
        let pointCount = Int(PullCordThemeTokens.Cord.points)

        let previousExtension = state.extensionAmount
        var pullTarget = 0.0
        if state.grab == 1 {
            var deltaX = state.fingerX - geometry.anchorX
            let sideReach = PullCordThemeMotion.Rope.sideReach * geometry.scale
            if abs(deltaX) > sideReach {
                deltaX = sign(deltaX)
                    * (sideReach + rubber(abs(deltaX) - sideReach, limit: sideReach * 0.5))
            }
            var deltaY = state.fingerY - geometry.anchorY
            let lift = geometry.length - PullCordThemeMotion.Rope.liftReach * geometry.scale
            if deltaY < lift {
                deltaY = lift - rubber(lift - deltaY, limit: 24 * geometry.scale)
            }
            let distance = max(hypot(deltaX, deltaY), 1e-6)
            let over = distance - geometry.length
            var reach = distance
            if over > 0 {
                let linear = PullCordThemeMotion.Switch.linear * geometry.scale
                pullTarget = over < linear
                    ? over
                    : linear + rubber(
                        over - linear,
                        limit: (PullCordThemeMotion.Switch.travel - PullCordThemeMotion.Switch.linear)
                            * geometry.scale
                    )
                reach = geometry.length + pullTarget
            }
            state.oldPositionX[pointCount - 1] = state.positionX[pointCount - 1]
            state.oldPositionY[pointCount - 1] = state.positionY[pointCount - 1]
            state.positionX[pointCount - 1] = geometry.anchorX + deltaX / distance * reach
            state.positionY[pointCount - 1] = geometry.anchorY + deltaY / distance * reach
        }

        if state.grab == 1, pullTarget >= state.extensionAmount {
            state.extensionAmount = pullTarget
            state.extensionVelocity = (state.extensionAmount - previousExtension) / delta
        } else {
            let damping = state.reducedMotion
                ? PullCordThemeMotion.Switch.reducedDamping
                : PullCordThemeMotion.Switch.damping
            state.extensionVelocity += (
                -PullCordThemeMotion.Switch.stiffness * state.extensionAmount
                    - damping * state.extensionVelocity
            ) * delta
            state.extensionAmount += state.extensionVelocity * delta
            if state.extensionAmount < pullTarget {
                state.extensionAmount = pullTarget
                state.extensionVelocity = 0
            }
            if state.extensionAmount < 0 {
                if state.extensionVelocity < -260,
                   !state.isScripted,
                   state.grab == 0,
                   !state.isDemoRunning,
                   !state.isTapRunning {
                    state.events.append(.home)
                }
                state.extensionAmount = 0
                state.extensionVelocity = 0
            }
        }

        ropeStep(state: &state, geometry: geometry)

        if state.extensionAmount < PullCordThemeMotion.Switch.rearm * geometry.scale {
            state.isArmed = true
        }
        if state.isArmed,
           state.extensionAmount >= PullCordThemeMotion.Switch.detent * geometry.scale {
            toggle(state: &state)
        }

        if state.grab == 1 {
            let tick = Int(floor(
                state.extensionAmount / (PullCordThemeMotion.Feel.tick * geometry.scale)
            ))
            if tick != state.tickIndex {
                if tick > state.tickIndex, !state.isScripted {
                    state.events.append(.tick)
                }
                state.tickIndex = tick
            }
        } else {
            state.tickIndex = 0
        }
        state.commitTime += delta

        if state.revealTime < state.revealDuration {
            state.revealTime = min(state.revealDuration, state.revealTime + delta)
            let progress = state.revealTime / state.revealDuration
            let eased = state.reducedMotion
                ? progress
                : revealEase(progress, toLight: state.revealTo == 1)
            state.revealValue = state.revealFrom
                + (state.revealTo - state.revealFrom) * eased
        }

        let pull = clamp(
            state.extensionAmount / (PullCordThemeMotion.Switch.detent * geometry.scale)
        )
        let target = state.isArmed
            ? (state.light == 1 ? 1 - pull : pull)
            : Double(state.light)
        let rate = state.isArmed
            ? PullCordThemeMotion.Level.follow
            : PullCordThemeMotion.Level.fill
        state.level += (target - state.level) * (1 - exp(-rate * delta))

        if state.ignitionTime >= 0 {
            state.ignitionTime += delta
            if let lastTime = PullCordThemeMotion.Ignite.times.last,
               state.ignitionTime > lastTime {
                state.ignitionTime = -1
            }
        }

        let angularFrequency = 2 * Double.pi / PullCordThemeMotion.BulbMotion.period
        if state.grab == 2 {
            let desired = atan2(
                state.fingerX - state.grabDeltaX - geometry.pivotX,
                state.fingerY - state.grabDeltaY - geometry.pivotY
            ) - state.grabAngle
            let idle = bulbAngle(state: state, geometry: geometry) - state.bulbDisplacement
            var displacement = desired - idle
            let maximum = PullCordThemeMotion.BulbMotion.maxAngle
            if abs(displacement) > maximum {
                displacement = sign(displacement)
                    * (maximum + rubber(abs(displacement) - maximum, limit: maximum * 0.4))
            }
            state.bulbVelocity = (displacement - state.bulbDisplacement) / delta
            state.bulbDisplacement = displacement
        } else {
            state.bulbVelocity += (
                -angularFrequency * angularFrequency * state.bulbDisplacement
                    - 2 * PullCordThemeMotion.BulbMotion.damping
                    * angularFrequency
                    * state.bulbVelocity
            ) * delta
            state.bulbDisplacement += state.bulbVelocity * delta
        }
    }

    private static func stepAutoplay(state: inout State, geometry: Geometry, autoPlay: Bool, delta: Double) {
        let loopDuration = PullCordThemeMotion.loopMS / 1_000
        if state.isDemoRunning {
            let startTime = state.demoTime
            var endTime = startTime + delta
            runScript(state: &state, geometry: geometry, track: PullCordThemeMotion.script, startTime: startTime, endTime: endTime)
            if endTime >= loopDuration {
                endTime -= loopDuration
                snapRestIfStill(state: &state, geometry: geometry)
            }
            state.demoTime = endTime
        } else if state.isTapRunning {
            let startTime = state.tapTime
            let endTime = startTime + delta
            runScript(state: &state, geometry: geometry, track: PullCordThemeMotion.tapPull, startTime: startTime, endTime: endTime)
            state.tapTime = endTime
            if let lastEnd = PullCordThemeMotion.tapPull.last?[1], endTime > lastEnd + PullCordThemeMotion.step {
                state.isTapRunning = false
            }
        } else if autoPlay, state.grab == 0 {
            state.quietTime += delta
            if state.quietTime * 1_000 >= PullCordThemeMotion.resumeMS {
                state.isDemoRunning = true
                state.demoTime = state.light == 1 ? PullCordThemeMotion.lightRestAt : 0
            }
        }
    }

    private static func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        value < lower ? lower : value > upper ? upper : value
    }

    private static func rubber(_ over: Double, limit: Double) -> Double {
        limit * (1 - exp(-over / limit))
    }

    private static func easeInOutSine(_ progress: Double) -> Double {
        (1 - cos(Double.pi * progress)) / 2
    }

    private static func sign(_ value: Double) -> Double {
        value < 0 ? -1 : value > 0 ? 1 : 0
    }
}
