import UIKit

/// The RN AUTO/LIVE/SEQ state machine, with value-preserving takeover and analytic live springs.
final class IslandGlassOrbEngine {
    typealias M = IslandGlassOrbMotion
    typealias Frame = M.Frame
    private struct Channel {
        var from: Double, to: Double, velocity: Double, at: Double, d: Double, k: Double, duration: Double, initialEnergy: Double
    }
    var autoPlay = true, reduced = false, level = -1.0, hold = 6.0
    var onListen: (() -> Void)?, onSubmit: (() -> Void)?, onCancel: (() -> Void)?, onReplyEnd: (() -> Void)?
    var mode = 0, time = 0.0, now = 0.0, clock = 0.0
    var height = 874.0
    var pin: Double?
    private var configuredPin = false, broken = false
    private var pinchAt = -10.0, mergeAt = -10.0
    private var history: [(t: Double, a: Double)] = []
    func configurePin(_ value: Double?) { if !configuredPin { configuredPin = true; pin = value; if let value { time = value } } }
    private func autoFrame(_ t: Double) -> Frame {
        var f = M.frame(t); let a = IslandGlassOrbArtwork.artScale(height)
        let cy = 58 + f["dy"]!, x = (200 + f["dx"]! - 201) * (a-1) * M.smooth(250,300,cy), y = max(0,cy-276) * (a-1), mc = M.clamp(f["m"]!)
        f["dx"]! += x; f["dy"]! += y; f["lineX"]! += x * mc; f["lineY"]! += y * mc
        return f
    }
    private var last: Double?
    private var l: Frame = ["m": 0, "pill": 0, "ban": 0, "dx": 0, "dy": 0, "amp": 0, "mic": 0, "spin": 0, "pluckX": 0, "pluckD": 0], channels: [String: Channel] = [:]
    private var listening = false, silent = 0.0, pluckRel = -1.0, pluckD0 = 0.0
    private var seqT = 0.0, seqStart = 0.0, seqBlend: Frame = [:], seqFrom = M.frame(0), replyAt = -1.0
    private var touching = false, lastTouch = -10.0, region = 0, downT = 0.0, moved = 0.0
    private var grab = false, grab0 = CGPoint.zero, grabT = CGPoint.zero, lastTr = CGPoint.zero
    private var plucking = false, atLimit = false, lineSide = 0, lastSel = 0.0, scrub = false, fired = false
    private var start = CGPoint.zero, configured = false

