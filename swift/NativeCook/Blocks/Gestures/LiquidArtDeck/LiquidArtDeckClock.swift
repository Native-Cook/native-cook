import SwiftUI
import AVFoundation
import UIKit

final class LiquidArtDeckClock: ObservableObject {
  @Published var moving = false
  var deck = LiquidArtDeckMotion.Deck()
  var t = 0.0, now = 0.0, phase = 0.0, last = -10.0
  private var date: Date?
  private var players: [String:AVAudioPlayer] = [:]
  private var pinnedState: (Double,LiquidArtDeckMotion.Deck)?
  func resetDate() { date = nil }
  func load() {
    _ = LiquidArtDeckResources.shared
    try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
    for cue in ["like","pass","undo"] {
      if let u = Bundle.main.url(forResource:"LiquidArtDeck-lad-\(cue)",withExtension:"wav"), let p = try? AVAudioPlayer(contentsOf:u) {
        p.volume = cue == "pass" ? 0.55 : 0.75; p.prepareToPlay(); players[cue] = p
      }
    }
  }
  func cue(_ cue: String) { players[cue]?.currentTime = 0; players[cue]?.play() }
  func sample(_ date: Date, _ auto: Bool, _ reduced: Bool, _ pin: Double?) -> (LiquidArtDeckMotion.Deck,Double,Double) {
    if let pin {
      if pinnedState?.0 != pin { pinnedState = (pin,LiquidArtDeckMotion.at(pin)) }
      return (pinnedState!.1,pin,pin)
    }
    // The display clock uses wall time even on slow recording simulators.
    let dt = min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016)); self.date = date
    now += dt
    // A scripted hold must keep advancing; a real touch resets t to zero.
    let eligible = auto && (t > 0 || deck.held == 0) && now-last >= 2 && (t > 0 || (deck.cur == 0 && deck.fly < 0 && deck.hist.isEmpty && abs(deck.dx)<1 && abs(deck.ds[0])<0.01))
    if eligible {
      let end = t+dt
      if end >= 9 { deck = .init(); t = end-9 }
      else {
        // Match the reference hook's fixed replay steps; no frame-rate-dependent springs.
        var st = t
        while st < end {
          let next = min(end,st+1/120), before = deck.hist.count
          deck.script(st,next)
          if DemoSound.inAutoPlay && !reduced {
            if before < deck.hist.count { cue(deck.kind == 1 ? "like" : "pass") }
            if before > deck.hist.count { cue("undo") }
          }
          st = next
        }
        t = end
      }
    } else { deck.step(min(dt,0.05),now) }
    // RN keeps the gentle artwork drift under reduced motion; only the deck script and smear stop.
    phase = (phase+dt).truncatingRemainder(dividingBy:9)
    let animating = deck.fly >= 0 || deck.held == 1 || abs(deck.dx)+abs(deck.dy)+abs(deck.vx)+abs(deck.vy)+abs(deck.sx)+abs(deck.sy)>0.01 || zip(deck.ds,0..<6).contains { abs($0.0-Double($0.1-deck.cur))>0.001 }
    if moving != animating { DispatchQueue.main.async { [weak self] in self?.moving = animating } }
    let drawingNow = auto && deck.held == 0 && now-last >= 2 ? t : now
    return (deck,drawingNow,phase)
  }
  func action(_ kind: Int, _ reduced: Bool) {
    last = now; t = 0; date = nil
    let ok = kind == 3 ? deck.undo(now) : deck.swipe(kind,now)
    if ok {
      moving = true
      if !reduced { cue(kind == 3 ? "undo" : kind == 1 ? "like" : "pass") }
      if kind == 3 { UISelectionFeedbackGenerator().selectionChanged() }
      else if kind == 1 { UINotificationFeedbackGenerator().notificationOccurred(.success) }
      else { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
  }
  func touch(_ state: UIGestureRecognizer.State, _ translation: CGPoint, _ velocity: CGPoint, _ reduced: Bool) {
    last = now; t = 0
    if state == .began {
      if deck.fly >= 0 || deck.cur >= 6 { return }; deck.held = 1; deck.vx = 0; deck.vy = 0; moving = true; date = nil
    } else if state == .changed {
      if deck.held == 1 { deck.dx = translation.x; deck.dy = translation.y*0.6 }
    } else if deck.held == 1 {
      deck.held = 0
      let proj = deck.dx+velocity.x*0.18
      if proj > 120 { action(1,reduced) }
      else if proj < -120 { action(2,reduced) }
      else { deck.vx = velocity.x; deck.vy = velocity.y*0.6 }
    }
  }
}

struct LiquidArtDeckTouch: UIViewRepresentable {
  var changed: (UIGestureRecognizer.State,CGPoint,CGPoint)->Void
  func makeUIView(context: Context) -> LiquidArtDeckTouchView {
    let view = LiquidArtDeckTouchView(); view.backgroundColor = .clear; view.changed = changed; return view
  }
  func updateUIView(_ view: LiquidArtDeckTouchView, context: Context) { view.changed = changed }
}
final class LiquidArtDeckTouchView: UIView {
  var changed: ((UIGestureRecognizer.State,CGPoint,CGPoint)->Void)?
  private var start = CGPoint.zero, previous = CGPoint.zero, velocity = CGPoint.zero
  private var stamp = 0.0
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    start = touch.location(in:self); previous = start; velocity = .zero; stamp = touch.timestamp
    changed?(.began,.zero,.zero)
  }
  private func report(_ touch: UITouch, _ state: UIGestureRecognizer.State) {
    let point = touch.location(in:self), dt = touch.timestamp-stamp
    if dt > 0 && point != previous { velocity = CGPoint(x:(point.x-previous.x)/dt,y:(point.y-previous.y)/dt) }
    previous = point; stamp = touch.timestamp
    changed?(state,CGPoint(x:point.x-start.x,y:point.y-start.y),velocity)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let t = touches.first { report(t,.changed) }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let t = touches.first { report(t,.ended) }
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let t = touches.first { report(t,.cancelled) }
  }
}
