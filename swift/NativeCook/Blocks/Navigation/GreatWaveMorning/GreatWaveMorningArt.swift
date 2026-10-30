import SwiftUI

// All artwork stays in pin coordinates. The painter keeps Canvas state local and uses one
// blurred draw per shadow; every path and phase below mirrors gwm-draw.ts.
final class GreatWaveMorningArt {
  typealias M = GreatWaveMorningMotion
  typealias C = GreatWaveMorningTheme.C
  typealias UI = GreatWaveMorningTheme.UI
  typealias COPY = GreatWaveMorningTheme.COPY
  let DY = GreatWaveMorningTheme.DY
  let S = GreatWaveMorningTheme.S
  let PIN_W = GreatWaveMorningTheme.PIN_W
  let COLLAPSE = GreatWaveMorningTheme.COLLAPSE
  let PULL = M.PULL
  let LOOP = M.LOOP
  let TAU = Double.pi * 2
  struct Geo {
    let w: Double
    let h: Double
    let s: Double
  }
  struct Sea {
    let pull: Double
    let e: Double
    let fold: Double
    let bottom: Double
  }
  var context: GraphicsContext
  var stack: [GraphicsContext] = []
  init(_ context: GraphicsContext) { self.context = context }
  func clamp01(_ x: Double) -> Double { M.clamp01(x) }
  func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { M.mix(a, b, k) }
  func smooth(_ x: Double) -> Double { M.smooth(x) }
  func easeOut(_ x: Double) -> Double { M.easeOut(x) }
  /** The header's geometry for a scroll: its bottom edge, how folded it is, how far it is pulled. */
  func seaOf(_ scroll: Double) -> Sea {
    let pull = max(0, -scroll)
    let fold = smooth(max(0, scroll) / COLLAPSE)
    let bottom = mix(UI.header.bottom, UI.header.collapsed, fold) + pull
    return Sea(pull: pull, e: pull / PULL, fold: fold, bottom: bottom)
  }

  /** The river's centre x and half width at a height y (it winds down from the horizon). */
  func riverAt(_ y: Double, _ sea: Sea) -> (x: Double, w: Double) {
    let top = 252 + sea.pull * 0.35
    let u = clamp01((y - top) / (UI.header.bottom + sea.pull - top))
    return (x: 360 + 46 * sin(u * 6.4 + 0.3) * (0.5 + 0.5 * u), w: mix(3, 70, pow(u, 1.6)))
  }

  let BASES: [Double] = [216, 246, 280, 318, 356, 398, 440, 478]
  let AMPS: [Double] = [10, 14, 16, 18, 20, 22, 20, 16]
  let LAMS: [Double] = [150, 120, 170, 140, 190, 150, 210, 170]

  func bandPath(_ i: Int, _ sea: Sea, _ amb: Double) -> Shape {
    let k = Double(i) / Double(BASES.count - 1)
    let ph = (TAU * amb) / LOOP
    // pulled: the sea drops with the content and swells; folded: every layer gathers into one line
    let rest = BASES[i] + sea.pull * (0.25 + 0.75 * k)
    let line = sea.bottom - 22 + Double(i - 7) * 1.5
    let base = mix(rest, line, sea.fold)
    let amp = AMPS[i] * (1 + 0.7 * sea.e * (0.4 + k)) * (1 - 0.82 * sea.fold)
    let lam = LAMS[i]
    let p = Shape()
    let step = 12.0
    let dip = 0.0
    for x in stride(from: -24.0, through: PIN_W + 24, by: step) {
      let hill = pow(
        abs(sin((Double.pi * x) / lam + Double(i) * 1.7 + ph * (i % 2 == 0 ? 1 : -1))), 0.8)
      let roll = sin((TAU * x) / (lam * 2.3) + Double(i) + ph * 2)
      var y = base - amp * (0.75 * hill + 0.35 * roll)
      if dip > 0 && sea.fold < 0.999 {
        // the front layers part where the river runs
        let r = riverAt(base, sea)
        let d = (x - r.x) / (r.w * 1.15 + 12)
        y += 130 * exp(-d * d) * (1 - sea.fold)
      }
      if x == -24 { p.moveTo(x, y) } else { p.lineTo(x, y) }
    }
    p.lineTo(PIN_W + 24, sea.bottom + 60)
    p.lineTo(-24, sea.bottom + 60)
    p.close()
    return p
  }

