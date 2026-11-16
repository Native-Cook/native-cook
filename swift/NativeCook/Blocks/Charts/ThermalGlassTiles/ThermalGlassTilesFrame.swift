import SwiftUI

struct ThermalGlassTilesLayout {
    let size: CGSize
    let scale: Double
    let ox: Double
    let oy: Double
    init(size: CGSize, top: Double, bottom: Double) {
        self.size = size
        let s = min(size.width / 402, max(0.1, (size.height - top - bottom - 24) / 458))
        scale = s; ox = (size.width - 402 * s) / 2
        let ideal = size.height * (447.0 / 874) - 425 * s
        oy = max(top + 12 - 196 * s, min(ideal, size.height - bottom - 12 - 654 * s))
    }
    func tile(_ i: Int) -> CGRect {
        let r = i == 0 ? CGRect(x: 51, y: 196, width: 300, height: 300) :
            (i == 1 ? CGRect(x: 51, y: 512, width: 142, height: 142) : CGRect(x: 209, y: 512, width: 142, height: 142))
        return CGRect(x: ox + r.minX * scale, y: oy + r.minY * scale, width: r.width * scale, height: r.height * scale)
    }
}

struct ThermalGlassTilesFrame: View {
    let state: ThermalGlassTilesFrameState
    let layout: ThermalGlassTilesLayout
    let resources: ThermalGlassTilesResources
    let displayScale: Double
    let fontScale: Double
    let reduced: Bool
    let miles: Bool

    var body: some View {
        let f = state, g = layout
        let wake = reduced ? 0 : 20 + 40 * ThermalGlassTilesMotion.smooth(0.4, 0.8, f.thermalEffort)
        let atlasFrame = f.phase * 24 / (Double.pi * 2)
        let uniforms: [Double] = [g.scale, g.ox, g.oy, f.fieldTime, atlasFrame, f.thermalEffort, f.sample.streak,
                                  f.direction, f.peak, f.circlePeak, f.smallPeak, f.touch.x, f.touch.y, f.touch.z,
                                  wake, f.sample.effort > 0.82 ? 1 : 0, displayScale]
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.thermalGlassTiles(.floatArray(uniforms.map(Float.init)), .image(resources.atlas)))
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var context = c
                context.translateBy(x: g.ox, y: g.oy); context.scaleBy(x: g.scale, y: g.scale)
                ThermalGlassTilesContent(state: f, resources: resources, fontScale: fontScale, miles: miles).draw(context)
            }
        }
        .frame(width: g.size.width, height: g.size.height, alignment: .topLeading)
    }
}
