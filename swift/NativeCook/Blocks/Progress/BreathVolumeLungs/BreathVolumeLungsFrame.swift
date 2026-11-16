import SwiftUI

struct BreathVolumeLungsFrame: View {
    let frame: BreathVolumeLungsFrameState
    let layout: BreathVolumeLungsLayout
    let reduced: Bool
    let fontScale: Double
    typealias R = BreathVolumeLungsResources
    typealias M = BreathVolumeLungsMotion

    var body: some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.breathVolumeLungs(.floatArray(uniforms)))
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in drawText(context) }
                .opacity(frame.fontAlpha)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .accessibilityHidden(true)
    }
    private var uniforms: [Float] {
        let l = layout, s = l.scale, p = M.clamp(frame.progress), value = p * 5.5
        let tenth = floor(value * 10 + 0.00001)
        let packed = BreathVolumeLungsGlyphs.pack(String(format: "%.1f", tenth / 10),
                                                 String(format: "%.1f", min(5.5, (tenth + 1) / 10)))
        var u: [Double] = [l.width, l.height]
        u += [l.pitchX, l.pitchY, l.heroTop, 32]
        u += [32, 40, 64, 1.28 * s]
        u += [l.width / 2 + 2 * s, l.digitY, l.pitchX * 0.4657, l.pitchY * 0.4657]
        u += [8 * s, l.cardTop, l.width - 16 * s, l.cardHeight]
        u += [frame.progress, reduced ? 0 : frame.flow, reduced ? 0 : frame.time, reduced ? 0 : M.breathe(frame.time)]
        u += [frame.packAge, frame.pressed, reduced ? 0 : frame.rebound, reduced ? 1 : 0]
        u += [l.knobX0, l.knobX1, l.knobY, 17 * s]
        u += packed
        u += [M.smooth((value * 10 - tenth) / 0.75)]
        u += R.rgb(0x030305); u += R.rgb(0x08080C); u += R.rgb(0xF4F4F8)
        u += M.heat(p); u += R.rgb(0xD8E270); u += R.rgb(0xF4F4F8)
        u += [0, reduced ? 0 : frame.lastAir]
        return u.map(Float.init)
    }
    private func drawText(_ c: GraphicsContext) {
        let l = layout, s = l.scale, k = l.k, w = l.width
        R.text(c, "Expiratory Volume", x: 20 * s, top: l.titleBase - 28.5 * s,
               width: w - 40 * s, size: 28.5 * s * fontScale, align: 1, medium: true, height: 28.5 * s * 1.3)
        R.text(c, "Liter", x: 83 * s, top: l.labelBase - 13.5 * s,
               width: w - 160 * s, size: 13.5 * s * fontScale, align: 1, height: 13.5 * s * 1.5)
        let numberY = l.cardTop + 36 * s * k
        // The reference caps only the two TextInput counters at 125% Dynamic Type.
        let numberSize = 15 * s * min(fontScale, 1.25)
        R.text(c, String(format: "%.2f", frame.elapsed), x: 52 * s, top: numberY,
               width: 64 * s, size: numberSize, tabular: true)
        R.text(c, String(format: "%.2f", max(0, frame.flow)), x: w - 116 * s, top: numberY,
               width: 64 * s, size: numberSize, align: 2, tabular: true)
        let labelY = l.cardTop + 60.5 * s * k
        R.text(c, "Seconds", x: 52 * s, top: labelY, width: 68 * s,
               size: max(11, 11.5 * s) * fontScale, ink: 0x8089DC, height: 16 * s)
        R.text(c, "L/s", x: w - 116 * s, top: labelY, width: 64 * s,
               size: max(11, 11.5 * s) * fontScale, align: 2, ink: 0x8089DC, height: 16 * s)
        let ys = [37.1, 42.8, 47.5, 53.1]
        let xs: [[Double]] = [[0], [-0.5, 0.5], [-1.5, -0.5, 0.5, 1.5], [-1.5, -0.5, 0.5, 1.5]]
        for row in 0..<4 {
            for x in xs[row] {
                let cx = w / 2 + (3 + x * 5.6) * s, cy = l.cardTop + ys[row] * s * k, r = 1.35 * s
                c.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)), with: .color(R.color(0xF4F4F8)))
            }
        }
        let captionY = l.cardTop + 75 * s * k - 15 * s
        R.text(c, "Empty your lungs", x: 104 * s, top: captionY, width: w - 208 * s,
               size: 15 * s * fontScale, align: 1, alpha: M.clamp(1 - 2 * frame.result))
        R.text(c, "Well done", x: 104 * s, top: captionY, width: w - 208 * s,
               size: 15 * s * fontScale, align: 1, alpha: M.clamp(2 * frame.result - 1))
    }
}
