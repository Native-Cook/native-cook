import SwiftUI

/// Canvas uses plain sRGB; the Skia-only Display P3 workaround is intentionally absent.
enum GhostFloatWelcomeArt {
  typealias M = GhostFloatWelcomeMotion
  typealias Theme = GhostFloatWelcomeTheme
  typealias R = GhostFloatWelcomeResources
  typealias P = GhostFloatWelcomePaths
  typealias C = GhostFloatWelcomeTheme.C
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }
  static func mix(_ a: Int, _ b: Int, _ p: Double) -> Color {
    func channel(_ shift: Int) -> Double {
      M.mix(Double((a >> shift) & 255), Double((b >> shift) & 255), p) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
  }
  static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
  }
  static func transformed(
    _ context: GraphicsContext, _ x: Double, _ y: Double, _ rotation: Double = 0,
    _ scale: Double = 1
  ) -> GraphicsContext {
    var c = context
    c.translateBy(x: x, y: y)
    c.rotate(by: .degrees(rotation))
    c.scaleBy(x: scale, y: scale)
    return c
  }
  static func label(
    _ context: GraphicsContext, _ label: R.Label, _ x: Double, _ cy: Double, _ anchor: Double,
    _ color: Color
  ) {
    var c = context
    c.translateBy(x: x - label.width * anchor, y: cy + label.size * 0.36)
    c.fill(label.path, with: .color(color))
  }
  static func drawScene(
    _ context: GraphicsContext, _ size: CGSize, _ frame: M.Frame, _ live: GhostFloatWelcomeLive
  ) {
    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(color(C.bg)))
    let scale = size.width / Theme.width
    let dy = (size.height - Theme.height * scale) / 2
    var c = context
    c.translateBy(x: 0, y: dy)
    c.scaleBy(x: scale, y: scale)
    for i in Theme.stars.indices {
      let star = Theme.stars[i]
      let pose = M.starPose(i, frame.t, live.amb)
      let j = 7 + i
      let local = transformed(
        c, pose.x + live.ox[j] - live.tiltX * 10 * star.depth,
        pose.y + live.oy[j] - live.tiltY * 8 * star.depth, pose.rot + live.or[j], star.s / 10)
      local.fill(P.star, with: .color(color(C.star)))
    }
    let ghost = M.bodyPose(0, frame.t, live.amb)
    let gx = ghost.x + live.ox[0] - live.tiltX * 12
    let gy = ghost.y + live.oy[0] - live.tiltY * 9
    ring(c, gx, gy, frame.ring)
    ring(c, gx, gy, live.ring)
    for i in 1..<7 {
      let b = Theme.bodies[i]
      let pose = M.bodyPose(i, frame.t, live.amb)
      let x = pose.x + live.ox[i] - live.tiltX * 12 * b.depth
      let y = pose.y + live.oy[i] - live.tiltY * 9 * b.depth
      let speed = hypot(live.vx[i], live.vy[i])
      if speed > 260 {
        let k = min(1, (speed - 260) / 900)
        let start = CGPoint(x: x, y: y)
        let end = CGPoint(x: x - live.vx[i] * 0.07, y: y - live.vy[i] * 0.07)
        var streak = Path(); streak.move(to: start); streak.addLine(to: end)
        let light = Color(.sRGB, red: 0.85, green: 0.82, blue: 1, opacity: 0.22 * k)
        c.stroke(streak, with: .linearGradient(Gradient(colors: [light, light.opacity(0)]), startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: b.r * 1.1, lineCap: .round))
      }
      token(c, i, x, y, pose.rot + live.or[i])
    }
    for k in Theme.glints.indices {
      let gl = Theme.glints[k]
      let body = Theme.bodies[gl.body]
      let pose = M.bodyPose(gl.body, frame.t, live.amb)
      let twinkle =
        0.5 + 0.5 * sin(M.tau * (Double(2 + k % 2) * live.amb / M.LOOP + Double(k) * 0.27))
      let local = transformed(
        c, pose.x + live.ox[gl.body] - live.tiltX * 12 * body.depth + gl.dx,
        pose.y + live.oy[gl.body] - live.tiltY * 9 * body.depth + gl.dy,
        12 * sin(M.tau * (live.amb / M.LOOP + Double(k) * 0.2)), gl.s * (0.7 + 0.3 * twinkle) / 10)
      local.fill(P.glint, with: .color(.white.opacity(0.75 + 0.25 * twinkle)))
    }
    drawGhost(c, frame, live, gx, gy, ghost.rot + live.or[0])
    controls(c, frame, live)
  }
  static func token(
    _ context: GraphicsContext, _ i: Int, _ x: Double, _ y: Double, _ rotation: Double
  ) {
    let b = Theme.bodies[i]
    var c = transformed(context, x, y)
    if b.kind == "dino" || b.kind == "penguin" {
      c.clip(to: circle(0, 0, b.r))
      c.rotate(by: .degrees(rotation * 0.5))
      c.draw(
        b.kind == "dino" ? R.dino : R.penguin,
        in: CGRect(x: -b.r * 1.04, y: -b.r * 1.04, width: b.r * 2.08, height: b.r * 2.08))
      return
    }
    guard let tk = Theme.tokens[b.kind] else { return }
    c.fill(circle(0, 0, b.r), with: .color(color(tk.face)))
    c.fill(
      circle(0, 0, b.r),
      with: .radialGradient(
        Gradient(colors: [.white.opacity(0.16), .white.opacity(0)]),
        center: CGPoint(x: -b.r * 0.4, y: -b.r * 0.5), startRadius: 0, endRadius: b.r * 1.3))
    c.rotate(by: .degrees(rotation))
    let k = b.r / 24 * 1.55
    c.scaleBy(x: k, y: k)
    switch b.kind {
    case "lumen": c.fill(P.lumen, with: .color(color(tk.glyph)))
    case "ember": c.fill(P.ember, with: .color(color(tk.glyph)), style: FillStyle(eoFill: true))
    case "fern":
      c.fill(P.fern, with: .color(color(tk.glyph)))
      c.stroke(
        P.fernRib, with: .color(color(tk.face)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round)
      )
    default: c.stroke(P.halo, with: .color(color(tk.glyph)), lineWidth: 2.4)
    }
  }
  static func drawGhost(
    _ context: GraphicsContext, _ f: M.Frame, _ live: GhostFloatWelcomeLive, _ x: Double,
    _ y: Double, _ rotation: Double
  ) {
    let phase = M.tau * 4 * live.amb / M.LOOP
    let puff = f.puff + live.puff
    let sx = 1 + 0.07 * f.inhale - 0.1 * puff
    let sy = 1 - 0.1 * f.inhale + 0.16 * puff
    let hop = f.hop + live.hop
    context.drawLayer { glow in
      glow.addFilter(.blur(radius: 14))
      let alpha = 0.025 + 0.02 * sin(M.tau * 2 * live.amb / M.LOOP) + 0.12 * max(0, puff)
      glow.fill(
        circle(x, y - hop, 44),
        with: .color(Color(.sRGB, red: 0.85, green: 0.82, blue: 1, opacity: alpha)))
    }
    var c = transformed(context, x, y - hop + 30)
    c.scaleBy(x: sx, y: sy)
    c.translateBy(x: 0, y: -30)
    c.rotate(by: .degrees(rotation - 8 + f.roll + live.roll))
    let w0 = 3.2 * sin(phase)
    let w1 = 3.2 * sin(phase + 2.1)
    let w2 = 3.2 * sin(phase + 4.2)
    var body = Path()
    body.move(to: CGPoint(x: -38, y: 14))
    body.addCurve(
      to: CGPoint(x: 2, y: -34), control1: CGPoint(x: -40, y: -14),
      control2: CGPoint(x: -20, y: -34))
    body.addCurve(
      to: CGPoint(x: 38, y: 10), control1: CGPoint(x: 26, y: -34), control2: CGPoint(x: 40, y: -14))
    body.addLine(to: CGPoint(x: 38, y: 20))
    body.addQuadCurve(to: CGPoint(x: 14, y: 24), control: CGPoint(x: 28, y: 36 + w0))
    body.addQuadCurve(to: CGPoint(x: -12, y: 23), control: CGPoint(x: 1, y: 36 + w1))
    body.addQuadCurve(to: CGPoint(x: -38, y: 14), control: CGPoint(x: -28, y: 33 + w2))
    body.closeSubpath()
    c.fill(body, with: .color(color(C.ghost)))
    let lx = live.lookX * 3.2
    let ly = live.lookY * 2.6
    let wide = max(f.wide, live.wide)
    let happy = f.happy
    for e in 0..<2 {
      let ex = (e == 0 ? 7.0 : 20.0) + lx
      let ey = -9 + ly
      if happy > 0.5 {
        var arc = Path()
        arc.move(to: CGPoint(x: ex - 4.6, y: ey + 2))
        arc.addQuadCurve(to: CGPoint(x: ex + 4.6, y: ey + 2), control: CGPoint(x: ex, y: ey - 5.5))
        c.stroke(
          arc, with: .color(color(C.eye, (happy - 0.5) * 2)),
          style: StrokeStyle(lineWidth: 3, lineCap: .round))
      } else {
        let k = 1 + 0.32 * wide
        let open =
          max(0.08, 1 - f.blink) * (1 - 0.35 * f.inhale)
          * (1 - happy * 2 > 0 ? 1 - happy * 2 : 0.05)
        c.fill(
          Path(
            ellipseIn: CGRect(
              x: ex - 4.3 * k, y: ey - 6.3 * k * open, width: 8.6 * k, height: 12.6 * k * open)),
          with: .color(color(C.eye)))
      }
    }
    let mouth = max(wide, max(0, puff)) * (1 - happy)
    if mouth > 0.05 {
      c.fill(
        Path(
          ellipseIn: CGRect(
            x: 13.5 + lx - 3.4 * mouth, y: 4 + ly - 4 * mouth, width: 6.8 * mouth, height: 8 * mouth
          )), with: .color(color(C.eye)))
    }
  }
  static func ring(_ c: GraphicsContext, _ x: Double, _ y: Double, _ p: Double) {
    if p < 0 { return }
    let q = 1 - pow(1 - p, 3)
    c.drawLayer { ring in
      ring.addFilter(.blur(radius: 4))
      ring.stroke(
        circle(x, y, M.mix(30, 270, q)),
        with: .color(Color(.sRGB, red: 0.86, green: 0.82, blue: 1, opacity: 0.3 * (1 - p))),
        lineWidth: M.mix(14, 1.5, q))
    }
  }
  static func controls(_ context: GraphicsContext, _ f: M.Frame, _ live: GhostFloatWelcomeLive) {
    label(context, R.title, 201, Theme.titleY, 0.5, .white)
    label(context, R.sub1, 201, Theme.sub1Y, 0.5, color(C.sub))
    label(context, R.sub2, 201, Theme.sub2Y, 0.5, color(C.sub))
    let pillW = 15.8
    let dotH = 5.3
    let gap = 9.9
    let total = pillW + 5 * gap
    var dotX = 201 - total / 2
    context.fill(
      Path(
        roundedRect: CGRect(x: dotX, y: Theme.dotsY - dotH / 2, width: pillW, height: dotH),
        cornerRadius: dotH / 2), with: .color(color(C.pill)))
    dotX += pillW + gap - dotH / 2
    for _ in 1..<6 {
      context.fill(circle(dotX, Theme.dotsY, dotH / 2), with: .color(color(C.dot)))
      dotX += gap
    }
    let agree = live.agree >= 0 ? live.agree : f.tick
    let ck = Theme.check
    let check = transformed(context, ck.x, ck.y, 0, 1 - 0.12 * f.press)
    check.stroke(
      circle(0, 0, ck.r - 0.85), with: .color(mix(C.ring, C.lavender, agree)), lineWidth: 1.7)
    if agree > 0.001 {
      check.fill(
        circle(0, 0, (ck.r - 0.2) * (0.6 + 0.4 * min(1, agree * 1.3))),
        with: .color(color(C.lavender, min(1, agree * 1.4))))
      check.stroke(
        P.check.trimmedPath(from: 0, to: min(1, max(0, (agree - 0.25) / 0.75))),
        with: .color(color(C.bg)),
        style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
    }
    if live.noP >= 0 {
      context.stroke(
        circle(ck.x, ck.y, ck.r + 10 * live.noP),
        with: .color(color(C.lavender, 0.7 * (1 - live.noP))), lineWidth: 2)
    }
    label(context, R.agree, Theme.agreeX, ck.y, 0, color(C.grey))
    label(context, R.terms, Theme.agreeX + R.agree.width, ck.y, 0, color(C.lavender))
    let b = Theme.button
    let buttonOn = live.agree >= 0 ? live.agree : f.button
    let button = transformed(
      context, b.x + b.w / 2 + 7 * live.no, b.y + b.h / 2, 0, 1 - 0.04 * live.btnPress)
    let rect = CGRect(x: -b.w / 2, y: -b.h / 2, width: b.w, height: b.h)
    let round = Path(roundedRect: rect, cornerRadius: b.h / 2)
    button.fill(round, with: .color(mix(C.btnOff, C.btnOn, buttonOn)))
    if f.sheen >= 0 && live.agree < 0 {
      let sx = M.mix(-b.w / 2 - 60, b.w / 2 + 60, f.sheen)
      var sheen = button
      sheen.clip(to: round)
      sheen.blendMode = .plusLighter
      sheen.fill(
        Path(rect),
        with: .linearGradient(
          Gradient(stops: [
            .init(color: .white.opacity(0), location: 0),
            .init(color: .white.opacity(0.35), location: 0.5),
            .init(color: .white.opacity(0), location: 1),
          ]), startPoint: CGPoint(x: sx - 50, y: 0), endPoint: CGPoint(x: sx + 50, y: 10)))
    }
    label(button, R.create, 0, 0, 0.5, mix(C.btnOffInk, C.btnOnInk, buttonOn))
    label(context, R.have, 201, Theme.haveY, 0.5, color(C.have))
    context.stroke(
      circle(Theme.help.x, Theme.help.y, Theme.help.r), with: .color(color(C.have)), lineWidth: 1.5)
    label(context, R.help, Theme.help.x, Theme.help.y, 0.5, color(C.have))
  }
}