  func drawClouds(_ canvas: GreatWaveMorningArt, _ sea: Sea, _ amb: Double) {
    let a = 1 - smooth(sea.fold * 1.6)
    if a < 0.01 { return }
    let drift = 6 * sin((TAU * amb) / LOOP)
    let sink = sea.fold * 60 + sea.pull * 0.15
    let puff = { [self] (cx: Double, cy: Double, rr: Double) in
      canvas.drawCircle(cx, cy + 6, rr, paint(C.cloudShade, a))
      canvas.drawCircle(cx, cy, rr, paint(C.cloud, a))
    }
    // left
    let lx = drift
    puff(lx + 20, 236 + sink, 34)
    puff(lx + 70, 222 + sink, 30)
    puff(lx + 118, 238 + sink, 24)
    puff(lx + 150, 254 + sink, 16)
    // right
    let rx = -drift
    puff(rx + 520, 228 + sink, 22)
    puff(rx + 570, 206 + sink, 32)
    puff(rx + 630, 186 + sink, 38)
    puff(rx + 700, 196 + sink, 40)
    puff(rx + 740, 224 + sink, 30)
  }

  func drawRiver(_ canvas: GreatWaveMorningArt, _ sea: Sea, _ amb: Double) {
    let a = 1 - smooth(sea.fold * 1.4)
    if a < 0.01 { return }
    let top = 252 + sea.pull * 0.35
    let left = Shape()
    var right: [(x: Double, y: Double)] = []
    let n = 28
    for i in 0...(n) {
      let y = mix(top, sea.bottom + 10, Double(i) / Double(n))
      let r = riverAt(y, sea)
      if i == 0 { left.moveTo(r.x - r.w, y) } else { left.lineTo(r.x - r.w, y) }
      right.append((x: r.x + r.w, y: y))
    }
    for i in right.indices.reversed() { left.lineTo(right[i].x, right[i].y) }
    left.close()
    let rp = Paint()
    rp.setAntiAlias(true)
    rp.setShader(
      gradient(
        CGPoint(x: 0, y: top), CGPoint(x: 0, y: sea.bottom), [col(C.river, a), col(C.riverLow, a)],
        nil))
    canvas.drawPath(left, shadowPaint(0.5 * a, 6))
    canvas.drawPath(left, rp)
    canvas.drawPath(left, stroke(0xB8D2EE, 2.5, 0.5 * a))
    // ripples drifting down the river
    let ph = amb / LOOP
    for k in 0..<(7) {
      let u = (Double(k) / 7 + ph).truncatingRemainder(dividingBy: 1)
      let y = mix(top + 20, sea.bottom - 10, u)
      let r = riverAt(y, sea)
      canvas.drawLine(
        r.x - r.w * 0.45, y, r.x + r.w * 0.2, y + 1.5,
        stroke(0x8DB3DB, 2, 0.55 * a * sin(Double.pi * u)))
    }
  }

