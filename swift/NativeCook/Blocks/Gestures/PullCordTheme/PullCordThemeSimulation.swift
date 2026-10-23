import CoreGraphics

enum PullCordThemeSimulation {
    static let balls = PullCordThemeTokens.Chain.links + 1

    struct Geometry: Equatable {
        let width: Double
        let height: Double
        let scale: Double
        let pivotX: Double
        let pivotY: Double
        let arm: Double
        let segment: Double
        let length: Double
    }

    enum Input {
        case down(x: Double, y: Double, script: Bool = false)
        case move(x: Double, y: Double, velocityX: Double, velocityY: Double, script: Bool = false)
        case up(velocityX: Double, velocityY: Double, cancelled: Bool = false, script: Bool = false)
        case tap(x: Double, y: Double)
        case toggle
    }

    enum Event {
        case click
    }

    final class State {
        var accumulator = 0.0
        var clock = 0.0
        var x: [Double]
        var y: [Double]
        var oldX: [Double]
        var oldY: [Double]
        var travel = 0.0
        var travelVelocity = 0.0
        var armed = true
        var grab = 0
        var grabbedIndex = balls - 1
        var scripted = false
        var fingerX = 0.0
        var fingerY = 0.0
        var grabDX = 0.0
        var grabDY = 0.0
        var lean = 0.0
        var pitch: Double
        var light = 1.0
        var heat = 1.0
        var adaptation = 1.0
        var onTime = 99.0
        var front = 1.0
        var frontFrom = 1.0
        var frontTo = 1.0
        var frontTime = 1.0
        var frontDuration = 1.0
        var frontTurnsOn = true
        var angle = 0.0
        var angularVelocity = 0.0
        var grabAngle = 0.0
        var demoOn: Bool
        var demoTime = 0.0
        var quiet = 0.0
        var tapOn = false
        var tapTime = 0.0
        var tapX = 0.0
        var tapY = 0.0
        let reduced: Bool
        var events: [Event] = []
        var restX: [Double] = []
        var restY: [Double] = []

        init(geometry: Geometry, reduced: Bool, autoPlay: Bool) {
            let foot = switchFoot(geometry, angle: 0)
            x = (0..<Self.count).map { _ in foot.x }
            y = (0..<Self.count).map { foot.y + geometry.segment * Double($0) }
            oldX = x
            oldY = y
            pitch = geometry.segment
            demoOn = autoPlay
            self.reduced = reduced
            for _ in 0..<PullCordThemeTokens.Motion.Rope.settleSteps {
                chainStep(self, geometry)
            }
            restX = x
            restY = y
            oldX = x
            oldY = y
        }

        private static let count = PullCordThemeSimulation.balls
    }

    static func makeGeometry(width: Double, height: Double) -> Geometry {
        let scale = width / PullCordThemeTokens.stageWidth
        let arm = PullCordThemeTokens.Lamp.cord * scale
        return Geometry(
            width: width,
            height: height,
            scale: scale,
            pivotX: PullCordThemeTokens.Lamp.x * scale,
            pivotY: PullCordThemeTokens.Lamp.capTop * scale - arm,
            arm: arm,
            segment: PullCordThemeTokens.Chain.pitch * scale,
            length: Double(PullCordThemeTokens.Chain.links) * PullCordThemeTokens.Chain.pitch * scale
        )
    }

    static func lampPoint(_ geometry: Geometry, angle: Double, localX: Double, localY: Double) -> CGPoint {
        let cosine = cos(angle)
        let sine = sin(angle)
        let y = geometry.arm + localY * geometry.scale
        let x = localX * geometry.scale
        return CGPoint(
            x: geometry.pivotX + x * cosine + y * sine,
            y: geometry.pivotY - x * sine + y * cosine
        )
    }

    static func switchFoot(_ geometry: Geometry, angle: Double) -> CGPoint {
        lampPoint(
            geometry,
            angle: angle,
            localX: PullCordThemeTokens.SwitchBody.x,
            localY: PullCordThemeTokens.SwitchBody.top + PullCordThemeTokens.SwitchBody.height
        )
    }

