import Foundation
import UIKit

/// The focus timer around the sand, ported step for step from the React Native
/// `sandHourglassEngine.ts`: the glass's turn, the minute ruler, the session (set, running, done) and
/// the autoPlay script.
///
/// The sand is the clock. A session starts when the glass is turned over; the neck's gate then lets
/// the grains through at the rate that empties the upper bulb in the chosen number of minutes, and
/// the time left is read straight from the grains still above the neck. Turning the glass
/// mid-session does what it does to a real one: the sand that already ran out is now on top and
/// runs back.
enum SandHourglassMetrics {
    typealias Sand = SandHourglassSand
    // One fixed simulation clock drives everything. Each tick is 1/60 s; a slow frame runs at most
    // `maxTicks` of them (a 50 ms cap).
    static let tick = 1.0 / 60
    static let maxTicks = 3
    // Passes of the grain grid per tick: grains fall 4 cells per tick.
    static let passes = 4

    // The glass, in pt at scale 1, around its centre. The grid fills the inside; the wall is
    // `glassWall` thick. Each end: a gap, a narrow collar where the glass is set, then a pale oak cap.
    static let innerHalfH = Double(Sand.rows) * Sand.cell / 2
    static let glassWall = 3.0
    static let capGap = 3.0
    static let collarH = 4.0
    static let capH = 15.0
    static let capHalfW = 96.0
    static let capRadius = 6.0
    static let halfHeight = innerHalfH + capGap + collarH + capH
    // The glass shrinks by up to `lift` while it lies on its side, as if lifted off the table.
    static let lift = 0.1
    // A drag turns the glass when it starts this far from its centre (pt).
    static let grabMin = 26.0
    static let grabMax = 260.0
    // Gesture thresholds standing in for the React Native recognisers: a pan starts after 4 pt, a
    // twist after a few degrees, and a tap is a touch lifted within 350 ms.
    static let panSlop = 4.0
    static let twistSlop = 0.035
    static let tapTime = 0.35
    // A one-finger drag that starts this close to the ruler's line moves the ruler (pt).
    static let rulerReach = 48.0

    // Light comes from the upper left of the screen, whichever way up the glass is.
    static let lightX = -0.5
    static let lightY = -0.866
    // The shadow on the table: a point h above the table falls h·shadowRun to the right and
    // h·shadowRise further back; the glass's depth shows as shadowDepth of its width.
    static let shadowRun = 0.92
    static let shadowRise = 0.2
    static let shadowDepth = 0.3
    // The light sweep that marks the end of a session (s).
    static let sweepFor = 1.15

    // The turn: a spring on the glass's angle (rad), a little under critical so it lands with a sway.
    static let turnStiffness = 38.0
    static let turnDamping = 7.4
    // Seconds of spin a release throws ahead when it picks which way up the glass lands.
    static let throwTime = 0.22

    // The ruler: minutes it offers, finger travel per minute (pt), the coast after a flick (speed
    // decays at this rate, 1/s), its fastest coast (min/s), the speed under which it snaps to a whole
    // minute, that snap, and how much of a pull past either end shows.
    static let minMinutes = 1.0
    static let maxMinutes = 60.0
    static let rulerPx = 11.0
    static let coastDecay = 4.5
    static let coastMax = 60.0
    static let snapSpeed = 2.0
    static let snapStiffness = 220.0
    static let snapDamping = 28.0
    static let rubber = 0.3
    // Selection ticks are at most this often (s).
    static let tickGap = 0.04
    static let defaultMinutes = 25.0

    // The last grain leaves the neck this long before it lands (s); a glass turned back before
    // `cancelWithin` s of a session simply goes back to being set; the "done" moment holds `doneHold` s.
    static let lastGrain = 0.35
    static let cancelWithin = 2.0
    static let doneHold = 2.6
    // The labels and the ruler cross-fade at this rate (1/s).
    static let fadeRate = 6.0

    // autoPlay (s): the ruler is pulled up to 40 min and back to 25, the glass is turned over at
    // `turnAt`, the sand runs out in about 10.5 s with the neck open, the session ends, the glass rests.
    static let loop = 18.0
    static let scriptRuler: [(Double, Double, Double, Double)] = [(0.6, 1.5, 25, 40), (1.9, 2.7, 40, 25)]
    static let turnAt = 3.1
    // The time of day the script pretends it is, for "Ends at" (minutes).
    static let scriptTime = 9 * 60 + 41
    // After a touch the loop waits this long, then starts over.
    static let userHold = 4.0

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// "25:00", "04:07".
    static func formatClock(_ seconds: Double) -> String {
        let whole = max(0, Int(ceil(seconds.isFinite ? seconds : 0)))
        let m = whole / 60
        let s = whole % 60
        return "\(m < 10 ? "0" : "")\(m):\(s < 10 ? "0" : "")\(s)"
    }
}

