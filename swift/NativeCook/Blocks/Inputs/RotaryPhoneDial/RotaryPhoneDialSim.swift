import Foundation

// The dial's mechanism: the finger wheel's angle, the drag that winds it toward the finger stop,
// and the governed return that counts one pulse for each number that passes the stop. Stepped in
// fixed substeps, a line-for-line port of the React Native simulation.
//
// Angles are in degrees on the screen: 0 points right and positive turns clockwise (screen y grows
// downward). `phi` is how far the wheel is wound clockwise from rest.
enum RotaryPhoneDialTuning {
    /// The finger stop's angle.
    static let stopAngle = 52.0
    /// Angle between neighbouring holes.
    static let holeStep = 30.0
    /// Holes, in dial order: digits 1…9 then 0.
    static let holes = 10
    /// The digit each hole dials.
    static let holeDigits = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0]
    // A finger in a hole meets the stop stopGap degrees before the hole's centre would reach it.
    static let stopGap = 16.0
    // Past the stop the wheel gives at most overMax degrees (rubber band, overSoft degrees soft);
    // below rest it gives underMax.
    static let overMax = 11.0
    static let overSoft = 20.0
    static let underMax = 3.0
    static let underSoft = 8.0
    // A release within reachSlack degrees of the stop still counts as reaching it.
    static let reachSlack = 2.0
    // The governor: the wheel returns at returnSpeed degrees per second (ten pulses a second),
    // reaching that speed with a time constant of governorLag s.
    static let returnSpeed = 300.0
    static let governorLag = 0.035
    // Landing on the rest stop it bounces back `bounce` of its speed, then settles on a spring.
    static let bounce = 0.13
    static let settleK = 900.0
    static let settleDamp = 30.0
    // Each pulse twitches the finger stop (degrees per second of kick, spring back).
    static let kick = 34.0
    static let kickK = 1400.0
    static let kickDamp = 26.0

    static let substep = 1.0 / 240
    static let maxFrame = 1.0 / 30

    static let eventTick = 1
    static let eventStop = 2
    static let eventDigit = 4
    static let eventClear = 8

    static let modeRest = 0
    static let modeDrag = 1
    static let modeReturn = 2
    static let modeSettle = 3

    static let scriptID = 1000

    /// Where hole i sits at rest.
    static func holeRest(_ i: Int) -> Double {
        stopAngle - Double(i + 2) * holeStep
    }

    /// How far hole i winds before the finger meets the stop.
    static func holeTravel(_ i: Int) -> Double {
        Double(i + 2) * holeStep - stopGap
    }

    // autoPlay: dials four digits, then presses the card to clear the strip, and rests. Each digit:
    // a finger settles into its hole, winds it to the stop on an ease-in-out curve, rests against
    // the stop, lets go, and waits for the governed return.

    /// The number the script dials.
    static let scriptDigits = [4, 1, 0, 7]
    static let start = 0.7
    static let fingerIn = 0.28
    static let dragSpeed = 250.0
    static let dragMin = 0.32
    static let hold = 0.2
    static let after = 0.42
    static let clearHold = 0.75
    static let clearRest = 1.3

    struct Stroke {
        let at: Double
        let hole: Int
        let drag: Double
        let release: Double
    }

    struct Plan {
        let strokes: [Stroke]
        let clearAt: Double
        let end: Double
    }

    static let plan: Plan = {
        var strokes: [Stroke] = []
        var t = start
        for digit in scriptDigits {
            let hole = digit == 0 ? 9 : digit - 1
            let travel = holeTravel(hole)
            let drag = max(dragMin, travel / dragSpeed)
            let release = t + fingerIn + drag + hold
            strokes.append(Stroke(at: t, hole: hole, drag: drag, release: release))
            t = release + travel / returnSpeed + after
        }
        return Plan(strokes: strokes, clearAt: t, end: t + clearHold + clearRest)
    }()

    /// Seconds the script takes before it starts over.
    static let loop = ceil(plan.end * 2) / 2
}

/// Where the scripted finger is drawn: its hole (-1 when it is away), its angle, how visible it is.
struct RotaryPhoneDialFinger {
    var hole = -1
    var angle = 0.0
    var shown = 0.0
}

final class RotaryPhoneDialSim {
    private typealias T = RotaryPhoneDialTuning

    var phi = 0.0
    var omega = 0.0
    var mode = RotaryPhoneDialTuning.modeRest
    /// The hole the finger is in (0…9), or -1.
    var hole = -1
    var touch = -1
    var scripted = false
    var grabFinger = 0.0
    var grabPhi = 0.0
    /// Raw wind the finger asks for (before the stops).
    var wish = 0.0
    var reached = false
    var dialling = false
    /// Pulses counted on this return.
    var pulses = 0
    /// The digit the last finished return dialled.
    var digit = -1
    var kick = 0.0
    var kickV = 0.0
    /// 0…1, how far the number card is pressed in.
    var card = 0.0
    var cardTarget = 0.0
    let reduced: Bool
    var events = 0
    var eventsByUser = false

    init(reduced: Bool) {
        self.reduced = reduced
    }

    /// `a` moved by whole turns to lie within half a turn of `near` (JavaScript's Math.round).
    private static func nearest(_ a: Double, _ near: Double) -> Double {
        a - 360 * floor((a - near) / 360 + 0.5)
    }

    /// The hole whose disc contains the point at (angle, radius) from the centre, or -1.
    func holeAt(angle: Double, radius: Double, holeR: Double, ringR: Double) -> Int {
        for i in 0..<T.holes {
            // A hole already wound past the stop cannot be taken (the wheel would jump back to it).
            if T.holeTravel(i) < phi { continue }
            let a = (T.holeRest(i) + phi) * .pi / 180
            let t = angle * .pi / 180
            let dx = radius * cos(t) - ringR * cos(a)
            let dy = radius * sin(t) - ringR * sin(a)
            if dx * dx + dy * dy < holeR * holeR * 1.25 { return i }
        }
        return -1
    }

