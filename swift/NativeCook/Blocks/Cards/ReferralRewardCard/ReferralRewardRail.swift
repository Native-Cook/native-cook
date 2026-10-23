import SwiftUI

struct ReferralRewardRail: View {
  let model: ReferralRewardModel
  @GestureState private var touching = false
  private static let images = ReferralRewardFriend.all.map { Image(uiImage: $0.image) }
  private static let dim = [0.74, 0.74, 0.52, 0.3, 0.3]
  private static let soft = [0.0, 0, 1.1, 3, 4.5]
  private static let edgeFade = Gradient(stops: [
    .init(color: .clear, location: 0), .init(color: .black, location: 0.3),
    .init(color: .black, location: 0.7), .init(color: .clear, location: 1),
  ])
  private static let lensShade = Gradient(stops: [
    .init(color: .clear, location: 0), .init(color: .clear, location: 0.74),
    .init(color: .black.opacity(0.3), location: 1),
  ])
  private static let check: Path = {
    var path = Path()
    path.move(to: CGPoint(x: -3.3, y: 0.2))
    path.addLine(to: CGPoint(x: -1, y: 2.5))
    path.addLine(to: CGPoint(x: 3.4, y: -2.2))
    return path
  }()
  private static let shine: Path = {
    var path = Path()
    path.addArc(
      center: .zero, radius: 33.5, startAngle: .degrees(-152), endAngle: .degrees(-108),
      clockwise: false)
    return path
  }()
  private static let progressArc: Path = {
    var path = Path()
    path.addArc(
      center: .zero, radius: 42, startAngle: .degrees(-90), endAngle: .degrees(269.99),
      clockwise: false)
    return path
  }()
  private static let tick: Path = {
    var path = Path()
    path.move(to: .zero)
    path.addLine(to: CGPoint(x: 1, y: 0))
    return path
  }()
  var body: some View {
    let _ = model.railClock.frame
    ZStack(alignment: .topLeading) {
      Canvas { [frame = model.railClock.frame] context, _ in
        let _ = frame
        draw(&context)
      }.accessibilityHidden(true)
      Color.clear.contentShape(Rectangle())
        .accessibilityElement(children: .ignore).accessibilityLabel(ReferralRewardL10n.t("friends"))
        .accessibilityValue(model.friendValue)
        .accessibilityAdjustableAction { direction in
          switch direction {
          case .increment: model.step(1)
          case .decrement: model.step(-1)
          @unknown default: break
          }
        }
        .accessibilityAction { model.accessibilityPeek() }
        // A default activation action otherwise infers the button trait.
        .accessibilityRemoveTraits(.isButton)
        .accessibilitySortPriority(60)
      ReferralRewardPoints(model: model).position(x: model.layout.width / 2, y: 37)
        .allowsHitTesting(false)
      ReferralRewardCapsule(model: model)
    }
    .contentShape(Rectangle())
    .gesture(
      DragGesture(minimumDistance: 0).updating($touching) { _, state, _ in state = true }
        .onChanged { model.gestureChanged($0) }.onEnded { model.gestureEnded($0) }
    )
    .onChange(of: touching) { _, active in if !active { model.cancelGesture() } }
  }
  private func draw(_ context: inout GraphicsContext) {
    let width = model.layout.width
    let centerX = width / 2
    let centerY = 112.0
    let peek = model.peek.value
    let closed = 1 - ReferralRewardMath.clamp(peek)
    let arrival = model.arrival
    let loose = model.loose
    let ringWidth = 84 + (width - 48 - 84) * peek
    let ringRect = CGRect(x: centerX - ringWidth / 2, y: centerY - 42, width: ringWidth, height: 84)
    let ringPath = Path(roundedRect: ringRect, cornerRadius: 42)
    context.drawLayer { c in
      c.opacity = arrival * (0.2 + 0.55 * model.glow.value) * (1 - 0.5 * loose)
      c.addFilter(.blur(radius: 18))
      c.fill(Self.circle(centerX, centerY, 34), with: .color(model.tint))
    }
    context.drawLayer { row in
      for i in 0..<7 {
        let offset = ReferralRewardMath.offset(i, model.scroll.value)
        let a = abs(offset)
        let spread = 1 + 0.3 * (1 - arrival)
        let x =
          centerX + (offset >= 0 ? 1.0 : -1.0)
          * ((a * 56 + 6 * ReferralRewardMath.smooth(a)) * spread + (width / 2 - 24 - 36) * peek)
        let soft = model.reduced ? 0 : ReferralRewardMath.ramp(a, Self.soft)
        let opacity =
          max(
            arrival * ReferralRewardMath.ramp(a, Self.dim)
              * (1 - ReferralRewardMath.smooth((a - 3) * 2)), arrival == 0 ? 0.012 : 0) * closed
        guard opacity > 0.001 else { continue }
        var avatar = row
        avatar.opacity = opacity
        avatar.drawLayer { p in
          p.addFilter(.saturation(0.62))
          Self.blur(&p, x: soft + min(model.rush * 0.43, 5), y: soft) { q in
            q.clip(to: Self.circle(x, centerY, 26))
            q.draw(Self.images[i], in: CGRect(x: x - 26, y: centerY - 26, width: 52, height: 52))
          }
        }
        let detailOpacity = max(1 - soft / 1.1, 0)
        avatar.stroke(
          Self.circle(x, centerY, 25.5), with: .color(.white.opacity(0.1 * detailOpacity)),
          lineWidth: 1)
        Self.badge(
          &avatar, x: x + 15, y: centerY + 15, scale: 0.62,
          opacity: ReferralRewardMath.clamp(model.marks[i].value) * detailOpacity, trim: 1)
      }
      row.blendMode = .destinationOut
      row.fill(ringPath, with: .color(.white))
      row.blendMode = .destinationIn
      row.fill(
        Path(CGRect(x: 0, y: 0, width: width, height: 212)),
        with: .linearGradient(Self.edgeFade, startPoint: .zero, endPoint: CGPoint(x: width, y: 0)))
    }
    context.fill(
      ringPath,
      with: .color(Color(red: 4 / 255, green: 4 / 255, blue: 8 / 255, opacity: 0.46 * (1 - closed)))
    )
    context.drawLayer { lens in
      let scale = (1 + 0.05 * model.pop.value) * (0.9 + 0.1 * arrival)
      lens.translateBy(x: centerX, y: centerY)
      lens.scaleBy(x: scale, y: scale)
      lens.translateBy(x: -centerX, y: -centerY)
      lens.opacity = max(arrival, arrival == 0 ? 0.012 : 0)
      let clipWidth = 74 + (width - 48 - 10 - 74) * peek
      lens.clip(
        to: Path(
          roundedRect: CGRect(
            x: centerX - clipWidth / 2, y: centerY - 37, width: clipWidth, height: 74),
          cornerRadius: 37))
      Self.blur(&lens, x: min(model.rush * 0.6, 7), y: 0) { inside in
        for i in 0..<7 {
          let offset = ReferralRewardMath.offset(i, model.scroll.value)
          let x = centerX + offset * 78 - (width / 2 - 66) * peek
          // Include the full three-sigma footprint before culling a portrait.
          guard abs(x - centerX) < clipWidth / 2 + 36 + 21 else { continue }
          var p = inside
          // Portrait, rim and check compose normally within a sample. The blur
          // weights the completed sample, including its transparent badge cut-out.
          p.opacity *= i == model.focus ? 1 : 1 - ReferralRewardMath.clamp(peek / 0.33)
          p.drawLayer { avatar in
            avatar.clip(to: Self.circle(x, centerY, 36))
            avatar.draw(
              Self.images[i], in: CGRect(x: x - 36, y: centerY - 36, width: 72, height: 72))
          }
          p.stroke(Self.circle(x, centerY, 35.5), with: .color(.white.opacity(0.1)), lineWidth: 1)
          let mark = model.marks[i].value
          Self.badge(
            &p, x: x + 19, y: centerY + 19, scale: 0.6 + 0.4 * mark,
            opacity: ReferralRewardMath.clamp(mark * 6),
            trim: ReferralRewardMath.clamp((mark - 0.3) / 0.6))
        }
      }
      var shading = lens
      shading.opacity *= closed
      shading.fill(
        Self.circle(centerX, centerY, 37),
        with: .radialGradient(
          Self.lensShade, center: CGPoint(x: centerX, y: centerY), startRadius: 0, endRadius: 37))
      lens.drawLayer { shine in
        shine.opacity = closed
        shine.addFilter(.blur(radius: 0.8))
        shine.translateBy(x: centerX, y: centerY)
        shine.stroke(
          Self.shine, with: .color(.white.opacity(0.3)),
          style: .init(lineWidth: 1.5, lineCap: .round))
      }
    }
    let wave = model.wave.value
    let crest = model.crest.value
    let ripple = model.ripple.value
    context.stroke(
      Self.circle(centerX, centerY, 42 + 40 * wave),
      with: .color(.white.opacity(0.5 * pow(max(1 - wave, 0), 1.5))),
      lineWidth: 0.5 + 1.5 * (1 - wave))
    context.stroke(
      Self.circle(centerX, centerY, 42 + 58 * crest),
      with: .color(.white.opacity(0.7 * pow(max(1 - crest, 0), 1.4))),
      lineWidth: 0.5 + 2.5 * (1 - crest))
    var ring = context
    let ringScale = (42 + (2.5 * loose - 1.2 * model.lock.value) * closed) / 42
    ring.translateBy(x: centerX, y: centerY)
    ring.scaleBy(x: ringScale, y: ringScale)
    ring.translateBy(x: -centerX, y: -centerY)
    ring.stroke(
      ringPath, with: .color(.white.opacity(0.26 * arrival * (1 - 0.45 * loose))), lineWidth: 1.5)
    ring.stroke(
      Self.circle(centerX, centerY, 42), with: .color(.white.opacity(model.flash.value * closed)),
      lineWidth: 2.5)
    let start = ReferralRewardMath.clamp(model.arcStart.value)
    let end = ReferralRewardMath.clamp(model.arcEnd.value)
    if end - start >= 0.004 {
      ring.translateBy(x: centerX, y: centerY)
      ring.stroke(
        Self.progressArc.trimmedPath(from: start, to: end),
        with: .color(.white.opacity(arrival * (1 - 0.3 * loose) * closed)),
        style: .init(lineWidth: 2 + 1.25 * model.flash.value, lineCap: .round))
    }
    let kick = model.kick.value
    let lock = model.lock.value
    let a = 42 + (13 + 6 * kick + 2 * loose) * closed
    let b = a + max((23 * (1 - 0.5 * loose) + 9 * kick + 4 * lock) * arrival * closed, 0)
    let tickOpacity = ReferralRewardMath.clamp(
      closed * arrival * (0.38 - 0.16 * loose + 0.5 * max(kick, 0) + 0.2 * lock))
    if b > a && tickOpacity > 0 {
      for index in 0..<4 {
        let angle = Double.pi / 4 + Double(index) * Double.pi / 2
        var tick = context
        tick.translateBy(x: centerX + cos(angle) * a, y: centerY + sin(angle) * a)
        tick.rotate(by: .radians(angle))
        tick.scaleBy(x: b - a, y: b - a)
        tick.stroke(
          Self.tick, with: .color(.white.opacity(tickOpacity)),
          style: .init(lineWidth: 1.25 / (b - a), lineCap: .round))
      }
    }
    context.stroke(
      Self.circle(centerX, centerY - 75, 12 + 20 * ReferralRewardMath.out(ripple)),
      with: .color(.white.opacity(0.45 * pow(max(1 - ripple, 0), 1.6))), lineWidth: 1.25)
  }
  static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
  }
  static func badge(
    _ context: inout GraphicsContext, x: Double, y: Double, scale: Double, opacity: Double,
    trim: Double
  ) {
    guard opacity > 0 else { return }
    var c = context
    c.opacity *= opacity
    c.translateBy(x: x, y: y)
    c.scaleBy(x: scale, y: scale)
    c.blendMode = .destinationOut
    c.fill(circle(0, 0, 10.5), with: .color(.white))
    c.blendMode = .normal
    c.fill(circle(0, 0, 8), with: .color(.white))
    c.stroke(
      Self.check.trimmedPath(from: 0, to: trim),
      with: .color(Color(red: 12 / 255, green: 12 / 255, blue: 13 / 255)),
      style: .init(lineWidth: 2, lineCap: .round, lineJoin: .round))
  }
  /// Separable Gaussian: isotropic depth blur followed by an X-only convolution.
  /// The discrete kernel is fixed and normalized; no kernels are allocated per frame.
  private static let weights: [Double] = {
    let kernel = (-9...9).map { exp(-0.5 * pow(Double($0) / 3, 2)) }
    let total = kernel.reduce(0, +)
    return kernel.map { $0 / total }
  }()
  static func blur(
    _ context: inout GraphicsContext, x: Double, y: Double, draw: (inout GraphicsContext) -> Void
  ) {
    let horizontal = sqrt(max(x * x - y * y, 0))
    guard x > 0.01 || y > 0.01 else {
      draw(&context)
      return
    }
    context.drawLayer { layer in
      if y > 0.01 { layer.addFilter(.blur(radius: y)) }
      if horizontal < 0.1 {
        draw(&layer)
      } else {
        layer.drawLayer { samples in
          samples.blendMode = .plusLighter
          for k in -9...9 {
            var weighted = samples
            weighted.opacity *= weights[k + 9]
            weighted.drawLayer { sample in
              // Apply weight when the whole sample is composited. Weighting
              // individual primitives makes dark checks wash out over white
              // badges, even when portrait brightness is normalized.
              sample.opacity = 1
              sample.blendMode = .normal
              sample.translateBy(x: Double(k) * horizontal / 3, y: 0)
              draw(&sample)
            }
          }
        }
      }
    }
  }
}