    func configure(autoPlay: Bool, reduced: Bool, level: Double?, hold: Double) {
        self.autoPlay = autoPlay; self.reduced = reduced
        self.level = level.map { M.clamp($0) } ?? -1; self.hold = hold
        if !configured { configured = true; mode = autoPlay && !reduced ? 0 : 1 }
        if (!autoPlay || reduced) && mode == 0 { mode = 1 }
    }
    func suspend() { last = nil }
    private func get(_ k: String) -> Double { l[k] ?? 0 }
    private func set(_ k: String, _ v: Double) { channels[k] = nil; l[k] = v }
    private func timing(_ k: String, _ to: Double, _ duration: Double) {
        if reduced { set(k, to); return }
        if let old = channels[k], old.duration > 0, old.to == to { return }
        channels[k] = Channel(from: get(k), to: to, velocity: 0, at: now, d: 0, k: 0, duration: duration, initialEnergy: 0)
    }
    private func spring(_ k: String, _ to: Double, _ config: (d: Double, k: Double), _ velocity: Double = 0) {
        if reduced { timing(k, to, 0.2); return }
        // Reanimated continues an already running spring with the same target.
        if let old = channels[k], old.duration == 0, old.to == to { return }
        var v = velocity + (channels[k].map { derivative($0) } ?? 0)
        if (to > get(k) && v < 0) || (to < get(k) && v > 0) { v = 0 }
        let initial = velocity * velocity + config.k * pow(get(k) - to, 2)
        channels[k] = Channel(from: get(k), to: to, velocity: v, at: now, d: config.d, k: config.k, duration: 0, initialEnergy: initial)
    }
    private func derivative(_ c: Channel) -> Double {
        guard c.duration == 0 else { return 0 }
        let t = now - c.at, x = c.from - c.to, w = sqrt(c.k), z = c.d / (2 * w), wd = w * sqrt(1 - z * z)
        let envelope = exp(-z * w * t), sin1 = sin(wd * t), cos1 = cos(wd * t)
        let fragment = envelope * (sin1 * (c.velocity + z * w * x) / wd + x * cos1)
        return -z * w * fragment + envelope * (cos1 * (c.velocity + z * w * x) - wd * x * sin1)
    }
    private func animate() {
        for (key, c) in channels {
            let t = now - c.at
            if c.duration > 0 {
                let u = M.clamp(t / c.duration), e = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
                l[key] = M.mix(c.from, c.to, e)
                if u >= 1 { channels[key] = nil }
            } else {
                l[key] = M.spring(t, c.from, c.to, c.velocity, c.d, c.k)
                let v = derivative(c)
                let energy = v * v + c.k * pow(get(key) - c.to, 2)
                if c.initialEnergy == 0 || energy <= c.initialEnergy * 6e-9 { l[key] = c.to; channels[key] = nil }
            }
        }
    }
    private func live() -> Frame {
        var values = l; values["broken"] = broken ? 1 : 0; values["ts"] = now - pinchAt
        var f = M.live(values, clock: clock)
        let tm = now - mergeAt
        if tm >= 0 && tm < 0.3 { f["ih"]! += 10 * sin(.pi * tm / 0.3) }
        return f
    }
    private func sequence() -> Frame {
        if reduced {
            let think = M.thinkRest(), reply = M.replyRest()
            if seqT < 4.878 { return M.blend(seqFrom, think, M.seg(seqT, 4.678, 4.878)) }
            if seqT < 5.95 { return think }
            if seqT < 6.15 { return M.blend(think, reply, M.seg(seqT, 5.95, 6.15)) }
            if seqT < 8.95 { return reply }
            if seqT < 9.15 { return M.blend(reply, M.frame(0), M.seg(seqT, 8.95, 9.15)) }
            return M.frame(0)
        }
        var f = M.frame(seqT)
        let k = 1 - M.out(M.seg(seqT - seqStart, 0, 0.35))
        if k > 0 { for key in M.blendKeys { f[key, default: 0] += (seqBlend[key] ?? 0) * k } }
        return f
    }
    func frame() -> Frame { mode == 0 ? autoFrame(pin ?? time) : mode == 2 ? sequence() : live() }
    var lobeTime: Double {
        let raw = mode == 0 ? pin ?? time : mode == 2 ? seqT : clock
        return (raw.truncatingRemainder(dividingBy: M.loop) + M.loop).truncatingRemainder(dividingBy: M.loop)
    }
    private func adopt(_ f: Frame) {
        for key in ["m", "pill", "ban", "dx", "dy", "amp", "mic", "pluckX"] { set(key, f[key] ?? 0) }
        broken = (f["free"] ?? 0) > 0.9 && hypot(f["dx"] ?? 0,f["dy"] ?? 0) > 160
        pinchAt = broken ? now - 0.16 : -10; mergeAt = -10
        set("e", f["e"] ?? 0); set("ampLag",f["ampLag"] ?? 0); set("causticAmp",f["causticAmp"] ?? 0)
        set("spin", f["spinA"] ?? 0); set("pluckD", 0)
    }
    private func idle() {
        spring("m", 0, M.snap); spring("pill", 0, M.snap); spring("ban", 0, M.glide)
        spring("dx", 0, M.glide); spring("dy", 0, M.glide)
        timing("mic", 0, 0.15); timing("spin", 0, 0.15); listening = false
    }
    private func listen() {
        spring("m", 1, M.pop); spring("pill", 0, M.snap); spring("ban", 0, M.pop)
        timing("mic", 1, 0.15); timing("spin", 0, 0.15)
        listening = true; silent = 0; mode = 1; buzz(.soft); onListen?()
    }
    private func submit() {
        let f = live(), f0 = M.frame(4.678)
        seqBlend = Dictionary(uniqueKeysWithValues: M.blendKeys.map { ($0, f[$0]! - f0[$0]!) })
        seqFrom = f; seqT = 4.678; seqStart = 4.678; replyAt = -1; listening = false; mode = 2; onSubmit?()
    }
    private func cancel() { adopt(sequence()); mode = 1; idle(); buzz(.light); onCancel?() }
    private func collapse() { spring("ban", 0, M.glide); set("pill", 0); set("m", 0); onReplyEnd?() }
    private func takeOver() {
        pin = nil
        guard mode == 0 else { return }
        let t = time, f = autoFrame(t)
        if t >= 4.678 && t < 9.55 {
            seqT = t; seqStart = t; seqBlend = [:]; seqFrom = f
            replyAt = t >= 6.25 ? now - (t - 6.25) : -1; mode = 2; return
        }
        adopt(f); clock = t; mode = 1
        if t >= 0.62 && t < 4.678 {
            listening = true; silent = 0; spring("m", 1, M.pop); spring("dx", 0, M.honey); spring("dy", 0, M.honey); timing("mic", 1, 0.15)
        } else { idle() }
    }
    func step(_ stamp: Double) {
        let dt = min(last.map { max(0, stamp - $0) } ?? 0.016, 0.05); last = stamp; now += dt
        if let pin { time = pin; return }
        animate()
        let md = mode
        if md == 0 { time = (time + dt).truncatingRemainder(dividingBy: 10); return }
        clock = (clock + dt).truncatingRemainder(dividingBy: 10)
        if touching && !fired && moved < 10 && now - downT >= 0.22 {
            if region == 1 || region == 3 {
                fired = true; if region == 3 { set("m", 0) }; listen()
                if region == 1 { grab = true; grab0 = .zero; grabT = lastTr; set("dx", 0); set("dy", 0) }
            } else if region == 2 && !plucking && !grab {
                fired = true; grab = true; grab0 = CGPoint(x: get("dx"), y: get("dy")); grabT = lastTr
                set("dx", get("dx")); set("dy", get("dy"))
            }
        }
        if mode == 1 && get("m") > 0.5 {
            let travel = hypot(get("dx"), get("dy"))
            if !broken && travel >= 250 { broken = true; pinchAt = now; mergeAt = -10; set("e", -0.07); buzz(.rigid) }
            else if broken && !grab && travel < 30 { broken = false; mergeAt = now; buzz(.medium) }
        }
        if listening {
            let target = plucking ? 0.6 : level >= 0 ? level : 0, a = get("amp"), tau = target > a ? 0.06 : 0.18
            set("amp", a + (target - a) * (1 - exp(-dt / tau)))
            silent = get("amp") < 0.08 && !touching ? silent + dt : 0
            if silent >= 1.2 && get("m") > 0.9 && !broken && hypot(get("dx"), get("dy")) < 20 { submit() }
        } else if md == 1 { set("amp", get("amp") * exp(-dt / 0.18)) }
        history.append((now,get("amp"))); history.removeAll { $0.t < now - 0.25 }
        for (key, delay) in [("ampLag",0.06),("causticAmp",0.08)] {
            let target = now - delay
            var value = history.first?.a ?? get("amp")
            for i in 1..<max(1,history.count) {
                let a = history[i-1], b = history[i]
                value = b.a
                if b.t >= target { value = M.mix(a.a,b.a,M.clamp((target-a.t)/max(b.t-a.t,0.0001))); break }
            }
            set(key,value)
        }
        if pluckRel >= 0 {
            let t = now - pluckRel
            if t > 0.5 { pluckRel = -1; set("pluckD", 0) }
            else { set("pluckD", pluckD0 * cos(2 * .pi * 6 * t) * exp(-t / 0.11)) }
        }
        if mode == 2 {
            var s = seqT + dt
            if s >= 6.25 && replyAt < 0 { replyAt = now }
            if s > 8.9 && seqT <= 8.9 && now - max(replyAt, lastTouch) < hold { s = 8.9 }
            seqT = s
            if s >= 9.95 { adopt(M.frame(0)); mode = 1; lastTouch = now; onReplyEnd?() }
            return
        }
        if !listening && !touching && get("ban") > 0.5 && now - max(lastTouch, replyAt) > hold { collapse(); lastTouch = now }
        let resting = abs(get("m")) < 0.01 && abs(get("ban")) < 0.01 && abs(get("pill")) < 0.01 && abs(get("dx")) + abs(get("dy")) < 0.5 && get("spin") < 0.01
        if autoPlay && !reduced && !touching && !listening && now - lastTouch > 2 {
            if resting { time = 0; mode = 0 }
            else { idle(); lastTouch = now - 1.4 }
        }
    }
    private func hit(_ p: CGPoint, _ oy: Double) -> Int {
        let f = frame(), s = M.shapes(f, oy: oy), b = s.box, d = s.drop
        let inBox = p.x > b.minX - 10 && p.x < b.maxX + 10 && p.y > b.minY - 10 && p.y < b.maxY + 10
        let inIsland = abs(p.x - 201) < 85 && p.y < 71 + oy
        let qx = (p.x - d.midX) / (d.width / 2 + 12), qy = (p.y - d.midY) / (d.height / 2 + 12)
        let inDrop = f["m"]! > 0.4 && qx * qx + qy * qy < 1
        if mode == 2 {
            if seqT >= 4.3 && seqT < 5.95 && inBox { return 4 }
            if seqT >= 6.9 && seqT < 8.95 && inBox { return 3 }
            return 0
        }
        if listening && (inDrop || (inIsland && !broken)) { return 2 }
        if get("ban") > 0.5 && inBox { return 3 }
        if get("m") < 0.3 && get("ban") < 0.3 && inIsland { return 1 }
        return 0
    }
    func begin(_ p: CGPoint, oy: Double) {
        takeOver(); touching = true; lastTouch = now; downT = now; moved = 0; fired = false
        plucking = false; scrub = false; atLimit = false; lastTr = .zero; start = p
        region = hit(p, oy)
        if region == 3 && mode == 2 { adopt(sequence()); set("pill", 0); set("m", 0); mode = 1 }
        if region == 2 && abs(get("dx")) + abs(get("dy")) > 3 {
            grab = true; fired = true; grab0 = CGPoint(x: get("dx"), y: get("dy")); grabT = .zero
            set("dx", get("dx")); set("dy", get("dy"))
        }
        lineSide = p.y > frame()["lineY"]! ? 1 : -1
    }
    func move(_ p: CGPoint, scale: Double) {
        lastTr = CGPoint(x: p.x - start.x, y: p.y - start.y)
        moved = max(moved, hypot(lastTr.x, lastTr.y) * scale)
        if grab {
            let x = min(max(200 + grab0.x + lastTr.x - grabT.x,40),362) - 200
            let y = min(max(58 + grab0.y + lastTr.y - grabT.y,48),700) - 58
            set("e", broken ? 0 : -0.07 * M.clamp(hypot(x,y)/250))
            set("dx", x); set("dy", y); return
        }
        if region == 2 && moved > 6 {
            plucking = true; fired = true; let f = frame()
            set("pluckX", p.x); set("pluckD", min(max(p.y - f["lineY"]!, -18), 18)); pluckRel = -1
            let side = p.y > f["lineY"]! ? 1 : -1
            if side != lineSide && now - lastSel > 0.06 { lastSel = now; UISelectionFeedbackGenerator().selectionChanged() }
            lineSide = side; return
        }
        if region == 3 && moved > 6 {
            let dy = lastTr.y
            if dy > 24 && !fired { fired = true; set("m", 0); listen(); return }
            if !fired && dy < 0 { scrub = true; set("ban", M.clamp(1 + dy / 83)) }
        }
    }
    func end(velocity: CGSize, success: Bool = true) {
        let tap = success && !fired && now - downT < 0.25 && moved < 10
        if grab { grab = false; spring("dx", 0, broken ? (16,210) : M.honey, success ? velocity.width : 0); spring("dy", 0, broken ? (16,210) : M.honey, success ? velocity.height : 0); spring("e",0,M.honey); if success && !broken { buzz(.soft) } }
        else if plucking { plucking = false; pluckD0 = get("pluckD"); pluckRel = now }
        else if region == 2 && tap && listening && !broken { buzz(.light); submit() }
        else if region == 4 && tap { cancel() }
        else if region == 3 && !fired {
            if scrub {
                if !success || get("ban") + velocity.height / 83 * 0.12 >= 0.5 { spring("ban", 1, M.glide); replyAt = now }
                else { collapse(); buzz(.light) }
            } else if tap { collapse(); buzz(.light) }
        }
        touching = false; lastTouch = now; region = 0
    }
    func cancelGesture() { if touching { end(velocity: .zero, success: false) } }
    private func buzz(_ style: UIImpactFeedbackGenerator.FeedbackStyle) { UIImpactFeedbackGenerator(style: style).impactOccurred() }
}