  func drawBoat(_ canvas: GreatWaveMorningArt, _ sea: Sea, _ amb: Double) {
    let ph = (TAU * 3 * amb) / LOOP
    let B = UI.boat
    let restY = B.y + sea.pull * 0.6 + 4 * sin(ph)
    let lineY = sea.bottom - 34
    let y = mix(restY, lineY, sea.fold)
    let x = mix(riverAt(restY, sea).x, 600, sea.fold)
    let sc = mix(1, 0.62, sea.fold) * (1 + 0.08 * sea.e)
    let tilt = 5 * sin(ph + 1) + 8 * sea.e * sin(ph * 2)
    canvas.save()
    canvas.translate(x, y)
    canvas.rotate(tilt, 0, 0)
    canvas.scale(sc, sc)
    canvas.drawOval(rect(-40, 14, 80, 10), paint(0x173763, 0.35))
    // Its wake: two soft lines opening out behind it, with ripples running down them.
    let wakeAlpha = 1 - sea.fold
    if wakeAlpha > 0.02 {
      for side in [-1.0, 1.0] {
        canvas.drawLine(side * 26, 16, side * 70, 64, stroke(0xCFE0F4, 3, 0.45 * wakeAlpha))
        for k in 0..<3 {
          let u = (amb / LOOP * 6 + Double(k) / 3).truncatingRemainder(dividingBy: 1)
          let px = mix(side * 26, side * 70, u)
          let py = mix(16, 64, u)
          canvas.drawLine(
            px - side * 6, py - 2, px + side * 2, py + 4,
            stroke(0xFFFFFF, 2.4, 0.55 * wakeAlpha * sin(Double.pi * u)))
        }
      }
    }
    let hull = Shape()
    hull.moveTo(-38, 0)
    hull.lineTo(38, 0)
    hull.lineTo(26, 16)
    hull.lineTo(-28, 16)
    hull.close()
    canvas.drawPath(hull, paint(C.boat))
    canvas.drawLine(-30, 6, 32, 6, stroke(C.sailShade, 2))
    canvas.drawLine(2, 0, 2, -62, stroke(0x2B3B55, 2.4))
    let sail = Shape()
    sail.moveTo(4, -58)
    sail.lineTo(4, -6)
    sail.lineTo(30, -6)
    sail.close()
    canvas.drawPath(sail, paint(C.sail))
    let sail2 = Shape()
    sail2.moveTo(0, -50)
    sail2.lineTo(0, -6)
    sail2.lineTo(-22, -6)
    sail2.close()
    canvas.drawPath(sail2, paint(C.sailShade))
    let flag = Shape()
    flag.moveTo(2, -62)
    flag.lineTo(14, -58 + 2 * sin(ph * 3))
    flag.lineTo(2, -54)
    flag.close()
    canvas.drawPath(flag, paint(0x21406E))
    canvas.restore()
  }