    /// A finger lands in hole `hole` at `angle`: it takes the wheel, wherever it was.
    func grab(_ id: Int, hole: Int, angle: Double, scripted: Bool) {
        touch = id
        self.scripted = scripted
        self.hole = hole
        mode = T.modeDrag
        grabFinger = angle
        grabPhi = phi
        wish = phi
        omega = 0
        reached = false
        // Catching the wheel mid-return loses that number.
        dialling = false
        pulses = 0
    }

    /// The finger moved to `angle`; it is unwrapped against the last angle so it can go round.
    func drag(_ id: Int, angle: Double) {
        if touch != id || mode != T.modeDrag { return }
        let a = Self.nearest(angle, grabFinger + (wish - grabPhi))
        wish = grabPhi + (a - grabFinger)
        let limit = T.holeTravel(hole)
        var p = wish
        if p > limit {
            p = limit + T.overMax * (1 - exp(-(p - limit) / T.overSoft))
        } else if p < 0 {
            p = -T.underMax * (1 - exp(p / T.underSoft))
        }
        phi = p
        if !reached && wish >= limit - T.reachSlack {
            reached = true
            events |= T.eventStop
            if !scripted { eventsByUser = true }
        }
    }

    /// The finger lets go: the governor takes the wheel home, dialling if it reached the stop.
    func release(_ id: Int) {
        if touch != id { return }
        touch = -1
        if mode != T.modeDrag { return }
        dialling = reached
        pulses = 0
        mode = phi > 0 ? T.modeReturn : T.modeSettle
        omega = 0
    }

    /// Long press on the number card: pressed while held, clears the strip when it fires.
    func cardPress(_ down: Bool) {
        cardTarget = down ? 1 : 0
    }

    /// A real finger takes over: the script's finger lets go without dialling.
    func cancelScripted() {
        if scripted && touch != -1 {
            touch = -1
            reached = false
            dialling = false
            if mode == T.modeDrag { mode = phi > 0 ? T.modeReturn : T.modeSettle }
        }
        if scripted { dialling = false }
        cardTarget = 0
    }

    func clear(byUser: Bool) {
        events |= T.eventClear
        if byUser { eventsByUser = true }
    }

    func step(_ frame: Double) {
        guard frame.isFinite, frame > 0 else { return }
        let dt = min(frame, T.maxFrame)
        let n = max(1, Int(ceil(dt / T.substep - 1e-6)))
        let h = dt / Double(n)
        for _ in 0..<n {
            if mode == T.modeReturn {
                let before = phi
                omega += (-T.returnSpeed - omega) * (1 - exp(-h / T.governorLag))
                phi += omega * h
                // A pulse for every multiple of holeStep the wheel passes on its way home.
                if dialling && floor(before / T.holeStep) > floor(max(phi, 0) / T.holeStep) && before > 0 {
                    pulses += 1
                    events |= T.eventTick
                    if !scripted { eventsByUser = true }
                    kickV += T.kick
                }
                if phi <= 0 {
                    phi = 0
                    omega = reduced ? 0 : -omega * T.bounce
                    mode = T.modeSettle
                    if dialling {
                        digit = pulses % 10
                        events |= T.eventDigit
                        if !scripted { eventsByUser = true }
                    }
                    dialling = false
                }
            } else if mode == T.modeSettle {
                omega += (-T.settleK * phi - T.settleDamp * omega) * h
                phi += omega * h
                if phi < 0 {
                    phi = 0
                    omega = -omega * 0.3
                }
                if abs(phi) < 0.02 && abs(omega) < 0.5 {
                    phi = 0
                    omega = 0
                    mode = T.modeRest
                }
            }
            // The finger stop's twitch.
            kickV += (-T.kickK * kick - T.kickDamp * kickV) * h
            kick += kickV * h
            // The card follows the press quickly.
            card += (cardTarget - card) * (1 - exp(-h * 18))
        }
    }

    private static func easeInOut(_ p: Double) -> Double {
        let t = min(1, max(0, p))
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// True when the loop's clock went past `at` between `before` and `now` (wrapping at the loop).
    private static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : at > before || at <= now
    }

    /// Drives the scripted finger from the loop's clock, which moved from `before` to `now`, and
    /// returns where the finger is drawn.
    func script(before: Double, now: Double) -> RotaryPhoneDialFinger {
        var finger = RotaryPhoneDialFinger()
        for st in T.plan.strokes {
            let rest = T.holeRest(st.hole)
            let travel = T.holeTravel(st.hole)
            if Self.passed(before, now, st.at + T.fingerIn) { grab(T.scriptID, hole: st.hole, angle: rest, scripted: true) }
            if now >= st.at && now < st.release + 0.3 {
                finger.hole = st.hole
                let u = (now - st.at - T.fingerIn) / st.drag
                // A little past the stop, so the wheel visibly presses into it.
                let wind = (travel + 5) * Self.easeInOut(u)
                finger.angle = rest + (now >= st.release ? travel : max(0, wind))
                finger.shown = min(1, (now - st.at) / T.fingerIn, max(0, (st.release + 0.3 - now) / 0.3))
                if touch == T.scriptID && now < st.release { drag(T.scriptID, angle: finger.angle) }
            }
            if Self.passed(before, now, st.release) { release(T.scriptID) }
        }
        if Self.passed(before, now, T.plan.clearAt) { cardPress(true) }
        if Self.passed(before, now, T.plan.clearAt + T.clearHold) {
            cardPress(false)
            clear(byUser: false)
        }
        return finger
    }
}
