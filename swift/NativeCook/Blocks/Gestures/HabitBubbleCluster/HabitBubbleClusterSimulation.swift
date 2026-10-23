import CoreGraphics
import Foundation

struct HabitBubbleClusterEntity: Identifiable {
    let id: String
    let name: String
    let icon: String
    let hue: String
    let radius: Double
    let home: CGPoint
    let set: Int
    let quiet: Bool
}

struct HabitBubbleClusterSimulation {
    static let entities: [HabitBubbleClusterEntity] = {
        let social = HabitBubbleClusterTokens.social.map {
            HabitBubbleClusterEntity(id: $0.id, name: $0.name, icon: $0.icon, hue: $0.hue, radius: $0.r, home: CGPoint(x: $0.x, y: $0.y), set: 0, quiet: false)
        }
        let quiet = HabitBubbleClusterTokens.quiet
        let dial = HabitBubbleClusterEntity(id: "quiet", name: quiet.title, icon: "moon-outline", hue: "red", radius: quiet.r, home: CGPoint(x: quiet.x, y: quiet.y), set: 0, quiet: true)
        let personal = HabitBubbleClusterTokens.personal.map {
            HabitBubbleClusterEntity(id: $0.id, name: $0.name, icon: $0.icon, hue: $0.hue, radius: $0.r, home: CGPoint(x: $0.x, y: $0.y), set: 1, quiet: false)
        }
        return social + [dial] + personal
    }()

    static let quietIndex = HabitBubbleClusterTokens.social.count

    var x: [Double] = []
    var y: [Double] = []
    var velocityX: [Double] = []
    var velocityY: [Double] = []
    var radius: [Double] = []
    var radiusVelocity: [Double] = []
    var radiusTarget: [Double] = []
    var homeX: [Double] = []
    var homeY: [Double] = []
    var done: [Double] = []
    var doneTarget: [Double] = []
    var flash: [Double] = []
    var pendingRadius: [Double] = []
    var pendingAt: [Double] = []
    var tab = 0
    var focus = -1
    var held = -1
    var mode = 0
    var pressMS = 0.0
    var charge = 0.0
    var tickCount = 0
    var ring = -1
    var fingerX = 0.0
    var fingerY = 0.0
    var offsetX = 0.0
    var offsetY = 0.0
    var startX = 0.0
    var startY = 0.0
    var burst = -1
    var burstMS = 0.0
    var clock = 0.0
    var remainder = 0.0

    init() {
        for entity in Self.entities {
            let visible = entity.set == 0 ? entity.radius : 0
            x.append(entity.home.x)
            y.append(entity.home.y)
            velocityX.append(0)
            velocityY.append(0)
            radius.append(visible)
            radiusVelocity.append(0)
            radiusTarget.append(visible)
            homeX.append(entity.home.x)
            homeY.append(entity.home.y)
            done.append(0)
            doneTarget.append(0)
            flash.append(-1)
            pendingRadius.append(-1)
            pendingAt.append(0)
        }
        packHomes()
        x = homeX
        y = homeY
    }

    mutating func setDone(_ index: Int, _ value: Bool) {
        doneTarget[index] = value ? 1 : 0
        radiusTarget[index] = baseRadius(index, done: value)
        if value {
            flash[index] = 0
            burst = index
            burstMS = 0
            for other in Self.entities.indices where other != index && radiusTarget[other] > 0 && !Self.entities[other].quiet {
                let dx = x[other] - x[index]
                let dy = y[other] - y[index]
                let distance = max(1, hypot(dx, dy))
                let kick = HabitBubbleClusterTokens.wave.speed * exp(-distance / HabitBubbleClusterTokens.wave.reach)
                velocityX[other] += dx / distance * kick
                velocityY[other] += dy / distance * kick
            }
        }
        packHomes()
    }