  /** A curling crest, cut from paper: a blue body rising into a white ribbon of foam that winds inward. */
  func drawCurl(
    _ canvas: GreatWaveMorningArt, _ cx: Double, _ cy: Double, _ R: Double, _ dir: Double,
    _ roll: Double, _ a: Double
  ) {
    if a < 0.01 { return }
    canvas.save()
    canvas.translate(cx, cy)
    canvas.scale(dir, 1)
    // the body: a swell rising from the left into the curl
    let body = Shape()
    body.moveTo(-2.6 * R, 1.25 * R)
    body.cubicTo(-1.7 * R, 1.0 * R, -1.2 * R, -0.1 * R, -0.35 * R, -0.85 * R)
    body.cubicTo(0.25 * R, -1.25 * R, 1.15 * R, -1.05 * R, 1.2 * R, -0.25 * R)
    body.cubicTo(1.22 * R, 0.25 * R, 0.8 * R, 0.5 * R, 0.45 * R, 0.35 * R)
    body.cubicTo(0.9 * R, 0.75 * R, 1.3 * R, 1.0 * R, 1.8 * R, 1.25 * R)
    body.close()
    canvas.drawPath(body, shadowPaint(0.4 * a, 8))
    let bp = Paint()
    bp.setAntiAlias(true)
    bp.setShader(
      gradient(
        CGPoint(x: -R, y: -R), CGPoint(x: R, y: R),
        [col(0x5C8CC8, a), col(0x2C5590, a), col(0x1C3F72, a)], [0, 0.55, 1]))
    canvas.drawPath(body, bp)
    // inner lines of the swell, like layered paper
    for k in 0..<(3) {
      let q = Shape()
      let o = 0.22 * R * Double(k + 1)
      q.moveTo(-2.2 * R + o, 1.2 * R)
      q.cubicTo(
        -1.4 * R + o, 0.9 * R, -0.9 * R + o * 0.6, 0.1 * R + o * 0.4, -0.25 * R + o * 0.5,
        -0.5 * R + o * 0.6)
      canvas.drawPath(q, stroke(0x7FA7DA, 3, 0.35 * a))
    }
    // the foam: a white ribbon, wide at the lip, winding into the eye of the curl
    let ox = 0.42 * R
    let oy = -0.2 * R
    let n = 36
    let outer = Shape()
    var inner: [(x: Double, y: Double)] = []
    for i in 0...(n) {
      let u = Double(i) / Double(n)
      let ang = Double.pi * 1.02 + u * Double.pi * 1.65 + roll * 0.25
      let ro = R * (1.0 - 0.62 * u)
      let wv = R * (0.4 * pow(1 - u, 1.1) + 0.04)
      let x = ox + cos(ang) * ro * 0.95
      let y = oy + sin(ang) * ro * 0.82
      if i == 0 { outer.moveTo(x - 0.12 * R, y + 0.25 * R) }
      outer.lineTo(x, y)
      inner.append((x: ox + cos(ang) * (ro - wv) * 0.95, y: oy + sin(ang) * (ro - wv) * 0.82))
    }
    for i in inner.indices.reversed() { outer.lineTo(inner[i].x, inner[i].y) }
    outer.close()
    canvas.drawPath(outer, shadowPaint(0.3 * a, 5))
    let fp = Paint()
    fp.setAntiAlias(true)
    fp.setShader(
      gradient(
        CGPoint(x: 0, y: -R), CGPoint(x: 0, y: R * 0.4),
        [col(0xFBF8F2, a), col(C.crest, a), col(C.crestShade, a)], [0, 0.6, 1]))
    canvas.drawPath(outer, fp)
    // the lip breaks into a few round lumps of foam
    for k in 0..<(3) {
      let u = 0.02 + Double(k) * 0.07
      let ang = Double.pi * 1.02 + u * Double.pi * 1.65 + roll * 0.25
      let ro = R * (1.0 - 0.62 * u)
      canvas.drawCircle(
        ox + cos(ang) * ro * 0.98, oy + sin(ang) * ro * 0.86, R * (0.13 - Double(k) * 0.025),
        paint(C.crest, a))
    }
    canvas.restore()
  }

  func drawFoam(
    _ canvas: GreatWaveMorningArt, _ frame: M.Frame, _ crests: [(x: Double, y: Double, d: Double)]
  ) {
    if frame.foam < 0 { return }
    let t = frame.foam
    for k in 0..<(crests.count) {
      let cr = crests[k]
      for i in 0..<(16) {
        let h1 = sin(Double(i + 1) * 12.9898 + Double(k) * 7.1) * 43758.5453
        let r1 = h1 - floor(h1)
        let h2 = sin(Double(i + 1) * 39.35 + Double(k) * 3.3) * 24634.63
        let r2 = h2 - floor(h2)
        let ang = -Double.pi / 2 + (r1 - 0.5) * 1.6 + cr.d * 0.4
        let v = (180 + 220 * r2) * frame.foamPow
        let x = cr.x + cos(ang) * v * t
        let y = cr.y + sin(ang) * v * t + 420 * t * t
        let a = (1 - smooth(t / 1.2)) * 0.9
        if a < 0.02 { continue }
        canvas.drawCircle(x, y, 3 + 4 * r1 * (1 - t), paint(C.crest, a))
      }
    }
  }

