import SwiftUI
import UIKit

final class SunriseCocktailPourClock: ObservableObject {
    typealias M=SunriseCocktailPourMotion
    var f=M.Frame()
    @Published var running=false
    private var date: Date?
    private var now=0.0, autoStart=0.0, lastTouch = -100.0
    private var mode=0, resumeAsked=false, stirring=false
    private var pouredAt = -1.0, stirredAt = -1.0
    private var servedAt: Double?, shiftAt: Double?, shiftFrom=0.0
    private var blendAt: Double?, blendFrom=0.0, fadeAt: Double?, fadeFrom=0.0
    private var redTween: SunriseCocktailPourTween?, riseTween: SunriseCocktailPourTween?, mixTween: SunriseCocktailPourTween?
    private var noteBefore=0, noteAfter=0, noteAt = -100.0
    private var noteFrom=[1.0,0.0,0.0]
    private let light=UIImpactFeedbackGenerator(style:.light)
    private let success=UINotificationFeedbackGenerator()
    func reset(_ auto: Bool) {
        f=M.Frame(); now=0; autoStart=0; date=nil; mode=auto ? 0 : 1
        pouredAt = -1; stirredAt = -1; servedAt=nil; shiftAt=nil; blendAt=nil; fadeAt=nil
        redTween=nil; riseTween=nil; mixTween=nil
        noteBefore=0; noteAfter=0; noteAt = -100; stirring=false
        noteFrom=[1,0,0]
        lastTouch = -100; resumeAsked=false; running=false
    }
    func active() { date=nil }
    private func fresh() {
        setStep(0)
        let phase=f.phase, shift=f.shift, finger=f.finger, press=f.fingerPress
        f=M.Frame(); f.phase=phase; f.shift=shift; f.finger=finger; f.fingerPress=press
        pouredAt = -1; stirredAt = -1
        redTween=nil; riseTween=nil; mixTween=nil
    }
    func takeOver(_ reduced: Bool) {
        if !running { date=nil }
        lastTouch=now; resumeAsked=false
        if mode==0 {
            let t=(now-autoStart).truncatingRemainder(dividingBy:M.loop)
            noteBefore=0; noteAfter=0; noteAt = -100
            for (boundary,before,after) in [(M.pourStart,0,1),(M.stirStart,1,2),(7.6,2,0)] where t>boundary {
                noteBefore=before; noteAfter=after; noteAt=now-t+boundary
            }
            noteFrom=(0..<3).map { $0==noteBefore ? 1 : 0 }
            mode=1; fadeAt=now; fadeFrom=f.finger; f.fingerPress=0
            let step=f.step
            if abs(f.shift)>0.001 {
                shiftFrom=f.shift; shiftAt=now
                if f.shift>0 { fresh() }
            }
            if step==1 && f.red<0.999 { pouredAt=now-1.2 }
            if step==2 && f.mix<0.999 { stirredAt=now-0.4 }
            f.stream=0
        }
        running=true
    }
    private func setStep(_ step: Int) {
        if min(2,step) != min(2,f.step) {
            let current=(0..<3).map { note($0,false).0 }
            noteFrom=current; noteBefore=min(2,f.step); noteAfter=min(2,step); noteAt=now
        }
        f.step=step
    }
    private func pour(_ reduced: Bool) {
        guard f.step==0 else { return }; setStep(1)
        if reduced {
            redTween=SunriseCocktailPourTween(from:f.red,at:now)
            riseTween=SunriseCocktailPourTween(from:f.rise,at:now)
        }
        else { pouredAt=now; light.impactOccurred() }
    }
    private func stir(_ reduced: Bool) {
        guard f.step==1 else { return }; setStep(2)
        if reduced { mixTween=SunriseCocktailPourTween(from:f.mix,at:now) }
        else { stirredAt=now; light.impactOccurred() }
    }
    private func serve(_ reduced: Bool) {
        guard f.step==2 else { return }
        success.notificationOccurred(.success)
        if reduced { setStep(0); fresh() }
        else { setStep(3); servedAt=now; shiftAt=nil }
    }
    func next(_ reduced: Bool) {
        takeOver(reduced)
        if f.step==0 { pour(reduced) } else if f.step==1 { stir(reduced) } else if f.step==2 { serve(reduced) }
    }
    func drag(_ state: UIGestureRecognizer.State, _ translation: CGPoint, _ velocity: CGPoint, _ scale: Double, _ reduced: Bool) {
        if state == .began { stirring=true; takeOver(reduced) }
        if state == .changed {
            lastTouch=now
            if f.step==1 && hypot(translation.x,translation.y)>40 { stir(reduced) }
            if f.step==2 && !reduced { f.swirl += velocity.x/(scale*60)*0.016*4 }
        }
        if state == .ended || state == .cancelled || state == .failed { stirring=false; lastTouch=now }
    }
    func note(_ i: Int, _ reduced: Bool) -> (Double,Double) {
        let p=M.cssOut(M.seg(now,noteAt,noteAt+0.26))
        let start=noteFrom[i], end=noteAfter==i ? 1.0 : 0
        let a=start+(end-start)*p
        return (a,reduced ? 0 : 6*(1-a))
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> (M.Frame,Double?) {
        if let pinned { return (M.at(pinned),pinned) }
        // The presentation clock follows wall time even on slow Xcode Cloud frames.
        let dt=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
        date=at; now+=dt
        if mode==0 && auto {
            let t=(now-autoStart).truncatingRemainder(dividingBy:M.loop)
            f=M.at(t)
            return (f,t)
        }
        if !reduced { f.phase=(f.phase+M.speed*dt).truncatingRemainder(dividingBy:2*Double.pi) }
        if let a=fadeAt {
            f.finger=fadeFrom*(1-M.defaultEase(M.seg(now,a,a+0.15)))
            if now>=a+0.15 { fadeAt=nil }
        }
        if pouredAt>=0 { M.pour(now-pouredAt,&f); if f.rise>=1 { pouredAt = -1 } }
        if stirredAt>=0 {
            let old=f.mix; M.stir(now-stirredAt,&f); f.mix=max(old,f.mix)
            if now-stirredAt>1.6 { stirredAt = -1 }
        }
        if let tween=redTween { f.red=tween.value(now); if tween.finished(now) { redTween=nil } }
        if let tween=riseTween { f.rise=tween.value(now); if tween.finished(now) { riseTween=nil } }
        if let tween=mixTween { f.mix=tween.value(now); if tween.finished(now) { mixTween=nil } }
        if let a=shiftAt {
            f.shift=shiftFrom*(1-M.defaultEase(M.seg(now,a,a+0.4)))
            if now>=a+0.4 { shiftAt=nil }
        }
        if let a=blendAt {
            f.mix=blendFrom+(1-blendFrom)*M.defaultEase(M.seg(now,a,a+0.3))
            if now>=a+0.3 { blendAt=nil; setStep(2); serve(false) }
        }
        if let a=servedAt {
            let tau=now-a
            if tau<0.7 { f.shift = -3.4*M.bezier(tau/0.7,0.77,0,0.175,1) }
            else {
                if f.step==3 { setStep(0); fresh() }
                f.shift=3.4*(1-M.cssOut(M.seg(tau,0.7,1.6)))
                if tau>=1.6 { f.shift=0; servedAt=nil }
            }
        }
        if auto && !stirring && now-lastTouch>=2 {
            if !resumeAsked {
                resumeAsked=true
                if f.step==2 { serve(false) }
                else if f.step==1 { blendAt=now; blendFrom=f.mix }
            } else if f.step==0 && abs(f.shift)<0.001 && pouredAt<0 {
                resumeAsked=false; autoStart=now; mode=0
            }
        }
        let moves = !reduced || redTween != nil || riseTween != nil || mixTween != nil || now<noteAt+0.26
        if running && !moves { DispatchQueue.main.async { [weak self] in self?.running=false } }
        return (f,nil)
    }
}

private struct SunriseCocktailPourTween {
    let from: Double
    let at: Double
    func value(_ now: Double) -> Double {
        from+(1-from)*SunriseCocktailPourMotion.defaultEase(SunriseCocktailPourMotion.seg(now,at,at+0.3))
    }
    func finished(_ now: Double) -> Bool { now>=at+0.3 }
}

struct SunriseCocktailPourTouch: UIViewRepresentable {
    let began: () -> Void
    let action: (UIGestureRecognizer.State,CGPoint,CGPoint) -> Void
    func makeUIView(context: Context) -> SunriseCocktailPourTouchSurface {
        let view=SunriseCocktailPourTouchSurface()
        view.began=began; view.action=action
        return view
    }
    func updateUIView(_ view: SunriseCocktailPourTouchSurface,context:Context) { view.began=began; view.action=action }
}
final class SunriseCocktailPourTouchSurface: UIView {
    var began: (() -> Void)?
    var action: ((UIGestureRecognizer.State,CGPoint,CGPoint) -> Void)?
    private var start=CGPoint.zero, active=false
    override init(frame:CGRect) { super.init(frame:frame); isMultipleTouchEnabled=false; backgroundColor = .clear }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch=touches.first else { return }
        start=touch.location(in:self); previous=start; timestamp=touch.timestamp; active=false; began?()
    }
    private var previous=CGPoint.zero
    private var timestamp=0.0
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch=touches.first else { return }
        let point=touch.location(in:self), translation=CGPoint(x:point.x-start.x,y:point.y-start.y)
        if !active && hypot(translation.x,translation.y)>=6 { active=true; action?(.began,translation,.zero) }
        if active {
            let dt=max(0.001,touch.timestamp-timestamp)
            let velocity=timestamp==0 ? CGPoint.zero : CGPoint(x:(point.x-previous.x)/dt,y:(point.y-previous.y)/dt)
            action?(.changed,translation,velocity)
        }
        previous=point; timestamp=touch.timestamp
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) { action?(.ended,.zero,.zero); timestamp=0 }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { action?(.cancelled,.zero,.zero); timestamp=0 }
}
