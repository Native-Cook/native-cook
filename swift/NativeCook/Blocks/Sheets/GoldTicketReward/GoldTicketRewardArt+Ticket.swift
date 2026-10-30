import SwiftUI

extension GoldTicketRewardArt {
  func drawAura(_ context: GraphicsContext, _ f: F) {
    if f.aura <= 0.005 { return }
    let box = f.ticket
    let cx = box.x + box.w / 2
    let cy = box.y + box.h / 2
    var c = context
    c.blendMode = .screen
    c.clip(to: rect(cx - box.w, cy - box.w * 0.7, box.w * 2, box.w * 1.4))
    c.translateBy(x: cx, y: cy)
    c.scaleBy(x: 1, y: 0.62)
    c.translateBy(x: -cx, y: -cy)
    c.fill(
      rect(cx - box.w, cy - box.w * 2, box.w * 2, box.w * 4),
      with: radial([(0xffb048, min(0.75, f.aura), 0.25), (0xffb048, 0, 1)], cx, cy, box.w * 0.85))
  }
  func drawTicket(_ context: GraphicsContext, _ f: F, _ live: Live) {
    let box = f.ticket
    let cx = box.x + box.w / 2
    let cy = box.y + box.h / 2
    var c = context
    c.translateBy(x: cx, y: cy + f.tDy)
    // Project the entire ticket (including its cradle, type, shadows and coin), exactly as
    // RN's perspective(520) · rotateX(-tiltY*.2) · rotateY(tiltX*.24) matrix.
    let rx = -live.tiltY * 0.2
    let ry = live.tiltX * 0.24
    var transform = ProjectionTransform(CGAffineTransform.identity)
    transform.m11 = cos(ry)
    transform.m12 = sin(rx) * sin(ry)
    transform.m13 = cos(rx) * sin(ry) / 520
    transform.m21 = 0
    transform.m22 = cos(rx)
    transform.m23 = -sin(rx) / 520
    c.addFilter(.projectionTransform(transform))
    c.drawLayer { layer in
      var ticket = layer
      ticket.scaleBy(x: f.tScaleX, y: f.tScaleY)
      ticket.translateBy(x: -cx, y: -cy)
      drawTicketBody(ticket, f, live)
    }
  }
  func drawTicketBody(_ context: GraphicsContext, _ f: F, _ live: Live) {
    let box = f.ticket
    let cy = box.y + box.h / 2
    let rad = A.radius
    let progress = (box.w - A.ticket.w) / (B.ticket.w - A.ticket.w)
    let rounded = rect(box.x, box.y, box.w, box.h, rad)
    if 1 - f.lift > 0.01 {
      var shadow = context
      shadow.addFilter(.blur(radius: 7))
      shadow.fill(
        rect(box.x, box.y + 3, box.w, box.h, rad),
        with: .color(Self.color(0x5a461e, 0.16 * (1 - f.lift))))
      if f.halo > 0.01 {
        shadow.fill(
          circle(A.coin.x, A.coin.y + 2, A.halo * f.halo),
          with: .color(Self.color(0x5a461e, 0.16 * (1 - f.lift))))
      }
    }
    if f.lift > 0.01 {
      var shadow = context
      shadow.addFilter(.blur(radius: 13))
      shadow.fill(
        rect(box.x + 6, box.y + 12, box.w - 12, box.h, rad),
        with: .color(Self.color(0x1a0656, 0.55 * f.lift)))
      shadow.fill(
        rect(box.x - 4, box.y - 2, box.w + 8, box.h + 4, rad + 4),
        with: .color(Self.color(0xffb347, 0.2 * f.lift)))
    }
    var notches = circle(box.x, cy, f.notch)
    notches.addPath(circle(box.x + box.w, cy, f.notch))
    var c = context
    c.clip(to: notches, options: .inverse)
    if f.halo > 0.01 { c.fill(circle(A.coin.x, A.coin.y, A.halo * f.halo), with: .color(.white)) }
    c.fill(rounded, with: .color(.white))
    let goldOn = f.gold > 0.001
    if goldOn {
      var foil = c
      foil.clip(to: rounded)
      foil.fill(
        Path(box.rect),
        with: .shader(
          ShaderLibrary.GoldTicketRewardFoil(
            .float4(box.x, box.y, box.w, box.h), .float(f.gold), .float(f.sweep),
            .float2(live.tiltX, live.tiltY))))
    }
    let winX = M.mix(A.win.x, B.win.x, progress)
    let winY = M.mix(A.win.y, B.win.y, progress)
    let prizeX = M.mix(A.prize.x, B.prize.x, progress)
    let prizeY = M.mix(A.prize.y, B.prize.y, progress)
    let winA = resources.labels["winA"]!
    let winB = resources.labels["winB"]!
    let prizeA = resources.labels["prizeA"]!
    let prizeB = resources.labels["prizeB"]!
    let winW = M.mix(winA.width, winB.width, progress)
    let prizeW = M.mix(prizeA.width, prizeB.width, progress)
    let edge = M.mix(-0.4, 1.4, f.gold)
    func xAt(_ v: Double) -> Double { box.x + box.w * (edge - (v - 0.5) * 0.35) }
    var side = Path()
    side.move(to: CGPoint(x: box.x - 400, y: box.y - 200))
    side.addLine(to: CGPoint(x: xAt(-200 / box.h), y: box.y - 200))
    side.addLine(to: CGPoint(x: xAt(1 + 200 / box.h), y: box.y + box.h + 200))
    side.addLine(to: CGPoint(x: box.x - 400, y: box.y + box.h + 200))
    side.closeSubpath()
    if f.gold < 0.999 {
      var a = c
      if goldOn { a.clip(to: side, options: .inverse) }
      text(a, "winA", winX, winY, 0.5, winW / winA.width, .color(Self.color(C.winA)))
      text(
        a, "prizeA", prizeX, prizeY, 0.5, prizeW / prizeA.width,
        linear(
          [(C.prizeA[0], 1, 0), (C.prizeA[1], 1, 0.5), (C.prizeA[2], 1, 1)], -H.WIDTH.prizeA / 2, 0,
          H.WIDTH.prizeA / 2, 0))
    }
    if goldOn {
      var b = c
      b.clip(to: side)
      text(
        b, "prizeB", prizeX, prizeY + 1.2, 0.5, prizeW / prizeB.width,
        .color(Self.color(0x7a3f05, 0.35)))
      text(b, "winB", winX, winY, 0.5, winW / winB.width, .color(Self.color(C.winB, 0.95)))
      text(b, "prizeB", prizeX, prizeY, 0.5, prizeW / prizeB.width, .color(Self.color(C.prizeB)))
    }
    drawCoin(context, f, live)
  }
  func drawCoin(_ context: GraphicsContext, _ f: F, _ live: Live) {
    let coin = f.coin
    let x = coin.x + live.tiltX * 3 * f.bAmount
    let y = coin.y + live.tiltY * 2 * f.bAmount
    let sw = coin.r * (0.9 + coin.shadow * 0.7)
    let sy = max(f.ticket.y + 6, y + coin.r * 0.55 + coin.shadow * 30)
    var shadow = context
    shadow.addFilter(.blur(radius: 5))
    shadow.fill(
      Path(
        ellipseIn: CGRect(x: x - sw, y: sy - coin.r * 0.28, width: sw * 2, height: coin.r * 0.56)),
      with: .color(Self.color(f.lift > 0.5 ? 0x5b2a00 : 0x6b5a33, 0.34 * (1 - coin.shadow * 0.75))))
    // JS Math.round uses floor(x+.5), including negative touch spin positions.
    let index = ((Int(floor(coin.frame + 0.5)) % 24) + 24) % 24
    let half = coin.r * 1.1
    context.draw(
      resources.coinFrames[index],
      in: CGRect(x: x - half, y: y - half, width: half * 2, height: half * 2))
    if f.glint > 0 && f.glint < 1 {
      var glint = context
      glint.clip(to: circle(x, y, coin.r * 0.97))
      glint.blendMode = .screen
      let gx = x + M.mix(-1.6, 1.6, f.glint) * coin.r
      glint.fill(
        rect(x - coin.r, y - coin.r, coin.r * 2, coin.r * 2),
        with: linear(
          [(0xfff8dc, 0, 0), (0xfff8dc, 0.85, 0.5), (0xfff8dc, 0, 1)], gx - coin.r * 0.5,
          y - coin.r, gx + coin.r * 0.5, y + coin.r))
    }
    if f.ring.a > 0.01 {
      context.stroke(
        circle(B.coin.x, B.coin.y, f.ring.r), with: .color(Self.color(0xffe7a8, f.ring.a)),
        lineWidth: M.mix(0.8, 2.6, f.ring.a / 0.85))
    }
    if f.flash > 0.01 {
      var flash = context
      flash.blendMode = .plusLighter
      flash.fill(
        circle(B.coin.x, B.coin.y, B.coin.r * 3.4),
        with: radial(
          [(0xffe2a0, 0.75 * f.flash, 0), (0xffe2a0, 0, 1)], B.coin.x, B.coin.y, B.coin.r * 3.4))
    }
  }
}
