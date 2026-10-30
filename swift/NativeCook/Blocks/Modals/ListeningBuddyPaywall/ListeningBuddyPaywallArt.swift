import SwiftUI

// Value-for-value drawing port of lbp-draw.ts. Coordinates below are the source pin's pixels.
struct ListeningBuddyPaywallArt {
  typealias M = ListeningBuddyPaywallMotion
  typealias Theme = ListeningBuddyPaywallTheme
  typealias C = Theme.C
  typealias UI = Theme.UI
  let TAU = Double.pi * 2
  let BEAT = M.BEAT
  let LOOP = M.LOOP
  let PIN_W = Theme.PIN_W
  let DY = Theme.DY
  let S = Theme.S
  struct Geo {
    let w: Double
    let h: Double
    let s: Double
  }
  struct Res {
    struct Fonts {
      let title = ListeningBuddyPaywallFont("Nunito-ExtraBold", 46)
      let sub = ListeningBuddyPaywallFont("Manrope_500Medium", 26)
      let name = ListeningBuddyPaywallFont("Manrope_700Bold", 22)
      let price = ListeningBuddyPaywallFont("Nunito-ExtraBold", 44)
      let per = ListeningBuddyPaywallFont("Manrope_500Medium", 19)
      let popular = ListeningBuddyPaywallFont("Manrope_700Bold", 19)
      let line = ListeningBuddyPaywallFont("Manrope_500Medium", 22)
      let link = ListeningBuddyPaywallFont("Manrope_500Medium", 22)
      let cta = ListeningBuddyPaywallFont("Manrope_700Bold", 26)
    }
    let f = Fonts()
  }
  static let resources = Res()
  func clamp01(_ x: Double) -> Double { M.clamp01(x) }
  func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { M.mix(a, b, k) }
  func smooth(_ x: Double) -> Double { M.smooth(x) }
  func easeOut(_ x: Double) -> Double { M.easeOut(x) }
  func spring(_ t: Double, _ response: Double, _ damping: Double) -> Double {
    M.spring(t, response, damping)
  }
  func bump(_ t: Double, _ at: Double, _ dur: Double) -> Double { M.bump(t, at, dur) }
  func paint(_ hex: Int, _ a: Double = 1) -> ListeningBuddyPaywallPaint {
    ListeningBuddyPaywallPaint(hex, a)
  }
  func stroke(_ hex: Int, _ w: Double, _ a: Double = 1) -> ListeningBuddyPaywallPaint {
    let p = paint(hex, a)
    p.width = w
    return p
  }
  func mixCol(_ a: Int, _ b: Int, _ k: Double, _ alpha: Double = 1) -> Color {
    func channel(_ hex: Int, _ shift: Int) -> Double { Double((hex >> shift) & 255) / 255 }
    return Color(
      .sRGB, red: mix(channel(a, 16), channel(b, 16), k),
      green: mix(channel(a, 8), channel(b, 8), k), blue: mix(channel(a, 0), channel(b, 0), k),
      opacity: alpha)
  }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
    CGRect(x: x, y: y, width: w, height: h)
  }
  func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double)
    -> ListeningBuddyPaywallPath
  {
    ListeningBuddyPaywallPath(
      CGPath(roundedRect: rect(x, y, w, h), cornerWidth: r, cornerHeight: r, transform: nil))
  }
  // Text is cached as glyph paths in its bundled font; y uses Skia's size * 0.36 baseline.
  @discardableResult func text(
    _ c: ListeningBuddyPaywallCanvas, _ s: String, _ x: Double, _ cy: Double,
    _ font: ListeningBuddyPaywallFont, _ p: ListeningBuddyPaywallPaint, _ ax: Double = 0,
    _ track: Double = 0
  ) -> Double {
    if track == 0 {
      let w = font.measureText(s).width
      c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font)
      return w
    }
    let glyphs = s.map(String.init)
    let w =
      glyphs.reduce(0) { $0 + font.measureText($1).width } + Double(max(0, glyphs.count - 1))
      * track
    var px = x - w * ax
    for glyph in glyphs {
      c.drawText(glyph, px, cy + font.getSize() * 0.36, p, font)
      px += font.measureText(glyph).width + track
    }
    return w
  }
  func note(
    _ c: ListeningBuddyPaywallCanvas, _ x: Double, _ y: Double, _ s: Double, _ color: Int,
    _ a: Double, _ double: Bool
  ) {
    c.save()
    c.translate(x, y)
    c.scale(s, s)
    c.rotate(-18, 0, 0)
    c.drawOval(rect(-11, -7, 22, 15), paint(color, a))
    c.restore()
    c.save()
    c.translate(x, y)
    c.scale(s, s)
    c.drawLine(9, -2, 9, -40, stroke(color, 3.4, a))
    if double {
      c.drawOval(rect(18, -12, 22, 15), paint(color, a))
      c.drawLine(38, -6, 38, -44, stroke(color, 3.4, a))
      c.drawLine(9, -40, 38, -44, stroke(color, 6, a))
    } else {
      let fl = ListeningBuddyPaywallPath()
      fl.moveTo(9, -40)
      fl.cubicTo(22, -34, 24, -24, 18, -16)
      c.drawPath(fl, stroke(color, 3.4, a))
    }
    c.restore()
  }

  // ------------------------------------------------------------------ the scene

  func drawSky(_ c: ListeningBuddyPaywallCanvas, _ f: M.Frame, _ top: Double) {
    c.drawRect(rect(-10, top, PIN_W + 20, UI.white - top), paint(C.sky))
    let drift = 26 * sin((TAU * f.amb) / LOOP)
    func cloud(_ x: Double, _ y: Double, _ s: Double) {
      let p = ListeningBuddyPaywallPath()
      p.moveTo(x - 140 * s, y)
      p.cubicTo(x - 140 * s, y - 40 * s, x - 90 * s, y - 50 * s, x - 70 * s, y - 40 * s)
      p.cubicTo(x - 60 * s, y - 95 * s, x + 30 * s, y - 100 * s, x + 40 * s, y - 50 * s)
      p.cubicTo(x + 80 * s, y - 70 * s, x + 130 * s, y - 40 * s, x + 130 * s, y)
      p.close()
      c.drawPath(p, paint(C.cloud))
    }
    cloud(140 + drift, 155, 1.05)
    cloud(640 - drift * 0.6, 238, 0.9)
    // the hills behind the buddy
    let h1 = ListeningBuddyPaywallPath()
    h1.moveTo(-10, 470)
    h1.cubicTo(140, 380, 300, 420, 420, 480)
    h1.cubicTo(520, 530, 600, 420, 746, 440)
    h1.lineTo(746, UI.white + 2)
    h1.lineTo(-10, UI.white + 2)
    h1.close()
    c.drawPath(h1, paint(C.hillB))
    let h2 = ListeningBuddyPaywallPath()
    h2.moveTo(330, UI.white + 2)
    h2.cubicTo(420, 560, 470, 520, 540, 500)
    h2.cubicTo(620, 470, 690, 440, 746, 470)
    h2.lineTo(746, UI.white + 2)
    h2.close()
    c.drawPath(h2, paint(C.hillA))
  }

  func drawBuddy(_ c: ListeningBuddyPaywallCanvas, _ f: M.Frame) {
    // the face blends from the last plan's to this one's on a spring
    let k = spring(f.selAt, 0.45, 0.62)
    let A = M.faces[f.from]
    let B = M.faces[f.plan]
    let eyes = mix(A.eyes, B.eyes, k)
    let smile = mix(A.smile, B.smile, k)
    let open = mix(A.open, B.open, k)
    let blush = mix(A.blush, B.blush, k)
    // the beat: a sway and a nod a happy hop on Continue or a poke
    let beat = f.amb.truncatingRemainder(dividingBy: BEAT) / BEAT
    let nod = pow(sin(Double.pi * beat), 2)
    let sway = 2.2 * sin((TAU * f.amb) / (BEAT * 2))
    let hop = max(
      f.go >= 0 ? 34 * bump(f.go, 0, 0.5) + 16 * bump(f.go, 0.5, 0.35) : 0,
      f.poke >= 0 ? 26 * bump(f.poke, 0, 0.42) : 0)
    let squeeze = f.poke >= 0 ? bump(f.poke, 0, 0.3) : 0
    let U = UI.buddy
    c.save()
    c.translate(U.x, U.y + 6 * nod - hop)
    c.rotate(-6 + sway, 0, 0)
    c.scale(1 + 0.04 * squeeze, 1 - 0.05 * squeeze)
    // the sprout, wiggling on the beat
    for i in 0..<2 {
      c.save()
      c.translate(-12 + Double(i) * 22, -238)
      c.rotate((i == 0 ? -28 : 26) + 8 * sin(TAU * beat + Double(i)), 0, 0)
      let lf = ListeningBuddyPaywallPath()
      lf.moveTo(0, 0)
      lf.cubicTo(-24, -14, -22, -50, 0, -66)
      lf.cubicTo(22, -50, 24, -14, 0, 0)
      lf.close()
      c.drawPath(lf, paint(i == 0 ? C.leaf : C.leafDark))
      c.drawLine(0, -4, 0, -56, stroke(0xFFFFFF, 2.4, 0.35))
      c.restore()
    }
    c.drawLine(-2, -232, 2, -246, stroke(C.leafDark, 6))
    // the body: a tall pebble
    let body = ListeningBuddyPaywallPath()
    body.moveTo(-150, 60)
    body.cubicTo(-158, -60, -150, -170, -100, -215)
    body.cubicTo(-50, -255, 70, -250, 115, -195)
    body.cubicTo(155, -145, 150, -40, 145, 60)
    body.close()
    c.drawPath(body, paint(C.body))
    c.save()
    c.clipPath(body)
    c.drawOval(rect(-120, -235, 150, 110), paint(C.bodyLight, 0.55))
    c.drawOval(rect(60, -160, 140, 260), paint(C.bodyShade, 0.4))
    c.restore()
    // cheeks
    c.drawCircle(-92, -78, 26, paint(C.cheek, 0.55 * blush + 0.2))
    c.drawCircle(98, -72, 24, paint(C.cheek, 0.55 * blush + 0.2))
    // eyes: closed happy arcs, open dots, or wide with a shine
    let ink = C.face
    for e in 0..<2 {
      let ex: Double = e == 0 ? -34 : 40
      let ey: Double = -128
      let closed = clamp01(1 - eyes)
      let wide = clamp01(eyes - 1)
      if closed > 0.01 {
        let arc = ListeningBuddyPaywallPath()
        arc.moveTo(ex - 16, ey)
        arc.quadTo(ex, ey + 14 * closed, ex + 16, ey)
        c.drawPath(arc, stroke(ink, 6, closed))
      }
      if closed < 0.99 {
        let r = mix(7, 13, wide)
        // blinks now and then
        let blink = max(0, 1 - abs((f.amb + 0.4).truncatingRemainder(dividingBy: 3) - 0.08) / 0.08)
        // They look down toward the chosen plan.
        let lookX = mix(Double(f.from - 1) * 4, Double(f.plan - 1) * 4, k)
        c.save()
        c.translate(ex + lookX, ey + 2.5)
        c.scale(1, max(0.12, (1 - blink) * (1 - closed)))
        c.drawCircle(0, 0, r, paint(ink))
        if wide > 0.05 { c.drawCircle(-r * 0.35, -r * 0.35, r * 0.32, paint(0xFFFFFF, wide)) }
        c.restore()
      }
    }
    // the mouth: a smile that widens, or an open "o"
    let mw = mix(18, 46, smile) * (1 - 0.6 * open)
    let mp = ListeningBuddyPaywallPath()
    mp.moveTo(4 - mw, -86)
    mp.quadTo(4, -86 + mix(10, 34, smile), 4 + mw, -86)
    if open < 0.05 {
      c.drawPath(mp, stroke(ink, 6))
    } else {
      c.drawPath(mp, stroke(ink, 6, 1 - open))
      c.drawOval(rect(4 - 12 * open, -92, 24 * open, 26 * open), paint(ink, open))
    }
    // headphones: the band over the top, the cups on either side
    let band = ListeningBuddyPaywallPath()
    band.moveTo(-150, -110)
    band.cubicTo(-160, -290, 150, -300, 140, -100)
    c.drawPath(band, stroke(C.phones, 15))
    c.drawPath(band, stroke(C.phonesLight, 4, 0.6))
    for side in [-1.0, 1.0] {
      c.save()
      c.translate(side < 0 ? -150 : 142, -100)
      c.drawRRect(rrect(-26, -44, 52, 88, 24), paint(C.phones))
      c.drawRRect(rrect(-14, -34, 18, 68, 9), paint(C.phonesLight, 0.6))
      c.restore()
    }
    c.restore()
    // the beat rings out of the right cup
    for k2 in 0..<2 {
      let q = (beat + Double(k2) * 0.5).truncatingRemainder(dividingBy: 1)
      let rx = U.x + 142 + 30 + 44 * q
      let a = 0.45 * (1 - q)
      let arc = ListeningBuddyPaywallPath()
      // small arcs beside the cup, clear of the pager dots below
      arc.addArc(rect(rx - 30, U.y - 150 - 14 * q, 60, 64 + 28 * q), -50, 100)
      c.drawPath(arc, stroke(C.phones, 4, a))
    }
  }

  func drawBushes(_ c: ListeningBuddyPaywallCanvas, _ f: M.Frame) {
    let bob = 3 * sin((TAU * f.amb) / LOOP)
    func bush(_ x: Double, _ y: Double, _ s: Double, _ color: Int) {
      let p = ListeningBuddyPaywallPath()
      p.moveTo(x - 75 * s, y)
      p.cubicTo(x - 80 * s, y - 40 * s, x - 40 * s, y - 60 * s, x - 15 * s, y - 45 * s)
      p.cubicTo(x, y - 85 * s, x + 50 * s, y - 80 * s, x + 50 * s, y - 40 * s)
      p.cubicTo(x + 80 * s, y - 45 * s, x + 85 * s, y - 10 * s, x + 80 * s, y)
      p.close()
      c.drawPath(p, paint(color))
    }
    bush(640, UI.white + 12 + bob, 1, C.bushB)
    bush(540, UI.white + 14 - bob, 1.05, C.bushA)
  }

  func drawNotes(_ c: ListeningBuddyPaywallCanvas, _ f: M.Frame) {
    // a note drifts up from the cup every 1.5 s
    let U = UI.buddy
    for i in 0..<6 {
      let q = (f.amb / 1.5 + Double(i)).truncatingRemainder(dividingBy: 6)
      if q > 1.6 { continue }
      let u = q / 1.6
      // they stay in the sky between the buddy and the hills, clear of the text above
      let x = U.x + 210 + 24 * sin(u * 5 + Double(i)) + 50 * u
      let y = U.y - 150 - 130 * u
      note(
        c, x, y, 0.7 + 0.2 * Double(i % 2), i % 2 != 0 ? C.phones : C.bodyShade,
        0.55 * sin(Double.pi * u), i % 3 == 0)
    }
    // Continue: a burst of notes from the bar and the buddy
    if f.go >= 0 && f.go < 2.4 {
      for i in 0..<9 {
        let t0 = Double(i) * 0.06
        let u = clamp01((f.go - t0) / 1.8)
        if u <= 0 || u >= 1 { continue }
        let fromBar = i % 3 != 0
        let x0 = fromBar ? 120 + Double(i) * 60 : U.x + 140
        let y0 = fromBar ? UI.bar.y : U.y - 160
        let x = x0 + 50 * sin(u * 6 + Double(i)) * u
        let y = y0 - 520 * easeOut(u)
        note(
          c, x, y, 0.9 + 0.3 * sin(Double(i)), i % 2 != 0 ? C.border : C.title,
          1 - smooth((u - 0.5) / 0.5), i % 2 == 0)
      }
    }
  }

  func drawPlans(_ c: ListeningBuddyPaywallCanvas, _ r: Res, _ f: M.Frame) {
    let K = UI.cards
    let Fr = UI.frame
    let k = spring(f.selAt, 0.42, 0.78)
    // the orange frame slides from the old card to the new one
    let fx = mix(K.x[f.from], K.x[f.plan], k)
    // While it travels, the frame stretches along its way and thins a little, like a drop.
    let travel = f.from != f.plan ? sin(Double.pi * clamp01(k)) : 0
    let fw = (K.w + Fr.pad * 2) * (1 + 0.22 * travel)
    let fh = (K.h + Fr.pad * 2) * (1 - 0.05 * travel)
    let fcx = fx + K.w / 2
    let fcy = K.y + K.h / 2
    c.drawRRect(
      rrect(fcx - fw / 2, fcy - fh / 2, fw, fh, Fr.r),
      stroke(C.border, Fr.w))
    for i in 0..<3 {
      let on = i == f.plan ? k : i == f.from ? 1 - k : 0
      let swell = i == f.plan ? 0.035 * bump(f.selAt, 0, 0.45) : 0
      let cx = K.x[i] + K.w / 2
      let cy = K.y + K.h / 2
      c.save()
      c.translate(cx, cy)
      c.scale(1 + swell, 1 + swell)
      c.translate(-cx, -cy)
      c.drawRRect(rrect(K.x[i], K.y, K.w, K.h, K.r), paint(C.card))
      if i == 1 {
        c.save()
        c.clipRRect(rrect(K.x[i], K.y, K.w, K.h, K.r))
        c.drawRect(rect(K.x[i], K.y, K.w, UI.headerH), paint(C.header))
        c.restore()
        text(c, Theme.Copy.popular[0], cx, K.y + 20, r.f.popular, paint(C.white), 0.5, 4)
        text(c, Theme.Copy.popular[1], cx, K.y + 44, r.f.popular, paint(C.white), 0.5, 4)
      }
      let ink = paint(0)

      ink.setColor(mixCol(C.label, 0x3F4560, on))
      let pl = Theme.plans[i]
      text(c, pl.name, cx, K.y + 106, r.f.name, ink, 0.5, 5)
      // the price, fitted to the card
      let pw = r.f.price.measureText(pl.price).width
      let sk = min(1, (K.w - 16) / pw)
      c.save()
      c.translate(cx, K.y + 163)
      c.scale(sk, sk)
      text(c, pl.price, 0, 0, r.f.price, ink, 0.5)
      c.restore()
      text(c, pl.per, cx, K.y + 216, r.f.per, paint(C.per), 0.5)
      c.restore()
    }
    // the note under the plans: the old lines leave, the new ones arrive
    let lk = smooth((f.selAt - 0.05) / 0.3)
    for l in 0..<2 {
      let y = UI.lines[l]
      let prev = Theme.plans[f.from].lines[l]
      let next = Theme.plans[f.plan].lines[l]
      if prev == next || lk >= 1 {
        text(c, next, PIN_W / 2, y, r.f.line, paint(C.note), 0.5)
        continue
      }
      text(c, prev, PIN_W / 2, y - 12 * lk, r.f.line, paint(C.note, 1 - lk), 0.5)
      text(c, next, PIN_W / 2, y + 12 * (1 - lk), r.f.line, paint(C.note, lk), 0.5)
    }
    let lw = r.f.link.measureText(Theme.Copy.links[0]).width
    let gap: Double = 48
    let rw = r.f.link.measureText(Theme.Copy.links[1]).width
    let x0 = PIN_W / 2 - (lw + gap + rw) / 2
    text(c, Theme.Copy.links[0], x0, UI.links, r.f.link, paint(C.fine))
    c.drawCircle(x0 + lw + gap / 2, UI.links, 2.5, paint(C.fine))
    text(c, Theme.Copy.links[1], x0 + lw + gap, UI.links, r.f.link, paint(C.fine))
  }

  func drawScene(_ c: ListeningBuddyPaywallCanvas, _ r: Res, _ g: Geo, _ f: M.Frame) {
    c.save()
    c.scale(g.s, g.s)
    c.translate(0, DY)
    c.scale(S, S)
    let top = -DY / S - 10
    let bottom = (g.h / g.s - DY) / S + 10
    c.drawRect(rect(-10, top, PIN_W + 20, bottom - top), paint(C.white))
    drawSky(c, f, top)
    // close
    let X = UI.close
    let cs = 1 - 0.04 * f.closeK
    c.save()
    c.translate(X.x, X.y)
    c.scale(cs, cs)
    let sh = paint(0xB9A68F, 0.18)
    sh.blur = 8
    c.drawCircle(0, 4, X.r, sh)
    c.drawCircle(0, 0, X.r, paint(C.white))
    c.drawLine(-15, -15, 15, 15, stroke(C.close, 4))
    c.drawLine(15, -15, -15, 15, stroke(C.close, 4))
    c.restore()
    text(c, Theme.Copy.title, PIN_W / 2, UI.titleY, r.f.title, paint(C.title), 0.5)
    for l in 0..<2 { text(c, Theme.Copy.sub[l], PIN_W / 2, UI.sub[l], r.f.sub, paint(C.sub), 0.5) }
    for i in 0..<3 { c.drawCircle(UI.dots.x[i], UI.dots.y, 7, paint(i == 0 ? C.dot : C.dotOff)) }
    // the buddy stands in the sky, cut off by the white sheet below
    c.save()
    c.clipRect(rect(-10, top, PIN_W + 20, UI.white - top))
    drawBuddy(c, f)
    c.restore()
    drawBushes(c, f)
    drawPlans(c, r, f)
    // the bar, full width to the bottom edge
    let B = UI.bar
    let bp = paint(0)

    bp.setColor(mixCol(C.bar, 0x5B6077, f.press))
    c.drawRect(rect(-10, B.y, PIN_W + 20, bottom - B.y), bp)
    let ts = 1 - 0.04 * f.press
    c.save()
    c.translate(PIN_W / 2, B.y + B.h / 2)
    c.scale(ts, ts)
    text(c, Theme.Copy.cta, 0, 0, r.f.cta, paint(C.white), 0.5, 6)
    c.restore()
    drawNotes(c, f)
    c.restore()
  }

}
