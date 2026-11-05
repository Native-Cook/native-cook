import CoreMotion
import SwiftUI
import UIKit

final class AuroraTeammatesOnboardingClock: ObservableObject {
    typealias M = AuroraTeammatesOnboardingMotion
    typealias L = AuroraTeammatesOnboardingTheme.Layout
    struct Sample { var scene:[Double];var press:Double }
    struct Tween {
        var from:Double,to:Double,start:Double,duration:Double
        var curve:Int=0
        var spring:AuroraTeammatesOnboardingSpring?
        mutating func value(_ now:Double,_ dt:Double)->Double {
            if var s=spring { let v=s.step(dt);spring=s;return to+v }
            let q=M.clamp01((now-start)/duration)
            let e=curve==1 ? 1-pow(1-q,3):curve==2 ? (q<0.5 ? 2*q*q:1-pow(-2*q+2,2)/2):q
            return M.mix(from,to,e)
        }
        func done(_ now:Double)->Bool { spring?.done ?? (now-start>=duration) }
    }
    @Published var running=false
    @Published var page=0
    private var date:Date?
    private var now=0.0,clock=0.0,amb=0.0,pos=0.0,resumeAt=0.0,autoVis=0.0
    private var took=false,held=false,playing=false,resumeFromPos=false
    private var playStart=0.0,playFrom=0.0,playTarget=0.0,playDuration=1.5,playReduced=false
    private var finaleStart:Double?
    private var input=M.Input()
    private(set) var press=0.0
    private var tweens:[String:Tween]=[:]
    private var dragFrom=0.0,dragging=false,failed=false,buttonTouch=false,buttonInside=false
    private var pressRect=CGRect.zero
    private var lastScene=Array(repeating:0.0,count:M.SIZE)
    private var lastTouched = -1,lastCheck=false
    private var motion=CMMotionManager()
    private var gyroInit=false,pitch=0.0,roll=0.0,gx=0.0,gy=0.0
    var onComplete:(()->Void)?
    private let selection=UISelectionFeedbackGenerator()
    private let soft=UIImpactFeedbackGenerator(style:.soft)
    private let success=UINotificationFeedbackGenerator()
    func configure(_ reduced:Bool,_ active:Bool,_ pinned:Bool) {
        date=nil
        if active && !reduced && !pinned && motion.isDeviceMotionAvailable {
            motion.deviceMotionUpdateInterval=1/60;motion.startDeviceMotionUpdates()
        } else { motion.stopDeviceMotionUpdates() }
    }
    deinit { motion.stopDeviceMotionUpdates() }
    func sample(_ at:Date,_ auto:Bool,_ reduced:Bool,_ pinned:Double?,_ l:L)->Sample {
        if let t=pinned {
            let tt=((t.truncatingRemainder(dividingBy:9))+9).truncatingRemainder(dividingBy:9),tau=tt.truncatingRemainder(dividingBy:3)
            var f=M.Input();f.drift=motion.isDeviceMotionAvailable ? 0:1;f.holdQ=tau<1.5 ? tau/1.5:1;f.autoVis=1
            return Sample(scene:M.computeScene(M.posAt(tt),tt,l,reduced,f),press:0)
        }
        // The demo clock follows wall time even on Xcode Cloud's slow software renderer.
        let dt=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0.016));date=at;now+=dt
        for key in Array(tweens.keys) {
            guard var t=tweens[key] else { continue }
            let value=t.value(now,dt)
            switch key { case "down":input.down=value;case "drag":input.drag=value;case "vx":input.vx=value;case "press":press=value;case "fin":input.fin=value;default:break }
            if t.done(now) { tweens[key]=nil } else { tweens[key]=t }
        }
        if !reduced {
            amb=(amb+dt).truncatingRemainder(dividingBy:9)
            if let r=motion.deviceMotion?.attitude {
                if !gyroInit && (r.pitch != 0 || r.roll != 0) { gyroInit=true;pitch=r.pitch;roll=r.roll }
                let kb=min(1,dt*0.5),ks=1-exp(-dt*9)
                pitch+=(r.pitch-pitch)*kb;roll+=(r.roll-roll)*kb
                gx+=(max(-1,min(1,(r.roll-roll)/0.35))-gx)*ks;gy+=(max(-1,min(1,(r.pitch-pitch)/0.35))-gy)*ks
            }
            autoVis+=((auto && !took ? 1:0)-autoVis)*(1-exp(-dt/0.22))
        }
        if let start=finaleStart {
            input.fin=M.clamp01((now-start)/(reduced ? 0.9:2.1))
            if input.fin>=1 { finaleStart=nil;let complete=onComplete;DispatchQueue.main.async { complete?() };playTo(floor(pos+0.5)+1,1,reduced) }
        } else if playing {
            let q=M.clamp01((now-playStart)/playDuration)
            let e=playReduced ? (q<0.5 ? 2*q*q:1-pow(-2*q+2,2)/2):q
            pos=M.mix(playFrom,playTarget,e)
            if q>=1 {
                pos=M.wrapPos(floor(pos+0.5));input.fin=0;playing=false;resumeAt=now+2;resumeFromPos=true
                selection.selectionChanged()
            }
        } else if !reduced && auto && !held && now>=resumeAt {
            took=false
            if resumeFromPos { clock=M.clockAt(pos);resumeFromPos=false }
            clock=(clock+dt).truncatingRemainder(dividingBy:9);pos=M.posAt(clock)
        }
        input.tiltX=gx;input.tiltY=gy;input.drift=motion.isDeviceMotionAvailable ? 0:1
        let tau=clock.truncatingRemainder(dividingBy:3)
        input.holdQ=tau<1.5 ? tau/1.5:1;input.autoVis=autoVis
        let sc=M.computeScene(pos,amb,l,reduced,input)
        let touched=Int(sc[M.GL.touched]),check=sc[M.GL.finCheck]>0.5
        if touched>=0 && touched != lastTouched { soft.impactOccurred() };lastTouched=touched
        if check && !lastCheck { success.notificationOccurred(.success) };lastCheck=check
        lastScene=sc
        let nextPage=Int(floor(M.wrapPos(pos)+0.5))%3
        if page != nextPage { DispatchQueue.main.async { [weak self] in if self?.page != nextPage { self?.page=nextPage } } }
        if reduced && !playing && finaleStart==nil && tweens.isEmpty && running {
            DispatchQueue.main.async { [weak self] in self?.running=false }
        }
        return Sample(scene:sc,press:press)
    }
    private func tween(_ key:String,_ a:Double,_ b:Double,_ duration:Double,_ cubic:Bool=false) {
        tweens[key]=Tween(from:a,to:b,start:now,duration:duration,curve:cubic ? 1:0);running=true
    }
    private func spring(_ key:String,_ a:Double,_ b:Double,_ duration:Double,_ ratio:Double) {
        tweens[key]=Tween(from:a,to:b,start:now,duration:duration,spring:AuroraTeammatesOnboardingSpring(value:a-b,duration:duration,ratio:ratio));running=true
    }
    private func playTo(_ target:Double,_ speed:Double,_ reduced:Bool) {
        playFrom=pos;playTarget=target;playStart=now;playReduced=reduced
        playDuration=reduced ? 0.45:max(0.12,abs(target-pos)*1.5*speed);playing=true;running=true
    }
    func next(_ reduced:Bool) {
        took=true
        if input.fin>0 { return }
        if !playing && abs(M.wrapPos(pos)-2)<1e-3 { playing=true;input.fin=0;finaleStart=now;running=true;return }
        let base=playing ? floor(pos+1):floor(pos+1e-4)
        let speed=playing ? 0.7:1
        playTo(base+1,speed,reduced)
    }
    func step(_ dir:Double,_ reduced:Bool) { playTo(floor(pos+0.5)+dir,1,reduced) }
    private func finalize(_ success:Bool,_ reduced:Bool) {
        held=false
        if !success && !playing && abs(pos-floor(pos+0.5))>1e-3 { playTo(ceil(pos),1,reduced) }
        tween("down",input.down,0,0.52,true);spring("vx",input.vx,0,0.7,0.55)
        if input.drag != 0 { spring("drag",input.drag,0,0.9,0.38) }
        if success || playing { resumeAt=now+2;resumeFromPos=true } else { resumeAt=now+0.25 }
    }
    func touch(_ state:UIGestureRecognizer.State,_ p:CGPoint,_ delta:CGSize,_ velocity:CGVector,_ l:L,_ reduced:Bool) {
        switch state {
        case .began:
            date=nil;running=true;dragging=false;failed=false
            let bottom=lastScene[M.GL.buttonBottom]
            let button=CGRect(x:l.cx-l.buttonW/2,y:l.H-bottom-l.buttonH,width:l.buttonW,height:l.buttonH)
            buttonTouch=button.contains(p);buttonInside=buttonTouch
            // React Native Pressability's default press retention: 20 on top/sides, 30 below.
            pressRect=CGRect(x:button.minX-20,y:button.minY-20,width:button.width+40,height:button.height+50)
            if buttonTouch { spring("press",press,1,0.12,1);return }
            held=true;input.x=p.x;input.y=p.y;tween("down",input.down,1,0.22,true)
        case .changed:
            if buttonTouch {
                let inside=pressRect.contains(p)
                if inside != buttonInside { buttonInside=inside;spring("press",press,inside ? 1:0,inside ? 0.12:0.26,inside ? 1:0.7) }
                return
            }
            if failed { return }
            if !dragging && abs(delta.height)>24 { finalize(false,reduced);failed=true;return }
            if !dragging && abs(delta.width)>12 {
                playing=false;took=true;dragFrom=pos;dragging=true
                if input.fin>0 { finaleStart=nil;tween("fin",input.fin,0,0.22) }
                tween("drag",input.drag,1,0.2,true)
            }
            if dragging {
                input.x=p.x;input.y=p.y;input.vx=velocity.dx
                let v=dragFrom+M.dragToPos(delta.width,l.W),lo=floor(dragFrom+1e-4)-1
                let over=v>lo+2 ? v-lo-2:v<lo ? v-lo:0,give=(over<0 ? -1.0:over>0 ? 1:0)*(1-exp(-abs(over)*3))/6
                pos=min(lo+2,max(lo,v))+give
            }
        case .ended,.cancelled,.failed:
            if buttonTouch {
                spring("press",press,0,0.26,0.7)
                if state == .ended && pressRect.contains(p) { next(reduced) }
                buttonTouch=false;return
            }
            if failed { return }
            if dragging && state == .ended {
                let projected=pos+M.dragToPos(velocity.dx*0.2,l.W),start=floor(dragFrom+1e-4)
                playTo(min(start+1,max(start-1,floor(projected+0.5))),0.85,reduced)
            }
            finalize(dragging && state == .ended,reduced)
            dragging=false
        default:break
        }
    }
}
