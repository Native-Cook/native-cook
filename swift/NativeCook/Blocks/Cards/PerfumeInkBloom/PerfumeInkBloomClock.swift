import AVFoundation
import SwiftUI
import UIKit

final class PerfumeInkBloomClock: ObservableObject {
  typealias M=PerfumeInkBloomMotion
  @Published var running=false
  var frame=M.Frame()
  var auto=false, reduced=false, holding=false
  private var mode=0, wakeVersion=0
  private var date: Date?
  private var now=0.0, autoStart=0.0, lastTouch = -100.0, sprayAt = -100.0
  private var pressAt = -100.0, pressFrom=0.0, fingerFrom=0.0, fingerAt = -100.0
  private var pressAnimating=false
  var frozenFingerPress=0.0
  var autoPlaying: Bool { mode==0 && auto }
  private var sloshV=0.0, prevTilt=0.0, prevTiltV=0.0
  private var spring: PerfumeInkBloomSpring?
  private var controls=[PerfumeInkBloomControl](repeating:.init(),count:4)
  private var players=[String:AVAudioPlayer]()
  private let medium=UIImpactFeedbackGenerator(style:.medium)
  func reset(_ auto: Bool,_ reduced: Bool) {
    self.auto=auto && !reduced; self.reduced=reduced
    mode=self.auto ? 0 : 1; autoStart=now; date=nil; frame=M.Frame()
    sprayAt = -100; pressAt = -100; pressAnimating=false; sloshV=0; prevTilt=0; prevTiltV=0; spring=nil; holding=false
    running=self.auto
    if players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
      for name in ["spray","slosh"] {
        if let url=Bundle.main.url(forResource:"PerfumeInkBloom-pib-\(name)",withExtension:"wav"),let p=try? AVAudioPlayer(contentsOf:url) {
          p.volume=name=="spray" ? 0.6 : 0.5; p.prepareToPlay(); players[name]=p
        }
      }
    }
  }
  func active(_ active: Bool) { date=nil; if !active { for p in players.values { p.stop() } } }
  private func cue(_ name: String) { if let p=players[name] { p.currentTime=0; p.play() } }
  private func wake() { wakeVersion += 1; if !running { date=nil; running=true } }
  func controlPress(_ id: Int) -> Double { controls[id].at(now) }
  func setControl(_ id: Int,_ down: Bool) {
    wake(); controls[id] = .init(from:controlPress(id),to:down ? 1 : 0,start:now)
  }
  func takeOver() {
    wake(); lastTouch=now
    if mode != 0 { return }
    mode=1; fingerFrom=frame.finger; fingerAt=now; frame.press2=0
    sprayAt=frame.age>=0 ? now-frame.age : -100
    pressFrom=frame.press; pressAnimating=false; frozenFingerPress=frame.press
    spring=PerfumeInkBloomSpring(frame.tilt,0.7,0.45)
  }
  func spray() {
    takeOver(); pressAt=now; pressFrom=frame.press; sprayAt=now; pressAnimating=true
    medium.impactOccurred(); cue("spray")
  }
  func beginDrag() { if !reduced { holding=true } }
  func drag(_ dx: Double,_ scale: Double) {
    lastTouch=now
    if reduced { return }
    holding=true; spring=nil; frame.tilt=0.38*tanh(dx/(160*scale))
  }
  func release(_ dx: Double,_ velocityX: Double,_ scale: Double) {
    lastTouch=now
    if holding {
      holding=false
      if abs(frame.tilt)>0.08 { cue("slosh") }
      let u=160*scale, sech=1/cosh(dx/u)
      spring=PerfumeInkBloomSpring(frame.tilt,0.9,0.35,0.38*sech*sech/u*velocityX)
    }
  }
  func sample(_ at: Date,_ pinned: Double?) -> M.Frame {
    if let pinned { return M.frameAt(pinned) }
    // Autoplay follows wall time on slow recording simulators. Only live physics clamps dt.
    let elapsed=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
    date=at
    let before=now-autoStart
    now += elapsed
    if mode==0 && auto {
      let t=(now-autoStart).truncatingRemainder(dividingBy:M.LOOP)
      if DemoSound.inAutoPlay {
        for (beat,name) in [(0.75,"spray"),(8.4,"slosh")] {
          if floor((before-beat)/M.LOOP)<floor((now-autoStart-beat)/M.LOOP) { cue(name) }
        }
      }
      frame=M.frameAt(t)
    } else {
      let dt=min(0.05,elapsed)
      if spring != nil {
        frame.tilt=spring!.step(min(0.064,elapsed))
        if spring!.done { spring=nil }
      }
      let p=now-pressAt
      if pressAnimating {
        frame.press=reduced ? 0 : p<0.12 ? pressFrom+(1-pressFrom)*M.out(M.clamp(p/0.12)) : 1-M.out(M.clamp((p-0.12)/0.26))
      }
      frame.finger=fingerFrom*(1-PerfumeInkBloomControl.timing(M.clamp((now-fingerAt)/0.15)))
      frame.press2=0
      let a=now-sprayAt, on=a>=0 && a<6.9
      frame.age=on ? (reduced ? 2.6 : a) : -1
      frame.condense=on ? M.ease(M.clamp((a-1.15)/1.3)) : 0
      frame.evaporate=on ? M.clamp((a-4.85)/1.2) : 0
      let v=dt>0 ? (frame.tilt-prevTilt)/dt : 0
      let acc=dt>0 ? (v-prevTiltV)/dt : 0
      prevTilt=frame.tilt; prevTiltV=v
      sloshV += (-81*frame.slosh-2*0.16*9*sloshV-max(-400,min(400,acc))*0.12)*dt
      frame.slosh=reduced ? 0 : frame.slosh+sloshV*dt
      if auto && !holding && now-lastTouch>=2 && frame.age<0 && abs(frame.tilt)<0.002 && abs(frame.slosh)<0.004 && frame.press<0.001 {
        autoStart=now; frame.slosh=0; sloshV=0; mode=0
      }
    }
    let moving=auto || holding || frame.age>=0 || spring != nil || abs(frame.slosh)>0.00001 || abs(sloshV)>0.0001 || now-pressAt<0.38 || now-fingerAt<0.15 || controls.contains { now-$0.start<0.15 }
    if !moving && running {
      let version=wakeVersion
      DispatchQueue.main.async { [weak self] in if self?.wakeVersion==version { self?.running=false } }
    }
    return frame
  }
}

