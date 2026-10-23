import SwiftUI
import UIKit

enum PullCordLightMetrics {
    // The lamp hangs from a ceiling point just above the screen on a wire `wireShare` of the screen's
    // height long, and swings about that point as a damped pendulum.
    static let pivotY = -24.0
    static let wireShare = 0.27
    static let gravity = 1800.0 // pt/s²
    static let swingDamping = 1.7 // 1/s
    // While held, the lamp follows the finger's angle through a stiff spring, so letting go hands its
    // angular speed to the pendulum. Past `maxTilt` it is held back with rubber banding.
    static let holdStiffness = 260.0
    static let holdDamping = 26.0
    static let maxTilt = 0.55

    // The shade, in pt, with its top at the end of the wire: a dome `shadeRim` wide at its mouth.
    static let shadeRim = 76.0
    static let shadeDepth = 84.0
    static let bulbR = 15.0

    // The pull cord: a chain of `segments` verlet links `cord` pt long, hanging from inside the rim
    // beside the bulb, with a wooden bead on the end that weighs `beadWeight` times a link.
    static let segments = 15
    static let cord = 190.0
    static let cordX = 26.0
    static let cordY = shadeDepth + 2
    static let beadWeight = 2.6
    static let substeps = 3
    static let iterations = 14
    static let air = 0.9955 // velocity kept per substep
    // Pulled, the links between the ceiling and the finger stretch up to `maxStretch`; past that the
    // finger is followed with rubber banding. Let go, the stretch springs back at `recoil` per second.
    static let maxStretch = 1.32
    static let recoil = 6.0
    // Pulling the bead `pullToClick` pt below where it hangs arms the switch; letting go clicks it.
    static let pullToClick = 62.0
    static let grabRadius = 38.0
    // How strongly a stretched cord tugs the lamp, and the little kick the switch gives it.
    static let tug = 0.02
    static let clickKick = 0.09

    // The light: it comes up at `lightOn` per second through a few quick flickers, and dies at
    // `lightOff` per second while the filament glows a little longer.
    static let lightOn = 9.0
    static let lightOff = 5.5
    static let filamentOff = 2.4
    static let coneSpread = 1.05 // half-width at the far end, in screen widths
    static let coneLength = 1.05 // in screen heights

    // Night grading and lamp grading of the same photo (4×5 colour matrices, rows R G B A, the last
    // column an offset).
    static let night: [Double] = [0.11, 0.15, 0.03, 0, 0.004, 0.11, 0.17, 0.05, 0, 0.008, 0.14, 0.22, 0.15, 0, 0.02, 0, 0, 0, 1, 0]
    static let lamp: [Double] = [1.12, 0.07, 0, 0, -0.03, 0.03, 0.97, 0.02, 0, -0.03, 0, 0.03, 0.72, 0, -0.04, 0, 0, 0, 1, 0]

    // autoPlay (seconds into `loop`): pull the cord on, push the lamp into a swing, pull it off, rest.
    static let loop = 10.0
    static let pullOn = [0.7, 1.55, 1.75]
    static let swing = [3.2, 3.8]
    static let pullOff = [5.9, 6.75, 6.95]
    static let pullBy = (x: 7.0, y: 96.0)
    static let swingTo = 0.3
    // Over the last `settle` s the motion is damped harder, so the loop seam meets a still room.
    static let settle = 0.9
    static let userHold = 4.0

    /// cubic-bezier(0.65, 0, 0.35, 1), as the script eases its pulls.
    static let easeInOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.65, y: 0),
        endControlPoint: UnitPoint(x: 0.35, y: 1)
    )

    static func rubber(_ over: Double, _ limit: Double) -> Double {
        limit * (1 - 1 / (1 + over / limit))
    }

    /// A bell, 1 at `at`, gone `width` either side.
    static func bump(_ t: Double, _ at: Double, _ width: Double) -> Double {
        exp(-pow((t - at) / width, 2))
    }

    /// The light's level `since` s after it was switched on: three quick dips, then a faint shimmer.
    static func flicker(_ since: Double, _ now: Double) -> Double {
        let dips = 0.62 * bump(since, 0.09, 0.045) + 0.4 * bump(since, 0.24, 0.04) + 0.2 * bump(since, 0.43, 0.05)
        let shimmer = 0.012 * sin(now * 13.1) * sin(now * 3.7)
        return max(0, 1 - dips + shimmer)
    }
}

struct PullCordLightSettings {
    var width: Double
    var height: Double
    var initiallyOn = false
    var autoPlay: Bool
    var reducedMotion: Bool
    var onToggle: ((Bool) -> Void)?
}

/// One frame to draw.
struct PullCordLightFrame {
    var angle: Double
    /// x, y, previous x, previous y for each particle, flat.
    var rope: [Double]
    var level: Double
    var glow: Double
    var filament: Double
}

