import SwiftUI

/// What a finished animation sets off.
enum InkSignaturePadEvent {
    case wipe
    case thud
}

/// A value animated on the pad's clock (ms) like Reanimated's `withTiming`, `withSequence` and
/// `withDelay`: a run of legs, each from where the last left off; during a delay the animation it
/// replaces runs on.
struct InkSignaturePadTrack {
    struct Leg {
        var start: Double
        var duration: Double
        var to: Double
        var curve: UnitCurve
        var event: InkSignaturePadEvent?
    }

    private var base: Double
    private var legs: [Leg] = []
    private var fired = 0
    private var oldBase = 0.0
    private var oldLegs: [Leg] = []

    init(_ value: Double) {
        base = value
    }

    private static func run(_ base: Double, _ legs: [Leg], _ t: Double) -> Double {
        var v = base
        for leg in legs {
            if t < leg.start { return v }
            if leg.duration <= 0 || t >= leg.start + leg.duration {
                v = leg.to
                continue
            }
            return v + (leg.to - v) * leg.curve.value(at: (t - leg.start) / leg.duration)
        }
        return v
    }

    func value(_ t: Double) -> Double {
        if let first = legs.first, t < first.start, !oldLegs.isEmpty { return Self.run(oldBase, oldLegs, t) }
        return Self.run(base, legs, t)
    }

    var target: Double { legs.last?.to ?? base }

    func moving(_ t: Double) -> Bool {
        guard let last = legs.last else { return false }
        return t < last.start + last.duration
    }

    /// Runs `steps` (to, ms, curve, what it sets off at its end) one after another from `now + delay`.
    mutating func animate(_ steps: [(to: Double, duration: Double, curve: UnitCurve, event: InkSignaturePadEvent?)], at now: Double, delay: Double = 0) {
        let start = now + delay
        let from = value(start)
        if delay > 0 {
            if let first = legs.first, now < first.start, !oldLegs.isEmpty {
                // Already waiting: the animation running now is the old one.
            } else {
                oldBase = base
                oldLegs = legs
            }
        } else {
            oldLegs = []
        }
        base = from
        var at = start
        legs = steps.map { step in
            defer { at += step.duration }
            return Leg(start: at, duration: step.duration, to: step.to, curve: step.curve, event: step.event)
        }
        fired = 0
    }

    mutating func animate(to value: Double, duration: Double, curve: UnitCurve = InkSignaturePadModel.inOutQuad, at now: Double, delay: Double = 0, event: InkSignaturePadEvent? = nil) {
        animate([(value, duration, curve, event)], at: now, delay: delay)
    }

    mutating func set(_ value: Double) {
        base = value
        legs = []
        oldLegs = []
        fired = 0
    }

    /// What the legs that have ended by `t` set off, once each.
    mutating func events(upTo t: Double) -> [InkSignaturePadEvent] {
        var out: [InkSignaturePadEvent] = []
        while fired < legs.count, legs[fired].start + legs[fired].duration <= t {
            if let event = legs[fired].event { out.append(event) }
            fired += 1
        }
        return out
    }
}

