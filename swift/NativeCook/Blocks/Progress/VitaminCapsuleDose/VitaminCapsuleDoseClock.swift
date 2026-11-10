import AVFoundation
import SwiftUI
import UIKit

final class VitaminCapsuleDoseClock: ObservableObject {
  typealias M=VitaminCapsuleDoseMotion
  @Published var running=true
  var frame=M.Frame()
  var checks=[1,0,0,1,0,0,1,0,0]
  private var checkAt=[Double](repeating:-100,count:9)
  private var checkFrom=[1.0,0,0,1,0,0,1,0,0]
  func checkLevel(_ i:Int)->Double {
    let p=M.bezier(M.seg(time,checkAt[i],checkAt[i]+0.28),0.25,0.1,0.25,1)
    return checkFrom[i]+(Double(checks[i])-checkFrom[i])*p
  }
  private func changeCheck(_ i:Int,_ value:Int) {
    guard checks[i] != value else{return}
    checkFrom[i]=checkLevel(i);checkAt[i]=time;checks[i]=value
  }
  private(set) var autoPlaying=false
  private(set) var reduced=false
  var previousMed = -1,medChangedAt=0.0
  var pressLevels=[Double](repeating:0,count:7)
  private var pressTweens:[Int:Tween]=[:]
  private var cancelledPress=false
  private func press(_ i:Int,_ down:Bool) {
    pressTweens[i]=Tween(from:pressLevels[i],to:down ? 1:0,start:time,duration:0.15,curve:2)
  }
  private var auto=false, configured=false
  private var previous:Date?
  private(set) var time=0.0
  private var autoStart=0.0,lastTouch = -100.0,lastLoop = -1.0
  private var resumeAsked=false,holding=false,dragging=false,pulling=false,holdTick=0
  private var yaw=0.0,pitch=0.0,yawV=0.0,pitchV=0.0
  private var target=0,origin=CGPoint.zero,lastPoint=CGPoint.zero,lastPointAt=0.0
  private var fingerPoint=CGPoint(x:-999,y:-999),fingerPrev=CGPoint(x:-999,y:-999),fingerDown=false
  private var spawn=false
  private var morphGeneration=0,takeGeneration=0
  private let light=UIImpactFeedbackGenerator(style:.light),selection=UISelectionFeedbackGenerator(),success=UINotificationFeedbackGenerator()
  private var players:[String:AVAudioPlayer]=[:]
  private struct Tween {
    var from,to,start,duration:Double
    var curve:Int
    func value(_ t:Double)->Double {
      let p=M.seg(t,start,start+duration)
      let k:Double
      switch curve {
      case 1:k=M.bezier(p,0.65,0,0.35,1)
      case 2:k=M.bezier(p,0.23,1,0.32,1)
      case 3:k=M.bezier(p,0.34,1.5,0.64,1)
      case 4:k=M.bezier(p,0.34,1.56,0.64,1)
      case 5:k=p
      default:k=p<0.5 ? 2*p*p:1-pow(-2*p+2,2)/2
      }
      return from+(to-from)*k
    }
  }
  private var tweens:[WritableKeyPath<M.Frame,Double>:Tween]=[:]
  private struct Spring {
    var to,value,velocity,omega,zeta,energy:Double
    mutating func step(_ dt:Double)->Bool {
      let t=min(0.064,dt),x=value-to,w1=omega*sqrt(1-zeta*zeta),e=exp(-zeta*omega*t)
      let sn=sin(w1*t),cs=cos(w1*t),frag=e*(sn*((velocity+zeta*omega*x)/w1)+x*cs)
      let nextVelocity = -zeta*omega*frag+e*(cs*(velocity+zeta*omega*x)-w1*x*sn)
      value=to+frag;velocity=nextVelocity
      if (omega*omega*4*pow(value-to,2)+4*velocity*velocity)/2/energy<=6e-9 {value=to;velocity=0;return true}
      return false
    }
  }
  private var springs:[WritableKeyPath<M.Frame,Double>:Spring]=[:]
  private func spring(_ key:WritableKeyPath<M.Frame,Double>,_ to:Double,_ duration:Double,_ zeta:Double,_ velocity:Double=0) {
    let x=frame[keyPath:key]-to
    var v=(springs[key]?.velocity ?? 0)+velocity
    // Match Reanimated's rejection of velocity pointing away from the target.
    if x*v>0 {v=0}
    if abs(x)<1e-12 && abs(v)<1e-12 {assign(key,to);return}
    // Reanimated 4.5.1 duration spring: solve stiffness against the same energy envelope.
    let settling=duration*1.5,mass=4.0,threshold=6e-9
    func diff(_ k:Double)->Double {
      let w=sqrt(k/mass)*zeta,e=exp(-w*settling)
      let xt=(x+(v+x*w)*settling)*e
      let vt=(x+(v+x*w)*settling)*e*(-w)+(v+x*w)*e
      return (k*xt*xt+mass*vt*vt)/(k*x*x+mass*v*v)-threshold
    }
    var a=Double.ulpOfOne,b=8000.0,mid=(Double.ulpOfOne+8000)/2
    let direction=diff(b)>=diff(a) ? 1.0:-1.0
    for _ in 0..<100 {
      if abs(diff(mid))<=threshold*0.001 {break}
      if diff(mid)*direction<0 {a=mid}else{b=mid};mid=(a+b)/2
    }
    tweens.removeValue(forKey:key)
    springs[key]=Spring(to:to,value:frame[keyPath:key],velocity:v,omega:sqrt(mid/mass),zeta:zeta,energy:(mid*x*x+mass*velocity*velocity)/2)
  }
  private var events:[(Double,()->Void)]=[]
  func configure(_ autoplay:Bool,_ reduce:Bool) {
    let preferenceChanged=reduced != reduce
    auto=autoplay && !reduce;reduced=reduce
    if !configured || autoPlaying != auto || preferenceChanged {
      autoStart=time;autoPlaying=auto;lastLoop = -1
      if !auto { frame.a=frame.k>0.5 ? frame.b:frame.a;frame.finger=0;frame.press=0;frame.hold=0;frame.take=0;frame.reform=1;frame.k=0 }
    }
    if reduce {
      frame.sep=0;frame.twist=0;frame.tilt=M.tilt;frame.mode=0;frame.hidden=0
      yaw=0;pitch=0;yawV=0;pitchV=0
      tweens.removeAll();springs.removeAll();events.removeAll()
    }
    configured=true;previous=nil;running = !reduce || auto
    if players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
      for (name,volume):(String,Float) in [("morph",0.5),("fill",0.45),("take",0.7),("done",0.6)] {
        if let url=Bundle.main.url(forResource:"VitaminCapsuleDose-vcd-\(name)",withExtension:"wav"),let p=try? AVAudioPlayer(contentsOf:url) {
          p.volume=volume;p.prepareToPlay();players[name]=p
        }
      }
    }
  }
  func active() { previous=nil }
  private func wake() { if !running { previous=nil;running=true };objectWillChange.send() }
  private func cue(_ name:String,_ stop:Bool=false) {
    guard let p=players[name] else{return}
    if stop {p.pause()} else {p.currentTime=0;p.play()}
  }
  private func animate(_ key:WritableKeyPath<M.Frame,Double>,_ to:Double,_ duration:Double,_ curve:Int=0,_ delay:Double=0) {
    springs.removeValue(forKey:key)
    tweens[key]=Tween(from:frame[keyPath:key],to:to,start:time+delay,duration:duration,curve:curve)
  }
  private func assign(_ key:WritableKeyPath<M.Frame,Double>,_ v:Double) { tweens.removeValue(forKey:key);springs.removeValue(forKey:key);frame[keyPath:key]=v }
  private func later(_ delay:Double,_ block:@escaping ()->Void) { events.append((time+delay,block)) }
  private func setDone(_ row:Int,_ v:Int) {setDoneIndex(frame.med*3+row,v)}
  private func setDoneIndex(_ i:Int,_ v:Int) {
    guard checks[i] != v else{return};changeCheck(i,v)
    if v==1 && !autoPlaying {success.notificationOccurred(.success);cue("done")}
  }
  func takeOver() {
    wake();lastTouch=time;resumeAsked=false
    if autoPlaying {
      autoPlaying=false
      animate(\.finger,0,0.15);frame.press=0
      if frame.k>0 {frame.a=frame.k>0.5 ? frame.b:frame.a;assign(\.k,0)}
      animate(\.hold,0,0.2)
      if frame.take>0 {assign(\.take,0);animate(\.reform,1,0.3)}
    }
  }
  func select(_ delta:Int) {
    guard frame.take<=0 else{return}
    let i=(frame.med+delta+3)%3,to=VitaminCapsuleDoseTheme.meds[i].shape
    previousMed=frame.med;medChangedAt=time
    frame.med=i;frame.a=frame.k>0.5 ? frame.b:frame.a;frame.b=to;assign(\.k,0)
    selection.selectionChanged();cue("morph")
    animate(\.k,1,reduced ? 0.25:M.morph,1)
    morphGeneration += 1;let generation=morphGeneration
    later(reduced ? 0.25:M.morph) { [weak self] in guard let self,self.morphGeneration==generation else{return};self.frame.a=self.frame.b;self.assign(\.k,0) }
  }
  private func popOpen() {
    guard frame.mode==0 else{return}
    animate(\.sep,M.sepOff,0.38,3);animate(\.twist,1.1,0.38);animate(\.tilt,M.pourTilt,0.5,1)
    frame.mode=2;spawn=true;light.impactOccurred();cue("take")
  }
  private func closeUp(_ toCheck:Bool) {
    if frame.mode==0 {spring(\.sep,0,0.5,0.7);animate(\.twist,0,0.4);return}
    frame.mode=toCheck ? 4:3
    animate(\.tilt,M.tilt,0.45,1,0.9);animate(\.twist,0,0.5,0,1.1);animate(\.sep,0,0.5,1,1.1)
    later(1.6) { [weak self] in self?.frame.mode=0 }
  }
  func doTake() {
    holding=false;animate(\.hold,0,0.26,2);cue("fill",true)
    guard let r=(0..<3).first(where:{checks[frame.med*3+$0]==0}) else {light.impactOccurred();return}
    frame.targetRow=r
    let index=frame.med*3+r
    if frame.mode==2 {
      closeUp(true);later(1) { [weak self] in self?.setDoneIndex(index,1) };return
    }
    cue("take")
    if reduced {setDone(r,1);return}
    assign(\.take,0);animate(\.take,1,M.take,5)
    takeGeneration += 1;let generation=takeGeneration
    later(M.take) { [weak self] in
      guard let self,self.takeGeneration==generation else{return};self.setDoneIndex(index,1);self.assign(\.take,0);self.assign(\.reform,0);self.animate(\.reform,1,M.reform,4)
    }
  }
  func undo(_ r:Int) {guard r>0,checks[frame.med*3+r]==1 else{return};setDone(r,0);light.impactOccurred()}
  func accessibilityTake() {takeOver();doTake()}
  func sample(_ date:Date,_ l:VitaminCapsuleDoseLayout)->M.Frame {
    // The playback clock follows wall time even on slow, GPU-less Cloud simulators.
    let wall=min(0.5,max(0,previous.map{date.timeIntervalSince($0)} ?? 0));previous=date;time += wall
    let dt=min(0.05,wall)
    let pending=events.filter{$0.0<=time};events.removeAll{$0.0<=time};for e in pending {e.1()}
    for (key,tween) in tweens where time>=tween.start {
      frame[keyPath:key]=tween.value(time)
      if time>=tween.start+tween.duration {tweens.removeValue(forKey:key)}
    }
    for key in Array(springs.keys) {
      guard var s=springs[key] else{continue}
      let done=s.step(wall);frame[keyPath:key]=s.value
      if done {springs.removeValue(forKey:key)}else{springs[key]=s}
    }
    for (i,tween) in pressTweens {
      pressLevels[i]=tween.value(time)
      if time>=tween.start+tween.duration {pressTweens.removeValue(forKey:i)}
    }
    let lt=time.truncatingRemainder(dividingBy:M.loop)
    if autoPlaying {
      let t=(time-autoStart).truncatingRemainder(dividingBy:M.loop)
      frame=M.at(t);frame.dt=min(1/30,wall)
      changeCheck(1,frame.taken>0.5 ? 1:0)
      if frame.fingerAt==3 {fingerPoint=M.finger(frame,l)}
      frame.fx=fingerPoint.x;frame.fy=fingerPoint.y
      frame.fdx=fingerPoint.x-fingerPrev.x;frame.fdy=fingerPoint.y-fingerPrev.y
      frame.down=frame.fingerAt==3 && frame.finger>0.5 ? 1:0
      if DemoSound.inAutoPlay {
        for (t0,name) in [(1.15,"take"),(5.0,"fill"),(7.19125989480318,"done")] where (lastLoop<t0 && t>=t0) {cue(name)}
        if lastLoop<6.2 && t>=6.2 {cue("fill",true)}
      }
      lastLoop=t
    } else {
      if !dragging && !reduced {
        let W=6.0
        yawV += (-W*W*yaw-2*0.45*W*yawV)*dt;pitchV += (-W*W*pitch-2*0.45*W*pitchV)*dt
        yaw += yawV*dt;pitch += pitchV*dt
      }
      if holding {
        assign(\.hold,min(1,frame.hold+dt/M.hold));let q=Int(floor(frame.hold*4))
        if q>holdTick {holdTick=q;light.impactOccurred()}
        if frame.hold>=1 {doTake()}
      }
      let id=reduced ? (yaw:0.0,pitch:0.0,bob:0.0):M.idle(lt)
      frame.yaw=id.yaw+yaw;frame.pitch=id.pitch+pitch-frame.hold*0.06;frame.bob=id.bob;frame.t=lt
      frame.hidden=frame.mode != 0 || spawn ? 1:0
      frame.dt=min(1/30,wall);frame.fx=fingerPoint.x;frame.fy=fingerPoint.y
      frame.fdx=fingerDown ? fingerPoint.x-fingerPrev.x:0;frame.fdy=fingerDown ? fingerPoint.y-fingerPrev.y:0;frame.down=fingerDown ? 1:0
      if auto && !dragging && !holding && time-lastTouch>=2 {
        if !resumeAsked {
          resumeAsked=true;for i in 0..<9 {changeCheck(i,i%3==0 ? 1:0)}
          if frame.mode != 0 || frame.sep>0.01 {closeUp(false)}
          if frame.med != 0 || frame.a != 0 {select(-frame.med)}
        } else {
          let still=frame.mode==0 && frame.sep<0.001 && abs(frame.tilt-M.tilt)<0.001 && abs(yaw)<0.01 && abs(pitch)<0.01 && abs(yawV)<0.05 && frame.k==0 && frame.take==0 && frame.reform>0.999
          if still && frame.a==0 && abs(id.yaw)<0.02 && abs(id.bob)<0.3 {resumeAsked=false;yaw=0;pitch=0;autoStart=time;lastLoop = -1;autoPlaying=true}
        }
      }
    }
    fingerPrev=fingerPoint
    var result=frame
    if spawn {result.mode=1;spawn=false}
    if reduced && !holding && tweens.isEmpty && springs.isEmpty && pressTweens.isEmpty && events.isEmpty && checkAt.allSatisfy({time-$0>=0.28}) && running {DispatchQueue.main.async {self.running=false}}
    return result
  }
  func touch(_ state:UIGestureRecognizer.State,_ p:CGPoint,_ translation:CGPoint,_ velocity:CGPoint,_ l:VitaminCapsuleDoseLayout) {
    if state == .began {
      origin=p;lastPoint=p;lastPointAt=time;target=0;pulling=false
      if CGRect(x:l.cx-l.unit*1.25,y:l.cy-l.unit*1.15,width:l.unit*2.5,height:l.unit*2.2).contains(p) {target=1}
      else if frame.mode==2 && CGRect(x:0,y:l.floor-70,width:l.w,height:84).contains(p) {target=2;fingerPoint=p;fingerPrev=p;fingerDown=true}
      else if CGRect(x:25*l.sx,y:l.y(777),width:353*l.sx,height:56*l.k).contains(p) {target=3}
      else {for r in 0..<3 where hypot(p.x-l.checkX,p.y-l.rowsY[r])<22 && checks[frame.med*3+r]==1 {target=4+r}}
      if target==0 {
        for i in 0..<4 where VitaminCapsuleDoseResources.shared.controlRect(l,i).insetBy(dx:i==0 ? -6:0,dy:i==0 ? -6:0).contains(p) {target=7+i}
      }
      cancelledPress=false
      if target>0 && target<4 {takeOver()}
      if target>=4 {wake();press(target<=6 ? target:target-7,true)}
      if target==3 && frame.take<=0 {holding=true;holdTick=0;cue("fill")}
    } else if state == .changed {
      guard target>0 else{return}
      if target>0 && target<4 {lastTouch=time}
      if target>=4 {
        let rect=target<=6 ? CGRect(x:l.checkX-22,y:l.rowsY[target-4]-22,width:44,height:44):VitaminCapsuleDoseResources.shared.controlRect(l,target-7)
        if !rect.insetBy(dx:-16,dy:-16).contains(p) {cancelledPress=true;press(target<=6 ? target:target-7,false)}
      }
      if target==1 && hypot(translation.x,translation.y)>=4 {
        if !dragging && !pulling {
          let along=(translation.x-translation.y)*sqrt(0.5)/max(1,hypot(translation.x,translation.y))
          pulling=frame.a==0 && frame.k==0 && frame.mode==0 && frame.sep<0.05 && along>0.6 && !reduced
        }
        if !reduced && frame.mode==0 {
          if pulling {
            let k=max(0,(translation.x-translation.y)*sqrt(0.5)/l.unit)
            assign(\.sep,k<0.6 ? k:0.6+(k-0.6)*0.2);assign(\.twist,min(1.2,frame.sep*1.8))
          } else {
            dragging=true;yaw=max(-1.4,min(1.4,translation.x/70));pitch=max(-0.9,min(0.9,-translation.y/90));yawV=velocity.x/70;pitchV = -velocity.y/90
          }
        }
      } else if target==2 {fingerPoint=p}
    } else if state == .ended || state == .cancelled || state == .failed {
      if target>0 && target<4 {lastTouch=time}
      if target>=4 {press(target<=6 ? target:target-7,false)}
      if target==1 && state == .ended {
        if pulling {
          if frame.sep>0.45 {popOpen()} else {
            let along=(velocity.x*sqrt(0.5)-velocity.y*sqrt(0.5))/l.unit*(frame.sep<0.6 ? 1:0.2)
            spring(\.sep,0,0.45,0.72,along);spring(\.twist,0,0.45,0.7,along*1.8)
          }
        }
        else if hypot(translation.x,translation.y)<8 {if frame.mode==2 {closeUp(false)}}
        else if frame.mode==0 && (abs(translation.x)>60 || abs(velocity.x)>650) {select(translation.x<0 ? 1:-1)}
      }
      if target==3 && holding {holding=false;animate(\.hold,0,0.26,2);cue("fill",true)}
      if target>=4 && target<=6 && !cancelledPress && state == .ended {takeOver();undo(target-4)}
      dragging=false;pulling=false;fingerDown=false;target=0
    }
  }
}
