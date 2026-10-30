import CoreText
import SwiftUI

struct SetupChecklistAllSetArt {
  typealias C = SetupChecklistAllSetTheme.C
  typealias COPY = SetupChecklistAllSetTheme.COPY
  typealias UI = SetupChecklistAllSetTheme.UI
  typealias D = SetupChecklistAllSetMotion.D
  typealias Live = SetupChecklistAllSetMotion.Live
  typealias Surface = SetupChecklistAllSetDrawing.Surface
  typealias Path = SetupChecklistAllSetDrawing.Path
  typealias Paint = SetupChecklistAllSetDrawing.Paint
  typealias Gradient = SetupChecklistAllSetDrawing.Gradient
  struct Point {
    let x: Double
    let y: Double
    init(_ x: Double, _ y: Double) {
      self.x = x
      self.y = y
    }
  }
  typealias Font = SetupChecklistAllSetDrawing.Font
  let N = 6
  let NEVER = -100.0
  let REF_H = 874.0
  let ITEMS = SetupChecklistAllSetTheme.items
  static let fontsRegistered: Void = {
    for weight in ["Regular", "Medium", "SemiBold", "Bold", "ExtraBold"] {
      if let url = Bundle.main.url(
        forResource: "SetupChecklistAllSet-Inter-" + weight, withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  struct Res {
    struct Fonts {
      let handle = Font("ExtraBold", 33), url = Font("Regular", 16.5)
      let chip = Font("Medium", 15), pro = Font("SemiBold", 15)
      let title = Font("Medium", 15.5), badge = Font("SemiBold", 13.5)
      let row = Font("Medium", 14.8), desc = Font("Regular", 14.6)
      let action = Font("Medium", 14.4), allSet = Font("Bold", 21)
      let body = Font("Regular", 15.5), cta = Font("SemiBold", 16)
      let tab = Font("Medium", 10.5), small = Font("Medium", 12.5), ring = Font("SemiBold", 12)
    }
    let f: Fonts
    init() {
      _ = SetupChecklistAllSetArt.fontsRegistered
      f = Fonts()
    }
  }
  static let resources = Res()
  func draw(_ context: inout GraphicsContext, _ size: CGSize, _ live: Live) {
    context.withCGContext { cg in drawScene(Surface(cg), Self.resources, size.width / 402, live) }
  }
  static func col(_ hex: String, _ alpha: Double = 1) -> [Double] {
    let number = Int(hex.dropFirst(), radix: 16) ?? 0
    return [
      Double((number >> 16) & 255) / 255, Double((number >> 8) & 255) / 255,
      Double(number & 255) / 255, alpha,
    ]
  }
  func col(_ hex: String, _ alpha: Double = 1) -> [Double] { Self.col(hex, alpha) }
  func mixCol(_ first: String, _ second: String, _ amount: Double, _ alpha: Double = 1) -> [Double]
  {
    let a = col(first)
    let b = col(second)
    return [mix(a[0], b[0], amount), mix(a[1], b[1], amount), mix(a[2], b[2], amount), alpha]
  }
  func paint(_ hex: String, _ alpha: Double = 1) -> Paint {
    let p = Paint()
    p.setColor(col(hex, alpha))
    return p
  }
  func stroke(_ hex: String, _ width: Double, _ alpha: Double = 1) -> Paint {
    let p = paint(hex, alpha)
    p.lineWidth = width
    return p
  }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
    CGRect(x: x, y: y, width: w, height: h)
  }
  func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ resources: Double) -> CGPath {
    CGPath(
      roundedRect: rect(x, y, w, h), cornerWidth: resources, cornerHeight: resources, transform: nil
    )
  }
  @discardableResult func text(
    _ canvas: Surface, _ string: String, _ x: Double, _ cy: Double, _ font: Font, _ paint: Paint,
    _ anchor: Double = 0
  ) -> Double {
    let width = font.width(string)
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: string, attributes: [.font: font.face, .foregroundColor: Surface.color(paint.rgba)])
    )
    canvas.save()
    canvas.translate(x - width * anchor, cy + font.size * 0.36)
    canvas.scale(1, -1)
    canvas.cg.textMatrix = .identity
    canvas.cg.textPosition = .zero
    CTLineDraw(line, canvas.cg)
    canvas.restore()
    return width
  }
  @discardableResult func textFit(
    _ canvas: Surface, _ string: String, _ x: Double, _ cy: Double, _ font: Font, _ paint: Paint,
    _ maxWidth: Double, _ anchor: Double = 0
  ) -> Double {
    let width = font.width(string)
    let scale = min(1, maxWidth / width)
    canvas.save()
    canvas.translate(x, cy)
    canvas.scale(scale, scale)
    text(canvas, string, 0, 0, font, paint, anchor)
    canvas.restore()
    return width * scale
  }
  func numberString(_ number: Double) -> String { String(Int(number)) }
  func remainder(_ x: Double, _ y: Double) -> Double { x.truncatingRemainder(dividingBy: y) }
  func clamp01(_ x: Double) -> Double { SetupChecklistAllSetMotion.clamp01(x) }
  func mix(_ a: Double, _ b: Double, _ p: Double) -> Double {
    SetupChecklistAllSetMotion.mix(a, b, p)
  }
  func smooth(_ x: Double) -> Double { SetupChecklistAllSetMotion.smooth(x) }
  func easeOut(_ x: Double) -> Double { SetupChecklistAllSetMotion.easeOut(x) }
  func easeInOut(_ x: Double) -> Double { SetupChecklistAllSetMotion.easeInOut(x) }
  func spring(_ t: Double, _ response: Double, _ damping: Double) -> Double {
    SetupChecklistAllSetMotion.spring(t, response, damping)
  }
  func countRolled(_ s: SetupChecklistAllSetMotion.State, _ now: Double) -> Double {
    SetupChecklistAllSetMotion.countRolled(s, now)
  }
  func openness(_ s: SetupChecklistAllSetMotion.State, _ i: Int, _ now: Double) -> Double {
    SetupChecklistAllSetMotion.openness(s, i, now)
  }
  func doneness(_ s: SetupChecklistAllSetMotion.State, _ i: Int, _ now: Double) -> Double {
    SetupChecklistAllSetMotion.doneness(s, i, now)
  }

  // ------------------------------------------------------------------ the page behind

  func drawPage(_ canvas: Surface, _ resources: Res) {
    let page = UI.page.self
    canvas.drawColor(col(C.page))
    // avatar
    canvas.drawCircle(page.avatar.x, page.avatar.y, page.avatar.r, paint("#C7C7C3"))
    canvas.save()
    canvas.clipRRect(
      rrect(
        page.avatar.x - page.avatar.r, page.avatar.y - page.avatar.r, page.avatar.r * 2,
        page.avatar.r * 2, page.avatar.r
      ))
    canvas.drawCircle(page.avatar.x, page.avatar.y - 4, 8, paint("#EDEDEA"))
    canvas.drawOval(rect(page.avatar.x - 15, page.avatar.y + 8, 30, 22), paint("#EDEDEA"))
    canvas.restore()
    let pr = page.pro
    canvas.drawRRect(rrect(pr.x, pr.y, pr.w, pr.h, 12), stroke(C.pro, 1.5))
    text(canvas, COPY.pro, pr.x + pr.w / 2, pr.y + pr.h / 2, resources.f.pro, paint(C.pro), 0.5)
    // share and bell
    let sh = stroke(C.ink, 1.8)
    canvas.drawLine(page.share.x, page.share.y - 12, page.share.x, page.share.y + 3, sh)
    canvas.drawLine(page.share.x - 5, page.share.y - 7, page.share.x, page.share.y - 12, sh)
    canvas.drawLine(page.share.x + 5, page.share.y - 7, page.share.x, page.share.y - 12, sh)
    let box = Path()
    box.moveTo(page.share.x - 5, page.share.y - 4)
    box.lineTo(page.share.x - 10, page.share.y - 4)
    box.lineTo(page.share.x - 10, page.share.y + 12)
    box.lineTo(page.share.x + 10, page.share.y + 12)
    box.lineTo(page.share.x + 10, page.share.y - 4)
    box.lineTo(page.share.x + 5, page.share.y - 4)
    canvas.drawPath(box, sh)
    let bell = Path()
    bell.moveTo(page.bell.x - 10, page.bell.y + 7)
    bell.lineTo(page.bell.x - 8, page.bell.y + 3)
    bell.cubicTo(
      page.bell.x - 8, page.bell.y - 13, page.bell.x + 8, page.bell.y - 13, page.bell.x + 8,
      page.bell.y + 3)
    bell.lineTo(page.bell.x + 10, page.bell.y + 7)
    bell.close()
    canvas.drawPath(bell, sh)
    canvas.drawLine(page.bell.x - 3, page.bell.y + 11, page.bell.x + 3, page.bell.y + 11, sh)
    text(canvas, COPY.handle, page.handle.x, page.handle.y, resources.f.handle, paint(C.ink))
    let uw = text(canvas, COPY.url, page.url.x, page.url.y, resources.f.url, paint(C.ink2))
    canvas.drawRRect(rrect(page.url.x + uw + 6, page.url.y - 5, 8, 9, 1.5), stroke(C.ink2, 1.2))
    canvas.drawRRect(rrect(page.url.x + uw + 9, page.url.y - 8, 8, 9, 1.5), stroke(C.ink2, 1.2))
    for i in 0..<3 {
      let x = page.chips.x[i]
      let w = page.chips.w[i]
      canvas.drawRRect(
        rrect(x, page.chips.y - page.chips.h / 2, w, page.chips.h, page.chips.h / 2), paint(C.chip))
      let ic = stroke(C.ink, 1.4)
      let ix = x + 22
      let iy = page.chips.y
      let g = Path()
      if i == 0 {
        // a T-shirt
        g.moveTo(ix - 4, iy - 7)
        g.quadTo(ix, iy - 4, ix + 4, iy - 7)
        g.lineTo(ix + 9, iy - 4)
        g.lineTo(ix + 7, iy)
        g.lineTo(ix + 5, iy - 1)
        g.lineTo(ix + 5, iy + 7)
        g.lineTo(ix - 5, iy + 7)
        g.lineTo(ix - 5, iy - 1)
        g.lineTo(ix - 7, iy)
        g.lineTo(ix - 9, iy - 4)
        g.close()
      } else if i == 1 {
        // a paint roller
        g.addRRect(rrect(ix - 8, iy - 7, 16, 7, 2))
        g.moveTo(ix + 8, iy - 4)
        g.lineTo(ix + 10, iy - 4)
        g.lineTo(ix + 10, iy + 1)
        g.lineTo(ix, iy + 2)
        g.lineTo(ix, iy + 8)
      } else {
        // a gear
        for k in 0..<8 {
          let a0 = (Double(k) * (Double.pi * 2)) / 8
          g.moveTo(ix + cos(a0) * 5.5, iy + sin(a0) * 5.5)
          g.lineTo(ix + cos(a0) * 8, iy + sin(a0) * 8)
        }
        g.addCircle(ix, iy, 5.5)
        g.addCircle(ix, iy, 2.2)
      }
      canvas.drawPath(g, ic)
      text(canvas, COPY.chips[i], x + 37, page.chips.y, resources.f.chip, paint(C.ink))
    }
    // the cards, mostly under the sheet
    let cy = page.cards.y
    canvas.drawRRect(rrect(17, cy, 169, page.cards.h, 14), paint("#E8E8E5"))
    canvas.drawRRect(rrect(17, cy, 169, page.cards.h, 14), stroke("#D2D2CE", 1))
    canvas.drawCircle(101, cy + 64, 20, paint("#B9B9B5"))
    text(canvas, "@" + COPY.handle, 101, cy + 104, resources.f.small, paint(C.ink), 0.5)
    canvas.drawRRect(rrect(54, cy + 146, 94, 22, 11), paint("#FFFFFF"))
    canvas.drawRRect(rrect(196, cy, 190, page.cards.h, 14), paint("#E1E1DE"))
    canvas.drawCircle(291, cy + 72, 34, paint("#C48BF0"))
    canvas.drawCircle(291, cy + 72, 18, paint("#FFFFFF", 0.85))
    text(canvas, "Set up your Shop", 291, cy + 132, resources.f.chip, paint(C.ink), 0.5)
    text(canvas, "Links", 17, cy + page.cards.h + 18, resources.f.chip, paint(C.ink))
    text(canvas, "Shop", 196, cy + page.cards.h + 18, resources.f.chip, paint(C.ink))
    canvas.drawRRect(rrect(330, cy + page.cards.h + 6, 56, 26, 13), paint(C.chip))
    text(canvas, "Set up", 358, cy + page.cards.h + 19, resources.f.small, paint(C.ink), 0.5)
    // the setup ring
    let rg = page.ring
    canvas.drawCircle(rg.x, rg.y, rg.r + 9, paint("#FFFFFF"))
    canvas.drawCircle(rg.x, rg.y, rg.r + 9, stroke("#C9B6F7", 2))
    canvas.drawCircle(rg.x, rg.y, rg.r, stroke("#EDE7FB", 5))
    let arc = Path()
    arc.addArc(rect(rg.x - rg.r, rg.y - rg.r, rg.r * 2, rg.r * 2), -90, 360 / 6)
    canvas.drawPath(arc, stroke(C.barFrom, 5))
    text(canvas, COPY.ring[0], rg.x, rg.y - 7, resources.f.ring, paint(C.ink), 0.5)
    text(canvas, COPY.ring[1], rg.x, rg.y + 8, resources.f.ring, paint(C.ink), 0.5)
    // tab bar
    canvas.drawRect(rect(0, 784, 402, 90), paint("#FFFFFF"))
    canvas.drawLine(0, 784, 402, 784, stroke("#E3E3E0", 1))
    let tabs = ["My Tapline", "Earn", "Insights", "Audience", "More"]
    for i in 0..<5 {
      let x = 40 + Double(i) * 80.5
      let on = i == 0
      canvas.drawRRect(rrect(x - 10, 798, 20, 18, 5), stroke(on ? C.badge : C.grey, 1.6))
      text(canvas, tabs[i], x, 832, resources.f.tab, paint(on ? C.badge : C.grey), 0.5)
    }
  }

  // ------------------------------------------------------------------ the checklist

  func drawCheck(_ canvas: Surface, _ x: Double, _ y: Double, _ rad: Double, _ p: Double) {
    // the empty ring
    canvas.drawCircle(x, y, rad, stroke(C.ring, 1.6, 1 - smooth(p * 3)))
    if p <= 0 { return }
    // a soft green ring pulses out as it is ticked
    if p > 0.15 && p < 0.9 {
      let q = (p - 0.15) / 0.75
      canvas.drawCircle(
        x, y, rad * (1 + 0.9 * easeOut(q)), stroke(C.green, 2 * (1 - q), 0.5 * (1 - q)))
    }
    let fill = spring(p * D.tick, 0.32, 0.55)
    let s = mix(0.4, 1, fill)
    canvas.drawCircle(x, y, rad * s, paint(C.green, min(1, p * 4)))
    // the tick, drawn
    let k = clamp01((p - 0.25) / 0.45)
    if k > 0 {
      let a = Point(x - rad * 0.42, y + rad * 0.02)
      let b = Point(x - rad * 0.1, y + rad * 0.34)
      let e = Point(x + rad * 0.46, y - rad * 0.3)
      let l1 = hypot(b.x - a.x, b.y - a.y)
      let l2 = hypot(e.x - b.x, e.y - b.y)
      let d = easeOut(k) * (l1 + l2)
      let tk = Path()
      tk.moveTo(a.x, a.y)
      if d <= l1 {
        tk.lineTo(mix(a.x, b.x, d / l1), mix(a.y, b.y, d / l1))
      } else {
        tk.lineTo(b.x, b.y)
        let q = (d - l1) / l2
        tk.lineTo(mix(b.x, e.x, q), mix(b.y, e.y, q))
      }
      canvas.drawPath(tk, stroke("#FFFFFF", 2.3))
    }
  }

  func chevron(_ canvas: Surface, _ x: Double, _ y: Double, _ up: Double, _ a: Double) {
    canvas.save()
    canvas.translate(x, y)
    canvas.rotate(180 * up)
    let p = Path()
    p.moveTo(-6.5, -3)
    p.lineTo(0, 3.5)
    p.lineTo(6.5, -3)
    canvas.drawPath(p, stroke(C.ink, 1.5, a))
    canvas.restore()
  }

  func drawChecklist(_ canvas: Surface, _ resources: Res, _ live: Live, _ fade: Double) {
    let s = live.s
    let now = live.now
    let a = 1 - fade
    let count = countRolled(s, now)
    // header
    let tw = text(canvas, COPY.title, UI.title.x, UI.title.y, resources.f.title, paint(C.ink, a))
    let badge = UI.badge.self
    let bx = UI.title.x + tw + 8
    let changed = s.doneAt.max() ?? NEVER
    let pq = clamp01((now - changed - 0.06) / 0.32)
    let pop = changed > NEVER && pq > 0 && pq < 1 ? sin(.pi * pq) * (1 - pq * 0.4) : 0
    canvas.save()
    canvas.translate(bx + badge.w / 2, badge.y + badge.h / 2)
    canvas.scale(1 + 0.15 * pop, 1 + 0.15 * pop)
    canvas.translate(-(bx + badge.w / 2), -(badge.y + badge.h / 2))
    canvas.drawRRect(rrect(bx, badge.y, badge.w, badge.h, badge.h / 2), paint(C.badge, a))
    // the count rolls
    let n0 = floor(count + 1e-4)
    let k = smooth(count - n0)
    canvas.save()
    canvas.clipRect(rect(bx, badge.y, badge.w, badge.h))
    let wp = paint("#FFFFFF", a)
    let slash = "/6"
    let sw = resources.f.badge.width(slash)
    let dw = resources.f.badge.width(numberString(n0))
    let x0 = bx + badge.w / 2 - (dw + sw) / 2
    text(canvas, numberString(n0), x0, badge.y + badge.h / 2 - k * 14, resources.f.badge, wp)
    if k > 0.001 {
      text(
        canvas, numberString(min(Double(N), n0 + 1)), x0, badge.y + badge.h / 2 + (1 - k) * 14,
        resources.f.badge, wp)
    }
    text(canvas, slash, x0 + dw, badge.y + badge.h / 2, resources.f.badge, wp)
    canvas.restore()
    canvas.restore()
    // close
    let close = UI.close.self
    canvas.drawCircle(close.x, close.y, close.r, paint(C.close, a))
    canvas.drawLine(close.x - 5, close.y - 5, close.x + 5, close.y + 5, stroke(C.ink, 1.7, a))
    canvas.drawLine(close.x + 5, close.y - 5, close.x - 5, close.y + 5, stroke(C.ink, 1.7, a))
    // the bar: its fill follows the count on a spring; a gloss runs along it after each change
    let bar = UI.bar.self
    canvas.drawRRect(rrect(bar.x, bar.y, bar.w, bar.h, bar.h / 2), paint(C.track, a))
    var last = NEVER
    for i in 0..<N { if s.doneAt[i] > last { last = s.doneAt[i] } }
    let fw = max(bar.h, (bar.w * count) / Double(N))
    let bp = Paint()
    bp.setAntiAlias(true)
    bp.setAlphaf(a)
    bp.gradient = Gradient(bar.x, bar.x + fw, [C.barFrom, C.barTo], [1, 1])
    canvas.drawRRect(rrect(bar.x, bar.y, fw, bar.h, bar.h / 2), bp)
    let gl = now - last
    if last > NEVER && gl > 0.1 && gl < 0.9 {
      let gx = bar.x + fw * easeInOut((gl - 0.1) / 0.8)
      canvas.save()
      canvas.clipRRect(rrect(bar.x, bar.y, fw, bar.h, bar.h / 2))
      let gp = Paint()
      gp.gradient = Gradient(
        gx - 40, gx + 10, ["#FFFFFF", "#FFFFFF", "#FFFFFF"], [0, 0.55 * a, 0])
      canvas.drawRect(rect(gx - 40, bar.y, 50, bar.h), gp)
      canvas.restore()
    }
    // rows
    let rows = UI.rows.self
    var y = rows.y0
    for i in 0..<N {
      let e = openness(s, i, now)
      let h = rows.h + rows.open * e
      let cy = y + rows.h / 2
      let dn = doneness(s, i, now)
      drawCheck(canvas, rows.check.x, cy, rows.check.r, dn)
      let ink = Paint()
      ink.setAntiAlias(true)
      let strikeK = clamp01((dn - 0.45) / 0.55)
      ink.setColor(mixCol(C.ink, C.mute, smooth(dn * 1.4), a))
      let tw2 = text(canvas, ITEMS[i].title, rows.textX, cy, resources.f.row, ink)
      if strikeK > 0 {
        canvas.drawLine(
          rows.textX, cy + 1, rows.textX + tw2 * easeOut(strikeK), cy + 1, stroke(C.mute, 1.4, a))
      }
      // done rows lose their chevron
      chevron(canvas, rows.chevronX, cy, e, a * (1 - smooth(dn * 2)))
      if e > 0.005 {
        canvas.save()
        canvas.clipRect(rect(0, y + rows.h - 6, 402, h - rows.h + 6))
        let ca = a * smooth((e - 0.15) / 0.85)
        let lift = (1 - e) * -12
        for l in 0..<2 {
          textFit(
            canvas, ITEMS[i].body[l], rows.textX, cy + UI.desc.dy[l] + lift, resources.f.desc,
            paint(C.ink2, ca),
            332)
        }
        let layout = UI.action.self
        let aw = resources.f.action.width(ITEMS[i].action) + layout.padX * 2
        let press = s.pressRow == i ? max(0, 1 - abs(now - s.pressAt - 0.08) / 0.12) : 0
        let ps = 1 - 0.04 * press
        canvas.save()
        canvas.translate(rows.textX + aw / 2, cy + layout.dy + lift)
        canvas.scale(ps, ps)
        canvas.drawRRect(rrect(-aw / 2, -layout.h / 2, aw, layout.h, 12), paint(C.purple, ca))
        text(canvas, ITEMS[i].action, 0, 0, resources.f.action, paint("#FFFFFF", ca), 0.5)
        canvas.restore()
        text(
          canvas, COPY.skip, rows.textX + aw + UI.skipDx, cy + layout.dy + lift, resources.f.action,
          paint(C.ink, ca))
        canvas.restore()
      }
      if i < N - 1 { canvas.drawLine(16.5, y + h, 385.5, y + h, stroke(C.divider, 1, a)) }
      y += h
    }
  }

  // ------------------------------------------------------------------ all set

  func drawPopper(_ canvas: Surface, _ x: Double, _ y: Double, _ k: Double, _ amb: Double) {
    if k <= 0 { return }
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(k, k)
    // the cone, pointing down-left
    let cone = Path()
    cone.moveTo(-15, 15)
    cone.lineTo(-3, -11)
    cone.lineTo(11, 3)
    cone.close()
    canvas.drawPath(cone, paint(C.popper))
    canvas.drawLine(-9, 2, 1, 12, stroke("#FFFFFF", 1.6, 0.8))
    // sparkles above it, twinkling
    let tw = { (i: Double) in 1 + 0.18 * sin(((Double.pi * 2) * 2 * amb) / 9 + i * 2) }
    func plus(_ px: Double, _ py: Double, _ rr: Double) {
      canvas.drawLine(px - rr, py, px + rr, py, stroke(C.popper, 1.8))
      canvas.drawLine(px, py - rr, px, py + rr, stroke(C.popper, 1.8))
    }
    plus(1, -17, 3.6 * tw(0))
    plus(15, -13, 4.4 * tw(1))
    plus(18, 4, 3.4 * tw(2))
    canvas.drawCircle(9, -3, 1.8, paint(C.popper))
    canvas.drawCircle(-6, -20, 1.5, paint(C.popper))
    canvas.restore()
  }

  /** Confetti from the popper: a fixed scatter, `tau` seconds after it fired. */
  func drawConfetti(_ canvas: Surface, _ x: Double, _ y: Double, _ tau: Double, _ seed: Double) {
    if tau < 0 || tau > 2.2 { return }
    for i in 0..<46 {
      let h1 = sin(Double(i + 1) * 12.9898 + seed * 78.233) * 43758.5453
      let r1 = h1 - floor(h1)
      let h2 = sin(Double(i + 1) * 39.3468 + seed * 11.135) * 24634.6345
      let r2 = h2 - floor(h2)
      let h3 = sin(Double(i + 1) * 73.156 + seed * 52.235) * 12345.6789
      let r3 = h3 - floor(h3)
      let ang = -Double.pi / 2 + (r1 - 0.45) * 2.6
      let v = 260 + 260 * r2
      let drag = 1 - exp(-tau * 2.6)
      let px = x + (cos(ang) * v * drag) / 2.6
      let py = y + (sin(ang) * v * drag) / 2.6 + 140 * tau * tau
      let fade = 1 - smooth((tau - 1.2) / 1)
      let cc = C.confetti[i % C.confetti.count]
      canvas.save()
      canvas.translate(px, py)
      canvas.rotate(remainder(r3 * 360 + tau * 520 * (r1 - 0.5), 360))
      let flutter = abs(cos(tau * (6 + 8 * r3) + r2 * 6))
      if i % 3 == 0 {
        canvas.drawCircle(0, 0, 2.6, paint(cc, fade))
      } else {
        canvas.drawRect(rect(-3.5, -1.8 * flutter - 0.4, 7, 3.6 * flutter + 0.8), paint(cc, fade))
      }
      canvas.restore()
    }
  }

  func drawAllSet(_ canvas: Surface, _ resources: Res, _ live: Live, _ a: Double) {
    let layout = UI.allSet.self
    let icon = layout.icon
    let now = live.now
    let s = live.s
    let m0 = D.morph
    let m1 = D.morph + D.bloom
    let tx = { (i: Double) in smooth((a - m0 - 0.3 - i * 0.07) / 0.35) }
    // the white sheet grows out of the bubble
    let g = a < m0 ? 0 : spring(a - m0, 0.5, 0.78)
    let top = mix(icon.y - icon.r, layout.y, g)
    let left = mix(icon.x - icon.r, 0, g)
    let right = mix(icon.x + icon.r, 402, g)
    let bottom = mix(icon.y + icon.r, REF_H + 60, g)
    let rad = mix(icon.r, 34, clamp01(g))
    if a >= m0 {
      canvas.drawRRect(rrect(left, top, right - left, bottom - top, rad), paint(C.sheet))
    }
    // the bubble: during the morph it is the shrinking sheet itself (see drawScene); after, the icon
    if a >= m0 {
      let settle = spring(a - m0, 0.38, 0.55)
      canvas.drawCircle(icon.x, icon.y, icon.r * mix(1.08, 1, settle), paint(C.lilac))
      drawPopper(canvas, icon.x, icon.y, spring(a - m0 - 0.12, 0.42, 0.5), live.amb)
    }
    drawConfetti(canvas, icon.x + 10, icon.y - 14, a - m0 - 0.2, 1)
    if s.ctaAt > NEVER { drawConfetti(canvas, 201, UI.allSet.cta.y, now - s.ctaAt, 2) }
    if a < m1 - 0.3 { return }
    let t0 = tx(0)
    text(
      canvas, COPY.allSet, 201, layout.title + 8 * (1 - t0), resources.f.allSet, paint(C.ink, t0),
      0.5)
    for l in 0..<3 {
      let k = tx(1 + Double(l) * 0.4)
      text(
        canvas, COPY.allSetBody[l], 201, layout.body[l] + 8 * (1 - k), resources.f.body,
        paint(C.grey, k), 0.5)
    }
    let kc = tx(3)
    let cp = s.ctaAt > NEVER ? max(0, 1 - abs(now - s.ctaAt - 0.08) / 0.14) : 0
    let cs = 1 - 0.04 * cp
    let cta = layout.cta
    canvas.save()
    canvas.translate(cta.x + cta.w / 2, cta.y + cta.h / 2 + 10 * (1 - kc))
    canvas.scale(cs, cs)
    canvas.drawRRect(rrect(-cta.w / 2, -cta.h / 2, cta.w, cta.h, cta.h / 2), paint(C.cta, kc))
    text(canvas, COPY.cta, 0, 0, resources.f.cta, paint("#FFFFFF", kc), 0.5)
    canvas.restore()
    let kl = tx(3.6)
    let lp = s.laterAt > NEVER ? max(0, 1 - abs(now - s.laterAt - 0.1) / 0.18) : 0
    if lp > 0 {
      canvas.drawRRect(rrect(150, layout.later - 18, 102, 36, 18), paint("#000000", 0.06 * lp))
    }
    text(
      canvas, COPY.later, 201, layout.later + 8 * (1 - kl), resources.f.cta, paint(C.ink, kl), 0.5)
  }

  func drawScene(_ canvas: Surface, _ resources: Res, _ scale: Double, _ live: Live) {
    let s = live.s
    let now = live.now
    canvas.save()
    canvas.scale(scale, scale)
    drawPage(canvas, resources)
    // how far the sheet is down (dismissed) and which sheet it is
    var down = 0.0
    if s.leaveAt > NEVER && now >= s.leaveAt {
      if s.backAt > NEVER && now >= s.backAt {
        down = 1 - easeOut((now - s.backAt) / D.enter)
      } else {
        down = easeInOut((now - s.leaveAt) / D.leave)
      }
    }
    let allSet = s.allSetAt > NEVER && now >= s.allSetAt && !(s.backAt > NEVER && now >= s.backAt)
    canvas.drawRect(rect(0, 0, 402, REF_H), paint(C.dim, 0.5 * (1 - down)))
    let off = down * (REF_H - UI.allSet.y + 40)
    canvas.save()
    canvas.translate(0, off)
    if !allSet {
      canvas.drawRRect(
        rrect(0, UI.sheet.y, 402, REF_H - UI.sheet.y + 80, UI.sheet.r), paint(C.sheet))
      drawChecklist(canvas, resources, live, 0)
    } else {
      let a = now - s.allSetAt
      if a < D.morph {
        // the list fades and shrinks first; the sheet folds into a bubble at the icon
        let icon = UI.allSet.icon.self
        let fade = smooth(a / 0.22)
        let m = easeInOut(a / D.morph)
        let top = mix(UI.sheet.y, icon.y - icon.r, m)
        let left = mix(0, icon.x - icon.r, m)
        let right = mix(402, icon.x + icon.r, m)
        let bottom = mix(REF_H + 80, icon.y + icon.r, m)
        let rad = mix(UI.sheet.r, icon.r, m)
        let sc = Paint()
        sc.setAntiAlias(true)
        sc.setColor(mixCol(C.sheet, C.lilac, smooth((m - 0.4) / 0.6)))
        canvas.drawRRect(rrect(left, top, right - left, bottom - top, rad), sc)
        if fade < 1 {
          canvas.save()
          canvas.clipRRect(rrect(left, top, right - left, bottom - top, rad))
          canvas.translate(icon.x, icon.y)
          canvas.scale(1 - 0.08 * fade, 1 - 0.08 * fade)
          canvas.translate(-icon.x, -icon.y)
          drawChecklist(canvas, resources, live, fade)
          canvas.restore()
        }
      } else {
        drawAllSet(canvas, resources, live, a)
      }
    }
    canvas.restore()
    canvas.restore()
  }
}
