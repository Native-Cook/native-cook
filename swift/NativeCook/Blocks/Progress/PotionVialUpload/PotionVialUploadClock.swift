import SwiftUI
import AVFoundation
import UIKit

final class PotionVialUploadClock: ObservableObject {
  typealias M = PotionVialUploadMotion
  @Published var running = false
  private var driver = PotionVialUploadDriver()
  private var date: Date?
  private var now = 0.0
  private var pauseQueued = false
  private var target = 0
  private var start = PotionVialUploadPoint(x:0,y:0)
  private var pinnedState: (Double,PotionVialUploadDriver,Double)?
  private var players: [String:AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style:.light)
  private let medium = UIImpactFeedbackGenerator(style:.medium)
  private let selection = UISelectionFeedbackGenerator()
  private let success = UINotificationFeedbackGenerator()
  func reset(_ reduced: Bool) {
    driver = .init(reduced ? M.T.still:0); date = nil; now = 0; target = 0; pauseQueued = false; running = false
  }
  func active(_ active: Bool,_ reduced: Bool,_ pinned: Bool) {
    date = nil
    if active && !reduced && !pinned {
      if players.isEmpty {
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for cue in ["pop","gulp","clink","chime","skitter"] {
          if let url = Bundle.main.url(forResource:"PotionVialUpload-pvu-\(cue)",withExtension:"wav"), let p = try? AVAudioPlayer(contentsOf:url) {
            p.volume = cue=="skitter" || cue=="clink" ? 0.6:0.85; p.prepareToPlay(); players[cue]=p
          }
        }
      }
    } else { for p in players.values { p.stop() } }
  }
  private func cue(_ name:String) { if let p = players[name] { p.currentTime=0; p.play() } }
  private func buzz(_ kind:Int) {
    if kind==3 { success.notificationOccurred(.success) } else if kind==2 { selection.selectionChanged() } else if kind==1 { medium.impactOccurred() } else { light.impactOccurred() }
  }
  private func event(_ ev:Int,_ sound:Bool) {
    if !sound { return }
    if ev==1 { cue("pop"); buzz(0) } else if ev==2 { cue("gulp"); buzz(1) } else if ev==3 { cue("clink") } else if ev==4 { cue("chime"); buzz(3) }
  }
  func sample(_ at:Date,_ auto:Bool,_ reduced:Bool,_ pinned:Double?,_ geo:PotionVialUploadGeo) -> PotionVialUploadFrame {
    if let pinned {
      if pinnedState?.0 != pinned { let (d,n) = PotionVialUploadDriver.replay(pinned); pinnedState=(pinned,d,n) }
      return M.frameAt(pinnedState!.1.t,pinnedState!.1,pinnedState!.2,geo,false)
    }
    if reduced { return M.frameAt(driver.t,driver,now,geo,true) }
    let raw = max(0,date.map{at.timeIntervalSince($0)} ?? 0.016), dt = min(raw,0.05)
    date=at; now += dt
    if driver.mode==1 && !driver.fileX.isNaN {
      let dist = hypot(driver.fileX-339,driver.fileY)
      if dist<88 && driver.gem.corked==1 && driver.gem.homeAt<0 { driver.gem.pop(0.8); event(1,true) }
      else if dist>110 && driver.gem.corked==0 { driver.gem.recork(now); event(3,true) }
    }
    if driver.fileBack<1 { driver.fileBack=min(1,(now-driver.fileBackAt)/0.5) }
    let autoplaying = auto && driver.mode==0 && driver.sound==0 && target==0 && now-driver.last>2
    let before = driver.t
    // Only unattended autoplay uses wall-clock time; touch playback and physics keep their step.
    let ev = driver.step(dt,now,auto,loopDT:autoplaying ? min(raw,0.5):dt)
    event(ev,driver.sound==1)
    if DemoSound.inAutoPlay && autoplaying {
      // Check every beat separately: a slow frame can cross both drop and recork.
      for (beat,name) in [(M.T.near,"pop"),(M.T.drop,"gulp"),(M.T.recork1,"clink"),
                          (M.T.done,"chime"),(M.T.recork2,"clink")] {
        let crossed = before <= driver.t
          ? before < beat && driver.t >= beat : before < beat || driver.t >= beat
        if crossed { cue(name) }
      }
    }
    if !auto && running && !pauseQueued && driver.mode == 0 && driver.gem.corked == 1
      && driver.gem.homeAt < 0 && driver.fileBack >= 1 && now-driver.last > 2
      && now-driver.gem.lastHome-driver.gem.homeDur >= 0.4 && now-driver.kickAt >= 1.2 {
      pauseQueued = true
      DispatchQueue.main.async { [weak self] in
        guard let self, self.pauseQueued else { return }
        self.running = false; self.pauseQueued = false; self.date = nil
      }
    }
    return M.frameAt(driver.t,driver,now,geo,false)
  }
  func touch(_ phase:Int,_ point:CGPoint,_ geo:PotionVialUploadGeo) {
    let p = PotionVialUploadPoint(x:(point.x-geo.ox)/geo.s,y:(point.y-geo.cy)/geo.s)
    let icon = geo.icon
    if phase==0 {
      start=p; target=0; driver.last=now; date=nil; pauseQueued=false; running=true
      let f=M.frameAt(driver.t,driver,now,geo,false)
      let inCard = p.y>=geo.cardY-8 && p.y<=geo.cardY+76 && p.x>=68 && p.x<=334
      if driver.gem.corked==0 && hypot(p.x-f.gemX,p.y-f.gemY)<34 { target=2 }
      else if hypot(p.x-f.spX,p.y-f.spY)<26 { target=3 }
      else if inCard && f.slotEmpty<0.5 { target=1 }
      else if abs(p.y)<40 && p.x>10 && p.x<345 { target=4 }
      if target==1 {
        driver.mode=1; driver.t=0; driver.fileX=icon.x; driver.fileY=icon.y
        if driver.gem.corked==0 { driver.gem.recork(now) }
      } else if target==2 {
        driver.mode=2; driver.gem.holdX=p.x-315.5; driver.gem.holdY=p.y-14
      } else if target==3 {
        driver.kickAt=now; driver.kickDir=f.spX>200 ? -1:1; cue("skitter"); buzz(2)
      }
    } else if phase==1 {
      driver.last=now
      if target==1 { driver.fileX=icon.x+p.x-start.x; driver.fileY=icon.y+p.y-start.y }
      else if target==2 { driver.gem.holdX=p.x-315.5; driver.gem.holdY=p.y-14 }
    } else {
      let tap=hypot(p.x-start.x,p.y-start.y)<10, tg=target; target=0; driver.last=now
      if tg==1 {
        let dist=driver.fileX.isNaN ? 999:hypot(driver.fileX-339,driver.fileY)
        if tap { driver.fileX = .nan; driver.t=0.6; driver.mode=0; driver.sound=1 }
        else if dist<88 { driver.drop(now); event(2,true) }
        else {
          driver.fileFromX=driver.fileX; driver.fileFromY=driver.fileY; driver.fileBack=0; driver.fileBackAt=now; driver.fileX = .nan; driver.mode=3
          if driver.gem.corked==0 { driver.gem.recork(now) }
        }
      } else if tg==2 { driver.gem.holdX = .nan; driver.mode=3; buzz(0) }
      else if tg==4 && tap {
        if driver.t>=8.5 && driver.t<11.6 { driver.t=11.59; driver.mode=0; driver.sound=1 }
        else if driver.t<0.6 || driver.mode==3 { driver.t=0.6; driver.mode=0; driver.sound=1 }
      }
    }
  }
  func replay(_ reduced:Bool) {
    if reduced { return }
    driver.t=0.6; driver.mode=0; driver.sound=1; driver.last=now; date=nil; pauseQueued=false; running=true
  }
}

struct PotionVialUploadTouchView: UIViewRepresentable {
  var onTouch:(Int,CGPoint)->Void
  func makeUIView(context:Context) -> PotionVialUploadTouchSurface {
    let view=PotionVialUploadTouchSurface(); view.backgroundColor = .clear; view.isMultipleTouchEnabled=false; view.onTouch=onTouch; return view
  }
  func updateUIView(_ view:PotionVialUploadTouchSurface,context:Context) { view.onTouch=onTouch }
}
final class PotionVialUploadTouchSurface: UIView {
  var onTouch:((Int,CGPoint)->Void)?
  override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) { if let t=touches.first { onTouch?(0,t.location(in:self)) } }
  override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) { if let t=touches.first { onTouch?(1,t.location(in:self)) } }
  override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) { if let t=touches.first { onTouch?(2,t.location(in:self)) } }
  override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { if let t=touches.first { onTouch?(2,t.location(in:self)) } }
}