    static func pullCentre(_ state: State, _ geometry: Geometry) -> CGPoint {
        let index = balls - 1
        return CGPoint(
            x: state.x[index],
            y: state.y[index] + PullCordThemeTokens.Chain.pullHeight * geometry.scale / 2
        )
    }

    static func input(_ state: State, _ geometry: Geometry, _ event: Input) {
        switch event {
        case .toggle:
            state.demoOn = false
            state.quiet = 0
            startTap(state, geometry)

        case let .tap(x, y):
            let centre = pullCentre(state, geometry)
            let hit = PullCordThemeTokens.Chain.hit * geometry.scale / 2
            if abs(x - centre.x) < hit, abs(y - centre.y) < hit {
                state.demoOn = false
                state.quiet = 0
                startTap(state, geometry)
            }

        case let .down(x, y, script):
            state.scripted = script
            if !script {
                state.demoOn = false
                state.tapOn = false
                state.quiet = 0
            }
            let last = balls - 1
            let centre = pullCentre(state, geometry)
            let hit = PullCordThemeTokens.Chain.hit * geometry.scale / 2
            let bulb = lampPoint(geometry, angle: state.angle, localX: 0, localY: PullCordThemeTokens.Lamp.bulbY)
            state.grab = 0
            if abs(x - centre.x) < hit, abs(y - centre.y) < hit {
                state.grab = 1
                state.grabbedIndex = last
            } else {
                var best = hit * 0.8
                if last > 2 {
                    for index in 2..<last {
                        let distance = hypot(x - state.x[index], y - state.y[index])
                        if distance < best {
                            best = distance
                            state.grab = 1
                            state.grabbedIndex = index
                        }
                    }
                }
            }
            if state.grab == 1 {
                state.grabDX = state.x[state.grabbedIndex] - x
                state.grabDY = state.y[state.grabbedIndex] - y
            } else if hypot(x - bulb.x, y - bulb.y) < PullCordThemeTokens.Motion.Swing.grabRadius * geometry.scale {
                state.grab = 2
                state.grabDX = 0
                state.grabDY = 0
                state.grabAngle = atan2(x - geometry.pivotX, y - geometry.pivotY) - state.angle
            }
            state.fingerX = x + state.grabDX
            state.fingerY = y + state.grabDY

        case let .move(x, y, _, _, script):
            if state.scripted, !script { return }
            guard state.grab != 0 else { return }
            state.fingerX = x + state.grabDX
            state.fingerY = y + state.grabDY

        case let .up(velocityX, velocityY, cancelled, script):
            if state.scripted, !script { return }
            if state.grab == 1 {
                let index = state.grabbedIndex
                let limit = PullCordThemeTokens.Motion.Rope.releaseSpeed * geometry.scale
                let vx = clamp(velocityX, -limit, limit)
                let vy = clamp(velocityY, -limit, limit)
                state.grab = 0
                state.oldX[index] = state.x[index] - vx * PullCordThemeTokens.Motion.step
                state.oldY[index] = state.y[index] - vy * PullCordThemeTokens.Motion.step
                if state.armed,
                   !cancelled,
                   vy > PullCordThemeTokens.Motion.Switch.flickSpeed * geometry.scale,
                   state.travel > PullCordThemeTokens.Motion.Switch.flickMinimum * geometry.scale {
                    click(state, geometry)
                }
            } else if state.grab == 2 {
                let divisor = geometry.arm + PullCordThemeTokens.Lamp.bulbY * geometry.scale
                state.angularVelocity = clamp(
                    velocityX / divisor,
                    -PullCordThemeTokens.Motion.Swing.maximumThrow,
                    PullCordThemeTokens.Motion.Swing.maximumThrow
                )
                state.grab = 0
            }
            state.scripted = false
        }
    }

