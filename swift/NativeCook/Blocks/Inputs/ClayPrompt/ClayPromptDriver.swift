import SwiftUI
import UIKit
import AVFoundation

final class ClayPromptDriver: ObservableObject {
  @Published var manual=false
  @Published var useUserText=false
  var scriptedChars=0
  var takeoverFrame=ClayPromptMotion.Frame(),takeoverDate=Date.distantPast
  var wipeRecover=0.0
  @Published var text=""
  @Published var focused=false
  @Published var keyboard=0.0
  @Published var revision=0
  @Published var generated=false
  var epoch=Date(), lastTouch=Date.distantPast
  var touchDown=false, dragFrom=0.0
  var dragCaught=false,dragFailed=false,wasAnimating=false
  var baseG=0.0, endG=0.0, genStart=Date(), genDuration=0.0
  var curve=0, releaseVelocity=0.0
  var releaseSpring=ClayPromptSpring(duration:0.52,damping:1)
  var shownKinds=[Int]()
  var slotKinds=[0,1,2,3]
  var wordPoints=[Int:CGPoint]()
  var wipeStart:Date?
  var kinds=[Int](), oldPops=[0.0,0,0,0], popTargets=[0.0,0,0,0], oldXs=[Double](repeating:0,count:4)
  var popStart=Date.distantPast, added=Set<Int>()
  var lastFrame=ClayPromptMotion.Frame(), lastTime = -1.0
  var players=[String:AVAudioPlayer]()
  let dust=ClayPromptDust()
  init() {
    for name in ["pop","melt","bloom"] {
      if let url=Bundle.main.url(forResource:"ClayPrompt-cpr-\(name)",withExtension:"wav"),let player=try? AVAudioPlayer(contentsOf:url) {
        player.volume=name == "pop" ? 0.6 : 0.7; player.prepareToPlay(); players[name]=player
      }
    }
    try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
  }
  func cue(_ name:String,_ reduced:Bool,_ scripted:Bool=false) {
    if !reduced && (!scripted || DemoSound.inAutoPlay),let p=players[name] { p.currentTime=0;p.play() }
    guard !scripted else { return }
    if name == "bloom" { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    else { UIImpactFeedbackGenerator(style:name == "pop" ? .light : .medium).impactOccurred() }
  }
  func currentG(_ now:Date)->Double {
    guard genDuration > 0 else { return endG }
    let x=ClayPromptMotion.seg(now.timeIntervalSince(genStart),0,genDuration)
    if curve == 2 {
      let result=releaseSpring.sample(now.timeIntervalSince(genStart)).0
      let value=endG+result
      // overshootClamping, as in the reference release.
      if (baseG < endG && value > endG) || (baseG > endG && value < endG) { return endG }
      return value
    }
    let a=curve == 1 ? cssEaseOut(x) : x
    return baseG+(endG-baseG)*a
  }
  func cssEaseOut(_ x:Double)->Double {
    var a=0.0,b=1.0
    for _ in 0..<20 {
      let t=(a+b)/2,u=1-t
      let bx=3*u*u*t*0.23+3*u*t*t*0.32+t*t*t
      if bx < x { a=t } else { b=t }
    }
    let t=(a+b)/2;return 1-pow(1-t,3)
  }
  func animate(_ to:Double,_ duration:Double,_ curve:Int=0,_ velocity:Double=0) {
    let now=Date();baseG=currentG(now);endG=to;genStart=now;genDuration=max(0.001,duration);self.curve=curve
    releaseVelocity=((to>baseG && velocity<0) || (to<baseG && velocity>0)) ? 0 : velocity
    releaseSpring=ClayPromptSpring(duration:genDuration,damping:1,displacement:baseG-to,velocity:releaseVelocity)
    lastTouch=curve == 0 ? now.addingTimeInterval(duration) : now;wasAnimating=true;revision += 1
  }
  func takeOver(_ frame:ClayPromptMotion.Frame,_ l:ClayPromptTheme.Layout) {
    let now=Date();lastTouch=now;wipeStart=nil
    if frame.clear>0 { wipeRecover=frame.clear;takeoverDate=now }
    if !manual {
      scriptedChars=frame.chars;takeoverFrame=frame;takeoverDate=now;wipeRecover=frame.clear
      baseG=frame.g;endG=frame.g;genDuration=0
      oldPops=frame.pops;popTargets=frame.pops;popStart=Date.distantPast
      kinds=[0,1,2];slotKinds=[0,1,2,3];oldXs=(0..<4).map { ClayPromptMotion.slotX($0,max(1,frame.pops.reduce(0,+)),l.w) }
      manual=true
    }
  }
  func edit(_ value:String,_ frame:ClayPromptMotion.Frame,_ l:ClayPromptTheme.Layout,_ reduced:Bool) {
    takeOver(frame,l)
    useUserText=true
    let now=Date(), previous=shownKinds, next=ClayPromptTheme.kinds(value)
    oldPops=samplePops(now,reduced);oldXs=sampleXs(now,l)
    shownKinds=next
    added=Set(next.filter { !previous.contains($0) });kinds=next
    for i in next.indices { slotKinds[i]=next[i] }
    popTargets=(0..<4).map { $0 < next.count ? 1 : 0 }
    for i in 0..<next.count where added.contains(next[i]) { oldPops[i]=0;oldXs[i]=ClayPromptMotion.slotX(i,Double(next.count),l.w) }
    popStart=now
    if let k=next.last(where:{ added.contains($0) }),let i=next.firstIndex(of:k) {
      cue("pop",reduced)
      if !reduced { dust.spawn(from:wordPoints[k] ?? CGPoint(x:l.w/2,y:l.composerTop+30-keyboard),to:CGPoint(x:ClayPromptMotion.slotX(i,Double(next.count),l.w),y:l.tokenY-keyboard),kind:k,time:now.timeIntervalSince(epoch),blobR:0) }
    }
    if currentG(now)>0.01 { animate(0,0.6,1);generated=false }
    text=value;wasAnimating=true;revision += 1
  }
  func samplePops(_ now:Date,_ reduced:Bool)->[Double] {
    let t=now.timeIntervalSince(popStart)
    return (0..<4).map { i in
      let isNew=i<kinds.count && added.contains(kinds[i])
      let a=reduced ? ClayPromptMotion.seg(t,0,0.2) : (isNew ? 1+ClayPromptSpring.pop.sample(t).0 : 1+ClayPromptSpring.move.sample(t).0)
      return oldPops[i]+(popTargets[i]-oldPops[i])*a
    }
  }
  func sampleXs(_ now:Date,_ l:ClayPromptTheme.Layout)->[Double] {
    if popStart == Date.distantPast { return oldXs }
    let a=1+ClayPromptSpring.move.sample(now.timeIntervalSince(popStart)).0
    return (0..<4).map { oldXs[$0]+(ClayPromptMotion.slotX($0,Double(max(1,kinds.count)),l.w)-oldXs[$0])*a }
  }
  func sample(_ now:Date,_ auto:Bool,_ reduced:Bool,_ pin:Double?)->(ClayPromptMotion.Frame,Double,[Double],[Int]) {
    let clock=max(0,now.timeIntervalSince(epoch))
    if auto && !reduced && !manual {
      let t=pin ?? clock.truncatingRemainder(dividingBy:11)
      return (ClayPromptMotion.frameAt(t),pin ?? clock,[],[0,1,2,3])
    }
    var f=ClayPromptMotion.Frame();f.chars=scriptedChars
    let takeoverAge=now.timeIntervalSince(takeoverDate)
    f.finger=takeoverFrame.finger*(1-ClayPromptMotion.seg(takeoverAge,0,0.15));f.fingerAt=takeoverFrame.fingerAt;f.dragY=takeoverFrame.dragY
    let wipeT=ClayPromptMotion.seg(takeoverAge,0,0.2)
    let wipeEase=wipeT<0.5 ? 2*wipeT*wipeT : 1-pow(-2*wipeT+2,2)/2
    f.clear=wipeRecover*(1-wipeEase)
    f.g=currentG(now);f.pops=samplePops(now,reduced)
    if let start=wipeStart { f.clear=cssEaseOut(ClayPromptMotion.seg(now.timeIntervalSince(start),0,0.7)) }
    return (f,clock,[],kinds)
  }
  func tick(_ f:ClayPromptMotion.Frame,_ clock:Double,_ l:ClayPromptTheme.Layout,_ auto:Bool,_ reduced:Bool) {
    let now=Date(), scripted=auto && !manual && !reduced
    if !reduced {
      for i in 0..<3 where f.pops[i]>0.02 && lastFrame.pops[i]<=0.02 {
        if scripted {
          cue("pop",false,true)
          dust.spawn(from:wordPoints[i] ?? CGPoint(x:l.w/2,y:l.composerTop+30),to:CGPoint(x:ClayPromptMotion.slotX(i,Double(i+1),l.w),y:l.tokenY),kind:i,time:clock,blobR:0)
        }
      }
      if f.g>0.2 && f.g<0.5 && !(lastFrame.g>0.2 && lastFrame.g<0.5) {
        dust.spawn(from:CGPoint(x:l.cx,y:l.cy),to:CGPoint(x:l.cx,y:l.cy),kind: -1,time:clock,blobR:ClayPromptMotion.look(f.g).blobR)
      }
    }
    if f.g>0.22 && lastFrame.g<=0.22 { cue("melt",reduced,scripted) }
    if f.g>0.55 && lastFrame.g<=0.55 { cue("bloom",reduced,scripted);if manual { generated=true } }
    if auto && !reduced && manual && !focused && !touchDown && (f.g<0.001 || f.g>0.999) && now.timeIntervalSince(lastTouch)>2 {
      if wipeStart == nil { wipeStart=now;revision += 1 }
      else if f.clear>0.999 {
        manual=false;useUserText=false;scriptedChars=0;text="";kinds=[];shownKinds=[];generated=false;endG=0;baseG=0;genDuration=0;wipeStart=nil;epoch=now;dust.reset();revision += 1
      }
    }
    let moving=now.timeIntervalSince(genStart)<genDuration*(curve == 2 ? 1.5 : 1) || now.timeIntervalSince(popStart)<2
    if wasAnimating && !moving { wasAnimating=false;revision += 1 }
    lastFrame=f;lastTime=clock
  }
}
