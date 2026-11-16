import Observation
import QuartzCore
import UIKit

/// Only the small frame view observes the per-frame look. No SwiftUI body writes state on a timeline tick.
@MainActor @Observable
final class ReededGlassRevealDriver {
    typealias M = ReededGlassRevealMotion
    var look = M.timeline(0, M.Geo(width: 402))
    var revealed = false
    var targetRevealed = false
    var swapHeld = 1.0
    var plusHeld = 1.0
    var contentOpacity = 0.0
    @ObservationIgnored private var gateDone = false
    @ObservationIgnored private var gateWait = 0.0
    @ObservationIgnored private var gateSmooth = 0
    @ObservationIgnored private var appearanceTime = 0.0
    @ObservationIgnored var onReveal: (() -> Void)?
    @ObservationIgnored var onMask: (() -> Void)?
    @ObservationIgnored private var g = M.Geo(width: 402)
    @ObservationIgnored private var live = M.Live(M.Geo(width: 402))
    @ObservationIgnored private var auto = false
    @ObservationIgnored private var reduce = false
    @ObservationIgnored private var playing = false
    @ObservationIgnored private var loopT = 0.0
    @ObservationIgnored private var clock = 0.0
    @ObservationIgnored private var touchedAt = -1e9
    @ObservationIgnored private var revealedAt = -1e9
    @ObservationIgnored private var previous: Double?
    @ObservationIgnored private var pin: Double?
    @ObservationIgnored private var active = true
    @ObservationIgnored private var startX = 0.0
    @ObservationIgnored private var frontStart = 0.0
    @ObservationIgnored private var dragged = false
    @ObservationIgnored private var fingerDown = false
    @ObservationIgnored private var command = false
    @ObservationIgnored private var configured = false
    @ObservationIgnored private var link: CADisplayLink?
    @ObservationIgnored private var proxy: ReededGlassRevealDisplayLink?
    @ObservationIgnored private var lastCue = -Double.infinity
    @ObservationIgnored private var lastTick = -Double.infinity
    @ObservationIgnored private var haptics = true
    @ObservationIgnored private var autoMask = 8.0
    @ObservationIgnored private var swapPress = Press()
    @ObservationIgnored private var plusPress = Press()
    @ObservationIgnored private let light = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    @ObservationIgnored private let soft = UIImpactFeedbackGenerator(style: .soft)
    @ObservationIgnored private let selection = UISelectionFeedbackGenerator()
    @ObservationIgnored private let success = UINotificationFeedbackGenerator()

    private struct Press {
        var value = 1.0, from = 1.0, target = 1.0, velocity = 0.0, elapsed = 0.0
        var held = false, reduced = false, initialEnergy = 0.0
        mutating func begin(_ reduce: Bool) {
            held = true; reduced = reduce; from = value; target = reduce ? 0.82 : 0.96; elapsed = 0; velocity = 0
        }
        mutating func end(_ reduce: Bool) {
            held = false; reduced = reduce; from = value; target = 1; elapsed = 0
            if velocity < 0 { velocity = 0 }
            initialEnergy = 0.5 * 322.3 * pow(value - 1, 2) + 0.5 * velocity * velocity
        }
        mutating func step(_ dt: Double) {
            elapsed += dt
            if held {
                value = from + (target - from) * M.bezier(0.23, 1, 0.32, 1, elapsed / 0.1)
            } else if reduced {
                let t = M.clamp(elapsed / 0.16), k = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
                value = from + (1 - from) * k
            } else {
                // Exact damped solution, with velocity hand-off when a press interrupts its release.
                let w = sqrt(322.3), z = 30.5 / (2 * w), wd = w * sqrt(1 - z * z)
                let a = value - 1, b = (velocity + z * w * a) / wd
                let e = exp(-z * w * dt), c = cos(wd * dt), s = sin(wd * dt)
                value = 1 + e * (a * c + b * s)
                velocity = e * (-z * w * (a * c + b * s) + wd * (-a * s + b * c))
                let energy = 0.5 * 322.3 * pow(value - 1, 2) + 0.5 * velocity * velocity
                if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { value = 1; velocity = 0 }
            }
        }
        var settled: Bool { !held && value == 1 && velocity == 0 }
    }