  func drawSea(_ canvas: GreatWaveMorningArt, _ frame: M.Frame) {
    let sea = seaOf(frame.scroll)
    let amb = frame.amb
    // the page's torn edge: the sea is cut along it
    let edgeY = { (x: Double) in sea.bottom + 4 * sin(x * 0.07 + 1.3) + 2 * sin(x * 0.23) }
    let above = Shape()
    above.moveTo(-24, -DY / S - 20)
    above.lineTo(PIN_W + 24, -DY / S - 20)
    for x in stride(from: PIN_W + 24, through: -24.0, by: -16) { above.lineTo(x, edgeY(x)) }
    above.close()
    canvas.save()
    canvas.clipPath(above)
    // sky
    let sp = Paint()
    sp.setShader(
      gradient(
        CGPoint(x: 0, y: -DY / S), CGPoint(x: 0, y: sea.bottom), [col(C.sky), col(C.skyLow)], nil))
    canvas.drawRect(rect(-30, -DY / S - 10, PIN_W + 60, sea.bottom + DY / S + 70), sp)
    let tx = frame.tiltX
    let ty = frame.tiltY
    canvas.save()
    canvas.translate(tx * 4, ty * 2)
    drawClouds(canvas, sea, amb)
    canvas.restore()
    for i in 0..<(BASES.count) {
      let p = bandPath(i, sea, amb)
      let depth = Double(i + 1) / Double(BASES.count)
      canvas.save()
      canvas.translate(tx * 18 * depth, ty * 8 * depth)
      canvas.translate(0, -5)
      canvas.drawPath(p, shadowPaint(0.45, 6))
      canvas.restore()
      canvas.save()
      canvas.translate(tx * 18 * depth, ty * 8 * depth)
      canvas.drawPath(p, paint(C.bands[i]))
      // a paler lip along each cut edge, where the light catches the paper
      canvas.save()
      canvas.clipPath(p)
      canvas.translate(0, 3)
      canvas.drawPath(p, stroke(0xFFFFFF, 3, 0.08))
      canvas.restore()
      canvas.restore()
    }
    canvas.save()
    canvas.translate(tx * 12, ty * 5)
    drawRiver(canvas, sea, amb)
    drawBoat(canvas, sea, amb)
    // the crests: they swell and roll forward when the sea is pulled
    let ph = (TAU * amb) / LOOP
    let roll = 0.18 * sin(ph * 2) + 0.5 * sea.e
    let ca = 1 - smooth(sea.fold * 1.5)
    let crests: [(x: Double, y: Double, R: Double, d: Double)] = [
      (x: 120 - 10 * sea.e, y: 380 + sea.pull * 0.75, R: 78 * (1 + 0.25 * sea.e), d: 1),
      (x: 600 + 10 * sea.e, y: 330 + sea.pull * 0.55, R: 64 * (1 + 0.25 * sea.e), d: -1),
      (x: 650 + 8 * sea.e, y: 462 + sea.pull * 0.9, R: 52 * (1 + 0.3 * sea.e), d: -1),
    ]
    for k in 0..<(crests.count) {
      drawCurl(
        canvas, crests[k].x, crests[k].y - 20 * sea.fold, crests[k].R * (1 - 0.6 * sea.fold),
        crests[k].d, roll + Double(k) * 0.3, ca)
    }
    canvas.restore()
    canvas.restore()
    // the paper sea casts a soft shadow on the page below its edge
    let line = Shape()
    for x in stride(from: -24.0, through: PIN_W + 24, by: 16) {
      if x == -24 { line.moveTo(x, edgeY(x) + 3) } else { line.lineTo(x, edgeY(x) + 3) }
    }
    let lp = stroke(0x06142B, 6, 0.18)
    lp.setMaskFilter(5)
    canvas.drawPath(line, lp)
    // the foam flies over the page too
    drawFoam(
      canvas,
      frame,
      crests.map { cr in (x: cr.x - cr.d * cr.R * 0.2, y: cr.y - cr.R, d: cr.d) }
    )
  }