/// Everything on the pad that changes, stepped frame by frame on the pad's own clock as the React
/// Native block does in its frame callback.
final class InkSignaturePadModel {
    // Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static let inOutQuad = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let stampEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.55, y: 0), endControlPoint: UnitPoint(x: 1, y: 0.45))
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let cssEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))

    // A stroke made after a pause longer than replayGap ms is replayed replayGap after the one
    // before. The replay starts replayLead ms after the press, over the ghost of the ink it redraws,
    // which fades in over ghostIn and out over ghostOut ms.
    static let replayGap = 450.0
    static let replayLead = 260.0
    static let ghostIn = 220.0
    static let ghostOut = 380.0
    // The pen fades in and out with a time constant of penFade ms.
    static let penFade = 70.0
    static let maxSamples = 9000
    // Clear: the wiper crosses the pad in sweepMs.
    static let sweepMs = 1300.0
    // Done: the page shrinks in sealMs, then the wax drops from stampFrom× in stampMs, squashes by
    // stampSquash and settles.
    static let sealMs = 720.0
    static let stampMs = 230.0
    static let stampSquash = 0.06
    static let unsealMs = 560.0

    // autoPlay loop (seconds): sign, replay, seal, open, wipe. A button "presses" PRESS before it acts.
    static let loop = 17.4
    static let signAt = 0.7
    static let replayAt = 5.9
    static let doneAt = 11.1
    static let unsealAt = 14.3
    static let clearAt = 15.5
    static let press = 0.14
    // After a touch, autoPlay waits this long (s) with no finger down before it picks up again.
    static let userHold = 4.0

    var reduced = false
    var onDone: (([Double]) -> Void)?
    var thud: () -> Void = {}
    var tap: () -> Void = {}

    // The script as ink samples, laid out for the pad.
    private(set) var script: [Double] = []
    private var scriptStrokes = 0
    private var scriptKey = ""

    private(set) var ink: [Double] = []
    private(set) var replay: [Double] = []
    private var replayCursor = 0
    var ghost = InkSignaturePadTrack(0)
    private var strokeCount = 0.0
    private(set) var drawing = false
    /// The pad's clock, ms.
    private(set) var clock = 0.0
    private var lastDate: Date?
    var sweep = InkSignaturePadTrack(0)
    var inkAlpha = InkSignaturePadTrack(1)
    var seal = InkSignaturePadTrack(0)
    var stamp = InkSignaturePadTrack(0)
    var fade = InkSignaturePadTrack(1)
    var hint = InkSignaturePadTrack(1)
    private(set) var pen = (x: 0.0, y: 0.0, lift: 0.0)
    private(set) var penAlpha = 0.0
    private var penOn = false
    // autoPlay: where the script is (sample index, or −1), when it started, and its first stroke.
    private var scriptCursor = -1
    private var scriptFrom = 0.0
    private var scriptStroke = 0.0
    private var looping = false
    private var loopAt = 0.0
    private var hold = 0.0
    var pressClear = InkSignaturePadTrack(0)
    var pressReplay = InkSignaturePadTrack(0)
    var pressDone = InkSignaturePadTrack(0)
    private(set) var sealed = false
    private var hadInk = false

    var hasInk: Bool { !ink.isEmpty }

    func configure(autoPlay: Bool, padWidth: Double, lineY: Double, signLeft: Double, signRight: Double, lift: Double) {
        if looping != autoPlay {
            looping = autoPlay
            loopAt = 0
            hold = 0
        }
        let key = "\(padWidth) \(lineY)"
        if key != scriptKey {
            scriptKey = key
            let s = InkSignaturePadScript.samples(left: signLeft, right: padWidth - signRight, baseline: lineY - lift)
            script = s.samples
            scriptStrokes = s.strokes
        }
    }

    /// Whether anything moves, so the pad has to be drawn again next frame.
    var live: Bool {
        let t = clock
        if looping || drawing || scriptCursor >= 0 || !replay.isEmpty || penOn || penAlpha > 0 || hold > 0 { return true }
        if !ink.isEmpty && t - ink[ink.count - 3] < InkSignaturePadInk.dryMs + 60 { return true }
        return [ghost, sweep, inkAlpha, seal, stamp, fade, hint, pressClear, pressReplay, pressDone].contains { $0.moving(t) }
    }

    private static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool {
        t >= before ? before < at && t >= at : at > before || at <= t
    }

    private func flash(_ press: inout InkSignaturePadTrack) {
        press.animate([(1, 90, Self.inOutQuad, nil), (0, 240, Self.inOutQuad, nil)], at: clock)
    }

    /// Ends a replay now: the redrawn ink becomes the ink.
    private func finishReplay() {
        if replay.isEmpty { return }
        ink = replay
        replay = []
        ghost.animate(to: 0, duration: Self.ghostOut, at: clock)
        penOn = false
    }

    func startReplay() {
        if ink.isEmpty || sweep.value(clock) > 0 || seal.value(clock) > 0 { return }
        finishReplay()
        replay = InkSignaturePadInk.retime(ink, start: clock + Self.replayLead, maxGap: Self.replayGap)
        replayCursor = 0
        ghost.animate(to: 1, duration: Self.ghostIn, at: clock)
        penOn = !reduced
    }

    private func wipe() {
        ink = []
        replay = []
        ghost.set(0)
        sweep.set(0)
        inkAlpha.set(1)
        penOn = false
    }

    func startSweep(user: Bool) {
        if ink.isEmpty || sweep.value(clock) > 0 || seal.value(clock) > 0 { return }
        finishReplay()
        scriptCursor = -1
        penOn = false
        if user { tap() }
        if reduced {
            inkAlpha.animate(to: 0, duration: 260, at: clock, event: .wipe)
            return
        }
        sweep.animate(to: 1, duration: Self.sweepMs, curve: Self.easeInOut, at: clock, event: .wipe)
    }

    func sealUp(user: Bool) {
        if ink.isEmpty || seal.value(clock) > 0 || sweep.value(clock) > 0 { return }
        finishReplay()
        scriptCursor = -1
        penOn = false
        sealed = true
        if user { onDone?(ink) }
        if reduced {
            fade.animate([(0, 160, Self.inOutQuad, nil), (1, 260, Self.inOutQuad, nil)], at: clock)
            seal.animate(to: 1, duration: 0, at: clock, delay: 160)
            stamp.animate(to: 1, duration: 260, at: clock, delay: 420, event: user ? .thud : nil)
            return
        }
        seal.animate(to: 1, duration: Self.sealMs, curve: Self.easeInOut, at: clock)
        stamp.animate(
            [
                (1, Self.stampMs, Self.stampEase, user ? .thud : nil),
                (1 + Self.stampSquash, 80, Self.inOutQuad, nil),
                (1, 200, Self.easeOut, nil),
            ],
            at: clock,
            delay: Self.sealMs - 60
        )
    }

    func sealDown() {
        if seal.value(clock) < 1 { return }
        sealed = false
        stamp.animate(to: 0, duration: 180, at: clock)
        if reduced {
            fade.animate([(0, 160, Self.inOutQuad, nil), (1, 260, Self.inOutQuad, nil)], at: clock)
            seal.animate(to: 0, duration: 0, at: clock, delay: 160)
            return
        }
        seal.animate(to: 0, duration: Self.unsealMs, curve: Self.easeInOut, at: clock, delay: 120)
    }

    /// A touch: autoPlay stops writing and waits until the pad has been left alone for a while.
    func touched() {
        hold = Self.userHold
        if scriptCursor >= 0 {
            scriptCursor = -1
            penOn = false
        }
    }

    /// Where the loop picks up after a touch: the page is opened or wiped first if need be.
    private func resumePoint() -> Double {
        if seal.value(clock) > 0.5 { return Self.unsealAt - 0.4 }
        if !ink.isEmpty { return Self.clearAt - 0.5 }
        return 0
    }

    // MARK: - The finger

    func begin(x: Double, y: Double) {
        if seal.value(clock) > 0 || sweep.value(clock) > 0 || inkAlpha.value(clock) < 1 { return }
        touched()
        finishReplay()
        strokeCount += 1
        if ink.count >= Self.maxSamples * InkSignaturePadInk.stride { return }
        InkSignaturePadInk.addSample(&ink, x: x, y: y, t: clock, stroke: strokeCount, starts: true, speed: -1)
        drawing = true
    }

    func move(x: Double, y: Double, speed: Double) {
        if !drawing || ink.count >= Self.maxSamples * InkSignaturePadInk.stride { return }
        InkSignaturePadInk.addSample(&ink, x: x, y: y, t: clock, stroke: strokeCount, starts: false, speed: speed)
    }

    func end() {
        drawing = false
    }

    // MARK: - The frame

    /// Moves the pad's clock on to `date` and does what a frame does.
    func advance(to date: Date) {
        let dt = lastDate.map { min(max(date.timeIntervalSince($0) * 1000, 0), 64) } ?? 16
        lastDate = date
        guard dt > 0 else { return }
        step(dt)
    }

    private func step(_ dt: Double) {
        let now = clock + dt
        clock = now

        for event in sweep.events(upTo: now) + inkAlpha.events(upTo: now) + stamp.events(upTo: now) {
            switch event {
            case .wipe: wipe()
            case .thud: thud()
            }
        }

        if looping {
            if hold > 0 {
                if !drawing {
                    hold -= dt / 1000
                    if hold <= 0 { loopAt = resumePoint() }
                }
            } else {
                let before = loopAt
                let t = (before + dt / 1000).truncatingRemainder(dividingBy: Self.loop)
                loopAt = t
                if Self.crossed(before, t, Self.signAt) && seal.value(now) == 0 && sweep.value(now) == 0 {
                    scriptFrom = now - (t - Self.signAt) * 1000
                    scriptCursor = 0
                    scriptStroke = strokeCount + 1
                    strokeCount += Double(scriptStrokes)
                    penOn = true
                }
                if Self.crossed(before, t, Self.replayAt - Self.press) && !ink.isEmpty { flash(&pressReplay) }
                if Self.crossed(before, t, Self.replayAt) { startReplay() }
                if Self.crossed(before, t, Self.doneAt - Self.press) && !ink.isEmpty { flash(&pressDone) }
                if Self.crossed(before, t, Self.doneAt) { sealUp(user: false) }
                if Self.crossed(before, t, Self.unsealAt) { sealDown() }
                if Self.crossed(before, t, Self.clearAt - Self.press) && !ink.isEmpty { flash(&pressClear) }
                if Self.crossed(before, t, Self.clearAt) { startSweep(user: false) }
            }
        }

        // autoPlay's hand: every script sample whose time has come goes down as ink.
        if scriptCursor >= 0 {
            let stride = InkSignaturePadInk.stride
            let count = script.count / stride
            let rel = now - scriptFrom
            var k = scriptCursor
            if k < count && script[k * stride + 2] <= rel && ink.count < Self.maxSamples * stride {
                while k < count && script[k * stride + 2] <= rel {
                    let at = k * stride
                    let starts = k == 0 || script[at + 4] != script[at - 1]
                    InkSignaturePadInk.addSample(&ink, x: script[at], y: script[at + 1], t: scriptFrom + script[at + 2], stroke: scriptStroke + script[at + 4], starts: starts, speed: -1)
                    k += 1
                }
            }
            let p = InkSignaturePadInk.penAt(script, cursor: k, time: rel)
            pen = (p.x, p.y, k == 0 ? 1 : p.lift)
            if k >= count {
                scriptCursor = -1
                penOn = false
            } else {
                scriptCursor = k
            }
        }

        // The replay: the pen follows the redrawn ink, and when it has caught up the replay ends.
        if !replay.isEmpty {
            let stride = InkSignaturePadInk.stride
            var k = replayCursor
            let count = replay.count / stride
            while k < count && replay[k * stride + 2] <= now { k += 1 }
            replayCursor = k
            let p = InkSignaturePadInk.penAt(replay, cursor: k, time: now)
            pen = (p.x, p.y, k == 0 ? 1 : p.lift)
            if k >= count && now > replay[replay.count - 3] + 140 { finishReplay() }
        }

        let target = penOn && !reduced ? 1.0 : 0.0
        if penAlpha != target {
            let next = penAlpha + (target - penAlpha) * (1 - exp(-dt / Self.penFade))
            penAlpha = abs(next - target) < 0.01 ? target : next
        }

        // "Sign above the line" fades as the first ink lands and back when it is gone.
        if hasInk != hadInk {
            hadInk = hasInk
            hint.animate(to: hasInk ? 0 : 1, duration: 220, curve: Self.cssEase, at: now)
        }
    }

    /// What the ink layer draws this frame.
    func frame(width: Double, height: Double) -> InkSignaturePadInk.Frame {
        InkSignaturePadInk.Frame(
            ink: ink,
            replay: replay,
            now: clock,
            ghost: ghost.value(clock),
            sweep: sweep.value(clock),
            alpha: inkAlpha.value(clock),
            width: width,
            height: height,
            pen: (pen.x, pen.y, pen.lift, penAlpha)
        )
    }
}
