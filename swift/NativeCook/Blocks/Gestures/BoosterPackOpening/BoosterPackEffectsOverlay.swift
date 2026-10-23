import SwiftUI

/// Fixed shapes of the vector overlay.
enum BoosterPackOverlayPaths {
  private static let arc = 0.55228475

  static func makeRing(_ centerX: Double, _ centerY: Double, _ radiusX: Double, _ topRadius: Double, _ bottomRadius: Double) -> CGPath {
    let path = BoosterPackPathBuilder()
    path.moveTo(centerX - radiusX, centerY)
    path.cubicTo(centerX - radiusX, centerY - topRadius * arc, centerX - radiusX * arc, centerY - topRadius, centerX, centerY - topRadius)
    path.cubicTo(centerX + radiusX * arc, centerY - topRadius, centerX + radiusX, centerY - topRadius * arc, centerX + radiusX, centerY)
    path.cubicTo(centerX + radiusX, centerY + bottomRadius * arc, centerX + radiusX * arc, centerY + bottomRadius, centerX, centerY + bottomRadius)
    path.cubicTo(centerX - radiusX * arc, centerY + bottomRadius, centerX - radiusX, centerY + bottomRadius * arc, centerX - radiusX, centerY)
    path.close()
    return path.build()
  }

  static func makeRingRibbon(_ width: Double, _ height: Double, _ cutY: Double) -> CGPath {
    // At f124 the far rim is only a thin arc above the pack's cap; the near and side
    // rims spread into a much wider flare. A constant-width ellipse hides that
    // depth cue and washes out the printed cap.
    let path = BoosterPackPathBuilder()
    path.moveTo(-width * 0.028, cutY)
    path.cubicTo(-width * 0.026, cutY - height * 0.064, width * 0.19, cutY - height * 0.108, width * 0.498, cutY - height * 0.108)
    path.cubicTo(width * 0.81, cutY - height * 0.108, width * 1.026, cutY - height * 0.06, width * 1.028, cutY + height * 0.002)
    path.cubicTo(width * 1.026, cutY + height * 0.054, width * 0.756, cutY + height * 0.076, width * 0.508, cutY + height * 0.07)
    path.cubicTo(width * 0.254, cutY + height * 0.07, -width * 0.026, cutY + height * 0.051, -width * 0.028, cutY)
    path.close()
    path.moveTo(width * 0.069, cutY)
    path.cubicTo(width * 0.073, cutY + height * 0.037, width * 0.282, cutY + height * 0.054, width * 0.506, cutY + height * 0.053)
    path.cubicTo(width * 0.738, cutY + height * 0.058, width * 0.93, cutY + height * 0.039, width * 0.931, cutY + height * 0.001)
    path.cubicTo(width * 0.929, cutY - height * 0.052, width * 0.793, cutY - height * 0.102, width * 0.499, cutY - height * 0.102)
    path.cubicTo(width * 0.216, cutY - height * 0.102, width * 0.074, cutY - height * 0.057, width * 0.069, cutY)
    path.close()
    return path.build()
  }

  static func makeRefractedFlare(_ width: Double, _ height: Double, _ cutY: Double) -> CGPath {
    // Light refracts through the torn packet in an uneven ribbon. Its lower
    // edge carries the warm-to-cyan diffraction visible around the artwork.
    let path = BoosterPackPathBuilder()
    path.moveTo(width * 0.124, cutY - height * 0.012)
    path.cubicTo(width * 0.24, cutY - height * 0.057, width * 0.43, cutY - height * 0.059, width * 0.54, cutY - height * 0.052)
    path.cubicTo(width * 0.72, cutY - height * 0.055, width * 0.8, cutY - height * 0.027, width * 0.87, cutY - height * 0.012)
    path.lineTo(width * 0.862, cutY + height * 0.043)
    path.lineTo(width * 0.834, cutY + height * 0.07)
    path.lineTo(width * 0.816, cutY + height * 0.032)
    path.lineTo(width * 0.795, cutY + height * 0.043)
    path.lineTo(width * 0.774, cutY + height * 0.073)
    path.lineTo(width * 0.742, cutY + height * 0.08)
    path.lineTo(width * 0.725, cutY + height * 0.065)
    path.lineTo(width * 0.695, cutY + height * 0.052)
    path.lineTo(width * 0.653, cutY + height * 0.029)
    path.cubicTo(width * 0.625, cutY + height * 0.053, width * 0.606, cutY + height * 0.068, width * 0.576, cutY + height * 0.072)
    path.lineTo(width * 0.554, cutY + height * 0.089)
    path.cubicTo(width * 0.509, cutY + height * 0.108, width * 0.459, cutY + height * 0.108, width * 0.44, cutY + height * 0.089)
    path.lineTo(width * 0.431, cutY + height * 0.076)
    path.lineTo(width * 0.397, cutY + height * 0.083)
    path.lineTo(width * 0.352, cutY + height * 0.075)
    path.lineTo(width * 0.31, cutY + height * 0.072)
    path.lineTo(width * 0.286, cutY + height * 0.077)
    path.lineTo(width * 0.236, cutY + height * 0.064)
    path.lineTo(width * 0.218, cutY + height * 0.059)
    path.lineTo(width * 0.168, cutY + height * 0.057)
    path.lineTo(width * 0.137, cutY + height * 0.041)
    path.close()
    return path.build()
  }