  func drawHeaderText(
    _ canvas: GreatWaveMorningArt, _ resources: GreatWaveMorningResources, _ frame: M.Frame
  ) {
    let sea = seaOf(frame.scroll)
    let fold = sea.fold
    // the greeting shrinks a little into the folded bar; the sprint line folds away
    let s = mix(1, 0.86, fold)
    canvas.save()
    canvas.translate(UI.hello.x, UI.hello.y + sea.pull * 0.15 + 4 * fold)
    canvas.scale(s, s)
    text(canvas, COPY.hello, 0, 0, resources.f.hello, paint(0xFFFFFF))
    canvas.restore()
    let pa = 1 - smooth(fold * 2.2)
    if pa > 0.01 {
      let P = UI.pill
      let f0 = resources.f.pill
      let w0 = f0.measureText(COPY.sprint[0]).width
      let w1 = f0.measureText(COPY.sprint[1]).width
      let w2 = f0.measureText(COPY.sprint[2]).width
      let pw = w0 + w1 + w2 + 24 + 36
      let py = P.y + sea.pull * 0.15 - 12 * fold
      canvas.drawRRect(rrect(P.x, py, pw, P.h, P.h / 2), paint(C.pill, pa))
      var x = P.x + 18
      x += text(canvas, COPY.sprint[0], x, py + P.h / 2, f0, paint(0xFFFFFF, pa)) + 12
      x += text(canvas, COPY.sprint[1], x, py + P.h / 2, f0, paint(0xFFFFFF, 0.7 * pa)) + 12
      text(canvas, COPY.sprint[2], x, py + P.h / 2, f0, paint(0xFFFFFF, pa))
    }
    let A = UI.avatar
    let avatarScale = mix(1, 0.78, fold)
    canvas.save()
    canvas.translate(A.x, A.y + sea.pull * 0.15)
    canvas.scale(avatarScale, avatarScale)
    canvas.drawCircle(0, 0, A.r + 4, paint(C.avatarRing))
    canvas.drawCircle(0, 0, A.r, paint(C.avatar))
    text(canvas, COPY.initials, 0, 0, resources.f.avatar, paint(C.navy), 0.5)
    canvas.restore()
  }

  // ------------------------------------------------------------------ the content

