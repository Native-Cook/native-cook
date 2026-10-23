import SwiftUI

private let READ = (x: GlowknobReferenceGeometry.readout.x, width: GlowknobReferenceGeometry.readout.width,
                    y: GlowknobReferenceGeometry.readout.y, line: GlowknobReferenceGeometry.readout.line,
                    size: GlowknobReferenceGeometry.readout.size)

/// The percentage readout (readout.tsx). It reads as one color at a time:
/// light glyphs on the empty well, dark glyphs once the light covers nearly
/// all of them. The whole number swaps (140ms crossfade, with hysteresis), so
/// a light edge never splits a digit into two colors. The value it shows
/// (`control.shown`) is handed over by a reaction only when the integer changes.
struct GlowknobReadout: View {
  let control: GlowknobTactileControl
  let scale: Double
  let highContrast: Bool
  let yoga: GlowknobYoga
  let origin: CGPoint

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("GlowknobReadout")
    let ink = control.numberInk
    let box = yoga.node(CGRect(x: READ.x * scale, y: (GLOWKNOB_SPECIMEN_TOP + READ.y) * scale, width: READ.width * scale,
                               height: READ.line * scale), parent: origin)
    let text = yoga.node(CGRect(x: 0, y: 0, width: READ.width * scale, height: READ.line * scale),
                         parent: box.absolute).frame
    ZStack(alignment: .topLeading) {
      number(highContrast ? GlowknobRGBA("#FFFFFF") : GlowknobTactile.text, text.size).opacity(1 - ink)
      number(GlowknobTactile.ink, text.size).opacity(ink)
    }
    .frame(width: box.frame.width, height: box.frame.height, alignment: .topTrailing)
    .opacity(control.numberAlpha)
    .offset(x: box.frame.minX, y: box.frame.minY)
    .allowsHitTesting(false)
  }

  private func number(_ tint: GlowknobRGBA, _ size: CGSize) -> some View {
    let shown = control.shown
    return GlowknobNumericText(props: GlowknobNumericTextProps(
      value: "\(shown.value)%",
      alignment: "end",
      direction: nil,
      animated: shown.roll && !control.reduced,
      duration: shown.quick ? 110 : 240,
      bounce: shown.quick ? 0 : 0.12,
      blur: !shown.quick,
      maxBlurRadius: 1.5,
      travel: shown.quick ? 0.28 : 0.4,
      enterScale: shown.quick ? 1 : 0.7,
      fontSize: READ.size * scale,
      fontWeight: highContrast ? "600" : "500",
      fontFamily: GlowknobArtText.systemFace,
      monospacedDigits: true,
      color: tint
    ))
    .frame(width: size.width, height: size.height)
  }
}