  /// The corners of a mark: a five-point star, or the four-point shape of a plain card.
  static func markPoints(_ x: Double, _ y: Double, _ size: Double, star: Bool) -> [CGPoint] {
    let points = star ? 10 : 4
    return (0..<points).map { i in
      let angle = -Double.pi / 2 + Double(i) * Double.pi * 2 / Double(points)
      let radius = star ? size * (i % 2 == 1 ? 0.46 : 1) : size * (i % 2 == 1 ? 0.65 : 1)
      return CGPoint(x: x + cos(angle) * radius, y: y + sin(angle) * radius)
    }
  }
}

/// The scene's vector layer, drawn over the Metal view from the same frame: the flash of the
/// cut, the burst that follows it, the trace under the finger, the sparks it throws, the
/// light that runs along the tear line of an idle pack, and the marks under a revealed card.
/// These are filled and stroked paths with real Gaussian blur, which SwiftUI's canvas does
/// well. Screen-blended parts blend against this layer rather than the scene below it.
struct BoosterPackEffectsOverlay: View {
  let experience: BoosterPackExperience

  private typealias M = BoosterPackModel
  private typealias E = BoosterPackOpeningEffectsMotion

  var body: some View {
    // Reading the counter re-evaluates this body for each new frame of the overlay, and the
    // canvas redraws because it is handed that frame's scene.
    let _ = experience.interface.overlayFrame
    let scene = experience.scene
    Canvas { context, _ in
      draw(&context, scene)
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }

  private func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
      blue: Double(hex & 0xFF) / 255, opacity: alpha)
  }

  private func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
    Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
  }

  private func gradient(_ colors: [Color], _ positions: [Double]? = nil) -> Gradient {
    guard let positions else { return Gradient(colors: colors) }
    return Gradient(stops: zip(colors, positions).map { Gradient.Stop(color: $0, location: $1) })
  }

  /// A copy of the context that blurs what is drawn through it and draws it at `opacity`.
  private func layer(_ context: GraphicsContext, blur: Double = 0, opacity: Double = 1) -> GraphicsContext {
    var copy = context
    copy.opacity = context.opacity * opacity
    if blur > 0.01 { copy.addFilter(.blur(radius: blur)) }
    return copy
  }

  private func line(_ from: CGPoint, _ to: CGPoint) -> Path {
    var path = Path()
    path.move(to: from)
    path.addLine(to: to)
    return path
  }

  private func draw(_ context: inout GraphicsContext, _ s: BoosterPackScene) {
    guard s.width > 0 else { return }
    if s.punch != 1 {
      context.translateBy(x: s.width / 2, y: s.height / 2)
      context.scaleBy(x: s.punch, y: s.punch)
      context.translateBy(x: -s.width / 2, y: -s.height / 2)
    }
    if s.committed {
      if s.postCutMs < 220 { drawFlash(context, s) }
      if s.burstLive { drawBurst(context, s) }
    }
    drawTrace(context, s)
    drawSparks(context, s)
    drawMarks(context, s)
  }

  // MARK: The flash

  // The reference's ring is a single-frame event at f124. The horizontal cut stays
  // visible afterward, narrowing from 22 px at f125 to a fine line at f133.
  private static let BEAM_TIMES: [Double] = [0, 16.667, 33.333, 50, 66.667, 100, 150, 220]
  private static let BEAM_WIDTHS: [Double] = [0.083, 0.056, 0.04, 0.038, 0.029, 0.012, 0.0045, 0.0045]
  private static let SPECTRUM: [UInt32] = [
    0xA9FFFD, 0xEFC8FF, 0xF9DCFF, 0x94FFFF, 0xB7FFFF, 0xF8BCFF, 0xFF87ED, 0xA9FFFD,
  ]
  private static let SPECTRUM_POSITIONS: [Double] = [0, 0.12, 0.25, 0.4, 0.5, 0.69, 0.84, 1]

  private func drawFlash(_ outer: GraphicsContext, _ s: BoosterPackScene) {
    let width = s.width
    let height = s.height
    let time = s.postCutMs
    let reducedMotion = s.reducedMotion
    let cutY = s.seamY - height * 0.003
    let center = CGPoint(x: width * 0.5, y: cutY)
    var context = outer
    context.clip(to: Path(CGRect(x: 0, y: cutY - height * 0.14, width: width, height: height * 0.28)))

    let ringOpacity = (reducedMotion ? 0.18 : 1) * M.interpolateKeyframes(time, [0, 8.333, 16.667], [1, 0.38, 0])
    if ringOpacity > 0.001 {
      var ring = layer(context, opacity: ringOpacity)
      let travel = reducedMotion ? 0 : M.clamp(time / 16.667, 0, 1)
      ring.translateBy(x: center.x, y: center.y - height * 0.002 * travel)
      ring.scaleBy(x: 1 + travel * 0.06, y: 1 + travel * 0.12)
      ring.translateBy(x: -center.x, y: -center.y)
      if reducedMotion {
        layer(ring, blur: width * 0.009).stroke(
          Path(ellipseIn: CGRect(x: width * 0.06, y: cutY - height * 0.078, width: width * 0.88, height: height * 0.156)),
          with: .color(color(0xB6FFFA)), lineWidth: width * 0.012)
      } else {
        // The packet occludes the rear arc; refracted light below is a
        // separate bloom, so the front never becomes a white hoop.
        var behind = ring
        behind.clip(to: Path(s.flashPacketMask), options: .inverse)
        let ringPath = Path(BoosterPackOverlayPaths.makeRing(width * 0.5, cutY, width * 0.528, height * 0.105, height * 0.088))
        let ribbon = Path(BoosterPackOverlayPaths.makeRingRibbon(width, height, cutY))
        let spectrum = GraphicsContext.Shading.conicGradient(
          gradient(Self.SPECTRUM.map { color($0) }, Self.SPECTRUM_POSITIONS), center: center)
        layer(behind, blur: width * 0.012).stroke(ringPath, with: spectrum, lineWidth: width * 0.028)
        layer(behind, blur: width * 0.006).stroke(ringPath, with: spectrum, lineWidth: width * 0.011)
        layer(behind, blur: width * 0.009).fill(ribbon, with: .color(color(0xE9FFF8)), style: FillStyle(eoFill: true))
        layer(behind, blur: width * 0.004).fill(ribbon, with: .color(.white), style: FillStyle(eoFill: true))

        var glow = layer(ring, blur: width * 0.04, opacity: 0.87)
        glow.blendMode = .screen
        glow.fill(
          Path(ellipseIn: CGRect(x: width * 0.23, y: cutY - height * 0.09, width: width * 0.54, height: height * 0.069)),
          with: .color(color(0xFFFFBC)))

        let flare = Path(BoosterPackOverlayPaths.makeRefractedFlare(width, height, cutY))
        var lifted = layer(ring, blur: width * 0.014, opacity: 0.9)
        lifted.translateBy(x: 0, y: -height * 0.008)
        lifted.fill(flare, with: .color(color(0xFFFFF8)))
        layer(ring, blur: width * 0.012).fill(flare, with: .linearGradient(
          gradient([
            rgba(255, 89, 191, 0), rgba(255, 148, 106, 0.32), rgba(255, 220, 133, 0.92), .white, .white,
            rgba(255, 255, 212, 0.98), rgba(255, 251, 99, 0.93), rgba(116, 255, 167, 0.82),
            rgba(97, 223, 255, 0.74), rgba(112, 135, 255, 0),
          ], [0, 0.12, 0.25, 0.36, 0.7, 0.76, 0.82, 0.88, 0.95, 1]),
          startPoint: CGPoint(x: 0, y: cutY - height * 0.061), endPoint: CGPoint(x: 0, y: cutY + height * 0.108)))
      }
    }

    let inset = reducedMotion ? 0 : width * 0.11 * (1 - M.clamp(time / 16.667, 0, 1))
    let beam = line(CGPoint(x: inset, y: cutY), CGPoint(x: width - inset, y: cutY))
    let beamWidth = width * (reducedMotion ? 0.35 : 1) * M.interpolateKeyframes(time, Self.BEAM_TIMES, Self.BEAM_WIDTHS)
    let beamEdgeWidth = beamWidth + width * 0.008
    let beamGlowWidth = beamWidth + width * 0.022
    let beamBloomWidth = beamWidth + width * 0.062
    let beamCoreBlur = width * M.interpolateKeyframes(time, [0, 16.667, 66.667], [0.012, 0.002, 0.0008])
    let beamEdgeBlur = width * M.interpolateKeyframes(time, [0, 16.667], [0.016, 0.002])
    let beamBloomOpacity = reducedMotion ? 0 : ringOpacity * 0.92
    let beamOpacity = (reducedMotion ? 0.45 : 1) * M.interpolateKeyframes(time, [0, 150, 183.333, 220], [1, 1, 0.55, 0])
    let glowOpacity = (reducedMotion ? 0.08 : 0.75)
      * M.interpolateKeyframes(time, [0, 16.667, 66.667, 150], [1, 0.4, 0.18, 0])
    let beamEdgeOpacity = beamOpacity * M.interpolateKeyframes(time, [0, 66.667, 150], [1, 0.55, 0])
    let warmEdgeOpacity = beamEdgeOpacity * M.interpolateKeyframes(time, [0, 16.667, 50, 66.667], [1, 0.8, 0.15, 0])
    let edgeStart = CGPoint(x: 0, y: cutY - beamEdgeWidth * 0.5)
    let edgeEnd = CGPoint(x: 0, y: cutY + beamEdgeWidth * 0.5)
    let round = { (lineWidth: Double) in StrokeStyle(lineWidth: lineWidth, lineCap: .round) }

    if glowOpacity > 0.001 {
      layer(context, blur: width * 0.016, opacity: glowOpacity)
        .stroke(beam, with: .color(color(0x83FFFA)), lineWidth: beamGlowWidth)
    }
    if beamBloomOpacity > 0.001 {
      layer(context, blur: width * 0.027, opacity: beamBloomOpacity)
        .stroke(beam, with: .color(color(0xFFFFED)), style: round(beamBloomWidth))
    }
    if beamEdgeOpacity > 0.001 {
      layer(context, blur: beamEdgeBlur, opacity: beamEdgeOpacity).stroke(beam, with: .linearGradient(
        gradient([color(0x67FFF5), color(0xD5FFF2), .white, color(0xDCFFFF), color(0x87F9FF)], [0, 0.18, 0.5, 0.8, 1]),
        startPoint: edgeStart, endPoint: edgeEnd), style: round(beamEdgeWidth))
    }
    if warmEdgeOpacity > 0.001 {
      layer(context, blur: beamEdgeBlur, opacity: warmEdgeOpacity).stroke(beam, with: .linearGradient(
        gradient([color(0x67FFF5), color(0xD5FFF2), .white, color(0xFFFFB3), color(0xFFE66D)], [0, 0.18, 0.5, 0.8, 1]),
        startPoint: edgeStart, endPoint: edgeEnd), style: round(beamEdgeWidth))
    }
    if beamOpacity > 0.001 {
      layer(context, blur: beamCoreBlur, opacity: beamOpacity).stroke(beam, with: .color(.white), style: round(beamWidth))
    }
  }

  // MARK: The burst

  private static let GLINT_COLORS: [UInt32] = [0x36E4FF, 0xFF62E6, 0xFFD84A, 0x5CF0FF, 0xFF7BEA, 0xFFE66B]

  /// Expanding ring and glints that carry the single-frame flash into the opening.
  private func drawBurst(_ context: GraphicsContext, _ s: BoosterPackScene) {
    let width = s.width
    let height = s.height
    let ms = s.postCutMs
    let cutY = s.seamY - height * 0.003
    if ms > 0 && ms < 480 {
      let wave = E.shockwaveAt(ms, width, height)
      var ring = layer(context, blur: width * 0.014, opacity: wave.opacity)
      ring.blendMode = .screen
      ring.stroke(
        Path(ellipseIn: CGRect(
          x: width * 0.5 - wave.radiusX, y: cutY - wave.radiusY, width: wave.radiusX * 2, height: wave.radiusY * 2)),
        with: .color(color(0xB4FFFA)), lineWidth: wave.strokeWidth)
    }
    guard ms > 0, ms < E.BURST_DURATION_MS else { return }
    var glints = Path()
    for index in 0..<E.BURST_GLINT_COUNT {
      guard let glint = E.burstGlintAt(index, ms, s.pack, cutY, width, height), glint.size > 0.2 else { continue }
      for corner in 0..<8 {
        let angle = glint.rotation + Double(corner) * Double.pi / 4
        let radius = corner % 2 == 0 ? glint.size : glint.size * 0.26
        let point = CGPoint(x: glint.x + cos(angle) * radius, y: glint.y + sin(angle) * radius)
        if corner == 0 { glints.move(to: point) } else { glints.addLine(to: point) }
      }
      glints.closeSubpath()
    }
    guard !glints.isEmpty else { return }
    // Saturated halos keep the glints readable over the pale background.
    let halo = GraphicsContext.Shading.linearGradient(
      gradient(Self.GLINT_COLORS.map { color($0) }),
      startPoint: CGPoint(x: s.pack.x, y: 0), endPoint: CGPoint(x: s.pack.x + s.pack.width, y: 0))
    layer(context, blur: width * 0.012).fill(glints, with: halo)
    layer(context, blur: width * 0.004).fill(glints, with: halo)
    layer(context, opacity: 0.92).fill(glints, with: .color(.white))
  }

  // MARK: The trace

  private func drawTrace(_ outer: GraphicsContext, _ s: BoosterPackScene) {
    guard s.seamHintOpacity > 0.001 || s.visibleTraceOpacity > 0.001 else { return }
    let width = s.width
    let pack = s.pack
    var context = outer
    context.clip(to: Path(pack.cgRect))
    let seamHintY = s.seamY - s.height * 0.0045

    if s.seamHintOpacity > 0.001 {
      let tail = CGPoint(x: s.seamHintTail, y: seamHintY)
      let head = CGPoint(x: s.seamHintHead, y: seamHintY)
      layer(context, blur: width * 0.012, opacity: s.seamHintOpacity).stroke(
        line(tail, head),
        with: .linearGradient(
          gradient([rgba(120, 240, 255, 0), rgba(150, 246, 255, 0.5), rgba(255, 255, 255, 0.95)], [0, 0.65, 1]),
          startPoint: tail, endPoint: head),
        style: StrokeStyle(lineWidth: width * 0.026, lineCap: .round))
      func disc(_ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: head.x - radius, y: head.y - radius, width: radius * 2, height: radius * 2))
      }
      layer(context, blur: width * 0.03, opacity: s.seamHintOpacity).fill(disc(width * 0.034), with: .color(color(0x9CF8FF)))
      layer(context, blur: width * 0.008, opacity: s.seamHintOpacity).fill(disc(width * 0.017), with: .color(.white))
    }

    let opacity = s.visibleTraceOpacity
    guard opacity > 0.001 else { return }
    let seam = line(CGPoint(x: pack.x, y: seamHintY), CGPoint(x: pack.x + pack.width, y: seamHintY))
    layer(context, blur: width * 0.003, opacity: opacity)
      .stroke(seam, with: .color(rgba(214, 38, 238, 0.55)), lineWidth: width * 0.016)
    layer(context, opacity: opacity).stroke(seam, with: .color(color(0xF006FA)), lineWidth: width * 0.008)

    let traceSize = s.traceSize
    let body = Path(s.traceBodyPath)
    let core = Path(s.traceCorePath)
    let stroke = { (lineWidth: Double) in StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round) }
    let positions: [Double] = [0, 0.62, 0.7, 0.82, 0.96, 1]
    layer(context, blur: traceSize * 0.032, opacity: opacity)
      .stroke(body, with: .color(color(0xE049F1)), style: stroke(traceSize * 0.055))
    layer(context, blur: traceSize * 0.0045, opacity: opacity).stroke(body, with: .linearGradient(
      gradient([color(0xCF49F1), color(0xCF49F1), color(0xD34AF2), color(0xD94EF2), color(0xDB4EF2), color(0xD54AF1)], positions),
      startPoint: s.traceGradientStart, endPoint: s.traceGradientEnd), style: stroke(traceSize * 0.072))
    layer(context, blur: traceSize * 0.002, opacity: opacity).stroke(body, with: .linearGradient(
      gradient([color(0xD04AF1), color(0xD04AF1), color(0xE66EF6), color(0xFBC5FD), color(0xFAC1FD), color(0xEE7AF6)], positions),
      startPoint: s.traceGradientStart, endPoint: s.traceGradientEnd), style: stroke(traceSize * 0.056))
    layer(context, blur: traceSize * 0.004, opacity: opacity).stroke(core, with: .linearGradient(
      gradient([
        rgba(255, 255, 255, 1), rgba(255, 255, 255, 1), rgba(255, 252, 255, 0.98), rgba(255, 248, 255, 0.82),
        rgba(255, 245, 255, 0.58), rgba(255, 240, 255, 0.32), rgba(255, 238, 255, 0.05), rgba(255, 232, 255, 0),
      ], [0, 0.52, 0.58, 0.64, 0.69, 0.735, 0.82, 1]),
      startPoint: s.traceGradientStart, endPoint: s.traceCoreGradientEnd), style: stroke(traceSize * 0.046))
  }

  // MARK: Sparks

  /// Sparks thrown from the tear frontier in proportion to newly torn material.
  private func drawSparks(_ outer: GraphicsContext, _ s: BoosterPackScene) {
    guard !s.sparks.isEmpty else { return }
    let width = s.width
    var path = Path()
    for spark in s.sparks {
      let speed = hypot(spark.vx, spark.vy)
      if speed < 0.001 { continue }
      let fade = 1 - spark.age / spark.life
      let dx = spark.vx / speed
      let dy = spark.vy / speed
      let length = spark.size * (1.6 + speed / (width * 0.22)) * fade
      let half = spark.size * 0.5 * fade + 0.25
      path.move(to: CGPoint(x: spark.x + dx * length, y: spark.y + dy * length))
      path.addLine(to: CGPoint(x: spark.x - dy * half, y: spark.y + dx * half))
      path.addLine(to: CGPoint(x: spark.x - dx * length * 0.55, y: spark.y - dy * length * 0.55))
      path.addLine(to: CGPoint(x: spark.x + dy * half, y: spark.y - dx * half))
      path.closeSubpath()
    }
    var context = outer
    context.blendMode = .screen
    layer(context, blur: width * 0.012).fill(path, with: .color(color(0xF45CFF)))
    layer(context, blur: width * 0.004).fill(path, with: .color(color(0xFF9CF7)))
    context.fill(path, with: .color(color(0xFFF6FF)))
  }

  // MARK: Marks

  /// One to three marks under the top card once it has settled: stars for an epic card.
  private func drawMarks(_ context: GraphicsContext, _ s: BoosterPackScene) {
    let size = s.width * 0.025
    for (slot, mark) in s.marks.enumerated() where mark.opacity > 0.001 {
      let x = s.width * (1 - M.REFERENCE_LAYOUT.card.width) / 2 + size + Double(slot) * size * 2.5
      let y = s.height * (1 + M.REFERENCE_LAYOUT.card.height) / 2 + s.width * 0.065
      var path = Path()
      path.addLines(BoosterPackOverlayPaths.markPoints(x, y, mark.epic ? size : size * 0.78, star: mark.epic))
      path.closeSubpath()
      let drawing = layer(context, opacity: mark.opacity)
      drawing.stroke(path, with: .color(color(0x263442)), style: StrokeStyle(lineWidth: size * 0.34, lineJoin: .round))
      drawing.fill(path, with: .linearGradient(
        gradient(mark.epic
          ? [color(0xFFF9C8), color(0xFFDF61), color(0xC18C24)]
          : [color(0xFAFCFF), color(0xDDE9EE), color(0x8DA1B3)]),
        startPoint: CGPoint(x: x, y: y - size), endPoint: CGPoint(x: x, y: y + size)))
    }
  }
}