    static func step(_ state: State, _ geometry: Geometry, frameDelta: Double, autoPlay: Bool) {
        let motion = PullCordThemeTokens.Motion.self
        state.accumulator = min(state.accumulator + frameDelta, motion.step * Double(motion.maximumSteps))
        while state.accumulator >= motion.step {
            fixedStep(state, geometry, autoPlay: autoPlay)
            state.accumulator -= motion.step
        }
    }

    static func flicker(_ state: State) -> Double {
        let filament = PullCordThemeTokens.Motion.Filament.self
        guard !state.reduced, state.onTime < filament.flicker else { return 1 }
        let t = state.onTime
        let noise = 0.5 + 0.5 * sin(t * 97) * sin(t * 41 + 1.3)
        return 1 - filament.flickerDepth * noise * (1 - t / filament.flicker)
    }

    private static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        min(max(value, low), high)
    }

    private static func bezier(_ curve: [Double], _ progress: Double) -> Double {
        if progress <= 0 { return 0 }
        if progress >= 1 { return 1 }
        let cx = 3 * curve[0]
        let bx = 3 * (curve[2] - curve[0]) - cx
        let ax = 1 - cx - bx
        let cy = 3 * curve[1]
        let by = 3 * (curve[3] - curve[1]) - cy
        let ay = 1 - cy - by
        var t = progress
        for _ in 0..<8 {
            let x = ((ax * t + bx) * t + cx) * t - progress
            let dx = (3 * ax * t + 2 * bx) * t + cx
            if abs(x) < 0.00001 || abs(dx) < 0.000001 { break }
            t = clamp(t - x / dx)
        }
        return ((ay * t + by) * t + cy) * t
    }

    private static func rubber(_ over: Double, _ limit: Double) -> Double {
        limit * (1 - exp(-over / limit))
    }

    private static func easeInOutSine(_ progress: Double) -> Double {
        (1 - cos(.pi * progress)) / 2
    }

    private static func inverseMass(_ state: State, _ index: Int) -> Double {
        if index == 0 { return 0 }
        if state.grab == 1, index == state.grabbedIndex { return 0 }
        return index == balls - 1 ? PullCordThemeTokens.Motion.Rope.pullWeight : 1
    }

    private static func tether(_ state: State, _ geometry: Geometry, from: Int) {
        let fixedX = state.x[from]
        let fixedY = state.y[from]
        for index in 0..<balls where index != from && inverseMass(state, index) != 0 {
            let maximum = Double(abs(index - from)) * state.pitch
            let dx = state.x[index] - fixedX
            let dy = state.y[index] - fixedY
            let distance = hypot(dx, dy)
            if distance <= maximum { continue }
            state.x[index] = fixedX + dx / distance * maximum
            state.y[index] = fixedY + dy / distance * maximum
        }
    }

    private static func solveChain(_ state: State, _ geometry: Geometry) {
        let segment = state.pitch
        let short = geometry.segment * PullCordThemeTokens.Motion.Rope.slack
        let bend = geometry.segment * PullCordThemeTokens.Motion.Rope.bend
        for _ in 0..<PullCordThemeTokens.Motion.Rope.passes {
            for index in 0..<(balls - 1) {
                let next = index + 1
                let dx = state.x[next] - state.x[index]
                let dy = state.y[next] - state.y[index]
                let distance = max(hypot(dx, dy), 0.000001)
                let wanted: Double
                if distance > segment { wanted = segment }
                else if distance < short { wanted = short }
                else { continue }
                let first = inverseMass(state, index)
                let second = inverseMass(state, next)
                let sum = first + second
                if sum == 0 { continue }
                let correction = (distance - wanted) / distance / sum
                state.x[index] += dx * correction * first
                state.y[index] += dy * correction * first
                state.x[next] -= dx * correction * second
                state.y[next] -= dy * correction * second
            }
            for index in 0..<(balls - 2) {
                let next = index + 2
                let dx = state.x[next] - state.x[index]
                let dy = state.y[next] - state.y[index]
                let distance = max(hypot(dx, dy), 0.000001)
                if distance >= bend { continue }
                let first = inverseMass(state, index)
                let second = inverseMass(state, next)
                let sum = first + second
                if sum == 0 { continue }
                let correction = (distance - bend) / distance / sum * 0.5
                state.x[index] += dx * correction * first
                state.y[index] += dy * correction * first
                state.x[next] -= dx * correction * second
                state.y[next] -= dy * correction * second
            }
            tether(state, geometry, from: 0)
            if state.grab == 1 { tether(state, geometry, from: state.grabbedIndex) }
        }
    }

    private static func chainStep(_ state: State, _ geometry: Geometry) {
        let foot = switchFoot(geometry, angle: state.angle)
        let anchorX = foot.x + sin(state.angle) * state.travel
        let anchorY = foot.y + cos(state.angle) * state.travel
        state.oldX[0] = state.x[0]
        state.oldY[0] = state.y[0]
        state.x[0] = anchorX
        state.y[0] = anchorY
        let gravity = PullCordThemeTokens.Motion.Rope.gravity * geometry.scale
            * PullCordThemeTokens.Motion.step * PullCordThemeTokens.Motion.step
        for index in 1..<balls where inverseMass(state, index) != 0 {
            let vx = (state.x[index] - state.oldX[index]) * PullCordThemeTokens.Motion.Rope.damping
            let vy = (state.y[index] - state.oldY[index]) * PullCordThemeTokens.Motion.Rope.damping
            state.oldX[index] = state.x[index]
            state.oldY[index] = state.y[index]
            state.x[index] += vx
            state.y[index] += vy + gravity
        }
        solveChain(state, geometry)
    }

    private static func startFront(_ state: State, to target: Double) {
        state.frontFrom = state.front
        state.frontTo = target
        state.frontTurnsOn = target == 1
        let span = abs(target - state.front)
        if state.reduced {
            state.frontDuration = max(0.05, PullCordThemeTokens.Motion.Front.reduced * span)
            state.frontTime = 0
        } else {
            state.frontDuration = max(0.05, (target == 1 ? PullCordThemeTokens.Motion.Front.on : PullCordThemeTokens.Motion.Front.off) * span)
            state.frontTime = target == 1 ? -PullCordThemeTokens.Motion.Front.onDelay : 0
        }
    }

    private static func click(_ state: State, _ geometry: Geometry) {
        state.light = 1 - state.light
        state.armed = false
        if state.light == 1 { state.onTime = 0 }
        startFront(state, to: state.light)
        if !state.reduced {
            state.angularVelocity += PullCordThemeTokens.Motion.Swing.kick
            let last = balls - 1
            for index in 1...last where inverseMass(state, index) != 0 {
                let kick = sin(.pi * Double(index) / Double(last))
                    * PullCordThemeTokens.Motion.Switch.kick * geometry.scale
                state.oldX[index] -= kick
                state.oldY[index] += kick * 0.3
            }
        }
        if !state.scripted || state.tapOn { state.events.append(.click) }
    }

    private static func restCentre(_ state: State, _ geometry: Geometry) -> CGPoint {
        let index = balls - 1
        return CGPoint(
            x: state.restX[index],
            y: state.restY[index] + PullCordThemeTokens.Chain.pullHeight * geometry.scale / 2
        )
    }

    private static func startTap(_ state: State, _ geometry: Geometry) {
        let current = pullCentre(state, geometry)
        let rest = restCentre(state, geometry)
        state.tapOn = true
        state.tapTime = 0
        state.tapX = (current.x - rest.x) / geometry.scale
        state.tapY = (current.y - rest.y) / geometry.scale
    }

    private static func runScript(
        _ state: State,
        _ geometry: Geometry,
        track: [PullCordThemeTokens.ScriptSegment],
        from startTime: Double,
        to endTime: Double,
        startX: Double,
        startY: Double
    ) {
        let rest = restCentre(state, geometry)
        for (index, segment) in track.enumerated() {
            let sx = index == 0 ? startX : segment.fromX
            let sy = index == 0 ? startY : segment.fromY
            if segment.begins, startTime <= segment.start, segment.start < endTime {
                input(state, geometry, .down(
                    x: rest.x + sx * geometry.scale,
                    y: rest.y + sy * geometry.scale,
                    script: true
                ))
            }
            if endTime > segment.start, startTime < segment.end {
                let progress = easeInOutSine(clamp((min(endTime, segment.end) - segment.start) / (segment.end - segment.start)))
                let x = rest.x + (sx + (segment.toX - sx) * progress) * geometry.scale
                let y = rest.y + (sy + (segment.toY - sy) * progress) * geometry.scale
                if state.scripted, state.grab != 0 {
                    input(state, geometry, .move(x: x, y: y, velocityX: 0, velocityY: 0, script: true))
                }
            }
            if segment.ends, startTime < segment.end, segment.end <= endTime, state.scripted {
                input(state, geometry, .up(velocityX: 0, velocityY: 0, script: true))
            }
        }
    }

    private static func snapRestIfStill(_ state: State, _ geometry: Geometry) {
        var worst = abs(state.travel)
        for index in 0..<balls {
            worst = max(worst, abs(state.x[index] - state.restX[index]), abs(state.y[index] - state.restY[index]))
        }
        if worst > PullCordThemeTokens.Motion.Rope.snapRest * geometry.scale || state.grab != 0 { return }
        for index in 0..<balls {
            state.x[index] = state.restX[index]
            state.y[index] = state.restY[index]
            state.oldX[index] = state.x[index]
            state.oldY[index] = state.y[index]
        }
        state.travel = 0
        state.travelVelocity = 0
        state.angle = 0
        state.angularVelocity = 0
        state.lean = 0
        state.heat = state.light
        state.adaptation = state.light
        state.front = state.light
        state.frontTime = state.frontDuration
    }

    private static func hangStraight(_ state: State, _ geometry: Geometry) {
        let foot = switchFoot(geometry, angle: 0)
        for index in 0..<balls {
            state.x[index] = foot.x
            state.y[index] = foot.y + state.travel + geometry.segment * Double(index)
            state.oldX[index] = state.x[index]
            state.oldY[index] = state.y[index]
        }
    }

    private static func follow(_ state: State, _ geometry: Geometry) -> Double {
        let foot = switchFoot(geometry, angle: state.angle)
        let reach = Double(state.grabbedIndex) * geometry.segment
        let dx = state.fingerX - foot.x
        var dy = state.fingerY - foot.y
        let top = reach * (1 - PullCordThemeTokens.Motion.Rope.lift)
        if dy < top { dy = top - rubber(top - dy, 16 * geometry.scale) }
        let distance = max(hypot(dx, dy), 0.000001)
        let over = distance - reach
        var hold = 0.0
        var give = 0.0
        if over > 0 {
            let travel = PullCordThemeTokens.Motion.Switch.travel * geometry.scale
            hold = min(over, travel)
            if over > travel {
                give = rubber(over - travel, PullCordThemeTokens.Motion.Switch.stretch * geometry.scale)
            }
            if !state.armed { hold = max(0, hold - PullCordThemeTokens.Motion.Switch.give * geometry.scale) }
            state.lean = dx / distance
        } else {
            state.lean = 0
        }
        let radius = over > 0 ? reach + hold + give : distance
        state.pitch = geometry.segment + give / Double(max(1, state.grabbedIndex))
        let index = state.grabbedIndex
        state.oldX[index] = state.x[index]
        state.oldY[index] = state.y[index]
        state.x[index] = foot.x + dx / distance * radius
        state.y[index] = foot.y + dy / distance * radius
        return hold
    }

    private static func fixedStep(_ state: State, _ geometry: Geometry, autoPlay: Bool) {
        let delta = PullCordThemeTokens.Motion.step
        if state.demoOn {
            let start = state.demoTime
            var end = start + delta
            let first = PullCordThemeTokens.autoplayScript[0]
            runScript(
                state, geometry, track: PullCordThemeTokens.autoplayScript,
                from: start, to: end, startX: first.fromX, startY: first.fromY
            )
            if end >= PullCordThemeTokens.Motion.loop {
                end -= PullCordThemeTokens.Motion.loop
                snapRestIfStill(state, geometry)
            }
            state.demoTime = end
        } else if state.tapOn {
            let start = state.tapTime
            let end = start + delta
            runScript(
                state, geometry, track: PullCordThemeTokens.tapScript,
                from: start, to: end, startX: state.tapX, startY: state.tapY
            )
            state.tapTime = end
            if let last = PullCordThemeTokens.tapScript.last, end > last.end + delta { state.tapOn = false }
        } else if autoPlay, state.grab == 0 {
            state.quiet += delta
            if state.quiet >= PullCordThemeTokens.Motion.resume {
                state.demoOn = true
                state.demoTime = state.light == 0 ? PullCordThemeTokens.Motion.nightRestAt : 0
            }
        }

        if state.grab == 1 {
            let previous = state.travel
            state.travel = follow(state, geometry)
            state.travelVelocity = (state.travel - previous) / delta
        } else {
            state.lean = 0
            state.pitch = geometry.segment
            let frequency = 2 * Double.pi / PullCordThemeTokens.Motion.Switch.springDuration
            state.travelVelocity += (
                -frequency * frequency * state.travel
                    - 2 * PullCordThemeTokens.Motion.Switch.springDampingRatio * frequency * state.travelVelocity
            ) * delta
            state.travel += state.travelVelocity * delta
            if state.travel < 0 || state.reduced {
                state.travel = 0
                state.travelVelocity = 0
            }
        }
        if state.travel < PullCordThemeTokens.Motion.Switch.rearm * geometry.scale { state.armed = true }
        if state.armed, state.travel >= PullCordThemeTokens.Motion.Switch.detent * geometry.scale { click(state, geometry) }

        if state.reduced, state.grab != 1 { hangStraight(state, geometry) }
        else { chainStep(state, geometry) }

        let filament = PullCordThemeTokens.Motion.Filament.self
        let heatTime = state.reduced ? filament.reduced : (state.light == 1 ? filament.warmUp : filament.coolDown)
        state.heat += (state.light - state.heat) * (1 - exp(-delta / heatTime))
        let adaptationTime = state.reduced ? filament.reduced : PullCordThemeTokens.Light.adaptTime
        state.adaptation += (state.light - state.adaptation) * (1 - exp(-delta / adaptationTime))
        state.onTime += delta
        if state.frontTime < state.frontDuration {
            state.frontTime = min(state.frontDuration, state.frontTime + delta)
            let progress = clamp(state.frontTime / state.frontDuration)
            let curve = state.frontTurnsOn || state.reduced
                ? PullCordThemeTokens.Motion.Front.easeOut
                : PullCordThemeTokens.Motion.Front.easeInOut
            let eased = bezier(curve, progress)
            state.front = state.frontFrom + (state.frontTo - state.frontFrom) * eased
        }

        if state.reduced {
            state.angle = 0
            state.angularVelocity = 0
        } else if state.grab == 2 {
            let wanted = atan2(state.fingerX - geometry.pivotX, state.fingerY - geometry.pivotY) - state.grabAngle
            var angle = wanted
            let maximum = PullCordThemeTokens.Motion.Swing.maximumAngle
            if abs(angle) > maximum {
                angle = angle.sign == .minus ? -(maximum + rubber(abs(angle) - maximum, maximum * 0.4))
                    : maximum + rubber(abs(angle) - maximum, maximum * 0.4)
            }
            state.angularVelocity = (angle - state.angle) / delta
            state.angle = angle
        } else {
            let frequency = 2 * Double.pi / PullCordThemeTokens.Motion.Swing.period
            let rest = PullCordThemeTokens.Motion.Swing.couple * state.lean
            state.angularVelocity += (
                -frequency * frequency * (state.angle - rest)
                    - 2 * PullCordThemeTokens.Motion.Swing.dampingRatio * frequency * state.angularVelocity
            ) * delta
            state.angle += state.angularVelocity * delta
        }
        state.clock += delta
    }
}
