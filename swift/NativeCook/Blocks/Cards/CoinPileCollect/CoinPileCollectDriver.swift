import AVFoundation
import CoreMotion
import Foundation
import UIKit

/// cpc-hook's accumulator, UI modes, touch inputs, cue gates and gravity adapter.
final class CoinPileCollectDriver {
    typealias M = CoinPileCollectMotion
    struct Frame { let a: [Double]; let theta: Double }
    let cfg = CoinPileCollectConfig()
    let a = M.makeState()
    let inp = M.makeInputs()
    let ev = M.makeEvents()
    private let motion = CMMotionManager()
    private var previous: Date?
    private var acc = 0.0
    private var idleT = 0.0
    private var restoreT = 0.0
    private var th0 = 0.0
    private var th1 = 0.0
    private var theta = 0.0
    private var mode = 1
    private var slide = 0.0
    private var fx = 0.0
    private var fy = 0.0
    private var command = 0.0
    private var cueClock = 0.0
    private var lastCue = 0.0
    private var coinSeq = 0.0
    private var collectedAcc = 0.0
    private var lastCallback = -1.0
    private var flip = false
    private var players: [String: AVAudioPlayer] = [:]
    private var pinned: Double?
    private(set) var fingerDown = 0
    private var total = 22034.0
    var autoPlay = false
    var reduce = false
    var haptics = true
    var sound = false {
        didSet {
            if sound && !oldValue { prepareSounds() }
            else if !sound && oldValue {
                for player in players.values { player.stop() }
                players.removeAll()
            }
        }
    }
    var onCollect: ((Double, Double) -> Void)?
    var onEmpty: ((Double) -> Void)?
    var onRefill: (() -> Void)?

    init() { M.initState(cfg, a, total) }

    func reset(total: Double, autoPlay: Bool, reduce: Bool) {
        self.total = total; self.autoPlay = autoPlay; self.reduce = reduce
        M.initState(cfg, a, total); M.resetInputs(inp)
        for i in ev.values.indices { ev.values[i] = 0 }
        previous = nil; acc = 0; idleT = 0; restoreT = 0; theta = 0; pinned = nil
        mode = autoPlay && !reduce ? 0 : 1; fingerDown = 0; slide = 0
    }

    func setActive(_ active: Bool, tilt: Bool) {
        previous = nil
        if active && tilt && motion.isDeviceMotionAvailable {
            motion.deviceMotionUpdateInterval = 1 / 60
            motion.startDeviceMotionUpdates()
        } else { motion.stopDeviceMotionUpdates() }
    }