  func drawContent(
    _ canvas: GreatWaveMorningArt, _ resources: GreatWaveMorningResources, _ frame: M.Frame
  ) {
    let off = -frame.scroll
    canvas.save()
    canvas.translate(0, off)
    let K = UI.cards
    for i in 0..<(3) {
      let st = COPY.stats[i]
      let x = K.x[i]
      canvas.drawRRect(rrect(x + 2, K.y + 6, K.w, K.h, K.r), paint(C.cardEdge))
      canvas.drawRRect(rrect(x, K.y, K.w, K.h, K.r), paint(C.card))
      // a little cut-paper wave in its corner, rocking
      canvas.save()
      canvas.clipRRect(rrect(x, K.y, K.w, K.h, K.r))
      let w = Shape()
      let ph = (TAU * frame.amb) / LOOP + Double(i)
      w.moveTo(x + K.w * 0.42, K.y + K.h)
      w.cubicTo(
        x + K.w * 0.6, K.y + K.h - 18 - 3 * sin(ph), x + K.w * 0.72, K.y + K.h - 46 + 4 * sin(ph),
        x + K.w, K.y + K.h - 58)
      w.lineTo(x + K.w, K.y + K.h)
      w.close()
      canvas.drawPath(w, paint(st.wave))
      canvas.restore()
      text(canvas, st.label, x + 26, K.y + 38, resources.f.cardLabel, paint(C.navy))
      text(canvas, st.value, x + 24, K.y + 96, resources.f.big, paint(st.color))
      let sw =
        !st.sub[0].isEmpty
        ? text(canvas, st.sub[0], x + 26, K.y + 142, resources.f.cardSub, paint(C.tan)) : 0
      text(canvas, st.sub[1], x + 26 + sw, K.y + 142, resources.f.cardSub, paint(C.navy))
    }
    for i in 0..<(3) {
      canvas.drawCircle(UI.dots.x[i], UI.dots.y, 5.5, paint(i == 0 ? C.blue : 0xD6C9AE))
    }
    text(canvas, COPY.focus, 42, UI.focus.y, resources.f.focus, paint(C.navy))
    canvas.drawLine(220, UI.focus.lineY, 648, UI.focus.lineY, stroke(C.line, 1.5))
    // a tiny wave at the end of the line
    let tw = Shape()
    tw.moveTo(652, UI.focus.lineY)
    tw.cubicTo(660, UI.focus.lineY - 12, 676, UI.focus.lineY - 12, 680, UI.focus.lineY - 4)
    tw.cubicTo(672, UI.focus.lineY - 8, 668, UI.focus.lineY - 2, 674, UI.focus.lineY)
    tw.lineTo(704, UI.focus.lineY)
    canvas.drawPath(tw, stroke(C.line, 1.5))
    let R = UI.rows
    for i in 0..<(COPY.rows.count) {
      let row = COPY.rows[i]
      let y = R.y0 + Double(i) * R.pitch
      let dn = frame.done[i]
      let press = frame.pressRow == i ? frame.pressK : 0
      canvas.drawRRect(rrect(R.x + 2, y + 5, R.w, R.h, 6), paint(C.cardEdge, 0.9))
      canvas.drawRRect(rrect(R.x, y, R.w, R.h, 6), paint(C.row))
      if press > 0.01 {
        canvas.drawRRect(rrect(R.x, y, R.w, R.h, 6), paint(0xE9DFC9, 0.7 * press))
      }
      let bp = Paint()
      bp.setAntiAlias(true)
      bp.setColor(mixCol(row.bar, COPY.done.bar, dn))
      canvas.drawRRect(rrect(R.x, y, 14, R.h, 4), bp)
      let tp = Paint()
      tp.setAntiAlias(true)
      tp.setColor(mixCol(C.navy, C.grey, dn))
      let tw2 = text(canvas, row.title, 74, y + R.h / 2, resources.f.row, tp)
      if dn > 0.01 {
        canvas.drawLine(
          74, y + R.h / 2 + 1, 74 + tw2 * easeOut(dn), y + R.h / 2 + 1, stroke(C.grey, 1.6))
      }
      text(canvas, row.time, 538, y + R.h / 2, resources.f.time, paint(C.time), 1)
      // the status pill flips to Done
      let label = dn > 0.5 ? COPY.done.status : row.status
      let pw = max(98, resources.f.status.measureText(label).width + 30)
      let flip = abs(cos(Double.pi * dn))
      canvas.save()
      canvas.translate(632, y + R.h / 2)
      canvas.scale(1, max(0.05, flip))
      let pp = Paint()
      pp.setAntiAlias(true)
      pp.setColor(dn > 0.5 ? col(COPY.done.pill) : col(row.pill))
      canvas.drawRRect(rrect(-pw / 2, -21, pw, 42, 21), pp)
      text(canvas, label, 0, 0, resources.f.status, paint(dn > 0.5 ? COPY.done.ink : row.ink), 0.5)
      canvas.restore()
    }
    canvas.restore()
  }

