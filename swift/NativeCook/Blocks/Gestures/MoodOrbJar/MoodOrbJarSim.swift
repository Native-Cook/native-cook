import Foundation

/// Fixed-step simulation of the jar: a pile of orbs that keep their slots, one orb thrown on a solved
/// arc that lands with real collisions, the lid's hinge and the undo swipe (jar-sim.ts). A finger and
/// the autoplay script share `apply(_:geometry:)`. Runs on the main thread, one step every 1/120 s.
struct MoodOrbJarSim {
    typealias Tokens = MoodOrbJarTokens
    typealias Motion = MoodOrbJarTokens.Motion

    static let poolCount = Tokens.poolCount
    static let pileCount = Tokens.pileCount

    var accumulator = 0.0
    var clock = 0.0
    var orbs: [MoodOrbJarOrb] = []

    // The finger.
    var held = -1
    var grabOffsetX = 0.0
    var grabOffsetY = 0.0
    var downX = 0.0
    var downY = 0.0
    var downTime = 0.0
    var moved = 0.0
    var swiping = false
    var swipeStartX = 0.0
    var rowX = 0.0
    var rowVelocity = 0.0
    var lastAdded = -1

    // The lid and the picker.
    var lid = 0.0
    var lidVelocity = 0.0
    var lidHold = 0.0
    var slotScale = [Double](repeating: 1, count: MoodOrbJarTokens.moods.count)

    // Autoplay.
    var scripted = false
    var demoOn: Bool
    var demoTime = 0.0
    var quiet = 0.0

    var reduced: Bool
    var events: [MoodOrbJarEvent] = []
    /// Phone tilt, -1 ... 1.
    var tilt = 0.0

    // Shared element, landing ripple and the new card's growth.
    var ghostTime = -1.0
    var ghostX = 0.0
    var ghostY = 0.0
    var ghostMood = 0
    /// -1 done, -2 waiting for the orb to arrive, otherwise seconds since the card started to grow.
    var growTime = -1.0
    var rippleTime = -1.0
    var rippleX = 0.0
    var rippleY = 0.0
    var rippleMood = 0
    var hopIndex = 0

    /// A seeded pile, dropped and settled once; that pose is its home.
    init(geometry g: MoodOrbJarGeometry, reduced: Bool, autoPlay: Bool) {
        self.reduced = reduced
        self.demoOn = autoPlay

        let r = MoodOrbJarTokens.Layout.orb * g.scale
        for i in 0..<Self.poolCount {
            let isPile = i < Self.pileCount
            let column = i % 5
            let row = i / 5
            var orb = MoodOrbJarOrb()
            orb.x = g.centerX + Double(column - 2) * r * 2.2 + Double(row % 2) * r * 0.6
            orb.y = g.floor - r - Double(row) * r * 2.4 - 20 * g.scale
            orb.mood = Tokens.pileMoods[i % Tokens.pileMoods.count]
            orb.state = isPile ? .settling : .off
            orb.fade = isPile ? 1 : 0
            orbs.append(orb)
        }
        for _ in 0..<Motion.Pile.settleSteps { stepJar(g, settleAll: true) }
        // Let the dropped pile find a stable spring pose. Taking each settled result as the next
        // home prevents a one-point pop when autoplay wraps to its seam.
        for _ in 0..<4 {
            for i in 0..<Self.pileCount {
                orbs[i].state = .pile
                orbs[i].homeX = orbs[i].x
                orbs[i].homeY = orbs[i].y
                orbs[i].vx = 0
                orbs[i].vy = 0
            }
            for _ in 0..<240 { stepJar(g, settleAll: false) }
        }
        for i in 0..<Self.pileCount {
            orbs[i].state = .pile
            orbs[i].homeX = orbs[i].x
            orbs[i].homeY = orbs[i].y
            orbs[i].vx = 0
            orbs[i].vy = 0
        }
    }