    func frame(at date: Date, pin: Double?) -> Frame {
        if let pin {
            if pinned != pin {
                M.replayTo(cfg, a, inp, ev, pin, total)
                theta = M.xcoinAngle(M.clockOf(a), a[M.CTL + M.C_KICKAT]); pinned = pin
            }
            return Frame(a: a.values, theta: theta)
        }
        let dt = min(previous.map { max(0, date.timeIntervalSince($0)) } ?? 0.0167, 0.05)
        previous = date
        acc += dt
        let steps = min(Int(floor(acc / M.DT)), 8)
        acc -= floor(acc / M.DT) * M.DT
        guard steps > 0 else { return Frame(a: a.values, theta: theta) }
        let uiMode = mode
        var gx = 0.0
        if uiMode == 1, let g = motion.deviceMotion?.gravity {
            let x = min(max(g.x, -M.GESTURE.tiltG), M.GESTURE.tiltG)
            gx = abs(x) < 0.04 ? 0 : x * 2600
        }
        let cmd = command; command = 0
        var collected = 0.0, cents = 0.0, impact = 0.0, flick = 0.0, emptied = 0.0, refilled = 0.0, latch = 0.0
        for k in 0..<steps {
            if uiMode == 0 {
                if a[M.CTL + M.C_STEP] >= (M.LOOP / M.DT).rounded() {
                    M.initState(cfg, a, total)
                    for q in ev.values.indices { ev.values[q] = 0 }
                }
                M.autoStep(cfg, a, inp, ev)
            } else {
                inp[M.IN_HOLD] = fingerDown == 1 ? 1 : 0
                inp[M.IN_SLIDE] = slide; inp[M.IN_FINGER] = fingerDown == 2 ? 1 : 0
                inp[M.IN_FX] = fx; inp[M.IN_FY] = fy; inp[M.IN_FR] = M.GESTURE.fingerRadius
                inp[M.IN_GX] = uiMode == 1 ? gx : 0
                inp[M.IN_REDUCED] = reduce ? 1 : 0; inp[M.IN_CMD] = k == 0 ? cmd : 0
                if reduce { M.reducedStep(cfg, a, inp, ev) } else { M.engineStep(cfg, a, inp, ev) }
            }
            collected += ev[M.EV_COLLECTED]; cents += ev[M.EV_CENTS]
            impact = max(impact, ev[M.EV_IMPACT]); flick += ev[M.EV_FLICK]
            emptied += ev[M.EVX_EMPTY]; refilled += ev[M.EVX_REFILLED]
            if ev[M.EVX_LATCH] != 0 { latch = ev[M.EVX_LATCH] }
        }
        let clock = M.clockOf(a), kick = a[M.CTL + M.C_KICKAT]
        if uiMode == 0 { theta = reduce ? 0 : M.xcoinAngle(clock, kick) }
        else if uiMode == 1 {
            theta = reduce ? 0 : M.xcoinAngle(clock, kick)
            idleT = fingerDown == 0 ? idleT + dt : 0
            let quiet = a[M.CTL + M.C_MODE] == M.MODE_LIVE && a[M.CTL + M.C_EMPTYT] < 0 && a[M.CTL + M.C_VALVE] < 0.01 && a[M.CTL + M.C_LATCH] < 0.5
            if autoPlay && !reduce && idleT >= M.RESTORE_IDLE && quiet {
                M.beginReturn(cfg, a, false)
                th0 = M.xcoinAngle(clock, kick); th1 = M.xcoinTarget(th0, 2 * .pi / M.XCOIN.turn)
                restoreT = 0; mode = 2
            }
        } else {
            restoreT += dt
            theta = M.xcoinEase(th0, th1, 2 * .pi / M.XCOIN.turn, restoreT / M.XCOIN.restWindow)
            if a[M.CTL + M.C_MODE] == M.MODE_LIVE && restoreT > 0.2 {
                M.initState(cfg, a, total); theta = 0; mode = 0; acc = 0; idleT = 0
            }
        }
        if uiMode != 0 && !reduce {
            cueClock += dt
            if collected > 0 {
                coinSeq += collected; collectedAcc += cents
                if coinSeq.truncatingRemainder(dividingBy: M.GESTURE.coinTick) < collected && cueClock - lastCue > M.GESTURE.hapticGap {
                    lastCue = cueClock; cue(2)
                }
            }
            if flick > 0 { cue(2) }
            if impact > M.GESTURE.impactMin && cueClock - lastCue > M.GESTURE.clickGap {
                lastCue = cueClock; cue(3)
            }
            if latch > 0 { cue(6) }
            if collectedAcc > 0 && (lastCallback < 0 || cueClock - lastCallback > 0.1) {
                let c = collectedAcc, r = a[M.CTL + M.C_TARGET]
                DispatchQueue.main.async { [weak self] in self?.onCollect?(c / 100, r / 100) }
                collectedAcc = 0; lastCallback = cueClock
            }
            if emptied > 0 { cue(4) }
            if refilled > 0 { cue(5) }
        } else if uiMode == 0 { coinSeq = 0; collectedAcc = 0 }
        return Frame(a: a.values, theta: theta)
    }

    func begin(_ p: CGPoint) {
        let onPill = p.x >= 16 && p.x <= 182 && p.y >= 260 && p.y <= 336
        fingerDown = onPill ? 1 : 2; move(p); idleT = 0; mode = 1
        if onPill && a[M.CTL + M.C_MODE] == M.MODE_LIVE && a[M.CTL + M.C_EMPTYT] < 0 { cue(1) }
    }
    func move(_ p: CGPoint) { fx = p.x; fy = p.y; slide = hypot(p.x - 99, p.y - 298) }
    func end() { fingerDown = 0; slide = 0; idleT = 0 }
    func pour() { command = 1 }
    func stop() { command = 2 }
    func refill() { command = 3 }

    private func cue(_ kind: Int) {
        // Dispatch out of the drawing callback, as scheduleOnRN does in the reference.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if self.haptics {
                switch kind {
                case 1: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                case 2, 6: UIImpactFeedbackGenerator(style: .light).impactOccurred()
                case 3: UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                case 4: UINotificationFeedbackGenerator().notificationOccurred(.success)
                default: break
                }
            }
            if kind == 3 { self.play(thunk: false) }
            if kind == 4 { self.play(thunk: true); self.onEmpty?(self.total / 100) }
            if kind == 5 { self.onRefill?() }
        }
    }
    private func prepareSounds() {
        for key in ["clink", "clink2", "thunk"] {
            if let url = Bundle.main.url(forResource: "CoinPileCollect-\(key)", withExtension: "wav") {
                // Like useCoinSound, leave the host audio session untouched. The default soloAmbient
                // session respects the ringer; this block never enables playback in silent mode.
                players[key] = try? AVAudioPlayer(contentsOf: url)
                players[key]?.prepareToPlay()
            }
        }
    }
    private func play(thunk: Bool) {
        guard sound else { return }
        if !thunk { flip.toggle() }
        let key = thunk ? "thunk" : (flip ? "clink" : "clink2")
        players[key]?.currentTime = 0; players[key]?.play()
    }
    deinit { motion.stopDeviceMotionUpdates() }
}