    mutating func setTab(_ value: Int) {
        guard tab != value else { return }
        tab = value
        for index in Self.entities.indices {
            let target = baseRadius(index, done: doneTarget[index] > 0.5)
            let distance = hypot(Self.entities[index].home.x, Self.entities[index].home.y)
            pendingRadius[index] = target
            pendingAt[index] = clock + distance / HabitBubbleClusterTokens.stagger.per * HabitBubbleClusterTokens.stagger.stepMs
        }
        let before = radiusTarget
        radiusTarget = pendingRadius
        packHomes()
        radiusTarget = before
    }

    mutating func open(_ index: Int, at point: CGPoint) {
        focus = index
        for other in Self.entities.indices {
            radiusTarget[other] = other == index ? HabitBubbleClusterTokens.detail.radius : 0
            guard other != index else { continue }
            let dx = x[other] - x[index]
            let dy = y[other] - y[index]
            let distance = max(1, hypot(dx, dy))
            homeX[other] = x[other] + dx / distance * HabitBubbleClusterTokens.scatter.distance
            homeY[other] = y[other] + dy / distance * HabitBubbleClusterTokens.scatter.distance
        }
        homeX[index] = point.x
        homeY[index] = point.y
    }

    mutating func close() {
        focus = -1
        for index in Self.entities.indices { radiusTarget[index] = baseRadius(index, done: doneTarget[index] > 0.5) }
        packHomes()
    }

    mutating func touchDown(_ point: CGPoint) {
        guard focus < 0 else { held = -1; return }
        held = hitTest(point)
        mode = 0
        pressMS = 0
        charge = 0
        tickCount = 0
        fingerX = point.x
        fingerY = point.y
        startX = point.x
        startY = point.y
        if held >= 0 {
            offsetX = x[held] - point.x
            offsetY = y[held] - point.y
        }
    }

    mutating func touchMove(_ point: CGPoint) {
        fingerX = point.x
        fingerY = point.y
        guard held >= 0, mode != 2 else { return }
        if hypot(point.x - startX, point.y - startY) > HabitBubbleClusterTokens.hold.moveTolerance { mode = 2 }
    }

    mutating func touchUp(velocity: CGVector, reducedMotion: Bool) -> Int {
        let index = held
        held = -1
        guard index >= 0 else { return -1 }
        if mode == 2 {
            if !reducedMotion {
                let speed = hypot(velocity.dx, velocity.dy)
                let scale = speed > HabitBubbleClusterTokens.physics.maxFling ? HabitBubbleClusterTokens.physics.maxFling / speed : 1
                velocityX[index] = velocity.dx * scale
                velocityY[index] = velocity.dy * scale
            }
            return -1
        }
        if pressMS < HabitBubbleClusterTokens.hold.tapMs, doneTarget[index] > 0.5 {
            setDone(index, false)
            return -1
        }
        return pressMS < HabitBubbleClusterTokens.hold.tapMs ? index : -1
    }

    mutating func step(milliseconds: Double, reducedMotion: Bool) -> [Int] {
        var events: [Int] = []
        remainder += min(milliseconds, 100)
        let dt = HabitBubbleClusterTokens.physics.step
        let stepMS = dt * 1000
        while remainder >= stepMS - 0.000_001 {
            remainder -= stepMS
            if held >= 0 && mode != 2 {
                pressMS += stepMS
                if pressMS >= HabitBubbleClusterTokens.hold.delayMs && doneTarget[held] < 0.5 {
                    mode = 1
                    ring = held
                    charge = min(1, charge + stepMS / HabitBubbleClusterTokens.hold.chargeMs)
                    let nextTick = charge >= HabitBubbleClusterTokens.hold.ticks[1] ? 2 : charge >= HabitBubbleClusterTokens.hold.ticks[0] ? 1 : 0
                    if nextTick > tickCount { tickCount = nextTick; events.append(1) }
                    if charge >= 1 {
                        let completed = held
                        setDone(completed, true)
                        held = -1
                        mode = 0
                        events.append(2)
                    }
                }
            } else if charge > 0 && (held < 0 || mode == 2) {
                charge = charge >= 1 ? 0 : max(0, charge - stepMS / HabitBubbleClusterTokens.hold.drainMs)
            }
            for index in Self.entities.indices where pendingRadius[index] >= 0 && clock >= pendingAt[index] {
                radiusTarget[index] = pendingRadius[index]
                pendingRadius[index] = -1
            }
            integrate(dt: dt, stepMS: stepMS, reducedMotion: reducedMotion)
            collide(dt: dt)
            if burst >= 0 {
                burstMS += stepMS
                if burstMS > HabitBubbleClusterTokens.burst.sparkMs { burst = -1 }
            }
        }
        return events
    }

