import SwiftUI
import AVFoundation
import UIKit

final class FriendRadarMapClock: ObservableObject {
  typealias M = FriendRadarMapMotion
  @Published var running = false
  @Published var labelIndex = 0
  private var frame = M.Frame()
  private var date: Date?
  private var now = 0.0, last = -10.0, doneAt = -10.0
  private var holding = false
  private var gestureStart: CGPoint?
  private var gestureDate: Date?
  private var maximumX = 0.0, maximumDistance = 0.0
  private var buttonGesture = false
  private var buttonFinalized = false
  private var recordingStarted = false
  private var players: [String: AVAudioPlayer] = [:]
  func activate(_ active: Bool,_ reduced:Bool) {
    date = nil
    if !active { holding = false; gestureStart = nil; buttonGesture = false; last = now }
    guard active && !reduced else { for p in players.values { p.stop() }; return }
    if players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
      for cue in ["glide","decode","ordered"] {
        if let url = Bundle.main.url(forResource:"FriendRadarMap-frm-"+cue,withExtension:"wav"), let p = try? AVAudioPlayer(contentsOf:url) {
          p.volume = cue == "decode" ? 0.5:0.75; p.prepareToPlay(); players[cue] = p
        }
      }
    }
  }
  private func cue(_ cue:String,_ reduced:Bool) { if !reduced, let p = players[cue] { p.currentTime = 0; p.play() } }
  func sample(_ at:Date,_ auto:Bool,_ reduced:Bool,_ pinned:Double?) -> M.Frame {
    if let pinned { return M.Frame.at(pinned) }
    // Keep the loop on wall time even on slow, GPU-less recording simulators.
    let dt = min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0.016)); date = at; now += dt
    let scripted = auto && !reduced && !holding && now-last>=2
    if scripted {
      let before = frame.time
      frame = M.Frame.at((frame.time+dt).truncatingRemainder(dividingBy:M.loop)).mergingOrder(frame)
      if DemoSound.inAutoPlay && !players.isEmpty {
        if !recordingStarted || Int(before/3) != Int(frame.time/3) || frame.time<before { cue("glide",reduced) }
        recordingStarted = true
      }
    } else { frame.since += dt }
    let step = min(dt,0.05)
    if holding && frame.fill<1 {
      frame.fill = min(1,frame.fill+step/1.1)
      if frame.fill>=1 { doneAt = now; cue("ordered",reduced); UINotificationFeedbackGenerator().notificationOccurred(.success) }
    } else if !holding && frame.fill<1 { frame.fill = max(0,frame.fill-step*3) }
    if frame.fill>=1 && now-doneAt>2.6 { frame.fill = 0 }
    frame.burst = now-doneAt
    let label = frame.labelIndex
    if labelIndex != label { DispatchQueue.main.async { [weak self] in self?.labelIndex = label } }
    if running && !holding && frame.fill == 0 && frame.since>1.3 && now-last>2 && (!auto || reduced) {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return frame
  }
  func go(_ direction:Int,_ reduced:Bool) {
    frame.prev = frame.cur; frame.cur = (frame.cur+direction+3)%3; frame.since = 0
    last = now; date = nil; running = true; cue("glide",reduced); UISelectionFeedbackGenerator().selectionChanged()
  }
  func changed(_ value:DragGesture.Value,_ size:CGSize,_ bottom:Double) {
    if gestureStart == nil {
      gestureStart = value.startLocation; gestureDate = Date(); maximumX = 0; maximumDistance = 0; buttonFinalized = false
      buttonGesture = value.startLocation.x>=20 && value.startLocation.x<=size.width-20 && value.startLocation.y>=size.height-bottom-86 && value.startLocation.y<=size.height-bottom-22
      if buttonGesture && frame.fill<1 {
        holding = true; last = now; running = true; date = nil; UIImpactFeedbackGenerator(style:.light).impactOccurred()
      }
    }
    maximumX = max(maximumX,abs(value.translation.width))
    maximumDistance = max(maximumDistance,hypot(value.translation.width,value.translation.height))
    if buttonGesture && !buttonFinalized && (maximumDistance>40 || value.location.x<20 || value.location.x>size.width-20 || value.location.y<size.height-bottom-86 || value.location.y>size.height-bottom-22) { holding = false; last = now; buttonFinalized = true }
  }
  func ended(_ value:DragGesture.Value,_ size:CGSize,_ top:Double,_ bottom:Double,_ reduced:Bool) {
    defer { gestureStart = nil }
    if buttonGesture {
      holding = false; if !buttonFinalized { last = now }; return
    }
    if maximumX>14 {
      if abs(value.translation.width)>40 || abs(value.velocity.width)>500 { go(value.translation.width<0 ? 1:-1,reduced) }
      return
    }
    guard maximumDistance<=10, Date().timeIntervalSince(gestureDate ?? Date())<=0.5 else { return }
    let selected = M.friends[frame.cur]
    for i in 0..<3 where i != frame.cur {
      let f = M.friends[i], x = size.width/2+f.x-selected.x, y = top+size.height*0.3+f.y-selected.y-34
      if abs(value.location.x-x)<36 && abs(value.location.y-y)<36 { go((i-frame.cur+3)%3 == 1 ? 1:-1,reduced); return }
    }
  }
}
private extension FriendRadarMapMotion.Frame {
  func mergingOrder(_ old:Self)->Self { var v = self; v.fill = old.fill; v.burst = old.burst; return v }
}
