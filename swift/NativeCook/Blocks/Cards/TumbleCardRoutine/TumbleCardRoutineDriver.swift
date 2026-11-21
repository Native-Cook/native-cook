import SwiftUI
import UIKit

/// Samples the same mass-one springs as the RN gesture; published positions are always the visible positions.
final class TumbleCardRoutineDriver: ObservableObject {
    @Published var deck=TumbleCardRoutineMotion.frame(0)
    private typealias M=TumbleCardRoutineMotion
    private var link:CADisplayLink?, target:TumbleCardRoutineDisplayTarget?
    private var previous=0.0, now=0.0, time=0.0, idle=0.0
    private var autoPlay=true, reduced=false, active=true, manual=false, live=false, touching=false
    private var pin:Double?, position=0.0, velocity=0.0, springFrom=0.0, springVelocity=0.0, springTarget=0.0, springStart=0.0
    private var settling=false, peek=0.0, peekTarget=0.0, drop=0.0, reveal=0.8, bump=0.0, bumpIndex = -1, ant=0.0
    private var base=0.0, start=0.0, dragging=false, spin=0.0, spinTarget=0.0, spinStart=0.0, press=0.0, nod=0.0
    init() {
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"tumbleCardRoutineManual")
        if let raw=UserDefaults.standard.object(forKey:"tumbleCardRoutineTime"), let n=Double("\(raw)") { pin=n; time=n; deck=M.frame(n) }
        #endif
    }
    deinit { link?.invalidate() }
    func configure(autoPlay:Bool,reduced:Bool,active:Bool) {
        self.autoPlay=autoPlay; self.reduced=reduced; self.active=active
        if reduced && !live { time=0; deck=M.frame(0) }
        if let pin { deck=M.frame(pin) }
        wake()
    }
    func stop() { link?.invalidate(); link=nil; target=nil; previous=0 }
    private func wake() {
        if link==nil {
            let target=TumbleCardRoutineDisplayTarget(); target.owner=self; self.target=target
            let link=CADisplayLink(target:target,selector:#selector(TumbleCardRoutineDisplayTarget.tick(_:)))
            link.add(to:.main,forMode:.common); self.link=link
        }
        previous=0; link?.isPaused = !active || pin != nil
    }
    func tick(_ link:CADisplayLink) {
        let dt=previous==0 ? 1.0/60 : min(max(link.timestamp-previous,0),0.05); previous=link.timestamp; now+=dt
        if !live {
            if autoPlay && !manual && !reduced { time=M.wrap(time+dt); deck=M.frame(time) }
            else { link.isPaused=true }
            return
        }
        let old=position
        if settling && !touching {
            let age=now-springStart
            position=M.spring(age,springFrom,springTarget,springVelocity,15.1,93.4)
            velocity=(M.spring(age+0.00001,springFrom,springTarget,springVelocity,15.1,93.4)-position)/0.00001
            if abs(position-springTarget)<0.0001 && abs(velocity)<0.001 { position=springTarget; velocity=0; settling=false }
        }
        let step=dt/0.3; peek += min(max(peekTarget-peek,-step),step)
        drop=max(0,drop-dt*700); reveal=min(0.8,reveal+dt); bump *= exp(-dt*15); ant *= exp(-dt*20)
        press=max(0,press-dt*5); nod=max(0,nod-dt*9)
        spin=spinTarget*M.out(M.seg(now-spinStart,0,0.6))
        let before=Int(floor(old+0.000001)), after=Int(floor(position+0.000001))
        if after != before { reveal=0.8; if after>before { peek=0; peekTarget=0 } }
        if !touching {
            idle+=dt
            if abs(position-position.rounded())<0.001 { spin=0; spinTarget=0 }
            if autoPlay && !manual && !reduced && idle>2 && !settling && peek<0.002 && drop<0.5 {
                let i=((Int(position.rounded())%4)+4)%4
                position=Double(i); live=false; time=M.rest[i]; deck=M.frame(time); return
            }
        }
        var d=M.Deck(); d.pos=position; d.peek=peek; d.drop=drop; d.ant=ant; d.bump=bump; d.bumpIndex=bumpIndex
        d.live=true; d.clock=now; d.rate=touching ? velocity : (position-old)/max(dt,0.0001); d.spin=spin; d.press=press; d.nod=nod
        let k=Int(floor(position+0.000001)), h=max(0,position-Double(k)), ha=max(h,0.22*peek)
        d.reveal[((k%4)+4)%4]=reveal; d.reveal[(((k+1)%4)+4)%4]=0.8*M.seg(ha,0.35,1)
        deck=d
        if !touching && !settling && (!autoPlay || manual || reduced) && peek==0 && drop==0 && press==0 && nod==0 { link.isPaused=true }
    }
    func begin() {
        if !live {
            let d=deck; position=d.pos>=4 ? d.pos-4 : d.pos; peek=d.peek; drop=d.drop; bump=d.bump; bumpIndex=d.bumpIndex; ant=d.ant
            reveal=d.reveal[((Int(floor(position+0.000001))%4)+4)%4]; live=true
        }
        pin=nil; settling=false; velocity=0; touching=true; dragging=false; idle=0; peekTarget=peek; start=position
        base=floor(position-0.22*peek+0.5); wake()
    }
    func hold() { if !reduced { peekTarget=1; wake() } }
    func drag(translation:Double,velocity:Double) {
        if !dragging { dragging=true; start=position; base=floor(position-0.22*peek+0.5); if peek>0.001 { position=base+0.22*peek; start=position } }
        guard !reduced else { return }
        var p=start-translation/240
        let edge=min(max(p,base-1),base+1), over=abs(p-edge)*240
        if p != edge { p=edge+(p>edge ? 1 : -1)*(over/(1+over/120))/240 }
        if translation>6 && peek>0 && p<base+0.22*peek { peekTarget=0 }
        position=p; self.velocity = -velocity/240
    }
    private func settle(_ target:Double,_ velocity:Double) {
        if reduced { position=target; settling=false; return }
        springFrom=position; springTarget=target; springVelocity=velocity; springStart=now; settling=true
    }
    func end(translation:Double,velocity:Double,cancelled:Bool=false) {
        if dragging {
            let v=cancelled ? 0 : -velocity/240
            let target=cancelled ? base : reduced ? base+(abs(translation)>30 ? (translation<0 ? 1 : -1) : 0) : min(max(floor(position+v*0.14+0.5),base-1),base+1)
            settle(target,v)
            if target>base && !reduced { let i=((Int(floor(position+0.000001))%4)+4)%4; spinTarget=min(abs(v)*30,90)*(M.sizes[i].5<0 ? -1 : 1)*0.6; spinStart=now }
            if !cancelled { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
        } else { settle(floor(position+0.5),0) }
        peekTarget=0; touching=false; dragging=false; idle=0; wake()
    }
    func tap(add:Bool) { if add { nod=1.5 } else { press=1 }; UIImpactFeedbackGenerator(style:.light).impactOccurred(); wake() }
}
private final class TumbleCardRoutineDisplayTarget:NSObject {
    weak var owner:TumbleCardRoutineDriver?
    @objc func tick(_ link:CADisplayLink) { owner?.tick(link) }
}

struct TumbleCardRoutineTouchSurface:UIViewRepresentable {
    let driver:TumbleCardRoutineDriver
    let bottom:Double
    func makeUIView(context:Context) -> TumbleCardRoutineTouchView { let view=TumbleCardRoutineTouchView(); view.driver=driver; return view }
    func updateUIView(_ view:TumbleCardRoutineTouchView,context:Context) { view.driver=driver; view.bottom=bottom }
}
final class TumbleCardRoutineTouchView:UIView {
    weak var driver:TumbleCardRoutineDriver?
    var bottom=0.0
    private var origin=CGPoint.zero, didDrag=false, didHold=false
    private var holdWork:DispatchWorkItem?
    private lazy var pan=UIPanGestureRecognizer(target:self,action:#selector(panned(_:)))
    override init(frame:CGRect) {
        super.init(frame:frame)
        pan.cancelsTouchesInView = false
        addGestureRecognizer(pan)
    }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let t=touches.first else { return }; origin=t.location(in:self); didDrag=false; didHold=false; driver?.begin()
        if origin.y>515+bottom && origin.y<719+bottom && abs(origin.x-201)>40 {
            let work=DispatchWorkItem { [weak self] in guard let self else { return }; self.didHold=true; self.driver?.hold() }; holdWork=work; DispatchQueue.main.asyncAfter(deadline:.now()+0.18,execute:work)
        }
    }
    override func point(inside point:CGPoint,with event:UIEvent?)->Bool { point.y>=160 && super.point(inside:point,with:event) }
    override func gestureRecognizerShouldBegin(_ gestureRecognizer:UIGestureRecognizer)->Bool {
        let v=pan.translation(in:self); return abs(v.y)>=abs(v.x)
    }
    @objc private func panned(_ recognizer:UIPanGestureRecognizer) {
        let translation=recognizer.translation(in:self), velocity=recognizer.velocity(in:self)
        switch recognizer.state {
        case .began,.changed: holdWork?.cancel(); didDrag=true; driver?.drag(translation:translation.y,velocity:velocity.y)
        case .ended: driver?.end(translation:translation.y,velocity:velocity.y)
        case .cancelled,.failed: driver?.end(translation:translation.y,velocity:0,cancelled:true)
        default: break
        }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
        holdWork?.cancel()
        if pan.state == .possible || pan.state == .failed {
            if !didHold && !didDrag { driver?.tap(add:origin.y>620+bottom && abs(origin.x-201)<38) }
            driver?.end(translation:0,velocity:0)
        }
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) {
        holdWork?.cancel(); if !didDrag { driver?.end(translation:0,velocity:0,cancelled:true) }
    }
}
