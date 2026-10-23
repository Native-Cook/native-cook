import Foundation

enum CassetteDeckMode: Int {
    case stop
    case play
    case fastForward
    case rewind
    case pause

    var accessibilityLabel: String {
        switch self {
        case .stop: "Stopped"
        case .play: "Playing"
        case .fastForward: "Fast forwarding"
        case .rewind: "Rewinding"
        case .pause: "Paused"
        }
    }
}

enum CassetteDeckHaptic {
    case key
    case latch
    case memory
}

private struct CassetteDeckSpring {
    let duration: Double
    let dampingRatio: Double
}

final class CassetteDeckModel {
    static let keyCount = 5
    static let keyRewind = 0
    static let keyPlay = 1
    static let keyFastForward = 2
    static let keyStop = 3
    static let keyPause = 4
    static let tapeStart = 0.18
    static let tapeEnd = 0.97
    static let tapePlaySpeed = 0.01
    static let tapeWindSpeed = 0.12
    static let tapeMotorTime = 0.22
    static let tapeLength = 13_200.0
    static let tapeLatchDepth = 0.78
    static let meterSegments = 12

    var tapePosition = tapeStart
    var tapeSpeed = 0.0
    var mode = CassetteDeckMode.stop
    var heldKey = -1
    var keyDepth = Array(repeating: 0.0, count: keyCount)
    private var keyVelocity = Array(repeating: 0.0, count: keyCount)
    var musicTime = 0.0
    var leftLevel = 0.0
    var rightLevel = 0.0
    var memory = false
    var ghostX = 0.0
    var ghostY = 0.0
    var ghostOpacity = 0.0
    var ghostTargetOpacity = 0.0
    var silent = false
    var events: [CassetteDeckHaptic] = []
    var version = 0
    var moving = true
    let reduceMotion: Bool

    init(reduceMotion: Bool) {
        self.reduceMotion = reduceMotion
    }

    func pressKey(_ index: Int) {
        guard (0..<Self.keyCount).contains(index) else { return }
        heldKey = index
        let previous = mode
        switch index {
        case Self.keyPlay where tapePosition < Self.tapeEnd:
            mode = .play
        case Self.keyFastForward:
            if tapePosition < Self.tapeEnd { mode = .fastForward }
            memory = false
        case Self.keyRewind:
            mode = .rewind
            memory = tapePosition > Self.tapeStart + 0.0005
        case Self.keyStop:
            mode = .stop
            memory = false
        case Self.keyPause:
            if mode == .play { mode = .pause }
            else if mode == .pause { mode = .play }
        default:
            break
        }
        let latching = mode != previous && [.play, .fastForward, .rewind].contains(mode)
        emit(latching ? .latch : .key)
        version += 1
        moving = true
    }

    func releaseKey() {
        heldKey = -1
        moving = true
    }

