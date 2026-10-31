import SwiftUI

struct SwipeToOrderOnboardingArt {
  typealias M = SwipeToOrderOnboardingMotion
  let size: CGSize
  let top: Double
  let bottom: Double
  let r = SwipeToOrderOnboardingResources.shared
  static func color(_ hex: Int, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: a)
  }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
  }
  func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: x-radius, y: y-radius, width: radius*2, height: radius*2))
  }
  func label(_ c: GraphicsContext, _ key: String, _ x: Double, _ y: Double, _ centered: Bool = false) {
    guard let im = r.labels[key] else { return }
    c.draw(Image(uiImage: im), in: CGRect(x: x-(centered ? (im.size.width-4)/2 : 0), y: y, width: im.size.width, height: im.size.height))
  }
  func background(_ c: GraphicsContext, _ f: M.Frame) {
    let brandH = r.labels["brand"]!.size.height
    let skipH = r.labels["skip"]!.size.height
    label(c, "brand", 22, top-48+(36-brandH)/2)
    label(c, "skip", size.width-22-(r.labels["skip"]!.size.width-4), top-48+(36-skipH)/2)
    for i in [2, 1] {
      let b = f.cards[i].p
      var card = c
      card.translateBy(x: size.width/2+b[0], y: top+b[1])
      card.rotate(by: .degrees(b[2])); card.scaleBy(x: b[3], y: b[3])
      card.draw(Image(uiImage: r.cards[i]), in: CGRect(x: -150, y: -43, width: 300, height: 86))
    }
  }
  static let bolt: Path = {
    var p = Path(); p.move(to: .init(x: 14, y: 2)); p.addLine(to: .init(x: 5, y: 14)); p.addLine(to: .init(x: 11, y: 14)); p.addLine(to: .init(x: 10, y: 22)); p.addLine(to: .init(x: 19, y: 10)); p.addLine(to: .init(x: 13, y: 10)); p.closeSubpath(); return p
  }()
  static let tick: Path = {
    var p = Path(); p.move(to: .init(x: 5, y: 12.5)); p.addLine(to: .init(x: 9.5, y: 17)); p.addLine(to: .init(x: 19, y: 7.5)); return p
  }()
  static let arrow: Path = {
    var p = Path(); p.move(to: .init(x: 6, y: 18)); p.addLine(to: .init(x: 18, y: 6)); p.move(to: .init(x: 9, y: 6)); p.addLine(to: .init(x: 18, y: 6)); p.addLine(to: .init(x: 18, y: 15)); return p
  }()
  func foreground(_ c: GraphicsContext, _ f: M.Frame, _ reduced: Bool) {
    let q = M.smooth(f.s)
    var badge = c
    // RN transform arrays apply translation in parent space, then scale/rotation about the centre.
    badge.translateBy(x: 88+M.mix(0, size.width-152, q), y: top+60+M.mix(0, 395, q))
    badge.scaleBy(x: M.mix(1, 0.62, q), y: M.mix(1, 0.62, q))
    badge.rotate(by: .radians(f.spin+(reduced ? 0 : 2*Double.pi*f.s)))
    badge.fill(circle(0, 0, 59), with: .color(Self.color(0xc8f54a)))
    badge.draw(Image(uiImage: r.ring), in: CGRect(x: -64, y: -64, width: 128, height: 128))
    badge.fill(circle(0, 0, 31), with: .color(Self.color(0x1d1a2b)))
    badge.translateBy(x: -12, y: -12)
    if f.done { badge.stroke(Self.tick, with: .color(Self.color(0xc8f54a)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round)) }
    else { badge.fill(Self.bolt, with: .color(Self.color(0xc8f54a))) }

    let hy = top+368, px = 24+r.takeWidth
    label(c, "take", 24, hy+1)
    var pill = c
    pill.clip(to: rect(px, hy, r.pillWidth, 60, 32))
    label(pill, "control", px+16, hy+6)
    var wipe = pill
    wipe.clip(to: rect(px+2, hy+2, q*(r.pillWidth-4), 56))
    wipe.fill(rect(px+2, hy+2, q*(r.pillWidth-4), 56), with: .color(Self.color(0xc8f54a)))
    label(wipe, "controlInk", px+16, hy+5)
    c.stroke(rect(px+1, hy+1, r.pillWidth-2, 58, 31), with: .color(.white), lineWidth: 2)
    label(c, "of", 24, hy+60)
    let dotX = 24+r.ofWidth
    c.fill(circle(dotX+23, hy+89, 23), with: .color(Self.color(0xc8f54a)))
    var half = c
    half.clip(to: circle(dotX+23, hy+89, 23))
    half.fill(rect(dotX+23, hy+66, 23, 46), with: .color(Self.color(0x1d1a2b)))
    label(c, "your", dotX+46, hy+60)
    label(c, "schedule", 24, hy+118)

    let bw = size.width-40, by = size.height-bottom-84, range = bw-62
    c.fill(rect(20, by, bw, 62, 31), with: .color(Self.color(0xc8f54a)))
    c.fill(rect(20, by, 62+f.s*range, 62, 31), with: .color(Self.color(0xb4e036)))
    var start = c; start.opacity = 1-M.smooth(f.s/0.4)
    label(start, "start", size.width/2+f.s*30, by+(62-r.labels["start"]!.size.height)/2, true)
    var done = c; done.opacity = M.smooth((f.s-0.8)/0.2)
    label(done, "done", size.width/2, by+(62-r.labels["done"]!.size.height)/2, true)
    let kx = 51+f.s*range
    c.fill(circle(kx, by+31, 27), with: .color(Self.color(0x1d1a2b)))
    var icon = c; icon.translateBy(x: kx-12, y: by+19)
    icon.stroke(f.done ? Self.tick : Self.arrow, with: .color(.white), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
    if !reduced && f.since >= 0 && f.since <= 1.6 {
      let t = f.since
      let colors = [0xc8f54a, 0xffffff, 0xff8ad8, 0xffd84a, 0x8fd0ff]
      for i in 0..<46 {
        let h = Self.confetti[i]
        let a = -Double.pi/2+(h[0]-0.62)*2.2
        let v = 380+h[1]*520
        var conf = c
        conf.opacity = 1-M.smooth((t-1)/0.6)
        conf.translateBy(x: size.width-51+cos(a)*v*t*(1-t*0.25), y: by+31+sin(a)*v*t+900*t*t)
        conf.rotate(by: .degrees(t*720*(h[2]-0.5)+Double(i)*40))
        conf.scaleBy(x: 1, y: abs(cos(t*9+Double(i)))+0.15)
        conf.fill(rect(-4, -2.5, 8, 5), with: .color(Self.color(colors[i%5])))
      }
    }
  }
  static let confetti: [[Double]] = (0..<46).map { i in
    [78.233, 156.466, 234.699].map { seed in
      let value = sin(Double(i+1)*12.9898+seed)*43758.5453
      return value-floor(value)
    }
  }
}
