import SwiftUI

/// The two light canvases straddle native text: pictograms/heart below, airborne effects above.
struct EmberDrawing: View {
  private typealias E = EmberTheme.E
  private typealias H = EmberTheme.Heart
  private typealias G = EmberTheme.Glyph
  private typealias FX = EmberTheme.Effects

  let frame: EmberFrame
  var airborne = false

  var body: some View {
    Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
      context.translateBy(x: E.bleed, y: E.bleed)
      if airborne {
        effects(context)
      } else {
        glyphs(context)
        heart(context)
      }
    }
    .frame(width: EmberTheme.canvas, height: EmberTheme.canvas)
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }

  private func glyphs(_ context: GraphicsContext) {
    let center = CGPoint(x: E.disc.x + E.disc.d / 2, y: E.disc.y + E.disc.d / 2)
    context.fill(
      Path(ellipseIn: CGRect(x: E.disc.x, y: E.disc.y, width: E.disc.d, height: E.disc.d)),
      with: .radialGradient(
        Gradient(colors: [EmberTone.discTop, EmberTone.discBottom]),
        center: CGPoint(x: center.x, y: center.y - G.discLightRise),
        startRadius: 0, endRadius: E.disc.d / 2 + G.discLightRise
      )
    )

    func glyph(
      _ x: Double, _ y: Double, _ size: Double, _ fills: [Path], _ strokes: [Path], _ color: Color
    ) {
      var glyphContext = context
      glyphContext.translateBy(x: x, y: y)
      glyphContext.scaleBy(x: size / G.grid, y: size / G.grid)
      for path in fills { glyphContext.fill(path, with: .color(color)) }
      for path in strokes {
        glyphContext.stroke(
          path, with: .color(color),
          style: StrokeStyle(lineWidth: G.stroke, lineCap: .round, lineJoin: .round))
      }
    }

    glyph(
      center.x - G.walkInset, center.y - G.walkInset, G.walkSize,
      [EmberPaths.walkFill0, EmberPaths.walkFill1], [], EmberTone.ink)
    glyph(
      E.metrics.x[0], E.metrics.glyphTop, E.metrics.glyph,
      [EmberPaths.routeFill0, EmberPaths.routeFill1], [EmberPaths.routeStroke0], EmberTone.sub)
    glyph(
      E.metrics.x[1], E.metrics.glyphTop, E.metrics.glyph,
      [], [EmberPaths.clockCircle, EmberPaths.clockHands], EmberTone.sub)
    glyph(
      E.metrics.x[2], E.metrics.glyphTop, E.metrics.glyph, [EmberPaths.boltFill0], [], EmberTone.sub
    )
  }

  private func heart(_ context: GraphicsContext) {
    var vessel = context
    vessel.translateBy(x: E.heart.x, y: E.heart.y)
    vessel.clip(to: Path(H.contactClip))
    let beat = EmberMath.clamp((frame.beat - 1) / EmberMotionConstants.beat.lub)
    let poolBeat = frame.quality == .low ? 0 : beat

    // Contact stays fixed on the floor; only its light strength follows the beat.
    if frame.quality != .low {
      var shadow = vessel
      shadow.addFilter(.blur(radius: H.contactBlur))
      shadow.translateBy(x: 0, y: H.shadowDrop)
      shadow.fill(
        EmberPaths.heart, with: .color(EmberHeartTone.contactShadow.opacity(H.shadowAlpha)))
      var caustic = vessel
      caustic.addFilter(.blur(radius: H.contactBlur))
      caustic.fill(
        Path(ellipseIn: H.caustic),
        with: .color(
          EmberHeartTone.contactCaustic.opacity(H.causticAlpha.rest + H.causticAlpha.beat * beat)))
    }

    // Body, pool and rims pulse around the box centre; the native number stays fixed.
    vessel.translateBy(x: H.center.x, y: H.center.y)
    vessel.scaleBy(x: frame.beat, y: frame.beat)
    vessel.translateBy(x: -H.center.x, y: -H.center.y)
    let path = EmberPaths.heart

    func gradient(_ colors: [Color], _ stops: [Double], _ start: CGPoint, _ end: CGPoint)
      -> GraphicsContext.Shading
    {
      .linearGradient(
        Gradient(stops: zip(colors, stops).map { .init(color: $0.0, location: $0.1) }),
        startPoint: start, endPoint: end)
    }
    func point(_ y: Double) -> CGPoint { CGPoint(x: H.center.x, y: y) }

    var body = vessel
    body.opacity = H.bodyAlpha
    body.fill(
      path,
      with: gradient(
        [EmberHeartTone.bodyTop, EmberHeartTone.bodyMiddle, EmberHeartTone.bodyTip],
        H.bodyStops, point(H.top), point(H.tip)))
    vessel.clip(to: path)
    var pool = vessel
    pool.opacity = H.pool.alpha + H.pool.alphaBeat * poolBeat
    pool.fill(
      path,
      with: gradient(
        [EmberHeartTone.poolSurface, EmberHeartTone.poolMiddle, EmberHeartTone.poolTip],
        H.bodyStops, point(H.pool.level - H.pool.heat * frame.glow - H.pool.beat * poolBeat),
        point(H.tip)))
    vessel.stroke(
      path,
      with: gradient(
        [EmberHeartTone.depthTop, EmberHeartTone.depthClear], [0, 1],
        point(H.top), point(H.depthEnd)), lineWidth: H.depthWidth)
    var rim = vessel
    rim.opacity = H.rimAlpha.rest + H.rimAlpha.beat * beat
    rim.stroke(
      path,
      with: gradient(
        [EmberHeartTone.rimEmberClear, EmberHeartTone.rimEmber], [0, 1],
        point(H.rimTop), point(H.tip)), lineWidth: H.rimWidth)

    // The key fades before the upper-left clip boundary, leaving the lower ember rim separate.
    var key = vessel
    key.opacity = H.keyAlpha
    key.clip(to: Path(CGRect(x: 0, y: 0, width: H.center.x, height: H.rimTop)))
    let start = CGPoint(
      x: H.center.x + cos(EmberTheme.emberHeartKeyAngle) * H.keyReach,
      y: H.center.y + sin(EmberTheme.emberHeartKeyAngle) * H.keyReach)
    key.stroke(
      path,
      with: gradient(
        [EmberHeartTone.rimKey, EmberHeartTone.rimKeyClear], H.keyStops,
        start, CGPoint(x: E.heart.w - start.x, y: E.heart.h - start.y)),
      lineWidth: H.rimWidth)
    vessel.stroke(
      EmberPaths.crescent, with: .color(EmberHeartTone.specularCrescent.opacity(H.crescentAlpha)),
      style: StrokeStyle(lineWidth: H.crescentWidth, lineCap: .round))
  }

  private func effects(_ context: GraphicsContext) {
    guard !frame.reduced, frame.quality != .low else { return }
    var air = context
    let transform = CGAffineTransform(
      translationX: E.heart.x + H.center.x, y: E.heart.y + H.center.y
    )
    .scaledBy(x: frame.beat, y: frame.beat).translatedBy(x: -H.center.x, y: -H.center.y)
    var mask = Path(
      CGRect(x: -E.bleed, y: -E.bleed, width: EmberTheme.canvas, height: EmberTheme.canvas))
    mask.addPath(EmberPaths.heart.applying(transform))
    // Even-odd clipping keeps the pulsing vessel in front of every airborne effect.
    air.clip(to: mask, style: FillStyle(eoFill: true))

    func circle(_ x: Double, _ y: Double, _ radius: Double, _ color: Color, _ opacity: Double) {
      guard radius > 0, opacity > 0 else { return }
      air.fill(
        Path(
          ellipseIn: CGRect(x: x - radius, y: y - radius, width: 2 * radius, height: 2 * radius)),
        with: .color(color.opacity(opacity)))
    }

    // Reference particle phases/rates are deterministic motion coefficients, not layout tokens.
    for index in 0..<FX.emberCount {
      let seed = Double(index)
      let life = (frame.time * 0.55 + seed * 0.137).truncatingRemainder(dividingBy: 1)
      let x = E.embers.x + seed * E.embers.pitch + sin(frame.time * 1.7 + seed * 2.1) * FX.emberSway
      let y = E.count.top + E.count.h + E.embers.below - life * FX.emberTravel
      circle(
        x, y, FX.emberRadius, EmberTone.wave,
        EmberMath.seg(frame.charge, 0.2, 0.7) * sin(.pi * life) * FX.emberAlpha
          * EmberTheme.countLight(x, y))
    }
    let head = E.bar.x + max(E.bar.h / 2, frame.progress * E.bar.w) - FX.headInset
    let wave = EmberMath.seg(frame.ignition, 0, EmberMotionConstants.waveEnd)
    if frame.ignition > 0 && wave < 1 {
      let radius = FX.waveRadius * EmberMath.out(wave)
      air.stroke(
        Path(
          ellipseIn: CGRect(
            x: head - radius, y: EmberTheme.barCenterY - radius,
            width: 2 * radius, height: 2 * radius)),
        with: .color(EmberTone.wave.opacity(FX.waveAlpha * (1 - wave))),
        lineWidth: FX.waveWidth - FX.waveThinning * wave)
    }
    guard frame.ignition > 0 else { return }
    // Fixed angle/speed/lifetime seeds from Spark; 260 is half the 520 pt/s² gravity.
    for index in 0..<(frame.quality == .high ? FX.highSparks : FX.mediumSparks) {
      let angle = -Double(20 + (index * 53) % 140) * .pi / 180
      let speed = 120 + Double((index * 37) % 100) / 100 * 240
      let life = 0.7 + Double((index * 29) % 100) / 100 * 0.4
      let age = frame.age
      let x = head + cos(angle) * speed * age
      let y = EmberTheme.barCenterY + sin(angle) * speed * age + 260 * age * age
      let radius = FX.sparkRadius * max(0, 1 - age / life)
      let cooled = EmberMath.seg(age, 0, life)
      let transmission = EmberTheme.countLight(x, y)
      circle(x, y, radius, EmberTone.spark, (1 - cooled) * transmission)
      circle(x, y, radius, EmberTone.sparkCool, cooled * transmission)
    }
  }
}
