import SwiftUI
import UIKit

final class AppIconAtelierClock: ObservableObject {
  typealias M=AppIconAtelierMotion
  typealias F=M.Frame
  typealias Key=WritableKeyPath<F,Double>
  struct Animation {
    var from,to,start,duration: Double
    var curve: Int
    var velocity=0.0, omega=0.0, damping=1.0, energy=0.0
    var done: (() -> Void)?
  }
  @Published var running=false
  private var f=F()
  private var animations: [Key:Animation]=[:]
  private var date: Date?
  private var now=0.0, t=0.0, lastTouch = -100.0, updatedUntil = -100.0
  private var manual=false, resumeAsked=false, dragging=false, holding=false
  private var frontFrom=1.45, pending = -1
  var reduced=false
  var updated: Bool { now<updatedUntil }
  private let select=UISelectionFeedbackGenerator()
  private let light=UIImpactFeedbackGenerator(style:.light)
  private let success=UINotificationFeedbackGenerator()
  func active() { date=nil }
  private func wake() { date=nil; running=true; objectWillChange.send() }
  private func timing(_ key: Key,_ to: Double,_ duration: Double,_ curve: Int=0,_ done: (() -> Void)?=nil) {
    animations[key]=Animation(from:f[keyPath:key],to:to,start:now,duration:duration,curve:curve,done:done)
  }
  // Duration springs use Reanimated 4's mass, energy threshold and stiffness bisection.
  private func spring(_ key: Key,_ to: Double,_ duration: Double,_ damping: Double,_ velocity: Double=0,_ done: (() -> Void)?=nil) {
    let x=f[keyPath:key]-to
    var v=(animations[key]?.velocity ?? 0)+velocity
    if (to>f[keyPath:key] && v<0) || (to<f[keyPath:key] && v>0) { v=0 }
    let mass=4.0, threshold=6e-9, settling=duration*1.5
    func energy(_ k: Double) -> Double {
      let omega=sqrt(k/mass)*damping, e=exp(-omega*settling)
      let xt=(x+(v+x*omega)*settling)*e
      let vt=xt * -omega+(v+x*omega)*e
      let initial=0.5*k*x*x+0.5*mass*v*v
      return initial==0 ? 0 : (0.5*k*xt*xt+0.5*mass*vt*vt)/initial
    }
    var lo=Double.ulpOfOne, hi=8000.0
    for _ in 0..<100 {
      let mid=(lo+hi)/2, diff=energy(mid)-threshold
      if abs(diff)<=threshold*1e-3 { lo=mid; hi=mid; break }
      if diff>0 { lo=mid } else { hi=mid }
    }
    let k=(lo+hi)/2
    animations[key]=Animation(from:f[keyPath:key],to:to,start:now,duration:duration,curve:3,velocity:v,omega:sqrt(k/mass),damping:damping,energy:0.5*k*x*x+2*velocity*velocity,done:done)
  }
  private func advance(_ dt: Double) {
    for key in Array(animations.keys) {
      guard var a=animations[key] else { continue }
      var done=false
      if a.curve==3 {
        let h=min(dt,0.064), x=f[keyPath:key]-a.to, z=a.damping, w=a.omega, e=exp(-z*w*h)
        let next: Double, velocity: Double
        if z<1 {
          let wd=w*sqrt(1-z*z), s=sin(wd*h), c=cos(wd*h), frag=e*(s*(a.velocity+z*w*x)/wd+x*c)
          next=a.to+frag; velocity = -z*w*frag+e*(c*(a.velocity+z*w*x)-wd*x*s)
        } else {
          let q=x+(a.velocity+w*x)*h
          next=a.to+e*q; velocity=e * (-w*q+a.velocity+w*x)
        }
        a.velocity=velocity; f[keyPath:key]=next
        let energy=2*w*w*pow(next-a.to,2)+2*velocity*velocity
        done=a.energy==0 || energy/a.energy<=6e-9
      } else {
        let p=M.clamp((now-a.start)/a.duration)
        let k: Double
        switch a.curve {
        case 1: k=M.bezier(p,0.23,1,0.32,1)
        case 2: k=M.bezier(p,0.77,0,0.175,1)
        case 4: k=p
        default: k=p<0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
        }
        f[keyPath:key]=a.from+(a.to-a.from)*k; done=p>=1
      }
      if done { f[keyPath:key]=a.to; animations.removeValue(forKey:key); a.done?() }
      else { animations[key]=a }
    }
  }
  func sample(_ at: Date,_ auto: Bool,_ pin: Double?) -> F {
    if let pin { return M.at(pin) }
    let dt=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
    date=at; now+=dt
    if auto && !manual { t=(t+dt).truncatingRemainder(dividingBy:M.loop); let applied=f.applied; f=M.at(t); f.applied=applied; return f }
    let wasLanded=f.peek>0.96
    advance(dt)
    if !reduced { f.phase=(f.phase+M.phaseSpeed*dt).truncatingRemainder(dividingBy:M.tau) }
    if !wasLanded && f.peek>0.96 {
      if !reduced { timing(\.land,0.07,0.09,1) { [weak self] in self?.spring(\.land,0,0.42,0.5) } }
      light.impactOccurred()
    }
    if auto && !holding && !dragging && now-lastTouch>=2 {
      if !resumeAsked {
        resumeAsked=true
        if f.peek>0.001 { spring(\.peek,0,0.6,0.8) }
        if f.a != 0 || pending>=0 { goTo(0) }
      } else {
        let k=M.progress(f.front,f.dir)
        if f.a==0 && (k<0.0005 || k>0.9995) && abs(f.peek)<0.002 && abs(f.lean)<0.003 && abs(f.bob)<0.001 {
          resumeAsked=false; t=0; manual=false; animations.removeAll()
        }
      }
    }
    if !auto && animations.isEmpty && !holding && !dragging && !updated && running {
      DispatchQueue.main.async { [weak self] in self?.running=false }
    }
    return f
  }
  func configure(_ reduced: Bool) {
    self.reduced=reduced
    if reduced {
      manual=true
      let k=M.progress(f.front,f.dir), d=f.dir, keep=k>0.5
      if k>0.001 && k<0.999 {
        timing(\.front,keep ? -d*M.end : d*M.end,0.2) { [weak self] in
          guard let self,keep else { return }; self.f.a=self.f.b; self.f.front=d*M.end
        }
      }
      for key in [\F.peek,\.bob,\.lean,\.press,\.land,\.finger,\.fingerPress] { timing(key,0,0.2) }
      wake()
    } else { manual=false; t=0; date=nil }
  }
  private func takeOver() {
    lastTouch=now; resumeAsked=false; wake()
    if manual { return }; manual=true
    timing(\.finger,0,0.15); f.fingerPress=0
    timing(\.bob,0,0.5,1); spring(\.lean,0,0.5,0.85)
    timing(\.press,0,0.15); timing(\.land,0,0.15)
    if f.peek>0.001 && !holding { spring(\.peek,0,0.6,0.75) }
    let k=M.progress(f.front,f.dir)
    if k>0.001 && k<0.999 {
      let d=f.dir
      timing(\.front,-d*M.end,0.26+0.5*(1-k),1) { [weak self] in guard let self else { return }; self.f.a=self.f.b; self.f.front=d*M.end }
    }
  }
  private func startSweep(_ i: Int) {
    guard i != f.a && i>=0 && i<5 else { return }
    let d=i>f.a ? 1.0 : -1.0
    f.dir=d; f.b=i; f.front=d*M.end
    timing(\.front,-d*M.end,reduced ? 0.22 : 0.9,reduced ? 4 : 2) { [weak self] in
      guard let self else { return }; self.f.a=i; self.f.front=d*M.end; self.select.selectionChanged()
    }
  }
  private func goTo(_ i: Int) {
    let k=M.progress(f.front,f.dir)
    if k>0.001 && k<0.999 {
      pending=i; let d=f.dir
      timing(\.front,-d*M.end,0.16,1) { [weak self] in
        guard let self else { return }; self.f.a=self.f.b; self.f.front=d*M.end
        let n=self.pending; self.pending = -1; if n>=0 { self.startSweep(n) }
      }
    } else { startSweep(i) }
  }
  func pick(_ i: Int) { takeOver(); goTo(i) }
  func step(_ d: Int) { pick(max(0,min(4,f.shown+d))) }
  func apply() {
    takeOver(); let i=f.shown
    guard i != f.applied else { return }
    f.applied=i; updatedUntil=now+1.8
    if !reduced { timing(\.pop,1,0.12,1) { [weak self] in self?.spring(\.pop,0,0.65,0.45) } }
    success.notificationOccurred(.success)
  }
  func hero(_ event: AppIconAtelierTouch.Event,_ dx: Double,_ vx: Double,_ size: Double) {
    switch event {
    case .down: takeOver(); if !reduced { timing(\.press,1,0.3,1) }
    case .dragStart:
      dragging=true
      let k=M.progress(f.front,f.dir)
      if k<=0.001 || k>=0.999 {
        if k>=0.999 { f.a=f.b }
        f.dir=dx<0 ? 1 : -1; let target=f.a+Int(f.dir)
        f.b=(0..<5).contains(target) ? target : f.a; f.front=f.dir*M.end
      }
      animations.removeValue(forKey:\.front); pending = -1; frontFrom=f.front-dx/size
    case .drag:
      lastTouch=now; let start=f.dir*M.end
      var front=frontFrom+dx/size
      if f.a==f.b { let over=(start-front)*f.dir; front=start-f.dir*(over>0 ? over/(1+over/0.35) : 0) }
      f.front=max(-M.end,min(M.end,front))
      if !reduced { f.lean=f.lean*0.8+0.2*max(-0.22,min(0.22,vx/(size*22))) }
    case .hold:
      holding=true; timing(\.press,0,0.2)
      if reduced { timing(\.peek,1,0.2) } else { spring(\.peek,1,0.65,0.7) }
    case .up, .cancel:
      lastTouch=now
      if dragging && event == .up {
        let d=f.dir, k=M.progress(f.front,d), v=vx/size, along = -v*d
        let commit=f.b != f.a && along > -1.2 && (k>0.45 || along>1.2)
        let done: () -> Void = { [weak self] in guard let self,commit else { return }; self.f.a=self.f.b; self.f.front=d*M.end; self.select.selectionChanged() }
        if reduced { timing(\.front,commit ? -d*M.end : d*M.end,0.22,0,done) }
        else { spring(\.front,commit ? -d*M.end : d*M.end,0.62,0.92,v,done) }
      }
      dragging=false; timing(\.press,0,0.16,1); spring(\.lean,0,0.65,0.6)
      if holding { holding=false; if reduced { timing(\.peek,0,0.2) } else { spring(\.peek,0,0.7,0.72) } }
    }
  }
}