    // MARK: Helpers

    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        value < low ? low : value > high ? high : value
    }

    static func easeInOut(_ u: Double) -> Double {
        u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// Haptics are silent while the script plays, as in the React Native block.
    mutating func emit(_ event: MoodOrbJarEvent) {
        if !scripted && !demoOn {
            events.append(event)
        }
    }

    func freeOrb() -> Int {
        for i in Self.pileCount..<Self.poolCount where orbs[i].state == .off {
            return i
        }
        return -1
    }

    /// Whether a touch at this point is this block's: a picker orb or the newest entry.
    func hits(x: Double, y: Double, geometry g: MoodOrbJarGeometry) -> Bool {
        for m in 0..<Tokens.moods.count where hypot(x - g.slotX[m], y - g.slotY) < 30 * g.scale {
            return true
        }
        return lastAdded >= 0 && y > g.rowY && y < g.rowY + g.rowHeight && x < g.width - 64 * g.scale
    }

    /// Launch on an arc solved to peak above the mouth and drop into it, blended with the finger's own velocity.
    private mutating func launch(_ i: Int, _ g: MoodOrbJarGeometry, ux: Double, uy: Double) {
        let gravity = Motion.Throw.gravity * g.scale
        let apex = min(orbs[i].y, g.mouthY) - Motion.Throw.apex * g.scale
        let vy0 = -sqrt(2 * gravity * max(1, orbs[i].y - apex))
        let up = -vy0 / gravity
        let down = sqrt((2 * (g.mouthY + 40 * g.scale - apex)) / gravity)
        let vx0 = (g.centerX - orbs[i].x) / (up + down)
        let k = Motion.Throw.solved
        let limit = Motion.Throw.maxSpeed * g.scale
        orbs[i].vx = Self.clamp(vx0 * k + ux * (1 - k) * 0.3, -limit, limit)
        orbs[i].vy = vy0 * k + Self.clamp(uy, -limit, 0) * (1 - k) * 0.25
        // The finger's share must not miss the mouth: re-aim horizontally for the time actually flown.
        let vy = orbs[i].vy
        let target = max(0, g.mouthY + 40 * g.scale - orbs[i].y)
        let flight = (-vy + sqrt(vy * vy + 2 * gravity * target)) / gravity
        orbs[i].vx = (g.centerX - orbs[i].x) / max(0.2, flight)
        orbs[i].state = .flying
        slotScale[orbs[i].mood] = 0
        emit(.haptic(.throwOrb))
    }

    /// Reduce Motion: the orb appears at the slot physics would give it, without the flight.
    private mutating func placeInstantly(_ i: Int, _ g: MoodOrbJarGeometry) {
        orbs[i].x = g.centerX
        orbs[i].y = g.mouthY + 30 * g.scale
        orbs[i].vx = 0
        orbs[i].vy = 0
        orbs[i].state = .settling
        for _ in 0..<240 {
            stepJar(g, settleAll: false)
        }
        orbs[i].state = .pile
        orbs[i].homeX = orbs[i].x
        orbs[i].homeY = orbs[i].y
        orbs[i].fade = 0
        slotScale[orbs[i].mood] = 0
    }

    mutating func added(_ i: Int) {
        lastAdded = i
        events.append(.added(mood: orbs[i].mood))
        // Shared element: a copy of the orb flies to the new entry's avatar.
        if !reduced {
            ghostTime = 0
            ghostX = orbs[i].x
            ghostY = orbs[i].y
            ghostMood = orbs[i].mood
            growTime = -2 // the new card waits for the orb to arrive
        }
    }

    private mutating func undo() {
        let i = lastAdded
        guard i >= 0, orbs[i].state == .pile else { return }
        orbs[i].state = .leaving
        orbs[i].time = 0
        orbs[i].fromX = orbs[i].x
        orbs[i].fromY = orbs[i].y
        lastAdded = -1
        events.append(.removed)
        emit(.haptic(.undo))
    }

    // MARK: Input

    mutating func apply(_ input: MoodOrbJarInput, geometry g: MoodOrbJarGeometry) {
        switch input {
        case .undo:
            demoOn = false
            quiet = 0
            undo()

        case .add(let mood):
            demoOn = false
            quiet = 0
            let i = freeOrb()
            guard i >= 0 else { return }
            orbs[i].mood = mood
            orbs[i].x = g.slotX[mood]
            orbs[i].y = g.slotY
            orbs[i].fade = 1
            if reduced {
                placeInstantly(i, g)
                added(i)
            } else {
                launch(i, g, ux: 0, uy: 0)
            }

        case .down(let x, let y, let script):
            down(x: x, y: y, script: script, g)

        case .move(let x, let y):
            move(x: x, y: y, g)

        case .up(let vx, let vy):
            up(vx: vx, vy: vy, g)
        }
    }

    private mutating func down(x: Double, y: Double, script: Bool, _ g: MoodOrbJarGeometry) {
        scripted = script
        if !scripted {
            demoOn = false
            quiet = 0
        }
        downX = x
        downY = y
        downTime = clock
        moved = 0
        for m in 0..<Tokens.moods.count {
            guard hypot(x - g.slotX[m], y - g.slotY) < 30 * g.scale, slotScale[m] > 0.9 else { continue }
            let i = freeOrb()
            guard i >= 0 else { return }
            held = i
            orbs[i].mood = m
            orbs[i].state = .held
            orbs[i].x = g.slotX[m]
            orbs[i].y = g.slotY
            orbs[i].fade = 1
            grabOffsetX = orbs[i].x - x
            grabOffsetY = orbs[i].y - y
            slotScale[m] = 0
            emit(.haptic(.grab))
            return
        }
        if lastAdded >= 0 && y > g.rowY && y < g.rowY + g.rowHeight {
            swiping = true
            swipeStartX = x
        }
    }

    private mutating func move(x: Double, y: Double, _ g: MoodOrbJarGeometry) {
        moved = max(moved, hypot(x - downX, y - downY))
        if held >= 0 {
            orbs[held].x = x + grabOffsetX
            orbs[held].y = y + grabOffsetY
        } else if swiping {
            let d = min(0, x - swipeStartX)
            rowX = d > -140 * g.scale ? d : -140 * g.scale - (-(d + 140 * g.scale)) * 0.3
        }
    }

    private mutating func up(vx: Double, vy: Double, _ g: MoodOrbJarGeometry) {
        if held >= 0 {
            let i = held
            held = -1
            let isTap = moved < 8 * g.scale && clock - downTime < 0.25
            if vy < -Motion.Throw.minFlick * g.scale || isTap {
                if reduced {
                    placeInstantly(i, g)
                    added(i)
                } else {
                    launch(i, g, ux: vx, uy: vy)
                }
            } else {
                orbs[i].state = .returning
                orbs[i].fromX = orbs[i].x
                orbs[i].fromY = orbs[i].y
                orbs[i].time = 0
            }
        } else if swiping {
            swiping = false
            if rowX < -Motion.Undo.threshold * g.scale {
                undo()
            }
        }
        scripted = false
    }
}