    mutating func advanceClock(milliseconds: Double) {
        clock += max(0, milliseconds)
    }

    private func baseRadius(_ index: Int, done: Bool) -> Double {
        let entity = Self.entities[index]
        guard entity.set == tab else { return 0 }
        return done ? entity.radius * HabitBubbleClusterTokens.hold.doneScale : entity.radius
    }

    private func hitTest(_ point: CGPoint) -> Int {
        var best = -1
        var bestDistance = Double.infinity
        for index in Self.entities.indices where !Self.entities[index].quiet && radiusTarget[index] > 0 {
            let distance = hypot(point.x - x[index], point.y - y[index])
            if distance < max(radius[index], 22), distance < bestDistance { best = index; bestDistance = distance }
        }
        return best
    }

    private mutating func packHomes() {
        var packedX = Self.entities.map(\.home.x)
        var packedY = Self.entities.map(\.home.y)
        let active = Self.entities.indices.filter { radiusTarget[$0] > 0 && !Self.entities[$0].quiet }.count
        let changed = Self.entities.indices.contains { radiusTarget[$0] > 0 && abs(radiusTarget[$0] - Self.entities[$0].radius) > 0.5 }
        let iterations = active > 1 && changed ? Int(HabitBubbleClusterTokens.physics.relaxIterations) : 0
        for _ in 0..<iterations {
            for index in Self.entities.indices where radiusTarget[index] > 0 && !Self.entities[index].quiet {
                packedX[index] -= packedX[index] * HabitBubbleClusterTokens.physics.relaxGravity
                packedY[index] -= packedY[index] * HabitBubbleClusterTokens.physics.relaxGravity
            }
            for first in Self.entities.indices where radiusTarget[first] > 0 {
                guard first + 1 < Self.entities.count else { continue }
                for second in (first + 1)..<Self.entities.count where radiusTarget[second] > 0 {
                    let dx = packedX[second] - packedX[first]
                    let dy = packedY[second] - packedY[first]
                    let distance = max(0.001, hypot(dx, dy))
                    let minimum = radiusTarget[first] + radiusTarget[second] + HabitBubbleClusterTokens.layout.gap
                    guard distance < minimum else { continue }
                    let firstWeight = Self.entities[first].quiet ? 0 : radiusTarget[second] * radiusTarget[second] / (radiusTarget[first] * radiusTarget[first] + radiusTarget[second] * radiusTarget[second])
                    let secondWeight = Self.entities[second].quiet ? 0 : radiusTarget[first] * radiusTarget[first] / (radiusTarget[first] * radiusTarget[first] + radiusTarget[second] * radiusTarget[second])
                    let sum = max(1, firstWeight + secondWeight)
                    let push = (minimum - distance) / distance
                    packedX[first] -= dx * push * firstWeight / sum
                    packedY[first] -= dy * push * firstWeight / sum
                    packedX[second] += dx * push * secondWeight / sum
                    packedY[second] += dy * push * secondWeight / sum
                }
            }
        }
        homeX = packedX.map(Double.init)
        homeY = packedY.map(Double.init)
    }

