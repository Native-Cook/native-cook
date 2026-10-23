import SwiftUI

typealias GlowknobTilt = SIMD2<Double>

private typealias G = GlowknobReferenceGeometry

let GLOWKNOB_SPECIMEN_TOP = 16.0
let GLOWKNOB_SPECIMEN_HEIGHT = 340.0

private let H = G.track.height
private let housing = GlowknobSk.rrect(1, 1, 998, 282, 141, 141)
private let trackPath = GlowknobSk.rrect(G.track.x, G.track.y, G.track.width, G.track.height, 52, 52)
private let ticks: [(CGPoint, CGPoint)] = (0..<28).map { i in
  let a = Double(i) * Double.pi * 2 / 28
  return (GlowknobSk.pt(G.dial.x + sin(a) * 85, G.dial.y - cos(a) * 85),
          GlowknobSk.pt(G.dial.x + sin(a) * 96, G.dial.y - cos(a) * 96))
}
private let bevelColors = [GlowknobRGBA("#FFFFFF00"), GlowknobRGBA("#FFFFFF2A"), GlowknobRGBA("#FFFFFF00")]
private let clear = GlowknobRGBA("#00000000")

/// Everything DeviceArt reads in one frame — the values of the shared and
/// derived values of `GlowknobTactileControl` plus tilt. Captured per frame for the
/// live canvas and the floor reflection, and once (with still tilt) for the
/// lifted object's face.
struct GlowknobDeviceArtFrame {
  var trackPress = 0.0
  var brightPress = 0.0
  var volumePress = 0.0
  var readout = 52.0
  var numberInk = 0.0
  var numberAlpha = 1.0
  var labelAlpha = 1.0
  var fill = glowknobFillForLevel(0.52)
  var mix = 0.0
  var lightAlpha = 1.0
  var lightBlur = 0.0
  var dialRotation = 0.0
  var dialPress = 0.0
  var glowPress = 0.0
  var active = 1.0
  var reduced = false
  var selectorMix = 0.0
  var slosh = 0.0
  var tilt = GlowknobTilt(0, 0)
  var mode: GlowknobChannel = 0

  @MainActor
  init(_ c: GlowknobTactileControl, tilt: GlowknobTilt) {
    trackPress = c.trackPress.get()
    brightPress = c.brightPress.get()
    volumePress = c.volumePress.get()
    readout = c.readout
    numberInk = c.numberInk
    numberAlpha = c.numberAlpha
    labelAlpha = c.labelAlpha
    fill = c.fill
    mix = c.lightMix
    lightAlpha = c.lightAlpha
    lightBlur = c.lightBlur
    dialRotation = c.dialRotation
    dialPress = c.dialPress.get()
    glowPress = c.glowPress
    active = c.active.get()
    reduced = c.reduced
    selectorMix = c.selectorMix
    slosh = c.slosh
    self.tilt = tilt
    mode = c.mode
  }
}

/// Resources DeviceArt needs that are built once: the housing grain, per
/// pixel density. The live canvases and the floor use the screen's density,
/// the lifted object's face its texture's; both stay cached (one entry used
/// to serve both, so every pick-up rebuilt the grain twice on the main
/// thread), and the face's is built off the main thread ahead of time.
@MainActor
final class GlowknobDeviceArtResources {
  static let shared = GlowknobDeviceArtResources()
  private var cache: [(pixelsPerUnit: Double, image: Image)] = []
  private var pending: [Double] = []

  /// GlowknobFractalNoise freq 0.65, 1 octave, seed 7, over the housing, sampled at
  /// the device pixels it lands on (Skia evaluates the shader per pixel).
  func noise(pixelsPerUnit: Double) -> Image? {
    if let hit = cached(pixelsPerUnit) { return hit }
    guard let cg = Self.grain(pixelsPerUnit) else { return nil }
    return store(cg, pixelsPerUnit)
  }

  /// Builds the grain for `pixelsPerUnit` on a background thread.
  func prewarm(pixelsPerUnit: Double) {
    if cached(pixelsPerUnit) != nil || pending.contains(where: { abs($0 - pixelsPerUnit) < 1e-6 }) { return }
    pending.append(pixelsPerUnit)
    Task.detached(priority: .utility) {
      let cg = Self.grain(pixelsPerUnit)
      await MainActor.run {
        let shared = GlowknobDeviceArtResources.shared
        shared.pending.removeAll { abs($0 - pixelsPerUnit) < 1e-6 }
        if let cg, shared.cached(pixelsPerUnit) == nil { _ = shared.store(cg, pixelsPerUnit) }
      }
    }
  }

