import SwiftUI
import UIKit

enum RamenBowlBuilderMotion {
  static let loop = 9.0
  static let names = ["Soft egg", "Fish cake", "Nori", "Shrimp", "Sushi roll"]
  static func smoother(_ v: Double) -> Double {
    let x = min(1, max(0, v))
    return x * x * x * (x * (x * 6 - 15) + 10)
  }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func scriptK(_ t: Double, _ i: Int) -> Double {
    let at = 1 + Double(i) * 1.1
    if t < at { return 0 }
    if t < 7.6 { return smoother((t - at) / 0.7) }
    return 1 - smoother((t - 7.6 - Double(4 - i) * 0.12) / 0.7)
  }
  struct Geo {
    let cx, cy, s: Double
    let homes, slots: [CGPoint]
    init(_ size: CGSize) {
      cx = size.width / 2
      cy = (size.height * 0.6).rounded()
      s = (size.width * 0.29).rounded()
      homes = [CGPoint(x: cx-s*1.15, y: cy-s*1.45), CGPoint(x: cx+s*0.25, y: cy-s*1.85),
        CGPoint(x: cx-s*1.25, y: cy-s*0.55), CGPoint(x: cx+s*1.25, y: cy-s*0.5),
        CGPoint(x: cx-s*0.45, y: cy-s*2.15)]
      slots = [CGPoint(x: cx-s*0.45, y: cy-s*0.12), CGPoint(x: cx+s*0.12, y: cy-s*0.18),
        CGPoint(x: cx-s*0.08, y: cy-s*0.02), CGPoint(x: cx+s*0.55, y: cy-s*0.05),
        CGPoint(x: cx+s*0.32, y: cy+s*0.04)]
    }
  }
  struct Frame {
    var k = [Double](repeating: 0, count: 5)
    var dragI = -1
    var dx = 0.0, dy = 0.0, t = 0.0
    var back = [Double](repeating: 0, count: 5)
    var bx = [Double](repeating: 0, count: 5)
    var by = [Double](repeating: 0, count: 5)
    var count: Int { k.filter { $0 > 0.95 }.count }
    static func at(_ time: Double) -> Self {
      let t = time.truncatingRemainder(dividingBy: loop)
      var f = Self()
      f.t = t
      f.k = (0..<5).map { scriptK(t, $0) }
      return f
    }
  }
}

// Like the RN shared-value driver: only count and wake/sleep changes invalidate SwiftUI.
final class RamenBowlBuilderClock: ObservableObject {
  typealias M = RamenBowlBuilderMotion
  @Published var count = 0
  @Published var moving = false
  private var frame = M.Frame()
  private var placed = [Int](repeating: 0, count: 5)
  private var scripted = false
  private var loopT = 0.0
  private var now = 0.0
  private var lastTouch = -100.0
  private var date: Date?
  private let selection = UISelectionFeedbackGenerator()
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  func resync() { date = nil }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
    if let pinned { return .at(pinned) }
    let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
    let dt = min(wall, 0.05)
    date = at
    now += wall
    let none = (0..<5).allSatisfy { placed[$0] == 0 && frame.k[$0] <= 0 }
    if auto && !reduced && frame.dragI < 0 && now-lastTouch > 3.5 && (scripted || none) {
      if !scripted { scripted = true; loopT = 0 }
      loopT = (loopT+wall).truncatingRemainder(dividingBy: M.loop)
      for i in 0..<5 {
        frame.k[i] = M.scriptK(loopT, i)
        placed[i] = frame.k[i] > 0.5 ? 1 : 0
        frame.back[i] = 0
      }
    } else {
      scripted = false
      for i in 0..<5 {
        if placed[i] == 1 && frame.k[i] < 1 {
          frame.k[i] = min(1, frame.k[i]+dt/(reduced ? 0.05 : 0.55))
          if frame.k[i] >= 1 { medium.impactOccurred() }
        }
        if frame.back[i] > 0 && frame.back[i] < 1 {
          frame.back[i] = min(1, frame.back[i]+dt/0.45)
        }
      }
    }
    frame.t = now
    let c = frame.count
    let active = frame.dragI >= 0 || (0..<5).contains {
      (placed[$0] == 1 && frame.k[$0] < 1) || (frame.back[$0] > 0 && frame.back[$0] < 1)
    }
    if count != c || moving != active {
      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        if self.count != c { self.count = c }
        if self.moving != active { self.moving = active }
      }
    }
    return frame
  }
  func begin(_ p: CGPoint, _ g: M.Geo) {
    scripted = false; lastTouch = now
    var best = -1
    var bd = g.s*0.55
    for i in 0..<5 where placed[i] != 1 {
      let d = hypot(p.x-g.homes[i].x, p.y-g.homes[i].y)
      if d < bd { bd = d; best = i }
    }
    frame.dragI = best; frame.dx = p.x; frame.dy = p.y
    if best >= 0 { selection.selectionChanged() }
    moving = true
  }
  func update(_ p: CGPoint) { frame.dx = p.x; frame.dy = p.y; lastTouch = now }
  func end(_ g: M.Geo) {
    let i = frame.dragI
    frame.dragI = -1
    guard i >= 0 else { return }
    frame.bx[i] = frame.dx; frame.by[i] = frame.dy
    if hypot(frame.dx-g.cx, (frame.dy-g.cy)*1.6) < g.s*1.3 {
      placed[i] = 1; frame.k[i] = 0; frame.back[i] = -1
    } else { frame.back[i] = 0.001 }
    lastTouch = now
    moving = true
  }
  func next(_ g: M.Geo) {
    selection.selectionChanged()
    guard frame.count >= 5 else { return }
    for i in 0..<5 where placed[i] == 1 {
      placed[i] = 0; frame.k[i] = 0
      frame.bx[i] = g.slots[i].x; frame.by[i] = g.slots[i].y
      frame.back[i] = 0.001
    }
    scripted = false; lastTouch = now; moving = true
  }
}
