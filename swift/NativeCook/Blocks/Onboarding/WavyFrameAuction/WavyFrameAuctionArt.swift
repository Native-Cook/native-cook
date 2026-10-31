import SwiftUI

struct WavyFrameAuctionArt {
  typealias M = WavyFrameAuctionMotion
  static func color(_ hex: Int, _ a: Double = 1) -> Color { Color(uiColor: WavyFrameAuctionResources.color(hex, a)) }
  let size: CGSize
  let top: Double
  let bottom: Double
  func draw(_ context: GraphicsContext, _ s: Double, _ ph: Double, _ lift: Double, _ seconds: Int) {
    let resource = WavyFrameAuctionResources.shared
    let (f, r) = M.frame(s, size, top)
    let amp = M.mix(9, 0, M.smooth(s * 1.25))
    var c = context
    c.translateBy(x: f.midX, y: f.midY)
    c.rotate(by: .degrees(-2.2 * lift)); c.scaleBy(x: 1 + 0.025 * lift, y: 1 + 0.025 * lift)
    c.translateBy(x: -f.midX, y: -f.midY)
    if lift > 0.01 {
      var shadow = c
      shadow.addFilter(.blur(radius: 22))
      shadow.fill(Path(roundedRect: f.offsetBy(dx: 0, dy: 18 * lift), cornerRadius: 30), with: .color(.black.opacity(0.55 * lift)))
    }
    let outer = M.wavy(f.insetBy(dx: -12, dy: -12), r + 10, amp * 1.15, ph * 2)
    c.fill(outer, with: .color(Self.color(0x2b2f29)))
    c.stroke(outer, with: .color(Self.color(0x4e515c)), lineWidth: 1.5)
    let inner = M.wavy(f, r, amp, ph * 2 + 0.6)
    c.clip(to: inner)
    c.fill(Path(f.insetBy(dx: -20, dy: -20)), with: .shader(ShaderLibrary.wavyFrameAuctionArt(.float2(f.midX, f.midY), .float2(f.width, f.height), .float(ph), .float(s))))
    let (tab, ty) = M.tab(size, bottom, s, ph)
    context.fill(tab, with: .color(Self.color(0xc8f560)))
    let ch = 1 - M.smooth(s * 2.5)
    if ch > 0.01 {
      let bob = sin(ph * 4) * 2
      for j in 0..<2 {
        let yy = ty + 28 + Double(j) * 10 + bob
        var p = Path()
        p.move(to: CGPoint(x: size.width / 2 - 14, y: yy + 6))
        p.addLine(to: CGPoint(x: size.width / 2, y: yy))
        p.addLine(to: CGPoint(x: size.width / 2 + 14, y: yy + 6))
        context.stroke(p, with: .color(Self.color(0x21251f, ch)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
      }
    }
    let intro = 1 - M.smooth(s * 2.2)
    if intro > 0 {
      var text = context; text.opacity = intro
      text.draw(Image(uiImage: resource.intro), at: CGPoint(x: size.width / 2, y: top + 430 + (1 - intro) * 24), anchor: .top)
    }
    let detail = M.smooth((s - 0.55) / 0.45)
    if detail > 0 {
      var text = context; text.opacity = detail
      text.draw(Image(uiImage: resource.details(seconds, size.width - 44)), at: CGPoint(x: 22, y: top + 64 + size.height * 0.56 + 22 + (1 - detail) * 20), anchor: .topLeading)
    }
    let bid = M.smooth((s - 0.7) / 0.3)
    if bid > 0 {
      var text = context; text.opacity = bid
      let label = Text("Place a Bid").font(.custom(resource.bid.fontName, fixedSize: 17)).foregroundColor(Self.color(0x21251f))
      text.draw(label, at: CGPoint(x: size.width / 2, y: size.height - bottom - 40), anchor: .bottom)
    }
  }
}