    func advance(_ seconds: Double) {
        let dt = min(seconds, 0.05)
        var busy = false
        let target: Double
        switch mode {
        case .play: target = Self.tapePlaySpeed
        case .fastForward: target = Self.tapeWindSpeed
        case .rewind: target = -Self.tapeWindSpeed
        case .stop, .pause: target = 0
        }
        let motor = reduceMotion ? 1 : 1 - exp(-dt / Self.tapeMotorTime)
        tapeSpeed += (target - tapeSpeed) * motor
        if abs(tapeSpeed - target) < 1e-5 { tapeSpeed = target }
        tapePosition += tapeSpeed * dt

        if memory && tapePosition <= Self.tapeStart {
            tapePosition = Self.tapeStart
            tapeSpeed = 0
            mode = .stop
            memory = false
            emit(.memory)
            version += 1
        }
        if tapePosition <= 0.02 && tapeSpeed < 0 {
            tapePosition = 0.02
            tapeSpeed = 0
            mode = .stop
            version += 1
        }
        if tapePosition >= Self.tapeEnd && tapeSpeed > 0 {
            tapePosition = Self.tapeEnd
            tapeSpeed = 0
            mode = .stop
            version += 1
        }
        if tapeSpeed != 0 || tapeSpeed != target { busy = true }

        for index in 0..<Self.keyCount {
            let goal = index == heldKey ? 1 : (isLatched(index) ? Self.tapeLatchDepth : 0)
            let spring = reduceMotion
                ? CassetteDeckSpring(duration: 0.160, dampingRatio: 1)
                : (goal > keyDepth[index]
                    ? CassetteDeckSpring(duration: 0.110, dampingRatio: 0.9)
                    : CassetteDeckSpring(duration: 0.220, dampingRatio: 0.55))
            let stepped = Self.springStep(keyDepth[index], keyVelocity[index], goal, dt, spring)
            keyDepth[index] = min(max(stepped.0, 0), 1.02)
            keyVelocity[index] = stepped.1
            if stepped.1 != 0 { busy = true }
        }

        let playing = mode == .play && abs(tapeSpeed - Self.tapePlaySpeed) < Self.tapePlaySpeed * 0.5
        if playing { musicTime += dt }
        let targetLeft = playing ? Self.level(musicTime, 0) : 0
        let targetRight = playing ? Self.level(musicTime, 1) : 0
        leftLevel = followLevel(leftLevel, targetLeft, dt)
        rightLevel = followLevel(rightLevel, targetRight, dt)
        if leftLevel < 0.002 && !playing { leftLevel = 0 }
        if rightLevel < 0.002 && !playing { rightLevel = 0 }
        if leftLevel > 0 || rightLevel > 0 { busy = true }

        ghostOpacity += (ghostTargetOpacity - ghostOpacity) * min(1, dt / 0.08)
        if abs(ghostOpacity - ghostTargetOpacity) < 0.01 { ghostOpacity = ghostTargetOpacity }
        moving = busy || heldKey >= 0 || ghostOpacity != ghostTargetOpacity
    }

    func resetToRest() {
        mode = .stop
        heldKey = -1
        memory = false
        tapeSpeed = 0
        tapePosition = Self.tapeStart
        musicTime = 0
        version += 1
        moving = true
    }

    func runScript(from previous: Double, to current: Double) {
        let presses = [(Self.keyPlay, 0.8, 1.0), (Self.keyFastForward, 4.0, 4.2), (Self.keyRewind, 5.3, 5.5)]
        for (index, down, up) in presses {
            let x = Double(CassetteDeckTheme.keyX(index) + CassetteDeckTheme.keyWidth / 2)
            let y = Double(CassetteDeckTheme.keysY + CassetteDeckTheme.keyTop + 40)
            if Self.crossed(previous, current, down - 0.18) {
                ghostX = x
                ghostY = y
                ghostTargetOpacity = 1
            }
            if Self.crossed(previous, current, down) { pressKey(index) }
            if Self.crossed(previous, current, up) {
                releaseKey()
                ghostTargetOpacity = 0
            }
        }
    }

    static func packRight(_ position: Double) -> Double {
        sqrt(CassetteDeckTheme.packMin * CassetteDeckTheme.packMin + position * packK)
    }

    static func packLeft(_ position: Double) -> Double {
        sqrt(CassetteDeckTheme.packMin * CassetteDeckTheme.packMin + (1 - position) * packK)
    }

    static func reelAngles(_ position: Double) -> (Double, Double) {
        let right = 2 * tapeLength * (packRight(position) - packRight(0)) / packK
        let left = 2 * tapeLength * (packLeft(0) - packLeft(position)) / packK
        return (left, right)
    }

    static func counterAt(_ position: Double) -> Double {
        (reelAngles(position).1 - reelAngles(tapeStart).1) / (Double.pi * 2)
    }

    static func keyAt(x: CGFloat, y: CGFloat) -> Int {
        guard y >= CassetteDeckTheme.keysY - 8,
              y <= CassetteDeckTheme.keysY + CassetteDeckTheme.keysHeight + 8 else { return -1 }
        for index in 0..<keyCount {
            let left = CassetteDeckTheme.keyX(index)
            if x >= left - 4 && x <= left + CassetteDeckTheme.keyWidth + 4 { return index }
        }
        return -1
    }