/// Phases of a session.
enum SandHourglassPhase {
    case idle, running, done
}

/// One frame to draw.
struct SandHourglassFrame {
    var angle: Double
    /// Minutes under the ruler's needle, fractional while it moves.
    var minutes: Double
    var secondsLeft: Double
    var duration: Int
    var endsAt: Int
    /// 0 while setting, 1 while a session runs or has just ended; 1 during the "done" moment.
    var runBlend: Double
    var doneBlend: Double
    /// Seconds into the "done" moment, or below 0.
    var doneAge: Double
    /// Grains in the bulb that is up and the one that is down.
    var upper: Int
    var lower: Int
    /// The grid as RGBA, one pixel per cell.
    var pixels: Data
}

/// The glass's clock, its turn, its ruler and its sand, stepped once a frame like the RN frame
/// callback, and the touches that drive them.
@MainActor
final class SandHourglassEngine {
    private typealias M = SandHourglassMetrics

    private var sand: SandHourglassSand?
    private var seed = 0
    private var pixels = Data()
    private var acc = 0.0
    private var lastDate: Date?
    private var looping = false
    private var still = false
    private var autoPlay: Bool?
    private var clock = 0.0
    private var hold = 0.0
    private var time = 0.0
    // The glass's angle (rad, clockwise), its spin and where its spring is taking it.
    private var angle = 0.0
    private var spin = 0.0
    private var target = 0.0
    private var turning = false
    private var grabAngle = 0.0
    private var fingerLast = 0.0
    private var turned = 0.0
    /// A turn made by hand: it lands with a haptic.
    private var landing = false
    // Ruler: minutes under the needle and what moves it.
    private var minutes = M.defaultMinutes
    private var rulerV = 0.0
    private var rulerDragging = false
    private var rulerAtGrab = M.defaultMinutes
    private var rulerCoasting = false
    private var rulerSnapping = false
    private var lastWhole = Int(M.defaultMinutes)
    private var lastTick = -1.0
    // Session.
    private var phase = SandHourglassPhase.idle
    private var duration = Int(M.defaultMinutes)
    private var endsAt = 0
    private var doneTime = 0.0
    private var lastGrain = 0.0
    private var startTime = 0.0
    private var userSession = false
    private var runBlend = 0.0
    private var doneBlend = 0.0
    private var secondsLeft = M.defaultMinutes * 60
    private var wallMinutes = 0
    private var wallRead = Date.distantPast

    private let landHaptic = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()
    private let notice = UINotificationFeedbackGenerator()

    func step(to date: Date, seed: Int, autoPlay: Bool, reducedMotion: Bool) -> SandHourglassFrame {
        still = reducedMotion
        if self.autoPlay != autoPlay {
            self.autoPlay = autoPlay
            looping = autoPlay
            clock = 0
            hold = 0
        }
        if sand == nil || self.seed != seed {
            let fresh = SandHourglassSand(seed: seed)
            fresh.paint()
            sand = fresh
            self.seed = seed
            pixels = Data()
        }
        if date.timeIntervalSince(wallRead) >= 1 || date < wallRead {
            wallRead = date
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            wallMinutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        }
        guard let sand else { return frame(upper: 0, lower: 0) }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0.016
        let dt = min(raw.isFinite ? max(0, raw) : 0, Double(M.maxTicks) * M.tick)
        lastDate = date
        var pending = acc + dt
        var ticks = 0
        while pending >= M.tick && ticks < M.maxTicks {
            tick(sand)
            pending -= M.tick
            ticks += 1
        }
        acc = min(pending, M.tick)
        if ticks > 0 || pixels.isEmpty {
            sand.paint()
            pixels = Data(bytes: sand.pixels, count: SandHourglassSand.count * 4)
        }
        let upIsFrameTop = cos(angle) >= 0
        return frame(upper: upIsFrameTop ? sand.upper : sand.lower, lower: upIsFrameTop ? sand.lower : sand.upper)
    }

    private func frame(upper: Int, lower: Int) -> SandHourglassFrame {
        SandHourglassFrame(
            angle: angle, minutes: minutes, secondsLeft: secondsLeft, duration: duration, endsAt: endsAt,
            runBlend: runBlend, doneBlend: doneBlend, doneAge: phase == .done ? doneTime : -1,
            upper: upper, lower: lower, pixels: pixels
        )
    }

