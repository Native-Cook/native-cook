import Foundation
import UIKit

@MainActor
final class ParticleRibbonVoiceDriver {
    typealias M = ParticleRibbonVoiceMotion
    struct Snapshot {
        var frame: M.Frame
        var cap: [Double], ask: [Double], clock: Double, textAlpha: Double
        var mode: Int
    }
    private var live = M.Live()
    private var current = M.frameAt(0), cap = M.captionAt(0), ask = M.askAt(0)
    private var playing = false, initialized = false, active = true
    private var previous: Date?, epoch: Date?, appeared: Date?, inactiveAt: Date?
    private var loopT = 0.0, clock = 0.0, rLevel = 0.0, resume = -1.0
    private var resumeFrom = M.frameAt(0)
    private var lastMode = 0, lastWords = 0, lastCross = 0
    private var tapStarts: [Int:Double] = [:], tapMoved = Set<Int>()
    private let feedback = ParticleRibbonVoiceFeedback()
    private var autoEnabled = false, reduced = false
    var sound = true, touchable = true, tapToToggle = false
    var pinned: Double?

    init() { feedback.prepare() }
    func reset(auto: Bool, reduce: Bool, at now: Date = Date()) {
        live = M.Live(); playing = auto && !reduce; autoEnabled = auto; reduced = reduce
        previous = now; epoch = now; initialized = true
        loopT = 0; resume = -1; rLevel = 0; cap = M.captionAt(0); ask = M.askAt(0)
        current = reduce ? M.reducedFrame(0,0,1):M.frameAt(0)
        tapStarts.removeAll(); tapMoved.removeAll()
        lastMode = 0; lastWords = 0; lastCross = 0
        if appeared == nil { appeared = now }
    }
    func setActive(_ value: Bool, at now: Date) {
        if !value {
            inactiveAt = now; active = false; feedback.stop()
            // Match onTouchesCancelled for every touch owned by this screen.
            let ids = touchIDs
            for id in ids { up(id) }
        } else {
            if let start = inactiveAt {
                let interval = now.timeIntervalSince(start)
                epoch = epoch?.addingTimeInterval(interval)
                appeared = appeared?.addingTimeInterval(interval)
            }
            inactiveAt = nil; previous = now; active = true
        }
    }
    private var touchIDs: [Int] {
        var ids = Set(tapStarts.keys)
        if live.talk>0 { ids.insert(live.talk-1) }
        for k in 0..<2 where live.win[k*11] == 1 { ids.insert(Int(live.win[k*11+1])) }
        return Array(ids)
    }
    func snapshot(at now: Date, auto: Bool, reduce: Bool, pin: Double?) -> Snapshot {
        pinned = pin
        if !initialized || auto != autoEnabled || reduce != reduced { reset(auto:auto,reduce:reduce,at:now) }
        let dt = active ? min(max(now.timeIntervalSince(previous ?? now),0),0.05):0
        previous = now
        if active { clock += dt }
        let elapsed = max(0,now.timeIntervalSince(appeared ?? now))
        let t = M.clamp01(elapsed/0.32)
        // withTiming's default in-out quadratic font-ready fade.
        let alpha = reduce ? 1:(t<0.5 ? 2*t*t:1-pow(-2*t+2,2)/2)
        if let pin = pinned {
            // RN pins the story but leaves its independent clock (the held control's halo) running.
            return Snapshot(frame:M.frameAt(pin),cap:M.captionAt(pin),ask:M.askAt(pin),clock:clock,textAlpha:alpha,mode:M.stateAt(pin))
        }
        guard active else { return Snapshot(frame:current,cap:cap,ask:ask,clock:clock,textAlpha:alpha,mode:lastMode) }
        if playing && !reduce {
            loopT = M.wrap(now.timeIntervalSince(epoch ?? now))
            current = M.frameAt(loopT); cap = M.captionAt(loopT); ask = M.askAt(loopT)
            announceMode(M.stateAt(loopT))
            return Snapshot(frame:current,cap:cap,ask:ask,clock:clock,textAlpha:alpha,mode:lastMode)
        }
        let tr = resume
        M.liveStep(&live,dt,true)
        let ease = 1-exp(-dt/0.06), held = live.talk>0 || live.mode == 1 ? 1.0:0
        for i in 0..<7 { ask[i] *= 1-ease }
        ask[7] += (held-ask[7])*ease
        announceMode(live.mode)
        if live.cross>lastCross { feedback.cue("cross",sound:sound) }
        lastCross = live.cross
        if live.words == 1 && lastWords == 0 && !playing && !reduce { feedback.cue("answer",sound:sound) }
        lastWords = live.words
        if reduce {
            rLevel += (live.level-rLevel)*(1-exp(-dt/0.07))
            current = M.reducedFrame(live.mode,rLevel,0.5+0.5*sin(2*Double.pi*clock/2.4)); cap = live.cap
        } else if auto && live.mode == 0 && live.quiet>=2 && live.talk == 0 {
            if tr<0 {
                resumeFrom = M.liveFrame(live); resume = 0
                current = M.liveFrame(live); cap = live.cap
            } else {
                let next = tr+dt/0.6
                if next>=1 { resume = -1; loopT = 0; epoch = now; playing = true }
                else {
                    resume = next
                    let k = M.smooth(0,1,next)
                    current = M.blendFrame(resumeFrom,M.frameAt(0),k); cap = M.lerpArr(live.cap,M.captionAt(0),k)
                }
            }
        } else {
            if tr>=0 { resume = -1 }
            current = M.liveFrame(live); cap = live.cap
        }
        return Snapshot(frame:current,cap:cap,ask:ask,clock:clock,textAlpha:alpha,mode:lastMode)
    }
    private func announceMode(_ mode: Int) {
        guard mode != lastMode else { return }
        lastMode = mode
        if mode>0 { UIAccessibility.post(notification:.announcement,argument:ParticleRibbonVoiceResources.states[mode]) }
    }
    private func takeOver() {
        resume = -1
        if playing { playing = false; M.liveFromLoop(&live,loopT) }
    }
    func down(_ id: Int, _ x: Double, _ y: Double) {
        guard pinned == nil else { return }
        if tapToToggle { tapStarts[id] = clock; tapMoved.remove(id); return }
        takeOver()
        let c = M.lineY(x,current), twist = M.twistBase(x,current)
        let result = M.liveDown(&live,id,x,y,c,touchable && !reduced,twist)
        if result == 1 { feedback.cue("onset",sound:sound) }
        else if result == 2 { feedback.cue("part",sound:sound) }
    }
    func move(_ id: Int, _ x: Double, _ y: Double) {
        guard pinned == nil else { return }
        if tapToToggle { tapMoved.insert(id); return }
        let c = M.lineY(x,current)
        M.liveMove(&live,id,x,y,c)
    }
    func up(_ id: Int, cancelled: Bool = false) {
        guard pinned == nil else { return }
        if tapToToggle {
            if !cancelled, let start = tapStarts[id], !tapMoved.contains(id), clock-start<0.35 { toggle(cues:true) }
            tapStarts.removeValue(forKey:id); tapMoved.remove(id); return
        }
        let result = M.liveUp(&live,id,true)
        if result == 1 { feedback.cue("release",sound:sound) }
    }
    func toggle(cues: Bool = false) {
        guard pinned == nil else { return }
        // VoiceOver intentionally follows RN's runA11yToggle: no cue and no loop hand-off.
        if cues { takeOver() } else { playing = false; resume = -1 }
        let on = live.mode == 1
        if on { M.liveMode(&live,2) }
        else { live.talkLevel = 0.6; if !cues { live.talk = 0 }; M.liveMode(&live,1) }
        if cues { feedback.cue(on ? "release":"onset",sound:sound) }
    }
    func stop() { feedback.stop() }
}