    private mutating func integrate(dt: Double, stepMS: Double, reducedMotion: Bool) {
        for index in Self.entities.indices {
            let squeeze = index == held && mode != 2 && !reducedMotion ? HabitBubbleClusterTokens.hold.squeeze : 1
            let cycles = HabitBubbleClusterTokens.idle.cyclesMin + Double(index % 3)
            let breathe = reducedMotion || HabitBubbleClusterTokens.qaHold != nil ? 0 : sin(clock / (HabitBubbleClusterTokens.loopMs / cycles) * .pi * 2 + Double(index)) * HabitBubbleClusterTokens.idle.breathe
            let target = radiusTarget[index] * squeeze * (1 + breathe)
            if reducedMotion {
                radius[index] += (target - radius[index]) * min(1, dt * 18)
                radiusVelocity[index] = 0
            } else {
                radiusVelocity[index] += (HabitBubbleClusterTokens.physics.radiusStiffness * (target - radius[index]) - HabitBubbleClusterTokens.physics.radiusDamping * radiusVelocity[index]) * dt
                radius[index] = max(0, radius[index] + radiusVelocity[index] * dt)
            }
            done[index] += (doneTarget[index] - done[index]) * min(1, dt * (reducedMotion ? 14 : 9))
            if flash[index] >= 0 { flash[index] = flash[index] + stepMS > HabitBubbleClusterTokens.burst.flashMs ? -1 : flash[index] + stepMS }
            if index == held && mode == 2 {
                let nextX = fingerX + offsetX
                let nextY = fingerY + offsetY
                velocityX[index] = (nextX - x[index]) / dt
                velocityY[index] = (nextY - y[index]) / dt
                x[index] = nextX
                y[index] = nextY
            } else if reducedMotion {
                x[index] += (homeX[index] - x[index]) * min(1, dt * 18)
                y[index] += (homeY[index] - y[index]) * min(1, dt * 18)
                velocityX[index] = 0
                velocityY[index] = 0
            } else {
                velocityX[index] += (HabitBubbleClusterTokens.physics.stiffness * (homeX[index] - x[index]) - HabitBubbleClusterTokens.physics.damping * velocityX[index]) * dt
                velocityY[index] += (HabitBubbleClusterTokens.physics.stiffness * (homeY[index] - y[index]) - HabitBubbleClusterTokens.physics.damping * velocityY[index]) * dt
                x[index] += velocityX[index] * dt
                y[index] += velocityY[index] * dt
            }
        }
    }

    private mutating func collide(dt: Double) {
        for first in Self.entities.indices where radius[first] > 0.5 {
            guard first + 1 < Self.entities.count else { continue }
            for second in (first + 1)..<Self.entities.count where radius[second] > 0.5 {
                let dx = x[second] - x[first]
                let dy = y[second] - y[first]
                let distance = max(0.001, hypot(dx, dy))
                let designed = abs(radiusTarget[first] - Self.entities[first].radius) < 0.5 && abs(radiusTarget[second] - Self.entities[second].radius) < 0.5 ? hypot(Self.entities[second].home.x - Self.entities[first].home.x, Self.entities[second].home.y - Self.entities[first].home.y) : .infinity
                let minimum = min(radius[first] + radius[second] + HabitBubbleClusterTokens.layout.gap, designed)
                guard distance < minimum else { continue }
                let fixedFirst = (first == held && mode == 2) || Self.entities[first].quiet
                let fixedSecond = (second == held && mode == 2) || Self.entities[second].quiet
                guard !(fixedFirst && fixedSecond) else { continue }
                let firstWeight = fixedFirst ? 0 : fixedSecond ? 1 : radius[second] * radius[second] / (radius[first] * radius[first] + radius[second] * radius[second])
                let secondWeight = fixedSecond ? 0 : fixedFirst ? 1 : radius[first] * radius[first] / (radius[first] * radius[first] + radius[second] * radius[second])
                let push = (minimum - distance) / distance * HabitBubbleClusterTokens.physics.separation
                x[first] -= dx * push * firstWeight; y[first] -= dy * push * firstWeight
                x[second] += dx * push * secondWeight; y[second] += dy * push * secondWeight
                velocityX[first] -= dx * push * firstWeight / dt * 0.25; velocityY[first] -= dy * push * firstWeight / dt * 0.25
                velocityX[second] += dx * push * secondWeight / dt * 0.25; velocityY[second] += dy * push * secondWeight / dt * 0.25
            }
        }
    }
}