  private func cached(_ pixelsPerUnit: Double) -> Image? {
    cache.first { abs($0.pixelsPerUnit - pixelsPerUnit) < 1e-6 }?.image
  }

  private func store(_ cg: CGImage, _ pixelsPerUnit: Double) -> Image {
    let image = Image(decorative: cg, scale: 1).interpolation(.none)
    cache.append((pixelsPerUnit, image))
    if cache.count > 4 { cache.removeFirst() }
    return image
  }

  nonisolated private static func grain(_ pixelsPerUnit: Double) -> CGImage? {
    GlowknobFractalNoise.image(units: CGSize(width: 1000, height: 284), pixelsPerUnit: pixelsPerUnit,
                       freq: 0.65, octaves: 1, seed: 7)
  }
}

/// The device artwork in drawing units (housing top-left at 0,0), drawn into
/// a context already scaled/translated by the caller. Used by the specimen
/// canvas, by the floor reflection (baked, mirrored) so the reflection shows
/// the same live light, knob and spill, and as the face of the lifted 3D
/// object (baked).
///   baked: also paints the native overlays (title, number, selector glyphs),
///   for the floor reflection and the lifted 3D object's face.
@MainActor
func glowknobDrawDeviceArt(_ ctx: GraphicsContext, _ f: GlowknobDeviceArtFrame, fx: GlowknobEffects, baked: Bool, noise: Image?) {
  let extent = G.track.width * f.fill
  // Liquid fill. Below the end cap's width the light rises from the left
  // wall like liquid in the channel: a slice whose right edge rounds as it
  // grows, becoming the full cap at extent = H, then a capsule as before. The
  // shape changes continuously from 0, at full color, so there is no pop and
  // no dead zone. The rect always starts H left of the wall so the track clip
  // draws the left cap; that leaves the right corners free to deform (meniscus).
  let fillX = G.track.x - H
  let fw = extent + H
  let fr = min(H / 2, extent / 2)
  // GlowknobMeniscus: the leading edge's horizontal radius follows the slosh spring,
  // scaled down with the slice so a thin light deforms proportionally.
  var radii = (rx: fr, ry: fr)
  if fx.meniscus {
    let ry = fr
    let rx = ry + f.slosh * (ry / (H / 2))
    radii = (max(ry * 0.55, min(ry * 1.7, rx, fw / 2)), ry)
  }
  // Fades the last hairline at ~0 instead of leaving a 1px sliver.
  let presence = min(1, extent / 8)
  // The halo grows with length so a few percent never reads as a blurred blob.
  let reachK = min(1, extent / H)
  let reach = reachK * reachK
  let glowWidth = max(1, extent)
  let end = GlowknobSk.pt(G.track.x + glowWidth, 0)
  let colors = [glowknobInterpolateColors(f.mix, GlowknobTactile.transferAt, GlowknobTactile.transfer[0]),
                glowknobInterpolateColors(f.mix, GlowknobTactile.transferAt, GlowknobTactile.transfer[1])]
  let alpha = f.lightAlpha * (0.35 + 0.65 * f.active)
  // Light spill: what the light illuminates around it. Strength grows with
  // length; the knob rim catches it only as the light gets close.
  let tip = G.track.x + extent
  let cy = G.track.y + H / 2
  let tipColor = glowknobInterpolateColors(f.mix, GlowknobTactile.transferAt, GlowknobTactile.transfer[1])
  let spill = fx.spill ? alpha * min(1, extent / 8) * (0.22 + 0.3 * f.fill) : 0
  let knobLight = fx.spill ? spill * max(0, min(1, (tip - 380) / 220)) * 1.6 : 0
  // GlowknobTilt: highlight band along the top bevel and the knob sheen angle.
  let bevelX = 500 + f.tilt.x * 320
  let sheenAngle = -0.6 + (fx.tilt ? f.tilt.x * 0.7 : 0)
  // Keep blur filters fixed; crossfade sharp/soft paints instead.
  let soft = min(1, f.lightBlur / 14)
  let softAlpha = alpha * presence * soft
  let sharpAlpha = alpha * presence * (1 - soft)
  // Colored halo the light casts on the housing. Static under Reduce Motion;
  // only the press swell is motion.
  let glow = alpha * presence * reach * (0.42 + (f.reduced ? 0 : f.glowPress * 0.18 + f.lightBlur / 60))
  // Selector wells follow the light's color handoff, not the React commit.
  let brightnessWell = glowknobInterpolateColors(f.selectorMix, [0, 1], [GlowknobRGBA("#151515"), GlowknobRGBA("#222222")])
  let volumeWell = glowknobInterpolateColors(f.selectorMix, [0, 1], [GlowknobRGBA("#222222"), GlowknobRGBA("#151515")])
  let brightnessRim = (1 - f.selectorMix) * 0.75
  let volumeRim = f.selectorMix * 0.75
  // Grab: a finger on the track wakes the light's tip — a soft highlight just
  // inside the leading edge, so the light reads as the thing being held.
  let grab = (f.reduced ? 0 : f.trackPress * 0.34) * alpha
  let grabCenter = GlowknobSk.pt(max(G.track.x + H / 2, tip - H / 2), cy)
  // Selector press: the pressed well darkens as it sinks.
  let brightSink = f.brightPress * 0.42
  let volumeSink = f.volumePress * 0.42

  // Drop shadow under the housing.
  ctx.blurred(14, opacity: 0.36) { l in
    l.fill(GlowknobSk.rrect(4, 12, 992, 280, 140), with: .color(.black))
  }
  // Housing.
  ctx.fill(GlowknobSk.rrect(0, 0, 1000, 284, 142),
           with: GlowknobSk.linear(GlowknobTactile.housing, [0, 0.48, 1], from: GlowknobSk.pt(0, 0), to: GlowknobSk.pt(0, 284)))
  // Grain.
  if let noise {
    var c = ctx
    c.clip(to: housing)
    c.opacity = 0.025
    c.blendMode = .multiply
    c.draw(noise, in: CGRect(x: 0, y: 0, width: 1000, height: 284))
  }
  // Rim.
  ctx.stroke(GlowknobSk.rrect(1.2, 1.2, 997.6, 281.6, 140.8),
             with: GlowknobSk.linear(["#646464", "#343434", "#191919"].map(GlowknobRGBA.init), [0, 0.3, 1],
                             from: GlowknobSk.pt(0, 0), to: GlowknobSk.pt(0, 284)),
             lineWidth: 2.4)
  if fx.tilt {
    var c = ctx
    c.clip(to: housing)
    c.fill(Path(CGRect(x: 0, y: 0, width: 1000, height: 18)),
           with: GlowknobSk.linear(bevelColors, from: GlowknobSk.pt(bevelX - 220, 0), to: GlowknobSk.pt(bevelX + 220, 0)))
  }
  if fx.spill {
    var c = ctx
    c.clip(to: housing)
    c.blendMode = .screen
    c.opacity = spill
    let center = GlowknobSk.pt(tip, cy)
    c.fill(GlowknobSk.circle(tip, cy, 330), with: GlowknobSk.radial([tipColor, clear], center: center, radius: 330))
  }
  // Track well with its inner shadows.
  let trackBox = (x: G.track.x, y: G.track.y, w: G.track.width, h: G.track.height, rx: 52.0, ry: 52.0)
  ctx.fill(trackPath, with: .color(GlowknobTactile.well.color))
  ctx.innerShadow(trackBox, dx: 0, dy: 5, sigma: 7, color: GlowknobRGBA("#000000B0"))
  ctx.innerShadow(trackBox, dx: 0, dy: -1, sigma: 1, color: GlowknobRGBA("#FFFFFF18"))
  ctx.stroke(trackPath,
             with: GlowknobSk.linear(["#101010", "#343434", "#565656"].map(GlowknobRGBA.init), [0, 0.75, 1],
                             from: GlowknobSk.pt(0, G.track.y), to: GlowknobSk.pt(0, G.track.y + G.track.height)),
             lineWidth: 1.2)
  // Halo.
  ctx.blurred(26, opacity: glow) { l in
    l.fill(GlowknobSk.rrect(G.track.x, G.track.y, glowWidth, H, fr),
           with: GlowknobSk.linear(colors, from: GlowknobSk.pt(G.track.x, 0), to: end))
  }
  do {
    var c = ctx
    c.clip(to: trackPath)
    let fillPath = GlowknobSk.rrect(fillX, G.track.y, fw, H, radii.rx, radii.ry)
    let shading = GlowknobSk.linear(colors, from: GlowknobSk.pt(G.track.x, 0), to: end)
    if sharpAlpha > 0 {
      var s = c
      s.opacity = sharpAlpha
      s.fill(fillPath, with: shading)
    }
    if softAlpha > 0 {
      c.blurred(14, opacity: softAlpha) { l in l.fill(fillPath, with: shading) }
    }
    if grab > 0 {
      var g = c
      g.blendMode = .screen
      g.opacity = grab
      g.fill(GlowknobSk.circle(grabCenter.x, grabCenter.y, H * 0.62),
             with: GlowknobSk.radial([GlowknobRGBA("#FFFFFFCC"), GlowknobRGBA("#FFFFFF00")], center: grabCenter, radius: H * 0.62))
    }
    if fx.glass {
      c.fill(Path(CGRect(x: G.track.x, y: G.track.y + 4, width: G.track.width, height: 34)),
             with: GlowknobSk.linear([GlowknobRGBA("#FFFFFF1C"), GlowknobRGBA("#FFFFFF00")],
                             from: GlowknobSk.pt(0, G.track.y + 4), to: GlowknobSk.pt(0, G.track.y + 38)))
    }
  }
  // Selector wells.
  for mode in 0...1 {
    let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y, r: G.brightness.radius)
      : (x: G.volume.x, y: G.volume.y, r: G.volume.radius)
    let box = (x: c.x - c.r, y: c.y - c.r, w: c.r * 2, h: c.r * 2, rx: c.r, ry: c.r)
    ctx.fill(GlowknobSk.rrect(box.x, box.y, box.w, box.h, c.r), with: .color((mode == 0 ? brightnessWell : volumeWell).color))
    ctx.innerShadow(box, dx: 0, dy: 2, sigma: 2, color: GlowknobRGBA("#000000B0"))
    ctx.innerShadow(box, dx: 0, dy: -1, sigma: 1, color: GlowknobRGBA("#FFFFFF35"))
  }
  if fx.rim {
    for mode in 0...1 {
      let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y, r: G.brightness.radius)
        : (x: G.volume.x, y: G.volume.y, r: G.volume.radius)
      var r = ctx
      r.opacity = mode == 0 ? brightnessRim : volumeRim
      r.stroke(GlowknobSk.circle(c.x, c.y, c.r - 1.5), with: .color((mode == 0 ? GlowknobTactile.warm[1] : GlowknobTactile.cool[1]).color),
               lineWidth: 2.2)
    }
  }
  for mode in 0...1 {
    let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y, r: G.brightness.radius)
      : (x: G.volume.x, y: G.volume.y, r: G.volume.radius)
    var s = ctx
    s.opacity = mode == 0 ? brightSink : volumeSink
    s.fill(GlowknobSk.circle(c.x, c.y, c.r), with: .color(.black))
  }
  // The knob. Only a touch on the knob presses the knob; the light answers any touch.
  do {
    var d = ctx
    let k = 1 - f.dialPress * 0.025
    d.translateBy(x: G.dial.x, y: G.dial.y)
    d.scaleBy(x: k, y: k)
    d.translateBy(x: -G.dial.x, y: -G.dial.y)
    d.blurred(16, opacity: 0.75) { l in l.fill(GlowknobSk.circle(G.dial.x, G.dial.y + 18, 97), with: .color(.black)) }
    d.fill(GlowknobSk.circle(G.dial.x, G.dial.y, 100),
           with: GlowknobSk.linear(GlowknobTactile.bezel, [0, 0.44, 1], from: GlowknobSk.pt(0, G.dial.y - 100), to: GlowknobSk.pt(0, G.dial.y + 100)))
    d.stroke(GlowknobSk.circle(G.dial.x, G.dial.y, 99),
             with: GlowknobSk.linear(["#8D8D8D", "#454545", "#171717"].map(GlowknobRGBA.init), [0, 0.38, 1],
                             from: GlowknobSk.pt(0, G.dial.y - 100), to: GlowknobSk.pt(0, G.dial.y + 100)),
             lineWidth: 1.6)
    do {
      var r = d
      r.translateBy(x: G.dial.x, y: G.dial.y)
      r.rotate(by: .radians(f.dialRotation))
      r.translateBy(x: -G.dial.x, y: -G.dial.y)
      var lines = Path()
      for (a, b) in ticks {
        lines.move(to: a)
        lines.addLine(to: b)
      }
      r.stroke(lines, with: .color(GlowknobTactile.groove.color), style: StrokeStyle(lineWidth: 3.3, lineCap: .round))
    }
    if fx.spill && knobLight > 0 {
      var c = d
      c.clip(to: GlowknobSk.circle(G.dial.x, G.dial.y, 100))
      c.blendMode = .screen
      c.opacity = knobLight
      let center = GlowknobSk.pt(G.dial.x - 130, G.dial.y)
      c.fill(GlowknobSk.circle(G.dial.x, G.dial.y, 100), with: GlowknobSk.radial([tipColor, clear], center: center, radius: 150))
    }
    if fx.sheen {
      var c = d
      c.translateBy(x: G.dial.x, y: G.dial.y)
      c.rotate(by: .radians(sheenAngle))
      c.translateBy(x: -G.dial.x, y: -G.dial.y)
      c.blendMode = .screen
      c.fill(GlowknobSk.circle(G.dial.x, G.dial.y, 99),
             with: GlowknobSk.sweep(["#FFFFFF00", "#FFFFFF30", "#FFFFFF00", "#FFFFFF00", "#FFFFFF14", "#FFFFFF00",
                             "#FFFFFF00"].map(GlowknobRGBA.init),
                            [0, 0.08, 0.2, 0.5, 0.58, 0.7, 1], center: GlowknobSk.pt(G.dial.x, G.dial.y)))
    }
    d.fill(GlowknobSk.circle(G.dial.x, G.dial.y, 70),
           with: GlowknobSk.linear(GlowknobTactile.dial, from: GlowknobSk.pt(0, G.dial.y - 70), to: GlowknobSk.pt(0, G.dial.y + 70)))
    d.stroke(GlowknobSk.circle(G.dial.x, G.dial.y, 70), with: .color(GlowknobRGBA("#4C4C4C").color), lineWidth: 1.4)
    d.fill(GlowknobSk.circle(G.dial.x, G.dial.y, 29), with: .color(GlowknobRGBA("#181818").color))
    // Inner Shadow image filter (dy 2, blur 3): the complement of the circle,
    // offset and blurred, kept inside the circle.
    d.innerShadow((x: G.dial.x - 29, y: G.dial.y - 29, w: 58, h: 58, rx: 29, ry: 29), dx: 0, dy: 2, sigma: 3,
                  color: GlowknobRGBA("#000000"))
    d.stroke(GlowknobSk.circle(G.dial.x, G.dial.y, 29), with: .color(GlowknobRGBA("#363636").color), lineWidth: 1)
  }
  if baked { drawBakedOverlays(ctx, f) }
}

