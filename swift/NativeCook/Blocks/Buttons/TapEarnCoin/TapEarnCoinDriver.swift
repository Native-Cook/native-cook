import SwiftUI
import UIKit

typealias TapEarnCoinM = TapEarnCoinMotion

struct TapEarnCoinFrame {
    var scene = TapEarnCoinM.Scene()
    var gleam = -1.0, charge = 0.0, shake = 0.0, labelPop = 0.0, fade = 1.0, sheetFade = 1.0
}

// Each track samples its initial value once; rendering never writes animation state.
struct TapEarnCoinTrack {
    var from: Double, to: Double, start: Double, duration: Double
    var curve = 0 // 0 RN ease-out bezier, 1 RN ease-in-out bezier, 2 duration spring, 3 linear
    var damping = 0.82
    var spring: TapEarnCoinSpring?
    func value(_ now: Double) -> Double {
        let dt = now-start
        if dt < 0 { return from }
        if let spring { return spring.sample(dt).value }
        if dt >= duration { return to }
        let u = dt/duration
        let f: Double
        if curve == 3 { f = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2 }
        else { f = Self.bezier(u, curve == 1 ? 0.77 : 0.23, curve == 1 ? 0 : 1, curve == 1 ? 0.175 : 0.32, 1) }
        return TapEarnCoinM.mix(from,to,f)
    }
    func finished(_ now: Double) -> Bool {
        if now < start { return false }
        if let spring { return spring.sample(now-start).finished }
        return now >= start+duration
    }
    func velocity(_ now: Double) -> Double { spring?.sample(max(0,now-start)).velocity ?? 0 }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        func point(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 { let mid = (lo+hi)/2; if point(mid,x1,x2) < x { lo = mid } else { hi = mid } }
        return point((lo+hi)/2,y1,y2)
    }
}

