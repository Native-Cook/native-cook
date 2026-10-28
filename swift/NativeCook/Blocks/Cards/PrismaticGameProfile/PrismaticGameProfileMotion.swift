import UIKit

/// One elapsed-time owner. Gesture regrabs sample the presented channels, never their targets.
@MainActor
final class PrismaticGameProfileMotion {
    typealias G=PrismaticGameProfileGeometry
    struct Channel {
        var from:Double;var to:Double;var start:Double;var duration:Double;var spring:Bool=false;var velocity:Double=0
        var stiffness:Double=0
        init(_ from:Double,_ to:Double,_ start:Double,_ duration:Double,_ spring:Bool=false,_ velocity:Double=0) {
            self.from=from;self.to=to;self.start=start;self.duration=max(0.001,duration);self.spring=spring;self.velocity=velocity
            if spring {
                // Same duration-based Reanimated spring: mass4, energy6e-9, perceptual duration*1.5.
                let x=from-to,v=velocity,m=4.0,z=0.8,t=duration*1.5,threshold=6e-9
                func error(_ k:Double)->Double {
                    let w=sqrt(k/m)*z,e=exp(-w*t),a=x+(v+x*w)*t
                    let xx=a*e,vv=a*e * -w+(v+x*w)*e
                    return (k*xx*xx+m*vv*vv)/max(1e-30,k*x*x+m*v*v)-threshold
                }
                var lo=Double.ulpOfOne,hi=8000.0
                for _ in 0..<100 {let k=(lo+hi)/2;if error(k)>0{lo=k}else{hi=k}}
                stiffness=(lo+hi)/2
            }
        }
        func sample(_ now:Double)->(value:Double,done:Bool) {
            let t=max(0,now-start)
            if !spring {let p=G.clamp(t/duration);return(G.mix(from,to,G.contentEase(p)),p>=1)}
            let w=sqrt(stiffness/4),z=0.8,wd=w*sqrt(1-z*z),x=from-to
            let displacement=exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/max(1e-8,wd)*sin(wd*t))
            let value=to+displacement
            let crossed=(to>from && value>=to)||(to<from && value<=to)
            let done=crossed || t>=duration*1.5 || abs(to-from)<1e-9
            return(done ? to:value,done)
        }
    }
    var source=G.Source()
    private(set) var raw=G.Frame()
    private(set) var progress=0.0
    private(set) var content=0.0
    private(set) var tiltX=0.0
    private(set) var tiltY=0.0
    private(set) var opticalCatch=0.0
    private(set) var selected=1
    private(set) var origin=G.Origin(x:196.5,y:468,size:164)
    private(set) var kind=0
    private(set) var reduced=false
    private(set) var active=true
    private(set) var dragging=false
    private(set) var target=0.0
    private(set) var generation=0
    var onChange:(()->Void)?
    var onOpen:((Int)->Void)?
    var onClose:(()->Void)?
    var haptic:(()->Void)?
    private var autoPlay=false,manual=false,fixed:Double?
    private var now=0.0,epoch=0.0,carry=0.0,pausedAt:Double?
    private var detailAnimation:Channel?,contentAnimation:Channel?,railAnimation:Channel?,tiltAnimationX:Channel?,tiltAnimationY:Channel?
    private var closeAfterFade=false,awaitingOpen=false
    private var pulseStart:Double?,pulseCarry:(value:Double,start:Double,duration:Double)?
    private var catchEligible=false
    private var materialStart:Double?,releaseLight=G.Light(),grabLight=G.Light(),railOrigin=1.0
    private var railFeedback=false
    private var returnTarget=0.0,gestureProgress=0.0,gestureY=0.0,gestureRail=1.0,gestureX=0.0
    private var session=0
    private var currentClock:Double {now}
    var wantsClock:Bool {active && ((!reduced && !manual && autoPlay && fixed==nil) || detailAnimation != nil || contentAnimation != nil || railAnimation != nil || tiltAnimationX != nil || tiltAnimationY != nil || pulseStart != nil || pulseCarry != nil || materialStart != nil)}
    var isPlayer:Bool {kind==1 && progress>0}
    func configure(autoPlay:Bool,previewTime:Double?,reduced:Bool) {
        let changed=self.autoPlay != autoPlay || fixed != previewTime || self.reduced != reduced
        guard changed else{return}
        self.autoPlay=autoPlay;fixed=previewTime;self.reduced=reduced
        generation+=1;cancelChannels();manual=false;kind=0;target=0;progress=0;content=0;carry=0;epoch=now;raw=G.Frame(reduced:reduced)
        render();onChange?()
    }
    func setForeground(_ foreground:Bool,_ timestamp:Double) {
        guard foreground != active else{return}
        if !foreground {
            advance(timestamp);pausedAt=timestamp;active=false;generation+=1;dragging=false;session = -1
            // Freeze the actual current pose and invalidate all contact ownership.
            pulseStart=nil;pulseCarry=nil;opticalCatch=0;catchEligible=false
            if detailAnimation != nil {detailAnimation=nil};contentAnimation=nil;railAnimation=nil;tiltAnimationX=nil;tiltAnimationY=nil
        } else {
            if let pausedAt {epoch+=timestamp-pausedAt};self.pausedAt=nil;now=timestamp;active=true
            if manual && kind>0 {if abs(progress-target)>0.001{settle(target,0,false)}else if target==1 && content<1{contentAnimation=Channel(content,1,now,0.09*(1-content))}}
        }
        render();onChange?()
    }
    func advance(_ timestamp:Double) {
        guard active else{return};if now==0 {epoch=timestamp};now=timestamp
        var opened=false,closed=false
        if let a=railAnimation {let s=a.sample(now);raw.rail=G.clamp(s.value,0,2);if s.done{railAnimation=nil;startMaterial(selected,releaseLight);if railFeedback && !reduced{railFeedback=false;haptic?()}}}
        if let a=detailAnimation {let s=a.sample(now);progress=G.clamp(s.value);if s.done{detailAnimation=nil;opened=target==1;closed=target==0}}
        if let a=contentAnimation {let s=a.sample(now);content=G.clamp(s.value);if s.done{contentAnimation=nil;if closeAfterFade{closeAfterFade=false;startDetail(0,0)}}}
        if let a=tiltAnimationX {let s=a.sample(now);tiltX=s.value;if s.done{tiltAnimationX=nil}}
        if let a=tiltAnimationY {let s=a.sample(now);tiltY=s.value;if s.done{tiltAnimationY=nil}}
        if opened {finishOpening()};if closed {finishClosing()}
        if let start=pulseStart {let age=now-start;if age>=0.35{pulseStart=nil;opticalCatch=0}else{opticalCatch=age<=0.09 ? G.contentEase(age/0.09):1-G.contentEase((age-0.09)/0.26)}}
        else if let p=pulseCarry {let t=(now-p.start)/max(0.001,p.duration);opticalCatch=p.value*(1-G.contentEase(t));if t>=1{pulseCarry=nil;opticalCatch=0}}
        render()
    }
    private func render() {
        if !manual {
            let t=fixed ?? max(0,now-epoch+carry).truncatingRemainder(dividingBy:10)
            raw=(autoPlay || fixed != nil) ? G.gallery(t,reduced):G.Frame(reduced:reduced)
            progress=raw.kind==1 ? raw.panel:0;kind=raw.kind
            selected=Int(G.clamp(raw.rail.rounded(),0,2));origin=G.sourceBadge(1,1,source)
            content=progress>0 ? G.content(raw.time,reduced):0
            let tilt=G.tilt(raw.time);tiltX=reduced ? 0:tilt.x;tiltY=reduced ? 0:tilt.y
            opticalCatch=reduced || progress<=0 ? 0:G.catchStrength(raw.time)
        } else if !reduced && kind==0 {
            if dragging || railAnimation != nil {raw.light=G.underFinger(raw.rail,railOrigin,grabLight)}
            else if let materialStart {let duration=selected==0 ? 0.45:selected==1 ? 0.42:0.5,age=now-materialStart
                if age>=duration {self.materialStart=nil;raw.light=G.Light()}else{raw.light=G.finiteLight(selected,age,duration,releaseLight)}}
        }
        raw.reduced=reduced;raw.active=active && kind==0;raw.time=manual ? now:raw.time
        if reduced {raw.light=G.Light();tiltX=0;tiltY=0;opticalCatch=0}
    }
    private func cancelChannels(){detailAnimation=nil;contentAnimation=nil;railAnimation=nil;tiltAnimationX=nil;tiltAnimationY=nil;pulseStart=nil;pulseCarry=nil;materialStart=nil;closeAfterFade=false;awaitingOpen=false;opticalCatch=0;dragging=false}
    private func takeOwnership(_ newKind:Int) {
        let wasGallery = !manual
        let previousRaw=raw,previousProgress=progress,previousContent=content,previousCatch=opticalCatch
        generation+=1;session=generation;manual=true;kind=newKind
        detailAnimation=nil;contentAnimation=nil;railAnimation=nil;tiltAnimationX=nil;tiltAnimationY=nil;closeAfterFade=false;materialStart=nil
        if newKind==1 {
            if wasGallery && previousRaw.kind==1 {
                progress=previousProgress;content=previousContent;target=progress>=0.5 ? 1:0
                if previousCatch>0 {pulseStart=nil;pulseCarry=(previousCatch,now,max(0,(6.15-previousRaw.time)));opticalCatch=previousCatch}
                catchEligible=false
            } else if previousProgress<=0 {origin=G.sourceBadge(previousRaw.rail,selected,source);progress=0;content=0;target=0;catchEligible = !reduced;pulseStart=nil;pulseCarry=nil;opticalCatch=0;tiltX=0;tiltY=0}
            // A manual same-open regrab preserves the running/spent optical pulse.
        } else {pulseStart=nil;pulseCarry=nil;opticalCatch=0;catchEligible=false}
        raw=previousRaw;raw.panel=0;raw.kind=0
    }
    func openPlayer(){guard active else{return};takeOwnership(1);onOpen?(1);target=1;if reduced{progress=1;contentAnimation=Channel(content,1,now,0.09*(1-content))}else{startDetail(1,0)};onChange?()}
    func openStatistics(){guard active else{return};takeOwnership(2);progress=0;content=0;target=1;onOpen?(2);detailAnimation=Channel(0,1,now,reduced ? 0.001:0.26);onChange?()}
    func close(){guard active else{return};if !manual{takeOwnership(kind)};generation+=1;session=generation;dragging=false;target=0;pulseStart=nil;pulseCarry=nil;opticalCatch=0;catchEligible=false;tiltAnimationX=nil;tiltAnimationY=nil;detailAnimation=nil
        if reduced {closeAfterFade=true;contentAnimation=Channel(content,0,now,0.07*content)}
        else if content>0 {closeAfterFade=true;contentAnimation=Channel(content,0,now,0.07*content)}else{startDetail(0,0)}
        onChange?()
    }
    private func startDetail(_ to:Double,_ velocity:Double){target=to;detailAnimation=Channel(progress,to,now,kind==1 ? 0.3:(to==1 ? 0.26:0.2),kind==1,velocity);if reduced{progress=to;detailAnimation=nil;if to==0{finishClosing()}}}
    private func finishOpening(){if kind==1 && catchEligible && !reduced && active {catchEligible=false;pulseStart=now;opticalCatch=0};contentAnimation=Channel(content,1,now,0.09*(1-content));awaitingOpen=false}
    private func finishClosing(){progress=0;content=0;kind=0;raw.panel=0;raw.kind=0;raw.light=G.Light();tiltX=0;tiltY=0;opticalCatch=0;onClose?()}
    private func startMaterial(_ index:Int,_ carry:G.Light){guard !reduced else{return};selected=index;releaseLight=carry;materialStart=now}
    func select(_ index:Int,_ velocity:Double=0,_ feedback:Bool=true){guard active else{return};takeOwnership(0);selected=max(0,min(2,index));railFeedback=feedback;railOrigin=raw.rail;grabLight=raw.light;releaseLight=raw.light
        if reduced{raw.rail=Double(selected);raw.light=G.Light()}else{railAnimation=Channel(raw.rail,Double(selected),now,0.4,true,velocity)};onChange?()
    }
    func beginHorizontal(_ x:Double){guard active && kind==0 else{return};takeOwnership(0);dragging=true;gestureRail=raw.rail;gestureX=x;railOrigin=raw.rail;grabLight=raw.light;onChange?()}
    func updateHorizontal(_ x:Double){guard active && dragging && session==generation && kind==0 else{return};if !reduced{raw.rail=G.clamp(gestureRail-(x-gestureX)/146,0,2)};selected=Int(G.clamp(raw.rail.rounded(),0,2));render();onChange?()}
    func endHorizontal(_ x:Double,_ velocity:Double,_ cancelled:Bool){guard dragging && session==generation else{return};dragging=false
        let v = -velocity/146,q=reduced ? gestureRail-(x-gestureX)/146:raw.rail
        let index=Int(G.clamp((q+(cancelled ? 0:v*0.12)).rounded(),0,2));select(index,cancelled ? 0:v,!cancelled)
    }
    func beginPull(_ y:Double,_ fromRail:Bool){guard active && !reduced else{return};let wasGallery = !manual;let oldTarget=target;let pullKind=fromRail ? 1:kind;takeOwnership(pullKind);returnTarget=fromRail ? 0:wasGallery ? (progress>=0.5 ? 1:0):oldTarget;gestureProgress=fromRail ? 0:progress;gestureY=fromRail ? 0:y;dragging=true;session=generation
        if fromRail{progress=G.clamp(-y/120)}
        if progress<0.999{content=0;contentAnimation=nil}else if content<1{contentAnimation=Channel(content,1,now,0.09*(1-content))};onChange?()
    }
    func updatePull(_ y:Double){guard active && dragging && session==generation && kind>0 && !reduced else{return};let old=progress;progress=G.clamp(gestureProgress-(y-gestureY)/120)
        if progress<0.999{content=0;contentAnimation=nil}else if old<0.999{contentAnimation=Channel(content,1,now,0.09)}
        onChange?()
    }
    func endPull(_ velocityY:Double,_ cancelled:Bool){guard dragging && session==generation && active else{return};dragging=false;let v = -velocityY/120
        let to=cancelled ? returnTarget:progress+v*0.18>0.5 ? 1.0:0.0
        settle(to,cancelled ? 0:v,!cancelled && to==1 && returnTarget==0)
        if to==1 && !cancelled{onOpen?(1)};onChange?()
    }
    private func settle(_ to:Double,_ velocity:Double,_ feedback:Bool){target=to;contentAnimation=nil;if to==0 || progress<0.999{content=0};if to==0{pulseStart=nil;pulseCarry=nil;opticalCatch=0;catchEligible=false}
        if reduced{startDetail(to,velocity)}else{detailAnimation=Channel(progress,to,now,0.3,true,velocity)};if feedback && !reduced{haptic?()}
    }
    func beginTilt(){guard active && kind==1 else{return};takeOwnership(1);dragging=true;session=generation;returnTarget=target;if progress>=0.999 && content<1{contentAnimation=Channel(content,1,now,0.09*(1-content))};onChange?()}
    func updateTilt(_ x:Double,_ y:Double){guard active && dragging && session==generation && !reduced else{return};tiltX=G.clamp(x,-6,6);tiltY=G.clamp(y,-6,6);onChange?()}
    func endTilt(_ cancelled:Bool=false,_ initialX:Double=0,_ initialY:Double=0,_ velocityX:Double=0,_ velocityY:Double=0){guard session==generation && active else{return};dragging=false;target=1;tiltAnimationX=Channel(tiltX,cancelled ? initialX:0,now,0.4,true,cancelled ? 0:-velocityY/90*6);tiltAnimationY=Channel(tiltY,cancelled ? initialY:0,now,0.4,true,cancelled ? 0:velocityX/90*6);onChange?()}
    func pauseForOverlay(){if !manual{takeOwnership(0)};active=false;generation+=1;dragging=false;onChange?()}
    func resumeAfterOverlay(){active=true;now=CACurrentMediaTime();raw.light=G.Light();onChange?()}
}