    /// Minutes the ruler shows, as a whole number in range.
    private var chosenMinutes: Int {
        Int(max(M.minMinutes, min(M.maxMinutes, M.round(minutes))))
    }

    private func touched(_ user: Bool) {
        if user { hold = M.userHold }
    }

    /// A session starts (or a finished one starts again) as the glass goes over.
    private func startSession(user: Bool) {
        if phase == .running { return }
        phase = .running
        startTime = time
        userSession = user
        duration = chosenMinutes
        endsAt = (looping ? M.scriptTime : wallMinutes) + duration
        lastGrain = 0
    }

    /// Sends the glass over to the next way up.
    private func flip(user: Bool) {
        touched(user)
        target = M.round(target / .pi) * .pi + .pi
        landing = user
        if user { landHaptic.prepare() }
        startSession(user: user)
    }

    private func grabGlass() {
        touched(true)
        turning = true
        grabAngle = angle
        spin = 0
        turned = 0
    }

    private func turnGlass(_ turn: Double) {
        guard turning, turn.isFinite else { return }
        turned = turn
        angle = grabAngle + turn
    }

    /// Lets go at `velocity` rad/s: the spring takes the glass to the nearest way up, thrown ahead.
    private func releaseGlass(_ velocity: Double) {
        guard turning else { return }
        turning = false
        touched(true)
        let before = M.round(grabAngle / .pi) * .pi
        let v = still || !velocity.isFinite ? 0 : max(-30, min(30, velocity))
        let goal = M.round((angle + v * M.throwTime) / .pi) * .pi
        spin = v
        target = goal
        landing = goal != before
        if landing {
            landHaptic.prepare()
            startSession(user: true)
        }
    }

    /// The ruler only listens while a session is being set.
    private var rulerLive: Bool { phase == .idle && runBlend < 0.5 }

    private func grabRuler() {
        touched(true)
        rulerDragging = true
        rulerCoasting = false
        rulerSnapping = false
        rulerV = 0
        rulerAtGrab = minutes
        selection.prepare()
    }

    /// Past either end the ruler gives way less and less.
    private func rubber(_ raw: Double) -> Double {
        if raw < M.minMinutes {
            let d = M.minMinutes - raw
            return M.minMinutes - d * M.rubber / (1 + d * 0.08)
        }
        if raw > M.maxMinutes {
            let d = raw - M.maxMinutes
            return M.maxMinutes + d * M.rubber / (1 + d * 0.08)
        }
        return raw
    }

    /// A drag to the left brings longer times under the needle.
    private func dragRuler(_ translationX: Double) {
        guard rulerDragging, translationX.isFinite else { return }
        touched(true)
        minutes = rubber(rulerAtGrab - translationX / M.rulerPx)
        answerRuler(user: true)
    }

    private func releaseRuler(_ velocityX: Double) {
        guard rulerDragging else { return }
        rulerDragging = false
        touched(true)
        let v = still || !velocityX.isFinite ? 0 : -velocityX / M.rulerPx
        rulerV = max(-M.coastMax, min(M.coastMax, v))
        let outside = minutes < M.minMinutes || minutes > M.maxMinutes
        rulerCoasting = !outside
        rulerSnapping = outside
        if outside { rulerV = 0 }
    }

    /// A selection tick each time a new whole minute comes under the needle.
    private func answerRuler(user: Bool) {
        let whole = chosenMinutes
        if whole == lastWhole { return }
        lastWhole = whole
        if user && time - lastTick >= M.tickGap {
            lastTick = time
            selection.selectionChanged()
        }
    }

    private func stepRuler() {
        if rulerCoasting {
            minutes += rulerV * M.tick
            rulerV *= exp(-M.coastDecay * M.tick)
            if minutes < M.minMinutes || minutes > M.maxMinutes {
                minutes = max(M.minMinutes, min(M.maxMinutes, minutes))
                rulerV = 0
                rulerCoasting = false
                rulerSnapping = true
            } else if abs(rulerV) < M.snapSpeed {
                rulerCoasting = false
                rulerSnapping = true
            }
            answerRuler(user: true)
        } else if rulerSnapping {
            let goal = Double(chosenMinutes)
            rulerV += (M.snapStiffness * (goal - minutes) - M.snapDamping * rulerV) * M.tick
            minutes += rulerV * M.tick
            if abs(minutes - goal) < 0.002 && abs(rulerV) < 0.02 {
                minutes = goal
                rulerV = 0
                rulerSnapping = false
            }
            answerRuler(user: true)
        }
    }