final class TapEarnCoinDriver: ObservableObject {
    @Published private(set) var revision = 0
    var autoPlay: Bool, reduced = false, active = true
    private(set) var manual = false, open = false, pressing = false
    private(set) var taps = 3, earned = 438.0
    private var base = 438.0, origin = Date.timeIntervalSinceReferenceDate
    private var state = TapEarnCoinFrame()
    private var tracks: [WritableKeyPath<TapEarnCoinFrame,Double>: TapEarnCoinTrack] = [:]
    private var bursts: [Double] = []
    private var lastTouch = -1e10, pressStart = 0.0, lastTap = -1e10, chargeTick = -1e10, combo = 0
    private var refillAt: Double?, refillCoinAt = -1e10, resumeAt: Double?
    private var events: [(Double, () -> Void)] = []
    private var previousAutoTime = -1.0
    private var inactiveAt: Double?
    private static let labelSpring = TapEarnCoinSpring(from:1,to:0,duration:0.42,damping:0.5)
    private var audio = TapEarnCoinAudio()
    private var pressedLean = (0.0,0.0)
    init(autoPlay: Bool) { self.autoPlay = autoPlay }
    var now: Double { Date.timeIntervalSinceReferenceDate }
    var running: Bool { active && ((!reduced && autoPlay) || pressing || !events.isEmpty || !tracks.isEmpty || refillAt != nil || !bursts.isEmpty) }
    func time(_ date: Double) -> Double {
        #if DEBUG
        if autoPlay, UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
        #endif
        return max(0,date-origin).truncatingRemainder(dividingBy: TapEarnCoinM.loop)
    }
    func configure(reduced: Bool, active: Bool) {
        let n = now
        if !active && self.active { inactiveAt = n; audio.stop() }
        else if active, let stopped = inactiveAt {
            let shift = n-stopped
            origin += shift; lastTouch += shift; pressStart += shift; lastTap += shift; chargeTick += shift; refillCoinAt += shift
            if let at = refillAt { refillAt = at+shift }
            if let at = resumeAt { resumeAt = at+shift }
            for key in Array(tracks.keys) { tracks[key]?.start += shift }
            events = events.map { ($0.0+shift,$0.1) }
            for b in stride(from:0,to:bursts.count,by:4) { bursts[b] += shift }
            inactiveAt = nil
        }
        if self.reduced != reduced {
            events = []; tracks = [:]; bursts = []; pressing = false; open = false; refillAt = nil; resumeAt = nil
            state = TapEarnCoinFrame(); state.scene.amount = earned; taps = 3
            origin = n; base = earned; manual = false
        }
        self.reduced = reduced; self.active = active
        revision += 1
    }
    func frame(_ date: Double) -> TapEarnCoinFrame {
        if autoPlay && !manual && !reduced {
            let t = time(date)
            var f = TapEarnCoinFrame(); f.scene = TapEarnCoinM.sceneAt(t,base); f.gleam = TapEarnCoinM.gleamAt(t)
            if let at = [1.01,1.76,2.51,9.3].last(where:{ $0 <= t }) {
                let age = t-at
                if age < 0.09 {
                    let u = age/0.09
                    f.labelPop = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
                } else { f.labelPop = Self.labelSpring.sample(age-0.09).value }
            }
            return f
        }
        var f = state
        for (key, track) in tracks { f[keyPath:key] = track.value(date) }
        if pressing { f.charge = reduced ? 0 : TapEarnCoinM.clamp01((date-pressStart-0.3)/1.2) }
        return f
    }
    func live(_ date: Double) -> [Double] {
        if reduced { return [] }
        if autoPlay && !manual { return TapEarnCoinM.loopBursts(time(date)) }
        var out: [Double] = []
        for b in stride(from:0,to:bursts.count,by:4) where b+3 < bursts.count {
            let age = date-bursts[b]; if age < TapEarnCoinM.burstLife { out += [age,bursts[b+1],bursts[b+2],bursts[b+3]] }
        }
        return out
    }
    func refillAge(_ date: Double) -> Double {
        if autoPlay && !manual { return time(date)-(TapEarnCoinM.T.refill-0.45) }
        return date-refillCoinAt
    }
    func refillLabel(_ date: Double) -> Int {
        if let r = refillAt { return max(1,Int(ceil(r+3-date))) }; return 1
    }
    private func animate(_ key: WritableKeyPath<TapEarnCoinFrame,Double>, _ value: Double, _ duration: Double,
                         delay: Double = 0, curve: Int = 0, damping: Double = 0.82, velocity: Double = 0) {
        let n = now, f = frame(n)
        state[keyPath:key] = f[keyPath:key]
        let oldVelocity = (tracks[key]?.velocity(n) ?? 0)+velocity
        let spring = curve == 2 ? TapEarnCoinSpring(from:f[keyPath:key],to:value,duration:duration,damping:damping,velocity:oldVelocity,configVelocity:velocity) : nil
        tracks[key] = TapEarnCoinTrack(from:f[keyPath:key],to:value,start:n+delay,duration:duration,curve:curve,damping:damping,spring:spring)
    }
    private func after(_ seconds: Double, _ block: @escaping () -> Void) { events.append((now+seconds,block)) }
    private func buzz(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) { UIImpactFeedbackGenerator(style:style).impactOccurred() }
    private func cue(_ name: String, _ rate: Double = 1) { if !reduced { audio.play(name,rate:rate) } }
    private func pop() {
        animate(\.labelPop,1,0.09,curve:3)
        after(0.09) { [weak self] in self?.animate(\.labelPop,0,0.42,curve:2,damping:0.5) }
    }
    func takeOver() {
        let n = now
        lastTouch = n; resumeAt = nil
        guard !manual else { revision += 1; return }
        let t = time(n), f = frame(n), liveBursts = live(n)
        state = f; manual = true
        bursts = stride(from:0,to:liveBursts.count,by:4).flatMap { [n-liveBursts[$0],liveBursts[$0+1],liveBursts[$0+2],liveBursts[$0+3]] }
        if autoPlay && !reduced {
            earned = round(TapEarnCoinM.sceneAt(min(t+0.75,10),base).amount)
            taps = f.scene.label >= 4 ? 0 : f.scene.label
            open = t > TapEarnCoinM.T.squash && t < TapEarnCoinM.T.close0
            animate(\.scene.amount,earned,0.42)
            animate(\.scene.press,0,0.42,curve:2,damping:0.5)
            animate(\.scene.leanX,0,0.42,curve:2,damping:0.5); animate(\.scene.leanZ,0,0.42,curve:2,damping:0.5)
            animate(\.scene.later,0,0.15)
            animate(\.scene.lift,open ? 1 : 0,open ? 0.4 : 0.45,curve:1)
            animate(\.scene.morph,open ? 1 : 0,0.52,curve:1)
            animate(\.scene.travel,open ? 1 : 0,0.56,curve:1)
            animate(\.scene.dim,open ? 1 : 0,open ? 0.36 : 0.3)
            animate(\.scene.sheet,open ? 1 : 0,open ? 0.52 : 0.42,curve:2,damping:open ? 0.85 : 0.999)
            animate(\.scene.hop,0,open ? 0.4 : 0.5,curve:2,damping:open ? 0.7 : 0.4)
            if f.scene.label >= 4 { startRefill() }
        }
        revision += 1
    }
    func beginPress(_ point: CGPoint, _ layout: TapEarnCoinTheme.Layout) {
        takeOver()
        let f = frame(now)
        guard f.scene.sheet <= 0.02 && f.scene.morph <= 0.02 && f.scene.lift <= 0.02 else { return }
        pressing = true; pressStart = now
        let dx = max(-1,min(1,(point.x-layout.coinR)/(2*layout.coinR*0.32)))
        let dy = max(-1,min(1,(point.y-layout.coinR*0.55)/(2*layout.coinR*0.2)))
        pressedLean = (dx*0.075,dy*0.06)
        animate(\.scene.press,1,reduced ? 0 : 0.09); animate(\.scene.leanX,pressedLean.0,reduced ? 0 : 0.09); animate(\.scene.leanZ,pressedLean.1,reduced ? 0 : 0.09)
        buzz(); revision += 1
    }
    func endPress(success: Bool) {
        guard pressing else { return }
        let held = frame(now).charge
        pressing = false; lastTouch = now
        animate(\.scene.press,0,reduced ? 0 : 0.48,curve:reduced ? 3 : 2,damping:0.42)
        animate(\.scene.leanX,0,reduced ? 0 : 0.42,curve:reduced ? 3 : 2,damping:0.45); animate(\.scene.leanZ,0,reduced ? 0 : 0.42,curve:reduced ? 3 : 2,damping:0.45)
        state.charge = held; animate(\.charge,0,0.22)
        if success { tap(held) }; revision += 1
    }
    func accessibleTap() { takeOver(); tap(0); revision += 1 }
    private func tap(_ held: Double) {
        guard !open else { return }
        guard taps > 0 else {
            animate(\.shake,1,0.05,curve:3); after(0.05) { [weak self] in self?.animate(\.shake,0,0.42,curve:2,damping:0.2) }
            pop(); buzz(.rigid); return
        }
        combo = now-lastTap < 0.45 ? min(combo+1,4) : 0; lastTap = now
        let power = max(min(combo,2),held > 0.5 ? 2 : held > 0.15 ? 1 : 0)
        let pay = 3+Int.random(in:0..<7)+combo+Int((held*12).rounded())
        earned += Double(pay)
        if !reduced { bursts += [now,Double.random(in:1..<91),Double(pay),Double(power)] }
        animate(\.scene.amount,earned,reduced ? 0 : 0.52,delay:reduced ? 0 : 0.12)
        state.gleam = -1; tracks.removeValue(forKey:\.gleam); animate(\.gleam,1,0.76)
        cue("coin",1+0.07*max(Double(combo),Double(power)*1.5))
        taps -= 1; state.scene.label = taps; pop()
        if taps == 0 { after(reduced ? 0.25 : 0.38) { [weak self] in self?.openSheet() } }
    }
    private func startRefill() {
        refillAt = now; state.scene.label = 4; refillCoinAt = now+2.55
    }
    private func openSheet() {
        open = true; lastTouch = now+(reduced ? 0.26 : 2.5)
        if reduced {
            animate(\.fade,0,0.16,curve:3); state.sheetFade = 0
            animate(\.sheetFade,1,0.26,curve:3); animate(\.scene.dim,1,0.26,curve:3)
            after(0.16) { [weak self] in
                guard let self else { return }; self.state.scene.morph = 1; self.state.scene.lift = 1
                self.state.scene.travel = 1; self.state.scene.sheet = 1; self.animate(\.fade,1,0.2,curve:3)
            }
        } else {
            UIAccessibility.post(notification:.announcement,argument:"Out of taps. Want a reminder when you can tap again?")
            cue("morph"); animate(\.scene.hop,0.55,0.15)
            after(0.15) { [weak self] in self?.animate(\.scene.hop,0,0.23,curve:1) }
            animate(\.scene.lift,1,0.46,delay:0.16); animate(\.scene.morph,1,1.1,delay:0.3,curve:1)
            animate(\.scene.dim,1,0.48,delay:1.6); animate(\.scene.travel,1,0.74,delay:1.76,curve:1)
            animate(\.scene.sheet,1,0.64,delay:1.76,curve:2,damping:0.82)
            after(1.4) { [weak self] in self?.ringBell(); self?.cue("ding") }
            after(2.5) { [weak self] in self?.buzz(.medium) }
        }
        revision += 1
    }
    func closeSheet(refill: Bool = true, velocity: Double = 0) {
        guard open else { return }; open = false; lastTouch = now
        if reduced {
            animate(\.fade,0,0.16,curve:3); animate(\.scene.dim,0,0.22,curve:3); animate(\.sheetFade,0,0.2,curve:3)
            after(0.16) { [weak self] in
                guard let self else { return }; self.state.scene.morph = 0; self.state.scene.lift = 0
                self.state.scene.travel = 0; self.state.scene.sheet = 0; self.animate(\.fade,1,0.2,curve:3)
            }
        } else {
            animate(\.scene.sheet,0,0.46,curve:2,damping:1,velocity:velocity); animate(\.scene.dim,0,0.42,curve:1)
            animate(\.scene.travel,0,0.64,curve:1); animate(\.scene.morph,0,0.9,delay:0.46,curve:1)
            animate(\.scene.lift,0,0.45,delay:1.24,curve:1)
            after(0.46) { [weak self] in self?.cue("morph") }
            after(1.69) { [weak self] in
                self?.buzz(); self?.animate(\.scene.hop,0.45,0.08)
                self?.after(0.08) { [weak self] in self?.animate(\.scene.hop,0,0.64,curve:2,damping:0.32) }
            }
        }
        if refill && taps <= 0 { after(reduced ? 0.2 : 0.7) { [weak self] in self?.startRefill() } }
        revision += 1
    }
    func ringBell(_ angle: Double = 0.32, damping: Double = 0.16) {
        guard !reduced else { return }
        animate(\.scene.swing,angle,angle < 0 ? 0.1 : 0.11,curve:3)
        after(angle < 0 ? 0.1 : 0.11) { [weak self] in self?.animate(\.scene.swing,0,angle < 0 ? 1.3 : 1.2,curve:2,damping:damping) }
    }
    func remind() {
        takeOver(); cue("ding"); UINotificationFeedbackGenerator().notificationOccurred(.success)
        ringBell(-0.4,damping:0.12); after(0.75) { [weak self] in self?.closeSheet() }
    }
    func share() { takeOver(); buzz() }
    func dragSheet(_ dy: Double, _ height: Double) {
        lastTouch = now
        let v = dy >= 0 ? max(0,1-dy/height) : 1+(-dy/(1 + -dy/60))/height, u = 1-min(v,1)
        for key in [\TapEarnCoinFrame.scene.sheet, \.scene.travel, \.scene.morph, \.scene.dim] { tracks.removeValue(forKey:key) }
        state.scene.sheet = v; state.scene.travel = 1-TapEarnCoinM.easeInOut(TapEarnCoinM.seg(u,0.25,0.9))
        state.scene.morph = 1-TapEarnCoinM.easeInOut(TapEarnCoinM.seg(u,0.3,0.95)); state.scene.dim = 1-TapEarnCoinM.easeInOut(TapEarnCoinM.seg(u,0.1,1))
        revision += 1
    }
    func endSheet(_ dy: Double, _ velocity: Double, _ height: Double) {
        lastTouch = now
        if dy > 120 || velocity > 800 { closeSheet(velocity:-velocity/height) }
        else {
            animate(\.scene.sheet,1,0.42,curve:2,damping:0.8,velocity:-velocity/height); animate(\.scene.travel,1,0.42,curve:2,damping:0.85)
            animate(\.scene.morph,1,0.42,curve:2,damping:0.85); animate(\.scene.dim,1,0.26)
        }
        revision += 1
    }
    func tick(_ date: Double) {
        guard active else { return }
        if autoPlay && !manual && !reduced {
            let t = time(date)
            #if DEBUG
            if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return }
            #endif
            if DemoSound.inAutoPlay {
                let cues: [(Double,String,Double)] = [(1.01,"coin",1),(1.76,"coin",1),(2.51,"coin",1.105),(3,"morph",1),(4.42,"ding",1),(7.66,"morph",1)]
                let previous = t < previousAutoTime ? -1 : previousAutoTime
                for (at,cue,rate) in cues where t >= at && previous < at { audio.play(cue,rate:rate) }
            }
            previousAutoTime = t
            return
        }
        var changed = false
        let oldBurstCount = bursts.count
        var remaining: [Double] = []
        for b in stride(from:0,to:bursts.count,by:4) where b+3 < bursts.count {
            if date-bursts[b] < TapEarnCoinM.burstLife { remaining += Array(bursts[b...b+3]) }
        }
        bursts = remaining
        if bursts.count != oldBurstCount { changed = true }
        let due = events.filter { $0.0 <= date }; events.removeAll { $0.0 <= date }
        for (_,action) in due { action(); changed = true }
        for (key,track) in tracks where track.finished(date) {
            state[keyPath:key] = track.to; tracks.removeValue(forKey:key); changed = true
        }
        if pressing, !reduced {
            let charge = frame(date).charge
            if charge > 0 && charge < 1 && date-chargeTick > 0.2 { buzz(); chargeTick = date }
        }
        if let r = refillAt, date >= r+3 {
            refillAt = nil; taps = 3; state.scene.label = 3; pop(); buzz(); changed = true
        }
        if autoPlay && !reduced && manual && !pressing && date-lastTouch > 2 && resumeAt == nil {
            events = []; if open { closeSheet(refill:false) }; refillAt = nil; taps = 3; state.scene.label = 3
            resumeAt = date+1.1; changed = true
        }
        if let at = resumeAt, date >= at {
            resumeAt = nil; events = []; tracks = [:]; base = earned; state = TapEarnCoinFrame(); state.scene.amount = earned
            origin = date; manual = false; previousAutoTime = -1; changed = true
        }
        if changed { revision += 1 }
    }
}