struct PerfumeInkBloomControl {
  var from=0.0, to=0.0, start = -100.0
  static func timing(_ p: Double) -> Double { p<0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2 }
  func at(_ now: Double) -> Double {
    let x=PerfumeInkBloomMotion.clamp((now-start)/0.15)
    var lo=0.0, hi=1.0
    func bez(_ t: Double,_ a: Double,_ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
    for _ in 0..<24 { let m=(lo+hi)/2; if bez(m,0.23,0.32)<x { lo=m } else { hi=m } }
    return from+(to-from)*bez((lo+hi)/2,1,1)
  }
}

// Reanimated 4 duration spring: mass 4, energyThreshold 6e-9, 1.5x perceptual duration.
struct PerfumeInkBloomSpring {
  var x: Double, v=0.0, w: Double, z: Double, done=false
  let initial: Double
  init(_ value: Double,_ duration: Double,_ damping: Double,_ velocity: Double=0) {
    x=value; z=damping
    // Match the installed Reanimated spring: only retain velocity toward its target.
    let v0=value*velocity>0 ? 0 : velocity
    v=v0
    var lo=Double.ulpOfOne, hi=8000.0
    let t=duration*1.5
    func fraction(_ stiffness: Double) -> Double {
      let om=sqrt(stiffness/4)*damping
      let xt=(value+(v0+value*om)*t)*exp(-om*t)
      let vt=xt*(-om)+(v0+value*om)*exp(-om*t)
      let energy=stiffness*value*value+4*v0*v0
      return energy==0 ? 0 : (stiffness*xt*xt+4*vt*vt)/energy-6e-9
    }
    for _ in 0..<100 {
      let mid=(lo+hi)/2, f=fraction(mid)
      if abs(f)<=6e-12 { lo=mid; hi=mid; break }
      if f>0 { lo=mid } else { hi=mid }
    }
    w=sqrt((lo+hi)/8)
    initial=w*w*value*value+velocity*velocity
  }
  mutating func step(_ dt: Double) -> Double {
    let wd=w*sqrt(1-z*z), s=sin(wd*dt), c=cos(wd*dt), e=exp(-z*w*dt)
    let a=e*(s*((v+z*w*x)/wd)+x*c)
    v = -z*w*a+e*(c*(v+z*w*x)-wd*x*s); x=a
    if initial==0 || (w*w*x*x+v*v)/initial<=6e-9 { x=0; v=0; done=true }
    return x
  }
}