    /// Puts the loop back at its resting state.
    private func restartScript() {
        clock = 0
        minutes = M.defaultMinutes
        lastWhole = Int(M.defaultMinutes)
        rulerV = 0
        rulerDragging = false
        rulerCoasting = false
        rulerSnapping = false
    }

    private static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    private func runScript(before: Double, after: Double) {
        for (t0, t1, m0, m1) in M.scriptRuler where after > t0 && before < t1 {
            let u = min(1, (after - t0) / (t1 - t0))
            minutes = m0 + (m1 - m0) * Self.easeInOut(u)
            answerRuler(user: false)
        }
        if before < M.turnAt && after >= M.turnAt { flip(user: false) }
    }

    /// One fixed tick: the script, the turn's spring, the ruler, the gate, the sand, then the session.
    private func tick(_ sand: SandHourglassSand) {
        time += M.tick
        if !turning {
            if hold > 0 {
                hold -= M.tick
                if hold <= 0 && looping { restartScript() }
            } else if looping {
                let before = clock
                let after = before + M.tick
                runScript(before: before, after: after)
                clock = after
                if after >= M.loop { restartScript() }
            }
            let a = angle
            let goal = target
            if still {
                angle = goal
                spin = 0
                if landing {
                    landing = false
                    landHaptic.impactOccurred()
                }
            } else {
                let w = spin + (M.turnStiffness * (goal - a) - M.turnDamping * spin) * M.tick
                let next = a + w * M.tick
                if landing && (a - goal) * (next - goal) <= 0 {
                    landing = false
                    landHaptic.impactOccurred()
                }
                spin = w
                angle = next
            }
            // A whole turn changes nothing on screen, so the numbers are kept small.
            if abs(target) > 2 * .pi + 0.1 {
                let turn = (target > 0 ? 1.0 : -1.0) * 2 * .pi
                target -= turn
                angle -= turn
            }
        }
        if !rulerDragging { stepRuler() }

        // The neck lets the grains through at the session's rate; the self-playing loop runs it open.
        let sessionMinutes = phase == .running ? duration : chosenMinutes
        sand.gate = looping ? -1 : Double(SandHourglassSand.grains) / (Double(sessionMinutes) * 60)
        sand.feedGate(M.tick)
        for _ in 0..<M.passes { sand.step(gx: sin(angle), gy: cos(angle)) }

        // The time left is the sand above the neck (counted by the last paint).
        let up = cos(angle) >= 0 ? sand.upper : sand.lower
        secondsLeft = phase == .idle
            ? Double(chosenMinutes) * 60
            : Double(up) / Double(SandHourglassSand.grains) * Double(sessionMinutes) * 60

        switch phase {
        case .running:
            // Only once the glass has landed: before a turn the empty bulb is still on top.
            let upright = !turning && abs(angle - target) < 0.15 && abs(spin) < 1
            if upright && up == 0 && sand.neckCount == 0 {
                lastGrain += M.tick
                if time - startTime < M.cancelWithin {
                    // Turned straight back: nothing ran, so there is nothing to celebrate.
                    phase = .idle
                } else if lastGrain >= M.lastGrain {
                    phase = .done
                    doneTime = 0
                    if userSession { notice.notificationOccurred(.success) }
                }
            } else {
                lastGrain = 0
            }
        case .done:
            doneTime += M.tick
            if doneTime >= M.doneHold { phase = .idle }
        case .idle:
            break
        }
        let fade = 1 - exp(-M.fadeRate * M.tick)
        runBlend += ((phase == .idle ? 0 : 1) - runBlend) * fade
        doneBlend += ((phase == .done ? 1 : 0) - doneBlend) * fade
    }

    // MARK: - Touches

    private enum Mode { case idle, pending, ruler, pan, twistPending, twist, done }

    private struct Sample {
        var time: Double
        var x: Double
        var y: Double
    }

    private var mode = Mode.idle
    private var fingers: [ObjectIdentifier: CGPoint] = [:]
    private var order: [ObjectIdentifier] = []
    private var downAt = 0.0
    private var start = CGPoint.zero
    private var center = CGPoint.zero
    private var glassHalf = CGSize.zero
    private var rulerY = 0.0
    // Recent finger positions (pan) or twist angles (x), for the speed at release.
    private var samples: [Sample] = []
    private var twistLast = 0.0
    private var twistTotal = 0.0

    /// VoiceOver's activate: turns the glass over, as a tap does.
    func accessibilityFlip() {
        flip(user: true)
    }

    private func record(_ time: Double, _ x: Double, _ y: Double) {
        samples.append(Sample(time: time, x: x, y: y))
        // Keep the last 100 ms.
        while let first = samples.first, time - first.time > 0.1, samples.count > 2 { samples.removeFirst() }
    }

