import Observation
import QuartzCore
import UIKit

@MainActor @Observable
final class ObjectSigninDissolveDriver: NSObject {
    typealias M = ObjectSigninDissolveMotion
    struct Glow: Equatable { var pool = 0.0, word = 0.0, pill = 0.0, zip = -1.0 }
    struct Frame: Equatable {
        var look = ObjectSigninDissolveMotion.lookAt(0)
        var glow = Glow()
        var clock = 0.0, lightMix = 0.0
    }
    private(set) var frame = Frame()
    var pressed = -1
    private(set) var barDark = false
    private(set) var isLight = false
    @ObservationIgnored private var link: CADisplayLink?
    @ObservationIgnored private var lastTimestamp: Double?
    @ObservationIgnored private var layout = ObjectSigninDissolveLayout(width: 402, height: 874, top: 62, bottom: 34)
    @ObservationIgnored private var configured = false
    @ObservationIgnored private var auto = false
    @ObservationIgnored private var reduce = false
    @ObservationIgnored private var playing = false
    @ObservationIgnored private var loopT = 0.0
    @ObservationIgnored private var clock = 0.0
    @ObservationIgnored private var live = M.Live(M.lookAt(0))
    @ObservationIgnored private(set) var held = false
    @ObservationIgnored private var fingerC = 0.0
    @ObservationIgnored private var touchedAt = -1e9
    @ObservationIgnored private var resuming = false
    @ObservationIgnored private var hint = -1.0
    @ObservationIgnored private var initialHintSent = false
    @ObservationIgnored private var wasOver = false
    @ObservationIgnored private var spun = false
    @ObservationIgnored private var gripYaw = 0.0
    @ObservationIgnored private var gripTime = 0.0
    @ObservationIgnored private var lastX = 0.0
    @ObservationIgnored private var lastT = 0.0
    @ObservationIgnored private var fingerV = 0.0
    @ObservationIgnored private var moved = 0.0
    @ObservationIgnored private var tickC = 0.0
    @ObservationIgnored private var lastTick = 0.0
    @ObservationIgnored private var previousRadius: Double?
    @ObservationIgnored private var gestureStarted = false
    @ObservationIgnored private let onset = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private let closing = UIImpactFeedbackGenerator(style: .rigid)
    @ObservationIgnored private let spin = UIImpactFeedbackGenerator(style: .soft)
    @ObservationIgnored private let tick = UISelectionFeedbackGenerator()

