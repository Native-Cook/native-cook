import SwiftUI
import UIKit

final class DiceBoardRewardsClock: ObservableObject {
  typealias M = DiceBoardRewardsMotion
  @Published var running = false
  @Published var rolls = 3
  private var start = Date()
  private var lastDate: Date?
  private var now = 0.0
  private var th = M.Throw()
  private var prev = M.Throw()
  private var pos = 0
  private var lastTouch = -100.0
  private var scripted = false
  private var loopT = 0.0
  private var lastHop = 0
  private var previous = M.Out()
  private var announced = -1
  private var pauseRequested = false
  private let selection = UISelectionFeedbackGenerator()
  private let medium = UIImpactFeedbackGenerator(style:.medium)
  private let success = UINotificationFeedbackGenerator()
  func reset() {
    start = Date(); lastDate = nil; now = 0; th = M.Throw(); prev = M.Throw(); pos = 0
    lastTouch = -100; scripted = false; loopT = 0; lastHop = 0; rolls = 3; running = false; pauseRequested = false; announced = -1
  }
  func active() { lastDate = nil }
  func sample(_ date: Date,_ auto: Bool,_ pinned: Double?) -> M.Out {
    if let pinned { return M.scriptAt(pinned) }
    let wall = min(0.5,max(0,lastDate.map { date.timeIntervalSince($0) } ?? date.timeIntervalSince(start)))
    lastDate = date
    now += wall
    let busy = now-th.at < 1.15+Double(th.steps)*M.hop+2
    if auto && !busy && now-lastTouch > 3.5 && (scripted || pos%12 == 0) {
      if !scripted { scripted = true; loopT = 0 }
      loopT = (loopT+wall).truncatingRemainder(dividingBy:M.loop)
      let o = M.scriptAt(loopT)
      pos = o.u < 0 ? 0 : (o.th.from+min(o.th.steps,max(0,Int(floor((o.u-1.15)/M.hop))+1)))%12
      previous = o
      announceToast(o)
      return o
    }
    scripted = false
    let u = now-th.at, r = M.rollAt(th,now-th.at)
    let hop = Int(floor(r.hopsDone))
    if u > 1.15 && hop != lastHop && hop <= th.steps {
      lastHop = hop
      selection.selectionChanged()
      if hop == th.steps { success.notificationOccurred(.success) }
    }
    previous = M.Out(th:th,u:u,prev:prev,uPrev:now-prev.at)
    announceToast(previous)
    if running && !pauseRequested && (!auto || pos%12 != 0) && u >= 1.15+Double(th.steps)*M.hop+2 {
      pauseRequested = true
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return previous
  }
  private func announceToast(_ o: M.Out) {
    let index = M.toastIndex(o)
    if index >= 0 && index != announced {
      UIAccessibility.post(notification:.announcement,argument:M.prizes[index])
    }
    announced = index
  }
  func throwDie() {
    let wallNow = now + (lastDate.map { Date().timeIntervalSince($0) } ?? Date().timeIntervalSince(start))
    now = wallNow; lastDate = Date()
    if now-th.at < 1.15+Double(th.steps)*M.hop+0.6 && th.at > 0 { return }
    let rnd = sin(now*12.9898+Double(pos)*78.233)*43758.5453
    let value = 1+Int(floor((rnd-floor(rnd))*6))
    prev = th
    th = M.Throw(at:now,value:value,steps:value,from:pos)
    pos = (pos+value)%12; lastHop = 0; scripted = false; lastTouch = now
    rolls = rolls > 0 ? rolls-1 : 3
    medium.impactOccurred()
    pauseRequested = false
    running = true
  }
  func needsAnimation(_ date: Date,_ auto: Bool,_ pinned: Double?) -> Bool {
    if pinned != nil { return false }
    if auto && (scripted || pos%12 == 0) { return true }
    return running
  }
}