    /// Speed over the recent samples, per second.
    private func velocity() -> (x: Double, y: Double) {
        guard let first = samples.first, let last = samples.last, last.time - first.time > 0.001 else { return (0, 0) }
        let dt = last.time - first.time
        return ((last.x - first.x) / dt, (last.y - first.y) / dt)
    }

    private func twistAngle() -> Double? {
        guard order.count >= 2, let a = fingers[order[0]], let b = fingers[order[1]] else { return nil }
        return atan2(b.y - a.y, b.x - a.x)
    }

    func touchesBegan(_ touches: [(ObjectIdentifier, CGPoint)], time: Double, center: CGPoint, glassHalf: CGSize, rulerY: Double) {
        self.center = center
        self.glassHalf = glassHalf
        self.rulerY = rulerY
        for (id, point) in touches {
            fingers[id] = point
            order.append(id)
        }
        switch mode {
        case .idle:
            if fingers.count == 1, let (_, point) = touches.first {
                mode = .pending
                start = point
                downAt = time
            } else if fingers.count >= 2 {
                beginTwistPending(time)
            }
        case .pending:
            if fingers.count >= 2 { beginTwistPending(time) }
        default:
            break
        }
    }

    private func beginTwistPending(_ time: Double) {
        mode = .twistPending
        twistLast = twistAngle() ?? 0
        twistTotal = 0
        samples = []
        record(time, 0, 0)
    }

    func touchesMoved(_ touches: [(ObjectIdentifier, CGPoint)], time: Double) {
        for (id, point) in touches where fingers[id] != nil { fingers[id] = point }
        switch mode {
        case .pending:
            guard let id = order.first, let p = fingers[id] else { return }
            if hypot(p.x - start.x, p.y - start.y) < M.panSlop { return }
            samples = []
            record(time, p.x, p.y)
            if rulerLive && abs(start.y - rulerY) < M.rulerReach {
                mode = .ruler
                grabRuler()
                dragRuler(p.x - start.x)
                return
            }
            mode = .pan
            let r = hypot(start.x - center.x, start.y - center.y)
            if r >= M.grabMin && r <= M.grabMax {
                grabGlass()
                fingerLast = atan2(p.y - center.y, p.x - center.x)
            }
        case .ruler:
            guard let id = order.first, let p = fingers[id] else { return }
            record(time, p.x, p.y)
            dragRuler(p.x - start.x)
        case .pan:
            guard let id = order.first, let p = fingers[id] else { return }
            record(time, p.x, p.y)
            guard turning else { return }
            let now = atan2(p.y - center.y, p.x - center.x)
            var d = now - fingerLast
            if d > .pi { d -= 2 * .pi }
            if d < -.pi { d += 2 * .pi }
            fingerLast = now
            turnGlass(turned + d)
        case .twistPending, .twist:
            guard let now = twistAngle() else { return }
            var d = now - twistLast
            if d > .pi { d -= 2 * .pi }
            if d < -.pi { d += 2 * .pi }
            twistLast = now
            twistTotal += d
            record(time, twistTotal, 0)
            if mode == .twistPending {
                if abs(twistTotal) < M.twistSlop { return }
                mode = .twist
                grabGlass()
            }
            turnGlass(twistTotal)
        default:
            break
        }
    }

    func touchesEnded(_ ids: [ObjectIdentifier], time: Double, cancelled: Bool) {
        switch mode {
        case .pending:
            if !cancelled && time - downAt <= M.tapTime,
               abs(start.x - center.x) <= glassHalf.width + 20, abs(start.y - center.y) <= glassHalf.height + 20 {
                flip(user: true)
            }
            mode = .done
        case .ruler:
            releaseRuler(cancelled ? 0 : velocity().x)
            mode = .done
        case .pan:
            if cancelled {
                releaseGlass(0)
            } else if turning, let id = order.first, let p = fingers[id] {
                // The finger's speed across the radius is the glass's spin.
                let v = velocity()
                let rx = p.x - center.x
                let ry = p.y - center.y
                releaseGlass((rx * v.y - ry * v.x) / max(rx * rx + ry * ry, 900))
            }
            mode = .done
        case .twist:
            releaseGlass(cancelled ? 0 : velocity().x)
            mode = .done
        case .twistPending:
            mode = .done
        default:
            break
        }
        for id in ids {
            fingers[id] = nil
            order.removeAll { $0 == id }
        }
        if fingers.isEmpty {
            // A finger lost to the system still lets go of the glass and the ruler.
            releaseGlass(0)
            releaseRuler(0)
            mode = .idle
            samples = []
        }
    }
}
