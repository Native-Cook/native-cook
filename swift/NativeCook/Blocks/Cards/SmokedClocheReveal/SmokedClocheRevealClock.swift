import SwiftUI
import UIKit

final class SmokedClocheRevealClock: ObservableObject {
  typealias M=SmokedClocheRevealMotion
  struct Tween {
    var from: Double, to: Double, at: Double, duration: Double, curve: Int
    var impacted=false
    var contact: Double? {
      curve == 4 ? at+duration*M.landAt : curve == 5 ? at+duration-0.22 : nil
    }
    func value(_ now: Double) -> Double {
      let p=M.seg(now,at,at+duration)
      if p == 0 { return from }
      if p == 1 { return to }
      if let contact {
        // Touch uses three timings; the scripted setDown uses a sine for its rock.
        let down=contact-at,rock=(duration-down)/2,t=now-at
        if t < down {
          let u=t/down
          return from*(1-(curve == 4 ? M.bezier(u) : u*u))
        }
        if t < down+rock { return from*M.landHop*(1-pow(1-(t-down)/rock,2)) }
        return from*M.landHop*(1-pow((t-down-rock)/rock,2))
      }
      let q: Double
      switch curve {
      case 1: q=p*p*p
      case 2: q=M.bezier(p)
      case 3: q=M.out(p)
      default: q=p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
      }
      return from+(to-from)*q
    }
  }
  @Published var moving=true
  @Published var sleeping=false
  private var sleepPending=false
  private var sleptAt: Date?
  private var wakeTimer: Timer?
  deinit { wakeTimer?.invalidate() }
  private func wake() {
    wakeTimer?.invalidate();wakeTimer=nil;sleepPending=false
    if let sleptAt { now += max(0,Date().timeIntervalSince(sleptAt)) }
    sleptAt=nil;date=nil;sleeping=false
  }
  private func sleepUntilReturn(_ seconds: Double) {
    guard !sleeping && !sleepPending else { return }
    sleepPending=true
    DispatchQueue.main.async { [weak self] in
      guard let self,self.sleepPending else { return }
      self.sleptAt=Date();self.sleeping=true;self.date=nil
      self.wakeTimer=Timer.scheduledTimer(withTimeInterval:seconds,repeats:false) { [weak self] _ in self?.wake() }
    }
  }
  private var date: Date?
  private var now=0.0,autoStart=0.0,lastTouch = -100.0,prevT = -1.0
  private var manual=false,auto=false,reduced=false,resumeAsked=false,lifting=false,dragged=false
  private var f=M.Frame(),channels=[String:Tween]()
  private var previousPoint=CGPoint(x:-999,y:-999)
  private let sound=SmokedClocheRevealSound()
  private var cartReturn: Double?,cartCounted=false
  private(set) var count=0
  func reset(_ autoplay: Bool, _ reduce: Bool) {
    wakeTimer?.invalidate();wakeTimer=nil;sleeping=false;sleepPending=false;sleptAt=nil
    date=nil;now=0;autoStart=0;lastTouch = -100;prevT = -1
    auto=autoplay && !reduce;manual = !auto;reduced=reduce;resumeAsked=false
    f=M.Frame();channels.removeAll();cartReturn=nil;moving = !reduce
  }
  func active() { if sleeping || sleepPending { wake() };date=nil }
  private func animate(_ key: String, _ target: Double, _ duration: Double, _ curve: Int) {
    let from=key == "lift" ? f.lift : key == "cart" ? f.cart : f.finger
    channels[key]=Tween(from:from,to:target,at:now,duration:duration,curve:curve)
    moving=true
  }
  func sample(_ time: Date, pinned: Double?) -> M.Frame {
    if let pin=pinned {
      var p=M.at(max(0,pin.truncatingRemainder(dividingBy:10)));p.pinned=pin;p.reduced=reduced;return p
    }
    // The loop follows elapsed wall time, including on GPU-less recording simulators.
    let dt=min(0.5,max(0,date.map { time.timeIntervalSince($0) } ?? 0.016))
    date=time;now += dt
    if !manual {
      let t=(now-autoStart).truncatingRemainder(dividingBy:10)
      f=M.at(t)
      if prevT < 8.7 && t >= 8.7 { f.mode=3 }
      if DemoSound.inAutoPlay {
        for (beat,kind) in [(0.85,1),(1.55,2),(6.2,0),(8.1+0.8*M.landAt,1)] {
          let crossed=prevT <= t ? prevT < beat && t >= beat : prevT < beat || t >= beat
          if crossed { sound.play(kind) }
        }
      }
      prevT=t
      // The dish is entirely in the cart and the smoke has thinned to zero.
      // No visible state changes until the return beat. Keep elapsed wall time.
      if t >= 6.9 && t < 7.4 { sleepUntilReturn(7.4-t) }
    } else {
      for key in Array(channels.keys) {
        var channel=channels[key]!
        let v=channel.value(now)
        if let contact=channel.contact, !channel.impacted && now >= contact {
          channel.impacted=true;haptic(1)
        }
        channels[key]=channel
        if key == "lift" { f.lift=v } else if key == "cart" { f.cart=v } else { f.finger=v }
        if now >= channel.at+channel.duration { channels.removeValue(forKey:key) }
      }
      if let returnAt=cartReturn {
        if !cartCounted && now >= returnAt-0.5 { count += 1;cartCounted=true }
        if now >= returnAt { animate("cart",0,reduced ? 0.001 : 0.6,3);cartReturn=nil }
      }
      if auto && !lifting && f.down == 0 && now-lastTouch >= 2 {
        if !resumeAsked { resumeAsked=true;if f.lift > 0.01 { putOn() } }
        else if f.lift < 0.001 && f.cart < 0.001 && now-lastTouch > 3.4 {
          autoStart=now;prevT = -1;manual=false;resumeAsked=false
        }
      }
    }
    f.time=now.truncatingRemainder(dividingBy:10);f.dt=reduced ? 0 : min(dt,0.05)
    if !manual {
      let p=M.fingerPoint(f,lastLayout)
      f.down=f.fingerAt == 1 && f.finger > 0.5 ? 1 : 0
      f.fx=f.down == 1 ? p.x : -999;f.fy=f.down == 1 ? p.y : -999
    }
    f.dx=f.down == 1 && previousPoint.x > -900 ? f.fx-previousPoint.x : 0
    f.dy=f.down == 1 && previousPoint.x > -900 ? f.fy-previousPoint.y : 0
    previousPoint=CGPoint(x:f.fx,y:f.fy)
    f.reduced=reduced
    let result=f
    if f.mode == 3 { f.mode=2 }
    if reduced && channels.isEmpty && cartReturn == nil && moving {
      DispatchQueue.main.async { [weak self] in self?.moving=false }
    }
    return result
  }
  var lastLayout=SmokedClocheRevealLayout(w:402,h:874,top:62,bottom:34)
  private func haptic(_ kind: Int) {
    if !reduced { sound.play(kind) }
    if kind == 0 { UISelectionFeedbackGenerator().selectionChanged() }
    else { UIImpactFeedbackGenerator(style:kind == 1 ? .light : .medium).impactOccurred() }
  }
  func takeOver() {
    if sleeping || sleepPending { wake() }
    date=nil;lastTouch=now;resumeAsked=false;moving=true
    if !manual {
      f.demoPoint=M.fingerPoint(f,lastLayout)
      manual=true;animate("finger",0,0.15,0);f.press=0;f.down=0
      if f.lift > 0.02 && f.lift < 0.98 { animate("lift",f.lift > 0.5 ? 1 : 0,0.3,0) }
      if f.cart > 0.01 { animate("cart",0,0.4,0) }
    }
  }
  func takeOff() { animate("lift",1,reduced ? 0.25 : 0.42,1);f.mode=1;haptic(2) }
  func putOn() {
    animate("lift",0,reduced ? 0.25 : 0.8,reduced ? 2 : 4);f.mode=3
    if reduced { haptic(1) }
  }
  func toCart() {
    takeOver();guard f.cart <= 0.01 else { return }
    animate("cart",1,reduced ? 0.001 : 0.7,2)
    cartReturn=now+(reduced ? 0.001 : 0.7)+0.5;cartCounted=false;haptic(0)
  }
  func touch(_ phase: Int, _ p: CGPoint, _ translation: Double, _ velocity: Double) {
    switch phase {
    case 0:
      takeOver();f.fx=p.x;f.fy=p.y;previousPoint=p;dragged=false
      if f.lift > 0.98 { f.down=1 }
    case 1:
      dragged=true;if f.lift < 0.02 { lifting=true }
      fallthrough
    case 2:
      lastTouch=now;f.fx=p.x;f.fy=p.y
      if lifting {
        let raw=max(0,-translation/260)
        let lift=raw < M.seal ? raw*0.35 : M.seal*0.35+(raw-M.seal)*1.1
        f.lift=lift < 0.6 ? lift : 0.6+0.15*tanh((lift-0.6)/0.15)
        channels.removeValue(forKey:"lift")
        if f.lift > 0.12 && f.mode != 1 { f.mode=1;haptic(1) }
      }
    default:
      if phase == 3 && lifting {
        if f.lift > 0.35 || velocity < -700 { takeOff() }
        else { animate("lift",0,max(0.12,0.42*sqrt(min(1,f.lift/0.35)))+0.22,5) }
      } else if phase == 3 && !dragged && f.lift > 0.98 { putOn() }
      lastTouch=now;lifting=false;f.down=0
    }
  }
  func accessibilityToggle() { takeOver();if f.lift > 0.5 { putOn() } else { takeOff() } }
}