/// The lamp, its cord and its light, stepped once a frame like the RN frame callback, and the
/// touches that move them.
@MainActor
final class PullCordLightEngine {
    private typealias M = PullCordLightMetrics

    // The lamp's angle (rad, positive swings it left) and its speed.
    private var angle = 0.0
    private var spin = 0.0
    private var lampHeld = false
    private var lampTarget = 0.0
    private var lampGrabFrom = 0.0
    private var lampAngleFrom = 0.0
    // The rope: x, y, previous x, previous y for each particle, flat.
    private var rope: [Double] = []
    // The particle held by a finger (−1 for none), where the finger wants it, and the offset of the grab.
    private var grabbed = -1
    private var goalX = 0.0
    private var goalY = 0.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var stretch = 1.0
    private var armed = false
    // The light: `on` is the switch, `level` follows it, `glow` adds the flicker, `filament` the bulb.
    private var on = false
    private var level = 0.0
    private var glow = 0.0
    private var filament = 0.0
    private var switchedAt = -10.0
    private var time = 0.0
    private var byUser = false
    private var loopClock = 0.0
    // Where the scripted finger took the bead.
    private var takenX = 0.0
    private var takenY = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var measured: (Double, Double)?
    private var settings = PullCordLightSettings(width: 0, height: 0, autoPlay: false, reducedMotion: false)
    private var touching = false

    private let click = UIImpactFeedbackGenerator(style: .medium)
    private let detent = UISelectionFeedbackGenerator()

    private var started = false

    private var pivotX: Double { settings.width / 2 }
    private var wire: Double { settings.height * M.wireShare }
    private var link: Double { M.cord / Double(M.segments) }
    // Moment arm of the pendulum: the wire plus half the shade.
    private var arm: Double { wire + M.shadeDepth * 0.6 }
    private var reducedMotion: Bool { settings.reducedMotion }

    func step(to date: Date, settings: PullCordLightSettings) -> PullCordLightFrame {
        self.settings = settings
        if !started {
            started = true
            on = settings.initiallyOn
            level = on ? 1 : 0
            glow = level
            filament = level
        }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            loopClock = 0
            hold = 0
        }
        // The rope hangs straight down from its anchor whenever the screen is measured.
        if settings.width > 0, measured.map({ $0 != (settings.width, settings.height) }) ?? true {
            measured = (settings.width, settings.height)
            rope = []
            for i in 0...M.segments {
                let x = pivotX + M.cordX
                let y = M.pivotY + wire + M.cordY + Double(i) * link
                rope += [x, y, x, y]
            }
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        if lastDate == nil || raw > 0 {
            lastDate = date
            let dt = min(raw, 0.04)
            if dt > 0 && !rope.isEmpty { advance(dt: dt) }
        }
        return PullCordLightFrame(angle: angle, rope: rope, level: level, glow: glow, filament: filament)
    }

    /// Where the cord hangs from on the shade, for the lamp at angle `a`.
    private func anchorX(_ a: Double) -> Double { pivotX + M.cordX * cos(a) - (wire + M.cordY) * sin(a) }
    private func anchorY(_ a: Double) -> Double { M.pivotY + M.cordX * sin(a) + (wire + M.cordY) * cos(a) }

    private func flip(user: Bool) {
        let next = !on
        on = next
        if next { switchedAt = time }
        if !reducedMotion { spin += next ? M.clickKick : -M.clickKick }
        if user {
            click.impactOccurred()
            settings.onToggle?(next)
        }
    }

    private func grabCord(_ index: Int, fingerX: Double, fingerY: Double, user: Bool) {
        byUser = user
        grabbed = index
        offsetX = rope[index * 4] - fingerX
        offsetY = rope[index * 4 + 1] - fingerY
        goalX = rope[index * 4]
        goalY = rope[index * 4 + 1]
        armed = false
    }

    private func moveCord(_ fingerX: Double, _ fingerY: Double) {
        goalX = fingerX + offsetX
        goalY = fingerY + offsetY
    }

    private func releaseCord() {
        guard grabbed >= 0 else { return }
        grabbed = -1
        if armed { flip(user: byUser) }
        armed = false
    }

    /// The lamp's angle with the finger at (x, y): the angle of the line from the ceiling point.
    private func fingerAngle(_ x: Double, _ y: Double) -> Double {
        atan2(-(x - pivotX), max(1, y - M.pivotY))
    }

    private func grabLamp(_ x: Double, _ y: Double, user: Bool) {
        byUser = user
        lampHeld = true
        lampGrabFrom = fingerAngle(x, y)
        lampAngleFrom = angle
        lampTarget = angle
    }

