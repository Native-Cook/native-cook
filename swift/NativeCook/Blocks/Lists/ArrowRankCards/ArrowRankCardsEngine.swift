import Foundation

/// One owner for autoplay, prism release and continuous ruler inertia; cancellation never advances the deck.
final class ArrowRankCardsEngine {
    private typealias M=ArrowRankCardsMotion
    var autoPlay=true, reduced=false, auto=true, pin: Double?
    var saved=[false,false,false], buzz: (String)->Void = { _ in }
    var time=0.0, now=0.0, live=M.rest(), start=M.rest()
    private var bookmarkAt = -10.0
    var bookmarkScale: Double {
        let t=now-bookmarkAt; if reduced || t>3 { return 1 }; let a=22.0/8,w=sqrt(360.0/4-a*a)
        return 1-0.14*exp(-a*t)*(cos(w*t)+a/w*sin(w*t))
    }
    var touching=false, rulerOn=false, rulerValue=0.0
    private var last: Double?, options: (Bool,Bool)?
    private var release = -1.0, commit=false, velocity=0.0, lastTouch=0.0
    private var rulerStart=0.0, rulerVelocity=0.0, wrongAt = -1.0, hintAt = -1.0, wrongOnce=false, threshold=false
    private var returnAt = -1.0, returnStart=M.rest(), lastSelection = -1.0, previousSlats=0, previousRank=0
    init() {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey: "arrowRankCardsTime") { pin=(value as? NSNumber)?.doubleValue ?? (value as? String).flatMap(Double.init); time=pin ?? 0 }
        #endif
    }
    var frame: ArrowRankCardsFrame { auto ? M.frame(pin ?? time) : live }
    func configure(_ play: Bool,_ reduce: Bool) {
        autoPlay=play;reduced=reduce
        if options == nil || options!.0 != play || options!.1 != reduce { auto=play && !reduce; if reduce { live=M.rest() }; options=(play,reduce) }
    }
    func suspend() { last=nil }
    func step(_ stamp: Double) {
        let dt=min(max(0,last.map { stamp-$0 } ?? 0.016),0.05);last=stamp;now+=dt
        if pin != nil { return }
        if auto { if !reduced { time=(time+dt).truncatingRemainder(dividingBy: 9) };return }
        if rulerOn {
            if !touching {
                let previous=rulerVelocity,v=previous*exp(-dt*7);rulerVelocity=v;rulerValue+=(previous-v)/7
                if abs(v)<0.25 {
                    rulerValue=M.mix(rulerValue,floor(rulerValue+0.5),min(1,dt*20))
                    if abs(rulerValue-floor(rulerValue+0.5))<0.001 { live=M.rest(Int(floor(rulerValue+0.5)));rulerOn=false }
                }
            }
            if rulerOn {
                let n=floor(rulerValue),p=rulerValue-n;var f=M.rest(Int(n));let a=M.shortest(f.head,M.heading(f.next))
                f.p=p;f.q=reduced ? f.q : M.slats(p);f.drum=p;f.flat=reduced ? 1 : 0
                if !reduced { f.head=M.mix(f.head,a,M.smooth(p));f.tail=f.head };f.ticks = -rulerValue*12;live=f
            }
        } else if release>=0 {
            let previous=release;release+=dt
            if reduced { var f=start;f.q=Array(repeating:0,count:9);f.drum=commit ? M.seg(release,0,0.2) : 0;f.stretch=0;f.flat=1;live=f }
            else { live=M.release(start,release,commit,velocity) }
            let landing=reduced ? 0.2 : 0.3
            if commit && previous<landing && release>=landing { buzz("medium") }
            if release >= (reduced ? 0.2 : 0.78) { live=M.rest(commit ? start.next : start.index);release = -1 }
        } else if !touching && !reduced && hintAt>=0 {
            let u=M.seg(now-hintAt,0,0.36);live.stretch=18*sin(.pi*u);live.beckon = -6*sin(.pi*2*u);if u==1 { hintAt = -1 }
        }
        if wrongAt>=0 && now-wrongAt<0.24 && !reduced { let v=6*sin((now-wrongAt)/0.24 * .pi*4);live.shake=v;live.twitch=v }
        else { live.shake=0;live.twitch=0 }
        if autoPlay && !reduced && !touching && release<0 && !rulerOn && now-lastTouch>2 {
            if returnAt<0 { returnAt=now;returnStart=live }
            let u=M.seg(now-returnAt,0,0.65),from=returnStart
            if from.index==0 { live=M.rest() }
            else { var f=M.rest(from.index);f.next=0;f.p=M.smooth(u);f.q=M.slats(f.p);f.drum=f.p;f.head=M.mix(from.head,M.shortest(from.head,0),f.p);f.tail=M.mix(from.tail,M.shortest(from.tail,0),f.p);live=f }
            if u==1 { time=0;auto=true;returnAt = -1 }
        }
        let count=live.q.filter { $0>=0.5 }.count
        if count>0 && count%3==0 && count != previousSlats && now-lastSelection>0.08 { buzz("selection");lastSelection=now };previousSlats=count
        let rank=Int(floor(rulerValue+0.5))
        if rulerOn && rank != previousRank && now-lastSelection>=0.1 { buzz("selection");lastSelection=now };previousRank=rank
    }
    func begin() { live=frame;start=live;auto=false;pin=nil;release = -1;returnAt = -1;touching=true;rulerOn=false;hintAt = -1;wrongAt = -1;wrongOnce=false;threshold=false;lastTouch=now;buzz("light") }
    func move(_ x: Double,_ y: Double) {
        let angle=M.heading(start.index) * .pi/180,dot=x*cos(angle)+y*sin(angle),p=M.clamp(start.p+dot/170,0,1.15)
        if dot < -10 && !wrongOnce { wrongOnce=true;wrongAt=now;buzz("rigid") }
        let a=M.slats(start.p),b=M.slats(p);var f=start;f.p=p
        f.q=reduced ? start.q : (0..<9).map { M.clamp(start.q[$0]+b[$0]-a[$0]) }
        f.stretch=reduced ? 0 : M.clamp(start.stretch+72*(p-start.p),0,36);live=f
        if p>=0.5 && !threshold { threshold=true;buzz("light") }
    }
    func end(_ vx: Double,_ vy: Double,_ success: Bool=true) {
        let a=M.heading(live.index) * .pi/180;velocity=success ? (vx*cos(a)+vy*sin(a))/170 : 0
        commit=success && live.p+velocity*0.12>=0.5;start=live;release=0;touching=false;lastTouch=now
    }
    func rulerBegin() { let position=rulerOn ? rulerValue : Double(frame.index);begin();rulerOn=true;rulerValue=position;rulerStart=position;rulerVelocity=0 }
    func rulerMove(_ x: Double) { rulerValue=rulerStart-x/12 }
    func rulerEnd(_ vx: Double) { touching=false;rulerVelocity=M.clamp(-vx/12,-12,12);lastTouch=now }
    func hint() { begin();touching=false;hintAt=now }
    func bookmark() { begin();touching=false;let i=live.drum<0.5 ? live.index : live.next;saved[i].toggle();bookmarkAt=now }
    func advance() { begin();live.p=1;end(0,0) }
}
