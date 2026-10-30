import SwiftUI

// All artwork stays in pin pixels, including icon paths and baseline offsets from cpf-draw.ts.
enum CashbackPickFiveArt {
  typealias M = CashbackPickFiveMotion
  typealias T = CashbackPickFiveTheme
  typealias C = T.C
  typealias UI = T.UI
  typealias Copy = T.Copy
  static let resources = CashbackPickFiveResources.shared

  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }
  static func mixColor(_ a: Int, _ b: Int, _ k: Double) -> Color {
    func channel(_ shift: Int) -> Double {
      M.mix(Double((a >> shift) & 255), Double((b >> shift) & 255), k) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
  }
  static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path
  {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
  }
  static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
  }
  static func line(
    _ canvas: GraphicsContext, _ x: Double, _ y: Double, _ xx: Double, _ yy: Double,
    _ ink: Int, _ width: Double, _ alpha: Double = 1
  ) {
    var path = Path()
    path.move(to: .init(x: x, y: y))
    path.addLine(to: .init(x: xx, y: yy))
    stroke(canvas, path, ink, width, alpha)
  }
  static func stroke(
    _ canvas: GraphicsContext, _ path: Path, _ ink: Int, _ width: Double, _ alpha: Double = 1
  ) {
    canvas.stroke(
      path, with: .color(color(ink, alpha)),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  @discardableResult static func text(
    _ canvas: GraphicsContext, _ string: String, _ x: Double, _ cy: Double,
    _ size: Double, _ weight: String, _ ink: Int, _ alpha: Double = 1, _ ax: Double = 0
  ) -> Double {
    let label = resources.label(string, size, weight)
    if alpha > 0 {
      var context = canvas
      context.opacity *= alpha
      var image = context.resolve(label.image)
      image.shading = .color(color(ink))
      context.draw(
        image,
        in: CGRect(
          x: x - label.width * ax - 12, y: cy + size * 0.36 - label.ascender - 12,
          width: label.size.width, height: label.size.height))
    }
    return label.width
  }
  static func width(_ string: String, _ size: Double, _ weight: String) -> Double {
    resources.label(string, size, weight).width
  }
  static func star4(
    _ canvas: GraphicsContext, _ x: Double, _ y: Double, _ radius: Double, _ alpha: Double
  ) {
    var path = Path()
    path.move(to: .init(x: x, y: y - radius))
    path.addQuadCurve(to: .init(x: x + radius, y: y), control: .init(x: x, y: y))
    path.addQuadCurve(to: .init(x: x, y: y + radius), control: .init(x: x, y: y))
    path.addQuadCurve(to: .init(x: x - radius, y: y), control: .init(x: x, y: y))
    path.addQuadCurve(to: .init(x: x, y: y - radius), control: .init(x: x, y: y))
    path.closeSubpath()
    canvas.fill(path, with: .color(color(C.star, alpha)))
  }
  static func plusMark(_ context: GraphicsContext, _ x: Double, _ y: Double, _ radius: Double) {
    context.fill(
      circle(x, y, radius),
      with: .linearGradient(
        Gradient(stops: [
          .init(color: color(0xFF7EB3), location: 0), .init(color: color(0xB04CFF), location: 0.55),
          .init(color: color(0x6A3BFF), location: 1),
        ]),
        startPoint: .init(x: x - radius, y: y - radius),
        endPoint: .init(x: x + radius, y: y + radius)))
    var canvas = context
    canvas.translateBy(x: x, y: y)
    canvas.rotate(by: .degrees(-18))
    line(canvas, -radius * 0.45, 0, radius * 0.45, 0, 0xFFFFFF, radius * 0.26)
    line(canvas, 0, -radius * 0.45, 0, radius * 0.45, 0xFFFFFF, radius * 0.26)
  }
  static func catIcon(
    _ context: GraphicsContext, _ index: Int, _ x: Double, _ y: Double, _ radius: Double
  ) {
    context.fill(circle(x, y, radius), with: .color(color(T.cats[index].color)))
    let scale = radius / 32
    var canvas = context
    canvas.translateBy(x: x, y: y)
    canvas.scaleBy(x: scale, y: scale)
    let white = 0xFFFFFF
    // The RN stroke is established before the icon transform, so preserve its extra scale.
    let ws = 3.4 * scale
    var path = Path()
    switch index {
    case 0:
      stroke(canvas, rect(-11, -15, 22, 30, 5), white, 3)
      text(canvas, "%", 0, 1, 26, "Bold", 0x7A3F8E, 1, 0.5)
    case 1:
      line(canvas, -7, -14, -7, 14, white, ws)
      line(canvas, -11, -14, -11, -4, white, 2.4)
      line(canvas, -3, -14, -3, -4, white, 2.4)
      path.move(to: .init(x: 7, y: 14))
      path.addLine(to: .init(x: 7, y: -14))
      path.addQuadCurve(to: .init(x: 12, y: 2), control: .init(x: 14, y: -8))
      path.addLine(to: .init(x: 7, y: 2))
      canvas.fill(path, with: .color(color(white)))
      line(canvas, 7, 0, 7, 14, white, ws)
    case 2:
      path.move(to: .init(x: 0, y: -6))
      path.addCurve(
        to: .init(x: -6, y: 14), control1: .init(x: -16, y: -14), control2: .init(x: -18, y: 8))
      path.addQuadCurve(to: .init(x: 6, y: 14), control: .init(x: 0, y: 11))
      path.addCurve(
        to: .init(x: 0, y: -6), control1: .init(x: 18, y: 8), control2: .init(x: 16, y: -14))
      path.closeSubpath()
      canvas.fill(path, with: .color(color(white)))
      line(canvas, 1, -7, 4, -15, white, 2.6)
    case 3:
      path.move(to: .init(x: -6, y: -13))
      path.addQuadCurve(to: .init(x: 6, y: -13), control: .init(x: 0, y: -9))
      for point in [
        (15.0, -7.0), (11, -1), (8, -3), (8, 13), (-8, 13), (-8, -3), (-11, -1), (-15, -7),
      ] {
        path.addLine(to: .init(x: point.0, y: point.1))
      }
      path.closeSubpath()
      canvas.fill(path, with: .color(color(white)))
    case 4:
      canvas.clip(to: circle(0, 0, 32))
      path.move(to: .init(x: -32, y: 32))
      path.addLine(to: .init(x: 32, y: 32))
      path.addLine(to: .init(x: -32, y: -2))
      path.closeSubpath()
      canvas.fill(path, with: .color(color(0x16161A)))
      for q in 0..<3 {
        canvas.fill(rect(-26 + Double(q) * 12, 18, 6, 6), with: .color(color(0xFFD60A)))
      }
    case 5:
      canvas.fill(rect(-14, -6, 28, 18, 3), with: .color(color(0xFF7A1A)))
      path.move(to: .init(x: -14, y: -9))
      path.addLine(to: .init(x: 14, y: -15))
      path.addLine(to: .init(x: 15, y: -10))
      path.addLine(to: .init(x: -13, y: -4))
      path.closeSubpath()
      canvas.fill(path, with: .color(color(0xFF7A1A)))
      line(canvas, -6, -10, -2, -6, 0x16161A, 2)
      line(canvas, 3, -12, 7, -8, 0x16161A, 2)
    case 6:
      canvas.fill(rect(-4.5, -14, 9, 28, 2), with: .color(color(white)))
      canvas.fill(rect(-14, -4.5, 28, 9, 2), with: .color(color(white)))
    case 7:
      canvas.fill(rect(-6, -2, 12, 16, 2), with: .color(color(white)))
      path.move(to: .init(x: -5, y: -2))
      path.addLine(to: .init(x: -5, y: -10))
      path.addLine(to: .init(x: 5, y: -16))
      path.addLine(to: .init(x: 5, y: -2))
      path.closeSubpath()
      canvas.fill(path, with: .color(color(white)))
    default:
      path.move(to: .init(x: 0, y: -15))
      path.addCurve(
        to: .init(x: 12, y: 6), control1: .init(x: 6, y: -6), control2: .init(x: 12, y: 0))
      path.addCurve(
        to: .init(x: 0, y: 16), control1: .init(x: 12, y: 13), control2: .init(x: 6, y: 16))
      path.addCurve(
        to: .init(x: -12, y: 6), control1: .init(x: -6, y: 16), control2: .init(x: -12, y: 13))
      path.addCurve(
        to: .init(x: 0, y: -15), control1: .init(x: -12, y: 0), control2: .init(x: -6, y: -6))
      path.closeSubpath()
      canvas.fill(path, with: .color(color(white)))
    }
  }
  static func checkbox(
    _ context: GraphicsContext, _ x: Double, _ y: Double, _ on: Double,
    _ at: Double, _ now: Double, _ nope: Double
  ) {
    let progress = M.clamp01((now - at) / 0.3)
    let pop = at > M.NEVER ? sin(.pi * progress) * (1 - progress) : 0
    let scale = 1 + 0.18 * pop
    let shake = nope > 0 ? 7 * sin(nope * 40) * exp(-nope * 6) : 0
    var canvas = context
    canvas.translateBy(x: x + shake, y: y)
    canvas.scaleBy(x: scale, y: scale)
    canvas.fill(rect(-24, -24, 48, 48, 10), with: .color(mixColor(C.box, C.boxOn, on)))
    if nope > 0 && nope < 0.5 {
      stroke(canvas, rect(-24, -24, 48, 48, 10), 0xFF5A5A, 3, 1 - nope * 2)
    }
    if on > 0.02 {
      let k = M.clamp01(on * 1.2)
      let l1 = hypot(7.0, 8.0)
      let l2 = hypot(12.0, -16.0)
      let distance = k * (l1 + l2)
      var path = Path()
      path.move(to: .init(x: -9, y: 0))
      if distance <= l1 {
        path.addLine(to: .init(x: M.mix(-9, -2, distance / l1), y: M.mix(0, 8, distance / l1)))
      } else {
        path.addLine(to: .init(x: -2, y: 8))
        path.addLine(
          to: .init(x: M.mix(-2, 10, (distance - l1) / l2), y: M.mix(8, -8, (distance - l1) / l2)))
      }
      stroke(canvas, path, 0xFFFFFF, 3.6)
    }
  }
  static func rowY(_ index: Int, _ scroll: Double) -> Double {
    UI.list.top + Double(index) * UI.list.pitch - scroll
  }
  static func panelK(_ frame: M.Frame) -> Double {
    if frame.state.panelAt <= M.NEVER { return 0 }
    let k = M.spring(frame.now - frame.state.panelAt, 0.5, 0.86)
    return frame.state.panelUp ? k : 1 - k
  }
  static func slotPos(_ index: Int, _ k: Double) -> CGPoint {
    let button = UI.button
    return .init(
      x: button.x + button.w - 48 - Double(T.MAX_PICKS - 1 - index) * 52,
      y: M.mix(UI.disabled.y + UI.disabled.h / 2, button.y + button.h / 2, k))
  }
  static func drawScene(_ context: GraphicsContext, _ size: CGSize, _ frame: M.Frame) {
    var canvas = context
    let scale = size.width / T.REF_W * T.S
    canvas.scaleBy(x: scale, y: scale)
    let height = size.height / scale
    canvas.fill(rect(0, 0, T.PIN_W, height), with: .color(color(C.bg)))
    drawPage(canvas, height, frame)
    let back = UI.back
    line(canvas, back.x - 14, back.y, back.x + 14, back.y, C.ink, 3.6)
    line(canvas, back.x - 14, back.y, back.x - 4, back.y - 10, C.ink, 3.6)
    line(canvas, back.x - 14, back.y, back.x - 4, back.y + 10, C.ink, 3.6)
    let brandWidth = width(Copy.brand, 33, "ExtraBold")
    plusMark(canvas, UI.logo.x - brandWidth / 2 - 8, UI.logo.y, 20)
    text(canvas, Copy.brand, UI.logo.x - brandWidth / 2 + 18, UI.logo.y, 33, "ExtraBold", C.ink)
    drawBottom(canvas, height, frame)
    drawFlights(canvas, frame)
  }
  static func drawPage(_ context: GraphicsContext, _ height: Double, _ frame: M.Frame) {
    var canvas = context
    canvas.clip(to: rect(0, 222, T.PIN_W, height))
    canvas.translateBy(x: 0, y: -frame.scroll)
    for index in 0..<2 {
      text(canvas, Copy.title[index], UI.title.x, UI.title.y[index], 44, "Bold", C.ink)
    }
    let banner = UI.banner
    canvas.fill(
      rect(banner.x, banner.y, banner.w, banner.h, 30),
      with: .linearGradient(
        Gradient(colors: [color(C.bannerA), color(C.bannerB)]),
        startPoint: .init(x: banner.x, y: 0), endPoint: .init(x: banner.x + banner.w, y: 0)))
    plusMark(canvas, 91, banner.y + 67, 33)
    let bannerWidth = text(canvas, Copy.banner, 150, banner.y + 50, 30, "Regular", C.ink)
    stroke(canvas, circle(150 + bannerWidth + 18, banner.y + 50, 10), C.grey, 2)
    let chipWidth = width(Copy.bannerSub, 21, "Regular")
    canvas.fill(rect(146, banner.y + 70, chipWidth + 16, 32, 10), with: .color(color(C.chip)))
    text(canvas, Copy.bannerSub, 154, banner.y + 86, 21, "Regular", 0x5C5470)
    canvas.fill(rect(630, banner.y + 43, 48, 48, 10), with: .color(color(0xE7E2F6)))
    let twinkle = 2 * Double.pi * frame.amb / M.LOOP
    let stars: [(Double, Double, Double)] = [
      (520, 24, 0), (600, 104, 1.7), (460, 112, 3.1), (640, 22, 4.4), (570, 58, 5.6),
    ]
    for (index, star) in stars.enumerated() {
      let tk = max(0, sin(twinkle * 2 + star.2))
      let burst =
        frame.sparkle >= 0 ? sin(.pi * M.clamp01((frame.sparkle - Double(index) * 0.08) / 0.7)) : 0
      let alpha = 0.35 * tk * tk + burst
      if alpha > 0.02 {
        star4(canvas, star.0, banner.y + star.1, 6 + 8 * burst + 2 * tk, min(1, alpha))
      }
    }
    let sectionWidth = text(canvas, Copy.section, 44, UI.sectionY, 30, "Medium", C.ink)
    canvas.fill(
      rect(44 + sectionWidth + 14, UI.sectionY - 17, 34, 34, 8), with: .color(color(C.pay)))
    text(canvas, "O", 44 + sectionWidth + 31, UI.sectionY, 26, "Bold", 0xFFFFFF, 1, 0.5)
    text(canvas, Copy.pay, 44 + sectionWidth + 58, UI.sectionY, 30, "Medium", C.ink)
    let list = UI.list
    canvas.fill(
      rect(list.x, list.y, list.w, Double(T.cats.count) * list.pitch + 40, 30),
      with: .color(color(C.card)))
    for (index, cat) in T.cats.enumerated() {
      let cy = list.top + Double(index) * list.pitch
      let ty = cat.sub == nil ? cy : cy - 12
      catIcon(canvas, index, list.iconX, cy, list.iconR)
      let rowWidth = text(canvas, "\(cat.pct) \(cat.name)", list.textX, ty, 29, "Regular", C.ink)
      stroke(canvas, circle(list.textX + rowWidth + 18, ty, 10), 0xB8BCC8, 2)
      text(canvas, "?", list.textX + rowWidth + 18, ty, 21, "Regular", 0xB8BCC8, 1, 0.5)
      if let sub = cat.sub { text(canvas, sub, list.textX, cy + 20, 21, "Regular", C.grey) }
      let on =
        frame.state.checked[index]
        ? M.smooth((frame.now - frame.state.checkAt[index]) / 0.18)
        : 1 - M.smooth((frame.now - frame.state.checkAt[index]) / 0.18)
      let value =
        frame.state.checkAt[index] <= M.NEVER ? (frame.state.checked[index] ? 1.0 : 0.0) : on
      checkbox(
        canvas, list.boxX, cy, value, frame.state.checkAt[index], frame.now,
        frame.state.nopeRow == index ? frame.now - frame.state.nopeAt : -1)
    }
  }
  static func drawBottom(_ context: GraphicsContext, _ height: Double, _ frame: M.Frame) {
    let k = panelK(frame)
    let disabled = UI.disabled
    let button = UI.button
    if k < 0.999 {
      context.fill(rect(0, disabled.y - 40, T.PIN_W, height), with: .color(color(C.bg, 1 - k)))
    }
    if k > 0.001 {
      let top = M.mix(height + 40, UI.panelY, k)
      var shadow = context
      shadow.addFilter(.blur(radius: 18))
      shadow.fill(rect(0, top, T.PIN_W, height - top + 60, 36), with: .color(color(0x2A2F45, 0.12)))
      context.fill(rect(0, top, T.PIN_W, height - top + 60, 36), with: .color(color(C.panel)))
      let alpha = M.smooth((k - 0.4) / 0.6)
      let bx = 64.0
      let by = UI.toggle + (1 - k) * 60
      var bolt = Path()
      bolt.move(to: .init(x: bx + 4, y: by - 18))
      for point in [
        CGPoint(x: bx - 8, y: by + 3), .init(x: bx, y: by + 3), .init(x: bx - 4, y: by + 18),
        .init(x: bx + 8, y: by - 3), .init(x: bx, y: by - 3),
      ] { bolt.addLine(to: point) }
      bolt.closeSubpath()
      context.fill(bolt, with: .color(color(C.purple, alpha)))
      for index in 0..<2 {
        text(
          context, Copy.connect[index], 104, by - 14 + Double(index) * 30, 25, "Regular", C.ink,
          alpha)
      }
      context.fill(rect(612, by - 23, 82, 46, 23), with: .color(color(C.purple, alpha)))
      context.fill(circle(671, by, 18), with: .color(color(0xFFFFFF, alpha)))
      for index in 0..<2 {
        text(
          context, Copy.fine[index], T.PIN_W / 2, UI.fine[index] + (1 - k) * 60, 17, "Regular",
          C.fine, alpha, 0.5)
      }
    }
    let bx = M.mix(disabled.x, button.x, k)
    let by = M.mix(disabled.y, button.y, k)
    let nope = frame.state.nopeAt > M.NEVER ? frame.now - frame.state.nopeAt : -1
    let shake = nope >= 0 ? 11 * sin(nope * 42) * exp(-nope * 6) : 0
    var canvas = context
    canvas.translateBy(x: shake, y: 0)
    canvas.fill(
      rect(bx, by, button.w, button.h, 30), with: .color(mixColor(C.disabled, C.button, k)))
    if frame.state.picks.count == T.MAX_PICKS && frame.state.countAt > M.NEVER
      && frame.now - frame.state.countAt < 1.2
    {
      let q = (frame.now - frame.state.countAt) / 1.2
      var sheen = canvas
      sheen.clip(to: rect(bx, by, button.w, button.h, 30))
      let gx = M.mix(bx - 120, bx + button.w + 120, M.easeInOut(q))
      sheen.fill(
        rect(gx - 90, by, 180, button.h),
        with: .linearGradient(
          Gradient(colors: [color(0xFFFFFF, 0), color(0xB48CFF, 0.35), color(0xFFFFFF, 0)]),
          startPoint: .init(x: gx - 90, y: 0), endPoint: .init(x: gx + 90, y: 0)))
    }
    let cy = by + button.h / 2
    if k < 0.5 {
      text(canvas, Copy.choose, bx + button.w / 2, cy, 32, "Medium", C.disabledText, 1 - k * 2, 0.5)
    }
    if k > 0.2 {
      let count = frame.state.picks.count
      let from = frame.state.countFrom
      let roll =
        frame.state.countAt > M.NEVER ? M.smooth((frame.now - frame.state.countAt) / 0.28) : 1
      let alpha = M.smooth((k - 0.2) / 0.5)
      let lead = "Choose"
      let tail = "of \(T.MAX_PICKS)"
      let space = width(" ", 32, "Medium")
      let gap = space > 0 ? space : 32 * 0.28
      let lw = width(lead, 32, "Medium") + gap
      let nw = width("0", 32, "Medium")
      let tw = width(tail, 32, "Medium") + gap
      let tx = bx + 200 - (lw + nw + tw) / 2
      text(canvas, lead, tx, cy, 32, "Medium", 0xFFFFFF, alpha)
      var counter = canvas
      counter.clip(to: rect(tx + lw - 2, cy - 26, nw + 4, 52))
      let direction = count > from ? 1.0 : -1.0
      if roll < 1 && count != from {
        text(
          counter, String(max(0, from)), tx + lw, cy - direction * 40 * roll, 32, "Medium",
          0xFFFFFF, alpha)
        text(
          counter, String(count), tx + lw, cy + direction * 40 * (1 - roll), 32, "Medium", 0xFFFFFF,
          alpha)
      } else {
        text(counter, String(max(1, count)), tx + lw, cy, 32, "Medium", 0xFFFFFF, alpha)
      }
      text(canvas, tail, tx + lw + nw + gap, cy, 32, "Medium", 0xFFFFFF, alpha)
      for index in 0..<T.MAX_PICKS {
        let point = slotPos(index, k)
        stroke(canvas, circle(point.x, point.y, 21), 0xFFFFFF, 2, 0.28 * M.smooth((k - 0.3) / 0.7))
      }
      for (slot, row) in frame.state.picks.enumerated() {
        let flying = frame.state.flights.contains {
          $0.row == row && $0.dir == 1 && frame.now - $0.at < M.FLY
        }
        if flying { continue }
        let slide =
          frame.state.slotAt[row] > M.NEVER
          ? M.spring(frame.now - frame.state.slotAt[row], 0.4, 0.75) : 1
        let fromPoint = slotPos(frame.state.slotFrom[row], k)
        let to = slotPos(slot, k)
        var land = 0.0
        for flight in frame.state.flights where flight.row == row && flight.dir == 1 {
          land = frame.now - flight.at - M.FLY
        }
        let squash = land >= 0 && land < 0.35 ? sin(.pi * land / 0.35) * (1 - land / 0.35) : 0
        if land >= 0 && land < 0.45 {
          let lq = land / 0.45
          stroke(canvas, circle(to.x, to.y, 21 + 14 * M.easeOut(lq)), 0xB48CFF, 3 * (1 - lq), 0.9 * (1 - lq))
        }
        var icon = canvas
        icon.translateBy(x: M.mix(fromPoint.x, to.x, slide), y: M.mix(fromPoint.y, to.y, slide))
        icon.scaleBy(x: 1 + 0.25 * squash, y: 1 - 0.18 * squash)
        catIcon(icon, row, 0, 0, 19)
      }
    }
  }
  static func drawFlights(_ context: GraphicsContext, _ frame: M.Frame) {
    let k = panelK(frame)
    for flight in frame.state.flights {
      let progress = (frame.now - flight.at) / M.FLY
      if progress < 0 || progress >= 1 { continue }
      let eased = M.easeInOut(progress)
      let row = CGPoint(x: UI.list.iconX, y: rowY(flight.row, frame.scroll))
      let slot = slotPos(flight.dir == 1 ? flight.to : flight.from, k)
      let a = flight.dir == 1 ? row : slot
      let b = flight.dir == 1 ? slot : row
      let controlX = (a.x + b.x) / 2
      let controlY = min(a.y, b.y) - 240
      let inverse = 1 - eased
      let x = inverse * inverse * a.x + 2 * inverse * eased * controlX + eased * eased * b.x
      let y = inverse * inverse * a.y + 2 * inverse * eased * controlY + eased * eased * b.y
      for g in stride(from: 3, through: 1, by: -1) {
        let eg = M.easeInOut(max(0, progress - Double(g) * 0.055))
        let inv = 1 - eg
        let gx = inv * inv * a.x + 2 * inv * eg * controlX + eg * eg * b.x
        let gy = inv * inv * a.y + 2 * inv * eg * controlY + eg * eg * b.y
        let gr = (flight.dir == 1 ? M.mix(32, 19, eg) : M.mix(19, 32, eg)) * (1 - Double(g) * 0.12)
        context.fill(circle(gx, gy, gr), with: .color(color(T.cats[flight.row].color, 0.16 * Double(4 - g) * sin(.pi * progress))))
      }
      let radius = flight.dir == 1 ? M.mix(32, 19, eased) : M.mix(19, 32, eased)
      let lift = sin(.pi * progress)
      var canvas = context
      canvas.translateBy(x: x, y: y)
      canvas.rotate(by: .degrees(Double(flight.dir) * 360 * M.easeOut(progress) * 0.5))
      canvas.scaleBy(x: 1 + 0.25 * lift, y: 1 + 0.25 * lift)
      var shadow = canvas
      shadow.addFilter(.blur(radius: 10))
      shadow.fill(circle(0, 14 * lift, radius), with: .color(color(0x2A2F45, 0.18 * lift)))
      catIcon(canvas, flight.row, 0, 0, radius)
    }
  }
}