    private func isLatched(_ index: Int) -> Bool {
        if index == Self.keyPlay { return mode == .play || mode == .pause }
        if index == Self.keyPause { return mode == .pause }
        if index == Self.keyFastForward { return mode == .fastForward }
        if index == Self.keyRewind { return mode == .rewind }
        return false
    }

    private func emit(_ event: CassetteDeckHaptic) {
        if !silent { events.append(event) }
    }

    private func followLevel(_ value: Double, _ target: Double, _ dt: Double) -> Double {
        let time = target > value ? 0.06 : 0.35
        return value + (target - value) * (1 - exp(-dt / time))
    }

    private static let packK = CassetteDeckTheme.packMax * CassetteDeckTheme.packMax
        - CassetteDeckTheme.packMin * CassetteDeckTheme.packMin

    private static func crossed(_ previous: Double, _ current: Double, _ at: Double) -> Bool {
        previous < at && current >= at
    }

    private static func level(_ time: Double, _ channel: Int) -> Double {
        let c = Double(channel)
        let beat = pow(max(0, sin(time * .pi * 2 * 1.9 + c * 0.4)), 6)
        let phrase = 0.5 + 0.22 * sin(time * 0.9 + c * 1.3) + 0.12 * sin(time * 2.7 + c)
        let grain = 0.08 * sin(time * 23 + c * 2.1) * sin(time * 7.3)
        return min(max(phrase + 0.32 * beat + grain, 0), 1)
    }

    private static func springStep(
        _ position: Double,
        _ velocity: Double,
        _ target: Double,
        _ dt: Double,
        _ spring: CassetteDeckSpring
    ) -> (Double, Double) {
        let omega = 2 * Double.pi / spring.duration
        let stiffness = omega * omega
        let damping = 2 * spring.dampingRatio * omega
        let count = max(1, Int(ceil(dt / 0.004)))
        let h = dt / Double(count)
        var x = position
        var v = velocity
        for _ in 0..<count {
            v += (-stiffness * (x - target) - damping * v) * h
            x += v * h
        }
        if abs(x - target) < 0.0008 && abs(v) < 0.01 { return (target, 0) }
        return (x, v)
    }
}

final class CassetteDeckSession {
    private(set) var model: CassetteDeckModel
    private var autoPlay: Bool
    private var paused: Bool
    private var touching = false
    private var quietTime = 0.0
    private var clock = 0.0

    init(reduceMotion: Bool, autoPlay: Bool) {
        model = CassetteDeckModel(reduceMotion: reduceMotion)
        self.autoPlay = autoPlay
        paused = !autoPlay
    }

    func advance(_ seconds: Double) -> Bool {
        let dt = min(seconds, 0.05)
        var changed = model.moving
        if autoPlay {
            if paused {
                if !touching { quietTime += dt }
                if quietTime >= 2 {
                    model.silent = true
                    model.resetToRest()
                    clock = 0
                    paused = false
                    changed = true
                }
            } else {
                model.silent = true
                let previous = clock
                let current = previous + dt
                model.runScript(from: previous, to: current)
                clock = current >= CassetteDeck.loop ? current - CassetteDeck.loop : current
                changed = changed || model.moving
            }
        }
        if model.moving {
            model.advance(dt)
            changed = true
        }
        return changed
    }

    func touchStart(x: CGFloat, y: CGFloat) {
        touching = true
        pauseDemo()
        model.pressKey(CassetteDeckModel.keyAt(x: x, y: y))
    }

    func touchEnd() {
        touching = false
        quietTime = 0
        model.releaseKey()
    }

    func accessibilityPress(_ index: Int) {
        pauseDemo()
        model.pressKey(index)
        model.releaseKey()
    }

    private func pauseDemo() {
        quietTime = 0
        model.silent = false
        guard !paused else { return }
        paused = true
        model.ghostTargetOpacity = 0
        model.releaseKey()
    }
}