  func tabIcon(_ canvas: GreatWaveMorningArt, _ i: Int, _ x: Double, _ y: Double, _ color: Int) {
    let p = stroke(color, 3.4)
    let fp = paint(color)
    if i == 0 {
      let h = Shape()
      h.moveTo(x - 18, y)
      h.lineTo(x, y - 17)
      h.lineTo(x + 18, y)
      h.lineTo(x + 13, y)
      h.lineTo(x + 13, y + 16)
      h.lineTo(x + 4, y + 16)
      h.lineTo(x + 4, y + 6)
      h.lineTo(x - 4, y + 6)
      h.lineTo(x - 4, y + 16)
      h.lineTo(x - 13, y + 16)
      h.lineTo(x - 13, y)
      h.close()
      canvas.drawPath(h, fp)
    } else if i == 1 {
      for k in 0..<(3) {
        canvas.drawCircle(x - 15, y - 12 + Double(k) * 12, 3, fp)
        canvas.drawLine(x - 6, y - 12 + Double(k) * 12, x + 18, y - 12 + Double(k) * 12, p)
      }
    } else if i == 2 {
      canvas.drawRRect(rrect(x - 20, y - 10, 40, 28, 4), fp)
      canvas.drawRRect(rrect(x - 7, y - 17, 14, 9, 2), stroke(color, 3))
      canvas.drawLine(x - 20, y + 2, x + 20, y + 2, stroke(C.tabBg, 2))
    } else if i == 3 {
      canvas.drawCircle(x - 8, y - 8, 7, fp)
      canvas.drawCircle(x + 10, y - 6, 6, fp)
      canvas.drawRRect(rrect(x - 20, y + 2, 24, 14, 7), fp)
      canvas.drawRRect(rrect(x + 2, y + 3, 20, 13, 6), fp)
    } else {
      canvas.drawRect(rect(x - 16, y + 2, 7, 14), fp)
      canvas.drawRect(rect(x - 4, y - 6, 7, 22), fp)
      canvas.drawRect(rect(x + 8, y - 16, 7, 32), fp)
      canvas.drawLine(x - 20, y + 17, x + 20, y + 17, stroke(color, 2.5))
    }
  }

  func drawTabs(
    _ canvas: GreatWaveMorningArt, _ resources: GreatWaveMorningResources, _ frame: M.Frame,
    _ screenH: Double
  ) {
    let T = UI.tab
    canvas.drawRect(rect(-10, T.y, PIN_W + 20, screenH), paint(C.tabBg))
    canvas.drawLine(0, T.y, PIN_W, T.y, stroke(C.line, 1.5))
    // the selected tile slides to its tab
    let pos = frame.tab
    let i0 = max(0, min(4, Int(floor(pos))))
    let i1 = min(4, i0 + 1)
    let tx = mix(T.x[i0], T.x[i1], pos - Double(i0))
    canvas.drawRRect(rrect(tx - T.tile / 2, T.y + 12, T.tile, 64, 14), paint(C.blue))
    for i in 0..<(5) {
      let on = max(0, 1 - abs(pos - Double(i)))
      let ink = mixCol(C.tabIcon, 0xFFFFFF, on)
      let iconColor = rgb(ink)
      tabIcon(canvas, i, T.x[i], T.y + 44, iconColor)
      let lp = Paint()
      lp.setAntiAlias(true)
      lp.setColor(mixCol(C.tabIcon, C.navy, on))
      text(canvas, COPY.tabs[i], T.x[i], T.y + 94, resources.f.tab, lp, 0.5)
    }
  }

  func drawScene(
    _ canvas: GreatWaveMorningArt, _ resources: GreatWaveMorningResources, _ g: Geo,
    _ frame: M.Frame
  ) {
    canvas.drawRect(rect(0, 0, g.w, g.h), paint(C.page))
    canvas.save()
    canvas.scale(g.s, g.s)
    canvas.translate(0, DY)
    canvas.scale(S, S)
    let screenH = (g.h / g.s - DY) / S
    drawContent(canvas, resources, frame)
    drawSea(canvas, frame)
    drawHeaderText(canvas, resources, frame)
    drawTabs(canvas, resources, frame, screenH)
    canvas.restore()
  }
}
