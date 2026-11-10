import SwiftUI
import UIKit
import AVFoundation

final class FilamentBulbDimmerClock:ObservableObject {
    typealias M=FilamentBulbDimmerMotion
    @Published var running=false
    var frame=M.Frame()
    private var date:Date?
    private var now=0.0,start=0.0,lastTouch = -100.0,switchedAt = -100.0
    private var mode=0,thV=0.0,sV=0.0,twV=0.0,held=false,clicked=false,resuming=false
    private var hold=CGPoint.zero,grab=CGPoint.zero,grabAcross=0.0,target=0,lastStep=16
    private var touchStarted=0.0
    private var maxTravel=0.0,scrubbing=false
    private var tweens:[String:Tween]=[:]
    private var togSpring:FilamentBulbDimmerSpring?,pctSpring:FilamentBulbDimmerSpring?,knobSpring:FilamentBulbDimmerSpring?
    private var uiOn=1.0,uiPct=78.0,uiTog=1.0,pctTarget=78.0,togTarget=1.0,knob=0.0,knobTarget=0.0
    private var trailSpring:FilamentBulbDimmerSpring?,trailTarget=2.0
    private var players:[String:AVAudioPlayer]=[:]
    struct Tween {
        var from,to,at,duration:Double
        var out=false
        var quad=false
        func value(_ now:Double)->Double {
            let x=M.seg(now,at,at+duration)
            if quad { return from+(to-from)*(x<0.5 ? 2*x*x:1-pow(-2*x+2,2)/2) }
            let v=out ? M.bezier(x,0.23,1,0.32,1):M.bezier(x,0.77,0,0.175,1)
            return from+(to-from)*v
        }
    }
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for (cue,volume) in [("pull",0.85),("release",0.6),("tink",0.35)] {
            if let u=Bundle.main.url(forResource:"FilamentBulbDimmer-fbd-\(cue)",withExtension:"wav"),let p=try? AVAudioPlayer(contentsOf:u) {
                p.volume=Float(volume); p.prepareToPlay(); players[cue]=p
            }
        }
    }
    func cue(_ name:String) { if let p=players[name] { p.currentTime=0; p.play() } }
    func buzz(_ kind:Int) { if kind==0 { UISelectionFeedbackGenerator().selectionChanged() } else { UIImpactFeedbackGenerator(style:kind==1 ? .light:.medium).impactOccurred() } }
    func reset(_ auto:Bool,_ reduced:Bool) {
        frame=M.Frame();mode=auto && !reduced ? 0:1;date=nil;start=now;running=false
        lastTouch = -100;switchedAt = -100;thV=0;sV=0;twV=0;held=false;clicked=false;resuming=false;target=0;scrubbing=false;lastStep=16;trailTarget=2
        tweens.removeAll();togSpring=nil;pctSpring=nil;knobSpring=nil;trailSpring=nil
        uiOn=1;uiPct=78;uiTog=1;pctTarget=78;togTarget=1;knob=0;knobTarget=0
    }
    func active() { date=nil }
    func configure(_ auto:Bool,_ reduced:Bool) {
        start=now; date=nil; mode=auto && !reduced ? 0:1
        if mode==1 { tweens.removeAll();trailSpring=nil;togSpring=nil;pctSpring=nil;knobSpring=nil;held=false;scrubbing=false;target=0;frame.on=frame.heat>0.35 ? 1:0; frame.heat=frame.on==1 ? frame.level:0; frame.th=0; frame.tw=0; frame.s=0; thV=0;sV=0;twV=0;frame.lead=frame.trail.rounded();frame.trail=frame.lead;frame.finger=0 }
    }
    func sample(_ at:Date,auto:Bool,reduced:Bool,pinned:Double?,layout l:FilamentBulbDimmerTheme.Layout)->M.Frame {
        if let t=pinned { return M.frameAt(t) }
        let wall=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0.016)); date=at
        let before=(now-start).truncatingRemainder(dividingBy:10); now+=wall
        let dt=min(0.05,wall)
        if mode==0 && auto && !reduced {
            let t=(now-start).truncatingRemainder(dividingBy:10),f=M.frameAt(t)
            if dt>0 { thV=(f.th-frame.th)/dt; sV=(f.s-frame.s)/dt; twV=(f.tw-frame.tw)/dt }
            frame=f
            if DemoSound.inAutoPlay {
                for (time,c) in [(0.6,"tink"),(M.off,"pull"),(8.42,"release"),(9.15,"pull")] {
                    if (before<time && t>=time) || (t<before && t>=time) { cue(c) }
                }
            }
        } else {
            for (key,a) in tweens {
                let v=a.value(now)
                switch key { case "level":frame.level=v;case "lead":frame.lead=v;case "trail":frame.trail=v;case "finger":frame.finger=v;default:break }
                if now>=a.at+a.duration { tweens[key]=nil }
            }
            if var a=trailSpring { frame.trail=trailTarget+a.step(dt);trailSpring=a.done ? nil:a }
            if !reduced {
                let h=dt/4,calm=resuming ? 4.0:1.0
                for _ in 0..<4 {
                    let a:Double,ss:Double
                    if held {
                        let dx=hold.x-grab.x-l.anchorX,dy=hold.y-grab.y-l.anchorY,tt=max(-1,min(1,atan2(dx,max(1,dy))))
                        var st=hypot(dx,dy)-l.cord
                        if st>30 { st=30+(st-30)*0.2 }; st=max(-44,min(42,st))
                        a=1600*(tt-frame.th)-80*thV;ss=1600*(st-frame.s)-80*sV
                        twV+=grabAcross*a*h*0.9
                    } else {
                        a = -M.SWING_W*M.SWING_W*sin(frame.th)-2*M.SWING_Z*calm*M.SWING_W*thV
                        ss=frame.s < -0.5 ? M.GRAV*cos(frame.th):-M.PULL_W*M.PULL_W*frame.s-2*M.PULL_Z*M.PULL_W*sV
                    }
                    let ta = -M.TWIST_W*M.TWIST_W*frame.tw-2*M.TWIST_Z*calm*M.TWIST_W*twV
                    thV+=a*h;frame.th+=thV*h;sV+=ss*h;frame.s+=sV*h;twV+=ta*h;frame.tw+=twV*h
                }
                if held && !clicked && frame.s>16 { clicked=true; toggle(true) }
                if clicked && frame.s<6 { clicked=false }
            }
            var heatTarget=frame.on==1 ? frame.level:0
            let since=now-switchedAt
            if frame.on==1 && since<M.WARM && !reduced { heatTarget*=M.warmUp(since/M.WARM) }
            let tau=reduced ? 0.07:heatTarget>frame.heat ? 0.09:0.22
            frame.heat+=(heatTarget-frame.heat)*(1-exp(-dt/tau));frame.time=now.truncatingRemainder(dividingBy:10)
            if auto && !reduced && !held && !scrubbing && now-lastTouch>=2 {
                if !resuming {
                    resuming=true
                    if frame.on==0 { frame.on=1;switchedAt=now }
                    tween("level",frame.level,0.78,0.7);tween("lead",frame.lead,2,0.5,true);tween("trail",frame.trail,2,0.75)
                } else if abs(frame.th)<0.004 && abs(thV)<0.02 && abs(frame.s)<0.4 && abs(sV)<2 && abs(frame.tw)<0.02 && abs(twV)<0.05 && abs(frame.heat-0.78)<0.002 && abs(frame.level-0.78)<0.001 && abs(frame.trail-2)<0.001 {
                    resuming=false;frame.th=0;frame.s=0;frame.tw=0;start=now;mode=0
                }
            }
        }
        if frame.on != uiOn { uiOn=frame.on;togTarget=uiOn;togSpring=FilamentBulbDimmerSpring(value:uiTog-togTarget,duration:0.42,ratio:0.72,velocity:togSpring?.velocity ?? 0) }
        let pct=(frame.level*100).rounded()
        if pct != pctTarget { pctTarget=pct;pctSpring=FilamentBulbDimmerSpring(value:uiPct-pct,duration:0.26,ratio:1,velocity:pctSpring?.velocity ?? 0) }
        if var a=togSpring { uiTog=togTarget+a.step(dt);togSpring=a.done ? nil:a } else { uiTog=togTarget }
        if var a=pctSpring { uiPct=pctTarget+a.step(dt);pctSpring=a.done ? nil:a } else { uiPct=pctTarget }
        if var a=knobSpring { knob=knobTarget+a.step(dt);knobSpring=a.done ? nil:a }
        frame.tog=reduced ? frame.on:uiTog; frame.shownPct=reduced ? pct:uiPct;frame.knobPress=reduced ? (target==2 ? 1:0):knob
        if reduced && abs(frame.heat-(frame.on==1 ? frame.level:0))<0.0001 && tweens.isEmpty && running {
            DispatchQueue.main.async { [weak self] in self?.running=false }
        }
        return frame
    }
    private func tween(_ key:String,_ a:Double,_ b:Double,_ d:Double,_ out:Bool=false) { tweens[key]=Tween(from:a,to:b,at:now,duration:d,out:out) }
    func takeOver() {
        if !running && mode==1 { date=nil }; running=true;lastTouch=now;resuming=false
        if mode==0 { mode=1;switchedAt = -100;tween("finger",frame.finger,0,0.15);tweens["finger"]?.quad=true;frame.fingerPress=0 }
    }
    func toggle(_ sound:Bool) { frame.on=frame.on==1 ? 0:1;switchedAt=now;buzz(2);if sound { cue("pull") } }
    func choose(_ i:Double,_ reduced:Bool) {
        takeOver();trailSpring=nil; let value=max(0,min(4,i))
        if reduced { frame.lead=value;frame.trail=value } else { tween("lead",frame.lead,value,0.34,true);tween("trail",frame.trail,value,0.6) };buzz(0)
    }
    func adjust(_ by:Double) { takeOver();tween("level",frame.level,max(0,min(1,frame.level+by)),0.2);tweens["level"]?.quad=true }
    func powerSwitch(_ reduced:Bool) { takeOver();toggle(true);if !reduced { twV+=frame.on==1 ? 0.25 : -0.25 } }
    private func setLevel(_ p:CGPoint,_ l:FilamentBulbDimmerTheme.Layout) {
        frame.level=l.levelAt(p);tweens["level"]=nil
        let s=Int((frame.level*20).rounded()); if s != lastStep { lastStep=s;buzz(s==0 || s==20 ? 1:0) }
    }
    func touch(_ state:UIGestureRecognizer.State,_ point:CGPoint,_ translation:CGSize,_ l:FilamentBulbDimmerTheme.Layout,_ reduced:Bool) {
        func swatch(_ x:Double)->Double { max(0,min(4,(x-62*l.sx)/(278*l.sx)*4)) }
        switch state {
        case .began:
            touchStarted=ProcessInfo.processInfo.systemUptime;target=0;maxTravel=0;scrubbing=false
            if l.toggle.contains(point) { target=4 }
            else if CGRect(x:l.cx-l.rOut-10,y:l.cy-l.rOut-10,width:l.rOut*2+20,height:l.rOut+l.leg+(l.rOut-l.rIn)/2+20).contains(point) { target=2 }
            else if CGRect(x:62*l.sx-26,y:l.dotsY-24,width:278*l.sx+52,height:48).contains(point) { target=3 }
            else if CGRect(x:l.anchorX-130,y:l.restY-12,width:260,height:3.74*l.R+8).contains(point) { target=1 }
            if target==0 { return };takeOver()
            if target==1 {
                let p=l.pose(frame).h;grab=CGPoint(x:point.x-p.x,y:point.y-p.y);hold=point
                grabAcross=max(-1,min(1,(grab.x*cos(frame.th)-grab.y*sin(frame.th))/l.R));held=true;cue("tink");buzz(1)
            }
            if target==2 { setLevel(point,l);knobTarget=1;knobSpring=FilamentBulbDimmerSpring(value:knob-1,duration:0.22,ratio:0.7) }
        case .changed:
            guard target>0 else { return };lastTouch=now;maxTravel=max(maxTravel,hypot(translation.width,translation.height))
            if target==1 { hold=point }
            if target==2 { setLevel(point,l) }
            if target==3 && (maxTravel>=8 || scrubbing) {
                scrubbing=true;let s=swatch(point.x);frame.lead=s;tweens["lead"]=nil;tweens["trail"]=nil
                if Int(s.rounded()) != Int(frame.trail.rounded()) { buzz(0) }
                if reduced { frame.trail=s;trailSpring=nil } else { trailTarget=s;trailSpring=FilamentBulbDimmerSpring(value:frame.trail-trailTarget,duration:0.38,ratio:0.9,velocity:trailSpring?.velocity ?? 0) }
            }
        case .ended,.cancelled,.failed:
            guard target>0 else { return };lastTouch=now;maxTravel=max(maxTravel,hypot(translation.width,translation.height))
            let isTap=state == .ended && ProcessInfo.processInfo.systemUptime-touchStarted<=0.5
            if target==1 { held=false;if frame.s>8 { cue("release") }; if isTap && maxTravel<=6 { toggle(true);if !reduced { sV+=170;twV+=0.5 } } }
            if target==2 { knobTarget=0;knobSpring=FilamentBulbDimmerSpring(value:knob,duration:0.3,ratio:0.7) }
            if target==3 { if scrubbing { choose(frame.lead.rounded(),reduced) } else if isTap && maxTravel<=8 { choose(swatch(point.x).rounded(),reduced) };scrubbing=false }
            if target==4 && state == .ended && l.toggle.insetBy(dx:-20,dy:-20).contains(point) { powerSwitch(reduced) }
            target=0
        default:break
        }
    }
}