    static var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-objectSigninDissolveTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite { return t }
        #endif
        return nil
    }
    func configure(auto: Bool, reduce: Bool, layout: ObjectSigninDissolveLayout) {
        let modeChanged = !configured || self.auto != auto || self.reduce != reduce
        self.auto = auto; self.reduce = reduce; self.layout = layout; configured = true
        if modeChanged { playing = auto && !reduce }
        if let pin = Self.pinnedTime {
            let t = pin.truncatingRemainder(dividingBy: M.loop)
            frame = Frame(look: M.lookAt(pin), glow: Glow(pool: M.lookAt(t).c, word: M.lookAt(t - 0.06).c,
                          pill: M.lookAt(t - 0.12).c, zip: M.zipSince(t)), clock: pin, lightMix: M.lookAt(pin).wipeP > 0.5 ? 1 : 0)
            // A fresh RN pin is approached from zero radius; the same direction-aware status threshold applies.
            previousRadius = 0; updateBar()
        }
    }
    func start() {
        guard link == nil, Self.pinnedTime == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(advance(_:)))
        l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 120)
        l.add(to: .main, forMode: .common); link = l
    }
    func stop() { link?.invalidate(); link = nil; lastTimestamp = nil }
    func active(_ active: Bool) {
        lastTimestamp = nil
        link?.isPaused = !active
    }
    private func wake() { lastTimestamp = nil; link?.isPaused = false }
    @objc private func advance(_ sender: CADisplayLink) {
        let dt = min(lastTimestamp.map { sender.timestamp - $0 } ?? 0.0167, 0.05)
        lastTimestamp = sender.timestamp
        clock += dt
        if !auto && !reduce && !initialHintSent && clock >= 1.2 { initialHintSent = true; hint = 0 }
        var k: M.Look
        if playing {
            loopT = (loopT + dt).truncatingRemainder(dividingBy: M.loop)
            k = M.lookAt(loopT)
        } else {
            if !held && auto && !reduce && clock - touchedAt > 2 {
                if !resuming {
                    resuming = true
                    live.yawTarget = M.yaw0 + M.jsRound((live.yaw - M.yaw0) / 360) * 360
                    live.qTarget = 0
                }
                let done = abs(live.yaw - live.yawTarget) < 0.4 && abs(live.yawV) < 4 && live.c < 0.004 && live.q <= 0.0005 && abs(live.scale - 1) < 0.001
                if done {
                    resuming = false; loopT = 0; playing = true
                    frame.look = M.lookAt(0)
                    return
                }
            }
            let h = hint
            var breathe = 0.0
            if held && !reduce && clock - lastT > 0.6 { breathe = 1.5 * sin(2 * .pi * 0.5 * (clock - lastT - 0.6)) }
            M.stepLive(&live, dt, held, fingerC, reduce, breathe)
            if h >= 0 {
                let u = h / 1.1
                if u >= 1 { hint = -1 }
                else { hint = h + dt; let b = sin(.pi * u); live.c = max(live.c, 0.073 * b * b) }
            }
            k = live.look(clock)
            if h >= 0 && h / 1.1 < 1 { k.yaw += 18 * sin(.pi * h / 1.1) * (reduce ? 0 : 1) }
            k.seam = M.seamOf(k.c)
            if wasOver && live.c < 0.02 { wasOver = false; closing.impactOccurred() }
            else if live.c > 0.15 { wasOver = true }
            let spinNow = abs(live.yawV) > (540 + 1260) / 2
            if spinNow != spun { spun = spinNow; if !reduce { spin.impactOccurred() } }
        }
        let old = frame.glow
        func lag(_ current: Double, _ tau: Double) -> Double { tau <= 0 ? k.c : current + (k.c - current) * (1 - exp(-dt / tau)) }
        var zip = old.zip >= 0 ? old.zip + dt : -1
        if zip > 1 { zip = -1 }
        if playing { zip = M.zipSince(loopT) }
        else if old.pool > 0.2 && k.c < 0.02 && old.zip < 0 { zip = 0 }
        let lightTarget = k.wipeP > 0.5 ? 1.0 : 0.0
        frame = Frame(look: k, glow: Glow(pool: lag(old.pool, 0), word: lag(old.word, 0.06), pill: lag(old.pill, 0.12), zip: zip),
                      clock: clock, lightMix: frame.lightMix + (lightTarget - frame.lightMix) * (1 - exp(-dt / 0.07)))
        updateBar()
    }
    private func updateBar() {
        let radius = reduce ? frame.lightMix * layout.reach : layout.reach * M.ease(frame.look.wipeP)
        let dBar = hypot(layout.width / 2 + 80, layout.height + 80 - layout.top * 0.6)
        if reduce { barDark = radius > layout.reach * 0.5 }
        else if let prev = previousRadius {
            if radius > prev && radius > dBar - 260 { barDark = true }
            else if radius < prev && radius < dBar + 260 { barDark = false }
        }
        previousRadius = radius
    }
    private func takeOver() {
        resuming = false
        if playing { live = M.Live(frame.look); playing = false }
        wake()
    }
    private func cOfY(_ y: Double) -> Double { (y - layout.cy - (-214) * layout.scale) / (410 * layout.scale) }
    func begin(_ p: CGPoint) {
        guard !gestureStarted, Self.pinnedTime == nil else { return }
        gestureStarted = true
        guard layout.heroBox.contains(p) else { return }
        takeOver(); held = true; touchedAt = clock; fingerC = cOfY(p.y)
        gripYaw = live.yaw; gripTime = clock
        lastX = p.x; lastT = clock; fingerV = 0; moved = 0; tickC = fingerC; lastTick = clock; spun = false
        live.yawV = 0
        if !reduce { onset.impactOccurred() }
    }
    func move(_ p: CGPoint, translation: CGSize) {
        guard held else { return }
        let yaw = gripYaw + translation.width * 0.55
        let v = (p.x - lastX) * 0.55 / max(clock - lastT, 0.001)
        fingerV += (v - fingerV) * 0.35
        live.yaw = yaw; live.yawV = fingerV; fingerC = cOfY(p.y)
        moved = max(moved, hypot(translation.width, translation.height))
        if abs(fingerC - tickC) * 410 * layout.scale >= 25 * layout.scale && clock - lastTick >= 0.1 {
            tickC = fingerC; lastTick = clock; tick.selectionChanged()
        }
        touchedAt = clock; lastX = p.x; lastT = clock
    }
    func end(velocity: Double, cancelled: Bool = false) {
        defer { held = false; touchedAt = clock; gestureStarted = false }
        guard held else { return }
        if !cancelled {
            let tap = moved < 8 && clock - gripTime < 0.18
            let fling = reduce ? 0 : M.clamp(velocity * 0.55, -1080 * 6.02, 1080 * 6.02)
            let v = tap ? 0 : fling
            live.yawV = v; live.yawTarget = M.heroNear(live.yaw, reduce ? 0 : v)
            if tap && !reduce { hint = 0 }
        }
    }
    func toggleTheme() {
        guard Self.pinnedTime == nil else { return }
        onset.impactOccurred(); takeOver(); isLight.toggle()
        live.qTarget = isLight ? 1 : 0
        if reduce { live.q = live.qTarget }
        touchedAt = clock
    }
    func bump(_ direction: Double) {
        guard Self.pinnedTime == nil else { return }
        takeOver(); live.yawTarget = live.yaw + direction * 180; live.yawV = 0; touchedAt = clock
    }
    func press() { onset.impactOccurred() }
}
