import Foundation

/// A finger writes the same event record as the autoplay. Cancellation only releases tension; it never submits.
final class ElasticStringQuizEngine {
    typealias M=ElasticStringQuizMotion
    var autoPlay=true, reduced=false, manual=false
    var pin:Double?
    var script:M.Script
    private(set) var frame:M.Frame
    private(set) var matched=[[String]](repeating:[],count:3)
    var words:[String]
    var onHaptic:((Int)->Void)?, onSound:((Int,Double)->Void)?, onAnswer:((String,Int,Bool)->Void)?, onRoundEnd:(([[String]])->Void)?
    private var events:M.Ev, now=0.0, phase=0.0, auto=true, touching=false, lastTouch=0.0
    private var held:Int?, dy0=0.0, grabOffset=0.0, began=0.0, moved=0.0, velocity=0.0, armed=false
    private var strumPoint:(Double,Double)?, lastStrum = -10.0
    init(script:M.Script,words:[String]) {
        self.script=script; self.words=words; events=M.initEv(script.score,0); frame=M.frameAt(0,script)
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"elasticStringQuizManual")
        if let raw=UserDefaults.standard.object(forKey:"elasticStringQuizTime"),let t=Double("\(raw)") { pin=t; phase=t; frame=M.frameAt(t,script) }
        #endif
    }
    func configure(autoPlay:Bool,reduced:Bool) {
        if self.autoPlay != autoPlay || self.reduced != reduced { auto=autoPlay && !reduced && !manual }
        if reduced && !self.reduced { events=M.initEv(script.score,1); now=0; pin=nil; touching=false }
        self.autoPlay=autoPlay; self.reduced=reduced; events.rm=reduced ? 1 : 0
        if manual || reduced { auto=false }
        publish()
    }
    private func publish() { frame=auto || pin != nil ? M.frameAt(pin ?? phase,script) : M.compose(events,now,script) }
    func advance(_ dt:Double) {
        guard pin==nil else { return }
        if auto { phase=(phase+dt).truncatingRemainder(dividingBy:12); publish(); return }
        now+=dt
        for i in 0..<3 {
            if events.tapT[i]>M.NEG/2 && now>=events.tapT[i]+0.14 {
                let t=events.tapT[i]+0.14; M.releaseCord(events,Double(i),t,52,0,0); M.firePulse(events,Double(i),t,1); onHaptic?(1); onSound?(i,0.6)
            }
            if events.pend[i]==1 && now>=events.pulseT[i]+M.LREST[i]/M.PULSE_SPEED {
                let t=events.pulseT[i]+M.LREST[i]/M.PULSE_SPEED; arrive(i,at:t)
            }
        }
        if now>=events.trAt { M.startTransition(events,events.trAt,script,0); matched=[[],[],[]] }
        if !reduced && autoPlay && !manual && !touching && now-lastTouch>2 && quiet() {
            let recap=events.recapT<=now && !(events.recapOffT>events.recapT)
            if !(recap && now<events.recapT+3.5) {
                if !recap && M.wordAt(events,now)==0 && events.sTo==script.score { phase=0; auto=true }
                else { if recap { events.recapOffT=now }; M.startTransition(events,recap ? now-0.2 : now,script,recap ? 1 : 0); matched=[[],[],[]]; lastTouch=now }
            }
        }
        publish()
    }
    private func quiet()->Bool {
        for i in 0..<3 {
            if events.held[i]==1 || events.tapT[i]>M.NEG/2 || events.pend[i]==1 || now-events.relT[i]<1.5 { return false }
            if events.snapT[i]<M.POS/2 && now<events.snapT[i]+1.6 { return false }
            if M.haloLevel(events,Double(i),now)>0.01 { return false }
            for s in 0..<3 { let b=(i*3+s)*5; if now>=events.pl[b+3] && abs(events.pl[b])*exp(-(now-events.pl[b+3])/events.pl[b+4])>0.03 { return false } }
        }
        return !(now<events.pop[1]+0.6 || now<events.pop[3]+0.6 || (events.nxT<M.POS/2 && now<events.nxT+1) || now<events.transT+2.3 || now<events.rejT+1.5)
    }
    private func takeOver() {
        if auto || pin != nil {
            let t=pin ?? phase; events=M.autoEventsAt(t,script)
            for i in 0..<3 where events.held[i]==1 { M.releaseCord(events,Double(i),t,events.hs[i],0,0) }
            events.rm=reduced ? 1 : 0; now=t; auto=false
            matched=[[],[],[]]; let w=Int(M.wordAt(events,now)); if w>0 { for j in 0..<min(w,words.count) { matched[Int(script.answers[j])].append(words[j]) } }
        }
        pin=nil; lastTouch=now
    }
    private func busy()->Bool { (now>=events.transT && now<events.transT+2.2) || now>=events.recapT-0.4 }
    @discardableResult func begin(_ i:Int)->Bool {
        takeOver(); touching=true
        if busy() || (events.snapT[i]<M.POS/2 && now>=events.snapT[i]-0.06 && now<events.snapT[i]+1.1) || (events.pend[i]==1 && reduced) { held=nil; return false }
        held=i; let s=M.sAt(events,Double(i),now), b=M.bendAt(events,Double(i),now)
        events.held[i]=1; events.hs[i]=reduced ? 0 : s; events.hb[i]=reduced ? 0 : b; events.tapT[i]=M.NEG
        if M.haloLevel(events,Double(i),now)<0.5 { events.haloOn[i]=now }; events.haloOff[i]=M.NEG
        dy0=M.elasticInv(max(s,-7)); grabOffset=s-M.elastic(dy0); began=now; moved=0; velocity=0; armed=s>=script.arm; onHaptic?(0); publish(); return true
    }
    func move(x:Double,y:Double,velocityY:Double) {
        guard let i=held else { return }; moved=max(moved,hypot(x,y)); guard !reduced else { return }
        let d=dy0+y, s=M.elastic(d)+grabOffset; events.hs[i]=s; events.hb[i]=24*tanh(x/48); velocity=velocityY*M.elasticSlope(d)
        let isArmed=s>=script.arm; if isArmed != armed { armed=isArmed; onHaptic?(3) }; lastTouch=now; publish()
    }
    func end(cancelled:Bool=false) {
        touching=false; lastTouch=now; guard let i=held else { return }; held=nil
        let s=events.hs[i], b=events.hb[i]
        if cancelled { M.releaseCord(events,Double(i),now,s,0,b); events.haloOff[i]=now+0.15; publish(); return }
        if reduced { events.held[i]=0; arrive(i,at:now); onSound?(i,0.6) }
        else if moved<8 && now-began<0.3 { events.held[i]=0; events.tapT[i]=now; events.tapS0[i]=s }
        else {
            let answer=s+velocity*0.1>=script.arm
            M.releaseCord(events,Double(i),now,s,velocity,b); M.firePulse(events,Double(i),now,answer ? 1 : 2)
            if !answer { events.haloOff[i]=now+0.15 }; onHaptic?(1); onSound?(i,M.clamp01(s/96))
        }
        publish()
    }
    private func arrive(_ i:Int,at:Double) {
        let w=Int(M.wordAt(events,at)); guard words.indices.contains(w) else { return }
        let correct=Int(script.answers[w])==i; M.arrive(events,Double(i),at,correct,script,0)
        onHaptic?(2); onSound?(correct ? 3 : 4,1); onAnswer?(words[w],i,correct)
        if correct { matched[i].append(words[w]); if w>=words.count-1 { onRoundEnd?(matched) } }
    }
    func strumBegin(x:Double,y:Double) { takeOver(); touching=true; strumPoint=(x,y) }
    func strumMove(x:Double,y:Double,velocityX:Double) {
        guard let p=strumPoint else { return }; defer { strumPoint=(x,y); lastTouch=now }
        guard !busy() && !reduced else { return }
        for i in 0..<3 {
            let cx=M.G.bx[i]; guard (p.0-cx)*(x-cx)<0 && p.0 != x else { continue }
            let yy=M.mix(p.1,y,(cx-p.0)/(x-p.0))
            guard yy>=353 && yy<=620 && events.held[i] != 1 && !(events.snapT[i]<M.POS/2 && now>=events.snapT[i] && now<events.snapT[i]+1.1) else { continue }
            let top=i==1 ? 300.0 : 353, u=M.clamp01((yy-top)/(640-top)), a=min(max(abs(velocityX)/150,2),9)*(x>p.0 ? 1 : -1)
            M.addPluck(events,Double(i),a*sin(Double.pi*u),a*sin(2*Double.pi*u)/4,a*sin(3*Double.pi*u)/9,now,0.9)
            if now-lastStrum>=0.06 { onHaptic?(3); lastStrum=now }; onSound?(i,abs(a)/9)
        }
    }
    func strumEnd() { touching=false; lastTouch=now; strumPoint=nil }
    func continueRound() {
        takeOver(); guard events.recapT<=now && !(events.recapOffT>events.recapT) else { return }
        events.recapOffT=now; M.startTransition(events,now-0.2,script,1); matched=[[],[],[]]; onHaptic?(1); publish()
    }
}
