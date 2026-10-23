import SwiftUI

private typealias G = GlowknobReferenceGeometry

// Drawing units below the specimen box available to the floor layers.
let GLOWKNOB_FLOOR = 210.0
// Glass floor: the mirror line sits just under the housing; the reflection is
// squashed slightly for perspective, softened a touch and faded with depth.
let GLOWKNOB_GAP = 6.0
let GLOWKNOB_SQUASH = 0.9
let GLOWKNOB_DEPTH = 0.7
// Visibility of the reflection from the glass edge down: strong at contact,
// then an eased falloff to nothing. Applied as a page-colored "fog" over the
// mirrored device (opacity = 1 - visibility).
let GLOWKNOB_VISIBLE = [0.42, 0.2, 0.06, 0]
let GLOWKNOB_VISIBLE_AT = [0, 0.25, 0.55, 1.0]

/// The page tone as seen: white dimmed by the brightness ambience (0…1).
@MainActor
func glowknobPageTone(_ control: GlowknobTactileControl, fx: GlowknobEffects) -> Double {
  let dimmed = fx.ambient && !control.reduced
  return dimmed ? 1 - (1 - max(0, min(1, control.brightness.get()))) * 0.1 : 1
}

/// Fog stops in the page tone: `rgba(c,c,c,1-v)` with c = round(255·tone).
func glowknobFogColors(_ tone: Double) -> [GlowknobRGBA] {
  let c = glowknobJsRound(255 * tone) / 255
  return GLOWKNOB_VISIBLE.map { GlowknobRGBA(c, c, c, 1 - $0) }
}

/// Brightness is a "world" setting: its level dims the page around the
/// device (≤10% black at 0), whichever channel is being edited.
struct GlowknobStageAmbient: View {
  let control: GlowknobTactileControl
  let fx: GlowknobEffects

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("GlowknobStageAmbient")
    let on = fx.ambient && !control.reduced
    Color.black
      .opacity(on ? (1 - max(0, min(1, control.brightness.get()))) * 0.1 : 0)
      .allowsHitTesting(false)
  }
}

/// The surface under the device: a glass floor that mirrors the live device
/// (same light, knob and spill, drawn by DeviceArt), and the colored pool
/// where the light falls on it.
@MainActor
func glowknobDrawStageFloor(_ ctx: GraphicsContext, _ art: GlowknobDeviceArtFrame, tone: Double, fx: GlowknobEffects, noise: Image?) {
  let bottom = GLOWKNOB_SPECIMEN_TOP + G.height
  let mirror = bottom + GLOWKNOB_GAP / 2
  let depth = G.height * GLOWKNOB_SQUASH * GLOWKNOB_DEPTH
  let extent = G.track.width * art.fill
  let tipColor = glowknobInterpolateColors(art.mix, GlowknobTactile.transferAt, GlowknobTactile.transfer[1])
  let pool = fx.spill ? art.lightAlpha * min(1, extent / 8) * (0.18 + 0.32 * art.fill) : 0
  let poolW = 160 + 300 * art.fill
  let poolX = G.track.x + extent * 0.7 - poolW / 2
  if fx.reflection {
    // A layer blurred by 1.2 units (Skia `<Blur>` image filter, decal), drawn
    // through the mirror transform.
    var m = ctx
    m.translateBy(x: 0, y: mirror)
    m.scaleBy(x: 1, y: -GLOWKNOB_SQUASH)
    m.translateBy(x: 0, y: -mirror)
    m.translateBy(x: 0, y: GLOWKNOB_SPECIMEN_TOP)
    m.drawLayer { layer in
      layer.addFilter(.blur(radius: GlowknobBlurCalibration.radius(sigma: 1.2)))
      glowknobDrawDeviceArt(layer, art, fx: fx, baked: true, noise: noise)
    }
    ctx.fill(Path(CGRect(x: 0, y: mirror, width: 1000, height: G.height)),
             with: GlowknobSk.linear(glowknobFogColors(tone), GLOWKNOB_VISIBLE_AT, from: GlowknobSk.pt(0, mirror), to: GlowknobSk.pt(0, mirror + depth)))
    // The glass edge: a hairline catching the light where the device meets
    // its reflection, brightest in the middle.
    ctx.fill(Path(CGRect(x: 120, y: mirror - 0.6, width: 760, height: 1.2)),
             with: GlowknobSk.linear([GlowknobRGBA("#FFFFFF00"), GlowknobRGBA("#FFFFFFB0"), GlowknobRGBA("#FFFFFF00")],
                             from: GlowknobSk.pt(120, 0), to: GlowknobSk.pt(880, 0)))
  }
  if fx.spill && pool > 0 {
    ctx.blurred(26, opacity: pool) { l in
      l.fill(Path(ellipseIn: CGRect(x: poolX, y: bottom + 6, width: poolW, height: 44)), with: .color(tipColor.color))
    }
  }
}