    private func moveLamp(_ x: Double, _ y: Double) {
        let raw = lampAngleFrom + fingerAngle(x, y) - lampGrabFrom
        let over = abs(raw) - M.maxTilt
        lampTarget = over > 0 ? (raw < 0 ? -1 : 1) * (M.maxTilt + M.rubber(over, 0.2)) : raw
    }

    private func advance(dt: Double) {
        let now = time + dt
        time = now

        // The script, while nobody is touching.
        var settle = 0.0
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                loopClock = 0
                // A light left on by hand is switched off by the next pull of the loop.
                if on { loopClock = M.pullOff[0] - 0.4 }
            }
        } else if looping && !(grabbed >= 0 && byUser) && !(lampHeld && byUser) {
            let before = loopClock
            var t = before + dt
            if t >= M.loop { t -= M.loop }
            loopClock = t
            func crossed(_ at: Double) -> Bool { before < at && t >= at }
            // Two pulls of the cord: down to `pullBy` from where the bead was taken, a beat held, and let go.
            for pull in [M.pullOn, M.pullOff] {
                if crossed(pull[0]) {
                    let o = M.segments * 4
                    takenX = rope[o]
                    takenY = rope[o + 1]
                    grabCord(M.segments, fingerX: rope[o], fingerY: rope[o + 1], user: false)
                }
                if t >= pull[0] && t < pull[2] && grabbed == M.segments && !byUser {
                    let p = M.easeInOut.value(at: min(1, (t - pull[0]) / (pull[1] - pull[0])))
                    moveCord(takenX + M.pullBy.x * p, takenY + M.pullBy.y * p)
                }
                if crossed(pull[2]) { releaseCord() }
            }
            // A push on the lamp, let go mid-swing.
            if crossed(M.swing[0]) {
                lampHeld = true
                byUser = false
                lampTarget = angle
            }
            if lampHeld && !byUser && t >= M.swing[0] && t < M.swing[1] {
                lampTarget = M.swingTo * M.easeInOut.value(at: (t - M.swing[0]) / (M.swing[1] - M.swing[0]))
            }
            if crossed(M.swing[1]) { lampHeld = false }
            if t > M.loop - M.settle { settle = (t - (M.loop - M.settle)) / M.settle }
            if t < before {
                // The seam: everything has come to rest; clear what is left so each loop starts the same.
                angle = 0
                spin = 0
                settle = 1
            }
        }

        // The light.
        let wantOn = on ? 1.0 : 0.0
        let rate = on ? M.lightOn : M.lightOff
        level += (wantOn - level) * (1 - exp(-dt * (reducedMotion ? 6 : rate)))
        let fl = reducedMotion || !on ? 1 : M.flicker(now - switchedAt, now)
        glow = level * fl
        let fRate = on ? M.lightOn * 1.4 : M.filamentOff
        filament += (wantOn * fl - filament) * (1 - exp(-dt * fRate))

        // The lamp: a damped pendulum, pulled toward the finger while held and tugged by the cord.
        let a = angle
        var accel = -(M.gravity / arm) * sin(a) - M.swingDamping * (1 + settle * 6) * spin
        if reducedMotion { accel = -(M.gravity / arm) * sin(a) - 9 * spin }
        if lampHeld { accel = M.holdStiffness * (lampTarget - a) - M.holdDamping * spin }

        // Stretch of the held stretch of cord: toward what the finger asks for, or back to 1.
        let g = grabbed
        let ax0 = anchorX(a)
        let ay0 = anchorY(a)
        var tx = goalX
        var ty = goalY
        if g > 0 {
            let dx = tx - ax0
            let dy = ty - ay0
            let d = max(1, hypot(dx, dy))
            let natural = Double(g) * link
            let limit = natural * M.maxStretch
            if d > limit {
                let reach = limit + M.rubber(d - limit, 46)
                tx = ax0 + dx / d * reach
                ty = ay0 + dy / d * reach
            }
            stretch = max(1, min(M.maxStretch, d / natural))
            // The click is armed once the held link is far enough below where it hangs.
            let pulled = ty - (ay0 + Double(g) * link)
            if !armed && pulled > M.pullToClick {
                armed = true
                if byUser { detent.selectionChanged() }
            } else if armed && pulled < M.pullToClick - 10 {
                armed = false
            }
            // A stretched cord pulls the shade toward the finger.
            let pull = (stretch - 1) * 1.6
            let fx = dx / d * pull
            let fy = dy / d * pull
            let rx = ax0 - pivotX
            let ry = ay0 - M.pivotY
            accel += (fy * rx - fx * ry) * M.tug * (reducedMotion ? 0.3 : 1)
        } else {
            stretch = 1 + (stretch - 1) * exp(-dt * M.recoil)
        }
        spin += accel * dt
        angle = a + spin * dt

        // The rope, in substeps: verlet integration, then the links pulled back to their lengths.
        let a1 = angle
        let ax1 = anchorX(a1)
        let ay1 = anchorY(a1)
        let h = dt / Double(M.substeps)
        let keep = M.air - settle * 0.03 - (reducedMotion ? 0.02 : 0)
        let s = stretch
        let n = M.segments + 1
        let link = self.link
        rope.withUnsafeMutableBufferPointer { p in
            for step in 1...M.substeps {
                let f = Double(step) / Double(M.substeps)
                let topX = ax0 + (ax1 - ax0) * f
                let topY = ay0 + (ay1 - ay0) * f
                for i in 1..<n where i != g {
                    let o = i * 4
                    let vx = (p[o] - p[o + 2]) * keep
                    let vy = (p[o + 1] - p[o + 3]) * keep
                    p[o + 2] = p[o]
                    p[o + 3] = p[o + 1]
                    p[o] += vx
                    p[o + 1] += vy + M.gravity * h * h
                }
                p[0] = topX
                p[1] = topY
                p[2] = topX
                p[3] = topY
                if g > 0 {
                    let o = g * 4
                    p[o + 2] = p[o]
                    p[o + 3] = p[o + 1]
                    p[o] += (tx - p[o]) * f
                    p[o + 1] += (ty - p[o + 1]) * f
                }
                for _ in 0..<M.iterations {
                    for i in 0..<(n - 1) {
                        let a0 = i * 4
                        let b0 = a0 + 4
                        let wa = i == 0 || i == g ? 0.0 : 1.0
                        let wb = i + 1 == g ? 0.0 : i + 1 == n - 1 ? 1 / M.beadWeight : 1.0
                        let sum = wa + wb
                        if sum == 0 { continue }
                        let dx = p[b0] - p[a0]
                        let dy = p[b0 + 1] - p[a0 + 1]
                        let d = max(0.0001, hypot(dx, dy))
                        let rest = link * (g > 0 && i >= g ? 1 : s)
                        let k = (d - rest) / d / sum
                        p[a0] += dx * k * wa
                        p[a0 + 1] += dy * k * wa
                        p[b0] -= dx * k * wb
                        p[b0 + 1] -= dy * k * wb
                    }
                }
            }
            if settle >= 1 {
                // At the seam: hang the rope straight, at rest.
                for i in 0..<n {
                    let o = i * 4
                    p[o] = ax1
                    p[o + 1] = ay1 + Double(i) * link
                    p[o + 2] = p[o]
                    p[o + 3] = p[o + 1]
                }
            }
        }
    }

    // MARK: Touches

    private func touched() {
        hold = M.userHold
        // Hand a scripted pull or push over to the finger.
        if grabbed >= 0 && !byUser { grabbed = -1 }
        if lampHeld && !byUser { lampHeld = false }
        armed = false
    }

    func touchChanged(_ value: DragGesture.Value) {
        if !touching {
            touching = true
            touchBegan(value.startLocation)
        }
        if grabbed >= 0 && byUser {
            moveCord(value.location.x, value.location.y)
        } else if lampHeld && byUser {
            moveLamp(value.location.x, value.location.y)
        }
    }

    private func touchBegan(_ point: CGPoint) {
        guard !rope.isEmpty else { return }
        let x = Double(point.x)
        let y = Double(point.y)
        // The nearest link of the cord, the bead first.
        var best = -1
        var bestDistance = M.grabRadius
        for i in stride(from: M.segments, through: 3, by: -1) {
            let d = hypot(rope[i * 4] - x, rope[i * 4 + 1] - y)
            if d < bestDistance - (i == M.segments ? 8 : 0) {
                best = i
                bestDistance = d
            }
        }
        if best > 0 {
            touched()
            grabCord(best, fingerX: x, fingerY: y, user: true)
            return
        }
        // Or the lamp: the point in the shade's own frame.
        let a = angle
        let dx = x - pivotX
        let dy = y - M.pivotY
        let lx = dx * cos(a) + dy * sin(a)
        let ly = -dx * sin(a) + dy * cos(a) - wire
        if abs(lx) < M.shadeRim + 18 && ly > -40 && ly < M.shadeDepth + 40 {
            touched()
            grabLamp(x, y, user: true)
        }
    }

    /// The drag ended without `onEnded` (the system took the touch): let go as a lift would, so the
    /// next touch can grab again. Deferred a turn so a normal `onEnded` in the same pass always wins.
    func touchCancelled() {
        Task { @MainActor [weak self] in
            guard let self, self.touching else { return }
            self.touchEnded()
        }
    }

    func touchEnded() {
        touching = false
        guard byUser else { return }
        if grabbed >= 0 { releaseCord() }
        if lampHeld { lampHeld = false }
    }

    /// The accessibility action: switches the light as a pull would.
    func toggle() {
        touched()
        flip(user: true)
    }
}
