import SwiftUI
import UIKit

final class ZodiacJellyDishClock: ObservableObject {
  typealias M = ZodiacJellyDishMotion
  @Published var awake = false
  private var frame = M.Frame()
  private var date: Date?
  private var uiNow = 0.0
  private var last = -10.0
  private var held = false
  private var target = 0
  private var began = Date()
  private var cancelTap: DispatchWorkItem?
  private var tapValid = true
  private var start = CGPoint.zero
  private var buy = M.Press(), save = M.Press()
  private var pinnedTime: Double?
  private var pinnedFrame = M.Frame()
  func reset() { date = nil }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
    if let pinned {
      if pinnedTime != pinned {
        pinnedTime = pinned
        // RN's debug hook pins shader time without replaying the autoplay forces.
        // Its springs settle while the capture waits; live playback still uses script/step below.
        pinnedFrame = M.Frame(t:max(0,pinned.truncatingRemainder(dividingBy:M.loop)))
      }
      return pinnedFrame
    }
    let raw = max(0,date.map { at.timeIntervalSince($0) } ?? 0.016)
    let dt = min(raw,0.05)
    date = at; uiNow += dt
    frame.buyPress = buy.sample(uiNow).0; frame.savePress = save.sample(uiNow).0
    if !reduced {
      frame.clock += dt
      let touched = held || frame.clock-last < 2
      if auto && !touched {
        let before = frame.t
        // Keep the recording on wall-clock time; springs still use the clamped step.
        frame.t += min(raw,0.5)
        if frame.t >= M.loop { frame.t -= M.loop }
        frame.jelly.script(before,frame.t)
        if DemoSound.inAutoPlay && target == 0 {
          // Match the touch cues for releasing the jelly and adding the dish.
          for (beat, cue) in [(1.75,"squish"),(4.95,"add")] {
            let crossed = before <= frame.t
              ? before < beat && frame.t >= beat : before < beat || frame.t >= beat
            if crossed { ZodiacJellyDishResources.shared.play(cue,reduced) }
          }
        }
      }
      frame.jelly.step(dt)
    }
    if awake && !held && frame.clock-last >= 2 && uiNow-max(buy.at,save.at) > 2
      && abs(frame.jelly.px)+abs(frame.jelly.py)+abs(frame.jelly.vx)+abs(frame.jelly.vy)+abs(frame.jelly.sq)+abs(frame.jelly.sv) < 0.001 {
      DispatchQueue.main.async { [weak self] in self?.awake = false }
    } else if reduced && target == 0 && uiNow-max(buy.at,save.at) > 2 {
      DispatchQueue.main.async { [weak self] in self?.awake = false }
    }
    return frame
  }
  private func wake() { if !awake { date = nil; awake = true } }
  func add(_ reduced: Bool) {
    wake(); frame.addedAt = frame.clock; last = frame.clock
    if !reduced { frame.jelly.sv += 1.6 }
    ZodiacJellyDishResources.shared.play("add",reduced)
    UINotificationFeedbackGenerator().notificationOccurred(.success)
  }
  func save(_ reduced: Bool) {
    ZodiacJellyDishResources.shared.play("tap",reduced)
    UIImpactFeedbackGenerator(style:.light).impactOccurred()
  }
  func touch(_ phase: Int, _ point: CGPoint, _ size: CGSize, _ top: Double, _ bottom: Double, _ reduced: Bool) {
    let cx = size.width/2-2, cy = top+293
    let buyY = size.height-bottom-121
    let saveY = buyY+61
    func insideButton(_ point: CGPoint, _ button: Int) -> Bool {
      let y = button == 2 ? buyY : saveY
      let scale = button == 2 ? frame.buyScale : 1-frame.savePress*0.04
      return abs(point.x-size.width/2) <= 142.5*scale && abs(point.y-(y+23)) <= 23*scale
    }
    if phase == 0 {
      cancelTap?.cancel(); tapValid = true
      start = point; began = Date(); target = 0
      if insideButton(point,2) {
        target = 2; buy.set(1,uiNow,reduced)
      } else if insideButton(point,3) {
        target = 3; save.set(1,uiNow,reduced)
      } else if abs(point.x-cx) <= 159.5 && abs(point.y-cy) <= 225.5 {
        target = 1; held = true
        frame.jelly.gx = point.x-cx; frame.jelly.gy = point.y-cy
        frame.jelly.hold = true; frame.jelly.tx = frame.jelly.px; frame.jelly.ty = frame.jelly.py
        last = frame.clock
      }
      if target != 0 && (!reduced || target != 1) { wake() }
      if target == 2 || target == 3 {
        let work = DispatchWorkItem { [weak self] in
          guard let self, self.tapValid else { return }
          self.tapValid = false
          if self.target == 2 { self.buy.set(0,self.uiNow,reduced) }
          if self.target == 3 { self.save.set(0,self.uiNow,reduced) }
        }
        cancelTap = work; DispatchQueue.main.asyncAfter(deadline:.now()+2,execute:work)
      }
    } else if phase == 1 {
      if (target == 2 || target == 3) && tapValid && !insideButton(point,target) {
        // iOS RNGH leaves maxDeltaX/Y and maxDistance unset for this Tap gesture.
        // Its only movement criterion is shouldCancelWhenOutside, in the scaled view.
        tapValid = false; cancelTap?.cancel()
        if target == 2 { buy.set(0,uiNow,reduced) } else { save.set(0,uiNow,reduced) }
      }
      if target == 1 && held {
        let dx = point.x-start.x, dy = point.y-start.y, d = hypot(dx,dy)
        let k = d > 0 ? 70*tanh(d/90)/d : 0
        frame.jelly.tx = dx*k*(reduced ? 0.3:1); frame.jelly.ty = dy*k*(reduced ? 0.3:1)
        last = frame.clock
      }
    } else {
      cancelTap?.cancel()
      let hit = target; target = 0
      if hit == 1 && held {
        frame.jelly.hold = false; held = false; last = frame.clock
        if hypot(frame.jelly.px,frame.jelly.py)>12 {
          ZodiacJellyDishResources.shared.play("squish",reduced)
          UIImpactFeedbackGenerator(style:.light).impactOccurred()
        }
      } else if hit == 2 || hit == 3 {
        if tapValid {
          if hit == 2 { buy.set(0,uiNow,reduced) } else { save.set(0,uiNow,reduced) }
        }
        let ok = tapValid && phase == 2 && Date().timeIntervalSince(began) <= 2
        if ok { if hit == 2 { add(reduced) } else { save(reduced) } }
      }
    }
  }
}

struct ZodiacJellyDishTouch: UIViewRepresentable {
  var action: (Int,CGPoint) -> Void
  func makeUIView(context: Context) -> ZodiacJellyDishTouchSurface {
    let v = ZodiacJellyDishTouchSurface(); v.action = action; return v
  }
  func updateUIView(_ view: ZodiacJellyDishTouchSurface, context: Context) { view.action = action }
}
final class ZodiacJellyDishTouchSurface: UIView {
  var action: ((Int,CGPoint)->Void)?
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) { if let t = touches.first { action?(0,t.location(in:self)) } }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { if let t = touches.first { action?(1,t.location(in:self)) } }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { if let t = touches.first { action?(2,t.location(in:self)) } }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { if let t = touches.first { action?(3,t.location(in:self)) } }
}