/// The native overlays (selector glyphs, title, readout) painted into the
/// artwork, for the reflection and the lifted face. Helvetica Neue, as RN
/// Skia's matchFont resolves it on iOS.
@MainActor
private func drawBakedOverlays(_ ctx: GraphicsContext, _ f: GlowknobDeviceArtFrame) {
  for mode in 0...1 {
    let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y) : (x: G.volume.x, y: G.volume.y)
    let size = mode == 0 ? 50.0 : 46.0
    let k = size / 26
    var g = ctx
    // The pressed glyph sinks with its well, as the native one does.
    let sink = 1 - (mode == 0 ? f.brightPress : f.volumePress) * 0.08
    g.translateBy(x: c.x, y: c.y)
    g.scaleBy(x: sink, y: sink)
    g.translateBy(x: -c.x, y: -c.y)
    g.translateBy(x: c.x - 13 * k, y: c.y - 13 * k)
    g.scaleBy(x: k, y: k)
    let path = GlowknobGlyphs.bakedPath(mode)
    let style = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
    g.stroke(path, with: .color(GlowknobTactile.inactive.color), style: style)
    var lit = g
    lit.opacity = mode == 0 ? 1 - f.selectorMix : f.selectorMix
    lit.stroke(path, with: .color(GlowknobTactile.text.color), style: style)
  }
  var title = ctx
  title.opacity = f.labelAlpha
  drawBaseline(title, Text(f.mode == 0 ? "Brightness" : "Volume")
    .font(.custom("HelveticaNeue", fixedSize: 31)).foregroundColor(GlowknobTactile.text.color),
               x: 118, baseline: 36 + 31, alignRight: false)
  var number = ctx
  number.opacity = f.numberAlpha
  let ink = glowknobInterpolateColors(f.numberInk, [0, 1], [GlowknobTactile.text, GlowknobTactile.ink])
  drawBaseline(number, Text("\(Int(glowknobJsRound(f.readout)))%")
    .font(.custom("HelveticaNeue-Medium", fixedSize: 30)).foregroundColor(ink.color),
               x: G.readout.x + G.readout.width, baseline: G.readout.y + G.readout.line * 0.78, alignRight: true)
}

/// Skia `<Text x y>`: `y` is the baseline; for the readout `x` is the right edge
/// (x = right − measureText(width)).
@MainActor
private func drawBaseline(_ ctx: GraphicsContext, _ text: Text, x: Double, baseline: Double, alignRight: Bool) {
  let resolved = ctx.resolve(text)
  let size = resolved.measure(in: CGSize(width: 10_000, height: 10_000))
  let ascent = resolved.firstBaseline(in: size)
  let left = alignRight ? x - size.width : x
  ctx.draw(resolved, in: CGRect(x: left, y: baseline - ascent, width: size.width, height: size.height))
}