    func configure(width: Double, auto: Bool, reduce: Bool, pin: Double?, haptics: Bool, autoMask: Double) {
        self.haptics = haptics; self.autoMask = autoMask
        guard !configured || g.width != width || self.auto != auto || self.reduce != reduce || self.pin != pin else { return }
        configured = true; self.auto = auto; self.reduce = reduce; self.pin = pin
        g = M.Geo(width: width); live = M.Live(g); loopT = 0; playing = auto && !reduce
        revealed = false; targetRevealed = false; previous = nil; fingerDown = false
        look = M.timeline(pin ?? 0, g)
        gateDone = pin != nil; gateWait = 0; gateSmooth = 0; appearanceTime = 0
        contentOpacity = pin == nil ? 0 : 1
        if let pin {
            let t = M.wrap(pin)
            revealed = t >= 3.46 && t < 6.5
            targetRevealed = look.front >= (g.front0 + g.front1) / 2
            stop()
        } else { wake() }
    }
    func setActive(_ value: Bool) {
        active = value
        if value { previous = nil; wake() } else { stop() }
    }
    private func wake() {
        guard active && pin == nil && link == nil else { return }
        previous = nil
        let proxy = ReededGlassRevealDisplayLink(self)
        let link = CADisplayLink(target: proxy, selector: #selector(ReededGlassRevealDisplayLink.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        self.proxy = proxy; self.link = link
    }
    func stop() { link?.invalidate(); link = nil; proxy = nil; previous = nil }
    func tick(_ timestamp: Double) {
        let elapsed = max(0, previous.map { timestamp - $0 } ?? 0.0167), dt = min(elapsed, 0.05)
        previous = timestamp
        if !gateDone {
            gateWait += elapsed
            gateSmooth = elapsed < 0.04 ? gateSmooth + 1 : 0
            gateDone = gateSmooth >= 8 && gateWait > 0.3
            if !gateDone { loopT = 0; look = M.timeline(0, g); return }
        }
        // FadeIn.duration(220) uses withTiming's default inOut quadratic easing.
        if contentOpacity < 1 {
            let p = M.clamp(appearanceTime / 0.22)
            contentOpacity = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
            appearanceTime += elapsed
        }
        clock += elapsed
        swapPress.step(dt); plusPress.step(dt)
        swapHeld = swapPress.value; plusHeld = plusPress.value
        if command {
            command = false; takeOver()
            let to = live.target >= g.front1 - 1 ? g.front0 : g.front1
            if reduce { live.target = to }
            else if abs(to - live.fx) > 1 { M.startSweep(&live, g, to) }
            touchedAt = clock
        }
        if playing {
            loopT = M.wrap(loopT + elapsed); look = M.timeline(loopT, g)
            revealed = loopT >= 3.46 && loopT < 6.5
            targetRevealed = look.front >= (g.front0 + g.front1) / 2
            return
        }
        let ev = M.step(&live, dt, g, reduce, clock)
        if ev.edge { cue(2) } else if ev.tick { cue(1) }
        if ev.revealed {
            revealedAt = clock; revealed = true; cue(3); onReveal?()
            UIAccessibility.post(notification: .announcement, argument: "Bank Account, number 4829 1730 2644")
        }
        if ev.masked {
            revealed = false; cue(4); onMask?()
            UIAccessibility.post(notification: .announcement, argument: "Bank Account, number hidden, ending 2644")
        }
        let idle = !live.touching && live.mode != .drag && live.mode != .sweep && live.mode != .hold
        if auto && !reduce {
            if !live.touching && live.mode == .spring && live.peek < 0.02 && clock - touchedAt >= 2 {
                if live.target != g.front0 { live.mode = .spring; live.target = g.front0 }
                if M.atRest(live, g) {
                    live = M.Live(g); loopT = 0; playing = true; look = M.timeline(0, g); revealed = false
                    return
                }
            }
        } else if idle && live.revealed && autoMask > 0 && clock - revealedAt > autoMask && clock - touchedAt > 2 {
            if reduce { live.target = g.front0 }
            else if live.target != g.front0 { M.startSweep(&live, g, g.front0) }
        }
        look = M.liveLook(live)
        targetRevealed = live.target >= g.front1 - 1
        if idle && (!auto || reduce) && M.atRest(live, g) && swapPress.settled && plusPress.settled && contentOpacity == 1 { stop() }
    }
    private func takeOver() {
        if playing { live = M.fromTimeline(loopT, g); playing = false }
    }
    func begin(_ x: Double) {
        guard pin == nil else { return }
        takeOver(); wake(); fingerDown = true
        live.touching = true; live.touchX = x; live.mode = .hold; live.fv = 0
        live.dragged = false; live.holdT = 0; live.peekRel = -1
        startX = x; frontStart = live.fx; dragged = false; touchedAt = clock; cue(0)
    }
    func move(_ x: Double, translation: Double, velocity: Double) {
        guard fingerDown else { return }
        live.touchX = x
        let dx = x - startX
        if live.peek <= 0.3 {
            if !reduce {
                let dead = live.fx <= g.front0 + 1 && !live.revealed ? 12.0 : 6.0
                if !dragged && abs(dx) > dead {
                    if live.fx <= g.front0 + 1 && !live.revealed { live.flip = startX > g.width * 0.5 && dx < 0 }
                    live.dragged = true; live.peek = 0; dragged = true
                }
                if dragged || abs(dx) > dead { M.drag(&live, g, frontStart, translation, velocity) }
            } else if !dragged && abs(dx) > 6 { live.dragged = true; dragged = true }
        }
        touchedAt = clock
    }
    func end(_ velocity: Double, success: Bool) {
        guard fingerDown else { return }; fingerDown = false
        M.finish(&live, g, dragged, velocity, reduce, success); touchedAt = clock
    }
    func holdButton(_ plus: Bool, feedback: Bool = true) {
        guard pin == nil else { return }
        takeOver(); wake()
        live.touching = true; live.touchX = -1e9; live.mode = .hold; live.fv = 0; live.dragged = true
        targetRevealed = live.target >= g.front1 - 1; touchedAt = clock
        if plus { plusPress.begin(reduce) } else { swapPress.begin(reduce) }
        if feedback { cue(0) }
    }
    func releaseButton(_ plus: Bool) {
        guard pin == nil else { return }
        live.touching = false; live.mode = .spring; live.dragged = false; touchedAt = clock
        if plus { plusPress.end(reduce) } else { swapPress.end(reduce) }
    }
    func toggle() { guard pin == nil else { return }; command = true; wake() }
    func pressCue() { cue(0) }
    private func cue(_ kind: Int) {
        guard haptics else { return }
        let now = CACurrentMediaTime(), isTick = kind == 1
        if now - (isTick ? lastTick : lastCue) < (isTick ? 0.06 : 0.04) { return }
        if isTick { lastTick = now } else { lastCue = now }
        switch kind {
        case 1: selection.selectionChanged()
        case 2: rigid.impactOccurred()
        case 3: success.notificationOccurred(.success)
        case 4: soft.impactOccurred()
        default: light.impactOccurred()
        }
    }
}

@MainActor
private final class ReededGlassRevealDisplayLink: NSObject {
    weak var owner: ReededGlassRevealDriver?
    init(_ owner: ReededGlassRevealDriver) { self.owner = owner }
    @objc func tick(_ link: CADisplayLink) { owner?.tick(link.timestamp) }
}
